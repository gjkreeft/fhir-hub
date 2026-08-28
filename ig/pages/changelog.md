Every release of this guide, newest first. What a version number promises is in
[Versioning and change policy](versioning.html).

Each entry names the version, the date it was published, and every change grouped by whether it
can affect a payload you already send. A release with no **Breaking** heading broke nothing.

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
