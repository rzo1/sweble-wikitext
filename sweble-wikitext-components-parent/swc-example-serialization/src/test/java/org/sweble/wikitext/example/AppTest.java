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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

import de.fau.cs.osr.utils.getopt.Options;

/**
 * Parses the command line of the serialization {@link App} without
 * serializing anything.
 */
public class AppTest
{
	@Test
	public void testCommandLine()
	{
		Options opt = App.parseOptions(new String[] {
				"-f", "json",
				"--simplify",
				"--strip-location",
				"Germany" });

		assertNotNull(opt);
		assertEquals("json", opt.value("format"));
		assertTrue(opt.has("simplify"));
		assertTrue(opt.has("strip-location"));
		assertFalse(opt.has("timings"));
		assertEquals(Arrays.asList("Germany"), opt.getFreeArguments());
	}

	@Test
	public void testMissingTitleIsRejected()
	{
		assertNull(App.parseOptions(new String[] { "--format", "xml" }));
	}

	@Test
	public void testMissingFormatIsRejected()
	{
		assertNull(App.parseOptions(new String[] { "Germany" }));
	}

	@Test
	public void testUnknownOptionIsRejected()
	{
		assertNull(App.parseOptions(new String[] {
				"-f", "xml",
				"--no-such-option",
				"Germany" }));
	}
}
