package dev.dodo.identity;

import dev.dodo.common.ErrorWriter;
import dev.dodo.common.Messages;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
@ConditionalOnWebApplication
public class SecurityConfiguration {
	public static final String BUSINESS_ATTRIBUTE = "businessId";
	private static final String[] DOCUMENTATION = {"/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui.html", "/swagger-ui/**"};
	private static final String API_POLICY = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'";
	private static final String DOCUMENTATION_POLICY = "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'";
	private static final String PERMISSIONS_POLICY = "accelerometer=(), camera=(), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), payment=(), usb=()";

	@Bean
	@Order(1)
	SecurityFilterChain documentation(HttpSecurity http, ErrorWriter errors) throws Exception {
		return hardened(http.securityMatcher(DOCUMENTATION), errors, DOCUMENTATION_POLICY)
			.authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET, DOCUMENTATION).permitAll().anyRequest().denyAll())
			.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain api(HttpSecurity http, ApiKeyAuthenticationProvider apiKeys, ErrorWriter errors) throws Exception {
		return hardened(http, errors, API_POLICY)
			.authorizeHttpRequests(a -> a
				.requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
				.requestMatchers("/error").permitAll()
				.requestMatchers("/api/**").hasRole(ApiKeyAuthenticationProvider.BUSINESS_ROLE)
				.anyRequest().denyAll())
			.addFilterBefore(apiKeyFilter(apiKeys, errors), UsernamePasswordAuthenticationFilter.class)
			.build();
	}

	@Bean
	HttpFirewall firewall() {
		var firewall = new StrictHttpFirewall();
		firewall.setAllowedHttpMethods(List.of("GET", "HEAD", "POST"));
		return firewall;
	}

	@Bean
	RequestRejectedHandler requestRejected(ErrorWriter errors) {
		return (request, response, exception) -> errors.write(response, 400, "invalid_request", Messages.REQUEST_REJECTED);
	}

	private static AuthenticationFilter apiKeyFilter(ApiKeyAuthenticationProvider apiKeys, ErrorWriter errors) {
		var filter = new AuthenticationFilter(new ProviderManager(apiKeys), new ApiKeyConverter());
		filter.setRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher("/api/**"));
		filter.setSuccessHandler((request, response, authentication) -> request.setAttribute(BUSINESS_ATTRIBUTE, authentication.getPrincipal()));
		filter.setFailureHandler((request, response, exception) -> {
			if (exception instanceof AuthenticationServiceException) errors.write(response, 503, "temporarily_unavailable", Messages.SERVICE_BUSY);
			else unauthorized(errors).commence(request, response, exception);
		});
		return filter;
	}

	private static AuthenticationEntryPoint unauthorized(ErrorWriter errors) {
		return (request, response, exception) -> errors.write(response, 401, "unauthorized", Messages.API_KEY_REQUIRED);
	}

	private static HttpSecurity hardened(HttpSecurity http, ErrorWriter errors, String contentSecurityPolicy) throws Exception {
		return http
			.csrf(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.rememberMe(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.cors(c -> c.configurationSource(request -> new CorsConfiguration()))
			.headers(h -> h
				.contentSecurityPolicy(csp -> csp.policyDirectives(contentSecurityPolicy))
				.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
				.referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
				.httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).preload(true).maxAgeInSeconds(31536000))
				.crossOriginOpenerPolicy(p -> p.policy(CrossOriginOpenerPolicy.SAME_ORIGIN))
				.crossOriginResourcePolicy(p -> p.policy(CrossOriginResourcePolicy.SAME_ORIGIN))
				.addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY)))
			.exceptionHandling(e -> e
				.authenticationEntryPoint(unauthorized(errors))
				.accessDeniedHandler((request, response, exception) -> errors.write(response, 403, "forbidden", Messages.NO_ACCESS)));
	}
}
