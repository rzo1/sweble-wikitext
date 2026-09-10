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

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Test;

public class AppArgumentsTest
{
	@Test
	public void testNoArgumentsPrintUsage() throws Exception
	{
		assertTrue(runMain().contains("Usage:"));
	}

	@Test
	public void testOptionWithoutTitlePrintsUsage() throws Exception
	{
		assertTrue(runMain("--text").contains("Usage:"));
		assertTrue(runMain("--html").contains("Usage:"));
	}

	@Test
	public void testTooManyArgumentsPrintUsage() throws Exception
	{
		assertTrue(runMain("--text", "Simple_Page", "extra").contains("Usage:"));
		assertTrue(runMain("Simple_Page", "extra").contains("Usage:"));
	}

	private static String runMain(String... args) throws Exception
	{
		PrintStream saveErr = System.err;
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		System.setErr(new PrintStream(err, true, "UTF-8"));
		try
		{
			App.main(args);
		}
		finally
		{
			System.setErr(saveErr);
		}
		return err.toString("UTF-8");
	}
}
