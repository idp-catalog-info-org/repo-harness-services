/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.modules;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.GitBackedEntity;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.providers.EnvironmentGitBackedEntity;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.providers.InfrastructureGitBackedEntity;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.providers.InputSetGitBackedEntity;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.providers.PipelineGitBackedEntity;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.providers.ServiceGitBackedEntity;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.providers.ServiceOverridesGitBackedEntity;
import io.harness.gitsync.gitxwebhooks.gitbackedentities.providers.TemplateGitBackedEntity;
import io.harness.gitsync.gitxwebhooks.service.GitXAutoCreationServiceImpl;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookEventProcessServiceImpl;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookEventServiceImpl;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookEventValidationProcessServiceImpl;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookEventValidationServiceImpl;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookHealthServiceImpl;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookPullRequestEventProcessServiceImpl;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookPullRequestEventServiceImpl;
import io.harness.gitsync.gitxwebhooks.service.GitXWebhookServiceImpl;
import io.harness.gitsync.gitxwebhooks.service.gitxtriggerexecution.GitXTriggerExecutionTrackerService;
import io.harness.gitsync.gitxwebhooks.service.gitxtriggerexecution.GitXTriggerExecutionTrackerServiceImpl;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXAutoCreationService;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookEventProcessService;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookEventService;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookEventValidationProcessService;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookEventValidationService;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookHealthService;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookPullRequestEventProcessService;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookPullRequestEventService;
import io.harness.gitsync.gitxwebhooks.service.gitxwebhook.GitXWebhookService;
import io.harness.ng.config.NextGenConfiguration;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_GITX})
public class GitXWebhookModule extends AbstractModule {
  NextGenConfiguration appConfig;

  public GitXWebhookModule(NextGenConfiguration appConfig) {
    this.appConfig = appConfig;
  }

  @Override
  protected void configure() {
    bind(GitXWebhookService.class).to(GitXWebhookServiceImpl.class);
    bind(GitXWebhookEventService.class).to(GitXWebhookEventServiceImpl.class);
    bind(GitXWebhookEventProcessService.class).to(GitXWebhookEventProcessServiceImpl.class);
    bind(GitXWebhookEventValidationService.class).to(GitXWebhookEventValidationServiceImpl.class);
    bind(GitXWebhookEventValidationProcessService.class).to(GitXWebhookEventValidationProcessServiceImpl.class);
    bind(GitXAutoCreationService.class).to(GitXAutoCreationServiceImpl.class);
    bind(GitXTriggerExecutionTrackerService.class).to(GitXTriggerExecutionTrackerServiceImpl.class);
    bind(GitXWebhookPullRequestEventService.class).to(GitXWebhookPullRequestEventServiceImpl.class);
    bind(GitXWebhookPullRequestEventProcessService.class).to(GitXWebhookPullRequestEventProcessServiceImpl.class);
    bind(GitXWebhookHealthService.class).to(GitXWebhookHealthServiceImpl.class);

    Multibinder<GitBackedEntity> providerBinder = Multibinder.newSetBinder(binder(), GitBackedEntity.class);
    providerBinder.addBinding().to(PipelineGitBackedEntity.class);
    providerBinder.addBinding().to(InputSetGitBackedEntity.class);
    providerBinder.addBinding().to(TemplateGitBackedEntity.class);
    providerBinder.addBinding().to(ServiceGitBackedEntity.class);
    providerBinder.addBinding().to(EnvironmentGitBackedEntity.class);
    providerBinder.addBinding().to(InfrastructureGitBackedEntity.class);
    providerBinder.addBinding().to(ServiceOverridesGitBackedEntity.class);
  }
}
