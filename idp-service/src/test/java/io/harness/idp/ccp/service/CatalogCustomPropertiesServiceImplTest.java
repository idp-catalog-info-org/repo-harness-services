/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.ccp.service;

import static io.harness.idp.common.RbacConstants.IDP_ADVANCED_CONFIGURATION;
import static io.harness.idp.common.RbacConstants.IDP_ADVANCED_CONFIGURATION_EDIT;
import static io.harness.outbox.OutboxSDKConstants.OUTBOX_TRANSACTION_TEMPLATE;
import static io.harness.rule.OwnerRule.ROUNAK;
import static io.harness.rule.OwnerRule.VIGNESWARA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.accesscontrol.AccessControlClient;
import io.harness.accesscontrol.acl.api.Principal;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.principals.PrincipalType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.clients.BackstageResourceClient;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.service.BackstageService;
import io.harness.idp.catalog.entities.CatalogEntity;
import io.harness.idp.catalog.entities.InlineCatalogEntity;
import io.harness.idp.catalog.repositories.CatalogEntityRepository;
import io.harness.idp.catalog.utils.CatalogUtils;
import io.harness.idp.ccp.cache.SchemaCache;
import io.harness.idp.ccp.entities.CatalogCustomPropertyEntity;
import io.harness.idp.ccp.events.CatalogCustomPropertyCreateEvent;
import io.harness.idp.ccp.events.CatalogCustomPropertyDeleteEvent;
import io.harness.idp.ccp.events.CatalogCustomPropertyUpdateEvent;
import io.harness.idp.ccp.repositories.CatalogCustomPropertiesRepository;
import io.harness.idp.common.Constants;
import io.harness.idp.common.IdpCommonService;
import io.harness.idp.events.utils.ResourceLocker;
import io.harness.idp.namespace.beans.entity.NamespaceEntity;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.lock.interfaces.AcquiredLock;
import io.harness.lock.redis.RedisAcquiredLock;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.UserPrincipal;
import io.harness.spec.server.idp.v1.model.CustomPropertiesBase;
import io.harness.spec.server.idp.v1.model.CustomPropertyByEntityDeleteRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByEntityRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldDeleteRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldDeleteResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyByFieldResponse;
import io.harness.spec.server.idp.v1.model.CustomPropertyFilterDeleteRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyFilterRequest;
import io.harness.spec.server.idp.v1.model.CustomPropertyResponse;
import io.harness.spec.server.idp.v1.model.EntityValue;
import io.harness.spec.server.idp.v1.model.PropertyValue;
import io.harness.spec.server.idp.v1.model.ScorecardFilter;

import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class CatalogCustomPropertiesServiceImplTest extends CategoryTest {
  private static final String ADMIN_USER_ID = "lv0euRhKRCyiXWzS7pOg6g";
  public static final String TEST_ACCOUNT = "account";
  public static final String RELEASE_VERSION = "metadata.releaseVersion";
  public static final String TEAM_LEAD = "metadata.teamLead";
  public static final String TEST_ENTITY_REF1 = "component:default/delivery-service";
  private static final String TEST_NAME1 = "delivery-service";
  public static final String TEST_ENTITY_REF2 = "component:default/location-service";
  private static final String TEST_NAME2 = "location-service";
  AutoCloseable openMocks;
  @InjectMocks CatalogCustomPropertiesServiceImpl ccpService;
  @Mock CatalogCustomPropertiesRepository ccpRepository;
  @Mock BackstageService backstageService;
  @Mock BackstageResourceClient backstageResourceClient;
  @Mock SchemaCache schemaCache;
  @Mock @Named(OUTBOX_TRANSACTION_TEMPLATE) private TransactionTemplate transactionTemplate;
  @Mock OutboxService outboxService;
  @Captor ArgumentCaptor<CatalogCustomPropertyCreateEvent> catalogCustomPropertyCreateEventArgumentCaptor;
  @Captor ArgumentCaptor<CatalogCustomPropertyUpdateEvent> catalogCustomPropertyUpdateEventArgumentCaptor;
  @Captor ArgumentCaptor<CatalogCustomPropertyDeleteEvent> catalogCustomPropertyDeleteEventArgumentCaptor;
  @Mock NamespaceService namespaceService;
  @Mock AccessControlClient accessControlClient;
  @Mock private IdpCommonService idpCommonService;
  @Mock ResourceLocker resourceLocker;
  @Mock CatalogEntityRepository catalogEntityRepository;
  private static Principal principal = Principal.of(PrincipalType.USER, ADMIN_USER_ID);
  private static ResourceScope resourceScope = ResourceScope.of(TEST_ACCOUNT, null, null);
  private static Resource resource = Resource.of(IDP_ADVANCED_CONFIGURATION, null);

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testResolveEntitiesAndUpsertCustomPropertiesDryRun() {
    CustomPropertyFilterRequest request = mockRequest(RELEASE_VERSION);
    List<BackstageCatalogEntity> entities = mockEntities();
    List<String> entityRefs = List.of(TEST_ENTITY_REF1, TEST_ENTITY_REF2);

    when(backstageService.queryEntities(request.getFilter(), TEST_ACCOUNT, new ArrayList<>())).thenReturn(entities);
    when(backstageService.findAllByAccountIdentifierAndEntityRefs(TEST_ACCOUNT, entityRefs)).thenReturn(entities);
    when(ccpRepository.findByAccountIdentifierAndEntityRefInAndField(TEST_ACCOUNT, entityRefs, RELEASE_VERSION))
        .thenReturn(mockCustomPropertyEntities());
    when(schemaCache.get("component")).thenReturn("{}");
    MockedStatic<SecurityContextBuilder> mockSecurityContext = mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", TEST_ACCOUNT));
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT)).thenReturn(false);

    CustomPropertyByFieldResponse response =
        ccpService.resolveEntitiesAndUpsertCustomProperties(request, TEST_ACCOUNT, true);

    assertNotNull(response);
    assertEquals(RELEASE_VERSION, response.getProperty());
    assertEquals(1, (int) response.getEntitiesWithAdditions().getCount());
    assertEquals(1, response.getEntitiesWithAdditions().getEntityRefs().size());
    assertEquals(TEST_ENTITY_REF1, response.getEntitiesWithAdditions().getEntityRefs().get(0));
    assertEquals(1, (int) response.getEntitiesWithUpdates().getCount());
    assertEquals(1, response.getEntitiesWithUpdates().getEntityRefs().size());
    assertEquals(TEST_ENTITY_REF2, response.getEntitiesWithUpdates().getEntityRefs().get(0));
    verify(backstageResourceClient, never()).createOrUpdateCustomProperties(eq(TEST_ACCOUNT), any());
    mockSecurityContext.close();
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testResolveEntitiesAndUpsertCustomProperties() {
    CustomPropertyFilterRequest request = mockRequest(RELEASE_VERSION);
    List<BackstageCatalogEntity> entities = mockEntities();
    List<String> entityRefs = List.of(TEST_ENTITY_REF1, TEST_ENTITY_REF2);

    when(backstageService.queryEntities(request.getFilter(), TEST_ACCOUNT, new ArrayList<>())).thenReturn(entities);
    when(backstageService.findAllByAccountIdentifierAndEntityRefs(TEST_ACCOUNT, entityRefs)).thenReturn(entities);
    when(ccpRepository.findByAccountIdentifierAndEntityRefInAndField(TEST_ACCOUNT, entityRefs, RELEASE_VERSION))
        .thenReturn(mockCustomPropertyEntities());
    when(schemaCache.get("component")).thenReturn("{}");
    when(outboxService.save(any())).thenReturn(OutboxEvent.builder().build());
    MockedStatic<SecurityContextBuilder> mockSecurityContext = mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", TEST_ACCOUNT));
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT)).thenReturn(false);

    CustomPropertyByFieldResponse response =
        ccpService.resolveEntitiesAndUpsertCustomProperties(request, TEST_ACCOUNT, false);

    assertNotNull(response);
    assertEquals(RELEASE_VERSION, response.getProperty());
    assertEquals(1, (int) response.getEntitiesWithAdditions().getCount());
    assertEquals(1, (int) response.getEntitiesWithUpdates().getCount());
    verify(backstageResourceClient).createOrUpdateCustomProperties(eq(TEST_ACCOUNT), any());
    mockSecurityContext.close();
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testResolveEntitiesAndUpsertCustomPropertiesRestrictedFieldsShouldThrowException() {
    CustomPropertyFilterRequest request = mockRequest("metadata.name");
    ccpService.resolveEntitiesAndUpsertCustomProperties(request, TEST_ACCOUNT, true);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteCustomPropertiesDryRun() {
    CustomPropertyFilterDeleteRequest request = mockCustomPropertyFilterDeleteRequest();
    List<BackstageCatalogEntity> entities = mockEntities();
    List<String> entityRefs = List.of(TEST_ENTITY_REF1, TEST_ENTITY_REF2);

    when(backstageService.queryEntities(request.getFilter(), TEST_ACCOUNT, new ArrayList<>())).thenReturn(entities);
    when(backstageService.findAllByAccountIdentifierAndEntityRefs(TEST_ACCOUNT, entityRefs)).thenReturn(entities);
    when(ccpRepository.findByAccountIdentifierAndEntityRefInAndField(TEST_ACCOUNT, entityRefs, RELEASE_VERSION))
        .thenReturn(mockCustomPropertyEntities());
    when(schemaCache.get("component")).thenReturn("{}");
    MockedStatic<SecurityContextBuilder> mockSecurityContext = mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", TEST_ACCOUNT));
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT)).thenReturn(false);

    CustomPropertyByFieldDeleteResponse response = ccpService.deleteCustomProperties(request, TEST_ACCOUNT, true);

    assertNotNull(response);
    assertEquals(RELEASE_VERSION, response.getProperty());
    assertEquals(1, (int) response.getEntitiesWithDeletion().getCount());
    assertEquals(1, response.getEntitiesWithDeletion().getEntityRefs().size());
    verify(backstageResourceClient, never()).deleteCustomProperties(eq(TEST_ACCOUNT), any());
    mockSecurityContext.close();
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testDeleteCustomProperties() {
    CustomPropertyFilterDeleteRequest request = mockCustomPropertyFilterDeleteRequest();
    List<BackstageCatalogEntity> entities = mockEntities();
    List<String> entityRefs = List.of(TEST_ENTITY_REF1, TEST_ENTITY_REF2);

    when(backstageService.queryEntities(request.getFilter(), TEST_ACCOUNT, new ArrayList<>())).thenReturn(entities);
    when(backstageService.findAllByAccountIdentifierAndEntityRefs(TEST_ACCOUNT, entityRefs)).thenReturn(entities);
    when(ccpRepository.findByAccountIdentifierAndEntityRefInAndField(TEST_ACCOUNT, entityRefs, RELEASE_VERSION))
        .thenReturn(mockCustomPropertyEntities());
    when(schemaCache.get("component")).thenReturn("{}");
    MockedStatic<SecurityContextBuilder> mockSecurityContext = mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", TEST_ACCOUNT));
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT)).thenReturn(false);

    CustomPropertyByFieldDeleteResponse response = ccpService.deleteCustomProperties(request, TEST_ACCOUNT, false);

    assertNotNull(response);
    assertEquals(RELEASE_VERSION, response.getProperty());
    assertEquals(1, (int) response.getEntitiesWithDeletion().getCount());
    verify(backstageResourceClient).deleteCustomProperties(eq(TEST_ACCOUNT), any());
    mockSecurityContext.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testResolveCustomPropertiesForEntityWithSingleProperty() {
    CustomPropertyByEntityRequest request = mockCustomPropertyByEntityRequest(true);
    when(backstageService.findByAccountIdentifierAndEntityRef(anyString(), anyString()))
        .thenReturn(mockEntities().get(1));
    when(ccpRepository.findByAccountIdentifierAndEntityRefAndFieldIn(
             TEST_ACCOUNT, TEST_ENTITY_REF2, List.of(RELEASE_VERSION)))
        .thenReturn(mockCustomPropertyEntities());
    when(schemaCache.get("component")).thenReturn("{}");
    MockedStatic<SecurityContextBuilder> mockSecurityContext = mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", TEST_ACCOUNT));
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(
            eq(principal), eq(resourceScope), eq(resource), eq(IDP_ADVANCED_CONFIGURATION_EDIT), anyString());
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT)).thenReturn(false);

    CustomPropertyResponse response = ccpService.resolveCustomPropertiesForEntity(request, TEST_ACCOUNT, false);

    verify(outboxService).save(catalogCustomPropertyUpdateEventArgumentCaptor.capture());
    assertEquals(request.getValue(),
        catalogCustomPropertyUpdateEventArgumentCaptor.getValue().getNewEntity().getValue().replace("\"", ""));
    assertNotNull(response);
    assertEquals(CustomPropertyResponse.StatusEnum.SUCCESS, response.getStatus());
    assertEquals(
        "Property has been updated successfully for entity component:default/location-service", response.getMessage());
    mockSecurityContext.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testResolveCustomPropertiesForEntityWithMultipleProperties() {
    CustomPropertyByEntityRequest request = mockCustomPropertyByEntityRequest(false);
    when(backstageService.findByAccountIdentifierAndEntityRef(anyString(), anyString()))
        .thenReturn(mockEntities().get(1));
    when(ccpRepository.findByAccountIdentifierAndEntityRefAndFieldIn(
             TEST_ACCOUNT, TEST_ENTITY_REF2, List.of(RELEASE_VERSION)))
        .thenReturn(new ArrayList<>());
    when(schemaCache.get("component")).thenReturn("{}");
    MockedStatic<SecurityContextBuilder> mockSecurityContext = mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", TEST_ACCOUNT));
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(
            eq(principal), eq(resourceScope), eq(resource), eq(IDP_ADVANCED_CONFIGURATION_EDIT), anyString());
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT)).thenReturn(false);

    CustomPropertyResponse response = ccpService.resolveCustomPropertiesForEntity(request, TEST_ACCOUNT, false);
    verify(outboxService, times(2)).save(catalogCustomPropertyCreateEventArgumentCaptor.capture());
    assertEquals(request.getProperties().get(0).getValue(),
        catalogCustomPropertyCreateEventArgumentCaptor.getAllValues().get(0).getEntity().getValue().replace("\"", ""));
    assertEquals(request.getProperties().get(1).getValue(),
        catalogCustomPropertyCreateEventArgumentCaptor.getAllValues().get(1).getEntity().getValue().replace("\"", ""));
    assertNotNull(response);
    assertEquals(CustomPropertyResponse.StatusEnum.SUCCESS, response.getStatus());
    assertEquals("All 2 properties have been updated successfully for entity component:default/location-service",
        response.getMessage());
    mockSecurityContext.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteCustomPropertiesForEntityWithSingleProperty() {
    CustomPropertyByEntityDeleteRequest request = mockCustomPropertyByEntityDeleteRequest(true);
    when(ccpRepository.findByAccountIdentifierAndEntityRefAndFieldIn(
             TEST_ACCOUNT, TEST_ENTITY_REF2, List.of(RELEASE_VERSION)))
        .thenReturn(mockCustomPropertyEntities())
        .thenReturn(new ArrayList<>());
    MockedStatic<SecurityContextBuilder> mockSecurityContext = mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", TEST_ACCOUNT));
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT)).thenReturn(false);

    CustomPropertyResponse response = ccpService.deleteCustomPropertiesForEntity(request, TEST_ACCOUNT, false);

    verify(outboxService).save(catalogCustomPropertyDeleteEventArgumentCaptor.capture());
    assertEquals(
        request.getProperty(), catalogCustomPropertyDeleteEventArgumentCaptor.getValue().getEntity().getField());
    assertNotNull(response);
    assertEquals(CustomPropertyResponse.StatusEnum.SUCCESS, response.getStatus());
    assertEquals(
        "Property(s) [metadata.releaseVersion] deleted successfully for entity component:default/location-service",
        response.getMessage());

    response = ccpService.deleteCustomPropertiesForEntity(request, TEST_ACCOUNT, false);
    assertNotNull(response);
    assertEquals(CustomPropertyResponse.StatusEnum.ERROR, response.getStatus());
    assertEquals(
        "No property found to be deleted for entity component:default/location-service", response.getMessage());
    mockSecurityContext.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteCustomPropertiesForEntityWithMultipleProperty() {
    CustomPropertyByEntityDeleteRequest request = mockCustomPropertyByEntityDeleteRequest(false);
    when(ccpRepository.findByAccountIdentifierAndEntityRefAndFieldIn(
             TEST_ACCOUNT, TEST_ENTITY_REF2, List.of(RELEASE_VERSION, TEAM_LEAD)))
        .thenReturn(mockCustomPropertyEntities());
    MockedStatic<SecurityContextBuilder> mockSecurityContext = mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", TEST_ACCOUNT));
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT)).thenReturn(false);

    CustomPropertyResponse response = ccpService.deleteCustomPropertiesForEntity(request, TEST_ACCOUNT, false);
    verify(outboxService).save(catalogCustomPropertyDeleteEventArgumentCaptor.capture());
    assertEquals(request.getProperties().get(0),
        catalogCustomPropertyDeleteEventArgumentCaptor.getValue().getEntity().getField());
    assertNotNull(response);
    assertEquals(CustomPropertyResponse.StatusEnum.SUCCESS, response.getStatus());
    assertEquals(
        "Property(s) [metadata.releaseVersion] deleted successfully for entity component:default/location-service",
        response.getMessage());
    mockSecurityContext.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testResolveEntitiesForCustomPropertyWithSingleEntity() {
    CustomPropertyByFieldRequest request = mockCustomPropertyByFieldRequest(true);
    when(backstageService.findAllByAccountIdentifierAndEntityRefs(anyString(), anyList()))
        .thenReturn(List.of(mockEntities().get(1)));
    when(ccpRepository.findByAccountIdentifierAndEntityRefAndFieldIn(
             TEST_ACCOUNT, TEST_ENTITY_REF2, List.of(RELEASE_VERSION)))
        .thenReturn(mockCustomPropertyEntities());
    when(schemaCache.get("component")).thenReturn("{}");
    MockedStatic<SecurityContextBuilder> mockSecurityContext = mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", TEST_ACCOUNT));
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(
            eq(principal), eq(resourceScope), eq(resource), eq(IDP_ADVANCED_CONFIGURATION_EDIT), anyString());
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT)).thenReturn(false);

    CustomPropertyResponse response = ccpService.resolveEntitiesForCustomProperty(request, TEST_ACCOUNT, false);
    verify(outboxService).save(catalogCustomPropertyCreateEventArgumentCaptor.capture());
    assertEquals(request.getValue(),
        catalogCustomPropertyCreateEventArgumentCaptor.getValue().getEntity().getValue().replace("\"", ""));
    assertNotNull(response);
    assertEquals(CustomPropertyResponse.StatusEnum.SUCCESS, response.getStatus());
    assertEquals("Entity has been updated successfully with property metadata.releaseVersion", response.getMessage());
    mockSecurityContext.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testResolveEntitiesForCustomPropertyWithMultipleEntities() {
    CustomPropertyByFieldRequest request = mockCustomPropertyByFieldRequest(false);
    when(backstageService.findAllByAccountIdentifierAndEntityRefs(anyString(), anyList())).thenReturn(mockEntities());
    when(ccpRepository.findByAccountIdentifierAndEntityRefAndFieldIn(
             TEST_ACCOUNT, TEST_ENTITY_REF2, List.of(RELEASE_VERSION)))
        .thenReturn(mockCustomPropertyEntities());
    when(schemaCache.get("component")).thenReturn("{}");
    MockedStatic<SecurityContextBuilder> mockSecurityContext = mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", TEST_ACCOUNT));
    doNothing()
        .when(accessControlClient)
        .checkForAccessOrThrow(
            eq(principal), eq(resourceScope), eq(resource), eq(IDP_ADVANCED_CONFIGURATION_EDIT), anyString());
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT)).thenReturn(false);

    CustomPropertyResponse response = ccpService.resolveEntitiesForCustomProperty(request, TEST_ACCOUNT, false);
    verify(outboxService, times(2)).save(catalogCustomPropertyCreateEventArgumentCaptor.capture());
    assertEquals(request.getEntityRefs().get(0).getValue(),
        catalogCustomPropertyCreateEventArgumentCaptor.getAllValues().get(0).getEntity().getValue().replace("\"", ""));
    assertEquals(request.getEntityRefs().get(1).getValue(),
        catalogCustomPropertyCreateEventArgumentCaptor.getAllValues().get(1).getEntity().getValue().replace("\"", ""));
    assertNotNull(response);
    assertEquals(CustomPropertyResponse.StatusEnum.SUCCESS, response.getStatus());
    assertEquals(
        "All 2 entities have been updated successfully with property metadata.releaseVersion", response.getMessage());
    mockSecurityContext.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteEntitiesForCustomPropertyWithSingleEntity() {
    CustomPropertyByFieldDeleteRequest request = mockCustomPropertyByFieldDeleteRequest(true);
    when(ccpRepository.findByAccountIdentifierAndEntityRefInAndField(
             TEST_ACCOUNT, List.of(TEST_ENTITY_REF2), RELEASE_VERSION))
        .thenReturn(mockCustomPropertyEntities())
        .thenReturn(new ArrayList<>());
    MockedStatic<SecurityContextBuilder> mockSecurityContext = mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", TEST_ACCOUNT));
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT)).thenReturn(false);

    CustomPropertyResponse response = ccpService.deleteEntitiesForCustomProperty(request, TEST_ACCOUNT, false);
    verify(outboxService).save(catalogCustomPropertyDeleteEventArgumentCaptor.capture());
    assertEquals(
        request.getEntityRef(), catalogCustomPropertyDeleteEventArgumentCaptor.getValue().getEntity().getEntityRef());
    assertNotNull(response);
    assertEquals(CustomPropertyResponse.StatusEnum.SUCCESS, response.getStatus());
    assertEquals(
        "Entity(s) [component:default/location-service] deleted successfully for property metadata.releaseVersion",
        response.getMessage());

    response = ccpService.deleteEntitiesForCustomProperty(request, TEST_ACCOUNT, false);
    assertNotNull(response);
    assertEquals(CustomPropertyResponse.StatusEnum.ERROR, response.getStatus());
    assertEquals("No entity found to be deleted for property metadata.releaseVersion", response.getMessage());
    mockSecurityContext.close();
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testDeleteEntitiesForCustomPropertyWithMultipleEntities() {
    CustomPropertyByFieldDeleteRequest request = mockCustomPropertyByFieldDeleteRequest(false);
    when(ccpRepository.findByAccountIdentifierAndEntityRefInAndField(
             TEST_ACCOUNT, List.of(TEST_ENTITY_REF2, TEST_ENTITY_REF1), RELEASE_VERSION))
        .thenReturn(mockCustomPropertyEntities());
    MockedStatic<SecurityContextBuilder> mockSecurityContext = mockStatic(SecurityContextBuilder.class);
    mockSecurityContext.when(SecurityContextBuilder::getPrincipal)
        .thenReturn(new UserPrincipal(ADMIN_USER_ID, "admin@harness.io", "admin", TEST_ACCOUNT));
    when(idpCommonService.idpV2Enabled(TEST_ACCOUNT)).thenReturn(false);

    CustomPropertyResponse response = ccpService.deleteEntitiesForCustomProperty(request, TEST_ACCOUNT, false);
    verify(outboxService).save(catalogCustomPropertyDeleteEventArgumentCaptor.capture());
    assertEquals(request.getEntityRefs().get(0),
        catalogCustomPropertyDeleteEventArgumentCaptor.getValue().getEntity().getEntityRef());
    assertNotNull(response);
    assertEquals(CustomPropertyResponse.StatusEnum.SUCCESS, response.getStatus());
    assertEquals(
        "Entity(s) [component:default/location-service] deleted successfully for property metadata.releaseVersion",
        response.getMessage());
    mockSecurityContext.close();
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testToggleCustomPropertiesEnabledToDisabled() {
    when(namespaceService.getEntityForAccountIdentifier(TEST_ACCOUNT))
        .thenReturn(
            Optional.of(NamespaceEntity.builder()
                            .metadata(NamespaceEntity.Metadata.builder().catalogCustomPropertiesEnabled(true).build())
                            .build()));

    ccpService.toggleCustomProperties(TEST_ACCOUNT, false);

    verify(outboxService).save(any());
    verify(backstageResourceClient).updateCatalogMetadata(eq(TEST_ACCOUNT), any());
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testToggleCustomPropertiesInvalid() {
    when(namespaceService.getEntityForAccountIdentifier(TEST_ACCOUNT)).thenReturn(Optional.empty());
    ccpService.toggleCustomProperties(TEST_ACCOUNT, false);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testToggleCustomPropertiesDisabledToEnabled() {
    when(namespaceService.getEntityForAccountIdentifier(TEST_ACCOUNT))
        .thenReturn(
            Optional.of(NamespaceEntity.builder()
                            .metadata(NamespaceEntity.Metadata.builder().catalogCustomPropertiesEnabled(false).build())
                            .build()));

    ccpService.toggleCustomProperties(TEST_ACCOUNT, true);

    verify(outboxService).save(any());
    verify(backstageResourceClient).updateCatalogMetadata(eq(TEST_ACCOUNT), any());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testToggleCustomPropertiesEnabledToEnabled() {
    when(namespaceService.getEntityForAccountIdentifier(TEST_ACCOUNT))
        .thenReturn(
            Optional.of(NamespaceEntity.builder()
                            .metadata(NamespaceEntity.Metadata.builder().catalogCustomPropertiesEnabled(true).build())
                            .build()));

    ccpService.toggleCustomProperties(TEST_ACCOUNT, true);

    verify(outboxService, never()).save(any());
    verify(backstageResourceClient, never()).updateCatalogMetadata(eq(TEST_ACCOUNT), any());
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testToggleCustomPropertiesDisabledToDisabled() {
    when(namespaceService.getEntityForAccountIdentifier(TEST_ACCOUNT))
        .thenReturn(
            Optional.of(NamespaceEntity.builder()
                            .metadata(NamespaceEntity.Metadata.builder().catalogCustomPropertiesEnabled(false).build())
                            .build()));

    ccpService.toggleCustomProperties(TEST_ACCOUNT, false);

    verify(outboxService, never()).save(any());
    verify(backstageResourceClient, never()).updateCatalogMetadata(eq(TEST_ACCOUNT), any());
  }

  private List<BackstageCatalogEntity> mockEntities() {
    List<BackstageCatalogEntity> entities = new ArrayList<>();
    entities.add(BackstageCatalogComponentEntity.builder()
                     .entityUid(TEST_ENTITY_REF1)
                     .kind("component")
                     .metadata(Map.of("namespace", "default", "name", TEST_NAME1))
                     .spec(BackstageCatalogComponentEntity.Spec.builder()
                               .type("service")
                               .system(Collections.singletonList("delivery"))
                               .lifecycle("lifecycle")
                               .owner("harness_account_all_users")
                               .build())
                     .build());
    entities.add(BackstageCatalogComponentEntity.builder()
                     .entityUid(TEST_ENTITY_REF2)
                     .kind("component")
                     .metadata(Map.of("namespace", "default", "name", TEST_NAME2))
                     .spec(BackstageCatalogComponentEntity.Spec.builder()
                               .type("service")
                               .system(Collections.singletonList("delivery"))
                               .lifecycle("lifecycle")
                               .owner("harness_account_all_users")
                               .build())
                     .build());
    return entities;
  }

  private CustomPropertyFilterDeleteRequest mockCustomPropertyFilterDeleteRequest() {
    CustomPropertyFilterDeleteRequest request = new CustomPropertyFilterDeleteRequest();
    request.setProperty(RELEASE_VERSION);
    request.setFilter(new ScorecardFilter());
    return request;
  }

  private CustomPropertyByEntityRequest mockCustomPropertyByEntityRequest(boolean singleProperty) {
    CustomPropertyByEntityRequest request = new CustomPropertyByEntityRequest();
    if (singleProperty) {
      request.setProperty(RELEASE_VERSION);
      request.setValue("1.8.0");
    } else {
      List<PropertyValue> propertyValues = new ArrayList<>();
      PropertyValue propertyValue1 = new PropertyValue();
      propertyValue1.setProperty(RELEASE_VERSION);
      propertyValue1.setValue("1.9.1");
      propertyValues.add(propertyValue1);
      PropertyValue propertyValue2 = new PropertyValue();
      propertyValue2.setProperty(TEAM_LEAD);
      propertyValue2.setValue("John Doe");
      propertyValues.add(propertyValue2);
      request.setProperties(propertyValues);
    }
    request.setEntityRef(TEST_ENTITY_REF2);
    return request;
  }

  private CustomPropertyByEntityDeleteRequest mockCustomPropertyByEntityDeleteRequest(boolean singleProperty) {
    CustomPropertyByEntityDeleteRequest request = new CustomPropertyByEntityDeleteRequest();
    if (singleProperty) {
      request.setProperty(RELEASE_VERSION);
    } else {
      request.setProperties(List.of(RELEASE_VERSION, TEAM_LEAD));
    }
    request.setEntityRef(TEST_ENTITY_REF2);
    return request;
  }

  private CustomPropertyByFieldRequest mockCustomPropertyByFieldRequest(boolean singleEntity) {
    CustomPropertyByFieldRequest request = new CustomPropertyByFieldRequest();
    if (singleEntity) {
      request.setEntityRef(TEST_ENTITY_REF2);
      request.setValue("1.5.0");
    } else {
      List<EntityValue> entityValues = new ArrayList<>();
      EntityValue entityValue1 = new EntityValue();
      entityValue1.setEntityRef(TEST_ENTITY_REF1);
      entityValue1.setValue("1.8.0");
      entityValues.add(entityValue1);
      EntityValue entityValue2 = new EntityValue();
      entityValue2.setEntityRef(TEST_ENTITY_REF2);
      entityValue2.setValue("2.1.0");
      entityValues.add(entityValue2);
      request.setEntityRefs(entityValues);
    }
    request.setProperty(RELEASE_VERSION);
    return request;
  }

  private CustomPropertyByFieldDeleteRequest mockCustomPropertyByFieldDeleteRequest(boolean singleProperty) {
    CustomPropertyByFieldDeleteRequest request = new CustomPropertyByFieldDeleteRequest();
    if (singleProperty) {
      request.entityRef(TEST_ENTITY_REF2);
    } else {
      request.entityRefs(List.of(TEST_ENTITY_REF2, TEST_ENTITY_REF1));
    }
    request.property(RELEASE_VERSION);
    return request;
  }

  // Wiring smoke tests: the metadata.apis gate fires from every entry point.
  // The full truth-table lives in CatalogCustomPropertiesApiEndpointGateTest.

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void wiring_resolveEntitiesAndUpsert_metadataApisRejected() {
    CustomPropertyFilterRequest request = mockRequest("metadata.apis.specHash");
    ccpService.resolveEntitiesAndUpsertCustomProperties(request, TEST_ACCOUNT, true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void wiring_resolveCustomPropertiesForEntity_metadataApisRejected() {
    CustomPropertyByEntityRequest request = new CustomPropertyByEntityRequest();
    request.setEntityRef(TEST_ENTITY_REF1);
    request.setProperty("metadata.apis.paths");
    request.setValue("anything");
    ccpService.resolveCustomPropertiesForEntity(request, TEST_ACCOUNT, true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void wiring_resolveEntitiesForCustomProperty_metadataApisRejected() {
    // This previously-ungated write path is now closed.
    CustomPropertyByFieldRequest request = new CustomPropertyByFieldRequest();
    request.setProperty("metadata.apis");
    request.setEntityRef(TEST_ENTITY_REF1);
    request.setValue("anything");
    ccpService.resolveEntitiesForCustomProperty(request, TEST_ACCOUNT, true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void wiring_deleteCustomProperties_metadataApisRejected() {
    CustomPropertyFilterDeleteRequest request = new CustomPropertyFilterDeleteRequest();
    request.setProperty("metadata.apis.extractionStatus");
    request.setFilter(new ScorecardFilter());
    ccpService.deleteCustomProperties(request, TEST_ACCOUNT, true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void wiring_deleteCustomPropertiesForEntity_metadataApisRejected() {
    CustomPropertyByEntityDeleteRequest request = new CustomPropertyByEntityDeleteRequest();
    request.setEntityRef(TEST_ENTITY_REF1);
    request.setProperty("metadata.apis.lastCheckedAt");
    ccpService.deleteCustomPropertiesForEntity(request, TEST_ACCOUNT, true);
  }

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void wiring_deleteEntitiesForCustomProperty_metadataApisRejected() {
    CustomPropertyByFieldDeleteRequest request = new CustomPropertyByFieldDeleteRequest();
    request.setProperty("metadata.apis.specHash");
    request.setEntityRef(TEST_ENTITY_REF1);
    ccpService.deleteEntitiesForCustomProperty(request, TEST_ACCOUNT, true);
  }

  // Lost-update protection: acquireApiEntityLocks (which entities get locked and cleanup on failure)

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void lockHelper_apiEntity_acquiresLock() {
    CatalogEntity entity = apiEntity("e1", TEST_ACCOUNT);
    AcquiredLock<?> lock = RedisAcquiredLock.builder().build();
    when(resourceLocker.acquireLock(anyString(), anyLong(), anyLong())).thenAnswer(invocation -> lock);

    List<AcquiredLock<?>> acquired = ccpService.acquireApiEntityLocks(List.of(entity));

    assertThat(acquired).hasSize(1);
    verify(resourceLocker, times(1)).acquireLock(anyString(), anyLong(), anyLong());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void lockHelper_nonApiEntity_noLockAcquired() {
    CatalogEntity entity = nonApiEntity("e1", TEST_ACCOUNT, "component");

    List<AcquiredLock<?>> acquired = ccpService.acquireApiEntityLocks(List.of(entity));

    assertThat(acquired).isEmpty();
    verify(resourceLocker, never()).acquireLock(anyString(), anyLong(), anyLong());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void lockHelper_mixedKinds_locksOnlyApiEntities() {
    CatalogEntity api1 = apiEntity("api1", TEST_ACCOUNT);
    CatalogEntity comp = nonApiEntity("comp1", TEST_ACCOUNT, "component");
    CatalogEntity api2 = apiEntity("api2", TEST_ACCOUNT);
    when(resourceLocker.acquireLock(anyString(), anyLong(), anyLong())).thenReturn(RedisAcquiredLock.builder().build());

    List<AcquiredLock<?>> acquired = ccpService.acquireApiEntityLocks(List.of(api1, comp, api2));

    assertThat(acquired).hasSize(2);
    verify(resourceLocker, times(2)).acquireLock(anyString(), anyLong(), anyLong());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void lockHelper_emptyOrNull_noOp() {
    assertThat(ccpService.acquireApiEntityLocks(null)).isEmpty();
    assertThat(ccpService.acquireApiEntityLocks(Collections.emptyList())).isEmpty();
    verify(resourceLocker, never()).acquireLock(anyString(), anyLong(), anyLong());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void lockHelper_acquireFails_releasesPriorAndThrows() {
    // Second lock-acquire times out; the first lock must be released and the helper must throw.
    CatalogEntity api1 = apiEntity("a1", TEST_ACCOUNT);
    CatalogEntity api2 = apiEntity("a2", TEST_ACCOUNT);
    CatalogEntity api3 = apiEntity("a3", TEST_ACCOUNT);
    AcquiredLock<?> firstLock = RedisAcquiredLock.builder().build();
    when(resourceLocker.acquireLock(anyString(), anyLong(), anyLong())).thenReturn(firstLock, null, null);

    assertThatThrownBy(() -> ccpService.acquireApiEntityLocks(List.of(api1, api2, api3)))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("retry");

    verify(resourceLocker, times(1)).releaseLock(firstLock);
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void lockHelper_releaseLocks_handlesNullAndIndividualFailures() {
    ccpService.releaseLocks(null);
    ccpService.releaseLocks(Collections.emptyList());

    // One release throwing must not prevent the other. RedisAcquiredLock is a @Value so equality
    // is field-based — differentiate the two instances so Mockito can tell them apart in verify().
    AcquiredLock<?> good = RedisAcquiredLock.builder().isLeaseInfinite(false).isSentinelMode(false).build();
    AcquiredLock<?> bad = RedisAcquiredLock.builder().isLeaseInfinite(true).isSentinelMode(false).build();
    doThrow(new RuntimeException("redis hiccup")).when(resourceLocker).releaseLock(bad);

    ccpService.releaseLocks(List.of(good, bad));

    verify(resourceLocker, times(1)).releaseLock(good);
    verify(resourceLocker, times(1)).releaseLock(bad);
  }

  private static CatalogEntity apiEntity(String id, String accountId) {
    CatalogEntity entity = InlineCatalogEntity.builder().build();
    entity.setId(id);
    entity.setAccountIdentifier(accountId);
    entity.setKind("api");
    entity.setIdentifier(id);
    return entity;
  }

  private static CatalogEntity nonApiEntity(String id, String accountId, String kind) {
    CatalogEntity entity = InlineCatalogEntity.builder().build();
    entity.setId(id);
    entity.setAccountIdentifier(accountId);
    entity.setKind(kind);
    entity.setIdentifier(id);
    return entity;
  }

  // TOCTOU closure: rebuildProcessedEntitiesFromFreshState re-reads the entity inside the lock
  // and re-applies CCP mutations, so a processor commit after the pre-lock read isn't clobbered.

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void rebuild_apiEntityReReadFromDB_appliesMutationToFreshState() {
    // Stale in-memory decorator A vs freshly-committed decorator B: rebuild applies the
    // enrichment on top of B, not A.
    CatalogEntity stale = apiEntity("api-1", TEST_ACCOUNT);
    stale.setParentUniqueId("acct-uniq");
    Map<String, Object> staleDecorator = new HashMap<>();
    staleDecorator.put(Constants.PROCESSED_DATA, Map.of("metadata", Map.of("other", "stale")));
    stale.setDecorator(staleDecorator);

    CatalogEntity fresh = apiEntity("api-1", TEST_ACCOUNT);
    fresh.setParentUniqueId("acct-uniq");
    Map<String, Object> freshDecorator = new HashMap<>();
    Map<String, Object> freshProcessedData = new LinkedHashMap<>();
    Map<String, Object> freshMetadata = new LinkedHashMap<>();
    Map<String, Object> freshApis = new LinkedHashMap<>();
    Map<String, Object> freshPaths = new LinkedHashMap<>();
    Map<String, Object> freshEndpoint = new LinkedHashMap<>();
    freshEndpoint.put("method", "GET");
    freshEndpoint.put("path", "/v1/x");
    freshPaths.put("GET /v1/x", freshEndpoint);
    freshApis.put("paths", freshPaths);
    freshApis.put("specHash", "abc123");
    freshMetadata.put("apis", freshApis);
    freshProcessedData.put("metadata", freshMetadata);
    freshDecorator.put(Constants.PROCESSED_DATA, freshProcessedData);
    fresh.setDecorator(freshDecorator);

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier("acct-uniq", "api", "api-1"))
        .thenReturn(Optional.of(fresh));

    String entityRef = CatalogUtils.entityRef(stale);
    CatalogCustomPropertyEntity ccpRecord = CatalogCustomPropertyEntity.builder()
                                                .accountIdentifier(TEST_ACCOUNT)
                                                .entityRef(entityRef)
                                                .field("metadata.apis.paths.\"GET /v1/x\".enrichments.riskScore")
                                                .value("\"7.5\"")
                                                .mode(CustomPropertiesBase.ModeEnum.REPLACE)
                                                .build();

    List<CatalogEntity> result =
        ccpService.rebuildProcessedEntitiesFromFreshState(List.of(stale), List.of(), List.of(ccpRecord));

    assertThat(result).hasSize(1);
    CatalogEntity rebuilt = result.get(0);
    // Fresh entity used as base: its extracted specHash survives.
    Map<String, Object> rebuiltProcessedData =
        (Map<String, Object>) rebuilt.getDecorator().get(Constants.PROCESSED_DATA);
    Map<String, Object> rebuiltMetadata = (Map<String, Object>) rebuiltProcessedData.get("metadata");
    Map<String, Object> rebuiltApis = (Map<String, Object>) rebuiltMetadata.get("apis");
    assertThat(rebuiltApis.get("specHash")).isEqualTo("abc123");
    // CCP enrichment applied on top of the fresh state.
    Map<String, Object> rebuiltPaths = (Map<String, Object>) rebuiltApis.get("paths");
    Map<String, Object> endpoint = (Map<String, Object>) rebuiltPaths.get("GET /v1/x");
    assertThat(endpoint).isNotNull();
    Map<String, Object> enrichments = (Map<String, Object>) endpoint.get("enrichments");
    assertThat(enrichments).containsEntry("riskScore", "7.5");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rebuild_nonApiEntity_passesThroughUnchanged() {
    // Non-API entities aren't subject to the race, so no DB re-read.
    CatalogEntity component = nonApiEntity("c1", TEST_ACCOUNT, "component");

    List<CatalogEntity> result =
        ccpService.rebuildProcessedEntitiesFromFreshState(List.of(component), List.of(), List.of());

    assertThat(result).containsExactly(component);
    verify(catalogEntityRepository, never())
        .findByParentUniqueIdAndKindAndIdentifier(anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rebuild_entityDeletedBetweenReadAndLock_skippedFromSaveList() {
    // An entity deleted between the pre-lock read and lock acquisition is skipped from the save list.
    CatalogEntity stale = apiEntity("api-1", TEST_ACCOUNT);
    stale.setParentUniqueId("acct-uniq");

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier("acct-uniq", "api", "api-1"))
        .thenReturn(Optional.empty());

    List<CatalogEntity> result =
        ccpService.rebuildProcessedEntitiesFromFreshState(List.of(stale), List.of(), List.of());

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rebuild_emptyOrNullInput_noOp() {
    assertThat(ccpService.rebuildProcessedEntitiesFromFreshState(null, List.of(), List.of())).isNull();
    assertThat(ccpService.rebuildProcessedEntitiesFromFreshState(Collections.emptyList(), List.of(), List.of()))
        .isEmpty();
    verify(catalogEntityRepository, never())
        .findByParentUniqueIdAndKindAndIdentifier(anyString(), anyString(), anyString());
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  @SuppressWarnings("unchecked")
  public void rebuild_appliesOnlyMutationsTargetingThisEntity() {
    // Only records targeting an entity's own entityRef get applied to it.
    CatalogEntity target = apiEntity("api-1", TEST_ACCOUNT);
    target.setParentUniqueId("acct-uniq");
    CatalogEntity other = apiEntity("api-2", TEST_ACCOUNT);
    other.setParentUniqueId("acct-uniq");

    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier("acct-uniq", "api", "api-1"))
        .thenReturn(Optional.of(freshEmptyApiEntity("api-1")));
    when(catalogEntityRepository.findByParentUniqueIdAndKindAndIdentifier("acct-uniq", "api", "api-2"))
        .thenReturn(Optional.of(freshEmptyApiEntity("api-2")));

    CatalogCustomPropertyEntity recordForApi1 = CatalogCustomPropertyEntity.builder()
                                                    .accountIdentifier(TEST_ACCOUNT)
                                                    .entityRef(CatalogUtils.entityRef(target))
                                                    .field("metadata.foo")
                                                    .value("\"for-api1\"")
                                                    .mode(CustomPropertiesBase.ModeEnum.REPLACE)
                                                    .build();
    CatalogCustomPropertyEntity recordForApi2 = CatalogCustomPropertyEntity.builder()
                                                    .accountIdentifier(TEST_ACCOUNT)
                                                    .entityRef(CatalogUtils.entityRef(other))
                                                    .field("metadata.bar")
                                                    .value("\"for-api2\"")
                                                    .mode(CustomPropertiesBase.ModeEnum.REPLACE)
                                                    .build();

    List<CatalogEntity> result = ccpService.rebuildProcessedEntitiesFromFreshState(
        List.of(target, other), List.of(), List.of(recordForApi1, recordForApi2));

    assertThat(result).hasSize(2);
    Map<String, Object> r1 =
        (Map<String, Object>) ((Map<String, Object>) result.get(0).getDecorator().get(Constants.PROCESSED_DATA))
            .get("metadata");
    assertThat(r1.get("foo")).isEqualTo("for-api1");
    assertThat(r1.get("bar")).isNull();
    Map<String, Object> r2 =
        (Map<String, Object>) ((Map<String, Object>) result.get(1).getDecorator().get(Constants.PROCESSED_DATA))
            .get("metadata");
    assertThat(r2.get("bar")).isEqualTo("for-api2");
    assertThat(r2.get("foo")).isNull();
  }

  private static CatalogEntity freshEmptyApiEntity(String id) {
    CatalogEntity entity = apiEntity(id, TEST_ACCOUNT);
    entity.setParentUniqueId("acct-uniq");
    return entity;
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private CustomPropertyFilterRequest mockRequest(String field) {
    CustomPropertyFilterRequest request = new CustomPropertyFilterRequest();
    request.setProperty(field);
    request.setValue("1.6.0");
    request.setFilter(new ScorecardFilter());
    return request;
  }

  private List<CatalogCustomPropertyEntity> mockCustomPropertyEntities() {
    return Collections.singletonList(CatalogCustomPropertyEntity.builder()
                                         .accountIdentifier(TEST_ACCOUNT)
                                         .entityRef(TEST_ENTITY_REF2)
                                         .field(RELEASE_VERSION)
                                         .value("1.5.0")
                                         .build());
  }
}
