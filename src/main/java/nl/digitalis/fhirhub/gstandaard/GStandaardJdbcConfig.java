package nl.digitalis.fhirhub.gstandaard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariDataSource;

/**
 * The read-only connection to the G-Standaard {@code gstandaard_views} database.
 *
 * <p>Built here rather than by Spring's DataSource auto-configuration, which stays excluded
 * (see {@code FhirHubApplication}): the properties live under {@code gstandaard.datasource}
 * rather than {@code spring.datasource}, because this is a reference lookup and not the
 * application's own store. fhir-hub holds no session state and writes nothing.
 *
 * <p>The pool is deliberately small — {@code MedicationCodeResolver} issues one short query per
 * drug while a session is being opened, and nothing else in the application touches JDBC.
 */
@Configuration
public class GStandaardJdbcConfig {

	private static final Logger log = LoggerFactory.getLogger(GStandaardJdbcConfig.class);

	@Bean("gstandaardDataSourceProperties")
	@ConfigurationProperties("gstandaard.datasource")
	public DataSourceProperties gstandaardDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean("gstandaardDataSource")
	@ConfigurationProperties("gstandaard.datasource.hikari")
	public HikariDataSource gstandaardDataSource(
			@Qualifier("gstandaardDataSourceProperties") DataSourceProperties properties) {

		DataSourceBuilder<HikariDataSource> builder = properties.initializeDataSourceBuilder()
				.type(HikariDataSource.class);
		HikariDataSource dataSource = builder.build();
		log.info("G-Standaard datasource configured for {}", dataSource.getJdbcUrl());

		return dataSource;
	}

	@Bean
	public JdbcTemplate gstandaardJdbcTemplate(@Qualifier("gstandaardDataSource") HikariDataSource dataSource) {
		log.info("G-Standaard pool size {} - {}", dataSource.getMinimumIdle(), dataSource.getMaximumPoolSize());

		return new JdbcTemplate(dataSource);
	}
}
