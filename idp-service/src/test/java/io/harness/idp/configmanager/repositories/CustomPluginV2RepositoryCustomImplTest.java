/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.configmanager.repositories;

import static io.harness.rule.OwnerRule.VIGNESWARA;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.configmanager.entities.CustomPluginV2Entity;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(HarnessTeam.IDP)
public class CustomPluginV2RepositoryCustomImplTest {
  @InjectMocks private CustomPluginV2RepositoryCustomImpl customPluginV2RepositoryCustomImpl;
  @Mock private MongoTemplate mongoTemplate;

  private static final String ACCOUNT_ID = "test-account-id";
  private static final String PLUGIN_ID = "my-custom-plugin";
  private static final String PLUGIN_NAME = "My Custom Plugin";

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCustomPluginsV2WithDefaultSort() {
    List<CustomPluginV2Entity> entities = Collections.singletonList(buildEntity());
    when(mongoTemplate.find(any(Query.class), eq(CustomPluginV2Entity.class))).thenReturn(entities);
    when(mongoTemplate.count(any(Query.class), eq(CustomPluginV2Entity.class))).thenReturn(1L);

    Page<CustomPluginV2Entity> result =
        customPluginV2RepositoryCustomImpl.getCustomPluginsV2(ACCOUNT_ID, 0, 10, null, null);

    assertNotNull(result);
    assertEquals(1, result.getContent().size());
    assertEquals(PLUGIN_ID, result.getContent().get(0).getIdentifier());
    assertEquals(PLUGIN_NAME, result.getContent().get(0).getName());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCustomPluginsV2WithEmptySort() {
    List<CustomPluginV2Entity> entities = Collections.singletonList(buildEntity());
    when(mongoTemplate.find(any(Query.class), eq(CustomPluginV2Entity.class))).thenReturn(entities);
    when(mongoTemplate.count(any(Query.class), eq(CustomPluginV2Entity.class))).thenReturn(1L);

    Page<CustomPluginV2Entity> result =
        customPluginV2RepositoryCustomImpl.getCustomPluginsV2(ACCOUNT_ID, 0, 10, "", null);

    assertNotNull(result);
    assertEquals(1, result.getContent().size());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCustomPluginsV2WithCustomSort() {
    List<CustomPluginV2Entity> entities = Collections.singletonList(buildEntity());
    when(mongoTemplate.find(any(Query.class), eq(CustomPluginV2Entity.class))).thenReturn(entities);
    when(mongoTemplate.count(any(Query.class), eq(CustomPluginV2Entity.class))).thenReturn(1L);

    Page<CustomPluginV2Entity> result =
        customPluginV2RepositoryCustomImpl.getCustomPluginsV2(ACCOUNT_ID, 0, 10, "name", null);

    assertNotNull(result);
    assertEquals(1, result.getContent().size());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCustomPluginsV2WithSearchTerm() {
    List<CustomPluginV2Entity> entities = Collections.singletonList(buildEntity());
    when(mongoTemplate.find(any(Query.class), eq(CustomPluginV2Entity.class))).thenReturn(entities);
    when(mongoTemplate.count(any(Query.class), eq(CustomPluginV2Entity.class))).thenReturn(1L);

    Page<CustomPluginV2Entity> result =
        customPluginV2RepositoryCustomImpl.getCustomPluginsV2(ACCOUNT_ID, 0, 10, null, "custom");

    assertNotNull(result);
    assertEquals(1, result.getContent().size());
    assertEquals(PLUGIN_NAME, result.getContent().get(0).getName());
  }

  @Test
  @Owner(developers = VIGNESWARA)
  @Category(UnitTests.class)
  public void testGetCustomPluginsV2EmptyResult() {
    when(mongoTemplate.find(any(Query.class), eq(CustomPluginV2Entity.class))).thenReturn(new ArrayList<>());
    when(mongoTemplate.count(any(Query.class), eq(CustomPluginV2Entity.class))).thenReturn(0L);

    Page<CustomPluginV2Entity> result =
        customPluginV2RepositoryCustomImpl.getCustomPluginsV2(ACCOUNT_ID, 0, 10, null, null);

    assertNotNull(result);
    assertEquals(0, result.getContent().size());
    assertEquals(0, result.getTotalElements());
  }

  private CustomPluginV2Entity buildEntity() {
    return CustomPluginV2Entity.builder()
        .identifier(PLUGIN_ID)
        .accountIdentifier(ACCOUNT_ID)
        .name(PLUGIN_NAME)
        .description("A custom plugin description")
        .icon("icon-url")
        .build();
  }
}
