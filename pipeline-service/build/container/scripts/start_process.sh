#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

set -x

# Trap signals and forward them to the Java process
_term() {
  echo "Caught SIGTERM/SIGINT, forwarding to Java process (PID: $child)"
  kill -TERM "$child" 2>/dev/null
}

_quit() {
  echo "Caught SIGQUIT, forwarding to Java process (PID: $child)"
  kill -QUIT "$child" 2>/dev/null
}

trap _term TERM INT
trap _quit QUIT

if [[ -v "{hostname}" ]]; then
   export HOSTNAME=$(hostname)
fi

if [[ -z "$MEMORY" ]]; then
   export MEMORY=4096m
fi

if [[ -z "$COMMAND" ]]; then
   export COMMAND=server
fi

echo "Using memory " "$MEMORY"

if [[ -z "$CAPSULE_JAR" ]]; then
   export CAPSULE_JAR=/opt/harness/pipeline-service-capsule.jar
fi

if [[ "${ENABLE_SERIALGC}" == "true" ]]; then
    export GC_PARAMS=" -XX:+UseSerialGC -Dfile.encoding=UTF-8"
else
    export GC_PARAMS=" -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=40 -XX:MaxGCPauseMillis=1000 -Dfile.encoding=UTF-8"
fi

# Check for required environment variables for heap dump collection
if [[ -z "$SERVICE_NAME" ]] || [[ -z "$POD_NAME" ]]; then
    echo "WARNING: SERVICE_NAME and/or POD_NAME environment variables are not set."
    echo "Heap dump collection may not work correctly without these values."
fi

export JAVA_OPTS="-Xmx${MEMORY} -Xms${MEMORY} -XX:+HeapDumpOnOutOfMemoryError -XX:+ExitOnOutOfMemoryError -XX:HeapDumpPath=/opt/harness/dumps/${SERVICE_NAME}/default/jfr_dumps/${POD_NAME} -Xloggc:mygclogfilename.gc $GC_PARAMS $JAVA_ADVANCED_FLAGS $JAVA_17_FLAGS"

if [[ "${ENABLE_REMOTE_DEBUG}" == "true" ]]; then
  export REMOTE_DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:5005"
  export JAVA_OPTS="$REMOTE_DEBUG $JAVA_OPTS"
  echo "Enabled remote debug"
fi

if [[ "${ENABLE_ERROR_TRACKING}" == "true" ]] ; then
    echo "Error Tracking is enabled"
    JAVA_OPTS=$JAVA_OPTS" -Xshare:off -XX:-UseTypeSpeculation -XX:ReservedCodeCacheSize=512m -agentpath:/opt/harness/harness/lib/libETAgent.so"
    echo "Using Error Tracking Java Agent"
fi

if [[ "${ENABLE_MONITORING}" == "true" ]] ; then
    echo "Monitoring  is enabled"
    JAVA_OPTS="$JAVA_OPTS ${MONITORING_FLAGS}"
    echo "Using inspectIT Java Agent"
fi

if [[ "${ENABLE_COVERAGE}" == "true" ]] ; then
    echo "functional code coverage is enabled"
    curl -o jacoco-0.8.7.zip https://repo1.maven.org/maven2/org/jacoco/jacoco/0.8.7/jacoco-0.8.7.zip
    mkdir /opt/harness/jacoco-0.8.7 && mv jacoco-0.8.7.zip /opt/harness/jacoco-0.8.7 && cd /opt/harness/jacoco-0.8.7 && jar -xvf jacoco-0.8.7.zip && rm -rf jacoco-0.8.7.zip && cd ..
    JAVA_OPTS=$JAVA_OPTS" -javaagent:/opt/harness/jacoco-0.8.7/lib/jacocoagent.jar=port=6300,address=0.0.0.0,append=true,output=tcpserver,destfile=jacoco-remote.exec"
    echo "Using Jacoco Java Agent"
fi

if [[ "${ENABLE_OPENTELEMETRY}" == "true" ]] ; then
    echo "OpenTelemetry is enabled"
    JAVA_OPTS=$JAVA_OPTS" -javaagent:/opt/harness/opentelemetry-javaagent.jar -Dotel.service.name=${OTEL_SERVICE_NAME:-pipeline-service}"

    if [ -n "$OTEL_EXPORTER_OTLP_ENDPOINT" ]; then
        JAVA_OPTS=$JAVA_OPTS" -Dotel.exporter.otlp.endpoint=$OTEL_EXPORTER_OTLP_ENDPOINT "
        JAVA_OPTS=$JAVA_OPTS" -Dotel.exporter.otlp.protocol=${OTEL_EXPORTER_OTLP_PROTOCOL:-grpc} "
        JAVA_OPTS=$JAVA_OPTS" -Dotel.logs.exporter=${OTEL_LOGS_EXPORTER:-none} "
    else
        echo "OpenTelemetry export is disabled"
        JAVA_OPTS=$JAVA_OPTS" -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "
    fi
    echo "Using OpenTelemetry Java Agent"
fi

if [[ "${DEPLOY_MODE}" == "KUBERNETES" || "${DEPLOY_MODE}" == "KUBERNETES_ONPREM" || "${DEPLOY_VERSION}" == "COMMUNITY" ]]; then
    java $JAVA_OPTS -cp $CLASSPATH:$CAPSULE_JAR io.harness.PipelineServiceApplication $COMMAND /opt/harness/config.yml &
else
    java $JAVA_OPTS -jar $CAPSULE_JAR $COMMAND /opt/harness/config.yml > /opt/harness/logs/pipeline-service.log 2>&1 &
fi

child=$!
wait "$child"