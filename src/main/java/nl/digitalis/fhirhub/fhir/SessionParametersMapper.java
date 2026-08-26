package nl.digitalis.fhirhub.fhir;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.UrlType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import nl.digitalis.fhirhub.model.CodedItem;
import nl.digitalis.fhirhub.model.DrugCode;
import nl.digitalis.fhirhub.model.ExistingPrescription;
import nl.digitalis.fhirhub.model.XisInfo;
import nl.digitalis.fhirhub.model.LabResult;
import nl.digitalis.fhirhub.model.PatientContext;
import nl.digitalis.fhirhub.model.SessionRequest;
import nl.digitalis.fhirhub.model.SessionType;
import nl.digitalis.fhirhub.prescriptor.CodeSystemTokens;

/** Maps the inbound {@code Parameters} of a session operation onto the internal request model. */
@Component
public class SessionParametersMapper {

	public static final String PARAM_PATIENT = "patient";
	public static final String PARAM_ALLERGY = "allergyIntolerance";
	public static final String PARAM_CONDITION = "condition";
	public static final String PARAM_MEDICATION = "medicationStatement";
	public static final String PARAM_OBSERVATION = "observation";
	public static final String PARAM_REASON = "reason";
	public static final String PARAM_END_SESSION_URL = "endSessionUrl";
	public static final String PARAM_XIS_ID = "xisId";
	public static final String PARAM_XIS_VERSION = "xisVersion";
	public static final String PARAM_PRESCRIPTION = "prescription";

	/**
	 * ICPC-1 NL: a letter and two digits, optionally followed by a dot and two more,
	 * e.g. A01 or U71.01. Enforced here so a malformed code is a 400 rather than an opaque
	 * upstream failure.
	 */
	private static final Pattern ICPC = Pattern.compile("^[A-Z][0-9]{2}(\\.[0-9]{2})?$");

	private final CodeSystemRegistry codeSystems;

	public SessionParametersMapper(CodeSystemRegistry codeSystems) {
		this.codeSystems = codeSystems;
	}

	public SessionRequest toSessionRequest(SessionInputs inputs) {
		if (inputs == null) {
			throw new InvalidRequestException("A Parameters resource is required");
		}

		SessionType type = inputs.type();
		Patient patient = inputs.patient();
		if (patient == null) {
			throw new InvalidRequestException(
					"Parameters.parameter:" + PARAM_PATIENT + " is required and must be a Patient");
		}

		String icpc = icpcCode(inputs.reason());
		if (icpc != null && !ICPC.matcher(icpc).matches()) {
			throw new InvalidRequestException(
					"Invalid ICPC code '" + icpc + "'. Must be a letter followed by two digits (e.g. A01), "
							+ "optionally with a dot and two more digits (e.g. U71.01)");
		}

		if (type == SessionType.FORMULARY && icpc == null) {
			throw new InvalidRequestException(
					"Parameters.parameter:" + PARAM_REASON
							+ " is required for a formulary session and must carry an ICPC-1 NL coding");
		}

		String endSessionUrl = endSessionUrl(inputs.endSessionUrl());
		XisInfo xis = xis(inputs.xisId(), inputs.xisVersion());

		PatientContext context = new PatientContext(
				gender(patient),
				birthDate(patient),
				allergies(inputs.allergyIntolerance()),
				contraIndications(inputs.condition()),
				medications(inputs.medicationStatement()),
				laboratoryData(inputs.observation()));

		return new SessionRequest(type, icpc, context, endSessionUrl, xis,
				prescription(inputs.prescription(), type));
	}

	/**
	 * Identifies the calling system. Required, and never forwarded to Prescriptor — it exists
	 * so a log line can be attributed to a supplier and a release.
	 *
	 * <p>Two flat strings rather than one parameter with {@code id} and {@code version} parts:
	 * HAPI's binder never reads {@code part}, so the multi-part shape could not appear in the
	 * generated {@code OperationDefinition} at all.
	 */
	private XisInfo xis(StringType xisId, StringType xisVersion) {
		return new XisInfo(
				requireNonBlank(xisId, PARAM_XIS_ID),
				requireNonBlank(xisVersion, PARAM_XIS_VERSION));
	}

	private String requireNonBlank(StringType value, String name) {
		if (value == null || value.getValue() == null || value.getValue().isBlank()) {
			throw new InvalidRequestException(
					"Parameters.parameter:" + name + " is required and must be a non-blank string");
		}

		return value.getValue();
	}

	/**
	 * A prescription the host already holds, for CreateRx to open for editing.
	 *
	 * <p>Modelled as a MedicationRequest, which is the mirror image of what
	 * {@code $session-result} returns — a host can hand back a prescription it received
	 * earlier without reshaping it.
	 */
	private ExistingPrescription prescription(MedicationRequest prescription, SessionType type) {
		if (prescription == null) {
			return null;
		}

		if (type != SessionType.CREATE_RX) {
			throw new InvalidRequestException(
					"Parameters.parameter:" + PARAM_PRESCRIPTION + " is only accepted by $createrx-session");
		}

		if (!(prescription.getMedication() instanceof CodeableConcept concept)) {
			throw new InvalidRequestException(
					PARAM_PRESCRIPTION + " requires medicationCodeableConcept");
		}

		List<DrugCode> codes = new ArrayList<>();
		String atc = null;
		for (Coding coding : concept.getCoding()) {
			if (Systems.ATC.equals(coding.getSystem())) {
				atc = coding.getCode();
				continue;
			}

			String token = codeSystems.tokenFor(coding.getSystem());
			if (token != null && CodeSystemTokens.MEDICATION.contains(token)) {
				codes.add(new DrugCode(token, toInteger(coding.getCode(), token),
						coding.hasDisplay() ? coding.getDisplay() : concept.getText(),
						null, null, null));
			}
		}

		if (codes.isEmpty()) {
			throw new InvalidRequestException(
					PARAM_PRESCRIPTION + " requires a PRK or HPK coding on medicationCodeableConcept");
		}

		Quantity quantity = prescription.getDispenseRequest().getQuantity();

		return new ExistingPrescription(
				List.copyOf(codes),
				atc,
				quantity.getValue(),
				quantity.hasCode() ? quantity.getCode() : quantity.getUnit(),
				codedDirections(prescription));
	}

	/**
	 * The NHG Tabel 25 coded instruction. Taken from the CodedDirections extension when the
	 * host round-trips a prescription this interface produced, and otherwise from Dosage.text.
	 *
	 * <p>Both the current and the pre-move extension URL are accepted, because a host may hand
	 * back a prescription issued before the canonical moved. Only the current one is emitted.
	 */
	private String codedDirections(MedicationRequest prescription) {
		for (Dosage dosage : prescription.getDosageInstruction()) {
			for (String url : new String[] {
					DigitalisExtensions.CODED_DIRECTIONS, DigitalisExtensions.LEGACY_CODED_DIRECTIONS }) {
				Extension coded = dosage.getExtensionByUrl(url);
				if (coded != null && coded.getValue() != null) {
					return coded.getValue().primitiveValue();
				}
			}
		}

		return prescription.getDosageInstructionFirstRep().getText();
	}

	private Integer toInteger(String code, String token) {
		try {
			return Integer.valueOf(code);
		}
		catch (RuntimeException e) {
			throw new InvalidRequestException("%s code '%s' is not numeric".formatted(token, code));
		}
	}

	/**
	 * FHIR's administrative gender has four values; Prescriptor's {@code PatientGender} has three
	 * — {@code M}, {@code F} and {@code X} ("Unknown").
	 *
	 * <p>{@code unknown} maps onto {@code X} rather than being rejected: a host that genuinely
	 * does not know is stating a fact, and refusing the session would leave it unable to
	 * prescribe at all. Note the clinical consequence — sex-specific surveillance checks cannot
	 * fire on {@code X}, so a host that knows the sex must send it.
	 *
	 * <p>{@code other} and an absent gender are still rejected. {@code other} is not the same
	 * assertion as {@code unknown}, and there is no upstream value for it; an absent gender is a
	 * caller omission rather than a statement about the patient. Coercing either into {@code X}
	 * would put words in the host's mouth.
	 */
	private String gender(Patient patient) {
		AdministrativeGender gender = patient.getGender();
		if (gender == AdministrativeGender.MALE) {
			return "M";
		}
		if (gender == AdministrativeGender.FEMALE) {
			return "F";
		}
		if (gender == AdministrativeGender.UNKNOWN) {
			return "X";
		}

		throw new InvalidRequestException(
				"Patient.gender must be 'male', 'female' or 'unknown'; Prescriptor cannot interpret "
						+ (gender == null ? "an absent gender" : "'" + gender.toCode() + "'"));
	}

	private LocalDate birthDate(Patient patient) {
		if (!patient.hasBirthDate()) {
			throw new InvalidRequestException("Patient.birthDate is required");
		}

		return patient.getBirthDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}

	/**
	 * The reason for encounter. A {@code CodeableConcept} only: a bare {@code Coding} used to be
	 * accepted too, but {@code OperationDefinition.parameter.type} is a single code, so a
	 * parameter cannot be both declared and polymorphic.
	 */
	private String icpcCode(CodeableConcept reason) {
		return reason == null ? null : firstCodeFor(reason, CodeSystemTokens.ICPC);
	}

	private String endSessionUrl(UrlType endSessionUrl) {
		String url = endSessionUrl == null ? null : endSessionUrl.getValue();
		if (url == null) {
			throw new InvalidRequestException(
					"Parameters.parameter:" + PARAM_END_SESSION_URL + " is required");
		}

		return requireHttpUrl(url);
	}

	/** The user is redirected here, so anything but http(s) is rejected rather than forwarded. */
	private String requireHttpUrl(String url) {
		try {
			String scheme = URI.create(url).getScheme();
			if ("http".equals(scheme) || "https".equals(scheme)) {
				return url;
			}
		}
		catch (IllegalArgumentException e) {
			// fall through to the same rejection
		}

		throw new InvalidRequestException("Invalid endSessionUrl. Must be a valid http or https URL.");
	}

	private List<CodedItem> allergies(List<AllergyIntolerance> allergies) {
		List<CodedItem> items = new ArrayList<>();
		for (AllergyIntolerance allergy : allergies) {
			addCoded(items, allergy.getCode(), CodeSystemTokens.ALLERGY, "AllergyIntolerance.code");
		}

		return items;
	}

	private List<CodedItem> contraIndications(List<Condition> conditions) {
		List<CodedItem> items = new ArrayList<>();
		for (Condition condition : conditions) {
			addCoded(items, condition.getCode(), CodeSystemTokens.CONTRA_INDICATION, "Condition.code");
		}

		return items;
	}

	private List<CodedItem> medications(List<MedicationStatement> statements) {
		List<CodedItem> items = new ArrayList<>();
		for (MedicationStatement statement : statements) {
			if (statement.getMedication() instanceof CodeableConcept concept) {
				addCoded(items, concept, CodeSystemTokens.MEDICATION, "MedicationStatement.medicationCodeableConcept");
			}
		}

		return items;
	}

	private void addCoded(List<CodedItem> items, CodeableConcept concept, java.util.Set<String> allowed, String path) {
		if (concept == null || concept.getCoding().isEmpty()) {
			throw new InvalidRequestException(path + " requires at least one coding");
		}

		for (Coding coding : concept.getCoding()) {
			String token = codeSystems.tokenFor(coding.getSystem());
			if (token != null && allowed.contains(token)) {
				items.add(new CodedItem(token, coding.getCode()));

				return;
			}
		}

		throw new InvalidRequestException(
				path + " has no coding in a system this interface routes; expected one of " + allowed);
	}

	private String firstCodeFor(CodeableConcept concept, String token) {
		for (Coding coding : concept.getCoding()) {
			if (token.equals(codeSystems.tokenFor(coding.getSystem()))) {
				return coding.getCode();
			}
		}

		return null;
	}

	/**
	 * Maps lab Observations onto NHG Tabel 45 determinations.
	 *
	 * <p>The code is the 8-position sleutelcode, which is split back into memo, materiaal and
	 * bijzonderheid because that is how the upstream dialect transmits it. They are not three
	 * independent facts — the combination is the identity of the determination.
	 */
	private List<LabResult> laboratoryData(List<Observation> observations) {
		List<LabResult> results = new ArrayList<>();
		for (Observation observation : observations) {
			String key = firstCodeForSystem(observation.getCode(), Systems.NHG_TABEL_45);
			if (key == null) {
				throw new InvalidRequestException(
						"Observation.code requires a coding in " + Systems.NHG_TABEL_45
								+ " (NHG Tabel 45 sleutelcode)");
			}

			String padded = "%-8s".formatted(key);
			results.add(new LabResult(
					padded.substring(0, 4).trim(),
					padded.substring(4, 6).trim(),
					padded.substring(6, 8).trim(),
					effectiveDate(observation),
					observationValue(observation)));
		}

		return results;
	}

	private String firstCodeForSystem(CodeableConcept concept, String system) {
		if (concept == null) {
			return null;
		}

		for (Coding coding : concept.getCoding()) {
			if (system.equals(coding.getSystem())) {
				return coding.getCode();
			}
		}

		return null;
	}

	private LocalDate effectiveDate(Observation observation) {
		if (!observation.hasEffectiveDateTimeType()) {
			throw new InvalidRequestException("Observation.effectiveDateTime is required");
		}

		return observation.getEffectiveDateTimeType().getValue()
				.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
	}

	/**
	 * Reads the result value as the plain string the upstream expects.
	 *
	 * <p>Both {@code valueQuantity} and {@code valueString} are accepted. The upstream carries
	 * no unit, so a Quantity's unit is dropped here — losing it is unavoidable, but accepting
	 * the richer type means integrators do not have to downgrade data they already hold.
	 */
	private String observationValue(Observation observation) {
		Type value = observation.getValue();
		if (value instanceof Quantity quantity && quantity.hasValue()) {
			return quantity.getValue().stripTrailingZeros().toPlainString();
		}
		if (value != null && value.isPrimitive()) {
			return value.primitiveValue();
		}

		throw new InvalidRequestException(
				"Observation.value must be a Quantity or a primitive type");
	}
}
