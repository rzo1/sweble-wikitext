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

/**
 * Expansion tests for {{convert}}.
 *
 * Unless noted otherwise, the expected values are the output of English
 * Wikipedia (Module:Convert) retrieved via
 * https://en.wikipedia.org/w/api.php?action=expandtemplates in September
 * 2026. As in the existing convert fixtures, the "&amp;nbsp;" which Wikipedia
 * puts between a value and a unit symbol is written as a plain space, "×10³"
 * is written with superscript characters instead of "&lt;sup&gt;" markup and
 * the error and warning markup is not compared.
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
		// Wikipedia formats fractions with HTML markup, this is its visible text
		assertExpands("1⁄2 mile (0.80 km)", "{{convert|1/2|mi|km}}");
		// the "+" is only visible for screen readers on Wikipedia
		assertExpands("2+1⁄2 inches (6.4 cm)", "{{convert|2+1/2|in|cm}}");
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
