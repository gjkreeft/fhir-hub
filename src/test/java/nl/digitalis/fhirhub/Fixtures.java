package nl.digitalis.fhirhub;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import nl.digitalis.fhirhub.model.CodedItem;
import nl.digitalis.fhirhub.model.LabResult;
import nl.digitalis.fhirhub.model.PatientContext;
import nl.digitalis.fhirhub.model.PrescriptorCredentials;
import nl.digitalis.fhirhub.model.SessionRequest;
import nl.digitalis.fhirhub.model.SessionType;
import nl.digitalis.fhirhub.model.XisInfo;

/** Shared test data, kept in one place so a change to the model does not ripple through every test. */
public final class Fixtures {

	public static final PrescriptorCredentials CREDENTIALS =
			new PrescriptorCredentials("practice-123", "license-key");

	private Fixtures() {
	}

	public static String xml(String name) {
		try (var in = Fixtures.class.getResourceAsStream("/xmlrpc/" + name)) {
			if (in == null) {
				throw new IllegalArgumentException("No such fixture: " + name);
			}

			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static PatientContext patient() {
		return new PatientContext(
				"F",
				LocalDate.of(1980, 1, 1),
				List.of(new CodedItem("SNK", "10499"), new CodedItem("OGGrp", "77"), new CodedItem("SSK", "3204")),
				List.of(new CodedItem("CICode", "228"), new CodedItem("ICPC", "A01")),
				List.of(new CodedItem("PRK", "8060")),
				List.of(new LabResult("62238-1", "eGFR volgens CKD-EPI", "mL/min/{1.73_m2}",
						LocalDate.of(2024, 7, 4), null, "65")));
	}

	public static final XisInfo XIS = new XisInfo("xis-001", "1.0");

	public static SessionRequest formularySession() {
		return new SessionRequest(
				SessionType.FORMULARY, "A01", patient(), "https://someurl.example/done", XIS, null);
	}
}
