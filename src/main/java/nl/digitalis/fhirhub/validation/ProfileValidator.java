package nl.digitalis.fhirhub.validation;

import java.util.List;

import org.hl7.fhir.instance.model.api.IBaseOperationOutcome;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationOptions;
import ca.uhn.fhir.validation.ValidationResult;

/**
 * Validates a payload against one of the profiles in {@code ig/} and turns a failure into a 400.
 *
 * <p>This runs <em>after</em> the interface's own checks, not instead of them. The hand-written
 * rejections in {@code SessionParametersMapper} say things like "Patient.gender must be 'male',
 * 'female' or 'unknown'; Prescriptor cannot interpret 'other'" — they name the upstream
 * consequence, which the validator cannot know. The validator catches the other half: the
 * elements base FHIR requires that this interface never reads, and a parameter name that is
 * subtly wrong. Losing the specific messages by validating first would be a bad trade.
 *
 * <p>Only {@code ERROR} and {@code FATAL} are rejected. Warnings are expected in normal
 * operation — the G-Standaard code systems cannot be expanded, so every coding in one produces
 * a "could not be validated" note — and treating those as failures would reject every real
 * request.
 */
@Component
public class ProfileValidator {

	private static final Logger log = LoggerFactory.getLogger(ProfileValidator.class);

	private final FhirValidator validator;
	private final FhirContext fhirContext;
	private final boolean enabled;

	public ProfileValidator(FhirValidator validator, FhirContext fhirContext,
			@Value("${fhirhub.validation.enabled:true}") boolean enabled) {
		this.validator = validator;
		this.fhirContext = fhirContext;
		this.enabled = enabled;

		if (!enabled) {
			log.warn("Profile validation is DISABLED. Payloads are accepted without being checked "
					+ "against the profiles; only this interface's own rules apply.");
		}
	}

	/**
	 * @throws InvalidRequestException rendered by HAPI as an OperationOutcome carrying one issue
	 *                                 per validation error, each with its location
	 */
	public void validate(IBaseResource resource, String profileUrl) {
		if (!enabled) {
			return;
		}

		ValidationResult result = validator.validateWithResult(resource,
				new ValidationOptions().addProfile(profileUrl));

		List<SingleValidationMessage> errors = result.getMessages().stream()
				.filter(message -> message.getSeverity() == ResultSeverityEnum.ERROR
						|| message.getSeverity() == ResultSeverityEnum.FATAL)
				.toList();

		if (errors.isEmpty()) {
			return;
		}

		log.info("Rejected a payload failing {}: {} error(s), first at {}",
				profileUrl, errors.size(), errors.getFirst().getLocationString());

		// The OperationOutcome is built from the errors alone, so the caller is not handed a
		// page of warnings about code systems they cannot do anything about.
		IBaseOperationOutcome outcome =
				new ValidationResult(fhirContext, errors).toOperationOutcome();

		throw new InvalidRequestException(
				"Payload does not conform to " + profileUrl + ": " + errors.getFirst().getMessage(),
				outcome);
	}
}
