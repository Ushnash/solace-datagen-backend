# Solace Data Generator

The Solace Data Generator publishes random [Avro](https://avro.apache.org/) payloads to a Solace PubSub+ instance using the [Avro Random Generator](https://github.com/confluentinc/avro-random-generator) utility (ARG).

## Usage

`java -jar solace-data-generator.jar -D...`

## Configurable Properties
 
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


## Schemas
The type of data generated depends on the schema file set with `datagen.schema`. All available schemas are located in `src/main/resources/schemas/`.

## Using Custom Schemas
The utility supports custom Avro schemas provided they are:

1. Syntactically valid.
1. Placed in the `/resources/schemas` folder.


## Using Topics & Topic Placeholders
Topics can be be static, dynamic, or both.

### Static Topics
Static topics are those where the value of each level is a string literal e.g. `solace/sample/topic`

### Placeholders & Dynamic Topics
With dynamic topics, topic levels reference _schema fields_, and the value of a given level is determined at runtime. To reference a schema-field, enclose it in `{...}` .

e.g. Whenever the data generator publishes on `{company}/payroll/{department}`, the `{company}` & `{department}` fields dynamically change based on the randomly generated data.

__Additional Examples__
`solace/sample/topic`
`{company}/payroll/{department`
`{company}/{department}/{employee_name}`

## Keys
Users can apply keys to published messages for the purposes of using [Partitioned Queues](https://docs.solace.com/Messaging/Guaranteed-Msg/Partitioned-Queue-Messaging.htm). Similar to topics, keys can be static or dynamic, following the same conventions detailed above.

