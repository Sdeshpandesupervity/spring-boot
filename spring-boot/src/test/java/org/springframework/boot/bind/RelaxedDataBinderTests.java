/*
 * Copyright 2012-2013 the original author or authors.
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

package org.springframework.boot.bind;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import javax.validation.constraints.NotNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.NotWritablePropertyException;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.DataBinder;
import org.springframework.validation.FieldError;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests for {@link RelaxedDataBinder}.
 * 
 * @author Dave Syer
 */
public class RelaxedDataBinderTests {

	@Rule
	public ExpectedException expected = ExpectedException.none();

	private ConversionService conversionService;

	@Test
	public void testBindString() throws Exception {
		VanillaTarget target = new VanillaTarget();
		bind(target, "foo: bar");
		assertEquals("bar", target.getFoo());
	}

	@Test
	public void testBindStringWithPrefix() throws Exception {
		VanillaTarget target = new VanillaTarget();
		bind(target, "test.foo: bar", "test");
		assertEquals("bar", target.getFoo());
	}

	@Test
	public void testBindFromEnvironmentStyleWithPrefix() throws Exception {
		VanillaTarget target = new VanillaTarget();
		bind(target, "TEST_FOO: bar", "test");
		assertEquals("bar", target.getFoo());
	}

	@Test
	public void testBindFromEnvironmentStyleWithNestedPrefix() throws Exception {
		VanillaTarget target = new VanillaTarget();
		bind(target, "TEST_IT_FOO: bar", "test.it");
		assertEquals("bar", target.getFoo());
	}

	@Test
	public void testBindCapitals() throws Exception {
		VanillaTarget target = new VanillaTarget();
		bind(target, "FOO: bar");
		assertEquals("bar", target.getFoo());
	}

	@Test
	public void testBindUnderscoreInActualPropertyName() throws Exception {
		VanillaTarget target = new VanillaTarget();
		bind(target, "foo-bar: bar");
		assertEquals("bar", target.getFoo_bar());
	}

	@Test
	public void testBindHyphen() throws Exception {
		VanillaTarget target = new VanillaTarget();
		bind(target, "foo-baz: bar");
		assertEquals("bar", target.getFooBaz());
	}

	@Test
	public void testBindCamelCase() throws Exception {
		VanillaTarget target = new VanillaTarget();
		bind(target, "foo-baz: bar");
		assertEquals("bar", target.getFooBaz());
	}

	@Test
	public void testBindNumber() throws Exception {
		VanillaTarget target = new VanillaTarget();
		bind(target, "foo: bar\n" + "value: 123");
		assertEquals(123, target.getValue());
	}

	@Test
	public void testSimpleValidation() throws Exception {
		ValidatedTarget target = new ValidatedTarget();
		BindingResult result = bind(target, "");
		assertEquals(1, result.getErrorCount());
	}

	@Test
	public void testRequiredFieldsValidation() throws Exception {
		TargetWithValidatedMap target = new TargetWithValidatedMap();
		BindingResult result = bind(target, "info[foo]: bar");
		assertEquals(2, result.getErrorCount());
		for (FieldError error : result.getFieldErrors()) {
			System.err.println(new StaticMessageSource().getMessage(error,
					Locale.getDefault()));
		}
	}

	@Test
	public void testAllowedFields() throws Exception {
		VanillaTarget target = new VanillaTarget();
		RelaxedDataBinder binder = getBinder(target, null);
		binder.setAllowedFields("foo");
		binder.setIgnoreUnknownFields(false);
		BindingResult result = bind(binder, target, "foo: bar\n" + "value: 123\n"
				+ "bar: spam");
		assertEquals(0, target.getValue());
		assertEquals("bar", target.getFoo());
		assertEquals(0, result.getErrorCount());
	}

	@Test
	public void testDisallowedFields() throws Exception {
		VanillaTarget target = new VanillaTarget();
		RelaxedDataBinder binder = getBinder(target, null);
		// Disallowed fields are not unknown...
		binder.setDisallowedFields("foo", "bar");
		binder.setIgnoreUnknownFields(false);
		BindingResult result = bind(binder, target, "foo: bar\n" + "value: 123\n"
				+ "bar: spam");
		assertEquals(123, target.getValue());
		assertEquals(null, target.getFoo());
		assertEquals(0, result.getErrorCount());
	}

	@Test
	public void testBindNested() throws Exception {
		TargetWithNestedObject target = new TargetWithNestedObject();
		bind(target, "nested.foo: bar\n" + "nested.value: 123");
		assertEquals(123, target.getNested().getValue());
	}

	@Test
	public void testBindNestedList() throws Exception {
		TargetWithNestedList target = new TargetWithNestedList();
		bind(target, "nested[0]: bar\nnested[1]: foo");
		assertEquals("[bar, foo]", target.getNested().toString());
	}

	@Test
	public void testBindNestedListCommaDelimitedOnly() throws Exception {
		TargetWithNestedList target = new TargetWithNestedList();
		this.conversionService = new DefaultConversionService();
		bind(target, "nested: bar,foo");
		assertEquals("[bar, foo]", target.getNested().toString());
	}

	@Test
	public void testBindNestedSetCommaDelimitedOnly() throws Exception {
		TargetWithNestedSet target = new TargetWithNestedSet();
		this.conversionService = new DefaultConversionService();
		bind(target, "nested: bar,foo");
		assertEquals("[bar, foo]", target.getNested().toString());
	}

	@Test(expected = NotWritablePropertyException.class)
	public void testBindNestedReadOnlyListCommaSeparated() throws Exception {
		TargetWithReadOnlyNestedList target = new TargetWithReadOnlyNestedList();
		this.conversionService = new DefaultConversionService();
		bind(target, "nested: bar,foo");
		assertEquals("[bar, foo]", target.getNested().toString());
	}

	@Test
	public void testBindNestedReadOnlyListIndexed() throws Exception {
		TargetWithReadOnlyNestedList target = new TargetWithReadOnlyNestedList();
		this.conversionService = new DefaultConversionService();
		bind(target, "nested[0]: bar\nnested[1]:foo");
		assertEquals("[bar, foo]", target.getNested().toString());
	}

	@Test
	public void testBindNestedReadOnlyCollectionIndexed() throws Exception {
		TargetWithReadOnlyNestedCollection target = new TargetWithReadOnlyNestedCollection();
		this.conversionService = new DefaultConversionService();
		bind(target, "nested[0]: bar\nnested[1]:foo");
		assertEquals("[bar, foo]", target.getNested().toString());
	}

	@Test
	public void testBindNestedMap() throws Exception {
		TargetWithNestedMap target = new TargetWithNestedMap();
		bind(target, "nested.foo: bar\n" + "nested.value: 123");
		assertEquals("123", target.getNested().get("value"));
	}

	@Test
	public void testBindNestedMapBracketReferenced() throws Exception {
		TargetWithNestedMap target = new TargetWithNestedMap();
		bind(target, "nested[foo]: bar\n" + "nested[value]: 123");
		assertEquals("123", target.getNested().get("value"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testBindDoubleNestedMap() throws Exception {
		TargetWithNestedMap target = new TargetWithNestedMap();
		bind(target, "nested.foo: bar\n" + "nested.bar.spam: bucket\n"
				+ "nested.bar.value: 123\nnested.bar.foo: crap");
		assertEquals(2, target.getNested().size());
		assertEquals(3, ((Map<String, Object>) target.getNested().get("bar")).size());
		assertEquals("123",
				((Map<String, Object>) target.getNested().get("bar")).get("value"));
		assertEquals("bar", target.getNested().get("foo"));
		assertFalse(target.getNested().containsValue(target.getNested()));
	}

	@Test
	public void testBindNestedMapOfListOfString() throws Exception {
		TargetWithNestedMapOfListOfString target = new TargetWithNestedMapOfListOfString();
		bind(target, "nested.foo[0]: bar\n" + "nested.bar[0]: bucket\n"
				+ "nested.bar[1]: 123\nnested.bar[2]: crap");
		assertEquals(2, target.getNested().size());
		assertEquals(3, target.getNested().get("bar").size());
		assertEquals("123", target.getNested().get("bar").get(1));
		assertEquals("[bar]", target.getNested().get("foo").toString());
	}

	@Test
	public void testBindNestedMapOfBean() throws Exception {
		TargetWithNestedMapOfBean target = new TargetWithNestedMapOfBean();
		bind(target, "nested.foo.foo: bar\n" + "nested.bar.foo: bucket");
		assertEquals(2, target.getNested().size());
		assertEquals("bucket", target.getNested().get("bar").getFoo());
	}

	@Test
	public void testBindNestedMapOfListOfBean() throws Exception {
		TargetWithNestedMapOfListOfBean target = new TargetWithNestedMapOfListOfBean();
		bind(target, "nested.foo[0].foo: bar\n" + "nested.bar[0].foo: bucket\n"
				+ "nested.bar[1].value: 123\nnested.bar[2].foo: crap");
		assertEquals(2, target.getNested().size());
		assertEquals(3, target.getNested().get("bar").size());
		assertEquals(123, target.getNested().get("bar").get(1).getValue());
		assertEquals("bar", target.getNested().get("foo").get(0).getFoo());
	}

	@Test
	public void testBindErrorTypeMismatch() throws Exception {
		VanillaTarget target = new VanillaTarget();
		BindingResult result = bind(target, "foo: bar\n" + "value: foo");
		assertEquals(1, result.getErrorCount());
	}

	@Test
	public void testBindErrorNotWritable() throws Exception {
		this.expected.expectMessage("property 'spam'");
		this.expected.expectMessage("not writable");
		VanillaTarget target = new VanillaTarget();
		BindingResult result = bind(target, "spam: bar\n" + "value: 123");
		assertEquals(1, result.getErrorCount());
	}

	@Test
	public void testBindErrorNotWritableWithPrefix() throws Exception {
		VanillaTarget target = new VanillaTarget();
		BindingResult result = bind(target, "spam: bar\n" + "vanilla.value: 123",
				"vanilla");
		assertEquals(0, result.getErrorCount());
		assertEquals(123, target.getValue());
	}

	@Test
	public void testOnlyTopLevelFields() throws Exception {
		VanillaTarget target = new VanillaTarget();
		RelaxedDataBinder binder = getBinder(target, null);
		binder.setIgnoreUnknownFields(false);
		binder.setIgnoreNestedProperties(true);
		BindingResult result = bind(binder, target, "foo: bar\n" + "value: 123\n"
				+ "nested.bar: spam");
		assertEquals(123, target.getValue());
		assertEquals("bar", target.getFoo());
		assertEquals(0, result.getErrorCount());
	}

	@Test
	public void testNoNestedFields() throws Exception {
		VanillaTarget target = new VanillaTarget();
		RelaxedDataBinder binder = getBinder(target, "foo");
		binder.setIgnoreUnknownFields(false);
		binder.setIgnoreNestedProperties(true);
		BindingResult result = bind(binder, target, "foo.foo: bar\n" + "foo.value: 123\n"
				+ "foo.nested.bar: spam");
		assertEquals(123, target.getValue());
		assertEquals("bar", target.getFoo());
		assertEquals(0, result.getErrorCount());
	}

	@Test
	public void testBindMap() throws Exception {
		Map<String, Object> target = new LinkedHashMap<String, Object>();
		BindingResult result = bind(target, "spam: bar\n" + "vanilla.value: 123",
				"vanilla");
		assertEquals(0, result.getErrorCount());
		assertEquals("123", target.get("value"));
	}

	@Test
	public void testBindMapNestedMap() throws Exception {
		Map<String, Object> target = new LinkedHashMap<String, Object>();
		BindingResult result = bind(target, "spam: bar\n" + "vanilla.foo.value: 123",
				"vanilla");
		assertEquals(0, result.getErrorCount());
		@SuppressWarnings("unchecked")
		Map<String, Object> map = (Map<String, Object>) target.get("foo");
		assertEquals("123", map.get("value"));
	}

	private BindingResult bind(Object target, String values) throws Exception {
		return bind(target, values, null);
	}

	private BindingResult bind(DataBinder binder, Object target, String values)
			throws Exception {
		Properties properties = PropertiesLoaderUtils
				.loadProperties(new ByteArrayResource(values.getBytes()));
		binder.bind(new MutablePropertyValues(properties));
		binder.validate();

		return binder.getBindingResult();
	}

	private BindingResult bind(Object target, String values, String namePrefix)
			throws Exception {
		return bind(getBinder(target, namePrefix), target, values);
	}

	private RelaxedDataBinder getBinder(Object target, String namePrefix) {
		RelaxedDataBinder binder = new RelaxedDataBinder(target, namePrefix);
		binder.setIgnoreUnknownFields(false);
		LocalValidatorFactoryBean validatorFactoryBean = new LocalValidatorFactoryBean();
		validatorFactoryBean.afterPropertiesSet();
		binder.setValidator(validatorFactoryBean);
		binder.setConversionService(this.conversionService);
		return binder;
	}

	@Documented
	@Target({ ElementType.FIELD })
	@Retention(RUNTIME)
	@Constraint(validatedBy = RequiredKeysValidator.class)
	public @interface RequiredKeys {

		String[] value();

		String message() default "Required fields are not provided for field ''{0}''";

		Class<?>[] groups() default {};

		Class<? extends Payload>[] payload() default {};

	}

	public static class RequiredKeysValidator implements
			ConstraintValidator<RequiredKeys, Map<String, Object>> {

		private String[] fields;

		@Override
		public void initialize(RequiredKeys constraintAnnotation) {
			this.fields = constraintAnnotation.value();
		}

		@Override
		public boolean isValid(Map<String, Object> value,
				ConstraintValidatorContext context) {
			boolean valid = true;
			for (String field : this.fields) {
				if (!value.containsKey(field)) {
					context.buildConstraintViolationWithTemplate(
							"Missing field ''" + field + "''").addConstraintViolation();
					valid = false;
				}
			}
			return valid;
		}

	}

	public static class TargetWithValidatedMap {

		@RequiredKeys({ "foo", "value" })
		private Map<String, Object> info;

		public Map<String, Object> getInfo() {
			return this.info;
		}

		public void setInfo(Map<String, Object> nested) {
			this.info = nested;
		}
	}

	public static class TargetWithNestedMap {
		private Map<String, Object> nested;

		public Map<String, Object> getNested() {
			return this.nested;
		}

		public void setNested(Map<String, Object> nested) {
			this.nested = nested;
		}
	}

	public static class TargetWithNestedMapOfListOfString {
		private Map<String, List<String>> nested;

		public Map<String, List<String>> getNested() {
			return this.nested;
		}

		public void setNested(Map<String, List<String>> nested) {
			this.nested = nested;
		}
	}

	public static class TargetWithNestedMapOfListOfBean {
		private Map<String, List<VanillaTarget>> nested;

		public Map<String, List<VanillaTarget>> getNested() {
			return this.nested;
		}

		public void setNested(Map<String, List<VanillaTarget>> nested) {
			this.nested = nested;
		}
	}

	public static class TargetWithNestedMapOfBean {
		private Map<String, VanillaTarget> nested;

		public Map<String, VanillaTarget> getNested() {
			return this.nested;
		}

		public void setNested(Map<String, VanillaTarget> nested) {
			this.nested = nested;
		}
	}

	public static class TargetWithNestedList {
		private List<String> nested;

		public List<String> getNested() {
			return this.nested;
		}

		public void setNested(List<String> nested) {
			this.nested = nested;
		}
	}

	public static class TargetWithReadOnlyNestedList {
		private List<String> nested = new ArrayList<String>();

		public List<String> getNested() {
			return this.nested;
		}
	}

	public static class TargetWithReadOnlyNestedCollection {
		private Collection<String> nested = new ArrayList<String>();

		public Collection<String> getNested() {
			return this.nested;
		}
	}

	public static class TargetWithNestedSet {
		private Set<String> nested = new LinkedHashSet<String>();

		public Set<String> getNested() {
			return this.nested;
		}

		public void setNested(Set<String> nested) {
			this.nested = nested;
		}
	}

	public static class TargetWithNestedObject {
		private VanillaTarget nested;

		public VanillaTarget getNested() {
			return this.nested;
		}

		public void setNested(VanillaTarget nested) {
			this.nested = nested;
		}
	}

	public static class VanillaTarget {

		private String foo;

		private int value;

		private String foo_bar;

		private String fooBaz;

		public int getValue() {
			return this.value;
		}

		public void setValue(int value) {
			this.value = value;
		}

		public String getFoo() {
			return this.foo;
		}

		public void setFoo(String foo) {
			this.foo = foo;
		}

		public String getFoo_bar() {
			return this.foo_bar;
		}

		public void setFoo_bar(String foo_bar) {
			this.foo_bar = foo_bar;
		}

		public String getFooBaz() {
			return this.fooBaz;
		}

		public void setFooBaz(String fooBaz) {
			this.fooBaz = fooBaz;
		}
	}

	public static class ValidatedTarget {

		@NotNull
		private String foo;

		public String getFoo() {
			return this.foo;
		}

		public void setFoo(String foo) {
			this.foo = foo;
		}

	}
}
