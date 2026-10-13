#!/bin/bash
# Cloud sessions only: install JDK 25 (not in the container's apt repos) and point Gradle at it.
# Local machines just need any JDK 25 on PATH or JAVA_HOME; this hook does nothing there.
set -euo pipefail
[ "${CLAUDE_CODE_REMOTE:-}" = "true" ] || exit 0

if ! ls -d /opt/jdk/jdk-25* >/dev/null 2>&1; then
  mkdir -p /opt/jdk
  curl -fsSL --retry 3 -o /tmp/jdk25.tar.gz https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz
  tar xzf /tmp/jdk25.tar.gz -C /opt/jdk
  rm /tmp/jdk25.tar.gz
fi
JDK=$(ls -d /opt/jdk/jdk-25* | head -1)
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  echo "export JAVA_HOME=$JDK" >> "$CLAUDE_ENV_FILE"
  echo "export PATH=$JDK/bin:\$PATH" >> "$CLAUDE_ENV_FILE"
fi
