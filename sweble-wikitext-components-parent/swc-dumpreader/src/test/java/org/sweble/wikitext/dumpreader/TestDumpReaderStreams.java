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
import static org.junit.Assert.fail;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.bzip2Members;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.dump;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.gzipMembers;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.openFileDescriptors;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.page;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.read;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.split;
import static org.sweble.wikitext.dumpreader.DumpTestSupport.utf8;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sweble.wikitext.dumpreader.DumpTestSupport.CloseTrackingInputStream;
import org.sweble.wikitext.dumpreader.DumpTestSupport.CollectingDumpReader;

public class TestDumpReaderStreams
{
	private static final ExportSchemaVersion V = ExportSchemaVersion.V0_10;

	private static final Logger LOGGER = LoggerFactory.getLogger(TestDumpReaderStreams.class);

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	// =========================================================================

	@Test
	public void testMultiMemberGzip() throws Exception
	{
		byte[] xml = utf8(dump(V, page(V, "FIRST") + page(V, "SECOND")));
		byte[][] parts = split(xml, xml.length / 2);
		byte[] gz = gzipMembers(parts[0], parts[1]);

		CollectingDumpReader reader = read(gz, "dump.xml.gz");

		assertEquals(2, reader.pages.size());
		assertEquals("SECOND", reader.pages.get(1).getTitle());
		assertEquals(gz.length, reader.getCompressedBytesRead());
		assertEquals(xml.length, reader.getDecompressedBytesRead());
	}

	@Test
	public void testMultiMemberBzip2() throws Exception
	{
		byte[] xml = utf8(dump(V, page(V, "FIRST") + page(V, "SECOND")));
		byte[][] parts = split(xml, xml.length / 2);
		byte[] bz2 = bzip2Members(parts[0], parts[1]);

		CollectingDumpReader reader = read(bz2, "dump.xml.bz2");

		assertEquals(2, reader.pages.size());
		assertEquals("SECOND", reader.pages.get(1).getTitle());
		assertEquals(bz2.length, reader.getCompressedBytesRead());
		assertEquals(xml.length, reader.getDecompressedBytesRead());
	}

	@Test
	public void testDecompressedBytesReadOfPlainDump() throws Exception
	{
		byte[] xml = utf8(dump(V, page(V, "TITLE")));

		CollectingDumpReader reader = read(xml, "dump.xml");

		assertEquals(1, reader.pages.size());
		assertEquals(xml.length, reader.getDecompressedBytesRead());
		assertEquals(xml.length, reader.getCompressedBytesRead());
	}

	// =========================================================================

	@Test
	public void testCountingInputStreamSkip() throws Exception
	{
		CountingInputStream in = new CountingInputStream(new ByteArrayInputStream(new byte[100]));

		assertEquals(10, in.skip(10));
		assertEquals(10, in.getCount());

		assertEquals(90, in.skip(1000));
		assertEquals(100, in.getCount());

		assertEquals(0, in.skip(10));
		assertEquals(100, in.getCount());
		in.close();
	}

	@Test
	public void testCountingInputStreamMarkReset() throws Exception
	{
		CountingInputStream in = new CountingInputStream(new ByteArrayInputStream(new byte[100]));

		assertEquals(10, in.read(new byte[10]));
		in.mark(50);
		assertEquals(20, in.read(new byte[20]));
		assertEquals(30, in.getCount());

		in.reset();
		assertEquals(10, in.getCount());

		assertEquals(90, in.read(new byte[200], 0, 200));
		assertEquals(100, in.getCount());
		in.close();
	}

	// =========================================================================

	@Test
	public void testFileSizeOfFileConstructor() throws Exception
	{
		byte[] xml = utf8(dump(V, page(V, "TITLE")));
		byte[] gz = gzipMembers(xml);
		File file = tmp.newFile("dump.xml.gz");
		Files.write(file.toPath(), gz);

		DumpReader reader = new DumpReader(file, StandardCharsets.UTF_8, LOGGER, true)
		{
			@Override
			protected void processPage(Object mediaWiki, Object page)
			{
			}
		};
		assertEquals(gz.length, reader.getFileSize());

		reader.unmarshal();
		assertEquals(1, reader.getParsedCount());
		assertEquals(gz.length, reader.getCompressedBytesRead());
		assertEquals(xml.length, reader.getDecompressedBytesRead());
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testFileSizeOfDeprecatedFileConstructor() throws Exception
	{
		File file = tmp.newFile("dump.xml");
		Files.write(file.toPath(), utf8(dump(V, page(V, "TITLE"))));

		DumpReader reader = new DumpReader(file, LOGGER)
		{
			@Override
			protected void processPage(Object mediaWiki, Object page)
			{
			}
		};
		assertEquals(file.length(), reader.getFileSize());
		reader.close();
	}

	@Test
	public void testFileSizeOfHandlerFileConstructor() throws Exception
	{
		File file = tmp.newFile("dump.xml");
		Files.write(file.toPath(), utf8(dump(V, page(V, "TITLE"))));

		DumpReaderWithHandler reader = new DumpReaderWithHandler(
				file,
				StandardCharsets.UTF_8,
				new DumpReaderListener()
				{
					@Override
					public void handlePage(Object mediaWiki, Object page)
					{
					}

					@Override
					public boolean handleRevisionOrUploadOrLogitem(Object page, Object revision)
					{
						return true;
					}
				},
				LOGGER,
				false);
		assertEquals(file.length(), reader.getFileSize());

		reader.unmarshal();
		assertEquals(1, reader.getParsedCount());
	}

	@Test
	public void testFileSizeOfStreamConstructors() throws Exception
	{
		byte[] xml = utf8(dump(V, page(V, "TITLE")));

		DumpReader withSize = new CollectingDumpReaderWithSize(new ByteArrayInputStream(xml), 1234);
		assertEquals(1234, withSize.getFileSize());
		withSize.close();

		DumpReader withoutSize = new CollectingDumpReader(new ByteArrayInputStream(xml), StandardCharsets.UTF_8, "dump.xml", false);
		assertEquals(-1, withoutSize.getFileSize());
		withoutSize.close();
	}

	// =========================================================================

	@Test
	public void testConstructorClosesStreamWhenInputIsEmpty() throws Exception
	{
		assertStreamClosedOnFailure(new byte[0], "dump.xml");
	}

	@Test
	public void testConstructorClosesStreamWhenNamespaceIsUnknown() throws Exception
	{
		assertStreamClosedOnFailure(utf8("<mediawiki xmlns=\"http://example.org/\"/>"), "dump.xml");
	}

	@Test
	public void testConstructorClosesStreamWhenCompressionIsBroken() throws Exception
	{
		assertStreamClosedOnFailure(utf8("not gzip at all"), "dump.xml.gz");
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testDeprecatedFileConstructorClosesFileOnFailure() throws Exception
	{
		final File empty = tmp.newFile("empty.xml");

		assertNoFileDescriptorLeak(new Construction()
		{
			@Override
			public void run() throws Exception
			{
				new DumpReader(empty, LOGGER)
				{
					@Override
					protected void processPage(Object mediaWiki, Object page)
					{
					}
				};
			}
		});
	}

	@Test
	public void testFileConstructorClosesFileOnFailure() throws Exception
	{
		final File empty = tmp.newFile("empty.xml");

		assertNoFileDescriptorLeak(new Construction()
		{
			@Override
			public void run() throws Exception
			{
				new DumpReader(empty, StandardCharsets.UTF_8, LOGGER, true)
				{
					@Override
					protected void processPage(Object mediaWiki, Object page)
					{
					}
				};
			}
		});
	}

	// =========================================================================

	private interface Construction
	{
		void run() throws Exception;
	}

	private static void assertNoFileDescriptorLeak(Construction construction)
	{
		Assume.assumeTrue(openFileDescriptors() >= 0);

		int before = openFileDescriptors();
		for (int i = 0; i < 50; ++i)
		{
			try
			{
				construction.run();
				fail("Expected construction to fail");
			}
			catch (Exception e)
			{
				// Expected
			}
		}
		int after = openFileDescriptors();

		assertTrue("Leaked " + (after - before) + " file descriptors", after - before < 10);
	}

	private static void assertStreamClosedOnFailure(byte[] data, String url)
	{
		CloseTrackingInputStream in = new CloseTrackingInputStream(data);
		try
		{
			new CollectingDumpReader(in, StandardCharsets.UTF_8, url, false);
			fail("Expected construction to fail");
		}
		catch (Exception e)
		{
			// Expected
		}
		assertTrue("Input stream was not closed", in.closed);
	}

	private static final class CollectingDumpReaderWithSize
			extends
				DumpReader
	{
		CollectingDumpReaderWithSize(InputStream in, long fileSize) throws Exception
		{
			super(in, StandardCharsets.UTF_8, "dump.xml", fileSize, LOGGER, false);
		}

		@Override
		protected void processPage(Object mediaWiki, Object page)
		{
		}
	}
}
