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
mvn test                 # 101 tests; no network and no database — WireMock stubs
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
house except what it declares itself; see *No house libraries, and `gstandaard-jar` least of
all* below.

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

**Lab values are LOINC end to end, and the list of them is the G-Standaard's.** There is no NHG
Tabel 45 mapping and there should not be one: the upstream carries lab data as `<LOINC num=…>`, and
the MFB datatest generator (`g-standaard/GStandaard/apps/mfb/functions`) builds a `DatatestLOINC`
keyed on that number, so translating would add a table to maintain and a class of determinations
that cannot be expressed at all.

`fhir/LabDeterminations` holds the list, and it is a copy of published data: `BST684T` rows with
`MFBEXSRT = 4` ("LOINC / Nederlandse Labcodeset") say which LOINC codes count as which MFB
parameter, and `BST685T` rows with `THMFBP = 2000` are the twelve measurements a rule can test at
all — current rules use four, the nierfunctie in 666 of them. Weight and height are read by dose
checking rather than by the rules, through `evs2.0`'s own LOINC xpath. A code outside the list is
refused, because forwarding it would tell a prescriber their lab data had been weighed when nothing
read it.

One eGFR code, `62238-1` (CKD-EPI), because that is what Dutch laboratories report. The G-Standaard
also lists `77147-7` (MDRD) and `50210-4` (cystatin C) for the same parameter — adding them is one
line each, but re-labelling one formula as another is not on, since they do not give the same
number.

Units are pinned per code and converted only where the conversion is exact (`m` → `cm`). The value
is evaluated in the unit the rule was written in, so kalium in mg/dL is a different answer, not a
rounded one. The eGFR must arrive as `mL/min/{1.73_m2}`: the G-Standaard compares it against ml/min
thresholds unchanged, which is its decision to make and not one to hide behind a permissive unit.

`LabDeterminationsTest` pins the table against `LabDeterminationVS`, which the profile binds — and
note that binding *does* catch a wrong code even though LOINC is not distributed here, because the
value set enumerates its concepts. That is the mechanism the G-Standaard bindings cannot use.

**The two session types read their key from different members** — `PrescriptorSessionKey` for
formulary, `SessionKey` for CreateRx. An upstream inconsistency, encoded in `SessionType` and
pinned by a test.

**No house libraries, and `gstandaard-jar` least of all.** It is the obvious one to reach for,
and there are three types in it this service could use — `T25Parser`, `GStandaardDatabase`, and
(transitively, from `formularium-api`) the `T25` DTO. None of its sixteen DAOs, and none of the
three worth the price: it drags in `formularium-api`, which brings Jackson 3 and collides with
HAPI's Jackson 2, and it pins its own Spring Boot version, which splits this application's Boot
modules across two minors.

What stands in for each:

- `GStandaardDatabase` + `GStandaardJdbcBeanFactory` → `gstandaard/GStandaardJdbcConfig`, which
  builds the same named Hikari pool from the same `gstandaard.datasource.*` properties, and a
  plain `JdbcTemplate`. Deployment configuration is the house one either way. Spring's own
  DataSource auto-configuration stays excluded, because this config builds its own named
  datasource.
- `T25Parser` → nothing. See *The coded dosage is passed through, not decoded* below.

`MedicationCodeResolver` queries `medcode` directly, because the DAOs do not cover the PRK+GPK
pair it needs. Adding a house library to get them back trades a working query for a dependency
collision.

**The coded dosage is passed through, not decoded.** `T25DosageMapper` emits `Dosage.text` and
the `CodedDirections` extension, both verbatim, and derives no `timing` or `doseAndRate`.
Decoding NHG Tabel 25 into structured elements is lossy — the b-codes and the trailing free text
have nowhere to go, and Tabel 25 units are not UCUM — no consumer reads the result, and
`SessionParametersMapper` would ignore a host's edits to it, because it reads the extension back
instead. `T25DosageMapperTest.derivesNoStructureFromTheCodedString` pins this. The class Javadoc
records what would have to be true to change the decision: the target is the zib
Gebruiksinstructie model from Medicatieproces 9, not a component split.

**`../json-interface` is the contract of record; fhir-hub tracks its version.** Currently v2
(`api/openapi.yaml`, `info.version: "2"`). When it moves again, diff these against it: the
`xisInfo` element, `MedicationType`, the `Prescription` member, the validation rules, and the
error wording all changed in v2 and all live in different files here. `XmlRpcRequestBuilderTest`
pins the wire format, so start there.

**`MedicationType` is a constant `9` (PRK), and that is load-bearing rather than lazy.** Upstream
it is not a description of the codes but an instruction: the open-session handler switches on it
once and then reads *that one attribute* off every `<drug>` element
(`../evs2.0/library/Prescriptor/OpenSession/Loader/Patient.php`), skipping any drug whose
attribute is absent — silently, because an empty code is dropped rather than rejected. Deriving
the value from the host's entries therefore makes a mixed PRK/HPK list unsafe: announce HPK, and
every PRK-coded entry vanishes from medication surveillance. `MedicationCodeResolver` resolves
every entry to a PRK + GPK pair, so PRK is the one attribute present on every drug this service
sends, whatever level the host used — which is what lets a host mix levels per entry.

Two things would invalidate the constant: dropping that enrichment, or a requirement to run
surveillance at HPK level. `0` is still sent for an empty list; note that the older
`../Webprescriptor/html/open_session.php` validates `MedicationType` against `{7,8,9}`, so verify
the empty-medication case against the live server before relying on it.

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

**It also decides the XML this service sends, which is why `XmlWriter` has a line that looks
redundant.** `hapi-fhir-validation` brings Woodstox, which registers itself as the StAX provider
and wins `XMLOutputFactory.newInstance()`. Woodstox serialises an empty element as
`<medication/>` where the JDK writes `<medication></medication>`, so the provider on the
classpath would otherwise decide the shape of every XML-RPC request. `XmlWriter.element` writes a
zero-length text event before the end tag to pin one form under both providers. Deleting that
line makes the wire format depend on dependency resolution.

**The cost is measured, in README under *Enforcement*:** +79 MB of dependencies, ~4.5 s for the
first validation (moved into startup by `warmUpValidator`), ~70 ms per request after that.

**The canonical is `http://spec.digitalis.nl/fhir`** — a host of its own for the artifacts,
independent of the corporate site and of whatever serves the API, with room under `/fhir` for the
other contract Digitalis publishes. One of the URLs under it is read as well as written:
`DigitalisExtensions.CODED_DIRECTIONS` is the extension `$createrx-session` reads back off a
prescription a host hands in. Changing that one breaks a round trip in a way the others cannot,
because falling through to `Dosage.text` loses the coded instruction silently rather than
failing. `IgCanonicalsTest` pins the canonical against `sushi-config.yaml`.

**A `Coding.system` is a URI, and the upstream tokens are not.** `PRK`, `SSK`, `ICPC` and the
rest are XML attribute names in the DigitalisRx document, and `CodeSystemRegistry` maps in one
direction only: `tokenFor` takes a system URI, `systemFor` produces one. Accepting a token as a
`Coding.system` would give the interface a second inbound dialect that no profile describes — and
it would be the form a host finds first, because it is shorter and it is what `json-interface`
takes. A rejection names the accepted URIs rather than the tokens, for the same reason.

**Do not define the G-Standaard code systems in `ig/`.** It reads like an improvement and is the
opposite: the tables are licensed and undistributable, so a definition would have to be
`content: not-present`, which makes any value set including it unexpandable and turns every
coding in it into a validation *error*. Left undefined, the system falls to
`UnknownCodeSystemWarningValidationSupport` and becomes a warning, which is the only way a
`required` binding onto a licensed table is ever satisfied. `TerminologyEnforcementTest` fails if
someone acts on the intuition.

**The profiles live in `ig/`, and the ids in them are load-bearing.** SUSHI derives a canonical
as `{canonical}/StructureDefinition/{Id}`, and those canonicals are on the wire, so renaming an
id silently breaks every payload in the field. `IgCanonicalsTest` fails if the FSH
and the constants in `fhir/` drift apart. Note the corollary — base R4 requires
several elements this interface never reads (`AllergyIntolerance.patient` and `clinicalStatus`,
`Condition.subject`, `MedicationStatement.status`/`subject`, `Observation.status`), and a
profile can only constrain, so callers must send them — and since validation runs before
mapping, they are required in practice and not only on paper. A payload that omits them is a 400
even though nothing here would have read them.

**nl-core is not derived from, and the reasons were checked.** `nl-core-MedicationContraIndication`
profiles `Flag` rather than `Condition`; `nl-core-MedicationUse2` is not in the nl-core package
at all; `nl-core-LaboratoryTestResult` wants an `Observation.category` this interface does not read
(its TestCode objection died with the move to LOINC, so ZIB-639 is no longer the blocker); and every
published nl-core R4 version is a pre-release. Only `nl-core-Patient` would validate clean today.
Re-check against the package before reopening this, and do not assert nl-core conformance
anywhere on the strength of the intention.

**Only assert `meta.profile` where the resource validates clean.** Half-meeting a profile and
claiming it is worse than claiming nothing, and nothing here asserts one. The Bundle
`ResultBundleMapper` produces does validate clean against `fhirhub-ResultBundle`
(`OutboundPayloadConformanceTest`), so asserting it there is a decision rather than a dependency
— but the Implementation Guide tells integrators not to route on `meta.profile`, so the two have
to move together.

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
every `/fhir/*` response. Nothing here needs Spring to serialise JSON. Jackson 3 reaches this
codebase through `formularium-api`, which arrives with `gstandaard-jar` — see *No house
libraries* above — and a Jackson 2 / Jackson 3 collision does not fail at the boundary that
caused it: expect the context to fail at `RestClient` construction with
`NoClassDefFoundError: …JsonSerializeAs`.

## Deliberately different from `json-interface`

**Current medication is enriched before it is sent, and fails closed.** `json-interface` emits
`<medication>` from the host's own codes at the level supplied — its `getAdditionalDrugCodes`
call is commented out in `translate-xml.ts`, so `additionalCodes` stays empty and no PRK + GPK
pair is added. `MedicationCodeResolver` does that lookup here, which is what makes an
unresolvable code a 400 rather than a silently thinner medication list. Do not "restore
compatibility" by dropping the lookup.

`prescriptor-api`'s `OpenSessionRequestBuilder.getAllergies` is the authority on which allergy
member carries which subsystem (`Allergies`→OGGRP, `AlStam`→SNK, `AlStof`→SSK). Both interfaces
populate all three. Re-read `translate-xml.ts` before asserting a difference here: it moves, and
a difference recorded from memory tends to describe a version of it that no longer runs.

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

Two tests guard claims the rest of the build cannot see, and both look deletable:

- `IgExampleConformanceTest` validates every example in `ig/fsh-generated` against the profile it
  declares. SUSHI validates nothing — it converts FSH to JSON — so without this an example can
  contradict both its profile and the payload the mappers build, and the build stays green.
- `TerminologyEnforcementTest` pins which `Coding.system` forms a session can be opened with, and
  fails if a G-Standaard code system is defined in `ig/`. See *Do not define the G-Standaard code
  systems in `ig/`* above.

`json-interface` had no tests at all. Keep this one from going the same way.
