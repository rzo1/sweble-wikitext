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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Loading of the unit data, which ships in the separate artifact
 * swc-convert-data (a dependency of the tests).
 */
public class ConvertDataTest
{
	/** Sees only the classes of the JDK. */
	private static final ClassLoader EMPTY = new ClassLoader(null)
	{
	};

	@Test
	public void testDataOfSwcConvertDataIsFound()
	{
		assertTrue(ConvertData.isAvailable());
		// the first and a later entry of [all_units]
		assertNotNull(ConvertData.get().getUnit("Gy"));
		assertNotNull(ConvertData.get().getUnit("m"));
	}

	@Test
	public void testLoadFromClassLoader()
	{
		ConvertData data = ConvertData.load(ConvertDataTest.class.getClassLoader());
		assertNotNull(data);
		assertNotNull(data.getUnit("m"));
		assertNull(data.getUnit("no such unit"));
		// km is not listed, it's m with an SI prefix; it has a link exception
		assertNull(data.getUnit("km"));
		assertEquals("Kilometre", data.getLinkException("km"));
	}

	@Test
	public void testLoadWithoutDataReturnsNull()
	{
		assertNull(ConvertData.load(EMPTY));
	}

	@Test
	public void testMissingDataMessageNamesTheArtifact()
	{
		assertTrue(ConvertData.MISSING_DATA.contains("io.github.rzo1.org.sweble.wikitext:swc-convert-data"));
	}

	@Test
	public void testResourceIsNotInAPackage()
	{
		String dir = ConvertData.RESOURCE.substring(0, ConvertData.RESOURCE.lastIndexOf('/'));
		for (String segment : dir.split("/"))
		{
			if (!isJavaIdentifier(segment))
			{
				return;
			}
		}
		fail("The directory of " + ConvertData.RESOURCE + " is a valid package name");
	}

	private static boolean isJavaIdentifier(String s)
	{
		if (s.isEmpty() || !Character.isJavaIdentifierStart(s.charAt(0)))
		{
			return false;
		}
		for (int i = 1; i < s.length(); i++)
		{
			if (!Character.isJavaIdentifierPart(s.charAt(i)))
			{
				return false;
			}
		}
		return true;
	}
}
