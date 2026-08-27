/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers.debezium;

import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.timescaledb.tables.records.ScorecardsRecord;

import java.sql.SQLException;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ScorecardsChangeEventHandlerTest extends CategoryTest {
  static final String TEST_ID = "test-id";
  static final String TEST_VALUE = "  {\n"
      + "    \"accountIdentifier\" : \"kmpySmUISimoRrJL6NL73w\",\n"
      + "                \"identifier\" : \"all\",\n"
      + "                \"name\" : \"all\",\n"
      + "                \"description\" : \"all\",\n"
      + "                \"filter\" : {\n"
      + "                    \"kind\" : \"component\",\n"
      + "                    \"type\" : \"service\",\n"
      + "                    \"owners\" : [\n"
      + "                        \"harness_account_all_users\"\n"
      + "                    ],\n"
      + "                    \"tags\" : [\n"
      + "                        \"java\",\n"
      + "                        \"go\"\n"
      + "                    ],\n"
      + "                    \"lifecycle\" : [\n"
      + "                        \"Unknown\"\n"
      + "                    ]\n"
      + "                },\n"
      + "                \"weightageStrategy\" : \"EQUAL_WEIGHTS\",\n"
      + "                \"checks\" : [\n"
      + "                    {\n"
      + "                        \"identifier\" : \"__HARNESS__bb1ae673-d05f-d705-21fd-58e6e73fd81c\",\n"
      + "                        \"weightage\" : 1.0,\n"
      + "                        \"isCustom\" : false\n"
      + "                    },\n"
      + "                    {\n"
      + "                        \"identifier\" : \"__HARNESS__c659266c-33f5-555f-95f5-be305f0es287\",\n"
      + "                        \"weightage\" : 1.0,\n"
      + "                        \"isCustom\" : false\n"
      + "                    }\n"
      + "                ],\n"
      + "                \"published\" : true,\n"
      + "                \"isDeleted\" : false,\n"
      + "                \"deletedAt\" : 0,\n"
      + "                \"createdBy\" : {\n"
      + "                    \"uuid\" : \"lv0euRhKRCyiXWzS7pOg6g\",\n"
      + "                    \"name\" : \"Admin\",\n"
      + "                    \"email\" : \"admin@harness.io\"\n"
      + "                },\n"
      + "                \"lastUpdatedBy\" : {\n"
      + "                    \"uuid\" : \"lv0euRhKRCyiXWzS7pOg6g\",\n"
      + "                    \"name\" : \"Admin\",\n"
      + "                    \"email\" : \"admin@harness.io\"\n"
      + "                },\n"
      + "                \"createdAt\" : 1700465026469,\n"
      + "                \"lastUpdatedAt\" : 1700465026469\n"
      + "  }";
  static final ScorecardsRecord scorecardsRecord = new ScorecardsRecord()
                                                       .setId("test-id")
                                                       .setAccountIdentifier("kmpySmUISimoRrJL6NL73w")
                                                       .setIdentifier("all")
                                                       .setDescription("all")
                                                       .setDeleted(false)
                                                       .setPublished(true);

  AutoCloseable openMocks;
  private MockDataProvider provider;
  @InjectMocks ScorecardsChangeEventHandler scorecardsChangeEventHandler;

  @Before
  public void setUp() throws SQLException, IllegalAccessException {
    openMocks = MockitoAnnotations.openMocks(this);

    provider = mock(MockDataProvider.class);

    final MockConnection connection = new MockConnection(provider);
    final DSLContext dslContext = DSL.using(connection, SQLDialect.POSTGRES);

    FieldUtils.writeField(scorecardsChangeEventHandler, "dsl", dslContext, true);

    final MockResult[] mockResults = {new MockResult(scorecardsRecord)};
    when(provider.execute(any())).thenReturn(mockResults);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleCreateEvent() {
    boolean result = scorecardsChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleCreateEventException() throws SQLException {
    given(provider.execute(any())).willAnswer(invocation -> { throw new DataAccessException("Exception Throw"); });
    boolean result = scorecardsChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE);
    assertFalse(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleDeleteEvent() {
    boolean result = scorecardsChangeEventHandler.handleDeleteEvent(TEST_ID);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleDeleteEventException() throws SQLException {
    given(provider.execute(any())).willAnswer(invocation -> { throw new DataAccessException("Exception Throw"); });
    boolean result = scorecardsChangeEventHandler.handleDeleteEvent(TEST_ID);
    assertFalse(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleUpdateEvent() {
    boolean result = scorecardsChangeEventHandler.handleUpdateEvent(TEST_ID, TEST_VALUE);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleUpdateEventException() throws SQLException {
    given(provider.execute(any())).willAnswer(invocation -> { throw new DataAccessException("Exception Throw"); });
    boolean result = scorecardsChangeEventHandler.handleUpdateEvent(TEST_ID, TEST_VALUE);
    assertFalse(result);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
