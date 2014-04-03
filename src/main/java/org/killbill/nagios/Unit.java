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
 * Unit enumeration.
 */
public enum Unit {
    /**
     * Unit bytes.
     */
    BYTES("B"),
    /**
     * Unit kilobytes.
     */
    KILOBYTES("KB"),
    /**
     * Unit megabytes.
     */
    MEGABYTES("MB"),
    /**
     * Unit terabytes.
     */
    TERABYTES("TB"),
    /**
     * Unit seconds.
     */
    SECONDS("s"),
    /**
     * Unit microseconds.
     */
    MICROSECONDS("us"),
    /**
     * Unit milliseconds.
     */
    MILLISECONDS("ms"),
    /**
     * Unit counter.
     */
    COUNTER("c");

    private final String abbreviation;

    /**
     * @param abbreviation Abbreviation.
     */
    private Unit(final String abbreviation) {
        this.abbreviation = abbreviation;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    /**
     * Parse abbreviation and return matching unit.
     *
     * @param abbr Abbreviation.
     * @return Matching unit, null if not found.
     */
    public static Unit parse(final String abbr) {
        for (final Unit unit : Unit.values()) {
            if (unit.getAbbreviation().equals(abbr))
                return unit;
        }
        return null;
    }
}