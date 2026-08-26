// The request and response payloads themselves.
//
// The generated OperationDefinitions already name every parameter with its type and
// cardinality. These profiles add what an OperationDefinition cannot say: what has to be true
// INSIDE each parameter, by pointing parameter.resource at the resource profiles.
//
// The slicing is CLOSED on purpose. fhir-hub ignores a parameter it does not recognise, so a
// host that sends "medicationstatement" instead of "medicationStatement" today gets a session
// opened against a silently thinner medication list — the same false-negative that makes an
// unresolvable drug code a 400 rather than a warning. A closed slice turns that typo into a
// validation error before the request is ever sent.

Invariant: fhirhub-http-url
Description: "Only http and https are accepted: the care provider's browser is redirected here, so a custom scheme such as an app deep link is rejected rather than forwarded."
Severity: #error
Expression: "$this.matches('^https?://')"

Invariant: fhirhub-non-blank
Description: "Must contain at least one non-whitespace character."
Severity: #error
Expression: "$this.matches('[^\\\\s]')"

// ---------------------------------------------------------------------------

// The shared base carries the LOOSEST cardinality for the two parameters that differ, so each
// operation can tighten. Deriving CreateRx from the formulary profile would not work: a
// derived profile can only constrain, and reason is 1..1 there.
Profile: FhirHubSessionInput
Parent: Parameters
Id: fhirhub-SessionInput
Title: "Session input (shared base)"
Description: "Everything the two session operations have in common. Use FhirHubFormularySessionInput or FhirHubCreateRxSessionInput rather than this directly."
* ^status = #draft
* parameter ^slicing.discriminator[0].type = #value
* parameter ^slicing.discriminator[0].path = "name"
* parameter ^slicing.rules = #closed
* parameter contains
    patient 1..1 and
    reason 0..1 and
    endSessionUrl 1..1 and
    xisId 1..1 and
    xisVersion 1..1 and
    allergyIntolerance 0..* and
    condition 0..* and
    medicationStatement 0..* and
    observation 0..* and
    prescription 0..1
* parameter[patient].name = "patient" (exactly)
* parameter[patient].resource 1..1
* parameter[patient].resource only FhirHubPatient
* parameter[patient].value[x] 0..0
* parameter[patient].part 0..0
* parameter[reason].name = "reason" (exactly)
* parameter[reason].value[x] 1..1
* parameter[reason].value[x] only CodeableConcept
* parameter[reason].valueCodeableConcept from IcpcVS (required)
* parameter[reason].valueCodeableConcept obeys fhirhub-icpc-shape
* parameter[reason].value[x] ^short = "ICPC-1 NL"
* parameter[reason].value[x] ^comment = "A bare Coding is not accepted: OperationDefinition.parameter.type is a single code, so this parameter cannot be both described and polymorphic."
* parameter[reason].resource 0..0
* parameter[reason].part 0..0
* parameter[endSessionUrl].name = "endSessionUrl" (exactly)
* parameter[endSessionUrl].value[x] 1..1
* parameter[endSessionUrl].value[x] only url
* parameter[endSessionUrl].value[x] obeys fhirhub-http-url
* parameter[endSessionUrl].value[x] ^short = "Where Prescriptor returns the browser"
* parameter[endSessionUrl].resource 0..0
* parameter[endSessionUrl].part 0..0
* parameter[xisId].name = "xisId" (exactly)
* parameter[xisId].value[x] 1..1
* parameter[xisId].value[x] only string
* parameter[xisId].value[x] obeys fhirhub-non-blank
* parameter[xisId].value[x] ^short = "Your system id; identifies your product, not the practice"
* parameter[xisId].value[x] ^comment = "Keep it stable across releases and move only xisVersion. Never forwarded to Prescriptor: it exists so a log line can be attributed to a supplier and a release."
* parameter[xisId].resource 0..0
* parameter[xisId].part 0..0
* parameter[xisVersion].name = "xisVersion" (exactly)
* parameter[xisVersion].value[x] 1..1
* parameter[xisVersion].value[x] only string
* parameter[xisVersion].value[x] obeys fhirhub-non-blank
* parameter[xisVersion].resource 0..0
* parameter[xisVersion].part 0..0
* parameter[allergyIntolerance].name = "allergyIntolerance" (exactly)
* parameter[allergyIntolerance].resource 1..1
* parameter[allergyIntolerance].resource only FhirHubAllergyIntolerance
* parameter[allergyIntolerance].value[x] 0..0
* parameter[allergyIntolerance].part 0..0
* parameter[condition].name = "condition" (exactly)
* parameter[condition].resource 1..1
* parameter[condition].resource only FhirHubCondition
* parameter[condition].value[x] 0..0
* parameter[condition].part 0..0
* parameter[medicationStatement].name = "medicationStatement" (exactly)
* parameter[medicationStatement].resource 1..1
* parameter[medicationStatement].resource only FhirHubMedicationStatement
* parameter[medicationStatement].value[x] 0..0
* parameter[medicationStatement].part 0..0
* parameter[observation].name = "observation" (exactly)
* parameter[observation].resource 1..1
* parameter[observation].resource only FhirHubLabObservation
* parameter[observation].value[x] 0..0
* parameter[observation].part 0..0
* parameter[prescription].name = "prescription" (exactly)
* parameter[prescription].resource 1..1
* parameter[prescription].resource only FhirHubPrescriptionInput
* parameter[prescription].value[x] 0..0
* parameter[prescription].part 0..0

Profile: FhirHubFormularySessionInput
Parent: FhirHubSessionInput
Id: fhirhub-FormularySessionInput
Title: "$formulary-session input"
Description: "The body of POST /fhir/$formulary-session. Opens a formulary session: the care provider picks a treatment for a stated reason for encounter."
* ^status = #draft
* parameter[reason] 1..1
* parameter[reason] ^short = "Required here: a formulary session is opened for a stated reason for encounter"
* parameter[prescription] 0..0
* parameter[prescription] ^short = "Not accepted here; $createrx-session only"
* parameter[prescription] ^comment = "Sending it is a 400, not a silent ignore."

Profile: FhirHubCreateRxSessionInput
Parent: FhirHubSessionInput
Id: fhirhub-CreateRxSessionInput
Title: "$createrx-session input"
Description: "The body of POST /fhir/$createrx-session. Prescribing without a formulary lookup, optionally starting from a prescription the host already holds."
* ^status = #draft
* parameter[reason] ^short = "Optional here: CreateRx prescribes without a formulary lookup"

Profile: FhirHubSessionOutput
Parent: Parameters
Id: fhirhub-SessionOutput
Title: "Session output"
Description: "What both session operations return: the launch URL and the session id the host later polls with."
* ^status = #draft
* parameter ^slicing.discriminator[0].type = #value
* parameter ^slicing.discriminator[0].path = "name"
* parameter ^slicing.rules = #closed
* parameter contains
    sessionId 1..1 and
    url 1..1
* parameter[sessionId].name = "sessionId" (exactly)
* parameter[sessionId].value[x] 1..1
* parameter[sessionId].value[x] only string
* parameter[sessionId].value[x] ^comment = "fhir-hub keeps no session store, so persist this yourself. It cannot be re-fetched."
* parameter[sessionId].resource 0..0
* parameter[sessionId].part 0..0
* parameter[url].name = "url" (exactly)
* parameter[url].value[x] 1..1
* parameter[url].value[x] only url
* parameter[url].value[x] ^comment = "Redirect the care provider's browser here."
* parameter[url].resource 0..0
* parameter[url].part 0..0
