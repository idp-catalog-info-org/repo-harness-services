/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.approval.step.servicenow.evaluation;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.ApprovalStepNGException;
import io.harness.logstreaming.NGLogCallback;
import io.harness.servicenow.ServiceNowFieldValueNG;
import io.harness.servicenow.ServiceNowTicketNG;
import io.harness.steps.approval.step.beans.ConditionDTO;
import io.harness.steps.approval.step.beans.CriteriaSpecDTO;
import io.harness.steps.approval.step.beans.JexlCriteriaSpecDTO;
import io.harness.steps.approval.step.beans.KeyValuesCriteriaSpecDTO;
import io.harness.steps.approval.step.beans.Operator;
import io.harness.steps.approval.step.entities.ServiceNowApprovalInstance;
import io.harness.steps.approval.step.evaluation.ConditionEvaluator;
import io.harness.utils.DateTimeUtils;

import software.wings.beans.LogColor;
import software.wings.beans.LogHelper;
import software.wings.beans.LogWeight;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class ServiceNowCriteriaEvaluator {
  public boolean evaluateCriteria(ServiceNowTicketNG ticket, CriteriaSpecDTO criteriaSpec, NGLogCallback logCallback) {
    if (ticket == null || ticket.getFields() == null || EmptyPredicate.isEmpty(ticket.getFields())) {
      throw new ApprovalStepNGException("Failed to fetch ticket. Ticket number might be invalid", true);
    }
    if (criteriaSpec instanceof JexlCriteriaSpecDTO) {
      return evaluateJexlCriteria(ticket, (JexlCriteriaSpecDTO) criteriaSpec, logCallback);
    } else if (criteriaSpec instanceof KeyValuesCriteriaSpecDTO) {
      return evaluateKeyValuesCriteria(ticket, (KeyValuesCriteriaSpecDTO) criteriaSpec, logCallback);
    } else {
      throw new ApprovalStepNGException("Unknown criteria type", true);
    }
  }

  private boolean evaluateJexlCriteria(
      ServiceNowTicketNG ticketNG, JexlCriteriaSpecDTO jexlCriteriaSpec, NGLogCallback logCallback) {
    String expression = jexlCriteriaSpec.getExpression();
    if (StringUtils.isBlank(expression)) {
      throw new ApprovalStepNGException("Expression cannot be blank in jexl criteria", true);
    }

    try {
      ServiceNowExpressionEvaluator serviceNowExpressionEvaluator = new ServiceNowExpressionEvaluator(ticketNG);
      Object result = serviceNowExpressionEvaluator.evaluateExpression(expression);
      if (result instanceof Boolean) {
        logFieldValuesForJexlCriteria(expression, (boolean) result, logCallback);
        return (boolean) result;
      } else {
        throw new ApprovalStepNGException("Non boolean result while evaluating approval condition", true);
      }
    } catch (Exception e) {
      throw new ApprovalStepNGException(
          String.format("Error while evaluating approval condition. expression: %s%n", expression), true, e);
    }
  }

  private void logFieldValuesForJexlCriteria(String expression, boolean result, NGLogCallback logCallback) {
    if (isEmpty(expression)) {
      return;
    }

    if (result) {
      String logMessage = format("Servicenow criteria matched for Jexl expression '%s'", expression);
      logCallback.saveExecutionLog(LogHelper.color(logMessage, LogColor.White, LogWeight.Bold));
    } else {
      String logMessage = format("Servicenow criteria mismatched for Jexl expression '%s'", expression);
      logCallback.saveExecutionLog(LogHelper.color(logMessage, LogColor.White, LogWeight.Bold));
    }
  }

  private boolean evaluateKeyValuesCriteria(
      ServiceNowTicketNG ticket, KeyValuesCriteriaSpecDTO keyValueCriteriaSpec, NGLogCallback logCallback) {
    List<ConditionDTO> conditions = keyValueCriteriaSpec.getConditions();
    if (isEmpty(conditions)) {
      throw new ApprovalStepNGException("Conditions in KeyValues criteria can't be empty", true);
    }

    boolean matchAnyCondition = keyValueCriteriaSpec.isMatchAnyCondition();
    for (ConditionDTO condition : conditions) {
      try {
        Operator operator = condition.getOperator();
        String standardValue = condition.getValue();
        if (!ticket.getFields().containsKey(condition.getKey())) {
          throw new ApprovalStepNGException("Field " + condition.getKey() + " doesn't exist in ticket", true);
        }
        Object ticketValue = ticket.getFields().get(condition.getKey()).getValue();
        Object ticketDisplayValue = ticket.getFields().get(condition.getKey()).getDisplayValue();
        boolean currentResult;
        if (Operator.EQ.equals(operator) || Operator.IN.equals(operator)) {
          currentResult = ConditionEvaluator.evaluate(ticketValue, standardValue, operator)
              || ConditionEvaluator.evaluate(ticketDisplayValue, standardValue, operator);
        } else {
          currentResult = ConditionEvaluator.evaluate(ticketValue, standardValue, operator)
              && ConditionEvaluator.evaluate(ticketDisplayValue, standardValue, operator);
        }

        logFieldValues(condition.getKey(), operator, standardValue, ticketValue, ticketDisplayValue, logCallback);

        if (matchAnyCondition) {
          if (currentResult) {
            String logMessage =
                format("MATCH FOUND (ANY condition mode) for field '%s' succeeded.", condition.getKey());
            logCallback.saveExecutionLog(LogHelper.color(logMessage, LogColor.White, LogWeight.Bold));
            return true;
          }
        } else {
          if (!currentResult) {
            String logMessage = format("MATCH FAILED (ALL conditions mode) for field '%s'.", condition.getKey());
            logCallback.saveExecutionLog(LogHelper.color(logMessage, LogColor.White, LogWeight.Bold));
            return false;
          }
        }
      } catch (Exception e) {
        throw new ApprovalStepNGException(
            String.format("Error while evaluating condition %s %s", condition.toString(), e.getMessage()), true, e);
      }
    }

    String logMessage = matchAnyCondition ? "MATCH FAILED (ANY conditions mode) no conditions matched."
                                          : "MATCH FOUND (ALL conditions mode) all conditions matched.";
    logCallback.saveExecutionLog(LogHelper.color(logMessage, LogColor.White));

    return !matchAnyCondition;
  }

  private void logFieldValues(String fieldKey, Operator operator, String standardValue, Object ticketValue,
      Object ticketDisplayValue, NGLogCallback logCallback) {
    String logMessage = String.format(
        "Evaluating condition for field '%s' using operator '%s' with value '%s'", fieldKey, operator, standardValue);
    logCallback.saveExecutionLog(logMessage);
    logMessage = String.format("Actual values for field '%s' are [Servicenow Value: '%s', Display Value: '%s']",
        fieldKey, ticketValue, ticketDisplayValue);
    logCallback.saveExecutionLog(logMessage);
  }

  public boolean validateWithinChangeWindow(
      ServiceNowTicketNG ticket, ServiceNowApprovalInstance instance, NGLogCallback logCallback) {
    if (ticket == null || ticket.getFields() == null || EmptyPredicate.isEmpty(ticket.getFields())) {
      throw new ApprovalStepNGException("Failed to fetch ticket. Ticket number might be invalid", true);
    }
    if (instance.getChangeWindow() == null) {
      return true;
    }
    log.info("Evaluating change window criteria for instance id - {}", instance.getId());
    logCallback.saveExecutionLog("Approval criteria has been met, evaluating change window criteria...");
    String startField = instance.getChangeWindow().getStartField();
    String endField = instance.getChangeWindow().getEndField();
    if (validateWithinChangeWindowInternal(validateAndGetChangeWindowFields(ticket, startField, "start field"),
            validateAndGetChangeWindowFields(ticket, endField, "end field"), logCallback)) {
      log.info("Change window criteria has been met for instance id - {}", instance.getId());
      logCallback.saveExecutionLog("Change window criteria has been met");
      return true;
    }
    return false;
  }

  private ServiceNowFieldValueNG validateAndGetChangeWindowFields(
      ServiceNowTicketNG ticket, String fieldValue, String fieldName) {
    if (StringUtils.isBlank(fieldValue)) {
      throw new ApprovalStepNGException(String.format("Change window %s can't be empty or blank", fieldName), true);
    }
    if (!ticket.getFields().containsKey(fieldValue)) {
      throw new ApprovalStepNGException("Field " + fieldValue + " doesn't exist in ticket", true);
    }

    ServiceNowFieldValueNG fieldTicketValue = ticket.getFields().get(fieldValue);
    if (StringUtils.isBlank(fieldTicketValue.getValue())) {
      throw new ApprovalStepNGException(
          String.format("Value of change window %s in the ticket can't be blank", fieldName), true);
    }
    return fieldTicketValue;
  }

  private boolean validateWithinChangeWindowInternal(ServiceNowFieldValueNG startTimeFieldTicketValue,
      ServiceNowFieldValueNG endTimeFieldTicketValue, NGLogCallback logCallback) {
    Instant nowInstant = Instant.now();
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    dateFormat.setTimeZone(TimeZone.getTimeZone(DateTimeUtils.GMT_TIMEZONE));
    try {
      // displayValue is in user's snow timezone settings, and the value is in UTC
      Instant startTime = dateFormat.parse(addTimeIfNeeded(startTimeFieldTicketValue.getValue())).toInstant();
      Instant endTime = dateFormat.parse(addTimeIfNeeded(endTimeFieldTicketValue.getValue())).toInstant();

      String startTimeFormatted = String.format("{%s} [%s %s]", startTimeFieldTicketValue.getDisplayValue(),
          startTimeFieldTicketValue.getValue(), DateTimeUtils.GMT_TIMEZONE);
      String endTimeFormatted = String.format("{%s} [%s %s]", endTimeFieldTicketValue.getDisplayValue(),
          endTimeFieldTicketValue.getValue(), DateTimeUtils.GMT_TIMEZONE);
      String currentTimeFormatted =
          String.format("[%s %s]", dateFormat.format(Date.from(nowInstant)), DateTimeUtils.GMT_TIMEZONE);

      log.info(
          "[CHANGE_WINDOW_TIME_LOG]: Start time: {}, End time: {}, Current time: {}", startTime, endTime, nowInstant);
      logCallback.saveExecutionLog(
          String.format("Change Window: %n Start time: %s %n End time: %s", startTimeFormatted, endTimeFormatted));
      logCallback.saveExecutionLog(String.format("Current time is %s", currentTimeFormatted));
      if (endTime.compareTo(startTime) <= 0) {
        throw new ApprovalStepNGException(String.format("Start window time %s must be earlier than end window time %s",
                                              startTimeFormatted, endTimeFormatted),
            true);
      }
      if (endTime.compareTo(nowInstant) < 0) {
        throw new ApprovalStepNGException(String.format("End window time %s must be greater than current time %s",
                                              endTimeFormatted, currentTimeFormatted),
            true);
      }
      return startTime.compareTo(nowInstant) < 0 && endTime.compareTo(nowInstant) > 0;
    } catch (ParseException pe) {
      throw new ApprovalStepNGException(
          String.format("Invalid approval Change window values in ServiceNow : %s", pe.getMessage()), true);
    }
  }

  private static String addTimeIfNeeded(String date) {
    if (date == null || date.contains(" ")) {
      return date;
    }
    return date + " 00:00:00";
  }
}
