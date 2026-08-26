# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

## What this is

A FHIR R4 interface for **Prescriptor 3**, functionally equivalent to **v2** of
`../json-interface` (Node/TypeScript) with authentication moved from the request body to HTTP Basic. Stateless
proxy: FHIR in, XML-RPC to Prescriptor, FHIR out. No session store. It does read the
G-Standaard database, read-only, to resolve current medication for medication surveillance.

Read `README.md` first — it holds the operation contracts, the full JSON→FHIR mapping table,
and the open items. This file covers what the README does not: why the code is shaped as it is.

## Commands

```bash
mvn test                 # 80 tests; no network and no database — WireMock stubs
                         # Prescriptor, H2 stands in for the medcode view
mvn spring-boot:run
mvn -o test -Dtest=X     # single test class
docker compose up --build

cd ig && npx sushi .     # rebuild the profiles; see ig/README.md for validating them
```

## Stack

Java 25, Spring Boot 4.1.1, HAPI FHIR 8.10.1, Maven. Matches the house backend convention
(`formularium-api`, `nhg-formularium-adapter`, `gstandaard-jar`): **no Spring Boot parent
POM**, explicit versions via properties, package `nl.digitalis.*`, tabs for indentation.

Boot's `spring-boot-dependencies` BOM *is* imported into `dependencyManagement`, which is not
the parent POM and does not change that convention — every direct dependency still declares its
own version, and those win. The BOM governs **transitive** versions only, because without it
nearest-wins silently downgraded third-party transitives. `maven-enforcer-plugin`
(`requireUpperBoundDeps`) fails the build if that starts happening again, so a dependency change
that reintroduces a downgrade is a build failure rather than a runtime surprise.

**No third-party `nl.digitalis` libraries.** fhir-hub deliberately depends on nothing from the
house except what it declares itself; see *Why gstandaard-jar was dropped* below.

## Architecture

Request flow, and the one place each concern lives:

```
HTTP  →  SecurityConfig / CredentialsResolver   practiceId + licenseKey off the Basic header
      →  server/*Provider                        HAPI @Operation methods
      →  validation/ProfileValidator             Parameters       → 400 if it fails its profile
      →  fhir/SessionParametersMapper            Parameters       → internal model
                                                  (xis, prescription, ICPC + URL validation)
      →  gstandaard/MedicationCodeResolver       PRK|HPK          → PRK+GPK(+HPK) via JDBC
      →  prescriptor/XmlRpcRequestBuilder        internal model   → XML-RPC
      →  prescriptor/PrescriptorClient           the only HTTP call out
      →  prescriptor/XmlRpcResponseParser        XML-RPC          → internal model
      →  fhir/ResultBundleMapper                 internal model   → Bundle
```

`model/` is plain records with no FHIR and no XML types. That boundary is the point: the FHIR
mappers and the XML-RPC layer never see each other's types, so either side can change without
dragging the other along.

## Things worth knowing before you change something

**Do not build XML by string templating.** The predecessor did, unescaped, which meant a single
`&` in a drug description or lab value produced a malformed request and let caller-supplied
text inject markup. Everything goes through `XmlWriter` (StAX), which escapes text and
attributes. `XmlRpcRequestBuilderTest.escapesMarkupInCallerSuppliedValues` guards this.

**`memo` + `mat` + `bijz` are one code, not three.** The 8-position NHG Tabel 45 sleutelcode
(memo 1–4, materiaal 5–6, bijzonderheid 7–8). It arrives as one `Observation.code.coding` and
is split only because the upstream dialect wants it split. Do not "improve" this into three
FHIR elements.

**The two session types read their key from different members** — `PrescriptorSessionKey` for
formulary, `SessionKey` for CreateRx. An upstream inconsistency, encoded in `SessionType` and
pinned by a test.

**Why `gstandaard-jar` was dropped.** fhir-hub used exactly three types from it — `T25Parser`,
`GStandaardDatabase`, and (transitively, from `formularium-api`) the `T25` DTO. None of its
sixteen DAOs. In exchange it dragged in `formularium-api`, which brings Jackson 3 and collided
with HAPI's Jackson 2, and it pinned its own Spring Boot version, which split this application's
Boot modules across two minors.

What replaced each piece:

- `GStandaardDatabase` + `GStandaardJdbcBeanFactory` → `gstandaard/GStandaardJdbcConfig`, which
  builds the same named Hikari pool from the same `gstandaard.datasource.*` properties, and a
  plain `JdbcTemplate`. Deployment configuration is unchanged. Spring's own DataSource
  auto-configuration stays excluded, because this config builds its own named datasource.
- `T25Parser` → nothing. See *The coded dosage is passed through, not decoded* below.

`MedicationCodeResolver` queries `medcode` directly, which is what it already did — the DAOs
never covered the PRK+GPK pair it needs. Do not reintroduce a house library to get it back.

**The coded dosage is passed through, not decoded.** `T25DosageMapper` emits `Dosage.text` and
the `CodedDirections` extension, both verbatim, and derives no `timing` or `doseAndRate`. An
earlier version parsed NHG Tabel 25 into structured elements; it was lossy, no consumer read it,
and `SessionParametersMapper` silently ignored a host's edits to it because it reads the
extension back instead. `T25DosageMapperTest.derivesNoStructureFromTheCodedString` pins this.
The class Javadoc records what would have to be true to change the decision — the target would
be the zib Gebruiksinstructie model from Medicatieproces 9, not the old component split.

**`../json-interface` is the contract of record; fhir-hub tracks its version.** Currently v2
(`api/openapi.yaml`, `info.version: "2"`). When it moves again, diff these against it: the
`xisInfo` element, `MedicationType`, the `Prescription` member, the validation rules, and the
error wording all changed in v2 and all live in different files here. `XmlRpcRequestBuilderTest`
pins the wire format, so start there.

**`MedicationType` is derived, not fixed.** It declares the level the patient's current
medication is supplied at: 0 when there is none, 7 for HPK, 9 for PRK, from the first entry.
It was hardcoded to 9 before v2 — do not "simplify" it back.

**Runtime validation is real, and it cost four dependencies rather than one.** Adding
`hapi-fhir-validation` alone gets you a service that fails on the first request. What it needs:

- `hapi-fhir-validation-resources-r4` — the R4 core StructureDefinitions ship separately from
  the model classes. Without it `DefaultProfileValidationSupport` resolves *nothing*
  (`fetchAllStructureDefinitions()` returns 0) and every request dies with `HAPI-0705: Unknown
  base definition: .../Parameters`.
- `hapi-fhir-caching-caffeine` — HAPI finds its cache through a `ServiceLoader` and ships none,
  so `CachingValidationSupport` throws `HAPI-2200` at construction.
- A jackson-bom pin, because `org.hl7.fhir.validation` needs jackson-databind 2.22.1 and Boot
  4.1.1's BOM manages it down to 2.21.5. `requireUpperBoundDeps` caught it, which is the whole
  reason that rule is configured.
- `UnknownCodeSystemWarningValidationSupport` with `setNonExistentCodeSystemSeverity(WARNING)`.
  Constructing it is not enough — the default is an error. Without the setter, every real
  payload fails, because a `required` binding onto a `content: not-present` G-Standaard system
  can never be satisfied.

**Adding it also changed the XML this service sends.** `hapi-fhir-validation` brings Woodstox,
which registers itself as the StAX provider and wins `XMLOutputFactory.newInstance()`. Woodstox
serialises an empty element as `<medication/>` where the JDK writes `<medication></medication>`,
so every XML-RPC request quietly changed shape. `XmlWriter.element` now writes a zero-length
text event before the end tag, which pins the output under both providers. That line looks
redundant and is not — deleting it makes the wire format depend on dependency resolution.

**The cost is measured, in README under *Enforcement*:** +79 MB of dependencies, ~4.5 s for the
first validation (moved into startup by `warmUpValidator`), ~70 ms per request after that.

**The canonical is `http://spec.digitalis.nl/fhir`, and the move away from `digitalis.nl/fhir`
left exactly one shim.** `DigitalisExtensions.LEGACY_CODED_DIRECTIONS` is accepted on input and
never emitted, because that extension is the only one this interface *reads back* — a host may
hand `$createrx-session` a prescription issued before the move. Dropping it would fall through
to `Dosage.text` and lose the coded instruction silently rather than failing, which is the class
of bug this codebase is built to avoid. The other moved URLs are emit-only and needed nothing.

The four retired G-Standaard code systems stay on `digitalis.nl` and carry an explicit `^url` in
the FSH so they do not follow the canonical. A retired identifier does not move; relocating one
invents a third form to support instead of retiring the second. `IgCanonicalsTest` pins both
halves of this.

**The profiles live in `ig/`, and the ids in them are load-bearing.** SUSHI derives a canonical
as `{canonical}/StructureDefinition/{Id}`, and those canonicals are on the wire, so renaming an
id silently breaks every payload in the field. `IgCanonicalsTest` fails if the FSH
and the constants in `fhir/` drift apart. Note the corollary — base R4 requires
several elements this interface never reads (`AllergyIntolerance.patient` and `clinicalStatus`,
`Condition.subject`, `MedicationStatement.status`/`subject`, `Observation.status`), and a
profile can only constrain, so callers must send them — and since validation runs before
mapping, they are now required in practice, not just on paper. The documented examples and the
test fixtures did not send them, until the profiles caught it.

**nl-core is not derived from, and the reasons were checked.** `nl-core-MedicationContraIndication`
profiles `Flag` rather than `Condition`; `nl-core-MedicationUse2` is not in the nl-core package
at all; `nl-core-LaboratoryTestResult` wants `Observation.category` on top of ZIB-639; and every
published nl-core R4 version is a pre-release. Only `nl-core-Patient` would validate clean today.
Re-check against the package before reopening this — an earlier version of README asserted
nl-core conformance that was never true.

**Only assert `meta.profile` where the resource validates clean.** Half-meeting a profile and
claiming it is worse than claiming nothing. Nothing asserts one today. The output side is no longer
blocked, though: the Bundle `ResultBundleMapper` produces validates clean against
`fhirhub-ResultBundle`, so asserting it there is now a decision rather than a dependency.

**OIDs in `fhir/Systems.java` came from the HL7 NL OID register, not from memory.** A wrong
digit in a `system` is a silent interoperability bug that surfaces months later. Check the
register before editing one.

**Medication surveillance fails closed.** A drug whose code G-Standaard cannot resolve aborts
the session with a 400 instead of being dropped from the list. Surveillance over an incomplete
list answers "no interaction found" — a false negative a prescriber cannot distinguish from a
genuine all-clear. Do not soften this into a warning without a clinical decision behind it.

**Errors must be `OperationOutcome`.** Throw HAPI's `BaseServerResponseException` subclasses
and let the server render them. Use `error/UnauthorizedException` rather than HAPI's
`AuthenticationException` for 401s — HAPI special-cases the latter into a `text/plain` body,
which would be the one un-parseable response in the API.

**Jackson comes from HAPI, and from nowhere else.** `spring-boot-starter-jackson` is excluded
from `starter-web` so only one Jackson is on the classpath, pinned by HAPI, which serialises
every `/fhir/*` response. Nothing here needs Spring to serialise JSON. The Jackson 2 / Jackson 3
collision that used to live here came in via `formularium-api` and left with `gstandaard-jar`;
if you add a dependency that brings `tools.jackson` back, expect the context to fail at
`RestClient` construction with `NoClassDefFoundError: …JsonSerializeAs`.

## Deliberately different from `json-interface`

**Current medication is enriched before it is sent, and fails closed.** `json-interface` emits
`<medication>` from the host's own codes at the level supplied — its `getAdditionalDrugCodes`
call is commented out in `translate-xml.ts`, so `additionalCodes` stays empty and no PRK + GPK
pair is added. `MedicationCodeResolver` does that lookup here, which is what makes an
unresolvable code a 400 rather than a silently thinner medication list. Do not "restore
compatibility" by reverting it.

`prescriptor-api`'s `OpenSessionRequestBuilder.getAllergies` is the authority on which allergy
member carries which subsystem (`Allergies`→OGGRP, `AlStam`→SNK, `AlStof`→SSK). Both interfaces
populate all three; an earlier version of this file recorded `AlStof` as empty in
`json-interface`, which has not been true since it was fixed there. Re-read `translate-xml.ts`
before asserting a difference — several claimed corrections have since been fixed upstream.

## Behaviour that looks like a bug and is not

- `$session-result` ends the session upstream, so a second call returns 401. Inherited from
  Prescriptor, documented in the README under *Known quirks*.
- **`Unknown element 'author' found while parsing`, three times at startup.** Nothing to do with
  this codebase. It comes from `HapiFhirStorageResponseCode.json`, a CodeSystem shipped inside
  `hapi-fhir-base` and loaded lazily by `DefaultProfileValidationSupportBundleStrategy` the first
  time the validator resolves a ValueSet — so since the validator warm-up, at every boot. The
  file carries `CodeSystem.author`, which exists in R5 but not in R4, so the R4 parser drops it
  and the lenient handler says so. It is HAPI's own storage-response CodeSystem, irrelevant to a
  stateless proxy, and the element it drops is a name.

  **Do not silence `ca.uhn.fhir.parser.LenientErrorHandler` to make it go away.** Inbound request
  bodies are parsed by the same lenient handler and log through the same logger, so muting it
  also mutes the only signal that an integrator is sending elements this interface silently
  ignores — which is precisely the kind of quiet data loss the rest of this file is about.

## Testing

Unit tests per mapper, plus `FhirHubIntegrationTest` which exercises all three operations over
real HTTP with WireMock standing in for Prescriptor. That integration test is where credential
forwarding, OperationOutcome rendering, and the CapabilityStatement are pinned — the things
unit tests cannot see. Fixtures live in `src/test/resources/xmlrpc/`; the stand-in `medcode`
view is `src/test/resources/gstandaard-medcode.sql`.

`json-interface` had no tests at all. Keep this one from going the same way.
