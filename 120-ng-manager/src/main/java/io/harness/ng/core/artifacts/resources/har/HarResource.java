/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.artifacts.resources.har;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.PageUtils.getNGPageResponse;

import io.harness.NGCommonEntityConstants;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.harnessartifact.HarnessArtifactRegistryConfig;
import io.harness.cdng.artifact.bean.yaml.harnessartifact.HarnessArtifactRegistryHelper;
import io.harness.cdng.artifact.bean.yaml.harnessartifact.HarnessRegistryConstants;
import io.harness.cdng.artifact.bean.yaml.harnessartifact.HarnessRegistryDockerConfig;
import io.harness.cdng.artifact.bean.yaml.harnessartifact.HarnessRegistryGenericConfig;
import io.harness.cdng.artifact.bean.yaml.harnessartifact.HarnessRegistryRawConfig;
import io.harness.evaluators.CDYamlExpressionEvaluator;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.interceptor.GitEntityFindInfoDTO;
import io.harness.ng.beans.PageResponse;
import io.harness.ng.core.artifacts.resources.util.ArtifactResourceUtils;
import io.harness.ng.core.artifacts.resources.util.YamlExpressionEvaluatorWithContext;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.utils.ScopeResolutionHelper;

import com.codahale.metrics.annotation.ResponseMetered;
import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;

@OwnedBy(CDC)
@Api("har")
@Path("/har")
@Produces({"application/json", "application/yaml"})
@Consumes({"application/json", "application/yaml"})
@ApiResponses(value =
    {
      @ApiResponse(code = 400, response = FailureDTO.class, message = "Bad Request")
      , @ApiResponse(code = 500, response = ErrorDTO.class, message = "Internal server error")
    })
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Slf4j
public class HarResource {
  private final ArtifactResourceUtils artifactResourceUtils;
  @Inject private HarnessArtifactRegistryHelper harnessArtifactRegistryHelper;
  @Inject private ScopeResolutionHelper scopeResolutionHelper;
  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());
  private static final String REGISTRY_KEY = "registry";
  private static final String ARTIFACT_KEY = "artifact";
  private static final String VERSION_KEY = "version";
  private static final String REGISTRY_TYPE_KEY = "registryType";

  @POST
  @Path("artifacts")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get list of artifacts from har registry", nickname = "getHarArtifacts")
  public ResponseDTO<PageResponse<String>> getArtifacts(@QueryParam("registry") String registry,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      String runtimeInputYaml, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    Map<String, String> fieldsMap = new HashMap<>();
    fieldsMap.put(REGISTRY_KEY, registry);
    Map<String, String> resolvedFieldsMap = getResolvedFields(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, fqnPath, gitEntityBasicInfo, runtimeInputYaml, serviceRef, fieldsMap);

    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier);

    String registryRef =
        harnessArtifactRegistryHelper.addSpaceRefToRegistryIdentifier(scopeInfo, resolvedFieldsMap.get(REGISTRY_KEY));

    Page<String> artifacts = harnessArtifactRegistryHelper.listArtifacts(registryRef, page, size, searchTerm);
    return ResponseDTO.newResponse(getNGPageResponse(artifacts));
  }

  @POST
  @Path("/artifacts/versions")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get list of artifact versions from har registry", nickname = "getHarArtifactVersions")
  public ResponseDTO<PageResponse<String>> getArtifactVersions(@QueryParam("registry") String registry,
      @QueryParam("artifact") String artifact,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      String runtimeInputYaml, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    Map<String, String> fieldsMap = new HashMap<>();
    fieldsMap.put(REGISTRY_KEY, registry);
    fieldsMap.put(ARTIFACT_KEY, artifact);
    Map<String, String> resolvedFieldsMap = getResolvedFields(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, fqnPath, gitEntityBasicInfo, runtimeInputYaml, serviceRef, fieldsMap);
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier);

    String registryRef =
        harnessArtifactRegistryHelper.addSpaceRefToRegistryIdentifier(scopeInfo, resolvedFieldsMap.get(REGISTRY_KEY));

    String resolvedArtifact = resolvedFieldsMap.get(ARTIFACT_KEY);
    if (StringUtils.isBlank(resolvedArtifact)) {
      throw new InvalidRequestException(
          "Unable to resolve artifact name for the given artifact source. Please provide the artifact name.");
    }

    Page<String> artifactVersions = harnessArtifactRegistryHelper.listArtifactVersions(
        registryRef, resolvedArtifact, page, size, searchTerm, accountId);
    return ResponseDTO.newResponse(getNGPageResponse(artifactVersions));
  }

  @POST
  @Path("/artifacts/versions/files")
  @Timed
  @ResponseMetered
  @ApiOperation(value = "Get list of artifact files from har registry", nickname = "getHarArtifactFiles")
  public ResponseDTO<PageResponse<String>> getArtifactFiles(@QueryParam("registry") String registry,
      @QueryParam("artifact") String artifact, @QueryParam("version") String version,
      @NotNull @QueryParam(NGCommonEntityConstants.ACCOUNT_KEY) String accountId,
      @QueryParam(NGCommonEntityConstants.ORG_KEY) String orgIdentifier,
      @QueryParam(NGCommonEntityConstants.PROJECT_KEY) String projectIdentifier,
      @QueryParam(NGCommonEntityConstants.PIPELINE_KEY) String pipelineIdentifier,
      @QueryParam("fqnPath") String fqnPath, @BeanParam GitEntityFindInfoDTO gitEntityBasicInfo,
      String runtimeInputYaml, @QueryParam(NGCommonEntityConstants.SERVICE_KEY) String serviceRef,
      @Parameter(description = NGCommonEntityConstants.PAGE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.PAGE) @DefaultValue("0") int page,
      @Parameter(description = NGCommonEntityConstants.SIZE_PARAM_MESSAGE) @QueryParam(
          NGCommonEntityConstants.SIZE) @DefaultValue("100") int size,
      @Parameter(description = "The word to be searched and included in the list response") @QueryParam(
          NGResourceFilterConstants.SEARCH_TERM_KEY) String searchTerm) {
    Map<String, String> fieldsMap = new HashMap<>();
    fieldsMap.put(REGISTRY_KEY, registry);
    fieldsMap.put(ARTIFACT_KEY, artifact);
    fieldsMap.put(VERSION_KEY, version);
    Map<String, String> resolvedFieldsMap = getResolvedFields(accountId, orgIdentifier, projectIdentifier,
        pipelineIdentifier, fqnPath, gitEntityBasicInfo, runtimeInputYaml, serviceRef, fieldsMap);
    ScopeInfo scopeInfo = scopeResolutionHelper.getScopeInfo(accountId, orgIdentifier, projectIdentifier);

    String registryRef =
        harnessArtifactRegistryHelper.addSpaceRefToRegistryIdentifier(scopeInfo, resolvedFieldsMap.get(REGISTRY_KEY));

    String resolvedRegistryType = resolvedFieldsMap.get(REGISTRY_TYPE_KEY);
    if (HarnessRegistryConstants.RAW.equals(resolvedRegistryType)) {
      Page<String> registryFiles = harnessArtifactRegistryHelper.listRegistryFiles(
          resolvedFieldsMap.get(REGISTRY_KEY), accountId, page, size, searchTerm);
      return ResponseDTO.newResponse(getNGPageResponse(registryFiles));
    }

    String resolvedArtifact = resolvedFieldsMap.get(ARTIFACT_KEY);
    String resolvedVersion = resolvedFieldsMap.get(VERSION_KEY);
    if (StringUtils.isBlank(resolvedArtifact)) {
      throw new InvalidRequestException(
          "Unable to resolve artifact name for the given artifact source. Please provide the artifact name.");
    }
    if (StringUtils.isBlank(resolvedVersion)) {
      throw new InvalidRequestException(
          "Unable to resolve version for the given artifact source. Please provide the version.");
    }

    Page<String> artifactFiles = harnessArtifactRegistryHelper.listArtifactFiles(
        registryRef, resolvedArtifact, resolvedVersion, page, size, searchTerm);
    return ResponseDTO.newResponse(getNGPageResponse(artifactFiles));
  }

  private Map<String, String> getResolvedFields(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String fqnPath, GitEntityFindInfoDTO gitEntityBasicInfo, String runtimeInputYaml,
      String serviceRef, Map<String, String> fieldsMap) {
    YamlExpressionEvaluatorWithContext baseEvaluatorWithContext = null;

    // remote services can be linked with a specific branch, so we parse the YAML in one go and store the context data
    //  has env git branch and service git branch
    if (isNotEmpty(serviceRef)
        && artifactResourceUtils.isRemoteService(accountId, orgIdentifier, projectIdentifier, serviceRef)) {
      baseEvaluatorWithContext = artifactResourceUtils.getYamlExpressionEvaluatorWithContext(accountId, orgIdentifier,
          projectIdentifier, pipelineIdentifier, runtimeInputYaml, fqnPath, gitEntityBasicInfo, serviceRef);
    }
    if (isNotEmpty(serviceRef)) {
      final ArtifactConfig artifactSpecFromService = artifactResourceUtils.locateArtifactInService(accountId,
          orgIdentifier, projectIdentifier, serviceRef, fqnPath,
          baseEvaluatorWithContext == null
              ? null
              : baseEvaluatorWithContext.getContextMap().get(artifactResourceUtils.SERVICE_GIT_BRANCH));

      populateFieldsFromArtifactConfig(artifactSpecFromService, fieldsMap);
    } else if (isNotEmpty(runtimeInputYaml)) {
      resolveFieldsFromRuntimeYaml(
          accountId, orgIdentifier, projectIdentifier, runtimeInputYaml, gitEntityBasicInfo, fieldsMap);
    }

    CDYamlExpressionEvaluator yamlExpressionEvaluator =
        baseEvaluatorWithContext == null ? null : baseEvaluatorWithContext.getYamlExpressionEvaluator();

    for (Map.Entry<String, String> entry : fieldsMap.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      // Getting the resolved value in case of expressions
      String resolvedValue = artifactResourceUtils
                                 .getResolvedFieldValueWithYamlExpressionEvaluator(accountId, orgIdentifier,
                                     projectIdentifier, pipelineIdentifier, runtimeInputYaml, value, fqnPath,
                                     gitEntityBasicInfo, serviceRef, yamlExpressionEvaluator)
                                 .getValue();
      fieldsMap.put(key, resolvedValue);
    }
    return fieldsMap;
  }

  private void populateFieldsFromArtifactConfig(ArtifactConfig artifactConfig, Map<String, String> fieldsMap) {
    if (!(artifactConfig instanceof HarnessArtifactRegistryConfig)) {
      return;
    }
    HarnessArtifactRegistryConfig harnessArtifactRegistryConfig = (HarnessArtifactRegistryConfig) artifactConfig;

    String registryType = harnessArtifactRegistryConfig.getRegistryType() != null
        ? (String) harnessArtifactRegistryConfig.getRegistryType().fetchFinalValue()
        : null;

    if (isNotEmpty(registryType)) {
      fieldsMap.put(REGISTRY_TYPE_KEY, registryType);
    }

    for (Map.Entry<String, String> entry : fieldsMap.entrySet()) {
      String key = entry.getKey();
      String value = entry.getValue();
      if (REGISTRY_KEY.equals(key) && StringUtils.isBlank(value)
          && harnessArtifactRegistryConfig.getRegistryRef() != null) {
        value = (String) harnessArtifactRegistryConfig.getRegistryRef().fetchFinalValue();
      } else if (ARTIFACT_KEY.equals(key) && StringUtils.isBlank(value)
          && harnessArtifactRegistryConfig.getHarnessRegistryConfigSpec() != null) {
        if (HarnessRegistryConstants.DOCKER.equals(registryType)) {
          HarnessRegistryDockerConfig harnessRegistryDockerConfig =
              (HarnessRegistryDockerConfig) harnessArtifactRegistryConfig.getHarnessRegistryConfigSpec();
          value = (String) harnessRegistryDockerConfig.getImagePath().fetchFinalValue();
        } else if (HarnessRegistryConstants.GENERIC.equals(registryType)) {
          HarnessRegistryGenericConfig harnessRegistryGenericConfig =
              (HarnessRegistryGenericConfig) harnessArtifactRegistryConfig.getHarnessRegistryConfigSpec();
          value = (String) harnessRegistryGenericConfig.getArtifact().fetchFinalValue();
        } else if (HarnessRegistryConstants.RAW.equals(registryType)) {
          HarnessRegistryRawConfig harnessRegistryRawConfig =
              (HarnessRegistryRawConfig) harnessArtifactRegistryConfig.getHarnessRegistryConfigSpec();
          value = (String) harnessRegistryRawConfig.getFileName().fetchFinalValue();
        }
      } else if (VERSION_KEY.equals(key) && StringUtils.isBlank(value)
          && harnessArtifactRegistryConfig.getHarnessRegistryConfigSpec() != null) {
        if (HarnessRegistryConstants.GENERIC.equals(registryType)) {
          HarnessRegistryGenericConfig harnessRegistryGenericConfig =
              (HarnessRegistryGenericConfig) harnessArtifactRegistryConfig.getHarnessRegistryConfigSpec();
          value = (String) harnessRegistryGenericConfig.getVersion().fetchFinalValue();
        }
      }
      fieldsMap.put(key, value);
    }
  }

  private void resolveFieldsFromRuntimeYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String runtimeInputYaml, GitEntityFindInfoDTO gitEntityBasicInfo, Map<String, String> fieldsMap) {
    String resolvedYaml = artifactResourceUtils.resolveTemplatesInYaml(
        accountId, orgIdentifier, projectIdentifier, runtimeInputYaml, gitEntityBasicInfo);
    if (isEmpty(resolvedYaml)) {
      return;
    }

    JsonNode rootNode;
    try {
      rootNode = YAML_MAPPER.readTree(resolvedYaml);
    } catch (Exception e) {
      throw new InvalidRequestException("Failed to parse resolved YAML for HAR artifact source", e);
    }
    if (rootNode == null) {
      return;
    }

    JsonNode genericSpec = findHarGenericArtifactSpec(rootNode);
    if (genericSpec == null) {
      return;
    }

    if (fieldsMap.containsKey(ARTIFACT_KEY) && StringUtils.isBlank(fieldsMap.get(ARTIFACT_KEY))) {
      JsonNode artifactNode = genericSpec.has("artifact") ? genericSpec.get("artifact") : genericSpec.get("fileName");
      if (artifactNode != null && artifactNode.isTextual() && StringUtils.isNotBlank(artifactNode.asText())) {
        fieldsMap.put(ARTIFACT_KEY, artifactNode.asText());
      }
    }
    if (fieldsMap.containsKey(VERSION_KEY) && StringUtils.isBlank(fieldsMap.get(VERSION_KEY))) {
      JsonNode versionNode = genericSpec.get("version");
      if (versionNode != null && versionNode.isTextual() && StringUtils.isNotBlank(versionNode.asText())) {
        fieldsMap.put(VERSION_KEY, versionNode.asText());
      }
    }
  }

  private JsonNode findHarGenericArtifactSpec(JsonNode node) {
    if (node == null) {
      return null;
    }
    Deque<JsonNode> stack = new ArrayDeque<>();
    stack.push(node);
    while (!stack.isEmpty()) {
      JsonNode current = stack.pop();
      if (current.isObject()) {
        JsonNode typeNode = current.get("type");
        JsonNode specNode = current.get("spec");
        if (typeNode != null && HarnessRegistryConstants.GENERIC.equals(typeNode.asText()) && specNode != null
            && specNode.isObject() && specNode.has("artifact")) {
          return specNode;
        }
        current.fields().forEachRemaining(e -> stack.push(e.getValue()));
      } else if (current.isArray()) {
        current.forEach(stack::push);
      }
    }
    return null;
  }
}
