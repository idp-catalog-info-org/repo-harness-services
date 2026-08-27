/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.events.delete;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.audit.ResourceTypeConstants;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.ProjectScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.pms.events.PipelineOutboxEvents;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(PIPELINE)
public class PipelineDeleteEventTest extends CategoryTest {
  @Mock private PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Mock private PipelineEntity pipelineEntity;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String PIPELINE_ID = "pipelineId";
  private static final String PIPELINE_NAME = "pipelineName";
  private static final String UNIQUE_ID = "uniqueId";
  private static final String PARENT_UNIQUE_ID = "parentUniqueId";

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(pipelineEntity.getIdentifier()).thenReturn(PIPELINE_ID);
    when(pipelineEntity.getName()).thenReturn(PIPELINE_NAME);
    when(pipelineEntity.getUniqueId()).thenReturn(UNIQUE_ID);
    when(pipelineEntity.getParentUniqueId()).thenReturn(PARENT_UNIQUE_ID);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testConstructorWithoutFeatureFlagHelper() {
    PipelineDeleteEvent event = new PipelineDeleteEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntity);

    assertThat(event.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(event.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(event.getProjectIdentifier()).isEqualTo(PROJECT_ID);
    assertThat(event.getPipeline()).isEqualTo(pipelineEntity);
    assertThat(event.getIsForOldGitSync()).isFalse();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testConstructorWithFeatureFlagHelper() {
    PipelineDeleteEvent event = new PipelineDeleteEvent(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntity, pmsFeatureFlagHelper, PARENT_UNIQUE_ID, null);

    assertThat(event.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(event.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(event.getProjectIdentifier()).isEqualTo(PROJECT_ID);
    assertThat(event.getPipeline()).isEqualTo(pipelineEntity);
    assertThat(event.getPmsFeatureFlagHelper()).isEqualTo(pmsFeatureFlagHelper);
    assertThat(event.getIsForOldGitSync()).isFalse();
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetResourceScope() {
    PipelineDeleteEvent event = new PipelineDeleteEvent(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntity, pmsFeatureFlagHelper, PARENT_UNIQUE_ID, null);

    ProjectScope scope = (ProjectScope) event.getResourceScope();
    assertThat(scope.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(scope.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(scope.getProjectIdentifier()).isEqualTo(PROJECT_ID);
    assertThat(scope.getUniqueId()).isEqualTo(PARENT_UNIQUE_ID);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetResource() {
    PipelineDeleteEvent event = new PipelineDeleteEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntity);

    Resource resource = event.getResource();
    assertThat(resource.getIdentifier()).isEqualTo(PIPELINE_ID);
    assertThat(resource.getType()).isEqualTo(ResourceTypeConstants.PIPELINE);
    assertThat(resource.getUniqueId()).isEqualTo(UNIQUE_ID);
    assertThat(resource.getLabels()).containsKey(ResourceConstants.LABEL_KEY_RESOURCE_NAME);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetResourceNameWithFeatureFlagEnabled() {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.PIPE_USE_PIPELINE_IDENTIFIER_IN_AUDIT_LOGS))
        .thenReturn(true);

    PipelineDeleteEvent event = new PipelineDeleteEvent(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntity, pmsFeatureFlagHelper, PARENT_UNIQUE_ID, null);
    Resource resource = event.getResource();

    assertThat(resource.getLabels().get(ResourceConstants.LABEL_KEY_RESOURCE_NAME)).isEqualTo(PIPELINE_ID);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetResourceNameWithFeatureFlagDisabled() {
    when(pmsFeatureFlagHelper.isEnabled(ACCOUNT_ID, FeatureName.PIPE_USE_PIPELINE_IDENTIFIER_IN_AUDIT_LOGS))
        .thenReturn(false);

    PipelineDeleteEvent event = new PipelineDeleteEvent(
        ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntity, pmsFeatureFlagHelper, PARENT_UNIQUE_ID, null);
    Resource resource = event.getResource();

    assertThat(resource.getLabels().get(ResourceConstants.LABEL_KEY_RESOURCE_NAME)).isEqualTo(PIPELINE_NAME);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetResourceNameWithNullFeatureFlagHelper() {
    PipelineDeleteEvent event = new PipelineDeleteEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntity);
    Resource resource = event.getResource();

    assertThat(resource.getLabels().get(ResourceConstants.LABEL_KEY_RESOURCE_NAME)).isEqualTo(PIPELINE_NAME);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetEventType() {
    PipelineDeleteEvent event = new PipelineDeleteEvent(ACCOUNT_ID, ORG_ID, PROJECT_ID, pipelineEntity);

    assertThat(event.getEventType()).isEqualTo(PipelineOutboxEvents.PIPELINE_DELETED);
  }
}
