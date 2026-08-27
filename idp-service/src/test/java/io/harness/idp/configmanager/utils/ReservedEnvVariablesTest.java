/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.configmanager.utils;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class ReservedEnvVariablesTest extends CategoryTest {
  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testReservedEnvVariables_ListNotNull() {
    assertNotNull(ReservedEnvVariables.RESERVED_ENV_VARIABLES);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testReservedEnvVariables_ListNotEmpty() {
    assertFalse(ReservedEnvVariables.RESERVED_ENV_VARIABLES.isEmpty());
    assertTrue(ReservedEnvVariables.RESERVED_ENV_VARIABLES.size() > 0);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testReservedEnvVariables_AllPatternsValid() {
    for (Pattern pattern : ReservedEnvVariables.RESERVED_ENV_VARIABLES) {
      assertNotNull(pattern);
      assertNotNull(pattern.pattern());
      assertFalse(pattern.pattern().isEmpty());
    }
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testReservedEnvVariables_GithubTokenPattern() {
    boolean foundGithubToken = false;
    for (Pattern pattern : ReservedEnvVariables.RESERVED_ENV_VARIABLES) {
      if (pattern.pattern().contains("GITHUB_TOKEN") || pattern.pattern().contains("github")) {
        foundGithubToken = true;
        break;
      }
    }
    assertTrue(foundGithubToken);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testReservedEnvVariables_GitlabTokenPattern() {
    boolean foundGitlabToken = false;
    for (Pattern pattern : ReservedEnvVariables.RESERVED_ENV_VARIABLES) {
      if (pattern.pattern().contains("GITLAB_TOKEN") || pattern.pattern().contains("gitlab")) {
        foundGitlabToken = true;
        break;
      }
    }
    assertTrue(foundGitlabToken);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testReservedEnvVariables_BitbucketTokenPattern() {
    boolean foundBitbucketToken = false;
    for (Pattern pattern : ReservedEnvVariables.RESERVED_ENV_VARIABLES) {
      if (pattern.pattern().contains("BITBUCKET") || pattern.pattern().contains("bitbucket")) {
        foundBitbucketToken = true;
        break;
      }
    }
    assertTrue(foundBitbucketToken);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testReservedEnvVariables_AzureTokenPattern() {
    boolean foundAzureToken = false;
    for (Pattern pattern : ReservedEnvVariables.RESERVED_ENV_VARIABLES) {
      if (pattern.pattern().contains("AZURE") || pattern.pattern().contains("azure")) {
        foundAzureToken = true;
        break;
      }
    }
    assertTrue(foundAzureToken);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testReservedEnvVariables_AuthPatternsExist() {
    boolean foundAuthPattern = false;
    for (Pattern pattern : ReservedEnvVariables.RESERVED_ENV_VARIABLES) {
      if (pattern.pattern().contains("AUTH") || pattern.pattern().contains("auth")) {
        foundAuthPattern = true;
        break;
      }
    }
    assertTrue(foundAuthPattern);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testReservedEnvVariables_BackendSecretPattern() {
    boolean foundBackendSecret = false;
    for (Pattern pattern : ReservedEnvVariables.RESERVED_ENV_VARIABLES) {
      if (pattern.pattern().contains("BACKEND_SECRET") || pattern.pattern().contains("backend")) {
        foundBackendSecret = true;
        break;
      }
    }
    assertTrue(foundBackendSecret);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testReservedEnvVariables_ProxyPatternExists() {
    boolean foundProxyPattern = false;
    for (Pattern pattern : ReservedEnvVariables.RESERVED_ENV_VARIABLES) {
      if (pattern.pattern().contains("PROXY") || pattern.pattern().contains("proxy")) {
        foundProxyPattern = true;
        break;
      }
    }
    assertTrue(foundProxyPattern);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testReservedEnvVariables_PermissionsAndUsergroupPatternsExist() {
    boolean foundPermissionsPattern = false;
    boolean foundUsergroupPattern = false;
    for (Pattern pattern : ReservedEnvVariables.RESERVED_ENV_VARIABLES) {
      if (pattern.pattern().contains("PERMISSIONS") || pattern.pattern().contains("permissions")) {
        foundPermissionsPattern = true;
      }
      if (pattern.pattern().contains("USERGROUP") || pattern.pattern().contains("usergroup")) {
        foundUsergroupPattern = true;
      }
    }
    assertTrue(foundPermissionsPattern || foundUsergroupPattern);
  }
}
