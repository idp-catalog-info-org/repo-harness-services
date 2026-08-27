/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.k8s.exception;

import static io.harness.eraro.ErrorCode.CLUSTER_CREDENTIALS_NOT_FOUND;
import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.Level;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ClusterCredentialsNotFoundExceptionTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testExceptionCreation() {
    String errorMessage = "Master URL not found";
    ClusterCredentialsNotFoundException exception = new ClusterCredentialsNotFoundException(errorMessage);

    assertNotNull(exception);
    assertTrue(exception instanceof WingsException);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testExceptionErrorCode() {
    String errorMessage = "Service Account Token not found";
    ClusterCredentialsNotFoundException exception = new ClusterCredentialsNotFoundException(errorMessage);

    assertEquals(CLUSTER_CREDENTIALS_NOT_FOUND, exception.getCode());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testExceptionLevel() {
    String errorMessage = "Credentials missing";
    ClusterCredentialsNotFoundException exception = new ClusterCredentialsNotFoundException(errorMessage);

    assertEquals(Level.ERROR, exception.getLevel());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testExceptionMessageParam() {
    String errorMessage = "Test error message";
    ClusterCredentialsNotFoundException exception = new ClusterCredentialsNotFoundException(errorMessage);

    assertNotNull(exception.getParams());
    assertEquals(errorMessage, exception.getParams().get("message"));
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testExceptionWithDifferentMessages() {
    String message1 = "Master URL not found";
    String message2 = "Service Account Token not found";

    ClusterCredentialsNotFoundException exception1 = new ClusterCredentialsNotFoundException(message1);
    ClusterCredentialsNotFoundException exception2 = new ClusterCredentialsNotFoundException(message2);

    assertEquals(message1, exception1.getParams().get("message"));
    assertEquals(message2, exception2.getParams().get("message"));
  }
}
