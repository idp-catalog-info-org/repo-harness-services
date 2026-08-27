/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.clients.integrationmanager;

import static io.harness.annotations.dev.HarnessTeam.IDP;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;

/**
 * Helper class to route IntegrationManagerClient calls based on scope.
 * Automatically selects between account-scoped, org-scoped, and project-scoped APIs based on
 * whether orgIdentifier and projectIdentifier are provided (blank = null or empty).
 */
@Singleton
@OwnedBy(IDP)
public class IntegrationManagerClientHelper {
  private final IntegrationManagerClient client;
  private final String integrationManagerIdpMappingId;

  @Inject
  public IntegrationManagerClientHelper(
      IntegrationManagerClient client, @Named("integrationManagerIdpMappingId") String integrationManagerIdpMappingId) {
    this.client = client;
    this.integrationManagerIdpMappingId = integrationManagerIdpMappingId;
  }

  /**
   * List account-scoped integration configs filtered by integration type.
   */
  public Call<List<TypesIntegrationConfig>> listIntegrationConfigs(String harnessAccount, String accountIdentifier,
      List<TypesIntegrationConfig.EnumIntegrationType> integrationTypes, boolean allChildScope, boolean enabled) {
    return client.listIntegrationConfigs(harnessAccount, accountIdentifier, integrationTypes, allChildScope, enabled);
  }

  /**
   * List entity mappings with automatic scope routing
   */
  public Call<List<TypesEntityMapping>> listEntityMappings(String harnessAccount, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String integrationConfigIdentifier, String mappingId) {
    if (isScopedToProject(orgIdentifier, projectIdentifier)) {
      return client.listEntityMappingsForProject(
          harnessAccount, accountIdentifier, orgIdentifier, projectIdentifier, integrationConfigIdentifier, mappingId);
    }
    if (isScopedToOrg(orgIdentifier, projectIdentifier)) {
      return client.listEntityMappingsForOrg(
          harnessAccount, accountIdentifier, orgIdentifier, integrationConfigIdentifier, mappingId);
    }
    return client.listEntityMappings(harnessAccount, accountIdentifier, integrationConfigIdentifier, mappingId);
  }

  /**
   * Subscribe to entity updates with automatic scope routing
   */
  public Call<EntitySubscribeEntitiesResponse> subscribeToEntityUpdates(String harnessAccount, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String integrationConfigIdentifier,
      OpenapiSubscribeEntitiesRequest request) {
    if (isScopedToProject(orgIdentifier, projectIdentifier)) {
      return client.subscribeToEntityUpdatesForProject(
          harnessAccount, accountIdentifier, orgIdentifier, projectIdentifier, integrationConfigIdentifier, request);
    }
    if (isScopedToOrg(orgIdentifier, projectIdentifier)) {
      return client.subscribeToEntityUpdatesForOrg(
          harnessAccount, accountIdentifier, orgIdentifier, integrationConfigIdentifier, request);
    }
    return client.subscribeToEntityUpdates(harnessAccount, accountIdentifier, integrationConfigIdentifier, request);
  }

  /**
   * Unsubscribe from entity updates with automatic scope routing
   */
  public Call<EntitySubscribeEntitiesResponse> unsubscribeFromEntityUpdates(String harnessAccount,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String integrationConfigIdentifier,
      OpenapiSubscribeEntitiesRequest request) {
    if (isScopedToProject(orgIdentifier, projectIdentifier)) {
      return client.unsubscribeFromEntityUpdatesForProject(
          harnessAccount, accountIdentifier, orgIdentifier, projectIdentifier, integrationConfigIdentifier, request);
    }
    if (isScopedToOrg(orgIdentifier, projectIdentifier)) {
      return client.unsubscribeFromEntityUpdatesForOrg(
          harnessAccount, accountIdentifier, orgIdentifier, integrationConfigIdentifier, request);
    }
    return client.unsubscribeFromEntityUpdates(harnessAccount, accountIdentifier, integrationConfigIdentifier, request);
  }

  /**
   * Get mapped entities with automatic scope routing
   */
  public Call<EntityMappedEntityResponseObject> getMappedEntities(String harnessAccount, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String integrationConfigIdentifier, String mappingId,
      boolean detailed, String sortBy, String order, int page, int limit, String searchTerm,
      OpenapiGetMappedEntitiesRequest request, boolean unsubscribedOnly) {
    if (isScopedToProject(orgIdentifier, projectIdentifier)) {
      return client.getMappedEntitiesForProject(harnessAccount, accountIdentifier, orgIdentifier, projectIdentifier,
          integrationConfigIdentifier, mappingId, detailed, sortBy, order, page, limit, searchTerm, unsubscribedOnly,
          request);
    }
    if (isScopedToOrg(orgIdentifier, projectIdentifier)) {
      return client.getMappedEntitiesForOrg(harnessAccount, accountIdentifier, orgIdentifier,
          integrationConfigIdentifier, mappingId, detailed, sortBy, order, page, limit, searchTerm, unsubscribedOnly,
          request);
    }
    return client.getMappedEntities(harnessAccount, accountIdentifier, integrationConfigIdentifier, mappingId, detailed,
        sortBy, order, page, limit, searchTerm, unsubscribedOnly, request);
  }

  /**
   * Get mapped entities using a raw source offset with automatic scope routing.
   */
  public Call<EntityMappedEntityResponseObject> getMappedEntitiesByOffset(String harnessAccount,
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String integrationConfigIdentifier,
      String mappingId, boolean detailed, String sortBy, String order, int offset, int limit, String searchTerm,
      OpenapiGetMappedEntitiesRequest request, boolean unsubscribedOnly) {
    if (isScopedToProject(orgIdentifier, projectIdentifier)) {
      return client.getMappedEntitiesByOffsetForProject(harnessAccount, accountIdentifier, orgIdentifier,
          projectIdentifier, integrationConfigIdentifier, mappingId, detailed, sortBy, order, offset, limit, searchTerm,
          unsubscribedOnly, request);
    }
    if (isScopedToOrg(orgIdentifier, projectIdentifier)) {
      return client.getMappedEntitiesByOffsetForOrg(harnessAccount, accountIdentifier, orgIdentifier,
          integrationConfigIdentifier, mappingId, detailed, sortBy, order, offset, limit, searchTerm, unsubscribedOnly,
          request);
    }
    return client.getMappedEntitiesByOffset(harnessAccount, accountIdentifier, integrationConfigIdentifier, mappingId,
        detailed, sortBy, order, offset, limit, searchTerm, unsubscribedOnly, request);
  }

  /**
   * Get integration config with automatic scope routing
   */
  public Call<TypesIntegrationConfig> getIntegrationConfig(String harnessAccount, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String integrationConfigIdentifier) {
    if (isScopedToProject(orgIdentifier, projectIdentifier)) {
      return client.getIntegrationConfigForProject(
          harnessAccount, accountIdentifier, orgIdentifier, projectIdentifier, integrationConfigIdentifier);
    }
    if (isScopedToOrg(orgIdentifier, projectIdentifier)) {
      return client.getIntegrationConfigForOrg(
          harnessAccount, accountIdentifier, orgIdentifier, integrationConfigIdentifier);
    }
    return client.getIntegrationConfig(harnessAccount, accountIdentifier, integrationConfigIdentifier);
  }

  /**
   * Update integration config with automatic scope routing
   */
  public Call<TypesIntegrationConfig> updateIntegrationConfig(String harnessAccount, String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String integrationConfigIdentifier,
      OpenapiUpdateIntegrationConfigRequest request) {
    if (isScopedToProject(orgIdentifier, projectIdentifier)) {
      return client.updateIntegrationConfigForProject(
          harnessAccount, accountIdentifier, orgIdentifier, projectIdentifier, integrationConfigIdentifier, request);
    }
    if (isScopedToOrg(orgIdentifier, projectIdentifier)) {
      return client.updateIntegrationConfigForOrg(
          harnessAccount, accountIdentifier, orgIdentifier, integrationConfigIdentifier, request);
    }
    return client.updateIntegrationConfig(harnessAccount, accountIdentifier, integrationConfigIdentifier, request);
  }

  public String getIntegrationManagerIdpMappingId() {
    return integrationManagerIdpMappingId;
  }

  private boolean isScopedToProject(String orgIdentifier, String projectIdentifier) {
    return StringUtils.isNotBlank(orgIdentifier) && StringUtils.isNotBlank(projectIdentifier);
  }

  private boolean isScopedToOrg(String orgIdentifier, String projectIdentifier) {
    return StringUtils.isNotBlank(orgIdentifier) && StringUtils.isBlank(projectIdentifier);
  }
}
