#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Shield 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

NG_MANAGER_CONFIG=$HOME/.bazel_runner/.mirrord/harness-services/ng-manager
CONFIG_FILE=$NG_MANAGER_CONFIG/config.yml
REDISSON_CACHE_FILE=$NG_MANAGER_CONFIG/redisson-jcache.yaml
ENTERPRISE_REDISSON_CACHE_FILE=$NG_MANAGER_CONFIG/enterprise-redisson-jcache.yaml

EVENTS_FRAMEWORK_REDIS_SENTINELS="${REDIS_SENTINEL_HARNESS_ANNOUNCE_0_PORT_26379_TCP/tcp:/redis:},${REDIS_SENTINEL_HARNESS_ANNOUNCE_1_PORT_26379_TCP/tcp:/redis:},${REDIS_SENTINEL_HARNESS_ANNOUNCE_2_PORT_26379_TCP/tcp:/redis:}"
EVENTS_FRAMEWORK_REDIS_URL="${EVENTS_FRAMEWORK_REDIS_SENTINELS}"
LOCK_CONFIG_REDIS_URL="$EVENTS_FRAMEWORK_REDIS_SENTINELS"
LOCK_CONFIG_REDIS_SENTINELS="$EVENTS_FRAMEWORK_REDIS_SENTINELS"

echo -e "\nReplacing redis urls in:"
echo "    $CONFIG_FILE"
echo "    $REDISSON_CACHE_FILE"
echo -e "    $ENTERPRISE_REDISSON_CACHE_FILE\n"

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

replace_key_value eventsFramework.redis.redisUrl $EVENTS_FRAMEWORK_REDIS_URL
replace_key_value redisLockConfig.redisUrl $LOCK_CONFIG_REDIS_URL

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.eventsFramework.redis.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "$LOCK_CONFIG_USE_SENTINEL" == "true" ]]; then
  yq -i 'del(.singleServerConfig)' $REDISSON_CACHE_FILE
  if [[ "" != "$LOCK_CONFIG_SENTINEL_MASTER_NAME" ]]; then
    export LOCK_CONFIG_SENTINEL_MASTER_NAME; yq -i '.sentinelServersConfig.masterName=env(LOCK_CONFIG_SENTINEL_MASTER_NAME)' $REDISSON_CACHE_FILE
  fi
  if [[ "" != "$LOCK_CONFIG_REDIS_SENTINELS" ]]; then
    IFS=',' read -ra SENTINEL_URLS <<< "$LOCK_CONFIG_REDIS_SENTINELS"
    INDEX=0
    for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
      export REDIS_SENTINEL_URL; export INDEX; yq -i '.redisLockConfig.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
      export REDIS_SENTINEL_URL; export INDEX; yq -i '.sentinelServersConfig.sentinelAddresses.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $REDISSON_CACHE_FILE
      INDEX=$(expr $INDEX + 1)
    done
  fi
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
    IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
    INDEX=0
    for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
        export REDIS_SENTINEL_URL; export INDEX; yq -i '.sentinelServersConfig.sentinelAddresses.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $ENTERPRISE_REDISSON_CACHE_FILE
        INDEX=$(expr $INDEX + 1)
    done
fi
