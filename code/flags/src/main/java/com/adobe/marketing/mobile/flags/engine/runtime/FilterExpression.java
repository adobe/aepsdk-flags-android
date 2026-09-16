/*
  Copyright 2026 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.marketing.mobile.flags.engine.runtime;

import com.adobe.marketing.mobile.flags.engine.models.UserAttributes;
import java.util.ArrayList;
import java.util.List;

/** Composite filter expression with logical operators (AND/OR/NEGATE). */
class FilterExpression implements IFilter {

    private FilterOperator operator;
    private final List<IFilter> operands;

    public FilterExpression(FilterOperator operator) {
        this.operator = operator != null ? operator : FilterOperator.NONE;
        this.operands = new ArrayList<>();
    }

    public FilterOperator getOperator() {
        return operator;
    }

    public void setOperator(FilterOperator operator) {
        this.operator = operator;
    }

    public void addOperand(IFilter operand) {
        if (operand != null) {
            operands.add(operand);
        }
    }

    /**
     * Check if this expression can be converted to a single filter. Only possible when operator is
     * NONE and there's exactly one Filter operand.
     *
     * @return {@code true} if this expression wraps a single filter
     */
    public boolean isFilterConvertible() {
        return operator == FilterOperator.NONE
                && operands.size() == 1
                && operands.get(0) instanceof Filter;
    }

    /**
     * Convert to a single filter if possible.
     *
     * @return the underlying filter, or {@code null} if not convertible
     */
    public IFilter toFilter() {
        if (isFilterConvertible()) {
            return operands.get(0);
        }
        return null;
    }

    @Override
    public boolean isValid(UserAttributes userAttributes) {
        return validateWithReturnValues(userAttributes).isValid();
    }

    @Override
    public FilterResult validateWithReturnValues(UserAttributes userAttributes) {
        Boolean isValid = null;
        UserAttributes matchedAttributes = new UserAttributes();

        for (IFilter operand : operands) {
            FilterResult result;

            switch (operator) {
                case AND:
                    if (isValid == null) {
                        isValid = true;
                    }
                    result = operand.validateWithReturnValues(userAttributes);
                    isValid = isValid && result.isValid();
                    mergeAttributes(matchedAttributes, result.getMatchedAttributes());

                    // Short-circuit: if any AND operand fails, return early
                    if (!isValid) {
                        return new FilterResult(false, matchedAttributes);
                    }
                    break;

                case OR:
                    if (isValid == null) {
                        isValid = false;
                    }
                    result = operand.validateWithReturnValues(userAttributes);
                    isValid = isValid || result.isValid();

                    // Short-circuit: if any OR operand succeeds, return early
                    if (isValid) {
                        return result;
                    }
                    break;

                case NEGATE:
                    // Accumulate operands with AND semantics; negate the final result after the
                    // loop
                    if (isValid == null) {
                        isValid = true;
                    }
                    result = operand.validateWithReturnValues(userAttributes);
                    isValid = isValid && result.isValid();
                    mergeAttributes(matchedAttributes, result.getMatchedAttributes());
                    break;

                case NONE:
                default:
                    return operand.validateWithReturnValues(userAttributes);
            }
        }

        if (isValid == null) {
            isValid = true; // Empty expression is valid
        }

        // Apply negation to the accumulated AND result
        if (operator == FilterOperator.NEGATE) {
            isValid = !isValid;
        }

        return new FilterResult(isValid, matchedAttributes);
    }

    private void mergeAttributes(UserAttributes target, UserAttributes source) {
        if (source != null && source.getAttributes() != null) {
            for (var entry : source.getAttributes().entrySet()) {
                for (String value : entry.getValue()) {
                    target.addAttribute(entry.getKey(), value);
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FilterExpression{operator=").append(operator).append(", operands=[");
        for (int i = 0; i < operands.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(operands.get(i));
        }
        sb.append("]}");
        return sb.toString();
    }
}
