#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

mkdir -p /opt/harness/logs
touch /opt/harness/logs/ci-manager.log

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
   export CAPSULE_JAR=/opt/harness/ci-manager-capsule.jar
fi

export GC_PARAMS=" -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=40 -XX:MaxGCPauseMillis=1000 -Dfile.encoding=UTF-8"

export JAVA_OPTS="-Xmx${MEMORY} -XX:+HeapDumpOnOutOfMemoryError -Xloggc:mygclogfilename.gc $GC_PARAMS $JAVA_ADVANCED_FLAGS $JAVA_17_FLAGS"

if [[ "${ENABLE_MONITORING}" == "true" ]] ; then
    echo "Monitoring  is enabled"
    JAVA_OPTS="$JAVA_OPTS ${MONITORING_FLAGS}"
    echo "Using inspectIT Java Agent"
fi

if [[ "${ENABLE_OPENTELEMETRY}" == "true" ]] ; then
    echo "OpenTelemetry is enabled"
    JAVA_OPTS=$JAVA_OPTS" -javaagent:/opt/harness/opentelemetry-javaagent.jar -Dotel.service.name=${OTEL_SERVICE_NAME:-ci-manager}"

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

if [[ "${ENABLE_COVERAGE}" == "true" ]] ; then
    echo "functional code coverage is enabled"
    curl -o jacoco-0.8.7.zip https://repo1.maven.org/maven2/org/jacoco/jacoco/0.8.7/jacoco-0.8.7.zip
    mkdir /opt/harness/jacoco-0.8.7 && mv jacoco-0.8.7.zip /opt/harness/jacoco-0.8.7 && cd /opt/harness/jacoco-0.8.7 && jar -xvf jacoco-0.8.7.zip && rm -rf jacoco-0.8.7.zip && cd ..
    JAVA_OPTS=$JAVA_OPTS" -javaagent:/opt/harness/jacoco-0.8.7/lib/jacocoagent.jar=port=6300,address=0.0.0.0,append=true,output=tcpserver,destfile=jacoco-remote.exec"
    echo "Using Jacoco Java Agent"
fi

java $JAVA_OPTS -cp $CLASSPATH:$CAPSULE_JAR io.harness.app.CIManagerApplication $COMMAND /opt/harness/ci-manager-config.yml
