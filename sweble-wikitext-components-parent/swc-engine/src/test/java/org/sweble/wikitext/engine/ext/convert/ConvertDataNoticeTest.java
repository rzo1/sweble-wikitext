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

package org.sweble.wikitext.engine.ext.convert;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * The attribution of the unit data (CC BY-SA 4.0) is in the NOTICE file of
 * swc-convert-data and repeated in the NOTICE file of the repository root.
 * Both have to name the revision the data was generated from.
 */
public class ConvertDataNoticeTest
{
	/** The tests of swc-engine run in its directory. */
	private static final Path ROOT = Paths.get("..", "..");

	private static final Path ROOT_NOTICE = ROOT.resolve("NOTICE");

	private static final Path MODULE_NOTICE = ROOT.resolve(
			"sweble-wikitext-components-parent/swc-convert-data/NOTICE");

	private static final Pattern REVISION = Pattern.compile("\\(revision (\\d+)\\)");

	@Test
	public void testRootNoticeRepeatsTheNoticeOfSwcConvertData() throws IOException
	{
		assertTrue(
				"The NOTICE of the repository root has to contain " + MODULE_NOTICE + " verbatim",
				read(ROOT_NOTICE).contains(read(MODULE_NOTICE)));
	}

	@Test
	public void testNoticesNameTheRevisionOfTheData() throws IOException
	{
		String revision = "revision " + dataRevision();
		assertTrue(MODULE_NOTICE + " doesn't name " + revision, read(MODULE_NOTICE).contains(revision));
		assertTrue(ROOT_NOTICE + " doesn't name " + revision, read(ROOT_NOTICE).contains(revision));
	}

	private static String read(Path path) throws IOException
	{
		return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
	}

	/**
	 * @return The revision in the header of the data file.
	 */
	private static String dataRevision() throws IOException
	{
		InputStream in = ConvertDataNoticeTest.class.getClassLoader().getResourceAsStream(ConvertData.RESOURCE);
		assertNotNull(in);
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = r.readLine()) != null && line.startsWith("#"))
			{
				Matcher m = REVISION.matcher(line);
				if (m.find())
				{
					return m.group(1);
				}
			}
		}
		fail("No revision in the header of " + ConvertData.RESOURCE);
		return null;
	}
}
