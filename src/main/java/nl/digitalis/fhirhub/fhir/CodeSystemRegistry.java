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
 * <h2>Open item</h2>
 * The G-Standaard subsystem OIDs for SSK, SNK, OGGrp and CICode are <strong>not yet
 * pinned</strong>. They live inside the Nictiz DECOR ValueSets
 * {@code 2.16.840.1.113883.2.4.3.11.60.121.11.2} (AllergyIntolerance CausativeAgent) and the
 * MedicationContraIndication binding, which have not been expanded. Until they are, the
 * Digitalis URIs below are authoritative for this interface, and the bare token is accepted
 * as a system so integration is not blocked. Do not treat the placeholders as national
 * identifiers — pin them before the interface is published externally.
 */
@Component
public class CodeSystemRegistry {

	static final String DIGITALIS_SSK = "http://digitalis.nl/fhir/CodeSystem/gstandaard-ssk";
	static final String DIGITALIS_SNK = "http://digitalis.nl/fhir/CodeSystem/gstandaard-snk";
	static final String DIGITALIS_OGGRP = "http://digitalis.nl/fhir/CodeSystem/gstandaard-oggrp";
	static final String DIGITALIS_CI_CODE = "http://digitalis.nl/fhir/CodeSystem/gstandaard-contraindicatie";

	private final Map<String, String> tokensBySystem = new LinkedHashMap<>();

	public CodeSystemRegistry() {
		register(CodeSystemTokens.SSK, DIGITALIS_SSK);
		register(CodeSystemTokens.SNK, DIGITALIS_SNK);
		register(CodeSystemTokens.OGGRP, DIGITALIS_OGGRP);
		register(CodeSystemTokens.CI_CODE, DIGITALIS_CI_CODE);
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
			case CodeSystemTokens.SSK -> DIGITALIS_SSK;
			case CodeSystemTokens.SNK -> DIGITALIS_SNK;
			case CodeSystemTokens.OGGRP -> DIGITALIS_OGGRP;
			case CodeSystemTokens.CI_CODE -> DIGITALIS_CI_CODE;
			default -> throw new IllegalArgumentException("No system URI known for token " + token);
		};
	}
}
