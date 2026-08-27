/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.outbox;

import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.execution.cdc.PipelineExecutionCDCEnrichment;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineEndEventKafkaSenderTest extends CategoryTest {
  private PipelineEndEventKafkaSender sender;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
    sender = new PipelineEndEventKafkaSender();
  }

  // -----------------------------------------------------------------------
  // buildPipelineCDCEnrichment — via reflective helper to keep the test clean
  // -----------------------------------------------------------------------

  private PipelineExecutionCDCEnrichment buildEnrichment(PipelineExecutionSummaryEntity entity) throws Exception {
    java.lang.reflect.Method method = PipelineEndEventKafkaSender.class.getDeclaredMethod(
        "buildPipelineCDCEnrichment", PipelineExecutionSummaryEntity.class);
    method.setAccessible(true);
    return (PipelineExecutionCDCEnrichment) method.invoke(sender, entity);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildPipelineCDCEnrichment_nullSummary_returnsEmptyEnrichment() throws Exception {
    // When
    PipelineExecutionCDCEnrichment enrichment = buildEnrichment(null);

    // Then: all fields should be null / default — no NPE thrown
    assertThat(enrichment).isNotNull();
    assertThat(enrichment.getRunSequence()).isNull();
    assertThat(enrichment.getTriggerType()).isNull();
    assertThat(enrichment.getTriggeredById()).isNull();
    assertThat(enrichment.getTriggeredByIdentifier()).isNull();
    assertThat(enrichment.getExecutedModules()).isNull();
    assertThat(enrichment.getDeleted()).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildPipelineCDCEnrichment_allFieldsPopulated() throws Exception {
    // Given
    TriggeredBy triggeredBy = TriggeredBy.newBuilder().setUuid("user-uuid-123").setIdentifier("admin").build();
    ExecutionTriggerInfo triggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.MANUAL).setTriggeredBy(triggeredBy).build();

    Set<String> modules = new HashSet<>(Arrays.asList("CD", "CI"));

    PipelineExecutionSummaryEntity summary = PipelineExecutionSummaryEntity.builder()
                                                 .runSequence(42)
                                                 .executionTriggerInfo(triggerInfo)
                                                 .executedModules(modules)
                                                 .pipelineDeleted(false)
                                                 .build();

    // When
    PipelineExecutionCDCEnrichment enrichment = buildEnrichment(summary);

    // Then
    assertThat(enrichment.getRunSequence()).isEqualTo(42);
    assertThat(enrichment.getTriggerType()).isEqualTo("MANUAL");
    assertThat(enrichment.getTriggeredById()).isEqualTo("user-uuid-123");
    assertThat(enrichment.getTriggeredByIdentifier()).isEqualTo("admin");
    assertThat(enrichment.getExecutedModules()).containsExactlyInAnyOrder("CD", "CI");
    assertThat(enrichment.getDeleted()).isFalse();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildPipelineCDCEnrichment_webhookTriggerType() throws Exception {
    // Given
    TriggeredBy triggeredBy = TriggeredBy.newBuilder().setUuid("webhook-uuid").setIdentifier("trigger1").build();
    ExecutionTriggerInfo triggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.WEBHOOK).setTriggeredBy(triggeredBy).build();

    PipelineExecutionSummaryEntity summary =
        PipelineExecutionSummaryEntity.builder().runSequence(1).executionTriggerInfo(triggerInfo).build();

    // When
    PipelineExecutionCDCEnrichment enrichment = buildEnrichment(summary);

    // Then
    assertThat(enrichment.getTriggerType()).isEqualTo("WEBHOOK");
    assertThat(enrichment.getTriggeredById()).isEqualTo("webhook-uuid");
    assertThat(enrichment.getTriggeredByIdentifier()).isEqualTo("trigger1");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildPipelineCDCEnrichment_noTriggerInfo_triggerFieldsNull() throws Exception {
    // Given: executionTriggerInfo is null (e.g., older record)
    PipelineExecutionSummaryEntity summary =
        PipelineExecutionSummaryEntity.builder().runSequence(5).pipelineDeleted(false).build();

    // When
    PipelineExecutionCDCEnrichment enrichment = buildEnrichment(summary);

    // Then: trigger fields are absent; other fields are populated
    assertThat(enrichment.getRunSequence()).isEqualTo(5);
    assertThat(enrichment.getTriggerType()).isNull();
    assertThat(enrichment.getTriggeredById()).isNull();
    assertThat(enrichment.getTriggeredByIdentifier()).isNull();
    assertThat(enrichment.getDeleted()).isFalse();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildPipelineCDCEnrichment_emptyExecutedModules_moduleListNull() throws Exception {
    // Given: executedModules is empty set — should not populate the list (to avoid empty array in CDC)
    PipelineExecutionSummaryEntity summary =
        PipelineExecutionSummaryEntity.builder().runSequence(3).executedModules(new HashSet<>()).build();

    // When
    PipelineExecutionCDCEnrichment enrichment = buildEnrichment(summary);

    // Then: executedModules should remain null (empty set is treated as "not set")
    assertThat(enrichment.getExecutedModules()).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildPipelineCDCEnrichment_pipelineDeleted_true() throws Exception {
    // Given: pipeline was deleted
    PipelineExecutionSummaryEntity summary =
        PipelineExecutionSummaryEntity.builder().runSequence(10).pipelineDeleted(true).build();

    // When
    PipelineExecutionCDCEnrichment enrichment = buildEnrichment(summary);

    // Then
    assertThat(enrichment.getDeleted()).isTrue();
    assertThat(enrichment.getRunSequence()).isEqualTo(10);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildPipelineCDCEnrichment_multipleModules() throws Exception {
    // Given: three executed modules
    Set<String> modules = new HashSet<>(Arrays.asList("CD", "CI", "STO"));
    PipelineExecutionSummaryEntity summary =
        PipelineExecutionSummaryEntity.builder().runSequence(2).executedModules(modules).build();

    // When
    PipelineExecutionCDCEnrichment enrichment = buildEnrichment(summary);

    // Then: all three modules included
    assertThat(enrichment.getExecutedModules()).hasSize(3);
    assertThat(enrichment.getExecutedModules()).containsExactlyInAnyOrder("CD", "CI", "STO");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildPipelineCDCEnrichment_noTriggeredByInTriggerInfo_triggeredByFieldsNull() throws Exception {
    // Given: triggerType is set but TriggeredBy message is absent
    ExecutionTriggerInfo triggerInfo =
        ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.SCHEDULER_CRON).build();
    PipelineExecutionSummaryEntity summary =
        PipelineExecutionSummaryEntity.builder().runSequence(3).executionTriggerInfo(triggerInfo).build();

    // When
    PipelineExecutionCDCEnrichment enrichment = buildEnrichment(summary);

    // Then: triggerType populated; triggeredBy fields absent
    assertThat(enrichment.getTriggerType()).isEqualTo("SCHEDULER_CRON");
    assertThat(enrichment.getTriggeredById()).isNull();
    assertThat(enrichment.getTriggeredByIdentifier()).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testBuildPipelineCDCEnrichment_runSequenceDefault_returnsZero() throws Exception {
    // Given: PipelineExecutionSummaryEntity with default runSequence (0)
    PipelineExecutionSummaryEntity summary = PipelineExecutionSummaryEntity.builder().build();

    // When
    PipelineExecutionCDCEnrichment enrichment = buildEnrichment(summary);

    // Then: default @Builder.Default value is 0
    assertThat(enrichment.getRunSequence()).isEqualTo(0);
  }
}
