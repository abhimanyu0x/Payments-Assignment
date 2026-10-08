package dev.dodo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class ApiDocumentTest extends IntegrationTest {
	static final String HEADER = "# GENERATED from the application code by ApiDocumentTest (mvn test). Do not edit by hand.\n" + "# Live version: http://localhost:8080/v3/api-docs.yaml and" + " http://localhost:8080/swagger-ui.html\n";

	@Test
	void writesOpenApiDocumentGeneratedFromCode() throws Exception {
		var response = anonymous("/v3/api-docs.yaml");
		assertEquals(200, response.statusCode());
		String document = response.body();
		assertTrue(document.contains("/api/v1/invoices/{id}/pay:"), "pay endpoint documented");
		assertTrue(document.contains("total_amount_cents:"), "snake_case property names");
		assertTrue(document.contains("ErrorResponse:"), "shared error schema");
		assertFalse(document.contains("totalAmountCents"), "no Java field names leak into the API");
		Files.writeString(TestDatabase.repositoryRoot().resolve("openapi.yaml"), HEADER + document);
	}
}
