package nl.digitalis.fhirhub.server;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import nl.digitalis.fhirhub.fhir.ResultBundleMapper;
import nl.digitalis.fhirhub.prescriptor.PrescriptorClient;

/**
 * Fetches the result of a finished session.
 *
 * <p>Declared idempotent so HAPI exposes it over GET, which is what a polling host wants.
 * Note that this is idempotent in the HTTP sense only: upstream, requesting the result
 * <em>ends</em> the session, so a second call for the same id returns a 401 rather than the
 * same Bundle again. That is inherited from Prescriptor, not introduced here.
 */
@Component
public class SessionResultProvider extends BaseProvider {

	public static final String SESSION_RESULT = "$session-result";
	public static final String IN_SESSION = "session";

	private final PrescriptorClient prescriptor;
	private final ResultBundleMapper mapper;

	public SessionResultProvider(PrescriptorClient prescriptor, ResultBundleMapper mapper) {
		this.prescriptor = prescriptor;
		this.mapper = mapper;
	}

	@Operation(name = SESSION_RESULT, idempotent = true)
	public Bundle sessionResult(@OperationParam(name = IN_SESSION, min = 1, max = 1) StringType session) {
		if (session == null || session.isEmpty()) {
			throw new InvalidRequestException("The 'session' parameter is required");
		}

		return mapper.toBundle(prescriptor.requestResult(session.getValue()));
	}
}
