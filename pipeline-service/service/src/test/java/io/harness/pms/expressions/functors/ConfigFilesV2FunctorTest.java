/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.NGCommonEntityConstants.FUNCTOR_BASE64_METHOD_NAME;
import static io.harness.NGCommonEntityConstants.FUNCTOR_STRING_METHOD_NAME;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;
import static io.harness.rule.OwnerRule.IVAN;
import static io.harness.rule.OwnerRule.SAHIL;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.data.OptionalOutcome;
import io.harness.engine.pms.data.outcome.PmsOutcomeService;
import io.harness.exception.InvalidArgumentsException;
import io.harness.expression.EngineExpressionEvaluator;
import io.harness.expression.common.ExpressionMode;
import io.harness.filestore.remote.FileStoreClient;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.rule.Owner;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class ConfigFilesV2FunctorTest extends CategoryTest {
  private static final String CONFIG_FILE_OUTCOME_WITH_FILE_AND_SECRET_FILE =
      "{\"__recast\":\"io.harness.cdng.configfile.steps.ConfigFilesOutcome\",\"configFileIdentifier\":{\"__recast\":\"io.harness.cdng.configfile.ConfigFileOutcome\",\"identifier\":\"configFileIdentifier\",\"store\":{\"__recast\":\"io.harness.cdng.manifest.yaml.harness.HarnessStore\",\"files\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\",\"value\":[\"account:/folder1/folder2/configFile\"]},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"secretFiles\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\",\"value\":[\"account.AzureFileSecret\"]},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}}},\"order\":0}}";
  private static final String CONFIG_FILE_OUTCOME_WITH_MORE_FILES =
      "{\"__recast\":\"io.harness.cdng.configfile.steps.ConfigFilesOutcome\",\"configFileIdentifier\":{\"__recast\":\"io.harness.cdng.configfile.ConfigFileOutcome\",\"identifier\":\"configFileIdentifier\",\"store\":{\"__recast\":\"io.harness.cdng.manifest.yaml.harness.HarnessStore\",\"files\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\",\"value\":[\"account:/folder1/folder2/configFile\",\"account:/folder1/folder2/configFile\"]},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"secretFiles\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}}},\"order\":0}}";

  private static final String CONFIG_FILE_OUTCOME_WITH_MORE_FILES_2 =
      "{\"__recast\":\"io.harness.cdng.configfile.steps.ConfigFilesOutcome\",\"configFileIdentifier\":{\"__recast\":\"io.harness.cdng.configfile.ConfigFileOutcome\",\"identifier\":\"configFileIdentifier\",\"store\":{\"__recast\":\"io.harness.cdng.manifest.yaml.harness.HarnessStore\",\"files\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\",\"value\":[\"account:/folder1/folder2/configFile\",\"account:/folder3/folder4/configFile\"]},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"secretFiles\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}}},\"order\":0}}";
  private static final String CONFIG_FILE_NOT_VALID_FILE_TYPE =
      "{\"__recast\":\"io.harness.cdng.configfile.steps.ConfigFilesOutcome\",\"configFileIdentifier\":{\"__recast\":\"io.harness.cdng.configfile.ConfigFileOutcome\",\"identifier\":\"configFileIdentifier\",\"store\":{\"__recast\":\"io.harness.cdng.manifest.yaml.harness.HarnessStore\",\"files\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\",\"value\":[\"account:/folder1/folder2/configFile\"]},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"secretFiles\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}}},\"order\":0}}";
  private static final String CONFIG_FILE_WITH_SECRET =
      "{\"__recast\":\"io.harness.cdng.configfile.steps.ConfigFilesOutcome\",\"configFileIdentifier\":{\"__recast\":\"io.harness.cdng.configfile.ConfigFileOutcome\",\"identifier\":\"configFileIdentifier\",\"store\":{\"__recast\":\"io.harness.cdng.manifest.yaml.harness.HarnessStore\",\"files\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\",\"value\":[\"account:/folder1/folder2/configFile\",\"org:/folder1/folder2/configFile\",\"/folder1/folder2/configFile\"]},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"secretFiles\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}}},\"order\":0}}";
  private static final String GIT_CONFIG_OUTCOME_WITH_FILES =
      "{\"__recast\":\"io.harness.cdng.configfile.steps.ConfigFilesOutcome\",\"configFileIdentifier\":{\"__recast\":\"io.harness.cdng.configfile.ConfigFileOutcome\",\"identifier\":\"configFileIdentifier\",\"store\":{\"__recast\":\"io.harness.cdng.manifest.yaml.GithubStore\",\"connectorRef\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.lang.String\",\"typeString\":true,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"branch\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.lang.String\",\"typeString\":true,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"commitId\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.lang.String\",\"typeString\":true,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"paths\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\",\"value\":[\"ssh-winrm/configSshFile.yml\"]},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"folderPath\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.lang.String\",\"typeString\":true,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"repoName\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.lang.String\",\"typeString\":true,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}}},\"gitFiles\":[{\"__recast\":\"io.harness.cdng.configfile.ConfigGitFile\",\"filePath\":\"ssh-winrm/configSshFile.yml\",\"fileContent\":\"git file content\"}],\"order\":0}}";
  private static final String GIT_CONFIG_OUTCOME_WITH_MORE_FILES =
      "{\"__recast\":\"io.harness.cdng.configfile.steps.ConfigFilesOutcome\",\"configFileIdentifier\":{\"__recast\":\"io.harness.cdng.configfile.ConfigFileOutcome\",\"identifier\":\"configFileIdentifier\",\"store\":{\"__recast\":\"io.harness.cdng.manifest.yaml.GithubStore\",\"connectorRef\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.lang.String\",\"typeString\":true,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"branch\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.lang.String\",\"typeString\":true,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"commitId\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.lang.String\",\"typeString\":true,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"paths\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\",\"value\":[\"ssh-winrm/configSshFile.yml\"]},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"folderPath\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.lang.String\",\"typeString\":true,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"repoName\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.lang.String\",\"typeString\":true,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}}},\"gitFiles\":[{\"__recast\":\"io.harness.cdng.configfile.ConfigGitFile\",\"filePath\":\"ssh-winrm/configSshFile.yml\",\"fileContent\":\"git file content\"}, {\"__recast\":\"io.harness.cdng.configfile.ConfigGitFile\",\"filePath\":\"ssh-winrm2/configSshFile.yml\",\"fileContent\":\"git file content 2\"}],\"order\":0}}";
  private static final String CONFIG_FILE_OUTCOME_WITH_SECRET_FILES =
      "{\"__recast\":\"io.harness.cdng.configfile.steps.ConfigFilesOutcome\",\"configFileIdentifier\":{\"__recast\":\"io.harness.cdng.configfile.ConfigFileOutcome\",\"identifier\":\"configFileIdentifier\",\"store\":{\"__recast\":\"io.harness.cdng.manifest.yaml.harness.HarnessStore\",\"files\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"secretFiles\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\",\"value\":[\"account.AzureFileSecret\"]},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}}},\"order\":0}}";
  private static final String CONFIG_FILE_WITH_MORE_SECRET_FILES =
      "{\"__recast\":\"io.harness.cdng.configfile.steps.ConfigFilesOutcome\",\"configFileIdentifier\":{\"__recast\":\"io.harness.cdng.configfile.ConfigFileOutcome\",\"identifier\":\"configFileIdentifier\",\"store\":{\"__recast\":\"io.harness.cdng.manifest.yaml.harness.HarnessStore\",\"files\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\"},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}},\"secretFiles\":{\"__recast\":\"parameterField\",\"__encodedValue\":{\"__recast\":\"io.harness.pms.yaml.ParameterDocumentField\",\"expression\":false,\"valueDoc\":{\"__recast\":\"io.harness.pms.yaml.ParameterFieldValueWrapper\",\"value\":[\"account.AzureFileSecret\",\"org.AzureFileSecret\",\"AzureFileSecret\"]},\"valueClass\":\"java.util.List\",\"typeString\":false,\"skipAutoEvaluation\":false,\"jsonResponseField\":false}}},\"order\":0}}";

  private static final Long EXPRESSION_FUNCTOR_TOKEN = 1L;
  private static final String ACCOUNT_IDENTIFIER = "accountIdentifier";
  private static final String ORG_IDENTIFIER = "orgIdentifier";
  private static final String PROJECT_IDENTIFIER = "projectIdentifier";
  private static final String FILE_CONTENT = "file content";

  private static final String FILE_CONTENT_2 = "file content 2";

  private static final String CONFIG_FILE_IDENTIFIER = "configFileIdentifier";
  private static final String BASE64_FILE_CONTENT =
      "<+fileStore.getAsBase64Internal(<+fileStore.getAsString('account:/folder1/folder2/configFile')>)>";
  private static final String ACCOUNT_SCOPED_FILE_PATH = "account:/folder1/folder2/configFile";
  public static final String ORG_SCOPED_FILE_PATH = "org:/folder1/folder2/configFile";
  public static final String PROJECT_SCOPED_FILE_PATH = "/folder1/folder2/configFile";
  public static final String GIT_FILE_PATH = "ssh-winrm/configSshFile.yml";
  private static final String ACCOUNT_SECRET_REF_PATH = "account.AzureFileSecret";
  public static final String ORG_SECRET_REF_PATH = "org.AzureFileSecret";
  public static final String PROJECT_SECRET_REF_PATH = "AzureFileSecret";
  private static final String GIT_FILE_CONTENT = "git file content";
  private static final String GIT_FILE_CONTENT_2 = "git file content 2";

  private static final String FILE_CONTENT_ON_ACCOUNT = "file-content-on-account";
  private static final String FILE_CONTENT_ON_ORG = "file-content-on-org";
  private static final String FILE_CONTENT_ON_PROJECT = "file-content-on-project";

  @Mock FileStoreClient fileStoreClient;
  @Mock EngineExpressionEvaluator engineExpressionService;
  @Mock private PmsOutcomeService outcomeService;

  private ConfigFileFunctorV2 configFileFunctor;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    configFileFunctor = Mockito.spy(
        new ConfigFileFunctorV2(outcomeService, fileStoreClient, getAmbiance(), engineExpressionService, 10));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetWithFileAndSecretFile() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(
            OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_OUTCOME_WITH_FILE_AND_SECRET_FILE).build());

    assertThatThrownBy(() -> configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, null))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "Found file and encrypted file both attached to config file, configFileIdentifier: configFileIdentifier");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetWithWithoutFileAndSecretFile() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(
            OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_OUTCOME_WITH_FILE_AND_SECRET_FILE).build());

    assertThatThrownBy(() -> configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, null))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "Found file and encrypted file both attached to config file, configFileIdentifier: configFileIdentifier");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetWithMoreFiles() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_OUTCOME_WITH_MORE_FILES).build());

    assertThatThrownBy(() -> configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, null))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Found more files attached to config file, configFileIdentifier: configFileIdentifier");
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetWithMoreFilesWithFileReference() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_OUTCOME_WITH_MORE_FILES).build());

    assertThatThrownBy(() -> configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, 3))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "file path reference is out of bounds for configFileIdentifier: configFileIdentifier. Expected between 0 and 1, but found 3");
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetWithLessFilesWithFileReference() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_OUTCOME_WITH_MORE_FILES).build());

    assertThatThrownBy(() -> configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, -1))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "file path reference is out of bounds for configFileIdentifier: configFileIdentifier. Expected between 0 and 1, but found -1");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetWithMoreSecretFiles() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_WITH_MORE_SECRET_FILES).build());

    assertThatThrownBy(() -> configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, null))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Found more encrypted files attached to config file, configFileIdentifier: configFileIdentifier");
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetWithMoreSecretFilesWithFileReference() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_WITH_MORE_SECRET_FILES).build());

    assertThatThrownBy(() -> configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, 4))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "encrypted file path reference is out of bounds for configFileIdentifier: configFileIdentifier. Expected between 0 and 2, but found 4");
  }
  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetWithLessSecretFilesWithFileReference() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_WITH_MORE_SECRET_FILES).build());

    assertThatThrownBy(() -> configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, -2))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage(
            "encrypted file path reference is out of bounds for configFileIdentifier: configFileIdentifier. Expected between 0 and 2, but found -2");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testFileGetAsString() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().outcome(CONFIG_FILE_NOT_VALID_FILE_TYPE).found(true).build());

    doReturn(FILE_CONTENT).when(configFileFunctor).getContent(anyString(), anyString(), anyString(), anyString());
    when(engineExpressionService.renderExpression(FILE_CONTENT, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, 10))
        .thenReturn(FILE_CONTENT);

    String fileContent = (String) configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, null);

    assertThat(fileContent).isEqualTo(FILE_CONTENT);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testFileGetAsStringWithFileReference() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().outcome(CONFIG_FILE_OUTCOME_WITH_MORE_FILES_2).found(true).build());

    doReturn(FILE_CONTENT)
        .when(configFileFunctor)
        .getContent(anyString(), anyString(), anyString(), eq("account:/folder1/folder2/configFile"));
    doReturn(FILE_CONTENT_2)
        .when(configFileFunctor)
        .getContent(anyString(), anyString(), anyString(), eq("account:/folder3/folder4/configFile"));

    when(engineExpressionService.renderExpression(FILE_CONTENT, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, 10))
        .thenReturn(FILE_CONTENT);
    when(engineExpressionService.renderExpression(FILE_CONTENT_2, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, 10))
        .thenReturn(FILE_CONTENT_2);

    String fileContent = (String) configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, 0);

    assertThat(fileContent).isEqualTo(FILE_CONTENT);

    fileContent = (String) configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, 1);

    assertThat(fileContent).isEqualTo(FILE_CONTENT_2);
  }

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void testGitFileGetAsString() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(GIT_CONFIG_OUTCOME_WITH_FILES).build());
    when(engineExpressionService.renderExpression(GIT_FILE_CONTENT, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, 10))
        .thenReturn(GIT_FILE_CONTENT);

    String fileContent = (String) configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, null);

    assertThat(fileContent).isEqualTo(GIT_FILE_CONTENT);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGitFileGetAsStringWithFileReference() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(GIT_CONFIG_OUTCOME_WITH_MORE_FILES).build());
    when(engineExpressionService.renderExpression(GIT_FILE_CONTENT, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, 10))
        .thenReturn(GIT_FILE_CONTENT);
    when(engineExpressionService.renderExpression(GIT_FILE_CONTENT_2, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, 10))
        .thenReturn(GIT_FILE_CONTENT_2);

    String fileContent = (String) configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, 0);

    assertThat(fileContent).isEqualTo(GIT_FILE_CONTENT);
    fileContent = (String) configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, 1);

    assertThat(fileContent).isEqualTo(GIT_FILE_CONTENT_2);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testFileGetAsBase64() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().outcome(CONFIG_FILE_NOT_VALID_FILE_TYPE).found(true).build());

    String base64FileContent = (String) configFileFunctor.get(FUNCTOR_BASE64_METHOD_NAME, CONFIG_FILE_IDENTIFIER, null);

    assertThat(base64FileContent).isEqualTo(BASE64_FILE_CONTENT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSecretFileGetAsString() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_OUTCOME_WITH_SECRET_FILES).build());

    String secretExpression = (String) configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, null);

    assertThat(secretExpression)
        .isEqualTo("${ngSecretManager.obtainSecretFileAsString(\"account.AzureFileSecret\", 1)}");
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testSecretFileGetAsStringWithFileReference() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_WITH_MORE_SECRET_FILES).build());

    String secretExpression = (String) configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, 0);

    assertThat(secretExpression)
        .isEqualTo("${ngSecretManager.obtainSecretFileAsString(\"account.AzureFileSecret\", 1)}");

    secretExpression = (String) configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, CONFIG_FILE_IDENTIFIER, 1);
    assertThat(secretExpression).isEqualTo("${ngSecretManager.obtainSecretFileAsString(\"org.AzureFileSecret\", 1)}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSecretFileGetAsBase64() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_OUTCOME_WITH_SECRET_FILES).build());

    String secretExpression = (String) configFileFunctor.get(FUNCTOR_BASE64_METHOD_NAME, CONFIG_FILE_IDENTIFIER, null);

    assertThat(secretExpression)
        .isEqualTo("${ngSecretManager.obtainSecretFileAsBase64(\"account.AzureFileSecret\", 1)}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFileOnOrgLevel() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_WITH_SECRET).build());

    doReturn(FILE_CONTENT_ON_ORG)
        .when(configFileFunctor)
        .getContent(anyString(), anyString(), anyString(), anyString());
    when(engineExpressionService.renderExpression(FILE_CONTENT_ON_ORG, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, 10))
        .thenReturn(FILE_CONTENT_ON_ORG);

    String resolvedFileContent = (String) configFileFunctor.get(
        FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier:" + ORG_SCOPED_FILE_PATH, null);

    assertThat(resolvedFileContent).isEqualTo(FILE_CONTENT_ON_ORG);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFileOnProjectLevel() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_WITH_SECRET).build());
    when(
        engineExpressionService.renderExpression(FILE_CONTENT_ON_PROJECT, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, 10))
        .thenReturn(FILE_CONTENT_ON_PROJECT);

    doReturn(FILE_CONTENT_ON_PROJECT)
        .when(configFileFunctor)
        .getContent(anyString(), anyString(), anyString(), anyString());

    String resolvedFileContent = (String) configFileFunctor.get(
        FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier:" + PROJECT_SCOPED_FILE_PATH, null);

    assertThat(resolvedFileContent).isEqualTo(FILE_CONTENT_ON_PROJECT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetConfigFileIdentifierAndReference() {
    assertThatThrownBy(() -> configFileFunctor.getConfigFileIdentifierAndReference(""))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Config file identifier cannot be null or empty");

    assertThatThrownBy(() -> configFileFunctor.getConfigFileIdentifierAndReference("configFileId:"))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Found invalid config file identifier, configFileId:");

    assertThatThrownBy(() -> configFileFunctor.getConfigFileIdentifierAndReference(":configFileId"))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessage("Found invalid config file identifier, :configFileId");

    Pair<String, String> configFileIdentifier = configFileFunctor.getConfigFileIdentifierAndReference("configFileId");
    assertThat(configFileIdentifier.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifier.getRight()).isEqualTo(null);

    Pair<String, String> configFileIdentifierAndReferenceFSAccount =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:account:/folder1/folder2/configFile");
    assertThat(configFileIdentifierAndReferenceFSAccount.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceFSAccount.getRight()).isEqualTo("account:/folder1/folder2/configFile");

    Pair<String, String> configFileIdentifierAndReferenceFSOrg =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:org:/folder1/folder2/configFile");
    assertThat(configFileIdentifierAndReferenceFSOrg.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceFSOrg.getRight()).isEqualTo("org:/folder1/folder2/configFile");

    Pair<String, String> configFileIdentifierAndReferenceFSProject =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:/folder1/folder2/configFile");
    assertThat(configFileIdentifierAndReferenceFSProject.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceFSProject.getRight()).isEqualTo("/folder1/folder2/configFile");

    Pair<String, String> configFileIdentifierAndReferenceGit1 =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:ssh-winrm/configSshFile.yml");
    assertThat(configFileIdentifierAndReferenceGit1.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceGit1.getRight()).isEqualTo("ssh-winrm/configSshFile.yml");

    Pair<String, String> configFileIdentifierAndReferenceGit2 =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:ssh-winrm/configSshFile2.yml");
    assertThat(configFileIdentifierAndReferenceGit2.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceGit2.getRight()).isEqualTo("ssh-winrm/configSshFile2.yml");

    Pair<String, String> configFileIdentifierAndReferenceSecretAccount =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:account.AzureFileSecret");
    assertThat(configFileIdentifierAndReferenceSecretAccount.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceSecretAccount.getRight()).isEqualTo("account.AzureFileSecret");

    Pair<String, String> configFileIdentifierAndReferenceSecretOrg =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:org.AzureFileSecret");
    assertThat(configFileIdentifierAndReferenceSecretOrg.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceSecretOrg.getRight()).isEqualTo("org.AzureFileSecret");

    Pair<String, String> configFileIdentifierAndReferenceSecretProject =
        configFileFunctor.getConfigFileIdentifierAndReference("configFileId:AzureFileSecret");
    assertThat(configFileIdentifierAndReferenceSecretProject.getLeft()).isEqualTo("configFileId");
    assertThat(configFileIdentifierAndReferenceSecretProject.getRight()).isEqualTo("AzureFileSecret");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFileOnAccountLevel() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_WITH_SECRET).build());
    doReturn(FILE_CONTENT_ON_ACCOUNT)
        .when(configFileFunctor)
        .getContent(anyString(), anyString(), anyString(), anyString());

    when(
        engineExpressionService.renderExpression(FILE_CONTENT_ON_ACCOUNT, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, 10))
        .thenReturn(FILE_CONTENT_ON_ACCOUNT);

    String resolvedFileContent = (String) configFileFunctor.get(
        FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier:" + ACCOUNT_SCOPED_FILE_PATH, null);

    assertThat(resolvedFileContent).isEqualTo(FILE_CONTENT_ON_ACCOUNT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetFileWithoutReference() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().outcome(CONFIG_FILE_NOT_VALID_FILE_TYPE).found(true).build());
    when(
        engineExpressionService.renderExpression(FILE_CONTENT_ON_ACCOUNT, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, 10))
        .thenReturn(FILE_CONTENT_ON_ACCOUNT);

    doReturn(FILE_CONTENT_ON_ACCOUNT)
        .when(configFileFunctor)
        .getContent(anyString(), anyString(), anyString(), anyString());

    String resolvedFileContent =
        (String) configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier", null);

    assertThat(resolvedFileContent).isEqualTo(FILE_CONTENT_ON_ACCOUNT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetSecretOnAccountLevel() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_WITH_MORE_SECRET_FILES).build());

    String secretExpression = (String) configFileFunctor.get(
        FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier:" + ACCOUNT_SECRET_REF_PATH, null);

    assertThat(secretExpression)
        .isEqualTo("${ngSecretManager.obtainSecretFileAsString(\"account.AzureFileSecret\", 1)}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetSecretWithoutReference() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_OUTCOME_WITH_SECRET_FILES).build());

    String secretExpression = (String) configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier", null);

    assertThat(secretExpression)
        .isEqualTo("${ngSecretManager.obtainSecretFileAsString(\"account.AzureFileSecret\", 1)}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetSecretOnOrgLevel() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_WITH_MORE_SECRET_FILES).build());

    String secretExpression =
        (String) configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier:" + ORG_SECRET_REF_PATH, null);

    assertThat(secretExpression).isEqualTo("${ngSecretManager.obtainSecretFileAsString(\"org.AzureFileSecret\", 1)}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetSecretOnAProjectLevel() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(CONFIG_FILE_WITH_MORE_SECRET_FILES).build());

    String secretExpression = (String) configFileFunctor.get(
        FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier:" + PROJECT_SECRET_REF_PATH, null);

    assertThat(secretExpression).isEqualTo("${ngSecretManager.obtainSecretFileAsString(\"AzureFileSecret\", 1)}");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetGitFile() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(GIT_CONFIG_OUTCOME_WITH_FILES).build());

    when(engineExpressionService.renderExpression(GIT_FILE_CONTENT, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, 10))
        .thenReturn(GIT_FILE_CONTENT);

    String gitFileContent =
        (String) configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier:" + GIT_FILE_PATH, null);

    assertThat(gitFileContent).isEqualTo(GIT_FILE_CONTENT);
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testGetGitFileWithFileReference() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(GIT_CONFIG_OUTCOME_WITH_FILES).build());

    when(engineExpressionService.renderExpression(GIT_FILE_CONTENT, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, 10))
        .thenReturn(GIT_FILE_CONTENT);

    String gitFileContent =
        (String) configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier:" + GIT_FILE_PATH, null);

    assertThat(gitFileContent).isEqualTo(GIT_FILE_CONTENT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetGitFileWithoutReference() {
    Ambiance ambiance = getAmbiance();
    when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("configFiles")))
        .thenReturn(OptionalOutcome.builder().found(true).outcome(GIT_CONFIG_OUTCOME_WITH_FILES).build());

    when(engineExpressionService.renderExpression(GIT_FILE_CONTENT, ExpressionMode.RETURN_NULL_IF_UNRESOLVED, 10))
        .thenReturn(GIT_FILE_CONTENT);
    String gitFileContent = (String) configFileFunctor.get(FUNCTOR_STRING_METHOD_NAME, "configFileIdentifier", null);

    assertThat(gitFileContent).isEqualTo(GIT_FILE_CONTENT);
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_IDENTIFIER)
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_IDENTIFIER)
        .setExpressionFunctorToken(EXPRESSION_FUNCTOR_TOKEN)
        .build();
  }
}