/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ng.runner.cgi;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.configuration.CgiTaskConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class CgiConfigService {
  @Inject @Named("cgiTaskConfig") public Map<String, CgiTaskConfig> cgiTaskConfig;

  public CgiTaskConfig get(String type) {
    return cgiTaskConfig.get(type);
  }

  public Map<String, CgiTaskConfig> getMany(List<String> cgiTypes) {
    if (cgiTaskConfig == null || isEmpty(cgiTypes)) {
      return Collections.emptyMap();
    }
    return cgiTypes.stream()
        .filter(cgiTaskConfig::containsKey)
        .collect(Collectors.toMap(Function.identity(), cgiTaskConfig::get));
  }
}
