package nl.digitalis.fhirhub.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import ca.uhn.fhir.context.FhirContext;
import nl.digitalis.fhirhub.auth.BasicAuthenticationFilter;

/**
 * Registers HTTP Basic authentication in front of the FHIR servlets.
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
 * <p>One registration per FHIR base, each scoped to its own path, because the paths a base leaves
 * unauthenticated are relative to that base: {@code /fhir/evs/metadata} and
 * {@code /fhir/surveillance/metadata} are two different discovery documents and each has to be
 * readable by an integrator who does not hold credentials yet. A single filter over
 * {@code /fhir/*} would have to be told the list of bases to keep that right, which is the same
 * fact written in one more place.
 *
 * <p>Note the consequence of scoping to the bases: a path outside them is a 404 from the container
 * rather than a 401, where Spring Security's {@code anyRequest().authenticated()} challenged first
 * and answered later.
 */
@Configuration
public class SecurityConfig {

	@Bean
	FilterRegistrationBean<BasicAuthenticationFilter> basicAuthentication(FhirContext fhirContext) {
		return filter(fhirContext, FhirConfig.EVS_BASE, "basic-authentication");
	}

	@Bean
	FilterRegistrationBean<BasicAuthenticationFilter> surveillanceBasicAuthentication(FhirContext fhirContext) {
		return filter(fhirContext, FhirConfig.SURVEILLANCE_BASE, "basic-authentication-surveillance");
	}

	private FilterRegistrationBean<BasicAuthenticationFilter> filter(FhirContext fhirContext, String base,
			String name) {
		FilterRegistrationBean<BasicAuthenticationFilter> registration =
				new FilterRegistrationBean<>(new BasicAuthenticationFilter(fhirContext, base));

		registration.addUrlPatterns(base + "/*");
		registration.setName(name);

		return registration;
	}
}
