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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.sweble.wikitext.engine.ExpansionCallback;
import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.FullPage;
import org.sweble.wikitext.engine.IllegalArgumentsWarning;
import org.sweble.wikitext.engine.InvalidPagenameWarning;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.WikitextWarning;
import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;

import de.fau.cs.osr.ptk.common.Warning;

/**
 * Parser functions must not swallow errors silently but report them as
 * warnings on the processed page.
 */
public class ParserFunctionWarningsTest
{
	private final WikiConfigImpl config = DefaultConfigEnWp.generate();

	private final WtEngineImpl engine = new WtEngineImpl(config);

	// =========================================================================

	@Test
	public void testInvalidExpressionInIfexprIsReported() throws Exception
	{
		List<Warning> warnings = expandAndGetWarnings("{{#ifexpr: 1 + | yes | no }}");

		assertHasWarning(warnings, IllegalArgumentsWarning.class, WarningSeverity.NORMAL);
	}

	@Test
	public void testValidExpressionInIfexprIsNotReported() throws Exception
	{
		List<Warning> warnings = expandAndGetWarnings("{{#ifexpr: 1 + 1 | yes | no }}");

		assertEquals(0, filterPfnWarnings(warnings).size());
	}

	@Test
	public void testInvalidPageNameInNamespaceVariableIsReported() throws Exception
	{
		List<Warning> warnings = expandAndGetWarnings("{{NAMESPACE:Foo<Bar}}");

		assertHasWarning(warnings, InvalidPagenameWarning.class, WarningSeverity.NORMAL);
	}

	@Test
	public void testUnknownUrlEncodingIsReported() throws Exception
	{
		List<Warning> warnings = expandAndGetWarnings("{{urlencode:a b|NO_SUCH_ENCODING}}");

		assertHasWarning(warnings, IllegalArgumentsWarning.class, WarningSeverity.INFORMATIVE);
	}

	@Test
	public void testNonNumericTitlepartsCountIsReported() throws Exception
	{
		List<Warning> warnings = expandAndGetWarnings("{{#titleparts: Foo/Bar/Baz | many }}");

		assertHasWarning(warnings, IllegalArgumentsWarning.class, WarningSeverity.INFORMATIVE);
	}

	// =========================================================================

	private List<Warning> expandAndGetWarnings(String wikitext) throws Exception
	{
		PageId pageId = new PageId(PageTitle.make(config, "Test"), -1);

		EngProcessedPage page = engine.expand(pageId, wikitext, new NullCallback());

		return page.getWarnings();
	}

	private static List<Warning> filterPfnWarnings(List<Warning> warnings)
	{
		List<Warning> result = new ArrayList<Warning>();
		for (Warning w : warnings)
		{
			if (w instanceof IllegalArgumentsWarning
					|| w instanceof InvalidPagenameWarning)
				result.add(w);
		}
		return result;
	}

	private static void assertHasWarning(
			List<Warning> warnings,
			Class<? extends Warning> type,
			WarningSeverity severity)
	{
		for (Warning w : warnings)
		{
			if (type.isInstance(w)
					&& ((WikitextWarning) w).getSeverity() == severity)
				return;
		}
		assertTrue(
				"Expected a " + type.getSimpleName() + " with severity " +
						severity + " but got: " + warnings,
				false);
	}

	// =========================================================================

	private static final class NullCallback
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
}
