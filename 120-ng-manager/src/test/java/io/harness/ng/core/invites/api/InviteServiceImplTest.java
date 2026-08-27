/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.invites.api;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.eraro.ErrorMessageConstants.INVALID_JWT_TOKEN;
import static io.harness.eraro.ErrorMessageConstants.TOKEN_EXPIRED;
import static io.harness.exception.WingsException.USER;
import static io.harness.ng.core.invites.InviteType.ADMIN_INITIATED_INVITE;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.ACCOUNT_INVITE_ACCEPTED;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.INVITE_EXPIRED;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.INVITE_INVALID;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.USER_ADDED_SUCCESSFULLY_TO_ACCOUNT;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.USER_ALREADY_ADDED;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.USER_ALREADY_INVITED;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.USER_INVITED_SUCCESSFULLY;
import static io.harness.rule.OwnerRule.ANKUSH;
import static io.harness.rule.OwnerRule.KAPIL;
import static io.harness.rule.OwnerRule.PRATEEK;
import static io.harness.rule.OwnerRule.SAHIBA;
import static io.harness.rule.OwnerRule.TEJAS;
import static io.harness.rule.OwnerRule.UJJAWAL;
import static io.harness.rule.OwnerRule.VIKAS_M;
import static io.harness.rule.OwnerRule.ZHENYU;

import static java.util.Optional.of;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.exception.ExpiredTokenException;
import io.harness.exception.InvalidTokenException;
import io.harness.invites.remote.InviteAcceptResponse;
import io.harness.mongo.MongoConfig;
import io.harness.ng.core.AccountOrgProjectHelper;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.UserInviteDTO;
import io.harness.ng.core.invites.InviteType;
import io.harness.ng.core.invites.JWTGeneratorUtils;
import io.harness.ng.core.invites.api.impl.InviteServiceImpl;
import io.harness.ng.core.invites.dto.InviteOperationResponse;
import io.harness.ng.core.invites.dto.RoleBinding;
import io.harness.ng.core.invites.entities.Invite;
import io.harness.ng.core.invites.entities.Invite.InviteKeys;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.TwoFactorAuthSettingsInfo;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ngsettings.services.UserSettingsService;
import io.harness.notification.channeldetails.NotificationChannel;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationResultWithStatus;
import io.harness.outbox.api.OutboxService;
import io.harness.repositories.invites.spring.InviteRepository;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.UserPrincipal;
import io.harness.telemetry.TelemetryReporter;
import io.harness.user.remote.UserClient;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.UserHelperService;

import com.auth0.jwt.interfaces.Claim;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import org.bson.Document;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.support.TransactionTemplate;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
public class InviteServiceImplTest extends CategoryTest {
  private static final String USER_VERIFICATION_SECRET = "abcde";
  private static final String accountIdentifier = randomAlphabetic(7);
  private static final String orgIdentifier = randomAlphabetic(7);
  private static final String orgUniqueId = randomAlphabetic(7);
  private static final String projectIdentifier = randomAlphabetic(7);
  private static final String projectUniqueId = randomAlphabetic(7);
  private static final String emailId = String.format("%s@%s", randomAlphabetic(7), randomAlphabetic(7));
  private static final String userId = randomAlphabetic(10);
  private static final String inviteId = randomAlphabetic(10);
  private static final String EMAIL_NOTIFY_TEMPLATE_ID = "email_notify";
  private static final String EMAIL_INVITE_TEMPLATE_ID = "email_invite";
  private static final String SHOULD_MAIL_CONTAIN_TWO_FACTOR_INFO = "shouldMailContainTwoFactorInfo";
  private ScopeInfo orgScopeInfo = ScopeInfo.builder()
                                       .accountIdentifier(accountIdentifier)
                                       .orgIdentifier(orgIdentifier)
                                       .uniqueId(orgUniqueId)
                                       .scopeType(ScopeLevel.ORGANIZATION)
                                       .build();
  private ScopeInfo projectScopeInfo = ScopeInfo.builder()
                                           .accountIdentifier(accountIdentifier)
                                           .orgIdentifier(orgIdentifier)
                                           .projectIdentifier(projectIdentifier)
                                           .uniqueId(projectUniqueId)
                                           .scopeType(ScopeLevel.PROJECT)
                                           .build();
  @Mock private JWTGeneratorUtils jwtGeneratorUtils;
  @Mock private NgUserService ngUserService;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private InviteRepository inviteRepository;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) AccountClient accountClient;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) UserClient userClient;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private AccessControlClient accessControlClient;
  @Mock private NotificationClient notificationClient;
  @Mock private OutboxService outboxService;
  @Mock private UserGroupService userGroupService;
  @Mock private AccountOrgProjectHelper accountOrgProjectHelper;
  @Mock private TelemetryReporter telemetryReporter;
  @Mock private ScheduledExecutorService executorService;
  @Mock private EnforcementClientService enforcementClientService;
  @Mock private UserHelperService userHelperService;
  @Mock private UserSettingsService userSettingsService;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) PmsFeatureFlagHelper ngFeatureFlagHelperService;
  @Captor private ArgumentCaptor<NotificationChannel> notificationChannelArgumentCaptor;

  private InviteService inviteService;
  MockedStatic<SourcePrincipalContextBuilder> sourcePrincipalContextBuilderMockedStatic;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    MongoConfig mongoConfig = MongoConfig.builder().uri("mongodb://localhost:27017/ng-harness").build();
    inviteService = new InviteServiceImpl(USER_VERIFICATION_SECRET, mongoConfig, jwtGeneratorUtils, ngUserService,
        transactionTemplate, inviteRepository, notificationClient, accountClient, outboxService, accessControlClient,
        userClient, accountOrgProjectHelper, false, telemetryReporter, ngFeatureFlagHelperService, executorService,
        enforcementClientService, userHelperService, scopeInfoService);

    when(accountClient.getAccountDTO(any()).execute())
        .thenReturn(Response.success(new RestResponse(AccountDTO.builder()
                                                          .identifier(accountIdentifier)
                                                          .companyName(accountIdentifier)
                                                          .name(accountIdentifier)
                                                          .build())));
    when(accountOrgProjectHelper.getBaseUrl(any())).thenReturn("qa.harness.io");
    when(notificationClient.sendNotificationAsync(any())).thenReturn(new NotificationResultWithStatus());
    when(accountOrgProjectHelper.getProjectName(any(), any(), any())).thenReturn("Project");
    when(accountOrgProjectHelper.getOrgName(any(), any())).thenReturn("Organization");
    when(accountOrgProjectHelper.getAccountName(any())).thenReturn("Account");

    Call<RestResponse<Boolean>> userCall = mock(Call.class);
    when(userClient.checkUserLimit(any(), anyString())).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse<>(false)));

    when(ngFeatureFlagHelperService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(false);
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any())).thenReturn(ffCall);
    when(accountClient.checkPLNoEmailForSamlAccountInvitesEnabledForAccount(anyString())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(false)));

    sourcePrincipalContextBuilderMockedStatic = mockStatic(SourcePrincipalContextBuilder.class);
    Principal principal = new UserPrincipal("name", "email", "userName", "accountId");
    when(SourcePrincipalContextBuilder.getSourcePrincipal()).thenReturn(principal);

    when(scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, null)).thenReturn(orgScopeInfo);
    when(scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier))
        .thenReturn(projectScopeInfo);
    when(scopeInfoService.getScopeInfo(accountIdentifier, Set.of(projectUniqueId)))
        .thenReturn(Map.of(projectUniqueId, of(projectScopeInfo)));
  }

  @After
  public void tearDown() {
    if (sourcePrincipalContextBuilderMockedStatic != null) {
      sourcePrincipalContextBuilderMockedStatic.close();
    }
  }

  private Invite getDummyInvite() {
    return Invite.builder()
        .accountIdentifier(accountIdentifier)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .parentUniqueId(projectUniqueId)
        .approved(Boolean.FALSE)
        .email(emailId)
        .name(randomAlphabetic(7))
        .id(inviteId)
        .roleBindings(getDummyRoleBinding())
        .inviteType(ADMIN_INITIATED_INVITE)
        .build();
  }

  private List<RoleBinding> getDummyRoleBinding() {
    return Collections.singletonList(RoleBinding.builder()
                                         .managedRole(false)
                                         .resourceGroupIdentifier(randomAlphabetic(7))
                                         .resourceGroupName(randomAlphabetic(7))
                                         .roleIdentifier(randomAlphabetic(7))
                                         .roleName(randomAlphabetic(7))
                                         .build());
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void testCreate_NullInvite() {
    InviteOperationResponse inviteOperationResponse = inviteService.create(projectScopeInfo, null, false, false);
    assertThat(inviteOperationResponse).isEqualTo(InviteOperationResponse.FAIL);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void testCreate_UserAlreadyExists_UserAlreadyAdded() {
    when(ngUserService.isUserAtScope(any(), (ScopeInfo) any())).thenReturn(true);
    when(ngUserService.getUserByEmail(any(), anyBoolean()))
        .thenReturn(of(UserMetadataDTO.builder().uuid(userId).build()));

    InviteOperationResponse inviteOperationResponse =
        inviteService.create(projectScopeInfo, getDummyInvite(), false, false);
    assertThat(inviteOperationResponse).isEqualTo(USER_ALREADY_ADDED);
  }

  @Test
  @Owner(developers = ZHENYU)
  @Category(UnitTests.class)
  public void testCreate_InviteRateLimitExceeded_Throws() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());
    doThrow(new LimitExceededException("Exceeded rate limitation. Current Limit: 50"))
        .when(enforcementClientService)
        .checkAvailabilityWithIncrement(eq(FeatureRestrictionName.INVITE_RATE_LIMIT), anyString(), anyLong());

    assertThatThrownBy(() -> inviteService.create(projectScopeInfo, getDummyInvite(), false, false))
        .isInstanceOf(LimitExceededException.class);

    // The rate limit must reject before anything is persisted or emailed.
    verify(inviteRepository, never()).save(any());
    verify(notificationClient, never()).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = ZHENYU)
  @Category(UnitTests.class)
  public void testCreate_InviteRateLimitCheckedPerCreatedInvite() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());
    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any()).execute())
        .thenReturn(Response.success(new RestResponse(false)));

    InviteOperationResponse response = inviteService.create(projectScopeInfo, getDummyInvite(), false, false);

    assertThat(response).isEqualTo(USER_INVITED_SUCCESSFULLY);
    // One invite created => the window is charged exactly once, not once per requested email.
    verify(enforcementClientService, times(1))
        .checkAvailabilityWithIncrement(eq(FeatureRestrictionName.INVITE_RATE_LIMIT), anyString(), eq(1L));
  }

  @Test
  @Owner(developers = ZHENYU)
  @Category(UnitTests.class)
  public void testCreate_UserAlreadyAdded_DoesNotConsumeRateLimit() {
    when(ngUserService.isUserAtScope(any(), (ScopeInfo) any())).thenReturn(true);
    when(ngUserService.getUserByEmail(any(), anyBoolean()))
        .thenReturn(of(UserMetadataDTO.builder().uuid(userId).build()));

    InviteOperationResponse response = inviteService.create(projectScopeInfo, getDummyInvite(), false, false);

    assertThat(response).isEqualTo(USER_ALREADY_ADDED);
    // No invite is created, so an existing member must not eat into the account's quota.
    verify(enforcementClientService, never())
        .checkAvailabilityWithIncrement(eq(FeatureRestrictionName.INVITE_RATE_LIMIT), anyString(), anyLong());
  }

  @Test
  @Owner(developers = ZHENYU)
  @Category(UnitTests.class)
  public void testResend_InviteRateLimitExceeded_Throws() {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.findFirstByAccountIdentifierAndParentUniqueIdAndEmailAndDeletedFalse(any(), any(), any()))
        .thenReturn(of(getDummyInvite()));
    doThrow(new LimitExceededException("Exceeded rate limitation. Current Limit: 50"))
        .when(enforcementClientService)
        .checkAvailabilityWithIncrement(eq(FeatureRestrictionName.INVITE_RATE_LIMIT), anyString(), anyLong());

    assertThatThrownBy(() -> inviteService.create(projectScopeInfo, getDummyInvite(), false, false))
        .isInstanceOf(LimitExceededException.class);

    // Resend path must also be blocked — no email sent.
    verify(notificationClient, never()).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = ZHENYU)
  @Category(UnitTests.class)
  public void testCreate_ScimInvite_BypassesRateLimit() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    Invite scimInvite = getDummyInvite();
    scimInvite.setApproved(true);
    when(inviteRepository.save(any())).thenReturn(scimInvite);
    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    Call<RestResponse<Boolean>> userCall = mock(Call.class);
    when(userClient.createUserAndCompleteNGInvite(any(), anyBoolean(), anyBoolean())).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    doThrow(new LimitExceededException("Exceeded rate limitation. Current Limit: 50"))
        .when(enforcementClientService)
        .checkAvailabilityWithIncrement(eq(FeatureRestrictionName.INVITE_RATE_LIMIT), anyString(), anyLong());

    inviteService.create(projectScopeInfo, scimInvite, true, false);

    // SCIM must bypass rate limit even when it would otherwise be exceeded.
    verify(enforcementClientService, never())
        .checkAvailabilityWithIncrement(eq(FeatureRestrictionName.INVITE_RATE_LIMIT), anyString(), anyLong());
  }

  @Test
  @Owner(developers = UJJAWAL)
  @Category(UnitTests.class)
  public void testCreateUserLimit() {
    when(ngUserService.isUserAtScope(any(), (ScopeInfo) any())).thenReturn(true);
    when(ngUserService.getUserByEmail(any(), anyBoolean()))
        .thenReturn(of(UserMetadataDTO.builder().uuid(userId).build()));

    InviteOperationResponse invite = inviteService.create(projectScopeInfo, getDummyInvite(), false, false);
    assertThat(invite).isEqualTo(USER_ALREADY_ADDED);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void testCreate_UserAlreadyExists_UserNotInvitedYet() throws IOException {
    UserMetadataDTO user = UserMetadataDTO.builder().name(randomAlphabetic(7)).email(emailId).uuid(userId).build();
    when(ngUserService.getUserByEmail(any(), anyBoolean())).thenReturn(of(user));
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());
    when(inviteRepository.findFirstByAccountIdentifierAndParentUniqueIdAndEmailAndDeletedFalse(any(), any(), any()))
        .thenReturn(Optional.empty());

    InviteOperationResponse inviteOperationResponse =
        inviteService.create(projectScopeInfo, getDummyInvite(), false, false);
    assertThat(inviteOperationResponse).isEqualTo(USER_INVITED_SUCCESSFULLY);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
    verify(userClient, times(0)).updateUserTwoFactorAuthInfo(eq(emailId), any(TwoFactorAuthSettingsInfo.class));
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void testCreate_UserDNE_UserNotInvitedYet() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());

    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any()).execute())
        .thenReturn(Response.success(new RestResponse(false)));
    InviteOperationResponse inviteOperationResponse =
        inviteService.create(projectScopeInfo, getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_INVITED_SUCCESSFULLY);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
    verify(userClient, times(0)).updateUserTwoFactorAuthInfo(eq(emailId), any(TwoFactorAuthSettingsInfo.class));
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void testCreate_UserInvitedBefore() {
    ArgumentCaptor<String> idArgumentCaptor = ArgumentCaptor.forClass(String.class);
    UserMetadataDTO user = UserMetadataDTO.builder().name(randomAlphabetic(7)).email(emailId).uuid(userId).build();

    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(of(user), Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());
    when(inviteRepository.findFirstByAccountIdentifierAndParentUniqueIdAndEmailAndDeletedFalse(any(), any(), any()))
        .thenReturn(of(getDummyInvite()));

    //    when user exists
    InviteOperationResponse inviteOperationResponse =
        inviteService.create(projectScopeInfo, getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_ALREADY_INVITED);
    verify(inviteRepository, atLeast(2)).updateInvite(idArgumentCaptor.capture(), any());
    String id = idArgumentCaptor.getValue();
    assertThat(id).isEqualTo(inviteId);
    verify(notificationClient, times(1)).sendNotificationAsync(any());

    //    when user doesn't exists
    inviteOperationResponse = inviteService.create(projectScopeInfo, getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_ALREADY_INVITED);
    verify(inviteRepository, atLeast(2)).updateInvite(idArgumentCaptor.capture(), any());
    id = idArgumentCaptor.getValue();
    assertThat(id).isEqualTo(inviteId);
    verify(notificationClient, times(2)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void testCreate_NewUser_InviteAccepted() {
    Invite invite = Invite.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .approved(Boolean.FALSE)
                        .email(emailId)
                        .name(randomAlphabetic(7))
                        .id(inviteId)
                        .roleBindings(getDummyInvite().getRoleBindings())
                        .inviteType(ADMIN_INITIATED_INVITE)
                        .approved(Boolean.TRUE)
                        .build();
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.findFirstByAccountIdentifierAndParentUniqueIdAndEmailAndDeletedFalse(any(), any(), any()))
        .thenReturn(of(invite));

    InviteOperationResponse inviteOperationResponse = inviteService.create(projectScopeInfo, invite, false, false);

    assertThat(inviteOperationResponse).isEqualTo(ACCOUNT_INVITE_ACCEPTED);
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testCreate_UserDoesNotExist_UserNotInvitedYet() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());

    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any()).execute())
        .thenReturn(Response.success(new RestResponse(false)));
    Invite dummyInvite = getDummyInvite();
    dummyInvite.setExternalId("test_external_id");
    InviteOperationResponse inviteOperationResponse = inviteService.create(projectScopeInfo, dummyInvite, false, false);

    ArgumentCaptor<Invite> inviteArgumentCaptor = ArgumentCaptor.forClass(Invite.class);

    verify(inviteRepository, times(1)).save(inviteArgumentCaptor.capture());
    Invite inviteArgumentCaptorValue = inviteArgumentCaptor.getValue();
    assertThat(inviteArgumentCaptorValue.getParentUniqueId()).isNotEmpty();
    assertThat(inviteArgumentCaptorValue.getParentUniqueId()).isEqualTo(projectUniqueId);

    assertThat(inviteOperationResponse).isEqualTo(USER_INVITED_SUCCESSFULLY);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = PRATEEK)
  @Category(UnitTests.class)
  public void testCreate_NewUser_InviteAccepted_LdapGroup() {
    final String testExternalId = "test_external_id";
    Invite invite = Invite.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .approved(Boolean.FALSE)
                        .email(emailId)
                        .name(randomAlphabetic(7))
                        .externalId(testExternalId)
                        .id(inviteId)
                        .roleBindings(getDummyInvite().getRoleBindings())
                        .inviteType(ADMIN_INITIATED_INVITE)
                        .approved(Boolean.TRUE)
                        .build();
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.findFirstByAccountIdentifierAndParentUniqueIdAndEmailAndDeletedFalse(any(), any(), any()))
        .thenReturn(of(invite));

    InviteOperationResponse inviteOperationResponse = inviteService.create(projectScopeInfo, invite, false, true);
    assertThat(inviteOperationResponse).isEqualTo(ACCOUNT_INVITE_ACCEPTED);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void deleteInvite_inviteExists() {
    ArgumentCaptor<String> idArgumentCaptor = ArgumentCaptor.forClass(String.class);
    when(inviteRepository.findFirstByIdAndDeleted(any(), any())).thenReturn(of(getDummyInvite()));
    when(inviteRepository.updateInvite(any(), any())).thenReturn(getDummyInvite());
    Invite invite = getDummyInvite();

    inviteService.deleteInvite(invite, projectScopeInfo);

    verify(inviteRepository, times(1)).updateInvite(idArgumentCaptor.capture(), any());
    assertThat(idArgumentCaptor.getValue()).isEqualTo(inviteId);
  }

  // Removed the test case as it's no longer valid we do optional check beforehand

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void acceptInvite_InvalidJWTToken() {
    InviteAcceptResponse inviteAcceptResponse = inviteService.acceptInvite(null);
    assertThat(inviteAcceptResponse.getResponse()).isEqualTo(INVITE_INVALID);

    inviteAcceptResponse = inviteService.acceptInvite("");
    assertThat(inviteAcceptResponse.getResponse()).isEqualTo(INVITE_INVALID);

    when(jwtGeneratorUtils.verifyJWTToken(any(), any())).thenReturn(Collections.emptyMap());

    inviteAcceptResponse = inviteService.acceptInvite("sadfs");
    assertThat(inviteAcceptResponse.getResponse()).isEqualTo(INVITE_INVALID);
  }

  @Test
  @Owner(developers = {ANKUSH, SAHIBA})
  @Category(UnitTests.class)
  public void acceptInvite_validToken() {
    String dummyJWTToken = "dummy invite token";
    Claim claim = mock(Claim.class);
    Invite invite = getDummyInvite();
    invite.setInviteToken(dummyJWTToken);
    UserMetadataDTO user = UserMetadataDTO.builder().name(randomAlphabetic(7)).email(emailId).uuid(userId).build();
    ArgumentCaptor<String> idCapture = ArgumentCaptor.forClass(String.class);
    when(claim.asString()).thenReturn(inviteId);
    when(jwtGeneratorUtils.verifyJWTTokenV2(any(), any())).thenReturn(Collections.singletonMap(InviteKeys.id, claim));
    when(inviteRepository.findById(any())).thenReturn(of(invite));
    when(ngUserService.getUserByEmail(any(), anyBoolean())).thenReturn(of(user));

    InviteAcceptResponse inviteAcceptResponse = inviteService.acceptInvite(dummyJWTToken);

    assertThat(inviteAcceptResponse.getResponse()).isEqualTo(ACCOUNT_INVITE_ACCEPTED);
    verify(inviteRepository, times(1)).updateInvite(idCapture.capture(), any());
    assertThat(idCapture.getValue()).isEqualTo(inviteId);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void acceptInvite_InvalidToken() {
    String dummyJWTToken = "dummy invite token";
    Claim claim = mock(Claim.class);
    Invite invite = getDummyInvite();
    invite.setInviteToken(dummyJWTToken);
    UserMetadataDTO user = UserMetadataDTO.builder().name(randomAlphabetic(7)).email(emailId).uuid(userId).build();
    ArgumentCaptor<String> idCapture = ArgumentCaptor.forClass(String.class);
    when(claim.asString()).thenReturn(inviteId);
    when(jwtGeneratorUtils.verifyJWTTokenV2(any(), any()))
        .thenThrow(new InvalidTokenException(INVALID_JWT_TOKEN, USER));
    when(inviteRepository.findById(any())).thenReturn(of(invite));
    when(ngUserService.getUserByEmail(any(), anyBoolean())).thenReturn(of(user));
    InviteAcceptResponse inviteAcceptResponse = inviteService.acceptInvite(dummyJWTToken);
    assertThat(inviteAcceptResponse.getResponse()).isEqualTo(INVITE_INVALID);
  }

  @Test
  @Owner(developers = SAHIBA)
  @Category(UnitTests.class)
  public void acceptInvite_ExpiredToken() {
    String dummyJWTToken = "dummy invite token";
    Claim claim = mock(Claim.class);
    Invite invite = getDummyInvite();
    invite.setInviteToken(dummyJWTToken);
    UserMetadataDTO user = UserMetadataDTO.builder().name(randomAlphabetic(7)).email(emailId).uuid(userId).build();
    ArgumentCaptor<String> idCapture = ArgumentCaptor.forClass(String.class);
    when(claim.asString()).thenReturn(inviteId);
    when(jwtGeneratorUtils.verifyJWTTokenV2(any(), any())).thenThrow(new ExpiredTokenException(TOKEN_EXPIRED, USER));
    when(inviteRepository.findById(any())).thenReturn(of(invite));
    when(ngUserService.getUserByEmail(any(), anyBoolean())).thenReturn(of(user));
    InviteAcceptResponse inviteAcceptResponse = inviteService.acceptInvite(dummyJWTToken);
    assertThat(inviteAcceptResponse.getResponse()).isEqualTo(INVITE_EXPIRED);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void updateInvite_invalidInviteId() {
    when(inviteRepository.findFirstByIdAndDeleted(any(), any())).thenReturn(Optional.empty());
    Optional<Invite> returnInvite = inviteService.updateInvite(projectScopeInfo, getDummyInvite());
    assertThat(returnInvite.isPresent()).isFalse();
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void updateInvite_ValidInviteId() {
    String dummyJWTToken = "Dummy jwt token";
    Claim claim = mock(Claim.class);
    when(inviteRepository.findFirstByIdAndDeleted(any(), any())).thenReturn(of(getDummyInvite()));
    when(claim.asString()).thenReturn(inviteId);
    when(jwtGeneratorUtils.generateJWTToken(any(), any(), any())).thenReturn(dummyJWTToken);
    when(notificationClient.sendNotificationAsync(any())).thenReturn(NotificationResultWithStatus.builder().build());

    Optional<Invite> returnInvite = inviteService.updateInvite(projectScopeInfo, getDummyInvite());
    assertThat(returnInvite.isPresent()).isTrue();
    assertThat(returnInvite.get().getInviteToken()).isEqualTo(dummyJWTToken);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void updateInvite_ValidInviteId_UserInitiatedInvite() {
    String dummyJWTToken = "Dummy jwt token";
    Invite invite = getDummyInvite();
    invite.setInviteType(InviteType.USER_INITIATED_INVITE);
    Claim claim = mock(Claim.class);
    when(inviteRepository.findFirstByIdAndDeleted(any(), any())).thenReturn(of(invite));
    when(claim.asString()).thenReturn(inviteId);
    when(jwtGeneratorUtils.generateJWTToken(any(), any(), any())).thenReturn(dummyJWTToken);
    when(notificationClient.sendNotificationAsync(any())).thenReturn(NotificationResultWithStatus.builder().build());

    assertThatThrownBy(() -> inviteService.updateInvite(projectScopeInfo, invite))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void completeInvite_InvalidJWTToken() {
    when(jwtGeneratorUtils.verifyJWTToken(any(), any())).thenReturn(Collections.emptyMap());

    boolean result = inviteService.completeInvite(Optional.empty());
    assertThat(result).isFalse();
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void completeInvite_ValidToken_UserNotPresent() {
    String dummyJWTTOken = "dummy jwt token";
    Claim claim = mock(Claim.class);
    when(claim.asString()).thenReturn(inviteId);
    when(jwtGeneratorUtils.verifyJWTToken(any(), any())).thenReturn(Collections.singletonMap(InviteKeys.id, claim));
    when(inviteRepository.findFirstByIdAndDeleted(any(), any())).thenReturn(of(getDummyInvite()));
    when(ngUserService.getUserByEmail(any(), anyBoolean())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> inviteService.completeInvite(of(getDummyInvite())))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @Owner(developers = ANKUSH)
  @Category(UnitTests.class)
  public void completeInvite_ValidToken() {
    String dummyJWTTOken = "dummy jwt token";
    Claim claim = mock(Claim.class);
    UserMetadataDTO user = UserMetadataDTO.builder().name(randomAlphabetic(7)).email(emailId).uuid(userId).build();
    Scope scope = Scope.builder()
                      .accountIdentifier(accountIdentifier)
                      .orgIdentifier(orgIdentifier)
                      .projectIdentifier(projectIdentifier)
                      .build();
    ArgumentCaptor<Update> updateCapture = ArgumentCaptor.forClass(Update.class);
    ArgumentCaptor<String> idCapture = ArgumentCaptor.forClass(String.class);

    when(claim.asString()).thenReturn(inviteId);
    when(jwtGeneratorUtils.verifyJWTToken(any(), any())).thenReturn(Collections.singletonMap(InviteKeys.id, claim));
    when(inviteRepository.findFirstByIdAndDeleted(any(), any())).thenReturn(of(getDummyInvite()));
    when(ngUserService.getUserByEmail(any(), anyBoolean())).thenReturn(of(user));
    doNothing().when(ngUserService).waitForRbacSetup(any(), anyString(), anyString());
    boolean result = inviteService.completeInvite(of(getDummyInvite()));
    verify(ngUserService, times(1)).waitForRbacSetup(any(), anyString(), anyString());
    assertThat(result).isTrue();
    verify(inviteRepository, times(1)).updateInvite(idCapture.capture(), updateCapture.capture());
    assertThat(idCapture.getValue()).isEqualTo(inviteId);
    assertThat(updateCapture.getValue().modifies(InviteKeys.deleted)).isTrue();
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void completeUserNgSetup() {
    UserMetadataDTO user = UserMetadataDTO.builder().name(randomAlphabetic(7)).email(emailId).uuid(userId).build();
    when(ngUserService.getUserByEmail(any(), anyBoolean())).thenReturn(of(user));
    doNothing().when(ngUserService).waitForRbacSetup(any(), anyString(), anyString());
    inviteService.completeUserNgSetupWithoutInvite(emailId, accountIdentifier);
    verify(ngUserService, times(1)).waitForRbacSetup(any(), anyString(), anyString());
    verify(ngUserService, times(1)).addUserToScope(any(), any(), any(), any(), any(), any());
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testCreate_withSsoEnabled_withAutoInviteAcceptanceEnabled() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());

    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    Call<RestResponse<Boolean>> userCall = mock(Call.class);
    when(userClient.createUserAndCompleteNGInvite(any(), anyBoolean(), anyBoolean())).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));

    InviteOperationResponse inviteOperationResponse =
        inviteService.create(projectScopeInfo, getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_INVITED_SUCCESSFULLY);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
    verify(notificationClient).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
    assertThat(notificationChannelArgumentCaptor.getValue().getTemplateId()).isEqualTo(EMAIL_NOTIFY_TEMPLATE_ID);
    assertThat(notificationChannelArgumentCaptor.getValue().getTemplateData().get(SHOULD_MAIL_CONTAIN_TWO_FACTOR_INFO))
        .isEqualTo("false");
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testCreate_withSsoEnabled_withAutoInviteAcceptanceEnabled_withTwoFactorEnforced() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());

    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    Call<RestResponse<Boolean>> userCall = mock(Call.class);
    when(userClient.createUserAndCompleteNGInvite(any(), anyBoolean(), anyBoolean())).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    Call<RestResponse<Optional<UserInfo>>> userUpdateCall = mock(Call.class);
    when(userClient.updateUserTwoFactorAuthInfo(any(), any())).thenReturn(userUpdateCall);
    when(userUpdateCall.execute()).thenReturn(Response.success(new RestResponse<>(of(UserInfo.builder().build()))));
    when(accountClient.getAccountDTO(any()).execute())
        .thenReturn(Response.success(new RestResponse(AccountDTO.builder()
                                                          .identifier(accountIdentifier)
                                                          .companyName(accountIdentifier)
                                                          .name(accountIdentifier)
                                                          .isTwoFactorAdminEnforced(true)
                                                          .build())));

    InviteOperationResponse inviteOperationResponse =
        inviteService.create(projectScopeInfo, getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_INVITED_SUCCESSFULLY);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
    verify(notificationClient).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
    assertThat(notificationChannelArgumentCaptor.getValue().getTemplateId()).isEqualTo(EMAIL_NOTIFY_TEMPLATE_ID);
    assertThat(notificationChannelArgumentCaptor.getValue().getTemplateData().get(SHOULD_MAIL_CONTAIN_TWO_FACTOR_INFO))
        .isEqualTo("true");
    verify(userClient, times(1)).updateUserTwoFactorAuthInfo(eq(emailId), any(TwoFactorAuthSettingsInfo.class));
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testCreate_withSsoEnabled_withPLNoEmailForSamlAccountInvitesEnabled() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());

    Call<RestResponse<Boolean>> call = mock(Call.class);
    when(userClient.createUserAndCompleteNGInvite(any(), anyBoolean(), anyBoolean())).thenReturn(call);
    when(accountClient.checkPLNoEmailForSamlAccountInvitesEnabledForAccount(anyString())).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(new RestResponse<>(true)));

    InviteOperationResponse inviteOperationResponse =
        inviteService.create(projectScopeInfo, getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_ADDED_SUCCESSFULLY_TO_ACCOUNT);
    verify(notificationClient, times(0)).sendNotificationAsync(any());
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testCreate_withSsoEnabled_withPLNoEmailForSamlAccountInvitesEnabled_withTwoFactorEnforced()
      throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());

    Call<RestResponse<Boolean>> call = mock(Call.class);
    when(userClient.createUserAndCompleteNGInvite(any(), anyBoolean(), anyBoolean())).thenReturn(call);
    Call<RestResponse<Optional<UserInfo>>> userUpdateCall = mock(Call.class);
    when(userClient.updateUserTwoFactorAuthInfo(any(), any())).thenReturn(userUpdateCall);
    when(userUpdateCall.execute()).thenReturn(Response.success(new RestResponse<>(of(UserInfo.builder().build()))));
    when(accountClient.checkPLNoEmailForSamlAccountInvitesEnabledForAccount(anyString())).thenReturn(call);
    when(accountClient.getAccountDTO(any()).execute())
        .thenReturn(Response.success(new RestResponse(AccountDTO.builder()
                                                          .identifier(accountIdentifier)
                                                          .companyName(accountIdentifier)
                                                          .name(accountIdentifier)
                                                          .isTwoFactorAdminEnforced(true)
                                                          .build())));
    when(call.execute()).thenReturn(Response.success(new RestResponse<>(true)));

    InviteOperationResponse inviteOperationResponse =
        inviteService.create(projectScopeInfo, getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_INVITED_SUCCESSFULLY);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
    verify(userClient, times(1)).updateUserTwoFactorAuthInfo(eq(emailId), any(TwoFactorAuthSettingsInfo.class));
  }

  @Test
  @Owner(developers = KAPIL)
  @Category(UnitTests.class)
  public void testCreate_withInviteEmail_with2FaEnforcedAtAccountLevel() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());
    when(inviteRepository.save(any())).thenReturn(getDummyInvite());

    Call<RestResponse<Boolean>> userCall = mock(Call.class);
    when(userClient.createUserAndCompleteNGInvite(any(), anyBoolean(), anyBoolean())).thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    Call<RestResponse<Optional<UserInfo>>> userInfoCall = mock(Call.class);
    when(userClient.updateUserTwoFactorAuthInfo(any(), any())).thenReturn(userInfoCall);
    when(userInfoCall.execute()).thenReturn(Response.success(new RestResponse<>(of(UserInfo.builder().build()))));
    when(accountClient.getAccountDTO(any()).execute())
        .thenReturn(Response.success(new RestResponse(AccountDTO.builder()
                                                          .identifier(accountIdentifier)
                                                          .companyName(accountIdentifier)
                                                          .name(accountIdentifier)
                                                          .isTwoFactorAdminEnforced(true)
                                                          .build())));

    InviteOperationResponse inviteOperationResponse =
        inviteService.create(projectScopeInfo, getDummyInvite(), false, false);

    assertThat(inviteOperationResponse).isEqualTo(USER_INVITED_SUCCESSFULLY);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
    verify(notificationClient).sendNotificationAsync(notificationChannelArgumentCaptor.capture());
    assertThat(notificationChannelArgumentCaptor.getValue().getTemplateId()).isEqualTo(EMAIL_INVITE_TEMPLATE_ID);
    assertThat(notificationChannelArgumentCaptor.getValue().getTemplateData().get(SHOULD_MAIL_CONTAIN_TWO_FACTOR_INFO))
        .isEqualTo("true");
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUserCreation_scimUser() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());

    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    Invite invite = Invite.builder()
                        .accountIdentifier("accountId")
                        .approved(true)
                        .email("primaryEmail")
                        .name("displayName")
                        .givenName("givenName")
                        .familyName("familyName")
                        .externalId("externalId")
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .inviteType(InviteType.SCIM_INITIATED_INVITE)
                        .id(inviteId)
                        .roleBindings(getDummyRoleBinding())
                        .build();
    when(inviteRepository.save(any())).thenReturn(invite);
    ArgumentCaptor<UserInviteDTO> argumentCaptor = ArgumentCaptor.forClass(UserInviteDTO.class);
    ArgumentCaptor<Boolean> argumentCaptor1 = ArgumentCaptor.forClass(Boolean.class);
    ArgumentCaptor<Boolean> argumentCaptor2 = ArgumentCaptor.forClass(Boolean.class);
    Call<RestResponse<Boolean>> userCall = mock(Call.class);
    when(userClient.createUserAndCompleteNGInvite(
             argumentCaptor.capture(), argumentCaptor1.capture(), argumentCaptor2.capture()))
        .thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    inviteService.create(projectScopeInfo, invite, true, false);
    assertThat(argumentCaptor1.getValue()).isEqualTo(true);
    assertThat(argumentCaptor2.getValue()).isEqualTo(false);
    assertThat(argumentCaptor.getValue().getName()).isEqualTo("displayName");
    assertThat(argumentCaptor.getValue().getEmail()).isEqualTo("primaryEmail");
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUserCreation_withoutName_shouldPopulateEmailInNameField() throws IOException {
    when(ngUserService.getUserByEmail(eq(emailId), anyBoolean())).thenReturn(Optional.empty());

    Call<RestResponse<Boolean>> ffCall = mock(Call.class);
    when(accountClient.checkAutoInviteAcceptanceEnabledForAccount(any())).thenReturn(ffCall);
    when(ffCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    Invite invite = Invite.builder()
                        .accountIdentifier("accountId")
                        .approved(true)
                        .email("primaryEmail")
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .id(inviteId)
                        .roleBindings(getDummyRoleBinding())
                        .inviteType(InviteType.ADMIN_INITIATED_INVITE)
                        .build();
    when(inviteRepository.save(any())).thenReturn(invite);
    ArgumentCaptor<UserInviteDTO> argumentCaptor = ArgumentCaptor.forClass(UserInviteDTO.class);
    ArgumentCaptor<Boolean> argumentCaptor1 = ArgumentCaptor.forClass(Boolean.class);
    ArgumentCaptor<Boolean> argumentCaptor2 = ArgumentCaptor.forClass(Boolean.class);
    Call<RestResponse<Boolean>> userCall = mock(Call.class);
    when(userClient.createUserAndCompleteNGInvite(
             argumentCaptor.capture(), argumentCaptor1.capture(), argumentCaptor2.capture()))
        .thenReturn(userCall);
    when(userCall.execute()).thenReturn(Response.success(new RestResponse<>(true)));
    inviteService.create(projectScopeInfo, invite, true, false);
    assertThat(argumentCaptor1.getValue()).isEqualTo(true);
    assertThat(argumentCaptor2.getValue()).isEqualTo(false);
    assertThat(argumentCaptor.getValue().getName()).isEqualTo("primaryEmail");
    assertThat(argumentCaptor.getValue().getEmail()).isEqualTo("primaryEmail");
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetRedirectUrl_ExistingPasswordUser_shouldSend2fa() throws IOException {
    String userId = randomAlphabetic(10);

    InviteAcceptResponse inviteAcceptResponse = InviteAcceptResponse.builder()
                                                    .response(USER_INVITED_SUCCESSFULLY)
                                                    .userInfo(UserInfo.builder().uuid(userId).email(emailId).build())
                                                    .accountIdentifier(accountIdentifier)
                                                    .build();
    AccountDTO accountDTO = AccountDTO.builder()
                                .authenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                                .isTwoFactorAdminEnforced(true)
                                .companyName(randomAlphabetic(7))
                                .build();

    when(ngUserService.isUserPasswordSet(accountIdentifier, emailId)).thenReturn(true);
    when(accountClient.getAccountDTO(accountIdentifier).execute())
        .thenReturn(Response.success(new RestResponse(accountDTO)));
    when(userClient.updateUserTwoFactorAuthInfo(eq(emailId), any(TwoFactorAuthSettingsInfo.class)).execute())
        .thenReturn(Response.success(new RestResponse(of(UserInfo.builder().build()))));

    Call call = mock(Call.class);
    when(userClient.sendTwoFactorAuthenticationResetEmail(userId, accountIdentifier)).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(new RestResponse(true)));

    URI uri = inviteService.getRedirectUrl(
        inviteAcceptResponse, URLEncoder.encode(emailId, "UTF-8"), emailId, randomAlphabetic(10));

    assertThat(uri).isNotNull();
    assertThat(uri).isEqualTo(URI.create(String.format("/ng/#/account/%s", accountIdentifier)));
    verify(userClient, times(1)).sendTwoFactorAuthenticationResetEmail(userId, accountIdentifier);
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testGetRedirectUrl_ExistingPasswordUser_shouldNotSend2fa() throws IOException {
    String userId = randomAlphabetic(10);
    InviteAcceptResponse inviteAcceptResponse =
        InviteAcceptResponse.builder()
            .response(USER_INVITED_SUCCESSFULLY)
            .userInfo(UserInfo.builder().uuid(userId).email(emailId).twoFactorAuthenticationEnabled(true).build())
            .accountIdentifier(accountIdentifier)
            .build();
    when(ngUserService.isUserPasswordSet(accountIdentifier, emailId)).thenReturn(true);

    AccountDTO accountDTO = AccountDTO.builder()
                                .authenticationMechanism(AuthenticationMechanism.USER_PASSWORD)
                                .isTwoFactorAdminEnforced(true)
                                .companyName(randomAlphabetic(7))
                                .build();
    when(accountClient.getAccountDTO(accountIdentifier).execute())
        .thenReturn(Response.success(new RestResponse(accountDTO)));

    Call call = mock(Call.class);
    when(userClient.sendTwoFactorAuthenticationResetEmail(userId, accountIdentifier)).thenReturn(call);
    when(call.execute()).thenReturn(Response.success(new RestResponse(true)));

    URI uri = inviteService.getRedirectUrl(
        inviteAcceptResponse, URLEncoder.encode(emailId, "UTF-8"), emailId, randomAlphabetic(10));

    assertThat(uri).isNotNull();
    assertThat(uri).isEqualTo(URI.create(String.format("/ng/#/account/%s", accountIdentifier)));
    verify(userClient, never()).sendTwoFactorAuthenticationResetEmail(any(String.class), any(String.class));
  }

  @Test
  @Owner(developers = TEJAS)
  @Category(UnitTests.class)
  public void testCreate_ResendInvite() throws IOException {
    UserMetadataDTO user = UserMetadataDTO.builder().name(randomAlphabetic(7)).email(emailId).uuid(userId).build();
    when(ngUserService.getUserByEmail(any(), anyBoolean())).thenReturn(of(user));
    Invite dummyInvite = getDummyInvite();
    when(inviteRepository.save(any())).thenReturn(dummyInvite);
    when(inviteRepository.findFirstByAccountIdentifierAndParentUniqueIdAndEmailAndDeletedFalse(any(), any(), any()))
        .thenReturn(of(dummyInvite));

    InviteOperationResponse inviteOperationResponse = inviteService.create(projectScopeInfo, dummyInvite, false, false);

    ArgumentCaptor<Update> updateArgumentCaptor = ArgumentCaptor.forClass(Update.class);
    ArgumentCaptor<String> inviteIdArgumentCaptor = ArgumentCaptor.forClass(String.class);

    verify(inviteRepository, atLeast(1)).updateInvite(inviteIdArgumentCaptor.capture(), updateArgumentCaptor.capture());
    assertThat(inviteIdArgumentCaptor.getValue()).isEqualTo(dummyInvite.getId());
    assertThat(((Document) updateArgumentCaptor.getAllValues().get(0).getUpdateObject().get("$set")).get("createdAt"))
        .isInstanceOf(Long.class);

    assertThat(inviteOperationResponse).isEqualTo(USER_ALREADY_INVITED);
    verify(notificationClient, times(1)).sendNotificationAsync(any());
    verify(userClient, times(0)).updateUserTwoFactorAuthInfo(eq(emailId), any(TwoFactorAuthSettingsInfo.class));
  }
}
