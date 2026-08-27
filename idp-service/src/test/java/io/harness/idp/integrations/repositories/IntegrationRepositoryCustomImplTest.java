/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.repositories;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.integrations.entities.IntegrationEntity.SubType.GITHUB_DIRECT;
import static io.harness.idp.integrations.entities.IntegrationEntity.SubType.GITHUB_ENTERPRISE;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_AZURE_INTEGRATION_ORGANIZATION1;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_AZURE_INTEGRATION_ORGANIZATION2;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_AZURE_INTEGRATION_ORGANIZATION3;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_AZURE_INTEGRATION_ORGANIZATION4;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_GITHUB_INTEGRATION_DIRECT_HOST;
import static io.harness.idp.integrations.helpers.IntegrationsTestHelper.TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST;
import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.CategoryTest;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.git.AzureIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketCloudIntegrationEntity;
import io.harness.idp.integrations.entities.git.BitbucketServerIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitIntegrationEntity;
import io.harness.idp.integrations.entities.git.GithubIntegrationEntity;
import io.harness.idp.integrations.entities.git.GitlabIntegrationEntity;
import io.harness.idp.integrations.helpers.IntegrationsTestHelper;
import io.harness.rule.Owner;
import io.harness.utils.PageUtils;

import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IntegrationRepositoryCustomImplTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_GIT_INTEGRATION_IDENTIFIER_FOR_SEARCH = "idp_testConnectorSearch123";
  AutoCloseable openMocks;

  @InjectMocks private IntegrationRepositoryCustomImpl integrationRepositoryCustom;

  @Mock private MongoTemplate mongoTemplate;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testFindAll() {
    GithubIntegrationEntity githubIntegrationEntityToken = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_DIRECT, TEST_GITHUB_INTEGRATION_DIRECT_HOST, GitIntegrationEntity.AuthMode.TOKEN);
    GithubIntegrationEntity githubIntegrationEntityGithubApp = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_DIRECT, TEST_GITHUB_INTEGRATION_DIRECT_HOST, GitIntegrationEntity.AuthMode.GITHUB_APP);
    GithubIntegrationEntity githubIntegrationEntityEnterpriseGithubApp = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_ENTERPRISE, TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST, GitIntegrationEntity.AuthMode.GITHUB_APP);
    GithubIntegrationEntity githubIntegrationEntityEnterpriseToken = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_ENTERPRISE, TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST, GitIntegrationEntity.AuthMode.TOKEN);
    GitlabIntegrationEntity gitlabIntegrationEntity = IntegrationsTestHelper.gitlabIntegrationEntity();
    AzureIntegrationEntity azureIntegrationEntityOrganization1 =
        IntegrationsTestHelper.azureIntegrationEntity(TEST_AZURE_INTEGRATION_ORGANIZATION1);
    AzureIntegrationEntity azureIntegrationEntityOrganization2 =
        IntegrationsTestHelper.azureIntegrationEntity(TEST_AZURE_INTEGRATION_ORGANIZATION2);
    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity =
        IntegrationsTestHelper.bitbucketCloudIntegrationEntity();
    BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity =
        IntegrationsTestHelper.bitbucketServerIntegrationEntity();

    List<IntegrationEntity> entities = new ArrayList<>();
    entities.add(githubIntegrationEntityToken);
    entities.add(githubIntegrationEntityGithubApp);
    entities.add(githubIntegrationEntityEnterpriseGithubApp);
    entities.add(githubIntegrationEntityEnterpriseToken);
    entities.add(gitlabIntegrationEntity);
    entities.add(azureIntegrationEntityOrganization1);
    entities.add(azureIntegrationEntityOrganization2);
    entities.add(bitbucketCloudIntegrationEntity);
    entities.add(bitbucketServerIntegrationEntity);

    Criteria criteria = buildCriteria(null);
    Pageable pageRequest = pageRequest(0, 10, null);
    Query query = new Query(criteria).with(pageRequest);

    when(mongoTemplate.find(query, IntegrationEntity.class)).thenReturn(entities);
    when(mongoTemplate.count(Query.of(query).limit(-1).skip(-1), IntegrationEntity.class)).thenReturn(9L);

    Page<IntegrationEntity> integrationEntities = integrationRepositoryCustom.findAll(criteria, pageRequest);

    assertEquals(1, integrationEntities.getTotalPages());
    assertEquals(0, integrationEntities.getNumber());
    assertEquals(9, integrationEntities.getNumberOfElements());
    assertEquals(10, integrationEntities.getSize());
    assertEquals(9L, integrationEntities.getTotalElements());
    assertEquals(entities, integrationEntities.getContent());

    AzureIntegrationEntity azureIntegrationEntityOrganization3 =
        IntegrationsTestHelper.azureIntegrationEntity(TEST_AZURE_INTEGRATION_ORGANIZATION3);
    entities.add(azureIntegrationEntityOrganization3);

    when(mongoTemplate.find(query, IntegrationEntity.class)).thenReturn(entities);
    when(mongoTemplate.count(Query.of(query).limit(-1).skip(-1), IntegrationEntity.class)).thenReturn(10L);

    integrationEntities = integrationRepositoryCustom.findAll(criteria, pageRequest);

    assertEquals(1, integrationEntities.getTotalPages());
    assertEquals(0, integrationEntities.getNumber());
    assertEquals(10, integrationEntities.getNumberOfElements());
    assertEquals(10, integrationEntities.getSize());
    assertEquals(10L, integrationEntities.getTotalElements());
    assertEquals(entities, integrationEntities.getContent());

    AzureIntegrationEntity azureIntegrationEntityOrganization4 =
        IntegrationsTestHelper.azureIntegrationEntity(TEST_AZURE_INTEGRATION_ORGANIZATION4);
    entities.add(azureIntegrationEntityOrganization4);

    when(mongoTemplate.find(query, IntegrationEntity.class)).thenReturn(entities);
    when(mongoTemplate.count(Query.of(query).limit(-1).skip(-1), IntegrationEntity.class)).thenReturn(11L);

    integrationEntities = integrationRepositoryCustom.findAll(criteria, pageRequest);

    assertEquals(2, integrationEntities.getTotalPages());
    assertEquals(0, integrationEntities.getNumber());
    assertEquals(11, integrationEntities.getNumberOfElements());
    assertEquals(10, integrationEntities.getSize());
    assertEquals(11L, integrationEntities.getTotalElements());
    assertEquals(entities, integrationEntities.getContent());

    pageRequest = pageRequest(1, 10, null);
    query = new Query(criteria).with(pageRequest);

    when(mongoTemplate.find(query, IntegrationEntity.class)).thenReturn(List.of(azureIntegrationEntityOrganization4));
    when(mongoTemplate.count(Query.of(query).limit(-1).skip(-1), IntegrationEntity.class)).thenReturn(11L);

    integrationEntities = integrationRepositoryCustom.findAll(criteria, pageRequest);

    assertEquals(2, integrationEntities.getTotalPages());
    assertEquals(1, integrationEntities.getNumber());
    assertEquals(1, integrationEntities.getNumberOfElements());
    assertEquals(10, integrationEntities.getSize());
    assertEquals(11L, integrationEntities.getTotalElements());
    assertEquals(List.of(azureIntegrationEntityOrganization4), integrationEntities.getContent());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testFindAllSearch() {
    GithubIntegrationEntity githubIntegrationEntityToken =
        IntegrationsTestHelper.githubIntegrationEntity(TEST_GIT_INTEGRATION_IDENTIFIER_FOR_SEARCH, GITHUB_DIRECT,
            TEST_GITHUB_INTEGRATION_DIRECT_HOST, GitIntegrationEntity.AuthMode.TOKEN);
    GithubIntegrationEntity githubIntegrationEntityEnterpriseGithubApp =
        IntegrationsTestHelper.githubIntegrationEntity(TEST_GIT_INTEGRATION_IDENTIFIER_FOR_SEARCH, GITHUB_ENTERPRISE,
            TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST, GitIntegrationEntity.AuthMode.GITHUB_APP);
    GitlabIntegrationEntity gitlabIntegrationEntity =
        IntegrationsTestHelper.gitlabIntegrationEntity(TEST_GIT_INTEGRATION_IDENTIFIER_FOR_SEARCH);
    AzureIntegrationEntity azureIntegrationEntityOrganization2 = IntegrationsTestHelper.azureIntegrationEntity(
        TEST_GIT_INTEGRATION_IDENTIFIER_FOR_SEARCH, TEST_AZURE_INTEGRATION_ORGANIZATION2);
    BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity =
        IntegrationsTestHelper.bitbucketServerIntegrationEntity(TEST_GIT_INTEGRATION_IDENTIFIER_FOR_SEARCH);

    List<IntegrationEntity> entities = new ArrayList<>();
    entities.add(githubIntegrationEntityToken);
    entities.add(githubIntegrationEntityEnterpriseGithubApp);
    entities.add(gitlabIntegrationEntity);
    entities.add(azureIntegrationEntityOrganization2);
    entities.add(bitbucketServerIntegrationEntity);

    Criteria criteria = buildCriteria("search");
    Pageable pageRequest = pageRequest(0, 7, null);
    Query query = new Query(criteria).with(pageRequest);

    when(mongoTemplate.find(query, IntegrationEntity.class)).thenReturn(entities);
    when(mongoTemplate.count(Query.of(query).limit(-1).skip(-1), IntegrationEntity.class)).thenReturn(5L);

    Page<IntegrationEntity> integrationEntities = integrationRepositoryCustom.findAll(criteria, pageRequest);

    assertEquals(1, integrationEntities.getTotalPages());
    assertEquals(0, integrationEntities.getNumber());
    assertEquals(5, integrationEntities.getNumberOfElements());
    assertEquals(7, integrationEntities.getSize());
    assertEquals(5L, integrationEntities.getTotalElements());
    assertEquals(entities, integrationEntities.getContent());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testFindAllSort() {
    GithubIntegrationEntity githubIntegrationEntityToken = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_DIRECT, TEST_GITHUB_INTEGRATION_DIRECT_HOST, GitIntegrationEntity.AuthMode.TOKEN);
    GithubIntegrationEntity githubIntegrationEntityGithubApp = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_DIRECT, TEST_GITHUB_INTEGRATION_DIRECT_HOST, GitIntegrationEntity.AuthMode.GITHUB_APP);
    GithubIntegrationEntity githubIntegrationEntityEnterpriseGithubApp = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_ENTERPRISE, TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST, GitIntegrationEntity.AuthMode.GITHUB_APP);
    GithubIntegrationEntity githubIntegrationEntityEnterpriseToken = IntegrationsTestHelper.githubIntegrationEntity(
        GITHUB_ENTERPRISE, TEST_GITHUB_INTEGRATION_ENTERPRISE_HOST, GitIntegrationEntity.AuthMode.TOKEN);
    GitlabIntegrationEntity gitlabIntegrationEntity = IntegrationsTestHelper.gitlabIntegrationEntity();
    AzureIntegrationEntity azureIntegrationEntityOrganization1 =
        IntegrationsTestHelper.azureIntegrationEntity(TEST_AZURE_INTEGRATION_ORGANIZATION1);
    AzureIntegrationEntity azureIntegrationEntityOrganization2 =
        IntegrationsTestHelper.azureIntegrationEntity(TEST_AZURE_INTEGRATION_ORGANIZATION2);
    BitbucketCloudIntegrationEntity bitbucketCloudIntegrationEntity =
        IntegrationsTestHelper.bitbucketCloudIntegrationEntity();
    BitbucketServerIntegrationEntity bitbucketServerIntegrationEntity =
        IntegrationsTestHelper.bitbucketServerIntegrationEntity();

    List<IntegrationEntity> entities = new ArrayList<>();
    entities.add(bitbucketServerIntegrationEntity);
    entities.add(bitbucketCloudIntegrationEntity);
    entities.add(azureIntegrationEntityOrganization1);
    entities.add(azureIntegrationEntityOrganization2);
    entities.add(gitlabIntegrationEntity);
    entities.add(githubIntegrationEntityToken);
    entities.add(githubIntegrationEntityGithubApp);
    entities.add(githubIntegrationEntityEnterpriseGithubApp);
    entities.add(githubIntegrationEntityEnterpriseToken);

    Criteria criteria = buildCriteria(null);
    Pageable pageRequest = pageRequest(0, 10, "lastUpdatedAt,DESC");
    Query query = new Query(criteria).with(pageRequest);

    when(mongoTemplate.find(query, IntegrationEntity.class)).thenReturn(entities);
    when(mongoTemplate.count(Query.of(query).limit(-1).skip(-1), IntegrationEntity.class)).thenReturn(9L);

    Page<IntegrationEntity> integrationEntities = integrationRepositoryCustom.findAll(criteria, pageRequest);

    assertEquals(1, integrationEntities.getTotalPages());
    assertEquals(0, integrationEntities.getNumber());
    assertEquals(9, integrationEntities.getNumberOfElements());
    assertEquals(10, integrationEntities.getSize());
    assertEquals(9L, integrationEntities.getTotalElements());
    assertEquals(entities, integrationEntities.getContent());
  }

  private Criteria buildCriteria(String searchTerm) {
    Criteria criteria = new Criteria();
    criteria.and(IntegrationEntity.IntegrationsKeys.accountIdentifier).is(TEST_ACCOUNT_IDENTIFIER);
    criteria.and(IntegrationEntity.IntegrationsKeys.integration).is(IntegrationEntity.Integration.GIT);

    if (isNotEmpty(searchTerm)) {
      criteria.andOperator(buildSearchCriteria(searchTerm));
    }
    return criteria;
  }

  private Criteria buildSearchCriteria(String searchTerm) {
    return new Criteria().orOperator(where(IntegrationEntity.IntegrationsKeys.identifier)
                                         .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
  }

  private Pageable pageRequest(int pageIndex, int pageLimit, String sort) {
    return isEmpty(sort) ? PageRequest.of(pageIndex, pageLimit,
                               Sort.by(Sort.Direction.DESC, IntegrationEntity.IntegrationsKeys.lastUpdatedAt))
                         : PageUtils.getPageRequest(pageIndex, pageLimit, List.of(sort));
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
