/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.migration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.migration.CreateDefaultCloudImageConfigMappingNGMigration;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.CI)
public class CreateDefaultUbuntuImageMappingNGMigrationTest extends CategoryTest {
  @Mock private MongoTemplate mongoTemplate;
  @Mock private MongoDatabase mongoDatabase;
  @Mock private MongoCollection<Document> mongoCollection;
  @Mock private FindIterable<Document> findIterable;

  private CreateDefaultCloudImageConfigMappingNGMigration migration;

  private static final String GLOBAL_ACCOUNT_ID = "global";
  private static final String COLLECTION_NAME = "buildImageConfig";
  private static final String UBUNTU_LATEST_VERSION = "ubuntu-latest";
  private static final String UBUNTU_24_VERSION = "ubuntu-24";
  private static final String MAC_LATEST_VERSION = "mac-latest";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    migration = new CreateDefaultCloudImageConfigMappingNGMigration();
    // Inject mocked MongoTemplate into the migration via reflection (field is private)
    Field mongoTemplateField = CreateDefaultCloudImageConfigMappingNGMigration.class.getDeclaredField("mongoTemplate");
    mongoTemplateField.setAccessible(true);
    mongoTemplateField.set(migration, mongoTemplate);

    when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
    when(mongoDatabase.getCollection(COLLECTION_NAME)).thenReturn(mongoCollection);
    when(mongoCollection.find(any(Document.class))).thenReturn(findIterable);
  }

  @Test
  @Owner(developers = OwnerRule.ABHAY)
  @Category(UnitTests.class)
  public void testMigrateWithEmptyDatabase() {
    when(findIterable.first()).thenReturn(null);

    migration.migrate();

    verify(mongoCollection, times(1)).insertOne(any(Document.class));
    verify(mongoCollection, never()).updateOne(any(Document.class), any(Document.class));
  }

  @Test
  @Owner(developers = OwnerRule.ABHAY)
  @Category(UnitTests.class)
  public void testMigrateWithExistingConfigButNoDefaultMappings() {
    Document existingConfig = new Document()
                                  .append("accountId", GLOBAL_ACCOUNT_ID)
                                  .append("data",
                                      new Document()
                                          .append("linux_amd64", new Document("primary", Arrays.asList()))
                                          .append("linux_arm64", new Document("primary", Arrays.asList()))
                                          .append("mac_arm64", new Document("primary", Arrays.asList()))
                                          .append("windows_amd64", new Document("primary", Arrays.asList())));

    when(findIterable.first()).thenReturn(existingConfig);

    migration.migrate();

    verify(mongoCollection, times(4)).updateOne(any(Document.class), any(Document.class));
    verify(mongoCollection, never()).insertOne(any(Document.class));
  }

  @Test
  @Owner(developers = OwnerRule.ABHAY)
  @Category(UnitTests.class)
  public void testMigrateWithExistingDefaultMappings() {
    Document existingConfig = createConfigWithDefaultMappings();
    when(findIterable.first()).thenReturn(existingConfig);

    migration.migrate();

    verify(mongoCollection, never()).insertOne(any(Document.class));
    verify(mongoCollection, never()).updateOne(any(Document.class), any(Document.class));
  }

  @Test
  @Owner(developers = OwnerRule.ABHAY)
  @Category(UnitTests.class)
  public void testMigrateIdempotency() {
    when(findIterable.first()).thenReturn(null);

    migration.migrate();

    Document configWithDefaults = createConfigWithDefaultMappings();
    when(findIterable.first()).thenReturn(configWithDefaults);

    migration.migrate();

    verify(mongoCollection, times(1)).insertOne(any(Document.class));
    verify(mongoCollection, never()).updateOne(any(Document.class), any(Document.class));
  }

  @Test
  @Owner(developers = OwnerRule.ABHAY)
  @Category(UnitTests.class)
  public void testMigrateWithDatabaseException() {
    when(findIterable.first()).thenThrow(new RuntimeException("Database connection error"));

    assertThatThrownBy(() -> migration.migrate())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("NG Migration failed for Hosted image mappings")
        .hasCauseInstanceOf(RuntimeException.class);
  }

  @Test
  @Owner(developers = OwnerRule.ABHAY)
  @Category(UnitTests.class)
  public void testCreateNewGlobalConfigStructure() {
    when(findIterable.first()).thenReturn(null);

    migration.migrate();

    verify(mongoCollection, times(1)).insertOne(any(Document.class));
  }

  @Test
  @Owner(developers = OwnerRule.ABHAY)
  @Category(UnitTests.class)
  public void testMergeWithPartialExistingData() {
    Document partialConfig =
        new Document()
            .append("accountId", GLOBAL_ACCOUNT_ID)
            .append("data",
                new Document().append("linux_amd64",
                    new Document("primary",
                        Arrays.asList(
                            new Document("version", "custom-ubuntu").append("image", "custom/ubuntu:latest")))));

    when(findIterable.first()).thenReturn(partialConfig);

    migration.migrate();

    verify(mongoCollection, times(4)).updateOne(any(Document.class), any(Document.class));
  }

  private Document createConfigWithDefaultMappings() {
    List<Document> linuxAmd64Mappings =
        Arrays.asList(new Document("version", UBUNTU_LATEST_VERSION).append("image", "harness/vmimage:v2"),
            new Document("version", UBUNTU_24_VERSION)
                .append("image", "harness/vmimage:hosted-vm-ubuntu-2404-noble-amd64-v20250530"));

    List<Document> linuxArm64Mappings = Arrays.asList(
        new Document("version", UBUNTU_LATEST_VERSION).append("image", "harness/vmimage:hosted-vm-164-arm"));

    List<Document> macArm64Mappings =
        Arrays.asList(new Document("version", MAC_LATEST_VERSION).append("image", "harness-tart"));

    List<Document> windowsAmd64Mappings =
        Arrays.asList(new Document("version", "windows-latest").append("image", "hosted-vm-windows22--119"));

    return new Document()
        .append("accountId", GLOBAL_ACCOUNT_ID)
        .append("data",
            new Document()
                .append("linux_amd64", new Document("primary", linuxAmd64Mappings))
                .append("linux_arm64", new Document("primary", linuxArm64Mappings))
                .append("mac_arm64", new Document("primary", macArm64Mappings))
                .append("windows_amd64", new Document("primary", windowsAmd64Mappings)));
  }
}