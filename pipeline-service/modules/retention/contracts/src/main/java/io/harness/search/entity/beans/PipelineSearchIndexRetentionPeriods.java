/*
 * Copyright 2024 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.search.entity.beans;

import static io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants.PMS_EXECUTION_ALIAS_12_MONTH_POLICY;
import static io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants.PMS_EXECUTION_ALIAS_24_MONTH_POLICY;
import static io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants.PMS_EXECUTION_ALIAS_6_MONTH;
import static io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants.PMS_EXECUTION_ALIAS_6_MONTH_INDEX_TEMPLATE;
import static io.harness.search.entity.beans.PipelineExecutionElasticSearchConstants.PMS_EXECUTION_ALIAS_6_MONTH_POLICY;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;

import lombok.Getter;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_ELASTIC_SEARCH})
@OwnedBy(HarnessTeam.PIPELINE)
@Getter
public enum PipelineSearchIndexRetentionPeriods {
  DEFAULT_RETENTION_6_MONTHS("DEFAULT_RETENTION_6_MONTHS", PMS_EXECUTION_ALIAS_6_MONTH_POLICY,
      PMS_EXECUTION_ALIAS_6_MONTH, PMS_EXECUTION_ALIAS_6_MONTH_INDEX_TEMPLATE),
  ACCOUNT_RETENTION_6_MONTHS("ACCOUNT_RETENTION_6_MONTHS", PMS_EXECUTION_ALIAS_6_MONTH_POLICY,
      "pms-%s-execution-alias-6-month", "pms-%s-execution-alias-6-month-template"),
  ACCOUNT_RETENTION_12_MONTHS("ACCOUNT_RETENTION_12_MONTHS", PMS_EXECUTION_ALIAS_12_MONTH_POLICY,
      "pms-%s-execution-alias-12-month", "pms-%s-execution-alias-12-month-template"),
  ACCOUNT_RETENTION_24_MONTHS("ACCOUNT_RETENTION_24_MONTHS", PMS_EXECUTION_ALIAS_24_MONTH_POLICY,
      "pms-%s-execution-alias-24-month", "pms-%s-execution-alias-24-month-template");

  private final String name;
  private final String policyName;
  private final String indexName;
  private final String indexTemplateName;

  PipelineSearchIndexRetentionPeriods(String name, String policyName, String indexName, String indexTemplateName) {
    this.name = name;
    this.policyName = policyName;
    this.indexName = indexName;
    this.indexTemplateName = indexTemplateName;
  }

  public String getIndexName(String accountIdentifier) {
    return String.format(indexName, accountIdentifier).toLowerCase();
  }

  public String getFirstIndexName(String accountIdentifier) {
    return String.format("%s-000001", getIndexName(accountIdentifier)).toLowerCase();
  }

  public String getIndexTemplateName(String accountIdentifier) {
    return String.format(indexTemplateName, accountIdentifier).toLowerCase();
  }

  public String getIndexPatterns(String accountIdentifier) {
    return String.format("%s-*", getIndexName(accountIdentifier)).toLowerCase();
  }

  public String getIndexName() {
    if (DEFAULT_RETENTION_6_MONTHS.equals(this)) {
      return indexName;
    }
    throw new InvalidRequestException("[ELASTIC_SEARCH]: Account Identifier is required to build the index name");
  }

  public String getIndexTemplateName() {
    if (DEFAULT_RETENTION_6_MONTHS.equals(this)) {
      return indexTemplateName;
    }
    throw new InvalidRequestException(
        "[ELASTIC_SEARCH]: Account Identifier is required to build the index template name");
  }

  public String getIndexPatterns() {
    if (DEFAULT_RETENTION_6_MONTHS.equals(this)) {
      return String.format("%s-*", getIndexName());
    }
    throw new InvalidRequestException("[ELASTIC_SEARCH]: Account Identifier is required to build the index patterns");
  }

  public String getFirstIndexName() {
    if (DEFAULT_RETENTION_6_MONTHS.equals(this)) {
      return String.format("%s-000001", getIndexName());
    }
    throw new InvalidRequestException("[ELASTIC_SEARCH]: Account Identifier is required to build the first index name");
  }
}
