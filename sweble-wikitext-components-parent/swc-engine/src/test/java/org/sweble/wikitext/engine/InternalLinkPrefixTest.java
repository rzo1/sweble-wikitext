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

package org.sweble.wikitext.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.sweble.wikitext.engine.config.ParserConfigImpl;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.utils.DefaultConfig;
import org.sweble.wikitext.parser.WikitextParser;
import org.sweble.wikitext.parser.nodes.WtInternalLink;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtText;
import org.sweble.wikitext.parser.utils.WtRtDataPrinter;

/**
 * Prefix and postfix of internal links with and without configured patterns.
 */
public class InternalLinkPrefixTest
{
	@Test
	public void testNoPrefixWithoutPrefixPattern() throws Exception
	{
		ParserConfigImpl config = defaultParserConfig();
		assertNull(config.getInternalLinkPrefixPattern());

		List<WtInternalLink> links = new ArrayList<WtInternalLink>();
		List<String> texts = new ArrayList<String>();
		// Used to be compiled to the pattern "(null)$"
		collect(parse(config, "xnull[[Foo]]"), links, texts);

		assertEquals(1, links.size());
		assertEquals("", links.get(0).getPrefix());
		assertEquals("[[Foo]]", WtRtDataPrinter.print(links.get(0)));
		assertEquals("[xnull]", texts.toString());
	}

	@Test
	public void testNoPostfixWithoutPostfixPattern() throws Exception
	{
		ParserConfigImpl config = defaultParserConfig();
		assertNull(config.getInternalLinkPostfixPattern());

		List<WtInternalLink> links = new ArrayList<WtInternalLink>();
		List<String> texts = new ArrayList<String>();
		collect(parse(config, "[[Foo]]bar"), links, texts);

		assertEquals(1, links.size());
		assertEquals("", links.get(0).getPostfix());
		assertEquals("[bar]", texts.toString());
	}

	@Test
	public void testEmptyPatternsMeanNoPrefixAndNoPostfix() throws Exception
	{
		ParserConfigImpl config = defaultParserConfig();
		config.setInternalLinkPrefixPattern("");
		config.setInternalLinkPostfixPattern("");

		List<WtInternalLink> links = new ArrayList<WtInternalLink>();
		List<String> texts = new ArrayList<String>();
		collect(parse(config, "xnull[[Foo]]bar"), links, texts);

		assertEquals(1, links.size());
		assertEquals("", links.get(0).getPrefix());
		assertEquals("", links.get(0).getPostfix());
		assertEquals("[xnull, bar]", texts.toString());
	}

	@Test
	public void testConfiguredPrefixPatternIsApplied() throws Exception
	{
		ParserConfigImpl config = defaultParserConfig();
		config.setInternalLinkPrefixPattern("[a-z]+");

		List<WtInternalLink> links = new ArrayList<WtInternalLink>();
		List<String> texts = new ArrayList<String>();
		collect(parse(config, "Hello xnull[[Foo]]"), links, texts);

		assertEquals(1, links.size());
		assertEquals("xnull", links.get(0).getPrefix());
		assertEquals("xnull[[Foo]]", WtRtDataPrinter.print(links.get(0)));
		assertTrue(texts.toString(), texts.contains("Hello "));
	}

	// =========================================================================

	/**
	 * The parser configuration of {@link DefaultConfig}, which configures
	 * neither a prefix nor a postfix pattern.
	 */
	private static ParserConfigImpl defaultParserConfig()
	{
		WikiConfigImpl c = new WikiConfigImpl();
		new DefaultConfig()
		{
			{
				configureParser(c);
			}
		};
		return c.getParserConfig();
	}

	private static WtNode parse(ParserConfigImpl config, String wikitext) throws Exception
	{
		return new WikitextParser(config).parseArticle(wikitext, "Test");
	}

	private static void collect(WtNode node, List<WtInternalLink> links, List<String> texts)
	{
		if (node instanceof WtInternalLink)
		{
			links.add((WtInternalLink) node);
			return;
		}
		if (node instanceof WtText)
			texts.add(((WtText) node).getContent());
		for (WtNode child : node)
			collect(child, links, texts);
	}
}
