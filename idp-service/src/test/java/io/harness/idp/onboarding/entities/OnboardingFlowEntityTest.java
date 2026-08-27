/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.onboarding.entities;

import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.UnexpectedException;
import io.harness.idp.common.Constants;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BaseIntegrationRequest;
import io.harness.spec.server.idp.v1.model.GitIntegrationRequest;
import io.harness.spec.server.idp.v1.model.WriteValidationDetails;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class OnboardingFlowEntityTest extends CategoryTest {
  static final String TEST_CONNECTOR_IDENTIFIER = "connectorIdentifier";
  static final String TEST_BRANCH = "branch";
  static final String TEST_PATH = "path";
  static final String TEST_FILE_PATH = "filePath";
  static final String TEST_REPOSITORY = "repository";
  static final String TEST_GITHUB_REPOSITORY = "https://github.com/harness/harness-core.git";
  static final String TEST_GITLAB_REPOSITORY = "https://gitlab.com/harness/harness-core.git";
  static final String TEST_AZURE_REPO_REPOSITORY =
      "https://automation-cdc@dev.azure.com/automation-cdc/IDP/_git/IDPAUTO";
  static final String TEST_BITBUCKET_CLOUD_REPOSITORY = "https://s_sathish@bitbucket.org/s_sathish/sathish-test.git";
  static final String TEST_BITBUCKET_SERVER_REPOSITORY = "https://bitbucket.dev.harness.io/scm/har/idp.git";
  static final String TEST_HARNESS_PROJECT_LEVEL_REPOSITORY =
      "https://git.harness.io/vpCkHKsDSxK9_KYfjCTMKA/HarnessHCRInternalUAT/Harness_Code/harness-core.git";
  static final String TEST_HARNESS_ORG_LEVEL_REPOSITORY =
      "https://git.harness.io/vpCkHKsDSxK9_KYfjCTMKA/HarnessHCRInternalUAT/harness-core.git";
  static final String TEST_HARNESS_ACCOUNT_LEVEL_REPOSITORY =
      "https://git.harness.io/vpCkHKsDSxK9_KYfjCTMKA/harness-core.git";

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testWriteDetailsFromGitIntegrationRequest() {
    GitIntegrationRequest gitIntegrationRequest = gitIntegrationRequest(TEST_REPOSITORY);
    OnboardingFlowEntity.WriteDetails writeDetails = OnboardingFlowEntity.from(gitIntegrationRequest);

    assertEquals(TEST_CONNECTOR_IDENTIFIER, writeDetails.getConnectorIdentifier());
    assertEquals(TEST_REPOSITORY, writeDetails.getRepositoryUrl());
    assertEquals(TEST_BRANCH, writeDetails.getBranch());
    assertEquals(TEST_PATH, writeDetails.getPath());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCalculateRegisterEntitiesOnIdpAt() {
    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    onboardingFlowEntity.setRegisterEntitiesOnIdpAt(1713595419000L);

    assertEquals(1713595419000L, onboardingFlowEntity.calculateRegisterEntitiesOnIdpAt());

    onboardingFlowEntity.setRegisterEntitiesOnIdpAt(Long.MAX_VALUE);

    assertTrue(onboardingFlowEntity.calculateRegisterEntitiesOnIdpAt() > System.currentTimeMillis());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGitIntegrationRequestFromWriteDetails() {
    OnboardingFlowEntity.WriteDetails writeDetails = writeDetails();

    GitIntegrationRequest gitIntegrationRequest = OnboardingFlowEntity.from(writeDetails);

    assertEquals(BaseIntegrationRequest.TypeEnum.GIT, gitIntegrationRequest.getType());
    assertEquals(TEST_CONNECTOR_IDENTIFIER, gitIntegrationRequest.getConnectorIdentifier());
    assertEquals(TEST_REPOSITORY, gitIntegrationRequest.getWriteValidationDetails().getRepository());
    assertEquals(TEST_BRANCH, gitIntegrationRequest.getWriteValidationDetails().getBranch());
    assertEquals(TEST_PATH, gitIntegrationRequest.getWriteValidationDetails().getPath());
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testIdpCatalogSourceLocationNotSupportedGitIntegrationType() {
    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    onboardingFlowEntity.idpCatalogSourceLocation("INVALID", gitIntegrationRequest("INVALID"), "INVALID");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testIdpCatalogSourceLocationGitlab() {
    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    String idpSourceLocation = onboardingFlowEntity.idpCatalogSourceLocation(
        Constants.GITLAB, gitIntegrationRequest(TEST_GITLAB_REPOSITORY), TEST_FILE_PATH);

    assertEquals("https://gitlab.com/harness/harness-core/blob/branch/path/filePath", idpSourceLocation);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testIdpCatalogSourceLocationGithub() {
    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    String idpSourceLocation = onboardingFlowEntity.idpCatalogSourceLocation(
        Constants.GITHUB, gitIntegrationRequest(TEST_GITHUB_REPOSITORY), TEST_FILE_PATH);

    assertEquals("https://github.com/harness/harness-core/blob/branch/path/filePath", idpSourceLocation);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testIdpCatalogSourceLocationAzureRepo() {
    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    String idpSourceLocation = onboardingFlowEntity.idpCatalogSourceLocation(
        Constants.AZURE_REPO, gitIntegrationRequest(TEST_AZURE_REPO_REPOSITORY), TEST_FILE_PATH);

    assertEquals("https://dev.azure.com/automation-cdc/IDP/_git/IDPAUTO/items?api-version=6.0&path=path/"
            + "filePath&version=GBbranch",
        idpSourceLocation);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testIdpCatalogSourceLocationBitbucketCloud() {
    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    String idpSourceLocation = onboardingFlowEntity.idpCatalogSourceLocation(
        Constants.BITBUCKET_CLOUD, gitIntegrationRequest(TEST_BITBUCKET_CLOUD_REPOSITORY), TEST_FILE_PATH);

    assertEquals("https://bitbucket.org/s_sathish/sathish-test/src/branch/path/filePath", idpSourceLocation);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testIdpCatalogSourceLocationBitbucketServer() {
    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    String idpSourceLocation = onboardingFlowEntity.idpCatalogSourceLocation(
        Constants.BITBUCKET_SERVER, gitIntegrationRequest(TEST_BITBUCKET_SERVER_REPOSITORY), TEST_FILE_PATH);

    assertEquals("https://bitbucket.dev.harness.io/projects/har/repos/idp/browse/path/filePath?at=refs/heads/branch",
        idpSourceLocation);
  }

  @Test(expected = UnexpectedException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testIdpCatalogSourceLocationBitbucketServerError() {
    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    onboardingFlowEntity.idpCatalogSourceLocation(
        Constants.BITBUCKET_SERVER, gitIntegrationRequest("https://bitbucket.dev.harness.io/har"), TEST_FILE_PATH);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testIdpCatalogSourceLocationHarnessProjectLevelRepo() {
    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    String idpSourceLocation = onboardingFlowEntity.idpCatalogSourceLocation(
        Constants.HARNESS, gitIntegrationRequest(TEST_HARNESS_PROJECT_LEVEL_REPOSITORY), TEST_FILE_PATH);

    assertEquals("https://git.harness.io/ng/account/vpCkHKsDSxK9_KYfjCTMKA/module/code/orgs/HarnessHCRInternalUAT/"
            + "projects/Harness_Code/repos/harness-core/files/branch/~/path/filePath",
        idpSourceLocation);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testIdpCatalogSourceLocationHarnessOrgLevelRepo() {
    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    String idpSourceLocation = onboardingFlowEntity.idpCatalogSourceLocation(
        Constants.HARNESS, gitIntegrationRequest(TEST_HARNESS_ORG_LEVEL_REPOSITORY), TEST_FILE_PATH);

    assertEquals("https://git.harness.io/ng/account/vpCkHKsDSxK9_KYfjCTMKA/module/code/orgs/HarnessHCRInternalUAT/"
            + "repos/harness-core/files/branch/~/path/filePath",
        idpSourceLocation);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testIdpCatalogSourceLocationHarnessAccountLevelRepo() {
    OnboardingFlowEntity onboardingFlowEntity = new OnboardingFlowEntity();
    String idpSourceLocation = onboardingFlowEntity.idpCatalogSourceLocation(
        Constants.HARNESS, gitIntegrationRequest(TEST_HARNESS_ACCOUNT_LEVEL_REPOSITORY), TEST_FILE_PATH);

    assertEquals("https://git.harness.io/ng/account/vpCkHKsDSxK9_KYfjCTMKA/module/code/repos/harness-core/files/branch/"
            + "~/path/filePath",
        idpSourceLocation);
  }

  private GitIntegrationRequest gitIntegrationRequest(String repository) {
    GitIntegrationRequest gitIntegrationRequest = new GitIntegrationRequest();
    gitIntegrationRequest.setConnectorIdentifier(TEST_CONNECTOR_IDENTIFIER);

    WriteValidationDetails writeValidationDetails = new WriteValidationDetails();
    writeValidationDetails.setRepository(repository);
    writeValidationDetails.setBranch(TEST_BRANCH);
    writeValidationDetails.setPath(TEST_PATH);

    gitIntegrationRequest.setWriteValidationDetails(writeValidationDetails);

    return gitIntegrationRequest;
  }

  private OnboardingFlowEntity.WriteDetails writeDetails() {
    OnboardingFlowEntity.WriteDetails writeDetails = new OnboardingFlowEntity.WriteDetails();
    writeDetails.setConnectorIdentifier(TEST_CONNECTOR_IDENTIFIER);
    writeDetails.setRepositoryUrl(TEST_REPOSITORY);
    writeDetails.setBranch(TEST_BRANCH);
    writeDetails.setPath(TEST_PATH);
    return writeDetails;
  }
}
