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

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converter class to extract date and time values from Strings.
 *
 * Implements a subset of the date/time formats understood by PHP's
 * <code>DateTime</code> class (see
 * <a href="https://www.php.net/manual/en/datetime.formats.php">Supported Date
 * and Time Formats</a>), which MediaWiki uses to parse the date argument of
 * <code>#time</code>:
 * <ul>
 * <li>ISO 8601 dates and times, with <code>T</code> or a space as separator,
 * and the MediaWiki timestamp format <code>YYYYMMDDHHMMSS</code>;</li>
 * <li>times without a date (<code>13:05</code>, <code>1:30 pm</code>);</li>
 * <li>Unix timestamps (<code>@1512163343</code>);</li>
 * <li>dates with English month names (<code>March 4, 2021</code>,
 * <code>4 Mar 2021</code>), <code>YYYY-MM</code>,
 * <code>YYYY/MM/DD</code>, <code>MM/DD/YYYY</code>,
 * <code>DD.MM.YYYY</code>, ISO week dates;</li>
 * <li>relative expressions (<code>+1 day</code>, <code>-2 weeks</code>,
 * <code>3 days ago</code>, <code>next monday</code>,
 * <code>last day of next month</code>, <code>second monday of march</code>,
 * <code>now</code>, <code>today</code>, <code>tomorrow</code>,
 * <code>yesterday</code>, ...);</li>
 * <li>time zone offsets, abbreviations and identifiers
 * (<code>+02:00</code>, <code>Z</code>, <code>CET</code>,
 * <code>Europe/Berlin</code>).</li>
 * </ul>
 *
 * Like PHP, the input is split into tokens. At each position the longest
 * matching token wins. The tokens set the date, the time, the time zone or
 * relative offsets, which are resolved against the current time afterwards.
 */
public final class StringToDateTimeConverter
{
	private final ArrayList<DateTimeMatcher> matchers = new ArrayList<DateTimeMatcher>();

	/**
	 * Time zone of dates which do not specify a time zone.
	 */
	private final ZoneId defaultZone;

	/**
	 * The current time, which relative dates are based on.
	 */
	private final Instant now;

	// =========================================================================

	/**
	 * Default constructor. Uses UTC as default time zone and the current
	 * system time as the base for relative dates.
	 */
	public StringToDateTimeConverter()
	{
		this(ZoneId.of("UTC"), Instant.now());
	}

	/**
	 * Uses the time zone of the given calendar as default time zone and its
	 * time as the base for relative dates.
	 */
	public StringToDateTimeConverter(Calendar cal)
	{
		this(cal.getTimeZone().toZoneId(), cal.toInstant());
	}

	/**
	 * @param defaultZone
	 *            The time zone of dates which do not specify a time zone.
	 * @param now
	 *            The current time, which relative dates are based on.
	 */
	public StringToDateTimeConverter(ZoneId defaultZone, Instant now)
	{
		this.defaultZone = defaultZone;
		this.now = now;
	}

	// =========================================================================

	/**
	 * Registers a matcher, which is tried if the built-in formats cannot
	 * handle an input string.
	 */
	public void registerDateTimeMatcher(DateTimeMatcher matcher)
	{
		matchers.add(matcher);
	}

	/**
	 * Tries to convert and interpret the input string which contains the time
	 * and date informations using the built-in formats and the registered
	 * DateTimeMatcher.
	 *
	 * @param input The String to convert.
	 * @return A Date object with the extracted date/time value, or null on
	 * error.
	 */
	public Date convertString(String input)
	{
		ZonedDateTime result = parse(input);
		if (result != null)
			return Date.from(result.toInstant());

		for (DateTimeMatcher matcher : matchers)
		{
			Date date = matcher.tryConvert(input);
			if (date != null)
			{
				return date;
			}
		}
		return null;
	}

	/**
	 * Parses the input string using the built-in formats.
	 *
	 * @param input The String to parse.
	 * @return The date and time in the time zone given in the input string
	 * or, if none was given, in the default time zone; or null if the input
	 * cannot be parsed.
	 */
	public ZonedDateTime parse(String input)
	{
		if (input == null)
			return null;

		try
		{
			ParsedTime time = new ParsedTime();
			if (!time.scan(input))
				return null;
			return time.resolve(now.atZone(defaultZone));
		}
		catch (DateTimeException e)
		{
			return null;
		}
		catch (ArithmeticException e)
		{
			return null;
		}
	}

	// =========================================================================

	public static interface DateTimeMatcher
	{
		public Date tryConvert(String input);
	}

	// =========================================================================

	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

	private static final String SP = "[ \\t\\u00A0\\u202F]+";

	private static final String SPO = "[ \\t\\u00A0\\u202F]*";

	private static final String HOUR24 = "(2[0-4]|[01]?[0-9])";

	private static final String HOUR24LZ = "(2[0-4]|[01][0-9])";

	private static final String HOUR12 = "(1[0-2]|0?[1-9])";

	private static final String MINUTE = "([0-5]?[0-9])";

	private static final String MINUTELZ = "([0-5][0-9])";

	private static final String SECOND = "(60|[0-5]?[0-9])";

	private static final String SECONDLZ = "(60|[0-5][0-9])";

	private static final String FRAC = "(?:\\.[0-9]+)";

	private static final String MERIDIAN = "([ap])\\.?m\\.?(?![a-z])";

	private static final String MONTH = "(1[0-2]|0?[0-9])";

	private static final String MONTHLZ = "(0[0-9]|1[0-2])";

	private static final String DAY = "(3[01]|[0-2]?[0-9])(?:st|nd|rd|th)?";

	private static final String DAYLZ = "(0[0-9]|[12][0-9]|3[01])";

	private static final String YEAR = "([0-9]{1,4})";

	private static final String YEAR2 = "([0-9]{2})";

	private static final String YEAR4 = "([0-9]{4})";

	private static final String MONTHTEXT = "(january|february|march|april|may|june|july|august|september|october|november|december|sept|jan|feb|mar|apr|jun|jul|aug|sep|oct|nov|dec)(?![a-z])";

	private static final String DAYTEXT = "(sunday|monday|tuesday|wednesday|thursday|friday|saturday|sun|mon|tue|wed|thu|fri|sat)";

	private static final String RELTEXT = "(first|next|second|third|fourth|fifth|sixth|seventh|eighth|eight|ninth|tenth|eleventh|twelfth|last|previous|this)";

	private static final String RELUNIT = "(msecs?|milliseconds?|µsecs?|microseconds?|usecs?|ms|µs|secs?|seconds?|mins?|minutes?|hours?|days?|fortnights?|forthnights?|months?|years?|weeks?|sundays?|mondays?|tuesdays?|wednesdays?|thursdays?|fridays?|saturdays?|sun|mon|tue|wed|thu|fri|sat)(?![a-z])";

	private static final String TIME24 = "t?" + HOUR24 + "[:.]" + MINUTE + "(?:[:.]" + SECOND + FRAC + "?)?";

	private static final Pattern SEPARATOR = Pattern.compile("[ \\t\\u00A0\\u202F,.]+");

	private static final Map<String, Integer> RELTEXT_AMOUNTS = new HashMap<String, Integer>();

	private static final Map<String, ZoneOffset> ZONE_ABBREVIATIONS = new HashMap<String, ZoneOffset>();

	private static final List<Rule> RULES;

	static
	{
		String[] ordinals = { "first", "second", "third", "fourth", "fifth",
				"sixth", "seventh", "eighth", "ninth", "tenth", "eleventh",
				"twelfth" };
		for (int i = 0; i < ordinals.length; ++i)
			RELTEXT_AMOUNTS.put(ordinals[i], i + 1);
		RELTEXT_AMOUNTS.put("eight", 8);
		RELTEXT_AMOUNTS.put("next", 1);
		RELTEXT_AMOUNTS.put("last", -1);
		RELTEXT_AMOUNTS.put("previous", -1);
		RELTEXT_AMOUNTS.put("this", 0);

		addZone(0, "utc", "gmt", "ut", "z", "wet");
		addZone(1, "west", "bst", "cet", "met");
		addZone(2, "cest", "mest", "eet");
		addZone(3, "eest", "msk");
		addZone(-5, "est", "cdt");
		addZone(-4, "edt", "ast");
		addZone(-6, "cst", "mdt");
		addZone(-7, "mst", "pdt");
		addZone(-8, "pst", "akdt");
		addZone(-9, "akst");
		addZone(-10, "hst");
		addZone(8, "awst");
		addZone(9, "jst", "kst");
		addZone(10, "aest");
		addZone(11, "aedt");
		addZone(12, "nzst");
		addZone(13, "nzdt");
		ZONE_ABBREVIATIONS.put("acst", ZoneOffset.ofHoursMinutes(9, 30));
		ZONE_ABBREVIATIONS.put("acdt", ZoneOffset.ofHoursMinutes(10, 30));

		List<Rule> rules = new ArrayList<Rule>();

		// ---- Unix timestamps and keywords

		rules.add(new Rule("@(-?[0-9]{1,18})(?:\\.([0-9]{0,6}))?", (t, m) -> t.timestamp(
				m.group(1),
				m.group(2))));

		rules.add(new Rule("(now|noon|midnight|today|tomorrow|yesterday)(?![a-z])", (t, m) -> t.keyword(
				lower(m.group(1)))));

		// ---- relative expressions

		rules.add(new Rule("(first|last)" + SP + "day" + SP + "of(?![a-z])", (t, m) -> t.firstLastDayOf(
				lower(m.group(1)).equals("first"))));

		rules.add(new Rule(RELTEXT + SP + DAYTEXT + SP + "of(?![a-z])", (t, m) -> t.weekdayOf(
				lower(m.group(1)),
				lower(m.group(2)))));

		rules.add(new Rule("(next|last|previous|this)" + SP + "week(?![a-z])", (t, m) -> t.relativeTextWeek(
				lower(m.group(1)))));

		rules.add(new Rule(RELTEXT + SP + RELUNIT, (t, m) -> t.relativeText(
				lower(m.group(1)),
				lower(m.group(2)))));

		rules.add(new Rule("([+-]*)[ \\t]*([0-9]{1,13})" + SPO + RELUNIT, (t, m) -> t.relative(
				m.group(1),
				Long.parseLong(m.group(2)),
				lower(m.group(3)))));

		rules.add(new Rule("ago(?![a-z])", (t, m) -> t.ago()));

		rules.add(new Rule(DAYTEXT + "(?![a-z])", (t, m) -> t.weekday(
				lower(m.group(1)))));

		// ---- dates

		rules.add(new Rule("([+-]?[0-9]{4})-" + MONTHLZ + "-" + DAYLZ, (t, m) -> t.date(
				Integer.parseInt(m.group(1)),
				num(m, 2),
				num(m, 3))));

		rules.add(new Rule(YEAR2 + "-" + MONTHLZ + "-" + DAYLZ, (t, m) -> t.date(
				year(m, 1),
				num(m, 2),
				num(m, 3))));

		rules.add(new Rule(YEAR4 + "-" + MONTH, (t, m) -> t.date(
				num(m, 1),
				num(m, 2),
				1)));

		rules.add(new Rule(YEAR + "-" + MONTH + "-" + DAY, (t, m) -> t.date(
				year(m, 1),
				num(m, 2),
				num(m, 3))));

		rules.add(new Rule(YEAR4 + "/" + MONTH + "/" + DAY + "/?", (t, m) -> t.date(
				num(m, 1),
				num(m, 2),
				num(m, 3))));

		rules.add(new Rule(MONTH + "/" + DAY + "/" + YEAR, (t, m) -> t.date(
				year(m, 3),
				num(m, 1),
				num(m, 2))));

		rules.add(new Rule(MONTH + "/" + DAY, (t, m) -> t.date(
				null,
				num(m, 1),
				num(m, 2))));

		rules.add(new Rule(DAY + "[.\\t-]" + MONTH + "[.-]" + YEAR4, (t, m) -> t.date(
				num(m, 3),
				num(m, 2),
				num(m, 1))));

		rules.add(new Rule(DAY + "[ \\t.-]*" + MONTHTEXT + "[ \\t.-]*" + YEAR, (t, m) -> t.date(
				year(m, 3),
				month(m, 2),
				num(m, 1))));

		rules.add(new Rule(MONTHTEXT + "[ .\\t-]*" + YEAR4, (t, m) -> t.date(
				num(m, 2),
				month(m, 1),
				1)));

		rules.add(new Rule(YEAR4 + "[ .\\t-]*" + MONTHTEXT, (t, m) -> t.date(
				num(m, 1),
				month(m, 2),
				1)));

		rules.add(new Rule(MONTHTEXT + "[ .\\t-]*" + DAY + "[,.stndrh\\t ]+" + YEAR, (t, m) -> t.date(
				year(m, 3),
				month(m, 1),
				num(m, 2))));

		rules.add(new Rule(MONTHTEXT + "[ .\\t-]*" + DAY + "(?:[,.stndrh\\t ]+|$)", (t, m) -> t.date(
				null,
				month(m, 1),
				num(m, 2))));

		rules.add(new Rule(MONTHTEXT + "[ .\\t-]*" + DAY + "[,.stndrh\\t ]+" + TIME24 + "(?:" + SPO + MERIDIAN + ")?", (t, m) -> t.date(
				null,
				month(m, 1),
				num(m, 2))
				&& t.time(
						meridian(num(m, 3), m.group(6)),
						num(m, 4),
						(m.group(5) != null) ? num(m, 5) : 0)));

		rules.add(new Rule(DAY + "[ .\\t-]*" + MONTHTEXT, (t, m) -> t.date(
				null,
				month(m, 2),
				num(m, 1))));

		rules.add(new Rule(YEAR4 + MONTHLZ + DAYLZ, (t, m) -> t.date(
				num(m, 1),
				num(m, 2),
				num(m, 3))));

		rules.add(new Rule(YEAR4 + "[.-]?(00[1-9]|0[1-9][0-9]|[12][0-9][0-9]|3[0-5][0-9]|36[0-6])", (t, m) -> t.date(
				num(m, 1),
				1,
				num(m, 2))));

		rules.add(new Rule(MONTHTEXT + "-" + DAYLZ + "-" + YEAR, (t, m) -> t.date(
				year(m, 3),
				month(m, 1),
				num(m, 2))));

		rules.add(new Rule(YEAR + "-" + MONTHTEXT + "-" + DAYLZ, (t, m) -> t.date(
				year(m, 1),
				month(m, 2),
				num(m, 3))));

		rules.add(new Rule(YEAR4 + "-?w(0[1-9]|[1-4][0-9]|5[0-3])(?:-?([0-7]))?", (t, m) -> t.isoWeek(
				num(m, 1),
				num(m, 2),
				(m.group(3) != null) ? num(m, 3) : 1)));

		rules.add(new Rule(YEAR4 + ":" + MONTHLZ + ":" + DAYLZ + " " + HOUR24LZ + ":" + MINUTELZ + ":" + SECONDLZ, (t, m) -> t.date(
				num(m, 1),
				num(m, 2),
				num(m, 3))
				&& t.time(num(m, 4), num(m, 5), num(m, 6))));

		rules.add(new Rule(MONTHTEXT, (t, m) -> t.date(
				null,
				month(m, 1),
				null)));

		// ---- times

		rules.add(new Rule(HOUR12 + SPO + MERIDIAN, (t, m) -> t.time(
				meridian(num(m, 1), m.group(2)),
				0,
				0)));

		rules.add(new Rule(HOUR12 + "[:.]" + MINUTELZ + SPO + MERIDIAN, (t, m) -> t.time(
				meridian(num(m, 1), m.group(3)),
				num(m, 2),
				0)));

		rules.add(new Rule(HOUR12 + "[:.]" + MINUTE + "[:.]" + SECONDLZ + SPO + MERIDIAN, (t, m) -> t.time(
				meridian(num(m, 1), m.group(4)),
				num(m, 2),
				num(m, 3))));

		rules.add(new Rule(TIME24, (t, m) -> t.time(
				num(m, 1),
				num(m, 2),
				(m.group(3) != null) ? num(m, 3) : 0)));

		rules.add(new Rule("t?" + HOUR24LZ + MINUTELZ, (t, m) -> t.gnuNoColon(
				num(m, 1),
				num(m, 2),
				Integer.parseInt(m.group(1) + m.group(2)))));

		rules.add(new Rule("t?" + HOUR24LZ + MINUTELZ + SECONDLZ, (t, m) -> t.time(
				num(m, 1),
				num(m, 2),
				num(m, 3))));

		// Four digits which are no valid time (e.g. "1999") set the year only
		rules.add(new Rule(YEAR4, (t, m) -> t.year(
				num(m, 1))));

		// ---- time zones

		rules.add(new Rule("(?:gmt)?([+-])([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?|[0-9]{1,6})(?![0-9])", (t, m) -> t.zone(
				parseZoneCorrection(m.group(1), m.group(2)))));

		rules.add(new Rule("\\(?([a-z]{1,6})\\)?(?![a-z])", (t, m) -> t.zone(
				ZONE_ABBREVIATIONS.get(lower(m.group(1))))));

		rules.add(new Rule("([a-z]+(?:[_/-][a-z]+)+)", (t, m) -> t.zone(
				lookupZoneId(m.group(1)))));

		RULES = Collections.unmodifiableList(rules);
	}

	private static void addZone(int hours, String... names)
	{
		for (String name : names)
			ZONE_ABBREVIATIONS.put(name, ZoneOffset.ofHours(hours));
	}

	private static String lower(String s)
	{
		return s.toLowerCase(Locale.ROOT);
	}

	private static int num(Matcher m, int group)
	{
		return Integer.parseInt(m.group(group).replaceAll("[^0-9]", ""));
	}

	/**
	 * Two-digit years are mapped to 1970-2069, like PHP does.
	 */
	private static int year(Matcher m, int group)
	{
		int year = num(m, group);
		if (m.group(group).length() < 4)
		{
			if (year < 70)
				year += 2000;
			else if (year < 100)
				year += 1900;
		}
		return year;
	}

	private static int month(Matcher m, int group)
	{
		String name = lower(m.group(group)).substring(0, 3);
		String[] months = { "jan", "feb", "mar", "apr", "may", "jun", "jul",
				"aug", "sep", "oct", "nov", "dec" };
		for (int i = 0; i < months.length; ++i)
		{
			if (months[i].equals(name))
				return i + 1;
		}
		throw new IllegalArgumentException(name);
	}

	/**
	 * Converts a 12-hour clock hour to a 24-hour clock hour. Hours without
	 * meridian are returned unchanged.
	 */
	private static int meridian(int hour, String meridian)
	{
		if (meridian == null)
			return hour;
		hour = hour % 12;
		if (lower(meridian).equals("p"))
			hour += 12;
		return hour;
	}

	/**
	 * @return The day of the week with Sunday = 0, or -1 if the name is not a
	 * day of the week.
	 */
	private static int weekdayNumber(String name)
	{
		String[] days = { "sun", "mon", "tue", "wed", "thu", "fri", "sat" };
		for (int i = 0; i < days.length; ++i)
		{
			if (name.startsWith(days[i]))
				return i;
		}
		return -1;
	}

	private static ZoneOffset parseZoneCorrection(String sign, String value)
	{
		int hours;
		int minutes = 0;
		int seconds = 0;
		if (value.indexOf(':') != -1)
		{
			String[] parts = value.split(":");
			hours = Integer.parseInt(parts[0]);
			minutes = Integer.parseInt(parts[1]);
			if (parts.length > 2)
				seconds = Integer.parseInt(parts[2]);
		}
		else
		{
			switch (value.length())
			{
				case 1:
				case 2:
					hours = Integer.parseInt(value);
					break;
				case 3:
				case 4:
					hours = Integer.parseInt(value.substring(0, value.length() - 2));
					minutes = Integer.parseInt(value.substring(value.length() - 2));
					break;
				case 6:
					hours = Integer.parseInt(value.substring(0, 2));
					minutes = Integer.parseInt(value.substring(2, 4));
					seconds = Integer.parseInt(value.substring(4));
					break;
				default:
					return null;
			}
		}

		int total = hours * 3600 + minutes * 60 + seconds;
		if (minutes > 59 || seconds > 59 || total > 18 * 3600)
			return null;
		return ZoneOffset.ofTotalSeconds(sign.equals("-") ? -total : total);
	}

	private static ZoneId lookupZoneId(String name)
	{
		for (String id : ZoneId.getAvailableZoneIds())
		{
			if (id.equalsIgnoreCase(name))
				return ZoneId.of(id);
		}
		return null;
	}

	// =========================================================================

	private static interface RuleAction
	{
		public boolean apply(ParsedTime time, Matcher m);
	}

	private static final class Rule
	{
		private final Pattern pattern;

		private final RuleAction action;

		public Rule(String regex, RuleAction action)
		{
			this.pattern = Pattern.compile(regex, FLAGS);
			this.action = action;
		}
	}

	// =========================================================================

	/**
	 * The result of scanning an input string, modeled after PHP's timelib.
	 * Unset fields are <code>null</code> and are taken from the current time
	 * when the date is resolved.
	 */
	private static final class ParsedTime
	{
		private static final int SPECIAL_DAY_OF_WEEK_IN_MONTH = 1;

		private static final int SPECIAL_LAST_DAY_OF_WEEK_IN_MONTH = 2;

		private Integer y;

		private Integer m;

		private Integer d;

		private Integer h;

		private Integer i;

		private Integer s;

		private boolean haveDate;

		private int haveTime;

		private ZoneId zone;

		private long relY;

		private long relM;

		private long relD;

		private long relH;

		private long relI;

		private long relS;

		private long relUs;

		private boolean haveWeekdayRelative;

		private int weekday;

		private int weekdayBehavior;

		private boolean firstDayOf;

		private boolean lastDayOf;

		private int special;

		// ---- scanning

		public boolean scan(String input)
		{
			int pos = 0;
			int length = input.length();
			Matcher separator = SEPARATOR.matcher(input);
			while (true)
			{
				separator.region(pos, length);
				if (separator.lookingAt())
					pos = separator.end();
				if (pos >= length)
					return true;

				Rule best = null;
				Matcher bestMatch = null;
				for (Rule rule : RULES)
				{
					Matcher m = rule.pattern.matcher(input);
					m.region(pos, length);
					if (m.lookingAt()
							&& m.end() > pos
							&& (bestMatch == null || m.end() > bestMatch.end()))
					{
						best = rule;
						bestMatch = m;
					}
				}

				if (best == null || !best.action.apply(this, bestMatch))
					return false;

				pos = bestMatch.end();
			}
		}

		// ---- actions

		public boolean date(Integer year, Integer month, Integer day)
		{
			if (haveDate)
				return false; // double date specification
			haveDate = true;
			if (year != null)
				y = year;
			if (month != null)
				m = month;
			if (day != null)
				d = day;
			return true;
		}

		public boolean time(int hour, int minute, int second)
		{
			if (haveTime != 0)
				return false; // double time specification
			haveTime = 1;
			h = hour;
			i = minute;
			s = second;
			return true;
		}

		private void unhaveTime()
		{
			haveTime = 0;
			h = 0;
			i = 0;
			s = 0;
		}

		/**
		 * PHP interprets four digits as time (HHMM), unless a time was already
		 * given, in which case they are the year.
		 */
		public boolean gnuNoColon(int hour, int minute, int year)
		{
			switch (haveTime)
			{
				case 0:
					h = hour;
					i = minute;
					s = 0;
					break;
				case 1:
					y = year;
					break;
				default:
					return false;
			}
			++haveTime;
			return true;
		}

		public boolean year(int year)
		{
			y = year;
			return true;
		}

		public boolean isoWeek(int year, int week, int dayOfWeek)
		{
			LocalDate date = LocalDate.of(year, 1, 4)
					.with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
					.with(DayOfWeek.MONDAY)
					.plusDays(dayOfWeek - 1);
			return date(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
		}

		public boolean zone(ZoneId z)
		{
			if (z == null || zone != null)
				return false; // unknown zone or double time zone specification
			zone = z;
			return true;
		}

		public boolean timestamp(String seconds, String fraction)
		{
			if (!zone(ZoneOffset.UTC))
				return false;
			haveDate = false;
			unhaveTime();
			y = 1970;
			m = 1;
			d = 1;
			relS += Long.parseLong(seconds);
			if (fraction != null && !fraction.isEmpty())
			{
				long us = Long.parseLong((fraction + "00000").substring(0, 6));
				relUs += seconds.startsWith("-") ? -us : us;
			}
			return true;
		}

		public boolean keyword(String keyword)
		{
			switch (keyword)
			{
				case "now":
					break;
				case "noon":
					unhaveTime();
					haveTime = 1;
					h = 12;
					break;
				case "midnight":
				case "today":
					unhaveTime();
					break;
				case "tomorrow":
					unhaveTime();
					relD = 1;
					break;
				case "yesterday":
					unhaveTime();
					relD = -1;
					break;
				default:
					return false;
			}
			return true;
		}

		public boolean firstLastDayOf(boolean first)
		{
			firstDayOf = first;
			lastDayOf = !first;
			return true;
		}

		public boolean weekdayOf(String relText, String day)
		{
			int amount = RELTEXT_AMOUNTS.get(relText);
			if (amount > 0)
			{
				special = SPECIAL_DAY_OF_WEEK_IN_MONTH;
				return setRelative(amount, 1, day);
			}
			else
			{
				special = SPECIAL_LAST_DAY_OF_WEEK_IN_MONTH;
				return setRelative(amount, relText.equals("this") ? 1 : 0, day);
			}
		}

		public boolean relativeTextWeek(String relText)
		{
			relD += RELTEXT_AMOUNTS.get(relText) * 7L;
			weekdayBehavior = 2;
			if (!haveWeekdayRelative)
			{
				haveWeekdayRelative = true;
				weekday = 1;
			}
			return true;
		}

		public boolean relativeText(String relText, String unit)
		{
			return setRelative(
					RELTEXT_AMOUNTS.get(relText),
					relText.equals("this") ? 1 : 0,
					unit);
		}

		public boolean relative(String signs, long amount, String unit)
		{
			for (int j = 0; j < signs.length(); ++j)
			{
				if (signs.charAt(j) == '-')
					amount = -amount;
			}
			return setRelative(amount, 1, unit);
		}

		public boolean ago()
		{
			relY = -relY;
			relM = -relM;
			relD = -relD;
			relH = -relH;
			relI = -relI;
			relS = -relS;
			relUs = -relUs;
			weekday = -weekday;
			if (weekday == 0)
				weekday = -7;
			return true;
		}

		public boolean weekday(String day)
		{
			haveWeekdayRelative = true;
			unhaveTime();
			weekday = weekdayNumber(day);
			if (weekdayBehavior != 2)
				weekdayBehavior = 1;
			return true;
		}

		private boolean setRelative(long amount, int behavior, String unit)
		{
			if (unit.equals("ms") || unit.startsWith("msec") || unit.startsWith("millisecond"))
				relUs += amount * 1000;
			else if (unit.equals("µs") || unit.startsWith("µsec") || unit.startsWith("usec") || unit.startsWith("microsecond"))
				relUs += amount;
			else if (unit.startsWith("sec"))
				relS += amount;
			else if (unit.startsWith("min"))
				relI += amount;
			else if (unit.startsWith("hour"))
				relH += amount;
			else if (unit.equals("day") || unit.equals("days"))
				relD += amount;
			else if (unit.startsWith("fortnight") || unit.startsWith("forthnight"))
				relD += amount * 14;
			else if (unit.startsWith("month"))
				relM += amount;
			else if (unit.startsWith("year"))
				relY += amount;
			else if (unit.startsWith("week"))
				relD += amount * 7;
			else
			{
				int day = weekdayNumber(unit);
				if (day == -1)
					return false;
				haveWeekdayRelative = true;
				unhaveTime();
				relD += (amount > 0 ? amount - 1 : amount) * 7;
				weekday = day;
				weekdayBehavior = behavior;
			}
			return true;
		}

		// ---- resolving

		/**
		 * Fills unset fields from the current time and applies the relative
		 * offsets like PHP's <code>timelib_fill_holes()</code> and
		 * <code>timelib_update_ts()</code>.
		 */
		public ZonedDateTime resolve(ZonedDateTime now)
		{
			if (haveDate && haveTime == 0)
			{
				h = 0;
				i = 0;
				s = 0;
			}

			long year = (y != null) ? y : now.getYear();
			long month = (m != null) ? m : now.getMonthValue();
			long day = (d != null) ? d : now.getDayOfMonth();
			long hour = (h != null) ? h : now.getHour();
			long minute = (i != null) ? i : now.getMinute();
			long second = (s != null) ? s : now.getSecond();

			if (special == SPECIAL_DAY_OF_WEEK_IN_MONTH)
			{
				day = 1;
				month += relM;
				relM = 0;
			}
			else if (special == SPECIAL_LAST_DAY_OF_WEEK_IN_MONTH)
			{
				day = 1;
				month += relM + 1;
				relM = 0;
			}

			LocalDateTime base = normalize(year, month, day, hour, minute, second);

			if (haveWeekdayRelative)
				base = base.plusDays(adjustForWeekday(base));

			year = base.getYear() + relY;
			month = base.getMonthValue() + relM;
			day = base.getDayOfMonth() + relD;
			if (firstDayOf)
			{
				day = 1;
			}
			else if (lastDayOf)
			{
				day = 0;
				++month;
			}

			LocalDateTime result = normalize(
					year,
					month,
					day,
					base.getHour() + relH,
					base.getMinute() + relI,
					base.getSecond() + relS);
			result = result.plusNanos(relUs * 1000);

			return ZonedDateTime.ofLocal(result, (zone != null) ? zone : now.getZone(), null);
		}

		private long adjustForWeekday(LocalDateTime base)
		{
			int currentDow = base.getDayOfWeek().getValue() % 7;
			if (weekdayBehavior == 2)
			{
				int wd = weekday;
				// To make "this week" work, where the current day is a Sunday
				if (currentDow == 0 && wd != 0)
					wd -= 7;
				// To make "sunday this week" work, where the current day is not
				// a Sunday
				if (wd == 0 && currentDow != 0)
					wd = 7;
				return wd - currentDow;
			}

			int difference = weekday - currentDow;
			if ((relD < 0 && difference < 0)
					|| (relD >= 0 && difference <= -weekdayBehavior))
				difference += 7;

			if (weekday >= 0)
				return difference;
			else
				return -(7 - (Math.abs(weekday) - currentDow));
		}

		/**
		 * Builds a date from fields which may exceed their range. Overflowing
		 * fields are carried over into the next larger field, e.g. February 30
		 * becomes March 2 (or 1).
		 */
		private static LocalDateTime normalize(
				long year,
				long month,
				long day,
				long hour,
				long minute,
				long second)
		{
			return LocalDate.of(0, 1, 1)
					.plusYears(year)
					.plusMonths(month - 1)
					.plusDays(day - 1)
					.atStartOfDay()
					.plusHours(hour)
					.plusMinutes(minute)
					.plusSeconds(second);
		}
	}
}
