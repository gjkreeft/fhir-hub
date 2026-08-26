package nl.digitalis.fhirhub.fhir;

import org.hl7.fhir.r4.model.Dosage;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

import nl.digitalis.fhirhub.model.Directions;

/**
 * Turns Prescriptor's dosing directions into a FHIR {@link Dosage}.
 *
 * <p>Two things are emitted, both verbatim:
 * <ol>
 *   <li>{@code Dosage.text} — the expanded free text. This is what a human reads, and the
 *       element every FHIR consumer understands without knowing anything about Dutch coding.</li>
 *   <li>The {@link DigitalisExtensions#CODED_DIRECTIONS} extension — the NHG Tabel 25 string
 *       exactly as Prescriptor reported it.</li>
 * </ol>
 *
 * <h2>Why nothing is decoded into {@code timing} / {@code doseAndRate}</h2>
 * Both ends of this interface speak NHG Tabel 25: Prescriptor emits it, and the HIS and XIS
 * systems consuming this API hand it onward to a pharmacy chain that reads it natively. Nobody
 * in that path computes on a FHIR dosing schedule.
 *
 * <p>An earlier version parsed the coded string into {@code timing.repeat} and
 * {@code doseAndRate}. That was removed deliberately, and re-adding it needs a reason:
 * <ul>
 *   <li>It was lossy — only the frequency, period, dose and unit components were mapped, while
 *       the b-codes and the trailing free text were dropped, and the unit was emitted as bare
 *       text because Tabel 25 units are not UCUM.</li>
 *   <li>It was never read back. {@code SessionParametersMapper} recovers the coded string from
 *       the extension, so a host editing {@code timing} and returning the resource had its edit
 *       silently ignored — two representations of one fact, only one of them authoritative.</li>
 *   <li>{@code MedicationRequest} asserts no profile at all, so no conformance obligation rests
 *       on the structured elements.</li>
 * </ul>
 *
 * <p>If national conformance is taken on later, the target is the zib Gebruiksinstructie /
 * Doseerinstructie model from Medicatieproces 9 — a different and fuller mapping than the
 * component split that used to live here, not an extension of it.
 */
@Component
public class T25DosageMapper {

	public Dosage toDosage(Directions directions) {
		Dosage dosage = new Dosage();

		if (directions == null) {
			return dosage;
		}

		if (directions.user() != null && !directions.user().isBlank()) {
			dosage.setText(directions.user());
		}

		String coded = directions.coded();
		if (coded == null || coded.isBlank()) {
			return dosage;
		}

		dosage.addExtension(DigitalisExtensions.CODED_DIRECTIONS, new StringType(coded));

		return dosage;
	}
}
