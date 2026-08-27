/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.event.handler;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.AYUSHI_TIWARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.beans.EntityReference.FullyQualifiedEntityIdentifier;
import io.harness.beans.IdentifierRef;
import io.harness.beans.IdentifierRef.IdentifierRefFullyQualifiedEntityIdentifier;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.eventsframework.schemas.entity.ScopeProtoEnum;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.polling.client.ConnectorPollingService;
import io.harness.rule.Owner;
import io.harness.utils.IdentifierRefHelper;

import com.google.protobuf.StringValue;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@OwnedBy(PIPELINE)
public class SecretEntityEventHandlerTest {
  @InjectMocks SecretEntityEventHandler secretEntityEventHandler;
  @Mock EntitySetupUsageService entitySetupUsageService;
  @Mock ConnectorPollingService pollingService;

  String ACCOUNT_ID = "accountId";
  String ORG_ID = "orgId";
  String PROJECT_ID = "projectId";
  String PROJECT_UNIQUE_ID = "projectUniqueId";
  String SECRET_ID = "secretId";
  String SCOPED_CONNECTOR_ID = "account.connectorId";
  String CONNECTOR_ID = "connectorId";

  String referredByEntityFQN = "accountId/orgId/projectId/connectorId";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = AYUSHI_TIWARI)
  @Category(UnitTests.class)
  public void testHandleUpdate() {
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(ACCOUNT_ID)
                              .orgIdentifier(ORG_ID)
                              .projectIdentifier(PROJECT_ID)
                              .uniqueId(PROJECT_UNIQUE_ID)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    FullyQualifiedEntityIdentifier fullyQualifiedEntityIdentifier =
        IdentifierRefFullyQualifiedEntityIdentifier.builder()
            .accountIdentifier(scopeInfo.getAccountIdentifier())
            .orgIdentifier(scopeInfo.getOrgIdentifier())
            .projectIdentifier(scopeInfo.getProjectIdentifier())
            .identifier(SECRET_ID)
            .build();
    EntityChangeDTO entityChangeDTO = EntityChangeDTO.newBuilder()
                                          .setIdentifier(StringValue.of(SECRET_ID))
                                          .setAccountIdentifier(StringValue.of(ACCOUNT_ID))
                                          .setProjectIdentifier(StringValue.of(PROJECT_ID))
                                          .setOrgIdentifier(StringValue.of(ORG_ID))
                                          .setScopeInfo(io.harness.eventsframework.schemas.entity.ScopeInfo.newBuilder()
                                                            .setScope(ScopeProtoEnum.PROJECT)
                                                            .setUniqueId(StringValue.of(PROJECT_UNIQUE_ID))
                                                            .build())
                                          .build();
    IdentifierRef referredByEntityRef =
        IdentifierRefHelper.getIdentifierRefFromEntityIdentifiers(SCOPED_CONNECTOR_ID, scopeInfo);
    IdentifierRef referredEntityRef = IdentifierRefHelper.getIdentifierRefFromEntityIdentifiers(SECRET_ID, scopeInfo);
    EntitySetupUsageDTO entitySetupUsageDTO =
        getEntitySetupUsageDTOFromReferredEntityAndReferredByEntity(referredEntityRef, referredByEntityRef);
    Page<EntitySetupUsageDTO> entitySetupUsageDTOPage = new PageImpl<>(List.of(entitySetupUsageDTO));
    when(entitySetupUsageService.listAllEntityUsage(
             eq(0), eq(10), eq(scopeInfo), eq(fullyQualifiedEntityIdentifier), eq(EntityType.SECRETS), eq("")))
        .thenReturn(entitySetupUsageDTOPage);
    doNothing().when(pollingService).resetPerpetualTasksForConnector(ACCOUNT_ID, SCOPED_CONNECTOR_ID);

    boolean result = secretEntityEventHandler.handleUpdate(entityChangeDTO);
    assertThat(result).isEqualTo(true);
  }

  private EntitySetupUsageDTO getEntitySetupUsageDTOFromReferredEntityAndReferredByEntity(
      IdentifierRef referredEntityRef, IdentifierRef referredByEntityRef) {
    return EntitySetupUsageDTO.builder()
        .referredByEntity(EntityDetail.builder().entityRef(referredByEntityRef).type(EntityType.CONNECTORS).build())
        .referredEntity(EntityDetail.builder().entityRef(referredEntityRef).type(EntityType.SECRETS).build())
        .build();
  }
}
