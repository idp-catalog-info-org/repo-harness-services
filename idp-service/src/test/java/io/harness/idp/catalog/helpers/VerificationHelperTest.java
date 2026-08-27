/*
 * Copyright 2025 Harness Inc. All rights reserved.
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
import static io.harness.idp.catalog.utils.Constants.GROUP_KIND;
import static io.harness.idp.catalog.utils.Constants.HARNESS_API_VERSION;
import static io.harness.idp.catalog.utils.Constants.HAS_MEMBER;
import static io.harness.idp.catalog.utils.Constants.LIFECYCLE;
import static io.harness.idp.catalog.utils.Constants.MEMBER_OF;
import static io.harness.idp.catalog.utils.Constants.OWNED_BY;
import static io.harness.idp.catalog.utils.Constants.PARENT_OF;
import static io.harness.idp.catalog.utils.Constants.PART_OF;
import static io.harness.idp.catalog.utils.Constants.PROVIDES_API;
import static io.harness.idp.catalog.utils.Constants.RESOURCE_KIND;
import static io.harness.idp.catalog.utils.Constants.USER_KIND;
import static io.harness.idp.catalog.utils.Constants.UUID;
import static io.harness.idp.catalog.utils.Constants.WORKFLOW_KIND;
import static io.harness.idp.common.Constants.CATALOG_ENTITIES_VERIFICATION_NOTIFICATION_SLACK_WEBHOOK;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
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
import io.harness.idp.catalog.beans.ReferenceType;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.common.IdpCommonService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.scopeinfoclient.remote.ScopeInfoClient;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import retrofit2.Call;
import retrofit2.Response;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class VerificationHelperTest extends CategoryTest {
  @InjectMocks VerificationHelper verificationHelper;
  AutoCloseable openMocks;
  private Call<Object> call;
  @Mock BackstageResourceClient backstageResourceClient;
  @Mock CatalogEntityRepository catalogEntityRepository;
  @Mock ScopeInfoClient scopeInfoClient;
  @Mock IdpCommonService idpCommonService;
  @Mock CatalogServiceHelper catalogServiceHelper;
  static Gson gson = new Gson();
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  @Mock HashMap<String, String> notificationConfigs = new HashMap<>();

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    call = mock(Call.class);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testVerifyHarnessAndIDPEntities() throws IOException {
    ResponseDTO<ScopeInfo> accountRestResponse =
        ResponseDTO.newResponse(ScopeInfo.builder().uniqueId(TEST_ACCOUNT_IDENTIFIER).build());
    Response<ResponseDTO<ScopeInfo>> accountResponse = Response.success(accountRestResponse);
    Call<ResponseDTO<ScopeInfo>> accountResponseDTOCall = mock(Call.class);
    when(accountResponseDTOCall.execute()).thenReturn(accountResponse);
    doReturn(accountResponseDTOCall)
        .when(scopeInfoClient)
        .getScopeInfo(eq(TEST_ACCOUNT_IDENTIFIER), eq(null), eq(null));

    when(catalogServiceHelper.getKindScopeIdentifier("component:default/ep-chaos-experiment"))
        .thenReturn(Triple.of("component", "account", "ep-chaos-experiment"));
    when(catalogServiceHelper.getKindScopeIdentifier("api:default/ce-nextgen"))
        .thenReturn(Triple.of("api", "account", "ce-nextgen"));
    when(catalogServiceHelper.getKindScopeIdentifier("template:default/accountaccess"))
        .thenReturn(Triple.of("workflow", "account", "accountaccess"));
    when(catalogServiceHelper.getKindScopeIdentifier("user:default/admin.user.plus"))
        .thenReturn(Triple.of("user", "account", "admin.user.plus"));
    when(catalogServiceHelper.getKindScopeIdentifier("group:default/idpadmin"))
        .thenReturn(Triple.of("group", "account", "IDPAdmin"));

    List<CatalogEntity> catalogEntities = constructInlineCatalogEntities();
    when(catalogServiceHelper.getAllScopes()).thenReturn("account.*");
    when(catalogServiceHelper.getScopeInfosBasedOnScopesAndEntityRefs(TEST_ACCOUNT_IDENTIFIER, "account.*", null))
        .thenReturn(Pair.of(Collections.emptyList(), Collections.emptyMap()));
    when(catalogEntityRepository.getEntities(anyString(), anyList(), anyInt(), anyInt(), any(), any(), any(), any(),
             any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(new PageImpl<>(catalogEntities, Pageable.unpaged(), catalogEntities.size()));

    Response<Object> response = backstageCatalogEntities();
    when(call.execute()).thenReturn(response);
    when(backstageResourceClient.getCatalogEntities(anyString())).thenReturn(call);

    when(notificationConfigs.get(CATALOG_ENTITIES_VERIFICATION_NOTIFICATION_SLACK_WEBHOOK)).thenReturn("");
    doNothing().when(idpCommonService).sendNotification(any());
    verificationHelper.verifyHarnessAndIDPEntities(TEST_ACCOUNT_IDENTIFIER);
    verify(idpCommonService, times(1)).sendNotification(any());
  }

  private static List<CatalogEntity> constructInlineCatalogEntities() {
    InlineCatalogEntity inlineComponentEntity = new InlineCatalogEntity();
    inlineComponentEntity.setAccountIdentifier(TEST_ACCOUNT_IDENTIFIER);
    inlineComponentEntity.setIdentifier("ep-chaos-experiment");
    inlineComponentEntity.setReferenceType(ReferenceType.INLINE);
    inlineComponentEntity.setApiVersion(HARNESS_API_VERSION);
    inlineComponentEntity.setKind(COMPONENT_KIND);
    inlineComponentEntity.setType("service");
    inlineComponentEntity.setName("ep-chaos-experiment");
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
    inlineAPIEntity.setIdentifier("ce-nextgen");
    inlineAPIEntity.setReferenceType(ReferenceType.INLINE);
    inlineAPIEntity.setApiVersion(HARNESS_API_VERSION);
    inlineAPIEntity.setKind(API_KIND);
    inlineAPIEntity.setType("openapi");
    inlineAPIEntity.setName("ce-nextgen");
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
    inlineWorkflowEntity.setIdentifier("accountaccess");
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
    inlineUserEntity.setName("Admin User");
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

    return List.of(inlineComponentEntity, inlineAPIEntity, inlineWorkflowEntity, inlineResourceEntity, inlineUserEntity,
        inlineGroupEntity);
  }

  private Response<Object> backstageCatalogEntities() {
    List<Map<String, Object>> backstageCatalogEntities = new ArrayList<>();
    Map<String, Object> backstageCatalogEntity = Map.of("metadata",
        Map.of("namespace", "default", "name", "accountaccess"), "apiVersion", "scaffolder.backstage.io/v1beta3",
        "kind", "Template", "spec",
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
                List.of(Map.of("title", "Pipeline Details", "url", "${{ steps.trigger.output.PipelineUrl }}")))));
    String responseString = gson.toJson(backstageCatalogEntity);
    Map<String, Object> responseMap = gson.fromJson(responseString, new TypeToken<Map<String, Object>>() {}.getType());
    backstageCatalogEntities.add(responseMap);

    backstageCatalogEntity = Map.of("metadata", Map.of("namespace", "default", "name", "ce-nextgen"), "apiVersion",
        "backstage.io/v1alpha1", "kind", "API", "spec",
        Map.of("type", "openapi", "lifecycle", "production", "owner", "ccmplayacc", "definition", ""));
    responseString = gson.toJson(backstageCatalogEntity);
    responseMap = gson.fromJson(responseString, new TypeToken<Map<String, Object>>() {}.getType());
    backstageCatalogEntities.add(responseMap);

    backstageCatalogEntity =
        Map.of("metadata", Map.of("namespace", "default", "name", "ep-chaos-experiment"), "kind", "Component", "spec",
            Map.of("type", "Service", "lifecycle", "production", "owner", "hceusers", "system", "chaos", "dependsOn",
                List.of("Component:ep-chaos-operator")),
            "apiVersion", "backstage.io/v1alpha1");
    responseString = gson.toJson(backstageCatalogEntity);
    responseMap = gson.fromJson(responseString, new TypeToken<Map<String, Object>>() {}.getType());
    backstageCatalogEntities.add(responseMap);

    backstageCatalogEntity =
        Map.of("metadata", Map.of("namespace", "default", "name", "admin.user.plus"), "kind", "User", "spec",
            Map.of("profile", Map.of("displayName", "admin", "email", "admin.user@harness.io"), "memberOf", List.of()),
            "apiVersion", "backstage.io/v1alpha1");
    responseString = gson.toJson(backstageCatalogEntity);
    responseMap = gson.fromJson(responseString, new TypeToken<Map<String, Object>>() {}.getType());
    backstageCatalogEntities.add(responseMap);

    backstageCatalogEntity = Map.of("metadata", Map.of("namespace", "default", "name", "idpadmin"), "kind", "Group",
        "spec", Map.of("type", "team", "children", List.of(), "members", List.of("admin.user")), "apiVersion",
        "backstage.io/v1alpha1");
    responseString = gson.toJson(backstageCatalogEntity);
    responseMap = gson.fromJson(responseString, new TypeToken<Map<String, Object>>() {}.getType());
    backstageCatalogEntities.add(responseMap);

    return Response.success(backstageCatalogEntities);
  }
}
