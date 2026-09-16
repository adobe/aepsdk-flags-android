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

/** Empty filter that always returns true. Used when no criteria is specified. */
class EmptyFilter implements IFilter {

    @Override
    public boolean isValid(UserAttributes userAttributes) {
        return true;
    }

    @Override
    public FilterResult validateWithReturnValues(UserAttributes userAttributes) {
        return new FilterResult(true, new UserAttributes());
    }

    @Override
    public String toString() {
        return "EmptyFilter{}";
    }
}
