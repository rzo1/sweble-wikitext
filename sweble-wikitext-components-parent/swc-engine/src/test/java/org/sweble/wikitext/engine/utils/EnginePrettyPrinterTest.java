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

package org.sweble.wikitext.engine.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.sweble.wikitext.engine.ExpansionCallback;
import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.FullPage;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngNowiki;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.parser.nodes.WtNode;

public class EnginePrettyPrinterTest
{
	private static final String NOWIKI = "<nowiki>a\n\nb</nowiki>";

	@Test
	public void testNowikiKeepsBlankLines() throws Exception
	{
		WikiConfig config = DefaultConfigEnWp.generate();
		EngNowiki nowiki = config.getNodeFactory().nowiki("a\n\nb");

		assertEquals(NOWIKI, EnginePrettyPrinter.print(nowiki));
		assertEquals(NOWIKI, EngineRtDataPrettyPrinter.print(nowiki));
	}

	@Test
	public void testNowikiRoundTrip() throws Exception
	{
		WikiConfig config = DefaultConfigEnWp.generate();
		WtEngineImpl engine = new WtEngineImpl(config);
		PageId pageId = new PageId(PageTitle.make(config, "Test"), -1);

		// Nowiki elements are only created during expansion
		ExpansionCallback callback = new NoPagesCallback();

		EngProcessedPage page = engine.postprocess(pageId, "x " + NOWIKI + " y", callback);
		EngNowiki nowiki = findNowiki(page);
		assertNotNull(nowiki);
		assertEquals("a\n\nb", nowiki.getContent());

		String printed = EnginePrettyPrinter.print(page);

		EngNowiki reparsed = findNowiki(engine.postprocess(pageId, printed, callback));
		assertNotNull(reparsed);
		assertEquals(nowiki.getContent(), reparsed.getContent());
	}

	private static final class NoPagesCallback
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

	private static EngNowiki findNowiki(WtNode node)
	{
		if (node instanceof EngNowiki)
			return (EngNowiki) node;
		for (WtNode child : node)
		{
			EngNowiki found = findNowiki(child);
			if (found != null)
				return found;
		}
		return null;
	}
}
