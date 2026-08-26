package nl.digitalis.fhirhub.model;

/** What Prescriptor returns when a session is opened: where to send the user, and the key to poll with. */
public record SessionHandle(String sessionId, String prescriptorUrl) {
}
