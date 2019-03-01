# Kairos Remote

This plugin used to ship as part of Kairos and was separated out for ease of maintenance.

The remote plugin is for forwarding metric data to a remote Kairos instance.
Metric data is gathered locally on the filesystem where it is compressed and uploaded to the 
remote Kairos on specified intervals.  (see kairos-remote.properties for options)

## Remote Datastore
The remote plugin can be loaded in one of two ways.  The first is as the Kairos datastore:

```properties
kairosdb.service.datastore=org.kairosdb.plugin.remote.RemoteModule
```

This effectively makes the Kairos node write only.  The node will not try to connect to 
Cassandra or load the H2 database.

## Remote Listener
The second way to load the remote plugin is as a data point listener:

```properties
kairosdb.datastore.remote.service.remote=org.kairosdb.plugin.remote.ListenerModule
```

The `ListenerModule` adds a listener to the data point events going into the datastore and 
forwards the events on to a remote Kairos instance.  Effectively letting you fork the data.