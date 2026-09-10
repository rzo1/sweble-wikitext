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

package org.sweble.wikitext.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtText;
import org.sweble.wikitext.parser.utils.WtRtDataPrinter;

import de.fau.cs.osr.ptk.common.Warning;

/**
 * Template expansion must be protected against runaway templates and
 * redirects like in MediaWiki.
 */
public class ExpansionVisitorTest
{
	private final WikiConfigImpl config = DefaultConfigEnWp.generate();

	private final WtEngineImpl engine = new WtEngineImpl(config);

	private final MapCallback callback = new MapCallback();

	// =========================================================================
	// == Template loops

	@Test
	public void testTemplateLoopIgnoresFragment() throws Exception
	{
		// Every call adds another character to the fragment
		callback.add("Template:F", "x{{F#{{{1|}}}a|{{{1|}}}a}}");

		EngProcessedPage page = expand("{{F}}");

		assertOutput(
				"x<span class=\"error\">Template loop detected: [[Template:F]]</span>",
				page);
		assertHasWarning(page, "TemplateLoopWarning");
	}

	@Test
	public void testTemplateLoopIgnoresInitialColon() throws Exception
	{
		callback.add("Template:G", "g{{:Template:G}}");

		EngProcessedPage page = expand("{{G}}");

		assertOutput(
				"g<span class=\"error\">Template loop detected: [[Template:G]]</span>",
				page);
		assertHasWarning(page, "TemplateLoopWarning");
	}

	// =========================================================================
	// == forInclusion

	@Test
	public void testRedirectTargetOfPageExpandedForInclusionIsIncluded() throws Exception
	{
		callback.add("Target", "a<noinclude>b</noinclude><includeonly>c</includeonly>");

		EngProcessedPage page = expand("#REDIRECT [[Target]]", true);

		assertEquals("ac", textOf(page.getPage()));
	}

	@Test
	public void testRedirectTargetOfPageExpandedForViewingIsViewed() throws Exception
	{
		callback.add("Target", "a<noinclude>b</noinclude><includeonly>c</includeonly>");

		EngProcessedPage page = expand("#REDIRECT [[Target]]", false);

		assertEquals("ab", textOf(page.getPage()));
	}

	// =========================================================================
	// == Errors

	@Test
	public void testErrorsAreNotSwallowed() throws Exception
	{
		StackOverflowError error = new StackOverflowError();
		callback.add("Template:T", "{{Boom}}");
		callback.fail("Template:Boom", error);

		try
		{
			expand("{{T}}");
			fail("The error was swallowed");
		}
		catch (EngineException e)
		{
			assertSame(error, e.getCause());
		}
	}

	// =========================================================================

	/**
	 * Adds the templates {@code prefix1} to {@code prefixN}, each transcluding
	 * the next one.
	 */
	private void addTemplateChain(
			String prefix,
			int length,
			String text,
			String end)
	{
		for (int i = 1; i < length; ++i)
			callback.add("Template:" + prefix + i, text + "{{" + prefix + (i + 1) + "}}");
		callback.add("Template:" + prefix + length, end);
	}

	private EngProcessedPage expand(String wikitext) throws Exception
	{
		return expand(wikitext, false);
	}

	private EngProcessedPage expand(String wikitext, boolean forInclusion) throws Exception
	{
		PageId pageId = new PageId(PageTitle.make(config, "Test"), -1);

		return engine.expand(pageId, wikitext, forInclusion, callback);
	}

	private void assertExpansion(String expected, String wikitext) throws Exception
	{
		EngProcessedPage page = expand(wikitext);
		assertOutput(expected, page);
		assertNoWarnings(page);
	}

	private static void assertOutput(String expected, EngProcessedPage page)
	{
		assertEquals(expected, WtRtDataPrinter.print(page.getPage()));
	}

	/**
	 * Returns the content of all text nodes, leaving out ignored content like
	 * &lt;noinclude> sections which the printer would reproduce.
	 */
	private static String textOf(WtNode node)
	{
		StringBuilder b = new StringBuilder();
		appendText(node, b);
		return b.toString();
	}

	private static void appendText(WtNode node, StringBuilder b)
	{
		if (node instanceof WtText)
			b.append(((WtText) node).getContent());
		for (WtNode child : node)
			appendText(child, b);
	}

	private static void assertNoWarnings(EngProcessedPage page)
	{
		assertEquals(new ArrayList<Warning>(), new ArrayList<Warning>(page.getWarnings()));
	}

	private static void assertHasWarning(EngProcessedPage page, String type)
	{
		for (Warning w : page.getWarnings())
		{
			if (w.getClass().getSimpleName().equals(type))
				return;
		}
		fail("Expected a " + type + " but got: " + page.getWarnings());
	}

	// =========================================================================

	/**
	 * Serves pages from a map, keyed by their normalized full title, and
	 * counts how often each page was retrieved.
	 */
	private static final class MapCallback
			implements
				ExpansionCallback
	{
		private final Map<String, String> pages = new HashMap<String, String>();

		private final Map<String, Error> errors = new HashMap<String, Error>();

		private final List<String> retrieved = new ArrayList<String>();

		public void add(String title, String wikitext)
		{
			pages.put(title, wikitext);
		}

		public void fail(String title, Error error)
		{
			errors.put(title, error);
		}

		public int getRetrievalCount(String title)
		{
			int count = 0;
			for (String t : retrieved)
			{
				if (t.equals(title))
					++count;
			}
			return count;
		}

		@Override
		public FullPage retrieveWikitext(
				ExpansionFrame expansionFrame,
				PageTitle pageTitle)
		{
			String title = pageTitle.getNormalizedFullTitle();
			retrieved.add(title);

			Error error = errors.get(title);
			if (error != null)
				throw error;

			String wikitext = pages.get(title);
			if (wikitext == null)
				return null;

			return new FullPage(new PageId(pageTitle, -1), wikitext);
		}

		@Override
		public String fileUrl(PageTitle pageTitle, int width, int height)
		{
			return null;
		}
	}
}
