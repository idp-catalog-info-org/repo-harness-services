/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.environment.resources;

import static io.harness.ng.core.environment.beans.EnvironmentType.Production;
import static io.harness.pms.rbac.CDNGRbacPermissions.ENVIRONMENT_VIEW_PERMISSION;
import static io.harness.pms.rbac.NGResourceType.ENVIRONMENT;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.entity.metadata.TemplateMetadata;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentGovernanceDataResponse;
import io.harness.ng.core.environment.dto.DestinationEnvironmentConfig;
import io.harness.ng.core.environment.dto.SourceEnvironmentConfig;
import io.harness.ng.core.environment.services.EnvironmentService;
import io.harness.ng.core.infrastructure.InfrastructureType;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.ng.core.infrastructure.entity.InfrastructureGovernanceDataResponse;
import io.harness.ng.core.infrastructure.services.InfrastructureEntityService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagHelper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDC)
public class EnvironmentCloneHelperTest extends CategoryTest {
  @InjectMocks EnvironmentCloneHelper environmentCloneHelper;

  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock AccessControlClient accessControlClient;
  @Mock EnvironmentService environmentService;
  @Mock PmsFeatureFlagHelper pmsFeatureFlagHelper;

  @Mock InfrastructureEntityService infrastructureEntityService;
  @Mock ScopeInfoService scopeInfoService;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String PROJ_UNIQUE_ID = "projUniqueId";
  private final String IDENTIFIER = "identifier";
  private final String envYaml = "environment:\n"
      + "  name: esource\n"
      + "  identifier: esource\n"
      + "  tags: {}\n"
      + "  type: Production\n"
      + "  orgIdentifier: default\n"
      + "  projectIdentifier: proj";

  private final String infraYaml = "infrastructureDefinition:\n"
      + "  name: i1\n"
      + "  identifier: i1\n"
      + "  orgIdentifier: default\n"
      + "  projectIdentifier: proj\n"
      + "  environmentRef: esource\n"
      + "  deploymentType: Kubernetes\n"
      + "  type: KubernetesDirect\n"
      + "  spec:\n"
      + "    connectorRef: account.K8sConnectorimkpq8IN8u\n"
      + "    namespace: nn\n"
      + "    releaseName: release-<+INFRA_KEY_SHORT_ID>\n"
      + "  allowSimultaneousDeployments: false\n";

  SourceEnvironmentConfig sourceEnvironmentConfig = SourceEnvironmentConfig.builder()
                                                        .orgIdentifier(ORG_IDENTIFIER)
                                                        .projectIdentifier(PROJ_IDENTIFIER)
                                                        .envIdentifier("e1")
                                                        .build();
  DestinationEnvironmentConfig destinationEnvironmentConfig =
      DestinationEnvironmentConfig.builder().description("desc").envName("e1").envIdentifier("e1").build();
  Environment environment;
  InfrastructureEntity infrastructure;
  EnvironmentGovernanceDataResponse environmentGovernanceDataResponse;
  InfrastructureGovernanceDataResponse infrastructureGovernanceDataResponse;

  private AutoCloseable mocks;
  ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
  @Before
  public void setup() {
    mocks = MockitoAnnotations.openMocks(this);
    environment = Environment.builder()
                      .accountId(ACCOUNT_ID)
                      .orgIdentifier(ORG_IDENTIFIER)
                      .projectIdentifier(PROJ_IDENTIFIER)
                      .identifier(IDENTIFIER)
                      .version(1L)
                      .type(Production)
                      .yaml(envYaml)
                      .build();

    infrastructure = InfrastructureEntity.builder()
                         .identifier("i1")
                         .name("i1")
                         .accountId(ACCOUNT_ID)
                         .orgIdentifier(ORG_IDENTIFIER)
                         .projectIdentifier(PROJ_IDENTIFIER)
                         .parentUniqueId(PROJ_UNIQUE_ID)
                         .type(InfrastructureType.KUBERNETES_AWS)
                         .yaml(infraYaml)
                         .templateMetadata(Collections.singletonList(TemplateMetadata.builder()
                                                                         .templateRef("someTemplate")
                                                                         .templateVersion("v1")
                                                                         .branchName("main")
                                                                         .build()))
                         .build();
    infrastructureGovernanceDataResponse =
        InfrastructureGovernanceDataResponse.builder().infrastructureEntity(infrastructure).build();
    environmentGovernanceDataResponse = EnvironmentGovernanceDataResponse.builder().environment(environment).build();
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCloneEnvironment() throws JsonProcessingException {
    when(scopeInfoService.getScopeInfo(any(), any(), any()))
        .thenReturn(ScopeInfo.builder()
                        .accountIdentifier(ACCOUNT_ID)
                        .orgIdentifier(sourceEnvironmentConfig.getOrgIdentifier())
                        .projectIdentifier(sourceEnvironmentConfig.getProjectIdentifier())
                        .uniqueId(PROJ_IDENTIFIER)
                        .scopeType(ScopeLevel.PROJECT)
                        .build());

    when(orgAndProjectValidationHelper.checkThatTheOrganizationAndProjectExists(
             ORG_IDENTIFIER, PROJ_IDENTIFIER, ACCOUNT_ID))
        .thenReturn(true);
    Optional<Environment> optionalEnvironment = Optional.of(environment);
    when(environmentService.get(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(optionalEnvironment);
    when(environmentService.get(any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(optionalEnvironment);
    when(infrastructureEntityService.getAllInfrastructureMetadataFromEnvRef(any(), any(), any(), any(), any()))
        .thenReturn(new ArrayList<>());
    when(environmentService.create(any(), any())).thenReturn(environmentGovernanceDataResponse);

    environmentCloneHelper.cloneEnvironment(ACCOUNT_ID, sourceEnvironmentConfig, destinationEnvironmentConfig, true);

    ArgumentCaptor<Environment> entityCaptor = ArgumentCaptor.forClass(Environment.class);
    verify(environmentService).create(entityCaptor.capture(), any(ScopeInfo.class));
    Environment clonedEnviornment = entityCaptor.getValue();

    assertThat(clonedEnviornment.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(clonedEnviornment.getOrgIdentifier()).isNull();
    assertThat(clonedEnviornment.getProjectIdentifier()).isNull();
    assertThat(clonedEnviornment.getName()).isEqualTo("e1");
    assertThat(clonedEnviornment.getIdentifier()).isEqualTo("e1");
    assertThat(clonedEnviornment.getDescription()).isEqualTo("desc");
    assertThat(clonedEnviornment.getTags()).isEmpty();
    assertThat(clonedEnviornment.getType()).isEqualTo(Production);

    String clonedEntityYaml = clonedEnviornment.getYaml();
    JsonNode clonedYamlJsonNode = objectMapper.readTree(clonedEntityYaml);

    assertThat(clonedYamlJsonNode.get("environment").findValue("name").asText()).isEqualTo("e1");
    assertThat(clonedYamlJsonNode.get("environment").findValue("identifier").asText()).isEqualTo("e1");
    assertThat(clonedYamlJsonNode.get("environment").findValue("description").asText()).isEqualTo("desc");
    assertThat(clonedYamlJsonNode.get("environment").findValue("type").asText()).isEqualTo("Production");

    Map<String, String> environmentAttributes = new HashMap<>();
    environmentAttributes.put("type", Production.toString());

    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, sourceEnvironmentConfig.getOrgIdentifier(),
                                   sourceEnvironmentConfig.getProjectIdentifier()),
            Resource.of(ENVIRONMENT, sourceEnvironmentConfig.getEnvIdentifier()), ENVIRONMENT_VIEW_PERMISSION);
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(destinationEnvironmentConfig.getOrgIdentifier(),
            destinationEnvironmentConfig.getProjectIdentifier(), ACCOUNT_ID);
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCloneInfrastructure() throws JsonProcessingException {
    when(infrastructureEntityService.create(any())).thenReturn(infrastructureGovernanceDataResponse);
    when(infrastructureEntityService.get(any(), any(), any(), any(), any(), any(), anyBoolean(), anyBoolean()))
        .thenReturn(Optional.ofNullable(infrastructure));
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_IDENTIFIER)
                              .projectIdentifier(PROJ_IDENTIFIER)
                              .uniqueId(PROJ_UNIQUE_ID)
                              .build();
    environmentCloneHelper.cloneInfrastructure(
        ACCOUNT_ID, infrastructure.getIdentifier(), sourceEnvironmentConfig, destinationEnvironmentConfig, scopeInfo);
    ArgumentCaptor<InfrastructureEntity> entityCaptor = ArgumentCaptor.forClass(InfrastructureEntity.class);
    verify(infrastructureEntityService).create(entityCaptor.capture());
    InfrastructureEntity clonedInfrastructure = entityCaptor.getValue();

    assertThat(clonedInfrastructure.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(clonedInfrastructure.getOrgIdentifier()).isNull();
    assertThat(clonedInfrastructure.getProjectIdentifier()).isNull();
    assertThat(clonedInfrastructure.getName()).isEqualTo("i1");
    assertThat(clonedInfrastructure.getIdentifier()).isEqualTo("i1");
    assertThat(clonedInfrastructure.getEnvIdentifier()).isEqualTo("e1");
    assertThat(clonedInfrastructure.getDescription()).isNull();
    assertThat(clonedInfrastructure.getTags()).isEmpty();
    assertThat(clonedInfrastructure.getType()).isEqualTo(InfrastructureType.KUBERNETES_AWS);
    assertThat(clonedInfrastructure.getTemplateMetadata()).isNull();

    String clonedEntityYaml = clonedInfrastructure.getYaml();
    JsonNode clonedYamlJsonNode = objectMapper.readTree(clonedEntityYaml);
    JsonNode sourceYamlJsonNode = objectMapper.readTree(infraYaml);

    assertThat(clonedYamlJsonNode.get("infrastructureDefinition").findValue("name").asText()).isEqualTo("i1");
    assertThat(clonedYamlJsonNode.get("infrastructureDefinition").findValue("identifier").asText()).isEqualTo("i1");
    assertThat(clonedYamlJsonNode.get("infrastructureDefinition").findValue("environmentRef").asText()).isEqualTo("e1");
    assertThat(clonedYamlJsonNode.get("infrastructureDefinition").findValue("spec"))
        .isEqualTo(sourceYamlJsonNode.get("infrastructureDefinition").findValue("spec"));
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }
}
