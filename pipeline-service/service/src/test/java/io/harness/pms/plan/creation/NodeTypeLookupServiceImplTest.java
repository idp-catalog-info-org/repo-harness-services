/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.creation;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ABHISHEK;
import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.plan.Node;
import io.harness.plan.PlanNode;
import io.harness.pms.contracts.steps.SdkStep;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.creation.lookup.NodeTypeLookupServiceImpl;
import io.harness.pms.sdk.PmsSdkInstanceService;
import io.harness.pms.sdk.helper.PmsSdkHelper;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class NodeTypeLookupServiceImplTest extends CategoryTest {
  @InjectMocks NodeTypeLookupServiceImpl nodeTypeLookupService;
  @Mock PmsSdkInstanceService pmsSdkInstanceService;
  @Mock PmsSdkHelper pmsSdkHelper;

  private static final String DB_SCHEMA_APPLY = "DBSchemaApply";
  private static final String DB_SCHEMA_ROLLBACK = "DBSchemaRollback";
  private static final String DB_COMMAND = "DBCommand";

  private static final Map<String, Integer> PRIORITY_MAP = Map.of("pms", 1, "cf", 2, "cd", 3, "iacm", 5, "sto", 4);

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testFindNodeTypeServiceName() {
    Map<String, Map<String, Set<String>>> instances = new HashMap<>();
    doReturn(instances).when(pmsSdkInstanceService).getInstanceNameToSupportedTypes();
    assertThatThrownBy(() -> nodeTypeLookupService.findNodeTypeServiceName("blah"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Supported Types Map is empty");

    Map<String, Set<String>> service1SupportedTypes = new HashMap<>();
    service1SupportedTypes.put("stage", Collections.singleton("Service1"));
    service1SupportedTypes.put("step", Collections.singleton("Service1Step"));
    instances.put("service1", service1SupportedTypes);

    Map<String, Set<String>> service2SupportedTypes = new HashMap<>();
    service2SupportedTypes.put("stage", Collections.singleton("Service2"));
    service2SupportedTypes.put("step", Collections.singleton("Service2Step"));
    instances.put("service2", service2SupportedTypes);

    Map<String, Set<String>> service3SupportedTypes = new HashMap<>();
    service3SupportedTypes.put("stage", Collections.singleton("Custom"));
    instances.put("service3", service3SupportedTypes);

    Map<String, Set<String>> pmsSupportedTypes = new HashMap<>();
    pmsSupportedTypes.put("stage", Collections.singleton("Custom"));
    instances.put("pms", pmsSupportedTypes);

    Map<String, Set<String>> cfServiceSupportedTypes = new HashMap<>();
    cfServiceSupportedTypes.put("stage", Collections.singleton("FeatureFlag"));
    instances.put("anyName", cfServiceSupportedTypes);

    doReturn(instances).when(pmsSdkInstanceService).getInstanceNameToSupportedTypes();
    String serviceName = nodeTypeLookupService.findNodeTypeServiceName("Service2");
    assertThat(serviceName).isEqualTo("service2");
    serviceName = nodeTypeLookupService.findNodeTypeServiceName("Service2Step");
    assertThat(serviceName).isEqualTo("service2");
    serviceName = nodeTypeLookupService.findNodeTypeServiceName("Service1");
    assertThat(serviceName).isEqualTo("service1");
    serviceName = nodeTypeLookupService.findNodeTypeServiceName("Service1Step");
    assertThat(serviceName).isEqualTo("service1");
    serviceName = nodeTypeLookupService.findNodeTypeServiceName("FeatureFlag");
    assertThat(serviceName).isEqualTo("cf");
    serviceName = nodeTypeLookupService.findNodeTypeServiceName("Custom");
    assertThat(serviceName).isEqualTo("pms");

    assertThatThrownBy(() -> nodeTypeLookupService.findNodeTypeServiceName("eh"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Unknown Node type: eh");
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testModulesThatSupportStepTypes() {
    Map<String, Set<SdkStep>> instances = new HashMap<>();
    doReturn(instances).when(pmsSdkInstanceService).getSdkSteps();
    List<Node> planNodeList = new ArrayList<>();
    assertThatThrownBy(() -> nodeTypeLookupService.modulesThatSupportStepTypes(planNodeList))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Supported Types Map is empty");

    Set<SdkStep> listOfSdkStep = new HashSet<>();
    StepType stepType = StepType.newBuilder().setType("type").setStepCategory(StepCategory.STEP).build();
    listOfSdkStep.add(SdkStep.newBuilder().setStepType(stepType).setIsPartOfStepPallete(true).build());
    StepType stepType5 = StepType.newBuilder().setType("type5").setStepCategory(StepCategory.STAGE).build();
    listOfSdkStep.add(SdkStep.newBuilder().setStepType(stepType5).build());
    StepType dBSchemaApplyStepType =
        StepType.newBuilder().setType("DBSchemaApply").setStepCategory(StepCategory.STEP).build();
    listOfSdkStep.add(SdkStep.newBuilder().setStepType(dBSchemaApplyStepType).build());
    StepType dBSchemaRollbackStepType =
        StepType.newBuilder().setType("DBSchemaRollback").setStepCategory(StepCategory.STEP).build();
    listOfSdkStep.add(SdkStep.newBuilder().setStepType(dBSchemaRollbackStepType).build());
    StepType dBCommandStepType = StepType.newBuilder().setType("DBCommand").setStepCategory(StepCategory.STEP).build();
    listOfSdkStep.add(SdkStep.newBuilder().setStepType(dBCommandStepType).build());
    instances.put("cd", listOfSdkStep);

    Set<SdkStep> listOfSdkStep1 = new HashSet<>();
    listOfSdkStep1.add(SdkStep.newBuilder().setStepType(stepType).setIsPartOfStepPallete(true).build());
    StepType stepType1 = StepType.newBuilder().setType("type1").setStepCategory(StepCategory.STEP).build();
    listOfSdkStep1.add(SdkStep.newBuilder().setStepType(stepType1).setIsPartOfStepPallete(false).build());
    instances.put("ci", listOfSdkStep1);

    Set<SdkStep> listOfSdkStep2 = new HashSet<>();
    listOfSdkStep2.add(SdkStep.newBuilder().setStepType(stepType).setIsPartOfStepPallete(true).build());
    listOfSdkStep2.add(SdkStep.newBuilder().setStepType(stepType1).setIsPartOfStepPallete(false).build());
    StepType stepType2 = StepType.newBuilder().setType("type2").setStepCategory(StepCategory.STEP).build();
    listOfSdkStep2.add(SdkStep.newBuilder().setStepType(stepType2).setIsPartOfStepPallete(true).build());
    instances.put("iacm", listOfSdkStep2);

    Set<SdkStep> listOfSdkStep3 = new HashSet<>();
    listOfSdkStep3.add(SdkStep.newBuilder().setStepType(stepType).setIsPartOfStepPallete(true).build());
    listOfSdkStep3.add(SdkStep.newBuilder().setStepType(stepType1).setIsPartOfStepPallete(false).build());
    listOfSdkStep3.add(SdkStep.newBuilder().setStepType(stepType2).setIsPartOfStepPallete(true).build());
    StepType stepType3 = StepType.newBuilder().setType("type3").setStepCategory(StepCategory.STEP).build();
    listOfSdkStep3.add(SdkStep.newBuilder().setStepType(stepType3).setIsPartOfStepPallete(true).build());
    instances.put("sto", listOfSdkStep3);

    doReturn(instances).when(pmsSdkInstanceService).getSdkSteps();
    StepType stepType4 = StepType.newBuilder().setType("type2").setStepCategory(StepCategory.STEP).build();
    PlanNode planNode0 = PlanNode.builder().name("planNode0").stepType(stepType4).build();
    PlanNode planNode12 = PlanNode.builder().name("planNode12").stepType(stepType5).serviceName("cd").build();
    PlanNode planNodeCustom =
        PlanNode.builder()
            .name("planNodeCustom")
            .stepType(StepType.newBuilder().setType("CUSTOM_STAGE").setStepCategory(StepCategory.STAGE).build())
            .serviceName("cd")
            .build();
    PlanNode planNodeFeatureFlag =
        PlanNode.builder()
            .name("planNodeFeatureFlag")
            .stepType(StepType.newBuilder().setType("FLAG_STAGE").setStepCategory(StepCategory.STAGE).build())
            .serviceName("pms")
            .build();
    planNodeList.add(planNodeCustom);
    planNodeList.add(planNodeFeatureFlag);
    planNodeList.add(planNode12);
    planNodeList.add(planNode0);
    Map<String, Integer> priorityMap = new HashMap<>();
    priorityMap.put("pms", 1);
    priorityMap.put("cf", 2);
    priorityMap.put("cd", 3);
    priorityMap.put("iacm", 5);
    priorityMap.put("sto", 4);

    doReturn(priorityMap).when(pmsSdkHelper).getPipelineSdkPriority();

    List<String> supportedModules = nodeTypeLookupService.modulesThatSupportStepTypes(planNodeList);
    assertThat(supportedModules.size()).isEqualTo(5);
    assertThat(supportedModules.toArray()[0]).isEqualTo("pms");
    assertThat(supportedModules.toArray()[1]).isEqualTo("cf");
    assertThat(supportedModules.toArray()[2]).isEqualTo("cd");
    assertThat(supportedModules.toArray()[3]).isEqualTo("sto");
    assertThat(supportedModules.toArray()[4]).isEqualTo("iacm");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testModulesThatSupportStepTypesForDBSchemaApply() {
    Set<SdkStep> listOfSdkStep = new HashSet<>();
    StepType dBSchemaApplyStepType =
        StepType.newBuilder().setType(DB_SCHEMA_APPLY).setStepCategory(StepCategory.STEP).build();
    listOfSdkStep.add(SdkStep.newBuilder().setStepType(dBSchemaApplyStepType).build());

    Map<String, Set<SdkStep>> instances = Map.of("cd", listOfSdkStep);

    doReturn(instances).when(pmsSdkInstanceService).getSdkSteps();

    StepType stepType4 = StepType.newBuilder().setType(DB_SCHEMA_APPLY).setStepCategory(StepCategory.STEP).build();
    PlanNode planNode0 = PlanNode.builder().name("planNode0").stepType(stepType4).build();
    List<Node> planNodeList = List.of(planNode0);

    doReturn(PRIORITY_MAP).when(pmsSdkHelper).getPipelineSdkPriority();

    List<String> supportedModules = nodeTypeLookupService.modulesThatSupportStepTypes(planNodeList);
    assertThat(supportedModules.size()).isEqualTo(1);
    assertThat(supportedModules.toArray()[0]).isEqualTo("dbops");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testModulesThatSupportStepTypesForDBSchemaRollback() {
    Set<SdkStep> listOfSdkStep = new HashSet<>();
    StepType dBSchemaRollbackStepType =
        StepType.newBuilder().setType(DB_SCHEMA_ROLLBACK).setStepCategory(StepCategory.STEP).build();
    listOfSdkStep.add(SdkStep.newBuilder().setStepType(dBSchemaRollbackStepType).build());

    Map<String, Set<SdkStep>> instances = Map.of("cd", listOfSdkStep);

    doReturn(instances).when(pmsSdkInstanceService).getSdkSteps();

    StepType stepType4 = StepType.newBuilder().setType(DB_SCHEMA_ROLLBACK).setStepCategory(StepCategory.STEP).build();
    PlanNode planNode0 = PlanNode.builder().name("planNode0").stepType(stepType4).build();
    List<Node> planNodeList = List.of(planNode0);

    doReturn(PRIORITY_MAP).when(pmsSdkHelper).getPipelineSdkPriority();

    List<String> supportedModules = nodeTypeLookupService.modulesThatSupportStepTypes(planNodeList);
    assertThat(supportedModules.size()).isEqualTo(1);
    assertThat(supportedModules.toArray()[0]).isEqualTo("dbops");
  }

  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testModulesThatSupportStepTypesForDBCommand() {
    Set<SdkStep> listOfSdkStep = new HashSet<>();
    StepType dBCommandStepType = StepType.newBuilder().setType(DB_COMMAND).setStepCategory(StepCategory.STEP).build();
    listOfSdkStep.add(SdkStep.newBuilder().setStepType(dBCommandStepType).build());

    Map<String, Set<SdkStep>> instances = Map.of("cd", listOfSdkStep);

    doReturn(instances).when(pmsSdkInstanceService).getSdkSteps();

    StepType stepType4 = StepType.newBuilder().setType(DB_COMMAND).setStepCategory(StepCategory.STEP).build();
    PlanNode planNode0 = PlanNode.builder().name("planNode0").stepType(stepType4).build();
    List<Node> planNodeList = List.of(planNode0);

    doReturn(PRIORITY_MAP).when(pmsSdkHelper).getPipelineSdkPriority();

    List<String> supportedModules = nodeTypeLookupService.modulesThatSupportStepTypes(planNodeList);
    assertThat(supportedModules.size()).isEqualTo(1);
    assertThat(supportedModules.toArray()[0]).isEqualTo("dbops");
  }
}
