/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.account.settings.service.impl;

import static io.harness.account.overrides.PipelineOverridesConstants.DEFAULT_MAX_CONCURRENCY_ENTERPRISE;
import static io.harness.account.overrides.PipelineOverridesConstants.DEFAULT_MAX_EXPRESSION_CALLS;
import static io.harness.account.overrides.PipelineOverridesConstants.DEFAULT_MAX_FILE_SIZE_LIMIT;
import static io.harness.account.overrides.PipelineOverridesConstants.DEFAULT_MAX_INPUT_PARAMETER_SIZE_IN_BYTES;
import static io.harness.account.overrides.PipelineOverridesConstants.DEFAULT_MAX_OUTCOME_RESPONSE_SIZE_IN_BYTES;
import static io.harness.account.overrides.PipelineOverridesConstants.DEFAULT_MAX_PAYLOAD_SIZE_LIMIT;
import static io.harness.account.overrides.PipelineOverridesConstants.DEFAULT_MAX_PIPELINE_CREATION_LIMIT;
import static io.harness.account.overrides.PipelineOverridesConstants.DEFAULT_MAX_QUEUED_EXECUTIONS;
import static io.harness.account.overrides.PipelineOverridesConstants.DEFAULT_MAX_TRIGGER_CREATION_LIMIT;
import static io.harness.account.overrides.PipelineOverridesConstants.DEFAULT_NO_LIMIT;
import static io.harness.beans.FeatureName.PIE_PIPELINE_SETTINGS_ENFORCEMENT_LIMIT;
import static io.harness.beans.FeatureName.PIPE_PROJECT_LEVEL_EXECUTION_CONCURRENCY;
import static io.harness.licensing.Edition.DEVOPS_ESSENTIALS;
import static io.harness.licensing.Edition.ENTERPRISE;
import static io.harness.licensing.Edition.ESSENTIALS;
import static io.harness.licensing.Edition.FREE;
import static io.harness.licensing.Edition.TEAM;
import static io.harness.pms.utils.NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_LIMIT;
import static io.harness.pms.utils.NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_PROJECTS;
import static io.harness.pms.utils.NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_TYPE;

import io.harness.account.overrides.EditionBasedAccountConfigStrategy;
import io.harness.account.overrides.EditionBasedAccountConfigStrategyFactory;
import io.harness.account.settings.response.PlanExecutionSettingResponse;
import io.harness.account.settings.service.PipelineSettingsService;
import io.harness.data.structure.EmptyPredicate;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionConcurrencyMode;
import io.harness.execution.PriorityConcurrentExecutionsMetadata;
import io.harness.execution.PriorityProjects;
import io.harness.execution.PriorityType;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ngsettings.SettingCategory;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.ngsettings.dto.SettingResponseDTO;
import io.harness.pms.accountoverrides.ExpressionCallType;
import io.harness.pms.utils.NGPipelineSettingsConstant;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.utils.PmsFeatureFlagService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class PipelineSettingsServiceImpl implements PipelineSettingsService {
  @Inject PlanExecutionService planExecutionService;
  @Inject NgLicenseHttpClient ngLicenseHttpClient;
  @Inject PmsFeatureFlagService featureFlagService;
  @Inject NGSettingsClient ngSettingsClient;
  @Inject EditionBasedAccountConfigStrategyFactory editionBasedAccountConfigStrategyFactory;
  @Inject PipelineRetentionService pipelineRetentionService;
  @Inject @Named("pipelineExecutionDefaultMaxLeafStepConcurrency") Integer defaultMaxLeafStepConcurrency;

  private final String SETTINGS_HARNESS_DEVELOPER_DOC_URL =
      "https://developer.harness.io/docs/platform/pipelines/pipeline-settings/#fixed-pipeline-settings";

  private final LoadingCache<String, List<ModuleLicenseDTO>> moduleLicensesCache =
      CacheBuilder.newBuilder()
          .expireAfterWrite(30, TimeUnit.MINUTES)
          .build(new CacheLoader<String, List<ModuleLicenseDTO>>() {
            @Override
            public List<ModuleLicenseDTO> load(@NotNull final String accountIdentifier) {
              return listAllEnabledFeatureFlagsForAccount(accountIdentifier);
            }
          });

  private List<ModuleLicenseDTO> listAllEnabledFeatureFlagsForAccount(String accountIdentifier) {
    return NGRestUtils.getResponse(ngLicenseHttpClient.getModuleLicenses(accountIdentifier));
  }

  private long getMaxOutcomeSizeByEdition(String accountIdentifier) {
    try {
      Edition edition = getEdition(accountIdentifier);
      return editionBasedAccountConfigStrategyFactory.getStrategy(edition).getMaxOutcomeSize();
    } catch (Exception ex) {
      log.warn(String.format(
                   "Failed to get default max outcome size for account {%s} with error message: ", accountIdentifier),
          ex);
    }
    return DEFAULT_MAX_OUTCOME_RESPONSE_SIZE_IN_BYTES;
  }

  private long getMaxQueuedExecutionLimitByEdition(String accountIdentifier) {
    try {
      Edition edition = getEdition(accountIdentifier);
      return editionBasedAccountConfigStrategyFactory.getStrategy(edition).getMaxQueuedExecutionLimit();
    } catch (Exception ex) {
      log.warn(String.format(
                   "Failed to get default max queued limit for account {%s} with error message: ", accountIdentifier),
          ex);
    }
    return DEFAULT_MAX_QUEUED_EXECUTIONS;
  }

  private long getMaxTriggerCreationLimitByEdition(String accountIdentifier) {
    try {
      Edition edition = getEdition(accountIdentifier);
      return editionBasedAccountConfigStrategyFactory.getStrategy(edition).getMaxTriggerCreationLimit();
    } catch (Exception ex) {
      log.warn(String.format("Failed to get default max trigger creation limit for account {%s} with error message: ",
                   accountIdentifier),
          ex);
    }
    return DEFAULT_MAX_TRIGGER_CREATION_LIMIT;
  }

  private long getMaxFileSizeLimitByEdition(String accountIdentifier) {
    try {
      Edition edition = getEdition(accountIdentifier);
      return editionBasedAccountConfigStrategyFactory.getStrategy(edition).getMaxFileSizeLimit();
    } catch (Exception ex) {
      log.warn(String.format("Failed to get default max file size limit for account {%s} with error message: ",
                   accountIdentifier),
          ex);
    }
    return DEFAULT_MAX_FILE_SIZE_LIMIT;
  }

  private long getPayloadSizeLimitByEdition(String accountIdentifier) {
    try {
      Edition edition = getEdition(accountIdentifier);
      return editionBasedAccountConfigStrategyFactory.getStrategy(edition).getPayloadSizeLimit();
    } catch (Exception ex) {
      log.warn(String.format("Failed to get default max file size limit for account {%s} with error message: ",
                   accountIdentifier),
          ex);
    }
    return DEFAULT_MAX_PAYLOAD_SIZE_LIMIT;
  }

  private long getMaxPipelineCreationLimitByEdition(String accountIdentifier) {
    try {
      Edition edition = getEdition(accountIdentifier);
      return editionBasedAccountConfigStrategyFactory.getStrategy(edition).getMaxPipelineCreationLimit();
    } catch (Exception ex) {
      log.warn(String.format("Failed to get default max pipeline creation limit for account {%s} with error message: ",
                   accountIdentifier),
          ex);
    }
    return DEFAULT_MAX_PIPELINE_CREATION_LIMIT;
  }

  @VisibleForTesting
  protected Edition getEdition(String accountIdentifier) throws ExecutionException {
    List<ModuleLicenseDTO> moduleLicenseDTOS = moduleLicensesCache.get(accountIdentifier);
    Edition edition = FREE; // Default edition is FREE

    // Iterate over the licenses to determine the correct edition
    for (ModuleLicenseDTO moduleLicenseDTO : moduleLicenseDTOS) {
      // Ignore trial licenses
      if (moduleLicenseDTO.getLicenseType() == LicenseType.TRIAL) {
        continue;
      }

      // Highest priority: ENTERPRISE
      if (moduleLicenseDTO.getEdition() == ENTERPRISE) {
        return ENTERPRISE; // Return immediately as it has the highest priority
      }

      // Second priority: ESSENTIALS
      if (moduleLicenseDTO.getEdition() == ESSENTIALS) {
        edition = ESSENTIALS;
      }

      // Third priority: TEAM
      if (moduleLicenseDTO.getEdition() == TEAM && edition != ESSENTIALS) {
        edition = TEAM;
      }

      // Fourth priority: DEVOPS_ESSENTIALS
      if (moduleLicenseDTO.getEdition() == DEVOPS_ESSENTIALS && edition != ESSENTIALS && edition != TEAM) {
        edition = DEVOPS_ESSENTIALS;
      }
    }

    return edition;
  }

  @Override
  public int getMaxExpressionCalls(String accountIdentifier, ExpressionCallType callType) {
    try {
      Edition edition = getEdition(accountIdentifier);
      return editionBasedAccountConfigStrategyFactory.getStrategy(edition).getMaxExpressionCalls(
          accountIdentifier, callType);
    } catch (Exception ex) {
      log.warn(String.format("Failed to get max %s expression calls for account {%s} with error message: ", callType,
                   accountIdentifier),
          ex);
    }
    return DEFAULT_MAX_EXPRESSION_CALLS;
  }

  @VisibleForTesting
  protected PlanExecutionSettingResponse shouldQueueInternal(long maxCount, long runningExecutionsForGivenPipeline) {
    if (runningExecutionsForGivenPipeline >= maxCount) {
      return PlanExecutionSettingResponse.builder().shouldQueue(true).useNewFlow(true).build();
    }
    return PlanExecutionSettingResponse.builder().shouldQueue(false).useNewFlow(true).build();
  }

  @VisibleForTesting
  protected long countCurrentQueuedExecutionInternal(String accountIdentifier) {
    try {
      return planExecutionService.countQueuedExecutionsForGivenAccount(accountIdentifier);
    } catch (Exception e) {
      log.warn(String.format(
                   "Failed to get current execution count for account {%s} with error message: ", accountIdentifier),
          e);
    }
    return DEFAULT_NO_LIMIT;
  }

  @Override
  public long getMaxPipelineCreationCount(String accountIdentifier) {
    try {
      Edition edition = getEdition(accountIdentifier);
      return editionBasedAccountConfigStrategyFactory.getStrategy(edition).getMaxPipelineCreationLimit(
          accountIdentifier);
    } catch (Exception ex) {
      log.warn(String.format("Failed to get max pipeline creation count for account {%s} with error message: ",
                   accountIdentifier),
          ex);
      return DEFAULT_MAX_PIPELINE_CREATION_LIMIT;
    }
  }

  @Override
  public PlanExecutionSettingResponse shouldQueuePlanExecution(String accountIdentifier) {
    long concurrency = getMaxConcurrency(accountIdentifier);
    long currentExecutionCount = getCurrentExecutionCount(accountIdentifier);
    if (concurrency == Long.MAX_VALUE || currentExecutionCount == -1) {
      return PlanExecutionSettingResponse.builder().shouldQueue(false).useNewFlow(false).build();
    }
    return shouldQueueInternal(concurrency, currentExecutionCount);
  }

  /**
   * Determines whether a pipeline execution should be queued based on account concurrency limits and priority settings.
   * The function implements a sophisticated queueing strategy that:
   * 1. Respects overall account concurrency limits
   * 2. Handles priority-based execution limits (High/Low)
   * 3. Manages both matching and non-matching priority scenarios
   * 4. Ensures high priority executions get preference when defined
   * 5. Allows low priority executions to use remaining capacity
   *
   * @param accountIdentifier The account for which to check execution limits
   * @param priorityTypeOfCurrentExecution Priority type (HIGH/LOW) of the current execution request
   * @return PlanExecutionSettingResponse indicating whether to queue and which flow to use
   */
  @Override
  public PlanExecutionSettingResponse shouldQueuePlanExecution(
      String accountIdentifier, PriorityType priorityTypeOfCurrentExecution) {
    // Get maximum allowed concurrent executions for the account
    long maxAccountConcurrency = getMaxConcurrency(accountIdentifier);
    // Get current number of executions running in the account
    long currentExecutionRunningInAccount = getCurrentExecutionCount(accountIdentifier);

    // If we're already at or above max concurrency, queue the execution
    if (currentExecutionRunningInAccount >= maxAccountConcurrency) {
      return PlanExecutionSettingResponse.builder()
          .shouldQueue(true)
          .useNewFlow(true)
          .priorityExecutionLimitReached(false)
          .build();
    }

    // Check if this is a default case (no limits set)
    if (isDefaultConcurrencyCase(maxAccountConcurrency, currentExecutionRunningInAccount)) {
      return PlanExecutionSettingResponse.builder()
          .shouldQueue(false)
          .useNewFlow(false)
          .priorityExecutionLimitReached(false)
          .build();
    }

    // Get priority execution preferences for the account
    PriorityConcurrentExecutionsMetadata priorityMetadata = getPriorityExecutionPreferences(accountIdentifier);
    // If no priority projects are defined, use simple concurrency check
    if (EmptyPredicate.isEmpty(priorityMetadata.getPriorityProjectsList())) {
      return shouldQueueInternal(maxAccountConcurrency, currentExecutionRunningInAccount);
    }

    // Handle priority-based execution queueing
    return handlePriorityBasedExecution(accountIdentifier, priorityTypeOfCurrentExecution, maxAccountConcurrency,
        currentExecutionRunningInAccount, priorityMetadata);
  }

  /**
   * Checks if this is a default concurrency case where no queueing is needed.
   * This happens when either:
   * 1. No max concurrency is set (maxAccountConcurrency = Long.MAX_VALUE)
   * 2. No executions are running (currentExecutionRunningInAccount = -1)
   */
  private boolean isDefaultConcurrencyCase(long maxAccountConcurrency, long currentExecutionRunningInAccount) {
    return maxAccountConcurrency == Long.MAX_VALUE || currentExecutionRunningInAccount == -1;
  }

  /**
   * Handles priority-based execution queueing logic.
   * This is the core logic that determines whether to queue based on:
   * - Whether the execution matches the defined priority
   * - Current execution counts for each priority
   * - Priority limits and allowances
   */
  private PlanExecutionSettingResponse handlePriorityBasedExecution(String accountIdentifier,
      PriorityType priorityTypeOfCurrentExecution, long maxAccountConcurrency, long currentExecutionRunningInAccount,
      PriorityConcurrentExecutionsMetadata priorityMetadata) {
    // Get the priority type defined in settings (HIGH/LOW)
    String definedPriority = priorityMetadata.getPriorityType();
    // Get the concurrent execution limit for the defined priority
    int priorityExecutionsLimit = priorityMetadata.getPriorityConcurrentExecutionsLimit();

    // Count current executions for the defined priority type
    PriorityType definedPriorityType = PriorityType.HIGH.name().equalsIgnoreCase(priorityMetadata.getPriorityType())
        ? PriorityType.HIGH
        : PriorityType.LOW;
    long currentExecutionsForPriority =
        planExecutionService.countRunningExecutionsForGivenPriorityInAccount(accountIdentifier, definedPriorityType);
    // Calculate executions of the other priority type
    long otherPriorityExecutions = currentExecutionRunningInAccount - currentExecutionsForPriority;

    // Check if current execution matches the defined priority type
    boolean isDefinedPriorityExecution = priorityTypeOfCurrentExecution.name().equalsIgnoreCase(definedPriority);
    // Check if HIGH priority is defined in settings
    boolean isHighPriorityDefined = YAMLFieldNameConstants.HIGH_PRIORITY.equals(definedPriority);

    // Handle based on whether this is a matching or non-matching priority execution
    if (isDefinedPriorityExecution) {
      return handleMatchingPriorityExecution(isHighPriorityDefined, maxAccountConcurrency, priorityExecutionsLimit,
          currentExecutionsForPriority, otherPriorityExecutions);
    }

    return handleNonMatchingPriorityExecution(isHighPriorityDefined, maxAccountConcurrency, priorityExecutionsLimit,
        currentExecutionsForPriority, otherPriorityExecutions);
  }

  /**
   * Handles queueing logic when execution priority matches defined priority.
   * For HIGH priority defined:
   * - Queue if high priority executions >= limit AND low priority is using its allowance
   * For LOW priority defined:
   * - Queue if low priority executions >= limit
   */
  private PlanExecutionSettingResponse handleMatchingPriorityExecution(boolean isHighPriorityDefined,
      long maxAccountConcurrency, int priorityExecutionsLimit, long currentExecutionsForPriority,
      long otherPriorityExecutions) {
    if (isHighPriorityDefined) {
      // currentExecutionsForPriority : Refers to HIGH Priority Executions
      // otherPriorityExecutions : Refers to LOW Priority Executions
      // For high priority, use min of limit and max concurrency
      long highPriorityAllowance = Math.min(priorityExecutionsLimit, maxAccountConcurrency);
      // Low priority can use remaining capacity
      long lowPriorityAllowance = Math.max(maxAccountConcurrency - highPriorityAllowance, 0);
      // Queue if at high priority limit AND low priority is using its allowance
      boolean shouldQueue =
          currentExecutionsForPriority >= highPriorityAllowance && lowPriorityAllowance <= otherPriorityExecutions;
      return PlanExecutionSettingResponse.builder()
          .shouldQueue(shouldQueue)
          .useNewFlow(true)
          .priorityExecutionLimitReached(shouldQueue)
          .build();
    }

    // currentExecutionsForPriority : Refers to LOW Priority Executions
    // For low priority, use min of limit and max concurrency
    long lowPriorityAllowance = Math.min(priorityExecutionsLimit, maxAccountConcurrency);
    // Queue if at low priority limit
    boolean shouldQueue = currentExecutionsForPriority >= lowPriorityAllowance;
    return PlanExecutionSettingResponse.builder()
        .shouldQueue(shouldQueue)
        .useNewFlow(true)
        .priorityExecutionLimitReached(shouldQueue)
        .build();
  }

  /**
   * Handles queueing logic when execution priority does NOT match defined priority.
   * For HIGH priority defined (current is LOW):
   * - Queue if remaining capacity after high priority <= current low priority executions
   * For LOW priority defined (current is HIGH):
   * - Queue if at low priority limit AND high priority allowance <= other executions
   */
  private PlanExecutionSettingResponse handleNonMatchingPriorityExecution(boolean isHighPriorityDefined,
      long maxAccountConcurrency, int priorityExecutionsLimit, long currentExecutionsForPriority,
      long otherPriorityExecutions) {
    if (!isHighPriorityDefined) {
      // Current execution is high priority
      // Low priority gets its defined limit
      long lowPriorityAllowance = Math.min(priorityExecutionsLimit, maxAccountConcurrency);
      // High priority can use remaining capacity
      long highPriorityAllowance = Math.max(maxAccountConcurrency - lowPriorityAllowance, 0);
      // Queue if at low priority limit AND high priority allowance is used up
      boolean shouldQueue =
          currentExecutionsForPriority >= lowPriorityAllowance && highPriorityAllowance <= otherPriorityExecutions;
      return PlanExecutionSettingResponse.builder()
          .shouldQueue(shouldQueue)
          .useNewFlow(true)
          .priorityExecutionLimitReached(shouldQueue)
          .build();
    }

    // Current execution is low priority, Defined Priority is High
    // Low priority gets remaining capacity after high priority
    long lowPriorityAllowance = Math.max(maxAccountConcurrency - priorityExecutionsLimit, 0);
    // Queue if other (high priority) executions are using the low priority allowance
    boolean shouldQueue = otherPriorityExecutions >= lowPriorityAllowance;
    return PlanExecutionSettingResponse.builder()
        .shouldQueue(shouldQueue)
        .useNewFlow(true)
        .priorityExecutionLimitReached(shouldQueue)
        .build();
  }

  @Override
  public int getMaxConcurrencyBasedOnEdition(String accountIdentifier, long childCount) {
    try {
      Edition edition = getEdition(accountIdentifier);
      EditionBasedAccountConfigStrategy strategy = editionBasedAccountConfigStrategyFactory.getStrategy(edition);
      long maxParallelismStopRestriction = strategy.getMaxParallelismStopRestriction();
      if (childCount > maxParallelismStopRestriction) {
        throw new InvalidRequestException(getConcurrencyLimitExceededMessage(maxParallelismStopRestriction, edition));
      }
      /*
         Check if the child count is less than the configured limit.
         If it is, return the configured value.
         Otherwise, return the overridden account-specific limit, if available.
      */
      if (childCount < strategy.getStepOrStageMaxConcurrency()) {
        return strategy.getStepOrStageMaxConcurrency();
      }
      return strategy.getStepOrStageMaxConcurrency(accountIdentifier);
    } catch (ExecutionException ex) {
      log.warn(String.format("Failed to get max concurrency execution count for account {%s} with error message: ",
                   accountIdentifier),
          ex);
      return DEFAULT_MAX_CONCURRENCY_ENTERPRISE;
    }
  }

  @Override
  public int getMaxStepConcurrency(String accountIdentifier) {
    try {
      Edition edition = getEdition(accountIdentifier);
      EditionBasedAccountConfigStrategy strategy = editionBasedAccountConfigStrategyFactory.getStrategy(edition);
      return strategy.getStepOrStageMaxConcurrency(accountIdentifier);
    } catch (ExecutionException ex) {
      log.warn(String.format("Failed to get max step concurrency for account {%s}: ", accountIdentifier), ex);
      return DEFAULT_MAX_CONCURRENCY_ENTERPRISE;
    }
  }

  @Override
  public int getMaxLeafStepConcurrency(String accountIdentifier) {
    try {
      Optional<Integer> override = pipelineRetentionService.getMaxLeafStepConcurrency(accountIdentifier);
      if (override.isPresent() && override.get() != null) {
        return override.get();
      }
    } catch (Exception ex) {
      log.warn(String.format("Failed to fetch per-account max leaf step concurrency override for {%s}, "
                       + "falling back to config default",
                   accountIdentifier),
          ex);
    }
    return defaultMaxLeafStepConcurrency == null ? 0 : defaultMaxLeafStepConcurrency;
  }

  @Override
  public String getAccountEdition(String accountIdentifier) {
    try {
      Edition edition = getEdition(accountIdentifier);
      return edition.toString();
    } catch (Exception e) {
      // do nothing
    }
    return ENTERPRISE.name();
  }

  @Override
  public long getMaxInputParameterSize(String accountIdentifier) {
    try {
      Edition edition = getEdition(accountIdentifier);
      return editionBasedAccountConfigStrategyFactory.getStrategy(edition).getMaxInputParameterSize(accountIdentifier);
    } catch (Exception ex) {
      log.warn(String.format(
                   "Failed to get max input parameter size for account {%s} with error message: ", accountIdentifier),
          ex);
    }
    return DEFAULT_MAX_INPUT_PARAMETER_SIZE_IN_BYTES;
  }

  @Override
  public long getCurrentExecutionCount(String accountIdentifier) {
    try {
      return planExecutionService.countRunningExecutionsForGivenPipelineInAccount(accountIdentifier);
    } catch (Exception ex) {
      log.warn(String.format(
                   "Failed to get current execution count for account {%s} with error message: ", accountIdentifier),
          ex);
      return DEFAULT_NO_LIMIT;
    }
  }

  @Override
  public long getMaxConcurrency(String accountIdentifier) {
    Long ngConcurrencyLimit = null;
    if (featureFlagService.isEnabled(accountIdentifier, PIE_PIPELINE_SETTINGS_ENFORCEMENT_LIMIT.name())) {
      try {
        ngConcurrencyLimit =
            Long.parseLong(NGRestUtils
                               .getResponse(ngSettingsClient.getSetting(
                                   NGPipelineSettingsConstant.CONCURRENT_ACTIVE_PIPELINE_EXECUTIONS.getName(),
                                   accountIdentifier, null, null))
                               .getValue());
      } catch (Exception exception) {
        log.error("Failed to get \"CONCURRENT_ACTIVE_PIPELINE_EXECUTIONS\" settings : ", exception);
      }
    }
    try {
      Edition edition = getEdition(accountIdentifier);
      return editionBasedAccountConfigStrategyFactory.getStrategy(edition).getPipelineLevelMaxConcurrency(
          accountIdentifier, ngConcurrencyLimit);
    } catch (Exception ex) {
      log.warn(
          String.format("Failed to get max concurrency for account {%s} with error message: ", accountIdentifier), ex);
      // Todo: This is a hard enforced limit, we will change the behaviour after monitoring the log, and exception cases
      return ngConcurrencyLimit != null ? ngConcurrencyLimit : Long.MAX_VALUE;
    }
  }

  @Override
  public PriorityConcurrentExecutionsMetadata getPriorityExecutionPreferences(String accountIdentifier) {
    List<SettingResponseDTO> concurrencySettings = new ArrayList<>();
    String executionPriorityType = null;
    String priorityProjectString = null;
    Integer priorityConcurrentExecutionLimit = null;
    List<PriorityProjects> projectsList = null;
    if (featureFlagService.isEnabled(accountIdentifier, PIPE_PROJECT_LEVEL_EXECUTION_CONCURRENCY.name())) {
      try {
        concurrencySettings = NGRestUtils.getResponse(ngSettingsClient.listSettings(accountIdentifier, null, null,
            SettingCategory.PMS, NGPipelineSettingsConstant.PRIORITY_EXECUTION_CONCURRENCY_PREFERENCES.getName()));
      } catch (Exception exception) {
        log.error("Failed to get \"PRIORITY_EXECUTION_CONCURRENCY_PREFERENCES\" settings : ", exception);
      }
      if (EmptyPredicate.isNotEmpty(concurrencySettings)) {
        // Fetch Priority Concurrent Execution Limit
        Optional<SettingResponseDTO> executionLimitSetting =
            concurrencySettings.stream()
                .filter(
                    s -> s.getSetting().getIdentifier().equals(PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_LIMIT.getName()))
                .findFirst();
        if (executionLimitSetting.isPresent()) {
          String priorityConcurrentExecutionLimitString = executionLimitSetting.get().getSetting().getValue();
          priorityConcurrentExecutionLimit = Integer.parseInt(priorityConcurrentExecutionLimitString);
        }

        // Fetch Concurrent Execution Priority Type
        Optional<SettingResponseDTO> executionPrioritySetting =
            concurrencySettings.stream()
                .filter(
                    s -> s.getSetting().getIdentifier().equals(PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_TYPE.getName()))
                .findFirst();
        if (executionPrioritySetting.isPresent()) {
          executionPriorityType = executionPrioritySetting.get().getSetting().getValue();
        }

        // Fetch Priority Execution Projects
        Optional<SettingResponseDTO> priorityProjectsSetting =
            concurrencySettings.stream()
                .filter(s
                    -> s.getSetting().getIdentifier().equals(
                        PRIORITY_EXECUTION_CONCURRENCY_PRIORITY_PROJECTS.getName()))
                .findFirst();
        if (priorityProjectsSetting.isPresent()) {
          priorityProjectString = priorityProjectsSetting.get().getSetting().getValue();
        }
        if (EmptyPredicate.isEmpty(priorityProjectString)) {
          priorityProjectString = "[]";
        }
        try {
          projectsList = YamlUtils.read(priorityProjectString, new TypeReference<List<PriorityProjects>>() {});
        } catch (IOException e) {
          log.error("Unable to parse Projects list from Account level Settings");
        }
      }
    }
    return PriorityConcurrentExecutionsMetadata.builder()
        .priorityConcurrentExecutionsLimit(priorityConcurrentExecutionLimit)
        .priorityProjectsList(projectsList)
        .priorityType(executionPriorityType)
        .build();
  }

  @Override
  public long getMaxOutcomeSize(String accountIdentifier) {
    try {
      Edition edition = getEdition(accountIdentifier);
      return editionBasedAccountConfigStrategyFactory.getStrategy(edition).getMaxOutcomeSize(accountIdentifier);
    } catch (Exception ex) {
      log.warn(
          String.format("Failed to get max outcome size for account {%s} with error message: ", accountIdentifier), ex);
    }
    return DEFAULT_MAX_OUTCOME_RESPONSE_SIZE_IN_BYTES;
  }

  @Override
  public boolean isStepInputSizeWithinLimit(String accountIdentifier, String inputParameters) {
    long maxInputSizeLimitLimit = getMaxInputParameterSize(accountIdentifier);
    return maxInputSizeLimitLimit > inputParameters.length();
  }

  @Override
  public boolean isOutcomeResponseWithinLimit(String accountIdentifier, String outcomeResponse) {
    if (EmptyPredicate.isEmpty(outcomeResponse)
        || (outcomeResponse.length() <= getMaxOutcomeSizeByEdition(accountIdentifier))) {
      return true;
    }
    long maxOutcomeResponseSizeLimit = getMaxOutcomeSize(accountIdentifier);
    return maxOutcomeResponseSizeLimit > outcomeResponse.length();
  }

  @Override
  public int getMaxQueuedExecutionLimit(String accountIdentifier) {
    try {
      Edition edition = getEdition(accountIdentifier);
      return editionBasedAccountConfigStrategyFactory.getStrategy(edition).getMaxQueuedExecutionLimit(
          accountIdentifier);
    } catch (Exception ex) {
      log.warn(String.format("Failed to get max allowed queued executions for account {%s} with error message: ",
                   accountIdentifier),
          ex);
    }
    return DEFAULT_MAX_QUEUED_EXECUTIONS;
  }

  @Override
  public boolean isQueuedExecutionsWithinLimit(String accountIdentifier) {
    long currentQueuedCount = countCurrentQueuedExecutionInternal(accountIdentifier);
    // Check if the currentCount is less than default values, avoid fetching overridden data from the cache
    if (currentQueuedCount <= getMaxQueuedExecutionLimitByEdition(accountIdentifier)) {
      return true;
    }
    long maxQueuedExecutionLimit = getMaxQueuedExecutionLimit(accountIdentifier);
    return maxQueuedExecutionLimit > currentQueuedCount;
  }

  @Override
  public int getMaxTriggerCreationLimit(String accountIdentifier) {
    try {
      Edition edition = getEdition(accountIdentifier);
      return editionBasedAccountConfigStrategyFactory.getStrategy(edition).getMaxTriggerCreationLimit(
          accountIdentifier);
    } catch (Exception ex) {
      log.warn(String.format("Failed to get max allowed trigger creation limit for account {%s} with error message: ",
                   accountIdentifier),
          ex);
    }
    return DEFAULT_MAX_TRIGGER_CREATION_LIMIT;
  }

  @Override
  public boolean isTriggerCreationWithinLimit(String accountIdentifier, long currentTriggerCount) {
    if (currentTriggerCount <= getMaxTriggerCreationLimitByEdition(accountIdentifier)) {
      return true;
    }
    long maxTriggerCreationLimit = getMaxTriggerCreationLimit(accountIdentifier);
    return currentTriggerCount <= maxTriggerCreationLimit;
  }

  public boolean isPipelineCreationWithinLimit(String accountIdentifier, long currentPipelineCount) {
    if (currentPipelineCount <= getMaxPipelineCreationLimitByEdition(accountIdentifier)) {
      return true;
    }
    long maxPipelineCreationLimit = getMaxPipelineCreationCount(accountIdentifier);
    return maxPipelineCreationLimit > currentPipelineCount;
  }

  @Override
  public long getMaxFileSizeLimit(String accountIdentifier) {
    try {
      Edition edition = getEdition(accountIdentifier);
      return editionBasedAccountConfigStrategyFactory.getStrategy(edition).getMaxFileSizeLimit(accountIdentifier);
    } catch (Exception ex) {
      log.warn(String.format("Failed to get max allowed file size limit for account {%s} with error message: ",
                   accountIdentifier),
          ex);
    }
    return DEFAULT_MAX_FILE_SIZE_LIMIT;
  }

  @Override
  public long getMaxPayloadSize(String accountIdentifier) {
    try {
      Edition edition = getEdition(accountIdentifier);
      return editionBasedAccountConfigStrategyFactory.getStrategy(edition).getPayloadSizeLimit(accountIdentifier);
    } catch (Exception ex) {
      log.warn(String.format(
                   "Failed to get max payload size limit for account {%s} with error message: ", accountIdentifier),
          ex);
    }
    return DEFAULT_MAX_PAYLOAD_SIZE_LIMIT;
  }

  @Override
  public boolean isFileSizeWithinLimit(String accountIdentifier, long currentFileSize) {
    if (currentFileSize <= getMaxFileSizeLimitByEdition(accountIdentifier)) {
      return true;
    }
    long maxFileSizeLimit = getMaxFileSizeLimit(accountIdentifier);
    return currentFileSize <= maxFileSizeLimit;
  }

  @Override
  public boolean isPayloadSizeWithinLimit(String accountIdentifier, long currPayloadSize) {
    if (currPayloadSize <= getPayloadSizeLimitByEdition(accountIdentifier)) {
      return true;
    }
    long maxFileSizeLimit = getMaxPayloadSize(accountIdentifier);
    return currPayloadSize <= maxFileSizeLimit;
  }

  @Override
  public PlanExecutionConcurrencyMode getConcurrencyMode(String accountIdentifier) {
    try {
      String value = NGRestUtils
                         .getResponse(ngSettingsClient.getSetting(
                             NGPipelineSettingsConstant.PIPELINE_EXECUTION_CONCURRENCY_MODE.getName(),
                             accountIdentifier, null, null))
                         .getValue();
      return PlanExecutionConcurrencyMode.fromSettingValue(value);
    } catch (Exception ex) {
      log.warn("Failed to read pipeline_execution_concurrency_mode for account {}; defaulting to PARTITIONS",
          accountIdentifier, ex);
      return PlanExecutionConcurrencyMode.PARTITIONS;
    }
  }

  @Override
  public int getEffectiveProjectConcurrency(String accountId, String parentUniqueId) {
    // Project-scoped override first (shown on the project settings page, account-admin editable).
    // Read via getSettingV2 keyed by the project's stable uniqueId (parentUniqueId) so the cap
    // lookup — like the counter — is resolved by identity and survives project-move-across-orgs.
    if (EmptyPredicate.isNotEmpty(parentUniqueId)) {
      try {
        String overrideValue = NGRestUtils
                                   .getResponse(ngSettingsClient.getSettingV2(
                                       NGPipelineSettingsConstant.PROJECT_EXECUTION_CONCURRENCY_LIMIT.getName(),
                                       accountId, parentUniqueId))
                                   .getValue();
        if (EmptyPredicate.isNotEmpty(overrideValue)) {
          return Integer.parseInt(overrideValue);
        }
      } catch (Exception ex) {
        log.warn("Failed to read project_execution_concurrency_limit for account {} parentUniqueId {}; "
                + "falling back to account default",
            accountId, parentUniqueId, ex);
      }
    }
    // Account-scoped default.
    try {
      String defaultValue =
          NGRestUtils
              .getResponse(ngSettingsClient.getSetting(
                  NGPipelineSettingsConstant.DEFAULT_PROJECT_EXECUTION_CONCURRENCY.getName(), accountId, null, null))
              .getValue();
      if (EmptyPredicate.isNotEmpty(defaultValue)) {
        return Integer.parseInt(defaultValue);
      }
    } catch (Exception ex) {
      log.warn("Failed to read default_project_execution_concurrency for account {}; treating as no per-project cap",
          accountId, ex);
    }
    return 0;
  }

  @Override
  public PriorityType getPriorityTypeOfCurrentExecution(
      String accountId, String orgId, String projectId, boolean priorityExecutionsFFEnabled) {
    if (!priorityExecutionsFFEnabled) {
      return PriorityType.NORMAL;
    }

    PriorityConcurrentExecutionsMetadata metadata = getPriorityExecutionPreferences(accountId);
    if (EmptyPredicate.isEmpty(metadata.getPriorityProjectsList())) {
      return PriorityType.NORMAL;
    }

    return getPriorityTypeForProject(orgId, projectId, metadata);
  }

  private PriorityType getPriorityTypeForProject(
      String orgId, String projectId, PriorityConcurrentExecutionsMetadata metadata) {
    boolean isGivenPriorityProject = isProjectInPriorityList(orgId, projectId, metadata.getPriorityProjectsList());
    String priorityType = metadata.getPriorityType();

    if (YAMLFieldNameConstants.HIGH_PRIORITY.equals(priorityType)) {
      return isGivenPriorityProject ? PriorityType.HIGH : PriorityType.LOW;
    } else if (YAMLFieldNameConstants.LOW_PRIORITY.equals(priorityType)) {
      return isGivenPriorityProject ? PriorityType.LOW : PriorityType.HIGH;
    }

    return PriorityType.NORMAL;
  }

  private boolean isProjectInPriorityList(String orgId, String projectId, List<PriorityProjects> projects) {
    String projectPath = getProjectPath(orgId, projectId);
    return projects.stream()
        .map(PriorityProjects::getFqn)
        .filter(Objects::nonNull)
        .anyMatch(fqn -> fqn.equals(projectPath));
  }

  private String getProjectPath(String orgId, String projectId) {
    String orgIdentifier = Optional.ofNullable(orgId).orElse("");
    String projectIdentifier = Optional.ofNullable(projectId).orElse("");
    return String.join("/", orgIdentifier, projectIdentifier);
  }

  private String getConcurrencyLimitExceededMessage(long maxParallelismStopRestriction, Edition edition) {
    String errorMessage =
        String.format("You are attempting to run more than %s concurrent stages or steps, which exceeds the current "
                + "limit. To learn more about this limitation, please contact Harness Support or visit: %s",
            maxParallelismStopRestriction, SETTINGS_HARNESS_DEVELOPER_DOC_URL);
    if (edition != ENTERPRISE) {
      errorMessage = String.format("You are attempting to run more than %s concurrent stages or steps, which exceeds "
              + "the current limit. Please upgrade your plan to Team/Enterprise (Paid) or reduce "
              + "concurrent steps or stages. For more details on concurrency limits, visit: %s",
          maxParallelismStopRestriction, SETTINGS_HARNESS_DEVELOPER_DOC_URL);
    }
    return errorMessage;
  }

  public List<ModuleLicenseDTO> getModuleLicense(String accountIdentifier) throws ExecutionException {
    return moduleLicensesCache.get(accountIdentifier);
  }
}
