package nl.digitalis.fhirhub.server;

import java.util.List;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.server.exceptions.NotImplementedOperationException;
import nl.digitalis.fhirhub.fhir.Profiles;
import nl.digitalis.fhirhub.validation.ProfileValidator;

/**
 * Medication surveillance as a direct question: given a patient's context and one or more
 * proposed prescriptions, which signals fire?
 *
 * <p><strong>This operation is not implemented.</strong> A well-formed request is answered with
 * <em>501 Not Implemented</em>. What exists today is the contract around it — the request profile,
 * the generated {@code OperationDefinition}, the CapabilityStatement entry and the published
 * specification — so that integrators can build and review the payload, and so that the shape can
 * be argued about before the rules engine behind it is wired up.
 *
 * <p><strong>Why 501 rather than an empty Bundle of findings.</strong> An empty
 * {@code Bundle} of {@code DetectedIssue} is indistinguishable from a genuine all-clear, and a
 * prescriber who sent a medication list and got no signal reads it as one. That is the same
 * false negative that makes an unresolvable G-Standaard code a 400 rather than a dropped drug —
 * see {@code MedicationCodeResolver} — and it is the reason this stub answers with a status no
 * client can mistake for a result. Do not soften it into a 200 with a warning.
 *
 * <p><strong>The request is validated all the same.</strong> A malformed body is a 400 naming
 * what is wrong with it and a conformant one is a 501, so a host can develop against the real
 * rules today and will not discover its payload was wrong on the day the check goes live. The
 * cost is one validator pass on a request that cannot succeed, which is the point rather than an
 * oversight.
 *
 * <p>What is still undecided, and has to be settled before this returns anything:
 * <ul>
 * <li><b>The upstream.</b> Either {@code prescriptor-api}'s {@code mb/} package, which already
 * does allergy signals, double medication, opium signals and clinical rules, or the Clinical
 * Rules Engine's SOAP service directly. That decision also decides whether the credentials on
 * this base are still Prescriptor's to validate, which is what keeps this service free of a
 * credential store.</li>
 * <li><b>The response.</b> {@code DetectedIssue} is the resource this is heading for — see
 * {@code ../simple-fhir-server} for a working sketch — but no response profile is published,
 * because a profile with nothing behind it is a promise this service cannot keep. What the
 * severity grades are, and how an MFB rule's own text and action come across, are open.</li>
 * <li><b>Whether the answer may ever be partial.</b> The EVS contract fails closed on an
 * unresolvable code. A check that cannot evaluate one rule of forty has to do the same or say so
 * in the payload, and that is a clinical decision rather than an engineering one.</li>
 * </ul>
 *
 * <p>Declared non-idempotent, so HAPI exposes it over POST only. That is not a claim about side
 * effects — the check reads and stores nothing — but about the body: the request carries a
 * patient's medication, allergies and lab results, which have no business in a URL, a proxy cache
 * or an access log.
 */
@Component
public class SurveillanceOperationProvider extends SurveillanceProvider {

	public static final String CHECK_MEDICATION = "$check-medication";

	private final ProfileValidator profileValidator;

	public SurveillanceOperationProvider(ProfileValidator profileValidator) {
		this.profileValidator = profileValidator;
	}

	/**
	 * The parameters are declared individually rather than the body being taken whole, so that
	 * HAPI generates an {@code OperationDefinition} listing every name, type and cardinality —
	 * which is most of what makes an unimplemented operation worth publishing at all.
	 *
	 * <p>{@code prescription} and {@code medicationStatement} reuse the resource profiles of the
	 * EVS contract, so a host that already builds a session payload has nothing new to shape. The
	 * return type is declared as a {@code Bundle} because that is what it will be; nothing is
	 * built yet.
	 */
	@Operation(name = CHECK_MEDICATION, idempotent = false)
	public Bundle checkMedication(
			@OperationParam(name = "patient", min = 1, max = 1) Patient patient,
			@OperationParam(name = "xisId", min = 1, max = 1) StringType xisId,
			@OperationParam(name = "xisVersion", min = 1, max = 1) StringType xisVersion,
			@OperationParam(name = "prescription", min = 0, max = OperationParam.MAX_UNLIMITED) List<MedicationRequest> prescription,
			@OperationParam(name = "medicationStatement", min = 0, max = OperationParam.MAX_UNLIMITED) List<MedicationStatement> medicationStatement,
			@OperationParam(name = "allergyIntolerance", min = 0, max = OperationParam.MAX_UNLIMITED) List<AllergyIntolerance> allergyIntolerance,
			@OperationParam(name = "condition", min = 0, max = OperationParam.MAX_UNLIMITED) List<Condition> condition,
			@OperationParam(name = "observation", min = 0, max = OperationParam.MAX_UNLIMITED) List<Observation> observation,
			@ResourceParam Parameters body) {

		profileValidator.validate(body, Profiles.SURVEILLANCE_INPUT);

		throw notImplemented();
	}

	/**
	 * A 501 carrying an {@code OperationOutcome}, like every other error this API returns.
	 *
	 * <p>The issue code is {@code not-supported}, so a host can branch on the status and the code
	 * rather than on the wording — which the change policy says may move in a patch release.
	 */
	private NotImplementedOperationException notImplemented() {
		String message = "Medication surveillance is published but not yet implemented: this request"
				+ " conforms to " + Profiles.SURVEILLANCE_INPUT + ", and the check behind it is not"
				+ " wired up. No conclusion about this patient's medication may be drawn from this"
				+ " response. Contact Digitalis for the release it is planned for.";

		OperationOutcome outcome = new OperationOutcome();
		outcome.addIssue()
				.setSeverity(IssueSeverity.ERROR)
				.setCode(IssueType.NOTSUPPORTED)
				.setDiagnostics(message);

		return new NotImplementedOperationException(message, outcome);
	}
}
