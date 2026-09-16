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

package com.adobe.marketing.mobile.flags.engine.exception;

/**
 * Exception thrown during SDK operations after initialization. This exception indicates that a
 * feature evaluation or API call failed.
 */
public class FlagClientException extends Exception {

    /**
     * Creates a new client exception with the specified message.
     *
     * @param message The detail message
     */
    public FlagClientException(String message) {
        super(message);
    }

    /**
     * Creates a new client exception with the specified message and cause.
     *
     * @param message The detail message
     * @param cause The cause of this exception
     */
    public FlagClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
