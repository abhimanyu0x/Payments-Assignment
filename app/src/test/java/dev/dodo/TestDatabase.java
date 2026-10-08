package dev.dodo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestDatabase {
	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgres() {
		return new PostgreSQLContainer<>(System.getProperty("postgres.image"))
			.withCopyFileToContainer(MountableFile.forHostPath(repositoryRoot().resolve("docker/init.sql")), "/docker-entrypoint-initdb.d/init.sql");
	}

	static Path repositoryRoot() {
		return Stream.iterate(Path.of("").toAbsolutePath(), Path::getParent)
			.takeWhile(Objects::nonNull)
			.filter(dir -> Files.exists(dir.resolve("docker-compose.yml")))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Repository root not found"));
	}
}
