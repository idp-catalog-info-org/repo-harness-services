/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.validation.service;

import static io.harness.beans.FeatureName.PIPE_ENABLE_RESTRICTION_IN_TRIGGER_HEADERS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidYamlException;
import io.harness.gitaware.helper.GitAwareContextHelper;
import io.harness.gitsync.sdk.EntityGitDetails;
import io.harness.governance.GovernanceMetadata;
import io.harness.notification.bean.NotificationRules;
import io.harness.notification.channelDetails.PmsWebhookChannel;
import io.harness.notification.channeldetails.NotificationChannelType;
import io.harness.pms.pipeline.PipelineEntity;
import io.harness.pms.pipeline.governance.service.PipelineGovernanceServiceImpl;
import io.harness.pms.pipeline.service.PMSYamlSchemaService;
import io.harness.pms.pipeline.service.helper.PMSPipelineServiceHelper;
import io.harness.pms.pipeline.service.response.PipelineCRUDErrorResponse;
import io.harness.pms.pipeline.validation.PipelineValidationResponse;
import io.harness.pms.pipeline.validation.async.beans.BarrierCycleValidator;
import io.harness.pms.pipeline.validation.service.intfc.PipelineValidationService;
import io.harness.pms.pipeline.yaml.BasicPipeline;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.webhook.WebhookHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
public class PipelineValidationServiceImpl implements PipelineValidationService {
  @Inject private final PMSYamlSchemaService pmsYamlSchemaService;
  @Inject private final PipelineGovernanceServiceImpl pipelineGovernanceService;
  @Inject private final BarrierCycleValidator barrierCycleValidator;
  @Inject PmsFeatureFlagService pmsFeatureFlagService;

  @Override
  public boolean validateYaml(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String yamlWithTemplatesResolved, String pipelineYaml, String harnessVersion) {
    if (harnessVersion.equals(HarnessYamlVersion.V0)) {
      checkIfRootNodeIsPipeline(pipelineYaml);
    }
    pmsYamlSchemaService.validateYamlSchema(accountIdentifier, orgIdentifier, projectIdentifier,
        YamlUtils.readAsJsonNode(yamlWithTemplatesResolved), harnessVersion);
    // validate unique fqn in resolveTemplateRefsInPipeline
    pmsYamlSchemaService.validateUniqueFqn(yamlWithTemplatesResolved, harnessVersion);
    try {
      BasicPipeline basicPipeline = YamlUtils.read(pipelineYaml, BasicPipeline.class);
      validateWebhookNotificationHeader(basicPipeline, accountIdentifier);
    } catch (IOException e) {
      throw new InvalidYamlException("Invalid YAML while parsing to basic pipeline", e);
    }
    barrierCycleValidator.validate(accountIdentifier, yamlWithTemplatesResolved);
    return true;
  }

  @Override
  public void validateYamlWithUnresolvedTemplates(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineYaml, String harnessVersion) {
    if (Objects.equals(harnessVersion, HarnessYamlVersion.V0)) {
      checkIfRootNodeIsPipeline(pipelineYaml);
    }
    pmsYamlSchemaService.validateYamlSchema(
        accountIdentifier, orgIdentifier, projectIdentifier, YamlUtils.readAsJsonNode(pipelineYaml), harnessVersion);
    pmsYamlSchemaService.validateUniqueFqn(pipelineYaml, harnessVersion);
  }

  @Override
  public PipelineValidationResponse validateYamlAndGovernanceRules(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String yamlWithTemplatesResolved, String resolvedYamlWithTemplateRefs,
      PipelineEntity pipelineEntity) {
    validateYaml(accountIdentifier, orgIdentifier, projectIdentifier, yamlWithTemplatesResolved,
        pipelineEntity.getYaml(), pipelineEntity.getHarnessVersion());

    String branch = GitAwareContextHelper.getBranchInRequest();
    GovernanceMetadata governanceMetadata = pipelineGovernanceService.validateGovernanceRulesAndThrowExceptionIfDenied(
        accountIdentifier, orgIdentifier, projectIdentifier, branch, pipelineEntity, resolvedYamlWithTemplateRefs);
    return PipelineValidationResponse.builder().governanceMetadata(governanceMetadata).build();
  }

  @Override
  public PipelineValidationResponse validateYamlAndGetGovernanceMetadata(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String yamlWithTemplatesResolved, String resolvedYamlWithTemplateRefs,
      PipelineEntity pipelineEntity) {
    // Validate YAML schema for all pipeline versions including v1
    validateYaml(accountIdentifier, orgIdentifier, projectIdentifier, yamlWithTemplatesResolved,
        pipelineEntity.getYaml(), pipelineEntity.getHarnessVersion());

    String branch = GitAwareContextHelper.getBranchInRequest();
    GovernanceMetadata governanceMetadata = pipelineGovernanceService.validateGovernanceRules(
        accountIdentifier, orgIdentifier, projectIdentifier, branch, pipelineEntity, resolvedYamlWithTemplateRefs);
    return PipelineValidationResponse.builder().governanceMetadata(governanceMetadata).build();
  }

  @VisibleForTesting
  void checkIfRootNodeIsPipeline(String pipelineYaml) {
    EntityGitDetails gitDetails = GitAwareContextHelper.getEntityGitDetailsFromScmGitMetadata();
    String branch = gitDetails.getBranch();
    String filePath = gitDetails.getFilePath();
    YamlField pipelineYamlField;

    try {
      pipelineYamlField = YamlUtils.readTree(pipelineYaml);
    } catch (IOException e) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForNotAYAMLFile(branch, filePath);
      throw PMSPipelineServiceHelper.buildInvalidYamlException(errorMessage, pipelineYaml);
    }
    if (pipelineYamlField.getNode().getField(YAMLFieldNameConstants.PIPELINE) == null) {
      String errorMessage = PipelineCRUDErrorResponse.errorMessageForNotAPipelineYAML(branch, filePath);
      throw PMSPipelineServiceHelper.buildInvalidYamlException(errorMessage, pipelineYaml);
    }
  }

  private boolean isWebhookNotification(NotificationRules notificationRule) {
    if (notificationRule != null && notificationRule.getNotificationChannelWrapper() != null
        && NotificationChannelType.WEBHOOK.equals(notificationRule.getNotificationChannelWrapper().get("type"))) {
      return true;
    }
    return false;
  }

  void validateWebhookNotificationHeader(BasicPipeline pipeline, String accountIdentifier) {
    if (pipeline != null && EmptyPredicate.isNotEmpty(pipeline.getNotificationRules())) {
      List<NotificationRules> notificationRulesList = pipeline.getNotificationRules();
      for (NotificationRules notificationRule : notificationRulesList) {
        if (isWebhookNotification(notificationRule)) {
          PmsWebhookChannel pmsWebhookChannel =
              (PmsWebhookChannel) notificationRule.getNotificationChannelWrapper().get("notificationChannel");
          if (pmsWebhookChannel != null) {
            boolean throwErrorOnViolation =
                pmsFeatureFlagService.isEnabled(accountIdentifier, PIPE_ENABLE_RESTRICTION_IN_TRIGGER_HEADERS);
            WebhookHelper.checkSpecialCharInWebhookHeaders(pmsWebhookChannel.getHeaders(), throwErrorOnViolation);
            WebhookHelper.checkSizeInWebhookHeaders(pmsWebhookChannel.getHeaders(), throwErrorOnViolation);
          }
        }
      }
    }
  }
}
