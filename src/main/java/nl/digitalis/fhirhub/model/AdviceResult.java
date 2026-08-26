package nl.digitalis.fhirhub.model;

/**
 * Patient advice produced during the session.
 *
 * @param uriList true when the advice is a link (thuisarts.nl) rather than prose; this decides
 *                whether it becomes a contentAttachment or a contentString in the Bundle
 */
public record AdviceResult(String text, boolean uriList) {
}
