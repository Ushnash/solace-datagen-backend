package com.solace.datagenerator;

import java.io.FileNotFoundException;
import java.util.Properties;

import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.solace.datagenerator.configuration.DatageneratorConfiguration;
import com.solace.datagenerator.configuration.SolaceConfiguration;
import com.solace.datagenerator.validation.ValidationService;
import com.solace.messaging.MessagingService;
import com.solace.messaging.config.SolaceProperties.AuthenticationProperties;
import com.solace.messaging.config.SolaceProperties.ServiceProperties;
import com.solace.messaging.config.SolaceProperties.TransportLayerProperties;
import com.solace.messaging.config.profile.ConfigurationProfile;
import com.solace.messaging.publisher.DirectMessagePublisher;
import com.solace.messaging.publisher.OutboundMessage;
import com.solace.messaging.publisher.OutboundMessageBuilder;
import com.solace.messaging.resources.Topic;
import com.solacesystems.jcsmp.XMLMessage;

import io.confluent.avro.random.generator.Generator;

/**
 * This application publishes randomly generated Avro data to a given PubSub+
 * topic.
 * 
 * @author ush shukla (Solace Inc.)
 * 
 *         <i>Configurable Properties</i> solace.host - Solace PubSub+ host to
 *         connect to in the form (tcp://{host}:{port}) solace.msgVpn - Solace
 *         message VPN solace.clientUsername - Username to use when connecting
 *         to the broker solace.clientPassword - Password to use when connecting
 *         to the broker datagen.topic - Topic to publish to on the broker.
 *         Supports schema references (see Readme.md) datagen.key - Message key
 *         to use when publishing. Supports schema references (see Readme.md)
 *         datagen.schema - Reference to a valid Avro schema already packaged
 *         with the application. datagen.publishdealyms - how many milliseconds
 *         to wait between message publishes.
 */

@Component
public class DatagenRunner implements CommandLineRunner {

	private static final String SCHEMA_LOCATION = "classpath:schemas/";
	private static final String PROP_PARTITION_KEY = XMLMessage.MessageUserPropertyConstants.QUEUE_PARTITION_KEY;

	@Autowired
	private ApplicationContext context;

	@Autowired
	private SolaceConfiguration brokerConfig;

	@Autowired
	private DatageneratorConfiguration datagenConfig;

	@Autowired
	private ValidationService validationService;

	@Autowired
	private ResourceLoader loader;

	private Resource schemaResource = null;

	private static final Logger logger = LoggerFactory.getLogger(DatagenRunner.class);

	@Override
	public void run(String... args) throws Exception {

		String schema = SCHEMA_LOCATION + datagenConfig.getSchema();
		String topic = datagenConfig.getTopic();
		String key = datagenConfig.getKey();

		/* basic validations */

		if (!validationService.schemaExists(schema)) {
			logger.error("### Cannot find schema file {}!", schema);
			throw new FileNotFoundException();
		}

		// we found the schema file, load it
		schemaResource = loader.getResource(schema);
		logger.info("loaded schema file {}!", schema);

		// Get an instance of the ARG
		Generator generator = new Generator.Builder()
				.schemaStream(schemaResource.getInputStream())
				.build();

		logger.info("Created an instance of Avro Random Generator with schema {}!", schema);

		// validate the topic
		if (validationService.isValidTopic(topic, generator.schema())) {
			logger.error("### One or more dynamic topic fields do not exist in schema!");
			shutdownApplication();
		}

		// Fail if a key placeholder "looks valid" but doesn't exist in the schema
		if (validationService.isPlaceholder(key)) {
			String tempKey = parsePlaceholder(key);

			if (!validationService.isSchemaElement(tempKey, generator.schema())) {
				logger.error("### Key does not exist in schema!");
				shutdownApplication();
			}
		}

		/* Passed basic validations, begin connecting to the broker */
		Properties brokerProperties = new Properties();
		brokerProperties.setProperty(TransportLayerProperties.HOST, brokerConfig.getHost());
		brokerProperties.setProperty(ServiceProperties.VPN_NAME, brokerConfig.getMessageVpn());
		brokerProperties.setProperty(ServiceProperties.RECEIVER_DIRECT_SUBSCRIPTION_REAPPLY, "true");
		brokerProperties.setProperty(AuthenticationProperties.SCHEME_BASIC_USER_NAME, brokerConfig.getUsername());
		brokerProperties.setProperty(AuthenticationProperties.SCHEME_BASIC_PASSWORD, brokerConfig.getPassword());

		final MessagingService messagingService = MessagingService
				.builder(ConfigurationProfile.V1)
				.fromProperties(brokerProperties)
				.build()
				.connect(); // blocking connect to the broker

		// prepare a message builder & user-properties holder
		OutboundMessageBuilder messageBuilder = messagingService.messageBuilder();
		Properties msgProperties = new Properties();

		// create and start the publisher
		final DirectMessagePublisher publisher = messagingService
				.createDirectMessagePublisherBuilder()
				.onBackPressureWait(1) // How many messages in the buffer before we cause back-pressure?
				.build().start();

		logger.info("Starting to publish to topic {}", topic);

		// actually start publishing to the broker

		GenericRecord record = null;
		Boolean isShutdown = false;
		while (System.in.available() == 0 && !isShutdown) {

			try {

				// generate a random record using our schema
				record = (GenericRecord) generator.generate();

				// if the key is a placeholder, we need to get it's value from the schema
				// otherwise just use the key as is.
				if (validationService.isPlaceholder(key)) {
					String tempKey = parsePlaceholder(key);
					msgProperties.put(PROP_PARTITION_KEY, record.get(tempKey));
				} else {
					msgProperties.put(PROP_PARTITION_KEY, key);
				}

				// create a message
				OutboundMessage message = messageBuilder.build(record.toString());

				String newTopic = buildTopic(topic, record);
				publisher.publish(message, Topic.of(newTopic), msgProperties);
				Thread.sleep(datagenConfig.getSleepTimemillis());

			} catch (RuntimeException e) {
				logger.error("### Exception caught during producer.send. {}", e);
				isShutdown = true;
			} catch (InterruptedException e) {
				// Thread.sleep was interrupted
			}
		}

		// all done, shutdown
		publisher.terminate(500);
		messagingService.disconnect();
	}

	/*
	 * Retrieves the value associated with a given topic-string placeholder.
	 * 
	 * String topic - The topic being processed. GenericRecord record - The randomly
	 * generated Avro record from which to get a value
	 */
	private String buildTopic(String topic, GenericRecord record) {

		String[] topicLevels = topic.split("/");

		/*
		 * Iterate over the topic levels. Whenever you find a placeholder, replace it
		 * with the corresponding value from the sample JSON (Avro).
		 */
		int index = 0;
		for (String level : topicLevels) {
			if (validationService.isPlaceholder(level)) {
				topicLevels[index] = record.get(parsePlaceholder(level)).toString();
			}
			index++;
		}

		return String.join("/", topicLevels);
	}

	/*
	 * Returns a placeholder without the enclosing '{' and '}'. if the input is a
	 * plain string (no {...}) then 'null' is returned
	 */
	private String parsePlaceholder(String placeholder) {
		return placeholder.substring(1, placeholder.length() - 1); // return the value inside the '{' & '}'
	}

	// Method for graceful shutdown of the application
	public void shutdownApplication() {

		int exitCode = SpringApplication.exit(context, () -> 0);
		System.exit(exitCode);
	}

}
