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
package org.sweble.wikitext.engine.output;

import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.parser.nodes.WtUrl;

public interface HtmlRendererCallback
{
	public MediaInfo getMediaInfo(String title, int width, int height);

	/**
	 * Like {@link #getMediaInfo(String, int, int)} but for the page of a
	 * multi-page file or the language of a file with translations (e.g. an
	 * SVG file), as given with the {@code page} and {@code lang} image options.
	 * The default implementation ignores both.
	 *
	 * @param lang
	 *            The language or {@code null} if not given.
	 * @param page
	 *            The page or -1 if not given.
	 */
	public default MediaInfo getMediaInfo(
			String title,
			int width,
			int height,
			String lang,
			int page)
	{
		return getMediaInfo(title, width, height);
	}

	public boolean resourceExists(PageTitle target);

	public String makeUrl(PageTitle linkTarget);

	/**
	 * Makes the URL of a page with the given query (e.g. the file description
	 * page of the page of a multi-page file). The default implementation
	 * appends the query to the URL returned by {@link #makeUrl(PageTitle)}.
	 *
	 * @param query
	 *            The query without leading {@code '?'} (e.g.
	 *            {@code "page=2&lang=de"}). It is not HTML escaped.
	 */
	public default String makeUrl(PageTitle linkTarget, String query)
	{
		String url = makeUrl(linkTarget);
		if (query == null || query.isEmpty())
			return url;
		return url + (url.indexOf('?') < 0 ? '?' : '&') + query;
	}

	public String makeUrl(WtUrl target);

	public String makeUrlMissingTarget(String path);
}
