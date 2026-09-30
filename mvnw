#!/bin/sh
# Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.
set -e
MAVEN_PROJECTBASEDIR=${MAVEN_BASEDIR:-"$(cd "$(dirname "$0")" && pwd)"}
MVNW_VERBOSE=${MVNW_VERBOSE:-false}
WRAPPER_PROPS="$MAVEN_PROJECTBASEDIR/.mvn/wrapper/maven-wrapper.properties"
DIST_URL=$(sed -n 's/^distributionUrl=//p' "$WRAPPER_PROPS")
MAVEN_VERSION=$(printf '%s' "$DIST_URL" | sed -n 's|.*/apache-maven-\([^/]*\)-bin.zip|\1|p')
MAVEN_HOME="${MAVEN_USER_HOME:-$HOME/.m2}/wrapper/dists/apache-maven-$MAVEN_VERSION"
if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$MAVEN_HOME"
  TMP_ZIP="$MAVEN_HOME/maven.zip"
  if command -v curl >/dev/null 2>&1; then curl -fsSL "$DIST_URL" -o "$TMP_ZIP"; else wget -q "$DIST_URL" -O "$TMP_ZIP"; fi
  unzip -q -o "$TMP_ZIP" -d "$MAVEN_HOME.tmp"
  mv "$MAVEN_HOME.tmp"/apache-maven-*/* "$MAVEN_HOME"
  rm -rf "$MAVEN_HOME.tmp" "$TMP_ZIP"
fi
exec "$MAVEN_HOME/bin/mvn" "$@"

