package dev.dodo.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class AppPropertiesTest {
	static final String PUBLIC_DEMO_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
	static final String PRIVATE_KEY = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=";
	final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	static AppProperties properties(String key, boolean demoMode, String demoUrl, String demoSecret) {
		var second = Duration.ofSeconds(1);
		return new AppProperties("http://psp", second, second, List.of(), key, demoMode, demoUrl, demoSecret, false, second, 1, second, second, second, List.of(second), 1, second, second, List.of(second), DataSize.ofKilobytes(64));
	}

	@Test
	void publicDemoKeyIsRefusedOutsideDemoMode() {
		var violations = validator.validate(properties(PUBLIC_DEMO_KEY, false, null, null));
		assertEquals(1, violations.size());
		assertTrue(violations.iterator().next().getMessage().contains("public demo key"));
		assertTrue(validator.validate(properties(PRIVATE_KEY, false, null, null)).isEmpty());
	}

	@Test
	void demoModeNeedsItsReceiverAddressAndSecret() {
		assertEquals(1, validator.validate(properties(PUBLIC_DEMO_KEY, true, null, null)).size());
		assertTrue(validator.validate(properties(PUBLIC_DEMO_KEY, true, "http://demo-receiver:8090/webhooks", PRIVATE_KEY)).isEmpty());
	}

	@Test
	void encryptionKeyMustBe32Bytes() {
		assertEquals(1, validator.validate(properties("c2hvcnQ=", false, null, null)).size());
	}
}
