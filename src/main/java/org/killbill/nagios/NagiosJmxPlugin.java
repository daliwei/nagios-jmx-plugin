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

import javax.management.MBeanAttributeInfo;
import javax.management.ObjectName;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.killbill.nagios.NagiosUtils.getStatus;

/**
 * Nagios JMX plugin.
 */
public class NagiosJmxPlugin {

    /**
     * Username system property.
     */
    public static final String PROP_USERNAME = "username";
    /**
     * Password system property.
     */
    public static final String PROP_PASSWORD = "password";
    /**
     * Object name system property.
     */
    public static final String PROP_OBJECT_NAME = "objectName";
    /**
     * Attribute name system property.
     */
    public static final String PROP_ATTRIBUTE_NAME = "attributeName";
    /**
     * Attribute key system property.
     */
    public static final String PROP_ATTRIBUTE_KEY = "attributeKey";
    /**
     * Service URL system property.
     */
    public static final String PROP_SERVICE_URL = "serviceUrl";
    /**
     * Threshold warning level system property.
     * The number format of this property has to correspond to the type of
     * the attribute object.
     */
    public static final String PROP_THRESHOLD_WARNING = "thresholdWarning";
    /**
     * Threshold critical level system property.
     * The number format of this property has to correspond the type of
     * the attribute object.
     */
    public static final String PROP_THRESHOLD_CRITICAL = "thresholdCritical";
    /**
     * Units system property.
     */
    public static final String PROP_UNITS = "units";
    /**
     * Operation to invoke on MBean.
     */
    public static final String PROP_OPERATION = "operation";
    /**
     * Verbose output.
     */
    public static final String PROP_VERBOSE = "verbose";
    /**
     * Help output.
     */
    public static final String PROP_HELP = "help";
    /**
     * Gather performance data.
     */
    public static final String PROP_PERFORMANCE = "performance";
    /**
     * If JMX metric is not existing ignore it instead of returning CRITICAL
     */
    public static final String PROP_IGNORE_MISSING = "ignoreMissing";

    /**
     * Get system properties and execute query
     *
     * @param args Arguments as properties
     * @return Nagios exit code
     * @throws NagiosJmxPluginException
     */
    public int execute(final Properties args) throws NagiosJmxPluginException {
        final String username = args.getProperty(PROP_USERNAME);
        final String password = args.getProperty(PROP_PASSWORD);
        final String objectNameString = args.getProperty(PROP_OBJECT_NAME);
        final String serviceUrl = args.getProperty(PROP_SERVICE_URL);
        final String operation = args.getProperty(PROP_OPERATION);
        final String units = args.getProperty(PROP_UNITS);
        final Boolean withPerformanceData = args.getProperty(PROP_PERFORMANCE) != null ? Boolean.valueOf(args.getProperty(PROP_PERFORMANCE)) : false;
        final Boolean ignoreUnknownMetrics = args.getProperty(PROP_IGNORE_MISSING) != null ? Boolean.valueOf(args.getProperty(PROP_IGNORE_MISSING)) : false;

        final String thresholdWarningList = args.getProperty(PROP_THRESHOLD_WARNING);
        final String[] thresholdWarnings = thresholdWarningList != null ? thresholdWarningList.split("\\s*,\\s*") : null;

        final String thresholdCriticalList = args.getProperty(PROP_THRESHOLD_CRITICAL);
        final String[] thresholdCriticals = thresholdCriticalList != null ? thresholdCriticalList.split("\\s*,\\s*") : null;

        final String attributeKeyList = args.getProperty(PROP_ATTRIBUTE_KEY);
        final String[] attributeKeys = attributeKeyList != null ? attributeKeyList.split("\\s*,\\s*") : null;

        final String attributeNameList = args.getProperty(PROP_ATTRIBUTE_NAME);
        final String[] attributeNames = attributeNameList != null ? attributeNameList.split("\\s*,\\s*") : null;

        if (objectNameString == null || (attributeNames == null && !withPerformanceData) || serviceUrl == null) {
            return Status.CRITICAL.getExitCode();
        }

        if (attributeNames != null &&
            ((thresholdCriticals != null && thresholdCriticals.length != attributeNames.length) ||
             (thresholdWarnings != null && thresholdWarnings.length != thresholdWarnings.length))) {
            return Status.CRITICAL.getExitCode();
        }

        final PrintStream out = System.out;

        final Unit unit;
        if (units != null && Unit.parse(units) == null) {
            throw new NagiosJmxPluginException("Unknown unit [" + units + "]");
        } else {
            unit = Unit.parse(units);
        }


        final JmxClient client = new JmxClient(serviceUrl, username, password);
        try {
            client.open();
            final ObjectName objectName = client.getObjectName(objectNameString);
            // Invoke operation if defined.
            if (operation != null) {
                client.invokeOperation(objectName, operation);
            }


            NagiosOutputFormatter outputFormatter;
            Status status = Status.OK;
            try {
                final Object[] values = client.queryAttributes(objectName, attributeNames, attributeKeys);
                status = getStatus(thresholdWarnings, thresholdCriticals, values);

                final Map<String, Object> performanceData = new HashMap<String, Object>();
                if (withPerformanceData) {
                    for (final MBeanAttributeInfo attribute : client.getAttributes(objectName)) {
                        final Object performanceValue = client.queryAttribute(objectName, attribute.getName(), null);
                        if (performanceValue != null && performanceValue instanceof Number) {
                            performanceData.put(attribute.getName(), (Number) performanceValue);
                        }
                    }
                }
                outputFormatter = new NagiosOutputFormatter(status, objectNameString, attributeNames, attributeKeys,
                        values, unit, performanceData);
            } catch (NagiosJmxPluginException e) {
                if (ignoreUnknownMetrics) {
                    outputFormatter = new NagiosOutputFormatter(status, objectNameString, attributeNames, attributeKeys,
                            null, unit, null);

                } else {
                    throw e;
                }
            }
            out.print(outputFormatter.toString());
            out.println();

            return status.getExitCode();
        } finally {
            client.close();
        }
    }

    /**
     * Main method.
     *
     * @param args Command line arguments
     */
    public static void main(final String[] args) {
        final PrintStream out = System.out;
        final Properties props = parseArguments(args);
        final String verbose = props.getProperty(PROP_VERBOSE);

        int exitCode;
        try {
            final NagiosJmxPlugin plugin = new NagiosJmxPlugin();
            exitCode = plugin.execute(props);
        } catch (final NagiosJmxPluginException e) {
            out.println(Status.CRITICAL.getMessagePrefix() + e.getMessage());
            if (verbose != null) {
                e.printStackTrace(out);
            }
            exitCode = Status.CRITICAL.getExitCode();
        } catch (final Exception e) {
            out.println(Status.UNKNOWN.getMessagePrefix() + e.getMessage());
            if (verbose != null) {
                e.printStackTrace(out);
            }
            exitCode = Status.UNKNOWN.getExitCode();
        }

        System.exit(exitCode);
    }

    /**
     * Parse command line arguments.
     *
     * @param args Command line arguments
     * @return Command line arguments as properties
     */
    private static Properties parseArguments(final String[] args) {
        final Properties props = new Properties();
        int i = 0;
        while (i < args.length) {
            if ("-h".equals(args[i])) {
                props.put(PROP_HELP, "");
            } else if ("-U".equals(args[i])) {
                props.put(PROP_SERVICE_URL, args[++i]);
            } else if ("-O".equals(args[i])) {
                props.put(PROP_OBJECT_NAME, args[++i]);
            } else if ("-A".equals(args[i])) {
                props.put(PROP_ATTRIBUTE_NAME, args[++i]);
            } else if ("-K".equals(args[i])) {
                props.put(PROP_ATTRIBUTE_KEY, args[++i]);
            } else if ("-v".equals(args[i])) {
                props.put(PROP_VERBOSE, "true");
            } else if ("-w".equals(args[i])) {
                props.put(PROP_THRESHOLD_WARNING, args[++i]);
            } else if ("-c".equals(args[i])) {
                props.put(PROP_THRESHOLD_CRITICAL, args[++i]);
            } else if ("--username".equals(args[i])) {
                props.put(PROP_USERNAME, args[++i]);
            } else if ("--password".equals(args[i])) {
                props.put(PROP_PASSWORD, args[++i]);
            } else if ("-u".equals(args[i])) {
                props.put(PROP_UNITS, args[++i]);
            } else if ("-o".equals(args[i])) {
                props.put(PROP_OPERATION, args[++i]);
            } else if ("-P".equals(args[i])) {
                props.put(PROP_PERFORMANCE, "true");
            } else if ("--ignoreMissing".equals(args[i])) {
                props.put(PROP_IGNORE_MISSING, "true");
            }
            i++;
        }
        return props;
    }
}
