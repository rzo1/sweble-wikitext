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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The unit data of {{convert}}.
 *
 * The units are read from "convert-data.txt" which is generated from
 * <a href="https://en.wikipedia.org/wiki/Module:Convert/data">Module:Convert/data</a>
 * (see ConvertDataGenerator in the tests). The file is licensed under CC BY-SA
 * 4.0 and therefore ships in the separate artifact swc-convert-data; without
 * it {{convert}} only reports an error. The remaining tables are taken from
 * <a href="https://en.wikipedia.org/wiki/Module:Convert/text">Module:Convert/text</a>.
 * The data is immutable and shared by all threads.
 */
final class ConvertData
{
	/**
	 * The resource in swc-convert-data. Its directory is no valid package name,
	 * so the resource is also found when the jars are used as modules.
	 */
	static final String RESOURCE = "org/sweble/wikitext/convert-data/convert-data.txt";

	static final String MISSING_DATA = "The unit data of {{convert}} is missing, "
			+ "add io.github.rzo1.org.sweble.wikitext:swc-convert-data to the class path";

	private static final ConvertData INSTANCE = loadDefault();

	/** SI prefixes which may be used with a unit that accepts them. */
	static final Map<String, SiPrefix> SI_PREFIXES;

	/** Names of engineering notation prefixes (e.g. "e6" in "e6km"). */
	static final Map<String, EngScale> ENG_SCALES;

	/** Qualifiers of units which may be linked (index 1 to 4). */
	static final String[][] CUSTOMARY_UNITS = {
			{ "US", "United States customary units" },
			{ "U.S.", "United States customary units" },
			{ "imperial", "Imperial units" },
			{ "imp", "Imperial units" } };

	/** Currency symbols which may be used for the first unit of a per unit. */
	static final Set<String> CURRENCY = Collections.unmodifiableSet(
			new HashSet<String>(Arrays.asList("$", "£", "€", "₱", "₽", "¥")));

	static
	{
		Map<String, SiPrefix> prefixes = new HashMap<String, SiPrefix>();
		addPrefix(prefixes, "Q", 30, "quetta", null, null);
		addPrefix(prefixes, "R", 27, "ronna", null, null);
		addPrefix(prefixes, "Y", 24, "yotta", null, null);
		addPrefix(prefixes, "Z", 21, "zetta", null, null);
		addPrefix(prefixes, "E", 18, "exa", null, null);
		addPrefix(prefixes, "P", 15, "peta", null, null);
		addPrefix(prefixes, "T", 12, "tera", null, null);
		addPrefix(prefixes, "G", 9, "giga", null, null);
		addPrefix(prefixes, "M", 6, "mega", null, null);
		addPrefix(prefixes, "k", 3, "kilo", null, null);
		addPrefix(prefixes, "h", 2, "hecto", null, null);
		addPrefix(prefixes, "da", 1, "deca", "deka", null);
		addPrefix(prefixes, "d", -1, "deci", null, null);
		addPrefix(prefixes, "c", -2, "centi", null, null);
		addPrefix(prefixes, "m", -3, "milli", null, null);
		addPrefix(prefixes, "μ", -6, "micro", null, null); // U+03BC
		addPrefix(prefixes, "µ", -6, "micro", null, "μ"); // U+00B5
		addPrefix(prefixes, "u", -6, "micro", null, "μ");
		addPrefix(prefixes, "n", -9, "nano", null, null);
		addPrefix(prefixes, "p", -12, "pico", null, null);
		addPrefix(prefixes, "f", -15, "femto", null, null);
		addPrefix(prefixes, "a", -18, "atto", null, null);
		addPrefix(prefixes, "z", -21, "zepto", null, null);
		addPrefix(prefixes, "y", -24, "yocto", null, null);
		addPrefix(prefixes, "r", -27, "ronto", null, null);
		addPrefix(prefixes, "q", -30, "quecto", null, null);
		SI_PREFIXES = Collections.unmodifiableMap(prefixes);

		Map<String, EngScale> scales = new HashMap<String, EngScale>();
		scales.put("3", new EngScale("thousand", null, 3));
		scales.put("6", new EngScale("million", null, 6));
		scales.put("9", new EngScale("billion", "1000000000 (number)", 9));
		scales.put("12", new EngScale("trillion", "1000000000000 (number)", 12));
		scales.put("15", new EngScale("quadrillion", "1000000000000000 (number)", 15));
		ENG_SCALES = Collections.unmodifiableMap(scales);
	}

	private static void addPrefix(
			Map<String, SiPrefix> prefixes,
			String key,
			int exponent,
			String name,
			String nameUs,
			String prefix)
	{
		prefixes.put(key, new SiPrefix((prefix != null) ? prefix : key, exponent, name, nameUs));
	}

	// =========================================================================

	private final Map<String, UnitDef> allUnits = new HashMap<String, UnitDef>();

	private final Map<String, String> defaultExceptions = new HashMap<String, String>();

	private final Map<String, String> linkExceptions = new HashMap<String, String>();

	private final Map<String, Fixup> perUnitFixups = new HashMap<String, Fixup>();

	private ConvertData()
	{
	}

	/**
	 * @return Whether the unit data (swc-convert-data) is on the class path.
	 */
	static boolean isAvailable()
	{
		return INSTANCE != null;
	}

	/**
	 * @throws IllegalStateException If the unit data is missing.
	 */
	static ConvertData get()
	{
		if (INSTANCE == null)
		{
			throw new IllegalStateException(MISSING_DATA);
		}
		return INSTANCE;
	}

	/**
	 * @return The definition of the given unit code or null.
	 */
	UnitDef getUnit(String code)
	{
		return allUnits.get(code);
	}

	/**
	 * @return The default output of a unit whose symbol (or defkey) is given
	 * if it differs from the default of the unit definition, otherwise null.
	 */
	String getDefaultException(String key)
	{
		return (key != null) ? defaultExceptions.get(key) : null;
	}

	/**
	 * @return The link of a unit whose symbol (or linkey) is given if it
	 * differs from the link of the unit definition, otherwise null.
	 */
	String getLinkException(String key)
	{
		return (key != null) ? linkExceptions.get(key) : null;
	}

	/**
	 * @return The properties of an automatically created per unit (e.g. of
	 * "length/time") or null.
	 */
	Fixup getPerUnitFixup(String utype)
	{
		return perUnitFixups.get(utype);
	}

	// =========================================================================

	/**
	 * Loads the data with the class loader of this class or, if it doesn't
	 * see swc-convert-data, with the context class loader.
	 */
	private static ConvertData loadDefault()
	{
		ConvertData data = load(ConvertData.class.getClassLoader());
		ClassLoader context = Thread.currentThread().getContextClassLoader();
		if (data == null && context != null)
		{
			data = load(context);
		}
		return data;
	}

	/**
	 * @return The data or null if the loader doesn't find the resource.
	 */
	static ConvertData load(ClassLoader loader)
	{
		InputStream in = (loader != null) ?
				loader.getResourceAsStream(RESOURCE) :
				ClassLoader.getSystemResourceAsStream(RESOURCE);
		if (in == null)
		{
			return null;
		}
		ConvertData data = new ConvertData();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String section = null;
			String line;
			while ((line = r.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				if (line.startsWith("["))
				{
					section = line.substring(1, line.length() - 1);
					continue;
				}
				String[] fields = line.split("\t");
				if ("all_units".equals(section))
				{
					data.allUnits.put(fields[0], parseUnit(fields));
				}
				else if ("default_exceptions".equals(section))
				{
					data.defaultExceptions.put(fields[0], fields[1]);
				}
				else if ("link_exceptions".equals(section))
				{
					data.linkExceptions.put(fields[0], fields[1]);
				}
				else if ("per_unit_fixups".equals(section))
				{
					data.perUnitFixups.put(fields[0], parseFixup(fields));
				}
				else
				{
					throw new IllegalStateException("Invalid line in " + RESOURCE + ": " + line);
				}
			}
		}
		catch (IOException e)
		{
			throw new IllegalStateException("Cannot read " + RESOURCE, e);
		}
		return data;
	}

	private static Fixup parseFixup(String[] fields)
	{
		Fixup fixup = new Fixup();
		for (int i = 1; i < fields.length; i++)
		{
			int eq = fields[i].indexOf('=');
			String key = fields[i].substring(0, eq);
			String value = fields[i].substring(eq + 1);
			if (key.equals("utype"))
			{
				fixup.utype = value;
			}
			else if (key.equals("link"))
			{
				fixup.link = value;
			}
			else if (key.equals("multiplier"))
			{
				fixup.multiplier = Double.valueOf(value);
			}
			else
			{
				throw new IllegalStateException("Unknown field " + key + " in " + RESOURCE);
			}
		}
		return fixup;
	}

	private static UnitDef parseUnit(String[] fields)
	{
		UnitDef u = new UnitDef(fields[0]);
		for (int i = 1; i < fields.length; i++)
		{
			int eq = fields[i].indexOf('=');
			String key = fields[i].substring(0, eq);
			String value = fields[i].substring(eq + 1);
			switch (key)
			{
				case "name1":
					u.name1 = value;
					break;
				case "name1_us":
					u.name1Us = value;
					break;
				case "name2":
					u.name2 = value;
					break;
				case "name2_us":
					u.name2Us = value;
					break;
				case "symbol":
					u.symbol = value;
					break;
				case "sym_us":
					u.symUs = value;
					break;
				case "_name1":
					u.prefixedName1 = value;
					break;
				case "_name1_us":
					u.prefixedName1Us = value;
					break;
				case "_name2":
					u.prefixedName2 = value;
					break;
				case "_name2_us":
					u.prefixedName2Us = value;
					break;
				case "_symbol":
					u.prefixedSymbol = value;
					break;
				case "_sym_us":
					u.prefixedSymUs = value;
					break;
				case "prefix_position":
					u.prefixPosition = Integer.parseInt(value);
					break;
				case "prefixes":
					u.prefixes = Integer.parseInt(value);
					break;
				case "utype":
					u.utype = value;
					break;
				case "alttype":
					u.alttype = value;
					break;
				case "scale":
					u.scale = Double.valueOf(value);
					break;
				case "offset":
					u.offset = Double.valueOf(value);
					break;
				case "invert":
					u.invert = Integer.parseInt(value);
					break;
				case "iscomplex":
					u.iscomplex = Boolean.parseBoolean(value);
					break;
				case "istemperature":
					u.istemperature = Boolean.parseBoolean(value);
					break;
				case "usename":
					u.usename = true;
					break;
				case "usesymbol":
					u.usesymbol = true;
					break;
				case "sp_us":
					u.spUs = Boolean.parseBoolean(value);
					break;
				case "customary":
					u.customary = Integer.parseInt(value);
					break;
				case "multiplier":
					u.multiplier = value;
					break;
				case "exception":
					u.exception = value;
					break;
				case "builtin":
					u.builtin = value;
					break;
				case "default":
					u.defaultCode = value;
					break;
				case "link":
					u.link = value;
					break;
				case "symlink":
					u.symlink = value;
					break;
				case "defkey":
					u.defkey = value;
					break;
				case "linkey":
					u.linkey = value;
					break;
				case "target":
					u.target = value;
					break;
				case "shouldbe":
					u.shouldbe = value;
					break;
				case "per":
					u.per = value.split("\\|", -1);
					break;
				case "combination":
					u.combination = value.split("\\|");
					break;
				case "multiple":
				{
					String[] items = value.split("\\|");
					u.multiple = new double[items.length];
					for (int k = 0; k < items.length; k++)
					{
						u.multiple[k] = Double.parseDouble(items[k]);
					}
					break;
				}
				case "subdivs":
					u.subdivs = new LinkedHashMap<String, Subdiv>();
					for (String item : value.split("\\|"))
					{
						String[] parts = item.split(":");
						u.subdivs.put(parts[0], new Subdiv(
								Integer.parseInt(parts[1]),
								parts[2],
								(parts.length > 3) ? parts[3] : null));
					}
					break;
				default:
					throw new IllegalStateException("Unknown field " + key + " in " + RESOURCE);
			}
		}
		return u;
	}

	// =========================================================================

	/**
	 * A unit as defined in Module:Convert/data. Fields which are not defined
	 * are null (or 0 or false).
	 */
	static final class UnitDef
	{
		final String code;

		String name1;

		String name1Us;

		String name2;

		String name2Us;

		String symbol;

		String symUs;

		/** The fields "_name1" etc. of units which accept SI prefixes. */
		String prefixedName1;

		String prefixedName1Us;

		String prefixedName2;

		String prefixedName2Us;

		String prefixedSymbol;

		String prefixedSymUs;

		/** Position (1-based) of an SI prefix in the name, or 0. */
		int prefixPosition;

		/** Power of the unit for SI prefixes (e.g. 2 for "m2"), or 0. */
		int prefixes;

		String utype;

		String alttype;

		Double scale;

		Double offset;

		int invert;

		boolean iscomplex;

		boolean istemperature;

		boolean usename;

		boolean usesymbol;

		boolean spUs;

		int customary;

		/** The multiplier of an alias (e.g. "100" for "100km") as given. */
		String multiplier;

		String exception;

		String builtin;

		String defaultCode;

		String link;

		String symlink;

		String defkey;

		String linkey;

		String target;

		String shouldbe;

		String[] per;

		String[] combination;

		double[] multiple;

		Map<String, Subdiv> subdivs;

		UnitDef(String code)
		{
			this.code = code;
		}
	}

	/**
	 * An allowed subdivision of a unit for multi-unit input like
	 * {{convert|5|ft|6|in}}.
	 */
	static final class Subdiv
	{
		/** The number of subdivisions per unit (e.g. 12 inches per foot). */
		final int count;

		/** The default output unit. */
		final String defaultCode;

		/** The unit which is shown instead of the given subdivision, or null. */
		final String unit;

		Subdiv(int count, String defaultCode, String unit)
		{
			this.count = count;
			this.defaultCode = defaultCode;
			this.unit = unit;
		}
	}

	static final class SiPrefix
	{
		final String prefix;

		final int exponent;

		final String name;

		final String nameUs;

		SiPrefix(String prefix, int exponent, String name, String nameUs)
		{
			this.prefix = prefix;
			this.exponent = exponent;
			this.name = name;
			this.nameUs = nameUs;
		}
	}

	static final class EngScale
	{
		final String name;

		final String link;

		final int exponent;

		EngScale(String name, String link, int exponent)
		{
			this.name = name;
			this.link = link;
			this.exponent = exponent;
		}
	}

	/**
	 * Properties of an automatically created per unit.
	 */
	static final class Fixup
	{
		String utype;

		String link;

		Double multiplier;
	}
}
