package nl.digitalis.fhirhub.gstandaard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * The read-only connection to the G-Standaard {@code gstandaard_views} database.
 *
 * <p>Hikari is configured directly rather than through Spring's {@code DataSourceBuilder}. The
 * builder's job is choosing a pool implementation from what happens to be on the classpath and
 * binding {@code spring.datasource.*}; neither applies here — the pool is Hikari by name and the
 * properties are this application's own, because a reference lookup is not the application's
 * store. Going direct took {@code spring-boot-starter-jdbc} off the tree with it.
 *
 * <p>The pool is deliberately small: {@code MedicationCodeResolver} issues one short query per
 * drug while a session is being opened, and nothing else in the application touches JDBC.
 *
 * <p>Read-only is a statement about this application's behaviour as well as a pool setting:
 * nothing here issues anything but {@code SELECT}, and the account it connects with should be
 * read-only too.
 *
 * <p>One bean, not two. {@code HikariDataSource} is already a {@code DataSource}, so
 * {@code MedicationCodeResolver} can take the interface and stay ignorant of the pool; adding a
 * second bean to "expose the interface" only gives the container two candidates of the same type
 * to choose between, and it refuses.
 */
@Configuration
public class GStandaardJdbcConfig {

	private static final Logger log = LoggerFactory.getLogger(GStandaardJdbcConfig.class);

	@Bean(destroyMethod = "close")
	public HikariDataSource gstandaardDataSource(GStandaardProperties properties) {
		HikariConfig config = new HikariConfig();
		config.setPoolName("gstandaard");
		config.setJdbcUrl(properties.url());
		config.setUsername(properties.username());
		config.setPassword(properties.password());
		config.setMaximumPoolSize(properties.hikari().maximumPoolSize());
		config.setMinimumIdle(properties.hikari().minimumIdle());
		// Nothing here writes, so a connection handed back mid-transaction is a bug rather than
		// something to roll back quietly. Autocommit keeps that honest.
		config.setAutoCommit(true);
		config.setReadOnly(true);

		HikariDataSource dataSource = new HikariDataSource(config);
		log.info("G-Standaard datasource configured for {} (pool {} - {})",
				properties.url(), config.getMinimumIdle(), config.getMaximumPoolSize());

		return dataSource;
	}
}
