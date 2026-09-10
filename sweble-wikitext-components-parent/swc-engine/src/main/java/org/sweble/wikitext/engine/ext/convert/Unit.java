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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sweble.wikitext.engine.ext.convert.ConvertData.EngScale;
import org.sweble.wikitext.engine.ext.convert.ConvertData.Fixup;
import org.sweble.wikitext.engine.ext.convert.ConvertData.SiPrefix;
import org.sweble.wikitext.engine.ext.convert.ConvertData.Subdiv;
import org.sweble.wikitext.engine.ext.convert.ConvertData.UnitDef;

/**
 * A unit of one invocation of {{convert}} (a "unit table" in
 * Module:Convert). Units are created by {@link #lookup} for each invocation,
 * so they may hold the state of the invocation (e.g. the values).
 *
 * The names and symbols which Module:Convert derives with metatables are
 * derived by the getters (e.g. {@link #name2()} is "name1" + "s" unless
 * defined).
 */
final class Unit
{
	/** Which kinds of units may be looked up. */
	enum Want
	{
		/** A single unit only. */
		NO_COMBINATION,
		/** A single unit or a combination or an output multiple. */
		ANY_COMBINATION,
		/** A single unit or an output multiple only. */
		ONLY_MULTIPLE
	}

	/** The keys of the names and symbols. */
	enum Key
	{
		SYMBOL, SYM_US, NAME1, NAME1_US, NAME2, NAME2_US;

		boolean isSymbol()
		{
			return this == SYMBOL || this == SYM_US;
		}
	}

	private enum Kind
	{
		/** A unit which does not accept SI prefixes. */
		PLAIN,
		/** A unit which accepts SI prefixes. */
		PREFIXED,
		/** A unit like "kg/ha". */
		PER,
		/** Output units like "km mi" or an output multiple like "ftin". */
		COMBINATION,
		/** Input units like {{convert|5|ft|6|in}}. */
		COMPOSITE
	}

	private static final String PLURAL_SUFFIX = "s";

	private static final Pattern ENG_RX = Pattern.compile("^([Ee])(\\d+)(.*)");

	private static final Pattern PER_RX = Pattern.compile("^(.*?)/([^/]+)$");

	private static final Pattern E_DIGIT_RX = Pattern.compile("e\\d");

	private final Kind kind;

	String unitcode;

	// names and symbols (null if derived)

	String name1;

	String name1Us;

	String name2;

	String name2Us;

	String symbol;

	String symUs;

	String link;

	// prefixed units

	private String prefixedName1;

	private String prefixedName1Us;

	private String prefixedName2;

	private String prefixedName2Us;

	private String prefixedSymbol;

	private String prefixedSymUs;

	private int prefixPosition;

	String siName = "";

	String siPrefix = "";

	int prefixes;

	// properties

	String utype;

	String alttype;

	double scale;

	Double offset;

	int invert;

	boolean iscomplex;

	boolean istemperature;

	boolean usename;

	boolean usesymbol;

	int customary;

	/** Multiplier of an alias like "100km" as text (null if none). */
	String multiplier;

	String exception;

	String builtin;

	String defaultCode;

	String symlink;

	String defkey;

	String linkey;

	Map<String, Subdiv> subdivs;

	// per units

	/** The units of a per unit (the first one may be null). */
	Unit[] per;

	double scalemultiplier = 1;

	/** A currency symbol which is shown before the value (e.g. "$"). */
	String vprefix;

	/** The symbol defined for a per unit (e.g. "mpg"), or null. */
	String symbolRaw;

	// combinations

	/** Units of an output combination; the least significant first for a multiple. */
	List<Unit> combination;

	/** Scaling factors of an output multiple (e.g. 12 for "ftin"), or null. */
	double[] multiple;

	// composite input

	List<Unit> composite;

	// engineering notation

	EngScale engscale;

	boolean thisNumberWord;

	// state of the invocation

	List<Convert.Info> valinfo;

	/** "in" or "out". */
	String inout;

	/** The separator between the value and the name or symbol. */
	String sep;

	/** Denominator of fractions for output values, or null. */
	Integer frac;

	/** The unit which follows the hand unit in an output combination. */
	Unit outNext;

	/** Altitude in feet given after the Mach unit, or null. */
	Double altitude;

	/** The highest default precision of the values of a range, or null. */
	Integer maxDefaultPrecision;

	private Unit(Kind kind)
	{
		this.kind = kind;
	}

	private static Unit fromDef(UnitDef def, String unitcode)
	{
		Unit u = new Unit((def.prefixes > 0) ? Kind.PREFIXED : Kind.PLAIN);
		u.unitcode = unitcode;
		u.name1 = def.name1;
		u.name1Us = def.name1Us;
		u.name2 = def.name2;
		u.name2Us = def.name2Us;
		u.symbol = def.symbol;
		u.symUs = def.symUs;
		u.link = def.link;
		u.prefixedName1 = def.prefixedName1;
		u.prefixedName1Us = def.prefixedName1Us;
		u.prefixedName2 = def.prefixedName2;
		u.prefixedName2Us = def.prefixedName2Us;
		u.prefixedSymbol = def.prefixedSymbol;
		u.prefixedSymUs = def.prefixedSymUs;
		u.prefixPosition = def.prefixPosition;
		u.prefixes = def.prefixes;
		u.utype = def.utype;
		u.alttype = def.alttype;
		u.scale = (def.scale != null) ? def.scale : 1;
		u.offset = def.offset;
		u.invert = def.invert;
		u.iscomplex = def.iscomplex;
		u.istemperature = def.istemperature;
		u.usename = def.usename;
		u.usesymbol = def.usesymbol;
		u.customary = def.customary;
		u.multiplier = null;
		u.exception = def.exception;
		u.builtin = def.builtin;
		u.defaultCode = def.defaultCode;
		u.symlink = def.symlink;
		u.defkey = def.defkey;
		u.linkey = def.linkey;
		u.subdivs = def.subdivs;
		return u;
	}

	/**
	 * Creates the composite unit of a multi-unit input.
	 */
	static Unit composite(List<Unit> units, double scale, String defaultCode)
	{
		Unit u = new Unit(Kind.COMPOSITE);
		u.utype = units.get(0).utype;
		u.scale = scale;
		u.composite = units;
		u.defaultCode = defaultCode;
		return u;
	}

	/**
	 * Creates a unit for an unknown input unit code, which is shown as given.
	 */
	static Unit unknown(String code)
	{
		Unit u = new Unit(Kind.PLAIN);
		u.symbol = code;
		u.name2 = code;
		u.utype = code;
		u.defaultCode = "";
		u.defkey = "";
		u.linkey = "";
		return u;
	}

	// =========================================================================

	boolean isPer()
	{
		return kind == Kind.PER;
	}

	boolean isComposite()
	{
		return kind == Kind.COMPOSITE;
	}

	boolean isCombination()
	{
		return kind == Kind.COMBINATION;
	}

	/**
	 * @return The name or symbol for the given key.
	 */
	String get(Key key)
	{
		switch (key)
		{
			case SYMBOL:
				return symbol();
			case SYM_US:
				return symUs();
			case NAME1:
				return name1();
			case NAME1_US:
				return name1Us();
			case NAME2:
				return name2();
			default:
				return name2Us();
		}
	}

	String symbol()
	{
		if (symbol != null)
		{
			return symbol;
		}
		if (kind == Kind.PREFIXED)
		{
			String value = siPrefix + prefixedSymbol;
			return value.equals("l") ? "L" : value;
		}
		if (kind == Kind.PER)
		{
			Unit unit1 = per[0];
			Unit unit2 = per[1];
			return (unit1 != null)
					? unit1.symbol() + "/" + unit2.symbol()
					: "/" + unit2.symbol();
		}
		return null;
	}

	String symUs()
	{
		if (symUs != null)
		{
			return symUs;
		}
		if (kind == Kind.PREFIXED && prefixedSymUs != null)
		{
			return siPrefix + prefixedSymUs;
		}
		return symbol();
	}

	String name1()
	{
		if (name1 != null)
		{
			return name1;
		}
		if (kind == Kind.PREFIXED)
		{
			return prefixedName(prefixedName1);
		}
		return symbol();
	}

	String name2()
	{
		if (name2 != null)
		{
			return name2;
		}
		if (kind == Kind.PREFIXED && prefixedName2 != null)
		{
			return prefixedName(prefixedName2);
		}
		return name1() + PLURAL_SUFFIX;
	}

	String name1Us()
	{
		if (name1Us != null)
		{
			return name1Us;
		}
		if (kind == Kind.PREFIXED && prefixedName1Us != null)
		{
			return prefixedName(prefixedName1Us);
		}
		return name1();
	}

	String name2Us()
	{
		if (name2Us != null)
		{
			return name2Us;
		}
		if (kind == Kind.PREFIXED)
		{
			if (prefixedName2Us != null)
			{
				return prefixedName(prefixedName2Us);
			}
			if (prefixedName1Us != null)
			{
				return name1Us() + PLURAL_SUFFIX;
			}
			return name2();
		}
		if (name1Us != null)
		{
			return name1Us + PLURAL_SUFFIX;
		}
		return name2();
	}

	/**
	 * @return The link target of the unit or null.
	 */
	String link()
	{
		if (link != null || kind == Kind.PER || kind == Kind.COMBINATION || kind == Kind.COMPOSITE)
		{
			return link;
		}
		return name1();
	}

	private String prefixedName(String name)
	{
		if (prefixPosition > 0)
		{
			return name.substring(0, prefixPosition - 1) + siName + name.substring(prefixPosition - 1);
		}
		return siName + name;
	}

	/**
	 * @return The value with the given index (more than one for a range).
	 */
	Convert.Info info(int which)
	{
		return valinfo.get(which);
	}

	// =========================================================================

	/**
	 * Looks up a unit code (lookup() in Module:Convert). The code is a unit
	 * symbol (like "g") with an optional SI prefix (like "kg"), an alias, a
	 * per unit (like "kg/ha"), a combination (like "km mi" or "km+mi"), an
	 * output multiple (like "ftin") or a unit with an engineering notation
	 * prefix (like "e6km").
	 *
	 * @throws ConvertException If the unit is not known or not allowed.
	 */
	static Unit lookup(Convert.Parms parms, String unitcode, Want what)
	{
		return lookup(parms, unitcode, what, 1);
	}

	private static Unit lookup(Convert.Parms parms, String code, Want what, int depth)
	{
		if (depth > 9)
		{
			throw new ConvertException("Unit \"" + code + "\" is incorrectly defined");
		}
		if (code == null || code.isEmpty())
		{
			throw new ConvertException("Needs name of unit");
		}
		final String unitcode = code.replace('_', ' ').replace("&nbsp;", " ").replaceAll("  +", " ");
		final ConvertData data = ConvertData.get();

		UnitDef t = data.getUnit(unitcode);
		if (t != null)
		{
			if (t.shouldbe != null)
			{
				throw new ConvertException(t.shouldbe.replace("%{", "\"").replace("%}", "\""));
			}
			if (t.spUs)
			{
				parms.optSpUs = true;
			}
			if (t.target != null)
			{
				Unit result = lookup(parms, t.target, what, depth + 1);
				if (t.customary != 0)
				{
					result.customary = t.customary;
				}
				if (t.defaultCode != null)
				{
					result.defaultCode = t.defaultCode;
					// so the default exceptions use the alias code, not the target symbol
					result.defkey = unitcode;
				}
				if (t.link != null)
				{
					result.link = t.link;
				}
				if (t.symbol != null)
				{
					result.symbol = t.symbol;
				}
				if (t.symlink != null)
				{
					result.symlink = t.symlink;
				}
				if (t.usename)
				{
					result.usename = true;
				}
				if (t.multiplier != null)
				{
					result.multiplier = t.multiplier;
					result.scale = result.scale * Double.parseDouble(t.multiplier);
				}
				return result;
			}
			if (t.per != null)
			{
				return makePer(parms, unitcode, t, depth);
			}
			if (t.combination != null)
			{
				boolean isMultiple = t.multiple != null;
				if (what == Want.NO_COMBINATION || (what == Want.ONLY_MULTIPLE && !isMultiple))
				{
					throw new ConvertException("Unit \"" + unitcode + "\" is invalid here");
				}
				Unit result = new Unit(Kind.COMBINATION);
				result.utype = t.utype;
				result.multiple = t.multiple;
				result.combination = new ArrayList<Unit>();
				for (String item : t.combination)
				{
					result.combination.add(lookup(parms, item,
							isMultiple ? Want.NO_COMBINATION : Want.ONLY_MULTIPLE, depth + 1));
				}
				return result;
			}
			return fromDef(t, unitcode);
		}

		// Look for an SI prefix; check for longer prefix first ("dam" is decametre).
		for (int plen = 2; plen >= 1; plen--)
		{
			if (unitcode.length() <= plen)
			{
				continue;
			}
			SiPrefix si = ConvertData.SI_PREFIXES.get(unitcode.substring(0, plen));
			if (si != null)
			{
				UnitDef base = data.getUnit(unitcode.substring(plen));
				if (base != null && base.prefixes > 0)
				{
					Unit result = fromDef(base, unitcode);
					result.siName = (parms.optSpUs && si.nameUs != null) ? si.nameUs : si.name;
					result.siPrefix = si.prefix;
					result.scale = base.scale * pow10(si.exponent * base.prefixes);
					return result;
				}
			}
		}

		// Accept user-defined combinations like "acre+m2+ha" or "acre m2 ha".
		// If '+' is used, each unit code can include a space and any error is
		// fatal.
		boolean errIsFatal = false;
		List<String> combo = new ArrayList<String>();
		if (unitcode.indexOf('+') >= 0)
		{
			errIsFatal = true;
			for (String item : unitcode.split("\\+"))
			{
				item = item.trim();
				if (!item.isEmpty())
				{
					combo.add(item);
				}
			}
		}
		else if (unitcode.matches("(?s).*\\s.*"))
		{
			for (String item : unitcode.trim().split("\\s+"))
			{
				combo.add(item);
			}
		}
		if (combo.size() > 1)
		{
			try
			{
				return lookupCombo(parms, unitcode, combo, what, depth);
			}
			catch (ConvertException e)
			{
				if (errIsFatal)
				{
					throw e;
				}
			}
		}

		// Accept any unit with an engineering notation prefix like "e6cuft",
		// but not chained prefixes, combinations, and units with an offset or
		// built-in units.
		Matcher m = ENG_RX.matcher(unitcode);
		if (m.find())
		{
			EngScale engscale = ConvertData.ENG_SCALES.get(m.group(2));
			if (engscale != null)
			{
				Unit result = null;
				try
				{
					result = lookup(parms, m.group(3), Want.NO_COMBINATION, depth + 1);
				}
				catch (ConvertException e)
				{
					// not a valid unit
				}
				if (result != null && result.offset == null && result.builtin == null
						&& result.engscale == null)
				{
					String ucode = unitcode;
					if (m.group(1).equals("E"))
					{
						result.thisNumberWord = true;
						ucode = "e" + unitcode.substring(1);
					}
					result.unitcode = ucode;
					result.defkey = ucode;
					result.engscale = engscale;
					result.scale = result.scale * pow10(engscale.exponent);
					return result;
				}
			}
		}

		// Look for x/y; split on right-most slash to get scale correct (x/y/z
		// is x/y per z).
		m = PER_RX.matcher(unitcode);
		if (m.find() && !E_DIGIT_RX.matcher(unitcode).find())
		{
			UnitDef perDef = new UnitDef(unitcode);
			perDef.per = new String[] { m.group(1), m.group(2) };
			try
			{
				return makePer(parms, unitcode, perDef, depth);
			}
			catch (ConvertException e)
			{
				// not a valid per unit
			}
		}

		throw new ConvertException("Unit name \"" + unitcode + "\" is not known");
	}

	private static Unit lookupCombo(
			Convert.Parms parms,
			String unitcode,
			List<String> combo,
			Want what,
			int depth)
	{
		if (what == Want.NO_COMBINATION || what == Want.ONLY_MULTIPLE)
		{
			throw new ConvertException("Unit \"" + unitcode + "\" is invalid here");
		}
		Unit result = new Unit(Kind.COMBINATION);
		result.combination = new ArrayList<Unit>();
		for (String item : combo)
		{
			Unit t = lookup(parms, item, Want.ONLY_MULTIPLE, depth + 1);
			if (result.combination.isEmpty())
			{
				result.utype = t.utype;
			}
			else
			{
				checkMismatch(result, t);
			}
			result.combination.add(t);
		}
		return result;
	}

	/**
	 * Creates a per unit like "kg/ha" (make_per() in Module:Convert).
	 */
	private static Unit makePer(Convert.Parms parms, String unitcode, UnitDef def, int depth)
	{
		Unit result = new Unit(Kind.PER);
		result.unitcode = unitcode;
		result.utype = def.utype;
		result.invert = def.invert;
		result.iscomplex = def.iscomplex;
		result.defaultCode = def.defaultCode;
		result.link = def.link;
		result.symbol = def.symbol;
		result.symlink = def.symlink;
		result.symbolRaw = def.symbol;
		result.per = new Unit[2];
		String prefix = null;
		for (int i = 0; i < def.per.length && i < 2; i++)
		{
			String v = def.per[i];
			if (i == 0 && v.isEmpty())
			{
				// first unit symbol can be empty; that gives no first unit
			}
			else if (i == 0 && ConvertData.CURRENCY.contains(v))
			{
				prefix = (parms.currencyText != null) ? parms.currencyText : v;
			}
			else
			{
				result.per[i] = lookup(parms, v, Want.NO_COMBINATION, depth + 1);
			}
		}
		Double multiplier = (def.multiplier != null) ? Double.valueOf(def.multiplier) : null;
		if (result.utype == null)
		{
			// creating an automatic per unit
			Unit unit1 = result.per[0];
			String utype = ((unit1 != null) ? unit1.utype : (prefix != null) ? prefix : "")
					+ "/" + result.per[1].utype;
			Fixup fixup = ConvertData.get().getPerUnitFixup(utype);
			if (fixup != null)
			{
				if (fixup.utype != null)
				{
					utype = fixup.utype;
				}
				if (result.link == null)
				{
					result.link = fixup.link;
				}
				if (multiplier == null)
				{
					multiplier = fixup.multiplier;
				}
			}
			result.utype = utype;
		}
		result.scalemultiplier = (multiplier != null) ? multiplier : 1;
		result.vprefix = prefix;
		Unit unit1 = result.per[0];
		result.scale = ((unit1 != null) ? unit1.scale : 1) * result.scalemultiplier / result.per[1].scale;
		return result;
	}

	/**
	 * Checks that unit1 can be converted to unit2 (check_mismatch() in
	 * Module:Convert). This allows conversion between units of the same type
	 * and between "Nm" (torque) and "ftlb" (energy) whose alternate types
	 * match.
	 *
	 * @throws ConvertException If the units cannot be converted.
	 */
	static void checkMismatch(Unit unit1, Unit unit2)
	{
		if (equal(unit1.utype, unit2.utype)
				|| (equal(unit1.utype, unit2.alttype) && equal(unit1.alttype, unit2.utype)))
		{
			return;
		}
		throw new ConvertException("Cannot convert \"" + unit1.utype + "\" to \"" + unit2.utype + "\"");
	}

	private static boolean equal(String a, String b)
	{
		return (a == null) ? b == null : a.equals(b);
	}

	/**
	 * @return 10 to the power of the given exponent, rounded like the C
	 * library (which is used by Module:Convert) does.
	 */
	static double pow10(int exponent)
	{
		return Double.parseDouble("1e" + exponent);
	}
}
