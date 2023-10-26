package com.solace.datagenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan(basePackages = "com.solace.datagenerator.configuration")
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

}
