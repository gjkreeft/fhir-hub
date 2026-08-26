package nl.digitalis.fhirhub.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import org.hl7.fhir.r4.model.Dosage;
import org.junit.jupiter.api.Test;

import nl.digitalis.fhirhub.model.Directions;

class T25DosageMapperTest {

	private final T25DosageMapper mapper = new T25DosageMapper();

	@Test
	void carriesTheFreeTextAndTheCodedStringVerbatim() {
		Dosage dosage = mapper.toDosage(new Directions(
				"tabel25", "3-4D1S; gedurende max. 1 maand", "3-4 keer per dag 1 zetpil"));

		assertThat(dosage.getText()).isEqualTo("3-4 keer per dag 1 zetpil");
		assertThat(dosage.getExtensionByUrl(DigitalisExtensions.CODED_DIRECTIONS).getValue().primitiveValue())
				.isEqualTo("3-4D1S; gedurende max. 1 maand");
	}

	/**
	 * The coded string is passed through, never decoded. This is the whole contract of this
	 * mapper: see the class Javadoc for why no {@code timing} or {@code doseAndRate} is derived,
	 * and what would have to be true to change that.
	 */
	@Test
	void derivesNoStructureFromTheCodedString() {
		Dosage dosage = mapper.toDosage(new Directions("tabel25", "3-4D1S", "drie tot vier maal daags"));

		assertThat(dosage.hasTiming()).isFalse();
		assertThat(dosage.hasDoseAndRate()).isFalse();
	}

	/**
	 * A string with no numeric structure at all is treated exactly like one that has it —
	 * there is no parse to succeed or fail, so there is no second code path.
	 */
	@Test
	void treatsAnUnstructurableStringLikeAnyOther() {
		Dosage dosage = mapper.toDosage(new Directions("tabel25", "ZO NODIG", "zo nodig"));

		assertThat(dosage.getExtensionByUrl(DigitalisExtensions.CODED_DIRECTIONS).getValue().primitiveValue())
				.isEqualTo("ZO NODIG");
		assertThat(dosage.getText()).isEqualTo("zo nodig");
		assertThat(dosage.hasTiming()).isFalse();
	}

	@Test
	void emitsTheTextAloneWhenThereIsNoCodedString() {
		Dosage dosage = mapper.toDosage(new Directions("tabel25", null, "zo nodig"));

		assertThat(dosage.getText()).isEqualTo("zo nodig");
		assertThat(dosage.getExtensionByUrl(DigitalisExtensions.CODED_DIRECTIONS)).isNull();
	}

	@Test
	void emitsTheCodedStringAloneWhenThereIsNoText() {
		Dosage dosage = mapper.toDosage(new Directions("tabel25", "3D1T", null));

		assertThat(dosage.hasText()).isFalse();
		assertThat(dosage.getExtensionByUrl(DigitalisExtensions.CODED_DIRECTIONS).getValue().primitiveValue())
				.isEqualTo("3D1T");
	}

	@Test
	void toleratesAbsentDirections() {
		assertThat(mapper.toDosage(null).isEmpty()).isTrue();
		assertThat(mapper.toDosage(new Directions("tabel25", null, null)).isEmpty()).isTrue();
	}
}
