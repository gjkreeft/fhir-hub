package nl.digitalis.fhirhub.fhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UrlType;
import org.junit.jupiter.api.Test;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import nl.digitalis.fhirhub.model.ExistingPrescription;
import nl.digitalis.fhirhub.model.LabResult;
import nl.digitalis.fhirhub.model.SessionRequest;
import nl.digitalis.fhirhub.model.SessionType;

class SessionParametersMapperTest {

	private final SessionParametersMapper mapper = new SessionParametersMapper(new CodeSystemRegistry());

	@Test
	void mapsAMinimalFormularySession() {
		SessionRequest request = mapper.toSessionRequest(bind(parameters(), SessionType.FORMULARY));

		assertThat(request.icpc()).isEqualTo("A01");
		assertThat(request.endSessionUrl()).isEqualTo("https://someurl.example/done");
		assertThat(request.patient().gender()).isEqualTo("F");
		assertThat(request.patient().dateOfBirth()).isEqualTo("1980-01-01");
	}

	@Test
	void requiresAnIcpcReasonForAFormularySessionButNotForCreateRx() {
		Parameters withoutReason = parameters();
		withoutReason.getParameter().removeIf(p -> SessionParametersMapper.PARAM_REASON.equals(p.getName()));

		assertThatThrownBy(() -> mapper.toSessionRequest(bind(withoutReason, SessionType.FORMULARY)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("reason");

		assertThat(mapper.toSessionRequest(bind(withoutReason, SessionType.CREATE_RX)).icpc()).isNull();
	}

	/** Prescriptor's PatientGender has an X, so a host that does not know can still prescribe. */
	@Test
	void mapsUnknownGenderOntoTheUpstreamX() {
		Parameters parameters = parameters();
		patientIn(parameters).setGender(AdministrativeGender.UNKNOWN);

		assertThat(mapper.toSessionRequest(bind(parameters, SessionType.FORMULARY)).patient().gender())
				.isEqualTo("X");
	}

	/**
	 * 'other' is a different assertion from 'unknown' and has no upstream value, and an absent
	 * gender is a caller omission rather than a statement about the patient. Coercing either
	 * into X would put words in the host's mouth.
	 */
	@Test
	void rejectsGendersPrescriptorCannotRepresent() {
		Parameters parameters = parameters();
		patientIn(parameters).setGender(AdministrativeGender.OTHER);

		assertThatThrownBy(() -> mapper.toSessionRequest(bind(parameters, SessionType.FORMULARY)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("male");

		Parameters absent = parameters();
		patientIn(absent).setGender(null);

		assertThatThrownBy(() -> mapper.toSessionRequest(bind(absent, SessionType.FORMULARY)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("absent gender");
	}

	@Test
	void routesCodingsToTheirUpstreamSubsystem() {
		Parameters parameters = parameters();
		parameters.addParameter().setName(SessionParametersMapper.PARAM_ALLERGY)
				.setResource(new AllergyIntolerance().setCode(concept(Systems.G_STANDAARD_SNK, "10499")));
		parameters.addParameter().setName(SessionParametersMapper.PARAM_CONDITION)
				.setResource(new Condition().setCode(concept(Systems.G_STANDAARD_CONTRA_INDICATIE, "228")));

		SessionRequest request = mapper.toSessionRequest(bind(parameters, SessionType.FORMULARY));

		assertThat(request.patient().allergies()).singleElement()
				.satisfies(item -> assertThat(item.codeSystem()).isEqualTo("SNK"));
		assertThat(request.patient().contraIndications()).singleElement()
				.satisfies(item -> assertThat(item.codeSystem()).isEqualTo("CICode"));
	}

	@Test
	void rejectsACodingInASystemThatIsNotRouted() {
		Parameters parameters = parameters();
		parameters.addParameter().setName(SessionParametersMapper.PARAM_ALLERGY)
				.setResource(new AllergyIntolerance().setCode(concept("http://snomed.info/sct", "91936005")));

		assertThatThrownBy(() -> mapper.toSessionRequest(bind(parameters, SessionType.FORMULARY)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("no coding in a system this interface routes");
	}

	/**
	 * memo, materiaal and bijzonderheid are one NHG Tabel 45 sleutelcode, not three fields.
	 * They are split apart again only because the upstream dialect transmits them apart.
	 */
	@Test
	void splitsTheNhgTabel45KeyCodeBackIntoItsPositions() {
		Parameters parameters = parameters();
		parameters.addParameter().setName(SessionParametersMapper.PARAM_OBSERVATION)
				.setResource(new Observation()
						.setCode(concept(Systems.NHG_TABEL_45, "RH24KQFB"))
						.setValue(new Quantity().setValue(10))
						.setEffective(new DateTimeType("2024-07-04")));

		LabResult lab = mapper.toSessionRequest(bind(parameters, SessionType.FORMULARY))
				.patient().laboratoryData().getFirst();

		assertThat(lab.memo()).isEqualTo("RH24");
		assertThat(lab.material()).isEqualTo("KQ");
		assertThat(lab.peculiarity()).isEqualTo("FB");
		assertThat(lab.value()).isEqualTo("10");
		assertThat(lab.keyCode()).isEqualTo("RH24KQFB");
	}

	@Test
	void padsAShortKeyCodeToTheFixedEightPositions() {
		Parameters parameters = parameters();
		parameters.addParameter().setName(SessionParametersMapper.PARAM_OBSERVATION)
				.setResource(new Observation()
						.setCode(concept(Systems.NHG_TABEL_45, "ALDOB"))
						.setValue(new StringType("10"))
						.setEffective(new DateTimeType("2024-07-04")));

		LabResult lab = mapper.toSessionRequest(bind(parameters, SessionType.FORMULARY))
				.patient().laboratoryData().getFirst();

		assertThat(lab.memo()).isEqualTo("ALDO");
		assertThat(lab.material()).isEqualTo("B");
		assertThat(lab.peculiarity()).isEmpty();
		assertThat(lab.keyCode()).isEqualTo("ALDOB   ");
	}

	/** ICPC-1 NL shape, enforced since v2 so a malformed code is a 400 not an upstream failure. */
	@Test
	void rejectsAnIcpcCodeThatIsNotWellFormed() {
		Parameters parameters = parameters();
		parameters.getParameter().removeIf(p -> SessionParametersMapper.PARAM_REASON.equals(p.getName()));
		parameters.addParameter().setName(SessionParametersMapper.PARAM_REASON)
				.setValue(concept(Systems.ICPC_1_NL, "AA1"));

		assertThatThrownBy(() -> mapper.toSessionRequest(bind(parameters, SessionType.FORMULARY)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("Invalid ICPC code");
	}

	@Test
	void acceptsASubdividedIcpcCode() {
		Parameters parameters = parameters();
		parameters.getParameter().removeIf(p -> SessionParametersMapper.PARAM_REASON.equals(p.getName()));
		parameters.addParameter().setName(SessionParametersMapper.PARAM_REASON)
				.setValue(concept(Systems.ICPC_1_NL, "U71.01"));

		assertThat(mapper.toSessionRequest(bind(parameters, SessionType.FORMULARY)).icpc()).isEqualTo("U71.01");
	}

	/** The user is redirected there, so a non-http scheme must not reach Prescriptor. */
	@Test
	void rejectsAnEndSessionUrlThatIsNotHttp() {
		Parameters parameters = parameters();
		parameters.getParameter().removeIf(p -> SessionParametersMapper.PARAM_END_SESSION_URL.equals(p.getName()));
		parameters.addParameter().setName(SessionParametersMapper.PARAM_END_SESSION_URL)
				.setValue(new UrlType("javascript:alert(1)"));

		assertThatThrownBy(() -> mapper.toSessionRequest(bind(parameters, SessionType.FORMULARY)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("http or https");
	}

	@Test
	void requiresTheCallingSystemToIdentifyItself() {
		Parameters parameters = parameters();
		parameters.getParameter().removeIf(p -> SessionParametersMapper.PARAM_XIS_ID.equals(p.getName()));

		assertThatThrownBy(() -> mapper.toSessionRequest(bind(parameters, SessionType.FORMULARY)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("xisId");
	}

	@Test
	void readsTheCallingSystemIdAndVersion() {
		SessionRequest request = mapper.toSessionRequest(bind(parameters(), SessionType.FORMULARY));

		assertThat(request.xis().id()).isEqualTo("xis-001");
		assertThat(request.xis().version()).isEqualTo("1.0");
	}

	/** An existing prescription is modelled as the MedicationRequest $session-result returns. */
	@Test
	void readsAnExistingPrescriptionForCreateRx() {
		Parameters parameters = parameters();
		parameters.addParameter().setName(SessionParametersMapper.PARAM_PRESCRIPTION)
				.setResource(existingPrescription());

		ExistingPrescription prescription =
				mapper.toSessionRequest(bind(parameters, SessionType.CREATE_RX)).prescription();

		assertThat(prescription.codes()).singleElement().satisfies(code -> {
			assertThat(code.type()).isEqualTo("PRK");
			assertThat(code.value()).isEqualTo(18996);
			assertThat(code.description()).isEqualTo("PARACETAMOL ZETPIL 1000MG");
		});
		assertThat(prescription.atc()).isEqualTo("N02BE01");
		assertThat(prescription.quantity()).isEqualByComparingTo("15");
		assertThat(prescription.unit()).isEqualTo("ST");
		assertThat(prescription.directions()).isEqualTo("3-4D1S; gedurende max. 1 maand");
	}

	/** The URL the extension is emitted at is on the wire, so it is pinned rather than assumed. */
	@Test
	void emitsTheCodedDirectionsExtensionAtItsCanonicalUrl() {
		assertThat(new T25DosageMapper()
				.toDosage(new nl.digitalis.fhirhub.model.Directions("tabel25", "3-4D1S", "drie maal daags"))
				.getExtension())
				.singleElement()
				.satisfies(extension ->
						assertThat(extension.getUrl()).isEqualTo(DigitalisExtensions.CODED_DIRECTIONS));
	}

	@Test
	void refusesAnExistingPrescriptionOnAFormularySession() {
		Parameters parameters = parameters();
		parameters.addParameter().setName(SessionParametersMapper.PARAM_PRESCRIPTION)
				.setResource(existingPrescription());

		assertThatThrownBy(() -> mapper.toSessionRequest(bind(parameters, SessionType.FORMULARY)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("$createrx-session");
	}

	private MedicationRequest existingPrescription() {
		MedicationRequest prescription = new MedicationRequest();
		prescription.setMedication(new CodeableConcept()
				.addCoding(new Coding().setSystem(Systems.PRK).setCode("18996")
						.setDisplay("PARACETAMOL ZETPIL 1000MG"))
				.addCoding(new Coding().setSystem(Systems.ATC).setCode("N02BE01")));
		prescription.getDispenseRequest().setQuantity(new Quantity()
				.setValue(15).setCode("ST").setSystem(Systems.G_STANDAARD_BASISEENHEID));
		Dosage dosage = new Dosage();
		dosage.addExtension(DigitalisExtensions.CODED_DIRECTIONS,
				new StringType("3-4D1S; gedurende max. 1 maand"));
		prescription.addDosageInstruction(dosage);

		return prescription;
	}

	@Test
	void requiresAnEndSessionUrl() {
		Parameters parameters = parameters();
		parameters.getParameter().removeIf(p -> SessionParametersMapper.PARAM_END_SESSION_URL.equals(p.getName()));

		assertThatThrownBy(() -> mapper.toSessionRequest(bind(parameters, SessionType.FORMULARY)))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("endSessionUrl");
	}

	private Parameters parameters() {
		Patient patient = new Patient();
		patient.setGender(AdministrativeGender.FEMALE);
		patient.setBirthDate(date(1980, 1, 1));

		Parameters parameters = new Parameters();
		parameters.addParameter().setName(SessionParametersMapper.PARAM_PATIENT).setResource(patient);
		parameters.addParameter().setName(SessionParametersMapper.PARAM_REASON)
				.setValue(concept(Systems.ICPC_1_NL, "A01"));
		parameters.addParameter().setName(SessionParametersMapper.PARAM_END_SESSION_URL)
				.setValue(new UrlType("https://someurl.example/done"));

		parameters.addParameter().setName(SessionParametersMapper.PARAM_XIS_ID)
				.setValue(new StringType("xis-001"));
		parameters.addParameter().setName(SessionParametersMapper.PARAM_XIS_VERSION)
				.setValue(new StringType("1.0"));

		return parameters;
	}

	private Patient patientIn(Parameters parameters) {
		return (Patient) parameters.getParameter().stream()
				.filter(p -> SessionParametersMapper.PARAM_PATIENT.equals(p.getName()))
				.findFirst()
				.orElseThrow()
				.getResource();
	}

	private CodeableConcept concept(String system, String code) {
		return new CodeableConcept().addCoding(new Coding().setSystem(system).setCode(code));
	}

	/**
	 * Stands in for HAPI's operation-parameter binder, the way H2 stands in for the medcode
	 * view: these tests are about the mapping and validation rules, not about the binding.
	 * Real binding is exercised over HTTP in {@code FhirHubIntegrationTest}.
	 *
	 * <p>It mirrors what HAPI actually does: match on {@code name}, then read {@code resource} or
	 * {@code value[x]}, and reject a value of the wrong type rather than coercing it.
	 */
	private SessionInputs bind(Parameters parameters, SessionType type) {
		return new SessionInputs(type,
				first(parameters, SessionParametersMapper.PARAM_PATIENT, Patient.class),
				value(parameters, SessionParametersMapper.PARAM_REASON, CodeableConcept.class),
				value(parameters, SessionParametersMapper.PARAM_END_SESSION_URL, UrlType.class),
				value(parameters, SessionParametersMapper.PARAM_XIS_ID, StringType.class),
				value(parameters, SessionParametersMapper.PARAM_XIS_VERSION, StringType.class),
				all(parameters, SessionParametersMapper.PARAM_ALLERGY, AllergyIntolerance.class),
				all(parameters, SessionParametersMapper.PARAM_CONDITION, Condition.class),
				all(parameters, SessionParametersMapper.PARAM_MEDICATION, MedicationStatement.class),
				all(parameters, SessionParametersMapper.PARAM_OBSERVATION, Observation.class),
				first(parameters, SessionParametersMapper.PARAM_PRESCRIPTION, MedicationRequest.class));
	}

	private <T> List<T> all(Parameters parameters, String name, Class<T> type) {
		return parameters.getParameter().stream()
				.filter(p -> name.equals(p.getName()))
				.map(ParametersParameterComponent::getResource)
				.filter(type::isInstance)
				.map(type::cast)
				.toList();
	}

	private <T> T first(Parameters parameters, String name, Class<T> type) {
		return all(parameters, name, type).stream().findFirst().orElse(null);
	}

	@SuppressWarnings("unchecked")
	private <T> T value(Parameters parameters, String name, Class<? super T> type) {
		return (T) parameters.getParameter().stream()
				.filter(p -> name.equals(p.getName()))
				.map(ParametersParameterComponent::getValue)
				.filter(type::isInstance)
				.findFirst()
				.orElse(null);
	}

	private Date date(int year, int month, int day) {
		return new GregorianCalendar(year, month - 1, day).getTime();
	}
}
