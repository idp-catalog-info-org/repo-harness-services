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
public class BackstageEnvSecretCreateEventTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String TEST_ENV_NAME = "TEST_ENV_VAR";
  private static final String TEST_SECRET_ID = "test-secret-id";
  private BackstageEnvSecretVariable testEnvVariable;

  @Before
  public void setUp() {
    testEnvVariable = new BackstageEnvSecretVariable();
    testEnvVariable.setEnvName(TEST_ENV_NAME);
    testEnvVariable.setHarnessSecretIdentifier(TEST_SECRET_ID);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testBackstageEnvSecretCreateEvent_Construction() {
    BackstageEnvSecretCreateEvent event = new BackstageEnvSecretCreateEvent(TEST_ACCOUNT_IDENTIFIER, testEnvVariable);

    assertEquals(TEST_ACCOUNT_IDENTIFIER, event.getAccountIdentifier());
    assertNotNull(event.getNewBackstageEnvSecretVariable());
    assertEquals(TEST_ENV_NAME, event.getNewBackstageEnvSecretVariable().getEnvName());
    assertEquals(TEST_SECRET_ID, event.getNewBackstageEnvSecretVariable().getHarnessSecretIdentifier());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testBackstageEnvSecretCreateEvent_EventType() {
    BackstageEnvSecretCreateEvent event = new BackstageEnvSecretCreateEvent(TEST_ACCOUNT_IDENTIFIER, testEnvVariable);

    assertEquals(BackstageEnvSecretCreateEvent.ENV_VARIABLE_CREATED, event.getEventType());
    assertEquals("EnvVariableCreated", event.getEventType());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testBackstageEnvSecretCreateEvent_ResourceScope() {
    BackstageEnvSecretCreateEvent event = new BackstageEnvSecretCreateEvent(TEST_ACCOUNT_IDENTIFIER, testEnvVariable);

    ResourceScope scope = event.getResourceScope();

    assertNotNull(scope);
    assertTrue(scope instanceof AccountScope);
    assertEquals(TEST_ACCOUNT_IDENTIFIER, ((AccountScope) scope).getAccountIdentifier());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testBackstageEnvSecretCreateEvent_Resource() {
    BackstageEnvSecretCreateEvent event = new BackstageEnvSecretCreateEvent(TEST_ACCOUNT_IDENTIFIER, testEnvVariable);

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
  public void testBackstageEnvSecretCreateEvent_NoArgsConstructor() {
    BackstageEnvSecretCreateEvent event = new BackstageEnvSecretCreateEvent();

    assertNotNull(event);
  }
}
