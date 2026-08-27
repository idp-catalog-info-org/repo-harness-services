/*
 * Copyright 2026 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.backstage.repositories;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.common.CommonUtils.getUserPrincipalFromPrincipal;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity;
import io.harness.idp.backstage.entities.BackstageScaffolderTaskEntity.BackstageScaffolderTasksKeys;
import io.harness.security.dto.UserPrincipal;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import java.util.List;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.support.PageableExecutionUtils;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.IDP)
public class BackstageScaffolderTaskEntityRepositoryCustomImpl
    implements BackstageScaffolderTaskEntityRepositoryCustom {
  private MongoTemplate mongoTemplate;

  @Override
  public Page<BackstageScaffolderTaskEntity> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<BackstageScaffolderTaskEntity> backstageScaffolderTaskEntityList =
        mongoTemplate.find(query, BackstageScaffolderTaskEntity.class);
    return PageableExecutionUtils.getPage(backstageScaffolderTaskEntityList, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), BackstageScaffolderTaskEntity.class));
  }

  @Override
  public Page<BackstageScaffolderTaskEntity> findExecutionHistory(String accountIdentifier, List<String> workflowIds,
      List<String> status, Integer page, Integer limit, String sort, Long start, Long end, String searchTerm,
      boolean executedByMe) {
    Criteria criteria = Criteria.where(BackstageScaffolderTasksKeys.accountIdentifier).is(accountIdentifier);
    if (!isEmpty(workflowIds)) {
      workflowIds = workflowIds.stream().map(workflowId -> workflowId.replace("workflow:", "template:")).toList();
      criteria.and(BackstageScaffolderTasksKeys.entityRef).in(workflowIds);
    }
    if (executedByMe) {
      UserPrincipal userPrincipal = getUserPrincipalFromPrincipal();
      if (userPrincipal != null) {
        String user = userPrincipal.getEmail().split("@")[0].replaceAll("\\+", "plus");
        List<String> matchingUserNames = List.of("user:account/" + user, "user:default/" + user);
        criteria.and(BackstageScaffolderTasksKeys.taskCreatedBy).in(matchingUserNames);
      }
    }
    if (!isEmpty(status)) {
      criteria.and(BackstageScaffolderTasksKeys.status).in(status);
    }
    if (start != null || end != null) {
      Criteria timeCriteria = Criteria.where(BackstageScaffolderTasksKeys.taskCreatedAt);
      if (start != null) {
        timeCriteria.gte(start);
      }
      if (end != null) {
        timeCriteria.lte(end);
      }
      criteria.andOperator(timeCriteria);
    }
    if (!isEmpty(searchTerm)) {
      criteria.and(BackstageScaffolderTasksKeys.name)
          .regex(Pattern.compile(Pattern.quote(searchTerm), Pattern.CASE_INSENSITIVE));
    }
    Pageable pageRequest = isEmpty(sort)
        ? PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, BackstageScaffolderTasksKeys.taskCreatedAt))
        : PageUtils.getPageRequest(page, limit, List.of(sort));

    Query query = new Query(criteria).with(pageRequest);
    List<BackstageScaffolderTaskEntity> results = mongoTemplate.find(query, BackstageScaffolderTaskEntity.class);
    return PageableExecutionUtils.getPage(results, pageRequest,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), BackstageScaffolderTaskEntity.class));
  }
}