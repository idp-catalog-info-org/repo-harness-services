/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.mappers;

import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;
import static io.harness.rule.OwnerRule.AGNIVA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.datasourcelocations.beans.DataSourceLocationType;
import io.harness.idp.scorecard.datasourcelocations.entity.CatalogDataSourceLocationEntity;
import io.harness.idp.scorecard.datasourcelocations.entity.HQLDataSourceLocationEntity;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.DataSourceLocationDetails;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class DataSourceLocationMapperTest extends CategoryTest {
  private static final String DATA_SOURCE_IDENTIFIER = "traceable";
  private static final String LOCATION_IDENTIFIER = "hql_traceable_risk_score";
  private static final String HQL_TEMPLATE = "find view idp:traceable_api_matches";
  private static final String JEXL = "catalog.spec.owner != null";

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testFromDtoCreatesHqlEntity() {
    DataSourceLocationDetails details = createHqlDetails();

    HQLDataSourceLocationEntity entity =
        (HQLDataSourceLocationEntity) DataSourceLocationMapper.fromDto(details, DATA_SOURCE_IDENTIFIER);

    assertThat(entity.getAccountIdentifier()).isEqualTo(GLOBAL_ACCOUNT_ID);
    assertThat(entity.getIdentifier()).isEqualTo(LOCATION_IDENTIFIER);
    assertThat(entity.getDataSourceIdentifier()).isEqualTo(DATA_SOURCE_IDENTIFIER);
    assertThat(entity.getType()).isEqualTo(DataSourceLocationType.HQL);
    assertThat(entity.getHqlTemplate()).isEqualTo(HQL_TEMPLATE);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testFromDtoCreatesCatalogEntity() {
    DataSourceLocationDetails details = createCatalogDetails();

    CatalogDataSourceLocationEntity entity =
        (CatalogDataSourceLocationEntity) DataSourceLocationMapper.fromDto(details, DATA_SOURCE_IDENTIFIER);

    assertThat(entity.getAccountIdentifier()).isEqualTo(GLOBAL_ACCOUNT_ID);
    assertThat(entity.getIdentifier()).isEqualTo(LOCATION_IDENTIFIER);
    assertThat(entity.getDataSourceIdentifier()).isEqualTo(DATA_SOURCE_IDENTIFIER);
    assertThat(entity.getType()).isEqualTo(DataSourceLocationType.CATALOG);
    assertThat(entity.getJexl()).isEqualTo(JEXL);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testUpdateEntityUpdatesHqlTemplate() {
    HQLDataSourceLocationEntity entity = new HQLDataSourceLocationEntity();
    entity.setHqlTemplate("old template");
    DataSourceLocationDetails details = createHqlDetails().hqlTemplate("updated template");

    DataSourceLocationMapper.updateEntity(entity, details);

    assertThat(entity.getHqlTemplate()).isEqualTo("updated template");
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testUpdateEntityUpdatesJexl() {
    CatalogDataSourceLocationEntity entity = new CatalogDataSourceLocationEntity();
    entity.setJexl("old jexl");
    DataSourceLocationDetails details = createCatalogDetails().jexl("updated jexl");

    DataSourceLocationMapper.updateEntity(entity, details);

    assertThat(entity.getJexl()).isEqualTo("updated jexl");
  }

  private DataSourceLocationDetails createHqlDetails() {
    return new DataSourceLocationDetails()
        .identifier(LOCATION_IDENTIFIER)
        .type(DataSourceLocationDetails.TypeEnum.HQL)
        .hqlTemplate(HQL_TEMPLATE);
  }

  private DataSourceLocationDetails createCatalogDetails() {
    return new DataSourceLocationDetails()
        .identifier(LOCATION_IDENTIFIER)
        .type(DataSourceLocationDetails.TypeEnum.CATALOG)
        .jexl(JEXL);
  }
}
