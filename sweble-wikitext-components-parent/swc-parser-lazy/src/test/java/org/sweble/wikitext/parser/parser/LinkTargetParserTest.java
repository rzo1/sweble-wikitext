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
package org.sweble.wikitext.parser.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.sweble.wikitext.parser.nodes.WtImageLink;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.utils.NonExpandingParser;
import org.sweble.wikitext.parser.utils.SimpleParserConfig;

/**
 * Tests the percent decoding of link targets, see upstream
 * sweble/sweble-wikitext#69.
 */
public class LinkTargetParserTest
{
	@Test
	public void testUrlDecodeDecodesUtf8Sequences()
	{
		assertEquals("\u00D6sterreich", LinkTargetParser.urlDecode("%C3%96sterreich"));
		assertEquals("\u20AC", LinkTargetParser.urlDecode("%e2%82%ac"));
		assertEquals("\uD83D\uDE00", LinkTargetParser.urlDecode("%F0%9F%98%80"));
	}

	@Test
	public void testUrlDecodeDecodesAsciiAndTrailingEscape()
	{
		assertEquals("Hallo Hallo", LinkTargetParser.urlDecode("Hallo%20Hallo"));
		assertEquals("FooA", LinkTargetParser.urlDecode("Foo%41"));
		assertEquals("\0", LinkTargetParser.urlDecode("%00"));
	}

	@Test
	public void testUrlDecodeLeavesPlusUntouched()
	{
		assertEquals("Hallo Hallo+Hallo", LinkTargetParser.urlDecode("Hallo%20Hallo+Hallo"));
		assertEquals("Hallo+Hallo", LinkTargetParser.urlDecode("Hallo+Hallo"));
	}

	@Test
	public void testUrlDecodeLeavesMalformedEscapesUntouched()
	{
		assertEquals("Hallo %0 Hallo", LinkTargetParser.urlDecode("Hallo %0 Hallo"));
		assertEquals("%zz", LinkTargetParser.urlDecode("%zz"));
		assertEquals("%+1", LinkTargetParser.urlDecode("%+1"));
		assertEquals("%4", LinkTargetParser.urlDecode("%4"));
		assertEquals("%", LinkTargetParser.urlDecode("%"));
	}

	@Test
	public void testUrlDecodeReplacesInvalidUtf8()
	{
		assertEquals("Hallo \uFFFD Hallo", LinkTargetParser.urlDecode("Hallo %80 Hallo"));
		assertEquals("\uFFFDx", LinkTargetParser.urlDecode("%C3x"));
		assertEquals("\uFFFD\uFFFD", LinkTargetParser.urlDecode("%C0%AF"));
	}

	@Test
	public void testParsePercentEncodedUtf8Title() throws Exception
	{
		LinkTargetParser ltp = new LinkTargetParser();
		ltp.parse(new SimpleParserConfig(), "File:%C3%96sterreich_Wien.JPG");

		assertEquals("File", ltp.getNamespace());
		assertEquals("\u00D6sterreich_Wien.JPG", ltp.getTitle());
	}

	@Test
	public void testParseKeepsPlus() throws Exception
	{
		LinkTargetParser ltp = new LinkTargetParser();
		ltp.parse(new SimpleParserConfig(), "Hallo%20Hallo+Hallo");

		assertEquals("Hallo_Hallo+Hallo", ltp.getTitle());
	}

	@Test
	public void testParseRejectsInvalidUtf8() throws Exception
	{
		try
		{
			new LinkTargetParser().parse(new SimpleParserConfig(), "Hallo %80 Hallo");
			fail("Expected LinkTargetException");
		}
		catch (LinkTargetException e)
		{
			// Expected
		}
	}

	@Test
	public void testPercentEncodedImageLinkIsRecognized() throws Exception
	{
		NonExpandingParser parser = new NonExpandingParser();

		WtNode article = parser.parseArticle(
				"[[File:%C3%96sterreich_Wien.JPG|mini|\u00D6sterreich]]",
				"title");

		assertNotNull(scanForImageLink(article));
	}

	private WtNode scanForImageLink(WtNode node)
	{
		if (node instanceof WtImageLink)
			return node;
		for (WtNode child : node)
		{
			WtNode link = scanForImageLink(child);
			if (link != null)
				return link;
		}
		return null;
	}
}
