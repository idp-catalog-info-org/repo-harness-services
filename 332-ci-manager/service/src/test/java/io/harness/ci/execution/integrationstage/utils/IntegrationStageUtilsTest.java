/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage.utils;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.connector.utils.ConnectorType.DOCKER;
import static io.harness.rule.OwnerRule.ABHAY;
import static io.harness.rule.OwnerRule.DEV_MITTAL;
import static io.harness.rule.OwnerRule.EBTASAM;
import static io.harness.rule.OwnerRule.SATYA;
import static io.harness.rule.OwnerRule.SATYAKOTA;
import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.yaml.extended.ci.codebase.Build.builder;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.DeleteWebhookEvent;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.ReleaseWebhookEvent;
import io.harness.beans.execution.Repository;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml.HostedVmInfraSpec;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.Platform;
import io.harness.beans.yaml.extended.platform.ArchType;
import io.harness.beans.yaml.extended.runtime.CloudRuntime;
import io.harness.beans.yaml.extended.runtime.CloudRuntime.CloudRuntimeSpec;
import io.harness.beans.yaml.extended.runtime.Runtime;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.buildstate.ConnectorUtils;
import io.harness.ci.execution.integrationstage.K8InitializeStepUtilsHelper;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.pipeline.executions.beans.CIImageDetails;
import io.harness.ci.pipeline.executions.beans.CIInfraDetails;
import io.harness.ci.pipeline.executions.beans.CIScmDetails;
import io.harness.ci.pipeline.executions.beans.TIBuildDetails;
import io.harness.cimanager.stages.IntegrationStageConfig;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.connector.CiIntegrationStageUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.DockerConnectorDTO;
import io.harness.delegate.beans.connector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.k8s.model.ImageDetails;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;
import io.harness.yaml.core.StepSpecType;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.spec.BranchBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.BuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.CommitShaBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.PRBuildSpec;
import io.harness.yaml.extended.ci.codebase.spec.TagBuildSpec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class IntegrationStageUtilsTest {
  private CIExecutionPlanTestHelper ciExecutionPlanTestHelper = new CIExecutionPlanTestHelper();

  @Test
  @Category(UnitTests.class)
  public void getGitURLTestWithoutGitSuffix() throws Exception {
    String yamlNode = "{\"connectorRef\":\"git_3464\",\"repoName\":\"harness-core\",\"build\":{\"type\":\"branch\","
        + "\"spec\":{\"branch\":\"develop\",\"__uuid\":\"YtRST1sGTMyuLgNvJYsInw\"},\"__uuid\":\"Sh-"
        + "Z7OKrQkeeg35DDI8tHQ\"},\"__uuid\":\"Yl_HajezQ4yOIRqE6xWZYQ\"}";
    CodeBase ciCodebase = YamlUtils.read(yamlNode, CodeBase.class);
    GitConnectionType connectionType = GitConnectionType.ACCOUNT;
    String url = "git@github.com:devkimittal";
    String actual = IntegrationStageUtils.getGitURL(ciCodebase, connectionType, url);
    String expected = "git@github.com:devkimittal/harness-core.git";
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Category(UnitTests.class)
  public void getGitURLTestWithGitSuffix() throws Exception {
    String yamlNode = "{\"connectorRef\":\"git_3464\",\"repoName\":\"harness-core.git\",\"build\":{\"type\":\"branch\","
        + "\"spec\":{\"branch\":\"develop\",\"__uuid\":\"YtRST1sGTMyuLgNvJYsInw\"},\"__uuid\":\"Sh-"
        + "Z7OKrQkeeg35DDI8tHQ\"},\"__uuid\":\"Yl_HajezQ4yOIRqE6xWZYQ\"}";
    CodeBase ciCodebase = YamlUtils.read(yamlNode, CodeBase.class);
    GitConnectionType connectionType = GitConnectionType.ACCOUNT;
    String url = "git@github.com:devkimittal";
    String actual = IntegrationStageUtils.getGitURL(ciCodebase, connectionType, url);
    String expected = "git@github.com:devkimittal/harness-core.git";
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Category(UnitTests.class)
  public void getTiBuildDetailsTest() throws Exception {
    ExecutionElementConfig executionElementConfig = ciExecutionPlanTestHelper.getExecutionElementConfig();
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder().executionElementConfig(executionElementConfig).build();

    List<TIBuildDetails> tiBuildDetailsList = IntegrationStageUtils.getTiBuildDetails(initializeStepInfo);

    TIBuildDetails tiBuildDetails = TIBuildDetails.builder().buildTool("Maven").language("Java").build();
    List<TIBuildDetails> expectedTiBuildDetails = new ArrayList<>();
    expectedTiBuildDetails.add(tiBuildDetails);

    assertThat(tiBuildDetailsList).isEqualTo(expectedTiBuildDetails);
  }

  @Test
  @Category(UnitTests.class)
  public void getCiImageDetailsTest() throws Exception {
    ExecutionElementConfig executionElementConfig = ciExecutionPlanTestHelper.getExecutionElementConfig();
    InitializeStepInfo initializeStepInfo =
        InitializeStepInfo.builder().executionElementConfig(executionElementConfig).build();

    List<CIImageDetails> ciImageDetailsList = IntegrationStageUtils.getCiImageDetails(initializeStepInfo);

    CIImageDetails image1 = CIImageDetails.builder().imageName("drone/git").imageTag("").build();
    CIImageDetails image2 = CIImageDetails.builder().imageName("maven").imageTag("3.6.3-jdk-8").build();
    CIImageDetails image3 = CIImageDetails.builder().imageName("plugins/git").imageTag("").build();
    CIImageDetails image4 = CIImageDetails.builder().imageName("maven").imageTag("3.6.3-jdk-8").build();

    List<CIImageDetails> expectedCiBuildDetails = new ArrayList<>();
    expectedCiBuildDetails.add(image1);
    expectedCiBuildDetails.add(image2);
    expectedCiBuildDetails.add(image3);
    expectedCiBuildDetails.add(image4);

    assertThat(ciImageDetailsList).isEqualTo(expectedCiBuildDetails);
  }

  @Test
  @Owner(developers = SATYAKOTA)
  @Category(UnitTests.class)
  public void getCiInfraDetailsTest() throws Exception {
    Infrastructure infrastructure = ciExecutionPlanTestHelper.getInfrastructureWithVolume();

    CIInfraDetails ciInfraDetails = IntegrationStageUtils.getCiInfraDetails(infrastructure);

    CIInfraDetails expectedCiInfraDetails = CIInfraDetails.builder()
                                                .infraType("KubernetesDirect")
                                                .infraOSType("Linux")
                                                .infraHostType("Self Hosted")
                                                .infraArchType("Amd64")
                                                .resourceClass(null)
                                                .imageName(null)
                                                .customImage(false)
                                                .connectorIdentifier(null)
                                                .nestedVirtualization(false)
                                                .build();

    assertThat(ciInfraDetails).isEqualTo(expectedCiInfraDetails);
  }

  @Test
  @Category(UnitTests.class)
  public void getCiScmDetailsTest() throws Exception {
    Infrastructure infrastructure = ciExecutionPlanTestHelper.getInfrastructureWithVolume();

    ConnectorUtils connectorUtils = new ConnectorUtils(null, null, null, null);
    ConnectorDetails connectorDetails = ciExecutionPlanTestHelper.getGitConnector();

    CIScmDetails ciScmDetails = IntegrationStageUtils.getCiScmDetails(connectorUtils, connectorDetails);

    CIScmDetails expectedCiScmDetails =
        CIScmDetails.builder().scmProvider("Git").scmAuthType("Http").scmHostType("SaaS").build();

    assertThat(ciScmDetails).isEqualTo(expectedCiScmDetails);
  }

  @Test
  @Category(UnitTests.class)
  public void testGetAllSteps() throws Exception {
    List<ExecutionWrapperConfig> wrapperConfigs =
        K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStepGroup1();
    List<CIAbstractStepNode> steps = IntegrationStageUtils.getAllSteps(wrapperConfigs);
    assertThat(steps.size()).isEqualTo(9);
    List<String> ids = new ArrayList<>();
    for (CIAbstractStepNode step : steps) {
      ids.add(step.getIdentifier());
    }
    assertThat(ids.contains("run2")).isTrue();
    assertThat(ids.contains("run1")).isTrue();
    assertThat(ids.contains("run31")).isTrue();
    assertThat(ids.contains("run32")).isTrue();
    assertThat(ids.contains("step-2")).isTrue();
    assertThat(ids.contains("step-3")).isTrue();
    assertThat(ids.contains("step-4")).isTrue();
    assertThat(ids.contains("run21")).isTrue();
    assertThat(ids.contains("run22")).isTrue();
    assertThat(ids.contains("run3")).isFalse();
  }

  @Test
  @Category(UnitTests.class)
  public void testGetStageConnectorRefs() throws Exception {
    List<ExecutionWrapperConfig> wrapperConfigs =
        K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStepGroup1();
    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(wrapperConfigs).build();
    IntegrationStageConfig integrationStageConfig =
        IntegrationStageConfigImpl.builder().execution(executionElementConfig).build();
    List<String> refs = IntegrationStageUtils.getStageConnectorRefs(integrationStageConfig, false);
    assertThat(refs.size()).isEqualTo(8);
    assertThat(refs.contains("account.harnessImage")).isTrue();
    assertThat(refs.contains("run")).isTrue();
  }

  @Test
  @Category(UnitTests.class)
  public void testGetStageConnectorRefsWithInject() throws Exception {
    List<ExecutionWrapperConfig> wrapperConfigs = K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithInject();
    ExecutionElementConfig executionElementConfig = ExecutionElementConfig.builder().steps(wrapperConfigs).build();
    IntegrationStageConfig integrationStageConfig =
        IntegrationStageConfigImpl.builder().execution(executionElementConfig).build();
    List<String> refs = IntegrationStageUtils.getStageConnectorRefs(integrationStageConfig, true);
    assertThat(refs.size()).isEqualTo(10);
    assertThat(refs.contains("account.harnessImage")).isTrue();
    assertThat(refs.contains("run")).isTrue();
  }

  @Test
  @Category(UnitTests.class)
  public void testInjectLoopEnvVariables() throws Exception {
    List<ExecutionWrapperConfig> wrapperConfigs =
        K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStepGroup1();
    for (ExecutionWrapperConfig config : wrapperConfigs) {
      IntegrationStageUtils.injectLoopEnvVariables(config);
    }
    List<CIAbstractStepNode> steps = IntegrationStageUtils.getAllSteps(wrapperConfigs);
    for (CIAbstractStepNode step : steps) {
      StepSpecType spec = step.getStepSpecType();
      StepParameters params = spec.getStepParameters();
      String stepJson = params.toString();
      assertThat(stepJson.contains("\"HARNESS_STAGE_INDEX\": \"<+stage.iteration>\""));
      assertThat(stepJson.contains("\"HARNESS_STAGE_TOTAL\": \"<+stage.iterations>\""));
      assertThat(stepJson.contains("\"HARNESS_STEP_INDEX\": \"<+step.iteration>\""));
      assertThat(stepJson.contains("\"HARNESS_STEP_TOTAL\": \"<+step.iterations>\""));
      assertThat(stepJson.contains("\"HARNESS_NODE_INDEX\": \"<+strategy.iterations>\""));
      assertThat(stepJson.contains("\"HARNESS_NODE_TOTAL\": \"<+strategy.iterations>\""));
    }

    wrapperConfigs = K8InitializeStepUtilsHelper.getExecutionWrapperConfigListWithStepGroup2();
    for (ExecutionWrapperConfig config : wrapperConfigs) {
      IntegrationStageUtils.injectLoopEnvVariables(config);
    }
    steps = IntegrationStageUtils.getAllSteps(wrapperConfigs);
    for (CIAbstractStepNode step : steps) {
      StepSpecType spec = step.getStepSpecType();
      StepParameters params = spec.getStepParameters();
      String stepJson = params.toString();
      assertThat(stepJson.contains("\"HARNESS_STAGE_INDEX\": \"<+stage.iteration>\""));
      assertThat(stepJson.contains("\"HARNESS_STAGE_TOTAL\": \"<+stage.iterations>\""));
      assertThat(stepJson.contains("\"HARNESS_STEP_INDEX\": \"<+step.iteration>\""));
      assertThat(stepJson.contains("\"HARNESS_STEP_TOTAL\": \"<+step.iterations>\""));
      assertThat(stepJson.contains("\"HARNESS_NODE_INDEX\": \"<+strategy.iterations>\""));
      assertThat(stepJson.contains("\"HARNESS_NODE_TOTAL\": \"<+strategy.iterations>\""));
    }
  }

  @Test
  @Category(UnitTests.class)
  public void getBuildTimeMultiplier() {
    HostedVmInfraYaml hostedVmInfraYaml =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraYaml.HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().arch(ParameterField.createValueField(ArchType.Amd64)).build()))
                      .build())
            .build();
    Double buildTimeMultiplier = IntegrationStageUtils.getBuildTimeMultiplierForHostedInfra(hostedVmInfraYaml);
    assertThat(buildTimeMultiplier).isEqualTo(2.0);

    hostedVmInfraYaml.setSpec(HostedVmInfraSpec.builder()
                                  .platform(ParameterField.createValueField(
                                      Platform.builder().os(ParameterField.createValueField(OSType.MacOS)).build()))
                                  .build());
    buildTimeMultiplier = IntegrationStageUtils.getBuildTimeMultiplierForHostedInfra(hostedVmInfraYaml);
    assertThat(buildTimeMultiplier).isEqualTo(60.0);

    hostedVmInfraYaml.setSpec(HostedVmInfraSpec.builder()
                                  .platform(ParameterField.createValueField(
                                      Platform.builder().os(ParameterField.createValueField(OSType.Windows)).build()))
                                  .build());
    buildTimeMultiplier = IntegrationStageUtils.getBuildTimeMultiplierForHostedInfra(hostedVmInfraYaml);
    assertThat(buildTimeMultiplier).isEqualTo(8.0);
  }

  @Test
  @Category(UnitTests.class)
  public void shouldNotFailForAzureOnPremUrl() {
    String accountUrl = "https://tfs.azureonprem.com/Org/Project/";
    String actualUrl =
        CiIntegrationStageUtils.retrieveGenericGitConnectorURL("repo", GitConnectionType.PROJECT, accountUrl);
    assertThat(actualUrl).isEqualTo(accountUrl + "_git/repo");
  }

  @Test
  @Owner(developers = DEV_MITTAL)
  @Category(UnitTests.class)
  public void testIsURLSame() {
    assertThat(
        IntegrationStageUtils.isURLSame(
            WebhookExecutionSource.builder()
                .webhookEvent(
                    BranchWebhookEvent.builder()
                        .repository(Repository.builder().httpURL("https://github.com/devkimittal/harness-core").build())
                        .build())
                .build(),
            "https://github.com/devkimittal/harness-core"))
        .isTrue();
    assertThat(
        IntegrationStageUtils.isURLSame(
            WebhookExecutionSource.builder()
                .webhookEvent(
                    BranchWebhookEvent.builder()
                        .repository(Repository.builder().httpURL("https://github.com/devkimittal/harness-core").build())
                        .build())
                .build(),
            "https://github.com/Devkimittal/Harness-core"))
        .isTrue();
    assertThat(
        IntegrationStageUtils.isURLSame(
            WebhookExecutionSource.builder()
                .webhookEvent(
                    PRWebhookEvent.builder()
                        .repository(Repository.builder().httpURL("https://github.com/devkimittal/harness-core").build())
                        .build())
                .build(),
            "https://github.com/Devkimittal/Harness-core"))
        .isTrue();
    assertThat(
        IntegrationStageUtils.isURLSame(
            WebhookExecutionSource.builder()
                .webhookEvent(
                    DeleteWebhookEvent.builder()
                        .repository(Repository.builder().httpURL("https://github.com/devkimittal/harness-core").build())
                        .build())
                .build(),
            "https://github.com/Devkimittal/Harness-core"))
        .isTrue();
    assertThat(
        IntegrationStageUtils.isURLSame(
            WebhookExecutionSource.builder()
                .webhookEvent(
                    ReleaseWebhookEvent.builder()
                        .repository(Repository.builder().httpURL("https://github.com/devkimittal/harness-core").build())
                        .build())
                .build(),
            "https://github.com/Devkimittal/Harness-core"))
        .isTrue();
  }

  @Test
  @Owner(developers = SATYA)
  @Category(UnitTests.class)
  public void testGetImageInfo() {
    // image without fqn
    String image = "repo:latest";
    ImageDetails imageDetails = IntegrationStageUtils.getImageInfo(image);
    assertThat(imageDetails.getName()).isEqualTo("repo");
    assertThat(imageDetails.getTag()).isEqualTo("latest");

    // private registry fqn
    image = "internal.registry.com/repo:latest";
    imageDetails = IntegrationStageUtils.getImageInfo(image);
    assertThat(imageDetails.getName()).isEqualTo("internal.registry.com/repo");
    assertThat(imageDetails.getTag()).isEqualTo("latest");

    // private registry fqn with port and no tag
    image = "internal.registry.com:5000/repo";
    imageDetails = IntegrationStageUtils.getImageInfo(image);
    assertThat(imageDetails.getName()).isEqualTo("internal.registry.com:5000/repo");
    assertThat(imageDetails.getTag()).isEqualTo("");

    // private registry fqn with port and  tag
    image = "internal.registry.com:5000/repo:latest";
    imageDetails = IntegrationStageUtils.getImageInfo(image);
    assertThat(imageDetails.getName()).isEqualTo("internal.registry.com:5000/repo");
    assertThat(imageDetails.getTag()).isEqualTo("latest");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testShouldCloneManuallyResultsFalseOne() throws IOException { // Case - 1 => <+trigger.prNumber>
    String yamlNode = "{\"connectorRef\":\"git_3464\",\"repoName\":\"harness-core\",\"build\":{\"type\":\"PR\","
        + "\"spec\":{\"number\":\"<+trigger.prNumber>\",\"__uuid\":\"YtRST1sGTMyuLgNvJYsInw\"},\"__"
        + "uuid\":\"Sh-Z7OKrQkeeg35DDI8tHQ\"},\"__uuid\":\"Yl_HajezQ4yOIRqE6xWZYQ\"}";
    CodeBase ciCodebase = YamlUtils.read(yamlNode, CodeBase.class);
    boolean shouldCloneManually = IntegrationStageUtils.shouldCloneManually(ciCodebase);
    assertThat(shouldCloneManually).isEqualTo(false);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testShouldCloneManuallyResultsFalseTwo()
      throws IOException { // Case - 2 => <+trigger.payload.pull_req.number>
    String yamlNode =
        "{\"connectorRef\":\"git_3464\",\"repoName\":\"harness-core\",\"build\":{\"type\":\"PR\",\"spec\":{\"number\":"
        + "\"<+trigger.payload.pull_req.number>\",\"__uuid\":\"YtRST1sGTMyuLgNvJYsInw\"},\"__uuid\":\"Sh-"
        + "Z7OKrQkeeg35DDI8tHQ\"},\"__uuid\":\"Yl_HajezQ4yOIRqE6xWZYQ\"}";
    CodeBase ciCodebase = YamlUtils.read(yamlNode, CodeBase.class);
    boolean shouldCloneManually = IntegrationStageUtils.shouldCloneManually(ciCodebase);
    assertThat(shouldCloneManually).isEqualTo(false);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testShouldCloneManuallyResultsTrue() throws IOException { // Case - 3 => Expressions other than above two
    String yamlNode = "{\"connectorRef\":\"git_3464\",\"repoName\":\"harness-core\",\"build\":{\"type\":\"PR\","
        + "\"spec\":{\"number\":\"<+trigger.payload.prNumber>\",\"__uuid\":\"YtRST1sGTMyuLgNvJYsInw\"},"
        + "\"__uuid\":\"Sh-Z7OKrQkeeg35DDI8tHQ\"},\"__uuid\":\"Yl_HajezQ4yOIRqE6xWZYQ\"}";
    CodeBase ciCodebase = YamlUtils.read(yamlNode, CodeBase.class);
    boolean shouldCloneManually = IntegrationStageUtils.shouldCloneManually(ciCodebase);
    assertThat(shouldCloneManually).isEqualTo(true);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testGetCIInfraWithNestedVirt() {
    ParameterField<Platform> platformParameterField =
        ParameterField.createValueField(Platform.builder()
                                            .os(ParameterField.createValueField(OSType.Linux))
                                            .arch(ParameterField.createValueField(ArchType.Amd64))
                                            .build());
    CloudRuntimeSpec cloudRuntimeSpec =
        CloudRuntimeSpec.builder().nestedVirtualization(ParameterField.createValueField(true)).build();
    ParameterField<Runtime> runtimeParameterField =
        ParameterField.createValueField(CloudRuntime.builder().spec(cloudRuntimeSpec).build());
    HostedVmInfraSpec spec =
        HostedVmInfraSpec.builder().platform(platformParameterField).runtime(runtimeParameterField).build();
    HostedVmInfraYaml infra = HostedVmInfraYaml.builder().spec(spec).build();
    CIInfraDetails ciInfraDetails = IntegrationStageUtils.getCiInfraDetails(infra);
    assertThat(ciInfraDetails.getInfraOSType()).isEqualTo("Linux");
    assertThat(ciInfraDetails.getInfraType()).isEqualTo("HostedVm");
    assertThat(ciInfraDetails.getInfraArchType()).isEqualTo("Amd64");
    assertThat(ciInfraDetails.getInfraHostType()).isEqualTo("Harness Hosted");
    assertThat(ciInfraDetails.isNestedVirtualization()).isEqualTo(true);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testFullyQualifiedImageNameForHAR() {
    ConnectorDetails connectorDetailsFoMock =
        ConnectorDetails.builder()
            .connectorConfig(DockerConnectorDTO.builder().dockerRegistryUrl("https://pkg.harness.io").build())
            .connectorType(DOCKER)
            .executeOnDelegate(false)
            .build();
    String fqn = IntegrationStageUtility.getFullyQualifiedImageName("image", connectorDetailsFoMock,
        IntegrationStageUtils.getBaseNGAccess("account", "org", "pro"), "registry", true, false);
    assertThat(fqn).isEqualTo("pkg.harness.io/account/registry/image");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testFullyQualifiedImageNameForHARFFDisabled() {
    ConnectorDetails connectorDetailsFoMock =
        ConnectorDetails.builder()
            .connectorConfig(DockerConnectorDTO.builder().dockerRegistryUrl("https://dockerhub.io").build())
            .connectorType(DOCKER)
            .executeOnDelegate(false)
            .build();
    String fqn = IntegrationStageUtility.getFullyQualifiedImageName("image", connectorDetailsFoMock,
        IntegrationStageUtils.getBaseNGAccess("account", "org", "pro"), null, false, false);
    assertThat(fqn).isEqualTo("dockerhub.io/image");
  }

  @Test
  @Owner(developers = ABHAY)
  @Category(UnitTests.class)
  public void testExecutionSourceForCommitSha() {
    TriggeredBy triggeredBy =
        TriggeredBy.newBuilder().putExtraInfo("email", ABHAY).setIdentifier(ABHAY).setUuid(generateUuid()).build();
    ExecutionTriggerInfo triggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggeredBy(triggeredBy).setTriggerType(TriggerType.MANUAL).build();
    ParameterField<Build> parameterField = createBuildParameter(BuildType.COMMIT_SHA, "commitSha");
    ExecutionSource executionSource = IntegrationStageUtils.buildExecutionSource(
        triggerInfo, null, "testCOmmitSha", parameterField, null, null, null, null);
    ((ManualExecutionSource) executionSource).getCommitSha().equals("commitSha");
    assertThat(((ManualExecutionSource) executionSource).getCommitSha()).isEqualTo("commitSha");
  }

  public ParameterField<Build> createBuildParameter(BuildType buildType, String value) {
    final ParameterField<String> buildStringParameter = ParameterField.<String>builder().value(value).build();
    BuildSpec buildSpec = null;
    if (BuildType.BRANCH == buildType) {
      buildSpec = BranchBuildSpec.builder().branch(buildStringParameter).build();
    } else if (BuildType.TAG == buildType) {
      buildSpec = TagBuildSpec.builder().tag(buildStringParameter).build();
    } else if (BuildType.PR == buildType) {
      buildSpec = PRBuildSpec.builder().number(buildStringParameter).build();
    } else if (BuildType.COMMIT_SHA == buildType) {
      buildSpec = CommitShaBuildSpec.builder().commitSha(buildStringParameter).build();
    }
    final Build build = builder().spec(buildSpec).type(buildType).build();
    return ParameterField.<Build>builder().value(build).build();
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testApplyWindowsOptimizationToImage_WindowsWithHTTPConnector() {
    String image = "git-clone:1.0";
    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Windows)).build()))
                      .build())
            .build();

    ConnectorDetails gitConnector = ConnectorDetails.builder()
                                        .connectorConfig(GitConfigDTO.builder().gitAuthType(GitAuthType.HTTP).build())
                                        .build();

    String result = IntegrationStageUtils.applyWindowsOptimizationToImage(image, infrastructure, gitConnector, true);

    assertThat(result).isEqualTo("git-clone:1.0-optimized");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testApplyWindowsOptimizationToImage_WindowsWithSSHConnector() {
    String image = "git-clone:1.0";
    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Windows)).build()))
                      .build())
            .build();

    ConnectorDetails gitConnector =
        ConnectorDetails.builder().connectorConfig(GitConfigDTO.builder().gitAuthType(GitAuthType.SSH).build()).build();

    String result = IntegrationStageUtils.applyWindowsOptimizationToImage(image, infrastructure, gitConnector, false);

    assertThat(result).isEqualTo("git-clone:1.0");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testApplyWindowsOptimizationToImage_LinuxWithHTTPConnector() {
    String image = "git-clone:1.0";
    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Linux)).build()))
                      .build())
            .build();

    ConnectorDetails gitConnector = ConnectorDetails.builder()
                                        .connectorConfig(GitConfigDTO.builder().gitAuthType(GitAuthType.HTTP).build())
                                        .build();

    String result = IntegrationStageUtils.applyWindowsOptimizationToImage(image, infrastructure, gitConnector, false);

    assertThat(result).isEqualTo("git-clone:1.0");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testApplyWindowsOptimizationToImage_NullInfrastructure() {
    String image = "git-clone:1.0";
    Infrastructure infrastructure = null;

    ConnectorDetails gitConnector = ConnectorDetails.builder()
                                        .connectorConfig(GitConfigDTO.builder().gitAuthType(GitAuthType.HTTP).build())
                                        .build();

    String result = IntegrationStageUtils.applyWindowsOptimizationToImage(image, infrastructure, gitConnector, false);

    assertThat(result).isEqualTo("git-clone:1.0");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testApplyWindowsOptimizationToImage_NullConnector() {
    String image = "git-clone:1.0";
    Infrastructure infrastructure =
        HostedVmInfraYaml.builder()
            .spec(HostedVmInfraSpec.builder()
                      .platform(ParameterField.createValueField(
                          Platform.builder().os(ParameterField.createValueField(OSType.Windows)).build()))
                      .build())
            .build();
    ConnectorDetails gitConnector = null;

    String result = IntegrationStageUtils.applyWindowsOptimizationToImage(image, infrastructure, gitConnector, false);

    assertThat(result).isEqualTo("git-clone:1.0");
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testApplyWindowsOptimizationToImage_BothNull() {
    String image = "git-clone:1.0";
    Infrastructure infrastructure = null;
    ConnectorDetails gitConnector = null;

    String result = IntegrationStageUtils.applyWindowsOptimizationToImage(image, infrastructure, gitConnector, false);

    assertThat(result).isEqualTo("git-clone:1.0");
  }
}
