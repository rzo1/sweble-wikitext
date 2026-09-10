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

package org.sweble.wikitext.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sweble.wikitext.engine.EngineCoverageTestUtils.assertPasses;
import static org.sweble.wikitext.engine.EngineCoverageTestUtils.findAll;
import static org.sweble.wikitext.engine.EngineCoverageTestUtils.single;
import static org.sweble.wikitext.engine.EngineCoverageTestUtils.textOf;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.nodes.EngLogExpansionPass;
import org.sweble.wikitext.engine.nodes.EngLogParserPass;
import org.sweble.wikitext.engine.nodes.EngLogPostprocessorPass;
import org.sweble.wikitext.engine.nodes.EngLogPreprocessorPass;
import org.sweble.wikitext.engine.nodes.EngLogProcessingPass;
import org.sweble.wikitext.engine.nodes.EngLogTransclusionResolution;
import org.sweble.wikitext.engine.nodes.EngLogUnhandledError;
import org.sweble.wikitext.engine.nodes.EngLogValidatorPass;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.WikitextPreprocessor;
import org.sweble.wikitext.parser.nodes.WtBold;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtPreproWikitextPage;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.nodes.WtTemplateArgument;
import org.sweble.wikitext.parser.nodes.WtTicks;

/**
 * Checks the result of each public stage of the {@link WtEngineImpl} and how
 * failures are reported.
 */
public class WtEngineImplCoverageTest
{
	private static final String TITLE = "Stage test";

	private static final Long REVISION = Long.valueOf(42);

	private static final String TEMPLATE_TEXT = "from the template";

	// =========================================================================

	private WikiConfigImpl config;

	private WtEngineImpl engine;

	private PageId pageId;

	// =========================================================================

	@Before
	public void setUp() throws Exception
	{
		config = DefaultConfigEnWp.generate();
		engine = new WtEngineImpl(config);
		pageId = new PageId(PageTitle.make(config, TITLE), REVISION);
	}

	// ==[ Stages ]=============================================================

	@Test
	public void testPreprocessLeavesTemplatesUnexpanded() throws Exception
	{
		EngProcessedPage result = engine.preprocess(pageId, "'''bold''' {{Foo}}", false, null);

		assertEquals(1, findAll(result.getPage(), WtTemplate.class).size());
		assertTrue(findAll(result.getPage(), WtTicks.class).isEmpty());
		assertTrue(textOf(result.getPage()).contains("'''bold'''"));
	}

	@Test
	public void testPreprocessForInclusionHonorsIncludeOnlyAndNoInclude() throws Exception
	{
		String wikitext = "<noinclude>viewed</noinclude><includeonly>included</includeonly>";

		EngProcessedPage forViewing = engine.preprocess(pageId, wikitext, false, null);
		assertTrue(textOf(forViewing.getPage()).contains("viewed"));
		assertFalse(textOf(forViewing.getPage()).contains("included"));

		EngProcessedPage forInclusion = engine.preprocess(pageId, wikitext, true, null);
		assertTrue(textOf(forInclusion.getPage()).contains("included"));
		assertFalse(textOf(forInclusion.getPage()).contains("viewed"));
	}

	@Test
	public void testPreprocessWithCallbackExpandsTemplates() throws Exception
	{
		TemplateCallback callback = new TemplateCallback();
		EngProcessedPage result = engine.preprocess(pageId, "{{Foo}}", false, callback);

		assertTrue(findAll(result.getPage(), WtTemplate.class).isEmpty());
		assertTrue(textOf(result.getPage()).contains(TEMPLATE_TEXT));
		assertEquals(1, callback.requests);
	}

	@Test
	public void testExpandTranscludesTemplates() throws Exception
	{
		RecordingHooks hooks = new RecordingHooks();
		engine.setDebugHooks(hooks);
		assertSame(hooks, engine.getDebugHooks());

		EngProcessedPage result = engine.expand(pageId, "a {{Foo}} b", new TemplateCallback());

		assertTrue(findAll(result.getPage(), WtTemplate.class).isEmpty());
		assertTrue(textOf(result.getPage()).contains(TEMPLATE_TEXT));
		assertNotNull(result.getEntityMap());

		assertEquals(1, hooks.transclusions.size());
		assertTrue(hooks.transclusions.get(0).getSuccess());
	}

	@Test
	public void testExpandForInclusion() throws Exception
	{
		String wikitext = "<noinclude>viewed</noinclude><includeonly>{{Foo}}</includeonly>";

		EngProcessedPage result = engine.expand(pageId, wikitext, true, new TemplateCallback());

		assertTrue(textOf(result.getPage()).contains(TEMPLATE_TEXT));
		assertFalse(textOf(result.getPage()).contains("viewed"));
	}

	@Test
	public void testParseDoesNotPostprocess() throws Exception
	{
		EngProcessedPage result = engine.parse(pageId, "'''bold''' text", null);

		assertFalse(findAll(result.getPage(), WtTicks.class).isEmpty());
		assertTrue(findAll(result.getPage(), WtBold.class).isEmpty());
	}

	@Test
	public void testParseWithCallbackExpandsBeforeParsing() throws Exception
	{
		TemplateCallback callback = new TemplateCallback();
		EngProcessedPage result = engine.parse(pageId, "{{Foo}}", callback);

		assertTrue(findAll(result.getPage(), WtTemplate.class).isEmpty());
		assertTrue(textOf(result.getPage()).contains(TEMPLATE_TEXT));
		assertEquals(1, callback.requests);
	}

	@Test
	public void testParseAndPostprocessSkipsPreprocessingAndExpansion() throws Exception
	{
		TemplateCallback callback = new TemplateCallback();
		EngProcessedPage result = engine.parseAndPostprocess(pageId, "'''bold''' {{Foo}}", callback);

		assertFalse(findAll(result.getPage(), WtBold.class).isEmpty());
		assertTrue(findAll(result.getPage(), WtTicks.class).isEmpty());
		assertFalse(textOf(result.getPage()).contains(TEMPLATE_TEXT));
		assertEquals(0, callback.requests);
	}

	@Test
	public void testPostprocessRunsAllStages() throws Exception
	{
		TemplateCallback callback = new TemplateCallback();
		EngProcessedPage result = engine.postprocess(pageId, "'''bold''' {{Foo}}", callback);

		assertFalse(findAll(result.getPage(), WtBold.class).isEmpty());
		assertTrue(findAll(result.getPage(), WtTicks.class).isEmpty());
		assertTrue(findAll(result.getPage(), WtTemplate.class).isEmpty());
		assertTrue(textOf(result.getPage()).contains(TEMPLATE_TEXT));
		assertEquals(1, callback.requests);
	}

	@Test
	public void testPostprocessWithoutCallbackKeepsTemplates() throws Exception
	{
		EngProcessedPage result = engine.postprocess(pageId, "'''bold''' {{Foo}}", null);

		assertFalse(findAll(result.getPage(), WtBold.class).isEmpty());
		assertEquals(1, findAll(result.getPage(), WtTemplate.class).size());
	}

	@Test
	public void testPostprocessPpOrExpAst() throws Exception
	{
		EngProcessedPage result = engine.postprocessPpOrExpAst(pageId, preprocess("'''bold''' text"));

		assertFalse(findAll(result.getPage(), WtBold.class).isEmpty());
		assertTrue(findAll(result.getPage(), WtTicks.class).isEmpty());
	}

	@Test
	public void testResolutionTimingIsOnlyRecordedWhenEnabled() throws Exception
	{
		RecordingHooks hooks = new RecordingHooks();
		engine.setDebugHooks(hooks);

		engine.expand(pageId, "{{Foo}}", new TemplateCallback());
		assertNull(hooks.transclusions.get(0).getTimeNeeded());

		engine.setTimingEnabled(true);
		assertTrue(engine.isTimingEnabled());

		engine.expand(pageId, "{{Foo}}", new TemplateCallback());
		assertNotNull(hooks.transclusions.get(1).getTimeNeeded());
	}

	@Test
	public void testDefaultSettings() throws Exception
	{
		assertSame(config, engine.getWikiConfig());
		assertSame(config.getNodeFactory(), engine.nf());
		assertNull(engine.getDebugHooks());
		assertFalse(engine.isNoRedirect());
		assertFalse(engine.isTimingEnabled());
		assertTrue(engine.isCatchAll());
	}

	// ==[ Logs ]===============================================================

	@Test
	public void testEachStageReturnsItsLog() throws Exception
	{
		TemplateCallback callback = new TemplateCallback();

		assertProcessingLog(engine.preprocess(pageId, "{{Foo}}", false, null).getLog(),
				EngLogValidatorPass.class,
				EngLogPreprocessorPass.class);

		assertProcessingLog(engine.preprocess(pageId, "{{Foo}}", false, callback).getLog(),
				EngLogValidatorPass.class,
				EngLogPreprocessorPass.class,
				EngLogExpansionPass.class);

		assertProcessingLog(engine.expand(pageId, "{{Foo}}", callback).getLog(),
				EngLogValidatorPass.class,
				EngLogPreprocessorPass.class,
				EngLogExpansionPass.class);

		assertProcessingLog(engine.parse(pageId, "{{Foo}}", null).getLog(),
				EngLogValidatorPass.class,
				EngLogPreprocessorPass.class,
				EngLogParserPass.class);

		assertProcessingLog(engine.parseAndPostprocess(pageId, "{{Foo}}", callback).getLog(),
				EngLogParserPass.class,
				EngLogPostprocessorPass.class);

		assertProcessingLog(engine.postprocess(pageId, "{{Foo}}", callback).getLog(),
				EngLogValidatorPass.class,
				EngLogPreprocessorPass.class,
				EngLogExpansionPass.class,
				EngLogParserPass.class,
				EngLogPostprocessorPass.class);

		assertProcessingLog(engine.postprocessPpOrExpAst(pageId, preprocess("text")).getLog(),
				EngLogParserPass.class,
				EngLogPostprocessorPass.class);
	}

	@Test
	public void testFailingPassLogsUnhandledError() throws Exception
	{
		try
		{
			engine.postprocess(pageId, null, null);
			fail("Expected EngineException");
		}
		catch (EngineException e)
		{
			assertProcessingLog(e.getLog(), EngLogValidatorPass.class);
			single(e.getLog().get(0), EngLogUnhandledError.class);
		}
	}

	// ==[ Illegal arguments ]==================================================

	@Test
	public void testStagesRejectMissingPageId() throws Exception
	{
		final WtPreproWikitextPage ppAst = preprocess("text");

		assertNpe(new Stage()
		{
			@Override
			public void run() throws Exception
			{
				engine.preprocess(null, "text", false, null);
			}
		});
		assertNpe(new Stage()
		{
			@Override
			public void run() throws Exception
			{
				engine.expand(null, "text", new TemplateCallback());
			}
		});
		assertNpe(new Stage()
		{
			@Override
			public void run() throws Exception
			{
				engine.expand(pageId, "text", null);
			}
		});
		assertNpe(new Stage()
		{
			@Override
			public void run() throws Exception
			{
				engine.parse(null, "text", null);
			}
		});
		assertNpe(new Stage()
		{
			@Override
			public void run() throws Exception
			{
				engine.parseAndPostprocess(null, "text", null);
			}
		});
		assertNpe(new Stage()
		{
			@Override
			public void run() throws Exception
			{
				engine.postprocess(null, "text", null);
			}
		});
		assertNpe(new Stage()
		{
			@Override
			public void run() throws Exception
			{
				engine.postprocessPpOrExpAst(null, ppAst);
			}
		});
	}

	// ==[ Failures ]===========================================================

	@Test
	public void testValidationFailureIsWrappedWithLog() throws Exception
	{
		try
		{
			engine.postprocess(pageId, null, null);
			fail("Expected EngineException");
		}
		catch (EngineException e)
		{
			assertFailure(e, "Validation failed!");
		}
	}

	@Test
	public void testParserFailureIsWrappedWithLog() throws Exception
	{
		try
		{
			engine.parseAndPostprocess(pageId, null, null);
			fail("Expected EngineException");
		}
		catch (EngineException e)
		{
			assertFailure(e, "Parsing failed!");
		}
	}

	@Test
	public void testParserFailureOnMissingPreprocessedAstIsWrappedWithLog() throws Exception
	{
		try
		{
			engine.postprocessPpOrExpAst(pageId, null);
			fail("Expected EngineException");
		}
		catch (EngineException e)
		{
			assertFailure(e, "Parsing failed!");
		}
	}

	@Test
	public void testExpansionFailureIsWrappedWithLogWhenNotCatchingAll() throws Exception
	{
		IllegalStateException failure = new IllegalStateException("callback failed");

		engine.setCatchAll(false);
		assertFalse(engine.isCatchAll());
		try
		{
			engine.expand(pageId, "{{Foo}}", new FailingCallback(failure));
			fail("Expected EngineException");
		}
		catch (EngineException e)
		{
			assertFailure(e, "Resolution failed!");

			// The ExpansionException wrapper is removed
			assertSame(failure, e.getCause());
		}
	}

	@Test
	public void testUnexpectedErrorIsWrappedWithLog() throws Exception
	{
		AssertionError failure = new AssertionError("unexpected");

		try
		{
			engine.postprocess(pageId, "{{Foo}}", new FailingCallback(failure));
			fail("Expected EngineException");
		}
		catch (EngineException e)
		{
			assertFailure(e, "Compilation failed!");
			assertSame(failure, e.getCause());
		}
	}

	@Test(expected = IllegalStateException.class)
	public void testLogCanOnlyBeAttachedOnce() throws Exception
	{
		EngineException e = new EngineException(
				pageId.getTitle(),
				"Failed",
				null,
				config.getNodeFactory().logProcessingPass());

		e.attachLog(config.getNodeFactory().logProcessingPass());
	}

	// =========================================================================

	private WtPreproWikitextPage preprocess(String wikitext) throws Exception
	{
		return (WtPreproWikitextPage)
				new WikitextPreprocessor(config.getParserConfig()).parseArticle(wikitext, TITLE);
	}

	private void assertFailure(EngineException e, String message)
	{
		assertTrue(e.getMessage(), e.getMessage().startsWith(message));
		assertTrue(e.getMessage(), e.getMessage().contains(TITLE));
		assertSame(pageId.getTitle(), e.getPageTitle());
		assertNotNull(e.getCause());

		EngLogProcessingPass log = e.getLog();
		assertNotNull("Log not attached", log);
		assertEquals(TITLE, log.getTitle());
		assertEquals(REVISION, log.getRevision());
	}

	private static void assertProcessingLog(EngLogProcessingPass log, Class<?>... passes)
	{
		assertNotNull("Log missing", log);
		assertEquals(TITLE, log.getTitle());
		assertEquals(REVISION, log.getRevision());
		assertPasses(log, passes);
	}

	private static void assertNpe(Stage stage) throws Exception
	{
		try
		{
			stage.run();
			fail("Expected NullPointerException");
		}
		catch (NullPointerException e)
		{
			// Expected
		}
	}

	// =========================================================================

	private interface Stage
	{
		void run() throws Exception;
	}

	private static final class TemplateCallback
			implements
				ExpansionCallback
	{
		int requests = 0;

		@Override
		public FullPage retrieveWikitext(ExpansionFrame expansionFrame, PageTitle pageTitle)
		{
			++requests;
			return new FullPage(new PageId(pageTitle, 1), TEMPLATE_TEXT);
		}

		@Override
		public String fileUrl(PageTitle pageTitle, int width, int height)
		{
			return null;
		}
	}

	static final class FailingCallback
			implements
				ExpansionCallback
	{
		private final Throwable failure;

		FailingCallback(Throwable failure)
		{
			this.failure = failure;
		}

		@Override
		public FullPage retrieveWikitext(ExpansionFrame expansionFrame, PageTitle pageTitle)
		{
			if (failure instanceof Error)
				throw (Error) failure;
			throw (RuntimeException) failure;
		}

		@Override
		public String fileUrl(PageTitle pageTitle, int width, int height)
		{
			return null;
		}
	}

	private static final class RecordingHooks
			extends
				ExpansionDebugHooks
	{
		final List<EngLogTransclusionResolution> transclusions =
				new ArrayList<EngLogTransclusionResolution>();

		@Override
		public WtNode afterResolveTransclusion(
				ExpansionVisitor expansionVisitor,
				WtTemplate n,
				String target,
				List<WtTemplateArgument> args,
				WtNode result,
				EngLogTransclusionResolution log)
		{
			transclusions.add(log);
			return result;
		}
	}
}
