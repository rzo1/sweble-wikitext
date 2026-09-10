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

package org.sweble.wikitext.engine.ext.core;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Test;
import org.sweble.wikitext.engine.ExpansionCallback;
import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.FullPage;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.config.WikiRuntimeInfo;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.utils.WtRtDataPrinter;

/**
 * The date and time variables like {{CURRENTYEAR}} and {{LOCALYEAR}}.
 *
 * Like in MediaWiki the CURRENT* variables use UTC, the LOCAL* variables the
 * local time zone of the wiki.
 */
public class CorePfnVariablesDateAndTimeTest
{
	/**
	 * Friday, 31st December 2021, 23:30:05 UTC, which is Saturday, 1st
	 * January 2022, 00:30:05 in Berlin.
	 */
	private static final Instant NEW_YEARS_EVE = Instant.parse("2021-12-31T23:30:05Z");

	/**
	 * Tuesday, 5th January 2021, 08:04:09 UTC.
	 */
	private static final Instant EARLY_JANUARY = Instant.parse("2021-01-05T08:04:09Z");

	private static final TimeZone BERLIN = TimeZone.getTimeZone("Europe/Berlin");

	private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

	// =========================================================================

	@Test
	public void testCurrentYearMonthAndDayUseUtc() throws Exception
	{
		assertEquals("2021", expand("{{CURRENTYEAR}}", NEW_YEARS_EVE, BERLIN));
		assertEquals("12", expand("{{CURRENTMONTH}}", NEW_YEARS_EVE, BERLIN));
		assertEquals("31", expand("{{CURRENTDAY}}", NEW_YEARS_EVE, BERLIN));
	}

	@Test
	public void testCurrentVariables() throws Exception
	{
		assertCurrent("12", "{{CURRENTMONTH2}}");
		assertCurrent("12", "{{CURRENTMONTH1}}");
		assertCurrent("December", "{{CURRENTMONTHNAME}}");
		assertCurrent("December", "{{CURRENTMONTHNAMEGEN}}");
		assertCurrent("Dec", "{{CURRENTMONTHABBREV}}");
		assertCurrent("31", "{{CURRENTDAY2}}");
		assertCurrent("5", "{{CURRENTDOW}}");
		assertCurrent("Friday", "{{CURRENTDAYNAME}}");
		assertCurrent("23:30", "{{CURRENTTIME}}");
		assertCurrent("23", "{{CURRENTHOUR}}");
		assertCurrent("52", "{{CURRENTWEEK}}");
		assertCurrent("20211231233005", "{{CURRENTTIMESTAMP}}");
	}

	@Test
	public void testLocalVariablesUseTimezoneOfWiki() throws Exception
	{
		assertCurrent("2022", "{{LOCALYEAR}}");
		assertCurrent("01", "{{LOCALMONTH}}");
		assertCurrent("01", "{{LOCALMONTH2}}");
		assertCurrent("1", "{{LOCALMONTH1}}");
		assertCurrent("January", "{{LOCALMONTHNAME}}");
		assertCurrent("January", "{{LOCALMONTHNAMEGEN}}");
		assertCurrent("Jan", "{{LOCALMONTHABBREV}}");
		assertCurrent("1", "{{LOCALDAY}}");
		assertCurrent("01", "{{LOCALDAY2}}");
		assertCurrent("6", "{{LOCALDOW}}");
		assertCurrent("Saturday", "{{LOCALDAYNAME}}");
		assertCurrent("00:30", "{{LOCALTIME}}");
		assertCurrent("00", "{{LOCALHOUR}}");
		assertCurrent("52", "{{LOCALWEEK}}");
		assertCurrent("20220101003005", "{{LOCALTIMESTAMP}}");
	}

	@Test
	public void testPaddingOfNumbers() throws Exception
	{
		assertEquals("01", expand("{{CURRENTMONTH}}", EARLY_JANUARY, UTC));
		assertEquals("1", expand("{{CURRENTMONTH1}}", EARLY_JANUARY, UTC));
		assertEquals("5", expand("{{CURRENTDAY}}", EARLY_JANUARY, UTC));
		assertEquals("05", expand("{{CURRENTDAY2}}", EARLY_JANUARY, UTC));
		assertEquals("08", expand("{{CURRENTHOUR}}", EARLY_JANUARY, UTC));
		assertEquals("08:04", expand("{{CURRENTTIME}}", EARLY_JANUARY, UTC));
		assertEquals("1", expand("{{CURRENTWEEK}}", EARLY_JANUARY, UTC));
		assertEquals("1", expand("{{LOCALWEEK}}", EARLY_JANUARY, UTC));
		assertEquals("2", expand("{{CURRENTDOW}}", EARLY_JANUARY, UTC));
	}

	@Test
	public void testVariablesAreCaseSensitive() throws Exception
	{
		assertEquals("{{currentdayname}}", expand("{{currentdayname}}", EARLY_JANUARY, UTC));
	}

	// =========================================================================

	private static void assertCurrent(String expected, String wikitext) throws Exception
	{
		assertEquals(wikitext, expected, expand(wikitext, NEW_YEARS_EVE, BERLIN));
	}

	/**
	 * Expands the wikitext in a wiki with the given time zone, whose current
	 * time is <code>now</code>.
	 */
	private static String expand(
			String wikitext,
			final Instant now,
			final TimeZone timezone) throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();
		config.setTimezone(timezone);
		config.setRuntimeInfo(new WikiRuntimeInfo()
		{
			@Override
			public Calendar getDateAndTime(Locale locale)
			{
				Calendar timestamp = new GregorianCalendar(timezone, locale);
				timestamp.setTimeInMillis(now.toEpochMilli());
				return timestamp;
			}

			@Override
			public Calendar getDateAndTime()
			{
				return getDateAndTime(Locale.ENGLISH);
			}
		});

		WtEngineImpl engine = new WtEngineImpl(config);
		PageId pageId = new PageId(PageTitle.make(config, "Test"), -1);
		EngProcessedPage page = engine.expand(pageId, wikitext, new NullCallback());
		return WtRtDataPrinter.print(page.getPage());
	}

	private static final class NullCallback
			implements
				ExpansionCallback
	{
		@Override
		public FullPage retrieveWikitext(
				ExpansionFrame expansionFrame,
				PageTitle pageTitle)
		{
			return null;
		}

		@Override
		public String fileUrl(PageTitle pageTitle, int width, int height)
		{
			return null;
		}
	}
}
