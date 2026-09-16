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
import java.util.List;

/** Interface for filter validator delegators that handle complex nested filter validation. */
interface FilterValidatorDelegator {

    /**
     * Delegate validation to a nested filter.
     *
     * @param stateValue State value from user attributes
     * @param filterValue Filter value (could be a nested filter definition)
     * @param relationalEquality Relational equality type (may be null)
     * @return true if validation passes
     */
    boolean delegate(Object stateValue, Object filterValue, Object relationalEquality);

    /**
     * Delegate validation with return values.
     *
     * @param stateValue State value from user attributes
     * @param filterValue Filter value
     * @param relationalEquality Relational equality type
     * @param id Filter ID
     * @param attrId Attribute ID
     * @return Pair of (isValid, matchedAttributes)
     */
    IFilter.FilterResult delegateWithReturnValues(
            Object stateValue,
            Object filterValue,
            Object relationalEquality,
            int id,
            String attrId);

    /**
     * Delegate validation returning all matching results.
     *
     * @param stateValue State value from user attributes
     * @param filterValue Filter value
     * @param relationalEquality Relational equality type
     * @param id Filter ID
     * @param attrId Attribute ID
     * @return Pair of (isValid, list of matching user attributes)
     */
    List<UserAttributes> delegateWithAllReturnValues(
            Object stateValue,
            Object filterValue,
            Object relationalEquality,
            int id,
            String attrId);

    /**
     * Preprocess user attributes before validation. Used to add selected attributes to nested
     * states.
     *
     * @param stateValue State value (typically a list of UserAttributes)
     * @param objectToValidate Parent user attributes to extract selected attrs from
     */
    void preprocessUserAttributes(Object stateValue, UserAttributes objectToValidate);
}
