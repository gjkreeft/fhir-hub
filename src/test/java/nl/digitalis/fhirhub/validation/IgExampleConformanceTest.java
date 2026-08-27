package nl.digitalis.fhirhub.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.ValidationOptions;
import ca.uhn.fhir.validation.ValidationResult;

/**
 * Every example in the IG has to satisfy the profile it claims.
 *
 * <p>The examples are the Implementation Guide's payloads written as instances, so that the
 * documentation cannot drift away from the profiles. Nothing else checks them: SUSHI converts FSH
 * to JSON and validates nothing, so without this test an example can contradict both the profile
 * it claims and the payload the service actually builds — a coding at the wrong level, say — and
 * the build stays green.
 *
 * <p>The examples are read off disk rather than the classpath because the build deliberately
 * copies only the conformance resources into the jar — an example is documentation, not something
 * the runtime validator should resolve. {@code ig/fsh-generated} is committed, so the path is
 * stable; {@code IgCanonicalsTest} reads {@code ig/input/fsh} the same way.
 */
@SpringBootTest
class IgExampleConformanceTest {

	private static final Path RESOURCES = Path.of("ig", "fsh-generated", "resources");

	/** Everything SUSHI emits that is a definition rather than an example. */
	private static final List<String> CONFORMANCE = List.of(
			"StructureDefinition", "ValueSet", "CodeSystem", "ImplementationGuide");

	@Autowired
	private FhirValidator validator;

	@Autowired
	private FhirContext fhirContext;

	@Test
	void everyExampleSatisfiesTheProfileItClaims() throws IOException {
		List<IBaseResource> examples = examples();

		// A glob that matched nothing would otherwise pass this test in silence.
		assertThat(examples)
				.as("examples found under %s — run 'npx sushi .' in ig/ if this is empty", RESOURCES)
				.hasSizeGreaterThanOrEqualTo(3);

		for (IBaseResource example : examples) {
			for (String profile : example.getMeta().getProfile().stream().map(Object::toString).toList()) {
				assertThat(errorsIn(example, profile))
						.as("%s against %s", example.getIdElement().getIdPart(), profile)
						.isEmpty();
			}
		}
	}

	/** Each example declares the profile it is an instance of, so nothing has to be mapped here. */
	@Test
	void everyExampleDeclaresTheProfileItIsAnInstanceOf() throws IOException {
		for (IBaseResource example : examples()) {
			assertThat(example.getMeta().getProfile())
					.as("%s declares a meta.profile", example.getIdElement().getIdPart())
					.isNotEmpty();
		}
	}

	private List<IBaseResource> examples() throws IOException {
		List<IBaseResource> examples = new ArrayList<>();
		try (Stream<Path> files = Files.list(RESOURCES)) {
			for (Path file : files.sorted().toList()) {
				if (CONFORMANCE.stream().anyMatch(type -> file.getFileName().toString().startsWith(type + "-"))) {
					continue;
				}

				try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					examples.add(fhirContext.newJsonParser().parseResource(reader));
				}
			}
		}

		return examples;
	}

	private List<String> errorsIn(IBaseResource resource, String profile) {
		ValidationResult result = validator.validateWithResult(resource,
				new ValidationOptions().addProfile(profile));

		return result.getMessages().stream()
				.filter(message -> message.getSeverity() == ResultSeverityEnum.ERROR
						|| message.getSeverity() == ResultSeverityEnum.FATAL)
				.map(message -> message.getLocationString() + ": " + message.getMessage())
				.toList();
	}
}
