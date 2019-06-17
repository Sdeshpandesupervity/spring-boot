/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.boot.autoconfigure.security.oauth2.resource.reactive;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.oauth2.resource.IssuerUriCondition;
import org.springframework.boot.autoconfigure.security.oauth2.resource.KeyValueCondition;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoders;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Configures a {@link ReactiveJwtDecoder} when a JWK Set URI, OpenID Connect Issuer URI
 * or Public Key configuration is available. Also configures a
 * {@link SecurityWebFilterChain} if a {@link ReactiveJwtDecoder} bean is found.
 *
 * @author Madhura Bhave
 * @author Artsiom Yudovin
 * @author Tadaya Tsuyukubo
 */
@Configuration(proxyBeanMethods = false)
class ReactiveOAuth2ResourceServerJwkConfiguration {

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingBean(ReactiveJwtDecoder.class)
	static class JwtConfiguration {

		private final OAuth2ResourceServerProperties.Jwt properties;

		JwtConfiguration(OAuth2ResourceServerProperties properties) {
			this.properties = properties.getJwt();
		}

		@Bean
		@ConditionalOnProperty(name = "spring.security.oauth2.resourceserver.jwt.jwk-set-uri")
		public ReactiveJwtDecoder jwtDecoder(ObjectProvider<OAuth2TokenValidator<Jwt>> jwtValidators) {
			NimbusReactiveJwtDecoder jwtDecoder = new NimbusReactiveJwtDecoder(this.properties.getJwkSetUri());
			setJwtValidatorsIfAvailable(jwtDecoder, jwtValidators);
			return jwtDecoder;
		}

		@Bean
		@Conditional(KeyValueCondition.class)
		public NimbusReactiveJwtDecoder jwtDecoderByPublicKeyValue(
				ObjectProvider<OAuth2TokenValidator<Jwt>> jwtValidators) throws Exception {
			RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
					.generatePublic(new X509EncodedKeySpec(getKeySpec(this.properties.readPublicKey())));
			NimbusReactiveJwtDecoder jwtDecoder = NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
			setJwtValidatorsIfAvailable(jwtDecoder, jwtValidators);
			return jwtDecoder;
		}

		private byte[] getKeySpec(String keyValue) {
			keyValue = keyValue.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "");
			return Base64.getMimeDecoder().decode(keyValue);
		}

		@Bean
		@Conditional(IssuerUriCondition.class)
		public ReactiveJwtDecoder jwtDecoderByIssuerUri(ObjectProvider<OAuth2TokenValidator<Jwt>> jwtValidators) {
			NimbusReactiveJwtDecoder jwtDecoder = (NimbusReactiveJwtDecoder) ReactiveJwtDecoders
					.fromOidcIssuerLocation(this.properties.getIssuerUri());
			setJwtValidatorsIfAvailable(jwtDecoder, jwtValidators);
			return jwtDecoder;
		}

		private void setJwtValidatorsIfAvailable(NimbusReactiveJwtDecoder jwtDecoder,
				ObjectProvider<OAuth2TokenValidator<Jwt>> jwtValidator) {
			List<OAuth2TokenValidator<Jwt>> validators = jwtValidator.orderedStream().collect(Collectors.toList());
			if (!validators.isEmpty()) {
				jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
			}
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingBean(SecurityWebFilterChain.class)
	static class WebSecurityConfiguration {

		@Bean
		@ConditionalOnBean(ReactiveJwtDecoder.class)
		public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
				ReactiveJwtDecoder jwtDecoder) {
			http.authorizeExchange().anyExchange().authenticated().and().oauth2ResourceServer().jwt()
					.jwtDecoder(jwtDecoder);
			return http.build();
		}

	}

}
