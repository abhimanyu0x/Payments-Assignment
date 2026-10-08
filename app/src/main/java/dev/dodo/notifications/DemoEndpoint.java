package dev.dodo.notifications;

import dev.dodo.configuration.AppProperties;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo-seed", havingValue = "true")
public class DemoEndpoint implements ApplicationRunner {
	static final UUID BUSINESS_ID = UUID.fromString("01a11810-ce4f-76f3-9863-cbf7566684b5");
	private final WebhookEndpointRepository endpoints;
	private final SecretCipher cipher;
	private final AppProperties app;

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (endpoints.existsByBusinessIdAndUrl(BUSINESS_ID, app.webhookUrl())) return;
		endpoints.save(WebhookEndpointEntity.builder().businessId(BUSINESS_ID).url(app.webhookUrl()).secretCiphertext(cipher.encrypt(app.demoWebhookSecretBytes())).build());
	}
}
