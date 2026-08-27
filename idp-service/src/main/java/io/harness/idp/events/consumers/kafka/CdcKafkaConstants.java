/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers.kafka;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(IDP)
@UtilityClass
public class CdcKafkaConstants {
  public static final String CATALOG_CONSUMER_GROUP = "idp-service-catalog-cdc";
  public static final String SCAFFOLDER_TASKS_CONSUMER_GROUP = "idp-service-scaffolder-tasks-cdc";
  public static final String CHECKS_CONSUMER_GROUP = "idp-service-checks-cdc";
  public static final String SCORECARDS_CONSUMER_GROUP = "idp-service-scorecards-cdc";
  public static final String MODULE_LICENSES_CONSUMER_GROUP = "idp-service-module-licenses-cdc";
  public static final String APP_CONFIGS_CONSUMER_GROUP = "idp-service-app-configs-cdc";

  public static final String CDC_KAFKA_EXECUTOR_SERVICE = "CdcKafkaExecutorService";

  // Feature flag names for CDC Kafka consumers
  public static final String FF_IDP_CDC_KAFKA_CATALOG = "IDP_CDC_KAFKA_CATALOG";
  public static final String FF_IDP_CDC_KAFKA_SCAFFOLDER_TASKS = "IDP_CDC_KAFKA_SCAFFOLDER_TASKS";
  public static final String FF_IDP_CDC_KAFKA_CHECKS = "IDP_CDC_KAFKA_CHECKS";
  public static final String FF_IDP_CDC_KAFKA_SCORECARDS = "IDP_CDC_KAFKA_SCORECARDS";
  public static final String FF_IDP_CDC_KAFKA_MODULE_LICENSES = "IDP_CDC_KAFKA_MODULE_LICENSES";
  public static final String FF_IDP_CDC_KAFKA_APP_CONFIGS = "IDP_CDC_KAFKA_APP_CONFIGS";
}
