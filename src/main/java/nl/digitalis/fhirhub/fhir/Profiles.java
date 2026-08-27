package nl.digitalis.fhirhub.fhir;

/**
 * Canonical URLs of the payload profiles, defined in {@code ig/input/fsh/}.
 *
 * <p>SUSHI derives each of these as {@code {canonical}/StructureDefinition/{Id}}, so the FSH ids
 * and these constants are the same fact written twice. {@code IgCanonicalsTest} fails if they
 * drift apart.
 *
 * <p>These are not decoration: the providers hand them to {@code ProfileValidator}, so every
 * inbound payload is checked against the URL named here.
 */
public final class Profiles {

	public static final String FORMULARY_SESSION_INPUT =
			"http://spec.digitalis.nl/fhir/StructureDefinition/fhirhub-FormularySessionInput";

	public static final String CREATERX_SESSION_INPUT =
			"http://spec.digitalis.nl/fhir/StructureDefinition/fhirhub-CreateRxSessionInput";

	public static final String SESSION_OUTPUT =
			"http://spec.digitalis.nl/fhir/StructureDefinition/fhirhub-SessionOutput";

	public static final String RESULT_BUNDLE =
			"http://spec.digitalis.nl/fhir/StructureDefinition/fhirhub-ResultBundle";

	private Profiles() {
	}
}
