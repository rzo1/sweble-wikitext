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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.ParserFunctionBase;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.ext.convert.NumberFormater.FormattedNumber;
import org.sweble.wikitext.engine.ext.convert.NumberFormater.ParsedNumber;
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
 * The implementation follows Module:Convert of the English Wikipedia. Only one
 * instance of this parser function exists per configuration and it is shared
 * by all threads. Therefore all state of an invocation is kept in a
 * {@link Context} object.
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
	 * Separator between a value and a unit symbol. Wikipedia uses "&amp;nbsp;"
	 * which is written as a plain space here.
	 */
	private static final String SYMBOL_SEP = " ";

	private static final Pattern NUMBER_RX =
			Pattern.compile("[+-]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?");

	private static final Pattern CLEAN_RX =
			Pattern.compile("^(\\d*)(\\.?)(\\d*)(.*)$");

	private static final Pattern SPACED_RANGE_RX =
			Pattern.compile("^(\\S+)\\s+(\\S+)\\s+(\\S.*)");

	private static final Pattern LINK_RX =
			Pattern.compile("\\[\\[([^\\[\\]|]+)(?:\\|([^\\[\\]]*))?\\]\\]([a-z]*)");

	/**
	 * The different abbreviation modes:
	 *
	 * <pre>
	 * "out" (def): 2 metres (6 ft 7 in)
	 * "on"       : 2 m (6 ft 7 in)
	 * "unit"     : 2 m (6 ft 7 in)
	 * "in"       : 2 m (6 feet 7 inches)
	 * "none"     : 2 metres (6 feet 7 inches)
	 * "off"      : 2 metres (6 feet 7 inches)
	 * "values"   : 2 (6 ft 7 in)
	 * "~"        : 2 metres [m] (6 ft 7 in)
	 * </pre>
	 */
	static enum AbbreviationMode {OUT, ON, UNIT, IN, NONE, OFF, VALUES, TILDE};

	/** The different modes of the "lk" option. */
	private static enum LinkMode {ON, IN, OUT, OFF};

	/** Output units which show a value in multiple units (e.g. "5 ft 6 in"). */
	private static final Map<String, String[]> MULTIPLES = new HashMap<String, String[]>();

	/** Words which separate the values of a range (e.g. "5 to 10"). */
	private static final Map<String, Range> RANGES = new HashMap<String, Range>();

	/** Range words which are also accepted without spaces (e.g. "5-10"). */
	private static final String[] RANGE_WORDS = { "-", "–", "xx", "x", "*" };

	static
	{
		// see https://en.wikipedia.org/wiki/Module:Convert/data
		MULTIPLES.put("ftin", new String[]{"ft", "in"});
		MULTIPLES.put("ydft", new String[]{"yd", "ft"});
		MULTIPLES.put("ydftin", new String[]{"yd", "ft", "in"});
		MULTIPLES.put("stlb", new String[]{"st", "lb"});
		MULTIPLES.put("lboz", new String[]{"lb", "oz"});

		// see https://en.wikipedia.org/wiki/Module:Convert/text
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
	 * Converts the given arguments.
	 *
	 * @param args The expanded arguments.
	 * @return The result as wikitext (which may contain internal links).
	 * @throws IllegalArgumentException If the arguments cannot be converted.
	 */
	static String convert(List<String> args) throws IllegalArgumentException
	{
		final Context ctx = new Context();
		final ArrayList<String> parms = new ArrayList<String>(args.size());
		final Map<String, String> options = new HashMap<String, String>();
		for (String arg : args)
		{
			if (arg.contains("="))
			{
				final String[] spl = arg.split("=", 2);
				options.put(spl[0].trim(), spl[1].trim());
			} else
			{
				parms.add(arg.trim());
			}
		}
		parseOptions(ctx, options);

		final Input in = parseInput(ctx, parms);

		// output unit and precision
		int i = in.next;
		String outCode = null;
		String word = get(parms, i++);
		if (word != null && !setPrecision(ctx, word))
		{
			outCode = word;
			if (setPrecision(ctx, get(parms, i)))
			{
				i++;
			}
		}
		// additional positional arguments are ignored (as by Module:Convert)

		final List<Output> outputs;
		if (outCode == null || outCode.isEmpty())
		{
			outputs = defaultOutputs(in);
		} else
		{
			outputs = lookupOutputs(outCode);
			if (outputs == null)
			{
				throw new IllegalArgumentException("Unit name \"" + outCode + "\" is not known");
			}
		}
		for (Output output : outputs)
		{
			for (Units unit : output.units)
			{
				if (!Units.isSameUnitType(in.unit, unit))
				{
					throw new IllegalArgumentException("Cannot convert \""
							+ in.unit.getTypeName() + "\" to \"" + unit.getTypeName() + "\"");
				}
			}
		}

		String lhs = null;
		String rhs;
		if (ctx.inputUnitOnly)
		{
			rhs = "";
		} else
		{
			if (!ctx.flip)
			{
				// process the input first so it gets linked first
				lhs = processInput(ctx, in);
			}
			List<String> items = new ArrayList<String>(outputs.size());
			for (Output output : outputs)
			{
				if (output.isMultiple())
				{
					items.add(processMultipleOutput(ctx, in, output));
				} else
				{
					items.add(processOutput(ctx, in, output.units[0]));
				}
			}
			rhs = String.join(ctx.joinBetween, items);
		}

		if (ctx.flip || lhs == null)
		{
			String input = processInput(ctx, in);
			if (ctx.flip)
			{
				lhs = rhs;
				rhs = input;
			} else
			{
				lhs = input;
			}
		}
		return lhs + ctx.join1 + rhs + ctx.join2;
	}

	/**
	 * Resolves the named options. Unknown options and invalid or empty values
	 * are ignored like Module:Convert does (it shows a warning in preview
	 * only).
	 */
	private static void parseOptions(Context ctx, Map<String, String> options)
	{
		final String abbr = options.get("abbr");
		if ("in".equals(abbr))
		{
			ctx.abbr = AbbreviationMode.IN;
		} else if ("out".equals(abbr))
		{
			ctx.abbr = AbbreviationMode.OUT;
		} else if ("on".equals(abbr) || "unit".equals(abbr) || "h".equals(abbr) || "hh".equals(abbr))
		{
			ctx.abbr = AbbreviationMode.ON;
		} else if ("off".equals(abbr) || "none".equals(abbr))
		{
			ctx.abbr = AbbreviationMode.OFF;
		} else if ("values".equals(abbr))
		{
			ctx.values = true;
		} else if ("~".equals(abbr))
		{
			ctx.alsoSymbol = true;
		}
		ctx.abbrOrg = ctx.abbr;

		String adj = options.get("adj");
		if (adj == null)
		{
			adj = options.get("sing"); // old alias
		}
		if ("on".equals(adj))
		{
			ctx.adjectival = true;
		} else if ("~".equals(adj))
		{
			ctx.alsoSymbol = true;
		}

		String lk = options.get("lk");
		if (lk == null)
		{
			lk = options.get("link");
		}
		if ("on".equals(lk))
		{
			ctx.link = LinkMode.ON;
		} else if ("in".equals(lk))
		{
			ctx.link = LinkMode.IN;
		} else if ("out".equals(lk))
		{
			ctx.link = LinkMode.OUT;
		} else if ("off".equals(lk))
		{
			ctx.link = LinkMode.OFF;
		}

		ctx.usSpelling = "us".equals(options.get("sp"));

		final String sigFig = options.get("sigfig");
		if (sigFig != null)
		{
			Double number = getNumber(sigFig);
			if (number != null && number == Math.floor(number) && number >= 1)
			{
				ctx.sigFig = (int) Math.min(number, Integer.MAX_VALUE);
			}
		}

		final String round = options.get("round");
		if ("each".equals(round))
		{
			ctx.roundEach = true;
		} else if ("0.5".equals(round) || "5".equals(round) || "10".equals(round)
				|| "25".equals(round) || "50".equals(round))
		{
			ctx.round = Double.valueOf(round);
		}

		if ("flip".equals(options.get("order")))
		{
			ctx.flip = true;
		}

		final String disp = options.get("disp");
		if ("or".equals(disp))
		{
			setJoins(ctx, " or ", "", " or ", true);
			ctx.dispWantName = true;
		} else if ("(or)".equals(disp))
		{
			setJoins(ctx, " (", ")", " or ", false);
		} else if ("comma".equals(disp))
		{
			setJoins(ctx, ", ", "", ", ", false);
		} else if ("semicolon".equals(disp))
		{
			setJoins(ctx, "; ", "", null, false);
		} else if ("sqbr".equals(disp))
		{
			setJoins(ctx, " [", "]", null, false);
		} else if ("flip".equals(disp))
		{
			ctx.flip = true;
		} else if ("number".equals(disp) || "output number only".equals(disp))
		{
			ctx.outputNumberOnly = true;
		} else if ("out".equals(disp) || "output only".equals(disp))
		{
			ctx.outputOnly = true;
		} else if ("unit".equals(disp))
		{
			ctx.inputUnitOnly = true;
		} else if ("unit2".equals(disp))
		{
			ctx.outputUnitOnly = true;
		} else if ("5".equals(disp))
		{
			ctx.round = 5d;
		}

		if (ctx.flip)
		{
			// the default abbreviation mode "out" is swapped as well
			if (ctx.abbr() == AbbreviationMode.IN)
			{
				ctx.abbr = AbbreviationMode.OUT;
			} else if (ctx.abbr() == AbbreviationMode.OUT)
			{
				ctx.abbr = AbbreviationMode.IN;
			}
			if (ctx.link == LinkMode.IN)
			{
				ctx.link = LinkMode.OUT;
			} else if (ctx.link == LinkMode.OUT)
			{
				ctx.link = LinkMode.IN;
			}
		}
	}

	private static void setJoins(
			Context ctx,
			String join1,
			String join2,
			String joinBetween,
			boolean wantName)
	{
		ctx.join1 = join1;
		ctx.join2 = join2;
		if (joinBetween != null)
		{
			ctx.joinBetween = joinBetween;
		}
		ctx.wantName = wantName;
	}

	/**
	 * Sets the precision if the given text is a number.
	 *
	 * @return True if the text was used for the precision (even if the
	 * precision is invalid and ignored).
	 */
	private static boolean setPrecision(Context ctx, String text)
	{
		Double number = (text != null) ? getNumber(text) : null;
		if (number == null)
		{
			return false;
		}
		if (number == Math.floor(number))
		{
			ctx.precision = (int) Math.max(-1000, Math.min(1000, number));
		}
		return true;
	}

	/**
	 * @return The value of the text if it is a number (no fraction and no
	 * Unicode minus), otherwise null.
	 */
	private static Double getNumber(String text)
	{
		String clean = text.trim().replace(",", "");
		if (!NUMBER_RX.matcher(clean).matches())
		{
			return null;
		}
		return Double.parseDouble(clean);
	}

	private static String get(List<String> parms, int index)
	{
		return (index < parms.size()) ? parms.get(index) : null;
	}

	// =========================================================================

	/**
	 * Parses the input values, range words and input units.
	 */
	private static Input parseInput(Context ctx, ArrayList<String> parms)
	{
		final Input in = new Input();

		int i = 0;
		boolean isChange = false;
		while (true)
		{
			ParsedNumber value = extractValue(parms, i);
			ctx.scientific |= value.isScientific();
			in.values.add(value);
			in.changes.add(isChange);
			i++;

			Range range = RANGES.get(get(parms, i));
			if (range == null)
			{
				break;
			}
			i++;
			in.ranges.add(range);
			ctx.outRangeX |= range.outRangeX;
			ctx.abbrRangeX |= range.abbrRangeX;
			isChange = range.isChange;
			if (in.ranges.size() > 30)
			{
				throw new IllegalArgumentException("Number has overflowed");
			}
		}

		final String unitCode = get(parms, i++);
		if (unitCode == null || unitCode.isEmpty())
		{
			throw new IllegalArgumentException("Needs name of unit");
		}
		in.unit = Units.searchUnitFromName(unitCode);
		if (in.unit == null)
		{
			throw new IllegalArgumentException("Unit name \"" + unitCode + "\" is not known");
		}

		if (in.ranges.isEmpty())
		{
			// multi-unit input like {{convert|5|ft|6|in}}
			List<Units> units = new ArrayList<Units>();
			List<ParsedNumber> subValues = new ArrayList<ParsedNumber>();
			units.add(in.unit);
			subValues.add(in.values.get(0));
			double total = in.values.get(0).getValue();
			Units subunit = in.unit;
			while (true)
			{
				String subCode = get(parms, i + 1);
				Units candidate = (subCode != null) ? Units.searchUnitFromName(subCode) : null;
				int count = (candidate != null) ? subunit.getSubdivisions(candidate) : 0;
				if (count == 0)
				{
					break;
				}
				ParsedNumber subValue = NumberFormater.parseValue(parms.get(i));
				ctx.scientific |= subValue.isScientific();
				i += 2;
				total = total * count + subValue.getValue();
				units.add(candidate);
				subValues.add(subValue);
				subunit = candidate;
			}
			if (units.size() > 1)
			{
				in.units = units;
				in.subValues = subValues;
				in.total = total;
			}
		}

		in.next = i;
		return in;
	}

	/**
	 * Parses a value. If the argument is not a value, it is unpacked as a range
	 * if possible (e.g. "1 to 2" or "1-2").
	 */
	private static ParsedNumber extractValue(ArrayList<String> parms, int i)
	{
		final String valStr = get(parms, i);
		if (valStr == null || valStr.isEmpty())
		{
			throw new IllegalArgumentException((i > 0)
					? "Needs another number for a range"
					: "Needs the number to be converted");
		}

		try
		{
			return NumberFormater.parseValue(valStr);
		} catch (NumberFormatException ex)
		{
			if (i < 20)
			{
				Matcher m = SPACED_RANGE_RX.matcher(valStr);
				if (m.find() && !(m.group(2).equals("-") && m.group(3).contains("/")))
				{
					if (m.group(2).matches(".*\\d.*"))
					{
						throw ex;
					}
					parms.set(i, m.group(3));
					parms.add(i, m.group(2));
					parms.add(i, m.group(1));
					return extractValue(parms, i);
				}
				if (!valStr.matches("(?s).*-.*/.*"))
				{
					for (String sep : RANGE_WORDS)
					{
						int start = valStr.indexOf(sep, 1);
						if (start >= 0)
						{
							parms.set(i, valStr.substring(start + sep.length()));
							parms.add(i, sep);
							parms.add(i, valStr.substring(0, start));
							return extractValue(parms, i);
						}
					}
				}
			}
			throw ex;
		}
	}

	/**
	 * Gets the default output units of the input unit.
	 */
	private static List<Output> defaultOutputs(Input in)
	{
		final DefCvt defCvt = in.defaultCvt();
		final String[] codes = defCvt.getUnits();
		final List<Output> outputs = new ArrayList<Output>(codes.length);
		if (defCvt.isMixedNotation())
		{
			final double value = in.value(0);
			if (value > 0 && value < defCvt.getMixedNotationLimit())
			{
				outputs.add(Output.multiple(codes));
			} else
			{
				outputs.add(new Output(lookupDefault(in, codes[0])));
			}
		} else
		{
			for (String code : codes)
			{
				outputs.add(new Output(lookupDefault(in, code)));
			}
		}
		return outputs;
	}

	private static Units lookupDefault(Input in, String code)
	{
		Units unit = Units.searchUnitFromName(code);
		if (unit == null)
		{
			throw new IllegalArgumentException("Unit \"" + in.unit.getSymbol() + "\" has an invalid default");
		}
		return unit;
	}

	/**
	 * Looks up an output unit, a multiple output unit (like "ftin") or a
	 * combination of output units (like "km mi" or "km+mi").
	 *
	 * @return The output units or null if a unit is not known.
	 */
	private static List<Output> lookupOutputs(String code)
	{
		Output output = lookupOutput(code);
		if (output != null)
		{
			return Collections.singletonList(output);
		}

		String[] items = code.contains("+") ? code.split("\\+") : code.split("\\s+");
		List<Output> outputs = new ArrayList<Output>(items.length);
		for (String item : items)
		{
			if (item.trim().isEmpty())
			{
				continue;
			}
			output = lookupOutput(item.trim());
			if (output == null)
			{
				return null;
			}
			outputs.add(output);
		}
		return (outputs.size() > 1) ? outputs : null;
	}

	private static Output lookupOutput(String code)
	{
		String[] multiple = MULTIPLES.get(code);
		if (multiple != null)
		{
			return Output.multiple(multiple);
		}
		Units unit = Units.searchUnitFromName(code);
		return (unit != null) ? new Output(unit) : null;
	}

	// =========================================================================

	/**
	 * @return The input part of the result (e.g. "5 metres").
	 */
	private static String processInput(Context ctx, Input in)
	{
		if (ctx.outputOnly || ctx.outputNumberOnly || ctx.outputUnitOnly)
		{
			ctx.join1 = "";
			ctx.join2 = "";
			return "";
		}

		final Units first = in.unit;
		final boolean firstSingular = in.isComposite()
				? in.subValues.get(0).isSingular()
				: in.values.get(0).isSingular();
		final Id id1 = makeId(ctx, first, true, firstSingular);

		if (ctx.inputUnitOnly)
		{
			ctx.join1 = "";
			ctx.join2 = "";
			String text = id1.text;
			if (in.isComposite())
			{
				for (int k = 1; k < in.units.size(); k++)
				{
					text += " " + makeId(ctx, in.units.get(k), true, in.subValues.get(k).isSingular()).text;
				}
			}
			return (id1.name && ctx.adjectival) ? hyphenated(text) : text;
		}

		if (ctx.alsoSymbol && !in.isComposite() && !ctx.flip
				&& (ctx.join1.equals(" (") || ctx.join1.equals(" [")))
		{
			ctx.join1 = " [" + first.getSymbol(ctx.usSpelling) + "]" + ctx.join1;
		}

		if (in.isComposite())
		{
			String sep1 = SYMBOL_SEP;
			String sep2 = " ";
			if (ctx.adjectival && id1.name)
			{
				sep1 = "-";
				sep2 = "-";
			}
			StringBuilder sb = new StringBuilder();
			sb.append(in.subValues.get(0).getShow()).append(sep1).append(id1.text);
			for (int k = 1; k < in.units.size(); k++)
			{
				ParsedNumber subValue = in.subValues.get(k);
				sb.append(sep2).append(subValue.getShow()).append(sep1)
						.append(makeId(ctx, in.units.get(k), true, subValue.isSingular()).text);
			}
			return sb.toString();
		}

		final int n = in.values.size() - 1;
		boolean addUnit = (ctx.flip && ctx.outRangeX) || (!id1.name && ctx.abbrRangeX);
		if (n > 0 && !addUnit)
		{
			ctx.linked.remove(first);
		}
		final Id id = (n > 0) ? makeId(ctx, first, true, in.values.get(n).isSingular()) : id1;
		final boolean hyphenated = id.name && ctx.adjectival && !id.text.isEmpty();
		if (hyphenated)
		{
			addUnit = false;
		}

		String result = in.values.get(0).getShow();
		if (addUnit && n > 0)
		{
			result += id1.sep + id1.text;
		}
		for (int k = 1; k <= n; k++)
		{
			String show = in.values.get(k).getShow();
			if (addUnit && k < n)
			{
				Id idK = makeId(ctx, first, true, in.values.get(k).isSingular());
				show += idK.sep + idK.text;
			}
			result = rangeText(ctx, in.ranges.get(k - 1), id1.name, result, show, true, false);
		}
		return result + extra(ctx, id);
	}

	/**
	 * @return The part of the result for a single output unit (e.g. "16 ft").
	 */
	private static String processOutput(Context ctx, Input in, Units out)
	{
		final FormattedNumber[] values = roundAll(ctx, in, out).numbers;
		final Id id1 = makeId(ctx, out, false, values[0].isSingular());
		if (ctx.outputUnitOnly)
		{
			return (id1.name && ctx.adjectival) ? hyphenated(id1.text) : id1.text;
		}

		final int n = values.length - 1;
		boolean addUnit = ((!ctx.flip && ctx.outRangeX) || (!id1.name && ctx.abbrRangeX))
				&& !ctx.outputNumberOnly;
		if (n > 0 && !addUnit)
		{
			ctx.linked.remove(out);
		}
		final Id id = (n > 0) ? makeId(ctx, out, false, values[n].isSingular()) : id1;
		final boolean hyphenated = id.name && ctx.adjectival && !id.text.isEmpty();
		if (hyphenated)
		{
			addUnit = false;
		}

		String result = values[0].getShow();
		if (addUnit && n > 0)
		{
			result += id1.sep + id1.text;
		}
		for (int k = 1; k <= n; k++)
		{
			String show = values[k].getShow();
			if (addUnit && k < n)
			{
				Id idK = makeId(ctx, out, false, values[k].isSingular());
				show += idK.sep + idK.text;
			}
			result = rangeText(ctx, in.ranges.get(k - 1), id1.name, result, show, false, false);
		}

		if (ctx.outputNumberOnly)
		{
			return result;
		}
		return result + extra(ctx, id);
	}

	/**
	 * @return The part of the result for an output in multiple units (e.g.
	 * "5 ft 6 in"). The value is rounded in the least significant unit, then
	 * split into the units and the sign is applied once.
	 */
	private static String processMultipleOutput(Context ctx, Input in, Output out)
	{
		final Units least = out.units[out.units.length - 1];
		final Rounded rounded = roundAll(ctx, in, least);

		final AbbreviationMode abbr = ctx.abbr();
		final boolean wantName = (ctx.abbrOrg == null && ctx.dispWantName)
				|| !(abbr == AbbreviationMode.ON || abbr == AbbreviationMode.OUT);
		final boolean wantLink = (ctx.link == LinkMode.ON || ctx.link == LinkMode.OUT);
		String sep1 = SYMBOL_SEP;
		String sep2 = " ";
		if (ctx.adjectival && wantName)
		{
			sep1 = "-";
			sep2 = "-";
		}

		String result = null;
		for (int k = 0; k < rounded.numbers.length; k++)
		{
			String item = makeMultiple(ctx, out, rounded.numbers[k], rounded.raw[k], wantName, wantLink, sep1, sep2);
			if (k == 0)
			{
				result = item;
			} else
			{
				result = rangeText(ctx, in.ranges.get(k - 1), wantName, result, item, false, true);
			}
		}
		return result;
	}

	private static String makeMultiple(
			Context ctx,
			Output out,
			FormattedNumber info,
			double rawAbsValue,
			boolean wantName,
			boolean wantLink,
			String sep1,
			String sep2)
	{
		String sign = info.getShow().startsWith(MINUS) ? MINUS : "";
		String strForce = null;
		int decimals = 0;
		if (info.isScientific())
		{
			strForce = info.getShow();
		} else
		{
			decimals = info.getDecimals();
		}

		double outValue;
		if (decimals == 0)
		{
			// keep all integer digits of least significant unit
			outValue = Math.floor(rawAbsValue + 0.5);
		} else
		{
			outValue = info.getAbsValue();
		}

		final List<String> results = new ArrayList<String>();
		for (int idx = out.units.length - 1; idx >= 0; idx--)
		{
			final Units unit = out.units[idx];
			double thisValue;
			if (idx > 0)
			{
				int scale = out.factors[idx - 1];
				double quotient = Math.floor(outValue / scale);
				thisValue = outValue - quotient * scale;
				if (!(0 <= thisValue && thisValue < scale))
				{
					thisValue = 0;
				}
				outValue = quotient;
			} else
			{
				thisValue = outValue;
			}

			String id;
			if (wantName)
			{
				boolean singular = ctx.adjectival || thisValue == 1;
				id = unit.getUnitName(!singular, false);
			} else
			{
				id = unit.getSymbol();
			}
			if (wantLink)
			{
				id = makeLink(ctx, unit.getLink(), id, unit);
			}

			String strVal;
			if (strForce != null && outValue == 0)
			{
				sign = ""; // any sign is in strForce
				strVal = strForce;
			} else if (thisValue == 0)
			{
				strVal = "0";
			} else
			{
				strVal = NumberFormater.withSeparator(NumberFormater.toFixed(thisValue, decimals));
			}
			results.add(strVal + sep1 + id);
			if (outValue == 0)
			{
				break;
			}
			decimals = 0; // only least significant unit can have a non-integral value
		}

		Collections.reverse(results);
		return sign + String.join(sep2, results);
	}

	private static String extra(Context ctx, Id id)
	{
		if (id.text.isEmpty())
		{
			return "";
		}
		if (id.name && ctx.adjectival)
		{
			return "-" + hyphenated(id.text);
		}
		return id.sep + id.text;
	}

	/**
	 * @return The text of a range (e.g. "5 to 10").
	 */
	private static String rangeText(
			Context ctx,
			Range range,
			boolean wantName,
			String before,
			String after,
			boolean isInput,
			boolean spaced)
	{
		String rText;
		if (range.text != null)
		{
			rText = range.text;
		} else
		{
			rText = wantName ? range.off : range.on;
			if (rText == null)
			{
				rText = (isInput == ctx.flip) ? range.output : range.input;
			}
		}

		if (ctx.adjectival
				&& (wantName || (range.exception && ctx.abbrOrg == AbbreviationMode.ON)))
		{
			rText = (range.adj != null) ? range.adj : rText.replace(' ', '-');
		}

		if (rText.equals("–") && (spaced || after.startsWith(MINUS)))
		{
			rText = " – ";
		}
		return before + rText + after;
	}

	// =========================================================================

	/**
	 * Converts and rounds all input values into the given unit.
	 */
	private static Rounded roundAll(Context ctx, Input in, Units out)
	{
		final int n = in.values.size();
		final double[] outValues = new double[n];
		for (int k = 0; k < n; k++)
		{
			outValues[k] = convertValue(in, k, out);
			if (Double.isNaN(outValues[k]) || Double.isInfinite(outValues[k]))
			{
				throw new IllegalArgumentException("Number has overflowed");
			}
		}

		final int[] precisions = new int[n];
		if (ctx.precision == null && ctx.sigFig == null && ctx.round == null)
		{
			// Like Module:Convert, all values of a range use the same (highest)
			// default precision.
			int max = Integer.MIN_VALUE;
			for (int k = 0; k < n; k++)
			{
				precisions[k] = defaultPrecision(in, k, Math.abs(outValues[k]), out);
				max = Math.max(max, precisions[k]);
			}
			if (!ctx.roundEach)
			{
				for (int k = 0; k < n; k++)
				{
					precisions[k] = max;
				}
			}
		}

		final Rounded rounded = new Rounded(n);
		for (int k = 0; k < n; k++)
		{
			rounded.numbers[k] = round(ctx, outValues[k], precisions[k]);
			rounded.raw[k] = Math.abs(outValues[k]);
		}
		return rounded;
	}

	private static FormattedNumber round(Context ctx, double value, int defaultPrecision)
	{
		if (ctx.precision != null)
		{
			return NumberFormater.formatRounded(value, ctx.precision, ctx.scientific);
		}
		if (ctx.sigFig != null)
		{
			return NumberFormater.formatSigFig(value, ctx.sigFig, ctx.scientific);
		}
		if (ctx.round != null)
		{
			final double absValue = Math.abs(value);
			final double n = ctx.round;
			String show;
			if (n == 0.5)
			{
				double rounded = Math.floor(2 * absValue + 0.5) / 2;
				show = NumberFormater.toFixed(rounded, (rounded == Math.floor(rounded)) ? 0 : 1);
			} else
			{
				show = NumberFormater.toFixed(Math.floor(absValue / n + 0.5) * n, 0);
			}
			return NumberFormater.formatShow(show, null, value < 0, ctx.scientific);
		}
		return NumberFormater.formatRounded(value, defaultPrecision, ctx.scientific);
	}

	/**
	 * Converts an input value into the given unit. Temperatures are converted
	 * with their offsets, unless the value is a change (e.g. "20 ± 5 °C").
	 */
	private static double convertValue(Input in, int k, Units out)
	{
		final Units inUnit = in.scaleUnit();
		final double inValue = in.value(k);
		if (inUnit.isTemperature() && !in.changes.get(k))
		{
			return (inValue - inUnit.getOffset()) * (inUnit.getScale() / out.getScale()) + out.getOffset();
		}
		return inValue * (inUnit.getScale() / out.getScale());
	}

	/**
	 * Calculates the default precision (default_precision() in
	 * Module:Convert). It depends on the number of significant figures of the
	 * input value and on the conversion factor.
	 *
	 * @return The precision (digits after the decimal mark, or if negative,
	 * digits before the decimal mark set to zero).
	 */
	static int defaultPrecision(Input in, int k, double outValue, Units out)
	{
		final ParsedNumber info = in.isComposite()
				? in.subValues.get(in.subValues.size() - 1)
				: in.values.get(k);
		final Units inUnit = in.scaleUnit();
		final double inValue = in.value(k);

		double prec;
		if (info.getDenominator() > 0)
		{
			prec = Math.max(Math.log10(info.getDenominator()), 1);
		} else
		{
			// count digits after decimal mark, handling cases like '12.345e6'
			Matcher m = CLEAN_RX.matcher(info.getClean());
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
				prec = in.isComposite() ? 0 : -zeros;
			} else
			{
				prec = m.group(3).length();
			}
			if (rest.startsWith("e") || rest.startsWith("E"))
			{
				prec -= Integer.parseInt(rest.substring(1));
			}
		}

		double adjust;
		double minPrec;
		if (inUnit.isTemperature() && out.isTemperature())
		{
			adjust = 0;
			double kelvin = Math.abs((inValue - inUnit.getOffset()) * inUnit.getScale());
			if (kelvin < 1e-8)
			{
				minPrec = 2;
			} else
			{
				// 3 significant figures in kelvin
				minPrec = 2 - Math.floor(Math.log10(kelvin) + FUDGE);
			}
		} else
		{
			if (inValue == 0 || outValue <= 0)
			{
				return 0;
			}
			if (out.hasIntegerMorePrecision() && Math.floor(inValue) == inValue)
			{
				adjust = -Math.log10(inUnit.getScale());
			} else if (in.isComposite() && inUnit.hasSubunitMorePrecision())
			{
				adjust = Math.log10(out.getScale()) + 2;
			} else
			{
				adjust = Math.log10(Math.abs(inValue / outValue));
			}
			adjust += Math.log10(2);
			// ensure that the output has at least two significant figures
			minPrec = 1 - Math.floor(Math.log10(outValue) + FUDGE);
		}
		return (int) Math.max(Math.floor(prec + adjust), minPrec);
	}

	// =========================================================================

	/**
	 * Determines the unit name or symbol (make_id() in Module:Convert).
	 */
	private static Id makeId(Context ctx, Units unit, boolean isInput, boolean singular)
	{
		if (ctx.values)
		{
			return new Id("", false, "");
		}

		Boolean wantName = null;
		if (ctx.abbrOrg == null)
		{
			if (ctx.wantName)
			{
				wantName = true;
			}
			if (unit.isSymbolPreferred())
			{
				wantName = false;
			}
		}
		if (wantName == null)
		{
			final AbbreviationMode abbr = ctx.abbr();
			wantName = !(abbr == AbbreviationMode.ON
					|| (abbr == AbbreviationMode.IN && isInput)
					|| (abbr == AbbreviationMode.OUT && !isInput));
		}
		final boolean wantLink = ctx.link == LinkMode.ON
				|| (ctx.link == LinkMode.IN && isInput)
				|| (ctx.link == LinkMode.OUT && !isInput);

		String id;
		String sep;
		if (wantName)
		{
			sep = " ";
			id = unit.getUnitName(!(ctx.adjectival || singular), ctx.usSpelling);
		} else
		{
			sep = SYMBOL_SEP;
			id = unit.getSymbol(ctx.usSpelling);
		}
		if (id.startsWith("/"))
		{
			sep = ""; // no separator before units like "/ha"
		}
		if (wantLink)
		{
			id = makeLink(ctx, unit.getLink(), id, unit);
		}
		return new Id(id, wantName, sep);
	}

	/**
	 * Creates a link like "[[Foot (unit)|ft]]" or "[[metre]]s". A unit is only
	 * linked once per conversion.
	 */
	private static String makeLink(Context ctx, String link, String id, Units unit)
	{
		if (link == null || link.isEmpty() || !ctx.linked.add(unit))
		{
			return id;
		}
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
	 * Returns a hyphenated form of the given name for adjectival usage (e.g.
	 * "10-metre"). Link targets are not changed.
	 */
	static String hyphenated(String name)
	{
		if (name.indexOf(' ') < 0)
		{
			return name;
		}
		StringBuilder sb = new StringBuilder();
		Matcher m = LINK_RX.matcher(name);
		int pos = 0;
		while (m.find())
		{
			sb.append(hyphenatedText(name.substring(pos, m.start())));
			String target = m.group(1);
			String title = (m.group(2) != null) ? m.group(2) : target;
			if (title.indexOf(' ') < 0)
			{
				sb.append(m.group(0));
			} else
			{
				sb.append("[[").append(target).append('|').append(hyphenatedText(title))
						.append("]]").append(m.group(3));
			}
			pos = m.end();
		}
		sb.append(hyphenatedText(name.substring(pos)));
		return sb.toString();
	}

	private static String hyphenatedText(String name)
	{
		// not in "(pre-1954 US) nautical mile" or "British thermal unit (ISO)"
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
			if (pos >= 1)
			{
				return name.substring(0, pos - 1).replace(' ', '-') + name.substring(pos - 1);
			}
		}
		return name.replace(' ', '-');
	}

	// =========================================================================

	/**
	 * The state of one invocation.
	 */
	private static final class Context
	{
		/** The abbreviation mode (IN, OUT, ON or OFF), or null for default. */
		AbbreviationMode abbr;

		/** The abbreviation mode as given (before disp=flip). */
		AbbreviationMode abbrOrg;

		boolean values;

		boolean alsoSymbol;

		boolean adjectival;

		LinkMode link;

		boolean usSpelling;

		Integer sigFig;

		Integer precision;

		Double round;

		boolean roundEach;

		boolean flip;

		/** Use names when no abbreviation mode was given (e.g. disp=or). */
		boolean wantName;

		/** Output in multiple units uses names (disp=or or disp=slash). */
		boolean dispWantName;

		String join1 = " (";

		String join2 = ")";

		String joinBetween = "; ";

		boolean outputOnly;

		boolean outputNumberOnly;

		boolean inputUnitOnly;

		boolean outputUnitOnly;

		/** An input value used e-notation. */
		boolean scientific;

		boolean outRangeX;

		boolean abbrRangeX;

		/** Units which were already linked. */
		final Set<Units> linked = new HashSet<Units>();

		AbbreviationMode abbr()
		{
			return (abbr != null) ? abbr : AbbreviationMode.OUT;
		}
	}

	/**
	 * The input values and units.
	 */
	private static final class Input
	{
		final List<ParsedNumber> values = new ArrayList<ParsedNumber>();

		/** Whether a value is a change (after "±"). */
		final List<Boolean> changes = new ArrayList<Boolean>();

		final List<Range> ranges = new ArrayList<Range>();

		/** The (first) input unit. */
		Units unit;

		/** All units of a multi-unit input, otherwise null. */
		List<Units> units;

		/** All values of a multi-unit input, otherwise null. */
		List<ParsedNumber> subValues;

		/** Value of a multi-unit input in the least significant unit. */
		double total;

		/** Index of the next positional argument. */
		int next;

		boolean isComposite()
		{
			return units != null;
		}

		Units scaleUnit()
		{
			return isComposite() ? units.get(units.size() - 1) : unit;
		}

		double value(int k)
		{
			return isComposite() ? total : values.get(k).getValue();
		}

		DefCvt defaultCvt()
		{
			return isComposite() ? unit.getSubdivisionDefaultCvt() : unit.getDefaultCvt();
		}
	}

	/**
	 * An output unit, which may show a value in multiple units.
	 */
	private static final class Output
	{
		/** The units, the most significant first. */
		final Units[] units;

		/** factors[k] is the number of units[k + 1] per units[k]. */
		final int[] factors;

		Output(Units unit)
		{
			this.units = new Units[]{unit};
			this.factors = new int[0];
		}

		private Output(Units[] units, int[] factors)
		{
			this.units = units;
			this.factors = factors;
		}

		static Output multiple(String[] codes)
		{
			Units[] units = new Units[codes.length];
			int[] factors = new int[codes.length - 1];
			for (int k = 0; k < codes.length; k++)
			{
				units[k] = Units.searchUnitFromName(codes[k]);
				if (k > 0)
				{
					factors[k - 1] = (int) Math.round(units[k - 1].getScale() / units[k].getScale());
				}
			}
			return new Output(units, factors);
		}

		boolean isMultiple()
		{
			return units.length > 1;
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

		/** The separator between value and unit. */
		final String sep;

		Id(String text, boolean name, String sep)
		{
			this.text = text;
			this.name = name;
			this.sep = sep;
		}
	}

	/**
	 * Rounded output values.
	 */
	private static final class Rounded
	{
		final FormattedNumber[] numbers;

		/** The absolute values before rounding. */
		final double[] raw;

		Rounded(int n)
		{
			this.numbers = new FormattedNumber[n];
			this.raw = new double[n];
		}
	}
}
