#!/bin/sh
APP_HOME="${0%/*}/"
if [ "${APP_HOME}" = "./" ]; then APP_HOME=""; fi
CLASSPATH="${APP_HOME}gradle/wrapper/gradle-wrapper.jar"
if [ -n "$JAVA_HOME" ]; then JAVACMD="$JAVA_HOME/bin/java"; else JAVACMD=java; fi
DEFAULT_JVM_OPTS='-Xmx64m -Xms64m'
set -- "-classpath" "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
eval "set -- $(printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" | xargs -n1 | sed 's~[^-[:alnum:]+,./:=@_]~\\&~g;' | tr '\n' ' ')" '"$@"'
exec "$JAVACMD" "$@"
