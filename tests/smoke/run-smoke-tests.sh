#!/usr/bin/env bash
# tests/smoke/run-smoke-tests.sh
# 真实 HTTP 客户端 (curl) → 真实 argouml-ai HTTP server 端到端测试
# 覆盖所有 25 个路由 + 错误码 + 边界
# 退出码: 0 全 pass, 1 有 fail

set -e
BIND=${BIND:-127.0.0.1}
PORT=${PORT:-18766}
BASE="http://${BIND}:${PORT}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 计数器
PASS=0
FAIL=0
FAILURES=()

# ----- helpers -----
# 启动时清大 log（> 200MB），防止累积
for f in /tmp/m3.log /tmp/gui.log /tmp/argouml-*.log /tmp/argouml-launcher/.server.log /tmp/argouml-launcher/.server.err; do
    [ -f "$f" ] && [ "$(stat -f%z "$f" 2>/dev/null || echo 0)" -gt 209715200 ] && rm -f "$f"
done
START_SIZE=$(du -sm /tmp 2>/dev/null | awk '{print $1}')
echo "[smoke] /tmp 起始占用: ${START_SIZE}MB"

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

# Wrapper that uses request/response to handle unique diagrams per run
make_diagram() {  # $1=name → echoes the unique name
    local base="$1"
    local stamp
    stamp=$(date +%s%N)
    echo "${base}_${stamp}"
}

status_of() {  # $1=raw response (body+code) -> echoes last line (code)
    echo "$1" | tail -n1
}
body_of() {  # $1=raw response -> echoes all but last line (body)
    echo "$1" | sed '$d'
}

assert_status() {  # $1=label $2=expected $3=raw
    local label=$1 expected=$2 raw=$3
    local actual
    actual=$(status_of "$raw")
    local body
    body=$(body_of "$raw" | head -c 200)
    if [ "$actual" = "$expected" ]; then
        echo "  PASS  $label  [HTTP $actual]"
        PASS=$((PASS + 1))
    else
        echo "  FAIL  $label  [expected $expected got $actual]"
        echo "        body: $body"
        FAIL=$((FAIL + 1))
        FAILURES+=("$label")
    fi
}

assert_contains() {  # $1=label $2=needle $3=raw
    local label=$1 needle=$2 raw=$3
    local body
    body=$(body_of "$raw")
    if echo "$body" | grep -qF "$needle"; then
        echo "  PASS  $label  [contains \"$needle\"]"
        PASS=$((PASS + 1))
    else
        local actual
        actual=$(status_of "$raw")
        echo "  FAIL  $label  [no \"$needle\"; HTTP $actual]"
        echo "        body: $(echo "$body" | head -c 200)"
        FAIL=$((FAIL + 1))
        FAILURES+=("$label")
    fi
}

section() { echo ""; echo "=== $* ==="; }

# ----- 自动启停 server -----
STARTED_HERE=0
if ! curl -sf -m 2 "${BASE}/health" > /dev/null 2>&1; then
    echo "[smoke] Server not running, starting it..."
    PORT="${PORT}" BIND="${BIND}" "${SCRIPT_DIR}/start-server.sh" \
        || { echo "[smoke] FATAL: start failed" >&2; exit 1; }
    STARTED_HERE=1
fi

cleanup() {
    if [ "${STARTED_HERE}" = "1" ]; then
        echo ""
        echo "[smoke] Stopping server (started by this run)..."
        PORT="${PORT}" BIND="${BIND}" "${SCRIPT_DIR}/stop-server.sh" || true
    fi
    # 内存保护：清理本次运行产生的大 log / 临时文件
    for f in /tmp/m3.log /tmp/gui.log /tmp/argouml-launcher/.server.log /tmp/argouml-launcher/.server.err; do
        [ -f "$f" ] && [ "$(stat -f%z "$f" 2>/dev/null || echo 0)" -gt 524288000 ] && rm -f "$f"
    done
    # 报告最终内存
    local final_size=$(du -sm /tmp 2>/dev/null | awk '{print $1}')
    echo "[smoke] /tmp 占用: ${final_size}MB"
}
# Note: the final trap is set right before the test loop, after EXIT_CODE is
# declared; see below. This early declaration is only used as a fallback.

# ====================================================================
echo "Real-client smoke test for argouml-ai HTTP server"
echo "  BASE: ${BASE}"
echo "  Started: $(date)"
EXIT_CODE=0

trap 'cleanup; exit $EXIT_CODE' EXIT INT TERM

# ====================================================================
section "Diagram lifecycle (5 tests)"

# Create a new diagram
R=$(req POST /project/diagrams '{"name":"SmokeDiagram"}')
assert_status "POST /project/diagrams (SmokeDiagram) → 201" 201 "$R"
assert_contains "create response kind=class" '"kind":"class"' "$R"

# Duplicate (409)
R=$(req POST /project/diagrams '{"name":"SmokeDiagram"}')
assert_status "POST duplicate → 409" 409 "$R"
assert_contains "409 body has DUPLICATE_DIAGRAM" "DUPLICATE_DIAGRAM" "$R"

# Empty name (400)
R=$(req POST /project/diagrams '{"name":""}')
assert_status "POST empty name → 400" 400 "$R"
assert_contains "400 body has INVALID_NAME" "INVALID_NAME" "$R"

# Missing name field (400)
R=$(req POST /project/diagrams '{}')
assert_status "POST missing name field → 400" 400 "$R"
assert_contains "400 body has INVALID_BODY" "INVALID_BODY" "$R"

# List now contains the new diagram
R=$(req GET /project/diagrams)
assert_status "GET /project/diagrams → 200" 200 "$R"
assert_contains "list contains SmokeDiagram" '"name":"SmokeDiagram"' "$R"

# ====================================================================
section "Health & Routing (4 tests)"

R=$(req GET /health)
assert_status "GET /health → 200" 200 "$R"
assert_contains "GET /health body has ok:true" '"ok":true' "$R"

R=$(req GET /no-such-path)
assert_status "GET /no-such-path → 404" 404 "$R"
assert_contains "404 body has ROUTE_NOT_FOUND" "ROUTE_NOT_FOUND" "$R"

# ====================================================================
section "Class CRUD (8 tests)"

R=$(req POST /d/Test/classes '{"name":"Customer","x":100,"y":80}')
assert_status "POST /d/Test/classes (Customer) → 201" 201 "$R"
assert_contains "POST Customer body has name" '"name":"Customer"' "$R"

R=$(req GET /d/Test/classes)
assert_status "GET /d/Test/classes → 200" 200 "$R"
assert_contains "GET classes list contains Customer" '"name":"Customer"' "$R"

R=$(req GET /d/Test/classes/Customer)
assert_status "GET /d/Test/classes/Customer → 200" 200 "$R"
assert_contains "GET class body has Customer" '"name":"Customer"' "$R"

R=$(req PUT /d/Test/classes/Customer '{"newName":"CustomerRenamed"}')
assert_status "PUT rename Customer → 200" 200 "$R"

R=$(req GET /d/Test/classes/CustomerRenamed)
assert_status "GET renamed → 200" 200 "$R"

R=$(req POST /d/Test/classes '{"name":"","x":1,"y":1}')
assert_status "POST empty name → 400" 400 "$R"
assert_contains "400 body has INVALID_NAME" "INVALID_NAME" "$R"

R=$(req POST /d/Test/classes '{"name":"CustomerRenamed","x":1,"y":1}')
assert_status "POST duplicate → 409" 409 "$R"
assert_contains "409 body has DUPLICATE" "DUPLICATE" "$R"

# ====================================================================
section "Attribute CRUD (4 tests)"

R=$(req POST /d/Test/classes/CustomerRenamed/attributes '{"name":"id","type":"long","visibility":"private"}')
assert_status "POST attribute id → 201" 201 "$R"

R=$(req GET /d/Test/classes/CustomerRenamed/attributes)
assert_status "GET attributes → 200" 200 "$R"
assert_contains "attributes list has id" '"name":"id"' "$R"

R=$(req POST /d/Test/classes/CustomerRenamed/attributes '{"name":""}')
assert_status "POST attribute empty name → 400" 400 "$R"

R=$(req DELETE /d/Test/classes/CustomerRenamed/attributes/id)
assert_status "DELETE attribute id → 204" 204 "$R"

# ====================================================================
section "Operation CRUD (4 tests)"

R=$(req POST /d/Test/classes/CustomerRenamed/operations '{"name":"save","returnType":"void","visibility":"public"}')
assert_status "POST operation save → 201" 201 "$R"

R=$(req GET /d/Test/classes/CustomerRenamed/operations)
assert_status "GET operations → 200" 200 "$R"
assert_contains "operations list has save" '"save"' "$R"

R=$(req POST /d/Test/classes/CustomerRenamed/operations '{"name":""}')
assert_status "POST op empty name → 400" 400 "$R"

R=$(req DELETE /d/Test/classes/CustomerRenamed/operations/save)
assert_status "DELETE operation save → 204" 204 "$R"

# ====================================================================
section "Relationship CRUD (6 tests)"

# Add second class for relationship endpoints
R=$(req POST /d/Test/classes '{"name":"Order","x":300,"y":80}')
assert_status "POST Order → 201" 201 "$R"

R=$(req POST /d/Test/classes/Order/attributes '{"name":"total","type":"double"}')
assert_status "POST Order.total → 201" 201 "$R"

R=$(req POST /d/Test/associations '{"classA":"CustomerRenamed","classB":"Order","multA":"1","multB":"0..*","labelA":"places","labelB":"placedBy"}')
assert_status "POST association → 201" 201 "$R"

R=$(req GET /d/Test/associations)
assert_status "GET associations → 200" 200 "$R"
assert_contains "associations has multA=1" '"multA":"1"' "$R"

R=$(req POST /d/Test/associations '{"classA":"NoSuchClass","classB":"Order"}')
assert_status "POST assoc with missing classA → 404" 404 "$R"
assert_contains "404 body has CLASS_NOT_FOUND" "CLASS_NOT_FOUND" "$R"

R=$(req POST /d/Test/generalizations '{"subclass":"Order","superclass":"CustomerRenamed"}')
assert_status "POST generalization Order→CustomerRenamed → 201" 201 "$R"

R=$(req GET /d/Test/generalizations)
assert_status "GET generalizations → 200" 200 "$R"

R=$(req POST /d/Test/dependencies '{"client":"Order","supplier":"CustomerRenamed"}')
assert_status "POST dependency Order→CustomerRenamed → 201" 201 "$R"

R=$(req DELETE '/d/Test/relationships/Order%7CCustomerRenamed?type=dependency')
assert_status "DELETE relationship id=Order|CustomerRenamed → 204" 204 "$R"

# ====================================================================
section "Snapshot (2 tests)"

R=$(req GET /project/diagrams/Test/snapshot)
assert_status "GET snapshot → 200" 200 "$R"
assert_contains "snapshot has diagram key" '"diagram":' "$R"
assert_contains "snapshot has classes key" '"classes":' "$R"
assert_contains "snapshot has associations key" '"associations":' "$R"

R=$(req GET /project/diagrams/NoSuch/snapshot)
assert_status "GET snapshot of unknown diagram → 404" 404 "$R"

# ====================================================================
section "Error envelopes (4 tests)"

R=$(req POST /d/Test/classes '{not valid json')
assert_status "Malformed JSON body → 400" 400 "$R"
assert_contains "malformed body has INVALID_BODY" "INVALID_BODY" "$R"

R=$(req GET /d/no-such-diagram/classes)
assert_status "GET classes of unknown diagram → 404" 404 "$R"
assert_contains "404 body has DIAGRAM_NOT_FOUND" "DIAGRAM_NOT_FOUND" "$R"

R=$(req GET /d/Test/classes/NoSuchClass)
assert_status "GET unknown class → 404" 404 "$R"

R=$(req DELETE /d/Test/classes/CustomerRenamed)
assert_status "DELETE class CustomerRenamed → 204" 204 "$R"

# ====================================================================
echo ""
echo "=== Package CRUD (20 tests) ==="

# Unique names per run to avoid re-run conflicts
TS=$(date +%s%N)
PKG_ROOT="domain_${TS}"
PKG_NESTED="model_${TS}"
PKG_MOVED_CLASS="PkgClass_${TS}"

# Create a throwaway class in Test diagram (to move between packages)
R=$(req POST "/d/Test/classes" "{\"name\":\"${PKG_MOVED_CLASS}\"}")
assert_status "POST ${PKG_MOVED_CLASS} class → 201" 201 "$R"

# 1. Create root package
R=$(req POST /project/packages "{\"name\":\"${PKG_ROOT}\"}")
assert_status "POST root pkg ${PKG_ROOT} → 201" 201 "$R"
assert_contains "root pkg has name" "\"name\":\"${PKG_ROOT}\"" "$R"

# 2. Create nested package (parent = root)
R=$(req POST /project/packages "{\"name\":\"${PKG_NESTED}\",\"parent\":\"${PKG_ROOT}\"}")
assert_status "POST nested pkg ${PKG_NESTED} → 201" 201 "$R"
assert_contains "nested pkg qualifiedName" "\"qualifiedName\":\"${PKG_ROOT}/${PKG_NESTED}\"" "$R"

# 3. Duplicate name → 409
R=$(req POST /project/packages "{\"name\":\"${PKG_ROOT}\"}")
assert_status "POST duplicate → 409" 409 "$R"
assert_contains "409 DUPLICATE_PACKAGE" "DUPLICATE_PACKAGE" "$R"

# 4. Missing parent → 400
R=$(req POST /project/packages "{\"name\":\"x\",\"parent\":\"nonexistent\"}")
assert_status "POST bad parent → 400" 400 "$R"
assert_contains "400 invalid name code" "INVALID_NAME" "$R"

# 5. Empty name → 400
R=$(req POST /project/packages "{\"name\":\"\"}")
assert_status "POST empty name → 400" 400 "$R"

# 6. List packages
R=$(req GET /project/packages)
assert_status "GET /project/packages → 200" 200 "$R"
assert_contains "list contains root" "\"name\":\"${PKG_ROOT}\"" "$R"
assert_contains "list contains nested" "\"name\":\"${PKG_NESTED}\"" "$R"
assert_contains "list has qualifiedName field" "\"qualifiedName\"" "$R"
assert_contains "list has classCount field" "\"classCount\"" "$R"

# 7. Get single root package
R=$(req GET /project/packages/${PKG_ROOT})
assert_status "GET root pkg → 200" 200 "$R"
assert_contains "root qualifiedName" "\"qualifiedName\":\"${PKG_ROOT}\"" "$R"
assert_contains "root has parent field" "\"parent\"" "$R"
assert_contains "root classCount = 0" "\"classCount\":0" "$R"

# 8. Get nested package (qualifiedName is full path)
R=$(req GET /project/packages/${PKG_NESTED})
assert_status "GET nested pkg → 200" 200 "$R"
assert_contains "nested full path" "\"qualifiedName\":\"${PKG_ROOT}/${PKG_NESTED}\"" "$R"

# 9. Get non-existent → 404
R=$(req GET /project/packages/nonexistent_${TS})
assert_status "GET missing → 404" 404 "$R"
assert_contains "404 PACKAGE_NOT_FOUND" "PACKAGE_NOT_FOUND" "$R"

# 10. Move class into nested package
R=$(req POST /project/packages/${PKG_NESTED}/classes/${PKG_MOVED_CLASS})
assert_status "POST move class → 200" 200 "$R"

# 11. List classes in nested package
R=$(req GET /project/packages/${PKG_NESTED}/classes)
assert_status "GET nested pkg classes → 200" 200 "$R"
assert_contains "nested pkg has moved class" "\"name\":\"${PKG_MOVED_CLASS}\"" "$R"

# 12. Delete non-empty package → 409
R=$(req DELETE /project/packages/${PKG_NESTED})
assert_status "DELETE non-empty → 409" 409 "$R"
assert_contains "409 PACKAGE_NOT_EMPTY" "PACKAGE_NOT_EMPTY" "$R"

# 13. Try to delete root (has nested package) → 409
R=$(req DELETE /project/packages/${PKG_ROOT})
assert_status "DELETE root with nested → 409" 409 "$R"

# 14. Cleanup empty helper package
R=$(req POST /project/packages "{\"name\":\"empty_${TS}\"}")
assert_status "POST cleanup empty pkg → 201" 201 "$R"
R=$(req DELETE /project/packages/empty_${TS})
assert_status "DELETE cleanup empty → 204" 204 "$R"

# 15. Test that list still contains our root+nested (cleanup is isolated)
R=$(req GET /project/packages)
assert_status "GET list after cleanup → 200" 200 "$R"
assert_contains "list still has root" "\"name\":\"${PKG_ROOT}\"" "$R"

# ====================================================================
echo ""
echo "=== Summary ==="
echo "  Passed: $PASS"
echo "  Failed: $FAIL"
if [ "$FAIL" -gt 0 ]; then
    echo "  Failures:"
    for f in "${FAILURES[@]}"; do
        echo "    - $f"
    done
    echo ""
    echo "RESULT: FAIL"
    EXIT_CODE=1
else
    echo ""
    echo "RESULT: ALL PASS"
    EXIT_CODE=0
fi
exit $EXIT_CODE
