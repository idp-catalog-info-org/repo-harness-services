/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.beans.git;

import static io.harness.idp.integrations.utils.Constants.IDP_GIT_INTEGRATION_MANAGED_HCR;
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
public class GitIntegrationManagedTokenAuthTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDefaultManagedTokenSecretIdentifier() {
    GitIntegrationManagedTokenAuth auth = new GitIntegrationManagedTokenAuth();

    assertThat(auth.getManagedTokenSecretIdentifier()).isEqualTo(IDP_GIT_INTEGRATION_MANAGED_HCR);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSettersAndGetters() {
    GitIntegrationManagedTokenAuth auth = new GitIntegrationManagedTokenAuth();
    String customToken = "customManagedToken";
    auth.setManagedTokenSecretIdentifier(customToken);

    assertThat(auth.getManagedTokenSecretIdentifier()).isEqualTo(customToken);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEqualsAndHashCode() {
    GitIntegrationManagedTokenAuth auth1 = new GitIntegrationManagedTokenAuth();
    GitIntegrationManagedTokenAuth auth2 = new GitIntegrationManagedTokenAuth();

    GitIntegrationManagedTokenAuth auth3 = new GitIntegrationManagedTokenAuth();
    auth3.setManagedTokenSecretIdentifier("different");

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
    GitIntegrationManagedTokenAuth auth = new GitIntegrationManagedTokenAuth();

    String toString = auth.toString();
    assertThat(toString).isNotNull();
    assertThat(toString).contains(IDP_GIT_INTEGRATION_MANAGED_HCR);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testInheritance() {
    GitIntegrationManagedTokenAuth auth = new GitIntegrationManagedTokenAuth();
    assertThat(auth).isInstanceOf(GitIntegrationAuth.class);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSetNullValue() {
    GitIntegrationManagedTokenAuth auth = new GitIntegrationManagedTokenAuth();
    auth.setManagedTokenSecretIdentifier(null);

    assertThat(auth.getManagedTokenSecretIdentifier()).isNull();
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testDefaultConstructor() {
    GitIntegrationManagedTokenAuth auth = new GitIntegrationManagedTokenAuth();
    assertThat(auth).isNotNull();
    assertThat(auth.getManagedTokenSecretIdentifier()).isEqualTo(IDP_GIT_INTEGRATION_MANAGED_HCR);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testOverrideDefaultValue() {
    GitIntegrationManagedTokenAuth auth = new GitIntegrationManagedTokenAuth();
    assertThat(auth.getManagedTokenSecretIdentifier()).isEqualTo(IDP_GIT_INTEGRATION_MANAGED_HCR);

    String newValue = "newManagedToken";
    auth.setManagedTokenSecretIdentifier(newValue);
    assertThat(auth.getManagedTokenSecretIdentifier()).isEqualTo(newValue);
    assertThat(auth.getManagedTokenSecretIdentifier()).isNotEqualTo(IDP_GIT_INTEGRATION_MANAGED_HCR);
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testEqualsWithCustomValues() {
    GitIntegrationManagedTokenAuth auth1 = new GitIntegrationManagedTokenAuth();
    auth1.setManagedTokenSecretIdentifier("custom1");

    GitIntegrationManagedTokenAuth auth2 = new GitIntegrationManagedTokenAuth();
    auth2.setManagedTokenSecretIdentifier("custom1");

    assertThat(auth1).isEqualTo(auth2);
    assertThat(auth1.hashCode()).isEqualTo(auth2.hashCode());
  }
}
