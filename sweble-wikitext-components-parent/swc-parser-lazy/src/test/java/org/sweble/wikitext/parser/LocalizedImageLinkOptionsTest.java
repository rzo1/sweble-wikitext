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
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.sweble.wikitext.parser.nodes.WtImageLink;
import org.sweble.wikitext.parser.nodes.WtImageLink.ImageHorizAlign;
import org.sweble.wikitext.parser.nodes.WtImageLink.ImageVertAlign;
import org.sweble.wikitext.parser.nodes.WtImageLink.ImageViewFormat;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtPageName;
import org.sweble.wikitext.parser.utils.NonExpandingParser;
import org.sweble.wikitext.parser.utils.SimpleParserConfig;

/**
 * Upstream sweble/sweble-wikitext#5: Image link options have to be resolved
 * using the (localized) aliases of the parser configuration.
 */
public class LocalizedImageLinkOptionsTest
{
	@Test
	public void testGermanOptionsAreRecognizedWithGermanConfig() throws Exception
	{
		ParserConfig config = new LocalizedParserConfig();

		WtImageLink img = parseImageLink(
				config,
				"[[File:Beispiel.jpg|mini|links|hochkant|rand|grundlinie|120px"
						+ "|verweis=Hauptseite|alternativtext=Alternativer Text"
						+ "|Eine Bildunterschrift]]");

		assertEquals(ImageViewFormat.THUMBNAIL, img.getFormat());
		assertEquals(ImageHorizAlign.LEFT, img.getHAlign());
		assertEquals(ImageVertAlign.BASELINE, img.getVAlign());
		assertTrue(img.getUpright());
		assertTrue(img.getBorder());
		assertEquals(120, img.getWidth());
		assertEquals(-1, img.getHeight());

		assertNotNull(img.getLink());
		assertEquals("Hauptseite", ((WtPageName) img.getLink().getTarget()).getAsString());

		assertTrue(img.hasAlt());
		assertEquals("Alternativer Text", toText(config, img.getAlt()));

		assertTrue(img.hasTitle());
		assertEquals("Eine Bildunterschrift", toText(config, img.getTitle()));
	}

	@Test
	public void testMoreGermanOptions() throws Exception
	{
		ParserConfig config = new LocalizedParserConfig();

		WtImageLink img = parseImageLink(
				config,
				"[[File:Beispiel.jpg|rahmenlos|zentriert|x80px|Titel]]");

		assertEquals(ImageViewFormat.FRAMELESS, img.getFormat());
		assertEquals(ImageHorizAlign.CENTER, img.getHAlign());
		assertFalse(img.getUpright());
		assertEquals(-1, img.getWidth());
		assertEquals(80, img.getHeight());
		assertEquals("Titel", toText(config, img.getTitle()));
	}

	@Test
	public void testNonLatinOptionsAreRecognized() throws Exception
	{
		ParserConfig config = new LocalizedParserConfig();

		WtImageLink img = parseImageLink(
				config,
				"[[File:Example.jpg|мини|справа|200пкс|Подпись]]");

		assertEquals(ImageViewFormat.THUMBNAIL, img.getFormat());
		assertEquals(ImageHorizAlign.RIGHT, img.getHAlign());
		assertEquals(200, img.getWidth());
		assertEquals("Подпись", toText(config, img.getTitle()));
	}

	@Test
	public void testEnglishOptionsStillWorkWithLocalizedConfig() throws Exception
	{
		ParserConfig config = new LocalizedParserConfig();

		WtImageLink img = parseImageLink(
				config,
				"[[File:Example.jpg|thumb|right|upright|border|100x50px"
						+ "|link=Main Page|alt=Alt text|A caption]]");

		assertEquals(ImageViewFormat.THUMBNAIL, img.getFormat());
		assertEquals(ImageHorizAlign.RIGHT, img.getHAlign());
		assertTrue(img.getUpright());
		assertTrue(img.getBorder());
		assertEquals(100, img.getWidth());
		assertEquals(50, img.getHeight());
		assertEquals("Main Page", ((WtPageName) img.getLink().getTarget()).getAsString());
		assertEquals("Alt text", toText(config, img.getAlt()));
		assertEquals("A caption", toText(config, img.getTitle()));
	}

	@Test
	public void testLocalizedOptionsAreNotRecognizedWithDefaultConfig() throws Exception
	{
		ParserConfig config = new SimpleParserConfig();

		WtImageLink img = parseImageLink(
				config,
				"[[File:Beispiel.jpg|mini|links|Eine Bildunterschrift]]");

		assertEquals(ImageViewFormat.UNRESTRAINED, img.getFormat());
		assertEquals(ImageHorizAlign.UNSPECIFIED, img.getHAlign());
		assertEquals("Eine Bildunterschrift", toText(config, img.getTitle()));
	}

	// =========================================================================

	private WtImageLink parseImageLink(ParserConfig config, String wikitext) throws Exception
	{
		NonExpandingParser parser = new NonExpandingParser(config);
		WtNode article = parser.parseArticle(wikitext, "title");
		WtImageLink img = findImageLink(article);
		assertNotNull("No image link found", img);
		return img;
	}

	private WtImageLink findImageLink(WtNode node)
	{
		if (node instanceof WtImageLink)
			return (WtImageLink) node;
		for (WtNode child : node)
		{
			WtImageLink img = findImageLink(child);
			if (img != null)
				return img;
		}
		return null;
	}

	private String toText(ParserConfig config, WtNode node) throws Exception
	{
		return config.getAstTextUtils().astToText(node).trim();
	}

	// =========================================================================

	/**
	 * A test configuration with some of the German and Russian aliases of the
	 * MediaWiki image option magic words.
	 */
	private static final class LocalizedParserConfig
			extends
				SimpleParserConfig
	{
		private final Map<String, String> aliases = new HashMap<String, String>();

		public LocalizedParserConfig()
		{
			addAliases(ImageLinkOptionAliases.IMG_THUMBNAIL, "mini", "miniatur", "мини");
			addAliases(ImageLinkOptionAliases.IMG_FRAMELESS, "rahmenlos");
			addAliases(ImageLinkOptionAliases.IMG_FRAMED, "gerahmt", "rahmen");
			addAliases(ImageLinkOptionAliases.IMG_LEFT, "links");
			addAliases(ImageLinkOptionAliases.IMG_RIGHT, "rechts", "справа");
			addAliases(ImageLinkOptionAliases.IMG_CENTER, "zentriert");
			addAliases(ImageLinkOptionAliases.IMG_NONE, "ohne");
			addAliases(ImageLinkOptionAliases.IMG_UPRIGHT, "hochkant", "hochkant=$1");
			addAliases(ImageLinkOptionAliases.IMG_BORDER, "rand");
			addAliases(ImageLinkOptionAliases.IMG_BASELINE, "grundlinie");
			addAliases(ImageLinkOptionAliases.IMG_WIDTH, "$1px", "$1пкс");
			addAliases(ImageLinkOptionAliases.IMG_LINK, "verweis=$1");
			addAliases(ImageLinkOptionAliases.IMG_ALT, "alternativtext=$1");
		}

		private void addAliases(String id, String... names)
		{
			for (String name : names)
				aliases.put(name, id);
		}

		@Override
		public String getImageLinkOptionId(String alias)
		{
			String id = aliases.get(alias);
			return (id != null) ? id : super.getImageLinkOptionId(alias);
		}
	}
}
