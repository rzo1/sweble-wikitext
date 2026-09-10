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

package org.sweble.wikitext.articlecruncher.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;
import org.sweble.wikitext.articlecruncher.WorkerInstantiator;

public class WorkerLauncherTest
{
	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final AtomicReference<Throwable> reported = new AtomicReference<Throwable>();

	private final AbortHandler abortHandler = new AbortHandler()
	{
		@Override
		public void notify(Throwable t)
		{
			reported.compareAndSet(null, t);
		}
	};

	// =========================================================================

	@After
	public void after()
	{
		executor.shutdownNow();
	}

	// =========================================================================

	@Test(timeout = 10000)
	public void testExceptionWhileInstantiatingWorkerIsReported() throws Exception
	{
		final RuntimeException cause = new RuntimeException("Cannot create worker");

		WorkerSynchronizer synchronizer = new WorkerSynchronizer();

		WorkerLauncher wl = new WorkerLauncher(new WorkerInstantiator()
		{
			@Override
			public WorkerBase instantiate()
			{
				throw cause;
			}
		}, abortHandler);

		wl.start(executor, synchronizer);

		synchronizer.waitForAny();

		assertTrue(wl.await(5, TimeUnit.SECONDS));
		assertSame(cause, reported.get());
	}

	@Test(timeout = 10000)
	public void testWorkerThatFailedToStartCountsForWaitForAll() throws Exception
	{
		WorkerSynchronizer synchronizer = new WorkerSynchronizer();

		WorkerLauncher failing = new WorkerLauncher(new WorkerInstantiator()
		{
			@Override
			public WorkerBase instantiate()
			{
				throw new RuntimeException("Cannot create worker");
			}
		}, abortHandler);

		WorkerLauncher working = new WorkerLauncher(
				createWorker(new CountDownLatch(0)),
				abortHandler);

		failing.start(executor, synchronizer);
		working.start(executor, synchronizer);

		synchronizer.waitForAll(2);

		assertTrue(synchronizer.isSynchronized());
	}

	@Test(timeout = 10000)
	public void testStateIsNotLockedViaEnumConstant() throws Exception
	{
		final CountDownLatch release = new CountDownLatch(1);

		WorkerLauncher wl = new WorkerLauncher(
				createWorker(release),
				abortHandler);

		wl.start(executor);

		// Unrelated code that happens to lock the enum constant
		final CountDownLatch locked = new CountDownLatch(1);
		Thread locker = new Thread()
		{
			@Override
			public void run()
			{
				synchronized (WorkerLauncher.WorkerState.RUNNING)
				{
					locked.countDown();
					try
					{
						release.await();
					}
					catch (InterruptedException e)
					{
						// Stop holding the lock
					}
				}
			}
		};
		locker.setDaemon(true);
		locker.start();
		locked.await();

		try
		{
			wl.stop();
			assertTrue(wl.await(5, TimeUnit.SECONDS));
		}
		finally
		{
			release.countDown();
		}
	}

	@Test(timeout = 10000)
	public void testStopWhileAwaiting() throws Exception
	{
		final WorkerLauncher wl = new WorkerLauncher(
				createWorker(new CountDownLatch(1)),
				abortHandler);

		wl.start(executor);

		final CountDownLatch joined = new CountDownLatch(1);
		Thread waiter = new Thread()
		{
			@Override
			public void run()
			{
				try
				{
					if (wl.await(5, TimeUnit.SECONDS))
						joined.countDown();
				}
				catch (Exception e)
				{
					// Not joined
				}
			}
		};
		waiter.setDaemon(true);
		waiter.start();

		// Give the waiter time to block in await()
		Thread.sleep(100);

		wl.stop();

		assertTrue(joined.await(5, TimeUnit.SECONDS));
		assertSame(null, reported.get());
	}

	@Test(timeout = 10000)
	public void testAwaitTimesOut() throws Exception
	{
		CountDownLatch release = new CountDownLatch(1);

		WorkerLauncher wl = new WorkerLauncher(
				createWorker(release),
				abortHandler);

		wl.start(executor);

		assertFalse(wl.await(100, TimeUnit.MILLISECONDS));

		release.countDown();

		assertTrue(wl.await(5, TimeUnit.SECONDS));
	}

	// =========================================================================

	private WorkerInstantiator createWorker(final CountDownLatch release)
	{
		return new WorkerInstantiator()
		{
			@Override
			public WorkerBase instantiate()
			{
				return new WorkerBase("TestWorker")
				{
					@Override
					protected void work() throws InterruptedException
					{
						release.await();
					}
				};
			}
		};
	}
}
