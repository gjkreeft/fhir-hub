package nl.digitalis.fhirhub.prescriptor;

import java.time.format.DateTimeFormatter;

import java.util.List;

import nl.digitalis.fhirhub.model.CodedItem;
import nl.digitalis.fhirhub.model.LabResult;
import nl.digitalis.fhirhub.model.MedicationCodes;
import nl.digitalis.fhirhub.model.PatientContext;
import nl.digitalis.fhirhub.model.PrescriptorCredentials;

/**
 * Builds the inner {@code <DigitalisRx>} document that travels inside the PresPlus member.
 *
 * <p>This is a second, complete XML document embedded in a CDATA section of the first — the
 * shape Prescriptor expects, preserved from the JSON interface.
 */
final class DigitalisRxBuilder {

	private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

	private DigitalisRxBuilder() {
	}

	static String build(PatientContext patient,
			List<MedicationCodes> medication,
			PrescriptorCredentials credentials) {
		return XmlWriter.document(xml -> xml.element("DigitalisRx", root -> {
			root.empty("schemaVersion").attribute("id", "id0");
			// Populated since v2 of the contract; the JSON interface sent this element empty
			// until then. Prescriptor reads the organisation from here as well as from the
			// PracticeID/LicenseKey members of the outer call.
			root.element("xisInfo", x -> x.empty("licenseKey")
					.attribute("password", credentials.licenseKey())
					.attribute("organisationUnitId", credentials.practiceId())
					.attribute("key", credentials.licenseKey()));
			root.element("patient", p -> {
				p.text("gender", patient.gender());
				p.text("dob", patient.dateOfBirth().format(ISO_DATE));
				p.text("patientId", "");
				p.element("medicalExaminer", m -> {
				});

				p.element("allergies", a -> {
					for (CodedItem allergy : patient.allergies()) {
						writeGStandaard(a, allergy);
					}
				});

				p.element("indications", i -> {
				});

				p.element("contraIndications", c -> {
					for (CodedItem contraIndication : patient.contraIndications()) {
						writeGStandaard(c, contraIndication);
					}
				});

				// Current medication, resolved to the PRK/GPK pair Prescriptor needs for
				// medication surveillance. PRK is the attribute the upstream actually reads —
				// see XmlRpcRequestBuilder.medicationType — and is why a host may mix code
				// levels per entry. HPK is written alongside it when the host identified the
				// drug at that level, as the more precise fact, not as the one read.
				p.element("medication", m -> {
					for (MedicationCodes codes : medication) {
						m.element("drug", drug -> {
							drug.attribute("pending", "false");
							drug.empty("GStandaard")
									.attribute("PRK", String.valueOf(codes.prk()))
									.attribute("GPK", String.valueOf(codes.gpk()));

							if (codes.hpk() != null) {
								drug.attribute("HPK", String.valueOf(codes.hpk()));
							}
						});
					}
				});

				// Lab data as LOINC, which is what the host sent and what the MFB datatests test:
				// the generator in g-standaard/apps/mfb builds a DatatestLOINC keyed on this
				// number. The unit is carried too — Prescriptor_Units reads it to convert weight
				// and height — though the value is already in the unit the rules evaluate in.
				p.element("laboratoryData", l -> {
					for (LabResult lab : patient.laboratoryData()) {
						l.empty("LOINC")
								.attribute("num", lab.loinc())
								.attribute("caption", lab.caption())
								.attribute("date", lab.date().format(ISO_DATE))
								.attribute("value", lab.value())
								.attribute("unit", lab.unit());
					}
				});

				p.empty("referrals");
			});
		}));
	}

	private static void writeGStandaard(XmlWriter xml, CodedItem item) {
		xml.empty("GStandaard")
				.attribute(item.codeSystem(), item.code())
				.attribute("caption", "")
				.attribute("UID", "");
	}
}
