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
import io.harness.ng.core.onboarding.dto.OnboardingContextDTO;
import io.harness.ng.core.onboarding.dto.OnboardingDeploymentType;
import io.harness.ng.core.onboarding.dto.OnboardingStrategy;

import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Renders a ready-to-run deployment pipeline from a template selected by deployment type and strategy. The template
 * lives at {@code onboarding/pipeline-templates/<deploymentType>_<strategy>.yaml} (e.g. {@code
 * kubernetes_rolling.yaml}); this generator loads it, validates the caller-supplied identifiers, generates a stage
 * name/id, and substitutes the
 * {@code ${...}} placeholders. Adding support for a new deployment type or strategy is a matter of adding the enum
 * constant and dropping the matching template file; no change is needed here.
 */
@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class OnboardingPipelineGenerator {
  private static final String TEMPLATE_PATH_FORMAT = "onboarding/pipeline-templates/%s_%s.yaml";

  // These identifiers are substituted verbatim into the YAML template, so they must be safe Harness identifiers.
  // Anything else — e.g. a newline, colon, or quote — would corrupt or inject into the rendered pipeline. We keep
  // onboarding uniformly '$'-free (the generation path also strips '$'), so this is intentionally stricter than the
  // entity layer's @EntityIdentifier: letter/underscore start, then alphanumerics or underscores only.
  private static final String IDENTIFIER_PATTERN = "^[a-zA-Z_][0-9a-zA-Z_]{0,127}$";

  private static final String PLACEHOLDER_STAGE_NAME = "${stageName}";
  private static final String PLACEHOLDER_STAGE_ID = "${stageId}";
  private static final String PLACEHOLDER_SERVICE = "${serviceIdentifier}";
  private static final String PLACEHOLDER_ENVIRONMENT = "${environmentIdentifier}";
  private static final String PLACEHOLDER_INFRASTRUCTURE = "${infrastructureIdentifier}";

  /**
   * Renders the pipeline YAML for the context's strategy. Callers must ensure the strategy is set before invoking.
   * The stage name and id share a single generated value: the strategy value, an underscore, then an 8-char suffix.
   */
  public String generate(OnboardingContextDTO context) {
    OnboardingStrategy strategy = context.getStrategy();
    if (strategy == null) {
      throw new InvalidRequestException("strategy is required to generate a pipeline");
    }
    OnboardingDeploymentType deploymentType = context.getDeploymentType();
    if (deploymentType == null) {
      throw new InvalidRequestException("deployment_type is required when a strategy is provided");
    }
    validateRequiredIdentifiers(context);

    String stageNameAndId = generateStageIdentifier(strategy);
    String template = loadTemplate(deploymentType, strategy);
    String pipelineYaml = template.replace(PLACEHOLDER_STAGE_NAME, stageNameAndId)
                              .replace(PLACEHOLDER_STAGE_ID, stageNameAndId)
                              .replace(PLACEHOLDER_SERVICE, context.getServiceId())
                              .replace(PLACEHOLDER_ENVIRONMENT, context.getPipelineEnvironmentIdentifier())
                              .replace(PLACEHOLDER_INFRASTRUCTURE, context.getPipelineInfrastructureIdentifier());

    log.info("Generated onboarding pipeline. deploymentType={}, strategy={}, stage={}, service={}, environment={}, "
            + "infrastructure={}",
        deploymentType.getValue(), strategy.getValue(), stageNameAndId, context.getServiceId(),
        context.getPipelineEnvironmentIdentifier(), context.getPipelineInfrastructureIdentifier());
    return pipelineYaml;
  }

  private static void validateRequiredIdentifiers(OnboardingContextDTO context) {
    validateIdentifier("service_identifier", context.getServiceId());
    validateIdentifier("environment_identifier", context.getPipelineEnvironmentIdentifier());
    validateIdentifier("infrastructure_identifier", context.getPipelineInfrastructureIdentifier());
  }

  /**
   * Ensures an identifier is present and a valid Harness identifier before it is substituted into the template.
   * These reference pre-existing entities, so we reject (rather than sanitize) anything malformed — a coerced value
   * would silently point at the wrong entity or, worse, corrupt the rendered YAML.
   */
  private static void validateIdentifier(String field, String value) {
    if (StringUtils.isBlank(value)) {
      throw new InvalidRequestException(String.format("%s is required when a strategy is provided", field));
    }
    if (!value.matches(IDENTIFIER_PATTERN)) {
      throw new InvalidRequestException(String.format(
          "%s '%s' is not a valid identifier: it must start with a letter or underscore and contain only letters, "
              + "digits, and underscores (max 128 characters).",
          field, value));
    }
  }

  private static String loadTemplate(OnboardingDeploymentType deploymentType, OnboardingStrategy strategy) {
    String resourcePath = String.format(TEMPLATE_PATH_FORMAT, deploymentType.getValue(), strategy.getValue());
    try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new InvalidRequestException(
            String.format("Pipeline template not found for deployment type '%s' and strategy '%s'",
                deploymentType.getValue(), strategy.getValue()));
      }
      return IOUtils.toString(is, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException(
          String.format("Failed to load pipeline template for deployment type '%s' and strategy '%s'",
              deploymentType.getValue(), strategy.getValue()),
          e);
    }
  }

  /**
   * Generates a stage identifier: the strategy value, an underscore, and an 8-char alphanumeric suffix (no hyphens).
   */
  private static String generateStageIdentifier(OnboardingStrategy strategy) {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    return strategy.getValue() + "_" + suffix;
  }
}
