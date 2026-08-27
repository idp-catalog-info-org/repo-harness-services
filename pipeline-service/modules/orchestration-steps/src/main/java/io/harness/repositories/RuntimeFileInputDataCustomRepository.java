/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.repositories;

import io.harness.steps.upload.RuntimeFileInputData;

import java.util.List;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

public interface RuntimeFileInputDataCustomRepository {
  /**
   Updates an existing RuntimeFileInputInstance based on the given query and update instructions.
   @param query the query object specifying the selection criteria for the update.
   @param update the update object specifying the fields and values to modify.
   @return the updated RuntimeFileInputInstance after the operation is executed.
   */
  RuntimeFileInputData update(Query query, Update update);

  /**
   * Finds a list of RuntimeFileInputInstance objects that match the given criteria.
   *
   * @param criteria the criteria object used to filter the results.
   * @return a list of RuntimeFileInputInstance objects that match the provided criteria.
   */
  List<RuntimeFileInputData> find(Criteria criteria);

  RuntimeFileInputData upsert(Query query, Update update);

  Long count(Criteria criteria);
}
