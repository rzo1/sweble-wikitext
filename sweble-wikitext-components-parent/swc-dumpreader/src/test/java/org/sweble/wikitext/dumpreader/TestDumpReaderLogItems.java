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

package org.sweble.wikitext.dumpreader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.dump;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.logItem;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.page;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.read;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.utf8;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import jakarta.xml.bind.ValidationEvent;
import jakarta.xml.bind.ValidationEventLocator;

import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.sweble.wikitext.dumpreader.DumpTestSupport.CollectingDumpReader;

public class TestDumpReaderLogItems
{
	/** Export versions with {@code <logitem>} elements below the root. */
	static final EnumSet<ExportSchemaVersion> ROOT_LOG_ITEM_VERSIONS = EnumSet.range(
			ExportSchemaVersion.V0_7,
			ExportSchemaVersion.V0_11);

	static String dumpWithLogItems(ExportSchemaVersion version)
	{
		return dump(version, page(version, "TITLE") + logItem(1) + logItem(2) + logItem(3));
	}

	// =========================================================================

	@Test
	public void testLogItemsDoNotAccumulate() throws Exception
	{
		for (ExportSchemaVersion version : ROOT_LOG_ITEM_VERSIONS)
		{
			CollectingDumpReader reader = read(utf8(dumpWithLogItems(version)), "dump.xml");

			assertEquals(1, reader.pages.size());
			assertTrue(version.name(), logItemsOf(version, reader.mediaWikis.get(0)).isEmpty());
		}
	}

	@Test
	public void testLogItemsAreDeliveredToProcessLogItem() throws Exception
	{
		for (ExportSchemaVersion version : ROOT_LOG_ITEM_VERSIONS)
		{
			final List<Object> mediaWikis = new ArrayList<Object>();
			final List<Object> logItems = new ArrayList<Object>();
			final List<String> events = new ArrayList<String>();

			DumpReader reader = new DumpReader(
					new ByteArrayInputStream(utf8(dumpWithLogItems(version))),
					StandardCharsets.UTF_8,
					"dump.xml",
					LoggerFactory.getLogger(getClass()),
					true)
			{
				@Override
				protected void processPage(Object mediaWiki, Object page)
				{
				}

				@Override
				protected void processLogItem(Object mediaWiki, Object logItem)
				{
					mediaWikis.add(mediaWiki);
					logItems.add(logItem);
				}

				@Override
				protected boolean processEvent(
						ValidationEvent ve,
						ValidationEventLocator vel)
				{
					events.add(ve.getMessage());
					return true;
				}
			};
			reader.unmarshal();

			assertTrue(version.name() + ": " + events, events.isEmpty());
			assertEquals(version.name(), 1, reader.getParsedCount());
			assertEquals(version.name(), 3, logItems.size());
			for (int i = 0; i < logItems.size(); ++i)
			{
				Object id = logItems.get(i).getClass().getMethod("getId").invoke(logItems.get(i));
				assertEquals(BigInteger.valueOf(i + 1), id);
			}
			assertTrue(version.getMediaWikiType().isInstance(mediaWikis.get(0)));
			assertTrue(logItemsOf(version, mediaWikis.get(0)).isEmpty());
		}
	}

	@Test
	public void testLogItemsAreDeliveredToTheListener() throws Exception
	{
		final List<Object> pages = new ArrayList<Object>();
		final List<Object> logItems = new ArrayList<Object>();

		DumpReaderLogItemListener listener = new DumpReaderLogItemListener()
		{
			@Override
			public void handlePage(Object mediaWiki, Object page)
			{
				pages.add(page);
			}

			@Override
			public boolean handleRevisionOrUploadOrLogitem(Object page, Object revision)
			{
				return true;
			}

			@Override
			public void handleLogItem(Object mediaWiki, Object logItem)
			{
				logItems.add(logItem);
			}
		};

		DumpReaderWithHandler reader = new DumpReaderWithHandler(
				new ByteArrayInputStream(utf8(dumpWithLogItems(ExportSchemaVersion.V0_11))),
				StandardCharsets.UTF_8,
				listener,
				"dump.xml",
				LoggerFactory.getLogger(getClass()),
				false);
		reader.unmarshal();

		assertEquals(1, pages.size());
		assertEquals(3, logItems.size());
	}

	@Test
	public void testListenerWithoutLogItemHandlerIgnoresLogItems() throws Exception
	{
		final List<Object> pages = new ArrayList<Object>();

		DumpReaderListener listener = new DumpReaderListener()
		{
			@Override
			public void handlePage(Object mediaWiki, Object page)
			{
				pages.add(page);
			}

			@Override
			public boolean handleRevisionOrUploadOrLogitem(Object page, Object revision)
			{
				return true;
			}
		};

		DumpReaderWithHandler reader = new DumpReaderWithHandler(
				new ByteArrayInputStream(utf8(dumpWithLogItems(ExportSchemaVersion.V0_10))),
				StandardCharsets.UTF_8,
				listener,
				"dump.xml",
				LoggerFactory.getLogger(getClass()),
				false);
		reader.unmarshal();

		assertEquals(1, pages.size());
	}

	// =========================================================================

	static List<?> logItemsOf(ExportSchemaVersion version, Object mediaWiki) throws Exception
	{
		return (List<?>) version.getMediaWikiType().getMethod("getLogitem").invoke(mediaWiki);
	}
}
