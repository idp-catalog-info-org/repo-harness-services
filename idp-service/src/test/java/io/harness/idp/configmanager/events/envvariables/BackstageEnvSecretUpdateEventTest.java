/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.events.envvariables;

import static io.harness.audit.ResourceTypeConstants.IDP_CONFIG_ENV_VARIABLES;
import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ng.core.AccountScope;
import io.harness.ng.core.Resource;
import io.harness.ng.core.ResourceConstants;
import io.harness.ng.core.ResourceScope;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class BackstageEnvSecretUpdateEventTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String TEST_ENV_NAME = "TEST_ENV_VAR";
  private static final String OLD_SECRET_ID = "old-secret-id";
  private static final String NEW_SECRET_ID = "new-secret-id";
  private BackstageEnvSecretVariable oldEnvVariable;
  private BackstageEnvSecretVariable newEnvVariable;

  @Before
  public void setUp() {
    oldEnvVariable = new BackstageEnvSecretVariable();
    oldEnvVariable.setEnvName(TEST_ENV_NAME);
    oldEnvVariable.setHarnessSecretIdentifier(OLD_SECRET_ID);

    newEnvVariable = new BackstageEnvSecretVariable();
    newEnvVariable.setEnvName(TEST_ENV_NAME);
    newEnvVariable.setHarnessSecretIdentifier(NEW_SECRET_ID);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testBackstageEnvSecretUpdateEvent_Construction() {
    BackstageEnvSecretUpdateEvent event =
        new BackstageEnvSecretUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newEnvVariable, oldEnvVariable);

    assertEquals(TEST_ACCOUNT_IDENTIFIER, event.getAccountIdentifier());
    assertNotNull(event.getNewBackstageEnvSecretVariable());
    assertNotNull(event.getOldBackstageEnvSecretVariable());
    assertEquals(NEW_SECRET_ID, event.getNewBackstageEnvSecretVariable().getHarnessSecretIdentifier());
    assertEquals(OLD_SECRET_ID, event.getOldBackstageEnvSecretVariable().getHarnessSecretIdentifier());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testBackstageEnvSecretUpdateEvent_EventType() {
    BackstageEnvSecretUpdateEvent event =
        new BackstageEnvSecretUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newEnvVariable, oldEnvVariable);

    assertEquals(BackstageEnvSecretUpdateEvent.ENV_VARIABLE_UPDATED, event.getEventType());
    assertEquals("EnvVariableUpdated", event.getEventType());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testBackstageEnvSecretUpdateEvent_ResourceScope() {
    BackstageEnvSecretUpdateEvent event =
        new BackstageEnvSecretUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newEnvVariable, oldEnvVariable);

    ResourceScope scope = event.getResourceScope();

    assertNotNull(scope);
    assertTrue(scope instanceof AccountScope);
    assertEquals(TEST_ACCOUNT_IDENTIFIER, ((AccountScope) scope).getAccountIdentifier());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testBackstageEnvSecretUpdateEvent_Resource() {
    BackstageEnvSecretUpdateEvent event =
        new BackstageEnvSecretUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newEnvVariable, oldEnvVariable);

    Resource resource = event.getResource();

    assertNotNull(resource);
    assertEquals(TEST_ENV_NAME + "_" + TEST_ACCOUNT_IDENTIFIER, resource.getIdentifier());
    assertEquals(IDP_CONFIG_ENV_VARIABLES, resource.getType());
    assertNotNull(resource.getLabels());
    assertEquals("IDP - " + TEST_ENV_NAME, resource.getLabels().get(ResourceConstants.LABEL_KEY_RESOURCE_NAME));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testBackstageEnvSecretUpdateEvent_NoArgsConstructor() {
    BackstageEnvSecretUpdateEvent event = new BackstageEnvSecretUpdateEvent();

    assertNotNull(event);
  }
}
