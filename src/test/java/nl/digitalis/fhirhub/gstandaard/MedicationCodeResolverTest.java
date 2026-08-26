package nl.digitalis.fhirhub.gstandaard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import nl.digitalis.fhirhub.model.CodedItem;
import nl.digitalis.fhirhub.model.MedicationCodes;

/** Exercises the medcode lookup against H2 standing in for the G-Standaard database. */
class MedicationCodeResolverTest {

	private static MedicationCodeResolver resolver;

	@BeforeAll
	static void setUp() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				"jdbc:h2:mem:medcode-test;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:gstandaard-medcode.sql'",
				"sa", "");

		resolver = new MedicationCodeResolver(new JdbcTemplate(dataSource));
	}

	@Test
	void resolvesAPrkToItsGpk() {
		MedicationCodes codes = resolver.resolve(List.of(new CodedItem("PRK", "18996"))).getFirst();

		assertThat(codes.prk()).isEqualTo(18996);
		assertThat(codes.gpk()).isEqualTo(111111);
		// HPK is only reported when the host identified the drug at that level.
		assertThat(codes.hpk()).isNull();
	}

	@Test
	void resolvesAnHpkToItsPrkAndGpk() {
		MedicationCodes codes = resolver.resolve(List.of(new CodedItem("HPK", "2106"))).getFirst();

		assertThat(codes.prk()).isEqualTo(43800);
		assertThat(codes.gpk()).isEqualTo(222222);
		assertThat(codes.hpk()).isEqualTo(2106);
	}

	@Test
	void resolvesSeveralDrugsInOrder() {
		List<MedicationCodes> codes = resolver.resolve(List.of(
				new CodedItem("PRK", "18996"),
				new CodedItem("HPK", "2106")));

		assertThat(codes).extracting(MedicationCodes::prk).containsExactly(18996, 43800);
	}

	/**
	 * An unresolvable drug must abort the session. Dropping it would leave medication
	 * surveillance answering "no interaction" over an incomplete list — a false negative the
	 * prescriber cannot distinguish from a genuine all-clear.
	 */
	@Test
	void refusesADrugThatGStandaardDoesNotKnow() {
		assertThatThrownBy(() -> resolver.resolve(List.of(new CodedItem("PRK", "404040"))))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("medication surveillance");
	}

	@Test
	void refusesAPrkThatHasNoGpk() {
		assertThatThrownBy(() -> resolver.resolve(List.of(new CodedItem("PRK", "99999"))))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("99999");
	}

	@Test
	void refusesANonNumericCode() {
		assertThatThrownBy(() -> resolver.resolve(List.of(new CodedItem("PRK", "not-a-code"))))
				.isInstanceOf(InvalidRequestException.class)
				.hasMessageContaining("not numeric");
	}

	@Test
	void resolvesNothingForAPatientOnNoMedication() {
		assertThat(resolver.resolve(List.of())).isEmpty();
	}
}
