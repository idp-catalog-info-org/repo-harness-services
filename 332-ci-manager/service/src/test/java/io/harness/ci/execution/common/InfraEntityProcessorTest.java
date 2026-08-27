/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.rule.OwnerRule.CHIRAG_S;
import static io.harness.unified.service.NGOutcomes.INFRA_V0_OUTCOME;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.app.beans.entities.EnvironmentEntity;
import io.harness.app.beans.entities.InfrastructureEntity;
import io.harness.beans.common.VariablesSweepingOutput;
import io.harness.category.element.UnitTests;
import io.harness.cd.beans.outcomes.EnvironmentOutcome;
import io.harness.ci.cd.service.EnvironmentEntityService;
import io.harness.ci.cd.service.InfrastructureEntityService;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ConnectorInputsMapper;
import io.harness.expression.common.ExpressionMode;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.infrastructure.InfrastructureResourceClient;
import io.harness.infrastructure.unified.UnifiedGitEntityInfoResponseDTO;
import io.harness.infrastructure.unified.UnifiedInfraConverterResponseDTO;
import io.harness.infrastructure.unified.UnifiedInfraConvertorResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.unified.service.NGOutcomes;
import io.harness.utils.CDStepsExpressionResolver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
public class InfraEntityProcessorTest {
  private static final String ACCOUNT_ID = "account";
  private static final String ORG_ID = "org";
  private static final String PROJECT_ID = "project";
  private static final String ENV_ID = "env1";
  private static final String INFRA_ID = "infra1";
  private static final String SERVICE_REF = "svc1";

  /** Minimal YAML that deserializes to {@link io.harness.unified.cd.infrastructure.InfraConfig} (k8s-direct). */
  private static final String MINIMAL_INFRA_YAML = "infrastructure:\n"
      + "  uses: k8s-direct\n"
      + "  with:\n"
      + "    connector: account.testconnector\n"
      + "    namespace: harness-delegate\n"
      + "    release: release-test\n";

  @Mock private InfrastructureEntityService infrastructureEntityService;
  @Mock private EnvironmentEntityService environmentEntityService;
  @Mock private InfrastructureResourceClient infrastructureResourceClient;
  @Mock private CDStepsExpressionResolver cdStepsExpressionResolver;
  @Mock private ConnectorInputsMapper connectorInputsMapper;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;
  @Mock private EnvOutcomeHelper envOutcomeHelper;
  @Mock private RuntimeExpressionConversionHelper expressionConversionHelper;
  @InjectMocks private InfraEntityProcessor infraEntityProcessor;

  private static ParameterField<String> exprField(String expr) {
    return ParameterField.createExpressionField(true, expr, null, true);
  }

  private static Ambiance ambiance() {
    return Ambiance.newBuilder().build();
  }

  private static ParameterField<String> branchField() {
    return ParameterField.createValueField("envBranch");
  }

  // region static helpers (validateRefs, getInfraInputsYaml)

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateRefsAndThrowWithNullEnvironmentRef() {
    assertThatThrownBy(()
                           -> InfraEntityProcessor.validateRefsAndThrow(ParameterField.ofNull(),
                               ParameterField.createValueField("infra1"), ParameterField.createValueField("svc1")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Environment reference is missing");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateRefsAndThrowWithNullInfraId() {
    assertThatThrownBy(()
                           -> InfraEntityProcessor.validateRefsAndThrow(ParameterField.createValueField("env1"),
                               ParameterField.ofNull(), ParameterField.createValueField("svc1")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Infrastructure identifier is missing");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateRefsAndThrowWithNullServiceRef() {
    assertThatThrownBy(()
                           -> InfraEntityProcessor.validateRefsAndThrow(ParameterField.createValueField("env1"),
                               ParameterField.createValueField("infra1"), ParameterField.ofNull()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Service reference is missing");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateRefsAndThrowWithValidRefs() {
    InfraEntityProcessor.validateRefsAndThrow(ParameterField.createValueField("env1"),
        ParameterField.createValueField("infra1"), ParameterField.createValueField("svc1"));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateRefsAndThrowEnvironmentRefExpression() {
    assertThatThrownBy(
        ()
            -> InfraEntityProcessor.validateRefsAndThrow(exprField("<+pipeline.stages.stage.spec.environment>"),
                ParameterField.createValueField("infra1"), ParameterField.createValueField("svc1")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("has not been resolved");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateRefsAndThrowInfraIdExpression() {
    assertThatThrownBy(()
                           -> InfraEntityProcessor.validateRefsAndThrow(ParameterField.createValueField("env1"),
                               exprField("<+infra>"), ParameterField.createValueField("svc1")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("has not been resolved");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testValidateRefsAndThrowServiceRefExpression() {
    assertThatThrownBy(()
                           -> InfraEntityProcessor.validateRefsAndThrow(ParameterField.createValueField("env1"),
                               ParameterField.createValueField("infra1"), exprField("<+service>")))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("has not been resolved");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetInfraInputsYamlWithNull() {
    String result = InfraEntityProcessor.getInfraInputsYaml(ParameterField.ofNull());
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetInfraInputsYamlWithEmptyMap() {
    String result = InfraEntityProcessor.getInfraInputsYaml(ParameterField.createValueField(new HashMap<>()));
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetInfraInputsYamlWithOverlay() {
    Map<String, Object> inputs = new HashMap<>();
    Map<String, Object> overlay = new HashMap<>();
    overlay.put("namespace", "default");
    inputs.put("overlay", overlay);
    String result = InfraEntityProcessor.getInfraInputsYaml(ParameterField.createValueField(inputs));
    assertThat(result).contains("namespace");
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetInfraInputsYamlWithoutOverlayReturnsEmpty() {
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("otherKey", "v");
    String result = InfraEntityProcessor.getInfraInputsYaml(ParameterField.createValueField(inputs));
    assertThat(result).isEmpty();
  }

  // endregion

  // region getGetInfraTaskExecutionMetadata CG path

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetGetInfraTaskExecutionMetadataCgPathSuccess() {
    InfrastructureEntity infraEntity = InfrastructureEntity.builder()
                                           .accountId(ACCOUNT_ID)
                                           .orgIdentifier(ORG_ID)
                                           .projectIdentifier(PROJECT_ID)
                                           .envIdentifier(ENV_ID)
                                           .identifier(INFRA_ID)
                                           .name("Infra")
                                           .description("desc")
                                           .yaml(MINIMAL_INFRA_YAML)
                                           .tags(Collections.emptyList())
                                           .build();
    when(infrastructureEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID))
        .thenReturn(Optional.of(infraEntity));

    EnvironmentEntity envEntity = EnvironmentEntity.builder()
                                      .accountId(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .identifier(ENV_ID)
                                      .name("Env")
                                      .build();
    when(environmentEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID)).thenReturn(Optional.of(envEntity));

    EnvironmentOutcome envOutcome = EnvironmentOutcome.builder().identifier(ENV_ID).ref(ENV_ID).name("Env").build();
    when(envOutcomeHelper.getEnvironmentOutcome(eq(ENV_ID), eq(envEntity), isNull())).thenReturn(envOutcome);

    ProcessedInfraResult result = infraEntityProcessor.getGetInfraTaskExecutionMetadata(ambiance(), ACCOUNT_ID, ORG_ID,
        PROJECT_ID, ParameterField.createValueField(SERVICE_REF), ParameterField.createValueField(ENV_ID),
        ParameterField.createValueField(INFRA_ID), ParameterField.ofNull(), branchField(), null);

    assertThat(result.getServiceRef()).isEqualTo(SERVICE_REF);
    assertThat(result.getEnvRef()).isEqualTo(ENV_ID);
    assertThat(result.getInfraId()).isEqualTo(INFRA_ID);
    assertThat(result.getEnvironmentOutcome()).isSameAs(envOutcome);
    assertThat(result.getInfraMetadata().getIdentifier()).isEqualTo(INFRA_ID);
    assertThat(result.getInfraConfig()).isNotNull();
    verify(sweepingOutputService, atLeastOnce())
        .consumeUpsert(any(), eq("infraOutput"), any(), eq(StepCategory.STAGE.name()));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetGetInfraTaskExecutionMetadataCgPathEnvironmentMissing() {
    InfrastructureEntity infraEntity = InfrastructureEntity.builder()
                                           .accountId(ACCOUNT_ID)
                                           .orgIdentifier(ORG_ID)
                                           .projectIdentifier(PROJECT_ID)
                                           .envIdentifier(ENV_ID)
                                           .identifier(INFRA_ID)
                                           .yaml(MINIMAL_INFRA_YAML)
                                           .build();
    when(infrastructureEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID))
        .thenReturn(Optional.of(infraEntity));
    when(environmentEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID)).thenReturn(Optional.empty());

    assertThatThrownBy(
        ()
            -> infraEntityProcessor.getGetInfraTaskExecutionMetadata(ambiance(), ACCOUNT_ID, ORG_ID, PROJECT_ID,
                ParameterField.createValueField(SERVICE_REF), ParameterField.createValueField(ENV_ID),
                ParameterField.createValueField(INFRA_ID), ParameterField.ofNull(), branchField(), null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Parent environment")
        .hasMessageContaining(ENV_ID)
        .hasMessageContaining(INFRA_ID);
  }

  // endregion

  // region getGetInfraTaskExecutionMetadata NG path

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetGetInfraTaskExecutionMetadataNgPathSuccessWithV0SavesAndNgOutcomes() {
    when(infrastructureEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID))
        .thenReturn(Optional.empty());

    VariablesSweepingOutput ngOutcomes = new VariablesSweepingOutput();
    ngOutcomes.put(NGOutcomes.SERVICE.getName(), "serviceKey: svcVal\n");
    ngOutcomes.put(NGOutcomes.ENVIRONMENT.getName(), "envKey: envVal\n");
    when(sweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(true).output(ngOutcomes).build());

    GitEntityInfo pipelineGit = GitEntityInfo.builder().branch("pipeBranch").parentEntityRepoName("pipeRepo").build();
    GitEntityInfo envGit = GitEntityInfo.builder().branch("envGitBranch").parentEntityRepoName("envRepo").build();
    UnifiedGitEntityInfoResponseDTO gitDetailsDto =
        UnifiedGitEntityInfoResponseDTO.builder().gitEntityInfo(envGit).build();

    UnifiedInfraConverterResponseDTO responseDto =
        UnifiedInfraConverterResponseDTO.builder()
            .mergedInfrastructureYaml(MINIMAL_INFRA_YAML)
            .identifier(INFRA_ID)
            .name("remote infra")
            .description("d")
            .tags(Map.of("t", "1"))
            .infraV0OutcomeYaml("v0Outcome: data\n")
            .infraV0Yaml("infrastructureDefinition:\n  spec:\n    connectorRef: conn1\n")
            .build();
    UnifiedInfraConvertorResponse unifiedResponse =
        UnifiedInfraConvertorResponse.builder().responseDTO(responseDto).build();

    EnvironmentOutcome ngEnvOutcome = EnvironmentOutcome.builder().identifier(ENV_ID).build();
    when(envOutcomeHelper.getEnvironmentOutcomeFromNGInfraResponse(eq(ENV_ID), eq(unifiedResponse), isNull()))
        .thenReturn(ngEnvOutcome);

    @SuppressWarnings("unchecked") Call<ResponseDTO<UnifiedGitEntityInfoResponseDTO>> gitCall = mock(Call.class);
    @SuppressWarnings("unchecked") Call<ResponseDTO<UnifiedInfraConvertorResponse>> convertCall = mock(Call.class);
    when(infrastructureResourceClient.getInfraGitDetails(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), eq("envBranch"), eq("pipeBranch"), eq("pipeRepo")))
        .thenReturn(gitCall);
    when(infrastructureResourceClient.convertToUnified(eq(INFRA_ID), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID),
             eq(ENV_ID), eq("envGitBranch"), eq("envRepo"), any()))
        .thenReturn(convertCall);

    try (MockedStatic<GitContextHelper> gitCtx = mockStatic(GitContextHelper.class);
         MockedStatic<NGRestUtils> ngRest = mockStatic(NGRestUtils.class)) {
      gitCtx.when(GitContextHelper::getGitEntityInfo).thenReturn(pipelineGit);
      ngRest.when(() -> NGRestUtils.getResponse(gitCall)).thenReturn(gitDetailsDto);
      ngRest.when(() -> NGRestUtils.getResponse(convertCall)).thenReturn(unifiedResponse);

      ProcessedInfraResult result = infraEntityProcessor.getGetInfraTaskExecutionMetadata(ambiance(), ACCOUNT_ID,
          ORG_ID, PROJECT_ID, ParameterField.createValueField(SERVICE_REF), ParameterField.createValueField(ENV_ID),
          ParameterField.createValueField(INFRA_ID), ParameterField.ofNull(), branchField(), null);

      assertThat(result.getEnvironmentOutcome()).isSameAs(ngEnvOutcome);
      assertThat(result.getInfraMetadata().getName()).isEqualTo("remote infra");
      verify(cdStepsExpressionResolver)
          .updateExpressions(any(), eq(unifiedResponse), eq(ExpressionMode.RETURN_ORIGINAL_EXPRESSION_IF_UNRESOLVED));
      verify(sweepingOutputService, atLeastOnce())
          .consumeUpsert(any(), eq(INFRA_V0_OUTCOME), any(), eq(StepCategory.STAGE.name()));
      verify(sweepingOutputService, atLeastOnce())
          .consumeUpsert(any(), eq("ngInfra"), any(), eq(StepCategory.STAGE.name()));
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetGetInfraTaskExecutionMetadataNgPathNoMergedYamlThrows() {
    when(infrastructureEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID))
        .thenReturn(Optional.empty());
    when(sweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    GitEntityInfo pipelineGit = GitEntityInfo.builder().branch("pipeBranch").parentEntityRepoName("pipeRepo").build();
    GitEntityInfo envGit = GitEntityInfo.builder().branch("b").parentEntityRepoName("r").build();
    UnifiedGitEntityInfoResponseDTO gitDetailsDto =
        UnifiedGitEntityInfoResponseDTO.builder().gitEntityInfo(envGit).build();

    UnifiedInfraConverterResponseDTO responseDto =
        UnifiedInfraConverterResponseDTO.builder().mergedInfrastructureYaml("").identifier(INFRA_ID).build();
    UnifiedInfraConvertorResponse unifiedResponse =
        UnifiedInfraConvertorResponse.builder().responseDTO(responseDto).build();

    @SuppressWarnings("unchecked") Call<ResponseDTO<UnifiedGitEntityInfoResponseDTO>> gitCall = mock(Call.class);
    @SuppressWarnings("unchecked") Call<ResponseDTO<UnifiedInfraConvertorResponse>> convertCall = mock(Call.class);
    when(infrastructureResourceClient.getInfraGitDetails(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), any(), any(), any()))
        .thenReturn(gitCall);
    when(infrastructureResourceClient.convertToUnified(
             eq(INFRA_ID), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), any(), any(), any()))
        .thenReturn(convertCall);

    try (MockedStatic<GitContextHelper> gitCtx = mockStatic(GitContextHelper.class);
         MockedStatic<NGRestUtils> ngRest = mockStatic(NGRestUtils.class)) {
      gitCtx.when(GitContextHelper::getGitEntityInfo).thenReturn(pipelineGit);
      ngRest.when(() -> NGRestUtils.getResponse(gitCall)).thenReturn(gitDetailsDto);
      ngRest.when(() -> NGRestUtils.getResponse(convertCall)).thenReturn(unifiedResponse);

      assertThatThrownBy(
          ()
              -> infraEntityProcessor.getGetInfraTaskExecutionMetadata(ambiance(), ACCOUNT_ID, ORG_ID, PROJECT_ID,
                  ParameterField.createValueField(SERVICE_REF), ParameterField.createValueField(ENV_ID),
                  ParameterField.createValueField(INFRA_ID), ParameterField.ofNull(), branchField(), null))
          .isInstanceOf(InvalidRequestException.class)
          .hasMessageContaining("No infrastructure entity found")
          .hasMessageContaining(INFRA_ID)
          .hasMessageContaining(ENV_ID);
    }
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testGetGetInfraTaskExecutionMetadataNgPathMalformedInfraV0YamlSkipsNgInfraUpsert() {
    when(infrastructureEntityService.get(ACCOUNT_ID, ORG_ID, PROJECT_ID, ENV_ID, INFRA_ID))
        .thenReturn(Optional.empty());
    when(sweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    GitEntityInfo pipelineGit = GitEntityInfo.builder().branch("pipeBranch").parentEntityRepoName("pipeRepo").build();
    GitEntityInfo envGit = GitEntityInfo.builder().branch("b").parentEntityRepoName("r").build();
    UnifiedGitEntityInfoResponseDTO gitDetailsDto =
        UnifiedGitEntityInfoResponseDTO.builder().gitEntityInfo(envGit).build();

    UnifiedInfraConverterResponseDTO responseDto = UnifiedInfraConverterResponseDTO.builder()
                                                       .mergedInfrastructureYaml(MINIMAL_INFRA_YAML)
                                                       .identifier(INFRA_ID)
                                                       .name("n")
                                                       .infraV0Yaml("{{{not_valid_yaml")
                                                       .build();
    UnifiedInfraConvertorResponse unifiedResponse =
        UnifiedInfraConvertorResponse.builder().responseDTO(responseDto).build();

    EnvironmentOutcome ngEnvOutcome = EnvironmentOutcome.builder().identifier(ENV_ID).build();
    when(envOutcomeHelper.getEnvironmentOutcomeFromNGInfraResponse(any(), any(), isNull())).thenReturn(ngEnvOutcome);

    @SuppressWarnings("unchecked") Call<ResponseDTO<UnifiedGitEntityInfoResponseDTO>> gitCall = mock(Call.class);
    @SuppressWarnings("unchecked") Call<ResponseDTO<UnifiedInfraConvertorResponse>> convertCall = mock(Call.class);
    when(infrastructureResourceClient.getInfraGitDetails(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), any(), any(), any()))
        .thenReturn(gitCall);
    when(infrastructureResourceClient.convertToUnified(
             eq(INFRA_ID), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(ENV_ID), any(), any(), any()))
        .thenReturn(convertCall);

    try (MockedStatic<GitContextHelper> gitCtx = mockStatic(GitContextHelper.class);
         MockedStatic<NGRestUtils> ngRest = mockStatic(NGRestUtils.class)) {
      gitCtx.when(GitContextHelper::getGitEntityInfo).thenReturn(pipelineGit);
      ngRest.when(() -> NGRestUtils.getResponse(gitCall)).thenReturn(gitDetailsDto);
      ngRest.when(() -> NGRestUtils.getResponse(convertCall)).thenReturn(unifiedResponse);

      ProcessedInfraResult result = infraEntityProcessor.getGetInfraTaskExecutionMetadata(ambiance(), ACCOUNT_ID,
          ORG_ID, PROJECT_ID, ParameterField.createValueField(SERVICE_REF), ParameterField.createValueField(ENV_ID),
          ParameterField.createValueField(INFRA_ID), ParameterField.ofNull(), branchField(), null);

      assertThat(result).isNotNull();
      verify(sweepingOutputService, never()).consumeUpsert(any(), eq("ngInfra"), any(), eq(StepCategory.STAGE.name()));
    }
  }

  // endregion
}
