/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.idp.steps.execution.filter;

import static io.harness.steps.common.Constants.SLACK_NOTIFY;

import io.harness.filters.GenericStepPMSFilterJsonCreatorV2;
import io.harness.idp.steps.Constants;
import io.harness.idp.steps.StepSpecTypeConstants;
import io.harness.idp.steps.utils.IDPCreatorUtils;

import java.util.HashSet;
import java.util.Set;

public class IDPStepFilterJsonCreator extends GenericStepPMSFilterJsonCreatorV2 {
  @Override
  public Set<String> getSupportedStepTypes() {
    Set<String> allSupportedSteps = new HashSet<>();
    allSupportedSteps.add(StepSpecTypeConstants.RUN);
    allSupportedSteps.add(StepSpecTypeConstants.PLUGIN);
    allSupportedSteps.add(StepSpecTypeConstants.GIT_CLONE);
    allSupportedSteps.add(Constants.COOKIECUTTER);
    allSupportedSteps.add(Constants.CREATE_REPO);
    allSupportedSteps.add(Constants.DIRECT_PUSH);
    allSupportedSteps.add(Constants.REGISTER_CATALOG);
    allSupportedSteps.add(Constants.CREATE_CATALOG);
    allSupportedSteps.add(SLACK_NOTIFY);
    allSupportedSteps.add(Constants.CREATE_ORGANISATION);
    allSupportedSteps.add(Constants.CREATE_PROJECT);
    allSupportedSteps.add(Constants.CREATE_RESOURCE);
    allSupportedSteps.add(Constants.UPDATE_CATALOG_PROPERTY);
    allSupportedSteps.add(StepSpecTypeConstants.AGENT);
    allSupportedSteps.add(io.harness.steps.StepSpecTypeConstants.IDP_ACTION);

    allSupportedSteps.addAll(IDPCreatorUtils.getLiteEngineStep());
    return allSupportedSteps;
  }
}