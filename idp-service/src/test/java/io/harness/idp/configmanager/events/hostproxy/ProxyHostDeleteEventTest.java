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
public class ProxyHostDeleteEventTest extends CategoryTest {
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-id";
  private static final String TEST_HOST = "test-host.example.com";
  private ProxyHostDetail testProxyHostDetail;

  @Before
  public void setUp() {
    testProxyHostDetail = new ProxyHostDetail();
    testProxyHostDetail.setHost(TEST_HOST);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProxyHostDeleteEvent_Construction() {
    ProxyHostDeleteEvent event = new ProxyHostDeleteEvent(TEST_ACCOUNT_IDENTIFIER, testProxyHostDetail);

    assertEquals(TEST_ACCOUNT_IDENTIFIER, event.getAccountIdentifier());
    assertNotNull(event.getOldProxyHostDetail());
    assertEquals(TEST_HOST, event.getOldProxyHostDetail().getHost());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProxyHostDeleteEvent_EventType() {
    ProxyHostDeleteEvent event = new ProxyHostDeleteEvent(TEST_ACCOUNT_IDENTIFIER, testProxyHostDetail);

    assertEquals(ProxyHostDeleteEvent.PROXY_HOST_DELETED, event.getEventType());
    assertEquals("HostProxyDeleted", event.getEventType());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProxyHostDeleteEvent_ResourceScope() {
    ProxyHostDeleteEvent event = new ProxyHostDeleteEvent(TEST_ACCOUNT_IDENTIFIER, testProxyHostDetail);

    ResourceScope scope = event.getResourceScope();

    assertNotNull(scope);
    assertTrue(scope instanceof AccountScope);
    assertEquals(TEST_ACCOUNT_IDENTIFIER, ((AccountScope) scope).getAccountIdentifier());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProxyHostDeleteEvent_Resource() {
    ProxyHostDeleteEvent event = new ProxyHostDeleteEvent(TEST_ACCOUNT_IDENTIFIER, testProxyHostDetail);

    Resource resource = event.getResource();

    assertNotNull(resource);
    assertEquals(TEST_HOST + "_" + TEST_ACCOUNT_IDENTIFIER, resource.getIdentifier());
    assertEquals(IDP_PROXY_HOST, resource.getType());
    assertNotNull(resource.getLabels());
    assertEquals(TEST_HOST, resource.getLabels().get(ResourceConstants.LABEL_KEY_RESOURCE_NAME));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testProxyHostDeleteEvent_NoArgsConstructor() {
    ProxyHostDeleteEvent event = new ProxyHostDeleteEvent();

    assertNotNull(event);
  }
}
