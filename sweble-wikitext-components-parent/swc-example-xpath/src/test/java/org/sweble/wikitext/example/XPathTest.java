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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Test;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;

public class XPathTest
{
	private static EngProcessedPage process(String wikitext) throws Exception
	{
		WikiConfig config = DefaultConfigEnWp.generate();
		WtEngineImpl engine = new WtEngineImpl(config);
		PageTitle pageTitle = PageTitle.make(config, "Test");
		return engine.postprocess(new PageId(pageTitle, -1), wikitext, null);
	}

	@Test
	public void testNumberResultIsPrinted() throws Exception
	{
		String actual = XPath.query(process("Hello ''world''!"), "count(//WtItalics)");

		assertEquals("(count(//WtItalics))[1]:\n\"\"\"1.0\"\"\"\n\n", actual);
	}

	@Test
	public void testStringResultIsPrinted() throws Exception
	{
		String actual = XPath.query(process("Hello ''world''!"), "concat('a', 'b')");

		assertEquals("(concat('a', 'b'))[1]:\n\"\"\"ab\"\"\"\n\n", actual);
	}

	@Test
	public void testAttributeResultIsPrinted() throws Exception
	{
		String actual = XPath.query(process("== Heading ==\nBody"), "//WtSection/@level");

		assertEquals("(//WtSection/@level)[1]:\n\"\"\"2\"\"\"\n\n", actual);
	}

	@Test
	public void testNodeResultIsPrintedAsWikitext() throws Exception
	{
		String actual = XPath.query(process("Hello ''world''!"), "//WtItalics");

		assertEquals("(//WtItalics)[1]:\n\"\"\"''world''\"\"\"\n\n", actual);
	}

	@Test
	public void testTooFewArgumentsPrintUsage() throws Exception
	{
		assertTrue(runMain().contains("Usage:"));
		assertTrue(runMain("Simple_Page").contains("Usage:"));
	}

	@Test
	public void testTooManyArgumentsPrintUsage() throws Exception
	{
		assertTrue(runMain("Simple_Page", "//WtText", "extra").contains("Usage:"));
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
