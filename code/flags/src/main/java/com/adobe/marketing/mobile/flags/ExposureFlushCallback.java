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

package com.adobe.marketing.mobile.flags;

import androidx.annotation.NonNull;
import java.util.List;

/** Transport callback invoked when a batch of exposure events is flushed. */
interface ExposureFlushCallback {

    /**
     * Dispatches a batch of aggregated exposure events.
     *
     * <p>Implementations must dispatch each event independently; a failure for one event must not
     * prevent dispatch of the remaining events in the batch.
     *
     * @param events aggregated exposure events for the current flush cycle; never empty
     */
    void onFlush(@NonNull List<AggregatedExposureEvent> events);
}
