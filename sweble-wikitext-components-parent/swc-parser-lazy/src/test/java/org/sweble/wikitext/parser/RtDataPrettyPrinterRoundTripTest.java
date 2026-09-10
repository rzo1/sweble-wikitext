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
import org.sweble.wikitext.parser.ParserIntegrationTestBase.CleanupAst;
import org.sweble.wikitext.parser.nodes.WtEmptyImmutableNode;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.utils.NonExpandingParser;
import org.sweble.wikitext.parser.utils.WtAstPrinter;
import org.sweble.wikitext.parser.utils.WtPrettyPrinter;
import org.sweble.wikitext.parser.utils.WtRtDataPrettyPrinter;

/**
 * Printing an AST which carries round-trip data with the
 * {@link WtRtDataPrettyPrinter} must reproduce the wikitext.
 */
public class RtDataPrettyPrinterRoundTripTest
{
	/**
	 * Wikitext which is printed exactly as it was parsed.
	 */
	private static final String[] REPRODUCED = {
			// Lists
			"* a\n** b\n* c",
			"*# a",
			"*: a",
			"*# a\n#: b",
			"# a\n## b\n#* c\n# d",
			"x\n* a\n*** b\n\ny",
			// Definition lists
			"; t : d\n: e",
			"; term\n: def\n:: nested",
			":{|\n| a\n|}",
			// Internal links
			"[[Foo]]",
			"[[Foo|bar]]",
			"pre[[Foo]]post",
			"[[Foo|a ''b'' c]]",
			"<i> a [[target| b </i> c ]] d",
			// External links
			"[http://x T]",
			"[http://x]",
			"[http://example.com a [[b]] c]",
			"http://example.com",
			// Images
			"[[File:X.png|upright|Caption]]",
			"[[File:X.png|thumb|100px|alt=A|link=Foo|Cap {{x}}]]",
			"[[File:X.png|{{T}}|x]]",
			"[[File:X.png|100x50px|left|[[Foo|bar]] caption]]",
			// Tables
			"{|\n|-\n| a || b\n|}",
			"{| class=\"t\"\n|+ caption\n|-\n! h1 !! h2\n|-\n| style=\"x\" | a || b\n|}",
			"a\n{|\n| b\n|}\nc",
			// Language conversion tags
			"-{zh-hans:a;zh-hant:b}-",
			"-{A|zh-hans:a;}-",
			"-{X;zh-hans|a}-",
			"-{a}-",
			// Headings
			"== H ==\ntext\n=== I ===\nt2",
			"=H=\n\n\ntext",
			// Templates
			"{{T|a|b=c}}",
			"{{T\n|a=1\n|b={{U|x}}\n}}",
			"a {{{p|d}}} b",
			// Others
			"a\n\nb",
			"''i'' '''b'''",
			"<span {{a}} title=\"x\">y</span>",
			"<span  foo title=\"x\">y</span>",
			"----",
			"x\n<!-- c -->\n\ny",
	};

	/**
	 * Wikitext in which no node carries round-trip data. It is pretty printed
	 * and parses to the same AST again.
	 */
	private static final String[] REPARSED = {
			"a\n\n\n\nb",
			" pre\n pre2\nx",
	};

	@Test
	public void testAstWithRtdIsReproduced() throws Exception
	{
		for (String wikitext : REPRODUCED)
		{
			String printed = WtRtDataPrettyPrinter.print(parse(wikitext));
			assertEquals(wikitext, printed);
		}
	}

	@Test
	public void testAstWithoutRtdParsesToSameAst() throws Exception
	{
		for (String wikitext : REPARSED)
		{
			String printed = WtRtDataPrettyPrinter.print(parse(wikitext));
			assertEquals(cleanedAst(wikitext), cleanedAst(printed));
		}
	}

	@Test
	public void testAstWithoutRtdIsPrettyPrinted() throws Exception
	{
		for (String wikitext : REPRODUCED)
		{
			WtNode page = parse(wikitext);
			clearRtd(page);
			assertEquals(
					WtPrettyPrinter.print(page),
					WtRtDataPrettyPrinter.print(page));
		}
	}

	/**
	 * If a node has no round-trip data, the pretty printer prints the syntax
	 * which is otherwise part of its round-trip data.
	 */
	@Test
	public void testSyntaxOfNodeWithoutRtdIsPrintedOnce() throws Exception
	{
		assertPrintedWithoutRtd("[[Foo|bar]]", WtNode.NT_INTERNAL_LINK, 0);
		assertPrintedWithoutRtd("[[Foo|bar]]", WtNode.NT_LINK_TITLE, 0);
		assertPrintedWithoutRtd("[http://x T]", WtNode.NT_EXTERNAL_LINK, 0);
		assertPrintedWithoutRtd("[[File:X.png|upright|Caption]]", WtNode.NT_LINK_TITLE, 0);
		assertPrintedWithoutRtd("[[File:X.png|upright|Caption]]", WtNode.NT_LINK_OPTION_KEYWORD, 0);
		assertPrintedWithoutRtd("[[File:X.png|{{T}}|x]]", WtNode.NT_TEMPLATE, 0);
		assertPrintedWithoutRtd("<span {{a}} title=\"x\">y</span>", WtNode.NT_TEMPLATE, 0);
		assertPrintedWithoutRtd("{{T|a|b=c}}", WtNode.NT_TEMPLATE_ARGUMENT, 1);
		assertPrintedWithoutRtd("-{zh-hans:a;zh-hant:b}-", WtNode.NT_LCT_RULE, 1);

		// The first item only opens the nested list
		assertPrintedWithoutRtd("*# a", WtNode.NT_LIST_ITEM, 1);
		assertPrintedWithoutRtd("*: a", WtNode.NT_DEFINITION_LIST_DEF, 0);
		assertPrintedWithoutRtd("* a\n** b\n* c", WtNode.NT_LIST_ITEM, 1);
	}

	// =========================================================================

	/**
	 * Removes the round-trip data of the node of the given type with the given
	 * index and checks that the wikitext is still reproduced.
	 */
	private static void assertPrintedWithoutRtd(
			String wikitext,
			int nodeType,
			int index) throws Exception
	{
		WtNode page = parse(wikitext);
		WtNode node = find(page, nodeType, index);
		assertNotNull(node.getRtd());
		node.clearRtd();

		// The pretty printer puts a list on a line of its own
		String printed = WtRtDataPrettyPrinter.print(page).replaceFirst("^\n+", "");
		assertEquals(wikitext, printed);
	}

	private static WtNode parse(String wikitext) throws Exception
	{
		return new NonExpandingParser(true, true, false).parseArticle(wikitext, "test");
	}

	private static void clearRtd(WtNode node)
	{
		if (!(node instanceof WtEmptyImmutableNode))
			node.clearRtd();
		for (WtNode child : node)
			clearRtd(child);
	}

	private static String cleanedAst(String wikitext) throws Exception
	{
		NonExpandingParser parser = new NonExpandingParser();
		parser.addVisitor(new CleanupAst((ParserConfig) parser.getConfig()));
		return WtAstPrinter.print(parser.parseArticle(wikitext, "test"));
	}

	private static WtNode find(WtNode page, int nodeType, int index)
	{
		WtNode found = find(page, nodeType, new int[] { index });
		assertNotNull(found);
		return found;
	}

	private static WtNode find(WtNode node, int nodeType, int[] index)
	{
		if (node.isNodeType(nodeType) && index[0]-- == 0)
			return node;
		for (WtNode child : node)
		{
			WtNode found = find(child, nodeType, index);
			if (found != null)
				return found;
		}
		return null;
	}
}
