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

package org.sweble.wikitext.engine.ext.convert;

import de.fau.cs.osr.utils.StringTools;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.ParserFunctionBase;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.ext.convert.ConvertData.Subdiv;
import org.sweble.wikitext.engine.ext.convert.NumberFormater.FormattedNumber;
import org.sweble.wikitext.engine.ext.convert.NumberFormater.ParsedNumber;
import org.sweble.wikitext.engine.ext.convert.Unit.Key;
import org.sweble.wikitext.engine.ext.convert.Unit.Want;
import org.sweble.wikitext.engine.nodes.EngSoftErrorNode;
import org.sweble.wikitext.engine.nodes.EngineRtData;
import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;
import org.sweble.wikitext.parser.nodes.WtInternalLink;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtNodeList;
import org.sweble.wikitext.parser.utils.StringConversionException;

/**
 * Template which converts a measurement unit into another. (e.g.
 * {{convert|1|m}} -> "1 metre (3 ft 3 in)")
 *
 * The implementation is a port of Module:Convert of the English Wikipedia and
 * uses its unit data (see {@link ConvertData}). The functions keep the names
 * of the module (e.g. {@link #processInput} is process_input()) so the code
 * can be compared with the module.
 *
 * The result differs from the wikitext of Module:Convert in the following
 * ways, which do not change the displayed text:
 * <ul>
 * <li>The "&amp;nbsp;" between a value and a unit symbol, in range texts and
 * in other texts of the module is a plain space; the "&amp;nbsp;" and other
 * entities in unit names and symbols are Unicode characters.</li>
 * <li>Powers of ten and superscript and subscript digits in unit symbols use
 * Unicode characters (like "×10³" and "km²") instead of markup.</li>
 * <li>No TemplateStyles are added for fractions, and no warnings and error
 * categories are added.</li>
 * </ul>
 *
 * Only one instance of this parser function exists per configuration and it
 * is shared by all threads. Therefore all state of an invocation is kept in a
 * {@link Parms} object and in the {@link Unit} objects of the invocation.
 *
 * Not supported are the options "input", "qid" and "qual" (which use Wikidata)
 * and "lang".
 *
 * @see <a href="https://en.wikipedia.org/wiki/Template:Convert/doc">Template:Convert/doc</a>
 * @see <a href="https://en.wikipedia.org/wiki/Help:Convert">Help:Convert</a>
 * @see <a href="https://en.wikipedia.org/wiki/Module:Convert">Module:Convert</a>
 */
public class Convert
		extends
		ParserFunctionBase
{
	private static final int MIN_ARGS = 2;

	private static final String MINUS = NumberFormater.MINUS;

	/** Fudge used by Module:Convert (like {{Order of magnitude}}). */
	private static final double FUDGE = 1e-14;

	/**
	 * Separator between a value and a unit symbol and replacement of the
	 * "&amp;nbsp;" in other texts of Module:Convert. Wikipedia uses
	 * "&amp;nbsp;" which is written as a plain space here.
	 */
	private static final String SYMBOL_SEP = " ";

	private static final String IN = "in";

	private static final String OUT = "out";

	private static final Pattern NUMBER_RX =
			Pattern.compile("\\s*[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?\\s*");

	private static final Pattern CLEAN_RX =
			Pattern.compile("^(\\d*)(\\.?)(\\d*)(.*)$");

	private static final Pattern SPACED_RANGE_RX =
			Pattern.compile("^(\\S+)\\s+(\\S+)\\s+(\\S.*)", Pattern.DOTALL);

	private static final Pattern LINK_RX =
			Pattern.compile("\\[\\[([^\\[\\]|]+)(?:\\|([^\\[\\]]*))?\\]\\]([a-z]*)");

	private static final Pattern HYPHENATE_RX =
			Pattern.compile("([^\\[]*)(\\[\\[[^\\[]*\\]\\])([^\\[]*)");

	private static final Pattern CONDITION_RX =
			Pattern.compile("^\\s*v\\s*([*]?)(.*?)([<>]=?)(.*)$", Pattern.DOTALL);

	private static final Pattern CONDITION_AND_RX =
			Pattern.compile("^(.*?\\W)and(\\W.*)", Pattern.DOTALL);

	private static final Pattern SUP_RX = Pattern.compile("<sup>([0-9−-]+)</sup>");

	private static final Pattern SUB_RX = Pattern.compile("<sub>([0-9]+)</sub>");

	/** Joins of the display options (disp_joins in Module:Convert/text). */
	private static final Map<String, Joins> DISP_JOINS = new HashMap<String, Joins>();

	/** Words which separate the values of a range (e.g. "5 to 10"). */
	private static final Map<String, Range> RANGES = new HashMap<String, Range>();

	/** Range words which are also accepted without spaces (e.g. "5-10"). */
	private static final String[] RANGE_WORDS = { "-", "–", "xx", "x", "*" };

	/**
	 * Speed of sound in miles per hour at an altitude of -15,000 to 400,000
	 * feet in steps of 5,000 feet (for the Mach unit).
	 */
	private static final double[] MACH_TABLE = {
			799.5, 787.0, 774.2, 761.207051,
			748.0, 734.6, 721.0, 707.0, 692.8, 678.3, 663.5, 660.1, 660.1, 660.1,
			660.1, 660.1, 660.1, 662.0, 664.3, 666.5, 668.9, 671.1, 673.4, 675.6,
			677.9, 683.7, 689.9, 696.0, 702.1, 708.1, 714.0, 719.9, 725.8, 731.6,
			737.3, 737.7, 737.7, 736.2, 730.5, 724.6, 718.8, 712.9, 707.0, 701.0,
			695.0, 688.9, 682.8, 676.6, 670.4, 664.1, 657.8, 652.9, 648.3, 643.7,
			639.1, 634.4, 629.6, 624.8, 620.0, 615.2, 613.2, 613.2, 613.2, 613.5,
			614.4, 615.3, 616.7, 619.8, 623.4, 629.7, 635.0, 641.1, 650.6, 660.0,
			672.5, 674.3, 676.1, 677.9, 679.7, 681.5, 683.3, 685.1, 686.8, 688.6 };

	static
	{
		// see https://en.wikipedia.org/wiki/Module:Convert/text
		DISP_JOINS.put("or", new Joins(" or ", "", " or ", true));
		DISP_JOINS.put("sqbr-sp", new Joins(" [", "]", null, false));
		DISP_JOINS.put("sqbr-nbsp", new Joins(" [", "]", null, false));
		DISP_JOINS.put("comma", new Joins(", ", "", ", ", false));
		DISP_JOINS.put("semicolon", new Joins("; ", "", null, false));
		DISP_JOINS.put("b", new Joins(" (", ")", null, false));
		DISP_JOINS.put("(or)", new Joins(" (", ")", " or ", false));
		DISP_JOINS.put("br", new Joins("<br />", "", null, true));
		DISP_JOINS.put("br()", new Joins("<br />(", ")", null, true));

		RANGES.put("+", new Range(" + "));
		RANGES.put(",", new Range(", "));
		RANGES.put(", and", new Range(", and "));
		RANGES.put(", or", new Range(", or "));
		RANGES.put("by", new Range(" by "));
		RANGES.put("-", new Range("–"));
		RANGES.put("to about", new Range(" to about "));
		RANGES.put("and", new Range(" and ", " and ", null, null, null, true, false, false, false));
		RANGES.put("and(-)", new Range(null, null, " and ", "–", null, false, false, false, false));
		RANGES.put("or", new Range(" or ", " or ", null, null, null, true, false, false, false));
		RANGES.put("to", new Range(" to ", " to ", null, null, null, true, false, false, false));
		RANGES.put("to(-)", new Range(null, null, " to ", "–", null, false, false, false, false));
		RANGES.put("+/-", new Range(" ± ", " ± ", null, null, " ± ", false, true, false, false));
		RANGES.put("by(x)", new Range(null, null, " by ", " × ", null, false, false, true, false));
		RANGES.put("x", new Range(" by ", " × ", null, null, null, false, false, false, true));
		RANGES.put("xx", new Range(" × "));
		RANGES.put("*", new Range("×"));
		RANGES.put("/", new Range(" / "));

		RANGES.put("–", RANGES.get("-"));
		RANGES.put("&ndash;", RANGES.get("-"));
		RANGES.put("×", RANGES.get("x"));
		RANGES.put("&times;", RANGES.get("x"));
		RANGES.put("±", RANGES.get("+/-"));
		RANGES.put("&plusmn;", RANGES.get("+/-"));
	}

	public Convert()
	{
		super("convert");
	}

	public Convert(WikiConfig wikiConfig)
	{
		super(wikiConfig, "convert");
	}

	@Override
	public WtNode invoke(
			WtNode pnf,
			ExpansionFrame frame,
			List<? extends WtNode> args)
	{
		if (args.size() < MIN_ARGS)
		{
			return error("Too few arguments!");
		}

		ArrayList<String> strArgs = new ArrayList<String>(args.size());
		for (int i = 0; i < args.size(); i++)
		{
			String tmpStr = expandArgToString(frame, args, i);
			if (tmpStr == null)
			{
				return error("Cannot convert argument to string!");
			}
			strArgs.add(tmpStr);
		}

		try
		{
			return toNodes(convert(strArgs));
		} catch (IllegalArgumentException ex)
		{
			return error(ex.getMessage());
		}
	}

	/**
	 * Expands an argument. Like MediaWiki, the whitespace around a positional
	 * argument is kept (it matters for disp=x and preunits).
	 */
	private String expandArgToString(
			ExpansionFrame preprocessorFrame,
			List<? extends WtNode> args,
			final int index)
	{
		WtNode arg = preprocessorFrame.expand(args.get(index));

		String format = null;
		try
		{
			format = tu().astToText(arg);
		} catch (StringConversionException e1)
		{
			fileInvalidNameWarning(preprocessorFrame, WarningSeverity.NORMAL, arg);
		}
		return format;
	}

	private EngSoftErrorNode error(final String msg)
	{
		return EngineRtData.set(nf().softError(
				EngineRtData.set(nf().nowiki(StringTools.escHtml(msg)))));
	}

	/**
	 * Creates text nodes and internal link nodes from the result.
	 */
	private WtNode toNodes(String wikitext)
	{
		Matcher m = LINK_RX.matcher(wikitext);
		if (!m.find())
		{
			return nf().text(wikitext);
		}

		WtNodeList list = nf().list();
		int pos = 0;
		do
		{
			if (m.start() > pos)
			{
				list.add(nf().text(wikitext.substring(pos, m.start())));
			}
			WtInternalLink link = nf().intLink(
					"",
					nf().pageName(nf().list(nf().text(m.group(1)))),
					m.group(3));
			if (m.group(2) != null)
			{
				link.setTitle(nf().linkTitle(nf().list(nf().text(m.group(2)))));
			}
			list.add(link);
			pos = m.end();
		} while (m.find());

		if (pos < wikitext.length())
		{
			list.add(nf().text(wikitext.substring(pos)));
		}
		return list;
	}

	// =========================================================================

	/**
	 * Converts the given arguments (_main_convert() in Module:Convert).
	 *
	 * @param args The expanded arguments. Named arguments are given as
	 * "name=value".
	 * @return The result as wikitext (which may contain internal links).
	 * @throws IllegalArgumentException If the arguments cannot be converted.
	 */
	static String convert(List<String> args) throws IllegalArgumentException
	{
		final Parms parms = new Parms();
		final List<String> positional = new ArrayList<String>(args.size());
		final Map<String, String> named = new LinkedHashMap<String, String>();
		for (String arg : args)
		{
			int eq = arg.indexOf('=');
			if (eq >= 0)
			{
				named.put(arg.substring(0, eq).trim(), arg.substring(eq + 1).trim());
			} else
			{
				positional.add(arg);
			}
		}
		translateParms(parms, named);

		try
		{
			String first = strip(get(positional, 0));
			if (first != null && first.matches("NNN+"))
			{
				// Some infoboxes have examples like {{convert|NNN|m}}.
				return "";
			}
			Unit inUnit = getParms(parms, positional);
			Unit outUnit = null;
			String result = null;
			for (int i = 0; i < 2; i++)
			{
				Object[] processed = process(parms, inUnit, outUnit);
				result = (String) processed[0];
				outUnit = (Unit) processed[1];
				if (!parms.doConvertAgain)
				{
					break;
				}
				parms.doConvertAgain = false;
			}
			return render(result);
		} catch (IllegalArgumentException ex)
		{
			if (parms.errorText != null)
			{
				return parms.errorText;
			}
			throw ex;
		}
	}

	/**
	 * Replaces the markup in names and symbols of the unit data with Unicode
	 * characters (see the class documentation).
	 */
	private static String render(String wikitext)
	{
		String s = wikitext
				.replace("&nbsp;", " ")
				.replace("&#8209;", "‑")
				.replace("&thinsp;", " ");
		if (s.contains("<sup>"))
		{
			s = replaceAll(SUP_RX, s, true);
		}
		if (s.contains("<sub>"))
		{
			s = replaceAll(SUB_RX, s, false);
		}
		return s;
	}

	private static String replaceAll(Pattern rx, String s, boolean superscript)
	{
		Matcher m = rx.matcher(s);
		StringBuffer sb = new StringBuffer();
		while (m.find())
		{
			String digits = m.group(1).replace(MINUS, "-");
			String replacement = superscript
					? NumberFormater.asSuperscriptNumber(digits)
					: NumberFormater.asSubscriptNumber(digits);
			m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/**
	 * Resolves the named options (translate_parms() in Module:Convert).
	 * Unknown options and invalid or empty values are ignored like
	 * Module:Convert does (it shows a warning in preview only).
	 */
	private static void translateParms(Parms parms, Map<String, String> options)
	{
		if (options.containsKey("adj") && options.containsKey("sing"))
		{
			options.remove("sing"); // old alias of adj
		}
		for (Map.Entry<String, String> e : options.entrySet())
		{
			String value = e.getValue();
			switch (e.getKey())
			{
				case "$":
					if (!value.isEmpty())
					{
						parms.currencyText = value.equals("euro") ? "€" : value;
					}
					break;
				case "abbr":
					translateAbbr(parms, value);
					break;
				case "adj":
				case "sing":
					translateAdj(parms, value);
					break;
				case "altitude_ft":
					parms.altitudeFt = getInteger(value, -1e6);
					break;
				case "altitude_m":
					parms.altitudeM = getInteger(value, -1e6);
					break;
				case "comma":
					if (value.equals("5"))
					{
						parms.options.comma5 = true;
					} else if (value.equals("gaps"))
					{
						parms.options.gaps = true;
					} else if (value.equals("gaps3"))
					{
						parms.options.gaps = true;
						parms.options.gaps3 = true;
					} else if (value.equals("off"))
					{
						parms.options.noComma = true;
					}
					break;
				case "debug":
					if (value.equals("yes"))
					{
						parms.optSortableDebug = true;
					}
					break;
				case "disp":
					translateDisp(parms, value);
					break;
				case "error":
					parms.errorText = value;
					break;
				case "frac":
				{
					Double number = getNumber(value);
					if (number != null && number < 0)
					{
						parms.optFractionHorizontal = true;
						number = -number;
					}
					if (number != null && number == Math.floor(number) && number >= 2)
					{
						parms.frac = (int) Math.min(number, Integer.MAX_VALUE);
					}
					break;
				}
				case "lk":
				case "link":
					if (value.equals("in") || value.equals("off") || value.equals("on") || value.equals("out"))
					{
						parms.lk = value;
					}
					break;
				case "order":
					if (value.equals("flip"))
					{
						parms.optFlip = true;
					} else if (value.equals("out"))
					{
						parms.optOrderOut = true;
					}
					break;
				case "round":
					if (value.equals("each"))
					{
						parms.optRoundEach = true;
					} else if (value.equals("0.5") || value.equals("5") || value.equals("10")
							|| value.equals("25") || value.equals("50"))
					{
						parms.optRound = Double.valueOf(value);
					}
					break;
				case "sigfig":
				{
					Double number = getInteger(value, 1);
					if (number != null)
					{
						parms.sigfig = (int) Math.min(number, Integer.MAX_VALUE);
					}
					break;
				}
				case "sortable":
					if (value.equals("on"))
					{
						parms.optSortableOn = true;
					} else if (value.equals("debug"))
					{
						parms.optSortableOn = true;
						parms.optSortableDebug = true;
					}
					break;
				case "sp":
					if (value.equals("us"))
					{
						parms.optSpUs = true;
					}
					break;
				case "spell":
					if (value.equals("in"))
					{
						parms.optSpellIn = true;
					} else if (value.equals("In"))
					{
						parms.optSpellIn = true;
						parms.optSpellUpper = true;
					} else if (value.equals("on"))
					{
						parms.optSpellIn = true;
						parms.optSpellOut = true;
					} else if (value.equals("On"))
					{
						parms.optSpellIn = true;
						parms.optSpellOut = true;
						parms.optSpellUpper = true;
					} else if (value.equals("us"))
					{
						parms.optSpUs = true;
					}
					break;
				case "stylein":
					parms.stylein = value.isEmpty() ? null : value;
					break;
				case "styleout":
					parms.styleout = value.isEmpty() ? null : value;
					break;
				default:
					// ignored (including "input", "qid", "qual", "lang" and
					// "tracking" which are not supported)
			}
		}

		final String abbrEntered = parms.abbr;
		if (parms.abbr != null)
		{
			if (parms.abbr.equals("unit"))
			{
				parms.abbr = "on";
				parms.numberWord = true;
			}
			parms.abbrOrg = parms.abbr;
		} else if (parms.optHandHh)
		{
			parms.abbrOrg = "on";
			parms.abbr = "on";
		} else
		{
			parms.abbr = OUT; // default is to abbreviate output only
		}
		if (parms.optOrderOut)
		{
			// disable options that do not work in a useful way with order=out
			parms.optFlip = false;
			parms.optSpellIn = false;
			parms.optSpellOut = false;
			parms.optSpellUpper = false;
		}
		if (parms.optSpellOut && abbrEntered == null)
		{
			parms.abbr = "off"; // show unit name when spelling the output value
		}
		if (parms.optFlip)
		{
			parms.abbr = swapInOut(parms.abbr);
			parms.lk = swapInOut(parms.lk);
			if (parms.optSpellIn && !parms.optSpellOut)
			{
				parms.optSpellIn = false;
				parms.optSpellOut = true;
			}
		}
		if (parms.optSpellUpper)
		{
			parms.spellUpper = parms.optFlip ? OUT : IN;
		}
		if (parms.optTable || parms.optTablecen)
		{
			if (abbrEntered == null && parms.lk == null)
			{
				parms.optValues = true;
			}
			parms.tableAlign = parms.optTable ? "right" : "center";
		}
		if (parms.tableAlign != null || parms.optSortableOn)
		{
			parms.needTableOrSort = true;
		}

		final Joins defaultJoins = DISP_JOINS.get("b");
		parms.joinBetween = "; ";
		String disp = parms.disp;
		if (disp == null)
		{
			parms.joins = new String[] { defaultJoins.before, defaultJoins.after };
		} else if (!disp.equals("x"))
		{
			if (disp.equals("sqbr"))
			{
				disp = "on".equals(parms.abbr) ? "sqbr-nbsp" : "sqbr-sp";
			}
			Joins joins = DISP_JOINS.get(disp);
			if (joins == null)
			{
				joins = defaultJoins;
			}
			parms.joins = new String[] { joins.before, joins.after };
			if (joins.between != null)
			{
				parms.joinBetween = joins.between;
			}
			parms.wantname = joins.wantName;
		}
	}

	private static void translateAbbr(Parms parms, String value)
	{
		switch (value)
		{
			case "h":
			case "on":
				parms.abbr = "on";
				break;
			case "hh":
				parms.optHandHh = true;
				break;
			case "in":
			case "off":
			case "out":
			case "unit":
				parms.abbr = value;
				break;
			case "none":
				parms.abbr = "off";
				break;
			case "values":
				parms.optValues = true;
				break;
			case "~":
				parms.optAlsoSymbol = true;
				break;
			default:
				// ignored (including "def")
		}
	}

	private static void translateAdj(Parms parms, String value)
	{
		switch (value)
		{
			case "mid":
				parms.optAdjectival = true;
				parms.optAdjMid = true;
				break;
			case "on":
				parms.optAdjectival = true;
				break;
			case "pre":
				parms.optOnePreunit = true;
				break;
			case "ri0":
			case "ri1":
			case "ri2":
			case "ri3":
				parms.optRi = value.charAt(2) - '0';
				break;
			case "~":
				parms.optAlsoSymbol = true;
				break;
			default:
				// ignored (including "off")
		}
	}

	private static void translateDisp(Parms parms, String value)
	{
		switch (value)
		{
			case "5":
				parms.optRound = 5d;
				break;
			case "b":
			case "(or)":
			case "br":
			case "br()":
			case "comma":
			case "or":
			case "semicolon":
			case "sqbr":
			case "x":
				parms.disp = value;
				break;
			case "flip":
				parms.optFlip = true;
				break;
			case "number":
			case "output number only":
				parms.optOutputNumberOnly = true;
				break;
			case "out":
			case "output only":
				parms.optOutputOnly = true;
				break;
			case "preunit":
				parms.optTwoPreunits = true;
				break;
			case "table":
				parms.optTable = true;
				break;
			case "tablecen":
				parms.optTablecen = true;
				break;
			case "unit":
				parms.optInputUnitOnly = true;
				break;
			case "unit or text":
				parms.optInputUnitOnly = true;
				parms.optIgnoreError = true;
				break;
			case "unit2":
				parms.optOutputUnitOnly = true;
				break;
			default:
				// ignored
		}
	}

	private static String swapInOut(String value)
	{
		if (IN.equals(value))
		{
			return OUT;
		} else if (OUT.equals(value))
		{
			return IN;
		}
		return value;
	}

	/**
	 * @return The value of the text if it is a number (no fraction and no
	 * Unicode minus), otherwise null (get_number() in Module:Convert).
	 */
	private static Double getNumber(String text)
	{
		if (text == null)
		{
			return null;
		}
		String clean = text.replace(",", "");
		if (!NUMBER_RX.matcher(clean).matches())
		{
			return null;
		}
		return Double.parseDouble(clean.trim());
	}

	/**
	 * @return The value of the text if it is an integer which is not less than
	 * the given minimum, otherwise null.
	 */
	private static Double getInteger(String text, double minimum)
	{
		Double number = getNumber(text);
		if (number != null && number == Math.floor(number) && number >= minimum)
		{
			return number;
		}
		return null;
	}

	private static String get(List<String> parms, int index)
	{
		return (index >= 0 && index < parms.size()) ? parms.get(index) : null;
	}

	/**
	 * @return The text without leading and trailing whitespace or null.
	 */
	private static String strip(String text)
	{
		return (text != null) ? text.trim() : null;
	}

	// =========================================================================

	/**
	 * Parses the positional arguments (get_parms() in Module:Convert).
	 *
	 * @return The input unit.
	 */
	private static Unit getParms(Parms parms, List<String> args)
	{
		final List<Info> valinfo = getValues(parms, args);
		int i = parms.nextArg;

		final String inCode = strip(get(args, i));
		i++;
		Unit inUnit;
		try
		{
			inUnit = Unit.lookup(parms, inCode, Want.NO_COMBINATION);
		} catch (ConvertException e)
		{
			if (!parms.optIgnoreError || inCode == null)
			{
				throw e;
			}
			// display given unit code with no error (disp=unit or text)
			inUnit = Unit.unknown(inCode);
			parms.badInput = true;
		}
		inUnit.valinfo = valinfo;
		inUnit.inout = IN;

		if (parms.range == null)
		{
			Object[] composite = getComposite(parms, args, i, inUnit);
			if (composite != null)
			{
				i = (Integer) composite[0];
				inUnit = (Unit) composite[1];
			}
		}

		if ("mach".equals(inUnit.builtin))
		{
			// A number following Mach as the input unit is the altitude.
			String text = get(args, i);
			Double altitude = getNumber(text);
			if (altitude == null && text != null)
			{
				try
				{
					ParsedNumber number = NumberFormater.parseValue(text);
					if (number.getFraction() == null)
					{
						altitude = number.getValue();
					}
				} catch (NumberFormatException e)
				{
					// not an altitude
				}
			}
			if (altitude != null)
			{
				i++;
				inUnit.altitude = altitude;
			}
		}

		String word = strip(get(args, i));
		i++;
		if (word != null && !setPrecision(parms, word))
		{
			parms.outUnit = word;
			if (setPrecision(parms, strip(get(args, i))))
			{
				i++;
			}
		}
		if (parms.optAdjMid)
		{
			word = get(args, i);
			i++;
			if (word != null)
			{
				parms.mid = word.startsWith("-") ? word : " " + word;
			}
		}
		if (parms.optOnePreunit)
		{
			String preunit = preunits(1, get(args, i), null)[0];
			if (parms.optFlip)
			{
				parms.preunit2 = preunit;
			} else
			{
				parms.preunit1 = preunit;
			}
			i++;
		}
		if ("x".equals(parms.disp))
		{
			String first = get(args, i);
			String second = get(args, i + 1);
			first = (first != null) ? first : "";
			second = (second != null) ? second : "";
			i += 2;
			if (first.trim().isEmpty())
			{
				// user can enter "&#32;" rather than " " to avoid the default
				first = " [" + SYMBOL_SEP + first;
				second = SYMBOL_SEP + "]" + second;
			}
			parms.joins = new String[] { first, second };
		} else if (parms.optTwoPreunits)
		{
			String[] p = preunits(2, get(args, i), get(args, i + 1));
			i += 2;
			if (parms.preunit1 != null)
			{
				// allow unlikely use of adj=pre with disp=preunit
				parms.preunit1 = parms.preunit1 + ((p[0] != null) ? p[0] : "");
				parms.preunit2 = p[1];
			} else
			{
				parms.preunit1 = p[0];
				parms.preunit2 = p[1];
			}
		}
		if (!parms.hasPrecision)
		{
			if (setPrecision(parms, strip(get(args, i))))
			{
				i++;
			}
		}
		// additional positional arguments are ignored (Module:Convert shows a
		// warning in preview only)
		return inUnit;
	}

	/**
	 * Sets the precision if the given text is a number.
	 *
	 * @return True if the text was used for the precision (even if the
	 * precision is invalid and ignored).
	 */
	private static boolean setPrecision(Parms parms, String text)
	{
		Double number = getNumber(text);
		if (number == null)
		{
			return false;
		}
		parms.hasPrecision = true;
		if (number == Math.floor(number))
		{
			parms.precision = (int) Math.max(-1000, Math.min(1000, number));
		}
		return true;
	}

	/**
	 * Parses the input values and range words (get_values() in
	 * Module:Convert). Sets parms.nextArg to the index of the input unit.
	 */
	private static List<Info> getValues(Parms parms, List<String> args)
	{
		final List<Info> valinfo = new ArrayList<Info>();
		final List<Range> range = new ArrayList<Range>();
		boolean hadNocomma = false;
		final String parm2 = strip(get(args, 1));
		if (parm2 != null && parm2.endsWith("nocomma"))
		{
			args.set(1, parm2.substring(0, parm2.length() - 7).trim());
			parms.options.noComma = true;
			hadNocomma = true;
		}

		int i = 0;
		boolean isChange = false;
		while (true)
		{
			Info info = extractor(parms, args, i);
			i++;
			info.isChange = isChange;
			isChange = false;
			valinfo.add(info);
			Range rangeItem = RANGES.get(strip(get(args, i)));
			if (rangeItem == null)
			{
				break;
			}
			i++;
			range.add(rangeItem);
			parms.outRangeX |= rangeItem.outRangeX;
			parms.abbrRangeX |= rangeItem.abbrRangeX;
			isChange = rangeItem.isChange;
		}
		if (!range.isEmpty())
		{
			if (range.size() > 30)
			{
				throw new ConvertException("Number has overflowed");
			}
			parms.range = range;
		} else if (hadNocomma)
		{
			throw new ConvertException("Unit name \"" + parm2 + "\" is not known");
		}
		parms.nextArg = i;
		return valinfo;
	}

	/**
	 * Parses a value. If the argument is not a value, it is unpacked as a range
	 * if possible (e.g. "1 to 2" or "1-2").
	 */
	private static Info extractor(Parms parms, List<String> args, int i)
	{
		final String valStr = strip(get(args, i));
		try
		{
			return extractNumber(parms, valStr, i > 0);
		} catch (ConvertException ex)
		{
			if (valStr != null && i < 20)
			{
				Matcher m = SPACED_RANGE_RX.matcher(valStr);
				if (m.find() && !(m.group(2).equals("-") && m.group(3).contains("/")))
				{
					if (m.group(2).matches(".*\\d.*"))
					{
						throw ex;
					}
					args.set(i, m.group(3));
					args.add(i, m.group(2));
					args.add(i, m.group(1));
					return extractor(parms, args, i);
				}
				if (!valStr.matches("(?s).*-.*/.*"))
				{
					for (String sep : RANGE_WORDS)
					{
						int start = valStr.indexOf(sep, 1);
						if (start >= 0)
						{
							args.set(i, valStr.substring(start + sep.length()));
							args.add(i, sep);
							args.add(i, valStr.substring(0, start));
							return extractor(parms, args, i);
						}
					}
				}
			}
			throw ex;
		}
	}

	/**
	 * Parses an input value (extract_number() in Module:Convert).
	 *
	 * @param another True if the value is not the first one.
	 */
	private static Info extractNumber(Parms parms, String text, boolean another)
	{
		if (text == null || text.trim().isEmpty() || text.trim().replace(",", "").isEmpty())
		{
			throw new ConvertException(another
					? "Needs another number for a range"
					: "Needs the number to be converted");
		}
		ParsedNumber number;
		try
		{
			number = NumberFormater.parseValue(text, parms.options, parms.optRi);
		} catch (NumberFormatException e)
		{
			throw new ConvertException(e.getMessage());
		}

		final Info info = new Info();
		info.value = number.getValue();
		info.altvalue = number.getAltValue();
		info.singular = number.isSingular();
		info.clean = number.getClean();
		info.show = number.getShow();
		info.denominator = number.getDenominator();

		final String[] fraction = number.getFraction();
		if (fraction != null)
		{
			if (parms.optSpellIn)
			{
				info.show = formatFraction(parms, IN, number.isNegative(),
						fraction[0], fraction[1], fraction[2], true, number.getFractionStyle());
			}
		} else
		{
			boolean scientific = number.isScientific();
			if (parms.optSpellIn)
			{
				String rounded = (number.getRounded() != null) ? number.getRounded() : number.getClean();
				String spelled = spellNumber(parms, IN, number.getSign() + rounded, null, null);
				if (spelled != null)
				{
					info.show = spelled;
				}
				scientific = false;
			}
			if (scientific)
			{
				parms.optScientific = true;
			}
		}
		return info;
	}

	/**
	 * Returns the spelled number or null if it cannot be spelled
	 * (spell_number() in Module:Convert).
	 */
	private static String spellNumber(
			Parms parms,
			String inout,
			String number,
			String numerator,
			String denominator)
	{
		boolean upper = false;
		if (parms.spellUpper != null && parms.spellUpper.equals(inout))
		{
			upper = true;
			parms.spellUpper = null; // only uppercase first word in a multiple unit
		}
		return NumberSpeller.spell(number, numerator, denominator, upper, !parms.optSpUs, parms.optAdjectival);
	}

	/**
	 * Returns the markup of a fraction or the spelled fraction
	 * (format_fraction() in Module:Convert).
	 *
	 * @param style 1 (like {{frac}}), 2 (like {{sfrac}}) or 0 (as given by the
	 * frac option).
	 */
	private static String formatFraction(
			Parms parms,
			String inout,
			boolean negative,
			String wholestr,
			String numstr,
			String denstr,
			boolean doSpell,
			int style)
	{
		if (style == 0)
		{
			style = parms.optFractionHorizontal ? 2 : 1;
		}
		if (wholestr != null && wholestr.isEmpty())
		{
			wholestr = null;
		}
		String wikitext = NumberFormater.formatFraction(negative, wholestr, numstr, denstr, style, parms.options);
		if (doSpell)
		{
			if (negative)
			{
				if (wholestr != null)
				{
					wholestr = "-" + wholestr;
				} else
				{
					numstr = "-" + numstr;
				}
			}
			String s = spellNumber(parms, inout, wholestr, numstr, denstr);
			if (s != null)
			{
				return s;
			}
		}
		return wikitext;
	}

	/**
	 * Looks for a composite input unit like {{convert|1|yd|2|ft|3|in}}
	 * (get_composite() in Module:Convert).
	 *
	 * @param iparm Index just after the first unit.
	 * @return Null if there is no composite unit, otherwise the index just
	 * after the composite units and the composite unit.
	 */
	private static Object[] getComposite(Parms parms, List<String> args, int iparm, Unit inUnit)
	{
		String defaultCode = null;
		Info subinfo = null;
		final List<Unit> units = new ArrayList<Unit>();
		units.add(inUnit);
		final Map<Integer, Object[]> fixups = new HashMap<Integer, Object[]>();
		double total = inUnit.info(0).value;
		Unit subunit = inUnit;
		while (subunit.subdivs != null)
		{
			String subcode = strip(get(args, iparm + 1));
			if (subcode == null)
			{
				break;
			}
			Subdiv subdiv = subunit.subdivs.get(subcode);
			if (subdiv == null)
			{
				ConvertData.UnitDef def = ConvertData.get().getUnit(subcode);
				if (def != null && def.target != null)
				{
					subdiv = subunit.subdivs.get(def.target);
				}
			}
			if (subdiv == null)
			{
				break;
			}
			subunit = Unit.lookup(parms, subcode, Want.NO_COMBINATION);
			subinfo = extractNumber(parms, get(args, iparm), false);
			iparm += 2;
			subunit.inout = IN;
			subunit.valinfo = Collections.singletonList(subinfo);
			total = total * subdiv.count + subinfo.value;
			if (defaultCode == null)
			{
				defaultCode = subdiv.defaultCode;
			}
			units.add(subunit);
			if (subdiv.unit != null)
			{
				fixups.put(units.size() - 1, new Object[] { subdiv.unit, subunit.valinfo });
			}
		}
		if (units.size() == 1)
		{
			return null;
		}
		for (Map.Entry<Integer, Object[]> e : fixups.entrySet())
		{
			@SuppressWarnings("unchecked")
			List<Info> fixupInfo = (List<Info>) e.getValue()[1];
			Unit alternate = Unit.lookup(parms, (String) e.getValue()[0], Want.NO_COMBINATION);
			alternate.inout = IN;
			alternate.valinfo = fixupInfo;
			units.set(e.getKey(), alternate);
		}
		Info info = new Info();
		info.value = total;
		info.altvalue = total;
		info.clean = subinfo.clean;
		info.denominator = subinfo.denominator;
		Unit composite = Unit.composite(units, subunit.scale,
				(defaultCode != null) ? defaultCode : inUnit.defaultCode);
		composite.valinfo = Collections.singletonList(info);
		composite.inout = IN;
		return new Object[] { iparm, composite };
	}

	/**
	 * Returns the texts to insert before the input unit and the output unit
	 * (preunits() in Module:Convert). An element is null for no text.
	 */
	private static String[] preunits(int count, String preunit1, String preunit2)
	{
		final String plus = "+ ";
		preunit1 = (preunit1 != null) ? preunit1 : "";
		final String trim1 = preunit1.trim();
		if (count == 1)
		{
			if (trim1.isEmpty())
			{
				return new String[] { null };
			}
			if (trim1.equals("+"))
			{
				return new String[] { plus };
			}
			return new String[] { withSpace(preunit1, true) };
		}
		preunit1 = withSpace(preunit1, false);
		preunit2 = (preunit2 != null) ? preunit2 : "";
		final String trim2 = preunit2.trim();
		if (trim1.equals("+"))
		{
			if (trim2.isEmpty() || trim2.equals("+"))
			{
				return new String[] { plus, plus };
			}
			preunit1 = plus;
		}
		if (trim2.isEmpty())
		{
			if (trim1.isEmpty())
			{
				return new String[] { null, null };
			}
			preunit2 = preunit1;
		} else if (trim2.equals("+"))
		{
			preunit2 = plus;
		} else if (trim2.equals("&#32;"))
		{
			preunit2 = null; // trick to make preunit2 empty
		} else
		{
			preunit2 = withSpace(preunit2, false);
		}
		return new String[] { preunit1, preunit2 };
	}

	/**
	 * Returns text with a space before and, if wantBoth, after. However, no
	 * space is added if there is a space or "&amp;nbsp;" or "-" at that
	 * position, and text starting with "&amp;" is not changed.
	 */
	private static String withSpace(String text, boolean wantBoth)
	{
		if (text.startsWith("&"))
		{
			return text;
		}
		if (!(text.startsWith(" ") || text.startsWith("-") || text.startsWith("+")))
		{
			text = " " + text;
		}
		if (wantBoth && !(text.endsWith(" ") || text.endsWith("-") || text.endsWith("&nbsp;")))
		{
			text = text + " ";
		}
		return text;
	}

	// =========================================================================

	/**
	 * Converts and formats (process() in Module:Convert).
	 *
	 * @return The result and the output unit (which is used again if the
	 * conversion is repeated with a higher precision).
	 */
	private static Object[] process(Parms parms, Unit inUnit, Unit outUnit)
	{
		parms.linkedPages = new HashSet<Object>();
		String outCode = parms.outUnit;
		// Module:Convert ignores any problem of the output unit if the input
		// unit is not known (and shown without an error).
		boolean noOutput = parms.badInput;
		if (!noOutput && (outCode == null || outCode.isEmpty()))
		{
			if (parms.optInputUnitOnly)
			{
				noOutput = true;
			} else
			{
				outCode = getDefault(inUnit.info(0).value, inUnit);
				parms.outUnit = outCode;
			}
		}
		if (!noOutput && outUnit == null)
		{
			outUnit = Unit.lookup(parms, outCode, Want.ANY_COMBINATION);
			Unit.checkMismatch(inUnit, outUnit);
		}

		String lhs = null;
		String rhs;
		final boolean flipped = parms.optFlip && !parms.badInput;
		if (noOutput || parms.optInputUnitOnly)
		{
			rhs = "";
		} else
		{
			List<Unit> combos = null; // null for "ft" or "ftin", or units of "m ft"
			if (outUnit.multiple == null)
			{
				combos = outUnit.combination;
			}
			if (parms.frac != null)
			{
				// Apply fraction to the unit (if only one), or to non-SI units
				// (if a combination), except that if a precision is also
				// specified, the fraction only applies to the hand unit.
				if (combos != null)
				{
					for (Unit unit : combos)
					{
						if ("hand".equals(unit.builtin) || (parms.precision == null && unit.prefixes == 0))
						{
							unit.frac = parms.frac;
						}
					}
				} else
				{
					outUnit.frac = parms.frac;
				}
			}
			final int imax = (combos != null) ? combos.size() : 1;
			if (imax == 1)
			{
				parms.optOrderOut = false; // only useful with an output combination
			}
			if (!flipped && !parms.optOrderOut)
			{
				// process left side first so any duplicate links are suppressed on right
				lhs = processInput(parms, inUnit);
			}
			List<String> outputs = new ArrayList<String>(imax);
			for (int i = 0; i < imax; i++)
			{
				Unit outCurrent = (combos != null) ? combos.get(i) : outUnit;
				outCurrent.inout = OUT;
				if (i == 0)
				{
					if (imax > 1 && "hand".equals(outCurrent.builtin))
					{
						outCurrent.outNext = combos.get(1);
					}
					if (parms.optOrderOut)
					{
						outCurrent.inout = IN;
					}
				}
				if (outCurrent.multiple != null)
				{
					outputs.add(makeOutputMultiple(parms, inUnit, outCurrent));
				} else
				{
					outputs.add(makeOutputSingle(parms, inUnit, outCurrent));
				}
			}
			if (parms.optOrderOut)
			{
				lhs = outputs.remove(0);
			}
			String sep = (parms.tableJoins != null) ? parms.tableJoins[1] : parms.joinBetween;
			rhs = String.join(sep, outputs);
		}
		if (flipped || lhs == null)
		{
			String input = processInput(parms, inUnit);
			if (flipped)
			{
				lhs = rhs;
				rhs = input;
			} else
			{
				lhs = input;
			}
		}
		if (parms.joinBefore != null)
		{
			lhs = parms.joinBefore + lhs;
		}
		String wikitext;
		if (parms.badInput)
		{
			wikitext = lhs;
		} else if (parms.tableJoins != null)
		{
			wikitext = parms.tableJoins[0] + lhs + parms.tableJoins[1] + rhs;
		} else
		{
			wikitext = lhs + parms.joins[0] + rhs + parms.joins[1];
		}
		return new Object[] { wikitext, outUnit };
	}

	/**
	 * Returns the code of the default output unit (get_default() in
	 * Module:Convert). Some units have a default that depends on the input
	 * value, like "v < 120 ! small ! big ! suffix".
	 */
	private static String getDefault(double value, Unit unit)
	{
		final ConvertData data = ConvertData.get();
		String defaultCode = data.getDefaultException((unit.defkey != null) ? unit.defkey : unit.symbol());
		if (defaultCode == null)
		{
			defaultCode = unit.defaultCode;
		}
		if (defaultCode == null)
		{
			if (unit.isPer())
			{
				Unit unit1 = unit.per[0];
				String def1 = (unit1 != null)
						? perDefault(value, unit1)
						: ((unit.vprefix != null) ? unit.vprefix : "");
				String def2 = perDefault(1, unit.per[1]);
				return def1 + "/" + def2;
			}
			throw new ConvertException("Unit \"" + unit.symbol() + "\" has no default output unit");
		}
		if (defaultCode.indexOf('!') < 0)
		{
			return defaultCode;
		}
		String[] t = defaultCode.split("!", -1);
		if (t.length == 3 || t.length == 4)
		{
			Boolean result = evaluateCondition(value, t[0]);
			if (result != null)
			{
				String d = (result ? t[1] : t[2]).trim();
				if (t.length == 4)
				{
					d = d + t[3].trim();
				}
				return d;
			}
		}
		throw new ConvertException("Unit \"" + unit.symbol() + "\" has an invalid default");
	}

	/**
	 * Returns the default of a unit of a per unit, using only the first unit
	 * if it is a combination (a_default() in Module:Convert).
	 */
	private static String perDefault(double value, Unit unit)
	{
		String ucode;
		try
		{
			ucode = getDefault(value, unit);
		} catch (ConvertException e)
		{
			return "?";
		}
		ConvertData.UnitDef t = ConvertData.get().getUnit(ucode);
		if (t != null)
		{
			if (t.combination != null)
			{
				// for a multiple like ftin, the "first" unit (ft) is last in the combination
				ucode = t.combination[(t.multiple != null) ? t.combination.length - 1 : 0];
			}
		} else
		{
			String item = null;
			int plus = ucode.indexOf('+');
			if (plus >= 0)
			{
				item = ucode.substring(0, plus);
			} else
			{
				Matcher m = Pattern.compile("^(\\S+)\\s").matcher(ucode);
				if (m.find())
				{
					item = m.group(1);
				}
			}
			if (item != null && ConvertData.get().getUnit(item) != null)
			{
				return item;
			}
		}
		return ucode;
	}

	/**
	 * Applies a condition like "v &lt; 9" or "v * 9 &gt;= 9 and v &lt; 5" to
	 * the value (evaluate_condition() in Module:Convert).
	 *
	 * @return The result or null if the condition is invalid.
	 */
	private static Boolean evaluateCondition(double value, String condition)
	{
		Matcher m = CONDITION_AND_RX.matcher(condition);
		if (m.matches())
		{
			Boolean lhs = compare(value, m.group(1));
			Boolean rhs = compare(value, m.group(2));
			return (lhs == null || rhs == null) ? null : lhs && rhs;
		}
		return compare(value, condition);
	}

	private static Boolean compare(double value, String text)
	{
		Matcher m = CONDITION_RX.matcher(text);
		if (!m.matches())
		{
			return null;
		}
		try
		{
			if (m.group(1).equals("*"))
			{
				value = value * Double.parseDouble(m.group(2).trim());
			} else if (!m.group(2).trim().isEmpty())
			{
				return null;
			}
			double limit = Double.parseDouble(m.group(4).trim());
			switch (m.group(3))
			{
				case "<":
					return value < limit;
				case "<=":
					return value <= limit;
				case ">":
					return value > limit;
				default:
					return value >= limit;
			}
		} catch (NumberFormatException e)
		{
			return null;
		}
	}

	// =========================================================================

	/**
	 * @return The input part of the result (e.g. "5 metres")
	 * (process_input() in Module:Convert).
	 */
	private static String processInput(Parms parms, Unit inCurrent)
	{
		if (parms.optOutputOnly || parms.optOutputNumberOnly || parms.optOutputUnitOnly)
		{
			parms.joins = new String[] { "", "" };
			return "";
		}
		final List<Unit> composite = inCurrent.composite;
		final Unit firstUnit = (composite != null) ? composite.get(0) : inCurrent;
		final Id id1 = makeId(parms, 0, firstUnit);
		final boolean wantName = id1.name;
		String sep = firstUnit.sep;
		String preunit = parms.preunit1;
		if (preunit != null)
		{
			sep = ""; // any separator is included in preunit
		} else
		{
			preunit = "";
		}
		if (parms.optInputUnitOnly)
		{
			parms.joins = new String[] { "", "" };
			String text = id1.text;
			if (composite != null)
			{
				for (int i = 1; i < composite.size(); i++)
				{
					text += " " + makeId(parms, 0, composite.get(i)).text;
				}
			}
			if (wantName && parms.optAdjectival)
			{
				return preunit + hyphenated(text);
			}
			return preunit + text;
		}
		if (parms.optAlsoSymbol && composite == null && !parms.optFlip)
		{
			String join1 = parms.joins[0];
			if (join1.equals(" (") || join1.equals(" ["))
			{
				parms.joins = new String[] {
						" [" + firstUnit.get(parms.optSpUs ? Key.SYM_US : Key.SYMBOL) + "]" + join1,
						parms.joins[1] };
			}
		}
		if ("mach".equals(inCurrent.builtin) && !"".equals(firstUnit.sep))
		{
			String result = id1.text + SYMBOL_SEP + firstUnit.info(0).show;
			if (parms.range != null)
			{
				// handle one range item only
				String prefix2 = makeId(parms, 1, firstUnit).text + SYMBOL_SEP;
				result = rangeText(parms, parms.range.get(0), wantName, result,
						prefix2 + firstUnit.info(1).show, IN, true);
			}
			return preunit + result;
		}
		if (composite != null)
		{
			// assume there is no range and no decoration
			String mid = (!parms.optFlip && parms.mid != null) ? parms.mid : "";
			String sep1 = SYMBOL_SEP;
			String sep2 = " ";
			if (parms.optAdjectival && wantName)
			{
				sep1 = "-";
				sep2 = "-";
			}
			StringBuilder sb = new StringBuilder();
			sb.append(firstUnit.info(0).show).append(sep1).append(id1.text);
			for (int i = 1; i < composite.size(); i++)
			{
				Unit unit = composite.get(i);
				sb.append(sep2).append(unit.info(0).show).append(sep1).append(makeId(parms, 0, unit).text);
			}
			return sb.append(mid).toString();
		}
		boolean addUnit = (parms.optFlip && parms.outRangeX) || (!wantName && parms.abbrRangeX);
		final List<Range> range = parms.range;
		if (range != null && !addUnit)
		{
			unlink(parms, firstUnit);
		}
		final Id id = (range != null) ? makeId(parms, range.size(), firstUnit) : id1;
		final String[] extra = hyphenatedMaybe(parms, wantName, sep, id.text, IN);
		if (extra[1] != null)
		{
			addUnit = false;
		}
		String result;
		if (range != null)
		{
			result = null;
			for (int i = 0; i <= range.size(); i++)
			{
				boolean enableNumberWord = false;
				if (i == range.size())
				{
					addUnit = false;
					enableNumberWord = true;
				}
				decorateValue(parms, firstUnit, i, enableNumberWord);
				String show = firstUnit.info(i).show;
				if (addUnit)
				{
					show += firstUnit.sep + ((i == 0) ? id1.text : makeId(parms, i, firstUnit).text);
				}
				result = (i == 0) ? show : rangeText(parms, range.get(i - 1), wantName, result, show, IN, false);
			}
		} else
		{
			decorateValue(parms, firstUnit, 0, true);
			result = firstUnit.info(0).show;
		}
		return result + preunit + extra[0];
	}

	/**
	 * @return The part of the result for a single output unit (e.g. "16 ft")
	 * (process_one_output() in Module:Convert).
	 */
	private static String processOneOutput(Parms parms, Unit outCurrent)
	{
		final String inout = outCurrent.inout; // normally "out" but can be "in" for order=out
		final Id id1 = makeId(parms, 0, outCurrent);
		final boolean wantName = id1.name;
		String sep = outCurrent.sep;
		String preunit = parms.preunit2;
		if (preunit != null)
		{
			sep = "";
		} else
		{
			preunit = "";
		}
		if (parms.optOutputUnitOnly)
		{
			if (wantName && parms.optAdjectival)
			{
				return preunit + hyphenated(id1.text);
			}
			return preunit + id1.text;
		}
		final List<Range> range = parms.range;
		if ("mach".equals(outCurrent.builtin) && !"".equals(outCurrent.sep))
		{
			String prefix = id1.text + SYMBOL_SEP;
			String result = prefix + outCurrent.info(0).show;
			if (range != null)
			{
				result = rangeText(parms, range.get(0), wantName, result,
						prefix + outCurrent.info(1).show, inout, true);
			}
			return preunit + result;
		}
		boolean addUnit = ((!parms.optFlip && parms.outRangeX) || (!wantName && parms.abbrRangeX))
				&& !parms.optOutputNumberOnly;
		if (range != null && !addUnit)
		{
			unlink(parms, outCurrent);
		}
		final Id id = (range != null) ? makeId(parms, range.size(), outCurrent) : id1;
		final String[] extra = hyphenatedMaybe(parms, wantName, sep, id.text, inout);
		if (extra[1] != null)
		{
			addUnit = false;
		}
		String result;
		if (range != null)
		{
			result = null;
			for (int i = 0; i <= range.size(); i++)
			{
				boolean enableNumberWord = false;
				if (i == range.size())
				{
					addUnit = false;
					enableNumberWord = true;
				}
				decorateValue(parms, outCurrent, i, enableNumberWord);
				String show = outCurrent.info(i).show;
				if (addUnit)
				{
					show += outCurrent.sep + ((i == 0) ? id1.text : makeId(parms, i, outCurrent).text);
				}
				result = (i == 0) ? show : rangeText(parms, range.get(i - 1), wantName, result, show, inout, false);
			}
		} else
		{
			decorateValue(parms, outCurrent, 0, true);
			result = outCurrent.info(0).show;
		}
		if (parms.optOutputNumberOnly)
		{
			return result;
		}
		return result + preunit + extra[0];
	}

	/**
	 * @return The conversion result for a single output (which is not a
	 * combination or a multiple) (make_output_single() in Module:Convert).
	 */
	private static String makeOutputSingle(Parms parms, Unit inUnit, Unit outUnit)
	{
		if (parms.optOrderOut && inUnit.unitcode != null && inUnit.unitcode.equals(outUnit.unitcode))
		{
			outUnit.valinfo = inUnit.valinfo;
		} else
		{
			outUnit.valinfo = new ArrayList<Info>();
			for (Info v : inUnit.valinfo)
			{
				outUnit.valinfo.add(cvtround(parms, v, inUnit, outUnit));
			}
		}
		return processOneOutput(parms, outUnit);
	}

	/**
	 * @return The conversion result for an output which is a multiple (like
	 * "ftin"). The value is rounded in the least significant unit, then split
	 * into the units and the sign is applied once (make_output_multiple() in
	 * Module:Convert).
	 */
	private static String makeOutputMultiple(Parms parms, Unit inUnit, Unit outUnit)
	{
		final String inout = outUnit.inout;
		final String abbr = parms.abbr;
		final boolean wantName = (parms.abbrOrg == null && "or".equals(parms.disp))
				|| !("on".equals(abbr) || abbr.equals(inout));
		final boolean wantLink = "on".equals(parms.lk) || inout.equals(parms.lk);
		final String mid = (parms.optFlip && parms.mid != null) ? parms.mid : "";
		final boolean doSpell = parms.optSpellOut;
		parms.optSpellOut = false; // so the call to cvtround does not spell the value

		String result = makeMultiple(parms, inUnit, outUnit, inUnit.info(0), true, wantName, wantLink, doSpell);
		if (parms.range != null)
		{
			for (int i = 0; i < parms.range.size(); i++)
			{
				String result2 = makeMultiple(parms, inUnit, outUnit, inUnit.info(i + 1), false,
						wantName, wantLink, doSpell);
				result = rangeText(parms, parms.range.get(i), wantName, result, result2, inout, true);
			}
		}
		return result + mid;
	}

	private static String makeMultiple(
			Parms parms,
			Unit inUnit,
			Unit outUnit,
			Info info,
			boolean isFirst,
			boolean wantName,
			boolean wantLink,
			boolean doSpell)
	{
		final String inout = outUnit.inout;
		final List<Unit> combos = outUnit.combination;
		final double[] multiple = outUnit.multiple;
		String sep1 = SYMBOL_SEP;
		String sep2 = " ";
		if (parms.optAdjectival && wantName)
		{
			sep1 = "-";
			sep2 = "-";
		}

		int decimals = 0;
		double outvalue = 0;
		String sign = "";
		final List<String> results = new ArrayList<String>();
		for (int i = 0; i < combos.size(); i++)
		{
			final Unit outCurrent = combos.get(i);
			outCurrent.inout = inout;
			// only the least significant unit can have a fraction or use
			// scientific notation
			FractionTable tfrac = null;
			String strforce = null;
			double thisvalue;
			if (i == 0)
			{
				// least significant unit ("in" of "ftin")
				outCurrent.frac = outUnit.frac;
				Info outinfo = cvtround(parms, info, inUnit, outCurrent);
				if (isFirst)
				{
					outUnit.valinfo = Collections.singletonList(outinfo);
				}
				sign = outinfo.sign;
				tfrac = outinfo.fractionTable;
				String decimalText;
				if (outinfo.isScientific)
				{
					strforce = outinfo.show;
					decimalText = "";
				} else if (tfrac != null)
				{
					decimalText = "";
				} else
				{
					int dot = outinfo.show.indexOf('.');
					decimalText = (dot >= 0) ? outinfo.show.substring(dot + 1) : "";
				}
				decimals = decimalText.length();
				if (decimalText.isEmpty())
				{
					outvalue = (tfrac != null)
							? Math.floor(outinfo.rawAbsvalue) // integer part only; fraction added later
							: Math.floor(outinfo.rawAbsvalue + 0.5); // keep all integer digits
				} else
				{
					outvalue = outinfo.absvalue();
				}
			}
			if (i < multiple.length)
			{
				double scale = multiple[i];
				double quotient = Math.floor(outvalue / scale);
				thisvalue = outvalue % scale;
				if (!(0 <= thisvalue && thisvalue < scale))
				{
					thisvalue = 0;
				}
				outvalue = quotient;
			} else
			{
				thisvalue = outvalue;
			}

			String id;
			if (wantName)
			{
				Key key = Key.NAME2;
				if (parms.optAdjectival)
				{
					key = Key.NAME1;
				} else if (tfrac != null)
				{
					if (thisvalue == 0)
					{
						key = Key.NAME1;
					}
				} else if (thisvalue == 1)
				{
					key = Key.NAME1;
				}
				id = outCurrent.get(key);
			} else
			{
				id = outCurrent.symbol();
			}
			if (i == 0 && id.startsWith("/"))
			{
				sep1 = "";
				sep2 = "";
			}
			if (wantLink)
			{
				String link = outCurrent.link();
				if (link != null)
				{
					id = makeLink(parms, link, id, outCurrent);
				}
			}
			// trick so the last value processed (first displayed) has uppercase
			String spellInout = (i == combos.size() - 1 || outvalue == 0) ? inout : "";
			String strval;
			if (strforce != null && outvalue == 0)
			{
				sign = ""; // any sign is in strforce
				strval = strforce;
			} else if (tfrac != null)
			{
				String wholestr = (thisvalue > 0) ? NumberFormater.luaToString(thisvalue) : null;
				strval = formatFraction(parms, spellInout, false, wholestr, tfrac.numstr, tfrac.denstr, doSpell, 0);
			} else
			{
				strval = (thisvalue == 0)
						? "0"
						: NumberFormater.withSeparator(NumberFormater.toFixed(thisvalue, decimals), parms.options);
				if (doSpell)
				{
					String spelled = spellNumber(parms, spellInout, strval, null, null);
					if (spelled != null)
					{
						strval = spelled;
					}
				}
			}
			results.add(strval + sep1 + id);
			if (outvalue == 0)
			{
				break;
			}
			decimals = 0; // only least significant unit can have a non-integral value
		}
		Collections.reverse(results);
		return sign + String.join(sep2, results);
	}

	/**
	 * Returns the text of a range (e.g. "5 to 10") (range_text() in
	 * Module:Convert).
	 */
	private static String rangeText(
			Parms parms,
			Range range,
			boolean wantName,
			String before,
			String after,
			String inout,
			boolean spaced)
	{
		String rtext;
		if (range.text != null)
		{
			rtext = range.text;
		} else
		{
			rtext = wantName ? range.off : range.on;
			if (rtext == null)
			{
				rtext = (IN.equals(inout) == parms.optFlip) ? range.output : range.input;
			}
		}
		if (parms.optAdjectival
				&& (wantName || (range.exception && "on".equals(parms.abbrOrg))))
		{
			rtext = (range.adj != null) ? range.adj : rtext.replace(' ', '-');
		}
		if (rtext.equals("–") && (spaced || after.startsWith(MINUS)))
		{
			rtext = " – ";
		}
		return before + rtext + after;
	}

	/**
	 * Adds engineering notation (like "×10⁶" or "million") and a currency
	 * symbol to a value (decorate_value() in Module:Convert).
	 */
	private static void decorateValue(Parms parms, Unit unit, int which, boolean enableNumberWord)
	{
		if (unit.engscale == null && unit.vprefix == null)
		{
			return;
		}
		Info info = unit.info(which);
		if (info.decorated)
		{
			return; // do not redecorate if repeating convert
		}
		info.decorated = true;
		if (unit.engscale != null)
		{
			String inout = unit.inout;
			if (("on".equals(parms.abbr) || parms.abbr.equals(inout))
					&& !(unit.thisNumberWord || parms.numberWord))
			{
				info.show = info.show + "×10"
						+ NumberFormater.asSuperscriptNumber(String.valueOf(unit.engscale.exponent));
			} else if (enableNumberWord)
			{
				String name = unit.engscale.name;
				String numberId = ("on".equals(parms.lk) || inout.equals(parms.lk))
						? makeLink(parms, unit.engscale.link, name, null)
						: name;
				info.show = info.show + (parms.optAdjectival ? "-" : SYMBOL_SEP) + numberId;
			}
		}
		if (unit.vprefix != null)
		{
			info.show = unit.vprefix + info.show;
		}
	}

	// =========================================================================

	/**
	 * Converts and rounds a value (cvtround() in Module:Convert).
	 */
	private static Info cvtround(Parms parms, Info info, Unit inCurrent, Unit outCurrent)
	{
		if ("hand".equals(outCurrent.builtin))
		{
			return cvtToHand(parms, info, inCurrent, outCurrent);
		}
		double invalue = "hand".equals(inCurrent.builtin) ? info.altvalue : info.value;
		final Converted converted = convertValue(parms, invalue, info, inCurrent, outCurrent);
		if (parms.needTableOrSort)
		{
			parms.needTableOrSort = false; // process using first input value only
			makeTableOrSort(parms, invalue, info, inCurrent);
		}
		double outvalue = converted.outvalue;
		final Extra extra = converted.extra;
		if (extra != null && extra.invalue != null)
		{
			invalue = extra.invalue;
		}
		if (Double.isNaN(outvalue) || Double.isInfinite(outvalue))
		{
			throw new ConvertException("Number has overflowed");
		}
		boolean isNegative = false;
		if (outvalue < 0)
		{
			isNegative = true;
			outvalue = -outvalue;
		}

		Integer precision = null;
		String show = null;
		Integer exponent = null;
		FractionTable tfrac = null;
		if (outCurrent.frac != null)
		{
			Object t = fractionTable(outvalue, outCurrent.frac);
			if (t instanceof FractionTable)
			{
				tfrac = (FractionTable) t;
			} else
			{
				show = (String) t;
			}
		} else
		{
			precision = parms.precision;
			if (precision == null)
			{
				if (parms.sigfig != null)
				{
					String[] sigfig = NumberFormater.makeSigFig(outvalue, parms.sigfig);
					show = sigfig[0];
					exponent = Integer.valueOf(sigfig[1]);
				} else if (parms.optRound != null)
				{
					double n = parms.optRound;
					if (n == 0.5)
					{
						double rounded = Math.floor(2 * outvalue + 0.5) / 2;
						show = NumberFormater.toFixed(rounded, (rounded == Math.floor(rounded)) ? 0 : 1);
					} else
					{
						show = NumberFormater.toFixed(Math.floor(outvalue / n + 0.5) * n, 0);
					}
				} else if ("mach".equals(inCurrent.builtin))
				{
					int sigfig = info.clean.replaceFirst("^[0.]+", "").replace(".", "").length() + 1;
					String[] digits = NumberFormater.makeSigFig(outvalue, sigfig);
					show = digits[0];
					exponent = Integer.valueOf(digits[1]);
				} else
				{
					String inclean = info.clean;
					if (extra != null)
					{
						if (extra.clean != null)
						{
							inclean = extra.clean;
						}
						show = extra.show;
					}
					if (show == null)
					{
						precision = defaultPrecision(parms, invalue, inclean, info.denominator,
								outvalue, inCurrent, outCurrent, extra);
					}
				}
			}
		}
		if (precision != null)
		{
			if (precision >= 0)
			{
				if (precision > 99)
				{
					throw new ConvertException("Precision \"" + precision + "\" is too large");
				}
				// fudge to handle common cases of bad rounding
				double fudge = (precision <= 8) ? 2e-14 : 0;
				show = NumberFormater.toFixed(outvalue + fudge, precision);
			} else
			{
				int digits = -precision;
				show = NumberFormater.toFixed(outvalue / Unit.pow10(digits), 0);
				if (!show.equals("0"))
				{
					exponent = show.length() + digits;
				}
			}
		}

		final Info t = formatNumber(parms, (tfrac != null) ? tfrac : show, exponent, isNegative);
		if (tfrac != null)
		{
			t.fractionTable = tfrac;
			t.singular = (outvalue <= 1);
		}
		t.rawAbsvalue = outvalue;
		return t;
	}

	/**
	 * Converts to hands and inches (cvt_to_hand() in Module:Convert).
	 */
	private static Info cvtToHand(Parms parms, Info info, Unit inCurrent, Unit outCurrent)
	{
		if (parms.abbrOrg == null)
		{
			outCurrent.usename = true; // default is to show name not symbol
		}
		final Integer precision = parms.precision;
		Integer frac = outCurrent.frac;
		if (frac == null && precision != null && precision > 1)
		{
			frac = (precision == 2) ? 2 : 4;
		}
		final Unit outNext = outCurrent.outNext;
		if (outNext != null && "subunit_more_precision".equals(outNext.exception))
		{
			// the inches of "hand in" are rounded to match the hands
			outNext.frac = frac;
		}
		// convert to inches; calculate hands from that
		final Unit dummy = Unit.unknown(null);
		dummy.scale = outCurrent.scale / 4;
		dummy.frac = frac;
		dummy.defaultCode = null;
		final Info outinfo = cvtround(parms, info, inCurrent, dummy);
		final FractionTable tfrac = outinfo.fractionTable;
		double inches = outinfo.rawAbsvalue;
		inches = (tfrac != null) ? Math.floor(inches) : Math.floor(inches + 0.5);
		double hands = Math.floor(inches / 4);
		inches = inches % 4;
		if (!(0 <= inches && inches < 4))
		{
			inches = 0;
		}
		outinfo.absvalueOverride = hands + inches / 4;
		String inchstr = NumberFormater.luaToString(inches);
		if (precision != null && precision <= 0)
		{
			// rounds to nearest hand
			hands = Math.floor(outinfo.rawAbsvalue / 4 + 0.5);
			inchstr = "";
		} else if (tfrac != null)
		{
			// always show an integer before the fraction (like "15.0½")
			inchstr = "." + formatFraction(parms, OUT, false, inchstr, tfrac.numstr, tfrac.denstr, false, 0);
		} else
		{
			inchstr = "." + inchstr;
		}
		outinfo.show = outinfo.sign + NumberFormater.withSeparator(NumberFormater.toFixed(hands, 0), parms.options) + inchstr;
		return outinfo;
	}

	/**
	 * Returns the value as a fraction with the given denominator, or as a
	 * string if there is no fraction (fraction_table() in Module:Convert).
	 */
	private static Object fractionTable(double value, int denominator)
	{
		if (value <= 0)
		{
			return "0";
		}
		if (denominator <= 0 || value > 1e8)
		{
			return NumberFormater.toFixed(value, 2);
		}
		double integer = Math.floor(value);
		double decimals = value - integer;
		long numerator = (long) Math.floor(decimals * denominator + 0.5 + 2e-14);
		long den = denominator;
		if (numerator >= den)
		{
			integer++;
			numerator = 0;
		}
		String wholestr = NumberFormater.luaToString(integer);
		if (numerator > 0)
		{
			long div = gcd(numerator, den);
			if (div > 1)
			{
				numerator /= div;
				den /= div;
			}
			FractionTable t = new FractionTable();
			t.wholestr = (integer > 0) ? wholestr : "";
			t.numstr = String.valueOf(numerator);
			t.denstr = String.valueOf(den);
			t.value = value;
			return t;
		}
		return wholestr;
	}

	private static long gcd(long a, long b)
	{
		while (a > 0)
		{
			long r = b % a;
			b = a;
			a = r;
		}
		return b;
	}

	/**
	 * Formats a rounded output value (format_number() in Module:Convert).
	 *
	 * @param show The digits (a string) or a fraction.
	 */
	private static Info formatNumber(Parms parms, Object show, Integer exponent, boolean isNegative)
	{
		final Info t = new Info();
		if (show instanceof FractionTable)
		{
			FractionTable tfrac = (FractionTable) show;
			t.clean = NumberFormater.luaToString(tfrac.value);
			t.sign = isNegative ? MINUS : "";
			t.show = formatFraction(parms, OUT, isNegative, tfrac.wholestr, tfrac.numstr, tfrac.denstr,
					parms.optSpellOut, 0);
			return t;
		}
		FormattedNumber f = NumberFormater.formatShow((String) show, exponent, isNegative,
				parms.optScientific, parms.options);
		t.clean = f.getClean();
		t.exponent = f.getExponent();
		t.sign = f.getSign();
		t.show = f.getShow();
		t.isScientific = f.isScientific();
		t.singular = f.isSingular();
		if (!t.isScientific && parms.optSpellOut)
		{
			String spelled = spellNumber(parms, OUT, t.sign + t.clean, null, null);
			if (spelled != null)
			{
				t.show = spelled;
			}
		}
		return t;
	}

	/**
	 * Converts a value from one unit to another (convert() in
	 * Module:Convert).
	 */
	private static Converted convertValue(Parms parms, double invalue, Info info, Unit inCurrent, Unit outCurrent)
	{
		double inscale = inCurrent.scale;
		double outscale = outCurrent.scale;
		if (!inCurrent.iscomplex && !outCurrent.iscomplex)
		{
			return new Converted(invalue * (inscale / outscale), null);
		}
		if (inCurrent.invert != 0 || outCurrent.invert != 0)
		{
			// inverted units, such as inverse length or fuel efficiency
			int in = (inCurrent.invert != 0) ? inCurrent.invert : 1;
			int out = (outCurrent.invert != 0) ? outCurrent.invert : 1;
			if (in * out < 0)
			{
				return new Converted(1 / (invalue * inscale * outscale), null);
			}
			return new Converted(invalue * (inscale / outscale), null);
		}
		if (inCurrent.offset != null)
		{
			// temperature
			if (info.isChange)
			{
				return new Converted(invalue * (inscale / outscale), null);
			}
			double outOffset = (outCurrent.offset != null) ? outCurrent.offset : 0;
			return new Converted((invalue - inCurrent.offset) * (inscale / outscale) + outOffset, null);
		}

		// built-in units
		final String inBuiltin = inCurrent.builtin;
		final String outBuiltin = outCurrent.builtin;
		if (inBuiltin != null && outBuiltin != null)
		{
			if (inBuiltin.equals(outBuiltin))
			{
				return new Converted(invalue, null);
			}
			throw new ConvertException("Bug: Cannot convert between specified units");
		}
		if ("mach".equals(inBuiltin) || "mach".equals(outBuiltin))
		{
			Double alt = (parms.altitudeFt != null) ? parms.altitudeFt : inCurrent.altitude;
			if (alt == null && parms.altitudeM != null)
			{
				alt = parms.altitudeM / 0.3048; // 1 ft = 0.3048 m
			}
			double spd = speedOfSound(alt);
			if ("mach".equals(inBuiltin))
			{
				inscale = spd;
				return new Converted(invalue * (inscale / outscale), null);
			}
			outscale = spd;
			double adjust = 0.1 / inscale;
			Extra extra = new Extra();
			extra.adjust = Math.log10(adjust) + Math.log10(2);
			return new Converted(invalue * (inscale / outscale), extra);
		}
		if ("hand".equals(inBuiltin))
		{
			// 1 hand = 4 inches; 1.2 hands = 6 inches. The entire fractional
			// part is interpreted as the number of inches / 10.
			double integer = (invalue < 0) ? Math.ceil(invalue) : Math.floor(invalue);
			double fracpart = invalue - integer;
			double inchValue = 4 * integer + 10 * fracpart; // equivalent number of inches
			double factor = inscale / outscale;
			if (factor == 4)
			{
				// converting to inches: show exact result and use "inches" by default
				if (parms.abbrOrg == null)
				{
					outCurrent.usename = true;
				}
				String show = NumberFormater.toGeneral(Math.abs(inchValue), 6);
				if (show.indexOf('e') < 0)
				{
					Extra extra = new Extra();
					extra.invalue = inchValue;
					extra.clean = show;
					extra.show = show;
					return new Converted(inchValue, extra);
				}
			}
			double outvalue = (integer + 2.5 * fracpart) * factor;
			int dot = info.clean.indexOf('.');
			String fracstr = (dot >= 0) ? info.clean.substring(dot + 1) : "";
			Extra extra = new Extra();
			extra.invalue = inchValue;
			extra.clean = NumberFormater.toFixed(inchValue, fracstr.isEmpty() ? 0 : Math.max(fracstr.length() - 1, 0));
			extra.minprec = 0d;
			return new Converted(outvalue, extra);
		}
		throw new ConvertException("Bug: Cannot convert between specified units");
	}

	/**
	 * @return The speed of sound in metres per second at the given altitude in
	 * feet (for the Mach unit).
	 */
	private static double speedOfSound(Double altitudeFt)
	{
		double altitude = ((altitudeFt != null) ? altitudeFt : 0) / 5000;
		if (altitude < -3)
		{
			altitude = -3;
		} else if (altitude > 80)
		{
			altitude = 80;
		}
		double a = Math.floor(altitude);
		int index = (int) a + 3;
		double machMph;
		if (a == altitude)
		{
			machMph = MACH_TABLE[index];
		} else
		{
			double t = altitude - a;
			machMph = t * MACH_TABLE[index + 1] + (1 - t) * MACH_TABLE[index];
		}
		return machMph * 0.44704; // mph converted to m/s
	}

	/**
	 * Calculates the default precision (default_precision() in
	 * Module:Convert). It depends on the number of significant figures of the
	 * input value and on the conversion factor.
	 *
	 * @return The precision (digits after the decimal mark, or if negative,
	 * digits before the decimal mark set to zero).
	 */
	private static int defaultPrecision(
			Parms parms,
			double invalue,
			String inclean,
			int denominator,
			double outvalue,
			Unit inCurrent,
			Unit outCurrent,
			Extra extra)
	{
		boolean subunitIgnoreTrailingZero = false;
		boolean subunitMorePrecision = false;
		final List<Unit> composite = inCurrent.composite;
		if (composite != null)
		{
			subunitIgnoreTrailingZero = true; // input "|2|st|10|lb" has precision 0, not -1
			if ("subunit_more_precision".equals(composite.get(composite.size() - 1).exception))
			{
				subunitMorePrecision = true;
			}
		}

		double prec;
		if (denominator > 0)
		{
			prec = Math.max(Math.log10(denominator), 1);
		} else
		{
			// count digits after decimal mark, handling cases like '12.345e6'
			Matcher m = CLEAN_RX.matcher(inclean);
			m.matches();
			String integer = m.group(1);
			String rest = m.group(4);
			if (m.group(2).isEmpty())
			{
				int zeros = 0;
				while (zeros < integer.length() && integer.charAt(integer.length() - 1 - zeros) == '0')
				{
					zeros++;
				}
				prec = subunitIgnoreTrailingZero ? 0 : -zeros;
			} else
			{
				prec = m.group(3).length();
			}
			if (rest.startsWith("e") || rest.startsWith("E"))
			{
				try
				{
					prec -= Integer.parseInt(rest.substring(1).replace("+", ""));
				} catch (NumberFormatException e)
				{
					// not an exponent
				}
			}
		}

		double adjust;
		double minprec;
		if (inCurrent.istemperature && outCurrent.istemperature)
		{
			adjust = 0;
			double kelvin = Math.abs((invalue - inCurrent.offset) * inCurrent.scale);
			if (kelvin < 1e-8)
			{
				minprec = 2;
			} else
			{
				minprec = 2 - Math.floor(Math.log10(kelvin) + FUDGE); // 3 sigfigs in kelvin
			}
		} else
		{
			if (invalue == 0 || outvalue <= 0)
			{
				return recordDefaultPrecision(parms, outCurrent, 0);
			}
			if ("integer_more_precision".equals(outCurrent.exception) && Math.floor(invalue) == invalue)
			{
				adjust = -Math.log10(inCurrent.scale);
			} else if (subunitMorePrecision)
			{
				adjust = Math.log10(outCurrent.scale) + 2;
			} else
			{
				adjust = Math.log10(Math.abs(invalue / outvalue));
			}
			adjust += Math.log10(2);
			// ensure that the output has at least two significant figures
			minprec = 1 - Math.floor(Math.log10(outvalue) + FUDGE);
		}
		if (extra != null)
		{
			if (extra.adjust != null)
			{
				adjust = extra.adjust;
			}
			if (extra.minprec != null)
			{
				minprec = extra.minprec;
			}
		}
		return recordDefaultPrecision(parms, outCurrent, (int) Math.max(Math.floor(prec + adjust), minprec));
	}

	/**
	 * Makes all values of a range use the same (highest) default precision,
	 * which may require to repeat the conversion (record_default_precision()
	 * in Module:Convert).
	 */
	private static int recordDefaultPrecision(Parms parms, Unit outCurrent, int precision)
	{
		if (!parms.optRoundEach)
		{
			Integer maxdef = outCurrent.maxDefaultPrecision;
			if (maxdef != null)
			{
				if (maxdef < precision)
				{
					parms.doConvertAgain = true;
					outCurrent.maxDefaultPrecision = precision;
				} else
				{
					precision = maxdef;
				}
			} else
			{
				outCurrent.maxDefaultPrecision = precision;
			}
		}
		return precision;
	}

	/**
	 * Sets the texts for a table cell (disp=table) and the sort key
	 * (sortable=on) (make_table_or_sort() in Module:Convert). The sort key is
	 * based on the value in a fake base unit with scale 1.
	 */
	private static void makeTableOrSort(Parms parms, double invalue, Info info, Unit inCurrent)
	{
		String sortkey = null;
		if (parms.optSortableOn)
		{
			Unit base = Unit.unknown(null);
			base.scale = 1;
			base.invert = (inCurrent.invert != 0) ? 1 : 0;
			base.iscomplex = inCurrent.iscomplex;
			base.offset = (inCurrent.offset != null) ? Double.valueOf(0) : null;
			double outvalue = convertValue(parms, invalue, info, inCurrent, base).outvalue;
			if (inCurrent.istemperature && Math.abs(outvalue) < 1e-12)
			{
				outvalue = 0; // assume numbers close to zero have a rounding error
			}
			if (Double.isNaN(outvalue) || Double.isInfinite(outvalue))
			{
				sortkey = (outvalue < 0) ? "1000000000000000000" : "9000000000000000000";
			} else if (outvalue == 0)
			{
				sortkey = "5000000000000000000";
			} else
			{
				int mag = (int) Math.floor(Math.log10(Math.abs(outvalue)) + 1e-14);
				int prefix;
				if (outvalue > 0)
				{
					prefix = 7000 + mag;
				} else
				{
					prefix = 2999 - mag;
					outvalue = outvalue + Unit.pow10(mag + 1);
				}
				String digits = NumberFormater.toFixed(Math.floor(outvalue * Unit.pow10(14 - mag)), 0);
				StringBuilder sb = new StringBuilder().append(prefix);
				for (int k = digits.length(); k < 15; k++)
				{
					sb.append('0');
				}
				sortkey = sb.append(digits).toString();
			}
		}
		if (sortkey != null && parms.tableAlign == null)
		{
			parms.joinBefore = parms.optSortableDebug
					? "<span data-sort-value=\"" + sortkey + "♠\"><span style=\"border:1px solid\">"
							+ sortkey + "♠</span></span>"
					: "<span data-sort-value=\"" + sortkey + "♠\"></span>";
		}
		if (parms.tableAlign != null)
		{
			String sort = "";
			if (sortkey != null)
			{
				sort = " data-sort-value=\"" + sortkey + "\"";
				if (parms.optSortableDebug)
				{
					parms.joinBefore = "<span style=\"border:1px solid\">" + sortkey + "</span>";
				}
			}
			String style = "style=\"text-align:" + parms.tableAlign + ";";
			parms.tableJoins = new String[] {
					style + userStyle(parms.stylein) + "\"" + sort + "|",
					"\n|" + style + userStyle(parms.styleout) + "\"" + sort + "|" };
		}
	}

	private static String userStyle(String style)
	{
		if (style == null)
		{
			return "";
		}
		style = style.replace("\"", "");
		if (style.isEmpty())
		{
			return "";
		}
		return style.endsWith(";") ? style : style + ";";
	}

	// =========================================================================

	/**
	 * Determines the unit name or symbol (make_id() in Module:Convert) and
	 * sets the separator of the unit.
	 *
	 * @param which Index of the value.
	 */
	private static Id makeId(Parms parms, int which, Unit unit)
	{
		if (parms.optValues)
		{
			unit.sep = "";
			return new Id("", false);
		}
		final String inout = unit.inout;
		final Info info = unit.info(which);
		final String lk = parms.lk;
		boolean wantLink = "on".equals(lk) || inout.equals(lk);
		boolean singular = info.singular;
		Boolean wantName = null;
		if (unit.usename)
		{
			wantName = true;
		} else
		{
			if (parms.abbrOrg == null)
			{
				if (parms.wantname)
				{
					wantName = true;
				}
				if (unit.usesymbol)
				{
					wantName = false;
				}
			}
			if (wantName == null)
			{
				String abbr = parms.abbr;
				wantName = !("on".equals(abbr) || abbr.equals(inout));
			}
		}
		Key key;
		if (wantName)
		{
			if (lk == null && "hand".equals(unit.builtin))
			{
				wantLink = true;
			}
			unit.sep = " ";
			if (unit.engscale != null)
			{
				singular = false; // "|1|e3kg" gives "1 thousand kilograms"
			}
			key = (parms.optAdjectival || singular) ? Key.NAME1 : Key.NAME2;
			if (parms.optSpUs)
			{
				key = (key == Key.NAME1) ? Key.NAME1_US : Key.NAME2_US;
			}
		} else
		{
			if ("hand".equals(unit.builtin) && parms.optHandHh)
			{
				unit.symbol = "hh";
			}
			unit.sep = SYMBOL_SEP;
			key = parms.optSpUs ? Key.SYM_US : Key.SYMBOL;
		}
		return new Id(linkedId(parms, unit, key, wantLink), wantName);
	}

	/**
	 * Returns the final unit name or symbol, optionally with a link, and
	 * updates the separator of the unit if required (linked_id() in
	 * Module:Convert).
	 */
	private static String linkedId(Parms parms, Unit unit, Key key, boolean wantLink)
	{
		final boolean abbrOn = key.isSymbol();
		if (abbrOn && wantLink && unit.symlink != null)
		{
			return unit.symlink; // for exceptions that have the linked symbol built-in
		}
		if (unit.isPer())
		{
			String paren1 = "";
			String paren2 = "";
			final Unit unit1 = unit.per[0];
			final Unit unit2 = unit.per[1];
			if (abbrOn)
			{
				if (unit1 == null)
				{
					unit.sep = ""; // no separator in "$2/acre"
				}
				if (!wantLink && unit.symbolRaw != null)
				{
					return unit.symbolRaw; // for exceptions that have the symbol built-in
				}
				if (unit2.symbol().indexOf('⋅') >= 0)
				{
					paren1 = "(";
					paren2 = ")";
				}
			}
			Key key2; // unit2 is always singular
			if (key == Key.NAME2)
			{
				key2 = Key.NAME1;
			} else if (key == Key.NAME2_US)
			{
				key2 = Key.NAME1_US;
			} else
			{
				key2 = key;
			}
			String result;
			if (abbrOn)
			{
				result = "/";
			} else if (unit1 != null)
			{
				result = " per ";
			} else
			{
				result = "per ";
			}
			if (wantLink && unit.link != null)
			{
				result = ((unit1 != null) ? linkedId(parms, unit1, key, false) : "")
						+ result + linkedId(parms, unit2, key2, false);
				if (result.startsWith("/"))
				{
					unit.sep = "";
				}
				return makeLink(parms, unit.link, result, unit);
			}
			if (unit1 != null)
			{
				result = linkedId(parms, unit1, key, wantLink) + result;
				if (unit1.sep != null)
				{
					unit.sep = unit1.sep;
				}
			}
			return result + paren1 + linkedId(parms, unit2, key2, wantLink) + paren2;
		}
		String multiplier = "";
		if (unit.multiplier != null)
		{
			// a multiplier (like "100" in "100km") forces the unit to be plural
			multiplier = unit.multiplier + SYMBOL_SEP;
			if (key == Key.NAME1)
			{
				key = Key.NAME2;
			} else if (key == Key.NAME1_US)
			{
				key = Key.NAME2_US;
			}
		}
		String id = unit.get(key);
		if (id.startsWith("/"))
		{
			unit.sep = "";
		}
		if (wantLink)
		{
			String link = ConvertData.get().getLinkException((unit.linkey != null) ? unit.linkey : unit.symbol());
			if (link == null)
			{
				link = unit.link();
			}
			if (link != null)
			{
				String before = "";
				int i = unit.customary;
				if (i == 1 && parms.optSpUs)
				{
					i = 2; // show "U.S." not "US"
				}
				if (i == 3 && abbrOn)
				{
					i = 4; // abbreviate "imperial" to "imp"
				}
				if (i >= 1 && i <= ConvertData.CUSTOMARY_UNITS.length)
				{
					String[] customary = ConvertData.CUSTOMARY_UNITS[i - 1];
					String pertext = "";
					if (id.startsWith("/"))
					{
						// want "/USgal" to display as "/U.S. gal", not "U.S. /gal"
						pertext = "/";
						id = id.substring(1);
					} else if (id.startsWith("per "))
					{
						pertext = "per ";
						id = id.substring(4);
					}
					// omit any "US"/"U.S."/"imp"/"imperial" from start of id since that will be inserted
					String[] removes = (i < 3)
							? new String[] { "US&nbsp;", "US ", "U.S.&nbsp;", "U.S. " }
							: new String[] { "imp&nbsp;", "imp ", "imperial " };
					for (String prefix : removes)
					{
						if (id.startsWith(prefix))
						{
							id = id.substring(prefix.length());
							break;
						}
					}
					before = pertext + makeLink(parms, customary[1], customary[0], null) + " ";
				}
				id = before + makeLink(parms, link, id, unit);
			}
		}
		return multiplier + id;
	}

	/**
	 * Creates a link like "[[Foot (unit)|ft]]" or "[[metre]]s". A unit (or
	 * page, if no unit is given) is only linked once per conversion
	 * (make_link() in Module:Convert).
	 */
	private static String makeLink(Parms parms, String link, String id, Unit unit)
	{
		Object linkKey = (unit != null) ? ((unit.unitcode != null) ? unit.unitcode : unit) : link;
		if (link == null || link.isEmpty() || parms.linkedPages.contains(linkKey))
		{
			return id;
		}
		parms.linkedPages.add(linkKey);
		String l = Character.toLowerCase(link.charAt(0)) + link.substring(1);
		if (link.equals(id) || l.equals(id))
		{
			return "[[" + id + "]]";
		} else if ((link + "s").equals(id) || (l + "s").equals(id))
		{
			return "[[" + id.substring(0, id.length() - 1) + "]]s";
		}
		return "[[" + link + "|" + id + "]]";
	}

	/**
	 * Forgets that the unit was linked (when only the id of the last value of
	 * a range is shown).
	 */
	private static void unlink(Parms parms, Unit unit)
	{
		parms.linkedPages.remove((unit.unitcode != null) ? unit.unitcode : unit);
	}

	/**
	 * @return The text after a value: the id, possibly hyphenated, and the
	 * mid text of adj=mid; and a non-null second element if hyphenated
	 * (hyphenated_maybe() in Module:Convert).
	 */
	private static String[] hyphenatedMaybe(Parms parms, boolean wantName, String sep, String id, String inout)
	{
		if (id == null || id.isEmpty())
		{
			return new String[] { "", null };
		}
		String mid = (inout.equals(parms.optFlip ? OUT : IN) && parms.mid != null) ? parms.mid : "";
		if (wantName && parms.optAdjectival)
		{
			return new String[] { "-" + hyphenated(id) + mid, "" };
		}
		return new String[] { sep + id + mid, null };
	}

	/**
	 * Returns a hyphenated form of the given name for adjectival usage (e.g.
	 * "10-metre"). Link targets are not changed (hyphenated() in
	 * Module:Convert).
	 */
	static String hyphenated(String name)
	{
		if (name.indexOf(' ') < 0)
		{
			return name;
		}
		StringBuilder sb = new StringBuilder();
		Matcher m = HYPHENATE_RX.matcher(name);
		boolean found = false;
		while (m.find())
		{
			found = true;
			String item = m.group(2);
			if (item.indexOf(' ') >= 0)
			{
				String prefix;
				int bar = item.indexOf('|');
				if (bar >= 0)
				{
					prefix = item.substring(0, bar + 1);
					item = item.substring(bar + 1, item.length() - 2);
				} else
				{
					prefix = item.substring(0, item.length() - 2) + "|";
					item = item.substring(2, item.length() - 2);
				}
				item = prefix + hyphenatedText(item) + "]]";
			}
			sb.append(m.group(1).replace(' ', '-')).append(item).append(m.group(3).replace(' ', '-'));
		}
		if (!found)
		{
			return hyphenatedText(name);
		}
		return sb.toString();
	}

	private static String hyphenatedText(String name)
	{
		if (name.indexOf(' ') < 0)
		{
			return name;
		}
		// not a space after ')' as in "(pre-1954 US) nautical mile" and not
		// the spaces in "British thermal unit (ISO)"
		if (name.startsWith("("))
		{
			int pos = name.indexOf(')');
			if (pos >= 0)
			{
				int end = Math.min(pos + 2, name.length());
				return name.substring(0, end) + name.substring(end).replace(' ', '-');
			}
		} else if (name.endsWith(")"))
		{
			int pos = name.indexOf('(');
			if (pos >= 0)
			{
				return name.substring(0, Math.max(pos - 1, 0)).replace(' ', '-') + name.substring(Math.max(pos - 1, 0));
			}
		}
		return name.replace(' ', '-');
	}

	// =========================================================================

	/**
	 * The options and state of one invocation ("parms" in Module:Convert).
	 */
	static final class Parms
	{
		final NumberFormater.Options options = new NumberFormater.Options();

		/** "on", "off", "in" or "out" (after the options were resolved). */
		String abbr;

		/** The abbreviation mode as given (before disp=flip), or null. */
		String abbrOrg;

		/** abbr=unit: use words for engineering notation. */
		boolean numberWord;

		boolean optValues;

		boolean optAlsoSymbol;

		boolean optHandHh;

		boolean optAdjectival;

		boolean optAdjMid;

		boolean optOnePreunit;

		/** Precision to round input values (adj=ri0 to ri3), or null. */
		Integer optRi;

		String lk;

		boolean optSpUs;

		String currencyText;

		Double altitudeFt;

		Double altitudeM;

		Integer sigfig;

		Integer precision;

		/** True if a precision was given (even if it is invalid). */
		boolean hasPrecision;

		Double optRound;

		boolean optRoundEach;

		boolean optFlip;

		boolean optOrderOut;

		Integer frac;

		boolean optFractionHorizontal;

		boolean optSpellIn;

		boolean optSpellOut;

		boolean optSpellUpper;

		/** "in" or "out" for the value spelled with an uppercase letter. */
		String spellUpper;

		boolean optSortableOn;

		boolean optSortableDebug;

		boolean optTable;

		boolean optTablecen;

		String tableAlign;

		String stylein;

		String styleout;

		boolean needTableOrSort;

		String[] tableJoins;

		/** Text before the result (the sort key). */
		String joinBefore;

		String disp;

		boolean optOutputOnly;

		boolean optOutputNumberOnly;

		boolean optInputUnitOnly;

		boolean optOutputUnitOnly;

		boolean optIgnoreError;

		boolean optTwoPreunits;

		String errorText;

		/** Use names when no abbreviation mode was given (e.g. disp=or). */
		boolean wantname;

		String[] joins;

		String joinBetween;

		String outUnit;

		String mid;

		String preunit1;

		String preunit2;

		List<Range> range;

		boolean outRangeX;

		boolean abbrRangeX;

		/** An input value used e-notation. */
		boolean optScientific;

		boolean doConvertAgain;

		/** The input unit is not known (with disp=unit or text). */
		boolean badInput;

		/** Index of the positional argument after the input values. */
		int nextArg;

		/** Units (or pages) which were already linked. */
		Set<Object> linkedPages = new HashSet<Object>();
	}

	/**
	 * An input or output value ("valinfo" in Module:Convert).
	 */
	static final class Info
	{
		double value;

		/** The value for the hand unit (see {@link ParsedNumber#getAltValue()}). */
		double altvalue;

		/** True if a unit name after this value is singular. */
		boolean singular;

		/** The unsigned value without separators (used for the precision). */
		String clean;

		/** The value formatted for display. */
		String show;

		/** The denominator if the value is a fraction, otherwise 0. */
		int denominator;

		/** The value is a change (after "±"). */
		boolean isChange;

		boolean decorated;

		/** The sign of an output value: "" or "−". */
		String sign = "";

		Integer exponent;

		boolean isScientific;

		FractionTable fractionTable;

		/** The absolute output value before rounding. */
		double rawAbsvalue;

		Double absvalueOverride;

		/**
		 * @return The absolute output value after rounding.
		 */
		double absvalue()
		{
			if (absvalueOverride != null)
			{
				return absvalueOverride;
			}
			double v = Double.parseDouble(clean);
			if (exponent != null)
			{
				v = v * Unit.pow10(exponent);
			}
			return v;
		}
	}

	/**
	 * An output value as a fraction.
	 */
	private static final class FractionTable
	{
		String wholestr;

		String numstr;

		String denstr;

		double value;
	}

	/**
	 * Result of a conversion with additional information for some built-in
	 * units.
	 */
	private static final class Converted
	{
		final double outvalue;

		final Extra extra;

		Converted(double outvalue, Extra extra)
		{
			this.outvalue = outvalue;
			this.extra = extra;
		}
	}

	private static final class Extra
	{
		Double invalue;

		String clean;

		String show;

		Double adjust;

		Double minprec;
	}

	/**
	 * Joins of the input and output (disp_joins in Module:Convert/text).
	 */
	private static final class Joins
	{
		final String before;

		final String after;

		/** The text between outputs of a combination or null for the default. */
		final String between;

		final boolean wantName;

		Joins(String before, String after, String between, boolean wantName)
		{
			this.before = before;
			this.after = after;
			this.between = between;
			this.wantName = wantName;
		}
	}

	/**
	 * The text between the values of a range.
	 */
	private static final class Range
	{
		/** The text, or null if it depends on the use of names or on the side. */
		final String text;

		final String off;

		final String on;

		final String input;

		final String output;

		final String adj;

		final boolean exception;

		final boolean isChange;

		final boolean outRangeX;

		final boolean abbrRangeX;

		Range(String text)
		{
			this(text, null, null, null, null, null, false, false, false, false);
		}

		Range(
				String off,
				String on,
				String input,
				String output,
				String adj,
				boolean exception,
				boolean isChange,
				boolean outRangeX,
				boolean abbrRangeX)
		{
			this(null, off, on, input, output, adj, exception, isChange, outRangeX, abbrRangeX);
		}

		private Range(
				String text,
				String off,
				String on,
				String input,
				String output,
				String adj,
				boolean exception,
				boolean isChange,
				boolean outRangeX,
				boolean abbrRangeX)
		{
			this.text = text;
			this.off = off;
			this.on = on;
			this.input = input;
			this.output = output;
			this.adj = adj;
			this.exception = exception;
			this.isChange = isChange;
			this.outRangeX = outRangeX;
			this.abbrRangeX = abbrRangeX;
		}
	}

	/**
	 * A unit name or symbol.
	 */
	private static final class Id
	{
		final String text;

		/** True if text is a name, false if it is a symbol. */
		final boolean name;

		Id(String text, boolean name)
		{
			this.text = text;
			this.name = name;
		}
	}
}
