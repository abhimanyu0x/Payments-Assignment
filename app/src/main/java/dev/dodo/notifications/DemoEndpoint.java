package dev.dodo.notifications;

import dev.dodo.configuration.AppProperties;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnWebApplication
@ConditionalOnProperty(name = "app.demo-mode", havingValue = "true")
public class DemoEndpoint implements ApplicationRunner {
	static final UUID BUSINESS_ID = UUID.fromString("01a11810-ce4f-76f3-9863-cbf7566684b5");
	private final WebhookEndpointRepository endpoints;
	private final SecretCipher cipher;
	private final AppProperties app;

	@Override
	public void run(ApplicationArguments args) {
		if (endpoints.existsByBusinessIdAndUrlAndActiveTrue(BUSINESS_ID, app.demoWebhookUrl())) return;
		try {
			endpoints.saveAndFlush(WebhookEndpointEntity.builder().businessId(BUSINESS_ID).url(app.demoWebhookUrl()).secretCiphertext(cipher.encrypt(app.demoWebhookSecretBytes())).build());
		} catch (DataIntegrityViolationException e) {
			log.info("demo_endpoint_already_registered");
		}
	}
}
