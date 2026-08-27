/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.migration;

import static io.harness.idp.common.JacksonUtils.readValue;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.idp.iterators.bean.Iterator;
import io.harness.idp.iterators.mapper.IteratorMapper;
import io.harness.idp.iterators.repositories.IteratorRepository;
import io.harness.migration.beans.NGMigration;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.IDP)
public class IteratorEntityMigration implements NGMigration {
  @Inject IteratorRepository iteratorRepository;
  private static final String ITERATORS_FILE_PATH = "migrations/iterators.json";

  @Override
  public void migrate() {
    log.info("Starting the migration for adding scheduled jobs in iterators collection.");
    String iteratorsContent = loadResourceFileAsString();
    List<Iterator> iterators = readValue(iteratorsContent, Iterator.class);
    iteratorRepository.saveAll(IteratorMapper.toEntityList(iterators));
    log.info("Migration complete for adding scheduled jobs in iterators collection.");
  }

  private String loadResourceFileAsString() {
    try {
      return Resources.toString(Resources.getResource(ITERATORS_FILE_PATH), StandardCharsets.UTF_8);
    } catch (IOException e) {
      log.error("Error in loading resource {} as string. Error = {}", ITERATORS_FILE_PATH, e.getMessage(), e);
      throw new UnexpectedException(
          "Error in loading resource " + ITERATORS_FILE_PATH + " as string. Error = " + e.getMessage());
    }
  }
}
