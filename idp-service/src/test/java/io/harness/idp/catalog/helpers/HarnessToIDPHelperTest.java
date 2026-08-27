/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.catalog.helpers;

import static io.harness.idp.catalog.utils.Constants.ANNOTATIONS;
import static io.harness.idp.catalog.utils.Constants.API_CONSUMED_BY;
import static io.harness.idp.catalog.utils.Constants.API_KIND;
import static io.harness.idp.catalog.utils.Constants.API_PROVIDED_BY;
import static io.harness.idp.catalog.utils.Constants.CHILD_OF;
import static io.harness.idp.catalog.utils.Constants.COMPONENT_KIND;
import static io.harness.idp.catalog.utils.Constants.CONSUMES_API;
import static io.harness.idp.catalog.utils.Constants.DEPENDENCY_OF;
import static io.harness.idp.catalog.utils.Constants.DEPENDS_ON;
import static io.harness.idp.catalog.utils.Constants.ENVIRONMENT_BLUEPRINT_KIND;
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.idp.catalog.utils.Constants.HARNESS_API_VERSION;
import static io.harness.idp.catalog.utils.Constants.HAS_LEADER;
import static io.harness.idp.catalog.utils.Constants.HAS_MEMBER;
import static io.harness.idp.catalog.utils.Constants.LEADER_OF;
import static io.harness.idp.catalog.utils.Constants.LIFECYCLE;
import static io.harness.idp.catalog.utils.Constants.MEMBER_OF;
import static io.harness.idp.catalog.utils.Constants.OWNED_BY;
import static io.harness.idp.catalog.utils.Constants.PARENT_OF;
import static io.harness.idp.catalog.utils.Constants.PART_OF;
import static io.harness.idp.catalog.utils.Constants.PROVIDES_API;
import static io.harness.idp.catalog.utils.Constants.RESOURCE_KIND;
import static io.harness.idp.catalog.utils.Constants.SUB_COMPONENT_OF;
import static io.harness.idp.catalog.utils.Constants.USER_KIND;
import static io.harness.idp.catalog.utils.Constants.UUID;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.rule.OwnerRule.ANDERSJOHNSEN;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.KOTA_KARTHIK;
import static io.harness.rule.OwnerRule.SATHISH;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.ng.core.dto.OrganizationDTO;
import io.harness.ng.core.dto.OrganizationResponse;
import io.harness.ng.core.dto.ProjectDTO;
import io.harness.ng.core.dto.ProjectResponse;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.role.dto.RoleAssignmentMetadataDTO;
import io.harness.ng.core.user.remote.dto.UserAggregateDTO;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.organization.remote.OrganizationClient;
import io.harness.project.remote.ProjectClient;
import io.harness.rule.Owner;
import io.harness.userng.remote.UserNGClient;

import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class HarnessToIDPHelperTest extends CategoryTest {
  public static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  AutoCloseable openMocks;
  @InjectMocks HarnessToIDPHelper harnessToIDPHelper;
  @Mock UserNGClient userNGClient;
  @Mock CatalogServiceHelper catalogServiceHelper;
  @Mock @Named("PRIVILEGED") ProjectClient projectClient;
  @Mock @Named("PRIVILEGED") OrganizationClient organizationClient;

  private static final List<InlineCatalogEntity> inlineCatalogEntities = constructInlineCatalogEntities();
  private static final String PROJECT_IDENTIFIER = "project-id";
  private static final String ORG_IDENTIFIER = "org-id";
  private static final String ORG_NAME = "org-name";
  private static final String PROJECT_NAME = "proj-name";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testBuildBackstageCatalogForComponent() {
    InlineCatalogEntity inlineComponentEntity = inlineCatalogEntities.get(0);
    Object backstageCatalog = harnessToIDPHelper.buildBackstageCatalog(inlineComponentEntity, false, true, false);
    String expected = "{"
        + "apiVersion=backstage.io/v1alpha1, "
        + "kind=Component, "
        + "metadata={name=idp-service, namespace=default, "
        + "annotations={backstage.io/managed-by-origin-location=url:https://github.com/idp-service.yaml, "
        + "backstage.io/managed-by-location=url:https://github.com/idp-service.yaml, "
        + "backstage.io/source-location=https://github.com, pager-duty=IDP}, description=IDP Service Catalog, "
        + "title=IDP Service, tags=[java, bazel]}, "
        + "spec={lifecycle=experimental, owner=user:default/admin.user.plus, providesApis=[api:default/idp-service], "
        + "subcomponentOf=component:default/devX, dependsOn=[component:default/ng-manager, "
        + "resource:default/ng-manager], "
        + "dependencyOf=[component:default/idp-admin], consumesApis=[api:default/ng-manager], type=service}"
        + "}";
    assertNotNull(backstageCatalog);
    assertEquals(expected, backstageCatalog.toString());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPopulateRelationsEmitsSubComponentOfForComponentTarget() {
    InlineCatalogEntity entity = new InlineCatalogEntity();
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setIdentifier("child-service");
    entity.setReferenceType(ReferenceType.INLINE);
    entity.setApiVersion(HARNESS_API_VERSION);
    entity.setKind(COMPONENT_KIND);
    entity.setType("service");
    entity.setName("Child Service");
    entity.setOwner("team-a");
    Map<String, Object> spec = new HashMap<>();
    spec.put(SUB_COMPONENT_OF, "parent-comp");
    entity.setSpec(spec);
    Map<String, Set<String>> relations = new HashMap<>();
    relations.put(PART_OF, Set.of("component:parent-comp"));
    relations.put(OWNED_BY, Set.of("group:team-a"));
    entity.setRelations(relations);

    when(catalogServiceHelper.getKindScopeIdentifier(anyString())).thenAnswer(invocation -> {
      String ref = invocation.getArgument(0);
      String kind = ref.contains(":") ? ref.substring(0, ref.indexOf(':')) : "component";
      String name = ref.contains("/") ? ref.substring(ref.indexOf('/') + 1) : ref;
      return Triple.of(kind, "account", name);
    });

    Object backstageCatalog = harnessToIDPHelper.buildBackstageCatalog(entity, true, false, false);
    String result = backstageCatalog.toString();
    assertThat(result).contains("type=" + SUB_COMPONENT_OF);
    assertThat(result).contains("targetRef=component:account/parent-comp");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPopulateRelationsEmitsPartOfForComponentTargetWithoutSubcomponentOf() {
    InlineCatalogEntity entity = new InlineCatalogEntity();
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setIdentifier("child-service");
    entity.setReferenceType(ReferenceType.INLINE);
    entity.setApiVersion(HARNESS_API_VERSION);
    entity.setKind(COMPONENT_KIND);
    entity.setType("service");
    entity.setName("Child Service");
    entity.setOwner("team-a");
    Map<String, Set<String>> relations = new HashMap<>();
    relations.put(PART_OF, Set.of("component:parent-comp"));
    relations.put(OWNED_BY, Set.of("group:team-a"));
    entity.setRelations(relations);

    when(catalogServiceHelper.getKindScopeIdentifier(anyString())).thenAnswer(invocation -> {
      String ref = invocation.getArgument(0);
      String kind = ref.contains(":") ? ref.substring(0, ref.indexOf(':')) : "component";
      String name = ref.contains("/") ? ref.substring(ref.indexOf('/') + 1) : ref;
      return Triple.of(kind, "account", name);
    });

    Object backstageCatalog = harnessToIDPHelper.buildBackstageCatalog(entity, true, false, false);
    String result = backstageCatalog.toString();
    assertThat(result).contains("type=" + PART_OF);
    assertThat(result).contains("targetRef=component:account/parent-comp");
    assertThat(result).doesNotContain(SUB_COMPONENT_OF);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testPopulateRelationsEmitsPartOfForSystemTarget() {
    InlineCatalogEntity entity = new InlineCatalogEntity();
    entity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    entity.setIdentifier("my-service");
    entity.setReferenceType(ReferenceType.INLINE);
    entity.setApiVersion(HARNESS_API_VERSION);
    entity.setKind(COMPONENT_KIND);
    entity.setType("service");
    entity.setName("My Service");
    entity.setOwner("team-a");
    Map<String, Set<String>> relations = new HashMap<>();
    relations.put(PART_OF, Set.of("system:my-system"));
    relations.put(OWNED_BY, Set.of("group:team-a"));
    entity.setRelations(relations);

    when(catalogServiceHelper.getKindScopeIdentifier(anyString())).thenAnswer(invocation -> {
      String ref = invocation.getArgument(0);
      String kind = ref.contains(":") ? ref.substring(0, ref.indexOf(':')) : "system";
      String name = ref.contains("/") ? ref.substring(ref.indexOf('/') + 1) : ref;
      return Triple.of(kind, "account", name);
    });

    Object backstageCatalog = harnessToIDPHelper.buildBackstageCatalog(entity, true, false, false);
    String result = backstageCatalog.toString();
    assertThat(result).contains("type=" + PART_OF);
    assertThat(result).contains("targetRef=system:account/my-system");
    assertThat(result).doesNotContain(SUB_COMPONENT_OF);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testBuildBackstageCatalogForComponentForProjectScope() throws Exception {
    InlineCatalogEntity inlineComponentEntity = inlineCatalogEntities.get(0);
    inlineComponentEntity.setProjectIdentifier(PROJECT_IDENTIFIER);
    inlineComponentEntity.setOrgIdentifier(ORG_IDENTIFIER);

    Call<ResponseDTO<Optional<OrganizationResponse>>> orgDTOCall = mock(Call.class);
    when(organizationClient.getOrganization(any(), any())).thenReturn(orgDTOCall);
    OrganizationResponse organizationResponse =
        OrganizationResponse.builder()
            .organization(OrganizationDTO.builder().identifier(ORG_IDENTIFIER).name(ORG_NAME).build())
            .build();
    ResponseDTO<Optional<OrganizationResponse>> orgRestResponse =
        ResponseDTO.newResponse(Optional.of(organizationResponse));
    Response<ResponseDTO<Optional<OrganizationResponse>>> orgResponse = Response.success(orgRestResponse);
    when(orgDTOCall.execute()).thenReturn(orgResponse);

    Call<ResponseDTO<Optional<ProjectResponse>>> projectCall = mock(Call.class);
    when(projectClient.getProject(anyString(), anyString(), anyString())).thenReturn(projectCall);
    when(projectCall.execute())
        .thenReturn(
            Response.success(ResponseDTO.newResponse(Optional.of(ProjectResponse.builder()
                                                                     .project(ProjectDTO.builder()
                                                                                  .name(PROJECT_NAME)
                                                                                  .identifier(PROJECT_IDENTIFIER)
                                                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                                                  .build())
                                                                     .build()))));

    Object backstageCatalog = harnessToIDPHelper.buildBackstageCatalog(inlineComponentEntity, false, false, false);
    String expected =
        "{apiVersion=backstage.io/v1alpha1, kind=Component, metadata={org_identifier=org-id, name=idp-service, "
        + "namespace=account.org-id.project-id, "
        + "annotations={backstage.io/managed-by-origin-location=url:https://github.com/idp-service.yaml, "
        + "backstage.io/managed-by-location=url:https://github.com/idp-service.yaml, "
        + "backstage.io/source-location=https://github.com, pager-duty=IDP}, description=IDP Service Catalog, "
        + "title=IDP Service, org_name=org-name, project_name=proj-name, project_identifier=project-id, tags=[java, "
        + "bazel]}, spec={lifecycle=experimental, owner=user:account/admin.user.plus, "
        + "providesApis=[api:account/idp-service], subcomponentOf=component:account/devX, "
        + "dependsOn=[component:account/ng-manager, "
        + "resource:account/ng-manager], dependencyOf=[component:account/idp-admin], "
        + "consumesApis=[api:account/ng-manager], type=service}}";
    assertNotNull(backstageCatalog);
    assertEquals(expected, backstageCatalog.toString());
    inlineComponentEntity.setOrgIdentifier(null);
    inlineComponentEntity.setProjectIdentifier(null);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testBuildBackstageCatalogForComponentForOrgScope() throws Exception {
    InlineCatalogEntity inlineComponentEntity = inlineCatalogEntities.get(0);
    inlineComponentEntity.setOrgIdentifier(ORG_IDENTIFIER);

    Call<ResponseDTO<Optional<OrganizationResponse>>> orgDTOCall = mock(Call.class);
    when(organizationClient.getOrganization(any(), any())).thenReturn(orgDTOCall);
    OrganizationResponse organizationResponse =
        OrganizationResponse.builder()
            .organization(OrganizationDTO.builder().identifier(ORG_IDENTIFIER).name(ORG_NAME).build())
            .build();
    ResponseDTO<Optional<OrganizationResponse>> orgRestResponse =
        ResponseDTO.newResponse(Optional.of(organizationResponse));
    Response<ResponseDTO<Optional<OrganizationResponse>>> orgResponse = Response.success(orgRestResponse);
    when(orgDTOCall.execute()).thenReturn(orgResponse);

    Object backstageCatalog = harnessToIDPHelper.buildBackstageCatalog(inlineComponentEntity, false, true, false);
    String expected =
        "{apiVersion=backstage.io/v1alpha1, kind=Component, metadata={name=idp-service, namespace=default, "
        + "annotations={backstage.io/managed-by-origin-location=url:https://github.com/idp-service.yaml, "
        + "backstage.io/managed-by-location=url:https://github.com/idp-service.yaml, "
        + "backstage.io/source-location=https://github.com, pager-duty=IDP}, description=IDP Service Catalog, "
        + "title=IDP Service, tags=[java, bazel]}, spec={lifecycle=experimental, owner=user:default/admin.user.plus, "
        + "providesApis=[api:default/idp-service], subcomponentOf=component:default/devX, "
        + "dependsOn=[component:default/ng-manager, "
        + "resource:default/ng-manager], dependencyOf=[component:default/idp-admin], "
        + "consumesApis=[api:default/ng-manager], type=service}}";
    assertNotNull(backstageCatalog);
    assertEquals(expected, backstageCatalog.toString());
    inlineComponentEntity.setOrgIdentifier(null);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testBuildBackstageCatalogForAPI() {
    InlineCatalogEntity inlineAPIEntity = inlineCatalogEntities.get(1);
    Object backstageCatalog = harnessToIDPHelper.buildBackstageCatalog(inlineAPIEntity, false, true, false);
    String expected = "{"
        + "apiVersion=backstage.io/v1alpha1, kind=API, "
        + "spec={type=openapi, owner=group:default/idp-team, definition={}}, "
        + "metadata={name=idp-service-api, namespace=default, "
        + "annotations={backstage.io/managed-by-origin-location=url:https://app.harness.io/API/default/"
        + "idp-service-api, "
        + "backstage.io/managed-by-location=url:https://app.harness.io/API/default/idp-service-api}, title=IDP "
        + "Service API}"
        + "}";
    assertNotNull(backstageCatalog);
    assertEquals(expected, backstageCatalog.toString());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testBuildBackstageCatalogForWorkflow() {
    InlineCatalogEntity inlineWorkflowEntity = inlineCatalogEntities.get(2);
    Object backstageCatalog = harnessToIDPHelper.buildBackstageCatalog(inlineWorkflowEntity, false, true, false);
    String expected = "{"
        + "apiVersion=scaffolder.backstage.io/v1beta3, kind=Template, spec={owner=group:default/pl-team, type=website, "
        + "parameters={}, steps={}}, "
        + "metadata={name=self-service, namespace=default, "
        + "annotations={backstage.io/managed-by-origin-location=url:https://app.harness.io/Template/default/"
        + "self-service, backstage.io/managed-by-location=url:https://app.harness.io/Template/default/self-service}, "
        + "title=Website}"
        + "}";
    assertNotNull(backstageCatalog);
    assertEquals(expected, backstageCatalog.toString());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testBuildBackstageCatalogForResource() {
    InlineCatalogEntity inlineResourceEntity = inlineCatalogEntities.get(3);
    Object backstageCatalog = harnessToIDPHelper.buildBackstageCatalog(inlineResourceEntity, false, true, false);
    String expected = "{"
        + "apiVersion=backstage.io/v1alpha1, kind=Resource, "
        + "metadata={name=mongo, namespace=default, "
        + "annotations={backstage.io/managed-by-origin-location=url:https://app.harness.io/Resource/default/mongo, "
        + "backstage.io/managed-by-location=url:https://app.harness.io/Resource/default/mongo}, title=Mongo DB}, "
        + "spec={owner=group:default/idp-team, dependsOn=[resource:default/db], "
        + "dependencyOf=[resource:default/storage], type=database}}";
    assertNotNull(backstageCatalog);
    assertEquals(expected, backstageCatalog.toString());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testBuildBackstageCatalogForUser() throws IOException {
    InlineCatalogEntity inlineUserEntity = inlineCatalogEntities.get(4);
    Response<ResponseDTO<UserAggregateDTO>> userAggregateResponse = getUserAggregateDTO();
    Call<ResponseDTO<UserAggregateDTO>> userAggregateCall = mock(Call.class);
    when(userAggregateCall.execute()).thenReturn(userAggregateResponse);
    when(userNGClient.getAggregatedUser("id123", TEST_ACCOUNT_IDENTIFIER, null, null)).thenReturn(userAggregateCall);
    Object backstageCatalog = harnessToIDPHelper.buildBackstageCatalog(inlineUserEntity, false, true, false);
    String expected = "{"
        + "apiVersion=backstage.io/v1alpha1, kind=User, metadata={name=admin.user.plus, namespace=default, "
        + "annotations={backstage.io/managed-by-origin-location=url:https://app.harness.io/User/default/"
        + "admin.user.plus, harness.io/entity-uuid=id123, "
        + "backstage.io/managed-by-location=url:https://app.harness.io/User/default/admin.user.plus}, title=Admin, "
        + "harness.io/roles=_account_admin,_account_viewer}, "
        + "spec={profile={displayName=Admin, email=admin.user.+@harness.io}, memberOf=[group:default/idp-team]}"
        + "}";
    assertNotNull(backstageCatalog);
    assertEquals(expected, backstageCatalog.toString());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testBuildBackstageCatalogForGroup() {
    InlineCatalogEntity inlineGroupEntity = inlineCatalogEntities.get(5);
    Object backstageCatalog = harnessToIDPHelper.buildBackstageCatalog(inlineGroupEntity, false, true, false);
    String expected = "{"
        + "apiVersion=backstage.io/v1alpha1, kind=Group, "
        + "metadata={name=IDPAdmin, namespace=default, "
        + "annotations={backstage.io/managed-by-origin-location=url:https://app.harness.io/Group/default/IDPAdmin, "
        + "backstage.io/managed-by-location=url:https://app.harness.io/Group/default/IDPAdmin}, title=IDP-Admin, "
        + "created_by=Harness}, spec={parent=group:default/_account_admin, children=[], "
        + "members=[user:default/admin.user.plus], type=team}}";
    assertNotNull(backstageCatalog);
    assertEquals(expected, backstageCatalog.toString());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testBuildBackstageCatalogForGroupWithHasLeader() {
    InlineCatalogEntity inlineGroupEntity = new InlineCatalogEntity();
    inlineGroupEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    inlineGroupEntity.setIdentifier("IDPAdmin");
    inlineGroupEntity.setReferenceType(ReferenceType.INLINE);
    inlineGroupEntity.setApiVersion(HARNESS_API_VERSION);
    inlineGroupEntity.setKind(GROUP_KIND);
    inlineGroupEntity.setName("IDP-Admin");
    inlineGroupEntity.setType("team");
    Map<String, Set<String>> relations = new HashMap<>();
    relations.put(HAS_LEADER, Set.of("user:admin.user.+@harness.io"));
    inlineGroupEntity.setRelations(relations);

    Object backstageCatalog = harnessToIDPHelper.buildBackstageCatalog(inlineGroupEntity, false, true, false);
    assertThat(backstageCatalog.toString()).contains("leaders=[user:default/admin.user.plus]");
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testBuildBackstageCatalogForUserWithLeaderOf() throws IOException {
    InlineCatalogEntity inlineUserEntity = new InlineCatalogEntity();
    inlineUserEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    inlineUserEntity.setIdentifier("admin.user.+@harness.io");
    inlineUserEntity.setReferenceType(ReferenceType.INLINE);
    inlineUserEntity.setApiVersion(HARNESS_API_VERSION);
    inlineUserEntity.setKind(USER_KIND);
    inlineUserEntity.setName("Admin");
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(UUID, "id123");
    inlineUserEntity.setMetadata(metadata);
    Map<String, Set<String>> relations = new HashMap<>();
    relations.put(LEADER_OF, Set.of("group:idp-team"));
    inlineUserEntity.setRelations(relations);

    Response<ResponseDTO<UserAggregateDTO>> userAggregateResponse = getUserAggregateDTO();
    Call<ResponseDTO<UserAggregateDTO>> userAggregateCall = mock(Call.class);
    when(userAggregateCall.execute()).thenReturn(userAggregateResponse);
    when(userNGClient.getAggregatedUser("id123", TEST_ACCOUNT_IDENTIFIER, null, null)).thenReturn(userAggregateCall);

    Object backstageCatalog = harnessToIDPHelper.buildBackstageCatalog(inlineUserEntity, false, true, true);
    assertThat(backstageCatalog.toString()).contains("leaderOf=[group:default/idp-team]");
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testConvertHarnessToBackstage() {
    doNothing().when(catalogServiceHelper).validateKindForCreateUpdateDelete(any());

    when(catalogServiceHelper.getKindScopeIdentifier(anyString())).thenReturn(Triple.of("group", "account", "team-c"));

    String convertedYaml = harnessToIDPHelper.convertHarnessToBackstage(TEST_ACCOUNT_IDENTIFIER,
        "apiVersion: harness.io/v1\n"
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
            + "  description: Hello World example for gRPC\n",
        null, false);

    assertThat(convertedYaml)
        .isEqualTo("apiVersion: backstage.io/v1alpha1\n"
            + "kind: API\n"
            + "metadata:\n"
            + "  name: hello-world\n"
            + "  namespace: account\n"
            + "  description: Hello World example for gRPC\n"
            + "  annotations:\n"
            + "    backstage.io/managed-by-origin-location: url:https://app.harness.io/API/account/hello-world\n"
            + "    backstage.io/managed-by-location: url:https://app.harness.io/API/account/hello-world\n"
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
            + "  type: grpc\n"
            + "relations:\n"
            + "  - targetRef: group:account/team-c\n"
            + "    type: ownedBy\n"
            + "    target:\n"
            + "      kind: group\n"
            + "      namespace: account\n"
            + "      name: team-c\n");
  }

  @Test
  @Owner(developers = ANDERSJOHNSEN)
  @Category(UnitTests.class)
  public void testShouldCopyToBackstageCatalog() {
    InlineCatalogEntity inlineGroupEntity = inlineCatalogEntities.get(5);
    assertEquals(false, harnessToIDPHelper.shouldCopyToBackstageCatalog(inlineGroupEntity));

    InlineCatalogEntity environmentBlueprintEntity = inlineCatalogEntities.get(6);
    assertEquals(false, harnessToIDPHelper.shouldCopyToBackstageCatalog(environmentBlueprintEntity));
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ANDERSJOHNSEN)
  @Category(UnitTests.class)
  public void testErrorOnEnvironmentBlueprintConversion() {
    harnessToIDPHelper.convertHarnessToBackstage(TEST_ACCOUNT_IDENTIFIER,
        "apiVersion: harness.io/v1\n"
            + "kind: EnvironmentBlueprint\n"
            + "type: ''\n"
            + "identifier: hello-world\n"
            + "name: hello-world\n"
            + "owner: team-c\n",
        null, false);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private static List<InlineCatalogEntity> constructInlineCatalogEntities() {
    InlineCatalogEntity inlineComponentEntity = new InlineCatalogEntity();
    inlineComponentEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    inlineComponentEntity.setIdentifier("idp-service");
    inlineComponentEntity.setReferenceType(ReferenceType.INLINE);
    inlineComponentEntity.setApiVersion(HARNESS_API_VERSION);
    inlineComponentEntity.setKind(COMPONENT_KIND);
    inlineComponentEntity.setType("service");
    inlineComponentEntity.setName("IDP Service");
    inlineComponentEntity.setOwner("admin.user.+@harness.io");
    inlineComponentEntity.setTags(List.of("java", "bazel"));
    inlineComponentEntity.setSourceLocation("https://github.com");
    inlineComponentEntity.setDescription("IDP Service Catalog");
    Map<String, String> annotations = new HashMap<>();
    annotations.put("pager-duty", "IDP");
    Map<String, Object> metadata = new HashMap<>();
    metadata.put(ANNOTATIONS, annotations);
    inlineComponentEntity.setMetadata(metadata);
    Map<String, Object> spec = new HashMap<>();
    spec.put(LIFECYCLE, "experimental");
    spec.put(SUB_COMPONENT_OF, "devX");
    inlineComponentEntity.setSpec(spec);
    Map<String, Set<String>> relations = new HashMap<>();
    relations.put(OWNED_BY, Set.of("user:admin.user.+@harness.io"));
    relations.put(PART_OF, Set.of("component:devX"));
    Set<String> dependsOn = new LinkedHashSet<>();
    dependsOn.add("component:ng-manager");
    dependsOn.add("resource:ng-manager");
    relations.put(DEPENDS_ON, dependsOn);
    relations.put(DEPENDENCY_OF, Set.of("component:idp-admin"));
    relations.put(PROVIDES_API, Set.of("api:idp-service"));
    relations.put(CONSUMES_API, Set.of("api:ng-manager"));
    inlineComponentEntity.setRelations(relations);

    InlineCatalogEntity inlineAPIEntity = new InlineCatalogEntity();
    inlineAPIEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    inlineAPIEntity.setIdentifier("idp-service-api");
    inlineAPIEntity.setReferenceType(ReferenceType.INLINE);
    inlineAPIEntity.setApiVersion(HARNESS_API_VERSION);
    inlineAPIEntity.setKind(API_KIND);
    inlineAPIEntity.setType("openapi");
    inlineAPIEntity.setName("IDP Service API");
    inlineAPIEntity.setOwner("idp-team");
    spec = new HashMap<>();
    spec.put("definition", "{}");
    inlineAPIEntity.setSpec(spec);
    relations = new HashMap<>();
    relations.put(OWNED_BY, Set.of("group:idp-team"));
    relations.put(API_CONSUMED_BY, Set.of("component:idp-admin"));
    relations.put(API_PROVIDED_BY, Set.of("component:idp-service"));
    inlineAPIEntity.setRelations(relations);

    InlineCatalogEntity inlineWorkflowEntity = new InlineCatalogEntity();
    inlineWorkflowEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    inlineWorkflowEntity.setIdentifier("self-service");
    inlineWorkflowEntity.setReferenceType(ReferenceType.INLINE);
    inlineWorkflowEntity.setApiVersion(HARNESS_API_VERSION);
    inlineWorkflowEntity.setKind(WORKFLOW_KIND);
    inlineWorkflowEntity.setType("website");
    inlineWorkflowEntity.setName("Website");
    inlineWorkflowEntity.setOwner("pl-team");
    spec = new HashMap<>();
    spec.put("parameters", "{}");
    spec.put("steps", "{}");
    inlineWorkflowEntity.setSpec(spec);
    relations = new HashMap<>();
    relations.put(OWNED_BY, Set.of("group:pl-team"));
    relations.put(DEPENDS_ON, Set.of("workflow:self-start"));
    inlineWorkflowEntity.setRelations(relations);

    InlineCatalogEntity inlineResourceEntity = new InlineCatalogEntity();
    inlineResourceEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    inlineResourceEntity.setIdentifier("mongo");
    inlineResourceEntity.setReferenceType(ReferenceType.INLINE);
    inlineResourceEntity.setApiVersion(HARNESS_API_VERSION);
    inlineResourceEntity.setKind(RESOURCE_KIND);
    inlineResourceEntity.setType("database");
    inlineResourceEntity.setName("Mongo DB");
    inlineResourceEntity.setOwner("idp-team");
    relations = new HashMap<>();
    relations.put(OWNED_BY, Set.of("group:idp-team"));
    relations.put(DEPENDS_ON, Set.of("resource:db"));
    relations.put(DEPENDENCY_OF, Set.of("resource:storage"));
    inlineResourceEntity.setRelations(relations);

    InlineCatalogEntity inlineUserEntity = new InlineCatalogEntity();
    inlineUserEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    inlineUserEntity.setIdentifier("admin.user.+@harness.io");
    inlineUserEntity.setReferenceType(ReferenceType.INLINE);
    inlineUserEntity.setApiVersion(HARNESS_API_VERSION);
    inlineUserEntity.setKind(USER_KIND);
    inlineUserEntity.setName("Admin");
    metadata = new HashMap<>();
    metadata.put(UUID, "id123");
    inlineUserEntity.setMetadata(metadata);
    relations = new HashMap<>();
    relations.put(MEMBER_OF, Set.of("group:idp-team"));
    inlineUserEntity.setRelations(relations);

    InlineCatalogEntity inlineGroupEntity = new InlineCatalogEntity();
    inlineGroupEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    inlineGroupEntity.setIdentifier("IDPAdmin");
    inlineGroupEntity.setReferenceType(ReferenceType.INLINE);
    inlineGroupEntity.setApiVersion(HARNESS_API_VERSION);
    inlineGroupEntity.setKind(GROUP_KIND);
    inlineGroupEntity.setName("IDP-Admin");
    inlineGroupEntity.setType("team");
    relations = new HashMap<>();
    relations.put(CHILD_OF, Set.of("group:_account_admin"));
    relations.put(PARENT_OF, new HashSet<>());
    relations.put(HAS_MEMBER, Set.of("user:admin.user.+@harness.io"));
    inlineGroupEntity.setRelations(relations);

    InlineCatalogEntity inlineEnvironmentBlueprintEntity = new InlineCatalogEntity();
    inlineEnvironmentBlueprintEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    inlineEnvironmentBlueprintEntity.setIdentifier("MyEnvBlueprint");
    inlineEnvironmentBlueprintEntity.setReferenceType(ReferenceType.INLINE);
    inlineEnvironmentBlueprintEntity.setApiVersion(HARNESS_API_VERSION);
    inlineEnvironmentBlueprintEntity.setKind(ENVIRONMENT_BLUEPRINT_KIND);
    inlineEnvironmentBlueprintEntity.setName("MyEnvBlueprint");
    inlineEnvironmentBlueprintEntity.setType("team");

    return List.of(inlineComponentEntity, inlineAPIEntity, inlineWorkflowEntity, inlineResourceEntity, inlineUserEntity,
        inlineGroupEntity, inlineEnvironmentBlueprintEntity);
  }

  private Response<ResponseDTO<UserAggregateDTO>> getUserAggregateDTO() {
    UserAggregateDTO userAggregateDTO = UserAggregateDTO.builder().build();
    List<RoleAssignmentMetadataDTO> roleAssignmentMetadata = new ArrayList<>();
    roleAssignmentMetadata.add(RoleAssignmentMetadataDTO.builder().roleIdentifier("_account_admin").build());
    roleAssignmentMetadata.add(RoleAssignmentMetadataDTO.builder().roleIdentifier("_account_viewer").build());
    userAggregateDTO.setUser(UserMetadataDTO.builder().build());
    userAggregateDTO.setRoleAssignmentMetadata(roleAssignmentMetadata);
    return Response.success(ResponseDTO.newResponse(userAggregateDTO));
  }
}
