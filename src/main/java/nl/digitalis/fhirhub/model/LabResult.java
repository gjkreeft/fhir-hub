package nl.digitalis.fhirhub.model;

import java.time.LocalDate;

/**
 * One laboratory determination on its way upstream.
 *
 * <p>The LOINC code travels all the way through: the upstream carries lab data as
 * {@code <LOINC num="…">} and the rules engine tests it by that number, so nothing here is
 * translated into another terminology. {@code caption} is display-only, and {@code value} is in
 * {@code unit} because the rules are written in one — see {@code fhir/LabDeterminations}.
 */
public record LabResult(
		String loinc,
		String caption,
		String unit,
		LocalDate date,
		String value) {

	public LabResult {
		// Coding.display when the host sent one; the determination's own name otherwise, so the
		// prescriber sees something better than a code.
		if (caption == null || caption.isBlank()) {
			caption = loinc;
		}
	}
}
