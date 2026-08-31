<!-- GUIDE-PREAMBLE -->

### The three calls

```
POST   /fhir/evs/$formulary-session        Parameters  ->  Parameters (sessionId, url)
POST   /fhir/evs/$createrx-session         Parameters  ->  Parameters (sessionId, url)
GET    /fhir/evs/$session-result?session=  ->  Bundle (MedicationRequest, Communication)
GET    /fhir/evs/metadata                  ->  CapabilityStatement (unauthenticated)
```

That is the whole surface. There is no resource REST API and no search — see
[Global flow](flow.html) for how the three fit together, and
[Conventions](conventions.html) for content types, formats and how a malformed request is
reported.

### Where to start

| If you are | Start at |
| --- | --- |
| Integrating for the first time | [Global flow](flow.html), then [Authentication](authentication.html) |
| Replacing a JSON API integration | [Moving from the JSON API](migration.html) |
| Building a request | the operation pages, then [Code systems](code-systems.html) |
| Wiring up a validator | [Downloads and validation](downloads.html) |
| Deciding what you can rely on | [Behaviour to design around](design-notes.html) and [Current limitations](limitations.html) |

### The machine-readable half

Every payload has a `StructureDefinition`, and they are not decoration: a request body is
validated against its profile *before* anything else happens, so what is written here is what
the service enforces. The [Artifacts](artifacts.html) page indexes all of them; the four you
will validate against directly are:

| Payload | Profile |
| --- | --- |
| `$formulary-session` request | [fhirhub-FormularySessionInput](StructureDefinition-fhirhub-FormularySessionInput.html) |
| `$createrx-session` request | [fhirhub-CreateRxSessionInput](StructureDefinition-fhirhub-CreateRxSessionInput.html) |
| Session response | [fhirhub-SessionOutput](StructureDefinition-fhirhub-SessionOutput.html) |
| `$session-result` response | [fhirhub-ResultBundle](StructureDefinition-fhirhub-ResultBundle.html) |

The example instances on those pages are the same payloads that appear in the prose, and each
one is validated against the profile it claims on every build of this guide — so an example
here cannot contradict either the profile or the running service.

### Status

This is release **0.1.0**, `draft`. Read [Versioning and change policy](versioning.html) before
you go live: while the guide is `draft`, a breaking change can still arrive at a minor version,
and the policy says how you will be told. [Current limitations](limitations.html) lists what you
may expect to be able to do and cannot yet.

### Getting in touch

Questions, or a case this guide does not cover: contact Digitalis.
