/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.events.hostproxy;

import static io.harness.audit.ResourceTypeConstants.IDP_PROXY_HOST;
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
import io.harness.spec.server.idp.v1.model.ProxyHostDetail;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ProxyHostUpdateEventTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String OLD_HOST = "old-host.example.com";
  private static final String NEW_HOST = "new-host.example.com";
  private ProxyHostDetail oldProxyHostDetail;
  private ProxyHostDetail newProxyHostDetail;

  @Before
  public void setUp() {
    oldProxyHostDetail = new ProxyHostDetail();
    oldProxyHostDetail.setHost(OLD_HOST);

    newProxyHostDetail = new ProxyHostDetail();
    newProxyHostDetail.setHost(NEW_HOST);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProxyHostUpdateEvent_Construction() {
    ProxyHostUpdateEvent event =
        new ProxyHostUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newProxyHostDetail, oldProxyHostDetail);

    assertEquals(TEST_ACCOUNT_IDENTIFIER, event.getAccountIdentifier());
    assertNotNull(event.getNewProxyHostDetail());
    assertNotNull(event.getOldProxyHostDetail());
    assertEquals(NEW_HOST, event.getNewProxyHostDetail().getHost());
    assertEquals(OLD_HOST, event.getOldProxyHostDetail().getHost());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProxyHostUpdateEvent_EventType() {
    ProxyHostUpdateEvent event =
        new ProxyHostUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newProxyHostDetail, oldProxyHostDetail);

    assertEquals(ProxyHostUpdateEvent.PROXY_HOST_UPDATED, event.getEventType());
    assertEquals("HostProxyUpdated", event.getEventType());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProxyHostUpdateEvent_ResourceScope() {
    ProxyHostUpdateEvent event =
        new ProxyHostUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newProxyHostDetail, oldProxyHostDetail);

    ResourceScope scope = event.getResourceScope();

    assertNotNull(scope);
    assertTrue(scope instanceof AccountScope);
    assertEquals(TEST_ACCOUNT_IDENTIFIER, ((AccountScope) scope).getAccountIdentifier());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProxyHostUpdateEvent_Resource() {
    ProxyHostUpdateEvent event =
        new ProxyHostUpdateEvent(TEST_ACCOUNT_IDENTIFIER, newProxyHostDetail, oldProxyHostDetail);

    Resource resource = event.getResource();

    assertNotNull(resource);
    assertEquals(NEW_HOST + "_" + TEST_ACCOUNT_IDENTIFIER, resource.getIdentifier());
    assertEquals(IDP_PROXY_HOST, resource.getType());
    assertNotNull(resource.getLabels());
    assertEquals(NEW_HOST, resource.getLabels().get(ResourceConstants.LABEL_KEY_RESOURCE_NAME));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProxyHostUpdateEvent_NoArgsConstructor() {
    ProxyHostUpdateEvent event = new ProxyHostUpdateEvent();

    assertNotNull(event);
  }
}
