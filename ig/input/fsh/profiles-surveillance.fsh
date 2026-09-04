// The medication-surveillance contract, served on its own FHIR base at /fhir/surveillance.
//
// THE OPERATION IS NOT IMPLEMENTED. A conformant request is answered with 501; see
// server/SurveillanceOperationProvider.java for why it is not a 200 with an empty result. What
// is published here is the request contract, so that a host can build and validate the payload,
// and so the shape can be reviewed before the rules engine behind it is wired up. Everything in
// this file is therefore `experimental = true`: it may be used to test against and not to
// conclude anything from.
//
// There is deliberately NO RESPONSE PROFILE. DetectedIssue is where this is heading, but a
// profile with nothing behind it is a promise this service cannot keep, and publishing one would
// invite a host to build against a shape nobody has produced a single instance of.
//
// Note what this file does NOT contain: profiles for Patient, MedicationStatement,
// AllergyIntolerance, Condition, Observation or the prescription. Those are the same resources
// the session operations take, so the same profiles bind here — which is the whole argument for
// the two contracts sharing a service rather than a copy of each other.

// Surveillance over nothing is the one answer a prescriber cannot use, and an empty request is
// the easiest way to get it: a host that built its parameter names wrong would otherwise be told
// "no signals" about a patient whose medication was never sent. Same reasoning as the closed
// slicing above it, and as the 400 on an unresolvable drug code.
Invariant: fhirhub-something-to-check
Description: "Send at least one prescription to check, or at least one current-medication entry to check for interactions among. A request carrying neither has nothing to evaluate."
Severity: #error
Expression: "parameter.where(name = 'prescription' or name = 'medicationStatement').exists()"

Profile: FhirHubSurveillanceInput
Parent: Parameters
Id: fhirhub-SurveillanceInput
Title: "$check-medication input"
Description: "The body of POST /fhir/surveillance/$check-medication: the patient's context plus the prescriptions to check against it. PUBLISHED BUT NOT IMPLEMENTED — a conformant request is answered with 501 Not Implemented, and no conclusion about a patient's medication may be drawn from it."
* ^status = #draft
// Not decoration: `experimental` is the FHIR-native way to say "test against this, do not rely
// on it". The service enforces the profile today; the operation behind it does nothing.
* ^experimental = true
* obeys fhirhub-something-to-check
// Closed, for the same reason the session requests are: this interface would ignore a parameter
// it does not recognise, and a host that sends "medicationstatements" would be told there were
// no signals for a list it never managed to send. See profiles-parameters.fsh.
* parameter ^slicing.discriminator[0].type = #value
* parameter ^slicing.discriminator[0].path = "name"
* parameter ^slicing.rules = #closed
* parameter contains
    patient 1..1 and
    xisId 1..1 and
    xisVersion 1..1 and
    prescription 0..* and
    medicationStatement 0..* and
    allergyIntolerance 0..* and
    condition 0..* and
    observation 0..*
* parameter[patient].name = "patient" (exactly)
* parameter[patient].resource 1..1
* parameter[patient].resource only FhirHubPatient
* parameter[patient].value[x] 0..0
* parameter[patient].part 0..0
* parameter[xisId].name = "xisId" (exactly)
* parameter[xisId].value[x] 1..1
* parameter[xisId].value[x] only string
* parameter[xisId].value[x] obeys fhirhub-non-blank
* parameter[xisId].value[x] ^short = "Your system id; identifies your product, not the practice"
* parameter[xisId].value[x] ^comment = "The same value you send to the session operations, and required here for the same reason: a log line has to be attributable to a supplier and a release. Never forwarded upstream."
* parameter[xisId].resource 0..0
* parameter[xisId].part 0..0
* parameter[xisVersion].name = "xisVersion" (exactly)
* parameter[xisVersion].value[x] 1..1
* parameter[xisVersion].value[x] only string
* parameter[xisVersion].value[x] obeys fhirhub-non-blank
* parameter[xisVersion].resource 0..0
* parameter[xisVersion].part 0..0
* parameter[prescription].name = "prescription" (exactly)
* parameter[prescription].resource 1..1
* parameter[prescription].resource only FhirHubPrescriptionInput
* parameter[prescription].value[x] 0..0
* parameter[prescription].part 0..0
* parameter[prescription] ^short = "The prescriptions to check; repeat for more than one"
* parameter[prescription] ^comment = "The same profile $createrx-session takes, so a prescription can be checked and then handed to a session without being reshaped. Repeatable here where the session accepts one, because a host may want a whole proposed regimen weighed at once — and because two proposed drugs can interact with each other and not with anything the patient already takes."
* parameter[medicationStatement].name = "medicationStatement" (exactly)
* parameter[medicationStatement].resource 1..1
* parameter[medicationStatement].resource only FhirHubMedicationStatement
* parameter[medicationStatement].value[x] 0..0
* parameter[medicationStatement].part 0..0
* parameter[medicationStatement] ^short = "The patient's current medication"
* parameter[medicationStatement] ^comment = "Optional only in the sense that the profile cannot know what the patient takes. Sending an incomplete list is the one error nothing downstream can detect: the answer will be about the list you sent, not about the patient."
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
* parameter[observation].name = "observation" (exactly)
* parameter[observation].resource 1..1
* parameter[observation].resource only FhirHubLabObservation
* parameter[observation].value[x] 0..0
* parameter[observation].part 0..0
