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
 * <p>Parsing the coded string into {@code timing.repeat} and {@code doseAndRate} is the obvious
 * thing to want, and it needs a better reason than obviousness:
 * <ul>
 *   <li>It is lossy. Frequency, period, dose and unit map across; the b-codes and the trailing
 *       free text do not, and the unit has to be emitted as bare text because Tabel 25 units are
 *       not UCUM.</li>
 *   <li>It would not be read back. {@code SessionParametersMapper} recovers the coded string
 *       from the extension, so a host that edits {@code timing} and returns the resource has its
 *       edit silently ignored — two representations of one fact, only one authoritative.</li>
 *   <li>{@code MedicationRequest} asserts no profile at all, so no conformance obligation rests
 *       on the structured elements.</li>
 * </ul>
 *
 * <p>If national conformance is taken on later, the target is the zib Gebruiksinstructie /
 * Doseerinstructie model from Medicatieproces 9, which is a fuller mapping than splitting the
 * string into components — not a step towards it.
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
