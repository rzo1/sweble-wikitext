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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.sweble.wikitext.engine.EngineCoverageTestUtils.findAll;
import static org.sweble.wikitext.engine.EngineCoverageTestUtils.single;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.sweble.wikitext.engine.WtEngineImplCoverageTest.FailingCallback;
import org.sweble.wikitext.engine.config.I18nAliasImpl;
import org.sweble.wikitext.engine.config.ParserFunctionGroup;
import org.sweble.wikitext.engine.config.TagExtensionGroup;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.nodes.EngLogMagicWordResolution;
import org.sweble.wikitext.engine.nodes.EngLogParameterResolution;
import org.sweble.wikitext.engine.nodes.EngLogParserFunctionResolution;
import org.sweble.wikitext.engine.nodes.EngLogRedirectResolution;
import org.sweble.wikitext.engine.nodes.EngLogResolution;
import org.sweble.wikitext.engine.nodes.EngLogTagExtensionResolution;
import org.sweble.wikitext.engine.nodes.EngLogTransclusionResolution;
import org.sweble.wikitext.engine.nodes.EngLogUnhandledError;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.WikitextPreprocessor;
import org.sweble.wikitext.parser.WtEntityMapImpl;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtNodeList;
import org.sweble.wikitext.parser.nodes.WtPageSwitch;
import org.sweble.wikitext.parser.nodes.WtPreproWikitextPage;
import org.sweble.wikitext.parser.nodes.WtRedirect;
import org.sweble.wikitext.parser.nodes.WtTagExtension;
import org.sweble.wikitext.parser.nodes.WtTagExtensionBody;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.nodes.WtTemplateArgument;
import org.sweble.wikitext.parser.nodes.WtTemplateParameter;

import de.fau.cs.osr.ptk.common.Warning;

/**
 * If resolving a redirect, a transclusion, a parser function, a parameter, a
 * tag extension or a page switch fails, the {@link ExpansionVisitor} logs the
 * failure as unhandled error and keeps the original markup. If the engine does
 * not catch all failures, expansion is aborted instead.
 *
 * The resolution logs are captured with {@link ExpansionDebugHooks} since the
 * log of the processed page is not accessible (see
 * {@link WtEngineImplCoverageTest#LOG_GETTER_BUG}).
 */
public class ExpansionVisitorErrorPathCoverageTest
{
	/**
	 * Name of the attribute that ExpansionVisitor attaches to nodes it failed
	 * to expand.
	 */
	private static final String SKIP_ATTR_NAME = "__SKIP__";

	private static final String TITLE = "Error paths";

	// =========================================================================

	private final IllegalStateException failure = new IllegalStateException("resolution failed");

	private final RecordingHooks hooks = new RecordingHooks();

	private WikiConfigImpl config;

	private WtEngineImpl engine;

	private PageId pageId;

	// =========================================================================

	@Before
	public void setUp() throws Exception
	{
		config = DefaultConfigEnWp.generate();

		config.addI18nAlias(new I18nAliasImpl("throwingpfn", false, Arrays.asList("#throwingpfn:")));
		config.addI18nAlias(new I18nAliasImpl("nullpfn", false, Arrays.asList("#nullpfn:")));
		config.addI18nAlias(new I18nAliasImpl("throwingswitch", false, Arrays.asList("__THROWINGSWITCH__")));

		ParserFunctionGroup pfns = new ParserFunctionGroup("Failing parser functions");
		pfns.addParserFunction(new ThrowingParserFunction(config, "throwingpfn", false, failure));
		pfns.addParserFunction(new ThrowingParserFunction(config, "throwingswitch", true, failure));
		pfns.addParserFunction(new NullParserFunction(config));
		config.addParserFunctionGroup(pfns);

		TagExtensionGroup tagExts = new TagExtensionGroup("Failing tag extensions");
		tagExts.addTagExtension(new ThrowingTagExtension(config, failure));
		config.addTagExtensionGroup(tagExts);

		engine = new WtEngineImpl(config);
		engine.setDebugHooks(hooks);

		pageId = new PageId(PageTitle.make(config, TITLE), -1);
	}

	// ==[ Failures are logged and the markup is kept ]=========================

	@Test
	public void testFailingRedirectCallbackIsLogged() throws Exception
	{
		EngProcessedPage result = engine.expand(pageId, "#REDIRECT [[Target]]", new FailingCallback(failure));

		assertMarkedAsFailed(single(result.getPage(), WtRedirect.class), failure);
		assertLoggedFailure(EngLogRedirectResolution.class, failure);
	}

	@Test
	public void testFailingTransclusionCallbackIsLogged() throws Exception
	{
		EngProcessedPage result = engine.expand(pageId, "a {{Foo|arg}} b", new FailingCallback(failure));

		assertMarkedAsFailed(single(result.getPage(), WtTemplate.class), failure);
		assertLoggedFailure(EngLogTransclusionResolution.class, failure);
	}

	@Test
	public void testFailingParserFunctionIsLogged() throws Exception
	{
		EngProcessedPage result = engine.expand(pageId, "a {{#throwingpfn: x|y}} b", new NoPagesCallback());

		assertMarkedAsFailed(single(result.getPage(), WtTemplate.class), failure);
		assertLoggedFailure(EngLogParserFunctionResolution.class, failure);
	}

	@Test
	public void testParserFunctionReturningNullIsLogged() throws Exception
	{
		EngProcessedPage result = engine.expand(pageId, "{{#nullpfn: x}}", new NoPagesCallback());

		WtTemplate template = single(result.getPage(), WtTemplate.class);
		Object error = template.getAttribute(SKIP_ATTR_NAME);
		assertTrue(String.valueOf(error), error instanceof NullPointerException);
		assertTrue(((NullPointerException) error).getMessage().contains("nullpfn"));

		assertLoggedFailure(EngLogParserFunctionResolution.class, (Exception) error);
	}

	@Test
	public void testFailingTagExtensionIsLogged() throws Exception
	{
		EngProcessedPage result = engine.expand(
				pageId,
				"<throwingtag attr=\"1\">body</throwingtag>",
				new NoPagesCallback());

		assertMarkedAsFailed(single(result.getPage(), WtTagExtension.class), failure);
		assertLoggedFailure(EngLogTagExtensionResolution.class, failure);
	}

	/**
	 * Page switches are recognized by the parser, not by the preprocessor. The
	 * expansion only encounters them in a preprocessed AST built by hand.
	 */
	@Test
	public void testFailingPageSwitchIsLogged() throws Exception
	{
		EngProcessedPage result = engine.expand(
				new NoPagesCallback(),
				pageId,
				preprocessedPageWithPageSwitch(),
				null,
				false,
				null,
				null,
				null);

		assertMarkedAsFailed(single(result.getPage(), WtPageSwitch.class), failure);
		assertLoggedFailure(EngLogMagicWordResolution.class, failure);
	}

	@Test
	public void testFailingParameterLookupIsLogged() throws Exception
	{
		ExpansionCallback callback = new NoPagesCallback();

		ExpansionFrame rootFrame = new ExpansionFrame(
				engine,
				callback,
				hooks,
				pageId.getTitle(),
				new WtEntityMapImpl(),
				false,
				new ArrayList<Warning>(),
				config.getNodeFactory().logExpansionPass(),
				false,
				true);

		EngProcessedPage result = engine.preprocessAndExpand(
				callback,
				pageId,
				"a {{{1|default}}} b",
				true,
				null,
				new FailingArguments(failure),
				rootFrame,
				rootFrame);

		assertMarkedAsFailed(single(result.getPage(), WtTemplateParameter.class), failure);
		assertLoggedFailure(EngLogParameterResolution.class, failure);
	}

	@Test
	public void testEachOccurrenceFailsOnItsOwn() throws Exception
	{
		EngProcessedPage result = engine.expand(
				pageId,
				"{{#throwingpfn: x}} {{#throwingpfn: x}}",
				new NoPagesCallback());

		List<WtTemplate> templates = findAll(result.getPage(), WtTemplate.class);
		assertEquals(2, templates.size());
		for (WtTemplate template : templates)
			assertMarkedAsFailed(template, failure);

		assertEquals(2, hooks.logs.size());
		for (EngLogResolution log : hooks.logs)
			assertSame(failure, single(log, EngLogUnhandledError.class).getException());
	}

	// ==[ Failures abort expansion if the engine does not catch all ]==========

	@Test
	public void testFailuresAbortExpansionWhenNotCatchingAll() throws Exception
	{
		engine.setCatchAll(false);

		assertAborts("#REDIRECT [[Target]]", new FailingCallback(failure));
		assertAborts("{{Foo}}", new FailingCallback(failure));
		assertAborts("{{#throwingpfn: x}}", new NoPagesCallback());
		assertAborts("<throwingtag>body</throwingtag>", new NoPagesCallback());

		try
		{
			engine.expand(
					new NoPagesCallback(),
					pageId,
					preprocessedPageWithPageSwitch(),
					null,
					false,
					null,
					null,
					null);
			fail("Expected EngineException for page switch");
		}
		catch (EngineException e)
		{
			assertAborted(e);
		}

		// Aborted resolutions do not reach the after-resolution hooks
		assertTrue(hooks.logs.isEmpty());
	}

	// =========================================================================

	private WtPreproWikitextPage preprocessedPageWithPageSwitch() throws Exception
	{
		WtPreproWikitextPage ppAst = (WtPreproWikitextPage)
				new WikitextPreprocessor(config.getParserConfig()).parseArticle("a ", TITLE);
		ppAst.add(config.getNodeFactory().pageSwitch("THROWINGSWITCH"));
		return ppAst;
	}

	private void assertAborts(String wikitext, ExpansionCallback callback) throws Exception
	{
		try
		{
			engine.expand(pageId, wikitext, callback);
			fail("Expected EngineException for: " + wikitext);
		}
		catch (EngineException e)
		{
			assertAborted(e);
		}
	}

	private void assertAborted(EngineException e)
	{
		assertTrue(e.getMessage(), e.getMessage().startsWith("Resolution failed!"));
		assertSame(failure, e.getCause());
		assertNotNull(e.getLog());
		assertEquals(TITLE, e.getLog().getTitle());
	}

	private static void assertMarkedAsFailed(WtNode node, Exception expected)
	{
		assertSame(expected, node.getAttribute(SKIP_ATTR_NAME));
	}

	private void assertLoggedFailure(
			Class<? extends EngLogResolution> resolutionType,
			Exception expected)
	{
		assertEquals(1, hooks.logs.size());

		EngLogResolution resolution = hooks.logs.get(0);
		assertSame(resolutionType, resolution.getClass());
		assertFalse(resolution.getSuccess());

		EngLogUnhandledError error = single(resolution, EngLogUnhandledError.class);
		assertSame(expected, error.getException());
		assertTrue(error.getDump().contains(expected.getClass().getName()));
	}

	// =========================================================================

	private static final class NoPagesCallback
			implements
				ExpansionCallback
	{
		@Override
		public FullPage retrieveWikitext(ExpansionFrame expansionFrame, PageTitle pageTitle)
		{
			return null;
		}

		@Override
		public String fileUrl(PageTitle pageTitle, int width, int height)
		{
			return null;
		}
	}

	/**
	 * Records the log of every completed resolution.
	 */
	private static final class RecordingHooks
			extends
				ExpansionDebugHooks
	{
		final List<EngLogResolution> logs = new ArrayList<EngLogResolution>();

		@Override
		public WtNode afterResolveRedirect(
				ExpansionVisitor expansionVisitor,
				WtRedirect n,
				String target,
				WtNode result,
				EngLogRedirectResolution log)
		{
			logs.add(log);
			return result;
		}

		@Override
		public WtNode afterResolveParserFunction(
				ExpansionVisitor expansionVisitor,
				WtTemplate n,
				ParserFunctionBase pfn,
				List<? extends WtNode> argsValues,
				WtNode result,
				EngLogParserFunctionResolution log)
		{
			logs.add(log);
			return result;
		}

		@Override
		public WtNode afterResolveTransclusion(
				ExpansionVisitor expansionVisitor,
				WtTemplate n,
				String target,
				List<WtTemplateArgument> args,
				WtNode result,
				EngLogTransclusionResolution log)
		{
			logs.add(log);
			return result;
		}

		@Override
		public WtNode afterResolveParameter(
				ExpansionVisitor expansionVisitor,
				WtTemplateParameter n,
				String name,
				WtNode result,
				EngLogParameterResolution log)
		{
			logs.add(log);
			return result;
		}

		@Override
		public WtNode afterResolveTagExtension(
				ExpansionVisitor expansionVisitor,
				WtTagExtension n,
				String name,
				WtNodeList attributes,
				WtTagExtensionBody wtTagExtensionBody,
				WtNode result,
				EngLogTagExtensionResolution log)
		{
			logs.add(log);
			return result;
		}

		@Override
		public WtNode afterResolvePageSwitch(
				ExpansionVisitor expansionVisitor,
				WtPageSwitch n,
				String word,
				WtNode result,
				EngLogMagicWordResolution log)
		{
			logs.add(log);
			return result;
		}
	}

	private static final class ThrowingParserFunction
			extends
				ParserFunctionBase
	{
		private static final long serialVersionUID = 1L;

		private final RuntimeException failure;

		ThrowingParserFunction(
				WikiConfig wikiConfig,
				String id,
				boolean pageSwitch,
				RuntimeException failure)
		{
			super(wikiConfig, PfnArgumentMode.UNEXPANDED_VALUES, pageSwitch, id);
			this.failure = failure;
		}

		@Override
		public WtNode invoke(
				WtNode template,
				ExpansionFrame preprocessorFrame,
				List<? extends WtNode> argsValues)
		{
			throw failure;
		}
	}

	private static final class NullParserFunction
			extends
				ParserFunctionBase
	{
		private static final long serialVersionUID = 1L;

		NullParserFunction(WikiConfig wikiConfig)
		{
			super(wikiConfig, "nullpfn");
		}

		@Override
		public WtNode invoke(
				WtNode template,
				ExpansionFrame preprocessorFrame,
				List<? extends WtNode> argsValues)
		{
			return null;
		}
	}

	private static final class ThrowingTagExtension
			extends
				TagExtensionBase
	{
		private static final long serialVersionUID = 1L;

		private final RuntimeException failure;

		ThrowingTagExtension(WikiConfig wikiConfig, RuntimeException failure)
		{
			super(wikiConfig, "throwingtag");
			this.failure = failure;
		}

		@Override
		public WtNode invoke(
				ExpansionFrame preprocessorFrame,
				WtTagExtension wtTagExtension,
				Map<String, WtNodeList> attributes,
				WtTagExtensionBody wtTagExtensionBody)
		{
			throw failure;
		}
	}

	/**
	 * Frame arguments whose lookup fails.
	 */
	private static final class FailingArguments
			extends
				HashMap<String, WtNodeList>
	{
		private static final long serialVersionUID = 1L;

		private final RuntimeException failure;

		FailingArguments(RuntimeException failure)
		{
			this.failure = failure;
		}

		@Override
		public WtNodeList get(Object key)
		{
			throw failure;
		}
	}
}
