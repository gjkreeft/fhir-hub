package nl.digitalis.fhirhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import nl.digitalis.fhirhub.config.PrescriptorProperties;

/**
 * FHIR R4 interface for Digitalis Prescriptor 3.
 *
 * <p>Spring's own DataSource auto-configuration stays excluded: {@code GStandaardJdbcConfig}
 * builds its own named datasource from {@code gstandaard.datasource}, and letting Boot also
 * look for a primary one would only produce a startup failure over a
 * {@code spring.datasource.url} that is deliberately not set.
 *
 * <p>fhir-hub still holds no session state. The database is a read-only reference lookup.
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(PrescriptorProperties.class)
public class FhirHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(FhirHubApplication.class, args);
	}
}
