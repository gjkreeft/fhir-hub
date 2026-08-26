package nl.digitalis.fhirhub.fhir;

/**
 * Canonical system URIs for the code systems this interface exchanges.
 *
 * <p>Every OID here was taken from the HL7 NL OID register
 * (https://hl7.nl/images/downloads/openbaar/OID/HL7NL_OID_register.pdf) rather than from
 * documentation or memory. A wrong digit in a {@code system} is a silent interoperability
 * bug, so do not edit these without checking the register.
 */
public final class Systems {

	/** G-Standaard GPK (Generieke Product Kode), bestand 711. */
	public static final String GPK = "urn:oid:2.16.840.1.113883.2.4.4.1";

	/** G-Standaard HPK (Handels Product Kode). */
	public static final String HPK = "urn:oid:2.16.840.1.113883.2.4.4.7";

	/** G-Standaard PRK (Voorschrijfproducten). */
	public static final String PRK = "urn:oid:2.16.840.1.113883.2.4.4.10";

	/** G-Standaard thesaurus basiseenheden — the system for supply units such as "ST". */
	public static final String G_STANDAARD_BASISEENHEID = "urn:oid:2.16.840.1.113883.2.4.4.1.900.2";

	/** ICPC-1 NL, including thesaurus. */
	public static final String ICPC_1_NL = "urn:oid:2.16.840.1.113883.2.4.4.31.1";

	/**
	 * NHG Tabel 45 Diagnostische bepalingen.
	 *
	 * <p>Nictiz publishes this CodeSystem with {@code content: not-present}, so it does not
	 * enumerate codes and accepts the 8-position sleutelcode (memo 1-4, materiaal 5-6,
	 * bijzonderheid 7-8) that the NHG table itself defines. It currently ships only in the
	 * zib2020 {@code 0.12.0-labtrial.1} pre-release; see Nictiz BITS ZIB-639.
	 */
	public static final String NHG_TABEL_45 = "urn:oid:2.16.840.1.113883.2.4.4.30.45";

	/** WHO ATC classification. */
	public static final String ATC = "http://www.whocc.no/atc";

	/** UCUM, used for the supply duration in days. */
	public static final String UCUM = "http://unitsofmeasure.org";

	/**
	 * G-Standaard bijzondere kenmerken (bestand BST401T, thesaurus BST922T).
	 *
	 * <p>No OID is registered for this table, so a Digitalis-local system URI is used.
	 * Relevant codes: 2 = product valt onder Opiumwet in volle omvang, 65 = product valt
	 * onder Opiumwet; afhandeling als UR, 107 = grondstof valt onder Opiumwet in volle omvang.
	 */
	public static final String G_STANDAARD_BIJZONDER_KENMERK =
			"http://digitalis.nl/fhir/CodeSystem/gstandaard-bijzonder-kenmerk";

	private Systems() {
	}
}
