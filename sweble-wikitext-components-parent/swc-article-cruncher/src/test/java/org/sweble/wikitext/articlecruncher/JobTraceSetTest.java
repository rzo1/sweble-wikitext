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

package org.sweble.wikitext.articlecruncher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class JobTraceSetTest
{
	@Test(timeout = 10000)
	public void testWaitForCompletionHonoursDeadline() throws Exception
	{
		JobTraceSet traces = new JobTraceSet();
		traces.add(new JobTrace());

		long start = System.nanoTime();
		traces.waitForCompletion(1);
		long elapsed = System.nanoTime() - start;

		assertTrue(elapsed >= TimeUnit.MILLISECONDS.toNanos(900));
		assertEquals(1, traces.getTraces().size());
	}

	@Test(timeout = 10000)
	public void testWaitForCompletionHonoursDeadlineWhileTracesChange() throws Exception
	{
		final JobTraceSet traces = new JobTraceSet();
		traces.add(new JobTrace());

		// Every removal wakes up the waiting thread
		Thread churn = new Thread()
		{
			@Override
			public void run()
			{
				try
				{
					while (true)
					{
						JobTrace trace = new JobTrace();
						traces.add(trace);
						Thread.sleep(50);
						traces.remove(trace);
					}
				}
				catch (InterruptedException e)
				{
					// Done
				}
			}
		};
		churn.setDaemon(true);
		churn.start();

		try
		{
			traces.waitForCompletion(1);
		}
		finally
		{
			churn.interrupt();
		}
	}

	@Test(timeout = 10000)
	public void testWaitForCompletionReturnsWhenAllTracesAreRemoved() throws Exception
	{
		final JobTraceSet traces = new JobTraceSet();
		final JobTrace trace = new JobTrace();
		traces.add(trace);

		Thread storer = new Thread()
		{
			@Override
			public void run()
			{
				try
				{
					Thread.sleep(100);
				}
				catch (InterruptedException e)
				{
					// Remove it anyway
				}
				traces.remove(trace);
			}
		};
		storer.setDaemon(true);
		storer.start();

		traces.waitForCompletion(60);

		assertTrue(traces.getTraces().isEmpty());
	}
}
