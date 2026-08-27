/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.events.oauth;

import static io.harness.audit.ResourceTypeConstants.IDP_OAUTH_CONFIG;
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
import io.harness.ng.core.ResourceScope;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class OAuthConfigUpdateEventTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String TEST_AUTH_ID = "github-auth";
  private static final String TEST_ENV_VAR_NAME = "GITHUB_CLIENT_ID";
  private static final String OLD_ENV_VAR_VALUE = "old-client-id";
  private static final String NEW_ENV_VAR_VALUE = "new-client-id";
  private List<BackstageEnvVariable> oldEnvVariables;
  private List<BackstageEnvVariable> newEnvVariables;

  @Before
  public void setUp() {
    BackstageEnvConfigVariable oldEnvVariable = new BackstageEnvConfigVariable();
    oldEnvVariable.setEnvName(TEST_ENV_VAR_NAME);
    oldEnvVariable.setValue(OLD_ENV_VAR_VALUE);
    oldEnvVariable.setType(BackstageEnvVariable.TypeEnum.CONFIG);

    BackstageEnvConfigVariable newEnvVariable = new BackstageEnvConfigVariable();
    newEnvVariable.setEnvName(TEST_ENV_VAR_NAME);
    newEnvVariable.setValue(NEW_ENV_VAR_VALUE);
    newEnvVariable.setType(BackstageEnvVariable.TypeEnum.CONFIG);

    oldEnvVariables = Arrays.asList(oldEnvVariable);
    newEnvVariables = Arrays.asList(newEnvVariable);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testOAuthConfigUpdateEvent_Construction() {
    OAuthConfigUpdateEvent event =
        new OAuthConfigUpdateEvent(TEST_ACCOUNT_IDENTIFIER, TEST_AUTH_ID, newEnvVariables, oldEnvVariables);

    assertEquals(TEST_ACCOUNT_IDENTIFIER, event.getAccountIdentifier());
    assertEquals(TEST_AUTH_ID, event.getAuthId());
    assertNotNull(event.getNewBackstageEnvVariables());
    assertNotNull(event.getOldBackstageEnvVariables());
    assertEquals(1, event.getNewBackstageEnvVariables().size());
    assertEquals(1, event.getOldBackstageEnvVariables().size());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testOAuthConfigUpdateEvent_EventType() {
    OAuthConfigUpdateEvent event =
        new OAuthConfigUpdateEvent(TEST_ACCOUNT_IDENTIFIER, TEST_AUTH_ID, newEnvVariables, oldEnvVariables);

    assertEquals(OAuthConfigUpdateEvent.OAUTH_CONFIG_UPDATED, event.getEventType());
    assertEquals("OAuthConfigUpdated", event.getEventType());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testOAuthConfigUpdateEvent_ResourceScope() {
    OAuthConfigUpdateEvent event =
        new OAuthConfigUpdateEvent(TEST_ACCOUNT_IDENTIFIER, TEST_AUTH_ID, newEnvVariables, oldEnvVariables);

    ResourceScope scope = event.getResourceScope();

    assertNotNull(scope);
    assertTrue(scope instanceof AccountScope);
    assertEquals(TEST_ACCOUNT_IDENTIFIER, ((AccountScope) scope).getAccountIdentifier());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testOAuthConfigUpdateEvent_Resource() {
    OAuthConfigUpdateEvent event =
        new OAuthConfigUpdateEvent(TEST_ACCOUNT_IDENTIFIER, TEST_AUTH_ID, newEnvVariables, oldEnvVariables);

    Resource resource = event.getResource();

    assertNotNull(resource);
    assertEquals(TEST_AUTH_ID + "_" + TEST_ACCOUNT_IDENTIFIER, resource.getIdentifier());
    assertEquals(IDP_OAUTH_CONFIG, resource.getType());
    assertNotNull(resource.getLabels());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testOAuthConfigUpdateEvent_NoArgsConstructor() {
    OAuthConfigUpdateEvent event = new OAuthConfigUpdateEvent();

    assertNotNull(event);
  }
}
