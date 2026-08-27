/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.scheduler;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authorization.AuthorizationServiceHeader;
import io.harness.events.billing.v1.BillingEvent;
import io.harness.ng.core.services.OrganizationService;
import io.harness.publishing.scheduler.AbstractBillingMetricJob;
import io.harness.shared.billing.v1.BillingMetric;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class OrganizationBillingMetricJob extends AbstractBillingMetricJob {
  private static final String JOB_NAME = "OrganizationBillingMetricJob";

  // MongoDB constants
  private static final String ACCOUNT_IDENTIFIER_FIELD = "accountId";
  private static final String ORGANIZATION_COUNT_FIELD = "count";

  private final OrganizationService organizationService;

  @Inject
  public OrganizationBillingMetricJob(OrganizationService organizationService) {
    this.organizationService = organizationService;
    log.info("{} initialized", JOB_NAME);
  }

  @Override
  protected String getJobName() {
    return JOB_NAME;
  }

  @Override
  protected BillingMetric getMetricName() {
    return BillingMetric.ORGANIZATION;
  }

  @Override
  protected List<String> getScheduleCrons() {
    return List.of(CRON_DAILY_11PM_UTC, CRON_DAILY_7AM_UTC, CRON_DAILY_3PM_UTC);
  }

  @Override
  protected AuthorizationServiceHeader getServiceSource() {
    return AuthorizationServiceHeader.NG_MANAGER;
  }

  @Override
  protected List<BillingEvent> collectBillingEvents(String executionDate) throws Exception {
    log.info("{} - Starting organization billing data collection for date: {}", JOB_NAME, executionDate);

    // Fetch organization counts from MongoDB aggregation
    List<Document> orgCountByAccountIdList = organizationService.getOrgCountsByAccount();

    if (isEmpty(orgCountByAccountIdList)) {
      log.info("{} - No accounts with organizations found for billing metrics", JOB_NAME);
      return new ArrayList<>();
    }

    int totalAccounts = orgCountByAccountIdList.size();
    log.info("{} - Processing {} accounts for organization billing metrics", JOB_NAME, totalAccounts);

    // Collect all billing events
    List<BillingEvent> billingEvents = new ArrayList<>();
    int successCount = 0;
    int errorCount = 0;

    // Process each account and create billing event
    for (Document accountDoc : orgCountByAccountIdList) {
      String accountId = accountDoc.getString(ACCOUNT_IDENTIFIER_FIELD);

      try {
        // MongoDB aggregation returns Integer for small counts and Long for large counts
        Number countNumber = (Number) accountDoc.get(ORGANIZATION_COUNT_FIELD);
        long count = countNumber != null ? countNumber.longValue() : 0L;

        BillingEvent event = createBillingEvent(accountId, count, executionDate);
        billingEvents.add(event);
        successCount++;

        log.debug("{} - Created billing event for account {}: {} organizations", JOB_NAME, accountId, count);

      } catch (Exception e) {
        errorCount++;
        log.error("{} - Failed to create billing event for account {}: {}", JOB_NAME, accountId, e.getMessage(), e);
        // Continue processing other accounts - don't let one failure break the entire job
      }
    }

    log.info("{} - Completed data collection. Total accounts: {}, Success: {}, Errors: {}", JOB_NAME, totalAccounts,
        successCount, errorCount);

    return billingEvents;
  }

  // Create billing event for a single account's organization count.
  private BillingEvent createBillingEvent(String accountId, Long orgCount, String executionDate) {
    if (orgCount == null) {
      orgCount = 0L;
    }

    Instant now = Instant.now();
    Timestamp timestamp = Timestamp.newBuilder().setSeconds(now.getEpochSecond()).setNanos(now.getNano()).build();

    // Idempotency key ensures we don't double-publish for same account/date
    String idempotencyKey = String.format("%s-%s-%s", accountId, executionDate, getMetricName().name());

    BillingEvent event = BillingEvent.newBuilder()
                             .setIdempotencyKey(idempotencyKey)
                             .setAccountId(accountId)
                             .setResourceParentUniqueIdentifier(accountId)
                             .setMetric(getMetricName())
                             .setValue(orgCount.doubleValue())
                             .setEventTimestamp(timestamp)
                             .setSendTimestamp(timestamp)
                             .build();

    log.debug("{} - Created billing event: accountId={}, orgCount={}, idempotencyKey={}", JOB_NAME, accountId, orgCount,
        idempotencyKey);

    return event;
  }
}
