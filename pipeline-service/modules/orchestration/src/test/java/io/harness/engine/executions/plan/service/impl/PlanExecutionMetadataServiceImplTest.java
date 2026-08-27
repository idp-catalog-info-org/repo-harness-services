/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.plan.service.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.YAGYANSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.dataretention.PipelineRetentionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.metrics.HarnessMetricRegistry;
import io.harness.metrics.service.api.MetricService;
import io.harness.repositories.planexecution.PlanExecutionMetadataRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class PlanExecutionMetadataServiceImplTest extends OrchestrationTestBase {
  @Inject private PlanExecutionMetadataRepository planExecutionMetadataRepository;
  @Mock private PmsFeatureFlagService pmsFeatureFlagService;
  @Mock PipelineRetentionService pipelineRetentionService;
  @Mock private HarnessMetricRegistry metricRegistry;
  @Mock private MetricService metricService;
  @Inject @InjectMocks private PlanExecutionMetadataService planExecutionMetadataService;
  @Inject private PlanExecutionService planExecutionService;
  String accountIdentifier = "accountIdentifier";
  private static final long VALID_UNTIL_30D_LB = 27;
  private static final long VALID_UNTIL_30D_UB = 31;
  private static final long VALID_UNTIL_180D_LB = 179;
  private static final long VALID_UNTIL_180D_UB = 183;

  @Test

  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void findByPlanExecutionId() {
    String planExecutionId = generateUuid();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().accountIdentifier(accountIdentifier).planExecutionId(planExecutionId).build();
    planExecutionMetadataRepository.save(planExecutionMetadata);

    Optional<PlanExecutionMetadata> saved =
        planExecutionMetadataService.findByPlanExecutionId(accountIdentifier, planExecutionId);
    assertThat(saved.isPresent()).isTrue();
    assertThat(saved.get().getPlanExecutionId()).isEqualTo(planExecutionId);
  }

  @Test
  @Owner(developers = YAGYANSH)
  @Category(UnitTests.class)
  public void getYaml() {
    String planExecutionId = generateUuid();
    String yaml = "pipeline :\n  identifier: pipelineId";
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .accountIdentifier(accountIdentifier)
                                                      .planExecutionId(planExecutionId)
                                                      .yaml(yaml)
                                                      .build();
    planExecutionMetadataRepository.save(planExecutionMetadata);
    Optional<String> getYaml = planExecutionMetadataService.getYaml(accountIdentifier, planExecutionId);
    assertThat(getYaml.isPresent()).isEqualTo(true);
    assertThat(getYaml.get()).isEqualTo(yaml);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void save() {
    String planExecutionId = generateUuid();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().planExecutionId(planExecutionId).build();
    planExecutionMetadataService.save(planExecutionMetadata);

    Optional<PlanExecutionMetadata> saved = planExecutionMetadataRepository.findById(planExecutionMetadata.getUuid());
    assertThat(saved.isPresent()).isTrue();
    long validUntilDays = Duration.between(Instant.now(), saved.get().getValidUntil().toInstant()).toDays();
    assertThat(validUntilDays).isGreaterThanOrEqualTo(VALID_UNTIL_180D_LB);
    assertThat(validUntilDays).isLessThanOrEqualTo(VALID_UNTIL_180D_UB);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void saveWithFFEnabled() {
    String planExecutionId = generateUuid();
    when(pmsFeatureFlagService.isEnabled(accountIdentifier, FeatureName.CDS_CUSTOMIZE_PIPELINE_TTL)).thenReturn(true);
    when(pipelineRetentionService.getRetentionPeriodInMonths(accountIdentifier)).thenReturn(1);
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().accountIdentifier(accountIdentifier).planExecutionId(planExecutionId).build();
    planExecutionMetadataService.save(planExecutionMetadata);

    Optional<PlanExecutionMetadata> saved = planExecutionMetadataRepository.findById(planExecutionMetadata.getUuid());
    assertThat(saved.isPresent()).isTrue();
    long validUntilDays = Duration.between(Instant.now(), saved.get().getValidUntil().toInstant()).toDays();
    assertThat(validUntilDays).isGreaterThanOrEqualTo(VALID_UNTIL_30D_LB);
    assertThat(validUntilDays).isLessThanOrEqualTo(VALID_UNTIL_30D_UB);
    verify(pipelineRetentionService).getRetentionPeriodInMonths(any());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testDeleteMetadataForGivenPlanExecutionIds() {
    String planExecutionId = generateUuid();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().accountIdentifier(accountIdentifier).planExecutionId(planExecutionId).build();
    planExecutionMetadataRepository.save(planExecutionMetadata);

    planExecutionMetadataService.deleteMetadataForGivenPlanExecutionIds(Sets.newHashSet(planExecutionId));
    Optional<PlanExecutionMetadata> optionalPlanExecutionMetadata =
        planExecutionMetadataService.findByPlanExecutionId(accountIdentifier, planExecutionId);
    assertThat(optionalPlanExecutionMetadata).isEmpty();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testUpdateTTLForGivenPlanExecutionIds() {
    String planExecutionId = generateUuid();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().accountIdentifier(accountIdentifier).planExecutionId(planExecutionId).build();
    planExecutionMetadataRepository.save(planExecutionMetadata);

    Date ttlExpiry = Date.from(OffsetDateTime.now().plus(Duration.ofMinutes(30)).toInstant());
    planExecutionMetadataService.updateTTL(planExecutionId, ttlExpiry);
    Optional<PlanExecutionMetadata> optionalPlanExecutionMetadata =
        planExecutionMetadataService.findByPlanExecutionId(accountIdentifier, planExecutionId);
    assertThat(optionalPlanExecutionMetadata.get().getValidUntil()).isEqualTo(ttlExpiry);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testDeleteMetadataForGivenPlanExecutionIds_RecordsMetric() {
    String planExecutionId = generateUuid();
    planExecutionMetadataService.deleteMetadataForGivenPlanExecutionIds(Sets.newHashSet(planExecutionId));

    verify(metricService).recordDuration(eq("pipeline_execution_metadata_deletion_time"), any(Duration.class));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testDeleteMetadataForGivenPlanExecutionIds_RecordsMetric_NULL() {
    String planExecutionId = generateUuid();
    planExecutionMetadataService.deleteMetadataForGivenPlanExecutionIds(Sets.newHashSet(planExecutionId));

    verify(metricService).recordDuration(eq("pipeline_execution_metadata_deletion_time"), any(Duration.class));
  }
}
