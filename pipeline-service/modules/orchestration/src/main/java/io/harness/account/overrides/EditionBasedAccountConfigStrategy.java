/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.account.overrides;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.pms.accountoverrides.ExpressionCallType;

@OwnedBy(HarnessTeam.PIPELINE)
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
public interface EditionBasedAccountConfigStrategy {
  /*
   * This method retrieves the maximum pipelines that can be created per account, based on the account's edition.
   * This fetches and returns the default limits.
   * */
  int getMaxPipelineCreationLimit();

  /*
   * This method retrieves the maximum pipelines that can be created per account, based on the account's edition.
   * It first checks for any overrides specific to the account and returns those limits if available, else falls back to
   * returning the default limits.
   * */
  int getMaxPipelineCreationLimit(String accountIdentifier);

  /*
   * This method retrieves the maximum concurrency limit allowed per account, based on the account's edition.
   * It first checks for any overrides specific to the account and returns those limits if available, else falls back to
   * returning the default limits.
   * */
  long getPipelineLevelMaxConcurrency(String accountIdentifier, Long ngConcurrencyLimit);

  /*
   * This method retrieves the maximum input parameter size allowed per account, based on the account's edition.
   * Input size means the resolved parameter size which is passed into redis.
   * It first checks for any overrides specific to the account and returns those limits if available, else falls back to
   * returning the default limits.
   * */
  long getMaxInputParameterSize(String accountIdentifier);

  /*
   * This method retrieves the maximum concurrency limit allowed per account, based on the account's
   * edition.
   * This fetches and returns the default limits.
   * */
  int getStepOrStageMaxConcurrency();

  /*
   * This method retrieves the maximum outcome response size (in strategy) allowed per account, based on the account's
   * edition.
   * It first checks for any overrides specific to the account and returns those limits if available, else falls back to
   * returning the default concurrency limits.
   * */
  long getMaxOutcomeSize(String accountIdentifier);

  /*
   * This method retrieves the maximum outcome response size allowed per account, based on the account's
   * edition.
   * This fetches and returns the default limits.
   * */
  long getMaxOutcomeSize();

  /*
   * This method retrieves the maximum concurrent steps or stages which allowed per account, based on the account's
   * edition.
   * This fetches and returns the default limits.
   * */
  long getMaxParallelismStopRestriction();

  /*
   * This method retrieves the maximum queued executions allowed per account at a given time, based on the account's
   * edition.
   * It first checks for any overrides specific to the account and returns those limits if available, else falls back to
   * returning the default limits.
   * */
  int getMaxQueuedExecutionLimit(String accountIdentifier);

  /*
   * This method retrieves the maximum queued executions allowed per account at a given time, based on the account's
   * edition.
   * This fetches and returns the default limits.
   * */
  int getMaxQueuedExecutionLimit();

  /*
   * This method retrieves the maximum trigger creations allowed per account at a given time, based on the account's
   * edition.
   * It first checks for any overrides specific to the account and returns those limits if available, else falls back to
   * returning the default limits.
   */
  int getMaxTriggerCreationLimit(String accountIdentifier);

  /*
   * This method retrieves the maximum trigger creations allowed per account at a given time, based on the account's
   * edition.
   * This fetches and returns the default limits.
   * */
  int getMaxTriggerCreationLimit();

  /*
   * This method retrieves the maximum file size allowed per account at a given time, based on the account's
   * edition.
   * It first checks for any overrides specific to the account and returns those limits if available, else falls back to
   * returning the default limits.
   */
  long getMaxFileSizeLimit(String accountIdentifier);

  /*
   * This method retrieves the custom webhook payload size allowed per account at a given time, based on the account's
   * edition.
   * It first checks for any overrides specific to the account and returns those limits if available, else falls back to
   * returning the default limits.
   * */
  long getPayloadSizeLimit(String accountIdentifier);

  /*
   * This method retrieves the maximum file size allowed per account at a given time, based on the account's
   * edition.
   * This fetches and returns the default limits.
   * */
  long getMaxFileSizeLimit();

  /*
   * This method retrieves the maximum custom webhook payload size allowed per account at a given time, based on the
   * account's edition. This fetches and returns the default limits.
   */
  long getPayloadSizeLimit();

  /*
   * This method retrieves the maximum concurrent steps in execution allowed per account, based on the account's
   * edition. It first checks for any overrides specific to the account and returns those limits if available, else
   * falls back to returning the default limits.
   * */
  int getStepOrStageMaxConcurrency(String accountIdentifier);

  /*
   * This method retrieves the per-node call budget of the given type (e.g. MONGO) for expression resolution allowed per
   * account, based on the account's edition. It first checks for any overrides specific to the account and returns
   * those limits if available, else falls back to returning the default limits.
   * */
  int getMaxExpressionCalls(String accountIdentifier, ExpressionCallType callType);
}
