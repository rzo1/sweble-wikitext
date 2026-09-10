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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

	@Test
	public void testTagWithoutBody() throws Exception
	{
		assertExpansion("<nowiki />", "{{#tag:nowiki}}");
		assertExpansion("<nowiki />", "{{#tag: NoWiki }}");
	}

	@Test
	public void testTagKeepsEqualsSignInContent() throws Exception
	{
		// Like MediaWiki's tagObj(), which expands the whole content argument
		assertExpansion("<nowiki>a=b</nowiki>", "{{#tag:nowiki|a=b}}");
		assertExpansion("<ref name=\"y\">name=x</ref>", "{{#tag:ref|name=x|name=y}}");
	}

	@Test
	public void testTagNameIsLowercased() throws Exception
	{
		assertExpansion("<ref>x</ref>", "{{#tag:REF|x}}");
	}

	@Test
	public void testTagNameDoesNotDependOnDefaultLocale() throws Exception
	{
		Locale defaultLocale = Locale.getDefault();
		try
		{
			Locale.setDefault(new Locale("tr"));

			assertExpansion("<nowiki>x</nowiki>", "{{#tag:NOWIKI|x}}");
		}
		finally
		{
			Locale.setDefault(defaultLocale);
		}
	}

	// =========================================================================
	// == #iferror

	@Test
	public void testIferrorDetectsErrorClassOfSpanAndDiv() throws Exception
	{
		assertExpansion("y", "{{#iferror: <span class=\"error\">x</span> | y | n }}");
		assertExpansion("y", "{{#iferror: <div class=\"error\">x</div> | y | n }}");
		assertExpansion("y", "{{#iferror: <p class=\"error\">x</p> | y | n }}");
		assertExpansion("y", "{{#iferror: <strong class=\"error\">x</strong> | y | n }}");
		assertExpansion("y", "{{#iferror: <span id=\"a\" class=\"big error mw-x\">x</span> | y | n }}");
	}

	@Test
	public void testIferrorIgnoresElementsWithoutErrorClass() throws Exception
	{
		assertExpansion("n", "{{#iferror: <strong>x</strong> | y | n }}");
		assertExpansion("n", "{{#iferror: <strong>x</strong> class=\"error\" | y | n }}");
		assertExpansion("n", "{{#iferror: <span class=\"errors\">x</span> | y | n }}");
		assertExpansion("n", "{{#iferror: <b class=\"error\">x</b> | y | n }}");
	}

	@Test
	public void testIferrorDetectsErrorsOfParserFunctions() throws Exception
	{
		assertExpansion("y", "{{#iferror: {{#expr: 1/0 }} | y | n }}");
		assertExpansion("y", "{{#iferror: {{#ifexpr: 1/0 | a | b }} | y | n }}");
		assertExpansion("y", "{{#iferror: {{#rel2abs: ../.. | Foo }} | y | n }}");
	}

	@Test
	public void testIferrorDoesNotSearchNowiki() throws Exception
	{
		// Like the strip markers in MediaWiki
		assertExpansion("n", "{{#iferror: <nowiki><span class=\"error\">x</span></nowiki> | y | n }}");
		assertExpansion("n", "{{#iferror: a<nowiki><span class=\"error\">x</span></nowiki> | y | n }}");
		assertExpansion("y", "{{#iferror: <nowiki>a</nowiki><span class=\"error\">x</span> | y | n }}");
	}

	@Test
	public void testNowikiInFirstArgumentOfOtherFunctionsIsStillConverted() throws Exception
	{
		assertExpansion("y", "{{#ifeq: <nowiki>a</nowiki> | a | y | n }}");
		assertExpansion("ab", "{{lc: <nowiki>AB</nowiki> }}");
	}

	@Test
	public void testIferrorReturnsTestStringIfThereIsNoElseBranch() throws Exception
	{
		assertExpansion("abc", "{{#iferror: abc | error }}");
		assertExpansion("<strong>x</strong>", "{{#iferror: <strong>x</strong> }}");
		assertExpansion("", "{{#iferror: {{#expr: 1/0 }} }}");
	}

	@Test
	public void testIferrorCanBeUsedByConcurrentThreads() throws Exception
	{
		final int threads = 8;
		final int iterations = 200;

		ExecutorService executor = Executors.newFixedThreadPool(threads);
		try
		{
			List<Future<Void>> results = new ArrayList<Future<Void>>();
			for (int i = 0; i < threads; ++i)
			{
				final String value = "value" + i;
				results.add(executor.submit(() -> {
					for (int j = 0; j < iterations; ++j)
						assertExpansion(value, "{{#iferror: " + value + " | error }}");
					return null;
				}));
			}

			for (Future<Void> result : results)
				result.get();
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	// =========================================================================
	// == #ifexpr

	@Test
	public void testIfexprReturnsExpressionError() throws Exception
	{
		assertExpansion(
				"<strong class=\"error\">Expression error: Missing operand for +.</strong>",
				"{{#ifexpr: 1 + | yes | no }}");
		assertExpansion(
				"<strong class=\"error\">Division by zero.</strong>",
				"{{#ifexpr: 1/0 }}");
	}

	@Test
	public void testIfexprChoosesBranch() throws Exception
	{
		assertExpansion("yes", "{{#ifexpr: 1 = 1 | yes | no }}");
		assertExpansion("no", "{{#ifexpr: 1 = 2 | yes | no }}");
		assertExpansion("no", "{{#ifexpr: | yes | no }}");
		assertExpansion("", "{{#ifexpr: 1 = 2 | yes }}");
	}

	// =========================================================================
	// == #rel2abs

	@Test
	public void testRel2absWithRelativePaths() throws Exception
	{
		assertExpansion("Help:Foo/bar/baz/quok", "{{#rel2abs: /quok | Help:Foo/bar/baz }}");
		assertExpansion("Help:Foo/bar/baz/quok", "{{#rel2abs: ./quok | Help:Foo/bar/baz }}");
		assertExpansion("Help:Foo/bar/quok", "{{#rel2abs: ../quok | Help:Foo/bar/baz }}");
		assertExpansion("Help:Foo/bar", "{{#rel2abs: ../. | Help:Foo/bar/baz }}");
		assertExpansion("Help:Foo/bar/quok", "{{#rel2abs: ../quok/. | Help:Foo/bar/baz }}");
		assertExpansion("Help:Foo/quok", "{{#rel2abs: ../../quok | Help:Foo/bar/baz }}");
		assertExpansion("quok", "{{#rel2abs: ../../../quok | Help:Foo/bar/baz }}");
		assertExpansion("Help:Foo", "{{#rel2abs: .. | Help:Foo/bar }}");
		assertExpansion("Help:Foo/bar/a/b", "{{#rel2abs: ./a//b/ | Help:Foo/bar }}");
	}

	@Test
	public void testRel2absWithAbsolutePath() throws Exception
	{
		assertExpansion("quok", "{{#rel2abs: quok | Help:Foo/bar/baz }}");
		assertExpansion("a/b", "{{#rel2abs: a/./b | Help:Foo }}");
	}

	@Test
	public void testRel2absIsRelativeToCurrentPage() throws Exception
	{
		assertExpansion("Help:Foo/baz", "Help:Foo/bar", "{{#rel2abs: ../baz }}");
		assertExpansion("Help:Foo/bar/baz", "Help:Foo/bar", "{{#rel2abs: /baz }}");
		assertExpansion("Help:Foo/bar", "Help:Foo/bar", "{{#rel2abs: }}");
		assertExpansion("Help:Foo/bar", "Help:Foo/bar", "{{#rel2abs: . }}");
	}

	@Test
	public void testRel2absAboveRootNodeIsAnError() throws Exception
	{
		assertExpansion(
				"<strong class=\"error\">Error: Invalid depth in path: \"Help:Foo/bar/baz/../../../../quok\" "
						+ "(tried to access a node above the root node).</strong>",
				"{{#rel2abs: ../../../../quok | Help:Foo/bar/baz }}");
	}

	// =========================================================================
	// == #ifexist

	@Test
	public void testIfexistCoreSpecialPagesExist() throws Exception
	{
		assertExpansion("y", "{{#ifexist:Special:RecentChanges|y|n}}");
		assertExpansion("y", "{{#ifexist:Special:recentchanges|y|n}}");
		assertExpansion("y", "{{#ifexist:Special:Search|y|n}}");
		assertExpansion("y", "{{#ifexist:Special:Contributions/Foo|y|n}}");
	}

	@Test
	public void testIfexistUnknownSpecialPageDoesNotExist() throws Exception
	{
		assertExpansion("n", "{{#ifexist:Special:NoSuchSpecialPage|y|n}}");
		assertExpansion("n", "{{#ifexist:Special:JavaScriptTest|y|n}}");
	}

	@Test
	public void testIfexistAsksCallbackForOtherSpecialPages() throws Exception
	{
		// E.g. special pages of extensions
		callback.add("Special:CiteThisPage", "");

		assertExpansion("y", "{{#ifexist:Special:CiteThisPage|y|n}}");
	}

	@Test
	public void testIfexistUsesCallback() throws Exception
	{
		callback.add("Foo", "x");

		assertExpansion("y", "{{#ifexist:Foo|y|n}}");
		assertExpansion("n", "{{#ifexist:Bar|y|n}}");
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
		return expand("Test", wikitext);
	}

	private EngProcessedPage expand(String title, String wikitext) throws Exception
	{
		PageId pageId = new PageId(PageTitle.make(config, title), -1);

		return engine.expand(pageId, wikitext, callback);
	}

	private void assertExpansion(String expected, String wikitext) throws Exception
	{
		assertOutput(expected, expand(wikitext));
	}

	private void assertExpansion(String expected, String title, String wikitext) throws Exception
	{
		assertOutput(expected, expand(title, wikitext));
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
