/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.buildtriggers.helpers.generator;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_GITX, HarnessModuleComponent.CDS_TRIGGERS})
public class Constants {
  public static String CONNECTOR = "spec.connector";
  public static String REPO = "spec.repo";
  public static String REPO_NAME = "spec.repo";
  public static String REPO_FORMAT = "spec.repo";
  public static String REPO_URL = "spec.repo";
  public static String PKG = "spec.pkg";
  public static String PKG_NAME = "spec.pkg.name";
  public static String PKG_TYPE = "spec.pkg.type";
  public static String LOCATION = "spec.location";
  public static String VERSION_REGEX = "spec.version_regex";
  public static String PATH = "spec.path";
  public static String DIR = "spec.dir";
  public static String FILTER = "spec.filter";
  public static String SUBSCRIPTION = "spec.subscription";
  public static String PATHS = "spec.paths";
  public static String PLAN_KEY = "spec.plan_key";
  public static String REGISTRY = "spec.registry";
  public static String HOST = "spec.host";
  public static String JOB = "spec.job";
  public static String GROUP_ID = "group_id";
  public static String ARTIFACT = "spec.artifact";
  public static String PORT = "spec.port";
  public static String BUCKET = "spec.bucket";
  public static String PATH_REGEX = "spec.path_regex";
  public static String CHART = "spec.chart";
  public static String HELM_VERSION = "spec.helm_version";
  public static String HELM_CONNECTOR = "spec.store.spec.connector";
  public static String HELM_LOCATION = "spec.store.spec.location";
}
