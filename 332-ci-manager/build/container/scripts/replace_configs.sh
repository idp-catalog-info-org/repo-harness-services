#!/usr/bin/env bash
# Copyright 2022 Harness Inc. All rights reserved.
# Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
# that can be found in the licenses directory at the root of this repository, also available at
# https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

CONFIG_FILE=/opt/harness/ci-manager-config.yml
REDISSON_CACHE_FILE=/opt/harness/redisson-jcache.yaml

if [[ "${APPLY_CI_PLUGIN_DEFAULTS}" == "true" ]]; then
  # Core images
  yq -i 'del(.ciExecutionServiceConfig.addonImage)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.liteEngineImage)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.addonImageRootless)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.liteEngineImageRootless)' $CONFIG_FILE

  # K8s step configs (image + entrypoint + windowsEntrypoint)
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.gitCloneConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.buildAndPushDockerRegistryConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.buildAndPushECRConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.buildAndPushGCRConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.buildAndPushGARConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.buildAndPushACRConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.buildAndPushBuildxDockerRegistryConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.buildAndPushBuildxECRConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.buildAndPushBuildxGCRConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.buildAndPushBuildxGARConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.buildAndPushBuildxACRConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.gcsUploadConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.s3UploadConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.artifactoryUploadConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.cacheGCSConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.cacheS3Config)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.cacheAzureConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.cacheConfig)' $CONFIG_FILE

  # VM image config
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.gitClone)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushDockerRegistry)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushECR)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushACR)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushGCR)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushGAR)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushBuildxDockerRegistry)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushBuildxECR)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushBuildxACR)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildAndPushBuildxGAR)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.gcsUpload)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.s3Upload)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.artifactoryUpload)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.cacheGCS)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.cacheS3)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.cacheAzure)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmImageConfig.cache)' $CONFIG_FILE

  # Containerless step config
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmContainerlessStepConfig.gitCloneConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmContainerlessStepConfig.s3UploadConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmContainerlessStepConfig.gcsUploadConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmContainerlessStepConfig.artifactoryUploadConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmContainerlessStepConfig.cacheGCSConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmContainerlessStepConfig.cacheS3Config)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmContainerlessStepConfig.cacheAzureConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmContainerlessStepConfig.cacheConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmContainerlessStepConfig.dockerBuildxConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmContainerlessStepConfig.dockerBuildxEcrConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmContainerlessStepConfig.dockerBuildxGcrConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmContainerlessStepConfig.dockerBuildxGarConfig)' $CONFIG_FILE
  yq -i 'del(.ciExecutionServiceConfig.stepConfig.vmContainerlessStepConfig.dockerBuildxAcrConfig)' $CONFIG_FILE
fi
ENTERPRISE_REDISSON_CACHE_FILE=/opt/harness/enterprise-redisson-jcache.yaml

replace_key_value () {
  CONFIG_KEY="$1";
  CONFIG_VALUE="$2";
  if [[ "" != "$CONFIG_VALUE" ]]; then
    export CONFIG_VALUE; export CONFIG_KEY; export CONFIG_KEY=.$CONFIG_KEY; yq -i 'eval(strenv(CONFIG_KEY))=env(CONFIG_VALUE)' $CONFIG_FILE
  fi
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

if [[ "" != "$SERVER_PORT" ]]; then
  export SERVER_PORT; yq -i '.server.applicationConnectors[0].port=env(SERVER_PORT)' $CONFIG_FILE
else
  yq -i '.server.applicationConnectors[0].port=7090' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_URL" ]]; then
  export MANAGER_URL; yq -i '.managerClientConfig.baseUrl=env(MANAGER_URL)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_URL" ]]; then
  export NG_MANAGER_URL; yq -i '.ngManagerClientConfig.baseUrl=env(NG_MANAGER_URL)' $CONFIG_FILE
fi

if [[ "" != "$RHS_CLIENT_BASE_URL" ]]; then
  export RHS_CLIENT_BASE_URL; yq -i '.rhsClientConfig.baseUrl=env(RHS_CLIENT_BASE_URL)' $CONFIG_FILE
fi

# Workload Identity (OIDC-without-connector): HarnessID client overrides from harness-pl-infra.
# The feature stays disabled until target, REST baseUrl and serviceAuthSecret are all set (isEnabled()).
if [[ "" != "$HARNESS_ID_GRPC_TARGET" ]]; then
  export HARNESS_ID_GRPC_TARGET; yq -i '.harnessIdGrpcClientConfig.target=env(HARNESS_ID_GRPC_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_ID_GRPC_AUTHORITY" ]]; then
  export HARNESS_ID_GRPC_AUTHORITY; yq -i '.harnessIdGrpcClientConfig.authority=env(HARNESS_ID_GRPC_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_ID_SERVICE_SECRET" ]]; then
  export HARNESS_ID_SERVICE_SECRET; yq -i '.harnessIdGrpcClientConfig.serviceAuthSecret=env(HARNESS_ID_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_ID_REST_BASE_URL" ]]; then
  export HARNESS_ID_REST_BASE_URL; yq -i '.harnessIdRestClientConfig.baseUrl=env(HARNESS_ID_REST_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$RHS_ENABLED" ]]; then
  export RHS_ENABLED; yq -i '.rhsEnabled=env(RHS_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$RESOURCE_HIERARCHY_SERVICE_SECRET" ]]; then
  export RESOURCE_HIERARCHY_SERVICE_SECRET; yq -i '.rhsServiceSecret=env(RESOURCE_HIERARCHY_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$SECRET_CONNECTOR_SERVICE_CLIENT_BASE_URL" ]]; then
  export SECRET_CONNECTOR_SERVICE_CLIENT_BASE_URL; yq -i '.secretConnectorServiceClientConfig.baseUrl=env(SECRET_CONNECTOR_SERVICE_CLIENT_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$SECRET_CONNECTOR_SERVICE_ENABLED" ]]; then
  export SECRET_CONNECTOR_SERVICE_ENABLED; yq -i '.secretConnectorServiceEnabled=env(SECRET_CONNECTOR_SERVICE_ENABLED)' $CONFIG_FILE
fi

if [[ "" != "$SECRET_CONNECTOR_SERVICE_SECRET" ]]; then
  export SECRET_CONNECTOR_SERVICE_SECRET; yq -i '.secretConnectorServiceSecret=env(SECRET_CONNECTOR_SERVICE_SECRET)' $CONFIG_FILE
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

if [[ "" != "$CACHE_PROXY_IMAGE" ]]; then
  export CACHE_PROXY_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.cacheProxyConfig.image=env(CACHE_PROXY_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$TMATE_ENDPOINT" ]]; then
  export TMATE_ENDPOINT; yq -i '.ciExecutionServiceConfig.tmateEndpoint=env(TMATE_ENDPOINT)' $CONFIG_FILE
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

if [[ "" != "$ACR_PUSH_IMAGE" ]]; then
  export ACR_PUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushACRConfig.image=env(ACR_PUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$GCR_PUSH_IMAGE" ]]; then
  export GCR_PUSH_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushGCRConfig.image=env(GCR_PUSH_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$BUILD_PUSH_DOCKER_DLC_IMAGE" ]]; then
  export BUILD_PUSH_DOCKER_DLC_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushBuildxDockerRegistryConfig.image=env(BUILD_PUSH_DOCKER_DLC_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$BUILD_PUSH_ECR_DLC_IMAGE" ]]; then
  export BUILD_PUSH_ECR_DLC_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushBuildxECRConfig.image=env(BUILD_PUSH_ECR_DLC_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$BUILD_PUSH_ACR_DLC_IMAGE" ]]; then
  export BUILD_PUSH_ACR_DLC_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushBuildxACRConfig.image=env(BUILD_PUSH_ACR_DLC_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$BUILD_PUSH_GCR_DLC_IMAGE" ]]; then
  export BUILD_PUSH_GCR_DLC_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushBuildxGCRConfig.image=env(BUILD_PUSH_GCR_DLC_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$BUILD_PUSH_GAR_DLC_IMAGE" ]]; then
  export BUILD_PUSH_GAR_DLC_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.buildAndPushBuildxGARConfig.image=env(BUILD_PUSH_GAR_DLC_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$BUILDKIT_IMAGE" ]]; then
  export BUILDKIT_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.buildkitConfig.image=env(BUILDKIT_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_BUILDKIT_IMAGE" ]]; then
  export VM_BUILDKIT_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.buildkit=env(VM_BUILDKIT_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$ENABLE_AUTH" ]]; then
  export ENABLE_AUTH; yq -i '.enableAuth=env(ENABLE_AUTH)' $CONFIG_FILE
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

if [[ "" != "$AZURE_CACHE_IMAGE" ]]; then
  export AZURE_CACHE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.cacheAzureConfig.image=env(AZURE_CACHE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$GENERIC_CACHE_IMAGE" ]]; then
  export GENERIC_CACHE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.cacheConfig.image=env(GENERIC_CACHE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_ORCHESTRATION_IMAGE" ]]; then
  export SSCA_ORCHESTRATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.sscaOrchestrationConfig.image=env(SSCA_ORCHESTRATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_ENFORCEMENT_IMAGE" ]]; then
  export SSCA_ENFORCEMENT_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.sscaEnforcementConfig.image=env(SSCA_ENFORCEMENT_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_CDXGEN_ORCHESTRATION_IMAGE" ]]; then
  export SSCA_CDXGEN_ORCHESTRATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.sscaCdxgenOrchestrationConfig.image=env(SSCA_CDXGEN_ORCHESTRATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_ARTIFACT_SIGNING_IMAGE" ]]; then
  export SSCA_ARTIFACT_SIGNING_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.sscaArtifactSigningConfig.image=env(SSCA_ARTIFACT_SIGNING_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_ARTIFACT_VERIFICATION_IMAGE" ]]; then
  export SSCA_ARTIFACT_VERIFICATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.sscaArtifactVerificationConfig.image=env(SSCA_ARTIFACT_VERIFICATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$DOCKER_PROVENANCE_IMAGE" ]]; then
  export DOCKER_PROVENANCE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.provenanceConfig.image=env(DOCKER_PROVENANCE_IMAGE)' $CONFIG_FILE
fi


if [[ "" != "$SLSA_VERIFICATION_DOCKER_IMAGE" ]]; then
  export SLSA_VERIFICATION_DOCKER_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.slsaVerificationConfig.image=env(SLSA_VERIFICATION_DOCKER_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_COMPLIANCE_IMAGE" ]]; then
  export SSCA_COMPLIANCE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.sscaComplianceConfig.image=env(SSCA_COMPLIANCE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_PR_ATTESTATION_IMAGE" ]]; then
  export SSCA_PR_ATTESTATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.sscaPrAttestationConfig.image=env(SSCA_PR_ATTESTATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_JUNIT_ATTESTATION_IMAGE" ]]; then
  export SSCA_JUNIT_ATTESTATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.sscaJunitAttestationConfig.image=env(SSCA_JUNIT_ATTESTATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$DEPLOY_ATTESTATION_IMAGE" ]]; then
  export DEPLOY_ATTESTATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.deployAttestationConfig.image=env(DEPLOY_ATTESTATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_AIBOM_ORCHESTRATION_IMAGE" ]]; then
  export SSCA_AIBOM_ORCHESTRATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.sscaAibomOrchestrationConfig.image=env(SSCA_AIBOM_ORCHESTRATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$ENFORCE_ATTESTATION_IMAGE" ]]; then
  export ENFORCE_ATTESTATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.enforceAttestationConfig.image=env(ENFORCE_ATTESTATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IACM_TERRAFORM_IMAGE" ]]; then
  export IACM_TERRAFORM_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.iacmTerraformConfig.image=env(IACM_TERRAFORM_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IACM_OPENTOFU_IMAGE" ]]; then
  export IACM_OPENTOFU_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.iacmOpenTofuConfig.image=env(IACM_OPENTOFU_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IACM_TFSEC_IMAGE" ]]; then
  export IACM_TFSEC_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.iacmTFSecConfig.image=env(IACM_TFSEC_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IACM_TFLINT_IMAGE" ]]; then
  export IACM_TFLINT_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.iacmTFLintConfig.image=env(IACM_TFLINT_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IACM_TFCOMPLIANCE_IMAGE" ]]; then
  export IACM_TFCOMPLIANCE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.iacmTFComplianceConfig.image=env(IACM_TFCOMPLIANCE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$IACM_CHECKOV_IMAGE" ]]; then
  export IACM_CHECKOV_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.iacmCheckovConfig.image=env(IACM_CHECKOV_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_SSCA_ORCHESTRATION_IMAGE" ]]; then
  export VM_SSCA_ORCHESTRATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.sscaOrchestration=env(VM_SSCA_ORCHESTRATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_SSCA_ENFORCEMENT_IMAGE" ]]; then
  export VM_SSCA_ENFORCEMENT_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.sscaEnforcement=env(VM_SSCA_ENFORCEMENT_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_SSCA_CDXGEN_ORCHESTRATION_IMAGE" ]]; then
  export VM_SSCA_CDXGEN_ORCHESTRATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.sscaCdxgenOrchestration=env(VM_SSCA_CDXGEN_ORCHESTRATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_SSCA_ARTIFACT_SIGNING_IMAGE" ]]; then
  export VM_SSCA_ARTIFACT_SIGNING_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.sscaArtifactSigning=env(VM_SSCA_ARTIFACT_SIGNING_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_SSCA_ARTIFACT_VERIFICATION_IMAGE" ]]; then
  export VM_SSCA_ARTIFACT_VERIFICATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.sscaArtifactVerification=env(VM_SSCA_ARTIFACT_VERIFICATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_SSCA_COMPLIANCE_IMAGE" ]]; then
  export VM_SSCA_COMPLIANCE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.sscaCompliance=env(VM_SSCA_COMPLIANCE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_SSCA_PR_ATTESTATION_IMAGE" ]]; then
  export VM_SSCA_PR_ATTESTATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.sscaPrAttestation=env(VM_SSCA_PR_ATTESTATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_SSCA_JUNIT_ATTESTATION_IMAGE" ]]; then
  export VM_SSCA_JUNIT_ATTESTATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.sscaJunitAttestation=env(VM_SSCA_JUNIT_ATTESTATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_DEPLOY_ATTESTATION_IMAGE" ]]; then
  export VM_DEPLOY_ATTESTATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.deployAttestation=env(VM_DEPLOY_ATTESTATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_SSCA_AIBOM_ORCHESTRATION_IMAGE" ]]; then
  export VM_SSCA_AIBOM_ORCHESTRATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.sscaAibomOrchestration=env(VM_SSCA_AIBOM_ORCHESTRATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_ENFORCE_ATTESTATION_IMAGE" ]]; then
  export VM_ENFORCE_ATTESTATION_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.enforceAttestation=env(VM_ENFORCE_ATTESTATION_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_DOCKER_PROVENANCE_IMAGE" ]]; then
  export VM_DOCKER_PROVENANCE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.provenance=env(VM_DOCKER_PROVENANCE_IMAGE)' $CONFIG_FILE
fi

if [[ "" != "$VM_SLSA_VERIFICATION_DOCKER_IMAGE" ]]; then
  export VM_SLSA_VERIFICATION_DOCKER_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.slsaVerification=env(VM_SLSA_VERIFICATION_DOCKER_IMAGE)' $CONFIG_FILE
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

if [[ "" != "$CACHE_BUCKET" ]]; then
  export CACHE_BUCKET; yq -i '.ciExecutionServiceConfig.cacheIntelligenceConfig.bucket=env(CACHE_BUCKET)' $CONFIG_FILE
fi

if [[ "" != "$CACHE_SERVICE_KEY" ]]; then
  export CACHE_SERVICE_KEY; yq -i '.ciExecutionServiceConfig.cacheIntelligenceConfig.serviceKey=env(CACHE_SERVICE_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CACHE_S3_BUCKET" ]]; then
  export CACHE_S3_BUCKET; yq -i '.ciExecutionServiceConfig.cacheIntelligenceS3Config.bucket=env(CACHE_S3_BUCKET)' $CONFIG_FILE
fi

if [[ "" != "$CACHE_S3_ACCESS_KEY" ]]; then
  export CACHE_S3_ACCESS_KEY; yq -i '.ciExecutionServiceConfig.cacheIntelligenceS3Config.accessKey=env(CACHE_S3_ACCESS_KEY)' $CONFIG_FILE
fi

if [[ "" != "$CACHE_S3_ACCESS_SECRET" ]]; then
  export CACHE_S3_ACCESS_SECRET; yq -i '.ciExecutionServiceConfig.cacheIntelligenceS3Config.accessSecret=env(CACHE_S3_ACCESS_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$CACHE_S3_REGION" ]]; then
  export CACHE_S3_REGION; yq -i '.ciExecutionServiceConfig.cacheIntelligenceS3Config.region=env(CACHE_S3_REGION)' $CONFIG_FILE
fi

if [[ "" != "$CACHE_S3_ENDPOINT" ]]; then
  export CACHE_S3_ENDPOINT; yq -i '.ciExecutionServiceConfig.cacheIntelligenceS3Config.endpoint=env(CACHE_S3_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$DLC_S3_ENDPOINT" ]]; then
  export DLC_S3_ENDPOINT; yq -i '.ciExecutionServiceConfig.dockerLayerCachingConfig.endpoint=env(DLC_S3_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$DLC_S3_BUCKET" ]]; then
  export DLC_S3_BUCKET; yq -i '.ciExecutionServiceConfig.dockerLayerCachingConfig.bucket=env(DLC_S3_BUCKET)' $CONFIG_FILE
fi

if [[ "" != "$DLC_S3_ACCESS_KEY" ]]; then
  export DLC_S3_ACCESS_KEY; yq -i '.ciExecutionServiceConfig.dockerLayerCachingConfig.accessKey=env(DLC_S3_ACCESS_KEY)' $CONFIG_FILE
fi

if [[ "" != "$DLC_S3_SECRET_KEY" ]]; then
  export DLC_S3_SECRET_KEY; yq -i '.ciExecutionServiceConfig.dockerLayerCachingConfig.secretKey=env(DLC_S3_SECRET_KEY)' $CONFIG_FILE
fi

if [[ "" != "$DLC_S3_REGION" ]]; then
  export DLC_S3_REGION; yq -i '.ciExecutionServiceConfig.dockerLayerCachingConfig.region=env(DLC_S3_REGION)' $CONFIG_FILE
fi

if [[ "" != "$DLC_GCS_ENDPOINT" ]]; then
  export DLC_GCS_ENDPOINT; yq -i '.ciExecutionServiceConfig.dockerLayerCachingGCSConfig.endpoint=env(DLC_GCS_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$DLC_GCS_BUCKET" ]]; then
  export DLC_GCS_BUCKET; yq -i '.ciExecutionServiceConfig.dockerLayerCachingGCSConfig.bucket=env(DLC_GCS_BUCKET)' $CONFIG_FILE
fi

if [[ "" != "$DLC_GCS_ACCESS_KEY" ]]; then
  export DLC_GCS_ACCESS_KEY; yq -i '.ciExecutionServiceConfig.dockerLayerCachingGCSConfig.accessKey=env(DLC_GCS_ACCESS_KEY)' $CONFIG_FILE
fi

if [[ "" != "$DLC_GCS_SECRET_KEY" ]]; then
  export DLC_GCS_SECRET_KEY; yq -i '.ciExecutionServiceConfig.dockerLayerCachingGCSConfig.secretKey=env(DLC_GCS_SECRET_KEY)' $CONFIG_FILE
fi

if [[ "" != "$DLC_GCS_REGION" ]]; then
  export DLC_GCS_REGION; yq -i '.ciExecutionServiceConfig.dockerLayerCachingGCSConfig.region=env(DLC_GCS_REGION)' $CONFIG_FILE
fi

if [[ "" != "$DLC_GCS_PROJECT_ID" ]]; then
  export DLC_GCS_PROJECT_ID; yq -i '.ciExecutionServiceConfig.dockerLayerCachingGCSConfig.projectId=env(DLC_GCS_PROJECT_ID)' $CONFIG_FILE
fi

if [[ "" != "$HOSTED_VM_SPLIT_LINUX_AMD64_POOL" ]]; then
  export HOSTED_VM_SPLIT_LINUX_AMD64_POOL; yq -i '.ciExecutionServiceConfig.hostedVmConfig.splitLinuxAmd64Pool=env(HOSTED_VM_SPLIT_LINUX_AMD64_POOL)' $CONFIG_FILE
fi

if [[ "" != "$HOSTED_VM_SPLIT_LINUX_ARM64_POOL" ]]; then
  export HOSTED_VM_SPLIT_LINUX_ARM64_POOL; yq -i '.ciExecutionServiceConfig.hostedVmConfig.splitLinuxArm64Pool=env(HOSTED_VM_SPLIT_LINUX_ARM64_POOL)' $CONFIG_FILE
fi

if [[ "" != "$HOSTED_VM_SPLIT_WINDOWS_AMD64_POOL" ]]; then
  export HOSTED_VM_SPLIT_WINDOWS_AMD64_POOL; yq -i '.ciExecutionServiceConfig.hostedVmConfig.splitWindowsAmd64Pool=env(HOSTED_VM_SPLIT_WINDOWS_AMD64_POOL)' $CONFIG_FILE
fi

if [[ "" != "$HOSTED_VM_INTERNAL_ACCOUNTS" ]]; then
  IFS=',' read -ra INTERNAL_ACCOUNTS <<< "$HOSTED_VM_INTERNAL_ACCOUNTS"
  INDEX=0
  for HOSTED_VM_INTERNAL_URL in "${INTERNAL_ACCOUNTS[@]}"; do
    export HOSTED_VM_INTERNAL_URL; export INDEX; yq -i '.ciExecutionServiceConfig.hostedVmConfig.internalAccounts.[env(INDEX)]=env(HOSTED_VM_INTERNAL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
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
if [[ "" != "$VM_AZURE_CACHE_IMAGE" ]]; then
  export VM_AZURE_CACHE_IMAGE; yq -i '.ciExecutionServiceConfig.stepConfig.vmImageConfig.cacheAzure=env(VM_AZURE_CACHE_IMAGE)' $CONFIG_FILE
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
if [[ "" != "$CI_IP_ALLOWLIST" ]]; then
  IFS=',' read -ra IP_ADDRESSES <<< "$CI_IP_ALLOWLIST"
  INDEX=0
  for IP_ADDRESS in "${IP_ADDRESSES[@]}"; do
    export IP_ADDRESS; export INDEX; yq -i '.ciExecutionServiceConfig.ipAllowlistConfig.ipAddresses.[env(INDEX)]=env(IP_ADDRESS)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi
if [[ "" != "$SERVER_MAX_THREADS" ]]; then
  export SERVER_MAX_THREADS; yq -i '.server.maxThreads=env(SERVER_MAX_THREADS)' $CONFIG_FILE
fi

if [[ "" != "$ALLOWED_ORIGINS" ]]; then
  yq -i 'del(.allowedOrigins)' $CONFIG_FILE
  export ALLOWED_ORIGINS; yq -i '.allowedOrigins=env(ALLOWED_ORIGINS)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_URI" ]]; then
  export MONGO_URI=${MONGO_URI//\\&/&}; yq -i '.harness-mongo.uri=env(MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_TARGET" ]]; then
  export MANAGER_TARGET; yq -i '.managerTarget=env(MANAGER_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$MANAGER_AUTHORITY" ]]; then
  export MANAGER_AUTHORITY; yq -i '.managerAuthority=env(MANAGER_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$CIMANAGER_MONGO_URI" ]]; then
  export CIMANAGER_MONGO_URI; yq -i '.cimanager-mongo.uri=env(CIMANAGER_MONGO_URI)' $CONFIG_FILE
fi

if [[ "" != "$SCM_SERVICE_URI" ]]; then
  export SCM_SERVICE_URI; yq -i '.scmConnectionConfig.url=env(SCM_SERVICE_URI)' $CONFIG_FILE
fi

if [[ "" != "$LOG_SERVICE_ENDPOINT" ]]; then
  export LOG_SERVICE_ENDPOINT; yq -i '.logServiceConfig.baseUrl=env(LOG_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$LOG_SERVICE_GLOBAL_TOKEN" ]]; then
  export LOG_SERVICE_GLOBAL_TOKEN; yq -i '.logServiceConfig.globalToken=env(LOG_SERVICE_GLOBAL_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$LOG_SERVICE_INTERNAL_URL" ]]; then
  export LOG_SERVICE_INTERNAL_URL; yq -i '.logServiceConfig.internalUrl=env(LOG_SERVICE_INTERNAL_URL)' $CONFIG_FILE
fi

if [[ "" != "$TI_SERVICE_ENDPOINT" ]]; then
  export TI_SERVICE_ENDPOINT; yq -i '.tiServiceConfig.baseUrl=env(TI_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$TI_SERVICE_INTERNAL_URL" ]]; then
  export TI_SERVICE_INTERNAL_URL; yq -i '.tiServiceConfig.internalUrl=env(TI_SERVICE_INTERNAL_URL)' $CONFIG_FILE
fi

if [[ "" != "$COVERAGE_SERVICE_ENDPOINT" ]]; then
  export COVERAGE_SERVICE_ENDPOINT; yq -i '.coverageServiceConfig.baseUrl=env(COVERAGE_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$COVERAGE_SERVICE_TOKEN" ]]; then
  export COVERAGE_SERVICE_TOKEN; yq -i '.coverageServiceConfig.token=env(COVERAGE_SERVICE_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_SERVICE_ENDPOINT" ]]; then
  export SSCA_SERVICE_ENDPOINT; yq -i '.sscaServiceConfig.httpClientConfig.baseUrl=env(SSCA_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_FULCIO_SERVICE_ENDPOINT" ]]; then
  export HARNESS_FULCIO_SERVICE_ENDPOINT; yq -i '.harnessFulcioServiceConfig.httpClientConfig.baseUrl=env(HARNESS_FULCIO_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$HARNESS_FULCIO_SERVICE_SECRET" ]]; then
  export HARNESS_FULCIO_SERVICE_SECRET; yq -i '.harnessFulcioServiceConfig.serviceSecret=env(HARNESS_FULCIO_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$IACM_SERVICE_ENDPOINT" ]]; then
  export IACM_SERVICE_ENDPOINT; yq -i '.iacmServiceConfig.baseUrl=env(IACM_SERVICE_ENDPOINT)' $CONFIG_FILE
fi

if [[ "" != "$API_URL" ]]; then
  export API_URL; yq -i '.apiUrl=env(API_URL)' $CONFIG_FILE
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

if [[ "" != "$APPLY_CI_PLUGIN_DEFAULTS" ]]; then
  export APPLY_CI_PLUGIN_DEFAULTS; yq -i '.ciExecutionServiceConfig.applyCIPluginDefaults=env(APPLY_CI_PLUGIN_DEFAULTS)' $CONFIG_FILE
fi

if [[ "" != "$GRPC_SERVER_PORT" ]]; then
  export GRPC_SERVER_PORT; yq -i '.pmsSdkGrpcServerConfig.connectors[0].port=env(GRPC_SERVER_PORT)' $CONFIG_FILE
fi

if [[ "" != "$TI_SERVICE_GLOBAL_TOKEN" ]]; then
  export TI_SERVICE_GLOBAL_TOKEN; yq -i '.tiServiceConfig.globalToken=env(TI_SERVICE_GLOBAL_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$SSCA_SERVICE_SECRET" ]]; then
  export SSCA_SERVICE_SECRET; yq -i '.sscaServiceConfig.serviceSecret=env(SSCA_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$IACM_SERVICE_GLOBAL_TOKEN" ]]; then
  export IACM_SERVICE_GLOBAL_TOKEN; yq -i '.iacmServiceConfig.globalToken=env(IACM_SERVICE_GLOBAL_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$NEXT_GEN_MANAGER_SECRET" ]]; then
  export NEXT_GEN_MANAGER_SECRET; yq -i '.ngManagerServiceSecret=env(NEXT_GEN_MANAGER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$JWT_AUTH_SECRET" ]]; then
  export JWT_AUTH_SECRET; yq -i '.jwtAuthSecret=env(JWT_AUTH_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$JWT_IDENTITY_SERVICE_SECRET" ]]; then
  export JWT_IDENTITY_SERVICE_SECRET; yq -i '.jwtIdentityServiceSecret=env(JWT_IDENTITY_SERVICE_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$JWT_DATA_HANDLER_SECRET" ]]; then
  export JWT_DATA_HANDLER_SECRET; yq -i '.jwtDataHandlerSecret=env(JWT_DATA_HANDLER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$API_URL" ]]; then
  export API_URL; yq -i '.apiUrl=env(API_URL)' $CONFIG_FILE
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

if [[ "" != "$ENABLE_DASHBOARD_TIMESCALE" ]]; then
  export ENABLE_DASHBOARD_TIMESCALE; yq -i '.enableDashboardTimescale=env(ENABLE_DASHBOARD_TIMESCALE)' $CONFIG_FILE
fi

if [[ "" != "$DISTRIBUTED_LOCK_IMPLEMENTATION" ]]; then
  export DISTRIBUTED_LOCK_IMPLEMENTATION; yq -i '.distributedLockImplementation=env(DISTRIBUTED_LOCK_IMPLEMENTATION)' $CONFIG_FILE
fi


if [[ "" != "$MANAGER_SECRET" ]]; then
  export MANAGER_SECRET; yq -i '.managerServiceSecret=env(MANAGER_SECRET)' $CONFIG_FILE
fi

if [[ "" != "$MONGO_INDEX_MANAGER_MODE" ]]; then
  export MONGO_INDEX_MANAGER_MODE; yq -i '.cimanager-mongo.indexManagerMode=env(MONGO_INDEX_MANAGER_MODE)' $CONFIG_FILE
fi

if [[ "$STACK_DRIVER_LOGGING_ENABLED" == "true" ]]; then
  yq -i 'del(.logging.appenders.[] | select(.type == "console"))' $CONFIG_FILE
  yq -i '(.logging.appenders.[] | select(.type == "gke-console") | .stackdriverLogEnabled) = true' $CONFIG_FILE
else
  yq -i 'del(.logging.appenders.[] | select(.type == "gke-console"))' $CONFIG_FILE
fi

replace_key_value accessControlClient.enableAccessControl "$ACCESS_CONTROL_ENABLED"

replace_key_value accessControlClient.accessControlServiceConfig.baseUrl "$ACCESS_CONTROL_BASE_URL"

replace_key_value accessControlClient.accessControlServiceSecret "$ACCESS_CONTROL_SECRET"

if [[ "" != "$EVENTS_FRAMEWORK_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$EVENTS_FRAMEWORK_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.eventsFramework.redis.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

yq -i 'del(.codec)' $REDISSON_CACHE_FILE

if [[ "$REDIS_SCRIPT_CACHE" == "false" ]]; then
  yq -i '.useScriptCache=false' $REDISSON_CACHE_FILE
fi


if [[ "" != "$CACHE_CONFIG_REDIS_URL" ]]; then
  export CACHE_CONFIG_REDIS_URL; yq -i '.singleServerConfig.address=env(CACHE_CONFIG_REDIS_URL)' $REDISSON_CACHE_FILE
fi

if [[ "$CACHE_CONFIG_USE_SENTINEL" == "true" ]]; then
  yq -i 'del(.singleServerConfig)' $REDISSON_CACHE_FILE
  if [[ "" != "$CACHE_CONFIG_SENTINEL_MASTER_NAME" ]]; then
    export CACHE_CONFIG_SENTINEL_MASTER_NAME; yq -i '.sentinelServersConfig.masterName=env(CACHE_CONFIG_SENTINEL_MASTER_NAME)' $REDISSON_CACHE_FILE
  fi

  if [[ "" != "$CACHE_CONFIG_REDIS_SENTINELS" ]]; then
    IFS=',' read -ra SENTINEL_URLS <<< "$CACHE_CONFIG_REDIS_SENTINELS"
    INDEX=0
    for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
      export REDIS_SENTINEL_URL; export INDEX; yq -i '.sentinelServersConfig.sentinelAddresses.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $REDISSON_CACHE_FILE
      INDEX=$(expr $INDEX + 1)
    done
  fi
fi

if [[ "" != "$CACHE_CONFIG_REDIS_USERNAME" ]]; then
  export CACHE_CONFIG_REDIS_USERNAME; yq -i '.singleServerConfig.username=env(CACHE_CONFIG_REDIS_USERNAME)' $REDISSON_CACHE_FILE
  export CACHE_CONFIG_REDIS_USERNAME; yq -i '.singleServerConfig.username=env(CACHE_CONFIG_REDIS_USERNAME)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_CONFIG_REDIS_PASSWORD" ]]; then
  export CACHE_CONFIG_REDIS_PASSWORD; yq -i '.singleServerConfig.password=env(CACHE_CONFIG_REDIS_PASSWORD)' $REDISSON_CACHE_FILE
  export CACHE_CONFIG_REDIS_PASSWORD; yq -i '.singleServerConfig.password=env(CACHE_CONFIG_REDIS_PASSWORD)' $ENTERPRISE_REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_CONFIG_REDIS_SSL_CA_TRUST_STORE_PATH" ]]; then
  export FILE_VAR="file:$CACHE_CONFIG_REDIS_SSL_CA_TRUST_STORE_PATH"; yq -i '.singleServerConfig.sslTruststore=env(FILE_VAR)' $REDISSON_CACHE_FILE
fi

if [[ "" != "$CACHE_CONFIG_REDIS_SSL_CA_TRUST_STORE_PASSWORD" ]]; then
  export CACHE_CONFIG_REDIS_SSL_CA_TRUST_STORE_PASSWORD; yq -i '.singleServerConfig.sslTruststorePassword=env(CACHE_CONFIG_REDIS_SSL_CA_TRUST_STORE_PASSWORD)' $REDISSON_CACHE_FILE
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

if [[ "" != "$NG_MANAGER_GITSYNC_TARGET" ]]; then
  export NG_MANAGER_GITSYNC_TARGET; yq -i '.gitSdkConfiguration.gitManagerGrpcClientConfig.target=env(NG_MANAGER_GITSYNC_TARGET)' $CONFIG_FILE
fi

if [[ "" != "$NG_MANAGER_GITSYNC_AUTHORITY" ]]; then
  export NG_MANAGER_GITSYNC_AUTHORITY; yq -i '.gitSdkConfiguration.gitManagerGrpcClientConfig.authority=env(NG_MANAGER_GITSYNC_AUTHORITY)' $CONFIG_FILE
fi

if [[ "" != "$SCM_SERVICE_URI" ]]; then
  export SCM_SERVICE_URI; yq -i '.gitSdkConfiguration.scmConnectionConfig.url=env(SCM_SERVICE_URI)' $CONFIG_FILE
fi

replace_key_value shouldDeployWithGitSync "$ENABLE_GIT_SYNC"

replace_key_value cacheConfig.cacheNamespace $CACHE_NAMESPACE
replace_key_value cacheConfig.cacheBackend $CACHE_BACKEND
replace_key_value cacheConfig.enterpriseCacheEnabled $ENTERPRISE_CACHE_ENABLED

replace_key_value segmentConfiguration.enabled "$SEGMENT_ENABLED"
replace_key_value segmentConfiguration.url "$SEGMENT_URL"
replace_key_value segmentConfiguration.apiKey "$SEGMENT_APIKEY"
replace_key_value segmentConfiguration.certValidationRequired "$SEGMENT_VERIFY_CERT"

replace_key_value eventsFramework.redis.sentinel $EVENTS_FRAMEWORK_USE_SENTINEL
replace_key_value eventsFramework.redis.envNamespace $EVENTS_FRAMEWORK_ENV_NAMESPACE
replace_key_value eventsFramework.redis.redisUrl $EVENTS_FRAMEWORK_REDIS_URL
replace_key_value eventsFramework.redis.masterName $EVENTS_FRAMEWORK_SENTINEL_MASTER_NAME
replace_key_value eventsFramework.redis.userName $EVENTS_FRAMEWORK_REDIS_USERNAME
replace_key_value eventsFramework.redis.password $EVENTS_FRAMEWORK_REDIS_PASSWORD
replace_key_value eventsFramework.redis.sslConfig.enabled $EVENTS_FRAMEWORK_REDIS_SSL_ENABLED
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePath $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PATH
replace_key_value eventsFramework.redis.sslConfig.CATrustStorePassword $EVENTS_FRAMEWORK_REDIS_SSL_CA_TRUST_STORE_PASSWORD
replace_key_value eventsFramework.redis.retryAttempts $REDIS_RETRY_ATTEMPTS
replace_key_value eventsFramework.redis.retryInterval $REDIS_RETRY_INTERVAL

if [[ "" != "$LOCK_CONFIG_REDIS_SENTINELS" ]]; then
  IFS=',' read -ra SENTINEL_URLS <<< "$LOCK_CONFIG_REDIS_SENTINELS"
  INDEX=0
  for REDIS_SENTINEL_URL in "${SENTINEL_URLS[@]}"; do
    export REDIS_SENTINEL_URL; export INDEX; yq -i '.redisLockConfig.sentinelUrls.[env(INDEX)]=env(REDIS_SENTINEL_URL)' $CONFIG_FILE
    INDEX=$(expr $INDEX + 1)
  done
fi

if [[ "" != "$HSQS_BASE_URL" ]]; then
  export HSQS_BASE_URL; yq -i '.ciExecutionServiceConfig.queueServiceClientConfig.httpClientConfig.baseUrl=env(HSQS_BASE_URL)' $CONFIG_FILE
fi

if [[ "" != "$HSQS_TOPIC" ]]; then
  export HSQS_TOPIC; yq -i '.ciExecutionServiceConfig.queueServiceClientConfig.topic=env(HSQS_TOPIC)' $CONFIG_FILE
fi

if [[ "" != "$HSQS_AUTH_TOKEN" ]]; then
  export HSQS_AUTH_TOKEN; yq -i '.ciExecutionServiceConfig.queueServiceClientConfig.queueServiceSecret=env(HSQS_AUTH_TOKEN)' $CONFIG_FILE
fi

if [[ "" != "$OVERRIDE_EXEC_LIMIT_FOR_ACCOUNT" ]]; then
  export OVERRIDE_EXEC_LIMIT_FOR_ACCOUNT; yq -i '.ciExecutionServiceConfig.executionLimits.overrideConfig[0]=env(OVERRIDE_EXEC_LIMIT_FOR_ACCOUNT)' $CONFIG_FILE
fi

if [[ "$FIPS_ENABLED" == "true" ]]; then
  export STO_STEP_CONFIG_DEFAULT_TAG="${STO_STEP_CONFIG_DEFAULT_TAG:-latest}-fips"
else
  export STO_STEP_CONFIG_DEFAULT_TAG="${STO_STEP_CONFIG_DEFAULT_TAG:-latest}"
fi

if [[ "" != "$QWIET_SHARED_SECRET" ]]; then
  export QWIET_SHARED_SECRET; yq -i '.qwietServiceConfig.globalToken=env(QWIET_SHARED_SECRET)' $CONFIG_FILE
fi

replace_key_value ciExecutionServiceConfig.stoStepConfig.defaultTag "$STO_STEP_CONFIG_DEFAULT_TAG"
replace_key_value ciExecutionServiceConfig.stepConfig.securityConfig.image "harness/sto-plugin:$STO_STEP_CONFIG_DEFAULT_TAG"
replace_key_value ciExecutionServiceConfig.stepConfig.vmImageConfig.security "harness/sto-plugin:$STO_STEP_CONFIG_DEFAULT_TAG"

replace_key_value redisLockConfig.redisUrl "$LOCK_CONFIG_REDIS_URL"
replace_key_value redisLockConfig.envNamespace "$LOCK_CONFIG_ENV_NAMESPACE"
replace_key_value redisLockConfig.sentinel "$LOCK_CONFIG_USE_SENTINEL"
replace_key_value redisLockConfig.masterName "$LOCK_CONFIG_SENTINEL_MASTER_NAME"
replace_key_value redisLockConfig.userName "$LOCK_CONFIG_REDIS_USERNAME"
replace_key_value redisLockConfig.password "$LOCK_CONFIG_REDIS_PASSWORD"
replace_key_value redisLockConfig.nettyThreads "$REDIS_NETTY_THREADS"
replace_key_value redisLockConfig.connectionPoolSize $REDIS_CONNECTION_POOL_SIZE
replace_key_value redisLockConfig.retryInterval $REDIS_RETRY_INTERVAL
replace_key_value redisLockConfig.retryAttempts $REDIS_RETRY_ATTEMPTS
replace_key_value redisLockConfig.timeout $REDIS_TIMEOUT
replace_key_value redisLockConfig.sslConfig.enabled $LOCK_CONFIG_REDIS_SSL_ENABLED
replace_key_value redisLockConfig.sslConfig.CATrustStorePath $LOCK_CONFIG_REDIS_SSL_CA_TRUST_STORE_PATH
replace_key_value redisLockConfig.sslConfig.CATrustStorePassword $LOCK_CONFIG_REDIS_SSL_CA_TRUST_STORE_PASSWORD

replace_key_value enableOpentelemetry "$ENABLE_OPENTELEMETRY"
replace_key_value enableLoopDetection "$ENABLE_LOOPDETECTION"
replace_key_value loopDetectionThreshold "$LOOP_DETECTION_THRESHOLD"
replace_key_value enforcementClientConfiguration.enforcementCheckEnabled "$ENFORCEMENT_CHECK_ENABLED"

replace_key_value policyManagerSecret "$OPA_SERVER_SECRET"
replace_key_value opaClientConfig.baseUrl "$OPA_SERVER_BASEURL"

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

#Changes to use internal connection urls for PMS client gRPC
replace_key_value pmsGrpcClientConfig.target "$INTERNAL_PMS_TARGET"
replace_key_value pmsGrpcClientConfig.authority "$INTERNAL_PMS_AUTHORITY"


replace_key_value managerTarget "$INTERNAL_MANAGER_TARGET"
replace_key_value managerAuthority "$INTERNAL_MANAGER_AUTHORITY"

if [[ "" != "$HARNESS_IMAGE_REPOSITORY" ]]; then
  ESCAPED_HARNESS_IMAGE_REPOSITORY=$(printf '%s' "$HARNESS_IMAGE_REPOSITORY" | sed 's/[&\\/|]/\\&/g')
  sed -i "s| harness/| ${ESCAPED_HARNESS_IMAGE_REPOSITORY}/|g" $CONFIG_FILE
  sed -i "s| plugins/| ${ESCAPED_HARNESS_IMAGE_REPOSITORY}/|g" $CONFIG_FILE
fi