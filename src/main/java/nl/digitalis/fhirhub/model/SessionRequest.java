package nl.digitalis.fhirhub.model;

/**
 * A request to open a Prescriptor or CreateRx session.
 *
 * <p>Credentials are deliberately not part of this record: they arrive on the HTTP layer and
 * are joined in at the point the XML-RPC call is built.
 *
 * @param prescription an existing prescription to edit; CreateRx only, absent for a formulary
 *                     session
 */
public record SessionRequest(
		SessionType type,
		String icpc,
		PatientContext patient,
		String endSessionUrl,
		XisInfo xis,
		ExistingPrescription prescription) {
}
