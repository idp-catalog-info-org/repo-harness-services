/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.utils;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.utils.ConnectorType;
import io.harness.idp.steps.beans.stepinfo.EntityRefsParameterField;
import io.harness.idp.steps.beans.stepinfo.IdpCookieCutterStepInfo;
import io.harness.idp.steps.beans.stepinfo.IdpCreateCatalogStepInfo;
import io.harness.idp.steps.beans.stepinfo.IdpCreateOrganisationStepInfo;
import io.harness.idp.steps.beans.stepinfo.IdpCreateProjectStepInfo;
import io.harness.idp.steps.beans.stepinfo.IdpCreateRepoStepInfo;
import io.harness.idp.steps.beans.stepinfo.IdpCreateResourceStepInfo;
import io.harness.idp.steps.beans.stepinfo.IdpDirectPushStepInfo;
import io.harness.idp.steps.beans.stepinfo.IdpRegisterCatalogStepInfo;
import io.harness.idp.steps.beans.stepinfo.IdpUpdateCatalogPropertyStepInfo;
import io.harness.idp.steps.beans.stepinfo.PropertiesParameterField;
import io.harness.idp.steps.utils.IdpStepUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.yaml.ParameterField;
import io.harness.reflection.ReflectionUtils;
import io.harness.rule.Owner;
import io.harness.steps.beans.stepinfo.IdpSlackNotifyStepInfo;
import io.harness.utils.CiCodebaseUtils;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class IDPStepUtilsTest extends CategoryTest {
  @InjectMocks IDPStepUtils idpStepUtils;
  @Mock IdpStepUtils stepUtils;

  @Mock CiCodebaseUtils ciCodebaseUtils;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testIdpCookieCutterStepEnvVariables() {
    String testName = "test-name";
    String testIdentifier = "test-identifier";
    String testTemplateType = "public";
    String testPath = "test-path";
    String testCookieCutterVarName = "testVarName";
    String testCookieCutterVarValue = "testVarValue";
    Map<String, JsonNode> cookiecutterVariables = new HashMap<>();
    cookiecutterVariables.put(testCookieCutterVarName, JsonNodeFactory.instance.textNode(testCookieCutterVarValue));

    IdpCookieCutterStepInfo idpCookieCutterStepInfo =
        IdpCookieCutterStepInfo.builder()
            .cookieCutterVariables(ParameterField.createValueField(cookiecutterVariables))
            .templateType(ParameterField.createValueField(testTemplateType))
            .pathForTemplate(ParameterField.createValueField(testPath))
            .name(testName)
            .identifier(testIdentifier)
            .build();

    Map<String, String> expected = new HashMap<>();
    expected.put("IDP_COOKIECUTTER_" + testCookieCutterVarName, testCookieCutterVarValue);
    expected.put("TEMPLATE_TYPE", testTemplateType);
    expected.put("PATH_FOR_TEMPLATE", testPath);
    Map<String, String> actual =
        idpStepUtils.getCookieCutterStepInfoEnvVariables(idpCookieCutterStepInfo, "test-identifier");
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testIdpCreateRepoStepEnvVariables() {
    String testName = "test-name";
    long expressionFunctorToken = 12345;
    String repoType = "private";
    String repoName = "test-repo-name";
    String orgName = "test-org-name";
    String personalAccount = "true";
    String connectorType = ConnectorType.GITHUB.getDisplayName();
    String testSecretIdentifier = "account.test";

    IdpCreateRepoStepInfo idpCreateRepoStepInfo = IdpCreateRepoStepInfo.builder()
                                                      .repoType(ParameterField.createValueField(repoType))
                                                      .repository(ParameterField.createValueField(repoName))
                                                      .name(testName)
                                                      .organization(ParameterField.createValueField(orgName))
                                                      .connectorRef(ParameterField.createValueField("myConnectorRef"))
                                                      .connectorType(ParameterField.createValueField(connectorType))
                                                      .personalAccount(ParameterField.createValueField(personalAccount))
                                                      .xApiKey(ParameterField.createValueField(testSecretIdentifier))
                                                      .build();

    Map<String, String> expected = new HashMap<>();
    expected.put("ORG_NAME", orgName);
    expected.put("REPO_TYPE", repoType);
    expected.put("REPO_NAME", repoName);
    expected.put("CONNECTOR_TYPE", ConnectorType.GITHUB.getDisplayName());
    expected.put("PERSONAL_ACCOUNT", personalAccount);

    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorType(ConnectorType.GITHUB).build();

    when(ciCodebaseUtils.getGitEnvVariables(any(), any())).thenReturn(new HashMap<>());

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .setExpressionFunctorToken(expressionFunctorToken)
                            .build();

    Map<String, String> actual = idpStepUtils.getCreateRepoStepInfoEnvVariables(
        idpCreateRepoStepInfo, connectorDetails, "test-id", ambiance, StageInfraDetails.Type.K8);

    assertThat(actual).isEqualTo(expected);

    expected.put("X_API_KEY",
        NGVariablesUtils.fetchSecretExpressionWithExpressionToken(testSecretIdentifier, expressionFunctorToken, false));
    actual = idpStepUtils.getCreateRepoStepInfoEnvVariables(
        idpCreateRepoStepInfo, connectorDetails, "test-id", ambiance, StageInfraDetails.Type.VM);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testIDPCodePushStepInfoEnvVariables() {
    String testName = "test-name";

    String repoName = "test-repo-name";
    String orgName = "test-org-name";
    String workspace = "test-workspace-name";
    String project = "test-project-name";
    String codeDirectory = "test-code-directory";
    String branch = "test-branch-name";
    String forcePush = "true";
    String connectorType = ConnectorType.GITHUB.getDisplayName();

    IdpDirectPushStepInfo idpCodePushStepInfo = IdpDirectPushStepInfo.builder()
                                                    .repository(ParameterField.createValueField(repoName))
                                                    .branch(ParameterField.createValueField(branch))
                                                    .codeDirectory(ParameterField.createValueField(codeDirectory))
                                                    .name(testName)
                                                    .organization(ParameterField.createValueField(orgName))
                                                    .workspace(ParameterField.createValueField(workspace))
                                                    .connectorRef(ParameterField.createValueField("myConnectorRef"))
                                                    .connectorType(ParameterField.createValueField(connectorType))
                                                    .forcePush(ParameterField.createValueField(forcePush))
                                                    .build();

    Map<String, String> expected = new HashMap<>();
    expected.put("ORG_NAME", orgName);
    expected.put("REPO_NAME", repoName);
    expected.put("WORKSPACE_NAME", workspace);
    expected.put("CODE_DIRECTORY", codeDirectory);
    expected.put("BRANCH", branch);
    expected.put("CONNECTOR_TYPE", ConnectorType.GITHUB.getDisplayName());
    expected.put("FORCE_PUSH", forcePush);

    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorType(ConnectorType.GITHUB).build();

    when(ciCodebaseUtils.getGitEnvVariables(any(), any())).thenReturn(new HashMap<>());

    Map<String, String> actual = idpStepUtils.getDirectPushStepInfoEnvVariables(
        idpCodePushStepInfo, connectorDetails, "test-id", StageInfraDetails.Type.K8);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testRegisterCatalogStepInfoEnvVariables() {
    String testName = "test-name";
    String repoName = "test-repo-name";
    String orgName = "test-org-name";
    String workspace = "test-workspace-name";
    String filePath = "test-file-path";
    String branch = "test-branch-name";
    String connectorType = ConnectorType.GITHUB.getDisplayName();

    IdpRegisterCatalogStepInfo idpRegisterCatalogStepInfo =
        IdpRegisterCatalogStepInfo.builder()
            .repository(ParameterField.createValueField(repoName))
            .branch(ParameterField.createValueField(branch))
            .filePath(ParameterField.createValueField(filePath))
            .name(testName)
            .organization(ParameterField.createValueField(orgName))
            .workspace(ParameterField.createValueField(workspace))
            .connectorRef(ParameterField.createValueField("myConnectorRef"))
            .connectorType(ParameterField.createValueField(connectorType))
            .xApiKey(ParameterField.createValueField("account.test"))
            .build();

    Map<String, String> expected = new HashMap<>();
    expected.put("ORG_NAME", orgName);
    expected.put("REPO_NAME", repoName);
    expected.put("WORKSPACE_NAME", workspace);
    expected.put("FILE_PATH", filePath);
    expected.put("BRANCH", branch);
    expected.put("CONNECTOR_TYPE", ConnectorType.GITHUB.getDisplayName());

    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorType(ConnectorType.GITHUB).build();

    when(ciCodebaseUtils.getGitEnvVariables(any(), any())).thenReturn(new HashMap<>());

    Map<String, String> actual = idpStepUtils.getRegisterCatalogStepInfoEnvVariables(idpRegisterCatalogStepInfo,
        connectorDetails, "test-id", Ambiance.newBuilder().build(), StageInfraDetails.Type.K8);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testRegisterCatalogStepInfoEnvVariablesV2() throws IllegalAccessException {
    String testName = "test-name";
    String repoName = "test-repo-name";
    String orgName = "test-org-name";
    String workspace = "test-workspace-name";
    String filePath = "test-file-path";
    String branch = "test-branch-name";
    String connectorType = ConnectorType.GITHUB.getDisplayName();

    IdpRegisterCatalogStepInfo idpRegisterCatalogStepInfo =
        IdpRegisterCatalogStepInfo.builder()
            .repository(ParameterField.createValueField(repoName))
            .branch(ParameterField.createValueField(branch))
            .filePath(ParameterField.createValueField(filePath))
            .name(testName)
            .organization(ParameterField.createValueField(orgName))
            .workspace(ParameterField.createValueField(workspace))
            .connectorRef(ParameterField.createValueField("myConnectorRef"))
            .connectorType(ParameterField.createValueField(connectorType))
            .xApiKey(ParameterField.createValueField("account.test"))
            .build();

    Field base = ReflectionUtils.getFieldByName(idpStepUtils.getClass(), "base");
    base.setAccessible(true);
    base.set(idpStepUtils, "https://app.harness.io");

    Map<String, String> expected = new HashMap<>();
    expected.put("ORG_NAME", orgName);
    expected.put("REPO_NAME", repoName);
    expected.put("WORKSPACE_NAME", workspace);
    expected.put("FILE_PATH", filePath);
    expected.put("BRANCH", branch);
    expected.put("CONNECTOR_TYPE", ConnectorType.GITHUB.getDisplayName());
    expected.put("IMPORT_ENTITY_URL", "https://app.harness.io/gateway/v1/entities/import");
    expected.put("BASE", "https://app.harness.io");
    expected.put("BODY",
        "{\"connector_ref\":\"myConnectorRef\",\"repo_name\":\"test-repo-name\",\"branch_name\":\"test-branch-name\","
            + "\"file_path\":\"test-file-path\",\"is_harness_code_repo\":null}");
    expected.put("GET_FILE_BY_BRANCH_URL",
        "https://app.harness.io/gateway/ng/api/scm/"
            + "get-file-by-branch?accountIdentifier=account123&connectorRef=myConnectorRef&repoName=test-repo-name&"
            + "branch="
            + "test-branch-name&filePath=test-file-path");

    ConnectorDetails connectorDetails = ConnectorDetails.builder().connectorType(ConnectorType.GITHUB).build();
    when(ciCodebaseUtils.getGitEnvVariables(any(), any())).thenReturn(new HashMap<>());
    Map<String, String> actual =
        idpStepUtils.getRegisterCatalogStepInfoEnvVariablesV2(idpRegisterCatalogStepInfo, connectorDetails, "test-id",
            Ambiance.newBuilder().putSetupAbstractions("accountId", "account123").build(), StageInfraDetails.Type.K8);
    assertThat(actual).isEqualTo(expected);

    expected = new HashMap<>();
    expected.put("ORG_NAME", orgName);
    expected.put("REPO_NAME", repoName);
    expected.put("WORKSPACE_NAME", workspace);
    expected.put("FILE_PATH", filePath);
    expected.put("BRANCH", branch);
    expected.put("CONNECTOR_TYPE", ConnectorType.GITHUB.getDisplayName());
    expected.put("IMPORT_ENTITY_URL", "https://app.harness.io/gateway/v1/entities/import");
    expected.put("BASE", "https://app.harness.io");
    expected.put("BODY",
        "{\"connector_ref\":\"myConnectorRef\",\"repo_name\":\"test-repo-name\",\"branch_name\":\"test-branch-name\","
            + "\"file_path\":\"test-file-path\",\"is_harness_code_repo\":null}");
    expected.put("GET_FILE_BY_BRANCH_URL",
        "https://app.harness.io/gateway/ng/api/scm/"
            + "get-file-by-branch?accountIdentifier=account123&connectorRef=myConnectorRef&repoName=test-repo-name&"
            + "branch="
            + "test-branch-name&filePath=test-file-path&orgIdentifier=default&projectIdentifier=IDP");

    connectorDetails = ConnectorDetails.builder()
                           .connectorType(ConnectorType.GITHUB)
                           .orgIdentifier("default")
                           .projectIdentifier("IDP")
                           .build();
    actual =
        idpStepUtils.getRegisterCatalogStepInfoEnvVariablesV2(idpRegisterCatalogStepInfo, connectorDetails, "test-id",
            Ambiance.newBuilder().putSetupAbstractions("accountId", "account123").build(), StageInfraDetails.Type.K8);
    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testUpdateCatalogPropertyInfoEnvVariables() throws IllegalAccessException {
    List<Integer> versions = List.of(1, 2);
    Map<String, Object> annotations = new HashMap<>();
    annotations.put("pagerduty", "IDP");
    annotations.put("__uuid", "y3Q2-AC4QOmM01Vnu5TU8P");
    Map<String, Object> teamDetails1 = new HashMap<>();
    teamDetails1.put("manager", "employee1");
    teamDetails1.put("lead", "employee2");
    teamDetails1.put("__uuid", "y3Q2-AC4QEmM71Vnu5TU8Q");
    Map<String, Object> teamDetails2 = new HashMap<>();
    teamDetails2.put("manager", "employee10");
    teamDetails2.put("lead", "employee20");
    teamDetails2.put("__uuid", "y3Q2-AC4QEmM71Vnu5TP9P");
    annotations.put("teamDetails", List.of(teamDetails1, teamDetails2));

    List<PropertiesParameterField> propertiesParameterFields = new ArrayList<>();
    PropertiesParameterField propertiesParameterField1 = PropertiesParameterField.builder()
                                                             .property(ParameterField.createValueField("versions"))
                                                             .value(ParameterField.createValueField(versions))
                                                             .mode(ParameterField.createValueField("replace"))
                                                             .build();
    PropertiesParameterField propertiesParameterField2 = PropertiesParameterField.builder()
                                                             .property(ParameterField.createValueField("annotations"))
                                                             .value(ParameterField.createValueField(annotations))
                                                             .mode(ParameterField.createValueField("replace"))
                                                             .build();

    Field base = ReflectionUtils.getFieldByName(idpStepUtils.getClass(), "base");
    base.setAccessible(true);
    base.set(idpStepUtils, "https://app.harness.io");

    propertiesParameterFields.add(propertiesParameterField1);
    propertiesParameterFields.add(propertiesParameterField2);
    IdpUpdateCatalogPropertyStepInfo idpUpdateCatalogPropertyStepInfo =
        IdpUpdateCatalogPropertyStepInfo.builder()
            .name("Update Catalog Property")
            .identifier("UpdateCatalogProperty")
            .type(ParameterField.createValueField("Entity"))
            .entityRef(ParameterField.createValueField("idp-service"))
            .properties(ParameterField.createValueField(propertiesParameterFields))
            .xApiKey(ParameterField.createValueField("account.test"))
            .build();

    Map<String, String> expected = new HashMap<>();
    expected.put("CUSTOM_PROPERTY_URL", "https://app.harness.io/gateway/v1/catalog/custom-properties/entity");
    expected.put("BODY",
        "{\"entity_ref\":\"idp-service\",\"property\":null,\"value\":null,\"mode\":null,"
            + "\"properties\":[{\"property\":\"versions\",\"value\":[1,2],\"mode\":\"replace\"},"
            + "{\"property\":\"annotations\",\"value\":{\"pagerduty\":\"IDP\","
            + "\"teamDetails\":[{\"manager\":\"employee1\",\"lead\":\"employee2\"},{\"manager\":\"employee10\","
            + "\"lead\":\"employee20\"}]},\"mode\":\"replace\"}]}");

    Map<String, String> actual = idpStepUtils.getUpdateCatalogPropertyStepInfoEnvVariables(
        idpUpdateCatalogPropertyStepInfo, "test-id", Ambiance.newBuilder().build(), StageInfraDetails.Type.K8);

    assertThat(actual).isEqualTo(expected);

    List<EntityRefsParameterField> entityRefsParameterFields = new ArrayList<>();
    EntityRefsParameterField entityRefsParameterField1 = EntityRefsParameterField.builder()
                                                             .entityRef(ParameterField.createValueField("idp-service"))
                                                             .value(ParameterField.createValueField(1.5))
                                                             .build();
    EntityRefsParameterField entityRefsParameterField2 =
        EntityRefsParameterField.builder().entityRef(ParameterField.createValueField("pipeline-service")).build();
    entityRefsParameterFields.add(entityRefsParameterField1);
    entityRefsParameterFields.add(entityRefsParameterField2);

    idpUpdateCatalogPropertyStepInfo.setType(ParameterField.createValueField("Property"));
    idpUpdateCatalogPropertyStepInfo.setProperty(ParameterField.createValueField("metadata.versions"));
    idpUpdateCatalogPropertyStepInfo.setEntityRefs(ParameterField.createValueField(entityRefsParameterFields));
    idpUpdateCatalogPropertyStepInfo.setValue(ParameterField.createValueField(1.2));
    idpUpdateCatalogPropertyStepInfo.setMode(ParameterField.createValueField("append"));

    actual = idpStepUtils.getUpdateCatalogPropertyStepInfoEnvVariables(
        idpUpdateCatalogPropertyStepInfo, "test-id", Ambiance.newBuilder().build(), StageInfraDetails.Type.K8);

    expected.put("CUSTOM_PROPERTY_URL", "https://app.harness.io/gateway/v1/catalog/custom-properties/property");
    expected.put("BODY",
        "{\"entity_ref\":null,\"property\":\"metadata.versions\",\"value\":1.2,\"mode\":\"append\","
            + "\"entity_refs\":[{\"entity_ref\":\"idp-service\",\"value\":1.5},{\"entity_ref\":\"pipeline-service\","
            + "\"value\":null}]}");

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testCreateCatalogStepInfoEnvVariables() {
    String testFileName = "file-name";
    String testFilePath = "file-path";
    String testFileContent = "file-content";

    IdpCreateCatalogStepInfo idpCreateCatalogStepInfo =
        IdpCreateCatalogStepInfo.builder()
            .fileContent(ParameterField.createValueField(testFileContent))
            .fileName(ParameterField.createValueField(testFileName))
            .filePath(ParameterField.createValueField(testFilePath))
            .build();

    Map<String, String> expected = new HashMap<>();
    expected.put("FILE_NAME", testFileName);
    expected.put("FILE_PATH", testFilePath);
    expected.put("FILE_CONTENT", testFileContent);
    expected.put("BASE", null);

    Map<String, String> actual = idpStepUtils.getCreateCatalogStepInfoEnvVariables(idpCreateCatalogStepInfo, "test-id");

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void testSlackNotifyWithChannelAndBlocksStepInfoEnvVariables() {
    long expressionFunctorToken = 12345;
    String testChannel = "channel";
    String testBlocks = "{}";
    String testSecretIdentifier = "account.test";
    String ts = "123456789.987654";

    IdpSlackNotifyStepInfo idpSlackNotifyStepInfo = IdpSlackNotifyStepInfo.builder()
                                                        .channel(ParameterField.createValueField(testChannel))
                                                        .blocks(ParameterField.createValueField(testBlocks))
                                                        .token(ParameterField.createValueField(testSecretIdentifier))
                                                        .replyBroadcast(ParameterField.createValueField(true))
                                                        .threadTs(ParameterField.createValueField(ts))
                                                        .build();

    Map<String, String> expected = new HashMap<>();
    expected.put("CHANNEL", testChannel);
    expected.put("BLOCKS", testBlocks);
    expected.put("REPLY_BROADCAST", "true");
    expected.put("THREAD_TS", ts);

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .setExpressionFunctorToken(expressionFunctorToken)
                            .build();

    Map<String, String> actual = idpStepUtils.getSlackNotifyStepInfoEnvVariables(
        idpSlackNotifyStepInfo, "test-id", ambiance, StageInfraDetails.Type.K8);

    assertThat(actual).isEqualTo(expected);

    // test for VM infra

    expected.put("SLACK_TOKEN",
        NGVariablesUtils.fetchSecretExpressionWithExpressionToken(testSecretIdentifier, expressionFunctorToken, false));
    actual = idpStepUtils.getSlackNotifyStepInfoEnvVariables(
        idpSlackNotifyStepInfo, "test-id", ambiance, StageInfraDetails.Type.VM);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSlackNotifyStepInfoEnvVariables() {
    long expressionFunctorToken = 12345;
    String testEmail = "email";
    String testMessageContent = "message-content";
    String testSecretIdentifier = "account.test";

    IdpSlackNotifyStepInfo idpSlackNotifyStepInfo =
        IdpSlackNotifyStepInfo.builder()
            .email(ParameterField.createValueField(testEmail))
            .messageContent(ParameterField.createValueField(testMessageContent))
            .token(ParameterField.createValueField(testSecretIdentifier))
            .build();

    Map<String, String> expected = new HashMap<>();
    expected.put("EMAIL", testEmail);
    expected.put("MESSAGE_CONTENT", testMessageContent);
    expected.put("REPLY_BROADCAST", "false");

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .setExpressionFunctorToken(expressionFunctorToken)
                            .build();

    Map<String, String> actual = idpStepUtils.getSlackNotifyStepInfoEnvVariables(
        idpSlackNotifyStepInfo, "test-id", ambiance, StageInfraDetails.Type.K8);

    assertThat(actual).isEqualTo(expected);

    // test for VM infra

    expected.put("SLACK_TOKEN",
        NGVariablesUtils.fetchSecretExpressionWithExpressionToken(testSecretIdentifier, expressionFunctorToken, false));
    actual = idpStepUtils.getSlackNotifyStepInfoEnvVariables(
        idpSlackNotifyStepInfo, "test-id", ambiance, StageInfraDetails.Type.VM);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSlackNotifyStepInfoSecretEnvVariables() {
    String testSlackToken = "test-token";

    IdpSlackNotifyStepInfo idpSlackNotifyStepInfo =
        IdpSlackNotifyStepInfo.builder().token(ParameterField.createValueField(testSlackToken)).build();

    Map<String, SecretNGVariable> actual =
        idpStepUtils.getSlackNotifyStepInfoSecretVariables(idpSlackNotifyStepInfo, "test-id");

    assertThat(actual.containsKey("SLACK_TOKEN"));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSlackNotifyStepInfoSecretEnvVariableInExpression() {
    String testSlackToken = "${ngSecretManager.obtain(\"account.testsecretslack\", -1506576056)}";

    IdpSlackNotifyStepInfo idpSlackNotifyStepInfo =
        IdpSlackNotifyStepInfo.builder().token(ParameterField.createValueField(testSlackToken)).build();

    Map<String, SecretNGVariable> actual =
        idpStepUtils.getSlackNotifyStepInfoSecretVariables(idpSlackNotifyStepInfo, "test-id");

    assertThat(actual.containsKey("SLACK_TOKEN"));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateOrganisationStepInfoEnvVariables() throws IllegalAccessException {
    long expressionFunctorToken = 12345;
    String orgIdentifier = "default_Org";
    String orgName = "default  Org";
    String description = "Default Org";
    String testSecretIdentifier = "account.X_API_KEY";
    String accountIdentifier = "accountId";
    List<String> tags = List.of("tag1", "tag2");

    IdpCreateOrganisationStepInfo idpCreateOrganisationStepInfo =
        IdpCreateOrganisationStepInfo.builder()
            .orgName(ParameterField.createValueField(orgName))
            .description(ParameterField.createValueField(description))
            .tags(ParameterField.createValueField(tags))
            .xApiKey(ParameterField.createValueField(testSecretIdentifier))
            .build();

    Field base = ReflectionUtils.getFieldByName(idpStepUtils.getClass(), "base");
    base.setAccessible(true);
    base.set(idpStepUtils, "https://app.harness.io");

    Map<String, String> expected = new HashMap<>();
    expected.put("TF_VAR_ORG_IDENTIFIER", orgIdentifier);
    expected.put("TF_VAR_ORG_NAME", orgName);
    expected.put("TF_VAR_DESCRIPTION", description);
    expected.put("TF_VAR_ACCOUNT_IDENTIFIER", accountIdentifier);
    expected.put("TF_VAR_ENDPOINT", "https://app.harness.io/gateway");
    expected.put("TF_VAR_TAGS", "[\"tag1\",\"tag2\"]");

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountIdentifier)
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .setExpressionFunctorToken(expressionFunctorToken)
                            .build();

    Map<String, String> actual = idpStepUtils.getCreateOrganisationStepInfoEnvVariables(
        idpCreateOrganisationStepInfo, "test-id", ambiance, StageInfraDetails.Type.K8);

    assertThat(actual).isEqualTo(expected);

    // test for VM infra

    expected.put("TF_VAR_X_API_KEY",
        NGVariablesUtils.fetchSecretExpressionWithExpressionToken(testSecretIdentifier, expressionFunctorToken, false));
    actual = idpStepUtils.getCreateOrganisationStepInfoEnvVariables(
        idpCreateOrganisationStepInfo, "test-id", ambiance, StageInfraDetails.Type.VM);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateOrganisationStepInfoSecretEnvVariables() {
    String testXApiKey = "test-token";

    IdpCreateOrganisationStepInfo idpCreateOrganisationStepInfo =
        IdpCreateOrganisationStepInfo.builder().xApiKey(ParameterField.createValueField(testXApiKey)).build();

    Map<String, SecretNGVariable> actual =
        idpStepUtils.getCreateOrganisationStepInfoSecretVariables(idpCreateOrganisationStepInfo, "test-id");

    assertThat(actual.containsKey("TF_VAR_X_API_KEY"));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProjectStepInfoEnvVariables() throws IllegalAccessException {
    long expressionFunctorToken = 12345;
    String projectIdentifier = "Test_12_Project_Demo";
    String projectName = "-1Test-_12 Project Demo";
    String orgIdentifier = "default";
    String description = "Default Org";
    String testSecretIdentifier = "account.X_API_KEY";
    String accountIdentifier = "accountId";

    IdpCreateProjectStepInfo idpCreateProjectStepInfo =
        IdpCreateProjectStepInfo.builder()
            .projectName(ParameterField.createValueField(projectName))
            .orgIdentifier(ParameterField.createValueField(orgIdentifier))
            .description(ParameterField.createValueField(description))
            .xApiKey(ParameterField.createValueField(testSecretIdentifier))
            .build();

    Field base = ReflectionUtils.getFieldByName(idpStepUtils.getClass(), "base");
    base.setAccessible(true);
    base.set(idpStepUtils, "https://app.harness.io");

    Map<String, String> expected = new HashMap<>();
    expected.put("TF_VAR_PROJECT_IDENTIFIER", projectIdentifier);
    expected.put("TF_VAR_PROJECT_NAME", projectName);
    expected.put("TF_VAR_ORG_IDENTIFIER", orgIdentifier);
    expected.put("TF_VAR_DESCRIPTION", description);
    expected.put("TF_VAR_ACCOUNT_IDENTIFIER", accountIdentifier);
    expected.put("TF_VAR_ENDPOINT", "https://app.harness.io/gateway");
    expected.put("TF_VAR_TAGS", "[]");

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountIdentifier)
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .setExpressionFunctorToken(expressionFunctorToken)
                            .build();

    Map<String, String> actual = idpStepUtils.getCreateProjectStepInfoEnvVariables(
        idpCreateProjectStepInfo, "test-id", ambiance, StageInfraDetails.Type.K8);

    assertThat(actual).isEqualTo(expected);

    // test for VM infra

    expected.put("TF_VAR_X_API_KEY",
        NGVariablesUtils.fetchSecretExpressionWithExpressionToken(testSecretIdentifier, expressionFunctorToken, false));
    actual = idpStepUtils.getCreateProjectStepInfoEnvVariables(
        idpCreateProjectStepInfo, "test-id", ambiance, StageInfraDetails.Type.VM);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateProjectStepInfoSecretEnvVariables() {
    String testXApiKey = "test-token";

    IdpCreateProjectStepInfo idpCreateProjectStepInfo =
        IdpCreateProjectStepInfo.builder().xApiKey(ParameterField.createValueField(testXApiKey)).build();

    Map<String, SecretNGVariable> actual =
        idpStepUtils.getCreateProjectStepInfoSecretVariables(idpCreateProjectStepInfo, "test-id");

    assertThat(actual.containsKey("TF_VAR_X_API_KEY"));
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateResourceStepInfoEnvVariables() throws IllegalAccessException {
    long expressionFunctorToken = 12345;
    String resourceDefinition = "resource \"harness_platform_organization\" \"this\" {\n"
        + "  identifier  = \"MyOrg\"\n"
        + "  name        = \"My Organization Sample\"\n"
        + "  description = \"An example organization\"\n"
        + "  tags        = [\"foo\"]\n"
        + "}";
    String testSecretIdentifier = "account.X_API_KEY";
    String accountIdentifier = "accountId";

    IdpCreateResourceStepInfo idpCreateResourceStepInfo =
        IdpCreateResourceStepInfo.builder()
            .resourceDefinition(ParameterField.createValueField(resourceDefinition))
            .xApiKey(ParameterField.createValueField(testSecretIdentifier))
            .build();

    Field base = ReflectionUtils.getFieldByName(idpStepUtils.getClass(), "base");
    base.setAccessible(true);
    base.set(idpStepUtils, "https://app.harness.io");

    Map<String, String> expected = new HashMap<>();
    String expectedResourceDefinition = "resource \"harness_platform_organization\" \"this\" {\n"
        + "  identifier  = \"MyOrg\"\n"
        + "  name        = \"My Organization Sample\"\n"
        + "  description = \"An example organization\"\n"
        + "  tags        = [\"foo\"]\n"
        + "}\n"
        + "\n"
        + "terraform {\n"
        + "  required_providers {\n"
        + "    harness = {\n"
        + "      source = \"harness/harness\"\n"
        + "    }\n"
        + "  }\n"
        + "}\n"
        + "\n"
        + "provider \"harness\" {\n"
        + "  endpoint = var.ENDPOINT\n"
        + "  account_id = var.ACCOUNT_IDENTIFIER\n"
        + "  platform_api_key = var.X_API_KEY\n"
        + "}";
    expected.put("RESOURCE_DEFINITION", expectedResourceDefinition);
    expected.put("TF_VAR_ACCOUNT_IDENTIFIER", accountIdentifier);
    expected.put("TF_VAR_ENDPOINT", "https://app.harness.io/gateway");

    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", accountIdentifier)
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .setExpressionFunctorToken(expressionFunctorToken)
                            .build();

    Map<String, String> actual = idpStepUtils.getCreateResourceStepInfoEnvVariables(
        idpCreateResourceStepInfo, "test-id", ambiance, StageInfraDetails.Type.K8);

    assertThat(actual).isEqualTo(expected);

    // test for VM infra

    expected.put("TF_VAR_X_API_KEY",
        NGVariablesUtils.fetchSecretExpressionWithExpressionToken(testSecretIdentifier, expressionFunctorToken, false));
    actual = idpStepUtils.getCreateResourceStepInfoEnvVariables(
        idpCreateResourceStepInfo, "test-id", ambiance, StageInfraDetails.Type.VM);

    assertThat(actual).isEqualTo(expected);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testCreateResourceStepInfoSecretEnvVariables() {
    String testXApiKey = "test-token";

    IdpCreateResourceStepInfo idpCreateResourceStepInfo =
        IdpCreateResourceStepInfo.builder().xApiKey(ParameterField.createValueField(testXApiKey)).build();

    Map<String, SecretNGVariable> actual =
        idpStepUtils.getCreateResourceStepInfoSecretVariables(idpCreateResourceStepInfo, "test-id");

    assertThat(actual.containsKey("TF_VAR_X_API_KEY"));
  }
}
