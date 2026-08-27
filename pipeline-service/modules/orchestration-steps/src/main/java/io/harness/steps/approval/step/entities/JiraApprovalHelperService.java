/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.entities;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.JiraConnectorDTO;
import io.harness.iterator.interfaces.PersistenceIterator;
import io.harness.pms.sdk.core.steps.io.v1.StepBaseParameters;
import io.harness.steps.approval.step.jira.JiraApprovalSpecParameters;

@OwnedBy(CDC)
public interface JiraApprovalHelperService {
  void handlePollingEvent(PersistenceIterator<ApprovalInstance> iterator, JiraApprovalInstance entity);
  JiraConnectorDTO getJiraConnector(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String connectorIdentifierRef);
  JiraApprovalSpecParameters getJiraApprovalStepParameters(StepBaseParameters stepParameters);
}
