package nl.digitalis.fhirhub.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the upstream Prescriptor XML-RPC endpoint.
 *
 * @param targetUrl      the XML-RPC dispatch endpoint; validated at startup
 * @param connectTimeout how long to wait for a connection to Prescriptor
 * @param readTimeout    how long to wait for a response; sessions can take a moment to open
 */
@ConfigurationProperties(prefix = "prescriptor")
public record PrescriptorProperties(
		URI targetUrl,
		Duration connectTimeout,
		Duration readTimeout) {

	public PrescriptorProperties {
		if (targetUrl == null || targetUrl.getHost() == null) {
			throw new IllegalStateException(
					"prescriptor.target-url must be set to an absolute URL");
		}

		connectTimeout = connectTimeout == null ? Duration.ofSeconds(10) : connectTimeout;
		readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
	}
}
