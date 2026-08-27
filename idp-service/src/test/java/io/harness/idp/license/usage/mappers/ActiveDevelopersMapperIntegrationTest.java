/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.mappers;

import static io.harness.rule.OwnerRule.NISARG;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.license.usage.dto.ActiveDevelopersTrendCountDTO;
import io.harness.idp.license.usage.dto.IDPLicenseUsageUserCaptureDTO;
import io.harness.idp.license.usage.entities.ActiveDevelopersDailyCountEntity;
import io.harness.idp.license.usage.entities.ActiveDevelopersEntity;
import io.harness.rule.Owner;

import java.util.Date;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ActiveDevelopersMapperIntegrationTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final String TEST_USER_IDENTIFIER = "testUser123";
  static final String TEST_EMAIL = "test@example.com";
  static final String TEST_USER_NAME = "Test User";
  static final long TEST_ACCESSED_AT = 1698294600000L;
  static final String TEST_DATE_STRING = "2023-10-26";
  static final long TEST_COUNT = 50L;

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testEntityMapperRoundTrip() {
    IDPLicenseUsageUserCaptureDTO originalDto = IDPLicenseUsageUserCaptureDTO.builder()
                                                    .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                    .userIdentifier(TEST_USER_IDENTIFIER)
                                                    .email(TEST_EMAIL)
                                                    .userName(TEST_USER_NAME)
                                                    .accessedAt(TEST_ACCESSED_AT)
                                                    .build();

    ActiveDevelopersEntity entity = ActiveDevelopersEntityMapper.fromDto(originalDto);
    assertThat(entity).isNotNull();
    assertThat(entity.getAccountIdentifier()).isEqualTo(TEST_ACCOUNT_IDENTIFIER);
    assertThat(entity.getUserIdentifier()).isEqualTo(TEST_USER_IDENTIFIER);

    IDPLicenseUsageUserCaptureDTO convertedDto = ActiveDevelopersEntityMapper.toDto(entity);
    assertThat(convertedDto).isNotNull();
    assertThat(convertedDto.getAccountIdentifier()).isEqualTo(originalDto.getAccountIdentifier());
    assertThat(convertedDto.getUserIdentifier()).isEqualTo(originalDto.getUserIdentifier());
    assertThat(convertedDto.getEmail()).isEqualTo(originalDto.getEmail());
    assertThat(convertedDto.getUserName()).isEqualTo(originalDto.getUserName());
    assertThat(convertedDto.getAccessedAt()).isEqualTo(originalDto.getAccessedAt());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testDailyCountEntityMapperConversion() {
    Date testDate = new Date();
    ActiveDevelopersDailyCountEntity entity = ActiveDevelopersDailyCountEntity.builder()
                                                  .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                  .dateInStringFormat(TEST_DATE_STRING)
                                                  .dateInDateFormat(testDate)
                                                  .count(TEST_COUNT)
                                                  .build();

    ActiveDevelopersTrendCountDTO dto = ActiveDevelopersDailyCountEntityMapper.toDto(entity);

    assertThat(dto).isNotNull();
    assertThat(dto.getDate()).isEqualTo(TEST_DATE_STRING);
    assertThat(dto.getCount()).isEqualTo(TEST_COUNT);
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testMapperConsistency() {
    IDPLicenseUsageUserCaptureDTO dto1 = IDPLicenseUsageUserCaptureDTO.builder()
                                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                             .userIdentifier(TEST_USER_IDENTIFIER)
                                             .email(TEST_EMAIL)
                                             .userName(TEST_USER_NAME)
                                             .accessedAt(TEST_ACCESSED_AT)
                                             .build();

    IDPLicenseUsageUserCaptureDTO dto2 = IDPLicenseUsageUserCaptureDTO.builder()
                                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                             .userIdentifier(TEST_USER_IDENTIFIER)
                                             .email(TEST_EMAIL)
                                             .userName(TEST_USER_NAME)
                                             .accessedAt(TEST_ACCESSED_AT)
                                             .build();

    ActiveDevelopersEntity entity1 = ActiveDevelopersEntityMapper.fromDto(dto1);
    ActiveDevelopersEntity entity2 = ActiveDevelopersEntityMapper.fromDto(dto2);

    assertThat(entity1.getAccountIdentifier()).isEqualTo(entity2.getAccountIdentifier());
    assertThat(entity1.getUserIdentifier()).isEqualTo(entity2.getUserIdentifier());
    assertThat(entity1.getEmail()).isEqualTo(entity2.getEmail());
    assertThat(entity1.getUserName()).isEqualTo(entity2.getUserName());
    assertThat(entity1.getLastAccessedAt()).isEqualTo(entity2.getLastAccessedAt());
  }
}
