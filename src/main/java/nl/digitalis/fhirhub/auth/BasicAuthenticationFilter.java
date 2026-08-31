package nl.digitalis.fhirhub.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;

import ca.uhn.fhir.context.FhirContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import nl.digitalis.fhirhub.model.PrescriptorCredentials;

/**
 * HTTP Basic authentication, in the one form this interface needs.
 *
 * <p>Basic is the starting point rather than the destination: the credentials are the practice id
 * and license key a host already holds, so nobody has to be issued anything new to migrate off the
 * JSON interface. SMART-on-FHIR / OAuth 2.0 is the upgrade path and slots in here, without
 * touching anything downstream of {@link CredentialsResolver}.
 *
 * <p><strong>Nothing is validated here beyond the shape of the header.</strong> Prescriptor is the
 * authority on whether a practice id and key are a real pair — it holds the licence administration
 * and answers with an XML-RPC fault when they are not. Checking locally would mean this service
 * keeping a credential store, which would make it stateful and put it out of step with licence
 * data it does not own. The deliberate consequence is that an unknown practice id is refused by
 * Prescriptor rather than here; the client sees 401 either way.
 *
 * <p>This replaced Spring Security, which was carrying an {@code AuthenticationProvider} that did
 * exactly the check below and a filter chain configured to authenticate everything except two
 * paths. Both are here now, in a form that can be read in one sitting, and the 401 gained an
 * {@code OperationOutcome} body it did not have before — see {@link #unauthorized}.
 */
public class BasicAuthenticationFilter extends HttpFilter {

	private static final long serialVersionUID = 1L;

	private static final String SCHEME = "basic ";
	private static final String REALM = "Basic realm=\"fhir-hub\", charset=\"UTF-8\"";

	private final transient FhirContext fhirContext;
	private final String base;

	public BasicAuthenticationFilter(FhirContext fhirContext, String base) {
		this.fhirContext = fhirContext;
		this.base = base;
	}

	@Override
	protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		if (isPublic(request)) {
			chain.doFilter(request, response);
			return;
		}

		PrescriptorCredentials credentials = credentialsFrom(request.getHeader("Authorization"));
		if (credentials == null) {
			unauthorized(response);
			return;
		}

		CurrentCredentials.set(credentials);
		try {
			chain.doFilter(request, response);
		} finally {
			CurrentCredentials.clear();
		}
	}

	/**
	 * The two paths an integrator has to reach before they hold credentials: the
	 * {@code CapabilityStatement}, which is how they discover what the server supports, and the
	 * {@code OperationDefinition}s it links to, which would otherwise be dead links in a document
	 * written for exactly the reader who cannot follow them. Both are metadata about the interface
	 * and say nothing about any patient.
	 */
	private boolean isPublic(HttpServletRequest request) {
		String path = request.getRequestURI();

		if ((base + "/metadata").equals(path)) {
			return true;
		}

		return "GET".equals(request.getMethod())
				&& (path.equals(base + "/OperationDefinition")
						|| path.startsWith(base + "/OperationDefinition/"));
	}

	/**
	 * {@code null} for anything this service will not act on: no header, another scheme, undecodable
	 * base64, no colon, or either half blank. A key is not checked against anything — see the class
	 * comment.
	 */
	private PrescriptorCredentials credentialsFrom(String header) {
		if (header == null || !header.toLowerCase().startsWith(SCHEME)) {
			return null;
		}

		String decoded;
		try {
			// RFC 7617: the charset is whatever the server asked for, and this one asks for UTF-8.
			decoded = new String(Base64.getDecoder().decode(header.substring(SCHEME.length()).trim()),
					StandardCharsets.UTF_8);
		} catch (IllegalArgumentException malformed) {
			return null;
		}

		// The first colon separates them: a user-id may not contain one, a password may.
		int separator = decoded.indexOf(':');
		if (separator < 0) {
			return null;
		}

		String practiceId = decoded.substring(0, separator);
		String licenseKey = decoded.substring(separator + 1);
		if (practiceId.isBlank() || licenseKey.isBlank()) {
			return null;
		}

		return new PrescriptorCredentials(practiceId, licenseKey);
	}

	/**
	 * A 401 with a {@code WWW-Authenticate} challenge and an {@code OperationOutcome} body.
	 *
	 * <p>The body is the part Spring Security did not do. Everything else this API can return is a
	 * FHIR resource — {@code error/UnauthorizedException} exists precisely so that a 401 raised
	 * further in is not the one un-parseable response — and a rejection before the servlet had no
	 * reason to be the exception to that.
	 */
	private void unauthorized(HttpServletResponse response) throws IOException {
		OperationOutcome outcome = new OperationOutcome();
		outcome.addIssue()
				.setSeverity(IssueSeverity.ERROR)
				.setCode(IssueType.SECURITY)
				.setDiagnostics("Basic authentication is required: send the practice id as the user"
						+ " and the license key as the password.");

		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setHeader("WWW-Authenticate", REALM);
		response.setContentType("application/fhir+json;charset=utf-8");
		// Parsers are not documented as thread-safe, and a 401 is rare enough that one per
		// rejection costs nothing worth pooling for.
		response.getWriter().write(fhirContext.newJsonParser().encodeResourceToString(outcome));
	}
}
