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

package org.sweble.wikitext.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtPageSwitch;
import org.sweble.wikitext.parser.nodes.WtText;
import org.sweble.wikitext.parser.utils.NonExpandingParser;
import org.sweble.wikitext.parser.utils.SimpleParserConfig;
import org.sweble.wikitext.parser.utils.WtRtDataPrinter;

/**
 * Page switches (behavior switches) like {@code __NOTOC__}
 * (rzo1/sweble-wikitext#100).
 */
public class PageSwitchTest
{
	@Test
	public void testEnglishSwitchesAreRecognized() throws Exception
	{
		SwitchParserConfig config = new SwitchParserConfig();

		WtNode ast = parse(config, "__NOTOC__ __TOC__ __FORCETOC__ __NOEDITSECTION__");

		assertEquals(Arrays.asList("NOTOC", "TOC", "FORCETOC", "NOEDITSECTION"), switchNames(ast));
		assertEquals("   ", text(ast));
	}

	@Test
	public void testNamesArePassedWithoutUnderscores() throws Exception
	{
		SwitchParserConfig config = new SwitchParserConfig();

		parse(config, "__NOTOC__");

		assertTrue(config.queried.contains("NOTOC"));
		assertFalse(config.queried.contains("__NOTOC__"));
	}

	@Test
	public void testLocalizedSwitchIsRecognized() throws Exception
	{
		WtNode ast = parse(new SwitchParserConfig(), "Text __KEININHALTSVERZEICHNIS__ Text");

		assertEquals(Arrays.asList("KEININHALTSVERZEICHNIS"), switchNames(ast));
		assertEquals("Text  Text", text(ast));
	}

	@Test
	public void testSwitchNamesWithUnderscores() throws Exception
	{
		WtNode ast = parse(
				new SwitchParserConfig(),
				"__KEIN_INHALTSVERZEICHNIS__ __INHALTSVERZEICHNIS_ERZWINGEN__ __БЕЗ_ОГЛАВЛЕНИЯ__");

		assertEquals(
				Arrays.asList("KEIN_INHALTSVERZEICHNIS", "INHALTSVERZEICHNIS_ERZWINGEN", "БЕЗ_ОГЛАВЛЕНИЯ"),
				switchNames(ast));
		assertEquals("  ", text(ast));
	}

	@Test
	public void testSwitchesNextToText() throws Exception
	{
		WtNode ast = parse(new SwitchParserConfig(), "a__NOTOC__b __KEIN_INHALTSVERZEICHNIS___c");

		assertEquals(Arrays.asList("NOTOC", "KEIN_INHALTSVERZEICHNIS"), switchNames(ast));
		assertEquals("ab _c", text(ast));
	}

	@Test
	public void testUnknownSwitchesStayText() throws Exception
	{
		String wikitext = "__FOO__ __FOO_BAR__ __NOTOC_X__ __KEIN__INHALTSVERZEICHNIS__ __KEIN_ INHALTSVERZEICHNIS__";

		WtNode ast = parse(new SwitchParserConfig(), wikitext);

		assertEquals(Collections.emptyList(), switchNames(ast));
		assertEquals(wikitext, text(ast));
	}

	@Test
	public void testSwitchesAreRoundTripped() throws Exception
	{
		String wikitext = "__NOTOC__\n__KEIN_INHALTSVERZEICHNIS__ __FOO_BAR__ Text__БЕЗ_ОГЛАВЛЕНИЯ__\n";

		WtNode ast = parse(new SwitchParserConfig(), wikitext);

		assertEquals(wikitext, WtRtDataPrinter.print(ast));
	}

	@Test
	public void testSimpleParserConfigOnlyKnowsNotoc() throws Exception
	{
		WtNode ast = parse(new SimpleParserConfig(), "__NOTOC__ __KEIN_INHALTSVERZEICHNIS__");

		assertEquals(Arrays.asList("NOTOC"), switchNames(ast));
		assertEquals(" __KEIN_INHALTSVERZEICHNIS__", text(ast));
	}

	// =========================================================================

	private WtNode parse(ParserConfig config, String wikitext) throws Exception
	{
		return new NonExpandingParser(config).parseArticle(wikitext, "title");
	}

	private List<String> switchNames(WtNode ast)
	{
		List<String> names = new ArrayList<String>();
		collectSwitchNames(ast, names);
		return names;
	}

	private void collectSwitchNames(WtNode node, List<String> names)
	{
		if (node instanceof WtPageSwitch)
			names.add(((WtPageSwitch) node).getName());
		for (WtNode child : node)
			collectSwitchNames(child, names);
	}

	/**
	 * Concatenates all text nodes, ignoring the paragraph structure and
	 * newlines.
	 */
	private String text(WtNode ast)
	{
		StringBuilder sb = new StringBuilder();
		collectText(ast, sb);
		return sb.toString().replace("\n", "");
	}

	private void collectText(WtNode node, StringBuilder sb)
	{
		if (node instanceof WtText)
			sb.append(((WtText) node).getContent());
		for (WtNode child : node)
			collectText(child, sb);
	}

	// =========================================================================

	/**
	 * A test configuration that knows some English, German and Russian
	 * behavior switch names.
	 */
	private static final class SwitchParserConfig
			extends
				SimpleParserConfig
	{
		private final Set<String> names = new HashSet<String>(Arrays.asList(
				"NOTOC",
				"TOC",
				"FORCETOC",
				"NOEDITSECTION",
				"KEININHALTSVERZEICHNIS",
				"KEIN_INHALTSVERZEICHNIS",
				"INHALTSVERZEICHNIS_ERZWINGEN",
				"БЕЗ_ОГЛАВЛЕНИЯ"));

		private final Set<String> queried = new HashSet<String>();

		@Override
		public boolean isValidPageSwitchName(String name)
		{
			queried.add(name);
			return names.contains(name);
		}
	}
}
