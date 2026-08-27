/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.conversion;

import static io.harness.rule.OwnerRule.RISHIKESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.gitsync.beans.StoreType;
import io.harness.goconvert.EntityType;
import io.harness.pms.conversion.beans.ConversionChecksum;
import io.harness.rule.Owner;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public class ConversionChecksumRepositoryCustomImplTest extends CategoryTest {
  private static final String ACCOUNT_ID = "account123";
  private static final String ORG_ID = "org1";
  private static final String PROJECT_ID = "project1";
  private static final String PARENT_UNIQUE_ID = "parent1";

  @Mock private MongoTemplate mongoTemplate;
  private ConversionChecksumRepositoryCustomImpl repository;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    java.lang.reflect.Constructor<ConversionChecksumRepositoryCustomImpl> constructor =
        ConversionChecksumRepositoryCustomImpl.class.getDeclaredConstructor(MongoTemplate.class);
    constructor.setAccessible(true);
    repository = constructor.newInstance(mongoTemplate);
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testFindByInlineEntity() {
    ConversionChecksum checksum = buildInlineChecksum();
    when(mongoTemplate.findOne(any(Query.class), eq(ConversionChecksum.class))).thenReturn(checksum);

    Optional<ConversionChecksum> result = repository.findByInlineEntity(
        ACCOUNT_ID, PARENT_UNIQUE_ID, ORG_ID, PROJECT_ID, "myPipeline", EntityType.PIPELINE, null);

    assertThat(result).isPresent();
    assertThat(result.get().getEntityId()).isEqualTo("myPipeline");
    assertThat(result.get().getStoreType()).isEqualTo(StoreType.INLINE);
    verify(mongoTemplate).findOne(any(Query.class), eq(ConversionChecksum.class));
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testFindByInlineEntityNotFound() {
    when(mongoTemplate.findOne(any(Query.class), eq(ConversionChecksum.class))).thenReturn(null);

    Optional<ConversionChecksum> result = repository.findByInlineEntity(
        ACCOUNT_ID, PARENT_UNIQUE_ID, ORG_ID, PROJECT_ID, "nonexistent", EntityType.PIPELINE, null);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testFindByRemoteEntity() {
    ConversionChecksum checksum = buildRemoteChecksum();
    when(mongoTemplate.findOne(any(Query.class), eq(ConversionChecksum.class))).thenReturn(checksum);

    Optional<ConversionChecksum> result = repository.findByRemoteEntity(
        ACCOUNT_ID, PARENT_UNIQUE_ID, ORG_ID, PROJECT_ID, "remotePipeline", "myRepo", "main", null);

    assertThat(result).isPresent();
    assertThat(result.get().getStoreType()).isEqualTo(StoreType.REMOTE);
    assertThat(result.get().getBranch()).isEqualTo("main");
    verify(mongoTemplate).findOne(any(Query.class), eq(ConversionChecksum.class));
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testFindByRemoteEntityNotFound() {
    when(mongoTemplate.findOne(any(Query.class), eq(ConversionChecksum.class))).thenReturn(null);

    Optional<ConversionChecksum> result = repository.findByRemoteEntity(
        ACCOUNT_ID, PARENT_UNIQUE_ID, ORG_ID, PROJECT_ID, "remotePipeline", "myRepo", "nonexistent", null);

    assertThat(result).isEmpty();
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testUpsertInlineChecksum() {
    ConversionChecksum checksum = buildInlineChecksum();
    when(mongoTemplate.findAndModify(
             any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(ConversionChecksum.class)))
        .thenReturn(checksum);

    ConversionChecksum result = repository.upsert(checksum);

    assertThat(result).isNotNull();
    assertThat(result.getEntityId()).isEqualTo("myPipeline");
    verify(mongoTemplate)
        .findAndModify(
            any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(ConversionChecksum.class));
  }

  @Test
  @Owner(developers = RISHIKESH)
  @Category(UnitTests.class)
  public void testUpsertRemoteChecksum() {
    ConversionChecksum checksum = buildRemoteChecksum();
    when(mongoTemplate.findAndModify(
             any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(ConversionChecksum.class)))
        .thenReturn(checksum);

    ConversionChecksum result = repository.upsert(checksum);

    assertThat(result).isNotNull();
    assertThat(result.getStoreType()).isEqualTo(StoreType.REMOTE);
    assertThat(result.getBranch()).isEqualTo("main");
  }

  private ConversionChecksum buildInlineChecksum() {
    return ConversionChecksum.builder()
        .accountId(ACCOUNT_ID)
        .orgId(ORG_ID)
        .projectId(PROJECT_ID)
        .entityId("myPipeline")
        .entityType(EntityType.PIPELINE)
        .storeType(StoreType.INLINE)
        .checksum("sha256abc")
        .v1Identifier("myPipeline_a3f2")
        .build();
  }

  private ConversionChecksum buildRemoteChecksum() {
    return ConversionChecksum.builder()
        .accountId(ACCOUNT_ID)
        .orgId(ORG_ID)
        .projectId(PROJECT_ID)
        .entityId("remotePipeline")
        .entityType(EntityType.PIPELINE)
        .storeType(StoreType.REMOTE)
        .repoURL("myRepo")
        .branch("main")
        .filePath(".harness/remotePipeline.yaml")
        .checksum("sha256def")
        .v1Identifier("remotePipeline_b4c3")
        .build();
  }
}
