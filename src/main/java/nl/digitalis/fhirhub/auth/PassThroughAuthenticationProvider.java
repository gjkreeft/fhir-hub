package nl.digitalis.fhirhub.auth;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * Accepts any well-formed practice id / license key pair.
 *
 * <p>Prescriptor is the authority on whether a pair is valid — it holds the licence
 * administration and answers with an XML-RPC fault when it is not. Validating locally would
 * mean fhir-hub keeping a credential store, which would make it stateful and put it out of
 * step with the licence data it does not own. So this provider checks only that both halves
 * are present, and lets the upstream reject what it does not recognise.
 *
 * <p>The consequence, deliberately accepted: a request with an unknown practice id gets a 401
 * from Prescriptor rather than from fhir-hub. The client sees the same status either way.
 */
@Component
public class PassThroughAuthenticationProvider implements AuthenticationProvider {

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		String practiceId = authentication.getName();
		Object credentials = authentication.getCredentials();
		String licenseKey = credentials == null ? null : credentials.toString();

		if (practiceId == null || practiceId.isBlank() || licenseKey == null || licenseKey.isBlank()) {
			throw new BadCredentialsException("A practice id and license key are both required");
		}

		return UsernamePasswordAuthenticationToken.authenticated(practiceId, licenseKey, java.util.List.of());
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}
}
