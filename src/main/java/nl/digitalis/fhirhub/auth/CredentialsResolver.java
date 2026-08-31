package nl.digitalis.fhirhub.auth;

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
 *
 * <p>The indirection earns its keep by being the one seam an OAuth 2.0 migration has to move:
 * the operation providers ask this class for credentials and never touch the HTTP layer
 * themselves.
 */
@Component
public class CredentialsResolver {

	public PrescriptorCredentials current() {
		PrescriptorCredentials credentials = CurrentCredentials.get();

		// Only reachable if a provider is invoked outside the filter this application registers,
		// which would be a wiring mistake rather than a caller's. A 401 is still the honest answer:
		// nothing established who is asking.
		if (credentials == null) {
			throw new UnauthorizedException("Basic authentication is required");
		}

		return credentials;
	}
}
