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

import org.sweble.wikitext.parser.nodes.WtNode;

/**
 * Reported when a transcluded page is omitted because the expansion exceeded
 * the configured maximum post-expand include size.
 *
 * @see org.sweble.wikitext.engine.config.EngineConfig#getMaxPostExpandIncludeSize()
 */
public class PostExpandIncludeSizeWarning
		extends
			OffendingNodeWarning
{
	private static final long serialVersionUID = 1L;

	private final PageTitle title;

	private final long limit;

	// =========================================================================

	public PostExpandIncludeSizeWarning(
			WarningSeverity severity,
			String origin,
			WtNode node,
			PageTitle title,
			long limit)
	{
		super(node, severity, origin, makeMessage(title, limit));
		this.title = title;
		this.limit = limit;
	}

	public PostExpandIncludeSizeWarning(
			WarningSeverity severity,
			Class<?> origin,
			WtNode node,
			PageTitle title,
			long limit)
	{
		super(node, severity, origin, makeMessage(title, limit));
		this.title = title;
		this.limit = limit;
	}

	private static String makeMessage(PageTitle title, long limit)
	{
		return "Template omitted, post-expand include size too large (" + limit +
				"): `" + title.getDenormalizedFullTitle() + "'";
	}

	public PageTitle getTitle()
	{
		return title;
	}

	public long getLimit()
	{
		return limit;
	}

	// =========================================================================

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (int) (limit ^ (limit >>> 32));
		result = prime * result + ((title == null) ? 0 : title.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		PostExpandIncludeSizeWarning other = (PostExpandIncludeSizeWarning) obj;
		if (limit != other.limit)
			return false;
		if (title == null)
		{
			if (other.title != null)
				return false;
		}
		else if (!title.equals(other.title))
			return false;
		return true;
	}
}
