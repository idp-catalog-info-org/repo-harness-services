/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.BHUMIJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.beans.resource.RollbackResponse;
import io.harness.pms.ngpipeline.inputset.service.impl.PMSInputSetInlineHcMigrationServiceImpl;
import io.harness.repositories.inputset.PMSInputSetRepository;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public class PMSInputSetInlineHcMigrationServiceImplTest extends CategoryTest {
  @Mock private PMSInputSetRepository inputSetRepository;
  @InjectMocks private PMSInputSetInlineHcMigrationServiceImpl pmsInputSetInlineHcMigrationService;

  @Captor private ArgumentCaptor<Criteria> criteriaCaptor;
  @Captor private ArgumentCaptor<Update> updateCaptor;

  private final String accountId = "account123";

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRollbackInputSetsFromInlineHCToInline_Success() {
    // Given
    when(inputSetRepository.updateInputSetMetadataBulk(any(Criteria.class), any(Update.class))).thenReturn(10L);

    // When
    RollbackResponse response = pmsInputSetInlineHcMigrationService.rollbackInputSetsFromInlineHCToInline(accountId);

    // Then
    verify(inputSetRepository).updateInputSetMetadataBulk(criteriaCaptor.capture(), updateCaptor.capture());

    Criteria criteria = criteriaCaptor.getValue();
    assertThat(criteria.getCriteriaObject().get(InputSetEntityKeys.accountId)).isEqualTo(accountId);
    assertThat(criteria.getCriteriaObject().get(InputSetEntityKeys.storeType)).isEqualTo(StoreType.INLINE_HC);
    assertThat(criteria.getCriteriaObject().get(InputSetEntityKeys.deleted)).isEqualTo(false);

    Update update = updateCaptor.getValue();
    assertThat(update.getUpdateObject().get("$set")).isNotNull();

    assertThat(response.getMigratedCount()).isEqualTo(10);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRollbackInputSetsFromInlineHCToInline_NoInputSetsFound() {
    // Given
    when(inputSetRepository.updateInputSetMetadataBulk(any(Criteria.class), any(Update.class))).thenReturn(0L);

    // When
    RollbackResponse response = pmsInputSetInlineHcMigrationService.rollbackInputSetsFromInlineHCToInline(accountId);

    // Then
    verify(inputSetRepository).updateInputSetMetadataBulk(any(Criteria.class), any(Update.class));
    assertThat(response.getMigratedCount()).isEqualTo(0);
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRollbackInputSetsFromInlineHCToInline_EmptyAccountId() {
    // Given
    String emptyAccountId = "";

    // When/Then
    assertThatThrownBy(() -> pmsInputSetInlineHcMigrationService.rollbackInputSetsFromInlineHCToInline(emptyAccountId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Account identifier is required");
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void testRollbackInputSetsFromInlineHCToInline_RepositoryException() {
    // Given
    when(inputSetRepository.updateInputSetMetadataBulk(any(Criteria.class), any(Update.class)))
        .thenThrow(new RuntimeException("Database error"));

    // When/Then
    assertThatThrownBy(() -> pmsInputSetInlineHcMigrationService.rollbackInputSetsFromInlineHCToInline(accountId))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Database error");
  }
}
