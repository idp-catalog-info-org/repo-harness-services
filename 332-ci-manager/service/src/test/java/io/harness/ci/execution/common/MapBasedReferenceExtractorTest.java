/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.common;

import static io.harness.rule.OwnerRule.CHIRAG_S;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.eventsframework.schemas.entity.EntityTypeProtoEnum;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class MapBasedReferenceExtractorTest {
  private MapBasedReferenceExtractor mapBasedReferenceExtractor;
  private Ambiance ambiance;

  @Before
  public void setUp() {
    mapBasedReferenceExtractor = new MapBasedReferenceExtractor();
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "account");
    setupAbstractions.put("orgIdentifier", "org");
    setupAbstractions.put("projectIdentifier", "project");
    ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromManifestMapWithNull() {
    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromManifestMap(null, ambiance);
    assertThat(refs).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromManifestMapWithConnector() {
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> store = new HashMap<>();
    store.put("connector", "my-connector");
    manifestMap.put("store", store);

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromManifestMap(manifestMap, ambiance);
    assertThat(refs).isNotEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromManifestMapSkipsExpressions() {
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> store = new HashMap<>();
    store.put("connector", "${{inputs.connector}}");
    manifestMap.put("store", store);

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromManifestMap(manifestMap, ambiance);
    assertThat(refs).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromArtifactMapWithNull() {
    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromArtifactMap(null, ambiance);
    assertThat(refs).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromArtifactMapWithConnector() {
    Map<String, Object> artifactMap = new HashMap<>();
    artifactMap.put("connector", "docker-connector");

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromArtifactMap(artifactMap, ambiance);
    assertThat(refs).isNotEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromArtifactMapWithInputsConnector() {
    Map<String, Object> artifactMap = new HashMap<>();
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("artifactConnector", "art-connector");
    artifactMap.put("inputs", inputs);

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromArtifactMap(artifactMap, ambiance);
    assertThat(refs).isNotEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromConfigFileMapWithNull() {
    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromConfigFileMap(null, ambiance);
    assertThat(refs).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromConfigFileMapWithConnector() {
    Map<String, Object> configFileMap = new HashMap<>();
    Map<String, Object> store = new HashMap<>();
    store.put("connector", "git-connector");
    configFileMap.put("store", store);

    Set<EntityDetailProtoDTO> refs =
        mapBasedReferenceExtractor.extractReferencesFromConfigFileMap(configFileMap, ambiance);
    assertThat(refs).isNotEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromManifestMapWithSecretRef() {
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("password", "secrets.getValue(\"mySecret\")");
    manifestMap.put("inputs", inputs);

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromManifestMap(manifestMap, ambiance);
    assertThat(refs).isNotEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesSkipsHarnessExpressions() {
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> store = new HashMap<>();
    store.put("connector", "<+pipeline.variables.connector>");
    manifestMap.put("store", store);

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromManifestMap(manifestMap, ambiance);
    assertThat(refs).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromManifestMapWithWithSection() {
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> with = new HashMap<>();
    with.put("connector", "with-connector");
    with.put("token", "prefix secrets.getValue(\"withSecret\") suffix");
    manifestMap.put("with", with);

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromManifestMap(manifestMap, ambiance);
    assertThat(refs.stream().filter(r -> r != null && r.getType() == EntityTypeProtoEnum.CONNECTORS)).hasSize(1);
    assertThat(refs.stream().filter(r -> r != null && r.getType() == EntityTypeProtoEnum.SECRETS)).hasSize(1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromManifestMapMultipleSecretsInOneString() {
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("combined", "secrets.getValue(\"s1\") and secrets.getValue(\"s2\")");
    manifestMap.put("inputs", inputs);

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromManifestMap(manifestMap, ambiance);
    assertThat(refs.stream().filter(r -> r != null && r.getType() == EntityTypeProtoEnum.SECRETS)).hasSize(2);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesCaseInsensitiveConnectorInNestedMap() {
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> inputs = new HashMap<>();
    Map<String, Object> nested = new HashMap<>();
    nested.put("CONNECTOR", "case-connector");
    inputs.put("spec", nested);
    manifestMap.put("inputs", inputs);

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromManifestMap(manifestMap, ambiance);
    assertThat(refs.stream().filter(r -> r != null && r.getType() == EntityTypeProtoEnum.CONNECTORS)).hasSize(1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromArtifactMapWithWithNestedMapAndList() {
    Map<String, Object> artifactMap = new HashMap<>();
    artifactMap.put("connector", "top-connector");

    Map<String, Object> with = new HashMap<>();
    with.put("connector", "with-artifact-connector");
    artifactMap.put("with", with);

    Map<String, Object> inputs = new HashMap<>();
    Map<String, Object> nested = new HashMap<>();
    nested.put("CONNECTOR", "nested-connector");
    inputs.put("spec", nested);

    List<Object> items = new ArrayList<>();
    items.add("secrets.getValue(\"listSecret\")");
    Map<String, Object> mapInList = new HashMap<>();
    mapInList.put("connector", "list-map-connector");
    items.add(mapInList);
    List<Object> innerList = new ArrayList<>();
    innerList.add("secrets.getValue(\"innerListSecret\")");
    items.add(innerList);
    inputs.put("items", items);
    artifactMap.put("inputs", inputs);

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromArtifactMap(artifactMap, ambiance);
    assertThat(refs.stream().filter(Objects::nonNull)).hasSize(6);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromConfigFileMapWithInputsSecretAndNestedConnector() {
    Map<String, Object> configFileMap = new HashMap<>();
    Map<String, Object> store = new HashMap<>();
    store.put("connector", "git-connector");
    configFileMap.put("store", store);

    Map<String, Object> inputs = new HashMap<>();
    inputs.put("password", "secrets.getValue(\"cfgSecret\")");
    Map<String, Object> nested = new HashMap<>();
    nested.put("connector", "cfg-nested-connector");
    inputs.put("nested", nested);
    configFileMap.put("inputs", inputs);

    Set<EntityDetailProtoDTO> refs =
        mapBasedReferenceExtractor.extractReferencesFromConfigFileMap(configFileMap, ambiance);
    assertThat(refs.stream().filter(Objects::nonNull)).hasSize(3);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromManifestMapSkipsNullInputValues() {
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("skip", null);
    inputs.put("ok", "secrets.getValue(\"onlySecret\")");
    manifestMap.put("inputs", inputs);

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromManifestMap(manifestMap, ambiance);
    assertThat(refs.stream().filter(r -> r != null && r.getType() == EntityTypeProtoEnum.SECRETS)).hasSize(1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromManifestMapNonStringStoreConnector() {
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> store = new HashMap<>();
    store.put("connector", Integer.valueOf(123));
    manifestMap.put("store", store);

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromManifestMap(manifestMap, ambiance);
    assertThat(refs).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromArtifactMapSkipsExpressionConnector() {
    Map<String, Object> artifactMap = new HashMap<>();
    artifactMap.put("connector", "${{inputs.artifactConnector}}");

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromArtifactMap(artifactMap, ambiance);
    assertThat(refs).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesFromManifestMapWithoutStore() {
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("k", "secrets.getValue(\"noStoreSecret\")");
    manifestMap.put("inputs", inputs);

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromManifestMap(manifestMap, ambiance);
    assertThat(refs.stream().filter(r -> r != null && r.getType() == EntityTypeProtoEnum.SECRETS)).hasSize(1);
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractSecretReferencesSkipsEmptyStringAndExpressionOnlyValues() {
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("empty", "");
    inputs.put("expr", "${{secrets.getValue(\"ignored\")}}");
    manifestMap.put("inputs", inputs);

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromManifestMap(manifestMap, ambiance);
    assertThat(refs).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesInvalidConnectorRefYieldsNullEntry() {
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> store = new HashMap<>();
    store.put("connector", "org.id.extraSegment");
    manifestMap.put("store", store);

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromManifestMap(manifestMap, ambiance);
    assertThat(refs).containsNull();
    assertThat(refs.stream().filter(Objects::nonNull)).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesInvalidSecretRefInGetValueYieldsNullEntry() {
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> inputs = new HashMap<>();
    inputs.put("bad", "secrets.getValue(\"a.b.c.invalid\")");
    manifestMap.put("inputs", inputs);

    Set<EntityDetailProtoDTO> refs = mapBasedReferenceExtractor.extractReferencesFromManifestMap(manifestMap, ambiance);
    assertThat(refs).containsNull();
    assertThat(refs.stream().filter(Objects::nonNull)).isEmpty();
  }

  @Test
  @Owner(developers = CHIRAG_S)
  @Category(UnitTests.class)
  public void testExtractReferencesWithAccountScopedConnectorAndAccountOnlyAmbiance() {
    Ambiance accountOnlyAmbiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "acct").build();
    Map<String, Object> manifestMap = new HashMap<>();
    Map<String, Object> store = new HashMap<>();
    store.put("connector", "account.acctScopedConnector");
    manifestMap.put("store", store);

    Set<EntityDetailProtoDTO> refs =
        mapBasedReferenceExtractor.extractReferencesFromManifestMap(manifestMap, accountOnlyAmbiance);
    assertThat(refs).hasSize(1);
    EntityDetailProtoDTO ref = refs.iterator().next();
    assertThat(ref.getType()).isEqualTo(EntityTypeProtoEnum.CONNECTORS);
    assertThat(ref.getIdentifierRef().getOrgIdentifier().getValue()).isEmpty();
    assertThat(ref.getIdentifierRef().getProjectIdentifier().getValue()).isEmpty();
  }
}
