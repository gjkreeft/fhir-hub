package nl.digitalis.fhirhub.fhir;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import nl.digitalis.fhirhub.prescriptor.CodeSystemTokens;

/**
 * Translates between FHIR {@code system} URIs and the upstream subsystem tokens.
 *
 * <p>Several URIs may map to one token, which is what makes a terminology change a
 * non-breaking one: a newly pinned OID can be added alongside the URI integrators already
 * send, both accepted, and the old one retired later. With sixteen integrating systems that
 * matters more than tidiness.
 *
 * <h2>The G-Standaard subsystems are pinned</h2>
 * SSK, SNK, OGGrp and CICode now carry national OIDs, taken from Nictiz's published artifacts
 * — see {@link Systems} for where each one came from and which is inferred. The migration is
 * the additive one this class was built for: the national OID is what {@link #systemFor}
 * emits, and the Digitalis URI is still accepted on input so that nothing an integrator
 * already sends stops working. The bare token remains accepted for the same reason.
 *
 * <p>The Digitalis URIs are therefore <strong>deprecated, not removed</strong>. Retire them
 * once the integrators have moved, which is a decision about them rather than about this code.
 */
@Component
public class CodeSystemRegistry {

	/** Deprecated in favour of the OIDs in {@link Systems}; still accepted on input. */
	static final String DIGITALIS_SSK = "http://digitalis.nl/fhir/CodeSystem/gstandaard-ssk";
	static final String DIGITALIS_SNK = "http://digitalis.nl/fhir/CodeSystem/gstandaard-snk";
	static final String DIGITALIS_OGGRP = "http://digitalis.nl/fhir/CodeSystem/gstandaard-oggrp";
	static final String DIGITALIS_CI_CODE = "http://digitalis.nl/fhir/CodeSystem/gstandaard-contraindicatie";

	private final Map<String, String> tokensBySystem = new LinkedHashMap<>();

	public CodeSystemRegistry() {
		register(CodeSystemTokens.SSK, Systems.G_STANDAARD_SSK, DIGITALIS_SSK);
		register(CodeSystemTokens.SNK, Systems.G_STANDAARD_SNK, DIGITALIS_SNK);
		register(CodeSystemTokens.OGGRP, Systems.G_STANDAARD_OGGRP, DIGITALIS_OGGRP);
		register(CodeSystemTokens.CI_CODE, Systems.G_STANDAARD_CONTRA_INDICATIE, DIGITALIS_CI_CODE);
		register(CodeSystemTokens.ICPC, Systems.ICPC_1_NL);
		register(CodeSystemTokens.PRK, Systems.PRK);
		register(CodeSystemTokens.HPK, Systems.HPK);
	}

	private void register(String token, String... systems) {
		// The token itself is accepted as a system, so a host that has not adopted the URIs
		// can still integrate.
		tokensBySystem.put(token, token);
		for (String system : systems) {
			tokensBySystem.put(system, token);
		}
	}

	/** The upstream token for a FHIR system URI, or null when the system is not one we route. */
	public String tokenFor(String system) {
		return system == null ? null : tokensBySystem.get(system);
	}

	/** The canonical FHIR system URI to emit for a given upstream token. */
	public String systemFor(String token) {
		return switch (token) {
			case CodeSystemTokens.PRK -> Systems.PRK;
			case CodeSystemTokens.HPK -> Systems.HPK;
			case "GPK" -> Systems.GPK;
			case CodeSystemTokens.ICPC -> Systems.ICPC_1_NL;
			case CodeSystemTokens.SSK -> Systems.G_STANDAARD_SSK;
			case CodeSystemTokens.SNK -> Systems.G_STANDAARD_SNK;
			case CodeSystemTokens.OGGRP -> Systems.G_STANDAARD_OGGRP;
			case CodeSystemTokens.CI_CODE -> Systems.G_STANDAARD_CONTRA_INDICATIE;
			default -> throw new IllegalArgumentException("No system URI known for token " + token);
		};
	}
}
