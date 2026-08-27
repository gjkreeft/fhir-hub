package nl.digitalis.fhirhub.fhir;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import nl.digitalis.fhirhub.prescriptor.CodeSystemTokens;

/**
 * Translates between FHIR {@code system} URIs and the upstream subsystem tokens.
 *
 * <p>Several URIs may map to one token, which is what lets a terminology change be additive: a
 * newly pinned OID can be registered beside the form hosts already send, and the older one
 * dropped once they have moved. With sixteen integrating systems that matters more than tidiness.
 *
 * <p>What this class decides is only whether a system can be <em>routed</em>. Whether a payload
 * is accepted is decided by the profiles in {@code ig/}, which bind one form per subsystem: the
 * national OID that {@link #systemFor} emits. The bare upstream token is mapped here as well —
 * it is the form the JSON interface carries in a field of its own — but it is not a FHIR system,
 * no profile admits it, and validation runs before this class does, so a coding that uses it is
 * rejected unless {@code fhirhub.validation.enabled=false}.
 * {@code TerminologyEnforcementTest} pins that.
 *
 * <p>See {@link Systems} for where each OID came from and which one is inferred.
 */
@Component
public class CodeSystemRegistry {

	private final Map<String, String> tokensBySystem = new LinkedHashMap<>();

	public CodeSystemRegistry() {
		register(CodeSystemTokens.SSK, Systems.G_STANDAARD_SSK);
		register(CodeSystemTokens.SNK, Systems.G_STANDAARD_SNK);
		register(CodeSystemTokens.OGGRP, Systems.G_STANDAARD_OGGRP);
		register(CodeSystemTokens.CI_CODE, Systems.G_STANDAARD_CONTRA_INDICATIE);
		register(CodeSystemTokens.ICPC, Systems.ICPC_1_NL);
		register(CodeSystemTokens.PRK, Systems.PRK);
		register(CodeSystemTokens.HPK, Systems.HPK);
	}

	private void register(String token, String... systems) {
		// The token is mapped as a system too, so a host porting a JSON-interface mapping is
		// routed rather than silently dropped. No profile admits it; see the class Javadoc.
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
