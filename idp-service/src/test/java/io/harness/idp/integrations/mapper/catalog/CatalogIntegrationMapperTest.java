/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.mapper.catalog;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.integrations.entities.IntegrationEntity;
import io.harness.idp.integrations.entities.catalog.HarnessCDIntegrationEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BaseIntegrationResponse;
import io.harness.spec.server.idp.v1.model.CatalogIntegrationResponse;
import io.harness.spec.server.idp.v1.model.HarnessCDIntegrationResponse;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class CatalogIntegrationMapperTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  private static final String TEST_IDENTIFIER = "test_integration";

  private HarnessCDIntegrationEntity harnessCDEntity;
  private String testScopes;

  @Before
  public void setup() {
    testScopes = "account";

    harnessCDEntity = HarnessCDIntegrationEntity.builder()
                          .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                          .identifier(TEST_IDENTIFIER)
                          .parentType(IntegrationEntity.ParentType.HARNESS_CD)
                          .scopesToSync(testScopes)
                          .enabled(true)
                          .autoDeletion(true)
                          .build();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToResponseWithHarnessCDEntity() {
    CatalogIntegrationResponse response = CatalogIntegrationMapper.toResponse(harnessCDEntity);

    assertThat(response).isNotNull();
    assertThat(response).isInstanceOf(HarnessCDIntegrationResponse.class);

    HarnessCDIntegrationResponse harnessCDResponse = (HarnessCDIntegrationResponse) response;
    assertThat(harnessCDResponse.getType()).isEqualTo(BaseIntegrationResponse.TypeEnum.CATALOG);
    assertThat(harnessCDResponse.getCatalogIntegrationType())
        .isEqualTo(CatalogIntegrationResponse.CatalogIntegrationTypeEnum.HARNESS_CD);
    assertThat(harnessCDResponse.getScopes()).isEqualTo(testScopes);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToResponseWithDisabledEntity() {
    HarnessCDIntegrationEntity disabledEntity = HarnessCDIntegrationEntity.builder()
                                                    .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                    .identifier(TEST_IDENTIFIER)
                                                    .parentType(IntegrationEntity.ParentType.HARNESS_CD)
                                                    .scopesToSync(testScopes)
                                                    .enabled(false)
                                                    .autoDeletion(false)
                                                    .build();
    CatalogIntegrationResponse response = CatalogIntegrationMapper.toResponse(disabledEntity);

    assertThat(response).isNotNull();
    assertThat(response).isInstanceOf(HarnessCDIntegrationResponse.class);

    HarnessCDIntegrationResponse harnessCDResponse = (HarnessCDIntegrationResponse) response;
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToResponseWithEmptyScopes() {
    HarnessCDIntegrationEntity entityWithEmptyScopes = HarnessCDIntegrationEntity.builder()
                                                           .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                           .identifier(TEST_IDENTIFIER)
                                                           .parentType(IntegrationEntity.ParentType.HARNESS_CD)
                                                           .scopesToSync("")
                                                           .enabled(true)
                                                           .autoDeletion(false)
                                                           .build();

    CatalogIntegrationResponse response = CatalogIntegrationMapper.toResponse(entityWithEmptyScopes);

    assertThat(response).isNotNull().isInstanceOf(HarnessCDIntegrationResponse.class);

    HarnessCDIntegrationResponse harnessCDResponse = (HarnessCDIntegrationResponse) response;
    assertThat(harnessCDResponse.getScopes()).isEmpty();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToResponseWithNullScopes() {
    HarnessCDIntegrationEntity entityWithNullScopes = HarnessCDIntegrationEntity.builder()
                                                          .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                                          .identifier(TEST_IDENTIFIER)
                                                          .parentType(IntegrationEntity.ParentType.HARNESS_CD)
                                                          .scopesToSync(null)
                                                          .enabled(true)
                                                          .autoDeletion(false)
                                                          .build();

    CatalogIntegrationResponse response = CatalogIntegrationMapper.toResponse(entityWithNullScopes);

    assertThat(response).isNotNull().isInstanceOf(HarnessCDIntegrationResponse.class);

    HarnessCDIntegrationResponse harnessCDResponse = (HarnessCDIntegrationResponse) response;
    assertThat(harnessCDResponse.getScopes()).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToResponseList() {
    HarnessCDIntegrationEntity entity1 = HarnessCDIntegrationEntity.builder()
                                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                             .identifier("entity1")
                                             .parentType(IntegrationEntity.ParentType.HARNESS_CD)
                                             .scopesToSync(testScopes)
                                             .enabled(true)
                                             .autoDeletion(false)
                                             .build();

    HarnessCDIntegrationEntity entity2 = HarnessCDIntegrationEntity.builder()
                                             .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                             .identifier("entity2")
                                             .parentType(IntegrationEntity.ParentType.HARNESS_CD)
                                             .scopesToSync("account")
                                             .enabled(false)
                                             .autoDeletion(true)
                                             .build();

    List<IntegrationEntity> entities = new ArrayList<>();
    entities.add(entity1);
    entities.add(entity2);

    List<CatalogIntegrationResponse> responses = CatalogIntegrationMapper.toResponse(entities);

    assertThat(responses).hasSize(2);
    assertThat(responses.get(0)).isInstanceOf(HarnessCDIntegrationResponse.class);
    assertThat(responses.get(1)).isInstanceOf(HarnessCDIntegrationResponse.class);

    HarnessCDIntegrationResponse response1 = (HarnessCDIntegrationResponse) responses.get(0);
    HarnessCDIntegrationResponse response2 = (HarnessCDIntegrationResponse) responses.get(1);

    assertThat(response1.getScopes()).isEqualTo(testScopes);

    assertThat(response2.getScopes()).isEqualTo("account");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToResponseListWithEmptyList() {
    List<IntegrationEntity> entities = new ArrayList<>();

    List<CatalogIntegrationResponse> responses = CatalogIntegrationMapper.toResponse(entities);

    assertThat(responses).isNotNull();
    assertThat(responses).isEmpty();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testUtilityClassStructure() {
    assertThat(CatalogIntegrationMapper.class.getDeclaredConstructors()).hasSize(1);
    assertThat(java.lang.reflect.Modifier.isPrivate(
                   CatalogIntegrationMapper.class.getDeclaredConstructors()[0].getModifiers()))
        .isTrue();
  }
}
