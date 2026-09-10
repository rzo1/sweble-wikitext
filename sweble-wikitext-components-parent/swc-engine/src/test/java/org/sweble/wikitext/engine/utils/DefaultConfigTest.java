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

package org.sweble.wikitext.engine.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.sweble.wikitext.engine.ExpansionCallback;
import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.FullPage;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.I18nAlias;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.ext.core.CorePfnBehaviorSwitches;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.output.HtmlRenderer;
import org.sweble.wikitext.engine.output.HtmlRendererCallback;
import org.sweble.wikitext.engine.output.MediaInfo;
import org.sweble.wikitext.parser.nodes.WtUrl;
import org.sweble.wikitext.parser.utils.WtRtDataPrinter;

public class DefaultConfigTest
{
	private final WikiConfigImpl config = DefaultConfig.generate();

	private final WtEngineImpl engine = new WtEngineImpl(config);

	/**
	 * The pages that can be transcluded, by full title.
	 */
	private final Map<String, String> pages = new HashMap<String, String>();

	// =========================================================================

	@Test
	public void testGenerateRegistersCanonicalEnglishMagicWords() throws Exception
	{
		assertNotNull(config.getParserFunction("SAFESUBST:"));
		assertNotNull(config.getParserFunction("#if:"));
		assertNotNull(config.getParserFunction("lc:"));
		assertNotNull(config.getParserFunction("PAGENAME"));
		assertNotNull(config.getPageSwitch("__NOTOC__"));
		assertTrue(config.getParserConfig().isRedirectKeyword("#redirect"));

		// Case-sensitive magic words
		assertNull(config.getParserFunction("pagename"));
		assertNull(config.getPageSwitch("__hiddencat__"));

		// Behavior switches of extensions are not part of the default config
		assertNull(config.getPageSwitch("__DISAMBIG__"));
	}

	@Test
	public void testEnWpExtendsTheDefaultAliases() throws Exception
	{
		WikiConfigImpl enWp = DefaultConfigEnWp.generate();

		Set<String> ids = new HashSet<String>();
		for (I18nAlias alias : config.getI18nAliases())
		{
			assertEquals(alias, enWp.getI18nAliasById(alias.getId()));
			ids.add(alias.getId());
		}

		Set<String> enWpOnly = new HashSet<String>();
		for (I18nAlias alias : enWp.getI18nAliases())
		{
			if (!ids.contains(alias.getId()))
				enWpOnly.add(alias.getId());
		}

		assertEquals(
				new HashSet<String>(Arrays.asList("disambiguation", "noglobal", "archivedtalk", "notalk")),
				enWpOnly);
	}

	@Test
	public void testPageIsExpandedWithGeneratedConfig() throws Exception
	{
		pages.put("Template:Greeting", "Hello {{{1}}}");

		assertEquals(
				"__NOTOC__\nFoo yes Hello World x",
				expand("Foo", "__NOTOC__\n{{PAGENAME}} {{#if:x|yes|no}} {{safesubst:Greeting|World}} {{lc:X}}"));
	}

	@Test
	public void testPageIsRenderedWithGeneratedConfig() throws Exception
	{
		pages.put("Template:Greeting", "Hello {{{1}}}");

		PageTitle pageTitle = PageTitle.make(config, "Foo");
		PageId pageId = new PageId(pageTitle, -1);

		EngProcessedPage page = engine.postprocess(
				pageId,
				"__NOTOC__\n{{PAGENAME}} {{#if:x|yes|no}} {{safesubst:Greeting|World}} {{lc:X}} [[Bar]]s",
				new MapCallback());

		assertEquals(
				new HashSet<String>(Arrays.asList("notoc")),
				CorePfnBehaviorSwitches.getBehaviorSwitches(config, page.getPage()));

		String html = HtmlRenderer.print(new TestRendererCallback(), config, pageTitle, page);

		assertTrue(html, html.contains("Foo yes Hello World x"));
		assertTrue(html, html.contains(">Bars</a>"));
		assertFalse(html, html.contains("NOTOC"));
	}

	// =========================================================================

	private String expand(String title, String wikitext) throws Exception
	{
		PageId pageId = new PageId(PageTitle.make(config, title), -1);

		EngProcessedPage page = engine.expand(pageId, wikitext, new MapCallback());

		return WtRtDataPrinter.print(page.getPage());
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
