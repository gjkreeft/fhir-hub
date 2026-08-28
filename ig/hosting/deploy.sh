#!/usr/bin/env bash
#
# Publishes the built Implementation Guide to the document root hosting/nginx-fhir.conf expects.
#
#   hosting/deploy.sh /srv/spec.digitalis.nl
#
# The layout it produces:
#
#   <root>/fhir/            the current release — republished on every deploy
#   <root>/fhir/0.1.0/      a frozen copy of that release, never rewritten
#
# The frozen copy is the point of the whole arrangement: a validator that names
# /fhir/0.1.0/package.tgz gets the same bytes forever, so a build cannot change under an
# integrator. This script therefore REFUSES to overwrite an existing snapshot. If a release went
# out wrong, the fix is the next version, not an edit to a published one — someone has already
# validated against those bytes and would not be told.
#
# Everything here is `cp`, `mv` and `chmod` on purpose — no rsync, which is not installed on
# every web server and was not on this one. There is no state, no database and nothing to roll
# back beyond restoring a directory.
#
# The release is assembled in a staging directory beside the target and swapped in with two
# renames. That is not decoration: a copy straight over the live tree serves a mix of two releases
# for as long as the copy takes, and it lets files a later build stopped producing accumulate
# forever. The staging tree cannot do either. The cost is a window of a few milliseconds where
# /fhir does not exist.
#
# It needs no root — only write access to the document root, which it checks for before touching
# anything and reports with the fix if it is missing. Nothing here reloads or configures a web
# server; installing hosting/nginx-maps.conf and hosting/nginx-fhir.conf is a one-time
# root-privileged step, done separately and by hand.

set -euo pipefail

# nginx runs as another user, so anything it cannot read is a 403 rather than a page. A personal
# umask of 077 is common and would produce exactly that, on every file, silently — so the
# permissions of what is published are set here rather than inherited, and swept again
# after each copy.
umask 022

readonly IG="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly OUTPUT="$IG/output"
readonly QA="$OUTPUT/qa.json"

usage() {
	cat >&2 <<USAGE
usage: hosting/deploy.sh <document-root> [--allow-qa-errors] [--republish-snapshot]

  <document-root>        e.g. /srv/spec.digitalis.nl. Created if absent.
  --allow-qa-errors      publish even though the publisher's QA reports errors.
  --republish-snapshot   overwrite an already-published versioned snapshot. Read the note in
                         this script before you reach for it.
USAGE
	exit 2
}

[[ $# -ge 1 ]] || usage
readonly ROOT="$1"; shift
allow_qa_errors=false
republish_snapshot=false
while [[ $# -gt 0 ]]; do
	case "$1" in
		--allow-qa-errors)    allow_qa_errors=true ;;
		--republish-snapshot) republish_snapshot=true ;;
		*) usage ;;
	esac
	shift
done

# ---------------------------------------------------------------------------------------------
# Refuse to publish something that was not built, or that the publisher itself flags.
# ---------------------------------------------------------------------------------------------

if [[ ! -f "$OUTPUT/index.html" ]]; then
	echo "deploy: no build in $OUTPUT — run 'npm run build' in ig/ first" >&2
	exit 1
fi

# The version comes from sushi-config.yaml rather than from an argument: the number in the
# snapshot path has to be the number stamped on the artifacts, and there is only one way to be
# sure of that.
version="$(sed -n 's/^version: *//p' "$IG/sushi-config.yaml" | head -1 | tr -d '"'"'"'')"
if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
	echo "deploy: could not read a three-part version from sushi-config.yaml (got '${version}')" >&2
	echo "        The server config only routes /fhir/<major>.<minor>.<patch>/, so a snapshot needs one" >&2
	exit 1
fi

# package-list.json is how FHIR tooling — and the IG Publisher's own version check — discovers
# which releases exist and where they are. A release missing from it is a release nothing can
# find, so it is a gate rather than a copy.
readonly PACKAGE_LIST="$IG/package-list.json"
if [[ ! -f "$PACKAGE_LIST" ]]; then
	echo "deploy: $PACKAGE_LIST is missing — tooling reads it to discover releases" >&2
	exit 1
fi
if ! grep -q "\"version\": *\"$version\"" "$PACKAGE_LIST"; then
	echo "deploy: package-list.json has no entry for $version." >&2
	echo "        Add one — version, date, desc, path, status — next to the Changelog entry." >&2
	exit 1
fi

if [[ -f "$QA" ]]; then
	errors="$(sed -n 's/.*"errs" *: *\([0-9]*\).*/\1/p' "$QA" | head -1)"
	if [[ -n "$errors" && "$errors" != "0" ]]; then
		echo "deploy: the publisher's QA reports $errors error(s) — see $OUTPUT/qa.html" >&2
		$allow_qa_errors || { echo "        pass --allow-qa-errors to publish anyway" >&2; exit 1; }
		echo "deploy: publishing anyway (--allow-qa-errors)" >&2
	fi
fi

# ---------------------------------------------------------------------------------------------
# Publish.
# ---------------------------------------------------------------------------------------------

readonly CURRENT="$ROOT/fhir"
readonly SNAPSHOT="$CURRENT/$version"

# Nothing here needs root, but it does need write access to the document root. Checked before any
# file is touched: a deploy that fails halfway leaves the guide serving a mix of two releases.
writable="$ROOT"
while [[ ! -e "$writable" && "$writable" != "/" ]]; do
	writable="$(dirname "$writable")"
done
if [[ ! -w "$writable" ]]; then
	echo "deploy: $writable is not writable by $(id -un)." >&2
	echo "        Either have the document root given to your user or group —" >&2
	echo "            sudo install -d -o $(id -un) -g $(id -gn) -m 755 $ROOT/fhir" >&2
	echo "        — or re-run this script under sudo:" >&2
	echo "            sudo hosting/deploy.sh $ROOT" >&2
	exit 1
fi

if [[ -d "$SNAPSHOT" ]] && ! $republish_snapshot; then
	echo "deploy: $SNAPSHOT is already published, and a published release is not edited in place." >&2
	echo "        Bump 'version' in sushi-config.yaml and rebuild, or pass --republish-snapshot if" >&2
	echo "        you are certain nobody has validated against it yet." >&2
	exit 1
fi

mkdir -p "$ROOT"

# Beside the target rather than in /tmp, so the swap at the end is a rename within one filesystem
# rather than a copy across two.
readonly STAGE="$ROOT/.fhir-publishing.$$"
trap 'chmod -R u+w "$STAGE" "$STAGE.old" 2>/dev/null; rm -rf "$STAGE" "$STAGE.old"' EXIT
rm -rf "$STAGE"
mkdir -p "$STAGE"

# `cp -R` and not `cp -a`: -a preserves ownership, which only root can do, and the published tree
# has no use for the build machine's uids.
echo "deploy: staging the release"
cp -R "$OUTPUT/." "$STAGE/"

# Not part of the publisher's output: it describes the releases rather than one release.
install -m 644 "$PACKAGE_LIST" "$STAGE/package-list.json"

echo "deploy: staging the frozen $version snapshot"
mkdir -p "$STAGE/$version"
cp -R "$OUTPUT/." "$STAGE/$version/"

# Readable by the user nginx runs as, whatever this user's umask and the build tree's modes were.
chmod -R a+rX "$STAGE"

# Releases published earlier are moved across rather than copied: they are immutable, and moving
# them is both cheaper and the only way they survive the swap below.
if [[ -d "$CURRENT" ]]; then
	for published in "$CURRENT"/*; do
		name="$(basename "$published")"
		[[ -d "$published" ]] || continue
		[[ "$name" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || continue
		[[ "$name" != "$version" ]] || continue

		echo "deploy: carrying over $name"
		# Renaming a directory rewrites its own `..` entry, so it needs the write bit even though
		# nothing inside it changes — and a published snapshot has had that bit removed on
		# purpose. Restored for the move and taken away again on the other side.
		chmod -R u+w "$published" 2>/dev/null
		mv "$published" "$STAGE/$name"
		chmod -R a-w "$STAGE/$name" 2>/dev/null || echo "deploy: could not re-lock $name" >&2
	done
fi

# A snapshot is immutable, and read-only permissions say so to the next person as well as to the
# next script. nginx only ever reads. Applied before the swap, so the release is never briefly
# writable at its published path.
chmod -R a-w "$STAGE/$version" 2>/dev/null || echo "deploy: could not make $version read-only" >&2

# The publish itself: two renames. Whatever was at $CURRENT is only the previous copy of the
# current release by now — every snapshot has been moved into $STAGE already — so it is discarded.
echo "deploy: publishing -> $CURRENT"
if [[ -d "$CURRENT" ]]; then
	mv "$CURRENT" "$STAGE.old"
fi
mv "$STAGE" "$CURRENT"
chmod -R u+w "$STAGE.old" 2>/dev/null
rm -rf "$STAGE.old"
trap - EXIT

cat <<DONE

deploy: published $version.

  current   https://spec.digitalis.nl/fhir/
  pinned    https://spec.digitalis.nl/fhir/$version/
  package   https://spec.digitalis.nl/fhir/package.tgz

Now check it behaves, before you tell anyone:

  hosting/verify.sh https://spec.digitalis.nl
DONE
