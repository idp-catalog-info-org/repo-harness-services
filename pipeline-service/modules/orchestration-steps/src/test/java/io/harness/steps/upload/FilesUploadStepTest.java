/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.steps.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.enforcement.exceptions.LimitExceededException;
import io.harness.logstreaming.LogStreamingStepClientFactory;
import io.harness.logstreaming.LogStreamingStepClientImpl;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.RuntimeFileInputDataRepository;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.steps.upload.RuntimeFileInputData.RuntimeFileInputDataKeys;

import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PIPELINE)
public class FilesUploadStepTest extends CategoryTest {
  @Mock RuntimeFileInputDataRepository runtimeFileInputDataRepository;
  @InjectMocks private FilesUploadStep filesUploadStep;

  @Mock LogStreamingStepClientImpl logClient;

  @Mock private LogStreamingStepClientFactory logStreamingStepClientFactory;

  public static String planExecutionId = "planExecutionId";
  public static String nodeExecutionId = "nodeExecutionId";
  public static String nodeExecutionId2 = "nodeExecutionId2";
  public static String accountId = "orgId";
  public static String orgIdentifier = "orgId";
  public static String projectIdentifier = "projId";
  public static String pipelineIdentifier = "pipelineIdentifier";

  private AutoCloseable mocks;
  @Before
  public void setup() {
    mocks = MockitoAnnotations.openMocks(this);
    when(logStreamingStepClientFactory.getLogStreamingStepClient(any())).thenReturn(logClient);
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  private Ambiance buildAmbiance() {
    return Ambiance.newBuilder()
        .putSetupAbstractions(SetupAbstractionKeys.accountId, accountId)
        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, orgIdentifier)
        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, projectIdentifier)
        .setMetadata(ExecutionMetadata.newBuilder().setPipelineIdentifier(pipelineIdentifier).build())
        .build();
  }

  @Test()
  @Owner(developers = OwnerRule.AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testExecuteAsyncAfterRbac_limitExeceeded() {
    Criteria criteria = new Criteria();
    criteria.and(RuntimeFileInputDataKeys.planExecutionId).is(planExecutionId);
    List<RuntimeFileInputData> data = new ArrayList<>();
    RuntimeFileInputData runtimeFileInputData = RuntimeFileInputData.builder()
                                                    .nodeExecutionId(nodeExecutionId)
                                                    .uuid("testUUid1")
                                                    .accountIdentifier(accountId)
                                                    .createdAt(System.currentTimeMillis())
                                                    .lastModifiedAt(System.currentTimeMillis())
                                                    .planExecutionId(planExecutionId)
                                                    .build();
    RuntimeFileInputData runtimeFileInputData2 = RuntimeFileInputData.builder()
                                                     .nodeExecutionId(nodeExecutionId2)
                                                     .uuid("testUUid2")
                                                     .accountIdentifier(accountId)
                                                     .createdAt(System.currentTimeMillis())
                                                     .lastModifiedAt(System.currentTimeMillis())
                                                     .planExecutionId(planExecutionId)
                                                     .build();
    data.add(runtimeFileInputData);
    data.add(runtimeFileInputData2);
    when(runtimeFileInputDataRepository.find(criteria)).thenReturn(data);

    StepBaseParameters stepBaseParameters = StepElementParameters.builder()
                                                .spec(FilesUploadStepParameters.infoBuilder().build())
                                                .timeout(ParameterField.createValueField("45m"))
                                                .build();
    try {
      filesUploadStep.executeAsyncAfterRbac(buildAmbiance(), stepBaseParameters, null);
    } catch (Exception exception) {
      assertThat(exception).isInstanceOf(LimitExceededException.class);
    }
  }
}
