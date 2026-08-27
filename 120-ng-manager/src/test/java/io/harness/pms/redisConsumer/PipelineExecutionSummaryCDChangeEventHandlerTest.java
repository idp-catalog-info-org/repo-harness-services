/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.redisConsumer;

import static io.harness.rule.OwnerRule.ABHINAV_MITTAL;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import java.io.IOException;
import java.net.URL;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.jooq.impl.TableImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class PipelineExecutionSummaryCDChangeEventHandlerTest {
  @Mock private ObjectMapper objectMapper;
  @Mock DSLContext dslContext;

  private AutoCloseable mocks;

  public final ObjectMapper objectMapperBehaviour = NG_DEFAULT_OBJECT_MAPPER;

  private final ClassLoader classLoader = this.getClass().getClassLoader();

  @InjectMocks private PipelineExecutionSummaryCDChangeEventHandler pipelineExecutionSummaryCDChangeEventHandler;

  @Before
  public void setUp() throws JsonProcessingException {
    mocks = MockitoAnnotations.openMocks(this);
    when(objectMapper.readTree(anyString())).thenAnswer(invocation -> {
      String json = invocation.getArgument(0, String.class);
      return objectMapperBehaviour.readTree(json);
    });

    when(dslContext.newRecord(any(TableImpl.class))).thenAnswer(invocation -> {
      TableImpl table = invocation.getArgument(0, TableImpl.class);
      return DSL.using(new DefaultConfiguration()).newRecord(table);
    });
  }

  @After
  public void tearDown() throws Exception {
    if (mocks != null) {
      mocks.close();
    }
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testCreateRecord() throws IOException {
    String st = readFile("debeziumHandlers/PipelineExecutionSummaryWIthModulesPresent.json");

    Record record = pipelineExecutionSummaryCDChangeEventHandler.createRecord(st, "66b24bf367e460494c9061db");

    assertThat(new Gson().toJson(record.intoMap()))
        .isEqualTo(
            "{\"id\":\"66b24bf367e460494c9061db\",\"accountid\":\"OgiB4-xETamKNVAz-wQRjw\",\"orgidentifier\":\"Ng_Pipelines_K8s_Organisations\",\"projectidentifier\":\"PipelinesApiAutomationProjectAzure6XnkE68S8G\",\"pipelineidentifier\":\"NGPipeAutoNGPipeAutoyZrySFuURQId4ejHtYwSLx1Mp\",\"name\":\"azurepipelinerollingrollbacktest\",\"status\":\"RUNNING\",\"moduleinfo_type\":\"CD\",\"startts\":1722960883149,\"planexecutionid\":\"bYyS1nb1TUKfDrentubOCg\",\"trigger_type\":\"MANUAL\",\"moduleinfo_author_id\":\"autocdpng\",\"triggered_by_id\":\"glYr0GmsRM6lvXXp5aWyUw\",\"unique_id\":\"XnjvVpFZRLmjci-uT0z-mw\"}");
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testCreateRecordWithModulesPresentModulesInfoAbsent() throws IOException {
    String st = readFile("debeziumHandlers/PipelineExecutionSummaryWIthModulesPresentButModuleInfoAbsent.json");

    Record record = pipelineExecutionSummaryCDChangeEventHandler.createRecord(st, "66b24bf367e460494c9061db");

    assertThat(new Gson().toJson(record.intoMap()))
        .isEqualTo(
            "{\"id\":\"66b24bf367e460494c9061db\",\"accountid\":\"OgiB4-xETamKNVAz-wQRjw\",\"orgidentifier\":\"Ng_Pipelines_K8s_Organisations\",\"projectidentifier\":\"PipelinesApiAutomationProjectAzure6XnkE68S8G\",\"pipelineidentifier\":\"NGPipeAutoNGPipeAutoyZrySFuURQId4ejHtYwSLx1Mp\",\"name\":\"azurepipelinerollingrollbacktest\",\"status\":\"RUNNING\",\"moduleinfo_type\":\"CD\",\"startts\":1722960883149,\"planexecutionid\":\"bYyS1nb1TUKfDrentubOCg\",\"trigger_type\":\"MANUAL\",\"moduleinfo_author_id\":\"autocdpng\",\"triggered_by_id\":\"glYr0GmsRM6lvXXp5aWyUw\",\"unique_id\":\"XnjvVpFZRLmjci-uT0z-mw\"}");
  }

  @Test
  @Owner(developers = ABHINAV_MITTAL)
  @Category(UnitTests.class)
  public void testCreateRecordWithModulesAndModulesInfoAbsent() throws IOException {
    String st = readFile("debeziumHandlers/PipelineExecutionSummaryWIthModulesAndModuleInfoAbsent.json");

    Record record = pipelineExecutionSummaryCDChangeEventHandler.createRecord(st, "66b24bf367e460494c9061db");

    assertThat(record).isNull();
  }

  private String readFile(String fileName) throws IOException {
    final URL testFile = classLoader.getResource(fileName);
    return Resources.toString(testFile, Charsets.UTF_8);
  }
}
