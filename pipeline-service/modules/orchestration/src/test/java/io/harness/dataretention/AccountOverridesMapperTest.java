/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dataretention;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.entity.accountoverrides.DataRetentionEntity;
import io.harness.entity.accountoverrides.DataRetentionSettings;
import io.harness.entity.accountoverrides.ExportSettings;
import io.harness.entity.accountoverrides.beans.AccountOverridesConfigDTO;
import io.harness.entity.accountoverrides.beans.DataRetentionSettingsDTO;
import io.harness.entity.accountoverrides.beans.ExportSettingsDTO;
import io.harness.pms.accountoverrides.DataRetentionPeriod;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class AccountOverridesMapperTest extends CategoryTest {
  private final String ACCOUNT_ID = "accountID";

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testToEntity() {
    AccountOverridesConfigDTO configDTO = AccountOverridesConfigDTO.builder()
                                              .accountIdentifier(ACCOUNT_ID)
                                              .retentionPeriodInMonths(12)
                                              .maxConcurrentExecutions(100L)
                                              .maxInputParameterSize(100L)
                                              .build();
    DataRetentionEntity expectedDataRetentionEntity = DataRetentionEntity.builder()
                                                          .accountIdentifier(ACCOUNT_ID)
                                                          .retentionPeriodInMonths(12)
                                                          .maxConcurrentExecutions(100L)
                                                          .maxInputParameterSize(100L)
                                                          .build();
    DataRetentionEntity gotDataRetentionEntity = AccountOverridesMapper.toEntity(configDTO, 6);
    assertThat(gotDataRetentionEntity).isEqualTo(expectedDataRetentionEntity);

    configDTO = AccountOverridesConfigDTO.builder()
                    .accountIdentifier(ACCOUNT_ID)
                    .dataRetentionSettings(DataRetentionSettingsDTO.builder()
                                               .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS)
                                               .build())
                    .build();
    expectedDataRetentionEntity =
        DataRetentionEntity.builder()
            .accountIdentifier(ACCOUNT_ID)
            .retentionPeriodInMonths(6)
            .dataRetentionSettings(DataRetentionSettings.builder()
                                       .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS)
                                       .build())
            .build();
    gotDataRetentionEntity = AccountOverridesMapper.toEntity(configDTO, 6);
    assertThat(gotDataRetentionEntity).isEqualTo(expectedDataRetentionEntity);

    configDTO = AccountOverridesConfigDTO.builder()
                    .accountIdentifier(ACCOUNT_ID)
                    .exportSettings(ExportSettingsDTO.builder().maxExportRequestsPerDay(15).build())
                    .build();
    expectedDataRetentionEntity = DataRetentionEntity.builder()
                                      .accountIdentifier(ACCOUNT_ID)
                                      .retentionPeriodInMonths(6)
                                      .exportSettings(ExportSettings.builder().maxExportRequestsPerDay(15).build())
                                      .build();
    gotDataRetentionEntity = AccountOverridesMapper.toEntity(configDTO, 6);
    assertThat(gotDataRetentionEntity).isEqualTo(expectedDataRetentionEntity);

    configDTO = AccountOverridesConfigDTO.builder()
                    .accountIdentifier(ACCOUNT_ID)
                    .retentionPeriodInMonths(12)
                    .maxConcurrentExecutions(100L)
                    .maxInputParameterSize(100L)
                    .dataRetentionSettings(DataRetentionSettingsDTO.builder()
                                               .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS)
                                               .build())
                    .exportSettings(ExportSettingsDTO.builder().maxExportRequestsPerDay(15).build())
                    .build();
    expectedDataRetentionEntity =
        DataRetentionEntity.builder()
            .accountIdentifier(ACCOUNT_ID)
            .retentionPeriodInMonths(12)
            .maxConcurrentExecutions(100L)
            .maxInputParameterSize(100L)
            .dataRetentionSettings(DataRetentionSettings.builder()
                                       .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS)
                                       .build())
            .exportSettings(ExportSettings.builder().maxExportRequestsPerDay(15).build())
            .build();
    gotDataRetentionEntity = AccountOverridesMapper.toEntity(configDTO, 6);
    assertThat(gotDataRetentionEntity).isEqualTo(expectedDataRetentionEntity);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testToCreateResponseDTO() {
    DataRetentionEntity entity = DataRetentionEntity.builder()
                                     .accountIdentifier(ACCOUNT_ID)
                                     .retentionPeriodInMonths(12)
                                     .maxConcurrentExecutions(100L)
                                     .maxInputParameterSize(100L)
                                     .build();
    AccountOverridesConfigDTO expectedResponseDTO = AccountOverridesConfigDTO.builder()
                                                        .accountIdentifier(ACCOUNT_ID)
                                                        .retentionPeriodInMonths(12)
                                                        .maxConcurrentExecutions(100L)
                                                        .maxInputParameterSize(100L)
                                                        .build();
    AccountOverridesConfigDTO gotResponseDTO = AccountOverridesMapper.toDTO(entity);
    assertThat(gotResponseDTO).isEqualTo(expectedResponseDTO);

    entity = DataRetentionEntity.builder()
                 .accountIdentifier(ACCOUNT_ID)
                 .dataRetentionSettings(DataRetentionSettings.builder()
                                            .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS)
                                            .build())
                 .build();
    expectedResponseDTO =
        AccountOverridesConfigDTO.builder()
            .accountIdentifier(ACCOUNT_ID)
            .retentionPeriodInMonths(6)
            .dataRetentionSettings(DataRetentionSettingsDTO.builder()
                                       .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS)
                                       .build())
            .build();
    gotResponseDTO = AccountOverridesMapper.toDTO(entity);
    assertThat(gotResponseDTO).isEqualTo(expectedResponseDTO);

    entity = DataRetentionEntity.builder()
                 .accountIdentifier(ACCOUNT_ID)
                 .exportSettings(ExportSettings.builder().maxExportRequestsPerDay(15).build())
                 .build();
    expectedResponseDTO = AccountOverridesConfigDTO.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .retentionPeriodInMonths(6)
                              .exportSettings(ExportSettingsDTO.builder().maxExportRequestsPerDay(15).build())
                              .build();
    gotResponseDTO = AccountOverridesMapper.toDTO(entity);
    assertThat(gotResponseDTO).isEqualTo(expectedResponseDTO);

    entity = DataRetentionEntity.builder()
                 .accountIdentifier(ACCOUNT_ID)
                 .retentionPeriodInMonths(12)
                 .maxConcurrentExecutions(100L)
                 .maxInputParameterSize(100L)
                 .dataRetentionSettings(DataRetentionSettings.builder()
                                            .dataRetentionPeriod(DataRetentionPeriod.DATA_RETENTION_PERIOD_12_MONTHS)
                                            .build())
                 .exportSettings(ExportSettings.builder().maxExportRequestsPerDay(15).build())
                 .build();
    expectedResponseDTO =
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
    gotResponseDTO = AccountOverridesMapper.toDTO(entity);
    assertThat(gotResponseDTO).isEqualTo(expectedResponseDTO);
  }
}
