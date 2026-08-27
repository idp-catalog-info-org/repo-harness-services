/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import static io.harness.timescaledb.tables.Connectors.CONNECTORS;
import static io.harness.timescaledb.tables.CustomStageExecution.CUSTOM_STAGE_EXECUTION;
import static io.harness.timescaledb.tables.Environments.ENVIRONMENTS;
import static io.harness.timescaledb.tables.ExecutionTagsInfoNg.EXECUTION_TAGS_INFO_NG;
import static io.harness.timescaledb.tables.Infrastructures.INFRASTRUCTURES;
import static io.harness.timescaledb.tables.NgInstanceStats.NG_INSTANCE_STATS;
import static io.harness.timescaledb.tables.NgInstanceStatsDay.NG_INSTANCE_STATS_DAY;
import static io.harness.timescaledb.tables.NgInstanceStatsHour.NG_INSTANCE_STATS_HOUR;
import static io.harness.timescaledb.tables.NgInstanceStatsIterator.NG_INSTANCE_STATS_ITERATOR;
import static io.harness.timescaledb.tables.NgUsers.NG_USERS;
import static io.harness.timescaledb.tables.Organizations.ORGANIZATIONS;
import static io.harness.timescaledb.tables.PipelineExecutionSummary.PIPELINE_EXECUTION_SUMMARY;
import static io.harness.timescaledb.tables.PipelineExecutionSummaryCd.PIPELINE_EXECUTION_SUMMARY_CD;
import static io.harness.timescaledb.tables.Pipelines.PIPELINES;
import static io.harness.timescaledb.tables.Projects.PROJECTS;
import static io.harness.timescaledb.tables.RuntimeInputsInfo.RUNTIME_INPUTS_INFO;
import static io.harness.timescaledb.tables.ServiceInfraInfo.SERVICE_INFRA_INFO;
import static io.harness.timescaledb.tables.Services.SERVICES;
import static io.harness.timescaledb.tables.StageExecution.STAGE_EXECUTION;
import static io.harness.timescaledb.tables.StepExecution.STEP_EXECUTION;
import static io.harness.timescaledb.tables.TagsInfoNg.TAGS_INFO_NG;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.lock.interfaces.PersistentLocker;
import io.harness.ng.core.metrics.ProjectMovementTimescaleDbMigrationMetricsCollector;
import io.harness.ng.core.metrics.ProjectMovementTimescaleDbMigrationMetricsCollector.TimescaleTableInfo;
import io.harness.ng.core.metrics.ProjectMovementTimescaleDbMigrationMetricsConfig;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.dropwizard.lifecycle.Managed;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;

/**
 * Managed job for Project Movement TimeScaleDB migration metrics collection.
 * This class configures and manages the migration metrics collector for tracking TimeScaleDB migration progress.
 */
@OwnedBy(HarnessTeam.PL)
@Slf4j
@SuppressWarnings("checkstyle:RepetitiveNameCheck")
public class ProjectMovementTimescaleDbMigrationMetricsJob implements Managed {
  private static final String SERVICE_NAME = "ng-manager";

  // Define NG-Manager TimeScaleDB tables with unique_id and parent_unique_id fields
  private static final List<TimescaleTableInfo> NG_MANAGER_TIMESCALE_TABLES = Arrays.asList(
      new TimescaleTableInfo("connectors", CONNECTORS, CONNECTORS.UNIQUE_ID, CONNECTORS.PARENT_UNIQUE_ID),
      new TimescaleTableInfo("custom_stage_execution", CUSTOM_STAGE_EXECUTION, CUSTOM_STAGE_EXECUTION.UNIQUE_ID,
          CUSTOM_STAGE_EXECUTION.PARENT_UNIQUE_ID),
      new TimescaleTableInfo("environments", ENVIRONMENTS, ENVIRONMENTS.UNIQUE_ID, ENVIRONMENTS.PARENT_UNIQUE_ID),
      new TimescaleTableInfo("execution_tags_info_ng", EXECUTION_TAGS_INFO_NG, EXECUTION_TAGS_INFO_NG.UNIQUE_ID,
          EXECUTION_TAGS_INFO_NG.PARENT_UNIQUE_ID),
      new TimescaleTableInfo(
          "infrastructures", INFRASTRUCTURES, INFRASTRUCTURES.UNIQUE_ID, INFRASTRUCTURES.PARENT_UNIQUE_ID),
      new TimescaleTableInfo(
          "ng_instance_stats", NG_INSTANCE_STATS, NG_INSTANCE_STATS.UNIQUE_ID, NG_INSTANCE_STATS.PARENT_UNIQUE_ID),
      new TimescaleTableInfo("ng_instance_stats_day", NG_INSTANCE_STATS_DAY, NG_INSTANCE_STATS_DAY.UNIQUE_ID,
          NG_INSTANCE_STATS_DAY.PARENT_UNIQUE_ID),
      new TimescaleTableInfo("ng_instance_stats_hour", NG_INSTANCE_STATS_HOUR, NG_INSTANCE_STATS_HOUR.UNIQUE_ID,
          NG_INSTANCE_STATS_HOUR.PARENT_UNIQUE_ID),
      new TimescaleTableInfo("ng_instance_stats_iterator", NG_INSTANCE_STATS_ITERATOR,
          NG_INSTANCE_STATS_ITERATOR.UNIQUE_ID, NG_INSTANCE_STATS_ITERATOR.PARENT_UNIQUE_ID),
      new TimescaleTableInfo("ng_users", NG_USERS, NG_USERS.UNIQUE_ID, NG_USERS.PARENT_UNIQUE_ID),
      new TimescaleTableInfo("organizations", ORGANIZATIONS, ORGANIZATIONS.UNIQUE_ID, ORGANIZATIONS.PARENT_UNIQUE_ID),
      new TimescaleTableInfo("projects", PROJECTS, PROJECTS.UNIQUE_ID, PROJECTS.PARENT_UNIQUE_ID),
      new TimescaleTableInfo("services", SERVICES, SERVICES.UNIQUE_ID, SERVICES.PARENT_UNIQUE_ID),
      new TimescaleTableInfo("tags_info_ng", TAGS_INFO_NG, TAGS_INFO_NG.UNIQUE_ID, TAGS_INFO_NG.PARENT_UNIQUE_ID),
      new TimescaleTableInfo("pipelines", PIPELINES, PIPELINES.UNIQUE_ID, PIPELINES.PARENT_UNIQUE_ID),
      new TimescaleTableInfo("pipeline_execution_summary", PIPELINE_EXECUTION_SUMMARY,
          PIPELINE_EXECUTION_SUMMARY.UNIQUE_ID, PIPELINE_EXECUTION_SUMMARY.PARENT_UNIQUE_ID),
      new TimescaleTableInfo("pipeline_execution_summary_cd", PIPELINE_EXECUTION_SUMMARY_CD,
          PIPELINE_EXECUTION_SUMMARY_CD.UNIQUE_ID, PIPELINE_EXECUTION_SUMMARY_CD.PARENT_UNIQUE_ID),
      new TimescaleTableInfo("runtime_inputs_info", RUNTIME_INPUTS_INFO, RUNTIME_INPUTS_INFO.UNIQUE_ID,
          RUNTIME_INPUTS_INFO.PARENT_UNIQUE_ID),
      new TimescaleTableInfo(
          "service_infra_info", SERVICE_INFRA_INFO, SERVICE_INFRA_INFO.UNIQUE_ID, SERVICE_INFRA_INFO.PARENT_UNIQUE_ID),
      new TimescaleTableInfo(
          "stage_execution", STAGE_EXECUTION, STAGE_EXECUTION.UNIQUE_ID, STAGE_EXECUTION.PARENT_UNIQUE_ID),
      new TimescaleTableInfo(
          "step_execution", STEP_EXECUTION, STEP_EXECUTION.UNIQUE_ID, STEP_EXECUTION.PARENT_UNIQUE_ID));

  private final DSLContext secondaryDslContext;
  private final PersistentLocker persistentLocker;
  private ProjectMovementTimescaleDbMigrationMetricsConfig metricsConfig;
  private ProjectMovementTimescaleDbMigrationMetricsCollector metricsCollector;

  @Inject
  public ProjectMovementTimescaleDbMigrationMetricsJob(
      @Named("SecondaryDSLContext") DSLContext secondaryDslContext, PersistentLocker persistentLocker) {
    this.secondaryDslContext = secondaryDslContext;
    this.persistentLocker = persistentLocker;
  }

  /**
   * Configure the metrics collector with the provided configuration.
   * @param metricsConfig Configuration for the metrics collector
   */
  public void configure(ProjectMovementTimescaleDbMigrationMetricsConfig metricsConfig) {
    this.metricsConfig = metricsConfig;
    this.metricsCollector = new ProjectMovementTimescaleDbMigrationMetricsCollector(
        secondaryDslContext, persistentLocker, SERVICE_NAME, NG_MANAGER_TIMESCALE_TABLES);
  }

  @Override
  public void start() throws Exception {
    if (metricsCollector == null) {
      throw new IllegalStateException(
          "ProjectMovementTimescaleDbMigrationMetricsCollector has not been configured yet");
    }
    if (metricsConfig == null) {
      throw new IllegalStateException("ProjectMovementTimescaleDbMigrationMetricsConfig has not been configured yet");
    }

    if (!metricsConfig.isEnabled()) {
      log.info("Project Movement TimeScaleDB migration metrics collection is disabled");
      return;
    }

    int initialDelayMinutes = metricsConfig.getInitialDelayMinutes();
    int frequencyMinutes = metricsConfig.getFrequencyMinutes();

    metricsCollector.configure(initialDelayMinutes, frequencyMinutes);
    metricsCollector.start();
    log.info("Project Movement TimeScaleDB migration metrics collection started successfully");
  }

  @Override
  public void stop() throws Exception {
    if (metricsCollector != null) {
      metricsCollector.stop();
    }
  }
}
