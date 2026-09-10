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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngLogContainer;
import org.sweble.wikitext.engine.utils.UrlService;
import org.sweble.wikitext.parser.WtEntityMap;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtNodeList;

import de.fau.cs.osr.ptk.common.Warning;

public class ExpansionFrame
{
	private final WtEngineImpl engine;

	private final ExpansionFrame rootFrame;

	private final ExpansionFrame parentFrame;

	private final PageTitle title;

	private final Map<String, WtNodeList> arguments;

	private final boolean forInclusion;

	private final EngLogContainer frameLog;

	private final ExpansionCallback callback;

	private final List<Warning> warnings;

	private final WtEntityMap entityMap;

	private final boolean noRedirect;

	private final int depth;

	private final int redirects;

	/**
	 * The post-expand include size of the whole expansion process. Only the
	 * field of the root frame is used.
	 */
	private long postExpandIncludeSize;

	/**
	 * Set in the root frame once a transclusion exceeded the maximum
	 * post-expand include size.
	 */
	private boolean postExpandIncludeSizeExceeded;

	private ExpansionVisitor expansionVisitor;

	// FIXME: That should have been initialized from a request!
	//        And only once for the whole expansion process!
	private UrlService urlService = new UrlService();

	// =========================================================================

	public ExpansionFrame(
			WtEngineImpl engine,
			ExpansionCallback callback,
			ExpansionDebugHooks hooks,
			PageTitle title,
			WtEntityMap entityMap,
			boolean noRedirect,
			List<Warning> warnings,
			EngLogContainer frameLog,
			boolean timingEnabled,
			boolean catchAll)
	{
		this(
				engine,
				callback,
				hooks,
				title,
				entityMap,
				false,
				noRedirect,
				warnings,
				frameLog,
				timingEnabled,
				catchAll);
	}

	/**
	 * Creates the root frame of an expansion process.
	 *
	 * @param forInclusion
	 *            Whether the page is expanded for inclusion. This is passed on
	 *            to the target page of a redirect.
	 */
	public ExpansionFrame(
			WtEngineImpl engine,
			ExpansionCallback callback,
			ExpansionDebugHooks hooks,
			PageTitle title,
			WtEntityMap entityMap,
			boolean forInclusion,
			boolean noRedirect,
			List<Warning> warnings,
			EngLogContainer frameLog,
			boolean timingEnabled,
			boolean catchAll)
	{
		this.engine = engine;
		this.callback = callback;
		this.title = title;
		this.entityMap = entityMap;
		this.arguments = new HashMap<String, WtNodeList>();
		this.forInclusion = forInclusion;
		this.noRedirect = noRedirect;
		this.warnings = warnings;
		this.frameLog = frameLog;
		this.rootFrame = this;
		this.parentFrame = null;
		this.depth = 0;
		this.redirects = 0;

		expansionVisitor = new ExpansionVisitor(
				this,
				frameLog,
				hooks,
				timingEnabled,
				catchAll);
	}

	public ExpansionFrame(
			WtEngineImpl engine,
			ExpansionCallback callback,
			ExpansionDebugHooks hooks,
			PageTitle title,
			WtEntityMap entityMap,
			Map<String, WtNodeList> arguments,
			boolean forInclusion,
			boolean noRedirect,
			ExpansionFrame rootFrame,
			ExpansionFrame parentFrame,
			List<Warning> warnings,
			EngLogContainer frameLog,
			boolean timingEnabled,
			boolean catchAll)
	{
		this(
				engine,
				callback,
				hooks,
				title,
				entityMap,
				arguments,
				forInclusion,
				noRedirect,
				rootFrame,
				parentFrame,
				false,
				warnings,
				frameLog,
				timingEnabled,
				catchAll);
	}

	/**
	 * Creates the frame of a transcluded page or of the target page of a
	 * redirect.
	 *
	 * @param redirect
	 *            Whether the page is the target of a redirect of the parent
	 *            frame. The target page replaces the redirecting page and is
	 *            expanded at the same depth.
	 */
	public ExpansionFrame(
			WtEngineImpl engine,
			ExpansionCallback callback,
			ExpansionDebugHooks hooks,
			PageTitle title,
			WtEntityMap entityMap,
			Map<String, WtNodeList> arguments,
			boolean forInclusion,
			boolean noRedirect,
			ExpansionFrame rootFrame,
			ExpansionFrame parentFrame,
			boolean redirect,
			List<Warning> warnings,
			EngLogContainer frameLog,
			boolean timingEnabled,
			boolean catchAll)
	{
		this.engine = engine;
		this.callback = callback;
		this.title = title;
		this.entityMap = entityMap;
		this.arguments = arguments;
		this.forInclusion = forInclusion;
		this.noRedirect = noRedirect;
		this.warnings = warnings;
		this.frameLog = frameLog;
		this.rootFrame = rootFrame;
		this.parentFrame = parentFrame;
		if (parentFrame == null)
		{
			this.depth = 0;
			this.redirects = redirect ? 1 : 0;
		}
		else if (redirect)
		{
			this.depth = parentFrame.getDepth();
			this.redirects = parentFrame.getRedirectCount() + 1;
		}
		else
		{
			this.depth = parentFrame.getDepth() + 1;
			this.redirects = 0;
		}

		expansionVisitor = new ExpansionVisitor(
				this,
				frameLog,
				hooks,
				timingEnabled,
				catchAll);
	}

	// =========================================================================

	public WtEngineImpl getEngine()
	{
		return engine;
	}

	public ExpansionFrame getRootFrame()
	{
		return rootFrame;
	}

	public ExpansionFrame getParentFrame()
	{
		return parentFrame;
	}

	public PageTitle getTitle()
	{
		return title;
	}

	public Map<String, WtNodeList> getArguments()
	{
		return arguments;
	}

	public boolean isForInclusion()
	{
		return forInclusion;
	}

	public EngLogContainer getFrameLog()
	{
		return frameLog;
	}

	public WikiConfig getWikiConfig()
	{
		return engine.getWikiConfig();
	}

	public void fileWarning(Warning warning)
	{
		warnings.add(warning);
	}

	public void addWarnings(Collection<Warning> warnings)
	{
		this.warnings.addAll(warnings);
	}

	public List<Warning> getWarnings()
	{
		return warnings;
	}

	public WtEntityMap getEntityMap()
	{
		return entityMap;
	}

	public ExpansionCallback getCallback()
	{
		return callback;
	}

	public boolean isNoRedirect()
	{
		return noRedirect;
	}

	public UrlService getUrlService()
	{
		return urlService;
	}

	/**
	 * Returns the number of nested transclusions that lead from the root frame
	 * to this frame. The root frame has depth 0. Following a redirect does not
	 * increase the depth.
	 */
	public int getDepth()
	{
		return depth;
	}

	/**
	 * Returns the number of consecutive redirects that were followed to reach
	 * this frame. The frame of a page that is not the target of a redirect has
	 * a redirect count of 0.
	 */
	public int getRedirectCount()
	{
		return redirects;
	}

	/**
	 * Returns the post-expand include size of the whole expansion process so
	 * far.
	 *
	 * @see org.sweble.wikitext.engine.config.EngineConfig#getMaxPostExpandIncludeSize()
	 */
	public long getPostExpandIncludeSize()
	{
		return rootFrame.postExpandIncludeSize;
	}

	/**
	 * Returns whether a transclusion of the expansion process exceeded the
	 * maximum post-expand include size. All further transclusions are omitted.
	 */
	public boolean isPostExpandIncludeSizeExceeded()
	{
		return rootFrame.postExpandIncludeSizeExceeded;
	}

	/**
	 * Adds the size of an expanded transclusion to the post-expand include
	 * size of the whole expansion process.
	 *
	 * @return False if the size would exceed the given limit. The size is not
	 *         added then and all further calls fail as well.
	 */
	boolean incrementPostExpandIncludeSize(long size, long limit)
	{
		ExpansionFrame root = rootFrame;
		if (root.postExpandIncludeSizeExceeded)
			return false;

		if (size > limit - root.postExpandIncludeSize)
		{
			root.postExpandIncludeSizeExceeded = true;
			return false;
		}

		root.postExpandIncludeSize += size;
		return true;
	}

	// =========================================================================

	public WtNode expand(WtNode ppAst) throws ExpansionException
	{
		try
		{
			return (WtNode) expansionVisitor.go(ppAst);
		}
		catch (Exception e)
		{
			throw new ExpansionException(e);
		}
	}

	// =========================================================================

	public boolean existsPage(PageTitle pageTitle)
	{
		return (callback.retrieveWikitext(this, pageTitle) != null) ||
				(callback.fileUrl(pageTitle, -1, -1) != null);
	}
}
