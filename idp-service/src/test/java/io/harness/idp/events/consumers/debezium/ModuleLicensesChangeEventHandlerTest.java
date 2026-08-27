/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.events.consumers.debezium;

import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.integrations.service.git.GitIntegrationServiceImpl;
import io.harness.idp.namespace.service.NamespaceService;
import io.harness.idp.provision.service.ProvisionService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.NamespaceInfo;

import java.sql.SQLException;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ModuleLicensesChangeEventHandlerTest extends CategoryTest {
  static final String TEST_ID = "test-id";
  static final String TEST_VALUE = "{\"accountIdentifier\":\"testAccount123\",\"moduleType\":\"IDP\"}";
  static final String TEST_VALUE_CODE = "{\"accountIdentifier\":\"testAccount123\",\"moduleType\":\"CODE\"}";

  AutoCloseable openMocks;
  @InjectMocks ModuleLicensesChangeEventHandler moduleLicensesChangeEventHandler;
  @Mock ProvisionService provisionService;
  @Mock NamespaceService namespaceService;
  @Mock GitIntegrationServiceImpl gitIntegrationService;

  @Before
  public void setUp() throws SQLException, IllegalAccessException {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleCreateEvent() {
    when(provisionService.provision("testAccount123")).thenReturn(new NamespaceInfo());
    boolean result = moduleLicensesChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleCreateEventCodeModule() {
    when(namespaceService.getAccountIdpStatus("testAccount123")).thenReturn(true);
    doNothing()
        .when(gitIntegrationService)
        .setupDefaultConnectorLessManagedHarnessCodeRepoIntegrationIfNotAlready("testAccount123");
    boolean result = moduleLicensesChangeEventHandler.handleCreateEvent(TEST_ID, TEST_VALUE_CODE);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleDeleteEvent() {
    boolean result = moduleLicensesChangeEventHandler.handleDeleteEvent(TEST_ID);
    assertTrue(result);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testHandleUpdateEvent() {
    boolean result = moduleLicensesChangeEventHandler.handleUpdateEvent(TEST_ID, TEST_VALUE);
    assertTrue(result);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
