/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.beans.response.GitFileResponse;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.processor.PlaceholderProcessor;
import io.harness.idp.catalog.processor.api.SpecFetchException;
import io.harness.idp.catalog.processor.api.SpecFetcher;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.integrations.service.git.GitIntegrationOps;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.idp.integrations.service.git.GithubIntegrationOpsImpl;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.rule.Owner;
import io.harness.service.ScmServiceClient;
import io.harness.spec.server.idp.v1.model.EntityResponse;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class ApiDefinitionResolverTest extends CategoryTest {
  private static final String ACCOUNT_ID = "test-account";
  private static final String ORG_ID = "test-org";
  private static final String PROJECT_ID = "test-project";
  private static final String OPENAPI_CONTENT = "openapi: 3.0.1\ninfo:\n  title: Petstore\n  version: 1.0.0";

  @Mock private PlaceholderProcessor placeholderProcessor;
  @Mock private SpecFetcher specFetcher;
  @Mock private GitIntegrationServiceImpl gitIntegrationService;
  @Mock private HarnessCodeRepoConfig harnessCodeRepoConfig;
  @Mock private HarnessCodeConnectorUtils harnessCodeConnectorUtils;
  @Mock private ScmServiceClient scmServiceClient;
  @Mock private SCMGrpc.SCMBlockingStub scmBlockingStub;

  @InjectMocks private ApiDefinitionResolver apiDefinitionResolver;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToRawContentUrl_GitHub() {
    assertThat(ApiDefinitionResolver.toRawContentUrl("https://github.com/owner/repo/blob/main/openapi.yaml"))
        .isEqualTo("https://raw.githubusercontent.com/owner/repo/main/openapi.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToRawContentUrl_GitHubSlashedBranch() {
    assertThat(
        ApiDefinitionResolver.toRawContentUrl(
            "https://github.com/CFG-ENTERPRISE/fuse-orchestrator/blob/feature/EP-94935-catalog/.harness/openapi.yaml"))
        .isEqualTo("https://raw.githubusercontent.com/CFG-ENTERPRISE/fuse-orchestrator/feature/EP-94935-catalog/"
            + ".harness/openapi.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToRawContentUrl_GitHubEnterprise() {
    assertThat(ApiDefinitionResolver.toRawContentUrl("https://ghe.company.com/owner/repo/blob/main/openapi.yaml"))
        .isEqualTo("https://ghe.company.com/raw/owner/repo/main/openapi.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToRawContentUrl_GitLab() {
    assertThat(ApiDefinitionResolver.toRawContentUrl("https://gitlab.com/group/repo/-/blob/main/openapi.yaml"))
        .isEqualTo("https://gitlab.com/group/repo/-/raw/main/openapi.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToRawContentUrl_BitbucketCloud() {
    assertThat(ApiDefinitionResolver.toRawContentUrl("https://bitbucket.org/owner/repo/src/main/openapi.yaml"))
        .isEqualTo("https://bitbucket.org/owner/repo/raw/main/openapi.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToRawContentUrl_PublicSwagger_NoConversion() {
    String url = "https://petstore.swagger.io/v2/swagger.json";
    assertThat(ApiDefinitionResolver.toRawContentUrl(url)).isEqualTo(url);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToRawContentUrl_AzureDevOps_NoConversion() {
    String url = "https://dev.azure.com/org/project/_git/repo?version=GBmain&path=/openapi.yaml";
    assertThat(ApiDefinitionResolver.toRawContentUrl(url)).isEqualTo(url);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToRawContentUrl_GitLabSelfHosted_NotMatchedByGHE() {
    assertThat(ApiDefinitionResolver.toRawContentUrl("https://git.company.com/group/repo/-/blob/main/openapi.yaml"))
        .isEqualTo("https://git.company.com/group/repo/-/raw/main/openapi.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToRawContentUrl_Null() {
    assertThat(ApiDefinitionResolver.toRawContentUrl(null)).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveWithTextPlaceholderGitHubSlashedBranch() {
    String url =
        "https://github.com/CFG-ENTERPRISE/fuse-orchestrator/blob/feature/EP-94935-catalog/.harness/openapi.yaml"
        + "?ref=feature/EP-94935-catalog";

    InlineCatalogEntity entity = buildEntity(Map.of("$text", url));
    setupConnectorMocks(entity, url, "account.github-connector");
    setupGitIntegrationMocks("github", OPENAPI_CONTENT);

    PlaceholderProcessor.GitInfo gitInfo = new PlaceholderProcessor.GitInfo();
    gitInfo.provider = "github";
    gitInfo.owner = "CFG-ENTERPRISE";
    gitInfo.repo = "fuse-orchestrator";
    gitInfo.branch = "feature/EP-94935-catalog";
    gitInfo.filePath = ".harness/openapi.yaml";
    when(placeholderProcessor.parseGitInfoWithRef(url)).thenReturn(gitInfo);
    when(placeholderProcessor.resolveRepoName(any(ConnectorInfoDTO.class), eq(gitInfo)))
        .thenReturn("fuse-orchestrator");

    EntityResponse response = apiDefinitionResolver.resolve(entity);

    assertThat(response).isNotNull();
    verify(specFetcher, never()).fetch(anyString());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveWithTextPlaceholderGitLabSlashedBranch() {
    String url = "https://gitlab.com/org/group/repo/-/blob/feature/my-ticket/openapi.yaml?ref=feature/my-ticket";

    InlineCatalogEntity entity = buildEntity(Map.of("$text", url));
    setupConnectorMocks(entity, url, "account.gitlab-connector");
    setupGitIntegrationMocks("gitlab", OPENAPI_CONTENT);

    PlaceholderProcessor.GitInfo gitInfo = new PlaceholderProcessor.GitInfo();
    gitInfo.provider = "gitlab";
    gitInfo.repo = "repo";
    gitInfo.branch = "feature/my-ticket";
    gitInfo.filePath = "openapi.yaml";
    when(placeholderProcessor.parseGitInfoWithRef(url)).thenReturn(gitInfo);
    when(placeholderProcessor.resolveRepoName(any(ConnectorInfoDTO.class), eq(gitInfo))).thenReturn("repo");

    EntityResponse response = apiDefinitionResolver.resolve(entity);

    assertThat(response).isNotNull();
    verify(specFetcher, never()).fetch(anyString());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveWithTextPlaceholderBitbucketCloudSlashedBranch() {
    String url = "https://bitbucket.org/org/repo/src/feature/my-ticket/path/to/openapi.yaml?at=feature/my-ticket";

    InlineCatalogEntity entity = buildEntity(Map.of("$text", url));
    setupConnectorMocks(entity, url, "account.bb-connector");
    setupGitIntegrationMocks("bitbucket", OPENAPI_CONTENT);

    PlaceholderProcessor.GitInfo gitInfo = new PlaceholderProcessor.GitInfo();
    gitInfo.provider = "bitbucket-cloud";
    gitInfo.repo = "repo";
    gitInfo.branch = "feature/my-ticket";
    gitInfo.filePath = "path/to/openapi.yaml";
    when(placeholderProcessor.parseGitInfoWithRef(url)).thenReturn(gitInfo);
    when(placeholderProcessor.resolveRepoName(any(ConnectorInfoDTO.class), eq(gitInfo))).thenReturn("repo");

    EntityResponse response = apiDefinitionResolver.resolve(entity);

    assertThat(response).isNotNull();
    verify(specFetcher, never()).fetch(anyString());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveWithTextPlaceholderBitbucketServer() {
    String url = "https://bitbucket.company.com/projects/PROJ/repos/repo/browse/path/to/openapi.yaml?at=refs/heads/"
        + "feature/my-branch";
    String cleanUrl = PlaceholderProcessor.removeRefParam(url);

    InlineCatalogEntity entity = buildEntity(Map.of("$text", url));
    when(specFetcher.fetch(cleanUrl)).thenThrow(new SpecFetchException("on-premise host blocked"));
    setupConnectorMocks(entity, url, "account.bbs-connector");
    setupGitIntegrationMocks("bitbucket_server", OPENAPI_CONTENT);

    PlaceholderProcessor.GitInfo gitInfo = new PlaceholderProcessor.GitInfo();
    gitInfo.provider = "bitbucket-server";
    gitInfo.repo = "repo";
    gitInfo.branch = "feature/my-branch";
    gitInfo.filePath = "path/to/openapi.yaml";
    when(placeholderProcessor.parseGitInfoWithRef(url)).thenReturn(gitInfo);
    when(placeholderProcessor.resolveRepoName(any(ConnectorInfoDTO.class), eq(gitInfo))).thenReturn("repo");

    EntityResponse response = apiDefinitionResolver.resolve(entity);

    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveWithTextPlaceholderAzureDevOps() {
    String url = "https://dev.azure.com/org/project/_git/repo?version=GBmain&path=/path/to/openapi.yaml";
    String cleanUrl = PlaceholderProcessor.removeRefParam(url);

    InlineCatalogEntity entity = buildEntity(Map.of("$text", url));
    when(specFetcher.fetch(cleanUrl)).thenThrow(new SpecFetchException("auth required"));
    setupConnectorMocks(entity, url, "account.azure-connector");
    setupGitIntegrationMocks("azure", OPENAPI_CONTENT);

    PlaceholderProcessor.GitInfo gitInfo = new PlaceholderProcessor.GitInfo();
    gitInfo.provider = "azure";
    gitInfo.repo = "repo";
    gitInfo.branch = "main";
    gitInfo.filePath = "/path/to/openapi.yaml";
    when(placeholderProcessor.parseGitInfoWithRef(url)).thenReturn(gitInfo);
    when(placeholderProcessor.resolveRepoName(any(ConnectorInfoDTO.class), eq(gitInfo))).thenReturn("repo");

    EntityResponse response = apiDefinitionResolver.resolve(entity);

    assertThat(response).isNotNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveWithPublicUrl() {
    String url = "https://petstore.swagger.io/v2/swagger.json";

    InlineCatalogEntity entity = buildEntity(Map.of("$text", url));
    when(specFetcher.fetch(url)).thenReturn(OPENAPI_CONTENT);

    EntityResponse response = apiDefinitionResolver.resolve(entity);

    assertThat(response).isNotNull();
    verify(specFetcher).fetch(url);
    verifyNoInteractions(gitIntegrationService);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveViaIntegrationConnectorSkipsAnonymousFetch() {
    String rawUrl = "https://github.com/org/repo/blob/main/openapi.yaml?ref=feature/branch";

    InlineCatalogEntity entity = buildEntity(Map.of("$text", rawUrl));
    setupConnectorMocks(entity, rawUrl, "account.github-connector");
    setupGitIntegrationMocks("github", OPENAPI_CONTENT);

    PlaceholderProcessor.GitInfo gitInfo = new PlaceholderProcessor.GitInfo();
    gitInfo.provider = "github";
    gitInfo.owner = "org";
    gitInfo.repo = "repo";
    gitInfo.branch = "feature/branch";
    gitInfo.filePath = "openapi.yaml";
    when(placeholderProcessor.parseGitInfoWithRef(rawUrl)).thenReturn(gitInfo);
    when(placeholderProcessor.resolveRepoName(any(ConnectorInfoDTO.class), eq(gitInfo))).thenReturn("repo");

    EntityResponse response = apiDefinitionResolver.resolve(entity);

    assertThat(response).isNotNull();
    verify(specFetcher, never()).fetch(anyString());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveGitXEntityUsesGitXConnectorDirectly() {
    String url = "https://github.com/org/repo/blob/main/openapi.yaml";

    GitReferencedCatalogEntity entity =
        GitReferencedCatalogEntity.builder()
            .accountIdentifier(ACCOUNT_ID)
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .kind("API")
            .identifier("test-api")
            .connectorRef("account.gitx-connector")
            .repo("repo")
            .repoURL("https://github.com/org/repo")
            .filePath("catalog-info.yaml")
            .fallBackBranch("main")
            .storeType(StoreType.REMOTE)
            .referenceType(ReferenceType.GIT)
            .yaml("apiVersion: backstage.io/v1alpha1\nkind: API\nspec:\n  definition:\n    $text: "
                + "https://github.com/org/repo/blob/main/openapi.yaml")
            .build();
    Map<String, Object> spec = new HashMap<>();
    spec.put("definition", new HashMap<>(Map.of("$text", url)));
    entity.setSpec(spec);

    ConnectorInfoDTO connectorInfo =
        ConnectorInfoDTO.builder().identifier("gitx-connector").accountIdentifier(ACCOUNT_ID).build();
    when(placeholderProcessor.getConnectorInfo(entity, "account.gitx-connector")).thenReturn(connectorInfo);
    setupGitIntegrationMocks("github", OPENAPI_CONTENT);

    PlaceholderProcessor.GitInfo gitInfo = new PlaceholderProcessor.GitInfo();
    gitInfo.provider = "github";
    gitInfo.owner = "org";
    gitInfo.repo = "repo";
    gitInfo.branch = "main";
    gitInfo.filePath = "openapi.yaml";
    when(placeholderProcessor.parseGitInfoWithRef(url)).thenReturn(gitInfo);
    when(placeholderProcessor.resolveRepoName(any(ConnectorInfoDTO.class), eq(gitInfo))).thenReturn("repo");

    EntityResponse response = apiDefinitionResolver.resolve(entity);

    assertThat(response).isNotNull();
    verify(specFetcher, never()).fetch(anyString());
    verify(placeholderProcessor, never()).getConnectorRefForApiResolve(any(), anyString());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveAllStrategiesFail_ThrowsInvalidRequestException() {
    String url = "https://github.com/org/private-repo/blob/main/openapi.yaml";
    String rawContentUrl = "https://raw.githubusercontent.com/org/private-repo/main/openapi.yaml";

    InlineCatalogEntity entity = buildEntity(Map.of("$text", url));
    when(specFetcher.fetch(rawContentUrl)).thenThrow(new SpecFetchException("private"));
    when(placeholderProcessor.getConnectorRefForApiResolve(entity, url)).thenReturn(null);

    assertThatThrownBy(() -> apiDefinitionResolver.resolve(entity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Could not resolve API definition from URL");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveWithNoDefinition() {
    InlineCatalogEntity entity = buildEntity(null);
    entity.setSpec(Map.of("lifecycle", "production"));

    assertThatThrownBy(() -> apiDefinitionResolver.resolve(entity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("spec.definition");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveWithNullSpec() {
    InlineCatalogEntity entity = InlineCatalogEntity.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .kind("API")
                                     .identifier("test-api")
                                     .referenceType(ReferenceType.INLINE)
                                     .yaml("apiVersion: backstage.io/v1alpha1\nkind: API\nspec: {}")
                                     .build();
    entity.setSpec(null);

    assertThatThrownBy(() -> apiDefinitionResolver.resolve(entity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("spec.definition");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveWithAlreadyResolvedDefinitionReturnsEntityAsIs() {
    Map<String, Object> spec = new HashMap<>();
    spec.put("definition", "openapi: 3.0.1\ninfo:\n  title: Petstore");

    InlineCatalogEntity entity = InlineCatalogEntity.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .kind("API")
                                     .identifier("test-api")
                                     .referenceType(ReferenceType.INLINE)
                                     .yaml("apiVersion: backstage.io/v1alpha1\nkind: API\nspec:\n  definition: plain")
                                     .build();
    entity.setSpec(spec);

    EntityResponse response = apiDefinitionResolver.resolve(entity);

    assertThat(response).isNotNull();
    verifyNoInteractions(specFetcher);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveWithAbsoluteUrlStringDefinition() {
    String url = "https://petstore.swagger.io/v2/swagger.json";

    Map<String, Object> spec = new HashMap<>();
    spec.put("definition", url);

    InlineCatalogEntity entity = InlineCatalogEntity.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .kind("API")
                                     .identifier("test-api")
                                     .referenceType(ReferenceType.INLINE)
                                     .yaml("apiVersion: backstage.io/v1alpha1\nkind: API\nspec:\n  definition: " + url)
                                     .build();
    entity.setSpec(spec);

    when(specFetcher.fetch(url)).thenReturn(OPENAPI_CONTENT);

    EntityResponse response = apiDefinitionResolver.resolve(entity);

    assertThat(response).isNotNull();
    verify(specFetcher).fetch(url);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveWithYamlPlaceholder() {
    String url = "https://github.com/org/repo/blob/main/openapi.yaml";
    String rawContentUrl = "https://raw.githubusercontent.com/org/repo/main/openapi.yaml";

    InlineCatalogEntity entity = buildEntity(Map.of("$yaml", url));
    when(specFetcher.fetch(rawContentUrl)).thenReturn(OPENAPI_CONTENT);

    EntityResponse response = apiDefinitionResolver.resolve(entity);

    assertThat(response).isNotNull();
    verify(specFetcher).fetch(rawContentUrl);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveWithJsonPlaceholder() {
    String url = "https://petstore.swagger.io/v2/swagger.json";
    String jsonContent = "{\"openapi\": \"3.0.1\", \"info\": {\"title\": \"Petstore\"}}";

    InlineCatalogEntity entity = buildEntity(Map.of("$json", url));
    when(specFetcher.fetch(url)).thenReturn(jsonContent);

    EntityResponse response = apiDefinitionResolver.resolve(entity);

    assertThat(response).isNotNull();
    verify(specFetcher).fetch(url);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveWithYamlPlaceholderMalformedContent() {
    String url = "https://github.com/org/repo/blob/main/openapi.yaml";
    String rawContentUrl = "https://raw.githubusercontent.com/org/repo/main/openapi.yaml";
    String invalidYaml = "not: valid: yaml: [unbalanced";

    InlineCatalogEntity entity = buildEntity(Map.of("$yaml", url));
    when(specFetcher.fetch(rawContentUrl)).thenReturn(invalidYaml);

    assertThatThrownBy(() -> apiDefinitionResolver.resolve(entity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("not valid YAML");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveRemovesRefParamBeforeConnectorLookup() {
    String rawUrl = "https://github.com/org/repo/blob/feature/branch/openapi.yaml?ref=feature/branch";
    String cleanUrl = "https://github.com/org/repo/blob/feature/branch/openapi.yaml";
    String rawContentUrl = "https://raw.githubusercontent.com/org/repo/feature/branch/openapi.yaml";

    InlineCatalogEntity entity = buildEntity(Map.of("$text", rawUrl));
    when(specFetcher.fetch(rawContentUrl)).thenThrow(new SpecFetchException("private"));
    when(placeholderProcessor.getConnectorRefForApiResolve(entity, cleanUrl)).thenReturn(null);

    assertThatThrownBy(() -> apiDefinitionResolver.resolve(entity)).isInstanceOf(InvalidRequestException.class);

    verify(specFetcher).fetch(rawContentUrl);
    verify(placeholderProcessor).getConnectorRefForApiResolve(entity, cleanUrl);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveHarnessCodeViaConnector() {
    String url = "https://app.harness.io/ng/account/abc123/module/code/orgs/default/projects/myproj/repos/my-repo"
        + "/files/refs/heads/main/~/path/to/openapi.yaml";

    InlineCatalogEntity entity = buildEntity(Map.of("$text", url));

    PlaceholderProcessor.GitInfo gitInfo = new PlaceholderProcessor.GitInfo();
    gitInfo.provider = "harness-code";
    gitInfo.repo = "orgs/default/projects/myproj/repos/my-repo";
    gitInfo.branch = "main";
    gitInfo.filePath = "path/to/openapi.yaml";
    when(placeholderProcessor.parseGitInfoWithRef(url)).thenReturn(gitInfo);

    setupHarnessCodeMocks(OPENAPI_CONTENT);

    EntityResponse response = apiDefinitionResolver.resolve(entity);

    assertThat(response).isNotNull();
    verify(specFetcher, never()).fetch(anyString());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveHarnessCodeSlashedBranch() {
    String url = "https://harness0.harness.io/ng/account/abc123/module/code/orgs/PROD/projects/Harness_Commons"
        + "/repos/harness-core/files/refs/heads/release/ng-manager_1.160.0/~/path/to/openapi.yaml";

    InlineCatalogEntity entity = buildEntity(Map.of("$text", url));

    PlaceholderProcessor.GitInfo gitInfo = new PlaceholderProcessor.GitInfo();
    gitInfo.provider = "harness-code";
    gitInfo.repo = "orgs/PROD/projects/Harness_Commons/repos/harness-core";
    gitInfo.branch = "release/ng-manager_1.160.0";
    gitInfo.filePath = "path/to/openapi.yaml";
    when(placeholderProcessor.parseGitInfoWithRef(url)).thenReturn(gitInfo);

    setupHarnessCodeMocks(OPENAPI_CONTENT);

    EntityResponse response = apiDefinitionResolver.resolve(entity);

    assertThat(response).isNotNull();
    verify(specFetcher, never()).fetch(anyString());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveWithJsonPlaceholderMalformedContent() {
    String url = "https://petstore.swagger.io/v2/swagger.json";
    String invalidContent = "key: [unbalanced";

    InlineCatalogEntity entity = buildEntity(Map.of("$json", url));
    when(specFetcher.fetch(url)).thenReturn(invalidContent);

    assertThatThrownBy(() -> apiDefinitionResolver.resolve(entity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("not valid YAML");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveDoesNotMutateOriginalSpecMap() {
    String url = "https://petstore.swagger.io/v2/swagger.json";

    Map<String, Object> originalDefinition = new HashMap<>(Map.of("$text", url));
    Map<String, Object> originalSpec = new HashMap<>();
    originalSpec.put("definition", originalDefinition);
    originalSpec.put("lifecycle", "production");

    InlineCatalogEntity entity = InlineCatalogEntity.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .orgIdentifier(ORG_ID)
                                     .projectIdentifier(PROJECT_ID)
                                     .kind("API")
                                     .identifier("test-api")
                                     .referenceType(ReferenceType.INLINE)
                                     .yaml("apiVersion: backstage.io/v1alpha1\nkind: API\nspec:\n  definition:\n"
                                         + "    $text: " + url)
                                     .spec(originalSpec)
                                     .build();

    when(specFetcher.fetch(url)).thenReturn(OPENAPI_CONTENT);

    apiDefinitionResolver.resolve(entity);

    assertThat(originalSpec.get("definition")).isEqualTo(originalDefinition);
    assertThat(originalDefinition).containsKey("$text");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testLooksLikeHtml_DocType() {
    assertThat(ApiDefinitionResolver.looksLikeHtml("<!DOCTYPE html><html>...</html>")).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testLooksLikeHtml_HtmlTag() {
    assertThat(ApiDefinitionResolver.looksLikeHtml("<html lang=\"en\">")).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testLooksLikeHtml_YamlContent() {
    assertThat(ApiDefinitionResolver.looksLikeHtml("openapi: 3.0.1\ninfo:\n  title: Test")).isFalse();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testLooksLikeHtml_Null() {
    assertThat(ApiDefinitionResolver.looksLikeHtml(null)).isFalse();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testLooksLikeHtml_LeadingWhitespace() {
    assertThat(ApiDefinitionResolver.looksLikeHtml("   \n  <!doctype html>")).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIsHarnessCodeUrl_Valid() {
    assertThat(ApiDefinitionResolver.isHarnessCodeUrl("https://app.harness.io/ng/account/abc/module/code/orgs/o/"
                   + "projects/p/repos/r/files/refs/heads/main/~/f.yaml"))
        .isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIsHarnessCodeUrl_GitHubIsFalse() {
    assertThat(ApiDefinitionResolver.isHarnessCodeUrl("https://github.com/org/repo/blob/main/openapi.yaml")).isFalse();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIsHarnessCodeUrl_HarnessIoWithoutCodeModuleIsFalse() {
    assertThat(ApiDefinitionResolver.isHarnessCodeUrl("https://app.harness.io/ng/account/abc/some/other/path"))
        .isFalse();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testIsHarnessCodeUrl_Null() {
    assertThat(ApiDefinitionResolver.isHarnessCodeUrl(null)).isFalse();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToRawContentUrl_BitbucketServerBrowse() {
    assertThat(
        ApiDefinitionResolver.toRawContentUrl(
            "https://bitbucket.company.com/projects/PROJ/repos/repo/browse/path/to/openapi.yaml?at=refs/heads/main"))
        .isEqualTo(
            "https://bitbucket.company.com/projects/PROJ/repos/repo/raw/path/to/openapi.yaml?at=refs/heads/main");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseHarnessCodeRepoRef_FullyQualified() {
    ApiDefinitionResolver.HarnessCodeRepoRef ref =
        ApiDefinitionResolver.parseHarnessCodeRepoRef("orgs/PROD/projects/Harness_Commons/repos/harness-core");
    assertThat(ref.org).isEqualTo("PROD");
    assertThat(ref.project).isEqualTo("Harness_Commons");
    assertThat(ref.repoName).isEqualTo("harness-core");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseHarnessCodeRepoRef_OrgOnly() {
    ApiDefinitionResolver.HarnessCodeRepoRef ref =
        ApiDefinitionResolver.parseHarnessCodeRepoRef("orgs/default/repos/my-repo");
    assertThat(ref.org).isEqualTo("default");
    assertThat(ref.project).isNull();
    assertThat(ref.repoName).isEqualTo("my-repo");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseHarnessCodeRepoRef_AccountScope() {
    ApiDefinitionResolver.HarnessCodeRepoRef ref = ApiDefinitionResolver.parseHarnessCodeRepoRef("repos/my-repo");
    assertThat(ref.org).isNull();
    assertThat(ref.project).isNull();
    assertThat(ref.repoName).isEqualTo("my-repo");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveAnonymousFetchReturnsHtmlThrows() {
    String url = "https://dev.azure.com/org/project/_git/repo?version=GBmain&path=/openapi.yaml";

    InlineCatalogEntity entity = buildEntity(Map.of("$text", url));
    when(placeholderProcessor.getConnectorRefForApiResolve(entity, url)).thenReturn(null);
    when(specFetcher.fetch(url)).thenReturn("<!DOCTYPE html><html><body>Azure DevOps</body></html>");

    assertThatThrownBy(() -> apiDefinitionResolver.resolve(entity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Could not resolve API definition from URL");
  }

  private InlineCatalogEntity buildEntity(Map<String, Object> definition) {
    Map<String, Object> spec = new HashMap<>();
    if (definition != null) {
      spec.put("definition", new HashMap<>(definition));
    }

    String yamlStr = "apiVersion: backstage.io/v1alpha1\nkind: API\nspec:\n  definition:\n    $text: placeholder";
    return InlineCatalogEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .orgIdentifier(ORG_ID)
        .projectIdentifier(PROJECT_ID)
        .kind("API")
        .identifier("test-api")
        .referenceType(ReferenceType.INLINE)
        .yaml(yamlStr)
        .spec(spec)
        .build();
  }

  @SuppressWarnings("unchecked")
  private void setupConnectorMocks(InlineCatalogEntity entity, String url, String connectorRef) {
    String cleanUrl = PlaceholderProcessor.removeRefParam(url);
    when(placeholderProcessor.getConnectorRefForApiResolve(entity, cleanUrl)).thenReturn(connectorRef);

    ConnectorInfoDTO connectorInfo = ConnectorInfoDTO.builder()
                                         .identifier(connectorRef.replace("account.", ""))
                                         .accountIdentifier(ACCOUNT_ID)
                                         .orgIdentifier(ORG_ID)
                                         .projectIdentifier(PROJECT_ID)
                                         .build();
    when(placeholderProcessor.getConnectorInfo(entity, connectorRef)).thenReturn(connectorInfo);
  }

  @SuppressWarnings("unchecked")
  private void setupGitIntegrationMocks(String integrationType, String fileContent) {
    when(gitIntegrationService.getGitIntegrationType(any(ConnectorInfoDTO.class))).thenReturn(integrationType);

    GithubIntegrationOpsImpl gitOps = mock(GithubIntegrationOpsImpl.class);
    when(gitIntegrationService.getServiceForGitIntegration(integrationType)).thenReturn((GitIntegrationOps) gitOps);
    when(gitOps.getFileContent(any(Scope.class), anyString(), anyString(), anyString(), anyString()))
        .thenReturn(fileContent);
  }

  private void setupHarnessCodeMocks(String fileContent) {
    when(harnessCodeRepoConfig.getServiceClientSharedSecret()).thenReturn("secret");
    when(harnessCodeRepoConfig.getApiUrl()).thenReturn("https://app.harness.io/code/api");
    when(harnessCodeRepoConfig.getGitBaseUrl()).thenReturn("https://app.harness.io/code/git");
    when(harnessCodeConnectorUtils.getToken("secret")).thenReturn("jwt-token");
    GitFileResponse gitFileResponse = GitFileResponse.builder().statusCode(200).content(fileContent).build();
    when(scmServiceClient.getFile(any(), any(), any())).thenReturn(gitFileResponse);
  }
}
