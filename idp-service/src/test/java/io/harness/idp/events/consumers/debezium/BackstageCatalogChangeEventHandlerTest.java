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
public class BackstageCatalogChangeEventHandlerTest extends CategoryTest {
  static final String TEST_ID = "test-id";
  static final String TEST_VALUE = "  {\n"
      + "    \"spec\" : {\n"
      + "                     \"type\" : \"Service\",\n"
      + "                     \"lifecycle\" : \"Unknown\",\n"
      + "                     \"owner\" : \"Unknown\",\n"
      + "                     \"system\" : \"harness\"\n"
      + "                 },\n"
      + "                 \"accountIdentifier\" : \"kmpySmUISimoRrJL6NL73w\",\n"
      + "                 \"entityUid\" : \"Component/default/service2\",\n"
      + "                 \"apiVersion\" : \"backstage.io/v1alpha1\",\n"
      + "                 \"metadata\" : {\n"
      + "                     \"name\" : \"service2\"\n"
      + "                 },\n"
      + "                 \"kind\" : \"Component\",\n"
      + "                 \"relations\" : [\n"
      + "                     {\n"
      + "                         \"type\" : \"partOf\",\n"
      + "                         \"targetRef\" : \"system:default/harness\",\n"
      + "                         \"target\" : {\n"
      + "                             \"kind\" : \"system\",\n"
      + "                             \"namespace\" : \"default\",\n"
      + "                             \"name\" : \"harness\"\n"
      + "                         }\n"
      + "                     },\n"
      + "                     {\n"
      + "                         \"type\" : \"ownedBy\",\n"
      + "                         \"targetRef\" : \"group:default/unknown\",\n"
      + "                         \"target\" : {\n"
      + "                             \"kind\" : \"group\",\n"
      + "                             \"namespace\" : \"default\",\n"
      + "                             \"name\" : \"unknown\"\n"
      + "                         }\n"
      + "                     }\n"
      + "                 ],\n"
      + "                 \"createdAt\" : 1703048175547,\n"
      + "                 \"lastUpdatedAt\" : 1707300757972\n"
      + "  }";

  AutoCloseable openMocks;
  private MockDataProvider provider;
  @InjectMocks BackstageCatalogChangeEventHandler backstageCatalogChangeEventHandler;

  @Before
  public void setUp() throws SQLException, IllegalAccessException {
    openMocks = MockitoAnnotations.openMocks(this);

    provider = mock(MockDataProvider.class);

    final MockConnection connection = new MockConnection(provider);
    final DSLContext dslContext = DSL.using(connection, SQLDialect.POSTGRES);

    FieldUtils.writeField(backstageCatalogChangeEventHandler, "dsl", dslContext, true);

    final MockResult[] mockResults = {new MockResult(1)};
    when(provider.execute(any())).thenReturn(mockResults);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleCreateEvent() {
    boolean result = backstageCatalogChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleCreateEventException() throws SQLException {
    given(provider.execute(any())).willAnswer(invocation -> { throw new DataAccessException("Exception Throw"); });
    boolean result = backstageCatalogChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE);
    assertFalse(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleDeleteEvent() {
    boolean result = backstageCatalogChangeEventHandler.handleDeleteEvent(TEST_ID);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleDeleteEventException() throws SQLException {
    given(provider.execute(any())).willAnswer(invocation -> { throw new DataAccessException("Exception Throw"); });
    boolean result = backstageCatalogChangeEventHandler.handleDeleteEvent(TEST_ID);
    assertFalse(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleUpdateEvent() {
    boolean result = backstageCatalogChangeEventHandler.handleUpdateEvent(TEST_ID, TEST_VALUE);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleUpdateEventException() throws SQLException {
    given(provider.execute(any())).willAnswer(invocation -> { throw new DataAccessException("Exception Throw"); });
    boolean result = backstageCatalogChangeEventHandler.handleUpdateEvent(TEST_ID, TEST_VALUE);
    assertFalse(result);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
