/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sweble.wikitext.engine.utils;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UrlEncodingTest
{
	/** Like MediaWiki's wfUrlencode() (issue #133). */
	@Test
	public void testWikiKeepsTilde()
	{
		assertEquals("User:~Foo_bar", UrlEncoding.WIKI.encode("User:~Foo bar"));
		assertEquals("A%26B~(c)", UrlEncoding.WIKI.encode("A&B~(c)"));
	}

	/** Like PHP's rawurlencode() (issue #133). */
	@Test
	public void testPathKeepsTilde()
	{
		assertEquals("~foo%20bar%2A", UrlEncoding.PATH.encode("~foo bar*"));
	}

	/** PHP's urlencode() encodes the tilde. */
	@Test
	public void testQueryEncodesTilde()
	{
		assertEquals("%7Efoo+bar%2A", UrlEncoding.QUERY.encode("~foo bar*"));
	}
}
