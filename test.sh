#!/bin/sh
# ===========================================================================
#  test.sh -- Acbric 快速测试入口 (Linux / macOS / WSL / Git Bash)
#
#  用法 / usage:
#    ./test.sh                 跑全部套件 (等同 test all)
#    ./test.sh all             跑全部套件
#    ./test.sh <suite>...      只跑指定套件；支持唯一前缀 (ev -> event)
#    ./test.sh list            列出所有套件及其覆盖范围
#    ./test.sh help            显示这段说明
#
#  本脚本只是 build.sh 的薄包装：套件选择经环境变量 ACBRIC_SUITES 传递，
#  JDK 定位与 Gradle 转发都由 build.sh 负责，不在这里重复实现。
#  以环境变量而不是 -Pacbric.suites= 传递，是为了让 test.cmd / test.sh 两端写法完全一致
#  （cmd.exe 会把参数里的 '=' 当分隔符拆开）。Gradle 侧两种写法都支持。
#
#  注意：本脚本不改变 CI 的行为 —— check 与 gradlew build 永远跑全部套件。
# ===========================================================================
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BUILD="$ROOT/build.sh"

if [ ! -f "$BUILD" ]; then
    echo "[test] build.sh not found next to test.sh." >&2
    exit 1
fi

case "${1:-}" in
    help|-h|--help)
        cat <<'EOF'
  ./test.sh                 跑全部套件 / run every suite
  ./test.sh all             同上 / same as above
  ./test.sh <suite>...      只跑指定套件 / run selected suites (unique prefix works: ev -> event)
  ./test.sh list            列出套件 / list available suites

  套件 / suites: data bundle classpath event rename mods
  CI 与 gradlew build 始终跑全部套件，不受这里影响。
EOF
        exit 0
        ;;
esac

SEL=""
RUN_ALL=""
for a in "$@"; do
    case $a in
        all) RUN_ALL=1 ;;
        *)   if [ -z "$SEL" ]; then SEL="$a"; else SEL="$SEL,$a"; fi ;;
    esac
done

if [ -n "$RUN_ALL" ] || [ -z "$SEL" ]; then
    echo "[test] running all regression suites"
    ACBRIC_SUITES=""
    export ACBRIC_SUITES
    exec "$BUILD" regressionTest
fi

echo "[test] suites: $SEL"
ACBRIC_SUITES="$SEL"
export ACBRIC_SUITES
exec "$BUILD" regressionTest
