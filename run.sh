#!/usr/bin/env bash
# Atalho para rodar o DuckTUI num terminal de verdade.
# (o modo inline precisa de um TTY; rodar pela IDE costuma falhar)
#
#   ./run.sh          -> menus por annotation (@Menu/@Submenu)
#   ./run.sh json     -> menus pelo arquivo menus.json
set -euo pipefail

cd "$(dirname "$0")"

MAIN="com.github.rhama.ducktui.DuckTuiApp"
if [ "${1:-}" = "json" ]; then
    MAIN="com.github.rhama.ducktui.DuckTuiJsonApp"
fi

# Usa o JDK 21 local, se o JAVA_HOME ainda nao estiver definido
if [ -z "${JAVA_HOME:-}" ]; then
    for dir in "$HOME/.local/opt"/jdk-21*; do
        if [ -x "$dir/bin/java" ]; then
            export JAVA_HOME="$dir"
            break
        fi
    done
fi

MVN="mvn"
if ! command -v mvn >/dev/null 2>&1; then
    for dir in "$HOME/.local/opt"/apache-maven-*; do
        if [ -x "$dir/bin/mvn" ]; then
            MVN="$dir/bin/mvn"
            break
        fi
    done
fi

exec "$MVN" -q compile exec:java -Dexec.mainClass="$MAIN"
