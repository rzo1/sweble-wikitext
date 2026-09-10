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

package org.sweble.engine.serialization;

import java.nio.charset.StandardCharsets;

import org.sweble.engine.serialization.WomSerializer.SerializationFormat;
import org.sweble.wikitext.engine.PageId;
import org.sweble.wom3.Wom3Document;
import org.sweble.wom3.swcadapter.utils.WtWom3Toolbox;
import org.w3c.dom.Document;

/**
 * Provides a WOM document of a realistic page (parsed and converted by the
 * engine) for the serialization tests.
 */
final class SerializationCoverageFixture
{
	static final String WIKITEXT = ""
			+ "= Serialization =\n"
			+ "'''Bold''', ''italic'' and a [[Main Page|link]] to [http://example.org example.org].\n"
			+ "\n"
			+ "== Lists ==\n"
			+ "* first item\n"
			+ "* second item with &amp; entity\n"
			+ "# numbered\n"
			+ "\n"
			+ "{| class=\"wikitable\"\n"
			+ "! Header\n"
			+ "|-\n"
			+ "| Cell || Other cell\n"
			+ "|}\n"
			+ "\n"
			+ "<span style=\"color:red\">Inline HTML</span> with <!-- a comment --> and <nowiki>''raw''</nowiki>.\n"
			+ "Unicode: äöü – 漢字\n";

	private static Wom3Document wom;

	// =========================================================================

	private SerializationCoverageFixture()
	{
	}

	// =========================================================================

	static synchronized Wom3Document getWom() throws Exception
	{
		if (wom == null)
		{
			WtWom3Toolbox toolbox = new WtWom3Toolbox();
			PageId pageId = toolbox.makePageId("Serialization");
			wom = toolbox.wmToWom(null, WIKITEXT, pageId).womDoc;
		}
		return wom;
	}

	/**
	 * Canonical form used to compare documents.
	 */
	static String toXml(WomSerializer serializer, Document doc) throws Exception
	{
		return utf8(serializer.serialize(doc, SerializationFormat.XML, false, false));
	}

	static String utf8(byte[] bytes)
	{
		return new String(bytes, StandardCharsets.UTF_8);
	}
}
