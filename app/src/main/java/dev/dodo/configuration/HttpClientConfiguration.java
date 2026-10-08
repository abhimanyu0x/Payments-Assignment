package dev.dodo.configuration;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfiguration {
	@Bean
	RestClient pspClient(RestClient.Builder builder, ClientHttpRequestFactoryBuilder<?> factory, ClientHttpRequestFactorySettings settings, AppProperties app) {
		return builder.baseUrl(app.pspUrl()).requestFactory(factory.build(settings.withReadTimeout(app.pspTimeout()))).build();
	}

	@Bean
	RestClient webhookClient(RestClient.Builder builder, ClientHttpRequestFactoryBuilder<?> factory, ClientHttpRequestFactorySettings settings, AppProperties app) {
		return builder.requestFactory(factory.build(settings.withReadTimeout(app.webhookTimeout()))).build();
	}
}
