/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.pipeline;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.queue.CIInitPollerUtils;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class IDPExecutionPollerTest extends CategoryTest {
  @Mock private CIInitPollerUtils executionPollerUtils;

  private IDPExecutionPoller idpExecutionPoller;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    idpExecutionPoller = new IDPExecutionPoller();
    idpExecutionPoller.executionPollerUtils = executionPollerUtils;
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testStop_Success() throws Exception {
    idpExecutionPoller.stop();
  }
}
