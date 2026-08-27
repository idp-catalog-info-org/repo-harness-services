/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.events;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PipelineMoveConfigEventTest extends CategoryTest {
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private Scope scope;

  private static final String ACCOUNT_ID = "accountId";
  private static final String PIPELINE_ID = "pipelineId";
  private static final String PIPELINE_NAME = "pipelineName";
  private static final String OLD_YAML = "oldYaml";
  private static final String NEW_YAML = "newYaml";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(scope.getAccountIdentifier()).thenReturn(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testConstructorWithoutFeatureFlagHelper() {
    PipelineMoveConfigEvent event = new PipelineMoveConfigEvent(scope, PIPELINE_ID, PIPELINE_NAME, OLD_YAML, NEW_YAML);

    assertThat(event.getScope()).isEqualTo(scope);
    assertThat(event.getIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(event.getName()).isEqualTo(PIPELINE_NAME);
    assertThat(event.getOldEntityAttributesYaml()).isEqualTo(OLD_YAML);
    assertThat(event.getNewEntityAttributesYaml()).isEqualTo(NEW_YAML);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testConstructorWithFeatureFlagHelper() {
    PipelineMoveConfigEvent event =
        new PipelineMoveConfigEvent(scope, PIPELINE_ID, PIPELINE_NAME, OLD_YAML, NEW_YAML, pmsFeatureFlagHelper);

    assertThat(event.getScope()).isEqualTo(scope);
    assertThat(event.getIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(event.getName()).isEqualTo(PIPELINE_NAME);
    assertThat(event.getOldEntityAttributesYaml()).isEqualTo(OLD_YAML);
    assertThat(event.getNewEntityAttributesYaml()).isEqualTo(NEW_YAML);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetResource() {
    PipelineMoveConfigEvent event = new PipelineMoveConfigEvent(scope, PIPELINE_ID, PIPELINE_NAME, OLD_YAML, NEW_YAML);

    Resource resource = event.getResource();
    assertThat(resource.getIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(resource.getType()).isEqualTo(ResourceTypeConstants.PIPELINE);
    assertThat(resource.getLabels()).containsKey(ResourceConstants.LABEL_KEY_RESOURCE_NAME);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetResourceNameWithFeatureFlagEnabled() {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.PIPE_USE_PIPELINE_IDENTIFIER_IN_AUDIT_LOGS))
        .thenReturn(true);

    PipelineMoveConfigEvent event =
        new PipelineMoveConfigEvent(scope, PIPELINE_ID, PIPELINE_NAME, OLD_YAML, NEW_YAML, pmsFeatureFlagHelper);
    Resource resource = event.getResource();

    assertThat(resource.getLabels().get(ResourceConstants.LABEL_KEY_RESOURCE_NAME)).isEqualTo(PIPELINE_ID);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetResourceNameWithFeatureFlagDisabled() {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.PIPE_USE_PIPELINE_IDENTIFIER_IN_AUDIT_LOGS))
        .thenReturn(false);

    PipelineMoveConfigEvent event =
        new PipelineMoveConfigEvent(scope, PIPELINE_ID, PIPELINE_NAME, OLD_YAML, NEW_YAML, pmsFeatureFlagHelper);
    Resource resource = event.getResource();

    assertThat(resource.getLabels().get(ResourceConstants.LABEL_KEY_RESOURCE_NAME)).isEqualTo(PIPELINE_NAME);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetResourceNameWithNullFeatureFlagHelper() {
    PipelineMoveConfigEvent event = new PipelineMoveConfigEvent(scope, PIPELINE_ID, PIPELINE_NAME, OLD_YAML, NEW_YAML);
    Resource resource = event.getResource();

    assertThat(resource.getLabels().get(ResourceConstants.LABEL_KEY_RESOURCE_NAME)).isEqualTo(PIPELINE_NAME);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetEventType() {
    PipelineMoveConfigEvent event = new PipelineMoveConfigEvent(scope, PIPELINE_ID, PIPELINE_NAME, OLD_YAML, NEW_YAML);

    assertThat(event.getEventType()).isEqualTo(PipelineOutboxEvents.PIPELINE_MOVED_TO_REMOTE);
  }
}
