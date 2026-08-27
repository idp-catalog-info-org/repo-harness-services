/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.api;

import static io.harness.account.accesscontrol.AccountAccessControlPermissions.EDIT_ACCOUNT_PERMISSION;
import static io.harness.account.accesscontrol.ResourceTypes.ACCOUNT;
import static io.harness.ci.api.AnnotationUtils.assertParameterCounts;
import static io.harness.rule.OwnerRule.ANURAG_MADNAWAT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.accesscontrol.NGAccessControlCheck;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.ci.api.DockerRunnerResourceImpl;
import io.harness.rule.Owner;

import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CI)
public class DockerRunnerResourceImplTest {
  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetAnnotations() throws NoSuchMethodException {
    // Check method level annotations for get method
    Method getMethod =
        DockerRunnerResourceImpl.class.getDeclaredMethod("get", String.class, String.class, String.class);
    assertTrue(getMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(ACCOUNT, getMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(EDIT_ACCOUNT_PERMISSION, getMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getMethod, 1, AccountIdentifier.class);
  }

  @Test
  @Owner(developers = ANURAG_MADNAWAT)
  @Category(UnitTests.class)
  public void testGetTrustLevelAnnotations() throws NoSuchMethodException {
    // Check method level annotations for getTrustLevel method
    Method getTrustLevelMethod = DockerRunnerResourceImpl.class.getDeclaredMethod("getTrustLevel", String.class);
    assertTrue(getTrustLevelMethod.isAnnotationPresent(NGAccessControlCheck.class));
    assertEquals(ACCOUNT, getTrustLevelMethod.getAnnotation(NGAccessControlCheck.class).resourceType());
    assertEquals(EDIT_ACCOUNT_PERMISSION, getTrustLevelMethod.getAnnotation(NGAccessControlCheck.class).permission());

    assertParameterCounts(getTrustLevelMethod, 1, AccountIdentifier.class);
  }
}
