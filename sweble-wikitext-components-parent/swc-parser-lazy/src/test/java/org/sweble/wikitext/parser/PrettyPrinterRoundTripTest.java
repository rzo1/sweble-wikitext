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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.sweble.wikitext.parser.ParserIntegrationTestBase.CleanupAst;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtText;
import org.sweble.wikitext.parser.nodes.WtXmlAttribute;
import org.sweble.wikitext.parser.utils.NonExpandingParser;
import org.sweble.wikitext.parser.utils.WtAstPrinter;
import org.sweble.wikitext.parser.utils.WtPrettyPrinter;

/**
 * Pretty printing and parsing again must result in the same AST.
 */
public class PrettyPrinterRoundTripTest
{
	@Test
	public void testTableMarkersDoNotContinuePreformattedText() throws Exception
	{
		assertEquals(
				"{|\n| a\n\n pre\n| b\n|}",
				assertRoundTrip("{|\n| a\n pre\n| b\n|}"));

		assertRoundTrip("{|\n|+ caption\n|-\n! a\n pre\n! b\n|-\n| c\n pre\n| d\n|}");
	}

	@Test
	public void testPreformattedTextAtStartOfCellStaysPreformatted() throws Exception
	{
		assertEquals("{|\n|\n pre\n|}", assertRoundTrip("{|\n|\n pre\n|}"));
		assertEquals("{|\n!\n pre\n|}", assertRoundTrip("{|\n!\n pre\n|}"));
		assertEquals("{|\n|+\n pre\n|}", assertRoundTrip("{|\n|+\n pre\n|}"));
		assertEquals("{|\n| x=\"y\" |\n pre\n|}", assertRoundTrip("{|\n| x=y |\n pre\n|}"));
	}

	@Test
	public void testAttributeValueWithDoubleQuoteIsSingleQuoted() throws Exception
	{
		assertEquals(
				"<span title='a \" b'>x</span>",
				assertRoundTrip("<span title='a \" b'>x</span>"));
		assertEquals(
				"<span title='a\"b'>x</span>",
				assertRoundTrip("<span title='a\"b'>x</span>"));
		assertEquals(
				"<span title=\"a'b\">x</span>",
				assertRoundTrip("<span title=\"a'b\">x</span>"));
		assertEquals(
				"<ref name='a\"b'>x</ref>",
				assertRoundTrip("<ref name='a\"b'>x</ref>"));

		assertRoundTrip("{| title='a\"b'\n|- title='a\"b'\n| title='a\"b' | c\n|}");
	}

	@Test
	public void testAttributeValueWithBothQuotesIsEscaped() throws Exception
	{
		WtNode page = parse("<span title=\"a'b\">x</span>");
		WtXmlAttribute attr = (WtXmlAttribute) find(page, WtNode.NT_XML_ATTRIBUTE);
		((WtText) attr.getValue().get(0)).setContent("a'b\"c");

		assertEquals(
				"<span title=\"a'b&quot;c\">x</span>",
				prettyPrint(page));
	}

	@Test
	public void testAdjacentTicksAreSeparated() throws Exception
	{
		assertEquals(
				"'''''bold italic'''''<nowiki/>''' bold'''",
				assertRoundTrip("'''''bold italic'' bold'''"));
	}

	@Test
	public void testAdjacentTicksAreOnlySeparatedWhereNeeded() throws Exception
	{
		assertEquals("'''''x'''''", assertRoundTrip("'''''x'''''"));
		assertEquals("'''''x''' y''", assertRoundTrip("'''''x''' y''"));
		assertEquals("''x'''''y'''", assertRoundTrip("''x'''''y'''"));
		assertEquals("'''x'''''y''", assertRoundTrip("'''x'''''y''"));
		assertEquals("a''''b'''", assertRoundTrip("a''''b'''"));
	}

	@Test
	public void testEmptyBoldAndItalicsPrintNothing() throws Exception
	{
		assertEquals("l", assertRoundTrip("l'''"));
		assertEquals("a ", assertRoundTrip("a '''''"));
		assertEquals("", prettyPrint(parse("'''''")));
	}

	@Test
	public void testUnrecognizedLctFlagsArePrinted() throws Exception
	{
		assertEquals("-{A|zh-hans:a;}-", assertRoundTrip("-{A|zh-hans:a;}-"));
		assertEquals("-{X|zh-hans:a;}-", assertRoundTrip("-{X|zh-hans:a;}-"));
		assertRoundTrip("-{X;zh-hans|a}-");
	}

	@Test
	public void testCommentOnOwnLineStaysBetweenBlocks() throws Exception
	{
		String wikitext = "a\n<!-- c -->\n\nb";
		assertEquals(wikitext, assertRoundTrip(wikitext));

		// The comment must neither become part of a paragraph ...
		assertEquals(
				Arrays.asList(
						WtNode.NT_PARAGRAPH,
						WtNode.NT_XML_COMMENT,
						WtNode.NT_PARAGRAPH),
				nodeTypes(parse(wikitext)));

		// ... nor after other blocks
		assertRoundTrip("{|\n| x\n|}\n<!-- c -->\n\nb");
		assertRoundTrip("----\n<!-- c -->\n\nb");
		assertRoundTrip(" pre\n<!-- c -->\n\nb");
		assertRoundTrip("a\n<!-- c1 -->\n<!-- c2 -->\n\nb");
	}

	// =========================================================================

	/**
	 * Checks that the pretty printed wikitext parses to the same AST as the
	 * given wikitext and that pretty printing it again yields the same
	 * wikitext.
	 *
	 * @return The pretty printed wikitext.
	 */
	private String assertRoundTrip(String wikitext) throws Exception
	{
		WtNode page = parse(wikitext);
		String printed = prettyPrint(page);

		assertEquals(cleanedAst(wikitext), cleanedAst(printed));

		// The structure of paragraphs is not compared by the cleaned ASTs
		assertEquals(nodeTypes(page), nodeTypes(parse(printed)));

		assertEquals(printed, prettyPrint(parse(printed)));

		return printed;
	}

	private static WtNode parse(String wikitext) throws Exception
	{
		return new NonExpandingParser().parseArticle(wikitext, "test");
	}

	private static String prettyPrint(WtNode page)
	{
		// Leading newlines are not significant
		return WtPrettyPrinter.print(page).replaceFirst("^\n+", "");
	}

	private static String cleanedAst(String wikitext) throws Exception
	{
		NonExpandingParser parser = new NonExpandingParser();
		parser.addVisitor(new CleanupAst((ParserConfig) parser.getConfig()));
		return WtAstPrinter.print(parser.parseArticle(wikitext, "test"));
	}

	private static List<Integer> nodeTypes(WtNode page)
	{
		List<Integer> types = new ArrayList<Integer>();
		for (WtNode n : page)
		{
			if (n.isNodeType(WtNode.NT_NEWLINE))
				continue;
			if (n.isNodeType(WtNode.NT_TEXT)
					&& ((WtText) n).getContent().trim().isEmpty())
				continue;
			types.add(n.getNodeType());
		}
		return types;
	}

	private static WtNode find(WtNode node, int nodeType)
	{
		if (node.isNodeType(nodeType))
			return node;
		for (WtNode child : node)
		{
			WtNode found = find(child, nodeType);
			if (found != null)
				return found;
		}
		return null;
	}
}
