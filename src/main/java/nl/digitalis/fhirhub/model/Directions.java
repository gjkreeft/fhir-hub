package nl.digitalis.fhirhub.model;

/**
 * Dosing directions as Prescriptor reports them.
 *
 * @param type  the coding syntax; expected to be "tabel25" (NHG Tabel 25 Gebruiksvoorschrift)
 * @param coded the coded instruction, e.g. "3-4D1S; gedurende max. 1 maand"
 * @param user  the expanded free text shown to the patient
 */
public record Directions(String type, String coded, String user) {
}
