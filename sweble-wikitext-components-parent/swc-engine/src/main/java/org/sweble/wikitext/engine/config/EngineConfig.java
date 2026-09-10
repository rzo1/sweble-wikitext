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

package org.sweble.wikitext.engine.config;

public interface EngineConfig
{

	public abstract boolean isTrimTransparentBeforeParsing();

	/**
	 * Returns the maximum depth of nested transclusions, like MediaWiki's
	 * {@code $wgMaxTemplateDepth}. A page that is expanded at this depth
	 * cannot transclude further pages; the transclusion is replaced by an
	 * error message instead.
	 *
	 * The default implementation returns
	 * {@link EngineConfigImpl#DEFAULT_MAX_TEMPLATE_DEPTH}.
	 */
	public default int getMaxTemplateDepth()
	{
		return EngineConfigImpl.DEFAULT_MAX_TEMPLATE_DEPTH;
	}

	/**
	 * Returns the maximum post-expand include size, like MediaWiki's
	 * {@code $wgMaxArticleSize} in bytes. The size of every expanded
	 * transclusion is added up (nested transclusions count again at every
	 * level). A transclusion that would exceed the limit is omitted and so
	 * are all following transclusions of the same expansion process.
	 *
	 * The size of an expanded transclusion is the length of the text it
	 * contains plus one for every other node of its AST, but at least 1. This
	 * way the number of transclusions and the time needed to measure the size
	 * are limited as well.
	 *
	 * The default implementation returns
	 * {@link EngineConfigImpl#DEFAULT_MAX_POST_EXPAND_INCLUDE_SIZE}.
	 */
	public default long getMaxPostExpandIncludeSize()
	{
		return EngineConfigImpl.DEFAULT_MAX_POST_EXPAND_INCLUDE_SIZE;
	}

	/**
	 * Returns the maximum number of consecutive redirects that are followed.
	 * This applies to redirects of transcluded pages as well as to the
	 * redirect of the page that is expanded. A redirect that is not followed
	 * is left unresolved.
	 *
	 * MediaWiki follows up to two redirects when transcluding a page (see
	 * Parser::statelessFetchTemplate(), up to version 1.35 only one) and a
	 * single redirect ({@code $wgMaxRedirects}) when viewing a page.
	 *
	 * The default implementation returns
	 * {@link EngineConfigImpl#DEFAULT_MAX_REDIRECTS}.
	 */
	public default int getMaxRedirects()
	{
		return EngineConfigImpl.DEFAULT_MAX_REDIRECTS;
	}

}
