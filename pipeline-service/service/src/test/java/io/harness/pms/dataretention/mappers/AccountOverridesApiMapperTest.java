/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.dataretention.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.entity.accountoverrides.beans.AccountOverridesConfigDTO;
import io.harness.entity.accountoverrides.beans.DataRetentionSettingsDTO;
import io.harness.entity.accountoverrides.beans.ExportSettingsDTO;
import io.harness.pms.accountoverrides.AccountOverridesCreateRequestDTO;
import io.harness.pms.accountoverrides.AccountOverridesCreateResponseDTO;
import io.harness.pms.accountoverrides.AccountOverridesUpdateRequestDTO;
import io.harness.pms.accountoverrides.AccountOverridesUpdateResponseDTO;
import io.harness.pms.accountoverrides.DataRetentionPeriod;
import io.harness.pms.accountoverrides.DataRetentionSettingsCreateResponseDTO;
import io.harness.pms.accountoverrides.DataRetentionSettingsUpdateResponseDTO;
import io.harness.pms.accountoverrides.ExportSettingsCreateRequestDTO;
import io.harness.pms.accountoverrides.ExportSettingsCreateResponseDTO;
import io.harness.pms.accountoverrides.ExportSettingsUpdateRequestDTO;
import io.harness.pms.accountoverrides.ExportSettingsUpdateResponseDTO;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class AccountOverridesApiMapperTest extends CategoryTest {
  private final String ACCOUNT_ID = "accountID";

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testToDTOCreateRequest() {
    AccountOverridesCreateRequestDTO requestDTO =
        AccountOverridesCreateRequestDTO.builder().maxConcurrentExecutions(100L).maxInputParameterSize(100L).build();
    AccountOverridesConfigDTO expectedConfigDTO = AccountOverridesConfigDTO.builder()
                                                      .accountIdentifier(ACCOUNT_ID)
                                                      .maxConcurrentExecutions(100L)
                                                      .maxInputParameterSize(100L)
                                                      .build();
    AccountOverridesConfigDTO gotConfigDTO = AccountOverridesApiMapper.toDTO(ACCOUNT_ID, requestDTO);
    assertThat(gotConfigDTO).isEqualTo(expectedConfigDTO);

    requestDTO = AccountOverridesCreateRequestDTO.builder()
                     .exportSettings(ExportSettingsCreateRequestDTO.builder().maxExportRequestsPerDay(15).build())
                     .build();
    expectedConfigDTO = AccountOverridesConfigDTO.builder()
                            .accountIdentifier(ACCOUNT_ID)
                            .exportSettings(ExportSettingsDTO.builder().maxExportRequestsPerDay(15).build())
                            .build();
    gotConfigDTO = AccountOverridesApiMapper.toDTO(ACCOUNT_ID, requestDTO);
    assertThat(gotConfigDTO).isEqualTo(expectedConfigDTO);

    requestDTO = AccountOverridesCreateRequestDTO.builder()
                     .maxConcurrentExecutions(100L)
                     .maxInputParameterSize(100L)
                     .exportSettings(ExportSettingsCreateRequestDTO.builder().maxExportRequestsPerDay(15).build())
                     .build();
    expectedConfigDTO = AccountOverridesConfigDTO.builder()
                            .accountIdentifier(ACCOUNT_ID)
                            .maxConcurrentExecutions(100L)
                            .maxInputParameterSize(100L)
                            .exportSettings(ExportSettingsDTO.builder().maxExportRequestsPerDay(15).build())
                            .build();
    gotConfigDTO = AccountOverridesApiMapper.toDTO(ACCOUNT_ID, requestDTO);
    assertThat(gotConfigDTO).isEqualTo(expectedConfigDTO);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testToDTOUpdateRequest() {
    AccountOverridesUpdateRequestDTO requestDTO =
        AccountOverridesUpdateRequestDTO.builder().maxConcurrentExecutions(100L).maxInputParameterSize(100L).build();
    AccountOverridesConfigDTO expectedConfigDTO = AccountOverridesConfigDTO.builder()
                                                      .accountIdentifier(ACCOUNT_ID)
                                                      .maxConcurrentExecutions(100L)
                                                      .maxInputParameterSize(100L)
                                                      .build();
    AccountOverridesConfigDTO gotConfigDTO = AccountOverridesApiMapper.toDTO(ACCOUNT_ID, requestDTO);
    assertThat(gotConfigDTO).isEqualTo(expectedConfigDTO);

    requestDTO = AccountOverridesUpdateRequestDTO.builder()
                     .exportSettings(ExportSettingsUpdateRequestDTO.builder().maxExportRequestsPerDay(15).build())
                     .build();
    expectedConfigDTO = AccountOverridesConfigDTO.builder()
                            .accountIdentifier(ACCOUNT_ID)
                            .exportSettings(ExportSettingsDTO.builder().maxExportRequestsPerDay(15).build())
                            .build();
    gotConfigDTO = AccountOverridesApiMapper.toDTO(ACCOUNT_ID, requestDTO);
    assertThat(gotConfigDTO).isEqualTo(expectedConfigDTO);

    requestDTO = AccountOverridesUpdateRequestDTO.builder()
                     .maxConcurrentExecutions(100L)
                     .maxInputParameterSize(100L)
                     .exportSettings(ExportSettingsUpdateRequestDTO.builder().maxExportRequestsPerDay(15).build())
                     .build();
    expectedConfigDTO = AccountOverridesConfigDTO.builder()
                            .accountIdentifier(ACCOUNT_ID)
                            .maxConcurrentExecutions(100L)
                            .maxInputParameterSize(100L)
                            .exportSettings(ExportSettingsDTO.builder().maxExportRequestsPerDay(15).build())
                            .build();
    gotConfigDTO = AccountOverridesApiMapper.toDTO(ACCOUNT_ID, requestDTO);
    assertThat(gotConfigDTO).isEqualTo(expectedConfigDTO);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testToCreateResponseDTO() {
    AccountOverridesCreateResponseDTO expectedCreateResponse = AccountOverridesCreateResponseDTO.builder()
                                                                   .accountIdentifier(ACCOUNT_ID)
                                                                   .retentionPeriodInMonths(12)
                                                                   .maxConcurrentExecutions(100L)
                                                                   .maxInputParameterSize(100L)
                                                                   .build();
    AccountOverridesConfigDTO createConfigDTO = AccountOverridesConfigDTO.builder()
                                                    .accountIdentifier(ACCOUNT_ID)
                                                    .retentionPeriodInMonths(12)
                                                    .maxConcurrentExecutions(100L)
                                                    .maxInputParameterSize(100L)
                                                    .build();
    AccountOverridesCreateResponseDTO gotResponseDTO = AccountOverridesApiMapper.toCreateResponseDTO(createConfigDTO);
    assertThat(gotResponseDTO).isEqualToComparingFieldByField(expectedCreateResponse);

    expectedCreateResponse =
        AccountOverridesCreateResponseDTO.builder()
            .accountIdentifier(ACCOUNT_ID)
            .dataRetentionSettings(DataRetentionSettingsCreateResponseDTO.builder()
                                       .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS)
                                       .build())
            .build();
    createConfigDTO =
        AccountOverridesConfigDTO.builder()
            .accountIdentifier(ACCOUNT_ID)
            .dataRetentionSettings(DataRetentionSettingsDTO.builder()
                                       .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS)
                                       .build())
            .build();
    gotResponseDTO = AccountOverridesApiMapper.toCreateResponseDTO(createConfigDTO);
    assertThat(gotResponseDTO.getDataRetentionSettings())
        .isEqualToComparingFieldByField(expectedCreateResponse.getDataRetentionSettings());
    assertThat(gotResponseDTO.getExportSettings()).isNull();
    assertCreateResponse(expectedCreateResponse, gotResponseDTO);

    expectedCreateResponse =
        AccountOverridesCreateResponseDTO.builder()
            .accountIdentifier(ACCOUNT_ID)
            .exportSettings(ExportSettingsCreateResponseDTO.builder().maxExportRequestsPerDay(15).build())
            .build();
    createConfigDTO = AccountOverridesConfigDTO.builder()
                          .accountIdentifier(ACCOUNT_ID)
                          .exportSettings(ExportSettingsDTO.builder().maxExportRequestsPerDay(15).build())
                          .build();
    gotResponseDTO = AccountOverridesApiMapper.toCreateResponseDTO(createConfigDTO);
    assertThat(gotResponseDTO.getExportSettings())
        .isEqualToComparingFieldByField(expectedCreateResponse.getExportSettings());
    assertThat(gotResponseDTO.getDataRetentionSettings()).isNull();
    assertCreateResponse(expectedCreateResponse, gotResponseDTO);

    expectedCreateResponse =
        AccountOverridesCreateResponseDTO.builder()
            .accountIdentifier(ACCOUNT_ID)
            .retentionPeriodInMonths(12)
            .maxConcurrentExecutions(100L)
            .maxInputParameterSize(100L)
            .dataRetentionSettings(DataRetentionSettingsCreateResponseDTO.builder()
                                       .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS)
                                       .build())
            .exportSettings(ExportSettingsCreateResponseDTO.builder().maxExportRequestsPerDay(15).build())
            .build();
    createConfigDTO =
        AccountOverridesConfigDTO.builder()
            .accountIdentifier(ACCOUNT_ID)
            .retentionPeriodInMonths(12)
            .maxConcurrentExecutions(100L)
            .maxInputParameterSize(100L)
            .dataRetentionSettings(DataRetentionSettingsDTO.builder()
                                       .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS)
                                       .build())
            .exportSettings(ExportSettingsDTO.builder().maxExportRequestsPerDay(15).build())
            .build();
    gotResponseDTO = AccountOverridesApiMapper.toCreateResponseDTO(createConfigDTO);
    assertThat(gotResponseDTO.getDataRetentionSettings())
        .isEqualToComparingFieldByField(expectedCreateResponse.getDataRetentionSettings());
    assertThat(gotResponseDTO.getExportSettings())
        .isEqualToComparingFieldByField(expectedCreateResponse.getExportSettings());
    assertCreateResponse(expectedCreateResponse, gotResponseDTO);
  }

  private void assertCreateResponse(
      AccountOverridesCreateResponseDTO expectedCreateResponse, AccountOverridesCreateResponseDTO gotResponseDTO) {
    assertThat(gotResponseDTO.getAccountIdentifier()).isEqualTo(expectedCreateResponse.getAccountIdentifier());
    assertThat(gotResponseDTO.getRetentionPeriodInMonths())
        .isEqualTo(expectedCreateResponse.getRetentionPeriodInMonths());
    assertThat(gotResponseDTO.getMaxConcurrentExecutions())
        .isEqualTo(expectedCreateResponse.getMaxConcurrentExecutions());
    assertThat(gotResponseDTO.getMaxOutcomeResponseSize())
        .isEqualTo(expectedCreateResponse.getMaxOutcomeResponseSize());
    assertThat(gotResponseDTO.getMaxInputParameterSize()).isEqualTo(expectedCreateResponse.getMaxInputParameterSize());
  }

  private void assertUpdateResponse(
      AccountOverridesUpdateResponseDTO expectedUpdateResponse, AccountOverridesUpdateResponseDTO gotResponseDTO) {
    assertThat(gotResponseDTO.getAccountIdentifier()).isEqualTo(expectedUpdateResponse.getAccountIdentifier());
    assertThat(gotResponseDTO.getRetentionPeriodInMonths())
        .isEqualTo(expectedUpdateResponse.getRetentionPeriodInMonths());
    assertThat(gotResponseDTO.getMaxConcurrentExecutions())
        .isEqualTo(expectedUpdateResponse.getMaxConcurrentExecutions());
    assertThat(gotResponseDTO.getMaxOutcomeResponseSize())
        .isEqualTo(expectedUpdateResponse.getMaxOutcomeResponseSize());
    assertThat(gotResponseDTO.getMaxInputParameterSize()).isEqualTo(expectedUpdateResponse.getMaxInputParameterSize());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testToUpdateResponseDTO() {
    AccountOverridesUpdateResponseDTO expectedUpdateResponse = AccountOverridesUpdateResponseDTO.builder()
                                                                   .accountIdentifier(ACCOUNT_ID)
                                                                   .retentionPeriodInMonths(12)
                                                                   .maxConcurrentExecutions(100L)
                                                                   .maxInputParameterSize(100L)
                                                                   .build();
    AccountOverridesConfigDTO updateConfigDTO = AccountOverridesConfigDTO.builder()
                                                    .accountIdentifier(ACCOUNT_ID)
                                                    .retentionPeriodInMonths(12)
                                                    .maxConcurrentExecutions(100L)
                                                    .maxInputParameterSize(100L)
                                                    .build();
    AccountOverridesUpdateResponseDTO gotResponseDTO = AccountOverridesApiMapper.toUpdateResponseDTO(updateConfigDTO);
    assertThat(gotResponseDTO).isEqualToComparingFieldByField(expectedUpdateResponse);

    expectedUpdateResponse =
        AccountOverridesUpdateResponseDTO.builder()
            .accountIdentifier(ACCOUNT_ID)
            .dataRetentionSettings(DataRetentionSettingsUpdateResponseDTO.builder()
                                       .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS)
                                       .build())
            .build();
    updateConfigDTO =
        AccountOverridesConfigDTO.builder()
            .accountIdentifier(ACCOUNT_ID)
            .dataRetentionSettings(DataRetentionSettingsDTO.builder()
                                       .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS)
                                       .build())
            .build();
    gotResponseDTO = AccountOverridesApiMapper.toUpdateResponseDTO(updateConfigDTO);
    assertThat(gotResponseDTO.getDataRetentionSettings())
        .isEqualToComparingFieldByField(expectedUpdateResponse.getDataRetentionSettings());
    assertThat(gotResponseDTO.getExportSettings()).isNull();
    assertUpdateResponse(expectedUpdateResponse, gotResponseDTO);

    expectedUpdateResponse =
        AccountOverridesUpdateResponseDTO.builder()
            .accountIdentifier(ACCOUNT_ID)
            .exportSettings(ExportSettingsUpdateResponseDTO.builder().maxExportRequestsPerDay(15).build())
            .build();
    updateConfigDTO = AccountOverridesConfigDTO.builder()
                          .accountIdentifier(ACCOUNT_ID)
                          .exportSettings(ExportSettingsDTO.builder().maxExportRequestsPerDay(15).build())
                          .build();
    gotResponseDTO = AccountOverridesApiMapper.toUpdateResponseDTO(updateConfigDTO);
    assertThat(gotResponseDTO.getExportSettings())
        .isEqualToComparingFieldByField(expectedUpdateResponse.getExportSettings());
    assertThat(gotResponseDTO.getDataRetentionSettings()).isNull();
    assertUpdateResponse(expectedUpdateResponse, gotResponseDTO);

    expectedUpdateResponse =
        AccountOverridesUpdateResponseDTO.builder()
            .accountIdentifier(ACCOUNT_ID)
            .retentionPeriodInMonths(12)
            .maxConcurrentExecutions(100L)
            .maxInputParameterSize(100L)
            .dataRetentionSettings(DataRetentionSettingsUpdateResponseDTO.builder()
                                       .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS)
                                       .build())
            .exportSettings(ExportSettingsUpdateResponseDTO.builder().maxExportRequestsPerDay(15).build())
            .build();
    updateConfigDTO =
        AccountOverridesConfigDTO.builder()
            .accountIdentifier(ACCOUNT_ID)
            .retentionPeriodInMonths(12)
            .maxConcurrentExecutions(100L)
            .maxInputParameterSize(100L)
            .dataRetentionSettings(DataRetentionSettingsDTO.builder()
                                       .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS)
                                       .build())
            .exportSettings(ExportSettingsDTO.builder().maxExportRequestsPerDay(15).build())
            .build();
    gotResponseDTO = AccountOverridesApiMapper.toUpdateResponseDTO(updateConfigDTO);
    assertThat(gotResponseDTO.getDataRetentionSettings())
        .isEqualToComparingFieldByField(expectedUpdateResponse.getDataRetentionSettings());
    assertThat(gotResponseDTO.getExportSettings())
        .isEqualToComparingFieldByField(expectedUpdateResponse.getExportSettings());
    assertUpdateResponse(expectedUpdateResponse, gotResponseDTO);
  }
}
