/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.sto;

import static io.harness.NGConstants.ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.ng.core.AccountOrgProjectHelper;
import io.harness.ng.core.entities.Organization;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.services.OrganizationService;
import io.harness.ng.core.services.ProjectService;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.remote.dto.UserMetadataDTO;
import io.harness.ng.core.user.service.NgUserService;
import io.harness.notification.NotificationTriggerRequest;
import io.harness.notification.Team;
import io.harness.notification.channeldetails.EmailChannel;
import io.harness.notification.entities.NotificationEntity;
import io.harness.notification.notificationclient.NotificationClient;
import io.harness.notification.templates.PredefinedTemplate;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.telemetry.Category;
import io.harness.telemetry.Destination;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

@OwnedBy(STO)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class StoServiceImpl implements StoService {
  private static final String ACCOUNT_ADMIN_ROLE = "_account_admin";
  // listUsersHavingRole has no caller-side pagination; cap outbound recipients for large accounts.
  private static final int MAX_QWIET_TRIAL_EXPIRY_ADMIN_RECIPIENTS = 50;

  // Telemetry for a trial expiry is emitted only on the first cron attempt — the
  // one that runs within 12h of the trial end. The 12h cron + ~24h email window
  // means a second attempt arrives 12-24h after expiry; gating on this window
  // keeps the event single-fire without any STO- or DB-side dedup state.
  private static final String QWIET_TRIAL_EXPIRED_EVENT = "harness_sast_sca_trial_expired";
  private static final long MILLIS_PER_DAY = 24 * 60 * 60 * 1000L;
  private static final long QWIET_TRIAL_EXPIRY_TELEMETRY_WINDOW_MS = 12 * 60 * 60 * 1000L;
  // Matches the human-readable date format used by the Qwiet trial emails (UTC).
  private static final DateTimeFormatter QWIET_TRIAL_DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.US).withZone(ZoneOffset.UTC);

  private final NotificationClient notificationClient;
  private final OrganizationService organizationService;
  private final ProjectService projectService;
  private final NgUserService ngUserService;
  private final PipelineServiceClient pipelineClient;
  private final AccountOrgProjectHelper accountOrgProjectHelper;
  private final TelemetryReporter telemetryReporter;

  @Override
  public void exemptionNotificationTrigger(StoExemptionNotificationRequest request) {
    String notificationTriggerRequestId = generateUuid();
    String accountIdentifier = request.getAccountId();
    String orgIdentifier = request.getOrgId();
    String projectIdentifier = request.getProjectId();
    String eventIdentifier = request.getEventIdentifier();
    String entityIdentifier = request.getEntityIdentifier();

    switch (eventIdentifier) {
      case "STO_EXEMPTION_REQUESTED":
      case "STO_EXEMPTION_STATUS_CHANGED":
        break;
      default:
        log.error("Unknown event identifier {} received for STO exemption notification, correlationId: {}",
            eventIdentifier, request.getCorrelationId());
        return;
    }

    Map<String, String> templateData = buildTemplateData(request);

    NotificationTriggerRequest.Builder notificationTriggerRequestBuilder =
        NotificationTriggerRequest.newBuilder()
            .setId(notificationTriggerRequestId)
            .setAccountId(accountIdentifier)
            .setOrgId(Objects.requireNonNullElse(orgIdentifier, ""))
            .setProjectId(Objects.requireNonNullElse(projectIdentifier, ""))
            .setEvent(eventIdentifier)
            .setEventEntity(NotificationEntity.STO_EXEMPTION.name())
            .setEntityIdentifier(entityIdentifier)
            .putAllTemplateData(templateData);

    log.info("Sending {} notification for {}, correlationId: {}", eventIdentifier, entityIdentifier,
        request.getCorrelationId());
    notificationClient.sendNotificationTrigger(notificationTriggerRequestBuilder.build());
  }

  @NotNull
  private Map<String, String> buildTemplateData(StoExemptionNotificationRequest request) {
    log.debug("Request data for exemption notification: {}", request);

    Map<String, String> templateData = new HashMap<>();
    templateData.put("TEMPLATE_IDENTIFIER", request.getEntityIdentifier());
    templateData.put("ISSUE_TITLE", request.getIssueTitle());
    templateData.put("SEVERITY_CODE", request.getSeverityCode());
    templateData.put("SCOPE", request.getScope());
    templateData.put("REASON", request.getReason());

    String expiration = Objects.requireNonNullElse(
        request.getExpiration(), Objects.requireNonNullElse(request.getDuration(), "(None)"));
    templateData.put("EXPIRATION", expiration);

    String status = Objects.requireNonNullElse(request.getStatus(), "(None)");
    templateData.put("STATUS", status);

    String scanTool = Objects.requireNonNullElse(request.getScanTool(), "Scan Tool");
    templateData.put("SCAN_TOOL", scanTool);

    templateData.put("APPROVER_LABEL", status.equals("Rejected") ? "Rejecter" : "Approver");

    String accountIdentifier = request.getAccountId();
    String orgIdentifier = request.getOrgId();
    String projectIdentifier = request.getProjectId();

    // Translate org ID to human-friendly name
    String orgName = Objects.requireNonNullElse(orgIdentifier, "(None)");
    if (orgIdentifier != null && orgIdentifier.isEmpty()) {
      Optional<Organization> org = organizationService.get(accountIdentifier, orgIdentifier);
      if (org.isPresent()) {
        orgName = org.get().getName();
      }
    }
    templateData.put("ORG", orgName);

    // Translate project ID to human-friendly name
    String projectName = Objects.requireNonNullElse(projectIdentifier, "(None)");
    if (projectIdentifier != null && projectIdentifier.isEmpty()) {
      Optional<Project> project = projectService.get(accountIdentifier, orgIdentifier, projectIdentifier);
      if (project.isPresent()) {
        projectName = project.get().getName();
      }
    }
    templateData.put("PROJECT", projectName);

    Optional<UserInfo> user = ngUserService.getUserById(request.getRequesterId());
    String requester = user.isPresent() ? user.get().getEmail() : request.getRequesterId();
    templateData.put("REQUESTER", requester);

    String approverId = request.getApproverId();
    if (approverId != null && !approverId.isEmpty()) {
      user = ngUserService.getUserById(approverId);
      String approver = user.isPresent() ? user.get().getEmail() : request.getRequesterId();
      templateData.put("APPROVER", approver);
    } else {
      templateData.put("APPROVER", "(None)");
    }

    String baseUrl = "https://app.harness.io";
    try {
      String executionUrl = NGRestUtils.getResponse(
          pipelineClient.getExecutionURL(accountIdentifier, orgIdentifier, projectIdentifier, "", "", List.of()));
      URL url = new URL(executionUrl);
      baseUrl = url.getProtocol() + "://" + url.getHost();
      if (url.getPort() != url.getDefaultPort() && url.getPort() > 0) {
        baseUrl += ":" + url.getPort();
      }
    } catch (MalformedURLException ignored) {
    }
    String url = baseUrl + request.getExemptionUrl();
    templateData.put("EXEMPTION_URL", url);

    log.debug("Template data for exemption notification: {}", templateData);

    return templateData;
  }

  @Override
  public void qwietTrialNotificationTrigger(StoQwietTrialNotificationRequest request) {
    String accountId = request.getAccountId();
    String correlationId = Objects.requireNonNullElse(request.getCorrelationId(), generateUuid());

    Map<String, String> templateData = buildQwietTrialTemplateData(request);

    String userEmail = request.getUserEmail();
    if (userEmail != null && !userEmail.isEmpty()) {
      sendQwietTrialEmail(accountId, PredefinedTemplate.STO_QWIET_TRIAL_ACTIVATION_CUSTOMER_EMAIL.getIdentifier(),
          List.of(userEmail), templateData, correlationId, "customer", null);
    }

    List<String> internalRecipients = emptyIfNull(request.getInternalRecipients());
    if (!internalRecipients.isEmpty()) {
      sendQwietTrialEmail(accountId, PredefinedTemplate.STO_QWIET_TRIAL_INTERNAL_EMAIL.getIdentifier(),
          internalRecipients, templateData, correlationId, "internal", null);
    }
  }

  @Override
  public void qwietTrialExpiryNotificationTrigger(StoQwietTrialExpiryNotificationRequest request) {
    String accountId = request.getAccountId();
    String correlationId = Objects.requireNonNullElse(request.getCorrelationId(), generateUuid());
    String idempotencyKey = Objects.requireNonNullElse(request.getIdempotencyKey(), correlationId);

    // Validate inputs before sending: missing/invalid timestamps would render an
    // empty or epoch (1970) date and a nonsensical duration in the email.
    Long startTimestamp = request.getStartTimestamp();
    Long expiryTimestamp = request.getExpiryTimestamp();
    if (startTimestamp == null || startTimestamp <= 0 || expiryTimestamp == null || expiryTimestamp <= 0
        || expiryTimestamp < startTimestamp) {
      log.error("Skipping Qwiet trial expiry notification: invalid start/expiry timestamps "
              + "(start={}, expiry={}), accountId: {}, correlationId: {}",
          startTimestamp, expiryTimestamp, accountId, correlationId);
      return;
    }

    Map<String, String> templateData = buildQwietTrialExpiryTemplateData(request);

    List<String> adminRecipients = resolveAccountAdminEmails(accountId);
    if (!adminRecipients.isEmpty()) {
      // A stable notification id makes the customer email idempotent: repeated
      // cron attempts for the same expired trial are deduped by notification-service.
      sendQwietTrialEmail(accountId, PredefinedTemplate.STO_QWIET_TRIAL_EXPIRY_CUSTOMER_EMAIL.getIdentifier(),
          adminRecipients, templateData, correlationId, "expiry-customer", idempotencyKey + ":customer");
    } else {
      log.warn("No account admins resolved for Qwiet trial expiry email, accountId: {}, correlationId: {}", accountId,
          correlationId);
    }

    List<String> internalRecipients = emptyIfNull(request.getInternalRecipients());
    if (!internalRecipients.isEmpty()) {
      sendQwietTrialEmail(accountId, PredefinedTemplate.STO_QWIET_TRIAL_EXPIRY_INTERNAL_EMAIL.getIdentifier(),
          internalRecipients, templateData, correlationId, "expiry-internal", idempotencyKey + ":internal");
    }

    emitQwietTrialExpiredTelemetry(request, correlationId);
  }

  // Emits the trial-expired telemetry event once per expiry. The cron triggers
  // this endpoint ~twice (12h apart) within the email dedup window, so we only
  // emit on the attempt that lands within 12h of the trial end date.
  private void emitQwietTrialExpiredTelemetry(StoQwietTrialExpiryNotificationRequest request, String correlationId) {
    Long expiryTimestamp = request.getExpiryTimestamp();
    if (expiryTimestamp == null || expiryTimestamp <= 0) {
      log.warn("Skipping Qwiet trial expiry telemetry: missing expiryTimestamp, accountId: {}, correlationId: {}",
          request.getAccountId(), correlationId);
      return;
    }

    long elapsedSinceExpiry = System.currentTimeMillis() - expiryTimestamp;
    if (elapsedSinceExpiry < 0 || elapsedSinceExpiry > QWIET_TRIAL_EXPIRY_TELEMETRY_WINDOW_MS) {
      // Not the first attempt (or clock skew): a later cron run already emitted it.
      return;
    }

    try {
      String accountId = request.getAccountId();
      HashMap<String, Object> properties = new HashMap<>();
      properties.put("accountId", accountId);
      properties.put("startTimestamp", request.getStartTimestamp());
      properties.put("expiryTimestamp", expiryTimestamp);
      properties.put("trialDurationDays", trialDurationDays(request.getStartTimestamp(), expiryTimestamp));
      properties.put("sastEnabled", Boolean.TRUE.equals(request.getIsSASTEnabled()));
      properties.put("scaEnabled", Boolean.TRUE.equals(request.getIsSCAEnabled()));

      // Send to all telemetry destinations (Amplitude, Salesforce, Marketo, ...).
      telemetryReporter.sendTrackEvent(QWIET_TRIAL_EXPIRED_EVENT, null, accountId, properties,
          Collections.singletonMap(Destination.ALL, true), Category.GLOBAL,
          TelemetryOption.builder().sendForCommunity(false).build());
      log.info("Emitted Qwiet trial expired telemetry, accountId: {}, correlationId: {}", accountId, correlationId);
    } catch (Exception e) {
      log.error("Failed to emit Qwiet trial expired telemetry, accountId: {}, correlationId: {}",
          request.getAccountId(), correlationId, e);
    }
  }

  private List<String> resolveAccountAdminEmails(String accountId) {
    try {
      ScopeInfo scopeInfo =
          ScopeInfo.builder().accountIdentifier(accountId).scopeType(ScopeLevel.ACCOUNT).uniqueId(accountId).build();
      List<UserMetadataDTO> admins = ngUserService.listUsersHavingRole(scopeInfo, ACCOUNT_ADMIN_ROLE,
          List.of(ALL_RESOURCES_INCLUDING_CHILD_SCOPES_RESOURCE_GROUP_IDENTIFIER,
              DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER));

      // De-duplicate while preserving order; admins may match multiple resource groups.
      Set<String> emails = new LinkedHashSet<>();
      for (UserMetadataDTO admin : emptyIfNull(admins)) {
        if (admin != null && admin.getEmail() != null && !admin.getEmail().isEmpty()) {
          emails.add(admin.getEmail());
        }
      }
      if (emails.size() > MAX_QWIET_TRIAL_EXPIRY_ADMIN_RECIPIENTS) {
        log.warn("Capping Qwiet trial expiry admin recipients from {} to {}, accountId: {}", emails.size(),
            MAX_QWIET_TRIAL_EXPIRY_ADMIN_RECIPIENTS, accountId);
        return new ArrayList<>(emails).subList(0, MAX_QWIET_TRIAL_EXPIRY_ADMIN_RECIPIENTS);
      }
      return new ArrayList<>(emails);
    } catch (Exception e) {
      log.error("Failed to resolve account admins for Qwiet trial expiry email, accountId: {}"
              + " — customer expiry email will NOT be sent",
          accountId, e);
      return Collections.emptyList();
    }
  }

  @NotNull
  private Map<String, String> buildQwietTrialExpiryTemplateData(StoQwietTrialExpiryNotificationRequest request) {
    String accountId = request.getAccountId();
    String accountName = "(unknown)";
    try {
      accountName = accountOrgProjectHelper.getAccountName(accountId);
    } catch (Exception e) {
      log.warn("Failed to resolve account name for Qwiet trial expiry notification, using placeholder", e);
    }

    long durationDays = trialDurationDays(request.getStartTimestamp(), request.getExpiryTimestamp());

    Map<String, String> templateData = new HashMap<>();
    // The customer expiry template greets the recipient; admins are addressed generically.
    templateData.put("USER_NAME", "there");
    templateData.put("ACCOUNT_NAME", accountName);
    templateData.put("ACCOUNT_ID", Objects.requireNonNullElse(accountId, ""));
    templateData.put("START_DATE", formatTrialDate(request.getStartTimestamp()));
    templateData.put("EXPIRY_DATE", formatTrialDate(request.getExpiryTimestamp()));
    // Actual trial length derived from the dates — not a hardcoded 45 days.
    templateData.put("TRIAL_DURATION_DAYS", String.valueOf(durationDays));
    // Capability flags gate which scanner badges render in the email.
    templateData.put("SHOW_SAST", String.valueOf(Boolean.TRUE.equals(request.getIsSASTEnabled())));
    templateData.put("SHOW_SCA", String.valueOf(Boolean.TRUE.equals(request.getIsSCAEnabled())));
    return templateData;
  }

  // Formats an epoch-millis trial date as "dd MMMM yyyy" in UTC; empty if absent.
  private static String formatTrialDate(Long epochMillis) {
    if (epochMillis == null || epochMillis <= 0) {
      return "";
    }
    return QWIET_TRIAL_DATE_FORMAT.format(Instant.ofEpochMilli(epochMillis));
  }

  // Trial length in whole days, rounded from the start/expiry difference.
  private static long trialDurationDays(Long startTimestamp, Long expiryTimestamp) {
    if (startTimestamp == null || expiryTimestamp == null || expiryTimestamp <= startTimestamp) {
      return 0;
    }
    return Math.round((expiryTimestamp - startTimestamp) / (double) MILLIS_PER_DAY);
  }

  private void sendQwietTrialEmail(String accountId, String templateId, List<String> recipients,
      Map<String, String> templateData, String correlationId, String emailType, String notificationId) {
    try {
      EmailChannel emailChannel = EmailChannel.builder()
                                      .accountId(accountId)
                                      .recipients(recipients)
                                      .templateId(templateId)
                                      .templateData(templateData)
                                      .team(Team.OTHER)
                                      .ccEmailIds(Collections.emptyList())
                                      .notificationId(notificationId)
                                      .build();

      log.info("Sending Qwiet trial {} email, correlationId: {}, recipients: {}", emailType, correlationId,
          recipients.size());
      notificationClient.sendNotificationAsync(emailChannel);
    } catch (Exception e) {
      log.error("Failed to send Qwiet trial {} email, correlationId: {}", emailType, correlationId, e);
    }
  }

  @NotNull
  private Map<String, String> buildQwietTrialTemplateData(StoQwietTrialNotificationRequest request) {
    String accountId = request.getAccountId();
    String accountName = accountId != null ? accountId : "";
    try {
      accountName = accountOrgProjectHelper.getAccountName(accountId);
    } catch (Exception e) {
      log.warn("Failed to resolve account name for Qwiet trial notification, using account ID", e);
    }

    Map<String, String> templateData = new HashMap<>();
    templateData.put("USER_NAME", Objects.requireNonNullElse(request.getUserName(), "there"));
    templateData.put("USER_EMAIL", Objects.requireNonNullElse(request.getUserEmail(), ""));
    templateData.put("ACCOUNT_NAME", accountName);
    templateData.put("ACCOUNT_ID", Objects.requireNonNullElse(accountId, ""));
    templateData.put("START_DATE", Objects.requireNonNullElse(request.getStartDate(), ""));
    templateData.put("EXPIRY_DATE", Objects.requireNonNullElse(request.getExpiryDate(), ""));

    String baseUrl = "https://app.harness.io";
    try {
      baseUrl = accountOrgProjectHelper.getBaseUrl(accountId);
    } catch (Exception e) {
      log.warn("Failed to resolve base URL for Qwiet trial notification, using default", e);
    }
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    templateData.put(
        "ACCOUNT_URL", baseUrl + "/ng/account/" + Objects.requireNonNullElse(accountId, "") + "/module/sto");

    return templateData;
  }
}
