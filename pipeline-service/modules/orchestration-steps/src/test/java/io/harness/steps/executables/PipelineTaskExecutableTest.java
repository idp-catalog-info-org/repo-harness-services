/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.executables;

import static io.harness.rule.OwnerRule.SATENDRA;
import static io.harness.steps.DelegateSelectorContextGuard.setDelegateSelectorsInOIDCContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.context.GlobalContext;
import io.harness.manage.GlobalContextManager;
import io.harness.oidc.OIDCContext;
import io.harness.oidc.OIDCContextData;
import io.harness.oidc.helper.OIDCContextHelper;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

/**
 * Test for PipelineTaskExecutable.obtainTask() method that verifies delegate selectors
 * are properly set in OIDC context before task creation.
 */
@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineTaskExecutableTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private OIDCContextHelper oidcContextHelper;

  @Before
  public void setUp() {
    doNothing().when(oidcContextHelper).setOIDCContext(any());
  }

  @After
  public void tearDown() {
    GlobalContextManager.unset();
  }

  /**
   * Test that verifies setDelegateSelectorsInOIDCContext() correctly extracts and sets
   * delegate selectors in the OIDC context when called with a parameter implementing
   * WithDelegateSelector interface.
   */
  @Test
  @Owner(developers = SATENDRA)
  @Category(UnitTests.class)
  public void testSetDelegateSelectorsInOIDCContext_singleSelector() {
    TestParams params = new TestParams();
    params.delegateSelectors =
        ParameterField.createValueField(Collections.singletonList(new TaskSelectorYaml("pool-a")));

    GlobalContextManager.set(new GlobalContext());

    setDelegateSelectorsInOIDCContext(params, oidcContextHelper);

    ArgumentCaptor<OIDCContext> contextCaptor = ArgumentCaptor.forClass(OIDCContext.class);
    verify(oidcContextHelper, times(1)).setOIDCContext(contextCaptor.capture());

    OIDCContext capturedContext = contextCaptor.getValue();
    assertThat(capturedContext.getDelegateSelectors()).isEqualTo("pool-a");
  }

  /**
   * Test that verifies when existing OIDC context is present, the method preserves
   * all existing fields while updating only the delegate selectors.
   */
  @Test
  @Owner(developers = SATENDRA)
  @Category(UnitTests.class)
  public void testSetDelegateSelectorsInOIDCContext_preservesExisting() {
    TestParams params = new TestParams();
    params.delegateSelectors =
        ParameterField.createValueField(Collections.singletonList(new TaskSelectorYaml("pool-b")));

    GlobalContextManager.set(new GlobalContext());
    OIDCContext existingContext =
        OIDCContext.builder().pipelineIdentifier("pipe-123").serviceIdentifier("svc-456").build();
    GlobalContextManager.upsertGlobalContextRecord(OIDCContextData.builder().oidcContext(existingContext).build());

    setDelegateSelectorsInOIDCContext(params, oidcContextHelper);

    ArgumentCaptor<OIDCContext> contextCaptor = ArgumentCaptor.forClass(OIDCContext.class);
    verify(oidcContextHelper, times(1)).setOIDCContext(contextCaptor.capture());

    OIDCContext capturedContext = contextCaptor.getValue();
    assertThat(capturedContext.getDelegateSelectors()).isEqualTo("pool-b");
    assertThat(capturedContext.getPipelineIdentifier()).isEqualTo("pipe-123");
    assertThat(capturedContext.getServiceIdentifier()).isEqualTo("svc-456");
  }

  /**
   * Test that verifies multiple delegate selectors are joined with comma separator.
   */
  @Test
  @Owner(developers = SATENDRA)
  @Category(UnitTests.class)
  public void testSetDelegateSelectorsInOIDCContext_multipleSelectors() {
    TestParams params = new TestParams();
    params.delegateSelectors = ParameterField.createValueField(
        List.of(new TaskSelectorYaml("pool-a"), new TaskSelectorYaml("pool-b"), new TaskSelectorYaml("pool-c")));

    GlobalContextManager.set(new GlobalContext());

    setDelegateSelectorsInOIDCContext(params, oidcContextHelper);

    ArgumentCaptor<OIDCContext> contextCaptor = ArgumentCaptor.forClass(OIDCContext.class);
    verify(oidcContextHelper, times(1)).setOIDCContext(contextCaptor.capture());

    OIDCContext capturedContext = contextCaptor.getValue();
    assertThat(capturedContext.getDelegateSelectors()).isEqualTo("pool-a,pool-b,pool-c");
  }

  static class TestParams implements WithDelegateSelector {
    ParameterField<List<TaskSelectorYaml>> delegateSelectors;

    @Override
    public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
      return delegateSelectors;
    }

    @Override
    public void setDelegateSelectors(ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
      this.delegateSelectors = delegateSelectors;
    }
  }
}
