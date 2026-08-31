#!/usr/bin/env node
//
// Writes output/history.html — the "Directory of published versions".
//
// Every page the publisher renders carries a publish box reading "See the Directory of published
// versions", linked to {canonical}/history.html. The publisher does not write that page: it is
// produced by the publication process, which for a guide published through HL7 is the registry.
// This guide is published by hosting/deploy.sh, so the page has to be produced here — and until it
// was, the most visible link on the front door was a 404.
//
// It cannot be an ordinary page of the guide. The publisher refuses the name: list history.md
// under `pages:` and QA reports "That file name is reserved by the publication process ... the
// content generated here will be overwritten and lost", plus three broken links, because the
// absolute release URLs in the table do not exist inside output/. So the page is written after the
// publisher has run, from the same package-list.json that hosting/deploy.sh gates a release on —
// one list of releases, and it cannot fall behind the file that decides what a release is.
//
// The chrome is cloned from a page the publisher just rendered rather than written out here, so
// the header, menu, styles and footer are the ones the rest of the site uses and stay in step with
// the template on their own.

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const IG = join(dirname(fileURLToPath(import.meta.url)), '..');
const OUTPUT = join(IG, 'output');
const PACKAGE_LIST = join(IG, 'package-list.json');
const SHELL = join(OUTPUT, 'downloads.html');
const TARGET = join(OUTPUT, 'history.html');
const TITLE = 'Directory of published versions';

const fail = (message) => {
	console.error(`build-history: ${message}`);
	process.exit(1);
};

const escape = (value) =>
	String(value).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

// ---------------------------------------------------------------------------
// The releases.
// ---------------------------------------------------------------------------

if (!existsSync(PACKAGE_LIST)) {
	fail(`no ${PACKAGE_LIST}`);
}

const list = JSON.parse(readFileSync(PACKAGE_LIST, 'utf8'));
const releases = [...(list.list ?? [])];

if (releases.length === 0) {
	fail('package-list.json names no releases, so there is nothing to publish a directory of');
}

// Newest first. package-list.json is maintained by hand and is not required to be in order.
releases.sort((a, b) => String(b.date ?? '').localeCompare(String(a.date ?? '')));

const canonical = list.canonical ?? '';

const rows = releases
	.map((release) => {
		// The version's own permanent path, not the canonical root, even for the current release: a
		// directory of published versions is about the copies that do not move.
		const path = escape(release.path ?? '');
		const version = release.current
			? `<strong>${escape(release.version)}</strong> (current)`
			: escape(release.version);

		return [
			'      <tr>',
			`        <td>${version}</td>`,
			`        <td>${escape(release.date ?? '')}</td>`,
			`        <td>${escape(release.status ?? '')}</td>`,
			`        <td>${escape(release.fhirversion ?? '')}</td>`,
			`        <td><a href="${path}">${path}</a></td>`,
			`        <td>${escape(release.desc ?? '')}</td>`,
			'      </tr>',
		].join('\n');
	})
	.join('\n');

const content = `
  <h2>${TITLE}</h2>

  <p>Every published release of ${escape(list.title ?? 'this guide')}, newest first. Each version
  stays at its own path and is never rewritten, so a validator that names one keeps getting the
  same bytes. The current release is served from
  <a href="${escape(canonical)}/">${escape(canonical)}</a> as well.</p>

  <table class="grid">
    <thead>
      <tr>
        <th>Version</th>
        <th>Published</th>
        <th>Status</th>
        <th>FHIR</th>
        <th>Location</th>
        <th></th>
      </tr>
    </thead>
    <tbody>
${rows}
    </tbody>
  </table>

  <p>What changed in each release is in the <a href="changelog.html">Changelog</a>; what a version
  number promises is in <a href="versioning.html">Versioning and change policy</a>. The
  machine-readable form of this table is
  <a href="${escape(canonical)}/package-list.json">package-list.json</a>.</p>
</div>
`;

// ---------------------------------------------------------------------------
// The page. Cloned from a rendered one, with the content region swapped.
// ---------------------------------------------------------------------------

if (!existsSync(SHELL)) {
	fail(`no ${SHELL} to take the page furniture from — run the publisher first (npm run build)`);
}

const shell = readFileSync(SHELL, 'utf8');

// The publish box ends the header and the inner-wrapper closes the content; between them is
// everything a page says for itself.
const OPEN = '<!--EndReleaseHeader-->';
const CLOSE = '        </div>  <!-- /inner-wrapper -->';

const start = shell.indexOf(OPEN);
const end = shell.lastIndexOf(CLOSE);
if (start === -1 || end === -1 || end < start) {
	fail(`could not find the content region in ${SHELL}; the template has changed shape`);
}

let page = shell.slice(0, start + OPEN.length) + content + shell.slice(end);

const titled = page.replace(/<title>[^<]*<\/title>/, (whole) =>
	whole.replace(/<title>[^<]*(?= - )/, `<title>${TITLE}`)
);
if (titled === page) {
	fail(`could not retitle ${SHELL}; the template has changed shape`);
}
page = titled;

// The breadcrumb names the page it was cloned from.
const crumbed = page.replace(/<li><b>[^<]*<\/b><\/li>/, `<li><b>${TITLE}</b></li>`);
if (crumbed === page) {
	fail('could not rewrite the breadcrumb; the template has changed shape');
}
page = crumbed;

writeFileSync(TARGET, page);

console.log(
	`build-history: ${releases.length} release(s) from package-list.json into output/history.html`
);
