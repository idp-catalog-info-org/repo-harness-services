/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasourcelocations.locations;

import io.harness.idp.scorecard.datasourcelocations.entity.DataSourceLocationEntity;
import io.harness.idp.scorecard.scores.beans.DataFetchDTO;

import java.util.List;
import java.util.Map;

public interface DataSourceLocationV2 {
  Map<String, Object> fetchData(String accountIdentifier, Object entity,
      DataSourceLocationEntity dataSourceLocationEntity, List<DataFetchDTO> dataPointAndInputValues);
}
