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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import static org.killbill.nagios.Utils.parseAsNumber;

public abstract class NagiosUtils {

    private NagiosUtils() {
    }

    public static Status getStatus(final String thresholdWarning, final String thresholdCritical, final Object value) throws NagiosJmxPluginException {
        final Status status;

        if (value == null) {
            status = Status.CRITICAL;
        } else if (value instanceof Number) {
            final Number numValue = (Number) value;
            if (isOutsideThreshold(numValue, thresholdCritical)) {
                status = Status.CRITICAL;
            } else if (isOutsideThreshold(numValue, thresholdWarning)) {
                status = Status.WARNING;
            } else {
                status = Status.OK;
            }
        } else {
            final String strValue = value.toString();
            if (matchesThreshold(strValue, thresholdCritical)) {
                status = Status.CRITICAL;
            } else if (matchesThreshold(strValue, thresholdWarning)) {
                status = Status.WARNING;
            } else {
                status = Status.OK;
            }
        }

        return status;
    }

    /**
     * Check if value is outside threshold range.
     *
     * @param value     Value, which is either Double, Long, Integer, Short, Byte,
     *                  or Float.
     * @param threshold Threshold range, which must be parseable in same number
     *                  format as value, can be null
     * @return true if value is outside threshold, false otherwise.
     * @throws NagiosJmxPluginException If number format is not parseable.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean isOutsideThreshold(final Number value, final String threshold) throws NagiosJmxPluginException {
        final boolean outsideThreshold;

        if (threshold != null) {
            final Number[] limits = getThresholdLimits(value.getClass(), threshold);
            final Number min = limits[0];
            final Number max = limits[1];
            if (value instanceof Double || value instanceof Float) {
                outsideThreshold =
                        (min != null && value.doubleValue() < min.doubleValue()) ||
                        (max != null && value.doubleValue() > max.doubleValue());
            } else if (value instanceof Long || value instanceof Integer ||
                       value instanceof Short || value instanceof Byte) {
                outsideThreshold =
                        (min != null && value.longValue() < min.longValue()) ||
                        (max != null && value.longValue() > max.longValue());
            } else if (value instanceof BigInteger ||
                       value instanceof BigDecimal) {
                final Comparable compVal = (Comparable) value;
                outsideThreshold =
                        (min != null && compVal.compareTo(min) < 0) ||
                        (max != null && compVal.compareTo(max) > 0);
            } else {
                throw new NumberFormatException("Can't handle object type [" + value.getClass().getName() + "]");
            }
        } else {
            outsideThreshold = false;
        }

        return outsideThreshold;
    }

    /**
     * Get threshold limits.
     * The array returned contains two values, min and max.
     * If value is +/- inifinity, value is set to null.
     *
     * @param clazz     Class threshold gets parsed as.
     * @param threshold Threshold range.
     * @return Array with two elements containing min, max.
     * @throws NagiosJmxPluginException If threshold can't be parsed as
     *                                  clazz or threshold format is not supported.
     * @see <a href="http://nagiosplug.sourceforge.net/developer-guidelines.html#THRESHOLDFORMAT">THRESHOLDFORMAT</a>
     */
    private static Number[] getThresholdLimits(final Class<? extends Number> clazz, final String threshold) throws NagiosJmxPluginException {
        // 10
        final Matcher matcher1 = Pattern.compile("^(\\d+\\.?\\d*)$").matcher(threshold);
        // 10:
        final Matcher matcher2 = Pattern.compile("^(\\d+\\.?\\d*):$").matcher(threshold);
        // ~:10
        final Matcher matcher3 = Pattern.compile("^~:(\\d+\\.?\\d*)$").matcher(threshold);
        // 10:20
        final Matcher matcher4 = Pattern.compile("^(\\d+\\.?\\d*):(\\d+\\.?\\d*)$").matcher(threshold);
        // @10:20
        final Matcher matcher5 = Pattern.compile("^@(\\d+\\.?\\d*):(\\d+\\.?\\d*)$").matcher(threshold);

        final Number[] limits = new Number[2];
        if (matcher1.matches()) {
            limits[0] = parseAsNumber(clazz, "0");
            limits[1] = parseAsNumber(clazz, matcher1.group(1));
        } else if (matcher2.matches()) {
            limits[0] = parseAsNumber(clazz, matcher2.group(1));
            limits[1] = null;
        } else if (matcher3.matches()) {
            limits[0] = null;
            limits[1] = parseAsNumber(clazz, matcher3.group(1));
        } else if (matcher4.matches()) {
            limits[0] = parseAsNumber(clazz, matcher4.group(1));
            limits[1] = parseAsNumber(clazz, matcher4.group(2));
        } else if (matcher5.matches()) {
            limits[0] = parseAsNumber(clazz, matcher5.group(2));
            limits[1] = parseAsNumber(clazz, matcher5.group(1));
        } else {
            throw new NagiosJmxPluginException("Error parsing threshold. " + "Unknown threshold range format [" + threshold + "]");
        }
        return limits;
    }

    /**
     * Check if value matches threshold regular expression.
     * A threshold starting with @ means that the threshold must
     * not match to return true.
     *
     * @param value     Value
     * @param threshold Threshold regular expression
     * @return true if value matches threshold regex, otherwise false.
     * @throws NagiosJmxPluginException If threshold regex is not parseable
     */
    private static boolean matchesThreshold(final String value, final String threshold) throws NagiosJmxPluginException {
        final boolean matchesThreshold;
        try {
            if (threshold == null) {
                matchesThreshold = false;
            } else {
                if (threshold.startsWith("@")) {
                    matchesThreshold = Pattern.matches(threshold.substring(1), value);
                } else {
                    matchesThreshold = !Pattern.matches(threshold, value);
                }
            }
            return matchesThreshold;
        } catch (final PatternSyntaxException e) {
            throw new NagiosJmxPluginException("Error parsing threshold " + "regex [" + threshold + "]", e);
        }
    }
}
