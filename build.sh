#!/bin/sh
# ===========================================================================
#  build.sh -- Acbric 快速编译入口 (Linux / macOS / WSL / Git Bash)
#
#  用法 / usage:
#    ./build.sh                  只编译 (assemble)，不跑测试
#    ./build.sh full             编译 + 全部回归测试 (等价 gradlew build)
#    ./build.sh clean            清理后重新编译
#    ./build.sh help             显示这段说明
#    ./build.sh <gradle 任务...>  其余参数原样转发给 gradlew
#
#  这个脚本只做两件事：定位 JDK 21、把命令拼给 gradlew。
#  构建逻辑全部在 build.gradle 里，所以 IDE、CI 和手敲 gradlew 的行为一致。
#
#  命名：刻意用 build.sh 而不是无扩展名的 build —— 后者在大小写不敏感的
#  文件系统上会和 Gradle 的 build/ 目录冲突。Windows 用同目录的 build.cmd。
# ===========================================================================
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$ROOT"

if [ ! -f ./gradlew ]; then
    echo "[build] gradlew not found. Run this from the Acbric repository." >&2
    exit 1
fi

# ---------------------------------------------------------------------------
#  定位 JDK 21：JAVA_HOME -> PATH 上的 java -> 常见安装目录
#  只认主版本号 >= 21，避免在 PATH 指向 Java 8 的机器上装死。
# ---------------------------------------------------------------------------
java_major() {
    "$1" -version 2>&1 | awk -F'"' '
        /version/ { split($2, v, "."); print (v[1] == 1 ? v[2] : v[1]); exit }
    '
}

accept() {
    [ -n "${1:-}" ] || return 1
    [ -x "$1/bin/java" ] || return 1
    maj=$(java_major "$1/bin/java" 2>/dev/null || true)
    [ -n "$maj" ] || return 1
    [ "$maj" -ge 21 ] 2>/dev/null || return 1
    printf '%s\n' "$1"
}

normalize_home() {
    # Git Bash / msys / cygwin hand over JAVA_HOME as a Windows path
    # (C:\Program Files\...), which sh cannot stat. Convert C:\x\y -> /c/x/y.
    case $1 in
        [A-Za-z]:[\\/]*)
            d=$(printf '%s' "$1" | cut -c1 | tr 'A-Z' 'a-z')
            rest=$(printf '%s' "$1" | cut -c3- | tr '\\' '/')
            printf '/%s/%s\n' "$d" "$rest"
            ;;
        *) printf '%s\n' "$1" ;;
    esac
}

home_of_java() {
    # 逐级解析符号链接（macOS 的 readlink 没有 -f），再取 bin 的上一层
    p=$1
    while [ -h "$p" ]; do
        d=$(dirname -- "$p")
        l=$(readlink -- "$p")
        case $l in
            /*) p=$l ;;
            *)  p=$d/$l ;;
        esac
    done
    (CDPATH= cd -- "$(dirname -- "$p")/.." && pwd)
}

JH=""
if [ -n "${JAVA_HOME:-}" ]; then
    JH=$(accept "$(normalize_home "$JAVA_HOME")" || true)
fi
if [ -z "$JH" ] && command -v java >/dev/null 2>&1; then
    JH=$(accept "$(home_of_java "$(command -v java)")" || true)
fi
if [ -z "$JH" ]; then
    for d in \
        /usr/lib/jvm/*21* /usr/lib/jvm/* \
        /usr/java/* /opt/java/* /opt/jdk* /opt/openjdk* \
        /Library/Java/JavaVirtualMachines/*/Contents/Home \
        /opt/homebrew/opt/openjdk* /usr/local/opt/openjdk* \
        "$HOME/.sdkman/candidates/java"/* "$HOME/.jdks"/* "$HOME/.gradle/jdks"/*
    do
        [ -d "$d" ] || continue
        JH=$(accept "$d" || true)
        [ -n "$JH" ] && break
    done
fi

if [ -z "$JH" ]; then
    cat >&2 <<'EOF'
[build] 找不到 JDK 21。/ no JDK 21 found.

  本工程要求 JDK 21（build.gradle 里 sourceCompatibility = 21）。请任选一种：
    1. 安装 JDK 21（Adoptium / Microsoft OpenJDK / Corretto / Homebrew openjdk@21 均可）
    2. 设置 JAVA_HOME 指向已有的 JDK 21 后重试：
         export JAVA_HOME=/path/to/jdk-21

  This project requires JDK 21. Install one, or export JAVA_HOME pointing at an
  existing JDK 21 installation, then run build again.
EOF
    exit 1
fi

JAVA_HOME=$JH
export JAVA_HOME

# ---------------------------------------------------------------------------
#  参数分发：识别 full / clean / help，其余原样透传。
#  用位置参数轮转而不是拼字符串，避免带空格的参数被拆开。
# ---------------------------------------------------------------------------
TASKS="assemble"
n=$#
i=0
while [ "$i" -lt "$n" ]; do
    arg=$1
    shift
    case $arg in
        help|-h|--help)
            cat <<'EOF'
  ./build.sh                  只编译 / compile only (assemble)
  ./build.sh full             编译 + 全部测试 / compile + all tests (build)
  ./build.sh clean            清理后编译 / clean then compile
  ./build.sh <gradle task...> 转发给 gradlew / pass through to gradlew

  测试用同目录的 test.sh：./test.sh all / ./test.sh <suite> / ./test.sh list
EOF
            exit 0
            ;;
        full)  TASKS="build" ;;
        clean) TASKS="clean assemble" ;;
        *)     set -- "$@" "$arg" ;;
    esac
    i=$((i + 1))
done

echo "[build] JDK 21  = $JAVA_HOME"
echo "[build] gradlew $TASKS $*"
exec ./gradlew $TASKS "$@"
