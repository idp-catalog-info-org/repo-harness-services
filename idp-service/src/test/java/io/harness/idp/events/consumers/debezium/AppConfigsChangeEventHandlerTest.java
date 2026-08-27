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
public class AppConfigsChangeEventHandlerTest extends CategoryTest {
  static final String TEST_ID = "test-id";
  static final String TEST_VALUE =
      "{\"configType\":\"PLUGIN\",\"accountIdentifier\":\"testAccount123\",\"configId\":\"testConfigId\","
      + "\"configName\":\"testConfigName\",\"enabled\":true,\"createdAt\":123,\"lastModifiedAt\":456}";
  static final String TEST_VALUE_INVALID =
      "{\"configType\":\"test\",\"accountIdentifier\":\"testAccount123\",\"configId\":\"testConfigId\",\"configName\":"
      + "\"testConfigName\",\"enabled\":true,\"createdAt\":123,\"lastModifiedAt\":456}";

  AutoCloseable openMocks;
  private MockDataProvider provider;
  @InjectMocks AppConfigsChangeEventHandler appConfigsChangeEventHandler;

  @Before
  public void setUp() throws SQLException, IllegalAccessException {
    openMocks = MockitoAnnotations.openMocks(this);

    provider = mock(MockDataProvider.class);

    final MockConnection connection = new MockConnection(provider);
    final DSLContext dslContext = DSL.using(connection, SQLDialect.POSTGRES);

    FieldUtils.writeField(appConfigsChangeEventHandler, "dsl", dslContext, true);

    final MockResult[] mockResults = {new MockResult(1)};
    when(provider.execute(any())).thenReturn(mockResults);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleCreateEvent() {
    boolean result = appConfigsChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleCreateEventInvalidEvent() {
    boolean result = appConfigsChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE_INVALID);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleCreateEventException() throws SQLException {
    given(provider.execute(any())).willAnswer(invocation -> { throw new DataAccessException("Exception Throw"); });
    boolean result = appConfigsChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE);
    assertFalse(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleDeleteEvent() {
    boolean result = appConfigsChangeEventHandler.handleDeleteEvent(TEST_ID);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleDeleteEventException() throws SQLException {
    given(provider.execute(any())).willAnswer(invocation -> { throw new DataAccessException("Exception Throw"); });
    boolean result = appConfigsChangeEventHandler.handleDeleteEvent(TEST_ID);
    assertFalse(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleUpdateEvent() {
    boolean result = appConfigsChangeEventHandler.handleUpdateEvent(TEST_ID, TEST_VALUE);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleUpdateEventInvalidEvent() {
    boolean result = appConfigsChangeEventHandler.handleUpdateEvent(TEST_ID, TEST_VALUE_INVALID);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleUpdateEventException() throws SQLException {
    given(provider.execute(any())).willAnswer(invocation -> { throw new DataAccessException("Exception Throw"); });
    boolean result = appConfigsChangeEventHandler.handleUpdateEvent(TEST_ID, TEST_VALUE);
    assertFalse(result);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
