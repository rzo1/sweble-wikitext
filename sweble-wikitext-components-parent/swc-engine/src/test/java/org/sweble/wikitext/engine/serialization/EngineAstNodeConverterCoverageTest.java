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

package org.sweble.wikitext.engine.serialization;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.nodes.WtBody.WtBodyImpl;
import org.sweble.wikitext.parser.nodes.WtBody.WtEmptyBody;
import org.sweble.wikitext.parser.nodes.WtBold;
import org.sweble.wikitext.parser.nodes.WtExternalLink;
import org.sweble.wikitext.parser.nodes.WtInternalLink;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtNodeList.WtNodeListImpl;
import org.sweble.wikitext.parser.nodes.WtParsedWikitextPage;
import org.sweble.wikitext.parser.nodes.WtPreproWikitextPage;
import org.sweble.wikitext.parser.nodes.WtSection;
import org.sweble.wikitext.parser.nodes.WtTable;
import org.sweble.wikitext.parser.nodes.WtTagExtension;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.nodes.WtText;
import org.sweble.wikitext.parser.nodes.WtXmlAttributes.WtXmlAttributesImpl;
import org.sweble.wikitext.parser.nodes.WtXmlElement;

import de.fau.cs.osr.ptk.common.serialization.AstConverterBase;
import de.fau.cs.osr.ptk.common.serialization.AstNodeConverterBase;
import de.fau.cs.osr.ptk.common.serialization.SimpleTypeNameMapper;

/**
 * Checks the type names and the converter configuration provided by the
 * {@link EngineAstNodeConverter}.
 */
public class EngineAstNodeConverterCoverageTest
{
	private static final Object[][] TYPE_NAMES = {
			{ WtBold.class, "b" },
			{ WtExternalLink.class, "extlink" },
			{ WtInternalLink.class, "intlink" },
			{ WtNodeListImpl.class, "list" },
			{ WtParsedWikitextPage.class, "parsed" },
			{ WtPreproWikitextPage.class, "prepro" },
			{ WtSection.class, "section" },
			{ WtTable.class, "table" },
			{ WtTagExtension.class, "tagext" },
			{ WtTemplate.class, "template" },
			{ WtText.class, "text" },
			{ WtXmlElement.class, "elem" } };

	// =========================================================================

	@Test
	public void testTypeNameMapperMapsBothWays() throws Exception
	{
		SimpleTypeNameMapper tnm = EngineAstNodeConverter.getTypeNameMapper();

		for (Object[] entry : TYPE_NAMES)
		{
			Class<?> type = (Class<?>) entry[0];
			String name = (String) entry[1];

			assertEquals(name, tnm.nameForType(type));
			assertSame(type, tnm.typeForName(name));
		}
	}

	@Test
	public void testTypeNameMapperIsShared() throws Exception
	{
		assertSame(EngineAstNodeConverter.getTypeNameMapper(), EngineAstNodeConverter.getTypeNameMapper());
	}

	@Test(expected = UnsupportedOperationException.class)
	public void testTypeNameMapperIsReadOnly() throws Exception
	{
		EngineAstNodeConverter.getTypeNameMapper().add(WtBold.class, "bold");
	}

	@Test
	public void testSetupConfiguresNodeConverter() throws Exception
	{
		WikiConfig config = DefaultConfigEnWp.generate();
		InspectableConverter converter = new InspectableConverter();

		EngineAstNodeConverter.setup(config, converter);

		// Basic types and node factory
		assertSame(WtText.class, converter.getStringNodeType());
		assertSame(config.getNodeFactory(), converter.getNodeFactory());

		// Type names
		for (Object[] entry : TYPE_NAMES)
		{
			assertEquals(entry[1], converter.alias((Class<?>) entry[0]));
			assertSame(entry[0], converter.classFor((String) entry[1]));
		}

		// Output minification
		assertTrue(converter.typeInfoSuppressed(WtNodeListImpl.class));
		assertTrue(converter.typeInfoSuppressed(WtBodyImpl.class));
		assertTrue(converter.typeInfoSuppressed(WtEmptyBody.class));
		assertTrue(converter.typeInfoSuppressed(WtXmlAttributesImpl.class));
		assertFalse(converter.typeInfoSuppressed(WtBold.class));
		assertFalse(converter.typeInfoSuppressed(WtTemplate.class));
	}

	@Test
	public void testSetupOfPlainConverterOnlyRegistersTypeNames() throws Exception
	{
		WikiConfig config = DefaultConfigEnWp.generate();
		InspectableConverter converter = new InspectableConverter();

		EngineAstNodeConverter.setup((AstConverterBase) converter);

		assertEquals("template", converter.alias(WtTemplate.class));
		assertSame(WtTemplate.class, converter.classFor("template"));

		assertNotSame(WtText.class, converter.getStringNodeType());
		assertNotSame(config.getNodeFactory(), converter.getNodeFactory());
		assertFalse(converter.typeInfoSuppressed(WtNodeListImpl.class));
	}

	// =========================================================================

	/**
	 * Exposes the configuration of the converter.
	 */
	private static final class InspectableConverter
			extends
				AstNodeConverterBase<WtNode>
	{
		InspectableConverter()
		{
			super(WtNode.class);
		}

		String alias(Class<?> type)
		{
			return getTypeAlias(type);
		}

		Class<?> classFor(String alias)
		{
			return getClassForAlias(alias);
		}

		boolean typeInfoSuppressed(Class<?> type)
		{
			return isTypeInfoSuppressed(type);
		}
	}
}
