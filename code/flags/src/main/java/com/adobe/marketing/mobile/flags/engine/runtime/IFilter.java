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

/** Interface for filter evaluation. Filters can be leaf filters or composite filter expressions. */
interface IFilter {

    /**
     * Check if user attributes match this filter.
     *
     * @param userAttributes User attributes to validate
     * @return true if filter matches
     */
    boolean isValid(UserAttributes userAttributes);

    /**
     * Validate and return matched attributes.
     *
     * @param userAttributes User attributes to validate
     * @return Validation result with matched attributes
     */
    FilterResult validateWithReturnValues(UserAttributes userAttributes);

    /** Validation result containing both result and matched attributes. */
    class FilterResult {
        private final boolean valid;
        private final UserAttributes matchedAttributes;

        public FilterResult(boolean valid, UserAttributes matchedAttributes) {
            this.valid = valid;
            this.matchedAttributes =
                    matchedAttributes != null ? matchedAttributes : new UserAttributes();
        }

        public boolean isValid() {
            return valid;
        }

        public UserAttributes getMatchedAttributes() {
            return matchedAttributes;
        }
    }
}
