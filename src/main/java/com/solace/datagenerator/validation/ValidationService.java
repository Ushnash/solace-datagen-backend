package com.solace.datagenerator.validation;

import org.apache.avro.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

@Service
public class ValidationService {

	@Autowired
	private ResourceLoader loader;

	public boolean schemaExists(String schema) {

		Resource schemaResource = loader.getResource(schema);

		if (schemaResource.exists())
			return true;

		return false;
	}

	/*
	 * checks if the input is a placeholder, enclosed in {...}.
	 */
	public boolean isPlaceholder(String value) {
		if (value.matches("\\{\\w+\\}"))
			return true;

		return false;
	}

	public boolean isSchemaElement(String field, Schema schema) {

		if (schema.getField(field) != null)
			return true;

		return false;
	}

	public boolean isValidTopic(String topic, Schema schema) {
		String[] topicLevels = topic.split("/");

		for (String level : topicLevels) {
			if (isPlaceholder(level) && isSchemaElement(level, schema))
				return true;

		}
		return false;
	}
}
