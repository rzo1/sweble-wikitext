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

package org.sweble.wikitext.parser;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.utils.NonExpandingParser;
import org.sweble.wikitext.parser.utils.WtRtDataPrinter;

/**
 * A term and its definition on one line ("; term : definition") like
 * MediaWiki's BlockLevelPass (rzo1/sweble-wikitext#104).
 */
public class DefinitionListTest
{
	@Test
	public void testTermAndDefinitionOnOneLine() throws Exception
	{
		assertStructure("dl(dt(a)dd(b))", ";a:b");
		assertStructure("dl(dt( t )dd( d))", "; t : d");
	}

	@Test
	public void testOnlyFirstColonEndsTerm() throws Exception
	{
		assertStructure("dl(dt(a)dd(b:c))", ";a:b:c");
	}

	@Test
	public void testEmptyDefinition() throws Exception
	{
		assertStructure("dl(dt(a)dd())", ";a:");
	}

	@Test
	public void testNestedPrefixes() throws Exception
	{
		assertStructure("dl(dt(a)dd(dl(dt(b))))", ";;a:b");
		assertStructure("dl(dt(a)dd(dl(dt(b)dd(c))))", ";;a:b:c");
		assertStructure("ul(li(dl(dt(a)dd(b))))", "*;a:b");
		assertStructure("dl(dd(dl(dt(a)dd(b))))", ":;a:b");
		assertStructure("dl(dt(a)dd(ul(li(b))))", ";*a:b");
		assertStructure("dl(dt(dl(dd(b))))", ";:b");
		assertStructure("ol(li(dl(dt(a)dd(b)dt(c)dd(d))))", "#;a:b\n#;c:d");
	}

	@Test
	public void testFollowingLines() throws Exception
	{
		assertStructure("dl(dt(a)dt(b)dd(c))", ";a\n;b:c");
		assertStructure("dl(dt(a)dd(b)dd(c))", ";a:b\n:c");
		assertStructure("dl(dt(a)dd(dl(dt(b)dt(c))))", ";;a:b\n;;c");
	}

	@Test
	public void testDefinitionContinuingTermLevelClosesTerm() throws Exception
	{
		assertStructure("dl(dt(a)dd(ul(li(b))))", ";a\n:*b");
		assertStructure("dl(dt(a)dd(dl(dt(b))))", ";a\n:;b");
	}

	@Test
	public void testColonInMarkupDoesNotEndTerm() throws Exception
	{
		assertStructure("dl(dt([[a:b]])dd(c))", ";[[a:b]]:c");
		assertStructure("dl(dt([http://example.org/a:b x])dd(y))", ";[http://example.org/a:b x]:y");
		assertStructure("dl(dt(<span title=\"a:b\">x</span>)dd(y))", ";<span title=\"a:b\">x</span>:y");
		assertStructure("dl(dt({{T|a:b}})dd(c))", ";{{T|a:b}}:c");
		assertStructure("dl(dt(http://example.org/a:b))", ";http://example.org/a:b");
		assertStructure("dl(dt(x http://example.org/a:b))", ";x http://example.org/a:b");
		assertStructure("dl(dt(<!-- a:b -->c))", ";<!-- a:b -->c");
		assertStructure("dl(dt(a&#58;b))", ";a&#58;b");
	}

	@Test
	public void testColonInsideElementDoesNotEndTerm() throws Exception
	{
		assertStructure("dl(dt(<span>a:b</span>))", ";<span>a:b</span>");
		assertStructure("dl(dt(a<span>b</span>)dd(c))", ";a<span>b</span>:c");
		assertStructure("dl(dt(a<br>b)dd(c))", ";a<br>b:c");
		assertStructure("dl(dt(a</span>)dd(b))", ";a</span>:b");
	}

	@Test
	public void testColonInsideFormattingDoesNotEndTerm() throws Exception
	{
		assertStructure("dl(dt(''a:b''))", ";''a:b''");
		assertStructure("dl(dt('''a:b))", ";'''a:b");
		assertStructure("dl(dt(''a'')dd(b))", ";''a'':b");
	}

	@Test
	public void testOtherListsAreNotSplit() throws Exception
	{
		assertStructure("ul(li(a:b))", "*a:b");
		assertStructure("ol(li(a:b))", "#a:b");
		assertStructure("dl(dd(a:b))", ":a:b");
	}

	@Test
	public void testTermsAndDefinitionsAreRoundTripped() throws Exception
	{
		String[] wikitexts = {
				";a:b",
				"; t : d \n",
				";a:b:c\n;a:\n",
				";;a:b\n;;a:b:c\n",
				"*;a:b\n:;a:b\n;*a:b\n#;a:b\n#;c:d\n",
				";:b\n;a\n:*b\n;a\n:;b\n",
				";;a:b\n;;c\n",
				";[[a:b]]:c\n;[http://example.org/a:b x]:y\n",
				";<span title=\"a:b\">x</span>:y\n;{{T|a:b}}:c\n",
				";''a:b''\n;''a'':b\n;'''a:b\n",
				";<span>a:b</span>\n;a<br>b:c\n;a</span>:b\n",
				"<!-- x -->;a<!-- y -->:b\n\n; a : b \n: c\n",
				":{|\n| a:b\n|}\n;a:b\n", };

		for (String wikitext : wikitexts)
			assertEquals(wikitext, WtRtDataPrinter.print(parse(wikitext)));
	}

	// =========================================================================

	private static WtNode parse(String wikitext) throws Exception
	{
		return new NonExpandingParser().parseArticle(wikitext, "title");
	}

	private static void assertStructure(String expected, String wikitext) throws Exception
	{
		StringBuilder sb = new StringBuilder();
		printStructure(parse(wikitext), sb);
		assertEquals(wikitext, expected, sb.toString());
	}

	/**
	 * Prints lists and list items as "tag(...)". The content of list items
	 * which is not a list is printed as wikitext.
	 */
	private static void printStructure(WtNode node, StringBuilder sb)
	{
		String tag = getTag(node);
		if (tag == null)
		{
			for (WtNode child : node)
				printStructure(child, sb);
			return;
		}

		sb.append(tag).append('(');
		for (WtNode child : node)
		{
			if (getTag(child) != null)
				printStructure(child, sb);
			else
				sb.append(WtRtDataPrinter.print(child));
		}
		sb.append(')');
	}

	private static String getTag(WtNode node)
	{
		switch (node.getNodeType())
		{
			case WtNode.NT_DEFINITION_LIST:
				return "dl";
			case WtNode.NT_DEFINITION_LIST_TERM:
				return "dt";
			case WtNode.NT_DEFINITION_LIST_DEF:
				return "dd";
			case WtNode.NT_UNORDERED_LIST:
				return "ul";
			case WtNode.NT_ORDERED_LIST:
				return "ol";
			case WtNode.NT_LIST_ITEM:
				return "li";
			default:
				return null;
		}
	}
}
