/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.harness.CategoryTest;
import io.harness.beans.execution.PublishedFileArtifact;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.execution.artifactDetails.ArtifactDetailsService;
import io.harness.delegate.task.stepstatus.artifact.ArtifactMetadata;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.rule.Owner;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class AbstractFileUploadStepTest extends CategoryTest {
  private static final String STORAGE_TYPE = "TEST_STORAGE";

  private static final class TestFileUploadStep extends AbstractFileUploadStep {
    private final StepArtifacts stepArtifactsToReturn;

    private TestFileUploadStep(StepArtifacts stepArtifactsToReturn) {
      this.stepArtifactsToReturn = stepArtifactsToReturn;
    }

    @Override
    protected String getStorageType() {
      return STORAGE_TYPE;
    }

    @Override
    protected StepArtifacts handleArtifact(ArtifactMetadata artifactMetadata, StepBaseParameters stepParameters) {
      return stepArtifactsToReturn;
    }
  }

  @Mock private ArtifactDetailsService artifactDetailsService;

  private Ambiance ambiance;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    ambiance = Ambiance.newBuilder().build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleArtifactPersistsFileArtifactsWhenServiceBound() {
    PublishedFileArtifact fileArtifact =
        PublishedFileArtifact.builder().name("file.txt").url("s3://bucket/file.txt").build();
    StepArtifacts stepArtifacts = StepArtifacts.builder().publishedFileArtifact(fileArtifact).build();
    TestFileUploadStep step = new TestFileUploadStep(stepArtifacts);
    step.artifactDetailsService = artifactDetailsService;

    StepArtifacts result = step.handleArtifact(null, null, ambiance);

    assertThat(result).isSameAs(stepArtifacts);
    verify(artifactDetailsService).saveFileArtifactDetails(eq(List.of(fileArtifact)), eq(ambiance), eq(STORAGE_TYPE));
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleArtifactSkipsPersistenceWhenServiceNotBound() {
    PublishedFileArtifact fileArtifact = PublishedFileArtifact.builder().name("file.txt").build();
    StepArtifacts stepArtifacts = StepArtifacts.builder().publishedFileArtifact(fileArtifact).build();
    TestFileUploadStep step = new TestFileUploadStep(stepArtifacts);
    // artifactDetailsService intentionally left null, mirroring @Inject(optional = true) not being bound

    StepArtifacts result = step.handleArtifact(null, null, ambiance);

    assertThat(result).as("Should still return the StepArtifacts built by the concrete step").isSameAs(stepArtifacts);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleArtifactSkipsPersistenceWhenNoPublishedFileArtifacts() {
    StepArtifacts stepArtifacts = StepArtifacts.builder().build();
    TestFileUploadStep step = new TestFileUploadStep(stepArtifacts);
    step.artifactDetailsService = artifactDetailsService;

    step.handleArtifact(null, null, ambiance);

    verifyNoInteractions(artifactDetailsService);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleArtifactSkipsPersistenceWhenStepArtifactsIsNull() {
    TestFileUploadStep step = new TestFileUploadStep(null);
    step.artifactDetailsService = artifactDetailsService;

    StepArtifacts result = step.handleArtifact(null, null, ambiance);

    assertThat(result).isNull();
    verifyNoInteractions(artifactDetailsService);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testHandleArtifactSwallowsPersistenceException() {
    PublishedFileArtifact fileArtifact = PublishedFileArtifact.builder().name("file.txt").build();
    StepArtifacts stepArtifacts = StepArtifacts.builder().publishedFileArtifact(fileArtifact).build();
    TestFileUploadStep step = new TestFileUploadStep(stepArtifacts);
    step.artifactDetailsService = artifactDetailsService;
    doThrow(new RuntimeException("mongo write failed"))
        .when(artifactDetailsService)
        .saveFileArtifactDetails(any(), any(), any());

    StepArtifacts result = step.handleArtifact(null, null, ambiance);

    assertThat(result).as("A persistence failure must not fail the step").isSameAs(stepArtifacts);
  }
}
