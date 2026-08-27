/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.onboarding.services.impl;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.onboarding.dto.KubeconfigContextDescriptorDTO;
import io.harness.ng.core.onboarding.dto.KubeconfigUploadResponseDTO;
import io.harness.ng.core.onboarding.mapper.KubeconfigAuthDetector;
import io.harness.ng.core.onboarding.mapper.KubeconfigParser;
import io.harness.ng.core.onboarding.mapper.model.ParsedKubeConfig;
import io.harness.ng.core.onboarding.services.KubeconfigOnboardingService;
import io.harness.stream.BoundedInputStream;

import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class KubeconfigOnboardingServiceImpl implements KubeconfigOnboardingService {
  // A real ~/.kube/config is a few KB; 1 MB is a generous ceiling that keeps an oversized upload from OOMing.
  private static final long MAX_KUBECONFIG_BYTES = 1024L * 1024L;

  @Override
  public KubeconfigUploadResponseDTO processKubeconfig(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, InputStream uploadedInputStream) {
    if (uploadedInputStream == null) {
      throw new InvalidRequestException("Kubeconfig file is missing from the request");
    }

    // Enforce the cap with BoundedInputStream, which throws once the limit is crossed, so memory stays bounded and we
    // never materialize the full oversized body.
    final String content;
    try {
      content =
          IOUtils.toString(new BoundedInputStream(uploadedInputStream, MAX_KUBECONFIG_BYTES), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException(
          String.format("Failed to read the uploaded kubeconfig file (max %d bytes)", MAX_KUBECONFIG_BYTES), e);
    }

    if (StringUtils.isBlank(content)) {
      throw new InvalidRequestException("Uploaded kubeconfig file is empty");
    }

    ParsedKubeConfig parsed = KubeconfigParser.parse(content);
    List<KubeconfigContextDescriptorDTO> contexts = KubeconfigAuthDetector.detect(parsed);

    // Log only an aggregate, non-sensitive summary: total context count plus a tally by type/importStatus. We
    // deliberately omit context/cluster names and current-context (user-supplied, customer-identifying) and, of
    // course, any secret material.
    log.info("Processed kubeconfig onboarding upload. account={}, org={}, project={}, contexts={}, detected={}",
        accountIdentifier, orgIdentifier, projectIdentifier, contexts.size(), summarize(contexts));

    return KubeconfigUploadResponseDTO.builder().currentContext(parsed.getCurrentContext()).contexts(contexts).build();
  }

  /** Non-sensitive tally for logging: count per type/importStatus bucket, no names or secret material. */
  private static Map<String, Long> summarize(List<KubeconfigContextDescriptorDTO> contexts) {
    return contexts.stream().collect(
        Collectors.groupingBy(c -> c.getType() + "/" + c.getImportStatus(), TreeMap::new, Collectors.counting()));
  }
}
