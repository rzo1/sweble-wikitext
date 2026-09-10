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

package org.sweble.wikitext.engine.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;

import org.junit.Test;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;

/**
 * Upstream sweble/sweble-wikitext#5: Image link options have to be resolved
 * using the img_* magic word aliases of the wiki configuration.
 */
public class ImageLinkOptionAliasesTest
{
	@Test
	public void testDefaultConfigHasNoImageLinkOptionAliases() throws Exception
	{
		// The default configuration does not register img_* magic words, the
		// parser falls back to the English aliases in that case
		ParserConfigImpl pc = DefaultConfigEnWp.generate().getParserConfig();

		assertNull(pc.getImageLinkOptionId("thumb"));
		assertNull(pc.getImageLinkOptionId("mini"));
	}

	@Test
	public void testGermanConfigResolvesGermanOptions() throws Exception
	{
		ParserConfigImpl pc = createGermanConfig().getParserConfig();

		assertEquals("img_thumbnail", pc.getImageLinkOptionId("mini"));
		assertEquals("img_thumbnail", pc.getImageLinkOptionId("thumb"));
		assertEquals("img_right", pc.getImageLinkOptionId("rechts"));
		assertEquals("img_left", pc.getImageLinkOptionId("links"));
		assertEquals("img_frameless", pc.getImageLinkOptionId("rahmenlos"));
		assertEquals("img_upright", pc.getImageLinkOptionId("hochkant"));
		assertEquals("img_upright", pc.getImageLinkOptionId("hochkant=$1"));
		assertEquals("img_width", pc.getImageLinkOptionId("$1px"));
		assertEquals("img_link", pc.getImageLinkOptionId("verweis=$1"));
		assertEquals("img_alt", pc.getImageLinkOptionId("alternativtext=$1"));

		// Not an image link option
		assertNull(pc.getImageLinkOptionId("Beispiel"));

		// The img_* aliases are case-sensitive
		assertNull(pc.getImageLinkOptionId("Mini"));
	}

	@Test
	public void testImageLinkOptionAliasesSurviveSaveAndLoad() throws Exception
	{
		WikiConfigImpl config = createGermanConfig();

		StringWriter writer = new StringWriter();
		config.save(writer);
		WikiConfigImpl loaded = WikiConfigImpl.load(new StringReader(writer.toString()));

		ParserConfigImpl pc = loaded.getParserConfig();
		assertEquals("img_thumbnail", pc.getImageLinkOptionId("mini"));
		assertEquals("img_link", pc.getImageLinkOptionId("verweis=$1"));
	}

	// =========================================================================

	/**
	 * Registers some of the German img_* magic word aliases the way
	 * LanguageConfigGenerator does for a German wiki.
	 */
	private WikiConfigImpl createGermanConfig() throws Exception
	{
		WikiConfigImpl c = DefaultConfigEnWp.generate();
		addAlias(c, "img_thumbnail", "mini", "miniatur", "thumbnail", "thumb");
		addAlias(c, "img_right", "rechts", "right");
		addAlias(c, "img_left", "links", "left");
		addAlias(c, "img_center", "zentriert", "center", "centre");
		addAlias(c, "img_frameless", "rahmenlos", "frameless");
		addAlias(c, "img_upright", "hochkant", "hochkant=$1", "hochkant $1", "upright", "upright=$1", "upright $1");
		addAlias(c, "img_width", "$1px");
		addAlias(c, "img_link", "verweis=$1", "link=$1");
		addAlias(c, "img_alt", "alternativtext=$1", "alt=$1");
		return c;
	}

	private void addAlias(WikiConfigImpl c, String id, String... aliases)
	{
		c.addI18nAlias(new I18nAliasImpl(id, true, Arrays.asList(aliases)));
	}
}
