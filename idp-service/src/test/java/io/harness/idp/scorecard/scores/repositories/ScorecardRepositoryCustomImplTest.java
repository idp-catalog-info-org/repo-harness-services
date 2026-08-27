/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.repositories;

import static io.harness.rule.OwnerRule.NITESH_GAHLOT;
import static io.harness.rule.OwnerRule.VIGNESWARA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.scorecards.entity.ScorecardEntity;
import io.harness.idp.scorecard.scorecards.repositories.ScorecardRepositoryCustomImpl;
import io.harness.rule.Owner;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
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
public class ScorecardRepositoryCustomImplTest extends CategoryTest {
  @InjectMocks ScorecardRepositoryCustomImpl scorecardRepositoryCustomImpl;
  @Mock private MongoTemplate mongoTemplate;
  private static final String ACCOUNT_ID = "123";
  private static final String SCORECARD_ID = "service_maturity";
  private static final String SCORECARD_NAME = "Service Maturity";
  private static final String SCORECARD_NAME_UPDATED = "Service Maturity Update";

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDelete() {
    when(mongoTemplate.remove(any(), eq(ScorecardEntity.class))).thenReturn(DeleteResult.acknowledged(1));
    DeleteResult deleteResult = scorecardRepositoryCustomImpl.delete(ACCOUNT_ID, SCORECARD_ID);
    assertEquals(1, deleteResult.getDeletedCount());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testFindByCheckIdentifierAndIsCustom() {
    when(mongoTemplate.find(any(), eq(ScorecardEntity.class))).thenReturn(List.of(getScorecardEntity()));
    List<ScorecardEntity> scorecardEntities =
        scorecardRepositoryCustomImpl.findByCheckIdentifierAndIsCustom(ACCOUNT_ID, "github_check", Boolean.TRUE);
    assertEquals(1, scorecardEntities.size());
    assertEquals(SCORECARD_ID, scorecardEntities.get(0).getIdentifier());
  }

  @Test
  @Owner(developers = NITESH_GAHLOT)
  @Category(UnitTests.class)
  public void testUpdateScoreCounts() {
    when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(ScorecardEntity.class)))
        .thenReturn(UpdateResult.acknowledged(1, 1L, null));
    ScorecardEntity.TierComponentCount tierCount = ScorecardEntity.TierComponentCount.builder()
                                                       .tierName("Gold")
                                                       .minScore(75)
                                                       .maxScore(100)
                                                       .tierColour("#FFD700")
                                                       .componentCount(2)
                                                       .build();

    scorecardRepositoryCustomImpl.updateScoreCounts(ACCOUNT_ID, SCORECARD_ID, 3, List.of(tierCount), 1234L);

    ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(mongoTemplate).updateFirst(queryCaptor.capture(), updateCaptor.capture(), eq(ScorecardEntity.class));
    assertThat(queryCaptor.getValue().getQueryObject().toJson()).contains("scoreCountsComputedAt", "$lte");
    Document fields = (Document) updateCaptor.getValue().getUpdateObject().get("$set");
    assertThat(fields.get("componentCount")).isEqualTo(3);
    assertThat(fields.get("tierComponentCounts")).isEqualTo(List.of(tierCount));
    assertThat(fields.get("scoreCountsComputedAt")).isEqualTo(1234L);
  }

  private ScorecardEntity getScorecardEntity() {
    return ScorecardEntity.builder()
        .accountIdentifier(ACCOUNT_ID)
        .name(SCORECARD_NAME)
        .identifier(SCORECARD_ID)
        .published(true)
        .build();
  }
}
