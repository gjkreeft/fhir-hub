package nl.digitalis.fhirhub.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import nl.digitalis.fhirhub.error.UnauthorizedException;
import nl.digitalis.fhirhub.model.PrescriptorCredentials;

/**
 * Lifts the Prescriptor credentials off the HTTP layer.
 *
 * <p>This is the whole of the "authentication moves out of the message" change: the practice
 * id and license key used to be fields in the request body, and are now the two halves of the
 * Basic credential. Everything downstream is unchanged — they still travel to Prescriptor as
 * the PracticeID and LicenseKey members of the XML-RPC call.
 */
@Component
public class CredentialsResolver {

	public PrescriptorCredentials current() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !authentication.isAuthenticated()) {
			throw new UnauthorizedException("Basic authentication is required");
		}

		Object credentials = authentication.getCredentials();
		if (credentials == null) {
			throw new UnauthorizedException("No license key present on the request");
		}

		return new PrescriptorCredentials(authentication.getName(), credentials.toString());
	}
}
