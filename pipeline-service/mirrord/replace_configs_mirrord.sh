#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Shield 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

PIPELINE_SERVICE_CONFIG=$HOME/.bazel_runner/.mirrord/harness-services/pipeline-service
CONFIG_FILE=$PIPELINE_SERVICE_CONFIG/config.yml
REDISSON_CACHE_FILE=$PIPELINE_SERVICE_CONFIG/redisson-jcache.yaml
ENTERPRISE_REDISSON_CACHE_FILE=$PIPELINE_SERVICE_CONFIG/enterprise-redisson-jcache.yaml

REDIS_SENTINELS="${REDIS_SENTINEL_HARNESS_ANNOUNCE_0_PORT_26379_TCP/tcp:/redis:},${REDIS_SENTINEL_HARNESS_ANNOUNCE_1_PORT_26379_TCP/tcp:/redis:},${REDIS_SENTINEL_HARNESS_ANNOUNCE_2_PORT_26379_TCP/tcp:/redis:}"

echo -e "\nReplacing redis urls in:"
echo "    $CONFIG_FILE"
echo "    $REDISSON_CACHE_FILE\n"
echo -e "    $ENTERPRISE_REDISSON_CACHE_FILE\n"

if [[ "" != "$REDIS_SENTINELS" ]]; then
  IFS=',' read -ra REDIS_SENTINEL_URLS <<< "$REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${REDIS_SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.eventsFramework.redis.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.redisLockConfig.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.eventsFrameworkSnapshotDebezium.redis.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.sentinelServersConfig.sentinelAddresses.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $REDISSON_CACHE_FILE
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.sentinelServersConfig.sentinelAddresses.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $ENTERPRISE_REDISSON_CACHE_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$REDIS_SENTINELS" ]]; then
  export REDIS_SENTINELS; yq -i '.eventsFramework.redis.redisUrl=env(REDIS_SENTINELS)' $CONFIG_FILE
  export REDIS_SENTINELS; yq -i '.redisLockConfig.redisUrl=env(REDIS_SENTINELS)' $CONFIG_FILE
  export REDIS_SENTINELS; yq -i '.eventsFrameworkSnapshotDebezium.redis.redisUrl=env(REDIS_SENTINELS)' $CONFIG_FILE
fi
