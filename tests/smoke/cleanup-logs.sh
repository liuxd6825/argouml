#!/usr/bin/env bash
# tests/smoke/cleanup-logs.sh
# 清理 ArgoUML 测试遗留下的日志文件
#   - 找到 /tmp 下所有 argouml* / m3.log / gui.log / smoke*.log
#   - 保留每个文件最新 50MB（truncate -s 50M）
#   - 删除 > 500MB 的（保留太占空间）
#   - 报"已清理 X MB"
#
# 可被定期任务调用，或手动跑：
#   bash tests/smoke/cleanup-logs.sh
# 退出码 0
set -e

TOTAL_FREED=0
echo "[cleanup-logs] 启动 $(date)"

# 1) 删除 > 500MB 的（保留太占空间）
for f in /tmp/m3.log /tmp/gui.log /tmp/argouml-*.log /tmp/argouml-launcher/.server.log /tmp/argouml-launcher/.server.err /tmp/smoke-*.log; do
    [ -f "$f" ] || continue
    SIZE=$(stat -f%z "$f" 2>/dev/null || echo 0)
    if [ "$SIZE" -gt 524288000 ]; then
        FREED=$((SIZE / 1048576))
        TOTAL_FREED=$((TOTAL_FREED + FREED))
        rm -f "$f"
        echo "  deleted $f (${FREED}MB)"
    fi
done

# 2) 对其它 argouml* 文件 truncate 到 50MB（保留最新内容）
for f in /tmp/m3.log /tmp/gui.log /tmp/argouml-*.log /tmp/argouml-launcher/.server.log /tmp/argouml-launcher/.server.err; do
    [ -f "$f" ] || continue
    SIZE=$(stat -f%z "$f" 2>/dev/null || echo 0)
    if [ "$SIZE" -gt 52428800 ]; then
        FREED=$(((SIZE - 52428800) / 1048576))
        TOTAL_FREED=$((TOTAL_FREED + FREED))
        truncate -s 52428800 "$f"
        echo "  truncated $f (freed ${FREED}MB)"
    fi
done

CURRENT_SIZE=$(du -sm /tmp 2>/dev/null | awk '{print $1}')
echo "[cleanup-logs] 完成: 释放 ${TOTAL_FREED}MB, /tmp 当前 ${CURRENT_SIZE}MB"
