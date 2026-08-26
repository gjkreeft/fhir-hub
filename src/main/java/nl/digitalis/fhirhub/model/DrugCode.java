package nl.digitalis.fhirhub.model;

import java.math.BigDecimal;

/**
 * One coded representation of a drug, with the supply and dosing that go with it.
 *
 * @param quantity amount to dispense. Decimal since v2 of the upstream contract: Prescriptor
 *                 sends NumbersSupplied as a double, and partial packs are real.
 * @param unit     G-Standaard basiseenheid, e.g. "ST"
 */
public record DrugCode(
		String type,
		Integer value,
		String description,
		BigDecimal quantity,
		String unit,
		Directions directions) {
}
