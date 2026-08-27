/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entitysetupusage.resource;

import static io.harness.annotations.dev.HarnessTeam.DX;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference.FullyQualifiedEntityIdentifier;
import io.harness.beans.IdentifierRef.IdentifierRefFullyQualifiedEntityIdentifier;
import io.harness.beans.NGTemplateReference.NGTemplateReferenceFullyQualifiedEntityIdentifier;
import io.harness.beans.ScopeInfo;
import io.harness.beans.ScopeLevel;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.entitysetupusage.dto.EntitySetupUsageDTO;
import io.harness.ng.core.entitysetupusage.dto.EntityUsageCountResponseDTO;
import io.harness.ng.core.entitysetupusage.service.EntitySetupUsageService;
import io.harness.ng.core.services.ScopeInfoService;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

@OwnedBy(DX)
public class EntitySetupUsageResourceTest extends CategoryTest {
  @InjectMocks EntitySetupUsageResource entitySetupUsageResource;
  @Mock EntitySetupUsageService entitySetupUsageService;
  @Mock ScopeInfoService scopeInfoService;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void listTest() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String uniqueId = "uniqueId";
    String identifier = "identifier";
    String searchTerm = "searchTerm";
    String referredEntityFQN = accountIdentifier + "/" + orgIdentifier + "/" + projectIdentifier + "/" + identifier;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    FullyQualifiedEntityIdentifier fullyQualifiedEntityIdentifier =
        IdentifierRefFullyQualifiedEntityIdentifier.builder()
            .accountIdentifier(scopeInfo.getAccountIdentifier())
            .orgIdentifier(scopeInfo.getOrgIdentifier())
            .projectIdentifier(scopeInfo.getProjectIdentifier())
            .identifier(identifier)
            .build();
    when(scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(scopeInfo);
    Page<EntitySetupUsageDTO> entitySetupUsageDTOPage = new PageImpl<>(new ArrayList<>());
    when(entitySetupUsageService.listAllEntityUsage(eq(100), eq(100), eq(scopeInfo), eq(fullyQualifiedEntityIdentifier),
             eq(EntityType.CONNECTORS), eq(searchTerm)))
        .thenReturn(entitySetupUsageDTOPage);
    entitySetupUsageResource.listAllEntityUsage(
        100, 100, accountIdentifier, referredEntityFQN, EntityType.CONNECTORS, searchTerm);
    Mockito.verify(entitySetupUsageService, times(1))
        .listAllEntityUsage(eq(100), eq(100), eq(scopeInfo), eq(fullyQualifiedEntityIdentifier),
            eq(EntityType.CONNECTORS), eq(searchTerm));
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void testListAllEntityUsageWithSupportForTwoFqnForASingleEntity() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String uniqueId = "uniqueId";
    String searchTerm = "searchTerm";
    String referredEntityIdentifier1 = "referredEntityIdentifier1";
    String referredEntityIdentifier2 = "referredEntityIdentifier2";
    String versionLabel = "v1";
    String referredEntityFQN1 = accountIdentifier + "/" + orgIdentifier + "/" + projectIdentifier + "/"
        + referredEntityIdentifier1 + "/" + versionLabel;
    String referredEntityFQN2 = accountIdentifier + "/" + orgIdentifier + "/" + projectIdentifier + "/"
        + referredEntityIdentifier2 + "/" + versionLabel;

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    FullyQualifiedEntityIdentifier referredEntityFQNIdentifier1 =
        NGTemplateReferenceFullyQualifiedEntityIdentifier.builder()
            .accountIdentifier(scopeInfo.getAccountIdentifier())
            .orgIdentifier(scopeInfo.getOrgIdentifier())
            .projectIdentifier(scopeInfo.getProjectIdentifier())
            .identifier(referredEntityIdentifier1)
            .versionLabel(versionLabel)
            .build();
    FullyQualifiedEntityIdentifier referredEntityFQNIdentifier2 =
        NGTemplateReferenceFullyQualifiedEntityIdentifier.builder()
            .accountIdentifier(scopeInfo.getAccountIdentifier())
            .orgIdentifier(scopeInfo.getOrgIdentifier())
            .projectIdentifier(scopeInfo.getProjectIdentifier())
            .identifier(referredEntityIdentifier2)
            .versionLabel(versionLabel)
            .build();
    when(scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(scopeInfo);

    Page<EntitySetupUsageDTO> entitySetupUsageDTOPage = new PageImpl<>(new ArrayList<>());
    when(entitySetupUsageService.listAllEntityUsageWithSupportForTwoFqnForASingleEntity(eq(100), eq(100), eq(scopeInfo),
             eq(referredEntityFQNIdentifier1), eq(scopeInfo), eq(referredEntityFQNIdentifier2), eq(EntityType.TEMPLATE),
             eq(searchTerm)))
        .thenReturn(entitySetupUsageDTOPage);
    entitySetupUsageResource.listAllEntityUsageWith2Fqns(
        100, 100, accountIdentifier, referredEntityFQN1, referredEntityFQN2, EntityType.TEMPLATE, searchTerm);
    Mockito.verify(entitySetupUsageService, times(1))
        .listAllEntityUsageWithSupportForTwoFqnForASingleEntity(eq(100), eq(100), eq(scopeInfo),
            eq(referredEntityFQNIdentifier1), eq(scopeInfo), eq(referredEntityFQNIdentifier2), eq(EntityType.TEMPLATE),
            eq(searchTerm));
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void isEntityReferenced() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String uniqueId = "uniqueId";
    String identifier = "identifier";
    String referredEntityFQN = accountIdentifier + "/" + orgIdentifier + "/" + projectIdentifier + "/" + identifier;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    FullyQualifiedEntityIdentifier fullyQualifiedEntityIdentifier =
        IdentifierRefFullyQualifiedEntityIdentifier.builder()
            .accountIdentifier(scopeInfo.getAccountIdentifier())
            .orgIdentifier(scopeInfo.getOrgIdentifier())
            .projectIdentifier(scopeInfo.getProjectIdentifier())
            .identifier(identifier)
            .build();
    when(scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(scopeInfo);
    entitySetupUsageResource.isEntityReferenced(accountIdentifier, referredEntityFQN, EntityType.CONNECTORS);
    Mockito.verify(entitySetupUsageService, times(1))
        .isEntityReferenced(eq(scopeInfo), eq(fullyQualifiedEntityIdentifier), eq(EntityType.CONNECTORS));
  }

  @Test
  @Owner(developers = OwnerRule.UTKARSH_CHOUBEY)
  @Category(UnitTests.class)
  public void isEntityReferencedV2() {
    String accountIdentifier = "accountIdentifier";
    String identifier = "identifier";
    String searchTerm = "searchTerm";
    String referredEntityFQN = accountIdentifier + "/" + identifier;
    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .uniqueId(accountIdentifier)
                              .scopeType(ScopeLevel.ACCOUNT)
                              .build();
    FullyQualifiedEntityIdentifier fullyQualifiedEntityIdentifier =
        IdentifierRefFullyQualifiedEntityIdentifier.builder()
            .accountIdentifier(scopeInfo.getAccountIdentifier())
            .identifier(identifier)
            .build();
    when(scopeInfoService.getScopeInfo(accountIdentifier, null, null)).thenReturn(scopeInfo);
    Page<EntitySetupUsageDTO> entitySetupUsageDTOPage = new PageImpl<>(new ArrayList<>());
    when(entitySetupUsageService.listAllEntityUsage(eq(100), eq(100), eq(scopeInfo), eq(fullyQualifiedEntityIdentifier),
             eq(EntityType.CONNECTORS), eq(searchTerm)))
        .thenReturn(entitySetupUsageDTOPage);
    entitySetupUsageResource.listAllEntityUsageV2(
        100, 100, accountIdentifier, referredEntityFQN, EntityType.CONNECTORS, searchTerm);
    Mockito.verify(entitySetupUsageService, times(1))
        .listAllEntityUsage(eq(100), eq(100), eq(scopeInfo), eq(fullyQualifiedEntityIdentifier),
            eq(EntityType.CONNECTORS), eq(searchTerm));
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void saveTest() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String identifier = "identifier";
    EntitySetupUsageDTO entitySetupUsageDTO =
        EntitySetupUsageDTO.builder().accountIdentifier(accountIdentifier).build();
    entitySetupUsageResource.save(entitySetupUsageDTO);
    Mockito.verify(entitySetupUsageService, times(1)).save(eq(entitySetupUsageDTO));
  }

  @Test
  @Owner(developers = OwnerRule.DEEPAK)
  @Category(UnitTests.class)
  public void deleteTest() {
    String accountIdentifier = "accountIdentifier";
    String orgIdentifier = "orgIdentifier";
    String projectIdentifier = "projectIdentifier";
    String uniqueId = "uniqueId";
    String referredEntityFQN = accountIdentifier + "/" + orgIdentifier + "/" + projectIdentifier + "/"
        + "referredEntityIdentifier";
    String referredByEntityFQN = accountIdentifier + "/" + orgIdentifier + "/" + projectIdentifier + "/"
        + "referredByEntityIdentifier";

    ScopeInfo scopeInfo = ScopeInfo.builder()
                              .accountIdentifier(accountIdentifier)
                              .orgIdentifier(orgIdentifier)
                              .projectIdentifier(projectIdentifier)
                              .uniqueId(uniqueId)
                              .scopeType(ScopeLevel.PROJECT)
                              .build();
    when(scopeInfoService.getScopeInfo(accountIdentifier, orgIdentifier, projectIdentifier)).thenReturn(scopeInfo);

    entitySetupUsageResource.delete(
        accountIdentifier, referredEntityFQN, EntityType.CONNECTORS, referredByEntityFQN, EntityType.SECRETS);

    Mockito.verify(entitySetupUsageService, times(1))
        .delete(eq(scopeInfo),
            eq(IdentifierRefFullyQualifiedEntityIdentifier.builder()
                    .accountIdentifier(scopeInfo.getAccountIdentifier())
                    .orgIdentifier(scopeInfo.getOrgIdentifier())
                    .projectIdentifier(scopeInfo.getProjectIdentifier())
                    .identifier("referredEntityIdentifier")
                    .build()),
            eq(EntityType.CONNECTORS), eq(scopeInfo),
            eq(IdentifierRefFullyQualifiedEntityIdentifier.builder()
                    .accountIdentifier(scopeInfo.getAccountIdentifier())
                    .orgIdentifier(scopeInfo.getOrgIdentifier())
                    .projectIdentifier(scopeInfo.getProjectIdentifier())
                    .identifier("referredByEntityIdentifier")
                    .build()),
            eq(EntityType.SECRETS));
  }

  @Test
  @Owner(developers = OwnerRule.SAYANTAN_MONDAL)
  @Category(UnitTests.class)
  public void testListAllEntityUsageWithMultiple2Fqns() {
    String accountIdentifier = "accountIdentifier";
    String referredEntityFQN1 = "account/org/project/template1/v1";
    String referredEntityFQN2 = "account/org/project/template2/v1";

    List<String> referredEntityFQNPairs = new ArrayList<>();
    referredEntityFQNPairs.add(referredEntityFQN1);
    referredEntityFQNPairs.add(referredEntityFQN2);

    Map<String, Integer> countsResponse = new HashMap<>();
    countsResponse.put(referredEntityFQN1, 2);
    countsResponse.put(referredEntityFQN2, 1);
    EntityUsageCountResponseDTO response =
        EntityUsageCountResponseDTO.builder().entityUsageCounts(countsResponse).build();
    when(entitySetupUsageService.listAllEntityUsageCountV2WithMultiple2Fqns(
             eq(accountIdentifier), eq(EntityType.TEMPLATE), eq(referredEntityFQNPairs)))
        .thenReturn(response);

    entitySetupUsageResource.listAllEntityUsageWithMultiple2Fqns(
        accountIdentifier, EntityType.TEMPLATE, referredEntityFQNPairs);

    Mockito.verify(entitySetupUsageService, times(1))
        .listAllEntityUsageCountV2WithMultiple2Fqns(
            eq(accountIdentifier), eq(EntityType.TEMPLATE), eq(referredEntityFQNPairs));
  }
}
