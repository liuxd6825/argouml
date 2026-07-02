#!/usr/bin/env bash
# tests/smoke/create-domain-model.sh
# End-to-end real-client test that builds a small class diagram via
# the argouml-ai HTTP server:
#   1. creates a brand-new diagram
#   2. adds 3 classes (Animal abstract, Dog, Owner) with attributes + methods
#   3. establishes 3 lines: inheritance, association, dependency
#   4. verifies the final model via GET /snapshot
#
# Reusable: each run uses a timestamped diagram name; can be invoked
# many times in a row without conflicts.
#
# Exit code: 0 = ALL PASS, 1 = at least one fail.

set -e
BIND=${BIND:-127.0.0.1}
PORT=${PORT:-18766}
BASE="http://${BIND}:${PORT}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# ---- counters ----
PASS=0
FAIL=0
EXIT_CODE=0

# ---- helpers (kept self-contained; mirror run-smoke-tests.sh) ----
req() {  # $1=method $2=path [$3=body]
    local method=$1 path=$2 body=${3:-}
    if [ -n "$body" ]; then
        curl -sS -m 10 -X "$method" "${BASE}${path}" \
            -H "Content-Type: application/json" -d "$body" \
            -w "\n%{http_code}" 2>/dev/null
    else
        curl -sS -m 10 -X "$method" "${BASE}${path}" \
            -w "\n%{http_code}" 2>/dev/null
    fi
}
status_of() { echo "$1" | tail -n1; }
body_of() { echo "$1" | sed '$d'; }

assert_status() {  # $1=label $2=expected $3=raw
    local label=$1 expected=$2 raw=$3
    local actual; actual=$(status_of "$raw")
    local body; body=$(body_of "$raw" | head -c 200)
    if [ "$actual" = "$expected" ]; then
        echo "  PASS  $1  [HTTP $actual]"
        PASS=$((PASS+1))
    else
        echo "  FAIL  $1  [expected $expected got $actual]"
        echo "        body: $body"
        FAIL=$((FAIL+1))
    fi
}

assert_contains() {  # $1=label $2=needle $3=raw
    local label=$1 needle=$2 raw=$3
    local body; body=$(body_of "$raw")
    if echo "$body" | grep -qF "$needle"; then
        echo "  PASS  $1"
        PASS=$((PASS+1))
    else
        local actual; actual=$(status_of "$raw")
        echo "  FAIL  $1  [no \"$needle\"; HTTP $actual]"
        echo "        body: $(echo "$body" | head -c 200)"
        FAIL=$((FAIL+1))
    fi
}

# ---- auto server lifecycle ----
STARTED_HERE=0
if ! curl -sf -m 2 "${BASE}/health" > /dev/null 2>&1; then
    echo "[create-domain-model] Server not running, starting it..."
    PORT="${PORT}" BIND="${BIND}" "${SCRIPT_DIR}/start-server.sh" \
        || { echo "[create-domain-model] FATAL: start failed" >&2; exit 1; }
    STARTED_HERE=1
fi
cleanup() {
    if [ "${STARTED_HERE}" = "1" ]; then
        echo ""
        echo "[create-domain-model] Stopping server (started by this run)..."
        PORT="${PORT}" BIND="${BIND}" "${SCRIPT_DIR}/stop-server.sh" || true
    fi
}
trap 'cleanup; exit $EXIT_CODE' EXIT INT TERM

# ---- diagram name (unique per run) ----
STAMP=$(date +%s%N)
DIAGRAM="AnimalDomain_${STAMP}"

# ---- execution ----
echo "Create domain model via real-client HTTP"
echo "  BASE:   ${BASE}"
echo "  Diagram: ${DIAGRAM}"
echo "  Started: $(date)"
echo ""

echo "=== Step 1: create the new diagram ==="
R=$(req POST /project/diagrams "{\"name\":\"${DIAGRAM}\"}")
assert_status "create diagram ${DIAGRAM} -> 201" 201 "$R"
assert_contains "create response has kind=class" '"kind":"class"' "$R"

echo ""
echo "=== Step 2: add classes ==="
# Animal (abstract) at (100, 80)
R=$(req POST "/d/${DIAGRAM}/classes" \
    '{"name":"Animal","x":100,"y":80,"isAbstract":true}')
assert_status "add Animal -> 201" 201 "$R"

# Dog at (300, 200)
R=$(req POST "/d/${DIAGRAM}/classes" \
    '{"name":"Dog","x":300,"y":200}')
assert_status "add Dog -> 201" 201 "$R"

# Owner at (500, 80)
R=$(req POST "/d/${DIAGRAM}/classes" \
    '{"name":"Owner","x":500,"y":80}')
assert_status "add Owner -> 201" 201 "$R"

echo ""
echo "=== Step 3: add attributes ==="
# Animal.name : String
R=$(req POST "/d/${DIAGRAM}/classes/Animal/attributes" \
    '{"name":"name","type":"String","visibility":"private"}')
assert_status "Animal.name -> 201" 201 "$R"

# Dog.breed : String
R=$(req POST "/d/${DIAGRAM}/classes/Dog/attributes" \
    '{"name":"breed","type":"String","visibility":"private"}')
assert_status "Dog.breed -> 201" 201 "$R"

# Owner.name : String + Owner.address : String
R=$(req POST "/d/${DIAGRAM}/classes/Owner/attributes" \
    '{"name":"name","type":"String","visibility":"private"}')
assert_status "Owner.name -> 201" 201 "$R"
R=$(req POST "/d/${DIAGRAM}/classes/Owner/attributes" \
    '{"name":"address","type":"String"}')
assert_status "Owner.address -> 201" 201 "$R"

echo ""
echo "=== Step 4: add operations (methods) ==="
R=$(req POST "/d/${DIAGRAM}/classes/Animal/operations" \
    '{"name":"speak","returnType":"void","visibility":"public"}')
assert_status "Animal.speak() -> 201" 201 "$R"

R=$(req POST "/d/${DIAGRAM}/classes/Dog/operations" \
    '{"name":"bark","returnType":"void","visibility":"public"}')
assert_status "Dog.bark() -> 201" 201 "$R"

R=$(req POST "/d/${DIAGRAM}/classes/Dog/operations" \
    '{"name":"speak","returnType":"void","visibility":"public"}')
assert_status "Dog.speak() (override) -> 201" 201 "$R"

R=$(req POST "/d/${DIAGRAM}/classes/Owner/operations" \
    '{"name":"getName","returnType":"String","visibility":"public"}')
assert_status "Owner.getName():String -> 201" 201 "$R"

echo ""
echo "=== Step 5: establish class-to-class lines ==="
# Inheritance: Dog extends Animal
R=$(req POST "/d/${DIAGRAM}/generalizations" \
    '{"subclass":"Dog","superclass":"Animal"}')
assert_status "Dog inherits Animal -> 201" 201 "$R"

# Association: Owner 1 -- 0..* Animal (a "has" relationship)
R=$(req POST "/d/${DIAGRAM}/associations" \
    '{"classA":"Owner","classB":"Animal","multA":"1","multB":"0..*","labelA":"owns","labelB":"pet"}')
assert_status "Owner --0..* Animal (association) -> 201" 201 "$R"

# Dependency: Dog depends on Owner
R=$(req POST "/d/${DIAGRAM}/dependencies" \
    '{"client":"Dog","supplier":"Owner"}')
assert_status "Dog -> Owner (dependency) -> 201" 201 "$R"

echo ""
echo "=== Step 6: verify classes + attributes + operations via /snapshot ==="
R=$(req GET "/project/diagrams/${DIAGRAM}/snapshot")
assert_status "GET /snapshot -> 200" 200 "$R"

# ProjectSnapshot.snapshot() includes only classes + associations
# (generalizations and dependencies are NOT serialized by
# ProjectSnapshot, a known limitation of the snapshot shape).
# We verify classes/attrs/ops from the snapshot, and verify the
# generalizations/dependencies via their dedicated list endpoints.
assert_contains "snapshot has Animal" '"name":"Animal"' "$R"
assert_contains "snapshot has Dog"    '"name":"Dog"'    "$R"
assert_contains "snapshot has Owner"  '"name":"Owner"'  "$R"
# attributes (ClassView encodes as "name:type")
assert_contains "snapshot has Animal.name:String" 'name:String' "$R"
assert_contains "snapshot has Dog.breed:String"    'breed:String' "$R"
# operations (ClassView encodes as "name(params):returnType")
assert_contains "snapshot has bark():void"         'bark():void'    "$R"
assert_contains "snapshot has getName():String"    'getName():String' "$R"
# associations are in the snapshot
assert_contains "snapshot has associations array"  '"associations":' "$R"

echo ""
echo "=== Step 7: verify inheritance + dependency via list endpoints ==="
# Inheritance
R=$(req GET "/d/${DIAGRAM}/generalizations")
assert_status "GET /d/.../generalizations -> 200" 200 "$R"
# ListGeneralizationsHandler returns {"child": "X", "parent": "Y"}
# (the pipe format "X|Y" is only for the DELETE endpoint's {id})
assert_contains "generalizations has child=Dog"     '"child":"Dog"'    "$R"
assert_contains "generalizations has parent=Animal" '"parent":"Animal"' "$R"

# Dependency
R=$(req GET "/d/${DIAGRAM}/dependencies")
assert_status "GET /d/.../dependencies -> 200" 200 "$R"
assert_contains "dependencies has Dog->Owner"     'Dog' "$R"

# Association
R=$(req GET "/d/${DIAGRAM}/associations")
assert_status "GET /d/.../associations -> 200" 200 "$R"
assert_contains "associations has multB=0..*"    '"multB":"0..*"' "$R"

echo ""
echo "=== Summary ==="
echo "Total: $((PASS+FAIL)), Passed: $PASS, Failed: $FAIL"
if [ "$FAIL" -gt 0 ]; then
    EXIT_CODE=1
    echo "RESULT: FAIL"
else
    EXIT_CODE=0
    echo "RESULT: ALL PASS"
fi
exit $EXIT_CODE
