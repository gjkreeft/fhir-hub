package nl.digitalis.fhirhub.model;

/**
 * A code plus the G-Standaard / NHG subsystem token it belongs to.
 *
 * <p>The token ({@code SNK}, {@code OGGrp}, {@code CICode}, {@code ICPC}, {@code PRK},
 * {@code HPK}) is what the upstream XML-RPC dialect keys on: it decides which member the code
 * is routed into. The FHIR mappers translate between the token and the full system URI in
 * {@link nl.digitalis.fhirhub.fhir.Systems}.
 */
public record CodedItem(String codeSystem, String code) {
}
