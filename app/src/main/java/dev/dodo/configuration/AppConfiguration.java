package dev.dodo.configuration;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class AppConfiguration {
	@Bean
	Jackson2ObjectMapperBuilderCustomizer strictJson(AppProperties app) {
		var limits = StreamReadConstraints.builder().maxDocumentLength(app.maxRequestSize().toBytes()).build();
		return builder -> builder.postConfigurer(mapper -> {
			mapper.getFactory().setStreamReadConstraints(limits);
			mapper.coercionConfigFor(LogicalType.Integer)
				.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
			mapper.coercionConfigFor(LogicalType.Textual)
				.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
		});
	}
}
