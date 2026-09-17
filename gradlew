#!/bin/sh

APP_NAME="Gradle"
APP_HOME="$(cd "$(dirname "$0")" && pwd)"
DEFAULT_JVM_OPTS=""
GRADLE_OPTS="${GRADLE_OPTS:-}"
GRADLE_OPTS="$GRADLE_OPTS -Dorg.gradle.appname=$APP_NAME"
GRADLE_OPTS="$GRADLE_OPTS -Dorg.gradle.java.home=${JAVA_HOME}"

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec java $GRADLE_OPTS -cp "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
