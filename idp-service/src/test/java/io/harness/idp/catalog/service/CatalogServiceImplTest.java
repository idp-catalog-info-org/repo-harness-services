/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.service;

import static io.harness.favorites.ResourceType.IDPENTITY;
import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.idp.catalog.utils.Constants.RESOURCE_KIND;
import static io.harness.idp.catalog.utils.Constants.USER_KIND;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.KOTA_KARTHIK;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.eventsframework.schemas.usermembership.UserMembershipDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.idp.catalog.beans.GetEntitiesDTO;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.cache.ScopeTopology;
import io.harness.idp.catalog.entities.BuiltInKindEntity;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.GitReferencedCatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.entities.KindEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.helpers.HarnessToIDPHelper;
import io.harness.idp.catalog.helpers.IDPGitXHelper;
import io.harness.idp.catalog.helpers.IDPToHarnessHelper;
import io.harness.idp.catalog.helpers.KindServiceHelper;
import io.harness.idp.catalog.helpers.STOHelper;
import io.harness.idp.catalog.opa.IdpEntityOpaService;
import io.harness.idp.catalog.processor.RelationsProcessor;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.repositories.CatalogEntityVersionRepository;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.scorecards.beans.ScorecardAndChecks;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.service.ScorecardService;
import io.harness.idp.scorecard.scores.entity.ScoreEntity;
import io.harness.idp.scorecard.scores.service.ScoreService;
import io.harness.lock.redis.RedisAcquiredLock;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.organization.remote.OrganizationClient;
import io.harness.outbox.api.OutboxService;
import io.harness.project.remote.ProjectClient;
import io.harness.rule.Owner;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.CheckStatus;
import io.harness.spec.server.idp.v1.model.EntitiesMigrateRequest;
import io.harness.spec.server.idp.v1.model.EntityConvertResponse;
import io.harness.spec.server.idp.v1.model.EntityCreateRequest;
import io.harness.spec.server.idp.v1.model.EntityFiltersResponse;
import io.harness.spec.server.idp.v1.model.EntityKindsResponse;
import io.harness.spec.server.idp.v1.model.EntityRequest;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.GitMetadataUpdateRequest;
import io.harness.springdata.TransactionHelper;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class CatalogServiceImplTest extends CategoryTest {
  public static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  private static final String TEST_USER_IDENTIFIER = "testUser123";
  private static final String TEST_EMAIL = "test@test.com";
  private static final String TEST_USER_GROUP_IDENTIFIER = "testUserGroup123";
  private Call<Object> call;
  static final String TEST_IDENTIFIER = "test1/test2/test3";
  static Gson gson = new Gson();

  AutoCloseable openMocks;
  @InjectMocks CatalogServiceImpl catalogService;
  @Mock IDPToHarnessHelper idpToHarnessHelper;
  @Mock ScopeInfoClient scopeInfoClient;
  @Mock CatalogEntityRepository catalogEntityRepository;
  @Mock CatalogEntityVersionRepository catalogEntityVersionRepository;
  @Mock BackstageResourceClient backstageResourceClient;
  @Mock TransactionHelper transactionHelper;
  @Mock HarnessToIDPHelper harnessToIDPHelper;
  @Mock NamespaceService namespaceService;
  @Mock IdpCommonService idpCommonService;
  @Mock IdpEntityOpaService idpEntityOpaService;
  @Mock CatalogServiceHelper catalogServiceHelper;
  @Mock RelationsProcessor relationsProcessor;
  @Mock OutboxService outboxService;
  @Mock OrganizationClient organizationClient;
  @Mock ProjectClient projectClient;
  @Mock ScorecardService scorecardService;
  @Mock ScoreService scoreService;
  @Mock IDPGitXHelper idpGitXHelper;
  @Mock STOHelper stoHelper;
  @Mock ResourceLocker resourceLocker;
  @Mock KindServiceHelper kindServiceHelper;
  @Mock ScorecardScoreHelper scorecardScoreHelper;
  @Mock CatalogScopeResolver catalogScopeResolver;
  @Mock CatalogServiceV2Impl catalogServiceV2Impl;
  @Mock CatalogOrgProjectService catalogOrgProjectService;
  @Mock HarnessCodeConnectorUtils harnessCodeConnectorUtils;
  @Mock HarnessCodeRepoConfig harnessCodeRepoConfig;

  private final ExecutorService entitiesGroupExecutor = MoreExecutors.newDirectExecutorService();

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    catalogService.entitiesGroupExecutor = entitiesGroupExecutor;

    call = mock(Call.class);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testBackgroundMigrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities() {
    catalogService.backgroundMigrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(TEST_ACCOUNT_IDENTIFIER);
    verify(idpToHarnessHelper, times(1))
        .validateAndMigrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleUserBasedOnActionUpdateCase() {
    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });
    when(idpCommonService.idpV2Enabled(any())).thenReturn(true);
    doNothing().when(harnessToIDPHelper).harnessToIdpSync(any(), any(), any());
    when(idpToHarnessHelper.updateUser(any(), any(), any())).thenReturn(InlineCatalogEntity.builder().build());
    UserMembershipDTO userMembershipDTO = UserMembershipDTO.newBuilder().build();
    catalogService.handleUserBasedOnAction(TEST_ACCOUNT_IDENTIFIER, userMembershipDTO, "update");
    verify(idpToHarnessHelper, times(1)).updateUser(TEST_ACCOUNT_IDENTIFIER, userMembershipDTO, false);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleUserBasedOnActionCreateCase() {
    UserMembershipDTO userMembershipDTO = UserMembershipDTO.newBuilder().build();
    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });
    when(idpCommonService.idpV2Enabled(any())).thenReturn(true);
    doNothing().when(harnessToIDPHelper).harnessToIdpSync(any(), any(), any());
    when(idpToHarnessHelper.updateUser(TEST_ACCOUNT_IDENTIFIER, userMembershipDTO, true))
        .thenReturn(InlineCatalogEntity.builder().build());
    catalogService.handleUserBasedOnAction(TEST_ACCOUNT_IDENTIFIER, userMembershipDTO, "create");
    verify(idpToHarnessHelper, times(1)).updateUser(TEST_ACCOUNT_IDENTIFIER, userMembershipDTO, true);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleUserBasedOnActionDeleteCase() throws IOException {
    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });
    when(idpCommonService.idpV2Enabled(any())).thenReturn(true);
    doNothing().when(harnessToIDPHelper).harnessToIdpSync(any(), any(), any());
    when(namespaceService.getAccountIdpStatus(any())).thenReturn(true);
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfoCall);
    doNothing().when(harnessToIDPHelper).harnessToIdpSync(any(), any(), any());

    UserMembershipDTO userMembershipDTO = UserMembershipDTO.newBuilder().setUserId(TEST_USER_IDENTIFIER).build();
    when(catalogEntityRepository.findUserBasedOnAccountIdAndUUID(TEST_ACCOUNT_IDENTIFIER, TEST_USER_IDENTIFIER))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().build()));
    catalogService.handleUserBasedOnAction(TEST_ACCOUNT_IDENTIFIER, userMembershipDTO, "delete");
    verify(catalogEntityRepository, times(1)).delete(any());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleUserGroupBasedOnActionUpdateCase() {
    doNothing().when(idpToHarnessHelper).updateUserGroup(TEST_ACCOUNT_IDENTIFIER, TEST_USER_GROUP_IDENTIFIER, "update");
    catalogService.handleUserGroupBasedOnAction(TEST_ACCOUNT_IDENTIFIER, TEST_USER_GROUP_IDENTIFIER, "update");
    verify(idpToHarnessHelper, times(1)).updateUserGroup(TEST_ACCOUNT_IDENTIFIER, TEST_USER_GROUP_IDENTIFIER, "update");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleUserGroupBasedOnActionCreateCase() {
    doNothing().when(idpToHarnessHelper).updateUserGroup(TEST_ACCOUNT_IDENTIFIER, TEST_USER_GROUP_IDENTIFIER, "create");
    catalogService.handleUserGroupBasedOnAction(TEST_ACCOUNT_IDENTIFIER, TEST_USER_GROUP_IDENTIFIER, "create");
    verify(idpToHarnessHelper, times(1)).updateUserGroup(TEST_ACCOUNT_IDENTIFIER, TEST_USER_GROUP_IDENTIFIER, "create");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testHandleUserGroupBasedOnActionDeleteCase() throws IOException {
    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    when(idpCommonService.idpV2Enabled(any())).thenReturn(true);
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build();
    when(catalogScopeResolver.resolveSingleScopeInfo(TEST_ACCOUNT_IDENTIFIER, "account")).thenReturn(scopeInfo);

    doNothing()
        .when(catalogEntityRepository)
        .deleteByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "group", TEST_USER_GROUP_IDENTIFIER);
    when(idpToHarnessHelper.removeRelationsForUsers(any(), any())).thenReturn(new ArrayList<>());
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(any(), any(), any()))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().build()));
    doNothing().when(harnessToIDPHelper).harnessToIdpSync(any(), any(), any());
    catalogService.handleUserGroupBasedOnAction(TEST_ACCOUNT_IDENTIFIER, TEST_USER_GROUP_IDENTIFIER, "delete");
    verify(catalogEntityRepository, times(1)).delete(any());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testSyncByType() throws IOException {
    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build();
    when(catalogScopeResolver.resolveSingleScopeInfo(TEST_ACCOUNT_IDENTIFIER, "account")).thenReturn(scopeInfo);

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "test2", "test3"))
        .thenReturn(Optional.of(InlineCatalogEntity.builder().yaml("yaml").build()));
    when(idpToHarnessHelper.getInlineEntityForApiOrComponentOrResourceOrTemplate(any(), any(), any(), any(), any()))
        .thenReturn(InlineCatalogEntity.builder().yaml("yaml1").build());

    Map<String, Object> apiResponse = backstageCatalogEntitiesApiResponse();
    Response<Object> response = Response.success(apiResponse);
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntityByName(any(), any())).thenReturn(call);
    assertTrue(catalogService.syncInSynchronousMode(TEST_ACCOUNT_IDENTIFIER, TEST_IDENTIFIER, "update"));

    assertTrue(catalogService.syncInSynchronousMode(TEST_ACCOUNT_IDENTIFIER, TEST_IDENTIFIER, "delete"));
    doNothing()
        .when(catalogEntityRepository)
        .deleteByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "test2", "test3");
    verify(catalogEntityRepository, times(1))
        .deleteByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, "test2", "test3");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntitiesKinds() {
    when(catalogEntityRepository.findAllByAccountIdentifierAndReturnProjectedFields(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(List.of(InlineCatalogEntity.builder().kind(COMPONENT_KIND).build(),
            InlineCatalogEntity.builder().kind(COMPONENT_KIND).build(),
            InlineCatalogEntity.builder().kind(WORKFLOW_KIND).build()));
    KindEntity componentKind = BuiltInKindEntity.builder().identifier(COMPONENT_KIND).build();
    KindEntity workflowKind = BuiltInKindEntity.builder().identifier(WORKFLOW_KIND).build();
    when(kindServiceHelper.findByAccountIdentifierIn(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(List.of(componentKind, workflowKind));
    List<EntityKindsResponse> entityKindsResponses =
        catalogService.getEntitiesKinds(TEST_ACCOUNT_IDENTIFIER, null, null);
    assertThat(entityKindsResponses).isNotEmpty();
    assertThat(entityKindsResponses.size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntitiesFilters() throws IOException {
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfoCall);

    when(catalogEntityRepository.findAllByParentUniqueIdAndKindIn(
             TEST_ACCOUNT_IDENTIFIER, List.of("api", "component", "resource")))
        .thenReturn(List.of(InlineCatalogEntity.builder()
                                .kind(COMPONENT_KIND)
                                .type("service")
                                .owner("owner")
                                .tags(List.of("tags1"))
                                .spec(Map.of("lifecycle", "test"))
                                .build(),
            InlineCatalogEntity.builder()
                .kind(RESOURCE_KIND)
                .type("db")
                .owner("owner")
                .tags(List.of("tags2"))
                .spec(Map.of("lifecycle", "qa"))
                .build(),
            InlineCatalogEntity.builder()
                .kind(WORKFLOW_KIND)
                .type("provision db")
                .owner("owner")
                .tags(List.of("tags3"))
                .spec(Map.of("lifecycle", "dev"))
                .build()));

    when(catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(TEST_ACCOUNT_IDENTIFIER, "account", null))
        .thenReturn(Pair.of(Collections.singletonList(ScopeInfo.builder().uniqueId(TEST_ACCOUNT_IDENTIFIER).build()),
            Collections.emptyMap()));

    List<EntityFiltersResponse> entityFiltersResponses =
        catalogService.getEntitiesFilters(TEST_ACCOUNT_IDENTIFIER, null, null, null);
    assertThat(entityFiltersResponses).isNotEmpty();
    assertThat(entityFiltersResponses.size()).isEqualTo(5);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testConvertEntityBackstageToHarness() {
    String yaml = "apiVersion: backstage.io/v1alpha1\n"
        + "kind: API\n"
        + "metadata:\n"
        + "  name: hello-world\n"
        + "  description: Hello World example for gRPC\n"
        + "spec:\n"
        + "  type: grpc\n"
        + "  lifecycle: deprecated\n"
        + "  owner: team-c\n"
        + "  definition: |\n"
        + "    // Copyright 2015 gRPC authors.\n"
        + "    //\n"
        + "    // Licensed under the Apache License, Version 2.0 (the \"License\");\n"
        + "    // you may not use this file except in compliance with the License.\n"
        + "    // You may obtain a copy of the License at\n"
        + "    //\n"
        + "    //     http://www.apache.org/licenses/LICENSE-2.0\n"
        + "    //\n"
        + "    // Unless required by applicable law or agreed to in writing, software\n"
        + "    // distributed under the License is distributed on an \"AS IS\" BASIS,\n"
        + "    // WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
        + "    // See the License for the specific language governing permissions and\n"
        + "    // limitations under the License.\n"
        + "\n"
        + "    syntax = \"proto3\";\n"
        + "\n"
        + "    option java_multiple_files = true;\n"
        + "    option java_package = \"io.grpc.examples.helloworld\";\n"
        + "    option java_outer_classname = \"HelloWorldProto\";\n"
        + "    option objc_class_prefix = \"HLW\";\n"
        + "\n"
        + "    package helloworld;\n"
        + "\n"
        + "    // The greeting service definition.\n"
        + "    service Greeter {\n"
        + "      // Sends a greeting\n"
        + "      rpc SayHello (HelloRequest) returns (HelloReply) {}\n"
        + "    }\n"
        + "\n"
        + "    // The request message containing the user's name.\n"
        + "    message HelloRequest {\n"
        + "      string name = 1;\n"
        + "    }\n"
        + "\n"
        + "    // The response message containing the greetings\n"
        + "    message HelloReply {\n"
        + "      string message = 1;\n"
        + "    }";

    String convertedYaml = "apiVersion: harness.io/v1\n"
        + "kind: API\n"
        + "type: grpc\n"
        + "identifier: hello-world\n"
        + "name: hello-world\n"
        + "owner: team-c\n"
        + "spec:\n"
        + "  lifecycle: deprecated\n"
        + "  definition: |-\n"
        + "    // Copyright 2015 gRPC authors.\n"
        + "    //\n"
        + "    // Licensed under the Apache License, Version 2.0 (the \"License\");\n"
        + "    // you may not use this file except in compliance with the License.\n"
        + "    // You may obtain a copy of the License at\n"
        + "    //\n"
        + "    //     http://www.apache.org/licenses/LICENSE-2.0\n"
        + "    //\n"
        + "    // Unless required by applicable law or agreed to in writing, software\n"
        + "    // distributed under the License is distributed on an \"AS IS\" BASIS,\n"
        + "    // WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
        + "    // See the License for the specific language governing permissions and\n"
        + "    // limitations under the License.\n"
        + "\n"
        + "    syntax = \"proto3\";\n"
        + "\n"
        + "    option java_multiple_files = true;\n"
        + "    option java_package = \"io.grpc.examples.helloworld\";\n"
        + "    option java_outer_classname = \"HelloWorldProto\";\n"
        + "    option objc_class_prefix = \"HLW\";\n"
        + "\n"
        + "    package helloworld;\n"
        + "\n"
        + "    // The greeting service definition.\n"
        + "    service Greeter {\n"
        + "      // Sends a greeting\n"
        + "      rpc SayHello (HelloRequest) returns (HelloReply) {}\n"
        + "    }\n"
        + "\n"
        + "    // The request message containing the user's name.\n"
        + "    message HelloRequest {\n"
        + "      string name = 1;\n"
        + "    }\n"
        + "\n"
        + "    // The response message containing the greetings\n"
        + "    message HelloReply {\n"
        + "      string message = 1;\n"
        + "    }\n"
        + "metadata:\n"
        + "  description: Hello World example for gRPC\n";

    EntityRequest entityRequest = new EntityRequest();
    entityRequest.setYaml(yaml);
    when(idpToHarnessHelper.convertBackstageToHarness(TEST_ACCOUNT_IDENTIFIER, yaml)).thenReturn(convertedYaml);
    EntityConvertResponse entityConvertResponse =
        catalogService.convertEntity(TEST_ACCOUNT_IDENTIFIER, "backstage-to-harness", entityRequest, null, false);
    assertThat(entityConvertResponse).isNotNull();
    assertThat(entityConvertResponse.getYaml()).isNotEmpty();
    assertThat(entityConvertResponse.getYaml()).isEqualTo(convertedYaml);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testConvertEntityHarnessToBackstage() {
    String yaml = "apiVersion: harness.io/v1\n"
        + "kind: API\n"
        + "type: grpc\n"
        + "identifier: hello-world\n"
        + "name: hello-world\n"
        + "owner: team-c\n"
        + "spec:\n"
        + "  lifecycle: deprecated\n"
        + "  definition: |-\n"
        + "    // Copyright 2015 gRPC authors.\n"
        + "    //\n"
        + "    // Licensed under the Apache License, Version 2.0 (the \"License\");\n"
        + "    // you may not use this file except in compliance with the License.\n"
        + "    // You may obtain a copy of the License at\n"
        + "    //\n"
        + "    //     http://www.apache.org/licenses/LICENSE-2.0\n"
        + "    //\n"
        + "    // Unless required by applicable law or agreed to in writing, software\n"
        + "    // distributed under the License is distributed on an \"AS IS\" BASIS,\n"
        + "    // WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
        + "    // See the License for the specific language governing permissions and\n"
        + "    // limitations under the License.\n"
        + "\n"
        + "    syntax = \"proto3\";\n"
        + "\n"
        + "    option java_multiple_files = true;\n"
        + "    option java_package = \"io.grpc.examples.helloworld\";\n"
        + "    option java_outer_classname = \"HelloWorldProto\";\n"
        + "    option objc_class_prefix = \"HLW\";\n"
        + "\n"
        + "    package helloworld;\n"
        + "\n"
        + "    // The greeting service definition.\n"
        + "    service Greeter {\n"
        + "      // Sends a greeting\n"
        + "      rpc SayHello (HelloRequest) returns (HelloReply) {}\n"
        + "    }\n"
        + "\n"
        + "    // The request message containing the user's name.\n"
        + "    message HelloRequest {\n"
        + "      string name = 1;\n"
        + "    }\n"
        + "\n"
        + "    // The response message containing the greetings\n"
        + "    message HelloReply {\n"
        + "      string message = 1;\n"
        + "    }\n"
        + "metadata:\n"
        + "  description: Hello World example for gRPC\n";

    String convertedYaml = "apiVersion: backstage.io/v1alpha1\n"
        + "kind: API\n"
        + "metadata:\n"
        + "  name: hello-world\n"
        + "  description: Hello World example for gRPC\n"
        + "spec:\n"
        + "  type: grpc\n"
        + "  lifecycle: deprecated\n"
        + "  owner: team-c\n"
        + "  definition: |\n"
        + "    // Copyright 2015 gRPC authors.\n"
        + "    //\n"
        + "    // Licensed under the Apache License, Version 2.0 (the \"License\");\n"
        + "    // you may not use this file except in compliance with the License.\n"
        + "    // You may obtain a copy of the License at\n"
        + "    //\n"
        + "    //     http://www.apache.org/licenses/LICENSE-2.0\n"
        + "    //\n"
        + "    // Unless required by applicable law or agreed to in writing, software\n"
        + "    // distributed under the License is distributed on an \"AS IS\" BASIS,\n"
        + "    // WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
        + "    // See the License for the specific language governing permissions and\n"
        + "    // limitations under the License.\n"
        + "\n"
        + "    syntax = \"proto3\";\n"
        + "\n"
        + "    option java_multiple_files = true;\n"
        + "    option java_package = \"io.grpc.examples.helloworld\";\n"
        + "    option java_outer_classname = \"HelloWorldProto\";\n"
        + "    option objc_class_prefix = \"HLW\";\n"
        + "\n"
        + "    package helloworld;\n"
        + "\n"
        + "    // The greeting service definition.\n"
        + "    service Greeter {\n"
        + "      // Sends a greeting\n"
        + "      rpc SayHello (HelloRequest) returns (HelloReply) {}\n"
        + "    }\n"
        + "\n"
        + "    // The request message containing the user's name.\n"
        + "    message HelloRequest {\n"
        + "      string name = 1;\n"
        + "    }\n"
        + "\n"
        + "    // The response message containing the greetings\n"
        + "    message HelloReply {\n"
        + "      string message = 1;\n"
        + "    }";

    EntityRequest entityRequest = new EntityRequest();
    entityRequest.setYaml(yaml);
    when(harnessToIDPHelper.convertHarnessToBackstage(TEST_ACCOUNT_IDENTIFIER, yaml, null, false))
        .thenReturn(convertedYaml);
    EntityConvertResponse entityConvertResponse =
        catalogService.convertEntity(TEST_ACCOUNT_IDENTIFIER, "harness-to-backstage", entityRequest, null, false);
    assertThat(entityConvertResponse).isNotNull();
    assertThat(entityConvertResponse.getYaml()).isNotEmpty();
    assertThat(entityConvertResponse.getYaml()).isEqualTo(convertedYaml);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCreateEntityWithTemplateToWorkflowConversion() throws IOException {
    String yaml = "apiVersion: scaffolder.backstage.io/v1beta3\n"
        + "kind: Template\n"
        + "metadata:\n"
        + "  name: notifications-demo\n"
        + "  title: Test Notifications template\n"
        + "  description: scaffolder v1beta3 template demo sending notification\n"
        + "spec:\n"
        + "  owner: backstage/techdocs-core\n"
        + "  type: service\n"
        + "  parameters:\n"
        + "    - title: Notification\n"
        + "      required:\n"
        + "        - recipients\n"
        + "        - title\n"
        + "      properties:\n"
        + "        recipients:\n"
        + "          title: Recipients\n"
        + "          type: string\n"
        + "          description: Notification recipients\n"
        + "          default: entity\n"
        + "          enum:\n"
        + "            - entity\n"
        + "            - broadcast\n"
        + "        entityRefs:\n"
        + "          title: Entities\n"
        + "          type: array\n"
        + "          description: Entities to send the notification. Required if recipients is entity\n"
        + "          ui:field: MultiEntityPicker\n"
        + "          ui:options:\n"
        + "            defaultNamespace: default\n"
        + "        title:\n"
        + "          title: Title\n"
        + "          type: string\n"
        + "          description: Notification title\n"
        + "        description:\n"
        + "          title: Description\n"
        + "          type: string\n"
        + "          description: Notification longer description\n"
        + "        link:\n"
        + "          title: Link\n"
        + "          type: string\n"
        + "          description: Notification link\n"
        + "        severity:\n"
        + "          title: Severity\n"
        + "          type: string\n"
        + "          description: Notification severity\n"
        + "          default: normal\n"
        + "          enum:\n"
        + "            - low\n"
        + "            - normal\n"
        + "            - high\n"
        + "            - critical\n"
        + "        scope:\n"
        + "          title: Scope\n"
        + "          type: string\n"
        + "          description: Notification scope\n"
        + "\n"
        + "  steps:\n"
        + "    - id: send-notification\n"
        + "      name: Send notification\n"
        + "      action: notification:send\n"
        + "      input:\n"
        + "        recipients: ${{ parameters.recipients }}\n"
        + "        entityRefs: ${{ parameters.entityRefs }}\n"
        + "        title: ${{ parameters.title }}\n"
        + "        description: ${{ parameters.description }}\n"
        + "        link: ${{ parameters.link }}\n"
        + "        severity: ${{ parameters.severity }}\n"
        + "        scope: ${{ parameters.scope }}";

    String convertedYaml = "apiVersion: harness.io/v1\n"
        + "kind: Workflow\n"
        + "type: service\n"
        + "identifier: notifications-demo\n"
        + "name: Test Notifications template\n"
        + "owner: backstage/techdocs-core\n"
        + "spec:\n"
        + "  parameters:\n"
        + "  - title: Notification\n"
        + "    required:\n"
        + "    - recipients\n"
        + "    - title\n"
        + "    properties:\n"
        + "      recipients:\n"
        + "        title: Recipients\n"
        + "        type: string\n"
        + "        description: Notification recipients\n"
        + "        default: entity\n"
        + "        enum:\n"
        + "        - entity\n"
        + "        - broadcast\n"
        + "      entityRefs:\n"
        + "        title: Entities\n"
        + "        type: array\n"
        + "        description: Entities to send the notification. Required if recipients is\n"
        + "          entity\n"
        + "        ui:field: MultiEntityPicker\n"
        + "        ui:options:\n"
        + "          defaultNamespace: default\n"
        + "      title:\n"
        + "        title: Title\n"
        + "        type: string\n"
        + "        description: Notification title\n"
        + "      description:\n"
        + "        title: Description\n"
        + "        type: string\n"
        + "        description: Notification longer description\n"
        + "      link:\n"
        + "        title: Link\n"
        + "        type: string\n"
        + "        description: Notification link\n"
        + "      severity:\n"
        + "        title: Severity\n"
        + "        type: string\n"
        + "        description: Notification severity\n"
        + "        default: normal\n"
        + "        enum:\n"
        + "        - low\n"
        + "        - normal\n"
        + "        - high\n"
        + "        - critical\n"
        + "      scope:\n"
        + "        title: Scope\n"
        + "        type: string\n"
        + "        description: Notification scope\n"
        + "  steps:\n"
        + "  - id: send-notification\n"
        + "    name: Send notification\n"
        + "    action: notification:send\n"
        + "    input:\n"
        + "      recipients: ${{ parameters.recipients }}\n"
        + "      entityRefs: ${{ parameters.entityRefs }}\n"
        + "      title: ${{ parameters.title }}\n"
        + "      description: ${{ parameters.description }}\n"
        + "      link: ${{ parameters.link }}\n"
        + "      severity: ${{ parameters.severity }}\n"
        + "      scope: ${{ parameters.scope }}\n"
        + "metadata:\n"
        + "  description: scaffolder v1beta3 template demo sending notification";

    EntityCreateRequest entityRequest = new EntityCreateRequest();
    entityRequest.setYaml(yaml);

    when(kindServiceHelper.kindEntity(TEST_ACCOUNT_IDENTIFIER, "workflow")).thenReturn(KindEntity.builder().build());
    when(catalogServiceHelper.validateAndSanitizeKind("Template")).thenReturn("template");
    when(idpToHarnessHelper.convertBackstageToHarness(TEST_ACCOUNT_IDENTIFIER, yaml)).thenReturn(convertedYaml);
    when(catalogServiceHelper.validateAndSanitizeIdentifier("notifications-demo")).thenReturn("notifications-demo");

    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build();
    when(catalogScopeResolver.resolveSingleScopeInfo(TEST_ACCOUNT_IDENTIFIER, "account")).thenReturn(scopeInfo);

    when(catalogServiceHelper.resolveExpressionsInEntityYaml(TEST_ACCOUNT_IDENTIFIER, convertedYaml))
        .thenReturn(convertedYaml);
    when(catalogServiceHelper.resolveMembersForCustomUserGroup("workflow", convertedYaml)).thenReturn(convertedYaml);

    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    EntityResponse entityResponse =
        catalogService.createEntity(TEST_ACCOUNT_IDENTIFIER, null, null, true, false, entityRequest);

    verify(transactionHelper, times(1)).performTransaction(any());
    assertThat(entityResponse).isNotNull();
    assertThat(entityResponse.getIdentifier()).isEqualTo("notifications-demo");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testCreateEntityComponent() throws IOException {
    String yaml = "apiVersion: harness.io/v1\n"
        + "kind: Component\n"
        + "type: service\n"
        + "identifier: artist-lookup\n"
        + "name: artist-lookup\n"
        + "owner: team-a\n"
        + "spec:\n"
        + "  lifecycle: experimental\n"
        + "  dependsOn:\n"
        + "  - resource:artists-db\n"
        + "  apiConsumedBy:\n"
        + "  - component:www-artist\n"
        + "metadata:\n"
        + "  description: Artist Lookup\n"
        + "  annotations:\n"
        + "    backstage.io/linguist: https://github.com/backstage/backstage/tree/master/plugins/playlist\n"
        + "  links:\n"
        + "  - url: https://example.com/user\n"
        + "    title: Examples Users\n"
        + "    icon: user\n"
        + "  - url: https://example.com/group\n"
        + "    title: Example Group\n"
        + "    icon: group\n"
        + "  - url: https://example.com/cloud\n"
        + "    title: Link with Cloud Icon\n"
        + "    icon: cloud\n"
        + "  - url: https://example.com/dashboard\n"
        + "    title: Dashboard\n"
        + "    icon: dashboard\n"
        + "  - url: https://example.com/help\n"
        + "    title: Support\n"
        + "    icon: help\n"
        + "  - url: https://example.com/web\n"
        + "    title: Website\n"
        + "    icon: web\n"
        + "  - url: https://example.com/alert\n"
        + "    title: Alerts\n"
        + "    icon: alert\n"
        + "  tags:\n"
        + "  - java\n"
        + "  - data";

    EntityCreateRequest entityRequest = new EntityCreateRequest();
    entityRequest.setYaml(yaml);

    when(kindServiceHelper.kindEntity(TEST_ACCOUNT_IDENTIFIER, "component")).thenReturn(KindEntity.builder().build());
    when(catalogServiceHelper.validateAndSanitizeKind("Component")).thenReturn("component");
    when(catalogServiceHelper.validateAndSanitizeIdentifier("artist-lookup")).thenReturn("artist-lookup");

    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build();
    when(catalogScopeResolver.resolveSingleScopeInfo(TEST_ACCOUNT_IDENTIFIER, "account")).thenReturn(scopeInfo);

    when(catalogServiceHelper.resolveExpressionsInEntityYaml(TEST_ACCOUNT_IDENTIFIER, yaml)).thenReturn(yaml);
    when(catalogServiceHelper.resolveMembersForCustomUserGroup("component", yaml)).thenReturn(yaml);

    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    EntityResponse entityResponse =
        catalogService.createEntity(TEST_ACCOUNT_IDENTIFIER, null, null, false, false, entityRequest);

    verify(transactionHelper, times(1)).performTransaction(any());
    assertThat(entityResponse).isNotNull();
    assertThat(entityResponse.getIdentifier()).isEqualTo("artist-lookup");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntity() throws IOException {
    String entityRef = "component:test";

    when(kindServiceHelper.kindEntity(TEST_ACCOUNT_IDENTIFIER, "component")).thenReturn(KindEntity.builder().build());
    when(catalogServiceHelper.getKindScopeIdentifier(entityRef)).thenReturn(Triple.of("component", "account", "test"));
    when(catalogServiceHelper.validateAndSanitizeKind("component")).thenReturn("component");
    when(catalogServiceHelper.validateAndSanitizeIdentifier("test")).thenReturn("test");
    when(scorecardService.getAllScorecardAndChecks(any(), any())).thenReturn(getScorecardsChecks());
    CheckStatus checkStatus = new CheckStatus();
    checkStatus.setIdentifier("check1");
    checkStatus.setStatus(CheckStatus.StatusEnum.PASS);
    ScoreEntity scoreEntity =
        ScoreEntity.builder().id("scorecard1").checkStatus(List.of(checkStatus)).score(85).build();
    when(scoreService.fetchScoresForCatalogEntity(any(), any(), any())).thenReturn(List.of(scoreEntity));

    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build();
    when(catalogScopeResolver.resolveSingleScopeInfo(TEST_ACCOUNT_IDENTIFIER, "account")).thenReturn(scopeInfo);

    when(catalogServiceHelper.catalogEntityFromGit(TEST_ACCOUNT_IDENTIFIER, "component", "test", false, false))
        .thenReturn(InlineCatalogEntity.builder()
                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                        .identifier("test")
                        .kind(COMPONENT_KIND)
                        .apiVersion("harness.io/v1")
                        .referenceType(ReferenceType.INLINE)
                        .build());

    EntityResponse entityResponse =
        catalogService.getEntity(TEST_ACCOUNT_IDENTIFIER, null, null, entityRef, false, false, false);

    assertThat(entityResponse).isNotNull();
    assertThat(entityResponse.getIdentifier()).isEqualTo("test");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testUpdateEntityComponent() throws IOException {
    String yaml = "apiVersion: harness.io/v1\n"
        + "kind: Component\n"
        + "type: service\n"
        + "identifier: artist-lookup\n"
        + "name: artist-lookup-updated\n"
        + "owner: team-a\n"
        + "spec:\n"
        + "  lifecycle: experimental\n"
        + "  dependsOn:\n"
        + "  - resource:artists-db\n"
        + "  apiConsumedBy:\n"
        + "  - component:www-artist\n"
        + "metadata:\n"
        + "  description: Artist Lookup\n"
        + "  annotations:\n"
        + "    backstage.io/linguist: https://github.com/backstage/backstage/tree/master/plugins/playlist\n"
        + "  links:\n"
        + "  - url: https://example.com/user\n"
        + "    title: Examples Users\n"
        + "    icon: user\n"
        + "  - url: https://example.com/group\n"
        + "    title: Example Group\n"
        + "    icon: group\n"
        + "  - url: https://example.com/cloud\n"
        + "    title: Link with Cloud Icon\n"
        + "    icon: cloud\n"
        + "  - url: https://example.com/dashboard\n"
        + "    title: Dashboard\n"
        + "    icon: dashboard\n"
        + "  - url: https://example.com/help\n"
        + "    title: Support\n"
        + "    icon: help\n"
        + "  - url: https://example.com/web\n"
        + "    title: Website\n"
        + "    icon: web\n"
        + "  - url: https://example.com/alert\n"
        + "    title: Alerts\n"
        + "    icon: alert\n"
        + "  tags:\n"
        + "  - java\n"
        + "  - data";

    EntityUpdateRequest entityRequest = new EntityUpdateRequest();
    entityRequest.setYaml(yaml);

    String entityRef = "component:artist-lookup";

    when(kindServiceHelper.kindEntity(TEST_ACCOUNT_IDENTIFIER, "component")).thenReturn(KindEntity.builder().build());
    when(catalogServiceHelper.getKindScopeIdentifier(entityRef))
        .thenReturn(Triple.of("component", "account", "artist-lookup"));
    when(catalogServiceHelper.validateAndSanitizeKind("Component")).thenReturn("component");
    when(catalogServiceHelper.validateAndSanitizeIdentifier("artist-lookup")).thenReturn("artist-lookup");

    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build();
    when(catalogScopeResolver.resolveSingleScopeInfo(TEST_ACCOUNT_IDENTIFIER, "account")).thenReturn(scopeInfo);

    when(catalogServiceHelper.catalogEntityFromGit(TEST_ACCOUNT_IDENTIFIER, "component", "artist-lookup", false, false))
        .thenReturn(InlineCatalogEntity.builder()
                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                        .identifier("artist-lookup")
                        .kind(COMPONENT_KIND)
                        .name("artist-lookup")
                        .apiVersion("harness.io/v1")
                        .referenceType(ReferenceType.INLINE)
                        .metadata(Map.of("test", "test"))
                        .build());
    when(catalogServiceHelper.resolveExpressionsInEntityYaml(TEST_ACCOUNT_IDENTIFIER, yaml)).thenReturn(yaml);
    when(catalogServiceHelper.resolveMembersForCustomUserGroup("component", yaml)).thenReturn(yaml);

    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });
    when(scorecardService.getAllScorecardAndChecks(any(), any())).thenReturn(getScorecardsChecks());
    CheckStatus checkStatus = new CheckStatus();
    checkStatus.setIdentifier("check1");
    checkStatus.setStatus(CheckStatus.StatusEnum.PASS);
    ScoreEntity scoreEntity =
        ScoreEntity.builder().id("scorecard1").checkStatus(List.of(checkStatus)).score(85).build();
    when(scoreService.fetchScoresForCatalogEntity(any(), any(), any())).thenReturn(List.of(scoreEntity));

    EntityResponse entityResponse =
        catalogService.updateEntity(TEST_ACCOUNT_IDENTIFIER, null, null, entityRef, entityRequest);

    verify(transactionHelper, times(1)).performTransaction(any());
    assertThat(entityResponse).isNotNull();
    assertThat(entityResponse.getIdentifier()).isEqualTo("artist-lookup");
    assertThat(entityResponse.getName()).isEqualTo("artist-lookup-updated");
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testDeleteEntityInvalidEntityRef() {
    String entityRef = "test";
    catalogService.getEntity(TEST_ACCOUNT_IDENTIFIER, null, null, entityRef, false, false, false);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testDeleteEntity() throws IOException {
    String entityRef = "component:test";

    when(catalogServiceHelper.validateAndSanitizeKind("component")).thenReturn("component");
    when(catalogServiceHelper.validateAndSanitizeIdentifier("test")).thenReturn("test");
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build();
    when(catalogScopeResolver.resolveSingleScopeInfo(TEST_ACCOUNT_IDENTIFIER, "account")).thenReturn(scopeInfo);

    when(catalogServiceHelper.catalogEntity(TEST_ACCOUNT_IDENTIFIER, "component", "test"))
        .thenReturn(InlineCatalogEntity.builder()
                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                        .identifier("test")
                        .kind(COMPONENT_KIND)
                        .name("test")
                        .apiVersion("harness.io/v1")
                        .referenceType(ReferenceType.INLINE)
                        .metadata(Map.of("test", "test"))
                        .build());

    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    catalogService.deleteEntity(TEST_ACCOUNT_IDENTIFIER, null, null, entityRef, false);

    verify(transactionHelper, times(1)).performTransaction(any());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testGetEntities() throws IOException {
    when(catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(TEST_ACCOUNT_IDENTIFIER, null, null))
        .thenReturn(Pair.of(List.of(ScopeInfo.builder()
                                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                        .scopeType(ScopeLevel.ACCOUNT)
                                        .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                        .build()),
            Collections.emptyMap()));
    Response<ResponseDTO<PageResponse<OrganizationResponse>>> pageOrganizationResponse =
        Response.success(ResponseDTO.newResponse(PageResponse.<OrganizationResponse>builder().build()));
    Call<ResponseDTO<PageResponse<OrganizationResponse>>> listOrganizationCall = mock(Call.class);
    when(listOrganizationCall.execute()).thenReturn(pageOrganizationResponse);
    when(organizationClient.listOrganization(
             eq(TEST_ACCOUNT_IDENTIFIER), anyList(), eq(null), eq(0), eq(1000), eq(null)))
        .thenReturn(listOrganizationCall);

    when(organizationClient.listAllOrganizations(eq(TEST_ACCOUNT_IDENTIFIER), anyList(), eq(null)))
        .thenReturn(listOrganizationCall);

    Response<ResponseDTO<PageResponse<ProjectResponse>>> pageProjectResponse =
        Response.success(ResponseDTO.newResponse(PageResponse.<ProjectResponse>builder().build()));
    Call<ResponseDTO<PageResponse<ProjectResponse>>> listProjectCall = mock(Call.class);
    when(listProjectCall.execute()).thenReturn(pageProjectResponse);
    when(projectClient.listWithMultiOrg(eq(TEST_ACCOUNT_IDENTIFIER), anySet(), anyBoolean(), anyList(), eq(null),
             eq(null), eq(0), eq(100), eq(null), eq(false)))
        .thenReturn(listProjectCall);

    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse =
        Response.success(ResponseDTO.newResponse(ScopeInfo.builder()
                                                     .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                     .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                                     .scopeType(ScopeLevel.ACCOUNT)
                                                     .build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfoCall);

    when(catalogServiceHelper.checkEntitiesRbac(TEST_ACCOUNT_IDENTIFIER, List.of(TEST_ACCOUNT_IDENTIFIER)))
        .thenReturn(List.of("component:test"));
    when(catalogServiceHelper.getKindScopeIdentifier("component:test"))
        .thenReturn(Triple.of("component", "account", "test"));
    when(catalogServiceHelper.scopeInfosRbac(TEST_ACCOUNT_IDENTIFIER,
             List.of(ScopeInfo.builder()
                         .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                         .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                         .scopeType(ScopeLevel.ACCOUNT)
                         .build()),
             null))
        .thenReturn(List.of(ScopeInfo.builder()
                                .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                .scopeType(ScopeLevel.ACCOUNT)
                                .build()));

    when(catalogServiceHelper.getUserFavoriteEntityRefs(TEST_ACCOUNT_IDENTIFIER, null, null, IDPENTITY.name()))
        .thenReturn("fav1");
    List<ScopeInfo> scopeInfos = List.of(ScopeInfo.builder()
                                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                             .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                             .scopeType(ScopeLevel.ACCOUNT)
                                             .build(),
        ScopeInfo.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .uniqueId(TEST_ACCOUNT_IDENTIFIER)
            .scopeType(ScopeLevel.ACCOUNT)
            .build());
    when(catalogServiceHelper.getOwnedByMe(TEST_ACCOUNT_IDENTIFIER, null)).thenReturn("owner");
    when(catalogEntityRepository.getOwnedEntitiesCount(
             List.of(TEST_ACCOUNT_IDENTIFIER), null, "owner", TEST_ACCOUNT_IDENTIFIER, scopeInfos, null, false, null))
        .thenReturn(1L);
    when(catalogEntityRepository.getFavoritesEntitiesCount(TEST_ACCOUNT_IDENTIFIER,
             List.of(ScopeInfo.builder()
                         .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                         .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                         .scopeType(ScopeLevel.ACCOUNT)
                         .build(),
                 ScopeInfo.builder()
                     .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                     .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                     .scopeType(ScopeLevel.ACCOUNT)
                     .build()),
             "fav1", null, false, null))
        .thenReturn(1L);

    List<CatalogEntity> catalogEntities = List.of(InlineCatalogEntity.builder()
                                                      .identifier("1")
                                                      .referenceType(ReferenceType.INLINE)
                                                      .kind(COMPONENT_KIND)
                                                      .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                      .apiVersion("harness.io/v1")
                                                      .build(),
        InlineCatalogEntity.builder()
            .identifier("2")
            .referenceType(ReferenceType.INLINE)
            .kind(COMPONENT_KIND)
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .apiVersion("harness.io/v1")
            .build(),
        InlineCatalogEntity.builder()
            .identifier("3")
            .referenceType(ReferenceType.INLINE)
            .kind(COMPONENT_KIND)
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .apiVersion("harness.io/v1")
            .build(),
        InlineCatalogEntity.builder()
            .identifier("4")
            .referenceType(ReferenceType.INLINE)
            .kind(COMPONENT_KIND)
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .apiVersion("harness.io/v1")
            .build(),
        InlineCatalogEntity.builder()
            .identifier("5")
            .referenceType(ReferenceType.INLINE)
            .kind(COMPONENT_KIND)
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .apiVersion("harness.io/v1")
            .build());

    when(catalogEntityRepository.getEntities(TEST_ACCOUNT_IDENTIFIER, scopeInfos, 0, 10, null, null, null, "",
             "component:test", null, null, null, null, null, null, null, false))
        .thenReturn(new PageImpl<>(catalogEntities, PageRequest.of(0, 10), 5));
    when(catalogServiceHelper.resolveOwner(anyString(), anyList())).thenReturn(catalogEntities);
    when(scorecardService.getAllScorecardAndChecks(any(), any())).thenReturn(getScorecardsChecks());
    CheckStatus checkStatus = new CheckStatus();
    checkStatus.setIdentifier("check1");
    checkStatus.setStatus(CheckStatus.StatusEnum.PASS);
    ScoreEntity scoreEntity =
        ScoreEntity.builder().id("scorecard1").checkStatus(List.of(checkStatus)).score(85).build();
    when(scoreService.fetchScoresForCatalogEntity(any(), any(), any())).thenReturn(List.of(scoreEntity));

    GetEntitiesDTO getEntitiesDTO = catalogService.getEntities(TEST_ACCOUNT_IDENTIFIER, 0, 10, null, null, false, null,
        null, false, false, null, null, null, null, null, null, true);
    assertThat(getEntitiesDTO).isNotNull();
    assertThat(getEntitiesDTO.getPageNumber()).isEqualTo(0);
    assertThat(getEntitiesDTO.getTotalElements()).isEqualTo(5);
    assertThat(getEntitiesDTO.getTotalOwned()).isEqualTo(1);
    assertThat(getEntitiesDTO.getTotalStarred()).isEqualTo(1);
    assertThat(getEntitiesDTO.getEntityResponses().size()).isEqualTo(5);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testMigrateEntitiesSuccess() {
    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(catalogServiceHelper).validateMigrateRequest(any(), any());
    when(namespaceService.getEntityForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(
            NamespaceEntity.builder()
                .metadata(NamespaceEntity.Metadata.builder()
                              .idpV2MigrationInfo(NamespaceEntity.Metadata.IdpV2MigrationInfo.builder()
                                                      .migrateDefaultToAccountNamespaceInDependentsCompleted(true)
                                                      .migrateDefaultToAccountNamespaceInBackstageCompleted(true)
                                                      .build())
                              .build())
                .build()));

    Principal principal = new UserPrincipal("name", "email", "username", TEST_ACCOUNT_IDENTIFIER);
    SourcePrincipalContextBuilder.setSourcePrincipal(principal);
    catalogService.migrateEntities(TEST_ACCOUNT_IDENTIFIER, new EntitiesMigrateRequest());
    verify(namespaceService, times(1)).save(any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testMigrateEntitiesThrowExceptionWhenLockAcquisitionFails() {
    when(resourceLocker.acquireLock(any())).thenReturn(null);
    catalogService.migrateEntities(TEST_ACCOUNT_IDENTIFIER, new EntitiesMigrateRequest());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testMigrateEntitiesThrowExceptionWhenIDPV2MigrationIsNotCompleted() {
    when(resourceLocker.acquireLock(any())).thenReturn(RedisAcquiredLock.builder().build());
    doNothing().when(catalogServiceHelper).validateMigrateRequest(any(), any());
    when(namespaceService.getEntityForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(
            NamespaceEntity.builder()
                .metadata(NamespaceEntity.Metadata.builder()
                              .idpV2MigrationInfo(NamespaceEntity.Metadata.IdpV2MigrationInfo.builder()
                                                      .migrateDefaultToAccountNamespaceInDependentsCompleted(false)
                                                      .migrateDefaultToAccountNamespaceInBackstageCompleted(false)
                                                      .build())
                              .build())
                .build()));
    catalogService.migrateEntities(TEST_ACCOUNT_IDENTIFIER, new EntitiesMigrateRequest());
  }

  private Map<String, Object> backstageCatalogEntitiesApiResponse() {
    return gson.fromJson("{\n"
            + "  \"apiVersion\": \"backstage.io/v1alpha1\",\n"
            + "  \"kind\": \"Component\",\n"
            + "  \"metadata\": {\n"
            + "    \"annotations\": {\n"
            + "      \"backstage.io/managed-by-location\": "
            + "\"url:https://app.harness.io/Component/account/lightwing-worker\",\n"
            + "      \"backstage.io/managed-by-origin-location\": "
            + "\"url:https://app.harness.io/Component/account/lightwing-worker\",\n"
            + "      \"backstage.io/view-url\": \"https://app.harness.io/Component/account/lightwing-worker\",\n"
            + "      \"backstage.io/edit-url\": "
            + "\"https://app.harness.io/ng/account/lightwing-worker/module/code/repos/Component/files//Component/"
            + "account/lightwing-worker/~/\",\n"
            + "      \"backstage.io/source-location\": \"url:https://github.com/wings-software/lightwing\",\n"
            + "      \"harness.io/projects-stage\": \"Operations,RELEASEBUILDS\",\n"
            + "      \"harness.io/ci-pipelineIds-stage\": \"Lightwing_Build\",\n"
            + "      \"harness.io/project-url-stage\": "
            + "\"https://stage.harness.io/ng/account/wFHXHD0RRQWoO8tIZT5YVw/cd/orgs/Harness/projects/Operations/"
            + "deployments\",\n"
            + "      \"backstage.io/kubernetes-label-selector\": \"app=faktory\",\n"
            + "      \"harness.io/cd-serviceId-stage\": \"LightwingFaktoryWorker\",\n"
            + "      \"github.com/project-slug\": \"wings-software/lightwing\",\n"
            + "      \"harness.io/cd-serviceId\": \"\",\n"
            + "      \"pagerduty.com/service-id\": \"PFVOX97\",\n"
            + "      \"jira/project-key\": \"CCM\"\n"
            + "    },\n"
            + "    \"harnessData\": {\n"
            + "      \"qa_version\": \"1.4\",\n"
            + "      \"prod_version\": \"1.3\"\n"
            + "    },\n"
            + "    \"name\": \"lightwing-worker\",\n"
            + "    \"namespace\": \"account\",\n"
            + "    \"description\": \"CCM lightwing worker service\",\n"
            + "    \"links\": [\n"
            + "      {\n"
            + "        \"title\": \"repo\",\n"
            + "        \"url\": \"https://github.com/wings-software/lightwing\"\n"
            + "      }\n"
            + "    ],\n"
            + "    \"title\": \"lightwing-worker\",\n"
            + "    \"tags\": [\n"
            + "      \"go\"\n"
            + "    ],\n"
            + "    \"uid\": \"1369c197-8b06-4f76-ac09-22df5c6540c8\",\n"
            + "    \"etag\": \"a1d44eaf4d35b24c590792823e3f0110f814aeab\"\n"
            + "  },\n"
            + "  \"relations\": [\n"
            + "    {\n"
            + "      \"type\": \"dependsOn\",\n"
            + "      \"targetRef\": \"component:account/ng-manager\",\n"
            + "      \"target\": {\n"
            + "        \"kind\": \"component\",\n"
            + "        \"namespace\": \"account\",\n"
            + "        \"name\": \"ng-manager\"\n"
            + "      }\n"
            + "    },\n"
            + "    {\n"
            + "      \"type\": \"ownedBy\",\n"
            + "      \"targetRef\": \"group:account/ccmplayacc\",\n"
            + "      \"target\": {\n"
            + "        \"kind\": \"group\",\n"
            + "        \"namespace\": \"account\",\n"
            + "        \"name\": \"ccmplayacc\"\n"
            + "      }\n"
            + "    }\n"
            + "  ],\n"
            + "  \"spec\": {\n"
            + "    \"lifecycle\": \"production\",\n"
            + "    \"owner\": \"group:account/ccmplayacc\",\n"
            + "    \"dependsOn\": [\n"
            + "      \"component:account/ng-manager\"\n"
            + "    ],\n"
            + "    \"type\": \"Service\"\n"
            + "  }\n"
            + "}",
        new TypeToken<Map<String, Object>>() {}.getType());
  }

  public List<ScorecardAndChecks> getScorecardsChecks() {
    ScorecardEntity scorecard1 = ScorecardEntity.builder()
                                     .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                     .identifier("scorecard1")
                                     .name("Scorecard 1")
                                     .description("Description for Scorecard 1")
                                     .published(true)
                                     .isDeleted(false)
                                     .build();
    ScorecardEntity scorecard2 = ScorecardEntity.builder()
                                     .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                     .identifier("scorecard2")
                                     .name("Scorecard 2")
                                     .description("Description for Scorecard 2")
                                     .published(true)
                                     .isDeleted(false)
                                     .build();
    CheckEntity check1 = CheckEntity.builder().name("check1").identifier("check1").build();
    CheckEntity check2 = CheckEntity.builder().name("check2").identifier("check2").build();
    ScorecardAndChecks scorecardAndChecks1 =
        ScorecardAndChecks.builder().scorecard(scorecard1).checks(List.of(check1)).build();
    ScorecardAndChecks scorecardAndChecks2 =
        ScorecardAndChecks.builder().scorecard(scorecard2).checks(List.of(check2)).build();
    return List.of(scorecardAndChecks1, scorecardAndChecks2);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetEntityAssociations_HappyPath_ScorecardsEnriched() throws IOException {
    String kind = "system";
    String identifier = "my-system";
    String relations = "hasPart";

    when(catalogServiceHelper.validateAndSanitizeKind(kind)).thenReturn(kind);
    doNothing().when(catalogServiceHelper).checkCrudRbac(any(), any(), any(), any(), any(), any());

    ScopeInfo accountScopeInfo = ScopeInfo.builder()
                                     .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                     .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                     .scopeType(ScopeLevel.ACCOUNT)
                                     .build();

    CatalogEntity parentEntity = InlineCatalogEntity.builder()
                                     .kind(kind)
                                     .identifier(identifier)
                                     .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                     .relations(Map.of("hasPart", Set.of("component:account/service-a")))
                                     .build();

    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(accountScopeInfo)));
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfoCall);

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, kind, identifier))
        .thenReturn(Optional.of(parentEntity));

    when(catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(Pair.of(List.of(accountScopeInfo), Collections.emptyMap()));

    // Team-ownership fallback helpers (no permitted groups -> no indirect refs added).
    when(catalogServiceHelper.uniqueParentScopesForGroups(any())).thenReturn(Set.of(TEST_ACCOUNT_IDENTIFIER));
    when(catalogServiceHelper.checkEntitiesRbacByKind(any(), anyList(), eq(GROUP_KIND)))
        .thenReturn(Collections.emptyList());
    when(catalogEntityRepository.findAllByParentUniqueIdInAndKindInAndOwnerIn(anyList(), anyList(), anyList()))
        .thenReturn(Collections.emptyList());

    when(catalogServiceHelper.checkEntitiesRbac(eq(TEST_ACCOUNT_IDENTIFIER), anyList()))
        .thenReturn(List.of("component:account/service-a"));

    when(catalogServiceHelper.getUserFavoriteEntityRefs(any(), any(), any(), any())).thenReturn(null);

    CatalogEntity associatedEntity = InlineCatalogEntity.builder()
                                         .kind(COMPONENT_KIND)
                                         .identifier("service-a")
                                         .name("Service A")
                                         .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                         .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
                                         .referenceType(ReferenceType.INLINE)
                                         .build();

    when(catalogEntityRepository.findEntitiesByRelationRefs(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(associatedEntity), PageRequest.of(0, 10), 1));

    when(kindServiceHelper.findByAccountIdentifierIn(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(List.of(BuiltInKindEntity.builder().identifier(COMPONENT_KIND).icon("icon").build()));

    when(catalogServiceHelper.resolveOwner(any(), anyList())).thenReturn(List.of(associatedEntity));

    ScopeTopology topology = mock(ScopeTopology.class);
    when(catalogScopeResolver.getOrBuildTopology(TEST_ACCOUNT_IDENTIFIER)).thenReturn(topology);

    List<ScorecardAndChecks> scorecardAndChecksList = getScorecardsChecks();
    when(scorecardService.getAllScorecardAndChecks(TEST_ACCOUNT_IDENTIFIER, null)).thenReturn(scorecardAndChecksList);

    ScoreEntity scoreEntity =
        ScoreEntity.builder()
            .scorecardIdentifier("scorecard1")
            .entityIdentifier("service-a")
            .score(85)
            .checkStatus(List.of(new CheckStatus().status(CheckStatus.StatusEnum.PASS).identifier("check1")))
            .build();

    when(scorecardScoreHelper.fetchScoresForEntities(
             eq(TEST_ACCOUNT_IDENTIFIER), anyList(), eq(scorecardAndChecksList), eq(topology)))
        .thenReturn(Map.of("component:account/service-a", List.of(scoreEntity)));

    when(catalogServiceHelper.getKindScopeIdentifier(anyString()))
        .thenReturn(Triple.of(COMPONENT_KIND, "account", "service-a"));
    when(catalogServiceHelper.isInheritableKind(COMPONENT_KIND)).thenReturn(true);

    GetEntitiesDTO result = catalogService.getEntityAssociations(TEST_ACCOUNT_IDENTIFIER, null, null, kind, identifier,
        relations, 0, 10, null, null, null, null, null, null, null, null, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);
    EntityResponse response = result.getEntityResponses().get(0);
    assertThat(response.getScorecards()).isNotNull();
    assertThat(response.getScorecards().getScores()).hasSize(1);
    assertThat(response.getScorecards().getScores().get(0).getScorecard()).isEqualTo("scorecard1");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetEntityAssociations_ScorecardEnrichmentError_ReturnsNullScorecards() throws IOException {
    String kind = "system";
    String identifier = "my-system";
    String relations = "hasPart";

    when(catalogServiceHelper.validateAndSanitizeKind(kind)).thenReturn(kind);
    doNothing().when(catalogServiceHelper).checkCrudRbac(any(), any(), any(), any(), any(), any());

    ScopeInfo accountScopeInfo = ScopeInfo.builder()
                                     .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                     .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                     .scopeType(ScopeLevel.ACCOUNT)
                                     .build();

    CatalogEntity parentEntity = InlineCatalogEntity.builder()
                                     .kind(kind)
                                     .identifier(identifier)
                                     .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                     .relations(Map.of("hasPart", Set.of("component:account/service-a")))
                                     .build();

    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(accountScopeInfo)));
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfoCall);

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, kind, identifier))
        .thenReturn(Optional.of(parentEntity));

    when(catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(Pair.of(List.of(accountScopeInfo), Collections.emptyMap()));

    // Team-ownership fallback helpers (no permitted groups -> no indirect refs added).
    when(catalogServiceHelper.uniqueParentScopesForGroups(any())).thenReturn(Set.of(TEST_ACCOUNT_IDENTIFIER));
    when(catalogServiceHelper.checkEntitiesRbacByKind(any(), anyList(), eq(GROUP_KIND)))
        .thenReturn(Collections.emptyList());
    when(catalogEntityRepository.findAllByParentUniqueIdInAndKindInAndOwnerIn(anyList(), anyList(), anyList()))
        .thenReturn(Collections.emptyList());

    when(catalogServiceHelper.checkEntitiesRbac(eq(TEST_ACCOUNT_IDENTIFIER), anyList()))
        .thenReturn(List.of("component:account/service-a"));

    when(catalogServiceHelper.getUserFavoriteEntityRefs(any(), any(), any(), any())).thenReturn(null);

    CatalogEntity associatedEntity = InlineCatalogEntity.builder()
                                         .kind(COMPONENT_KIND)
                                         .identifier("service-a")
                                         .name("Service A")
                                         .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                         .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
                                         .referenceType(ReferenceType.INLINE)
                                         .build();

    when(catalogEntityRepository.findEntitiesByRelationRefs(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(associatedEntity), PageRequest.of(0, 10), 1));

    when(kindServiceHelper.findByAccountIdentifierIn(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(List.of(BuiltInKindEntity.builder().identifier(COMPONENT_KIND).icon("icon").build()));

    when(catalogServiceHelper.resolveOwner(any(), anyList())).thenReturn(List.of(associatedEntity));

    when(catalogScopeResolver.getOrBuildTopology(TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new RuntimeException("Topology build failed"));

    when(catalogServiceHelper.getKindScopeIdentifier(anyString()))
        .thenReturn(Triple.of(COMPONENT_KIND, "account", "service-a"));
    when(catalogServiceHelper.isInheritableKind(COMPONENT_KIND)).thenReturn(true);

    GetEntitiesDTO result = catalogService.getEntityAssociations(TEST_ACCOUNT_IDENTIFIER, null, null, kind, identifier,
        relations, 0, 10, null, null, null, null, null, null, null, null, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);
    EntityResponse response = result.getEntityResponses().get(0);
    assertThat(response.getScorecards()).isNotNull();
    assertThat(response.getScorecards().getAverage()).isNull();
    assertThat(response.getScorecards().getScores()).isEmpty();
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetEntityAssociations_NonCoreKind_SkipsScorecardEnrichment() throws IOException {
    String kind = "system";
    String identifier = "my-system";
    String relations = "hasPart";

    when(catalogServiceHelper.validateAndSanitizeKind(kind)).thenReturn(kind);
    doNothing().when(catalogServiceHelper).checkCrudRbac(any(), any(), any(), any(), any(), any());

    ScopeInfo accountScopeInfo = ScopeInfo.builder()
                                     .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                     .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                     .scopeType(ScopeLevel.ACCOUNT)
                                     .build();

    CatalogEntity parentEntity = InlineCatalogEntity.builder()
                                     .kind(kind)
                                     .identifier(identifier)
                                     .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                     .relations(Map.of("hasPart", Set.of("user:account/user1")))
                                     .build();

    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(accountScopeInfo)));
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfoCall);

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, kind, identifier))
        .thenReturn(Optional.of(parentEntity));

    when(catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(Pair.of(List.of(accountScopeInfo), Collections.emptyMap()));

    // Team-ownership fallback helpers (no permitted groups -> no indirect refs added).
    when(catalogServiceHelper.uniqueParentScopesForGroups(any())).thenReturn(Set.of(TEST_ACCOUNT_IDENTIFIER));
    when(catalogServiceHelper.checkEntitiesRbacByKind(any(), anyList(), eq(GROUP_KIND)))
        .thenReturn(Collections.emptyList());
    when(catalogEntityRepository.findAllByParentUniqueIdInAndKindInAndOwnerIn(anyList(), anyList(), anyList()))
        .thenReturn(Collections.emptyList());

    when(catalogServiceHelper.checkEntitiesRbac(eq(TEST_ACCOUNT_IDENTIFIER), anyList()))
        .thenReturn(List.of("user:account/user1"));

    when(catalogServiceHelper.getUserFavoriteEntityRefs(any(), any(), any(), any())).thenReturn(null);

    CatalogEntity associatedEntity = InlineCatalogEntity.builder()
                                         .kind("user")
                                         .identifier("user1")
                                         .name("User One")
                                         .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                         .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
                                         .referenceType(ReferenceType.INLINE)
                                         .build();

    when(catalogEntityRepository.findEntitiesByRelationRefs(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(associatedEntity), PageRequest.of(0, 10), 1));

    when(kindServiceHelper.findByAccountIdentifierIn(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(List.of(BuiltInKindEntity.builder().identifier("user").icon("user-icon").build()));

    when(catalogServiceHelper.resolveOwner(any(), anyList())).thenReturn(List.of(associatedEntity));

    // The associated user is non-inheritable, so the team fallback is skipped; direct access still grants it.
    when(catalogServiceHelper.getKindScopeIdentifier(anyString())).thenReturn(Triple.of(USER_KIND, "account", "user1"));
    when(catalogServiceHelper.isInheritableKind(USER_KIND)).thenReturn(false);

    GetEntitiesDTO result = catalogService.getEntityAssociations(TEST_ACCOUNT_IDENTIFIER, null, null, kind, identifier,
        relations, 0, 10, null, null, null, null, null, null, null, null, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);
    EntityResponse response = result.getEntityResponses().get(0);
    assertThat(response.getScorecards()).isNotNull();
    assertThat(response.getScorecards().getAverage()).isNull();
    assertThat(response.getScorecards().getScores()).isEmpty();
    verify(scorecardScoreHelper, times(0)).fetchScoresForEntities(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetEntityAssociationsRetainsTeamOwnedAssociations() throws IOException {
    // The associated entity is accessible only indirectly, because it is owned by a team the user belongs to. It
    // must still be returned even though the user has no direct RBAC access to it.
    String kind = "system";
    String identifier = "my-system";
    String relations = "hasPart";
    String teamOwnedRef = "component:account/service-a";

    when(catalogServiceHelper.validateAndSanitizeKind(kind)).thenReturn(kind);
    doNothing().when(catalogServiceHelper).checkCrudRbac(any(), any(), any(), any(), any(), any());

    ScopeInfo accountScopeInfo = ScopeInfo.builder()
                                     .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                     .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                     .scopeType(ScopeLevel.ACCOUNT)
                                     .build();

    CatalogEntity parentEntity = InlineCatalogEntity.builder()
                                     .kind(kind)
                                     .identifier(identifier)
                                     .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                     .relations(Map.of("hasPart", Set.of(teamOwnedRef)))
                                     .build();

    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(accountScopeInfo)));
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfoCall);

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, kind, identifier))
        .thenReturn(Optional.of(parentEntity));

    when(catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(Pair.of(List.of(accountScopeInfo), Collections.emptyMap()));

    // No direct access to the associated entity.
    when(catalogServiceHelper.checkEntitiesRbac(eq(TEST_ACCOUNT_IDENTIFIER), anyList()))
        .thenReturn(Collections.emptyList());

    // The association points at an inheritable (component) kind, so it is eligible for indirect team access.
    when(catalogServiceHelper.getKindScopeIdentifier(teamOwnedRef))
        .thenReturn(Triple.of(COMPONENT_KIND, "account", "service-a"));
    when(catalogServiceHelper.isInheritableKind(COMPONENT_KIND)).thenReturn(true);

    // Team-ownership fallback grants indirect access: user belongs to team1, which owns service-a.
    when(catalogServiceHelper.uniqueParentScopesForGroups(any())).thenReturn(Set.of(TEST_ACCOUNT_IDENTIFIER));
    when(catalogServiceHelper.checkEntitiesRbacByKind(any(), anyList(), eq(GROUP_KIND)))
        .thenReturn(List.of("group:account/team1"));
    CatalogEntity teamOwnedEntity = InlineCatalogEntity.builder()
                                        .kind(COMPONENT_KIND)
                                        .identifier("service-a")
                                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                        .build();
    when(catalogEntityRepository.findAllByParentUniqueIdInAndKindInAndOwnerIn(
             List.of(TEST_ACCOUNT_IDENTIFIER), List.of(COMPONENT_KIND), List.of("group:account/team1")))
        .thenReturn(List.of(teamOwnedEntity));

    when(catalogServiceHelper.getUserFavoriteEntityRefs(any(), any(), any(), any())).thenReturn(null);

    CatalogEntity associatedEntity = InlineCatalogEntity.builder()
                                         .kind(COMPONENT_KIND)
                                         .identifier("service-a")
                                         .name("Service A")
                                         .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                         .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
                                         .referenceType(ReferenceType.INLINE)
                                         .build();

    ArgumentCaptor<List<String>> allowedRefsCaptor = ArgumentCaptor.forClass(List.class);
    when(catalogEntityRepository.findEntitiesByRelationRefs(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(List.of(associatedEntity), PageRequest.of(0, 10), 1));

    when(kindServiceHelper.findByAccountIdentifierIn(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(List.of(BuiltInKindEntity.builder().identifier(COMPONENT_KIND).icon("icon").build()));

    when(catalogServiceHelper.resolveOwner(any(), anyList())).thenReturn(List.of(associatedEntity));

    ScopeTopology topology = mock(ScopeTopology.class);
    when(catalogScopeResolver.getOrBuildTopology(TEST_ACCOUNT_IDENTIFIER)).thenReturn(topology);
    when(scorecardService.getAllScorecardAndChecks(TEST_ACCOUNT_IDENTIFIER, null)).thenReturn(Collections.emptyList());
    when(scorecardScoreHelper.fetchScoresForEntities(any(), anyList(), any(), any()))
        .thenReturn(Collections.emptyMap());

    GetEntitiesDTO result = catalogService.getEntityAssociations(TEST_ACCOUNT_IDENTIFIER, null, null, kind, identifier,
        relations, 0, 10, null, null, null, null, null, null, null, null, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).hasSize(1);

    verify(catalogEntityRepository)
        .findEntitiesByRelationRefs(eq(TEST_ACCOUNT_IDENTIFIER), allowedRefsCaptor.capture(), any(), anyList(), any(),
            any(), any(), any(), any(), any(), any());
    assertThat(allowedRefsCaptor.getValue()).contains(teamOwnedRef);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetEntityAssociationsExcludesNonInheritableTeamOwnedAssociation() throws IOException {
    // The association points at a NON-inheritable kind (workflow) owned by a team the user belongs to. Team ownership
    // must NOT grant indirect access to non-inheritable kinds -- they require direct access -- so the workflow must be
    // excluded and the team-ownership fallback query must never run for it.
    String kind = "system";
    String identifier = "my-system";
    String relations = "hasPart";
    String teamOwnedWorkflowRef = "workflow:account/wf-a";

    when(catalogServiceHelper.validateAndSanitizeKind(kind)).thenReturn(kind);
    doNothing().when(catalogServiceHelper).checkCrudRbac(any(), any(), any(), any(), any(), any());

    ScopeInfo accountScopeInfo = ScopeInfo.builder()
                                     .uniqueId(TEST_ACCOUNT_IDENTIFIER)
                                     .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                     .scopeType(ScopeLevel.ACCOUNT)
                                     .build();

    CatalogEntity parentEntity = InlineCatalogEntity.builder()
                                     .kind(kind)
                                     .identifier(identifier)
                                     .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                     .relations(Map.of("hasPart", Set.of(teamOwnedWorkflowRef)))
                                     .build();

    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(accountScopeInfo)));
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfoCall);

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier(TEST_ACCOUNT_IDENTIFIER, kind, identifier))
        .thenReturn(Optional.of(parentEntity));

    when(catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(eq(TEST_ACCOUNT_IDENTIFIER), any(), any()))
        .thenReturn(Pair.of(List.of(accountScopeInfo), Collections.emptyMap()));

    // No direct access to the associated workflow.
    when(catalogServiceHelper.checkEntitiesRbac(eq(TEST_ACCOUNT_IDENTIFIER), anyList()))
        .thenReturn(Collections.emptyList());

    // workflow is non-inheritable, so the fallback must be skipped entirely for it.
    when(catalogServiceHelper.getKindScopeIdentifier(teamOwnedWorkflowRef))
        .thenReturn(Triple.of(WORKFLOW_KIND, "account", "wf-a"));
    when(catalogServiceHelper.isInheritableKind(WORKFLOW_KIND)).thenReturn(false);

    GetEntitiesDTO result = catalogService.getEntityAssociations(TEST_ACCOUNT_IDENTIFIER, null, null, kind, identifier,
        relations, 0, 10, null, null, null, null, null, null, null, null, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getEntityResponses()).isEmpty();
    assertThat(result.getTotalElements()).isEqualTo(0L);

    // The team-ownership fallback query must never run for a non-inheritable association.
    verify(catalogEntityRepository, never())
        .findAllByParentUniqueIdInAndKindInAndOwnerIn(anyList(), anyList(), anyList());
    // No permitted refs remain, so the paged relation lookup is short-circuited too.
    verify(catalogEntityRepository, never())
        .findEntitiesByRelationRefs(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveVanityUiBaseUrlReplacesHostOnly() throws Exception {
    String vanityUrl = "https://idp-internal-prod0.harness.io";
    String defaultUrl = "https://app.harness.io/ng/";
    AccountDTO accountDTO = AccountDTO.builder().subdomainURL(vanityUrl).build();
    when(idpCommonService.getAccountDTO(TEST_ACCOUNT_IDENTIFIER)).thenReturn(accountDTO);

    java.lang.reflect.Method method =
        CatalogServiceImpl.class.getDeclaredMethod("resolveVanityUiBaseUrl", String.class, String.class);
    method.setAccessible(true);
    String result = (String) method.invoke(catalogService, TEST_ACCOUNT_IDENTIFIER, defaultUrl);

    assertThat(result).isEqualTo("https://idp-internal-prod0.harness.io/ng/");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveVanityUiBaseUrlFallsBackWhenNoSubdomain() throws Exception {
    String defaultUrl = "https://app.harness.io/ng/";
    AccountDTO accountDTO = AccountDTO.builder().subdomainURL(null).build();
    when(idpCommonService.getAccountDTO(TEST_ACCOUNT_IDENTIFIER)).thenReturn(accountDTO);

    java.lang.reflect.Method method =
        CatalogServiceImpl.class.getDeclaredMethod("resolveVanityUiBaseUrl", String.class, String.class);
    method.setAccessible(true);
    String result = (String) method.invoke(catalogService, TEST_ACCOUNT_IDENTIFIER, defaultUrl);

    assertThat(result).isEqualTo(defaultUrl);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveVanityUiBaseUrlPrependsHttpsForBareHostname() throws Exception {
    String defaultUrl = "https://app.harness.io/ng/";
    AccountDTO accountDTO = AccountDTO.builder().subdomainURL("idp-internal-prod0.harness.io").build();
    when(idpCommonService.getAccountDTO(TEST_ACCOUNT_IDENTIFIER)).thenReturn(accountDTO);

    java.lang.reflect.Method method =
        CatalogServiceImpl.class.getDeclaredMethod("resolveVanityUiBaseUrl", String.class, String.class);
    method.setAccessible(true);
    String result = (String) method.invoke(catalogService, TEST_ACCOUNT_IDENTIFIER, defaultUrl);

    assertThat(result).isEqualTo("https://idp-internal-prod0.harness.io/ng/");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testResolveVanityUiBaseUrlFallsBackOnException() throws Exception {
    String defaultUrl = "https://app.harness.io/ng/";
    when(idpCommonService.getAccountDTO(TEST_ACCOUNT_IDENTIFIER)).thenThrow(new RuntimeException("service down"));

    java.lang.reflect.Method method =
        CatalogServiceImpl.class.getDeclaredMethod("resolveVanityUiBaseUrl", String.class, String.class);
    method.setAccessible(true);
    String result = (String) method.invoke(catalogService, TEST_ACCOUNT_IDENTIFIER, defaultUrl);

    assertThat(result).isEqualTo(defaultUrl);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateGitMetadataThrowsOnLeadingSlashFilePath() {
    String entityRef = "component:account/test-entity";
    when(catalogServiceHelper.getKindScopeIdentifier(entityRef))
        .thenReturn(Triple.of("Component", "account", "test-entity"));
    when(catalogServiceHelper.validateAndSanitizeKind("Component")).thenReturn("component");
    when(catalogServiceHelper.validateAndSanitizeIdentifier("test-entity")).thenReturn("test-entity");
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build();
    when(catalogScopeResolver.resolveSingleScopeInfo(TEST_ACCOUNT_IDENTIFIER, "account")).thenReturn(scopeInfo);
    when(catalogServiceHelper.catalogEntity(TEST_ACCOUNT_IDENTIFIER, "component", "test-entity"))
        .thenReturn(GitReferencedCatalogEntity.builder()
                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                        .identifier("test-entity")
                        .kind("component")
                        .storeType(StoreType.REMOTE)
                        .filePath(".harness/catalog-info.yaml")
                        .build());
    doNothing().when(catalogServiceHelper).checkRbacWithOwnerFallback(anyString(), anyString(), any(), anyString());
    doThrow(new InvalidRequestException(IDPGitXHelper.INVALID_FILE_PATH_ERROR))
        .when(idpGitXHelper)
        .validateFilePath("/.harness/catalog-info.yaml");

    GitMetadataUpdateRequest body = new GitMetadataUpdateRequest();
    body.setFilePath("/.harness/catalog-info.yaml");

    assertThatThrownBy(() -> catalogService.updateGitMetadata(TEST_ACCOUNT_IDENTIFIER, null, null, entityRef, body))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(IDPGitXHelper.INVALID_FILE_PATH_ERROR);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUpdateGitMetadataThrowsOnMultipleLeadingSlashesFilePath() {
    String entityRef = "component:account/test-entity";
    when(catalogServiceHelper.getKindScopeIdentifier(entityRef))
        .thenReturn(Triple.of("Component", "account", "test-entity"));
    when(catalogServiceHelper.validateAndSanitizeKind("Component")).thenReturn("component");
    when(catalogServiceHelper.validateAndSanitizeIdentifier("test-entity")).thenReturn("test-entity");
    ScopeInfo scopeInfo =
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build();
    when(catalogScopeResolver.resolveSingleScopeInfo(TEST_ACCOUNT_IDENTIFIER, "account")).thenReturn(scopeInfo);
    when(catalogServiceHelper.catalogEntity(TEST_ACCOUNT_IDENTIFIER, "component", "test-entity"))
        .thenReturn(GitReferencedCatalogEntity.builder()
                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                        .identifier("test-entity")
                        .kind("component")
                        .storeType(StoreType.REMOTE)
                        .filePath(".harness/catalog-info.yaml")
                        .build());
    doNothing().when(catalogServiceHelper).checkRbacWithOwnerFallback(anyString(), anyString(), any(), anyString());
    doThrow(new InvalidRequestException(IDPGitXHelper.INVALID_FILE_PATH_ERROR))
        .when(idpGitXHelper)
        .validateFilePath("//.harness/catalog-info.yaml");

    GitMetadataUpdateRequest body = new GitMetadataUpdateRequest();
    body.setFilePath("//.harness/catalog-info.yaml");

    assertThatThrownBy(() -> catalogService.updateGitMetadata(TEST_ACCOUNT_IDENTIFIER, null, null, entityRef, body))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(IDPGitXHelper.INVALID_FILE_PATH_ERROR);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
