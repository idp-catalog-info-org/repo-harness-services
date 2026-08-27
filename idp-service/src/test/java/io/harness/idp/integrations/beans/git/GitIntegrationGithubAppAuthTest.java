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
public class GitIntegrationGithubAppAuthTest extends CategoryTest {
  private static final String TEST_APPLICATION_ID = "app123";
  private static final String TEST_APPLICATION_ID_DUMMY = "appId123";
  private static final String TEST_INSTALLATION_ID = "install123";
  private static final String TEST_INSTALLATION_ID_SECRET = "installId123";
  private static final String TEST_DUMMY_KEY = "dummy123";

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSettersAndGetters() {
    GitIntegrationGithubAppAuth auth = new GitIntegrationGithubAppAuth();
    auth.setApplicationId(TEST_APPLICATION_ID);
    auth.setApplicationIdSecretIdentifier(TEST_APPLICATION_ID_DUMMY);
    auth.setInstallationId(TEST_INSTALLATION_ID);
    auth.setInstallationIdSecretIdentifier(TEST_INSTALLATION_ID_SECRET);
    auth.setPrivateKeySecretIdentifier(TEST_DUMMY_KEY);

    assertThat(auth.getApplicationId()).isEqualTo(TEST_APPLICATION_ID);
    assertThat(auth.getApplicationIdSecretIdentifier()).isEqualTo(TEST_APPLICATION_ID_DUMMY);
    assertThat(auth.getInstallationId()).isEqualTo(TEST_INSTALLATION_ID);
    assertThat(auth.getInstallationIdSecretIdentifier()).isEqualTo(TEST_INSTALLATION_ID_SECRET);
    assertThat(auth.getPrivateKeySecretIdentifier()).isEqualTo(TEST_DUMMY_KEY);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSettersAndGettersWithDirectIds() {
    GitIntegrationGithubAppAuth auth = new GitIntegrationGithubAppAuth();
    auth.setApplicationId(TEST_APPLICATION_ID);
    auth.setInstallationId(TEST_INSTALLATION_ID);
    auth.setPrivateKeySecretIdentifier(TEST_DUMMY_KEY);

    assertThat(auth.getApplicationId()).isEqualTo(TEST_APPLICATION_ID);
    assertThat(auth.getApplicationIdSecretIdentifier()).isNull();
    assertThat(auth.getInstallationId()).isEqualTo(TEST_INSTALLATION_ID);
    assertThat(auth.getInstallationIdSecretIdentifier()).isNull();
    assertThat(auth.getPrivateKeySecretIdentifier()).isEqualTo(TEST_DUMMY_KEY);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSettersAndGettersWithSecretIds() {
    GitIntegrationGithubAppAuth auth = new GitIntegrationGithubAppAuth();
    auth.setApplicationIdSecretIdentifier(TEST_APPLICATION_ID_DUMMY);
    auth.setInstallationIdSecretIdentifier(TEST_INSTALLATION_ID_SECRET);
    auth.setPrivateKeySecretIdentifier(TEST_DUMMY_KEY);

    assertThat(auth.getApplicationId()).isNull();
    assertThat(auth.getApplicationIdSecretIdentifier()).isEqualTo(TEST_APPLICATION_ID_DUMMY);
    assertThat(auth.getInstallationId()).isNull();
    assertThat(auth.getInstallationIdSecretIdentifier()).isEqualTo(TEST_INSTALLATION_ID_SECRET);
    assertThat(auth.getPrivateKeySecretIdentifier()).isEqualTo(TEST_DUMMY_KEY);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEqualsAndHashCode() {
    GitIntegrationGithubAppAuth auth1 = new GitIntegrationGithubAppAuth();
    auth1.setApplicationId(TEST_APPLICATION_ID);
    auth1.setInstallationId(TEST_INSTALLATION_ID);
    auth1.setPrivateKeySecretIdentifier(TEST_DUMMY_KEY);

    GitIntegrationGithubAppAuth auth2 = new GitIntegrationGithubAppAuth();
    auth2.setApplicationId(TEST_APPLICATION_ID);
    auth2.setInstallationId(TEST_INSTALLATION_ID);
    auth2.setPrivateKeySecretIdentifier(TEST_DUMMY_KEY);

    GitIntegrationGithubAppAuth auth3 = new GitIntegrationGithubAppAuth();
    auth3.setApplicationId("different");
    auth3.setPrivateKeySecretIdentifier(TEST_DUMMY_KEY);

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
    GitIntegrationGithubAppAuth auth = new GitIntegrationGithubAppAuth();
    auth.setApplicationId(TEST_APPLICATION_ID);
    auth.setInstallationId(TEST_INSTALLATION_ID);
    auth.setPrivateKeySecretIdentifier(TEST_DUMMY_KEY);

    String toString = auth.toString();
    assertThat(toString).isNotNull();
    assertThat(toString).contains(TEST_APPLICATION_ID);
    assertThat(toString).contains(TEST_INSTALLATION_ID);
    assertThat(toString).contains(TEST_DUMMY_KEY);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInheritance() {
    GitIntegrationGithubAppAuth auth = new GitIntegrationGithubAppAuth();
    assertThat(auth).isInstanceOf(GitIntegrationAuth.class);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testWithNullValues() {
    GitIntegrationGithubAppAuth auth = new GitIntegrationGithubAppAuth();
    auth.setApplicationId(null);
    auth.setApplicationIdSecretIdentifier(null);
    auth.setInstallationId(null);
    auth.setInstallationIdSecretIdentifier(null);
    auth.setPrivateKeySecretIdentifier(null);

    assertThat(auth.getApplicationId()).isNull();
    assertThat(auth.getApplicationIdSecretIdentifier()).isNull();
    assertThat(auth.getInstallationId()).isNull();
    assertThat(auth.getInstallationIdSecretIdentifier()).isNull();
    assertThat(auth.getPrivateKeySecretIdentifier()).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDefaultConstructor() {
    GitIntegrationGithubAppAuth auth = new GitIntegrationGithubAppAuth();
    assertThat(auth).isNotNull();
    assertThat(auth.getApplicationId()).isNull();
    assertThat(auth.getApplicationIdSecretIdentifier()).isNull();
    assertThat(auth.getInstallationId()).isNull();
    assertThat(auth.getInstallationIdSecretIdentifier()).isNull();
    assertThat(auth.getPrivateKeySecretIdentifier()).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testMixedDirectAndSecretIds() {
    GitIntegrationGithubAppAuth auth = new GitIntegrationGithubAppAuth();
    auth.setApplicationId(TEST_APPLICATION_ID);
    auth.setInstallationIdSecretIdentifier(TEST_INSTALLATION_ID_SECRET);
    auth.setPrivateKeySecretIdentifier(TEST_DUMMY_KEY);

    assertThat(auth.getApplicationId()).isEqualTo(TEST_APPLICATION_ID);
    assertThat(auth.getApplicationIdSecretIdentifier()).isNull();
    assertThat(auth.getInstallationId()).isNull();
    assertThat(auth.getInstallationIdSecretIdentifier()).isEqualTo(TEST_INSTALLATION_ID_SECRET);
    assertThat(auth.getPrivateKeySecretIdentifier()).isEqualTo(TEST_DUMMY_KEY);
  }
}
