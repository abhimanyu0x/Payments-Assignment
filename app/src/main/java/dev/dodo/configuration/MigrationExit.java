package dev.dodo.configuration;

import org.springframework.boot.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("migrate")
public class MigrationExit implements ApplicationRunner {
  private final ConfigurableApplicationContext context;

  public MigrationExit(ConfigurableApplicationContext context) {
    this.context = context;
  }

  public void run(ApplicationArguments args) {
    SpringApplication.exit(context, () -> 0);
  }
}
