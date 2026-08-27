/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.plan.creator.filter;

import static io.harness.rule.OwnerRule.BRIJESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cimanager.stages.V1.UnifiedStageNodeV1;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.eventsframework.schemas.entity.IdentifierRefProtoDTO;
import io.harness.eventsframework.schemas.entity.InfraDefinitionReferenceProtoDTO;
import io.harness.pms.contracts.plan.SetupMetadata;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CI)
@RunWith(MockitoJUnitRunner.class)
public class UnifiedStageFilterCreatorTest {
  @InjectMocks private UnifiedStageFilterCreator unifiedStageFilterCreator;

  private static final String ACCOUNT_ID = "acc123";
  private static final String ORG_ID = "org123";
  private static final String PROJECT_ID = "proj123";
  private static final ObjectMapper mapper = new ObjectMapper();

  private FilterCreationContext filterCreationContext;

  @Before
  public void setup() {
    filterCreationContext =
        FilterCreationContext.builder()
            .setupMetadata(
                SetupMetadata.newBuilder().setAccountId(ACCOUNT_ID).setOrgId(ORG_ID).setProjectId(PROJECT_ID).build())
            .build();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetSupportedStageTypes() {
    assertThat(unifiedStageFilterCreator.getSupportedStageTypes()).contains(YAMLFieldNameConstants.UNIFIED);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetFieldClass() {
    assertThat(unifiedStageFilterCreator.getFieldClass()).isEqualTo(UnifiedStageNodeV1.class);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_InlineService() {
    // Inline service as string
    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setService(ParameterField.createValueField("my-service"));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getType()).isEqualTo(EntityTypeProtoEnum.SERVICE);
    IdentifierRefProtoDTO identifierRef = (IdentifierRefProtoDTO) refs.get(0).getIdentifierRef();
    assertThat(identifierRef.getIdentifier().getValue()).isEqualTo("my-service");
    assertThat(identifierRef.getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_ServiceWithId() {
    // Service as object with id
    Map<String, Object> serviceMap = new HashMap<>();
    serviceMap.put(YAMLFieldNameConstants.ID, "service-with-id");

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setService(ParameterField.createValueField(serviceMap));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getType()).isEqualTo(EntityTypeProtoEnum.SERVICE);
    IdentifierRefProtoDTO identifierRef = (IdentifierRefProtoDTO) refs.get(0).getIdentifierRef();
    assertThat(identifierRef.getIdentifier().getValue()).isEqualTo("service-with-id");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_MultipleServices() {
    // Service items format
    Map<String, Object> serviceMap = new HashMap<>();
    List<Object> serviceItems = List.of("service1", "service2", Map.of(YAMLFieldNameConstants.ID, "service3"));
    serviceMap.put(YAMLFieldNameConstants.ITEMS, serviceItems);

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setService(ParameterField.createValueField(serviceMap));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    assertThat(refs).hasSize(3);
    assertThat(refs).allMatch(ref -> ref.getType() == EntityTypeProtoEnum.SERVICE);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_InlineEnvironment() {
    // Environment with id and deploy-to
    Map<String, Object> envMap = new HashMap<>();
    envMap.put(YAMLFieldNameConstants.ID, "prod-env");
    envMap.put(YAMLFieldNameConstants.DEPLOY_TO, "k8s-infra");

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setEnvironment(ParameterField.createValueField(envMap));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    assertThat(refs).hasSize(2);

    // Check environment reference
    EntityDetailProtoDTO envRef =
        refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.ENVIRONMENT).findFirst().orElse(null);
    assertThat(envRef).isNotNull();
    IdentifierRefProtoDTO envIdentifierRef = (IdentifierRefProtoDTO) envRef.getIdentifierRef();
    assertThat(envIdentifierRef.getIdentifier().getValue()).isEqualTo("prod-env");

    // Check infrastructure reference with envIdentifier
    EntityDetailProtoDTO infraRef =
        refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.INFRASTRUCTURE).findFirst().orElse(null);
    assertThat(infraRef).isNotNull();
    InfraDefinitionReferenceProtoDTO infraIdentifierRef = infraRef.getInfraDefRef();
    assertThat(infraIdentifierRef.getIdentifier().getValue()).isEqualTo("k8s-infra");
    assertThat(infraIdentifierRef.getEnvIdentifier().getValue()).isEqualTo("prod-env");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_MultipleEnvironments() {
    // Multiple environments with items format
    Map<String, Object> env1 = new HashMap<>();
    env1.put(YAMLFieldNameConstants.ID, "staging");
    env1.put(YAMLFieldNameConstants.DEPLOY_TO, "staging-infra");

    Map<String, Object> env2 = new HashMap<>();
    env2.put(YAMLFieldNameConstants.ID, "prod");
    env2.put(YAMLFieldNameConstants.DEPLOY_TO, List.of("prod-infra1", "prod-infra2"));

    Map<String, Object> envMap = new HashMap<>();
    envMap.put(YAMLFieldNameConstants.ITEMS, List.of(env1, env2));

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setEnvironment(ParameterField.createValueField(envMap));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    // Should have 2 environments + 3 infrastructures
    assertThat(refs).hasSize(5);

    // Check environments
    List<EntityDetailProtoDTO> envRefs =
        refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.ENVIRONMENT).toList();
    assertThat(envRefs).hasSize(2);

    // Check infrastructures with proper envIdentifiers
    List<EntityDetailProtoDTO> infraRefs =
        refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.INFRASTRUCTURE).toList();
    assertThat(infraRefs).hasSize(3);

    // Verify staging infra has staging envIdentifier
    EntityDetailProtoDTO stagingInfra = infraRefs.stream()
                                            .filter(ref -> {
                                              InfraDefinitionReferenceProtoDTO infraRef = ref.getInfraDefRef();
                                              return infraRef.getIdentifier().getValue().equals("staging-infra");
                                            })
                                            .findFirst()
                                            .orElse(null);
    assertThat(stagingInfra).isNotNull();
    InfraDefinitionReferenceProtoDTO stagingInfraRef = stagingInfra.getInfraDefRef();
    assertThat(stagingInfraRef.getEnvIdentifier().getValue()).isEqualTo("staging");

    // Verify prod infras have prod envIdentifier
    infraRefs.stream()
        .filter(ref -> {
          InfraDefinitionReferenceProtoDTO infraRef = ref.getInfraDefRef();
          return infraRef.getIdentifier().getValue().startsWith("prod-infra");
        })
        .forEach(ref -> {
          InfraDefinitionReferenceProtoDTO infraRef = ref.getInfraDefRef();
          assertThat(infraRef.getEnvIdentifier().getValue()).isEqualTo("prod");
        });
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_ServiceAndEnvironment() {
    // Combined service and environment
    Map<String, Object> envMap = new HashMap<>();
    envMap.put(YAMLFieldNameConstants.ID, "test-env");
    envMap.put(YAMLFieldNameConstants.DEPLOY_TO, "test-infra");

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setService(ParameterField.createValueField("test-service"));
    stageNode.setEnvironment(ParameterField.createValueField(envMap));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    // Should have 1 service + 1 environment + 1 infrastructure
    assertThat(refs).hasSize(3);
    assertThat(refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.SERVICE).count()).isEqualTo(1);
    assertThat(refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.ENVIRONMENT).count()).isEqualTo(1);
    assertThat(refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.INFRASTRUCTURE).count()).isEqualTo(1);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_JsonNodeService() throws IOException {
    // Service as JsonNode (string)
    JsonNode serviceNode = mapper.readTree("\"json-service\"");

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setService(ParameterField.createValueField(serviceNode));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getType()).isEqualTo(EntityTypeProtoEnum.SERVICE);
    IdentifierRefProtoDTO identifierRef = (IdentifierRefProtoDTO) refs.get(0).getIdentifierRef();
    assertThat(identifierRef.getIdentifier().getValue()).isEqualTo("json-service");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_JsonNodeServiceWithItems() throws IOException {
    // Service as JsonNode with items
    String serviceJson = "{\"items\": [\"service1\", {\"id\": \"service2\"}]}";
    JsonNode serviceNode = mapper.readTree(serviceJson);

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setService(ParameterField.createValueField(serviceNode));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    assertThat(refs).hasSize(2);
    assertThat(refs).allMatch(ref -> ref.getType() == EntityTypeProtoEnum.SERVICE);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_JsonNodeEnvironment() throws IOException {
    // Environment as JsonNode
    String envJson = "{\"id\": \"json-env\", \"deploy-to\": \"json-infra\"}";
    JsonNode envNode = mapper.readTree(envJson);

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setEnvironment(ParameterField.createValueField(envNode));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    assertThat(refs).hasSize(2);

    EntityDetailProtoDTO envRef =
        refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.ENVIRONMENT).findFirst().orElse(null);
    assertThat(envRef).isNotNull();

    EntityDetailProtoDTO infraRef =
        refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.INFRASTRUCTURE).findFirst().orElse(null);
    assertThat(infraRef).isNotNull();
    InfraDefinitionReferenceProtoDTO infraIdentifierRef = infraRef.getInfraDefRef();
    assertThat(infraIdentifierRef.getEnvIdentifier().getValue()).isEqualTo("json-env");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_NullValues() {
    // Test with null service and environment
    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setService(null);
    stageNode.setEnvironment(null);

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    assertThat(refs).isEmpty();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_EmptyValues() {
    // Test with empty ParameterField
    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setService(ParameterField.createValueField(null));
    stageNode.setEnvironment(ParameterField.createValueField(null));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    assertThat(refs).isEmpty();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_EnvironmentWithMultipleInfrastructures() {
    // Environment with multiple infrastructures in deploy-to array
    Map<String, Object> envMap = new HashMap<>();
    envMap.put(YAMLFieldNameConstants.ID, "multi-infra-env");
    envMap.put(YAMLFieldNameConstants.DEPLOY_TO, List.of("infra1", "infra2", "infra3"));

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setEnvironment(ParameterField.createValueField(envMap));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    // Should have 1 environment + 3 infrastructures
    assertThat(refs).hasSize(4);

    List<EntityDetailProtoDTO> infraRefs =
        refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.INFRASTRUCTURE).toList();
    assertThat(infraRefs).hasSize(3);

    // All infrastructures should have the same envIdentifier
    infraRefs.forEach(ref -> {
      InfraDefinitionReferenceProtoDTO infraRef = ref.getInfraDefRef();
      assertThat(infraRef.getEnvIdentifier().getValue()).isEqualTo("multi-infra-env");
    });
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_OrgAndProjectScoping() {
    // Test that org and project IDs are properly included
    Map<String, Object> envMap = new HashMap<>();
    envMap.put(YAMLFieldNameConstants.ID, "scoped-env");
    envMap.put(YAMLFieldNameConstants.DEPLOY_TO, "scoped-infra");

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setService(ParameterField.createValueField("scoped-service"));
    stageNode.setEnvironment(ParameterField.createValueField(envMap));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    // Verify all references have correct scope
    refs.forEach(ref -> {
      if (ref.getType() == EntityTypeProtoEnum.INFRASTRUCTURE) {
        InfraDefinitionReferenceProtoDTO infraRef = ref.getInfraDefRef();
        assertThat(infraRef.getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_ID);
        assertThat(infraRef.getOrgIdentifier().getValue()).isEqualTo(ORG_ID);
        assertThat(infraRef.getProjectIdentifier().getValue()).isEqualTo(PROJECT_ID);
      } else {
        IdentifierRefProtoDTO identifierRef = ref.getIdentifierRef();
        assertThat(identifierRef.getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_ID);
      }
    });
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_AccountScopedService() {
    // Test account-scoped service reference (account.service)
    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setService(ParameterField.createValueField("account.my-account-service"));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getType()).isEqualTo(EntityTypeProtoEnum.SERVICE);
    IdentifierRefProtoDTO identifierRef = refs.get(0).getIdentifierRef();

    // Verify identifier is extracted correctly (without "account." prefix)
    assertThat(identifierRef.getIdentifier().getValue()).isEqualTo("my-account-service");

    // Verify only accountId is set for account-scoped entities
    assertThat(identifierRef.getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(identifierRef.hasOrgIdentifier()).isFalse();
    assertThat(identifierRef.hasProjectIdentifier()).isFalse();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_OrgScopedService() {
    // Test org-scoped service reference (org.service)
    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setService(ParameterField.createValueField("org.my-org-service"));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getType()).isEqualTo(EntityTypeProtoEnum.SERVICE);
    IdentifierRefProtoDTO identifierRef = refs.get(0).getIdentifierRef();

    // Verify identifier is extracted correctly (without "org." prefix)
    assertThat(identifierRef.getIdentifier().getValue()).isEqualTo("my-org-service");

    // Verify accountId and orgId are set for org-scoped entities, but not projectId
    assertThat(identifierRef.getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(identifierRef.getOrgIdentifier().getValue()).isEqualTo(ORG_ID);
    assertThat(identifierRef.hasProjectIdentifier()).isFalse();
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_ProjectScopedService() {
    // Test project-scoped service reference (no prefix)
    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setService(ParameterField.createValueField("my-project-service"));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    assertThat(refs).hasSize(1);
    assertThat(refs.get(0).getType()).isEqualTo(EntityTypeProtoEnum.SERVICE);
    IdentifierRefProtoDTO identifierRef = refs.get(0).getIdentifierRef();

    // Verify identifier remains unchanged
    assertThat(identifierRef.getIdentifier().getValue()).isEqualTo("my-project-service");

    // Verify all scope identifiers are set for project-scoped entities
    assertThat(identifierRef.getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(identifierRef.getOrgIdentifier().getValue()).isEqualTo(ORG_ID);
    assertThat(identifierRef.getProjectIdentifier().getValue()).isEqualTo(PROJECT_ID);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_AccountScopedEnvironment() {
    // Test account-scoped environment reference (account.env)
    // Infrastructure identifier is ALWAYS plain (no scope prefix)
    Map<String, Object> envMap = new HashMap<>();
    envMap.put(YAMLFieldNameConstants.ID, "account.my-account-env");
    envMap.put(YAMLFieldNameConstants.DEPLOY_TO, "my-account-infra"); // Plain identifier

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setEnvironment(ParameterField.createValueField(envMap));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    assertThat(refs).hasSize(2);

    // Verify environment
    EntityDetailProtoDTO envRef =
        refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.ENVIRONMENT).findFirst().orElse(null);
    assertThat(envRef).isNotNull();
    IdentifierRefProtoDTO envIdentifierRef = envRef.getIdentifierRef();
    assertThat(envIdentifierRef.getIdentifier().getValue()).isEqualTo("my-account-env");
    assertThat(envIdentifierRef.getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(envIdentifierRef.hasOrgIdentifier()).isFalse();
    assertThat(envIdentifierRef.hasProjectIdentifier()).isFalse();

    // Verify infrastructure (inherits environment's scope)
    EntityDetailProtoDTO infraRef =
        refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.INFRASTRUCTURE).findFirst().orElse(null);
    assertThat(infraRef).isNotNull();
    InfraDefinitionReferenceProtoDTO infraIdentifierRef = infraRef.getInfraDefRef();
    assertThat(infraIdentifierRef.getIdentifier().getValue()).isEqualTo("my-account-infra");
    assertThat(infraIdentifierRef.getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(infraIdentifierRef.getOrgIdentifier().getValue()).isEmpty();
    assertThat(infraIdentifierRef.getProjectIdentifier().getValue()).isEmpty();
    // Verify envIdentifier is parsed (scope prefix removed)
    assertThat(infraIdentifierRef.getEnvIdentifier().getValue()).isEqualTo("my-account-env");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_OrgScopedEnvironment() {
    // Test org-scoped environment reference (org.env)
    // Infrastructure identifier is ALWAYS plain (no scope prefix)
    Map<String, Object> envMap = new HashMap<>();
    envMap.put(YAMLFieldNameConstants.ID, "org.my-org-env");
    envMap.put(YAMLFieldNameConstants.DEPLOY_TO, "my-org-infra"); // Plain identifier

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setEnvironment(ParameterField.createValueField(envMap));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    assertThat(refs).hasSize(2);

    // Verify environment
    EntityDetailProtoDTO envRef =
        refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.ENVIRONMENT).findFirst().orElse(null);
    assertThat(envRef).isNotNull();
    IdentifierRefProtoDTO envIdentifierRef = envRef.getIdentifierRef();
    assertThat(envIdentifierRef.getIdentifier().getValue()).isEqualTo("my-org-env");
    assertThat(envIdentifierRef.getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(envIdentifierRef.getOrgIdentifier().getValue()).isEqualTo(ORG_ID);
    assertThat(envIdentifierRef.hasProjectIdentifier()).isFalse();

    // Verify infrastructure (inherits environment's scope)
    EntityDetailProtoDTO infraRef =
        refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.INFRASTRUCTURE).findFirst().orElse(null);
    assertThat(infraRef).isNotNull();
    InfraDefinitionReferenceProtoDTO infraIdentifierRef = infraRef.getInfraDefRef();
    assertThat(infraIdentifierRef.getIdentifier().getValue()).isEqualTo("my-org-infra");
    assertThat(infraIdentifierRef.getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(infraIdentifierRef.getOrgIdentifier().getValue()).isEqualTo(ORG_ID);
    assertThat(infraIdentifierRef.getProjectIdentifier().getValue()).isEmpty();
    // Verify envIdentifier is parsed (scope prefix removed)
    assertThat(infraIdentifierRef.getEnvIdentifier().getValue()).isEqualTo("my-org-env");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_MixedScopes() {
    // Test mixed scopes - account service, org environment
    // IMPORTANT: Infrastructure inherits environment's scope, so it will also be org-scoped
    Map<String, Object> envMap = new HashMap<>();
    envMap.put(YAMLFieldNameConstants.ID, "org.my-org-env");
    envMap.put(YAMLFieldNameConstants.DEPLOY_TO, "my-infra"); // Plain identifier

    UnifiedStageNodeV1 stageNode = new UnifiedStageNodeV1();
    stageNode.setService(ParameterField.createValueField("account.my-account-service"));
    stageNode.setEnvironment(ParameterField.createValueField(envMap));

    List<EntityDetailProtoDTO> refs =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode);

    // Should have 1 service + 1 environment + 1 infrastructure
    assertThat(refs).hasSize(3);

    // Verify account-scoped service
    EntityDetailProtoDTO serviceRef =
        refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.SERVICE).findFirst().orElse(null);
    assertThat(serviceRef).isNotNull();
    IdentifierRefProtoDTO serviceIdentifierRef = serviceRef.getIdentifierRef();
    assertThat(serviceIdentifierRef.getIdentifier().getValue()).isEqualTo("my-account-service");
    assertThat(serviceIdentifierRef.hasOrgIdentifier()).isFalse();
    assertThat(serviceIdentifierRef.hasProjectIdentifier()).isFalse();

    // Verify org-scoped environment
    EntityDetailProtoDTO envRef =
        refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.ENVIRONMENT).findFirst().orElse(null);
    assertThat(envRef).isNotNull();
    IdentifierRefProtoDTO envIdentifierRef = envRef.getIdentifierRef();
    assertThat(envIdentifierRef.getIdentifier().getValue()).isEqualTo("my-org-env");
    assertThat(envIdentifierRef.getOrgIdentifier().getValue()).isEqualTo(ORG_ID);
    assertThat(envIdentifierRef.hasProjectIdentifier()).isFalse();

    // Verify infrastructure (inherits org scope from environment)
    EntityDetailProtoDTO infraRef =
        refs.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.INFRASTRUCTURE).findFirst().orElse(null);
    assertThat(infraRef).isNotNull();
    InfraDefinitionReferenceProtoDTO infraIdentifierRef = infraRef.getInfraDefRef();
    assertThat(infraIdentifierRef.getIdentifier().getValue()).isEqualTo("my-infra");
    // Infrastructure inherits environment's scope (org-level), NOT project
    assertThat(infraIdentifierRef.getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(infraIdentifierRef.getOrgIdentifier().getValue()).isEqualTo(ORG_ID);
    assertThat(infraIdentifierRef.getProjectIdentifier().getValue()).isEmpty();
    // Verify envIdentifier is parsed (scope prefix removed)
    assertThat(infraIdentifierRef.getEnvIdentifier().getValue()).isEqualTo("my-org-env");
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testExtractReferredEntities_EnvIdentifierScopeParsing() {
    // Bug fix test: Verify that envIdentifier is correctly parsed to remove scope prefix
    // Previously, if environment was "account.my-account-env", the infrastructure's envIdentifier
    // would incorrectly contain "account.my-account-env" instead of just "my-account-env"
    //
    // IMPORTANT: Infrastructure identifiers are ALWAYS plain (no scope prefix).
    // Infrastructure scope is derived from the environment's scope, not from parsing the infra identifier.

    // Test with account-scoped environment and plain infrastructure identifier
    Map<String, Object> accountEnvMap = new HashMap<>();
    accountEnvMap.put(YAMLFieldNameConstants.ID, "account.my-account-env");
    accountEnvMap.put(YAMLFieldNameConstants.DEPLOY_TO, "my-infra"); // Plain identifier, no scope prefix

    UnifiedStageNodeV1 stageNode1 = new UnifiedStageNodeV1();
    stageNode1.setEnvironment(ParameterField.createValueField(accountEnvMap));

    List<EntityDetailProtoDTO> refs1 =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode1);

    EntityDetailProtoDTO infraRef1 =
        refs1.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.INFRASTRUCTURE).findFirst().orElse(null);
    assertThat(infraRef1).isNotNull();
    InfraDefinitionReferenceProtoDTO infraIdentifierRef1 = infraRef1.getInfraDefRef();

    // CRITICAL: envIdentifier should be parsed to extract only the actual identifier without scope prefix
    assertThat(infraIdentifierRef1.getEnvIdentifier().getValue())
        .isEqualTo("my-account-env")
        .withFailMessage("envIdentifier should be 'my-account-env', not 'account.my-account-env'");

    // Infrastructure identifier should remain plain (no parsing needed since it has no scope)
    assertThat(infraIdentifierRef1.getIdentifier().getValue()).isEqualTo("my-infra");

    // Infrastructure should inherit environment's scope (account-level)
    assertThat(infraIdentifierRef1.getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(infraIdentifierRef1.getOrgIdentifier().getValue()).isEmpty();
    assertThat(infraIdentifierRef1.getProjectIdentifier().getValue()).isEmpty();

    // Test with org-scoped environment and plain infrastructure identifier
    Map<String, Object> orgEnvMap = new HashMap<>();
    orgEnvMap.put(YAMLFieldNameConstants.ID, "org.my-org-env");
    orgEnvMap.put(YAMLFieldNameConstants.DEPLOY_TO, "my-infra2"); // Plain identifier, no scope prefix

    UnifiedStageNodeV1 stageNode2 = new UnifiedStageNodeV1();
    stageNode2.setEnvironment(ParameterField.createValueField(orgEnvMap));

    List<EntityDetailProtoDTO> refs2 =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode2);

    EntityDetailProtoDTO infraRef2 =
        refs2.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.INFRASTRUCTURE).findFirst().orElse(null);
    assertThat(infraRef2).isNotNull();
    InfraDefinitionReferenceProtoDTO infraIdentifierRef2 = infraRef2.getInfraDefRef();

    // CRITICAL: envIdentifier should be parsed to extract only the actual identifier without scope prefix
    assertThat(infraIdentifierRef2.getEnvIdentifier().getValue())
        .isEqualTo("my-org-env")
        .withFailMessage("envIdentifier should be 'my-org-env', not 'org.my-org-env'");

    // Infrastructure identifier should remain plain
    assertThat(infraIdentifierRef2.getIdentifier().getValue()).isEqualTo("my-infra2");

    // Infrastructure should inherit environment's scope (org-level)
    assertThat(infraIdentifierRef2.getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(infraIdentifierRef2.getOrgIdentifier().getValue()).isEqualTo(ORG_ID);
    assertThat(infraIdentifierRef2.getProjectIdentifier().getValue()).isEmpty();

    // Test with project-scoped environment (no prefix)
    Map<String, Object> projectEnvMap = new HashMap<>();
    projectEnvMap.put(YAMLFieldNameConstants.ID, "my-project-env");
    projectEnvMap.put(YAMLFieldNameConstants.DEPLOY_TO, "my-infra3");

    UnifiedStageNodeV1 stageNode3 = new UnifiedStageNodeV1();
    stageNode3.setEnvironment(ParameterField.createValueField(projectEnvMap));

    List<EntityDetailProtoDTO> refs3 =
        unifiedStageFilterCreator.extractReferredEntities(filterCreationContext, stageNode3);

    EntityDetailProtoDTO infraRef3 =
        refs3.stream().filter(ref -> ref.getType() == EntityTypeProtoEnum.INFRASTRUCTURE).findFirst().orElse(null);
    assertThat(infraRef3).isNotNull();
    InfraDefinitionReferenceProtoDTO infraIdentifierRef3 = infraRef3.getInfraDefRef();

    // For project scope (no prefix), envIdentifier should remain as-is
    assertThat(infraIdentifierRef3.getEnvIdentifier().getValue()).isEqualTo("my-project-env");

    // Infrastructure identifier should remain plain
    assertThat(infraIdentifierRef3.getIdentifier().getValue()).isEqualTo("my-infra3");

    // Infrastructure should inherit environment's scope (project-level)
    assertThat(infraIdentifierRef3.getAccountIdentifier().getValue()).isEqualTo(ACCOUNT_ID);
    assertThat(infraIdentifierRef3.getOrgIdentifier().getValue()).isEqualTo(ORG_ID);
    assertThat(infraIdentifierRef3.getProjectIdentifier().getValue()).isEqualTo(PROJECT_ID);
  }
}
