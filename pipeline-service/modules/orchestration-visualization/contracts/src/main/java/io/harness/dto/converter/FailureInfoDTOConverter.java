/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dto.converter;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.HarnessStringUtils.emptyIfNull;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dto.FailureInfoDTO;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureSubType;
import io.harness.pms.contracts.execution.failure.FailureTypeInfo;
import io.harness.pms.execution.utils.EngineExceptionUtils;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.PIPELINE)
public class FailureInfoDTOConverter {
  public static FailureInfo toFailureInfo(FailureInfoDTO failureInfoDTO) {
    if (failureInfoDTO == null) {
      return null;
    }

    FailureInfo.Builder builder = FailureInfo.newBuilder();
    if (isNotEmpty(failureInfoDTO.getMessage())) {
      builder.setErrorMessage(failureInfoDTO.getMessage());
    }
    if (isNotEmpty(failureInfoDTO.getFailureTypeList())) {
      builder.addAllFailureTypes(
          EngineExceptionUtils.transformToOrchestrationFailureTypes(failureInfoDTO.getFailureTypeList()));
    }
    if (isNotEmpty(failureInfoDTO.getResponseMessages())) {
      failureInfoDTO.getResponseMessages().forEach(
          responseMessage -> builder.addFailureData(toFailureData(responseMessage)));
    }
    return builder.build();
  }

  public static FailureInfoDTO toFailureInfoDTO(FailureInfo failureInfo) {
    if (failureInfo == null) {
      return null;
    }

    return FailureInfoDTO.builder()
        .message(failureInfo.getErrorMessage())
        .failureTypeList(EngineExceptionUtils.transformToWingsFailureTypes(failureInfo.getFailureTypesList()))
        .responseMessages(failureInfo.getFailureDataList()
                              .stream()
                              .map(fd -> {
                                Map<String, String> additionalInfo = new HashMap<>();
                                if (isNotEmpty(fd.getStepIdentifier())) {
                                  additionalInfo.put("stepIdentifier", fd.getStepIdentifier());
                                }
                                if (isNotEmpty(fd.getStageIdentifier())) {
                                  additionalInfo.put("stageIdentifier", fd.getStageIdentifier());
                                }
                                return ResponseMessage.builder()
                                    .code(getErrorCode(fd))
                                    .level(getErrorLevel(fd))
                                    .failureTypes(getResponseMessageFailureTypes(fd))
                                    .failureSubTypes(getResponseMessageFailureSubTypes(fd))
                                    .message(fd.getMessage())
                                    .additionalInfo(additionalInfo)
                                    .build();
                              })
                              .collect(Collectors.toList()))
        .build();
  }

  private static FailureData toFailureData(ResponseMessage responseMessage) {
    FailureData.Builder builder =
        FailureData.newBuilder()
            .setCode(responseMessage.getCode() != null ? responseMessage.getCode().name()
                                                       : ErrorCode.DEFAULT_ERROR_CODE.name())
            .setLevel(responseMessage.getLevel() != null ? responseMessage.getLevel().name() : Level.ERROR.name())
            .setMessage(emptyIfNull(responseMessage.getMessage()));
    if (isNotEmpty(responseMessage.getFailureTypes())) {
      builder.addAllFailureTypes(
          EngineExceptionUtils.transformToOrchestrationFailureTypes(responseMessage.getFailureTypes()));
    }
    if (isNotEmpty(responseMessage.getFailureTypes()) || isNotEmpty(responseMessage.getFailureSubTypes())) {
      builder.addAllFailureTypeInfos(EngineExceptionUtils.createFailureTypeInfos(
          EngineExceptionUtils.transformToOrchestrationFailureTypes(responseMessage.getFailureTypes()),
          responseMessage.getFailureSubTypes()));
    }
    if (isNotEmpty(responseMessage.getAdditionalInfo())) {
      if (responseMessage.getAdditionalInfo().containsKey("stepIdentifier")) {
        builder.setStepIdentifier(responseMessage.getAdditionalInfo().get("stepIdentifier"));
      }
      if (responseMessage.getAdditionalInfo().containsKey("stageIdentifier")) {
        builder.setStageIdentifier(responseMessage.getAdditionalInfo().get("stageIdentifier"));
      }
    }
    return builder.build();
  }

  private EnumSet<FailureSubType> getResponseMessageFailureSubTypes(FailureData failureData) {
    if (isNotEmpty(failureData.getFailureTypeInfosList())) {
      return EnumSet.copyOf(failureData.getFailureTypeInfosList()
                                .stream()
                                .map(FailureTypeInfo::getFailureSubType)
                                .collect(Collectors.toSet()));
    }
    return EnumSet.noneOf(FailureSubType.class);
  }

  private EnumSet<io.harness.exception.FailureType> getResponseMessageFailureTypes(FailureData failureData) {
    if (isNotEmpty(failureData.getFailureTypeInfosList())) {
      return EngineExceptionUtils.transformToWingsFailureTypes(
          failureData.getFailureTypeInfosList().stream().map(FailureTypeInfo::getFailureType).toList());
    }
    return EngineExceptionUtils.transformToWingsFailureTypes(failureData.getFailureTypesList());
  }

  private ErrorCode getErrorCode(FailureData failureData) {
    if (isEmpty(failureData.getCode())) {
      return ErrorCode.DEFAULT_ERROR_CODE;
    }
    return ErrorCode.valueOf(failureData.getCode());
  }

  private Level getErrorLevel(FailureData failureData) {
    if (isEmpty(failureData.getLevel())) {
      return Level.ERROR;
    }
    return Level.valueOf(failureData.getLevel());
  }
}
