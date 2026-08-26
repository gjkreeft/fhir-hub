package nl.digitalis.fhirhub.model;

/**
 * The G-Standaard code set for one drug the patient is currently using.
 *
 * <p>A host sends a single code, but Prescriptor's medication surveillance needs the product
 * identified at several levels at once. These are resolved from the G-Standaard service before
 * the session is opened.
 *
 * @param prk voorschrijfproduct code; always present
 * @param gpk generiek product code; always present
 * @param hpk handelsproduct code, only when the host identified the drug at HPK level
 */
public record MedicationCodes(Integer prk, Integer gpk, Integer hpk) {
}
