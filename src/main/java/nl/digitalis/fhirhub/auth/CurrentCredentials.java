package nl.digitalis.fhirhub.auth;

import nl.digitalis.fhirhub.model.PrescriptorCredentials;

/**
 * The credentials of the request being served, for the duration of that request.
 *
 * <p>{@link BasicAuthenticationFilter} puts them here and clears them again; everything
 * downstream reads them through {@link CredentialsResolver}. A thread-local rather than a
 * parameter because the code between the two is HAPI's — the operation providers are invoked by
 * the {@code RestfulServer}, which has no notion of this application's credentials and no way to
 * carry them.
 *
 * <p>That makes one assumption, and it is worth stating because breaking it is silent: the filter
 * and the provider run on the same thread. They do, because nothing here dispatches
 * asynchronously — no {@code AsyncContext}, no reactive handler, no executor between the two. If
 * that ever changes, this holder stops working and the symptom is a request served with another
 * request's licence key rather than an error, so move the credentials onto the request instead of
 * making this cleverer.
 */
final class CurrentCredentials {

	private static final ThreadLocal<PrescriptorCredentials> CURRENT = new ThreadLocal<>();

	private CurrentCredentials() {
	}

	static void set(PrescriptorCredentials credentials) {
		CURRENT.set(credentials);
	}

	static PrescriptorCredentials get() {
		return CURRENT.get();
	}

	/** Always in a finally block: a pooled request thread outlives the request it served. */
	static void clear() {
		CURRENT.remove();
	}
}
