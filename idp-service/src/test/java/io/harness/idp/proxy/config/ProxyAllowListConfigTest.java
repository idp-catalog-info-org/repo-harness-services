/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.proxy.config;

import static io.harness.rule.OwnerRule.NISARG;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ProxyAllowListConfigTest extends CategoryTest {
  private static final String SERVICE_NAME = "test-service";
  private static final String PROXY_PATH = "/v1/idp-proxy-service/test";
  private static final String BASE_URL = "http://localhost:8080";
  private static final String SECRET = "test-secret";

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testProxyAllowListConfigCreation() {
    Map<String, ProxyAllowListConfig.ServiceDefinitionConfig> services = new HashMap<>();
    ProxyAllowListConfig config = ProxyAllowListConfig.builder().services(services).build();

    assertNotNull(config);
    assertNotNull(config.getServices());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testServiceDefinitionConfigCreation() {
    List<String> allowList = new ArrayList<>();
    allowList.add("/api/endpoint1");
    allowList.add("/api/endpoint2");

    ServiceHttpClientConfig clientConfig = ServiceHttpClientConfig.builder().baseUrl(BASE_URL).build();

    ProxyAllowListConfig.ServiceDefinitionConfig serviceConfig = ProxyAllowListConfig.ServiceDefinitionConfig.builder()
                                                                     .proxyPath(PROXY_PATH)
                                                                     .clientConfig(clientConfig)
                                                                     .secret(SECRET)
                                                                     .allowList(allowList)
                                                                     .build();

    assertNotNull(serviceConfig);
    assertEquals(PROXY_PATH, serviceConfig.getProxyPath());
    assertEquals(clientConfig, serviceConfig.getClientConfig());
    assertEquals(SECRET, serviceConfig.getSecret());
    assertEquals(allowList, serviceConfig.getAllowList());
    assertEquals(2, serviceConfig.getAllowList().size());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testServiceDefinitionConfigWithEncryptionList() {
    List<String> shouldEncryptResponseList = new ArrayList<>();
    shouldEncryptResponseList.add("/api/secrets");

    ProxyAllowListConfig.ServiceDefinitionConfig serviceConfig =
        ProxyAllowListConfig.ServiceDefinitionConfig.builder()
            .proxyPath(PROXY_PATH)
            .secret(SECRET)
            .shouldEncryptResponseList(shouldEncryptResponseList)
            .build();

    assertNotNull(serviceConfig.getShouldEncryptResponseList());
    assertEquals(1, serviceConfig.getShouldEncryptResponseList().size());
    assertEquals("/api/secrets", serviceConfig.getShouldEncryptResponseList().get(0));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testProxyAllowListConfigWithMultipleServices() {
    Map<String, ProxyAllowListConfig.ServiceDefinitionConfig> services = new HashMap<>();

    List<String> allowList1 = new ArrayList<>();
    allowList1.add("/api/endpoint1");

    List<String> allowList2 = new ArrayList<>();
    allowList2.add("/api/endpoint2");

    ProxyAllowListConfig.ServiceDefinitionConfig service1 = ProxyAllowListConfig.ServiceDefinitionConfig.builder()
                                                                .proxyPath("/service1")
                                                                .secret("secret1")
                                                                .allowList(allowList1)
                                                                .build();

    ProxyAllowListConfig.ServiceDefinitionConfig service2 = ProxyAllowListConfig.ServiceDefinitionConfig.builder()
                                                                .proxyPath("/service2")
                                                                .secret("secret2")
                                                                .allowList(allowList2)
                                                                .build();

    services.put("service1", service1);
    services.put("service2", service2);

    ProxyAllowListConfig config = ProxyAllowListConfig.builder().services(services).build();

    assertEquals(2, config.getServices().size());
    assertEquals(service1, config.getServices().get("service1"));
    assertEquals(service2, config.getServices().get("service2"));
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testServiceDefinitionConfigWithClientConfig() {
    ServiceHttpClientConfig clientConfig =
        ServiceHttpClientConfig.builder().baseUrl(BASE_URL).connectTimeOutSeconds(30L).readTimeOutSeconds(60L).build();

    ProxyAllowListConfig.ServiceDefinitionConfig serviceConfig = ProxyAllowListConfig.ServiceDefinitionConfig.builder()
                                                                     .proxyPath(PROXY_PATH)
                                                                     .clientConfig(clientConfig)
                                                                     .secret(SECRET)
                                                                     .build();

    assertNotNull(serviceConfig.getClientConfig());
    assertEquals(BASE_URL, serviceConfig.getClientConfig().getBaseUrl());
    assertEquals(Long.valueOf(30L), serviceConfig.getClientConfig().getConnectTimeOutSeconds());
    assertEquals(Long.valueOf(60L), serviceConfig.getClientConfig().getReadTimeOutSeconds());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testServiceDefinitionConfigWithEmptyLists() {
    ProxyAllowListConfig.ServiceDefinitionConfig serviceConfig = ProxyAllowListConfig.ServiceDefinitionConfig.builder()
                                                                     .proxyPath(PROXY_PATH)
                                                                     .secret(SECRET)
                                                                     .allowList(new ArrayList<>())
                                                                     .shouldEncryptResponseList(new ArrayList<>())
                                                                     .build();

    assertNotNull(serviceConfig);
    assertNotNull(serviceConfig.getAllowList());
    assertNotNull(serviceConfig.getShouldEncryptResponseList());
    assertEquals(0, serviceConfig.getAllowList().size());
    assertEquals(0, serviceConfig.getShouldEncryptResponseList().size());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testProxyAllowListConfigWithEmptyServices() {
    ProxyAllowListConfig config = ProxyAllowListConfig.builder().services(new HashMap<>()).build();

    assertNotNull(config);
    assertNotNull(config.getServices());
    assertEquals(0, config.getServices().size());
  }

  @Test
  @Owner(developers = NISARG)
  @Category(UnitTests.class)
  public void testServiceDefinitionConfigWithAllFields() {
    List<String> allowList = new ArrayList<>();
    allowList.add("/api/users");
    allowList.add("/api/groups");

    List<String> encryptList = new ArrayList<>();
    encryptList.add("/api/secrets/.*");

    ServiceHttpClientConfig clientConfig = ServiceHttpClientConfig.builder().baseUrl(BASE_URL).build();

    ProxyAllowListConfig.ServiceDefinitionConfig serviceConfig = ProxyAllowListConfig.ServiceDefinitionConfig.builder()
                                                                     .proxyPath(PROXY_PATH)
                                                                     .clientConfig(clientConfig)
                                                                     .secret(SECRET)
                                                                     .allowList(allowList)
                                                                     .shouldEncryptResponseList(encryptList)
                                                                     .build();

    assertEquals(PROXY_PATH, serviceConfig.getProxyPath());
    assertEquals(clientConfig, serviceConfig.getClientConfig());
    assertEquals(SECRET, serviceConfig.getSecret());
    assertEquals(2, serviceConfig.getAllowList().size());
    assertEquals(1, serviceConfig.getShouldEncryptResponseList().size());
  }
}
