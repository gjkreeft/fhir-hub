package nl.digitalis.fhirhub.error;

import ca.uhn.fhir.rest.server.exceptions.BaseServerResponseException;

/**
 * A 401 that is rendered as an {@code OperationOutcome}.
 *
 * <p>HAPI's own {@code AuthenticationException} is special-cased inside the server: it is
 * written as a bare {@code text/plain} body rather than a FHIR resource. That is a reasonable
 * default for a server that authenticates its own callers, but here it produces the one
 * response in the API that is not FHIR — integrators writing a single error handler would hit
 * a parse failure exactly when something has gone wrong.
 *
 * <p>Subclassing {@code BaseServerResponseException} directly keeps the status code and gets
 * the standard OperationOutcome rendering.
 */
public class UnauthorizedException extends BaseServerResponseException {

	private static final long serialVersionUID = 1L;

	private static final int STATUS_UNAUTHORIZED = 401;

	public UnauthorizedException(String message) {
		super(STATUS_UNAUTHORIZED, message);
	}
}
