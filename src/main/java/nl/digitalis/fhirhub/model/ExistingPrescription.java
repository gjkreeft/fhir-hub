package nl.digitalis.fhirhub.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * A prescription the host already holds, handed to CreateRx so the care provider can edit it
 * rather than start from scratch.
 *
 * @param codes      the drug at PRK and/or HPK level; the level present decides PrescriptionType
 * @param atc        ATC classification
 * @param quantity   amount to dispense
 * @param unit       G-Standaard basiseenheid for the quantity, e.g. "ST"
 * @param directions the NHG Tabel 25 coded instruction, e.g. "3-4D1S; gedurende max. 1 maand"
 */
public record ExistingPrescription(
		List<DrugCode> codes,
		String atc,
		BigDecimal quantity,
		String unit,
		String directions) {
}
