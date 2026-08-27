/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers.debezium;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

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
public class CatalogChangeEventHandlerTest extends CategoryTest {
  static final String TEST_ID = "test-catalog-id";
  static final String TEST_VALUE = "{\n"
      + "  \"accountIdentifier\": \"testAccount123\",\n"
      + "  \"orgIdentifier\": \"testOrg\",\n"
      + "  \"projectIdentifier\": \"testProject\",\n"
      + "  \"identifier\": \"test-service\",\n"
      + "  \"kind\": \"Component\",\n"
      + "  \"name\": \"Test Service\",\n"
      + "  \"type\": \"service\",\n"
      + "  \"owner\": \"team-a\",\n"
      + "  \"relations\": [\n"
      + "    {\n"
      + "      \"type\": \"ownedBy\",\n"
      + "      \"targetRef\": \"group:default/team-a\"\n"
      + "    },\n"
      + "    {\n"
      + "      \"type\": \"partOf\",\n"
      + "      \"targetRef\": \"system:default/test-system\"\n"
      + "    }\n"
      + "  ],\n"
      + "  \"tags\": [\"backend\", \"microservice\", \"java\"],\n"
      + "  \"createdAt\": 1703048175547,\n"
      + "  \"lastUpdatedAt\": 1707300757972\n"
      + "}";

  static final String TEST_VALUE_WITH_MINIMAL_FIELDS = "{\n"
      + "  \"accountIdentifier\": \"testAccount123\",\n"
      + "  \"identifier\": \"minimal-service\",\n"
      + "  \"kind\": \"Component\"\n"
      + "}";

  static final String TEST_VALUE_WITHOUT_PROJECT = "{\n"
      + "  \"accountIdentifier\": \"testAccount123\",\n"
      + "  \"orgIdentifier\": \"testOrg\",\n"
      + "  \"identifier\": \"org-level-service\",\n"
      + "  \"kind\": \"Component\",\n"
      + "  \"name\": \"Org Level Service\"\n"
      + "}";

  static final String TEST_VALUE_WITH_TAGS = "{\n"
      + "  \"accountIdentifier\": \"testAccount123\",\n"
      + "  \"identifier\": \"service-with-tags\",\n"
      + "  \"kind\": \"Component\",\n"
      + "  \"tags\": [\"frontend\", \"react\", \"production\"]\n"
      + "}";

  static final String TEST_VALUE_WITH_RELATIONS = "{\n"
      + "  \"accountIdentifier\": \"testAccount123\",\n"
      + "  \"identifier\": \"service-with-relations\",\n"
      + "  \"kind\": \"Component\",\n"
      + "  \"relations\": [\n"
      + "    {\n"
      + "      \"type\": \"dependsOn\",\n"
      + "      \"targetRef\": \"component:default/database\"\n"
      + "    },\n"
      + "    {\n"
      + "      \"type\": \"dependsOn\",\n"
      + "      \"targetRef\": \"component:default/cache\"\n"
      + "    },\n"
      + "    {\n"
      + "      \"type\": \"ownedBy\",\n"
      + "      \"targetRef\": \"group:default/team-b\"\n"
      + "    }\n"
      + "  ]\n"
      + "}";

  AutoCloseable openMocks;
  private MockDataProvider provider;
  @InjectMocks CatalogChangeEventHandler catalogChangeEventHandler;

  @Before
  public void setUp() throws SQLException, IllegalAccessException {
    openMocks = MockitoAnnotations.openMocks(this);

    provider = mock(MockDataProvider.class);

    final MockConnection connection = new MockConnection(provider);
    final DSLContext dslContext = DSL.using(connection, SQLDialect.POSTGRES);

    FieldUtils.writeField(catalogChangeEventHandler, "dsl", dslContext, true);

    final MockResult[] mockResults = {new MockResult(1)};
    when(provider.execute(any())).thenReturn(mockResults);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleCreateEvent() {
    boolean result = catalogChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE);
    assertTrue(result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleCreateEventWithMinimalFields() {
    boolean result = catalogChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE_WITH_MINIMAL_FIELDS);
    assertTrue(result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleCreateEventWithoutProject() {
    boolean result = catalogChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE_WITHOUT_PROJECT);
    assertTrue(result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleCreateEventException() throws SQLException {
    given(provider.execute(any())).willAnswer(invocation -> { throw new DataAccessException("Exception Throw"); });
    boolean result = catalogChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE);
    assertFalse(result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleDeleteEvent() {
    boolean result = catalogChangeEventHandler.handleDeleteEvent(TEST_ID);
    assertTrue(result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleDeleteEventException() throws SQLException {
    given(provider.execute(any())).willAnswer(invocation -> { throw new DataAccessException("Exception Throw"); });
    boolean result = catalogChangeEventHandler.handleDeleteEvent(TEST_ID);
    assertFalse(result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleUpdateEvent() {
    boolean result = catalogChangeEventHandler.handleUpdateEvent(TEST_ID, TEST_VALUE);
    assertTrue(result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleUpdateEventWithMinimalFields() {
    boolean result = catalogChangeEventHandler.handleUpdateEvent(TEST_ID, TEST_VALUE_WITH_MINIMAL_FIELDS);
    assertTrue(result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleUpdateEventException() throws SQLException {
    given(provider.execute(any())).willAnswer(invocation -> { throw new DataAccessException("Exception Throw"); });
    boolean result = catalogChangeEventHandler.handleUpdateEvent(TEST_ID, TEST_VALUE);
    assertFalse(result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleCreateEventWithTags() {
    boolean result = catalogChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE_WITH_TAGS);
    assertTrue(result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleCreateEventWithRelations() {
    boolean result = catalogChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE_WITH_RELATIONS);
    assertTrue(result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleUpdateEventWithTags() {
    boolean result = catalogChangeEventHandler.handleUpdateEvent(TEST_ID, TEST_VALUE_WITH_TAGS);
    assertTrue(result);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testHandleUpdateEventWithRelations() {
    boolean result = catalogChangeEventHandler.handleUpdateEvent(TEST_ID, TEST_VALUE_WITH_RELATIONS);
    assertTrue(result);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}