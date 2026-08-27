/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.aisre.resource;

import static io.harness.rule.OwnerRule.CAMERON;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.aisre.AiSrePipelineClient;
import io.harness.aisre.AiSrePipelineContextData;
import io.harness.aisre.IncidentTypeList;
import io.harness.aisre.IncidentTypeMetadata;
import io.harness.aisre.IncidentTypeSummary;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.exception.WingsException;
import io.harness.manage.GlobalContextManager;
import io.harness.network.SafeHttpCall;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.rbac.PipelineRbacPermissions;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import retrofit2.Call;

@OwnedBy(HarnessTeam.CHAOS)
@RunWith(MockitoJUnitRunner.class)
public class AisreIncidentTypesResourceTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String SHORT_ID = "incident-type-1";

  @Mock private AiSrePipelineClient aiSrePipelineClient;
  @Mock private AccessControlClient accessControlClient;
  @InjectMocks private AisreIncidentTypesResource aisreIncidentTypesResource;

  @Before
  public void setUp() {
    GlobalContextManager.set(new GlobalContext());
  }

  @After
  public void tearDown() {
    GlobalContextManager.unset();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void listIncidentTypes_whenAccessDenied_doesNotCallAiSreClient() {
    denyPipelineViewAccess();

    assertThatThrownBy(() -> aisreIncidentTypesResource.listIncidentTypes(ACCOUNT_ID, ORG_ID, PROJECT_ID))
        .isInstanceOf(NGAccessDeniedException.class);

    verify(accessControlClient)
        .checkForAccessOrThrow(eq(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID)), eq(Resource.of("PIPELINE", null)),
            eq(PipelineRbacPermissions.PIPELINE_VIEW));
    verify(aiSrePipelineClient, never()).listIncidentTypes(any(), any(), any());
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void getIncidentTypeMetadata_whenAccessDenied_doesNotCallAiSreClient() {
    denyPipelineViewAccess();

    assertThatThrownBy(
        () -> aisreIncidentTypesResource.getIncidentTypeMetadata(SHORT_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID))
        .isInstanceOf(NGAccessDeniedException.class);

    verify(accessControlClient)
        .checkForAccessOrThrow(eq(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID)), eq(Resource.of("PIPELINE", null)),
            eq(PipelineRbacPermissions.PIPELINE_VIEW));
    verify(aiSrePipelineClient, never()).getIncidentTypeMetadata(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void listIncidentTypes_setsTargetScopeDuringCallAndClearsAfter() throws Exception {
    IncidentTypeList list = new IncidentTypeList();
    list.setEntities(List.of());
    list.setTotalCount(0);
    Call<IncidentTypeList> call = Mockito.mock(Call.class);
    when(aiSrePipelineClient.listIncidentTypes(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(call);

    try (MockedStatic<SafeHttpCall> safeHttpCall = Mockito.mockStatic(SafeHttpCall.class)) {
      safeHttpCall.when(() -> SafeHttpCall.executeWithExceptions(call)).thenAnswer(invocation -> {
        assertTargetScope(ACCOUNT_ID, ORG_ID, PROJECT_ID);
        return list;
      });

      ResponseDTO<List<IncidentTypeSummary>> response =
          aisreIncidentTypesResource.listIncidentTypes(ACCOUNT_ID, ORG_ID, PROJECT_ID);

      assertThat(response.getData()).isEmpty();
    }

    assertThat(AiSrePipelineContextData.get().getAccountIdentifier()).isNull();
    assertThat(AiSrePipelineContextData.get().getOrgIdentifier()).isNull();
    assertThat(AiSrePipelineContextData.get().getProjectIdentifier()).isNull();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void getIncidentTypeMetadata_setsTargetScopeDuringCallAndClearsAfter() throws Exception {
    IncidentTypeMetadata metadata = new IncidentTypeMetadata();
    metadata.setShortId(SHORT_ID);
    Call<IncidentTypeMetadata> call = Mockito.mock(Call.class);
    when(aiSrePipelineClient.getIncidentTypeMetadata(SHORT_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(call);

    try (MockedStatic<SafeHttpCall> safeHttpCall = Mockito.mockStatic(SafeHttpCall.class)) {
      safeHttpCall.when(() -> SafeHttpCall.executeWithExceptions(call)).thenAnswer(invocation -> {
        assertTargetScope(ACCOUNT_ID, ORG_ID, PROJECT_ID);
        return metadata;
      });

      ResponseDTO<IncidentTypeMetadata> response =
          aisreIncidentTypesResource.getIncidentTypeMetadata(SHORT_ID, ACCOUNT_ID, ORG_ID, PROJECT_ID);

      assertThat(response.getData().getShortId()).isEqualTo(SHORT_ID);
    }

    assertThat(AiSrePipelineContextData.get().getAccountIdentifier()).isNull();
    assertThat(AiSrePipelineContextData.get().getOrgIdentifier()).isNull();
    assertThat(AiSrePipelineContextData.get().getProjectIdentifier()).isNull();
  }

  @Test
  @Owner(developers = CAMERON)
  @Category(UnitTests.class)
  public void listIncidentTypes_clearsTargetScopeWhenClientThrows() throws Exception {
    Call<IncidentTypeList> call = Mockito.mock(Call.class);
    when(aiSrePipelineClient.listIncidentTypes(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(call);

    try (MockedStatic<SafeHttpCall> safeHttpCall = Mockito.mockStatic(SafeHttpCall.class)) {
      safeHttpCall.when(() -> SafeHttpCall.executeWithExceptions(call))
          .thenThrow(new RuntimeException("upstream failed"));

      assertThatThrownBy(() -> aisreIncidentTypesResource.listIncidentTypes(ACCOUNT_ID, ORG_ID, PROJECT_ID))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("upstream failed");
    }

    assertThat(AiSrePipelineContextData.get().getAccountIdentifier()).isNull();
    assertThat(AiSrePipelineContextData.get().getOrgIdentifier()).isNull();
    assertThat(AiSrePipelineContextData.get().getProjectIdentifier()).isNull();
  }

  private void denyPipelineViewAccess() {
    doThrow(new NGAccessDeniedException(
                "Not authorized", EnumSet.noneOf(WingsException.ReportTarget.class), Collections.emptyList()))
        .when(accessControlClient)
        .checkForAccessOrThrow(eq(ResourceScope.of(ACCOUNT_ID, ORG_ID, PROJECT_ID)), eq(Resource.of("PIPELINE", null)),
            eq(PipelineRbacPermissions.PIPELINE_VIEW));
  }

  private static void assertTargetScope(String accountId, String orgId, String projectId) {
    AiSrePipelineContextData context = AiSrePipelineContextData.get();
    assertThat(context.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(context.getOrgIdentifier()).isEqualTo(orgId);
    assertThat(context.getProjectIdentifier()).isEqualTo(projectId);
  }
}
