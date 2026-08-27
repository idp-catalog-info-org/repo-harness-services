#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Shield 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.

CONFIG_FILE=/opt/harness/config.yml
REDISSON_CACHE_FILE=/opt/harness/redisson-jcache.yaml
ENTERPRISE_REDISSON_CACHE_FILE=/opt/harness/enterprise-redisson-jcache.yaml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
}

write_mongo_hosts_and_ports() {
  IFS=',' read -ra HOST_AND_PORT <<< "$2"
  for INDEX in "${!HOST_AND_PORT[@]}"; do
    HOST=$(cut -d: -f 1 <<< "${HOST_AND_PORT[$INDEX]}")
    PORT=$(cut -d: -f 2 -s <<< "${HOST_AND_PORT[$INDEX]}")

    export HOST; export ARG1=$1; export INDEX; yq -i '.env(ARG1).[env(INDEX)].host=env(HOST)' $CONFIG_FILE
    if [[ "" != "$PORT" ]]; then
      export PORT; export ARG1=$1; export INDEX; yq -i '.env(ARG1).[env(INDEX)].port=env(PORT)' $CONFIG_FILE
    fi
  done
}

write_mongo_params() {
  IFS='&' read -ra PARAMS <<< "$2"
  for PARAM_PAIR in "${PARAMS[@]}"; do
    NAME=$(cut -d= -f 1 <<< "$PARAM_PAIR")
    VALUE=$(cut -d= -f 2 <<< "$PARAM_PAIR")
    export VALUE; export ARG1=$1; export NAME; yq -i '.env(ARG1).params.env(NAME)=env(VALUE)' $CONFIG_FILE
  done
}

yq -i 'del(.server.applicationConnectors.[] | select(.type == "https"))' $CONFIG_FILE
yq -i 'del(.server.adminConnectors.[] | select(.type == "https"))' $CONFIG_FILE

yq -i 'del(.grpcServer.connectors.[] | select(.secure == true))' $CONFIG_FILE
yq -i 'del(.pmsSdkGrpcServerConfig.connectors.[] | select(.secure == true))' $CONFIG_FILE
yq -i 'del(.gitSyncServerConfig.connectors.[] | select(.secure == true))' $CONFIG_FILE

if [[ "" != "$LOGGING_LEVEL" ]]; then
    export LOGGING_LEVEL; yq -i '.logging.level=env(LOGGING_LEVEL)' $CONFIG_FILE
fi

if [[ "" != "$LOGGERS" ]]; then
  IFS=',' read -ra LOGGER_ITEMS <<< "$LOGGERS"
  for ITEM in "${LOGGER_ITEMS[@]}"; do
    LOGGER=`echo $ITEM | awk -F= '{print $1}'`
    LOGGER_LEVEL=`echo $ITEM | awk -F= '{print $2}'`
    export LOGGER_LEVEL; export LOGGER; yq -i '.logging.loggers.[env(LOGGER)]=env(LOGGER_LEVEL)' $CONFIG_FILE
  done
fi

if [[ "" != "$SERVER_PORT" ]]; then
  export SERVER_PORT; yq -i '.server.applicationConnectors[0].port=env(SERVER_PORT)' $CONFIG_FILE
else
  yq -i '.server.applicationConnectors[0].port=7090' $CONFIG_FILE
fi

if [[ "" != "$ADMIN_PORT" ]]; then
  export ADMIN_PORT; yq -i '.server.adminConnectors[0].port=env(ADMIN_PORT)' $CONFIG_FILE
else
  yq -i '.server.adminConnectors[0].port=7458' $CONFIG_FILE
fi

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  export SERVER_MAX_THREADS; yq -i '.server.maxThreads=env(SERVER_MAX_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq -i 'del(.allowedOrigins)' $CONFIG_FILE
  export ALLOWED_ORIGINS; yq -i '.allowedOrigins=(env(ALLOWED_ORIGINS) | split(",") | map(trim))' $CONFIG_FILE
fi

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI=${MONGO_URI//\\&/&}; yq -i '.mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_HOSTS_AND_PORTS" ]]; then
  yq -i 'del(.mongo.uri)' $CONFIG_FILE
  export MONGO_USERNAME; yq -i '.mongo.username=env(MONGO_USERNAME)' $CONFIG_FILE
  export MONGO_PASSWORD; yq -i '.mongo.password=env(MONGO_PASSWORD)' $CONFIG_FILE
  export MONGO_DATABASE; yq -i '.mongo.database=env(MONGO_DATABASE)' $CONFIG_FILE
  export MONGO_SCHEMA; yq -i '.mongo.schema=env(MONGO_SCHEMA)' $CONFIG_FILE
  write_mongo_hosts_and_ports mongo "$MONGO_HOSTS_AND_PORTS"
  write_mongo_params mongo "$MONGO_PARAMS"
fi

if [[ "" != "$MONGO_TRACE_MODE" ]]; then
  export MONGO_TRACE_MODE; yq -i '.mongo.traceMode=env(MONGO_TRACE_MODE)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_MAX_OPERATION_TIME_IN_MILLIS" ]]; then
  export MONGO_MAX_OPERATION_TIME_IN_MILLIS; yq -i '.mongo.maxOperationTimeInMillis=env(MONGO_MAX_OPERATION_TIME_IN_MILLIS)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_MAX_DOCUMENT_LIMIT" ]]; then
  export MONGO_MAX_DOCUMENT_LIMIT; yq -i '.mongo.maxDocumentsToBeFetched=env(MONGO_MAX_DOCUMENT_LIMIT)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECT_TIMEOUT" ]]; then
  export MONGO_CONNECT_TIMEOUT; yq -i '.mongo.connectTimeout=env(MONGO_CONNECT_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SERVER_SELECTION_TIMEOUT" ]]; then
  export MONGO_SERVER_SELECTION_TIMEOUT; yq -i '.mongo.serverSelectionTimeout=env(MONGO_SERVER_SELECTION_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_SOCKET_TIMEOUT" ]]; then
  export MONGO_SOCKET_TIMEOUT; yq -i '.mongo.socketTimeout=env(MONGO_SOCKET_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$MAX_CONNECTION_IDLE_TIME" ]]; then
  export MAX_CONNECTION_IDLE_TIME; yq -i '.mongo.maxConnectionIdleTime=env(MAX_CONNECTION_IDLE_TIME)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_CONNECTIONS_PER_HOST" ]]; then
  export MONGO_CONNECTIONS_PER_HOST; yq -i '.mongo.connectionsPerHost=env(MONGO_CONNECTIONS_PER_HOST)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  export MONGO_INDEX_MANAGER_MODE; yq -i '.mongo.indexManagerMode=env(MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_TRANSACTIONS_ALLOWED" ]]; then
  export MONGO_TRANSACTIONS_ALLOWED; yq -i '.mongo.transactionsEnabled=env(MONGO_TRANSACTIONS_ALLOWED)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_TARGET" ]]; then
  export MANAGER_TARGET; yq -i '.grpcClient.target=env(MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_AUTHORITY" ]]; then
  export MANAGER_AUTHORITY; yq -i '.grpcClient.authority=env(MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  export GRPC_SERVER_PORT; yq -i '.grpcServer.connectors[0].port=env(GRPC_SERVER_PORT)' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  export NEXT_GEN_MANAGER_SECRET; yq -i '.nextGen.managerServiceSecret=env(NEXT_GEN_MANAGER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  export NEXT_GEN_MANAGER_SECRET; yq -i '.nextGen.ngManagerServiceSecret=env(NEXT_GEN_MANAGER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$USER_VERIFICATION_SECRET" ]]; then
  export USER_VERIFICATION_SECRET; yq -i '.nextGen.userVerificationSecret=env(USER_VERIFICATION_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$JWT_IDENTITY_SERVICE_SECRET" ]]; then
  export JWT_IDENTITY_SERVICE_SECRET; yq -i '.nextGen.jwtIdentityServiceSecret=env(JWT_IDENTITY_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  export NEXT_GEN_MANAGER_SECRET; yq -i '.nextGen.pipelineServiceSecret=env(NEXT_GEN_MANAGER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  export NEXT_GEN_MANAGER_SECRET; yq -i '.nextGen.ciManagerSecret=env(NEXT_GEN_MANAGER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  export NEXT_GEN_MANAGER_SECRET; yq -i '.nextGen.ceNextGenServiceSecret=env(NEXT_GEN_MANAGER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  export NEXT_GEN_MANAGER_SECRET; yq -i '.nextGen.ffServiceSecret=env(NEXT_GEN_MANAGER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$IACM_SERVICE_SECRET" ]]; then
  export IACM_SERVICE_SECRET; yq -i '.nextGen.iacmServiceSecret=env(IACM_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$TEMPLATE_SERVICE_ENDPOINT" ]]; then
  export TEMPLATE_SERVICE_ENDPOINT; yq -i '.templateServiceClientConfig.baseUrl=env(TEMPLATE_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$TEMPLATE_SERVICE_SECRET" ]]; then
  export TEMPLATE_SERVICE_SECRET; yq -i '.nextGen.templateServiceSecret=env(TEMPLATE_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$IDP_SERVICE_SECRET" ]]; then
  export IDP_SERVICE_SECRET; yq -i '.nextGen.idpServiceSecret=env(IDP_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$DBOPS_SERVICE_SECRET" ]]; then
  export DBOPS_SERVICE_SECRET; yq -i '.nextGen.dbOpsServiceSecret=env(DBOPS_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$AIMLOPS_SERVICE_SECRET" ]]; then
  export AIMLOPS_SERVICE_SECRET; yq -i '.nextGen.aiMLOpsServiceSecret=env(AIMLOPS_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_REGISTRY_SERVICE_SECRET" ]]; then
  export HARNESS_REGISTRY_SERVICE_SECRET; yq -i '.nextGen.harnessRegistryServiceSecret=env(HARNESS_REGISTRY_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$APPSEC_SERVICE_SECRET" ]]; then
  export APPSEC_SERVICE_SECRET; yq -i '.nextGen.appsecServiceSecret=env(APPSEC_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_ARTIFACT_REGISTRY_WEBHOOK_SECRET" ]]; then
  export HARNESS_ARTIFACT_REGISTRY_WEBHOOK_SECRET; yq -i '.webhookSecretsConfig.artifactRegistry=env(HARNESS_ARTIFACT_REGISTRY_WEBHOOK_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_CODE_WEBHOOK_SECRET" ]]; then
  export HARNESS_CODE_WEBHOOK_SECRET; yq -i '.webhookSecretsConfig.code=env(HARNESS_CODE_WEBHOOK_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$AUTH_ENABLED" ]]; then
  export AUTH_ENABLED; yq -i '.enableAuth=env(AUTH_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_ENABLED" ]]; then
  export AUDIT_ENABLED; yq -i '.enableAudit=env(AUDIT_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_CLIENT_BASEURL" ]]; then
  export MANAGER_CLIENT_BASEURL; yq -i '.managerClientConfig.baseUrl=env(MANAGER_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_CLIENT_BASEURL" ]]; then
  export NG_MANAGER_CLIENT_BASEURL; yq -i '.ngManagerClientConfig.baseUrl=env(NG_MANAGER_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$CENG_CLIENT_BASEURL" ]]; then
  export CENG_CLIENT_BASEURL; yq -i '.ceNextGenClientConfig.baseUrl=env(CENG_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$CENG_CLIENT_READ_TIMEOUT" ]]; then
  export CENG_CLIENT_READ_TIMEOUT; yq -i '.ceNextGenClientConfig.readTimeOutSeconds=env(CENG_CLIENT_READ_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$CENG_CLIENT_CONNECT_TIMEOUT" ]]; then
  export CENG_CLIENT_CONNECT_TIMEOUT; yq -i '.ceNextGenClientConfig.connectTimeOutSeconds=env(CENG_CLIENT_CONNECT_TIMEOUT)' $CONFIG_FILE
fi

if [[ "" != "$JWT_AUTH_SECRET" ]]; then
  export JWT_AUTH_SECRET; yq -i '.nextGen.jwtAuthSecret=env(JWT_AUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.eventsFramework.redis.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  export GRPC_SERVER_PORT; yq -i '.pmsSdkGrpcServerConfig.connectors[0].port=env(GRPC_SERVER_PORT)' $CONFIG_FILE
fi


if [[ "" != "$SHOULD_CONFIGURE_WITH_PMS" ]]; then
  export SHOULD_CONFIGURE_WITH_PMS; yq -i '.shouldConfigureWithPMS=env(SHOULD_CONFIGURE_WITH_PMS)' $CONFIG_FILE
fi

if [[ "" != "$PMS_TARGET" ]]; then
  export PMS_TARGET; yq -i '.pmsGrpcClientConfig.target=env(PMS_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$PMS_AUTHORITY" ]]; then
  export PMS_AUTHORITY; yq -i '.pmsGrpcClientConfig.authority=env(PMS_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_TARGET" ]]; then
 export NG_MANAGER_TARGET; yq -i '.gitGrpcClientConfigs.core.target=env(NG_MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_AUTHORITY" ]]; then
  export NG_MANAGER_AUTHORITY; yq -i '.gitGrpcClientConfigs.core.authority=env(NG_MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_TARGET" ]]; then
  export NG_MANAGER_TARGET; yq -i '.gitSdkConfiguration.gitManagerGrpcClientConfig.target=env(NG_MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_AUTHORITY" ]]; then
  export NG_MANAGER_AUTHORITY; yq -i '.gitSdkConfiguration.gitManagerGrpcClientConfig.authority=env(NG_MANAGER_AUTHORITY)' $CONFIG_FILE
fi


if [[ "" != "$HARNESS_IMAGE_USER_NAME" ]]; then
  export HARNESS_IMAGE_USER_NAME; yq -i '.ciDefaultEntityConfiguration.harnessImageUseName=env(HARNESS_IMAGE_USER_NAME)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_IMAGE_PASSWORD" ]]; then
  export HARNESS_IMAGE_PASSWORD; yq -i '.ciDefaultEntityConfiguration.harnessImagePassword=env(HARNESS_IMAGE_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$CE_NG_CLIENT_BASEURL" ]]; then
  export CE_NG_CLIENT_BASEURL; yq -i '.ceNextGenClientConfig.baseUrl=env(CE_NG_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$LW_CLIENT_BASEURL" ]]; then
  export LW_CLIENT_BASEURL; yq -i '.lightwingClientConfig.baseUrl=env(LW_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$CF_CLIENT_BASEURL" ]]; then
  export CF_CLIENT_BASEURL; yq -i '.ffServerClientConfig.baseUrl=env(CF_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$IACM_SERVICE_BASE_URL" ]]; then
  export IACM_SERVICE_BASE_URL; yq -i '.iacmClientConfig.baseUrl=env(IACM_SERVICE_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_CLIENT_BASEURL" ]]; then
  export AUDIT_CLIENT_BASEURL; yq -i '.auditClientConfig.baseUrl=env(AUDIT_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$SCM_SERVICE_URI" ]]; then
  export SCM_SERVICE_URI; yq -i '.gitSdkConfiguration.scmConnectionConfig.url=env(SCM_SERVICE_URI)' $CONFIG_FILE
fi

if [[ "" != "$LOG_STREAMING_SERVICE_BASEURL" ]]; then
  export LOG_STREAMING_SERVICE_BASEURL; yq -i '.logStreamingServiceConfig.baseUrl=env(LOG_STREAMING_SERVICE_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$LOG_STREAMING_SERVICE_TOKEN" ]]; then
  export LOG_STREAMING_SERVICE_TOKEN; yq -i '.logStreamingServiceConfig.serviceToken=env(LOG_STREAMING_SERVICE_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$CANNY_TOKEN" ]]; then
  export CANNY_TOKEN; yq -i '.cannyApiConfig.token=env(CANNY_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$CLEARBIT_TOKEN" ]]; then
  export CLEARBIT_TOKEN; yq -i '.clearbitApiConfig.token=env(CLEARBIT_TOKEN)' $CONFIG_FILE
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders.[] | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders.[] | select(.type == "gke-console") | .stackdriverLogEnabled) = true' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALE_PASSWORD" ]]; then
  export TIMESCALE_PASSWORD; yq -i '.timescaledb.timescaledbPassword=env(TIMESCALE_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALE_URI" ]]; then
  export TIMESCALE_URI; yq -i '.timescaledb.timescaledbUrl=env(TIMESCALE_URI)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_USERNAME" ]]; then
  export TIMESCALEDB_USERNAME; yq -i '.timescaledb.timescaledbUsername=env(TIMESCALEDB_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SSL_MODE" ]]; then
  export TIMESCALEDB_SSL_MODE; yq -i '.timescaledb.sslMode=env(TIMESCALEDB_SSL_MODE)' $CONFIG_FILE
fi

if [[ "" != "$TIMESCALEDB_SSL_ROOT_CERT" ]]; then
  export TIMESCALEDB_SSL_ROOT_CERT; yq -i '.timescaledb.sslRootCert=env(TIMESCALEDB_SSL_ROOT_CERT)' $CONFIG_FILE
fi

if [[ "" != "$ALLOYDB_URI" ]]; then
  export ALLOYDB_URI; yq -i '.alloydb.alloyDbUrl=env(ALLOYDB_URI)' $CONFIG_FILE
fi

if [[ "" != "$ALLOYDB_USERNAME" ]]; then
  export ALLOYDB_USERNAME; yq -i '.alloydb.alloyDbUsername=env(ALLOYDB_USERNAME)' $CONFIG_FILE
fi

if [[ "" != "$ALLOYDB_PASSWORD" ]]; then
  export ALLOYDB_PASSWORD; yq -i '.alloydb.alloyDbPassowrd=env(ALLOYDB_PASSWORD)' $CONFIG_FILE
fi

if [[ "" != "$ALLOYDB_SSL_MODE" ]]; then
  export ALLOYDB_SSL_MODE; yq -i '.alloydb.sslMode=env(ALLOYDB_SSL_MODE)' $CONFIG_FILE
fi

if [[ "" != "$ALLOYDB_SSL_ROOT_CERT" ]]; then
  export ALLOYDB_SSL_ROOT_CERT; yq -i '.alloydb.sslRootCert=env(ALLOYDB_SSL_ROOT_CERT)' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_DASHBOARD_TIMESCALE" ]]; then
  export ENABLE_DASHBOARD_TIMESCALE; yq -i '.enableDashboardTimescale=env(ENABLE_DASHBOARD_TIMESCALE)' $CONFIG_FILE
fi

if [[ "" != "$FILE_STORAGE_MODE" ]]; then
  export FILE_STORAGE_MODE; yq -i '.fileServiceConfiguration.fileStorageMode=env(FILE_STORAGE_MODE)' $CONFIG_FILE
fi

if [[ "" != "$FILE_STORAGE_CLUSTER_NAME" ]]; then
  export FILE_STORAGE_CLUSTER_NAME; yq -i '.fileServiceConfiguration.clusterName=env(FILE_STORAGE_CLUSTER_NAME)' $CONFIG_FILE
fi

if [[ "" != "$FILE_STORAGE_AWS_REGION" ]]; then
  export FILE_STORAGE_AWS_REGION; yq -i '.fileServiceConfiguration.awsRegion=env(FILE_STORAGE_AWS_REGION)' $CONFIG_FILE
fi

yq -i 'del(.codec)' $REDISSON_CACHE_FILE

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.redisLockConfig.useScriptCache=false' $CONFIG_FILE
  yq -i '.useScriptCache=false' $REDISSON_CACHE_FILE
fi

if [[ "" != "$STRIPE_API_KEY" ]]; then
  export STRIPE_API_KEY; yq -i '' $CONFIG_FILE
fi

if [[ "" != "$STRIPE_IDEMPOTENCY_KEY" ]]; then
  export STRIPE_IDEMPOTENCY_KEY; yq -i '' $CONFIG_FILE
fi

if [[ "" != "$STRIPE_ACCOUNT" ]]; then
  export STRIPE_ACCOUNT; yq -i '' $CONFIG_FILE
fi

replace_key_value distributedLockImplementation $DISTRIBUTED_LOCK_IMPLEMENTATION

replace_key_value redisLockConfig.sentinel $LOCK_CONFIG_USE_SENTINEL
replace_key_value redisLockConfig.envNamespace $LOCK_CONFIG_ENV_NAMESPACE
replace_key_value redisLockConfig.redisUrl $LOCK_CONFIG_REDIS_URL
replace_key_value redisLockConfig.masterName $LOCK_CONFIG_SENTINEL_MASTER_NAME
replace_key_value redisLockConfig.userName $LOCK_CONFIG_REDIS_USERNAME
replace_key_value redisLockConfig.password $LOCK_CONFIG_REDIS_PASSWORD
replace_key_value redisLockConfig.nettyThreads $REDIS_NETTY_THREADS
replace_key_value redisLockConfig.connectionPoolSize $REDIS_CONNECTION_POOL_SIZE
replace_key_value redisLockConfig.retryInterval $REDIS_RETRY_INTERVAL
replace_key_value redisLockConfig.retryAttempts $REDIS_RETRY_ATTEMPTS
replace_key_value redisLockConfig.timeout $REDIS_TIMEOUT
replace_key_value redisLockConfig.sslConfig.enabled $LOCK_CONFIG_REDIS_SSL_ENABLED
replace_key_value redisLockConfig.sslConfig.CATrustStorePath $LOCK_CONFIG_REDIS_SSL_CA_TRUST_STORE_PATH
replace_key_value redisLockConfig.sslConfig.CATrustStorePassword $LOCK_CONFIG_REDIS_SSL_CA_TRUST_STORE_PASSWORD

replace_key_value gitOpsStepExecutorServiceConfig.corePoolSize $GITOPS_STEP_EXECUTOR_POOL_CORE_SIZE
replace_key_value gitOpsStepExecutorServiceConfig.maxPoolSize $GITOPS_STEP_EXECUTOR_POOL_MAX_SIZE
replace_key_value gitOpsStepExecutorServiceConfig.idleTime $GITOPS_STEP_EXECUTOR_POOL_IDLE_TIME
replace_key_value gitOpsStepExecutorServiceConfig.timeUnit $GITOPS_STEP_EXECUTOR_POOL_IDLE_TIME_UNIT

if [[ "" != "$HSQS_BASE_URL" ]]; then
  export HSQS_BASE_URL; yq -i '.queueServiceClientConfig.httpClientConfig.baseUrl=env(HSQS_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$HSQS_TOPIC" ]]; then
  export HSQS_TOPIC; yq -i '.queueServiceClientConfig.topic=env(HSQS_TOPIC)' $CONFIG_FILE
fi

if [[ "" != "$HSQS_AUTH_TOKEN" ]]; then
  export HSQS_AUTH_TOKEN; yq -i '.queueServiceClientConfig.queueServiceSecret=env(HSQS_AUTH_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$LOCK_CONFIG_REDIS_URL" ]]; then
  export LOCK_CONFIG_REDIS_URL; yq -i '.singleServerConfig.address=env(LOCK_CONFIG_REDIS_URL)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$LOCK_CONFIG_REDIS_SSL_CA_TRUST_STORE_PATH" ]]; then
  export FILE_VAR="file:$LOCK_CONFIG_REDIS_SSL_CA_TRUST_STORE_PATH"; yq -i '.singleServerConfig.sslTruststore=env(FILE_VAR)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$LOCK_CONFIG_REDIS_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  export LOCK_CONFIG_REDIS_SSL_CA_TRUST_STORE_PASSWORD; yq -i '.singleServerConfig.sslTruststorePassword=env(LOCK_CONFIG_REDIS_SSL_CA_TRUST_STORE_PASSWORD)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$GITLAB_OAUTH_CLIENT" ]]; then
  export GITLAB_OAUTH_CLIENT; yq -i '.gitlabConfig.clientId=env(GITLAB_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$GITLAB_OAUTH_SECRET" ]]; then
  export GITLAB_OAUTH_SECRET; yq -i '.gitlabConfig.clientSecret=env(GITLAB_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$GITLAB_OAUTH_CALLBACK_URL" ]]; then
  export GITLAB_OAUTH_CALLBACK_URL; yq -i '.gitlabConfig.callbackUrl=env(GITLAB_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$BITBUCKET_OAUTH_CLIENT" ]]; then
  export BITBUCKET_OAUTH_CLIENT; yq -i '.bitbucketConfig.clientId=env(BITBUCKET_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$BITBUCKET_OAUTH_SECRET" ]]; then
  export BITBUCKET_OAUTH_SECRET; yq -i '.bitbucketConfig.clientSecret=env(BITBUCKET_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$BITBUCKET_OAUTH_CALLBACK_URL" ]]; then
  export BITBUCKET_OAUTH_CALLBACK_URL; yq -i '.bitbucketConfig.callbackUrl=env(BITBUCKET_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$ZOOM_OAUTH_CLIENT" ]]; then
   export ZOOM_OAUTH_CLIENT; yq -i '.zoomConfig.clientId=env(ZOOM_OAUTH_CLIENT)' $CONFIG_FILE
 fi

if [[ "" != "$ZOOM_OAUTH_SECRET" ]]; then
 export ZOOM_OAUTH_SECRET; yq -i '.zoomConfig.clientSecret=env(ZOOM_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$ZOOM_OAUTH_CALLBACK_URL" ]]; then
 export ZOOM_OAUTH_CALLBACK_URL; yq -i '.zoomConfig.callbackUrl=env(ZOOM_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$MS_TEAMS_OAUTH_CLIENT" ]]; then
   export MS_TEAMS_OAUTH_CLIENT; yq -i '.msTeamsConfig.clientId=env(MS_TEAMS_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$MS_TEAMS_OAUTH_SECRET" ]]; then
 export MS_TEAMS_OAUTH_SECRET; yq -i '.msTeamsConfig.clientSecret=env(MS_TEAMS_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$MS_TEAMS_OAUTH_CALLBACK_URL" ]]; then
 export MS_TEAMS_OAUTH_CALLBACK_URL; yq -i '.msTeamsConfig.callbackUrl=env(MS_TEAMS_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$MS_TEAMS_OAUTH_SCOPE" ]]; then
 export MS_TEAMS_OAUTH_SCOPE; yq -i '.msTeamsConfig.scope=env(MS_TEAMS_OAUTH_SCOPE)' $CONFIG_FILE
fi

if [[ "" != "$CONFLUENCE_OAUTH_CLIENT" ]]; then
   export CONFLUENCE_OAUTH_CLIENT; yq -i '.confluenceConfig.clientId=env(CONFLUENCE_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$CONFLUENCE_OAUTH_SECRET" ]]; then
 export CONFLUENCE_OAUTH_SECRET; yq -i '.confluenceConfig.clientSecret=env(CONFLUENCE_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$CONFLUENCE_OAUTH_CALLBACK_URL" ]]; then
 export CONFLUENCE_OAUTH_CALLBACK_URL; yq -i '.confluenceConfig.callbackUrl=env(CONFLUENCE_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$CONFLUENCE_OAUTH_SCOPE" ]]; then
 export CONFLUENCE_OAUTH_SCOPE; yq -i '.confluenceConfig.scope=env(CONFLUENCE_OAUTH_SCOPE)' $CONFIG_FILE
fi

if [[ "" != "$SLACK_OAUTH_CLIENT" ]]; then
   export SLACK_OAUTH_CLIENT; yq -i '.slackConfig.clientId=env(SLACK_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$SLACK_OAUTH_SECRET" ]]; then
 export SLACK_OAUTH_SECRET; yq -i '.slackConfig.clientSecret=env(SLACK_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$SLACK_OAUTH_CALLBACK_URL" ]]; then
 export SLACK_OAUTH_CALLBACK_URL; yq -i '.slackConfig.callbackUrl=env(SLACK_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$SLACK_OAUTH_SCOPE" ]]; then
 export SLACK_OAUTH_SCOPE; yq -i '.slackConfig.scope=env(SLACK_OAUTH_SCOPE)' $CONFIG_FILE
fi

if [[ "" != "$GOOGLE_CHAT_OAUTH_CLIENT" ]]; then
   export GOOGLE_CHAT_OAUTH_CLIENT; yq -i '.googleChatConfig.clientId=env(GOOGLE_CHAT_OAUTH_CLIENT)' $CONFIG_FILE
fi

if [[ "" != "$GOOGLE_CHAT_OAUTH_SECRET" ]]; then
 export GOOGLE_CHAT_OAUTH_SECRET; yq -i '.googleChatConfig.clientSecret=env(GOOGLE_CHAT_OAUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$GOOGLE_CHAT_OAUTH_CALLBACK_URL" ]]; then
 export GOOGLE_CHAT_OAUTH_CALLBACK_URL; yq -i '.googleChatConfig.callbackUrl=env(GOOGLE_CHAT_OAUTH_CALLBACK_URL)' $CONFIG_FILE
fi

if [[ "" != "$CLOUD_CREDITS_HOURLY_ROLL_UP_JOB_SCHEDULE" ]]; then
  export CLOUD_CREDITS_HOURLY_ROLL_UP_JOB_SCHEDULE; yq -i '.cloudCreditsHourlyRollUpJobSchedule=env(CLOUD_CREDITS_HOURLY_ROLL_UP_JOB_SCHEDULE)' $CONFIG_FILE
fi

if [[ "" != "$CLOUD_CREDITS_HOURLY_ROLL_UP_BATCH_SIZE" ]]; then
  export CLOUD_CREDITS_HOURLY_ROLL_UP_BATCH_SIZE; yq -i '.cloudCreditsHourlyRollUpBatchSize=env($CLOUD_CREDITS_HOURLY_ROLL_UP_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$OAUTH_REFRESH_FREQUECY" ]]; then
  export OAUTH_REFRESH_FREQUECY; yq -i '.oauthRefreshFrequency=env(OAUTH_REFRESH_FREQUECY)' $CONFIG_FILE
fi

if [[ "" != "$OAUTH_REFRESH_ENABLED" ]]; then
  export OAUTH_REFRESH_ENABLED; yq -i '.oauthRefreshEnabled=env(OAUTH_REFRESH_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_STATUS_ENDPOINT" ]]; then
  export DELEGATE_STATUS_ENDPOINT; yq -i '.delegateStatusEndpoint=env(DELEGATE_STATUS_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$SIGNUP_TARGET_ENV" ]]; then
  export SIGNUP_TARGET_ENV; yq -i '.signupTargetEnv=env(SIGNUP_TARGET_ENV)' $CONFIG_FILE
fi

if [[ "$LOCK_CONFIG_USE_SENTINEL" == "true" ]]; then
  yq -i 'del(.singleServerConfig)' $REDISSON_CACHE_FILE
fi

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

if [[ "" != "$REDIS_NETTY_THREADS" ]]; then
  export REDIS_NETTY_THREADS; yq -i '.nettyThreads=env(REDIS_NETTY_THREADS)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_CONNECTION_POOL_SIZE" ]]; then
  export REDIS_CONNECTION_POOL_SIZE; yq -i '.singleServerConfig.connectionPoolSize=env(REDIS_CONNECTION_POOL_SIZE)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_RETRY_INTERVAL" ]]; then
  export REDIS_RETRY_INTERVAL; yq -i '.singleServerConfig.retryInterval=env(REDIS_RETRY_INTERVAL)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_RETRY_ATTEMPTS" ]]; then
  export REDIS_RETRY_ATTEMPTS; yq -i '.singleServerConfig.retryAttempts=env(REDIS_RETRY_ATTEMPTS)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_TIMEOUT" ]]; then
  export REDIS_TIMEOUT; yq -i '.singleServerConfig.timeout=env(REDIS_TIMEOUT)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$REDIS_CACHE_CLEANUP_KEYS_AMOUNT" ]]; then
  export $REDIS_CACHE_CLEANUP_KEYS_AMOUNT; yq -i '.cleanUpKeysAmount=env(REDIS_CACHE_CLEANUP_KEYS_AMOUNT)' $REDISSON_CACHE_FILE
  export $REDIS_CACHE_CLEANUP_KEYS_AMOUNT; yq -i '.cleanUpKeysAmount=env(REDIS_CACHE_CLEANUP_KEYS_AMOUNT)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

yq -i 'del(.codec)' $ENTERPRISE_REDISSON_CACHE_FILE

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.useScriptCache=false' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_NETTY_THREADS" ]]; then
  export EVENTS_FRAMEWORK_NETTY_THREADS; yq -i '.nettyThreads=env(EVENTS_FRAMEWORK_NETTY_THREADS)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_URL" ]]; then
  export EVENTS_FRAMEWORK_REDIS_URL; yq -i '.singleServerConfig.address=env(EVENTS_FRAMEWORK_REDIS_URL)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_USERNAME" ]]; then
  export EVENTS_FRAMEWORK_REDIS_USERNAME; yq -i '.singleServerConfig.username=env(EVENTS_FRAMEWORK_REDIS_USERNAME)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_PASSWORD" ]]; then
  export EVENTS_FRAMEWORK_REDIS_PASSWORD; yq -i '.singleServerConfig.password=env(EVENTS_FRAMEWORK_REDIS_PASSWORD)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH" ]]; then
  export FILE_VAR="file:$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH"; yq -i '.singleServerConfig.sslTruststore=env(FILE_VAR)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  export EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD; yq -i '.singleServerConfig.sslTruststorePassword=env(EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "$EVENTS_FRAMEWORK_USE_SENTINEL" == "true" ]]; then
  yq -i 'del(.singleServerConfig)' $ENTERPRISE_REDISSON_CACHE_FILE

  if [[ "" != "$EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME" ]]; then
    export EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME; yq -i '.sentinelServersConfig.masterName=env(EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME)' $ENTERPRISE_REDISSON_CACHE_FILE
  fi

  if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
    IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
    INDEX=0
    for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
      export REDIS_SENTINEL_URL; export INDEX; yq -i '.sentinelServersConfig.sentinelAddresses.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $ENTERPRISE_REDISSON_CACHE_FILE
      INDEX=$(expr $INDEX + 1)
    done
  fi
fi

replace_key_value cdTsDbRetentionPeriodMonths "$CD_TSDB_RETENTION_PERIOD_MONTHS"
replace_key_value cacheConfig.cacheNamespace $CACHE_NAMESPACE
replace_key_value cacheConfig.cacheBackend $CACHE_BACKEND
replace_key_value cacheConfig.enterpriseCacheEnabled $ENTERPRISE_CACHE_ENABLED

replace_key_value eventsFramework.redis.sentinel $EVENTS_FRAMEWORK_USE_SENTINEL
replace_key_value eventsFramework.redis.envNamespace $EVENTS_FRAMEWORK_ENV_NAMESPACE
replace_key_value eventsFramework.redis.redisUrl $EVENTS_FRAMEWORK_REDIS_URL
replace_key_value eventsFramework.redis.masterName $EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME
replace_key_value eventsFramework.redis.userName $EVENTS_FRAMEWORK_REDIS_USERNAME
replace_key_value eventsFramework.redis.password $EVENTS_FRAMEWORK_REDIS_PASSWORD
replace_key_value eventsFramework.redis.nettyThreads $EVENTS_FRAMEWORK_NETTY_THREADS
replace_key_value eventsFramework.redis.sslConfig.enabled $EVENTS_FRAMEWORK_REDIS_SSL_ENABLED
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePath $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePassword $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD
replace_key_value eventsFramework.redis.retryAttempts $REDIS_RETRY_ATTEMPTS
replace_key_value eventsFramework.redis.retryInterval $REDIS_RETRY_INTERVAL

replace_key_value ceAwsSetupConfig.accessKey $CE_AWS_ACCESS_KEY

replace_key_value ceAwsSetupConfig.secretKey $CE_AWS_SECRET_KEY

replace_key_value ceAwsSetupConfig.destinationBucket $CE_AWS_DESTINATION_BUCKET

replace_key_value ceAwsSetupConfig.templateURL $CE_AWS_TEMPLATE_URL

replace_key_value ceGcpSetupConfig.gcpProjectId $CE_SETUP_CONFIG_GCP_PROJECT_ID

replace_key_value accessControlClient.enableAccessControl "$ACCESS_CONTROL_ENABLED"

replace_key_value accessControlClient.accessControlServiceConfig.baseUrl "$ACCESS_CONTROL_BASE_URL"

replace_key_value accessControlClient.accessControlServiceSecret "$ACCESS_CONTROL_SECRET"

replace_key_value accessControlAdminClient.accessControlServiceConfig.baseUrl "$ACCESS_CONTROL_BASE_URL"

replace_key_value accessControlAdminClient.accessControlServiceSecret "$ACCESS_CONTROL_SECRET"

replace_key_value outboxPollConfig.initialDelayInSeconds "$OUTBOX_POLL_INITIAL_DELAY"

replace_key_value outboxPollConfig.pollingIntervalInSeconds "$OUTBOX_POLL_INTERVAL"

replace_key_value outboxPollConfig.maximumRetryAttemptsForAnEvent "$OUTBOX_MAX_RETRY_ATTEMPTS"

replace_key_value notificationClient.httpClient.baseUrl "$NOTIFICATION_BASE_URL"

replace_key_value notificationClient.secrets.notificationClientSecret "$NEXT_GEN_MANAGER_SECRET"

replace_key_value notificationClient.messageBroker.uri "${NOTIFICATION_MONGO_URI//\\&/&}"

replace_key_value accessControlAdminClient.mockAccessControlService "${MOCK_ACCESS_CONTROL_SERVICE:-true}"

replace_key_value gitSdkConfiguration.scmConnectionConfig.url "$SCM_SERVICE_URL"

replace_key_value resourceGroupClientConfig.serviceConfig.baseUrl "$RESOURCE_GROUP_BASE_URL"

replace_key_value resourceGroupClientConfig.secret "$NEXT_GEN_MANAGER_SECRET"

replace_key_value baseUrls.currentGenUiUrl "$CURRENT_GEN_UI_URL"
replace_key_value baseUrls.nextGenUiUrl "$NEXT_GEN_UI_URL"
replace_key_value baseUrls.nextGenAuthUiUrl "$NG_AUTH_UI_URL"
replace_key_value baseUrls.webhookBaseUrl "$WEBHOOK_BASE_URL"

replace_key_value ngAuthUIEnabled "$HARNESS_ENABLE_NG_AUTH_UI_PLACEHOLDER"

replace_key_value exportMetricsToStackDriver "$EXPORT_METRICS_TO_STACK_DRIVER"

replace_key_value signupNotificationConfiguration.projectId "$SIGNUP_NOTIFICATION_GCS_PROJECT_ID"
replace_key_value signupNotificationConfiguration.bucketName "$SIGNUP_NOTIFICATION_GCS_BUCKET_NAME"

replace_key_value segmentConfiguration.enabled "$SEGMENT_ENABLED"
replace_key_value segmentConfiguration.loggingEnabled "$SEGMENT_LOGGING_ENABLED"
replace_key_value segmentConfiguration.url "$SEGMENT_URL"
replace_key_value segmentConfiguration.apiKey "$SEGMENT_APIKEY"
replace_key_value segmentConfiguration.certValidationRequired "$SEGMENT_VERIFY_CERT"

replace_key_value accountConfig.deploymentClusterName "$DEPLOYMENT_CLUSTER_NAME"

replace_key_value gitGrpcClientConfigs.pms.target "$PMS_GITSYNC_TARGET"
replace_key_value gitGrpcClientConfigs.pms.authority "$PMS_GITSYNC_AUTHORITY"

replace_key_value gitGrpcClientConfigs.templateservice.target "$TEMPLATE_GITSYNC_TARGET"
replace_key_value gitGrpcClientConfigs.templateservice.authority "$TEMPLATE_GITSYNC_AUTHORITY"

replace_key_value gitGrpcClientConfigs.cf.target "$CF_GITSYNC_TARGET"
replace_key_value gitGrpcClientConfigs.cf.authority "$CF_GITSYNC_AUTHORITY"

replace_key_value cfClientConfig.apiKey "$CF_CLIENT_API_KEY"
replace_key_value cfClientConfig.configUrl "$CF_CLIENT_CONFIG_URL"
replace_key_value cfClientConfig.eventUrl "$CF_CLIENT_EVENT_URL"
replace_key_value cfClientConfig.analyticsEnabled "$CF_CLIENT_ANALYTICS_ENABLED"
replace_key_value cfClientConfig.connectionTimeout "$CF_CLIENT_CONNECTION_TIMEOUT"
replace_key_value cfClientConfig.readTimeout "$CF_CLIENT_READ_TIMEOUT"
replace_key_value cfClientConfig.bufferSize "$CF_CLIENT_BUFFER_SIZE"
replace_key_value featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"
replace_key_value ceAzureSetupConfig.azureAppClientId "$AZURE_APP_CLIENT_ID"
replace_key_value ceAzureSetupConfig.azureAppClientSecret "$AZURE_APP_CLIENT_SECRET"
replace_key_value ceAzureSetupConfig.azureStorageSuffix "$AZURE_STORAGE_SUFFIX"
replace_key_value ceAzureSetupConfig.azureAuthorityHost "$AZURE_AUTHORITY_HOST"
replace_key_value pipelineServiceClientConfig.baseUrl "$PIPELINE_SERVICE_CLIENT_BASEURL"
replace_key_value dbOpsServiceClientConfig.baseUrl "$DBOPS_SERVICE_CLIENT_BASEURL"
replace_key_value ciManagerClientConfig.baseUrl "$CI_MANAGER_SERVICE_CLIENT_BASEURL"
replace_key_value harnessRegistryServiceClientConfig.baseUrl "$HARNESS_REGISTRY_SERVICE_CLIENT_BASEURL"
replace_key_value scopeAccessCheckEnabled "${SCOPE_ACCESS_CHECK:-true}"

replace_key_value enforcementClientConfiguration.enforcementCheckEnabled "$ENFORCEMENT_CHECK_ENABLED"
replace_key_value secretsConfiguration.gcpSecretManagerProject "$GCP_SECRET_MANAGER_PROJECT"
replace_key_value secretsConfiguration.secretResolutionEnabled "$RESOLVE_SECRETS"

replace_key_value opaServerConfig.baseUrl "$OPA_SERVER_BASEURL"
replace_key_value opaServerConfig.secret "$OPA_SERVER_SECRET"


replace_key_value gitopsResourceClientConfig.config.baseUrl "$GITOPS_SERVICE_CLIENT_BASEURL"
replace_key_value gitopsResourceClientConfig.secret "$GITOPS_SERVICE_SECRET"

replace_key_value subscriptionConfig.stripeApiKey "$STRIPE_API_KEY"

replace_key_value enableOpentelemetry "$ENABLE_OPENTELEMETRY"
replace_key_value enableLoopDetection "$ENABLE_LOOPDETECTION"
replace_key_value loopDetectionThreshold "$LOOP_DETECTION_THRESHOLD"

replace_key_value chaosServiceClientConfig.baseUrl "$CHAOS_SERVICE_BASE_URL"

replace_key_value proxy.enabled "$PROXY_ENABLED"
replace_key_value proxy.host "$PROXY_HOST"
replace_key_value proxy.port "$PROXY_PORT"
replace_key_value proxy.username "$PROXY_USERNAME"
replace_key_value proxy.password "$PROXY_PASSWORD"
replace_key_value proxy.protocol "$PROXY_PROTOCOL"

replace_key_value awsServiceEndpointUrls.enabled "$AWS_SERVICE_ENDPOINT_URLS_ENABLED"
replace_key_value awsServiceEndpointUrls.endPointRegion "$AWS_SERVICE_ENDPOINT_URLS_ENDPOINT_REGION"
replace_key_value awsServiceEndpointUrls.stsEndPointUrl "$AWS_SERVICE_ENDPOINT_URLS_STS_ENDPOINT_URL"
replace_key_value awsServiceEndpointUrls.ecsEndPointUrl "$AWS_SERVICE_ENDPOINT_URLS_ECS_ENDPOINT_URL"
replace_key_value awsServiceEndpointUrls.cloudwatchEndPointUrl "$AWS_SERVICE_ENDPOINT_URLS_CLOUDWATCH_ENDPOINT_URL"
replace_key_value awsServiceEndpointUrls.bcmDataExportsEndPointUrl "$AWS_SERVICE_ENDPOINT_URLS_BCM_DATA_EXPORTS_ENDPOINT_URL"

replace_key_value enablePaginatedQueryOnTimescale "$ENABLE_PAGINATED_QUERY_ON_TIMESCALE"
#Changes to use internal connection urls for PMS client gRPC
replace_key_value pmsGrpcClientConfig.target "$INTERNAL_PMS_TARGET"
replace_key_value pmsGrpcClientConfig.authority "$INTERNAL_PMS_AUTHORITY"

replace_key_value grpcClient.target "$INTERNAL_MANAGER_TARGET"
replace_key_value grpcClient.authority "$INTERNAL_MANAGER_AUTHORITY"
#Changes to use internal connection urls for GitSync gRPC
replace_key_value gitGrpcClientConfigs.templateservice.target "$INTERNAL_TEMPLATE_GITSYNC_TARGET"
replace_key_value gitGrpcClientConfigs.templateservice.authority "$INTERNAL_TEMPLATE_GITSYNC_AUTHORITY"
replace_key_value gitGrpcClientConfigs.core.target "$INTERNAL_NG_MANAGER_TARGET"
replace_key_value gitGrpcClientConfigs.core.authority "$INTERNAL_NG_MANAGER_AUTHORITY"
replace_key_value gitSdkConfiguration.gitManagerGrpcClientConfig.target "$INTERNAL_NG_MANAGER_TARGET"
replace_key_value gitSdkConfiguration.gitManagerGrpcClientConfig.authority "$INTERNAL_NG_MANAGER_AUTHORITY"
replace_key_value gitGrpcClientConfigs.pms.target "$INTERNAL_PMS_GITSYNC_TARGET"
replace_key_value gitGrpcClientConfigs.pms.authority "$INTERNAL_PMS_GITSYNC_AUTHORITY"
replace_key_value gitGrpcClientConfigs.cf.target "$INTERNAL_CF_GITSYNC_TARGET"
replace_key_value gitGrpcClientConfigs.cf.authority "$INTERNAL_CF_GITSYNC_AUTHORITY"

replace_key_value secondaryTimescaledb.timescaledbUrl "$SECONDARY_TIMESCALE_URI"
replace_key_value secondaryTimescaledb.timescaledbUsername "$SECONDARY_TIMESCALEDB_USERNAME"
replace_key_value secondaryTimescaledb.timescaledbPassword "$SECONDARY_TIMESCALE_PASSWORD"
replace_key_value secondaryTimescaledb.sslMode "$SECONDARY_TIMESCALEDB_SSL_MODE"
replace_key_value secondaryTimescaledb.sslRootCert "$SECONDARY_TIMESCALEDB_SSL_ROOT_CERT"

replace_key_value deploymentCountBQConfig.projectId "$BIG_QUERY_GCP_PROJECT_ID"
replace_key_value deploymentCountBQConfig.dataset "$BIG_QUERY_DEPLOYMENT_COUNT_DATA_SET"
replace_key_value deploymentCountBQConfig.totalDeploymentsTableName "$BIG_QUERY_DEPLOYMENT_COUNT_TABLE"

# CD Plugin Image Overrides
replace_key_value pluginExecutionConfig.gitCloneConfig.image "$CD_GIT_CLONE_IMAGE"
replace_key_value pluginExecutionConfig.downloadAwsS3Config.image "$CD_DOWNLOAD_AWS_S3_IMAGE"
replace_key_value pluginExecutionConfig.downloadHarnessStoreConfig.image "$CD_DOWNLOAD_HARNESS_STORE_IMAGE"
replace_key_value pluginExecutionConfig.downloadGcsConfig.image "$CD_DOWNLOAD_GCS_IMAGE"
replace_key_value pluginExecutionConfig.samBuildStepImageConfig.image "$CD_SAM_BUILD_IMAGE"
replace_key_value pluginExecutionConfig.samDeployStepImageConfig.image "$CD_SAM_DEPLOY_IMAGE"
replace_key_value pluginExecutionConfig.serverlessBaseRepository "$CD_SERVERLESS_BASE_REPOSITORY"
