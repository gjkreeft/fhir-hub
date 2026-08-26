package nl.digitalis.fhirhub.fhir;

/**
 * Extensions minted by Digitalis, for the two concepts that have no national home.
 *
 * <p>Both were checked against Nictiz's published artifacts first — nothing in nl-core,
 * zib2020 or Medicatieproces 9 covers either — so these are additions rather than
 * duplications of national work.
 *
 * <p>The canonical base moved from {@code http://digitalis.nl/fhir} to
 * {@code http://spec.digitalis.nl/fhir} so that the artifacts have a host of their own — see
 * README, <em>Profiles</em>. Only {@link #CODED_DIRECTIONS} needed a compatibility shim, because
 * it is the one extension this interface reads back off a payload a host round-trips; see
 * {@link #LEGACY_CODED_DIRECTIONS}.
 */
public final class DigitalisExtensions {

	/**
	 * The NHG Tabel 25 coded usage instruction, verbatim, e.g. {@code "3-4D1S; gedurende max. 1 maand"}.
	 *
	 * <p>FHIR models dosing structurally ({@code Dosage.timing} / {@code doseAndRate}) and has
	 * no slot for a compact coded string. HL7 NL registers OIDs only for tabel 25 components
	 * (tijdseenheden 2.16.840.1.113883.2.4.4.3, aanvullende teksten 2.16.840.1.113883.2.4.4.5),
	 * not for the composite string.
	 *
	 * <p>This extension carries the raw string, and is the authoritative form of the dosing
	 * instruction in both directions: {@code T25DosageMapper} writes it, and
	 * {@code SessionParametersMapper} reads it back. {@code Dosage.text} accompanies it for
	 * readers that cannot interpret Tabel 25. See {@code T25DosageMapper} for why no structured
	 * {@code timing} / {@code doseAndRate} is derived.
	 */
	public static final String CODED_DIRECTIONS =
			"http://spec.digitalis.nl/fhir/StructureDefinition/ext-Dosage.CodedDirections";

	/**
	 * The pre-move URL of {@link #CODED_DIRECTIONS}, still accepted on input and never emitted.
	 *
	 * <p>This one extension is read back: a host may hand a prescription it received earlier
	 * straight into {@code $createrx-session}, and a prescription issued before the move carries
	 * the old URL. Dropping it would silently fall back to {@code Dosage.text} and lose the
	 * coded instruction — a quiet downgrade of the dosing, not a visible failure, which is the
	 * kind this interface refuses to ship.
	 *
	 * <p>Retire it when no stored prescription can still predate the move.
	 */
	public static final String LEGACY_CODED_DIRECTIONS =
			"http://digitalis.nl/fhir/StructureDefinition/ext-Dosage.CodedDirections";

	/**
	 * Opiumwet classification of the prescribed product, as a G-Standaard bijzonder kenmerk.
	 *
	 * <p>Carried as a CodeableConcept rather than a boolean: codes 2 ("in volle omvang") and
	 * 65 ("afhandeling als UR") have materially different consequences for a pharmacist, and a
	 * boolean flattens that away. Prescriptor currently reports only a yes/no flag, which
	 * corresponds to bijzonder kenmerk rubriek 72 nr 2 — the same predicate
	 * {@code gstandaard-jar}'s {@code OpiumDao} applies — so only code 2 is emitted today.
	 * Codes 65 and 107 become available without a breaking change if the upstream is enriched.
	 */
	public static final String OPIUM_ACT_CLASSIFICATION =
			"http://spec.digitalis.nl/fhir/StructureDefinition/ext-MedicationRequest.OpiumActClassification";

	private DigitalisExtensions() {
	}
}
