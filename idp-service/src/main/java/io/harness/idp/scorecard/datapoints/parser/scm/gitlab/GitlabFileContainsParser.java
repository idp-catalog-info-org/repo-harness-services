/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.scm.gitlab;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.parser.scm.ScmFileContainsParser;

import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.IDP)
public class GitlabFileContainsParser extends ScmFileContainsParser {
  @Override
  protected String getFileContent(Map<String, Object> data) {
    List<Map<String, Object>> nodes = (List<Map<String, Object>>) CommonUtils.findObjectByName(data, "nodes");
    if (isEmpty(nodes)) {
      return null;
    }
    return (String) nodes.get(0).get("rawTextBlob");
  }
}
