#!/usr/bin/env node
//
// Stamps the IG version onto the conformance resources SUSHI writes.
//
// Why this exists: with `FSHOnly: true` SUSHI stamped `version` itself. Now that this project
// builds a real Implementation Guide, SUSHI leaves `version`, `publisher` and `jurisdiction` off
// and the IG Publisher applies them on the way into `output/` — which is correct for the
// publisher and wrong for us, because `ig/fsh-generated/resources` is not only an intermediate
// here: the Maven build copies it into the jar and the running service validates every request
// against those files. Without this step the service would enforce version-less profiles while
// the published package says 0.1.0, and an `OperationOutcome` would stop naming the version the
// rule came from — which the change policy promises it does.
//
// Only `version` is stamped. `publisher` and `jurisdiction` are for a reader of the published
// artifact and nothing at runtime looks at them, so they are left to the publisher rather than
// duplicated here.
//
// IgCanonicalsTest.theProfilesCarryTheIgVersion fails if this has not been run.

import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const IG = join(dirname(fileURLToPath(import.meta.url)), '..');
const RESOURCES = join(IG, 'fsh-generated', 'resources');

// The same resource types the Maven build copies into the jar.
const CONFORMANCE = new Set(['StructureDefinition', 'ValueSet', 'CodeSystem']);

const config = readFileSync(join(IG, 'sushi-config.yaml'), 'utf8');
const version = /^version: *["']?([^"'\s]+)["']?$/m.exec(config)?.[1];

if (!version) {
	console.error('stamp-version: no `version:` in sushi-config.yaml');
	process.exit(1);
}

let stamped = 0;
for (const file of readdirSync(RESOURCES).filter((f) => f.endsWith('.json'))) {
	const path = join(RESOURCES, file);
	const resource = JSON.parse(readFileSync(path, 'utf8'));

	if (!CONFORMANCE.has(resource.resourceType) || resource.version === version) {
		continue;
	}

	// Rebuilt key by key rather than assigned, so `version` lands where FHIR's element order
	// puts it and the committed diff stays readable.
	const stampedResource = {};
	for (const [key, value] of Object.entries(resource)) {
		stampedResource[key] = value;
		if (key === 'url') {
			stampedResource.version = version;
		}
	}
	if (!stampedResource.version) {
		stampedResource.version = version;
	}

	writeFileSync(path, JSON.stringify(stampedResource, null, 2) + '\n');
	stamped++;
}

console.log(`stamp-version: version ${version} stamped onto ${stamped} conformance resources`);
