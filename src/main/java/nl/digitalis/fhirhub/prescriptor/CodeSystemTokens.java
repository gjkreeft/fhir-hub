package nl.digitalis.fhirhub.prescriptor;

import java.util.Set;

/**
 * The G-Standaard / NHG subsystem tokens the upstream XML dialect understands.
 *
 * <p>These tokens end up as XML <em>attribute names</em> in the inner DigitalisRx document, so
 * they are validated against this allow-list before use. Without that, a caller-supplied code
 * system would be able to inject attributes.
 */
public final class CodeSystemTokens {

	public static final String SSK = "SSK";
	public static final String SNK = "SNK";
	public static final String OGGRP = "OGGrp";

	public static final String CI_CODE = "CICode";
	public static final String ICPC = "ICPC";

	public static final String PRK = "PRK";
	public static final String HPK = "HPK";

	public static final Set<String> ALLERGY = Set.of(SSK, SNK, OGGRP);
	public static final Set<String> CONTRA_INDICATION = Set.of(CI_CODE, ICPC);
	public static final Set<String> MEDICATION = Set.of(PRK, HPK);

	private CodeSystemTokens() {
	}
}
