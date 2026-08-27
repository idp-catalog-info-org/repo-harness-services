/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.repositories;

import static io.harness.rule.OwnerRule.SATHISH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.backstage.entities.BackstageCatalogComponentEntity;
import io.harness.idp.backstage.entities.BackstageCatalogEntity;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class BackstageCatalogEntityRepositoryCustomImplTest extends CategoryTest {
  static final String TEST_ACCOUNT = "testAccount";
  static final String TEST_KIND = "testKind";
  static final String TEST_TYPE = "testType";
  static final List<String> TEST_OWNERS = Collections.singletonList("TEST_OWNER");
  static final List<String> TEST_TAGS = Collections.singletonList("TEST_TAG");
  static final List<String> TEST_LIFE_CYCLES = Collections.singletonList("TEST_LIFECYCLE");

  AutoCloseable openMocks;
  @InjectMocks BackstageCatalogEntityRepositoryCustomImpl backstageCatalogEntityRepositoryCustom;
  @Mock MongoTemplate mongoTemplate;

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testQueryEntities() {
    BackstageCatalogEntity backstageCatalogEntity =
        BackstageCatalogComponentEntity.builder()
            .accountIdentifier(TEST_ACCOUNT)
            .kind(TEST_KIND)
            .spec(BackstageCatalogComponentEntity.Spec.builder().type(TEST_TYPE).build())
            .build();
    when(mongoTemplate.find(any(), any())).thenReturn(Collections.singletonList(backstageCatalogEntity));
    List<BackstageCatalogEntity> backstageCatalogEntities = backstageCatalogEntityRepositoryCustom.queryEntities(
        TEST_KIND, TEST_TYPE, TEST_OWNERS, TEST_TAGS, TEST_LIFE_CYCLES, TEST_ACCOUNT, new ArrayList<>());
    assertThat(backstageCatalogEntities).isNotNull();
    assertThat(backstageCatalogEntities.size()).isEqualTo(1);
  }

  @After
  public void tearDown() throws Exception {
    openMocks.close();
  }
}
