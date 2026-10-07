package dev.dodo.mock;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MockApplication {
  public static void main(String[] args) {
    SpringApplication.run(MockApplication.class, args);
  }
}
