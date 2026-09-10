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

package org.sweble.wikitext.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeSet;

import org.junit.Test;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.ext.generic.GenericTagExtension;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtNodeList;
import org.sweble.wikitext.parser.nodes.WtTagExtension;
import org.sweble.wikitext.parser.nodes.WtTagExtensionBody;

public class TagExtensionBaseTest
{
	private final WikiConfigImpl config = DefaultConfigEnWp.generate();

	@Test
	public void testCompareToIsConsistentWithEquals() throws Exception
	{
		TagExtensionBase generic = new GenericTagExtension(config, "x");
		TagExtensionBase other = new OtherTagExtension(config, "x");

		assertFalse(generic.equals(other));
		assertTrue(generic.compareTo(other) != 0);
		assertEquals(-Integer.signum(generic.compareTo(other)), Integer.signum(other.compareTo(generic)));

		TagExtensionBase sameAsGeneric = new GenericTagExtension(config, "x");
		assertTrue(generic.equals(sameAsGeneric));
		assertEquals(0, generic.compareTo(sameAsGeneric));
	}

	@Test
	public void testCompareToOrdersById() throws Exception
	{
		TagExtensionBase a = new OtherTagExtension(config, "a");
		TagExtensionBase b = new GenericTagExtension(config, "b");

		assertTrue(a.compareTo(b) < 0);
		assertTrue(b.compareTo(a) > 0);
	}

	@Test
	public void testSortedSetKeepsTagExtensionsOfDifferentClasses() throws Exception
	{
		TreeSet<TagExtensionBase> set = new TreeSet<TagExtensionBase>(Arrays.asList(
				new GenericTagExtension(config, "x"),
				new OtherTagExtension(config, "x")));

		assertEquals(2, set.size());
	}

	// =========================================================================

	private static final class OtherTagExtension
			extends
				TagExtensionBase
	{
		private static final long serialVersionUID = 1L;

		public OtherTagExtension(WikiConfigImpl config, String id)
		{
			super(config, id);
		}

		@Override
		public WtNode invoke(
				ExpansionFrame frame,
				WtTagExtension tagExt,
				Map<String, WtNodeList> attrs,
				WtTagExtensionBody body)
		{
			return tagExt;
		}
	}
}
