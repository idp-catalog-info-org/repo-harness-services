/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.rule.OwnerRule.AJINKYA_SHINGANE;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;
import io.harness.rule.Owner;

import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class SystemExistsParserTest extends CategoryTest {
  private static final String ERROR_MESSAGE_KEY = "error_messages";
  private static final String RULE_IDENTIFIER1 = "rule1";

  AutoCloseable openMocks;
  @InjectMocks SystemExistsParser systemExistsParser;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = AJINKYA_SHINGANE)
  @Category(UnitTests.class)
  public void testParseDataPointWhenErrorMessageExists() {
    Map<String, Object> data = new HashMap<>();
    data.put(ERROR_MESSAGE_KEY, "Some error occurred");

    DataFetchDTO dataFetchDTO = getDataFetchDTO();

    Map<String, Object> result = (Map<String, Object>) systemExistsParser.parseDataPoint(data, dataFetchDTO);
    Map<String, Object> dataPointResponse = (Map<String, Object>) result.get("rule1");

    assertNotNull(dataPointResponse);
    assertFalse((Boolean) dataPointResponse.get(DATA_POINT_VALUE_KEY));
  }

  @Test
  @Owner(developers = AJINKYA_SHINGANE)
  @Category(UnitTests.class)
  public void testParseDataPointWhenErrorMessageDoesNotExist() {
    DataFetchDTO dataFetchDTO = getDataFetchDTO();

    Map<String, Object> result = (Map<String, Object>) systemExistsParser.parseDataPoint(new HashMap<>(), dataFetchDTO);
    Map<String, Object> dataPointResponse = (Map<String, Object>) result.get("rule1");

    assertNotNull(dataPointResponse);
    assertTrue((Boolean) dataPointResponse.get(DATA_POINT_VALUE_KEY));
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }

  private DataFetchDTO getDataFetchDTO() {
    return DataFetchDTO.builder().ruleIdentifier(RULE_IDENTIFIER1).build();
  }
}
