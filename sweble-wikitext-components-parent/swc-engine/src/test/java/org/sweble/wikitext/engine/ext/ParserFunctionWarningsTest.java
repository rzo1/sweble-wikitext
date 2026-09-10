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
import java.util.List;

import org.junit.Test;
import org.sweble.wikitext.engine.ExpansionCallback;
import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.FullPage;
import org.sweble.wikitext.engine.IllegalArgumentsWarning;
import org.sweble.wikitext.engine.InvalidNameWarning;
import org.sweble.wikitext.engine.InvalidPagenameWarning;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.WikitextWarning;
import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;
import org.sweble.wikitext.parser.utils.WtRtDataPrinter;

import de.fau.cs.osr.ptk.common.Warning;

/**
 * Parser functions must not swallow errors silently but report them as
 * warnings on the processed page.
 *
 * Reporting a warning must not change the result of the expansion. Where a
 * test checks the expanded output, the expected value is what the expansion
 * produced before warnings were reported.
 *
 * An argument containing <code>{{Missing}}</code> (a template that does not
 * exist) cannot be converted into plain text, which is used to trigger the
 * <code>StringConversionException</code> paths.
 */
public class ParserFunctionWarningsTest
{
	private final WikiConfigImpl config = DefaultConfigEnWp.generate();

	private final WtEngineImpl engine = new WtEngineImpl(config);

	// =========================================================================
	// == #ifexpr

	@Test
	public void testInvalidExpressionInIfexprIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{#ifexpr: 1 + | yes | no }}");

		assertHasWarning(page, IllegalArgumentsWarning.class, WarningSeverity.NORMAL);
		assertOutput("no", page);
	}

	@Test
	public void testUnconvertibleExpressionInIfexprIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{#ifexpr:{{Missing}}|y|n}}");

		assertHasWarning(page, InvalidNameWarning.class, WarningSeverity.NORMAL);
		assertOutput("n", page);
	}

	@Test
	public void testValidExpressionInIfexprIsNotReported() throws Exception
	{
		List<Warning> warnings = expand("{{#ifexpr: 1 + 1 | yes | no }}").getWarnings();

		assertEquals(0, filterPfnWarnings(warnings).size());
	}

	// =========================================================================
	// == #ifexist

	@Test
	public void testUnconvertiblePageNameInIfexistIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{#ifexist:{{Missing}}|y|n}}");

		assertHasWarning(page, InvalidNameWarning.class, WarningSeverity.NORMAL);
		assertOutput("n", page);
	}

	@Test
	public void testInvalidPageNameInIfexistIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{#ifexist:Foo<Bar|y|n}}");

		assertHasWarning(page, InvalidPagenameWarning.class, WarningSeverity.INFORMATIVE);
		assertOutput("n", page);
	}

	@Test
	public void testFailingExistenceCheckInIfexistIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{#ifexist:Foo|y|n}}", new ThrowingCallback());

		assertHasWarning(page, IllegalArgumentsWarning.class, WarningSeverity.NORMAL);
		assertOutput("n", page);
	}

	// =========================================================================
	// == #titleparts

	@Test
	public void testNonNumericTitlepartsCountIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{#titleparts: Foo/Bar/Baz | many }}");

		assertHasWarning(page, IllegalArgumentsWarning.class, WarningSeverity.INFORMATIVE);
		assertOutput("Foo/Bar/Baz", page);
	}

	@Test
	public void testNonNumericTitlepartsFirstSegmentIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{#titleparts:Foo/Bar/Baz|1|abc}}");

		assertHasWarning(page, IllegalArgumentsWarning.class, WarningSeverity.INFORMATIVE);
		assertOutput("Foo", page);
	}

	@Test
	public void testUnconvertibleArgumentsInTitlepartsAreReported() throws Exception
	{
		EngProcessedPage page = expand("{{#titleparts:{{Missing}}|1}}");

		assertHasWarning(page, IllegalArgumentsWarning.class, WarningSeverity.NORMAL);
		assertOutput("{{#titleparts:{{Missing}}|1}}", page);
	}

	@Test
	public void testInvalidPageNameInTitlepartsIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{#titleparts:Foo<Bar|1}}");

		assertHasWarning(page, InvalidPagenameWarning.class, WarningSeverity.NORMAL);
		assertOutput("{{#titleparts:Foo<Bar|1}}", page);
	}

	// =========================================================================
	// == #time, #timel, convert

	@Test
	public void testUnconvertibleArgumentInTimeIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{#time:{{Missing}}}}");

		assertHasWarning(page, InvalidNameWarning.class, WarningSeverity.NORMAL);
		assertOutput(
				"<strong class=\"error\"><nowiki>Cannot convert format argument to string!</nowiki></strong>",
				page);
	}

	@Test
	public void testUnconvertibleArgumentInTimelIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{#timel:{{Missing}}}}");

		assertHasWarning(page, InvalidNameWarning.class, WarningSeverity.NORMAL);
		assertOutput(
				"<strong class=\"error\"><nowiki>Cannot convert format argument to string!</nowiki></strong>",
				page);
	}

	@Test
	public void testUnconvertibleArgumentInConvertIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{convert|{{Missing}}|m}}");

		assertHasWarning(page, InvalidNameWarning.class, WarningSeverity.NORMAL);
		assertOutput(
				"<strong class=\"error\"><nowiki>Cannot convert argument to string!</nowiki></strong>",
				page);
	}

	// =========================================================================
	// == #tag

	@Test
	public void testUnconvertibleTagNameInTagIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{#tag:{{Missing}}|body}}");

		assertHasWarning(page, InvalidNameWarning.class, WarningSeverity.NORMAL);
		assertOutput("{{#tag:{{Missing}}|body}}", page);
	}

	@Test
	public void testUnconvertibleAttributeInTagIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{#tag:nowiki|body|a={{Missing}}}}");

		assertHasWarning(page, InvalidNameWarning.class, WarningSeverity.NORMAL);
		assertOutput("<nowiki>body</nowiki>", page);
	}

	@Test
	public void testInvalidAttributeNameInTagIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{#tag:nowiki|body|1a=v}}");

		assertHasWarning(page, IllegalArgumentsWarning.class, WarningSeverity.NORMAL);
		assertOutput("<nowiki>body</nowiki>", page);
	}

	// =========================================================================
	// == padleft

	@Test
	public void testUnconvertibleArgumentsInPadleftAreReported() throws Exception
	{
		EngProcessedPage page = expand("{{PADLEFT:<ref>x</ref>|5}}");

		assertHasWarning(page, IllegalArgumentsWarning.class, WarningSeverity.NORMAL);
		assertOutput("<ref>x</ref>", page);
	}

	@Test
	public void testNonNumericLengthInPadleftIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{PADLEFT:7|abc}}");

		assertHasWarning(page, IllegalArgumentsWarning.class, WarningSeverity.NORMAL);
		assertOutput("7", page);
	}

	@Test
	public void testUnconvertiblePadStringInPadleftIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{PADLEFT:7|5|{{Missing}}}}");

		assertHasWarning(page, InvalidNameWarning.class, WarningSeverity.INFORMATIVE);
		assertOutput("00007", page);
	}

	// =========================================================================
	// == ns

	@Test
	public void testUnconvertibleArgumentInNsIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{NS:{{Missing}}}}");

		assertHasWarning(page, InvalidNameWarning.class, WarningSeverity.NORMAL);
		assertOutput("{{NS:{{Missing}}}}", page);
	}

	@Test
	public void testUnknownNamespaceInNsIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{NS:NoSuchNamespace}}");

		assertHasWarning(page, IllegalArgumentsWarning.class, WarningSeverity.NORMAL);
		assertOutput("{{NS:NoSuchNamespace}}", page);
	}

	// =========================================================================
	// == filepath

	@Test
	public void testUnconvertibleFileNameInFilepathIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{FILEPATH:{{Missing}}}}");

		assertHasWarning(page, InvalidNameWarning.class, WarningSeverity.NORMAL);
		assertOutput("{{FILEPATH:{{Missing}}}}", page);
	}

	@Test
	public void testInvalidFileNameInFilepathIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{FILEPATH:Foo<Bar}}");

		assertHasWarning(page, InvalidPagenameWarning.class, WarningSeverity.NORMAL);
		assertOutput("{{FILEPATH:Foo<Bar}}", page);
	}

	@Test
	public void testUnconvertibleOptionsInFilepathAreReported() throws Exception
	{
		EngProcessedPage page = expand("{{FILEPATH:Foo.jpg|{{Missing}}}}");

		assertHasWarning(page, IllegalArgumentsWarning.class, WarningSeverity.INFORMATIVE);
		assertOutput("", page);
	}

	@Test
	public void testNonNumericSizeInFilepathIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{FILEPATH:Foo.jpg|abc}}");

		assertHasWarning(page, IllegalArgumentsWarning.class, WarningSeverity.INFORMATIVE);
		assertOutput("", page);
	}

	@Test
	public void testFailingUrlRetrievalInFilepathIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{FILEPATH:Foo.jpg}}", new ThrowingCallback());

		assertHasWarning(page, IllegalArgumentsWarning.class, WarningSeverity.NORMAL);
		assertOutput("{{FILEPATH:Foo.jpg}}", page);
	}

	// =========================================================================
	// == urlencode

	@Test
	public void testUnknownUrlEncodingIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{urlencode:a b|NO_SUCH_ENCODING}}");

		assertHasWarning(page, IllegalArgumentsWarning.class, WarningSeverity.INFORMATIVE);
		assertOutput("a+b", page);
	}

	@Test
	public void testUnconvertibleUrlEncodingIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{URLENCODE:a b|{{Missing}}}}");

		assertHasWarning(page, InvalidNameWarning.class, WarningSeverity.INFORMATIVE);
		assertOutput("a+b", page);
	}

	@Test
	public void testUnconvertibleTextInUrlencodeIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{URLENCODE:{{Missing}}}}");

		assertHasWarning(page, InvalidNameWarning.class, WarningSeverity.NORMAL);
		assertOutput("{{URLENCODE:{{Missing}}}}", page);
	}

	// =========================================================================
	// == NAMESPACE, PAGENAMEE, TALKPAGENAME

	@Test
	public void testInvalidPageNameInNamespaceVariableIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{NAMESPACE:Foo<Bar}}");

		assertHasWarning(page, InvalidPagenameWarning.class, WarningSeverity.NORMAL);
		assertOutput("{{NAMESPACE:Foo<Bar}}", page);
	}

	@Test
	public void testUnconvertiblePageNameInNamespaceVariableIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{NAMESPACE:{{Missing}}}}");

		assertHasWarning(page, InvalidNameWarning.class, WarningSeverity.NORMAL);
		assertOutput("{{NAMESPACE:{{Missing}}}}", page);
	}

	@Test
	public void testInvalidPageNameInPagenameeVariableIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{PAGENAMEE:Foo<Bar}}");

		assertHasWarning(page, InvalidPagenameWarning.class, WarningSeverity.NORMAL);
		assertOutput("{{PAGENAMEE:Foo<Bar}}", page);
	}

	@Test
	public void testUnconvertiblePageNameInPagenameeVariableIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{PAGENAMEE:{{Missing}}}}");

		assertHasWarning(page, InvalidNameWarning.class, WarningSeverity.NORMAL);
		assertOutput("{{PAGENAMEE:{{Missing}}}}", page);
	}

	@Test
	public void testInvalidPageNameInTalkpagenameVariableIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{TALKPAGENAME:Foo<Bar}}");

		assertHasWarning(page, InvalidPagenameWarning.class, WarningSeverity.NORMAL);
		assertOutput("{{TALKPAGENAME:Foo<Bar}}", page);
	}

	@Test
	public void testUnconvertiblePageNameInTalkpagenameVariableIsReported() throws Exception
	{
		EngProcessedPage page = expand("{{TALKPAGENAME:{{Missing}}}}");

		assertHasWarning(page, InvalidNameWarning.class, WarningSeverity.NORMAL);
		assertOutput("{{TALKPAGENAME:{{Missing}}}}", page);
	}

	// =========================================================================
	// == Valid input

	@Test
	public void testValidInputIsNotReported() throws Exception
	{
		assertNoWarnings("007", "{{PADLEFT:7|3}}");
		assertNoWarnings("Talk", "{{NS:1}}");
		assertNoWarnings("a+b", "{{URLENCODE:a b}}");
		assertNoWarnings("Foo/Bar", "{{#titleparts:Foo/Bar/Baz|2}}");
		assertNoWarnings("<nowiki>body</nowiki>", "{{#tag:nowiki|body|a=v}}");
	}

	// =========================================================================

	private EngProcessedPage expand(String wikitext) throws Exception
	{
		return expand(wikitext, new NullCallback());
	}

	private EngProcessedPage expand(
			String wikitext,
			ExpansionCallback callback) throws Exception
	{
		PageId pageId = new PageId(PageTitle.make(config, "Test"), -1);

		return engine.expand(pageId, wikitext, callback);
	}

	private void assertNoWarnings(String expectedOutput, String wikitext) throws Exception
	{
		EngProcessedPage page = expand(wikitext);

		assertEquals(
				"Unexpected warnings for " + wikitext,
				new ArrayList<Warning>(),
				page.getWarnings());
		assertOutput(expectedOutput, page);
	}

	private static void assertOutput(String expected, EngProcessedPage page)
	{
		assertEquals(expected, WtRtDataPrinter.print(page.getPage()));
	}

	private static List<Warning> filterPfnWarnings(List<Warning> warnings)
	{
		List<Warning> result = new ArrayList<Warning>();
		for (Warning w : warnings)
		{
			if (w instanceof IllegalArgumentsWarning
					|| w instanceof InvalidPagenameWarning
					|| w instanceof InvalidNameWarning)
				result.add(w);
		}
		return result;
	}

	private static void assertHasWarning(
			EngProcessedPage page,
			Class<? extends Warning> type,
			WarningSeverity severity)
	{
		List<Warning> warnings = page.getWarnings();
		for (Warning w : warnings)
		{
			if (type.isInstance(w)
					&& ((WikitextWarning) w).getSeverity() == severity)
				return;
		}
		fail("Expected a " + type.getSimpleName() + " with severity " +
				severity + " but got: " + warnings);
	}

	// =========================================================================

	private static class NullCallback
			implements
				ExpansionCallback
	{
		@Override
		public FullPage retrieveWikitext(
				ExpansionFrame expansionFrame,
				PageTitle pageTitle)
		{
			return null;
		}

		@Override
		public String fileUrl(PageTitle pageTitle, int width, int height)
		{
			return null;
		}
	}

	/**
	 * Simulates a backend that fails while looking up pages and files.
	 */
	private static final class ThrowingCallback
			implements
				ExpansionCallback
	{
		@Override
		public FullPage retrieveWikitext(
				ExpansionFrame expansionFrame,
				PageTitle pageTitle)
		{
			throw new IllegalStateException("Backend failure");
		}

		@Override
		public String fileUrl(PageTitle pageTitle, int width, int height)
		{
			throw new IllegalStateException("Backend failure");
		}
	}
}
