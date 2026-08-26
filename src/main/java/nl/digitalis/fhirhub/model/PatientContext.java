package nl.digitalis.fhirhub.model;

import java.time.LocalDate;
import java.util.List;

/**
 * The patient context a host pushes in when opening a session.
 *
 * <p>Gender is the Prescriptor domain value ("M", "F" or "X" for unknown), not the FHIR one —
 * FHIR's administrative gender has four values and Prescriptor accepts three, so the mapper
 * narrows it and rejects what cannot be represented rather than guessing.
 */
public record PatientContext(
		String gender,
		LocalDate dateOfBirth,
		List<CodedItem> allergies,
		List<CodedItem> contraIndications,
		List<CodedItem> medications,
		List<LabResult> laboratoryData) {
}
