package nl.digitalis.fhirhub.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ca.uhn.fhir.context.FhirContext;
import nl.digitalis.fhirhub.auth.BasicAuthenticationFilter;

/**
 * Registers HTTP Basic authentication in front of the FHIR servlet.
 *
 * <p>A servlet filter rather than Spring Security. The authentication this interface performs is
 * "is there a well-formed practice id and license key on the request" — Prescriptor is the
 * authority on whether they are a real pair — so there is no user store, no roles, no session and
 * no CSRF surface. Spring Security modelled that in an {@code AuthenticationProvider}, a
 * {@code ProviderManager} with credential erasure switched off, a filter chain and a
 * {@code SecurityContextHolder}, all to carry two strings from a header to
 * {@code CredentialsResolver}. The filter does the same in one class you can read end to end, and
 * takes {@code spring-boot-starter-security} and its 6 jars off the tree with it.
 *
 * <p>Scoped to the FHIR base, which is everything this application serves. Note the consequence:
 * a path outside it is now a 404 from the container rather than a 401, where Spring Security's
 * {@code anyRequest().authenticated()} challenged first and answered later.
 */
@Configuration
public class SecurityConfig {

	@Bean
	FilterRegistrationBean<BasicAuthenticationFilter> basicAuthentication(FhirContext fhirContext) {
		FilterRegistrationBean<BasicAuthenticationFilter> registration = new FilterRegistrationBean<>(
				new BasicAuthenticationFilter(fhirContext, FhirConfig.FHIR_BASE));

		registration.addUrlPatterns(FhirConfig.FHIR_BASE + "/*");
		registration.setName("basic-authentication");

		return registration;
	}
}
