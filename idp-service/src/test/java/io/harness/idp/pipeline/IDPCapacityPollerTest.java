/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.pipeline;

import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ci.execution.queue.CICapacityPollerUtils;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class IDPCapacityPollerTest extends CategoryTest {
  @Mock private CICapacityPollerUtils executionPollerUtils;

  private IDPCapacityPoller idpCapacityPoller;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    idpCapacityPoller = new IDPCapacityPoller();
    idpCapacityPoller.executionPollerUtils = executionPollerUtils;
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testStop_Success() throws Exception {
    idpCapacityPoller.stop();
  }
}
