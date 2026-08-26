package nl.digitalis.fhirhub.gstandaard;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import nl.digitalis.fhirhub.model.CodedItem;
import nl.digitalis.fhirhub.model.MedicationCodes;
import nl.digitalis.fhirhub.prescriptor.CodeSystemTokens;

/**
 * Resolves the patient's current medication to the full G-Standaard code set.
 *
 * <p>A host identifies a drug by one code, PRK or HPK. Prescriptor's medication surveillance
 * needs PRK and GPK together (plus HPK when the host had it), so each drug is looked up in the
 * {@code medcode} view of the G-Standaard database before the session is opened.
 *
 * <h2>Why this fails loudly</h2>
 * An unresolvable code aborts the session rather than opening one with an incomplete
 * medication list. Surveillance that silently receives fewer drugs than the patient is taking
 * does not fail visibly: it answers "no interaction found", which is a false negative in the
 * dangerous direction, and a prescriber cannot tell it apart from a genuine all-clear. So the
 * safe behaviour is to refuse and name the code that could not be resolved.
 */
@Service
public class MedicationCodeResolver {

	private static final Logger log = LoggerFactory.getLogger(MedicationCodeResolver.class);

	/**
	 * One row of {@code medcode} carries the whole chain, so a single lookup serves both
	 * directions. A drug can appear on several rows — one per packaging — so the query is
	 * ordered and the first row wins; every row carries the same PRK+GPK pair, which is all
	 * medication surveillance needs.
	 */
	private static final String BY_PRK = """
			SELECT prk, gpk, hpk
			FROM medcode
			WHERE prk = ?
			AND gpk > 0
			ORDER BY gpk
			""";

	private static final String BY_HPK = """
			SELECT prk, gpk, hpk
			FROM medcode
			WHERE hpk = ?
			AND prk > 0
			AND gpk > 0
			ORDER BY prk
			""";

	private final JdbcTemplate jdbc;

	public MedicationCodeResolver(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<MedicationCodes> resolve(List<CodedItem> medications) {
		List<MedicationCodes> resolved = new ArrayList<>();
		for (CodedItem medication : medications) {
			resolved.add(resolve(medication));
		}

		return List.copyOf(resolved);
	}

	private MedicationCodes resolve(CodedItem medication) {
		boolean byHpk = CodeSystemTokens.HPK.equals(medication.codeSystem());
		long code = toCode(medication);

		// query() rather than queryForObject(): an unknown code is an expected outcome that has
		// to produce the message below, not an EmptyResultDataAccessException.
		List<MedicationCodes> matches = jdbc.query(
				byHpk ? BY_HPK : BY_PRK,
				(rs, row) -> new MedicationCodes(
						rs.getInt("prk"),
						rs.getInt("gpk"),
						byHpk ? rs.getInt("hpk") : null),
				code);

		if (matches.isEmpty()) {
			throw new InvalidRequestException(
					"G-Standaard has no product for %s %s, so it cannot take part in medication surveillance"
							.formatted(medication.codeSystem(), medication.code()));
		}

		MedicationCodes codes = matches.getFirst();

		log.debug("Resolved {} {} to PRK {} / GPK {}",
				medication.codeSystem(), medication.code(), codes.prk(), codes.gpk());

		return codes;
	}

	private long toCode(CodedItem medication) {
		try {
			return Long.parseLong(medication.code().trim());
		}
		catch (NumberFormatException e) {
			throw new InvalidRequestException(
					"%s code '%s' is not numeric".formatted(medication.codeSystem(), medication.code()));
		}
	}
}
