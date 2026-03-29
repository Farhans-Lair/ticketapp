#!/bin/sh
# Maven Wrapper script — downloads Maven 3.9.6 automatically on first run.
# Usage: ./mvnw package   (instead of: mvn package)
set -e

MAVEN_VERSION="3.9.6"
MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-$MAVEN_VERSION"
MAVEN_ZIP_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.zip"

if [ ! -d "$MAVEN_HOME" ]; then
  echo "Downloading Maven $MAVEN_VERSION..."
  mkdir -p "$MAVEN_HOME"
  TMP_ZIP=$(mktemp /tmp/maven.XXXXXX.zip)
  if command -v curl > /dev/null 2>&1; then
    curl -fsSL "$MAVEN_ZIP_URL" -o "$TMP_ZIP"
  elif command -v wget > /dev/null 2>&1; then
    wget -q "$MAVEN_ZIP_URL" -O "$TMP_ZIP"
  else
    echo "Error: curl or wget is required to download Maven." >&2
    exit 1
  fi
  unzip -q "$TMP_ZIP" -d "$HOME/.m2/wrapper/dists/"
  rm -f "$TMP_ZIP"
  echo "Maven $MAVEN_VERSION installed."
fi

MVNW_EXEC="$MAVEN_HOME/apache-maven-$MAVEN_VERSION/bin/mvn"
exec "$MVNW_EXEC" "$@"
