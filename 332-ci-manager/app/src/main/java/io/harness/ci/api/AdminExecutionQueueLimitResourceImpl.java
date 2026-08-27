/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ci.api;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.util.Objects.isNull;

import io.harness.ModuleType;
import io.harness.accesscontrol.AccountIdentifier;
import io.harness.app.beans.entities.ExecutionQueueLimit;
import io.harness.ci.config.ExecutionLimitSpec;
import io.harness.ci.execution.execution.QueueExecutionUtils;
import io.harness.ci.pipeline.executions.beans.AdminExecutionQueueLimitResource;
import io.harness.ci.pipeline.executions.beans.ExecutionQueueLimitDTO;
import io.harness.exception.EntityNotFoundException;
import io.harness.repositories.ExecutionQueueLimitRepository;
import io.harness.rest.RestResponse;

import software.wings.security.annotations.AdminPortalAuth;

import com.google.inject.Inject;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@AdminPortalAuth
@Slf4j
public class AdminExecutionQueueLimitResourceImpl implements AdminExecutionQueueLimitResource {
  @Inject ExecutionQueueLimitRepository executionQueueLimitRepository;
  @Inject QueueExecutionUtils queueExecutionUtils;
  @Override
  public RestResponse<Boolean> updateExecutionLimits(
      @AccountIdentifier String accountIdentifier, ExecutionQueueLimitDTO executionQueueLimitDTO) {
    Optional<ExecutionQueueLimit> firstByAccountIdentifier =
        executionQueueLimitRepository.findFirstByAccountIdentifier(accountIdentifier);
    if (firstByAccountIdentifier.isPresent()) {
      executionQueueLimitRepository.deleteById(firstByAccountIdentifier.get().getUuid());
    }
    ExecutionQueueLimit build = ExecutionQueueLimit.builder()
                                    .macExecLimit(executionQueueLimitDTO.getMacExecutionLimits())
                                    .totalExecLimit(executionQueueLimitDTO.getTotalExecutionLimits())
                                    .accountIdentifier(accountIdentifier)
                                    .build();
    ExecutionQueueLimit save = executionQueueLimitRepository.save(build);
    return new RestResponse<>(true);
  }

  @Override
  public RestResponse<ExecutionQueueLimitDTO> getExecutionLimits(@AccountIdentifier String accountIdentifier) {
    Optional<ExecutionQueueLimit> firstByAccountIdentifier =
        executionQueueLimitRepository.findFirstByAccountIdentifier(accountIdentifier);
    if (firstByAccountIdentifier.isPresent()) {
      ExecutionQueueLimit executionQueueLimit = firstByAccountIdentifier.get();

      String macLimit = executionQueueLimit.getMacExecLimit();
      String totalLimit = executionQueueLimit.getTotalExecLimit();

      if (isEmpty(macLimit) || isEmpty(totalLimit)) {
        ExecutionLimitSpec defaultLimits =
            queueExecutionUtils.getDefaultLimitsBasedOnLicenseAndModuleType(accountIdentifier, ModuleType.CI.name());

        if (!isNull(defaultLimits)) {
          if (isEmpty(macLimit)) {
            macLimit = String.valueOf(defaultLimits.getDefaultMacExecutionCount());
          }
          if (isEmpty(totalLimit)) {
            totalLimit = String.valueOf(defaultLimits.getDefaultTotalExecutionCount());
          }
        }
      }

      return new RestResponse<>(
          ExecutionQueueLimitDTO.builder().macExecutionLimits(macLimit).totalExecutionLimits(totalLimit).build());
    } else {
      ExecutionLimitSpec defaultLimits =
          queueExecutionUtils.getDefaultLimitsBasedOnLicenseAndModuleType(accountIdentifier, ModuleType.CI.name());
      if (isNull(defaultLimits)) {
        throw new EntityNotFoundException(
            String.format("no execution config found for accountId: %s", accountIdentifier));
      }
      return new RestResponse<>(ExecutionQueueLimitDTO.builder()
                                    .macExecutionLimits(String.valueOf(defaultLimits.getDefaultMacExecutionCount()))
                                    .totalExecutionLimits(String.valueOf(defaultLimits.getDefaultTotalExecutionCount()))
                                    .build());
    }
  }
}
