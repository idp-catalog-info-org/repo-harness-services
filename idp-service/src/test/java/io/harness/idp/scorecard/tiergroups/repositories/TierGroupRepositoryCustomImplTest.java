/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.tiergroups.repositories;

import static io.harness.rule.OwnerRule.NITESH_GAHLOT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.tiergroups.entity.TierGroupEntity;
import io.harness.rule.Owner;

import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.IDP)
public class TierGroupRepositoryCustomImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account";
  private static final String TIER_GROUP_ID = "custom_tiers";

  @InjectMocks private TierGroupRepositoryCustomImpl repository;
  @Mock private MongoTemplate mongoTemplate;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void deleteSoftDeletesActiveTierGroup() {
    when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(TierGroupEntity.class)))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));

    UpdateResult result = repository.softDelete(ACCOUNT_ID, TIER_GROUP_ID);

    assertThat(result.getModifiedCount()).isEqualTo(1);
    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate).updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(TierGroupEntity.class));
    assertThat(queryCaptor.getValue().getQueryObject())
        .containsEntry("accountIdentifier", ACCOUNT_ID)
        .containsEntry("identifier", TIER_GROUP_ID)
        .containsEntry("isDeleted", false);
    Document set = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
    assertThat(set).containsEntry("isDeleted", true);
    assertThat((Long) set.get("deletedAt")).isPositive();
  }
}
