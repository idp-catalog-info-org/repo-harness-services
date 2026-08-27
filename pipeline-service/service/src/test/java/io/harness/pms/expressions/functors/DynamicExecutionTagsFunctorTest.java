/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.expressions.functors;

import static io.harness.rule.OwnerRule.RISHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.EngineFunctorException;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.plan.execution.service.intfc.PMSExecutionService;
import io.harness.rule.Owner;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public class DynamicExecutionTagsFunctorTest extends CategoryTest {
  private static final String PLAN_EXECUTION_ID = "planExecutionId";

  private PmsExecutionSummaryService pmsExecutionSummaryService;
  private PMSExecutionService pmsExecutionService;
  private DynamicExecutionTagsFunctor dynamicExecutionTagsFunctor;

  @Before
  public void setUp() {
    pmsExecutionSummaryService = mock(PmsExecutionSummaryService.class);
    pmsExecutionService = mock(PMSExecutionService.class);
    dynamicExecutionTagsFunctor =
        new DynamicExecutionTagsFunctor(PLAN_EXECUTION_ID, pmsExecutionSummaryService, pmsExecutionService);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testAddTagSuccessCases() {
    Set<String> expectedProjections = Set.of(PlanExecutionSummaryKeys.tags);

    // Test 1: Add tag with key only
    PipelineExecutionSummaryEntity entity1 = createEntityWithTags(new HashSet<>());
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entity1);

    Object result1 = dynamicExecutionTagsFunctor.addTag("testKey");
    assertThat(result1).isEqualTo("testKey:");
    verifyUpdateCall("testKey", "");
    reset(pmsExecutionSummaryService);

    // Test 2: Add tag with key and value
    PipelineExecutionSummaryEntity entity2 = createEntityWithTags(new HashSet<>());
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entity2);

    Object result2 = dynamicExecutionTagsFunctor.addTag("testKey", "testValue");
    assertThat(result2).isEqualTo("testKey:testValue");
    verifyUpdateCall("testKey", "testValue");
    reset(pmsExecutionSummaryService);

    // Test 3: Add tag with null value (should convert to empty string)
    PipelineExecutionSummaryEntity entity3 = createEntityWithTags(new HashSet<>());
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entity3);

    Object result3 = dynamicExecutionTagsFunctor.addTag("testKey", null);
    assertThat(result3).isEqualTo("testKey:");
    verifyUpdateCall("testKey", "");
    reset(pmsExecutionSummaryService);

    // Test 4: Add tag with maximum allowed key and value length
    String maxKey = "a".repeat(1000);
    String maxValue = "b".repeat(1000);
    PipelineExecutionSummaryEntity entity4 = createEntityWithTags(new HashSet<>());
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entity4);

    Object result4 = dynamicExecutionTagsFunctor.addTag(maxKey, maxValue);
    assertThat(result4).isEqualTo(maxKey + ":" + maxValue);
    verifyUpdateCall(maxKey, maxValue);
    reset(pmsExecutionSummaryService);

    // Test 5: Add tag when just under maximum tags (999 tags)
    Set<NGTag> nearMaxTags = new HashSet<>();
    IntStream.range(0, 999).forEach(i -> nearMaxTags.add(NGTag.builder().key("key" + i).value("value" + i).build()));
    PipelineExecutionSummaryEntity entity5 = createEntityWithTags(nearMaxTags);
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entity5);

    Object result5 = dynamicExecutionTagsFunctor.addTag("newKey", "newValue");
    assertThat(result5).isEqualTo("newKey:newValue");
    verifyUpdateCall("newKey", "newValue");
    reset(pmsExecutionSummaryService);

    // Test 6: Add tag with leading and trailing spaces (should be trimmed)
    PipelineExecutionSummaryEntity entity6 = createEntityWithTags(new HashSet<>());
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entity6);

    Object result6 = dynamicExecutionTagsFunctor.addTag("  keyWithSpaces  ", "  valueWithSpaces  ");
    assertThat(result6).isEqualTo("keyWithSpaces:valueWithSpaces");
    verifyUpdateCall("keyWithSpaces", "valueWithSpaces");
    reset(pmsExecutionSummaryService);

    // Test 7: Add tag with only key having spaces (should be trimmed)
    PipelineExecutionSummaryEntity entity7 = createEntityWithTags(new HashSet<>());
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entity7);

    Object result7 = dynamicExecutionTagsFunctor.addTag("  trimmedKey  ");
    assertThat(result7).isEqualTo("trimmedKey:");
    verifyUpdateCall("trimmedKey", "");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testAddTagFailureCases() {
    // Test 1: Empty key
    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTag(""))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessage("Cannot add the tag to the execution, as resolved tag key is null/empty string");

    // Test 2: Null key
    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTag(null))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessage("Cannot add the tag to the execution, as resolved tag key is null/empty string");

    // Test 3: Key too long (>1000 characters)
    String longKey = "a".repeat(1001);
    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTag(longKey, "value"))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessage("Cannot add the tag to the execution, as the provided key is longer than 1000 characters");

    // Test 4: Value too long (>1000 characters)
    String longValue = "a".repeat(1001);
    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTag("key", longValue))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessage("Cannot add the tag to the execution, as the provided value is longer than 1000 characters");

    // Test 5: Maximum tags reached (1000 tags)
    Set<String> expectedProjections = Set.of(PlanExecutionSummaryKeys.tags);
    Set<NGTag> maxTags = new HashSet<>();
    IntStream.range(0, 1000).forEach(i -> maxTags.add(NGTag.builder().key("key" + i).value("value" + i).build()));
    PipelineExecutionSummaryEntity entityWithMaxTags = createEntityWithTags(maxTags);
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entityWithMaxTags);

    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTag("newKey", "newValue"))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessage("Cannot add the tag newKey to the execution, maximum limit of 1000 tags reached");
  }

  private PipelineExecutionSummaryEntity createEntityWithTags(Set<NGTag> tags) {
    return PipelineExecutionSummaryEntity.builder().tags(tags).build();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testAddTagsListSuccessCases() {
    Set<String> expectedProjections = Set.of(PlanExecutionSummaryKeys.tags);

    // Test 1: Add multiple tags from JSON
    PipelineExecutionSummaryEntity entity1 = createEntityWithTags(new HashSet<>());
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entity1);

    String tagsJson1 = "[{\"key\":\"tag1\",\"value\":\"val1\"},{\"key\":\"tag2\",\"value\":\"val2\"}]";
    Object result1 = dynamicExecutionTagsFunctor.addTagsList(tagsJson1);
    assertThat(result1).isEqualTo("tag1:val1, tag2:val2");
    reset(pmsExecutionSummaryService);

    // Test 2: Add tags with empty values
    PipelineExecutionSummaryEntity entity2 = createEntityWithTags(new HashSet<>());
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entity2);

    String tagsJson2 = "[{\"key\":\"environment\",\"value\":\"production\"},{\"key\":\"team\",\"value\":\"\"}]";
    Object result2 = dynamicExecutionTagsFunctor.addTagsList(tagsJson2);
    assertThat(result2).isEqualTo("environment:production, team:");
    reset(pmsExecutionSummaryService);

    // Test 3: Add single tag via list
    PipelineExecutionSummaryEntity entity3 = createEntityWithTags(new HashSet<>());
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entity3);

    String tagsJson3 = "[{\"key\":\"single\",\"value\":\"tag\"}]";
    Object result3 = dynamicExecutionTagsFunctor.addTagsList(tagsJson3);
    assertThat(result3).isEqualTo("single:tag");
    reset(pmsExecutionSummaryService);

    // Test 4: Add tags with spaces (should be trimmed)
    PipelineExecutionSummaryEntity entity4 = createEntityWithTags(new HashSet<>());
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entity4);

    String tagsJson4 = "[{\"key\":\"  spaced  \",\"value\":\"  value  \"}]";
    Object result4 = dynamicExecutionTagsFunctor.addTagsList(tagsJson4);
    assertThat(result4).isEqualTo("spaced:value");
    reset(pmsExecutionSummaryService);

    // Test 5: Add multiple tags when some exist (999 + 1)
    Set<NGTag> nearMaxTags = new HashSet<>();
    IntStream.range(0, 999).forEach(i -> nearMaxTags.add(NGTag.builder().key("key" + i).value("value" + i).build()));
    PipelineExecutionSummaryEntity entity5 = createEntityWithTags(nearMaxTags);
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entity5);

    String tagsJson5 = "[{\"key\":\"newTag\",\"value\":\"newValue\"}]";
    Object result5 = dynamicExecutionTagsFunctor.addTagsList(tagsJson5);
    assertThat(result5).isEqualTo("newTag:newValue");
    reset(pmsExecutionSummaryService);

    // Test 6: Add tags with maximum allowed key and value length
    String maxKey = "a".repeat(1000);
    String maxValue = "b".repeat(1000);
    PipelineExecutionSummaryEntity entity6 = createEntityWithTags(new HashSet<>());
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entity6);

    String tagsJson6 = String.format("[{\"key\":\"%s\",\"value\":\"%s\"}]", maxKey, maxValue);
    Object result6 = dynamicExecutionTagsFunctor.addTagsList(tagsJson6);
    assertThat(result6).isEqualTo(maxKey + ":" + maxValue);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testAddTagsListFailureCases() {
    Set<String> expectedProjections = Set.of(PlanExecutionSummaryKeys.tags);

    // Test 1: Null/empty JSON string
    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTagsList(null))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessage("Cannot add tags to the execution, as resolved tags JSON is null/empty string");

    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTagsList(""))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessage("Cannot add tags to the execution, as resolved tags JSON is null/empty string");

    // Test 2: Invalid JSON format
    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTagsList("invalid json"))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessageContaining("Cannot parse tags JSON");

    // Test 3: Empty list
    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTagsList("[]"))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessage("Cannot add tags to the execution, as the tags list is empty");

    // Test 4: Tag with null/empty key
    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTagsList("[{\"key\":\"\",\"value\":\"val\"}]"))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessage("Cannot add tags to the execution, one or more tags have null/empty key");

    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTagsList("[{\"key\":null,\"value\":\"val\"}]"))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessage("Cannot add tags to the execution, one or more tags have null/empty key");

    // Test 5: Key too long (>1000 characters)
    String longKey = "a".repeat(1001);
    String tagsJsonLongKey = String.format("[{\"key\":\"%s\",\"value\":\"val\"}]", longKey);
    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTagsList(tagsJsonLongKey))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessage("Cannot add the tag to the execution, as the provided key is longer than 1000 characters");

    // Test 6: Value too long (>1000 characters)
    String longValue = "a".repeat(1001);
    String tagsJsonLongValue = String.format("[{\"key\":\"key\",\"value\":\"%s\"}]", longValue);
    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTagsList(tagsJsonLongValue))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessage("Cannot add the tag to the execution, as the provided value is longer than 1000 characters");

    // Test 7: Maximum tags limit reached (1000 tags)
    Set<NGTag> maxTags = new HashSet<>();
    IntStream.range(0, 1000).forEach(i -> maxTags.add(NGTag.builder().key("key" + i).value("value" + i).build()));
    PipelineExecutionSummaryEntity entityWithMaxTags = createEntityWithTags(maxTags);
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entityWithMaxTags);

    String tagsJson = "[{\"key\":\"newKey\",\"value\":\"newValue\"}]";
    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTagsList(tagsJson))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessageContaining("Cannot add 1 tags to the execution")
        .hasMessageContaining("Current count: 1000")
        .hasMessageContaining("Maximum limit: 1000");

    // Test 8: Adding multiple tags exceeds limit (998 existing + 3 new)
    Set<NGTag> nearMaxTags = new HashSet<>();
    IntStream.range(0, 998).forEach(i -> nearMaxTags.add(NGTag.builder().key("key" + i).value("value" + i).build()));
    PipelineExecutionSummaryEntity entityNearMax = createEntityWithTags(nearMaxTags);
    when(pmsExecutionService.fetchExecutionSummaryFromDb(eq(PLAN_EXECUTION_ID), eq(expectedProjections)))
        .thenReturn(entityNearMax);

    String tagsJsonMultiple = "[{\"key\":\"tag1\",\"value\":\"val1\"},{\"key\":\"tag2\",\"value\":\"val2\"},{\"key\":"
        + "\"tag3\",\"value\":\"val3\"}]";
    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTagsList(tagsJsonMultiple))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessageContaining("Cannot add 3 tags to the execution")
        .hasMessageContaining("Current count: 998")
        .hasMessageContaining("Maximum limit: 1000");

    // Test 9: Malformed JSON (missing required fields)
    assertThatThrownBy(() -> dynamicExecutionTagsFunctor.addTagsList("[{\"wrongField\":\"value\"}]"))
        .isInstanceOf(EngineFunctorException.class)
        .hasMessage("Cannot add tags to the execution, one or more tags have null/empty key");
  }

  private void verifyUpdateCall(String expectedKey, String expectedValue) {
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    verify(pmsExecutionSummaryService).update(eq(PLAN_EXECUTION_ID), updateCaptor.capture());

    Update capturedUpdate = updateCaptor.getValue();
    NGTag expectedTag = NGTag.builder().key(expectedKey).value(expectedValue).build();

    // Verify the update object is not null
    assertThat(capturedUpdate).isNotNull();

    // Verify the update contains an addToSet operation for tags field
    assertThat(capturedUpdate.toString()).contains("$addToSet");
    assertThat(capturedUpdate.toString()).contains("tags");

    // Create a new Update object with the same operation to compare
    Update expectedUpdate = new Update();
    expectedUpdate.addToSet(PlanExecutionSummaryKeys.tags, expectedTag);

    // Compare the string representations to verify the same operation
    assertThat(capturedUpdate.toString()).isEqualTo(expectedUpdate.toString());
  }
}
