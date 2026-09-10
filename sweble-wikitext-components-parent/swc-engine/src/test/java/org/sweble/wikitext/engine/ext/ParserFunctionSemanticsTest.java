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

package org.sweble.wikitext.engine.ext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.sweble.wikitext.engine.ExpansionCallback;
import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.FullPage;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.TemplateLoopWarning;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.utils.WtRtDataPrinter;

import de.fau.cs.osr.ptk.common.Warning;

/**
 * Parser functions and template expansion must follow the semantics of
 * MediaWiki core and the ParserFunctions extension.
 */
public class ParserFunctionSemanticsTest
{
	private final WikiConfigImpl config = DefaultConfigEnWp.generate();

	private final WtEngineImpl engine = new WtEngineImpl(config);

	private final MapCallback callback = new MapCallback();

	// =========================================================================
	// == #tag

	@Test
	public void testTagStripsDoubleQuotesFromAttributeValue() throws Exception
	{
		assertExpansion("<ref name=\"a b\">x</ref>", "{{#tag:ref|x|name=\"a b\"}}");
	}

	@Test
	public void testTagStripsSingleQuotesFromAttributeValue() throws Exception
	{
		assertExpansion("<ref name=\"a b\">x</ref>", "{{#tag:ref|x|name='a b'}}");
	}

	@Test
	public void testTagTrimsAttributeNameAndValue() throws Exception
	{
		assertExpansion("<ref name=\"a b\">x</ref>", "{{#tag:ref|x| name = \"a b\" }}");
	}

	@Test
	public void testTagKeepsUnquotedAttributeValue() throws Exception
	{
		assertExpansion("<ref name=\"a\">x</ref>", "{{#tag:ref|x|name=a}}");
	}

	// =========================================================================
	// == #ifeq

	@Test
	public void testIfeqDecodesNamedCharacterReferences() throws Exception
	{
		assertExpansion("y", "{{#ifeq: &amp; | & | y | n}}");
	}

	@Test
	public void testIfeqDecodesNumericCharacterReferences() throws Exception
	{
		assertExpansion("y", "{{#ifeq: &#65;&#x42; | AB | y | n}}");
	}

	@Test
	public void testIfeqKeepsUnknownCharacterReferences() throws Exception
	{
		assertExpansion("n", "{{#ifeq: &nosuchentity; | & | y | n}}");
	}

	@Test
	public void testIfeqComparesNaNAsStrings() throws Exception
	{
		assertExpansion("y", "{{#ifeq: NaN | NaN | y | n}}");
	}

	@Test
	public void testIfeqDoesNotTreatJavaFloatSuffixAsNumeric() throws Exception
	{
		assertExpansion("n", "{{#ifeq: 1d | 1 | y | n}}");
	}

	@Test
	public void testIfeqComparesNumbersNumerically() throws Exception
	{
		assertExpansion("y", "{{#ifeq: 01 | 1 | y | n}}");
		assertExpansion("y", "{{#ifeq: 1e3 | 1000 | y | n}}");
		assertExpansion("y", "{{#ifeq: .5 | 0.5 | y | n}}");
	}

	@Test
	public void testIfeqComparesLargeIntegersExactly() throws Exception
	{
		assertExpansion("n", "{{#ifeq: 12345678901234567 | 12345678901234568 | y | n}}");
	}

	@Test
	public void testIfeqDoesNotTreatHexOrInfinityAsNumeric() throws Exception
	{
		assertExpansion("n", "{{#ifeq: 0x1A | 26 | y | n}}");
		assertExpansion("y", "{{#ifeq: Infinity | Infinity | y | n}}");
	}

	// =========================================================================
	// == #switch

	@Test
	public void testSwitchDecodesCharacterReferences() throws Exception
	{
		assertExpansion("amp", "{{#switch: &amp; | & = amp | other }}");
		assertExpansion("amp", "{{#switch: & | &#38; = amp | other }}");
	}

	@Test
	public void testSwitchComparesLikePhp() throws Exception
	{
		assertExpansion("nan", "{{#switch: NaN | NaN = nan | other }}");
		assertExpansion("other", "{{#switch: 1 | 1d = one | other }}");
		assertExpansion("one", "{{#switch: 1.0 | 1 = one | other }}");
	}

	@Test
	public void testSwitchBareDefaultFallsThrough() throws Exception
	{
		assertExpansion("A", "{{#switch: z | #default | a = A }}");
	}

	@Test
	public void testSwitchLastValueWithoutEqualsWinsOverExplicitDefault() throws Exception
	{
		assertExpansion("E", "{{#switch: x | #default = D | E }}");
	}

	@Test
	public void testSwitchExplicitDefault() throws Exception
	{
		assertExpansion("D", "{{#switch: x | a | b = B | #default = D }}");
	}

	@Test
	public void testSwitchMatchingValueWithoutEqualsFallsThrough() throws Exception
	{
		assertExpansion("AB", "{{#switch: a | a | b = AB | C }}");
	}

	// =========================================================================
	// == Template arguments

	@Test
	public void testLaterPositionalArgumentOverridesNumberedArgument() throws Exception
	{
		callback.add("Template:T", "[{{{1}}}]");

		assertExpansion("[b]", "{{T|1=a|b}}");
	}

	@Test
	public void testLaterNumberedArgumentOverridesPositionalArgument() throws Exception
	{
		callback.add("Template:T", "[{{{1}}}]");

		assertExpansion("[a]", "{{T|b|1=a}}");
	}

	// =========================================================================
	// == Template loops

	@Test
	public void testDirectTemplateLoopIsDetected() throws Exception
	{
		callback.add("Template:Loop", "x{{Loop}}");

		EngProcessedPage page = expand("x{{Loop}}");

		assertOutput(
				"xx<span class=\"error\">Template loop detected: [[Template:Loop]]</span>",
				page);
		assertHasWarning(page, TemplateLoopWarning.class);
	}

	@Test
	public void testIndirectTemplateLoopIsDetected() throws Exception
	{
		callback.add("Template:A", "a{{B}}");
		callback.add("Template:B", "b{{A}}");

		EngProcessedPage page = expand("{{A}}");

		assertOutput(
				"ab<span class=\"error\">Template loop detected: [[Template:A]]</span>",
				page);
		assertHasWarning(page, TemplateLoopWarning.class);
	}

	@Test
	public void testRepeatedTransclusionIsNoLoop() throws Exception
	{
		callback.add("Template:T", "[{{{1}}}]");
		callback.add("Template:U", "{{T|{{T|u}}}}");

		assertExpansion("[1][[u]]", "{{T|1}}{{U}}");
	}

	// =========================================================================
	// == padleft

	@Test
	public void testPadleftCastsLengthToInteger() throws Exception
	{
		assertExpansion("0000x", "{{padleft:x|5.5}}");
		assertExpansion("00x", "{{padleft:x|3 apples}}");
	}

	@Test
	public void testPadleftCountsCodePoints() throws Exception
	{
		assertExpansion("00😀", "{{padleft:😀|3}}");
		assertExpansion("😀😀x", "{{padleft:x|3|😀}}");
	}

	@Test
	public void testPadleftLengthIsCappedAt500() throws Exception
	{
		assertExpansion(StringUtils.repeat('0', 499) + "x", "{{padleft:x|1000}}");
	}

	@Test
	public void testPadleftWithNegativeLength() throws Exception
	{
		assertExpansion("x", "{{padleft:x|-5}}");
	}

	// =========================================================================
	// == urlencode

	@Test
	public void testUrlencodeEncodesAsterisk() throws Exception
	{
		assertExpansion("a%2Ab", "{{urlencode:a*b}}");
		assertExpansion("a%2Ab+c", "{{urlencode:a*b c|QUERY}}");
	}

	@Test
	public void testUrlencodePathEncodesAsterisk() throws Exception
	{
		assertExpansion("a%2Ab%20c", "{{urlencode:a*b c|PATH}}");
	}

	@Test
	public void testUrlencodeWikiKeepsAsterisk() throws Exception
	{
		assertExpansion("a*b_c", "{{urlencode:a*b c|WIKI}}");
	}

	// =========================================================================

	private EngProcessedPage expand(String wikitext) throws Exception
	{
		PageId pageId = new PageId(PageTitle.make(config, "Test"), -1);

		return engine.expand(pageId, wikitext, callback);
	}

	private void assertExpansion(String expected, String wikitext) throws Exception
	{
		assertOutput(expected, expand(wikitext));
	}

	private static void assertOutput(String expected, EngProcessedPage page)
	{
		assertEquals(expected, WtRtDataPrinter.print(page.getPage()));
	}

	private static void assertHasWarning(
			EngProcessedPage page,
			Class<? extends Warning> type)
	{
		for (Warning w : page.getWarnings())
		{
			if (type.isInstance(w))
				return;
		}
		fail("Expected a " + type.getSimpleName() + " but got: " + page.getWarnings());
	}

	// =========================================================================

	/**
	 * Serves pages from a map, keyed by their normalized full title.
	 */
	private final class MapCallback
			implements
				ExpansionCallback
	{
		private final Map<String, String> pages = new HashMap<String, String>();

		public void add(String title, String wikitext)
		{
			pages.put(title, wikitext);
		}

		@Override
		public FullPage retrieveWikitext(
				ExpansionFrame expansionFrame,
				PageTitle pageTitle)
		{
			String wikitext = pages.get(pageTitle.getNormalizedFullTitle());
			if (wikitext == null)
				return null;

			return new FullPage(new PageId(pageTitle, -1), wikitext);
		}

		@Override
		public String fileUrl(PageTitle pageTitle, int width, int height)
		{
			return null;
		}
	}
}
