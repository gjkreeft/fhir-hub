package nl.digitalis.fhirhub.server;

/**
 * Marker superclass for the providers of the medication-surveillance contract, served at
 * {@code /fhir/surveillance}.
 *
 * <p>The sibling of {@link EvsProvider}, and deliberately not a subclass of anything it shares
 * with it — see that class for why.
 */
public abstract class SurveillanceProvider {
}
