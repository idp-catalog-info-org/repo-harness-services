/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.dsldataprovider.impl;

import static io.harness.rule.OwnerRule.AGNIVA;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.common.HarnessCodeRepoConfig;
import io.harness.idp.scorecard.datasourcelocations.beans.ApiRequestDetails;
import io.harness.idp.scorecard.datasourcelocations.client.DirectDslClient;
import io.harness.idp.scorecard.datasourcelocations.client.DslClientFactory;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.DataPointInputValues;
import io.harness.spec.server.idp.v1.model.DataSourceLocationInfo;
import io.harness.spec.server.idp.v1.model.InputValue;
import io.harness.spec.server.idp.v1.model.ScmConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class HarnessCodeContentDslTest extends CategoryTest {
  private static final String accountIdentifier = "testAccount";
  final HarnessCodeRepoConfig harnessCodeRepoConfig = HarnessCodeRepoConfig.builder()
                                                          .apiUrl("https://app.harness.io/code/git")
                                                          .gitBaseUrl("https://git.harness.io")
                                                          .baseUrl("https://app.harness.io")
                                                          .internalApiUrl("http://localhost:8080")
                                                          .serviceClientSharedSecret("serviceClientSharedSecret")
                                                          .build();
  AutoCloseable openMocks;
  @Mock DslClientFactory dslClientFactory;
  @Mock DirectDslClient dslClient;
  @InjectMocks HarnessCodeContentDsl harnessCodeContentDsl;
  ScmConfig scmConfig = getScmConfig();
  @Before
  public void setup() throws IllegalAccessException {
    openMocks = MockitoAnnotations.openMocks(this);
    FieldUtils.writeField(harnessCodeContentDsl, "harnessCodeRepoConfig", harnessCodeRepoConfig, true);
  }
  @Test
  @Owner(developers = AGNIVA)
  @Category(UnitTests.class)
  public void testgetDslData() {
    when(dslClientFactory.getClient(any(), any(), anyBoolean())).thenReturn(dslClient);
    when(dslClient.call(eq(accountIdentifier), any(ApiRequestDetails.class), anySet(), any()))
        .thenReturn(Response.status(Response.Status.OK).entity("{entries:[{name:datapointvalue}]}").build());
    Map<String, Object> dslData = harnessCodeContentDsl.getDslData(accountIdentifier, scmConfig);
    assertEquals(dslData.get("text"), "{entries:[{name:datapointvalue}]}");
  }

  private ScmConfig getScmConfig() {
    ScmConfig scmConfig = new ScmConfig();
    scmConfig.setAccountIdentifier("testAccount");
    scmConfig.setRepoScm("git");
    scmConfig.setRepoName("https://git.harness.io");
    scmConfig.setToken("Token");
    scmConfig.setDataSourceLocation(getdataSourceLocationInfo());
    scmConfig.setThroughDelegate(false);
    scmConfig.setDelegateSelectors(List.of());
    return scmConfig;
  }

  private DataSourceLocationInfo getdataSourceLocationInfo() {
    DataPointInputValues dataPointInputValues = new DataPointInputValues();
    dataPointInputValues.setDataPointIdentifier("datapointidentifier");
    List<InputValue> inputValues = new ArrayList<>();
    InputValue inputValue = new InputValue();
    inputValue.setKey("filePath");
    inputValue.setValue("datapointvalue");
    inputValues.add(inputValue);
    dataPointInputValues.setInputValues(inputValues);
    List<DataPointInputValues> dataPointInputValuesList = new ArrayList<>();
    dataPointInputValuesList.add(dataPointInputValues);
    DataSourceLocationInfo dataSourceLocationInfo = new DataSourceLocationInfo();
    dataSourceLocationInfo.setDataPoints(dataPointInputValuesList);
    return dataSourceLocationInfo;
  }
}
