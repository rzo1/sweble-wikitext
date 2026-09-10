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

package org.sweble.wikitext.dumpreader;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.sweble.wikitext.dumpreader.model.DumpConverter;
import org.sweble.wikitext.dumpreader.model.Page;
import org.sweble.wikitext.dumpreader.model.Revision;
import org.sweble.wikitext.dumpreader.model.UnsupportedDumpFormat;

import de.fau.cs.osr.utils.StringTools;

/**
 * Keeps the "Getting started" dump reader snippet of the README compiling and
 * working.
 */
public class GettingStartedTest
{
	@Test
	public void testReadDump() throws Exception
	{
		File dumpFile = new File(StringTools.decodeUsingDefaultCharset(
				getClass().getResource("/input-0.10.xml").getFile()));

		final DumpConverter converter = new DumpConverter();
		final List<String> seen = new ArrayList<String>();

		try (InputStream in = new FileInputStream(dumpFile);
				DumpReader reader = new DumpReader(
						in,
						StandardCharsets.UTF_8,
						dumpFile.getAbsolutePath(),
						LoggerFactory.getLogger(getClass()),
						true)
				{
					@Override
					protected void processPage(Object mediaWiki, Object page)
					{
						try
						{
							Page p = converter.convertPage(page);
							for (Revision r : p.getRevisions())
								seen.add(p.getTitle() + ": " + r.getText());
						}
						catch (UnsupportedDumpFormat e)
						{
							throw new IllegalStateException(e);
						}
					}
				})
		{
			reader.unmarshal();
		}

		assertEquals(1, seen.size());
		assertEquals("TITLE: TEXT", seen.get(0));
	}
}
