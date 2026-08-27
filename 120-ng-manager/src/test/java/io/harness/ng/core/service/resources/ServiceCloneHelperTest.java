/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.resources;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.pms.rbac.CDNGRbacPermissions.SERVICE_CREATE_PERMISSION;
import static io.harness.rule.OwnerRule.HARSHIT;
import static io.harness.rule.OwnerRule.LOVISH_BANSAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.gitsync.beans.StoreType;
import io.harness.ng.core.entity.metadata.TemplateMetadata;
import io.harness.ng.core.service.dto.DestinationServiceConfig;
import io.harness.ng.core.service.dto.SourceServiceConfig;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceGovernanceDataResponse;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.pms.rbac.NGResourceType;
import io.harness.rule.Owner;
import io.harness.utils.NGFeatureFlagHelperService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.util.Collections;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(CDC)
public class ServiceCloneHelperTest extends CategoryTest {
  @InjectMocks ServiceCloneHelper serviceCloneHelper;
  @Mock ServiceEntityService serviceEntityService;
  @Mock AccessControlClient accessControlClient;
  @Mock NGFeatureFlagHelperService featureFlagHelperService;
  @Mock OrgAndProjectValidationHelper orgAndProjectValidationHelper;

  private final String ACCOUNT_ID = "account_id";
  private final String ORG_IDENTIFIER = "orgId";
  private final String PROJ_IDENTIFIER = "projId";
  private final String IDENTIFIER = "identifier";
  private final String yaml = "service:\n"
      + "  name: identifier\n"
      + "  identifier: identifier\n"
      + "  orgIdentifier: orgId\n"
      + "  projectIdentifier: projId\n"
      + "  serviceDefinition:\n"
      + "    spec: {}\n"
      + "    type: Kubernetes\n";
  ServiceEntity entity;
  ServiceGovernanceDataResponse serviceGovernanceDataResponse;
  ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
  private AutoCloseable mocks;
  @Before
  public void setup() {
    mocks = MockitoAnnotations.openMocks(this);
    entity = ServiceEntity.builder()
                 .accountId(ACCOUNT_ID)
                 .orgIdentifier(ORG_IDENTIFIER)
                 .projectIdentifier(PROJ_IDENTIFIER)
                 .identifier(IDENTIFIER)
                 .version(1L)
                 .description("")
                 .gitOpsEnabled(true)
                 .yaml(yaml)
                 .templateMetadata(Collections.singletonList(TemplateMetadata.builder()
                                                                 .templateRef("someTemplate")
                                                                 .templateVersion("v1")
                                                                 .branchName("main")
                                                                 .build()))
                 .build();
    serviceGovernanceDataResponse = ServiceGovernanceDataResponse.builder().service(entity).build();
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = LOVISH_BANSAL)
  @Category(UnitTests.class)
  public void testCloneService() throws JsonProcessingException {
    Optional<ServiceEntity> optionalServiceEntity = Optional.of(entity);
    when(serviceEntityService.get(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(optionalServiceEntity);
    when(serviceEntityService.create(any())).thenReturn(serviceGovernanceDataResponse);

    DestinationServiceConfig destinationServiceConfig =
        DestinationServiceConfig.builder().description("desc").serviceName("s1").serviceIdentifier("s1").build();

    SourceServiceConfig sourceServiceConfig = SourceServiceConfig.builder()
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .serviceIdentifier(IDENTIFIER)
                                                  .build();

    ServiceEntity clonedEntity =
        serviceCloneHelper.cloneService(ACCOUNT_ID, sourceServiceConfig, destinationServiceConfig, null).getService();
    assertThat(clonedEntity.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(clonedEntity.getOrgIdentifier()).isNull();
    assertThat(clonedEntity.getProjectIdentifier()).isNull();
    assertThat(clonedEntity.getName()).isEqualTo("s1");
    assertThat(clonedEntity.getIdentifier()).isEqualTo("s1");
    assertThat(clonedEntity.getDescription()).isEqualTo("desc");
    assertThat(clonedEntity.getTags()).isEmpty();
    assertThat(clonedEntity.getGitOpsEnabled()).isTrue();
    assertThat(clonedEntity.getTemplateMetadata()).isNull();

    String clonedEntityYaml = clonedEntity.getYaml();
    JsonNode clonedYamlJsonNode = objectMapper.readTree(clonedEntityYaml);
    JsonNode sourceYamlJsonNode = objectMapper.readTree(yaml);

    assertThat(clonedYamlJsonNode.get("service").findValue("name").asText()).isEqualTo("s1");
    assertThat(clonedYamlJsonNode.get("service").findValue("identifier").asText()).isEqualTo("s1");
    assertThat(clonedYamlJsonNode.get("service").findValue("description").asText()).isEqualTo("desc");
    assertThat(clonedYamlJsonNode.get("service").findValue("serviceDefinition"))
        .isEqualTo(sourceYamlJsonNode.get("service").findValue("serviceDefinition"));

    verify(accessControlClient, times(1))
        .checkForAccessOrThrow(ResourceScope.of(ACCOUNT_ID, destinationServiceConfig.getOrgIdentifier(),
                                   destinationServiceConfig.getProjectIdentifier()),
            Resource.of(NGResourceType.SERVICE, null), SERVICE_CREATE_PERMISSION);
    verify(orgAndProjectValidationHelper, times(1))
        .checkThatTheOrganizationAndProjectExists(
            destinationServiceConfig.getOrgIdentifier(), destinationServiceConfig.getProjectIdentifier(), ACCOUNT_ID);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testCloneServiceToRemoteWithoutConnectorRefThrows() {
    Optional<ServiceEntity> optionalServiceEntity = Optional.of(entity);
    when(serviceEntityService.get(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(optionalServiceEntity);

    SourceServiceConfig sourceServiceConfig = SourceServiceConfig.builder()
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .serviceIdentifier(IDENTIFIER)
                                                  .build();
    DestinationServiceConfig destinationServiceConfig = DestinationServiceConfig.builder()
                                                            .serviceName("s1")
                                                            .serviceIdentifier("s1")
                                                            .storeType(StoreType.REMOTE)
                                                            .repoName("some-repo")
                                                            .branch("main")
                                                            .filePath(".harness/s1.yaml")
                                                            .build(); // connectorRef absent, isHarnessCodeRepo false

    assertThatThrownBy(
        () -> serviceCloneHelper.cloneService(ACCOUNT_ID, sourceServiceConfig, destinationServiceConfig, null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("connector ref not present in the request");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testCloneServiceToHarnessCodeRepoWithoutConnectorRefDoesNotThrow() {
    Optional<ServiceEntity> optionalServiceEntity = Optional.of(entity);
    when(serviceEntityService.get(any(), any(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(optionalServiceEntity);
    when(serviceEntityService.create(any())).thenReturn(serviceGovernanceDataResponse);

    SourceServiceConfig sourceServiceConfig = SourceServiceConfig.builder()
                                                  .orgIdentifier(ORG_IDENTIFIER)
                                                  .projectIdentifier(PROJ_IDENTIFIER)
                                                  .serviceIdentifier(IDENTIFIER)
                                                  .build();
    DestinationServiceConfig destinationServiceConfig = DestinationServiceConfig.builder()
                                                            .serviceName("s1")
                                                            .serviceIdentifier("s1")
                                                            .storeType(StoreType.REMOTE)
                                                            .repoName("account.my-repo")
                                                            .branch("main")
                                                            .filePath(".harness/s1.yaml")
                                                            .isHarnessCodeRepo(true)
                                                            .build(); // connectorRef absent, isHarnessCodeRepo true

    // Should not throw InvalidRequestException for missing connectorRef
    serviceCloneHelper.cloneService(ACCOUNT_ID, sourceServiceConfig, destinationServiceConfig, null);
  }
}