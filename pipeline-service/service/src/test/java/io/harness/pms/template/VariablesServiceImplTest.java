/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.template;

import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.service.VariableMergeResponseProto;
import io.harness.pms.contracts.service.VariablesServiceRequest;
import io.harness.pms.contracts.service.VariablesServiceRequestV2;
import io.harness.pms.variables.VariableCreatorMergeService;
import io.harness.pms.variables.VariableMergeServiceResponse;
import io.harness.rule.Owner;

import io.grpc.stub.StreamObserver;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class VariablesServiceImplTest extends PipelineServiceTestBase {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String YAML = "pipeline:\n  identifier: p1\n  name: pipeline1\n";

  @Mock VariableCreatorMergeService variableCreatorMergeService;
  @Mock StreamObserver<VariableMergeResponseProto> responseObserver;

  VariablesServiceImpl variablesService;

  @Before
  public void setUp() {
    variablesService = new VariablesServiceImpl(variableCreatorMergeService);
    when(variableCreatorMergeService.createVariablesResponses(anyString(), anyBoolean(), any()))
        .thenReturn(VariableMergeServiceResponse.builder().yaml(YAML).build());
    when(variableCreatorMergeService.createVariablesResponsesV2(anyString(), anyString(), anyString(), anyString()))
        .thenReturn(VariableMergeServiceResponse.builder().yaml(YAML).build());
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetVariablesCallsCreateVariablesResponses() {
    VariablesServiceRequest request = VariablesServiceRequest.newBuilder().setYaml(YAML).build();

    variablesService.getVariables(request, responseObserver);

    verify(variableCreatorMergeService).createVariablesResponses(eq(YAML), eq(false), isNull());
    verify(responseObserver).onNext(any(VariableMergeResponseProto.class));
    verify(responseObserver).onCompleted();
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testGetVariablesV2CallsCreateVariablesResponsesV2() {
    VariablesServiceRequestV2 request = VariablesServiceRequestV2.newBuilder()
                                            .setAccountId(ACCOUNT_ID)
                                            .setOrgId(ORG_ID)
                                            .setProjectId(PROJECT_ID)
                                            .setYaml(YAML)
                                            .build();

    variablesService.getVariablesV2(request, responseObserver);

    verify(variableCreatorMergeService).createVariablesResponsesV2(ACCOUNT_ID, ORG_ID, PROJECT_ID, YAML);
    verify(responseObserver).onNext(any(VariableMergeResponseProto.class));
    verify(responseObserver).onCompleted();
  }
}
