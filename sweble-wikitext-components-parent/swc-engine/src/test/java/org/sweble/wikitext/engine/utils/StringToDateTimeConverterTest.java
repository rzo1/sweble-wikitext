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

package org.sweble.wikitext.engine.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.junit.Test;

/**
 * The expected values were obtained from PHP 8.3's <code>DateTime</code>
 * class with the same fixed current time, which MediaWiki uses to parse the
 * date argument of <code>#time</code>.
 */
public class StringToDateTimeConverterTest
{
	/**
	 * Wednesday, 10th March 2021, 14:25:13 UTC.
	 */
	private static final Instant NOW = Instant.parse("2021-03-10T14:25:13Z");

	private static final DateTimeFormatter FORMAT =
			DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

	private final StringToDateTimeConverter conv =
			new StringToDateTimeConverter(ZoneId.of("UTC"), NOW);

	// =========================================================================

	@Test
	public void testExamplesFromIssue()
	{
		assertParsed("2021-03-04 13:05:00", "2021-03-04 13:05");
		assertParsed("2021-03-04 13:05:00", "2021-03-04T13:05");
		assertParsed("1999-12-31 00:00:00", "2000-01-01 -1 day");
		assertParsed("2021-03-04 00:00:00", "March 4, 2021");
		assertParsed("1970-01-01 00:00:00", "@0");
		assertParsed("2021-03-01 00:00:00", "2021-03");
		assertParsed("2021-03-10 13:05:00", "13:05");
		assertParsed("2021-01-03 00:00:00", "2021-01-03");
	}

	@Test
	public void testIsoDatesAndTimes()
	{
		assertParsed("2021-03-04 13:05:07", "2021-03-04 13:05:07");
		assertParsed("2021-03-04 13:05:07", "2021-03-04T13:05:07");
		assertParsed("2021-03-04 13:05:07", "2021-03-04T13:05:07.5");
		assertParsed("2021-03-04 13:05:00", "2021-03-04t13:05");
		assertParsed("2021-03-04 15:30:00", "2021-03-04 1530");
		assertParsed("2021-03-05 00:00:00", "2021-03-04 24:00");
		assertParsed("2021-03-04 00:00:00", "20210304");
		assertParsed("2021-03-04 13:15:00", "20210304131500");
		assertParsed("2021-03-04 13:05:07", "20210304T13:05:07");
		assertParsed("2021-03-04 13:05:07", "2021:03:04 13:05:07");
		assertParsed("0000-01-01 00:00:00", "0000-01-01");
	}

	@Test
	public void testTimesWithoutDate()
	{
		assertParsed("2021-03-10 13:05:00", "13:05");
		assertParsed("2021-03-10 13:05:07", "13:05:07");
		assertParsed("2021-03-10 13:05:07", "13:05:07.5");
		assertParsed("2021-03-10 04:03:21", "04.03.21");
		assertParsed("2021-03-10 12:00:00", "12pm");
		assertParsed("2021-03-10 00:00:00", "12am");
		assertParsed("2021-03-10 13:30:00", "1:30 pm");
		assertParsed("2021-03-10 01:30:05", "1:30:05 a.m.");
	}

	@Test
	public void testFourDigitNumbersAreTimesUnlessATimeWasGiven()
	{
		assertParsed("2021-03-10 20:21:00", "2021");
		assertParsed("2021-03-10 00:00:00", "00:00 2021");
		assertParsed("1999-03-10 00:00:00", "00:00 1999");
		assertParsed("1999-03-10 14:25:13", "1999");
	}

	@Test
	public void testUnixTimestamps()
	{
		assertParsed("1970-01-01 00:00:00", "@0");
		assertParsed("2017-12-01 21:22:23", "@1512163343");
		assertParsed("1969-12-31 00:00:00", "@-86400");
		assertParsed("2017-12-02 21:22:23", "@1512163343 +1 day");
	}

	@Test
	public void testDatesWithMonthNames()
	{
		assertParsed("2021-03-04 00:00:00", "March 4, 2021");
		assertParsed("2021-03-04 00:00:00", "March 4th, 2021");
		assertParsed("2021-03-04 00:00:00", "4 March 2021");
		assertParsed("2021-03-04 00:00:00", "04-Mar-2021");
		assertParsed("2017-11-24 00:00:00", "24 Nov 2017");
		assertParsed("2017-09-24 00:00:00", "24 Sept 2017");
		assertParsed("2021-03-01 00:00:00", "Mar 2021");
		assertParsed("2021-03-10 00:00:00", "March");
		assertParsed("2021-01-01 00:00:00", "1 January");
		assertParsed("2021-01-01 00:00:00", "Jan 1");
		assertParsed("2021-03-04 13:05:00", "March 4 13:05");
		assertParsed("2021-03-04 13:05:00", "Thu, 04 Mar 2021 13:05:00 +0000");
	}

	@Test
	public void testOtherDateFormats()
	{
		assertParsed("2021-03-04 00:00:00", "2021/03/04");
		assertParsed("2021-03-04 00:00:00", "03/04/2021");
		assertParsed("2017-11-24 00:00:00", "24.11.2017");
		assertParsed("2021-03-01 00:00:00", "2021-W09");
		assertParsed("2021-03-03 00:00:00", "2021-W09-3");
		assertParsed("2021-03-04 00:00:00", "2021.063");
	}

	@Test
	public void testInvalidDaysOverflowIntoNextMonth()
	{
		assertParsed("2021-03-02 00:00:00", "2021-02-30");
		assertParsed("2021-03-03 00:00:00", "31 February 2021");
	}

	@Test
	public void testKeywords()
	{
		assertParsed("2021-03-10 14:25:13", "now");
		assertParsed("2021-03-10 00:00:00", "today");
		assertParsed("2021-03-10 00:00:00", "midnight");
		assertParsed("2021-03-10 12:00:00", "noon");
		assertParsed("2021-03-11 00:00:00", "tomorrow");
		assertParsed("2021-03-09 00:00:00", "yesterday");
		assertParsed("2021-03-11 13:05:00", "tomorrow 13:05");
		assertParsed("2021-03-10 12:00:00", "today noon");
		assertParsed("2021-03-10 14:25:13", "NOW");
	}

	@Test
	public void testRelativeOffsets()
	{
		assertParsed("2021-03-11 14:25:13", "+1 day");
		assertParsed("2021-03-09 14:25:13", "-1 day");
		assertParsed("2021-02-24 14:25:13", "-2 weeks");
		assertParsed("2021-03-07 14:25:13", "3 days ago");
		assertParsed("2021-02-24 14:25:13", "1 fortnight ago");
		assertParsed("2021-03-19 14:25:13", "+1 week 2 days");
		assertParsed("2021-04-09 14:25:13", "+1 month -1 day");
		assertParsed("2021-03-10 15:25:13", "+1 hour");
		assertParsed("2021-03-10 12:55:13", "-90 minutes");
		assertParsed("2021-03-10 14:25:43", "+30 sec");
		assertParsed("2021-04-10 14:25:13", "next month");
		assertParsed("2020-03-10 14:25:13", "last year");
		assertParsed("2022-03-10 14:25:13", "next year");
		assertParsed("2021-03-03 00:00:00", "2021-01-31 +1 month");
	}

	@Test
	public void testWeekdays()
	{
		assertParsed("2021-03-15 00:00:00", "monday");
		assertParsed("2021-03-10 00:00:00", "wednesday");
		assertParsed("2021-03-15 00:00:00", "next monday");
		assertParsed("2021-03-08 00:00:00", "last monday");
		assertParsed("2021-03-15 00:00:00", "this monday");
		assertParsed("2021-03-17 00:00:00", "next wednesday");
		assertParsed("2021-03-03 00:00:00", "last wednesday");
		assertParsed("2021-03-11 13:05:00", "thursday 13:05");
		assertParsed("2021-03-11 00:00:00", "13:05 thursday");
	}

	@Test
	public void testWeeks()
	{
		assertParsed("2021-03-15 14:25:13", "next week");
		assertParsed("2021-03-08 14:25:13", "this week");
		assertParsed("2021-03-01 14:25:13", "last week");
		assertParsed("2021-03-14 00:00:00", "sunday this week");
		assertParsed("2021-03-15 00:00:00", "monday next week");
	}

	@Test
	public void testFirstAndLastDayOf()
	{
		assertParsed("2021-04-01 14:25:13", "first day of next month");
		assertParsed("2021-04-30 14:25:13", "last day of next month");
		assertParsed("2021-02-28 00:00:00", "last day of february");
		assertParsed("2021-03-01 14:25:13", "first day of");
		assertParsed("2021-03-08 00:00:00", "second monday of march 2021");
		assertParsed("2021-04-30 00:00:00", "last friday of next month");
	}

	@Test
	public void testTimeZones()
	{
		assertParsed("2021-03-04 11:05:00", "2021-03-04 13:05 +0200");
		assertParsed("2021-03-04 11:05:00", "2021-03-04 13:05 +02:00");
		assertParsed("2021-03-04 15:05:00", "2021-03-04 13:05 -2");
		assertParsed("2021-03-04 12:05:00", "2021-03-04T13:05:00+01:00");
		assertParsed("2021-03-04 12:05:00", "2021-03-04 13:05 CET");
		assertParsed("2021-03-04 12:05:00", "2021-03-04 13:05 Europe/Berlin");
		assertParsed("2021-07-04 11:05:00", "2021-07-04 13:05 Europe/Berlin");
		assertParsed("2021-03-04 13:05:00", "2021-03-04 13:05Z");
		assertParsed("2021-03-04 13:05:00", "2021-03-04 13:05 UTC");
		assertParsed("2021-03-04 13:05:00", "2021-03-04 13:05 GMT+0000");

		ZonedDateTime berlin = conv.parse("2021-03-04 13:05 Europe/Berlin");
		assertEquals(ZoneId.of("Europe/Berlin"), berlin.getZone());
	}

	@Test
	public void testDefaultTimeZoneIsUsedForDatesWithoutTimeZone()
	{
		StringToDateTimeConverter berlin =
				new StringToDateTimeConverter(ZoneId.of("Europe/Berlin"), NOW);
		assertEquals(
				"2021-03-04 12:05:00",
				FORMAT.format(berlin.parse("2021-03-04 13:05").withZoneSameInstant(ZoneOffset.UTC)));
	}

	@Test
	public void testUnsupportedInputIsRejected()
	{
		assertInvalid("foo");
		assertInvalid("next");
		assertInvalid("2021-13-01");
		assertInvalid("2021-03-04 25:00");
		assertInvalid("2021-03-04 2021-03-05");
		assertInvalid("13:05 14:00");
		assertInvalid("2021-03-04 13:05 Foo/Bar");
		assertInvalid("2021-03-04 13:05 +01:00 +02:00");
		assertInvalid(null);
	}

	@Test
	public void testEmptyInputIsNow()
	{
		assertParsed("2021-03-10 14:25:13", "");
		assertParsed("2021-03-10 14:25:13", " ");
	}

	@Test
	public void testConvertString()
	{
		Date date = conv.convertString("2021-03-04 13:05");
		assertEquals(Instant.parse("2021-03-04T13:05:00Z"), date.toInstant());

		assertNull(conv.convertString("foo"));

		final Date custom = new Date(0);
		conv.registerDateTimeMatcher(input -> input.equals("foo") ? custom : null);
		assertEquals(custom, conv.convertString("foo"));
	}

	@Test
	public void testCalendarConstructorUsesTimeAndTimeZoneOfCalendar()
	{
		Calendar cal = new GregorianCalendar(TimeZone.getTimeZone("Europe/Berlin"));
		cal.setTimeInMillis(NOW.toEpochMilli());

		StringToDateTimeConverter c = new StringToDateTimeConverter(cal);
		assertEquals(
				Instant.parse("2021-03-09T23:00:00Z"),
				c.convertString("today").toInstant());
	}

	// =========================================================================

	private void assertParsed(String expected, String input)
	{
		ZonedDateTime result = conv.parse(input);
		assertNotNull("Cannot parse: " + input, result);
		assertEquals(input, expected, FORMAT.format(result.withZoneSameInstant(ZoneOffset.UTC)));
	}

	private void assertInvalid(String input)
	{
		assertNull(String.valueOf(input), conv.parse(input));
	}
}
