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

package org.sweble.wikitext.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.sweble.wikitext.parser.encval.ValidatedWikitext;
import org.sweble.wikitext.parser.nodes.WtExternalLink;
import org.sweble.wikitext.parser.nodes.WtIgnored;
import org.sweble.wikitext.parser.nodes.WtImageLink;
import org.sweble.wikitext.parser.nodes.WtInternalLink;
import org.sweble.wikitext.parser.nodes.WtItalics;
import org.sweble.wikitext.parser.nodes.WtLctVarConv;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtPreproWikitextPage;
import org.sweble.wikitext.parser.nodes.WtTagExtension;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.nodes.WtTemplateParameter;
import org.sweble.wikitext.parser.nodes.WtText;
import org.sweble.wikitext.parser.nodes.WtXmlComment;
import org.sweble.wikitext.parser.nodes.WtXmlElement;
import org.sweble.wikitext.parser.nodes.WtXmlStartTag;
import org.sweble.wikitext.parser.parser.PreprocessorToParserTransformer;
import org.sweble.wikitext.parser.parser.RatsWikitextParser;
import org.sweble.wikitext.parser.preprocessor.PreprocessedWikitext;
import org.sweble.wikitext.parser.preprocessor.RatsWikitextPreprocessor;
import org.sweble.wikitext.parser.utils.NonExpandingParser;
import org.sweble.wikitext.parser.utils.SimpleParserConfig;
import org.sweble.wikitext.parser.utils.WtAstPrinter;

/**
 * Issue #143: The parser must neither overflow the stack on deeply nested
 * input, nor take super-quadratic time on unclosed openers, nor swallow the
 * rest of the page after an unclosed extension tag, and it must be usable
 * from several threads.
 */
public class ParserRobustnessTest
{
	/** A normal thread stack as used by most JVMs on 64 bit platforms. */
	private static final long STACK_SIZE = 1024L * 1024L;

	/**
	 * The time bound for inputs that took between 10 and several hundred
	 * seconds before the fix and take well below a second afterwards.
	 */
	private static final long TIMEOUT = 20000;

	// =========================================================================
	// Nesting depth

	@Test
	public void testNestedLangConvTagsDoNotOverflowTheStack() throws Throwable
	{
		parseOnNormalStack(StringUtils.repeat("-{", 1000));
		parseOnNormalStack(StringUtils.repeat("-{", 1000) + "x" + StringUtils.repeat("}-", 1000));
	}

	@Test
	public void testNestedInternalLinksDoNotOverflowTheStack() throws Throwable
	{
		parseOnNormalStack(StringUtils.repeat("[[a|", 1000) + "x" + StringUtils.repeat("]]", 1000));
	}

	@Test
	public void testNestedImageLinksDoNotOverflowTheStack() throws Throwable
	{
		parseOnNormalStack(StringUtils.repeat("[[File:a.png|", 500) + "x" + StringUtils.repeat("]]", 500));
	}

	@Test
	public void testNestedTemplatesDoNotOverflowTheStack() throws Throwable
	{
		parseOnNormalStack(StringUtils.repeat("{{x|", 1000) + "y" + StringUtils.repeat("}}", 1000));
	}

	@Test
	public void testNestedTemplateParametersDoNotOverflowTheStack() throws Throwable
	{
		parseOnNormalStack(StringUtils.repeat("{{{x|", 1000) + "y" + StringUtils.repeat("}}}", 1000));
	}

	@Test
	public void testManyLinkOpenersDoNotOverflowTheStack() throws Throwable
	{
		parseOnNormalStack(StringUtils.repeat("[[", 3000));
	}

	@Test
	public void testRealisticNestingIsStillRecognized() throws Throwable
	{
		int depth = 40;

		WtNode templates = parseOnNormalStack(
				StringUtils.repeat("{{x|", depth) + "y" + StringUtils.repeat("}}", depth));
		assertEquals(depth, count(templates, WtTemplate.class));

		WtNode params = parseOnNormalStack(
				StringUtils.repeat("{{{x|", depth) + "y" + StringUtils.repeat("}}}", depth));
		assertEquals(depth, count(params, WtTemplateParameter.class));

		WtNode images = parseOnNormalStack(
				StringUtils.repeat("[[File:a.png|", depth) + "x" + StringUtils.repeat("]]", depth));
		assertEquals(depth, count(images, WtImageLink.class));

		WtNode lcts = parseOnNormalStack(
				StringUtils.repeat("-{", depth) + "x" + StringUtils.repeat("}-", depth));
		assertEquals(depth, count(lcts, WtLctVarConv.class));
	}

	@Test
	public void testNestingDepthIsConfigurable() throws Exception
	{
		ParserConfig config = new SimpleParserConfig()
		{
			@Override
			public int getMaxNestingDepth()
			{
				return 5;
			}
		};
		NonExpandingParser parser = new NonExpandingParser(config);

		WtNode templates = parser.parseArticle(
				StringUtils.repeat("{{x|", 10) + "y" + StringUtils.repeat("}}", 10), "Test");
		assertEquals(5, count(templates, WtTemplate.class));

		WtNode images = parser.parseArticle(
				StringUtils.repeat("[[File:a.png|", 10) + "x" + StringUtils.repeat("]]", 10), "Test");
		assertEquals(5, count(images, WtImageLink.class));

		WtNode lcts = parser.parseArticle(
				StringUtils.repeat("-{", 10) + "x" + StringUtils.repeat("}-", 10), "Test");
		assertEquals(5, count(lcts, WtLctVarConv.class));
	}

	// =========================================================================
	// Parse time

	@Test(timeout = TIMEOUT)
	public void testUnclosedExternalLinksWithTitleTakeLinearTime() throws Exception
	{
		WtNode ast = parse(StringUtils.repeat("[http://a b ", 8000));
		assertEquals(0, count(ast, WtExternalLink.class));
	}

	@Test(timeout = TIMEOUT)
	public void testUnclosedExternalLinksWithoutTitleTakeLinearTime() throws Exception
	{
		WtNode ast = parse(StringUtils.repeat("[http://a ", 8000));
		assertEquals(0, count(ast, WtExternalLink.class));
	}

	@Test(timeout = TIMEOUT)
	public void testUnclosedInternalLinksTakeLinearTime() throws Exception
	{
		WtNode ast = parse(StringUtils.repeat("[[a ", 8000));
		assertEquals(0, count(ast, WtInternalLink.class));
	}

	@Test(timeout = TIMEOUT)
	public void testUnclosedTemplatesTakeLinearTime() throws Exception
	{
		WtNode prepro = preprocess(StringUtils.repeat("{{a ", 8000), false);
		assertEquals(0, count(prepro, WtTemplate.class));
	}

	@Test(timeout = TIMEOUT)
	public void testUnclosedExtensionTagsDoNotRescanTheInput() throws Exception
	{
		WtNode prepro = preprocess(StringUtils.repeat("<nowiki>a", 40000), false);
		assertEquals(0, count(prepro, WtTagExtension.class));
	}

	/**
	 * Issue #167: Every text node used to be merged into the previous one by
	 * copying its content, which took quadratic time for text interrupted by
	 * many angle brackets.
	 */
	@Test(timeout = TIMEOUT)
	public void testManyLeftAngleBracketsTakeLinearTime() throws Exception
	{
		String wikitext = StringUtils.repeat("<b>abcdef", 40000);

		WtNode prepro = preprocess(wikitext, false);
		assertEquals(wikitext, textOf(prepro));

		WtNode ast = parse(wikitext);
		assertTrue(count(ast, WtXmlElement.class) > 0);
	}

	@Test
	public void testExternalLinkTitleSpanningLinesViaInternalLinkIsUnchanged() throws Exception
	{
		WtNode ast = parse("[http://example.com a [[b|c\nd]] e]");
		assertEquals(1, count(ast, WtExternalLink.class));
	}

	// =========================================================================
	// Unclosed extension tags

	@Test
	public void testUnclosedExtensionTagBecomesText() throws Exception
	{
		WtNode prepro = preprocess("<nowiki>abc ''b'' [[Foo]]", false);
		assertEquals(0, count(prepro, WtTagExtension.class));
		assertTrue(textOf(prepro).startsWith("<nowiki>abc "));

		// The rest of the page is parsed as usual
		WtNode ast = parse("<nowiki>abc ''b'' [[Foo]]");
		assertEquals(0, count(ast, WtTagExtension.class));
		assertEquals(1, count(ast, WtItalics.class));
		assertEquals(1, count(ast, WtInternalLink.class));
	}

	/**
	 * Issue #167: The parser and the post-processor must not turn the opening
	 * tag, which the preprocessor left as text, into an HTML element that
	 * wraps the rest of the page.
	 */
	@Test
	public void testUnclosedExtensionTagStaysTextAfterParsing() throws Exception
	{
		for (String tag : new String[] { "<nowiki>", "<NoWiki>", "<ref name=a>", "<ref name=a >", "</ref>" })
		{
			String wikitext = "a " + tag + "b ''c''\n\nd [[Foo]]";

			WtNode ast = parse(wikitext);
			assertEquals(tag, 0, count(ast, WtXmlElement.class));
			assertEquals(tag, 0, count(ast, WtXmlStartTag.class));
			assertEquals(tag, 0, count(ast, WtTagExtension.class));
			assertEquals(tag, 1, count(ast, WtItalics.class));
			assertEquals(tag, 1, count(ast, WtInternalLink.class));
			assertTrue(tag, textOf(ast).startsWith("a " + tag + "b "));
		}
	}

	@Test
	public void testClosedExtensionTagAfterUnclosedOneIsRecognized() throws Exception
	{
		WtNode ast = parse("<nowiki>a</nowiki> <nowiki>b");
		assertEquals(1, count(ast, WtTagExtension.class));
		assertEquals(0, count(ast, WtXmlElement.class));
		assertTrue(textOf(ast).endsWith(" <nowiki>b"));
	}

	@Test
	public void testUnclosedPreStaysAnElementLikeInMediaWiki() throws Exception
	{
		// pre is an HTML element as well, MediaWiki turns it into one
		WtNode ast = parse("a <pre class=\"x\">b");
		assertEquals(0, count(ast, WtTagExtension.class));
		assertEquals(1, count(ast, WtXmlElement.class));
	}

	@Test
	public void testUnclosedExtensionTagWithAttributesBecomesText() throws Exception
	{
		WtNode prepro = preprocess("<ref name=\"{{x}}\">abc {{y}}", false);
		assertEquals(0, count(prepro, WtTagExtension.class));
		// Like MediaWiki the whole opening tag becomes text, the template in
		// the attribute is not recognized, the one after it is.
		assertEquals(1, count(prepro, WtTemplate.class));
		assertTrue(textOf(prepro).startsWith("<ref name=\"{{x}}\">abc "));
	}

	@Test
	public void testOnlyTheUnclosedExtensionTagBecomesText() throws Exception
	{
		WtNode prepro = preprocess("<nowiki>a</NOWIKI> <nowiki>b", false);
		assertEquals(1, count(prepro, WtTagExtension.class));
		assertTrue(textOf(prepro).endsWith(" <nowiki>b"));
	}

	@Test
	public void testClosingTagInFrontOfOpeningTagDoesNotClose() throws Exception
	{
		WtNode prepro = preprocess("</nowiki>a<nowiki>b", false);
		assertEquals(0, count(prepro, WtTagExtension.class));
	}

	@Test
	public void testUnclosedIncludeOnlyRunsToTheEnd() throws Exception
	{
		WtNode prepro = preprocess("a<includeonly>b {{x}}", false);
		assertEquals(1, count(prepro, WtIgnored.class));
		assertEquals(0, count(prepro, WtTemplate.class));
	}

	@Test
	public void testUnclosedNoIncludeRunsToTheEndForInclusion() throws Exception
	{
		WtNode prepro = preprocess("a<noinclude>b {{x}}", true);
		assertEquals(1, count(prepro, WtIgnored.class));
		assertEquals(0, count(prepro, WtTemplate.class));
	}

	@Test
	public void testUnclosedCommentStillRunsToTheEnd() throws Exception
	{
		WtNode prepro = preprocess("a<!-- b {{x}}", false);
		assertEquals(1, count(prepro, WtXmlComment.class));
		assertEquals(0, count(prepro, WtTemplate.class));
	}

	// =========================================================================
	// Thread safety

	@Test
	public void testParserClassesHaveNoMutableStaticState() throws Exception
	{
		assertNoMutableStaticFields(RatsWikitextParser.class);
		assertNoMutableStaticFields(RatsWikitextPreprocessor.class);
		assertNoMutableStaticFields(WikitextParser.class);
		assertNoMutableStaticFields(WikitextPreprocessor.class);
	}

	@Test
	public void testParserInstancesCanBeSharedBetweenThreads() throws Exception
	{
		final ParserConfig config = new SimpleParserConfig();
		final WikitextPreprocessor preprocessor = new WikitextPreprocessor(config);
		final WikitextParser parser = new WikitextParser(config);

		final String[] inputs = {
				"== Heading ==\n* item [[Link|title]] {{tmpl|a=b}}\n",
				"{| class=\"wikitable\"\n| cell || ''cell''\n|}\n",
				"Some text with <ref>a ref</ref> and [http://example.com ext].\n",
				StringUtils.repeat("'''bold''' [[a|b]] -{zh:x}- ", 200),
		};

		final String[] expected = new String[inputs.length];
		for (int i = 0; i < inputs.length; ++i)
			expected[i] = WtAstPrinter.print(parseWith(preprocessor, parser, inputs[i]));

		ExecutorService executor = Executors.newFixedThreadPool(8);
		try
		{
			List<Future<Void>> futures = new ArrayList<Future<Void>>();
			for (int t = 0; t < 8; ++t)
			{
				final int offset = t;
				futures.add(executor.submit(new Callable<Void>()
				{
					@Override
					public Void call() throws Exception
					{
						for (int j = 0; j < 40; ++j)
						{
							int i = (offset + j) % inputs.length;
							WtNode ast = parseWith(preprocessor, parser, inputs[i]);
							assertEquals(expected[i], WtAstPrinter.print(ast));
						}
						return null;
					}
				}));
			}
			for (Future<Void> f : futures)
				f.get();
		}
		finally
		{
			executor.shutdownNow();
		}
	}

	// =========================================================================

	private static WtNode parse(String wikitext) throws Exception
	{
		NonExpandingParser parser = new NonExpandingParser(
				true /*warningsEnabled*/,
				true /*gatherRtd*/,
				false /*autoCorrect*/);
		return parser.parseArticle(wikitext, "Test");
	}

	private static WtNode parseOnNormalStack(final String wikitext) throws Throwable
	{
		final AtomicReference<WtNode> result = new AtomicReference<WtNode>();
		final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
		Thread thread = new Thread(null, new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					result.set(parse(wikitext));
				}
				catch (Throwable t)
				{
					error.set(t);
				}
			}
		}, "parser-robustness", STACK_SIZE);
		thread.start();
		thread.join();
		if (error.get() != null)
			throw error.get();
		return result.get();
	}

	private static WtNode preprocess(String wikitext, boolean forInclusion) throws Exception
	{
		WikitextPreprocessor preprocessor = new WikitextPreprocessor(new SimpleParserConfig());
		return preprocessor.parseArticle(
				new ValidatedWikitext(wikitext, new WtEntityMapImpl(), false),
				"Test",
				forInclusion);
	}

	private static WtNode parseWith(
			WikitextPreprocessor preprocessor,
			WikitextParser parser,
			String wikitext) throws Exception
	{
		WtPreproWikitextPage prepro = (WtPreproWikitextPage) preprocessor.parseArticle(
				new ValidatedWikitext(wikitext, new WtEntityMapImpl(), false),
				"Test",
				false);
		PreprocessedWikitext ppw = PreprocessorToParserTransformer.transform(prepro);
		return parser.parseArticle(ppw, "Test");
	}

	private static int count(WtNode node, Class<? extends WtNode> clazz)
	{
		int n = clazz.isInstance(node) ? 1 : 0;
		for (WtNode child : node)
			n += count(child, clazz);
		return n;
	}

	private static String textOf(WtNode node)
	{
		StringBuilder sb = new StringBuilder();
		textOf(node, sb);
		return sb.toString();
	}

	private static void textOf(WtNode node, StringBuilder sb)
	{
		if (node instanceof WtText)
		{
			sb.append(((WtText) node).getContent());
		}
		else
		{
			for (WtNode child : node)
				textOf(child, sb);
		}
	}

	private static void assertNoMutableStaticFields(Class<?> clazz)
	{
		for (Field f : clazz.getDeclaredFields())
		{
			int m = f.getModifiers();
			if (Modifier.isStatic(m) && !f.isSynthetic())
				assertTrue(clazz.getSimpleName() + "." + f.getName() + " is static but not final", Modifier.isFinal(m));
		}
		for (Field f : clazz.getDeclaredFields())
		{
			assertFalse(
					clazz.getSimpleName() + "." + f.getName() + " holds a Rats! parser",
					!Modifier.isStatic(f.getModifiers())
							&& (f.getType() == RatsWikitextParser.class
									|| f.getType() == RatsWikitextPreprocessor.class));
		}
	}
}
