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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.ConnectException;
import java.util.HashMap;
import java.util.Set;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectInstance;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.InvalidKeyException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

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

    private final HashMap<MBeanServerConnection, JMXConnector> connections =
            new HashMap<MBeanServerConnection, JMXConnector>();

    /**
     * Open a connection to a MBean server.
     *
     * @param serviceUrl Service URL,
     *                   e.g. service:jmx:rmi://HOST:PORT/jndi/rmi://HOST:PORT/jmxrmi
     * @param username   Username
     * @param password   Password
     * @return MBeanServerConnection if succesfull.
     * @throws IOException XX
     */
    public MBeanServerConnection openConnection(final JMXServiceURL serviceUrl,
                                                final String username,
                                                final String password) throws IOException {
        final JMXConnector connector;
        HashMap<String, Object> environment = new HashMap<String, Object>();
        // Add environment variable to check for dead connections.
        environment.put("jmx.remote.x.client.connection.check.period", 5000);
        if (username != null && password != null) {
            environment = new HashMap<String, Object>();
            environment.put(JMXConnector.CREDENTIALS, new String[]{username, password});
            connector = JMXConnectorFactory.connect(serviceUrl, environment);
        } else {
            connector = JMXConnectorFactory.connect(serviceUrl, environment);
        }
        final MBeanServerConnection connection = connector.getMBeanServerConnection();
        connections.put(connection, connector);
        return connection;
    }

    /**
     * Close JMX connection.
     *
     * @param connection Connection.
     * @throws IOException XX.
     */
    public void closeConnection(final MBeanServerConnection connection) throws IOException {
        final JMXConnector connector = connections.remove(connection);
        if (connector != null) {
            connector.close();
        }
    }

    /**
     * Get object name object.
     *
     * @param connection MBean server connection.
     * @param objectName Object name string.
     * @return Object name object.
     * @throws InstanceNotFoundException    If object not found.
     * @throws MalformedObjectNameException If object name is malformed.
     * @throws NagiosJmxPluginException     If object name is not unqiue.
     * @throws IOException                  In case of a communication error.
     */
    public ObjectName getObjectName(final MBeanServerConnection connection,
                                    final String objectName)
            throws InstanceNotFoundException, MalformedObjectNameException,
            NagiosJmxPluginException, IOException {
        ObjectName objName = new ObjectName(objectName);
        if (objName.isPropertyPattern() || objName.isDomainPattern()) {
            final Set<ObjectInstance> mBeans = connection.queryMBeans(objName, null);

            if (mBeans.size() == 0) {
                throw new InstanceNotFoundException();
            } else if (mBeans.size() > 1) {
                throw new NagiosJmxPluginException(
                        "Object name not unique: objectName pattern matches " +
                                mBeans.size() + " MBeans."
                );
            } else {
                objName = mBeans.iterator().next().getObjectName();
            }
        }
        return objName;
    }

    /**
     * Query MBean object.
     *
     * @param connection    MBean server connection.
     * @param objectName    Object name.
     * @param attributeName Attribute name.
     * @param attributeKey  Attribute key.
     * @return Value.
     * @throws InstanceNotFoundException    XX
     * @throws IntrospectionException       XX
     * @throws ReflectionException          XX
     * @throws IOException                  XX
     * @throws AttributeNotFoundException   XX
     * @throws MBeanException               XX
     * @throws MalformedObjectNameException XX
     * @throws NagiosJmxPluginException     XX
     */
    public Object query(final MBeanServerConnection connection, final String objectName,
                        final String attributeName, final String attributeKey)
            throws InstanceNotFoundException, IntrospectionException,
            ReflectionException, IOException, AttributeNotFoundException,
            MBeanException, MalformedObjectNameException,
            NagiosJmxPluginException {
        final Object value;
        final ObjectName objName = getObjectName(connection, objectName);

        final Object attribute = connection.getAttribute(objName, attributeName);
        if (attribute instanceof CompositeDataSupport) {
            final CompositeDataSupport compositeAttr =
                    (CompositeDataSupport) attribute;
            value = compositeAttr.get(attributeKey);
        } else {
            value = attribute;
        }

        return value;
    }

    /**
     * Invoke an operation on MBean.
     *
     * @param connection    MBean server connection.
     * @param objectName    Object name.
     * @param operationName Operation name.
     * @throws InstanceNotFoundException    XX
     * @throws IOException                  XX
     * @throws MalformedObjectNameException XX
     * @throws MBeanException               XX
     * @throws ReflectionException          XX
     * @throws NagiosJmxPluginException     XX
     */
    public void invoke(final MBeanServerConnection connection, final String objectName,
                       final String operationName)
            throws InstanceNotFoundException, IOException,
            MalformedObjectNameException, MBeanException, ReflectionException,
            NagiosJmxPluginException {
        final ObjectName objName = getObjectName(connection, objectName);
        connection.invoke(objName, operationName, null, null);
    }

    /**
     * Get system properties and execute query.
     *
     * @param args Arguments as properties.
     * @return Nagios exit code.
     * @throws NagiosJmxPluginException XX
     */
    public int execute(final Properties args) throws NagiosJmxPluginException {
        final String username = args.getProperty(PROP_USERNAME);
        final String password = args.getProperty(PROP_PASSWORD);
        final String objectName = args.getProperty(PROP_OBJECT_NAME);
        final String attributeName = args.getProperty(PROP_ATTRIBUTE_NAME);
        final String attributeKey = args.getProperty(PROP_ATTRIBUTE_KEY);
        final String serviceUrl = args.getProperty(PROP_SERVICE_URL);
        final String thresholdWarning = args.getProperty(PROP_THRESHOLD_WARNING);
        final String thresholdCritical = args.getProperty(PROP_THRESHOLD_CRITICAL);
        final String operation = args.getProperty(PROP_OPERATION);
        final String units = args.getProperty(PROP_UNITS);
        final String help = args.getProperty(PROP_HELP);

        final PrintStream out = System.out;

        if (help != null) {
            showHelp();
            return Status.OK.getExitCode();
        }

        if (objectName == null || attributeName == null || serviceUrl == null) {
            showUsage();
            return Status.CRITICAL.getExitCode();
        }

        final Unit unit;
        if (units != null && Unit.parse(units) == null) {
            throw new NagiosJmxPluginException("Unknown unit [" + units + "]");
        } else {
            unit = Unit.parse(units);
        }

        final JMXServiceURL url;
        try {
            url = new JMXServiceURL(serviceUrl);
        } catch (final MalformedURLException e) {
            throw new NagiosJmxPluginException("Malformed service URL [" +
                    serviceUrl + "]", e);
        }
        // Connect to MBean server.
        MBeanServerConnection connection = null;
        Object value = null;
        try {
            try {
                connection = openConnection(url, username, password);
            } catch (final ConnectException ce) {
                throw new NagiosJmxPluginException(
                        "Error opening RMI connection: " + ce.getMessage(), ce);
            } catch (final Exception e) {
                throw new NagiosJmxPluginException(
                        "Error opening connection: " + e.getMessage(), e);
            }
            // Query attribute.
            try {
                value = query(connection, objectName, attributeName,
                        attributeKey);
            } catch (final MalformedObjectNameException e) {
                throw new NagiosJmxPluginException(
                        "Malformed objectName [" + objectName + "]", e);
            } catch (final InstanceNotFoundException e) {
                throw new NagiosJmxPluginException(
                        "objectName not found [" + objectName + "]", e);
            } catch (final AttributeNotFoundException e) {
                throw new NagiosJmxPluginException(
                        "attributeName not found [" + attributeName + "]", e);
            } catch (final InvalidKeyException e) {
                throw new NagiosJmxPluginException(
                        "attributeKey not found [" + attributeKey + "]", e);
            } catch (final Exception e) {
                throw new NagiosJmxPluginException(
                        "Error querying server: " + e.getMessage(), e);
            }
            // Invoke operation if defined.
            if (operation != null) {
                try {
                    invoke(connection, objectName, operation);
                } catch (final Exception e) {
                    throw new NagiosJmxPluginException(
                            "Error invoking operation [" + operation + "]: " +
                                    e.getMessage(), e
                    );
                }
            }
        } finally {
            if (connection != null) {
                try {
                    closeConnection(connection);
                } catch (final Exception e) {
                    throw new NagiosJmxPluginException(
                            "Error closing JMX connection", e);
                }
            }
        }
        final int exitCode;
        if (value != null) {
            final Status status;
            if (value instanceof Number) {
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
            outputStatus(out, status, objectName, attributeName, attributeKey,
                    value, unit);
            if (value instanceof Number) {
                outputPerformanceData(out, objectName, attributeName,
                        attributeKey, (Number) value, thresholdWarning,
                        thresholdCritical, unit);
            }
            out.println();
            exitCode = status.getExitCode();
        } else {
            out.print(Status.WARNING.getMessagePrefix());
            out.println("Value not set. JMX query returned null value.");
            exitCode = Status.WARNING.getExitCode();
        }
        return exitCode;
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
     * @see http://nagiosplug.sourceforge.net/developer-guidelines.html#THRESHOLDFORMAT
     */
    Number[] getThresholdLimits(
            final Class<? extends Number> clazz, final String threshold)
            throws NagiosJmxPluginException {
        // 10
        final Matcher matcher1 = Pattern.compile(
                "^(\\d+\\.?\\d*)$").matcher(threshold);
        // 10:
        final Matcher matcher2 = Pattern.compile(
                "^(\\d+\\.?\\d*):$").matcher(threshold);
        // ~:10
        final Matcher matcher3 = Pattern.compile(
                "^~:(\\d+\\.?\\d*)$").matcher(threshold);
        // 10:20
        final Matcher matcher4 = Pattern.compile(
                "^(\\d+\\.?\\d*):(\\d+\\.?\\d*)$").matcher(threshold);
        // @10:20
        final Matcher matcher5 = Pattern.compile(
                "^@(\\d+\\.?\\d*):(\\d+\\.?\\d*)$").matcher(threshold);

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
            throw new NagiosJmxPluginException("Error parsing threshold. " +
                    "Unknown threshold range format [" + threshold + "]");
        }
        return limits;
    }

    /**
     * Parse value as clazz.
     *
     * @param clazz Class.
     * @param value Value.
     * @return Value parsed as Number of type clazz.
     * @throws NagiosJmxPluginException If clazz is not supported or value
     *                                  can't be parsed.
     */
    Number parseAsNumber(final Class<? extends Number> clazz, final String value)
            throws NagiosJmxPluginException {
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
                throw new NumberFormatException("Can't handle object type [" +
                        value.getClass().getName() + "]");
            }
        } catch (final NumberFormatException e) {
            throw new NagiosJmxPluginException("Error parsing threshold " +
                    "value [" + value + "]. Expected [" + clazz.getName() +
                    "]", e);
        }
        return result;
    }

    /**
     * Output status.
     *
     * @param out           Print stream.
     * @param status        Status.
     * @param objectName    Object name.
     * @param attributeName Attribute name.
     * @param attributeKey  Attribute key, or null
     * @param value         Value
     * @param unit          Unit.
     */
    private void outputStatus(final PrintStream out, final Status status, final String objectName,
                              final String attributeName, final String attributeKey, final Object value,
                              final Unit unit) {
        final StringBuilder output = new StringBuilder(status.getMessagePrefix());
        output.append(attributeName);
        if (attributeKey != null) {
            output.append(".").append(attributeKey);
        }
        output.append(" = ").append(value);
        if (unit != null) {
            output.append(unit.getAbbreviation());
        }
        out.print(output.toString());
    }

    /**
     * Get performance data output.
     *
     * @param out               Print stream.
     * @param objectName        Object name.
     * @param attributeName     Attribute name.
     * @param attributeKey      Attribute key, or null
     * @param value             Value
     * @param thresholdWarning  Warning threshold.
     * @param thresholdCritical Critical threshold.
     * @param unit              Unit, null if not defined.
     */
    private void outputPerformanceData(final PrintStream out, final String objectName,
                                       final String attributeName, final String attributeKey, final Number value,
                                       final String thresholdWarning, final String thresholdCritical,
                                       final Unit unit) {
        final StringBuilder output = new StringBuilder();
        output.append(" | '");
        output.append(attributeName);
        if (attributeKey != null) {
            output.append(" ").append(attributeKey);
        }
        output.append("'=").append(value);
        if (unit != null) {
            output.append(unit.getAbbreviation());
        }
        output.append(";");
        if (thresholdWarning != null) {
            output.append(thresholdWarning);
        }
        output.append(";");
        if (thresholdCritical != null) {
            output.append(thresholdCritical);
        }
        output.append(";;");
        out.print(output.toString());
    }

    /**
     * Check if value is outside threshold range.
     *
     * @param value     Value, which is either Double, Long, Integer, Short, Byte,
     *                  or Float.
     * @param threshold Threshold range, which must be parsable in same number
     *                  format as value, can be null
     * @return true if value is outside threshold, false otherwise.
     * @throws NagiosJmxPluginException If number format is not parseable.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean isOutsideThreshold(final Number value, final String threshold)
            throws NagiosJmxPluginException {
        final boolean outsideThreshold;
        if (threshold != null) {
            final Number[] limits = getThresholdLimits(value.getClass(), threshold);
            final Number min = limits[0];
            final Number max = limits[1];
            if (value instanceof Double || value instanceof Float) {
                outsideThreshold =
                        (min != null &&
                                value.doubleValue() < min.doubleValue()) ||
                                (max != null &&
                                        value.doubleValue() > max.doubleValue());
            } else if (value instanceof Long || value instanceof Integer ||
                    value instanceof Short || value instanceof Byte) {
                outsideThreshold =
                        (min != null &&
                                value.longValue() < min.longValue()) ||
                                (max != null &&
                                        value.longValue() > max.longValue());
            } else if (value instanceof BigInteger ||
                    value instanceof BigDecimal) {
                final Comparable compVal = (Comparable) value;
                outsideThreshold =
                        (min != null &&
                                compVal.compareTo(min) < 0) ||
                                (max != null &&
                                        compVal.compareTo(max) > 0);
            } else {
                throw new NumberFormatException("Can't handle object type [" +
                        value.getClass().getName() + "]");
            }
        } else {
            outsideThreshold = false;
        }
        return outsideThreshold;
    }

    /**
     * Check if value matches threshold regular expression.
     * A threshold starting with @ means that the threshold must
     * not match to return true.
     *
     * @param value     Value.
     * @param threshold Threshold regular expression.
     * @return true if value matches threshold regex, otherwise false.
     * @throws NagiosJmxPluginException If threshold regex is not parseable.
     */
    private boolean matchesThreshold(final String value, final String threshold)
            throws NagiosJmxPluginException {
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
            throw new NagiosJmxPluginException("Error parsing threshold " +
                    "regex [" + threshold + "]", e);
        }
    }

    /**
     * Main method.
     *
     * @param args Command line arguments.
     */
    public static void main(final String[] args) {
        final PrintStream out = System.out;
        final NagiosJmxPlugin plugin = new NagiosJmxPlugin();
        int exitCode;
        final Properties props = parseArguments(args);
        final String verbose = props.getProperty(PROP_VERBOSE);
        try {
            exitCode = plugin.execute(props);
        } catch (final NagiosJmxPluginException e) {
            out.println(Status.CRITICAL.getMessagePrefix() + e.getMessage());
            if (verbose != null) {
                e.printStackTrace(System.out);
            }
            exitCode = Status.CRITICAL.getExitCode();
        } catch (final Exception e) {
            out.println(Status.UNKNOWN.getMessagePrefix() + e.getMessage());
            if (verbose != null) {
                e.printStackTrace(System.out);
            }
            exitCode = Status.UNKNOWN.getExitCode();
        }
        System.exit(exitCode);
    }

    /**
     * Show usage.
     *
     * @throws NagiosJmxPluginException XX
     */
    private void showUsage() throws NagiosJmxPluginException {
        outputResource(getClass().getResource("usage.txt"));
    }

    /**
     * Show help.
     *
     * @throws NagiosJmxPluginException XX
     */
    private void showHelp() throws NagiosJmxPluginException {
        outputResource(getClass().getResource("help.txt"));
    }

    /**
     * Output resource.
     *
     * @param url Resource URL.
     * @throws NagiosJmxPluginException XX
     */
    private void outputResource(final URL url) throws NagiosJmxPluginException {
        final PrintStream out = System.out;
        try {
            final Reader r = new InputStreamReader(url.openStream());
            final StringBuilder sbHelp = new StringBuilder();
            final char[] buffer = new char[1024];
            for (int len = r.read(buffer); len != -1; len = r.read(buffer)) {
                sbHelp.append(buffer, 0, len);
            }
            out.println(sbHelp.toString());
        } catch (final IOException e) {
            throw new NagiosJmxPluginException(e);
        }
    }

    /**
     * Parse command line arguments.
     *
     * @param args Command line arguments.
     * @return Command line arguments as properties.
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
            }
            i++;
        }
        return props;
    }
}
