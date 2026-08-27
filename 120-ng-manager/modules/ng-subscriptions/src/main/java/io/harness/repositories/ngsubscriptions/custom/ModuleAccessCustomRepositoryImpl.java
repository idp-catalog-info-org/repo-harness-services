/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.ngsubscriptions.custom;

import io.harness.ngsubscriptions.entity.ModuleAccess;
import io.harness.ngsubscriptions.entity.TotalAccountUsers;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.support.PageableExecutionUtils;

@Slf4j
public class ModuleAccessCustomRepositoryImpl implements ModuleAccessCustomRepository {
  private final MongoTemplate mongoTemplate;

  @Inject
  public ModuleAccessCustomRepositoryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Page<ModuleAccess> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<ModuleAccess> moduleAccesses = mongoTemplate.find(query, ModuleAccess.class);
    return PageableExecutionUtils.getPage(moduleAccesses, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), TotalAccountUsers.class));
  }

  @Override
  public ModuleAccess findAndModify(Criteria criteria, Update update) {
    Query query = new Query(criteria);
    return mongoTemplate.findAndModify(
        query, update, FindAndModifyOptions.options().returnNew(true), ModuleAccess.class);
  }
}
