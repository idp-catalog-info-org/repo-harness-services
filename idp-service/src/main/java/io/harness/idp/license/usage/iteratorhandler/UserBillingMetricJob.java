/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.iteratorhandler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.accesscontrol.AccessControlAdminClient;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentFilterDTO;
import io.harness.accesscontrol.roleassignments.api.RoleAssignmentResponseDTO;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.events.billing.v1.BillingEvent;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.metrics.recorder.OperationTimeMetricRecorder;
import io.harness.metrics.service.api.MetricService;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.user.UserInfo;
import io.harness.publishing.scheduler.AbstractBillingMetricJob;
import io.harness.remote.client.CGRestUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.shared.billing.v1.BillingMetric;
import io.harness.user.remote.UserClient;
import io.harness.user.remote.UserFilterNG;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.IDP)
public class UserBillingMetricJob extends AbstractBillingMetricJob {
  private static final String JOB_NAME = "UserBillingMetricJob";
  private static final String DURATION_METRIC = "idp_user_billing_duration";
  private static final String PROCESSED_TOTAL_METRIC = "idp_user_billing_processed_total";
  private static final String ERROR_METRIC = "idp_user_billing_error_total";
  private final AccessControlAdminClient accessControlAdminClient;
  private final NamespaceService namespaceService;
  private final UserClient userClient;
  @Inject(optional = true) private MetricService metricService;

  @Inject
  public UserBillingMetricJob(
      AccessControlAdminClient accessControlAdminClient, UserClient userClient, NamespaceService namespaceService) {
    this.accessControlAdminClient = accessControlAdminClient;
    this.namespaceService = namespaceService;
    this.userClient = userClient;
    log.info("{} initialized", JOB_NAME);
  }

  @Override
  protected String getJobName() {
    return JOB_NAME;
  }

  @Override
  protected List<String> getScheduleCrons() {
    return List.of(CRON_DAILY_11PM_UTC);
  }

  @Override
  protected List<BillingEvent> collectBillingEvents(String executionDate) {
    log.info("{} - Starting user billing data collection for date: {}", JOB_NAME, executionDate);

    Map<String, String> labels = new HashMap<>();
    labels.put("job", JOB_NAME);
    OperationTimeMetricRecorder timer =
        metricService != null ? new OperationTimeMetricRecorder(DURATION_METRIC, labels, metricService) : null;
    try {
      return doCollectBillingEvents(executionDate);
    } catch (RuntimeException ex) {
      if (metricService != null) {
        metricService.incCounter(ERROR_METRIC);
      }
      throw ex;
    } finally {
      if (timer != null) {
        timer.close();
      }
    }
  }

  private List<BillingEvent> doCollectBillingEvents(String executionDate) {
    List<String> accountIdentifiers = namespaceService.getAccountIds();
    int successCount = 0;
    int errorCount = 0;

    SecurityContextBuilder.setContext(new ServicePrincipal(AuthorizationServiceHeader.IDP_SERVICE.getServiceId()));
    RoleAssignmentFilterDTO filterDTO =
        RoleAssignmentFilterDTO.builder().roleFilter(Collections.singleton("_idp_user")).build();
    List<BillingEvent> billingEvents = new ArrayList<>();

    for (String accountIdentifier : accountIdentifiers) {
      PageResponse<RoleAssignmentResponseDTO> response;
      List<String> userIds = new ArrayList<>();
      int page = 0;
      do {
        response = NGRestUtils.getResponse(accessControlAdminClient.getFilteredRoleAssignmentsWithInternalRoles(
            accountIdentifier, null, null, page, 100, false, filterDTO));
        if (response != null && !isEmpty(response.getContent())) {
          userIds.addAll(response.getContent()
                             .stream()
                             .map(roleAssignmentResponseDTO
                                 -> roleAssignmentResponseDTO.getRoleAssignment().getPrincipal().getIdentifier())
                             .toList());
        }
        page++;
      } while (response != null && !isEmpty(response.getContent()) && response.getTotalPages() > page);

      if (isEmpty(userIds)) {
        continue;
      }

      List<UserInfo> users = CGRestUtils.getResponse(
          userClient.listUsers(accountIdentifier, UserFilterNG.builder().userIds(userIds).build()));
      for (UserInfo user : users) {
        try {
          BillingEvent event = createUserBillingEvent(user, accountIdentifier, executionDate);
          billingEvents.add(event);
          successCount++;
        } catch (Exception e) {
          errorCount++;
          log.error("{} - Failed to create billing event for account {}: email: {}", JOB_NAME, accountIdentifier,
              e.getMessage(), e);
        }
      }
    }
    log.info("{} - Completed data collection. Total Success: {}, Errors: {}", JOB_NAME, successCount, errorCount);
    if (metricService != null) {
      if (successCount > 0) {
        metricService.recordMetric(PROCESSED_TOTAL_METRIC, successCount);
      }
      if (errorCount > 0) {
        metricService.recordMetric(ERROR_METRIC, errorCount);
      }
    }
    return billingEvents;
  }

  @Override
  protected BillingMetric getMetricName() {
    return BillingMetric.IDP_USER;
  }

  @Override
  protected AuthorizationServiceHeader getServiceSource() {
    return AuthorizationServiceHeader.IDP_SERVICE;
  }

  private BillingEvent createUserBillingEvent(UserInfo userInfo, String accountId, String executionDate) {
    Instant now = Instant.now();
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build();
    String userId = userInfo.getUuid();
    String idempotencyKey = String.format("%s-%s-%s-%s", accountId, userId, executionDate, getMetricName().name());
    Map<String, String> metadata = new HashMap<>();
    metadata.put("identifier", userId);
    metadata.put("email", userInfo.getEmail());
    metadata.put("name", userInfo.getName());
    BillingEvent event = BillingEvent.newBuilder()
                             .setIdempotencyKey(idempotencyKey)
                             .setAccountId(accountId)
                             .setResourceParentUniqueIdentifier(accountId)
                             .setMetric(getMetricName())
                             .setValue(1)
                             .setEventTimestamp(timestamp)
                             .setSendTimestamp(timestamp)
                             .putAllTags(metadata)
                             .build();
    log.debug("{} - Created billing event: accountId={}, email={}, idempotencyKey={}", JOB_NAME, accountId,
        userInfo.getEmail(), idempotencyKey);
    return event;
  }
}
