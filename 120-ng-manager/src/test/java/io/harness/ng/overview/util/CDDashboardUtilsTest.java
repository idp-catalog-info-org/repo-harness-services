/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.overview.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.service.dto.ServiceDashboardResponseDTO;
import io.harness.ng.core.service.dto.ServiceResponseDTO;
import io.harness.ng.core.service.entity.ServiceFilterPropertiesDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.spec.server.ng.v1.model.Service;
import io.harness.spec.server.ng.v1.model.ServiceDashboardResponse;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class CDDashboardUtilsTest extends CategoryTest {
  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String SERVICE_ID = "serviceId";
  private static final String SERVICE_NAME = "serviceName";

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testCreateFilterProperties_WithAllParameters() {
    List<String> serviceIdentifiers = Arrays.asList("svc1", "svc2");
    List<String> serviceNames = Arrays.asList("Service 1", "Service 2");
    List<String> tags = Arrays.asList("env:prod", "team:backend", "solo");
    List<String> serviceTypes = Arrays.asList("Kubernetes", "ECS");

    ServiceFilterPropertiesDTO result =
        CDDashboardUtils.createFilterProperties(serviceIdentifiers, serviceNames, tags, serviceTypes);

    assertThat(result).isNotNull();
    assertThat(result.getServiceIdentifiers()).containsExactlyElementsOf(serviceIdentifiers);
    assertThat(result.getServiceNames()).containsExactlyElementsOf(serviceNames);
    assertThat(result.getServiceTypes()).containsExactlyElementsOf(serviceTypes);
    assertThat(result.getTags()).hasSize(3);
    assertThat(result.getTags().get("env")).isEqualTo("prod");
    assertThat(result.getTags().get("team")).isEqualTo("backend");
    assertThat(result.getTags().get("solo")).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testCreateFilterProperties_WithEmptyParameters() {
    ServiceFilterPropertiesDTO result = CDDashboardUtils.createFilterProperties(null, null, null, null);
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testCreateFilterProperties_WithEmptyLists() {
    ServiceFilterPropertiesDTO result = CDDashboardUtils.createFilterProperties(
        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testCreateFilterProperties_WithOnlyServiceIdentifiers() {
    List<String> serviceIdentifiers = Arrays.asList("svc1", "svc2");

    ServiceFilterPropertiesDTO result = CDDashboardUtils.createFilterProperties(serviceIdentifiers, null, null, null);

    assertThat(result).isNotNull();
    assertThat(result.getServiceIdentifiers()).containsExactlyElementsOf(serviceIdentifiers);
    assertThat(result.getServiceNames()).isNull();
    assertThat(result.getServiceTypes()).isNull();
    assertThat(result.getTags()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testCreateFilterProperties_WithOnlyTags() {
    List<String> tags = Arrays.asList("env:prod", "region:us-east-1");

    ServiceFilterPropertiesDTO result = CDDashboardUtils.createFilterProperties(null, null, tags, null);

    assertThat(result).isNotNull();
    assertThat(result.getTags()).hasSize(2);
    assertThat(result.getTags().get("env")).isEqualTo("prod");
    assertThat(result.getTags().get("region")).isEqualTo("us-east-1");
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testCreateFilterProperties_WithComplexTags() {
    // Test tags with multiple colons - splits on first colon only
    List<String> tags = Arrays.asList("url:http", "key:value");

    ServiceFilterPropertiesDTO result = CDDashboardUtils.createFilterProperties(null, null, tags, null);

    assertThat(result).isNotNull();
    assertThat(result.getTags()).hasSize(2);
    assertThat(result.getTags().get("url")).isEqualTo("http");
    assertThat(result.getTags().get("key")).isEqualTo("value");
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testMapToServiceDashboardResponseList_WithEmptyList() {
    List<ServiceDashboardResponse> result = CDDashboardUtils.mapToServiceDashaboardResponseList(null);
    assertThat(result).isEmpty();

    result = CDDashboardUtils.mapToServiceDashaboardResponseList(Collections.emptyList());
    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testMapToServiceDashboardResponseList_WithValidData() {
    ServiceResponseDTO serviceResponseDTO = ServiceResponseDTO.builder()
                                                .accountId(ACCOUNT_ID)
                                                .orgIdentifier(ORG_ID)
                                                .projectIdentifier(PROJECT_ID)
                                                .identifier(SERVICE_ID)
                                                .name(SERVICE_NAME)
                                                .description("Test Service")
                                                .yaml("yaml content")
                                                .tags(createTags("env", "prod"))
                                                .build();

    ServiceDashboardResponseDTO dashboardResponseDTO = ServiceDashboardResponseDTO.builder()
                                                           .service(serviceResponseDTO)
                                                           .createdAt(1000L)
                                                           .lastModifiedAt(2000L)
                                                           .deploymentTypeList(Set.of("Kubernetes", "ECS"))
                                                           .build();

    List<ServiceDashboardResponse> result =
        CDDashboardUtils.mapToServiceDashaboardResponseList(Collections.singletonList(dashboardResponseDTO));

    assertThat(result).hasSize(1);
    ServiceDashboardResponse response = result.get(0);
    assertThat(response.getCreatedAt()).isEqualTo(1000L);
    assertThat(response.getLastModifiedAt()).isEqualTo(2000L);
    assertThat(response.getDeploymentTypes()).containsExactlyInAnyOrder("Kubernetes", "ECS");
    assertThat(response.getService()).isNotNull();
    assertThat(response.getService().getIdentifier()).isEqualTo(SERVICE_ID);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testMapToServiceDashboardResponse() {
    ServiceResponseDTO serviceResponseDTO = ServiceResponseDTO.builder()
                                                .accountId(ACCOUNT_ID)
                                                .orgIdentifier(ORG_ID)
                                                .projectIdentifier(PROJECT_ID)
                                                .identifier(SERVICE_ID)
                                                .name(SERVICE_NAME)
                                                .description("Test Service")
                                                .yaml("yaml content")
                                                .tags(createTags("env", "prod"))
                                                .build();

    ServiceDashboardResponseDTO dashboardResponseDTO = ServiceDashboardResponseDTO.builder()
                                                           .service(serviceResponseDTO)
                                                           .createdAt(1000L)
                                                           .lastModifiedAt(2000L)
                                                           .deploymentTypeList(Set.of("Kubernetes"))
                                                           .build();

    ServiceDashboardResponse result = CDDashboardUtils.mapToServiceDashboardResponse(dashboardResponseDTO);

    assertThat(result).isNotNull();
    assertThat(result.getCreatedAt()).isEqualTo(1000L);
    assertThat(result.getLastModifiedAt()).isEqualTo(2000L);
    assertThat(result.getDeploymentTypes()).containsExactly("Kubernetes");
    assertThat(result.getService()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testMapToService() {
    Map<String, String> tags = createTags("env", "prod", "team", "backend");
    ServiceResponseDTO serviceResponseDTO = ServiceResponseDTO.builder()
                                                .accountId(ACCOUNT_ID)
                                                .orgIdentifier(ORG_ID)
                                                .projectIdentifier(PROJECT_ID)
                                                .identifier(SERVICE_ID)
                                                .name(SERVICE_NAME)
                                                .description("Test Service Description")
                                                .yaml("yaml: content")
                                                .tags(tags)
                                                .build();

    Service result = CDDashboardUtils.mapToService(serviceResponseDTO);

    assertThat(result).isNotNull();
    assertThat(result.getAccount()).isEqualTo(ACCOUNT_ID);
    assertThat(result.getOrg()).isEqualTo(ORG_ID);
    assertThat(result.getProject()).isEqualTo(PROJECT_ID);
    assertThat(result.getIdentifier()).isEqualTo(SERVICE_ID);
    assertThat(result.getName()).isEqualTo(SERVICE_NAME);
    assertThat(result.getDescription()).isEqualTo("Test Service Description");
    assertThat(result.getYaml()).isEqualTo("yaml: content");
    assertThat(result.getTags()).isEqualTo(tags);
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testMapToService_WithNullOptionalFields() {
    ServiceResponseDTO serviceResponseDTO = ServiceResponseDTO.builder()
                                                .accountId(ACCOUNT_ID)
                                                .orgIdentifier(ORG_ID)
                                                .projectIdentifier(PROJECT_ID)
                                                .identifier(SERVICE_ID)
                                                .name(SERVICE_NAME)
                                                .build();

    Service result = CDDashboardUtils.mapToService(serviceResponseDTO);

    assertThat(result).isNotNull();
    assertThat(result.getAccount()).isEqualTo(ACCOUNT_ID);
    assertThat(result.getIdentifier()).isEqualTo(SERVICE_ID);
    assertThat(result.getDescription()).isNull();
    assertThat(result.getYaml()).isNull();
    assertThat(result.getTags()).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.ABOSII)
  @Category(UnitTests.class)
  public void testMapToServiceDashboardResponse_WithEmptyDeploymentTypes() {
    ServiceResponseDTO serviceResponseDTO =
        ServiceResponseDTO.builder().accountId(ACCOUNT_ID).identifier(SERVICE_ID).name(SERVICE_NAME).build();

    ServiceDashboardResponseDTO dashboardResponseDTO = ServiceDashboardResponseDTO.builder()
                                                           .service(serviceResponseDTO)
                                                           .createdAt(1000L)
                                                           .lastModifiedAt(2000L)
                                                           .deploymentTypeList(Set.of())
                                                           .build();

    ServiceDashboardResponse result = CDDashboardUtils.mapToServiceDashboardResponse(dashboardResponseDTO);

    assertThat(result).isNotNull();
    assertThat(result.getDeploymentTypes()).isEmpty();
  }

  private Map<String, String> createTags(String... keyValuePairs) {
    Map<String, String> tags = new HashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      tags.put(keyValuePairs[i], keyValuePairs[i + 1]);
    }
    return tags;
  }
}
