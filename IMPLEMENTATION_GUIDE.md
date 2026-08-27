# fhir-hub Implementation Guide

FHIR R4 interface to **Prescriptor 3**. This document is the integration reference: the flow, and
every endpoint with its input and output specification.

- **FHIR version** — R4 (4.0.1). All payloads are `Parameters` or `Bundle`; there is no resource
  REST API and no search.
- **Relationship to the Prescriptor JSON API** — same operations, same semantics and the same HTTP
  status codes, with authentication moved from the request body onto HTTP Basic. If you are moving
  across from it, the differences that affect you are collected under
  [Moving from the JSON API](#moving-from-the-json-api).
- **Statelessness** — no session store, so persist the session id yourself, and store the result
  Bundle when you receive it. Nothing can be re-fetched.
- **Normative status** — this document is the specification in prose. The machine-readable form is
  the implementation guide published under the canonical `http://spec.digitalis.nl/fhir`: every
  payload described here has a `StructureDefinition` you can validate against, and every request is
  checked against its profile before it is processed. See [Profiles](#profiles).

## Table of contents

- [Global flow](#global-flow)
- [Conventions](#conventions)
- [Authentication](#authentication)
- [`GET /fhir/metadata`](#get-fhirmetadata)
- [`POST /fhir/$formulary-session`](#post-fhirformulary-session)
- [`POST /fhir/$createrx-session`](#post-fhircreaterx-session)
- [`GET /fhir/$session-result`](#get-fhirsession-result)
- [Profiles](#profiles)
- [Code systems](#code-systems)
- [Extensions](#extensions)
- [Errors](#errors)
- [Behaviour to design around](#behaviour-to-design-around)
- [Moving from the JSON API](#moving-from-the-json-api)
- [Current limitations](#current-limitations)

## Global flow

Three calls, plus a browser round trip the host does not mediate.

```
Host (XIS/HIS)                fhir-hub                 Prescriptor        G-Standaard
     |                            |                         |                  |
  1  |-- POST $…-session -------->|                         |                  |
     |                            |-- resolve PRK|HPK ------------------------->|
     |                            |<- PRK + GPK (+HPK) -------------------------|
     |                            |-- open session -------->|                  |
     |                            |<- sessionId + url ------|                  |
     |<- Parameters{sessionId,url}-|                         |                  |
     |                            |                         |                  |
  2  |  redirect the user's browser to `url` ----------------->  (Prescriptor UI)
     |                            |                         |                  |
     |  user prescribes; Prescriptor redirects to endSessionUrl <---------------|
     |                            |                         |                  |
  3  |-- GET $session-result ---->|                         |                  |
     |                            |-- request result ------>|  (session ends)  |
     |<- Bundle -------------------|<- drugs + advice -------|                  |
```

1. **Open a session.** The host posts the patient context — demographics, allergies,
   contra-indications, current medication, lab results — and receives a `sessionId` and a `url`.
   Current medication is resolved against the G-Standaard *before* the session is opened, because
   surveillance needs PRK and GPK together.
2. **The care provider works in the Prescriptor UI** at that `url`. fhir-hub is not involved.
   When finished, Prescriptor redirects the browser to the `endSessionUrl` the host supplied.
3. **The host fetches the result** with the `sessionId` and receives a `Bundle` of
   `MedicationRequest` (prescriptions) and `Communication` (patient advice) resources.

These three operations are the entire surface. There is no resource REST API and no search: reads
or writes against `/fhir/Patient`, `/fhir/MedicationRequest` and the like are not supported.

## Conventions

| | |
| --- | --- |
| Base path | `/fhir` |
| Request content type | `application/fhir+json` (`application/fhir+xml` also accepted) |
| Response content type | `application/fhir+json`, pretty-printed |
| Format override | `?_format=json`, `?_format=xml`, `?_format=html` |
| Browser rendering | A request whose highest-ranked `Accept` is `text/html` gets a syntax-highlighted HTML page instead of raw JSON |
| Errors | Always `OperationOutcome` — no endpoint returns a non-FHIR body |

Set `Accept: application/fhir+json` explicitly in machine clients; relying on the default works,
but a browser-style `Accept` header will get HTML.

**A parameter name this interface does not define is a 400, and so is a repeat of a single-valued
one.** The request profiles close the list of parameter names and carry each one's cardinality, so
`medicationstatement` for `medicationStatement` is rejected — *"does not match any known slice
defined in the profile … and slicing is CLOSED"* — rather than silently dropped, and a second
`endSessionUrl` is rejected as *"max allowed = 1, but found 2"*. Both are caught before anything is
sent upstream; see [Profiles](#profiles).

## Authentication

```
Authorization: Basic base64(organization.id ":" organization.key)
```

The two halves are the same organization id and key the JSON API takes in its request body, so no
new credentials are issued for the move.

Credentials are checked by Prescriptor, not here, which has two consequences for you. A wrong pair
reaches you as a **401 on the operation you called**, not as a separate login step — there is no
token to obtain and nothing to refresh. And a licence change takes effect on the next call, so a
401 that appears without a code change is worth checking with Digitalis before you debug your
client.

Unauthenticated paths: `GET /fhir/metadata`, `GET /fhir/OperationDefinition/**` and
`GET /actuator/health/**` — the discovery documents, so you can read what the interface accepts
before your credentials are issued. Everything else requires the header; a missing or malformed one
is a 401 with `WWW-Authenticate: Basic`.

## `GET /fhir/metadata`

The FHIR CapabilityStatement. Unauthenticated, so you can read it before your credentials are
issued.

**Input** — none. **Output** — `CapabilityStatement` listing `formulary-session`,
`createrx-session` and `session-result`.

```bash
curl -sS 'http://localhost:8080/fhir/metadata?_format=json'
```

The statement links each operation to a generated `OperationDefinition`:
`/fhir/OperationDefinition/-s-formulary-session`, `…/-s-createrx-session` and
`…/-s-session-result`. Each describes **every** input parameter with `use: in`, a `type` and a
cardinality, so a request can be generated from the definition alone — `prescription` reads
`max: "0"` on `$formulary-session` and `max: "1"` on `$createrx-session`. Those URLs are readable
unauthenticated, like the statement that advertises them.

The `OperationDefinition`s give you the parameter list; the [profiles](#profiles) give you what has
to be true *inside* each parameter. Use the CapabilityStatement to confirm the operations and the
FHIR version, and this document for the payloads.

## `POST /fhir/$formulary-session`

Opens a **formulary** session: the care provider picks a treatment for a stated reason for
encounter.

### Input

A `Parameters` resource. The cardinalities below are enforced; anything outside them is a 400 that
names the element.

| Parameter | Card. | Type | Notes |
| --- | --- | --- | --- |
| `patient` | 1..1 | `Patient` | `gender` (`male`, `female` or `unknown`) and `birthDate` required — see below |
| `reason` | 1..1 | `CodeableConcept` | ICPC-1 NL. **Required** for this operation |
| `endSessionUrl` | 1..1 | `url` | Where Prescriptor returns the browser. `http` or `https` only |
| `xisId` | 1..1 | `string` | Your system id, non-blank |
| `xisVersion` | 1..1 | `string` | Your release version, non-blank |
| `allergyIntolerance` | 0..* | `AllergyIntolerance` | `code.coding` in SSK, SNK or OGGrp |
| `condition` | 0..* | `Condition` | `code.coding` in CICode or ICPC |
| `medicationStatement` | 0..* | `MedicationStatement` | `medicationCodeableConcept` in PRK or HPK |
| `observation` | 0..* | `Observation` | NHG Tabel 45 determination — see below |
| `prescription` | 0..0 | — | Rejected here with a 400; `$createrx-session` only |

**`patient`** — `gender` must be `male`, `female` or `unknown`. Send the sex when you know it:
sex-specific surveillance checks cannot fire when it is unknown, and the prescriber is not told that
they were skipped.

`other` and an absent `gender` are both a 400. If your record holds `other`, send `unknown`. If it
holds nothing at all, fill the gap before opening a session — this interface will not choose a value
for you.

`birthDate` is required. Nothing else in the `Patient` is read, so you can leave the rest out: no
name, identifier or address is forwarded to Prescriptor or stored here.

**`reason`** — send a `CodeableConcept`; a bare `valueCoding` is rejected, because
`OperationDefinition.parameter.type` is a single code and a declared parameter cannot also be
polymorphic. The `system` must be the ICPC-1 NL OID `urn:oid:2.16.840.1.113883.2.4.4.31.1`, and the
code must match `^[A-Z][0-9]{2}(\.[0-9]{2})?$` (`A01`, `U71.01`); anything else is a 400.

**`endSessionUrl`** — where Prescriptor sends the care provider's browser when the session ends.
Send it as `valueUrl`, and only as `valueUrl`: a `valueString` is refused by the operation binding
and a `valueUri` by the profile. Only `http` and `https` are accepted, so a custom scheme such as an
app deep link is a 400: use an `https` landing page and redirect on from there.

**`xisId` and `xisVersion`** — identify your product, not the practice. Send your own system id and
release version; keep the id stable across releases and move only the version. It is used to trace your calls in this
service's logs when you raise a support question, and is **never forwarded to Prescriptor**.

**`medicationStatement`** — every code is looked up in the G-Standaard. A code that cannot be
resolved **fails the whole request with a 400** naming that code; it is not skipped, and the session
is not opened. So refresh codes against a current G-Standaard before sending, and do not expect to
pass a drug you cannot code — there is no free-text fallback.

**Each entry carries its own code level.** PRK for one drug and HPK for the next is fine, in any
order — every entry is resolved to a PRK + GPK pair before the session opens, so the level you use
per entry is your choice and does not affect the others.

**`observation`** — a lab result. `code.coding` must carry the 8-position NHG Tabel 45 sleutelcode
under `urn:oid:2.16.840.1.113883.2.4.4.30.45` (unlike the other systems, a short name is *not*
accepted here). Send the composite code as **one** coding: memo (positions 1–4), materiaal (5–6) and
bijzonderheid (7–8) belong together — do not split them across three codings or three resources.
Codes shorter than 8 positions are padded for you. `effectiveDateTime` is required.

The value is required, and is one of `valueQuantity`, `valueString`, `valueBoolean`,
`valueInteger`, `valueTime` or `valueDateTime`. `CodeableConcept`, `Range`, `Ratio`, `SampledData`
and `Period` are rejected, because nothing reads them. Any `unit` on a Quantity is **ignored**, so
send the value in the unit the determination is defined in rather than converting.

```jsonc
POST /fhir/$formulary-session
Content-Type: application/fhir+json
Authorization: Basic ...

{
  "resourceType": "Parameters",
  "parameter": [
    { "name": "patient", "resource": {
        "resourceType": "Patient", "gender": "female", "birthDate": "1980-01-01" } },
    { "name": "reason", "valueCodeableConcept": { "coding": [ {
        "system": "urn:oid:2.16.840.1.113883.2.4.4.31.1", "code": "A01" } ] } },
    { "name": "endSessionUrl", "valueUrl": "https://host.example/done" },
    { "name": "xisId",      "valueString": "xis-001" },
    { "name": "xisVersion", "valueString": "1.0" },

    { "name": "allergyIntolerance", "resource": {
        "resourceType": "AllergyIntolerance",
        "clinicalStatus": { "coding": [ {
          "system": "http://terminology.hl7.org/CodeSystem/allergyintolerance-clinical",
          "code": "active" } ] },
        "patient": { "extension": [ {
          "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
          "valueCode": "unknown" } ] },
        "code": { "coding": [ {
          "system": "urn:oid:2.16.840.1.113883.2.4.4.1.750", "code": "10499" } ] } } },
    { "name": "condition", "resource": {
        "resourceType": "Condition",
        "subject": { "extension": [ {
          "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
          "valueCode": "unknown" } ] },
        "code": { "coding": [ {
          "system": "urn:oid:2.16.840.1.113883.2.4.4.1.902.40",
          "code": "228" } ] } } },
    { "name": "medicationStatement", "resource": {
        "resourceType": "MedicationStatement",
        "status": "active",
        "subject": { "extension": [ {
          "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
          "valueCode": "unknown" } ] },
        "medicationCodeableConcept": { "coding": [ {
          "system": "urn:oid:2.16.840.1.113883.2.4.4.10", "code": "18996" } ] } } },
    { "name": "observation", "resource": {
        "resourceType": "Observation",
        "status": "final",
        "code": { "coding": [ {
          "system": "urn:oid:2.16.840.1.113883.2.4.4.30.45", "code": "ALDOB" } ] },
        "effectiveDateTime": "2024-07-04",
        "valueQuantity": { "value": 10 } } }
  ]
}
```

`status` and `subject` on the input resources are not read — and are nonetheless **required**. Base
R4 makes them mandatory and a profile can only constrain, so a payload without them fails
validation. Send them as the example does, with a `data-absent-reason` where you have nothing to
point at, and do not expect either to influence the session. See
[Elements FHIR requires that fhir-hub does not read](#elements-fhir-requires-that-fhir-hub-does-not-read).

### Output

`200` with a `Parameters` resource:

| Parameter | Card. | Type | Notes |
| --- | --- | --- | --- |
| `sessionId` | 1..1 | `string` | Pass this to `$session-result` |
| `url` | 1..1 | `url` | Redirect the care provider's browser here |

```json
{
  "resourceType": "Parameters",
  "parameter": [
    { "name": "sessionId", "valueString": "sess-abc-123" },
    { "name": "url", "valueUrl": "https://evs.prescriptor.nl/web_current/index.php?sk=sess-abc-123" }
  ]
}
```

Treat `url` as opaque: redirect to it unchanged. Its shape is Prescriptor's and is not part of this
contract, so do not parse the session id back out of it — `sessionId` is where it lives.

## `POST /fhir/$createrx-session`

Opens a **CreateRx** session: prescribing without a formulary lookup, optionally starting from a
prescription the host already holds.

Identical to `$formulary-session` except:

| | |
| --- | --- |
| `reason` | **0..1** — optional, because CreateRx prescribes without a formulary lookup. Still validated against the ICPC pattern when present |
| `prescription` | **0..1** `MedicationRequest` — a prescription to open for editing |

### `prescription`

Send back the `MedicationRequest` you received from `$session-result`, with whatever edits you
intend. The two shapes are the same, with one exception: the product must be coded as **PRK or
HPK**, and `$session-result` may have returned it at GPK level. A GPK-coded prescription cannot be
handed back — resolve it to a PRK or an HPK first, or open the session without a prescription.

| Element | Card. | Notes |
| --- | --- | --- |
| `status`, `intent` | 1..1 | Mandatory in base R4, not read. `active` and `order` |
| `subject` | 1..1 | Mandatory in base R4, not read. A `data-absent-reason` of `unknown`, as on the way out |
| `medicationCodeableConcept` | 1..1 | Required. Must carry a PRK **or** HPK coding; PRK wins when both are given |
| `medicationCodeableConcept.coding` (ATC) | 0..1 | `http://www.whocc.no/atc`, forwarded as-is |
| `medicationCodeableConcept.text` | 0..1 | Used as the description when a coding has no `display` |
| `dosageInstruction.extension[CodedDirections]` | 0..1 | The authoritative dosing instruction |
| `dosageInstruction.text` | 0..1 | Fallback when the extension is absent |
| `dispenseRequest.quantity` | 0..1 | `value` plus `code` (or `unit`) as the G-Standaard basiseenheid |

Dosing is read from the `CodedDirections` extension first and from `Dosage.text` only as a
fallback. Edits to `timing` or `doseAndRate` are **ignored** — this interface passes NHG Tabel 25
through verbatim in both directions and derives no structured dosing. See
[Extensions](#extensions).

```jsonc
{ "name": "prescription", "resource": {
    "resourceType": "MedicationRequest",
    "status": "active", "intent": "order",
    "subject": { "extension": [ {
      "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
      "valueCode": "unknown" } ] },
    "medicationCodeableConcept": { "coding": [
      { "system": "urn:oid:2.16.840.1.113883.2.4.4.10", "code": "18996",
        "display": "PARACETAMOL ZETPIL 1000MG" },
      { "system": "http://www.whocc.no/atc", "code": "N02BE01" } ] },
    "dosageInstruction": [ { "extension": [ {
      "url": "http://spec.digitalis.nl/fhir/StructureDefinition/ext-Dosage.CodedDirections",
      "valueString": "3-4D1S; gedurende max. 1 maand" } ] } ],
    "dispenseRequest": { "quantity": {
      "value": 15, "code": "ST",
      "system": "urn:oid:2.16.840.1.113883.2.4.4.1.900.2" } } } }
```

Sending `prescription` to `$formulary-session` is a 400. Output is the same
`Parameters{sessionId, url}` as above.

## `GET /fhir/$session-result`

Fetches what a finished session produced.

### Input

| | |
| --- | --- |
| Method | `GET` |
| `session` | 1..1, query parameter — the `sessionId` from the open-session response |

```bash
curl -sS \
  -u 'practice-123:licence-key' \
  -H 'Accept: application/fhir+json' \
  'http://localhost:8080/fhir/$session-result?session=sess-abc-123'
```

Quote the URL: unquoted, the shell expands `$session-result` and you request `/fhir/-result`.

**One shot.** The first successful call *ends the session*; a second call with the same id returns
401, not the same Bundle. Store the Bundle as soon as you receive it, and make sure a retry after a
timeout cannot fire twice — a lost response cannot be recovered.

**Call it once, when the browser returns to your `endSessionUrl`** — that redirect is the signal
that the session is finished. Do not poll on a timer: a 401 does not distinguish "unknown id" from
"already collected", so a polling loop cannot tell a not-yet-finished session from one whose result
it has already consumed and lost.

### Output

`200` with a `Bundle`, `type: collection`. Entries are, in order, one `MedicationRequest` per
prescribed drug followed by one `Communication` per piece of patient advice. Either list may be
empty — a session that produced no prescriptions and no advice yields an empty Bundle, not an
error. An *unknown or already-consumed* session id is a 401 instead.

#### `MedicationRequest`

| Element | Card. | Notes |
| --- | --- | --- |
| `status` | 1..1 | Fixed `active` |
| `intent` | 1..1 | Fixed `order` |
| `subject` | 1..1 | **Always** a `data-absent-reason: unknown` extension, never a reference |
| `medicationCodeableConcept.coding` | 1..2 | Exactly one G-Standaard coding, at the level Prescriptor prescribed at — PRK, HPK **or** GPK, with `display` — plus ATC when known |
| `medicationCodeableConcept.text` | 0..1 | Product description |
| `dosageInstruction` | 0..1 | `text` (human-readable) plus the `CodedDirections` extension |
| `dispenseRequest.quantity` | 0..1 | `value` (decimal), `code`/`unit` = G-Standaard basiseenheid, e.g. `ST` |
| `dispenseRequest.expectedSupplyDuration` | 0..1 | Days: `unit: "dag"`, `system: UCUM`, `code: "d"` |
| `extension[OpiumActClassification]` | 0..1 | Present only when the product falls under the Opiumwet |

`subject` never carries a reference — this service never stored the `Patient` you sent — so do not
try to read a patient identity out of the Bundle. Correlate it with your own record using the
session id you polled with, and populate `subject` yourself afterwards if your own storage needs it.

Parse `quantity` as a **decimal**. Whole numbers are not guaranteed: partial packs are real.
Clients that assume an integer here will truncate a real quantity.

Do not expect a code hierarchy on the way out. Each prescription comes back at exactly one code
level, whichever Prescriptor prescribed at, so match on whichever coding is present rather than
looking for PRK. This is the reverse of the input side, where a PRK or HPK you send is expanded to a
PRK + GPK (+ HPK) triple for surveillance.

#### `Communication`

| Element | Card. | Notes |
| --- | --- | --- |
| `status` | 1..1 | Fixed `completed` |
| `category` | 1..1 | `instruction` in `http://terminology.hl7.org/CodeSystem/communication-category` |
| `payload.contentString` | 0..1 | Prose advice |
| `payload.contentAttachment` | 0..1 | A link (thuisarts.nl): `contentType: text/uri-list`, `url` set |

Exactly one of the two is present per `Communication`. The attachment form is used when the advice
is a link — its text starts with `https://www.thuisarts.nl` — and the string form for prose, so
switch on which element is populated rather than inspecting the text yourself.

```jsonc
{
  "resourceType": "Bundle",
  "type": "collection",
  "entry": [
    { "fullUrl": "urn:uuid:9f1b2d34-5a67-4c89-b012-3456789abcde",
      "resource": {
        "resourceType": "MedicationRequest",
        "extension": [ {
          "url": "http://spec.digitalis.nl/fhir/StructureDefinition/ext-MedicationRequest.OpiumActClassification",
          "valueCodeableConcept": { "coding": [ {
            "system": "http://spec.digitalis.nl/fhir/CodeSystem/gstandaard-bijzonder-kenmerk",
            "code": "2", "display": "Product valt onder Opiumwet in volle omvang" } ] } } ],
        "status": "active", "intent": "order",
        "subject": { "extension": [ {
          "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
          "valueCode": "unknown" } ] },
        "medicationCodeableConcept": {
          "coding": [
            { "system": "urn:oid:2.16.840.1.113883.2.4.4.10", "code": "18996",
              "display": "PARACETAMOL ZETPIL 1000MG" },
            { "system": "http://www.whocc.no/atc", "code": "N02BE01" } ],
          "text": "PARACETAMOL ZETPIL 1000MG" },
        "dosageInstruction": [ {
          "extension": [ {
            "url": "http://spec.digitalis.nl/fhir/StructureDefinition/ext-Dosage.CodedDirections",
            "valueString": "3-4D1S; gedurende max. 1 maand" } ],
          "text": "3 tot 4 maal per dag 1 stuk" } ],
        "dispenseRequest": {
          "quantity": { "value": 15, "unit": "ST", "system": "urn:oid:2.16.840.1.113883.2.4.4.1.900.2",
                        "code": "ST" },
          "expectedSupplyDuration": { "value": 30, "unit": "dag",
                                      "system": "http://unitsofmeasure.org", "code": "d" } } } },
    { "fullUrl": "urn:uuid:1c2d3e4f-5678-4a9b-8c0d-1e2f3a4b5c6d",
      "resource": {
        "resourceType": "Communication",
        "status": "completed",
        "category": [ { "coding": [ {
          "system": "http://terminology.hl7.org/CodeSystem/communication-category",
          "code": "instruction" } ] } ],
        "payload": [ { "contentString": "Neem in bij voorkeur met wat water." } ] } },
    { "fullUrl": "urn:uuid:7b8c9d01-2e3f-4a5b-9c6d-7e8f9a0b1c2d",
      "resource": {
        "resourceType": "Communication",
        "status": "completed",
        "category": [ { "coding": [ {
          "system": "http://terminology.hl7.org/CodeSystem/communication-category",
          "code": "instruction" } ] } ],
        "payload": [ { "contentAttachment": {
          "contentType": "text/uri-list",
          "url": "https://www.thuisarts.nl/paracetamol" } } ] } }
  ]
}
```

## Profiles

Every payload in this document has a `StructureDefinition` you can validate against. They are
published as an implementation guide with the canonical `http://spec.digitalis.nl/fhir`:

| Payload | Profile |
| --- | --- |
| `$formulary-session` request | `fhirhub-FormularySessionInput` |
| `$createrx-session` request | `fhirhub-CreateRxSessionInput` |
| Session response | `fhirhub-SessionOutput` |
| `$session-result` response | `fhirhub-ResultBundle` |

The input profiles slice `Parameters.parameter` by name and point each slice at a resource
profile, so validating the request body checks the resources inside it in one pass. **The slicing
is closed**: a parameter name this interface does not define is an error. That is deliberate. The
mapping layer behind the validator reads the parameters it knows and ignores the rest, so
`medicationstatement` for `medicationStatement` would otherwise open a session against a silently
thinner medication list — the same class of false negative that makes an unresolvable drug code a
400 rather than a warning. The closed slice turns the typo into a rejected request.

Both session requests, the session response and the result Bundle in this document are also
instances in the IG, and each is validated against the profile it claims on every build — so the
documentation cannot drift away from the profiles.

**These profiles are enforced.** A request body is validated against its profile before anything
else happens — before the G-Standaard lookup and before the call upstream — and a payload that
does not conform is a 400 whose `OperationOutcome` carries one issue per error, each with the
element it failed on. You therefore get every problem in one response rather than one per round
trip.

Only errors reject. Warnings are normal here and are not failures: the G-Standaard code systems
cannot be expanded, so every G-Standaard coding produces a "could not be validated" note.

### Elements FHIR requires that fhir-hub does not read

Base R4 makes several elements mandatory that this interface never looks at. A profile can only
constrain, never relax, so a **conformant payload must carry them** even though sending them
changes nothing:

| Resource | Element | Note |
| --- | --- | --- |
| `AllergyIntolerance` | `patient`, `clinicalStatus` | `clinicalStatus` is required by invariant `ait-1` |
| `Condition` | `subject` | |
| `MedicationStatement` | `status`, `subject` | |
| `Observation` | `status` | |
| `MedicationRequest` (`prescription`) | `status`, `intent`, `subject` | `$createrx-session` only |

The patient travels as a sibling parameter rather than a contained resource, so there is nothing
for `patient` and `subject` to reference. Send a `data-absent-reason` of `unknown`, as the
examples above do — the same idiom `$session-result` uses for `MedicationRequest.subject` on the
way out. The mapping layer itself would accept a payload without these; it is the profiles, and
base FHIR underneath them, that require them — and since validation runs before mapping, they are
required in practice and not only on paper.

## Code systems

Emitted on output and accepted on input:

| Concept | `system` |
| --- | --- |
| PRK (voorschrijfproducten) | `urn:oid:2.16.840.1.113883.2.4.4.10` |
| HPK (handelsproduct) | `urn:oid:2.16.840.1.113883.2.4.4.7` |
| GPK (generiek product) | `urn:oid:2.16.840.1.113883.2.4.4.1` |
| G-Standaard basiseenheid | `urn:oid:2.16.840.1.113883.2.4.4.1.900.2` |
| ICPC-1 NL | `urn:oid:2.16.840.1.113883.2.4.4.31.1` |
| NHG Tabel 45 (diagnostische bepalingen) | `urn:oid:2.16.840.1.113883.2.4.4.30.45` |
| ATC | `http://www.whocc.no/atc` |
| UCUM | `http://unitsofmeasure.org` |

The four G-Standaard subsystems are identified by national OIDs as well:

| Concept | `system` | Used by |
| --- | --- | --- |
| SSK (stofnaamcode incl. toedieningsweg) | `urn:oid:2.16.840.1.113883.2.4.4.1.725` | `allergyIntolerance` |
| SNK (stofnaamcode / generieke namen) | `urn:oid:2.16.840.1.113883.2.4.4.1.750` | `allergyIntolerance` |
| OGGrp (ongewenste medicatiegroep, thesaurus 122) | `urn:oid:2.16.840.1.113883.2.4.4.1.902.122` | `allergyIntolerance` |
| CICode (contra-indicatie, thesaurus 40) | `urn:oid:2.16.840.1.113883.2.4.4.1.902.40` | `condition` |

These are the same OIDs Nictiz binds to in `nl-core-AllergyIntolerance` and
`nl-core-MedicationContraIndication`, so a coding you send here is one you can send to any Dutch
system that follows those profiles.

**Copy these strings exactly, and send nothing else.** There is one accepted `system` per table,
and there is no lenient form: in particular the bare code-system token — `PRK`, `HPK`, `SSK`,
`SNK`, `OGGrp`, `CICode`, `ICPC` — is what the JSON API carries in a field of its own, and it is
not a FHIR `system`. Sending one, or any other URI for the same table, is a 400 on the element it
was on:

```
None of the codings provided are in the value set 'ICPC-1 NL'
(http://spec.digitalis.nl/fhir/ValueSet/icpc-1-nl|0.1.0), and a coding from this value set is
required) (codes = ICPC#A01)
```

A rejection lists the `system` URIs the element accepts, so the error tells you what to send:

```
AllergyIntolerance.code has no coding in a system this interface routes; expected one of
[urn:oid:2.16.840.1.113883.2.4.4.1.725, urn:oid:2.16.840.1.113883.2.4.4.1.750,
urn:oid:2.16.840.1.113883.2.4.4.1.902.122]
```

The G-Standaard tables are licensed and are not distributed with the profiles, so what is checked
is the `system`, not the code: a coding from one of the systems above passes with a warning that its
code could not be verified, and a coding from any other system is an error. The one code shape that
*is* checked is ICPC-1 NL, by an invariant rather than by expansion.

## Extensions

Two, both Digitalis-defined because no national artifact covers them. Ask Digitalis for the
`StructureDefinition`s if your tooling needs them — the canonical URLs do not dereference, see
[Current limitations](#current-limitations).

**`ext-Dosage.CodedDirections`** —
`http://spec.digitalis.nl/fhir/StructureDefinition/ext-Dosage.CodedDirections`, `valueString`. The NHG
Tabel 25 coded instruction, e.g. `"3-4D1S; gedurende max. 1 maand"`.

This is the dosing instruction **in both directions**. Store it and hand it back unchanged unless
you mean to change the dose; `Dosage.text` beside it is the human-readable form, for display only.
If you do not parse Tabel 25, treat the string as opaque and pass it through — a pharmacy system
downstream reads it natively.

No `timing` or `doseAndRate` is produced, and any you send is **ignored**. To change a dose, edit
this extension; editing `timing` has no effect.

**`ext-MedicationRequest.OpiumActClassification`** —
`http://spec.digitalis.nl/fhir/StructureDefinition/ext-MedicationRequest.OpiumActClassification`,
`valueCodeableConcept`. A G-Standaard bijzonder kenmerk, present only when the product falls under
the Opiumwet.

Only code `2` ("Product valt onder Opiumwet in volle omvang") is emitted today. Read the code rather
than treating the extension as a boolean flag: further codes (`65`, `107`) carry different handling
rules for a pharmacist and may appear later without a breaking change, so switch on the code and
have a default branch.

## Errors

Every error is an `OperationOutcome`, the 401s included, so one error path in your client handles
all of them. No response in this API is un-parseable by a FHIR library.

| Condition | Status |
| --- | --- |
| Missing or malformed Basic credentials | 401 |
| Organization id or key rejected by Prescriptor (`Invalid organization ID or key`) | 401 |
| Unknown or already-consumed session id (`No data found for the session ID`) | 401 |
| Invalid request | 400 |
| Prescriptor unreachable (`Could not reach Prescriptor`) or unparseable | 500 |

A 400 comes from one of three places. Knowing which saves time when you read `diagnostics`, and
each is quoted as returned so you can recognise it while building.

**The profile**, which is where most of them come from. The body is validated before anything else
happens, and the `OperationOutcome` carries one issue per error with the element it failed on — so
you get every problem in one response rather than one per round trip. The wording is the FHIR
validator's:

- `Slice 'Parameters.parameter:endSessionUrl': a matching slice is required, but not found` — a
  missing parameter
- `This element does not match any known slice defined in the profile … and slicing is CLOSED` — a
  misspelled parameter name
- `Parameters.parameter:endSessionUrl: max allowed = 1, but found 2` — a repeated single-valued
  parameter
- `Parameters.parameter:prescription: max allowed = 0, but found 1` — a prescription sent to
  `$formulary-session`
- `Patient.birthDate: minimum required = 1, but only found 0`
- `MedicationRequest.subject: minimum required = 1, but only found 0` — the `data-absent-reason`
  idiom is missing; see [Elements FHIR requires that fhir-hub does not read](#elements-fhir-requires-that-fhir-hub-does-not-read)
- `The value provided ('other') was not found in the value set 'Administrative gender accepted by
  Prescriptor' …, and a code is required from this value set`
- `None of the codings provided are in the value set 'Contra-indication (CICode or ICPC-1 NL)' …,
  and a coding from this value set is required) (codes = …)`
- `Constraint failed: fhirhub-icpc-shape: 'An ICPC-1 NL code is a letter and two digits, optionally
  followed by a dot and two more (A01, U71.01).'`
- `Constraint failed: fhirhub-http-url: 'Only http and https are accepted: …'`

**The operation binding**, before the body is validated at all, when a parameter carries the wrong
type:

- `HAPI-0362: Request has parameter reason of type Coding but method expects type CodeableConcept`
- `HAPI-0362: Request has parameter endSessionUrl of type StringType but method expects type UrlType`

**This interface's own rules**, for the things a profile cannot express:

- `G-Standaard has no product for PRK 404040, so it cannot take part in medication surveillance`
- `PRK code '18996a' is not numeric`
- `The 'session' parameter is required` — on `$session-result`

None of this wording is part of the contract. Branch on the HTTP status, and show `diagnostics` to
whoever has to act on it rather than matching on the text.

```json
{
  "resourceType": "OperationOutcome",
  "issue": [ {
    "severity": "error",
    "code": "processing",
    "details": { "coding": [ {
      "system": "http://hl7.org/fhir/java-core-messageId",
      "code": "Validation_VAL_Profile_Minimum_SLICE" } ] },
    "diagnostics": "Slice 'Parameters.parameter:endSessionUrl': a matching slice is required, but not found (from http://spec.digitalis.nl/fhir/StructureDefinition/fhirhub-FormularySessionInput|0.1.0). Note that other slices are allowed in addition to this required slice",
    "location": [ "Parameters", "Line[1] Col[894]" ],
    "expression": [ "Parameters" ]
  } ]
}
```

Validator issues also carry `operationoutcome-issue-line`, `-issue-col` and `-message-id`
extensions, trimmed here. `expression` and `location` are the two fields worth surfacing to a
developer: they name the element that failed.

## Behaviour to design around

- **`$session-result` is single-use.** Requesting it ends the session in Prescriptor; a second call
  returns 401.
- **Medication surveillance fails closed.** One unresolvable drug code fails the whole request with
  a 400 that names it; nothing is silently skipped. Refresh the code and retry rather than dropping
  the drug from the list.
- **No patient identity comes back.** Correlate the Bundle with your own record using the session
  id you polled with.
- **`xisId` and `xisVersion` never reach Prescriptor.** Send them anyway: it is how a support
  question gets traced to your calls.
- **Structured dosing is not round-tripped.** Edit the `CodedDirections` extension, not `timing`.
- **A GPK-coded prescription cannot be handed back** to `$createrx-session`, which needs a PRK or an
  HPK. See [`prescription`](#prescription).
- **Nothing in a request is ignored.** An unrecognised parameter name, a repeated single-valued
  parameter and a wrong value type are all 400s, so a typo surfaces as a rejection rather than as a
  session opened on partial data.
- **Lab units are dropped.** Send values in the determination's own unit; a `Quantity.unit` is
  ignored.
- **Retries are not safe on `$session-result`.** Trigger it from the `endSessionUrl` redirect, not
  from a poll, and guard against a double call. See *One shot* above.

## Moving from the JSON API

If you are replacing a JSON API integration, the payloads change shape but the operations, the
semantics and the status codes do not. Three differences go beyond syntax.

- **Credentials move to the HTTP layer.** The organization id and key leave the request body and
  become an HTTP Basic header. The values are unchanged.
- **Drug codes in `medicationStatement` are resolved before the session opens.** Each PRK or HPK is
  looked up in the G-Standaard and forwarded as a PRK + GPK (+ HPK) triple. A code that cannot be
  resolved fails the request with a 400 — see *Medication surveillance fails closed* above. The JSON
  API forwards current medication at the level you supply it without that lookup, so a code that is
  accepted there can be rejected here. Check your codes against a current G-Standaard before
  switching over.
- **The code system stops being a separate field and becomes the `system` on the coding.** Where the
  JSON API took a code alongside a `PRK`, `SSK` or `ICPC` token, here the token is replaced by the
  URI from [Code systems](#code-systems). The tokens themselves are not accepted as a `system`.

Everything else is a mapping question, and the field-by-field table Digitalis can send you covers
it.

## Current limitations

Things you may expect to be able to do, and cannot yet. Each is a gap in the published artifacts,
not in the running service.

- **The canonical URLs do not dereference yet.** `http://spec.digitalis.nl/fhir/...` appears in
  every payload and the definitions exist, but the host is not serving them yet. Request the IG
  package from Digitalis and load it locally. Note the consequence if your tooling resolves
  profiles over the network: an unresolvable profile is reported as *not checked* rather than as
  a failure, so a validation run can report success having verified nothing.
- **No `meta.profile` is asserted** on any resource, so do not filter or route on it. Validate
  against the profile URLs above explicitly instead. The IG's example instances do carry one,
  because the publishing tool adds it; a live payload does not.
- **nl-core is not derived from**, so do not expect these resources to satisfy nl-core. Three of
  the five are blocked on Nictiz rather than on effort: `nl-core-MedicationContraIndication`
  profiles `Flag` rather than `Condition`, `nl-core-LaboratoryTestResult` does not admit NHG
  Tabel 45 codes (Nictiz BITS **ZIB-639**), and `nl-core-MedicationUse2` is not published in the
  nl-core package. Ask Digitalis before building anything that depends on nl-core conformance.
- **The artifacts are `draft`, at version `0.1.0`,** and no versioning policy is agreed yet: there
  is no path versioning and no published change policy. Agree with Digitalis how you want to be
  told about a change before you go live.

Questions, or a case this document does not cover: contact Digitalis. If you are moving from the
JSON API, ask for the field-by-field mapping table, which lists each JSON field alongside its FHIR
equivalent.
