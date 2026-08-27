/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.workflowlibrary.service;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.request.GitFileRequest;
import io.harness.beans.response.GitFileResponse;
import io.harness.connector.utils.HarnessCodeConnectorUtils;
import io.harness.delegate.beans.connector.HarnessConnectorDTO;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.harness.HarnessApiAccessDTO;
import io.harness.delegate.beans.connector.scm.harness.HarnessApiAccessType;
import io.harness.delegate.beans.connector.scm.harness.HarnessTokenSpecDTO;
import io.harness.encryption.SecretRefData;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.workflowlibrary.config.WorkflowLibraryConfig;
import io.harness.idp.workflowlibrary.entity.WorkflowAdminInput;
import io.harness.idp.workflowlibrary.entity.WorkflowLibraryEntity;
import io.harness.idp.workflowlibrary.entity.WorkflowPipelineSnapshot;
import io.harness.idp.workflowlibrary.repositories.WorkflowLibraryRepository;
import io.harness.impl.scm.ScmGitProviderMapper;
import io.harness.product.ci.scm.proto.FileChange;
import io.harness.product.ci.scm.proto.FindFilesInBranchRequest;
import io.harness.product.ci.scm.proto.FindFilesInBranchResponse;
import io.harness.product.ci.scm.proto.SCMGrpc;
import io.harness.service.ScmServiceClient;
import io.harness.utils.ScmGrpcClientUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class WorkflowLibrarySyncServiceImpl implements WorkflowLibrarySyncService {
  private static final String PIPELINE_REF_PATTERN = "OOTB_PIPELINE_REF:([\\w-]+)";
  private static final Pattern PIPELINE_REF_REGEX = Pattern.compile(PIPELINE_REF_PATTERN);
  private static final String WORKFLOWS_BASE_PATH = "workflows";
  private static final String PIPELINES_BASE_PATH = "pipelines";

  private final WorkflowLibraryRepository repository;
  private final WorkflowLibraryConfig config;
  private final HarnessCodeRepoConfig harnessCodeRepoConfig;
  private final ScmServiceClient scmServiceClient;
  private final SCMGrpc.SCMBlockingStub scmBlockingStub;
  private final HarnessCodeConnectorUtils harnessCodeConnectorUtils;
  private final ScmGitProviderMapper scmGitProviderMapper;

  @Inject
  public WorkflowLibrarySyncServiceImpl(WorkflowLibraryRepository repository,
      @Named("workflowLibraryConfig") WorkflowLibraryConfig config,
      @Named("harnessCodeRepoConfig") HarnessCodeRepoConfig harnessCodeRepoConfig, ScmServiceClient scmServiceClient,
      SCMGrpc.SCMBlockingStub scmBlockingStub, HarnessCodeConnectorUtils harnessCodeConnectorUtils,
      ScmGitProviderMapper scmGitProviderMapper) {
    this.repository = repository;
    this.config = config;
    this.harnessCodeRepoConfig = harnessCodeRepoConfig;
    this.scmServiceClient = scmServiceClient;
    this.scmBlockingStub = scmBlockingStub;
    this.harnessCodeConnectorUtils = harnessCodeConnectorUtils;
    this.scmGitProviderMapper = scmGitProviderMapper;
  }

  @Override
  public void syncFromGitRepository() {
    log.info("Starting workflow library sync from Harness Code repo: {}", config.getRepoIdentifier());
    try {
      HarnessConnectorDTO connector = buildConnector();

      List<String> workflowDirEntries = listDirectory(connector, WORKFLOWS_BASE_PATH);
      String workflowPrefix = WORKFLOWS_BASE_PATH + "/";
      Set<String> workflowIdentifiers =
          workflowDirEntries.stream()
              .filter(name -> !name.contains("."))
              .map(name -> name.startsWith(workflowPrefix) ? name.substring(workflowPrefix.length()) : name)
              .filter(name -> !name.isEmpty())
              .collect(Collectors.toSet());

      log.info("Workflow library sync: discovered workflow identifiers: {}", workflowIdentifiers);

      for (String workflowIdentifier : workflowIdentifiers) {
        syncWorkflow(workflowIdentifier, connector);
      }
      log.info("Workflow library sync completed successfully");
    } catch (Exception e) {
      log.error("Error during workflow library sync", e);
    }
  }

  private void syncWorkflow(String workflowIdentifier, HarnessConnectorDTO connector) {
    try {
      String configPath = WORKFLOWS_BASE_PATH + "/" + workflowIdentifier + "/config.yaml";
      String configContent = fetchFileContent(connector, configPath);

      Yaml yaml = new Yaml();
      Map<String, Object> workflowConfig = yaml.load(configContent);
      if (workflowConfig == null) {
        log.warn("Empty or invalid config.yaml for workflow: {}", workflowIdentifier);
        return;
      }
      String stableVersion = workflowConfig.get("stable") != null ? String.valueOf(workflowConfig.get("stable")) : null;
      if (stableVersion == null) {
        log.warn("No stable version declared for workflow: {}", workflowIdentifier);
        return;
      }

      if (syncVersion(workflowIdentifier, stableVersion, workflowConfig, connector)) {
        repository.deleteByIdentifierAndVersionNot(workflowIdentifier, stableVersion);
      }
    } catch (Exception e) {
      log.error("Failed to sync workflow: {}", workflowIdentifier, e);
    }
  }

  @SuppressWarnings("unchecked")
  private boolean syncVersion(
      String workflowIdentifier, String version, Map<String, Object> workflowConfig, HarnessConnectorDTO connector) {
    try {
      String basePath = WORKFLOWS_BASE_PATH + "/" + workflowIdentifier + "/" + version;

      String workflowYaml = fetchFileContent(connector, basePath + "/workflow.yaml");

      List<WorkflowPipelineSnapshot> pipelines = resolvePipelines(workflowYaml, connector);
      List<WorkflowAdminInput> adminInputs = buildAggregatedAdminInputs(pipelines, workflowConfig);

      List<String> tags =
          workflowConfig.get("tags") != null ? (List<String>) workflowConfig.get("tags") : new ArrayList<>();
      String status = resolveStatus((String) workflowConfig.get("status"));

      WorkflowLibraryEntity existing = repository.findByIdentifierAndVersion(workflowIdentifier, version);

      WorkflowLibraryEntity entity = WorkflowLibraryEntity.builder()
                                         .id(existing != null ? existing.getId() : null)
                                         .identifier(workflowIdentifier)
                                         .version(version)
                                         .isStable(true)
                                         .deprecated(false)
                                         .name((String) workflowConfig.get("name"))
                                         .description((String) workflowConfig.get("description"))
                                         .longDescription((String) workflowConfig.get("longDescription"))
                                         .category((String) workflowConfig.get("category"))
                                         .icon((String) workflowConfig.get("icon"))
                                         .tags(tags)
                                         .status(status)
                                         .adminInputs(adminInputs)
                                         .workflowYaml(workflowYaml)
                                         .pipelines(pipelines)
                                         .syncedAt(System.currentTimeMillis())
                                         .build();

      repository.save(entity);
      log.info("Synced workflow: {} version: {}", workflowIdentifier, version);
      return true;
    } catch (Exception e) {
      log.error("Failed to sync workflow {} version {}", workflowIdentifier, version, e);
      return false;
    }
  }

  @SuppressWarnings("unchecked")
  private List<WorkflowPipelineSnapshot> resolvePipelines(String workflowYaml, HarnessConnectorDTO connector) {
    List<WorkflowPipelineSnapshot> pipelines = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    Matcher matcher = PIPELINE_REF_REGEX.matcher(workflowYaml);

    while (matcher.find()) {
      String pipelineId = matcher.group(1);
      if (!seen.add(pipelineId)) {
        continue;
      }

      try {
        String pipelineConfigPath = PIPELINES_BASE_PATH + "/" + pipelineId + "/config.yaml";
        String pipelineConfigContent = fetchFileContent(connector, pipelineConfigPath);
        Yaml yaml = new Yaml();
        Map<String, Object> pipelineConfig = yaml.load(pipelineConfigContent);

        String pipelineVersion = pipelineConfig != null && pipelineConfig.get("stable") != null
            ? String.valueOf(pipelineConfig.get("stable"))
            : null;
        if (pipelineVersion == null) {
          log.warn("No stable version found for pipeline: {}", pipelineId);
          continue;
        }

        String pipelineYamlPath = PIPELINES_BASE_PATH + "/" + pipelineId + "/" + pipelineVersion + "/pipeline.yaml";
        String pipelineYaml = fetchFileContent(connector, pipelineYamlPath);

        String resolvedIdentifier = resolveIdentifierFromPipelineYaml(pipelineYaml, pipelineId);
        List<WorkflowAdminInput> pipelineAdminInputs = parseAdminInputs(pipelineConfig, "adminInputs");
        String name = pipelineConfig.get("name") != null ? (String) pipelineConfig.get("name") : pipelineId;

        pipelines.add(WorkflowPipelineSnapshot.builder()
                          .identifier(resolvedIdentifier)
                          .symbolicRef(pipelineId)
                          .name(name)
                          .pipelineYaml(pipelineYaml)
                          .adminInputs(pipelineAdminInputs)
                          .build());
      } catch (Exception e) {
        log.error("Failed to resolve pipeline: {}", pipelineId, e);
      }
    }

    return pipelines;
  }

  @SuppressWarnings("unchecked")
  private String resolveIdentifierFromPipelineYaml(String pipelineYaml, String fallback) {
    try {
      Yaml yaml = new Yaml();
      Map<String, Object> doc = yaml.load(pipelineYaml);
      if (doc != null && doc.containsKey("pipeline")) {
        Map<String, Object> pipeline = (Map<String, Object>) doc.get("pipeline");
        if (pipeline != null && pipeline.containsKey("identifier")) {
          return (String) pipeline.get("identifier");
        }
      }
    } catch (Exception e) {
      log.warn("Could not parse pipeline YAML identifier, using fallback: {}", fallback);
    }
    return fallback;
  }

  @SuppressWarnings("unchecked")
  private List<WorkflowAdminInput> parseAdminInputs(Map<String, Object> config, String key) {
    List<WorkflowAdminInput> adminInputs = new ArrayList<>();
    Object adminInputsObj = config.get(key);
    if (adminInputsObj instanceof List) {
      List<Map<String, Object>> inputsList = (List<Map<String, Object>>) adminInputsObj;
      for (Map<String, Object> input : inputsList) {
        adminInputs.add(
            WorkflowAdminInput.builder()
                .key((String) input.get("key"))
                .type((String) input.get("type"))
                .connectorTypes(input.get("connectorTypes") != null ? (List<String>) input.get("connectorTypes") : null)
                .label((String) input.get("label"))
                .hint((String) input.get("hint"))
                .required(Boolean.TRUE.equals(input.get("required")))
                .defaultValue((String) input.get("defaultValue"))
                .options(input.get("options") != null ? (List<String>) input.get("options") : null)
                .targets(input.get("targets") != null ? (List<String>) input.get("targets") : null)
                .build());
      }
    }
    return adminInputs;
  }

  private List<WorkflowAdminInput> buildAggregatedAdminInputs(
      List<WorkflowPipelineSnapshot> pipelines, Map<String, Object> workflowConfig) {
    List<WorkflowAdminInput> aggregated = new ArrayList<>();
    Set<String> seenKeys = new HashSet<>();

    for (WorkflowPipelineSnapshot pipeline : pipelines) {
      if (pipeline.getAdminInputs() == null) {
        continue;
      }
      String ref = pipeline.getSymbolicRef() != null ? pipeline.getSymbolicRef() : pipeline.getIdentifier();
      for (WorkflowAdminInput input : pipeline.getAdminInputs()) {
        if (seenKeys.add(input.getKey())) {
          aggregated.add(WorkflowAdminInput.builder()
                             .key(input.getKey())
                             .type(input.getType())
                             .connectorTypes(input.getConnectorTypes())
                             .label(input.getLabel())
                             .hint(input.getHint())
                             .required(input.isRequired())
                             .defaultValue(input.getDefaultValue())
                             .options(input.getOptions())
                             .targets(input.getTargets())
                             .pipelineRef(ref)
                             .build());
        }
      }
    }

    List<WorkflowAdminInput> workflowInputs = parseAdminInputs(workflowConfig, "adminInputs");
    for (WorkflowAdminInput input : workflowInputs) {
      if (seenKeys.add(input.getKey())) {
        aggregated.add(input);
      }
    }

    return aggregated;
  }

  private static final Set<String> VALID_STATUSES =
      Set.of(WorkflowLibraryEntity.STATUS_GA, WorkflowLibraryEntity.STATUS_PREVIEW);

  private String resolveStatus(String raw) {
    if (raw == null) {
      return WorkflowLibraryEntity.STATUS_PREVIEW;
    }
    if (VALID_STATUSES.contains(raw)) {
      return raw;
    }
    log.warn("Unrecognized workflow library status '{}', defaulting to preview", raw);
    return WorkflowLibraryEntity.STATUS_PREVIEW;
  }

  private String fetchFileContent(HarnessConnectorDTO connector, String filePath) {
    GitFileRequest request =
        GitFileRequest.builder().branch(config.getBranch()).filepath(filePath).getOnlyFileContent(true).build();
    GitFileResponse response = scmServiceClient.getFile(connector, request, scmBlockingStub);
    if (response.getStatusCode() >= 300) {
      throw new RuntimeException(String.format(
          "Failed to fetch file %s: %s (status: %d)", filePath, response.getError(), response.getStatusCode()));
    }
    return response.getContent();
  }

  private List<String> listDirectory(HarnessConnectorDTO connector, String path) {
    FindFilesInBranchRequest request = FindFilesInBranchRequest.newBuilder()
                                           .setSlug(connector.getSlug())
                                           .setBranch(config.getBranch())
                                           .setPath(path)
                                           .setProvider(scmGitProviderMapper.mapToSCMGitProvider(connector))
                                           .build();
    FindFilesInBranchResponse response =
        ScmGrpcClientUtils.retryAndProcessException(scmBlockingStub::findFilesInBranch, request);
    return response.getFileList().stream().map(FileChange::getPath).collect(Collectors.toList());
  }

  private HarnessConnectorDTO buildConnector() {
    String token = config.getServiceAccountToken();
    if (token != null && !token.isEmpty()) {
      log.info("Using service account token for workflow library sync");
      return buildConnectorWithToken(token);
    }
    String pat = System.getenv("WORKFLOW_LIBRARY_PAT");
    if (pat != null && !pat.isEmpty()) {
      log.info("Using PAT for workflow library sync (devspace)");
      return buildConnectorWithToken(pat);
    }
    log.info("Using JWT-based auth for workflow library sync (same-cluster only)");
    return harnessCodeConnectorUtils.getDummyHarnessCodeConnectorWithJwtAuth(config.getAccountIdentifier(),
        config.getOrgIdentifier(), config.getProjectIdentifier(), config.getRepoIdentifier(),
        harnessCodeRepoConfig.getServiceClientSharedSecret(), config.getApiUrl(), config.getGitBaseUrl(), null);
  }

  private HarnessConnectorDTO buildConnectorWithToken(String token) {
    SecretRefData tokenRef = SecretRefData.builder().decryptedValue(token.toCharArray()).build();
    HarnessTokenSpecDTO tokenSpec = HarnessTokenSpecDTO.builder().tokenRef(tokenRef).build();
    String slug = String.join("/", config.getAccountIdentifier(), config.getOrgIdentifier(),
                      config.getProjectIdentifier(), config.getRepoIdentifier())
        + "/+";
    return HarnessConnectorDTO.builder()
        .connectionType(GitConnectionType.REPO)
        .apiAccess(HarnessApiAccessDTO.builder().spec(tokenSpec).type(HarnessApiAccessType.TOKEN).build())
        .executeOnDelegate(false)
        .apiUrl(config.getApiUrl())
        .gitBaseUrl(config.getGitBaseUrl())
        .accountId(config.getAccountIdentifier())
        .orgId(config.getOrgIdentifier())
        .projectId(config.getProjectIdentifier())
        .slug(slug)
        .build();
  }
}
