/**
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

package org.sweble.wikitext.engine.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

/**
 * equals(), hashCode(), compareTo() and serialization of the entries of a
 * wiki configuration (issue #133).
 */
public class ConfigEntryContractTest
{
	private static <T extends Comparable<? super T>> void assertConsistent(T a, T b)
	{
		assertEquals(a + " / " + b, a.equals(b), a.compareTo(b) == 0);
		assertEquals(b + " / " + a, b.equals(a), b.compareTo(a) == 0);
		assertEquals(Integer.signum(a.compareTo(b)), -Integer.signum(b.compareTo(a)));
		if (a.equals(b))
			assertEquals(a.hashCode(), b.hashCode());
	}

	private static Object serializeAndDeserialize(Object o) throws Exception
	{
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes))
		{
			out.writeObject(o);
		}
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
		{
			return in.readObject();
		}
	}

	@Test
	public void testNamespaceCompareToIsConsistentWithEquals()
	{
		NamespaceImpl ns = new NamespaceImpl(10, "Template", "Template", true, false, Arrays.asList("T"));

		assertConsistent(ns, new NamespaceImpl(10, "Template", "Template", true, false, Arrays.asList("T")));
		assertConsistent(ns, new NamespaceImpl(11, "Template", "Template", true, false, Arrays.asList("T")));
		assertConsistent(ns, new NamespaceImpl(10, "Vorlage", "Template", true, false, Arrays.asList("T")));
		assertConsistent(ns, new NamespaceImpl(10, "Template", "Tmpl", true, false, Arrays.asList("T")));
		assertConsistent(ns, new NamespaceImpl(10, "Template", "Template", false, false, Arrays.asList("T")));
		assertConsistent(ns, new NamespaceImpl(10, "Template", "Template", true, true, Arrays.asList("T")));
		assertConsistent(ns, new NamespaceImpl(10, "Template", "Template", true, false, NamespaceCase.CASE_SENSITIVE, Arrays.asList("T")));
		assertConsistent(ns, new NamespaceImpl(10, "Template", "Template", true, false, Arrays.asList("T", "V")));
		assertConsistent(ns, new NamespaceImpl(10, "Template", "Template", true, false, Collections.<String> emptyList()));

		assertNotEquals(ns, new NamespaceImpl(10, "Template", "Template", true, false, NamespaceCase.CASE_SENSITIVE, Arrays.asList("T")));
	}

	@Test
	public void testInterwikiCompareToIsConsistentWithEquals()
	{
		InterwikiImpl iw = new InterwikiImpl("de", "https://de.wikipedia.org/wiki/$1", true, false);

		assertConsistent(iw, new InterwikiImpl("de", "https://de.wikipedia.org/wiki/$1", true, false));
		assertConsistent(iw, new InterwikiImpl("en", "https://de.wikipedia.org/wiki/$1", true, false));
		assertConsistent(iw, new InterwikiImpl("de", "https://example.org/wiki/$1", true, false));
		assertConsistent(iw, new InterwikiImpl("de", null, true, false));
		assertConsistent(iw, new InterwikiImpl("de", "https://de.wikipedia.org/wiki/$1", false, false));
		assertConsistent(iw, new InterwikiImpl("de", "https://de.wikipedia.org/wiki/$1", true, true));
	}

	@Test
	public void testI18nAliasCompareToIsConsistentWithEquals()
	{
		I18nAliasImpl alias = new I18nAliasImpl("redirect", false, Arrays.asList("#REDIRECT"));

		assertConsistent(alias, new I18nAliasImpl("redirect", false, Arrays.asList("#REDIRECT")));
		assertConsistent(alias, new I18nAliasImpl("notoc", false, Arrays.asList("#REDIRECT")));
		assertConsistent(alias, new I18nAliasImpl("redirect", true, Arrays.asList("#REDIRECT")));
		assertConsistent(alias, new I18nAliasImpl("redirect", false, Arrays.asList("#WEITERLEITUNG")));
		assertConsistent(alias, new I18nAliasImpl("redirect", false, Arrays.asList("#REDIRECT", "#WEITERLEITUNG")));
		assertConsistent(alias, new I18nAliasImpl("redirect", false, Collections.<String> emptyList()));
	}

	@Test
	public void testI18nAliasSerialization() throws Exception
	{
		I18nAliasImpl alias = new I18nAliasImpl("redirect", false, Arrays.asList("#REDIRECT", "#WEITERLEITUNG"));
		I18nAliasImpl copy = (I18nAliasImpl) serializeAndDeserialize(alias);

		assertEquals(alias, copy);
		assertEquals(alias.hashCode(), copy.hashCode());
		assertTrue(copy.hasAlias("#redirect"));
		assertTrue(copy.hasAlias("#Weiterleitung"));

		I18nAliasImpl caseSensitive = new I18nAliasImpl("pagename", true, Arrays.asList("PAGENAME"));
		I18nAliasImpl caseSensitiveCopy = (I18nAliasImpl) serializeAndDeserialize(caseSensitive);

		assertEquals(caseSensitive, caseSensitiveCopy);
		assertTrue(caseSensitiveCopy.hasAlias("PAGENAME"));
		assertFalse(caseSensitiveCopy.hasAlias("pagename"));
	}

	@Test
	public void testNamespaceAndInterwikiSerialization() throws Exception
	{
		NamespaceImpl ns = new NamespaceImpl(10, "Template", "Template", true, false, NamespaceCase.CASE_SENSITIVE, Arrays.asList("T"));
		assertEquals(ns, serializeAndDeserialize(ns));

		InterwikiImpl iw = new InterwikiImpl("de", "https://de.wikipedia.org/wiki/$1", true, false);
		assertEquals(iw, serializeAndDeserialize(iw));
	}
}
