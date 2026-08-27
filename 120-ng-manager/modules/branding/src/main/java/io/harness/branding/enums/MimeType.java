/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.branding.enums;
public enum MimeType {
  PNG("image/png"),
  ICO("image/x-icon"),
  DEFAULT("application/octet-stream");
  private final String type;
  MimeType(String type) {
    this.type = type;
  }
  public String getType() {
    return type;
  }
  @Override
  public String toString() {
    return type;
  }
  public static MimeType fromExtension(String extension) {
    if (extension == null) {
      return DEFAULT;
    }
    String ext = extension.trim().toLowerCase();
    for (MimeType mime : values()) {
      if (mime.name().equalsIgnoreCase(ext)) {
        return mime;
      }
    }
    return DEFAULT;
  }
}