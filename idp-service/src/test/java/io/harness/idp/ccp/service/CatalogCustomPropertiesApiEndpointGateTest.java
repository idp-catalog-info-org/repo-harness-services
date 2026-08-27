/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.ccp.service;

import static io.harness.rule.OwnerRule.ROUNAK;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Truth-table for the {@code metadata.apis} write/delete protection gate
 * ({@link CatalogCustomPropertiesServiceImpl#checkApiEndpointDataProtection}). End-to-end
 * request-handling tests live in {@link CatalogCustomPropertiesServiceImplTest}.
 */
@OwnedBy(HarnessTeam.IDP)
public class CatalogCustomPropertiesApiEndpointGateTest extends CategoryTest {
  // Rejected paths

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rejectsBareApisSubtree() {
    // Wholesale replacement of the entire system-managed subtree.
    assertGateRejects("metadata.apis");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rejectsApisSpecHash() {
    // Corrupting specHash would defeat the hash-skip loop-breaker.
    assertGateRejects("metadata.apis.specHash");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rejectsApisLastCheckedAt() {
    // Resetting lastCheckedAt could make the ASC-sorted iterator cursor loop on this entity.
    assertGateRejects("metadata.apis.lastCheckedAt");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rejectsApisExtractionStatus() {
    assertGateRejects("metadata.apis.extractionStatus");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rejectsBarePathsSubtree() {
    // Writing the whole paths map would let a caller invent endpoints.
    assertGateRejects("metadata.apis.paths");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rejectsEndpointWithoutEnrichmentsSegment() {
    // Stopping at the endpoint would overwrite system-extracted fields (method, path, etc.).
    assertGateRejects("metadata.apis.paths.\"GET /v1/users\"");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rejectsEndpointSystemField() {
    // Spec-determined endpoint fields can't be rewritten.
    assertGateRejects("metadata.apis.paths.\"GET /v1/users\".description");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rejectsUnknownDirectChildOfApis() {
    // Only the enrichments escape hatch is allowed directly under metadata.apis.
    assertGateRejects("metadata.apis.somethingNew");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void rejectsCaseInsensitiveMetadataApis() {
    // The gate is case-insensitive on the fixed segments.
    assertGateRejects("Metadata.Apis.specHash");
    assertGateRejects("METADATA.APIS.specHash");
  }

  // Allowed enrichment paths

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void allowsScalarEnrichmentOnEndpoint() {
    assertGateAccepts("metadata.apis.paths.\"POST /v1/payments\".enrichments.riskScore");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void allowsNestedEnrichmentField() {
    // The subtree under enrichments is unconstrained.
    assertGateAccepts("metadata.apis.paths.\"GET /v1/users\".enrichments.owner.team");
    assertGateAccepts("metadata.apis.paths.\"GET /v1/users\".enrichments.classification.tags.0");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void allowsEndpointKeyContainingDotsAndSlashes() {
    // Quoted endpoint keys with dots (e.g. a version segment) stay a single token.
    assertGateAccepts("metadata.apis.paths.\"PUT /v2.1/payments/{id}\".enrichments.owner");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void allowsEnrichmentsRoot() {
    // Bulk overwrite of the whole enrichments object is allowed.
    assertGateAccepts("metadata.apis.paths.\"GET /v1/users\".enrichments");
  }

  // Unrelated paths pass through (no over-restriction)

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void allowsUnrelatedMetadataPaths() {
    assertGateAccepts("metadata.annotations.team");
    assertGateAccepts("metadata.labels.tier");
    assertGateAccepts("metadata.description");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void allowsTopLevelMetadataField() {
    // metadata.foo doesn't reach the apis gate (blocked elsewhere via UNMODIFIABLE_FIELDS).
    assertGateAccepts("metadata.foo");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void allowsNonMetadataTopLevel() {
    // The gate checks only metadata.apis.*; other top-level keys are out of scope.
    assertGateAccepts("apis.somethingElse");
    assertGateAccepts("randomTopLevel");
  }

  @Test
  @Owner(developers = ROUNAK)
  @Category(UnitTests.class)
  public void allowsSingleSegmentProperty() {
    // The gate needs at least 2 tokens (metadata + apis) to engage.
    assertGateAccepts("metadata");
  }

  // --- helpers ---

  private static void assertGateRejects(String property) {
    assertThatThrownBy(() -> CatalogCustomPropertiesServiceImpl.checkApiEndpointDataProtection(property))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("metadata.apis is system-managed");
  }

  private static void assertGateAccepts(String property) {
    assertThatCode(() -> CatalogCustomPropertiesServiceImpl.checkApiEndpointDataProtection(property))
        .doesNotThrowAnyException();
  }
}
