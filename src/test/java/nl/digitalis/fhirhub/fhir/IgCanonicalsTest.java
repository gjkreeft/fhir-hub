package nl.digitalis.fhirhub.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * The IG under {@code ig/} and the constants in this package have to agree on every canonical
 * URL, because they meet only on the wire.
 *
 * <p>SUSHI derives a canonical as {@code {canonical}/StructureDefinition/{Id}}, so the FSH ids
 * are load-bearing: renaming one is a silent breaking change for every payload already in the
 * field, and nothing else in either build would notice. This test is the thing that notices.
 */
class IgCanonicalsTest {

	private static final Path FSH = Path.of("ig", "input", "fsh");

	private static final String CANONICAL = "http://spec.digitalis.nl/fhir";

	@Test
	void theIgDefinesTheExtensionsAtTheUrlsThisServiceEmits() throws IOException {
		String extensions = read("extensions.fsh");

		assertThat(idIn(extensions, DigitalisExtensions.CODED_DIRECTIONS))
				.as("CodedDirections is declared in the IG at the URL fhir-hub emits")
				.isTrue();
		assertThat(idIn(extensions, DigitalisExtensions.OPIUM_ACT_CLASSIFICATION))
				.as("OpiumActClassification is declared in the IG at the URL fhir-hub emits")
				.isTrue();
	}

	@Test
	void theIgDefinesTheDigitalisCodeSystem() throws IOException {
		assertThat(idIn(read("terminology.fsh"), Systems.G_STANDAARD_BIJZONDER_KENMERK)).isTrue();
	}

	/**
	 * The national OIDs have to be the ones in the value sets, or the profiles would bind
	 * something other than what the service emits.
	 */
	@Test
	void theIgBindsTheNationalGStandaardOids() throws IOException {
		String terminology = read("terminology.fsh");

		assertThat(terminology)
				.contains(Systems.G_STANDAARD_SSK)
				.contains(Systems.G_STANDAARD_SNK)
				.contains(Systems.G_STANDAARD_OGGRP)
				.contains(Systems.G_STANDAARD_CONTRA_INDICATIE);
	}

	/** The OIDs are quoted into the profiles by hand, so they are worth pinning too. */
	@Test
	void theIgUsesTheSameOidsAsSystems() throws IOException {
		String terminology = read("terminology.fsh");

		assertThat(terminology)
				.contains(Systems.ICPC_1_NL)
				.contains(Systems.PRK)
				.contains(Systems.HPK)
				.contains(Systems.GPK)
				.contains(Systems.ATC);
	}

	/**
	 * The IG's canonical and the constants have to name the same host. SUSHI derives every URL
	 * from sushi-config.yaml, so a canonical edited there and nowhere else would silently
	 * republish every artifact at an address this service does not use.
	 */
	@Test
	void theIgCanonicalMatchesTheConstants() throws IOException {
		assertThat(Files.readString(Path.of("ig", "sushi-config.yaml")))
				.contains("canonical: " + CANONICAL);

		assertThat(DigitalisExtensions.CODED_DIRECTIONS).startsWith(CANONICAL + "/");
		assertThat(DigitalisExtensions.OPIUM_ACT_CLASSIFICATION).startsWith(CANONICAL + "/");
		assertThat(Systems.G_STANDAARD_BIJZONDER_KENMERK).startsWith(CANONICAL + "/");
		assertThat(Profiles.FORMULARY_SESSION_INPUT).startsWith(CANONICAL + "/");
		assertThat(Profiles.CREATERX_SESSION_INPUT).startsWith(CANONICAL + "/");
		assertThat(Profiles.SESSION_OUTPUT).startsWith(CANONICAL + "/");
		assertThat(Profiles.RESULT_BUNDLE).startsWith(CANONICAL + "/");
	}

	/** SUSHI turns "Id: x" into {canonical}/StructureDefinition/x — or /CodeSystem/x. */
	private boolean idIn(String fsh, String canonical) {
		String id = canonical.substring(canonical.lastIndexOf('/') + 1);

		return fsh.contains("Id: " + id + "\n");
	}

	private String read(String file) throws IOException {
		Path path = FSH.resolve(file);
		assertThat(path).as("the IG source is where the build expects it").exists();

		return Files.readString(path);
	}
}
