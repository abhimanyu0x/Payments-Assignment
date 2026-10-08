package dev.dodo.receiver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class ReceiverApplication {
	public static void main(String[] args) {
		SpringApplication.run(ReceiverApplication.class, args);
	}
}
