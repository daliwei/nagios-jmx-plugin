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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

public class NagiosOutputFormatter {

    private final Status status;
    private final String objectName;
    private final String [] attributeNames;
    private final String [] attributeKeys;
    private final Object [] values;
    private final Unit unit;
    private final Map<String, Object> performanceData;

    private final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    private final PrintStream out = new PrintStream(outStream);

    public NagiosOutputFormatter(final Status status, final String objectName, final String [] attributeNames, final String [] attributeKeys,
                                 final Object [] values, final Unit unit, final Map<String, Object> performanceData) {
        this.status = status;
        this.objectName = objectName;
        this.attributeNames = attributeNames;
        this.attributeKeys = attributeKeys;
        this.values = values;
        this.unit = unit;
        this.performanceData = performanceData;
    }

    @Override
    public String toString() {
        if (outStream.size() == 0) {
            outputStatus();
            //outPerformanceData();
        }
        return outStream.toString();
    }

    private void outputStatus() {
        // e.g. "JMX OK - "
        out.append(status.getMessagePrefix());
        if (values != null && values.length > 0) {
            out.append(" | ");
            boolean first = true;
            for (int i = 0; i < values.length; i++) {
                if (!first) {
                    out.append(";");
                }
                final String attributeKey = attributeKeys != null ? attributeKeys[i] : null;
                //out.append(getLabel(attributeNames[i], attributeKey)).append("=").append(values[i] == null ? "NULL" : values[i].toString());
                if (values[i] != null) {
                    out.append(values[i].toString());
                }
                first = false;
            }
        }
        if (unit != null) {
            out.append(unit.getAbbreviation());
        }
    }

    private void outPerformanceData() {
        out.append(" |");

        for (final String key : performanceData.keySet()) {
            out.append(" ");
            outOnePerformanceData(key, null, performanceData.get(key), null, null, null, null, null);
        }
    }

    private void outOnePerformanceData(final String attributeName, final String attributeKey, final Object value,
                                       final Unit unit, final Object thresholdWarning, final Object thresholdCritical,
                                       final Object min, final Object max) {
        out.append("'").append(getSanitizedLabel(attributeName, attributeKey)).append("'=").append(value == null ? "0" : value.toString());

        if (unit != null) {
            out.append(unit.getAbbreviation());
        }

        out.append(";");
        if (thresholdWarning != null) {
            out.append(thresholdWarning.toString());
        }
        out.append(";");
        if (thresholdCritical != null) {
            out.append(thresholdCritical.toString());
        }
        out.append(";");
        if (min != null) {
            out.append(min.toString());
        }
        out.append(";");
        if (max != null) {
            out.append(max.toString());
        }
    }

    private String getSanitizedLabel(final String attributeName, final String attributeKey) {
        final String label = getLabel(attributeName, attributeKey);
        return label.replace(" ", "_").replace("=", "_").replace(";", "_").replace("'", "_");
    }

    private String getLabel(final String attributeName, final String attributeKey) {
        if (attributeName == null) {
            return objectName;
        } else if (attributeKey == null) {
            return objectName + "." + attributeName;
        } else {
            return objectName + "." + attributeName + "." + attributeKey;
        }
    }
}
