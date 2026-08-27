/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.service.locations;

import static io.harness.idp.scorecard.datapoints.constants.DataPoints.CATALOG_SYSTEM_IS_DEFINED_AND_IT_EXISTS;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.SYSTEM_NOT_DEFINED;
import static io.harness.idp.scorecard.datasourcelocations.constants.DataSourceLocations.CATALOG;
import static io.harness.rule.OwnerRule.AJINKYA_SHINGANE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.backstage.beans.BackstageCatalogEntityTypes;
import io.harness.idp.backstage.beans.MetadataFieldConstants;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.idp.backstage.service.impl.BackstageServiceImpl;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datasourcelocations.locations.CatalogSystemDsl;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.rule.Owner;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class CatalogSystemDslTest extends CategoryTest {
  private static final String ACCOUNT_ID = "123";
  private static final String RULE_IDENTIFIER = "rule1";
  public static final String SOURCE_LOCATION_ANNOTATION = "backstage.io/source-location";

  AutoCloseable openMocks;
  @InjectMocks CatalogSystemDsl catalogSystemDsl;
  @Mock BackstageServiceImpl backstageService;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = AJINKYA_SHINGANE)
  @Category(UnitTests.class)
  public void testFetchDataForSystemExists() {
    when(backstageService.findByAccountIdentifierAndEntityRef(anyString(), anyString()))
        .thenReturn(getBackstageCatalogEntity(true));

    Map<String, Object> data = catalogSystemDsl.fetchData(ACCOUNT_ID, getBackstageCatalogEntity(true), null,
        List.of(getDataFetchDTO()), new HashMap<>(), new HashMap<>(), new HashMap<>(), null, false, Set.of());

    assertTrue(data.isEmpty());
  }

  @Test
  @Owner(developers = AJINKYA_SHINGANE)
  @Category(UnitTests.class)
  public void testFetchDataForSystemIsNotDefined() {
    when(backstageService.findByAccountIdentifierAndEntityRef(anyString(), anyString()))
        .thenReturn(getBackstageCatalogEntity(false));

    Map<String, Object> data = catalogSystemDsl.fetchData(ACCOUNT_ID, getBackstageCatalogEntity(false), null,
        List.of(getDataFetchDTO()), new HashMap<>(), new HashMap<>(), new HashMap<>(), null, false, Set.of());

    assertEquals(SYSTEM_NOT_DEFINED, data.get("error_messages"));
  }

  @Test
  @Owner(developers = AJINKYA_SHINGANE)
  @Category(UnitTests.class)
  public void testFetchDataForSystemDoesNotExists() {
    when(backstageService.findByAccountIdentifierAndEntityRef(anyString(), anyString()))
        .thenThrow(new InvalidRequestException("msg"));

    Map<String, Object> data = catalogSystemDsl.fetchData(ACCOUNT_ID, getBackstageCatalogEntity(true), null,
        List.of(getDataFetchDTO()), new HashMap<>(), new HashMap<>(), new HashMap<>(), null, false, Set.of());

    assertEquals("Defined system testSystem does not exist in the Catalog. Please add the System as a new entity. Read "
            + "https://developer.harness.io/docs/internal-developer-portal/catalog/yaml-file#kind-system",
        data.get("error_messages"));
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private BackstageCatalogEntity getBackstageCatalogEntity(Boolean setSystem) {
    BackstageCatalogComponentEntity backstageCatalogComponentEntity =
        BackstageCatalogComponentEntity.builder()
            .kind(BackstageCatalogEntityTypes.COMPONENT.kind)
            .metadata(Map.of(MetadataFieldConstants.NAME, "idp-service", MetadataFieldConstants.ANNOTATIONS,
                Map.of(SOURCE_LOCATION_ANNOTATION, "url:https://github.com/harness/harness-core/tree/develop"),
                MetadataFieldConstants.HARNESS_DATA, Map.of("branch", "develop")))
            .build();

    BackstageCatalogComponentEntity.Spec spec =
        BackstageCatalogComponentEntity.Spec.builder().type("service").owner("team-a").build();

    if (setSystem) {
      spec.setSystem(Collections.singletonList("testSystem"));
    }
    backstageCatalogComponentEntity.setSpec(spec);
    return backstageCatalogComponentEntity;
  }

  private DataFetchDTO getDataFetchDTO() {
    DataPointEntity dataPointEntity = DataPointEntity.builder()
                                          .dataSourceIdentifier(CATALOG)
                                          .identifier(CATALOG_SYSTEM_IS_DEFINED_AND_IT_EXISTS)
                                          .build();
    return DataFetchDTO.builder().ruleIdentifier(RULE_IDENTIFIER).dataPoint(dataPointEntity).build();
  }
}
