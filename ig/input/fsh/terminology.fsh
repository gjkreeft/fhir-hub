// Code systems and value sets for the fhir-hub payloads.
//
// The OID-based systems below (ICPC-1 NL, PRK, HPK, GPK, NHG Tabel 45) are national
// identifiers and are NOT defined here — they belong to HL7 NL and the NHG, and every OID was
// taken from the HL7 NL OID register rather than from memory. Only the systems Digitalis
// actually mints are defined as CodeSystems in this IG.

Alias: $icpc      = urn:oid:2.16.840.1.113883.2.4.4.31.1
Alias: $prk       = urn:oid:2.16.840.1.113883.2.4.4.10
Alias: $hpk       = urn:oid:2.16.840.1.113883.2.4.4.7
Alias: $gpk       = urn:oid:2.16.840.1.113883.2.4.4.1
Alias: $nhg45     = urn:oid:2.16.840.1.113883.2.4.4.30.45
Alias: $basiseenheid = urn:oid:2.16.840.1.113883.2.4.4.1.900.2
Alias: $atc       = http://www.whocc.no/atc
Alias: $ssk       = urn:oid:2.16.840.1.113883.2.4.4.1.725
Alias: $snk       = urn:oid:2.16.840.1.113883.2.4.4.1.750
Alias: $oggrp     = urn:oid:2.16.840.1.113883.2.4.4.1.902.122
Alias: $cicode    = urn:oid:2.16.840.1.113883.2.4.4.1.902.40
Alias: $ucum      = http://unitsofmeasure.org

// ---------------------------------------------------------------------------
// Digitalis-local code systems: DEPRECATED, and kept only for migration.
//
// The four G-Standaard subsystems below were placeholders while the national OIDs were
// unpinned. They now are pinned — see Systems.java for the provenance of each — so the value
// sets further down include the national OID *and* the Digitalis URI, and fhir-hub emits the
// national one. Send the OIDs.
//
// These are not deleted because sixteen integrating systems already send them. Retire them
// once those integrators have moved; the value sets are where to do it.
//
// Note the explicit ^url on each: they stay on the OLD host, digitalis.nl, and do NOT follow
// the canonical to spec.digitalis.nl. A retired identifier does not move — these URLs exist
// only to name what integrators already send, and relocating them would invent a third form
// to support rather than retiring the second.
// ---------------------------------------------------------------------------

CodeSystem: GStandaardSsk
Id: gstandaard-ssk
// Pinned to the pre-move host on purpose; see above.
Title: "G-Standaard SSK (stofnaam-samenstelling)"
Description: "DEPRECATED placeholder for urn:oid:2.16.840.1.113883.2.4.4.1.725 (G-standaard Stofnaamcode i.c.m. toedieningsweg). Accepted on input; not emitted."
* ^url = "http://digitalis.nl/fhir/CodeSystem/gstandaard-ssk"
* ^status = #retired
* ^content = #not-present
* ^caseSensitive = true

CodeSystem: GStandaardSnk
Id: gstandaard-snk
// Pinned to the pre-move host on purpose; see above.
Title: "G-Standaard SNK (stofnaam)"
Description: "DEPRECATED placeholder for urn:oid:2.16.840.1.113883.2.4.4.1.750 (G-Standaard generieke namen, bestand 750). Accepted on input; not emitted."
* ^url = "http://digitalis.nl/fhir/CodeSystem/gstandaard-snk"
* ^status = #retired
* ^content = #not-present
* ^caseSensitive = true

CodeSystem: GStandaardOggrp
Id: gstandaard-oggrp
// Pinned to the pre-move host on purpose; see above.
Title: "G-Standaard OGGrp (ongewenste medicatiegroep)"
Description: "DEPRECATED placeholder for urn:oid:2.16.840.1.113883.2.4.4.1.902.122 (G-standaard Ongewenste medicatiegroepen, thesaurus 122). Accepted on input; not emitted."
* ^url = "http://digitalis.nl/fhir/CodeSystem/gstandaard-oggrp"
* ^status = #retired
* ^content = #not-present
* ^caseSensitive = true

CodeSystem: GStandaardContraIndicatie
Id: gstandaard-contraindicatie
// Pinned to the pre-move host on purpose; see above.
Title: "G-Standaard contra-indicatie (CICode)"
Description: "DEPRECATED placeholder for urn:oid:2.16.840.1.113883.2.4.4.1.902.40 (G-Standaard Contra Indicaties, thesaurus 40). Accepted on input; not emitted."
* ^url = "http://digitalis.nl/fhir/CodeSystem/gstandaard-contraindicatie"
* ^status = #retired
* ^content = #not-present
* ^caseSensitive = true

// Unlike the four above, this one is complete: BST401T rubriek 72 defines exactly these three
// Opiumwet codes. Prescriptor reports only a yes/no today, so fhir-hub emits only code 2.
CodeSystem: GStandaardBijzonderKenmerk
Id: gstandaard-bijzonder-kenmerk
Title: "G-Standaard bijzondere kenmerken (Opiumwet)"
Description: "The Opiumwet subset of G-Standaard bestand BST401T (thesaurus BST922T). No OID is registered for this table, so a Digitalis-local URI is used."
* ^status = #draft
* ^content = #complete
* ^caseSensitive = true
* #2   "Product valt onder Opiumwet in volle omvang"
* #65  "Product valt onder Opiumwet; afhandeling als UR"
* #107 "Grondstof valt onder Opiumwet in volle omvang"

// ---------------------------------------------------------------------------
// Value sets.
//
// Every one of these binds the SYSTEM rather than the code: the G-Standaard tables are
// licensed and enormous, so their code systems are content: not-present and a validator can
// check only that the coding comes from a system this interface routes. That is exactly the
// check SessionParametersMapper performs.
//
// CodeSystemRegistry additionally accepts the bare upstream token ("SSK", "PRK", "ICPC", ...)
// as a Coding.system, so a host that has not adopted the URIs is not blocked. That
// accommodation is deliberately NOT blessed here: it is a migration path, not a conformant
// payload, and a profile that endorsed it would make the token permanent.
// ---------------------------------------------------------------------------

ValueSet: AllergyCausativeAgentVS
Id: allergy-causative-agent
Title: "Allergy causative agent (SSK, SNK, OGGrp)"
Description: "Causative agents fhir-hub routes to Prescriptor. Which member carries which subsystem is set by prescriptor-api's OpenSessionRequestBuilder.getAllergies."
* ^status = #draft
* include codes from system $ssk
* include codes from system $snk
* include codes from system $oggrp
// Deprecated, accepted while integrators migrate.
* include codes from system GStandaardSsk
* include codes from system GStandaardSnk
* include codes from system GStandaardOggrp

ValueSet: ContraIndicationVS
Id: contra-indication
Title: "Contra-indication (CICode or ICPC-1 NL)"
Description: "Contra-indications fhir-hub routes to Prescriptor, as either a G-Standaard CICode or an ICPC-1 NL code."
* ^status = #draft
* include codes from system $cicode
* include codes from system $icpc
// Deprecated, accepted while integrators migrate.
* include codes from system GStandaardContraIndicatie

ValueSet: MedicationCodeVS
Id: medication-code
Title: "Medication code (PRK or HPK)"
Description: "Product code levels accepted for current medication. Send one level throughout: the level of the first entry is the one declared upstream for the whole list."
* ^status = #draft
* include codes from system $prk
* include codes from system $hpk

ValueSet: DispensedMedicationCodeVS
Id: dispensed-medication-code
Title: "Prescribed medication code (PRK, GPK, HPK or ATC)"
Description: "Codings that may appear on a MedicationRequest returned by $session-result. PRK and GPK are always present as a pair; HPK and ATC appear when the upstream supplies them."
* ^status = #draft
* include codes from system $prk
* include codes from system $gpk
* include codes from system $hpk
* include codes from system $atc

ValueSet: IcpcVS
Id: icpc-1-nl
Title: "ICPC-1 NL"
Description: "Reason for encounter. The code shape is additionally constrained by an invariant, because ICPC-1 NL is not expanded here."
* ^status = #draft
* include codes from system $icpc

ValueSet: LabDeterminationVS
Id: nhg-tabel-45
Title: "NHG Tabel 45 Diagnostische bepalingen"
Description: "The 8-position sleutelcode: memo (1-4), materiaal (5-6), bijzonderheid (7-8). One coding, not three — the combination is the identity of the determination."
* ^status = #draft
* include codes from system $nhg45

ValueSet: OpiumActVS
Id: opium-act-classification
Title: "Opiumwet classification"
Description: "The Opiumwet codes from G-Standaard BST401T rubriek 72."
* ^status = #draft
* include codes from system GStandaardBijzonderKenmerk
