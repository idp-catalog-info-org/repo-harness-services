/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.execution.dynamic;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.EntityNotFoundException;
import io.harness.execution.DynamicExecutionInstance;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceRequestDTO;
import io.harness.execution.dynamic.dtos.DynamicExecutionInstanceResponseDTO;
import io.harness.repositories.dynamic.DynamicExecutionInstanceRepository;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class DynamicExecutionServiceImplTest extends CategoryTest {
  private static final String NODE_EXECUTION_ID = "nodeExecutionId";
  private static final String PLAN_EXECUTION_ID = "planExecutionId";

  @Mock DynamicExecutionInstanceRepository dynamicExecutionInstanceRepository;

  @InjectMocks DynamicExecutionServiceImpl dynamicExecutionService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testCreate() {
    String dynamicYaml = "some-dynamic-yaml";
    DynamicExecutionInstanceRequestDTO requestDto = DynamicExecutionInstanceRequestDTO.builder()
                                                        .nodeExecutionId(NODE_EXECUTION_ID)
                                                        .planExecutionId(PLAN_EXECUTION_ID)
                                                        .yaml(dynamicYaml)
                                                        .build();
    dynamicExecutionService.create(requestDto);
    ArgumentCaptor<DynamicExecutionInstance> captor = ArgumentCaptor.forClass(DynamicExecutionInstance.class);
    verify(dynamicExecutionInstanceRepository, times(1)).save(captor.capture());
    DynamicExecutionInstance savedInstance = captor.getValue();
    assertThat(savedInstance.getNodeExecutionId()).isEqualTo(NODE_EXECUTION_ID);
    assertThat(savedInstance.getPlanExecutionId()).isEqualTo(PLAN_EXECUTION_ID);
    assertThat(savedInstance.getYaml()).isEqualTo(dynamicYaml);
    assertThat(savedInstance.getProcessedYaml()).isNull();
    assertThat(savedInstance.getIdentifier()).isNull();
    assertThat(savedInstance.getValidUntil()).isNotNull();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetByNodeExecutionId() {
    assertThatThrownBy(() -> dynamicExecutionService.getByNodeExecutionId("invalidNodeExecutionId"))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("DynamicExecution Instance could not be found for the nodeExecutionId invalidNodeExecutionId");
    String dynamicYaml = "some-dynamic-yaml";
    DynamicExecutionInstance dynamicExecutionInstance = DynamicExecutionInstance.builder()
                                                            .nodeExecutionId(NODE_EXECUTION_ID)
                                                            .planExecutionId(PLAN_EXECUTION_ID)
                                                            .yaml(dynamicYaml)
                                                            .build();
    doReturn(Optional.of(dynamicExecutionInstance))
        .when(dynamicExecutionInstanceRepository)
        .findByNodeExecutionId(NODE_EXECUTION_ID);
    DynamicExecutionInstanceResponseDTO response = dynamicExecutionService.getByNodeExecutionId(NODE_EXECUTION_ID);
    assertThat(response.getNodeExecutionId()).isEqualTo(dynamicExecutionInstance.getNodeExecutionId());
    assertThat(response.getPlanExecutionId()).isEqualTo(dynamicExecutionInstance.getPlanExecutionId());
    assertThat(response.getYaml()).isEqualTo(dynamicExecutionInstance.getYaml());
  }
}