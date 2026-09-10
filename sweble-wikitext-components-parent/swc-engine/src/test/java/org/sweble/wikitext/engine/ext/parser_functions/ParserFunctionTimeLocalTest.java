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
import static org.sweble.wikitext.engine.ext.parser_functions.ParserFunctionTimeTest.ALL;
import static org.sweble.wikitext.engine.ext.parser_functions.ParserFunctionTimeTest.assertFormat;
import static org.sweble.wikitext.engine.ext.parser_functions.ParserFunctionTimeTest.date;
import static org.sweble.wikitext.engine.ext.parser_functions.ParserFunctionTimeTest.expand;

import java.time.ZoneId;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Test;

public class ParserFunctionTimeLocalTest
{
	private static final TimeZone BERLIN = TimeZone.getTimeZone("Europe/Berlin");

	// =========================================================================

	/**
	 * The expected values were obtained from PHP 8.3's <code>date()</code>.
	 */
	@Test
	public void testFormatCharactersInOtherTimeZones()
	{
		assertFormat(
				"07|Tue|7|Tuesday|2|2|65|10|March|03|Mar|3|31|0|2017|2017|17|am|AM|1|1|01|01|02|03|"
						+ "2017-03-07T01:02:03+01:00|Tue, 07 Mar 2017 01:02:03 +0100|1488844923|Europe/Berlin|0|+0100|+01:00|CET|3600",
				ALL,
				date(2017, 3, 7, 1, 2, 3, ZoneId.of("Europe/Berlin")));

		// Daylight Saving Time
		assertFormat(
				"07|Fri|7|Friday|5|5|187|27|July|07|Jul|7|31|0|2017|2017|17|am|AM|1|1|01|01|02|03|"
						+ "2017-07-07T01:02:03+02:00|Fri, 07 Jul 2017 01:02:03 +0200|1499382123|Europe/Berlin|1|+0200|+02:00|CEST|7200",
				ALL,
				date(2017, 7, 7, 1, 2, 3, ZoneId.of("Europe/Berlin")));

		assertFormat(
				"07|Tue|7|Tuesday|2|2|65|10|March|03|Mar|3|31|0|2017|2017|17|am|AM|1|1|01|01|02|03|"
						+ "2017-03-07T01:02:03-08:00|Tue, 07 Mar 2017 01:02:03 -0800|1488877323|America/Los_Angeles|0|-0800|-08:00|PST|-28800",
				ALL,
				date(2017, 3, 7, 1, 2, 3, ZoneId.of("America/Los_Angeles")));

		assertFormat(
				"07|Tue|7|Tuesday|2|2|65|10|March|03|Mar|3|31|0|2017|2017|17|am|AM|1|1|01|01|02|03|"
						+ "2017-03-07T01:02:03+09:30|Tue, 07 Mar 2017 01:02:03 +0930|1488814323|Australia/Darwin|0|+0930|+09:30|ACST|34200",
				ALL,
				date(2017, 3, 7, 1, 2, 3, ZoneId.of("Australia/Darwin")));
	}

	@Test
	public void testFormatCalendar()
	{
		Calendar timestamp = new GregorianCalendar(BERLIN);
		timestamp.clear();
		timestamp.set(2017, Calendar.MARCH, 7, 1, 2, 3);

		assertEquals(
				"2017-03-07T01:02:03+01:00 1488844923 Europe/Berlin CET",
				ParserFunctionTime.format("c U e T", timestamp, Locale.ENGLISH));
	}

	// =========================================================================

	@Test
	public void testTimelUsesTimeZoneOfWiki() throws Exception
	{
		// dates without time zone are UTC, like in MediaWiki
		assertEquals("14:05 CET", expand("{{#timel: H:i T|2021-03-04 13:05}}", BERLIN));
		assertEquals("15:05 1", expand("{{#timel: H:i I|2021-07-04 13:05}}", BERLIN));
		assertEquals("1970-01-01T01:00:00+01:00", expand("{{#timel: c|@0}}", BERLIN));
		assertEquals(
				"2021-03-03 19:00 PST",
				expand("{{#timel: Y-m-d H:i T|2021-03-04 03:00}}", TimeZone.getTimeZone("America/Los_Angeles")));
	}

	@Test
	public void testTimelUsesCurrentTimeWithoutDate() throws Exception
	{
		assertEquals(
				"2021-03-10 15:25:13 Europe/Berlin",
				expand("{{#timel: Y-m-d H:i:s e}}", BERLIN));
		assertEquals(
				"2021-03-10 14:25:13 UTC",
				expand("{{#timel: Y-m-d H:i:s e}}", TimeZone.getTimeZone("UTC")));
	}

	@Test
	public void testTimelSupportsRelativeDatesAndLanguage() throws Exception
	{
		assertEquals("2021-03-09", expand("{{#timel: Y-m-d|-1 day}}", BERLIN));
		assertEquals("Freitag", expand("{{#timel: l|2017-11-24|de}}", BERLIN));
	}

	@Test
	public void testTimelInvalidTime() throws Exception
	{
		assertEquals(
				"<strong class=\"error\">Error: Invalid time.</strong>",
				expand("{{#timel: Y|foo}}", BERLIN));
	}
}
