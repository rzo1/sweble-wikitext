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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Test;
import org.sweble.wikitext.articlecruncher.Job;
import org.sweble.wikitext.articlecruncher.JobTraceSet;
import org.sweble.wikitext.articlecruncher.utils.AbortHandler;

import de.fau.cs.osr.utils.StringTools;

public class DumpReaderJobGeneratorTest
{
	/**
	 * 2012-05-21T11:11:11Z
	 */
	private static final long TIMESTAMP = 1337598671000L;

	@Test
	public void testExport_0_5() throws Throwable
	{
		List<RevisionJob> jobs = generateJobs("/dump-0.5.xml");

		assertJobs(jobs, null, "");
	}

	@Test
	public void testExport_0_10() throws Throwable
	{
		List<RevisionJob> jobs = generateJobs("/dump-0.10.xml");

		assertJobs(jobs, BigInteger.ZERO, "Example");
	}

	@Test
	public void testExport_0_11() throws Throwable
	{
		List<RevisionJob> jobs = generateJobs("/dump-0.11.xml");

		assertJobs(jobs, BigInteger.ZERO, "Example");
	}

	@Test
	public void testFileSizeIsKnown() throws Throwable
	{
		File file = getFile("/dump-0.11.xml");

		DumpReaderJobGenerator generator = createGenerator(
				file,
				new LinkedBlockingQueue<Job>(),
				new JobTraceSet());

		try
		{
			assertEquals(file.length(), generator.getFileSize());
		}
		finally
		{
			generator.after();
		}
	}

	// =========================================================================

	private static void assertJobs(
			List<RevisionJob> jobs,
			BigInteger namespace,
			String redirect)
	{
		assertEquals(3, jobs.size());

		{
			RevisionJob job = jobs.get(0);
			assertEquals(BigInteger.valueOf(1), job.getPageId());
			assertEquals(namespace, job.getPageNamespace());
			assertEquals("Example", job.getPageTitle());
			assertNull(job.getPageRedirect());
			assertEquals(BigInteger.valueOf(100), job.getId());
			assertFalse(job.isMinor());
			assertEquals(TIMESTAMP, job.getTimestamp().getTimeInMillis());
			assertEquals("Hello ''world''!", job.getTextText());
			assertFalse(job.isTextDeleted());
		}

		{
			RevisionJob job = jobs.get(1);
			assertEquals(BigInteger.valueOf(1), job.getPageId());
			assertEquals(BigInteger.valueOf(101), job.getId());
			assertTrue(job.isMinor());
			assertEquals(TIMESTAMP + 60 * 1000, job.getTimestamp().getTimeInMillis());
			assertEquals("Hello '''world'''!", job.getTextText());
		}

		{
			RevisionJob job = jobs.get(2);
			assertEquals(BigInteger.valueOf(2), job.getPageId());
			assertEquals("Old name", job.getPageTitle());
			assertEquals(redirect, job.getPageRedirect());
			assertEquals(BigInteger.valueOf(200), job.getId());
			assertEquals("#REDIRECT [[Example]]", job.getTextText());
		}
	}

	private static List<RevisionJob> generateJobs(String resource) throws Throwable
	{
		LinkedBlockingQueue<Job> inTray = new LinkedBlockingQueue<Job>();
		JobTraceSet jobTraces = new JobTraceSet();

		DumpReaderJobGenerator generator = createGenerator(
				getFile(resource),
				inTray,
				jobTraces);

		try
		{
			generator.work();
		}
		finally
		{
			generator.after();
		}

		List<RevisionJob> jobs = new ArrayList<RevisionJob>();
		for (Job job : inTray)
			jobs.add((RevisionJob) job);

		assertEquals(jobs.size(), jobTraces.getTraces().size());

		return jobs;
	}

	private static File getFile(String resource)
	{
		URL url = DumpReaderJobGeneratorTest.class.getResource(resource);
		return new File(StringTools.decodeUsingDefaultCharset(url.getFile()));
	}

	private static DumpReaderJobGenerator createGenerator(
			File file,
			LinkedBlockingQueue<Job> inTray,
			JobTraceSet jobTraces)
	{
		// Without a dump cruncher no GUI is updated
		return new DumpReaderJobGenerator(
				null,
				file,
				Charset.forName("UTF8"),
				new AbortHandler()
				{
					@Override
					public void notify(Throwable t)
					{
						throw new AssertionError(t);
					}
				},
				inTray,
				jobTraces);
	}
}
