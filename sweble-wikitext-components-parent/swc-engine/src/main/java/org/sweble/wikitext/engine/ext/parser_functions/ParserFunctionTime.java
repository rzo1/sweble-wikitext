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

import java.time.Instant;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngSoftErrorNode;
import org.sweble.wikitext.engine.nodes.EngineRtData;
import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.utils.StringConversionException;
import org.sweble.wikitext.engine.utils.StringToDateTimeConverter;

import de.fau.cs.osr.utils.StringTools;

/**
 * Parser function for the <code>#time</code> template. This template works only
 * on the time base of UTC+0 (GMT). For functions depending on the local
 * time of the client, or a predefined time zone, see <code>#timel</code>.
 *
 * The current time is taken from {@link WikiConfig#getRuntimeInfo()}, the
 * local time zone from {@link WikiConfig#getTimezone()}. Like MediaWiki,
 * dates without time zone are interpreted as UTC.
 *
 * @see ParserFunctionTimeLocal
 * @see StringToDateTimeConverter
 */
public class ParserFunctionTime
		extends
			ParserFunctionsExtPfn
{
	private static final long serialVersionUID = 1L;

	private static final ZoneId UTC = ZoneId.of("UTC");

	private static final String INVALID_TIME = "Error: Invalid time.";

	private static final String YEAR_TOO_SMALL = "Error: #time only supports years from 0.";

	private static final String YEAR_TOO_BIG = "Error: #time only supports years up to 9999.";

	private static Set<String> availableLanguages;

	/**
	 * For un-marshaling only.
	 */
	public ParserFunctionTime()
	{
		super("time");
	}

	public ParserFunctionTime(WikiConfig wikiConfig)
	{
		super(wikiConfig, "time");
	}

	/**
	 * For un-marshaling only.
	 */
	protected ParserFunctionTime(String name)
	{
		super(name);
	}

	protected ParserFunctionTime(WikiConfig wikiConfig, String name)
	{
		super(wikiConfig, name);
	}

	/**
	 * @return Whether the result is given in the local time zone of the wiki
	 * instead of UTC.
	 */
	protected boolean isLocal()
	{
		return false;
	}

	/**
	 * Formats the current time of the wiki like <code>#time</code> does. Used
	 * by the date and time variables like <code>{{CURRENTYEAR}}</code>. Names
	 * of months and days are given in the content language of the wiki.
	 *
	 * @param wikiConfig
	 *            The configuration of the wiki, which provides the current
	 *            time, the local time zone and the content language.
	 * @param format
	 *            The format string, see
	 *            {@link #format(String, ZonedDateTime, Locale)}.
	 * @param local
	 *            Whether the local time zone of the wiki is used instead of
	 *            UTC.
	 * @return The formated current time.
	 */
	public static String formatNow(
			WikiConfig wikiConfig,
			String format,
			boolean local)
	{
		Locale locale = getLocale(wikiConfig.getContentLanguage());
		if (locale == null)
			locale = Locale.ENGLISH;

		ZoneId zone = local ? wikiConfig.getTimezone().toZoneId() : UTC;

		Instant now = wikiConfig.getRuntimeInfo().getDateAndTime().toInstant();

		return format(format, now.atZone(zone), locale);
	}

	@Override
	public WtNode invoke(
			WtTemplate pfn,
			ExpansionFrame frame,
			List<? extends WtNode> args)
	{
		if (args.size() < 1)
			return pfn;

		// ---- format

		String format = expandArgToString(frame, args, 0);
		if (format == null)
			return error("Cannot convert format argument to string!");

		// ---- language

		String languageTag = null;
		if (args.size() >= 3)
		{
			languageTag = expandArgToString(frame, args, 2);
			if (languageTag == null)
				return error("Cannot convert language argument to string!");
		}

		Locale locale = getLocale(languageTag);
		if (locale == null)
			locale = getLocale(getWikiConfig().getContentLanguage());
		if (locale == null)
			locale = Locale.ENGLISH;

		// ---- local (only #time has this argument)

		boolean local = isLocal();
		if (!local && args.size() >= 4)
		{
			String localStr = expandArgToString(frame, args, 3);
			if (localStr == null)
				return error("Cannot convert local argument to string!");
			local = !localStr.isEmpty() && !localStr.equals("0");
		}

		ZoneId zone = local ? getWikiConfig().getTimezone().toZoneId() : UTC;

		// ---- timestamp

		Instant now = getWikiConfig().getRuntimeInfo().getDateAndTime().toInstant();

		ZonedDateTime timestamp = now.atZone(UTC);

		if (args.size() >= 2)
		{
			String timestampStr = expandArgToString(frame, args, 1);
			if (timestampStr == null)
				return error("Cannot convert timestamp argument to string!");

			if (!timestampStr.isEmpty())
			{
				// Like MediaWiki: PHP would interpret 'XXXX' as XX:XX o'clock
				if (timestampStr.matches("[0-9]{4}"))
					timestampStr = "00:00 " + timestampStr;

				StringToDateTimeConverter conv = new StringToDateTimeConverter(UTC, now);
				timestamp = conv.parse(timestampStr);
				if (timestamp == null)
					return timeError(INVALID_TIME);
			}
		}

		timestamp = timestamp.withZoneSameInstant(zone);

		if (timestamp.getYear() < 0)
			return timeError(YEAR_TOO_SMALL);
		if (timestamp.getYear() > 9999)
			return timeError(YEAR_TOO_BIG);

		// ---- let's format ourselves a date...

		return nf().text(format(format, timestamp, locale));
	}

	/**
	 * @return The locale for the given language tag, or null if no locale
	 * data exists for the language.
	 */
	private static Locale getLocale(String languageTag)
	{
		if (languageTag == null || languageTag.isEmpty())
			return null;

		Locale locale = Locale.forLanguageTag(languageTag);
		if (!getAvailableLanguages().contains(locale.getLanguage()))
			return null;

		return locale;
	}

	private static synchronized Set<String> getAvailableLanguages()
	{
		if (availableLanguages == null)
		{
			Set<String> languages = new HashSet<String>();
			for (Locale l : Locale.getAvailableLocales())
			{
				if (!l.getLanguage().isEmpty())
					languages.add(l.getLanguage());
			}
			availableLanguages = languages;
		}
		return availableLanguages;
	}

	/**
	 * Interprets the symbols in the <code>format</code> string and returns the
	 * result with the inserted date/time fields.
	 *
	 * @param format
	 *            The string to interpret.
	 * @param timestamp
	 *            The date/time used to populate the fields.
	 * @param locale
	 *            The locale for i18n.
	 * @return The formated string with substituted symbols.
	 */
	protected static String format(final String format,
			final Calendar timestamp,
			final Locale locale)
	{
		return format(
				format,
				ZonedDateTime.ofInstant(
						timestamp.toInstant(),
						timestamp.getTimeZone().toZoneId()),
				locale);
	}

	/**
	 * Interprets the symbols in the <code>format</code> string and returns the
	 * result with the inserted date/time fields, like MediaWiki's
	 * <code>Language::sprintfDate()</code>.
	 *
	 * Supported are the format characters
	 * <code>dDjlNwzWFmMntLoYyaAgGhHiscrUeIOPTZ</code>, the extensions
	 * <code>xn</code>, <code>xN</code>, <code>xr</code>, <code>xx</code>,
	 * <code>xg</code>, <code>xkY</code> and <code>xoY</code>, backslash
	 * escaping and quoted literals. Digits are not localized.
	 *
	 * @param format
	 *            The string to interpret.
	 * @param timestamp
	 *            The date/time used to populate the fields.
	 * @param locale
	 *            The locale for i18n.
	 * @return The formated string with substituted symbols.
	 * @see <a href="https://www.mediawiki.org/wiki/Help:Extension:ParserFunctions#time">Help:Extension:ParserFunctions#time</a>
	 */
	protected static String format(final String format,
			final ZonedDateTime timestamp,
			final Locale locale)
	{
		StringBuilder sb = new StringBuilder();
		boolean raw = false;
		boolean rawToggle = false;
		boolean roman = false;
		int length = format.length();

		for (int p = 0; p < length; ++p)
		{
			String num = null;

			String code = String.valueOf(format.charAt(p));
			if (code.equals("x") && p < length - 1)
				code += format.charAt(++p);

			if ((code.equals("xi")
					|| code.equals("xj")
					|| code.equals("xk")
					|| code.equals("xm")
					|| code.equals("xo")
					|| code.equals("xt"))
					&& p < length - 1)
				code += format.charAt(++p);

			switch (code)
			{
				case "xx": // literal x
					sb.append('x');
					break;

				case "xn": // do not translate digits of the next number
				case "xh": // hebrew numerals are not supported, use digits
					raw = true;
					break;

				case "xN": // toggle raw digits
					rawToggle = !rawToggle;
					break;

				case "xr": // roman numerals for the next number
					roman = true;
					break;

				case "xg": // genitive month name
					sb.append(getMonthName(timestamp.getMonth(), TextStyle.FULL, locale));
					break;

				case "xkY": // year in the Thai solar calendar
					num = String.valueOf(getThaiYear(timestamp));
					break;

				case "xoY": // year in the Minguo calendar
					num = String.valueOf(timestamp.getYear() - 1911);
					break;

				case "Y": // 4-digit year
					num = pad(timestamp.getYear(), 4);
					break;

				case "y": // 2-digit year
					num = pad(timestamp.getYear() % 100, 2);
					break;

				case "L": // '1' if it's a leap year, '0' if not
					num = timestamp.toLocalDate().isLeapYear() ? "1" : "0";
					break;

				case "o": // ISO 8601 week-numbering year
					num = String.valueOf(timestamp.get(IsoFields.WEEK_BASED_YEAR));
					break;

				case "n": // month index, not zero-padded
					num = String.valueOf(timestamp.getMonthValue());
					break;

				case "m": // month index, zero-padded
					num = pad(timestamp.getMonthValue(), 2);
					break;

				case "M": // abbreviation of the month name
					sb.append(timestamp.getMonth().getDisplayName(TextStyle.SHORT, locale));
					break;

				case "F": // full month name
					sb.append(getMonthName(timestamp.getMonth(), TextStyle.FULL_STANDALONE, locale));
					break;

				case "t": // number of days in the current month
					num = String.valueOf(timestamp.toLocalDate().lengthOfMonth());
					break;

				case "W": // ISO 8601 week number, zero-padded
					num = pad(timestamp.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR), 2);
					break;

				case "j": // day of the month, not zero-padded
					num = String.valueOf(timestamp.getDayOfMonth());
					break;

				case "d": // day of the month, zero-padded
					num = pad(timestamp.getDayOfMonth(), 2);
					break;

				case "z": // day of the year (January 1 = 0)
					num = String.valueOf(timestamp.getDayOfYear() - 1);
					break;

				case "D": // abbreviation for the day of the week
					sb.append(timestamp.getDayOfWeek().getDisplayName(TextStyle.SHORT, locale));
					break;

				case "l": // the full weekday name
					sb.append(timestamp.getDayOfWeek().getDisplayName(TextStyle.FULL, locale));
					break;

				case "N": // ISO 8601 day of the week (Monday = 1, Sunday = 7)
					num = String.valueOf(timestamp.getDayOfWeek().getValue());
					break;

				case "w": // Number of the day of the week (Sunday = 0, Saturday = 6)
					num = String.valueOf(timestamp.getDayOfWeek().getValue() % 7);
					break;

				case "a": // am, pm
					sb.append(timestamp.getHour() < 12 ? "am" : "pm");
					break;

				case "A": // AM, PM
					sb.append(timestamp.getHour() < 12 ? "AM" : "PM");
					break;

				case "g": // hour in 12-hour format, not zero-padded
					num = String.valueOf(get12Hour(timestamp));
					break;

				case "h": // hour in 12-hour format, zero-padded
					num = pad(get12Hour(timestamp), 2);
					break;

				case "G": // hour in 24-hour format, not zero-padded
					num = String.valueOf(timestamp.getHour());
					break;

				case "H": // hour in 24-hour format, zero-padded
					num = pad(timestamp.getHour(), 2);
					break;

				case "i": // minutes past the hour, zero-padded
					num = pad(timestamp.getMinute(), 2);
					break;

				case "s": // seconds past the minute, zero-padded
					num = pad(timestamp.getSecond(), 2);
					break;

				case "U": // Unix time
					num = String.valueOf(timestamp.toEpochSecond());
					break;

				case "e": // time zone identifier
					sb.append(getZoneId(timestamp));
					break;

				case "I": // '1' if Daylight Saving Time is currently used, otherwise '0'
					num = timestamp.getZone().getRules().isDaylightSavings(timestamp.toInstant()) ? "1" : "0";
					break;

				case "O": // difference to Greenwich time (GMT)
					sb.append(getOffset(timestamp, false));
					break;

				case "P": // difference to Greenwich time (GMT), with colon
					sb.append(getOffset(timestamp, true));
					break;

				case "Z": // time zone offset in seconds
					num = String.valueOf(timestamp.getOffset().getTotalSeconds());
					break;

				case "T": // time zone abbreviation
					sb.append(getZoneAbbreviation(timestamp));
					break;

				case "c": // ISO 8601 formatted date, equivalent to Y-m-d"T"H:i:sP
					sb.append(format("Y-m-d\\TH:i:sP", timestamp, Locale.ENGLISH));
					break;

				case "r": // RFC 5322 formatted date, equivalent to D, d M Y H:i:s O
					sb.append(format("D, d M Y H:i:s O", timestamp, Locale.ENGLISH));
					break;

				case "\\": // backslash escaping
					if (p < length - 1)
						sb.append(format.charAt(++p));
					else
						sb.append('\\');
					break;

				case "\"": // quoted literal
					int endQuote = (p < length - 1) ? format.indexOf('"', p + 1) : -1;
					if (endQuote == -1)
					{
						// no terminating quote, assume literal "
						sb.append('"');
					}
					else
					{
						sb.append(format, p + 1, endQuote);
						p = endQuote;
					}
					break;

				default:
					sb.append(format.charAt(p));
					break;
			}

			if (num != null)
			{
				if (rawToggle || raw)
				{
					sb.append(num);
					raw = false;
				}
				else if (roman)
				{
					sb.append(romanNumeral(Integer.parseInt(num)));
					roman = false;
				}
				else
				{
					sb.append(num);
				}
			}
		}

		return sb.toString();
	}

	private static String pad(int value, int digits)
	{
		StringBuilder sb = new StringBuilder(String.valueOf(value));
		while (sb.length() < digits)
			sb.insert(0, '0');
		return sb.toString();
	}

	private static int get12Hour(final ZonedDateTime timestamp)
	{
		int hour = timestamp.getHour() % 12;
		return (hour == 0) ? 12 : hour;
	}

	/**
	 * Returns the month name. The nominative (stand-alone) form is used for
	 * <code>F</code>, the genitive (format) form for <code>xg</code>.
	 */
	private static String getMonthName(Month month, TextStyle style, Locale locale)
	{
		String name = month.getDisplayName(style, locale);
		if (name.matches("[0-9]+"))
			name = month.getDisplayName(TextStyle.FULL, locale);
		return name;
	}

	/**
	 * Like MediaWiki's <code>Language::tsToYear()</code> for the Thai solar
	 * calendar.
	 */
	private static int getThaiYear(final ZonedDateTime timestamp)
	{
		int year = timestamp.getYear();
		int thaiYear = year + 543;
		if (year >= 1912 && year <= 1940 && timestamp.getMonthValue() <= 3)
			--thaiYear;
		return thaiYear;
	}

	/**
	 * Like MediaWiki's <code>Language::romanNumeral()</code>.
	 */
	protected static String romanNumeral(int num)
	{
		final String[][] table = {
				{ "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" },
				{ "", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC", "C" },
				{ "", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM", "M" },
				{ "", "M", "MM", "MMM", "MMMM", "MMMMM", "MMMMMM", "MMMMMMM",
						"MMMMMMMM", "MMMMMMMMM", "MMMMMMMMMM" } };

		if (num > 10000 || num <= 0)
			return String.valueOf(num);

		StringBuilder sb = new StringBuilder();
		for (int pow10 = 1000, i = 3; i >= 0; pow10 /= 10, --i)
		{
			if (num >= pow10)
				sb.append(table[i][num / pow10]);
			num %= pow10;
		}
		return sb.toString();
	}

	private static String getZoneId(final ZonedDateTime timestamp)
	{
		ZoneId zone = timestamp.getZone();
		if (zone instanceof ZoneOffset)
			return getOffset(timestamp, true);
		return zone.getId();
	}

	/**
	 * Returns the abbreviation of the time zone (e.g. "CET" or "CEST"). If
	 * there is none, the offset is returned like PHP does (e.g. "+03").
	 */
	private static String getZoneAbbreviation(final ZonedDateTime timestamp)
	{
		if (timestamp.getZone() instanceof ZoneOffset)
			return "GMT" + getOffset(timestamp, false);

		String name = DateTimeFormatter.ofPattern("zzz", Locale.ENGLISH).format(timestamp);
		if (name.startsWith("GMT+") || name.startsWith("GMT-"))
		{
			name = getOffset(timestamp, false);
			if (name.endsWith("00"))
				name = name.substring(0, 3);
		}
		return name;
	}

	/**
	 * Gathers the local offset from the given timestamp and returns it as a
	 * formated string according to the RFC 5322 specification. The offset may
	 * also contain the Daylight Saving Time in dependence of the corresponding
	 * time zone.
	 *
	 * @param timestamp The timestamp containing the local time offset.
	 * @param withColon Whether hours and minutes are separated by a colon.
	 * @return A string in the form of "+/-hhmm" (e.g. "+0100") or "+/-hh:mm"
	 * (e.g. "+01:00").
	 */
	private static String getOffset(final ZonedDateTime timestamp, boolean withColon)
	{
		int offset = timestamp.getOffset().getTotalSeconds() / 60; // in minutes
		StringBuilder sb = new StringBuilder();
		sb.append(offset < 0 ? '-' : '+');
		offset = Math.abs(offset);
		sb.append(pad(offset / 60, 2));
		if (withColon)
			sb.append(':');
		sb.append(pad(offset % 60, 2));
		return sb.toString();
	}

	private String expandArgToString(
			ExpansionFrame preprocessorFrame,
			List<? extends WtNode> args,
			final int index)
	{
		WtNode arg = preprocessorFrame.expand(args.get(index));

		tu().trim(arg);

		String format = null;
		try
		{
			format = tu().astToText(arg).trim();
		}
		catch (StringConversionException e1)
		{
			fileInvalidNameWarning(preprocessorFrame, WarningSeverity.NORMAL, arg);
		}
		return format;
	}

	private EngSoftErrorNode error(String msg)
	{
		return EngineRtData.set(nf().softError(
				EngineRtData.set(nf().nowiki(StringTools.escHtml(msg)))));
	}

	/**
	 * Returns an error like MediaWiki does, which is rendered as
	 * <code>&lt;strong class="error"&gt;msg&lt;/strong&gt;</code>.
	 */
	private EngSoftErrorNode timeError(String msg)
	{
		return EngineRtData.set(nf().softError(msg));
	}
}
