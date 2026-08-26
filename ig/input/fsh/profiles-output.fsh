// What $session-result returns: a collection Bundle of the prescriptions the care provider
// wrote and the advice texts that came with them.
//
// Note what is NOT here: no Patient. fhir-hub is stateless and never stored the Patient the
// host pushed in, so the prescriptions come back without a subject and the host correlates on
// the session id it polled with.

Alias: $commCategory = http://terminology.hl7.org/CodeSystem/communication-category
Alias: $darExt = http://hl7.org/fhir/StructureDefinition/data-absent-reason

Invariant: fhirhub-subject-absent
Description: "subject carries a data-absent-reason rather than a reference: a session result has no patient identity to point at."
Severity: #error
Expression: "extension('http://hl7.org/fhir/StructureDefinition/data-absent-reason').exists()"

Profile: FhirHubResultBundle
Parent: Bundle
Id: fhirhub-ResultBundle
Title: "$session-result output"
Description: "The finished session: zero or more prescriptions and zero or more advice texts. An empty Bundle is a valid answer — the care provider may have prescribed nothing."
* ^status = #draft
* type = #collection (exactly)
* type ^comment = "A collection, not a searchset: this is not the result of a search and has no paging."
* entry ^slicing.discriminator[0].type = #type
* entry ^slicing.discriminator[0].path = "resource"
* entry ^slicing.rules = #closed
* entry contains
    prescription 0..* and
    advice 0..*
* entry[prescription].resource 1..1
* entry[prescription].resource only FhirHubPrescription
* entry[advice].resource 1..1
* entry[advice].resource only FhirHubAdvice
* entry ^comment = "Store this when you receive it. $session-result ends the session upstream, so a second call returns 401 and nothing can be re-fetched."

Profile: FhirHubPrescription
Parent: MedicationRequest
Id: fhirhub-Prescription
Title: "fhir-hub prescription"
Description: "One prescription written in the session."
* ^status = #draft
* status = #active (exactly)
* intent = #order (exactly)
* subject 1..1
* subject obeys fhirhub-subject-absent
* subject ^short = "Always a data-absent-reason of 'unknown', never a reference"
* subject ^comment = "MedicationRequest.subject is 1..1 in base R4, but a session result carries no patient identity: fhir-hub is stateless and never stored the Patient the host pushed in. data-absent-reason is the FHIR idiom for a mandatory element that genuinely cannot be supplied, and is honest about the gap in a way an invented reference would not be. Do not resolve it; correlate on the session id you polled with."
* medication[x] only CodeableConcept
* medicationCodeableConcept 1..1
* medicationCodeableConcept from DispensedMedicationCodeVS (required)
* medicationCodeableConcept ^short = "PRK and GPK as a pair, plus HPK and ATC when the upstream supplies them"
* medicationCodeableConcept ^comment = "CodeableConcept.text carries the product description."
* extension contains OpiumActClassification named opiumAct 0..1
* extension[opiumAct] ^short = "Present only when the product falls under the Opiumwet"
* dosageInstruction.extension contains CodedDirections named codedDirections 0..1
* dosageInstruction.extension[codedDirections] ^short = "The NHG Tabel 25 instruction, verbatim"
* dosageInstruction.extension[codedDirections] ^comment = "This is the authoritative form of the dosing instruction. Dosage.text accompanies it for readers that cannot interpret Tabel 25, and no timing or doseAndRate is derived from it."
* dosageInstruction.timing 0..0
* dosageInstruction.doseAndRate 0..0
* dispenseRequest.quantity.system ^comment = "The G-Standaard thesaurus basiseenheden, not UCUM: 'ST' (stuks) has no UCUM equivalent."
* dispenseRequest.expectedSupplyDuration ^comment = "In days, UCUM code 'd'."

Profile: FhirHubAdvice
Parent: Communication
Id: fhirhub-Advice
Title: "fhir-hub advice"
Description: "One advice text produced by the session, either prose or a pointer to patient information."
* ^status = #draft
* status = #completed (exactly)
* category 1..1
* category = $commCategory#instruction (exactly)
* payload 1..1
* payload.content[x] only string or Attachment
* payload.content[x] ^short = "Prose as a string, or an Attachment with contentType text/uri-list for a pointer"
* payload.content[x] ^comment = "The text/plain versus text/uri-list distinction of the JSON interface becomes the choice of payload type, so no custom content-type field is needed. An Attachment carries the target in Attachment.url."
