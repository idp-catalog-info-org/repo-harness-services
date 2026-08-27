/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class MetadataFieldConstants {
  public static final String IDENTIFIER = "identifier";
  public static final String NAME = "name";
  public static final String ABSOLUTE_IDENTIFIER = "absoluteIdentifier";
  public static final String TITLE = "title";
  public static final String NAMESPACE = "namespace";
  public static final String DESCRIPTION = "description";
  public static final String TAGS = "tags";
  public static final String UID = "uid";
  public static final String ETAG = "etag";
  public static final String ANNOTATIONS = "annotations";
  public static final String LINKS = "links";
  public static final String LABELS = "labels";
  public static final String HARNESS_DATA = "harnessData";
}
