#!/usr/bin/env bash
# Copyright 2021 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Shield 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

if [[ -v "{hostname}" ]]; then
   export HOSTNAME=$(hostname)
fi

if [[ -z "$MEMORY" ]]; then
   export MEMORY=2096m
fi

if [[ -z "$COMMAND" ]]; then
   export COMMAND=server
fi

echo "Using memory " "$MEMORY"

if [[ -z "$CAPSULE_JAR" ]]; then
   export CAPSULE_JAR=/opt/harness/ng-manager-capsule.jar
fi

if [[ "${ENABLE_SERIALGC}" == "true" ]]; then
    export GC_PARAMS=" -XX:+UseSerialGC -Dfile.encoding=UTF-8"
else
    export GC_PARAMS=" -XX:+UseG1GC -XX:InitiatingHeapOccupancyPercent=40 -XX:MaxGCPauseMillis=1000 -Dfile.encoding=UTF-8"
fi

export JAVA_OPTS="-Xmx${MEMORY} -XX:+HeapDumpOnOutOfMemoryError -Xloggc:mygclogfilename.gc $GC_PARAMS $JAVA_ADVANCED_FLAGS $JAVA_17_FLAGS"

if [[ "${ENABLE_REMOTE_DEBUG}" == "true" ]]; then
  export REMOTE_DEBUG="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:5005"
  export JAVA_OPTS="$REMOTE_DEBUG $JAVA_OPTS"
  echo "Enabled remote debug"
fi

if [[ "${ENABLE_OVEROPS}" == "true" ]] ; then
    echo "OverOps is enabled"
    JAVA_OPTS=$JAVA_OPTS" -Xshare:off -XX:-UseTypeSpeculation -XX:ReservedCodeCacheSize=512m -agentpath:/opt/harness/harness/lib/libETAgent.so"
    echo "Using Overops Java Agent"
fi

if [[ "${ENABLE_OPENTELEMETRY}" == "true" ]] ; then
    echo "OpenTelemetry is enabled"
    JAVA_OPTS=$JAVA_OPTS" -javaagent:/opt/harness/opentelemetry-javaagent.jar -Dotel.service.name=${OTEL_SERVICE_NAME:-ng-manager} "
    JAVA_OPTS=$JAVA_OPTS" -Dotel.instrumentation.mongo.enabled=false -Dotel.instrumentation.spring-data.enabled=false "

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
    EXPECTED_CHECKSUM="3d1e2fdcd703731f96eb34df001472c4c882d2eb14578968ac8768eb5d30fd92"
    FILE="jacoco-0.8.7.zip"
    # Verify checksum
    if echo "$EXPECTED_CHECKSUM  $FILE" | sha256sum -c - >/dev/null 2>&1; then
        echo "Checksum OK — file is valid."
        mkdir /opt/harness/jacoco-0.8.7 && mv jacoco-0.8.7.zip /opt/harness/jacoco-0.8.7 && cd /opt/harness/jacoco-0.8.7 && jar -xvf jacoco-0.8.7.zip && rm -rf jacoco-0.8.7.zip && cd ..
        JAVA_OPTS=$JAVA_OPTS" -javaagent:/opt/harness/jacoco-0.8.7/lib/jacocoagent.jar=port=6300,address=0.0.0.0,append=true,output=tcpserver,destfile=jacoco-remote.exec"
        echo "Using Jacoco Java Agent"
    else
        echo " Checksum FAILED — file is corrupted or tampered with."
    fi
fi


if [[ "${DEPLOY_MODE}" == "KUBERNETES" || "${DEPLOY_MODE}" == "KUBERNETES_ONPREM" || "${DEPLOY_VERSION}" == "COMMUNITY" ]]; then
    java $JAVA_OPTS -jar $CAPSULE_JAR $COMMAND /opt/harness/config.yml
else
    java $JAVA_OPTS -jar $CAPSULE_JAR $COMMAND /opt/harness/config.yml > /opt/harness/logs/ng-manager.log 2>&1
fi
