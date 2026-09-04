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

	/**
	 * The Implementation Guide's canonical, and the host that serves it.
	 *
	 * <p>Deliberately not used to build the constants below: an integrator reading a payload greps
	 * for the whole URL, and so does anyone chasing one through this codebase, so each is written
	 * out. {@code IgCanonicalsTest} checks that they all sit under this one.
	 */
	public static final String CANONICAL = "http://spec.digitalis.nl/fhir";

	public static final String FORMULARY_SESSION_INPUT =
			"http://spec.digitalis.nl/fhir/StructureDefinition/fhirhub-FormularySessionInput";

	public static final String CREATERX_SESSION_INPUT =
			"http://spec.digitalis.nl/fhir/StructureDefinition/fhirhub-CreateRxSessionInput";

	public static final String SESSION_OUTPUT =
			"http://spec.digitalis.nl/fhir/StructureDefinition/fhirhub-SessionOutput";

	public static final String RESULT_BUNDLE =
			"http://spec.digitalis.nl/fhir/StructureDefinition/fhirhub-ResultBundle";

	/**
	 * The request profile of {@code $check-medication}, on the surveillance base.
	 *
	 * <p>Enforced even though the operation behind it is not implemented: a well-formed body gets
	 * a 501 and a malformed one still gets a 400 naming what is wrong with it, so an integrator
	 * can build and test the payload against the same rules that will apply when the check goes
	 * live. There is deliberately no response profile yet — see {@code SurveillanceOperationProvider}.
	 */
	public static final String SURVEILLANCE_INPUT =
			"http://spec.digitalis.nl/fhir/StructureDefinition/fhirhub-SurveillanceInput";

	private Profiles() {
	}
}
