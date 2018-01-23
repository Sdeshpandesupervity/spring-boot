/*
 * Copyright 2012-2018 the original author or authors.
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

package org.springframework.boot.autoconfigure.ldap;

import org.junit.After;
import org.junit.Test;

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.ldap.core.ContextSource;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LdapAutoConfiguration}.
 *
 * @author Eddú Meléndez
 */
public class LdapAutoConfigurationTests {

	private AnnotationConfigApplicationContext context;

	@After
	public void close() {
		if (this.context != null) {
			this.context.close();
		}
	}

	@Test
	public void testDefaultUrl() {
		load();
		ContextSource contextSource = this.context.getBean(ContextSource.class);
		String[] urls = (String[]) ReflectionTestUtils.getField(contextSource, "urls");
		assertThat(urls).containsExactly("ldap://localhost:389");
	}

	@Test
	public void testContextSourceSetOneUrl() {
		load("spring.ldap.urls:ldap://localhost:123");
		ContextSource contextSource = this.context.getBean(ContextSource.class);
		String[] urls = (String[]) ReflectionTestUtils.getField(contextSource, "urls");
		assertThat(urls).containsExactly("ldap://localhost:123");
	}

	@Test
	public void testContextSourceSetTwoUrls() {
		load("spring.ldap.urls:ldap://localhost:123,ldap://mycompany:123");
		ContextSource contextSource = this.context.getBean(ContextSource.class);
		LdapProperties ldapProperties = this.context.getBean(LdapProperties.class);
		String[] urls = (String[]) ReflectionTestUtils.getField(contextSource, "urls");
		assertThat(urls).containsExactly("ldap://localhost:123", "ldap://mycompany:123");
		assertThat(ldapProperties.getUrls()).hasSize(2);
	}

	@Test
	public void testContextSourceWithMoreProperties() {
		load("spring.ldap.urls:ldap://localhost:123", "spring.ldap.username:root",
				"spring.ldap.password:root", "spring.ldap.base:cn=SpringDevelopers",
				"spring.ldap.baseEnvironment.java.naming.security"
						+ ".authentication:DIGEST-MD5");
		LdapProperties ldapProperties = this.context.getBean(LdapProperties.class);
		assertThat(ldapProperties.getBaseEnvironment())
				.containsEntry("java.naming.security.authentication", "DIGEST-MD5");
	}

	@Test
	public void testContextSourceWithDefaultAnonymousReadOnly() {
		load("spring.ldap.urls:ldap://localhost:123,ldap://mycompany:123");
		LdapContextSource contextSource = context.getBean(LdapContextSource.class);
		assertThat(contextSource.isAnonymousReadOnly()).isFalse();
	}

	@Test
	public void testContextSourceWithAnonymousReadOnly() {
		load("spring.ldap.urls:ldap://localhost:123,ldap://mycompany:123",
				"spring.ldap.anonymousReadOnly:true");
		LdapContextSource contextSource = context.getBean(LdapContextSource.class);
		assertThat(contextSource.isAnonymousReadOnly()).isTrue();
	}

	private void load(String... properties) {
		this.context = new AnnotationConfigApplicationContext();
		TestPropertyValues.of(properties).applyTo(this.context);
		this.context.register(LdapAutoConfiguration.class,
				PropertyPlaceholderAutoConfiguration.class);
		this.context.refresh();
	}

}
