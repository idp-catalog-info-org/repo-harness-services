/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.search.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.plan.TriggerType.MANUAL;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.rule.Owner;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PipelineSearchHelperTest extends CategoryTest {
  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetPipelineTagsQuery() {
    Query query = PipelineSearchHelper.getPipelineTagsQuery(Arrays.asList(NGTag.builder().key("tagKey1").build(),
        NGTag.builder().key("tagValue2").build(), NGTag.builder().key("tagKey3").value("tagValue3").build()));
    assertThat(query.toString())
        .isEqualTo("Query: "
            + "{\"nested\":{\"path\":\"tags\",\"query\":{\"bool\":{\"should\":[{\"bool\":{\"must\":[{\"term\":{"
            + "\"tags.key\":{\"value\":\"tagKey3\"}}},{\"term\":{\"tags.value\":{\"value\":\"tagValue3\"}}}]}},{"
            + "\"terms\":{\"tags.key\":[\"tagKey1\",\"tagValue2\"]}},{\"terms\":{\"tags.value\":[\"tagKey1\","
            + "\"tagValue2\"]}}]}}}}");
    query = PipelineSearchHelper.getPipelineTagsQuery(
        Arrays.asList(NGTag.builder().key("tagKey1").value("tagValue1").build(),
            NGTag.builder().key("tagKey2").value("tagValue2").build()));
    assertThat(query.toString())
        .isEqualTo("Query: "
            + "{\"nested\":{\"path\":\"tags\",\"query\":{\"bool\":{\"should\":[{\"bool\":{\"must\":[{\"term\":{"
            + "\"tags.key\":{\"value\":\"tagKey1\"}}},{\"term\":{\"tags.value\":{\"value\":\"tagValue1\"}}}]}},{"
            + "\"bool\":{\"must\":[{\"term\":{\"tags.key\":{\"value\":\"tagKey2\"}}},{\"term\":{\"tags.value\":{"
            + "\"value\":\"tagValue2\"}}}]}}]}}}}");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetModuleNameQuery() {
    Query query = PipelineSearchHelper.getModuleNameQuery("cd");
    assertThat(query.toString())
        .isEqualTo("Query: "
            + "{\"bool\":{\"should\":[{\"term\":{\"modules\":{\"value\":\"common\"}}},{\"term\":{\"modules\":{"
            + "\"value\":\"cd\"}}}]}}");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetExecutionModeQuery() {
    Query query = PipelineSearchHelper.getExecutionModeQuery();
    assertThat(query.toString())
        .isEqualTo(
            "Query: {\"terms\":{\"executionMode\":[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}}");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetTriggeredByQuery() {
    Query query = PipelineSearchHelper.getTriggeredByQuery(
        MANUAL.toString(), TriggeredBy.newBuilder().putExtraInfo("email", "test@email.com").build());
    assertThat(query.toString())
        .isEqualTo("Query: "
            + "{\"bool\":{\"must\":[{\"term\":{\"triggerType\":{\"value\":\"MANUAL\"}}},{\"term\":{"
            + "\"triggeredBy.email\":{\"value\":\"test@email.com\"}}}]}}");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetPipelineNameQuery() {
    Query query = PipelineSearchHelper.getPipelineNameQuery("pipelineName");
    assertThat(query.toString())
        .isEqualTo("Query: "
            + "{\"bool\":{\"should\":[{\"wildcard\":{\"pipelineIdentifier\":{\"case_insensitive\":true,"
            + "\"value\":\"*pipelineName*\"}}},{\"wildcard\":{\"name\":{\"case_insensitive\":true,\"value\":\"*"
            + "pipelineName*\"}}}]}}");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetStatusQuery() {
    Query query = PipelineSearchHelper.getStatusQuery(Arrays.asList(ExecutionStatus.EXPIRED, ExecutionStatus.ABORTED));
    assertThat(query.toString()).isEqualTo("Query: {\"terms\":{\"status\":[\"EXPIRED\",\"ABORTED\"]}}");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetScopeQuery() {
    List<Query> query = PipelineSearchHelper.getScopeQuery("accountId", null, null);
    assertThat(query).hasSize(2);
    assertThat(query.get(0).toString()).isEqualTo("Query: {\"term\":{\"accountId\":{\"value\":\"accountId\"}}}");
    assertThat(query.get(1).toString()).isEqualTo("Query: {\"term\":{\"deleted\":{\"value\":false}}}");
    query = PipelineSearchHelper.getScopeQuery("accountId", "orgId", null);
    assertThat(query).hasSize(3);
    assertThat(query.get(0).toString()).isEqualTo("Query: {\"term\":{\"accountId\":{\"value\":\"accountId\"}}}");
    assertThat(query.get(1).toString()).isEqualTo("Query: {\"term\":{\"orgIdentifier\":{\"value\":\"orgId\"}}}");
    assertThat(query.get(2).toString()).isEqualTo("Query: {\"term\":{\"deleted\":{\"value\":false}}}");
    query = PipelineSearchHelper.getScopeQuery("accountId", "orgId", "projectId");
    assertThat(query).hasSize(4);
    assertThat(query.get(0).toString()).isEqualTo("Query: {\"term\":{\"accountId\":{\"value\":\"accountId\"}}}");
    assertThat(query.get(1).toString()).isEqualTo("Query: {\"term\":{\"orgIdentifier\":{\"value\":\"orgId\"}}}");
    assertThat(query.get(2).toString())
        .isEqualTo("Query: {\"term\":{\"projectIdentifier\":{\"value\":\"projectId\"}}}");
    assertThat(query.get(3).toString()).isEqualTo("Query: {\"term\":{\"deleted\":{\"value\":false}}}");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetSearchTermQuery() {
    Query query = PipelineSearchHelper.getSearchTermQuery("searchTerm");
    assertThat(query.toString())
        .isEqualTo("Query: "
            + "{\"bool\":{\"should\":[{\"wildcard\":{\"pipelineIdentifier\":{\"case_insensitive\":true,"
            + "\"value\":\"*searchTerm*\"}}},{\"wildcard\":{\"name\":{\"case_insensitive\":true,\"value\":\"*"
            + "searchTerm*\"}}},{\"nested\":{\"path\":\"tags\",\"query\":{\"bool\":{\"should\":[{\"wildcard\":{"
            + "\"tags.key\":{\"case_insensitive\":true,\"value\":\"*searchTerm*\"}}},{\"wildcard\":{\"tags."
            + "value\":{\"case_insensitive\":true,\"value\":\"*searchTerm*\"}}}]}}}}]}}");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testGetNotesQuery() {
    Query query = PipelineSearchHelper.getNotesQuery(Arrays.asList("one", "two", ":D", "edgar"));
    assertThat(query.toString())
        .isEqualTo("Query: "
            + "{\"bool\":{\"should\":[{\"wildcard\":{\"notes\":{\"case_insensitive\":true,\"value\":\"*one*"
            + "\"}}},{\"wildcard\":{\"notes\":{\"case_insensitive\":true,\"value\":\"*two*\"}}},{"
            + "\"wildcard\":{\"notes\":{\"case_insensitive\":true,\"value\":\"*:D*\"}}},{\"wildcard\":{"
            + "\"notes\":{\"case_insensitive\":true,\"value\":\"*edgar*\"}}}]}}");
  }
}
