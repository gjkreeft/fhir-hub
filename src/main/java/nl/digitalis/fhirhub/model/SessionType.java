package nl.digitalis.fhirhub.model;

/**
 * The two kinds of session Prescriptor can open.
 *
 * <p>They differ in the XML-RPC method invoked and — a genuine upstream inconsistency — in
 * the member name under which the session key comes back.
 */
public enum SessionType {

	/** Formulary session; takes an ICPC code as SearchKey. */
	FORMULARY("openSession", "PrescriptorSessionKey"),

	/** CreateRx session; ICPC is optional. */
	CREATE_RX("createPrescription", "SessionKey");

	private final String methodName;
	private final String sessionKeyMember;

	SessionType(String methodName, String sessionKeyMember) {
		this.methodName = methodName;
		this.sessionKeyMember = sessionKeyMember;
	}

	public String methodName() {
		return methodName;
	}

	public String sessionKeyMember() {
		return sessionKeyMember;
	}
}
