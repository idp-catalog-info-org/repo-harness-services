/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static java.lang.String.format;

import io.harness.account.utils.AccountUtils;
import io.harness.migration.beans.NGMigration;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverrideSpecConfig;
import io.harness.ng.core.serviceoverridev2.beans.ServiceOverridesSpec;
import io.harness.persistence.HPersistence;
import io.harness.pms.yaml.YamlUtils;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Slf4j
public class AddServiceOverrideV2YamlV2GenerationMigration implements NGMigration {
  @Inject private HPersistence persistence;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private AccountUtils accountUtils;
  private static final String DEBUG_LOG = "[AddServiceOverrideV2YamlV2GenerationMigration]: ";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting migration to add yaml v2 field in service overrides v2 entities");
      List<String> allNGAccountIds = accountUtils.getAllNGAccountIds();
      for (String accountId : allNGAccountIds) {
        Criteria criteria = Criteria.where(NGServiceOverridesEntityKeys.accountId).is(accountId);
        criteria.and(NGServiceOverridesEntityKeys.spec).exists(true).ne(null);
        Query query = new Query(criteria);
        long totalEntitiesCount = mongoTemplate.count(query, NGServiceOverridesEntity.class);
        if (totalEntitiesCount > 0L) {
          try (Stream<NGServiceOverridesEntity> stream = mongoTemplate.stream(query, NGServiceOverridesEntity.class)) {
            Iterator<NGServiceOverridesEntity> iterator = stream.iterator();
            while (iterator.hasNext()) {
              NGServiceOverridesEntity overridesEntity = iterator.next();
              try {
                ServiceOverridesSpec spec = overridesEntity.getSpec();
                Criteria criteria2 = new Criteria().and(NGServiceOverridesEntityKeys.id).is(overridesEntity.getId());
                Query query2 = new Query(criteria2);
                Update update = new Update();
                if (spec != null) {
                  ServiceOverrideSpecConfig specConfig = ServiceOverrideSpecConfig.builder().spec(spec).build();
                  String updatedYamlV2 = YamlUtils.writeYamlString(specConfig);
                  update.set(NGServiceOverridesEntityKeys.yamlV2, updatedYamlV2);
                  UpdateResult updateResult = mongoTemplate.updateFirst(query2, update, NGServiceOverridesEntity.class);
                  if (updateResult.getModifiedCount() == 0L) {
                    log.error(format(DEBUG_LOG
                            + ("Couldn't update yamlV2 for override with environmentRef: [%s], serviceRef: [%s], "
                                + "projectId: [%s], orgId: [%s]"),
                        overridesEntity.getEnvironmentRef(), overridesEntity.getServiceRef(),
                        overridesEntity.getProjectIdentifier(), overridesEntity.getOrgIdentifier()));
                  }
                }
              } catch (Exception e) {
                log.error(format(DEBUG_LOG
                                  + ("Migration failed for override with environmentRef: [%s], serviceRef: [%s], "
                                      + "projectId: [%s], orgId: [%s]"),
                              overridesEntity.getEnvironmentRef(), overridesEntity.getServiceRef(),
                              overridesEntity.getProjectIdentifier(), overridesEntity.getOrgIdentifier()),
                    e);
              }
            }
          } catch (Exception e) {
            log.error(DEBUG_LOG + "Migration failed for accountId: " + accountId, e);
          }
        }
      }
      log.info(DEBUG_LOG + "Finished migration to add yaml v2 field in service overrides v2 entities");
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Migration failed", e);
    }
  }
}
