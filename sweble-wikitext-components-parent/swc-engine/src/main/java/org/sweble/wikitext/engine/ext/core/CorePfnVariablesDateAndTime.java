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

import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.config.ParserFunctionGroup;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.ext.parser_functions.ParserFunctionTime;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtTemplate;

/**
 * The date and time variables, like MediaWiki's
 * <code>CoreMagicVariables</code>.
 *
 * The current time is taken from {@link WikiConfig#getRuntimeInfo()}. The
 * <code>CURRENT*</code> variables use UTC, the <code>LOCAL*</code> variables
 * the time zone of the wiki ({@link WikiConfig#getTimezone()}). Names of
 * months and days are given in the content language of the wiki.
 */
public class CorePfnVariablesDateAndTime
		extends
			ParserFunctionGroup
{
	private static final long serialVersionUID = 1L;

	// =========================================================================

	protected CorePfnVariablesDateAndTime(WikiConfig wikiConfig)
	{
		super("Core - Variables - Date and Time");
		addParserFunction(new CurrentYearPfn(wikiConfig));
		addParserFunction(new CurrentMonthPfn(wikiConfig));
		addParserFunction(new CurrentMonth1Pfn(wikiConfig));
		addParserFunction(new CurrentMonthNamePfn(wikiConfig));
		addParserFunction(new CurrentMonthNameGenPfn(wikiConfig));
		addParserFunction(new CurrentMonthAbbrevPfn(wikiConfig));
		addParserFunction(new CurrentDayPfn(wikiConfig));
		addParserFunction(new CurrentDay2Pfn(wikiConfig));
		addParserFunction(new CurrentDowPfn(wikiConfig));
		addParserFunction(new CurrentDayNamePfn(wikiConfig));
		addParserFunction(new CurrentTimePfn(wikiConfig));
		addParserFunction(new CurrentHourPfn(wikiConfig));
		addParserFunction(new CurrentWeekPfn(wikiConfig));
		addParserFunction(new CurrentTimestampPfn(wikiConfig));
		addParserFunction(new LocalYearPfn(wikiConfig));
		addParserFunction(new LocalMonthPfn(wikiConfig));
		addParserFunction(new LocalMonth1Pfn(wikiConfig));
		addParserFunction(new LocalMonthNamePfn(wikiConfig));
		addParserFunction(new LocalMonthNameGenPfn(wikiConfig));
		addParserFunction(new LocalMonthAbbrevPfn(wikiConfig));
		addParserFunction(new LocalDayPfn(wikiConfig));
		addParserFunction(new LocalDay2Pfn(wikiConfig));
		addParserFunction(new LocalDowPfn(wikiConfig));
		addParserFunction(new LocalDayNamePfn(wikiConfig));
		addParserFunction(new LocalTimePfn(wikiConfig));
		addParserFunction(new LocalHourPfn(wikiConfig));
		addParserFunction(new LocalWeekPfn(wikiConfig));
		addParserFunction(new LocalTimestampPfn(wikiConfig));
	}

	public static CorePfnVariablesDateAndTime group(WikiConfig wikiConfig)
	{
		return new CorePfnVariablesDateAndTime(wikiConfig);
	}

	// =========================================================================

	/**
	 * A variable that formats the current time of the wiki like
	 * <code>#time</code> does.
	 *
	 * @see ParserFunctionTime#formatNow(WikiConfig, String, boolean)
	 */
	public static abstract class DateAndTimeVariable
			extends
				CorePfnVariable
	{
		private static final long serialVersionUID = 1L;

		private final String format;

		private final boolean local;

		/**
		 * For un-marshaling only.
		 */
		protected DateAndTimeVariable(String name, String format, boolean local)
		{
			super(name);
			this.format = format;
			this.local = local;
		}

		protected DateAndTimeVariable(
				WikiConfig wikiConfig,
				String name,
				String format,
				boolean local)
		{
			super(wikiConfig, name);
			this.format = format;
			this.local = local;
		}

		@Override
		protected final WtNode invoke(WtTemplate var, ExpansionFrame frame)
		{
			String value = ParserFunctionTime.formatNow(getWikiConfig(), format, local);
			return nf().text(postprocess(value));
		}

		/**
		 * @return The value of the variable for the formated current time.
		 */
		protected String postprocess(String value)
		{
			return value;
		}
	}

	// =========================================================================
	// ==
	// == {{CURRENTYEAR}}, {{LOCALYEAR}}
	// ==
	// =========================================================================

	public static final class CurrentYearPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CurrentYearPfn()
		{
			super("currentyear", "Y", false);
		}

		public CurrentYearPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "currentyear", "Y", false);
		}
	}

	public static final class LocalYearPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalYearPfn()
		{
			super("localyear", "Y", true);
		}

		public LocalYearPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localyear", "Y", true);
		}
	}

	// =========================================================================
	// ==
	// == {{CURRENTMONTH}}, {{CURRENTMONTH2}}, {{LOCALMONTH}}, {{LOCALMONTH2}}
	// ==
	// =========================================================================

	public static final class CurrentMonthPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CurrentMonthPfn()
		{
			super("currentmonth", "m", false);
		}

		public CurrentMonthPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "currentmonth", "m", false);
		}
	}

	public static final class LocalMonthPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalMonthPfn()
		{
			super("localmonth", "m", true);
		}

		public LocalMonthPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localmonth", "m", true);
		}
	}

	// =========================================================================
	// ==
	// == {{CURRENTMONTH1}}, {{LOCALMONTH1}}
	// ==
	// =========================================================================

	public static final class CurrentMonth1Pfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CurrentMonth1Pfn()
		{
			super("currentmonth1", "n", false);
		}

		public CurrentMonth1Pfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "currentmonth1", "n", false);
		}
	}

	public static final class LocalMonth1Pfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalMonth1Pfn()
		{
			super("localmonth1", "n", true);
		}

		public LocalMonth1Pfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localmonth1", "n", true);
		}
	}

	// =========================================================================
	// ==
	// == {{CURRENTMONTHNAME}}, {{LOCALMONTHNAME}}
	// ==
	// =========================================================================

	public static final class CurrentMonthNamePfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CurrentMonthNamePfn()
		{
			super("currentmonthname", "F", false);
		}

		public CurrentMonthNamePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "currentmonthname", "F", false);
		}
	}

	public static final class LocalMonthNamePfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalMonthNamePfn()
		{
			super("localmonthname", "F", true);
		}

		public LocalMonthNamePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localmonthname", "F", true);
		}
	}

	// =========================================================================
	// ==
	// == {{CURRENTMONTHNAMEGEN}}, {{LOCALMONTHNAMEGEN}}
	// ==
	// =========================================================================

	public static final class CurrentMonthNameGenPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CurrentMonthNameGenPfn()
		{
			super("currentmonthnamegen", "xg", false);
		}

		public CurrentMonthNameGenPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "currentmonthnamegen", "xg", false);
		}
	}

	public static final class LocalMonthNameGenPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalMonthNameGenPfn()
		{
			super("localmonthnamegen", "xg", true);
		}

		public LocalMonthNameGenPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localmonthnamegen", "xg", true);
		}
	}

	// =========================================================================
	// ==
	// == {{CURRENTMONTHABBREV}}, {{LOCALMONTHABBREV}}
	// ==
	// =========================================================================

	public static final class CurrentMonthAbbrevPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CurrentMonthAbbrevPfn()
		{
			super("currentmonthabbrev", "M", false);
		}

		public CurrentMonthAbbrevPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "currentmonthabbrev", "M", false);
		}
	}

	public static final class LocalMonthAbbrevPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalMonthAbbrevPfn()
		{
			super("localmonthabbrev", "M", true);
		}

		public LocalMonthAbbrevPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localmonthabbrev", "M", true);
		}
	}

	// =========================================================================
	// ==
	// == {{CURRENTDAY}}, {{LOCALDAY}}
	// ==
	// =========================================================================

	public static final class CurrentDayPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CurrentDayPfn()
		{
			super("currentday", "j", false);
		}

		public CurrentDayPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "currentday", "j", false);
		}
	}

	public static final class LocalDayPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalDayPfn()
		{
			super("localday", "j", true);
		}

		public LocalDayPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localday", "j", true);
		}
	}

	// =========================================================================
	// ==
	// == {{CURRENTDAY2}}, {{LOCALDAY2}}
	// ==
	// =========================================================================

	public static final class CurrentDay2Pfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CurrentDay2Pfn()
		{
			super("currentday2", "d", false);
		}

		public CurrentDay2Pfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "currentday2", "d", false);
		}
	}

	public static final class LocalDay2Pfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalDay2Pfn()
		{
			super("localday2", "d", true);
		}

		public LocalDay2Pfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localday2", "d", true);
		}
	}

	// =========================================================================
	// ==
	// == {{CURRENTDOW}}, {{LOCALDOW}}
	// ==
	// =========================================================================

	public static final class CurrentDowPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CurrentDowPfn()
		{
			super("currentdow", "w", false);
		}

		public CurrentDowPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "currentdow", "w", false);
		}
	}

	public static final class LocalDowPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalDowPfn()
		{
			super("localdow", "w", true);
		}

		public LocalDowPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localdow", "w", true);
		}
	}

	// =========================================================================
	// ==
	// == {{CURRENTDAYNAME}}, {{LOCALDAYNAME}}
	// ==
	// =========================================================================

	public static final class CurrentDayNamePfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CurrentDayNamePfn()
		{
			super("currentdayname", "l", false);
		}

		public CurrentDayNamePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "currentdayname", "l", false);
		}
	}

	public static final class LocalDayNamePfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalDayNamePfn()
		{
			super("localdayname", "l", true);
		}

		public LocalDayNamePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localdayname", "l", true);
		}
	}

	// =========================================================================
	// ==
	// == {{CURRENTTIME}}, {{LOCALTIME}}
	// ==
	// =========================================================================

	public static final class CurrentTimePfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CurrentTimePfn()
		{
			super("currenttime", "H:i", false);
		}

		public CurrentTimePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "currenttime", "H:i", false);
		}
	}

	public static final class LocalTimePfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalTimePfn()
		{
			super("localtime", "H:i", true);
		}

		public LocalTimePfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localtime", "H:i", true);
		}
	}

	// =========================================================================
	// ==
	// == {{CURRENTHOUR}}, {{LOCALHOUR}}
	// ==
	// =========================================================================

	public static final class CurrentHourPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CurrentHourPfn()
		{
			super("currenthour", "H", false);
		}

		public CurrentHourPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "currenthour", "H", false);
		}
	}

	public static final class LocalHourPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalHourPfn()
		{
			super("localhour", "H", true);
		}

		public LocalHourPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localhour", "H", true);
		}
	}

	// =========================================================================
	// ==
	// == {{CURRENTWEEK}}, {{LOCALWEEK}}
	// ==
	// =========================================================================

	/**
	 * Like MediaWiki, the ISO 8601 week number is not zero-padded.
	 */
	public static final class CurrentWeekPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CurrentWeekPfn()
		{
			super("currentweek", "W", false);
		}

		public CurrentWeekPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "currentweek", "W", false);
		}

		@Override
		protected String postprocess(String value)
		{
			return String.valueOf(Integer.parseInt(value));
		}
	}

	/**
	 * Like MediaWiki, the ISO 8601 week number is not zero-padded.
	 */
	public static final class LocalWeekPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalWeekPfn()
		{
			super("localweek", "W", true);
		}

		public LocalWeekPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localweek", "W", true);
		}

		@Override
		protected String postprocess(String value)
		{
			return String.valueOf(Integer.parseInt(value));
		}
	}

	// =========================================================================
	// ==
	// == {{CURRENTTIMESTAMP}}, {{LOCALTIMESTAMP}}
	// ==
	// =========================================================================

	public static final class CurrentTimestampPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public CurrentTimestampPfn()
		{
			super("currenttimestamp", "YmdHis", false);
		}

		public CurrentTimestampPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "currenttimestamp", "YmdHis", false);
		}
	}

	public static final class LocalTimestampPfn
			extends
				DateAndTimeVariable
	{
		private static final long serialVersionUID = 1L;

		/**
		 * For un-marshaling only.
		 */
		public LocalTimestampPfn()
		{
			super("localtimestamp", "YmdHis", true);
		}

		public LocalTimestampPfn(WikiConfig wikiConfig)
		{
			super(wikiConfig, "localtimestamp", "YmdHis", true);
		}
	}
}
