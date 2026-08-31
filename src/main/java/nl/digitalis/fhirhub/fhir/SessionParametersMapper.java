package nl.digitalis.fhirhub.fhir;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateTimeType;
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
import org.hl7.fhir.r4.model.UrlType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import nl.digitalis.fhirhub.fhir.LabDeterminations.Determination;
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

	private final LabDeterminations determinations;

	public SessionParametersMapper(CodeSystemRegistry codeSystems, LabDeterminations determinations) {
		this.codeSystems = codeSystems;
		this.determinations = determinations;
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
			throw new InvalidRequestException(PARAM_PRESCRIPTION
					+ " requires a PRK or HPK coding on medicationCodeableConcept; expected one of "
					+ codeSystems.systemsFor(CodeSystemTokens.MEDICATION));
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
	 * <p>The extension wins over {@code Dosage.text}, which is the human-readable form beside
	 * it: reading the text in preference would downgrade the dosing silently rather than
	 * visibly, and that is the failure this interface is built to avoid.
	 */
	private String codedDirections(MedicationRequest prescription) {
		for (Dosage dosage : prescription.getDosageInstruction()) {
			Extension coded = dosage.getExtensionByUrl(DigitalisExtensions.CODED_DIRECTIONS);
			if (coded != null && coded.getValue() != null) {
				return coded.getValue().primitiveValue();
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
	 * The reason for encounter, as a {@code CodeableConcept} and nothing else:
	 * {@code OperationDefinition.parameter.type} is a single code, so a parameter cannot be both
	 * declared and polymorphic.
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

	private void addCoded(List<CodedItem> items, CodeableConcept concept, Set<String> allowed, String path) {
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

		// The accepted system URIs, not the upstream tokens: the tokens are exactly the strings
		// a caller must not put in Coding.system, so naming them here would misdirect.
		throw new InvalidRequestException(
				path + " has no coding in a system this interface routes; expected one of "
						+ codeSystems.systemsFor(allowed));
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
	 * Maps lab Observations onto the determinations medication surveillance reads.
	 *
	 * <p>A host sends a LOINC code and the upstream tests that same code, so nothing is translated;
	 * {@link LabDeterminations} says which codes a rule can read and in which unit. A code outside
	 * that list is refused rather than forwarded, because forwarding it would leave the prescriber
	 * believing a value had been weighed when nothing read it — the same false all-clear an
	 * unresolvable drug code is refused for.
	 */
	private List<LabResult> laboratoryData(List<Observation> observations) {
		List<LabResult> results = new ArrayList<>();
		for (Observation observation : observations) {
			Coding coding = firstCodingForSystem(observation.getCode(), Systems.LOINC);
			if (coding == null) {
				throw new InvalidRequestException(
						"Observation.code requires a coding in " + Systems.LOINC
								+ "; the determinations medication surveillance reads are "
								+ determinations.acceptedCodes());
			}

			Determination determination = determinations.forLoinc(coding.getCode());
			if (determination == null) {
				throw new InvalidRequestException(
						"LOINC code '" + coding.getCode() + "' is not a determination medication"
								+ " surveillance reads, so sending it would suggest it had been"
								+ " weighed. Accepted: " + determinations.acceptedCodes());
			}

			Effective effective = effective(observation);
			results.add(new LabResult(
					determination.loinc(),
					coding.hasDisplay() ? coding.getDisplay() : determination.display(),
					determination.unit(),
					effective.date(),
					effective.time(),
					valueIn(observation, determination)));
		}

		return results;
	}

	private Coding firstCodingForSystem(CodeableConcept concept, String system) {
		if (concept == null) {
			return null;
		}

		for (Coding coding : concept.getCoding()) {
			if (system.equals(coding.getSystem())) {
				return coding;
			}
		}

		return null;
	}

	/**
	 * When the sample was taken, to the precision the host stated it in.
	 *
	 * <p>The time of day is kept rather than truncated away. Several results for one determination
	 * are resolved upstream by taking the most recent — {@code TProtocolParserDataLOINC.GetValueExt}
	 * in the rules engine — and that comparison reads the {@code date} attribute alone. Sending a
	 * date-only value puts every result of one day at midnight, and the engine's most-recent scan
	 * keeps the first of a tie, so this morning's eGFR would beat this afternoon's whenever the host
	 * listed it first. The host had the time; dropping it here is what loses the ordering.
	 *
	 * <p>A host that states only a date still gets a date: it is a claim about precision, and
	 * inventing a midnight it did not send would be a different claim.
	 */
	private Effective effective(Observation observation) {
		if (!observation.hasEffectiveDateTimeType()) {
			throw new InvalidRequestException("Observation.effectiveDateTime is required");
		}

		DateTimeType effective = observation.getEffectiveDateTimeType();
		ZonedDateTime moment = effective.getValue().toInstant().atZone(ZoneId.systemDefault());
		boolean statedTime = effective.getPrecision() != null
				&& effective.getPrecision().ordinal() >= TemporalPrecisionEnum.MINUTE.ordinal();

		// Seconds are the finest the upstream parses; anything below is dropped rather than rounded.
		return new Effective(moment.toLocalDate(),
				statedTime ? moment.toLocalTime().withNano(0) : null);
	}

	/** {@code effectiveDateTime}, split into the two attributes the upstream document carries. */
	private record Effective(LocalDate date, LocalTime time) {
	}

	/**
	 * The result value, in the unit the rules evaluate in.
	 *
	 * <p>The upstream carries no unit, so the number has to be right on arrival: a kalium in mg/dL
	 * rather than mmol/L is a different answer, not a rounded one, and nothing downstream could
	 * notice. Hence a {@code Quantity} with a UCUM code the determination accepts, converted where
	 * the conversion is exact, and a refusal otherwise.
	 */
	private String valueIn(Observation observation, Determination determination) {
		if (!(observation.getValue() instanceof Quantity quantity) || !quantity.hasValue()) {
			throw new InvalidRequestException(
					"Observation.valueQuantity is required for " + determination.loinc() + " ("
							+ determination.display() + "), in " + determination.acceptedUnits());
		}

		String unit = quantity.hasCode() ? quantity.getCode() : quantity.getUnit();
		BigDecimal converted = determination.toUpstreamUnit(unit, quantity.getValue());
		if (converted == null) {
			throw new InvalidRequestException(
					"Observation.valueQuantity for " + determination.loinc() + " ("
							+ determination.display() + ") must be in " + determination.acceptedUnits()
							+ " as a UCUM code, not '" + unit + "': the upstream carries no unit, so"
							+ " the value is evaluated as " + determination.unit());
		}

		return converted.stripTrailingZeros().toPlainString();
	}
}
