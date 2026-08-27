/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.cd.api.beans;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceResponse {
  ServiceResponseDTO service;
  Long createdAt;
  Long lastModifiedAt;

  @Builder
  public ServiceResponse(ServiceResponseDTO service, Long createdAt, Long lastModifiedAt) {
    this.service = service;
    this.createdAt = createdAt;
    this.lastModifiedAt = lastModifiedAt;
  }
}
