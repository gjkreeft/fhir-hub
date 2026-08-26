# fhir-hub

A FHIR R4 interface for **Digitalis Prescriptor 3**, functionally equivalent to **v2** of the
JSON interface in `../json-interface`, with one deliberate difference: **authentication has moved
out of the message body and onto the HTTP layer**.

Like its predecessor it is a stateless proxy: it accepts FHIR, translates to the XML-RPC
dialect Prescriptor speaks, and translates the answer back. No session state and no credential
store. It does read the G-Standaard database, read-only, to resolve current medication — see
*Medication surveillance* below.

**Integrating?** Read [IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md) — the flow and every
endpoint with its input and output specification. This README covers the design rationale behind
those specifications.

## The flow

1. The host opens a session with the patient context. It gets back a launch URL and a session id.
2. The care provider works in the Prescriptor UI at that URL.
3. The host polls for the result and receives the prescriptions and advice.

```
POST   /fhir/$formulary-session        Parameters  ->  Parameters (sessionId, url)
POST   /fhir/$createrx-session         Parameters  ->  Parameters (sessionId, url)
GET    /fhir/$session-result?session=  ->  Bundle (MedicationRequest, Communication)
GET    /fhir/metadata                  ->  CapabilityStatement (unauthenticated)
```

Responses are JSON by default. A browser gets a syntax-highlighted HTML rendering, because
HAPI's `ResponseHighlighterInterceptor` is registered; `?_format=json`, `?_format=xml` or
`?_format=html` overrides that for any client.

Custom operations rather than a resource REST API: the interaction is a remote procedure call
with a side effect, not CRUD over stored resources, and `Parameters` is the FHIR container
built for exactly that. It also maps one-to-one onto the three endpoints hosts already use.

## Authentication

```
Authorization: Basic base64(organization.id ":" organization.key)
```

The two halves are the same organization id and key that sit in the JSON body of v2, so no host
needs new credentials to migrate.

fhir-hub does **not** validate them. Prescriptor owns the licence administration and answers
with an XML-RPC fault when a pair is wrong; fhir-hub forwards the pair as the `PracticeID` and
`LicenseKey` members and surfaces that fault as a 401. This is what keeps the service stateless
— there is no credential store to provision, rotate, or keep in step with Prescriptor.

SMART-on-FHIR / OAuth 2.0 is the upgrade path. It slots in at `SecurityConfig` and
`CredentialsResolver`; nothing downstream of those changes.

## Opening a session

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
          "system": "http://digitalis.nl/fhir/CodeSystem/gstandaard-snk", "code": "10499" } ] } } },
    { "name": "condition", "resource": {
        "resourceType": "Condition",
        "subject": { "extension": [ {
          "url": "http://hl7.org/fhir/StructureDefinition/data-absent-reason",
          "valueCode": "unknown" } ] },
        "code": { "coding": [ {
          "system": "http://digitalis.nl/fhir/CodeSystem/gstandaard-contraindicatie",
          "code": "228" } ] } } },
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

`reason` is required for `$formulary-session` and optional for `$createrx-session`, matching
the ICPC requirement of the two upstream methods. ICPC codes must match
`^[A-Z][0-9]{2}(\.[0-9]{2})?$` (`A01`, `U71.01`), and `endSessionUrl` must be `http` or
`https` — both rejected with a 400 rather than passed upstream.

`xisId` and `xisVersion` identify the calling system. Both are required and neither is forwarded
to Prescriptor: they exist so a log line can be attributed to a supplier and a release, which
matters more with sixteen integrators than with one.

### Editing an existing prescription (CreateRx only)

`$createrx-session` accepts a prescription the host already holds, so the care provider opens
it for editing rather than starting over. It is modelled as a `MedicationRequest` — the mirror
image of what `$session-result` returns, so a host can hand back what it received:

```jsonc
{ "name": "prescription", "resource": {
    "resourceType": "MedicationRequest",
    "medicationCodeableConcept": { "coding": [
      { "system": "urn:oid:2.16.840.1.113883.2.4.4.10", "code": "18996",
        "display": "PARACETAMOL ZETPIL 1000MG" },
      { "system": "http://www.whocc.no/atc", "code": "N02BE01" } ] },
    "dosageInstruction": [ { "extension": [ {
      "url": "http://digitalis.nl/fhir/StructureDefinition/ext-Dosage.CodedDirections",
      "valueString": "3-4D1S; gedurende max. 1 maand" } ] } ],
    "dispenseRequest": { "quantity": {
      "value": 15, "code": "ST",
      "system": "urn:oid:2.16.840.1.113883.2.4.4.1.900.2" } } } }
```

The coded directions are read from the `CodedDirections` extension, falling back to
`Dosage.text`. Sending it to `$formulary-session` is a 400.

## How the JSON interface maps onto FHIR

| JSON interface | FHIR |
| --- | --- |
| `icpc` | `Coding`, ICPC-1 NL (`urn:oid:2.16.840.1.113883.2.4.4.31.1`) |
| `patient.gender` | `Patient.gender` — see *Gender* below |
| `patient.dob` | `Patient.birthDate` |
| `allergies[]` + `SSK`/`SNK`/`OGGrp` | `AllergyIntolerance.code.coding` |
| `contraIndications[]` + `CICode`/`ICPC` | `Condition.code.coding` |
| `medications[]` + `PRK`/`HPK` | `MedicationStatement.medicationCodeableConcept` — see *Medication surveillance* |
| `laboratoryData[].memo`/`mat`/`bijz` | **one** `Observation.code.coding`, NHG Tabel 45 |
| `laboratoryData[].date` / `.value` | `Observation.effectiveDateTime` / `.value[x]` |
| `endSessionUrl` | `Parameters.parameter:endSessionUrl.valueUrl`, http(s) only |
| `xis.id` / `xis.version` | `Parameters.parameter:xisId` / `:xisVersion`, both `valueString` |
| `prescription` (CreateRx) | `Parameters.parameter:prescription`, a `MedicationRequest` |
| `organization.id`, `organization.key` | HTTP Basic |

| `drugs[].codes[]`, `.atc` | `MedicationRequest.medicationCodeableConcept.coding[]` |
| `drugs[].codes[].quantity` `{value, unit}` | `dispenseRequest.quantity` (G-Standaard basiseenheid, **not** UCUM) |
| `drugs[].duration` | `dispenseRequest.expectedSupplyDuration` (UCUM `d`) |
| `directions.user` | `Dosage.text` |
| `directions.coded` | the `CodedDirections` extension, verbatim — see *Dosing* below |
| `drugs[].opium` | the `OpiumActClassification` extension |
| `advices[]` + `contentType` | `Communication.payload` — `contentString` or `contentAttachment` |

**`memo` + `mat` + `bijz` are not three fields.** They are the 8-position NHG Tabel 45
sleutelcode (memo 1–4, materiaal 5–6, bijzonderheid 7–8), which uniquely and permanently
identifies a determination. Send the composite code; fhir-hub splits it apart again only
because the upstream dialect transmits it apart. Short codes are space-padded.

**Gender.** FHIR has four administrative genders; Prescriptor's `PatientGender` has three —
`M`, `F` and `X` ("Unknown"). `male`, `female` and `unknown` all map across. Sex-specific
surveillance checks cannot fire on `X`, so send the sex when you know it.

`other` and an absent gender are rejected with a 400 rather than coerced: `other` is not the
same assertion as `unknown` and has no upstream value, and an absent gender is a caller
omission rather than a statement about the patient. Guessing a patient's sex to satisfy a
medication-surveillance check would be the wrong kind of helpful.

Note that this is wider than v2, whose JSON schema enumerated `["F", "M"]` only.

## Medication surveillance

`medicationStatement` carries what the patient is currently taking, so Prescriptor can check a
new prescription against it for interactions and duplicate therapy. Send one parameter per drug:

```jsonc
{ "name": "medicationStatement", "resource": {
    "resourceType": "MedicationStatement",
    "medicationCodeableConcept": { "coding": [
      { "system": "urn:oid:2.16.840.1.113883.2.4.4.10", "code": "18996" } ] } } }
```

A host identifies a drug by **one** code, PRK or HPK. Prescriptor needs PRK *and* GPK together
(plus HPK where the host had it), so `MedicationCodeResolver` looks each drug up in the
`medcode` view of the G-Standaard database before opening the session, and emits:

```xml
<drug pending="false"><GStandaard PRK="18996" GPK="111111"/></drug>
```

**An unresolvable drug code fails the request with a 400.** This is deliberate. Surveillance
running on an incomplete medication list does not fail visibly — it answers "no interaction
found", which is a false negative in the dangerous direction, and a prescriber cannot tell it
apart from a genuine all-clear. Refusing the session and naming the offending code is the safe
behaviour. The `OperationOutcome` says which code could not be resolved.

This is the one part of the interface that needs a database. It is a read-only reference
lookup on `gstandaard_views`, queried directly by `MedicationCodeResolver` over its own Hikari
pool (`GStandaardJdbcConfig`, configured under `gstandaard.datasource.*`), and it holds no state
of its own.

## Dosing

`directions.coded` is an NHG Tabel 25 string. It is carried verbatim in the `CodedDirections`
extension and **not** decoded into `Dosage.timing` / `Dosage.doseAndRate`; `Dosage.text` carries
the expanded free text alongside it for readers that do not interpret Tabel 25.

Both ends of this interface speak Tabel 25 — Prescriptor emits it, and the HIS and XIS systems
consuming this API pass it to a pharmacy chain that reads it natively — so the extension is the
authoritative form in both directions. `$createrx-session` reads the same extension back, which
means a prescription can be round-tripped without loss. Structured dosing would be a Medicatieproces 9
zib Gebruiksinstructie mapping, and is not attempted today.

## Profiles

StructureDefinitions for every payload live in `ig/`, written in FSH and built with SUSHI. They
cover the two session inputs, the session output, the `$session-result` Bundle, the five
resources a host sends in, the two Digitalis extensions, and the terminology behind them. Every
example in `IMPLEMENTATION_GUIDE.md` is also an instance in the IG and validates clean against
the HL7 validator, so the documentation cannot drift from the profiles.

**The parent is plain R4, not nl-core.** That is a checked decision, not a shortcut. Verified
against `nictiz.fhir.nl.r4.nl-core` `0.12.1-beta.1`:

Each row below was checked by running the HL7 validator over the actual payload against the
nl-core profile, not by reading cardinalities:

| Resource | nl-core profile | Result |
| --- | --- | --- |
| Patient | `nl-core-Patient` | **0 errors.** The one that is ready today |
| AllergyIntolerance | `nl-core-AllergyIntolerance` | **Fails.** `code` has a *required* binding to Nictiz DECOR ValueSet `…60.121.11.2`, which a Digitalis placeholder G-Standaard system cannot satisfy. Blocked on the same open item as the unpinned subsystem OIDs |
| Observation (lab) | `nl-core-LaboratoryTestResult` | **Fails, 3 errors.** `Observation.category` and its `laboratoryCategory` slice are required and are neither sent nor read here; and the TestCode binding rejects NHG Tabel 45 (`ALDOB`). Still rejected in the `0.12.0-labtrial.1` pre-release — publishing the CodeSystem was not enough, the binding itself has to change (Nictiz BITS **ZIB-639**) |
| Condition | *none applicable* | The contra-indication profile, `nl-core-MedicationContraIndication`, is on **`Flag`**. Of the 8 nl-core `Condition` profiles none models a medication contra-indication |
| MedicationStatement | *none* | The package contains **no** `MedicationStatement` profile. `nl-core-MedicationUse2` is a Medicatieproces artifact, published separately |
| MedicationRequest, Communication | *none* | The package contains no profile for either resource type |

Every published nl-core R4 version is also a pre-release (`beta`, `rc`, `labtrial`), so deriving
today pins these profiles to a moving parent for a claim we cannot yet make about four of the
five resources.

`meta.profile` is still not asserted on any resource. Asserting a profile that is only half met
is worse than asserting nothing: validators reject it, and integrators will have trusted the
claim. The blocker for the output side is now gone, though — the Bundle `ResultBundleMapper`
produces validates clean against `fhirhub-ResultBundle` — so asserting it there is a decision
rather than a dependency.

### Enforcement

Inbound payloads are validated against their profile at runtime, before the G-Standaard lookup
and before anything is sent upstream. A non-conformant body is a 400 carrying one
`OperationOutcome` issue per error. Only `error` and `fatal` reject — warnings are routine,
because the G-Standaard code systems are `content: not-present` and can never be expanded.

Outbound Bundles are **not** validated at runtime: putting the reference validator in the path
of every response, for a payload this service built itself, is not worth 70 ms.
`OutboundPayloadConformanceTest` closes that gap in the build instead.

Set `fhirhub.validation.enabled=false` to turn enforcement off. It logs a warning at startup
when you do, because a validator that is present but disabled looks exactly like one that is
working.

What it costs, measured rather than estimated:

| | |
| --- | --- |
| Dependencies | **+79 MB**, fat jar 171 MB. 47 MB of that is the DSTU2/DSTU3/R4B/R5 models, for FHIR versions this service does not speak — they arrive via `org.hl7.fhir.convertors` and cannot be excluded without breaking the validator |
| First validation | ~4.5 s, moved into startup by `FhirValidationConfig.warmUpValidator` so no request pays it. Startup goes from ~2 s to ~5 s |
| Each validation | ~70 ms, on the request path |

That 70 ms is real overhead on an operation whose own work is one XML-RPC round trip. It buys
rejecting a malformed payload before a session is opened upstream, which is the failure that is
expensive to undo.

## Extensions

Two, both checked against Nictiz first — nothing in nl-core, zib2020, or Medicatieproces 9
covers either. Definitions are in `src/main/resources/fhir/`.

- **`ext-Dosage.CodedDirections`** — the NHG Tabel 25 string verbatim. FHIR models dosing
  structurally and has no slot for a coded string; HL7 NL registers OIDs only for Tabel 25
  *components*, not the composite. It is the authoritative dosing instruction in both
  directions — written on the way out, read back on the way in. See *Dosing* above.
- **`ext-MedicationRequest.OpiumActClassification`** — a G-Standaard bijzonder kenmerk
  (BST401T / BST922T), not a boolean. Codes 2 and 65 have different consequences for a
  pharmacist. Prescriptor reports only yes/no today, corresponding to rubriek 72 nr 2, so only
  code 2 is emitted; 65 and 107 can be added without a breaking change.

## Errors

Every error is an `OperationOutcome`, with the same status codes the JSON interface used.

| Condition | Status |
| --- | --- |
| Missing or malformed Basic credentials | 401 |
| Upstream fault — bad organization id or key | 401 |
| Unknown or already-consumed session id | 401 |
| Invalid request | 400 |
| Prescriptor unreachable or unparseable | 500 |

HAPI renders its own `AuthenticationException` as `text/plain`; `error/UnauthorizedException`
exists so that no response in this API is un-parseable by a FHIR client.

## Build and run

```bash
mvn test          # 63 tests; no network and no database needed
mvn spring-boot:run
docker compose up --build
```

| Variable | Default |
| --- | --- |
| `PRESCRIPTOR_TARGET_URL` | `https://evs.prescriptor.nl/web_current/xmlrpc_dispatch.php` |
| `GSTANDAARD_DB_URL` | `jdbc:mariadb://192.168.31.9:3306/gstandaard_views?serverTimezone=CET&useSSL=false` |
| `GSTANDAARD_DB_USER` / `GSTANDAARD_DB_PASSWORD` | `adapter` / `adapter` |
| `PORT` | `8080` |
| `LOG_LEVEL` | `INFO` |

The target URL is validated at startup; the application fails fast if it is absent or not a URL.
Tests need no database — H2 stands in for the `medcode` view.

## Differences from the JSON interface

**Current medication is enriched before it is sent.** `json-interface` forwards the codes a host
supplies, at the level supplied: its `getAdditionalDrugCodes` lookup is commented out, so the
`additionalCodes` list stays empty and no PRK + GPK pair is added. fhir-hub resolves each code
against the G-Standaard first — see *Medication surveillance* above — which is also why a code
that `json-interface` accepts can be a 400 here.

All three allergy members are populated in both interfaces; `prescriptor-api`'s
`OpenSessionRequestBuilder.getAllergies` is the authority on which member carries which
subsystem (`Allergies`→`OGGRP`, `AlStam`→`SNK`, `AlStof`→`SSK`).

## Known quirks, inherited deliberately

These are Prescriptor behaviours preserved from the JSON interface rather than corrected.
Changing them alters clinical behaviour and needs its own decision.

- **Requesting a result ends the session.** `$session-result` is idempotent in the HTTP sense
  only; a second call for the same id returns 401.

## Open items

- **Pin the G-Standaard subsystem OIDs for `SSK`, `SNK`, `OGGrp`, `CICode.`** They live inside
  the Nictiz DECOR ValueSets `…60.121.11.2` (AllergyIntolerance CausativeAgent) and the
  MedicationContraIndication binding, which have not been expanded. Until then
  `CodeSystemRegistry` serves Digitalis URIs and also accepts the bare token, so a later
  change is additive rather than breaking. **Do not publish externally before pinning these.**
- **Publish an Implementation Guide.** With a dozen HIS suppliers and several XIS systems,
  profiles, examples, and a changelog need to be published rather than described in a README.
- **Versioning policy.** `version` has left the payload. Path versioning plus an additive-only
  change policy needs to be agreed before the first integrator goes live.
- **A sandbox for integrator self-testing.** `../tests-digitalisrx-testpatients` is the
  natural seed.
- **No resource sets `meta.profile`.** The table above describes intent, not behaviour. The
  constants class that held these URLs was never referenced by any mapper and has been removed;
  the URLs are recorded above so the research is not lost. Assert them where each resource
  genuinely validates clean, or say plainly in this section that none are asserted — an
  integrator should not have to discover that from the payload.
  Either assert them or correct this section before an integrator relies on the claim.
