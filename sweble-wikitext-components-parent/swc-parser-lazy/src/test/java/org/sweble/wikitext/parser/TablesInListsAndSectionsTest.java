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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.sweble.wikitext.parser.nodes.WtDefinitionListDef;
import org.sweble.wikitext.parser.nodes.WtListItem;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtSection;
import org.sweble.wikitext.parser.nodes.WtTable;
import org.sweble.wikitext.parser.nodes.WtTableCell;
import org.sweble.wikitext.parser.nodes.WtText;
import org.sweble.wikitext.parser.nodes.WtUnorderedList;
import org.sweble.wikitext.parser.utils.NonExpandingParser;

/**
 * Issue #167: Unclosed tables in list items and at the end of section bodies.
 * Like MediaWiki the line after the opening line of a table can start a list
 * or a heading. Content in front of the first table row is moved in front of
 * the table (HTML5 foster parenting), content in a table cell stays in the
 * cell, just like in MediaWiki's output.
 */
public class TablesInListsAndSectionsTest
{
	@Test
	public void testListItemsInFrontOfFirstRowOfIndentedTable() throws Exception
	{
		WtNode ast = parse("* a\n:{|\n* b\n* c");

		WtDefinitionListDef dd = find(ast, WtDefinitionListDef.class).get(0);
		WtTable table = find(dd, WtTable.class).get(0);

		// Both items form one list, which is foster parented into the
		// definition in front of the table
		List<WtUnorderedList> lists = find(dd, WtUnorderedList.class);
		assertEquals(1, lists.size());
		assertEquals(2, find(lists.get(0), WtListItem.class).size());
		assertTrue(indexOf(dd, lists.get(0)) < indexOf(dd, table));
		assertTrue(find(table, WtListItem.class).isEmpty());

		assertFalse(textOf(ast).contains("*"));
	}

	@Test
	public void testDefinitionsInFrontOfFirstRowOfIndentedTable() throws Exception
	{
		WtNode ast = parse(": a\n:{|\n: b\n: c");
		assertFalse(textOf(ast).contains(":"));
		assertEquals(4, find(ast, WtDefinitionListDef.class).size());
	}

	@Test
	public void testListItemsInCellOfIndentedTableStayInTheCell() throws Exception
	{
		// MediaWiki: <ul><li>a</li></ul><dl><dd><table><tr><td>x
		//   <ul><li>b</li><li>c</li></ul></td></tr></table></dd></dl>
		WtNode ast = parse("* a\n:{|\n|x\n* b\n* c");

		WtTableCell cell = find(ast, WtTableCell.class).get(0);
		assertEquals(2, find(cell, WtListItem.class).size());
		assertEquals(3, find(ast, WtListItem.class).size());
	}

	@Test
	public void testUnclosedTableAtEndOfSectionBodyDoesNotSwallowNextSection() throws Exception
	{
		WtNode ast = parse("== S1 ==\n{|\n== S2 ==\ny");

		List<WtSection> sections = find(ast, WtSection.class);
		assertEquals(2, sections.size());
		assertFalse(textOf(ast).contains("=="));

		WtSection s2 = sections.get(1);
		assertEquals(" S2 ", textOf(s2.getHeading()));
		assertTrue(textOf(s2.getBody()).contains("y"));

		// The next section is not part of the table
		WtTable table = find(ast, WtTable.class).get(0);
		assertTrue(find(table, WtSection.class).isEmpty());
	}

	@Test
	public void testUnclosedTableAtEndOfLastSectionStaysInTheSection() throws Exception
	{
		WtNode ast = parse("== S1 ==\nx\n{|\n");

		WtSection s1 = find(ast, WtSection.class).get(0);
		assertNotNull(find(s1.getBody(), WtTable.class).get(0));
	}

	@Test
	public void testSectionInCellOfUnclosedTableStaysInTheCell() throws Exception
	{
		// MediaWiki: <h2>S1</h2><table><tr><td>x<h2>S2</h2><p>y</p></td></tr></table>
		WtNode ast = parse("== S1 ==\n{|\n|x\n== S2 ==\ny");

		WtTableCell cell = find(ast, WtTableCell.class).get(0);
		assertEquals(1, find(cell, WtSection.class).size());
		assertNull(findTextContaining(ast, "=="));
	}

	// =========================================================================

	private static WtNode parse(String wikitext) throws Exception
	{
		NonExpandingParser parser = new NonExpandingParser(
				true /*warningsEnabled*/,
				true /*gatherRtd*/,
				false /*autoCorrect*/);
		return parser.parseArticle(wikitext, "Test");
	}

	private static <T extends WtNode> List<T> find(WtNode node, Class<T> clazz)
	{
		List<T> result = new ArrayList<T>();
		find(node, clazz, result);
		return result;
	}

	private static <T extends WtNode> void find(WtNode node, Class<T> clazz, List<T> result)
	{
		for (WtNode child : node)
		{
			if (clazz.isInstance(child))
				result.add(clazz.cast(child));
			find(child, clazz, result);
		}
	}

	/**
	 * @return The index of the child of {@code parent} that is or contains
	 *         {@code node}.
	 */
	private static int indexOf(WtNode parent, WtNode node)
	{
		for (int i = 0; i < parent.size(); ++i)
		{
			WtNode child = parent.get(i);
			if (child == node || find(child, node.getClass()).contains(node))
				return i;
		}
		return -1;
	}

	private static WtText findTextContaining(WtNode node, String s)
	{
		for (WtText text : find(node, WtText.class))
		{
			if (text.getContent().contains(s))
				return text;
		}
		return null;
	}

	private static String textOf(WtNode node)
	{
		StringBuilder sb = new StringBuilder();
		for (WtText text : find(node, WtText.class))
			sb.append(text.getContent());
		return sb.toString();
	}
}
