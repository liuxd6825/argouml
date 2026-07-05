#!/usr/bin/env bash
# tests/smoke/pre-smoke-cleanup.sh
# 跑 run-smoke-tests.sh 之前的状态清理:
#   - 列出所有 diagram, 删除 Test 图的所有 classes
#   - 强制 POST /project/diagrams {"name":"Test"} (201 新建 / 409 已存在)
#   - 不触碰系统包 (无标题模型) 也不删除 diagrams
#
# run-smoke-tests.sh:
#   - 测试 #1: POST /project/diagrams {"name":"SmokeDiagram"} 期望 201
#     如果上一次 run 留了 SmokeDiagram, 这个测试会失败 (409)
#   - 测试 #2: POST duplicate SmokeDiagram 期望 409
#   - 后续测试用 /d/Test/...
#
# 退出码 0
set -e
BIND=${BIND:-127.0.0.1}
PORT=${PORT:-18766}
BASE="http://${BIND}:${PORT}"

req() {
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
body_of() { echo "$1" | sed '$d'; }
status_of() { echo "$1" | tail -n1; }

echo "[pre-cleanup] 列出所有 diagram..."
R=$(req GET /project/diagrams)
BODY=$(body_of "$R")
echo "  $BODY"

# 用 python 解析 JSON
NAMES=$(echo "$BODY" | python3 -c "
import json,sys
d=json.load(sys.stdin)
items=d.get('data',d) if isinstance(d.get('data',d),list) else []
print('\n'.join(x['name'] for x in items))
" 2>/dev/null || echo "")

# 删除每张图的所有 classes
echo "$NAMES" | while IFS= read -r d; do
    [ -z "$d" ] && continue
    # URL encode the name
    ENC=$(python3 -c "import urllib.parse; print(urllib.parse.quote('$d'))")
    echo "[pre-cleanup] 清空 diagram: $d (enc=$ENC)"
    R=$(req GET "/d/${ENC}/classes")
    CLS=$(body_of "$R" | python3 -c "
import json,sys
d=json.load(sys.stdin)
items=d.get('data',d) if isinstance(d.get('data',d),list) else []
print('\n'.join(x['name'] for x in items))
" 2>/dev/null || echo "")
    echo "$CLS" | while IFS= read -r c; do
        [ -z "$c" ] && continue
        CENC=$(python3 -c "import urllib.parse; print(urllib.parse.quote('$c'))")
        R=$(req DELETE "/d/${ENC}/classes/${CENC}")
        CODE=$(status_of "$R")
        echo "    delete $d/$c -> $CODE"
    done
done

# 强制创建 Test 图
echo "[pre-cleanup] 强制创建 Test..."
R=$(req POST /project/diagrams '{"name":"Test"}')
echo "  POST Test -> $(status_of "$R")"

# 验证 Test 图存在且为空
sleep 1
R=$(req GET /d/Test/classes)
echo "[pre-cleanup] 验证 Test/classes -> $(body_of "$R")"

# 检查 SmokeDiagram 是否存在 (上次 run 留下)
R=$(req GET /project/diagrams)
HAS_SMOKE=$(echo "$(body_of "$R")" | python3 -c "
import json,sys
d=json.load(sys.stdin)
items=d.get('data',d) if isinstance(d.get('data',d),list) else []
print('yes' if any(x['name']=='SmokeDiagram' for x in items) else 'no')
" 2>/dev/null || echo "err")
echo "[pre-cleanup] SmokeDiagram 存在: $HAS_SMOKE"
if [ "$HAS_SMOKE" = "yes" ]; then
    echo "  警告: SmokeDiagram 已存在, run-smoke-tests.sh #1 会因 409 失败"
    echo "  建议: 这次测试会失败, 需要先关闭服务并删除 ~/.argouml/<profile>.zargo"
fi

# NOTE: 不预创建 SmokeDiagram, run-smoke-tests.sh 自己会 POST 它 (期望 201)
# 如果 pre-cleanup 也创建, run-smoke-tests.sh 会得到 409 冲突

echo "[pre-cleanup] 完成"