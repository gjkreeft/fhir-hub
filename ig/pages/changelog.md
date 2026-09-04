Every release of this guide, newest first. What a version number promises is in
[Versioning and change policy](versioning.html).

Each entry names the version, the date it was published, and every change grouped by whether it
can affect a payload you already send. A release with no **Breaking** heading broke nothing.

### 0.2.0 — medication surveillance, published and not implemented

`draft`, 2026-09-02. **Nothing breaks.** Every request accepted at 0.1.0 is accepted unchanged, and
no response gained an element. Additive, on a FHIR base that did not exist before.

**A second contract, on a second base.** `POST /fhir/surveillance/$check-medication` asks the
medication-surveillance question directly — a patient's context and one or more proposed
prescriptions in, the signals that fire out — without a session or a browser round trip.

**It is not implemented.** A conformant request is answered with **501 Not Implemented** and an
`OperationOutcome` whose `issue.code` is `not-supported`. Nothing about a patient's medication may
be concluded from that response, and no release of your product should depend on this endpoint yet.
It is published because the request contract is worth reviewing and building against before the
check behind it exists: a malformed body is a 400 naming what is wrong with it, exactly as it will
be when the check goes live.

What this release adds:

- 1 profile, `fhirhub-SurveillanceInput`, marked `experimental` — the request body of
  `$check-medication`. It reuses the resource profiles of the session contract unchanged, so a host
  that already opens sessions has no new payload to shape, only a new address to post to
- 1 invariant, `fhirhub-something-to-check`: at least one `prescription` or one
  `medicationStatement`, because a request carrying neither has nothing to evaluate
- 1 example, `$check-medication request`, validated against its profile like every other
- A second FHIR base, `/fhir/surveillance`, with its own unauthenticated
  `GET /fhir/surveillance/metadata` and its own generated `OperationDefinition`. The credentials,
  the content types, the error shape and the version number are the same as on `/fhir/evs`

**The guide is now organised by application.** `fhir-hub` was documented as "the interface to
Prescriptor 3", which stopped being true with this release: it is one interface in front of two
distinct applications. [Prescriptor](prescriptor.html) and [Surveillance](surveillance.html) each
have a page saying what they are, which base they answer on and how they differ, and the menu has
a group for each. Nothing about the Prescriptor payloads moved — the operation pages, the profiles
and the examples are where they were — but the page that used to be *Global flow* is now
[The Prescriptor flow](flow.html), because that is whose flow it is.

**No response profile is published.** `DetectedIssue` is where it is heading; the severity grades,
how a rule's own text and action come across, and whether a partial answer is ever permitted are
all open. A profile with nothing behind it would be a promise this interface cannot keep.

**The version number moved for a change to a contract you may not use.** One release covers both
bases — see [Versioning and change policy](versioning.html) — so an EVS-only integrator reads 0.2.0
in `GET /fhir/evs/metadata` and has nothing to do about it. Nothing on the EVS contract changed in
this release.

### 0.1.0 — first release

`draft`, 2026-08-28. Nothing preceded it, so nothing is reported as changed.

What it contains:

- The three operations — `$formulary-session`, `$createrx-session`, `$session-result` — with the
  input and output specification for each
- 13 profiles: the two session requests, the session response, the `$session-result` Bundle, and
  the resources a host sends in or receives back
- 2 Digitalis extensions: `ext-Dosage.CodedDirections` and
  `ext-MedicationRequest.OpiumActClassification`
- 1 Digitalis code system, the Opiumwet subset of G-Standaard `BST401T`, and 9 value sets binding
  the national systems this interface routes
- 4 example payloads, each validated against the profile it claims

Known gaps at this release are listed under [Current limitations](limitations.html). The one worth
repeating here: the artifacts are `draft`, so a breaking change can still arrive at a minor
version.
