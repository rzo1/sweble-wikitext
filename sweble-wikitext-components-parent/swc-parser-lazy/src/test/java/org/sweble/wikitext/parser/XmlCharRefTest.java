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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtText;
import org.sweble.wikitext.parser.nodes.WtXmlCharRef;
import org.sweble.wikitext.parser.utils.NonExpandingParser;

/**
 * Like MediaWiki's {@code Sanitizer} only character references to valid code
 * points become characters, all others are kept as literal text.
 */
public class XmlCharRefTest
{
	@Test
	public void testHugeDecimalCharRefIsKeptAsText() throws Exception
	{
		assertKeptAsText("&#99999999999;");
		assertKeptAsText("&#99999999999999999999999999999;");
	}

	@Test
	public void testHugeHexCharRefIsKeptAsText() throws Exception
	{
		assertKeptAsText("&#x123456789AB;");
		assertKeptAsText("&#xFFFFFFFFFFFFFFFFFFFFFFFF;");
	}

	@Test
	public void testCodePointAboveUnicodeRangeIsKeptAsText() throws Exception
	{
		assertKeptAsText("&#x110000;");
		assertKeptAsText("&#1114112;");
	}

	@Test
	public void testSurrogateCharRefIsKeptAsText() throws Exception
	{
		assertKeptAsText("&#xD800;");
		assertKeptAsText("&#xDFFF;");
		assertKeptAsText("&#56320;");
	}

	@Test
	public void testNulCharRefIsKeptAsText() throws Exception
	{
		assertKeptAsText("&#0;");
		assertKeptAsText("&#x0;");
		assertKeptAsText("&#00000000000000000000;");
	}

	@Test
	public void testControlAndNonCharacterCharRefsAreKeptAsText() throws Exception
	{
		assertKeptAsText("&#x8;");
		assertKeptAsText("&#12;");
		assertKeptAsText("&#13;");
		assertKeptAsText("&#20;");
		assertKeptAsText("&#x7F;");
		assertKeptAsText("&#x9F;");
		assertKeptAsText("&#xFFFE;");
		assertKeptAsText("&#xFFFF;");
	}

	@Test
	public void testValidCharRefsBecomeCharRefs() throws Exception
	{
		assertCharRef("&#9;", 0x09);
		assertCharRef("&#10;", 0x0A);
		assertCharRef("&#65;", 0x41);
		assertCharRef("&#x41;", 0x41);
		assertCharRef("&#0000000000000065;", 0x41);
		assertCharRef("&#x00000000000041;", 0x41);
		assertCharRef("&#xA0;", 0xA0);
		assertCharRef("&#xD7FF;", 0xD7FF);
		assertCharRef("&#xE000;", 0xE000);
		assertCharRef("&#xFFFD;", 0xFFFD);
		assertCharRef("&#x1F600;", 0x1F600);
		assertCharRef("&#x10FFFF;", 0x10FFFF);
		assertCharRef("&#1114111;", 0x10FFFF);
	}

	@Test
	public void testUpperCaseHexCharRefs() throws Exception
	{
		// Like MediaWiki's Sanitizer::CHAR_REFS_REGEX
		assertCharRef("&#X41;", 0x41);
		assertCharRef("&#X1F600;", 0x1F600);
		assertKeptAsText("&#X110000;");
		assertKeptAsText("&#X0;");
	}

	@Test
	public void testInvalidCharRefInAttributeIsKeptAsText() throws Exception
	{
		WtNode ast = parse("<span title=\"&#x110000;\">x</span>");

		assertTrue(findCharRefs(ast).isEmpty());
		// The attribute name, the attribute value and the content
		assertEquals("title&#x110000;x", collectText(ast));
	}

	// =========================================================================

	private static void assertKeptAsText(String ref) throws Exception
	{
		WtNode ast = parse("a " + ref + " b");

		assertTrue(ref, findCharRefs(ast).isEmpty());
		assertEquals(ref, "a " + ref + " b", collectText(ast));
	}

	private static void assertCharRef(String ref, int codePoint) throws Exception
	{
		WtNode ast = parse("a " + ref + " b");

		List<WtXmlCharRef> refs = findCharRefs(ast);
		assertEquals(ref, 1, refs.size());
		assertEquals(ref, codePoint, refs.get(0).getCodePoint());
		assertEquals(ref, "a  b", collectText(ast));
	}

	private static WtNode parse(String wikitext) throws Exception
	{
		return new NonExpandingParser().parseArticle(wikitext, "title");
	}

	private static List<WtXmlCharRef> findCharRefs(WtNode node)
	{
		List<WtXmlCharRef> refs = new ArrayList<WtXmlCharRef>();
		findCharRefs(node, refs);
		return refs;
	}

	private static void findCharRefs(WtNode node, List<WtXmlCharRef> refs)
	{
		if (node instanceof WtXmlCharRef)
			refs.add((WtXmlCharRef) node);
		for (WtNode child : node)
			findCharRefs(child, refs);
	}

	private static String collectText(WtNode node)
	{
		StringBuilder b = new StringBuilder();
		collectText(node, b);
		return b.toString();
	}

	private static void collectText(WtNode node, StringBuilder b)
	{
		if (node instanceof WtText)
			b.append(((WtText) node).getContent());
		for (WtNode child : node)
			collectText(child, b);
	}
}
