/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.mappers;

import static io.harness.rule.OwnerRule.AGNIVA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.beans.EmbeddedUser;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.scores.entity.AsyncScoreComputationEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ScorecardRecalibrateInfo;
import io.harness.spec.server.idp.v1.model.User;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AsyncScoreComputationMapperTest {
  private AsyncScoreComputationEntity asyncScoreComputationEntity;
  private static final String User_uuid = "user123";
  private static final String User_name = "Test User";
  private static final String User_email = "test.user@example.com";
  @Before
  public void setUp() {
    EmbeddedUser createdByUser = EmbeddedUser.builder().uuid(User_uuid).name(User_name).email(User_email).build();
    asyncScoreComputationEntity =
        AsyncScoreComputationEntity.builder().startTime(1609459200000L).createdBy(createdByUser).build();
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testToDTO() {
    ScorecardRecalibrateInfo result = AsyncScoreComputationMapper.toDTO(asyncScoreComputationEntity);
    assertNotNull(result);
    assertEquals(Long.valueOf(1609459200000L), result.getStartTime());
    assertNotNull(result.getStartedBy());
    User startedByUser = result.getStartedBy();
    assertEquals(User_uuid, startedByUser.getUuid());
    assertEquals(User_name, startedByUser.getName());
    assertEquals(User_email, startedByUser.getEmail());
  }
}
