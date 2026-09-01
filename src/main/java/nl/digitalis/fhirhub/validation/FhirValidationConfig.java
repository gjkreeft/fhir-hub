package nl.digitalis.fhirhub.validation;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.UnknownCodeSystemWarningValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationOptions;
import nl.digitalis.fhirhub.fhir.Profiles;

/**
 * The validator that enforces the profiles in {@code ig/} at runtime.
 *
 * <p>The profiles are loaded from the classpath rather than fetched: {@code ig/fsh-generated}
 * is copied into the jar under {@code fhir-profiles/} by the build, so this service resolves
 * every canonical it needs locally and makes no network call. That matters — a validator that
 * reaches out to a terminology server would put an external dependency in the path of every
 * request, and this one is stateless by design.
 *
 * <p>The chain order is deliberate. The IG comes first so its profiles win, and
 * {@link SnapshotGeneratingValidationSupport} is present because SUSHI emits differentials
 * only — without it every profile resolves to an empty element list and validation silently
 * passes everything. {@link UnknownCodeSystemWarningValidationSupport} is last and turns an
 * unresolvable code system into a warning rather than an error, which is required here: the
 * G-Standaard code systems are {@code content: not-present} and the national OID systems are
 * not distributed at all, so their codes can never be checked. Without it, every real payload
 * would fail on terminology this interface has no way to resolve.
 */
@Configuration
public class FhirValidationConfig {

	private static final Logger log = LoggerFactory.getLogger(FhirValidationConfig.class);

	private static final String PROFILE_LOCATION = "classpath:fhir-profiles/*.json";

	@Bean
	public FhirValidator fhirValidator(FhirContext fhirContext) throws IOException {
		// No CachingValidationSupport wrapper: ValidationSupportChain caches on its own since
		// HAPI 8, with CacheConfiguration.defaultValues(), and the wrapper is deprecated for
		// removal. It still resolves its cache through the ServiceLoader, so
		// hapi-fhir-caching-caffeine stays a dependency — see the POM.
		IValidationSupport chain = new ValidationSupportChain(
				igProfiles(fhirContext),
				new DefaultProfileValidationSupport(fhirContext),
				new InMemoryTerminologyServerValidationSupport(fhirContext),
				new CommonCodeSystemsTerminologyService(fhirContext),
				new SnapshotGeneratingValidationSupport(fhirContext),
				unknownCodeSystemsAreAWarning(fhirContext));

		FhirValidator validator = fhirContext.newValidator();
		validator.registerValidatorModule(new FhirInstanceValidator(chain));

		return validator;
	}

	/**
	 * Constructing this support is not enough — its default severity is an error, which is what
	 * every real payload would hit. The G-Standaard systems are {@code content: not-present} and
	 * the national OID systems are not distributed at all, so their codes can never be looked
	 * up. Downgrading to a warning is what lets a {@code required} binding pass on a system
	 * whose codes are unknowable, while still failing a coding from a system the profile does
	 * not list.
	 */
	private UnknownCodeSystemWarningValidationSupport unknownCodeSystemsAreAWarning(FhirContext fhirContext) {
		UnknownCodeSystemWarningValidationSupport support =
				new UnknownCodeSystemWarningValidationSupport(fhirContext);
		support.setNonExistentCodeSystemSeverity(IValidationSupport.IssueSeverity.WARNING);

		return support;
	}

	/**
	 * Validates a throwaway payload at startup so that no integrator pays for the warm-up.
	 *
	 * <p>The first validation costs roughly 4.5 seconds — the reference validator builds its
	 * structure maps and expands what terminology it can on first use — against about 70 ms once
	 * warm. Without this, the first request after every deploy carries that spike, which for a
	 * synchronous session-opening call is the difference between "slow" and "the host timed out".
	 *
	 * <p>Deliberately not fatal. A failure here means the warm-up payload is wrong, not that the
	 * service is broken: real requests would still validate correctly, just slowly.
	 */
	@Bean
	public ApplicationRunner warmUpValidator(FhirValidator validator) {
		return args -> {
			long startedAt = System.nanoTime();
			try {
				Parameters probe = new Parameters();
				probe.addParameter().setName("xisId").setValue(new StringType("warm-up"));
				validator.validateWithResult(probe,
						new ValidationOptions().addProfile(Profiles.FORMULARY_SESSION_INPUT));

				log.info("Validator warmed up in {} ms", (System.nanoTime() - startedAt) / 1_000_000);
			}
			catch (RuntimeException e) {
				log.warn("Validator warm-up failed; the first request will pay for it instead", e);
			}
		};
	}

	private PrePopulatedValidationSupport igProfiles(FhirContext fhirContext) throws IOException {
		PrePopulatedValidationSupport support = new PrePopulatedValidationSupport(fhirContext);
		Resource[] profiles = new PathMatchingResourcePatternResolver()
				.getResources(PROFILE_LOCATION);

		if (profiles.length == 0) {
			// The jar was built without ig/fsh-generated. Failing here rather than starting up
			// is the point: a validator with no profiles accepts everything, so the service
			// would look healthy while enforcing nothing at all.
			throw new IllegalStateException(
					"No profiles found at " + PROFILE_LOCATION + ". Run 'npm run sushi' in ig/ and rebuild.");
		}

		for (Resource profile : profiles) {
			try (Reader reader = new InputStreamReader(profile.getInputStream(), StandardCharsets.UTF_8)) {
				IBaseResource parsed = fhirContext.newJsonParser().parseResource(reader);
				support.addResource(parsed);
			}
		}

		log.info("Loaded {} conformance resources from {}", profiles.length, PROFILE_LOCATION);

		return support;
	}
}
