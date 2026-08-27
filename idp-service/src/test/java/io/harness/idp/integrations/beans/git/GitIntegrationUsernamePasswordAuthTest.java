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

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class GitIntegrationUsernamePasswordAuthTest extends CategoryTest {
  private static final String TEST_USERNAME = "testUser";
  private static final String TEST_USERNAME_SECRET_ID = "usernameSecret123";
  private static final String TEST_PASSWORD_SECRET_ID = "passwordSecret123";

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSettersAndGetters() {
    GitIntegrationUsernamePasswordAuth auth = new GitIntegrationUsernamePasswordAuth();
    auth.setUsername(TEST_USERNAME);
    auth.setUsernameSecretIdentifier(TEST_USERNAME_SECRET_ID);
    auth.setPasswordSecretIdentifier(TEST_PASSWORD_SECRET_ID);

    assertThat(auth.getUsername()).isEqualTo(TEST_USERNAME);
    assertThat(auth.getUsernameSecretIdentifier()).isEqualTo(TEST_USERNAME_SECRET_ID);
    assertThat(auth.getPasswordSecretIdentifier()).isEqualTo(TEST_PASSWORD_SECRET_ID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSettersAndGettersWithUsernameOnly() {
    GitIntegrationUsernamePasswordAuth auth = new GitIntegrationUsernamePasswordAuth();
    auth.setUsername(TEST_USERNAME);
    auth.setPasswordSecretIdentifier(TEST_PASSWORD_SECRET_ID);

    assertThat(auth.getUsername()).isEqualTo(TEST_USERNAME);
    assertThat(auth.getUsernameSecretIdentifier()).isNull();
    assertThat(auth.getPasswordSecretIdentifier()).isEqualTo(TEST_PASSWORD_SECRET_ID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSettersAndGettersWithUsernameSecretOnly() {
    GitIntegrationUsernamePasswordAuth auth = new GitIntegrationUsernamePasswordAuth();
    auth.setUsernameSecretIdentifier(TEST_USERNAME_SECRET_ID);
    auth.setPasswordSecretIdentifier(TEST_PASSWORD_SECRET_ID);

    assertThat(auth.getUsername()).isNull();
    assertThat(auth.getUsernameSecretIdentifier()).isEqualTo(TEST_USERNAME_SECRET_ID);
    assertThat(auth.getPasswordSecretIdentifier()).isEqualTo(TEST_PASSWORD_SECRET_ID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEqualsAndHashCode() {
    GitIntegrationUsernamePasswordAuth auth1 = new GitIntegrationUsernamePasswordAuth();
    auth1.setUsername(TEST_USERNAME);
    auth1.setUsernameSecretIdentifier(TEST_USERNAME_SECRET_ID);
    auth1.setPasswordSecretIdentifier(TEST_PASSWORD_SECRET_ID);

    GitIntegrationUsernamePasswordAuth auth2 = new GitIntegrationUsernamePasswordAuth();
    auth2.setUsername(TEST_USERNAME);
    auth2.setUsernameSecretIdentifier(TEST_USERNAME_SECRET_ID);
    auth2.setPasswordSecretIdentifier(TEST_PASSWORD_SECRET_ID);

    GitIntegrationUsernamePasswordAuth auth3 = new GitIntegrationUsernamePasswordAuth();
    auth3.setUsername("different");
    auth3.setPasswordSecretIdentifier(TEST_PASSWORD_SECRET_ID);

    assertThat(auth1).isEqualTo(auth2);
    assertThat(auth1).isNotEqualTo(auth3);
    assertThat(auth1).isNotEqualTo(null);
    assertThat(auth1).isEqualTo(auth1);

    assertThat(auth1.hashCode()).isEqualTo(auth2.hashCode());
    assertThat(auth1.hashCode()).isNotEqualTo(auth3.hashCode());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToString() {
    GitIntegrationUsernamePasswordAuth auth = new GitIntegrationUsernamePasswordAuth();
    auth.setUsername(TEST_USERNAME);
    auth.setPasswordSecretIdentifier(TEST_PASSWORD_SECRET_ID);

    String toString = auth.toString();
    assertThat(toString).isNotNull();
    assertThat(toString).contains(TEST_USERNAME);
    assertThat(toString).contains(TEST_PASSWORD_SECRET_ID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInheritance() {
    GitIntegrationUsernamePasswordAuth auth = new GitIntegrationUsernamePasswordAuth();
    assertThat(auth).isInstanceOf(GitIntegrationAuth.class);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testWithNullValues() {
    GitIntegrationUsernamePasswordAuth auth = new GitIntegrationUsernamePasswordAuth();
    auth.setUsername(null);
    auth.setUsernameSecretIdentifier(null);
    auth.setPasswordSecretIdentifier(null);

    assertThat(auth.getUsername()).isNull();
    assertThat(auth.getUsernameSecretIdentifier()).isNull();
    assertThat(auth.getPasswordSecretIdentifier()).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDefaultConstructor() {
    GitIntegrationUsernamePasswordAuth auth = new GitIntegrationUsernamePasswordAuth();
    assertThat(auth).isNotNull();
    assertThat(auth.getUsername()).isNull();
    assertThat(auth.getUsernameSecretIdentifier()).isNull();
    assertThat(auth.getPasswordSecretIdentifier()).isNull();
  }
}
