package nl.digitalis.fhirhub.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.server.RestfulServer;
import ca.uhn.fhir.rest.server.interceptor.ResponseHighlighterInterceptor;
import jakarta.servlet.annotation.WebServlet;
import nl.digitalis.fhirhub.server.BaseProvider;

/** Wires the HAPI FHIR server and the HTTP client that talks to Prescriptor. */
@Configuration
public class FhirConfig {

	public static final String FHIR_BASE = "/fhir";

	/**
	 * A FhirContext is expensive to build and cheap to share; one per application is the
	 * documented HAPI pattern.
	 */
	@Bean
	FhirContext fhirContext() {
		return FhirContext.forR4();
	}

	@Bean
	ServletRegistrationBean<RestfulServer> fhirServlet(FhirContext fhirContext, List<BaseProvider> providers) {
		RestfulServer server = new FhirHubRestfulServer(fhirContext, providers);

		ServletRegistrationBean<RestfulServer> registration =
				new ServletRegistrationBean<>(server, FHIR_BASE + "/*");
		registration.setName("fhir-hub");
		registration.setLoadOnStartup(1);

		return registration;
	}

	@Bean
	RestClient prescriptorRestClient(PrescriptorProperties properties) {
		return restClient(properties.targetUrl().toString(),
				properties.connectTimeout(), properties.readTimeout());
	}

	private RestClient restClient(String baseUrl, Duration connectTimeout, Duration readTimeout) {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(connectTimeout);
		factory.setReadTimeout(readTimeout);

		return RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory((ClientHttpRequestFactory) factory)
				.build();
	}

	/** The HAPI plain server, configured for R4 with JSON as the default encoding. */
	@WebServlet
	static class FhirHubRestfulServer extends RestfulServer {

		private static final long serialVersionUID = 1L;

		FhirHubRestfulServer(FhirContext fhirContext, List<BaseProvider> providers) {
			super(fhirContext);
			registerProviders(providers);
			setDefaultResponseEncoding(EncodingEnum.JSON);
			// Integrators debugging a payload should get readable output without asking.
			setDefaultPrettyPrint(true);
			// A browser ranks text/html above application/xml, so without this the
			// CapabilityStatement rendered as raw XML. Machine clients are unaffected:
			// the interceptor only engages on Accept: text/html or _format=html.
			registerInterceptor(new ResponseHighlighterInterceptor());
		}
	}
}
