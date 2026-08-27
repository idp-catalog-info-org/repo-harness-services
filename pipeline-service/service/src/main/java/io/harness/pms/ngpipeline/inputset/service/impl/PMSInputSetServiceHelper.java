/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.ngpipeline.inputset.service.impl;

import static io.harness.NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS;
import static io.harness.NGResourceFilterConstants.EXACT_MATCH_REGEX;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.EntityNotFoundException;
import io.harness.pms.ngpipeline.inputset.api.utils.InputSetsApiUtils;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity;
import io.harness.pms.ngpipeline.inputset.beans.entity.InputSetEntity.InputSetEntityKeys;
import io.harness.pms.ngpipeline.inputset.mappers.PMSInputSetElementMapper;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.repositories.inputset.PMSInputSetRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Singleton
@Slf4j
@OwnedBy(PIPELINE)
public class PMSInputSetServiceHelper {
  @Inject private PMSInputSetRepository inputSetRepository;
  private static final int INPUT_SET_YAML_VALIDATION_LIMIT = 100;

  public List<InputSetEntity> fetchAllInputSetByFilePathAndRepo(
      String accountIdentifier, String filePath, String repoName) {
    Criteria criteria = buildCriteriaWithFilePathAndRepoName(accountIdentifier, filePath, repoName);
    List<String> fieldsToBeExcluded = List.of(InputSetEntityKeys.yaml);
    Pageable pageable = PageRequest.of(0, INPUT_SET_YAML_VALIDATION_LIMIT);
    List<InputSetEntity> inputSetEntityList =
        inputSetRepository.findAllFromSecondaryDb(criteria, fieldsToBeExcluded, pageable);
    if (EmptyPredicate.isEmpty(inputSetEntityList)) {
      throw new EntityNotFoundException(
          String.format("No InputSet exist with file path: [%s], repo: [%s]", filePath, repoName));
    }
    return inputSetEntityList;
  }

  private Criteria buildCriteriaWithFilePathAndRepoName(String accountIdentifier, String filePath, String repoName) {
    Criteria criteria = new Criteria();
    criteria.and(InputSetEntityKeys.accountId).is(accountIdentifier);
    criteria.and(InputSetEntityKeys.filePath).is(filePath);
    criteria.and(InputSetEntityKeys.repo)
        .regex(String.format(EXACT_MATCH_REGEX, repoName), CASE_INSENSITIVE_MONGO_OPTIONS);
    return criteria;
  }

  public InputSetEntity buildInputSetEntityForForceImport(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String pipelineIdentifier, String yaml, String version, String name,
      String identifier) {
    InputSetEntity inputSetEntity;
    try {
      inputSetEntity = PMSInputSetElementMapper.buildInputSetEntity(
          accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, yaml, version);
    } catch (Exception ex) {
      log.warn("Error while mapping to inputSet entity.", ex);
      // falling back to simplistic approach
      inputSetEntity = PMSInputSetElementMapper.toMinimalInputSetEntity(
          accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, identifier, name, yaml, version);
    }
    return inputSetEntity;
  }

  public Map<String, String> getNameAndIdentifierFromYaml(String yaml, String version) {
    if (HarnessYamlVersion.isV1(version)) {
      return Collections.emptyMap();
    }

    return InputSetsApiUtils.getNameAndIdentifierFromYaml(yaml);
  }
}
