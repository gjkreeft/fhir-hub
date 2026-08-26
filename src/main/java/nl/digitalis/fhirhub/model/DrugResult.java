package nl.digitalis.fhirhub.model;

import java.util.List;

/**
 * A drug prescribed during the session.
 *
 * @param opium whether the product falls under the Opiumwet. Prescriptor reports a plain
 *              yes/no, which corresponds to G-Standaard bijzonder kenmerk rubriek 72 nr 2.
 */
public record DrugResult(
		List<DrugCode> codes,
		Integer durationDays,
		boolean opium,
		String description,
		String atc) {
}
