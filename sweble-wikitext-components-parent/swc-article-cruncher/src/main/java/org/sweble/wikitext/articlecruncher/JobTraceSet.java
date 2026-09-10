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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class JobTraceSet
{
	private Set<JobTrace> traces = new HashSet<JobTrace>();

	private boolean aborted = false;

	// =========================================================================

	public synchronized void add(JobTrace trace)
	{
		traces.add(trace);
	}

	public synchronized boolean remove(JobTrace trace)
	{
		boolean removed = traces.remove(trace);
		notifyAll();
		return removed;
	}

	public synchronized Set<JobTrace> getTraces()
	{
		return Collections.unmodifiableSet(traces);
	}

	/**
	 * Waits until all traces have been removed or the timeout has elapsed.
	 */
	public synchronized void waitForCompletion(int timeoutInSeconds) throws InterruptedException
	{
		awaitCompletion(timeoutInSeconds, TimeUnit.SECONDS);
	}

	// =========================================================================

	/**
	 * Waits until all traces have been removed, the timeout has elapsed or
	 * waiting was aborted.
	 *
	 * @return Whether all traces have been removed.
	 */
	synchronized boolean awaitCompletion(long timeout, TimeUnit unit) throws InterruptedException
	{
		long deadline = System.nanoTime() + unit.toNanos(timeout);
		while (!traces.isEmpty() && !aborted)
		{
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0)
				break;
			TimeUnit.NANOSECONDS.timedWait(this, remaining);
		}
		return traces.isEmpty();
	}

	/**
	 * Makes current and future calls to awaitCompletion() return immediately.
	 */
	synchronized void abortWaiting()
	{
		aborted = true;
		notifyAll();
	}
}
