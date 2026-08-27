/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.beans.git;

import static io.harness.rule.OwnerRule.KOTA_KARTHIK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class GitIntegrationAuthTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testJsonSubTypesAnnotation() {
    JsonSubTypes annotation = GitIntegrationAuth.class.getAnnotation(JsonSubTypes.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).hasSize(4);
    boolean hasTokenAuth = false;
    boolean hasUsernamePasswordAuth = false;
    boolean hasGithubAppAuth = false;
    boolean hasManagedTokenAuth = false;

    for (JsonSubTypes.Type type : annotation.value()) {
      if (type.value().equals(GitIntegrationTokenAuth.class) && type.name().equals("TOKEN")) {
        hasTokenAuth = true;
      } else if (type.value().equals(GitIntegrationUsernamePasswordAuth.class)
          && type.name().equals("USERNAME_PASSWORD")) {
        hasUsernamePasswordAuth = true;
      } else if (type.value().equals(GitIntegrationGithubAppAuth.class) && type.name().equals("GITHUB_APP")) {
        hasGithubAppAuth = true;
      } else if (type.value().equals(GitIntegrationManagedTokenAuth.class) && type.name().equals("MANAGED_TOKEN")) {
        hasManagedTokenAuth = true;
      }
    }

    assertThat(hasTokenAuth).isTrue();
    assertThat(hasUsernamePasswordAuth).isTrue();
    assertThat(hasGithubAppAuth).isTrue();
    assertThat(hasManagedTokenAuth).isTrue();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSubclassCreation() {
    GitIntegrationTokenAuth tokenAuth = new GitIntegrationTokenAuth();
    tokenAuth.setTokenSecretIdentifier("token123");
    assertThat(tokenAuth).isInstanceOf(GitIntegrationAuth.class);

    GitIntegrationUsernamePasswordAuth usernamePasswordAuth = new GitIntegrationUsernamePasswordAuth();
    usernamePasswordAuth.setPasswordSecretIdentifier("password123");
    assertThat(usernamePasswordAuth).isInstanceOf(GitIntegrationAuth.class);

    GitIntegrationGithubAppAuth githubAppAuth = new GitIntegrationGithubAppAuth();
    githubAppAuth.setPrivateKeySecretIdentifier("privateKey123");
    assertThat(githubAppAuth).isInstanceOf(GitIntegrationAuth.class);

    GitIntegrationManagedTokenAuth managedTokenAuth = new GitIntegrationManagedTokenAuth();
    assertThat(managedTokenAuth).isInstanceOf(GitIntegrationAuth.class);
  }
}
