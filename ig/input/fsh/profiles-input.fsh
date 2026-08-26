// Profiles for the resources a host sends into $formulary-session and $createrx-session.
//
// Parent is plain R4, not nl-core. That is a verified decision, not a shortcut — see the
// IG's README and the Implementation Guide's Profiles section for the four specific reasons.
//
// A note that applies to almost every profile below. Base R4 makes several elements mandatory
// that fhir-hub never reads: AllergyIntolerance.patient, Condition.subject,
// MedicationStatement.status and .subject, Observation.status. A profile can only constrain,
// never relax, so a conformant payload must carry them even though this interface ignores
// them. Where a host has nothing to say, the data-absent-reason extension is the honest
// filler — the same idiom ResultBundleMapper already uses for MedicationRequest.subject on
// the way out.

Alias: $adminGender = http://hl7.org/fhir/administrative-gender
Alias: $dar = http://hl7.org/fhir/StructureDefinition/data-absent-reason

ValueSet: PatientGenderVS
Id: patient-gender
Title: "Administrative gender accepted by Prescriptor"
Description: "Prescriptor's PatientGender has three values — M, F and X — so FHIR's 'other' has no upstream equivalent."
* ^status = #draft
* $adminGender#male "Male"
* $adminGender#female "Female"
* $adminGender#unknown "Unknown"

Invariant: fhirhub-icpc-shape
Description: "An ICPC-1 NL code is a letter and two digits, optionally followed by a dot and two more (A01, U71.01)."
Severity: #error
Expression: "coding.where(system = 'urn:oid:2.16.840.1.113883.2.4.4.31.1').all(code.matches('^[A-Z][0-9]{2}(\\\\.[0-9]{2})?$'))"

Invariant: fhirhub-product-code
Description: "At least one coding must be a PRK or an HPK: an ATC alone cannot be dispensed against."
Severity: #error
Expression: "coding.where(system = 'urn:oid:2.16.840.1.113883.2.4.4.10' or system = 'urn:oid:2.16.840.1.113883.2.4.4.7').exists()"

// ---------------------------------------------------------------------------

Profile: FhirHubPatient
Parent: Patient
Id: fhirhub-Patient
Title: "fhir-hub Patient"
Description: "The demographics a session needs. Only gender and birthDate are read; anything else you send is ignored and never forwarded to Prescriptor or stored."
* ^status = #draft
* gender 1..1
* gender from PatientGenderVS (required)
* gender ^short = "male, female or unknown"
* gender ^comment = "Send the sex when you know it. Sex-specific surveillance checks cannot fire on 'unknown', and the prescriber is not told that they were skipped. 'other' is rejected rather than coerced: it is a different assertion from 'unknown', and there is no upstream value for it."
* birthDate 1..1
* birthDate ^comment = "Used for age-dependent surveillance."
* name ^comment = "Not read. No name, identifier or address is forwarded to Prescriptor or stored by fhir-hub."

Profile: FhirHubAllergyIntolerance
Parent: AllergyIntolerance
Id: fhirhub-AllergyIntolerance
Title: "fhir-hub AllergyIntolerance"
Description: "A known hypersensitivity, coded in a G-Standaard subsystem Prescriptor routes."
* ^status = #draft
* code 1..1
* code from AllergyCausativeAgentVS (required)
* code ^short = "SSK, SNK or OGGrp"
* code ^comment = "The first coding in a routed system wins; codings in other systems are ignored. A resource with no routed coding at all is a 400."
* patient ^comment = "Mandatory in base R4 and not read by fhir-hub. Send a data-absent-reason of 'unknown' if you have nothing to reference."
* clinicalStatus ^comment = "Required by the base R4 invariant ait-1 whenever verificationStatus is not entered-in-error, and not read by fhir-hub."

Profile: FhirHubCondition
Parent: Condition
Id: fhirhub-Condition
Title: "fhir-hub Condition"
Description: "A contra-indication, coded as a G-Standaard CICode or an ICPC-1 NL code."
* ^status = #draft
* code 1..1
* code from ContraIndicationVS (required)
* code obeys fhirhub-icpc-shape
* code ^short = "CICode or ICPC-1 NL"
* subject ^comment = "Mandatory in base R4 and not read by fhir-hub. Send a data-absent-reason of 'unknown' if you have nothing to reference."

Profile: FhirHubMedicationStatement
Parent: MedicationStatement
Id: fhirhub-MedicationStatement
Title: "fhir-hub MedicationStatement"
Description: "One entry of the patient's current medication, for medication surveillance."
* ^status = #draft
* medication[x] only CodeableConcept
* medicationCodeableConcept 1..1
* medicationCodeableConcept from MedicationCodeVS (required)
* medicationCodeableConcept ^short = "PRK or HPK"
* medicationCodeableConcept ^comment = "Every code is resolved against the G-Standaard before the session is opened. A code that cannot be resolved fails the whole request with a 400 naming it — it is not skipped, because surveillance over an incomplete list answers 'no interaction found', which a prescriber cannot distinguish from a genuine all-clear. Send one level throughout: the level of the first entry is the one declared upstream for the whole list."
* status ^comment = "Mandatory in base R4 and not read by fhir-hub."
* subject ^comment = "Mandatory in base R4 and not read by fhir-hub. Send a data-absent-reason of 'unknown' if you have nothing to reference."

Profile: FhirHubLabObservation
Parent: Observation
Id: fhirhub-LabObservation
Title: "fhir-hub laboratory Observation"
Description: "A laboratory determination, coded with the 8-position NHG Tabel 45 sleutelcode."
* ^status = #draft
* code 1..1
* code from LabDeterminationVS (required)
* code ^short = "One NHG Tabel 45 sleutelcode"
* code ^comment = "Send the composite code as ONE coding: memo (positions 1-4), materiaal (5-6) and bijzonderheid (7-8) belong together — the combination is the identity of the determination. Do not split them across three codings or three resources. Codes shorter than 8 positions are padded. Unlike the other systems, the short name is not accepted here."
* effective[x] only dateTime
* effectiveDateTime 1..1
* value[x] only Quantity or string or boolean or integer or time or dateTime
* value[x] 1..1
* value[x] ^comment = "A Quantity's unit is ignored — the upstream carries no unit — so send the value in the unit the determination is defined in rather than converting. CodeableConcept, Range, Ratio, SampledData and Period are excluded because the mapper reads only a Quantity or a primitive."
* status ^comment = "Mandatory in base R4 and not read by fhir-hub."

Profile: FhirHubPrescriptionInput
Parent: MedicationRequest
Id: fhirhub-PrescriptionInput
Title: "fhir-hub prescription input"
Description: "A prescription the host already holds, handed to $createrx-session to open for editing. The mirror image of what $session-result returns, so a host can hand back a prescription it received earlier without reshaping it."
* ^status = #draft
* medication[x] only CodeableConcept
* medicationCodeableConcept 1..1
* medicationCodeableConcept from PrescriptionInputMedicationVS (required)
* medicationCodeableConcept obeys fhirhub-product-code
* medicationCodeableConcept ^short = "PRK or HPK, optionally with an ATC"
* dosageInstruction.extension contains CodedDirections named codedDirections 0..1
* dosageInstruction.extension[codedDirections] ^short = "The NHG Tabel 25 instruction, read in preference to Dosage.text"
* dosageInstruction.text ^comment = "Used only when the CodedDirections extension is absent."
* dispenseRequest.quantity ^comment = "Quantity.code is read in preference to Quantity.unit."

ValueSet: PrescriptionInputMedicationVS
Id: prescription-input-medication
Title: "Prescription input medication code (PRK, HPK or ATC)"
Description: "Codings accepted on the prescription handed to $createrx-session. An ATC may accompany the product code but cannot stand alone."
* ^status = #draft
* include codes from system urn:oid:2.16.840.1.113883.2.4.4.10
* include codes from system urn:oid:2.16.840.1.113883.2.4.4.7
* include codes from system http://www.whocc.no/atc
