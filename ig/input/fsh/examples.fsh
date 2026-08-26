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
* code.coding[0].system = "http://digitalis.nl/fhir/CodeSystem/gstandaard-snk"
* code.coding[0].code = #10499

Instance: ExampleContraIndication
InstanceOf: FhirHubCondition
Usage: #inline
* subject.extension[0].url = "http://hl7.org/fhir/StructureDefinition/data-absent-reason"
* subject.extension[0].valueCode = #unknown
* code.coding[0].system = "http://digitalis.nl/fhir/CodeSystem/gstandaard-contraindicatie"
* code.coding[0].code = #228

Instance: ExampleCurrentMedication
InstanceOf: FhirHubMedicationStatement
Usage: #inline
* status = #active
* subject.extension[0].url = "http://hl7.org/fhir/StructureDefinition/data-absent-reason"
* subject.extension[0].valueCode = #unknown
* medicationCodeableConcept.coding[0].system = "urn:oid:2.16.840.1.113883.2.4.4.10"
* medicationCodeableConcept.coding[0].code = #18996

Instance: ExampleLabResult
InstanceOf: FhirHubLabObservation
Usage: #inline
* status = #final
* code.coding[0].system = "urn:oid:2.16.840.1.113883.2.4.4.30.45"
* code.coding[0].code = #ALDOB
* effectiveDateTime = "2024-07-04"
* valueQuantity.value = 10

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
* parameter[url].valueUrl = "https://alfa.prescriptor-test.nl/api/prescriptor/session/sess-abc-123"

Instance: ExamplePrescription
InstanceOf: FhirHubPrescription
Usage: #inline
* status = #active
* intent = #order
* subject.extension[0].url = "http://hl7.org/fhir/StructureDefinition/data-absent-reason"
* subject.extension[0].valueCode = #unknown
* medicationCodeableConcept.coding[0].system = "urn:oid:2.16.840.1.113883.2.4.4.10"
* medicationCodeableConcept.coding[0].code = #18996
* medicationCodeableConcept.coding[0].display = "PARACETAMOL TABLET 500MG"
* medicationCodeableConcept.coding[1].system = "urn:oid:2.16.840.1.113883.2.4.4.1"
* medicationCodeableConcept.coding[1].code = #111111
* medicationCodeableConcept.text = "PARACETAMOL TABLET 500MG"
* dosageInstruction[0].extension[0].url = "http://digitalis.nl/fhir/StructureDefinition/ext-Dosage.CodedDirections"
* dosageInstruction[0].extension[0].valueString = "3-4D1S; gedurende max. 1 maand"
* dosageInstruction[0].text = "3 tot 4 maal daags 1 stuk; gedurende max. 1 maand"
* dispenseRequest.quantity.value = 15
* dispenseRequest.quantity.system = "urn:oid:2.16.840.1.113883.2.4.4.1.900.2"
* dispenseRequest.quantity.code = #ST
* dispenseRequest.quantity.unit = "ST"

Instance: ExampleAdvice
InstanceOf: FhirHubAdvice
Usage: #inline
* status = #completed
* category[0] = http://terminology.hl7.org/CodeSystem/communication-category#instruction
* payload[0].contentString = "Neem de tabletten met voldoende water in."

Instance: ExampleResultBundle
InstanceOf: FhirHubResultBundle
Usage: #example
Title: "$session-result response"
Description: "One prescription and one advice text."
* type = #collection
// A urn:uuid identity: these resources exist only inside this Bundle. fhir-hub stores nothing
// and there is no endpoint to fetch a prescription back from.
* entry[prescription][0].fullUrl = "urn:uuid:9f1b2d34-5a67-4c89-b012-3456789abcde"
* entry[prescription][0].resource = ExamplePrescription
* entry[advice][0].fullUrl = "urn:uuid:1c2d3e4f-5678-4a9b-8c0d-1e2f3a4b5c6d"
* entry[advice][0].resource = ExampleAdvice
