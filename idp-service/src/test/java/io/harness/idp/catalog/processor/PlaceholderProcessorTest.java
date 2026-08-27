/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.processor;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;
import static io.harness.rule.OwnerRule.ROUNAK;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.exception.UnexpectedException;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.integrations.entities.git.GithubIntegrationEntity;
import io.harness.idp.integrations.repositories.IntegrationEntityRepository;
import io.harness.idp.integrations.service.git.GitIntegrationOps;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.yaml.snakeyaml.Yaml;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(HarnessTeam.IDP)
public class PlaceholderProcessorTest extends CategoryTest {
  @Mock private IntegrationEntityRepository integrationEntityRepository;
  @Mock private GitIntegrationServiceImpl gitIntegrationService;
  @Mock private ConnectorResourceClient connectorResourceClient;

  @InjectMocks private PlaceholderProcessor placeholderProcessor;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testProcessWithNoPlaceholders() {
    InlineCatalogEntity catalogEntity = new InlineCatalogEntity();
    String yaml = "apiVersion: v1\nkind: Component\nname: test";
    catalogEntity.setYaml(yaml);
    catalogEntity.setAccountIdentifier("test-account");

    String result = placeholderProcessor.process(catalogEntity);

    assertThat(result).isEqualTo(yaml);
    verify(integrationEntityRepository, never()).findByAccountIdentifierAndAdditionalIndexer(anyString(), anyString());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetPlaceholdersDecorator() {
    String unresolvedYaml = "spec:\n  readme:\n    $text: ./README.md\n  config:\n    $yaml: ./config.yaml";
    String resolvedYaml = "spec:\n  readme: This is README\n  config:\n    key1: value1\n    key2: value2";

    Map<String, Object> decorator = placeholderProcessor.getPlaceholdersDecorator(unresolvedYaml, resolvedYaml);

    assertThat(decorator).isNotEmpty();
    assertThat(decorator).containsKey("spec");
    Map<String, Object> specDecorator = (Map<String, Object>) decorator.get("spec");
    assertThat(specDecorator).containsKey("readme");
    assertThat(specDecorator).containsKey("config");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testGetPlaceholdersDecoratorStoresTextContentVerbatim() {
    // Regression: a $text placeholder resolves to a raw String. The decorator must store that
    // string verbatim, NOT re-encode it via Yaml().dump(), which would wrap a multi-line spec into a
    // single quoted YAML scalar (a string-of-a-string) that the OpenAPI parser can't read.
    String unresolvedYaml = "spec:\n  definition:\n    $text: https://github.com/acme/repo/blob/main/openapi.yaml";
    String resolvedYaml = "spec:\n  definition: |\n"
        + "    openapi: 3.0.1\n"
        + "    info:\n"
        + "      title: Petstore\n"
        + "      version: 1.0.0\n"
        + "    paths:\n"
        + "      /pet:\n"
        + "        get:\n"
        + "          responses:\n"
        + "            '200':\n"
        + "              description: ok\n";

    Map<String, Object> decorator = placeholderProcessor.getPlaceholdersDecorator(unresolvedYaml, resolvedYaml);

    Map<String, Object> specDecorator = (Map<String, Object>) decorator.get("spec");
    Map<String, Object> definitionDecorator = (Map<String, Object>) specDecorator.get("definition");
    Object stored = definitionDecorator.get("$text");

    assertThat(stored).isInstanceOf(String.class);
    // The stored content must be loadable as an OpenAPI map, i.e. not a re-quoted scalar string.
    Object reloaded = new Yaml().load((String) stored);
    assertThat(reloaded).isInstanceOf(Map.class);
    assertThat((Map<String, Object>) reloaded).containsKey("openapi").containsKey("paths");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testReplacePlaceholdersWithResolvedDataYaml() {
    Map<String, Object> node = new HashMap<>();
    Map<String, Object> spec = new HashMap<>();
    Map<String, Object> placeholder = new HashMap<>();
    placeholder.put("$yaml", "https://example.com/config.yaml");
    spec.put("config", placeholder);
    node.put("spec", spec);

    Map<String, String> replacements = new HashMap<>();
    replacements.put("spec.config.$yaml", "key1: value1\nkey2: value2");

    PlaceholderProcessor.replacePlaceholdersWithResolvedData(node, "", replacements);

    assertThat(node.get("spec")).isInstanceOf(Map.class);
    Map<String, Object> resultSpec = (Map<String, Object>) node.get("spec");
    assertThat(resultSpec.get("config")).isInstanceOf(Map.class);
    Map<String, Object> config = (Map<String, Object>) resultSpec.get("config");
    assertThat(config).containsEntry("key1", "value1");
    assertThat(config).containsEntry("key2", "value2");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testReplacePlaceholdersWithResolvedDataJson() {
    Map<String, Object> node = new HashMap<>();
    Map<String, Object> placeholder = new HashMap<>();
    placeholder.put("$json", "https://example.com/data.json");
    node.put("data", placeholder);

    Map<String, String> replacements = new HashMap<>();
    replacements.put("data.$json", "{\"field1\": \"value1\", \"field2\": 123}");

    PlaceholderProcessor.replacePlaceholdersWithResolvedData(node, "", replacements);

    assertThat(node.get("data")).isInstanceOf(Map.class);
    Map<String, Object> data = (Map<String, Object>) node.get("data");
    assertThat(data).containsEntry("field1", "value1");
    assertThat(data).containsEntry("field2", 123);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testReplacePlaceholdersWithResolvedDataText() {
    Map<String, Object> node = new HashMap<>();
    Map<String, Object> placeholder = new HashMap<>();
    placeholder.put("$text", "https://example.com/readme.txt");
    node.put("readme", placeholder);

    Map<String, String> replacements = new HashMap<>();
    replacements.put("readme.$text", "This is the README content");

    PlaceholderProcessor.replacePlaceholdersWithResolvedData(node, "", replacements);

    assertThat(node.get("readme")).isEqualTo("This is the README content");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testReplacePlaceholdersWithInvalidJson() {
    Map<String, Object> node = new HashMap<>();
    Map<String, Object> placeholder = new HashMap<>();
    placeholder.put("$json", "https://example.com/data.json");
    node.put("data", placeholder);

    Map<String, String> replacements = new HashMap<>();
    replacements.put("data.$json", "invalid json content");

    assertThatThrownBy(() -> PlaceholderProcessor.replacePlaceholdersWithResolvedData(node, "", replacements))
        .isInstanceOf(UnexpectedException.class);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseGitInfoGitHub() {
    String url = "https://github.com/org/repo/blob/main/catalog/entity.yaml";

    PlaceholderProcessor.GitInfo gitInfo = placeholderProcessor.parseGitInfo(url);

    assertThat(gitInfo.provider).isEqualTo("github");
    assertThat(gitInfo.owner).isEqualTo("org");
    assertThat(gitInfo.repo).isEqualTo("repo");
    assertThat(gitInfo.branch).isEqualTo("main");
    assertThat(gitInfo.filePath).isEqualTo("catalog/entity.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseGitInfoGitLab() {
    String url = "https://gitlab.com/org/group/repo/-/blob/develop/src/config.yaml";

    PlaceholderProcessor.GitInfo gitInfo = placeholderProcessor.parseGitInfo(url);

    assertThat(gitInfo.provider).isEqualTo("gitlab");
    assertThat(gitInfo.repo).isEqualTo("repo");
    assertThat(gitInfo.branch).isEqualTo("blob");
    assertThat(gitInfo.filePath).isEqualTo("develop");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseGitInfoAzure() {
    String url = "https://dev.azure.com/org/project/_git/repo?version=GBmain&path=/src/file.yaml";

    PlaceholderProcessor.GitInfo gitInfo = placeholderProcessor.parseGitInfo(url);

    assertThat(gitInfo.provider).isEqualTo("azure");
    assertThat(gitInfo.repo).isEqualTo("repo");
    assertThat(gitInfo.branch).isEqualTo("main");
    assertThat(gitInfo.filePath).isEqualTo("/src/file.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseGitInfoBitbucketCloud() {
    String url = "https://bitbucket.org/org/repo/src/develop/path/to/file.yaml";

    PlaceholderProcessor.GitInfo gitInfo = placeholderProcessor.parseGitInfo(url);

    assertThat(gitInfo.provider).isEqualTo("bitbucket-cloud");
    assertThat(gitInfo.repo).isEqualTo("repo");
    assertThat(gitInfo.branch).isEqualTo("develop");
    assertThat(gitInfo.filePath).isEqualTo("path/to/file.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseGitInfoBitbucketServer() {
    String url =
        "https://bitbucket.company.com/projects/PROJ/repos/repo/browse/path/to/file.yaml?at=refs/heads/feature";

    PlaceholderProcessor.GitInfo gitInfo = placeholderProcessor.parseGitInfo(url);

    assertThat(gitInfo.provider).isEqualTo("bitbucket-server");
    assertThat(gitInfo.repo).isEqualTo("repo");
    assertThat(gitInfo.branch).isEqualTo("feature");
    assertThat(gitInfo.filePath).isEqualTo("path/to/file.yaml?at=refs/heads/feature");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseGitInfoInvalidUrl() {
    String url = "https://example.com/some/path";

    PlaceholderProcessor.GitInfo gitInfo = placeholderProcessor.parseGitInfo(url);

    assertThat(gitInfo.provider).isNull();
    assertThat(gitInfo.repo).isNull();
    assertThat(gitInfo.branch).isNull();
    assertThat(gitInfo.filePath).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseGitInfoGitHubSlashedBranchWithRefParam() {
    // Branch name contains slashes — ?ref= carries the authoritative branch name.
    String url =
        "https://github.com/CFG-ENTERPRISE/fuse-orchestrator/blob/feature/EP-94935-catalog/.harness/openapi.yaml"
        + "?ref=feature/EP-94935-catalog";

    PlaceholderProcessor.GitInfo gitInfo = placeholderProcessor.parseGitInfoWithRef(url);

    assertThat(gitInfo.provider).isEqualTo("github");
    assertThat(gitInfo.owner).isEqualTo("CFG-ENTERPRISE");
    assertThat(gitInfo.repo).isEqualTo("fuse-orchestrator");
    assertThat(gitInfo.branch).isEqualTo("feature/EP-94935-catalog");
    assertThat(gitInfo.filePath).isEqualTo(".harness/openapi.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseGitInfoGitHubSimpleBranchNoRefParam() {
    // No ?ref= — legacy single-segment branch behaviour must be preserved.
    String url = "https://github.com/org/repo/blob/main/path/to/file.yaml";

    PlaceholderProcessor.GitInfo gitInfo = placeholderProcessor.parseGitInfo(url);

    assertThat(gitInfo.provider).isEqualTo("github");
    assertThat(gitInfo.owner).isEqualTo("org");
    assertThat(gitInfo.repo).isEqualTo("repo");
    assertThat(gitInfo.branch).isEqualTo("main");
    assertThat(gitInfo.filePath).isEqualTo("path/to/file.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseGitInfoGitLabSlashedBranchWithRefParam() {
    String url = "https://gitlab.com/org/group/repo/-/blob/feature/my-ticket/file.yaml?ref=feature/my-ticket";

    PlaceholderProcessor.GitInfo gitInfo = placeholderProcessor.parseGitInfoWithRef(url);

    assertThat(gitInfo.provider).isEqualTo("gitlab");
    assertThat(gitInfo.repo).isEqualTo("repo");
    assertThat(gitInfo.branch).isEqualTo("feature/my-ticket");
    assertThat(gitInfo.filePath).isEqualTo("file.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseGitInfoBitbucketCloudSlashedBranchWithAtParam() {
    String url = "https://bitbucket.org/org/repo/src/feature/my-ticket/path/to/file.yaml?at=feature/my-ticket";

    PlaceholderProcessor.GitInfo gitInfo = placeholderProcessor.parseGitInfoWithRef(url);

    assertThat(gitInfo.provider).isEqualTo("bitbucket-cloud");
    assertThat(gitInfo.repo).isEqualTo("repo");
    assertThat(gitInfo.branch).isEqualTo("feature/my-ticket");
    assertThat(gitInfo.filePath).isEqualTo("path/to/file.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseGitInfoBitbucketServerWithEncodedAtParam() {
    String url = "https://bitbucket.company.com/projects/PROJ/repos/repo/browse/path/to/file.yaml"
        + "?at=refs%2Fheads%2Ffeature%2Fmy-branch";

    PlaceholderProcessor.GitInfo gitInfo = placeholderProcessor.parseGitInfoWithRef(url);

    assertThat(gitInfo.provider).isEqualTo("bitbucket-server");
    assertThat(gitInfo.repo).isEqualTo("repo");
    assertThat(gitInfo.branch).isEqualTo("feature/my-branch");
    assertThat(gitInfo.filePath).isEqualTo("path/to/file.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseGitInfoWithRefAzureDevOps() {
    String url = "https://dev.azure.com/org/project/_git/repo?version=GBfeature/my-branch&path=/src/openapi.yaml";

    PlaceholderProcessor.GitInfo gitInfo = placeholderProcessor.parseGitInfoWithRef(url);

    assertThat(gitInfo.provider).isEqualTo("azure");
    assertThat(gitInfo.repo).isEqualTo("repo");
    assertThat(gitInfo.branch).isEqualTo("feature/my-branch");
    assertThat(gitInfo.filePath).isEqualTo("src/openapi.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseGitInfoHarnessCodeSlashedBranchWithRefParam() {
    String url = "https://harness0.harness.io/ng/account/abc123/module/code/orgs/PROD/projects/Harness_Commons/repos/"
        + "harness-core"
        + "/files/refs/heads/release/ng-manager_1.160.0/~/path/to/openapi.yaml?ref=release/ng-manager_1.160.0";

    PlaceholderProcessor.GitInfo gitInfo = placeholderProcessor.parseGitInfoWithRef(url);

    assertThat(gitInfo.provider).isEqualTo("harness-code");
    assertThat(gitInfo.repo).isEqualTo("orgs/PROD/projects/Harness_Commons/repos/harness-core");
    assertThat(gitInfo.branch).isEqualTo("release/ng-manager_1.160.0");
    assertThat(gitInfo.filePath).isEqualTo("path/to/openapi.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseGitInfoHarnessCodeSimpleBranch() {
    String url = "https://app.harness.io/ng/account/abc123/module/code/orgs/default/projects/myproj/repos/my-repo"
        + "/files/refs/heads/main/~/src/openapi.yaml";

    PlaceholderProcessor.GitInfo gitInfo = placeholderProcessor.parseGitInfoWithRef(url);

    assertThat(gitInfo.provider).isEqualTo("harness-code");
    assertThat(gitInfo.repo).isEqualTo("orgs/default/projects/myproj/repos/my-repo");
    assertThat(gitInfo.branch).isEqualTo("main");
    assertThat(gitInfo.filePath).isEqualTo("src/openapi.yaml");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testParseGitInfoHarnessCodeRefsHeadsBranchNoRefParam() {
    String url =
        "https://harness0.harness.io/ng/account/l7B_kbSEQD2wjrM7PShm5w/module/code/orgs/PROD/projects/Harness_Commons"
        + "/repos/harness-core/files/refs/heads/release/ng-manager_1.160.0"
        + "/~/122-ng-authentication-settings/src/test/java/io/harness/ng/authenticationsettings/"
        + "AuthenticationSettingTestRule.java";

    PlaceholderProcessor.GitInfo gitInfo = placeholderProcessor.parseGitInfoWithRef(url);

    assertThat(gitInfo.provider).isEqualTo("harness-code");
    assertThat(gitInfo.repo).isEqualTo("orgs/PROD/projects/Harness_Commons/repos/harness-core");
    assertThat(gitInfo.branch).isEqualTo("release/ng-manager_1.160.0");
    assertThat(gitInfo.filePath)
        .isEqualTo("122-ng-authentication-settings/src/test/java/io/harness/ng/authenticationsettings/"
            + "AuthenticationSettingTestRule.java");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testExtractRefQueryParam() {
    assertThat(
        PlaceholderProcessor.extractRefQueryParam("https://github.com/org/repo/blob/main/file.yaml?ref=feature/EP-123"))
        .isEqualTo("feature/EP-123");
    assertThat(PlaceholderProcessor.extractRefQueryParam("https://github.com/org/repo/blob/main/file.yaml")).isNull();
    assertThat(PlaceholderProcessor.extractRefQueryParam(
                   "https://github.com/org/repo/blob/main/file.yaml?foo=bar&ref=feature/EP-123&baz=1"))
        .isEqualTo("feature/EP-123");
    assertThat(PlaceholderProcessor.extractRefQueryParam(null)).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testGetPlaceholdersDecoratorWithNoPlaceholders() {
    String unresolvedYaml = "spec:\n  name: test\n  version: 1.0";
    String resolvedYaml = "spec:\n  name: test\n  version: 1.0";

    Map<String, Object> decorator = placeholderProcessor.getPlaceholdersDecorator(unresolvedYaml, resolvedYaml);

    assertThat(decorator).isEmpty();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testReplacePlaceholdersWithNonMapNode() {
    String node = "not a map";
    Map<String, String> replacements = new HashMap<>();
    replacements.put("test", "value");

    // Should not throw exception and should not modify the node
    PlaceholderProcessor.replacePlaceholdersWithResolvedData(node, "", replacements);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testProcessWithMissingConnector() {
    InlineCatalogEntity catalogEntity = new InlineCatalogEntity();
    String yaml = "apiVersion: v1\nspec:\n  readme:\n    $text: ./README.md";
    catalogEntity.setYaml(yaml);
    catalogEntity.setAccountIdentifier("test-account");
    catalogEntity.setSourceLocation("");

    when(integrationEntityRepository.findByAccountIdentifierAndAdditionalIndexer(anyString(), any()))
        .thenReturn(Arrays.asList());

    String result = placeholderProcessor.process(catalogEntity);

    assertThat(result).contains("$text: ./README.md");
  }

  private ConnectorInfoDTO githubConnector(GitConnectionType connectionType, String url) {
    return ConnectorInfoDTO.builder()
        .connectorConfig(GithubConnectorDTO.builder().connectionType(connectionType).url(url).build())
        .build();
  }

  private PlaceholderProcessor.GitInfo gitHubGitInfo(String url) {
    return placeholderProcessor.parseGitInfo(url);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testResolveRepoNameAccountHostOnlyQualifiesWithOwner() {
    // Host-only ACCOUNT connector: owner is lost from the connector URL, so it must be taken from
    // the placeholder URL to allow fetching any repo the connector's credentials can access.
    ConnectorInfoDTO connector = githubConnector(GitConnectionType.ACCOUNT, "https://github.enterprise.com/");
    PlaceholderProcessor.GitInfo gitInfo =
        gitHubGitInfo("https://github.enterprise.com/MY-ORG/my-repo/blob/main/apis/spec.yaml");

    assertThat(placeholderProcessor.resolveRepoName(connector, gitInfo)).isEqualTo("MY-ORG/my-repo");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testResolveRepoNameAccountOrgAnchoredKeepsBareRepo() {
    // Org-anchored ACCOUNT connector already carries the owner in its URL, so qualifying would
    // produce a 3-segment path. Keep the bare repo (existing behaviour).
    ConnectorInfoDTO connector = githubConnector(GitConnectionType.ACCOUNT, "https://github.enterprise.com/MY-ORG");
    PlaceholderProcessor.GitInfo gitInfo =
        gitHubGitInfo("https://github.enterprise.com/MY-ORG/my-repo/blob/main/apis/spec.yaml");

    assertThat(placeholderProcessor.resolveRepoName(connector, gitInfo)).isEqualTo("my-repo");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testResolveRepoNameRepoConnectorKeepsBareRepo() {
    ConnectorInfoDTO connector =
        githubConnector(GitConnectionType.REPO, "https://github.enterprise.com/MY-ORG/my-repo");
    PlaceholderProcessor.GitInfo gitInfo =
        gitHubGitInfo("https://github.enterprise.com/MY-ORG/my-repo/blob/main/apis/spec.yaml");

    assertThat(placeholderProcessor.resolveRepoName(connector, gitInfo)).isEqualTo("my-repo");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testResolveRepoNameNullOwnerKeepsBareRepo() {
    ConnectorInfoDTO connector = githubConnector(GitConnectionType.ACCOUNT, "https://github.enterprise.com/");
    PlaceholderProcessor.GitInfo gitInfo = new PlaceholderProcessor.GitInfo();
    gitInfo.repo = "my-repo";

    assertThat(placeholderProcessor.resolveRepoName(connector, gitInfo)).isEqualTo("my-repo");
  }
}
