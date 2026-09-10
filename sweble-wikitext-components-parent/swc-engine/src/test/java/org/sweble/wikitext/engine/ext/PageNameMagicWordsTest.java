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

package org.sweble.wikitext.engine.ext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.sweble.wikitext.engine.ExpansionCallback;
import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.FullPage;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.NamespaceCase;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.output.HtmlRenderer;
import org.sweble.wikitext.engine.output.HtmlRendererCallback;
import org.sweble.wikitext.engine.output.MediaInfo;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.engine.utils.UrlEncoding;
import org.sweble.wikitext.parser.nodes.WtUrl;
import org.sweble.wikitext.parser.utils.WtRtDataPrinter;

/**
 * Page name magic words (<code>{{PAGENAME}}</code> and friends),
 * transclusions with an initial colon and <code>#titleparts</code>.
 *
 * The expected values are what MediaWiki produces.
 */
public class PageNameMagicWordsTest
{
	private final WikiConfigImpl config = DefaultConfigEnWp.generate();

	private final WtEngineImpl engine = new WtEngineImpl(config);

	/**
	 * The pages that can be transcluded, by full title.
	 */
	private final Map<String, String> pages = new HashMap<String, String>();

	// =========================================================================
	// == Trimming of the name

	@Test
	public void testNameOfVariableIsTrimmed() throws Exception
	{
		pages.put("Template:PAGENAME", "template");

		assertEquals("Foo", expand("Foo", "{{PAGENAME }}"));
		assertEquals("Foo", expand("Foo", "{{ PAGENAME }}"));
		assertEquals("Foo", expand("Foo", "{{\nPAGENAME\n}}"));
		assertEquals("Talk:Foo", expand("Foo", "{{ TALKPAGENAME }}"));
		assertEquals("Foo", expand("Test", "{{ PAGENAME : foo }}"));
		assertEquals("|", expand("Test", "{{ ! }}"));
	}

	// =========================================================================
	// == BASEPAGENAME, ROOTPAGENAME, SUBPAGENAME

	@Test
	public void testBasepagenameStripsNamespaceAndLastSubpage() throws Exception
	{
		assertEquals("Foo", expand("User:Foo/Bar", "{{BASEPAGENAME}}"));
		assertEquals("Foo/Bar", expand("User:Foo/Bar/Baz", "{{BASEPAGENAME}}"));
		assertEquals("Foo", expand("User:Foo", "{{BASEPAGENAME}}"));
		assertEquals("A", expand("Talk:A/B", "{{BASEPAGENAME}}"));
		assertEquals("A", expand("Template:A/doc", "{{BASEPAGENAME}}"));
		assertEquals("A", expand("Portal:A/B", "{{BASEPAGENAME}}"));
		assertEquals("/Foo", expand("User:/Foo", "{{BASEPAGENAME}}"));
		assertEquals("Foo_bar", expand("User:Foo bar/Baz", "{{BASEPAGENAMEE}}"));
	}

	@Test
	public void testBasepagenameInNamespaceWithoutSubpages() throws Exception
	{
		assertEquals("Foo/Bar", expand("Foo/Bar", "{{BASEPAGENAME}}"));
		assertEquals("A/B", expand("File:A/B", "{{BASEPAGENAME}}"));
		assertEquals("A/B", expand("MediaWiki:A/B", "{{BASEPAGENAME}}"));
	}

	@Test
	public void testRootpagenameAndSubpagename() throws Exception
	{
		assertEquals("Foo", expand("User:Foo/Bar/Baz", "{{ROOTPAGENAME}}"));
		assertEquals("Baz", expand("User:Foo/Bar/Baz", "{{SUBPAGENAME}}"));
		assertEquals("Foo_bar", expand("User:Foo bar/Baz", "{{ROOTPAGENAMEE}}"));
		assertEquals("Baz_qux", expand("User:Foo/Baz qux", "{{SUBPAGENAMEE}}"));
		assertEquals("Foo/Bar/Baz", expand("Foo/Bar/Baz", "{{ROOTPAGENAME}}"));
		assertEquals("Foo/Bar/Baz", expand("Foo/Bar/Baz", "{{SUBPAGENAME}}"));
	}

	// =========================================================================
	// == Variables without argument

	@Test
	public void testVariablesReferToCurrentPage() throws Exception
	{
		String title = "User talk:Foo bar/Baz";

		assertEquals("User talk:Foo bar/Baz", expand(title, "{{FULLPAGENAME}}"));
		assertEquals("User_talk:Foo_bar/Baz", expand(title, "{{FULLPAGENAMEE}}"));
		assertEquals("Foo bar/Baz", expand(title, "{{PAGENAME}}"));
		assertEquals("Foo_bar/Baz", expand(title, "{{PAGENAMEE}}"));
		assertEquals("User talk", expand(title, "{{NAMESPACE}}"));
		assertEquals("User_talk", expand(title, "{{NAMESPACEE}}"));
		assertEquals("User talk:Foo bar/Baz", expand(title, "{{TALKPAGENAME}}"));
		assertEquals("User_talk:Foo_bar/Baz", expand(title, "{{TALKPAGENAMEE}}"));
		assertEquals("User:Foo bar/Baz", expand(title, "{{SUBJECTPAGENAME}}"));
		assertEquals("User:Foo_bar/Baz", expand(title, "{{SUBJECTPAGENAMEE}}"));
		assertEquals("User:Foo bar/Baz", expand(title, "{{ARTICLEPAGENAME}}"));
		assertEquals("Foo bar", expand(title, "{{BASEPAGENAME}}"));
		assertEquals("Foo bar", expand(title, "{{ROOTPAGENAME}}"));
		assertEquals("Baz", expand(title, "{{SUBPAGENAME}}"));
	}

	// =========================================================================
	// == Variables with argument

	@Test
	public void testPagenameHonoursArgument() throws Exception
	{
		assertEquals("Foo bar", expand("Test", "{{PAGENAME:foo_bar}}"));
		assertEquals("Foo_bar", expand("Test", "{{PAGENAMEE:foo bar}}"));
	}

	@Test
	public void testPageNameVariablesHonourArgument() throws Exception
	{
		assertEquals("User talk:Foo bar", expand("Test", "{{FULLPAGENAME:user talk:foo_bar}}"));
		assertEquals("Wikipedia:Foo", expand("Test", "{{FULLPAGENAME:WP:Foo}}"));
		assertEquals("User_talk:Foo_bar", expand("Test", "{{FULLPAGENAMEE:User talk:Foo bar}}"));
		assertEquals("User talk", expand("Test", "{{NAMESPACE:User talk:Foo}}"));
		assertEquals("User_talk", expand("Test", "{{NAMESPACEE:User talk:Foo}}"));
		assertEquals("", expand("Test", "{{NAMESPACE:Foo}}"));
		assertEquals("Talk:Foo", expand("Test", "{{TALKPAGENAME:Foo}}"));
		assertEquals("User talk:Foo", expand("Test", "{{TALKPAGENAME:User:Foo}}"));
		assertEquals("User_talk:Foo_bar", expand("Test", "{{TALKPAGENAMEE:User:Foo bar}}"));
		assertEquals("User:Foo", expand("Test", "{{SUBJECTPAGENAME:User talk:Foo}}"));
		assertEquals("Foo_bar", expand("Test", "{{SUBJECTPAGENAMEE:Talk:Foo bar}}"));
		assertEquals("Foo", expand("Test", "{{ARTICLEPAGENAME:Talk:Foo}}"));
		assertEquals("Foo", expand("Test", "{{BASEPAGENAME:User:Foo/Bar}}"));
		assertEquals("Foo_bar", expand("Test", "{{BASEPAGENAMEE:User:Foo bar/Baz}}"));
		assertEquals("A", expand("Test", "{{ROOTPAGENAME:User:A/B/C}}"));
		assertEquals("A", expand("Test", "{{ROOTPAGENAMEE:User:A/B/C}}"));
		assertEquals("C", expand("Test", "{{SUBPAGENAME:User:A/B/C}}"));
		assertEquals("C_d", expand("Test", "{{SUBPAGENAMEE:User:A/B/C d}}"));
	}

	@Test
	public void testArgumentOfPageNameVariableIsExpanded() throws Exception
	{
		pages.put("Template:Name", "user:foo/bar");

		assertEquals("Foo", expand("Test", "{{BASEPAGENAME:{{Name}}}}"));
	}

	@Test
	public void testPagesWithoutTalkPage() throws Exception
	{
		assertEquals("", expand("Test", "{{TALKPAGENAME:Special:Foo}}"));
		assertEquals("Special:Foo", expand("Test", "{{SUBJECTPAGENAME:Special:Foo}}"));
	}

	// =========================================================================
	// == Transclusion with initial colon

	@Test
	public void testInitialColonTranscludesPageFromMainNamespace() throws Exception
	{
		pages.put("Main", "main page");
		pages.put("Template:Main", "template");
		pages.put("Talk:Main", "talk page");

		assertEquals("main page", expand("Test", "{{:Main}}"));
		assertEquals("template", expand("Test", "{{Main}}"));
		assertEquals("talk page", expand("Test", "{{:Talk:Main}}"));
	}

	// =========================================================================
	// == #titleparts

	@Test
	public void testTitlepartsKeepsNamespaceInFirstSegment() throws Exception
	{
		assertEquals("B", expand("Test", "{{#titleparts:Talk:A/B|1|2}}"));
		assertEquals("Talk:A", expand("Test", "{{#titleparts:Talk:A/B|1}}"));
		assertEquals("Wikipedia:Foo", expand("Test", "{{#titleparts:WP:foo/bar|1}}"));
	}

	@Test
	public void testTitlepartsWithNegativeArguments() throws Exception
	{
		assertEquals("C", expand("Test", "{{#titleparts:A/B/C|1|-1}}"));
		assertEquals("B", expand("Test", "{{#titleparts:A/B/C|-1|2}}"));
		assertEquals("A", expand("Test", "{{#titleparts:A/B/C|-2}}"));
		assertEquals("B/C", expand("Test", "{{#titleparts:A/B/C||-2}}"));
		assertEquals("A/B/C", expand("Test", "{{#titleparts:A/B/C|0|-5}}"));
	}

	@Test
	public void testTitlepartsWithPositiveArguments() throws Exception
	{
		assertEquals("B/C", expand("Test", "{{#titleparts:A/B/C|2|2}}"));
		assertEquals("", expand("Test", "{{#titleparts:A/B/C|1|4}}"));
	}

	@Test
	public void testTitlepartsNormalizesTitle() throws Exception
	{
		assertEquals("Foo bar", expand("Test", "{{#titleparts:foo_bar}}"));
		assertEquals("Foo bar/Baz", expand("Test", "{{#titleparts:foo_bar/Baz|0}}"));
	}

	// =========================================================================
	// == Escaping

	@Test
	public void testPageNameIsHtmlEscapedWhenRendered() throws Exception
	{
		assertEquals("A&B's", expand("A&B's", "{{PAGENAME}}"));

		String html = render("A&B's", "{{PAGENAME}}");

		assertTrue(html, html.contains("A&amp;B&#39;s"));
	}

	@Test
	public void testPageNameCanBeUsedAsLinkTarget() throws Exception
	{
		String html = render("Test", "[[{{FULLPAGENAME:A&B's}}]]");

		assertTrue(html, html.contains("href=\"/wiki/A%26B%27s\""));
		assertTrue(html, html.contains("A&amp;B&#39;s</a>"));

		html = render("A&B's", "[[{{FULLPAGENAME}}]]");

		assertTrue(html, html.contains("<strong class=\"selflink\">A&amp;B&#39;s</strong>"));
	}

	// =========================================================================
	// == Case-sensitive namespaces (issue #103)

	@Test
	public void testCaseSensitiveNamespaceKeepsCaseOfPageName() throws Exception
	{
		config.getNamespace(0).setCase(NamespaceCase.CASE_SENSITIVE);
		config.getNamespace(1).setCase(NamespaceCase.CASE_SENSITIVE);
		config.getNamespace(10).setCase(NamespaceCase.CASE_SENSITIVE);

		assertEquals("wort", expand("wort", "{{PAGENAME}}"));
		assertEquals("wort", expand("Test", "{{PAGENAME:wort}}"));
		assertEquals("Talk:wort", expand("Test", "{{TALKPAGENAME:wort}}"));
		assertEquals("wort", expand("Test", "{{#titleparts:wort/a|1}}"));

		// User is still first-letter
		assertEquals("User:Foo", expand("Test", "{{FULLPAGENAME:User:foo}}"));
		assertEquals("User talk:Foo", expand("Test", "{{TALKPAGENAME:User:foo}}"));

		pages.put("Template:foo", "lower");
		pages.put("Template:Foo", "upper");
		pages.put("bar", "main");

		assertEquals("lower", expand("Test", "{{foo}}"));
		assertEquals("upper", expand("Test", "{{Foo}}"));
		assertEquals("main", expand("Test", "{{:bar}}"));
	}

	// =========================================================================

	private String expand(String title, String wikitext) throws Exception
	{
		PageId pageId = new PageId(PageTitle.make(config, title), -1);

		EngProcessedPage page = engine.expand(pageId, wikitext, new MapCallback());

		return WtRtDataPrinter.print(page.getPage());
	}

	private String render(String title, String wikitext) throws Exception
	{
		PageTitle pageTitle = PageTitle.make(config, title);
		PageId pageId = new PageId(pageTitle, -1);

		EngProcessedPage page = engine.postprocess(pageId, wikitext, new MapCallback());

		return HtmlRenderer.print(new TestRendererCallback(), config, pageTitle, page);
	}

	// =========================================================================

	private final class MapCallback
			implements
				ExpansionCallback
	{
		@Override
		public FullPage retrieveWikitext(
				ExpansionFrame expansionFrame,
				PageTitle pageTitle)
		{
			String text = pages.get(pageTitle.getPrefixedText());
			if (text == null)
				return null;
			return new FullPage(new PageId(pageTitle, -1), text);
		}

		@Override
		public String fileUrl(PageTitle pageTitle, int width, int height)
		{
			return null;
		}
	}

	private static final class TestRendererCallback
			implements
				HtmlRendererCallback
	{
		@Override
		public MediaInfo getMediaInfo(String title, int width, int height)
		{
			return null;
		}

		@Override
		public boolean resourceExists(PageTitle target)
		{
			return true;
		}

		@Override
		public String makeUrl(PageTitle target)
		{
			return "/wiki/" + UrlEncoding.WIKI.encode(target.getNormalizedFullTitle());
		}

		@Override
		public String makeUrl(WtUrl target)
		{
			return "";
		}

		@Override
		public String makeUrlMissingTarget(String path)
		{
			return "/wiki/" + path;
		}
	}
}
