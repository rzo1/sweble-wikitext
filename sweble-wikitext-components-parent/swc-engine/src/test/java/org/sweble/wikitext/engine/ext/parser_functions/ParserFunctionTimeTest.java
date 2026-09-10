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

package org.sweble.wikitext.engine.ext.parser_functions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

public class ParserFunctionTimeTest
{
	/**
	 * The current time of the wiki: Wednesday, 10th March 2021, 14:25:13 UTC.
	 */
	static final Instant NOW = Instant.parse("2021-03-10T14:25:13Z");

	/**
	 * All format characters of PHP's <code>date()</code> which MediaWiki's
	 * <code>Language::sprintfDate()</code> supports.
	 */
	static final String ALL = "d|D|j|l|N|w|z|W|F|m|M|n|t|L|o|Y|y|a|A|g|G|h|H|i|s|c|r|U|e|I|O|P|T|Z";

	private static final ZoneId UTC = ZoneId.of("UTC");

	// =========================================================================

	/**
	 * The expected values were obtained from PHP 8.3's <code>date()</code>.
	 */
	@Test
	public void testFormatCharacters()
	{
		assertFormat(
				"07|Tue|7|Tuesday|2|2|65|10|March|03|Mar|3|31|0|2017|2017|17|am|AM|1|1|01|01|02|03|"
						+ "2017-03-07T01:02:03+00:00|Tue, 07 Mar 2017 01:02:03 +0000|1488848523|UTC|0|+0000|+00:00|UTC|0",
				ALL,
				date(2017, 3, 7, 1, 2, 3, UTC));

		// Sunday in the last ISO week of the previous year
		assertFormat(
				"03|Sun|3|Sunday|7|0|2|53|January|01|Jan|1|31|0|2020|2021|21|am|AM|12|0|12|00|00|00|"
						+ "2021-01-03T00:00:00+00:00|Sun, 03 Jan 2021 00:00:00 +0000|1609632000|UTC|0|+0000|+00:00|UTC|0",
				ALL,
				date(2021, 1, 3, 0, 0, 0, UTC));

		// Last day of a leap year
		assertFormat(
				"31|Thu|31|Thursday|4|4|365|53|December|12|Dec|12|31|1|2020|2020|20|pm|PM|11|23|11|23|59|59|"
						+ "2020-12-31T23:59:59+00:00|Thu, 31 Dec 2020 23:59:59 +0000|1609459199|UTC|0|+0000|+00:00|UTC|0",
				ALL,
				date(2020, 12, 31, 23, 59, 59, UTC));

		// Monday in the first ISO week of the next year
		assertFormat(
				"30|Mon|30|Monday|1|1|363|01|December|12|Dec|12|31|0|2020|2019|19|pm|PM|12|12|12|12|00|00|"
						+ "2019-12-30T12:00:00+00:00|Mon, 30 Dec 2019 12:00:00 +0000|1577707200|UTC|0|+0000|+00:00|UTC|0",
				ALL,
				date(2019, 12, 30, 12, 0, 0, UTC));

		// Half an hour after midnight
		assertFormat(
				"14|Thu|14|Thursday|4|4|194|28|July|07|Jul|7|31|0|2005|2005|05|am|AM|12|0|12|00|30|00|"
						+ "2005-07-14T00:30:00+00:00|Thu, 14 Jul 2005 00:30:00 +0000|1121301000|UTC|0|+0000|+00:00|UTC|0",
				ALL,
				date(2005, 7, 14, 0, 30, 0, UTC));
	}

	@Test
	public void testYearsArePadded()
	{
		assertFormat("0999 99", "Y y", date(999, 1, 1, 0, 0, 0, UTC));
		assertFormat("0000 00", "Y y", date(0, 1, 1, 0, 0, 0, UTC));
	}

	@Test
	public void testExtensions()
	{
		ZonedDateTime timestamp = date(2017, 3, 7, 1, 2, 3, UTC);

		// literal x, unknown extensions and a trailing x
		assertFormat("x", "xx", timestamp);
		assertFormat("q", "xq", timestamp);
		assertFormat("2017x", "Yx", timestamp);

		// raw digits
		assertFormat("2017", "xnY", timestamp);
		assertFormat("2017 03", "xNY m", timestamp);
		assertFormat("2017", "xhY", timestamp);

		// roman numerals apply to the next number only
		assertFormat("MMXVII 2017", "xrY Y", timestamp);
		assertFormat("III", "xrn", timestamp);
		assertFormat("VII", "xrj", timestamp);
		assertEquals("MMMMMMMMMM", ParserFunctionTime.romanNumeral(10000));
		assertEquals("10001", ParserFunctionTime.romanNumeral(10001));
		assertEquals("0", ParserFunctionTime.romanNumeral(0));

		// genitive month name
		assertFormat("March", "xg", timestamp);
		assertEquals("март", ParserFunctionTime.format("F", timestamp, Locale.forLanguageTag("ru")));
		assertEquals("марта", ParserFunctionTime.format("xg", timestamp, Locale.forLanguageTag("ru")));
		assertEquals("marzec", ParserFunctionTime.format("F", timestamp, Locale.forLanguageTag("pl")));
		assertEquals("marca", ParserFunctionTime.format("xg", timestamp, Locale.forLanguageTag("pl")));

		// Thai solar and Minguo years
		assertFormat("2560", "xkY", timestamp);
		assertFormat("2472", "xkY", date(1930, 2, 1, 0, 0, 0, UTC));
		assertFormat("2473", "xkY", date(1930, 4, 1, 0, 0, 0, UTC));
		assertFormat("106", "xoY", timestamp);
	}

	@Test
	public void testEscaping()
	{
		ZonedDateTime timestamp = date(2017, 3, 7, 1, 2, 3, UTC);

		assertFormat("Y", "\\Y", timestamp);
		assertFormat("2017\\", "Y\\", timestamp);
		assertFormat("Y", "\"Y\"", timestamp);
		assertFormat("\"", "\"", timestamp);
		assertFormat("02'03\"", "i's\"", timestamp);
		assertFormat("The month is March", "\"The month is\" F", timestamp);
		assertFormat("2017-Ymds h-03-CD-07", "Y-\"Ymds h\"-m-\"CD\"-d", timestamp);
		assertFormat("2017-03-07T01:02:03+00:00", "Y-m-d\"T\"H:i:sP", timestamp);
	}

	@Test
	public void testLocalizedNames()
	{
		ZonedDateTime tuesday = date(2017, 3, 7, 1, 2, 3, UTC);

		assertTrue(ParserFunctionTime.format("M", tuesday, Locale.GERMAN).startsWith("Mär"));
		assertEquals("März", ParserFunctionTime.format("F", tuesday, Locale.GERMAN));
		assertTrue(ParserFunctionTime.format("D", tuesday, Locale.GERMAN).startsWith("Di"));
		assertEquals("Dienstag", ParserFunctionTime.format("l", tuesday, Locale.GERMAN));
		assertEquals("februari", ParserFunctionTime.format("F", date(1988, 2, 28, 0, 0, 0, UTC), new Locale("nl")));
		assertEquals("金曜日", ParserFunctionTime.format("l", date(2017, 11, 24, 0, 0, 0, UTC), Locale.JAPANESE));

		// c and r are never localized
		assertEquals(
				"Tue, 07 Mar 2017 01:02:03 +0000",
				ParserFunctionTime.format("r", tuesday, Locale.GERMAN));
	}

	@Test
	public void testFormatCalendar()
	{
		Calendar timestamp = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
		timestamp.clear();
		timestamp.set(2017, Calendar.MARCH, 7, 1, 2, 3);

		assertEquals(
				"2017-03-07T01:02:03+00:00 1488848523",
				ParserFunctionTime.format("c U", timestamp, Locale.ENGLISH));
	}

	// =========================================================================

	@Test
	public void testExamplesFromIssue() throws Exception
	{
		assertTime("13:05", "{{#time: H:i|2021-03-04 13:05}}");
		assertTime("13:05", "{{#time: H:i|2021-03-04T13:05}}");
		assertTime("1999-12-31", "{{#time: Y-m-d|2000-01-01 -1 day}}");
		assertTime("2021-03-04", "{{#time: Y-m-d|March 4, 2021}}");
		assertTime("1970-01-01 00:00:00", "{{#time: Y-m-d H:i:s|@0}}");
		assertTime("2021-03", "{{#time: Y-m|2021-03}}");
		assertTime("2021-03-10 13:05", "{{#time: Y-m-d H:i|13:05}}");
		assertTime("53", "{{#time: W|2021-01-03}}");
		assertTime("2020", "{{#time: o|2021-01-03}}");
	}

	@Test
	public void testCurrentTimeIsUsedWithoutDate() throws Exception
	{
		assertTime("2021-03-10 14:25:13", "{{#time: Y-m-d H:i:s}}");
		assertTime("2021-03-10 14:25:13", "{{#time: Y-m-d H:i:s|}}");
		assertTime("2021-03-10 14:25:13", "{{#time: Y-m-d H:i:s|now}}");
	}

	@Test
	public void testRelativeDates() throws Exception
	{
		assertTime("2021-03-09", "{{#time: Y-m-d|-1 day}}");
		assertTime("2021-03-24", "{{#time: Y-m-d|+2 weeks}}");
		assertTime("2021-03-15", "{{#time: Y-m-d|next monday}}");
		assertTime("2021-04-30", "{{#time: Y-m-d|last day of next month}}");
		assertTime("2021-03-11 00:00:00", "{{#time: Y-m-d H:i:s|tomorrow}}");
		assertTime("2021-03-09 00:00:00", "{{#time: Y-m-d H:i:s|yesterday}}");
		assertTime("2021-03-10 00:00:00", "{{#time: Y-m-d H:i:s|today}}");
	}

	@Test
	public void testFourDigitNumberIsYear() throws Exception
	{
		assertTime("1999-03-10 00:00", "{{#time: Y-m-d H:i|1999}}");
		assertTime("2021-03-10 00:00", "{{#time: Y-m-d H:i|2021}}");
	}

	@Test
	public void testTimeZoneOfInputIsConvertedToUtc() throws Exception
	{
		assertTime("11:05 UTC", "{{#time: H:i T|2021-03-04 13:05 +02:00}}");
		assertTime("12:05 UTC", "{{#time: H:i e|2021-03-04 13:05 Europe/Berlin}}");
	}

	@Test
	public void testTimeIsUtcRegardlessOfWikiTimeZone() throws Exception
	{
		TimeZone berlin = TimeZone.getTimeZone("Europe/Berlin");
		assertEquals("13:05 UTC", expand("{{#time: H:i e|2021-03-04 13:05}}", berlin));
		assertEquals("2021-03-10 14:25:13", expand("{{#time: Y-m-d H:i:s}}", berlin));

		// the fourth argument selects the local time zone
		assertEquals("14:05 CET", expand("{{#time: H:i T|2021-03-04 13:05||1}}", berlin));
		assertEquals("13:05 UTC", expand("{{#time: H:i T|2021-03-04 13:05||0}}", berlin));
	}

	@Test
	public void testLanguage() throws Exception
	{
		assertTime("Freitag", "{{#time: l|2017-11-24|de}}");
		assertTime("28 februari 1988", "{{#time: d F Y|1988-02-28|nl}}");

		// unknown languages fall back to the content language
		assertTime("March", "{{#time: F|2021-03-04|xyz}}");
	}

	@Test
	public void testInvalidTime() throws Exception
	{
		assertTime("<strong class=\"error\">Error: Invalid time.</strong>", "{{#time: Y|foo}}");
		assertTime("<strong class=\"error\">Error: Invalid time.</strong>", "{{#time: Y|2021-13-01}}");
		assertTime("bad", "{{#iferror: {{#time: Y|foo}} | bad | good }}");
	}

	@Test
	public void testYearOutOfRange() throws Exception
	{
		assertTime(
				"<strong class=\"error\">Error: #time only supports years from 0.</strong>",
				"{{#time: Y|-0001-01-01}}");
		assertTime(
				"<strong class=\"error\">Error: #time only supports years up to 9999.</strong>",
				"{{#time: Y|@253402300800}}");
		assertTime("0000 9999", "{{#time: Y|0000-01-01}} {{#time: Y|@253402300799}}");
	}

	// =========================================================================

	static ZonedDateTime date(
			int year,
			int month,
			int day,
			int hour,
			int minute,
			int second,
			ZoneId zone)
	{
		return ZonedDateTime.of(year, month, day, hour, minute, second, 0, zone);
	}

	static void assertFormat(String expected, String format, ZonedDateTime timestamp)
	{
		assertEquals(format, expected, ParserFunctionTime.format(format, timestamp, Locale.ENGLISH));
	}

	private static void assertTime(String expected, String wikitext) throws Exception
	{
		assertEquals(wikitext, expected, expand(wikitext, TimeZone.getTimeZone("UTC")));
	}

	/**
	 * Expands the wikitext in a wiki with the given time zone, whose current
	 * time is {@link #NOW}.
	 */
	static String expand(String wikitext, final TimeZone timezone) throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();
		config.setTimezone(timezone);
		config.setRuntimeInfo(new WikiRuntimeInfo()
		{
			@Override
			public Calendar getDateAndTime(Locale locale)
			{
				Calendar timestamp = new GregorianCalendar(timezone, locale);
				timestamp.setTimeInMillis(NOW.toEpochMilli());
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
