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

package com.adobe.marketing.mobile.flags.engine.runtime.expression;

import com.adobe.marketing.mobile.flags.engine.runtime.FieldDataType;
import java.util.HashMap;
import java.util.Map;

/**
 * Expression validator factory that provides appropriate validators based on field data type.
 *
 * <p>This enum maps field data types to their corresponding expression validators. Currently
 * supports DATE and DATE_TIME expressions.
 */
public enum ExpressionValidator {
    DATE(FieldDataType.DATE, new DateExpressionValidator()),
    DATE_TIME(FieldDataType.DATE_TIME, new DateExpressionValidator());

    private static final Map<FieldDataType, ExpressionValidator> expressionValidatorTypeMap =
            new HashMap<>();

    static {
        for (ExpressionValidator expressionValidator : ExpressionValidator.values()) {
            expressionValidatorTypeMap.put(expressionValidator.getDataType(), expressionValidator);
        }
    }

    private final FieldDataType dataType;
    private final IExpressionValidator expressionValidatorInstance;

    ExpressionValidator(FieldDataType dataType, IExpressionValidator expressionValidatorInstance) {
        this.dataType = dataType;
        this.expressionValidatorInstance = expressionValidatorInstance;
    }

    public FieldDataType getDataType() {
        return dataType;
    }

    public IExpressionValidator getExpressionValidatorInstance() {
        return expressionValidatorInstance;
    }

    /**
     * Get the expression validator for a specific field data type.
     *
     * @param dataType Field data type
     * @return ExpressionValidator or null if not supported
     */
    public static ExpressionValidator getExpressionValidator(FieldDataType dataType) {
        return expressionValidatorTypeMap.get(dataType);
    }

    /**
     * Check if expression validation is supported for a data type.
     *
     * @param dataType Field data type
     * @return true if expression validation is supported
     */
    public static boolean supportsExpression(FieldDataType dataType) {
        return expressionValidatorTypeMap.containsKey(dataType);
    }

    /**
     * Parse an expression using the appropriate validator.
     *
     * @param expression Expression string to parse
     * @return Parsed value
     */
    public Object parseExpression(Object expression) {
        return getExpressionValidatorInstance().parseExpression(String.valueOf(expression));
    }
}
