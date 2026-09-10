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

package org.sweble.wikitext.engine.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.TagExtensionBase;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.ext.builtin.BuiltInTagExtensions.TagExtensionPre;
import org.sweble.wikitext.engine.ext.generic.GenericTagExtension;
import org.sweble.wikitext.engine.ext.generic.WikipediaTagExtensions;
import org.sweble.wikitext.engine.ext.ref.RefTagExt.RefTagExtImpl;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.output.HtmlRenderer;
import org.sweble.wikitext.engine.output.HtmlRendererCallback;
import org.sweble.wikitext.engine.output.MediaInfo;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.engine.utils.LanguageConfigGenerator;
import org.sweble.wikitext.engine.utils.UrlEncoding;
import org.sweble.wikitext.parser.nodes.WtBold;
import org.sweble.wikitext.parser.nodes.WtInternalLink;
import org.sweble.wikitext.parser.nodes.WtItalics;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtTagExtension;
import org.sweble.wikitext.parser.nodes.WtUrl;
import org.sweble.wikitext.parser.nodes.WtXmlElement;

/**
 * Extension tags reported by the siteinfo API are registered as tag
 * extensions and matched case-insensitively (rzo1/sweble-wikitext#99). Uses a
 * local siteinfo response so no network access is required.
 */
public class ExtensionTagsTest
{
	/** The extension tags listed in extension-tags-siteinfo.xml. */
	private static final List<String> REPORTED_TAGS = Arrays.asList(
			"pre", "nowiki", "gallery", "indicator", "langconvert", "graph",
			"timeline", "hiero", "ref", "references", "inputbox", "imagemap",
			"source", "syntaxhighlight", "poem", "categorytree", "section",
			"score", "templatestyles", "templatedata", "math", "ce", "chem",
			"mapframe");

	private static final String SYNTAXHIGHLIGHT_BODY = "'''b''' [[a]]";

	private static final String SYNTAXHIGHLIGHT =
			"<syntaxhighlight lang=\"c\">" + SYNTAXHIGHLIGHT_BODY + "</syntaxhighlight>";

	private static final String GALLERY_BODY =
			"\nFile:A.jpg|''Caption'' [[a]]\nFile:B.jpg|'''b'''\n";

	private static final String GALLERY = "<gallery>" + GALLERY_BODY + "</gallery>";

	// =========================================================================

	private static WikiConfigImpl generateConfig() throws Exception
	{
		URL siteinfo = ExtensionTagsTest.class.getResource("/extension-tags-siteinfo.xml");
		URL namespaceAliases = ExtensionTagsTest.class.getResource("/extension-tags-namespacealiases.xml");
		assertNotNull(siteinfo);
		assertNotNull(namespaceAliases);

		return (WikiConfigImpl) LanguageConfigGenerator.generateWikiConfig(
				"Test Wiki",
				"http://localhost/",
				"de",
				namespaceAliases.toString(),
				siteinfo.toString(),
				siteinfo.toString(),
				siteinfo.toString(),
				siteinfo.toString());
	}

	// =========================================================================

	@Test
	public void testReportedExtensionTagsAreRegistered() throws Exception
	{
		WikiConfigImpl config = generateConfig();

		for (String name : REPORTED_TAGS)
			assertNotNull(name, config.getTagExtension(name));

		// Implemented tag extensions are not replaced by generic ones
		assertTrue(config.getTagExtension("pre") instanceof TagExtensionPre);
		assertTrue(config.getTagExtension("ref") instanceof RefTagExtImpl);
		assertTrue(config.getTagExtension("syntaxhighlight") instanceof GenericTagExtension);

		// Only the tags the wiki knows are registered
		assertNull(config.getTagExtension("charinsert"));
		assertNull(config.getTagExtension("maplink"));
		assertNull(config.getTagExtension("span"));
		assertEquals(REPORTED_TAGS.size(), config.getTagExtensions().size());
	}

	@Test
	public void testDefaultEnWpConfigRegistersWikipediaTags() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();

		for (String name : WikipediaTagExtensions.TAG_NAMES)
			assertTrue(name, config.getTagExtension(name) instanceof GenericTagExtension);
		for (String name : REPORTED_TAGS)
			assertNotNull(name, config.getTagExtension(name));
	}

	@Test
	public void testTagExtensionNamesAreCaseInsensitiveByDefault() throws Exception
	{
		for (WikiConfigImpl config : configs())
		{
			assertFalse(config.isTagExtensionNamesCaseSensitive());
			assertSame(config.getTagExtension("ref"), config.getTagExtension("REF"));
			assertSame(config.getTagExtension("syntaxhighlight"), config.getTagExtension("SyntaxHighlight"));
			assertTrue(config.getParserConfig().isValidExtensionTagName("REF"));
		}
	}

	@Test
	public void testCaseSensitiveTagExtensionNames() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();

		config.setTagExtensionNamesCaseSensitive(true);
		assertNotNull(config.getTagExtension("ref"));
		assertNull(config.getTagExtension("REF"));

		config.setTagExtensionNamesCaseSensitive(false);
		assertNotNull(config.getTagExtension("REF"));
	}

	@Test
	public void testSyntaxhighlightBodyIsNotParsed() throws Exception
	{
		for (WikiConfigImpl config : configs())
		{
			WtNode page = postprocess(config, "before " + SYNTAXHIGHLIGHT + " after");

			List<WtTagExtension> tagExts = findAll(page, WtTagExtension.class);
			assertEquals(1, tagExts.size());
			assertEquals("syntaxhighlight", tagExts.get(0).getName());
			assertEquals(SYNTAXHIGHLIGHT_BODY, tagExts.get(0).getBody().getContent());

			assertTrue(findAll(page, WtBold.class).isEmpty());
			assertTrue(findAll(page, WtInternalLink.class).isEmpty());
			assertTrue(findAll(page, WtXmlElement.class).isEmpty());
		}
	}

	@Test
	public void testGalleryBodyIsNotParsed() throws Exception
	{
		for (WikiConfigImpl config : configs())
		{
			WtNode page = postprocess(config, GALLERY);

			List<WtTagExtension> tagExts = findAll(page, WtTagExtension.class);
			assertEquals(1, tagExts.size());
			assertEquals("gallery", tagExts.get(0).getName());
			assertEquals(GALLERY_BODY, tagExts.get(0).getBody().getContent());

			assertTrue(findAll(page, WtBold.class).isEmpty());
			assertTrue(findAll(page, WtItalics.class).isEmpty());
			assertTrue(findAll(page, WtInternalLink.class).isEmpty());
		}
	}

	@Test
	public void testUpperCaseRefIsRef() throws Exception
	{
		for (WikiConfigImpl config : configs())
		{
			WtNode page = postprocess(config, "x<REF>y</REF>");

			List<WtTagExtension> tagExts = findAll(page, WtTagExtension.class);
			assertEquals(1, tagExts.size());
			assertEquals("y", tagExts.get(0).getBody().getContent());

			TagExtensionBase tagExt = config.getTagExtension(tagExts.get(0).getName());
			assertNotNull(tagExt);
			assertEquals("ref", tagExt.getId());

			assertTrue(findAll(page, WtXmlElement.class).isEmpty());
		}
	}

	@Test
	public void testRenderingDoesNotFail() throws Exception
	{
		StringBuilder wikitext = new StringBuilder();
		wikitext.append("before\n\n");
		wikitext.append(SYNTAXHIGHLIGHT).append("\n\n");
		wikitext.append(GALLERY).append("\n\n");
		wikitext.append("text<REF>y</REF>\n\n");
		wikitext.append("<references />\n\n");
		wikitext.append("<templatestyles src=\"a/styles.css\" />\n\n");
		for (String name : REPORTED_TAGS)
			wikitext.append("<").append(name).append(">'''x''' [[x]]</").append(name).append(">\n\n");
		wikitext.append("after\n");

		for (WikiConfigImpl config : configs())
		{
			PageTitle pageTitle = PageTitle.make(config, "Example");
			EngProcessedPage cp = new WtEngineImpl(config).postprocess(
					new PageId(pageTitle, -1),
					wikitext.toString(),
					null);

			String html = HtmlRenderer.print(new TestCallback(), config, pageTitle, cp.getPage());

			assertTrue(html, html.contains("before"));
			assertTrue(html, html.contains("after"));
			assertFalse(html, html.contains("<b>"));
		}
	}

	@Test
	public void testSyntaxhighlightIsRenderedAsEscapedPre() throws Exception
	{
		for (WikiConfigImpl config : configs())
		{
			String html = render(config, "<syntaxhighlight lang=\"c\">a < b && [[c]]</syntaxhighlight>");
			assertTrue(html, html.contains("<pre class=\"mw-highlight lang-c\">a &lt; b &amp;&amp; [[c]]</pre>"));

			html = render(config, "<source>x</source>");
			assertTrue(html, html.contains("<pre class=\"mw-highlight\">x</pre>"));

			html = render(config, "x <syntaxhighlight lang=\"c\" inline>a</syntaxhighlight> y");
			assertTrue(html, html.contains("<code class=\"mw-highlight lang-c\">a</code>"));

			// The language only contributes harmless characters to the class
			html = render(config, "<syntaxhighlight lang=\"c onclick=x\">a</syntaxhighlight>");
			assertFalse(html, html.contains("onclick="));
			assertTrue(html, html.contains("<pre class=\"mw-highlight lang-conclickx\">a</pre>"));

			// Like in MediaWiki the tag ends at the first '>'
			html = render(config, "<syntaxhighlight lang='\"><script>'>a</syntaxhighlight>");
			assertFalse(html, html.contains("<script"));
			assertTrue(html, html.contains("<pre class=\"mw-highlight\">&lt;script&gt;"));
		}
	}

	@Test
	public void testPoemIsRenderedWithLineBreaks() throws Exception
	{
		for (WikiConfigImpl config : configs())
		{
			String html = render(config, "<poem>\nRoses & red\n<i>violets</i> [[blue]]\n</poem>");
			assertTrue(html, html.contains("<div class=\"poem\">Roses &amp; red<br />"));
			assertTrue(html, html.contains("&lt;i&gt;violets&lt;/i&gt; [[blue]]</div>"));
			assertFalse(html, html.contains("<i>"));
			assertFalse(html, html.contains("<a "));
		}
	}

	@Test
	public void testTemplatestylesIsNotRendered() throws Exception
	{
		for (WikiConfigImpl config : configs())
		{
			String html = render(config, "before <templatestyles src=\"a/styles.css\" /> after");
			assertTrue(html, html.contains("before"));
			assertTrue(html, html.contains("after"));
			assertFalse(html, html.contains("templatestyles"));
			assertFalse(html, html.contains("styles.css"));
		}
	}

	@Test
	public void testGalleryIsRenderedAsEscapedText() throws Exception
	{
		for (WikiConfigImpl config : configs())
		{
			String html = render(config, "<gallery>\nFile:A.jpg|<b>Caption</b> [[a]]\n</gallery>");
			assertTrue(html, html.contains(
					"<div class=\"mw-ext-gallery\">\nFile:A.jpg|&lt;b&gt;Caption&lt;/b&gt; [[a]]\n</div>"));
			assertFalse(html, html.contains("<b>"));
			assertFalse(html, html.contains("<a "));
		}
	}

	@Test
	public void testSaveAndLoadGeneratedConfig() throws Exception
	{
		WikiConfigImpl config = generateConfig();

		StringWriter writer = new StringWriter();
		config.save(writer);

		WikiConfigImpl loaded = WikiConfigImpl.load(new StringReader(writer.toString()));
		assertEquals(config, loaded);
		assertEquals("syntaxhighlight", loaded.getTagExtension("SYNTAXHIGHLIGHT").getId());
		assertTrue(loaded.getTagExtension("gallery") instanceof GenericTagExtension);
		assertTrue(loaded.getTagExtension("ref") instanceof RefTagExtImpl);
	}

	// =========================================================================

	private static List<WikiConfigImpl> configs() throws Exception
	{
		return Arrays.asList(generateConfig(), DefaultConfigEnWp.generate());
	}

	private static WtNode postprocess(WikiConfigImpl config, String wikitext) throws Exception
	{
		PageTitle pageTitle = PageTitle.make(config, "Example");
		EngProcessedPage cp = new WtEngineImpl(config).postprocess(
				new PageId(pageTitle, -1),
				wikitext,
				null);
		return cp.getPage();
	}

	private static String render(WikiConfigImpl config, String wikitext) throws Exception
	{
		PageTitle pageTitle = PageTitle.make(config, "Example");
		EngProcessedPage cp = new WtEngineImpl(config).postprocess(
				new PageId(pageTitle, -1),
				wikitext,
				null);
		return HtmlRenderer.print(new TestCallback(), config, pageTitle, cp.getPage());
	}

	private static <T extends WtNode> List<T> findAll(WtNode node, Class<T> clazz)
	{
		List<T> result = new ArrayList<T>();
		findAll(node, clazz, result);
		return result;
	}

	private static <T extends WtNode> void findAll(WtNode node, Class<T> clazz, List<T> result)
	{
		if (clazz.isInstance(node))
			result.add(clazz.cast(node));
		for (WtNode child : node)
			findAll(child, clazz, result);
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
