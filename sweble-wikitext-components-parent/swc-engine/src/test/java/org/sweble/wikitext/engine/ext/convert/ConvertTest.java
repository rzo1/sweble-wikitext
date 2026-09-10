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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.sweble.wikitext.engine.ExpansionCallback;
import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.FullPage;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.engine.utils.EnginePrettyPrinter;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtXmlAttribute;
import org.sweble.wikitext.parser.nodes.WtXmlElement;

/**
 * Expansion tests for {{convert}}.
 *
 * Unless noted otherwise, the expected values are the output of English
 * Wikipedia (Module:Convert) retrieved via
 * https://en.wikipedia.org/w/api.php?action=expandtemplates in September
 * 2026. As in the existing convert fixtures, the "&amp;nbsp;" which Wikipedia
 * puts between a value and a unit symbol is written as a plain space, "×10³"
 * is written with superscript characters instead of "&lt;sup&gt;" markup and
 * the error and warning markup is not compared. The other differences, which
 * do not change the displayed text, are listed in the documentation of
 * {@link Convert}: the "&amp;nbsp;" in range texts and other texts of
 * Module:Convert is a plain space, entities in unit names and symbols are
 * Unicode characters (like "&amp;nbsp;" in "sq&amp;nbsp;ft" or "&amp;frasl;"
 * in fractions), and the TemplateStyles of fractions are omitted.
 */
public class ConvertTest
{
	private static final String NBSP = "\u00A0";

	private static final WikiConfigImpl CONFIG = DefaultConfigEnWp.generate();

	private static final Map<String, String> PAGES = new HashMap<String, String>();
	static
	{
		PAGES.put("Template:Metres to feet", "{{convert|{{{1}}}|m|ft}}");
	}

	// =========================================================================

	@Test
	public void testConcurrentInvocationsDoNotShareState() throws Exception
	{
		final String[][] cases = {
				{ "{{convert|2|m|abbr=on}}", "2 m (6 ft 7 in)" },
				{ "{{convert|2|m|abbr=off}}", "2 metres (6 feet 7 inches)" },
				{ "{{convert|1|AU|km|sigfig=4}}", "1 astronomical unit (149,600,000 km)" },
				{ "{{convert|2|m|cm|sp=us|abbr=off}}", "2 meters (200 centimeters)" },
				{ "{{convert|3|m|cm|abbr=values}}", "3 (300)" },
				{ "{{convert|1|AU|km}}", "1 astronomical unit (150,000,000 km)" } };

		final int threads = 8;
		final int iterations = 300;
		final CountDownLatch start = new CountDownLatch(1);
		final AtomicInteger failures = new AtomicInteger();

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try
		{
			List<Future<Void>> futures = new ArrayList<Future<Void>>();
			for (int t = 0; t < threads; t++)
			{
				final int offset = t;
				futures.add(pool.submit(new Callable<Void>()
				{
					@Override
					public Void call() throws Exception
					{
						WtEngineImpl engine = createEngine();
						start.await();
						for (int i = 0; i < iterations; i++)
						{
							String[] c = cases[(offset + i) % cases.length];
							if (!c[1].equals(expand(engine, c[0])))
							{
								failures.incrementAndGet();
							}
						}
						return null;
					}
				}));
			}
			start.countDown();
			for (Future<Void> future : futures)
			{
				future.get();
			}
		}
		finally
		{
			pool.shutdown();
		}

		assertEquals("wrong conversions out of " + threads * iterations, 0, failures.get());
	}

	@Test
	public void testTemperatureWithExplicitTargetUnit() throws Exception
	{
		assertExpands("37 °C (99 °F)", "{{convert|37|C|F}}");
		assertExpands("0 °C (273 K)", "{{convert|0|C|K}}");
		assertExpands("100 °C (212 °F)", "{{convert|100|C|F}}");
		assertExpands("−40 °C (−40 °F)", "{{convert|-40|C|F}}");
		assertExpands("98.6 °F (37.0 °C)", "{{convert|98.6|F|C}}");
		assertExpands("300 K (27 °C)", "{{convert|300|K|C}}");
		assertExpands("0 K (−459.67 °F)", "{{convert|0|K|F}}");
		assertExpands("20 °C (293 K)", "{{convert|20|C|K}}");
		assertExpands("500 °R (278 K)", "{{convert|500|R|K}}");
		assertExpands("37 degrees Celsius (99 degrees Fahrenheit)", "{{convert|37|C|F|abbr=off}}");
	}

	@Test
	public void testTemperatureWithDefaultTargetUnit() throws Exception
	{
		assertExpands("0 °C (32 °F)", "{{convert|0|C}}");
		assertExpands("1 K (−272.15 °C; −457.87 °F)", "{{convert|1|K}}");
		assertExpands("100 K (−173 °C; −280 °F)", "{{convert|100|K}}");
		assertExpands("10 to 20 °C (50 to 68 °F)", "{{convert|10|to|20|C}}");
	}

	@Test
	public void testZero() throws Exception
	{
		assertExpands("0 metres (0 ft)", "{{convert|0|m|ft}}");
		assertExpands("0 metres (0 ft)", "{{convert|0|m}}");
	}

	@Test
	public void testInvalidNumbersGiveAnError() throws Exception
	{
		assertError("must be a number", "{{convert|1/0|m}}");
		assertError("must be a number", "{{convert|0/0|m}}");
		assertError("Number has overflowed", "{{convert|1e999|m}}");
		assertError("Number has overflowed", "{{convert|INF|m}}");
		assertError("Number has overflowed", "{{convert|NaN|m}}");
		assertError("must be a number", "{{convert|abc|m}}");
	}

	@Test
	public void testNumberNotations() throws Exception
	{
		assertExpands("−5 metres (−16 ft)", "{{convert|−5|m|ft}}");
		assertExpands("1×10³ metres (3.3×10³ ft)", "{{convert|1E3|m|ft}}");
		assertExpands("1×10³ metres (3.3×10³ ft)", "{{convert|1e3|m|ft}}");
		assertExpands("2.5×10⁻³ metres (2.5 mm)", "{{convert|2.5e-3|m|mm}}");
	}

	@Test
	public void testEmptyOptionsAreIgnored() throws Exception
	{
		assertExpands("5 metres (16 ft)", "{{convert|5|m|ft|abbr=}}");
		assertExpands("5 metres (16 ft)", "{{convert|5|m|ft|sigfig=}}");
	}

	@Test
	public void testMixedUnitOutput() throws Exception
	{
		assertExpands("1.52 metres (5 ft 0 in)", "{{convert|1.52|m}}");
		assertExpands("1.83 metres (6 ft 0 in)", "{{convert|1.83|m}}");
		assertExpands("0.3 metres (1 ft 0 in)", "{{convert|0.3|m}}");
		assertExpands("2.99 metres (9 ft 10 in)", "{{convert|2.99|m}}");
		assertExpands("180 centimetres (5 ft 11 in)", "{{convert|180|cm|ftin}}");
		assertExpands("−1.52 metres (−5 ft 0 in)", "{{convert|-1.52|m|ftin}}");
		assertExpands("1.52 metres (5 feet 0 inches)", "{{convert|1.52|m|ftin|abbr=off}}");
		assertExpands("5 centimetres (2.0 in)", "{{convert|5|cm|ftin}}");
		assertExpands("1 metre (3 ft 3.4 in)", "{{convert|1|m|ftin|1}}");
		assertExpands("−1 metre (−3.3 ft)", "{{convert|-1|m}}");
	}

	@Test
	public void testDefaultPrecision() throws Exception
	{
		assertExpands("1,234 metres (4,049 ft)", "{{convert|1234|m|ft}}");
		assertExpands("1,234 metres (4,049 ft)", "{{convert|1234|m}}");
		assertExpands("12.5 kilometres (7.8 mi)", "{{convert|12.5|km|mi}}");
		assertExpands("1,000 kilometres (620 mi)", "{{convert|1000|km|mi}}");
		assertExpands("1,234,567 metres (4,050,417 ft)", "{{convert|1234567|m|ft}}");
		assertExpands("0.001 metres (0.039 in)", "{{convert|0.001|m|in}}");
		assertExpands("1,000,000,000 metres (1,000,000 km)", "{{convert|1000000000|m|km}}");
		assertExpands("3.0 metres (9.8 ft)", "{{convert|3.0|m|ft}}");
		assertExpands("1,234.5678 metres (4,050.419 ft)", "{{convert|1234.5678|m|ft}}");
		assertExpands("1,234 metres (4,049 ft)", "{{Metres to feet|1234}}");
	}

	@Test
	public void testPositionalPrecision() throws Exception
	{
		assertExpands("1,234 metres (4,049 ft)", "{{convert|1234|m|ft|0}}");
		assertExpands("1,234 metres (4,000 ft)", "{{convert|1234|m|ft|-2}}");
		assertExpands("1,234.5 metres (4,050.2 ft)", "{{convert|1234.5|m|ft|1}}");
		assertExpands("5 kilometres (3.11 mi)", "{{convert|5|km|2}}");
	}

	@Test
	public void testAbbreviationOptions() throws Exception
	{
		assertExpands("10 m (33 ft)", "{{convert|10|m|ft|abbr=on}}");
		assertExpands("10 metres (33 feet)", "{{convert|10|m|ft|abbr=off}}");
		assertExpands("10 m (33 feet)", "{{convert|10|m|ft|abbr=in}}");
		assertExpands("10 metres (33 ft)", "{{convert|10|m|ft|abbr=out}}");
		assertExpands("10 (33)", "{{convert|10|m|ft|abbr=values}}");
		assertExpands("10 meters (33 ft)", "{{convert|10|m|ft|sp=us}}");
	}

	@Test
	public void testUnknownOptionsAreIgnored() throws Exception
	{
		assertExpands("10 metres (33 ft)", "{{convert|10|m|ft|foo=bar}}");
		assertExpands("10 metres (33 ft)", "{{convert|10|m|ft|abbr=bogus}}");
		assertExpands("10 metres (33 ft)", "{{convert|10|m|ft|disp=bogus}}");
		assertExpands("10 metres (33 ft)", "{{convert|10|m|ft|disp=slash}}");
	}

	@Test
	public void testAdjectivalOption() throws Exception
	{
		assertExpands("10-metre (33 ft)", "{{convert|10|m|adj=on}}");
		assertExpands("10-metre (33 ft)", "{{convert|10|m|ft|adj=on}}");
		assertExpands("10-metre (33-foot)", "{{convert|10|m|ft|adj=on|abbr=off}}");
	}

	@Test
	public void testLinkOption() throws Exception
	{
		assertExpands("10 [[metre]]s (33 [[Foot (unit)|ft]])", "{{convert|10|m|ft|lk=on}}");
		assertExpands("10 [[metre]]s (33 ft)", "{{convert|10|m|ft|lk=in}}");
		assertExpands("10 metres (33 [[Foot (unit)|ft]])", "{{convert|10|m|ft|lk=out}}");
		assertExpands("10 metres (33 ft)", "{{convert|10|m|ft|lk=off}}");
	}

	@Test
	public void testDisplayOption() throws Exception
	{
		assertExpands("10 metres or 33 feet", "{{convert|10|m|ft|disp=or}}");
		assertExpands("10 metres (33 ft)", "{{convert|10|m|ft|disp=b}}");
		assertExpands("33 ft", "{{convert|10|m|ft|disp=output only}}");
		assertExpands("33", "{{convert|10|m|ft|disp=number}}");
		assertExpands("3 ft 3 in", "{{convert|1|m|disp=output only}}");
		assertExpands("1 metre or 3 feet 3 inches", "{{convert|1|m|disp=or}}");
	}

	@Test
	public void testSpeedUnits() throws Exception
	{
		assertExpands("100 kilometres per hour (62 mph)", "{{convert|100|km/h}}");
		assertExpands("60 miles per hour (97 km/h)", "{{convert|60|mph}}");
		assertExpands("10 metres per second (33 ft/s)", "{{convert|10|m/s}}");
		assertExpands("20 knots (37 km/h; 23 mph)", "{{convert|20|kn}}");
		assertExpands("20 knots (37 km/h; 23 mph)", "{{convert|20|knot}}");
		assertExpands("100 kilometres per hour (28 m/s)", "{{convert|100|km/h|m/s}}");
		assertExpands("10 kilometers per hour (6.2 miles per hour)", "{{convert|10|km/h|mph|sp=us|abbr=off}}");
	}

	@Test
	public void testVolumeUnits() throws Exception
	{
		assertExpands("1 litre (0.22 imp" + NBSP + "gal; 0.26 US" + NBSP + "gal)", "{{convert|1|L}}");
		assertExpands("500 millilitres (18 imp" + NBSP + "fl" + NBSP + "oz; 17 US" + NBSP + "fl" + NBSP + "oz)", "{{convert|500|ml}}");
		assertExpands("500 millilitres (18 imp" + NBSP + "fl" + NBSP + "oz; 17 US" + NBSP + "fl" + NBSP + "oz)", "{{convert|500|mL}}");
		assertExpands("1 cubic metre (35 cu" + NBSP + "ft)", "{{convert|1|m3}}");
		assertExpands("10 US gallons (38 L; 8.3 imp" + NBSP + "gal)", "{{convert|10|USgal}}");
		assertExpands("10 imperial gallons (45 L; 12 US" + NBSP + "gal)", "{{convert|10|impgal}}");
		assertExpands("100 cubic feet (2.8 m³)", "{{convert|100|cuft}}");
		assertExpands("10 cubic inches (160 cm³)", "{{convert|10|cuin}}");
		assertExpands("1 liter (0.22 imperial gallons; 0.26 U.S. gallons)", "{{convert|1|L|abbr=off|sp=us}}");
	}

	@Test
	public void testPowerUnits() throws Exception
	{
		assertExpands("100 watts (0.13 hp)", "{{convert|100|W}}");
		assertExpands("100 kilowatts (130 hp)", "{{convert|100|kW}}");
		assertExpands("1 megawatt (1,300 hp)", "{{convert|1|MW}}");
		assertExpands("100 horsepower (75 kW)", "{{convert|100|hp}}");
	}

	@Test
	public void testPressureUnits() throws Exception
	{
		assertExpands("100 pascals (0.015 psi)", "{{convert|100|Pa}}");
		assertExpands("100 kilopascals (15 psi)", "{{convert|100|kPa}}");
		assertExpands("1 hectopascal (0.015 psi)", "{{convert|1|hPa}}");
		assertExpands("1 megapascal (150 psi)", "{{convert|1|MPa}}");
		assertExpands("1 bar (100 kPa)", "{{convert|1|bar}}");
		assertExpands("30 pounds per square inch (210 kPa)", "{{convert|30|psi}}");
		assertExpands("1 standard atmosphere (100 kPa)", "{{convert|1|atm}}");
		assertExpands("1 bar (100 kilopascals)", "{{convert|1|bar|abbr=off}}");
		assertExpands("2 bars (200 kilopascals)", "{{convert|2|bar|abbr=off}}");
	}

	@Test
	public void testPluralUnitNames() throws Exception
	{
		assertExpands("5 feet (1.5 m)", "{{convert|5|feet}}");
		assertExpands("5 metres (16 ft)", "{{convert|5|metres}}");
		assertExpands("5 miles (8.0 km)", "{{convert|5|miles}}");
		assertExpands("1 foot (0.30 m)", "{{convert|1|foot}}");
		assertExpands("5 inches (130 mm)", "{{convert|5|inches}}");
		assertExpands("5 metres (16 ft)", "{{convert|5|metres|ft}}");
	}

	@Test
	public void testRanges() throws Exception
	{
		assertExpands("5 to 10 metres (16 to 33 ft)", "{{convert|5|to|10|m}}");
		assertExpands("5–10 metres (16–33 ft)", "{{convert|5|-|10|m}}");
		assertExpands("5 and 10 metres (16 and 33 ft)", "{{convert|5|and|10|m}}");
		assertExpands("5 or 10 metres (16 or 33 ft)", "{{convert|5|or|10|m}}");
		assertExpands("5 by 10 metres (16 ft × 33 ft)", "{{convert|5|×|10|m}}");
		assertExpands("5 by 10 metres (16 ft × 33 ft)", "{{convert|5|x|10|m}}");
		assertExpands("5 to 10 kilometres (3.1 to 6.2 mi)", "{{convert|5|to|10|km|mi}}");
		assertExpands("1 to 2 metres (3 ft 3 in to 6 ft 7 in)", "{{convert|1|to|2|m}}");
	}

	@Test
	public void testMultiUnitInput() throws Exception
	{
		assertExpands("5 feet 6 inches (1.68 m)", "{{convert|5|ft|6|in|m}}");
		assertExpands("5 feet 6 inches (1.68 m)", "{{convert|5|ft|6|in}}");
		assertExpands("6 feet 1 inch (185 cm)", "{{convert|6|ft|1|in|cm}}");
		assertExpands("1 yard 2 feet (1.5 m)", "{{convert|1|yd|2|ft|m}}");
		assertExpands("11 stone 4 pounds (72 kg)", "{{convert|11|st|4|lb|kg}}");
		assertExpands("1 mile 200 yards (1,792 m)", "{{convert|1|mi|200|yd|m}}");
		assertExpands("5 ft 6 in (1.68 m)", "{{convert|5|ft|6|in|m|abbr=on}}");
		assertExpands("5 feet 6 inches (168 cm)", "{{convert|5|ft|6|in|cm}}");
		assertExpands("1 pound 8 ounces (0.68 kg)", "{{convert|1|lb|8|oz|kg}}");
		assertExpands("5-foot-6-inch (1.68 m)", "{{convert|5|ft|6|in|m|adj=on}}");
	}

	@Test
	public void testMoreDisplayOptions() throws Exception
	{
		assertExpands("33 feet (10 m)", "{{convert|10|m|ft|disp=flip}}");
		assertExpands("3.3 feet (1 m)", "{{convert|1|m|ft|order=flip}}");
		assertExpands("10 metres (33 ft)", "{{convert|10|m|ft|disp=(or)}}");
		assertExpands("10 metres, 33 ft", "{{convert|10|m|ft|disp=comma}}");
		assertExpands("10 metres [33 ft]", "{{convert|10|m|ft|disp=sqbr}}");
		assertExpands("10 metres; 33 ft", "{{convert|10|m|ft|disp=semicolon}}");
		assertExpands("metres", "{{convert|10|m|ft|disp=unit}}");
		assertExpands("ft", "{{convert|10|m|ft|disp=unit2}}");
		assertExpands("37 °C or 99 °F", "{{convert|37|C|F|disp=or}}");
		assertExpands("10 kilometres [km] (6.2 mi)", "{{convert|10|km|mi|abbr=~}}");
		assertExpands("1 (3.3)", "{{convert|1|m|ft|abbr=values}}");
	}

	@Test
	public void testRoundingOptions() throws Exception
	{
		assertExpands("10 metres (35 ft)", "{{convert|10|m|ft|round=5}}");
		assertExpands("10 metres (33 ft)", "{{convert|10|m|ft|round=0.5}}");
		assertExpands("1,000 feet (305 m)", "{{convert|1000|ft|m|sigfig=3}}");
		assertExpands("1 horsepower (0.75 kW)", "{{convert|1|hp|kW|2}}");
	}

	@Test
	public void testMoreLinks() throws Exception
	{
		assertExpands("1 [[metre]] (3 [[Foot (unit)|ft]] 3 [[inch|in]])", "{{convert|1|m|lk=on}}");
		assertExpands("1 [[Foot (unit)|ft]] (0.30 [[Metre|m]])", "{{convert|1|ft|m|lk=on|abbr=on}}");
		assertExpands("1 [[Foot (unit)|foot]] (12 [[inch|in]])", "{{convert|1|ft|in|lk=on}}");
	}

	@Test
	public void testMoreAdjectival() throws Exception
	{
		assertExpands("2-metre (6-foot-7-inch)", "{{convert|2|m|abbr=off|adj=on}}");
		assertExpands("5-to-10-metre (16 to 33 ft)", "{{convert|5|to|10|m|adj=on}}");
		assertExpands("5-to-10 m (16-to-33 ft)", "{{convert|5|to|10|m|abbr=on|adj=on}}");
	}

	@Test
	public void testMoreRanges() throws Exception
	{
		assertExpands("20 ± 5 °C (68 ± 9 °F)", "{{convert|20|+/-|5|C}}");
		assertExpands("20 ± 5 °C (68 ± 9 °F)", "{{convert|20|±|5|C|F}}");
		assertExpands("1 to 2 metres (3 ft 3 in to 6 ft 7 in)", "{{convert|1 to 2|m}}");
		assertExpands("1–2 metres (3 ft 3 in – 6 ft 7 in)", "{{convert|1-2|m}}");
		assertExpands("5 m × 10 m (16 ft × 33 ft)", "{{convert|5|x|10|m|abbr=on}}");
		assertExpands("3 to 5 metres (9.8 to 16.4 ft)", "{{convert|3|to|5|m}}");
		assertExpands("−5 to 5 °C (23 to 41 °F)", "{{convert|-5|to|5|C}}");
	}

	@Test
	public void testOutputCombinations() throws Exception
	{
		assertExpands("1 kilometre (1,000 m; 0.62 mi)", "{{convert|1|km|m mi}}");
		assertExpands("1 nautical mile (1.9 km; 1.2 mi)", "{{convert|1|nmi|km+mi}}");
		assertExpands("100 knots (190 km/h; 120 mph)", "{{convert|100|kn|km/h mph}}");
		assertExpands("10 U.S." + NBSP + "gal (38 L)", "{{convert|10|USgal|L|abbr=on|sp=us}}");
		assertExpands("10 L (2.6 U.S." + NBSP + "gal)", "{{convert|10|L|USgal|abbr=on|sp=us}}");
	}

	@Test
	public void testFractions() throws Exception
	{
		// the markup of {{frac}} and {{sfrac}} (with "⁄" instead of "&frasl;")
		assertExpands(frac("", "1", "2") + " mile (0.80 km)", "{{convert|1/2|mi|km}}");
		assertExpands(frac("2", "1", "2") + " inches (6.4 cm)", "{{convert|2+1/2|in|cm}}");
		assertExpands(frac("−", "1", "2") + " inch (−13 mm)", "{{convert|-1/2|in|mm}}");
		assertExpands(frac("−2", "1", "2") + " inches (−64 mm)", "{{convert|-2-1/2|in|mm}}");
		assertExpands("1 to " + frac("1", "1", "2") + " inches (25 to 38 mm)", "{{convert|1|to|1+1/2|in|mm}}");
		assertExpands("<span class=\"sfrac tion\"><span class=\"num\">1</span><span class=\"sr-only\">/</span>"
				+ "<span class=\"den\">2</span></span> inch (13 mm)", "{{convert|1//2|in|mm}}");
		assertExpands("<span class=\"sfrac\">2<span class=\"sr-only\">+</span><span class=\"tion\">"
				+ "<span class=\"num\">1</span><span class=\"sr-only\">/</span><span class=\"den\">2</span>"
				+ "</span></span> inches (64 mm)", "{{convert|2+1//2|in|mm}}");
	}

	@Test
	public void testFractionMarkupIsParsed() throws Exception
	{
		PageId pageId = new PageId(PageTitle.make(CONFIG, "Convert test"), -1);
		EngProcessedPage ast = createEngine().postprocess(pageId, "{{convert|2+1/2|in|cm}}", new MapExpansionCallback(PAGES));
		List<String> classes = new ArrayList<String>();
		collectSpanClasses(ast, classes);
		assertEquals(Arrays.asList("frac", "sr-only", "num", "den"), classes);
	}

	@Test
	public void testFracOption() throws Exception
	{
		assertExpands("10 millimetres (" + frac("", "3", "8") + " in)", "{{convert|10|mm|in|frac=16}}");
		assertExpands("1 metre (" + frac("39", "1", "4") + " in)", "{{convert|1|m|in|frac=4}}");
		assertExpands("1 metre (" + frac("3", "1", "3") + " ft)", "{{convert|1|m|ft|frac=3}}");
		assertExpands("100 millimetres (4 in)", "{{convert|100|mm|in|frac=2}}");
		assertExpands("0.1 millimetres (0 in)", "{{convert|0.1|mm|in|frac=4}}");
		assertExpands("25 millimetres (1 in)", "{{convert|25|mm|in|frac=-8}}");
		assertExpands("3 metres (9 ft " + frac("10", "1", "8") + " in)", "{{convert|3|m|ftin|frac=8}}");
		assertExpands("3 metres (" + frac("9", "3", "4") + " ft; 118 in)", "{{convert|3|m|ft in|frac=4}}");
		assertExpands("1 to 2 metres (" + frac("39", "1", "4") + " to " + frac("78", "3", "4") + " in)",
				"{{convert|1|to|2|m|in|frac=4}}");
		assertExpands("−1 metre (" + frac("−39", "1", "4") + " in)", "{{convert|-1|m|in|frac=4}}");
	}

	@Test
	public void testHandUnit() throws Exception
	{
		assertExpands("1 [[Hand (unit)|hand]] (4 inches; 10 cm)", "{{convert|1|hand}}");
		assertExpands("15.2 [[Hand (unit)|hands]] (62 inches; 157 cm)", "{{convert|15.2|hand}}");
		assertExpands("15 [[Hand (unit)|hands]] (152 cm)", "{{convert|15|hand|cm}}");
		assertExpands("16.1 [[Hand (unit)|hands]] (65 inches)", "{{convert|16.1|hand|in}}");
		assertExpands("62 inches (15.2 [[Hand (unit)|hands]])", "{{convert|62|in|hand}}");
		assertExpands("157 centimetres (15.2 [[Hand (unit)|hands]])", "{{convert|157|cm|hand}}");
		assertExpands("15.2 h (62 in; 157 cm)", "{{convert|15.2|hand|abbr=on}}");
		assertExpands("15.2 hh (62 in; 157 cm)", "{{convert|15.2|hand|abbr=hh}}");
		assertExpands("15.2 hands (62 inches; 157 cm)", "{{convert|15.2|hand|lk=off}}");
		assertExpands("15.3 [[Hand (unit)|hands]] (160 centimetres)", "{{convert|15.3|hand|cm|abbr=off}}");
		assertExpands("14.2 to 15.1 [[Hand (unit)|hands]] (58 to 61 inches; 147 to 155 cm)",
				"{{convert|14.2|to|15.1|hand}}");
		assertExpands("60 inches (15 [[Hand (unit)|hands]])", "{{convert|60|in|hand|0}}");
		assertExpands("60.5 inches (15." + frac("0", "1", "2") + " [[Hand (unit)|hands]])",
				"{{convert|60.5|in|hand|2}}");
		assertExpands("156 centimetres (15." + frac("1", "1", "2") + " [[Hand (unit)|hands]]; "
				+ frac("61", "1", "2") + " in)", "{{convert|156|cm|hand in|1|frac=2}}");
		assertExpands("1 [[Hand (unit)|hand]] (4 [[inch|inches]]; 10 [[Centimetre|cm]])",
				"{{convert|1|hand|lk=on}}");
	}

	@Test
	public void testMachUnit() throws Exception
	{
		assertExpands("Mach 1 (1,200 km/h; 760 mph)", "{{convert|1|Mach}}");
		assertExpands("Mach 0.8 (980 km/h; 610 mph)", "{{convert|0.8|Mach}}");
		assertExpands("Mach 2 (2,500 km/h)", "{{convert|2|Mach|km/h}}");
		assertExpands("1 kilometre per hour (Mach 0.00082)", "{{convert|1|km/h|Mach}}");
		assertExpands("1,000 kilometres per hour (Mach 0.82)", "{{convert|1000|km/h|Mach}}");
		assertExpands("500 mph (Mach 0.66)", "{{convert|500|mph|Mach|abbr=on}}");
		assertExpands("Mach 2.5 (1,700 mph)", "{{convert|2.5|Mach|mph|altitude_ft=30000}}");
		assertExpands("Mach 3.2 (3,450 km/h)", "{{convert|3.2|Mach|km/h|altitude_m=10000}}");
		assertExpands("Mach 1 to Mach 2 (1,200 to 2,500 km/h; 760 to 1,500 mph)", "{{convert|1|to|2|Mach}}");
		assertExpands("Mach 1 (1,200 kilometres per hour; 760 miles per hour)", "{{convert|1|Mach|abbr=off}}");
		assertExpands("[[Mach number|Mach]] 1 (340 [[metre per second|m/s]])", "{{convert|1|Mach|m/s|lk=on}}");
	}

	@Test
	public void testNauticalMileVariants() throws Exception
	{
		assertExpands("1 nautical mile (1.9 km; 1.2 mi)", "{{convert|1|oldUKnmi}}");
		assertExpands("1 nautical mile (1.9 km; 1.2 mi)", "{{convert|1|admiralty nmi}}");
		assertExpands("1 nautical mile (1.9 km; 1.2 mi)", "{{convert|1|oldUSnmi}}");
		assertExpands("2 nautical miles (3.7 km; 2.3 mi)", "{{convert|2|oldUSnmi}}");
		assertExpands("1 admiralty mile (1.9 km; 1.2 mi)", "{{convert|1|admi}}");
		assertExpands("2 admiralty miles (3.7 km)", "{{convert|2|admi|km}}");
		assertExpands("1 British nautical mile (1.9 km; 1.2 mi)", "{{convert|1|Brnmi}}");
		assertExpands("1 nmi (1.9 km; 1.2 mi)", "{{convert|1|oldUKnmi|abbr=on}}");
		assertExpands("1 nmi" + NBSP + "(admiralty) (1.9 km; 1.2 mi)", "{{convert|1|admi|abbr=on}}");
		assertExpands("1 (Brit)" + NBSP + "nmi (1.9 km; 1.2 mi)", "{{convert|1|Brnmi|abbr=on}}");
		assertExpands("1 (pre-1954" + NBSP + "US) nautical mile (1.9 km; 1.2 mi)", "{{convert|1|pre1954USnmi}}");
		assertExpands("1 (pre-1954" + NBSP + "U.S.) nautical mile (1.9 km; 1.2 mi)", "{{convert|1|pre1954USnmi|sp=us}}");
		assertExpands("1 (pre-1954" + NBSP + "U.S.) nautical mile (1.9 km; 1.2 mi)", "{{convert|1|pre1954U.S.nmi}}");
		assertExpands("1 (pre‑1954" + NBSP + "US) nmi (1.9 km; 1.2 mi)", "{{convert|1|pre1954USnmi|abbr=on}}");
		assertExpands("1-(pre-1954" + NBSP + "US) nautical-mile (1.9 km)", "{{convert|1|pre1954USnmi|km|adj=on}}");
		assertExpands("1 [[Nautical mile|admiralty mile]] (1.9 [[Kilometre|km]])", "{{convert|1|admi|km|lk=on}}");
		assertExpands("1 [[nautical mile]] (1.9 [[Kilometre|km]])", "{{convert|1|oldUKnmi|km|lk=on}}");
	}

	@Test
	public void testTimeUnits() throws Exception
	{
		assertExpands("1 hour (60 min)", "{{convert|1|h|min}}");
		assertExpands("90 minutes (1.5 h)", "{{convert|90|min|h}}");
		assertExpands("1 day (24 h)", "{{convert|1|d|h}}");
		assertExpands("2 weeks (14 d)", "{{convert|2|wk|d}}");
		assertExpands("1 year (370 d)", "{{convert|1|year|d}}");
		assertExpands("30 seconds (0.50 min)", "{{convert|30|s|min}}");
		assertExpands("1 hour (3.6 ks)", "{{convert|1|h}}");
		assertExpands("1 second (0.017 min)", "{{convert|1|s}}");
		assertExpands("5 milliseconds (0.0050 s)", "{{convert|5|ms|s}}");
		assertExpands("10 nanoseconds (0.010 μs)", "{{convert|10|ns}}");
		assertExpands("5 megaseconds (8.3 weeks)", "{{convert|5|Ms}}");
	}

	@Test
	public void testForceAndTorqueUnits() throws Exception
	{
		assertExpands("1 newton (0.22 lb<sub>f</sub>)", "{{convert|1|N}}");
		assertExpands("1 kilonewton (220 lb<sub>f</sub>)", "{{convert|1|kN}}");
		assertExpands("5 meganewtons (1,100,000 lb<sub>f</sub>)", "{{convert|5|MN}}");
		assertExpands("10 pounds-force (44 N)", "{{convert|10|lbf}}");
		assertExpands("1 kilogram-force (9.8 N; 2.2 lbf)", "{{convert|1|kgf}}");
		assertExpands("100 newtons (22 lbf)", "{{convert|100|N|lbf}}");
		assertExpands("100 newton-metres (74 lbf⋅ft)", "{{convert|100|Nm}}");
		assertExpands("100 newton-metres (74 lbf⋅ft)", "{{convert|100|N.m|lbf.ft}}");
		assertExpands("100 pound-feet (140 N⋅m)", "{{convert|100|lbft}}");
		assertExpands("100 pound force-feet (140 N⋅m)", "{{convert|100|lb.ft}}");
		assertExpands("10 kilogram force-metres (98 N⋅m; 72 lbf⋅ft)", "{{convert|10|kgf.m}}");
		// torque can be converted to energy
		assertExpands("100 newton-metres (74 ft⋅lb)", "{{convert|100|Nm|ftlb}}");
	}

	@Test
	public void testFlowUnits() throws Exception
	{
		assertExpands("10 cubic metres per second (350 cu" + NBSP + "ft/s)", "{{convert|10|m3/s}}");
		assertExpands("100 cubic feet per second (2.8 m³/s)", "{{convert|100|cuft/s}}");
		assertExpands("10 litres per second (0.35 cu" + NBSP + "ft/s)", "{{convert|10|L/s}}");
		assertExpands("500 US gallons per minute (0.032 m³/s)", "{{convert|500|USgal/min}}");
		assertExpands("10 cubic metres per hour (350 cu" + NBSP + "ft/h)", "{{convert|10|m3/h}}");
		assertExpands("1,000 barrels per day (160 m³/d)", "{{convert|1000|oilbbl/d}}");
	}

	@Test
	public void testFuelEfficiencyUnits() throws Exception
	{
		assertExpands("10 litres per 100 kilometres (28 mpg<sub>‑imp</sub>; 24 mpg<sub>‑US</sub>)",
				"{{convert|10|L/100 km}}");
		assertExpands("5 litres per 100 kilometres (47 mpg<sub>‑US</sub>)", "{{convert|5|L/100 km|mpgus}}");
		assertExpands("8 litres per 100 kilometres (35 mpg<sub>‑imp</sub>; 29 mpg<sub>‑US</sub>)",
				"{{convert|8|L/100 km|mpgimp mpgus}}");
		assertExpands("30 miles per US gallon (7.8 L/100 km; 36 mpg<sub>‑imp</sub>)", "{{convert|30|mpgus}}");
		assertExpands("30 miles per imperial gallon (9.4 L/100 km; 25 mpg<sub>‑US</sub>)",
				"{{convert|30|mpgimp}}");
		assertExpands("40 miles per US gallon (5.9 L/100 km)", "{{convert|40|mpgus|L/100 km}}");
		assertExpands("20 kilometres per litre (56 mpg<sub>‑imp</sub>; 47 mpg<sub>‑US</sub>)", "{{convert|20|km/L}}");
		assertExpands("10 [[litre]]s per 100 [[kilometre]]s (24 [[Fuel economy in automobiles#Units of measure|mpg]]"
				+ "<sub>‑[[United States customary units|US]]</sub>)", "{{convert|10|L/100 km|mpgus|lk=on}}");
		assertExpands("30 [[mile]]s per [[United States customary units|US]] [[US gallon|gallon]] (7.8 "
				+ "[[Fuel economy in automobiles#Units of measure|L/100" + NBSP + "km]]; 36 "
				+ "[[Fuel economy in automobiles#Units of measure|mpg]]<sub>‑[[Imperial units|imp]]</sub>)",
				"{{convert|30|mpgus|lk=on}}");
	}

	@Test
	public void testFrequencyUnits() throws Exception
	{
		// frequencies are converted to wavelengths
		assertExpands("60 hertz (5,000,000 m)", "{{convert|60|Hz}}");
		assertExpands("1 kilohertz (300,000 m)", "{{convert|1|kHz}}");
		assertExpands("1 megahertz (300 m)", "{{convert|1|MHz}}");
		assertExpands("1 megahertz (1,000 kHz)", "{{convert|1|MHz|kHz}}");
		assertExpands("2 gigahertz (2,000 MHz)", "{{convert|2|GHz|MHz}}");
		assertExpands("100 revolutions per minute (1.7 Hz)", "{{convert|100|rpm}}");
	}

	@Test
	public void testSiPrefixes() throws Exception
	{
		assertExpands("5 megametres (3,100 mi)", "{{convert|5|Mm}}");
		assertExpands("5 decametres (160 ft)", "{{convert|5|dam}}");
		assertExpands("5 dekameters (160 ft)", "{{convert|5|dam|sp=us}}");
		assertExpands("3 micrometres (0.00012 in)", "{{convert|3|μm}}");
		assertExpands("3 micrometres (0.00012 in)", "{{convert|3|µm}}");
		assertExpands("3 micrometres (0.00012 in)", "{{convert|3|um}}");
		assertExpands("3 gigagrams (6,600,000 lb)", "{{convert|3|Gg}}");
		assertExpands("2 megatonnes (2,000,000 long tons; 2,200,000 short tons)", "{{convert|2|Mt}}");
		assertExpands("1 megajoule (0.28 kWh)", "{{convert|1|MJ}}");
		assertExpands("1 kiloelectronvolt (0.16 fJ)", "{{convert|1|keV}}");
		assertExpands("1 megaelectronvolt (1.6×10⁻¹³ J)", "{{convert|1|MeV|J}}");
		assertExpands("1 gigawatt (1,000 MW)", "{{convert|1|GW|MW}}");
		assertExpands("1 terawatt (1.3×10⁹ hp)", "{{convert|1|TW}}");
		assertExpands("5 gigapascals (730,000 psi)", "{{convert|5|GPa}}");
		assertExpands("1 megalitre (35×10³ cu" + NBSP + "ft)", "{{convert|1|ML}}");
		assertExpands("1 decilitre (3.5 imp" + NBSP + "fl" + NBSP + "oz; 3.4 US" + NBSP + "fl" + NBSP + "oz)",
				"{{convert|1|dL}}");
		// the prefix of a squared or cubed unit applies to the base unit
		assertExpands("3 square millimetres (0.0047 sq" + NBSP + "in)", "{{convert|3|mm2}}");
		assertExpands("3 cubic kilometres (0.72 cu" + NBSP + "mi)", "{{convert|3|km3}}");
		assertExpands("5 [[megametre]]s (3,100 [[mile|mi]])", "{{convert|5|Mm|lk=on}}");
		assertExpands("1 [[kilometre]] (0.62 [[mile|mi]])", "{{convert|1|km|lk=on}}");
	}

	@Test
	public void testPerUnits() throws Exception
	{
		assertExpands("10 kilograms per hectare (8.9 lb/acre)", "{{convert|10|kg/ha}}");
		assertExpands("10 tonnes per hectare (4.0 long ton/acre; 4.5 short ton/acre)", "{{convert|10|t/ha}}");
		assertExpands("100 grams per kilometre (5.7 oz/mi)", "{{convert|100|g/km|oz/mi}}");
		assertExpands("5 kilograms per square metre (1.0 lb/sq" + NBSP + "ft)", "{{convert|5|kg/m2}}");
		assertExpands("5 kg/m² (1.0 lb/sq" + NBSP + "ft)", "{{convert|5|kg/m2|abbr=on}}");
		assertExpands("10 kilograms per square centimetre (140 psi)", "{{convert|10|kg/cm2}}");
		assertExpands("3 metres per minute (9.8 ft/min)", "{{convert|3|m/min}}");
		assertExpands("60 kilometres per minute (0.62 mi/s)", "{{convert|60|km/min}}");
		assertExpands("10 watts per square metre (0.0012 hp/sq" + NBSP + "ft)", "{{convert|10|W/m2}}");
		assertExpands("100-kilometre-per-hour (62 mph)", "{{convert|100|km/h|mph|adj=on}}");
		assertExpands("10 [[kilogram]]s per [[hectare]] (8.9 [[Pound (mass)|lb]]/[[acre]])", "{{convert|10|kg/ha|lk=on}}");
		assertExpands("10 [[Kilogram|kg]]/[[hectare|ha]] (8.9 [[Pound (mass)|lb]]/[[acre]])",
				"{{convert|10|kg/ha|lk=on|abbr=on}}");
		assertExpands("3 [[Metre per second|metres per minute]] (9.8 [[Feet per second|ft/min]])",
				"{{convert|3|m/min|lk=on}}");
		// currencies
		assertExpands("$10 per hectare ($4.0/acre)", "{{convert|10|$/ha}}");
		assertExpands("$10 per acre ($25/ha)", "{{convert|10|$/acre|$/ha}}");
		assertExpands("€10 per hectare (€4.0/acre)", "{{convert|10|€/ha|$=€}}");
	}

	@Test
	public void testEngineeringNotation() throws Exception
	{
		assertExpands("3 million kilometres (1.9×10⁶ mi)", "{{convert|3|e6km}}");
		assertExpands("2 thousand acres (8.1 km²)", "{{convert|2|e3acre}}");
		assertExpands("1.5 billion cubic metres (53×10⁹ cu" + NBSP + "ft)", "{{convert|1.5|e9m3|e9cuft}}");
		assertExpands("3×10⁶ km (1.9×10⁶ mi)", "{{convert|3|e6km|abbr=on}}");
		assertExpands("3 million km (1.9 million mi)", "{{convert|3|e6km|abbr=unit}}");
		assertExpands("1 million kilometres (0.62 million miles)", "{{convert|1|e6km|abbr=off}}");
		assertExpands("10 to 20 million kilometres (6.2×10⁶ to 12.4×10⁶ mi)", "{{convert|10|to|20|e6km}}");
		assertExpands("5 million kilometres (5,000,000 km)", "{{convert|5|e6km|km}}");
		assertExpands("3 million [[kilometre]]s (1.9×10⁶ [[mile|mi]])", "{{convert|3|e6km|lk=on}}");
	}

	@Test
	public void testCustomaryUnitLinks() throws Exception
	{
		assertExpands("10 [[US gallon]]s (38 [[Litre|L]])", "{{convert|10|USgal|L|lk=on}}");
		assertExpands("10 [[US gallon|US" + NBSP + "gal]] (38 [[Litre|L]])", "{{convert|10|USgal|L|lk=on|abbr=on}}");
		assertExpands("10 [[imperial gallon|imp" + NBSP + "gal]] (45 [[Litre|L]])", "{{convert|10|impgal|L|lk=on|abbr=on}}");
	}

	@Test
	public void testDispX() throws Exception
	{
		assertExpands("10 metres, or about 33 ft", "{{convert|10|m|ft|disp=x|, or about |}}");
		assertExpands("10 metres (~33 ft)", "{{convert|10|m|ft|disp=x| (~|)}}");
		assertExpands("10 metres ≈ 33 ft", "{{convert|10|m|ft|disp=x| ≈ |}}");
		assertExpands("10 metres&#32;33 ft", "{{convert|10|m|ft|disp=x|&#32;|}}");
	}

	@Test
	public void testDispBr() throws Exception
	{
		assertExpands("10 metres<br />33 feet", "{{convert|10|m|disp=br}}");
		assertExpands("10 metres<br />(33 feet)", "{{convert|10|m|disp=br()}}");
		assertExpands("10 m<br />33 ft", "{{convert|10|m|ft|disp=br|abbr=on}}");
		assertExpands("10 metres<br />(33 feet; 10 metres)", "{{convert|10|m|ft m|disp=br()}}");
	}

	@Test
	public void testPreunits() throws Exception
	{
		assertExpands("10 deep metres (33 deep ft)", "{{convert|10|m|ft|disp=preunit|deep |deep }}");
		assertExpands("10+ metres (33+ ft)", "{{convert|10|m|ft|disp=preunit|+}}");
		assertExpands("10+ metres (33 ft)", "{{convert|10|m|ft|disp=preunit|+|&#32;}}");
		assertExpands("10 about kilograms (22 about lb)", "{{convert|10|kg|lb|disp=preunit|about |}}");
		assertExpands("10 ~metres (33 ~ft)", "{{convert|10|m|ft|disp=preunit|~}}");
		assertExpands("10-highmetres (33-highft)", "{{convert|10|m|ft|disp=preunit|-high|}}");
		assertExpands("10 RMS watts (0.013 hp)", "{{convert|10|W|hp|adj=pre|RMS}}");
		assertExpands("10+ metres (33 ft)", "{{convert|10|m|ft|adj=pre|+}}");
		assertExpands("33 high feet (10 m)", "{{convert|10|m|ft|adj=pre|high|disp=flip}}");
	}

	@Test
	public void testAdjMidAndRoundInput() throws Exception
	{
		assertExpands("10-metre-long (33 ft)", "{{convert|10|m|ft|adj=mid|-long}}");
		assertExpands("10-metre long (33 ft)", "{{convert|10|m|ft|adj=mid|long}}");
		assertExpands("33-foot-long (10 m)", "{{convert|10|m|ft|adj=mid|-long|disp=flip}}");
		assertExpands("5-to-10-metre-long (16 to 33 ft)", "{{convert|5|to|10|m|ft|adj=mid|-long}}");
		assertExpands("10 metres (33.21 ft)", "{{convert|10.123|m|ft|adj=ri0}}");
		assertExpands("10.1 metres (33.21 ft)", "{{convert|10.123|m|ft|adj=ri1}}");
		assertExpands("10.13 metres (33.22 ft)", "{{convert|10.126|m|ft|adj=ri2}}");
	}

	@Test
	public void testSpellOption() throws Exception
	{
		assertExpands("three metres (9.8 ft)", "{{convert|3|m|spell=in}}");
		assertExpands("Three metres (9.8 ft)", "{{convert|3|m|spell=In}}");
		assertExpands("three metres (nine point eight feet)", "{{convert|3|m|spell=on}}");
		assertExpands("three meters (nine point eight feet)", "{{convert|3|m|ft|spell=on|sp=us}}");
		assertExpands("twenty-one kilometres (thirteen miles)", "{{convert|21|km|mi|spell=on}}");
		assertExpands("One hundred kilometres (sixty-two miles)", "{{convert|100|km|mi|spell=On}}");
		assertExpands("three-metre (9.8 ft)", "{{convert|3|m|spell=in|adj=on}}");
		assertExpands("one thousand two hundred and fifty metres (4,100 ft)", "{{convert|1250|m|ft|spell=in}}");
		assertExpands("one hundred three meters (338 ft)", "{{convert|103|m|ft|spell=in|sp=us}}");
		assertExpands("one million metres (1,000 km)", "{{convert|1000000|m|km|spell=in}}");
		assertExpands("two thousand five hundred metres (2.5 km)", "{{convert|2.5e3|m|km|spell=in}}");
		assertExpands("three point five metres (11 ft)", "{{convert|3.5|m|ft|spell=in}}");
		assertExpands("negative three °C (27 °F)", "{{convert|-3|C|spell=in}}");
		assertExpands("zero metres (zero feet)", "{{convert|0|m|spell=on}}");
		assertExpands("one point zero five metres (one hundred and five centimetres)", "{{convert|1.05|m|cm|spell=on}}");
		assertExpands("twelve m (thirty-nine ft)", "{{convert|12|m|ft|spell=on|abbr=on}}");
		assertExpands("one to three metres (3 ft 3 in to 9 ft 10 in)", "{{convert|1|to|3|m|spell=in}}");
		assertExpands("five feet six inches (168 cm)", "{{convert|5|ft|6|in|cm|spell=in}}");
		assertExpands("one metre (three feet three inches)", "{{convert|1|m|ftin|spell=on}}");
		// fractions
		assertExpands("one-half inch (13 mm)", "{{convert|1/2|in|mm|spell=in}}");
		assertExpands("two and a half inches (64 mm)", "{{convert|2+1/2|in|mm|spell=in}}");
		assertExpands("three-fourths inch (19 mm)", "{{convert|3/4|in|mm|spell=in|sp=us}}");
		assertExpands("One-third inch (8.5 mm)", "{{convert|1/3|in|mm|spell=In}}");
		assertExpands("ten millimetres (one-half inch)", "{{convert|10|mm|in|frac=4|spell=on}}");
		// a fraction which cannot be spelled
		assertExpands(frac("1", "1", "7") + " inches (29 mm)", "{{convert|1+1/7|in|mm|spell=in}}");
	}

	@Test
	public void testCommaOption() throws Exception
	{
		assertExpands("12345 metres (40502 ft)", "{{convert|12345|m|comma=off}}");
		assertExpands("1234 metres (4049 ft)", "{{convert|1234|m|comma=5}}");
		assertExpands("12,345 metres (40,502 ft)", "{{convert|12345|m|comma=5}}");
		assertExpands("123,456,789 metres (123,456.789 km)", "{{convert|123456789|m|km|comma=5}}");
		assertExpands(gaps("12", "345.6789") + " metres (" + gaps("12.345", "6789") + " km)",
				"{{convert|12345.6789|m|km|comma=gaps}}");
		assertExpands(gaps("12", "345", "678") + " metres (" + gaps("40", "504", "193") + " ft)",
				"{{convert|12345678|m|comma=gaps}}");
		assertExpands(gaps("1", "234", "567.123", "4") + " metres (" + gaps("1", "234.567", "123", "4") + " km)",
				"{{convert|1234567.1234|m|km|comma=gaps3}}");
		assertExpands("1000 to 2000 metres (3300 to 6600 ft)", "{{convert|1000|tonocomma|2000|m}}");
	}

	@Test
	public void testOrderOut() throws Exception
	{
		assertExpands("1.6 kilometres (1,600 m)", "{{convert|1|mi|km m|order=out}}");
		assertExpands("62 miles (100 km)", "{{convert|100|km|mi km|order=out}}");
		assertExpands("1,000 metres (3,300 ft; 0.62 mi)", "{{convert|1|km|m ft mi|order=out}}");
		assertExpands("10 kilometres (6.2 mi)", "{{convert|10|km|km mi|order=out}}");
		assertExpands("1,000 to 2,000 metres (3,300 to 6,600 ft)", "{{convert|1|to|2|km|m ft|order=out}}");
	}

	@Test
	public void testSortableOption() throws Exception
	{
		assertExpands(sortKey("7001100000000000000") + "10 metres (33 ft)", "{{convert|10|m|sortable=on}}");
		assertExpands(sortKey("7002268149999999999") + "−5 °C (23 °F)", "{{convert|-5|C|sortable=on}}");
		assertExpands(sortKey("5000000000000000000") + "0 metres (0 ft)", "{{convert|0|m|sortable=on}}");
		assertExpands(sortKey("2996800000000000000") + "−2 kilometres (−1.2 mi)", "{{convert|-2|km|mi|sortable=on}}");
		assertExpands(sortKey("7000100000000000000") + "0.001 kilometres (0.00062 mi)",
				"{{convert|0.001|km|mi|sortable=on}}");
		assertExpands(sortKey("7003200000000000000") + "2 to 3 kilometres (1.2 to 1.9 mi)",
				"{{convert|2|to|3|km|mi|sortable=on}}");
		assertExpands(sortKey("6992235214583333333") + "100 miles per US gallon (2.4 L/100 km; 120 mpg<sub>‑imp</sub>)",
				"{{convert|100|mpgus|sortable=on}}");
		assertExpands(sortKey("7000167640000000000") + "5 feet 6 inches (1.68 m)", "{{convert|5|ft|6|in|sortable=on}}");
		assertExpands(sortKey("6999101600000000000") + "1 [[Hand (unit)|hand]] (4 inches; 10 cm)",
				"{{convert|1|hand|sortable=on}}");
		assertExpands("<span data-sort-value=\"7001100000000000000♠\"><span style=\"border:1px solid\">"
				+ "7001100000000000000♠</span></span>10 metres (33 ft)", "{{convert|10|m|sortable=debug}}");
	}

	@Test
	public void testDispTable() throws Exception
	{
		assertExpands("style=\"text-align:right;\"|10\n|style=\"text-align:right;\"|33", "{{convert|10|m|disp=table}}");
		assertExpands("style=\"text-align:center;\"|10\n|style=\"text-align:center;\"|33", "{{convert|10|m|disp=tablecen}}");
		assertExpands("style=\"text-align:right;\"|10 m\n|style=\"text-align:right;\"|33 ft",
				"{{convert|10|m|ft|disp=table|abbr=on}}");
		assertExpands("style=\"text-align:right;\"|1 to 2\n|style=\"text-align:right;\"|3.3 to 6.6",
				"{{convert|1|to|2|m|ft|disp=table}}");
		assertExpands("style=\"text-align:right;\" data-sort-value=\"7001100000000000000\"|10\n"
				+ "|style=\"text-align:right;\" data-sort-value=\"7001100000000000000\"|33",
				"{{convert|10|m|ft|disp=table|sortable=on}}");
		assertExpands("style=\"text-align:right;color:red;\"|10\n|style=\"text-align:right;font-weight:bold;\"|33",
				"{{convert|10|m|ft|disp=table|stylein=color:red|styleout=font-weight:bold}}");
		assertExpands("style=\"text-align:center;\"|10 [[kilometre]]s\n|style=\"text-align:center;\"|6.2 [[mile|mi]]",
				"{{convert|10|km|mi|disp=tablecen|lk=on}}");
	}

	@Test
	public void testErrorAndUnitOrTextOptions() throws Exception
	{
		assertExpands("10 metres (33 ft)", "{{convert|10|m|ft|error=oops}}");
		assertExpands("oops", "{{convert|10|foo|ft|error=oops}}");
		assertExpands("metres", "{{convert|10|m|ft|disp=unit or text}}");
		assertExpands("foo", "{{convert|10|foo|ft|disp=unit or text}}");
	}

	@Test
	public void testMoreUnits() throws Exception
	{
		assertExpands("1 long ton 2 hundredweight (1.1 t)", "{{convert|1|LT|2|Lcwt}}");
		assertExpands("1 yard (3 ft 0 in)", "{{convert|1|yd|ftin}}");
		assertExpands("1 100 kilometres (62 mi)", "{{convert|1|100km}}");
		assertExpands("1 British thermal unit (IT) (1.1 kJ)", "{{convert|1|BTU-IT}}");
		assertExpands("1 standard gravity (9.8 m/s²)", "{{convert|1|g0}}");
		assertExpands("1 million British thermal units (1.1 GJ)", "{{convert|1|MMBtu}}");
		assertExpands("50 kilograms per litre (420 lb/US" + NBSP + "gal)", "{{convert|50|kg/L}}");
	}

	// =========================================================================

	/**
	 * @return The markup of a fraction like {{frac}}.
	 */
	private static String frac(String whole, String numerator, String denominator)
	{
		boolean hasWhole = !whole.isEmpty() && !whole.equals("−");
		return "<span class=\"frac\">" + whole
				+ (hasWhole ? "<span class=\"sr-only\">+</span>" : "")
				+ "<span class=\"num\">" + numerator + "</span>⁄<span class=\"den\">" + denominator + "</span></span>";
	}

	/**
	 * @return The markup of a number with comma=gaps.
	 */
	private static String gaps(String first, String... groups)
	{
		StringBuilder sb = new StringBuilder("<span style=\"white-space: nowrap\">").append(first);
		for (String group : groups)
		{
			sb.append("<span style=\"margin-left: 0.25em\">").append(group).append("</span>");
		}
		return sb.append("</span>").toString();
	}

	private static String sortKey(String key)
	{
		return "<span data-sort-value=\"" + key + "♠\"></span>";
	}

	private static void collectSpanClasses(WtNode node, List<String> classes)
	{
		if (node instanceof WtXmlElement && ((WtXmlElement) node).getName().equals("span"))
		{
			for (WtNode attr : ((WtXmlElement) node).getXmlAttributes())
			{
				if (attr instanceof WtXmlAttribute
						&& ((WtXmlAttribute) attr).getName().getAsString().equals("class"))
				{
					classes.add(EnginePrettyPrinter.print(((WtXmlAttribute) attr).getValue()));
				}
			}
		}
		for (WtNode child : node)
		{
			collectSpanClasses(child, classes);
		}
	}

	// =========================================================================

	private static void assertExpands(String expected, String wikitext) throws Exception
	{
		assertEquals(wikitext, expected, expand(createEngine(), wikitext));
	}

	private static void assertError(String message, String wikitext) throws Exception
	{
		String actual = expand(createEngine(), wikitext);
		assertTrue(wikitext + " gave: " + actual, actual.contains(message));
	}

	private static WtEngineImpl createEngine()
	{
		WtEngineImpl engine = new WtEngineImpl(CONFIG);
		engine.setCatchAll(false);
		return engine;
	}

	private static String expand(WtEngineImpl engine, String wikitext) throws Exception
	{
		PageId pageId = new PageId(PageTitle.make(CONFIG, "Convert test"), -1);
		EngProcessedPage ast = engine.expand(pageId, wikitext, false, new MapExpansionCallback(PAGES));
		return EnginePrettyPrinter.print(ast.getPage()).trim();
	}

	private static final class MapExpansionCallback
			implements
				ExpansionCallback
	{
		private final Map<String, String> pages;

		public MapExpansionCallback(Map<String, String> pages)
		{
			this.pages = pages;
		}

		@Override
		public FullPage retrieveWikitext(
				ExpansionFrame expansionFrame,
				PageTitle pageTitle)
		{
			String text = pages.get(pageTitle.getDenormalizedFullTitle());
			if (text == null)
			{
				return null;
			}
			return new FullPage(new PageId(pageTitle, -1), text);
		}

		@Override
		public String fileUrl(PageTitle pageTitle, int width, int height)
		{
			return null;
		}
	}
}
