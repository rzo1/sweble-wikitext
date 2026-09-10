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

package org.sweble.wikitext.engine.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Collections;

import org.junit.Test;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.ParserConfig;

public class WikiConfigTest
{
	@Test
	public void testSave() throws Exception
	{
		// We just want to know if the process works without failing fatally
		DefaultConfigEnWp.generate().save(new StringWriter());
	}

	@Test
	public void testLanguageConversionIsOnlyEnabledWithVariants() throws Exception
	{
		// Like MediaWiki on wikis without variants (English Wikipedia)
		ParserConfigImpl pc = DefaultConfigEnWp.generate().getParserConfig();
		assertFalse(pc.isLangConvTagsEnabled());

		pc.addLctVariantMapping("zh-hans", "zh-hans");
		assertTrue(pc.isLangConvTagsEnabled());

		pc.setLangConvTagsEnabled(false);
		assertFalse(pc.isLangConvTagsEnabled());
	}

	@Test
	public void testLctVariantsAreMatchedIgnoringCase() throws Exception
	{
		ParserConfigImpl pc = DefaultConfigEnWp.generate().getParserConfig();
		pc.addLctVariantMapping("zh-hans", "zh-hans");
		pc.addLctVariantMapping("zh-classical", "lzh");

		assertTrue(pc.isLctVariant("zh-hans"));
		assertTrue(pc.isLctVariant(" ZH-Hans "));
		assertEquals("zh-hans", pc.normalizeLctVariant("ZH-HANS"));
		assertTrue(pc.isLctVariant("zh-classical"));
		assertEquals("lzh", pc.normalizeLctVariant("zh-classical"));
		assertFalse(pc.isLctVariant("zh-hant"));
		assertEquals("zh-hant", pc.normalizeLctVariant("ZH-HANT"));
	}

	@Test
	public void testLoadConfig() throws Exception
	{
		WikiConfigImpl gconf = DefaultConfigEnWp.generate();

		StringWriter writer = new StringWriter();
		gconf.save(writer);

		String original = writer.toString();
		StringReader reader = new StringReader(original);
		WikiConfigImpl c = WikiConfigImpl.load(reader);

		writer = new StringWriter();
		c.save(writer);

		// First check if saved results looks identical (easier to debug)
		assertEquals(original, writer.toString());

		// Now check if the configurations are really identical
		assertEquals(gconf, c);
	}

	@Test
	public void testXmlAndGeneratedConfigAreEqual() throws Exception
	{
		WikiConfigImpl gconf = DefaultConfigEnWp.generate();
		StringWriter wgconf = new StringWriter();
		gconf.save(wgconf);

		WikiConfigImpl xconf = WikiConfigImpl.load(getClass().getResourceAsStream(
				"/org/sweble/wikitext/engine/utils/DefaultConfigEnWp.xml"));
		StringWriter wxconf = new StringWriter();
		xconf.save(wxconf);

		// First check if saved results looks identical (easier to debug)
		assertEquals(wxconf.toString(), wgconf.toString());

		// Now check if the configurations are really identical
		assertEquals(xconf, gconf);
	}

	@Test
	public void testNamespaceCaseIsSavedAndLoaded() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();
		config.getNamespace(0).setCase(NamespaceCase.CASE_SENSITIVE);
		config.getNamespace(10).setCase(NamespaceCase.CASE_SENSITIVE);

		StringWriter writer = new StringWriter();
		config.save(writer);
		String saved = writer.toString();
		assertTrue(saved, saved.contains("case=\"case-sensitive\""));
		assertTrue(saved, saved.contains("case=\"first-letter\""));

		WikiConfigImpl loaded = WikiConfigImpl.load(new StringReader(saved));

		assertEquals(NamespaceCase.CASE_SENSITIVE, loaded.getNamespace(0).getCase());
		assertEquals(NamespaceCase.CASE_SENSITIVE, loaded.getNamespace(10).getCase());
		assertEquals(NamespaceCase.FIRST_LETTER, loaded.getNamespace(2).getCase());
		for (Namespace ns : config.getNamespaces())
			assertEquals(ns.getName(), ns.getCase(), loaded.getNamespace(ns.getId()).getCase());

		writer = new StringWriter();
		loaded.save(writer);
		assertEquals(saved, writer.toString());
		assertEquals(config, loaded);
	}

	@Test
	public void testNamespacesWithoutCaseAreFirstLetter() throws Exception
	{
		// The XML configuration has no case attributes
		WikiConfigImpl config = WikiConfigImpl.load(getClass().getResourceAsStream(
				"/org/sweble/wikitext/engine/utils/DefaultConfigEnWp.xml"));

		assertFalse(config.getNamespaces().isEmpty());
		for (Namespace ns : config.getNamespaces())
			assertEquals(ns.getName(), NamespaceCase.FIRST_LETTER, ns.getCase());
	}

	private static WikiConfigImpl saveAndLoad(WikiConfigImpl config) throws Exception
	{
		StringWriter writer = new StringWriter();
		config.save(writer);
		return WikiConfigImpl.load(new StringReader(writer.toString()));
	}

	/** The nesting depth limit can be configured and persisted (issue #167). */
	@Test
	public void testMaxNestingDepthIsSavedAndLoaded() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();
		ParserConfigImpl parserConfig = config.getParserConfig();
		assertEquals(ParserConfig.DEFAULT_MAX_NESTING_DEPTH, parserConfig.getMaxNestingDepth());

		parserConfig.setMaxNestingDepth(42);
		assertEquals(42, parserConfig.getMaxNestingDepth());
		assertNotEquals(DefaultConfigEnWp.generate(), config);

		StringWriter writer = new StringWriter();
		config.save(writer);
		String saved = writer.toString();
		assertTrue(saved, saved.contains("<maxNestingDepth>42</maxNestingDepth>"));

		WikiConfigImpl loaded = WikiConfigImpl.load(new StringReader(saved));
		assertEquals(42, loaded.getParserConfig().getMaxNestingDepth());
		assertEquals(config, loaded);

		try
		{
			parserConfig.setMaxNestingDepth(0);
			fail("Expected IllegalArgumentException");
		}
		catch (IllegalArgumentException e)
		{
			// Expected
		}
	}

	/** Configurations without the element get the default (issue #167). */
	@Test
	public void testConfigWithoutMaxNestingDepthGetsTheDefault() throws Exception
	{
		StringWriter writer = new StringWriter();
		DefaultConfigEnWp.generate().save(writer);
		String saved = writer.toString().replaceAll("\\s*<maxNestingDepth>[0-9]+</maxNestingDepth>", "");
		assertFalse(saved, saved.contains("maxNestingDepth"));

		WikiConfigImpl loaded = WikiConfigImpl.load(new StringReader(saved));
		assertEquals(ParserConfig.DEFAULT_MAX_NESTING_DEPTH, loaded.getParserConfig().getMaxNestingDepth());
		assertEquals(DefaultConfigEnWp.generate(), loaded);
	}

	/** Case-insensitive aliases stay case-insensitive after loading (issue #133). */
	@Test
	public void testAliasesStayCaseInsensitiveAfterLoad() throws Exception
	{
		WikiConfigImpl generated = DefaultConfigEnWp.generate();
		assertTrue(generated.getParserConfig().isRedirectKeyword("#redirect"));

		for (WikiConfigImpl loaded : new WikiConfigImpl[] {
				saveAndLoad(generated),
				WikiConfigImpl.load(getClass().getResourceAsStream(
						"/org/sweble/wikitext/engine/utils/DefaultConfigEnWp.xml")) })
		{
			ParserConfigImpl parserConfig = loaded.getParserConfig();
			assertTrue(parserConfig.isRedirectKeyword("#REDIRECT"));
			assertTrue(parserConfig.isRedirectKeyword("#redirect"));
			assertTrue(parserConfig.isRedirectKeyword("#Redirect"));
			assertTrue(loaded.getI18nAliasById("redirect").hasAlias("#redirect"));
		}
	}

	/** The site name is part of the configuration (issue #133). */
	@Test
	public void testEqualsIncludesSiteName() throws Exception
	{
		WikiConfigImpl a = DefaultConfigEnWp.generate();
		WikiConfigImpl b = DefaultConfigEnWp.generate();
		assertEquals(a, b);

		b.setSiteName("Another Wiki");
		assertNotEquals(a, b);
	}

	/** All parser switches are part of the configuration (issue #133). */
	@Test
	public void testParserConfigEqualsIncludesAllSwitches() throws Exception
	{
		WikiConfigImpl a = DefaultConfigEnWp.generate();
		WikiConfigImpl b = DefaultConfigEnWp.generate();

		b.getParserConfig().setConvertIllegalCodePoints(!a.getParserConfig().isConvertIllegalCodePoints());
		assertNotEquals(a.getParserConfig(), b.getParserConfig());
		assertNotEquals(a, b);

		b = DefaultConfigEnWp.generate();
		b.getParserConfig().setPreserveSemiPreLeadingSpace(!a.getParserConfig().isPreserveSemiPreLeadingSpace());
		assertNotEquals(a.getParserConfig(), b.getParserConfig());
		assertNotEquals(a, b);

		b = DefaultConfigEnWp.generate();
		b.getParserConfig().setConvertIllegalCodePoints(true);
		b.getParserConfig().setPreserveSemiPreLeadingSpace(true);
		WikiConfigImpl loaded = saveAndLoad(b);
		assertEquals(b.getParserConfig(), loaded.getParserConfig());
		assertEquals(b.getParserConfig().hashCode(), loaded.getParserConfig().hashCode());
		assertEquals(b, loaded);
	}

	/** The settings of a namespace are part of the configuration (issue #133). */
	@Test
	public void testEqualsIncludesNamespaceSettings() throws Exception
	{
		WikiConfigImpl a = DefaultConfigEnWp.generate();
		WikiConfigImpl b = DefaultConfigEnWp.generate();

		b.getNamespace(0).setCase(NamespaceCase.CASE_SENSITIVE);
		assertNotEquals(a, b);
	}

	@Test
	public void testSaveWithoutDefaultNamespaceFailsWithConfigurationException() throws Exception
	{
		try
		{
			new WikiConfigImpl().save(new StringWriter());
			fail("Expected a WikiConfigurationException");
		}
		catch (WikiConfigurationException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains("default namespace"));
		}
	}

	@Test
	public void testSaveWithoutTemplateNamespaceFailsWithConfigurationException() throws Exception
	{
		WikiConfigImpl config = new WikiConfigImpl();
		NamespaceImpl main = new NamespaceImpl(0, "", "", false, false, Collections.<String> emptyList());
		config.addNamespace(main);
		config.setDefaultNamespace(main);

		try
		{
			config.save(new StringWriter());
			fail("Expected a WikiConfigurationException");
		}
		catch (WikiConfigurationException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains("template namespace"));
		}
	}

	/**
	 * The namespace prefix mapper and the formatted output are applied and
	 * nothing is printed to stderr (issue #133).
	 */
	@Test
	public void testSavedXmlIsFormattedAndUsesEnginePrefix() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();

		PrintStream oldErr = System.err;
		ByteArrayOutputStream err = new ByteArrayOutputStream();
		StringWriter writer = new StringWriter();
		System.setErr(new PrintStream(err, true, "UTF-8"));
		try
		{
			config.save(writer);
		}
		finally
		{
			System.setErr(oldErr);
		}

		String saved = writer.toString();
		assertTrue(saved, saved.contains("<swc-engine:WikiConfig "));
		assertTrue(saved, saved.contains("xmlns:swc-engine=\"org.sweble.wikitext.engine\""));
		assertFalse(saved, saved.contains("ns2:"));
		assertTrue(saved, saved.contains("\n    <siteName>"));
		assertEquals("", err.toString("UTF-8"));

		assertNotNull(config.getAsJAXBSource());
	}
}
