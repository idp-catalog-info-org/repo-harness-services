/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.deployable.resources;

import static io.harness.rule.OwnerRule.HARSHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.beans.ScopeInfo;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.deployable.beans.DeployableDataResponse;
import io.harness.ng.core.deployable.entity.DeployableEntity;
import io.harness.ng.core.deployable.services.DeployableEntityService;
import io.harness.ng.core.deployable.services.impl.DeployableYamlSchemaHelper;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.ng.core.utils.OrgAndProjectValidationHelper;
import io.harness.rule.Owner;
import io.harness.spec.server.ng.v1.model.Deployable;
import io.harness.spec.server.ng.v1.model.DeployableCreateRequest;
import io.harness.spec.server.ng.v1.model.DeployableMetadata;
import io.harness.spec.server.ng.v1.model.DeployableType;
import io.harness.spec.server.ng.v1.model.DeployableUpdateRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public class AbstractDeployablesApiImplTest extends CategoryTest {
  @Mock private DeployableEntityService deployableEntityService;
  @Mock private AccessControlClient accessControlClient;
  @Mock private OrgAndProjectValidationHelper orgAndProjectValidationHelper;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private DeployableYamlSchemaHelper deployableYamlSchemaHelper;

  @InjectMocks private AbstractDeployablesApiImpl abstractDeployablesApi;

  private static final String ACCOUNT_ID = "accountId";
  private static final String ORG_ID = "orgId";
  private static final String PROJECT_ID = "projectId";
  private static final String DEPLOYABLE_ID = "deployableId";
  private static final String DEPLOYABLE_NAME = "deployableName";
  private static final String DEPLOYABLE_TYPE = "SalesforceDxProject";
  private static final String YAML_CONTENT = "deployable:\n  identifier: deployableId\n  name: deployableName\n  type: "
      + "SalesforceDxProject\n  spec:\n    projectPath: force-app";

  private ScopeInfo scopeInfo;
  private DeployableEntity deployableEntity;
  private DeployableCreateRequest createRequest;
  private DeployableUpdateRequest updateRequest;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    scopeInfo = ScopeInfo.builder()
                    .accountIdentifier(ACCOUNT_ID)
                    .orgIdentifier(ORG_ID)
                    .projectIdentifier(PROJECT_ID)
                    .uniqueId("uniqueId")
                    .build();

    deployableEntity = DeployableEntity.builder()
                           .accountIdentifier(ACCOUNT_ID)
                           .orgIdentifier(ORG_ID)
                           .projectIdentifier(PROJECT_ID)
                           .identifier(DEPLOYABLE_ID)
                           .name(DEPLOYABLE_NAME)
                           .type(DEPLOYABLE_TYPE)
                           .yaml(YAML_CONTENT)
                           .build();

    createRequest = new DeployableCreateRequest();
    createRequest.setIdentifier(DEPLOYABLE_ID);
    createRequest.setName(DEPLOYABLE_NAME);
    createRequest.setType(DeployableType.SALESFORCEDXPROJECT);
    createRequest.setYaml(YAML_CONTENT);

    updateRequest = new DeployableUpdateRequest();
    updateRequest.setIdentifier(DEPLOYABLE_ID);
    updateRequest.setName(DEPLOYABLE_NAME);
    updateRequest.setType(DeployableType.SALESFORCEDXPROJECT);
    updateRequest.setYaml(YAML_CONTENT);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testCreateDeployableEntity_Success() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    DeployableDataResponse dataResponse = DeployableDataResponse.builder().deployable(deployableEntity).build();
    when(deployableEntityService.create(any(DeployableEntity.class), eq(scopeInfo))).thenReturn(dataResponse);

    Response response = abstractDeployablesApi.createDeployableEntity(createRequest, ORG_ID, PROJECT_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.CREATED.getStatusCode());
    assertThat(response.getEntity()).isInstanceOf(Deployable.class);
    verify(deployableYamlSchemaHelper).validateSchema(ACCOUNT_ID, YAML_CONTENT);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testCreateDeployableEntity_NullRequest() {
    assertThatThrownBy(() -> abstractDeployablesApi.createDeployableEntity(null, ORG_ID, PROJECT_ID, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No request body sent in the API");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetDeployableEntity_Success() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(deployableEntityService.get(scopeInfo, DEPLOYABLE_ID, false)).thenReturn(Optional.of(deployableEntity));

    Response response = abstractDeployablesApi.getDeployableEntity(ORG_ID, PROJECT_ID, DEPLOYABLE_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isInstanceOf(Deployable.class);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetDeployableEntity_NotFound() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(deployableEntityService.get(scopeInfo, DEPLOYABLE_ID, false)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> abstractDeployablesApi.getDeployableEntity(ORG_ID, PROJECT_ID, DEPLOYABLE_ID, ACCOUNT_ID))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Deployable with identifier [" + DEPLOYABLE_ID + "] not found");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testUpdateDeployableEntity_Success() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    DeployableDataResponse dataResponse = DeployableDataResponse.builder().deployable(deployableEntity).build();
    when(deployableEntityService.update(any(DeployableEntity.class), eq(scopeInfo))).thenReturn(dataResponse);

    Response response =
        abstractDeployablesApi.updateDeployableEntity(updateRequest, ORG_ID, PROJECT_ID, DEPLOYABLE_ID, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    assertThat(response.getEntity()).isInstanceOf(Deployable.class);
    verify(deployableYamlSchemaHelper).validateSchema(ACCOUNT_ID, YAML_CONTENT);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testUpdateDeployableEntity_ValidationErrors() {
    assertThatThrownBy(
        () -> abstractDeployablesApi.updateDeployableEntity(null, ORG_ID, PROJECT_ID, DEPLOYABLE_ID, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("No request body sent in the API");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testDeleteDeployableEntity() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    when(deployableEntityService.delete(scopeInfo, DEPLOYABLE_ID, null, false)).thenReturn(true);

    Response response = abstractDeployablesApi.deleteDeployableEntity(ORG_ID, PROJECT_ID, DEPLOYABLE_ID, ACCOUNT_ID);
    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());

    when(deployableEntityService.delete(scopeInfo, DEPLOYABLE_ID, null, false)).thenReturn(false);
    assertThatThrownBy(
        () -> abstractDeployablesApi.deleteDeployableEntity(ORG_ID, PROJECT_ID, DEPLOYABLE_ID, ACCOUNT_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("could not be deleted");
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetDeployableEntities_Success() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    DeployableEntity entity1 = DeployableEntity.builder().identifier("deployable1").name("Deployable 1").build();
    DeployableEntity entity2 = DeployableEntity.builder().identifier("deployable2").name("Deployable 2").build();
    Page<DeployableEntity> page = new PageImpl<>(Arrays.asList(entity1, entity2));
    when(deployableEntityService.list(any(Criteria.class), any(Pageable.class))).thenReturn(page);

    Response response = abstractDeployablesApi.getDeployableEntities(ORG_ID, PROJECT_ID, 0, 10, null, null, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
    List<DeployableMetadata> result = (List<DeployableMetadata>) response.getEntity();
    assertThat(result).hasSize(2);
  }

  @Test
  @Owner(developers = HARSHIT)
  @Category(UnitTests.class)
  public void testGetDeployableEntities_WithFilters() {
    when(scopeInfoService.getScopeInfo(ACCOUNT_ID, ORG_ID, PROJECT_ID)).thenReturn(scopeInfo);
    Page<DeployableEntity> page = new PageImpl<>(Arrays.asList(deployableEntity));
    when(deployableEntityService.list(any(Criteria.class), any(Pageable.class))).thenReturn(page);

    abstractDeployablesApi.getDeployableEntities(ORG_ID, PROJECT_ID, 0, 10, "test", null, ACCOUNT_ID);
    abstractDeployablesApi.getDeployableEntities(ORG_ID, PROJECT_ID, 0, 10, null, DEPLOYABLE_TYPE, ACCOUNT_ID);
    Response response =
        abstractDeployablesApi.getDeployableEntities(ORG_ID, PROJECT_ID, 0, 10, "test", DEPLOYABLE_TYPE, ACCOUNT_ID);

    assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
  }
}
