/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.mappers;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ADITHYA;
import static io.harness.rule.OwnerRule.AVEESHA_JINDAL;
import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.KUSHAL_DASARI;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.PRASHANTSHARMA;
import static io.harness.rule.OwnerRule.RITEK_ROUNAK;
import static io.harness.rule.OwnerRule.SHALINI;
import static io.harness.rule.OwnerRule.SOUMYAJIT;
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static java.time.LocalDate.now;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.beans.StoreType;
import io.harness.gitsync.interceptor.GitSyncConstants;
import io.harness.gitsync.scm.beans.ScmGitMetaData;
import io.harness.gitsync.sdk.CacheResponse;
import io.harness.gitsync.sdk.CacheState;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.gitsync.sdk.EntityValidityDetails;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.EntityDetail;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStageInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.plan.TriggeredBy;
import io.harness.pms.execution.ExecutionStatus;
import io.harness.pms.pipeline.CacheResponseMetadataDTO;
import io.harness.pms.pipeline.ExecutionSummaryInfo;
import io.harness.pms.pipeline.PMSPipelineResponseDTO;
import io.harness.pms.pipeline.PMSPipelineSummaryResponseDTO;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.PipelineMetadataV2;
import io.harness.pms.pipeline.PipelineValidationResponseDTO;
import io.harness.pms.pipeline.RecentExecutionInfo;
import io.harness.pms.pipeline.RecentExecutionInfoDTO;
import io.harness.pms.pipeline.api.dto.PipelineRequestInfoDTO;
import io.harness.pms.pipeline.mappers.dto.PMSPipelineDtoMapper;
import io.harness.pms.pipeline.validation.async.beans.PipelineValidationEvent;
import io.harness.pms.pipeline.validation.async.beans.ValidationResult;
import io.harness.pms.pipeline.validation.async.beans.ValidationStatus;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.rule.Owner;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PMSPipelineDtoMapperTest extends CategoryTest {
  String yaml = "yaml";
  String identifier = "identifier";

  Integer getYamlHash(String yaml) {
    return Hashing.murmur3_32_fixed().hashString(yaml, StandardCharsets.UTF_8).asInt();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToEntity() {
    String acc = "acc";
    String org = "org1";
    String proj = "proj1";
    String yaml = "pipeline:\n"
        + "  identifier: p1\n"
        + "  name: p1\n"
        + "  description: desc\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";
    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toPipelineEntity(acc, org, proj, yaml);
    assertThat(pipelineEntity.getYamlHash()).isEqualTo(getYamlHash(yaml));
    assertThat(pipelineEntity.getIdentifier()).isEqualTo("p1");
    assertThat(pipelineEntity.getName()).isEqualTo("p1");
    assertThat(pipelineEntity.getAccountId()).isEqualTo("acc");
    assertThat(pipelineEntity.getOrgIdentifier()).isEqualTo("org1");
    assertThat(pipelineEntity.getProjectIdentifier()).isEqualTo("proj1");
    assertThat(pipelineEntity.getAllowStageExecutions()).isFalse();
    String yamlWithAllowExecutions = yaml + "  allowStageExecutions: true\n";
    assertThat(PMSPipelineDtoMapper.toPipelineEntity(acc, org, proj, yamlWithAllowExecutions).getAllowStageExecutions())
        .isTrue();
    String yamlWithDisallowExecutions = yaml + "  allowStageExecutions: false\n";
    assertThat(
        PMSPipelineDtoMapper.toPipelineEntity(acc, org, proj, yamlWithDisallowExecutions).getAllowStageExecutions())
        .isFalse();

    PipelineEntity pipelineEntity1 = PMSPipelineDtoMapper.toPipelineEntity(acc, yaml);
    assertThat(pipelineEntity1.getIdentifier()).isEqualTo("p1");
    assertThat(pipelineEntity1.getName()).isEqualTo("p1");
    assertThat(pipelineEntity1.getAccountId()).isEqualTo("acc");
    assertThat(pipelineEntity1.getOrgIdentifier()).isEqualTo("org1");
    assertThat(pipelineEntity1.getProjectIdentifier()).isEqualTo("proj1");
    assertThat(pipelineEntity1.getAllowStageExecutions()).isFalse();

    assertThat(PMSPipelineDtoMapper.toPipelineEntity(acc, yamlWithAllowExecutions).getAllowStageExecutions()).isTrue();
    assertThat(PMSPipelineDtoMapper.toPipelineEntity(acc, yamlWithDisallowExecutions).getAllowStageExecutions())
        .isFalse();

    String yamlWithIDAsRuntimeInput = "pipeline:\n"
        + "  identifier: <+input>\n"
        + "  name: p1\n"
        + "  description: desc\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";
    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(null, null, null, yamlWithIDAsRuntimeInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline identifier cannot be runtime input");

    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(null, yamlWithIDAsRuntimeInput))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline identifier cannot be runtime input");
    String notAYaml = "pipeline:\n";
    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(null, null, null, notAYaml))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(null, notAYaml))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetDeploymentsAndErrors() {
    Map<String, Integer> deploymentMap = new HashMap<>();
    Map<String, Integer> numberOfErrorMap = new HashMap<>();
    LocalDate todayDate = now();
    DateTimeFormatter formatters = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    for (int i = 0; i < 10; i++) {
      LocalDate variableDate = todayDate.minusDays(i);
      deploymentMap.put(variableDate.format(formatters), i + 10);
      numberOfErrorMap.put(variableDate.format(formatters), i);
    }
    List<Integer> deploymentList = new ArrayList<>();
    List<Integer> numberOfErrorsList = new ArrayList<>();
    for (int i = 6; i >= 0; i--) {
      LocalDate variableDate = todayDate.minusDays(i);
      deploymentList.add(deploymentMap.get(variableDate.format(formatters)));
      numberOfErrorsList.add(numberOfErrorMap.get(variableDate.format(formatters)));
    }
    PipelineEntity pipelineEntity =
        PipelineEntity.builder()
            .accountId("acc")
            .orgIdentifier("org")
            .projectIdentifier("pro")
            .executionSummaryInfo(
                ExecutionSummaryInfo.builder().deployments(deploymentMap).numOfErrors(numberOfErrorMap).build())
            .build();

    PMSPipelineSummaryResponseDTO pmsPipelineSummaryResponseDTO =
        PMSPipelineDtoMapper.preparePipelineSummary(pipelineEntity, false);

    assertThat(deploymentList).isEqualTo(pmsPipelineSummaryResponseDTO.getExecutionSummaryInfo().getDeployments());
    assertThat(numberOfErrorsList).isEqualTo(pmsPipelineSummaryResponseDTO.getExecutionSummaryInfo().getNumOfErrors());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetEntityGitDetails() {
    PipelineEntity oldNonGitSync = PipelineEntity.builder().build();
    EntityGitDetails entityGitDetails0 = PMSPipelineDtoMapper.getEntityGitDetails(oldNonGitSync);
    assertThat(entityGitDetails0).isEqualTo(EntityGitDetails.builder().build());

    PipelineEntity oldGitSync = PipelineEntity.builder().yamlGitConfigRef("repo").branch("branch1").build();
    EntityGitDetails entityGitDetails1 = PMSPipelineDtoMapper.getEntityGitDetails(oldGitSync);
    assertThat(entityGitDetails1).isNotNull();
    assertThat(entityGitDetails1.getRepoIdentifier()).isEqualTo("repo");
    assertThat(entityGitDetails1.getBranch()).isEqualTo("branch1");

    PipelineEntity inline = PipelineEntity.builder().storeType(StoreType.INLINE).build();
    EntityGitDetails entityGitDetails2 = PMSPipelineDtoMapper.getEntityGitDetails(inline);
    assertThat(entityGitDetails2).isNull();

    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").build());

    PipelineEntity remote = PipelineEntity.builder().storeType(StoreType.REMOTE).build();
    EntityGitDetails entityGitDetails3 = PMSPipelineDtoMapper.getEntityGitDetails(remote);
    assertThat(entityGitDetails3).isNotNull();
    assertThat(entityGitDetails3.getBranch()).isEqualTo("brName");
    assertThat(entityGitDetails3.getRepoName()).isEqualTo("repoName");
    assertThat(entityGitDetails3.getRepoIdentifier()).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testGetEntityValidityDetails() {
    PipelineEntity oldNonGitSync = PipelineEntity.builder().yaml(yaml).build();
    EntityValidityDetails entityValidityDetails = PMSPipelineDtoMapper.getEntityValidityDetails(oldNonGitSync);
    assertThat(entityValidityDetails.isValid()).isTrue();
    assertThat(entityValidityDetails.getInvalidYaml()).isNull();

    PipelineEntity oldGitSyncValid = PipelineEntity.builder().yaml(yaml).build();
    entityValidityDetails = PMSPipelineDtoMapper.getEntityValidityDetails(oldGitSyncValid);
    assertThat(entityValidityDetails.isValid()).isTrue();
    assertThat(entityValidityDetails.getInvalidYaml()).isNull();

    PipelineEntity oldGitSyncInvalid =
        PipelineEntity.builder().yaml(yaml).yamlGitConfigRef("repo").isEntityInvalid(true).build();
    entityValidityDetails = PMSPipelineDtoMapper.getEntityValidityDetails(oldGitSyncInvalid);
    assertThat(entityValidityDetails.isValid()).isFalse();
    assertThat(entityValidityDetails.getInvalidYaml()).isEqualTo(yaml);

    PipelineEntity inline = PipelineEntity.builder().yaml(yaml).storeType(StoreType.INLINE).build();
    entityValidityDetails = PMSPipelineDtoMapper.getEntityValidityDetails(inline);
    assertThat(entityValidityDetails.isValid()).isTrue();
    assertThat(entityValidityDetails.getInvalidYaml()).isNull();

    PipelineEntity remote = PipelineEntity.builder().yaml(yaml).storeType(StoreType.REMOTE).build();
    entityValidityDetails = PMSPipelineDtoMapper.getEntityValidityDetails(remote);
    assertThat(entityValidityDetails.isValid()).isTrue();
    assertThat(entityValidityDetails.getInvalidYaml()).isNull();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testWritePipelineDto() {
    PipelineEntity oldNonGitSync =
        PipelineEntity.builder().yaml(yaml).filters(Collections.singletonMap("cd", null)).build();
    PMSPipelineResponseDTO pipelineResponseDTO = PMSPipelineDtoMapper.writePipelineDto(oldNonGitSync);
    assertThat(pipelineResponseDTO.getYamlPipeline()).isEqualTo(yaml);
    assertThat(pipelineResponseDTO.getModules()).containsExactly("cd");
    assertThat(pipelineResponseDTO.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());
    assertThat(pipelineResponseDTO.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());

    PipelineEntity oldGitSyncValid = PipelineEntity.builder()
                                         .yaml(yaml)
                                         .filters(Collections.singletonMap("cd", null))
                                         .yamlGitConfigRef("repo")
                                         .branch("br1")
                                         .build();
    pipelineResponseDTO = PMSPipelineDtoMapper.writePipelineDto(oldGitSyncValid);
    assertThat(pipelineResponseDTO.getYamlPipeline()).isEqualTo(yaml);
    assertThat(pipelineResponseDTO.getModules()).containsExactly("cd");
    assertThat(pipelineResponseDTO.getGitDetails())
        .isEqualTo(EntityGitDetails.builder().repoIdentifier("repo").branch("br1").build());
    assertThat(pipelineResponseDTO.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());

    PipelineEntity oldGitSyncInvalid = PipelineEntity.builder()
                                           .yaml(yaml)
                                           .filters(Collections.singletonMap("cd", null))
                                           .isEntityInvalid(true)
                                           .yamlGitConfigRef("repo")
                                           .branch("br1")
                                           .build();
    pipelineResponseDTO = PMSPipelineDtoMapper.writePipelineDto(oldGitSyncInvalid);
    assertThat(pipelineResponseDTO.getYamlPipeline()).isEqualTo(yaml);
    assertThat(pipelineResponseDTO.getModules()).containsExactly("cd");
    assertThat(pipelineResponseDTO.getGitDetails())
        .isEqualTo(EntityGitDetails.builder().repoIdentifier("repo").branch("br1").build());
    assertThat(pipelineResponseDTO.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(false).invalidYaml(yaml).build());

    PipelineEntity inline = PipelineEntity.builder()
                                .yaml(yaml)
                                .filters(Collections.singletonMap("cd", null))
                                .storeType(StoreType.INLINE)
                                .build();
    pipelineResponseDTO = PMSPipelineDtoMapper.writePipelineDto(inline);
    assertThat(pipelineResponseDTO.getYamlPipeline()).isEqualTo(yaml);
    assertThat(pipelineResponseDTO.getModules()).containsExactly("cd");
    assertThat(pipelineResponseDTO.getGitDetails()).isNull();
    assertThat(pipelineResponseDTO.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());
    assertThat(pipelineResponseDTO.getIsInlineHCEntity()).isFalse();

    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").build());

    PipelineEntity inlineHC = PipelineEntity.builder()
                                  .yaml(yaml)
                                  .filters(Collections.singletonMap("cd", null))
                                  .storeType(StoreType.INLINE_HC)
                                  .build();
    pipelineResponseDTO = PMSPipelineDtoMapper.writePipelineDto(inlineHC);
    assertThat(pipelineResponseDTO.getYamlPipeline()).isEqualTo(yaml);
    assertThat(pipelineResponseDTO.getModules()).containsExactly("cd");
    assertThat(pipelineResponseDTO.getGitDetails())
        .isEqualTo(EntityGitDetails.builder().repoName("repoName").branch("brName").build());
    assertThat(pipelineResponseDTO.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());
    assertThat(pipelineResponseDTO.getIsInlineHCEntity()).isTrue();

    PipelineEntity remote = PipelineEntity.builder()
                                .yaml(yaml)
                                .filters(Collections.singletonMap("cd", null))
                                .storeType(StoreType.REMOTE)
                                .build();
    pipelineResponseDTO = PMSPipelineDtoMapper.writePipelineDto(remote);
    assertThat(pipelineResponseDTO.getYamlPipeline()).isEqualTo(yaml);
    assertThat(pipelineResponseDTO.getModules()).containsExactly("cd");
    assertThat(pipelineResponseDTO.getIsInlineHCEntity()).isFalse();
    assertThat(pipelineResponseDTO.getGitDetails())
        .isEqualTo(EntityGitDetails.builder().repoName("repoName").branch("brName").build());
    assertThat(pipelineResponseDTO.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testPreparePipelineSummary() {
    PipelineEntity oldNonGitSync = PipelineEntity.builder()
                                       .name("name")
                                       .identifier(identifier)
                                       .description("desc")
                                       .stageCount(23)
                                       .filters(Collections.singletonMap("cd", null))
                                       .build();
    PMSPipelineSummaryResponseDTO pipelineSummaryResponse =
        PMSPipelineDtoMapper.preparePipelineSummary(oldNonGitSync, false);
    assertThat(pipelineSummaryResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());
    assertThat(pipelineSummaryResponse.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());
    assertThat(pipelineSummaryResponse.getName()).isEqualTo("name");
    assertThat(pipelineSummaryResponse.getIdentifier()).isEqualTo(identifier);
    assertThat(pipelineSummaryResponse.getDescription()).isEqualTo("desc");
    assertThat(pipelineSummaryResponse.getNumOfStages()).isEqualTo(23);
    assertThat(pipelineSummaryResponse.getStoreType()).isNull();
    assertThat(pipelineSummaryResponse.getConnectorRef()).isNull();
    assertThat(pipelineSummaryResponse.getIsInlineHCEntity()).isFalse();

    PipelineEntity oldGitSyncValid = PipelineEntity.builder()
                                         .name("name")
                                         .identifier(identifier)
                                         .description("desc")
                                         .stageCount(23)
                                         .filters(Collections.singletonMap("cd", null))
                                         .yamlGitConfigRef("repo")
                                         .branch("br1")
                                         .build();
    pipelineSummaryResponse = PMSPipelineDtoMapper.preparePipelineSummary(oldGitSyncValid, false);
    assertThat(pipelineSummaryResponse.getGitDetails())
        .isEqualTo(EntityGitDetails.builder().repoIdentifier("repo").branch("br1").build());
    assertThat(pipelineSummaryResponse.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());
    assertThat(pipelineSummaryResponse.getName()).isEqualTo("name");
    assertThat(pipelineSummaryResponse.getIdentifier()).isEqualTo(identifier);
    assertThat(pipelineSummaryResponse.getDescription()).isEqualTo("desc");
    assertThat(pipelineSummaryResponse.getNumOfStages()).isEqualTo(23);
    assertThat(pipelineSummaryResponse.getStoreType()).isNull();
    assertThat(pipelineSummaryResponse.getConnectorRef()).isNull();
    assertThat(pipelineSummaryResponse.getIsInlineHCEntity()).isFalse();

    PipelineEntity oldGitSyncInvalid = PipelineEntity.builder()
                                           .name("name")
                                           .identifier(identifier)
                                           .description("desc")
                                           .yaml(yaml)
                                           .stageCount(23)
                                           .filters(Collections.singletonMap("cd", null))
                                           .yamlGitConfigRef("repo")
                                           .branch("br1")
                                           .isEntityInvalid(true)
                                           .build();
    pipelineSummaryResponse = PMSPipelineDtoMapper.preparePipelineSummary(oldGitSyncInvalid, false);
    assertThat(pipelineSummaryResponse.getGitDetails())
        .isEqualTo(EntityGitDetails.builder().repoIdentifier("repo").branch("br1").build());
    assertThat(pipelineSummaryResponse.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(false).invalidYaml(yaml).build());
    assertThat(pipelineSummaryResponse.getName()).isEqualTo("name");
    assertThat(pipelineSummaryResponse.getIdentifier()).isEqualTo(identifier);
    assertThat(pipelineSummaryResponse.getDescription()).isEqualTo("desc");
    assertThat(pipelineSummaryResponse.getNumOfStages()).isEqualTo(23);
    assertThat(pipelineSummaryResponse.getStoreType()).isNull();
    assertThat(pipelineSummaryResponse.getConnectorRef()).isNull();
    assertThat(pipelineSummaryResponse.getIsInlineHCEntity()).isFalse();

    PipelineEntity inline = PipelineEntity.builder()
                                .name("name")
                                .identifier(identifier)
                                .description("desc")
                                .stageCount(23)
                                .filters(Collections.singletonMap("cd", null))
                                .storeType(StoreType.INLINE)
                                .build();
    pipelineSummaryResponse = PMSPipelineDtoMapper.preparePipelineSummary(inline, false);
    assertThat(pipelineSummaryResponse.getGitDetails()).isNull();
    assertThat(pipelineSummaryResponse.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());
    assertThat(pipelineSummaryResponse.getName()).isEqualTo("name");
    assertThat(pipelineSummaryResponse.getIdentifier()).isEqualTo(identifier);
    assertThat(pipelineSummaryResponse.getDescription()).isEqualTo("desc");
    assertThat(pipelineSummaryResponse.getNumOfStages()).isEqualTo(23);
    assertThat(pipelineSummaryResponse.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(pipelineSummaryResponse.getIsInlineHCEntity()).isFalse();

    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").build());

    PipelineEntity remote = PipelineEntity.builder()
                                .name("name")
                                .identifier(identifier)
                                .description("desc")
                                .stageCount(23)
                                .filters(Collections.singletonMap("cd", null))
                                .storeType(StoreType.REMOTE)
                                .repo("repoName")
                                .connectorRef("conn")
                                .build();
    pipelineSummaryResponse = PMSPipelineDtoMapper.preparePipelineSummary(remote, false);
    assertThat(pipelineSummaryResponse.getGitDetails())
        .isEqualTo(EntityGitDetails.builder().repoName("repoName").branch("brName").build());
    assertThat(pipelineSummaryResponse.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());
    assertThat(pipelineSummaryResponse.getName()).isEqualTo("name");
    assertThat(pipelineSummaryResponse.getIdentifier()).isEqualTo(identifier);
    assertThat(pipelineSummaryResponse.getDescription()).isEqualTo("desc");
    assertThat(pipelineSummaryResponse.getNumOfStages()).isEqualTo(23);
    assertThat(pipelineSummaryResponse.getStoreType()).isEqualTo(StoreType.REMOTE);
    assertThat(pipelineSummaryResponse.getConnectorRef()).isEqualTo("conn");
    assertThat(pipelineSummaryResponse.getIsInlineHCEntity()).isFalse();

    PipelineEntity inlineHcEntity = PipelineEntity.builder()
                                        .name("name")
                                        .identifier(identifier)
                                        .description("desc")
                                        .stageCount(23)
                                        .filters(Collections.singletonMap("cd", null))
                                        .yamlGitConfigRef("repo")
                                        .branch("br1")
                                        .storeType(StoreType.INLINE_HC)
                                        .connectorRef("dummy-connector")
                                        .build();
    pipelineSummaryResponse = PMSPipelineDtoMapper.preparePipelineSummary(inlineHcEntity, false);
    assertThat(pipelineSummaryResponse.getGitDetails())
        .isEqualTo(EntityGitDetails.builder().repoName("repoName").branch("brName").build());
    assertThat(pipelineSummaryResponse.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());
    assertThat(pipelineSummaryResponse.getName()).isEqualTo("name");
    assertThat(pipelineSummaryResponse.getIdentifier()).isEqualTo(identifier);
    assertThat(pipelineSummaryResponse.getDescription()).isEqualTo("desc");
    assertThat(pipelineSummaryResponse.getNumOfStages()).isEqualTo(23);
    assertThat(pipelineSummaryResponse.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(pipelineSummaryResponse.getConnectorRef()).isEqualTo("dummy-connector");
    assertThat(pipelineSummaryResponse.getIsInlineHCEntity()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testPreparePipelineSummaryForListView() {
    PipelineEntity oldNonGitSync = PipelineEntity.builder()
                                       .name("name")
                                       .identifier(identifier)
                                       .description("desc")
                                       .stageCount(23)
                                       .filters(Collections.singletonMap("cd", null))
                                       .build();
    PMSPipelineSummaryResponseDTO pipelineSummaryResponse =
        PMSPipelineDtoMapper.preparePipelineSummaryForListView(oldNonGitSync, Collections.emptyMap());
    assertThat(pipelineSummaryResponse.getGitDetails()).isEqualTo(EntityGitDetails.builder().build());
    assertThat(pipelineSummaryResponse.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());
    assertThat(pipelineSummaryResponse.getName()).isEqualTo("name");
    assertThat(pipelineSummaryResponse.getIdentifier()).isEqualTo(identifier);
    assertThat(pipelineSummaryResponse.getDescription()).isEqualTo("desc");
    assertThat(pipelineSummaryResponse.getNumOfStages()).isEqualTo(23);
    assertThat(pipelineSummaryResponse.getStoreType()).isNull();
    assertThat(pipelineSummaryResponse.getConnectorRef()).isNull();
    assertThat(pipelineSummaryResponse.getYamlVersion()).isEqualTo("0");
    assertThat(pipelineSummaryResponse.getIsInlineHCEntity()).isFalse();

    PipelineEntity oldGitSyncValid = PipelineEntity.builder()
                                         .name("name")
                                         .identifier(identifier)
                                         .description("desc")
                                         .stageCount(23)
                                         .filters(Collections.singletonMap("cd", null))
                                         .yamlGitConfigRef("repo")
                                         .branch("br1")
                                         .build();
    pipelineSummaryResponse =
        PMSPipelineDtoMapper.preparePipelineSummaryForListView(oldGitSyncValid, Collections.emptyMap());
    assertThat(pipelineSummaryResponse.getGitDetails())
        .isEqualTo(EntityGitDetails.builder().repoIdentifier("repo").branch("br1").build());
    assertThat(pipelineSummaryResponse.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());
    assertThat(pipelineSummaryResponse.getName()).isEqualTo("name");
    assertThat(pipelineSummaryResponse.getIdentifier()).isEqualTo(identifier);
    assertThat(pipelineSummaryResponse.getDescription()).isEqualTo("desc");
    assertThat(pipelineSummaryResponse.getNumOfStages()).isEqualTo(23);
    assertThat(pipelineSummaryResponse.getStoreType()).isNull();
    assertThat(pipelineSummaryResponse.getConnectorRef()).isNull();
    assertThat(pipelineSummaryResponse.getYamlVersion()).isEqualTo("0");
    assertThat(pipelineSummaryResponse.getIsInlineHCEntity()).isFalse();

    PipelineEntity oldGitSyncInvalid = PipelineEntity.builder()
                                           .name("name")
                                           .identifier(identifier)
                                           .description("desc")
                                           .yaml(yaml)
                                           .stageCount(23)
                                           .filters(Collections.singletonMap("cd", null))
                                           .yamlGitConfigRef("repo")
                                           .branch("br1")
                                           .harnessVersion("1")
                                           .isEntityInvalid(true)
                                           .build();
    pipelineSummaryResponse =
        PMSPipelineDtoMapper.preparePipelineSummaryForListView(oldGitSyncInvalid, Collections.emptyMap());
    assertThat(pipelineSummaryResponse.getGitDetails())
        .isEqualTo(EntityGitDetails.builder().repoIdentifier("repo").branch("br1").build());
    assertThat(pipelineSummaryResponse.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(false).invalidYaml(yaml).build());
    assertThat(pipelineSummaryResponse.getName()).isEqualTo("name");
    assertThat(pipelineSummaryResponse.getIdentifier()).isEqualTo(identifier);
    assertThat(pipelineSummaryResponse.getDescription()).isEqualTo("desc");
    assertThat(pipelineSummaryResponse.getNumOfStages()).isEqualTo(23);
    assertThat(pipelineSummaryResponse.getStoreType()).isNull();
    assertThat(pipelineSummaryResponse.getConnectorRef()).isNull();
    assertThat(pipelineSummaryResponse.getYamlVersion()).isEqualTo("1");
    assertThat(pipelineSummaryResponse.getIsInlineHCEntity()).isFalse();

    PipelineEntity inline = PipelineEntity.builder()
                                .name("name")
                                .identifier(identifier)
                                .description("desc")
                                .stageCount(23)
                                .filters(Collections.singletonMap("cd", null))
                                .storeType(StoreType.INLINE)
                                .build();
    pipelineSummaryResponse = PMSPipelineDtoMapper.preparePipelineSummaryForListView(inline, Collections.emptyMap());
    assertThat(pipelineSummaryResponse.getGitDetails()).isNull();
    assertThat(pipelineSummaryResponse.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());
    assertThat(pipelineSummaryResponse.getName()).isEqualTo("name");
    assertThat(pipelineSummaryResponse.getIdentifier()).isEqualTo(identifier);
    assertThat(pipelineSummaryResponse.getDescription()).isEqualTo("desc");
    assertThat(pipelineSummaryResponse.getNumOfStages()).isEqualTo(23);
    assertThat(pipelineSummaryResponse.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(pipelineSummaryResponse.getIsInlineHCEntity()).isFalse();

    PipelineEntity remote = PipelineEntity.builder()
                                .name("name")
                                .identifier(identifier)
                                .description("desc")
                                .stageCount(23)
                                .filters(Collections.singletonMap("cd", null))
                                .storeType(StoreType.REMOTE)
                                .repo("repoName")
                                .connectorRef("conn")
                                .build();
    pipelineSummaryResponse = PMSPipelineDtoMapper.preparePipelineSummaryForListView(remote, Collections.emptyMap());
    assertThat(pipelineSummaryResponse.getGitDetails())
        .isEqualTo(EntityGitDetails.builder().repoName("repoName").isHarnessCodeRepo(false).build());
    assertThat(pipelineSummaryResponse.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());
    assertThat(pipelineSummaryResponse.getName()).isEqualTo("name");
    assertThat(pipelineSummaryResponse.getIdentifier()).isEqualTo(identifier);
    assertThat(pipelineSummaryResponse.getDescription()).isEqualTo("desc");
    assertThat(pipelineSummaryResponse.getNumOfStages()).isEqualTo(23);
    assertThat(pipelineSummaryResponse.getStoreType()).isEqualTo(StoreType.REMOTE);
    assertThat(pipelineSummaryResponse.getConnectorRef()).isEqualTo("conn");
    assertThat(pipelineSummaryResponse.getIsInlineHCEntity()).isFalse();

    PipelineEntity inlineHcEntity = PipelineEntity.builder()
                                        .name("name")
                                        .identifier(identifier)
                                        .description("desc")
                                        .stageCount(23)
                                        .filters(Collections.singletonMap("cd", null))
                                        .yamlGitConfigRef("repo")
                                        .storeType(StoreType.INLINE_HC)
                                        .repo("repoName")
                                        .connectorRef(GitSyncConstants.EMPTY)
                                        .build();
    pipelineSummaryResponse =
        PMSPipelineDtoMapper.preparePipelineSummaryForListView(inlineHcEntity, Collections.emptyMap());
    assertThat(pipelineSummaryResponse.getGitDetails())
        .isEqualTo(EntityGitDetails.builder().repoName("repoName").isHarnessCodeRepo(true).build());
    assertThat(pipelineSummaryResponse.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());
    assertThat(pipelineSummaryResponse.getName()).isEqualTo("name");
    assertThat(pipelineSummaryResponse.getIdentifier()).isEqualTo(identifier);
    assertThat(pipelineSummaryResponse.getDescription()).isEqualTo("desc");
    assertThat(pipelineSummaryResponse.getNumOfStages()).isEqualTo(23);
    assertThat(pipelineSummaryResponse.getStoreType()).isEqualTo(StoreType.INLINE);
    assertThat(pipelineSummaryResponse.getConnectorRef()).isEqualTo("");
    assertThat(pipelineSummaryResponse.getIsInlineHCEntity()).isTrue();
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testPrepareRecentExecutionsInfo() {
    assertThat(PMSPipelineDtoMapper.prepareRecentExecutionsInfo(null)).hasSize(0);
    assertThat(PMSPipelineDtoMapper.prepareRecentExecutionsInfo(PipelineMetadataV2.builder().build())).hasSize(0);
    ExecutionTriggerInfo executionTriggerInfo =
        ExecutionTriggerInfo.newBuilder()
            .setTriggerType(TriggerType.MANUAL)
            .setTriggeredBy(TriggeredBy.newBuilder().setIdentifier("id").putExtraInfo("email", "id@e.com").build())
            .build();
    RecentExecutionInfo recentExecutionInfo0 = RecentExecutionInfo.builder()
                                                   .executionTriggerInfo(executionTriggerInfo)
                                                   .planExecutionId("planId0")
                                                   .status(Status.ABORTED)
                                                   .startTs(1L)
                                                   .endTs(3L)
                                                   .runSequence(12)
                                                   .build();
    RecentExecutionInfo recentExecutionInfo1 = RecentExecutionInfo.builder()
                                                   .executionTriggerInfo(executionTriggerInfo)
                                                   .planExecutionId("planId1")
                                                   .status(Status.SUCCEEDED)
                                                   .startTs(2L)
                                                   .endTs(4L)
                                                   .build();
    List<RecentExecutionInfo> recentExecutionInfoList = Arrays.asList(recentExecutionInfo0, recentExecutionInfo1);
    PipelineMetadataV2 pipelineMetadata =
        PipelineMetadataV2.builder().recentExecutionInfoList(recentExecutionInfoList).build();
    List<RecentExecutionInfoDTO> recentExecutionInfoDTOList =
        PMSPipelineDtoMapper.prepareRecentExecutionsInfo(pipelineMetadata);
    assertThat(recentExecutionInfoDTOList).hasSize(2);
    RecentExecutionInfoDTO response0 = recentExecutionInfoDTOList.get(0);
    assertThat(response0.getPlanExecutionId()).isEqualTo("planId0");
    assertThat(response0.getStatus()).isEqualTo(ExecutionStatus.ABORTED);
    assertThat(response0.getStartTs()).isEqualTo(1L);
    assertThat(response0.getEndTs()).isEqualTo(3L);
    assertThat(response0.getRunSequence()).isEqualTo(12);
    assertThat(response0.getExecutorInfo().getTriggerType()).isEqualTo(TriggerType.MANUAL);
    assertThat(response0.getExecutorInfo().getUsername()).isEqualTo("id");
    assertThat(response0.getExecutorInfo().getEmail()).isEqualTo("id@e.com");
    RecentExecutionInfoDTO response1 = recentExecutionInfoDTOList.get(1);
    assertThat(response1.getPlanExecutionId()).isEqualTo("planId1");
    assertThat(response1.getStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(response1.getStartTs()).isEqualTo(2L);
    assertThat(response1.getEndTs()).isEqualTo(4L);
    assertThat(response1.getExecutorInfo().getTriggerType()).isEqualTo(TriggerType.MANUAL);
    assertThat(response1.getExecutorInfo().getUsername()).isEqualTo("id");
    assertThat(response1.getExecutorInfo().getEmail()).isEqualTo("id@e.com");
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testToEntityDetail() {
    PipelineEntity pipelineEntity = PipelineEntity.builder()
                                        .accountId("acc")
                                        .orgIdentifier("org")
                                        .projectIdentifier("proj")
                                        .identifier("id")
                                        .name("name")
                                        .build();
    EntityDetail entityDetail = PMSPipelineDtoMapper.toEntityDetail(pipelineEntity);
    assertThat(entityDetail.getName()).isEqualTo("name");
    assertThat(entityDetail.getType()).isEqualTo(EntityType.PIPELINES);
    EntityReference entityRef = entityDetail.getEntityRef();
    assertThat(entityRef.getAccountIdentifier()).isEqualTo("acc");
    assertThat(entityRef.getOrgIdentifier()).isEqualTo("org");
    assertThat(entityRef.getProjectIdentifier()).isEqualTo("proj");
    assertThat(entityRef.getIdentifier()).isEqualTo("id");
  }

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void testPreparePipelineSummaryForDraft() {
    PipelineEntity inline = PipelineEntity.builder()
                                .name("name")
                                .identifier(identifier)
                                .filters(Collections.singletonMap("cd", null))
                                .storeType(StoreType.INLINE)
                                .build();
    PMSPipelineSummaryResponseDTO pipelineSummaryResponse = PMSPipelineDtoMapper.preparePipelineSummary(inline, false);
    assertThat(pipelineSummaryResponse.getGitDetails()).isNull();
    assertThat(pipelineSummaryResponse.getEntityValidityDetails())
        .isEqualTo(EntityValidityDetails.builder().valid(true).build());
    assertThat(pipelineSummaryResponse.getName()).isEqualTo("name");
    assertThat(pipelineSummaryResponse.getIdentifier()).isEqualTo(identifier);
    assertThat(pipelineSummaryResponse.getIsDraft()).isEqualTo(false);
    assertThat(pipelineSummaryResponse.getStoreType()).isEqualTo(StoreType.INLINE);
  }

  @Test
  @Owner(developers = ADITHYA)
  @Category(UnitTests.class)
  public void testWritePipelineDtoWithCache() {
    CacheResponse cacheResponse = CacheResponse.builder().cacheState(CacheState.VALID_CACHE).build();

    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").cacheResponse(cacheResponse).build());

    PipelineEntity remote = PipelineEntity.builder()
                                .yaml(yaml)
                                .filters(Collections.singletonMap("cd", null))
                                .storeType(StoreType.REMOTE)
                                .build();
    PMSPipelineResponseDTO pipelineResponseDTO = PMSPipelineDtoMapper.writePipelineDto(remote);
    assertThat(pipelineResponseDTO.getCacheResponse().getCacheState()).isEqualTo(CacheState.VALID_CACHE);
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testBuildPipelineValidationResponseDTO() {
    PipelineValidationEvent event = PipelineValidationEvent.builder()
                                        .status(ValidationStatus.IN_PROGRESS)
                                        .result(ValidationResult.builder().build())
                                        .startTs(1L)
                                        .endTs(2L)
                                        .build();
    PipelineValidationResponseDTO responseBody = PMSPipelineDtoMapper.buildPipelineValidationResponseDTO(event);
    assertThat(responseBody.getStatus()).isEqualTo("IN_PROGRESS");
    assertThat(responseBody.getPolicyEval()).isNull();

    event = PipelineValidationEvent.builder()
                .status(ValidationStatus.IN_PROGRESS)
                .result(ValidationResult.builder()
                            .governanceMetadata(GovernanceMetadata.newBuilder().setDeny(false).build())
                            .build())
                .startTs(1L)
                .endTs(null)
                .build();
    responseBody = PMSPipelineDtoMapper.buildPipelineValidationResponseDTO(event);
    assertThat(responseBody.getStatus()).isEqualTo("IN_PROGRESS");
    assertThat(responseBody.getPolicyEval().getDeny()).isFalse();
  }

  @Test
  @Owner(developers = SHALINI)
  @Category(UnitTests.class)
  public void testPrepareRecentExecutionInfo() {
    PipelineStageInfo parentStageInfo = PipelineStageInfo.newBuilder()
                                            .setRunSequence(12)
                                            .setStageNodeId("stageId")
                                            .setExecutionId("execId")
                                            .setIdentifier(identifier)
                                            .setProjectId("project1")
                                            .setOrgId("org1")
                                            .setHasParentPipeline(true)
                                            .build();
    RecentExecutionInfo recentExecutionInfo =
        RecentExecutionInfo.builder()
            .executionTriggerInfo(
                ExecutionTriggerInfo.newBuilder()
                    .setTriggeredBy(TriggeredBy.newBuilder().setUuid("uuid").setIdentifier("id1").build())
                    .setTriggerType(TriggerType.SCHEDULER_CRON)
                    .build())
            .parentStageInfo(parentStageInfo)
            .build();
    RecentExecutionInfoDTO recentExecutionInfoDTO =
        PMSPipelineDtoMapper.prepareRecentExecutionInfo(recentExecutionInfo);
    assertEquals(recentExecutionInfoDTO.getParentStageInfo(), parentStageInfo);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetCacheResponseFromGitContext() {
    CacheResponse cacheResponse = CacheResponse.builder().cacheState(CacheState.VALID_CACHE).build();
    GitAwareContextHelper.updateScmGitMetaData(
        ScmGitMetaData.builder().branchName("brName").repoName("repoName").cacheResponse(cacheResponse).build());

    CacheResponseMetadataDTO cacheResponseMetadataDTO = PMSPipelineDtoMapper.getCacheResponseFromGitContext();
    assertThat(cacheResponseMetadataDTO.getCacheState()).isEqualTo(CacheState.VALID_CACHE);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testToSimplifiedPipelineEntity() {
    String accountId = generateUuid();
    String orgId = generateUuid();
    String projectId = generateUuid();
    String identifier = "valid123";

    assertThatThrownBy(
        () -> PMSPipelineDtoMapper.toSimplifiedPipelineEntity(accountId, orgId, projectId, "", "", "yaml", null, false))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(
        () -> PMSPipelineDtoMapper.toSimplifiedPipelineEntity(accountId, orgId, projectId, "", "", "yaml", null, false))
        .hasMessage("Pipeline identifier cannot be empty");

    assertThatThrownBy(()
                           -> PMSPipelineDtoMapper.toSimplifiedPipelineEntity(
                               accountId, orgId, projectId, "<+input>", "", "yaml", null, false))
        .isInstanceOf(InvalidRequestException.class);

    assertThatThrownBy(()
                           -> PMSPipelineDtoMapper.toSimplifiedPipelineEntity(
                               accountId, orgId, projectId, "<+input>", "pipelineName", "yaml", null, false))
        .isInstanceOf(InvalidRequestException.class);
    assertThatThrownBy(()
                           -> PMSPipelineDtoMapper.toSimplifiedPipelineEntity(
                               accountId, orgId, projectId, "<+input>", "pipelineName", "yaml", null, false))
        .hasMessage("Pipeline identifier cannot be runtime input");

    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.toSimplifiedPipelineEntity(
        accountId, orgId, projectId, identifier, "pipelineName", "yaml", null, false);

    assertThat(pipelineEntity.getIdentifier()).isEqualTo(identifier);
    assertThat(pipelineEntity.getYamlHash()).isEqualTo(getYamlHash("yaml"));
    assertThat(pipelineEntity.getName()).isEqualTo("pipelineName");

    assertThat(pipelineEntity.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(pipelineEntity.getOrgIdentifier()).isEqualTo(orgId);
    assertThat(pipelineEntity.getProjectIdentifier()).isEqualTo(projectId);
    assertThat(pipelineEntity.getHarnessVersion()).isEqualTo(HarnessYamlVersion.V1);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testValidateAndConvertToPipelineEntity() {
    String acc = "acc";
    String org = "org1";
    String proj = "proj1";
    Map<String, String> tags = new HashMap<>();
    tags.put("type", "workflow");
    String yaml = "pipeline:\n  name: name\n  identifier: identifier\n  projectIdentifier: proj1\n  orgIdentifier: "
        + "org1\n  tags:\n    type: workflow\n";

    PipelineRequestInfoDTO requestInfoDTO = PipelineRequestInfoDTO.builder()
                                                .identifier(identifier)
                                                .name("name")
                                                .yaml(yaml)
                                                .description("desc")
                                                .tags(tags)
                                                .build();

    PipelineEntity pipelineEntity = PMSPipelineDtoMapper.validateAndConvertToPipelineEntity(
        requestInfoDTO, acc, org, proj, false, null, false, null, false, null);

    assertNotNull(pipelineEntity);
    assertThat(pipelineEntity.getIdentifier()).isEqualTo(identifier);
    assertThat(pipelineEntity.getYamlHash()).isEqualTo(getYamlHash(yaml));
    assertThat(pipelineEntity.getName()).isEqualTo("name");

    assertThat(pipelineEntity.getAccountIdentifier()).isEqualTo(acc);
    assertThat(pipelineEntity.getOrgIdentifier()).isEqualTo(org);
    assertThat(pipelineEntity.getProjectIdentifier()).isEqualTo(proj);
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testValidateAndConvertToPipelineEntity_ThrowInvalidRequestException() {
    // Where the identifier in request is not equal to the identifier in the yaml passed
    String acc = "acc";
    String org = "org1";
    String proj = "proj1";
    String yaml = "pipeline:\n"
        + "  identifier: p1\n"
        + "  name: name\n"
        + "  description: desc\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";

    PipelineRequestInfoDTO requestInfoDTO = PipelineRequestInfoDTO.builder()
                                                .identifier(identifier)
                                                .name("name")
                                                .yaml(yaml)
                                                .description("desc")
                                                .tags(null)
                                                .build();

    assertThatThrownBy(()
                           -> PMSPipelineDtoMapper.validateAndConvertToPipelineEntity(
                               requestInfoDTO, acc, org, proj, false, null, false, null, false, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(String.format(
            "Expected Pipeline identifier in YAML to be [%s], but was [%s]", requestInfoDTO.getIdentifier(), "p1"));
  }

  @Test
  @Owner(developers = AVEESHA_JINDAL)
  @Category(UnitTests.class)
  public void testValidateAndConvertToPipelineEntity_InvalidException_TagsMismatch() {
    // Where the tags in request is null but the passed yaml has valid tags
    String acc = "acc";
    String org = "org1";
    String proj = "proj1";
    String yaml = "pipeline:\n"
        + "  identifier: identifier\n"
        + "  name: name\n"
        + "  description: desc\n"
        + "  orgIdentifier: org1\n"
        + "  tags:\n type: workflow\n"
        + "  projectIdentifier: proj1\n";

    PipelineRequestInfoDTO requestInfoDTO = PipelineRequestInfoDTO.builder()
                                                .identifier(identifier)
                                                .name("name")
                                                .yaml(yaml)
                                                .description("desc")
                                                .tags(null)
                                                .build();

    assertThatThrownBy(()
                           -> PMSPipelineDtoMapper.validateAndConvertToPipelineEntity(
                               requestInfoDTO, acc, org, proj, false, null, false, null, false, null))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testPipelineIdentifierValidation() {
    String acc = "acc";
    String org = "org1";
    String proj = "proj1";

    // Test empty identifier
    String yamlWithEmptyId = "pipeline:\n"
        + "  identifier: \n"
        + "  name: p1\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";
    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(acc, org, proj, yamlWithEmptyId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline identifier cannot be empty");

    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(acc, yamlWithEmptyId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline identifier cannot be empty");

    // Test identifier starting with number
    String yamlWithNumberStart = "pipeline:\n"
        + "  identifier: 1pipeline\n"
        + "  name: p1\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";
    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(acc, org, proj, yamlWithNumberStart))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Pipeline Identifier must be up to 128 characters, start with a letter, and contain only alphanumeric "
            + "characters, underscores (_), or dollar signs ($). It cannot start with a number or $.");

    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(acc, yamlWithNumberStart))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Pipeline Identifier must be up to 128 characters, start with a letter, and contain only alphanumeric "
            + "characters, underscores (_), or dollar signs ($). It cannot start with a number or $.");

    // Test identifier starting with dollar sign
    String yamlWithDollarStart = "pipeline:\n"
        + "  identifier: $pipeline\n"
        + "  name: p1\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";
    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(acc, org, proj, yamlWithDollarStart))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Pipeline Identifier must be up to 128 characters, start with a letter, and contain only alphanumeric "
            + "characters, underscores (_), or dollar signs ($). It cannot start with a number or $.");

    // Test identifier with special characters
    String yamlWithSpecialChars = "pipeline:\n"
        + "  identifier: pipeline-with-dash\n"
        + "  name: p1\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";
    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(acc, org, proj, yamlWithSpecialChars))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Pipeline Identifier must be up to 128 characters, start with a letter, and contain only alphanumeric "
            + "characters, underscores (_), or dollar signs ($). It cannot start with a number or $.");

    // Test identifier too long (over 128 characters)
    String longIdentifier = "a".repeat(129);
    String yamlWithLongId = "pipeline:\n"
        + "  identifier: " + longIdentifier + "\n"
        + "  name: p1\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";
    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(acc, org, proj, yamlWithLongId))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Pipeline Identifier must be up to 128 characters, start with a letter, and contain only alphanumeric "
            + "characters, underscores (_), or dollar signs ($). It cannot start with a number or $.");

    // Test valid identifiers
    String yamlWithValidId1 = "pipeline:\n"
        + "  identifier: validPipeline123\n"
        + "  name: p1\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";
    PipelineEntity entity1 = PMSPipelineDtoMapper.toPipelineEntity(acc, org, proj, yamlWithValidId1);
    assertThat(entity1.getIdentifier()).isEqualTo("validPipeline123");

    String yamlWithValidId2 = "pipeline:\n"
        + "  identifier: _underscore_start\n"
        + "  name: p1\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";
    PipelineEntity entity2 = PMSPipelineDtoMapper.toPipelineEntity(acc, org, proj, yamlWithValidId2);
    assertThat(entity2.getIdentifier()).isEqualTo("_underscore_start");

    String yamlWithValidId3 = "pipeline:\n"
        + "  identifier: pipeline_with_dollar$\n"
        + "  name: p1\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";
    PipelineEntity entity3 = PMSPipelineDtoMapper.toPipelineEntity(acc, org, proj, yamlWithValidId3);
    assertThat(entity3.getIdentifier()).isEqualTo("pipeline_with_dollar$");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testValidateAndConvertToPipelineEntityIdentifierValidation() {
    String acc = "acc";
    String org = "org1";
    String proj = "proj1";

    // Test identifier starting with number in API flow
    String yamlWithNumberStart = "pipeline:\n"
        + "  identifier: 1pipeline\n"
        + "  name: p1\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";
    PipelineRequestInfoDTO requestInfo =
        PipelineRequestInfoDTO.builder().identifier("1pipeline").name("p1").yaml(yamlWithNumberStart).build();

    assertThatThrownBy(()
                           -> PMSPipelineDtoMapper.validateAndConvertToPipelineEntity(
                               requestInfo, acc, org, proj, false, "v1", false, null, false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Pipeline Identifier must be up to 128 characters, start with a letter, and contain only alphanumeric "
            + "characters, underscores (_), or dollar signs ($). It cannot start with a number or $.");

    // Test identifier with special characters in API flow
    String yamlWithSpecialChars = "pipeline:\n"
        + "  identifier: pipeline-with-dash\n"
        + "  name: p1\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";
    PipelineRequestInfoDTO requestInfo2 =
        PipelineRequestInfoDTO.builder().identifier("pipeline-with-dash").name("p1").yaml(yamlWithSpecialChars).build();

    assertThatThrownBy(()
                           -> PMSPipelineDtoMapper.validateAndConvertToPipelineEntity(
                               requestInfo2, acc, org, proj, false, "v1", false, null, false, false))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(
            "Pipeline Identifier must be up to 128 characters, start with a letter, and contain only alphanumeric "
            + "characters, underscores (_), or dollar signs ($). It cannot start with a number or $.");

    // Test valid identifier in API flow
    String yamlWithValidId = "pipeline:\n"
        + "  identifier: validPipeline123\n"
        + "  name: p1\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";
    PipelineRequestInfoDTO requestInfo3 =
        PipelineRequestInfoDTO.builder().identifier("validPipeline123").name("p1").yaml(yamlWithValidId).build();

    PipelineEntity entity = PMSPipelineDtoMapper.validateAndConvertToPipelineEntity(
        requestInfo3, acc, org, proj, false, "v1", false, null, false, false);
    assertThat(entity.getIdentifier()).isEqualTo("validPipeline123");
  }

  @Test
  @Owner(developers = KUSHAL_DASARI)
  @Category(UnitTests.class)
  public void testToPipelineEntityWithEmptyName() {
    String acc = "acc";
    String org = "org";
    String proj = "proj";

    // Test YAML with missing name field
    String yamlWithoutName = "pipeline:\n"
        + "  identifier: p1\n"
        + "  description: desc\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";

    // Test first toPipelineEntity method
    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(acc, org, proj, yamlWithoutName))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline name cannot be empty");

    // Test second toPipelineEntity method
    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(acc, yamlWithoutName))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline name cannot be empty");

    // Test YAML with empty name field
    String yamlWithEmptyName = "pipeline:\n"
        + "  identifier: p1\n"
        + "  name: \n"
        + "  description: desc\n"
        + "  orgIdentifier: org1\n"
        + "  projectIdentifier: proj1\n";

    // Test first toPipelineEntity method with empty name
    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(acc, org, proj, yamlWithEmptyName))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline name cannot be empty");

    // Test second toPipelineEntity method with empty name
    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(acc, yamlWithEmptyName))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Pipeline name cannot be empty");
  }

  @Test
  @Owner(developers = RITEK_ROUNAK)
  @Category(UnitTests.class)
  public void testToPipelineEntityThrowsClearMessageWhenYamlExceedsSizeLimit() {
    String acc = "accountId";
    String org = "orgId";
    String proj = "projectId";
    StringBuilder largeYamlBuilder = new StringBuilder("pipeline:\n  name: test\n  identifier: test\n  stages:\n");
    String padding = "x".repeat(1024);
    while (largeYamlBuilder.length() <= 3 * 1024 * 1024) {
      largeYamlBuilder.append("    - stage:\n        name: s\n        identifier: s\n        value: \"")
          .append(padding)
          .append("\"\n");
    }
    String largeYaml = largeYamlBuilder.toString();

    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(acc, org, proj, largeYaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Pipeline YAML size exceeds the maximum allowed limit of 3 MB");

    assertThatThrownBy(() -> PMSPipelineDtoMapper.toPipelineEntity(acc, largeYaml))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Pipeline YAML size exceeds the maximum allowed limit of 3 MB");
  }
}
