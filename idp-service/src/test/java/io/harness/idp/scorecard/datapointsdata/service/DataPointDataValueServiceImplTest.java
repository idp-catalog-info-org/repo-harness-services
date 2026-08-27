/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.service;

import static io.harness.idp.common.Constants.BITBUCKET_IDENTIFIER;
import static io.harness.idp.common.Constants.GITHUB_IDENTIFIER;
import static io.harness.idp.common.Constants.GITLAB_IDENTIFIER;
import static io.harness.idp.common.Constants.HARNESS_IDENTIFIER;
import static io.harness.idp.common.Constants.KUBERNETES_IDENTIFIER;
import static io.harness.idp.scorecard.datapoints.constants.Inputs.BRANCH_NAME;
import static io.harness.rule.OwnerRule.AGNIVA;
import static io.harness.rule.OwnerRule.VIKYATH_HAREKAL;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.factory.DataSourceDslFactory;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.factory.KubernetesDslFactory;
import io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl.KubernetesDsl;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.ClusterConfig;
import io.harness.spec.server.idp.v1.model.DataPointInputValues;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointInfo;
import io.harness.spec.server.idp.v1.model.DataSourceLocationInfo;
import io.harness.spec.server.idp.v1.model.InputValue;
import io.harness.spec.server.idp.v1.model.KubernetesConfig;
import io.harness.spec.server.idp.v1.model.ScmConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class DataPointDataValueServiceImplTest extends CategoryTest {
  private static final String TEST_DATAPOINT_IDENTIFIER = "dp1";
  private static final String TEST_DSL_IDENTIFIER = "dsl1";
  private static final String TEST_CLUSTER = "cluster1";
  private static final String TEST_LABEL_SELECTOR = "app=myapp";
  private static final String TEST_URL = "http://192.168.0.1";
  private static final String TEST_ACCOUNT_IDENTIFIER = "testAccount";
  private static final String datapointidentifier = "datapointidentifer";
  private static final String datasourceidentifier = "datasourceidentifier";
  AutoCloseable openMocks;
  @InjectMocks DataPointDataValueServiceImpl dataPointDataValueService;
  @Mock DataSourceDslFactory dataSourceDataProviderFactory;
  @Mock DataPointService dataPointService;
  @Mock KubernetesDslFactory factory;
  @Mock KubernetesDsl kubernetesDsl;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIKYATH_HAREKAL)
  @Category(UnitTests.class)
  public void testK8sGetDataPointDataValues() {
    List<ClusterConfig> clusters = new ArrayList<>();
    ClusterConfig clusterConfig = new ClusterConfig();
    clusterConfig.setName(TEST_CLUSTER);
    clusterConfig.setUrl(TEST_URL);
    clusters.add(clusterConfig);

    DataSourceLocationInfo dslInfo = new DataSourceLocationInfo();
    DataPointInputValues dpInputValues = new DataPointInputValues();
    dpInputValues.setDataPointIdentifier(TEST_DATAPOINT_IDENTIFIER);
    dslInfo.setDataPoints(Collections.singletonList(dpInputValues));

    KubernetesConfig kubernetesConfig = new KubernetesConfig();
    kubernetesConfig.setLabelSelector(TEST_LABEL_SELECTOR);
    kubernetesConfig.setClusters(clusters);
    kubernetesConfig.setDataSourceLocation(dslInfo);

    Map<String, List<DataPointEntity>> dataToFetch = new HashMap<>();
    DataPointEntity datapoint = DataPointEntity.builder()
                                    .dataSourceIdentifier(KUBERNETES_IDENTIFIER)
                                    .identifier(TEST_DATAPOINT_IDENTIFIER)
                                    .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                    .build();
    dataToFetch.put(TEST_DSL_IDENTIFIER, Collections.singletonList(datapoint));

    when(dataSourceDataProviderFactory.getDataSourceDataProvider(KUBERNETES_IDENTIFIER)).thenReturn(factory);
    when(dataPointService.getDslDataPointsInfo(
             TEST_ACCOUNT_IDENTIFIER, Collections.singletonList(TEST_DATAPOINT_IDENTIFIER), KUBERNETES_IDENTIFIER))
        .thenReturn(dataToFetch);
    when(factory.getDslDataProvider(TEST_DSL_IDENTIFIER)).thenReturn(kubernetesDsl);

    dataPointDataValueService.getDataPointDataValues(TEST_ACCOUNT_IDENTIFIER, KUBERNETES_IDENTIFIER, kubernetesConfig);

    verify(kubernetesDsl).getDslData(TEST_ACCOUNT_IDENTIFIER, kubernetesConfig);
  }
  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testGetDataPointIdentifiersWithDataSourceDataPointInfo() {
    DataSourceDataPointInfo dataSourceDataPointInfo = mock(DataSourceDataPointInfo.class);
    DataSourceLocationInfo locationInfo = getDataSourceLocationInfo();
    when(dataSourceDataPointInfo.getDataSourceLocation()).thenReturn(locationInfo);
    DataSourceLocationInfo result =
        dataPointDataValueService.getDataPointIdentifiers(HARNESS_IDENTIFIER, dataSourceDataPointInfo);
    assertvalues(result);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testGetDataPointIdentifiersWithScmConfig() {
    ScmConfig scmConfig = mock(ScmConfig.class);
    DataSourceLocationInfo locationInfo = getDataSourceLocationInfo();
    when(scmConfig.getDataSourceLocation()).thenReturn(locationInfo);
    DataSourceLocationInfo result = dataPointDataValueService.getDataPointIdentifiers(HARNESS_IDENTIFIER, scmConfig);
    assertvalues(result);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testGetDataPointIdentifiersWithGitHubIdentifier() {
    ScmConfig scmConfig = mock(ScmConfig.class);
    DataSourceLocationInfo locationInfo = getDataSourceLocationInfo();
    when(scmConfig.getDataSourceLocation()).thenReturn(locationInfo);
    DataSourceLocationInfo result = dataPointDataValueService.getDataPointIdentifiers(GITHUB_IDENTIFIER, scmConfig);
    assertvalues(result);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testGetDataPointIdentifiersWithBitbucketIdentifier() {
    ScmConfig scmConfig = mock(ScmConfig.class);
    DataSourceLocationInfo locationInfo = getDataSourceLocationInfo();
    when(scmConfig.getDataSourceLocation()).thenReturn(locationInfo);
    DataSourceLocationInfo result = dataPointDataValueService.getDataPointIdentifiers(BITBUCKET_IDENTIFIER, scmConfig);
    assertvalues(result);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testGetDataPointIdentifiersWithGitLabIdentifier() {
    DataSourceLocationInfo locationInfo = getDataSourceLocationInfo();
    ScmConfig scmConfig = mock(ScmConfig.class);
    when(scmConfig.getDataSourceLocation()).thenReturn(locationInfo);
    DataSourceLocationInfo result = dataPointDataValueService.getDataPointIdentifiers(GITLAB_IDENTIFIER, scmConfig);
    assertvalues(result);
  }

  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testGetDataPointIdentifiersWithDefaultIdentifier() {
    DataSourceLocationInfo locationInfo = getDataSourceLocationInfo();
    ScmConfig scmConfig = mock(ScmConfig.class);
    when(scmConfig.getDataSourceLocation()).thenReturn(locationInfo);
    try {
      dataPointDataValueService.getDataPointIdentifiers("default", scmConfig);
    } catch (UnsupportedOperationException e) {
      assertEquals("default data source is not supported", e.getMessage());
    }
  }

  private DataSourceLocationInfo getDataSourceLocationInfo() {
    List<DataPointInputValues> dataPointInputValuesList = new ArrayList<>();
    DataPointInputValues dataPointInputValues = new DataPointInputValues();
    List<InputValue> inputValues = new ArrayList<>();
    InputValue inputValue = new InputValue();
    inputValue.setKey(BRANCH_NAME);
    inputValue.setValue("develop");
    inputValues.add(inputValue);
    dataPointInputValues.setInputValues(inputValues);
    dataPointInputValues.setDataPointIdentifier(datapointidentifier);
    dataPointInputValues.setDataSourceIdentifier(datasourceidentifier);
    dataPointInputValuesList.add(dataPointInputValues);
    DataSourceLocationInfo locationInfo = new DataSourceLocationInfo();
    locationInfo.setDataPoints(dataPointInputValuesList);
    return locationInfo;
  }
  private void assertvalues(DataSourceLocationInfo result) {
    assertEquals(1, result.getDataPoints().size());
    assertEquals(datapointidentifier, result.getDataPoints().get(0).getDataPointIdentifier());
    assertEquals(datasourceidentifier, result.getDataPoints().get(0).getDataSourceIdentifier());
    assertEquals(1, result.getDataPoints().get(0).getInputValues().size());
    assertEquals(BRANCH_NAME, result.getDataPoints().get(0).getInputValues().get(0).getKey());
  }
}
