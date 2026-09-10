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
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtText;
import org.sweble.wikitext.parser.nodes.WtXmlComment;
import org.sweble.wikitext.parser.preprocessor.PreprocessedWikitext;
import org.sweble.wikitext.parser.utils.SimpleParserConfig;

/**
 * Parser entity markers that do not denote a registered entity are not
 * entities and must neither make the parser throw nor alias another entity.
 */
public class ParserEntityTest
{
	private final SimpleParserConfig config = new SimpleParserConfig();

	@Test
	public void testRegisteredEntityIsResolved() throws Exception
	{
		WtEntityMap entityMap = new WtEntityMapImpl();
		int id = entityMap.registerEntity(config.getNodeFactory().comment("c"));

		WtNode ast = parse("a\uE000" + id + "\uE001b", entityMap);

		assertEquals(1, count(ast, WtXmlComment.class));
		assertEquals("ab", collectText(ast));
	}

	@Test
	public void testEntityIdThatOverflowsIsNotAnEntity() throws Exception
	{
		String source = "a\uE00099999999999999999999\uE001b";

		WtNode ast = parse(source, new WtEntityMapImpl());

		assertEquals(source, collectText(ast));
	}

	@Test
	public void testUnknownEntityIdIsNotAnEntity() throws Exception
	{
		String source = "a\uE00042\uE001b";

		WtNode ast = parse(source, new WtEntityMapImpl());

		assertEquals(source, collectText(ast));
	}

	@Test
	public void testUnknownEntityIdDoesNotAliasRegisteredEntity() throws Exception
	{
		WtEntityMap entityMap = new WtEntityMapImpl();
		entityMap.registerEntity(config.getNodeFactory().comment("c"));

		// 4294967296 would wrap around to 0 if truncated to an int
		String source = "a\uE0004294967296\uE001b";

		WtNode ast = parse(source, entityMap);

		assertEquals(0, count(ast, WtXmlComment.class));
		assertEquals(source, collectText(ast));
	}

	@Test
	public void testUnknownEntityIdInAttributeValueIsNotAnEntity() throws Exception
	{
		WtNode ast = parse("<span title=\"x\uE00042\uE001y\">z</span>", new WtEntityMapImpl());

		String text = collectText(ast);
		assertTrue(text, text.contains("\uE00042\uE001"));
	}

	// =========================================================================

	private WtNode parse(String source, WtEntityMap entityMap) throws Exception
	{
		WikitextParser parser = new WikitextParser(config);
		return parser.parseArticle(new PreprocessedWikitext(source, entityMap), "title");
	}

	private static int count(WtNode node, Class<?> clazz)
	{
		int count = clazz.isInstance(node) ? 1 : 0;
		for (WtNode child : node)
			count += count(child, clazz);
		return count;
	}

	private static String collectText(WtNode node)
	{
		StringBuilder b = new StringBuilder();
		collectText(node, b);
		return b.toString();
	}

	private static void collectText(WtNode node, StringBuilder b)
	{
		if (node instanceof WtText)
			b.append(((WtText) node).getContent());
		for (WtNode child : node)
			collectText(child, b);
	}
}
