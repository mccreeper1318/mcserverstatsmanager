#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$PROJECT_DIR/src/main/java"
BUILD_DIR="$PROJECT_DIR/build"
CLASSES_DIR="$BUILD_DIR/classes/java/main"
MAIN_CLASS="com.pinnacle.mcstats.MinecraftServerStatsApp"
JAR_NAME="mc-server-stats-manager-0.1.0-alpha.jar"

compile() {
  mkdir -p "$CLASSES_DIR"
  find "$SRC_DIR" -name '*.java' > "$BUILD_DIR/sources.txt"
  javac --release 17 -d "$CLASSES_DIR" @"$BUILD_DIR/sources.txt"
}

make_jar() {
  compile
  mkdir -p "$BUILD_DIR/libs"
  (cd "$CLASSES_DIR" && jar --create --file "$BUILD_DIR/libs/$JAR_NAME" --main-class "$MAIN_CLASS" .)
  echo "Built $BUILD_DIR/libs/$JAR_NAME"
}

TASK="${1:-run}"
case "$TASK" in
  run)
    compile
    java -cp "$CLASSES_DIR" "$MAIN_CLASS"
    ;;
  jar|build|assemble)
    make_jar
    ;;
  clean)
    rm -rf "$BUILD_DIR"
    ;;
  *)
    echo "Supported tasks: run, jar, build, assemble, clean"
    exit 1
    ;;
esac
