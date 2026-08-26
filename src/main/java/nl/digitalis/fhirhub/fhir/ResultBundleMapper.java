package nl.digitalis.fhirhub.fhir;

import java.util.UUID;

import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.CodeType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Communication;
import org.hl7.fhir.r4.model.Communication.CommunicationStatus;
import org.hl7.fhir.r4.model.Duration;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestIntent;
import org.hl7.fhir.r4.model.MedicationRequest.MedicationRequestStatus;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

import nl.digitalis.fhirhub.model.AdviceResult;
import nl.digitalis.fhirhub.model.DrugCode;
import nl.digitalis.fhirhub.model.DrugResult;
import nl.digitalis.fhirhub.model.SessionResult;

/** Maps a finished session onto the Bundle returned by {@code $session-result}. */
@Component
public class ResultBundleMapper {

	private static final String DATA_ABSENT_REASON =
			"http://hl7.org/fhir/StructureDefinition/data-absent-reason";

	private static final String COMMUNICATION_CATEGORY =
			"http://terminology.hl7.org/CodeSystem/communication-category";

	private final CodeSystemRegistry codeSystems;
	private final T25DosageMapper dosageMapper;

	public ResultBundleMapper(CodeSystemRegistry codeSystems, T25DosageMapper dosageMapper) {
		this.codeSystems = codeSystems;
		this.dosageMapper = dosageMapper;
	}

	public Bundle toBundle(SessionResult result) {
		Bundle bundle = new Bundle();
		bundle.setType(BundleType.COLLECTION);

		for (DrugResult drug : result.drugs()) {
			addEntry(bundle, toMedicationRequest(drug));
		}

		for (AdviceResult advice : result.advices()) {
			addEntry(bundle, toCommunication(advice));
		}

		return bundle;
	}

	/**
	 * Every entry of a Bundle that is not a transaction or a batch must carry a fullUrl, so a
	 * Bundle without one is not valid FHIR however well-formed its resources are.
	 *
	 * <p>These resources have no server identity — fhir-hub stores nothing and there is no
	 * endpoint to fetch a prescription back from — so the identity is a fresh {@code urn:uuid},
	 * which is the FHIR idiom for exactly that case. It identifies the entry within this Bundle
	 * and nowhere else: do not persist it as a prescription id or expect it to be stable across
	 * calls, because {@code $session-result} cannot be called twice anyway.
	 */
	private void addEntry(Bundle bundle, Resource resource) {
		bundle.addEntry()
				.setFullUrl("urn:uuid:" + UUID.randomUUID())
				.setResource(resource);
	}

	private MedicationRequest toMedicationRequest(DrugResult drug) {
		MedicationRequest request = new MedicationRequest();
		request.setStatus(MedicationRequestStatus.ACTIVE);
		request.setIntent(MedicationRequestIntent.ORDER);

		// MedicationRequest.subject is 1..1, but a session result carries no patient identity:
		// fhir-hub is stateless and never stored the Patient the host pushed in. The host
		// correlates on the session id it polled with. data-absent-reason is the FHIR idiom for
		// a mandatory element that genuinely cannot be supplied, and is honest about the gap in
		// a way an invented reference would not be.
		Reference subject = new Reference();
		subject.addExtension(DATA_ABSENT_REASON, new CodeType("unknown"));
		request.setSubject(subject);

		request.setMedication(medication(drug));

		if (drug.opium()) {
			request.addExtension(DigitalisExtensions.OPIUM_ACT_CLASSIFICATION, opiumClassification());
		}

		for (DrugCode code : drug.codes()) {
			if (code.directions() != null) {
				request.addDosageInstruction(dosageMapper.toDosage(code.directions()));
			}

			dispenseRequest(request, code, drug);
		}

		return request;
	}

	private CodeableConcept medication(DrugResult drug) {
		CodeableConcept concept = new CodeableConcept();

		for (DrugCode code : drug.codes()) {
			if (code.value() == null) {
				continue;
			}

			concept.addCoding(new Coding()
					.setSystem(codeSystems.systemFor(code.type()))
					.setCode(String.valueOf(code.value()))
					.setDisplay(code.description()));
		}

		if (drug.atc() != null && !drug.atc().isBlank()) {
			concept.addCoding(new Coding().setSystem(Systems.ATC).setCode(drug.atc()));
		}

		if (drug.description() != null && !drug.description().isBlank()) {
			concept.setText(drug.description());
		}

		return concept;
	}

	private void dispenseRequest(MedicationRequest request, DrugCode code, DrugResult drug) {
		MedicationRequest.MedicationRequestDispenseRequestComponent dispense = request.getDispenseRequest();

		if (code.quantity() != null) {
			Quantity quantity = new Quantity().setValue(code.quantity());

			if (code.unit() != null && !code.unit().isBlank()) {
				// G-Standaard basiseenheid, not UCUM: "ST" (stuks) has no UCUM equivalent.
				quantity.setSystem(Systems.G_STANDAARD_BASISEENHEID)
						.setCode(code.unit())
						.setUnit(code.unit());
			}

			dispense.setQuantity(quantity);
		}

		if (drug.durationDays() != null) {
			dispense.setExpectedSupplyDuration((Duration) new Duration()
					.setValue(drug.durationDays())
					.setUnit("dag")
					.setSystem(Systems.UCUM)
					.setCode("d"));
		}
	}

	/**
	 * Prescriptor reports a plain yes/no for the Opiumwet, which corresponds to G-Standaard
	 * bijzonder kenmerk rubriek 72 nr 2 — "product valt onder Opiumwet in volle omvang". Codes
	 * 65 and 107 exist upstream in BST401T but are not distinguished by Prescriptor today, so
	 * only code 2 is asserted. A CodeableConcept means adding them later is additive.
	 */
	private CodeableConcept opiumClassification() {
		return new CodeableConcept().addCoding(new Coding()
				.setSystem(Systems.G_STANDAARD_BIJZONDER_KENMERK)
				.setCode("2")
				.setDisplay("Product valt onder Opiumwet in volle omvang"));
	}

	private Communication toCommunication(AdviceResult advice) {
		Communication communication = new Communication();
		communication.setStatus(CommunicationStatus.COMPLETED);
		communication.addCategory(new CodeableConcept().addCoding(new Coding()
				.setSystem(COMMUNICATION_CATEGORY)
				.setCode("instruction")));

		// The text/plain versus text/uri-list distinction of the JSON interface becomes the
		// choice of payload type: prose is a string, a thuisarts.nl pointer is an attachment
		// with a URL. No custom content-type field is needed.
		if (advice.uriList()) {
			communication.addPayload().setContent(new Attachment()
					.setContentType("text/uri-list")
					.setUrl(advice.text()));
		}
		else {
			communication.addPayload().setContent(new StringType(advice.text()));
		}

		return communication;
	}
}
