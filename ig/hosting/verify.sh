#!/usr/bin/env bash
#
# Checks that a deployed guide actually behaves the way the spec says it does.
#
#   hosting/verify.sh                                  # against http://spec.digitalis.nl
#   hosting/verify.sh http://localhost:8081            # against a staging server
#
# This exists because the interesting half of the deployment is not the files — the publisher
# produces those — but the URL mapping in nginx-fhir.conf: whether a canonical dereferences at
# and whether a machine and a browser each get what they asked for. Neither is visible by looking
# at the output directory, and both are what an integrator's validator depends on.
#
# Run it after every deploy, and after any edit to the server configuration.

set -uo pipefail

# https by default, because that is the scheme tooling can actually use: the HL7 validator's SSRF
# protection refuses a plain-http fetch before it makes the request. The http checks at the end
# cover the browser and curl case.
readonly BASE="${1:-https://spec.digitalis.nl}"
readonly IG="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

version="$(sed -n 's/^version: *//p' "$IG/sushi-config.yaml" | head -1 | tr -d '"'"'"'')"

failures=0

# check <description> <expected-substring-of-content-type> <curl args...>
check() {
	local what="$1" expect="$2"; shift 2
	local out status ctype
	out="$(curl -sS -o /dev/null -w '%{http_code} %{content_type}' "$@" 2>&1)" || {
		printf 'FAIL  %s\n      curl: %s\n' "$what" "$out"; failures=$((failures + 1)); return
	}
	status="${out%% *}"
	ctype="${out#* }"
	if [[ "$status" != "200" ]]; then
		printf 'FAIL  %s\n      expected 200, got %s\n' "$what" "$status"
		failures=$((failures + 1))
	elif [[ "$ctype" != *"$expect"* ]]; then
		printf 'FAIL  %s\n      expected Content-Type containing %s, got %s\n' "$what" "$expect" "$ctype"
		failures=$((failures + 1))
	else
		printf 'ok    %s  (%s)\n' "$what" "$ctype"
	fi
}

readonly PROFILE="$BASE/fhir/StructureDefinition/fhirhub-FormularySessionInput"
# An extension id carries a dot. It is the one that breaks a careless rewrite rule.
readonly EXTENSION="$BASE/fhir/StructureDefinition/ext-Dosage.CodedDirections"

echo "verifying $BASE (release $version)"
echo

echo "-- canonical URLs dereference, and content negotiation works"
check "profile canonical, as FHIR JSON" "application/fhir+json" -H 'Accept: application/fhir+json' "$PROFILE"
check "profile canonical, as a page"    "text/html"             -H 'Accept: text/html'             "$PROFILE"
check "profile canonical, ?_format=json overrides Accept" "application/fhir+json" \
	-H 'Accept: text/html' "$PROFILE?_format=json"
check "profile canonical, as FHIR XML"  "application/fhir+xml"  -H 'Accept: application/fhir+xml'  "$PROFILE"
check "extension canonical (id contains a dot)" "application/fhir+json" \
	-H 'Accept: application/fhir+json' "$EXTENSION"
check "value set canonical" "application/fhir+json" \
	-H 'Accept: application/fhir+json' "$BASE/fhir/ValueSet/lab-determination"
check "code system canonical" "application/fhir+json" \
	-H 'Accept: application/fhir+json' "$BASE/fhir/CodeSystem/gstandaard-bijzonder-kenmerk"

echo
echo "-- the pinned release"
check "versioned canonical" "application/fhir+json" \
	-H 'Accept: application/fhir+json' "$BASE/fhir/$version/StructureDefinition/fhirhub-FormularySessionInput"
check "versioned package"   "application/gzip" "$BASE/fhir/$version/package.tgz"

echo
echo "-- the guide itself"
check "landing page"     "text/html"        "$BASE/fhir/"
check "package"          "application/gzip" "$BASE/fhir/package.tgz"
check "package-list"     "json"             "$BASE/fhir/package-list.json"
check "artifact index"   "text/html"        "$BASE/fhir/artifacts.html"

echo
echo "-- a cache in between must not mix the representations"
vary="$(curl -sSI -H 'Accept: application/fhir+json' "$PROFILE" 2>/dev/null | tr -d '\r' | grep -i '^vary:' || true)"
if [[ "$vary" == *[Aa]ccept* ]]; then
	printf 'ok    Vary: Accept is set  (%s)\n' "$vary"
else
	printf 'FAIL  Vary: Accept is missing on a negotiated response\n'
	failures=$((failures + 1))
fi

echo
echo "-- the other scheme gets you to the content too"
# A canonical is an http URL, so that is the one integrators paste into a browser, and it has to
# lead somewhere. Redirects are followed rather than rejected: a 301 to https that preserves the
# path is the right answer, and is what spec.digitalis.nl does. It is not the scheme tooling
# fetches over — that one refuses http by scheme before making the request — so what matters here
# is only that a person or a curl ends up at the page.
other="${BASE/#https:/http:}"
if [[ "$other" == "$BASE" ]]; then
	other="${BASE/#http:/https:}"
	printf 'note  checked over http; https is the one FHIR tooling can fetch\n'
fi
check "landing page over ${other%%:*} (redirects followed)" "text/html" -L "$other/fhir/"
check "package over ${other%%:*} (redirects followed)" "application/gzip" -L "$other/fhir/package.tgz"

echo
echo "-- the whole integrator flow, which is the only check that really counts"
echo "   (needs validator_cli.jar; SSRF protection stays ON, so this also proves the scheme)"
cat <<HOWTO
   java -jar validator_cli.jar -version 4.0.1 \\
     -ig ${BASE/#http:/https:}/fhir/package.tgz \\
     -profile http://spec.digitalis.nl/fhir/StructureDefinition/fhirhub-FormularySessionInput \\
     your-request.json

   It must log 'Load nl.digitalis.fhirhub' before you believe any result it prints.
HOWTO

echo
if [[ "$failures" -eq 0 ]]; then
	echo "all checks passed"
else
	echo "$failures check(s) failed"
	exit 1
fi
