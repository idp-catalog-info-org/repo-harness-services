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
public class OAuthConfigCreateEventTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String TEST_AUTH_ID = "github-auth";
  private static final String TEST_ENV_VAR_NAME = "GITHUB_CLIENT_ID";
  private static final String TEST_ENV_VAR_VALUE = "test-client-id";
  private List<BackstageEnvVariable> testEnvVariables;

  @Before
  public void setUp() {
    BackstageEnvConfigVariable envVariable = new BackstageEnvConfigVariable();
    envVariable.setEnvName(TEST_ENV_VAR_NAME);
    envVariable.setValue(TEST_ENV_VAR_VALUE);
    envVariable.setType(BackstageEnvVariable.TypeEnum.CONFIG);

    testEnvVariables = Arrays.asList(envVariable);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testOAuthConfigCreateEvent_Construction() {
    OAuthConfigCreateEvent event = new OAuthConfigCreateEvent(TEST_ACCOUNT_IDENTIFIER, TEST_AUTH_ID, testEnvVariables);

    assertEquals(TEST_ACCOUNT_IDENTIFIER, event.getAccountIdentifier());
    assertEquals(TEST_AUTH_ID, event.getAuthId());
    assertNotNull(event.getNewBackstageEnvVariables());
    assertEquals(1, event.getNewBackstageEnvVariables().size());
    assertEquals(TEST_ENV_VAR_NAME, event.getNewBackstageEnvVariables().get(0).getEnvName());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testOAuthConfigCreateEvent_EventType() {
    OAuthConfigCreateEvent event = new OAuthConfigCreateEvent(TEST_ACCOUNT_IDENTIFIER, TEST_AUTH_ID, testEnvVariables);

    assertEquals(OAuthConfigCreateEvent.OAUTH_CONFIG_CREATED, event.getEventType());
    assertEquals("OAuthConfigCreated", event.getEventType());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testOAuthConfigCreateEvent_ResourceScope() {
    OAuthConfigCreateEvent event = new OAuthConfigCreateEvent(TEST_ACCOUNT_IDENTIFIER, TEST_AUTH_ID, testEnvVariables);

    ResourceScope scope = event.getResourceScope();

    assertNotNull(scope);
    assertTrue(scope instanceof AccountScope);
    assertEquals(TEST_ACCOUNT_IDENTIFIER, ((AccountScope) scope).getAccountIdentifier());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testOAuthConfigCreateEvent_Resource() {
    OAuthConfigCreateEvent event = new OAuthConfigCreateEvent(TEST_ACCOUNT_IDENTIFIER, TEST_AUTH_ID, testEnvVariables);

    Resource resource = event.getResource();

    assertNotNull(resource);
    assertEquals(TEST_AUTH_ID + "_" + TEST_ACCOUNT_IDENTIFIER, resource.getIdentifier());
    assertEquals(IDP_OAUTH_CONFIG, resource.getType());
    assertNotNull(resource.getLabels());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testOAuthConfigCreateEvent_NoArgsConstructor() {
    OAuthConfigCreateEvent event = new OAuthConfigCreateEvent();

    assertNotNull(event);
  }
}
