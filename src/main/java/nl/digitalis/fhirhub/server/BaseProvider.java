package nl.digitalis.fhirhub.server;

/**
 * Marker superclass for the plain operation providers.
 *
 * <p>HAPI's plain-provider model needs no interface; this exists so the server configuration
 * can discover every provider by type rather than listing them by hand and forgetting one.
 */
public abstract class BaseProvider {
}
