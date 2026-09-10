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

package org.sweble.wikitext.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;

import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.List;

import org.junit.Test;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;
import org.sweble.wikitext.parser.nodes.WikitextNodeFactoryImpl;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.utils.AstTextUtilsImpl;

public class ParserFunctionBaseTest
{
	private static class TestPfn
			extends
				ParserFunctionBase
	{
		private static final long serialVersionUID = 1L;

		TestPfn(String id)
		{
			super(id);
		}

		@Override
		public WtNode invoke(
				WtNode template,
				ExpansionFrame preprocessorFrame,
				List<? extends WtNode> argsValues)
		{
			return null;
		}
	}

	private static class OtherTestPfn
			extends
				TestPfn
	{
		private static final long serialVersionUID = 1L;

		OtherTestPfn(String id)
		{
			super(id);
		}
	}

	private static void assertConsistent(ParserFunctionBase a, ParserFunctionBase b)
	{
		assertEquals(a + " / " + b, a.equals(b), a.compareTo(b) == 0);
		assertEquals(b + " / " + a, b.equals(a), b.compareTo(a) == 0);
		assertEquals(Integer.signum(a.compareTo(b)), -Integer.signum(b.compareTo(a)));
		if (a.equals(b))
			assertEquals(a.hashCode(), b.hashCode());
	}

	/** equals(), hashCode() and compareTo() use the id and the class (issue #133). */
	@Test
	public void testEqualsHashCodeAndCompareToAgree()
	{
		TestPfn a = new TestPfn("a");

		assertEquals(a, new TestPfn("a"));
		assertNotEquals(a, new TestPfn("b"));
		assertNotEquals(a, new OtherTestPfn("a"));

		assertConsistent(a, new TestPfn("a"));
		assertConsistent(a, new TestPfn("b"));
		assertConsistent(a, new OtherTestPfn("a"));
		assertConsistent(a, new OtherTestPfn("b"));
	}

	/**
	 * Loading a configuration binds the parser functions to the node factory
	 * and text utilities of the loaded parser configuration (issue #133).
	 */
	@Test
	public void testLoadedConfigRebindsParserFunctions() throws Exception
	{
		StringWriter writer = new StringWriter();
		DefaultConfigEnWp.generate().save(writer);
		WikiConfigImpl config = WikiConfigImpl.load(new StringReader(writer.toString()));

		Field textUtilsConfig = AstTextUtilsImpl.class.getDeclaredField("parserConfig");
		textUtilsConfig.setAccessible(true);
		Field nodeFactoryConfig = WikitextNodeFactoryImpl.class.getDeclaredField("parserConfig");
		nodeFactoryConfig.setAccessible(true);

		assertSame(config.getParserConfig(), textUtilsConfig.get(config.getAstTextUtils()));
		assertSame(config.getParserConfig(), nodeFactoryConfig.get(config.getNodeFactory()));

		assertFalse(config.getParserFunctions().isEmpty());
		for (ParserFunctionBase pfn : config.getParserFunctions())
		{
			assertSame(pfn.getId(), config, pfn.getWikiConfig());
			assertSame(pfn.getId(), config.getNodeFactory(), pfn.nf());
			assertSame(pfn.getId(), config.getAstTextUtils(), pfn.tu());
		}
	}
}
