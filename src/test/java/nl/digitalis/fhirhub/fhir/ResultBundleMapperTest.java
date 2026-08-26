package nl.digitalis.fhirhub.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Communication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.junit.jupiter.api.Test;

import nl.digitalis.fhirhub.model.AdviceResult;
import nl.digitalis.fhirhub.model.Directions;
import nl.digitalis.fhirhub.model.DrugCode;
import nl.digitalis.fhirhub.model.DrugResult;
import nl.digitalis.fhirhub.model.SessionResult;

class ResultBundleMapperTest {

	private final ResultBundleMapper mapper =
			new ResultBundleMapper(new CodeSystemRegistry(), new T25DosageMapper());

	@Test
	void mapsADrugOntoAMedicationRequest() {
		Bundle bundle = mapper.toBundle(new SessionResult(List.of(paracetamol()), List.of()));

		assertThat(bundle.getType()).isEqualTo(BundleType.COLLECTION);

		MedicationRequest request = (MedicationRequest) bundle.getEntryFirstRep().getResource();
		CodeableConcept medication = (CodeableConcept) request.getMedication();

		assertThat(medication.getText()).isEqualTo("PARACETAMOL ZETPIL 1000MG");
		assertThat(medication.getCoding()).extracting("system", "code")
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(Systems.PRK, "18996"),
						org.assertj.core.groups.Tuple.tuple(Systems.ATC, "N02BE01"));
	}

	@Test
	void mapsSupplyOntoTheDispenseRequest() {
		Bundle bundle = mapper.toBundle(new SessionResult(List.of(paracetamol()), List.of()));
		MedicationRequest request = (MedicationRequest) bundle.getEntryFirstRep().getResource();

		assertThat(request.getDispenseRequest().getQuantity().getValue().intValue()).isEqualTo(15);
		// "ST" is a G-Standaard basiseenheid, not UCUM.
		assertThat(request.getDispenseRequest().getQuantity().getSystem())
				.isEqualTo(Systems.G_STANDAARD_BASISEENHEID);
		assertThat(request.getDispenseRequest().getExpectedSupplyDuration().getValue().intValue()).isEqualTo(4);
		assertThat(request.getDispenseRequest().getExpectedSupplyDuration().getCode()).isEqualTo("d");
	}

	/**
	 * MedicationRequest.subject is mandatory but a stateless session result carries no patient
	 * identity, so the standard data-absent-reason idiom is used rather than an invented
	 * reference.
	 */
	@Test
	void marksTheSubjectAsAbsentRatherThanInventingOne() {
		Bundle bundle = mapper.toBundle(new SessionResult(List.of(paracetamol()), List.of()));
		MedicationRequest request = (MedicationRequest) bundle.getEntryFirstRep().getResource();

		assertThat(request.getSubject().getReference()).isNull();
		assertThat(request.getSubject()
				.getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/data-absent-reason")
				.getValue().primitiveValue()).isEqualTo("unknown");
	}

	@Test
	void carriesTheOpiumActClassificationAsACodeNotABoolean() {
		Bundle bundle = mapper.toBundle(new SessionResult(List.of(oxycodon()), List.of()));
		MedicationRequest request = (MedicationRequest) bundle.getEntryFirstRep().getResource();

		CodeableConcept classification = (CodeableConcept) request
				.getExtensionByUrl(DigitalisExtensions.OPIUM_ACT_CLASSIFICATION).getValue();

		assertThat(classification.getCodingFirstRep().getSystem())
				.isEqualTo(Systems.G_STANDAARD_BIJZONDER_KENMERK);
		assertThat(classification.getCodingFirstRep().getCode()).isEqualTo("2");
	}

	@Test
	void omitsTheOpiumExtensionForOrdinaryDrugs() {
		Bundle bundle = mapper.toBundle(new SessionResult(List.of(paracetamol()), List.of()));
		MedicationRequest request = (MedicationRequest) bundle.getEntryFirstRep().getResource();

		assertThat(request.getExtensionByUrl(DigitalisExtensions.OPIUM_ACT_CLASSIFICATION)).isNull();
	}

	/** The text/plain versus text/uri-list distinction becomes the choice of payload type. */
	@Test
	void mapsProseAdviceToAStringAndLinkAdviceToAnAttachment() {
		Bundle bundle = mapper.toBundle(new SessionResult(List.of(), List.of(
				new AdviceResult("spreid de activiteiten over de dag", false),
				new AdviceResult("https://www.thuisarts.nl/pijn", true))));

		Communication prose = (Communication) bundle.getEntry().get(0).getResource();
		Communication link = (Communication) bundle.getEntry().get(1).getResource();

		assertThat(prose.getPayloadFirstRep().getContent().primitiveValue())
				.isEqualTo("spreid de activiteiten over de dag");

		Attachment attachment = (Attachment) link.getPayloadFirstRep().getContent();
		assertThat(attachment.getContentType()).isEqualTo("text/uri-list");
		assertThat(attachment.getUrl()).isEqualTo("https://www.thuisarts.nl/pijn");
	}

	private DrugResult paracetamol() {
		return new DrugResult(
				List.of(new DrugCode("PRK", 18996, "PARACETAMOL ZETPIL 1000MG", new BigDecimal("15"), "ST",
						new Directions("tabel25", "3-4D1S", "3-4 keer per dag 1 zetpil"))),
				4, false, "PARACETAMOL ZETPIL 1000MG", "N02BE01");
	}

	private DrugResult oxycodon() {
		return new DrugResult(
				List.of(new DrugCode("HPK", 2106, "OXYCODON HCL TABLET 5MG", new BigDecimal("30"), "ST",
						new Directions("tabel25", "3D1T", "3 keer per dag 1 tablet"))),
				10, true, "OXYCODON HCL TABLET 5MG", "N02AA05");
	}
}
