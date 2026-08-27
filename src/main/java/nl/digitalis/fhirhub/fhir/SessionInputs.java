package nl.digitalis.fhirhub.fhir;

import java.util.List;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.UrlType;

import nl.digitalis.fhirhub.model.SessionType;

/**
 * The inbound parameters of a session operation, as HAPI destructured them.
 *
 * <p>The two operations declare their parameters individually rather than taking one opaque
 * {@code Parameters}, which is what lets HAPI generate an {@code OperationDefinition} carrying
 * every name, type and cardinality — the thing an integrator can generate code from.
 *
 * <p>The cardinalities on those {@code @OperationParam} annotations are <em>declarations</em>,
 * not enforcement: HAPI reads {@code min} and {@code max} only when it builds the
 * {@code OperationDefinition}, never while binding a request. {@link SessionParametersMapper}
 * is still the only thing that rejects a bad request, so the 400 wording the Implementation
 * Guide documents is unchanged.
 *
 * <p>Every parameter is bound, so nothing is missing from the generated definition. Two of them
 * are shaped by that requirement rather than by the domain:
 *
 * <ul>
 * <li>{@code xisId} and {@code xisVersion} are two plain strings rather than one {@code xis}
 * parameter with {@code id} and {@code version} parts, because HAPI's binder reads only
 * {@code name}, {@code value[x]} and {@code resource} — never {@code part}, so a multi-part
 * parameter cannot appear in the generated definition at all.
 * <li>{@code reason} is a {@code CodeableConcept} and not also a {@code Coding}. HAPI rejects a
 * polymorphic {@code @OperationParam} outright (HAPI-0361), which is the correct FHIR position:
 * {@code OperationDefinition.parameter.type} is a single code.
 * </ul>
 */
public record SessionInputs(
		SessionType type,
		Patient patient,
		CodeableConcept reason,
		UrlType endSessionUrl,
		StringType xisId,
		StringType xisVersion,
		List<AllergyIntolerance> allergyIntolerance,
		List<Condition> condition,
		List<MedicationStatement> medicationStatement,
		List<Observation> observation,
		MedicationRequest prescription) {

	public SessionInputs {
		allergyIntolerance = allergyIntolerance == null ? List.of() : List.copyOf(allergyIntolerance);
		condition = condition == null ? List.of() : List.copyOf(condition);
		medicationStatement = medicationStatement == null ? List.of() : List.copyOf(medicationStatement);
		observation = observation == null ? List.of() : List.copyOf(observation);
	}
}
