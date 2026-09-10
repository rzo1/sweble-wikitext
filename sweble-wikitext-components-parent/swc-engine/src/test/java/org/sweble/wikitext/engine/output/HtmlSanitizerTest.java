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
package org.sweble.wikitext.engine.output;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

public class HtmlSanitizerTest
{
	private static final String INSECURE = HtmlSanitizer.INSECURE_CSS_REPLACEMENT;

	@Test
	public void testElements()
	{
		for (String e : new String[] { "span", "SPAN", "div", "table", "td", "br", "sup", "abbr", "tbody", "thead", "tfoot", "colgroup", "col" })
			assertTrue(e, HtmlSanitizer.isAllowedElement(e));

		for (String e : new String[] { "script", "iframe", "object", "embed", "style", "a", "img", "form", "input", "svg", "math", "gallery", "references", "html", "body" })
			assertFalse(e, HtmlSanitizer.isAllowedElement(e));
	}

	@Test
	public void testAttributes()
	{
		assertTrue(HtmlSanitizer.isAllowedAttribute("span", "class"));
		assertTrue(HtmlSanitizer.isAllowedAttribute("span", "STYLE"));
		assertTrue(HtmlSanitizer.isAllowedAttribute("td", "colspan"));
		assertTrue(HtmlSanitizer.isAllowedAttribute("a", "href"));
		assertTrue(HtmlSanitizer.isAllowedAttribute("div", "data-foo"));

		assertFalse(HtmlSanitizer.isAllowedAttribute("span", "onclick"));
		assertFalse(HtmlSanitizer.isAllowedAttribute("span", "OnMouseOver"));
		assertFalse(HtmlSanitizer.isAllowedAttribute("span", "href"));
		assertFalse(HtmlSanitizer.isAllowedAttribute("div", "colspan"));
		assertFalse(HtmlSanitizer.isAllowedAttribute("div", "data-mw"));
		assertFalse(HtmlSanitizer.isAllowedAttribute("div", "data-mw-foo"));
		assertFalse(HtmlSanitizer.isAllowedAttribute("div", "data-ooui"));
		assertFalse(HtmlSanitizer.isAllowedAttribute("div", "data-a:b"));
		assertFalse(HtmlSanitizer.isAllowedAttribute("div", "data-a\"b"));
		assertFalse(HtmlSanitizer.isAllowedAttribute("script", "class"));
	}

	@Test
	public void testUnsafeUrls()
	{
		for (String url : new String[] {
				"javascript:alert(1)",
				"JaVaScRiPt:alert(1)",
				" javascript:alert(1)",
				"java\tscript:alert(1)",
				"java\nscript:alert(1)",
				"&#106;avascript:alert(1)",
				"&#x6A;avascript:alert(1)",
				"javascript&colon;alert(1)",
				"vbscript:msgbox(1)",
				"data:text/html,<script>alert(1)</script>" })
		{
			assertFalse(url, HtmlSanitizer.isSafeUrl(url));
			assertNull(url, HtmlSanitizer.sanitizeAttributeValue("href", url));
		}
	}

	@Test
	public void testSafeUrls()
	{
		for (String url : new String[] {
				"http://example.com/",
				"https://example.com/a?b=1&c=2",
				"mailto:someone@example.com",
				"//example.com/x",
				"/wiki/Main_Page",
				"Main_Page#javascript:x" })
		{
			assertTrue(url, HtmlSanitizer.isSafeUrl(url));
			assertEquals(url, HtmlSanitizer.sanitizeAttributeValue("cite", url));
		}
	}

	@Test
	public void testUnsafeCss()
	{
		for (String css : new String[] {
				"width: expression(alert(1))",
				"width: EXPRESSION(alert(1))",
				"background: url(javascript:alert(1))",
				"background: url (x)",
				"-moz-binding: url(x.xml#x)",
				"behavior: url(x.htc)",
				"behavior:x",
				"background-image: image(x.png)",
				"background-image: image-set(x.png 1x)",
				"color: javascript:alert(1)",
				"width: \\65xpression(alert(1))",
				"width: \\000065 xpression(alert(1))",
				"width: &#101;xpression(alert(1))",
				"background: u\\rl(x)" })
		{
			assertEquals(css, INSECURE, HtmlSanitizer.checkCss(css));
		}
	}

	@Test
	public void testCssComments()
	{
		assertEquals("width: ex pression(1)", HtmlSanitizer.checkCss("width: ex/**/pression(1)"));
		assertEquals("color:red ", HtmlSanitizer.checkCss("color:red /* unterminated"));
		assertEquals("/* a comment */", HtmlSanitizer.checkCss("/* a comment */"));
	}

	@Test
	public void testInvalidCss()
	{
		assertEquals(HtmlSanitizer.INVALID_CSS_REPLACEMENT, HtmlSanitizer.checkCss("color: red\u0001"));
		assertEquals(HtmlSanitizer.INVALID_CSS_REPLACEMENT, HtmlSanitizer.checkCss("color: red&#0;"));
	}

	@Test
	public void testSafeCssIsUnchanged()
	{
		for (String css : new String[] {
				"color:red",
				"color: red; background-color: #fff; width: 100%",
				"text-align: center; ",
				"font-family: \"Times New Roman\", serif",
				"scroll-behavior: smooth" })
		{
			assertEquals(css, css, HtmlSanitizer.checkCss(css));
		}
	}

	@Test
	public void testSanitizeAttributes()
	{
		Map<String, String> in = new LinkedHashMap<String, String>();
		in.put("CLASS", "a");
		in.put("onclick", "alert(1)");
		in.put("style", "width:expression(1)");
		in.put("itemtype", "http://schema.org/Thing");
		in.put("tabindex", "5");
		in.put("class", "b");
		in.put("data-x", "1");

		Map<String, String> out = HtmlSanitizer.sanitizeAttributes("span", in);

		assertEquals(Arrays.asList("class", "style", "data-x"), new ArrayList<String>(out.keySet()));
		assertEquals("b", out.get("class"));
		assertEquals(INSECURE, out.get("style"));
		assertEquals("1", out.get("data-x"));
	}

	@Test
	public void testMicrodataTags()
	{
		Map<String, String> attrs = new LinkedHashMap<String, String>();
		attrs.put("itemprop", "name");
		assertFalse(HtmlSanitizer.isValidTag("meta", attrs));
		assertFalse(HtmlSanitizer.isValidTag("link", attrs));
		attrs.put("content", "x");
		assertTrue(HtmlSanitizer.isValidTag("meta", attrs));
		assertTrue(HtmlSanitizer.isValidTag("span", new LinkedHashMap<String, String>()));
	}

	@Test
	public void testEscapeAttributeKeepingCharRefs()
	{
		assertEquals(
				"a&amp;b&amp;c&#34;&quot;&lt;&gt;&#39;",
				HtmlSanitizer.escapeAttributeKeepingCharRefs("a&amp;b&c&#34;\"<>'"));
		assertEquals("", HtmlSanitizer.escapeAttributeKeepingCharRefs(null));
	}

	@Test
	public void testEscapeTextKeepingCharRefs()
	{
		assertEquals(
				"a&amp;b&amp;c&#34;&#x3C;\"&lt;&gt;'",
				HtmlSanitizer.escapeTextKeepingCharRefs("a&amp;b&c&#34;&#x3C;\"<>'"));
		assertEquals("", HtmlSanitizer.escapeTextKeepingCharRefs(null));
	}
}
