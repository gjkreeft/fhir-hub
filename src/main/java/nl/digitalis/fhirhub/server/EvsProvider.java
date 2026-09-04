package nl.digitalis.fhirhub.server;

/**
 * Marker superclass for the providers of the EVS contract, served at {@code /fhir/evs}.
 *
 * <p>HAPI's plain-provider model needs no interface; this exists so the server configuration can
 * discover every provider by type rather than listing them by hand and forgetting one.
 *
 * <p>There is deliberately no common parent shared with {@link SurveillanceProvider}. The two
 * contracts are separate FHIR bases with separate CapabilityStatements, and a shared marker would
 * make {@code List<CommonParent>} the easy thing to inject — which would advertise every EVS
 * operation on the surveillance base and the other way round.
 * {@code SurveillanceIntegrationTest.theSurveillanceBaseAdvertisesOnlyItsOwnOperations} is what
 * notices if a provider ever extends the wrong one of the two.
 */
public abstract class EvsProvider {
}
