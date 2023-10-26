package com.solace.datagenerator.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "datagen")
public class DatageneratorConfiguration {

	private String topic;
	private String key;
	private String schema;
	private long publishdelayms;

	@ConstructorBinding
	public DatageneratorConfiguration(String topic, String key, String schema, long publishdelayms) {

		this.topic = topic;
		this.key = key;
		this.schema = schema;
		this.publishdelayms = publishdelayms;

	}

	public String getTopic() {
		return topic;
	}

	public String getKey() {
		return key;
	}

	public String getSchema() {
		return schema;
	}

	public long getSleepTimemillis() {
		return publishdelayms;
	}

}
