/**
 * Copyright 2011 The Open Source Research Group,
 *                University of Erlangen-Nürnberg
 *
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

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class UrlServiceTest
{
	@Test
	public void testQueryMapToStringSeparatesKeyAndValueWithEquals() throws Exception
	{
		Map<String, String> query = new LinkedHashMap<String, String>();
		query.put("title", "Foo bar");
		query.put("action", "edit");

		assertEquals("title=Foo+bar&action=edit", UrlService.queryMapToString(query));
	}

	@Test
	public void testAppendQueryMap() throws Exception
	{
		Map<String, String> query = new LinkedHashMap<String, String>();
		query.put("action", "edit");

		assertEquals(
				"http://localhost/index.php?title=Foo&action=edit",
				UrlService.appendQuery(new URL("http://localhost/index.php?title=Foo"), query).toString());
	}
}
