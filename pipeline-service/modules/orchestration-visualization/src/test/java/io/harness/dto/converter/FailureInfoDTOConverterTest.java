/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dto.converter;

import static io.harness.pms.contracts.execution.failure.FailureSubType.UNKNOWN_FAILURE_REASON;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.OrchestrationVisualizationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dto.FailureInfoDTO;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.pms.contracts.execution.failure.FailureData;
import io.harness.pms.contracts.execution.failure.FailureInfo;
import io.harness.pms.contracts.execution.failure.FailureType;
import io.harness.pms.contracts.execution.failure.FailureTypeInfo;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableList;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.PIPELINE)
public class FailureInfoDTOConverterTest extends OrchestrationVisualizationTestBase {
  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void toFailureInfoDTOWhenInfoIsNUll() {
    assertThat(FailureInfoDTOConverter.toFailureInfoDTO(null)).isNull();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void toFailureInfoDTO() {
    String errorMessage = "This is error message";
    String errorCode = "code";
    FailureInfo failureInfo = FailureInfo.newBuilder()
                                  .setErrorMessage(errorMessage)
                                  .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                  .addFailureData(FailureData.newBuilder()
                                                      .setCode(ErrorCode.ACCESS_DENIED.name())
                                                      .setLevel(Level.ERROR.name())
                                                      .setMessage("message")
                                                      .addFailureTypes(FailureType.CONNECTIVITY_FAILURE)
                                                      .build())
                                  .build();

    FailureInfoDTO failureInfoDTO = FailureInfoDTO.builder()
                                        .message(errorMessage)
                                        .failureTypeList(EnumSet.of(io.harness.exception.FailureType.APPLICATION_ERROR))
                                        .responseMessages(ImmutableList.of(
                                            ResponseMessage.builder()
                                                .code(ErrorCode.ACCESS_DENIED)
                                                .level(Level.ERROR)
                                                .failureTypes(EnumSet.of(io.harness.exception.FailureType.CONNECTIVITY))
                                                .message("message")
                                                .build()))
                                        .build();

    assertThat(FailureInfoDTOConverter.toFailureInfoDTO(failureInfo)).isEqualTo(failureInfoDTO);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void toFailureInfoWhenDtoIsNull() {
    assertThat(FailureInfoDTOConverter.toFailureInfo(null)).isNull();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void toFailureInfoPreservesFailureTypeListAndResponseMessages() {
    String errorMessage = "Pipeline failed at Deploy stage";
    FailureInfoDTO failureInfoDTO = FailureInfoDTO.builder()
                                        .message(errorMessage)
                                        .failureTypeList(EnumSet.of(io.harness.exception.FailureType.APPLICATION_ERROR))
                                        .responseMessages(ImmutableList.of(
                                            ResponseMessage.builder()
                                                .code(ErrorCode.ACCESS_DENIED)
                                                .level(Level.ERROR)
                                                .failureTypes(EnumSet.of(io.harness.exception.FailureType.CONNECTIVITY))
                                                .message("connectivity issue")
                                                .build()))
                                        .build();

    FailureInfo failureInfo = FailureInfoDTOConverter.toFailureInfo(failureInfoDTO);

    assertThat(failureInfo.getErrorMessage()).isEqualTo(errorMessage);
    assertThat(failureInfo.getFailureTypesList()).containsExactly(FailureType.APPLICATION_FAILURE);
    assertThat(failureInfo.getFailureDataList()).hasSize(1);
    assertThat(failureInfo.getFailureDataList().get(0).getMessage()).isEqualTo("connectivity issue");
    assertThat(failureInfo.getFailureDataList().get(0).getFailureTypesList())
        .containsExactly(FailureType.CONNECTIVITY_FAILURE);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void toFailureInfoRoundTripFromProto() {
    FailureInfo failureInfo = FailureInfo.newBuilder()
                                  .setErrorMessage("This is error message")
                                  .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                  .addFailureData(FailureData.newBuilder()
                                                      .setCode(ErrorCode.ACCESS_DENIED.name())
                                                      .setLevel(Level.ERROR.name())
                                                      .setMessage("message")
                                                      .addFailureTypes(FailureType.CONNECTIVITY_FAILURE)
                                                      .build())
                                  .build();

    FailureInfoDTO failureInfoDTO = FailureInfoDTOConverter.toFailureInfoDTO(failureInfo);
    FailureInfo roundTripped = FailureInfoDTOConverter.toFailureInfo(failureInfoDTO);

    assertThat(roundTripped.getErrorMessage()).isEqualTo(failureInfo.getErrorMessage());
    assertThat(roundTripped.getFailureTypesList()).isEqualTo(failureInfo.getFailureTypesList());
    assertThat(roundTripped.getFailureDataList()).hasSameSizeAs(failureInfo.getFailureDataList());
    assertThat(roundTripped.getFailureDataList().get(0).getMessage())
        .isEqualTo(failureInfo.getFailureDataList().get(0).getMessage());
    assertThat(roundTripped.getFailureDataList().get(0).getFailureTypesList())
        .isEqualTo(failureInfo.getFailureDataList().get(0).getFailureTypesList());
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void toFailureInfoDTOForEmptyErrorCode() {
    String errorMessage = "This is error message";
    String errorCode = "code";
    FailureInfo failureInfo = FailureInfo.newBuilder()
                                  .setErrorMessage(errorMessage)
                                  .addFailureTypes(FailureType.APPLICATION_FAILURE)
                                  .addFailureData(FailureData.newBuilder()
                                                      .setLevel(Level.ERROR.name())
                                                      .setMessage("message")
                                                      .addFailureTypes(FailureType.CONNECTIVITY_FAILURE)
                                                      .build())
                                  .build();

    FailureInfoDTO failureInfoDTO = FailureInfoDTO.builder()
                                        .message(errorMessage)
                                        .failureTypeList(EnumSet.of(io.harness.exception.FailureType.APPLICATION_ERROR))
                                        .responseMessages(ImmutableList.of(
                                            ResponseMessage.builder()
                                                .code(ErrorCode.DEFAULT_ERROR_CODE)
                                                .level(Level.ERROR)
                                                .failureTypes(EnumSet.of(io.harness.exception.FailureType.CONNECTIVITY))
                                                .message("message")
                                                .build()))
                                        .build();

    assertThat(FailureInfoDTOConverter.toFailureInfoDTO(failureInfo)).isEqualTo(failureInfoDTO);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void toFailureInfoDTOWithFailureTypeInfo() {
    String errorMessage = "Test for failureInfo conversion in case failureData has failureTypeInfo populated";
    FailureInfo failureInfo =
        FailureInfo.newBuilder()
            .setErrorMessage(errorMessage)
            .addFailureTypes(FailureType.APPLICATION_FAILURE)
            .addFailureData(
                FailureData.newBuilder()
                    .setCode(ErrorCode.DEFAULT_ERROR_CODE.name())
                    .setLevel(Level.ERROR.name())
                    .setMessage(errorMessage)
                    .addFailureTypes(FailureType.CONNECTIVITY_FAILURE)
                    .addAllFailureTypeInfos(
                        List.of(FailureTypeInfo.newBuilder().setFailureType(FailureType.CONNECTIVITY_FAILURE).build(),
                            FailureTypeInfo.newBuilder().setFailureType(FailureType.TIMEOUT_FAILURE).build()))
                    .build())
            .build();

    FailureInfoDTO failureInfoDTO = FailureInfoDTO.builder()
                                        .message(errorMessage)
                                        .failureTypeList(EnumSet.of(io.harness.exception.FailureType.APPLICATION_ERROR))
                                        .responseMessages(ImmutableList.of(
                                            ResponseMessage.builder()
                                                .code(ErrorCode.DEFAULT_ERROR_CODE)
                                                .level(Level.ERROR)
                                                .failureSubTypes(EnumSet.of(UNKNOWN_FAILURE_REASON))
                                                .failureTypes(EnumSet.of(io.harness.exception.FailureType.CONNECTIVITY,
                                                    io.harness.exception.FailureType.EXPIRED))
                                                .message(errorMessage)
                                                .build()))
                                        .build();

    assertThat(FailureInfoDTOConverter.toFailureInfoDTO(failureInfo)).isEqualTo(failureInfoDTO);
  }
}
