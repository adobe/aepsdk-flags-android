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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cache for reusable filter patches referenced by the PATCH operator. */
class FilterPatchesCache {

    private static final Logger logger = LoggerFactory.getLogger(FilterPatchesCache.class);

    private ConcurrentHashMap<String, IFilter> patchCache;
    private AtomicLong lastModifiedTime;

    private static final FilterPatchesCache INSTANCE = new FilterPatchesCache();

    /**
     * Get the singleton instance.
     *
     * @return FilterPatchesCache instance
     */
    public static FilterPatchesCache getInstance() {
        return INSTANCE;
    }

    private FilterPatchesCache() {
        this.patchCache = new ConcurrentHashMap<>();
        this.lastModifiedTime = new AtomicLong(0L);
    }

    /**
     * Get a filter patch by key.
     *
     * @param key Patch key
     * @return Filter patch or EmptyFilter if not found
     */
    public IFilter getPatch(String key) {
        if (patchCache.containsKey(key)) {
            return patchCache.get(key);
        }
        logger.error("Could not find patch in cache for key: {}", key);
        return new EmptyFilter();
    }

    /**
     * Check if a patch exists.
     *
     * @param key Patch key
     * @return true if patch exists
     */
    public boolean hasPatch(String key) {
        return patchCache.containsKey(key);
    }

    /**
     * Add a filter patch to the cache.
     *
     * @param key Patch key
     * @param filter Filter to cache
     */
    public void addToCache(String key, IFilter filter) {
        patchCache.put(key, filter);
        lastModifiedTime.set(System.currentTimeMillis());
    }

    /**
     * Remove a patch from the cache.
     *
     * @param key Patch key
     */
    public void removeFromCache(String key) {
        patchCache.remove(key);
        lastModifiedTime.set(System.currentTimeMillis());
    }

    /** Clear all patches from the cache. */
    public void refreshCache() {
        patchCache.clear();
        lastModifiedTime.set(System.currentTimeMillis());
    }

    /**
     * Get the last modified time.
     *
     * @return Last modified time
     */
    public AtomicLong getLastModifiedTime() {
        return lastModifiedTime;
    }

    /**
     * Set the last modified time.
     *
     * @param lastModifiedTime Last modified time
     */
    public void setLastModifiedTime(AtomicLong lastModifiedTime) {
        this.lastModifiedTime = lastModifiedTime;
    }

    /**
     * Get the number of patches in the cache.
     *
     * @return Number of patches
     */
    public int size() {
        return patchCache.size();
    }
}
