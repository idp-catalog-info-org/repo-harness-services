/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.resourcerestraint.service;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.distribution.constraint.Consumer.State.ACTIVE;
import static io.harness.distribution.constraint.Consumer.State.BLOCKED;
import static io.harness.distribution.constraint.Consumer.State.FINISHED;
import static io.harness.rule.OwnerRule.ALEXEI;
import static io.harness.rule.OwnerRule.ARCHIT;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.FERNANDOD;
import static io.harness.rule.OwnerRule.PRASHANT;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.OrchestrationStepsTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.distribution.constraint.Constraint;
import io.harness.distribution.constraint.Consumer.State;
import io.harness.distribution.constraint.ConsumerId;
import io.harness.distribution.constraint.RunnableConsumers;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionService;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecution;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.repositories.ResourceRestraintInstanceRepository;
import io.harness.rule.Owner;
import io.harness.steps.resourcerestraint.HoldingScope;
import io.harness.steps.resourcerestraint.beans.ResourceRestraint;
import io.harness.steps.resourcerestraint.beans.ResourceRestraintInstance;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.PIPELINE)
public class ResourceRestraintInstanceServiceImplTest extends OrchestrationStepsTestBase {
  private static final String RESOURCE_UNIT = generateUuid();

  @Inject private ResourceRestraintInstanceRepository restraintInstanceRepository;

  @Mock private PlanExecutionService planExecutionService;
  @Mock private NodeExecutionService nodeExecutionService;
  @Mock private ResourceRestraintService resourceRestraintService;
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject @InjectMocks @Spy private ResourceRestraintInstanceService resourceRestraintInstanceService;

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestSave() {
    ResourceRestraintInstance instance = ResourceRestraintInstance.builder()
                                             .releaseEntityId(generateUuid())
                                             .releaseEntityType("PLAN")
                                             .order(1)
                                             .permits(1)
                                             .resourceRestraintId(generateUuid())
                                             .state(ACTIVE)
                                             .build();

    ResourceRestraintInstance savedInstance = resourceRestraintInstanceService.save(instance);
    assertThat(savedInstance).isNotNull();
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void shouldTestDeleteInstancesForGivenReleaseType() {
    String releaseEntityId1 = generateUuid();
    savePipelineActiveInstance(releaseEntityId1, "keyA");
    savePipelineActiveInstance(releaseEntityId1, "keyA");
    savePipelineActiveInstance(releaseEntityId1, "keyB");

    String releaseEntityId2 = generateUuid();
    savePipelineActiveInstance(releaseEntityId2, "keyA");
    savePipelineActiveInstance(releaseEntityId2, "keyC");

    String releaseEntityId3 = generateUuid();
    savePipelineActiveInstance(releaseEntityId3, "keyA");

    List<ResourceRestraintInstance> allActiveAndBlockedByReleaseEntityId1 =
        resourceRestraintInstanceService.findAllActiveAndBlockedByReleaseEntityId(releaseEntityId1);
    assertThat(allActiveAndBlockedByReleaseEntityId1.size()).isEqualTo(3);
    List<ResourceRestraintInstance> allActiveAndBlockedByReleaseEntityId2 =
        resourceRestraintInstanceService.findAllActiveAndBlockedByReleaseEntityId(releaseEntityId2);
    assertThat(allActiveAndBlockedByReleaseEntityId2.size()).isEqualTo(2);

    resourceRestraintInstanceService.deleteInstancesForGivenReleaseType(
        Set.of(releaseEntityId1, releaseEntityId2), HoldingScope.PIPELINE);
    allActiveAndBlockedByReleaseEntityId1 =
        resourceRestraintInstanceService.findAllActiveAndBlockedByReleaseEntityId(releaseEntityId1);
    assertThat(allActiveAndBlockedByReleaseEntityId1.size()).isZero();
    allActiveAndBlockedByReleaseEntityId2 =
        resourceRestraintInstanceService.findAllActiveAndBlockedByReleaseEntityId(releaseEntityId2);
    assertThat(allActiveAndBlockedByReleaseEntityId2.size()).isZero();
    List<ResourceRestraintInstance> allActiveAndBlockedByReleaseEntityId3 =
        resourceRestraintInstanceService.findAllActiveAndBlockedByReleaseEntityId(releaseEntityId3);
    assertThat(allActiveAndBlockedByReleaseEntityId3.size()).isEqualTo(1);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestActivateBlockedInstance() {
    ResourceRestraintInstance instance = ResourceRestraintInstance.builder()
                                             .releaseEntityId(generateUuid())
                                             .releaseEntityType("PLAN")
                                             .resourceUnit(generateUuid())
                                             .order(1)
                                             .permits(1)
                                             .resourceRestraintId(generateUuid())
                                             .state(BLOCKED)
                                             .build();
    ResourceRestraintInstance savedInstance = resourceRestraintInstanceService.save(instance);
    assertThat(savedInstance).isNotNull();

    ResourceRestraintInstance updatedInstance = resourceRestraintInstanceService.activateBlockedInstance(
        savedInstance.getUuid(), savedInstance.getResourceUnit());
    assertThat(updatedInstance).isNotNull();
    assertThat(updatedInstance.getState()).isEqualTo(ACTIVE);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestActivateBlockedInstance_InvalidRequestException() {
    ResourceRestraintInstance instance = ResourceRestraintInstance.builder()
                                             .releaseEntityId(generateUuid())
                                             .releaseEntityType("PLAN")
                                             .resourceUnit(generateUuid())
                                             .order(1)
                                             .permits(1)
                                             .resourceRestraintId(generateUuid())
                                             .state(BLOCKED)
                                             .build();
    ResourceRestraintInstance savedInstance = resourceRestraintInstanceService.save(instance);
    assertThat(savedInstance).isNotNull();

    assertThatThrownBy(
        () -> resourceRestraintInstanceService.activateBlockedInstance(generateUuid(), savedInstance.getResourceUnit()))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageStartingWith("Cannot find ResourceRestraintInstance with id");
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestFinishActiveInstance() {
    ResourceRestraintInstance instance = ResourceRestraintInstance.builder()
                                             .releaseEntityId(generateUuid())
                                             .releaseEntityType("PLAN")
                                             .resourceUnit(generateUuid())
                                             .order(1)
                                             .permits(1)
                                             .resourceRestraintId(generateUuid())
                                             .state(ACTIVE)
                                             .build();
    ResourceRestraintInstance savedInstance = resourceRestraintInstanceService.save(instance);
    assertThat(savedInstance).isNotNull();

    ResourceRestraintInstance updatedInstance =
        resourceRestraintInstanceService.finishInstance(savedInstance.getUuid(), savedInstance.getResourceUnit());
    assertThat(updatedInstance).isNotNull();
    assertThat(updatedInstance.getState()).isEqualTo(FINISHED);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldTestFinishActiveInstance_Null() {
    ResourceRestraintInstance instance = ResourceRestraintInstance.builder()
                                             .releaseEntityId(generateUuid())
                                             .releaseEntityType("PLAN")
                                             .resourceUnit(generateUuid())
                                             .order(1)
                                             .permits(1)
                                             .resourceRestraintId(generateUuid())
                                             .state(ACTIVE)
                                             .build();
    ResourceRestraintInstance savedInstance = resourceRestraintInstanceService.save(instance);
    assertThat(savedInstance).isNotNull();

    assertThat(resourceRestraintInstanceService.finishInstance(generateUuid(), savedInstance.getResourceUnit()))
        .isNull();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldUpdateActiveConstraintsForInstance_ForPlan() {
    ResourceRestraintInstance instance = saveInstance(BLOCKED, HoldingScope.PIPELINE);
    PlanExecution planExecution = PlanExecution.builder().status(Status.SUCCEEDED).build();
    when(planExecutionService.getWithFieldsIncluded(any(), any())).thenReturn(planExecution);

    boolean isUpdated = resourceRestraintInstanceService.updateActiveConstraintsForInstance(instance);
    assertThat(isUpdated).isTrue();

    Optional<ResourceRestraintInstance> updatedInstance = restraintInstanceRepository.findById(instance.getUuid());
    assertThat(updatedInstance.isPresent()).isTrue();
    assertThat(updatedInstance.get().getState()).isEqualTo(FINISHED);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldUpdateActiveConstraintsForInstance_ForPlan_InvalidRequestException() {
    ResourceRestraintInstance instance = saveInstance(BLOCKED, HoldingScope.PIPELINE);

    when(planExecutionService.getWithFieldsIncluded(any(), any())).thenThrow(new EntityNotFoundException(""));

    boolean isUpdated = resourceRestraintInstanceService.updateActiveConstraintsForInstance(instance);
    assertThat(isUpdated).isTrue();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldUpdateActiveConstraintsForInstance_ForOther() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(generateUuid())
                            .addAllLevels(Collections.singletonList(
                                Level.newBuilder().setRuntimeId(generateUuid()).setSetupId(generateUuid()).build()))
                            .build();
    ResourceRestraintInstance instance = saveInstance(BLOCKED, HoldingScope.STAGE);

    when(nodeExecutionService.getWithFieldsIncluded(any(), any()))
        .thenReturn(
            NodeExecution.builder().ambiance(ambiance).mode(ExecutionMode.SYNC).status(Status.SUCCEEDED).build());

    boolean isUpdated = resourceRestraintInstanceService.updateActiveConstraintsForInstance(instance);
    assertThat(isUpdated).isTrue();

    Optional<ResourceRestraintInstance> updatedInstance = restraintInstanceRepository.findById(instance.getUuid());
    assertThat(updatedInstance.isPresent()).isTrue();
    assertThat(updatedInstance.get().getState()).isEqualTo(FINISHED);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldUpdateActiveConstraintsForInstance_ForOther_InvalidRequestException() {
    ResourceRestraintInstance instance = saveInstance(BLOCKED, HoldingScope.STAGE);

    doReturn(Optional.empty()).when(nodeExecutionService).getOptional(any(), any());

    boolean isUpdated = resourceRestraintInstanceService.updateActiveConstraintsForInstance(instance);
    assertThat(isUpdated).isTrue();
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldGetAllByRestraintIdAndResourceUnitAndStates() {
    ResourceRestraintInstance instance = saveInstance(ACTIVE, HoldingScope.PIPELINE);

    List<ResourceRestraintInstance> instances = new ArrayList<>();
    Stream<ResourceRestraintInstance> instancesIterators =
        resourceRestraintInstanceService.getAllByRestraintIdAndResourceUnitAndStates(
            instance.getResourceRestraintId(), instance.getResourceUnit(), new ArrayList<>(Arrays.asList(ACTIVE)));
    Iterator<ResourceRestraintInstance> instanceIterator = instancesIterators.iterator();
    while (instanceIterator.hasNext()) {
      instances.add(instanceIterator.next());
    }

    assertThat(instances).isNotEmpty();
    assertThat(instances.size()).isEqualTo(1);
    assertThat(instances).contains(instance);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldGetMaxOrder() {
    String resourceRestraintId = generateUuid();
    ResourceRestraintInstance instance = saveInstance(resourceRestraintId, ACTIVE, HoldingScope.PIPELINE, 1);
    saveInstance(resourceRestraintId, ACTIVE, HoldingScope.PIPELINE, 2);

    int maxOrder = resourceRestraintInstanceService.getMaxOrder(instance.getResourceRestraintId());
    assertThat(maxOrder).isEqualTo(2);
  }

  @Test
  @Owner(developers = ALEXEI)
  @Category(UnitTests.class)
  public void shouldGetAllCurrentlyAcquiredPermits() {
    String releaseEntityId = generateUuid();
    ResourceRestraintInstance instance = savePipelineActiveInstance(releaseEntityId, RESOURCE_UNIT);
    savePipelineActiveInstance(releaseEntityId, RESOURCE_UNIT);

    int maxOrder = resourceRestraintInstanceService.getAllCurrentlyAcquiredPermits(
        HoldingScope.valueOf(instance.getReleaseEntityType()), instance.getReleaseEntityId(),
        instance.getResourceUnit());
    assertThat(maxOrder).isEqualTo(2);
  }

  /**
   * Verify that the right repository access is made during service execution.
   */
  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetAllCurrentlyAcquiredPermitsVerifyRepositoryUsage() throws IllegalAccessException {
    ResourceRestraintInstanceServiceImpl service = new ResourceRestraintInstanceServiceImpl();
    ResourceRestraintInstanceRepository repository = mock(ResourceRestraintInstanceRepository.class);

    Field field = FieldUtils.getField(ResourceRestraintInstanceServiceImpl.class, "restraintInstanceRepository", true);
    FieldUtils.writeField(field, service, repository);

    // WE DON'T CARE ABOUT SERVICE RESULT, THE FOCUS IS THE REPOSITORY ACCESS.
    String releaseEntityId = generateUuid();
    service.getAllCurrentlyAcquiredPermits(HoldingScope.PIPELINE, releaseEntityId, RESOURCE_UNIT);

    verify(repository)
        .findByReleaseEntityTypeAndReleaseEntityIdAndResourceUnitAndState(
            HoldingScope.PIPELINE.name(), releaseEntityId, RESOURCE_UNIT, ACTIVE);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldGetAllCurrentlyAcquiredPermitsForDifferentKeys() {
    String releaseEntityId = generateUuid();
    savePipelineActiveInstance(releaseEntityId, "keyA");
    savePipelineActiveInstance(releaseEntityId, "keyA");
    savePipelineActiveInstance(releaseEntityId, "keyB");

    int permits =
        resourceRestraintInstanceService.getAllCurrentlyAcquiredPermits(HoldingScope.PIPELINE, releaseEntityId, "keyB");
    assertThat(permits).isEqualTo(1);
  }

  private ResourceRestraintInstance savePipelineActiveInstance(String releaseEntityId, String resourceUnit) {
    return saveInstance(generateUuid(), ACTIVE, HoldingScope.PIPELINE, releaseEntityId, 1, resourceUnit);
  }

  private ResourceRestraintInstance saveInstance(State state, HoldingScope scope) {
    return saveInstance(generateUuid(), state, scope,
        scope == HoldingScope.PIPELINE ? generateUuid() : generateUuid() + '|' + generateUuid(), 1, generateUuid());
  }

  private ResourceRestraintInstance saveInstance(
      String resourceRestraintId, State state, HoldingScope scope, int order) {
    return saveInstance(resourceRestraintId, state, scope,
        scope == HoldingScope.PIPELINE ? generateUuid() : generateUuid() + '|' + generateUuid(), order, generateUuid());
  }

  private ResourceRestraintInstance saveInstance(String resourceRestraintId, State state,
      HoldingScope releaseEntityType, String releaseEntityId, int order, String resourceUnit) {
    ResourceRestraintInstance instance = ResourceRestraintInstance.builder()
                                             .releaseEntityId(releaseEntityId)
                                             .releaseEntityType(releaseEntityType.name())
                                             .resourceUnit(resourceUnit)
                                             .order(order)
                                             .permits(1)
                                             .resourceRestraintId(resourceRestraintId)
                                             .state(state)
                                             .build();
    ResourceRestraintInstance savedInstance = resourceRestraintInstanceService.save(instance);
    assertThat(savedInstance).isNotNull();

    return savedInstance;
  }

  private ResourceRestraintInstance saveInstance(
      String resourceRestraintId, State state, HoldingScope releaseEntityType, String releaseEntityId, String fqn) {
    ResourceRestraintInstance instance = ResourceRestraintInstance.builder()
                                             .resourceRestraintId(resourceRestraintId)
                                             .releaseEntityId(releaseEntityId)
                                             .releaseEntityType(releaseEntityType.name())
                                             .permits(1)
                                             .state(state)
                                             .fqn(fqn)
                                             .build();
    ResourceRestraintInstance savedInstance = resourceRestraintInstanceService.save(instance);
    assertThat(savedInstance).isNotNull();
    return savedInstance;
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testProcessRestraintBlockedInstance() {
    when(pmsFeatureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_RESTRAINT_UNBLOCKING_V2))).thenReturn(false);
    ResourceRestraintInstance instance = getResourceRestraint(BLOCKED);
    ResourceRestraint resourceRestraint =
        ResourceRestraint.builder().uuid(generateUuid()).capacity(1).strategy(Constraint.Strategy.FIFO).build();
    when(resourceRestraintService.get(instance.getResourceRestraintId())).thenReturn(resourceRestraint);
    resourceRestraintInstanceService.processRestraint(instance);
    verify(resourceRestraintInstanceService)
        .updateBlockedConstraints(Sets.newHashSet(instance.getResourceRestraintId()));
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testProcessRestraintBlockedInstanceV2() {
    when(pmsFeatureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_RESTRAINT_UNBLOCKING_V2))).thenReturn(true);
    ResourceRestraintInstance instance = getResourceRestraint(BLOCKED);
    ResourceRestraint resourceRestraint =
        ResourceRestraint.builder().uuid(generateUuid()).capacity(1).strategy(Constraint.Strategy.FIFO).build();
    when(resourceRestraintService.get(instance.getResourceRestraintId())).thenReturn(resourceRestraint);
    resourceRestraintInstanceService.processRestraint(instance);
    verify(resourceRestraintInstanceService).updateBlockedConstraints(instance);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testProcessRestraintActiveInstance() {
    when(pmsFeatureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_RESTRAINT_UNBLOCKING_V2))).thenReturn(false);
    ResourceRestraintInstance instance = getResourceRestraint(ACTIVE);
    ResourceRestraint resourceRestraint =
        ResourceRestraint.builder().uuid(generateUuid()).capacity(1).strategy(Constraint.Strategy.FIFO).build();
    when(resourceRestraintService.get(instance.getResourceRestraintId())).thenReturn(resourceRestraint);
    Set<String> constraintIds = Sets.newHashSet(instance.getResourceRestraintId());
    doNothing().when(resourceRestraintInstanceService).updateBlockedConstraints(constraintIds);
    doReturn(true).when(resourceRestraintInstanceService).updateActiveConstraintsForInstance(eq(instance));
    resourceRestraintInstanceService.processRestraint(instance);
    verify(resourceRestraintInstanceService).updateActiveConstraintsForInstance(eq(instance));
    verify(resourceRestraintInstanceService).updateBlockedConstraints(constraintIds);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testProcessRestraintActiveInstanceV2() {
    when(pmsFeatureFlagHelper.isEnabled(any(), eq(FeatureName.PIPE_RESTRAINT_UNBLOCKING_V2))).thenReturn(true);
    ResourceRestraintInstance instance = getResourceRestraint(ACTIVE);
    ResourceRestraint resourceRestraint =
        ResourceRestraint.builder().uuid(generateUuid()).capacity(1).strategy(Constraint.Strategy.FIFO).build();
    when(resourceRestraintService.get(instance.getResourceRestraintId())).thenReturn(resourceRestraint);
    doNothing().when(resourceRestraintInstanceService).updateBlockedConstraints(instance);
    doReturn(true).when(resourceRestraintInstanceService).updateActiveConstraintsForInstance(eq(instance));
    resourceRestraintInstanceService.processRestraint(instance);
    verify(resourceRestraintInstanceService).updateActiveConstraintsForInstance(eq(instance));
    verify(resourceRestraintInstanceService).updateBlockedConstraints(instance);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void testActiveInstance_WhenNoInstancesAreUpdated() {
    ResourceRestraintInstance instance = getResourceRestraint(ACTIVE);
    doReturn(false).when(resourceRestraintInstanceService).updateActiveConstraintsForInstance(eq(instance));
    resourceRestraintInstanceService.processRestraint(instance);
    verify(resourceRestraintInstanceService, never())
        .updateBlockedConstraints(Sets.newHashSet(instance.getResourceRestraintId()));
  }

  private ResourceRestraintInstance getResourceRestraint(State state) {
    return ResourceRestraintInstance.builder()
        .resourceRestraintId(generateUuid())
        .state(state)
        .uuid(generateUuid())
        .build();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldCreateAbstraction() {
    ResourceRestraint resourceRestraint =
        ResourceRestraint.builder().uuid("UUID").capacity(1010).strategy(Constraint.Strategy.ASAP).build();
    Constraint constraint = resourceRestraintInstanceService.createAbstraction(resourceRestraint);
    assertThat(constraint).isNotNull();
    assertThat(constraint.getId()).isNotNull();
    assertThat(constraint.getId().getValue()).isEqualTo("UUID");
    assertThat(constraint.getSpec()).isNotNull();
    assertThat(constraint.getSpec().getLimits()).isEqualTo(1010);
    assertThat(constraint.getSpec().getStrategy()).isEqualTo(Constraint.Strategy.ASAP);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldUpdateBlockedConstraintsForSpecificUnit() {
    String resourceRestraintId = generateUuid();
    String resourceUnit = generateUuid();
    String releaseEntityId = generateUuid();

    ResourceRestraint resourceRestraint =
        ResourceRestraint.builder().uuid(resourceRestraintId).capacity(1).strategy(Constraint.Strategy.FIFO).build();

    ResourceRestraintInstance blockedInstance =
        saveInstance(resourceRestraintId, BLOCKED, HoldingScope.PIPELINE, releaseEntityId, 1, resourceUnit);

    when(resourceRestraintService.get(resourceRestraintId)).thenReturn(resourceRestraint);

    Constraint constraint = resourceRestraintInstanceService.createAbstraction(resourceRestraint);
    RunnableConsumers runnableConsumers =
        RunnableConsumers.builder()
            .consumerIds(Collections.singletonList(new ConsumerId(blockedInstance.getUuid())))
            .build();

    resourceRestraintInstanceService.updateBlockedConstraints(blockedInstance);

    Optional<ResourceRestraintInstance> updatedInstance =
        restraintInstanceRepository.findById(blockedInstance.getUuid());
    assertThat(updatedInstance).isPresent();
    assertThat(updatedInstance.get().getState()).isEqualTo(ACTIVE);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldNotProcessUnrelatedInstances() {
    String resourceRestraintId = generateUuid();
    String resourceUnit1 = "unit-1";
    String resourceUnit2 = "unit-2";
    String releaseEntityId = generateUuid();

    ResourceRestraint resourceRestraint =
        ResourceRestraint.builder().uuid(resourceRestraintId).capacity(1).strategy(Constraint.Strategy.FIFO).build();

    ResourceRestraintInstance instance1 =
        saveInstance(resourceRestraintId, BLOCKED, HoldingScope.PIPELINE, releaseEntityId, 1, resourceUnit1);
    ResourceRestraintInstance instance2 =
        saveInstance(resourceRestraintId, BLOCKED, HoldingScope.PIPELINE, releaseEntityId, 2, resourceUnit2);

    when(resourceRestraintService.get(resourceRestraintId)).thenReturn(resourceRestraint);

    resourceRestraintInstanceService.updateBlockedConstraints(instance1);

    Optional<ResourceRestraintInstance> updatedInstance1 = restraintInstanceRepository.findById(instance1.getUuid());
    assertThat(updatedInstance1).isPresent();
    assertThat(updatedInstance1.get().getState()).isEqualTo(ACTIVE);

    Optional<ResourceRestraintInstance> updatedInstance2 = restraintInstanceRepository.findById(instance2.getUuid());
    assertThat(updatedInstance2).isPresent();
    assertThat(updatedInstance2.get().getState()).isEqualTo(BLOCKED);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void shouldProcessMultipleConsumersForSameUnit() {
    String resourceRestraintId = generateUuid();
    String resourceUnit = generateUuid();
    String releaseEntityId1 = generateUuid();
    String releaseEntityId2 = generateUuid();

    ResourceRestraint resourceRestraint =
        ResourceRestraint.builder().uuid(resourceRestraintId).capacity(2).strategy(Constraint.Strategy.FIFO).build();

    ResourceRestraintInstance instance1 =
        saveInstance(resourceRestraintId, BLOCKED, HoldingScope.PIPELINE, releaseEntityId1, 1, resourceUnit);
    ResourceRestraintInstance instance2 =
        saveInstance(resourceRestraintId, BLOCKED, HoldingScope.PIPELINE, releaseEntityId2, 2, resourceUnit);

    when(resourceRestraintService.get(resourceRestraintId)).thenReturn(resourceRestraint);

    resourceRestraintInstanceService.updateBlockedConstraints(instance1);

    assertThat(restraintInstanceRepository.findById(instance1.getUuid()))
        .isPresent()
        .get()
        .extracting(ResourceRestraintInstance::getState)
        .isEqualTo(ACTIVE);

    resourceRestraintInstanceService.updateBlockedConstraints(instance2);

    assertThat(restraintInstanceRepository.findById(instance2.getUuid()))
        .isPresent()
        .get()
        .extracting(ResourceRestraintInstance::getState)
        .isEqualTo(ACTIVE);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldFindByReleaseEntityIdAndFqn_WhenSingleActiveInstanceExists() {
    String releaseEntityId = generateUuid();
    String fqn = "pipeline.stages.stage1.steps.step1";
    String resourceRestraintId = generateUuid();
    saveInstance(resourceRestraintId, ACTIVE, HoldingScope.STAGE, releaseEntityId, fqn);
    ResourceRestraintInstance result =
        resourceRestraintInstanceService.findByReleaseEntityIdAndFqn(releaseEntityId, fqn);
    assertThat(result).isNotNull();
    assertThat(result.getState()).isEqualTo(ACTIVE);
    assertThat(result.getFqn()).isEqualTo(fqn);
    assertThat(result.getResourceRestraintId()).isEqualTo(resourceRestraintId);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldFindByReleaseEntityIdAndFqn_WhenSingleBlockedInstanceExists() {
    String releaseEntityId = generateUuid();
    String fqn = "pipeline.stages.stage1.steps.step1";
    String resourceRestraintId = generateUuid();
    saveInstance(resourceRestraintId, BLOCKED, HoldingScope.STAGE, releaseEntityId, fqn);
    ResourceRestraintInstance result =
        resourceRestraintInstanceService.findByReleaseEntityIdAndFqn(releaseEntityId, fqn);
    assertThat(result).isNotNull();
    assertThat(result.getState()).isEqualTo(BLOCKED);
    assertThat(result.getFqn()).isEqualTo(fqn);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldReturnNull_WhenNoInstancesFound() {
    String releaseEntityId = generateUuid();
    String fqn = "pipeline.stages.stage1.steps.step1";
    ResourceRestraintInstance result =
        resourceRestraintInstanceService.findByReleaseEntityIdAndFqn(releaseEntityId, fqn);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldReturnNull_WhenMultipleInstancesFound() {
    ResourceRestraintInstanceRepository resourceRestraintInstanceRepository =
        mock(ResourceRestraintInstanceRepository.class);
    String releaseEntityId = generateUuid();
    String fqn = "pipeline.stages.stage1.steps.step1";
    String resourceRestraintId = generateUuid();
    saveInstance(resourceRestraintId, ACTIVE, HoldingScope.STAGE, releaseEntityId, fqn);
    saveInstance(resourceRestraintId, BLOCKED, HoldingScope.STAGE, releaseEntityId, fqn);
    ResourceRestraintInstance result =
        resourceRestraintInstanceService.findByReleaseEntityIdAndFqn(releaseEntityId, fqn);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void shouldIgnoreInstancesWithNonMatchingStates() {
    String releaseEntityId = generateUuid();
    String fqn = "pipeline.stages.stage1.steps.step1";
    ResourceRestraintInstance result =
        resourceRestraintInstanceService.findByReleaseEntityIdAndFqn(releaseEntityId, fqn);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldUpdateActiveConstraintsForStage_WhenStageStuckInRunningButPlanIsTerminal() {
    // Regression test for PIPE-35548: stage stuck in RUNNING after execution fails
    // should release permit when parent PlanExecution is in a terminal state
    String planExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .addAllLevels(Collections.singletonList(
                Level.newBuilder().setRuntimeId(stageNodeExecutionId).setSetupId(generateUuid()).build()))
            .build();

    ResourceRestraintInstance instance = saveInstance(ACTIVE, HoldingScope.STAGE);
    instance.setReleaseEntityId(stageNodeExecutionId);
    restraintInstanceRepository.save(instance);

    NodeExecution stuckStageNode = NodeExecution.builder()
                                       .uuid(stageNodeExecutionId)
                                       .ambiance(ambiance)
                                       .mode(ExecutionMode.SYNC)
                                       .status(Status.RUNNING)
                                       .build();

    PlanExecution terminalPlan = PlanExecution.builder().uuid(planExecutionId).status(Status.FAILED).build();

    when(nodeExecutionService.getOptional(eq(stageNodeExecutionId), any())).thenReturn(Optional.of(stuckStageNode));
    when(planExecutionService.getWithFieldsIncluded(eq(planExecutionId), any())).thenReturn(terminalPlan);

    boolean isUpdated = resourceRestraintInstanceService.updateActiveConstraintsForInstance(instance);
    assertThat(isUpdated).isTrue();

    Optional<ResourceRestraintInstance> updatedInstance = restraintInstanceRepository.findById(instance.getUuid());
    assertThat(updatedInstance.isPresent()).isTrue();
    assertThat(updatedInstance.get().getState()).isEqualTo(FINISHED);
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void shouldNotUpdateActiveConstraintsForStage_WhenBothStageAndPlanAreRunning() {
    // Normal case: stage RUNNING and plan RUNNING should NOT release permit
    String planExecutionId = generateUuid();
    String stageNodeExecutionId = generateUuid();

    Ambiance ambiance =
        Ambiance.newBuilder()
            .setPlanExecutionId(planExecutionId)
            .addAllLevels(Collections.singletonList(
                Level.newBuilder().setRuntimeId(stageNodeExecutionId).setSetupId(generateUuid()).build()))
            .build();

    ResourceRestraintInstance instance = saveInstance(ACTIVE, HoldingScope.STAGE);
    instance.setReleaseEntityId(stageNodeExecutionId);
    restraintInstanceRepository.save(instance);

    NodeExecution runningStageNode = NodeExecution.builder()
                                         .uuid(stageNodeExecutionId)
                                         .ambiance(ambiance)
                                         .mode(ExecutionMode.SYNC)
                                         .status(Status.RUNNING)
                                         .build();

    PlanExecution runningPlan = PlanExecution.builder().uuid(planExecutionId).status(Status.RUNNING).build();

    when(nodeExecutionService.getOptional(eq(stageNodeExecutionId), any())).thenReturn(Optional.of(runningStageNode));
    when(planExecutionService.getWithFieldsIncluded(eq(planExecutionId), any())).thenReturn(runningPlan);

    boolean isUpdated = resourceRestraintInstanceService.updateActiveConstraintsForInstance(instance);
    assertThat(isUpdated).isFalse();

    Optional<ResourceRestraintInstance> updatedInstance = restraintInstanceRepository.findById(instance.getUuid());
    assertThat(updatedInstance.isPresent()).isTrue();
    assertThat(updatedInstance.get().getState()).isEqualTo(ACTIVE);
  }
}
