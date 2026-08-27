/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.invites.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorMessageConstants.TOKEN_EXPIRED;
import static io.harness.exception.WingsException.USER_SRE;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.ng.accesscontrol.PlatformPermissions.INVITE_PERMISSION_IDENTIFIER;
import static io.harness.ng.core.common.beans.UserSource.LDAP;
import static io.harness.ng.core.common.beans.UserSource.MANUAL;
import static io.harness.ng.core.common.beans.UserSource.SCIM;
import static io.harness.ng.core.invites.InviteType.ADMIN_INITIATED_INVITE;
import static io.harness.ng.core.invites.InviteType.USER_INITIATED_INVITE;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.FAIL;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.INVITE_EXPIRED;
import static io.harness.ng.core.invites.dto.InviteOperationResponse.INVITE_INVALID;
import static io.harness.ng.core.invites.mapper.InviteMapper.toInviteList;
import static io.harness.ng.core.invites.mapper.InviteMapper.writeDTO;
import static io.harness.ng.core.invites.mapper.RoleBindingMapper.sanitizeRoleBindings;
import static io.harness.ng.core.user.UserMembershipUpdateSource.ACCEPTED_INVITE;
import static io.harness.springdata.PersistenceUtils.getRetryPolicy;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.NgInviteLogContext;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.account.services.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.ExpiredTokenException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidTokenException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.invites.remote.InviteAcceptResponse;
import io.harness.logging.AutoLogContext;
import io.harness.mongo.MongoConfig;
import io.harness.ng.accesscontrol.user.ACLAggregateFilter;
import io.harness.ng.beans.PageRequest;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.AccountOrgProjectHelper;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.common.beans.UserSource;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.dto.UserInviteDTO;
import io.harness.ng.core.dto.UserInviteDTO.UserInviteDTOBuilder;
import io.harness.ng.core.events.UserInviteCreateEvent;
import io.harness.ng.core.events.UserInviteDeleteEvent;
import io.harness.ng.core.events.UserInviteUpdateEvent;
import io.harness.ng.core.invites.JWTGeneratorUtils;
import io.harness.ng.core.invites.api.InviteService;
import io.harness.ng.core.invites.dto.CreateInviteDTO;
import io.harness.ng.core.invites.dto.InviteDTO;
import io.harness.ng.core.invites.dto.InviteOperationResponse;
import io.harness.ng.core.invites.dto.RoleBinding;
import io.harness.ng.core.invites.dto.RoleBinding.RoleBindingKeys;
import io.harness.ng.core.invites.dto.UserSettingFromInvite;
import io.harness.ng.core.invites.entities.Invite;
import io.harness.ng.core.invites.entities.Invite.InviteKeys;
import io.harness.ng.core.invites.utils.InviteUtils;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.user.TwoFactorAuthMechanismInfo;
import io.harness.ng.core.user.TwoFactorAuthSettingsInfo;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.UserMembershipUpdateSource;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.ngsettings.SettingUpdateType;
import io.harness.ngsettings.dto.UserSettingRequestDTO;
import io.harness.ngsettings.services.UserSettingsService;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.EmailChannel;
import io.harness.notification.channeldetails.EmailChannel.EmailChannelBuilder;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.notificationclient.NotificationResult;
import io.harness.outbox.api.OutboxService;
import io.harness.remote.client.CGRestUtils;
import io.harness.repositories.invites.spring.InviteRepository;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetryReporter;
import io.harness.user.remote.UserClient;
import io.harness.user.remote.UserFilterNG;
import io.harness.utils.PageUtils;
import io.harness.utils.PmsFeatureFlagHelper;
import io.harness.utils.UserHelperService;
import io.harness.utils.UserUtils;

import com.auth0.jwt.interfaces.Claim;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;
import com.mongodb.MongoClientURI;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.transaction.support.TransactionTemplate;

@Singleton
@Slf4j
@OwnedBy(PL)
public class InviteServiceImpl implements InviteService {
  public static final String NG_INVITE_THREAD_EXECUTOR = "ngInviteExecutorService";
  private static final int INVITATION_VALIDITY_IN_DAYS = 30;
  private static final int LINK_VALIDITY_IN_DAYS = 7;
  private static final int DEFAULT_PAGE_SIZE = 1000;
  private static final String INVITE_URL =
      "/invite?accountId=%s&account=%s&company=%s&email=%s&inviteId=%s&generation=NG";
  private static final String NG_AUTH_UI_PATH_PREFIX = "auth/";
  private static final String NG_ACCOUNT_CREATION_FRAGMENT =
      "accountIdentifier=%s&email=%s&token=%s&returnUrl=%s&generation=NG";
  private static final String ACCEPT_INVITE_PATH = "ng/api/invites/verify";
  private static final String USER_INVITE = "user_invite";
  private static final String EMAIL_INVITE_TEMPLATE_ID = "email_invite";
  private static final String EMAIL_NOTIFY_TEMPLATE_ID = "email_notify";
  private static final String SHOULD_MAIL_CONTAIN_TWO_FACTOR_INFO = "shouldMailContainTwoFactorInfo";
  private static final String TOTP_SECRET = "totpSecret";
  private static final String LANDING_PAGE_URL = "landing_page_url";
  private static final String TOTP_URL = "totpUrl";
  private static final String TOTP_URL_PREFIX = "otpauth://totp/%s:%s?secret=%s&issuer=Harness-Inc";
  private final String jwtPasswordSecret;
  private final JWTGeneratorUtils jwtGeneratorUtils;
  private final NgUserService ngUserService;
  private final InviteRepository inviteRepository;
  private final boolean useMongoTransactions;
  private final TransactionTemplate transactionTemplate;
  private final NotificationClient notificationClient;
  private final AccountClient accountClient;
  private final OutboxService outboxService;
  private final AccessControlClient accessControlClient;
  private final boolean isNgAuthUIEnabled;
  private final UserClient userClient;
  private final AccountOrgProjectHelper accountOrgProjectHelper;
  private final TelemetryReporter telemetryReporter;
  private final PmsFeatureFlagHelper ngFeatureFlagHelperService;
  private final ScheduledExecutorService scheduledExecutor;
  private final EnforcementClientService enforcementClientService;
  private final ScopeInfoService scopeInfoService;
  @Inject UserSettingsService userSettingsService;

  private final UserHelperService userHelperService;

  private final RetryPolicy<Object> transactionRetryPolicy =
      getRetryPolicy("[Retrying]: Failed to mark previous invites as stale; attempt: {}",
          "[Failed]: Failed to mark previous invites as stale; attempt: {}");

  @Inject
  public InviteServiceImpl(@Named("userVerificationSecret") String jwtPasswordSecret, MongoConfig mongoConfig,
      JWTGeneratorUtils jwtGeneratorUtils, NgUserService ngUserService, TransactionTemplate transactionTemplate,
      InviteRepository inviteRepository, NotificationClient notificationClient, AccountClient accountClient,
      OutboxService outboxService, AccessControlClient accessControlClient, UserClient userClient,
      AccountOrgProjectHelper accountOrgProjectHelper, @Named("isNgAuthUIEnabled") boolean isNgAuthUIEnabled,
      TelemetryReporter telemetryReporter, PmsFeatureFlagHelper ngFeatureFlagHelperService,
      @Named(NG_INVITE_THREAD_EXECUTOR) ScheduledExecutorService scheduledExecutorService,
      EnforcementClientService enforcementClientService, UserHelperService userHelperService,
      ScopeInfoService scopeInfoService) {
    this.jwtPasswordSecret = jwtPasswordSecret;
    this.jwtGeneratorUtils = jwtGeneratorUtils;
    this.ngUserService = ngUserService;
    this.inviteRepository = inviteRepository;
    this.transactionTemplate = transactionTemplate;
    this.notificationClient = notificationClient;
    this.accountClient = accountClient;
    this.userClient = userClient;
    this.outboxService = outboxService;
    this.isNgAuthUIEnabled = isNgAuthUIEnabled;
    this.accessControlClient = accessControlClient;
    this.accountOrgProjectHelper = accountOrgProjectHelper;
    this.telemetryReporter = telemetryReporter;
    this.ngFeatureFlagHelperService = ngFeatureFlagHelperService;
    this.scheduledExecutor = scheduledExecutorService;
    this.enforcementClientService = enforcementClientService;
    this.userHelperService = userHelperService;
    this.scopeInfoService = scopeInfoService;
    MongoClientURI uri = new MongoClientURI(mongoConfig.getUri());
    useMongoTransactions = uri.getHosts().size() > 2;
  }

  @Override
  public InviteOperationResponse create(ScopeInfo scopeInfo, Invite invite, boolean isScimInvite, boolean isLdap) {
    if (invite == null) {
      return FAIL;
    }
    checkPermissions(scopeInfo.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(),
        INVITE_PERMISSION_IDENTIFIER);
    preCreateInvite(scopeInfo, invite);
    if (checkIfUserAlreadyAdded(scopeInfo, invite)) {
      return InviteOperationResponse.USER_ALREADY_ADDED;
    }

    if (!isScimInvite && !isLdap) {
      checkInviteRateLimit(invite.getAccountIdentifier());
    }

    Optional<Invite> existingInviteOptional = getExistingInvite(invite, scopeInfo);
    if (isScimInvite && existingInviteOptional.isPresent()
        && FALSE.equals(existingInviteOptional.get().getApproved())) {
      inviteRepository.delete(existingInviteOptional.get());
      existingInviteOptional = Optional.empty();
    }
    if (existingInviteOptional.isPresent()) {
      if (TRUE.equals(existingInviteOptional.get().getApproved())) {
        return InviteOperationResponse.ACCOUNT_INVITE_ACCEPTED;
      }
      log.info("NG User Invite: resending the existing invite with inviteId {}", existingInviteOptional.get().getId());

      wrapperForTransactions(this::resendInvite, existingInviteOptional.get(), scopeInfo);

      return InviteOperationResponse.USER_ALREADY_INVITED;
    }
    log.info("INVITE_NPE: Check create invite flow, existingInviteOptional {}, preCreated invite {}",
        existingInviteOptional.isPresent() ? existingInviteOptional.get() : null, invite);
    boolean[] scimLdapArray = {isScimInvite, isLdap};
    try {
      return wrapperForTransactions(this::newInvite, invite, scimLdapArray, scopeInfo);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(getExceptionMessage(invite, scopeInfo), USER_SRE, ex);
    }
  }

  @Override
  public List<InviteOperationResponse> createInvitations(ScopeInfo scopeInfo, CreateInviteDTO createInviteDTO) {
    List<InviteOperationResponse> inviteOperationResponses = new ArrayList<>();
    List<Invite> invites = toInviteList(createInviteDTO, scopeInfo);
    for (Invite invite : invites) {
      try {
        InviteOperationResponse response = create(scopeInfo, invite, false, false);
        inviteOperationResponses.add(response);
      } catch (DuplicateFieldException ex) {
        log.error("error: ", ex);
      }
    }
    return inviteOperationResponses;
  }

  private void preCreateInvite(ScopeInfo scopeInfo, Invite invite) {
    List<RoleBinding> roleBindings = invite.getRoleBindings();
    sanitizeRoleBindings(roleBindings, scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier());
  }

  private boolean checkIfUserAlreadyAdded(ScopeInfo scopeInfo, Invite invite) {
    Optional<UserMetadataDTO> userOptional = ngUserService.getUserByEmail(invite.getEmail(), false);
    return userOptional.filter(user -> ngUserService.isUserAtScope(user.getUuid(), scopeInfo)).isPresent();
  }

  @Override
  public Optional<Invite> getInvite(String inviteId, boolean allowDeleted) {
    return allowDeleted ? inviteRepository.findById(inviteId)
                        : inviteRepository.findFirstByIdAndDeleted(inviteId, FALSE);
  }

  @Override
  public PageResponse<Invite> getInvites(Criteria criteria, PageRequest pageRequest) {
    Pageable pageable = PageUtils.getPageRequest(pageRequest);
    return PageUtils.getNGPageResponse(inviteRepository.findAllWithCollation(criteria, pageable));
  }

  @Override
  public PageResponse<InviteDTO> getPendingInvites(
      ScopeInfo scopeInfo, String searchTerm, PageRequest pageRequest, ACLAggregateFilter aclAggregateFilter) {
    validateRequest(searchTerm, aclAggregateFilter);
    PageResponse<Invite> invitesPage = getInvitePage(scopeInfo, searchTerm, pageRequest, aclAggregateFilter);
    List<String> userEmails = invitesPage.getContent().stream().map(Invite::getEmail).collect(toList());
    Map<String, UserMetadataDTO> userSearchDTOs = getPendingUserMap(userEmails, scopeInfo.getAccountIdentifier());
    List<InviteDTO> inviteDTOS = aggregatePendingUsers(invitesPage.getContent(), userSearchDTOs);
    return PageUtils.getNGPageResponse(invitesPage, inviteDTOS);
  }

  @Override
  public Optional<Invite> deleteInvite(Invite invite, ScopeInfo scopeInfo) {
    String orgIdentifier = scopeInfo.getOrgIdentifier();
    String projectIdentifier = scopeInfo.getProjectIdentifier();

    checkPermissions(invite.getAccountIdentifier(), orgIdentifier, projectIdentifier, INVITE_PERMISSION_IDENTIFIER);
    Update update = new Update().set(InviteKeys.deleted, TRUE);
    Invite updatedInvite = inviteRepository.updateInvite(invite.getId(), update);
    if (updatedInvite != null) {
      outboxService.save(new UserInviteDeleteEvent(
          updatedInvite.getAccountIdentifier(), orgIdentifier, projectIdentifier, writeDTO(updatedInvite, scopeInfo)));
    }
    return updatedInvite == null ? Optional.empty() : Optional.of(updatedInvite);
  }

  @Override
  public boolean isUserPasswordSet(String accountIdentifier, String email) {
    return ngUserService.isUserPasswordSet(accountIdentifier, email);
  }

  @Override
  public URI getRedirectUrl(
      InviteAcceptResponse inviteAcceptResponse, String email, String decodedEmail, String jwtToken) {
    String accountIdentifier = inviteAcceptResponse.getAccountIdentifier();
    if (inviteAcceptResponse.getResponse().equals(INVITE_EXPIRED)
        || inviteAcceptResponse.getResponse().equals(INVITE_INVALID)) {
      return getLoginPageUrl(accountIdentifier, inviteAcceptResponse.getResponse());
    }

    UserInfo userInfo = inviteAcceptResponse.getUserInfo();
    AccountDTO account = CGRestUtils.getResponse(accountClient.getAccountDTO(accountIdentifier));
    if (account == null) {
      throw new IllegalStateException(String.format("Account with identifier [%s] doesn't exists", accountIdentifier));
    }

    AuthenticationMechanism authMechanism = account.getAuthenticationMechanism();
    boolean isPasswordRequired =
        authMechanism == null || (authMechanism == AuthenticationMechanism.USER_PASSWORD && !account.isOauthEnabled());

    String landing_page_url = getLandingPageUrlFromInvite(inviteAcceptResponse.getInviteId());

    String baseUrl = accountOrgProjectHelper.getBaseUrl(accountIdentifier);
    URI resourceUrl = InviteUtils.getResourceUrl(baseUrl, accountIdentifier, inviteAcceptResponse.getOrgIdentifier(),
        inviteAcceptResponse.getProjectIdentifier(), landing_page_url);

    if (userInfo == null) {
      if (isPasswordRequired) {
        return getUserInfoSubmitUrl(baseUrl, resourceUrl, email, jwtToken, inviteAcceptResponse);
      } else {
        createAndInviteNonPasswordUser(
            accountIdentifier, jwtToken, decodedEmail.trim(), false, true, null, null, null, null, MANUAL);
        return resourceUrl;
      }
    } else {
      boolean isUserPasswordSet = isUserPasswordSet(accountIdentifier, userInfo.getEmail());
      if (isPasswordRequired && !isUserPasswordSet) {
        return getUserInfoSubmitUrl(baseUrl, resourceUrl, email, jwtToken, inviteAcceptResponse);
      } else {
        Optional<Invite> inviteOpt = getInviteFromToken(jwtToken, false);
        completeInvite(inviteOpt);

        TwoFactorAuthSettingsInfo twoFactorAuthSettingsInfo = getTwoFactorAuthSettingsInfo(accountIdentifier, email);
        if (twoFactorAuthSettingsInfo.isTwoFactorAuthenticationEnabled()
            && !userInfo.isTwoFactorAuthenticationEnabled()) {
          updateUserTwoFactorAuthInfo(userInfo.getEmail(), twoFactorAuthSettingsInfo);
          CGRestUtils.getResponse(
              userClient.sendTwoFactorAuthenticationResetEmail(userInfo.getUuid(), accountIdentifier),
              String.format("Failed to send two factor authentication setup email for user %s", userInfo.getUuid()));
        }
        return resourceUrl;
      }
    }
  }

  private String getLandingPageUrlFromInvite(String inviteId) {
    Optional<Invite> inviteOptional = getInvite(inviteId, false);
    if (inviteOptional.isPresent() && inviteOptional.get().getUserSettings() != null) {
      for (UserSettingFromInvite userPreference : inviteOptional.get().getUserSettings()) {
        if (userPreference.getIdentifier().equals(LANDING_PAGE_URL)) {
          return userPreference.getValue();
        }
      }
    }
    return null;
  }

  List<UserSettingRequestDTO> getUserSettingRequestDTO(List<UserSettingFromInvite> userSettingDTOSList) {
    List<UserSettingRequestDTO> userSettingRequestDTOList = new ArrayList<>();
    for (UserSettingFromInvite userSettingFromInvite : userSettingDTOSList) {
      UserSettingRequestDTO userSettingRequestDTO = UserSettingRequestDTO.builder()
                                                        .identifier(userSettingFromInvite.getIdentifier())
                                                        .value(userSettingFromInvite.getValue())
                                                        .updateType(SettingUpdateType.UPDATE)
                                                        .build();
      userSettingRequestDTOList.add(userSettingRequestDTO);
    }
    return userSettingRequestDTOList;
  }

  @Override
  public String getInviteLinkFromInviteId(String inviteId) {
    Invite invite = getInvite(inviteId, false).<InvalidRequestException>orElseThrow(() -> {
      throw new InvalidRequestException("Invalid or Expired Invite Id");
    });
    try {
      return isNgAuthUIEnabled ? getAcceptInviteUrl(invite) : getInvitationMailEmbedUrl(invite);
    } catch (URISyntaxException | UnsupportedEncodingException e) {
      log.error("URL format incorrect. Cannot create invite link. InviteId: " + invite.getId(), e);
      throw new UnexpectedException("Could not create invite link. Unexpectedly failed due to malformed URL.");
    }
  }

  @Override
  public void deleteAtAllScopes(ScopeInfo scopeInfo) {
    Criteria criteria = createScopeCriteria(scopeInfo);
    inviteRepository.deleteAll(criteria);
  }

  private Criteria createScopeCriteria(ScopeInfo scopeInfo) {
    Criteria criteria = new Criteria();
    criteria.and(InviteKeys.accountIdentifier).is(scopeInfo.getAccountIdentifier());
    criteria.and(InviteKeys.parentUniqueId).is(scopeInfo.getUniqueId());
    return criteria;
  }

  private void checkUserLimit(String accountId, String emailId) {
    boolean limitHasBeenReached = CGRestUtils.getResponse(userClient.checkUserLimit(accountId, emailId));
    if (limitHasBeenReached) {
      throw new InvalidRequestException("The user count limit has been reached in this account");
    }
  }

  private void checkNgLimit(String accountId) {
    enforcementClientService.checkAvailabilityWithIncrement(FeatureRestrictionName.MULTIPLE_USERS, accountId, 1);
  }

  // Separate from checkNgLimit: that one caps seats and counts memberships, so pending invites are
  // invisible to it and an account far below its seat limit can still send unbounded invites.
  // Called from create() for both new invites and resends, so existing members in a request are
  // not charged, and SCIM/LDAP sync (which call create() directly) are covered too.
  private void checkInviteRateLimit(String accountId) {
    try {
      enforcementClientService.checkAvailabilityWithIncrement(FeatureRestrictionName.INVITE_RATE_LIMIT, accountId, 1);
    } catch (LimitExceededException ex) {
      log.info("NG User Invite: invite rate limit reached for account {}", accountId);
      throw ex;
    }
  }

  private void createAndInviteNonPasswordUser(String accountIdentifier, String jwtToken, String email,
      boolean isScimInvite, boolean shouldSendTwoFactorAuthResetEmail, String displayName, String givenName,
      String familyName, String externalId, UserSource userSource) {
    UserInviteDTOBuilder userInviteDTOBuilder = UserInviteDTO.builder()
                                                    .accountId(accountIdentifier)
                                                    .email(email)
                                                    .name(displayName != null ? displayName : email)
                                                    .givenName(givenName)
                                                    .familyName(familyName)
                                                    .externalId(externalId)
                                                    .token(jwtToken);

    try {
      if (ngFeatureFlagHelperService.isEnabled(accountIdentifier, FeatureName.PL_USER_ACCOUNT_LEVEL_DATA_FLOW)) {
        userInviteDTOBuilder.userSource(userSource);
        log.info("NG User Invite: With Account Level Data: Calling CG to create User in account: {} for email: {}, "
                + "userSource: {}, set2FA: {}",
            accountIdentifier, email, userSource, shouldSendTwoFactorAuthResetEmail);
        CGRestUtils.getResponse(userClient.createUserWithAccountLevelDataAndCompleteNGInvite(
            userInviteDTOBuilder.build(), shouldSendTwoFactorAuthResetEmail));
      } else {
        log.info("NG User Invite: Calling CG to create User in account: {} for email: {}, isSCIM: {}, set2FA: {}",
            accountIdentifier, email, isScimInvite, shouldSendTwoFactorAuthResetEmail);
        CGRestUtils.getResponse(userClient.createUserAndCompleteNGInvite(
            userInviteDTOBuilder.build(), isScimInvite, shouldSendTwoFactorAuthResetEmail));
      }
    } catch (Exception ex) {
      log.error("NG User Invite: While making a userClient call to CG to Create User an exception occurred: ", ex);
      throw ex;
    }
  }

  private URI getUserInfoSubmitUrl(
      String baseUrl, URI resourceUrl, String email, String jwtToken, InviteAcceptResponse inviteAcceptResponse) {
    String accountIdentifier = inviteAcceptResponse.getAccountIdentifier();
    try {
      String accountCreationFragment =
          String.format(NG_ACCOUNT_CREATION_FRAGMENT, accountIdentifier, email, jwtToken, resourceUrl);
      URIBuilder uriBuilder = new URIBuilder(baseUrl);
      uriBuilder.setPath(NG_AUTH_UI_PATH_PREFIX);
      uriBuilder.setFragment("/accept-invite?" + accountCreationFragment);
      return uriBuilder.build();
    } catch (URISyntaxException e) {
      throw new WingsException(e);
    }
  }

  private URI getLoginPageUrl(String accountIdentifier, InviteOperationResponse inviteOperationResponse) {
    try {
      String baseUrl = accountOrgProjectHelper.getBaseUrl(accountIdentifier);
      URIBuilder uriBuilder = new URIBuilder(baseUrl);
      uriBuilder.setPath(NG_AUTH_UI_PATH_PREFIX);
      uriBuilder.setFragment("/signin?errorCode=" + inviteOperationResponse.getType());
      return uriBuilder.build();
    } catch (URISyntaxException e) {
      throw new WingsException(e);
    }
  }

  private Invite resendInvite(Invite newInvite, ScopeInfo scopeInfo) {
    try (AutoLogContext ignore =
             new NgInviteLogContext(newInvite.getAccountIdentifier(), newInvite.getId(), OVERRIDE_ERROR)) {
      checkPermissions(newInvite.getAccountIdentifier(), scopeInfo.getOrgIdentifier(), scopeInfo.getProjectIdentifier(),
          INVITE_PERMISSION_IDENTIFIER);

      Update update = new Update()
                          .set(InviteKeys.createdAt, Instant.now().toEpochMilli())
                          .set(InviteKeys.validUntil,
                              Date.from(OffsetDateTime.now().plusDays(INVITATION_VALIDITY_IN_DAYS).toInstant()))
                          .set(InviteKeys.roleBindings, newInvite.getRoleBindings())
                          .set(InviteKeys.userGroups, newInvite.getUserGroups());
      inviteRepository.updateInvite(newInvite.getId(), update);
      String accountId = newInvite.getAccountIdentifier();
      boolean isPLNoEmailForSamlAccountInvitesEnabled = isPLNoEmailForSamlAccountInvitesEnabled(accountId);
      boolean isAutoInviteAcceptanceEnabled = isAutoInviteAcceptanceEnabled(accountId);
      TwoFactorAuthSettingsInfo twoFactorAuthSettingsInfo =
          getTwoFactorAuthSettingsInfo(accountId, newInvite.getEmail());

      try {
        sendInvitationMail(newInvite, isPLNoEmailForSamlAccountInvitesEnabled, isAutoInviteAcceptanceEnabled,
            twoFactorAuthSettingsInfo, scopeInfo);
      } catch (URISyntaxException ex) {
        log.error(
            "For invite: {} Mail embed url incorrect, can't sent email due to an exception: ", newInvite.getId(), ex);
      } catch (UnsupportedEncodingException ex) {
        log.error("For invite: {} Invite Email resending failed due to encoding exception: ", newInvite.getId(), ex);
      } catch (Exception ex) {
        log.error("For invite: {} while inviting or notifying user to join harness an exception occurred: ",
            newInvite.getId(), ex);
      }

      if (!(isPLNoEmailForSamlAccountInvitesEnabled && !twoFactorAuthSettingsInfo.isTwoFactorAuthenticationEnabled())) {
        ngAuditUserInviteUpdateEvent(newInvite, scopeInfo);
      }
      return newInvite;
    }
  }

  public InviteAcceptResponse acceptInvite(String jwtToken) {
    Optional<Invite> inviteOptional = null;
    try {
      inviteOptional = getInviteFromTokenV2(jwtToken, true);
    } catch (ExpiredTokenException e) {
      return InviteAcceptResponse.builder().response(INVITE_EXPIRED).build();
    }
    if (!inviteOptional.isPresent() || !inviteOptional.get().getInviteToken().equals(jwtToken)) {
      log.warn("Invite token {} is invalid", jwtToken);
      return InviteAcceptResponse.builder().response(INVITE_INVALID).build();
    }
    Invite invite = inviteOptional.get();
    Date today = Date.from(OffsetDateTime.now().toInstant());
    Date validUntil = invite.getValidUntil();
    if (validUntil.compareTo(today) < 0) {
      log.warn("Invite expired");
      return InviteAcceptResponse.builder().response(INVITE_EXPIRED).build();
    }
    Optional<UserMetadataDTO> ngUserOpt = ngUserService.getUserByEmail(invite.getEmail(), true);
    UserInfo userInfo = ngUserOpt
                            .map(user
                                -> UserInfo.builder()
                                       .uuid(user.getUuid())
                                       .name(user.getName())
                                       .email(user.getEmail())
                                       .locked(user.isLocked())
                                       .disabled(user.isDisabled())
                                       .externallyManaged(user.isExternallyManaged())
                                       .build())
                            .orElse(null);

    ScopeInfo scopeInfo =
        scopeInfoService.getScopeInfo(invite.getAccountIdentifier(), Collections.singleton(invite.getParentUniqueId()))
            .get(invite.getParentUniqueId())
            .orElseThrow(
                () -> new NotFoundException("ScopeInfo not found for parentUniqueId: " + invite.getParentUniqueId()));

    markInviteApproved(invite);
    return InviteAcceptResponse.builder()
        .response(InviteOperationResponse.ACCOUNT_INVITE_ACCEPTED)
        .userInfo(userInfo)
        .accountIdentifier(scopeInfo.getAccountIdentifier())
        .orgIdentifier(scopeInfo.getOrgIdentifier())
        .projectIdentifier(scopeInfo.getProjectIdentifier())
        .inviteId(invite.getId())
        .email(invite.getEmail())
        .build();
  }

  @Override
  public Optional<Invite> getInviteFromToken(String jwtToken, boolean allowDeleted) {
    if (isBlank(jwtToken)) {
      return Optional.empty();
    }
    Optional<String> inviteIdOptional = Optional.empty();
    try {
      inviteIdOptional = getInviteIdFromToken(jwtToken);
    } catch (InvalidRequestException e) {
      log.error("Invalid invite JWT token", e);
    }
    if (!inviteIdOptional.isPresent()) {
      log.warn("Invalid token. verification failed");
      return Optional.empty();
    }
    return getInvite(inviteIdOptional.get(), allowDeleted);
  }

  @Override
  public Optional<Invite> getInviteFromTokenV2(String jwtToken, boolean allowDeleted) {
    if (isBlank(jwtToken)) {
      return Optional.empty();
    }
    Optional<String> inviteIdOptional = Optional.empty();
    try {
      inviteIdOptional = getInviteIdFromTokenV2(jwtToken);
    } catch (ExpiredTokenException e) {
      throw new ExpiredTokenException(TOKEN_EXPIRED, WingsException.USER);
    } catch (InvalidTokenException e) {
      log.error("Invalid invite JWT token", e);
    }
    if (inviteIdOptional.isEmpty()) {
      log.warn("Invalid token. verification failed");
      return Optional.empty();
    }
    return getInvite(inviteIdOptional.get(), allowDeleted);
  }

  @Override
  public Optional<Invite> updateInvite(ScopeInfo scopeInfo, Invite updatedInvite) {
    if (updatedInvite == null) {
      return Optional.empty();
    }
    if (isNotEmpty(updatedInvite.getName())) {
      updatedInvite.setName(UserUtils.sanitizeUserName(updatedInvite.getName()));
    }
    preCreateInvite(scopeInfo, updatedInvite);
    Optional<Invite> inviteOptional = getInvite(updatedInvite.getId(), false);
    if (!inviteOptional.isPresent() || TRUE.equals(inviteOptional.get().getApproved())) {
      return Optional.empty();
    }

    Invite existingInvite = inviteOptional.get();
    updatedInvite.setAccountIdentifier(existingInvite.getAccountIdentifier());
    updatedInvite.setOrgIdentifier(scopeInfo.getOrgIdentifier());
    updatedInvite.setProjectIdentifier(scopeInfo.getProjectIdentifier());
    updatedInvite.setUniqueId(existingInvite.getUniqueId());
    updatedInvite.setParentUniqueId(existingInvite.getParentUniqueId());

    Preconditions.checkState(updatedInvite.getEmail().equals(existingInvite.getEmail()),
        "Can't update recipient of an Invite after creation");

    if (existingInvite.getInviteType() == ADMIN_INITIATED_INVITE) {
      wrapperForTransactions(this::resendInvite, updatedInvite, scopeInfo);

      return Optional.of(updatedInvite);
    } else if (existingInvite.getInviteType() == USER_INITIATED_INVITE) {
      throw new UnsupportedOperationException("User initiated requests are not supported yet");
    }
    return Optional.empty();
  }

  private String getExceptionMessage(Invite invite, ScopeInfo scopeInfo) {
    String message = String.format("Invite [%s] under account [%s] ", invite.getId(), invite.getAccountIdentifier());
    if (!isBlank(scopeInfo.getProjectIdentifier())) {
      message = message
          + String.format(",organization [%s] and project [%s] already exists", scopeInfo.getOrgIdentifier(),
              scopeInfo.getProjectIdentifier());
    } else if (!isBlank(scopeInfo.getOrgIdentifier())) {
      message = message + String.format("and organization [%s] already exists", scopeInfo.getOrgIdentifier());
    } else {
      message = message + "already exists";
    }
    return message;
  }

  private Optional<Invite> getExistingInvite(Invite invite, ScopeInfo scopeInfo) {
    return inviteRepository.findFirstByAccountIdentifierAndParentUniqueIdAndEmailAndDeletedFalse(
        invite.getAccountIdentifier(), scopeInfo.getUniqueId(), invite.getEmail());
  }

  private InviteOperationResponse newInvite(Invite invite, boolean[] scimLdapArray, ScopeInfo scopeInfo) {
    checkNgLimit(invite.getAccountIdentifier());
    if (isNotEmpty(invite.getName())) {
      invite.setName(UserUtils.sanitizeUserName(invite.getName()));
    }
    if (scopeInfo != null) {
      invite.setParentUniqueId(scopeInfo.getUniqueId());
    }
    Invite savedInvite = inviteRepository.save(invite);

    try (AutoLogContext ignore =
             new NgInviteLogContext(savedInvite.getAccountIdentifier(), savedInvite.getId(), OVERRIDE_ERROR)) {
      String accountId = invite.getAccountIdentifier();
      boolean isPLNoEmailForSamlAccountInvitesEnabled = isPLNoEmailForSamlAccountInvitesEnabled(accountId);
      boolean isAutoInviteAcceptanceEnabled = isAutoInviteAcceptanceEnabled(accountId);
      TwoFactorAuthSettingsInfo twoFactorAuthSettingsInfo =
          getTwoFactorAuthSettingsInfo(accountId, savedInvite.getEmail());

      try {
        sendInvitationMail(savedInvite, isPLNoEmailForSamlAccountInvitesEnabled, isAutoInviteAcceptanceEnabled,
            twoFactorAuthSettingsInfo, scopeInfo);
      } catch (URISyntaxException ex) {
        log.error("NG User Invite: Mail embed url incorrect, can't sent email due to an exception: ", ex);
      } catch (UnsupportedEncodingException ex) {
        log.error("NG User Invite: Invite Email sending failed due to encoding exception: ", ex);
      } catch (Exception ex) {
        log.error("NG User Invite: while inviting or notifying user to join harness an exception occurred: ", ex);
      }

      String email = invite.getEmail().trim();

      if (scimLdapArray[0]) {
        createAndInviteNonPasswordUser(accountId, invite.getInviteToken(), email, true, false, invite.getName(),
            invite.getGivenName(), invite.getFamilyName(), invite.getExternalId(), SCIM);
        updateUserTwoFactorAuthInfo(email, twoFactorAuthSettingsInfo);
      } else if (scimLdapArray[1] || isAutoInviteAcceptanceEnabled || isPLNoEmailForSamlAccountInvitesEnabled) {
        createAndInviteNonPasswordUser(accountId, invite.getInviteToken(), email, false, false, invite.getName(),
            invite.getGivenName(), invite.getFamilyName(), invite.getExternalId(), scimLdapArray[1] ? LDAP : MANUAL);
        updateUserTwoFactorAuthInfo(email, twoFactorAuthSettingsInfo);
      }

      if (isPLNoEmailForSamlAccountInvitesEnabled && !twoFactorAuthSettingsInfo.isTwoFactorAuthenticationEnabled()) {
        return InviteOperationResponse.USER_ADDED_SUCCESSFULLY_TO_ACCOUNT;
      } else {
        ngAuditUserInviteCreateEvent(savedInvite, scopeInfo);
      }
      return InviteOperationResponse.USER_INVITED_SUCCESSFULLY;
    }
  }

  private void updateUserTwoFactorAuthInfo(String email, TwoFactorAuthSettingsInfo twoFactorAuthSettingsInfo) {
    try {
      if (twoFactorAuthSettingsInfo.isTwoFactorAuthenticationEnabled()) {
        Optional<UserInfo> userInfo =
            CGRestUtils.getResponse(userClient.updateUserTwoFactorAuthInfo(email, twoFactorAuthSettingsInfo));
        userInfo.ifPresent(
            info -> log.info("NG User Invite: two factor auth settings for the user {} is updated", info.getEmail()));
      }
    } catch (Exception ex) {
      log.error(
          "NG User Invite: while making an accountClient call to updateUserTwoFactorAuthInfo failed with exception: ",
          ex);
      throw ex;
    }
  }

  private TwoFactorAuthSettingsInfo getTwoFactorAuthSettingsInfo(String accountIdentifier, String email) {
    AccountDTO accountDTO = getAccountDTO(accountIdentifier);
    if (accountDTO.isTwoFactorAdminEnforced()) {
      String totpSecretKey = TimeBasedOneTimePasswordUtil.generateBase32Secret();
      String otpUrl = generateOtpUrl(accountDTO.getCompanyName(), email, totpSecretKey);

      return TwoFactorAuthSettingsInfo.builder()
          .email(email)
          .twoFactorAuthenticationEnabled(true)
          .mechanism(TwoFactorAuthMechanismInfo.TOTP)
          .totpSecretKey(totpSecretKey)
          .totpqrurl(otpUrl)
          .build();
    } else {
      return TwoFactorAuthSettingsInfo.builder().twoFactorAuthenticationEnabled(false).build();
    }
  }

  private boolean isPLNoEmailForSamlAccountInvitesEnabled(String accountIdentifier) {
    try {
      return CGRestUtils.getResponse(
          accountClient.checkPLNoEmailForSamlAccountInvitesEnabledForAccount(accountIdentifier));
    } catch (Exception ex) {
      log.error("NG User Invite: while making an accountClient call to check FF PL_NO_EMAIL_FOR_SAML_ACCOUNT_INVITES "
              + "status failed with exception: ",
          ex);
      throw ex;
    }
  }

  private boolean isAutoInviteAcceptanceEnabled(String accountIdentifier) {
    try {
      return CGRestUtils.getResponse(accountClient.checkAutoInviteAcceptanceEnabledForAccount(accountIdentifier));
    } catch (Exception ex) {
      log.error("NG User Invite: while making an accountClient call to check AutoInviteAcceptanceEnabled failed with "
              + "exception: ",
          ex);
      throw ex;
    }
  }

  private void updateJWTTokenInInvite(Invite invite) {
    String jwtToken = jwtGeneratorUtils.generateJWTToken(ImmutableMap.of(InviteKeys.id, invite.getId()),
        TimeUnit.MILLISECONDS.convert(LINK_VALIDITY_IN_DAYS, TimeUnit.DAYS), jwtPasswordSecret);
    invite.setInviteToken(jwtToken);
    Update update = new Update().set(InviteKeys.inviteToken, invite.getInviteToken());
    inviteRepository.updateInvite(invite.getId(), update);
  }

  private void sendInvitationMail(Invite invite, boolean isPLNoEmailForSamlAccountInvitesEnabled,
      boolean isAutoInviteAcceptanceEnabled, TwoFactorAuthSettingsInfo twoFactorAuthSettingsInfo, ScopeInfo scopeInfo)
      throws URISyntaxException, UnsupportedEncodingException {
    updateJWTTokenInInvite(invite);
    if (isPLNoEmailForSamlAccountInvitesEnabled && !twoFactorAuthSettingsInfo.isTwoFactorAuthenticationEnabled()) {
      log.info("NG User Invite: is not required as FF PL_NO_EMAIL_FOR_SAML_ACCOUNT_INVITES and SSO is enabled");
      return;
    }
    String url = isNgAuthUIEnabled ? getAcceptInviteUrl(invite) : getInvitationMailEmbedUrl(invite);
    EmailChannelBuilder emailChannelBuilder = EmailChannel.builder()
                                                  .accountId(invite.getAccountIdentifier())
                                                  .recipients(Collections.singletonList(invite.getEmail()))
                                                  .team(Team.PL)
                                                  .userGroups(Collections.emptyList());

    if (isAutoInviteAcceptanceEnabled
        || (isPLNoEmailForSamlAccountInvitesEnabled && twoFactorAuthSettingsInfo.isTwoFactorAuthenticationEnabled())) {
      emailChannelBuilder.templateId(EMAIL_NOTIFY_TEMPLATE_ID);
    } else {
      emailChannelBuilder.templateId(EMAIL_INVITE_TEMPLATE_ID);
    }

    Map<String, String> templateData = new HashMap<>();
    templateData.put(SHOULD_MAIL_CONTAIN_TWO_FACTOR_INFO,
        Boolean.toString(twoFactorAuthSettingsInfo.isTwoFactorAuthenticationEnabled()));
    if (twoFactorAuthSettingsInfo.isTwoFactorAuthenticationEnabled()) {
      templateData.put(TOTP_SECRET, twoFactorAuthSettingsInfo.getTotpSecretKey());
      templateData.put(TOTP_URL, twoFactorAuthSettingsInfo.getTotpqrurl());
    }

    templateData.put("name", invite.getEmail());
    templateData.put("url", url);
    templateData.put("year", Year.now().toString());
    if (!isBlank(scopeInfo.getProjectIdentifier())) {
      templateData.put("projectname", accountOrgProjectHelper.getProjectName(invite.getParentUniqueId()));
    } else if (!isBlank(scopeInfo.getOrgIdentifier())) {
      templateData.put("organizationname", accountOrgProjectHelper.getOrgName(invite.getParentUniqueId()));
    } else {
      templateData.put("accountname", accountOrgProjectHelper.getAccountName(invite.getAccountIdentifier()));
    }
    try {
      Principal principal = SourcePrincipalContextBuilder.getSourcePrincipal();
      if (principal == null) {
        throw new InvalidRequestException("Principal doesn't exists");
      }
      switch (principal.getType()) {
        case USER:
          UserPrincipal userPrincipal = (UserPrincipal) principal;
          templateData.put("inviterName", userPrincipal.getUsername());
          templateData.put("inviterEmail", userPrincipal.getEmail());
          break;
        case SERVICE_ACCOUNT:
          ServiceAccountPrincipal serviceAccountPrincipal = (ServiceAccountPrincipal) principal;
          templateData.put("inviterName", serviceAccountPrincipal.getUsername());
          templateData.put("inviterEmail", serviceAccountPrincipal.getEmail());
          break;
        default:
          throw new InvalidRequestException("Unsupported Principal type: " + principal.getType());
      }
    } catch (Exception ex) {
      if (invite.getInviteType() == ADMIN_INITIATED_INVITE) {
        templateData.put("inviterName", "Admin");
        templateData.put("inviterEmail", "Admin");
      }

      else {
        log.error("Failed to invite user with email {} to account {} invite type {}", invite.getEmail(),
            invite.getAccountIdentifier(), invite.getInviteType());
        throw ex;
      }
    }
    emailChannelBuilder.templateData(templateData);

    NotificationResult notificationResult = notificationClient.sendNotificationAsync(emailChannelBuilder.build());
    log.info("NG User Invite: notification with notificationId {} and templateId {} is successfully sent",
        notificationResult.getNotificationId(), emailChannelBuilder.build().getTemplateId());
  }

  private AccountDTO getAccountDTO(String accountIdentifier) {
    try {
      return CGRestUtils.getResponse(accountClient.getAccountDTO(accountIdentifier));
    } catch (Exception ex) {
      log.error("NG User Invite:while making an accountClient call to get AccountDTO failed with exception: ",
          accountIdentifier, ex);
      throw ex;
    }
  }

  private String generateOtpUrl(String companyName, String userEmailAddress, String secret) {
    return format(TOTP_URL_PREFIX, "Harness_" + companyName.replace(" ", "-"), userEmailAddress, secret);
  }

  private void ngAuditUserInviteCreateEvent(Invite invite, ScopeInfo scopeInfo) {
    try {
      outboxService.save(new UserInviteCreateEvent(invite.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
          scopeInfo.getProjectIdentifier(), writeDTO(invite, scopeInfo)));
    } catch (Exception ex) {
      log.error("For account {} the Audit trails for User Invite Create Event with inviteId {} failed with exception: ",
          invite.getAccountIdentifier(), invite.getId(), ex);
    }
  }

  private void ngAuditUserInviteUpdateEvent(Invite invite, ScopeInfo scopeInfo) {
    try {
      outboxService.save(new UserInviteUpdateEvent(invite.getAccountIdentifier(), scopeInfo.getOrgIdentifier(),
          scopeInfo.getProjectIdentifier(), writeDTO(invite, scopeInfo), writeDTO(invite, scopeInfo)));
    } catch (Exception ex) {
      log.error("For account {} the Audit trails for User Invite Update Event with inviteId {} failed with exception: ",
          invite.getAccountIdentifier(), invite.getId(), ex);
    }
  }

  private String getInvitationMailEmbedUrl(Invite invite) throws URISyntaxException {
    AccountDTO account = CGRestUtils.getResponse(accountClient.getAccountDTO(invite.getAccountIdentifier()));
    String fragment = String.format(INVITE_URL, invite.getAccountIdentifier(), account.getName(),
        account.getCompanyName(), invite.getEmail(), invite.getInviteToken());

    String baseUrl = accountOrgProjectHelper.getBaseUrl(invite.getAccountIdentifier());
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setFragment(fragment);
    return uriBuilder.toString();
  }

  private String getAcceptInviteUrl(Invite invite) throws URISyntaxException, UnsupportedEncodingException {
    String baseUrl = accountOrgProjectHelper.getGatewayBaseUrl(invite.getAccountIdentifier()) + ACCEPT_INVITE_PATH;
    URIBuilder uriBuilder = new URIBuilder(baseUrl);
    uriBuilder.setParameters(getParameterList(invite));
    uriBuilder.setFragment(null);
    return uriBuilder.toString();
  }

  private List<NameValuePair> getParameterList(Invite invite) throws UnsupportedEncodingException {
    return Arrays.asList(new BasicNameValuePair("accountIdentifier", invite.getAccountIdentifier()),
        new BasicNameValuePair("email", URLEncoder.encode(invite.getEmail(), "UTF-8")),
        new BasicNameValuePair("token", invite.getInviteToken()));
  }

  @Override
  public boolean completeInvite(Optional<Invite> inviteOpt) {
    if (!inviteOpt.isPresent()) {
      return false;
    }
    Invite invite = inviteOpt.get();
    String email = invite.getEmail();
    Optional<UserMetadataDTO> userOpt = ngUserService.getUserByEmail(email, true);
    Preconditions.checkState(userOpt.isPresent(), "Illegal state: user doesn't exists");
    UserMetadataDTO user = userOpt.get();

    Map<String, Optional<ScopeInfo>> scopeInfoMap =
        scopeInfoService.getScopeInfo(invite.getAccountIdentifier(), Set.of(invite.getParentUniqueId()));
    ScopeInfo scopeInfo =
        scopeInfoMap.get(invite.getParentUniqueId())
            .orElseThrow(
                () -> new NotFoundException("ScopeInfo not found for parentUniqueId: " + invite.getParentUniqueId()));

    Scope scope = Scope.of(scopeInfo);
    ngUserService.addUserToScope(
        user.getUuid(), scope, invite.getRoleBindings(), invite.getUserGroups(), ACCEPTED_INVITE, scopeInfo);
    // Adding user to the account for sign in flow to work
    ngUserService.addUserToCG(user.getUuid(), scope);

    updateUserSettings(invite, scopeInfo.getAccountIdentifier(), user.getUuid());

    markInviteApprovedAndDeleted(invite);
    // telemetry for adding user to an account
    sendInviteAcceptTelemetryEvents(user, invite.getAccountIdentifier());
    ngUserService.waitForRbacSetup(scope, user.getUuid(), email);
    return true;
  }

  private void updateUserSettings(Invite invite, String accountIdentifier, String userUuid) {
    if (isNotEmpty(invite.getUserSettings())) {
      try {
        userSettingsService.update(accountIdentifier, userUuid, getUserSettingRequestDTO(invite.getUserSettings()));
      } catch (Exception e) {
        log.error("Error updating default URL for user", e);
      }
    } else {
      log.info("No default User settings provided in User Invite");
    }
  }

  @Override
  public boolean completeUserNgSetupWithoutInvite(String email, String accountId) {
    Optional<UserMetadataDTO> userOpt = ngUserService.getUserByEmail(email, true);
    Preconditions.checkState(userOpt.isPresent(), "Illegal state: user doesn't exists");
    UserMetadataDTO user = userOpt.get();
    Scope scope = Scope.builder().accountIdentifier(accountId).orgIdentifier(null).projectIdentifier(null).build();
    ngUserService.addUserToScope(user.getUuid(), scope, new ArrayList<>(), new ArrayList<>(),
        UserMembershipUpdateSource.USER,
        ScopeInfo.builder().accountIdentifier(accountId).scopeType(ScopeLevel.ACCOUNT).uniqueId(accountId).build());
    ngUserService.waitForRbacSetup(scope, user.getUuid(), email);
    return true;
  }

  @Override
  public void sendInviteAcceptTelemetryEvents(UserMetadataDTO user, String accountId) {
    String userEmail = user.getEmail();

    String accountName = "";
    // get the name of the account
    AccountDTO account = CGRestUtils.getResponse(accountClient.getAccountDTO(accountId));
    if (account != null) {
      accountName = account.getName();
    }

    HashMap<String, Object> properties = new HashMap<>();
    properties.put("email", userEmail);
    properties.put("name", user.getName());
    properties.put("id", user.getUuid());
    properties.put("startTime", String.valueOf(Instant.now().toEpochMilli()));
    properties.put("accountId", accountId);
    properties.put("accountName", accountName);
    properties.put("source", USER_INVITE);

    // identify event to register new user
    telemetryReporter.sendIdentifyEvent(userEmail, properties,
        ImmutableMap.<Destination, Boolean>builder()
            .put(Destination.MARKETO, true)
            .put(Destination.AMPLITUDE, true)
            .build());

    HashMap<String, Object> groupProperties = new HashMap<>();
    groupProperties.put("group_id", accountId);
    groupProperties.put("group_type", "Account");
    groupProperties.put("group_name", accountName);

    // group event to register new signed-up user with new account
    telemetryReporter.sendGroupEvent(
        accountId, userEmail, groupProperties, ImmutableMap.<Destination, Boolean>builder().build());

    // flush all events so that event queue is empty
    telemetryReporter.flush();

    properties.put("platform", "NG");
    // Wait 20 seconds, to ensure identify is sent before track
    scheduledExecutor.schedule(
        ()
            -> telemetryReporter.sendTrackEvent("Invite  Accepted", userEmail, accountId, properties,
                ImmutableMap.<Destination, Boolean>builder()
                    .put(Destination.MARKETO, true)
                    .put(Destination.AMPLITUDE, true)
                    .build(),
                null),
        20, TimeUnit.SECONDS);
    log.info("User Invite telemetry sent");
  }

  private void markInviteApproved(Invite invite) {
    invite.setApproved(TRUE);
    Update update = new Update().set(InviteKeys.approved, TRUE);
    inviteRepository.updateInvite(invite.getId(), update);
  }

  private void markInviteApprovedAndDeleted(Invite invite) {
    invite.setApproved(TRUE);
    invite.setDeleted(TRUE);
    Update update = new Update().set(InviteKeys.approved, TRUE).set(InviteKeys.deleted, TRUE);
    inviteRepository.updateInvite(invite.getId(), update);
  }

  private Optional<String> getInviteIdFromToken(String token) {
    Map<String, Claim> claims = jwtGeneratorUtils.verifyJWTToken(token, jwtPasswordSecret);
    return getInviteKeysFromClaims(claims);
  }

  private Optional<String> getInviteIdFromTokenV2(String token) {
    Map<String, Claim> claims = jwtGeneratorUtils.verifyJWTTokenV2(token, jwtPasswordSecret);
    return getInviteKeysFromClaims(claims);
  }

  private Optional<String> getInviteKeysFromClaims(Map<String, Claim> claims) {
    if (!claims.containsKey("exp")) {
      log.warn(this.getClass().getName() + " verifies JWT Token without Expiry Date");
      Principal principal = SecurityContextBuilder.getPrincipalFromClaims(claims);
      if (principal != null) {
        log.info(String.format(
            "Principal type is %s and its name is %s", principal.getType().toString(), principal.getName()));
      }
    }
    if (!claims.containsKey(InviteKeys.id)) {
      return Optional.empty();
    }
    return Optional.of(claims.get(InviteKeys.id).asString());
  }
  private void validateRequest(String searchTerm, ACLAggregateFilter aclAggregateFilter) {
    if (!isBlank(searchTerm) && ACLAggregateFilter.isFilterApplied(aclAggregateFilter)) {
      log.error("Search term and filter on role/resourcegroup identifiers can't be applied at the same time");
      throw new InvalidRequestException(
          "Search term and filter on role/resourcegroup identifiers can't be applied at the same time");
    }
  }

  private PageResponse<Invite> getInvitePage(
      ScopeInfo scopeInfo, String searchTerm, PageRequest pageRequest, ACLAggregateFilter aclAggregateFilter) {
    Criteria criteria = Criteria.where(InviteKeys.accountIdentifier)
                            .is(scopeInfo.getAccountIdentifier())
                            .and(InviteKeys.parentUniqueId)
                            .is(scopeInfo.getUniqueId())
                            .and(InviteKeys.deleted)
                            .is(FALSE);
    if (ACLAggregateFilter.isFilterApplied(aclAggregateFilter)) {
      if (isNotEmpty(aclAggregateFilter.getRoleIdentifiers())) {
        criteria.and(InviteKeys.roleBindings + "." + RoleBindingKeys.roleIdentifier)
            .in(aclAggregateFilter.getRoleIdentifiers());
      }
      if (isNotEmpty(aclAggregateFilter.getResourceGroupIdentifiers())) {
        criteria.and(InviteKeys.roleBindings + "." + RoleBindingKeys.resourceGroupIdentifier)
            .in(aclAggregateFilter.getResourceGroupIdentifiers());
      }
    }
    if (!isBlank(searchTerm)) {
      Page<UserInfo> userInfos = ngUserService.listCurrentGenUsers(scopeInfo.getAccountIdentifier(), searchTerm,
          org.springframework.data.domain.PageRequest.of(0, DEFAULT_PAGE_SIZE));
      List<String> emailIds = userInfos.stream().map(UserInfo::getEmail).collect(toList());
      Criteria searchTermCriteria = new Criteria();
      searchTermCriteria.orOperator(
          Criteria.where(InviteKeys.email).regex(searchTerm), Criteria.where(InviteKeys.email).in(emailIds));
      criteria = new Criteria().andOperator(criteria, searchTermCriteria);
    }
    return getInvites(criteria, pageRequest);
  }

  private Map<String, UserMetadataDTO> getPendingUserMap(List<String> userEmails, String accountIdentifier) {
    List<UserInfo> users =
        ngUserService.listCurrentGenUsers(accountIdentifier, UserFilterNG.builder().emailIds(userEmails).build());
    Map<String, UserMetadataDTO> userMetadataMap = new HashMap<>();
    users.forEach(user
        -> userMetadataMap.put(user.getEmail(),
            UserMetadataDTO.builder()
                .email(user.getEmail())
                .name(user.getName())
                .uuid(user.getUuid())
                .locked(user.isLocked())
                .disabled(user.isDisabled())
                .externallyManaged(user.isExternallyManaged())
                .twoFactorAuthenticationEnabled(user.isTwoFactorAuthenticationEnabled())
                .build()));
    for (String email : userEmails) {
      userMetadataMap.computeIfAbsent(email, email1 -> UserMetadataDTO.builder().email(email1).build());
    }
    return userMetadataMap;
  }

  private List<InviteDTO> aggregatePendingUsers(List<Invite> invites, Map<String, UserMetadataDTO> userMap) {
    Preconditions.checkState(invites.stream().map(Invite::getEmail).distinct().count() == userMap.size(),
        "Number of invites should be same as number of invites. Invariant violated");
    List<InviteDTO> inviteDTOs = new ArrayList<>();
    for (Invite invite : invites) {
      UserMetadataDTO user = userMap.get(invite.getEmail());
      inviteDTOs.add(InviteDTO.builder()
                         .id(invite.getId())
                         .name(user.getName())
                         .email(user.getEmail())
                         .roleBindings(invite.getRoleBindings())
                         .inviteType(invite.getInviteType())
                         .build());
    }
    return inviteDTOs;
  }

  private void checkPermissions(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String permissionIdentifier) {
    accessControlClient.checkForAccessOrThrow(ResourceScope.of(accountIdentifier, orgIdentifier, projectIdentifier),
        Resource.of("USER", null), permissionIdentifier);
  }

  private <T, S> S wrapperForTransactions(Function<T, S> function, T arg) {
    if (!useMongoTransactions) {
      return function.apply(arg);
    } else {
      return Failsafe.with(transactionRetryPolicy)
          .get(() -> transactionTemplate.execute(status -> function.apply(arg)));
    }
  }

  private <T, U, R> R wrapperForTransactions(BiFunction<T, U, R> function, T arg1, U arg2) {
    if (!useMongoTransactions) {
      return function.apply(arg1, arg2);
    } else {
      return Failsafe.with(transactionRetryPolicy)
          .get(() -> transactionTemplate.execute(status -> function.apply(arg1, arg2)));
    }
  }

  @FunctionalInterface
  public interface TriFunction<T, U, V, R> {
    R apply(T t, U u, V v);
  }

  private <T, U, V, R> R wrapperForTransactions(TriFunction<T, U, V, R> function, T arg1, U arg2, V arg3) {
    if (!useMongoTransactions) {
      return function.apply(arg1, arg2, arg3);
    } else {
      return Failsafe.with(transactionRetryPolicy)
          .get(() -> transactionTemplate.execute(status -> function.apply(arg1, arg2, arg3)));
    }
  }
}
