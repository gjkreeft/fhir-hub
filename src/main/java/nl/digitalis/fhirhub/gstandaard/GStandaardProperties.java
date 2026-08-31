package nl.digitalis.fhirhub.gstandaard;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection settings for the read-only G-Standaard database.
 *
 * <p>Under {@code gstandaard.datasource} rather than {@code spring.datasource}, because this is a
 * reference lookup and not the application's own store — fhir-hub holds no session state and
 * writes nothing. Deployment configuration is the house one either way.
 *
 * <p>{@code hikari} names the two pool settings this application sets. It is a fixed pair rather
 * than a free-form map on purpose: Hikari's own {@code HikariConfig(Properties)} takes camelCase
 * keys, so a kebab-case {@code maximum-pool-size} handed to it is silently ignored rather than
 * rejected, and a pool that quietly kept its default size is exactly the kind of failure that
 * shows up as a timeout under load months later. Another knob means another field here.
 *
 * @param url      JDBC URL of the {@code gstandaard_views} database
 * @param username read-only account
 * @param password its password
 * @param hikari   the pool sizing; small by design, see {@link GStandaardJdbcConfig}
 */
@ConfigurationProperties(prefix = "gstandaard.datasource")
public record GStandaardProperties(
		String url,
		String username,
		String password,
		Pool hikari) {

	public GStandaardProperties {
		if (url == null || url.isBlank()) {
			throw new IllegalStateException("gstandaard.datasource.url must be set");
		}

		hikari = hikari == null ? new Pool(null, null) : hikari;
	}

	/**
	 * @param maximumPoolSize connections at full tilt; one short query per drug in a session
	 * @param minimumIdle     0 keeps nothing open between sessions, which is the normal state
	 */
	public record Pool(Integer maximumPoolSize, Integer minimumIdle) {

		public Pool {
			maximumPoolSize = maximumPoolSize == null ? 3 : maximumPoolSize;
			minimumIdle = minimumIdle == null ? 0 : minimumIdle;
		}
	}
}
