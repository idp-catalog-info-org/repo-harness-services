/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.rule.OwnerRule.SHOBHIT_SINGH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.data.RawOptionalSweepingOutput;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;
import io.harness.serializer.recaster.RecastOrchestrationUtils;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class ServerlessImageConfigFunctorTest extends CategoryTest {
  // <prefix>:<runtimeLanguage>-<serverlessVersion>-<rest...>
  private static final String BASE_IMAGE = "harnessdev/serverless-plugin:nodejs18.x-3.39.0-0.0.3";

  @Mock private PmsSweepingOutputService pmsSweepingOutputService;

  private final Ambiance ambiance = Ambiance.newBuilder().build();
  private ServerlessImageConfigFunctor functor;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    functor = new ServerlessImageConfigFunctor(ambiance, pmsSweepingOutputService);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testReturnsNullWhenServiceOutputResolvesToNull() {
    // serviceOutput not produced yet -> defer resolution by returning null. Under RETURN_ORIGINAL the raw expression is
    // preserved (and callers guard against "null"), so resolution happens later where serviceOutput is available.
    when(pmsSweepingOutputService.resolveOptional(any(), any())).thenReturn(null);
    assertThat(functor.get(BASE_IMAGE)).isNull();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testReturnsNullWhenServiceOutputNotFound() {
    when(pmsSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(RawOptionalSweepingOutput.builder().found(false).build());
    assertThat(functor.get(BASE_IMAGE)).isNull();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testReturnsNullWhenServiceOutputIsEmpty() {
    when(pmsSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(RawOptionalSweepingOutput.builder().found(true).output("").build());
    assertThat(functor.get(BASE_IMAGE)).isNull();
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testReturnsBaseImageWhenNoTagSeparator() {
    // No ":" -> does not split into <prefix>:<tag>, so the image is returned untouched.
    stubServiceOutput(pluginInfo("python3.12", "4.0.0"));
    assertThat(functor.get("harnessdev/serverless-plugin")).isEqualTo("harnessdev/serverless-plugin");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testReturnsBaseImageWhenMultipleTagSeparators() {
    // More than one ":" -> not exactly two parts, so the image is returned untouched.
    String image = "registry:5000/serverless-plugin:nodejs18.x-3.39.0";
    stubServiceOutput(pluginInfo("python3.12", "4.0.0"));
    assertThat(functor.get(image)).isEqualTo(image);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testReturnsBaseImageWhenTagHasSingleSegment() {
    // Tag does not split into at least <runtimeLanguage>-<serverlessVersion>, so the image is returned untouched.
    String image = "harnessdev/serverless-plugin:nodejs18.x";
    stubServiceOutput(pluginInfo("python3.12", "4.0.0"));
    assertThat(functor.get(image)).isEqualTo(image);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testOverridesRuntimeAndServerlessFromPluginInfo() {
    stubServiceOutput(pluginInfo("python3.12", "4.0.0"));
    assertThat(functor.get(BASE_IMAGE)).isEqualTo("harnessdev/serverless-plugin:python3.12-4.0.0-0.0.3");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testFallsBackToBaseImageRuntimeWhenMissing() {
    Map<String, Object> pluginInfo = new HashMap<>();
    pluginInfo.put("serverlessVersion", "4.0.0");
    stubServiceOutput(pluginInfo);
    // runtimeLanguage falls back to the base image's nodejs18.x.
    assertThat(functor.get(BASE_IMAGE)).isEqualTo("harnessdev/serverless-plugin:nodejs18.x-4.0.0-0.0.3");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testFallsBackToBaseImageServerlessVersionWhenMissing() {
    Map<String, Object> pluginInfo = new HashMap<>();
    pluginInfo.put("runtimeLanguage", "python3.12");
    stubServiceOutput(pluginInfo);
    // serverlessVersion falls back to the base image's 3.39.0.
    assertThat(functor.get(BASE_IMAGE)).isEqualTo("harnessdev/serverless-plugin:python3.12-3.39.0-0.0.3");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testReturnsBaseImageWhenPluginInfoMissing() {
    // No pluginInfo -> both segments fall back to the base image, so it is effectively unchanged.
    stubServiceOutput(null);
    assertThat(functor.get(BASE_IMAGE)).isEqualTo(BASE_IMAGE);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testFallsBackWhenPluginValuesAreNullLiteral() {
    // Literal "null" plugin values are treated as absent -> base image segments are kept.
    stubServiceOutput(pluginInfo("null", "null"));
    assertThat(functor.get(BASE_IMAGE)).isEqualTo(BASE_IMAGE);
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testPreservesAllTrailingTagSegments() {
    stubServiceOutput(pluginInfo("python3.12", "4.0.0"));
    assertThat(functor.get("acme/serverless:go1.x-3.40.0-a-b-c")).isEqualTo("acme/serverless:python3.12-4.0.0-a-b-c");
  }

  @Test
  @Owner(developers = SHOBHIT_SINGH)
  @Category(UnitTests.class)
  public void testHonoursProvidedPrefix() {
    stubServiceOutput(pluginInfo("rust1.75", "3.41.0"));
    assertThat(functor.get("acme/serverless:go1.x-3.40.0-9")).isEqualTo("acme/serverless:rust1.75-3.41.0-9");
  }

  private Map<String, Object> pluginInfo(String runtimeLanguage, String serverlessVersion) {
    Map<String, Object> pluginInfo = new HashMap<>();
    pluginInfo.put("runtimeLanguage", runtimeLanguage);
    pluginInfo.put("serverlessVersion", serverlessVersion);
    return pluginInfo;
  }

  private void stubServiceOutput(Map<String, Object> pluginInfo) {
    Map<String, Object> serviceOutput = new HashMap<>();
    if (pluginInfo != null) {
      serviceOutput.put("pluginInfo", pluginInfo);
    }
    when(pmsSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(RawOptionalSweepingOutput.builder()
                        .found(true)
                        .output(RecastOrchestrationUtils.toJson(serviceOutput))
                        .build());
  }
}
