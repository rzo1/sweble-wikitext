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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for {@link ConvertDataGenerator}. The input has the syntax of
 * Module:Convert/data (the entries are copied from there, shortened).
 */
public class ConvertDataGeneratorTest
{
	private static final String LUA = ""
			+ "-- Conversion data used by [[Module:Convert]].\n"
			+ "local all_units = {\n"
			+ "    [\"ft\"] = {\n"
			+ "\tname1    = \"foot\",\n"
			+ "\tname2    = \"feet\",\n"
			+ "\tsymbol   = \"ft\",\n"
			+ "\tutype    = \"length\",\n"
			+ "\tscale    = 0.3048,\n"
			+ "\tsubdivs  = { [\"in\"] = { 12, default = \"m\" } },\n"
			+ "\tlink     = \"Foot (unit)\",\n"
			+ "    },\n"
			+ "    [\"F\"] = {\n"
			+ "\tscale    = 0.55555555555555558,\n"
			+ "\toffset   = 32-273.15*(9/5),\n"
			+ "\tiscomplex= true,\n"
			+ "    },\n"
			+ "    [\"ftin\"] = {\n"
			+ "\tcombination= { \"in\", \"ft\" },\n"
			+ "\tmultiple = { 12 },\n"
			+ "    },\n"
			+ "    [\"$/ha\"] = {\n"
			+ "\tper      = { \"$\", \"ha\" },\n"
			+ "    },\n"
			+ "}\n"
			+ "\n"
			+ "local default_exceptions = {\n"
			+ "    -- Prefixed units with a default different from that of the base unit.\n"
			+ "    [\"km\"] = \"mi\",\n"
			+ "}\n"
			+ "\n"
			+ "local link_exceptions = {\n"
			+ "    [\"km\"] = \"Kilometre\",\n"
			+ "}\n"
			+ "\n"
			+ "local per_unit_fixups = {\n"
			+ "    [\"/area\"] = \"per unit area\",\n"
			+ "    [\"mass/area\"] = { utype = \"pressure\", multiplier = 9.80665 },\n"
			+ "}\n"
			+ "\n"
			+ "return {\n"
			+ "    all_units = all_units,\n"
			+ "    default_exceptions = default_exceptions,\n"
			+ "    link_exceptions = link_exceptions,\n"
			+ "    per_unit_fixups = per_unit_fixups,\n"
			+ "}\n";

	@Test
	public void testGenerate()
	{
		String data = ConvertDataGenerator.generate(LUA, "123");
		assertTrue(data.contains("Module:Convert/data (revision 123)"));
		String body = data.substring(data.indexOf("[all_units]"));
		assertEquals(""
				+ "[all_units]\n"
				+ "ft\tname1=foot\tname2=feet\tsymbol=ft\tutype=length\tscale=0.3048\tsubdivs=in:12:m\tlink=Foot (unit)\n"
				+ "F\tscale=0.55555555555555558\toffset=" + (32 - 273.15 * (9d / 5)) + "\tiscomplex=true\n"
				+ "ftin\tcombination=in|ft\tmultiple=12\n"
				+ "$/ha\tper=$|ha\n"
				+ "[default_exceptions]\n"
				+ "km\tmi\n"
				+ "[link_exceptions]\n"
				+ "km\tKilometre\n"
				+ "[per_unit_fixups]\n"
				+ "/area\tutype=per unit area\n"
				+ "mass/area\tutype=pressure\tmultiplier=9.80665\n",
				body);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testMissingTableIsRejected()
	{
		ConvertDataGenerator.generate("local all_units = {}\n", null);
	}
}
