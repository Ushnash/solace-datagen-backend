package com.solace.datagenerator.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties(prefix = "solace")
public class SolaceConfiguration {

	private String host;
	private String messageVpn;
	private String username;
	private String password;

	@ConstructorBinding
	public SolaceConfiguration(String host, String messageVpn, String username, String password) {

		this.host = host;
		this.messageVpn = messageVpn;
		this.username = username;
		this.password = password;

	}

	public String getHost() {
		return host;
	}

	public String getMessageVpn() {
		return messageVpn;
	}

	public String getUsername() {
		return username;
	}

	public String getPassword() {
		return password;
	}

}
