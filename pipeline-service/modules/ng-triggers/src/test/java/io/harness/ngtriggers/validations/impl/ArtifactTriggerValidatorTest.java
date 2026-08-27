/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.ngtriggers.validations.impl;

import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;
import static io.harness.rule.OwnerRule.ROHITKARELIA;
import static io.harness.rule.OwnerRule.VINICIUS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.HintException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.buildtriggers.helpers.BuildTriggerHelper;
import io.harness.ngtriggers.buildtriggers.helpers.dtos.BuildTriggerOpsData;
import io.harness.ngtriggers.buildtriggers.helpers.generator.CustomPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.DockerRegistryPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GcrPollingItemGenerator;
import io.harness.ngtriggers.buildtriggers.helpers.generator.GeneratorFactory;
import io.harness.ngtriggers.mapper.NGTriggerElementMapper;
import io.harness.ngtriggers.validations.result.ValidationResult;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.pms.pipeline.TemplatesResolvedPipelineResponseDTO;
import io.harness.polling.contracts.PollingItem;
import io.harness.rule.Owner;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class ArtifactTriggerValidatorTest extends CategoryTest {
  @InjectMocks @Inject private NGTriggerElementMapper ngTriggerElementMapper;
  @Mock private PipelineServiceClient pipelineServiceClient;
  private DockerRegistryPollingItemGenerator dockerRegistryPollingItemGenerator;

  private GcrPollingItemGenerator gcrPollingItemGenerator;
  @Mock private GeneratorFactory generatorFactory;
  private BuildTriggerHelper buildTriggerHelper;
  private ArtifactTriggerValidator artifactTriggerValidator;

  private String ngTriggerYaml_artifact_dockerregistry;
  private String pipelineYaml;

  private String gcr_artifact_trigger;
  private String gcr_artifact_pipeline;
  private String customArtifactInlineScriptTrigger;
  @Before
  public void setUp() throws IOException {
    MockitoAnnotations.initMocks(this);
    ClassLoader classLoader = getClass().getClassLoader();
    ngTriggerYaml_artifact_dockerregistry = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-artifact-dockerregistry-v2.yaml")),
        StandardCharsets.UTF_8);
    pipelineYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource("pipeline.yaml")), StandardCharsets.UTF_8);

    gcr_artifact_trigger = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("gcr_artifact_trigger.yaml")), StandardCharsets.UTF_8);

    gcr_artifact_pipeline = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("gcr_artifact_pipeline.yaml")), StandardCharsets.UTF_8);

    customArtifactInlineScriptTrigger = Resources.toString(
        Objects.requireNonNull(classLoader.getResource("ng-trigger-custom-artifact-inline-script.yaml")),
        StandardCharsets.UTF_8);

    buildTriggerHelper = spy(new BuildTriggerHelper(pipelineServiceClient));
    artifactTriggerValidator = new ArtifactTriggerValidator(buildTriggerHelper, generatorFactory);
    dockerRegistryPollingItemGenerator = new DockerRegistryPollingItemGenerator(buildTriggerHelper);
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testValidateBasedOnArtifactType() throws Exception {
    TriggerDetails triggerDetails = ngTriggerElementMapper.toTriggerDetails(
        "account", "org", "proj", null, ngTriggerYaml_artifact_dockerregistry, true);
    BuildTriggerOpsData buildTriggerOpsData =
        buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(triggerDetails, "");
    when(generatorFactory.retrievePollingItemGenerator(any())).thenReturn(dockerRegistryPollingItemGenerator);
    artifactTriggerValidator.validateBasedOnArtifactType(buildTriggerOpsData, null, false);

    // invalid trigger
    buildTriggerOpsData.getTriggerSpecMap().clear();
    assertThatThrownBy(() -> artifactTriggerValidator.validateBasedOnArtifactType(buildTriggerOpsData, null, false))
        .isInstanceOf(HintException.class)
        .hasMessage("Expression type might contain some unresolved expressions which could not be evaluated.");
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testValidateBasedOnArtifactTypeThrowsException() throws Exception {
    TriggerDetails triggerDetails = ngTriggerElementMapper.toTriggerDetails(
        "account", "org", "proj", null, ngTriggerYaml_artifact_dockerregistry, true);
    BuildTriggerOpsData buildTriggerOpsData =
        buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(triggerDetails, "");
    when(generatorFactory.retrievePollingItemGenerator(any())).thenReturn(null);
    assertThatThrownBy(() -> artifactTriggerValidator.validateBasedOnArtifactType(buildTriggerOpsData, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Failed to find Polling Generator For Trigger. Please Check Manifest Config In Trigger");
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testValidate() throws IOException {
    BuildTriggerHelper validationHelper = new BuildTriggerHelper(pipelineServiceClient);
    ArtifactTriggerValidator spyArtifactTriggerValidator =
        spy(new ArtifactTriggerValidator(validationHelper, generatorFactory));
    TriggerDetails triggerDetails = ngTriggerElementMapper.toTriggerDetails(
        "account", "org", "proj", null, ngTriggerYaml_artifact_dockerregistry, true);
    Call<ResponseDTO<TemplatesResolvedPipelineResponseDTO>> templatesResolvedPipelineDTO = mock(Call.class);
    when(pipelineServiceClient.getResolvedTemplatesPipelineByIdentifier(
             triggerDetails.getNgTriggerEntity().getTargetIdentifier(), "account", "org", "proj",
             triggerDetails.getNgTriggerConfigV2().getPipelineBranchName(), null, false, "true"))
        .thenReturn(templatesResolvedPipelineDTO);
    when(templatesResolvedPipelineDTO.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            TemplatesResolvedPipelineResponseDTO.builder().resolvedTemplatesPipelineYaml(pipelineYaml).build())));
    doNothing().when(spyArtifactTriggerValidator).validateBasedOnArtifactType(any(), any(), anyBoolean());
    when(generatorFactory.retrievePollingItemGenerator(any())).thenReturn(dockerRegistryPollingItemGenerator);
    ValidationResult validate = spyArtifactTriggerValidator.validate(triggerDetails, null, false);
    assertThat(validate.isSuccess()).isTrue();
  }

  @Test
  @Owner(developers = ROHITKARELIA)
  @Category(UnitTests.class)
  public void testValidateThrowsExceptionForServiceV1() throws IOException {
    BuildTriggerHelper validationHelper = new BuildTriggerHelper(pipelineServiceClient);
    ArtifactTriggerValidator spyArtifactTriggerValidator =
        spy(new ArtifactTriggerValidator(validationHelper, generatorFactory));
    TriggerDetails triggerDetails =
        ngTriggerElementMapper.toTriggerDetails("account", "org", "proj", null, gcr_artifact_trigger, false);
    Call<ResponseDTO<TemplatesResolvedPipelineResponseDTO>> templatesResolvedPipelineDTO = mock(Call.class);
    when(pipelineServiceClient.getResolvedTemplatesPipelineByIdentifier(
             triggerDetails.getNgTriggerEntity().getTargetIdentifier(), "account", "org", "proj",
             triggerDetails.getNgTriggerConfigV2().getPipelineBranchName(), null, false, "true"))
        .thenReturn(templatesResolvedPipelineDTO);
    when(templatesResolvedPipelineDTO.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(TemplatesResolvedPipelineResponseDTO.builder()
                                                                 .resolvedTemplatesPipelineYaml(gcr_artifact_pipeline)
                                                                 .build())));
    doNothing().when(spyArtifactTriggerValidator).validateBasedOnArtifactType(any(), any(), anyBoolean());
    when(generatorFactory.retrievePollingItemGenerator(any())).thenReturn(gcrPollingItemGenerator);
    ValidationResult validate = spyArtifactTriggerValidator.validate(triggerDetails, null, false);
    assertThat(validate.isSuccess()).isFalse();
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testFetchValueFromJsonNodeReturnsEmptyForMissingNestedPath() throws Exception {
    // Probing a path that doesn't exist on the trigger spec map must return EMPTY, not throw.
    // CustomPollingItemGenerator relies on this contract to fall back from
    // "spec.scripts.fetchAllArtifacts.spec.source.spec.script" (Format A, NG service v2)
    // to "spec.script" (Format B, legacy/inline). After the JEXL 3.5 upgrade this probe
    // started throwing, breaking trigger save for all CustomArtifact triggers.
    TriggerDetails triggerDetails = ngTriggerElementMapper.toTriggerDetails(
        "account", "org", "proj", null, customArtifactInlineScriptTrigger, true);
    BuildTriggerOpsData opsData = buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(triggerDetails, "");

    String missingPath = buildTriggerHelper.fetchValueFromJsonNode(
        "spec.scripts.fetchAllArtifacts.spec.source.spec.script", opsData.getTriggerSpecMap());
    assertThat(missingPath).isEmpty();

    String inlineScript = buildTriggerHelper.fetchValueFromJsonNode("spec.script", opsData.getTriggerSpecMap());
    assertThat(inlineScript).contains("curl -X POST");
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testCustomPollingItemGeneratorFallsBackToInlineScriptWhenNestedPathMissing() throws Exception {
    // Verifies the probe-then-fallback flow inside CustomPollingItemGenerator end-to-end.
    // The trigger uses Format B (inline `spec.script`) — the generator must fall back from the
    // Format A probe and emit a polling item with the inline script preserved.
    TriggerDetails triggerDetails = ngTriggerElementMapper.toTriggerDetails(
        "account", "org", "proj", null, customArtifactInlineScriptTrigger, true);
    BuildTriggerOpsData opsData = buildTriggerHelper.generateBuildTriggerOpsDataForArtifact(triggerDetails, "");

    CustomPollingItemGenerator customGenerator = new CustomPollingItemGenerator(buildTriggerHelper);
    PollingItem pollingItem = customGenerator.generatePollingItem(opsData, null, false);

    String pollingScript = pollingItem.getPollingPayloadData().getCustomPayload().getScript();
    assertThat(pollingScript).contains("curl -X POST");
    assertThat(pollingScript).contains("<+secrets.getValue(\"jfrog_Auth\")>");
    assertThat(pollingItem.getPollingPayloadData().getCustomPayload().getArtifactsArrayPath()).isEqualTo("$.results");
    assertThat(pollingItem.getPollingPayloadData().getCustomPayload().getVersionPath()).isEqualTo("name");
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testValidateSucceedsForCustomArtifactTriggerWithInlineScript() throws Exception {
    // Reproduces CDS-126016: full validator path for a CustomArtifact trigger with an inline script.
    // Pre-fix this returned `success=false` with "might contain some unresolved expressions" because
    // the Format A probe in fetchValueFromJsonNode threw under JEXL 3.5. After the fix the probe
    // returns EMPTY, the fallback to `spec.script` succeeds, and validation passes.
    BuildTriggerHelper validationHelper = new BuildTriggerHelper(pipelineServiceClient);
    ArtifactTriggerValidator spyValidator = spy(new ArtifactTriggerValidator(validationHelper, generatorFactory));
    TriggerDetails triggerDetails = ngTriggerElementMapper.toTriggerDetails(
        "account", "org", "proj", null, customArtifactInlineScriptTrigger, true);

    Call<ResponseDTO<TemplatesResolvedPipelineResponseDTO>> templatesCall = mock(Call.class);
    when(pipelineServiceClient.getResolvedTemplatesPipelineByIdentifier(
             triggerDetails.getNgTriggerEntity().getTargetIdentifier(), "account", "org", "proj",
             triggerDetails.getNgTriggerConfigV2().getPipelineBranchName(), null, false, "true"))
        .thenReturn(templatesCall);
    when(templatesCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            TemplatesResolvedPipelineResponseDTO.builder()
                .resolvedTemplatesPipelineYaml("pipeline:\n  identifier: pipeline\n  stages: []\n")
                .build())));
    when(generatorFactory.retrievePollingItemGenerator(any()))
        .thenReturn(new CustomPollingItemGenerator(validationHelper));

    ValidationResult result = spyValidator.validate(triggerDetails, null, false);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getMessage()).isNull();
  }
}
