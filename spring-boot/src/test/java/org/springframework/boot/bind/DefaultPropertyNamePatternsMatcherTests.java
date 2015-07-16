/*
 * Copyright 2012-2014 the original author or authors.
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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link DefaultPropertyNamePatternsMatcher}.
 *
 * @author Phillip Webb
 * @author Dave Syer
 */
public class DefaultPropertyNamePatternsMatcherTests {

	@Test
	public void namesShorter() {
		assertFalse(new DefaultPropertyNamePatternsMatcher("aaaa", "bbbb")
				.matches("zzzzz"));

	}

	@Test
	public void namesExactMatch() {
		assertTrue(new DefaultPropertyNamePatternsMatcher("aaaa", "bbbb", "cccc")
				.matches("bbbb"));
	}

	@Test
	public void namesArrayMatch() {
		assertTrue(new DefaultPropertyNamePatternsMatcher("aaaa", "bbbb", "cccc")
				.matches("bbbb[0]"));
	}

	@Test
	public void namesLonger() {
		assertFalse(new DefaultPropertyNamePatternsMatcher("aaaaa", "bbbbb", "ccccc")
				.matches("bbbb"));
	}

	@Test
	public void namesLongerWithSeparatorInPattern() {
		assertTrue(new DefaultPropertyNamePatternsMatcher("aaaa.bar", "bbbb.FOO", "cccc.bar")
				.matches("bbbb.foo"));
	}

	@Test
	public void nameWithDot() throws Exception {
		assertTrue(new DefaultPropertyNamePatternsMatcher("aaaa", "bbbb", "cccc")
				.matches("bbbb.anything"));
	}

	@Test
	public void nameWithUnderscore() throws Exception {
		assertTrue(new DefaultPropertyNamePatternsMatcher("aaaa", "bbbb", "cccc")
				.matches("bbbb_anything"));
	}

	@Test
	public void nameWithDotAndUnderscore() throws Exception {
		assertTrue(new DefaultPropertyNamePatternsMatcher("aaaa", "bbbb", "cccc")
				.matches("bbbb.foo_bar"));
	}

	@Test
	public void nameWithDotAndUnderscoreUppercase() throws Exception {
		assertTrue(new DefaultPropertyNamePatternsMatcher("aaaa", "bbbb", "cccc")
				.matches("bbbb.FOO_BAR"));
	}

	@Test
	public void namesMatchWithDifferentLengths() throws Exception {
		assertTrue(new DefaultPropertyNamePatternsMatcher("aaa", "bbbb", "ccccc")
				.matches("bbbb"));

	}

	@Test
	public void patternWithDotAndUnderscore() throws Exception {
		assertTrue(new DefaultPropertyNamePatternsMatcher("aaaa.spam", "bbbb.fooBar", "cccc.bucket")
				.matches("bbbb.foo_bar"));
	}

	@Test
	public void patternWithDotAndUnderscoreUppercase() throws Exception {
		assertTrue(new DefaultPropertyNamePatternsMatcher("aaaa.spam", "bbbb.fooBar", "cccc.bucket")
				.matches("bbbb.FOO_BAR"));
	}

}
