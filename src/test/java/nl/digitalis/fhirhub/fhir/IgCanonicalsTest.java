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
	void theIgDefinesTheDigitalisCodeSystems() throws IOException {
		String terminology = read("terminology.fsh");

		assertThat(idIn(terminology, Systems.G_STANDAARD_BIJZONDER_KENMERK)).isTrue();
		assertThat(idIn(terminology, CodeSystemRegistry.DIGITALIS_SSK)).isTrue();
		assertThat(idIn(terminology, CodeSystemRegistry.DIGITALIS_SNK)).isTrue();
		assertThat(idIn(terminology, CodeSystemRegistry.DIGITALIS_OGGRP)).isTrue();
		assertThat(idIn(terminology, CodeSystemRegistry.DIGITALIS_CI_CODE)).isTrue();
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
				.contains(Systems.NHG_TABEL_45)
				.contains(Systems.ATC);
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
