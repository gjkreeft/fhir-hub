package nl.digitalis.fhirhub.model;

/**
 * The Prescriptor practice credentials, carried on the HTTP layer rather than in the payload.
 *
 * <p>Clients authenticate with {@code Authorization: Basic base64(practiceId ":" licenseKey)}.
 * fhir-hub does not validate them — it forwards them as the {@code PracticeID} and
 * {@code LicenseKey} members of the XML-RPC call and lets Prescriptor adjudicate, which is
 * what keeps this service stateless and free of a credential store.
 */
public record PrescriptorCredentials(String practiceId, String licenseKey) {
}
