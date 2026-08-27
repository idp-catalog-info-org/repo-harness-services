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
public class GitIntegrationTokenAuthTest extends CategoryTest {
  private static final String TEST_TOKEN_SECRET_ID = "tokenSecret123";

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSettersAndGetters() {
    GitIntegrationTokenAuth tokenAuth = new GitIntegrationTokenAuth();
    tokenAuth.setTokenSecretIdentifier(TEST_TOKEN_SECRET_ID);

    assertThat(tokenAuth.getTokenSecretIdentifier()).isEqualTo(TEST_TOKEN_SECRET_ID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEqualsAndHashCode() {
    GitIntegrationTokenAuth tokenAuth1 = new GitIntegrationTokenAuth();
    tokenAuth1.setTokenSecretIdentifier(TEST_TOKEN_SECRET_ID);

    GitIntegrationTokenAuth tokenAuth2 = new GitIntegrationTokenAuth();
    tokenAuth2.setTokenSecretIdentifier(TEST_TOKEN_SECRET_ID);

    GitIntegrationTokenAuth tokenAuth3 = new GitIntegrationTokenAuth();
    tokenAuth3.setTokenSecretIdentifier("different");

    assertThat(tokenAuth1).isEqualTo(tokenAuth2);
    assertThat(tokenAuth1).isNotEqualTo(tokenAuth3);
    assertThat(tokenAuth1).isNotEqualTo(null);
    assertThat(tokenAuth1).isEqualTo(tokenAuth1);

    assertThat(tokenAuth1.hashCode()).isEqualTo(tokenAuth2.hashCode());
    assertThat(tokenAuth1.hashCode()).isNotEqualTo(tokenAuth3.hashCode());
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testToString() {
    GitIntegrationTokenAuth tokenAuth = new GitIntegrationTokenAuth();
    tokenAuth.setTokenSecretIdentifier(TEST_TOKEN_SECRET_ID);

    String toString = tokenAuth.toString();
    assertThat(toString).isNotNull();
    assertThat(toString).contains(TEST_TOKEN_SECRET_ID);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInheritance() {
    GitIntegrationTokenAuth tokenAuth = new GitIntegrationTokenAuth();
    assertThat(tokenAuth).isInstanceOf(GitIntegrationAuth.class);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testWithNullValue() {
    GitIntegrationTokenAuth tokenAuth = new GitIntegrationTokenAuth();
    tokenAuth.setTokenSecretIdentifier(null);

    assertThat(tokenAuth.getTokenSecretIdentifier()).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDefaultConstructor() {
    GitIntegrationTokenAuth tokenAuth = new GitIntegrationTokenAuth();
    assertThat(tokenAuth).isNotNull();
    assertThat(tokenAuth.getTokenSecretIdentifier()).isNull();
  }
}
