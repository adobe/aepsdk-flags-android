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

import java.util.Calendar;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Date expression validator that supports expressions like "today+5d", "today-3d".
 *
 * <p>Supported expressions:
 *
 * <ul>
 *   <li>today+Nd - N days from today
 *   <li>today-Nd - N days before today
 * </ul>
 */
public final class DateExpressionValidator implements IExpressionValidator {

    private static final Logger logger = LoggerFactory.getLogger(DateExpressionValidator.class);

    /** Expression parsers for different date expression formats. */
    public enum ExpressionParser {
        /** Parses expressions like "today+5d" or "today-3d". */
        TODAY_OFFSET("^today[+-]\\d+[d]$") {
            @Override
            protected Long parseDate(String expression) {
                char sign = expression.charAt(5);
                int days = Integer.parseInt(expression.substring(6, expression.length() - 1));

                Calendar date = Calendar.getInstance();

                if (sign == '+') {
                    date.add(Calendar.DAY_OF_YEAR, days);
                } else {
                    date.add(Calendar.DAY_OF_YEAR, -days);
                }

                return date.getTimeInMillis();
            }
        };

        private final String expression;
        private final Pattern compiledExpression;

        public String getExpression() {
            return expression;
        }

        public Pattern getCompiledExpression() {
            return compiledExpression;
        }

        ExpressionParser(String expression) {
            this.expression = expression;
            this.compiledExpression = Pattern.compile(expression);
        }

        protected abstract Long parseDate(String expression);
    }

    @Override
    public Object parseExpression(String expression) {
        if (expression == null || expression.isEmpty()) {
            throw new IllegalArgumentException("Expression cannot be null or empty");
        }

        for (ExpressionParser expressionParser : ExpressionParser.values()) {
            if (expressionParser.getCompiledExpression().matcher(expression).matches()) {
                return expressionParser.parseDate(expression);
            }
        }

        throw new IllegalArgumentException(
                "Invalid expression for date: '"
                        + expression
                        + "'. Did not match any valid regex. "
                        + "Supported formats: today+Nd, today-Nd");
    }
}
