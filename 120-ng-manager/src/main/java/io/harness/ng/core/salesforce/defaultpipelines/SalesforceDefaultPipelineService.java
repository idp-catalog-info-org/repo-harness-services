/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.salesforce.defaultpipelines;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.ScopeInfo;
import io.harness.cdng.infra.mapper.InfrastructureMapper;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentMapper;
import io.harness.ng.core.environment.beans.EnvironmentType;
import io.harness.ng.core.environment.dto.EnvironmentRequestDTO;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.dto.InfrastructureRequestDTO;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.pipeline.remote.PipelineServiceClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.ng.v1.model.SalesforceDefaultPipelineDTO;
import io.harness.spec.server.pipeline.v1.model.PipelineCreateRequestBody;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@Slf4j
@Singleton
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.SALESFORCE})
@OwnedBy(HarnessTeam.CDP)
public class SalesforceDefaultPipelineService {
  private static final String PLACEHOLDER_NAME = "__PIPELINE_NAME__";
  private static final String PLACEHOLDER_IDENTIFIER = "__PIPELINE_IDENTIFIER__";
  private static final String PLACEHOLDER_ORG = "__ORG_IDENTIFIER__";
  private static final String PLACEHOLDER_PROJECT = "__PROJECT_IDENTIFIER__";

  private static final String DEFAULT_ENV_IDENTIFIER = "salesforce_runtime";
  private static final String DEFAULT_ENV_NAME = "Salesforce";
  private static final String DEFAULT_INFRA_IDENTIFIER = "salesforce_runtime";
  private static final String DEFAULT_INFRA_NAME = "salesforce_runtime";

  private static final String ENV_RESOURCE_PATH = "salesforce/salesforce_environment.yaml";
  private static final String INFRA_RESOURCE_PATH = "salesforce/salesforce_infrastructure.yaml";

  private static final List<DefaultPipelineSpec> PREDEFINED_PIPELINES =
      List.of(new DefaultPipelineSpec(
                  "salesforce_dx_deploy", "Salesforce DX Deploy", "salesforce/pipelines/salesforce_dx_deploy.yaml"),
          new DefaultPipelineSpec(
              "salesforce_dx_validate", "Salesforce DX Validate", "salesforce/pipelines/salesforce_validate.yaml"),
          new DefaultPipelineSpec("salesforce_quick_deploy", "Salesforce Quick Deploy",
              "salesforce/pipelines/salesforce_quick_deploy.yaml"),
          new DefaultPipelineSpec("salesforce_evaluate_diff", "Salesforce Evaluate Diff",
              "salesforce/pipelines/salesforce_evaluate_diff.yaml"),
          new DefaultPipelineSpec("salesforce_source_backup", "Salesforce Source Backup",
              "salesforce/pipelines/salesforce_source_backup.yaml"));

  @Inject private PipelineServiceClient pipelineServiceClient;
  @Inject private EnvironmentService environmentService;
  @Inject private InfrastructureEntityService infrastructureEntityService;
  @Inject private ScopeInfoService scopeInfoService;

  public List<SalesforceDefaultPipelineDTO> createDefaultPipelines(String accountId, String orgId, String projectId) {
    List<SalesforceDefaultPipelineDTO> results = new ArrayList<>();
    ScopeInfo scopeInfo = scopeInfoService.getScopeInfo(accountId, orgId, projectId);

    results.add(createDefaultEnvironment(accountId, orgId, projectId, scopeInfo));
    results.add(createDefaultInfrastructure(accountId, orgId, projectId, scopeInfo));

    for (DefaultPipelineSpec spec : PREDEFINED_PIPELINES) {
      results.add(processSpec(spec, accountId, orgId, projectId));
    }
    return results;
  }

  private SalesforceDefaultPipelineDTO createDefaultEnvironment(
      String accountId, String orgId, String projectId, ScopeInfo scopeInfo) {
    if (environmentService.get(accountId, orgId, projectId, DEFAULT_ENV_IDENTIFIER, false).isPresent()) {
      log.info("Salesforce default environment already exists: identifier={}, account={}, org={}, project={}",
          DEFAULT_ENV_IDENTIFIER, accountId, orgId, projectId);
      return new SalesforceDefaultPipelineDTO()
          .identifier(DEFAULT_ENV_IDENTIFIER)
          .name(DEFAULT_ENV_NAME)
          .status(SalesforceDefaultPipelineDTO.StatusEnum.ALREADY_EXISTS);
    }

    try {
      String yaml = loadTemplate(ENV_RESOURCE_PATH, orgId, projectId);
      EnvironmentRequestDTO dto = EnvironmentRequestDTO.builder()
                                      .identifier(DEFAULT_ENV_IDENTIFIER)
                                      .name(DEFAULT_ENV_NAME)
                                      .orgIdentifier(orgId)
                                      .projectIdentifier(projectId)
                                      .type(EnvironmentType.PreProduction)
                                      .yaml(yaml)
                                      .build();
      Environment environment = EnvironmentMapper.toEnvironmentEntity(accountId, dto, scopeInfo);
      environmentService.create(environment, scopeInfo);
      log.info("Created Salesforce default environment: identifier={}, account={}, org={}, project={}",
          DEFAULT_ENV_IDENTIFIER, accountId, orgId, projectId);
      return new SalesforceDefaultPipelineDTO()
          .identifier(DEFAULT_ENV_IDENTIFIER)
          .name(DEFAULT_ENV_NAME)
          .status(SalesforceDefaultPipelineDTO.StatusEnum.CREATED);
    } catch (Exception e) {
      log.warn(
          "Failed to create Salesforce default environment: identifier={}, account={}, org={}, project={}, error={}",
          DEFAULT_ENV_IDENTIFIER, accountId, orgId, projectId, e.getMessage());
      return new SalesforceDefaultPipelineDTO()
          .identifier(DEFAULT_ENV_IDENTIFIER)
          .name(DEFAULT_ENV_NAME)
          .status(SalesforceDefaultPipelineDTO.StatusEnum.FAILED)
          .errorMessage(e.getMessage());
    }
  }

  private SalesforceDefaultPipelineDTO createDefaultInfrastructure(
      String accountId, String orgId, String projectId, ScopeInfo scopeInfo) {
    if (infrastructureEntityService
            .get(accountId, orgId, projectId, scopeInfo, DEFAULT_ENV_IDENTIFIER, DEFAULT_INFRA_IDENTIFIER)
            .isPresent()) {
      log.info("Salesforce default infrastructure already exists: identifier={}, account={}, org={}, project={}",
          DEFAULT_INFRA_IDENTIFIER, accountId, orgId, projectId);
      return new SalesforceDefaultPipelineDTO()
          .identifier(DEFAULT_INFRA_IDENTIFIER)
          .name(DEFAULT_INFRA_NAME)
          .status(SalesforceDefaultPipelineDTO.StatusEnum.ALREADY_EXISTS);
    }

    try {
      String yaml = loadTemplate(INFRA_RESOURCE_PATH, orgId, projectId);
      InfrastructureRequestDTO dto = InfrastructureRequestDTO.builder()
                                         .identifier(DEFAULT_INFRA_IDENTIFIER)
                                         .name(DEFAULT_INFRA_NAME)
                                         .orgIdentifier(orgId)
                                         .projectIdentifier(projectId)
                                         .environmentRef(DEFAULT_ENV_IDENTIFIER)
                                         .yaml(yaml)
                                         .build();
      infrastructureEntityService.create(InfrastructureMapper.toInfrastructureEntity(accountId, dto));
      log.info("Created Salesforce default infrastructure: identifier={}, account={}, org={}, project={}",
          DEFAULT_INFRA_IDENTIFIER, accountId, orgId, projectId);
      return new SalesforceDefaultPipelineDTO()
          .identifier(DEFAULT_INFRA_IDENTIFIER)
          .name(DEFAULT_INFRA_NAME)
          .status(SalesforceDefaultPipelineDTO.StatusEnum.CREATED);
    } catch (Exception e) {
      log.warn(
          "Failed to create Salesforce default infrastructure: identifier={}, account={}, org={}, project={}, error={}",
          DEFAULT_INFRA_IDENTIFIER, accountId, orgId, projectId, e.getMessage());
      return new SalesforceDefaultPipelineDTO()
          .identifier(DEFAULT_INFRA_IDENTIFIER)
          .name(DEFAULT_INFRA_NAME)
          .status(SalesforceDefaultPipelineDTO.StatusEnum.FAILED)
          .errorMessage(e.getMessage());
    }
  }

  private SalesforceDefaultPipelineDTO processSpec(
      DefaultPipelineSpec spec, String accountId, String orgId, String projectId) {
    if (pipelineExists(spec.identifier(), accountId, orgId, projectId)) {
      log.info("Salesforce default pipeline already exists: identifier={}, account={}, org={}, project={}",
          spec.identifier(), accountId, orgId, projectId);
      return new SalesforceDefaultPipelineDTO()
          .identifier(spec.identifier())
          .name(spec.name())
          .status(SalesforceDefaultPipelineDTO.StatusEnum.ALREADY_EXISTS);
    }

    String yaml = loadAndFillTemplate(spec, orgId, projectId);
    PipelineCreateRequestBody body =
        new PipelineCreateRequestBody().name(spec.name()).identifier(spec.identifier()).pipelineYaml(yaml);
    try {
      NGRestUtils.getGeneralResponse(pipelineServiceClient.createPipeline(body, orgId, projectId, accountId));
    } catch (Exception e) {
      log.warn("Failed to create Salesforce default pipeline: identifier={}, account={}, org={}, project={}, error={}",
          spec.identifier(), accountId, orgId, projectId, e.getMessage());
      return new SalesforceDefaultPipelineDTO()
          .identifier(spec.identifier())
          .name(spec.name())
          .status(SalesforceDefaultPipelineDTO.StatusEnum.FAILED)
          .errorMessage(e.getMessage());
    }
    log.info("Created Salesforce default pipeline: identifier={}, account={}, org={}, project={}", spec.identifier(),
        accountId, orgId, projectId);
    return new SalesforceDefaultPipelineDTO()
        .identifier(spec.identifier())
        .name(spec.name())
        .status(SalesforceDefaultPipelineDTO.StatusEnum.CREATED);
  }

  private boolean pipelineExists(String identifier, String accountId, String orgId, String projectId) {
    try {
      NGRestUtils.getGeneralResponse(pipelineServiceClient.getPipeline(
          orgId, projectId, identifier, accountId, null, null, null, null, null, null, null));
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private String loadTemplate(String resourcePath, String orgId, String projectId) {
    try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new InvalidRequestException("Salesforce template not found: " + resourcePath);
      }
      return IOUtils.toString(is, StandardCharsets.UTF_8)
          .replace(PLACEHOLDER_ORG, orgId)
          .replace(PLACEHOLDER_PROJECT, projectId);
    } catch (IOException e) {
      throw new InvalidRequestException("Failed to load Salesforce template: " + resourcePath, e);
    }
  }

  private String loadAndFillTemplate(DefaultPipelineSpec spec, String orgId, String projectId) {
    try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(spec.resourcePath())) {
      if (is == null) {
        throw new InvalidRequestException("Salesforce pipeline template not found: " + spec.resourcePath());
      }
      return IOUtils.toString(is, StandardCharsets.UTF_8)
          .replace(PLACEHOLDER_NAME, spec.name())
          .replace(PLACEHOLDER_IDENTIFIER, spec.identifier())
          .replace(PLACEHOLDER_ORG, orgId)
          .replace(PLACEHOLDER_PROJECT, projectId);
    } catch (IOException e) {
      throw new InvalidRequestException("Failed to load Salesforce pipeline template: " + spec.resourcePath(), e);
    }
  }

  record DefaultPipelineSpec(String identifier, String name, String resourcePath) {}
}
