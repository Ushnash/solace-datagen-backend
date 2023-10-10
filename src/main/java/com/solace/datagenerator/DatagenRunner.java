package com.solace.datagenerator;

import java.io.FileNotFoundException;
import java.util.Properties;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import com.solace.messaging.MessagingService;
import com.solace.messaging.config.SolaceProperties.AuthenticationProperties;
import com.solace.messaging.config.SolaceProperties.ServiceProperties;
import com.solace.messaging.config.SolaceProperties.TransportLayerProperties;
import com.solace.messaging.config.profile.ConfigurationProfile;
import com.solace.messaging.publisher.DirectMessagePublisher;
import com.solace.messaging.publisher.OutboundMessage;
import com.solace.messaging.publisher.OutboundMessageBuilder;
import com.solace.messaging.resources.Topic;

import io.confluent.avro.random.generator.Generator;

/**
 * This application publishes randomly generated Avro data to a given PubSub+ topic.
 * 
 * @author ush shukla (Solace Inc.)
 * 
 * <i>Configurable Properties</i>
 * solace.host            - Solace PubSub+ host to connect to in the form (tcp://{host}:{port})
 * solace.msgVpn          - Solace message VPN
 * solace.clientUsername  - Username to use when connecting to the broker
 * solace.clientPassword  - Password to use when connecting to the broker
 * datagen.topic          - Topic to publish to on the broker. Supports schema references (see Readme.md)
 * datagen.key            - Message key to use when publishing. Supports schema references (see Readme.md)
 * datagen.schema         - Reference to a valid Avro schema already packaged with the application.
 * datagen.publishdealyms - how many milliseconds to wait between message publishes.
 */

@Component
public class DatagenRunner implements CommandLineRunner {

	@Autowired
	private ApplicationContext context;

	@Autowired
	private ResourceLoader resourceLoader;

	private static final Logger logger = LoggerFactory.getLogger(DatagenRunner.class);

	@Value("${solace.host}")
	private String host;

	@Value("${solace.msgVpn}")
	private String messageVpn;

	@Value("${solace.clientUsername}")
	private String username;

	@Value("${solace.clientPassword}")
	private String password;

	@Value("${datagen.topic}")
	private String topic;

	@Value("${datagen.key}")
	private String key;

	@Value("schemas/${datagen.schema}")
	private String schemaFilename;

	@Value("${datagen.publishdelayms}")
	private long sleepTimemillis;

	@Override
	public void run(String... args) throws Exception {

		/* basic validations */

		/*
		 * Check if the schema file exists.
		 * If it does, create a new Random Generator
		 */
		Generator generator = null;
		if (resourceLoader.getResource("classpath:" + schemaFilename).exists()) {
			generator = new Generator.Builder()
					.schemaFile(resourceLoader.getResource("classpath:" + schemaFilename).getFile()).build();
			logger.debug(String.format("loaded schema file %s", schemaFilename));
		} else {
			throw new FileNotFoundException(String.format("### Cannot find schema file %s!", schemaFilename));
		}

		// validate the topic structure
		if (!validateTopic(topic, generator.schema())) {
			logger.error("### One or more dynamic topic fields do not exist in schema!");
			shutdownApplication();

		}

		// validate the key
		String newKey = processPlaceholder(key);

		// if this is a structurally valid placeholder but doesn't exist in the schema
		if (!newKey.equals(key) && generator.schema().getField(newKey) == null) {
			logger.error("### Key not found!");
			shutdownApplication();
		}

		/* Passed basic validations, begin connecting to the broker */
		Properties brokerProperties = new Properties();

		brokerProperties.setProperty(TransportLayerProperties.HOST, host);
		brokerProperties.setProperty(ServiceProperties.VPN_NAME, messageVpn);
		brokerProperties.setProperty(ServiceProperties.RECEIVER_DIRECT_SUBSCRIPTION_REAPPLY, "true");
		brokerProperties.setProperty(AuthenticationProperties.SCHEME_BASIC_USER_NAME, username);
		brokerProperties.setProperty(AuthenticationProperties.SCHEME_BASIC_PASSWORD, password);

		final MessagingService messagingService = MessagingService.builder(ConfigurationProfile.V1)
				.fromProperties(brokerProperties) // use the following properties
				.build().connect(); // blocking connect to the broker

		// prepare a message builder & user-properties holder
		OutboundMessageBuilder messageBuilder = messagingService.messageBuilder();
		Properties msgProperties = new Properties();

		// create and start the publisher
		final DirectMessagePublisher publisher = messagingService.createDirectMessagePublisherBuilder()
				.onBackPressureWait(1) // How many messages in the buffer before we cause back-pressure?
				.build().start();

		logger.info("Starting to publish to topic {}", topic);

		// actually start publishing to the broker
		Boolean isShutdown = false;
		while (System.in.available() == 0 && !isShutdown) {

			try {

				// generate a random record using our schema
				GenericRecord record = (GenericRecord) generator.generate();

				// create a message and give it a key
				OutboundMessage message = messageBuilder.build(record.toString());
				msgProperties.put("QUEUE_PARTITION_KEY", record.get(newKey));

				String newTopic = buildTopic(topic, record);
				publisher.publish(message, Topic.of(newTopic), msgProperties);
				Thread.sleep(sleepTimemillis);

			} catch (RuntimeException e) {
				logger.error("### Exception caught during producer.send(): {}", e);
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
	 * Validates that a given placeholder actually exists in the Avro schema
	 */
	private boolean validateTopic(String topic, Schema schema) {

		String[] topicLevels = topic.split("/");

		for (String level : topicLevels) {
			String newLevel = processPlaceholder(level);

			// The parsed placeholder isn't in the schema
			if (!newLevel.equals(level) && schema.getField(newLevel) == null)
				return false;
		}
		return true;
	}

	/*
	 * Retrieves the value associated with a given topic-string placeholder.
	 * 
	 * String topic - The topic being processed. 
	 * GenericRecord record - The randomly generated Avro record from which to get a value
	 */
	private String buildTopic(String topic, GenericRecord record) {

		String[] topicLevels = topic.split("/");

		/*
		 * Iterate over the topic levels. Whenever you find a placeholder, replace it
		 * with the corresponding value from the sample JSON (Avro).
		 */
		int index = 0;
		for (String level : topicLevels) {
			String newLevel = processPlaceholder(level);
			if (!newLevel.equals(level)) {
				topicLevels[index] = record.get(newLevel).toString();
			}
			index++;
		}

		return String.join("/", topicLevels);
	}

	/*
	 * Returns a placeholder without the enclosing '{' and '}'.
	 */
	private String processPlaceholder(String placeholder) {

		if (placeholder.matches("\\{\\w+\\}")) // if this is a valid placeholder
			return placeholder.substring(1, placeholder.length() - 1); // return the value inside the '{' & '}'

		return placeholder;
	}

	// Method for graceful shutdown of the application
	public void shutdownApplication() {

		int exitCode = SpringApplication.exit(context, () -> 0);
		System.exit(exitCode);
	}

}
