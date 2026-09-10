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

package org.sweble.wikitext.articlecruncher.pnodes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;
import org.sweble.wikitext.articlecruncher.Job;
import org.sweble.wikitext.articlecruncher.WorkerInstantiator;
import org.sweble.wikitext.articlecruncher.utils.AbortHandler;
import org.sweble.wikitext.articlecruncher.utils.WorkerBase;
import org.sweble.wikitext.articlecruncher.utils.WorkerLauncher;

public class LpnGathererTest
{
	private final ExecutorService executor = Executors.newCachedThreadPool();

	// =========================================================================

	@After
	public void after()
	{
		executor.shutdownNow();
	}

	// =========================================================================

	@Test(timeout = 10000)
	public void testExecutionFailureReleasesPermitAndIsEscalated() throws Exception
	{
		final AssertionError error = new AssertionError("Processor failed");

		final CompletionService<Job> ecs = new ExecutorCompletionService<Job>(executor);
		ecs.submit(new Callable<Job>()
		{
			@Override
			public Job call()
			{
				throw error;
			}
		});

		final Semaphore backPressure = new Semaphore(0);

		final AtomicReference<Throwable> reported = new AtomicReference<Throwable>();
		final AbortHandler abortHandler = new AbortHandler()
		{
			@Override
			public void notify(Throwable t)
			{
				reported.compareAndSet(null, t);
			}
		};

		WorkerLauncher wl = new WorkerLauncher(new WorkerInstantiator()
		{
			@Override
			public WorkerBase instantiate()
			{
				return new LpnGatherer(
						abortHandler,
						ecs,
						new LinkedBlockingQueue<Job>(),
						backPressure);
			}
		}, abortHandler);

		wl.start(executor);

		// The gatherer terminates instead of waiting for further jobs
		assertTrue(wl.await(5, TimeUnit.SECONDS));

		assertEquals(1, backPressure.availablePermits());
		assertSame(error, reported.get());
	}
}
