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

package org.sweble.wikitext.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtUrl;
import org.sweble.wikitext.parser.utils.NonExpandingParser;

/**
 * Free (plain) URLs end where MediaWiki ends them: at characters which are
 * not in Parser::EXT_LINK_URL_CLASS and at the character references
 * Parser::makeFreeExternalLink() stops at.
 */
public class FreeUrlTest
{
	@Test
	public void testFreeUrlStopsAtSpaceSeparators() throws Exception
	{
		assertEquals("//example.com/a", parseUrl("http://example.com/a\u00A0b").getPath());
		assertEquals("//example.com/a", parseUrl("http://example.com/a\u3000b").getPath());
		assertEquals("//example.com/a", parseUrl("http://example.com/a\u2009b").getPath());
		assertEquals("//example.com/a", parseUrl("http://example.com/a\u202Fb").getPath());
	}

	@Test
	public void testFreeUrlStopsAtReplacementCharacter() throws Exception
	{
		assertEquals("//example.com/a", parseUrl("http://example.com/a\uFFFDb").getPath());
	}

	@Test
	public void testFreeUrlStopsAtEscapedAngleBracketsAndNbsp() throws Exception
	{
		String[] refs = {
				"&lt;",
				"&gt;",
				"&nbsp;",
				"&#60;",
				"&#062;",
				"&#160;",
				"&#x3C;",
				"&#x3e;",
				"&#x003C;",
				"&#xA0;",
				"&#xa0;" };

		for (String ref : refs)
		{
			assertEquals(ref, "//example.com/a", parseUrl("http://example.com/a" + ref + "b").getPath());
		}
	}

	@Test
	public void testFreeUrlKeepsOtherCharacterReferences() throws Exception
	{
		assertEquals("//example.com/?a=1&amp;b=2", parseUrl("http://example.com/?a=1&amp;b=2").getPath());
		assertEquals("//example.com/a&#65;b", parseUrl("http://example.com/a&#65;b").getPath());
		assertEquals("//example.com/a&LT;b", parseUrl("http://example.com/a&LT;b").getPath());
	}

	// =========================================================================

	private WtUrl parseUrl(String wikitext) throws Exception
	{
		NonExpandingParser parser = new NonExpandingParser();
		WtUrl url = find(parser.parseArticle("x " + wikitext + " y", "title"));
		assertNotNull("No URL found", url);
		return url;
	}

	private WtUrl find(WtNode node)
	{
		if (node instanceof WtUrl)
			return (WtUrl) node;
		for (WtNode child : node)
		{
			WtUrl url = find(child);
			if (url != null)
				return url;
		}
		return null;
	}
}
