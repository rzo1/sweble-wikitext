/**
 * Copyright 2011 The Open Source Research Group,
 *                University of Erlangen-Nürnberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/.
 */
package org.sweble.wom3.swcadapter;

import static org.junit.Assert.assertEquals;

import java.util.Locale;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sweble.wom3.swcadapter.utils.WtWom3Toolbox;

/**
 * The conversion of XML elements to WOM must not depend on the default locale
 * of the JVM (Turkish dotless i).
 */
public class DefaultLocaleIndependenceTest
{
	private Locale defaultLocale;

	// =========================================================================

	@Before
	public void saveDefaultLocale()
	{
		defaultLocale = Locale.getDefault();
	}

	@After
	public void restoreDefaultLocale()
	{
		Locale.setDefault(defaultLocale);
	}

	// =========================================================================

	@Test
	public void testDivConversionUnderTurkishLocale() throws Exception
	{
		String wikitext = "a<DIV>b</DIV>c\n\n<div>d</div>";

		String expected = toWomXml(Locale.US, wikitext);

		assertEquals(expected, toWomXml(new Locale("tr", "TR"), wikitext));
	}

	// =========================================================================

	private static String toWomXml(Locale locale, String wikitext) throws Exception
	{
		Locale.setDefault(locale);
		WtWom3Toolbox toolbox = new WtWom3Toolbox();
		return toolbox.wmToWomXml(toolbox.makePageId("Test"), wikitext);
	}
}
