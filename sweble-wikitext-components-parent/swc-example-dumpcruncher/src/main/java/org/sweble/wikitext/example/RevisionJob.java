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

package org.sweble.wikitext.example;

import java.math.BigInteger;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.sweble.wikitext.articlecruncher.Job;
import org.sweble.wikitext.dumpreader.model.Page;
import org.sweble.wikitext.dumpreader.model.Revision;

public class RevisionJob
		extends
			Job
{
	// -- page info --

	private final BigInteger pageId;

	private final BigInteger pageNamespace;

	private final String pageTitle;

	private final String pageRedirect;

	// -- revision info --

	private final BigInteger id;

	private final boolean minor;

	private final Calendar timestamp;

	// -- text info --

	private final String textText;

	private final boolean isTextDeleted;

	// =========================================================================

	/**
	 * @param page
	 *            The page as converted by
	 *            {@link org.sweble.wikitext.dumpreader.model.DumpConverter},
	 *            which makes the job independent of the export version of the
	 *            dump.
	 * @param rev
	 *            A revision of the page.
	 */
	public RevisionJob(Page page, Revision rev)
	{
		this.pageId = page.getId();

		this.pageNamespace = page.getNamespace();

		this.pageTitle = page.getTitle();

		this.pageRedirect = page.getRedirectTitle();

		this.id = rev.getId();

		this.minor = rev.isMinor();

		this.isTextDeleted = rev.isTextDeleted();

		this.textText = rev.getText();

		if (rev.getTimestamp() != null)
		{
			this.timestamp = rev.getTimestamp().toGregorianCalendar();
		}
		else
		{
			this.timestamp = new GregorianCalendar();
			this.timestamp.setTimeZone(TimeZone.getTimeZone("UTC"));
			this.timestamp.setTimeInMillis(0);
		}
	}

	// =========================================================================
	// page

	public BigInteger getPageId()
	{
		return pageId;
	}

	public BigInteger getPageNamespace()
	{
		return pageNamespace;
	}

	public String getPageTitle()
	{
		return pageTitle;
	}

	public String getPageRedirect()
	{
		return pageRedirect;
	}

	// =========================================================================
	// revision

	public BigInteger getId()
	{
		return id;
	}

	public boolean isMinor()
	{
		return minor;
	}

	public Calendar getTimestamp()
	{
		return timestamp;
	}

	// =========================================================================
	// text

	public String getTextText()
	{
		return textText;
	}

	public boolean isTextDeleted()
	{
		return isTextDeleted;
	}
}
