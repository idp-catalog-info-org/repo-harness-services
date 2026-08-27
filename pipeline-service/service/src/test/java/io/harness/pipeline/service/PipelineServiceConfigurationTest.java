/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pipeline.service;

import static io.harness.rule.OwnerRule.BRIJESH;
import static io.harness.rule.OwnerRule.MAYANK_AGARWAL;

import static junit.framework.TestCase.assertNotNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.dropwizard.bundles.swagger.SwaggerBundleConfiguration;
import io.harness.opaclient.OpaServiceConfiguration;
import io.harness.remote.client.ServiceHttpClientConfig;
import io.harness.rule.Owner;

import io.dropwizard.core.server.DefaultServerFactory;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class PipelineServiceConfigurationTest extends CategoryTest {
  @InjectMocks PipelineServiceConfiguration configuration;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = BRIJESH)
  @Category(UnitTests.class)
  public void testGetSwaggerBundleConfiguration() {
    SwaggerBundleConfiguration swaggerBundleConfiguration = configuration.getSwaggerBundleConfiguration();
    assertNotNull(swaggerBundleConfiguration);
    assertNotNull(swaggerBundleConfiguration.getResourcePackage());
    assertNotNull(swaggerBundleConfiguration.getTitle());
    assertNotNull(swaggerBundleConfiguration.getVersion());
    assertNotNull(swaggerBundleConfiguration.getSchemes());
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetDbAliases() {
    List<String> dbAliases = configuration.getDbAliases();
    assertThat(dbAliases).isNotNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetOasConfig() {
    assertThat(configuration.getOasConfig()).isNotNull();
    assertThat(configuration.getOasConfig().getOpenAPI()).isNotNull();
    assertThat(configuration.getOasConfig().getOpenAPI().getInfo()).isNotNull();
    assertThat(configuration.getOasConfig().getOpenAPI().getInfo().getTitle())
        .isEqualTo("Pipeline Service API Reference");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetOpenApiResources() {
    assertThat(PipelineServiceConfiguration.getOpenApiResources()).isNotNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetResourceClasses() {
    assertThat(PipelineServiceConfiguration.getResourceClasses()).isNotNull();
    // Note: Resource classes may be empty in test environment depending on classpath scanning
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testSetServerFactory() {
    DefaultServerFactory defaultServerFactory = new DefaultServerFactory();
    configuration.setServerFactory(defaultServerFactory);
    assertThat(configuration.getServerFactory()).isNotNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetOpaServerConfig() {
    OpaServiceConfiguration opaServerConfig =
        OpaServiceConfiguration.builder().baseUrl("http://localhost:3000").secret("test-secret").build();
    configuration.setOpaServerConfig(opaServerConfig);
    assertThat(configuration.getOpaServerConfig()).isNotNull();
    assertThat(configuration.getOpaServerConfig().getBaseUrl()).isEqualTo("http://localhost:3000");
    assertThat(configuration.getOpaServerConfig().getSecret()).isEqualTo("test-secret");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetOpaServerConfigWithNull() {
    configuration.setOpaServerConfig(null);
    assertThat(configuration.getOpaServerConfig()).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetOpaClientConfig() {
    ServiceHttpClientConfig opaClientConfig = ServiceHttpClientConfig.builder()
                                                  .baseUrl("http://localhost:3001")
                                                  .connectTimeOutSeconds(30)
                                                  .readTimeOutSeconds(30)
                                                  .build();
    configuration.setOpaClientConfig(opaClientConfig);
    assertThat(configuration.getOpaClientConfig()).isNotNull();
    assertThat(configuration.getOpaClientConfig().getBaseUrl()).isEqualTo("http://localhost:3001");
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetOpaClientConfigWithNull() {
    configuration.setOpaClientConfig(null);
    assertThat(configuration.getOpaClientConfig()).isNull();
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetOpaEvaluationPluginImage() {
    String pluginImage = "harness/opa-evaluation-plugin:latest";
    configuration.setOpaEvaluationPluginImage(pluginImage);
    assertThat(configuration.getOpaEvaluationPluginImage()).isNotNull();
    assertThat(configuration.getOpaEvaluationPluginImage()).isEqualTo(pluginImage);
  }

  @Test
  @Owner(developers = MAYANK_AGARWAL)
  @Category(UnitTests.class)
  public void testGetOpaEvaluationPluginImageWithNull() {
    configuration.setOpaEvaluationPluginImage(null);
    assertThat(configuration.getOpaEvaluationPluginImage()).isNull();
  }
}
