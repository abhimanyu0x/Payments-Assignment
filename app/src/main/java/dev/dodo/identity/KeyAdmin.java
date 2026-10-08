package dev.dodo.identity;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component
@Profile("key-admin")
@RequiredArgsConstructor
public class KeyAdmin implements ApplicationRunner {
	private final ApiKeyService keys;
	private final ConfigurableApplicationContext context;

	@Override
	public void run(ApplicationArguments args) {
		String operation = value(args, "operation");
		UUID business = UUID.fromString(value(args, "business-id"));
		switch (operation) {
			case "create" -> {
				var issued = keys.issue(business);
				System.out.println("New API key (shown once): " + issued.apiKey());
				System.out.println("Key ID: " + issued.id());
			}
			case "revoke" -> System.out.println("Keys revoked: " + (keys.revoke(business, UUID.fromString(value(args, "key-id"))) ? 1 : 0));
			default -> throw new IllegalArgumentException("operation must be create or revoke");
		}
		SpringApplication.exit(context, () -> 0);
	}

	private static String value(ApplicationArguments args, String name) {
		var values = args.getOptionValues(name);
		if (CollectionUtils.isEmpty(values) || values.size() != 1) throw new IllegalArgumentException("Provide --" + name + "=...");
		return values.getFirst();
	}
}
