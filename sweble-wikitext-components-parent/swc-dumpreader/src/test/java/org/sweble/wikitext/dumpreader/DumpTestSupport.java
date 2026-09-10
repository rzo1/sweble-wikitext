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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.slf4j.LoggerFactory;
import org.sweble.wikitext.dumpreader.model.DumpConverter;
import org.sweble.wikitext.dumpreader.model.Page;

/**
 * Builds small inline dumps and streams for the dump reader tests.
 */
final class DumpTestSupport
{
	static final String TIMESTAMP = "2012-05-21T11:11:11Z";

	private DumpTestSupport()
	{
	}

	// =========================================================================

	static String versionNumber(ExportSchemaVersion version)
	{
		return version.name().substring(1).replace('_', '.');
	}

	static String dump(ExportSchemaVersion version, String body)
	{
		return dump(version, "", body);
	}

	/**
	 * @param prolog
	 *            Inserted between the XML declaration and the root element.
	 */
	static String dump(ExportSchemaVersion version, String prolog, String body)
	{
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
				+ prolog
				+ "<mediawiki xmlns=\"" + version.getMediaWikiNamespace() + "\""
				+ " version=\"" + versionNumber(version) + "\" xml:lang=\"en\">"
				+ body
				+ "</mediawiki>\n";
	}

	static String page(ExportSchemaVersion version, String title, String revision)
	{
		String ns = (version == ExportSchemaVersion.V0_5) ? "" : "<ns>0</ns>";
		return "<page><title>" + title + "</title>" + ns + "<id>10</id>" + revision + "</page>";
	}

	static String page(ExportSchemaVersion version, String title)
	{
		return page(version, title, revision(version, "<comment>COMMENT</comment>", "<text xml:space=\"preserve\">TEXT</text>"));
	}

	/**
	 * A revision in the element order of the given export version.
	 */
	static String revision(ExportSchemaVersion version, String comment, String text)
	{
		boolean hasParentId = (version != ExportSchemaVersion.V0_5) && (version != ExportSchemaVersion.V0_6);

		StringBuilder b = new StringBuilder();
		b.append("<revision><id>1</id>");
		if (hasParentId)
			b.append("<parentid>2</parentid>");
		b.append("<timestamp>" + TIMESTAMP + "</timestamp>");
		b.append("<contributor><username>USERNAME</username><id>3</id></contributor>");
		b.append(comment);
		switch (version)
		{
			case V0_5:
				b.append(text);
				break;
			case V0_6:
			case V0_7:
				b.append("<sha1>SHA1</sha1>").append(text);
				break;
			case V0_8:
			case V0_9:
				b.append(text).append("<sha1>SHA1</sha1><model>wikitext</model><format>text/x-wiki</format>");
				break;
			case V0_10:
				b.append("<model>wikitext</model><format>text/x-wiki</format>").append(text).append("<sha1>SHA1</sha1>");
				break;
			case V0_11:
				b.append("<origin>1</origin><model>wikitext</model><format>text/x-wiki</format>").append(text).append("<sha1>SHA1</sha1>");
				break;
			default:
				throw new AssertionError(version);
		}
		b.append("</revision>");
		return b.toString();
	}

	/**
	 * A revision document as read by {@link DumpUnmarshaller}.
	 */
	static String revisionFragment(ExportSchemaVersion version, String comment, String text)
	{
		return revision(version, comment, text).replaceFirst(
				"<revision>",
				"<revision xmlns=\"" + version.getMediaWikiNamespace() + "\">");
	}

	static String logItem(int id)
	{
		return "<logitem><id>" + id + "</id><timestamp>" + TIMESTAMP + "</timestamp>"
				+ "<contributor><username>USERNAME</username><id>3</id></contributor>"
				+ "<type>block</type><action>block</action><logtitle>User:Example</logtitle></logitem>";
	}

	// =========================================================================

	static byte[] utf8(String s)
	{
		return s.getBytes(StandardCharsets.UTF_8);
	}

	static byte[][] split(byte[] data, int at)
	{
		return new byte[][] {
				Arrays.copyOfRange(data, 0, at),
				Arrays.copyOfRange(data, at, data.length) };
	}

	static byte[] gzipMembers(byte[]... members) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] member : members)
		{
			GZIPOutputStream gz = new GZIPOutputStream(out);
			gz.write(member);
			gz.finish();
		}
		return out.toByteArray();
	}

	static byte[] bzip2Members(byte[]... members) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] member : members)
		{
			BZip2CompressorOutputStream bz = new BZip2CompressorOutputStream(new NonClosingOutputStream(out));
			bz.write(member);
			bz.close();
		}
		return out.toByteArray();
	}

	/**
	 * @return The number of open file descriptors of this process or -1 if the
	 *         platform does not tell.
	 */
	static int openFileDescriptors()
	{
		String[] fds = new File("/dev/fd").list();
		return (fds == null) ? -1 : fds.length;
	}

	// =========================================================================

	static CollectingDumpReader read(byte[] data, String url) throws Exception
	{
		return read(new ByteArrayInputStream(data), url, StandardCharsets.UTF_8);
	}

	static CollectingDumpReader read(InputStream in, String url, Charset encoding) throws Exception
	{
		CollectingDumpReader reader = new CollectingDumpReader(in, encoding, url, false);
		reader.unmarshal();
		return reader;
	}

	// =========================================================================

	static class CollectingDumpReader
			extends
				DumpReader
	{
		final List<Object> mediaWikis = new ArrayList<Object>();

		final List<Page> pages = new ArrayList<Page>();

		CollectingDumpReader(
				InputStream is,
				Charset encoding,
				String url,
				boolean useSchema) throws Exception
		{
			super(is, encoding, url, LoggerFactory.getLogger(CollectingDumpReader.class), useSchema);
		}

		@Override
		protected void processPage(Object mediaWiki, Object page)
		{
			mediaWikis.add(mediaWiki);
			pages.add(new DumpConverter().convertPage(page));
		}

		String firstText()
		{
			return pages.get(0).getRevisions().get(0).getText();
		}
	}

	/**
	 * Returns at most a few bytes per read and never claims more are
	 * available.
	 */
	static final class TrickleInputStream
			extends
				InputStream
	{
		private final InputStream in;

		private final int chunk;

		TrickleInputStream(InputStream in, int chunk)
		{
			this.in = in;
			this.chunk = chunk;
		}

		@Override
		public int read() throws IOException
		{
			return in.read();
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException
		{
			if (len == 0)
				return 0;
			return in.read(b, off, Math.min(len, chunk));
		}

		@Override
		public int available()
		{
			return 0;
		}

		@Override
		public void close() throws IOException
		{
			in.close();
		}
	}

	static final class CloseTrackingInputStream
			extends
				ByteArrayInputStream
	{
		boolean closed;

		CloseTrackingInputStream(byte[] data)
		{
			super(data);
		}

		@Override
		public void close() throws IOException
		{
			closed = true;
			super.close();
		}
	}

	private static final class NonClosingOutputStream
			extends
				OutputStream
	{
		private final OutputStream out;

		NonClosingOutputStream(OutputStream out)
		{
			this.out = out;
		}

		@Override
		public void write(int b) throws IOException
		{
			out.write(b);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException
		{
			out.write(b, off, len);
		}

		@Override
		public void close() throws IOException
		{
			out.flush();
		}
	}
}
