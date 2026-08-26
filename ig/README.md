# fhir-hub Implementation Guide

Conformance artifacts for the payloads described in `../IMPLEMENTATION_GUIDE.md`. FSH source in
`input/fsh/`; `fsh-generated/` is a build output and is not committed.

```bash
npm install          # once
npx sushi .          # FSH -> StructureDefinition/ValueSet/CodeSystem JSON in fsh-generated/
```

To validate the examples you also need the HL7 validator (~180 MB, not committed):

```bash
curl -sL -o .pkg/validator_cli.jar \
  https://github.com/hapifhir/org.hl7.fhir.core/releases/latest/download/validator_cli.jar

java -jar .pkg/validator_cli.jar -version 4.0.1 -ig fsh-generated/resources \
  fsh-generated/resources/Parameters-ExampleFormularySessionInput.json \
  fsh-generated/resources/Parameters-ExampleSessionOutput.json \
  fsh-generated/resources/Bundle-ExampleResultBundle.json
```

All three must report `0 errors`. The warnings that remain are expected and benign: the
G-Standaard code systems are `content: not-present` so codes cannot be checked, the
`data-absent-reason` references have no display, and `dom-6` wants a narrative.

## Things worth knowing

**The ids are load-bearing.** SUSHI derives a canonical as
`{canonical}/StructureDefinition/{Id}`, and those canonicals are already on the wire — see
`fhir/DigitalisExtensions.java` and `fhir/CodeSystemRegistry.java`. Renaming an id is a silent
breaking change for every payload in the field. `IgCanonicalsTest` in the Maven build fails if
the two drift apart.

**The parent is plain R4, not nl-core**, for reasons that were checked rather than assumed. See
*Profiles* in `../README.md`.

**The four G-Standaard subsystem URIs are placeholders.** The national OIDs are not yet pinned.
Do not publish this IG externally before they are — see *Open items* in `../README.md`.

**These profiles ARE enforced at runtime**, so editing one changes what the service accepts.
`fsh-generated/resources` is committed and copied into the jar by the Maven build — the Docker
image builds with Maven and no node, so it cannot regenerate them. After editing any FSH, run
`npx sushi .` and commit the result, or the running service keeps enforcing the old profile.
