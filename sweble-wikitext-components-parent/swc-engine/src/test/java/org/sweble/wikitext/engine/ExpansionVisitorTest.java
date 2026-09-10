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
import static org.junit.Assert.assertTrue;
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
	// == Template depth

	@Test
	public void testTemplateDepthIsLimitedByDefault() throws Exception
	{
		addTemplateChain("L", 50, "", "end");

		EngProcessedPage page = expand("{{L1}}");

		assertOutput(
				"<span class=\"error\">Template recursion depth limit exceeded (40)</span>",
				page);
		assertHasWarning(page, "TemplateRecursionDepthWarning");
	}

	@Test
	public void testTemplateDepthLimitIsConfigurable() throws Exception
	{
		config.getEngineConfig().setMaxTemplateDepth(3);
		addTemplateChain("L", 5, "x", "end");

		EngProcessedPage page = expand("{{L1}}");

		assertOutput(
				"xxx<span class=\"error\">Template recursion depth limit exceeded (3)</span>",
				page);
		assertHasWarning(page, "TemplateRecursionDepthWarning");
	}

	@Test
	public void testTemplatesWithinDepthLimitAreExpanded() throws Exception
	{
		config.getEngineConfig().setMaxTemplateDepth(5);
		addTemplateChain("L", 5, "x", "end");

		assertExpansion("xxxxend", "{{L1}}");
	}

	// =========================================================================
	// == Post-expand include size

	private static final String OMITTED =
			"<!-- WARNING: template omitted, post-expand include size too large -->";

	@Test
	public void testPostExpandIncludeSizeIsLimited() throws Exception
	{
		// Each transclusion of T has size 11: 10 characters plus the list
		config.getEngineConfig().setMaxPostExpandIncludeSize(25);
		callback.add("Template:T", "0123456789");

		EngProcessedPage page = expand("{{T}}{{T}}{{T}}");

		assertOutput("01234567890123456789[[:Template:T]]" + OMITTED, page);
		assertHasWarning(page, "PostExpandIncludeSizeWarning");
	}

	@Test
	public void testTransclusionsAfterExceedingPostExpandIncludeSizeAreOmitted() throws Exception
	{
		// Each transclusion of T has size 11: 10 characters plus the list
		config.getEngineConfig().setMaxPostExpandIncludeSize(25);
		callback.add("Template:T", "0123456789");
		callback.add("Template:U", "u");

		EngProcessedPage page = expand("{{T}}{{T}}{{T}}{{U}}");

		assertOutput(
				"01234567890123456789[[:Template:T]]" + OMITTED + "[[:Template:U]]" + OMITTED,
				page);
		assertEquals(0, callback.getRetrievalCount("Template:U"));
	}

	@Test
	public void testNestedTransclusionsCountAtEveryLevel() throws Exception
	{
		config.getEngineConfig().setMaxPostExpandIncludeSize(15);
		callback.add("Template:Outer", "{{Inner}}");
		callback.add("Template:Inner", "0123456789");

		EngProcessedPage page = expand("{{Outer}}");

		assertOutput("[[:Template:Outer]]" + OMITTED, page);
		assertHasWarning(page, "PostExpandIncludeSizeWarning");
	}

	@Test(timeout = 10000)
	public void testExponentialTemplateIsCut() throws Exception
	{
		config.getEngineConfig().setMaxPostExpandIncludeSize(1000);

		// Every level transcludes the next level twice: 2^29 empty transclusions
		for (int i = 1; i < 30; ++i)
			callback.add("Template:L" + i, "{{L" + (i + 1) + "}}{{L" + (i + 1) + "}}");
		callback.add("Template:L30", "");

		EngProcessedPage page = expand("{{L1}}");

		assertHasWarning(page, "PostExpandIncludeSizeWarning");
	}

	@Test(timeout = 10000)
	public void testExponentialArgumentIsCut() throws Exception
	{
		config.getEngineConfig().setMaxPostExpandIncludeSize(1000);

		// Every call doubles its argument: 2^30 characters
		callback.add("Template:T", "{{{1}}}{{{1}}}");

		StringBuilder wikitext = new StringBuilder("x");
		for (int i = 0; i < 30; ++i)
			wikitext.insert(0, "{{T|").append("}}");

		EngProcessedPage page = expand(wikitext.toString());

		assertHasWarning(page, "PostExpandIncludeSizeWarning");
		assertOutput("[[:Template:T]]" + OMITTED, page);
	}

	// =========================================================================
	// == Parser functions that fetch pages (msgnw)

	@Test
	public void testMsgnwIsSubjectToTemplateDepthLimit() throws Exception
	{
		config.getEngineConfig().setMaxTemplateDepth(1);
		callback.add("Template:A", "a{{msgnw:B}}");
		callback.add("Template:B", "b");

		EngProcessedPage page = expand("{{A}}");

		assertOutput(
				"a<span class=\"error\">Template recursion depth limit exceeded (1)</span>",
				page);
		assertHasWarning(page, "TemplateRecursionDepthWarning");
		assertEquals(0, callback.getRetrievalCount("Template:B"));
	}

	@Test
	public void testMsgnwDetectsTemplateLoop() throws Exception
	{
		callback.add("Template:A", "a{{msgnw:A}}");

		EngProcessedPage page = expand("{{A}}");

		assertOutput(
				"a<span class=\"error\">Template loop detected: [[Template:A]]</span>",
				page);
		assertHasWarning(page, "TemplateLoopWarning");
	}

	@Test
	public void testMsgnwIsSubjectToPostExpandIncludeSize() throws Exception
	{
		config.getEngineConfig().setMaxPostExpandIncludeSize(15);
		callback.add("Template:Big", "0123456789012345678");

		EngProcessedPage page = expand("{{msgnw:Big}}");

		assertOutput("[[:Template:Big]]" + OMITTED, page);
		assertHasWarning(page, "PostExpandIncludeSizeWarning");
	}

	@Test
	public void testMsgnwCountsTowardsPostExpandIncludeSize() throws Exception
	{
		// The source of T has size 10, a transclusion of T size 11
		config.getEngineConfig().setMaxPostExpandIncludeSize(25);
		callback.add("Template:T", "0123456789");

		EngProcessedPage page = expand("{{msgnw:T}}{{T}}{{T}}");

		String output = WtRtDataPrinter.print(page.getPage());
		assertTrue(output, output.startsWith("<nowiki>0123456789</nowiki>0123456789[[:Template:T]]"));
		assertTrue(output, output.endsWith(OMITTED));
		assertHasWarning(page, "PostExpandIncludeSizeWarning");
	}

	// =========================================================================
	// == Redirects

	@Test
	public void testRedirectIsFollowed() throws Exception
	{
		callback.add("Template:R1", "#REDIRECT [[Template:R2]]");
		callback.add("Template:R2", "end");

		assertExpansion("end", "{{R1}}");
	}

	@Test
	public void testRedirectLoopIsDetected() throws Exception
	{
		config.getEngineConfig().setMaxRedirects(10);
		callback.add("Template:R1", "#REDIRECT [[Template:R2]]");
		callback.add("Template:R2", "#REDIRECT [[Template:R1]]");

		EngProcessedPage page = expand("{{R1}}");

		assertOutput("#REDIRECT [[Template:R1]]", page);
		assertHasWarning(page, "RedirectLoopWarning");
	}

	@Test
	public void testSelfRedirectIsDetected() throws Exception
	{
		callback.add("Template:R", "#REDIRECT [[Template:R]]");

		EngProcessedPage page = expand("{{R}}");

		assertOutput("#REDIRECT [[Template:R]]", page);
		assertHasWarning(page, "RedirectLoopWarning");
	}

	@Test
	public void testSelfRedirectOfExpandedPageIsDetected() throws Exception
	{
		callback.add("Test", "#REDIRECT [[Test]]");

		EngProcessedPage page = expand("#REDIRECT [[Test]]");

		assertOutput("#REDIRECT [[Test]]", page);
		assertHasWarning(page, "RedirectLoopWarning");
	}

	@Test
	public void testTwoRedirectsAreFollowedByDefault() throws Exception
	{
		// Like MediaWiki's Parser::statelessFetchTemplate()
		callback.add("Template:R1", "#REDIRECT [[Template:R2]]");
		callback.add("Template:R2", "#REDIRECT [[Template:R3]]");
		callback.add("Template:R3", "end");

		assertExpansion("end", "{{R1}}");
	}

	@Test
	public void testThirdRedirectIsNotFollowedByDefault() throws Exception
	{
		callback.add("Template:R1", "#REDIRECT [[Template:R2]]");
		callback.add("Template:R2", "#REDIRECT [[Template:R3]]");
		callback.add("Template:R3", "#REDIRECT [[Template:R4]]");
		callback.add("Template:R4", "end");

		EngProcessedPage page = expand("{{R1}}");

		assertOutput("#REDIRECT [[Template:R4]]", page);
		assertHasWarning(page, "RedirectLimitWarning");
		assertEquals(0, callback.getRetrievalCount("Template:R4"));
	}

	@Test
	public void testRedirectLimitIsConfigurable() throws Exception
	{
		config.getEngineConfig().setMaxRedirects(1);
		callback.add("Template:R1", "#REDIRECT [[Template:R2]]");
		callback.add("Template:R2", "#REDIRECT [[Template:R3]]");
		callback.add("Template:R3", "end");

		EngProcessedPage page = expand("{{R1}}");

		assertOutput("#REDIRECT [[Template:R3]]", page);
		assertHasWarning(page, "RedirectLimitWarning");
		assertEquals(0, callback.getRetrievalCount("Template:R3"));
	}

	@Test
	public void testRedirectsOfDifferentTransclusionsAreCountedSeparately() throws Exception
	{
		callback.add("Template:R", "#REDIRECT [[Template:T]]");
		callback.add("Template:T", "t{{S}}");
		callback.add("Template:S", "#REDIRECT [[Template:U]]");
		callback.add("Template:U", "u");

		assertExpansion("tu", "{{R}}");
	}

	@Test
	public void testRedirectDoesNotIncreaseTemplateDepth() throws Exception
	{
		config.getEngineConfig().setMaxTemplateDepth(2);
		callback.add("Template:R", "#REDIRECT [[Template:L1]]");
		callback.add("Template:L1", "x{{L2}}");
		callback.add("Template:L2", "y");

		assertExpansion("xy", "{{R}}");
	}

	// =========================================================================
	// == Template arguments

	@Test
	public void testUnusedArgumentIsNotExpanded() throws Exception
	{
		callback.add("Template:T", "[{{{1}}}]");

		EngProcessedPage page = expand("{{T|a|{{Missing}}}}");

		assertOutput("[a]", page);
		assertNoWarnings(page);
		assertEquals(0, callback.getRetrievalCount("Template:Missing"));
	}

	@Test
	public void testOverriddenArgumentIsNotExpanded() throws Exception
	{
		callback.add("Template:T", "[{{{1}}}]");

		EngProcessedPage page = expand("{{T|{{Missing}}|1=b}}");

		assertOutput("[b]", page);
		assertNoWarnings(page);
		assertEquals(0, callback.getRetrievalCount("Template:Missing"));
	}

	@Test
	public void testArgumentIsExpandedOnlyOnce() throws Exception
	{
		callback.add("Template:T", "{{{1}}}{{{1}}}");
		callback.add("Template:U", "u");

		assertExpansion("uu", "{{T|{{U}}}}");
		assertEquals(1, callback.getRetrievalCount("Template:U"));
	}

	@Test
	public void testNamedArgumentIsTrimmed() throws Exception
	{
		callback.add("Template:T", "[{{{a}}}]");
		callback.add("Template:U", "u");

		assertExpansion("[u]", "{{T| a = {{U}} }}");
	}

	@Test
	public void testPositionalArgumentIsNotTrimmed() throws Exception
	{
		callback.add("Template:T", "[{{{1}}}]");
		callback.add("Template:U", "u");

		assertExpansion("[ u ]", "{{T| {{U}} }}");
	}

	@Test
	public void testArgumentIsExpandedInCallingFrame() throws Exception
	{
		callback.add("Template:Outer", "{{Inner|{{{1}}}|{{{2|d}}}}}");
		callback.add("Template:Inner", "[{{{1}}}|{{{2}}}|{{{3|e}}}]");

		assertExpansion("[z|d|e]", "{{Outer|z}}");
	}

	@Test
	public void testArgumentsArePassedToRedirectTarget() throws Exception
	{
		callback.add("Template:R", "#REDIRECT [[Template:T]]");
		callback.add("Template:T", "[{{{1}}}]");

		assertExpansion("[a]", "{{R|a}}");
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
