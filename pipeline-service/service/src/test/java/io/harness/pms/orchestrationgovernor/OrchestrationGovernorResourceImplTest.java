/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.orchestrationgovernor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorConsumerKeys;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorState;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorState.Mode;
import io.harness.engine.execution.consumers.flowgovernor.FlowGovernorStateStore;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;
import io.harness.security.dto.UserPrincipal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationGovernorResourceImplTest extends CategoryTest {
  private static final String SERVICE_NAME = "pipeline-service";

  @Mock FlowGovernorStateStore stateStore;
  private OrchestrationGovernorResourceImpl resource;

  @Before
  public void setUp() {
    resource = new OrchestrationGovernorResourceImpl(stateStore);
    SecurityContextBuilder.setContext(new ServicePrincipal(SERVICE_NAME));
  }

  @After
  public void tearDown() {
    SecurityContextBuilder.unsetCompleteContext();
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void haltWritesHaltedStateAndBumpsVersion() {
    when(stateStore.get()).thenReturn(FlowGovernorState.builder().mode(Mode.NORMAL).version(7L).build());

    ResponseDTO<FlowGovernorStateDTO> response = resource.halt();

    ArgumentCaptor<FlowGovernorState> captor = ArgumentCaptor.forClass(FlowGovernorState.class);
    verify(stateStore).put(captor.capture());
    FlowGovernorState written = captor.getValue();
    assertThat(written.getMode()).isEqualTo(Mode.HALTED);
    assertThat(written.getVersion()).isEqualTo(8L);
    assertThat(written.getUpdatedBy()).isEqualTo(SERVICE_NAME);
    assertThat(written.getUpdatedAt()).isGreaterThan(0L);
    assertThat(response.getData().getMode()).isEqualTo("HALTED");
    assertThat(response.getData().getVersion()).isEqualTo(8L);
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void resumeThrottledWithoutConsumerSetsDefaultRps() {
    when(stateStore.get()).thenReturn(FlowGovernorState.builder().mode(Mode.NORMAL).version(3L).build());

    ResponseDTO<FlowGovernorStateDTO> response = resource.resumeThrottled(25, null);

    ArgumentCaptor<FlowGovernorState> captor = ArgumentCaptor.forClass(FlowGovernorState.class);
    verify(stateStore).put(captor.capture());
    FlowGovernorState written = captor.getValue();
    assertThat(written.getMode()).isEqualTo(Mode.THROTTLED);
    assertThat(written.getTargetRps()).isEqualTo(25);
    assertThat(written.getTargetRpsByConsumer()).isNull();
    assertThat(written.getVersion()).isEqualTo(4L);
    assertThat(response.getData().getTargetRps()).isEqualTo(25);
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void resumeThrottledWithConsumerAddsOverrideWithoutTouchingDefault() {
    when(stateStore.get())
        .thenReturn(FlowGovernorState.builder().mode(Mode.THROTTLED).targetRps(100).version(1L).build());

    resource.resumeThrottled(15, FlowGovernorConsumerKeys.INITIATE_NODE);

    ArgumentCaptor<FlowGovernorState> captor = ArgumentCaptor.forClass(FlowGovernorState.class);
    verify(stateStore).put(captor.capture());
    FlowGovernorState written = captor.getValue();
    assertThat(written.getTargetRps()).isEqualTo(100);
    assertThat(written.getTargetRpsByConsumer()).containsEntry(FlowGovernorConsumerKeys.INITIATE_NODE, 15).hasSize(1);
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void resumeThrottledRejectsMissingRps() {
    assertThatThrownBy(() -> resource.resumeThrottled(null, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("rps");
    verify(stateStore, never()).put(any());
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void resumeThrottledRejectsRpsOutOfRange() {
    assertThatThrownBy(() -> resource.resumeThrottled(0, null)).isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> resource.resumeThrottled(10_001, null)).isInstanceOf(InvalidRequestException.class);
    verify(stateStore, never()).put(any());
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void resumeThrottledRejectsUnknownConsumerKey() {
    assertThatThrownBy(() -> resource.resumeThrottled(50, "unknownConsumer"))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("unknownConsumer");
    verify(stateStore, never()).put(any());
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void resumeFullClearsThrottleAndReturnsNormal() {
    when(stateStore.get())
        .thenReturn(FlowGovernorState.builder().mode(Mode.THROTTLED).targetRps(20).version(5L).build());

    ResponseDTO<FlowGovernorStateDTO> response = resource.resumeFull();

    ArgumentCaptor<FlowGovernorState> captor = ArgumentCaptor.forClass(FlowGovernorState.class);
    verify(stateStore).put(captor.capture());
    FlowGovernorState written = captor.getValue();
    assertThat(written.getMode()).isEqualTo(Mode.NORMAL);
    assertThat(written.getTargetRps()).isNull();
    assertThat(written.getTargetRpsByConsumer()).isNull();
    assertThat(written.getVersion()).isEqualTo(6L);
    assertThat(response.getData().getMode()).isEqualTo("NORMAL");
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void getStateReturnsCurrentStateWithoutMutating() {
    FlowGovernorState existing = FlowGovernorState.builder()
                                     .mode(Mode.THROTTLED)
                                     .targetRps(42)
                                     .version(9L)
                                     .updatedBy("prior-caller")
                                     .updatedAt(123L)
                                     .build();
    when(stateStore.get()).thenReturn(existing);

    ResponseDTO<FlowGovernorStateDTO> response = resource.getState();

    verify(stateStore, never()).put(any());
    assertThat(response.getData().getMode()).isEqualTo("THROTTLED");
    assertThat(response.getData().getTargetRps()).isEqualTo(42);
    assertThat(response.getData().getVersion()).isEqualTo(9L);
    assertThat(response.getData().getUpdatedBy()).isEqualTo("prior-caller");
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void nonServicePrincipalIsRejected() {
    SecurityContextBuilder.setContext(new UserPrincipal("u", "e@h.io", "user", "acct"));
    assertThatThrownBy(() -> resource.halt()).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> resource.resumeThrottled(10, null)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> resource.resumeFull()).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> resource.getState()).isInstanceOf(AccessDeniedException.class);
    verify(stateStore, never()).put(any());
  }

  @Test
  @Owner(developers = OwnerRule.BRIJESH)
  @Category(UnitTests.class)
  public void missingPrincipalIsRejected() {
    SecurityContextBuilder.unsetCompleteContext();
    assertThatThrownBy(() -> resource.halt()).isInstanceOf(AccessDeniedException.class);
  }
}
