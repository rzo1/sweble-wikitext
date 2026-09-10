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
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.sweble.wikitext.parser.nodes.WtImageLink;
import org.sweble.wikitext.parser.nodes.WtImageLink.ImageHorizAlign;
import org.sweble.wikitext.parser.nodes.WtImageLink.ImageVertAlign;
import org.sweble.wikitext.parser.nodes.WtImageLink.ImageViewFormat;
import org.sweble.wikitext.parser.nodes.WtLinkOptionKeyword;
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
	public void testOptionsContainingUnderscoresAreRecognized() throws Exception
	{
		ParserConfig config = new LocalizedParserConfig();

		WtImageLink img = parseImageLink(
				config,
				"[[File:Exemple.jpg|sans_cadre|légende]]");

		assertEquals(ImageViewFormat.FRAMELESS, img.getFormat());
		assertEquals("légende", toText(config, img.getTitle()));

		img = parseImageLink(
				config,
				"[[File:Przyklad.jpg| bez_ramki |podpis]]");

		assertEquals(ImageViewFormat.FRAMELESS, img.getFormat());
		assertEquals("podpis", toText(config, img.getTitle()));

		img = parseImageLink(
				config,
				"[[File:Exemple.jpg|non_encadré|légende]]");

		assertEquals(ImageViewFormat.FRAMELESS, img.getFormat());
		assertEquals("légende", toText(config, img.getTitle()));
	}

	@Test
	public void testNonLatinOptionsContainingUnderscoresAreRecognized() throws Exception
	{
		ParserConfig config = new LocalizedParserConfig();

		WtImageLink img = parseImageLink(
				config,
				"[[File:Example.jpg|خط_أساسي|تعليق]]");
		assertEquals(ImageVertAlign.BASELINE, img.getVAlign());
		assertEquals("تعليق", toText(config, img.getTitle()));

		img = parseImageLink(config, "[[File:Example.jpg|نص_أعلى]]");
		assertEquals(ImageVertAlign.TEXT_TOP, img.getVAlign());

		img = parseImageLink(config, "[[File:Example.jpg|نص_أسفل]]");
		assertEquals(ImageVertAlign.TEXT_BOTTOM, img.getVAlign());
	}

	@Test
	public void testCaptionsContainingUnderscoresAreNotOptions() throws Exception
	{
		ParserConfig config = new LocalizedParserConfig();

		WtImageLink img = parseImageLink(
				config,
				"[[File:Exemple.jpg|sans_cadre|sans_cadre_du_tout]]");

		assertEquals(ImageViewFormat.FRAMELESS, img.getFormat());
		assertEquals("sans_cadre_du_tout", toText(config, img.getTitle()));

		// Underscores are not normalized: only the exact alias is an option
		img = parseImageLink(config, "[[File:Exemple.jpg|sans cadre]]");

		assertEquals(ImageViewFormat.UNRESTRAINED, img.getFormat());
		assertEquals("sans cadre", toText(config, img.getTitle()));

		// English has no aliases containing '_'
		ParserConfig defaultConfig = new SimpleParserConfig();

		img = parseImageLink(
				defaultConfig,
				"[[File:Example.jpg|thumb|text_top|a_caption]]");

		assertEquals(ImageViewFormat.THUMBNAIL, img.getFormat());
		assertEquals(ImageVertAlign.MIDDLE, img.getVAlign());
		assertEquals("a_caption", toText(defaultConfig, img.getTitle()));
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

	@Test
	public void testEnglishParameterizedOptionsAreNotCaptions() throws Exception
	{
		ParserConfig config = new SimpleParserConfig();

		String[] options = {
				"upright=1.5",
				"upright 1.5",
				"class=noviewer",
				"lang=de",
				"page=2",
				"page 2",
				"thumb=Other.png",
				"thumbnail=Other.png" };

		for (String option : options)
		{
			WtImageLink img = parseImageLink(
					config,
					"[[File:Example.jpg|" + option + "|A caption]]");

			assertEquals(option, "A caption", toText(config, img.getTitle()));
			assertEquals(option, option, findKeyword(img, option).getKeyword());
		}
	}

	@Test
	public void testEnglishParameterizedOptionsSetTheirProperties() throws Exception
	{
		ParserConfig config = new SimpleParserConfig();

		WtImageLink img = parseImageLink(config, "[[File:Example.jpg|upright=1.5]]");
		assertTrue(img.getUpright());
		assertFalse(img.hasTitle());

		img = parseImageLink(config, "[[File:Example.jpg|upright 0.5]]");
		assertTrue(img.getUpright());

		img = parseImageLink(config, "[[File:Example.jpg|thumb=Other.png]]");
		assertEquals(ImageViewFormat.THUMBNAIL, img.getFormat());

		img = parseImageLink(config, "[[File:Example.jpg|thumbnail=Other.png]]");
		assertEquals(ImageViewFormat.THUMBNAIL, img.getFormat());
	}

	@Test
	public void testParameterizedOptionsWithInvalidValuesAreCaptions() throws Exception
	{
		ParserConfig config = new SimpleParserConfig();

		String[] captions = {
				"upright=abc",
				"upright=",
				"upright big",
				"page=two",
				"page 2 of 3",
				"page=0",
				"lang=not a language",
				"class=a\nb" };

		for (String caption : captions)
		{
			WtImageLink img = parseImageLink(
					config,
					"[[File:Example.jpg|" + caption + "]]");

			assertFalse(caption, img.getUpright());
			assertTrue(caption, img.hasTitle());
		}
	}

	@Test
	public void testEnglishKeywordSynonyms() throws Exception
	{
		ParserConfig config = new SimpleParserConfig();

		WtImageLink img = parseImageLink(config, "[[File:Example.jpg|centre|sup]]");
		assertEquals(ImageHorizAlign.CENTER, img.getHAlign());
		assertEquals(ImageVertAlign.SUPER, img.getVAlign());
		assertFalse(img.hasTitle());

		img = parseImageLink(config, "[[File:Example.jpg|framed]]");
		assertEquals(ImageViewFormat.FRAME, img.getFormat());

		img = parseImageLink(config, "[[File:Example.jpg|enframed]]");
		assertEquals(ImageViewFormat.FRAME, img.getFormat());
	}

	/**
	 * All img_* magic words are case-sensitive in MediaWiki (MessagesEn.php).
	 */
	@Test
	public void testEnglishOptionsAreCaseSensitive() throws Exception
	{
		ParserConfig config = new SimpleParserConfig();

		WtImageLink img = parseImageLink(config, "[[File:Example.jpg|Thumb]]");
		assertEquals(ImageViewFormat.UNRESTRAINED, img.getFormat());
		assertEquals("Thumb", toText(config, img.getTitle()));

		img = parseImageLink(config, "[[File:Example.jpg|RIGHT]]");
		assertEquals(ImageHorizAlign.UNSPECIFIED, img.getHAlign());
		assertEquals("RIGHT", toText(config, img.getTitle()));

		img = parseImageLink(config, "[[File:Example.jpg|Upright=1.5]]");
		assertFalse(img.getUpright());
		assertEquals("Upright=1.5", toText(config, img.getTitle()));

		img = parseImageLink(config, "[[File:Example.jpg|100PX]]");
		assertEquals(-1, img.getWidth());
		assertEquals("100PX", toText(config, img.getTitle()));
	}

	@Test
	public void testLocalizedParameterizedOptionsAreRecognized() throws Exception
	{
		ParserConfig config = new LocalizedParserConfig();

		WtImageLink img = parseImageLink(
				config,
				"[[File:Beispiel.jpg|hochkant=1.2|klasse=rechts|Titel]]");

		assertTrue(img.getUpright());
		assertEquals("Titel", toText(config, img.getTitle()));
		assertNotNull(findKeyword(img, "hochkant=1.2"));
		assertNotNull(findKeyword(img, "klasse=rechts"));
	}

	@Test
	public void testNodeFactoryResolvesParameterizedOptions() throws Exception
	{
		ParserConfig config = new SimpleParserConfig();

		WtImageLink img = parseImageLink(
				config,
				"[[File:Example.jpg|upright=1.5|thumb=Other.png|A caption]]");

		WtImageLink rebuilt = config.getNodeFactory().img(img.getTarget(), img.getOptions());

		assertTrue(rebuilt.getUpright());
		assertEquals(ImageViewFormat.THUMBNAIL, rebuilt.getFormat());
	}

	// =========================================================================

	private WtLinkOptionKeyword findKeyword(WtImageLink img, String keyword)
	{
		for (WtNode option : img.getOptions())
		{
			if (option instanceof WtLinkOptionKeyword &&
					keyword.equals(((WtLinkOptionKeyword) option).getKeyword()))
				return (WtLinkOptionKeyword) option;
		}
		fail("No option keyword `" + keyword + "' found");
		return null;
	}

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
	 * A test configuration with some of the German, Russian, French, Polish
	 * and Arabic aliases of the MediaWiki image option magic words.
	 */
	private static final class LocalizedParserConfig
			extends
				SimpleParserConfig
	{
		private final Map<String, String> aliases = new HashMap<String, String>();

		public LocalizedParserConfig()
		{
			addAliases(ImageLinkOptionAliases.IMG_THUMBNAIL, "mini", "miniatur", "мини");
			addAliases(ImageLinkOptionAliases.IMG_FRAMELESS, "rahmenlos", "sans_cadre", "non_encadré", "bez_ramki");
			addAliases(ImageLinkOptionAliases.IMG_FRAMED, "gerahmt", "rahmen");
			addAliases(ImageLinkOptionAliases.IMG_LEFT, "links");
			addAliases(ImageLinkOptionAliases.IMG_RIGHT, "rechts", "справа");
			addAliases(ImageLinkOptionAliases.IMG_CENTER, "zentriert");
			addAliases(ImageLinkOptionAliases.IMG_NONE, "ohne");
			addAliases(ImageLinkOptionAliases.IMG_UPRIGHT, "hochkant", "hochkant=$1");
			addAliases(ImageLinkOptionAliases.IMG_BORDER, "rand");
			addAliases(ImageLinkOptionAliases.IMG_BASELINE, "grundlinie", "خط_أساسي");
			addAliases(ImageLinkOptionAliases.IMG_TEXT_TOP, "نص_أعلى");
			addAliases(ImageLinkOptionAliases.IMG_TEXT_BOTTOM, "نص_أسفل");
			addAliases(ImageLinkOptionAliases.IMG_WIDTH, "$1px", "$1пкс");
			addAliases(ImageLinkOptionAliases.IMG_LINK, "verweis=$1");
			addAliases(ImageLinkOptionAliases.IMG_ALT, "alternativtext=$1");
			addAliases(ImageLinkOptionAliases.IMG_CLASS, "klasse=$1");
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
