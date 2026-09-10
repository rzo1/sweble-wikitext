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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.output.HtmlRenderer;
import org.sweble.wikitext.engine.output.HtmlRendererCallback;
import org.sweble.wikitext.engine.output.MediaInfo;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.engine.utils.UrlEncoding;
import org.sweble.wikitext.parser.nodes.WtUrl;

/**
 * Keeps the "Getting started" snippets of the README compiling and working.
 */
public class GettingStartedTest
{
	@Test
	public void testParseAndRenderHtml() throws Exception
	{
		// 1. Parse wikitext into an AST
		WikiConfig config = DefaultConfigEnWp.generate();
		WtEngineImpl engine = new WtEngineImpl(config);

		PageTitle pageTitle = PageTitle.make(config, "Example");
		PageId pageId = new PageId(pageTitle, -1);

		EngProcessedPage cp = engine.postprocess(pageId, "'''Hello''' [[World]]!", null);
		assertNotNull(cp.getPage());

		// 2. Render the AST to HTML
		HtmlRendererCallback callback = new HtmlRendererCallback()
		{
			@Override
			public boolean resourceExists(PageTitle target)
			{
				return false;
			}

			@Override
			public MediaInfo getMediaInfo(String title, int width, int height)
			{
				return null;
			}

			@Override
			public String makeUrl(PageTitle target)
			{
				return "/wiki/" + UrlEncoding.WIKI.encode(target.getNormalizedFullTitle());
			}

			@Override
			public String makeUrl(WtUrl target)
			{
				return target.getProtocol().isEmpty() ? target.getPath() : target.getProtocol() + ":" + target.getPath();
			}

			@Override
			public String makeUrlMissingTarget(String path)
			{
				return "/w/index.php?title=" + path + "&amp;action=edit&amp;redlink=1";
			}
		};

		String html = HtmlRenderer.print(callback, config, pageTitle, cp.getPage());

		assertTrue(html, html.contains("<b>Hello</b>"));
		assertTrue(html, html.contains("World"));
		assertTrue(html, html.contains("redlink=1"));
	}
}
