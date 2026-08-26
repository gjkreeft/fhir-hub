package nl.digitalis.fhirhub.model;

import java.time.LocalDate;

/**
 * One NHG Tabel 45 laboratory determination.
 *
 * <p>{@code memo}, {@code material} and {@code peculiarity} are not three independent fields:
 * together they form the 8-position NHG Tabel 45 sleutelcode (memo 1-4, materiaal 5-6,
 * bijzonderheid 7-8) that uniquely and permanently identifies a determination. They are kept
 * apart here only because the upstream XML dialect transmits them apart.
 */
public record LabResult(
		String memo,
		String material,
		String peculiarity,
		LocalDate date,
		String value) {

	/**
	 * The composite sleutelcode, space padded to the fixed 8 positions the NHG table defines.
	 */
	public String keyCode() {
		return "%-4s%-2s%-2s".formatted(
				memo == null ? "" : memo,
				material == null ? "" : material,
				peculiarity == null ? "" : peculiarity);
	}
}
