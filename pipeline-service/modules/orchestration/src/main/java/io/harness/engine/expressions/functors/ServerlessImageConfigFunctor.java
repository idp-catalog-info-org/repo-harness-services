/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.expressions.functors;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.engine.pms.data.RawOptionalSweepingOutput;
import io.harness.engine.pms.data.sweepingoutput.PmsSweepingOutputService;
import io.harness.expression.celcustomfunctor.WithGet;
import io.harness.expression.functors.ExpressionFunctor;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.execution.NodeExecutionUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Backs the {@code serverlessImageConfig} functor, which overlays the deployed service's plugin info onto a base
 * serverless plugin image. Registered for <b>both</b> expression dialects:
 *
 * <ul>
 *   <li>JEXL - {@code <+serverlessImageConfig.get("harnessdev/serverless-plugin:nodejs18.x-3.39.0-0.0.3")>}</li>
 *   <li>CEL  - {@code ${{serverlessImageConfig.get("harnessdev/serverless-plugin:nodejs18.x-3.39.0-0.0.3")}}}</li>
 * </ul>
 *
 * <p>CEL support comes from implementing {@link WithGet}: {@code CommonFunctorCelLibrary}'s generic {@code
 * get(receiver, String)} binding routes {@code serverlessImageConfig.get("...")} to {@link #get(String)}, so no
 * dedicated CEL library is required.
 */
@Slf4j
@OwnedBy(PIPELINE)
public class ServerlessImageConfigFunctor implements ExpressionFunctor, WithGet {
  public static final String SERVERLESS_IMAGE_CONFIG_FUNCTOR_NAME = "serverlessImageConfig";

  private static final String SERVICE_OUTPUT = "serviceOutput";
  private static final String PLUGIN_INFO = "pluginInfo";
  private static final String RUNTIME_LANGUAGE = "runtimeLanguage";
  private static final String SERVERLESS_VERSION = "serverlessVersion";
  private static final String NULL_STR = "null";
  private static final String IMAGE_TAG_SEPARATOR = ":";
  private static final String TAG_SEGMENT_SEPARATOR = "-";

  private final Ambiance ambiance;
  private final PmsSweepingOutputService pmsSweepingOutputService;

  public ServerlessImageConfigFunctor(Ambiance ambiance, PmsSweepingOutputService pmsSweepingOutputService) {
    this.ambiance = ambiance;
    this.pmsSweepingOutputService = pmsSweepingOutputService;
  }

  /**
   * Overlays the deployed service's {@code runtimeLanguage} / {@code serverlessVersion} (from
   * {@code serviceOutput.pluginInfo}) onto the given base image and returns the effective plugin image.
   *
   * <p>The base image is expected to look like {@code <prefix>:<runtimeLanguage>-<serverlessVersion>[-<rest...>]}. Only
   * the runtimeLanguage and serverlessVersion segments are overridden, and only when pluginInfo carries a real value;
   * the prefix and every remaining tag segment are preserved as-is. The base image is returned unchanged when:
   */
  @Override
  public String get(String baseImage) {
    RawOptionalSweepingOutput serviceOutput =
        pmsSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(SERVICE_OUTPUT));
    if (serviceOutput == null || !serviceOutput.isFound() || isEmpty(serviceOutput.getOutput())) {
      return null;
    }
    if (isEmpty(baseImage)) {
      return baseImage;
    }
    // Split into "<prefix>:<tag>" - must be exactly two parts, else the image is not in the expected shape.
    String[] imageParts = baseImage.split(IMAGE_TAG_SEPARATOR);
    if (imageParts.length != 2) {
      return baseImage;
    }
    // Split the tag into "<runtimeLanguage>-<serverlessVersion>[-<rest...>]" - at least two parts required.
    String[] tagSegments = imageParts[1].split(TAG_SEGMENT_SEPARATOR);
    if (tagSegments.length < 2) {
      return baseImage;
    }

    Map<String, Object> pluginInfo = resolvePluginInfo(serviceOutput);
    // The base-image segments are the fallback; pluginInfo overrides them only when it provides a real value.
    String runtimeLanguage = firstNonEmpty(pluginValue(pluginInfo, RUNTIME_LANGUAGE), tagSegments[0]);
    String serverlessVersion = firstNonEmpty(pluginValue(pluginInfo, SERVERLESS_VERSION), tagSegments[1]);

    StringBuilder image = new StringBuilder(imageParts[0])
                              .append(IMAGE_TAG_SEPARATOR)
                              .append(runtimeLanguage)
                              .append(TAG_SEGMENT_SEPARATOR)
                              .append(serverlessVersion);
    // Preserve any trailing tag segments (e.g. the base version) untouched.
    for (int i = 2; i < tagSegments.length; i++) {
      image.append(TAG_SEGMENT_SEPARATOR).append(tagSegments[i]);
    }
    return image.toString();
  }

  private Map<String, Object> resolvePluginInfo(RawOptionalSweepingOutput serviceOutput) {
    return extractPluginInfo(serviceOutput.getOutput());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> extractPluginInfo(String serviceOutputJson) {
    Object serviceOutput = NodeExecutionUtils.extractAndProcessObject(serviceOutputJson);
    if (!(serviceOutput instanceof Map)) {
      return null;
    }
    Object pluginInfo = ((Map<String, Object>) serviceOutput).get(PLUGIN_INFO);
    return (pluginInfo instanceof Map) ? (Map<String, Object>) pluginInfo : null;
  }

  /**
   * Returns the plugin field value, or {@code null} when it is absent, empty, or the literal {@code "null"} - all of
   * which mean "no override", so the base-image segment is used instead.
   */
  private String pluginValue(Map<String, Object> pluginInfo, String key) {
    Object raw = (pluginInfo == null) ? null : pluginInfo.get(key);
    String value = (raw == null) ? null : String.valueOf(raw);
    return (isEmpty(value) || NULL_STR.equals(value)) ? null : value;
  }

  private String firstNonEmpty(String override, String fallback) {
    return isNotEmpty(override) ? override : fallback;
  }
}
