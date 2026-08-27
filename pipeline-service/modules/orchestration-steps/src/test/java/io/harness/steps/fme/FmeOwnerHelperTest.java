/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.fme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.category.element.UnitTests;

import io.split.client.dtos.URN;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Unit tests for FmeOwnerHelper.
 *
 * Verifies parsing of "type:id" formatted owner strings into URN objects,
 * including type normalization and backward compatibility with bare IDs.
 */
@Category(UnitTests.class)
public class FmeOwnerHelperTest {
  @Test
  @Category(UnitTests.class)
  public void testParseOwner_UserPrefix_Capitalized() {
    URN urn = FmeOwnerHelper.parseOwner("User:abc-123");
    assertThat(urn.type).isEqualTo("User");
    assertThat(urn.id).isEqualTo("abc-123");
  }

  @Test
  @Category(UnitTests.class)
  public void testParseOwner_UserPrefix_Lowercase() {
    URN urn = FmeOwnerHelper.parseOwner("user:abc-123");
    assertThat(urn.type).isEqualTo("User");
    assertThat(urn.id).isEqualTo("abc-123");
  }

  @Test
  @Category(UnitTests.class)
  public void testParseOwner_GroupPrefix_Capitalized() {
    URN urn = FmeOwnerHelper.parseOwner("Group:xyz-456");
    assertThat(urn.type).isEqualTo("group");
    assertThat(urn.id).isEqualTo("xyz-456");
  }

  @Test
  @Category(UnitTests.class)
  public void testParseOwner_GroupPrefix_Lowercase() {
    URN urn = FmeOwnerHelper.parseOwner("group:xyz-456");
    assertThat(urn.type).isEqualTo("group");
    assertThat(urn.id).isEqualTo("xyz-456");
  }

  @Test
  @Category(UnitTests.class)
  public void testParseOwner_BareId_DefaultsToUser() {
    URN urn = FmeOwnerHelper.parseOwner("abc-123-no-prefix");
    assertThat(urn.type).isEqualTo("User");
    assertThat(urn.id).isEqualTo("abc-123-no-prefix");
  }

  @Test
  @Category(UnitTests.class)
  public void testParseOwner_UuidWithUserPrefix() {
    URN urn = FmeOwnerHelper.parseOwner("User:550e8400-e29b-41d4-a716-446655440000");
    assertThat(urn.type).isEqualTo("User");
    assertThat(urn.id).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
  }

  @Test
  @Category(UnitTests.class)
  public void testParseOwner_UuidWithGroupPrefix() {
    URN urn = FmeOwnerHelper.parseOwner("Group:550e8400-e29b-41d4-a716-446655440000");
    assertThat(urn.type).isEqualTo("group");
    assertThat(urn.id).isEqualTo("550e8400-e29b-41d4-a716-446655440000");
  }

  @Test
  @Category(UnitTests.class)
  public void testParseOwners_MixedList() {
    List<String> owners = Arrays.asList("User:u1", "Group:g1", "User:u2");
    List<URN> urns = FmeOwnerHelper.parseOwners(owners);

    assertThat(urns).hasSize(3);
    assertThat(urns.get(0).type).isEqualTo("User");
    assertThat(urns.get(0).id).isEqualTo("u1");
    assertThat(urns.get(1).type).isEqualTo("group");
    assertThat(urns.get(1).id).isEqualTo("g1");
    assertThat(urns.get(2).type).isEqualTo("User");
    assertThat(urns.get(2).id).isEqualTo("u2");
  }

  @Test
  @Category(UnitTests.class)
  public void testParseOwners_EmptyList() {
    List<URN> urns = FmeOwnerHelper.parseOwners(Collections.emptyList());
    assertThat(urns).isEmpty();
  }

  @Test
  @Category(UnitTests.class)
  public void testParseOwners_BackwardCompat_BareIds() {
    List<String> owners = Arrays.asList("bare-id-1", "bare-id-2");
    List<URN> urns = FmeOwnerHelper.parseOwners(owners);

    assertThat(urns).hasSize(2);
    assertThat(urns.get(0).type).isEqualTo("User");
    assertThat(urns.get(0).id).isEqualTo("bare-id-1");
    assertThat(urns.get(1).type).isEqualTo("User");
    assertThat(urns.get(1).id).isEqualTo("bare-id-2");
  }

  @Test
  @Category(UnitTests.class)
  public void testParseOwners_MixedPrefixedAndBare() {
    List<String> owners = Arrays.asList("User:u1", "bare-id", "Group:g1");
    List<URN> urns = FmeOwnerHelper.parseOwners(owners);

    assertThat(urns).hasSize(3);
    assertThat(urns.get(0).type).isEqualTo("User");
    assertThat(urns.get(0).id).isEqualTo("u1");
    assertThat(urns.get(1).type).isEqualTo("User");
    assertThat(urns.get(1).id).isEqualTo("bare-id");
    assertThat(urns.get(2).type).isEqualTo("group");
    assertThat(urns.get(2).id).isEqualTo("g1");
  }

  @Test
  @Category(UnitTests.class)
  public void testParseOwner_UnknownPrefix_PassedThrough() {
    URN urn = FmeOwnerHelper.parseOwner("custom:some-id");
    assertThat(urn.type).isEqualTo("custom");
    assertThat(urn.id).isEqualTo("some-id");
  }

  @Test
  @Category(UnitTests.class)
  public void testParseOwner_NullInput_ThrowsException() {
    assertThatThrownBy(() -> FmeOwnerHelper.parseOwner(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null or empty");
  }

  @Test
  @Category(UnitTests.class)
  public void testParseOwner_EmptyInput_ThrowsException() {
    assertThatThrownBy(() -> FmeOwnerHelper.parseOwner(""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("null or empty");
  }

  @Test
  @Category(UnitTests.class)
  public void testParseOwner_ColonAtEnd_TreatedAsBareId() {
    URN urn = FmeOwnerHelper.parseOwner("User:");
    assertThat(urn.type).isEqualTo("User");
    assertThat(urn.id).isEqualTo("User:");
  }

  @Test
  @Category(UnitTests.class)
  public void testParseOwner_ColonAtStart_TreatedAsBareId() {
    URN urn = FmeOwnerHelper.parseOwner(":some-id");
    assertThat(urn.type).isEqualTo("User");
    assertThat(urn.id).isEqualTo(":some-id");
  }
}
