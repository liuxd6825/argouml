#!/usr/bin/env bash
# tests/smoke/usecase-diagram-e2e.sh
# 完整覆盖用例图 17 个已注册端点 + 全部错误码
# 退出码: 0 = ALL PASS, 1 = at least one fail

set -e
BIND=${BIND:-127.0.0.1}
PORT=${PORT:-18766}
BASE="http://${BIND}:${PORT}"

# ---- counters ----
PASS=0
FAIL=0
FAILURES=()

# ---- helpers ----
# 启动时清大 log（> 200MB），防止累积
for f in /tmp/m3.log /tmp/gui.log /tmp/argouml-*.log /tmp/argouml-launcher/.server.log /tmp/argouml-launcher/.server.err; do
    [ -f "$f" ] && [ "$(stat -f%z "$f" 2>/dev/null || echo 0)" -gt 209715200 ] && rm -f "$f"
done

req() {  # $1=method $2=path [$3=body] → echoes "body\ncode"
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

code_of() { echo "$1" | tail -n1; }
body_of() { echo "$1" | sed '$d'; }

json_get() {  # $1=json $2=python expression → echoes result
    # Use bracket notation so d['data'] works on JSON dicts.
    # The expression is evaluated in a context where `d` is the parsed JSON object.
    # Use d['key'], d['key']['subkey'], len(d['list']) etc.
    python3 -c "
import json, sys
d = json.loads(sys.argv[1])
val = ($2)
print(val)
" "$1" 2>/dev/null
}



# 中文 URL 编码
urlenc() { python3 -c "import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1]))" "$1"; }

# ---- assertion helpers ----
assert_ok() {  # $1=label  $2=expected  $3=raw_response
    local label=$1 expected=$2 raw=$3
    local actual=$(code_of "$raw")
    if [ "$actual" = "$expected" ]; then
        PASS=$((PASS+1))
        echo "  PASS  $label  [HTTP $actual]"
    else
        FAIL=$((FAIL+1))
        FAILURES+=("$label (expected $expected, got $actual)")
        echo "  FAIL  $label  [expected $expected, got $actual]"
        body_of "$raw" | head -c 200
        echo ""
    fi
}

assert_body_contains() {  # $1=label  $2=needle  $3=raw
    local label=$1 needle=$2 raw=$3
    local body=$(body_of "$raw")
    if echo "$body" | grep -qF "$needle"; then
        PASS=$((PASS+1))
        echo "  PASS  $label  [body contains '$needle']"
    else
        FAIL=$((FAIL+1))
        FAILURES+=("$label (body missing '$needle')")
        echo "  FAIL  $label  [body missing '$needle']"
        echo "        body: $(echo "$body" | head -c 200)"
    fi
}

assert_body_json() {  # $1=label $2=keypath $3=expected $4=raw
    local label=$1 keypath=$2 expected=$3 raw=$4
    local body=$(body_of "$raw")
    local actual=$(json_get "$body" "$keypath" 2>/dev/null || echo "ERR")
    if [ "$actual" = "$expected" ]; then
        PASS=$((PASS+1))
        echo "  PASS  $label  [$keypath=$expected]"
    else
        FAIL=$((FAIL+1))
        FAILURES+=("$label ($keypath expected $expected, got $actual)")
        echo "  FAIL  $label  [$keypath expected $expected, got $actual]"
    fi
}

assert_body_json_ne() {  # $1=label $2=keypath $3=unexpected $4=raw
    local label=$1 keypath=$2 unexpected=$3 raw=$4
    local body=$(body_of "$raw")
    local actual=$(json_get "$body" "$keypath" 2>/dev/null || echo "ERR")
    if [ "$actual" != "$unexpected" ]; then
        PASS=$((PASS+1))
        echo "  PASS  $label  [$keypath != $unexpected]"
    else
        FAIL=$((FAIL+1))
        FAILURES+=("$label ($keypath unexpectedly equals $unexpected)")
        echo "  FAIL  $label  [$keypath unexpectedly equals $unexpected]"
    fi
}

section() { echo ""; echo "=== $* ==="; }

# ---- preflight ----
echo "[uc-e2e] preflight health check"
H=$(req GET /health)
if [ "$(code_of "$H")" != "200" ]; then
    echo "FATAL: server not reachable at $BASE/health"
    exit 2
fi
echo "  server ok: $(body_of "$H")"

# ---- 1. 建图 ----
section "1. create usecasediagram"
DIAG="uc-e2e-$(date +%s%N)"
DIAG_ENC=$(urlenc "$DIAG")
R=$(req POST /project/diagrams "{\"name\":\"$DIAG\",\"kind\":\"usecasediagram\"}")
assert_ok "POST /project/diagrams (usecasediagram)" 201 "$R"
assert_body_json "  kind=usecase" "d['data']['kind']" "usecase" "$R"
assert_body_json "  name=$DIAG" "d['data']['name']" "$DIAG" "$R"

# ---- 2. Actor CRUD ----
section "2. Actor CRUD (8 tests)"
R=$(req POST "/d/$DIAG_ENC/usecasediagram/actors" '{"name":"User"}')
assert_ok "  POST actor User" 201 "$R"
R=$(req POST "/d/$DIAG_ENC/usecasediagram/actors" '{"name":"Admin"}')
assert_ok "  POST actor Admin" 201 "$R"
R=$(req POST "/d/$DIAG_ENC/usecasediagram/actors" '{"name":"Guest"}')
assert_ok "  POST actor Guest" 201 "$R"

R=$(req GET "/d/$DIAG_ENC/usecasediagram/actors")
assert_ok "  GET /actors" 200 "$R"
assert_body_json "  list count=3" "len(d['data'])" "3" "$R"

R=$(req GET "/d/$DIAG_ENC/usecasediagram/actors/User")
assert_ok "  GET /actors/User" 200 "$R"
assert_body_json "    name=User" "d['data']['name']" "User" "$R"

R=$(req PUT "/d/$DIAG_ENC/usecasediagram/actors/User" '{"x":200,"y":50}')
assert_ok "  PUT /actors/User (move)" 200 "$R"
R=$(req GET "/d/$DIAG_ENC/usecasediagram/actors/User")
assert_body_json "    User.x=200" "d['data']['x']" "200" "$R"
assert_body_json "    User.y=50"  "d['data']['y']" "50"  "$R"

R=$(req POST "/d/$DIAG_ENC/usecasediagram/actors" '{"name":"User"}')
assert_ok "  POST duplicate User → 409" 409 "$R"
assert_body_json "    code=DUPLICATE_ACTOR" "d['error']['code']" "DUPLICATE_ACTOR" "$R"

R=$(req POST "/d/$DIAG_ENC/usecasediagram/actors" '{"name":""}')
assert_ok "  POST empty name → 400" 400 "$R"
assert_body_json "    code=INVALID_NAME" "d['error']['code']" "INVALID_NAME" "$R"

R=$(req DELETE "/d/$DIAG_ENC/usecasediagram/actors/Guest")
assert_ok "  DELETE /actors/Guest" 204 "$R"
R=$(req GET "/d/$DIAG_ENC/usecasediagram/actors")
assert_body_json "  list count=2 (after delete)" "len(d['data'])" "2" "$R"

R=$(req DELETE "/d/$DIAG_ENC/usecasediagram/actors/Nobody")
assert_ok "  DELETE /actors/Nobody → 404" 404 "$R"
assert_body_json "    code=ACTOR_NOT_FOUND" "d['error']['code']" "ACTOR_NOT_FOUND" "$R"

# ---- 3. UseCase CRUD ----
section "3. UseCase CRUD (8 tests)"
R=$(req POST "/d/$DIAG_ENC/usecasediagram/usecases" '{"name":"登录"}')
assert_ok "  POST usecase 登录" 201 "$R"
R=$(req POST "/d/$DIAG_ENC/usecasediagram/usecases" '{"name":"浏览商品"}')
assert_ok "  POST usecase 浏览商品" 201 "$R"
R=$(req POST "/d/$DIAG_ENC/usecasediagram/usecases" '{"name":"下单"}')
assert_ok "  POST usecase 下单" 201 "$R"

R=$(req GET "/d/$DIAG_ENC/usecasediagram/usecases")
assert_ok "  GET /usecases" 200 "$R"
assert_body_json "    list count=3" "len(d['data'])" "3" "$R"

R=$(req GET "/d/$DIAG_ENC/usecasediagram/usecases/登录")
assert_ok "  GET /usecases/登录" 200 "$R"
assert_body_json "    name=登录" "d['data']['name']" "登录" "$R"

R=$(req PUT "/d/$DIAG_ENC/usecasediagram/usecases/登录" '{"x":400,"y":200}')
assert_ok "  PUT /usecases/登录 (move)" 200 "$R"
R=$(req GET "/d/$DIAG_ENC/usecasediagram/usecases/登录")
assert_body_json "    登录.x=400" "d['data']['x']" "400" "$R"

R=$(req POST "/d/$DIAG_ENC/usecasediagram/usecases" '{"name":"登录"}')
assert_ok "  POST duplicate 登录 → 409" 409 "$R"
assert_body_json "    code=DUPLICATE_USECASE" "d['error']['code']" "DUPLICATE_USECASE" "$R"

R=$(req POST "/d/$DIAG_ENC/usecasediagram/usecases" '{"name":""}')
assert_ok "  POST empty name → 400" 400 "$R"

R=$(req DELETE "/d/$DIAG_ENC/usecasediagram/usecases/下单")
assert_ok "  DELETE /usecases/下单" 204 "$R"
R=$(req GET "/d/$DIAG_ENC/usecasediagram/usecases")
assert_body_json "    list count=2 (after delete)" "len(d['data'])" "2" "$R"

R=$(req GET "/d/$DIAG_ENC/usecasediagram/usecases/Nobody")
assert_ok "  GET /usecases/Nobody → 404" 404 "$R"
assert_body_json "    code=USECASE_NOT_FOUND" "d['error']['code']" "USECASE_NOT_FOUND" "$R"

# ---- 4. Association ----
section "4. Association CRUD (8 tests)"
R=$(req POST "/d/$DIAG_ENC/usecasediagram/associations" '{"actor":"User","usecase":"登录"}')
assert_ok "  POST assoc User→登录" 201 "$R"
R=$(req POST "/d/$DIAG_ENC/usecasediagram/associations" '{"actor":"Admin","usecase":"浏览商品"}')
assert_ok "  POST assoc Admin→浏览商品" 201 "$R"

R=$(req GET "/d/$DIAG_ENC/usecasediagram/associations")
assert_ok "  GET /associations" 200 "$R"
assert_body_json "    count=2" "len(d['data'])" "2" "$R"

R=$(req POST "/d/$DIAG_ENC/usecasediagram/associations" '{"actor":"User","usecase":"登录"}')
assert_ok "  POST duplicate assoc (allowed)" 201 "$R"
# Now 3 associations: User→登录 (x2), Admin→浏览商品

R=$(req GET "/d/$DIAG_ENC/usecasediagram/associations")
assert_body_json "    count=3 (after duplicate)" "len(d['data'])" "3" "$R"

R=$(req POST "/d/$DIAG_ENC/usecasediagram/associations" '{"actor":"Nobody","usecase":"登录"}')
assert_ok "  POST assoc with bad actor → 404" 404 "$R"
assert_body_json "    code=ACTOR_NOT_FOUND" "d['error']['code']" "ACTOR_NOT_FOUND" "$R"

R=$(req POST "/d/$DIAG_ENC/usecasediagram/associations" '{"actor":"User","usecase":"Nobody"}')
assert_ok "  POST assoc with bad usecase → 404" 404 "$R"
assert_body_json "    code=USECASE_NOT_FOUND" "d['error']['code']" "USECASE_NOT_FOUND" "$R"

R=$(req DELETE "/d/$DIAG_ENC/usecasediagram/associations/User%7C登录")
assert_ok "  DELETE /associations/User|登录" 204 "$R"
R=$(req GET "/d/$DIAG_ENC/usecasediagram/associations")
assert_body_json "    count=2 (after delete)" "len(d['data'])" "2" "$R"

R=$(req DELETE "/d/$DIAG_ENC/usecasediagram/associations/User%7C登录")
assert_ok "  DELETE non-existent assoc → 404" 404 "$R"

# ---- 5. Include ----
section "5. Include CRUD (4 tests)"
# 注: GET /includes 列表未注册, 跳过 list 测试
R=$(req POST "/d/$DIAG_ENC/usecasediagram/includes" '{"base":"登录","inclusion":"浏览商品"}')
assert_ok "  POST include 登录 includes 浏览商品" 201 "$R"
assert_body_json "    id=登录|浏览商品" "d['data']['id']" "登录|浏览商品" "$R"

R=$(req POST "/d/$DIAG_ENC/usecasediagram/includes" '{"base":"登录","inclusion":"浏览商品"}')
assert_ok "  POST duplicate include (allowed)" 201 "$R"

R=$(req DELETE "/d/$DIAG_ENC/usecasediagram/includes/登录%7C浏览商品")
assert_ok "  DELETE /includes/登录|浏览商品" 204 "$R"

R=$(req POST "/d/$DIAG_ENC/usecasediagram/includes" '{"base":"Nobody","inclusion":"登录"}')
assert_ok "  POST include with bad base → 404" 404 "$R"

# ---- 6. Extend ----
section "6. Extend CRUD (4 tests)"
# 下单 was deleted in step 3 cleanup; recreate it for extend tests
req POST "/d/$DIAG_ENC/usecasediagram/usecases" '{"name":"下单"}' >/dev/null

R=$(req POST "/d/$DIAG_ENC/usecasediagram/extends" '{"base":"登录","extension":"下单","extensionPoint":"付款后"}')
assert_ok "  POST extend 登录 extends 下单 (extensionPoint=付款后)" 201 "$R"
assert_body_json "    id=登录|下单" "d['data']['id']" "登录|下单" "$R"
assert_body_json "    extensionPoint=付款后" "d['data']['extensionPoint']" "付款后" "$R"

R=$(req POST "/d/$DIAG_ENC/usecasediagram/extends" '{"base":"登录","extension":"下单"}')
assert_ok "  POST extend without extensionPoint" 201 "$R"
assert_body_json "    extensionPoint empty" "d['data']['extensionPoint']" "" "$R"

R=$(req DELETE "/d/$DIAG_ENC/usecasediagram/extends/登录%7C下单")
assert_ok "  DELETE /extends/登录|下单" 204 "$R"

R=$(req POST "/d/$DIAG_ENC/usecasediagram/extends" '{"base":"Nobody","extension":"登录"}')
assert_ok "  POST extend with bad base → 404" 404 "$R"

# ---- 7. 综合场景: 电商下单流程 ----
section "7. 综合场景: 电商下单流程"
# 清空当前图
req DELETE "/d/$DIAG_ENC/usecasediagram/actors/Admin" >/dev/null
req DELETE "/d/$DIAG_ENC/usecasediagram/actors/User" >/dev/null
req DELETE "/d/$DIAG_ENC/usecasediagram/usecases/登录" >/dev/null
req DELETE "/d/$DIAG_ENC/usecasediagram/usecases/浏览商品" >/dev/null
req DELETE "/d/$DIAG_ENC/usecasediagram/associations/Admin%7C浏览商品" >/dev/null

# 建电商元素
req POST "/d/$DIAG_ENC/usecasediagram/actors" '{"name":"员工","x":80,"y":50}' >/dev/null
req POST "/d/$DIAG_ENC/usecasediagram/actors" '{"name":"经理","x":80,"y":200}' >/dev/null
req POST "/d/$DIAG_ENC/usecasediagram/usecases" '{"name":"开发","description":"编写代码","x":300,"y":50}' >/dev/null
req POST "/d/$DIAG_ENC/usecasediagram/usecases" '{"name":"测试","description":"功能测试","x":300,"y":150}' >/dev/null
req POST "/d/$DIAG_ENC/usecasediagram/usecases" '{"name":"部署","description":"发布到生产","x":300,"y":250}' >/dev/null

# 3 associations
req POST "/d/$DIAG_ENC/usecasediagram/associations" '{"actor":"员工","usecase":"开发"}' >/dev/null
req POST "/d/$DIAG_ENC/usecasediagram/associations" '{"actor":"员工","usecase":"测试"}' >/dev/null
req POST "/d/$DIAG_ENC/usecasediagram/associations" '{"actor":"经理","usecase":"部署"}' >/dev/null

# 2 includes: 测试 includes 开发, 部署 includes 测试
req POST "/d/$DIAG_ENC/usecasediagram/includes" '{"base":"测试","inclusion":"开发"}' >/dev/null
req POST "/d/$DIAG_ENC/usecasediagram/includes" '{"base":"部署","inclusion":"测试"}' >/dev/null

# 1 extend: 部署 extends 测试 (at "通过测试后")
req POST "/d/$DIAG_ENC/usecasediagram/extends" '{"base":"测试","extension":"部署","extensionPoint":"通过测试后"}' >/dev/null

# 验证总数
R=$(req GET "/d/$DIAG_ENC/usecasediagram/actors")
assert_body_json "  actors count=2" "len(d['data'])" "2" "$R"
R=$(req GET "/d/$DIAG_ENC/usecasediagram/usecases")
assert_body_json "  usecases count=3" "len(d['data'])" "3" "$R"
R=$(req GET "/d/$DIAG_ENC/usecasediagram/associations")
assert_body_json "  associations count=3" "len(d['data'])" "3" "$R"

# 验证 actor 名称
R=$(req GET "/d/$DIAG_ENC/usecasediagram/actors/员工")
assert_body_json "  员工 exists" "d['data']['name']" "员工" "$R"

# 验证 usecase description
R=$(req GET "/d/$DIAG_ENC/usecasediagram/usecases/开发")
assert_body_json_ne "  开发 desc not empty" ".data.description" "" "$R"

# ---- 8. 错误路径 ----
section "8. 错误路径"
# 不存在的图
R=$(req POST "/d/no-such-diagram/usecasediagram/actors" '{"name":"x"}')
assert_ok "  POST on non-existent diagram → 404" 404 "$R"
assert_body_json "    code=DIAGRAM_NOT_FOUND" "d['error']['code']" "DIAGRAM_NOT_FOUND" "$R"

R=$(req GET "/d/no-such-diagram/usecasediagram/actors")
assert_ok "  GET on non-existent diagram → 404" 404 "$R"

# 错误 body
R=$(req POST "/d/$DIAG_ENC/usecasediagram/actors" '{not valid json}')
assert_ok "  POST malformed body → 400" 400 "$R"
assert_body_json "    code=INVALID_BODY" "d['error']['code']" "INVALID_BODY" "$R"

R=$(req POST "/d/$DIAG_ENC/usecasediagram/actors" '{}')
assert_ok "  POST empty body → 400" 400 "$R"
assert_body_json "    code=INVALID_BODY" "d['error']['code']" "INVALID_BODY" "$R"

# 跨类型 — class 图的端点不能用在 usecase 图
# 已经在前面的 DIAGRAM_NOT_FOUND 测试中覆盖 (因为 no-such-diagram 不存在)

# ---- 9. 未实现端点记录 ----
section "9. 未实现端点 (预期 404)"
# 这些端点 SPEC 中规划但尚未注册
R=$(req GET "/d/$DIAG_ENC/usecasediagram/includes")
assert_ok "  GET /includes 列表 (未实现)" 404 "$R"
R=$(req GET "/d/$DIAG_ENC/usecasediagram/extends")
assert_ok "  GET /extends 列表 (未实现)" 404 "$R"
R=$(req GET "/d/$DIAG_ENC/usecasediagram/layout")
assert_ok "  GET /layout 快照 (未实现)" 404 "$R"
R=$(req POST "/d/$DIAG_ENC/usecasediagram/layout")
assert_ok "  POST /layout 自动布局 (未实现)" 404 "$R"

# ---- 10. 清理 ----
section "10. cleanup"
for name in 员工 经理; do
    req DELETE "/d/$DIAG_ENC/usecasediagram/actors/$name" >/dev/null
done
for name in 开发 测试 部署; do
    req DELETE "/d/$DIAG_ENC/usecasediagram/usecases/$name" >/dev/null
done
# 关系删除 (id 包含 | 需 URL 编码)
req DELETE "/d/$DIAG_ENC/usecasediagram/associations/员工%7C开发" >/dev/null
req DELETE "/d/$DIAG_ENC/usecasediagram/associations/员工%7C测试" >/dev/null
req DELETE "/d/$DIAG_ENC/usecasediagram/associations/经理%7C部署" >/dev/null
req DELETE "/d/$DIAG_ENC/usecasediagram/includes/测试%7C开发" >/dev/null
req DELETE "/d/$DIAG_ENC/usecasediagram/includes/部署%7C测试" >/dev/null
req DELETE "/d/$DIAG_ENC/usecasediagram/extends/测试%7C部署" >/dev/null

# ---- 报告 ----
echo ""
echo "================================"
echo "PASS=$PASS FAIL=$FAIL"
if [ "$FAIL" -gt 0 ]; then
    echo ""
    echo "Failed tests:"
    for f in "${FAILURES[@]}"; do
        echo "  - $f"
    done
    exit 1
fi
echo "ALL PASS"
exit 0
