/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.HarnessCodeServiceConfig;
import io.harness.beans.IdentifierRef;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.intfc.ScmConnector;
import io.harness.gitsync.beans.GitRepositoryDTO;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.pipeline.branchsequence.BranchSequenceResult;
import io.harness.pms.pipeline.branchsequence.PipelineBranchSequence;
import io.harness.pms.pipeline.branchsequence.RepoUrlNormalizer;
import io.harness.pms.yaml.YamlUtils;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.branchsequence.PipelineBranchSequenceRepository;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CI)
@Singleton
@Slf4j
public class BranchSequenceServiceImpl implements BranchSequenceService {
  private static final String REFS_HEADS_PREFIX = "refs/heads/";
  private static final String REFS_TAGS_PREFIX = "refs/tags/";

  private final PipelineBranchSequenceRepository branchSequenceRepository;
  private final ConnectorResourceClient connectorResourceClient;
  private final HarnessCodeServiceConfig harnessCodeServiceConfig;

  @Inject
  public BranchSequenceServiceImpl(PipelineBranchSequenceRepository branchSequenceRepository,
      ConnectorResourceClient connectorResourceClient, HarnessCodeServiceConfig harnessCodeServiceConfig) {
    this.branchSequenceRepository = branchSequenceRepository;
    this.connectorResourceClient = connectorResourceClient;
    this.harnessCodeServiceConfig = harnessCodeServiceConfig;
  }

  @Override
  public long incrementBranchSequence(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String repoUrl, String branch, @Nullable String parentUniqueId) {
    String normalizedRepoUrl = RepoUrlNormalizer.normalize(repoUrl);
    String normalizedBranch = RepoUrlNormalizer.normalizeBranch(branch);

    if (isEmpty(normalizedRepoUrl) || isEmpty(normalizedBranch)) {
      log.warn("Cannot increment branch sequence: repoUrl={}, branch={}, normalizedRepoUrl={}, normalizedBranch={}",
          repoUrl, branch, normalizedRepoUrl, normalizedBranch);
      return 0;
    }

    PipelineBranchSequence result = branchSequenceRepository.incrementAndGet(accountIdentifier, orgIdentifier,
        projectIdentifier, pipelineIdentifier, normalizedRepoUrl, normalizedBranch, parentUniqueId);

    if (result == null) {
      log.error("Failed to increment branch sequence for pipeline={}, repo={}, branch={}", pipelineIdentifier,
          normalizedRepoUrl, normalizedBranch);
      return 0;
    }

    return result.getSequenceId();
  }

  @Override
  public Optional<Long> getBranchSequence(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String repoUrl, String branch) {
    String normalizedRepoUrl = RepoUrlNormalizer.normalize(repoUrl);
    String normalizedBranch = RepoUrlNormalizer.normalizeBranch(branch);

    if (isEmpty(normalizedRepoUrl) || isEmpty(normalizedBranch)) {
      return Optional.empty();
    }

    return branchSequenceRepository
        .getBranchSequence(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, normalizedRepoUrl,
            normalizedBranch)
        .map(PipelineBranchSequence::getSequenceId);
  }

  @Override
  @Nullable
  public BranchSequenceResult incrementBranchSequenceFromTriggerPayload(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, TriggerPayload triggerPayload,
      @Nullable String parentUniqueId) {
    if (triggerPayload == null || !triggerPayload.hasParsedPayload()) {
      return null;
    }

    String branch = extractBranchFromPayload(triggerPayload.getParsedPayload());
    String repoUrl = extractRepoUrlFromPayload(triggerPayload.getParsedPayload());

    if (isEmpty(repoUrl) && isNotEmpty(triggerPayload.getConnectorRef())) {
      repoUrl = resolveConnectorToRepoUrl(
          triggerPayload.getConnectorRef(), null, accountIdentifier, orgIdentifier, projectIdentifier);
    }

    if (isEmpty(branch) || isEmpty(repoUrl)) {
      log.debug("[BranchSeqId] Missing branch={} or repoUrl={} for pipeline={}", branch, repoUrl, pipelineIdentifier);
      return null;
    }

    String normalizedRepoUrl = RepoUrlNormalizer.normalize(repoUrl);
    String normalizedBranch = RepoUrlNormalizer.normalizeBranch(branch);
    if (isEmpty(normalizedRepoUrl) || isEmpty(normalizedBranch)) {
      return null;
    }

    long sequenceId = incrementBranchSequence(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, repoUrl, branch, parentUniqueId);

    if (sequenceId > 0) {
      log.info("Incremented branchSeqId to {} for pipeline={}, branch={}, repo={}", sequenceId, pipelineIdentifier,
          normalizedBranch, normalizedRepoUrl);

      return BranchSequenceResult.builder()
          .branchSeqId(sequenceId)
          .normalizedBranch(normalizedBranch)
          .normalizedRepoUrl(normalizedRepoUrl)
          .build();
    }

    return null;
  }

  @Override
  public long deleteAllForPipeline(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    return branchSequenceRepository.deleteAllForPipeline(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);
  }

  @Override
  public List<PipelineBranchSequence> getAllForPipeline(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    return branchSequenceRepository.getAllForPipeline(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);
  }

  @Override
  public boolean deleteBranchSequence(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String repoUrl, String branch) {
    String normalizedRepoUrl = RepoUrlNormalizer.normalize(repoUrl);
    String normalizedBranch = RepoUrlNormalizer.normalizeBranch(branch);

    if (isEmpty(normalizedRepoUrl) || isEmpty(normalizedBranch)) {
      log.warn("Cannot delete branch sequence: repoUrl={}, branch={}, normalizedRepoUrl={}, normalizedBranch={}",
          repoUrl, branch, normalizedRepoUrl, normalizedBranch);
      return false;
    }

    return branchSequenceRepository.deleteBranchSequence(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, normalizedRepoUrl, normalizedBranch);
  }

  @Override
  @Nullable
  public PipelineBranchSequence setBranchSequence(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String repoUrl, String branch, long sequenceId) {
    String normalizedRepoUrl = RepoUrlNormalizer.normalize(repoUrl);
    String normalizedBranch = RepoUrlNormalizer.normalizeBranch(branch);

    if (isEmpty(normalizedRepoUrl) || isEmpty(normalizedBranch)) {
      log.warn("Cannot set branch sequence: repoUrl={}, branch={}, normalizedRepoUrl={}, normalizedBranch={}", repoUrl,
          branch, normalizedRepoUrl, normalizedBranch);
      return null;
    }

    return branchSequenceRepository.setSequenceId(accountIdentifier, orgIdentifier, projectIdentifier,
        pipelineIdentifier, normalizedRepoUrl, normalizedBranch, sequenceId);
  }

  @Override
  @Nullable
  public BranchSequenceResult incrementBranchSequenceFromProcessedYaml(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String processedYaml,
      @Nullable TriggerPayload triggerPayload, @Nullable String parentUniqueId) {
    if (isEmpty(processedYaml)) {
      return incrementBranchSequenceFromTriggerPayload(
          accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, triggerPayload, parentUniqueId);
    }

    try {
      CodebaseInfo codebaseInfo = extractCodebaseInfoFromYaml(processedYaml);

      if (codebaseInfo == null || isEmpty(codebaseInfo.branch)) {
        return incrementBranchSequenceFromTriggerPayload(
            accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, triggerPayload, parentUniqueId);
      }

      String repoUrl = extractRepoUrlFromPayload(
          triggerPayload != null && triggerPayload.hasParsedPayload() ? triggerPayload.getParsedPayload() : null);

      if (isEmpty(repoUrl) && triggerPayload != null && isNotEmpty(triggerPayload.getConnectorRef())) {
        repoUrl = resolveConnectorToRepoUrl(
            triggerPayload.getConnectorRef(), null, accountIdentifier, orgIdentifier, projectIdentifier);
      }

      if (isEmpty(repoUrl) && isNotEmpty(codebaseInfo.connectorRef)) {
        repoUrl = resolveConnectorToRepoUrl(
            codebaseInfo.connectorRef, codebaseInfo.repoName, accountIdentifier, orgIdentifier, projectIdentifier);
      }

      // Harness Code repos may not have connectorRef in YAML -- use repoName as stable identifier
      if (isEmpty(repoUrl) && isEmpty(codebaseInfo.connectorRef) && isNotEmpty(codebaseInfo.repoName)) {
        String gitBaseUrl = harnessCodeServiceConfig != null ? harnessCodeServiceConfig.getGitUrl() : null;
        repoUrl = HarnessCodeConnectorUtils.getRepoGitUrl(
            gitBaseUrl, accountIdentifier, orgIdentifier, projectIdentifier, codebaseInfo.repoName);
      }

      if (isEmpty(repoUrl)) {
        return null;
      }

      String normalizedBranch = RepoUrlNormalizer.normalizeBranch(codebaseInfo.branch);
      String normalizedRepoUrl = RepoUrlNormalizer.normalize(repoUrl);
      if (isEmpty(normalizedBranch) || isEmpty(normalizedRepoUrl)) {
        return null;
      }

      PipelineBranchSequence result = branchSequenceRepository.incrementAndGet(accountIdentifier, orgIdentifier,
          projectIdentifier, pipelineIdentifier, normalizedRepoUrl, normalizedBranch, parentUniqueId);

      if (result == null) {
        log.error("Failed to increment branch sequence from YAML for pipeline={}", pipelineIdentifier);
        return null;
      }

      log.info("Incremented branchSeqId to {} for pipeline={}, branch={}, repo={}", result.getSequenceId(),
          pipelineIdentifier, normalizedBranch, normalizedRepoUrl);

      return BranchSequenceResult.builder()
          .branchSeqId(result.getSequenceId())
          .normalizedBranch(normalizedBranch)
          .normalizedRepoUrl(normalizedRepoUrl)
          .build();

    } catch (Exception e) {
      log.warn("Error extracting codebase info from processed YAML for pipeline={}: {}", pipelineIdentifier,
          e.getMessage(), e);
      return null;
    }
  }

  @Nullable
  private String extractBranchFromPayload(ParsedPayload parsedPayload) {
    if (parsedPayload == null) {
      return null;
    }

    switch (parsedPayload.getPayloadCase()) {
      case PR:
        if (parsedPayload.getPr().hasPr()) {
          return parsedPayload.getPr().getPr().getSource();
        }
        break;
      case PUSH:
        String ref = parsedPayload.getPush().getRef();
        if (ref != null && ref.startsWith(REFS_HEADS_PREFIX)) {
          return ref.substring(REFS_HEADS_PREFIX.length());
        } else if (ref != null && !ref.startsWith(REFS_TAGS_PREFIX)) {
          return ref;
        }
        break;
      case BRANCH:
        if (parsedPayload.getBranch().hasRef()) {
          String refName = parsedPayload.getBranch().getRef().getName();
          if (refName != null && refName.startsWith(REFS_HEADS_PREFIX)) {
            return refName.substring(REFS_HEADS_PREFIX.length());
          }
          return refName;
        }
        break;
      default:
        break;
    }
    return null;
  }

  @Nullable
  private String extractRepoUrlFromPayload(ParsedPayload parsedPayload) {
    if (parsedPayload == null) {
      return null;
    }

    String repoUrl = null;
    switch (parsedPayload.getPayloadCase()) {
      case PR:
        if (parsedPayload.getPr().hasRepo()) {
          repoUrl = parsedPayload.getPr().getRepo().getLink();
          if (isEmpty(repoUrl)) {
            repoUrl = parsedPayload.getPr().getRepo().getClone();
          }
        }
        break;
      case PUSH:
        if (parsedPayload.getPush().hasRepo()) {
          repoUrl = parsedPayload.getPush().getRepo().getLink();
          if (isEmpty(repoUrl)) {
            repoUrl = parsedPayload.getPush().getRepo().getClone();
          }
        }
        break;
      case BRANCH:
        if (parsedPayload.getBranch().hasRepo()) {
          repoUrl = parsedPayload.getBranch().getRepo().getLink();
          if (isEmpty(repoUrl)) {
            repoUrl = parsedPayload.getBranch().getRepo().getClone();
          }
        }
        break;
      default:
        break;
    }
    return repoUrl;
  }

  @Nullable
  private CodebaseInfo extractCodebaseInfoFromYaml(String processedYaml) {
    try {
      JsonNode codebaseNode =
          YamlUtils.readAsJsonNode(processedYaml).path("pipeline").path("properties").path("ci").path("codebase");

      if (codebaseNode.isMissingNode()) {
        return null;
      }

      String connectorRef = getTextValue(codebaseNode.path("connectorRef"));
      String repoName = getTextValue(codebaseNode.path("repoName"));
      JsonNode buildNode = codebaseNode.path("build");
      String buildType = getTextValue(buildNode.path("type"));

      String branch = null;
      if ("branch".equalsIgnoreCase(buildType)) {
        branch = getTextValue(buildNode.path("spec").path("branch"));
      }

      if (isEmpty(branch) && isEmpty(connectorRef)) {
        return null;
      }

      return new CodebaseInfo(branch, connectorRef, repoName);

    } catch (Exception e) {
      log.warn("[BranchSeqId] Error parsing processed YAML for codebase info: {}", e.getMessage(), e);
      return null;
    }
  }

  private String getTextValue(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    String value = node.asText();
    if (isEmpty(value) || value.startsWith("<+")) {
      return null;
    }
    return value;
  }

  private static class CodebaseInfo {
    final String branch;
    final String connectorRef;
    final String repoName;

    CodebaseInfo(String branch, String connectorRef, String repoName) {
      this.branch = branch;
      this.connectorRef = connectorRef;
      this.repoName = repoName;
    }
  }

  @Nullable
  private String resolveConnectorToRepoUrl(
      String connectorRef, String repoName, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    try {
      IdentifierRef identifierRef =
          IdentifierRefHelper.getIdentifierRef(connectorRef, accountIdentifier, orgIdentifier, projectIdentifier);
      if (identifierRef == null || isEmpty(identifierRef.getIdentifier())) {
        return null;
      }

      Optional<ConnectorDTO> connectorDTOOptional = NGRestUtils.getResponse(
          connectorResourceClient.get(identifierRef.getIdentifier(), identifierRef.getAccountIdentifier(),
              identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier()));
      if (connectorDTOOptional.isEmpty()) {
        return null;
      }

      ConnectorConfigDTO configDTO = connectorDTOOptional.get().getConnectorInfo().getConnectorConfig();
      if (!(configDTO instanceof ScmConnector)) {
        return null;
      }

      ScmConnector scmConnector = (ScmConnector) configDTO;
      GitConnectionType connectionType = scmConnector.getConnectionTypeForGit();

      if (connectionType == GitConnectionType.REPO) {
        return scmConnector.getUrl();
      }

      if (connectionType == GitConnectionType.ACCOUNT && isNotEmpty(repoName)) {
        return scmConnector.getGitConnectionUrl(GitRepositoryDTO.builder().name(repoName).build());
      }

      String baseUrl = scmConnector.getUrl();
      if (isNotEmpty(baseUrl) && isNotEmpty(repoName)) {
        return baseUrl.endsWith("/") ? baseUrl + repoName : baseUrl + "/" + repoName;
      }

      return null;
    } catch (Exception e) {
      log.warn("[BranchSeqId] Failed to resolve connector {}: {}", connectorRef, e.getMessage());
      return null;
    }
  }
}
