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

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sweble.wikitext.articlecruncher.WorkerInstantiator;

import de.fau.cs.osr.utils.WrappedException;

public class WorkerLauncher
{
	public enum WorkerState
	{
		INITIALIZED,
		RUNNING,
		POISONED,
		STOPPED,
	}

	private static final Logger logger = LoggerFactory.getLogger(WorkerLauncher.class.getName());

	/**
	 * Guards the state of the launcher. Also makes sure that this.future is
	 * set before the worker can kick-off.
	 */
	private final Object lock = new Object();

	private final WorkerInstantiator workerInstantiator;

	private final AbortHandler abortHandler;

	private volatile String workerName;

	private volatile Future<?> future;

	private volatile WorkerState state;

	private volatile WorkerSynchronizer synchronizer;

	// =========================================================================

	public WorkerLauncher(
			WorkerInstantiator workerInstantiator,
			AbortHandler abortHandler)
	{
		this.workerInstantiator = workerInstantiator;
		this.abortHandler = abortHandler;

		//this.logger = Logger.getLogger(workerName);
		this.state = WorkerState.INITIALIZED;
	}

	// =========================================================================

	public final void start(ExecutorService executor)
	{
		start(executor, null);
	}

	public final void start(
			ExecutorService executor,
			WorkerSynchronizer synchronizer)
	{
		synchronized (lock)
		{
			if (state != WorkerState.INITIALIZED)
				throw new IllegalStateException("start() can be called only once");

			this.state = WorkerState.RUNNING;
			this.synchronizer = synchronizer;

			this.future = executor.submit(new WorkerRunnable());
		}
	}

	public final void stop()
	{
		synchronized (lock)
		{
			switch (state)
			{
				case RUNNING:
					logger.info("Sending stop signal to worker " + workerName);
					state = WorkerState.POISONED;
					future.cancel(true);
					break;
				/*
				case POISONED:
					logger.warn("Already sent stop signal to worker " + workerName);
					break;

				case STOPPED:
					logger.warn("Worker " + workerName + " already terminated");
					break;

				case INITIALIZED:
					throw new IllegalStateException("stop() can only be called after start()");
				*/
				default:
					break;

			}
		}
	}

	public final void await() throws InterruptedException, ExecutionException
	{
		await(Long.MAX_VALUE, null);
	}

	public final boolean await(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException
	{
		Future<?> f;
		synchronized (lock)
		{
			if (state == WorkerState.INITIALIZED)
				throw new IllegalStateException("await() can only be called after start()");

			f = future;
		}

		// Don't wait inside lock, the worker could not be stopped otherwise!
		if (f != null)
		{
			try
			{
				if (timeout == Long.MAX_VALUE && unit == null)
				{
					f.get();
				}
				else
				{
					f.get(timeout, unit);
				}
			}
			catch (CancellationException e)
			{
				// stopped
			}
			catch (TimeoutException e)
			{
				// timed out
				return false;
			}
		}
		else
		{
			logger.warn("Worker " + workerName + " already terminated");
		}

		// joined with future
		return true;
	}

	// =========================================================================

	private final class WorkerRunnable
			implements
				Runnable
	{
		@Override
		public void run()
		{
			synchronized (lock)
			{
			}

			WorkerBase worker = null;

			boolean registered = false;

			try
			{
				worker = workerInstantiator.instantiate();
				workerName = worker.getWorkerName();
				worker.setLauncher(WorkerLauncher.this);

				if (synchronizer != null)
				{
					// oneStarted() counts the worker before it can throw
					registered = true;
					synchronizer.oneStarted();
				}

				logger.info(workerName + " starting");

				try
				{
					worker.work();
				}
				catch (WrappedException e)
				{
					throw e.getCause();
				}
			}
			catch (InterruptedException e)
			{
				// Don't call abort inside lock!
				if (state != WorkerState.POISONED)
				{
					logger.error(workerName + " interrupted unexpectedly", e);
					abortHandler.notify(e);
				}
			}
			catch (Throwable t)
			{
				if (worker == null)
				{
					logger.error("Creating worker failed", t);
				}
				else
				{
					logger.error(workerName + " terminated by exception", t);
				}
				abortHandler.notify(t);
			}
			finally
			{
				if (worker != null)
				{
					try
					{
						worker.after();
					}
					catch (Throwable t)
					{
						logger.error(workerName + ".after() threw exception", t);
					}
				}

				synchronized (lock)
				{
					state = WorkerState.STOPPED;
				}

				if (worker != null)
					logger.info(workerName + " stopped");

				if (synchronizer != null)
				{
					if (registered)
					{
						synchronizer.oneStopped();
					}
					else
					{
						synchronizer.oneFailedToStart();
					}
				}
			}
		}
	}
}
