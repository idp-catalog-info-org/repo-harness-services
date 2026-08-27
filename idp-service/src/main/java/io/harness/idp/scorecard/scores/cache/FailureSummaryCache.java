/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scores.cache;

import static io.harness.remote.client.NGRestUtils.getGeneralResponse;

import io.harness.clients.IdpAgentAskGenAIRequest;
import io.harness.clients.IdpAgentAskGenAIResponse;
import io.harness.clients.IdpAgentClient;
import io.harness.spec.server.idp.v1.model.EvaluationData;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class FailureSummaryCache implements FailureSummaryService {
  private static final long MAX_CACHE_SIZE = 5000;
  private static final long EXPIRE_AFTER_WRITE_HOURS = 12;
  private static final String CACHE_KEY_SEPARATOR = "#";
  private static final String COLON_SEPARATOR = ":";
  private static final String SPACE_SEPARATOR = " ";
  private static final String CHECK_SUMMARY_PROMPT = """
      Summarize in clear, readable English (1–2 lines) why a scorecard check failed in the Internal Developer Portal.
      The check named '%s' is described as '%s', evaluated using the expression '%s'.
      For this run, the actual values for all variables in the expression are: '%s'.
      Use this information to infer which conditions caused the overall expression to evaluate to false,
      summarize the failure in natural, readable English,
      and briefly indicate how it can be corrected to meet the expected criteria.""";

  private final LoadingCache<String, String> cache;
  private final IdpAgentClient idpAgentClient;

  @Inject
  public FailureSummaryCache(IdpAgentClient idpAgentClient) {
    this.idpAgentClient = idpAgentClient;
    this.cache =
        CacheBuilder.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .expireAfterWrite(EXPIRE_AFTER_WRITE_HOURS, TimeUnit.HOURS)
            .build(CacheLoader.from(key -> {
              // This should never be called directly since we provide a Callable in getOrCompute
              throw new UnsupportedOperationException("Direct cache loading not supported. Use getOrCompute method.");
            }));
  }

  @Override
  public String getOrCompute(String accountId, String checkId, String checkName, String checkDescription,
      String checkExpression, List<EvaluationData> checkEvaluationData) {
    StringBuilder checkEvaluatedValuesBuilder = new StringBuilder();
    Map<String, String> expressionToActualValueMap = new HashMap<>();
    if (checkEvaluationData != null && !checkEvaluationData.isEmpty()) {
      checkEvaluationData.forEach(evaluationData
          -> expressionToActualValueMap.put(evaluationData.getRuleExpression(), evaluationData.getActualValue()));
    }
    expressionToActualValueMap.forEach(
        (key, value)
            -> checkEvaluatedValuesBuilder.append(key).append(COLON_SEPARATOR).append(value).append(SPACE_SEPARATOR));
    String checkEvaluatedValues = checkEvaluatedValuesBuilder.toString();
    String cacheKey = String.join(
        CACHE_KEY_SEPARATOR, accountId != null ? accountId : "", checkId != null ? checkId : "", checkEvaluatedValues);

    try {
      return cache.get(
          cacheKey, () -> generateSummary(checkName, checkDescription, checkExpression, checkEvaluatedValues));
    } catch (ExecutionException e) {
      log.warn("Error computing failure summary for check {} in account {}", checkName, accountId, e);
      return generateSummary(checkName, checkDescription, checkExpression, checkEvaluatedValues);
    }
  }

  private String generateSummary(
      String checkName, String checkDescription, String checkExpression, String evaluatedValues) {
    try {
      IdpAgentAskGenAIRequest request = new IdpAgentAskGenAIRequest(
          String.format(CHECK_SUMMARY_PROMPT, checkName, checkDescription, checkExpression, evaluatedValues));
      IdpAgentAskGenAIResponse response = getGeneralResponse(idpAgentClient.askGenAI(request));
      return response.getAnswer();
    } catch (Exception e) {
      log.warn("Failed to generate failure summary for check {}", checkName, e);
      return "";
    }
  }
}
