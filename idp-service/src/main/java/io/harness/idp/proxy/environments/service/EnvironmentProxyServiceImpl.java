/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.proxy.environments.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.readFileFromClassPath;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.clients.POServerClientUtils;
import io.harness.governance.GovernanceMetadata;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.helpers.CatalogServiceHelper;
import io.harness.idp.catalog.service.CatalogService;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.spec.server.idp.v1.model.EntityCreateRequest;
import io.harness.spec.server.idp.v1.model.EntityResponse;
import io.harness.spec.server.idp.v1.model.EntityUpdateRequest;
import io.harness.spec.server.idp.v1.model.EnvironmentProxyCreateRequest;
import io.harness.spec.server.idp.v1.model.EnvironmentProxyResponse;
import io.harness.spec.server.idp.v1.model.EnvironmentProxyUpdateRequest;
import io.harness.springdata.TransactionHelper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.inject.Inject;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

@OwnedBy(HarnessTeam.IDP)
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@Slf4j
public class EnvironmentProxyServiceImpl implements EnvironmentProxyService {
  @Inject POServerClientUtils poserverClientUtils;

  @Inject CatalogService catalogService;
  @Inject CatalogServiceHelper catalogServiceHelper;
  @Inject TransactionHelper transactionHelper;

  private static final String ENVIRONMENT_YAML = "environment.yaml";

  // Shared YAML mapper, built once and never mutated (thread-safe for read/write).
  // WRITE_DOC_START_MARKER off suppresses the leading '---'. No modules needed: this class only
  // round-trips Map<String, Object> parsed from YAML, so type-specific (de)serializers never apply.
  private static final ObjectMapper YAML_MAPPER =
      new ObjectMapper(new YAMLFactory().configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false));

  @Override
  public EnvironmentProxyResponse createCompileAndExecuteEnvironment(EnvironmentProxyCreateRequest body,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Boolean dryRun) {
    String environmentYaml;
    try {
      environmentYaml = getEnvironmentYaml(body, orgIdentifier, projectIdentifier);
    } catch (Exception e) {
      throw new RuntimeException(
          String.format("Failed to construct the environment yaml in account - %s ", accountIdentifier), e);
    }

    log.info("Environment yaml: {}", environmentYaml);

    boolean isDryRun = Boolean.TRUE.equals(dryRun);
    EnvironmentProxyResponse proxyResponse = new EnvironmentProxyResponse();

    if (isDryRun) {
      Object compileResponse =
          poserverClientUtils.compile(environmentYaml, accountIdentifier, orgIdentifier, projectIdentifier);
      proxyResponse.setResponse(compileResponse);
      return proxyResponse;
    } else {
      Object compileResponse =
          poserverClientUtils.compile(environmentYaml, accountIdentifier, orgIdentifier, projectIdentifier);

      EntityCreateRequest entityCreateRequest = new EntityCreateRequest();
      entityCreateRequest.setYaml(environmentYaml);
      EntityResponse entityResponse = catalogService.createEntity(
          accountIdentifier, orgIdentifier, projectIdentifier, false, false, entityCreateRequest);

      Object governanceMetadata = entityResponse.getGovernanceMetadata();
      if (governanceMetadata instanceof GovernanceMetadata && ((GovernanceMetadata) governanceMetadata).getDeny()) {
        proxyResponse.setGovernanceMetadata(governanceMetadata);
        return proxyResponse;
      }

      Object executeResponse =
          poserverClientUtils.execute(compileResponse, accountIdentifier, orgIdentifier, projectIdentifier);
      proxyResponse.setResponse(executeResponse);
      if (governanceMetadata != null) {
        proxyResponse.setGovernanceMetadata(governanceMetadata);
      }
      return proxyResponse;
    }
  }

  @Override
  public EnvironmentProxyResponse updateCompileAndExecuteEnvironment(String environmentId,
      EnvironmentProxyUpdateRequest body, String accountIdentifier, String orgIdentifier, String projectIdentifier)
      throws Exception {
    CatalogEntity catalogEntity = catalogService.getCatalogEntityByParentUniqueIdAndKindAndIdentifier(
        accountIdentifier, orgIdentifier, projectIdentifier, "environment", environmentId);

    String environmentYaml = catalogEntity.getYaml();

    Map<String, Object> root = YAML_MAPPER.readValue(environmentYaml, new TypeReference<>() {});

    Map<String, Object> spec = (Map<String, Object>) root.get("spec");
    if (spec == null) {
      spec = new LinkedHashMap<>();
      root.put("spec", spec);
    }

    if (body.getEnvironmentBlueprintVersion() != null && !body.getEnvironmentBlueprintVersion().isEmpty()) {
      Map<String, Object> envBlueprint = (Map<String, Object>) spec.get("environmentBlueprint");
      envBlueprint.put("version", body.getEnvironmentBlueprintVersion());
    }

    if (!isEmpty(body.getBasedOnIdentifier())) {
      Map<String, Object> basedOn = parseBasedOnIdentifier(body.getBasedOnIdentifier());
      spec.put("basedOn", basedOn);
    }

    if (body.getOverrides() != null && !body.getOverrides().isEmpty()) {
      String sanitizedConfig = body.getOverrides().replace("\t", "  ");
      Map<String, Object> configMap =
          YAML_MAPPER.readValue(sanitizedConfig, new TypeReference<Map<String, Object>>() {});
      spec.put("overrides", configMap);
    }

    if (!isEmpty(body.getTargetState())) {
      Map<String, Object> targetStateMap = new LinkedHashMap<>();
      targetStateMap.put("state", body.getTargetState());
      spec.put("targetState", targetStateMap);
    }

    if (body.getInputs() != null && !body.getInputs().isEmpty()) {
      String sanitizedInputs = body.getInputs().replace("\t", "  ");
      Map<String, Object> inputsMap =
          YAML_MAPPER.readValue(sanitizedInputs, new TypeReference<Map<String, Object>>() {});
      spec.put("inputs", inputsMap);
    }

    String updatedYaml = YAML_MAPPER.writeValueAsString(root);

    log.info("Environment yaml: {}", updatedYaml);

    EnvironmentProxyResponse proxyResponse = new EnvironmentProxyResponse();

    // Update always follows: Compile → Update Entity → Execute
    Object compileResponse =
        poserverClientUtils.compile(updatedYaml, accountIdentifier, orgIdentifier, projectIdentifier);

    EntityUpdateRequest entityUpdateRequest = new EntityUpdateRequest();
    entityUpdateRequest.setYaml(updatedYaml);
    EntityResponse entityResponse = catalogService.updateEntity(accountIdentifier, orgIdentifier, projectIdentifier,
        CatalogUtils.entityRef(catalogEntity), entityUpdateRequest, true, false, false, false);

    Object governanceMetadata = entityResponse.getGovernanceMetadata();
    if (governanceMetadata instanceof GovernanceMetadata && ((GovernanceMetadata) governanceMetadata).getDeny()) {
      proxyResponse.setGovernanceMetadata(governanceMetadata);
      return proxyResponse;
    }

    Object executeResponse =
        poserverClientUtils.execute(compileResponse, accountIdentifier, orgIdentifier, projectIdentifier);
    proxyResponse.setResponse(executeResponse);
    if (governanceMetadata != null) {
      proxyResponse.setGovernanceMetadata(governanceMetadata);
    }
    return proxyResponse;
  }

  @Override
  public void deleteEnvironment(
      String environmentId, String accountIdentifier, String orgIdentifier, String projectIdentifier) {
    poserverClientUtils.deleteEnvironment(environmentId, accountIdentifier, orgIdentifier, projectIdentifier);
    catalogService.deleteEntity(accountIdentifier, orgIdentifier, projectIdentifier,
        "environment:" + String.format("account.%s.%s", orgIdentifier, projectIdentifier) + "/" + environmentId, false);
  }

  private String getEnvironmentYaml(EnvironmentProxyCreateRequest body, String orgIdentifier, String projectIdentifier)
      throws Exception {
    Map<String, Object> root =
        YAML_MAPPER.readValue(readFileFromClassPath(ENVIRONMENT_YAML), new TypeReference<Map<String, Object>>() {});

    root.put("identifier", body.getEnvironmentIdentifier());
    root.put("name", body.getEnvironmentName());
    root.put("owner", body.getOwner());
    root.put("scope", String.format("account.%s.%s", orgIdentifier, projectIdentifier));
    root.put("orgIdentifier", orgIdentifier);
    root.put("projectIdentifier", projectIdentifier);

    Map<String, Object> metadata = new LinkedHashMap<>();
    if (body.getTags() != null && !body.getTags().isEmpty()) {
      metadata.put("tags", body.getTags());
    }
    if (body.getDescription() != null && !body.getDescription().isEmpty()) {
      metadata.put("description", body.getDescription());
    }
    if (!metadata.isEmpty()) {
      root.put("metadata", metadata);
    }

    Map<String, Object> spec = (Map<String, Object>) root.get("spec");
    Map<String, Object> envBlueprint = (Map<String, Object>) spec.get("environmentBlueprint");
    envBlueprint.put("identifier", body.getEnvironmentBlueprintIdentifier());
    envBlueprint.put("version", body.getEnvironmentBlueprintVersion());

    if (body.getBasedOnIdentifier() != null && !body.getBasedOnIdentifier().isEmpty()) {
      Map<String, Object> basedOn = parseBasedOnIdentifier(body.getBasedOnIdentifier());
      spec.put("basedOn", basedOn);
    } else {
      spec.remove("basedOn");
    }

    if (body.getOverrides() != null && !body.getOverrides().isEmpty()) {
      String sanitizedConfig = body.getOverrides().replace("\t", "  ");
      Map<String, Object> configMap =
          YAML_MAPPER.readValue(sanitizedConfig, new TypeReference<Map<String, Object>>() {});

      spec.put("overrides", configMap);
    } else {
      Map<String, Object> defaultOverrides = new LinkedHashMap<>();
      defaultOverrides.put("config", new LinkedHashMap<>());
      defaultOverrides.put("entities", new LinkedHashMap<>());
      spec.put("overrides", defaultOverrides);
    }

    Map<String, Object> targetState = (Map<String, Object>) spec.get("targetState");
    String state = body.getTargetState() != null ? body.getTargetState() : "inactive";
    targetState.put("state", state);

    if (body.getInputs() != null && !body.getInputs().isEmpty()) {
      String sanitizedInputs = body.getInputs().replace("\t", "  ");
      Map<String, Object> inputsMap =
          YAML_MAPPER.readValue(sanitizedInputs, new TypeReference<Map<String, Object>>() {});
      spec.put("inputs", inputsMap);
    } else {
      spec.remove("inputs");
    }

    if (body.getType() != null && !body.getType().isEmpty()) {
      root.put("type", body.getType());
    } else {
      root.remove("type");
    }

    return YAML_MAPPER.writeValueAsString(root);
  }

  private Map<String, Object> parseBasedOnIdentifier(String basedOnIdentifier) {
    Map<String, Object> basedOn = new LinkedHashMap<>();

    Triple<String, String, String> kindScopeIdentifier = catalogServiceHelper.getKindScopeIdentifier(basedOnIdentifier);
    String scope = kindScopeIdentifier.getMiddle();
    String identifier = kindScopeIdentifier.getRight();

    Pair<String, String> orgProject = catalogServiceHelper.getOrgProjectFromScope(scope);
    String orgIdentifier = orgProject.getLeft();
    String projectIdentifier = orgProject.getRight();

    basedOn.put("identifier", identifier);
    basedOn.put("orgIdentifier", orgIdentifier != null ? orgIdentifier : "");
    basedOn.put("projectIdentifier", projectIdentifier != null ? projectIdentifier : "");

    return basedOn;
  }
}
