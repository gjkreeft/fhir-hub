package nl.digitalis.fhirhub.server;

import java.util.List;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UrlType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import nl.digitalis.fhirhub.auth.CredentialsResolver;
import nl.digitalis.fhirhub.fhir.Profiles;
import nl.digitalis.fhirhub.fhir.SessionInputs;
import nl.digitalis.fhirhub.fhir.SessionParametersMapper;
import nl.digitalis.fhirhub.model.SessionHandle;
import nl.digitalis.fhirhub.model.SessionRequest;
import nl.digitalis.fhirhub.model.SessionType;
import nl.digitalis.fhirhub.prescriptor.PrescriptorClient;
import nl.digitalis.fhirhub.validation.ProfileValidator;

/**
 * The two session-opening operations.
 *
 * <p>Both take the patient context in a {@code Parameters} body and return the launch URL plus
 * the session id the host later polls with. They are declared non-idempotent, so HAPI exposes
 * them over POST only — opening a session has an effect upstream and must not be cached or
 * retried blindly.
 *
 * <p>The parameters are declared individually rather than the body being taken whole, so that
 * HAPI generates an {@code OperationDefinition} listing every name, type and cardinality — the
 * thing an integrator can generate code from. Nothing is read off the raw body, so nothing is
 * missing from that definition. The two operations differ in exactly two declared places:
 * {@code reason} is 1..1 for formulary and 0..1 for CreateRx, and {@code prescription} is 0..0
 * for formulary and 0..1 for CreateRx.
 *
 * <p>{@link SessionInputs} records why the cardinalities are declarations rather than
 * enforcement.
 */
@Component
public class SessionOperationProvider extends BaseProvider {

	public static final String FORMULARY_SESSION = "$formulary-session";
	public static final String CREATERX_SESSION = "$createrx-session";

	public static final String OUT_SESSION_ID = "sessionId";
	public static final String OUT_URL = "url";

	private final SessionParametersMapper mapper;
	private final PrescriptorClient prescriptor;
	private final CredentialsResolver credentials;
	private final ProfileValidator profileValidator;

	public SessionOperationProvider(SessionParametersMapper mapper,
			PrescriptorClient prescriptor,
			CredentialsResolver credentials,
			ProfileValidator profileValidator) {
		this.mapper = mapper;
		this.prescriptor = prescriptor;
		this.credentials = credentials;
		this.profileValidator = profileValidator;
	}

	@Operation(name = FORMULARY_SESSION, idempotent = false)
	public Parameters formularySession(
			@OperationParam(name = "patient", min = 1, max = 1) Patient patient,
			@OperationParam(name = "reason", min = 1, max = 1) CodeableConcept reason,
			@OperationParam(name = "endSessionUrl", min = 1, max = 1) UrlType endSessionUrl,
			@OperationParam(name = "xisId", min = 1, max = 1) StringType xisId,
			@OperationParam(name = "xisVersion", min = 1, max = 1) StringType xisVersion,
			@OperationParam(name = "allergyIntolerance", min = 0, max = OperationParam.MAX_UNLIMITED) List<AllergyIntolerance> allergyIntolerance,
			@OperationParam(name = "condition", min = 0, max = OperationParam.MAX_UNLIMITED) List<Condition> condition,
			@OperationParam(name = "medicationStatement", min = 0, max = OperationParam.MAX_UNLIMITED) List<MedicationStatement> medicationStatement,
			@OperationParam(name = "observation", min = 0, max = OperationParam.MAX_UNLIMITED) List<Observation> observation,
			@OperationParam(name = "prescription", min = 0, max = 0) MedicationRequest prescription,
			@ResourceParam Parameters body) {

		return openSession(new SessionInputs(SessionType.FORMULARY, patient, reason, endSessionUrl, xisId, xisVersion,
				allergyIntolerance, condition, medicationStatement, observation, prescription),
				body, Profiles.FORMULARY_SESSION_INPUT);
	}

	@Operation(name = CREATERX_SESSION, idempotent = false)
	public Parameters createRxSession(
			@OperationParam(name = "patient", min = 1, max = 1) Patient patient,
			@OperationParam(name = "reason", min = 0, max = 1) CodeableConcept reason,
			@OperationParam(name = "endSessionUrl", min = 1, max = 1) UrlType endSessionUrl,
			@OperationParam(name = "xisId", min = 1, max = 1) StringType xisId,
			@OperationParam(name = "xisVersion", min = 1, max = 1) StringType xisVersion,
			@OperationParam(name = "allergyIntolerance", min = 0, max = OperationParam.MAX_UNLIMITED) List<AllergyIntolerance> allergyIntolerance,
			@OperationParam(name = "condition", min = 0, max = OperationParam.MAX_UNLIMITED) List<Condition> condition,
			@OperationParam(name = "medicationStatement", min = 0, max = OperationParam.MAX_UNLIMITED) List<MedicationStatement> medicationStatement,
			@OperationParam(name = "observation", min = 0, max = OperationParam.MAX_UNLIMITED) List<Observation> observation,
			@OperationParam(name = "prescription", min = 0, max = 1) MedicationRequest prescription,
			@ResourceParam Parameters body) {

		return openSession(new SessionInputs(SessionType.CREATE_RX, patient, reason, endSessionUrl, xisId, xisVersion,
				allergyIntolerance, condition, medicationStatement, observation, prescription),
				body, Profiles.CREATERX_SESSION_INPUT);
	}

	/**
	 * The body is validated against its profile before anything else happens: before the
	 * G-Standaard lookup and before the call upstream, so the cheapest possible failure.
	 *
	 * <p>Validating first also means an integrator gets <em>every</em> problem in one
	 * OperationOutcome rather than one per round trip, which is what onboarding actually needs.
	 * The trade is real, though: for the handful of rules both layers check, the validator's
	 * generic message wins over the mapper's — "Patient.gender must be 'male', 'female' or
	 * 'unknown'; Prescriptor cannot interpret 'other'" names a consequence a binding error
	 * cannot. Swap the two calls to prefer the specific message over the complete list.
	 */
	private Parameters openSession(SessionInputs inputs, Parameters body, String profile) {
		profileValidator.validate(body, profile);

		SessionRequest request = mapper.toSessionRequest(inputs);
		SessionHandle handle = prescriptor.openSession(request, credentials.current());

		Parameters response = new Parameters();
		response.addParameter().setName(OUT_SESSION_ID).setValue(new StringType(handle.sessionId()));
		response.addParameter().setName(OUT_URL).setValue(new UrlType(handle.prescriptorUrl()));

		return response;
	}
}
