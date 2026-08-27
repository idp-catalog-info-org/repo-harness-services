/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.ngsubscriptions.custom;

import io.harness.ngsubscriptions.entity.DailyAccountUsers;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

@Slf4j
public class DailyAccountUsersCustomRepositoryImpl implements DailyAccountUsersCustomRepository {
  private final MongoTemplate mongoTemplate;

  @Inject
  public DailyAccountUsersCustomRepositoryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public Page<DailyAccountUsers> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<DailyAccountUsers> dailyAccountUsers = mongoTemplate.find(query, DailyAccountUsers.class);
    return PageableExecutionUtils.getPage(dailyAccountUsers, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), DailyAccountUsers.class));
  }
}
