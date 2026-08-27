/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.app.migration;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.beans.NGMigration;

import com.google.inject.Inject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * NG Migration for creating default Ubuntu image mappings.
 * This migration ensures the CI Manager service has the necessary default image mappings
 * for Ubuntu and Mac build environments.
 *
 * @author CI Team
 */
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class CreateDefaultCloudImageConfigMappingNGMigration implements NGMigration {
  @Inject private MongoTemplate mongoTemplate;

  private static final String GLOBAL_ACCOUNT_ID = "global";
  private static final String COLLECTION_NAME = "buildImageConfig";

  // Linux AMD64 mappings
  private static final String UBUNTU_LATEST_VERSION = "ubuntu-latest";
  private static final String UBUNTU_LATEST_AMD64_IMAGE = "harness/vmimage:v1";

  // Linux ARM64 mappings
  private static final String UBUNTU_LATEST_ARM64_IMAGE = "harness/vmimage:hosted-vm-164-arm";

  // Mac ARM64 mappings
  private static final String MAC_LATEST_VERSION = "mac-latest";
  private static final String MAC_LATEST_IMAGE = "harness-tart";
  // Windows mappings
  private static final String WINDOWS_LATEST_VERSION = "windows-latest";
  private static final String WINDOWS_LATEST_IMAGE = "hosted-vm-windows22--119";

  @Override
  public void migrate() {
    log.info("Starting NG Migration: Creating default hosted image mappings for CI Manager");

    try {
      MongoDatabase database = mongoTemplate.getDb();
      MongoCollection<Document> collection = database.getCollection(COLLECTION_NAME);

      // Check if default image mappings already exist
      if (defaultImageMappingsAlreadyExist(collection)) {
        log.info("Default image mappings already exist in global config, skipping migration");
        return;
      }

      // Add default image mappings without overwriting existing data
      addDefaultImageMappingsToGlobalConfig(collection);

      log.info("Successfully completed NG Migration: Default Hosted image mappings created");
    } catch (Exception e) {
      log.error("NG Migration failed: Error creating default Hosted image mappings", e);
      throw new RuntimeException("NG Migration failed for Hosted image mappings", e);
    }
  }

  private boolean defaultImageMappingsAlreadyExist(MongoCollection<Document> collection) {
    Document query = new Document("accountId", GLOBAL_ACCOUNT_ID);
    Document config = collection.find(query).first();

    if (config == null) {
      log.info("No global config exists, will create with default image mappings");
      return false;
    }

    // Check if any of the default mappings exist
    return hasDefaultMapping(config, "data.linux_amd64.primary")
        || hasDefaultMapping(config, "data.linux_arm64.primary") || hasDefaultMapping(config, "data.mac_arm64.primary");
  }

  private boolean hasDefaultMapping(Document config, String path) {
    Object primaryImages = getNestedValue(config, path);
    if (!(primaryImages instanceof List)) {
      return false;
    }

    List<?> imageList = (List<?>) primaryImages;
    return imageList.stream()
        .filter(obj -> obj instanceof Document)
        .map(obj -> (Document) obj)
        .anyMatch(img
            -> UBUNTU_LATEST_VERSION.equals(img.getString("version"))
                || MAC_LATEST_VERSION.equals(img.getString("version")));
  }

  private void addDefaultImageMappingsToGlobalConfig(MongoCollection<Document> collection) {
    Document query = new Document("accountId", GLOBAL_ACCOUNT_ID);
    Document existingConfig = collection.find(query).first();

    if (existingConfig == null) {
      // Create new config with default image mappings
      createNewGlobalConfig(collection);
    } else {
      // Add default image mappings to existing config
      mergeDefaultMappingsIntoExistingConfig(collection, existingConfig);
    }
  }

  private void createNewGlobalConfig(MongoCollection<Document> collection) {
    Document newConfig = new Document()
                             .append("accountId", GLOBAL_ACCOUNT_ID)
                             .append("data", createImageOSWithDefaultMappings())
                             .append("_class", "buildImageConfig")
                             .append("createdAt", System.currentTimeMillis());

    collection.insertOne(newConfig);
    log.info("Created new global config with default image mappings");
  }

  private void mergeDefaultMappingsIntoExistingConfig(MongoCollection<Document> collection, Document existingConfig) {
    Document query = new Document("accountId", GLOBAL_ACCOUNT_ID);

    // Add Linux AMD64 mappings
    addMappingsToPath(collection, query, "data.linux_amd64.primary", createLinuxAmd64Mappings(), existingConfig);

    // Add Linux ARM64 mappings
    addMappingsToPath(collection, query, "data.linux_arm64.primary", createLinuxArm64Mappings(), existingConfig);

    // Add Mac ARM64 mappings
    addMappingsToPath(collection, query, "data.mac_arm64.primary", createMacArm64Mappings(), existingConfig);
    // Add Windows mappings
    addMappingsToPath(collection, query, "data.windows.primary", createWindowsAmd64Mappings(), existingConfig);

    log.info("Merged default image mappings into existing global config");
  }

  private void addMappingsToPath(MongoCollection<Document> collection, Document query, String path,
      List<Document> mappings, Document existingConfig) {
    if (pathExists(existingConfig, path)) {
      // Path exists, use $addToSet to add only if not already present
      Document update = new Document("$addToSet", new Document(path, new Document("$each", mappings)));
      collection.updateOne(query, update);
    } else {
      // Path doesn't exist, create it with mappings
      Document update = new Document("$set", new Document(path, mappings));
      collection.updateOne(query, update);
    }
  }

  private Document createImageOSWithDefaultMappings() {
    Document linuxAmd64Env = new Document("primary", createLinuxAmd64Mappings());
    Document linuxArm64Env = new Document("primary", createLinuxArm64Mappings());
    Document macArm64Env = new Document("primary", createMacArm64Mappings());
    Document windowsEnv = new Document("primary", createWindowsAmd64Mappings());

    return new Document()
        .append("linux_amd64", linuxAmd64Env)
        .append("linux_arm64", linuxArm64Env)
        .append("mac_arm64", macArm64Env)
        .append("windows_amd64", windowsEnv);
  }

  private List<Document> createLinuxAmd64Mappings() {
    return Arrays.asList(new Document("version", UBUNTU_LATEST_VERSION).append("image", UBUNTU_LATEST_AMD64_IMAGE));
  }

  private List<Document> createLinuxArm64Mappings() {
    return Arrays.asList(new Document("version", UBUNTU_LATEST_VERSION).append("image", UBUNTU_LATEST_ARM64_IMAGE));
  }

  private List<Document> createMacArm64Mappings() {
    return Arrays.asList(new Document("version", MAC_LATEST_VERSION).append("image", MAC_LATEST_IMAGE));
  }

  private List<Document> createWindowsAmd64Mappings() {
    return Arrays.asList(new Document("version", WINDOWS_LATEST_VERSION).append("image", WINDOWS_LATEST_IMAGE));
  }

  private Object getNestedValue(Document obj, String path) {
    String[] parts = path.split("\\.");
    Object current = obj;

    for (String part : parts) {
      if (current instanceof Document) {
        current = ((Document) current).get(part);
      } else {
        return null;
      }
    }

    return current;
  }

  private boolean pathExists(Document obj, String path) {
    return getNestedValue(obj, path) != null;
  }
}