#!/usr/bin/env bash
#
# Builds the publishable Implementation Guide: pages -> SUSHI -> version stamp -> IG Publisher.
#
# This is a script rather than a chain of npm scripts because of one line — the PATH line below.
#
# The IG Publisher shells out to `jekyll` by bare name and has no option to be told where it is,
# so jekyll has to be on the PATH of the process it inherits. `gem install --user-install jekyll`
# puts it somewhere that is not on a default PATH, and the failure that follows is a Java stack
# trace 45 seconds into the build —
#
#     Cannot run program "jekyll" (in directory ".../ig/temp/pages"): error=2
#
# which says nothing about gems, nothing about PATH, and arrives long after the point where a
# missing dependency should have been reported. So the PATH is assembled here from `gem env`
# rather than assumed, and every prerequisite is checked before any work starts.

set -euo pipefail

readonly IG="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$IG"

# ---------------------------------------------------------------------------------------------
# Preflight. Each failure names the fix, because each of these has cost someone an afternoon.
# ---------------------------------------------------------------------------------------------

# Every gem home's bin directory, taken from rubygems itself: the path contains the ruby minor
# version, so hard-coding one goes stale on the next `brew upgrade ruby`.
if command -v gem > /dev/null 2>&1; then
	while IFS= read -r gem_home; do
		[[ -d "$gem_home/bin" ]] && PATH="$gem_home/bin:$PATH"
	done < <(gem env gempath 2>/dev/null | tr ':' '\n')
	export PATH
fi

missing=0
need() {
	echo "build: $1" >&2
	echo "       $2" >&2
	missing=1
}

command -v java > /dev/null 2>&1 || need "java not found" "Java 17 or later is needed to run the IG Publisher."
command -v node > /dev/null 2>&1 || need "node not found" "Node is needed for SUSHI and the page build."

command -v jekyll > /dev/null 2>&1 || need \
	"jekyll not found on PATH (the IG Publisher execs it by name and cannot be told where it is)" \
	"gem install --user-install jekyll     # then re-run; this script finds it via 'gem env gempath'"

[[ -f "$IG/.pkg/publisher.jar" ]] || need \
	"ig/.pkg/publisher.jar is missing (~240 MB, deliberately not committed)" \
	"curl -sL -o .pkg/publisher.jar https://github.com/HL7/fhir-ig-publisher/releases/latest/download/publisher.jar"

[[ -d "$IG/node_modules" ]] || need "ig/node_modules is missing" "npm install"

if [[ "$missing" -ne 0 ]]; then
	exit 1
fi

echo "build: jekyll $(jekyll -v 2>/dev/null | tail -1) at $(command -v jekyll)"

# ---------------------------------------------------------------------------------------------
# Build.
# ---------------------------------------------------------------------------------------------

mkdir -p "$IG/.pkg"

node scripts/build-pages.mjs
npx sushi .
node scripts/stamp-version.mjs
java -jar .pkg/publisher.jar -ig . -no-sushi

# After the publisher, not before: the page it writes is the one the publisher deliberately does
# not — see scripts/build-history.mjs — and it is cloned from a page the publisher has just
# rendered.
node scripts/build-history.mjs

# ---------------------------------------------------------------------------------------------
# Report. The publisher exits 0 whatever its QA found, and 61 expected warnings are enough to
# bury one real error in the scroll-back, so the count is repeated here and errors fail the build.
# hosting/deploy.sh gates on the same number.
# ---------------------------------------------------------------------------------------------

readonly QA="$IG/output/qa.json"
if [[ ! -f "$QA" ]]; then
	echo "build: the publisher wrote no $QA — the build did not finish" >&2
	exit 1
fi

errors="$(sed -n 's/.*"errs" *: *\([0-9]*\).*/\1/p' "$QA" | head -1)"
warnings="$(sed -n 's/.*"warnings" *: *\([0-9]*\).*/\1/p' "$QA" | head -1)"

echo
echo "build: QA ${errors:-?} errors, ${warnings:-?} warnings — see output/qa.html"
echo "build: site at output/index.html"

if [[ "${errors:-1}" != "0" ]]; then
	echo "build: FAILED on QA errors. Warnings are expected here; errors are not." >&2
	exit 1
fi
