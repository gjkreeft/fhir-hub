package nl.digitalis.fhirhub.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import nl.digitalis.fhirhub.error.UnauthorizedException;
import nl.digitalis.fhirhub.model.PrescriptorCredentials;

/**
 * Lifts the Prescriptor credentials off the HTTP layer.
 *
 * <p>This is the whole of "authentication lives on the HTTP layer": the JSON interface carries
 * the practice id and license key as fields in the request body, and here they are the two
 * halves of the Basic credential. Downstream nothing differs — they travel to Prescriptor as the
 * PracticeID and LicenseKey members of the XML-RPC call either way.
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
