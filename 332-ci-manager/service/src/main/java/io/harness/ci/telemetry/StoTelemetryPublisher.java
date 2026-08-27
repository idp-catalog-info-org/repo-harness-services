/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.telemetry;

import static io.harness.annotations.dev.HarnessTeam.STO;
import static io.harness.configuration.DeployVariant.DEPLOY_VERSION;
import static io.harness.telemetry.Destination.ALL;

import static java.lang.Math.ceil;
import static java.lang.Math.max;

import io.harness.ModuleType;
import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.telemetry.TelemetryOption;
import io.harness.telemetry.TelemetryReporter;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(STO)
public class StoTelemetryPublisher {
  @Inject TelemetryReporter telemetryReporter;
  @Inject private AccountUtils accountUtils;
  @Inject CILicenseService ciLicenseService;
  @Inject private STOServiceUtils stoServiceUtils;

  String OLD_LICENSE_USAGE = "sto_license_usage_old";
  String NEW_LICENSE_USAGE = "sto_license_usage_new";
  String DAILY_LICENSE_USAGE = "sto_license_usage_daily";
  String HARNESS_CODE_USAGE = "sto_harness_code_usage";
  String HARNESS_CODE_USAGE_DAILY = "sto_harness_code_usage_daily";
  String HARNESS_CONTAINER_USAGE = "sto_harness_container_usage";
  String HARNESS_CONTAINER_USAGE_DAILY = "sto_harness_container_usage_daily";
  String ACCOUNT_DEPLOY_TYPE = "account_deploy_type";
  private static final String ACCOUNT = "Account";
  private static final String GROUP_TYPE = "group_type";
  private static final String GROUP_ID = "group_id";
  private static final Double SCAN_COUNT_PER_DEVELOPER = 100.0;

  public void recordTelemetry() {
    log.info("STOTelemetryPublisher recordTelemetry execute started.");
    try {
      List<String> allAccounts = accountUtils.getAllNGAccountIds();

      long timestamp = Instant.now().toEpochMilli();
      final Gson gson = new Gson();
      Type type = new TypeToken<List<StoUsage>>() {}.getType();

      List<StoUsage> monthlyUsage = gson.fromJson(stoServiceUtils.getUsageAllAccounts(timestamp, 30), type);
      HashMap<String, StoUsage> monthlyUsageMap = new HashMap<>();
      for (StoUsage usage : monthlyUsage) {
        monthlyUsageMap.put(usage.accountId, usage);
      }

      List<StoUsage> dailyUsage = gson.fromJson(stoServiceUtils.getUsageAllAccounts(timestamp, 1), type);
      HashMap<String, StoUsage> dailyUsageMap = new HashMap<>();
      for (StoUsage usage : dailyUsage) {
        dailyUsageMap.put(usage.accountId, usage);
      }

      log.info("Size of the account list is {} ", monthlyUsage.size());

      for (String accountId : allAccounts) {
        StoUsage mUsage = monthlyUsageMap.get(accountId);
        StoUsage dUsage = dailyUsageMap.get(accountId);

        if (ciLicenseService.hasActiveModuleLicense(accountId, ModuleType.STO.toString())) {
          HashMap<String, Object> map = new HashMap<>();
          map.put(GROUP_TYPE, ACCOUNT);
          map.put(GROUP_ID, accountId);
          map.put(ACCOUNT_DEPLOY_TYPE, System.getenv().get(DEPLOY_VERSION));

          if (mUsage != null) {
            map.put(
                OLD_LICENSE_USAGE, max((int) ceil(mUsage.scanCount / SCAN_COUNT_PER_DEVELOPER), mUsage.developerCount));
            map.put(NEW_LICENSE_USAGE, mUsage.scanCount);
            map.put(HARNESS_CODE_USAGE, mUsage.harnessCodeCount);
            map.put(HARNESS_CONTAINER_USAGE, mUsage.harnessContainerCount);
          } else {
            map.put(OLD_LICENSE_USAGE, 0);
            map.put(NEW_LICENSE_USAGE, 0);
            map.put(HARNESS_CODE_USAGE, 0);
            map.put(HARNESS_CONTAINER_USAGE, 0);
          }

          if (dUsage != null) {
            map.put(DAILY_LICENSE_USAGE, dUsage.scanCount);
            map.put(HARNESS_CODE_USAGE_DAILY, dUsage.harnessCodeCount);
            map.put(HARNESS_CONTAINER_USAGE_DAILY, dUsage.harnessContainerCount);
          } else {
            map.put(DAILY_LICENSE_USAGE, 0);
            map.put(HARNESS_CODE_USAGE_DAILY, 0);
            map.put(HARNESS_CONTAINER_USAGE_DAILY, 0);
          }

          telemetryReporter.sendGroupEvent(accountId, null, map, Collections.singletonMap(ALL, true),
              TelemetryOption.builder().sendForCommunity(false).build());
          log.info("Scheduled STOTelemetryPublisher event sent for account {}", accountId);
        }
      }
    } catch (Exception e) {
      log.error("STOTelemetryPublisher recordTelemetry execute failed.", e);
    } finally {
      log.info("STOTelemetryPublisher recordTelemetry execute finished.");
    }
  }
}

class StoUsage {
  String accountId;
  int developerCount;
  int scanCount;
  int harnessCodeCount;
  int harnessContainerCount;
}
