/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.helper;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BHUMIJ;
import static io.harness.rule.OwnerRule.KAPIL_GARG;
import static io.harness.rule.OwnerRule.MEENA;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.NIKHIL_NEERUDU;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.RISHABH;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SAMARTH;
import static io.harness.rule.OwnerRule.SOURABH;
import static io.harness.rule.OwnerRule.UTKARSH_CHOUBEY;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.ModuleType;
import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.DuplicateFileImportException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.filter.FilterType;
import io.harness.filter.dto.FilterDTO;
import io.harness.filter.service.FilterService;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitaware.helper.GitAwareEntityHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.helpers.GitContextHelper;
import io.harness.gitsync.interceptor.GitEntityInfo;
import io.harness.governance.GovernanceMetadata;
import io.harness.licensing.enforcement.client.FlexEnforcementClient;
import io.harness.licensing.enforcement.client.FlexEnforcementHandler;
import io.harness.licensing.enforcement.client.model.DegradationLevel;
import io.harness.licensing.enforcement.client.model.FlexEnforcementRequest;
import io.harness.licensing.enforcement.client.model.FlexEnforcementResponse;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.ng.core.template.TemplateMergeResponseDTO;
import io.harness.ng.core.template.TemplateReferenceSummary;
import io.harness.pms.filter.creation.FilterCreatorMergeServiceResponse;
import io.harness.pms.filter.creation.service.FilterCreatorMergeService;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineEntity.PipelineEntityKeys;
import io.harness.pms.pipeline.PipelineFilterPropertiesDto;
import io.harness.pms.pipeline.PipelineImportRequestDTO;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceService;
import io.harness.pms.pipeline.references.PipelineSetupUsageCreationHelper;
import io.harness.pms.pipeline.service.enforcement.PMSPipelineTemplateHelper;
import io.harness.pms.pipeline.service.intfc.PMSPipelineService;
import io.harness.pms.pipeline.validation.PipelineValidationResponse;
import io.harness.pms.pipeline.validation.service.intfc.PipelineValidationService;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YamlUtils;
import io.harness.pms.yaml.preprocess.YamlPreProcessorFactory;
import io.harness.pms.yaml.preprocess.YamlPreprocessorResponseDTO;
import io.harness.pms.yaml.preprocess.YamlV1PreProcessor;
import io.harness.repositories.pipeline.PMSPipelineRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.utils.ScopeResolutionHelper;
import io.harness.yaml.validator.InvalidYamlException;

import com.mongodb.BasicDBList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.bson.Document;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.jupiter.api.Assertions;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(PIPELINE)
public class PMSPipelineServiceHelperTest extends PipelineServiceTestBase {
  @Mock FilterService filterService;
  @Mock FilterCreatorMergeService filterCreatorMergeService;
  @Mock PMSPipelineTemplateHelper pipelineTemplateHelper;
  @Mock GitAwareEntityHelper gitAwareEntityHelper;
  @Mock PMSPipelineRepository pmsPipelineRepository;
  @Mock PipelineValidationService pipelineValidationService;
  @Mock PipelineGovernanceService pipelineGovernanceService;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;
  @Mock PipelineSetupUsageCreationHelper pipelineSetupUsageCreationHelper;
  @Mock PMSPipelineService pmsPipelineService;
  @Mock YamlV1PreProcessor preProcessor;
  @Mock YamlPreProcessorFactory yamlPreprocessorFactory;
  @Spy @InjectMocks PMSPipelineServiceHelper pmsPipelineServiceHelper;
  @Mock ScopeResolutionHelper scopeResolutionHelper;
  @Mock FlexEnforcementClient flexEnforcementClient;

  String accountIdentifier = "account";
  String orgIdentifier = "org";
  String projectIdentifier = "project";
  String pipelineIdentifier = "pipeline";
  String parentUniqueId = "someRandomId";

  String repoName = "testRepo";

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testValidatePresenceOfRequiredFields() {
    assertThatThrownBy(
        ()
            -> PMSPipelineServiceHelper.validatePresenceOfRequiredFields(PipelineEntity.builder()
                                                                             .accountId(accountIdentifier)
                                                                             .orgIdentifier(orgIdentifier)
                                                                             .identifier(pipelineIdentifier)
                                                                             .name("name")
                                                                             .build(),
                false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Required field [parentUniqueId] is either null or empty.");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetPipelineEqualityCriteria() {
    Criteria criteria = PMSPipelineServiceHelper.getPipelineEqualityCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, false, 2L);
    assertThat(criteria).isNotNull();
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(PipelineEntityKeys.accountId)).isEqualTo(accountIdentifier);
    assertThat(criteriaObject.get(PipelineEntityKeys.orgIdentifier)).isEqualTo(orgIdentifier);
    assertThat(criteriaObject.get(PipelineEntityKeys.projectIdentifier)).isEqualTo(projectIdentifier);
    assertThat(criteriaObject.get(PipelineEntityKeys.identifier)).isEqualTo(pipelineIdentifier);
    assertThat(criteriaObject.get(PipelineEntityKeys.deleted)).isEqualTo(false);
    assertThat(criteriaObject.get(PipelineEntityKeys.version)).isEqualTo(2L);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testUpdatePipelineInfo() throws IOException {
    FilterCreatorMergeServiceResponse response =
        FilterCreatorMergeServiceResponse.builder()
            .stageCount(1)
            .stageNames(Collections.singletonList("stage-1"))
            .filters(Collections.singletonMap("whatKey?", "{\"some\" : \"value\"}"))
            .build();
    doReturn(response).when(filterCreatorMergeService).getPipelineInfo(any());
    PipelineEntity entity = PipelineEntity.builder().build();
    PipelineEntity updatedEntity =
        pmsPipelineServiceHelper.updatePipelineInfo(entity, HarnessYamlVersion.V0, null, false);
    assertThat(updatedEntity.getStageCount()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().size()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().contains("stage-1")).isTrue();
    assertThat(updatedEntity.getFilters().size()).isEqualTo(1);
    assertThat(updatedEntity.getFilters().containsKey("whatKey?")).isTrue();
    assertThat(updatedEntity.getFilters().containsValue(Document.parse("{\"some\" : \"value\"}"))).isTrue();

    response = FilterCreatorMergeServiceResponse.builder()
                   .stageCount(1)
                   .stageNames(Collections.singletonList("stage-1"))
                   .build();
    doReturn(response).when(filterCreatorMergeService).getPipelineInfo(any());
    updatedEntity = pmsPipelineServiceHelper.updatePipelineInfo(updatedEntity, HarnessYamlVersion.V0, null, false);
    assertThat(updatedEntity.getStageCount()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().size()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().contains("stage-1")).isTrue();
    assertThat(updatedEntity.getFilters().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdatePipelineInfoWithEmptyFilterValue() throws IOException {
    FilterCreatorMergeServiceResponse response = FilterCreatorMergeServiceResponse.builder()
                                                     .stageCount(1)
                                                     .stageNames(Collections.singletonList("stage-1"))
                                                     .filters(Collections.singletonMap("whatKey?", ""))
                                                     .build();
    doReturn(response).when(filterCreatorMergeService).getPipelineInfo(any());
    PipelineEntity entity = PipelineEntity.builder().build();
    PipelineEntity updatedEntity =
        pmsPipelineServiceHelper.updatePipelineInfo(entity, HarnessYamlVersion.V0, null, false);
    assertThat(updatedEntity.getStageCount()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().size()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().contains("stage-1")).isTrue();
    assertThat(updatedEntity.getFilters().size()).isEqualTo(1);
    assertThat(updatedEntity.getFilters().containsKey("whatKey?")).isTrue();
    assertThat(updatedEntity.getFilters().containsValue(Document.parse("{}"))).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testUpdatePipelineInfoWithInvalidFilterValue() throws IOException {
    FilterCreatorMergeServiceResponse response = FilterCreatorMergeServiceResponse.builder()
                                                     .stageCount(1)
                                                     .stageNames(Collections.singletonList("stage-1"))
                                                     .filters(Collections.singletonMap("whatKey?", "-`6^!"))
                                                     .build();
    doReturn(response).when(filterCreatorMergeService).getPipelineInfo(any());
    PipelineEntity entity = PipelineEntity.builder().build();
    PipelineEntity updatedEntity =
        pmsPipelineServiceHelper.updatePipelineInfo(entity, HarnessYamlVersion.V0, null, false);
    assertThat(updatedEntity.getStageCount()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().size()).isEqualTo(1);
    assertThat(updatedEntity.getStageNames().contains("stage-1")).isTrue();
    assertThat(updatedEntity.getFilters().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testUpdatePipelineInfoSkippedWhenYamlEmptyV1() throws IOException {
    // V1 metadata-only PATCH legitimately omits the YAML body; in that case filter/reference
    // re-computation must be skipped because nothing in the YAML changed and existing DB values
    // should be left untouched.
    PipelineEntity entity = PipelineEntity.builder()
                                .stageCount(7)
                                .stageNames(Collections.singletonList("existing-stage"))
                                .harnessVersion(HarnessYamlVersion.V1)
                                .build();
    PipelineEntity result = pmsPipelineServiceHelper.updatePipelineInfo(entity, HarnessYamlVersion.V1, null, false);
    assertThat(result).isSameAs(entity);
    assertThat(result.getStageCount()).isEqualTo(7);
    verify(filterCreatorMergeService, times(0)).getPipelineInfo(any());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testPopulateFilterUsingIdentifier() {
    String filterIdentifier = "filterIdentifier";
    FilterDTO filterDTO = FilterDTO.builder()
                              .filterProperties(PipelineFilterPropertiesDto.builder()
                                                    .name(pipelineIdentifier)
                                                    .description("some description")
                                                    .pipelineTags(List.of(NGTag.builder().key("c").value("h").build(),
                                                        NGTag.builder().key("c").value(null).build()))
                                                    .pipelineIdentifiers(Collections.singletonList(pipelineIdentifier))
                                                    .build())
                              .build();
    doReturn(null)
        .when(filterService)
        .get(accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, FilterType.PIPELINESETUP);
    assertThatThrownBy(()
                           -> pmsPipelineServiceHelper.populateFilterUsingIdentifier(new ArrayList<>(), new Criteria(),
                               accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, null, false))
        .isInstanceOf(InvalidRequestException.class);
    doReturn(filterDTO)
        .when(filterService)
        .get(accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, FilterType.PIPELINESETUP);
    Criteria criteria = new Criteria();
    List<Criteria> criteriaList = new ArrayList<>();
    pmsPipelineServiceHelper.populateFilterUsingIdentifier(
        criteriaList, criteria, accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, null, false);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(PipelineEntityKeys.name).toString()).isEqualTo(pipelineIdentifier);
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get(PipelineEntityKeys.identifier)).get("$in")).size())
        .isEqualTo(1);
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get(PipelineEntityKeys.identifier)).get("$in"))
                   .contains(pipelineIdentifier))
        .isTrue();
    assertEquals(criteriaList.size(), 1);
    assertEquals(criteriaList.get(0).getCriteriaObject().toString(),
        "Document{{$or=[Document{{tags.key=Document{{$in=[c]}}}}, Document{{tags.value=Document{{$in=[c]}}}}, "
            + "Document{{tags=Document{{$in=[NGTag(key=c, value=h)]}}}}]}}");
    filterDTO = FilterDTO.builder()
                    .filterProperties(PipelineFilterPropertiesDto.builder()
                                          .name(pipelineIdentifier)
                                          .description("some description")
                                          .pipelineTags(List.of(NGTag.builder().key(null).value("c").build()))
                                          .pipelineIdentifiers(Collections.singletonList(pipelineIdentifier))
                                          .build())
                    .build();
    doReturn(filterDTO)
        .when(filterService)
        .get(accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, FilterType.PIPELINESETUP);
    assertThatThrownBy(()
                           -> pmsPipelineServiceHelper.populateFilterUsingIdentifier(new ArrayList<>(), criteria,
                               accountIdentifier, orgIdentifier, projectIdentifier, filterIdentifier, null, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Key in Pipeline Tags filter cannot be null");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testPopulateFilter() {
    List<Criteria> criteriaList = new ArrayList<>();
    Criteria criteria = new Criteria();
    PipelineFilterPropertiesDto pipelineFilter =
        PipelineFilterPropertiesDto.builder()
            .name(pipelineIdentifier)
            .description("some description")
            .pipelineTags(Collections.singletonList(NGTag.builder().key("c").value("h").build()))
            .pipelineIdentifiers(Collections.singletonList(pipelineIdentifier))
            .build();
    PMSPipelineServiceHelper.populateFilter(criteriaList, criteria, pipelineFilter);
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(PipelineEntityKeys.name).toString()).isEqualTo(pipelineIdentifier);
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get(PipelineEntityKeys.identifier)).get("$in")).size())
        .isEqualTo(1);
    assertThat(((List<?>) ((Map<?, ?>) criteriaObject.get(PipelineEntityKeys.identifier)).get("$in"))
                   .contains(pipelineIdentifier))
        .isTrue();
    assertEquals(criteriaList.size(), 1);
    assertEquals(
        criteriaList.get(0).getCriteriaObject().toString(), "Document{{tags=Document{{$in=[NGTag(key=c, value=h)]}}}}");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testValidatePipelineYamlInternal() {
    String yaml = "yaml";
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .yaml(yaml)
                                        .build();
    List<TemplateReferenceSummary> templateReferenceSummaryList = new ArrayList<>();
    TemplateMergeResponseDTO templateMergeResponseDTO = TemplateMergeResponseDTO.builder()
                                                            .mergedPipelineYaml(yaml)
                                                            .templateReferenceSummaries(templateReferenceSummaryList)
                                                            .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .scopeType(ScopeLevel.PROJECT)
                              .uniqueId(parentUniqueId)
                              .build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, scopeInfo, false, false);

    when(pipelineValidationService.validateYamlAndGovernanceRules(any(), any(), any(), any(), any(), any()))
        .thenReturn(PipelineValidationResponse.builder()
                        .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                        .build());
    GovernanceMetadata governanceMetadata = pmsPipelineServiceHelper.resolveTemplatesAndValidatePipelineYaml(
        pipelineEntity, true, false, scopeInfo, false, false, null);
    assertThat(governanceMetadata.getDeny()).isFalse();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testValidatePipelineYamlInternalForV1Pipeline() {
    String yaml = "yaml";
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .yaml(yaml)
                                        .harnessVersion(HarnessYamlVersion.V1)
                                        .build();
    doReturn(preProcessor).when(yamlPreprocessorFactory).getProcessorInstance(HarnessYamlVersion.V1);
    doReturn(YamlPreprocessorResponseDTO.builder().preprocessedJsonNode(YamlUtils.readAsJsonNode(yaml)).build())
        .when(preProcessor)
        .preProcess(yaml, false);
    List<TemplateReferenceSummary> templateReferenceSummaryList = new ArrayList<>();
    TemplateMergeResponseDTO templateMergeResponseDTO = TemplateMergeResponseDTO.builder()
                                                            .mergedPipelineYaml(yaml)
                                                            .templateReferenceSummaries(templateReferenceSummaryList)
                                                            .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .scopeType(ScopeLevel.PROJECT)
                              .uniqueId(parentUniqueId)
                              .build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, scopeInfo, false, false);

    when(pipelineValidationService.validateYamlAndGovernanceRules(any(), any(), any(), any(), any(), any()))
        .thenReturn(PipelineValidationResponse.builder()
                        .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                        .build());
    GovernanceMetadata governanceMetadata = pmsPipelineServiceHelper.resolveTemplatesAndValidatePipelineYaml(
        pipelineEntity, true, false, scopeInfo, false, false, null);
    assertThat(governanceMetadata.getDeny()).isFalse();
  }

  @Test
  @Owner(developers = SAMARTH)
  @Category(UnitTests.class)
  public void testFormCriteria() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .scopeType(ScopeLevel.PROJECT)
                              .uniqueId(parentUniqueId)
                              .build();
    Criteria form = pmsPipelineServiceHelper.formCriteria(accountIdentifier, orgIdentifier, projectIdentifier, null,
        null, false, ModuleType.CI.name(), null, scopeInfo, true);

    assertThat(form.getCriteriaObject().get("accountId").toString().contentEquals(accountIdentifier)).isEqualTo(true);
    assertThat(form.getCriteriaObject().get(PipelineEntityKeys.parentUniqueId).toString().contentEquals(parentUniqueId))
        .isEqualTo(true);
    assertThat(form.getCriteriaObject().containsKey("status")).isEqualTo(false);
    assertThat(form.getCriteriaObject().get("deleted")).isEqualTo(false);
    BasicDBList orFilter =
        ((Document) form.getCriteriaObject().get("$and", BasicDBList.class).get(0)).get("$or", BasicDBList.class);
    assert ((Document) (orFilter.get(1))).containsKey("filters.ci");
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testFormCriteriaRepoFilter() {
    PipelineFilterPropertiesDto filterProperties = PipelineFilterPropertiesDto.builder().repoName(repoName).build();
    Criteria criteria = pmsPipelineServiceHelper.formCriteria(
        accountIdentifier, orgIdentifier, projectIdentifier, null, filterProperties, false, null, null, null, false);

    assertThat(criteria.getCriteriaObject().get(PipelineEntityKeys.repo).toString()).isEqualTo(repoName);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildInvalidYamlException() {
    String error = "this error message";
    String yaml = "yaml";
    InvalidYamlException invalidYamlException = PMSPipelineServiceHelper.buildInvalidYamlException(error, yaml);
    assertThat(invalidYamlException.getYaml()).isEqualTo(yaml);
    assertThatThrownBy(() -> { throw invalidYamlException; }).hasMessage(error);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetRepoUrlAndCheckForFileUniqueness() {
    String repoUrl = "repoUrl";
    GitEntityInfo gitEntityInfo = GitEntityInfo.builder().filePath("filePath").build();
    MockedStatic<GitAwareContextHelper> utilities = Mockito.mockStatic(GitAwareContextHelper.class);
    utilities.when(GitAwareContextHelper::getGitRequestParamsInfo).thenReturn(gitEntityInfo);

    doReturn(repoUrl).when(gitAwareEntityHelper).getRepoUrl(accountIdentifier, orgIdentifier, projectIdentifier);
    doReturn(10L)
        .when(pmsPipelineRepository)
        .countFileInstances(accountIdentifier, repoUrl, gitEntityInfo.getFilePath());
    assertThatThrownBy(()
                           -> pmsPipelineServiceHelper.getRepoUrlAndCheckForFileUniqueness(accountIdentifier,
                               orgIdentifier, projectIdentifier, pipelineIdentifier, false, null, false))
        .isInstanceOf(DuplicateFileImportException.class);
    assertThat(pmsPipelineServiceHelper.getRepoUrlAndCheckForFileUniqueness(
                   accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, true, null, false))
        .isEqualTo(repoUrl);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testImportPipelineValidationChecks() {
    String importedPipeline = "pipeline:\n"
        + "  name: abcPipelineImport\n"
        + "  identifier: abcPipelineImport\n"
        + "  projectIdentifier: GitX_Remote\n"
        + "  orgIdentifier: default\n"
        + "  tags: {}\n"
        + "  stages:\n"
        + "    - stage:\n"
        + "        name: zd\n"
        + "        identifier: zd\n"
        + "        description: \"\"\n"
        + "        type: Approval\n"
        + "        spec:\n"
        + "          execution:\n"
        + "            steps:\n"
        + "              - step:\n"
        + "                  name: dsf\n"
        + "                  identifier: dsf\n"
        + "                  type: HarnessApproval\n"
        + "                  timeout: 1d\n"
        + "                  spec:\n"
        + "                    approvalMessage: |-\n"
        + "                      Please review the following information\n"
        + "                      and approve the pipeline progression\n"
        + "                    includePipelineExecutionHistory: true\n"
        + "                    approvers:\n"
        + "                      minimumCount: 1\n"
        + "                      disallowPipelineExecutor: false\n"
        + "                      userGroups: <+input>\n"
        + "                    approverInputs: []\n"
        + "        tags: {}";
    String orgIdentifier = "default";
    String projectIdentifier = "GitX_Remote";
    String pipelineIdentifier = "abcPipelineImport";
    PipelineImportRequestDTO pipelineImportRequest = PipelineImportRequestDTO.builder()
                                                         .pipelineName("abcPipelineImport")
                                                         .pipelineDescription("junk pipeline description")
                                                         .build();
    Assertions.assertDoesNotThrow(
        ()
            -> PMSPipelineServiceHelper.checkAndThrowMismatchInImportedPipelineMetadataInternal(
                orgIdentifier, projectIdentifier, pipelineIdentifier, pipelineImportRequest, importedPipeline));
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testResolveTemplatesAndValidatePipeline() {
    String yaml = "yaml";
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .yaml(yaml)
                                        .build();
    List<TemplateReferenceSummary> templateReferenceSummaryList = new ArrayList<>();
    TemplateMergeResponseDTO templateMergeResponseDTO = TemplateMergeResponseDTO.builder()
                                                            .mergedPipelineYaml(yaml)
                                                            .templateReferenceSummaries(templateReferenceSummaryList)
                                                            .build();
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .scopeType(ScopeLevel.PROJECT)
                              .uniqueId(parentUniqueId)
                              .build();
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(pipelineEntity, scopeInfo, false, false);

    when(pipelineValidationService.validateYamlAndGovernanceRules(any(), any(), any(), any(), any(), any()))
        .thenReturn(PipelineValidationResponse.builder()
                        .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                        .build());

    GitEntityInfo gitEntityInfo = GitEntityInfo.builder()
                                      .repoName("repoName")
                                      .connectorRef("connectorRef")
                                      .isNewBranch(true)
                                      .branch("branch")
                                      .build();
    GitAwareContextHelper.updateGitEntityContext(gitEntityInfo);

    GovernanceMetadata governanceMetadata = pmsPipelineServiceHelper.resolveTemplatesAndValidatePipeline(
        pipelineEntity, true, false, scopeInfo, false, false);
    GitEntityInfo gitEntityInfo1 = GitContextHelper.getGitEntityInfo();

    assertEquals(gitEntityInfo1.getBranch(), gitEntityInfo.getBranch());
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveTemplatesAndValidatePipeline_newBranch_ffOff_opaSeesNewBranch() {
    // FF disabled (default): OPA must see newBranch + storeType=REMOTE, not the baseBranch
    String yaml = "yaml";
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .yaml(yaml)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    TemplateMergeResponseDTO templateMergeResponseDTO = TemplateMergeResponseDTO.builder()
                                                            .mergedPipelineYaml(yaml)
                                                            .templateReferenceSummaries(new ArrayList<>())
                                                            .build();

    doReturn(false).when(pmsFeatureFlagService).isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE);
    doReturn(false)
        .when(pmsFeatureFlagService)
        .isEnabled(accountIdentifier, FeatureName.PIPE_DISABLE_OPA_GITCONFIG_NEW_BRANCH_FIX);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(any(), any(), eq(false), eq(false));

    AtomicReference<GitEntityInfo> capturedOpaContext = new AtomicReference<>();
    when(pipelineValidationService.validateYamlAndGovernanceRules(any(), any(), any(), any(), any(), any()))
        .thenAnswer(inv -> {
          capturedOpaContext.set(GitContextHelper.getGitEntityInfo());
          return PipelineValidationResponse.builder()
              .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
              .build();
        });

    GitAwareContextHelper.updateGitEntityContext(GitEntityInfo.builder()
                                                     .branch("new-branch")
                                                     .baseBranch("main")
                                                     .isNewBranch(true)
                                                     .storeType(StoreType.REMOTE)
                                                     .connectorRef("connectorRef")
                                                     .repoName("repoName")
                                                     .build());

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .scopeType(ScopeLevel.PROJECT)
                              .uniqueId(parentUniqueId)
                              .build();
    pmsPipelineServiceHelper.resolveTemplatesAndValidatePipeline(pipelineEntity, true, false, scopeInfo, false, false);

    assertThat(capturedOpaContext.get()).isNotNull();
    assertThat(capturedOpaContext.get().getBranch()).isEqualTo("new-branch");
    assertThat(capturedOpaContext.get().getStoreType()).isEqualTo(StoreType.REMOTE);
    assertThat(capturedOpaContext.get().isNewBranch()).isTrue();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveTemplatesAndValidatePipeline_newBranch_ffOff_templateResolutionUsesBaseBranch() {
    // FF disabled (default): template resolution must use baseBranch — applyTemplatesOnGivenYamlV2 sends
    // gitEntityInfo.getBranch() to the template service, so baseBranch must be active in context at that point
    String yaml = "yaml";
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .yaml(yaml)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    TemplateMergeResponseDTO templateMergeResponseDTO = TemplateMergeResponseDTO.builder()
                                                            .mergedPipelineYaml(yaml)
                                                            .templateReferenceSummaries(new ArrayList<>())
                                                            .build();

    doReturn(false).when(pmsFeatureFlagService).isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE);
    doReturn(false)
        .when(pmsFeatureFlagService)
        .isEnabled(accountIdentifier, FeatureName.PIPE_DISABLE_OPA_GITCONFIG_NEW_BRANCH_FIX);

    AtomicReference<GitEntityInfo> capturedTemplateContext = new AtomicReference<>();
    when(pipelineTemplateHelper.resolveTemplateRefsInPipeline(any(), any(), eq(false), eq(false))).thenAnswer(inv -> {
      capturedTemplateContext.set(GitContextHelper.getGitEntityInfo());
      return templateMergeResponseDTO;
    });
    when(pipelineValidationService.validateYamlAndGovernanceRules(any(), any(), any(), any(), any(), any()))
        .thenReturn(PipelineValidationResponse.builder()
                        .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                        .build());

    GitAwareContextHelper.updateGitEntityContext(GitEntityInfo.builder()
                                                     .branch("new-branch")
                                                     .baseBranch("main")
                                                     .isNewBranch(true)
                                                     .storeType(StoreType.REMOTE)
                                                     .connectorRef("connectorRef")
                                                     .repoName("repoName")
                                                     .build());

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .scopeType(ScopeLevel.PROJECT)
                              .uniqueId(parentUniqueId)
                              .build();
    pmsPipelineServiceHelper.resolveTemplatesAndValidatePipeline(pipelineEntity, true, false, scopeInfo, false, false);

    assertThat(capturedTemplateContext.get()).isNotNull();
    assertThat(capturedTemplateContext.get().getBranch()).isEqualTo("main");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveTemplatesAndValidatePipeline_newBranch_ffOn_opaSeesBaseBranch() {
    // FF enabled (kill switch): old behaviour — OPA sees baseBranch context, not newBranch
    String yaml = "yaml";
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .yaml(yaml)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    TemplateMergeResponseDTO templateMergeResponseDTO = TemplateMergeResponseDTO.builder()
                                                            .mergedPipelineYaml(yaml)
                                                            .templateReferenceSummaries(new ArrayList<>())
                                                            .build();

    doReturn(false).when(pmsFeatureFlagService).isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE);
    doReturn(true)
        .when(pmsFeatureFlagService)
        .isEnabled(accountIdentifier, FeatureName.PIPE_DISABLE_OPA_GITCONFIG_NEW_BRANCH_FIX);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(any(), any(), eq(false), eq(false));

    AtomicReference<GitEntityInfo> capturedOpaContext = new AtomicReference<>();
    when(pipelineValidationService.validateYamlAndGovernanceRules(any(), any(), any(), any(), any(), any()))
        .thenAnswer(inv -> {
          capturedOpaContext.set(GitContextHelper.getGitEntityInfo());
          return PipelineValidationResponse.builder()
              .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
              .build();
        });

    GitAwareContextHelper.updateGitEntityContext(GitEntityInfo.builder()
                                                     .branch("new-branch")
                                                     .baseBranch("main")
                                                     .isNewBranch(true)
                                                     .storeType(StoreType.REMOTE)
                                                     .connectorRef("connectorRef")
                                                     .repoName("repoName")
                                                     .build());

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .scopeType(ScopeLevel.PROJECT)
                              .uniqueId(parentUniqueId)
                              .build();
    pmsPipelineServiceHelper.resolveTemplatesAndValidatePipeline(pipelineEntity, true, false, scopeInfo, false, false);

    assertThat(capturedOpaContext.get()).isNotNull();
    assertThat(capturedOpaContext.get().getBranch()).isEqualTo("main");
    assertThat(capturedOpaContext.get().getStoreType()).isNull();
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testResolveTemplatesAndValidatePipeline_newBranch_ffOff_contextRestoredAfterCall() {
    // After the call returns, the ThreadLocal context must be back to the original request context
    String yaml = "yaml";
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId(accountIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .projectIdentifier(projectIdentifier)
                                        .yaml(yaml)
                                        .storeType(StoreType.REMOTE)
                                        .build();
    TemplateMergeResponseDTO templateMergeResponseDTO = TemplateMergeResponseDTO.builder()
                                                            .mergedPipelineYaml(yaml)
                                                            .templateReferenceSummaries(new ArrayList<>())
                                                            .build();

    doReturn(false).when(pmsFeatureFlagService).isEnabled(accountIdentifier, FeatureName.OPA_PIPELINE_GOVERNANCE);
    doReturn(false)
        .when(pmsFeatureFlagService)
        .isEnabled(accountIdentifier, FeatureName.PIPE_DISABLE_OPA_GITCONFIG_NEW_BRANCH_FIX);
    doReturn(templateMergeResponseDTO)
        .when(pipelineTemplateHelper)
        .resolveTemplateRefsInPipeline(any(), any(), eq(false), eq(false));
    when(pipelineValidationService.validateYamlAndGovernanceRules(any(), any(), any(), any(), any(), any()))
        .thenReturn(PipelineValidationResponse.builder()
                        .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                        .build());

    GitAwareContextHelper.updateGitEntityContext(GitEntityInfo.builder()
                                                     .branch("new-branch")
                                                     .baseBranch("main")
                                                     .isNewBranch(true)
                                                     .storeType(StoreType.REMOTE)
                                                     .connectorRef("connectorRef")
                                                     .repoName("repoName")
                                                     .build());

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .scopeType(ScopeLevel.PROJECT)
                              .uniqueId(parentUniqueId)
                              .build();
    pmsPipelineServiceHelper.resolveTemplatesAndValidatePipeline(pipelineEntity, true, false, scopeInfo, false, false);

    GitEntityInfo restored = GitContextHelper.getGitEntityInfo();
    assertThat(restored.getBranch()).isEqualTo("new-branch");
    assertThat(restored.getStoreType()).isEqualTo(StoreType.REMOTE);
    assertThat(restored.isNewBranch()).isTrue();
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testSetPermittedPipeline() {
    Criteria criteria = new Criteria();
    List<String> pipelinelist = Arrays.asList(pipelineIdentifier);
    doReturn(false)
        .when(pmsPipelineService)
        .validateViewPermission(accountIdentifier, orgIdentifier, projectIdentifier);
    doReturn(pipelinelist).when(pmsPipelineService).listAllIdentifiers(any());
    doReturn(pipelinelist)
        .when(pmsPipelineService)
        .getPermittedPipelineIdentifier(accountIdentifier, orgIdentifier, projectIdentifier, pipelinelist);
    pmsPipelineServiceHelper.setPermittedPipelines(
        accountIdentifier, orgIdentifier, projectIdentifier, criteria, PlanExecutionSummaryKeys.pipelineIdentifier);

    assertThat(criteria.getCriteriaObject().get(PlanExecutionSummaryKeys.pipelineIdentifier).toString())
        .isEqualTo("Document{{$in=[pipeline]}}");

    pmsPipelineServiceHelper.setPermittedPipelines(
        accountIdentifier, orgIdentifier, projectIdentifier, criteria, PipelineEntityKeys.identifier);
    assertThat(criteria.getCriteriaObject().get(PipelineEntityKeys.identifier).toString())
        .isEqualTo("Document{{$in=[pipeline]}}");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetPermittedPipelines() {
    Criteria criteria = new Criteria();
    criteria.and(PlanExecutionSummaryKeys.accountId).is(accountIdentifier);
    criteria.and(PlanExecutionSummaryKeys.orgIdentifier).is(orgIdentifier);
    criteria.and(PlanExecutionSummaryKeys.projectIdentifier).is(projectIdentifier);

    List<String> pipelinelist = List.of(pipelineIdentifier);
    doReturn(false)
        .when(pmsPipelineService)
        .validateViewPermission(accountIdentifier, orgIdentifier, projectIdentifier);
    doReturn(pipelinelist).when(pmsPipelineService).listAllIdentifiers(eq(criteria));
    doReturn(pipelinelist)
        .when(pmsPipelineService)
        .getPermittedPipelineIdentifier(accountIdentifier, orgIdentifier, projectIdentifier, pipelinelist);
    assertThat(
        pmsPipelineServiceHelper.getPermittedPipelines(criteria, accountIdentifier, orgIdentifier, projectIdentifier))
        .isEqualTo(pipelinelist);

    doReturn(true).when(pmsPipelineService).validateViewPermission(accountIdentifier, orgIdentifier, projectIdentifier);
    assertThat(
        pmsPipelineServiceHelper.getPermittedPipelines(criteria, accountIdentifier, orgIdentifier, projectIdentifier))
        .isNull();
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetScopeInfo() {
    String newOrg = "newOrg";
    when(scopeResolutionHelper.getScopeInfo(any(), any(), any()))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(accountIdentifier)
                        .orgIdentifier(orgIdentifier)
                        .projectIdentifier(projectIdentifier)
                        .scopeType(ScopeLevel.PROJECT)
                        .uniqueId(parentUniqueId)
                        .build());
    ScopeInfo resultingScopeInfo =
        pmsPipelineServiceHelper.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier, null);
    assertEquals(orgIdentifier, resultingScopeInfo.getOrgIdentifier());
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(newOrg)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(parentUniqueId)
                              .build();
    resultingScopeInfo =
        pmsPipelineServiceHelper.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier, scopeInfo);
    assertEquals(newOrg, resultingScopeInfo.getOrgIdentifier());
  }

  @Test
  @Owner(developers = MEENA)
  @Category(UnitTests.class)
  public void testGetPipelineEqualityCriteriaWithScopeInfo() {
    Criteria criteria = PMSPipelineServiceHelper.getPipelineEqualityCriteria(
        accountIdentifier, parentUniqueId, pipelineIdentifier, false, 2L);
    assertThat(criteria).isNotNull();
    Document criteriaObject = criteria.getCriteriaObject();
    assertThat(criteriaObject.get(PipelineEntityKeys.accountId)).isEqualTo(accountIdentifier);
    assertThat(criteriaObject.get(PipelineEntityKeys.parentUniqueId)).isEqualTo(parentUniqueId);
    assertThat(criteriaObject.get(PipelineEntityKeys.identifier)).isEqualTo(pipelineIdentifier);
    assertThat(criteriaObject.get(PipelineEntityKeys.deleted)).isEqualTo(false);
    assertThat(criteriaObject.get(PipelineEntityKeys.version)).isEqualTo(2L);
  }

  @Test
  @Owner(developers = NIKHIL_NEERUDU)
  @Category(UnitTests.class)
  public void testEscapeRegexSpecialCharacters() {
    // Test null and empty inputs
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters(null)).isNull();
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("")).isEmpty();

    // Test normal text without special characters
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("normalstring")).isEqualTo("normalstring");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("with spaces")).isEqualTo("with spaces");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("123-456_789")).isEqualTo("123-456_789");

    // Test individual special characters - |, ^, $ should NOT be escaped
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters(".")).isEqualTo("\\.");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("^")).isEqualTo("^");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("$")).isEqualTo("$");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("*")).isEqualTo("\\*");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("+")).isEqualTo("\\+");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("?")).isEqualTo("\\?");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("{")).isEqualTo("\\{");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("}")).isEqualTo("\\}");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("[")).isEqualTo("\\[");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("]")).isEqualTo("\\]");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("\\")).isEqualTo("\\\\");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("(")).isEqualTo("\\(");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters(")")).isEqualTo("\\)");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("|")).isEqualTo("|");

    // Test with real-world use cases containing pipe character
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("dev-default|-staging|-release"))
        .isEqualTo("dev-default|-staging|-release");

    // Test mixed content (special chars + pipe)
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("[test]|{value}"))
        .isEqualTo("\\[test\\]|\\{value\\}");

    // Test complex strings with multiple special characters
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("a.b*c+d?e[f]g(h)i$j^"))
        .isEqualTo("a\\.b\\*c\\+d\\?e\\[f\\]g\\(h\\)i$j^");

    // Test common search patterns that might be problematic in MongoDB regex
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("deploy-*")).isEqualTo("deploy-\\*");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("app(v1)")).isEqualTo("app\\(v1\\)");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("$.field.value")).isEqualTo("$\\.field\\.value");

    // Test edge cases with brackets and dollar signs (common in MongoDB queries)
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("{$and:[{$gt:5}]}"))
        .isEqualTo("\\{$and:\\[\\{$gt:5\\}\\]\\}");

    // Test empty special character sequences
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("()")).isEqualTo("\\(\\)");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("[]")).isEqualTo("\\[\\]");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("{}")).isEqualTo("\\{\\}");

    // Test search terms with problematic regex patterns
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters(".*")).isEqualTo("\\.\\*");
    assertThat(PMSPipelineServiceHelper.escapeRegexSpecialCharacters("a{3,5}")).isEqualTo("a\\{3,5\\}");
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testFetchAllPipelinesByFilePathAndRepo() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId("account")
                                        .orgIdentifier("org")
                                        .projectIdentifier("pro")
                                        .identifier("id")
                                        .name("pip")
                                        .yaml("yaml")
                                        .storeType(StoreType.INLINE)
                                        .harnessVersion(HarnessYamlVersion.V0)
                                        .stageCount(1)
                                        .stageName("qaStage")
                                        .version(null)
                                        .deleted(false)
                                        .createdAt(System.currentTimeMillis())
                                        .lastUpdatedAt(System.currentTimeMillis())
                                        .build();
    Stream<PipelineEntity> closeableIterator = createCloseableIterator(List.of(pipelineEntity).iterator()).stream();

    when(pmsPipelineRepository.findAllFromSecondaryDb(any(), any())).thenReturn(closeableIterator);
    ArgumentCaptor<Criteria> criteriaArgumentCaptor = ArgumentCaptor.forClass(Criteria.class);
    pmsPipelineServiceHelper.fetchAllPipelinesByFilePathAndRepo(accountIdentifier, "file", "repo");
    verify(pmsPipelineRepository, times(1)).findAllFromSecondaryDb(criteriaArgumentCaptor.capture(), any());
    Criteria criteria = criteriaArgumentCaptor.getValue();
    assertTrue(criteriaArgumentCaptor.getValue().getCriteriaObject().get("repo").toString().equals("^repo$"));
  }

  private <T> CloseableIterator<T> createCloseableIterator(Iterator<T> iterator) {
    return new CloseableIterator<T>() {
      @Override
      public void close() {}

      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public T next() {
        return iterator.next();
      }
    };
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testValidateAndThrowFlexEnforcementRules_D0VerdictThrowsAccessDenied() throws Exception {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(parentUniqueId)
                              .build();
    doAnswer(invocation -> {
      FlexEnforcementHandler handler = invocation.getArgument(1);
      handler.handle(FlexEnforcementResponse.builder()
                         .degradationLevel(DegradationLevel.D0)
                         .degradationDetail("limit reached")
                         .build());
      return null;
    })
        .when(flexEnforcementClient)
        .check(any(FlexEnforcementRequest.class), any());

    assertThatThrownBy(
        () -> pmsPipelineServiceHelper.validateAndThrowFlexEnforcementRules("PIPELINE_CREATE", scopeInfo))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("Operation blocked by license enforcement: limit reached");
  }

  @Test
  @Owner(developers = KAPIL_GARG)
  @Category(UnitTests.class)
  public void testValidateAndThrowFlexEnforcementRules_NoneVerdictDoesNotThrow() throws Exception {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(parentUniqueId)
                              .build();
    doAnswer(invocation -> {
      FlexEnforcementHandler handler = invocation.getArgument(1);
      handler.handle(FlexEnforcementResponse.builder().degradationLevel(DegradationLevel.NONE).build());
      return null;
    })
        .when(flexEnforcementClient)
        .check(any(FlexEnforcementRequest.class), any());

    Assertions.assertDoesNotThrow(
        () -> pmsPipelineServiceHelper.validateAndThrowFlexEnforcementRules("PIPELINE_CREATE", scopeInfo));
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void checkThatTheModuleExists_ValidModule() {
    List<String> validModules = List.of(
        // valid module in upper case
        ModuleType.CI.name(),
        // valid module in lower case
        ModuleType.CI.name().toLowerCase(),
        // special handling for PMS
        ModuleType.PMS.name(), ModuleType.PMS.name().toLowerCase());
    validModules.forEach(module -> {
      try {
        pmsPipelineServiceHelper.checkThatTheModuleExists(module);
      } catch (Exception exception) {
        Assertions.fail("Unexpected exception thrown for module: " + module, exception);
      }
    });
  }

  @Test
  @Owner(developers = BHUMIJ)
  @Category(UnitTests.class)
  public void checkThatTheModuleExists_InvalidModule() {
    boolean exceptionRaised = false;
    try {
      pmsPipelineServiceHelper.checkThatTheModuleExists(ModuleType.CORE.name());
    } catch (WingsException exception) {
      exceptionRaised = true;
      assertThat(exception).hasMessage(format("Invalid module type [%s]", ModuleType.CORE.name()));
      assertThat(exception.getCause())
          .hasMessage(format("Please select the correct module type %s", ModuleType.getPublicModules()));
    } catch (Exception exception) {
      Assertions.fail("Unexpected exception thrown", exception);
    } finally {
      assertThat(exceptionRaised).isTrue();
    }
  }
}
