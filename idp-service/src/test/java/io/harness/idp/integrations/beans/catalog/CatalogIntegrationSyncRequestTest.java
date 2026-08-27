/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.integrations.beans.catalog;

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
public class CatalogIntegrationSyncRequestTest extends CategoryTest {
  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testJsonSubTypesAnnotation() {
    JsonSubTypes annotation = CatalogIntegrationSyncRequest.class.getAnnotation(JsonSubTypes.class);
    assertThat(annotation).isNotNull();
    assertThat(annotation.value()).hasSize(1);
    assertThat(annotation.value()[0].value()).isEqualTo(HarnessCDIntegrationSyncRequest.class);
    assertThat(annotation.value()[0].name()).isEqualTo("HARNESS_CD");
  }

  @Test
  @Owner(developers = KOTA_KARTHIK)
  @Category(UnitTests.class)
  public void testSubclassCreation() {
    HarnessCDIntegrationSyncRequest request = HarnessCDIntegrationSyncRequest.builder()
                                                  .accountIdentifier("acc123")
                                                  .identifier("id123")
                                                  .action("CREATE")
                                                  .build();

    assertThat(request).isInstanceOf(CatalogIntegrationSyncRequest.class);
    assertThat(request.getAccountIdentifier()).isEqualTo("acc123");
  }
}
