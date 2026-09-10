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

package org.sweble.wikitext.engine.output;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.engine.utils.UrlEncoding;
import org.sweble.wikitext.parser.nodes.WtUrl;

/**
 * A term and its definition on one line ("; term : definition") are rendered
 * like MediaWiki does (rzo1/sweble-wikitext#104).
 */
public class HtmlRendererDefinitionListTest
{
	@Test
	public void testTermAndDefinitionOnOneLine() throws Exception
	{
		assertContains(render(";a:b"), "<dl><dt>a</dt><dd>b</dd></dl>");
		assertContains(render("; t : d"), "<dl><dt>t</dt><dd>d</dd></dl>");
		assertContains(render(";a:b:c"), "<dl><dt>a</dt><dd>b:c</dd></dl>");
	}

	@Test
	public void testNestedPrefixes() throws Exception
	{
		assertContains(render(";;a:b"), "<dl><dt>a</dt><dd><dl><dt>b</dt></dl></dd></dl>");
		assertContains(render("*;a:b"), "<ul><li><dl><dt>a</dt><dd>b</dd></dl></li></ul>");
		assertContains(render(":;a:b"), "<dl><dd><dl><dt>a</dt><dd>b</dd></dl></dd></dl>");
		assertContains(render(";*a:b"), "<dl><dt>a</dt><dd><ul><li>b</li></ul></dd></dl>");
	}

	@Test
	public void testFollowingLines() throws Exception
	{
		assertContains(render(";a\n;b:c"), "<dl><dt>a</dt><dt>b</dt><dd>c</dd></dl>");
		assertContains(render(";a:b\n:c"), "<dl><dt>a</dt><dd>b</dd><dd>c</dd></dl>");
		assertContains(render(";a\n:*b"), "<dl><dt>a</dt><dd><ul><li>b</li></ul></dd></dl>");
	}

	@Test
	public void testColonInLinkDoesNotEndTerm() throws Exception
	{
		String html = render(";[[a:b]]:c");

		assertContains(html, "a:b</a></dt><dd>c</dd></dl>");
		assertSingleTerm(html);
	}

	@Test
	public void testColonInExternalLinkDoesNotEndTerm() throws Exception
	{
		String html = render(";[http://example.org/a:b x]:y");

		assertContains(html, "href=\"http://example.org/a:b\"");
		assertContains(html, "x</a></dt><dd>y</dd></dl>");
		assertSingleTerm(html);
	}

	@Test
	public void testColonInAttributeDoesNotEndTerm() throws Exception
	{
		assertContains(
				render(";<span title=\"a:b\">x</span>:y"),
				"<dt><span title=\"a:b\">x</span></dt><dd>y</dd></dl>");
	}

	@Test
	public void testColonInUrlDoesNotEndTerm() throws Exception
	{
		String html = render(";x http://example.org/a:b");

		assertContains(html, "href=\"http://example.org/a:b\"");
		assertFalse(html, html.contains("<dd>"));
	}

	@Test
	public void testColonInsideElementOrFormattingDoesNotEndTerm() throws Exception
	{
		String html = render(";<span>a:b</span>");
		assertContains(html, "<dl><dt><span>a:b</span></dt></dl>");

		html = render(";''a:b''");
		assertContains(html, "<dl><dt><i>a:b</i></dt></dl>");

		html = render(";''a'':b");
		assertContains(html, "<dl><dt><i>a</i></dt><dd>b</dd></dl>");
	}

	// =========================================================================

	private static void assertContains(String html, String expected)
	{
		assertTrue("Expected <" + expected + "> in:\n" + html, html.contains(expected));
	}

	private static void assertSingleTerm(String html)
	{
		assertTrue(html, html.indexOf("<dt>") == html.lastIndexOf("<dt>"));
	}

	/**
	 * Renders the wikitext and removes the whitespace around tags.
	 */
	private static String render(String wikitext) throws Exception
	{
		WikiConfig config = DefaultConfigEnWp.generate();
		WtEngineImpl engine = new WtEngineImpl(config);

		PageTitle pageTitle = PageTitle.make(config, "Example");
		PageId pageId = new PageId(pageTitle, -1);

		EngProcessedPage cp = engine.postprocess(pageId, wikitext, null);

		String html = HtmlRenderer.print(new TestCallback(), config, pageTitle, cp.getPage());

		return html.replaceAll("\\s*<", "<").replaceAll(">\\s*", ">");
	}

	// =========================================================================

	private static final class TestCallback
			implements
				HtmlRendererCallback
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
	}
}
