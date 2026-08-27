/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.source.v1.constants;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRIGGERS})
@OwnedBy(PIPELINE)
public interface YamlSimplConstants {
  String PR_EVENT_TYPE = "pr";
  String PUSH_EVENT_TYPE = "push";
  String MR_EVENT_TYPE = "mr";

  // Webhook Triggers
  String AZURE_REPO = "azure-repo";
  String GITHUB_REPO = "github";
  String GITLAB_REPO = "gitlab";
  String BITBUCKET_REPO = "bitbucket";
  String AWS_CODECOMMIT_REPO = "aws-code-commit";
  String CUSTOM_REPO = "custom";
  String HARNESS_REPO = "harness";

  // Artifact Trigger

  String DOCKER_REGISTRY = "docker-registry";
  String NEXUS3_REGISTRY = "nexus3-registry";
  String NEXUS2_REGISTRY = "nexus2-registry";
  String ARTIFACTORY_REGISTRY = "artifactory-registry";
  String GCR = "gcr";
  String ECR = "ecr";
  String ACR = "acr";
  String JENKINS = "jenkins";
  String BAMBOO = "bamboo";
  String AMAZON_S3 = "amazon-s3";
  String CUSTOM_ARTIFACT = "custom";
  String GOOGLE_ARTIFACT_REGISTRY = "google-artifact-registry";
  String GITHUB_PACKAGES = "github-package-registry";
  String AZURE_ARTIFACTS = "azure";
  String AMI = "amazon-machine-image";
  String GOOGLE_CLOUD_STORAGE = "google-cloud-storage";

  // Manifest Triggers
  String HELM_CHART = "helm-chart";

  // Cron
  String CRON = "cron";

  String DELETE_EVENT_TYPE = "delete";
  String EDIT_EVENT_TYPE = "edit";
  String CREATE_EVENT_TYPE = "create";
  String CLOSE_EVENT_TYPE = "close";
  String OPEN_EVENT_TYPE = "open";
  String REOPEN_EVENT_TYPE = "reopen";
  String LABEL_EVENT_TYPE = "label";
  String UNLABEL_EVENT_TYPE = "unlabel";
  String SYNC_EVENT_TYPE = "sync";
  String READY_FOR_REVIEW_EVENT_TYPE = "ready-for-review";
  String RELEASE_EVENT_TYPE = "release";
  String PRE_RELEASE_EVENT_TYPE = "pre-release";
  String PUBLISH_EVENT_TYPE = "publish";
  String UNPUBLISH_EVENT_TYPE = "unpublish";
  String MERGE_EVENT_TYPE = "merge";
  String UPDATE_EVENT_TYPE = "update";
  String DECLINE_EVENT_TYPE = "decline";

  String ISSUE_COMMENT_EVENT_TYPE = "issue-comment";
  String MR_COMMENT_EVENT_TYPE = "mr-comment";
  String PR_COMMENT_EVENT_TYPE = "pr-comment";
  String COMMENT_EVENT_TYPE = "comment";

  String WEBHOOK_TYPE = "webhook";
  String SCHEDULED_TYPE = "scheduled";
  /** Top-level trigger type discriminator for system-event triggers (matches {@code type: system-event}). */
  String SYSTEM_EVENT_TYPE = "system-event";
  /** Sub-type discriminator inside a system-event spec (matches {@code type: pipeline}). */
  String PIPELINE_SYSTEM_EVENT = "pipeline";
  String MULTI_REGION_ARTIFACT_TYPE = "multi-region-artifact";
  String ARTIFACT_TYPE = "artifact";
  String MANIFEST_TYPE = "manifest";
  String HTTP = "http";
  String S3 = "s3";
  String GCS = "gcs";
}
