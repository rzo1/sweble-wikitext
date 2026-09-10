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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

import org.junit.Before;
import org.junit.Test;
import org.sweble.wikitext.articlecruncher.Job;
import org.sweble.wikitext.articlecruncher.JobGeneratorFactory;
import org.sweble.wikitext.articlecruncher.JobTrace;
import org.sweble.wikitext.articlecruncher.JobTraceSet;
import org.sweble.wikitext.articlecruncher.Nexus;
import org.sweble.wikitext.articlecruncher.ProcessingNodeFactory;
import org.sweble.wikitext.articlecruncher.Processor;
import org.sweble.wikitext.articlecruncher.StorerFactory;
import org.sweble.wikitext.articlecruncher.pnodes.LocalProcessingNode;
import org.sweble.wikitext.articlecruncher.pnodes.LpnJobProcessorFactory;
import org.sweble.wikitext.articlecruncher.utils.AbortHandler;
import org.sweble.wikitext.articlecruncher.utils.WorkerBase;

public class CruncherFailureTest
		extends
			CruncherTestBase
{
	private static final int NUM_JOBS = 1000;

	private static final int NUM_WORKERS = 4;

	private Nexus nexus;

	private final CountDownLatch generationFinished = new CountDownLatch(1);

	// =========================================================================

	@Before
	public void before() throws Throwable
	{
		nexus = new Nexus();

		nexus.setUp(
				16, /* in tray capacity */
				16, /* processed jobs capacity */
				16 /* out tray capacity */);
	}

	// =========================================================================

	@Test(timeout = 30000)
	public void testExceptionWhileCreatingJobGeneratorFailsStart() throws Throwable
	{
		final RuntimeException cause = new RuntimeException("Dump file does not exist");

		nexus.addJobGenerator(new JobGeneratorFactory()
		{
			@Override
			public WorkerBase create(
					AbortHandler abortHandler,
					BlockingQueue<Job> inTray,
					JobTraceSet jobTraces)
			{
				throw cause;
			}
		});
		nexus.addProcessingNode(createPassThroughNodeFactory());
		nexus.addStorer(createStorerFactory());

		assertSame(cause, startAndGetCause());
	}

	@Test(timeout = 30000)
	public void testExceptionWhileCreatingStorerFailsStart() throws Throwable
	{
		final RuntimeException cause = new RuntimeException("Cannot open output");

		nexus.addJobGenerator(createJobFactory(NUM_JOBS));
		nexus.addProcessingNode(createPassThroughNodeFactory());
		nexus.addStorer(new StorerFactory()
		{
			@Override
			public WorkerBase create(
					AbortHandler abortHandler,
					JobTraceSet jobTraces,
					BlockingQueue<Job> outTray)
			{
				throw cause;
			}
		});

		assertSame(cause, startAndGetCause());
	}

	@Test(timeout = 30000)
	public void testExceptionWhileCreatingLpnDistributorFailsStart() throws Throwable
	{
		final RuntimeException cause = new RuntimeException("No name template");

		nexus.addJobGenerator(createJobFactory(NUM_JOBS));
		nexus.addProcessingNode(createLpnFactory(new LpnJobProcessorFactory()
		{
			@Override
			public Processor createProcessor()
			{
				return createPassThroughProcessor();
			}

			@Override
			public String getProcessorNameTemplate()
			{
				throw cause;
			}
		}));
		nexus.addStorer(createStorerFactory());

		assertSame(cause, startAndGetCause());
	}

	@Test(timeout = 30000)
	public void testErrorInProcessorFailsStart() throws Throwable
	{
		final AssertionError error = new AssertionError("Processor failed");

		nexus.addJobGenerator(createJobFactory(NUM_JOBS));
		nexus.addProcessingNode(createLpnFactory(createProcessorFactory(new Processor()
		{
			@Override
			public Object process(Job job)
			{
				throw error;
			}
		})));
		nexus.addStorer(createStorerFactory());

		assertSame(error, startAndGetCause());
	}

	@Test(timeout = 30000)
	public void testErrorInProcessorAfterJobGenerationFailsStart() throws Throwable
	{
		final AssertionError error = new AssertionError("Processor failed");

		nexus.addJobGenerator(createJobFactory(4));
		nexus.addProcessingNode(createLpnFactory(createProcessorFactory(new Processor()
		{
			@Override
			public Object process(Job job)
			{
				awaitEndOfJobGeneration();
				throw error;
			}
		})));
		nexus.addStorer(createStorerFactory());

		assertSame(error, startAndGetCause());
	}

	@Test(timeout = 30000)
	public void testEmergencyShutdownAfterJobGenerationFailsStart() throws Throwable
	{
		final RuntimeException cause = new RuntimeException("Emergency");

		nexus.addJobGenerator(createJobFactory(4));
		nexus.addProcessingNode(createPassThroughNodeFactory());
		// Never completes the jobs
		nexus.addStorer(new StorerFactory()
		{
			@Override
			public WorkerBase create(
					AbortHandler abortHandler,
					JobTraceSet jobTraces,
					final BlockingQueue<Job> outTray)
			{
				return new WorkerBase("Storer", abortHandler)
				{
					@Override
					protected void work() throws InterruptedException
					{
						while (true)
							outTray.take();
					}
				};
			}
		});

		Thread shutdown = new Thread()
		{
			@Override
			public void run()
			{
				awaitEndOfJobGeneration();
				nexus.emergencyShutdown(cause);
			}
		};
		shutdown.setDaemon(true);
		shutdown.start();

		assertSame(cause, startAndGetCause());
	}

	@Test(timeout = 30000)
	public void testAllJobGeneratorsAreWaitedFor() throws Throwable
	{
		final int numJobs = 100;

		nexus.addJobGenerator(createJobFactory(numJobs));
		nexus.addJobGenerator(new JobGeneratorFactory()
		{
			@Override
			public WorkerBase create(
					AbortHandler abortHandler,
					BlockingQueue<Job> inTray,
					JobTraceSet jobTraces)
			{
				// Takes a while to open its input
				try
				{
					Thread.sleep(500);
				}
				catch (InterruptedException e)
				{
					throw new RuntimeException(e);
				}
				return createJobFactory(numJobs).create(abortHandler, inTray, jobTraces);
			}
		});
		nexus.addProcessingNode(createPassThroughNodeFactory());
		nexus.addStorer(createStorerFactory());

		nexus.start();

		assertEquals(2 * numJobs, generated.get());
		assertEquals(2 * numJobs, stored.get());
		assertTrue(nexus.getJobTraces().isEmpty());
	}

	// =========================================================================

	private Throwable startAndGetCause()
	{
		try
		{
			nexus.start();
		}
		catch (Throwable t)
		{
			return t;
		}

		fail("Nexus.start() did not fail");
		return null;
	}

	private void awaitEndOfJobGeneration()
	{
		try
		{
			generationFinished.await();
			// Give the Nexus time to notice that job generation has finished
			Thread.sleep(200);
		}
		catch (InterruptedException e)
		{
			throw new RuntimeException(e);
		}
	}

	private JobGeneratorFactory createJobFactory(final int numJobs)
	{
		return new JobGeneratorFactory()
		{
			@Override
			public WorkerBase create(
					final AbortHandler abortHandler,
					final BlockingQueue<Job> inTray,
					final JobTraceSet jobTraces)
			{
				return new WorkerBase("JobGenerator", abortHandler)
				{
					@Override
					protected void work() throws InterruptedException
					{
						for (int i = 0; i < numJobs; ++i)
						{
							Job job = new TestJob();
							generated.incrementAndGet();

							JobTrace trace = job.getTrace();
							trace.signOff(getClass(), null);

							jobTraces.add(trace);

							inTray.put(job);
						}

						generationFinished.countDown();
					}
				};
			}
		};
	}

	private ProcessingNodeFactory createPassThroughNodeFactory()
	{
		return new ProcessingNodeFactory()
		{
			@Override
			public WorkerBase create(
					final AbortHandler abortHandler,
					final BlockingQueue<Job> inTray,
					final BlockingQueue<Job> processedJobs)
			{
				return new WorkerBase("ProcessingNode", abortHandler)
				{
					@Override
					protected void work() throws InterruptedException
					{
						while (true)
						{
							Job job = inTray.take();

							job.signOff(getClass(), null);

							job.processed((Object) null);

							processedJobs.put(job);
						}
					}
				};
			}
		};
	}

	private ProcessingNodeFactory createLpnFactory(
			final LpnJobProcessorFactory jobProcessorFactory)
	{
		return new ProcessingNodeFactory()
		{
			@Override
			public WorkerBase create(
					AbortHandler abortHandler,
					BlockingQueue<Job> inTray,
					BlockingQueue<Job> processedJobs)
			{
				return new LocalProcessingNode(
						abortHandler,
						inTray,
						processedJobs,
						jobProcessorFactory,
						NUM_WORKERS);
			}
		};
	}

	private LpnJobProcessorFactory createProcessorFactory(
			final Processor processor)
	{
		return new LpnJobProcessorFactory()
		{
			@Override
			public Processor createProcessor()
			{
				return processor;
			}

			@Override
			public String getProcessorNameTemplate()
			{
				return "Processor-%02d";
			}
		};
	}

	private Processor createPassThroughProcessor()
	{
		return new Processor()
		{
			@Override
			public Object process(Job job)
			{
				job.signOff(getClass(), null);
				return null;
			}
		};
	}
}
