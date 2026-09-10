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
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;

import org.junit.Test;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;

public class EngineConfigTest
{
	@Test
	public void testExpansionLimitDefaults() throws Exception
	{
		assertDefaultLimits(new EngineConfigImpl());
		assertDefaultLimits(DefaultConfigEnWp.generate().getEngineConfig());
		assertDefaultLimits(WikiConfigImpl.load(getClass().getResourceAsStream(
				"/org/sweble/wikitext/engine/utils/DefaultConfigEnWp.xml")).getEngineConfig());
	}

	@Test
	public void testImplementationWithoutLimitsUsesDefaults() throws Exception
	{
		// Implementations written before the limits were introduced
		EngineConfig config = new EngineConfig()
		{
			@Override
			public boolean isTrimTransparentBeforeParsing()
			{
				return false;
			}
		};

		assertDefaultLimits(config);
	}

	@Test
	public void testExpansionLimitsAreSavedAndLoaded() throws Exception
	{
		WikiConfigImpl config = DefaultConfigEnWp.generate();
		config.getEngineConfig().setMaxTemplateDepth(7);
		config.getEngineConfig().setMaxPostExpandIncludeSize(1234);
		config.getEngineConfig().setMaxRedirects(3);

		StringWriter writer = new StringWriter();
		config.save(writer);
		String saved = writer.toString();
		assertTrue(saved, saved.contains("<maxTemplateDepth>7</maxTemplateDepth>"));
		assertTrue(saved, saved.contains("<maxPostExpandIncludeSize>1234</maxPostExpandIncludeSize>"));
		assertTrue(saved, saved.contains("<maxRedirects>3</maxRedirects>"));

		WikiConfigImpl loaded = WikiConfigImpl.load(new StringReader(saved));

		assertEquals(7, loaded.getEngineConfig().getMaxTemplateDepth());
		assertEquals(1234, loaded.getEngineConfig().getMaxPostExpandIncludeSize());
		assertEquals(3, loaded.getEngineConfig().getMaxRedirects());
		assertEquals(config.getEngineConfig(), loaded.getEngineConfig());
		assertEquals(config, loaded);
	}

	@Test
	public void testMissingExpansionLimitsAreLoadedAsDefaults() throws Exception
	{
		StringWriter writer = new StringWriter();
		DefaultConfigEnWp.generate().save(writer);
		String saved = writer.toString().replaceAll("\\s*<max[A-Za-z]+>[0-9]+</max[A-Za-z]+>", "");

		WikiConfigImpl loaded = WikiConfigImpl.load(new StringReader(saved));

		assertDefaultLimits(loaded.getEngineConfig());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNegativeTemplateDepthIsRejected() throws Exception
	{
		new EngineConfigImpl().setMaxTemplateDepth(-1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNegativePostExpandIncludeSizeIsRejected() throws Exception
	{
		new EngineConfigImpl().setMaxPostExpandIncludeSize(-1);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNegativeRedirectLimitIsRejected() throws Exception
	{
		new EngineConfigImpl().setMaxRedirects(-1);
	}

	// =========================================================================

	private static void assertDefaultLimits(EngineConfig config)
	{
		assertEquals(40, config.getMaxTemplateDepth());
		assertEquals(2097152, config.getMaxPostExpandIncludeSize());
		assertEquals(2, config.getMaxRedirects());
	}
}
