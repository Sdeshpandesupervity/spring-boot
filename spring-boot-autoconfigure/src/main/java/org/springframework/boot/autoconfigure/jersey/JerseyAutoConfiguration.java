/*
 * Copyright 2012-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.autoconfigure.jersey;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;
import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.ServletProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.DispatcherServletAutoConfiguration;
import org.springframework.boot.context.embedded.FilterRegistrationBean;
import org.springframework.boot.context.embedded.RegistrationBean;
import org.springframework.boot.context.embedded.ServletRegistrationBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.filter.RequestContextFilter;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Jersey.
 *
 * @author Dave Syer
 * @author Andy Wilkinson
 * @author Eddú Meléndez
 */
@Configuration
@ConditionalOnClass(name = { "org.glassfish.jersey.server.spring.SpringComponentProvider",
		"javax.servlet.ServletRegistration" })
@ConditionalOnBean(type = "org.glassfish.jersey.server.ResourceConfig")
@ConditionalOnWebApplication
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@AutoConfigureBefore(DispatcherServletAutoConfiguration.class)
@AutoConfigureAfter(JacksonAutoConfiguration.class)
@EnableConfigurationProperties(JerseyProperties.class)
public class JerseyAutoConfiguration implements ServletContextAware {

	private static final Log logger = LogFactory.getLog(JerseyAutoConfiguration.class);

	@Autowired
	private JerseyProperties jersey;

	@Autowired
	private ResourceConfig config;

	@Autowired(required = false)
	private ResourceConfigCustomizer customizer;

	private String path;

	@PostConstruct
	public void path() {
		resolveApplicationPath();
		applyCustomConfig();
	}

	private void applyCustomConfig() {
		if (this.customizer != null) {
			this.customizer.customize(this.config);
		}
	}

	private void resolveApplicationPath() {
		if (StringUtils.hasLength(this.jersey.getApplicationPath())) {
			this.path = parseApplicationPath(this.jersey.getApplicationPath());
		}
		else {
			this.path = findApplicationPath(AnnotationUtils
					.findAnnotation(this.config.getClass(), ApplicationPath.class));
		}
	}

	@Bean
	@ConditionalOnMissingBean
	public FilterRegistrationBean requestContextFilter() {
		FilterRegistrationBean registration = new FilterRegistrationBean();
		registration.setFilter(new RequestContextFilter());
		registration.setOrder(this.jersey.getFilter().getOrder() - 1);
		registration.setName("requestContextFilter");
		return registration;
	}

	@Bean
	@ConditionalOnMissingBean(name = "jerseyFilterRegistration")
	@ConditionalOnProperty(prefix = "spring.jersey", name = "type", havingValue = "filter")
	public FilterRegistrationBean jerseyFilterRegistration() {
		FilterRegistrationBean registration = new FilterRegistrationBean();
		registration.setFilter(new ServletContainer(this.config));
		registration.setUrlPatterns(Arrays.asList(this.path));
		registration.setOrder(this.jersey.getFilter().getOrder());
		registration.addInitParameter(ServletProperties.FILTER_CONTEXT_PATH,
				stripPattern(this.path));
		addInitParameters(registration);
		registration.setName("jerseyFilter");
		registration.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
		return registration;
	}

	private String stripPattern(String path) {
		if (path.endsWith("/*")) {
			path = path.substring(0, path.lastIndexOf("/*"));
		}
		return path;
	}

	@Bean
	@ConditionalOnMissingBean(name = "jerseyServletRegistration")
	@ConditionalOnProperty(prefix = "spring.jersey", name = "type", havingValue = "servlet", matchIfMissing = true)
	public ServletRegistrationBean jerseyServletRegistration() {
		ServletRegistrationBean registration = new ServletRegistrationBean(
				new ServletContainer(this.config), this.path);
		addInitParameters(registration);
		registration.setName(getServletRegistrationName());
		return registration;
	}

	private String getServletRegistrationName() {
		return ClassUtils.getUserClass(this.config.getClass()).getName();
	}

	private void addInitParameters(RegistrationBean registration) {
		for (Entry<String, String> entry : this.jersey.getInit().entrySet()) {
			registration.addInitParameter(entry.getKey(), entry.getValue());
		}
	}

	private static String findApplicationPath(ApplicationPath annotation) {
		// Jersey doesn't like to be the default servlet, so map to /* as a fallback
		if (annotation == null) {
			return "/*";
		}
		return parseApplicationPath(annotation.value());
	}

	private static String parseApplicationPath(String applicationPath) {
		if (!applicationPath.startsWith("/")) {
			applicationPath = "/" + applicationPath;
		}
		return applicationPath.equals("/") ? "/*" : applicationPath + "/*";
	}

	@Override
	public void setServletContext(ServletContext servletContext) {
		String servletRegistrationName = getServletRegistrationName();
		ServletRegistration registration = servletContext
				.getServletRegistration(servletRegistrationName);
		if (registration != null) {
			if (logger.isInfoEnabled()) {
				logger.info("Configuring existing registration for Jersey servlet '"
						+ servletRegistrationName + "'");
			}
			registration.setInitParameters(this.jersey.getInit());
			registration.setInitParameter(
					CommonProperties.METAINF_SERVICES_LOOKUP_DISABLE,
					Boolean.TRUE.toString());
		}
	}

	@Order(Ordered.HIGHEST_PRECEDENCE)
	public static final class JerseyWebApplicationInitializer
			implements WebApplicationInitializer {

		@Override
		public void onStartup(ServletContext servletContext) throws ServletException {
			// We need to switch *off* the Jersey WebApplicationInitializer because it
			// will try and register a ContextLoaderListener which we don't need
			servletContext.setInitParameter("contextConfigLocation", "<NONE>");
		}
	}

	@ConditionalOnClass(JacksonFeature.class)
	@Configuration
	static class ObjectMapperResourceConfigCustomizer {

		@Bean
		public ResourceConfigCustomizer resourceConfigCustomizer() {
			return new ResourceConfigCustomizer() {
				@Override
				public void customize(ResourceConfig config) {
					config.register(JacksonFeature.class);
					config.register(ObjectMapperContextResolver.class);
				}
			};
		}

		@Provider
		static class ObjectMapperContextResolver
				implements ContextResolver<ObjectMapper> {

			@Autowired
			private ObjectMapper objectMapper;

			@Override
			public ObjectMapper getContext(Class<?> type) {
				return this.objectMapper;
			}

		}

	}

}
