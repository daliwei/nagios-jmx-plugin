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

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.InvalidKeyException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Set;

public class JmxClient {

    private final String serviceUrl;
    private final String username;
    private final String password;

    private JMXConnector connector = null;
    private MBeanServerConnection connection = null;

    public JmxClient(final String serviceUrl, final String username, final String password) {
        this.serviceUrl = serviceUrl;
        this.username = username;
        this.password = password;
    }

    public void open() throws NagiosJmxPluginException {
        final JMXServiceURL url;
        try {
            url = new JMXServiceURL(serviceUrl);
        } catch (final MalformedURLException e) {
            throw new NagiosJmxPluginException("Malformed service URL [" + serviceUrl + "]", e);
        }

        try {
            openConnection(url, username, password);
        } catch (final IOException e) {
            throw new NagiosJmxPluginException("Error opening connection: " + e.getMessage(), e);
        }
    }

    public void close() throws NagiosJmxPluginException {
        try {
            closeConnection();
        } catch (final IOException e) {
            throw new NagiosJmxPluginException("Error closing JMX connection", e);
        }
    }

    /**
     * Get ObjectName object.
     *
     * @param objectNameString Object name string
     * @return Object name object
     * @throws NagiosJmxPluginException
     */
    public ObjectName getObjectName(final String objectNameString) throws NagiosJmxPluginException {
        ObjectName objectName;
        try {
            objectName = new ObjectName(objectNameString);
        } catch (final MalformedObjectNameException e) {
            throw new NagiosJmxPluginException("Malformed objectName [" + objectNameString + "]", e);
        }

        if (objectName.isPropertyPattern() || objectName.isDomainPattern()) {
            final Set<ObjectInstance> mBeans;
            try {
                mBeans = connection.queryMBeans(objectName, null);
            } catch (final IOException e) {
                throw new NagiosJmxPluginException("Error querying MBeans for [" + objectNameString + "]", e);
            }

            if (mBeans.size() == 0) {
                throw new NagiosJmxPluginException("objectName not found [" + objectNameString + "]");
            } else if (mBeans.size() > 1) {
                throw new NagiosJmxPluginException("Object name not unique: objectName pattern matches " + mBeans.size() + " MBeans");
            } else {
                objectName = mBeans.iterator().next().getObjectName();
            }
        }

        return objectName;
    }

    public Object queryAttribute(final ObjectName objectName, final String attributeName, final String attributeKey) throws NagiosJmxPluginException {
        Object value = null;
        if (attributeName != null) {
            try {
                value = query(objectName, attributeName, attributeKey);
            } catch (final InstanceNotFoundException e) {
                throw new NagiosJmxPluginException("objectName not found [" + objectName + "]", e);
            } catch (final AttributeNotFoundException e) {
                throw new NagiosJmxPluginException("attributeName not found [" + attributeName + "]", e);
            } catch (final InvalidKeyException e) {
                throw new NagiosJmxPluginException("attributeKey not found [" + attributeKey + "]", e);
            } catch (final Exception e) {
                throw new NagiosJmxPluginException("Error querying server: " + e.getMessage(), e);
            }
        }
        return value;
    }


    public Object[] queryAttributes(final ObjectName objectName, final String[] attributeNames, final String[] attributeKeys) throws NagiosJmxPluginException {
        Object[] values = null;
        if (attributeNames != null) {
            try {
                values = query(objectName, attributeNames, attributeKeys);
            } catch (final InstanceNotFoundException e) {
                throw new NagiosJmxPluginException("objectName not found [" + objectName + "]", e);
            } catch (final AttributeNotFoundException e) {
                throw new NagiosJmxPluginException("attributeNames not found [" + join(attributeNames) + "]", e);
            } catch (final InvalidKeyException e) {
                throw new NagiosJmxPluginException("attributeKeys not found [" + join(attributeKeys) + "]", e);
            } catch (final Exception e) {
                throw new NagiosJmxPluginException("Error querying server: " + e.getMessage(), e);
            }
        }
        return values;
    }

    // Joiner would be nice, but to avoid pulling guava for one function...
    private String join(final String[] input) {
        if (input == null) {
            return "";
        }
        final StringBuilder tmp = new StringBuilder();
        boolean first = true;
        for (String s : input) {
            if (!first) {
                tmp.append(",");
            }
            tmp.append(s);
            first = false;
        }
        return tmp.toString();
    }


    public void invokeOperation(final ObjectName objectName, final String operation) throws NagiosJmxPluginException {
        // Invoke operation if defined.
        if (operation != null) {
            try {
                invoke(objectName, operation);
            } catch (final Exception e) {
                throw new NagiosJmxPluginException("Error invoking operation [" + operation + "]: " + e.getMessage(), e);
            }
        }
    }

    public MBeanAttributeInfo[] getAttributes(final ObjectName objectName) throws NagiosJmxPluginException {
        final MBeanInfo mBeanInfo;
        try {
            mBeanInfo = connection.getMBeanInfo(objectName);
            return mBeanInfo.getAttributes();
        } catch (final Exception e) {
            throw new NagiosJmxPluginException("Error getting attributes", e);
        }
    }

    /**
     * Open a connection to a MBean server.
     *
     * @param serviceUrl Service URL,
     *                   e.g. service:jmx:rmi://HOST:PORT/jndi/rmi://HOST:PORT/jmxrmi
     * @param username   Username
     * @param password   Password
     * @throws java.io.IOException
     */
    private void openConnection(final JMXServiceURL serviceUrl,
                                final String username,
                                final String password) throws IOException {
        final HashMap<String, Object> environment = new HashMap<String, Object>();
        // Add environment variable to check for dead connections.
        environment.put("jmx.remote.x.client.connection.check.period", 5000);

        if (username != null && password != null) {
            environment.put(JMXConnector.CREDENTIALS, new String[]{username, password});
        }

        connector = JMXConnectorFactory.connect(serviceUrl, environment);
        connection = connector.getMBeanServerConnection();
    }

    /**
     * Close JMX connection.
     *
     * @throws IOException
     */
    private void closeConnection() throws IOException {
        if (connector != null) {
            connector.close();
        }
    }

    /**
     * Query MBean object.
     *
     * @param objName       ObjectName
     * @param attributeName Attribute name
     * @param attributeKey  Attribute key
     * @return Value.
     * @throws InstanceNotFoundException
     * @throws ReflectionException
     * @throws IOException
     * @throws AttributeNotFoundException
     * @throws MBeanException
     */
    private Object query(final ObjectName objName, final String attributeName, final String attributeKey) throws AttributeNotFoundException, MBeanException,
            ReflectionException, InstanceNotFoundException, IOException {
        final Object value;

        final Object attribute = connection.getAttribute(objName, attributeName);
        if (attribute instanceof CompositeDataSupport) {
            final CompositeDataSupport compositeAttr = (CompositeDataSupport) attribute;
            value = compositeAttr.get(attributeKey);
        } else {
            value = attribute;
        }

        return value;
    }

    /**
     * @param objName
     * @param attributeNames
     * @param attributeKeys
     * @return
     * @throws AttributeNotFoundException
     * @throws MBeanException
     * @throws ReflectionException
     * @throws InstanceNotFoundException
     * @throws IOException
     */
    private Object[] query(final ObjectName objName, final String[] attributeNames, final String[] attributeKeys) throws AttributeNotFoundException, MBeanException,
            ReflectionException, InstanceNotFoundException, IOException {
        final AttributeList attributes = connection.getAttributes(objName, attributeNames);

        // Handle looking up a composite value
        // e.g. -O java.lang:type=MemoryPool,name=Metaspace -A Usage -K used,committed
        if (attributeKeys != null &&
            attributeKeys.length > 0 &&
            attributes.size() == 1 &&
            attributes.get(0) instanceof Attribute &&
            ((Attribute) attributes.get(0)).getValue() instanceof CompositeDataSupport) {
            final CompositeDataSupport compositeAttr = (CompositeDataSupport) ((Attribute) attributes.get(0)).getValue();
            final Object[] result = new Object[attributeKeys.length];
            for (int i = 0; i < attributeKeys.length; i++) {
                result[i] = new Attribute(attributeKeys[i], compositeAttr.get(attributeKeys[i]));
            }

            return result;
        }

        final Object[] result = new Object[attributes.size()];
        for (int i = 0; i < attributes.size(); i++) {
            final Object attribute = attributes.get(i);
            if (attribute instanceof CompositeDataSupport && attributeKeys != null) {
                final CompositeDataSupport compositeAttr = (CompositeDataSupport) attribute;
                result[i] = compositeAttr.get(attributeKeys[i]);
            } else {
                result[i] = attribute;
            }
        }
        return result;
    }

    /**
     * Invoke an operation on MBean.
     *
     * @param objectName    Object name.
     * @param operationName Operation name.
     * @throws InstanceNotFoundException
     * @throws IOException
     * @throws MBeanException
     * @throws ReflectionException
     */
    private void invoke(final ObjectName objectName, final String operationName) throws ReflectionException, MBeanException,
            InstanceNotFoundException, IOException {
        connection.invoke(objectName, operationName, null, null);
    }
}
