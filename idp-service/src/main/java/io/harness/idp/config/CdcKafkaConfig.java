/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.config;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@OwnedBy(IDP)
@Value
@Builder
@Jacksonized
@SuppressWarnings("checkstyle:RepetitiveNameCheck")
public class CdcKafkaConfig {
  public static final String CATALOG_CONSUMER = "catalog";
  public static final String SCAFFOLDER_TASKS_CONSUMER = "scaffolderTasks";
  public static final String CHECKS_CONSUMER = "checks";
  public static final String SCORECARDS_CONSUMER = "scorecards";
  public static final String MODULE_LICENSES_CONSUMER = "moduleLicenses";
  public static final String APP_CONFIGS_CONSUMER = "appConfigs";

  @JsonProperty boolean enabled;
  @JsonProperty @Builder.Default List<CdcKafkaConsumerConfig> consumers = Collections.emptyList();
  @JsonProperty @Builder.Default int maxPollRecords = 100;

  public Optional<CdcKafkaConsumerConfig> getConsumer(String name) {
    if (consumers == null) {
      return Optional.empty();
    }
    return consumers.stream().filter(c -> name.equals(c.getName())).findFirst();
  }

  public boolean isConsumerEnabled(String name) {
    if (!enabled) {
      return false;
    }
    return getConsumer(name).map(CdcKafkaConsumerConfig::isEnabled).orElse(false);
  }

  public static CdcKafkaConfig defaultConfig() {
    return CdcKafkaConfig.builder().enabled(false).consumers(Collections.emptyList()).build();
  }
}
