package nl.digitalis.fhirhub.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.hl7.fhir.r4.model.Narrative;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;

/**
 * A host may put a narrative on any resource it sends, and validating one must not need
 * {@code org.ogce:xpp3}.
 *
 * <p>That jar is excluded in the POM, and it is the exclusion with the least margin. Its
 * {@code org.xmlpull} classes are referenced two thousand times across the validator, almost all
 * of it from {@code org.hl7.fhir.r5.formats.XmlParser} — which parses R5 XML <em>text</em>, and
 * is not how anything gets parsed here. HAPI reads inbound XML with StAX, the validator walks the
 * element model over DOM, and {@code XhtmlParser} has both a DOM and a pull-parser entry point,
 * of which only the first is reached. The exclusion is worth the care because xpp3 is an
 * abandoned project that declares no licence and drags in {@code jakarta-regexp}, which declares
 * none either — two SOUP items that cost more to document than the 0.4 MB they occupy.
 *
 * <p>So this pins the claim rather than the bytes: if a future HAPI routes narrative or XML
 * parsing through the pull parser, this fails with a {@code NoClassDefFoundError} here instead of
 * on a host's request.
 */
@SpringBootTest
class XhtmlNarrativeParsingTest {

	private static final String DIV =
			"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>Jan de <b>Vries</b></p></div>";

	@Autowired
	private FhirValidator validator;

	@Autowired
	private FhirContext fhirContext;

	@Test
	void validatesAResourceCarryingANarrative() {
		assertThatCode(() -> validator.validateWithResult(parametersWithNarrative()))
				.doesNotThrowAnyException();
	}

	@Test
	void readsAndWritesTheSameNarrativeAsXml() {
		String xml = fhirContext.newXmlParser().encodeResourceToString(parametersWithNarrative());

		Parameters reparsed =
				fhirContext.newXmlParser().parseResource(Parameters.class, xml);

		assertThat(reparsed.getParameterFirstRep().getResource())
				.asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(Patient.class))
				.extracting(patient -> patient.getText().getDivAsString())
				.asString()
				.contains("Vries");
	}

	private Parameters parametersWithNarrative() {
		Narrative text = new Narrative();
		text.setStatus(Narrative.NarrativeStatus.GENERATED);
		text.setDivAsString(DIV);

		Patient patient = new Patient();
		patient.setId("p1");
		patient.setText(text);

		Parameters parameters = new Parameters();
		parameters.addParameter().setName("patient").setResource(patient);

		return parameters;
	}
}
