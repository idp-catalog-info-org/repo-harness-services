/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.execution.service.impl;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.pms.contracts.interrupts.InterruptType.ABORT_ALL;
import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;
import static io.harness.rule.OwnerRule.DEVESH;
import static io.harness.rule.OwnerRule.EBTASAM;
import static io.harness.rule.OwnerRule.EDGAR_GARCIA;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.MEET;
import static io.harness.rule.OwnerRule.MLUKIC;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.RISHIKESH;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SAKSHI;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.SHIVAM;
import static io.harness.rule.OwnerRule.SOUMYAJIT;
import static io.harness.rule.OwnerRule.SRIDHAR;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VINICIUS;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.ModuleType;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.dataretention.service.ExecutionRetentionService;
import io.harness.dto.SimplifiedOrchestrationGraphDTO;
import io.harness.engine.OrchestrationService;
import io.harness.engine.executions.node.service.NodeExecutionService;
import io.harness.engine.executions.plan.service.PlanExecutionMetadataService;
import io.harness.engine.interrupts.InterruptPackage;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.EntityNotFoundException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.execution.PlanExecutionMetadata.PlanExecutionMetadataKeys;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.common.dtos.BitbucketSCMResponseDTO;
import io.harness.gitsync.common.dtos.GithubSCMResponseDTO;
import io.harness.gitsync.common.dtos.GitlabSCMResponseDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerResponseDTO;
import io.harness.gitsync.common.dtos.UserSourceCodeManagerResponseDTOList;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.gitsync.remote.GitSyncManagerClient;
import io.harness.gitsync.scm.GitSyncSdkService;
import io.harness.governance.GovernanceMetadata;
import io.harness.governance.PolicyMetadata;
import io.harness.governance.PolicySetMetadata;
import io.harness.interrupts.Interrupt;
import io.harness.ng.core.common.beans.FilterWithOperator;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.ng.userprofile.commons.SCMType;
import io.harness.opaclient.OpaServiceClientHelper;
import io.harness.opaclient.model.EvaluationDetailsResponse;
import io.harness.opaclient.model.OpaEvaluationResponseHolder;
import io.harness.opaclient.model.OpaPolicyEvaluationResponse;
import io.harness.opaclient.model.OpaPolicySetEvaluationResponse;
import io.harness.opaclient.model.PolicyData;
import io.harness.pms.contracts.interrupts.InterruptType;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.contracts.triggers.ManifestData;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.contracts.triggers.Type;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.execution.TimeRange;
import io.harness.pms.gitsync.PmsGitSyncHelper;
import io.harness.pms.helpers.TriggeredByHelper;
import io.harness.pms.helpers.YamlExpressionResolveHelper;
import io.harness.pms.instrumentaion.PipelineTelemetryHelper;
import io.harness.pms.merger.helpers.InputSetMergeHelper;
import io.harness.pms.merger.helpers.InputSetTemplateHelper;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsRequestDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.BulkInputSetsResponseDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetDetailsDTO;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetSummaryResponseDTOPMS;
import io.harness.pms.ngpipeline.inputset.beans.resource.InputSetYamlWithTemplateDTO;
import io.harness.pms.ngpipeline.inputset.helpers.validate.ValidateAndMergeHelper;
import io.harness.pms.ngpipeline.inputset.service.PMSInputSetService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.ResolveInputYamlType;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.plan.execution.PlanExecutionInterruptType;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.dto.CDModulePropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.CIExecutionInfoDTO;
import io.harness.pms.plan.execution.beans.dto.CIModulePropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.CIPullRequestDTO;
import io.harness.pms.plan.execution.beans.dto.CustomPage;
import io.harness.pms.plan.execution.beans.dto.ExecutionDataResponseDTO;
import io.harness.pms.plan.execution.beans.dto.ExecutionMetaDataResponseDetailsDTO;
import io.harness.pms.plan.execution.beans.dto.InterruptDTO;
import io.harness.pms.plan.execution.beans.dto.ModulePropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionFilterPropertiesDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionOutlineDTO;
import io.harness.pms.plan.execution.beans.dto.PipelineExecutionOutlineFilterDTO;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.repositories.executions.PmsExecutionSummaryRepository;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.service.GraphGenerationService;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.amazonaws.services.secretsmanager.model.ResourceNotFoundException;
import com.google.common.io.Resources;
import com.mongodb.BasicDBList;
import com.mongodb.client.result.UpdateResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Assertions;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.mongodb.core.query.Criteria;
import retrofit2.Call;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(PIPELINE)
public class PMSExecutionServiceImplTest extends CategoryTest {
  @Mock private PmsExecutionSummaryRepository pmsExecutionSummaryRepository;
  @Mock private UpdateResult updateResult;
  @InjectMocks private PMSExecutionServiceImpl pmsExecutionService;
  @Mock private PmsGitSyncHelper pmsGitSyncHelper;
  @Mock private ValidateAndMergeHelper validateAndMergeHelper;
  @Mock private PlanExecutionMetadataService planExecutionMetadataService;
  @Mock private PMSInputSetService pmsInputSetService;
  @Mock private PipelineExecutionSummaryEntity executionSummaryEntity;
  @Mock private GitSyncSdkService gitSyncSdkService;
  @Mock OrchestrationService orchestrationService;
  @Mock FilterService filterService;
  @Mock YamlExpressionResolveHelper yamlExpressionResolveHelper;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock GraphGenerationService graphGenerationService;
  @Mock PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Mock private PipelineTelemetryHelper pipelineTelemetryHelper;
  @Mock private PmsExecutionSummaryService pmsExecutionSummaryService;
  @Mock private TriggeredByHelper triggeredByHelper;
  @Mock private GitSyncManagerClient gitSyncManagerClient;
  @Mock private ScopeResolutionHelper scopeResolutionHelper;

  @Mock AccessControlClient accessControlClient;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock ExecutionRetentionService executionRetentionService;
  @Mock NodeExecutionService nodeExecutionService;
  @Mock OpaServiceClientHelper opaServiceClientHelper;
  private final String ACCOUNT_ID = "account_id";
  private final String USER_IDENTIFIER = "user_id";
  private final String IDENTIFIER = "identifier";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String UNIQUE_IDENTIFIER = "uniqueId";
  private final ScopeInfo SCOPE_INFO = ScopeInfo.builder()
                                           .accountIdentifier(ACCOUNT_ID)
                                           .orgIdentifier(ORG_IDENTIFIER)
                                           .projectIdentifier(PROJ_IDENTIFIER)
                                           .uniqueId(UNIQUE_IDENTIFIER)
                                           .build();
  private final String PIPELINE_IDENTIFIER = "basichttpFail";
  private final String PLAN_EXECUTION_ID = "planId";
  private final String FILTER_IDENTIFIER = "filterId";
  private final String FILTER_IDENTIFIER_1 = "filterIdWebhook1";
  private final String FILTER_IDENTIFIER_2 = "filterIdWebhook2";
  private final List<String> PIPELINE_IDENTIFIER_LIST = Arrays.asList(PIPELINE_IDENTIFIER);
  private final String INVALID_PLAN_EXECUTION_ID = "InvalidPlanId";
  private final Boolean PIPELINE_DELETED = Boolean.FALSE;
  private String inputSetYaml;
  private String template;
  private String executionYaml;

  PipelineEntity pipelineEntity;
  PlanExecutionMetadata planExecutionMetadata;

  @Before
  public void setUp() throws IOException {
    AutoCloseable mockitoSession = MockitoAnnotations.openMocks(this);
    ClassLoader classLoader = this.getClass().getClassLoader();
    String inputSetFilename = "inputSet1.yml";
    inputSetYaml =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(inputSetFilename)), StandardCharsets.UTF_8);

    String executionYamlFilename = "execution-yaml.yaml";
    executionYaml = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(executionYamlFilename)), StandardCharsets.UTF_8);

    String templateFilename = "pipeline-extensive-template.yml";
    template =
        Resources.toString(Objects.requireNonNull(classLoader.getResource(templateFilename)), StandardCharsets.UTF_8);

    // Configure the mock executionSummaryEntity
    doReturn(ACCOUNT_ID).when(executionSummaryEntity).getAccountId();
    doReturn(ORG_IDENTIFIER).when(executionSummaryEntity).getOrgIdentifier();
    doReturn(PROJ_IDENTIFIER).when(executionSummaryEntity).getProjectIdentifier();
    doReturn(PIPELINE_IDENTIFIER).when(executionSummaryEntity).getPipelineIdentifier();
    doReturn(PLAN_EXECUTION_ID).when(executionSummaryEntity).getPlanExecutionId();
    doReturn(PLAN_EXECUTION_ID).when(executionSummaryEntity).getName();
    doReturn(inputSetYaml).when(executionSummaryEntity).getResolvedUserInputSetYaml();
    doReturn(0).when(executionSummaryEntity).getRunSequence();
    doReturn(template).when(executionSummaryEntity).getPipelineTemplate();

    planExecutionMetadata = PlanExecutionMetadata.builder().inputSetYaml(inputSetYaml).build();

    String pipelineYamlFileName = "failure-strategy.yaml";
    String pipelineYaml = Resources.toString(
        Objects.requireNonNull(classLoader.getResource(pipelineYamlFileName)), StandardCharsets.UTF_8);

    pipelineEntity = PipelineEntity.builder()
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .identifier(PIPELINE_IDENTIFIER)
                         .name(PIPELINE_IDENTIFIER)
                         .yaml(pipelineYaml)
                         .build();

    when(scopeResolutionHelper.getScopeInfo(any(), any(), any()))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(ORG_IDENTIFIER)
                        .projectIdentifier(PROJ_IDENTIFIER)
                        .uniqueId(UNIQUE_IDENTIFIER)
                        .scopeType(ScopeLevel.PROJECT)
                        .build());
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testCheckIfCriteriaIsPopulated_FalseForEmptyCriteria() {
    Criteria empty = new Criteria();
    boolean populated = org.joor.Reflect.on(pmsExecutionService).call("checkIfCriteriaIsPopulated", empty).get();
    assertThat(populated).isFalse();
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testCheckIfCriteriaIsPopulated_TrueWhenFieldAdded() {
    Criteria c = new Criteria();
    c.and("accountId").is("acc");
    boolean populated = org.joor.Reflect.on(pmsExecutionService).call("checkIfCriteriaIsPopulated", c).get();
    assertThat(populated).isTrue();
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testFormCriteria() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);
    when(pmsFeatureFlagService.isEnabled(
             ACCOUNT_ID, FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION_FOR_TRIGGER_EXECUTIONS))
        .thenReturn(true);

    doReturn(Arrays.asList(PIPELINE_IDENTIFIER))
        .when(pmsPipelineService)
        .getPermittedPipelineIdentifier(any(), any(), any(), any());
    Criteria form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        null, null, null, null, null, false, !PIPELINE_DELETED, true, null);

    assertThat(form.getCriteriaObject().get("accountId").toString().contentEquals(ACCOUNT_ID)).isEqualTo(true);
    assertThat(form.getCriteriaObject().get("orgIdentifier").toString().contentEquals(ORG_IDENTIFIER)).isEqualTo(true);
    assertThat(form.getCriteriaObject().get("projectIdentifier").toString().contentEquals(PROJ_IDENTIFIER))
        .isEqualTo(true);
    assertThat(form.getCriteriaObject()
                   .get("pipelineIdentifier")
                   .toString()
                   .contentEquals("Document{{$in=[" + PIPELINE_IDENTIFIER + "]}}"))
        .isEqualTo(true);
    assertThat(form.getCriteriaObject().containsKey("status")).isEqualTo(false);
    assertThat(form.getCriteriaObject().get("pipelineDeleted")).isNotEqualTo(true);
    assertThat(form.getCriteriaObject().containsKey("executionTriggerInfo")).isEqualTo(false);
    assertThat(form.getCriteriaObject().get("isLatestExecution")).isNotEqualTo(false);
    assertThat(form.getCriteriaObject().get("executionMode")).isNotEqualTo(false);

    PipelineExecutionFilterPropertiesDTO pipelineExecutionFilterPropertiesDTO =
        PipelineExecutionFilterPropertiesDTO.builder()
            .triggerIdentifiers(Collections.singletonList("triggerIdentifier"))
            .build();
    doNothing().when(pmsPipelineServiceHelper).setPermittedPipelines(any(), any(), any(), any(), any());
    FilterDTO filterDTO = FilterDTO.builder().filterProperties(pipelineExecutionFilterPropertiesDTO).build();
    doReturn(filterDTO)
        .when(filterService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, FILTER_IDENTIFIER, FilterType.PIPELINEEXECUTION);
    Criteria form1 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        FILTER_IDENTIFIER, null, null, null, null, false, !PIPELINE_DELETED, false, null);
    assertThat(form1.getCriteriaObject().toString())
        .matches("Document\\{\\{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, "
            + "pipelineIdentifier=Document\\{\\{\\$in=\\[basichttpFail]}}, "
            + "\\$and=\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{executionTriggerInfo\\.triggeredBy\\.triggerIdentifier=Document\\{\\{\\$in="
            + "\\[triggerIdentifier]}}}}]}}]}}");
    Criteria form_1 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        null, pipelineExecutionFilterPropertiesDTO, null, null, null, false, !PIPELINE_DELETED, false, null);
    assertThat(form_1.getCriteriaObject().toString())
        .matches("Document\\{\\{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, "
            + "pipelineIdentifier=Document\\{\\{\\$in=\\[basichttpFail]}}, "
            + "\\$and=\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{executionTriggerInfo\\.triggeredBy\\.triggerIdentifier=Document\\{\\{\\$in="
            + "\\[triggerIdentifier]}}}}]}}]}}");
    PipelineExecutionFilterPropertiesDTO pipelineExecutionFilterPropertiesDTO1 =
        PipelineExecutionFilterPropertiesDTO.builder()
            .triggerTypes(Collections.singletonList(TriggerType.WEBHOOK))
            .build();
    filterDTO = FilterDTO.builder().filterProperties(pipelineExecutionFilterPropertiesDTO1).build();
    doReturn(filterDTO)
        .when(filterService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, FILTER_IDENTIFIER_1, FilterType.PIPELINEEXECUTION);
    Criteria form2 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        FILTER_IDENTIFIER_1, null, null, null, null, false, !PIPELINE_DELETED, false, null);
    assertThat(form2.getCriteriaObject().toString())
        .matches("Document\\{\\{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, "
            + "pipelineIdentifier=Document\\{\\{\\$in=\\[basichttpFail]}}, "
            + "\\$and=\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{executionTriggerInfo\\.triggerType=Document\\{\\{\\$in=\\[WEBHOOK]}}}}]}}]}}");
    doReturn(true).when(pmsFeatureFlagService).isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION);
    List<ExecutionStatus> statusList = new ArrayList<>();
    statusList.add(ExecutionStatus.QUEUED);
    Criteria form_2 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        null, pipelineExecutionFilterPropertiesDTO1, null, null, statusList, false, !PIPELINE_DELETED, false, null);
    assertThat(form_2.getCriteriaObject().toString())
        .matches("Document\\{\\{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, "
            + "pipelineIdentifier=Document\\{\\{\\$in=\\[basichttpFail]}}, "
            + "status=Document\\{\\{\\$in=\\[QUEUED, QUEUED_PLAN_CREATION, STARTING_PLAN_CREATION]}}, "
            + "\\$and=\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{executionTriggerInfo\\.triggerType=Document\\{\\{\\$in=\\[WEBHOOK]}}}}]}}]}}");
    filterDTO = FilterDTO.builder().filterProperties(pipelineExecutionFilterPropertiesDTO1).build();
    doReturn(filterDTO)
        .when(filterService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, FILTER_IDENTIFIER_2, FilterType.PIPELINEEXECUTION);
    Criteria form3 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        FILTER_IDENTIFIER_2, null, null, null, null, false, !PIPELINE_DELETED, true, null);
    assertThat(form3.getCriteriaObject().toString())
        .matches("Document\\{\\{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, "
            + "pipelineIdentifier=Document\\{\\{\\$in=\\[basichttpFail]}}, "
            + "\\$and=\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{executionTriggerInfo\\.triggerType=Document\\{\\{\\$in=\\[WEBHOOK]}}}}]}}]}}");
    Criteria form_3 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        null, pipelineExecutionFilterPropertiesDTO1, null, null, null, false, !PIPELINE_DELETED, true, null);
    assertThat(form_3.getCriteriaObject().toString())
        .matches("Document\\{\\{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, "
            + "pipelineIdentifier=Document\\{\\{\\$in=\\[basichttpFail]}}, "
            + "\\$and=\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{executionTriggerInfo\\.triggerType=Document\\{\\{\\$in=\\[WEBHOOK]}}}}]}}]}}");

    // Filter properties with Git - both branch and repo in query params
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(false);
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().branch("branch1").repoName("repo1").build();
    MockedStatic<GitAwareContextHelper> utilities = mockStatic(GitAwareContextHelper.class);
    utilities.when(GitAwareContextHelper::getGitRequestParamsInfo).thenReturn(gitEntityInfo);
    form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithGitFilter(null, null), null, null, null, false, !PIPELINE_DELETED, true, null);
    assertThat(form.getCriteriaObject().toString())
        .matches("Document\\{\\{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, "
            + "\\$and=\\[Document\\{\\{entityGitDetails.branch=branch1, entityGitDetails.repoName=repo1}}, "
            + "Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}}}]}}");

    // Filter properties with Git - branch in query params and repo in body params
    gitEntityInfo = GitEntityInfo.builder().branch("branch1").build();
    utilities.when(GitAwareContextHelper::getGitRequestParamsInfo).thenReturn(gitEntityInfo);
    form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithGitFilter(null, "repo1"), null, null, null, false, !PIPELINE_DELETED, true, null);
    assertThat(form.getCriteriaObject().toString())
        .matches("Document\\{\\{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, "
            + "\\$and=\\[Document\\{\\{entityGitDetails.branch=branch1, entityGitDetails.repoName=repo1}}, "
            + "Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}}}]}}");

    // Filter properties with Git - both branch and repo in body params
    gitEntityInfo = GitEntityInfo.builder().build();
    utilities.when(GitAwareContextHelper::getGitRequestParamsInfo).thenReturn(gitEntityInfo);
    form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithGitFilter("branch1", "repo1"), null, null, null, false, !PIPELINE_DELETED, true,
        null);
    assertThat(form.getCriteriaObject().toString())
        .matches("Document\\{\\{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, "
            + "\\$and=\\[Document\\{\\{entityGitDetails.branch=branch1, entityGitDetails.repoName=repo1}}, "
            + "Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}}}]}}");

    // Filter properties with Git - both branch and repo in body params
    gitEntityInfo = GitEntityInfo.builder().branch("__default__").repoName("__default__").build();
    utilities.when(GitAwareContextHelper::getGitRequestParamsInfo).thenReturn(gitEntityInfo);
    form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithGitFilter("branch1", "repo1"), null, null, null, false, !PIPELINE_DELETED, true,
        null);
    assertThat(form.getCriteriaObject().toString())
        .matches("Document\\{\\{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, "
            + "\\$and=\\[Document\\{\\{entityGitDetails.branch=branch1, entityGitDetails.repoName=repo1}}, "
            + "Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}}}]}}");

    pipelineExecutionFilterPropertiesDTO1 = PipelineExecutionFilterPropertiesDTO.builder()
                                                .pipelineIdentifiers(Arrays.asList("pipeline1", "pipeline2"))
                                                .build();
    filterDTO = FilterDTO.builder().filterProperties(pipelineExecutionFilterPropertiesDTO1).build();
    doReturn(filterDTO)
        .when(filterService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, FILTER_IDENTIFIER_1, FilterType.PIPELINEEXECUTION);
    form2 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, FILTER_IDENTIFIER_1,
        null, null, null, null, false, !PIPELINE_DELETED, false, null);
    assertThat(form2.getCriteriaObject().toString())
        .matches("Document\\{\\{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, "
            + "pipelineIdentifier=Document\\{\\{\\$in=\\[pipeline1, pipeline2]}}, "
            + "\\$and=\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}}}]}}");

    pipelineExecutionFilterPropertiesDTO1 = PipelineExecutionFilterPropertiesDTO.builder()
                                                .pipelineIdentifiers(Arrays.asList("pipeline1", "pipeline2"))
                                                .planExecutionIds(Arrays.asList("execution1", "execution2"))
                                                .build();
    filterDTO = FilterDTO.builder().filterProperties(pipelineExecutionFilterPropertiesDTO1).build();
    doReturn(filterDTO)
        .when(filterService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, FILTER_IDENTIFIER_1, FilterType.PIPELINEEXECUTION);
    form2 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, FILTER_IDENTIFIER_1,
        null, null, null, null, false, !PIPELINE_DELETED, false, null);
    assertThat(form2.getCriteriaObject().toString())
        .matches("Document\\{\\{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, "
            + "pipelineIdentifier=Document\\{\\{\\$in=\\[pipeline1, pipeline2]}}, "
            + "\\$and=\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "planExecutionId=Document\\{\\{\\$in=\\[execution1, execution2]}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}}}]}}");

    pipelineExecutionFilterPropertiesDTO1 = PipelineExecutionFilterPropertiesDTO.builder()
                                                .pipelineIdentifiers(Arrays.asList("pipeline1", "pipeline2"))
                                                .inputSetIdentifiers(Arrays.asList("inputSet1", "inputSet2"))
                                                .build();
    filterDTO = FilterDTO.builder().filterProperties(pipelineExecutionFilterPropertiesDTO1).build();
    doReturn(filterDTO)
        .when(filterService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, FILTER_IDENTIFIER_1, FilterType.PIPELINEEXECUTION);
    form2 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, FILTER_IDENTIFIER_1,
        null, null, null, null, false, !PIPELINE_DELETED, false, null);
    assertThat(form2.getCriteriaObject().toString())
        .matches("Document\\{\\{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, "
            + "pipelineIdentifier=Document\\{\\{\\$in=\\[pipeline1, pipeline2]}}, "
            + "\\$and=\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "inputSetIdentifiers=Document\\{\\{\\$in=\\[inputSet1, inputSet2]}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}}}]}}");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFormQueryForSearch() throws IOException {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);

    // Pipeline Identifier
    doReturn(List.of(PIPELINE_IDENTIFIER))
        .when(pmsPipelineService)
        .getPermittedPipelineIdentifier(any(), any(), any(), any());
    Query query = pmsExecutionService.formQueryForSearch(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null, null, null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"pipelineIdentifier\":\\[\"basichttpFail\"]}},\\{\"terms\":\\{\"executionMode\":"
            + "\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{\"range\":\\{\"startTs\":\\{"
            + "\"gte\":\\d+,\"lte\":\\d+}}}]}}}}");

    // Pipeline Identifier in Filter Properties
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithIdentifierFilter(), null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"pipelineIdentifier\":\\[\"basichttpFail\"]}},\\{\"terms\":\\{\"executionMode\":"
            + "\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{\"range\":\\{\"startTs\":\\{"
            + "\"gte\":\\d+,\"lte\":\\d+}}}]}}}}");

    // Status Filter
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithIdentifierFilter(), null, null,
        Arrays.asList(ExecutionStatus.ABORTED, ExecutionStatus.EXPIRED), false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"pipelineIdentifier\":\\[\"basichttpFail\"]}},\\{\"terms\":\\{\"status\":\\["
            + "\"ABORTED\",\"EXPIRED\"]}},\\{\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\","
            + "\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}}]}}}}");

    // No Pipeline Identifier
    doReturn(true).when(pmsPipelineService).validateViewPermission(any(), any(), any());
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null,
        null, Arrays.asList(ExecutionStatus.ABORTED, ExecutionStatus.EXPIRED), false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"status\":\\[\"ABORTED\",\"EXPIRED\"]}},\\{\"terms\":\\{\"executionMode\":\\["
            + "\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{\"range\":\\{\"startTs\":\\{"
            + "\"gte\":\\d+,\"lte\":\\d+}}}]}}}}");

    // My Deployments
    Map<String, String> extra = new HashMap<>();
    extra.put("email", "test@email");
    TriggeredBy triggeredBy1 = TriggeredBy.newBuilder().setIdentifier(IDENTIFIER).putAllExtraInfo(extra).build();
    doReturn(triggeredBy1).when(triggeredByHelper).getFromSecurityContext();
    query = pmsExecutionService.formQueryForSearch(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, null, true, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}}"
            + ",\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"must\":\\[\\{"
            + "\"term\":\\{\"triggerType\":\\{\"value\":\"MANUAL\"}}},\\{\"term\":\\{\"triggeredBy.email\":\\{"
            + "\"value\":\"test@email\"}}}]}}]}}}}");

    // My Deployments with PIPE_FILTER_EXECUTIONS_BY_GIT_EVENTS
    enableFilterExecutionsByGit();
    query = pmsExecutionService.formQueryForSearch(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, null, null, true, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":\\{"
            + "\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}},\\{"
            + "\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{"
            + "\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"should\":\\[\\{\"bool\":\\{"
            + "\"must\":\\[\\{\"term\":\\{\"triggerType\":\\{\"value\":\"MANUAL\"}}},\\{\"term\":\\{\"triggeredBy."
            + "email\":\\{\"value\":\"test@email\"}}}]}},\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"triggerType\":\\{"
            + "\"value\":\"WEBHOOK\"}}},\\{\"terms\":\\{\"triggeredBy.gitUser\":\\[\"username\",\"username1\","
            + "\"username2\"]}}]}},\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"triggerType\":\\{\"value\":\"WEBHOOK_"
            + "CUSTOM\"}}},\\{\"term\":\\{\"triggeredBy.email\":\\{\"value\":\"test@email\"}}}]}}]}}]}}}}");

    // Module name
    query = pmsExecutionService.formQueryForSearch(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, "cd", null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":\\{"
            + "\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}},\\{"
            + "\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{"
            + "\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"should\":\\[\\{\"term\":\\{"
            + "\"modules\":\\{\"value\":\"common\"}}},\\{\"term\":\\{\"modules\":\\{\"value\":\"cd\"}}}]}}]}}}}");

    // Search term
    query = pmsExecutionService.formQueryForSearch(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null, "testing", null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}}"
            + ",\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"should\":\\[\\{"
            + "\"wildcard\":\\{\"pipelineIdentifier\":\\{\"case_insensitive\":true,\"value\":\"\\*testing\\*\"}}},"
            + "\\{\"wildcard\":\\{\"name\":\\{\"case_insensitive\":true,\"value\":\"\\*testing\\*\"}}},\\{"
            + "\"nested\":\\{\"path\":\"tags\",\"query\":\\{\"bool\":\\{\"should\":\\[\\{\"wildcard\":\\{\"tags."
            + "key\":\\{\"case_insensitive\":true,\"value\":\"\\*testing\\*\"}}},\\{\"wildcard\":\\{\"tags."
            + "value\":\\{\"case_insensitive\":true,\"value\":\"\\*testing\\*\"}}}]}}}}]}}]}}}}");

    // Filter properties
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithTimeStatusAndNameFilter(), null, null, null, false, null);
    assertThat(query.toString())
        .isEqualTo("Query: "
            + "{\"constant_score\":{\"filter\":{\"bool\":{\"must\":[{\"term\":{\"accountId\":{\"value\":\"account_id\"}"
            + "}},{\"term\":{\"orgIdentifier\":{\"value\":\"orgId\"}}},{\"term\":{\"projectIdentifier\":{\"value\":"
            + "\"projId\"}}},{\"term\":{\"deleted\":{\"value\":false}}},{\"terms\":{\"executionMode\":[\"POST_"
            + "EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},{\"range\":{\"startTs\":{\"gte\":1712116800000,"
            + "\"lte\":1714653568130}}},{\"terms\":{\"status\":[\"SUCCESS\",\"EXPIRED\"]}},{\"bool\":{\"should\":[{"
            + "\"wildcard\":{\"pipelineIdentifier\":{\"case_insensitive\":true,\"value\":\"*test-pipeline-name*\"}}},{"
            + "\"wildcard\":{\"name\":{\"case_insensitive\":true,\"value\":\"*test-pipeline-name*\"}}}]}}]}}}}");

    // Filter properties with status
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithTimeStatusAndNameFilter(), null, null,
        Arrays.asList(ExecutionStatus.ABORTED, ExecutionStatus.EXPIRED), false, null);
    assertThat(query.toString())
        .isEqualTo("Query: "
            + "{\"constant_score\":{\"filter\":{\"bool\":{\"must\":[{\"term\":{\"accountId\":{\"value\":\"account_id\"}"
            + "}},{\"term\":{\"orgIdentifier\":{\"value\":\"orgId\"}}},{\"term\":{\"projectIdentifier\":{\"value\":"
            + "\"projId\"}}},{\"term\":{\"deleted\":{\"value\":false}}},{\"terms\":{\"status\":[\"ABORTED\","
            + "\"EXPIRED\"]}},{\"terms\":{\"executionMode\":[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]"
            + "}},{\"range\":{\"startTs\":{\"gte\":1712116800000,\"lte\":1714653568130}}},{\"bool\":{\"should\":[{"
            + "\"wildcard\":{\"pipelineIdentifier\":{\"case_insensitive\":true,\"value\":\"*test-pipeline-name*\"}}},{"
            + "\"wildcard\":{\"name\":{\"case_insensitive\":true,\"value\":\"*test-pipeline-name*\"}}}]}}]}}}}");

    // Filter properties with status and planexecutionid
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithTimeStatusPlanExecutionIdAndNameFilter(), null, null,
        Arrays.asList(ExecutionStatus.ABORTED, ExecutionStatus.EXPIRED), false, null);
    assertThat(query.toString())
        .isEqualTo("Query: "
            + "{\"constant_score\":{\"filter\":{\"bool\":{\"must\":[{\"term\":{\"accountId\":{\"value\":\"account_id\"}"
            + "}},{\"term\":{\"orgIdentifier\":{\"value\":\"orgId\"}}},{\"term\":{\"projectIdentifier\":{\"value\":"
            + "\"projId\"}}},{\"term\":{\"deleted\":{\"value\":false}}},{\"terms\":{\"status\":[\"ABORTED\","
            + "\"EXPIRED\"]}},{\"terms\":{\"executionMode\":[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]"
            + "}},{\"range\":{\"startTs\":{\"gte\":1712116800000,\"lte\":1714653568130}}},{\"bool\":{\"should\":[{"
            + "\"wildcard\":{\"pipelineIdentifier\":{\"case_insensitive\":true,\"value\":\"*test-pipeline-name*\"}}},{"
            + "\"wildcard\":{\"name\":{\"case_insensitive\":true,\"value\":\"*test-pipeline-name*\"}}}]}},{\"terms\":{"
            + "\"planExecutionId\":[\"executionId1\",\"executionId2\"]}}]}}}}");

    // Filter properties with status and inputSetIdentifier
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithTimeStatusInputSetIdentifierAndNameFilter(), null, null,
        Arrays.asList(ExecutionStatus.ABORTED, ExecutionStatus.EXPIRED), false, null);
    assertThat(query.toString())
        .isEqualTo("Query: "
            + "{\"constant_score\":{\"filter\":{\"bool\":{\"must\":[{\"term\":{\"accountId\":{\"value\":\"account_id\"}"
            + "}},{\"term\":{\"orgIdentifier\":{\"value\":\"orgId\"}}},{\"term\":{\"projectIdentifier\":{\"value\":"
            + "\"projId\"}}},{\"term\":{\"deleted\":{\"value\":false}}},{\"terms\":{\"status\":[\"ABORTED\","
            + "\"EXPIRED\"]}},{\"terms\":{\"executionMode\":[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]"
            + "}},{\"range\":{\"startTs\":{\"gte\":1712116800000,\"lte\":1714653568130}}},{\"bool\":{\"should\":[{"
            + "\"wildcard\":{\"pipelineIdentifier\":{\"case_insensitive\":true,\"value\":\"*test-pipeline-name*\"}}},{"
            + "\"wildcard\":{\"name\":{\"case_insensitive\":true,\"value\":\"*test-pipeline-name*\"}}}]}},{\"terms\":{"
            + "\"inputSetIdentifiers\":[\"inputSetIdentifier1\",\"inputSetIdentifier2\"]}}]}}}}");

    // Filter properties with Tags
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithTriggerAndPipelineTagsFilter(), null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}}"
            + ",\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"nested\":\\{\"path\":\"tags\","
            + "\"query\":\\{\"bool\":\\{\"should\":\\[\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{"
            + "\"value\":\"tagKey3\"}}},\\{\"term\":\\{\"tags.value\":\\{\"value\":\"tagValue3\"}}}]}},\\{"
            + "\"terms\":\\{\"tags.key\":\\[\"tagKey1\",\"tagValue2\"]}},\\{\"terms\":\\{\"tags.value\":\\["
            + "\"tagKey1\",\"tagValue2\"]}}]}}}},\\{\"terms\":\\{\"triggeredBy.triggerIdentifier\":\\["
            + "\"trigger1\",\"trigger2\"]}},\\{\"terms\":\\{\"triggerType\":\\[\"MANUAL\",\"WEBHOOK\"]}}]}}}}");

    // Filter properties with Module properties, all CD properties as array
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithModulePropertiesFilter(Arrays.asList("nginx:stable", "nginx:latest"),
            Arrays.asList("env1", "env2"), Arrays.asList("service1", "service2"), Arrays.asList("Kubernetes", "Helm"),
            Arrays.asList("helm1", "helm2"), Arrays.asList("gitOps1", "gitOps2")),
        null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":\\{"
            + "\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}},\\{"
            + "\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{"
            + "\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"terms\":\\{\"cdModuleInfo."
            + "envIdentifiers\":\\[\"env1\",\"env2\"]}},\\{\"terms\":\\{\"cdModuleInfo.artifactDisplayNames\":\\["
            + "\"nginx:stable\",\"nginx:latest\"]}},\\{\"terms\":\\{\"cdModuleInfo.serviceIdentifiers\":\\["
            + "\"service1\",\"service2\"]}},\\{\"terms\":\\{\"cdModuleInfo.serviceDefinitionTypes\":\\[\"Kubernetes\","
            + "\"Helm\"]}},\\{\"terms\":\\{\"cdModuleInfo.helmChartVersions\":\\[\"helm1\",\"helm2\"]}},\\{\"terms\":"
            + "\\{\"cdModuleInfo.gitOpsAppIdentifiers\":\\[\"gitOps1\",\"gitOps2\"]}},\\{\"term\":\\{\"ciModuleInfo."
            + "branch\":\\{\"value\":\"main\"}}},\\{\"term\":\\{\"ciModuleInfo.buildType\":\\{\"value\":\"branch\"}}},"
            + "\\{\"term\":\\{\"ciModuleInfo.tag\":\\{\"value\":\"tag\"}}},\\{\"term\":\\{\"ciModuleInfo."
            + "ciExecutionInfoDTO.event\":\\{\"value\":\"pullRequest\"}}},\\{\"term\":\\{\"ciModuleInfo."
            + "ciExecutionInfoDTO.pullRequest.sourceBranch\":\\{\"value\":\"sourceBranch\"}}},\\{\"term\":\\{"
            + "\"ciModuleInfo.ciExecutionInfoDTO.pullRequest.targetBranch\":\\{\"value\":\"targetBranch\"}}}]}}}}");

    // Filter properties with Module properties, all CD properties as null
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithModulePropertiesFilter(null, null, null, null, null, null), null, null, null, false,
        null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":\\{"
            + "\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}},\\{"
            + "\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{"
            + "\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"term\":\\{\"ciModuleInfo.branch\":\\{"
            + "\"value\":\"main\"}}},\\{\"term\":\\{\"ciModuleInfo.buildType\":\\{\"value\":\"branch\"}}},\\{\"term\":"
            + "\\{\"ciModuleInfo.tag\":\\{\"value\":\"tag\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO."
            + "event\":\\{\"value\":\"pullRequest\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO.pullRequest."
            + "sourceBranch\":\\{\"value\":\"sourceBranch\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO."
            + "pullRequest.targetBranch\":\\{\"value\":\"targetBranch\"}}}]}}}}");

    // Filter properties with Module properties, all CD properties as list of string/null values
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithModulePropertiesFilter(Arrays.asList("nginx:stable", null, "nginx:latest", null),
            Arrays.asList(null, "env1", "env2", null), Arrays.asList("service1", null, null, "service2"),
            Arrays.asList("Kubernetes", "Helm"), Arrays.asList("helm1", "helm2"), Arrays.asList("gitOps1", "gitOps2")),
        null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":\\{"
            + "\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}},\\{"
            + "\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{"
            + "\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"should\":\\[\\{\"terms\":\\{"
            + "\"cdModuleInfo.envIdentifiers\":\\[\"env1\",\"env2\"]}},\\{\"bool\":\\{\"must_not\":\\[\\{\"exists\":\\{"
            + "\"field\":\"cdModuleInfo.envIdentifiers\"}}]}}]}},\\{\"bool\":\\{\"should\":\\[\\{\"terms\":\\{"
            + "\"cdModuleInfo.artifactDisplayNames\":\\[\"nginx:stable\",\"nginx:latest\"]}},\\{\"bool\":\\{\"must_"
            + "not\":\\[\\{\"exists\":\\{\"field\":\"cdModuleInfo.artifactDisplayNames\"}}]}}]}},\\{\"bool\":\\{"
            + "\"should\":\\[\\{\"terms\":\\{\"cdModuleInfo.serviceIdentifiers\":\\[\"service1\",\"service2\"]}},\\{"
            + "\"bool\":\\{\"must_not\":\\[\\{\"exists\":\\{\"field\":\"cdModuleInfo.serviceIdentifiers\"}}]}}]}},\\{"
            + "\"terms\":\\{\"cdModuleInfo.serviceDefinitionTypes\":\\[\"Kubernetes\",\"Helm\"]}},\\{\"terms\":\\{"
            + "\"cdModuleInfo.helmChartVersions\":\\[\"helm1\",\"helm2\"]}},\\{\"terms\":\\{\"cdModuleInfo."
            + "gitOpsAppIdentifiers\":\\[\"gitOps1\",\"gitOps2\"]}},\\{\"term\":\\{\"ciModuleInfo.branch\":\\{"
            + "\"value\":\"main\"}}},\\{\"term\":\\{\"ciModuleInfo.buildType\":\\{\"value\":\"branch\"}}},\\{\"term\":"
            + "\\{\"ciModuleInfo.tag\":\\{\"value\":\"tag\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO."
            + "event\":\\{\"value\":\"pullRequest\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO.pullRequest."
            + "sourceBranch\":\\{\"value\":\"sourceBranch\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO."
            + "pullRequest.targetBranch\":\\{\"value\":\"targetBranch\"}}}]}}}}");

    // Filter properties with Module properties, all CD properties as list of string/null values or null
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithModulePropertiesFilter("nginx:stable", Arrays.asList(null, "env1", "env2", null),
            "service2", null, Arrays.asList("helm1", "helm2"), Arrays.asList("gitOps1", "gitOps2")),
        null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":\\{"
            + "\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}},\\{"
            + "\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{"
            + "\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"should\":\\[\\{\"terms\":\\{"
            + "\"cdModuleInfo.envIdentifiers\":\\[\"env1\",\"env2\"]}},\\{\"bool\":\\{\"must_not\":\\[\\{\"exists\":\\{"
            + "\"field\":\"cdModuleInfo.envIdentifiers\"}}]}}]}},\\{\"terms\":\\{\"cdModuleInfo.artifactDisplayNames\":"
            + "\\[\"nginx:stable\"]}},\\{\"terms\":\\{\"cdModuleInfo.serviceIdentifiers\":\\[\"service2\"]}},\\{"
            + "\"terms\":\\{\"cdModuleInfo.helmChartVersions\":\\[\"helm1\",\"helm2\"]}},\\{\"terms\":\\{"
            + "\"cdModuleInfo.gitOpsAppIdentifiers\":\\[\"gitOps1\",\"gitOps2\"]}},\\{\"term\":\\{\"ciModuleInfo."
            + "branch\":\\{\"value\":\"main\"}}},\\{\"term\":\\{\"ciModuleInfo.buildType\":\\{\"value\":\"branch\"}}},"
            + "\\{\"term\":\\{\"ciModuleInfo.tag\":\\{\"value\":\"tag\"}}},\\{\"term\":\\{\"ciModuleInfo."
            + "ciExecutionInfoDTO.event\":\\{\"value\":\"pullRequest\"}}},\\{\"term\":\\{\"ciModuleInfo."
            + "ciExecutionInfoDTO.pullRequest.sourceBranch\":\\{\"value\":\"sourceBranch\"}}},\\{\"term\":\\{"
            + "\"ciModuleInfo.ciExecutionInfoDTO.pullRequest.targetBranch\":\\{\"value\":\"targetBranch\"}}}]}}}}");

    // Filter properties with Git - both branch and repo in query params
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(false);
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().branch("branch1").repoName("repo1").build();
    MockedStatic<GitAwareContextHelper> utilities = mockStatic(GitAwareContextHelper.class);
    utilities.when(GitAwareContextHelper::getGitRequestParamsInfo).thenReturn(gitEntityInfo);
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithGitFilter(null, null), null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":\\{"
            + "\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}},\\{"
            + "\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{"
            + "\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"term\":\\{\"entityGitDetails.branch\":\\{"
            + "\"value\":\"branch1\"}}},\\{\"term\":\\{\"entityGitDetails.repoName\":\\{\"value\":\"repo1\"}}}]}}}}");

    // Filter properties with Git - branch in query params and repo in body params
    gitEntityInfo = GitEntityInfo.builder().branch("branch1").build();
    utilities.when(GitAwareContextHelper::getGitRequestParamsInfo).thenReturn(gitEntityInfo);
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithGitFilter(null, "repo1"), null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":\\{"
            + "\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}},\\{"
            + "\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{"
            + "\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"term\":\\{\"entityGitDetails.branch\":\\{"
            + "\"value\":\"branch1\"}}},\\{\"term\":\\{\"entityGitDetails.repoName\":\\{\"value\":\"repo1\"}}}]}}}}");

    // Filter properties with Git - both branch and repo in body params
    gitEntityInfo = GitEntityInfo.builder().repoName("__default__").branch("__default__").build();
    utilities.when(GitAwareContextHelper::getGitRequestParamsInfo).thenReturn(gitEntityInfo);
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithGitFilter("branch1", "repo1"), null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":\\{"
            + "\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}},\\{"
            + "\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{"
            + "\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"term\":\\{\"entityGitDetails.branch\":\\{"
            + "\"value\":\"branch1\"}}},\\{\"term\":\\{\"entityGitDetails.repoName\":\\{\"value\":\"repo1\"}}}]}}}}");

    // Filter properties with Git - branch in body params and repo in query params
    gitEntityInfo = GitEntityInfo.builder().repoName("repo1").build();
    utilities.when(GitAwareContextHelper::getGitRequestParamsInfo).thenReturn(gitEntityInfo);
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithGitFilter("branch1", null), null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":\\{"
            + "\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}},\\{"
            + "\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{"
            + "\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"term\":\\{\"entityGitDetails.branch\":\\{"
            + "\"value\":\"branch1\"}}},\\{\"term\":\\{\"entityGitDetails.repoName\":\\{\"value\":\"repo1\"}}}]}}}}");

    FilterDTO filterDTO = FilterDTO.builder().filterProperties(getFilterPropertiesDTOWithIdentifierFilter()).build();
    doReturn(filterDTO)
        .when(filterService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, FILTER_IDENTIFIER_1, FilterType.PIPELINEEXECUTION);
    query = pmsExecutionService.formQueryForSearch(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, FILTER_IDENTIFIER_1, null, null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":\\{"
            + "\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}},\\{"
            + "\"terms\":\\{\"pipelineIdentifier\":\\[\"basichttpFail\"]}},\\{\"terms\":\\{\"executionMode\":\\[\"POST_"
            + "EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,"
            + "\"lte\":\\d+}}},\\{\"term\":\\{\"entityGitDetails.repoName\":\\{\"value\":\"repo1\"}}}]}}}}");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFormQueryForSearchOROperator() {
    // Pipeline Identifier
    doReturn(List.of(PIPELINE_IDENTIFIER))
        .when(pmsPipelineService)
        .getPermittedPipelineIdentifier(any(), any(), any(), any());
    Query query = pmsExecutionService.formQueryForSearchOROperator(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, Collections.singletonList(PIPELINE_IDENTIFIER), null, null, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"should\":\\[\\{"
            + "\"terms\":\\{\"pipelineIdentifier\":\\[\"basichttpFail\"]}}]}}]}}}}");

    // No Pipeline Identifier
    doReturn(true).when(pmsPipelineService).validateViewPermission(any(), any(), any());
    query = pmsExecutionService.formQueryForSearchOROperator(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null, null, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}}]}}}}");

    // Filter properties
    query = pmsExecutionService.formQueryForSearchOROperator(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithTimeStatusAndNameFilter(), null);
    assertThat(query.toString())
        .isEqualTo("Query: "
            + "{\"constant_score\":{\"filter\":{\"bool\":{\"must\":[{\"term\":{\"accountId\":{\"value\":\"account_id\"}"
            + "}},{\"term\":{\"orgIdentifier\":{\"value\":\"orgId\"}}},{\"term\":{\"projectIdentifier\":{\"value\":"
            + "\"projId\"}}},{\"term\":{\"deleted\":{\"value\":false}}},{\"range\":{\"startTs\":{\"gte\":1712116800000,"
            + "\"lte\":1714653568130}}},{\"terms\":{\"status\":[\"SUCCESS\",\"EXPIRED\"]}},{\"bool\":{\"should\":[{"
            + "\"wildcard\":{\"pipelineIdentifier\":{\"case_insensitive\":true,\"value\":\"*test-pipeline-name*\"}}},{"
            + "\"wildcard\":{\"name\":{\"case_insensitive\":true,\"value\":\"*test-pipeline-name*\"}}}]}}]}}}}");

    // Filter properties with Tags
    query = pmsExecutionService.formQueryForSearchOROperator(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithTriggerAndPipelineTagsFilter(), null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"nested\":\\{\"path\":\"tags\","
            + "\"query\":\\{\"bool\":\\{\"should\":\\[\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{"
            + "\"value\":\"tagKey3\"}}},\\{\"term\":\\{\"tags.value\":\\{\"value\":\"tagValue3\"}}}]}},\\{"
            + "\"terms\":\\{\"tags.key\":\\[\"tagKey1\",\"tagValue2\"]}},\\{\"terms\":\\{\"tags.value\":\\["
            + "\"tagKey1\",\"tagValue2\"]}}]}}}},\\{\"terms\":\\{\"triggeredBy.triggerIdentifier\":\\["
            + "\"trigger1\",\"trigger2\"]}},\\{\"terms\":\\{\"triggerType\":\\[\"MANUAL\",\"WEBHOOK\"]}}]}}}}");

    // Filter properties with Module properties, all CD properties as array
    query = pmsExecutionService.formQueryForSearchOROperator(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithModulePropertiesFilter(Arrays.asList("nginx:stable", "nginx:latest"),
            Arrays.asList("env1", "env2"), Arrays.asList("service1", "service2"), Arrays.asList("Kubernetes", "Helm"),
            Arrays.asList("helm1", "helm2"), Arrays.asList("gitOps1", "gitOps2")),
        null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":\\{"
            + "\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}},\\{"
            + "\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"should\":\\[\\{\"bool\":\\{"
            + "\"must\":\\[\\{\"terms\":\\{\"cdModuleInfo.envIdentifiers\":\\[\"env1\",\"env2\"]}},\\{\"terms\":\\{"
            + "\"cdModuleInfo.artifactDisplayNames\":\\[\"nginx:stable\",\"nginx:latest\"]}},\\{\"terms\":\\{"
            + "\"cdModuleInfo.serviceIdentifiers\":\\[\"service1\",\"service2\"]}},\\{\"terms\":\\{\"cdModuleInfo."
            + "serviceDefinitionTypes\":\\[\"Kubernetes\",\"Helm\"]}},\\{\"terms\":\\{\"cdModuleInfo."
            + "helmChartVersions\":\\[\"helm1\",\"helm2\"]}},\\{\"terms\":\\{\"cdModuleInfo.gitOpsAppIdentifiers\":\\["
            + "\"gitOps1\",\"gitOps2\"]}}]}},\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"ciModuleInfo.branch\":\\{"
            + "\"value\":\"main\"}}},\\{\"term\":\\{\"ciModuleInfo.buildType\":\\{\"value\":\"branch\"}}},\\{\"term\":"
            + "\\{\"ciModuleInfo.tag\":\\{\"value\":\"tag\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO."
            + "event\":\\{\"value\":\"pullRequest\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO.pullRequest."
            + "sourceBranch\":\\{\"value\":\"sourceBranch\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO."
            + "pullRequest.targetBranch\":\\{\"value\":\"targetBranch\"}}}]}}]}}]}}}}");

    // Filter properties with Module properties, all CD properties as null
    query = pmsExecutionService.formQueryForSearchOROperator(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithModulePropertiesFilter(null, null, null, null, null, null), null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"should\":\\[\\{"
            + "\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"ciModuleInfo.branch\":\\{\"value\":\"main\"}}},\\{"
            + "\"term\":\\{\"ciModuleInfo.buildType\":\\{\"value\":\"branch\"}}},\\{\"term\":\\{\"ciModuleInfo."
            + "tag\":\\{\"value\":\"tag\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO.event\":\\{"
            + "\"value\":\"pullRequest\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO.pullRequest."
            + "sourceBranch\":\\{\"value\":\"sourceBranch\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO."
            + "pullRequest.targetBranch\":\\{\"value\":\"targetBranch\"}}}]}}]}}]}}}}");

    // Filter properties with Module properties, all CD properties as list of string/null values
    query = pmsExecutionService.formQueryForSearchOROperator(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithModulePropertiesFilter(Arrays.asList("nginx:stable", null, "nginx:latest", null),
            Arrays.asList(null, "env1", "env2", null), Arrays.asList("service1", null, null, "service2"),
            Arrays.asList("Kubernetes", "Helm"), Arrays.asList("helm1", "helm2"), Arrays.asList("gitOps1", "gitOps2")),
        null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":\\{"
            + "\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}},\\{"
            + "\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"should\":\\[\\{\"bool\":\\{"
            + "\"must\":\\[\\{\"bool\":\\{\"should\":\\[\\{\"terms\":\\{\"cdModuleInfo.envIdentifiers\":\\[\"env1\","
            + "\"env2\"]}},\\{\"bool\":\\{\"must_not\":\\[\\{\"exists\":\\{\"field\":\"cdModuleInfo.envIdentifiers\"}}]"
            + "}}]}},\\{\"bool\":\\{\"should\":\\[\\{\"terms\":\\{\"cdModuleInfo.artifactDisplayNames\":\\[\"nginx:"
            + "stable\",\"nginx:latest\"]}},\\{\"bool\":\\{\"must_not\":\\[\\{\"exists\":\\{\"field\":\"cdModuleInfo."
            + "artifactDisplayNames\"}}]}}]}},\\{\"bool\":\\{\"should\":\\[\\{\"terms\":\\{\"cdModuleInfo."
            + "serviceIdentifiers\":\\[\"service1\",\"service2\"]}},\\{\"bool\":\\{\"must_not\":\\[\\{\"exists\":\\{"
            + "\"field\":\"cdModuleInfo.serviceIdentifiers\"}}]}}]}},\\{\"terms\":\\{\"cdModuleInfo."
            + "serviceDefinitionTypes\":\\[\"Kubernetes\",\"Helm\"]}},\\{\"terms\":\\{\"cdModuleInfo."
            + "helmChartVersions\":\\[\"helm1\",\"helm2\"]}},\\{\"terms\":\\{\"cdModuleInfo.gitOpsAppIdentifiers\":\\["
            + "\"gitOps1\",\"gitOps2\"]}}]}},\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"ciModuleInfo.branch\":\\{"
            + "\"value\":\"main\"}}},\\{\"term\":\\{\"ciModuleInfo.buildType\":\\{\"value\":\"branch\"}}},\\{\"term\":"
            + "\\{\"ciModuleInfo.tag\":\\{\"value\":\"tag\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO."
            + "event\":\\{\"value\":\"pullRequest\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO.pullRequest."
            + "sourceBranch\":\\{\"value\":\"sourceBranch\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO."
            + "pullRequest.targetBranch\":\\{\"value\":\"targetBranch\"}}}]}}]}}]}}}}");

    // Filter properties with Module properties, all CD properties as list of string/null values or null
    query = pmsExecutionService.formQueryForSearchOROperator(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        getFilterPropertiesDTOWithModulePropertiesFilter("nginx:stable", Arrays.asList(null, "env1", "env2", null),
            "service2", null, Arrays.asList("helm1", "helm2"), Arrays.asList("gitOps1", "gitOps2")),
        null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":\\{"
            + "\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}},\\{"
            + "\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"should\":\\[\\{\"bool\":\\{"
            + "\"must\":\\[\\{\"bool\":\\{\"should\":\\[\\{\"terms\":\\{\"cdModuleInfo.envIdentifiers\":\\[\"env1\","
            + "\"env2\"]}},\\{\"bool\":\\{\"must_not\":\\[\\{\"exists\":\\{\"field\":\"cdModuleInfo.envIdentifiers\"}}]"
            + "}}]}},\\{\"terms\":\\{\"cdModuleInfo.artifactDisplayNames\":\\[\"nginx:stable\"]}},\\{\"terms\":\\{"
            + "\"cdModuleInfo.serviceIdentifiers\":\\[\"service2\"]}},\\{\"terms\":\\{\"cdModuleInfo."
            + "helmChartVersions\":\\[\"helm1\",\"helm2\"]}},\\{\"terms\":\\{\"cdModuleInfo.gitOpsAppIdentifiers\":\\["
            + "\"gitOps1\",\"gitOps2\"]}}]}},\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"ciModuleInfo.branch\":\\{"
            + "\"value\":\"main\"}}},\\{\"term\":\\{\"ciModuleInfo.buildType\":\\{\"value\":\"branch\"}}},\\{\"term\":"
            + "\\{\"ciModuleInfo.tag\":\\{\"value\":\"tag\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO."
            + "event\":\\{\"value\":\"pullRequest\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO.pullRequest."
            + "sourceBranch\":\\{\"value\":\"sourceBranch\"}}},\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO."
            + "pullRequest.targetBranch\":\\{\"value\":\"targetBranch\"}}}]}}]}}]}}}}");
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testFormQueryForSearchOROperatorWithQueuedStatus() {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION))
        .thenReturn(true);
    // Pipeline Identifier
    doReturn(List.of(PIPELINE_IDENTIFIER))
        .when(pmsPipelineService)
        .getPermittedPipelineIdentifier(any(), any(), any(), any());
    // Filter properties with Module properties, all CD properties as list of string/null values or null
    Query query =
        pmsExecutionService.formQueryForSearchOROperator(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
            getFilterPropertiesDTOWithModulePropertiesFilterWithStatus("nginx:stable",
                Arrays.asList(null, "env1", "env2", null), "service2", null, Arrays.asList("helm1", "helm2"),
                Arrays.asList("gitOps1", "gitOps2")),
            null);

    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\["
            + "\\{\"term\":\\{\"accountId\":\\{\"value\":\"account_id\"}}},"
            + "\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},"
            + "\\{\"term\":\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},"
            + "\\{\"term\":\\{\"deleted\":\\{\"value\":false}}},"
            + "\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},"
            + "\\{\"terms\":\\{\"status\":\\[\"SUCCESS\",\"QUEUED\",\"QUEUED_PLAN_CREATION\",\"STARTING_PLAN_"
            + "CREATION\"]}},"
            + "\\{\"bool\":\\{\"should\":\\["
            + "\\{\"bool\":\\{\"must\":\\["
            + "\\{\"bool\":\\{\"should\":\\["
            + "\\{\"terms\":\\{\"cdModuleInfo.envIdentifiers\":\\[\"env1\",\"env2\"]}},"
            + "\\{\"bool\":\\{\"must_not\":\\[\\{\"exists\":\\{\"field\":\"cdModuleInfo.envIdentifiers\"}}]}}]}},"
            + "\\{\"terms\":\\{\"cdModuleInfo.artifactDisplayNames\":\\[\"nginx:stable\"]}},"
            + "\\{\"terms\":\\{\"cdModuleInfo.serviceIdentifiers\":\\[\"service2\"]}},"
            + "\\{\"terms\":\\{\"cdModuleInfo.helmChartVersions\":\\[\"helm1\",\"helm2\"]}},"
            + "\\{\"terms\":\\{\"cdModuleInfo.gitOpsAppIdentifiers\":\\[\"gitOps1\",\"gitOps2\"]}}]}},"
            + "\\{\"bool\":\\{\"must\":\\["
            + "\\{\"term\":\\{\"ciModuleInfo.branch\":\\{\"value\":\"main\"}}},"
            + "\\{\"term\":\\{\"ciModuleInfo.buildType\":\\{\"value\":\"branch\"}}},"
            + "\\{\"term\":\\{\"ciModuleInfo.tag\":\\{\"value\":\"tag\"}}},"
            + "\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO.event\":\\{\"value\":\"pullRequest\"}}},"
            + "\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO.pullRequest.sourceBranch\":\\{\"value\":"
            + "\"sourceBranch\"}}},"
            + "\\{\"term\":\\{\"ciModuleInfo.ciExecutionInfoDTO.pullRequest.targetBranch\":\\{\"value\":"
            + "\"targetBranch\"}}}"
            + "]}}]}}]}}}}");
  }

  private PipelineExecutionFilterPropertiesDTO getFilterPropertiesDTOWithIdentifierFilter() {
    return PipelineExecutionFilterPropertiesDTO.builder()
        .pipelineIdentifiers(Collections.singletonList(PIPELINE_IDENTIFIER))
        .build();
  }

  private PipelineExecutionFilterPropertiesDTO getFilterPropertiesDTOWithTimeStatusAndNameFilter() {
    return PipelineExecutionFilterPropertiesDTO.builder()
        .timeRange(TimeRange.builder().startTime(1712116800000L).endTime(1714653568130L).build())
        .status(Arrays.asList(ExecutionStatus.SUCCESS, ExecutionStatus.EXPIRED))
        .pipelineName("test-pipeline-name")
        .build();
  }

  private PipelineExecutionFilterPropertiesDTO getFilterPropertiesDTOWithTimeStatusPlanExecutionIdAndNameFilter() {
    return PipelineExecutionFilterPropertiesDTO.builder()
        .timeRange(TimeRange.builder().startTime(1712116800000L).endTime(1714653568130L).build())
        .status(Arrays.asList(ExecutionStatus.SUCCESS, ExecutionStatus.EXPIRED))
        .pipelineName("test-pipeline-name")
        .planExecutionIds(Arrays.asList("executionId1", "executionId2"))
        .build();
  }

  private PipelineExecutionFilterPropertiesDTO getFilterPropertiesDTOWithTimeStatusInputSetIdentifierAndNameFilter() {
    return PipelineExecutionFilterPropertiesDTO.builder()
        .timeRange(TimeRange.builder().startTime(1712116800000L).endTime(1714653568130L).build())
        .status(Arrays.asList(ExecutionStatus.SUCCESS, ExecutionStatus.EXPIRED))
        .pipelineName("test-pipeline-name")
        .inputSetIdentifiers(Arrays.asList("inputSetIdentifier1", "inputSetIdentifier2"))
        .build();
  }

  private PipelineExecutionFilterPropertiesDTO getFilterPropertiesDTOWithTriggerAndPipelineTagsFilter() {
    return PipelineExecutionFilterPropertiesDTO.builder()
        .triggerIdentifiers(Arrays.asList("trigger1", "trigger2"))
        .triggerTypes(Arrays.asList(TriggerType.MANUAL, TriggerType.WEBHOOK))
        .pipelineTags(Arrays.asList(NGTag.builder().key("tagKey1").build(), NGTag.builder().key("tagValue2").build(),
            NGTag.builder().key("tagKey3").value("tagValue3").build()))
        .build();
  }

  private PipelineExecutionFilterPropertiesDTO getFilterPropertiesDTOWithModulePropertiesFilter(
      Object artifactDisplayNames, Object envIdentifiers, Object serviceIdentifiers, Object serviceDefinitionTypes,
      Object helmChartVersions, Object gitOpsAppIdentifiers) {
    return PipelineExecutionFilterPropertiesDTO.builder()
        .moduleProperties(getModuleProperties(artifactDisplayNames, envIdentifiers, serviceIdentifiers,
            serviceDefinitionTypes, helmChartVersions, gitOpsAppIdentifiers))
        .build();
  }

  private PipelineExecutionFilterPropertiesDTO getFilterPropertiesDTOWithModulePropertiesFilterWithStatus(
      Object artifactDisplayNames, Object envIdentifiers, Object serviceIdentifiers, Object serviceDefinitionTypes,
      Object helmChartVersions, Object gitOpsAppIdentifiers) {
    return PipelineExecutionFilterPropertiesDTO.builder()
        .moduleProperties(getModuleProperties(artifactDisplayNames, envIdentifiers, serviceIdentifiers,
            serviceDefinitionTypes, helmChartVersions, gitOpsAppIdentifiers))
        .status(Arrays.asList(ExecutionStatus.SUCCESS, ExecutionStatus.QUEUED))
        .build();
  }

  private PipelineExecutionFilterPropertiesDTO getFilterPropertiesDTOWithGitFilter(String branchName, String repo) {
    return PipelineExecutionFilterPropertiesDTO.builder().branchName(branchName).repo(repo).build();
  }

  private void enableFilterExecutionsByGit() throws IOException {
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_FILTER_EXECUTIONS_BY_GIT_EVENTS))
        .thenReturn(true);
    List<UserSourceCodeManagerResponseDTO> userSourceCodeManagerResponseDTO = new ArrayList<>();
    userSourceCodeManagerResponseDTO.add(GithubSCMResponseDTO.builder()
                                             .accountIdentifier(ACCOUNT_ID)
                                             .userIdentifier(USER_IDENTIFIER)
                                             .type(SCMType.GITHUB)
                                             .userName("username")
                                             .userEmail("userEmail")
                                             .build());
    userSourceCodeManagerResponseDTO.add(GitlabSCMResponseDTO.builder()
                                             .accountIdentifier(ACCOUNT_ID)
                                             .userIdentifier(USER_IDENTIFIER)
                                             .type(SCMType.GITLAB)
                                             .userName("username1")
                                             .userEmail("userEmail1")
                                             .build());
    userSourceCodeManagerResponseDTO.add(BitbucketSCMResponseDTO.builder()
                                             .accountIdentifier(ACCOUNT_ID)
                                             .userIdentifier(USER_IDENTIFIER)
                                             .type(SCMType.BITBUCKET)
                                             .userName("username2")
                                             .userEmail("userEmail2")
                                             .build());
    UserSourceCodeManagerResponseDTOList userSourceCodeManagerResponseDTOList =
        UserSourceCodeManagerResponseDTOList.builder()
            .userSourceCodeManagerResponseDTOList(userSourceCodeManagerResponseDTO)
            .build();
    Call<ResponseDTO<UserSourceCodeManagerResponseDTOList>> gitSyncClientCall = mock(Call.class);
    doReturn(gitSyncClientCall).when(gitSyncManagerClient).get(any(), any());
    when(gitSyncClientCall.execute())
        .thenReturn(retrofit2.Response.success(ResponseDTO.newResponse(userSourceCodeManagerResponseDTOList)));
  }

  private ModulePropertiesDTO getModuleProperties(Object artifactDisplayNames, Object envIdentifiers,
      Object serviceIdentifiers, Object serviceDefinitionTypes, Object helmChartVersions, Object gitOpsAppIdentifiers) {
    CDModulePropertiesDTO cd = CDModulePropertiesDTO.builder()
                                   .artifactDisplayNames(artifactDisplayNames)
                                   .serviceDefinitionTypes(serviceDefinitionTypes)
                                   .serviceIdentifiers(serviceIdentifiers)
                                   .envIdentifiers(envIdentifiers)
                                   .helmChartVersions(helmChartVersions)
                                   .gitOpsAppIdentifiers(gitOpsAppIdentifiers)
                                   .build();

    CIPullRequestDTO ciPullRequestDTO =
        CIPullRequestDTO.builder().targetBranch("targetBranch").sourceBranch("sourceBranch").build();
    CIModulePropertiesDTO ci =
        CIModulePropertiesDTO.builder()
            .ciExecutionInfoDTO(CIExecutionInfoDTO.builder().event("pullRequest").pullRequest(ciPullRequestDTO).build())
            .tag("tag")
            .branch("main")
            .buildType("branch")
            .build();

    return ModulePropertiesDTO.builder().cd(cd).ci(ci).build();
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testFormCriteria_1() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);

    doReturn(Arrays.asList(PIPELINE_IDENTIFIER))
        .when(pmsPipelineService)
        .getPermittedPipelineIdentifier(any(), any(), any(), any());

    PipelineExecutionFilterPropertiesDTO pipelineExecutionFilterPropertiesDTO =
        PipelineExecutionFilterPropertiesDTO.builder()
            .triggerTypes(Collections.singletonList(TriggerType.WEBHOOK))
            .build();
    doNothing().when(pmsPipelineServiceHelper).setPermittedPipelines(any(), any(), any(), any(), any());
    doReturn(FilterDTO.builder().filterProperties(pipelineExecutionFilterPropertiesDTO).build())
        .when(filterService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "FILTER_IDENTIFIER", FilterType.PIPELINEEXECUTION);
    Criteria form3 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        "FILTER_IDENTIFIER", null, null, null, null, false, !PIPELINE_DELETED, true, null);
    assertThat(form3.getCriteriaObject().toString())
        .matches("Document\\{\\{accountId=account_id, orgIdentifier=orgId, projectIdentifier=projId, "
            + "pipelineIdentifier=Document\\{\\{\\$in=\\[basichttpFail]}}, "
            + "\\$and=\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{executionTriggerInfo\\.triggerType=Document\\{\\{\\$in=\\[WEBHOOK]}}}}]}}]}}");
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testFormCriteria_2() throws IOException {
    // flow to check if myDeployments is true
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);

    doReturn(Arrays.asList(PIPELINE_IDENTIFIER))
        .when(pmsPipelineService)
        .getPermittedPipelineIdentifier(any(), any(), any(), any());

    PipelineExecutionFilterPropertiesDTO pipelineExecutionFilterPropertiesDTO =
        PipelineExecutionFilterPropertiesDTO.builder()
            .triggerTypes(Collections.singletonList(TriggerType.WEBHOOK))
            .build();
    doNothing().when(pmsPipelineServiceHelper).setPermittedPipelines(any(), any(), any(), any(), any());
    doReturn(FilterDTO.builder().filterProperties(pipelineExecutionFilterPropertiesDTO).build())
        .when(filterService)
        .get(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, "FILTER_IDENTIFIER", FilterType.PIPELINEEXECUTION);
    Map<String, String> extra = new HashMap<>();
    extra.put("email", "emailEmail");
    TriggeredBy triggeredBy1 = TriggeredBy.newBuilder().setIdentifier(IDENTIFIER).putAllExtraInfo(extra).build();
    doReturn(triggeredBy1).when(triggeredByHelper).getFromSecurityContext();
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_FILTER_EXECUTIONS_BY_GIT_EVENTS))
        .thenReturn(false);
    Criteria form3 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        "FILTER_IDENTIFIER", null, null, null, null, true, !PIPELINE_DELETED, true, null);
    assertThat(form3.getCriteriaObject().get("executionTriggerInfo.triggerType").toString()).isEqualTo("MANUAL");
    assertThat(form3.getCriteriaObject().get("executionTriggerInfo.triggeredBy.extraInfo.email").toString())
        .isEqualTo("emailEmail");

    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_FILTER_EXECUTIONS_BY_GIT_EVENTS))
        .thenReturn(true);
    doReturn(triggeredBy1).when(triggeredByHelper).getFromSecurityContext();
    List<UserSourceCodeManagerResponseDTO> userSourceCodeManagerResponseDTO = new ArrayList<>();
    userSourceCodeManagerResponseDTO.add(GithubSCMResponseDTO.builder()
                                             .accountIdentifier(ACCOUNT_ID)
                                             .userIdentifier(USER_IDENTIFIER)
                                             .type(SCMType.GITHUB)
                                             .userName("username")
                                             .userEmail("userEmail")
                                             .build());
    userSourceCodeManagerResponseDTO.add(GitlabSCMResponseDTO.builder()
                                             .accountIdentifier(ACCOUNT_ID)
                                             .userIdentifier(USER_IDENTIFIER)
                                             .type(SCMType.GITLAB)
                                             .userName("username1")
                                             .userEmail("userEmail1")
                                             .build());
    userSourceCodeManagerResponseDTO.add(BitbucketSCMResponseDTO.builder()
                                             .accountIdentifier(ACCOUNT_ID)
                                             .userIdentifier(USER_IDENTIFIER)
                                             .type(SCMType.BITBUCKET)
                                             .userName("username2")
                                             .userEmail("userEmail2")
                                             .build());
    UserSourceCodeManagerResponseDTOList userSourceCodeManagerResponseDTOList =
        UserSourceCodeManagerResponseDTOList.builder()
            .userSourceCodeManagerResponseDTOList(userSourceCodeManagerResponseDTO)
            .build();
    Call<ResponseDTO<UserSourceCodeManagerResponseDTOList>> gitSyncClientCall = mock(Call.class);
    doReturn(gitSyncClientCall).when(gitSyncManagerClient).get(any(), any());
    when(gitSyncClientCall.execute())
        .thenReturn(retrofit2.Response.success(ResponseDTO.newResponse(userSourceCodeManagerResponseDTOList)));
    Criteria form4 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        "FILTER_IDENTIFIER", null, null, null, null, true, !PIPELINE_DELETED, true, null);

    assertThat(form4.getCriteriaObject().get("$or").toString())
        .isEqualTo("[Document{{executionTriggerInfo.triggerType=MANUAL, "
            + "executionTriggerInfo.triggeredBy.extraInfo.email=emailEmail}}, "
            + "Document{{executionTriggerInfo.triggerType=WEBHOOK, "
            + "executionTriggerInfo.triggeredBy.extraInfo.gitUser=Document{{$in=[username, username1, "
            + "username2]}}}}, Document{{executionTriggerInfo.triggerType=WEBHOOK_CUSTOM, "
            + "executionTriggerInfo.triggeredBy.extraInfo.email=emailEmail}}]");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testFormCriteriaOROperatorOnModules() {
    when(pmsPipelineService.getPermittedPipelineIdentifier(any(), any(), any(), any()))
        .thenReturn(PIPELINE_IDENTIFIER_LIST);
    Criteria form = pmsExecutionService.formCriteriaOROperatorOnModules(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER_LIST, null, null);
    BasicDBList orList = (BasicDBList) form.getCriteriaObject().get("$or");
    Document scopeCriteria = (Document) orList.get(0);
    Document pipelineIdentifierCriteria = (Document) scopeCriteria.get("pipelineIdentifier");
    List<String> pipelineList = (List<String>) pipelineIdentifierCriteria.get("$in");

    assertThat(form.getCriteriaObject().get("accountId").toString().contentEquals(ACCOUNT_ID)).isEqualTo(true);
    assertThat(form.getCriteriaObject().get("orgIdentifier").toString().contentEquals(ORG_IDENTIFIER)).isEqualTo(true);
    assertThat(form.getCriteriaObject().get("projectIdentifier").toString().contentEquals(PROJ_IDENTIFIER))
        .isEqualTo(true);
    assertThat(pipelineList.equals(PIPELINE_IDENTIFIER_LIST)).isEqualTo(true);
    assertThat(form.getCriteriaObject().containsKey("pipelineIdentifier")).isEqualTo(false);
    assertThat(form.getCriteriaObject().get("pipelineDeleted")).isNotEqualTo(true);
    assertThat(form.getCriteriaObject().containsKey("executionTriggerInfo")).isEqualTo(false);
    assertThat(form.getCriteriaObject().get("isLatestExecution")).isNotEqualTo(false);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testFormCriteriaWithModuleName() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);
    Criteria form =
        pmsExecutionService.formCriteria(null, null, null, null, null, null, "cd", null, null, false, true, true, null);
    Criteria criteria = new Criteria();

    Criteria searchCriteria = new Criteria();
    searchCriteria.orOperator(Criteria.where(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.modules)
                                  .is(Collections.singletonList(ModuleType.PMS.name().toLowerCase())),
        Criteria.where(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.modules).in("cd"));

    criteria.andOperator(searchCriteria);

    assertThat(((Document) ((BasicDBList) form.getCriteriaObject().get("$and")).get(0)).size()).isEqualTo(2);
    assertThat(searchCriteria.getCriteriaObject().toString())
        .isEqualTo("Document{{$or=[Document{{modules=[pms]}}, Document{{modules=Document{{$in=[cd]}}}}]}}");
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetInputSetYaml() {
    doReturn(planExecutionMetadata)
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(ACCOUNT_ID, PLAN_EXECUTION_ID,
            Set.of(PlanExecutionMetadataKeys.inputSetYaml, PlanExecutionMetadataKeys.harnessVersion));
    doReturn(Optional.of(executionSummaryEntity))
        .when(pmsExecutionSummaryRepository)
        .findByPlanExecutionIdAndPipelineDeletedNot(PLAN_EXECUTION_ID, !PIPELINE_DELETED);
    doReturn(null).when(pmsGitSyncHelper).getEntityGitDetailsFromBytes(any());
    doReturn(template).when(validateAndMergeHelper).getPipelineTemplate((ScopeInfo) any(), any(), any(), any(), any());

    List<String> inputSetIds = Arrays.asList("inputset1", "inputset2");
    doReturn(inputSetIds).when(executionSummaryEntity).getInputSetIdentifiers();

    List<InputSetSummaryResponseDTOPMS> inputSets = new ArrayList<>();
    inputSets.add(InputSetSummaryResponseDTOPMS.builder().identifier("inputset1").name("My InputSet 1").build());
    inputSets.add(InputSetSummaryResponseDTOPMS.builder().identifier("inputset2").name("My InputSet 2").build());
    BulkInputSetsResponseDTO bulkResponse = BulkInputSetsResponseDTO.builder().inputSets(inputSets).build();

    doReturn(bulkResponse)
        .when(pmsInputSetService)
        .getBulkInputSets(any(ScopeInfo.class), eq(PIPELINE_IDENTIFIER), any(BulkInputSetsRequestDTO.class));

    InputSetYamlWithTemplateDTO result = pmsExecutionService.getInputSetYamlWithTemplate(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PLAN_EXECUTION_ID, PIPELINE_DELETED, false, ResolveInputYamlType.UNKNOWN);

    assertThat(result.getInputSetYaml()).isEqualTo(inputSetYaml);
    assertThat(result.getInputSetDetails()).isNotNull();
    assertThat(result.getInputSetDetails()).hasSize(2);

    Map<String, InputSetDetailsDTO> detailsMap =
        result.getInputSetDetails().stream().collect(Collectors.toMap(InputSetDetailsDTO::getIdentifier, dto -> dto));

    assertThat(detailsMap.get("inputset1").getName()).isEqualTo("My InputSet 1");
    assertThat(detailsMap.get("inputset2").getName()).isEqualTo("My InputSet 2");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetInputSetYamlWithNoInputSetIdentifiers() {
    doReturn(planExecutionMetadata)
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(ACCOUNT_ID, PLAN_EXECUTION_ID,
            Set.of(PlanExecutionMetadataKeys.inputSetYaml, PlanExecutionMetadataKeys.harnessVersion));
    doReturn(Optional.of(executionSummaryEntity))
        .when(pmsExecutionSummaryRepository)
        .findByPlanExecutionIdAndPipelineDeletedNot(PLAN_EXECUTION_ID, !PIPELINE_DELETED);
    doReturn(null).when(pmsGitSyncHelper).getEntityGitDetailsFromBytes(any());
    doReturn(template).when(validateAndMergeHelper).getPipelineTemplate((ScopeInfo) any(), any(), any(), any(), any());

    doReturn(null).when(executionSummaryEntity).getInputSetIdentifiers();

    InputSetYamlWithTemplateDTO result = pmsExecutionService.getInputSetYamlWithTemplate(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PLAN_EXECUTION_ID, PIPELINE_DELETED, false, ResolveInputYamlType.UNKNOWN);

    assertThat(result.getInputSetYaml()).isEqualTo(inputSetYaml);
    assertThat(result.getInputSetDetails()).isNotNull();
    assertThat(result.getInputSetDetails()).isEmpty();

    verify(pmsInputSetService, never()).getBulkInputSets(any(), any(), any());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testGetInputSetYamlWithPartialResults() {
    doReturn(planExecutionMetadata)
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(ACCOUNT_ID, PLAN_EXECUTION_ID,
            Set.of(PlanExecutionMetadataKeys.inputSetYaml, PlanExecutionMetadataKeys.harnessVersion));
    doReturn(Optional.of(executionSummaryEntity))
        .when(pmsExecutionSummaryRepository)
        .findByPlanExecutionIdAndPipelineDeletedNot(PLAN_EXECUTION_ID, !PIPELINE_DELETED);
    doReturn(null).when(pmsGitSyncHelper).getEntityGitDetailsFromBytes(any());
    doReturn(template).when(validateAndMergeHelper).getPipelineTemplate((ScopeInfo) any(), any(), any(), any(), any());

    List<String> inputSetIds = Arrays.asList("inputset1", "inputset2", "inputset3");
    doReturn(inputSetIds).when(executionSummaryEntity).getInputSetIdentifiers();

    List<InputSetSummaryResponseDTOPMS> inputSets = new ArrayList<>();
    inputSets.add(InputSetSummaryResponseDTOPMS.builder().identifier("inputset1").name("My InputSet 1").build());
    inputSets.add(InputSetSummaryResponseDTOPMS.builder().identifier("inputset3").name("My InputSet 3").build());
    BulkInputSetsResponseDTO bulkResponse = BulkInputSetsResponseDTO.builder().inputSets(inputSets).build();

    doReturn(bulkResponse)
        .when(pmsInputSetService)
        .getBulkInputSets(any(ScopeInfo.class), eq(PIPELINE_IDENTIFIER), any(BulkInputSetsRequestDTO.class));

    InputSetYamlWithTemplateDTO result = pmsExecutionService.getInputSetYamlWithTemplate(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PLAN_EXECUTION_ID, PIPELINE_DELETED, false, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);

    assertThat(result.getInputSetYaml()).isEqualTo(inputSetYaml);
    assertThat(result.getInputSetDetails()).isNotNull();
    assertThat(result.getInputSetDetails()).hasSize(3);

    Map<String, InputSetDetailsDTO> detailsMap =
        result.getInputSetDetails().stream().collect(Collectors.toMap(InputSetDetailsDTO::getIdentifier, dto -> dto));

    assertThat(detailsMap.get("inputset1").getName()).isEqualTo("My InputSet 1");
    assertThat(detailsMap.get("inputset2").getName()).isNull();
    assertThat(detailsMap.get("inputset3").getName()).isEqualTo("My InputSet 3");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetInputSetYamlWithInputSetBranchName() {
    doReturn(planExecutionMetadata)
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(ACCOUNT_ID, PLAN_EXECUTION_ID,
            Set.of(PlanExecutionMetadataKeys.inputSetYaml, PlanExecutionMetadataKeys.harnessVersion));
    doReturn(Optional.of(executionSummaryEntity))
        .when(pmsExecutionSummaryRepository)
        .findByPlanExecutionIdAndPipelineDeletedNot(PLAN_EXECUTION_ID, !PIPELINE_DELETED);
    doReturn(null).when(pmsGitSyncHelper).getEntityGitDetailsFromBytes(any());
    doReturn(template).when(validateAndMergeHelper).getPipelineTemplate((ScopeInfo) any(), any(), any(), any(), any());

    List<String> inputSetIds = Arrays.asList("inputset1", "inputset2");
    doReturn(inputSetIds).when(executionSummaryEntity).getInputSetIdentifiers();
    String inputSetBranchName = "feature-branch";
    doReturn(inputSetBranchName).when(executionSummaryEntity).getInputSetBranchName();

    List<InputSetSummaryResponseDTOPMS> inputSets = new ArrayList<>();
    inputSets.add(InputSetSummaryResponseDTOPMS.builder().identifier("inputset1").name("My InputSet 1").build());
    inputSets.add(InputSetSummaryResponseDTOPMS.builder().identifier("inputset2").name("My InputSet 2").build());
    BulkInputSetsResponseDTO bulkResponse = BulkInputSetsResponseDTO.builder().inputSets(inputSets).build();

    doReturn(bulkResponse)
        .when(pmsInputSetService)
        .getBulkInputSets(any(ScopeInfo.class), eq(PIPELINE_IDENTIFIER), any(BulkInputSetsRequestDTO.class));

    InputSetYamlWithTemplateDTO result = pmsExecutionService.getInputSetYamlWithTemplate(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PLAN_EXECUTION_ID, PIPELINE_DELETED, false, ResolveInputYamlType.UNKNOWN);

    assertThat(result.getInputSetYaml()).isEqualTo(inputSetYaml);
    assertThat(result.getInputSetDetails()).isNotNull();
    assertThat(result.getInputSetDetails()).hasSize(2);

    Map<String, InputSetDetailsDTO> detailsMap =
        result.getInputSetDetails().stream().collect(Collectors.toMap(InputSetDetailsDTO::getIdentifier, dto -> dto));

    assertThat(detailsMap.get("inputset1").getName()).isEqualTo("My InputSet 1");
    assertThat(detailsMap.get("inputset2").getName()).isEqualTo("My InputSet 2");
    assertThat(result.getInputSetBranchName()).isEqualTo("feature-branch");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testGetInputSetYamlWithNullInputSetBranchName() {
    doReturn(planExecutionMetadata)
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(ACCOUNT_ID, PLAN_EXECUTION_ID,
            Set.of(PlanExecutionMetadataKeys.inputSetYaml, PlanExecutionMetadataKeys.harnessVersion));
    doReturn(Optional.of(executionSummaryEntity))
        .when(pmsExecutionSummaryRepository)
        .findByPlanExecutionIdAndPipelineDeletedNot(PLAN_EXECUTION_ID, !PIPELINE_DELETED);
    doReturn(null).when(pmsGitSyncHelper).getEntityGitDetailsFromBytes(any());
    doReturn(template).when(validateAndMergeHelper).getPipelineTemplate((ScopeInfo) any(), any(), any(), any(), any());

    List<String> inputSetIds = Arrays.asList("inputset1", "inputset2");
    doReturn(inputSetIds).when(executionSummaryEntity).getInputSetIdentifiers();
    doReturn(null).when(executionSummaryEntity).getInputSetBranchName();

    List<InputSetSummaryResponseDTOPMS> inputSets = new ArrayList<>();
    inputSets.add(InputSetSummaryResponseDTOPMS.builder().identifier("inputset1").name("My InputSet 1").build());
    inputSets.add(InputSetSummaryResponseDTOPMS.builder().identifier("inputset2").name("My InputSet 2").build());
    BulkInputSetsResponseDTO bulkResponse = BulkInputSetsResponseDTO.builder().inputSets(inputSets).build();

    doReturn(bulkResponse)
        .when(pmsInputSetService)
        .getBulkInputSets(any(ScopeInfo.class), eq(PIPELINE_IDENTIFIER), any(BulkInputSetsRequestDTO.class));

    InputSetYamlWithTemplateDTO result = pmsExecutionService.getInputSetYamlWithTemplate(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PLAN_EXECUTION_ID, PIPELINE_DELETED, false, ResolveInputYamlType.UNKNOWN);

    assertThat(result.getInputSetYaml()).isEqualTo(inputSetYaml);
    assertThat(result.getInputSetDetails()).isNotNull();
    assertThat(result.getInputSetDetails()).hasSize(2);

    Map<String, InputSetDetailsDTO> detailsMap =
        result.getInputSetDetails().stream().collect(Collectors.toMap(InputSetDetailsDTO::getIdentifier, dto -> dto));

    assertThat(detailsMap.get("inputset1").getName()).isEqualTo("My InputSet 1");
    assertThat(detailsMap.get("inputset2").getName()).isEqualTo("My InputSet 2");
    assertThat(result.getInputSetBranchName()).isNull();
  }

  @Test
  @Owner(developers = MEET)
  @Category(UnitTests.class)
  public void testGetInputSetYamlWithResolvedTriggerExpressions() throws IOException {
    ClassLoader classLoaderWithTriggerExpression = this.getClass().getClassLoader();
    String inputSetWithTriggerExpressionFilename = "inputsetWithTriggerExpression.yaml";
    String inputSetYamlWithTriggerExpression = Resources.toString(
        Objects.requireNonNull(classLoaderWithTriggerExpression.getResource(inputSetWithTriggerExpressionFilename)),
        StandardCharsets.UTF_8);

    String inputSetWithResolvedTriggerExpressionFilename = "inputsetWithResolvedTriggerExpressions.yaml";

    PipelineExecutionSummaryEntity executionSummaryEntity1 =
        PipelineExecutionSummaryEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .pipelineIdentifier(PIPELINE_IDENTIFIER)
            .planExecutionId(PLAN_EXECUTION_ID)
            .name(PLAN_EXECUTION_ID)
            .resolvedUserInputSetYaml(inputSetWithResolvedTriggerExpressionFilename)
            .runSequence(0)
            .pipelineTemplate(template)
            .build();

    PlanExecutionMetadata planExecutionMetadata1 =
        PlanExecutionMetadata.builder().inputSetYaml(inputSetYamlWithTriggerExpression).build();

    doReturn(planExecutionMetadata1)
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(ACCOUNT_ID, PLAN_EXECUTION_ID,
            Set.of(PlanExecutionMetadataKeys.inputSetYaml, PlanExecutionMetadataKeys.harnessVersion));
    doReturn(Optional.of(executionSummaryEntity1))
        .when(pmsExecutionSummaryRepository)
        .findByPlanExecutionIdAndPipelineDeletedNot(PLAN_EXECUTION_ID, !PIPELINE_DELETED);
    doReturn(null).when(pmsGitSyncHelper).getEntityGitDetailsFromBytes(any());
    doReturn(template).when(validateAndMergeHelper).getPipelineTemplate((ScopeInfo) any(), any(), any(), any(), any());
    doReturn(inputSetWithResolvedTriggerExpressionFilename)
        .when(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID,
            ResolveInputYamlType.RESOLVE_TRIGGER_EXPRESSIONS, HarnessYamlVersion.V0);
    String inputSet = pmsExecutionService
                          .getInputSetYamlWithTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID,
                              PIPELINE_DELETED, false, ResolveInputYamlType.RESOLVE_TRIGGER_EXPRESSIONS)
                          .getInputSetYaml();

    assertThat(inputSet).isEqualTo(inputSetWithResolvedTriggerExpressionFilename);
    verify(yamlExpressionResolveHelper, times(0))
        .resolveExpressionsInYaml(inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID,
            ResolveInputYamlType.RESOLVE_TRIGGER_EXPRESSIONS, HarnessYamlVersion.V0);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testMergeRuntimeInputIntoPipeline_1() throws IOException {
    ClassLoader classLoaderWithTriggerExpression = this.getClass().getClassLoader();
    String inputSetWithTriggerExpressionFilename = "inputsetWithTriggerExpression.yaml";
    String inputSetYamlWithTriggerExpression = Resources.toString(
        Objects.requireNonNull(classLoaderWithTriggerExpression.getResource(inputSetWithTriggerExpressionFilename)),
        StandardCharsets.UTF_8);

    String inputSetWithResolvedTriggerExpressionFilename = "inputsetWithResolvedTriggerExpressions.yaml";

    PipelineExecutionSummaryEntity executionSummaryEntity1 = PipelineExecutionSummaryEntity.builder()
                                                                 .accountId(ACCOUNT_ID)
                                                                 .orgIdentifier(ORG_IDENTIFIER)
                                                                 .projectIdentifier(PROJ_IDENTIFIER)
                                                                 .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                                 .planExecutionId(PLAN_EXECUTION_ID)
                                                                 .name(PLAN_EXECUTION_ID)
                                                                 .pipelineTemplate(template)
                                                                 .runSequence(0)
                                                                 .build();

    PlanExecutionMetadata planExecutionMetadata1 =
        PlanExecutionMetadata.builder().inputSetYaml(inputSetYamlWithTriggerExpression).build();

    doReturn(planExecutionMetadata1)
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(ACCOUNT_ID, PLAN_EXECUTION_ID,
            Set.of(PlanExecutionMetadataKeys.inputSetYaml, PlanExecutionMetadataKeys.harnessVersion));
    doReturn(Optional.of(executionSummaryEntity1))
        .when(pmsExecutionSummaryRepository)
        .findByPlanExecutionId(PLAN_EXECUTION_ID);
    doReturn(inputSetWithResolvedTriggerExpressionFilename)
        .when(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID,
            ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, HarnessYamlVersion.V0);
    String inputSet = pmsExecutionService.mergeRuntimeInputIntoPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PLAN_EXECUTION_ID, false, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);

    assertThat(inputSet).isEqualTo(InputSetMergeHelper.mergeInputSetIntoPipeline(template, "", false));
    verify(yamlExpressionResolveHelper, times(0))
        .resolveExpressionsInYaml(inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID,
            ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, HarnessYamlVersion.V0);

    doReturn(null)
        .when(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID,
            ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, HarnessYamlVersion.V0);
    inputSet = pmsExecutionService.mergeRuntimeInputIntoPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PLAN_EXECUTION_ID, false, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);
    assertThat(inputSet).isEqualTo(InputSetMergeHelper.mergeInputSetIntoPipeline(template, "", false));
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testInputIntoPipelineForDeleted() throws IOException {
    ClassLoader classLoaderWithTriggerExpression = this.getClass().getClassLoader();
    String inputSetWithTriggerExpressionFilename = "inputsetWithTriggerExpression.yaml";
    String inputSetYamlWithTriggerExpression = Resources.toString(
        Objects.requireNonNull(classLoaderWithTriggerExpression.getResource(inputSetWithTriggerExpressionFilename)),
        StandardCharsets.UTF_8);

    String inputSetWithResolvedTriggerExpressionFilename = "inputsetWithResolvedTriggerExpressions.yaml";

    PipelineExecutionSummaryEntity executionSummaryEntity1 = PipelineExecutionSummaryEntity.builder()
                                                                 .accountId(ACCOUNT_ID)
                                                                 .orgIdentifier(ORG_IDENTIFIER)
                                                                 .projectIdentifier(PROJ_IDENTIFIER)
                                                                 .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                                 .planExecutionId(PLAN_EXECUTION_ID)
                                                                 .name(PLAN_EXECUTION_ID)
                                                                 .pipelineTemplate(template)
                                                                 .runSequence(0)
                                                                 .build();

    PlanExecutionMetadata planExecutionMetadata1 =
        PlanExecutionMetadata.builder().inputSetYaml(inputSetYamlWithTriggerExpression).build();

    doReturn(planExecutionMetadata1)
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(ACCOUNT_ID, PLAN_EXECUTION_ID,
            Set.of(PlanExecutionMetadataKeys.inputSetYaml, PlanExecutionMetadataKeys.harnessVersion));
    doReturn(Optional.of(executionSummaryEntity1))
        .when(pmsExecutionSummaryRepository)
        .findByPlanExecutionId(PLAN_EXECUTION_ID);
    doThrow(InvalidRequestException.class)
        .when(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID,
            ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, HarnessYamlVersion.V0);
    String inputSet = pmsExecutionService.mergeRuntimeInputIntoPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PLAN_EXECUTION_ID, true, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);

    assertThat(inputSet).isEqualTo(InputSetMergeHelper.mergeInputSetIntoPipeline(template, "", false));
    verify(yamlExpressionResolveHelper, times(0))
        .resolveExpressionsInYaml(inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID,
            ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, HarnessYamlVersion.V0);

    doReturn(null)
        .when(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID,
            ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, HarnessYamlVersion.V0);
    inputSet = pmsExecutionService.mergeRuntimeInputIntoPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PLAN_EXECUTION_ID, true, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);
    assertThat(inputSet).isEqualTo(InputSetMergeHelper.mergeInputSetIntoPipeline(template, "", false));
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testMergeRuntimeInputIntoPipeline() throws IOException {
    ClassLoader classLoaderWithTriggerExpression = this.getClass().getClassLoader();
    String inputSetWithTriggerExpressionFilename = "inputsetWithTriggerExpression.yaml";
    String inputSetYamlWithTriggerExpression = Resources.toString(
        Objects.requireNonNull(classLoaderWithTriggerExpression.getResource(inputSetWithTriggerExpressionFilename)),
        StandardCharsets.UTF_8);

    String inputSetWithResolvedTriggerExpressionFilename = "inputsetWithResolvedTriggerExpressions.yaml";

    PipelineExecutionSummaryEntity executionSummaryEntity1 = PipelineExecutionSummaryEntity.builder()
                                                                 .accountId(ACCOUNT_ID)
                                                                 .orgIdentifier(ORG_IDENTIFIER)
                                                                 .projectIdentifier(PROJ_IDENTIFIER)
                                                                 .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                                 .planExecutionId(PLAN_EXECUTION_ID)
                                                                 .name(PLAN_EXECUTION_ID)
                                                                 .runSequence(0)
                                                                 .build();

    PlanExecutionMetadata planExecutionMetadata1 =
        PlanExecutionMetadata.builder().inputSetYaml(inputSetYamlWithTriggerExpression).build();

    doReturn(planExecutionMetadata1)
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(ACCOUNT_ID, PLAN_EXECUTION_ID,
            Set.of(PlanExecutionMetadataKeys.inputSetYaml, PlanExecutionMetadataKeys.harnessVersion));
    doReturn(Optional.of(executionSummaryEntity1))
        .when(pmsExecutionSummaryRepository)
        .findByPlanExecutionId(PLAN_EXECUTION_ID);
    doReturn(inputSetWithResolvedTriggerExpressionFilename)
        .when(yamlExpressionResolveHelper)
        .resolveExpressionsInYaml(inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID,
            ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, HarnessYamlVersion.V0);
    String inputSet = pmsExecutionService.mergeRuntimeInputIntoPipeline(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        PLAN_EXECUTION_ID, true, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS);

    assertThat(inputSet).isEqualTo("");
    verify(yamlExpressionResolveHelper, times(0))
        .resolveExpressionsInYaml(inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID,
            ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, HarnessYamlVersion.V0);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetCountOfExecutions() {
    pmsExecutionService.getCountOfExecutions(Criteria.where("key").is("value"));
    verify(pmsExecutionSummaryRepository, times(1)).getCountOfExecutionSummary(any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetOrchestrationGraph() {
    pmsExecutionService.getOrchestrationGraph(ACCOUNT_ID, "stageNodeId", "planExecutionId", "stageNodeExecutionId");
    verify(graphGenerationService, times(1))
        .generatePartialOrchestrationGraphFromSetupNodeIdAndExecutionId(any(), any(), any(), any());
    pmsExecutionService.getOrchestrationGraph(ACCOUNT_ID, "", "planExecutionId", "stageNodeExecutionId");
    verify(graphGenerationService, times(1)).generateOrchestrationGraphV2(eq(ACCOUNT_ID), any());
  }

  @Test
  @Owner(developers = EBTASAM)
  @Category(UnitTests.class)
  public void testGetOrchestrationGraphForAllStages() {
    pmsExecutionService.getOrchestrationGraphForAllStages(ACCOUNT_ID, "planExecutionId");
    verify(graphGenerationService, times(1)).generateOrchestrationGraphV2(any(), any());
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testSendGraphUpdateEvent() {
    pmsExecutionService.sendGraphUpdateEvent(PipelineExecutionSummaryEntity.builder().build());
    verify(graphGenerationService, times(1)).sendUpdateEventIfAny(any());
  }

  @Test
  @Owner(developers = VINICIUS)
  @Category(UnitTests.class)
  public void testGetInputSetYamlWithResolvedExpressionsUsingResolvedFieldFromSummary() throws IOException {
    ClassLoader classLoaderWithTriggerExpression = this.getClass().getClassLoader();
    String inputSetWithTriggerExpressionFilename = "inputsetWithTriggerExpression.yaml";
    String inputSetYamlWithTriggerExpression = Resources.toString(
        Objects.requireNonNull(classLoaderWithTriggerExpression.getResource(inputSetWithTriggerExpressionFilename)),
        StandardCharsets.UTF_8);

    String inputSetWithResolvedTriggerExpressionFilename = "inputsetWithResolvedTriggerExpressions.yaml";

    PipelineExecutionSummaryEntity executionSummaryEntity1 =
        PipelineExecutionSummaryEntity.builder()
            .accountId(ACCOUNT_ID)
            .orgIdentifier(ORG_IDENTIFIER)
            .projectIdentifier(PROJ_IDENTIFIER)
            .pipelineIdentifier(PIPELINE_IDENTIFIER)
            .planExecutionId(PLAN_EXECUTION_ID)
            .name(PLAN_EXECUTION_ID)
            .runSequence(0)
            .resolvedUserInputSetYaml(inputSetWithResolvedTriggerExpressionFilename)
            .pipelineTemplate(template)
            .build();
    doReturn(Optional.of(executionSummaryEntity1))
        .when(pmsExecutionSummaryRepository)
        .findByPlanExecutionIdAndPipelineDeletedNot(PLAN_EXECUTION_ID, !PIPELINE_DELETED);
    doReturn(null).when(pmsGitSyncHelper).getEntityGitDetailsFromBytes(any());
    doReturn(template).when(validateAndMergeHelper).getPipelineTemplate((ScopeInfo) any(), any(), any(), any(), any());
    String inputSet = pmsExecutionService
                          .getInputSetYamlWithTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PLAN_EXECUTION_ID,
                              PIPELINE_DELETED, false, ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS)
                          .getInputSetYaml();

    assertThat(inputSet).isEqualTo(inputSetWithResolvedTriggerExpressionFilename);
    verify(yamlExpressionResolveHelper, times(0))
        .resolveExpressionsInYaml(inputSetYamlWithTriggerExpression, PLAN_EXECUTION_ID,
            ResolveInputYamlType.RESOLVE_ALL_EXPRESSIONS, HarnessYamlVersion.V0);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetInputSetYamlWithInvalidExecutionId() {
    doReturn(Optional.empty())
        .when(pmsExecutionSummaryRepository)
        .findByPlanExecutionIdAndPipelineDeletedNot(INVALID_PLAN_EXECUTION_ID, !PIPELINE_DELETED);
    doReturn(null).when(pmsGitSyncHelper).getEntityGitDetailsFromBytes(any());
    doReturn(null).when(executionRetentionService).readExpiredRecordFromObjectStore(any(), any(), any(), any());
    doReturn(template).when(validateAndMergeHelper).getPipelineTemplate((ScopeInfo) any(), any(), any(), any(), any());

    assertThatThrownBy(
        ()
            -> pmsExecutionService.getInputSetYamlWithTemplate(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                INVALID_PLAN_EXECUTION_ID, PIPELINE_DELETED, false, ResolveInputYamlType.UNKNOWN))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Invalid request : Input Set did not exist or pipeline execution has been deleted");
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testGetInputSetYamlWithResolvedYamlForV1Pipeline() {
    // Setup for V1 pipeline
    PipelineExecutionSummaryEntity v1ExecutionSummaryEntity = PipelineExecutionSummaryEntity.builder()
                                                                  .accountId(ACCOUNT_ID)
                                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                                  .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                                                  .planExecutionId(PLAN_EXECUTION_ID)
                                                                  .name(PLAN_EXECUTION_ID)
                                                                  .pipelineTemplate(template)
                                                                  .pipelineVersion(HarnessYamlVersion.V1)
                                                                  .runSequence(0)
                                                                  .build();

    doReturn(Optional.of(v1ExecutionSummaryEntity))
        .when(pmsExecutionSummaryRepository)
        .findByPlanExecutionIdAndPipelineDeletedNot(PLAN_EXECUTION_ID, !PIPELINE_DELETED);
    doReturn(null).when(pmsGitSyncHelper).getEntityGitDetailsFromBytes(any());
    doReturn(template).when(validateAndMergeHelper).getPipelineTemplate((ScopeInfo) any(), any(), any(), any(), any());

    // Mock getExecutionData for V1 pipeline
    PlanExecutionMetadata v1PlanExecutionMetadata =
        PlanExecutionMetadata.builder().yaml(executionYaml).inputSetYaml(inputSetYaml).build();
    doReturn(Optional.of(v1PlanExecutionMetadata))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(ACCOUNT_ID, PLAN_EXECUTION_ID);

    InputSetYamlWithTemplateDTO result = pmsExecutionService.getInputSetYamlWithTemplate(ACCOUNT_ID, ORG_IDENTIFIER,
        PROJ_IDENTIFIER, PLAN_EXECUTION_ID, PIPELINE_DELETED, false, ResolveInputYamlType.UNKNOWN);

    assertThat(result.getResolvedYaml()).isNotNull();
    assertThat(result.getResolvedYaml()).isEqualTo(executionYaml);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionSummaryEntity() {
    doReturn(Optional.of(executionSummaryEntity))
        .when(pmsExecutionSummaryRepository)
        .findByPlanExecutionIdAndPipelineDeletedNot(PLAN_EXECUTION_ID, !PIPELINE_DELETED);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(ACCOUNT_ID, PLAN_EXECUTION_ID, PIPELINE_DELETED);

    assertThat(pipelineExecutionSummaryEntity).isEqualTo(executionSummaryEntity);
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionSummaryEntityWithInvalidExecutionId() {
    doReturn(Optional.empty())
        .when(pmsExecutionSummaryRepository)
        .findByPlanExecutionIdAndPipelineDeletedNot(INVALID_PLAN_EXECUTION_ID, !PIPELINE_DELETED);

    assertThatThrownBy(()
                           -> pmsExecutionService.getPipelineExecutionSummaryEntity(
                               ACCOUNT_ID, INVALID_PLAN_EXECUTION_ID, PIPELINE_DELETED))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Plan Execution Summary does not exist or has been deleted for planExecutionId: "
            + INVALID_PLAN_EXECUTION_ID);
  }

  @Test
  @Owner(developers = MLUKIC)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionSummary() {
    doReturn(Optional.of(executionSummaryEntity))
        .when(pmsExecutionSummaryRepository)
        .findByPlanExecutionId(PLAN_EXECUTION_ID);

    PipelineExecutionSummaryEntity pipelineExecutionSummaryEntity =
        pmsExecutionService.getPipelineExecutionSummaryEntity(ACCOUNT_ID, PLAN_EXECUTION_ID);

    assertThat(pipelineExecutionSummaryEntity).isEqualTo(executionSummaryEntity);
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testGetPipelineExecutionSummaryWithInvalidExecutionId() {
    doReturn(Optional.empty()).when(pmsExecutionSummaryRepository).findByPlanExecutionId(PLAN_EXECUTION_ID);

    assertThatThrownBy(
        () -> pmsExecutionService.getPipelineExecutionSummaryEntity(ACCOUNT_ID, INVALID_PLAN_EXECUTION_ID))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Plan Execution Summary does not exist or has been deleted for planExecutionId: "
            + INVALID_PLAN_EXECUTION_ID);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testGetExecutionMetadata() {
    String planExecutionID = "tempID";

    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().yaml(executionYaml).planExecutionId(planExecutionID).build();

    doReturn(Optional.of(planExecutionMetadata))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(ACCOUNT_ID, planExecutionID);

    ExecutionDataResponseDTO executionData = pmsExecutionService.getExecutionData(ACCOUNT_ID, planExecutionID);

    assertThat(executionData.getExecutionYaml()).isEqualTo(planExecutionMetadata.getYaml());
    verify(planExecutionMetadataService, times(1)).findByPlanExecutionId(ACCOUNT_ID, planExecutionID);
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testGetExecutionMetadataFailure() {
    String planExecutionID = "tempID";

    assertThatThrownBy(() -> pmsExecutionService.getExecutionData(ACCOUNT_ID, planExecutionID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format("Execution with id [%s] is not present or deleted", planExecutionID));
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetExecutionMetadataDetails() {
    String planExecutionID = "tempID";

    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder()
            .yaml(executionYaml)
            .planExecutionId(planExecutionID)
            .triggerPayload(TriggerPayload.newBuilder()
                                .setType(Type.MANIFEST)
                                .setManifestData(ManifestData.newBuilder().setVersion("1.0").build())
                                .build())
            .build();

    doReturn(Optional.of(planExecutionMetadata))
        .when(planExecutionMetadataService)
        .findByPlanExecutionId(ACCOUNT_ID, planExecutionID);
    ExecutionMetaDataResponseDetailsDTO executionData =
        pmsExecutionService.getExecutionDataDetails(planExecutionID, ACCOUNT_ID);
    assertThat(executionData.getExecutionYaml()).isEqualTo(planExecutionMetadata.getYaml());
    assertThat(executionData.getTriggerPayload().getType()).isEqualTo(Type.MANIFEST);
    assertThat(executionData.getTriggerPayload().getManifestData().getVersion()).isEqualTo("1.0");
    verify(planExecutionMetadataService, times(1)).findByPlanExecutionId(ACCOUNT_ID, planExecutionID);
  }

  @Test
  @Owner(developers = SRIDHAR)
  @Category(UnitTests.class)
  public void testGetExecutionMetadataDetailsFailure() {
    String planExecutionID = "tempID";

    assertThatThrownBy(() -> pmsExecutionService.getExecutionDataDetails(planExecutionID, ACCOUNT_ID))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining(String.format("Execution with id [%s] is not present or deleted", planExecutionID));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testGetInputSetYamlForRerun() {
    doReturn(PlanExecutionMetadata.builder().inputSetYaml("inputSetYaml").build())
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(
            ACCOUNT_ID, PLAN_EXECUTION_ID, Set.of(PlanExecutionMetadataKeys.inputSetYaml));
    assertEquals("inputSetYaml", pmsExecutionService.getInputSetYamlForRerun(ACCOUNT_ID, PLAN_EXECUTION_ID, false));
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testMergeInputSetIntoPipelineForRerun() {
    doReturn(PipelineEntity.builder().yaml("pipelineYaml").build())
        .when(validateAndMergeHelper)
        .getPipelineEntity(SCOPE_INFO, PIPELINE_IDENTIFIER, null, null, false, false);
    doReturn(PlanExecutionMetadata.builder().inputSetYaml("inputSetYaml").build())
        .when(planExecutionMetadataService)
        .getWithFieldsIncludedFromSecondary(
            ACCOUNT_ID, PLAN_EXECUTION_ID, Set.of(PlanExecutionMetadataKeys.inputSetYaml));
    MockedStatic<InputSetMergeHelper> aStatic = Mockito.mockStatic(InputSetMergeHelper.class);
    aStatic.when(() -> InputSetMergeHelper.mergeInputSetIntoPipeline("pipelineTemplate", "inputSetYaml", false))
        .thenReturn("finalMergedYaml");
    MockedStatic<InputSetTemplateHelper> bStatic = Mockito.mockStatic(InputSetTemplateHelper.class);
    bStatic.when(() -> InputSetTemplateHelper.createTemplateFromPipeline("pipelineYaml"))
        .thenReturn("pipelineTemplate");
    Assertions.assertEquals("finalMergedYaml",
        pmsExecutionService.mergeRuntimeInputIntoPipelineForRerun(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
            PIPELINE_IDENTIFIER, PLAN_EXECUTION_ID, null, null, Collections.emptyList(), SCOPE_INFO));
    bStatic.when(() -> InputSetTemplateHelper.createTemplateFromPipeline("pipelineYaml")).thenReturn("");
    assertThat(pmsExecutionService.mergeRuntimeInputIntoPipelineForRerun(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                   PIPELINE_IDENTIFIER, PLAN_EXECUTION_ID, null, null, Collections.emptyList(), SCOPE_INFO))
        .isEqualTo("");
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testRegisterInterrupt() {
    MockedStatic<SecurityContextBuilder> mockedStatic = Mockito.mockStatic(SecurityContextBuilder.class);
    mockedStatic.when(() -> SecurityContextBuilder.getPrincipal())
        .thenReturn(new UserPrincipal("name1", "user1@harness.io", "user1", "accountId"));
    Interrupt interrupt = Interrupt.builder().uuid("uuid").type(ABORT_ALL).planExecutionId("planExecutionId").build();
    when(orchestrationService.registerInterrupt(any())).thenReturn(interrupt);
    doNothing().when(pipelineTelemetryHelper).sendInterruptTelemetryEvent(any());
    when(nodeExecutionService.get("nodeExecutionId")).thenReturn(NodeExecution.builder().build());
    InterruptDTO interruptDTO =
        pmsExecutionService.registerInterrupt(PlanExecutionInterruptType.ABORTALL, "planExecutionId", null);
    assertEquals(interruptDTO.getPlanExecutionId(), "planExecutionId");
    assertEquals(interruptDTO.getId(), "uuid");
    assertEquals(interruptDTO.getType(), PlanExecutionInterruptType.ABORTALL);
    ArgumentCaptor<InterruptPackage> interruptPackageArgumentCaptor = ArgumentCaptor.forClass(InterruptPackage.class);
    verify(orchestrationService, times(1)).registerInterrupt(interruptPackageArgumentCaptor.capture());
    assertEquals(
        interruptPackageArgumentCaptor.getValue().getInterruptConfig().getIssuedBy().getManualIssuer().getEmailId(),
        "user1@harness.io");
    assertEquals(
        interruptPackageArgumentCaptor.getValue().getInterruptConfig().getIssuedBy().getManualIssuer().getUserId(),
        "user1");
    assertEquals(
        interruptPackageArgumentCaptor.getValue().getInterruptConfig().getIssuedBy().getManualIssuer().getIdentifier(),
        "name1");
    assertEquals(
        interruptPackageArgumentCaptor.getValue().getInterruptConfig().getIssuedBy().getManualIssuer().getType(),
        "USER");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testRegisterPipelineRollbackInterrupt() {
    MockedStatic<SecurityContextBuilder> mockedStatic = Mockito.mockStatic(SecurityContextBuilder.class);
    mockedStatic.when(() -> SecurityContextBuilder.getPrincipal())
        .thenReturn(new UserPrincipal("name1", "user1@harness.io", "user1", "accountId"));

    // Create an interrupt with expected metadata for pipeline rollback
    Map<String, String> expectedMetadata = new HashMap<>();
    expectedMetadata.put("ROLLBACK", "PipelineRollback");
    Interrupt expectedInterrupt = Interrupt.builder()
                                      .uuid("uuid")
                                      .type(InterruptType.MARK_FAILED)
                                      .planExecutionId("planExecutionId")
                                      .metadata(expectedMetadata)
                                      .build();

    when(orchestrationService.registerInterrupt(any())).thenReturn(expectedInterrupt);
    doNothing().when(pipelineTelemetryHelper).sendInterruptTelemetryEvent(any());
    when(nodeExecutionService.get("nodeExecutionId")).thenReturn(NodeExecution.builder().build());
    // Call the service method with PIPELINEROLLBACK
    InterruptDTO interruptDTO = pmsExecutionService.registerInterrupt(
        PlanExecutionInterruptType.PIPELINEROLLBACK, "planExecutionId", "nodeExecutionId");

    // Verify the interrupt was created with correct metadata and type
    assertEquals("planExecutionId", interruptDTO.getPlanExecutionId());
    assertEquals("uuid", interruptDTO.getId());
    assertEquals(PlanExecutionInterruptType.PIPELINEROLLBACK, interruptDTO.getType());
    assertThat(expectedInterrupt.getMetadata()).containsEntry("ROLLBACK", "PipelineRollback");
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetSimplifiedOrchestrationGraph() {
    when(graphGenerationService.generateSimplifiedOrchestrationGraphV2(eq(ACCOUNT_ID), any()))
        .thenReturn(SimplifiedOrchestrationGraphDTO.builder().build());
    SimplifiedOrchestrationGraphDTO simplifiedOrchestrationGraphDTO =
        pmsExecutionService.getSimplifiedOrchestrationGraph(ACCOUNT_ID, PLAN_EXECUTION_ID);
    assertNotNull(simplifiedOrchestrationGraphDTO);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetPipelineIdentifier() {
    PipelineExecutionSummaryEntity expectedPipelineExecutionSummaryEntity =
        PipelineExecutionSummaryEntity.builder().pipelineIdentifier(PIPELINE_IDENTIFIER).build();
    when(pmsExecutionSummaryService.getPipelineExecutionSummaryWithProjections(any(), any(), any()))
        .thenReturn(expectedPipelineExecutionSummaryEntity);

    String pipelineIdentifier = pmsExecutionService.getPipelineIdentifier(ACCOUNT_ID, PLAN_EXECUTION_ID);
    assertEquals(expectedPipelineExecutionSummaryEntity.getPipelineIdentifier(), pipelineIdentifier);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testFormCriteria_timeRange() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);

    doReturn(Arrays.asList(PIPELINE_IDENTIFIER))
        .when(pmsPipelineService)
        .getPermittedPipelineIdentifier(any(), any(), any(), any());
    PipelineExecutionFilterPropertiesDTO filterPropertiesDTO =
        PipelineExecutionFilterPropertiesDTO.builder()
            .timeRange(TimeRange.builder().startTime(1712116800000L).endTime(1714653568130L).build())
            .build();
    Criteria form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER,
        null, filterPropertiesDTO, null, null, null, false, !PIPELINE_DELETED, true, null);

    assertThat(form.getCriteriaObject().get("$and").toString())
        .isEqualTo("[Document{{startTs=Document{{$gte=1712116800000, $lte=1714653568130}}, "
            + "executionMode=Document{{$ne=PIPELINE_ROLLBACK}}}}]");

    doReturn(true).when(pmsFeatureFlagService).isEnabled(any(), any(FeatureName.class));
    filterPropertiesDTO = null;
    form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null,
        filterPropertiesDTO, null, null, null, false, !PIPELINE_DELETED, true, null);
    assertThat(form.getCriteriaObject().get("$and").toString()).contains("startTs");
    assertThat(form.getCriteriaObject().get("$and").toString()).contains("$gte");
    assertThat(form.getCriteriaObject().get("$and").toString()).contains("$lte");
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testFormCriteriaForPipelineExecutionOutline() {
    PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO =
        PipelineExecutionOutlineFilterDTO.builder()
            .pipelineIdentifier(PIPELINE_IDENTIFIER)
            .planExecutionIds(List.of(PLAN_EXECUTION_ID))
            .status(List.of(ExecutionStatus.ABORTED))
            .timeRange(TimeRange.builder().startTime(1700000000000L).endTime(1900000000000L).build())
            .build();

    doReturn(List.of(PIPELINE_IDENTIFIER))
        .when(pmsPipelineService)
        .getPermittedPipelineIdentifier(any(), any(), any(), any());
    Criteria criteria = new Criteria();
    Criteria form = pmsExecutionService.formFilterCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
        pipelineExecutionOutlineFilterDTO, 1800000000000L, "someExecutionId", criteria);
    BasicDBList lastSeenCriteriaList = (BasicDBList) form.getCriteriaObject().get("$or");

    // test conditions for lastSeenStartTime, lastSeenExecutionId => startTs range should be updated as well
    assertThat(lastSeenCriteriaList.size()).isEqualTo(2);
    assertEquals(String.valueOf(1800000000000L), ((Document) lastSeenCriteriaList.get(0)).get("startTs").toString());
    assertEquals(
        "Document{{$lt=someExecutionId}}", ((Document) lastSeenCriteriaList.get(0)).get("planExecutionId").toString());
    Document timeRangeCriteria = (Document) lastSeenCriteriaList.get(1);
    assertEquals("Document{{$gte=1700000000000, $lt=1800000000000}}", timeRangeCriteria.get("startTs").toString());

    assertEquals(PIPELINE_IDENTIFIER, form.getCriteriaObject().get("pipelineIdentifier").toString());
    assertEquals(
        "Document{{$in=[" + PLAN_EXECUTION_ID + "]}}", form.getCriteriaObject().get("planExecutionId").toString());
    assertEquals("Document{{$in=[ABORTED]}}", form.getCriteriaObject().get("status").toString());

    // timeRange default to 1 month
    doReturn(true).when(pmsFeatureFlagService).isEnabled(any(), any(FeatureName.class));
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testBuildTimeRangeCriteria() {
    TimeRange timeRange = null;

    Criteria criteria = pmsExecutionService.buildTimeRangeCriteria(timeRange, false);
    assertThat(criteria).isNull();

    timeRange = TimeRange.builder().build();
    criteria = pmsExecutionService.buildTimeRangeCriteria(timeRange, false);
    assertThat(criteria).isNull();

    timeRange = TimeRange.builder().startTime(1700000000000L).build();
    criteria = pmsExecutionService.buildTimeRangeCriteria(timeRange, false);
    assertEquals("Document{{$gte=1700000000000}}", criteria.getCriteriaObject().get("startTs").toString());

    timeRange = TimeRange.builder().startTime(1700000000000L).endTime(1900000000000L).build();
    criteria = pmsExecutionService.buildTimeRangeCriteria(timeRange, false);
    assertEquals(
        "Document{{$gte=1700000000000, $lte=1900000000000}}", criteria.getCriteriaObject().get("startTs").toString());

    criteria = pmsExecutionService.buildTimeRangeCriteria(timeRange, true);
    assertEquals(
        "Document{{$gte=1700000000000, $lt=1900000000000}}", criteria.getCriteriaObject().get("startTs").toString());

    timeRange = TimeRange.builder().endTime(1900000000000L).build();
    criteria = pmsExecutionService.buildTimeRangeCriteria(timeRange, true);
    assertEquals("Document{{$lt=1900000000000}}", criteria.getCriteriaObject().get("startTs").toString());
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetListOfExecutionsOutlineWithNoAccess() {
    PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO =
        PipelineExecutionOutlineFilterDTO.builder()
            .pipelineIdentifier(PIPELINE_IDENTIFIER)
            .planExecutionIds(List.of(PLAN_EXECUTION_ID))
            .status(List.of(ExecutionStatus.ABORTED))
            .timeRange(TimeRange.builder().startTime(1700000000000L).endTime(1900000000000L).build())
            .build();
    doReturn(new ArrayList<>())
        .when(pmsPipelineService)
        .getPermittedToViewPipelineIdentifiers(any(), any(), any(), any());
    assertThatThrownBy(()
                           -> pmsExecutionService.getListOfExecutionsOutline(ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, pipelineExecutionOutlineFilterDTO, null, null, 5))
        .isInstanceOf(AccessDeniedException.class);

    PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO1 =
        PipelineExecutionOutlineFilterDTO.builder()
            .planExecutionIds(List.of(PLAN_EXECUTION_ID))
            .status(List.of(ExecutionStatus.ABORTED))
            .timeRange(TimeRange.builder().startTime(1700000000000L).endTime(1900000000000L).build())
            .build();
    doReturn(Collections.singletonList(PipelineExecutionSummaryEntity.builder()
                                           .planExecutionId(PLAN_EXECUTION_ID)
                                           .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                           .build()))
        .when(pmsExecutionSummaryRepository)
        .findAllWithProjectionWithoutPagination(any(), any());
    doReturn(new ArrayList<>())
        .when(pmsPipelineService)
        .getPermittedToViewPipelineIdentifiers(any(), any(), any(), any());
    doReturn(Collections.emptyMap())
        .when(executionRetentionService)
        .readExpiredRecordsFromObjectStore(any(), any(), any(), any());
    assertThatThrownBy(()
                           -> pmsExecutionService.getListOfExecutionsOutline(ACCOUNT_ID, ORG_IDENTIFIER,
                               PROJ_IDENTIFIER, pipelineExecutionOutlineFilterDTO1, null, null, 5))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetListOfExecutionsOutline() {
    PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO =
        PipelineExecutionOutlineFilterDTO.builder()
            .status(List.of(ExecutionStatus.ABORTED))
            .timeRange(TimeRange.builder().startTime(1700000000000L).endTime(1900000000000L).build())
            .build();
    doReturn(new ArrayList<>())
        .when(pmsExecutionSummaryRepository)
        .findAllWithProjectionWithoutPagination(any(), any(), any(), any());
    pmsExecutionService.getListOfExecutionsOutline(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, pipelineExecutionOutlineFilterDTO, null, null, 5);
    verify(pmsPipelineServiceHelper, times(1)).setCriteriaForPermittedPipelines(any(), any(), any(), any(), any());

    PipelineExecutionOutlineFilterDTO pipelineExecutionOutlineFilterDTO1 =
        PipelineExecutionOutlineFilterDTO.builder()
            .planExecutionIds(List.of(PLAN_EXECUTION_ID))
            .status(List.of(ExecutionStatus.ABORTED))
            .timeRange(TimeRange.builder().startTime(1700000000000L).endTime(1900000000000L).build())
            .build();
    doReturn(Collections.singletonList(PipelineExecutionSummaryEntity.builder()
                                           .planExecutionId(PLAN_EXECUTION_ID)
                                           .pipelineIdentifier(PIPELINE_IDENTIFIER)
                                           .build()))
        .when(pmsExecutionSummaryRepository)
        .findAllWithProjectionWithoutPagination(any(), any());
    doReturn(List.of(PIPELINE_IDENTIFIER))
        .when(pmsPipelineService)
        .getPermittedToViewPipelineIdentifiers(any(), any(), any(), any());
    doReturn(List.of(PipelineExecutionSummaryEntity.builder().build()))
        .when(pmsExecutionSummaryRepository)
        .findAllWithProjectionWithoutPagination(any(), any(), any(), any());
    CustomPage<PipelineExecutionOutlineDTO> executions = pmsExecutionService.getListOfExecutionsOutline(
        ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, pipelineExecutionOutlineFilterDTO1, null, null, 5);
    assertEquals(executions.getContent().size(), 1);
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testFormCriteria_withoutExecutionNotes() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(false);
    doNothing().when(pmsPipelineServiceHelper).setPermittedPipelines(any(), any(), any(), any(), any());
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION))
        .thenReturn(false);

    PipelineExecutionFilterPropertiesDTO filterProps = PipelineExecutionFilterPropertiesDTO.builder().build();

    Criteria criteria = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        filterProps, null, null, null, false, !PIPELINE_DELETED, true, null);

    String criteriaString = criteria.getCriteriaObject().toString();
    assertThat(criteriaString)
        .contains("accountId=" + ACCOUNT_ID)
        .contains("orgIdentifier=" + ORG_IDENTIFIER)
        .contains("projectIdentifier=" + PROJ_IDENTIFIER)
        .doesNotContain("notes=");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testFormCriteria_withBlankExecutionNotesIsSkipped() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(false);
    doNothing().when(pmsPipelineServiceHelper).setPermittedPipelines(any(), any(), any(), any(), any());
    when(pmsFeatureFlagService.isEnabled(ACCOUNT_ID, FeatureName.PIPE_ENABLE_QUEUE_BASED_PLAN_CREATION))
        .thenReturn(false);

    PipelineExecutionFilterPropertiesDTO filterProps =
        PipelineExecutionFilterPropertiesDTO.builder().executionNotes(Arrays.asList("")).build();

    Criteria criteria = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        filterProps, null, null, null, false, !PIPELINE_DELETED, true, null);

    String criteriaString = criteria.getCriteriaObject().toString();
    assertThat(criteriaString)
        .contains("accountId=" + ACCOUNT_ID)
        .contains("orgIdentifier=" + ORG_IDENTIFIER)
        .contains("projectIdentifier=" + PROJ_IDENTIFIER)
        .doesNotContain("notes=");
  }

  @Test
  @Owner(developers = EDGAR_GARCIA)
  @Category(UnitTests.class)
  public void testFormCriteria_withExecutionNotes_singleAndMultiple() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(false);
    doNothing().when(pmsPipelineServiceHelper).setPermittedPipelines(any(), any(), any(), any(), any());

    PipelineExecutionFilterPropertiesDTO withSingleNote =
        PipelineExecutionFilterPropertiesDTO.builder().executionNotes(Arrays.asList("NoteOne")).build();

    Criteria c1 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        withSingleNote, null, null, null, false, !PIPELINE_DELETED, true, null);

    String criteriaString1 = c1.getCriteriaObject().toString();
    assertThat(criteriaString1)
        .contains("accountId=" + ACCOUNT_ID)
        .contains("orgIdentifier=" + ORG_IDENTIFIER)
        .contains("projectIdentifier=" + PROJ_IDENTIFIER)
        .contains("notes=")
        .contains("NoteOne");

    PipelineExecutionFilterPropertiesDTO withMultipleNotes =
        PipelineExecutionFilterPropertiesDTO.builder().executionNotes(Arrays.asList("First", " ", "Second")).build();

    Criteria c2 = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        withMultipleNotes, null, null, null, false, !PIPELINE_DELETED, true, null);

    String criteriaString2 = c2.getCriteriaObject().toString();
    assertThat(criteriaString2)
        .contains("accountId=" + ACCOUNT_ID)
        .contains("orgIdentifier=" + ORG_IDENTIFIER)
        .contains("projectIdentifier=" + PROJ_IDENTIFIER)
        .contains("notes=")
        .contains("First")
        .contains("Second");
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testGetListOfEvaluatedPolicy_UpdatesOrgIdForProjectLevelPolicies() {
    String oldOrgId = "oldOrg";
    String newOrgId = "newOrg";
    String projectId = "testProject";
    String planExecutionId = "execId123";
    List<Integer> evaluationIds = List.of(1, 2);

    // Setup: PlanExecutionMetadata with evaluationIds
    PlanExecutionMetadata metadata = PlanExecutionMetadata.builder().evaluatedPolicyIds(evaluationIds).build();
    when(planExecutionMetadataService.findByPlanExecutionIdWithFieldsIncluded(
             ACCOUNT_ID, planExecutionId, Set.of(PlanExecutionMetadataKeys.evaluatedPolicyIds)))
        .thenReturn(metadata);

    // Setup: OPA returns policy with OLD orgId
    PolicyData projectPolicyData = PolicyData.builder()
                                       .identifier("policy1")
                                       .name("Test Policy")
                                       .account_id(ACCOUNT_ID)
                                       .org_id(oldOrgId)
                                       .project_id(projectId)
                                       .created(123L)
                                       .build();

    OpaPolicyEvaluationResponse policyResponse = OpaPolicyEvaluationResponse.builder()
                                                     .status("pass")
                                                     .policy(projectPolicyData)
                                                     .deny_messages(Collections.emptyList())
                                                     .build();

    OpaPolicySetEvaluationResponse policySetResponse = OpaPolicySetEvaluationResponse.builder()
                                                           .identifier("policyset1")
                                                           .name("Test PolicySet")
                                                           .status("pass")
                                                           .account_id(ACCOUNT_ID)
                                                           .org_id(oldOrgId)
                                                           .project_id(projectId)
                                                           .details(Collections.singletonList(policyResponse))
                                                           .created(456L)
                                                           .build();

    OpaEvaluationResponseHolder opaResponse = OpaEvaluationResponseHolder.builder()
                                                  .id("eval1")
                                                  .status("pass")
                                                  .account_id(ACCOUNT_ID)
                                                  .org_id(oldOrgId)
                                                  .project_id(projectId)
                                                  .details(Collections.singletonList(policySetResponse))
                                                  .created(789L)
                                                  .build();

    EvaluationDetailsResponse opaResponseWrapper = EvaluationDetailsResponse.builder()
                                                       .evaluations(Collections.singletonList(opaResponse))
                                                       .pageIndex(0)
                                                       .pageSize(10)
                                                       .totalPages(1)
                                                       .totalItems(1)
                                                       .build();

    when(opaServiceClientHelper.listOpaPolicyEvaluationsWithRetry(ACCOUNT_ID, 10, 0, evaluationIds))
        .thenReturn(opaResponseWrapper);

    // Act
    org.springframework.data.domain.Page<GovernanceMetadata> result =
        pmsExecutionService.getListOfEvaluatedPolicy(ACCOUNT_ID, newOrgId, projectId, planExecutionId, 10, 0);

    // Assert: OrgId should be updated to NEW org for project-level policies
    assertThat(result.getContent()).hasSize(1);
    GovernanceMetadata governanceMetadata = result.getContent().get(0);
    assertThat(governanceMetadata.getDetailsList()).hasSize(1);

    PolicySetMetadata policySet = governanceMetadata.getDetails(0);
    assertThat(policySet.getOrgId()).isEqualTo(newOrgId); // Updated!
    assertThat(policySet.getProjectId()).isEqualTo(projectId);

    PolicyMetadata policy = policySet.getPolicyMetadata(0);
    assertThat(policy.getOrgId()).isEqualTo(newOrgId); // Updated!
    assertThat(policy.getProjectId()).isEqualTo(projectId);
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testFormQueryForSearch_PipelineTagsV2() throws IOException {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);
    doReturn(true).when(pmsPipelineService).validateViewPermission(any(), any(), any());

    // OR operator: nested queries combined with bool.should
    Query query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build(),
                                    NGTag.builder().key("team").value("platform").build()))
                                .operator(FilterWithOperator.FilterOperator.OR)
                                .build())
            .build(),
        null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}}"
            + ",\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"should\":\\[\\{\"nested\":"
            + "\\{\"path\":\"tags\",\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":"
            + "\"env\"}}},\\{\"term\":\\{\"tags.value\":\\{\"value\":\"prod\"}}}]}}}},\\{\"nested\":\\{\"path\":"
            + "\"tags\",\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":\"team\"}}},"
            + "\\{\"term\":\\{\"tags.value\":\\{\"value\":\"platform\"}}}]}}}}]}}]}}}}");

    // AND operator with 2 key-value tags: 2 nested must queries combined with bool.must
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build(),
                                    NGTag.builder().key("team").value("platform").build()))
                                .operator(FilterWithOperator.FilterOperator.AND)
                                .build())
            .build(),
        null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}}"
            + ",\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"must\":\\[\\{\"nested\":"
            + "\\{\"path\":\"tags\",\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":"
            + "\"env\"}}},\\{\"term\":\\{\"tags.value\":\\{\"value\":\"prod\"}}}]}}}},\\{\"nested\":\\{\"path\":"
            + "\"tags\",\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":\"team\"}}},"
            + "\\{\"term\":\\{\"tags.value\":\\{\"value\":\"platform\"}}}]}}}}]}}]}}}}");

    // AND operator with 3 key-value tags: 3 nested must queries combined with bool.must
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build(),
                                    NGTag.builder().key("team").value("platform").build(),
                                    NGTag.builder().key("region").value("us-west").build()))
                                .operator(FilterWithOperator.FilterOperator.AND)
                                .build())
            .build(),
        null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}}"
            + ",\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"must\":\\[\\{\"nested\":"
            + "\\{\"path\":\"tags\",\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":"
            + "\"env\"}}},\\{\"term\":\\{\"tags.value\":\\{\"value\":\"prod\"}}}]}}}},\\{\"nested\":\\{\"path\":"
            + "\"tags\",\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":\"team\"}}},"
            + "\\{\"term\":\\{\"tags.value\":\\{\"value\":\"platform\"}}}]}}}},\\{\"nested\":\\{\"path\":\"tags\","
            + "\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":\"region\"}}},\\{"
            + "\"term\":\\{\"tags.value\":\\{\"value\":\"us-west\"}}}]}}}}]}}]}}}}");

    // Key-only tags: match either tags.key OR tags.value with the key
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(
                FilterWithOperator.<NGTag>builder()
                    .itemsList(Arrays.asList(NGTag.builder().key("env").build(), NGTag.builder().key("team").build()))
                    .operator(FilterWithOperator.FilterOperator.AND)
                    .build())
            .build(),
        null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}}"
            + ",\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"must\":\\[\\{\"nested\":"
            + "\\{\"path\":\"tags\",\"query\":\\{\"bool\":\\{\"should\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":"
            + "\"env\"}}},\\{\"term\":\\{\"tags.value\":\\{\"value\":\"env\"}}}]}}}},\\{\"nested\":\\{\"path\":"
            + "\"tags\",\"query\":\\{\"bool\":\\{\"should\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":\"team\"}}},"
            + "\\{\"term\":\\{\"tags.value\":\\{\"value\":\"team\"}}}]}}}}]}}]}}}}");

    // Mixed key-value and key-only: key-value uses must[key+value], key-only uses should[key||value]
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build(),
                                    NGTag.builder().key("team").build()))
                                .operator(FilterWithOperator.FilterOperator.AND)
                                .build())
            .build(),
        null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}}"
            + ",\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"must\":\\[\\{\"nested\":"
            + "\\{\"path\":\"tags\",\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":"
            + "\"env\"}}},\\{\"term\":\\{\"tags.value\":\\{\"value\":\"prod\"}}}]}}}},\\{\"nested\":\\{\"path\":"
            + "\"tags\",\"query\":\\{\"bool\":\\{\"should\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":\"team\"}}},"
            + "\\{\"term\":\\{\"tags.value\":\\{\"value\":\"team\"}}}]}}}}]}}]}}}}");

    // Multiple tags (4): 4 separate nested queries combined with AND
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build(),
                                    NGTag.builder().key("team").value("platform").build(),
                                    NGTag.builder().key("region").value("us-west").build(),
                                    NGTag.builder().key("critical").build()))
                                .operator(FilterWithOperator.FilterOperator.AND)
                                .build())
            .build(),
        null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}}"
            + ",\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"must\":\\[\\{\"nested\":"
            + "\\{\"path\":\"tags\",\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":"
            + "\"env\"}}},\\{\"term\":\\{\"tags.value\":\\{\"value\":\"prod\"}}}]}}}},\\{\"nested\":\\{\"path\":"
            + "\"tags\",\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":\"team\"}}},"
            + "\\{\"term\":\\{\"tags.value\":\\{\"value\":\"platform\"}}}]}}}},\\{\"nested\":\\{\"path\":\"tags\","
            + "\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":\"region\"}}},\\{"
            + "\"term\":\\{\"tags.value\":\\{\"value\":\"us-west\"}}}]}}}},\\{\"nested\":\\{\"path\":\"tags\","
            + "\"query\":\\{\"bool\":\\{\"should\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":\"critical\"}}},"
            + "\\{\"term\":\\{\"tags.value\":\\{\"value\":\"critical\"}}}]}}}}]}}]}}}}");

    // Both V1 and V2 tags set: V2 takes precedence, V1 tags (tagKey1, tagValue2, tagKey3) are ignored
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTags(Arrays.asList(NGTag.builder().key("tagKey1").build(),
                NGTag.builder().key("tagValue2").build(), NGTag.builder().key("tagKey3").value("tagValue3").build()))
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build(),
                                    NGTag.builder().key("team").value("platform").build()))
                                .operator(FilterWithOperator.FilterOperator.AND)
                                .build())
            .build(),
        null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}}"
            + ",\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"must\":\\[\\{\"nested\":"
            + "\\{\"path\":\"tags\",\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":"
            + "\"env\"}}},\\{\"term\":\\{\"tags.value\":\\{\"value\":\"prod\"}}}]}}}},\\{\"nested\":\\{\"path\":"
            + "\"tags\",\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":\"team\"}}},"
            + "\\{\"term\":\\{\"tags.value\":\\{\"value\":\"platform\"}}}]}}}}]}}]}}}}");

    // Single tag: still creates nested query structure
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build()))
                                .operator(FilterWithOperator.FilterOperator.AND)
                                .build())
            .build(),
        null, null, null, false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"executionMode\":\\[\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}}"
            + ",\\{\"range\":\\{\"startTs\":\\{\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"must\":\\[\\{\"nested\":"
            + "\\{\"path\":\"tags\",\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":"
            + "\"env\"}}},\\{\"term\":\\{\"tags.value\":\\{\"value\":\"prod\"}}}]}}}}]}}]}}}}");

    // Tags V2 combined with status filter
    query = pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build(),
                                    NGTag.builder().key("team").value("platform").build()))
                                .operator(FilterWithOperator.FilterOperator.AND)
                                .build())
            .build(),
        null, null, Arrays.asList(ExecutionStatus.SUCCESS, ExecutionStatus.FAILED), false, null);
    assertThat(query.toString())
        .matches("Query: "
            + "\\{\"constant_score\":\\{\"filter\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"accountId\":\\{"
            + "\"value\":\"account_id\"}}},\\{\"term\":\\{\"orgIdentifier\":\\{\"value\":\"orgId\"}}},\\{\"term\":"
            + "\\{\"projectIdentifier\":\\{\"value\":\"projId\"}}},\\{\"term\":\\{\"deleted\":\\{\"value\":false}}"
            + "},\\{\"terms\":\\{\"status\":\\[\"SUCCESS\",\"FAILED\"]}},\\{\"terms\":\\{\"executionMode\":\\["
            + "\"POST_EXECUTION_ROLLBACK\",\"NORMAL\",\"UNDEFINED_MODE\"]}},\\{\"range\":\\{\"startTs\":\\{"
            + "\"gte\":\\d+,\"lte\":\\d+}}},\\{\"bool\":\\{\"must\":\\[\\{\"nested\":\\{\"path\":\"tags\","
            + "\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":\"env\"}}},\\{"
            + "\"term\":\\{\"tags.value\":\\{\"value\":\"prod\"}}}]}}}},\\{\"nested\":\\{\"path\":\"tags\","
            + "\"query\":\\{\"bool\":\\{\"must\":\\[\\{\"term\":\\{\"tags.key\":\\{\"value\":\"team\"}}},\\{"
            + "\"term\":\\{\"tags.value\":\\{\"value\":\"platform\"}}}]}}}}]}}]}}}}");

    // Null key in tag throws InvalidRequestException
    PipelineExecutionFilterPropertiesDTO nullKeyProps =
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build(),
                                    NGTag.builder().key(null).value("platform").build()))
                                .operator(FilterWithOperator.FilterOperator.AND)
                                .build())
            .build();
    assertThatThrownBy(()
                           -> pmsExecutionService.formQueryForSearch(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, null,
                               null, nullKeyProps, null, null, null, false, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Key in Pipeline Tags filter cannot be null");
  }

  @Test
  @Owner(developers = SAKSHI)
  @Category(UnitTests.class)
  public void testFormCriteria_PipelineTagsV2() {
    when(gitSyncSdkService.isGitSyncEnabled(any(), any(), any())).thenReturn(true);
    doReturn(Arrays.asList(PIPELINE_IDENTIFIER))
        .when(pmsPipelineService)
        .getPermittedPipelineIdentifier(any(), any(), any(), any());

    // AND operator with key-value tags
    Criteria form =
        pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null,
            PipelineExecutionFilterPropertiesDTO.builder()
                .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                    .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build(),
                                        NGTag.builder().key("team").value("platform").build()))
                                    .operator(FilterWithOperator.FilterOperator.AND)
                                    .build())
                .build(),
            null, null, null, false, false, true, null);
    assertThat(form.getCriteriaObject().get("$and").toString())
        .matches("\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{\\$and=\\[Document\\{\\{tags=NGTag\\(key=env, value=prod\\)}}, "
            + "Document\\{\\{tags=NGTag\\(key=team, value=platform\\)}}]}}]}}]");

    // AND operator with 3 key-value tags
    form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build(),
                                    NGTag.builder().key("team").value("platform").build(),
                                    NGTag.builder().key("region").value("us-west").build()))
                                .operator(FilterWithOperator.FilterOperator.AND)
                                .build())
            .build(),
        null, null, null, false, false, true, null);
    assertThat(form.getCriteriaObject().get("$and").toString())
        .matches("\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{\\$and=\\[Document\\{\\{tags=NGTag\\(key=env, value=prod\\)}}, "
            + "Document\\{\\{tags=NGTag\\(key=team, value=platform\\)}}, "
            + "Document\\{\\{tags=NGTag\\(key=region, value=us-west\\)}}]}}]}}]");

    // OR operator with key-value tags
    form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build(),
                                    NGTag.builder().key("team").value("platform").build()))
                                .operator(FilterWithOperator.FilterOperator.OR)
                                .build())
            .build(),
        null, null, null, false, false, true, null);
    assertThat(form.getCriteriaObject().get("$and").toString())
        .matches("\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{\\$or=\\[Document\\{\\{tags=NGTag\\(key=env, value=prod\\)}}, "
            + "Document\\{\\{tags=NGTag\\(key=team, value=platform\\)}}]}}]}}]");

    // Key-only tags: match either tags.key OR tags.value with the key
    form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(
                FilterWithOperator.<NGTag>builder()
                    .itemsList(Arrays.asList(NGTag.builder().key("env").build(), NGTag.builder().key("team").build()))
                    .operator(FilterWithOperator.FilterOperator.AND)
                    .build())
            .build(),
        null, null, null, false, false, true, null);
    assertThat(form.getCriteriaObject().get("$and").toString())
        .matches("\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{\\$and=\\[Document\\{\\{\\$or=\\[Document\\{\\{tags\\.key=env}}, "
            + "Document\\{\\{tags\\.value=env}}]}}, "
            + "Document\\{\\{\\$or=\\[Document\\{\\{tags\\.key=team}}, "
            + "Document\\{\\{tags\\.value=team}}]}}]}}]}}]");

    // Mixed key-value and key-only: key-value uses $and[key+value], key-only uses $or[key||value]
    form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build(),
                                    NGTag.builder().key("team").build()))
                                .operator(FilterWithOperator.FilterOperator.AND)
                                .build())
            .build(),
        null, null, null, false, false, true, null);
    assertThat(form.getCriteriaObject().get("$and").toString())
        .matches("\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{\\$and=\\[Document\\{\\{tags=NGTag\\(key=env, value=prod\\)}}, "
            + "Document\\{\\{\\$or=\\[Document\\{\\{tags\\.key=team}}, "
            + "Document\\{\\{tags\\.value=team}}]}}]}}]}}]");

    // Multiple tags (4): 4 separate criteria combined with AND
    form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build(),
                                    NGTag.builder().key("team").value("platform").build(),
                                    NGTag.builder().key("region").value("us-west").build(),
                                    NGTag.builder().key("critical").build()))
                                .operator(FilterWithOperator.FilterOperator.AND)
                                .build())
            .build(),
        null, null, null, false, false, true, null);
    assertThat(form.getCriteriaObject().get("$and").toString())
        .matches("\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{\\$and=\\[Document\\{\\{tags=NGTag\\(key=env, value=prod\\)}}, "
            + "Document\\{\\{tags=NGTag\\(key=team, value=platform\\)}}, "
            + "Document\\{\\{tags=NGTag\\(key=region, value=us-west\\)}}, "
            + "Document\\{\\{\\$or=\\[Document\\{\\{tags\\.key=critical}}, "
            + "Document\\{\\{tags\\.value=critical}}]}}]}}]}}]");

    // Both V1 and V2 tags set: V2 takes precedence, V1 tags are ignored
    form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTags(Arrays.asList(NGTag.builder().key("tagKey1").build(),
                NGTag.builder().key("tagValue2").build(), NGTag.builder().key("tagKey3").value("tagValue3").build()))
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build(),
                                    NGTag.builder().key("team").value("platform").build()))
                                .operator(FilterWithOperator.FilterOperator.AND)
                                .build())
            .build(),
        null, null, null, false, false, true, null);
    assertThat(form.getCriteriaObject().get("$and").toString())
        .matches("\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{\\$and=\\[Document\\{\\{tags=NGTag\\(key=env, value=prod\\)}}, "
            + "Document\\{\\{tags=NGTag\\(key=team, value=platform\\)}}]}}]}}]");

    // Single tag: still creates nested criteria structure
    form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build()))
                                .operator(FilterWithOperator.FilterOperator.AND)
                                .build())
            .build(),
        null, null, null, false, false, true, null);
    assertThat(form.getCriteriaObject().get("$and").toString())
        .matches("\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{\\$and=\\[Document\\{\\{tags=NGTag\\(key=env, value=prod\\)}}]}}]}}]");

    // Tags V2 combined with status filter
    form = pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER, PIPELINE_IDENTIFIER, null,
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key("env").value("prod").build(),
                                    NGTag.builder().key("team").value("platform").build()))
                                .operator(FilterWithOperator.FilterOperator.AND)
                                .build())
            .build(),
        null, null, Arrays.asList(ExecutionStatus.SUCCESS, ExecutionStatus.FAILED), false, false, true, null);
    assertThat(form.getCriteriaObject().get("$and").toString())
        .matches("\\[Document\\{\\{startTs=Document\\{\\{\\$gte=\\d+, \\$lte=\\d+}}, "
            + "executionMode=Document\\{\\{\\$ne=PIPELINE_ROLLBACK}}, "
            + "\\$and=\\[Document\\{\\{\\$and=\\[Document\\{\\{tags=NGTag\\(key=env, value=prod\\)}}, "
            + "Document\\{\\{tags=NGTag\\(key=team, value=platform\\)}}]}}]}}]");

    // Null key in tag throws InvalidRequestException
    PipelineExecutionFilterPropertiesDTO nullKeyProps =
        PipelineExecutionFilterPropertiesDTO.builder()
            .pipelineTagsV2(FilterWithOperator.<NGTag>builder()
                                .itemsList(Arrays.asList(NGTag.builder().key(null).value("prod").build()))
                                .operator(FilterWithOperator.FilterOperator.AND)
                                .build())
            .build();
    assertThatThrownBy(()
                           -> pmsExecutionService.formCriteria(ACCOUNT_ID, ORG_IDENTIFIER, PROJ_IDENTIFIER,
                               PIPELINE_IDENTIFIER, null, nullKeyProps, null, null, null, false, false, true, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Key in Pipeline Tags filter cannot be null");
  }
}
