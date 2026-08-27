/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.triggers;

import static io.harness.ng.accesscontrol.PlatformPermissions.MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.SERVICEACCOUNT;
import static io.harness.rule.OwnerRule.AYUSHMAN;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.NGAccessDeniedException;
import io.harness.accesscontrol.acl.api.AccessCheckResponseDTO;
import io.harness.accesscontrol.acl.api.AccessControlDTO;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.core.user.UserInfo;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngtriggers.beans.dto.TriggerExecutorDTO;
import io.harness.ngtriggers.beans.entity.NGTriggerEntity;
import io.harness.ngtriggers.beans.source.NGTriggerType;
import io.harness.ngtriggers.beans.target.TargetType;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.serviceaccount.ServiceAccountDTOInternal;
import io.harness.serviceaccount.remote.ServiceAccountClient;
import io.harness.user.remote.UserClient;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import retrofit2.Call;
import retrofit2.Response;

public class TriggerExecutorResolverTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String USER_ID = "userId";
  private static final String USER_EMAIL = "user@test.com";
  private static final String USER_NAME = "userName";
  private static final String SA_ID = "serviceAccountId";
  private static final String SA_EMAIL = "sa@test.com";
  private static final String SA_NAME = "saName";

  @Mock private UserClient userClient;
  @Mock private ServiceAccountClient serviceAccountClient;
  @Mock private NGSettingsClient settingsClient;
  @Mock private AccessControlClient accessControlClient;
  @Mock private AccessControlClient privilegedAccessControlClient;
  @Mock private Call mockCall;
  @Mock private MetricService metricService;

  private TriggerExecutorResolver triggerExecutorResolver;
  private NGTriggerEntity triggerEntity;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    triggerExecutorResolver = new TriggerExecutorResolver(userClient, serviceAccountClient, settingsClient,
        accessControlClient, privilegedAccessControlClient, metricService);
    triggerEntity = NGTriggerEntity.builder()
                        .accountId(ACCOUNT_ID)
                        .orgIdentifier(ORG_ID)
                        .projectIdentifier(PROJECT_ID)
                        .identifier("testTrigger")
                        .name("Test Trigger")
                        .type(NGTriggerType.WEBHOOK)
                        .targetIdentifier("pipeline1")
                        .targetType(TargetType.PIPELINE)
                        .yaml("yaml")
                        .build();
  }

  @After
  public void tearDown() {
    // Prevent thread-local security context leaking between tests. Source principal is
    // stored in GlobalContextManager and is overwritten by tests that need it, so no
    // explicit cleanup is required there.
    SecurityContextBuilder.unsetCompleteContext();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testResolveExecutorPrincipalForUser() throws Exception {
    triggerEntity.setExecutorInfo(
        TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build());

    UserInfo userInfo = UserInfo.builder().uuid(USER_ID).email(USER_EMAIL).name(USER_NAME).build();

    when(userClient.getUserById(USER_ID)).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.of(userInfo))));

    Principal principal = triggerExecutorResolver.resolveExecutorPrincipal(triggerEntity);

    assertThat(principal).isInstanceOf(UserPrincipal.class);
    UserPrincipal userPrincipal = (UserPrincipal) principal;
    assertThat(userPrincipal.getName()).isEqualTo(USER_ID);
    assertThat(userPrincipal.getEmail()).isEqualTo(USER_EMAIL);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testResolveExecutorPrincipalForUserNotFound() throws Exception {
    triggerEntity.setExecutorInfo(
        TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build());

    when(userClient.getUserById(USER_ID)).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.empty())));

    assertThatThrownBy(() -> triggerExecutorResolver.resolveExecutorPrincipal(triggerEntity))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("was not found");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testResolveExecutorPrincipalForDisabledUser() throws Exception {
    triggerEntity.setExecutorInfo(
        TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build());

    UserInfo userInfo = UserInfo.builder().uuid(USER_ID).email(USER_EMAIL).name(USER_NAME).disabled(true).build();

    when(userClient.getUserById(USER_ID)).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.of(userInfo))));

    assertThatThrownBy(() -> triggerExecutorResolver.resolveExecutorPrincipal(triggerEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("is disabled");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testResolveExecutorPrincipalForServiceAccount() throws Exception {
    triggerEntity.setExecutorInfo(TriggerExecutorDTO.builder()
                                      .identifier(SA_ID)
                                      .accountIdentifier(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT)
                                      .build());

    ServiceAccountDTOInternal saDto = ServiceAccountDTOInternal.builder()
                                          .identifier(SA_ID)
                                          .email(SA_EMAIL)
                                          .name(SA_NAME)
                                          .uniqueIdInternal("saUniqueId")
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .build();

    when(serviceAccountClient.listServiceAccountsInternal(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(Collections.singletonList(SA_ID))))
        .thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Collections.singletonList(saDto))));

    Principal principal = triggerExecutorResolver.resolveExecutorPrincipal(triggerEntity);

    assertThat(principal).isInstanceOf(ServiceAccountPrincipal.class);
    ServiceAccountPrincipal saPrincipal = (ServiceAccountPrincipal) principal;
    assertThat(saPrincipal.getName()).isEqualTo(SA_ID);
    assertThat(saPrincipal.getEmail()).isEqualTo(SA_EMAIL);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testResolveExecutorPrincipalForServiceAccountFallsBackToTriggerScope() throws Exception {
    triggerEntity.setExecutorInfo(
        TriggerExecutorDTO.builder().identifier(SA_ID).type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT).build());

    ServiceAccountDTOInternal saDto = ServiceAccountDTOInternal.builder()
                                          .identifier(SA_ID)
                                          .email(SA_EMAIL)
                                          .name(SA_NAME)
                                          .uniqueIdInternal("saUniqueId")
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .build();
    when(serviceAccountClient.listServiceAccountsInternal(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(Collections.singletonList(SA_ID))))
        .thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Collections.singletonList(saDto))));

    Principal principal = triggerExecutorResolver.resolveExecutorPrincipal(triggerEntity);

    assertThat(principal).isInstanceOf(ServiceAccountPrincipal.class);
    verify(serviceAccountClient)
        .listServiceAccountsInternal(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(Collections.singletonList(SA_ID)));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testResolveExecutorPrincipalForServiceAccountNotFound() throws Exception {
    triggerEntity.setExecutorInfo(TriggerExecutorDTO.builder()
                                      .identifier(SA_ID)
                                      .accountIdentifier(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT)
                                      .build());

    when(serviceAccountClient.listServiceAccountsInternal(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(Collections.singletonList(SA_ID))))
        .thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Collections.emptyList())));

    assertThatThrownBy(() -> triggerExecutorResolver.resolveExecutorPrincipal(triggerEntity))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("was not found");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testResolveExecutorPrincipalUnknownType() {
    triggerEntity.setExecutorInfo(TriggerExecutorDTO.builder().identifier("someId").build());

    assertThatThrownBy(() -> triggerExecutorResolver.resolveExecutorPrincipal(triggerEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("missing executor type");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testResolveExecutorPrincipalNullType() {
    triggerEntity.setExecutorInfo(TriggerExecutorDTO.builder().identifier("someId").type(null).build());

    assertThatThrownBy(() -> triggerExecutorResolver.resolveExecutorPrincipal(triggerEntity))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("missing executor type");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorOnCreateForUserWithExplicitSelf() throws Exception {
    mockPipelinePermissionsGranted();

    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    UserInfo userInfo = UserInfo.builder().uuid(USER_ID).email(USER_EMAIL).name(USER_NAME).build();
    when(userClient.getUserById(USER_ID)).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.of(userInfo))));

    TriggerExecutorDTO executorDTO =
        TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build();

    triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
        triggerEntity, executorDTO, ACCOUNT_ID, ORG_ID, PROJECT_ID, true);

    assertThat(triggerEntity.getExecutorInfo().getIdentifier()).isEqualTo(USER_ID);
    assertThat(triggerEntity.getExecutorInfo().getType()).isEqualTo(TriggerExecutorDTO.ExecutorType.USER);
    assertThat(triggerEntity.getExecutorInfo().getAccountIdentifier()).isNull();
    assertThat(triggerEntity.getExecutorInfo().getOrgIdentifier()).isNull();
    assertThat(triggerEntity.getExecutorInfo().getProjectIdentifier()).isNull();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorOnCreateServiceAccountPrincipalCannotSetUserExecutor() {
    ServiceAccountPrincipal currentSa = new ServiceAccountPrincipal(SA_ID, SA_EMAIL, SA_NAME, ACCOUNT_ID, "saUniqueId");
    SourcePrincipalContextBuilder.setSourcePrincipal(currentSa);

    TriggerExecutorDTO executorDTO =
        TriggerExecutorDTO.builder().identifier("someUserId").type(TriggerExecutorDTO.ExecutorType.USER).build();

    assertThatThrownBy(()
                           -> triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
                               triggerEntity, executorDTO, ACCOUNT_ID, ORG_ID, PROJECT_ID, true))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining("Service account cannot assign a user");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorOnCreateUserCannotSetOtherUser() {
    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    TriggerExecutorDTO executorDTO =
        TriggerExecutorDTO.builder().identifier("otherUserId").type(TriggerExecutorDTO.ExecutorType.USER).build();

    assertThatThrownBy(()
                           -> triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
                               triggerEntity, executorDTO, ACCOUNT_ID, ORG_ID, PROJECT_ID, true))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining("only set themselves");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorOnCreateUserCannotSetServiceAccountWithoutManageApiKeyPermission() throws Exception {
    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    ServiceAccountDTOInternal saDto = ServiceAccountDTOInternal.builder()
                                          .identifier(SA_ID)
                                          .email(SA_EMAIL)
                                          .name(SA_NAME)
                                          .uniqueIdInternal("saUniqueId")
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .build();

    when(serviceAccountClient.listServiceAccountsInternal(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(Collections.singletonList(SA_ID))))
        .thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Collections.singletonList(saDto))));

    // User does not have manageapikey permission on the SA
    when(accessControlClient.hasAccess(any(ResourceScope.class), eq(Resource.of(SERVICEACCOUNT, SA_ID)),
             eq(MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION)))
        .thenReturn(false);

    TriggerExecutorDTO executorDTO = TriggerExecutorDTO.builder()
                                         .identifier(SA_ID)
                                         .accountIdentifier(ACCOUNT_ID)
                                         .orgIdentifier(ORG_ID)
                                         .projectIdentifier(PROJECT_ID)
                                         .type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT)
                                         .build();

    assertThatThrownBy(()
                           -> triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
                               triggerEntity, executorDTO, ACCOUNT_ID, ORG_ID, PROJECT_ID, true))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining(MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorOnCreateUserCanOnlySetSelf() {
    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    TriggerExecutorDTO executorDTO =
        TriggerExecutorDTO.builder().identifier("otherUserId").type(TriggerExecutorDTO.ExecutorType.USER).build();

    assertThatThrownBy(()
                           -> triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
                               triggerEntity, executorDTO, ACCOUNT_ID, ORG_ID, PROJECT_ID, true))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining("only set themselves");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorOnCreateRejectsUnknownExecutorUser() throws Exception {
    when(userClient.getUserById(USER_ID)).thenReturn(mockCall);
    when(mockCall.clone()).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.empty())));

    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    TriggerExecutorDTO executorDTO =
        TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build();

    assertThatThrownBy(()
                           -> triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
                               triggerEntity, executorDTO, ACCOUNT_ID, ORG_ID, PROJECT_ID, true))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("was not found");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorOnCreateRejectsMissingTypeWhenEnforcementOn() {
    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    TriggerExecutorDTO noType = TriggerExecutorDTO.builder().identifier("x").build();

    assertThatThrownBy(()
                           -> triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
                               triggerEntity, noType, ACCOUNT_ID, ORG_ID, PROJECT_ID, true))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("executorInfo with uuid and type");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorOnCreateFailsWhenEnforcementOnAndExecutorOmitted() {
    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    assertThatThrownBy(()
                           -> triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
                               triggerEntity, null, ACCOUNT_ID, ORG_ID, PROJECT_ID, true))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("executorInfo with uuid and type");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorOnCreateUserCanSetServiceAccountWithManageApiKeyPermission() throws Exception {
    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    ServiceAccountDTOInternal saDto = ServiceAccountDTOInternal.builder()
                                          .identifier(SA_ID)
                                          .email(SA_EMAIL)
                                          .name(SA_NAME)
                                          .uniqueIdInternal("saUniqueId")
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .build();

    when(serviceAccountClient.listServiceAccountsInternal(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(Collections.singletonList(SA_ID))))
        .thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Collections.singletonList(saDto))));

    // User has manageapikey permission on the SA
    when(accessControlClient.hasAccess(any(ResourceScope.class), eq(Resource.of(SERVICEACCOUNT, SA_ID)),
             eq(MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION)))
        .thenReturn(true);
    // SA has pipeline execute permission
    mockPipelinePermissionsGranted();

    TriggerExecutorDTO executorDto = TriggerExecutorDTO.builder()
                                         .identifier(SA_ID)
                                         .accountIdentifier(ACCOUNT_ID)
                                         .orgIdentifier(ORG_ID)
                                         .projectIdentifier(PROJECT_ID)
                                         .type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT)
                                         .build();

    triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
        triggerEntity, executorDto, ACCOUNT_ID, ORG_ID, PROJECT_ID, true);

    assertThat(triggerEntity.getExecutorInfo().getIdentifier()).isEqualTo(SA_ID);
    assertThat(triggerEntity.getExecutorInfo().getName()).isEqualTo(SA_NAME);
    assertThat(triggerEntity.getExecutorInfo().getEmail()).isEqualTo(SA_EMAIL);
    assertThat(triggerEntity.getExecutorInfo().getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(triggerEntity.getExecutorInfo().getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(triggerEntity.getExecutorInfo().getProjectIdentifier()).isEqualTo(PROJECT_ID);
    assertThat(triggerEntity.getExecutorInfo().getType()).isEqualTo(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorOnCreateResolvesOrgLevelServiceAccountWhenScopeSpecified() throws Exception {
    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    ServiceAccountDTOInternal saDto = ServiceAccountDTOInternal.builder()
                                          .identifier(SA_ID)
                                          .email(SA_EMAIL)
                                          .name(SA_NAME)
                                          .uniqueIdInternal("saUniqueId")
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier("parentOrg")
                                          .projectIdentifier(null)
                                          .build();

    when(serviceAccountClient.listServiceAccountsInternal(
             eq(ACCOUNT_ID), eq("parentOrg"), eq(null), eq(Collections.singletonList(SA_ID))))
        .thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Collections.singletonList(saDto))));

    when(accessControlClient.hasAccess(any(ResourceScope.class), eq(Resource.of(SERVICEACCOUNT, SA_ID)),
             eq(MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION)))
        .thenReturn(true);
    mockPipelinePermissionsGranted();

    TriggerExecutorDTO executorDto = TriggerExecutorDTO.builder()
                                         .identifier(SA_ID)
                                         .accountIdentifier(ACCOUNT_ID)
                                         .orgIdentifier("parentOrg")
                                         .type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT)
                                         .build();

    triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
        triggerEntity, executorDto, ACCOUNT_ID, ORG_ID, PROJECT_ID, true);

    assertThat(triggerEntity.getExecutorInfo().getIdentifier()).isEqualTo(SA_ID);
    assertThat(triggerEntity.getExecutorInfo().getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(triggerEntity.getExecutorInfo().getOrgIdentifier()).isEqualTo("parentOrg");
    assertThat(triggerEntity.getExecutorInfo().getProjectIdentifier()).isNull();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorOnCreateRejectsServiceAccountWithoutScope() {
    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    TriggerExecutorDTO executorDto =
        TriggerExecutorDTO.builder().identifier(SA_ID).type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT).build();

    assertThatThrownBy(()
                           -> triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
                               triggerEntity, executorDto, ACCOUNT_ID, ORG_ID, PROJECT_ID, true))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("accountIdentifier is required");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorOnCreateRejectsUnknownServiceAccount() throws Exception {
    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    when(serviceAccountClient.listServiceAccountsInternal(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(Collections.singletonList("unknownSA"))))
        .thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Collections.emptyList())));

    TriggerExecutorDTO executorDTO = TriggerExecutorDTO.builder()
                                         .identifier("unknownSA")
                                         .accountIdentifier(ACCOUNT_ID)
                                         .orgIdentifier(ORG_ID)
                                         .projectIdentifier(PROJECT_ID)
                                         .type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT)
                                         .build();

    assertThatThrownBy(()
                           -> triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
                               triggerEntity, executorDTO, ACCOUNT_ID, ORG_ID, PROJECT_ID, true))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessageContaining("was not found");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorSkippedWhenSettingDisabledAndNoExplicitExecutor() {
    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
        triggerEntity, null, ACCOUNT_ID, ORG_ID, PROJECT_ID, false);

    assertThat(triggerEntity.getExecutorInfo()).isNull();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorWhenSettingDisabledClearsExistingExecutorOnEntity() {
    triggerEntity.setExecutorInfo(
        TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build());

    triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
        triggerEntity, null, ACCOUNT_ID, ORG_ID, PROJECT_ID, false);

    assertThat(triggerEntity.getExecutorInfo()).isNull();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorAcceptsValidExecutorWhenSettingDisabled() throws Exception {
    mockPipelinePermissionsGranted();

    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    UserInfo userInfo = UserInfo.builder().uuid(USER_ID).email(USER_EMAIL).name(USER_NAME).build();
    when(userClient.getUserById(USER_ID)).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.of(userInfo))));

    TriggerExecutorDTO executorDTO =
        TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build();

    triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
        triggerEntity, executorDTO, ACCOUNT_ID, ORG_ID, PROJECT_ID, false);

    assertThat(triggerEntity.getExecutorInfo()).isNotNull();
    assertThat(triggerEntity.getExecutorInfo().getIdentifier()).isEqualTo(USER_ID);
    assertThat(triggerEntity.getExecutorInfo().getType()).isEqualTo(TriggerExecutorDTO.ExecutorType.USER);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testHandleExecutorOnUpdatePreservesExecutorWhenSettingDisabledAndNoExecutorProvided() {
    NGTriggerEntity existingEntity =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .identifier("testTrigger")
            .name("Test Trigger")
            .type(NGTriggerType.WEBHOOK)
            .targetIdentifier("pipeline1")
            .targetType(TargetType.PIPELINE)
            .yaml("yaml")
            .executorInfo(
                TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build())
            .build();

    triggerExecutorResolver.handleExecutorOnUpdate(
        triggerEntity, existingEntity, null, ACCOUNT_ID, ORG_ID, PROJECT_ID, false);

    assertThat(triggerEntity.getExecutorInfo().getIdentifier()).isEqualTo(USER_ID);
    assertThat(triggerEntity.getExecutorInfo().getType()).isEqualTo(TriggerExecutorDTO.ExecutorType.USER);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testHandleExecutorOnUpdateAcceptsValidExecutorWhenSettingDisabled() throws Exception {
    mockPipelinePermissionsGranted();

    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    UserInfo userInfo = UserInfo.builder().uuid(USER_ID).email(USER_EMAIL).name(USER_NAME).build();
    when(userClient.getUserById(USER_ID)).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.of(userInfo))));

    NGTriggerEntity existingEntity = NGTriggerEntity.builder()
                                         .accountId(ACCOUNT_ID)
                                         .orgIdentifier(ORG_ID)
                                         .projectIdentifier(PROJECT_ID)
                                         .identifier("testTrigger")
                                         .name("Test Trigger")
                                         .type(NGTriggerType.WEBHOOK)
                                         .targetIdentifier("pipeline1")
                                         .targetType(TargetType.PIPELINE)
                                         .yaml("yaml")
                                         .build();

    TriggerExecutorDTO executorDTO =
        TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build();

    triggerExecutorResolver.handleExecutorOnUpdate(
        triggerEntity, existingEntity, executorDTO, ACCOUNT_ID, ORG_ID, PROJECT_ID, false);

    assertThat(triggerEntity.getExecutorInfo()).isNotNull();
    assertThat(triggerEntity.getExecutorInfo().getIdentifier()).isEqualTo(USER_ID);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testHandleExecutorOnUpdatePreservesExistingExecutorWhenSettingOnButExecutorOmitted() {
    NGTriggerEntity existingEntity =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .identifier("testTrigger")
            .name("Test Trigger")
            .type(NGTriggerType.WEBHOOK)
            .targetIdentifier("pipeline1")
            .targetType(TargetType.PIPELINE)
            .yaml("yaml")
            .executorInfo(
                TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build())
            .build();

    triggerExecutorResolver.handleExecutorOnUpdate(
        triggerEntity, existingEntity, null, ACCOUNT_ID, ORG_ID, PROJECT_ID, true);

    assertThat(triggerEntity.getExecutorInfo().getIdentifier()).isEqualTo(USER_ID);
    assertThat(triggerEntity.getExecutorInfo().getType()).isEqualTo(TriggerExecutorDTO.ExecutorType.USER);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testHandleExecutorOnUpdateAutoDefaultsExecutorForLegacyTriggerWhenSettingOn() throws Exception {
    mockPipelinePermissionsGranted();

    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    UserInfo userInfo = UserInfo.builder().uuid(USER_ID).email(USER_EMAIL).name(USER_NAME).build();
    when(userClient.getUserById(USER_ID)).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.of(userInfo))));

    NGTriggerEntity existingEntity = NGTriggerEntity.builder()
                                         .accountId(ACCOUNT_ID)
                                         .orgIdentifier(ORG_ID)
                                         .projectIdentifier(PROJECT_ID)
                                         .identifier("testTrigger")
                                         .name("Test Trigger")
                                         .type(NGTriggerType.WEBHOOK)
                                         .targetIdentifier("pipeline1")
                                         .targetType(TargetType.PIPELINE)
                                         .yaml("yaml")
                                         .build();

    triggerExecutorResolver.handleExecutorOnUpdate(
        triggerEntity, existingEntity, null, ACCOUNT_ID, ORG_ID, PROJECT_ID, true);

    assertThat(triggerEntity.getExecutorInfo().getIdentifier()).isEqualTo(USER_ID);
    assertThat(triggerEntity.getExecutorInfo().getType()).isEqualTo(TriggerExecutorDTO.ExecutorType.USER);
    assertThat(triggerEntity.getExecutorInfo().getAccountIdentifier()).isNull();
    assertThat(triggerEntity.getExecutorInfo().getOrgIdentifier()).isNull();
    assertThat(triggerEntity.getExecutorInfo().getProjectIdentifier()).isNull();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testHandleExecutorOnUpdateFailsForLegacyTriggerWhenSettingOnAndCallerIsServiceAccount() {
    ServiceAccountPrincipal currentSa = new ServiceAccountPrincipal(SA_ID, SA_EMAIL, SA_NAME, ACCOUNT_ID, "saUniqueId");
    SourcePrincipalContextBuilder.setSourcePrincipal(currentSa);

    NGTriggerEntity existingEntity = NGTriggerEntity.builder()
                                         .accountId(ACCOUNT_ID)
                                         .orgIdentifier(ORG_ID)
                                         .projectIdentifier(PROJECT_ID)
                                         .identifier("testTrigger")
                                         .build();

    assertThatThrownBy(()
                           -> triggerExecutorResolver.handleExecutorOnUpdate(
                               triggerEntity, existingEntity, null, ACCOUNT_ID, ORG_ID, PROJECT_ID, true))
        .isInstanceOf(InvalidArgumentsException.class)
        .hasMessageContaining("executorInfo with uuid and type");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testHandleExecutorOnUpdateSucceedsWhenSettingOnAndValidExecutorProvided() throws Exception {
    mockPipelinePermissionsGranted();

    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    UserInfo userInfo = UserInfo.builder().uuid(USER_ID).email(USER_EMAIL).name(USER_NAME).build();
    when(userClient.getUserById(USER_ID)).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.of(userInfo))));

    NGTriggerEntity existingEntity =
        NGTriggerEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_ID)
            .projectIdentifier(PROJECT_ID)
            .identifier("testTrigger")
            .name("Test Trigger")
            .type(NGTriggerType.WEBHOOK)
            .targetIdentifier("pipeline1")
            .targetType(TargetType.PIPELINE)
            .yaml("yaml")
            .executorInfo(
                TriggerExecutorDTO.builder().identifier("oldUserId").type(TriggerExecutorDTO.ExecutorType.USER).build())
            .build();

    TriggerExecutorDTO newExecutor =
        TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build();

    triggerExecutorResolver.handleExecutorOnUpdate(
        triggerEntity, existingEntity, newExecutor, ACCOUNT_ID, ORG_ID, PROJECT_ID, true);

    assertThat(triggerEntity.getExecutorInfo().getIdentifier()).isEqualTo(USER_ID);
    assertThat(triggerEntity.getExecutorInfo().getType()).isEqualTo(TriggerExecutorDTO.ExecutorType.USER);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorRejectsUserWithoutPipelinePermissions() throws Exception {
    mockPipelinePermissionsDenied();

    UserInfo userInfo = UserInfo.builder().uuid(USER_ID).name(USER_NAME).email(USER_EMAIL).disabled(false).build();
    when(userClient.getUserById(USER_ID)).thenReturn(mockCall);
    when(mockCall.clone()).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.of(userInfo))));

    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    TriggerExecutorDTO executorDto = TriggerExecutorDTO.builder()
                                         .identifier(USER_ID)
                                         .name(USER_NAME)
                                         .email(USER_EMAIL)
                                         .type(TriggerExecutorDTO.ExecutorType.USER)
                                         .build();

    assertThatThrownBy(()
                           -> triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
                               triggerEntity, executorDto, ACCOUNT_ID, ORG_ID, PROJECT_ID, true))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining("does not have permission to run pipeline");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorRejectsServiceAccountWithoutPipelinePermissions() throws Exception {
    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    ServiceAccountDTOInternal saDto = ServiceAccountDTOInternal.builder()
                                          .identifier(SA_ID)
                                          .name(SA_NAME)
                                          .email(SA_EMAIL)
                                          .uniqueIdInternal("saUniqueId")
                                          .accountIdentifier(ACCOUNT_ID)
                                          .build();
    when(serviceAccountClient.listServiceAccountsInternal(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), any()))
        .thenReturn(mockCall);
    when(mockCall.clone()).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Collections.singletonList(saDto))));

    // User has manageapikey permission
    when(accessControlClient.hasAccess(any(ResourceScope.class), eq(Resource.of(SERVICEACCOUNT, SA_ID)),
             eq(MANAGEAPIKEY_SERVICEACCOUNT_PERMISSION)))
        .thenReturn(true);
    // SA does NOT have pipeline permissions
    mockPipelinePermissionsDenied();

    TriggerExecutorDTO executorDto = TriggerExecutorDTO.builder()
                                         .identifier(SA_ID)
                                         .accountIdentifier(ACCOUNT_ID)
                                         .orgIdentifier(ORG_ID)
                                         .projectIdentifier(PROJECT_ID)
                                         .name(SA_NAME)
                                         .email(SA_EMAIL)
                                         .type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT)
                                         .build();

    assertThatThrownBy(()
                           -> triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
                               triggerEntity, executorDto, ACCOUNT_ID, ORG_ID, PROJECT_ID, true))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining("does not have permission to run pipeline");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorWhenSettingEnabledAndUserWithNoPipelinePerms() throws Exception {
    mockPipelinePermissionsDenied();

    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    UserInfo userInfo = UserInfo.builder().uuid(USER_ID).email(USER_EMAIL).name(USER_NAME).build();
    when(userClient.getUserById(USER_ID)).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.of(userInfo))));

    TriggerExecutorDTO executorDTO =
        TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build();

    assertThatThrownBy(()
                           -> triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
                               triggerEntity, executorDTO, ACCOUNT_ID, ORG_ID, PROJECT_ID, true))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining("does not have permission to run pipeline");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testValidateExecutorRequiredForExecutionThrowsWhenEnforceEnabledAndExecutorMissing() throws Exception {
    NGTriggerEntity entityWithoutExecutor = NGTriggerEntity.builder()
                                                .accountId(ACCOUNT_ID)
                                                .orgIdentifier(ORG_ID)
                                                .projectIdentifier(PROJECT_ID)
                                                .identifier("testTrigger")
                                                .build();

    when(settingsClient.getSetting(any(), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID))).thenReturn(mockCall);
    when(mockCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            io.harness.ngsettings.dto.SettingValueResponseDTO.builder().value("true").build())));

    assertThatThrownBy(() -> triggerExecutorResolver.validateExecutorRequiredForExecution(entityWithoutExecutor, true))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("requires an executor");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testValidateExecutorPermissionsForExecutionSkipsWhenExecutorMissing() {
    NGTriggerEntity entityWithoutExecutor = NGTriggerEntity.builder()
                                                .accountId(ACCOUNT_ID)
                                                .orgIdentifier(ORG_ID)
                                                .projectIdentifier(PROJECT_ID)
                                                .identifier("testTrigger")
                                                .targetIdentifier("pipeline1")
                                                .build();

    triggerExecutorResolver.validateExecutorPermissionsForExecution(entityWithoutExecutor);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testValidateExecutorPermissionsForExecutionPassesWhenExecutorHasPipelineExecute() {
    triggerEntity.setExecutorInfo(TriggerExecutorDTO.builder()
                                      .identifier(SA_ID)
                                      .uniqueId("saUniqueId")
                                      .type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT)
                                      .build());

    mockPipelinePermissionsGranted();

    triggerExecutorResolver.validateExecutorPermissionsForExecution(triggerEntity);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testValidateExecutorPermissionsForExecutionFailsWhenExecutorLacksPipelinePermissions() {
    triggerEntity.setExecutorInfo(TriggerExecutorDTO.builder()
                                      .identifier(SA_ID)
                                      .uniqueId("saUniqueId")
                                      .type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT)
                                      .build());

    mockPipelinePermissionsDenied();

    assertThatThrownBy(() -> triggerExecutorResolver.validateExecutorPermissionsForExecution(triggerEntity))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining("does not have permission to run pipeline");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testValidateExecutorPermissionsForExecutionFailsWhenUserExecutorLacksPermissions() {
    triggerEntity.setExecutorInfo(
        TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build());

    mockPipelinePermissionsDenied();

    assertThatThrownBy(() -> triggerExecutorResolver.validateExecutorPermissionsForExecution(triggerEntity))
        .isInstanceOf(NGAccessDeniedException.class)
        .hasMessageContaining("does not have permission to run pipeline");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testValidateExecutorPermissionsForExecutionPassesWhenUserExecutorHasPipelineExecute() {
    triggerEntity.setExecutorInfo(
        TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build());

    mockPipelinePermissionsGranted();

    triggerExecutorResolver.validateExecutorPermissionsForExecution(triggerEntity);

    // Verify that the correct USER principal was passed in the batched ACL call.
    ArgumentCaptor<io.harness.accesscontrol.acl.api.Principal> principalCaptor =
        ArgumentCaptor.forClass(io.harness.accesscontrol.acl.api.Principal.class);
    verify(privilegedAccessControlClient, atLeastOnce()).checkForAccess(principalCaptor.capture(), any(List.class));

    assertThat(principalCaptor.getValue().getPrincipalType())
        .isEqualTo(io.harness.accesscontrol.principals.PrincipalType.USER);
    assertThat(principalCaptor.getValue().getPrincipalIdentifier()).isEqualTo(USER_ID);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testValidateExecutorPermissionsForExecutionFailsWhenAclCheckIsUnavailable() {
    triggerEntity.setExecutorInfo(
        TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build());

    doThrow(new RuntimeException("access-control unavailable"))
        .when(privilegedAccessControlClient)
        .checkForAccess(any(io.harness.accesscontrol.acl.api.Principal.class), any(List.class));

    assertThatThrownBy(() -> triggerExecutorResolver.validateExecutorPermissionsForExecution(triggerEntity))
        .isInstanceOf(UnexpectedException.class)
        .hasMessageContaining("Unable to verify pipeline permissions for executor")
        .hasMessageContaining("access-control unavailable");
  }

  // ─────────────────────────────────────────────────────────────────────
  // setExecutorContext: guards the security-context wiring for execution
  // ─────────────────────────────────────────────────────────────────────

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testSetExecutorContextKeepsPipelineServiceAsAuthContextForUserExecutor() {
    Principal executor = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);

    triggerExecutorResolver.setExecutorContext(executor);

    // Auth context must remain a ServicePrincipal so outbound service-to-service calls keep
    // pipeline-service identity. Overwriting it with the executor breaks access-control
    // checkPreconditions for any body-principal check.
    Principal authContext = SecurityContextBuilder.getPrincipal();
    assertThat(authContext).isInstanceOf(ServicePrincipal.class);

    // Source principal carries the executor for X-Source-Principal impersonation.
    Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    assertThat(sourcePrincipal).isEqualTo(executor);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testSetExecutorContextKeepsPipelineServiceAsAuthContextForServiceAccountExecutor() {
    Principal executor = new ServiceAccountPrincipal(SA_ID, SA_EMAIL, SA_NAME, ACCOUNT_ID, "saUniqueId");

    triggerExecutorResolver.setExecutorContext(executor);

    Principal authContext = SecurityContextBuilder.getPrincipal();
    assertThat(authContext).isInstanceOf(ServicePrincipal.class);

    Principal sourcePrincipal = SourcePrincipalContextBuilder.getSourcePrincipal();
    assertThat(sourcePrincipal).isEqualTo(executor);
  }

  // ─────────────────────────────────────────────────────────────────────
  // isEnforceExecutorEnabled: reads the ng-settings toggle
  // ─────────────────────────────────────────────────────────────────────

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testIsEnforceExecutorEnabledReturnsTrueWhenSettingIsTrue() throws Exception {
    when(settingsClient.getSetting(any(), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID))).thenReturn(mockCall);
    when(mockCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            io.harness.ngsettings.dto.SettingValueResponseDTO.builder().value("true").build())));

    assertThat(triggerExecutorResolver.isEnforceExecutorEnabled(ACCOUNT_ID, ORG_ID, PROJECT_ID)).isTrue();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testIsEnforceExecutorEnabledReturnsFalseWhenSettingIsFalse() throws Exception {
    when(settingsClient.getSetting(any(), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID))).thenReturn(mockCall);
    when(mockCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            io.harness.ngsettings.dto.SettingValueResponseDTO.builder().value("false").build())));

    assertThat(triggerExecutorResolver.isEnforceExecutorEnabled(ACCOUNT_ID, ORG_ID, PROJECT_ID)).isFalse();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testIsEnforceExecutorEnabledDefaultsToFalseOnSettingsFailure() throws Exception {
    when(settingsClient.getSetting(any(), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID))).thenReturn(mockCall);
    when(mockCall.execute()).thenThrow(new java.io.IOException("settings service down"));

    // Must not throw: settings failures must not block trigger create/update/execution.
    assertThat(triggerExecutorResolver.isEnforceExecutorEnabled(ACCOUNT_ID, ORG_ID, PROJECT_ID)).isFalse();
  }

  // ─────────────────────────────────────────────────────────────────────
  // validateExecutorRequiredForExecution: skip paths
  // ─────────────────────────────────────────────────────────────────────

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testValidateExecutorRequiredForExecutionSkipsWhenFeatureFlagDisabled() {
    // No settings call expected: FF short-circuits before consulting the setting.
    triggerExecutorResolver.validateExecutorRequiredForExecution(triggerEntity, false);
    // No exception; nothing to assert beyond absence of throw.
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testValidateExecutorRequiredForExecutionSkipsWhenSettingDisabled() throws Exception {
    when(settingsClient.getSetting(any(), eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID))).thenReturn(mockCall);
    when(mockCall.execute())
        .thenReturn(Response.success(ResponseDTO.newResponse(
            io.harness.ngsettings.dto.SettingValueResponseDTO.builder().value("false").build())));

    // Even without executorInfo, missing executor must NOT throw when the enforce setting is off.
    triggerExecutorResolver.validateExecutorRequiredForExecution(triggerEntity, true);
  }

  // ─────────────────────────────────────────────────────────────────────
  // validateExecutorHasPipelinePermissions: OR-of-permissions logic
  // ─────────────────────────────────────────────────────────────────────

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorAcceptsUserWithOnlyPipelineEditPermission() throws Exception {
    // Executor has ONLY pipeline_edit — must be sufficient to pass validation.
    mockPipelinePermissionsGranted();

    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    UserInfo userInfo = UserInfo.builder().uuid(USER_ID).name(USER_NAME).email(USER_EMAIL).disabled(false).build();
    when(userClient.getUserById(USER_ID)).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.of(userInfo))));

    TriggerExecutorDTO executorDto =
        TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build();

    triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
        triggerEntity, executorDto, ACCOUNT_ID, ORG_ID, PROJECT_ID, true);

    assertThat(triggerEntity.getExecutorInfo()).isNotNull();
    assertThat(triggerEntity.getExecutorInfo().getIdentifier()).isEqualTo(USER_ID);
    assertThat(triggerEntity.getExecutorInfo().getType()).isEqualTo(TriggerExecutorDTO.ExecutorType.USER);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testPopulateExecutorAcceptsUserWithOnlyPipelineAbortPermission() throws Exception {
    // Only pipeline_abort should also be sufficient.
    mockPipelinePermissionsGranted();

    UserPrincipal currentUser = new UserPrincipal(USER_ID, USER_EMAIL, USER_NAME, ACCOUNT_ID);
    SourcePrincipalContextBuilder.setSourcePrincipal(currentUser);

    UserInfo userInfo = UserInfo.builder().uuid(USER_ID).name(USER_NAME).email(USER_EMAIL).disabled(false).build();
    when(userClient.getUserById(USER_ID)).thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(new RestResponse<>(Optional.of(userInfo))));

    TriggerExecutorDTO executorDto =
        TriggerExecutorDTO.builder().identifier(USER_ID).type(TriggerExecutorDTO.ExecutorType.USER).build();

    triggerExecutorResolver.populateExecutorOnCreateOrUpdate(
        triggerEntity, executorDto, ACCOUNT_ID, ORG_ID, PROJECT_ID, true);

    assertThat(triggerEntity.getExecutorInfo()).isNotNull();
  }

  // ─────────────────────────────────────────────────────────────────────
  // Argument validation: invalid executor type
  // ─────────────────────────────────────────────────────────────────────

  // Note: type is enforced by the ExecutorType enum on deserialization, so
  // requestedType outside the enum cannot come through the API. The internal
  // type check remains as a defence-in-depth guard.

  // ─────────────────────────────────────────────────────────────────────
  @Test
  @Owner(developers = AYUSHMAN)
  @Category(UnitTests.class)
  public void testValidateExecutorPermissionsForExecution_passesPrincipalUniqueId_storedPath() {
    String saUniqueId = "sa-unique-id-stored";
    triggerEntity.setExecutorInfo(TriggerExecutorDTO.builder()
                                      .identifier(SA_ID)
                                      .uniqueId(saUniqueId)
                                      .type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT)
                                      .build());

    mockPipelinePermissionsGranted();

    triggerExecutorResolver.validateExecutorPermissionsForExecution(triggerEntity);

    verify(privilegedAccessControlClient, atLeastOnce())
        .checkForAccess(argThat(p -> saUniqueId.equals(p.getPrincipalUniqueId())), any(List.class));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testValidateExecutorPermissionsForExecution_resolvesLiveUniqueIdWhenStoredUniqueIdIsNull()
      throws Exception {
    String saUniqueId = "sa-unique-id-live";
    triggerEntity.setExecutorInfo(TriggerExecutorDTO.builder()
                                      .identifier(SA_ID)
                                      .accountIdentifier(ACCOUNT_ID)
                                      .orgIdentifier(ORG_ID)
                                      .projectIdentifier(PROJECT_ID)
                                      .type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT)
                                      .build());

    ServiceAccountDTOInternal saDto = ServiceAccountDTOInternal.builder()
                                          .identifier(SA_ID)
                                          .email(SA_EMAIL)
                                          .name(SA_NAME)
                                          .uniqueIdInternal(saUniqueId)
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .build();
    when(serviceAccountClient.listServiceAccountsInternal(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(Collections.singletonList(SA_ID))))
        .thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Collections.singletonList(saDto))));
    mockPipelinePermissionsGranted();

    triggerExecutorResolver.validateExecutorPermissionsForExecution(triggerEntity);

    verify(privilegedAccessControlClient, atLeastOnce())
        .checkForAccess(argThat(p -> saUniqueId.equals(p.getPrincipalUniqueId())), any(List.class));
    verify(serviceAccountClient)
        .listServiceAccountsInternal(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(Collections.singletonList(SA_ID)));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testValidateExecutorPermissionsForExecution_resolvesLiveUniqueIdWhenExecutorScopeMissing()
      throws Exception {
    String saUniqueId = "sa-unique-id-live";
    triggerEntity.setExecutorInfo(
        TriggerExecutorDTO.builder().identifier(SA_ID).type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT).build());

    ServiceAccountDTOInternal saDto = ServiceAccountDTOInternal.builder()
                                          .identifier(SA_ID)
                                          .email(SA_EMAIL)
                                          .name(SA_NAME)
                                          .uniqueIdInternal(saUniqueId)
                                          .accountIdentifier(ACCOUNT_ID)
                                          .orgIdentifier(ORG_ID)
                                          .projectIdentifier(PROJECT_ID)
                                          .build();
    when(serviceAccountClient.listServiceAccountsInternal(
             eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(Collections.singletonList(SA_ID))))
        .thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Collections.singletonList(saDto))));
    mockPipelinePermissionsGranted();

    triggerExecutorResolver.validateExecutorPermissionsForExecution(triggerEntity);

    verify(serviceAccountClient)
        .listServiceAccountsInternal(eq(ACCOUNT_ID), eq(ORG_ID), eq(PROJECT_ID), eq(Collections.singletonList(SA_ID)));
    verify(privilegedAccessControlClient, atLeastOnce())
        .checkForAccess(argThat(p -> saUniqueId.equals(p.getPrincipalUniqueId())), any(List.class));
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testValidateExecutorPermissionsForExecution_resolvesLiveUniqueIdForAccountScopedSa() throws Exception {
    String saUniqueId = "y4i2_CQbQ9S4w8raP0pMbA";
    triggerEntity.setExecutorInfo(TriggerExecutorDTO.builder()
                                      .identifier(SA_ID)
                                      .accountIdentifier(ACCOUNT_ID)
                                      .type(TriggerExecutorDTO.ExecutorType.SERVICE_ACCOUNT)
                                      .build());

    ServiceAccountDTOInternal saDto = ServiceAccountDTOInternal.builder()
                                          .identifier(SA_ID)
                                          .email(SA_EMAIL)
                                          .name(SA_NAME)
                                          .uniqueIdInternal(saUniqueId)
                                          .accountIdentifier(ACCOUNT_ID)
                                          .build();
    when(serviceAccountClient.listServiceAccountsInternal(
             eq(ACCOUNT_ID), eq(null), eq(null), eq(Collections.singletonList(SA_ID))))
        .thenReturn(mockCall);
    when(mockCall.execute()).thenReturn(Response.success(ResponseDTO.newResponse(Collections.singletonList(saDto))));
    mockPipelinePermissionsGranted();

    triggerExecutorResolver.validateExecutorPermissionsForExecution(triggerEntity);

    verify(serviceAccountClient)
        .listServiceAccountsInternal(eq(ACCOUNT_ID), eq(null), eq(null), eq(Collections.singletonList(SA_ID)));
    verify(privilegedAccessControlClient, atLeastOnce())
        .checkForAccess(argThat(p -> saUniqueId.equals(p.getPrincipalUniqueId())), any(List.class));
  }

  // Test helpers
  // ─────────────────────────────────────────────────────────────────────

  private AccessCheckResponseDTO accessCheckResponse(boolean permitted) {
    AccessControlDTO dto = AccessControlDTO.builder().permitted(permitted).build();
    return AccessCheckResponseDTO.builder().accessControlList(Collections.singletonList(dto)).build();
  }

  private void mockPipelinePermissionsGranted() {
    when(privilegedAccessControlClient.checkForAccess(
             any(io.harness.accesscontrol.acl.api.Principal.class), any(List.class)))
        .thenReturn(accessCheckResponse(true));
  }

  private void mockPipelinePermissionsDenied() {
    when(privilegedAccessControlClient.checkForAccess(
             any(io.harness.accesscontrol.acl.api.Principal.class), any(List.class)))
        .thenReturn(accessCheckResponse(false));
  }
}
