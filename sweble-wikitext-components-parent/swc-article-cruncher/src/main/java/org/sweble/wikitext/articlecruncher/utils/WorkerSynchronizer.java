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

public class WorkerSynchronizer
{
	private final Object lock = new Object();

	private final Object goLock = new Object();

	private int running = 0;

	/**
	 * The number of workers that started or failed to start.
	 */
	private int arrived = 0;

	private boolean oneStopped = false;

	private volatile boolean abort = false;

	private volatile boolean isSync = false;

	private boolean go = false;

	// =========================================================================

	public WorkerSynchronizer()
	{
	}

	// =========================================================================

	public void oneStarted() throws InterruptedException
	{
		synchronized (lock)
		{
			++running;
			++arrived;
			lock.notifyAll();
		}

		synchronized (goLock)
		{
			while (!go)
				goLock.wait();
		}
	}

	public void oneStopped()
	{
		synchronized (lock)
		{
			--running;
			oneStopped = true;
			lock.notifyAll();
		}
	}

	/**
	 * Called instead of oneStarted() and oneStopped() if a worker could not be
	 * created.
	 */
	public void oneFailedToStart()
	{
		synchronized (lock)
		{
			++arrived;
			oneStopped = true;
			lock.notifyAll();
		}
	}

	public void abort()
	{
		synchronized (lock)
		{
			abort = true;
			lock.notifyAll();
		}
	}

	public void waitForAll(int numWaitingFor) throws InterruptedException
	{
		synchronized (lock)
		{
			while (arrived < numWaitingFor && !abort)
				lock.wait();
		}

		synchronized (goLock)
		{
			go = true;
			goLock.notifyAll();
		}

		synchronized (lock)
		{
			while (running > 0 && !abort)
				lock.wait();

			isSync = true;
		}
	}

	public void waitForAny() throws InterruptedException
	{
		synchronized (goLock)
		{
			go = true;
			goLock.notifyAll();
		}

		synchronized (lock)
		{
			while (!oneStopped && !abort)
				lock.wait();

			isSync = true;
		}
	}

	public boolean isSynchronized()
	{
		return isSync;
	}

	public boolean isAborted()
	{
		return abort;
	}

	public Object getMonitor()
	{
		return lock;
	}
}
