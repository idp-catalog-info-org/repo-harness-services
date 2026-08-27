/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.execution;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.app.beans.entities.ExecutionQueueLimit;
import io.harness.beans.FeatureName;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.stages.parameters.IntegrationStageStepParametersPMS;
import io.harness.beans.yaml.extended.CIResourceClass;
import io.harness.beans.yaml.extended.infrastrucutre.HostedVmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.platform.ArchType;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.ExecutionLimitSpec;
import io.harness.ci.config.ExecutionLimits;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata;
import io.harness.ci.execution.execution.metadata.CIExecutionMetadata.CIExecutionMetadataBuilder;
import io.harness.ci.execution.integrationstage.VmInitializeTaskParamsBuilder;
import io.harness.ci.execution.integrationstage.utils.IntegrationStageUtils;
import io.harness.ci.execution.integrationstage.vm.intfc.VmInitializeUtils;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.metrics.ExecutionMetricsService;
import io.harness.ci.pipeline.executions.beans.CIInfraDetails;
import io.harness.delegate.task.citasks.cik8handler.params.CIConstants;
import io.harness.exception.WingsException;
import io.harness.licensing.Edition;
import io.harness.licensing.beans.summary.dto.LicensesWithSummaryDTO;
import io.harness.plancreator.steps.common.StageBaseParameters;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.CIExecutionRepository;
import io.harness.repositories.ExecutionQueueLimitRepository;
import io.harness.utils.CILicenseUsageUtils;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class QueueExecutionUtils {
  @Inject private CIExecutionRepository ciExecutionRepository;
  @Inject(optional = true) private CILicenseService ciLicenseService;
  @Inject private ExecutionLimits executionLimits;
  private static final int MAX_LIMIT = 100;
  @Inject private ExecutionQueueLimitRepository executionQueueLimitRepository;
  @Inject private ExecutionMetricsService executionMetricsService;
  @Inject private VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;
  @Inject private VmInitializeUtils vmInitializeUtils;
  @Inject CILicenseUsageUtils ciLicenseUsageUtils;
  @Inject(optional = true) CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private CIFeatureFlagService featureFlagService;
  private static final String QUEUED_EXECUTION_COUNT = "ci_queued_vm_execution_count";
  private static final String GLOBAL_QUEUED_EXECUTION_COUNT = "ci_global_queued_vm_execution_count";
  private static final String QUEUED_EXECUTION_TIME = "ci_vm_queued_time";
  private static final String GLOBAL_QUEUED_EXECUTION_TIME = "ci_vm_global_queued_time";
  private static final String ACTIVE_EXECUTION_COUNT = "ci_active_vm_execution_count";
  private static final String QUEUE_LIMITS = "ci_queue_limits_per_account";
  private static final String QUEUE_LIMITS_MAC = "ci_queue_limits_mac_per_account";
  private static final String GLOBAL_QUEUE_TOPIC = "global_capacity_queue";

  public void addExecutionRecord(
      Infrastructure infrastructure, String accountId, String stageExecutionID, Long stageTimeout) {
    try {
      addActiveExecutionBuild(infrastructure, accountId, stageExecutionID, stageTimeout);
    } catch (Exception ex) {
      log.error("Failed to add Execution record for {}", stageExecutionID, ex);
    }
  }

  private void addActiveExecutionBuild(
      Infrastructure infrastructure, String accountID, String stagExecutionID, Long stageTimeout) {
    if (ciExecutionRepository.findByStageExecutionId(stagExecutionID) == null) {
      Infrastructure.Type infraType = infrastructure.getType();
      OSType buildType = IntegrationStageUtils.getBuildType(infrastructure);

      CIExecutionMetadataBuilder builder = CIExecutionMetadata.builder()
                                               .accountId(accountID)
                                               .status(Status.QUEUED.toString())
                                               .buildType(buildType)
                                               .stageExecutionId(stagExecutionID)
                                               .infraType(infraType);

      if (stageTimeout != null && stageTimeout > CIConstants.STAGE_MAX_TTL_SECS) {
        stageTimeout = stageTimeout + CIConstants.TEN_MINTUTES_IN_SEC; // Add 10 minute buffer
        Date expireAfter = Date.from(OffsetDateTime.now().plusSeconds(stageTimeout).toInstant());
        builder.expireAfter(expireAfter);
        log.info("Overriding expireAfter of CIExecutionMetadata for stageExecutionId: {} with expireAfter: {}s",
            stagExecutionID, stageTimeout);
      }
      ciExecutionRepository.save(builder.build());
    }
  }

  private long getExecutionsCountByOSAndStatus(
      String accountID, OSType osType, List<String> status, Infrastructure.Type infraType) {
    return ciExecutionRepository.countByAccountIdAndBuildTypeAndStatusInAndInfraType(
        accountID, osType, status, infraType);
  }

  public CIExecutionMetadata deleteActiveExecutionRecord(String stageExecutionID) {
    CIExecutionMetadata executionMetadata = ciExecutionRepository.findByStageExecutionId(stageExecutionID);
    ciExecutionRepository.deleteByStageExecutionId(stageExecutionID);
    return executionMetadata;
  }

  public boolean shouldQueue(String accountID, Infrastructure infrastructure, boolean enableQueue, String moduleType) {
    return shouldQueue(accountID, infrastructure, enableQueue, moduleType, null);
  }

  public boolean shouldQueue(String accountID, Infrastructure infrastructure, boolean enableQueue, String moduleType,
      ExecutionPrincipalInfo principalInfo) {
    if (!enableQueue) {
      return false;
    }

    OSType osType = IntegrationStageUtils.getBuildType(infrastructure);
    String archType = getArchType(infrastructure).toString();

    Map<OSType, Long> activeCounts = getExecutionCountsByOS(accountID,
        List.of(Status.QUEUED.toString(), Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(),
            Status.RUNNING.toString()),
        infrastructure.getType());
    ExecutionLimitSpec executionLimitSpec = getExecutionLimit(accountID, moduleType, principalInfo);

    long linuxAndWindowsCount =
        activeCounts.getOrDefault(OSType.Linux, 0L) + activeCounts.getOrDefault(OSType.Windows, 0L);

    log.info("Queue limits for account: {}, total (linux+windows): {}, mac: {}. Current count: linux: {}, mac: {}, "
            + "windows: {}, combined (linux+windows): {}",
        accountID, executionLimitSpec.getDefaultTotalExecutionCount(), executionLimitSpec.getDefaultMacExecutionCount(),
        activeCounts.getOrDefault(OSType.Linux, 0L), activeCounts.getOrDefault(OSType.MacOS, 0L),
        activeCounts.getOrDefault(OSType.Windows, 0L), linuxAndWindowsCount);

    recordExecutionMetrics(accountID, archType, activeCounts, true, ACTIVE_EXECUTION_COUNT);

    long queuedCount = switch (osType) {
      case MacOS -> Math.max(0, activeCounts.getOrDefault(OSType.MacOS, 0L) - executionLimitSpec.getDefaultMacExecutionCount());
      default -> Math.max(0, linuxAndWindowsCount - executionLimitSpec.getDefaultTotalExecutionCount());
    };

    executionMetricsService.recordQueuedExecutionCount(accountID, QUEUED_EXECUTION_COUNT, osType.toString(), archType, queuedCount);
    return queuedCount > 0;
  }

  public boolean shouldRun(String accountID, Infrastructure infrastructure, String moduleType) {
    return shouldRun(accountID, infrastructure, moduleType, null);
  }

  public boolean shouldRun(
      String accountID, Infrastructure infrastructure, String moduleType, ExecutionPrincipalInfo principalInfo) {
    OSType osType = IntegrationStageUtils.getBuildType(infrastructure);
    Map<OSType, Long> runningCounts = getExecutionCountsByOS(accountID, List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString(), Status.RUNNING.toString()), infrastructure.getType());
    ExecutionLimitSpec executionLimitSpec = getExecutionLimit(accountID, moduleType, principalInfo);

    long linuxAndWindowsCount = runningCounts.getOrDefault(OSType.Linux, 0L) + runningCounts.getOrDefault(OSType.Windows, 0L);

    boolean shouldRun = switch (osType) {
      case MacOS -> runningCounts.getOrDefault(OSType.MacOS, 0L) < executionLimitSpec.getDefaultMacExecutionCount();
      default -> linuxAndWindowsCount < executionLimitSpec.getDefaultTotalExecutionCount();
    };

    if (!shouldRun) {
      log.info(
          "Total executions limit for account {} total (linux+windows): {}, mac: {}. Total running executions: linux: {}, mac: {}, windows: {}, combined (linux+windows): {}",
          accountID, executionLimitSpec.getDefaultTotalExecutionCount(),
          executionLimitSpec.getDefaultMacExecutionCount(),
          runningCounts.getOrDefault(OSType.Linux, 0L),
          runningCounts.getOrDefault(OSType.MacOS, 0L),
          runningCounts.getOrDefault(OSType.Windows, 0L),
          linuxAndWindowsCount);
    }

    return shouldRun;
  }

  private Map<OSType, Long> getExecutionCountsByOS(String accountId, List<String> statuses, Infrastructure.Type infraType) {
    Map<OSType, Long> counts = new EnumMap<>(OSType.class);
    for (OSType os : OSType.values()) {
          counts.put(os, getExecutionsCountByOSAndStatus(accountId, os, statuses, infraType));
        }
        return counts;
    }

    private void recordExecutionMetrics(
        String accountId, String archType, Map<OSType, Long> counts, boolean isActive, String metric) {
      for (Map.Entry<OSType, Long> entry : counts.entrySet()) {
        if (isActive) {
          executionMetricsService.recordActiveExecutionCount(
              accountId, metric, entry.getKey().toString(), archType, entry.getValue());
        } else {
          executionMetricsService.recordQueuedExecutionCount(
              accountId, metric, entry.getKey().toString(), archType, entry.getValue());
        }
      }
    }

    public ExecutionLimitSpec getDefaultLimitsBasedOnLicenseAndModuleType(String accountId, String moduleType) {
      return getDefaultLimitsBasedOnLicenseAndModuleType(accountId, moduleType, null);
    }

    public ExecutionLimitSpec getDefaultLimitsBasedOnLicenseAndModuleType(
        String accountId, String moduleType, ExecutionPrincipalInfo principalInfo) {
      LicensesWithSummaryDTO licenseSummary = ciLicenseService.getLicenseSummary(accountId, moduleType, principalInfo);
      if (licenseSummary == null) {
        throw new WingsException("Please enable CI free plan or reach out to support.");
      }
      return switch (licenseSummary.getEdition()) {
        case TEAM -> executionLimits.getTeam();
        case ENTERPRISE -> executionLimits.getEnterprise();
        case DEVOPS_ESSENTIALS -> executionLimits.getDevopsEssentials();
        case ESSENTIALS -> executionLimits.getEssentials();
        default -> executionLimits.getFree();
      };
    }

    private ExecutionLimitSpec getExecutionLimit(String accountId, String moduleType) {
      return getExecutionLimit(accountId, moduleType, null);
    }

    private ExecutionLimitSpec getExecutionLimit(String accountId, String moduleType, ExecutionPrincipalInfo principalInfo) {
      long macLimit, totalLimit;
      ExecutionLimitSpec defaultSpec = getDefaultLimitsBasedOnLicenseAndModuleType(accountId, moduleType, principalInfo);

    macLimit = defaultSpec.getDefaultMacExecutionCount();
    totalLimit = defaultSpec.getDefaultTotalExecutionCount();

    // Apply overrides if they exist (partial override support)
    Optional<ExecutionQueueLimit> overriddenConfig = executionQueueLimitRepository.findFirstByAccountIdentifier(accountId);
    if (overriddenConfig.isPresent()) {
      ExecutionQueueLimit override = overriddenConfig.get();
      if (StringUtils.isNotEmpty(override.getMacExecLimit())) {
        macLimit = Long.parseLong(override.getMacExecLimit());
      }
      if (StringUtils.isNotEmpty(override.getTotalExecLimit())) {
        totalLimit = Long.parseLong(override.getTotalExecLimit());
      }
    }

    // Record metrics
    executionMetricsService.recordQueueLimitPerAccount(accountId, QUEUE_LIMITS, totalLimit);
    executionMetricsService.recordQueueLimitPerAccount(accountId, QUEUE_LIMITS_MAC, macLimit);

    return ExecutionLimitSpec.builder()
        .defaultMacExecutionCount(macLimit)
        .defaultTotalExecutionCount(totalLimit)
        .build();
  }


  public void publishQueueCountMetrics(Ambiance ambiance, Infrastructure infrastructure) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String archType = getArchType(infrastructure).toString();

    Map<OSType, Long> queuedCounts = getExecutionCountsByOS(accountId, List.of(Status.QUEUED.toString()), infrastructure.getType());
    Map<OSType, Long> globalQueuedCounts = getExecutionCountsByOS(accountId, List.of(Status.QUEUED_GLOBAL_INFRA_CAPACITY_REACHED.toString()), infrastructure.getType());
    Map<OSType, Long> activeCounts = getExecutionCountsByOS(accountId, List.of(Status.QUEUED.toString(), Status.RUNNING.toString()), infrastructure.getType());

    recordExecutionMetrics(accountId, archType, queuedCounts, false, QUEUED_EXECUTION_COUNT);
    recordExecutionMetrics(accountId, archType, globalQueuedCounts, false, GLOBAL_QUEUED_EXECUTION_COUNT);
    recordExecutionMetrics(accountId, archType, activeCounts, true, ACTIVE_EXECUTION_COUNT);
  }

  // Publishes the queue time metric using the Redis stream queue ID.\
  // The queue ID is in the format "<epochMillis>-<sequence>", where the first part
  // is the epoch time in milliseconds when the task was queued.
  public void publishQueueTimeMetrics(Ambiance ambiance, Infrastructure infrastructure, String queueId) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();
    String archType = getArchType(infrastructure).toString();
    String osType = IntegrationStageUtils.getBuildType(infrastructure).toString();

    Double queueTimeMs = computeQueueTimeInMillis(queueId);
    if (queueTimeMs != null) {
      executionMetricsService.recordQueuedExecutionTime(
          accountId, stageExecutionId, QUEUED_EXECUTION_TIME, osType, archType, queueTimeMs);
    }
  }

  // Publishes the queue time metric using the Redis stream queue ID.\
  // The queue ID is in the format "<epochMillis>-<sequence>", where the first part
  // is the epoch time in milliseconds when the task was queued.
  public void publishGlobalQueueTimeMetrics(Ambiance ambiance, Infrastructure infrastructure, String queueId) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();
    String archType = getArchType(infrastructure).toString();
    String osType = IntegrationStageUtils.getBuildType(infrastructure).toString();

    Double queueTimeMs = computeQueueTimeInMillis(queueId);
    if (queueTimeMs != null) {
      executionMetricsService.recordQueuedExecutionTime(
          accountId, stageExecutionId, GLOBAL_QUEUED_EXECUTION_TIME, osType, archType, queueTimeMs);
    }
  }

  // Computes queue time in milliseconds using the Redis stream queue ID.
  // The queue ID is expected to be in the format "<epochMillis>-<sequence>".
  public Double computeQueueTimeInMillis(String queueId) {
    if (isNotEmpty(queueId)) {
      try {
        long queueEntryTimeMillis = Long.parseLong(queueId.split("-")[0]);
        return (double) (System.currentTimeMillis() - queueEntryTimeMillis);
      } catch (Exception e) {
        log.warn("Failed to parse queueId timestamp from: {}", queueId, e);
      }
    }
    return null;
  }

  // Checks if a queue message is stale (older than 35 days
  // Returns true if the message should be skipped (acknowledged without processing).
  public boolean isStaleQueueMessage(String itemId) {
    Double queueTimeInMillis = computeQueueTimeInMillis(itemId);
    if (queueTimeInMillis != null && queueTimeInMillis > CIConstants.THIRTY_FIVEDAYS_IN_MILLI_SEC) {
      log.warn("Stale queue message detected (older than 35 days). ItemId: {}, QueueTime: {} milliseconds",
          itemId, queueTimeInMillis);
      return true;
    }
    return false;
  }

  public static Infrastructure getInfrastructure(StepParameters stepParameters) {
    StageBaseParameters stageBaseParameters = (StageBaseParameters) stepParameters;
    IntegrationStageStepParametersPMS integrationStageConfig =
        (IntegrationStageStepParametersPMS) stageBaseParameters.getSpecConfig();
    return integrationStageConfig.getInfrastructure();
  }

  public static ParameterField<Timeout> getStageTimeout(StepParameters stepParameters) {
    if (stepParameters instanceof StageElementParameters stageElementParameters) {
      ParameterField<String> stageTimeout = stageElementParameters.getStageTimeout();
      if (stageTimeout != null && stageTimeout.getValue() != null) {
        return ParameterField.createValueField(Timeout.fromString(stageTimeout.getValue()));
      }
    }
    return null;
  }

  public static Map<String, Object> getStageVariables(StepParameters stepParameters) {
    StageBaseParameters stageBaseParameters = (StageBaseParameters) stepParameters;
    ParameterField<Map<String, Object>> variablesField = stageBaseParameters.getVariables();

    if (variablesField == null || ParameterField.isNull(variablesField)) {
      return null;
    }

    return variablesField.obtainValue();
  }

  public boolean isGlobalQueueEnabled(Ambiance ambiance, Infrastructure infrastructure) {
    boolean queueConcurrencyEnabled = (infrastructure.getType() == Infrastructure.Type.HOSTED_VM);
    OSType osType = IntegrationStageUtils.getBuildType(infrastructure);
    FeatureName globalQueueingFeatureFlag = getGlobalQueueingFeatureFlag(osType);

    return queueConcurrencyEnabled
        && featureFlagService.isEnabled(globalQueueingFeatureFlag, AmbianceUtils.getAccountId(ambiance))
        && ciExecutionServiceConfig.getGlobalQueueingConfig().getEnableGlobalQueue();
  }

  private static FeatureName getGlobalQueueingFeatureFlag(OSType osType) {
    if (osType == OSType.MacOS) {
      return FeatureName.CI_MAC_GLOBAL_QUEUEING_ENABLED;
    }
    return FeatureName.CI_GLOBAL_QUEUEING_ENABLED;
  }

  public static String getGlobalQueueTopic(String moduleType) {
    return GLOBAL_QUEUE_TOPIC + "_" + moduleType;
  }

  public String getGlobalQueueSubTopic(Ambiance ambiance, StepParameters stepParameters) {
    Infrastructure infrastructure = getInfrastructure(stepParameters);
    String osType = IntegrationStageUtils.getBuildType(infrastructure).toString();
    String archType = getArchType(infrastructure).toString();

    HostedVmInfraYaml hostedVmInfraYaml = (HostedVmInfraYaml) infrastructure;
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String moduleType = AmbianceUtils.getStageModuleType(ambiance);

    LicensesWithSummaryDTO licenseSummary =
        ciLicenseService.getLicenseSummary(accountId, moduleType, ambiance.getMetadata().getPrincipalInfo());
    boolean ciFreeLicense = licenseSummary != null && licenseSummary.getEdition() == Edition.FREE;
    Optional<String> resourceClass = ciLicenseUsageUtils.getResourceClass(accountId, hostedVmInfraYaml, ciFreeLicense);

    // subtopic format: os-arch-free/paid-resourceClass
    String licenseType = ciFreeLicense ? "free" :
          "paid";

          return osType + "-" + archType + "-" + licenseType + "-"
              + resourceClass.orElse(CIResourceClass.FLEX.toString());
      }

      private static ArchType getArchType(Infrastructure infrastructure) {
        CIInfraDetails ciInfraDetails = IntegrationStageUtils.getCiInfraDetails(infrastructure);
        return ArchType.fromString(ciInfraDetails.getInfraArchType());
      }
    }
