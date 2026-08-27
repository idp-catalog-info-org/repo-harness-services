/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.refresh.helper;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;
import static io.harness.rule.OwnerRule.NAMANG;
import static io.harness.rule.OwnerRule.THRISHANK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.base.NgManagerTestBase;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.cdng.customdeployment.helper.CustomDeploymentEntitySetupHelper;
import io.harness.cdng.envGroup.services.EnvironmentGroupService;
import io.harness.cdng.environment.helper.EnvironmentInfraFilterHelper;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.connector.services.ConnectorService;
import io.harness.eventsframework.api.Producer;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.ng.core.CDGitxSettingHelper;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.environment.helpers.EnvironmentFilterHelper;
import io.harness.ng.core.environment.services.impl.EnvironmentEntityYamlSchemaHelper;
import io.harness.ng.core.environment.services.impl.EnvironmentServiceHelper;
import io.harness.ng.core.environment.services.impl.EnvironmentServiceImpl;
import io.harness.ng.core.infrastructure.dto.NoInputMergeInputAction;
import io.harness.ng.core.infrastructure.services.impl.InfrastructureEntityServiceHelper;
import io.harness.ng.core.infrastructure.services.impl.InfrastructureEntityServiceImpl;
import io.harness.ng.core.infrastructure.services.impl.InfrastructureEntityVersionAwareFacade;
import io.harness.ng.core.infrastructure.services.impl.InfrastructureYamlSchemaHelper;
import io.harness.ng.core.opa.environment.EnvironmentOpaService;
import io.harness.ng.core.opa.gitx.EnvironmentOpaStatusHandler;
import io.harness.ng.core.opa.gitx.InfrastructureOpaStatusHandler;
import io.harness.ng.core.opa.gitx.ServiceOpaStatusHandler;
import io.harness.ng.core.opa.infrastructure.InfrastructureOpaService;
import io.harness.ng.core.opa.service.ServiceOpaService;
import io.harness.ng.core.refresh.bean.EntityRefreshContext;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.impl.ServiceEntityServiceImpl;
import io.harness.ng.core.service.services.impl.ServiceEntitySetupUsageHelper;
import io.harness.ng.core.service.services.impl.ServiceRbacHelper;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
import io.harness.ng.core.serviceoverridev2.service.ServiceOverridesServiceV2;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.template.refresh.v2.InputsValidationResponse;
import io.harness.ng.core.utils.CDGitXService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.ng.core.utils.ServiceOverrideV2ValidationHelper;
import io.harness.ngsettings.SettingValueType;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingValueResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HPersistence;
import io.harness.pms.yaml.YamlNode;
import io.harness.repositories.environment.spring.EnvironmentRepository;
import io.harness.repositories.infrastructure.spring.InfrastructureRepository;
import io.harness.repositories.service.spring.ServiceRepository;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.setupusage.EnvironmentEntitySetupUsageHelper;
import io.harness.setupusage.InfrastructureEntitySetupUsageHelper;
import io.harness.utils.NGFeatureFlagHelperService;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.ScopeResolutionHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import com.google.inject.name.Named;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.CDC)
public class CDInputsValidationHelperTest extends NgManagerTestBase {
  private static final String RESOURCE_PATH_PREFIX = "refresh/validate/";
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  @InjectMocks CDInputsValidationHelper CDInputsValidationHelper;
  @InjectMocks EntityFetchHelper entityFetchHelper;
  @Mock ServiceRepository serviceRepository;
  @Mock EnvironmentRepository environmentRepository;
  @Mock InfrastructureRepository infrastructureRepository;
  @Mock EntitySetupUsageService entitySetupUsageService;
  @Mock Producer eventProducer;
  @Mock TransactionTemplate transactionTemplate;
  @Mock OutboxService outboxService;
  @Mock ServiceOverrideService serviceOverrideService;
  @Mock ServiceEntitySetupUsageHelper entitySetupUsageHelper;
  @Mock ClusterService clusterService;
  @Mock CustomDeploymentEntitySetupHelper customDeploymentEntitySetupHelper;
  @Mock InfrastructureEntitySetupUsageHelper infrastructureEntitySetupUsageHelper;
  @Mock AccountClient accountClient;
  @Mock NGSettingsClient settingsClient;

  @Mock HPersistence hPersistence;
  @Mock EnvironmentEntitySetupUsageHelper environmentEntitySetupUsageHelper;
  @Mock private Call<ResponseDTO<SettingValueResponseDTO>> request;
  @Mock private Call<RestResponse<Boolean>> restRequest;
  @Mock ServiceOverridesServiceV2 serviceOverridesServiceV2;
  @Mock ServiceOverrideV2ValidationHelper overrideV2ValidationHelper;
  ServiceEntityServiceImpl serviceEntityService;
  EnvironmentServiceImpl environmentService;
  InfrastructureEntityServiceImpl infrastructureEntityService;
  EnvironmentRefreshHelper environmentRefreshHelper;
  @Mock @Named(DEFAULT_CONNECTOR_SERVICE) private ConnectorService connectorService;
  @Mock EnvironmentFilterHelper environmentFilterHelper;
  @Mock CDGitXService cdGitXService;
  @Mock GitAwareEntityHelper gitAwareEntityHelper;
  @Mock EnvironmentServiceHelper environmentServiceHelper;
  @Mock InfrastructureEntityServiceHelper infrastructureEntityServiceHelper;
  @Mock ServiceRbacHelper serviceRbacHelper;
  @Mock InfrastructureEntityVersionAwareFacade infraVersionAwareFacade;
  @Mock EnvironmentEntityYamlSchemaHelper environmentEntityYamlSchemaHelper;
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock InfrastructureYamlSchemaHelper infrastructureYamlSchemaHelper;
  @Mock ServiceOpaService serviceOpaService;
  @Mock EnvironmentOpaService environmentOpaService;
  @Mock InfrastructureOpaService infrastructureOpaService;
  @Mock ServiceOpaStatusHandler serviceOpaStatusHandler;
  @Mock EnvironmentOpaStatusHandler environmentOpaStatusHandler;
  @Mock InfrastructureOpaStatusHandler infrastructureOpaStatusHandler;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock ScopeInfoService scopeInfoService;
  @Mock CDGitxSettingHelper cdGitxSettingHelper;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @Mock EnvironmentInfraFilterHelper environmentInfraFilterHelper;
  @Mock EnvironmentGroupService environmentGroupService;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;
  private ScopeInfo scopeInfo;

  @Before
  public void setup() throws IOException {
    serviceEntityService = spy(new ServiceEntityServiceImpl(serviceRepository, entitySetupUsageService, eventProducer,
        outboxService, transactionTemplate, serviceOverrideService, serviceOverridesServiceV2, entitySetupUsageHelper,
        connectorService, cdGitXService, gitAwareEntityHelper, serviceRbacHelper, serviceOpaService,
        scopeResolutionHelper, featureFlagHelperService, serviceOpaStatusHandler));
    infrastructureEntityService = spy(new InfrastructureEntityServiceImpl(infrastructureRepository, transactionTemplate,
        outboxService, customDeploymentEntitySetupHelper, infrastructureEntitySetupUsageHelper, hPersistence,
        serviceOverridesServiceV2, overrideV2ValidationHelper, null, environmentService, gitAwareEntityHelper,
        cdGitXService, infrastructureEntityServiceHelper, infraVersionAwareFacade, orgAndProjectValidationHelper,
        infrastructureYamlSchemaHelper, infrastructureOpaService, featureFlagHelperService, scopeInfoService,
        cdGitxSettingHelper, environmentInfraFilterHelper, infrastructureOpaStatusHandler, null));
    environmentService = spy(new EnvironmentServiceImpl(environmentRepository, entitySetupUsageService, eventProducer,
        outboxService, transactionTemplate, infrastructureEntityService, clusterService, serviceOverrideService,
        serviceOverridesServiceV2, serviceEntityService, accountClient, settingsClient,
        environmentEntitySetupUsageHelper, overrideV2ValidationHelper, environmentFilterHelper, cdGitXService,
        gitAwareEntityHelper, environmentServiceHelper, environmentEntityYamlSchemaHelper,
        orgAndProjectValidationHelper, environmentOpaService, featureFlagHelperService, scopeInfoService,
        cdGitxSettingHelper, scopeResolutionHelper, environmentInfraFilterHelper, environmentOpaStatusHandler,
        () -> environmentGroupService));
    environmentRefreshHelper = spy(new EnvironmentRefreshHelper(environmentService, infrastructureEntityService,
        serviceOverrideService, pmsFeatureFlagHelper, scopeInfoService, serviceOverridesServiceV2, accountClient,
        overrideV2ValidationHelper));
    on(entityFetchHelper).set("serviceEntityService", serviceEntityService);
    on(CDInputsValidationHelper).set("serviceEntityService", serviceEntityService);
    on(CDInputsValidationHelper).set("entityFetchHelper", entityFetchHelper);
    on(CDInputsValidationHelper).set("environmentRefreshHelper", environmentRefreshHelper);
    on(CDInputsValidationHelper).set("chainedPipelineScopeHelper", new ChainedPipelineScopeHelper());

    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_ID)
                    .orgIdentifier(ORG_ID)
                    .projectIdentifier(PROJECT_ID)
                    .uniqueId("uniqueId")
                    .build();
    doReturn(scopeInfo).when(scopeInfoService).getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID);

    SettingValueResponseDTO settingValueResponseDTO =
        SettingValueResponseDTO.builder().value("true").valueType(SettingValueType.BOOLEAN).build();
    doReturn(request).when(settingsClient).getSetting(anyString(), anyString(), anyString(), anyString());
    doReturn(Response.success(ResponseDTO.newResponse(settingValueResponseDTO))).when(request).execute();
    doReturn(restRequest).when(accountClient).isFeatureFlagEnabled(anyString(), anyString());
    RestResponse<Boolean> mockResponse = new RestResponse<>(true);
    doReturn(Response.success(mockResponse)).when(restRequest).execute();
    lenient()
        .when(scopeResolutionHelper.getScopeInfo(anyString(), anyString(), anyString()))
        .thenAnswer(invocation
            -> ScopeInfo.builder()
                   .accountIdentifier(invocation.getArgument(0))
                   .orgIdentifier(invocation.getArgument(1))
                   .projectIdentifier(invocation.getArgument(2))
                   .uniqueId("uniqueId")
                   .build());
    lenient()
        .when(scopeResolutionHelper.getScopeInfo(anyString(), any()))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .uniqueId("uniqueId")
                        .build());
  }

  private String readFile(String filename) {
    String relativePath = RESOURCE_PATH_PREFIX + filename;
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(relativePath)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithValidServiceServiceEnvironmentAndInfra() {
    String pipelineYmlWithService = readFile("pipeline-with-single-service.yaml");
    String serviceYaml = readFile("serverless-service-valid.yaml");

    doReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()))
        .when(serviceEntityService)
        .get(any(), eq("serverless"), eq(false));
    doReturn(null).when(environmentService).createEnvironmentInputsYaml(scopeInfo, "testenv", null);
    doReturn("infrastructureDefinitions:\n"
        + "  - identifier: \"infra2\"\n")
        .when(infrastructureEntityService)
        .createInfrastructureInputsFromYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, scopeInfo, "testenv", null,
            Collections.singletonList("infra2"), false, NoInputMergeInputAction.ADD_IDENTIFIER_NODE);

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithInvalidServiceHavingFixedPrimaryArtifactRef() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-single-service.yaml");
    String serviceYaml = readFile("serverless-service.yaml");

    doReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()))
        .when(serviceEntityService)
        .get(any(), eq("serverless"), eq(false));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithServiceRuntimeAndServiceInputsFixed() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-svc-runtime-serviceInputs-fixed.yaml");

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithServiceRuntimeAndServiceInputsNotPresent() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-svc-runtime-serviceInputs-not-present.yaml");

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithPrimaryRefFixedAndSourcesRuntime() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-primaryRef-fixed-source-runtime.yaml");
    String serviceYaml = readFile("serverless-service.yaml");

    doReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()))
        .when(serviceEntityService)
        .get(any(), eq("serverless"), eq(false));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithServiceInputsEmptyInService() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-single-service.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    doReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()))
        .when(serviceEntityService)
        .get(any(), eq("serverless"), eq(false));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithServiceInputsEmptyInServiceAndNoServiceInputsInLinkedYaml() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-no-serviceInputs.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    doReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()))
        .when(serviceEntityService)
        .get(any(), eq("serverless"), eq(false));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithEnvRefRuntimeButInfraDefsFixed() {
    String pipelineYmlWithService = readFile("env/pipeline-with-env-ref-runtime-and-envInputs-infraDefs-fixed.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    doReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()))
        .when(serviceEntityService)
        .get(any(), eq("serverless"), eq(false));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithEnvRefInfraDefsAndEnvInputsRuntime() {
    String pipelineYmlWithService = readFile("env/pipeline-with-envRef-envInputs-infraDefs-runtime.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    doReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()))
        .when(serviceEntityService)
        .get(any(), eq("serverless"), eq(false));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithEnvRefFixedAndEnvInputsIncorrect() {
    String pipelineYmlWithService = readFile("env/pipeline-with-fixed-envRef-incorrect-envInputs.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    doReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()))
        .when(serviceEntityService)
        .get(any(), eq("serverless"), eq(false));
    doReturn(null).when(environmentService).createEnvironmentInputsYaml(scopeInfo, "testenv", null);

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithEnvRefFixedAndInfraDefsIncorrect() {
    String pipelineYmlWithService = readFile("env/pipeline-with-env-ref-fixed-and-infraDefs-incorrect.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    doReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()))
        .when(serviceEntityService)
        .get(any(), eq("serverless"), eq(false));
    doReturn(null).when(environmentService).createEnvironmentInputsYaml(scopeInfo, "testenv", null);
    doReturn("infrastructureDefinitions:\n"
        + "- identifier: \"IDENTIFIER\"")
        .when(infrastructureEntityService)
        .createInfrastructureInputsFromYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, scopeInfo, "testenv", null,
            Collections.singletonList("IDENTIFIER"), false, NoInputMergeInputAction.ADD_IDENTIFIER_NODE);

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void testValidateInfraInTemplateInputsWithNoEnvRef() {
    String templateWithInfraFixed = readFile("env/pipTemplate-with-infra-fixed.yaml");
    String resolvedTemplateWithInfraFixed = readFile("env/pipTemplate-with-infra-fixed-resoved.yaml");

    doReturn("infrastructureDefinitions:\n"
        + "- identifier: \"infra1\"")
        .when(infrastructureEntityService)
        .createInfrastructureInputsFromYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, scopeInfo, "testenv", null,
            Collections.singletonList("infra1"), false, NoInputMergeInputAction.ADD_IDENTIFIER_NODE);

    InputsValidationResponse validationResponse = CDInputsValidationHelper.validateInputsForYaml(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, templateWithInfraFixed, resolvedTemplateWithInfraFixed);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testValidateInputsForPipelineYamlWithPrimaryRefExpressionAndSourcesRuntime() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-primaryRef-expression-source-runtime.yaml");
    String serviceYaml = readFile("serverless-service.yaml");

    doReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()))
        .when(serviceEntityService)
        .get(any(), eq("serverless"), eq(false));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void
  testValidateInputsForServiceYamlWithPrimaryRefExpressionForPipelineYamlWithPrimaryRefExpressionAndSourcesRuntime() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService = readFile("pipeline-with-primaryRef-expression-source-runtime.yaml");
    String serviceYaml = readFile("serverless-service-with-primary-artifact-ref-expression.yaml");

    doReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()))
        .when(serviceEntityService)
        .get(any(), eq("serverless"), eq(false));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void
  testValidateInputsForServiceYamlWithPrimaryRefExpressionForPipelineYamlWithPrimaryRefExpressionAndSourcesRuntimeButDifferentExpression() {
    doNothing()
        .when(environmentRefreshHelper)
        .validateEnvironmentInputs(
            any(YamlNode.class), any(EntityRefreshContext.class), any(InputsValidationResponse.class));
    String pipelineYmlWithService =
        readFile("pipeline-with-primaryRef-expression-different-from-service-source-runtime.yaml");
    String serviceYaml = readFile("serverless-service-with-primary-artifact-ref-expression.yaml");

    doReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()))
        .when(serviceEntityService)
        .get(any(), eq("serverless"), eq(false));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYmlWithService, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isFalse();
    assertThat(validationResponse.getChildrenErrorNodes()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testValidateInputsWithInvalidResolvedTemplatesYamlThrowsException() {
    String entityNodeYaml = readFile("pipeline-with-use-from-stage-service.yaml");
    String resolvedTemplatesYaml = readFile("template-with-both-useFromStage-and-serviceRef-field.yaml");

    when(serviceEntityService.isServiceField(eq("service"), any(JsonNode.class))).thenReturn(true);

    assertThatThrownBy(()
                           -> CDInputsValidationHelper.validateInputsForYaml(
                               ACCOUNT_ID, ORG_ID, PROJECT_ID, entityNodeYaml, resolvedTemplatesYaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Service in stage [s1] cannot be propagated from it's previous stages if this service is "
            + "already defined as a fixed or an expression value in the template or pipeline yaml.");
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testValidateInputsForCrossOrgChainedPipelineUsesChildScope() {
    String pipelineYaml = readFile("chained-pipeline-cross-org.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    doReturn(Optional.empty()).when(serviceEntityService).get(any(), eq("iisDealtool"), eq(false));
    doReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()))
        .when(serviceEntityService)
        .get(argThat(scope -> matchesScope(scope, "WIN", "WIN_1384")), eq("iisDealtool"), eq(false));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    verify(serviceEntityService)
        .get(argThat(scope -> matchesScope(scope, "WIN", "WIN_1384")), eq("iisDealtool"), eq(false));
    verify(serviceEntityService, never())
        .get(argThat(scope -> matchesScope(scope, ORG_ID, PROJECT_ID)), eq("iisDealtool"), eq(false));
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testValidateInputsForSameOrgChainedPipeline() {
    String pipelineYaml = readFile("chained-pipeline-same-org.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    doReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()))
        .when(serviceEntityService)
        .get(argThat(scope -> matchesScope(scope, ORG_ID, PROJECT_ID)), eq("serverless"), eq(false));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    verify(serviceEntityService)
        .get(argThat(scope -> matchesScope(scope, ORG_ID, PROJECT_ID)), eq("serverless"), eq(false));
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testValidateInputsForNestedChainedPipelineUsesGrandchildScope() {
    String pipelineYaml = readFile("chained-pipeline-nested.yaml");
    String serviceYaml = readFile("serverless-service-with-all-values-fixed.yaml");

    doReturn(Optional.empty()).when(serviceEntityService).get(any(), eq("grandchildSvc"), eq(false));
    doReturn(Optional.of(ServiceEntity.builder().yaml(serviceYaml).build()))
        .when(serviceEntityService)
        .get(argThat(scope -> matchesScope(scope, "grandchildOrg", "grandchildProject")), eq("grandchildSvc"),
            eq(false));

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    verify(serviceEntityService)
        .get(argThat(scope -> matchesScope(scope, "grandchildOrg", "grandchildProject")), eq("grandchildSvc"),
            eq(false));
  }

  @Test
  @Owner(developers = THRISHANK)
  @Category(UnitTests.class)
  public void testValidateInputsSkipsChainedPipelineWhenChildScopeIsUnresolvable() {
    String pipelineYaml = readFile("chained-pipeline-unresolvable-scope.yaml");

    InputsValidationResponse validationResponse =
        CDInputsValidationHelper.validateInputsForYaml(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineYaml, null);
    assertThat(validationResponse).isNotNull();
    assertThat(validationResponse.isValid()).isTrue();
    verify(serviceEntityService, never()).get(any(), eq("iisDealtool"), eq(false));
  }

  private static boolean matchesScope(ScopeInfo scope, String org, String project) {
    return scope != null && org.equals(scope.getOrgIdentifier()) && project.equals(scope.getProjectIdentifier());
  }
}
