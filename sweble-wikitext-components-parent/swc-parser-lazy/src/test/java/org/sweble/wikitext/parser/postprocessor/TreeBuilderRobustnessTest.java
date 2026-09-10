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

package org.sweble.wikitext.parser.postprocessor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;
import org.sweble.wikitext.parser.ParserConfig;
import org.sweble.wikitext.parser.WikitextPostprocessor;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtNodeList;
import org.sweble.wikitext.parser.nodes.WtParsedWikitextPage;
import org.sweble.wikitext.parser.nodes.WtSection;
import org.sweble.wikitext.parser.nodes.WtText;
import org.sweble.wikitext.parser.nodes.WtXmlElement;
import org.sweble.wikitext.parser.nodes.WtXmlStartTag;
import org.sweble.wikitext.parser.utils.NonExpandingParser;
import org.sweble.wikitext.parser.utils.NonExpandingParserConfig;
import org.sweble.wikitext.parser.utils.NonPostproParser;
import org.sweble.wikitext.parser.utils.WtRtDataPrinter;

/**
 * Regression tests for the HTML tree construction of the post-processor
 * (TicksAnalyzer and TreeBuilder).
 */
public class TreeBuilderRobustnessTest
{
	// =========================================================================
	// hasRows

	@Test
	public void testTemplateInNativeTableWithoutTransclusionFostering() throws Exception
	{
		WtParsedWikitextPage page = postprocessWithoutTransclusionFostering(
				"{|\n{{t}}\n|X\n|}");

		assertEquals(1, findAll(page, WtNode.NT_TABLE_CELL).size());
		assertEquals("X", upperCaseLetters(textOf(page)));
	}

	@Test
	public void testTemplateInNativeRowWithoutTransclusionFostering() throws Exception
	{
		WtParsedWikitextPage page = postprocessWithoutTransclusionFostering(
				"{|\n|-\n{{t}}\n|X\n|}");

		assertEquals(1, findAll(page, WtNode.NT_TABLE_CELL).size());
		assertEquals("X", upperCaseLetters(textOf(page)));
	}

	@Test
	public void testTableSectionTagFollowedByNativeCell() throws Exception
	{
		WtParsedWikitextPage page = postprocess("{|\n<thead>\n|X\n|}");

		assertEquals(1, findAll(page, WtNode.NT_TABLE_CELL).size());
		assertEquals("X", upperCaseLetters(textOf(page)));
	}

	// =========================================================================
	// Stray table section tags in body

	@Test
	public void testStrayTableSectionTagsInBodyAreIgnored() throws Exception
	{
		String[] names = { "thead", "tbody", "tfoot", "col", "colgroup" };
		for (String name : names)
		{
			String[] inputs = {
					"<" + name + ">A",
					"<" + name + "/>A",
					"<" + name + ">A</" + name + ">B",
					"<" + name + "><td>A</td></" + name + ">B",
					"<" + name + "><tr><td>A</td></tr></" + name + ">B" };

			for (String input : inputs)
			{
				WtParsedWikitextPage page = postprocess(input);
				assertTrue(input, findElements(page, name).isEmpty());
				assertEquals(input, upperCaseLetters(input), upperCaseLetters(textOf(page)));
			}
		}
	}

	@Test
	public void testStrayTableSectionTagsInBodyKeepRoundTripData() throws Exception
	{
		String input = "<thead>A</thead><col>B<colgroup/>C";
		assertEquals(input, WtRtDataPrinter.print(postprocess(input)));
	}

	@Test
	public void testStrayTableSectionTagsInsideFormatting() throws Exception
	{
		String[] inputs = {
				"<b><thead>A</b>B</thead>C",
				"<thead><b>A</thead>B</b>C",
				"<tbody>A<b>B</tbody>C</b>D",
				"<i><tfoot>A<table>B</tfoot>C</table>D</i>E",
				"<colgroup><col>A<table><td>B</colgroup>C" };

		for (String input : inputs)
		{
			WtParsedWikitextPage page = postprocess(input);
			assertEquals(input, upperCaseLetters(input), upperCaseLetters(textOf(page)));
		}
	}

	// =========================================================================
	// Empty table tags

	@Test
	public void testEmptyTableTagInBody() throws Exception
	{
		WtParsedWikitextPage page = postprocess("<table/>A");

		assertEquals(1, findElements(page, "table").size());
		assertEquals("A", upperCaseLetters(textOf(page)));
	}

	@Test
	public void testEmptyTableTagInsideTableClosesTheTable() throws Exception
	{
		String[] inputs = {
				"<table><table/>A",
				"<table><tr><td>A<table/>B",
				"{|\n|A<table/>B\n|}",
				"<table><caption>A<table/>B",
				"<table><colgroup><table/>A" };

		for (String input : inputs)
		{
			WtParsedWikitextPage page = postprocess(input);
			assertEquals(input, upperCaseLetters(input), upperCaseLetters(textOf(page)));
		}
	}

	@Test
	public void testFosterParentOfFosteredTable() throws Exception
	{
		// The inner table is foster parented in front of the outer table but
		// sits on top of the outer table on the stack of open elements.
		String input = "<table><b><table>A";
		WtParsedWikitextPage page = postprocess(input);
		assertEquals("A", upperCaseLetters(textOf(page)));
	}

	// =========================================================================
	// Unclosed tables

	@Test
	public void testTextAfterUnclosedTableIsNotLost() throws Exception
	{
		String[] inputs = {
				"A<table>B",
				"* B<table>C",
				"# B<table>C",
				"; B<table>C",
				": B<table>C",
				"; B<table>C\n: D<table>E",
				"* A\n** B<table>C\n* D",
				"==S==\n<table>C",
				"'''B<table>C'''D",
				"<b>B<table>C</b>D" };

		for (String input : inputs)
		{
			WtParsedWikitextPage page = postprocess(input);
			assertEquals(input, upperCaseLetters(input), upperCaseLetters(textOf(page)));
		}
	}

	// =========================================================================
	// Ticks

	@Test
	public void testHtmlBoldDoesNotChangeTickState() throws Exception
	{
		WtParsedWikitextPage page = postprocess("'''A<b>B</b>C'''D");

		assertFalse(textOfFormatting(page, WtNode.NT_BOLD, "b").contains("D"));
		assertEquals("ABCD", upperCaseLetters(textOf(page)));
	}

	@Test
	public void testHtmlItalicsDoesNotChangeTickState() throws Exception
	{
		WtParsedWikitextPage page = postprocess("''A<i>B</i>C''D");

		assertFalse(textOfFormatting(page, WtNode.NT_ITALICS, "i").contains("D"));
		assertEquals("ABCD", upperCaseLetters(textOf(page)));
	}

	@Test
	public void testUnclosedHtmlBoldDoesNotSuppressClosingTicksAtEndOfLine() throws Exception
	{
		// MediaWiki closes the bold ticks at the end of the line, the <b>
		// element stays open.
		WtParsedWikitextPage page = postprocess("<b>A'''B\nC");

		List<WtNode> bold = findAll(page, WtNode.NT_BOLD);
		assertEquals(1, bold.size());
		assertEquals("B", upperCaseLetters(textOf(bold.get(0))));
		assertEquals("ABC", upperCaseLetters(textOf(findElements(page, "b").get(0))));
	}

	// =========================================================================
	// Adoption agency

	@Test
	public void testAdopterElementDoesNotRepeatStartTag() throws Exception
	{
		String[] inputs = {
				"<div><b>A<div>B</b>C</div></div>",
				"<div><b class=\"x\">A<div>B</b>C</div></div>",
				"<div><i>A<b>B<div>C</i>D</b>E</div></div>" };

		for (String input : inputs)
			assertEquals(input, WtRtDataPrinter.print(postprocess(input)));
	}

	// =========================================================================
	// Stray </table>

	@Test
	public void testNativeTableElementsAfterStrayTableEndTagKeepTheirContent() throws Exception
	{
		String input = "{|\n|A</table>\n|B [[C]]\n|-\n|+ D [[E]]\n! F [[G]]\n|H ''I''\n|}";
		WtParsedWikitextPage page = postprocess(input);

		assertEquals(3, findAll(page, WtNode.NT_INTERNAL_LINK).size());
		assertEquals(1, findAll(page, WtNode.NT_ITALICS).size());
		for (WtNode text : findAll(page, WtNode.NT_TEXT))
			assertFalse(((WtText) text).getContent().contains("[["));
		assertEquals(upperCaseLetters(input), upperCaseLetters(textOf(page)));
		assertEquals(input, WtRtDataPrinter.print(page));
	}

	// =========================================================================
	// Tables in section headings

	@Test
	public void testTableInSectionHeadingIsClosedAtEndOfHeading() throws Exception
	{
		String input = "==<table>A==\nB\n==C==\nD";
		WtParsedWikitextPage page = postprocess(input);

		assertEquals(2, page.size());
		assertTrue(page.get(0) instanceof WtSection);
		assertTrue(page.get(1) instanceof WtSection);
		assertTrue(findAll(((WtSection) page.get(0)).getHeading(), WtNode.NT_SECTION).isEmpty());
		assertEquals("ABCD", upperCaseLetters(textOf(page)));
	}

	@Test
	public void testTableRowInSectionHeadingIsClosedAtEndOfHeading() throws Exception
	{
		String input = "==<table><tr><td>A==\nB\n==C==\nD";
		WtParsedWikitextPage page = postprocess(input);

		assertEquals(2, page.size());
		assertTrue(findAll(((WtSection) page.get(0)).getHeading(), WtNode.NT_SECTION).isEmpty());
		assertEquals("ABCD", upperCaseLetters(textOf(page)));
	}

	// =========================================================================
	// Attributes

	@Test
	public void testIsSameAttributesChecksAllAttributes() throws Exception
	{
		WtNodeList a0 = attributesOf("<b hidden class=\"x\">");
		WtNodeList a1 = attributesOf("<b hidden class=\"y\">");
		WtNodeList a2 = attributesOf("<b hidden class=\"x\">");
		WtNodeList a3 = attributesOf("<b class=\"x\" hidden>");

		assertFalse(TreeBuilder.isSameAttributes(a0, a1));
		assertFalse(TreeBuilder.isSameAttributes(a1, a0));
		assertTrue(TreeBuilder.isSameAttributes(a0, a2));
		assertTrue(TreeBuilder.isSameAttributes(a0, a3));
	}

	@Test
	public void testIsSameAttributesDistinguishesMissingAttributes() throws Exception
	{
		WtNodeList a0 = attributesOf("<b hidden>");
		WtNodeList a1 = attributesOf("<b title>");
		WtNodeList a2 = attributesOf("<b>");

		assertFalse(TreeBuilder.isSameAttributes(a0, a1));
		assertFalse(TreeBuilder.isSameAttributes(a0, a2));
		assertFalse(TreeBuilder.isSameAttributes(a2, a0));
	}

	@Test
	public void testNoahsArkClauseKeepsDistinctFormattingElements() throws Exception
	{
		// Four <b> elements that only differ in their second attribute must
		// all be reconstructed in the next paragraph.
		String input = "<b hidden class=a><b hidden class=b><b hidden class=c><b hidden class=d>A\n\nB";
		WtParsedWikitextPage page = postprocess(input);

		assertEquals(8, findElements(page, "b").size());
		assertEquals("AB", upperCaseLetters(textOf(page)));
	}

	// =========================================================================
	// Performance

	@Test(timeout = 10000)
	public void testManyDistinctFormattingElementsAreNotQuadratic() throws Exception
	{
		// Used to take about a minute (Noah's Ark clause)
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 8000; ++i)
			sb.append("<b class=c").append(i).append('>');
		sb.append('X');

		WtParsedWikitextPage page = postprocess(sb.toString());
		assertEquals(8000, findElements(page, "b").size());
	}

	@Test(timeout = 5000)
	public void testDeeplyNestedBlockElementsAreNotQuadratic() throws Exception
	{
		// Used to take about ten seconds (p in button scope checks)
		StringBuilder sb = new StringBuilder("<p><table><tr><td>");
		for (int i = 0; i < 30000; ++i)
			sb.append("<div>");
		sb.append('X');

		WtParsedWikitextPage page = postprocess(sb.toString());
		assertEquals(30000, findElements(page, "div").size());
	}

	@Test
	public void testDepthOfTreeIsLimited() throws Exception
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 3000; ++i)
			sb.append("<span>");
		sb.append('X');
		for (int i = 0; i < 3000; ++i)
			sb.append("</span>");
		sb.append('Y');

		WtParsedWikitextPage page = postprocess(sb.toString());

		assertTrue(elementDepth(page) <= TreeBuilder.MAX_TREE_DEPTH);
		assertEquals(3000, findElements(page, "span").size());
		assertEquals("XY", upperCaseLetters(textOf(page)));

		// Deep trees used to overflow the stack of recursive visitors. The
		// elements beyond the maximum depth are siblings, therefore their end
		// tags are printed in a different order.
		String printed = WtRtDataPrinter.print(page);
		assertEquals(sb.length(), printed.length());
		assertTrue(printed.startsWith("<span><span><span>"));
	}

	// =========================================================================
	// Fuzzing

	private static final String[] FUZZ_TOKENS = {
			"<table>", "</table>", "<table/>",
			"<thead>", "</thead>", "<tbody>", "</tbody>", "<tfoot>", "</tfoot>",
			"<col>", "<col/>", "<colgroup>", "</colgroup>",
			"<caption>", "</caption>", "<tr>", "</tr>", "<td>", "</td>", "<th>",
			"<b>", "</b>", "<i>", "</i>", "<b class=x>", "<b hidden class=y>",
			"<p>", "</p>", "<div>", "</div>",
			"''", "'''", "{{t}}", "<!-- c -->",
			"\n", "\n\n", "\n* ", "\n# ", "\n; ", "\n: ",
			"\n{|\n", "\n|", "\n|-\n", "\n|+ ", "\n!", "\n|}\n", "\n==", "==\n",
			"[[l|", "]]" };

	@Test
	public void testFuzzedTableAndFormattingMarkup() throws Exception
	{
		Random random = new Random(145);
		for (int i = 0; i < 1500; ++i)
		{
			StringBuilder sb = new StringBuilder();
			int count = 1 + random.nextInt(14);
			for (int j = 0; j < count; ++j)
			{
				if (random.nextInt(3) == 0)
					sb.append((char) ('A' + random.nextInt(26)));
				else
					sb.append(FUZZ_TOKENS[random.nextInt(FUZZ_TOKENS.length)]);
			}

			String input = sb.toString();
			WtParsedWikitextPage page;
			try
			{
				page = postprocess(input);
			}
			catch (Exception e)
			{
				throw new AssertionError("Post-processing failed for: " + escape(input), e);
			}

			assertEquals(
					"Text lost for: " + escape(input),
					sortedUpperCaseLetters(input),
					sortedUpperCaseLetters(textOf(page)));
		}
	}

	// =========================================================================

	private static WtParsedWikitextPage postprocess(String wikitext) throws Exception
	{
		NonPostproParser parser = new NonPostproParser();
		WtNode parsed = parser.parseArticle(wikitext, "-");
		WikitextPostprocessor postp = new WikitextPostprocessor(
				(ParserConfig) parser.getConfig());
		return (WtParsedWikitextPage) postp.postprocess(parsed, "-");
	}

	private static WtParsedWikitextPage postprocessWithoutTransclusionFostering(
			String wikitext) throws Exception
	{
		NonExpandingParser parser = new NonExpandingParser();
		NonExpandingParserConfig config = (NonExpandingParserConfig) parser.getConfig();
		config.setFosterParenting(true);
		config.setFosterParentingForTransclusions(false);
		return (WtParsedWikitextPage) parser.parseArticle(wikitext, "-");
	}

	private static WtNodeList attributesOf(String startTag) throws Exception
	{
		NonPostproParser parser = new NonPostproParser();
		WtNode parsed = parser.parseArticle(startTag, "-");
		List<WtNode> tags = findAll(parsed, WtNode.NT_XML_START_TAG);
		assertEquals(1, tags.size());
		return ((WtXmlStartTag) tags.get(0)).getXmlAttributes();
	}

	private static List<WtNode> findAll(WtNode root, int nodeType)
	{
		List<WtNode> result = new ArrayList<WtNode>();
		findAll(root, nodeType, result);
		return result;
	}

	private static void findAll(WtNode node, int nodeType, List<WtNode> result)
	{
		if (node.isNodeType(nodeType))
			result.add(node);
		for (WtNode child : node)
			findAll(child, nodeType, result);
	}

	private static List<WtNode> findElements(WtNode root, String name)
	{
		List<WtNode> result = new ArrayList<WtNode>();
		for (WtNode e : findAll(root, WtNode.NT_XML_ELEMENT))
		{
			if (((WtXmlElement) e).getName().equalsIgnoreCase(name))
				result.add(e);
		}
		return result;
	}

	private static String textOfFormatting(WtNode root, int nodeType, String name)
	{
		StringBuilder sb = new StringBuilder();
		for (WtNode n : findAll(root, nodeType))
			sb.append(textOf(n));
		for (WtNode n : findElements(root, name))
			sb.append(textOf(n));
		return sb.toString();
	}

	/**
	 * The number of nested XML elements.
	 */
	private static int elementDepth(WtNode node)
	{
		int max = 0;
		for (WtNode child : node)
			max = Math.max(max, elementDepth(child));
		return node.isNodeType(WtNode.NT_XML_ELEMENT) ? max + 1 : max;
	}

	private static String textOf(WtNode node)
	{
		StringBuilder sb = new StringBuilder();
		textOf(node, sb);
		return sb.toString();
	}

	private static void textOf(WtNode node, StringBuilder sb)
	{
		switch (node.getNodeType())
		{
			case WtNode.NT_TEXT:
				sb.append(((WtText) node).getContent());
				break;
			case WtNode.NT_XML_ATTRIBUTES:
				break;
			default:
				for (WtNode child : node)
					textOf(child, sb);
				break;
		}
	}

	private static String upperCaseLetters(String text)
	{
		StringBuilder sb = new StringBuilder();
		for (char ch : text.toCharArray())
		{
			if (ch >= 'A' && ch <= 'Z')
				sb.append(ch);
		}
		return sb.toString();
	}

	private static String sortedUpperCaseLetters(String text)
	{
		char[] letters = upperCaseLetters(text).toCharArray();
		java.util.Arrays.sort(letters);
		return new String(letters);
	}

	private static String escape(String text)
	{
		return "\"" + text.replace("\n", "\\n") + "\"";
	}
}
