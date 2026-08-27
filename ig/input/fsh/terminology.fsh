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
// The one code system Digitalis mints. The G-Standaard subsystems are NOT defined here, and
// must not be: a CodeSystem this IG defines is checked more strictly than one it does not
// know. The G-Standaard tables are licensed, so any definition would have to be
// content: not-present, which makes a value set including it unexpandable and turns every
// coding in it into an error. An entirely unknown system falls to
// UnknownCodeSystemWarningValidationSupport and becomes a warning instead, which is the only
// way a required binding onto a licensed table can be satisfied. TerminologyEnforcementTest
// pins that.
//
// BST401T rubriek 72 is different: it defines exactly these three Opiumwet codes, so it can be
// content: complete. Prescriptor reports only a yes/no today, so fhir-hub emits only code 2.
// ---------------------------------------------------------------------------

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
// licensed and enormous, they are not distributed with these profiles, and so a validator can
// check only that the coding comes from a system this interface routes. That is exactly the
// check SessionParametersMapper performs.
//
// There is exactly ONE accepted system per subsystem, and it is a URI. The upstream tokens
// ("SSK", "PRK", "ICPC", ...) are XML attribute names in the DigitalisRx document, not
// identifiers a caller may put in Coding.system, so neither these bindings nor
// CodeSystemRegistry accept one — with or without runtime validation. Blessing the token here
// would put a non-FHIR identifier on the wire permanently, and it would be the form a host
// discovers first.
// ---------------------------------------------------------------------------

ValueSet: AllergyCausativeAgentVS
Id: allergy-causative-agent
Title: "Allergy causative agent (SSK, SNK, OGGrp)"
Description: "Causative agents fhir-hub routes to Prescriptor. Which member carries which subsystem is set by prescriptor-api's OpenSessionRequestBuilder.getAllergies."
* ^status = #draft
* include codes from system $ssk
* include codes from system $snk
* include codes from system $oggrp

ValueSet: ContraIndicationVS
Id: contra-indication
Title: "Contra-indication (CICode or ICPC-1 NL)"
Description: "Contra-indications fhir-hub routes to Prescriptor, as either a G-Standaard CICode or an ICPC-1 NL code."
* ^status = #draft
* include codes from system $cicode
* include codes from system $icpc

ValueSet: MedicationCodeVS
Id: medication-code
Title: "Medication code (PRK or HPK)"
Description: "Product code levels accepted for current medication. Each entry carries its own level, so PRK and HPK may be mixed within one list."
* ^status = #draft
* include codes from system $prk
* include codes from system $hpk

ValueSet: DispensedMedicationCodeVS
Id: dispensed-medication-code
Title: "Prescribed medication code (PRK, GPK, HPK or ATC)"
Description: "Codings that may appear on a MedicationRequest returned by $session-result. Exactly one of PRK, GPK and HPK is present — the level the prescription was written at — with an ATC beside it when the upstream supplies one."
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
