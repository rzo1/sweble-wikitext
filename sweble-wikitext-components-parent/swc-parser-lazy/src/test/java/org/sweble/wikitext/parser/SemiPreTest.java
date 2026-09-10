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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtSemiPre;
import org.sweble.wikitext.parser.utils.NonExpandingParser;

/**
 * A line containing a block element is not a semi-pre line. Like MediaWiki
 * (BlockLevelPass) the element names are matched case-insensitively and have
 * to be followed by a word boundary.
 */
public class SemiPreTest
{
	@Test
	public void testLineWithBlockElementIsNotSemiPre() throws Exception
	{
		String[] lines = {
				" a <div>b</div>",
				" a <p> b",
				" a </p> b",
				" a <table> b",
				" a <td> b",
				" a </li> b",
				" a <hr/> b",
				" a <center> b",
				" a <dd> b",
				" a <figure> b" };

		for (String line : lines)
			assertFalse(line, hasSemiPre(line));
	}

	@Test
	public void testBlockElementsAreMatchedCaseInsensitively() throws Exception
	{
		String[] lines = {
				" a <DIV>b</DIV>",
				" a <Div> b",
				" a <P> b",
				" a </P> b",
				" a <TABLE> b",
				" a <Td> b",
				" a <HR/> b",
				" a </Center> b" };

		for (String line : lines)
			assertFalse(line, hasSemiPre(line));
	}

	@Test
	public void testBlockElementsRequireWordBoundary() throws Exception
	{
		String[] lines = {
				" a <person> b",
				" a <poem2> b",
				" a <tray> b",
				" a <preface> b",
				" a <h1x> b",
				" a <p_x> b",
				" a <divx> b",
				" a <thing> b",
				" a <centered> b" };

		for (String line : lines)
			assertTrue(line, hasSemiPre(line));
	}

	@Test
	public void testLineWithoutBlockElementIsSemiPre() throws Exception
	{
		assertTrue(hasSemiPre(" a <span>b</span>"));
		assertTrue(hasSemiPre(" a < p b"));
	}

	// =========================================================================

	private boolean hasSemiPre(String wikitext) throws Exception
	{
		NonExpandingParser parser = new NonExpandingParser();
		return find(parser.parseArticle(wikitext, "title"));
	}

	private boolean find(WtNode node)
	{
		if (node instanceof WtSemiPre)
			return true;
		for (WtNode child : node)
		{
			if (find(child))
				return true;
		}
		return false;
	}
}
