package nl.digitalis.fhirhub.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import nl.digitalis.fhirhub.auth.PassThroughAuthenticationProvider;

/**
 * HTTP Basic authentication.
 *
 * <p>Basic is the starting point rather than the destination: the credentials are the existing
 * practice id and license key, so no host has to be issued anything new to migrate off the
 * JSON interface. SMART-on-FHIR / OAuth 2.0 is the upgrade path, and slots in here without
 * touching anything downstream of {@code CredentialsResolver}.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager)
			throws Exception {
		return http
				// No browser clients and no cookies: every request carries its own credentials.
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authenticationManager(authenticationManager)
				.authorizeHttpRequests(requests -> requests
						// CapabilityStatement discovery is unauthenticated: integrators need to
						// read what the server supports before they have credentials for it.
						.requestMatchers(FhirConfig.FHIR_BASE + "/metadata").permitAll()
						// The CapabilityStatement links each operation to an OperationDefinition,
						// so those links have to be readable by the same anonymous integrator.
						// Read-only metadata about the interface, not about any patient.
						.requestMatchers(HttpMethod.GET, FhirConfig.FHIR_BASE + "/OperationDefinition/**").permitAll()
						.requestMatchers("/actuator/health/**").permitAll()
						.anyRequest().authenticated())
				.httpBasic(Customizer.withDefaults())
				.build();
	}

	@Bean
	AuthenticationManager authenticationManager(PassThroughAuthenticationProvider provider) {
		ProviderManager manager = new ProviderManager(List.of(provider));
		// The license key has to survive authentication: it is forwarded to Prescriptor.
		manager.setEraseCredentialsAfterAuthentication(false);

		return manager;
	}
}
