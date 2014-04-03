nagios-jmx-plugin
=================

Fork of the [Syabru Nagios JMX Plugin](http://exchange.nagios.org/directory/Plugins/Java-Applications-and-Servers/Syabru-Nagios-JMX-Plugin/details).

See https://github.com/killbill/killbill/wiki/Nagios-monitoring for how to use it to monitor Kill Bill instances.

Build
-----

`mvn clean install`

The plugin will be available as a self-executable jar at `target/check_jmx_ng`.
