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
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtRedirect;
import org.sweble.wikitext.parser.utils.NonExpandingParser;

/**
 * Like MediaWiki (WikitextContentHandler::extractRedirectTargetAndText() and
 * Title::isValidRedirectTarget()) a page is only a redirect if the target is
 * a valid title.
 */
public class RedirectTest
{
	@Test
	public void testRedirectToValidTarget() throws Exception
	{
		assertEquals("Main Page", parseRedirect("#REDIRECT [[Main Page]]").getTarget().getAsString());
		assertEquals("File:A.png", parseRedirect("#REDIRECT [[File:A.png]]").getTarget().getAsString());
		assertEquals("A#Section", parseRedirect("#REDIRECT [[A#Section]]").getTarget().getAsString());
	}

	@Test
	public void testRedirectToInvalidTargetIsNoRedirect() throws Exception
	{
		String[] targets = {
				"~~~",
				"A~~~~B",
				"File:",
				"File: ",
				"#Section",
				"A&#xD800;" };

		for (String target : targets)
		{
			NonExpandingParser parser = new NonExpandingParser();
			WtNode page = parser.parseArticle("#REDIRECT [[" + target + "]]", "title");
			assertNull(target, find(page));
		}
	}

	// =========================================================================

	private WtRedirect parseRedirect(String wikitext) throws Exception
	{
		NonExpandingParser parser = new NonExpandingParser();
		WtRedirect redirect = find(parser.parseArticle(wikitext, "title"));
		assertNotNull("No redirect found", redirect);
		return redirect;
	}

	private WtRedirect find(WtNode node)
	{
		if (node instanceof WtRedirect)
			return (WtRedirect) node;
		for (WtNode child : node)
		{
			WtRedirect redirect = find(child);
			if (redirect != null)
				return redirect;
		}
		return null;
	}
}
