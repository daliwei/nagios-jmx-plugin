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

public abstract class Utils {

    private Utils() {
    }

    /**
     * Parse value as clazz.
     *
     * @param clazz Class
     * @param value Value
     * @return Value parsed as Number of type clazz
     * @throws NagiosJmxPluginException If clazz is not supported or value
     *                                  can't be parsed.
     */
    public static Number parseAsNumber(final Class<? extends Number> clazz, final String value) throws NagiosJmxPluginException {
        final Number result;
        try {
            if (Double.class.equals(clazz)) {
                result = Double.valueOf(value);
            } else if (Integer.class.equals(clazz)) {
                result = Integer.valueOf(value);
            } else if (Long.class.equals(clazz)) {
                result = Long.valueOf(value);
            } else if (Short.class.equals(clazz)) {
                result = Short.valueOf(value);
            } else if (Byte.class.equals(clazz)) {
                result = Byte.valueOf(value);
            } else if (Float.class.equals(clazz)) {
                result = Float.valueOf(value);
            } else if (BigInteger.class.equals(clazz)) {
                result = new BigInteger(value);
            } else if (BigDecimal.class.equals(clazz)) {
                result = new BigDecimal(value);
            } else {
                throw new NumberFormatException("Can't handle object type [" + value.getClass().getName() + "]");
            }
        } catch (final NumberFormatException e) {
            throw new NagiosJmxPluginException("Error parsing threshold " + "value [" + value + "]. Expected [" + clazz.getName() + "]", e);
        }

        return result;
    }
}
