# Table of Contents

- [Overview](#overview)
- [Usage](#usage)
- [Configuration](#configuration)
- [Topics](#topics)
- [Event Keys](#keys)
- [Using Custom Avro Schemas](#custom-schemas)

# Overview

The Solace Data Generator publishes random [Avro](https://avro.apache.org/) data to topics on a Solace PubSub+ broker. The data is created using the [Avro Random Generator (ARG)](https://github.com/confluentinc/avro-random-generator) library.

The [schema](#custom-schemas) selected at runtime determine the flavor (stores, pizza orders, inventory, stock trades,...) of data that is generated.

## Usage

`java -jar solace-data-generator.jar -D<propertyName>=<value>`.

## Configuration
 
|Property | Description | Example
|--|--|--|
|`solace.host` |Hostname of the PubSub+ broker|`tcps://localhost:8080`
|`solace.msgVpn`|Message VPN to connect to on the broker| `my_vpn`
|`solace.clientUsername`|Username to use when connecting to the broker| solace-cloud-client
|`solace.clientPassword`|Password to connect with| `8nWb6pBnJEAYaVw`
|`datagen.topic`|Topic to publish messages to| `solace/data/credit` OR `solace/cards/{card_num}/{cvv}`
|`datagen.schema`|Schema to use when generating random data| `credit_cards.avro`
|`datagen.key`|The literal or schema-referenced key|`123234342424` OR `{card_num}`
|`datagen.publishdelayms`|How many milliseconds to wait between message publications| `2000`


## Topics
Topics can be be static, dynamic, or both.

### Static Topics
Static topics have predefined values for each level. e.g. `solace/sample/topic` or `order/pizza/cancelled`.

### Dynamic Topics
Dynamic topics contain topic-levels that reference _fields in the deployed schema_ . These values are determined at the time a message is published. To reference a schema-field, enclose it in `{...}`.

#### Example
Let's say we have the following Avro schema that defines a list of possible "stores":

```java
{
        "namespace": "datagen.example",
        "name": "stores",
        "type": "record",
        "fields": [
                {
                  "name": "store_id",
                  "type": "int"
                },
                {
                  "name": "city",
                  "type": "string"
                },
                {
                  "name": "state",
                  "type": "string"
                }
         ],
         "arg.properties": {
           "options": [
                { "store_id": 1,
                  "city": "Raleigh",
                  "state": "NC"
                },
                { "store_id": 2,
                  "city": "Chicago",
                  "state": "IL"
                },
                { "store_id": 3,
                  "city": "Sacramento",
                  "state": "CA"
                },
                { "store_id": 4,
                  "city": "Austin",
                  "state": "TX"
                },
                { "store_id": 5,
                  "city": "Boston",
                  "state": "MA"
                },
                { "store_id": 6,
                  "city": "Atlanta",
                  "state": "GA"
                },
                { "store_id": 7,
                  "city": "Lexington",
                  "state": "SC"
                }
            ]
         }
}
```

If we set `datagen.topic=stores/{store_id}/{city}/{state}` then message would be published onto topics of the form:

`stores/3/Scaramento/CA`

`stores/1/Raleigh/NC`

`stores/7/Lexington/SC`

That is, each placeholder is substituted with a value from the generated payload.


## Keys
Users can apply keys to published events in order to use [Partitioned Queues](https://docs.solace.com/Messaging/Guaranteed-Msg/Partitioned-Queue-Messaging.htm). Similar to topics, keys can be static or dynamic.

e.g. `datagen.key=some_key` or `datagen.key={store_id}`.

### Custom Schemas
To use custom Avro schemas, add them to the `/resources` folder & recompile the utility. Hot-loading of schemas is unsupported.
