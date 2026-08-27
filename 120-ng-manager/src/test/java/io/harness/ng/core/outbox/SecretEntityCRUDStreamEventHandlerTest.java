/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.outbox;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.rule.OwnerRule.VIKAS_M;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference.FullyQualifiedEntityIdentifier;
import io.harness.beans.IdentifierRef;
import io.harness.beans.IdentifierRef.IdentifierRefFullyQualifiedEntityIdentifier;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.connector.services.ConnectorService;
import io.harness.connector.validator.SecretEntityCRUDEventHandler;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.api.SecretCrudService;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.rule.Owner;
import io.harness.utils.IdentifierRefHelper;

import com.google.protobuf.StringValue;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@OwnedBy(PL)
public class SecretEntityCRUDStreamEventHandlerTest extends CategoryTest {
  @Mock private EntitySetupUsageService entitySetupUsageService;
  @Mock private ConnectorService connectorService;
  @Mock private SecretCrudService secretCrudService;
  @Mock private ScopeInfoService scopeInfoService;
  @Mock private Clock clock;
  @InjectMocks private SecretEntityCRUDEventHandler eventHandler;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKAS_M)
  @Category(UnitTests.class)
  public void testUpdateSecret_withOnlyOneSecretManagerConnectorAssociatedWithIt() {
    String ACCOUNT_ID = "accountId";
    String ORG_ID = "orgId";
    String PROJECT_ID = "projectId";
    String UNIQUE_ID = "uniqueId";
    String SCOPE_UNIQUE_ID = "scopeUniqueId";
    String SECRET_ID = "secretId";
    String CONNECTOR_ID = "connectorId";
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(SCOPE_UNIQUE_ID)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    FullyQualifiedEntityIdentifier secretFullyQualifiedEntityIdentifier =
        IdentifierRefFullyQualifiedEntityIdentifier.builder()
            .accountIdentifier(scopeInfo.getAccountIdentifier())
            .orgIdentifier(scopeInfo.getOrgIdentifier())
            .projectIdentifier(scopeInfo.getProjectIdentifier())
            .identifier(SECRET_ID)
            .build();
    FullyQualifiedEntityIdentifier connectorFullyQualifiedEntityIdentifier =
        IdentifierRefFullyQualifiedEntityIdentifier.builder()
            .accountIdentifier(scopeInfo.getAccountIdentifier())
            .orgIdentifier(scopeInfo.getOrgIdentifier())
            .projectIdentifier(scopeInfo.getProjectIdentifier())
            .identifier(CONNECTOR_ID)
            .build();
    // Arrange
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setAccountIdentifier(com.google.protobuf.StringValue.of(ACCOUNT_ID))
                                          .setOrgIdentifier(com.google.protobuf.StringValue.of(ORG_ID))
                                          .setProjectIdentifier(com.google.protobuf.StringValue.of(PROJECT_ID))
                                          .setIdentifier(StringValue.of(SECRET_ID))
                                          .setUniqueId(StringValue.of(UNIQUE_ID))
                                          .setScopeInfo(io.harness.eventsframework.schemas.entity.ScopeInfo.newBuilder()
                                                            .setUniqueId(StringValue.of(SCOPE_UNIQUE_ID))
                                                            .setScope(ScopeProtoEnum.PROJECT)
                                                            .build())
                                          .build();
    IdentifierRef referredByEntityRef =
        IdentifierRefHelper.getIdentifierRefFromEntityIdentifiers(CONNECTOR_ID, scopeInfo);
    IdentifierRef referredEntityRef = IdentifierRefHelper.getIdentifierRefFromEntityIdentifiers(SECRET_ID, scopeInfo);
    EntitySetupUsageDTO entitySetupUsageDTO =
        getEntitySetupUsageDTOFromReferredEntityAndReferredByEntity(referredEntityRef, referredByEntityRef);
    Page<EntitySetupUsageDTO> entitySetupUsageDTOPage = new PageImpl<>(List.of(entitySetupUsageDTO));
    when(entitySetupUsageService.listAllEntityUsage(
             eq(0), eq(10), eq(scopeInfo), eq(secretFullyQualifiedEntityIdentifier), eq(EntityType.SECRETS), eq("")))
        .thenReturn(entitySetupUsageDTOPage);

    when(clock.millis()).thenReturn(0L);

    when(entitySetupUsageService.listAllEntityUsage(eq(0), eq(10), eq(scopeInfo),
             eq(connectorFullyQualifiedEntityIdentifier), eq(EntityType.CONNECTORS), eq("")))
        .thenReturn(Page.empty());

    when(connectorService.getHeartbeatPerpetualTaskId(any(), any())).thenReturn("testPerpetualTaskId");
    when(scopeInfoService.getScopeInfo(any(), any())).thenReturn(Map.of(SCOPE_UNIQUE_ID, Optional.of((scopeInfo))));
    // Act
    boolean result = eventHandler.handleUpdate(entityChangeDTO);

    // Assert
    verify(connectorService, times(1)).resetHeartbeatForReferringConnectors(any());
    verify(entitySetupUsageService, times(1))
        .listAllEntityUsage(anyInt(), anyInt(), eq(scopeInfo), eq(secretFullyQualifiedEntityIdentifier),
            eq(EntityType.SECRETS), anyString());
    verify(entitySetupUsageService, times(1))
        .listAllEntityUsage(anyInt(), anyInt(), eq(scopeInfo), eq(connectorFullyQualifiedEntityIdentifier),
            eq(EntityType.CONNECTORS), anyString());
    assertTrue(result);
  }

  private EntitySetupUsageDTO getEntitySetupUsageDTOFromReferredEntityAndReferredByEntity(
      IdentifierRef referredEntityRef, IdentifierRef referredByEntityRef) {
    return EntitySetupUsageDTO.builder()
        .referredByEntity(EntityDetail.builder().entityRef(referredByEntityRef).type(EntityType.CONNECTORS).build())
        .referredEntity(EntityDetail.builder().entityRef(referredEntityRef).type(EntityType.SECRETS).build())
        .build();
  }
}
