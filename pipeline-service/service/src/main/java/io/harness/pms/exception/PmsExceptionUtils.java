/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.exception;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.HarnessStringUtils;
import io.harness.engine.governance.PolicyEvaluationFailureException;
import io.harness.eraro.ErrorCode;
import io.harness.exception.FilterCreatorException;
import io.harness.exception.PlanCreatorException;
import io.harness.exception.bean.FilterCreatorErrorResponse;
import io.harness.opa.gitx.OpaGovernanceMetadataCodec;
import io.harness.opa.gitx.OpaOnSaveStatusDTO;
import io.harness.pms.contracts.plan.Dependencies;
import io.harness.pms.contracts.plan.ErrorMetadata;
import io.harness.pms.contracts.plan.ErrorResponse;
import io.harness.pms.contracts.plan.ErrorResponseV2;
import io.harness.pms.contracts.plan.YamlFieldBlob;
import io.harness.pms.yaml.YamlField;
import io.harness.serializer.JsonUtils;

import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
@Slf4j
public class PmsExceptionUtils {
  public String getUnresolvedDependencyPathsErrorMessage(Dependencies dependencies) {
    return String.format(
        "Following yaml paths could not be parsed: %s", String.join(",", dependencies.getDependenciesMap().values()));
  }

  @VisibleForTesting
  List<YamlNodeErrorInfo> getYamlNodeErrorInfo(Collection<YamlFieldBlob> yamlFieldBlobs) throws IOException {
    List<YamlNodeErrorInfo> yamlNodeErrorInfos = new ArrayList<>();
    for (YamlFieldBlob yamlFieldBlob : yamlFieldBlobs) {
      YamlField yamlField = YamlField.fromFieldBlob(yamlFieldBlob);
      yamlNodeErrorInfos.add(YamlNodeErrorInfo.fromField(yamlField));
    }
    return yamlNodeErrorInfos;
  }

  public void checkAndThrowFilterCreatorException(
      List<ErrorResponse> errorResponses, List<ErrorResponseV2> errorResponsesV2, List<String> errorModules) {
    if (EmptyPredicate.isEmpty(errorResponses) && EmptyPredicate.isEmpty(errorResponsesV2)) {
      return;
    }
    List<String> messages = new ArrayList<>();
    FilterCreatorErrorResponse filterCreatorErrorResponse = FilterCreatorErrorResponse.builder().build();
    if (isNotEmpty(errorResponsesV2)) {
      for (ErrorResponseV2 errorResponseV2 : errorResponsesV2) {
        for (ErrorMetadata errorMetadata : errorResponseV2.getErrorsList()) {
          ErrorCode wingsErrorCode;
          try {
            wingsErrorCode = ErrorCode.valueOf(errorMetadata.getWingsExceptionErrorCode());
          } catch (Exception ex) {
            wingsErrorCode = ErrorCode.GENERAL_ERROR;
          }
          filterCreatorErrorResponse.addErrorMetadata(io.harness.exception.bean.ErrorMetadata.builder()
                                                          .errorCode(wingsErrorCode)
                                                          .errorMessage(errorMetadata.getErrorMessage())
                                                          .build());
          messages.add(errorMetadata.getErrorMessage());
        }
      }
    }
    if (isNotEmpty(errorResponses)) {
      messages.addAll(
          errorResponses.stream().flatMap(resp -> resp.getMessagesList().stream()).collect(Collectors.toList()));
    }
    throw new FilterCreatorException(HarnessStringUtils.join(",", messages), filterCreatorErrorResponse,
        isNotEmpty(errorModules) ? new HashSet<>(errorModules) : new HashSet<>());
  }

  public void checkAndThrowPlanCreatorException(List<ErrorResponse> errorResponses, List<String> errorModules) {
    if (EmptyPredicate.isEmpty(errorResponses)) {
      return;
    }
    for (ErrorResponse errorResponse : errorResponses) {
      if (isNotEmpty(errorResponse.getOpaOnSaveStatusJson())) {
        OpaOnSaveStatusDTO opaStatus = reconstructOpaOnSaveStatus(errorResponse);
        if (opaStatus != null) {
          String message = isNotEmpty(errorResponse.getMessagesList())
              ? HarnessStringUtils.join(",", errorResponse.getMessagesList())
              : "Execution blocked by governance policies.";
          throw new PolicyEvaluationFailureException(String.format("Error creating Plan: %s", message), opaStatus);
        }
      }
    }
    List<String> messages =
        errorResponses.stream().flatMap(resp -> resp.getMessagesList().stream()).collect(Collectors.toList());
    throw new PlanCreatorException(String.format("Error creating Plan: %s", HarnessStringUtils.join(",", messages)),
        isNotEmpty(errorModules) ? new HashSet<>(errorModules) : new HashSet<>());
  }

  private OpaOnSaveStatusDTO reconstructOpaOnSaveStatus(ErrorResponse errorResponse) {
    try {
      OpaOnSaveStatusDTO withoutGm =
          JsonUtils.asObject(errorResponse.getOpaOnSaveStatusJson(), OpaOnSaveStatusDTO.class);
      if (withoutGm == null) {
        return null;
      }
      return OpaOnSaveStatusDTO.builder()
          .status(withoutGm.getStatus())
          .repoURL(withoutGm.getRepoURL())
          .filePath(withoutGm.getFilePath())
          .evaluatedAtCommitId(withoutGm.getEvaluatedAtCommitId())
          .lastValidCommitId(withoutGm.getLastValidCommitId())
          .evaluatedAt(withoutGm.getEvaluatedAt())
          .message(withoutGm.getMessage())
          .governanceMetadata(OpaGovernanceMetadataCodec.fromBytes(errorResponse.getGovernanceMetadata().isEmpty()
                  ? null
                  : errorResponse.getGovernanceMetadata().toByteArray()))
          .build();
    } catch (Exception e) {
      log.warn("Failed to reconstruct OpaOnSaveStatusDTO from plan-creation ErrorResponse", e);
      return null;
    }
  }
}
