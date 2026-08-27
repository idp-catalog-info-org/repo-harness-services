/*
 * Copyright 2025 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.pipeline.service.yamlConversion;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.beans.FeatureName.PIPE_V0_TO_V1_CONVERSION;
import static io.harness.pms.pipeline.yamlConversion.PipelineYamlConversionEntity.TTL_MONTHS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.goconvert.EntityType;
import io.harness.goconvert.GoConvertServiceClient;
import io.harness.goconvert.proto.ConvertResponse;
import io.harness.pms.pipeline.yamlConversion.PipelineYamlConversionEntity;
import io.harness.pms.pipeline.yamlConversion.PipelineYamlConversionEntity.PipelineYamlConversionEntityKeys;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.repositories.pipeline.yamlConversion.PipelineYamlConversionRepository;
import io.harness.utils.PmsFeatureFlagHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(PIPELINE)
@Slf4j
@Singleton
public class PipelineYamlConversionEntityServiceImpl implements PipelineYamlConversionEntityService {
  @Inject PipelineYamlConversionRepository pipelineYamlConversionRepository;
  @Inject PmsFeatureFlagHelper pmsFeatureFlagHelper;
  @Inject GoConvertServiceClient goConvertServiceClient;
  @Inject @Named("PipelineYamlConversionExecutorService") Executor executor;

  @Override
  public PipelineYamlConversionEntity get(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    Criteria criteria = getFindCriteria(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);
    return pipelineYamlConversionRepository.findOne(criteria);
  }

  @Override
  public String convertV0PipelineYamlToV1(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String pipelineVersion, String v0_pipeline_yaml, boolean shouldRunAsV1) {
    if (pmsFeatureFlagHelper.isEnabled(accountIdentifier, PIPE_V0_TO_V1_CONVERSION)
        && HarnessYamlVersion.V0.equals(pipelineVersion)) {
      if (shouldRunAsV1) {
        return convertAndUpsertYaml(
            accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, v0_pipeline_yaml);
      } else {
        CompletableFuture.runAsync(() -> {
          try {
            convertAndUpsertYaml(
                accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, v0_pipeline_yaml);
            log.info("Async YAML conversion completed for pipeline: {}", pipelineIdentifier);
          } catch (Exception e) {
            log.error("Error during async YAML conversion for pipeline: {}", pipelineIdentifier, e);
          }
        }, executor);
      }
    }
    return v0_pipeline_yaml;
  }

  private String convertAndUpsertYaml(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String v0_pipeline_yaml) {
    ConvertResponse convertResponse = goConvertServiceClient.convert(
        EntityType.PIPELINE, v0_pipeline_yaml, Collections.emptyMap(), Collections.emptyMap());
    String convertedV1Yaml = convertResponse.getYaml();
    if (convertedV1Yaml == null || convertedV1Yaml.isEmpty()) {
      log.error("go-convert returned empty YAML for pipeline: {}", pipelineIdentifier);
      return v0_pipeline_yaml;
    }
    createOrUpdateEntity(
        accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier, v0_pipeline_yaml, convertedV1Yaml);
    return convertedV1Yaml;
  }

  @Override
  public void createOrUpdateEntity(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String v0Yaml, String v1Yaml) {
    Criteria criteria = getFindCriteria(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);
    Update update =
        getBaseUpdateWithFixedFields(accountIdentifier, orgIdentifier, projectIdentifier, pipelineIdentifier);
    update.set(PipelineYamlConversionEntityKeys.originalV0Yaml, v0Yaml);
    update.set(PipelineYamlConversionEntityKeys.convertedV1Yaml, v1Yaml);
    pipelineYamlConversionRepository.upsert(criteria, update);
  }

  private Criteria getFindCriteria(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    return Criteria.where(PipelineYamlConversionEntityKeys.accountIdentifier)
        .is(accountIdentifier)
        .and(PipelineYamlConversionEntityKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(PipelineYamlConversionEntityKeys.projectIdentifier)
        .is(projectIdentifier)
        .and(PipelineYamlConversionEntityKeys.pipelineIdentifier)
        .is(pipelineIdentifier);
  }

  private Update getBaseUpdateWithFixedFields(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String pipelineIdentifier) {
    Update update = new Update();
    long currentTime = System.currentTimeMillis();

    update.setOnInsert(PipelineYamlConversionEntityKeys.accountIdentifier, accountIdentifier);
    update.setOnInsert(PipelineYamlConversionEntityKeys.orgIdentifier, orgIdentifier);
    update.setOnInsert(PipelineYamlConversionEntityKeys.projectIdentifier, projectIdentifier);
    update.setOnInsert(PipelineYamlConversionEntityKeys.pipelineIdentifier, pipelineIdentifier);
    update.setOnInsert(PipelineYamlConversionEntityKeys.validUntil,
        Date.from(OffsetDateTime.now().plusMonths(TTL_MONTHS).toInstant()));
    update.setOnInsert(PipelineYamlConversionEntityKeys.createdAt, currentTime);
    update.set(PipelineYamlConversionEntityKeys.lastUpdatedAt, currentTime);

    // By default, mongodb do not populate this on upsert
    update.setOnInsert("_class", PipelineYamlConversionEntity.class.getAnnotation(TypeAlias.class).value());
    return update;
  }
}
