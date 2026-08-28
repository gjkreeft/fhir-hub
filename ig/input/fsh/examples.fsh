// The examples from the Implementation Guide, as validatable instances.
//
// The point is that the documentation cannot drift from the profiles: if an example here
// stops satisfying its profile, the build says so. These mirror the payloads in
// IMPLEMENTATION_GUIDE.md — keep the two in step.

Instance: ExamplePatient
InstanceOf: FhirHubPatient
Usage: #inline
* gender = #female
* birthDate = "1980-01-01"

Instance: ExampleAllergy
InstanceOf: FhirHubAllergyIntolerance
Usage: #inline
// Mandatory in base R4 and not read by fhir-hub; there is no Patient resource to point at,
// because the patient travels as a sibling parameter rather than a contained resource.
* patient.extension[0].url = "http://hl7.org/fhir/StructureDefinition/data-absent-reason"
* patient.extension[0].valueCode = #unknown
// Required by base R4 invariant ait-1, and likewise not read by fhir-hub.
* clinicalStatus = http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical#active
* code.coding[0].system = "urn:oid:2.16.840.1.113883.2.4.4.1.750"
* code.coding[0].code = #10499

Instance: ExampleContraIndication
InstanceOf: FhirHubCondition
Usage: #inline
* subject.extension[0].url = "http://hl7.org/fhir/StructureDefinition/data-absent-reason"
* subject.extension[0].valueCode = #unknown
* code.coding[0].system = "urn:oid:2.16.840.1.113883.2.4.4.1.902.40"
* code.coding[0].code = #228

Instance: ExampleCurrentMedication
InstanceOf: FhirHubMedicationStatement
Usage: #inline
* status = #active
* subject.extension[0].url = "http://hl7.org/fhir/StructureDefinition/data-absent-reason"
* subject.extension[0].valueCode = #unknown
* medicationCodeableConcept.coding[0].system = "urn:oid:2.16.840.1.113883.2.4.4.10"
* medicationCodeableConcept.coding[0].code = #18996

// The determination medication surveillance turns on: 666 of the current MFB rules test the
// nierfunctie. Note the unit — an eGFR is normalised per 1.73 m2 and is sent as such.
Instance: ExampleLabResult
InstanceOf: FhirHubLabObservation
Usage: #inline
* status = #final
* code.coding[0].system = "http://loinc.org"
* code.coding[0].code = #62238-1
* code.coding[0].display = "eGFR volgens CKD-EPI"
* effectiveDateTime = "2024-07-04"
* valueQuantity.value = 65
* valueQuantity.unit = "mL/min/1.73m2"
* valueQuantity.system = "http://unitsofmeasure.org"
* valueQuantity.code = #"mL/min/{1.73_m2}"

Instance: ExampleFormularySessionInput
InstanceOf: FhirHubFormularySessionInput
Usage: #example
Title: "$formulary-session request"
Description: "A formulary session with one allergy, one contra-indication, one current drug and one lab result."
* parameter[patient].name = "patient"
* parameter[patient].resource = ExamplePatient
* parameter[reason].name = "reason"
* parameter[reason].valueCodeableConcept.coding[0].system = "urn:oid:2.16.840.1.113883.2.4.4.31.1"
* parameter[reason].valueCodeableConcept.coding[0].code = #A01
* parameter[endSessionUrl].name = "endSessionUrl"
* parameter[endSessionUrl].valueUrl = "https://host.example/done"
* parameter[xisId].name = "xisId"
* parameter[xisId].valueString = "xis-001"
* parameter[xisVersion].name = "xisVersion"
* parameter[xisVersion].valueString = "1.0"
* parameter[allergyIntolerance][0].name = "allergyIntolerance"
* parameter[allergyIntolerance][0].resource = ExampleAllergy
* parameter[condition][0].name = "condition"
* parameter[condition][0].resource = ExampleContraIndication
* parameter[medicationStatement][0].name = "medicationStatement"
* parameter[medicationStatement][0].resource = ExampleCurrentMedication
* parameter[observation][0].name = "observation"
* parameter[observation][0].resource = ExampleLabResult

Instance: ExampleSessionOutput
InstanceOf: FhirHubSessionOutput
Usage: #example
Title: "Session response"
Description: "What both session operations return."
* parameter[sessionId].name = "sessionId"
* parameter[sessionId].valueString = "sess-abc-123"
* parameter[url].name = "url"
* parameter[url].valueUrl = "https://evs.prescriptor.nl/web_current/index.php?sk=sess-abc-123"

// One G-Standaard coding, at the level the prescription was written at, plus the ATC. Exactly
// one, which is easy to get wrong in this direction: it is the INPUT side that expands a code
// into a PRK + GPK set, for surveillance, and none of that comes back out.
Instance: ExamplePrescription
InstanceOf: FhirHubPrescription
Usage: #inline
* status = #active
* intent = #order
* subject.extension[0].url = "http://hl7.org/fhir/StructureDefinition/data-absent-reason"
* subject.extension[0].valueCode = #unknown
* extension[opiumAct].url = "http://spec.digitalis.nl/fhir/StructureDefinition/ext-MedicationRequest.OpiumActClassification"
* extension[opiumAct].valueCodeableConcept = GStandaardBijzonderKenmerk#2 "Product valt onder Opiumwet in volle omvang"
* medicationCodeableConcept.coding[0].system = "urn:oid:2.16.840.1.113883.2.4.4.10"
* medicationCodeableConcept.coding[0].code = #18996
* medicationCodeableConcept.coding[0].display = "PARACETAMOL ZETPIL 1000MG"
* medicationCodeableConcept.coding[1].system = "http://www.whocc.no/atc"
* medicationCodeableConcept.coding[1].code = #N02BE01
* medicationCodeableConcept.text = "PARACETAMOL ZETPIL 1000MG"
* dosageInstruction[0].extension[0].url = "http://spec.digitalis.nl/fhir/StructureDefinition/ext-Dosage.CodedDirections"
* dosageInstruction[0].extension[0].valueString = "3-4D1S; gedurende max. 1 maand"
* dosageInstruction[0].text = "3 tot 4 maal per dag 1 stuk"
* dispenseRequest.quantity.value = 15
* dispenseRequest.quantity.system = "urn:oid:2.16.840.1.113883.2.4.4.1.900.2"
* dispenseRequest.quantity.code = #ST
* dispenseRequest.quantity.unit = "ST"
* dispenseRequest.expectedSupplyDuration.value = 30
* dispenseRequest.expectedSupplyDuration.unit = "dag"
* dispenseRequest.expectedSupplyDuration.system = "http://unitsofmeasure.org"
* dispenseRequest.expectedSupplyDuration.code = #d

Instance: ExampleAdvice
InstanceOf: FhirHubAdvice
Usage: #inline
* status = #completed
* category[0] = http://terminology.hl7.org/CodeSystem/communication-category#instruction
* payload[0].contentString = "Neem in bij voorkeur met wat water."

// The other payload form: advice that is a pointer rather than prose. Prescriptor reports both
// as text, and the text/plain versus text/uri-list distinction becomes the payload type.
Instance: ExampleAdviceLink
InstanceOf: FhirHubAdvice
Usage: #inline
* status = #completed
* category[0] = http://terminology.hl7.org/CodeSystem/communication-category#instruction
* payload[0].contentAttachment.contentType = #text/uri-list
* payload[0].contentAttachment.url = "https://www.thuisarts.nl/paracetamol"

Instance: ExampleResultBundle
InstanceOf: FhirHubResultBundle
Usage: #example
Title: "$session-result response"
Description: "One prescription, one prose advice and one pointer to patient information."
* type = #collection
// A urn:uuid identity: these resources exist only inside this Bundle. fhir-hub stores nothing
// and there is no endpoint to fetch a prescription back from.
* entry[prescription][0].fullUrl = "urn:uuid:9f1b2d34-5a67-4c89-b012-3456789abcde"
* entry[prescription][0].resource = ExamplePrescription
* entry[advice][0].fullUrl = "urn:uuid:1c2d3e4f-5678-4a9b-8c0d-1e2f3a4b5c6d"
* entry[advice][0].resource = ExampleAdvice
* entry[advice][1].fullUrl = "urn:uuid:7b8c9d01-2e3f-4a5b-9c6d-7e8f9a0b1c2d"
* entry[advice][1].resource = ExampleAdviceLink

// ---------------------------------------------------------------------------
// $createrx-session: the same context, plus a prescription to open for editing.
//
// This is the payload the Implementation Guide documents under `prescription`, and the reason
// it is here is that it was the one example the build never checked. Note what a conformant
// prescription needs beyond the product code: status, intent and subject are mandatory in base
// R4 and unread here, so subject carries a data-absent-reason like everything else the host
// has nothing to point at.
//
// Note also the level: PRK or HPK, never GPK. $session-result can return a prescription coded
// at GPK level, and that one cannot be handed back without resolving it first.
// ---------------------------------------------------------------------------

Instance: ExamplePrescriptionInput
InstanceOf: FhirHubPrescriptionInput
Usage: #inline
* status = #active
* intent = #order
* subject.extension[0].url = "http://hl7.org/fhir/StructureDefinition/data-absent-reason"
* subject.extension[0].valueCode = #unknown
* medicationCodeableConcept.coding[0].system = "urn:oid:2.16.840.1.113883.2.4.4.10"
* medicationCodeableConcept.coding[0].code = #18996
* medicationCodeableConcept.coding[0].display = "PARACETAMOL ZETPIL 1000MG"
* medicationCodeableConcept.coding[1].system = "http://www.whocc.no/atc"
* medicationCodeableConcept.coding[1].code = #N02BE01
* dosageInstruction[0].extension[0].url = "http://spec.digitalis.nl/fhir/StructureDefinition/ext-Dosage.CodedDirections"
* dosageInstruction[0].extension[0].valueString = "3-4D1S; gedurende max. 1 maand"
* dispenseRequest.quantity.value = 15
* dispenseRequest.quantity.system = "urn:oid:2.16.840.1.113883.2.4.4.1.900.2"
* dispenseRequest.quantity.code = #ST

Instance: ExampleCreateRxSessionInput
InstanceOf: FhirHubCreateRxSessionInput
Usage: #example
Title: "$createrx-session request"
Description: "A CreateRx session opened on a prescription the host already holds. No reason for encounter: CreateRx prescribes without a formulary lookup."
* parameter[patient].name = "patient"
* parameter[patient].resource = ExamplePatient
* parameter[endSessionUrl].name = "endSessionUrl"
* parameter[endSessionUrl].valueUrl = "https://host.example/done"
* parameter[xisId].name = "xisId"
* parameter[xisId].valueString = "xis-001"
* parameter[xisVersion].name = "xisVersion"
* parameter[xisVersion].valueString = "1.0"
* parameter[medicationStatement][0].name = "medicationStatement"
* parameter[medicationStatement][0].resource = ExampleCurrentMedication
* parameter[prescription].name = "prescription"
* parameter[prescription].resource = ExamplePrescriptionInput
