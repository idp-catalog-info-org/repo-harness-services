/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.support.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Value;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "CannyCategoriesResponse",
    description = "Contains list of Categories and their ID's from Canny for a given board")
public class CannyCategoriesResponseDTO {
  String boardId;
  List<Category> categoryList;

  @Value
  @Builder
  public static class Category {
    private String name;
    private String id;
  }
}
