# Apiary Kafka Metastore Listener

## Overview

This module contains classes for publishing Hive Metastore events to Kafka.

### Kafka Metastore Listener Configuration

To configure the listener, the following Hadoop configuration can be used:

| Parameter | Required | Default
|:----|:----:|:----:|
| `topic.name`   | Yes | n/a
| `bootstrap.servers` | Yes | n/a
| `client.id` | Yes | n/a
| `acks` | No | "all"
| `retries` | No | 3
| `max.in.flight.requests.per.connection` | No | 1
| `batch.size` | No | 16384
| `linger.ms` | No | 1
| `buffer.memory` | No | 33554432
| `serde.class` | No | com.expediagroup.apiary.extensions.events.metastore.io.jackson.JsonMetaStoreEventSerDe

All configuration parameters must be prefixed with `com.expediagroup.apiary.extensions.events.metastore.kafka.messaging.`. For example, in your `hive-site.xml`:

### MSK Kafka Metastore Listener Configuration
For configuring the Kafka Metastore Listener to publish Hive Metastore events to MSK Kafka, the following Hadoop configuration can be used:

| Parameter | Required | Default
|:----|:--------:|:----:|
| `security.protocol`   |   Yes    | n/a
| `sasl.mechanism` |   Yes    | n/a
| `sasl.jaas.config` |   Yes    | n/a
| `sasl.client.callback.handler.class` |   Yes    | n/a

These parameters also need to be prefixed with `com.expediagroup.apiary.extensions.events.metastore.kafka.messaging.`. For example, in your `hive-site.xml`:
Also consider that this configuration works with the default MSK cluster properties, if you have a custom MSK cluster, you may need to add more configurations.

The default MSK cluster properties as suggested by AWS are:

security.protocol=SASL_SSL  
sasl.mechanism=AWS_MSK_IAM  
sasl.jaas.config=software.amazon.msk.auth.iam.IAMLoginModule required;  
sasl.client.callback.handler.class=software.amazon.msk.auth.iam.IAMClientCallbackHandler  

```xml
<property>
  <name>com.expediagroup.apiary.extensions.events.metastore.kafka.messaging.bootstrap.servers</name>
  <value>...</value>
</property>
```

For more information about these parameters, documentation for Kafka Producer configuration can be found [here](https://kafka.apache.org/documentation/#producerconfigs).

# Legal
This project is available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0.html).

Copyright 2020 Expedia, Inc.
