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

package org.sweble.wikitext.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;

public class TextConverterTest
{
	private static String convert(String wikitext) throws Exception
	{
		WikiConfig config = DefaultConfigEnWp.generate();
		WtEngineImpl engine = new WtEngineImpl(config);
		PageTitle pageTitle = PageTitle.make(config, "Test");
		EngProcessedPage cp = engine.postprocess(new PageId(pageTitle, -1), wikitext, null);
		return (String) new TextConverter(config, 80).go(cp.getPage());
	}

	// =========================================================================

	@Test
	public void testTableCellsAreSeparated() throws Exception
	{
		String actual = convert("{|\n! H1 !! H2\n|-\n| a || b\n|-\n| c || d\n|}");

		assertEquals("H1 | H2\na | b\nc | d", actual);
	}

	@Test
	public void testTableIsSeparatedFromParagraphs() throws Exception
	{
		String actual = convert("Before\n{|\n|+ Caption\n| a\n|}\nAfter");

		assertEquals("Before\n\nCaption\na\n\nAfter", actual);
	}

	@Test
	public void testNestedTable() throws Exception
	{
		String actual = convert("{|\n| a\n|\n{|\n| b || c\n|}\n| d\n|}");

		assertFalse(actual, actual.contains("<Wt"));
		assertEquals("a |\nb | c\n| d", actual);
	}

	@Test
	public void testHtmlTableCellsAreSeparated() throws Exception
	{
		String actual = convert("<table><tr><td>a</td><td>b</td></tr><tr><td>c</td></tr></table>");

		assertEquals("a | b\nc", actual);
	}

	@Test
	public void testDefinitionList() throws Exception
	{
		String actual = convert(";Term\n:Definition\nAfter");

		assertEquals("Term\nDefinition\n\nAfter", actual);
	}

	@Test
	public void testListIsFollowedByNewline() throws Exception
	{
		String actual = convert("* a\n* b\n** c\n* d\nAfter");

		assertEquals("a\nb\nc\nd\n\nAfter", actual);
	}

	@Test
	public void testSemiPreKeepsLinesAndSpaces() throws Exception
	{
		String actual = convert("Before\n foo  bar\n  baz\nAfter");

		assertEquals("Before\n\nfoo  bar\n baz\n\nAfter", actual);
	}

	@Test
	public void testPreTagKeepsLinesAndSpaces() throws Exception
	{
		String actual = convert("Before\n<pre>a  b\nc ''d''</pre>\nAfter");

		assertEquals("Before\n\na  b\nc ''d''\n\nAfter", actual);
	}

	@Test
	public void testNowikiContentIsPrinted() throws Exception
	{
		String actual = convert("a <nowiki>''b''</nowiki> c");

		assertEquals("a ''b'' c", actual);
	}

	@Test
	public void testPoemKeepsLines() throws Exception
	{
		String actual = convert("<poem>\nline one\nline two\n</poem>");

		assertEquals("line one\nline two", actual);
	}

	@Test
	public void testTagExtensionTextIsPrinted() throws Exception
	{
		String actual = convert("Formula <math>x^2</math> here");

		assertEquals("Formula x^2 here", actual);
	}

	@Test
	public void testRefsAreHidden() throws Exception
	{
		String actual = convert("Text<ref>Note</ref>.\n\n<references />");

		assertEquals("Text.", actual);
	}

	@Test
	public void testSignature() throws Exception
	{
		String actual = convert("Comment ~~~~");

		assertEquals("Comment ~~~~", actual);
	}

	@Test
	public void testRedirect() throws Exception
	{
		String actual = convert("#REDIRECT [[Target page]]");

		assertEquals("REDIRECT Target page", actual);
	}

	@Test
	public void testLanguageConversionTextIsPrinted() throws Exception
	{
		String actual = convert("a -{b}- c");

		assertEquals("a b c", actual);
	}

	@Test
	public void testBlockElementsAreOnTheirOwnLines() throws Exception
	{
		String actual = convert("Before<div>Inside</div>After");

		assertEquals("Before\n\nInside\nAfter", actual);
	}

	@Test
	public void testLeadingColonOfLinkTargetIsStripped() throws Exception
	{
		String actual = convert("[[:Foo]] and [[:Category:Bar]]");

		assertEquals("Foo and Category:Bar", actual);
	}

	@Test
	public void testInvalidCharRefIsKeptAsText() throws Exception
	{
		// References to invalid code points are no character references but
		// text, like in MediaWiki (#142)
		String actual = convert("a&#x110000;b&#xD800;c&#65;");

		assertEquals("a&#x110000;b&#xD800;cA", actual);
	}

	@Test
	public void testNoPlaceholders() throws Exception
	{
		String actual = convert(""
				+ "#REDIRECT [[Target]]\n"
				+ "{|\n| a\n|}\n"
				+ "~~~ -{x}- -{H|zh-cn:y; zh-tw:z}-\n"
				+ "<span>s</span></div>\n"
				+ "; t : d\n");

		assertFalse(actual, actual.contains("<Wt"));
		assertFalse(actual, actual.contains("<Eng"));
	}
}
