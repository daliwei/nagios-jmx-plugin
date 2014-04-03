/*
 * Copyright 2014 The Billing Project, LLC
 *
 * The Billing Project, LLC licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.killbill.nagios;

/**
 * Nagios status codes and messages.
 */
public enum Status {
    /**
     * Status code NAGIOS OK.
     */
    OK(0, "JMX OK - "),
    /**
     * Status code NAGIOS WARNING.
     */
    WARNING(1, "JMX WARNING - "),
    /**
     * Status code NAGIOS CRITICAL.
     */
    CRITICAL(2, "JMX CRITICAL - "),
    /**
     * Status code NAGIOS UNKNOWN.
     */
    UNKNOWN(3, "JMX UNKNOWN - ");

    private final int exitCode;
    private final String messagePrefix;

    /**
     * @param exitCode      Exit code.
     * @param messagePrefix Message prefix.
     */
    private Status(final int exitCode, final String messagePrefix) {
        this.exitCode = exitCode;
        this.messagePrefix = messagePrefix;
    }

    public int getExitCode() {
        return exitCode;
    }

    public String getMessagePrefix() {
        return messagePrefix;
    }
}