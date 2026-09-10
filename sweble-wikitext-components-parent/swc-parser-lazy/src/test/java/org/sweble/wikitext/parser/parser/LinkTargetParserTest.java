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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.sweble.wikitext.parser.nodes.WtImageLink;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.utils.NonExpandingParser;
import org.sweble.wikitext.parser.utils.SimpleParserConfig;

/**
 * Tests the percent decoding of link targets (see upstream
 * sweble/sweble-wikitext#69), the namespace separator, the decoding of
 * character references and the reuse of the parser.
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
	public void testSpacesAroundNamespaceColon() throws Exception
	{
		String[] targets = {
				"File : A.png",
				"File  :  A.png",
				"File_:_A.png",
				"File\u00A0:\u3000A.png",
				" File : A.png " };

		for (String target : targets)
		{
			LinkTargetParser ltp = new LinkTargetParser();
			ltp.parse(new SimpleParserConfig(), target);

			assertEquals(target, "File", ltp.getNamespace());
			assertEquals(target, "A.png", ltp.getTitle());
		}
	}

	@Test
	public void testSpacesInFrontOfFragmentAreTrimmed() throws Exception
	{
		String[] targets = {
				"A #b",
				"A  #b",
				"A_#b",
				"A #b",
				"A 　_#b" };

		for (String target : targets)
		{
			LinkTargetParser ltp = new LinkTargetParser();
			ltp.parse(new SimpleParserConfig(), target);

			assertEquals(target, "A", ltp.getTitle());
			assertEquals(target, "b", ltp.getFragment());
		}
	}

	@Test
	public void testSpacesInNamespaceNamesAreNormalized() throws Exception
	{
		String[] targets = {
				"User talk:A",
				"User_talk:A",
				"User  talk:A",
				"User talk:A",
				"User _talk:A" };

		SimpleParserConfig config = new SimpleParserConfig()
		{
			@Override
			public boolean isNamespace(String name)
			{
				return "user talk".equalsIgnoreCase(name) || super.isNamespace(name);
			}
		};

		for (String target : targets)
		{
			LinkTargetParser ltp = new LinkTargetParser();
			ltp.parse(config, target);

			assertEquals(target, "User talk", ltp.getNamespace());
			assertEquals(target, "A", ltp.getTitle());
		}
	}

	@Test
	public void testSpacesAfterInitialColon() throws Exception
	{
		LinkTargetParser ltp = new LinkTargetParser();
		ltp.parse(new SimpleParserConfig(), ": File : A.png");

		assertTrue(ltp.isInitialColon());
		assertEquals("File", ltp.getNamespace());
		assertEquals("A.png", ltp.getTitle());
	}

	@Test
	public void testImageLinkWithSpacesAroundNamespaceColonIsRecognized() throws Exception
	{
		NonExpandingParser parser = new NonExpandingParser();

		assertNotNull(scanForImageLink(parser.parseArticle("[[File : A.png|thumb]]", "title")));
		assertNotNull(scanForImageLink(parser.parseArticle("[[File_: A.png|thumb]]", "title")));
	}

	@Test
	public void testParseResetsState() throws Exception
	{
		SimpleParserConfig config = new SimpleParserConfig();
		LinkTargetParser ltp = new LinkTargetParser();

		ltp.parse(config, ":File:A.png#Section");
		assertEquals("File", ltp.getNamespace());
		assertEquals("Section", ltp.getFragment());
		assertTrue(ltp.isInitialColon());

		ltp.parse(config, "B");
		assertEquals("B", ltp.getTitle());
		assertNull(ltp.getNamespace());
		assertNull(ltp.getFragment());
		assertNull(ltp.getInterwiki());
		assertFalse(ltp.isInitialColon());

		try
		{
			ltp.parse(config, "File:");
			fail("Expected LinkTargetException");
		}
		catch (LinkTargetException e)
		{
			// Expected
		}

		ltp.parse(config, "C");
		assertEquals("C", ltp.getTitle());
		assertNull(ltp.getNamespace());
	}

	@Test
	public void testInvalidCharReferencesMakeTitleInvalid() throws Exception
	{
		String[] targets = {
				"A&#99999999999;",
				"A&#xD800;",
				"A&#xDFFF;B",
				"A&#x110041;",
				"A&#1114177;",
				"A&#xFFFE;",
				"A&#x80;" };

		for (String target : targets)
		{
			try
			{
				new LinkTargetParser().parse(new SimpleParserConfig(), target);
				fail("Expected LinkTargetException for " + target);
			}
			catch (LinkTargetException e)
			{
				// Expected
			}
		}
	}

	@Test
	public void testValidCharReferencesAreDecoded() throws Exception
	{
		LinkTargetParser ltp = new LinkTargetParser();

		ltp.parse(new SimpleParserConfig(), "A&#66;&#x43;&#X44;");
		assertEquals("ABCD", ltp.getTitle());

		ltp.parse(new SimpleParserConfig(), "A&#x1F600;");
		assertEquals("A\uD83D\uDE00", ltp.getTitle());

		ltp.parse(new SimpleParserConfig(), "A&amp;B");
		assertEquals("A&B", ltp.getTitle());
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
