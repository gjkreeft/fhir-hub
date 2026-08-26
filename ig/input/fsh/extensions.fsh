// The two extensions Digitalis mints, at exactly the URLs fhir-hub already puts on the wire.
// Both were checked against Nictiz's published artifacts first — nothing in nl-core, zib2020
// or Medicatieproces 9 covers either — so these are additions, not duplications.
//
// The ids below are what make the canonicals come out right, and they are load-bearing:
// SUSHI derives the URL as {canonical}/StructureDefinition/{Id}, which must equal the
// constants in fhir/DigitalisExtensions.java. Renaming an id silently breaks every payload
// already in the field.

Extension: CodedDirections
Id: ext-Dosage.CodedDirections
Title: "Coded directions (NHG Tabel 25)"
Description: "The NHG Tabel 25 coded usage instruction, verbatim, e.g. '3-4D1S; gedurende max. 1 maand'."
* ^status = #draft
* ^context[0].type = #element
* ^context[0].expression = "Dosage"
* ^purpose = "FHIR models dosing structurally (Dosage.timing, Dosage.doseAndRate) and has no slot for a compact coded string. HL7 NL registers OIDs for Tabel 25 components — tijdseenheden 2.16.840.1.113883.2.4.4.3, aanvullende teksten 2.16.840.1.113883.2.4.4.5 — but not for the composite string."
* value[x] only string
* value[x] 1..1
* value[x] ^short = "The coded instruction, passed through undecoded"
* value[x] ^comment = "Authoritative in both directions: fhir-hub writes it and reads it back, so a host that edits the dosing must edit this, not Dosage.text. No timing or doseAndRate is derived from it — an earlier version did, and it was lossy."

Extension: OpiumActClassification
Id: ext-MedicationRequest.OpiumActClassification
Title: "Opiumwet classification"
Description: "Opiumwet classification of the prescribed product, as a G-Standaard bijzonder kenmerk."
* ^status = #draft
* ^context[0].type = #element
* ^context[0].expression = "MedicationRequest"
* ^purpose = "Carried as a CodeableConcept rather than a boolean because codes 2 ('in volle omvang') and 65 ('afhandeling als UR') have materially different consequences for a pharmacist, and a boolean flattens that away."
* value[x] only CodeableConcept
* value[x] 1..1
* value[x] from OpiumActVS (required)
* value[x] ^short = "The Opiumwet code; absent when the product does not fall under the Opiumwet"
* value[x] ^comment = "Prescriptor reports only a yes/no today, which corresponds to rubriek 72 nr 2, so only code 2 is emitted. Codes 65 and 107 become available without a breaking change if the upstream is enriched."
