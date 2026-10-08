package dev.dodo.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dodo.common.ErrorResponse;
import dev.dodo.common.Messages;
import dev.dodo.common.PageQuery;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Configuration
@OpenAPIDefinition(
	info = @Info(title = "Invoice and Payment Service", version = "1.0.0", description = Messages.DOC_API),
	servers = @Server(url = "/"),
	security = @SecurityRequirement(name = "apiKey"))
@SecurityScheme(name = "apiKey", type = SecuritySchemeType.HTTP, scheme = "bearer", description = Messages.API_KEY_REQUIRED)
public class ApiDocumentation {
	@Bean
	ModelResolver modelResolver(ObjectMapper mapper) {
		return new ModelResolver(mapper) {
			@Override
			protected boolean applyBeanValidatorAnnotations(Schema property, Annotation[] annotations, Schema parent, boolean applyNotNullAnnotations) {
				boolean changed = super.applyBeanValidatorAnnotations(property, annotations, parent, applyNotNullAnnotations);
				if (!ObjectUtils.isEmpty(annotations) && Arrays.stream(annotations).anyMatch(NotBlank.class::isInstance)) {
					property.setMinLength(1);
					return true;
				}
				return changed;
			}
		};
	}

	@Bean
	OperationCustomizer standardErrors() {
		return (operation, handler) -> {
			var parameters = Arrays.asList(handler.getMethodParameters());
			boolean pathId = parameters.stream().anyMatch(p -> p.hasParameterAnnotation(PathVariable.class));
			boolean body = parameters.stream().anyMatch(p -> p.hasParameterAnnotation(RequestBody.class));
			boolean paged = parameters.stream().anyMatch(p -> p.getParameterType() == PageQuery.class);
			error(operation, "400", Messages.REQUEST_UNREADABLE);
			error(operation, "401", Messages.API_KEY_REQUIRED);
			if (pathId) error(operation, "404", Messages.NOT_FOUND);
			if (body) error(operation, "413", Messages.REQUEST_TOO_LARGE);
			if (body || paged)
				error(operation, "422", Messages.DETAILS_INVALID);
			error(operation, "500", Messages.UNEXPECTED);
			error(operation, "503", Messages.SERVICE_BUSY);
			return operation;
		};
	}

	@Bean
	OpenApiCustomizer errorSchema(ModelResolver resolver) {
		return api -> {
			var converters = new ModelConverters();
			converters.addConverter(resolver);
			converters.readAll(new AnnotatedType(ErrorResponse.class)).forEach(api.getComponents()::addSchemas);
		};
	}

	private static void error(Operation operation, String status, String description) {
		if (operation.getResponses().containsKey(status)) return;
		operation.getResponses().addApiResponse(status, new ApiResponse().description(description).content(new Content().addMediaType("application/json", new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse")))));
	}
}
