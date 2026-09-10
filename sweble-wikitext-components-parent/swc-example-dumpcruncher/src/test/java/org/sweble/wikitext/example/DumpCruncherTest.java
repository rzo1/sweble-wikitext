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

package org.sweble.wikitext.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import de.fau.cs.osr.utils.getopt.Options;

/**
 * Parses the command line of the {@link DumpCruncher} without crunching a
 * dump.
 */
public class DumpCruncherTest
{
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void testCommandLine() throws Exception
	{
		DumpCruncher cruncher = new DumpCruncher();

		assertTrue(cruncher.parseOptions(new String[] {
				"--dump", "enwiki-pages-articles.xml.bz2",
				"--processing-workers", "2" }));

		Options options = cruncher.getOptions();
		assertEquals("enwiki-pages-articles.xml.bz2", options.value("dump"));
		assertEquals(2, (int) options.value("processing-workers", int.class));
		assertEquals(2, (int) options.value("Nexus.NumProcessingWorkers", int.class));
		assertEquals(8, (int) options.value("Nexus.InTrayCapacity", int.class));
		assertEquals(8, (int) options.value("Nexus.ProcessedJobsCapacity", int.class));
		assertEquals(8, (int) options.value("Nexus.OutTrayCapacity", int.class));
	}

	@Test
	public void testPropertiesFile() throws Exception
	{
		File properties = tmp.newFile("dumpcruncher.properties");
		Files.write(properties.toPath(), Arrays.asList(
				"DumpCruncher.File = dump.xml",
				"Nexus.InTrayCapacity = 16"), StandardCharsets.ISO_8859_1);

		DumpCruncher cruncher = new DumpCruncher();

		assertTrue(cruncher.parseOptions(new String[] {
				"-P", properties.getPath() }));

		Options options = cruncher.getOptions();
		assertEquals("dump.xml", options.value("dump"));
		assertEquals(16, (int) options.value("Nexus.InTrayCapacity", int.class));
		assertEquals(4, (int) options.value("Nexus.NumProcessingWorkers", int.class));
	}

	@Test
	public void testMissingDumpIsRejected() throws Exception
	{
		assertFalse(new DumpCruncher().parseOptions(new String[0]));
	}

	@Test
	public void testUnknownOptionIsRejected() throws Exception
	{
		assertFalse(new DumpCruncher().parseOptions(new String[] {
				"--dump", "dump.xml",
				"--no-such-option" }));
	}

	@Test
	public void testHelpDoesNotCrunch() throws Exception
	{
		assertFalse(new DumpCruncher().parseOptions(new String[] { "--help" }));
	}
}
