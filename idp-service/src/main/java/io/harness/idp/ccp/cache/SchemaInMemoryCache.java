/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.ccp.cache;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.UnexpectedException;
import io.harness.idp.common.CommonUtils;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Singleton;
import org.jetbrains.annotations.NotNull;

@Singleton
@CodePulse(module = ProductModule.IDP, unitCoverageRequired = true, components = {HarnessModuleComponent.IDP_SERVICE})
@OwnedBy(HarnessTeam.IDP)
public class SchemaInMemoryCache implements SchemaCache {
  private static final long MAX_CACHE_SIZE = 8;
  public static final String SCHEMA_PATH_PATTERN = "backstage-entity-schema/%s.schema.json";
  LoadingCache<String, String> cache = CacheBuilder.newBuilder().maximumSize(MAX_CACHE_SIZE).build(new CacheLoader<>() {
    @NotNull
    @Override
    public String load(@NotNull String kind) {
      return CommonUtils.readFileFromClassPath(String.format(SCHEMA_PATH_PATTERN, kind));
    }
  });

  @Override
  public String get(String kind) {
    try {
      return cache.get(kind.toLowerCase());
    } catch (Exception e) {
      throw new UnexpectedException(String.format("Failed to load schema with error: %s", e.getMessage()), e);
    }
  }
}
