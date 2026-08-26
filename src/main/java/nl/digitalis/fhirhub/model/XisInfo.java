package nl.digitalis.fhirhub.model;

/**
 * Identifies the calling XIS (the host system), for logging and support.
 *
 * <p>Never sent to Prescriptor — the JSON interface uses it purely to attribute requests in the
 * log. With a dozen HIS suppliers integrating, being able to tell from a log line which system
 * and which release produced a request is worth the required field.
 */
public record XisInfo(String id, String version) {
}
