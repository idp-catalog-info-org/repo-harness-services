/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.helpers;

import static io.harness.eventsframework.EventsFrameworkMetadataConstants.UPDATE_ACTION;
import static io.harness.idp.catalog.utils.CatalogUtils.parseBackstageEntityReferenceToCatalogRelationRef;
import static io.harness.idp.catalog.utils.Constants.API_KIND;
import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.idp.catalog.utils.Constants.HARNESS_API_VERSION;
import static io.harness.idp.catalog.utils.Constants.HAS_LEADER;
import static io.harness.idp.catalog.utils.Constants.LEADER_OF;
import static io.harness.idp.catalog.utils.Constants.MEMBER_OF;
import static io.harness.idp.catalog.utils.Constants.METADATA_ANNOTATIONS_BACKSTAGE_IO_SOURCE_LOCATION;
import static io.harness.idp.catalog.utils.Constants.USER_KIND;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.idp.common.CommonUtils.from;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.ROUNAK;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.events.producers.IdpServiceMiscRedisProducer;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.ng.core.dto.UserGroupFilterDTO;
import io.harness.ng.core.user.remote.dto.UserFilter;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.rule.Owner;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;
import io.harness.springdata.TransactionHelper;
import io.harness.usergroups.UserGroupClient;
import io.harness.userng.remote.UserNGClient;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class IDPToHarnessHelperTest extends CategoryTest {
  public static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static Gson gson = new Gson();

  AutoCloseable openMocks;
  @Spy @InjectMocks IDPToHarnessHelper idpToHarnessHelper;
  @Mock NamespaceService namespaceService;
  private Call<Object> call;
  @Mock BackstageResourceClient backstageResourceClient;
  @Mock UserNGClient userNGClient;
  @Mock UserGroupClient userGroupClient;
  @Mock ScopeInfoClient scopeInfoClient;
  @Mock CatalogEntityRepository catalogEntityRepository;
  @Mock TransactionHelper transactionHelper;
  @Mock IdpCommonService idpCommonService;
  @Mock CatalogServiceHelper catalogServiceHelper;
  @Mock IdpServiceMiscRedisProducer idpServiceMiscRedisProducer;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    call = mock(Call.class);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testMigrateCatalogEntitiesFromBackstageToHarnessAsInlineEntitiesInvalidAccount() {
    when(namespaceService.getEntityForAccountIdentifier("invalidAccount")).thenReturn(Optional.empty());
    idpToHarnessHelper.validateAndMigrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities("invalidAccount");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testMigrateCatalogEntitiesFromBackstageToHarnessAsInlineEntitiesForAccount() {
    when(namespaceService.getEntityForAccountIdentifier("invalidAccount")).thenReturn(Optional.empty());
    idpToHarnessHelper.migrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities("invalidAccount");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testMigrateCatalogEntitiesFromBackstageToHarnessAsInlineEntitiesAlreadyCompleted() {
    when(namespaceService.getEntityForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(NamespaceEntity.builder()
                                    .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                    .metadata(NamespaceEntity.Metadata.builder()
                                                  .migrateCatalogEntitiesFromBackstageToHarnessCompleted(true)
                                                  .build())
                                    .build()));
    idpToHarnessHelper.validateAndMigrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(TEST_ACCOUNT_IDENTIFIER);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testBuildInlineCatalogEntityForMigrationInvalidType() {
    idpToHarnessHelper.buildInlineCatalogEntityForMigration(TEST_ACCOUNT_IDENTIFIER, "invalid", null, null);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testBuildInlineCatalogEntityForMigrationUser() {
    UserMetadataDTO userMetadataDTO = getUsers().body().getData().getContent().get(0);
    InlineCatalogEntity inlineCatalogEntity =
        idpToHarnessHelper.buildInlineCatalogEntityForMigration(TEST_ACCOUNT_IDENTIFIER, "user", userMetadataDTO, null);
    assertThat(inlineCatalogEntity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(inlineCatalogEntity.getOrgIdentifier()).isEqualTo(null);
    assertThat(inlineCatalogEntity.getProjectIdentifier()).isEqualTo(null);
    assertThat(inlineCatalogEntity.getIdentifier()).isEqualTo(userMetadataDTO.getEmail());
    assertThat(inlineCatalogEntity.getReferenceType()).isEqualTo(ReferenceType.INLINE);
    assertThat(inlineCatalogEntity.getApiVersion()).isEqualTo(HARNESS_API_VERSION);
    assertThat(inlineCatalogEntity.getKind()).isEqualTo(USER_KIND);
    assertThat(inlineCatalogEntity.getType()).isEqualTo(null);
    assertThat(inlineCatalogEntity.getName()).isEqualTo(userMetadataDTO.getName());
    assertThat(inlineCatalogEntity.getMetadata().get("uuid")).isEqualTo(userMetadataDTO.getUuid());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testBuildInlineCatalogEntityForMigrationUserGroup() {
    UserGroupDTO userGroupDTO = getUserGroups().body().getData().getContent().get(0);
    InlineCatalogEntity inlineCatalogEntity = idpToHarnessHelper.buildInlineCatalogEntityForMigration(
        TEST_ACCOUNT_IDENTIFIER, "user_group", userGroupDTO, null);
    assertThat(inlineCatalogEntity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(inlineCatalogEntity.getOrgIdentifier()).isEqualTo(null);
    assertThat(inlineCatalogEntity.getProjectIdentifier()).isEqualTo(null);
    assertThat(inlineCatalogEntity.getIdentifier()).isEqualTo(userGroupDTO.getIdentifier());
    assertThat(inlineCatalogEntity.getReferenceType()).isEqualTo(ReferenceType.INLINE);
    assertThat(inlineCatalogEntity.getApiVersion()).isEqualTo(HARNESS_API_VERSION);
    assertThat(inlineCatalogEntity.getKind()).isEqualTo(GROUP_KIND);
    assertThat(inlineCatalogEntity.getType()).isEqualTo("team");
    assertThat(inlineCatalogEntity.getName()).isEqualTo(userGroupDTO.getName());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testBuildInlineCatalogEntityForMigrationUserAPI() {
    Map<String, Object> component =
        ((List<Map<String, Object>>) backstageCatalogEntities().body())
            .stream()
            .filter(backstageCatalogEntity -> backstageCatalogEntity.get("kind").equals("API"))
            .findFirst()
            .get();
    String sourceLocation = from(component, METADATA_ANNOTATIONS_BACKSTAGE_IO_SOURCE_LOCATION, String.class);
    InlineCatalogEntity inlineCatalogEntity = idpToHarnessHelper.buildInlineCatalogEntityForMigration(
        TEST_ACCOUNT_IDENTIFIER, "api_component_resource_template", component, null);
    assertThat(inlineCatalogEntity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(inlineCatalogEntity.getOrgIdentifier()).isEqualTo(null);
    assertThat(inlineCatalogEntity.getProjectIdentifier()).isEqualTo(null);
    assertThat(inlineCatalogEntity.getIdentifier()).isEqualTo(from(component, "metadata.name", String.class));
    assertThat(inlineCatalogEntity.getReferenceType()).isEqualTo(ReferenceType.INLINE);
    assertThat(inlineCatalogEntity.getApiVersion()).isEqualTo(HARNESS_API_VERSION);
    assertThat(inlineCatalogEntity.getKind()).isEqualTo(API_KIND);
    assertThat(inlineCatalogEntity.getType()).isEqualTo("openapi");
    assertThat(inlineCatalogEntity.getName()).isEqualTo(from(component, "metadata.name", String.class));
    assertThat(inlineCatalogEntity.getOwner())
        .isEqualTo(
            parseBackstageEntityReferenceToCatalogRelationRef(from(component, "spec.owner", String.class), null));
    assertThat(inlineCatalogEntity.getSourceLocation()).isEqualTo(sourceLocation);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testBuildInlineCatalogEntityForMigrationUserComponent() {
    Map<String, Object> component =
        ((List<Map<String, Object>>) backstageCatalogEntities().body())
            .stream()
            .filter(backstageCatalogEntity -> backstageCatalogEntity.get("kind").equals("Component"))
            .findFirst()
            .get();
    String sourceLocation = from(component, METADATA_ANNOTATIONS_BACKSTAGE_IO_SOURCE_LOCATION, String.class);
    InlineCatalogEntity inlineCatalogEntity = idpToHarnessHelper.buildInlineCatalogEntityForMigration(
        TEST_ACCOUNT_IDENTIFIER, "api_component_resource_template", component, null);
    assertThat(inlineCatalogEntity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(inlineCatalogEntity.getOrgIdentifier()).isEqualTo(null);
    assertThat(inlineCatalogEntity.getProjectIdentifier()).isEqualTo(null);
    assertThat(inlineCatalogEntity.getIdentifier()).isEqualTo(from(component, "metadata.name", String.class));
    assertThat(inlineCatalogEntity.getReferenceType()).isEqualTo(ReferenceType.INLINE);
    assertThat(inlineCatalogEntity.getApiVersion()).isEqualTo(HARNESS_API_VERSION);
    assertThat(inlineCatalogEntity.getKind()).isEqualTo(COMPONENT_KIND);
    assertThat(inlineCatalogEntity.getType()).isEqualTo("Service");
    assertThat(inlineCatalogEntity.getName()).isEqualTo(from(component, "metadata.name", String.class));
    assertThat(inlineCatalogEntity.getOwner())
        .isEqualTo(
            parseBackstageEntityReferenceToCatalogRelationRef(from(component, "spec.owner", String.class), null));
    assertThat(inlineCatalogEntity.getSourceLocation()).isEqualTo(sourceLocation);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testBuildInlineCatalogEntityForMigrationStripsLeaderRelationsFromSpec() {
    Map<String, Object> component = Map.of("metadata", Map.of("name", "idp-service"), "kind", "Component", "spec",
        Map.of("type", "service", "owner", "user:default/test", LEADER_OF, List.of("group:default/idp-team"),
            HAS_LEADER, List.of("user:default/leader1")));

    InlineCatalogEntity inlineCatalogEntity = idpToHarnessHelper.buildInlineCatalogEntityForMigration(
        TEST_ACCOUNT_IDENTIFIER, "api_component_resource_template", component, null);

    assertThat(inlineCatalogEntity.getSpec()).doesNotContainKey(LEADER_OF);
    assertThat(inlineCatalogEntity.getSpec()).doesNotContainKey(HAS_LEADER);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testBuildInlineCatalogEntityForMigrationUserTemplate() {
    Map<String, Object> component =
        ((List<Map<String, Object>>) backstageCatalogEntities().body())
            .stream()
            .filter(backstageCatalogEntity -> backstageCatalogEntity.get("kind").equals("Template"))
            .findFirst()
            .get();
    Map<String, String> usernameAndEmailMapping = new HashMap<>();
    usernameAndEmailMapping.put("test", "test@harness.io");
    String sourceLocation = from(component, METADATA_ANNOTATIONS_BACKSTAGE_IO_SOURCE_LOCATION, String.class);
    InlineCatalogEntity inlineCatalogEntity = idpToHarnessHelper.buildInlineCatalogEntityForMigration(
        TEST_ACCOUNT_IDENTIFIER, "api_component_resource_template", component, usernameAndEmailMapping);
    assertThat(inlineCatalogEntity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(inlineCatalogEntity.getOrgIdentifier()).isEqualTo(null);
    assertThat(inlineCatalogEntity.getProjectIdentifier()).isEqualTo(null);
    assertThat(inlineCatalogEntity.getIdentifier()).isEqualTo(from(component, "metadata.name", String.class));
    assertThat(inlineCatalogEntity.getReferenceType()).isEqualTo(ReferenceType.INLINE);
    assertThat(inlineCatalogEntity.getApiVersion()).isEqualTo(HARNESS_API_VERSION);
    assertThat(inlineCatalogEntity.getKind()).isEqualTo(WORKFLOW_KIND);
    assertThat(inlineCatalogEntity.getType()).isEqualTo("service");
    assertThat(inlineCatalogEntity.getName()).isEqualTo(from(component, "metadata.title", String.class));
    assertThat(inlineCatalogEntity.getOwner())
        .isEqualTo(parseBackstageEntityReferenceToCatalogRelationRef(
            from(component, "spec.owner", String.class), usernameAndEmailMapping));
    assertThat(inlineCatalogEntity.getSourceLocation()).isEqualTo(sourceLocation);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testRemoveRelationsForUsers() throws Exception {
    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfoCall);

    String userGroupId = "group:test-ug";
    Map<String, Set<String>> relations = new HashMap<>();
    relations.put(MEMBER_OF, new HashSet<>(Set.of(userGroupId)));
    InlineCatalogEntity inlineCatalogEntity =
        InlineCatalogEntity.builder().relations(relations).kind(USER_KIND).build();
    when(catalogEntityRepository.findAllByParentUniqueIdAndKind(any(), any())).thenReturn(List.of(inlineCatalogEntity));
    List<CatalogEntity> catalogEntities =
        idpToHarnessHelper.removeRelationsForUsers(TEST_ACCOUNT_IDENTIFIER, userGroupId);
    assertEquals(catalogEntities.size(), 1);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testMigrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities() throws IOException {
    when(namespaceService.getEntityForAccountIdentifier(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Optional.of(NamespaceEntity.builder()
                                    .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                    .metadata(NamespaceEntity.Metadata.builder().build())
                                    .build()));

    Response<Object> response = backstageCatalogEntities();
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntities(anyString())).thenReturn(call);

    Response<ResponseDTO<io.harness.ng.beans.PageResponse<UserMetadataDTO>>> usersResponse = getUsers();
    Call<ResponseDTO<io.harness.ng.beans.PageResponse<UserMetadataDTO>>> usersCall = mock(Call.class);
    when(usersCall.execute()).thenReturn(usersResponse);
    Call<ResponseDTO<io.harness.ng.beans.PageResponse<UserMetadataDTO>>> usersCallEmptyResponse = mock(Call.class);
    when(usersCallEmptyResponse.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(io.harness.ng.beans.PageResponse.<UserMetadataDTO>builder().build())));
    when(userNGClient.userBatch(TEST_ACCOUNT_IDENTIFIER, null, null, 0, 100, UserFilter.builder().build()))
        .thenReturn(usersCall);
    when(userNGClient.userBatch(TEST_ACCOUNT_IDENTIFIER, null, null, 1, 100, UserFilter.builder().build()))
        .thenReturn(usersCallEmptyResponse);

    Response<ResponseDTO<io.harness.ng.beans.PageResponse<UserGroupDTO>>> userGroupsResponse = getUserGroups();
    Call<ResponseDTO<io.harness.ng.beans.PageResponse<UserGroupDTO>>> userGroupsCall = mock(Call.class);
    when(userGroupsCall.execute()).thenReturn(userGroupsResponse);
    Call<ResponseDTO<io.harness.ng.beans.PageResponse<UserGroupDTO>>> userGroupsCallEmptyResponse = mock(Call.class);
    when(userGroupsCallEmptyResponse.execute())
        .thenReturn(Response.success(
            ResponseDTO.newResponse(io.harness.ng.beans.PageResponse.<UserGroupDTO>builder().build())));
    when(userGroupClient.getUserGroups(TEST_ACCOUNT_IDENTIFIER,
             UserGroupFilterDTO.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).build(), 0, 100, null))
        .thenReturn(userGroupsCall);
    when(userGroupClient.getUserGroups(TEST_ACCOUNT_IDENTIFIER,
             UserGroupFilterDTO.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).build(), 1, 100, null))
        .thenReturn(userGroupsCallEmptyResponse);

    Response<ResponseDTO<ScopeInfo>> scopeInfoResponse = Response.success(ResponseDTO.newResponse(
        ScopeInfo.builder().accountIdentifier(TEST_ACCOUNT_IDENTIFIER).uniqueId(TEST_ACCOUNT_IDENTIFIER).build()));
    Call<ResponseDTO<ScopeInfo>> scopeInfoCall = mock(Call.class);
    when(scopeInfoCall.execute()).thenReturn(scopeInfoResponse);
    when(scopeInfoClient.getScopeInfo(TEST_ACCOUNT_IDENTIFIER, null, null)).thenReturn(scopeInfoCall);

    when(transactionHelper.performTransaction(any())).thenAnswer(invocation -> {
      TransactionHelper.TransactionFunction<?> function = invocation.getArgument(0);
      return function.execute();
    });

    when(catalogEntityRepository.findAllByParentUniqueId(any()))
        .thenReturn(List.of(InlineCatalogEntity.builder()
                                .kind(GROUP_KIND)
                                .identifier("ug1")
                                .parentUniqueId(TEST_ACCOUNT_IDENTIFIER)
                                .build()));

    idpToHarnessHelper.validateAndMigrateCatalogEntitiesFromBackstageToHarnessAsInlineEntities(TEST_ACCOUNT_IDENTIFIER);

    verify(catalogEntityRepository, times(1)).saveAll(any());
    verify(namespaceService, times(1)).save(any());
    verify(transactionHelper, times(1)).performTransaction(any());
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testConvertBackstageToHarness() {
    doNothing().when(catalogServiceHelper).validateKindForCreateUpdateDelete(any());

    String convertedYaml = idpToHarnessHelper.convertBackstageToHarness(TEST_ACCOUNT_IDENTIFIER,
        "apiVersion: backstage.io/v1alpha1\n"
            + "kind: API\n"
            + "metadata:\n"
            + "  name: hello-world\n"
            + "  namespace: default\n"
            + "  description: Hello World example for gRPC\n"
            + "  annotations:\n"
            + "    backstage.io/managed-by-origin-location: url:https://app.harness.io/API/default/hello-world\n"
            + "    backstage.io/managed-by-location: url:https://app.harness.io/API/default/hello-world\n"
            + "  title: hello-world\n"
            + "spec:\n"
            + "  lifecycle: deprecated\n"
            + "  owner: team-c\n"
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
            + "  type: grpc\n");

    assertThat(convertedYaml)
        .isEqualTo("apiVersion: harness.io/v1\n"
            + "kind: API\n"
            + "type: grpc\n"
            + "identifier: hello_world\n"
            + "name: hello_world\n"
            + "owner: team_c\n"
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
            + "  description: Hello World example for gRPC\n");
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private Response<ResponseDTO<io.harness.ng.beans.PageResponse<UserMetadataDTO>>> getUsers() {
    io.harness.ng.beans.PageResponse<UserMetadataDTO> pageResponse =
        io.harness.ng.beans.PageResponse.<UserMetadataDTO>builder().build();
    List<UserMetadataDTO> userMetadataDTOS = new ArrayList<>();
    UserMetadataDTO userMetadataDTO1 =
        UserMetadataDTO.builder().uuid("uuid1").email("user1@user.com").name("user1").build();
    UserMetadataDTO userMetadataDTO2 =
        UserMetadataDTO.builder().uuid("uuid2").email("user2@user.com").name("user2").build();
    UserMetadataDTO userMetadataDTO3 =
        UserMetadataDTO.builder().uuid("uuid3").email("user3@user.com").name("user3").build();
    UserMetadataDTO userMetadataDTO4 =
        UserMetadataDTO.builder().uuid("uuid4").email("user3@abc.com").name("user3").build();
    userMetadataDTOS.add(userMetadataDTO1);
    userMetadataDTOS.add(userMetadataDTO2);
    userMetadataDTOS.add(userMetadataDTO3);
    userMetadataDTOS.add(userMetadataDTO4);
    pageResponse.setContent(userMetadataDTOS);
    return Response.success(ResponseDTO.newResponse(pageResponse));
  }

  private Response<ResponseDTO<io.harness.ng.beans.PageResponse<UserGroupDTO>>> getUserGroups() {
    io.harness.ng.beans.PageResponse<UserGroupDTO> pageResponse =
        io.harness.ng.beans.PageResponse.<UserGroupDTO>builder().build();
    List<UserGroupDTO> userGroups = new ArrayList<>();
    UserGroupDTO userGroupDTO1 =
        UserGroupDTO.builder().identifier("ug1").users(List.of("uuid1", "uuid2", "uuid3")).name("ug1").build();
    UserGroupDTO userGroupDTO2 =
        UserGroupDTO.builder().identifier("ug2").users(List.of("uuid1", "uuid2")).name("ug2").build();
    UserGroupDTO userGroupDTO3 = UserGroupDTO.builder().identifier("ug3").users(List.of("uuid1")).name("ug3").build();
    userGroups.add(userGroupDTO1);
    userGroups.add(userGroupDTO2);
    userGroups.add(userGroupDTO3);
    pageResponse.setContent(userGroups);
    return Response.success(ResponseDTO.newResponse(pageResponse));
  }

  private Response<Object> backstageCatalogEntities() {
    List<Map<String, Object>> backstageCatalogEntities = new ArrayList<>();
    Map<String, Object> backstageCatalogEntity = Map.of("metadata",
        Map.of("namespace", "default", "annotations",
            Map.of("backstage.io/managed-by-location",
                "url:https://github.com/test/Catalog/tree/main/test-account-access-template.yaml",
                "backstage.io/managed-by-origin-location",
                "url:https://github.com/test/Catalog/blob/main/test-account-access-template.yaml",
                "backstage.io/view-url", "https://github.com/test/Catalog/tree/main/test-account-access-template.yaml",
                "backstage.io/edit-url", "https://github.com/test/Catalog/edit/main/test-account-access-template.yaml",
                "backstage.io/source-location", "url:https://github.com/test/Catalog/tree/main/"),
            "name", "accountaccess", "title", "Provide access to Harness account", "description",
            "Template for providing access to Harness account", "tags", List.of("access", "harness"), "uid",
            "0edfcc78-d464-4165-a159-cf76c203db74", "etag", "446c4ee07400b5b2ba3d22ff2ed5d715ac9f3b24"),
        "apiVersion", "scaffolder.backstage.io/v1beta3", "kind", "Template", "spec",
        Map.of("owner", "user:default/test", "type", "service", "parameters1",
            List.of(Map.of("title", "Harness Details", "required", List.of("user_email", "account_id"), "properties",
                Map.of("account_id",
                    Map.of("title", "Harness account ID", "type", "string", "description",
                        "Harness account ID where you need access"),
                    "token",
                    Map.of("title", "Harness Token", "type", "string", "ui:widget", "password", "ui:field",
                        "HarnessAuthToken")))),
            "steps",
            List.of(Map.of("id", "trigger", "name", "Inviting user to account", "action",
                "trigger:harness-custom-pipeline", "input",
                Map.of("url",
                    "https://app.harness.io/ng/account/vpCkHKsDSxK9_KYfjCTMKA/cd/orgs/IDP_SAAS/projects/IDPSAASTEST/"
                        + "pipelines/grant_account_access/pipeline-studio/?storeType=INLINE",
                    "inputset",
                    Map.of("user_email", "${{ parameters.user_email }}", "account_id", "${{ parameters.account_id }}"),
                    "apikey", "${{ parameters.token }}"))),
            "output",
            Map.of("links",
                List.of(Map.of("title", "Pipeline Details", "url", "${{ steps.trigger.output.PipelineUrl }}")))),
        "relations",
        List.of(Map.of("type", "ownedBy", "targetRef", "user:default/test", "target",
            Map.of("kind", "user", "namespace", "default", "name", "test"))));
    String responseString = gson.toJson(backstageCatalogEntity);
    Map<String, Object> responseMap = gson.fromJson(responseString, new TypeToken<Map<String, Object>>() {}.getType());
    backstageCatalogEntities.add(responseMap);

    backstageCatalogEntity = Map.of("metadata",
        Map.of("namespace", "default", "annotations",
            Map.of("backstage.io/managed-by-location",
                "url:https://github.com/wings-software/harness-service-catalog-manifests/tree/main/harness-services/"
                    + "Apis/CCM/ce-nextgen-service.yaml",
                "backstage.io/managed-by-origin-location",
                "url:https://github.com/wings-software/harness-service-catalog-manifests/blob/main/harness-services/"
                    + "Apis/CCM/ce-nextgen-service.yaml",
                "backstage.io/view-url",
                "https://github.com/wings-software/harness-service-catalog-manifests/tree/main/harness-services/Apis/"
                    + "CCM/ce-nextgen-service.yaml",
                "backstage.io/edit-url",
                "https://github.com/wings-software/harness-service-catalog-manifests/edit/main/harness-services/Apis/"
                    + "CCM/ce-nextgen-service.yaml",
                "backstage.io/source-location",
                "url:https://github.com/wings-software/harness-service-catalog-manifests/tree/main/harness-services/"
                    + "Apis/CCM/"),
            "name", "ce-nextgen", "description", "The official CE NEXTGEN service REST APIs", "uid",
            "24427bdd-8052-4b6a-a8e9-90ca9c8bc9fc", "etag", "33583e05e588ce53752be729a5a2b8f703a58337"),
        "apiVersion", "backstage.io/v1alpha1", "kind", "API", "spec",
        Map.of("type", "openapi", "lifecycle", "production", "owner", "ccmplayacc", "definition", ""), "relations",
        List.of(Map.of("type", "apiConsumedBy", "targetRef", "component:default/ng-ce-ui", "target",
                    Map.of("kind", "component", "namespace", "default", "name", "ng-ce-ui")),
            Map.of("type", "apiProvidedBy", "targetRef", "component:default/ce-nextgen-service", "target",
                Map.of("kind", "component", "namespace", "default", "name", "ce-nextgen-service")),
            Map.of("type", "ownedBy", "targetRef", "group:default/ccmplayacc", "target",
                Map.of("kind", "group", "namespace", "default", "name", "ccmplayacc"))),
        "status",
        Map.of("items",
            List.of(Map.of("type", "backstage.io/catalog-processing", "level", "error", "message",
                "InputError: Processor PlaceholderProcessor threw an error while preprocessing; caused by Error: "
                    + "Placeholder $text could not read location https://app.harness.io/prod1/ccm/api/openapi.json, "
                    + "Error: "
                    + "https://app.harness.io/prod1/ccm/api/openapi.json x "
                    + "https://app.harness.io/gateway/code/api/v1/repos/prod1/prod1/+/raw/"
                    + "?routingId=prod1&git_ref=refs/"
                    + "heads//prod1/ccm/api, 401 Unauthorized",
                "error",
                Map.of("name", "InputError", "message",
                    "Processor PlaceholderProcessor threw an error while preprocessing; caused by Error: Placeholder "
                        + "$text could not read location https://app.harness.io/prod1/ccm/api/openapi.json, Error: "
                        + "https://app.harness.io/prod1/ccm/api/openapi.json x "
                        + "https://app.harness.io/gateway/code/api/v1/repos/prod1/prod1/+/raw/"
                        + "?routingId=prod1&git_ref=refs/heads//prod1/ccm/api, 401 Unauthorized",
                    "cause",
                    Map.of("name", "Error", "message",
                        "Placeholder $text could not read location https://app.harness.io/prod1/ccm/api/openapi.json, "
                            + "Error: https://app.harness.io/prod1/ccm/api/openapi.json x "
                            + "https://app.harness.io/gateway/code/api/v1/repos/prod1/prod1/+/raw/"
                            + "?routingId=prod1&git_ref=refs/heads//prod1/ccm/api, 401 Unauthorized",
                        "stack",
                        "Error: Placeholder $text could not read location "
                            + "https://app.harness.io/prod1/ccm/api/openapi.json, Error: "
                            + "https://app.harness.io/prod1/ccm/api/openapi.json x "
                            + "https://app.harness.io/gateway/code/api/v1/repos/prod1/prod1/+/raw/"
                            + "?routingId=prod1&git_ref=refs/heads//prod1/ccm/api, 401 Unauthorized\n    at "
                            + "readTextLocation "
                            + "(/app/node_modules/@backstage/plugin-catalog-backend/dist/cjs/"
                            + "CatalogBuilder-C7ANIkk3.cjs.js:580:11)\n    at process.processTicksAndRejections "
                            + "(node:internal/process/task_queues:95:5)\n    at async textPlaceholderResolver "
                            + "(/app/node_modules/@backstage/plugin-catalog-backend/dist/cjs/"
                            + "CatalogBuilder-C7ANIkk3.cjs.js:570:28)\n    at async process "
                            + "(/app/node_modules/@backstage/plugin-catalog-backend/dist/cjs/"
                            + "CatalogBuilder-C7ANIkk3.cjs.js:519:9)\n    at async Promise.all (index 3)\n    at async "
                            + "process "
                            + "(/app/node_modules/@backstage/plugin-catalog-backend/dist/cjs/"
                            + "CatalogBuilder-C7ANIkk3.cjs.js:494:25)\n    at async Promise.all (index 3)\n    at "
                            + "async "
                            + "process "
                            + "(/app/node_modules/@backstage/plugin-catalog-backend/dist/cjs/"
                            + "CatalogBuilder-C7ANIkk3.cjs.js:494:25)\n    at async "
                            + "PlaceholderProcessor.preProcessEntity "
                            + "(/app/node_modules/@backstage/plugin-catalog-backend/dist/cjs/"
                            + "CatalogBuilder-C7ANIkk3.cjs.js:530:22)\n    at async "
                            + "/app/node_modules/@backstage/plugin-catalog-backend/dist/cjs/"
                            + "CatalogBuilder-C7ANIkk3.cjs.js:3030:26"))))));
    responseString = gson.toJson(backstageCatalogEntity);
    responseMap = gson.fromJson(responseString, new TypeToken<Map<String, Object>>() {}.getType());
    backstageCatalogEntities.add(responseMap);

    backstageCatalogEntity = Map.of("metadata",
        Map.of("namespace", "default", "annotations",
            Map.ofEntries(Map.entry("backstage.io/managed-by-location",
                              "url:https://github.com/wings-software/harness-service-catalog-manifests/tree/main/"
                                  + "harness-services/Service/CHAOS/ep-chaos-experiment.yaml"),
                Map.entry("backstage.io/managed-by-origin-location",
                    "url:https://github.com/wings-software/harness-service-catalog-manifests/blob/main/"
                        + "harness-services/Service/**/*.yaml"),
                Map.entry("backstage.io/view-url",
                    "https://github.com/wings-software/harness-service-catalog-manifests/tree/main/harness-services/"
                        + "Service/CHAOS/ep-chaos-experiment.yaml"),
                Map.entry("backstage.io/edit-url",
                    "https://github.com/wings-software/harness-service-catalog-manifests/edit/main/harness-services/"
                        + "Service/CHAOS/ep-chaos-experiment.yaml"),
                Map.entry("backstage.io/source-location", "url:https://github.com/wings-software/litmus-go"),
                Map.entry("harness.io/cd-serviceId", ""),
                Map.entry("harness.io/project-url-stage",
                    "https://stage.harness.io/ng/account/wFHXHD0RRQWoO8tIZT5YVw/cd/orgs/Harness/projects/Operations/"
                        + "deployments"),
                Map.entry("harness.io/projects-stage", "Operations,RELEASEBUILDS"),
                Map.entry("harness.io/cd-serviceId-stage", ""), Map.entry("harness.io/ci-pipelineIds-stage", ""),
                Map.entry("backstage.io/kubernetes-label-selector", ""), Map.entry("jira/project-key", "CHAOS"),
                Map.entry("pagerduty.com/service-id", "PCE5986"),
                Map.entry("github.com/project-slug", "wings-software/litmus-go")),
            "name", "ep-chaos-experiment", "description",
            "Execution Plane Component - It execute the fault business logic, probes and generate the final result",
            "tags", List.of("go", "ep"), "links",
            List.of(Map.of("title", "repo", "url", "https://github.com/wings-software/litmus-go")), "uid",
            "0079a877-69a3-4394-8e19-248d42f5f587", "etag", "f7767c5158b61522c3724b65a5ecd672c819c655"),
        "kind", "Component", "spec",
        Map.of("type", "Service", "lifecycle", "production", "owner", "hceusers", "system", "chaos", "dependsOn",
            List.of("Component:ep-chaos-operator")),
        "apiVersion", "backstage.io/v1alpha1", "relations",
        List.of(Map.of("type", "dependsOn", "targetRef", "component:default/ep-chaos-operator", "target",
                    Map.of("kind", "component", "namespace", "default", "name", "ep-chaos-operator")),
            Map.of("type", "ownedBy", "targetRef", "group:default/hceusers", "target",
                Map.of("kind", "group", "namespace", "default", "name", "hceusers")),
            Map.of("type", "partOf", "targetRef", "system:default/chaos", "target",
                Map.of("kind", "system", "namespace", "default", "name", "chaos"))));
    responseString = gson.toJson(backstageCatalogEntity);
    responseMap = gson.fromJson(responseString, new TypeToken<Map<String, Object>>() {}.getType());
    backstageCatalogEntities.add(responseMap);

    return Response.success(backstageCatalogEntities);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void testSendCatalogEventsToRedis_publishesV3PerEntity() {
    CatalogEntity apiEntity = apiEntity("api-1");
    CatalogEntity componentEntity = InlineCatalogEntity.builder()
                                        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                        .kind(COMPONENT_KIND)
                                        .identifier("svc-1")
                                        .build();

    idpToHarnessHelper.sendCatalogEventsToRedis(List.of(apiEntity, componentEntity), UPDATE_ACTION);

    verify(idpServiceMiscRedisProducer, times(2)).publishIDPCatalogEntitiesToRedisV2(any(), any(), any(), any());
  }

  private CatalogEntity apiEntity(String identifier) {
    return InlineCatalogEntity.builder()
        .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
        .kind(API_KIND)
        .identifier(identifier)
        .build();
  }
}
