#!/usr/bin/env bash
# Copyright 2023 Harness Inc. All rights reserved.
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

yq -i 'del(.server.applicationConnectors.[] | select(.type == "https"))' $CONFIG_FILE
yq -i '.server.adminConnectors=[]' $CONFIG_FILE

yq -i 'del(.pmsSdkGrpcServerConfig.connectors.[] | select(.secure == true))' $CONFIG_FILE

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

# Logging File Appender Configurations
if [[ "" != "$LOG_FILENAME" ]]; then
  export filename="${LOG_FILENAME%.*}.%d{yyyy-MM-dd}.%i.${LOG_FILENAME##*.}"
  yq -i '(.logging.appenders[] | select(.type == "file") | .archivedLogFilenamePattern) = env(filename)' $CONFIG_FILE
fi
if [[ "" == "$FILE_LOGGING_ENABLED" || "$FILE_LOGGING_ENABLED" != "true" ]]; then
  yq -i 'del(.logging.appenders[] | select(.type == "file"))' $CONFIG_FILE
fi

if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  export SERVER_MAX_THREADS; yq -i '.server.maxThreads=env(SERVER_MAX_THREADS)' $CONFIG_FILE
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

if [[ "" != "$PMS_TARGET" ]]; then
  export PMS_TARGET; yq -i '.pmsGrpcClientConfig.target=env(PMS_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$PMS_AUTHORITY" ]]; then
  export PMS_AUTHORITY; yq -i '.pmsGrpcClientConfig.authority=env(PMS_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$SHOULD_CONFIGURE_WITH_PMS" ]]; then
  export SHOULD_CONFIGURE_WITH_PMS; yq -i '.shouldConfigureWithPMS=env(SHOULD_CONFIGURE_WITH_PMS)' $CONFIG_FILE
fi

if [[ "" != "$DISTRIBUTED_LOCK_IMPLEMENTATION" ]]; then
  export DISTRIBUTED_LOCK_IMPLEMENTATION; yq -i '.distributedLockImplementation=env(DISTRIBUTED_LOCK_IMPLEMENTATION)' $CONFIG_FILE
fi

if [[ "" != "$LOG_STREAMING_SERVICE_BASEURL" ]]; then
  export LOG_STREAMING_SERVICE_BASEURL; yq -i '.logStreamingServiceConfig.baseUrl=env(LOG_STREAMING_SERVICE_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$LOG_STREAMING_SERVICE_TOKEN" ]]; then
  export LOG_STREAMING_SERVICE_TOKEN; yq -i '.logStreamingServiceConfig.serviceToken=env(LOG_STREAMING_SERVICE_TOKEN)' $CONFIG_FILE
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders.[] | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders.[] | select(.type == "gke-console") | .stackdriverLogEnabled) = true' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_SERVICE_SECRET" ]]; then
  export NG_MANAGER_SERVICE_SECRET; yq -i '.ngManagerServiceSecret=env(NG_MANAGER_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$RHS_ENABLED" ]]; then
  export RHS_ENABLED; yq -i '.rhsEnabled=env(RHS_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$SCS_CUTOVER_ENABLED" ]]; then
  export SCS_CUTOVER_ENABLED; yq -i '.scsCutoverEnabled=env(SCS_CUTOVER_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$SCS_SERVICE_SECRET" ]]; then
  export SCS_SERVICE_SECRET; yq -i '.scsServiceSecret=env(SCS_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$RESOURCE_HIERARCHY_SERVICE_SECRET" ]]; then
  export RESOURCE_HIERARCHY_SERVICE_SECRET; yq -i '.rhsServiceSecret=env(RESOURCE_HIERARCHY_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_SERVICE_SECRET" ]]; then
  export MANAGER_SERVICE_SECRET; yq -i '.managerServiceSecret=env(MANAGER_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_BASE_URL" ]]; then
  export NG_MANAGER_BASE_URL; yq -i '.ngManagerServiceHttpClientConfig.baseUrl=env(NG_MANAGER_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$RHS_CLIENT_BASE_URL" ]]; then
  export RHS_CLIENT_BASE_URL; yq -i '.rhsClientConfig.baseUrl=env(RHS_CLIENT_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$SCS_CLIENT_BASE_URL" ]]; then
  export SCS_CLIENT_BASE_URL; yq -i '.scsClientConfig.baseUrl=env(SCS_CLIENT_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$PLATFORM_CONFIG_SERVICE_CLIENT_BASE_URL" ]]; then
  export PLATFORM_CONFIG_SERVICE_CLIENT_BASE_URL; yq -i '.platformConfigServiceClientConfig.baseUrl=env(PLATFORM_CONFIG_SERVICE_CLIENT_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$PLATFORM_CONFIG_SERVICE_ENABLED" ]]; then
  export PLATFORM_CONFIG_SERVICE_ENABLED; yq -i '.platformConfigServiceEnabled=env(PLATFORM_CONFIG_SERVICE_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$PLATFORM_CONFIG_SERVICE_SECRET" ]]; then
  export PLATFORM_CONFIG_SERVICE_SECRET; yq -i '.platformConfigServiceSecret=env(PLATFORM_CONFIG_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_CLIENT_BASE_URL" ]]; then
  export MANAGER_CLIENT_BASE_URL; yq -i '.managerClientConfig.baseUrl=env(MANAGER_CLIENT_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_BASE_URL" ]]; then
  export ACCESS_CONTROL_BASE_URL; yq -i '.accessControlClient.accessControlServiceConfig.baseUrl=env(ACCESS_CONTROL_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_SECRET" ]]; then
  export ACCESS_CONTROL_SECRET; yq -i '.accessControlClient.accessControlServiceSecret=env(ACCESS_CONTROL_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$ACCESS_CONTROL_ENABLED" ]]; then
  export ACCESS_CONTROL_ENABLED; yq -i '.accessControlClient.enableAccessControl=env(ACCESS_CONTROL_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$BACKSTAGE_BASE_URL" ]]; then
  export BACKSTAGE_BASE_URL; yq -i '.backstageHttpClientConfig.baseUrl=env(BACKSTAGE_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$IDP_AGENT_BASE_URL" ]]; then
  export IDP_AGENT_BASE_URL; yq -i '.idpAgentHttpClientConfig.baseUrl=env(IDP_AGENT_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$BACKSTAGE_SERVICE_SECRET" ]]; then
  export BACKSTAGE_SERVICE_SECRET; yq -i '.backstageServiceSecret=env(BACKSTAGE_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$IDP_SERVICE_SECRET" ]]; then
  export IDP_SERVICE_SECRET; yq -i '.idpServiceSecret=env(IDP_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$IDP_ENCRYPTION_SECRET" ]]; then
  export IDP_ENCRYPTION_SECRET; yq -i '.idpEncryptionSecret=env(IDP_ENCRYPTION_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$IDP_AUTOMATION_GITHUB_TOKEN" ]]; then
  export IDP_AUTOMATION_GITHUB_TOKEN; yq -i '.idpAutomationGitHubToken=env(IDP_AUTOMATION_GITHUB_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$IDP_AUTOMATION_X_API_KEY" ]]; then
  export IDP_AUTOMATION_X_API_KEY; yq -i '.idpAutomationXApiKey=env(IDP_AUTOMATION_X_API_KEY)' $CONFIG_FILE
fi

if [[ "" != "$JWT_EXTERNAL_SERVICE_SECRET" ]]; then
  export JWT_EXTERNAL_SERVICE_SECRET; yq -i '.jwtExternalServiceSecret=env(JWT_EXTERNAL_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_TARGET" ]]; then
  export MANAGER_TARGET; yq -i '.managerTarget=env(MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_AUTHORITY" ]]; then
  export MANAGER_AUTHORITY; yq -i '.managerAuthority=env(MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$PROXY_ALLOW_LIST_CONFIG_SERVICES" ]]; then
  export PROXY_ALLOW_LIST_CONFIG_SERVICES; yq -i '.proxyAllowList.services=env(PROXY_ALLOW_LIST_CONFIG_SERVICES)' $CONFIG_FILE
fi

if [[ "" != "$CPU" ]]; then
  export CPU; yq -i '.cpu=env(CPU)' $CONFIG_FILE
fi

if [[ "" != "$SCORE_COMPUTER_THREADS_PER_CORE_FOR_ITERATOR" ]]; then
  export SCORE_COMPUTER_THREADS_PER_CORE_FOR_ITERATOR; yq -i '.scoreComputerThreadsPerCoreForIterator=env(SCORE_COMPUTER_THREADS_PER_CORE_FOR_ITERATOR)' $CONFIG_FILE
fi

if [[ "" != "$SCORE_COMPUTER_THREADS_PER_CORE_FOR_USER" ]]; then
  export SCORE_COMPUTER_THREADS_PER_CORE_FOR_USER; yq -i '.scoreComputerThreadsPerCoreForUser=env(SCORE_COMPUTER_THREADS_PER_CORE_FOR_USER)' $CONFIG_FILE
fi

if [[ "" != "AGGREGATION_RULE_COMPUTE_THREADS_PER_CORE" ]]; then
  export AGGREGATION_RULE_COMPUTE_THREADS_PER_CORE; yq -i '.aggregationRuleComputeThreadsPerCore=env(AGGREGATION_RULE_COMPUTE_THREADS_PER_CORE)' $CONFIG_FILE
fi


if [[ "" != "$IDP_ENCRYPTION_SECRET" ]]; then
  export IDP_ENCRYPTION_SECRET; yq -i '.idpEncryptionSecret=env(IDP_ENCRYPTION_SECRET)' $CONFIG_FILE
fi

yq -i 'del(.codec)' $REDISSON_CACHE_FILE

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.redisLockConfig.useScriptCache=false' $CONFIG_FILE
  yq -i '.useScriptCache=false' $REDISSON_CACHE_FILE
fi

if [[ "" != "$LOG_SERVICE_ENDPOINT" ]]; then
  export LOG_SERVICE_ENDPOINT; yq -i '.logServiceConfig.baseUrl=env(LOG_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$LOG_SERVICE_GLOBAL_TOKEN" ]]; then
  export LOG_SERVICE_GLOBAL_TOKEN; yq -i '.logServiceConfig.globalToken=env(LOG_SERVICE_GLOBAL_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$TI_SERVICE_ENDPOINT" ]]; then
  export TI_SERVICE_ENDPOINT; yq -i '.tiServiceConfig.baseUrl=env(TI_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$TI_SERVICE_GLOBAL_TOKEN" ]]; then
  export TI_SERVICE_GLOBAL_TOKEN; yq -i '.tiServiceConfig.globalToken=env(TI_SERVICE_GLOBAL_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_SERVICE_ENDPOINT" ]]; then
  export SSCA_SERVICE_ENDPOINT; yq -i '.sscaServiceConfig.httpClientConfig.baseUrl=env(SSCA_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_SERVICE_SECRET" ]]; then
  export SSCA_SERVICE_SECRET; yq -i '.sscaServiceConfig.serviceSecret=env(SSCA_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$STO_SERVICE_REST_CLIENT_BASEURL" ]]; then
  export STO_SERVICE_REST_CLIENT_BASEURL; yq -i '.stoServiceRestClientConfig.baseUrl=env(STO_SERVICE_REST_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$STO_SERVICE_ENDPOINT" ]]; then
  export STO_SERVICE_ENDPOINT; yq -i '.stoServiceConfig.baseUrl=env(STO_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$STO_SERVICE_INTERNAL_ENDPOINT" ]]; then
  export STO_SERVICE_INTERNAL_ENDPOINT; yq -i '.stoServiceConfig.internalUrl=env(STO_SERVICE_INTERNAL_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$STO_SERVICE_GLOBAL_TOKEN" ]]; then
  export STO_SERVICE_GLOBAL_TOKEN; yq -i '.stoServiceConfig.globalToken=env(STO_SERVICE_GLOBAL_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$API_URL" ]]; then
  export API_URL; yq -i '.apiUrl=env(API_URL)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_GITSYNC_TARGET" ]]; then
  export NG_MANAGER_GITSYNC_TARGET; yq -i '.gitSdkConfiguration.gitManagerGrpcClientConfig.target=env(NG_MANAGER_GITSYNC_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_GITSYNC_AUTHORITY" ]]; then
  export NG_MANAGER_GITSYNC_AUTHORITY; yq -i '.gitSdkConfiguration.gitManagerGrpcClientConfig.authority=env(NG_MANAGER_GITSYNC_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$SCM_SERVICE_URI" ]]; then
  export SCM_SERVICE_URI; yq -i '.gitSdkConfiguration.scmConnectionConfig.url=env(SCM_SERVICE_URI)' $CONFIG_FILE
fi

if [[ "" != "$ADDON_IMAGE" ]]; then
  export ADDON_IMAGE; yq -i '.ciExecutionServiceConfig.addonImage=env(ADDON_IMAGE)' $CONFIG_FILE
fi
if [[ "" != "$LE_IMAGE" ]]; then
  export LE_IMAGE; yq -i '.ciExecutionServiceConfig.liteEngineImage=env(LE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$ADDON_IMAGE_ROOTLESS" ]]; then
  export ADDON_IMAGE_ROOTLESS; yq -i '.ciExecutionServiceConfig.addonImageRootless=env(ADDON_IMAGE_ROOTLESS)' $CONFIG_FILE
fi

if [[ "" != "$LE_IMAGE_ROOTLESS" ]]; then
  export LE_IMAGE_ROOTLESS; yq -i '.ciExecutionServiceConfig.liteEngineImageRootless=env(LE_IMAGE_ROOTLESS)' $CONFIG_FILE
fi

if [[ "" != "$GIT_CLONE_IMAGE" ]]; then
  export GIT_CLONE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.gitCloneConfig.image=env(GIT_CLONE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$DOCKER_PUSH_IMAGE" ]]; then
  export DOCKER_PUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushDockerRegistryConfig.image=env(DOCKER_PUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$ECR_PUSH_IMAGE" ]]; then
  export ECR_PUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushECRConfig.image=env(ECR_PUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$GCR_PUSH_IMAGE" ]]; then
  export GCR_PUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushGCRConfig.image=env(GCR_PUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$GCS_UPLOAD_IMAGE" ]]; then
  export GCS_UPLOAD_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.gcsUploadConfig.image=env(GCS_UPLOAD_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$S3_UPLOAD_IMAGE" ]]; then
  export S3_UPLOAD_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.s3UploadConfig.image=env(S3_UPLOAD_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$SECURITY_IMAGE" ]]; then
  export SECURITY_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.securityConfig.image=env(SECURITY_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$ARTIFACTORY_UPLOAD_IMAGE" ]]; then
  export ARTIFACTORY_UPLOAD_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.artifactoryUploadConfig.image=env(ARTIFACTORY_UPLOAD_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$GCS_CACHE_IMAGE" ]]; then
  export GCS_CACHE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.cacheGCSConfig.image=env(GCS_CACHE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$S3_CACHE_IMAGE" ]]; then
  export S3_CACHE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.cacheS3Config.image=env(S3_CACHE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$GENERIC_CACHE_IMAGE" ]]; then
  export GENERIC_CACHE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.cacheConfig.image=env(GENERIC_CACHE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_GIT_CLONE_IMAGE" ]]; then
  export VM_GIT_CLONE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.gitClone=env(VM_GIT_CLONE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_DOCKER_PUSH_IMAGE" ]]; then
  export VM_DOCKER_PUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushDockerRegistry=env(VM_DOCKER_PUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_ECR_PUSH_IMAGE" ]]; then
  export VM_ECR_PUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushECR=env(VM_ECR_PUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_GCR_PUSH_IMAGE" ]]; then
  export VM_GCR_PUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushGCR=env(VM_GCR_PUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_GCS_UPLOAD_IMAGE" ]]; then
  export VM_GCS_UPLOAD_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.gcsUpload=env(VM_GCS_UPLOAD_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_S3_UPLOAD_IMAGE" ]]; then
  export VM_S3_UPLOAD_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.s3Upload=env(VM_S3_UPLOAD_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_SECURITY_IMAGE" ]]; then
  export VM_SECURITY_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.security=env(VM_SECURITY_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IDP_COOKIECUTTER_IMAGE" ]]; then
  export IDP_COOKIECUTTER_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.cookieCutter.image=env(IDP_COOKIECUTTER_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IDP_COOKICUTTER_IMAGE_VM" ]]; then
  export IDP_COOKICUTTER_IMAGE_VM; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.cookieCutter=env(IDP_COOKICUTTER_IMAGE_VM)' $CONFIG_FILE
fi

if [[ "" != "$IDP_CREATEREPO_IMAGE" ]]; then
  export IDP_CREATEREPO_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.createRepo.image=env(IDP_CREATEREPO_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IDP_CREATEREPO_IMAGE_VM" ]]; then
  export IDP_CREATEREPO_IMAGE_VM; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.createRepo=env(IDP_CREATEREPO_IMAGE_VM)' $CONFIG_FILE
fi

if [[ "" != "$IDP_DIRECTPUSH_IMAGE" ]]; then
  export IDP_DIRECTPUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.directPush.image=env(IDP_DIRECTPUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IDP_DIRECTPUSH_IMAGE_VM" ]]; then
  export IDP_DIRECTPUSH_IMAGE_VM; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.directPush=env(IDP_DIRECTPUSH_IMAGE_VM)' $CONFIG_FILE
fi

if [[ "" != "$IDP_REGISTERCATALOG_IMAGE" ]]; then
  export IDP_REGISTERCATALOG_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.registerCatalog.image=env(IDP_REGISTERCATALOG_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IDP_REGISTERCATALOG_IMAGE_VM" ]]; then
  export IDP_REGISTERCATALOG_IMAGE_VM; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.registerCatalog=env(IDP_REGISTERCATALOG_IMAGE_VM)' $CONFIG_FILE
fi

if [[ "" != "$IDP_CREATECATALOG_IMAGE" ]]; then
  export IDP_CREATECATALOG_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.createCatalog.image=env(IDP_CREATECATALOG_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IDP_CREATECATALOG_IMAGE_VM" ]]; then
  export IDP_CREATECATALOG_IMAGE_VM; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.createCatalog=env(IDP_CREATECATALOG_IMAGE_VM)' $CONFIG_FILE
fi

if [[ "" != "$IDP_SLACKNOTIFY_IMAGE" ]]; then
  export IDP_SLACKNOTIFY_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.slackNotify.image=env(IDP_SLACKNOTIFY_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IDP_SLACKNOTIFY_IMAGE_VM" ]]; then
  export IDP_SLACKNOTIFY_IMAGE_VM; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.slackNotify=env(IDP_SLACKNOTIFY_IMAGE_VM)' $CONFIG_FILE
fi

if [[ "" != "$IDP_CREATE_ORGANISATION_IMAGE" ]]; then
  export IDP_CREATE_ORGANISATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.createOrganisation.image=env(IDP_CREATE_ORGANISATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IDP_CREATE_ORGANISATION_IMAGE_VM" ]]; then
  export IDP_CREATE_ORGANISATION_IMAGE_VM; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.createOrganisation=env(IDP_CREATE_ORGANISATION_IMAGE_VM)' $CONFIG_FILE
fi

if [[ "" != "$IDP_CREATE_PROJECT_IMAGE" ]]; then
  export IDP_CREATE_PROJECT_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.createProject.image=env(IDP_CREATE_PROJECT_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IDP_CREATE_PROJECT_IMAGE_VM" ]]; then
  export IDP_CREATE_PROJECT_IMAGE_VM; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.createProject=env(IDP_CREATE_PROJECT_IMAGE_VM)' $CONFIG_FILE
fi

if [[ "" != "$IDP_CREATE_RESOURCE_IMAGE" ]]; then
  export IDP_CREATE_RESOURCE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.createResource.image=env(IDP_CREATE_RESOURCE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IDP_CREATE_RESOURCE_IMAGE_VM" ]]; then
  export IDP_CREATE_RESOURCE_IMAGE_VM; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.createResource=env(IDP_CREATE_RESOURCE_IMAGE_VM)' $CONFIG_FILE
fi

if [[ "" != "$IDP_UPDATE_CATALOG_PROPERTY_IMAGE" ]]; then
  export IDP_UPDATE_CATALOG_PROPERTY_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.updateCatalogProperty.image=env(IDP_UPDATE_CATALOG_PROPERTY_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IDP_UPDATE_CATALOG_PROPERTY_IMAGE_VM" ]]; then
  export IDP_UPDATE_CATALOG_PROPERTY_IMAGE_VM; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.updateCatalogProperty=env(IDP_UPDATE_CATALOG_PROPERTY_IMAGE_VM)' $CONFIG_FILE
fi

if [[ "" != "$CACHE_BUCKET" ]]; then
  export CACHE_BUCKET; yq -i '.ciExecutionServiceConfig.cacheIntelligenceConfig.bucket=env(CACHE_BUCKET)' $CONFIG_FILE
fi

if [[ "" != "$CACHE_SERVICE_KEY" ]]; then
  export CACHE_SERVICE_KEY; yq -i '.ciExecutionServiceConfig.cacheIntelligenceConfig.serviceKey=env(CACHE_SERVICE_KEY)' $CONFIG_FILE
fi

if [[ "" != "$VM_ARTIFACTORY_UPLOAD_IMAGE" ]]; then
  export VM_ARTIFACTORY_UPLOAD_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.artifactoryUpload=env(VM_ARTIFACTORY_UPLOAD_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_GCS_CACHE_IMAGE" ]]; then
  export VM_GCS_CACHE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.cacheGCS=env(VM_GCS_CACHE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_S3_CACHE_IMAGE" ]]; then
  export VM_S3_CACHE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.cacheS3=env(VM_S3_CACHE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$DEFAULT_MEMORY_LIMIT" ]]; then
  export DEFAULT_MEMORY_LIMIT; yq -i '.ciExecutionServiceConfig.defaultMemoryLimit=env(DEFAULT_MEMORY_LIMIT)' $CONFIG_FILE
fi

if [[ "" != "$DEFAULT_CPU_LIMIT" ]]; then
  export DEFAULT_CPU_LIMIT; yq -i '.ciExecutionServiceConfig.defaultCPULimit=env(DEFAULT_CPU_LIMIT)' $CONFIG_FILE
fi

if [[ "" != "$DEFAULT_INTERNAL_IMAGE_CONNECTOR" ]]; then
  export DEFAULT_INTERNAL_IMAGE_CONNECTOR; yq -i '.ciExecutionServiceConfig.defaultInternalImageConnector=env(DEFAULT_INTERNAL_IMAGE_CONNECTOR)' $CONFIG_FILE
fi

if [[ "" != "$PVC_DEFAULT_STORAGE_SIZE" ]]; then
  export PVC_DEFAULT_STORAGE_SIZE; yq -i '.ciExecutionServiceConfig.pvcDefaultStorageSize=env(PVC_DEFAULT_STORAGE_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$DELEGATE_SERVICE_ENDPOINT_VARIABLE_VALUE" ]]; then
  export DELEGATE_SERVICE_ENDPOINT_VARIABLE_VALUE; yq -i '.ciExecutionServiceConfig.delegateServiceEndpointVariableValue=env(DELEGATE_SERVICE_ENDPOINT_VARIABLE_VALUE)' $CONFIG_FILE
fi

if [[ "" != "$MINING_GCS_PROJECT_ID" ]]; then
  export MINING_GCS_PROJECT_ID; yq -i '.ciExecutionServiceConfig.miningPatternConfig.projectId=env(MINING_GCS_PROJECT_ID)' $CONFIG_FILE
fi

if [[ "" != "$MINING_GCS_BUCKET_NAME" ]]; then
  export MINING_GCS_BUCKET_NAME; yq -i '.ciExecutionServiceConfig.miningPatternConfig.bucketName=env(MINING_GCS_BUCKET_NAME)' $CONFIG_FILE
fi

if [[ "" != "$MINING_GCS_CREDS" ]]; then
  export MINING_GCS_CREDS; yq -i '.ciExecutionServiceConfig.miningPatternConfig.gcsCreds=env(MINING_GCS_CREDS)' $CONFIG_FILE
fi

if [[ "" != "$DEPLOYMENT_TYPE" ]]; then
  export DEPLOYMENT_TYPE; yq -i '.deploymentType=env(DEPLOYMENT_TYPE)' $CONFIG_FILE
fi

if [[ "" != "$DEPLOYMENT_NAMESPACE" ]]; then
  export DEPLOYMENT_NAMESPACE; yq -i '.deploymentNamespace=env(DEPLOYMENT_NAMESPACE)' $CONFIG_FILE
fi

if [[ "" != "$HOSTED_VM_SPLIT_LINUX_AMD64_POOL" ]]; then
  export HOSTED_VM_SPLIT_LINUX_AMD64_POOL; yq -i '.ciExecutionServiceConfig.hostedVmConfig.splitLinuxAmd64Pool=env(HOSTED_VM_SPLIT_LINUX_AMD64_POOL)' $CONFIG_FILE
fi

if [[ "" != "$HOSTED_VM_SPLIT_LINUX_ARM64_POOL" ]]; then
  export HOSTED_VM_SPLIT_LINUX_ARM64_POOL; yq -i '.ciExecutionServiceConfig.hostedVmConfig.splitLinuxArm64Pool=env(HOSTED_VM_SPLIT_LINUX_ARM64_POOL)' $CONFIG_FILE
fi

if [[ "" != "$IACM_SERVICE_ENDPOINT" ]]; then
  export IACM_SERVICE_ENDPOINT; yq -i '.iacmServiceConfig.baseUrl=env(IACM_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$IACM_EXTERNAL_SERVICE_ENDPOINT" ]]; then
  export IACM_EXTERNAL_SERVICE_ENDPOINT; yq -i '.iacmServiceConfig.externalUrl=env(IACM_EXTERNAL_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$IACM_SERVICE_GLOBAL_TOKEN" ]]; then
  export IACM_SERVICE_GLOBAL_TOKEN; yq -i '.iacmServiceConfig.globalToken=env(IACM_SERVICE_GLOBAL_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$PO_SERVER_ENDPOINT" ]]; then
  export PO_SERVER_ENDPOINT; yq -i '.poServerConfig.baseUrl=env(PO_SERVER_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$PO_SERVER_EXTERNAL_SERVICE_ENDPOINT" ]]; then
  export PO_SERVER_EXTERNAL_SERVICE_ENDPOINT; yq -i '.poServerConfig.externalUrl=env(PO_SERVER_EXTERNAL_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$PO_SERVER_GLOBAL_TOKEN" ]]; then
  export PO_SERVER_GLOBAL_TOKEN; yq -i '.poServerConfig.globalToken=env(PO_SERVER_GLOBAL_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$DYNAMIC_CONFIG_RESOLUTION" ]]; then
  export DYNAMIC_CONFIG_RESOLUTION; yq -i '.dynamicConfigResolution=env(DYNAMIC_CONFIG_RESOLUTION)' $CONFIG_FILE
fi

if [[ "" != "$FILE_STORAGE_MODE" ]]; then
  export FILE_STORAGE_MODE; yq -i '.fileServiceConfiguration.fileStorageMode=env(FILE_STORAGE_MODE)' $CONFIG_FILE
fi

if [[ "" != "$FILE_STORAGE_CLUSTER_NAME" ]]; then
  export FILE_STORAGE_CLUSTER_NAME; yq -i '.fileServiceConfiguration.clusterName=env(FILE_STORAGE_CLUSTER_NAME)' $CONFIG_FILE
fi

if [[ "" != "$FREE_DEFAULT_EXECUTION_COUNT" ]]; then
  export $FREE_DEFAULT_EXECUTION_COUNT; yq -i '.ciExecutionServiceConfig.executionLimits.free.defaultTotalExecutionCount=env(FREE_DEFAULT_EXECUTION_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$FREE_DEFAULT_MAC_EXECUTION_COUNT" ]]; then
  export $FREE_DEFAULT_MAC_EXECUTION_COUNT; yq -i '.ciExecutionServiceConfig.executionLimits.free.defaultMacExecutionCount=env(FREE_DEFAULT_MAC_EXECUTION_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$TEAM_DEFAULT_EXECUTION_COUNT" ]]; then
  export $TEAM_DEFAULT_EXECUTION_COUNT; yq -i '.ciExecutionServiceConfig.executionLimits.team.defaultTotalExecutionCount=env(TEAM_DEFAULT_EXECUTION_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$TEAM_DEFAULT_MAC_EXECUTION_COUNT" ]]; then
  export $TEAM_DEFAULT_MAC_EXECUTION_COUNT; yq -i '.ciExecutionServiceConfig.executionLimits.team.defaultMacExecutionCount=env(TEAM_DEFAULT_MAC_EXECUTION_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$ENTERPRISE_DEFAULT_EXECUTION_COUNT" ]]; then
  export $ENTERPRISE_DEFAULT_EXECUTION_COUNT; yq -i '.ciExecutionServiceConfig.executionLimits.enterprise.defaultTotalExecutionCount=env(ENTERPRISE_DEFAULT_EXECUTION_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$ENTERPRISE_DEFAULT_MAC_EXECUTION_COUNT" ]]; then
  export $ENTERPRISE_DEFAULT_MAC_EXECUTION_COUNT; yq -i '.ciExecutionServiceConfig.executionLimits.enterprise.defaultMacExecutionCount=env(ENTERPRISE_DEFAULT_MAC_EXECUTION_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$FREE_NEW_USER_DAILY_MAX_BUILD" ]]; then
  export $FREE_NEW_USER_DAILY_MAX_BUILD; yq -i '.ciExecutionServiceConfig.executionLimits.freeNewUser.dailyMaxBuildsCount=env(FREE_NEW_USER_DAILY_MAX_BUILD)' $CONFIG_FILE
fi

if [[ "" != "$FREE_NEW_USER_MONTHLY_MAX_CREDIT" ]]; then
  export $FREE_NEW_USER_MONTHLY_MAX_CREDIT; yq -i '.ciExecutionServiceConfig.executionLimits.freeNewUser.monthlyMaxCreditsCount=env(FREE_NEW_USER_MONTHLY_MAX_CREDIT)' $CONFIG_FILE
fi

if [[ "" != "$FREE_BASIC_USER_DAILY_MAX_BUILD" ]]; then
  export $FREE_BASIC_USER_DAILY_MAX_BUILD; yq -i '.ciExecutionServiceConfig.executionLimits.freeBasicUser.dailyMaxBuildsCount=env(FREE_BASIC_USER_DAILY_MAX_BUILD)' $CONFIG_FILE
fi

if [[ "" != "$FREE_BASIC_USER_MONTHLY_MAX_CREDIT" ]]; then
  export $FREE_BASIC_USER_MONTHLY_MAX_CREDIT; yq -i '.ciExecutionServiceConfig.executionLimits.freeBasicUser.monthlyMaxCreditsCount=env(FREE_BASIC_USER_MONTHLY_MAX_CREDIT)' $CONFIG_FILE
fi

replace_key_value queryServiceConfig.grpcClientConfig.target "$QUERY_SERVICE_GRPC_TARGET"
replace_key_value queryServiceConfig.grpcClientConfig.authority "$QUERY_SERVICE_GRPC_AUTHORITY"
if [[ "" != "$UDP_INTERNAL_SECRET" ]]; then
  export UDP_INTERNAL_SECRET
  yq -i '.queryServiceConfig.udpInternalSecret=strenv(UDP_INTERNAL_SECRET)' $CONFIG_FILE
fi

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
replace_key_value redisLockConfig.sentinel $LOCK_CONFIG_USE_SENTINEL
replace_key_value redisLockConfig.envNamespace $LOCK_CONFIG_ENV_NAMESPACE
replace_key_value redisLockConfig.redisUrl $LOCK_CONFIG_REDIS_URL
replace_key_value redisLockConfig.masterName $LOCK_CONFIG_SENTINEL_MASTER_NAME
replace_key_value redisLockConfig.userName $LOCK_CONFIG_REDIS_USERNAME
replace_key_value redisLockConfig.password $LOCK_CONFIG_REDIS_PASSWORD
replace_key_value redisLockConfig.nettyThreads $REDIS_NETTY_THREADS
replace_key_value idpAppConfig.primary.masterUrl "$IDP_APP_PRIMARY_MASTER_URL"
replace_key_value idpAppConfig.primary.token "$IDP_APP_PRIMARY_SA_TOKEN"
replace_key_value idpAppConfig.primary.caCrt "$IDP_APP_PRIMARY_SA_CA_CRT"
replace_key_value idpAppConfig.primary.workloadIdentity.enabled "$IDP_APP_PRIMARY_WORKLOAD_IDENTITY_ENABLED"
replace_key_value idpAppConfig.primary.workloadIdentity.project "$IDP_APP_PRIMARY_WORKLOAD_IDENTITY_PROJECT"
replace_key_value idpAppConfig.primary.workloadIdentity.location "$IDP_APP_PRIMARY_WORKLOAD_IDENTITY_LOCATION"
replace_key_value idpAppConfig.primary.workloadIdentity.cluster "$IDP_APP_PRIMARY_WORKLOAD_IDENTITY_CLUSTER"
replace_key_value idpAppConfig.primary.eksAuth.enabled "$IDP_APP_PRIMARY_EKS_AUTH_ENABLED"
replace_key_value idpAppConfig.primary.eksAuth.clusterName "$IDP_APP_PRIMARY_EKS_CLUSTER_NAME"
replace_key_value idpAppConfig.primary.eksAuth.region "$IDP_APP_PRIMARY_EKS_REGION"
replace_key_value idpAppConfig.failover.masterUrl "$IDP_APP_FAILOVER_MASTER_URL"
replace_key_value idpAppConfig.failover.token "$IDP_APP_FAILOVER_SA_TOKEN"
replace_key_value idpAppConfig.failover.caCrt "$IDP_APP_FAILOVER_SA_CA_CRT"
replace_key_value idpAppConfig.failover.workloadIdentity.enabled "$IDP_APP_FAILOVER_WORKLOAD_IDENTITY_ENABLED"
replace_key_value idpAppConfig.failover.workloadIdentity.project "$IDP_APP_FAILOVER_WORKLOAD_IDENTITY_PROJECT"
replace_key_value idpAppConfig.failover.workloadIdentity.location "$IDP_APP_FAILOVER_WORKLOAD_IDENTITY_LOCATION"
replace_key_value idpAppConfig.failover.workloadIdentity.cluster "$IDP_APP_FAILOVER_WORKLOAD_IDENTITY_CLUSTER"
replace_key_value idpAppConfig.failover.eksAuth.enabled "$IDP_APP_FAILOVER_EKS_AUTH_ENABLED"
replace_key_value idpAppConfig.failover.eksAuth.clusterName "$IDP_APP_FAILOVER_EKS_CLUSTER_NAME"
replace_key_value idpAppConfig.failover.eksAuth.region "$IDP_APP_FAILOVER_EKS_REGION"
replace_key_value idpAppConfig.failoverSync.enabled "$IDP_APP_FAILOVER_SYNC_ENABLED"
replace_key_value idpAppConfig.failoverSync.threadCount "$IDP_APP_FAILOVER_SYNC_THREAD_COUNT"
replace_key_value idpAppConfig.podLabel "$IDP_APP_POD_LABEL"
replace_key_value backstageEntitiesFetchLimit "$BACKSTAGE_ENTITIES_FETCH_LIMIT"
replace_key_value idpServiceSecret "$IDP_SERVICE_SECRET"
replace_key_value idpAutomationGitHubToken "$IDP_AUTOMATION_GITHUB_TOKEN"
replace_key_value idpEncryptionSecret "$IDP_ENCRYPTION_SECRET"
replace_key_value jwtExternalServiceSecret "$JWT_EXTERNAL_SERVICE_SECRET"
replace_key_value idpAutomationXApiKey "$IDP_AUTOMATION_X_API_KEY"
replace_key_value jwtAuthSecret "$JWT_AUTH_SECRET"
replace_key_value jwtIdentityServiceSecret "$JWT_IDENTITY_SERVICE_SECRET"
replace_key_value provisionModuleConfig.triggerPipelineUrl "$TRIGGER_PIPELINE_URL"
replace_key_value accessControlClient.enableAccessControl $ACCESS_CONTROL_ENABLED
replace_key_value accessControlClient.accessControlServiceConfig.baseUrl "$ACCESS_CONTROL_BASE_URL"
replace_key_value accessControlClient.accessControlServiceSecret "$ACCESS_CONTROL_SECRET"
replace_key_value backstageHttpClientConfig.baseUrl "$BACKSTAGE_BASE_URL"
replace_key_value backstageServiceSecret "$BACKSTAGE_SERVICE_SECRET"
replace_key_value onboardingModuleConfig.harnessCiCdAnnotations.projectUrl "$ONBOARDING_MODULE_CONFIG_HARNESS_CI_CD_ANNOTATIONS_PROJECT_URL"
replace_key_value onboardingModuleConfig.harnessCiCdAnnotations.serviceUrl "$ONBOARDING_MODULE_CONFIG_HARNESS_CI_CD_ANNOTATIONS_SERVICE_URL"
replace_key_value env "$ENV"
replace_key_value devSpaceDefaultBackstageNamespace "$DEVSPACE_DEFAULT_BACKSTAGE_NAMESPACE"
replace_key_value devSpaceDefaultAccountId "$DEVSPACE_DEFAULT_ACCOUNT_ID"
replace_key_value backstageAppBaseUrl "$BACKSTAGE_APP_BASE_URL"
replace_key_value backstagePostgresHost "$BACKSTAGE_POSTGRES_HOST"
replace_key_value onboardingModuleConfig.useGitServiceGrpcForSingleEntityPush $ONBOARDING_MODULE_CONFIG_USE_GIT_SERVICE_GRPC_FOR_SINGLE_ENTITY_PUSH
replace_key_value delegateSelectorsCacheMode "$DELEGATE_SELECTORS_CACHE_MODE"
replace_key_value shouldConfigureWithNotification "$SHOULD_CONFIGURE_WITH_NOTIFICATION"
replace_key_value notificationClient.secrets.notificationClientSecret "$NOTIFICATION_CLIENT_SECRET"
replace_key_value segmentConfiguration.certValidationRequired "$SEGMENT_VERIFY_CERT"
replace_key_value opaClientConfig.baseUrl "$OPA_SERVER_BASEURL"
replace_key_value policyManagerSecret "$OPA_SERVER_SECRET"
replace_key_value segmentConfiguration.enabled "$SEGMENT_ENABLED"
replace_key_value segmentConfiguration.url "$SEGMENT_URL"
replace_key_value segmentConfiguration.apiKey "$SEGMENT_APIKEY"
replace_key_value segmentConfiguration.certValidationRequired "$SEGMENT_VERIFY_CERT"
replace_key_value enableOpenTelemetry "$ENABLE_OPENTELEMETRY"
replace_key_value delegateSelectorsCacheMode "$DELEGATE_SELECTORS_CACHE_MODE"
replace_key_value enableMetrics "$ENABLE_METRICS"
replace_key_value enableAPIMetrics "$ENABLE_API_METRICS"
replace_key_value customPlugins.triggerPipelineUrl "$CUSTOM_PLUGINS_TRIGGER_PIPELINE_URL"
replace_key_value customPlugins.pipelineExecutionUrl "$CUSTOM_PLUGINS_PIPELINE_EXECUTION_URL"
replace_key_value customPlugins.pipelineExecutionLogUrl "$CUSTOM_PLUGINS_PIPELINE_EXECUTION_LOG_URL"
replace_key_value customPlugins.bucketName "$CUSTOM_PLUGINS_BUCKET_NAME"
replace_key_value customPlugins.imageBucketName "$CUSTOM_PLUGINS_IMAGE_BUCKET_NAME"
replace_key_value catalogContent.bucketName "$CATALOG_CONTENT_BUCKET_NAME"
replace_key_value contentEncryption.enabled "$IDP_CONTENT_ENCRYPTION_ENABLED"
replace_key_value contentEncryption.kmsKeyUri "$IDP_CONTENT_ENCRYPTION_KMS_KEY_URI"
replace_key_value enforcementClientConfiguration.enforcementCheckEnabled "$ENFORCEMENT_CHECK_ENABLED"
replace_key_value accessControlAdminClient.accessControlServiceConfig.baseUrl "$ACCESS_CONTROL_BASE_URL"
replace_key_value accessControlAdminClient.accessControlServiceSecret "$ACCESS_CONTROL_SECRET"
replace_key_value resourceGroupClientConfig.serviceConfig.baseUrl "$RESOURCE_GROUP_BASE_URL"
replace_key_value resourceGroupClientConfig.secret "$NEXT_GEN_MANAGER_SECRET"
replace_key_value dslClientConfig.connectTimeOutSeconds "$DSL_CLIENT_CONNECT_TIMEOUT"
replace_key_value dslClientConfig.readTimeOutSeconds "$DSL_CLIENT_READ_TIMEOUT"
replace_key_value dslClientConfig.writeTimeOutSeconds "$DSL_CLIENT_WRITE_TIMEOUT"
replace_key_value harnessCodeRepoConfig.baseUrl "$HARNESS_CODE_REPO_CONFIG_BASE_URL"
replace_key_value harnessCodeRepoConfig.gitBaseUrl "$HARNESS_CODE_REPO_CONFIG_GIT_BASE_URL"
replace_key_value harnessCodeRepoConfig.apiUrl "$HARNESS_CODE_REPO_CONFIG_API_URL"
replace_key_value harnessCodeRepoConfig.internalApiUrl "$HARNESS_CODE_REPO_CONFIG_INTERNAL_API_URL"
replace_key_value harnessCodeRepoConfig.serviceClientSharedSecret "$HARNESS_CODE_REPO_CONFIG_SERVICE_CLIENT_SHARED_SECRET"
replace_key_value cfClientConfig.apiKey "$CF_CLIENT_API_KEY"
replace_key_value cfClientConfig.configUrl "$CF_CLIENT_CONFIG_URL"
replace_key_value cfClientConfig.eventUrl "$CF_CLIENT_EVENT_URL"
replace_key_value cfClientConfig.analyticsEnabled "$CF_CLIENT_ANALYTICS_ENABLED"
replace_key_value cfClientConfig.connectionTimeout "$CF_CLIENT_CONNECTION_TIMEOUT"
replace_key_value cfClientConfig.readTimeout "$CF_CLIENT_READ_TIMEOUT"
replace_key_value cfClientConfig.bufferSize "$CF_CLIENT_BUFFER_SIZE"
replace_key_value cfClientConfig.retries "$CF_RETRIES"
replace_key_value cfClientConfig.sleepInterval "$CF_SLEEP_INTERVAL"
replace_key_value featureFlagConfig.featureFlagSystem "$FEATURE_FLAG_SYSTEM"
replace_key_value featureFlagConfig.syncFeaturesToCF "$SYNC_FEATURES_TO_CF"
replace_key_value harnessCiCdAnnotationsServiceUrl "$HARNESS_CI_CD_ANNOTATIONS_SERVICE_URL"
replace_key_value integrationsHarnessCiCdAnnotationsServiceUrl "$INTEGRATIONS_HARNESS_CI_CD_ANNOTATIONS_SERVICE_URL"

if [[ "" != "$LOCK_CONFIG_REDIS_URL" ]]; then
  export LOCK_CONFIG_REDIS_URL; yq -i '.singleServerConfig.address=env(LOCK_CONFIG_REDIS_URL)' $REDISSON_CACHE_FILE
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

if [[ "" != "$CACHE_CONFIG_REDIS_USERNAME" ]]; then
  export CACHE_CONFIG_REDIS_USERNAME; yq -i '.singleServerConfig.username=env(CACHE_CONFIG_REDIS_USERNAME)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_CONFIG_REDIS_PASSWORD" ]]; then
  export CACHE_CONFIG_REDIS_PASSWORD; yq -i '.singleServerConfig.password=env(CACHE_CONFIG_REDIS_PASSWORD)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$NOTIFICATION_BASE_URL" ]]; then
  export NOTIFICATION_BASE_URL; yq -i '.notificationClient.httpClient.baseUrl=env(NOTIFICATION_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$NOTIFICATION_MONGO_URI" ]]; then
  export NOTIFICATION_MONGO_URI=${NOTIFICATION_MONGO_URI//\\&/&}; yq -i '.notificationClient.messageBroker.uri=env(NOTIFICATION_MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$NOTIFICATION_CONFIGS_PLUGIN_REQUESTS_NOTIFICATION_SLACK" ]]; then
  export NOTIFICATION_CONFIGS_PLUGIN_REQUESTS_NOTIFICATION_SLACK; yq -i '.notificationConfigs.pluginRequestsNotificationSlack=env(NOTIFICATION_CONFIGS_PLUGIN_REQUESTS_NOTIFICATION_SLACK)' $CONFIG_FILE
fi

if [[ "" != "$NOTIFICATION_CONFIGS_CATALOG_ENTITIES_VERIFICATION_NOTIFICATION_SLACK" ]]; then
  export NOTIFICATION_CONFIGS_CATALOG_ENTITIES_VERIFICATION_NOTIFICATION_SLACK; yq -i '.notificationConfigs.catalogEntitiesVerificationNotificationSlack=env(NOTIFICATION_CONFIGS_CATALOG_ENTITIES_VERIFICATION_NOTIFICATION_SLACK)' $CONFIG_FILE
fi

if [[ "" != "$PIPELINE_SERVICE_CLIENT_BASEURL" ]]; then
  export PIPELINE_SERVICE_CLIENT_BASEURL; yq -i '.pipelineServiceClientConfig.baseUrl=env(PIPELINE_SERVICE_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$PIPELINE_SERVICE_SECRET" ]]; then
  export PIPELINE_SERVICE_SECRET; yq -i '.pipelineServiceSecret=env(PIPELINE_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$TI_SERVICE_ENDPOINT" ]]; then
  export TI_SERVICE_ENDPOINT; yq -i '.tiServiceConfig.baseUrl=env(TI_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$TI_SERVICE_INTERNAL_URL" ]]; then
  export TI_SERVICE_INTERNAL_URL; yq -i '.tiServiceConfig.internalUrl=env(TI_SERVICE_INTERNAL_URL)' $CONFIG_FILE
fi

if [[ "" != "$TI_SERVICE_GLOBAL_TOKEN" ]]; then
  export TI_SERVICE_GLOBAL_TOKEN; yq -i '.tiServiceConfig.globalToken=env(TI_SERVICE_GLOBAL_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$SCORECARD_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export SCORECARD_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.scorecardScoreComputation.threadPoolCount=env(SCORECARD_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$SCORECARD_ITERATOR_ENABLED" ]]; then
  export SCORECARD_ITERATOR_ENABLED; yq -i '.iteratorsConfig.scorecardScoreComputation.enabled=env(SCORECARD_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$SCORECARD_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export SCORECARD_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.scorecardScoreComputation.targetIntervalInSeconds=env(SCORECARD_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$MARKETPLACE_PLUGINS_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export MARKETPLACE_PLUGINS_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.marketPlacePluginsSync.threadPoolCount=env(MARKETPLACE_PLUGINS_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$MARKETPLACE_PLUGINS_ITERATOR_ENABLED" ]]; then
  export MARKETPLACE_PLUGINS_ITERATOR_ENABLED; yq -i '.iteratorsConfig.marketPlacePluginsSync.enabled=env(MARKETPLACE_PLUGINS_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$MARKETPLACE_PLUGINS_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export MARKETPLACE_PLUGINS_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.marketPlacePluginsSync.targetIntervalInSeconds=env(MARKETPLACE_PLUGINS_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$BACKSTAGE_ENV_VARIABLES_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export BACKSTAGE_ENV_VARIABLES_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.backstageEnvVariablesSync.threadPoolCount=env(BACKSTAGE_ENV_VARIABLES_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$BACKSTAGE_ENV_VARIABLES_ITERATOR_ENABLED" ]]; then
  export BACKSTAGE_ENV_VARIABLES_ITERATOR_ENABLED; yq -i '.iteratorsConfig.backstageEnvVariablesSync.enabled=env(BACKSTAGE_ENV_VARIABLES_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$BACKSTAGE_ENV_VARIABLES_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export BACKSTAGE_ENV_VARIABLES_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.backstageEnvVariablesSync.targetIntervalInSeconds=env(BACKSTAGE_ENV_VARIABLES_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$BACKSTAGE_PERMISSIONS_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export BACKSTAGE_PERMISSIONS_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.backstagePermissionsSync.threadPoolCount=env(BACKSTAGE_PERMISSIONS_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$BACKSTAGE_PERMISSIONS_ITERATOR_ENABLED" ]]; then
  export BACKSTAGE_PERMISSIONS_ITERATOR_ENABLED; yq -i '.iteratorsConfig.backstagePermissionsSync.enabled=env(BACKSTAGE_PERMISSIONS_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$BACKSTAGE_PERMISSIONS_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export BACKSTAGE_PERMISSIONS_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.backstagePermissionsSync.targetIntervalInSeconds=env(BACKSTAGE_PERMISSIONS_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$CONFIG_PURGE_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export CONFIG_PURGE_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.configPurge.threadPoolCount=env(CONFIG_PURGE_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$CONFIG_PURGE_ITERATOR_ENABLED" ]]; then
  export CONFIG_PURGE_ITERATOR_ENABLED; yq -i '.iteratorsConfig.configPurge.enabled=env(CONFIG_PURGE_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$CONFIG_PURGE_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export CONFIG_PURGE_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.configPurge.targetIntervalInSeconds=env(CONFIG_PURGE_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$LICENSE_USAGE_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export LICENSE_USAGE_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.licenseUsageCount.threadPoolCount=env(LICENSE_USAGE_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$LICENSE_USAGE_ITERATOR_ENABLED" ]]; then
  export LICENSE_USAGE_ITERATOR_ENABLED; yq -i '.iteratorsConfig.licenseUsageCount.enabled=env(LICENSE_USAGE_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$LICENSE_USAGE_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export LICENSE_USAGE_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.licenseUsageCount.targetIntervalInSeconds=env(LICENSE_USAGE_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$SCAFFOLDER_TASKS_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export SCAFFOLDER_TASKS_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.scaffolderTasksSync.threadPoolCount=env(SCAFFOLDER_TASKS_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$SCAFFOLDER_TASKS_ITERATOR_ENABLED" ]]; then
  export SCAFFOLDER_TASKS_ITERATOR_ENABLED; yq -i '.iteratorsConfig.scaffolderTasksSync.enabled=env(SCAFFOLDER_TASKS_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$SCAFFOLDER_TASKS_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export SCAFFOLDER_TASKS_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.scaffolderTasksSync.targetIntervalInSeconds=env(SCAFFOLDER_TASKS_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$USER_SYNC_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export USER_SYNC_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.userSync.threadPoolCount=env(USER_SYNC_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$USER_SYNC_ITERATOR_ENABLED" ]]; then
  export USER_SYNC_ITERATOR_ENABLED; yq -i '.iteratorsConfig.userSync.enabled=env(USER_SYNC_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$USER_SYNC_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export USER_SYNC_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.userSync.targetIntervalInSeconds=env(USER_SYNC_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$TELEMETRY_RECORDS_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export TELEMETRY_RECORDS_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.telemetryRecords.threadPoolCount=env(TELEMETRY_RECORDS_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$TELEMETRY_RECORDS_ITERATOR_ENABLED" ]]; then
  export TELEMETRY_RECORDS_ITERATOR_ENABLED; yq -i '.iteratorsConfig.telemetryRecords.enabled=env(TELEMETRY_RECORDS_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$TELEMETRY_RECORDS_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export TELEMETRY_RECORDS_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.telemetryRecords.targetIntervalInSeconds=env(TELEMETRY_RECORDS_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$ONBOARDING_FLOW_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export ONBOARDING_FLOW_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.onboardingFlow.threadPoolCount=env(ONBOARDING_FLOW_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$ONBOARDING_FLOW_ITERATOR_ENABLED" ]]; then
  export ONBOARDING_FLOW_ITERATOR_ENABLED; yq -i '.iteratorsConfig.onboardingFlow.enabled=env(ONBOARDING_FLOW_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$ONBOARDING_FLOW_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export ONBOARDING_FLOW_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.onboardingFlow.targetIntervalInSeconds=env(ONBOARDING_FLOW_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$STATS_COMPUTATION_SYNC_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export STATS_COMPUTATION_SYNC_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.statsComputationSync.threadPoolCount=env(STATS_COMPUTATION_SYNC_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$STATS_COMPUTATION_SYNC_ITERATOR_ENABLED" ]]; then
  export STATS_COMPUTATION_SYNC_ITERATOR_ENABLED; yq -i '.iteratorsConfig.statsComputationSync.enabled=env(STATS_COMPUTATION_SYNC_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$STATS_COMPUTATION_SYNC_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export STATS_COMPUTATION_SYNC_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.statsComputationSync.targetIntervalInSeconds=env(STATS_COMPUTATION_SYNC_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$ACTIVE_DEVELOPERS_SYNC_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export ACTIVE_DEVELOPERS_SYNC_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.activeDevelopersSync.threadPoolCount=env(ACTIVE_DEVELOPERS_SYNC_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$ACTIVE_DEVELOPERS_SYNC_ITERATOR_ENABLED" ]]; then
  export ACTIVE_DEVELOPERS_SYNC_ITERATOR_ENABLED; yq -i '.iteratorsConfig.activeDevelopersSync.enabled=env(ACTIVE_DEVELOPERS_SYNC_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$ACTIVE_DEVELOPERS_SYNC_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export ACTIVE_DEVELOPERS_SYNC_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.activeDevelopersSync.targetIntervalInSeconds=env(ACTIVE_DEVELOPERS_SYNC_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$SCORECARD_STATS_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export SCORECARD_STATS_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.scorecardStatsComputation.threadPoolCount=env(SCORECARD_STATS_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$SCORECARD_STATS_ITERATOR_ENABLED" ]]; then
  export SCORECARD_STATS_ITERATOR_ENABLED; yq -i '.iteratorsConfig.scorecardStatsComputation.enabled=env(SCORECARD_STATS_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$SCORECARD_STATS_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export SCORECARD_STATS_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.scorecardStatsComputation.targetIntervalInSeconds=env(SCORECARD_STATS_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$IDP_TO_HARNESS_ENTITIES_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export IDP_TO_HARNESS_ENTITIES_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.idpToHarnessEntities.threadPoolCount=env(IDP_TO_HARNESS_ENTITIES_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$IDP_TO_HARNESS_ENTITIES_ITERATOR_ENABLED" ]]; then
  export IDP_TO_HARNESS_ENTITIES_ITERATOR_ENABLED; yq -i '.iteratorsConfig.idpToHarnessEntities.enabled=env(IDP_TO_HARNESS_ENTITIES_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$IDP_TO_HARNESS_ENTITIES_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export IDP_TO_HARNESS_ENTITIES_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.idpToHarnessEntities.targetIntervalInSeconds=env(IDP_TO_HARNESS_ENTITIES_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_TO_IDP_USER_GROUP_SYNC_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export HARNESS_TO_IDP_USER_GROUP_SYNC_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.harnessToIDPUserGroupSync.threadPoolCount=env(HARNESS_TO_IDP_USER_GROUP_SYNC_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_TO_IDP_USER_GROUP_SYNC_ITERATOR_ENABLED" ]]; then
  export HARNESS_TO_IDP_USER_GROUP_SYNC_ITERATOR_ENABLED; yq -i '.iteratorsConfig.harnessToIDPUserGroupSync.enabled=env(HARNESS_TO_IDP_USER_GROUP_SYNC_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_TO_IDP_USER_GROUP_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export HARNESS_TO_IDP_USER_GROUP_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.harnessToIDPUserGroupSync.targetIntervalInSeconds=env(HARNESS_TO_IDP_USER_GROUP_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$CATALOG_ENTITIES_VERIFICATION_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export CATALOG_ENTITIES_VERIFICATION_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.catalogEntitiesVerification.threadPoolCount=env(CATALOG_ENTITIES_VERIFICATION_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$CATALOG_ENTITIES_VERIFICATION_ITERATOR_ENABLED" ]]; then
  export CATALOG_ENTITIES_VERIFICATION_ITERATOR_ENABLED; yq -i '.iteratorsConfig.catalogEntitiesVerification.enabled=env(CATALOG_ENTITIES_VERIFICATION_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$CATALOG_ENTITIES_VERIFICATION_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export CATALOG_ENTITIES_VERIFICATION_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.catalogEntitiesVerification.targetIntervalInSeconds=env(CATALOG_ENTITIES_VERIFICATION_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$MODIFY_ENTITY_IDENTIFIER_IN_DEPENDENTS_FOR_IDP_V2_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export MODIFY_ENTITY_IDENTIFIER_IN_DEPENDENTS_FOR_IDP_V2_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.modifyEntityIdentifierInDependentsForIdpV2.threadPoolCount=env(MODIFY_ENTITY_IDENTIFIER_IN_DEPENDENTS_FOR_IDP_V2_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$MODIFY_ENTITY_IDENTIFIER_IN_DEPENDENTS_FOR_IDP_V2_ITERATOR_ENABLED" ]]; then
  export MODIFY_ENTITY_IDENTIFIER_IN_DEPENDENTS_FOR_IDP_V2_ITERATOR_ENABLED; yq -i '.iteratorsConfig.modifyEntityIdentifierInDependentsForIdpV2.enabled=env(MODIFY_ENTITY_IDENTIFIER_IN_DEPENDENTS_FOR_IDP_V2_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$MODIFY_ENTITY_IDENTIFIER_IN_DEPENDENTS_FOR_IDP_V2_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export MODIFY_ENTITY_IDENTIFIER_IN_DEPENDENTS_FOR_IDP_V2_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.modifyEntityIdentifierInDependentsForIdpV2.targetIntervalInSeconds=env(MODIFY_ENTITY_IDENTIFIER_IN_DEPENDENTS_FOR_IDP_V2_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$AGGREGATION_RULES_COMPUTATION_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export AGGREGATION_RULES_COMPUTATION_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.aggregationRulesComputation.threadPoolCount=env(AGGREGATION_RULES_COMPUTATION_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$AGGREGATION_RULES_COMPUTATION_ITERATOR_ENABLED" ]]; then
  export AGGREGATION_RULES_COMPUTATION_ITERATOR_ENABLED; yq -i '.iteratorsConfig.aggregationRulesComputation.enabled=env(AGGREGATION_RULES_COMPUTATION_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$AGGREGATION_RULES_COMPUTATION_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export AGGREGATION_RULES_COMPUTATION_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.aggregationRulesComputation.targetIntervalInSeconds=env(AGGREGATION_RULES_COMPUTATION_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_CLIENT_BASEURL" ]]; then
  export AUDIT_CLIENT_BASEURL; yq -i '.auditClientConfig.baseUrl=env(AUDIT_CLIENT_BASEURL)' $CONFIG_FILE
fi

if [[ "" != "$AUDIT_ENABLED" ]]; then
  export AUDIT_ENABLED; yq -i '.enableAudit=env(AUDIT_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$INTERNAL_ACCOUNTS" ]]; then
  yq -i 'del(.internalAccounts)' $CONFIG_FILE
  export INTERNAL_ACCOUNTS; yq -i '.internalAccounts=(env(INTERNAL_ACCOUNTS) | split(",") | map(trim))' $CONFIG_FILE
fi

if [[ "" != "$ALLOWED_KINDS_FOR_CATALOG_SYNC" ]]; then
  yq -i 'del(.allowedKindsForCatalogSync)' $CONFIG_FILE
  export ALLOWED_KINDS_FOR_CATALOG_SYNC; yq -i '.allowedKindsForCatalogSync=(env(ALLOWED_KINDS_FOR_CATALOG_SYNC) | split(",") | map(trim))' $CONFIG_FILE
fi

if [[ "" != "$ALLOWED_KINDS_FOR_AUDIT" ]]; then
  yq -i 'del(.allowedKindsForAudit)' $CONFIG_FILE
  export ALLOWED_KINDS_FOR_AUDIT; yq -i '.allowedKindsForAudit=(env(ALLOWED_KINDS_FOR_AUDIT) | split(",") | map(trim))' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_BACKSTAGE_CATALOG_CONSUMER_TOPIC_NAME" ]]; then
  export DEBEZIUM_BACKSTAGE_CATALOG_CONSUMER_TOPIC_NAME; yq -i '.debeziumConsumersConfigs.backstageCatalog.topic=env(DEBEZIUM_BACKSTAGE_CATALOG_CONSUMER_TOPIC_NAME)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_BACKSTAGE_CATALOG_CONSUMER_BATCH_SIZE" ]]; then
  export DEBEZIUM_BACKSTAGE_CATALOG_CONSUMER_BATCH_SIZE; yq -i '.debeziumConsumersConfigs.backstageCatalog.batchSize=env(DEBEZIUM_BACKSTAGE_CATALOG_CONSUMER_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_BACKSTAGE_CATALOG_CONSUMER_MAX_PROCESSING_TIME_SECONDS" ]]; then
  export DEBEZIUM_BACKSTAGE_CATALOG_CONSUMER_MAX_PROCESSING_TIME_SECONDS; yq -i '.debeziumConsumersConfigs.backstageCatalog.maxProcessingTimeSeconds=env(DEBEZIUM_BACKSTAGE_CATALOG_CONSUMER_MAX_PROCESSING_TIME_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_BACKSTAGE_SCAFFOLDER_TASKS_CONSUMER_TOPIC_NAME" ]]; then
  export DEBEZIUM_BACKSTAGE_SCAFFOLDER_TASKS_CONSUMER_TOPIC_NAME; yq -i '.debeziumConsumersConfigs.backstageScaffolderTasks.topic=env(DEBEZIUM_BACKSTAGE_SCAFFOLDER_TASKS_CONSUMER_TOPIC_NAME)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_BACKSTAGE_SCAFFOLDER_TASKS_CONSUMER_BATCH_SIZE" ]]; then
  export DEBEZIUM_BACKSTAGE_SCAFFOLDER_TASKS_CONSUMER_BATCH_SIZE; yq -i '.debeziumConsumersConfigs.backstageScaffolderTasks.batchSize=env(DEBEZIUM_BACKSTAGE_SCAFFOLDER_TASKS_CONSUMER_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_BACKSTAGE_SCAFFOLDER_TASKS_CONSUMER_MAX_PROCESSING_TIME_SECONDS" ]]; then
  export DEBEZIUM_BACKSTAGE_SCAFFOLDER_TASKS_CONSUMER_MAX_PROCESSING_TIME_SECONDS; yq -i '.debeziumConsumersConfigs.backstageScaffolderTasks.maxProcessingTimeSeconds=env(DEBEZIUM_BACKSTAGE_SCAFFOLDER_TASKS_CONSUMER_MAX_PROCESSING_TIME_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_SCORECARDS_CONSUMER_TOPIC_NAME" ]]; then
  export DEBEZIUM_SCORECARDS_CONSUMER_TOPIC_NAME; yq -i '.debeziumConsumersConfigs.scorecards.topic=env(DEBEZIUM_SCORECARDS_CONSUMER_TOPIC_NAME)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_SCORECARDS_CONSUMER_BATCH_SIZE" ]]; then
  export DEBEZIUM_SCORECARDS_CONSUMER_BATCH_SIZE; yq -i '.debeziumConsumersConfigs.scorecards.batchSize=env(DEBEZIUM_SCORECARDS_CONSUMER_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_SCORECARDS_CONSUMER_MAX_PROCESSING_TIME_SECONDS" ]]; then
  export DEBEZIUM_SCORECARDS_CONSUMER_MAX_PROCESSING_TIME_SECONDS; yq -i '.debeziumConsumersConfigs.scorecards.maxProcessingTimeSeconds=env(DEBEZIUM_SCORECARDS_CONSUMER_MAX_PROCESSING_TIME_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_APP_CONFIGS_CONSUMER_TOPIC_NAME" ]]; then
  export DEBEZIUM_APP_CONFIGS_CONSUMER_TOPIC_NAME; yq -i '.debeziumConsumersConfigs.appConfigs.topic=env(DEBEZIUM_APP_CONFIGS_CONSUMER_TOPIC_NAME)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_APP_CONFIGS_CONSUMER_BATCH_SIZE" ]]; then
  export DEBEZIUM_APP_CONFIGS_CONSUMER_BATCH_SIZE; yq -i '.debeziumConsumersConfigs.appConfigs.batchSize=env(DEBEZIUM_APP_CONFIGS_CONSUMER_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_APP_CONFIGS_CONSUMER_MAX_PROCESSING_TIME_SECONDS" ]]; then
  export DEBEZIUM_APP_CONFIGS_CONSUMER_MAX_PROCESSING_TIME_SECONDS; yq -i '.debeziumConsumersConfigs.appConfigs.maxProcessingTimeSeconds=env(DEBEZIUM_APP_CONFIGS_CONSUMER_MAX_PROCESSING_TIME_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_MODULE_LICENSES_CONSUMER_TOPIC_NAME" ]]; then
  export DEBEZIUM_MODULE_LICENSES_CONSUMER_TOPIC_NAME; yq -i '.debeziumConsumersConfigs.moduleLicenses.topic=env(DEBEZIUM_MODULE_LICENSES_CONSUMER_TOPIC_NAME)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_MODULE_LICENSES_CONSUMER_BATCH_SIZE" ]]; then
  export DEBEZIUM_MODULE_LICENSES_CONSUMER_BATCH_SIZE; yq -i '.debeziumConsumersConfigs.moduleLicenses.batchSize=env(DEBEZIUM_MODULE_LICENSES_CONSUMER_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_MODULE_LICENSES_CONSUMER_MAX_PROCESSING_TIME_SECONDS" ]]; then
  export DEBEZIUM_MODULE_LICENSES_CONSUMER_MAX_PROCESSING_TIME_SECONDS; yq -i '.debeziumConsumersConfigs.moduleLicenses.maxProcessingTimeSeconds=env(DEBEZIUM_MODULE_LICENSES_CONSUMER_MAX_PROCESSING_TIME_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_CHECKS_CONSUMER_TOPIC_NAME" ]]; then
  export DEBEZIUM_CHECKS_CONSUMER_TOPIC_NAME; yq -i '.debeziumConsumersConfigs.checks.topic=env(DEBEZIUM_CHECKS_CONSUMER_TOPIC_NAME)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_CHECKS_CONSUMER_BATCH_SIZE" ]]; then
  export DEBEZIUM_CHECKS_CONSUMER_BATCH_SIZE; yq -i '.debeziumConsumersConfigs.checks.batchSize=env(DEBEZIUM_CHECKS_CONSUMER_BATCH_SIZE)' $CONFIG_FILE
fi

if [[ "" != "$DEBEZIUM_CHECKS_CONSUMER_MAX_PROCESSING_TIME_SECONDS" ]]; then
  export DEBEZIUM_CHECKS_CONSUMER_MAX_PROCESSING_TIME_SECONDS; yq -i '.debeziumConsumersConfigs.checks.maxProcessingTimeSeconds=env(DEBEZIUM_CHECKS_CONSUMER_MAX_PROCESSING_TIME_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_DASHBOARD_TIMESCALE" ]]; then
  export ENABLE_DASHBOARD_TIMESCALE; yq -i '.enableDashboardTimescale=env(ENABLE_DASHBOARD_TIMESCALE)' $CONFIG_FILE
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

if [[ "" != "$ENTITY_CRUD_CONSUMER_THREADS" ]]; then
  export ENTITY_CRUD_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.entity_crud=env(ENTITY_CRUD_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$USER_MEMBERSHIP_CONSUMER_THREADS" ]]; then
  export USER_MEMBERSHIP_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.usermembership=env(USER_MEMBERSHIP_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT_CONSUMER_THREADS" ]]; then
  export IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.idp_module_license_usage_capture=env(IDP_MODULE_LICENSE_USAGE_CAPTURE_EVENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_CONSUMER_THREADS" ]]; then
  export IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.idp_catalog_entities_sync_capture=env(IDP_CATALOG_ENTITIES_SYNC_CAPTURE_EVENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$IDP_CATALOG_ENTITIES_V3_REFERENCED_ENTITIES_SYNC_CONSUMER_THREADS" ]]; then
  export IDP_CATALOG_ENTITIES_V3_REFERENCED_ENTITIES_SYNC_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.idp_catalog_entities_v3_referenced_entities_sync=env(IDP_CATALOG_ENTITIES_V3_REFERENCED_ENTITIES_SYNC_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$IDP_CATALOG_ENTITIES_V3_STO_ENRICHMENT_CONSUMER_THREADS" ]]; then
  export IDP_CATALOG_ENTITIES_V3_STO_ENRICHMENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.idp_catalog_entities_v3_sto_enrichment=env(IDP_CATALOG_ENTITIES_V3_STO_ENRICHMENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$IDP_CATALOG_ENTITIES_V3_API_ENDPOINT_CONSUMER_THREADS" ]]; then
  export IDP_CATALOG_ENTITIES_V3_API_ENDPOINT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.idp_catalog_entities_v3_api_endpoint=env(IDP_CATALOG_ENTITIES_V3_API_ENDPOINT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$PROJECT_MOVEMENT_CONSUMER_THREADS" ]]; then
  export PROJECT_MOVEMENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.project_movement=env(PROJECT_MOVEMENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$PROJECT_TOPOLOGY_REBUILD_CONSUMER_THREADS" ]]; then
  export PROJECT_TOPOLOGY_REBUILD_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.project_topology_rebuild=env(PROJECT_TOPOLOGY_REBUILD_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$IDP_CATALOG_CUSTOM_PROPERTY_CAPTURE_EVENT_CONSUMER_THREADS" ]]; then
  export IDP_CATALOG_CUSTOM_PROPERTY_CAPTURE_EVENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.idp_catalog_custom_property_entity_sync_capture=env(IDP_CATALOG_CUSTOM_PROPERTY_CAPTURE_EVENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$BACKSTAGE_HTTP_CLIENT_CP_MAX_IDLE_CONNECTIONS" ]]; then
  export BACKSTAGE_HTTP_CLIENT_CP_MAX_IDLE_CONNECTIONS; yq -i '.okHttpClientConnectionPoolConfigs.backstageHttpClient.maxIdleConnections=env(BACKSTAGE_HTTP_CLIENT_CP_MAX_IDLE_CONNECTIONS)' $CONFIG_FILE
fi

if [[ "" != "$BACKSTAGE_HTTP_CLIENT_CP_KEEP_ALIVE_DURATION" ]]; then
  export BACKSTAGE_HTTP_CLIENT_CP_KEEP_ALIVE_DURATION; yq -i '.okHttpClientConnectionPoolConfigs.backstageHttpClient.keepAliveDuration=env(BACKSTAGE_HTTP_CLIENT_CP_KEEP_ALIVE_DURATION)' $CONFIG_FILE
fi

if [[ "" != "$BACKSTAGE_HTTP_CLIENT_CP_TIME_UNIT" ]]; then
  export BACKSTAGE_HTTP_CLIENT_CP_TIME_UNIT; yq -i '.okHttpClientConnectionPoolConfigs.backstageHttpClient.timeUnit=env(BACKSTAGE_HTTP_CLIENT_CP_TIME_UNIT)' $CONFIG_FILE
fi

if [[ "" != "$DIRECT_DSL_CLIENT_HTTP_CLIENT_CP_MAX_IDLE_CONNECTIONS" ]]; then
  export DIRECT_DSL_CLIENT_HTTP_CLIENT_CP_MAX_IDLE_CONNECTIONS; yq -i '.okHttpClientConnectionPoolConfigs.directDslClientHttpClient.maxIdleConnections=env(DIRECT_DSL_CLIENT_HTTP_CLIENT_CP_MAX_IDLE_CONNECTIONS)' $CONFIG_FILE
fi

if [[ "" != "$DIRECT_DSL_CLIENT_HTTP_CLIENT_CP_KEEP_ALIVE_DURATION" ]]; then
  export DIRECT_DSL_CLIENT_HTTP_CLIENT_CP_KEEP_ALIVE_DURATION; yq -i '.okHttpClientConnectionPoolConfigs.directDslClientHttpClient.keepAliveDuration=env(DIRECT_DSL_CLIENT_HTTP_CLIENT_CP_KEEP_ALIVE_DURATION)' $CONFIG_FILE
fi

if [[ "" != "$DIRECT_DSL_CLIENT_HTTP_CLIENT_CP_TIME_UNIT" ]]; then
  export DIRECT_DSL_CLIENT_HTTP_CLIENT_CP_TIME_UNIT; yq -i '.okHttpClientConnectionPoolConfigs.directDslClientHttpClient.timeUnit=env(DIRECT_DSL_CLIENT_HTTP_CLIENT_CP_TIME_UNIT)' $CONFIG_FILE
fi

if [[ "" != "$PROXY_API_MANAGER_HTTP_CLIENT_CP_MAX_IDLE_CONNECTIONS" ]]; then
  export PROXY_API_MANAGER_HTTP_CLIENT_CP_MAX_IDLE_CONNECTIONS; yq -i '.okHttpClientConnectionPoolConfigs.proxyApiManagerHttpClient.maxIdleConnections=env(PROXY_API_MANAGER_HTTP_CLIENT_CP_MAX_IDLE_CONNECTIONS)' $CONFIG_FILE
fi

if [[ "" != "$PROXY_API_MANAGER_HTTP_CLIENT_CP_KEEP_ALIVE_DURATION" ]]; then
  export PROXY_API_MANAGER_HTTP_CLIENT_CP_KEEP_ALIVE_DURATION; yq -i '.okHttpClientConnectionPoolConfigs.proxyApiManagerHttpClient.keepAliveDuration=env(PROXY_API_MANAGER_HTTP_CLIENT_CP_KEEP_ALIVE_DURATION)' $CONFIG_FILE
fi

if [[ "" != "$PROXY_API_MANAGER_HTTP_CLIENT_CP_TIME_UNIT" ]]; then
  export PROXY_API_MANAGER_HTTP_CLIENT_CP_TIME_UNIT; yq -i '.okHttpClientConnectionPoolConfigs.proxyApiManagerHttpClient.timeUnit=env(PROXY_API_MANAGER_HTTP_CLIENT_CP_TIME_UNIT)' $CONFIG_FILE
fi

if [[ "" != "$PROXY_API_NG_MANAGER_HTTP_CLIENT_CP_MAX_IDLE_CONNECTIONS" ]]; then
  export PROXY_API_NG_MANAGER_HTTP_CLIENT_CP_MAX_IDLE_CONNECTIONS; yq -i '.okHttpClientConnectionPoolConfigs.proxyApiNgManagerHttpClient.maxIdleConnections=env(PROXY_API_NG_MANAGER_HTTP_CLIENT_CP_MAX_IDLE_CONNECTIONS)' $CONFIG_FILE
fi

if [[ "" != "$PROXY_API_NG_MANAGER_HTTP_CLIENT_CP_KEEP_ALIVE_DURATION" ]]; then
  export PROXY_API_NG_MANAGER_HTTP_CLIENT_CP_KEEP_ALIVE_DURATION; yq -i '.okHttpClientConnectionPoolConfigs.proxyApiNgManagerHttpClient.keepAliveDuration=env(PROXY_API_NG_MANAGER_HTTP_CLIENT_CP_KEEP_ALIVE_DURATION)' $CONFIG_FILE
fi

if [[ "" != "$PROXY_API_NG_MANAGER_HTTP_CLIENT_CP_TIME_UNIT" ]]; then
  export PROXY_API_NG_MANAGER_HTTP_CLIENT_CP_TIME_UNIT; yq -i '.okHttpClientConnectionPoolConfigs.proxyApiNgManagerHttpClient.timeUnit=env(PROXY_API_NG_MANAGER_HTTP_CLIENT_CP_TIME_UNIT)' $CONFIG_FILE
fi

if [[ "" != "$IDP_AGENT_HTTP_CLIENT_CP_MAX_IDLE_CONNECTIONS" ]]; then
  export IDP_AGENT_HTTP_CLIENT_CP_MAX_IDLE_CONNECTIONS; yq -i '.okHttpClientConnectionPoolConfigs.idpAgentHttpClient.maxIdleConnections=env(IDP_AGENT_HTTP_CLIENT_CP_MAX_IDLE_CONNECTIONS)' $CONFIG_FILE
fi

if [[ "" != "$IDP_AGENT_HTTP_CLIENT_CP_KEEP_ALIVE_DURATION" ]]; then
  export IDP_AGENT_HTTP_CLIENT_CP_KEEP_ALIVE_DURATION; yq -i '.okHttpClientConnectionPoolConfigs.idpAgentHttpClient.keepAliveDuration=env(IDP_AGENT_HTTP_CLIENT_CP_KEEP_ALIVE_DURATION)' $CONFIG_FILE
fi

if [[ "" != "$IDP_AGENT_HTTP_CLIENT_CP_TIME_UNIT" ]]; then
  export IDP_AGENT_HTTP_CLIENT_CP_TIME_UNIT; yq -i '.okHttpClientConnectionPoolConfigs.idpAgentHttpClient.timeUnit=env(IDP_AGENT_HTTP_CLIENT_CP_TIME_UNIT)' $CONFIG_FILE
fi

if [[ "" != "$BACKSTAGE_CATALOG_REDIS_EVENT_CONSUMER_THREADS" ]]; then
  export BACKSTAGE_CATALOG_REDIS_EVENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.BACKSTAGE_CATALOG_REDIS_EVENT_CONSUMER=env(BACKSTAGE_CATALOG_REDIS_EVENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$BACKSTAGE_SCAFFOLDER_TASKS_REDIS_EVENT_CONSUMER_THREADS" ]]; then
  export BACKSTAGE_SCAFFOLDER_TASKS_REDIS_EVENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.BACKSTAGE_SCAFFOLDER_TASKS_REDIS_EVENT_CONSUMER=env(BACKSTAGE_SCAFFOLDER_TASKS_REDIS_EVENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$SCORECARDS_REDIS_EVENT_CONSUMER_THREADS" ]]; then
  export SCORECARDS_REDIS_EVENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.IDP_SCORECARDS_REDIS_EVENT_CONSUMER=env(SCORECARDS_REDIS_EVENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$APP_CONFIGS_REDIS_EVENT_CONSUMER_THREADS" ]]; then
  export APP_CONFIGS_REDIS_EVENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.IDP_APP_CONFIGS_REDIS_EVENT_CONSUMER=env(APP_CONFIGS_REDIS_EVENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$MODULE_LICENSES_REDIS_EVENT_CONSUMER_THREADS" ]]; then
  export MODULE_LICENSES_REDIS_EVENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.MODULE_LICENSES_REDIS_EVENT_CONSUMER=env(MODULE_LICENSES_REDIS_EVENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$CHECKS_REDIS_EVENT_CONSUMER_THREADS" ]]; then
  export CHECKS_REDIS_EVENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.IDP_CHECKS_REDIS_EVENT_CONSUMER=env(CHECKS_REDIS_EVENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$STO_SCAN_COMPLETION_EVENT" ]]; then
  export STO_SCAN_COMPLETION_EVENT; yq -i '.numberOfThreadsToUseForConsumers.STO_SCAN_COMPLETION_EVENT=env(STO_SCAN_COMPLETION_EVENT)' $CONFIG_FILE
fi

if [[ "" != "$IDP_INTEGRATION_CRUD_EVENT_CONSUMER_THREADS" ]]; then
  export IDP_INTEGRATION_CRUD_EVENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.integration_idp_crud=env(IDP_INTEGRATION_CRUD_EVENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT_CONSUMER_THREADS" ]]; then
  export IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.idp_integration_catalog_processor=env(IDP_INTEGRATION_CATALOG_PROCESSOR_EVENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$IDP_RELATIONSHIP_PROCESSING_EVENT_CONSUMER_THREADS" ]]; then
  export IDP_RELATIONSHIP_PROCESSING_EVENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.idp_relationship_processing=env(IDP_RELATIONSHIP_PROCESSING_EVENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$IDP_KIND_PROCESSOR_EVENT_CONSUMER_THREADS" ]]; then
  export IDP_KIND_PROCESSOR_EVENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.idp_kind_processor=env(IDP_KIND_PROCESSOR_EVENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$IDP_BULK_UPDATE_EVENT_CONSUMER_THREADS" ]]; then
  export IDP_BULK_UPDATE_EVENT_CONSUMER_THREADS; yq -i '.numberOfThreadsToUseForConsumers.idp_bulk_field_update=env(IDP_BULK_UPDATE_EVENT_CONSUMER_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$MODIFY_DEFAULT_TO_ACCOUNT_NAMESPACE_IN_BACKSTAGE_FOR_IDP_V2_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export MODIFY_DEFAULT_TO_ACCOUNT_NAMESPACE_IN_BACKSTAGE_FOR_IDP_V2_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.modifyDefaultToAccountNamespaceInBackstageForIdpV2.threadPoolCount=env(MODIFY_DEFAULT_TO_ACCOUNT_NAMESPACE_IN_BACKSTAGE_FOR_IDP_V2_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$MODIFY_DEFAULT_TO_ACCOUNT_NAMESPACE_IN_BACKSTAGE_FOR_IDP_V2_ITERATOR_ENABLED" ]]; then
  export MODIFY_DEFAULT_TO_ACCOUNT_NAMESPACE_IN_BACKSTAGE_FOR_IDP_V2_ITERATOR_ENABLED; yq -i '.iteratorsConfig.modifyDefaultToAccountNamespaceInBackstageForIdpV2.enabled=env(MODIFY_DEFAULT_TO_ACCOUNT_NAMESPACE_IN_BACKSTAGE_FOR_IDP_V2_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$MODIFY_DEFAULT_TO_ACCOUNT_NAMESPACE_IN_BACKSTAGE_FOR_IDP_V2_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export MODIFY_DEFAULT_TO_ACCOUNT_NAMESPACE_IN_BACKSTAGE_FOR_IDP_V2_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.modifyDefaultToAccountNamespaceInBackstageForIdpV2.targetIntervalInSeconds=env(MODIFY_DEFAULT_TO_ACCOUNT_NAMESPACE_IN_BACKSTAGE_FOR_IDP_V2_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$MODIFY_WORKFLOW_FORM_CONTEXT_DATA_FOR_IDP_V2_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export MODIFY_WORKFLOW_FORM_CONTEXT_DATA_FOR_IDP_V2_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.modifyWorkflowFormContextDataForIdpV2.threadPoolCount=env(MODIFY_WORKFLOW_FORM_CONTEXT_DATA_FOR_IDP_V2_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$MODIFY_WORKFLOW_FORM_CONTEXT_DATA_FOR_IDP_V2_ITERATOR_ENABLED" ]]; then
  export MODIFY_WORKFLOW_FORM_CONTEXT_DATA_FOR_IDP_V2_ITERATOR_ENABLED; yq -i '.iteratorsConfig.modifyWorkflowFormContextDataForIdpV2.enabled=env(MODIFY_WORKFLOW_FORM_CONTEXT_DATA_FOR_IDP_V2_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$MODIFY_WORKFLOW_FORM_CONTEXT_DATA_FOR_IDP_V2_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export MODIFY_WORKFLOW_FORM_CONTEXT_DATA_FOR_IDP_V2_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.modifyWorkflowFormContextDataForIdpV2.targetIntervalInSeconds=env(MODIFY_WORKFLOW_FORM_CONTEXT_DATA_FOR_IDP_V2_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$POPULATE_QUERYABLE_ENTITY_REF_IN_CATALOG_FOR_IDP_V2_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export POPULATE_QUERYABLE_ENTITY_REF_IN_CATALOG_FOR_IDP_V2_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.populateQueryableEntityRefInCatalogForIdpV2.threadPoolCount=env(POPULATE_QUERYABLE_ENTITY_REF_IN_CATALOG_FOR_IDP_V2_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$POPULATE_QUERYABLE_ENTITY_REF_IN_CATALOG_FOR_IDP_V2_ITERATOR_ENABLED" ]]; then
  export POPULATE_QUERYABLE_ENTITY_REF_IN_CATALOG_FOR_IDP_V2_ITERATOR_ENABLED; yq -i '.iteratorsConfig.populateQueryableEntityRefInCatalogForIdpV2.enabled=env(POPULATE_QUERYABLE_ENTITY_REF_IN_CATALOG_FOR_IDP_V2_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$POPULATE_QUERYABLE_ENTITY_REF_IN_CATALOG_FOR_IDP_V2_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export POPULATE_QUERYABLE_ENTITY_REF_IN_CATALOG_FOR_IDP_V2_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.populateQueryableEntityRefInCatalogForIdpV2.targetIntervalInSeconds=env(POPULATE_QUERYABLE_ENTITY_REF_IN_CATALOG_FOR_IDP_V2_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$MIGRATE_SCOPE_FOR_IDP_V2_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export MIGRATE_SCOPE_FOR_IDP_V2_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.migrateEntityScopeForIdpV2.threadPoolCount=env(MIGRATE_SCOPE_FOR_IDP_V2_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$MIGRATE_SCOPE_FOR_IDP_V2_ITERATOR_ENABLED" ]]; then
  export MIGRATE_SCOPE_FOR_IDP_V2_ITERATOR_ENABLED; yq -i '.iteratorsConfig.migrateEntityScopeForIdpV2.enabled=env(MIGRATE_SCOPE_FOR_IDP_V2_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$MIGRATE_SCOPE_FOR_IDP_V2_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export MIGRATE_SCOPE_FOR_IDP_V2_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.migrateEntityScopeForIdpV2.targetIntervalInSeconds=env(MIGRATE_SCOPE_FOR_IDP_V2_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$RELATIONSHIP_RETRY_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export RELATIONSHIP_RETRY_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.relationshipRetry.threadPoolCount=env(RELATIONSHIP_RETRY_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$RELATIONSHIP_RETRY_ITERATOR_ENABLED" ]]; then
  export RELATIONSHIP_RETRY_ITERATOR_ENABLED; yq -i '.iteratorsConfig.relationshipRetry.enabled=env(RELATIONSHIP_RETRY_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$RELATIONSHIP_RETRY_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export RELATIONSHIP_RETRY_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.relationshipRetry.targetIntervalInSeconds=env(RELATIONSHIP_RETRY_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

if [[ "" != "$SCOPE_TOPOLOGY_CACHE_REBUILD_ITERATOR_THREAD_POOL_COUNT" ]]; then
  export SCOPE_TOPOLOGY_CACHE_REBUILD_ITERATOR_THREAD_POOL_COUNT; yq -i '.iteratorsConfig.scopeTopologyCacheRebuild.threadPoolCount=env(SCOPE_TOPOLOGY_CACHE_REBUILD_ITERATOR_THREAD_POOL_COUNT)' $CONFIG_FILE
fi

if [[ "" != "$SCOPE_TOPOLOGY_CACHE_REBUILD_ITERATOR_ENABLED" ]]; then
  export SCOPE_TOPOLOGY_CACHE_REBUILD_ITERATOR_ENABLED; yq -i '.iteratorsConfig.scopeTopologyCacheRebuild.enabled=env(SCOPE_TOPOLOGY_CACHE_REBUILD_ITERATOR_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$SCOPE_TOPOLOGY_CACHE_REBUILD_ITERATOR_TARGET_INTERVAL_IN_SECONDS" ]]; then
  export SCOPE_TOPOLOGY_CACHE_REBUILD_ITERATOR_TARGET_INTERVAL_IN_SECONDS; yq -i '.iteratorsConfig.scopeTopologyCacheRebuild.targetIntervalInSeconds=env(SCOPE_TOPOLOGY_CACHE_REBUILD_ITERATOR_TARGET_INTERVAL_IN_SECONDS)' $CONFIG_FILE
fi

#Changes to use internal connection urls for PMS client gRPC
replace_key_value pmsGrpcClientConfig.target "$INTERNAL_PMS_TARGET"
replace_key_value pmsGrpcClientConfig.authority "$INTERNAL_PMS_AUTHORITY"

replace_key_value managerTarget "$INTERNAL_MANAGER_TARGET"
replace_key_value managerAuthority "$INTERNAL_MANAGER_AUTHORITY"
replace_key_value base $ENVIRONMENT_BASE_URL
replace_key_value proxyEndPointEnv $PROXY_ENDPOINT_ENV
replace_key_value gcsForTechDocsDelegate "$GCS_FOR_TECHDOCS_DELEGATE"

if [[ "" != "$INTEGRATION_MANAGER_BASE_URL" ]]; then
  export INTEGRATION_MANAGER_BASE_URL; yq -i '.integrationManagerClientConfig.baseUrl=env(INTEGRATION_MANAGER_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$INTEGRATION_MANAGER_SECRET" ]]; then
  export INTEGRATION_MANAGER_SECRET; yq -i '.integrationManagerSecret=env(INTEGRATION_MANAGER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$INTEGRATION_MANAGER_IDP_MAPPING_ID" ]]; then
  export INTEGRATION_MANAGER_IDP_MAPPING_ID; yq -i '.integrationManagerIdpMappingId=env(INTEGRATION_MANAGER_IDP_MAPPING_ID)' $CONFIG_FILE
fi
