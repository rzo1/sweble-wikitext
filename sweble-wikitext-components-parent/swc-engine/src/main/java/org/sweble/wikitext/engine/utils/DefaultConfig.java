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
package org.sweble.wikitext.engine.utils;

import org.sweble.wikitext.engine.ParserFunctionBase;
import org.sweble.wikitext.engine.config.EngineConfigImpl;
import org.sweble.wikitext.engine.config.I18nAliasImpl;
import org.sweble.wikitext.engine.config.NamespaceImpl;
import org.sweble.wikitext.engine.config.ParserConfigImpl;
import org.sweble.wikitext.engine.config.ParserFunctionGroup;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.ext.builtin.BuiltInParserFunctions;
import org.sweble.wikitext.engine.ext.builtin.BuiltInTagExtensions;
import org.sweble.wikitext.engine.ext.convert.ConvertPnfExt;
import org.sweble.wikitext.engine.ext.core.CorePfnBehaviorSwitches;
import org.sweble.wikitext.engine.ext.core.CorePfnFunctionsFormatting;
import org.sweble.wikitext.engine.ext.core.CorePfnFunctionsLocalization;
import org.sweble.wikitext.engine.ext.core.CorePfnFunctionsMiscellaneous;
import org.sweble.wikitext.engine.ext.core.CorePfnFunctionsNamespaces;
import org.sweble.wikitext.engine.ext.core.CorePfnFunctionsUrlData;
import org.sweble.wikitext.engine.ext.core.CorePfnVariablesDateAndTime;
import org.sweble.wikitext.engine.ext.core.CorePfnVariablesNamespaces;
import org.sweble.wikitext.engine.ext.core.CorePfnVariablesPageNames;
import org.sweble.wikitext.engine.ext.core.CorePfnVariablesStatistics;
import org.sweble.wikitext.engine.ext.core.CorePfnVariablesTechnicalMetadata;
import org.sweble.wikitext.engine.ext.math.MathTagExt;
import org.sweble.wikitext.engine.ext.parser_functions.ParserFunctionsPfnExt;
import org.sweble.wikitext.engine.ext.ref.RefTagExt;
import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Programmatically generate a default configuration for a Wiki.
 *
 * The configuration uses MediaWiki's defaults for an English wiki: the
 * canonical namespaces, the canonical English magic words and the English link
 * trail.
 */
public class DefaultConfig
{
	public static WikiConfigImpl generate()
	{
		WikiConfigImpl c = new WikiConfigImpl();
		new DefaultConfig().configureWiki(c);
		return c;
	}

	protected void configureWiki(WikiConfigImpl c)
	{
		configureEngine(c);

		// --[ Properties of the wiki instance ]--

		configureSiteProperties(c);

		// --[ Namespaces, Known Wikis, Internationalization ]--

		addNamespaces(c);

		addInterwikis(c);

		addI18nAliases(c);

		// --[ Parser functions ]--

		addParserFunctions(c);

		// --[ Tag extensions ]--

		addTagExtensions(c);
	}

	protected EngineConfigImpl configureEngine(WikiConfigImpl c)
	{
		configureParser(c);

		EngineConfigImpl cc = c.getEngineConfig();

		cc.setTrimTransparentBeforeParsing(true);

		return cc;
	}

	protected ParserConfigImpl configureParser(WikiConfigImpl c)
	{
		ParserConfigImpl pc = c.getParserConfig();

		// ==[ Parser features ]================================================

		pc.setAutoCorrect(false);
		pc.setGatherRtData(true);
		pc.setMinSeverity(WarningSeverity.INFORMATIVE);
		pc.setWarningsEnabled(true);

		// --[ Link classification and parsing ]--

		pc.addUrlProtocol("http://");
		pc.addUrlProtocol("https://");
		pc.addUrlProtocol("mailto:");

		// English has no link prefix and the link trail from MessagesEn.php
		pc.setInternalLinkPrefixPattern(null);
		pc.setInternalLinkPostfixPattern("[a-z]+");

		// ==[ Parsing XML elements ]===========================================

		addXmlEntities(pc);

		// ==[ Language Conversion Tags ]=======================================

		addLctMappings(pc);

		return pc;
	}

	// =========================================================================

	protected void addXmlEntities(ParserConfigImpl pc)
	{
		// From: http://www.w3.org/TR/html4/sgml/entities.html

		// 24 Character entity references in HTML 4

		// 24.2 Character entity references for ISO 8859-1 characters
		pc.addXmlEntity("nbsp", "\u00a0");
		pc.addXmlEntity("iexcl", "\u00a1");
		pc.addXmlEntity("cent", "\u00a2");
		pc.addXmlEntity("pound", "\u00a3");
		pc.addXmlEntity("curren", "\u00a4");
		pc.addXmlEntity("yen", "\u00a5");
		pc.addXmlEntity("brvbar", "\u00a6");
		pc.addXmlEntity("sect", "\u00a7");
		pc.addXmlEntity("uml", "\u00a8");
		pc.addXmlEntity("copy", "\u00a9");
		pc.addXmlEntity("ordf", "\u00aa");
		pc.addXmlEntity("laquo", "\u00ab");
		pc.addXmlEntity("not", "\u00ac");
		pc.addXmlEntity("shy", "\u00ad");
		pc.addXmlEntity("reg", "\u00ae");
		pc.addXmlEntity("macr", "\u00af");
		pc.addXmlEntity("deg", "\u00b0");
		pc.addXmlEntity("plusmn", "\u00b1");
		pc.addXmlEntity("sup2", "\u00b2");
		pc.addXmlEntity("sup3", "\u00b3");
		pc.addXmlEntity("acute", "\u00b4");
		pc.addXmlEntity("micro", "\u00b5");
		pc.addXmlEntity("para", "\u00b6");
		pc.addXmlEntity("middot", "\u00b7");
		pc.addXmlEntity("cedil", "\u00b8");
		pc.addXmlEntity("sup1", "\u00b9");
		pc.addXmlEntity("ordm", "\u00ba");
		pc.addXmlEntity("raquo", "\u00bb");
		pc.addXmlEntity("frac14", "\u00bc");
		pc.addXmlEntity("frac12", "\u00bd");
		pc.addXmlEntity("frac34", "\u00be");
		pc.addXmlEntity("iquest", "\u00bf");
		pc.addXmlEntity("Agrave", "\u00c0");
		pc.addXmlEntity("Aacute", "\u00c1");
		pc.addXmlEntity("Acirc", "\u00c2");
		pc.addXmlEntity("Atilde", "\u00c3");
		pc.addXmlEntity("Auml", "\u00c4");
		pc.addXmlEntity("Aring", "\u00c5");
		pc.addXmlEntity("AElig", "\u00c6");
		pc.addXmlEntity("Ccedil", "\u00c7");
		pc.addXmlEntity("Egrave", "\u00c8");
		pc.addXmlEntity("Eacute", "\u00c9");
		pc.addXmlEntity("Ecirc", "\u00ca");
		pc.addXmlEntity("Euml", "\u00cb");
		pc.addXmlEntity("Igrave", "\u00cc");
		pc.addXmlEntity("Iacute", "\u00cd");
		pc.addXmlEntity("Icirc", "\u00ce");
		pc.addXmlEntity("Iuml", "\u00cf");
		pc.addXmlEntity("ETH", "\u00d0");
		pc.addXmlEntity("Ntilde", "\u00d1");
		pc.addXmlEntity("Ograve", "\u00d2");
		pc.addXmlEntity("Oacute", "\u00d3");
		pc.addXmlEntity("Ocirc", "\u00d4");
		pc.addXmlEntity("Otilde", "\u00d5");
		pc.addXmlEntity("Ouml", "\u00d6");
		pc.addXmlEntity("times", "\u00d7");
		pc.addXmlEntity("Oslash", "\u00d8");
		pc.addXmlEntity("Ugrave", "\u00d9");
		pc.addXmlEntity("Uacute", "\u00da");
		pc.addXmlEntity("Ucirc", "\u00db");
		pc.addXmlEntity("Uuml", "\u00dc");
		pc.addXmlEntity("Yacute", "\u00dd");
		pc.addXmlEntity("THORN", "\u00de");
		pc.addXmlEntity("szlig", "\u00df");
		pc.addXmlEntity("agrave", "\u00e0");
		pc.addXmlEntity("aacute", "\u00e1");
		pc.addXmlEntity("acirc", "\u00e2");
		pc.addXmlEntity("atilde", "\u00e3");
		pc.addXmlEntity("auml", "\u00e4");
		pc.addXmlEntity("aring", "\u00e5");
		pc.addXmlEntity("aelig", "\u00e6");
		pc.addXmlEntity("ccedil", "\u00e7");
		pc.addXmlEntity("egrave", "\u00e8");
		pc.addXmlEntity("eacute", "\u00e9");
		pc.addXmlEntity("ecirc", "\u00ea");
		pc.addXmlEntity("euml", "\u00eb");
		pc.addXmlEntity("igrave", "\u00ec");
		pc.addXmlEntity("iacute", "\u00ed");
		pc.addXmlEntity("icirc", "\u00ee");
		pc.addXmlEntity("iuml", "\u00ef");
		pc.addXmlEntity("eth", "\u00f0");
		pc.addXmlEntity("ntilde", "\u00f1");
		pc.addXmlEntity("ograve", "\u00f2");
		pc.addXmlEntity("oacute", "\u00f3");
		pc.addXmlEntity("ocirc", "\u00f4");
		pc.addXmlEntity("otilde", "\u00f5");
		pc.addXmlEntity("ouml", "\u00f6");
		pc.addXmlEntity("divide", "\u00f7");
		pc.addXmlEntity("oslash", "\u00f8");
		pc.addXmlEntity("ugrave", "\u00f9");
		pc.addXmlEntity("uacute", "\u00fa");
		pc.addXmlEntity("ucirc", "\u00fb");
		pc.addXmlEntity("uuml", "\u00fc");
		pc.addXmlEntity("yacute", "\u00fd");
		pc.addXmlEntity("thorn", "\u00fe");
		pc.addXmlEntity("yuml", "\u00ff");

		// 24.3 Character entity references for symbols, mathematical symbols, and Greek letters
		pc.addXmlEntity("fnof", "\u0192");
		pc.addXmlEntity("Alpha", "\u0391");
		pc.addXmlEntity("Beta", "\u0392");
		pc.addXmlEntity("Gamma", "\u0393");
		pc.addXmlEntity("Delta", "\u0394");
		pc.addXmlEntity("Epsilon", "\u0395");
		pc.addXmlEntity("Zeta", "\u0396");
		pc.addXmlEntity("Eta", "\u0397");
		pc.addXmlEntity("Theta", "\u0398");
		pc.addXmlEntity("Iota", "\u0399");
		pc.addXmlEntity("Kappa", "\u039a");
		pc.addXmlEntity("Lambda", "\u039b");
		pc.addXmlEntity("Mu", "\u039c");
		pc.addXmlEntity("Nu", "\u039d");
		pc.addXmlEntity("Xi", "\u039e");
		pc.addXmlEntity("Omicron", "\u039f");
		pc.addXmlEntity("Pi", "\u03a0");
		pc.addXmlEntity("Rho", "\u03a1");
		pc.addXmlEntity("Sigma", "\u03a3");
		pc.addXmlEntity("Tau", "\u03a4");
		pc.addXmlEntity("Upsilon", "\u03a5");
		pc.addXmlEntity("Phi", "\u03a6");
		pc.addXmlEntity("Chi", "\u03a7");
		pc.addXmlEntity("Psi", "\u03a8");
		pc.addXmlEntity("Omega", "\u03a9");
		pc.addXmlEntity("alpha", "\u03b1");
		pc.addXmlEntity("beta", "\u03b2");
		pc.addXmlEntity("gamma", "\u03b3");
		pc.addXmlEntity("delta", "\u03b4");
		pc.addXmlEntity("epsilon", "\u03b5");
		pc.addXmlEntity("zeta", "\u03b6");
		pc.addXmlEntity("eta", "\u03b7");
		pc.addXmlEntity("theta", "\u03b8");
		pc.addXmlEntity("iota", "\u03b9");
		pc.addXmlEntity("kappa", "\u03ba");
		pc.addXmlEntity("lambda", "\u03bb");
		pc.addXmlEntity("mu", "\u03bc");
		pc.addXmlEntity("nu", "\u03bd");
		pc.addXmlEntity("xi", "\u03be");
		pc.addXmlEntity("omicron", "\u03bf");
		pc.addXmlEntity("pi", "\u03c0");
		pc.addXmlEntity("rho", "\u03c1");
		pc.addXmlEntity("sigmaf", "\u03c2");
		pc.addXmlEntity("sigma", "\u03c3");
		pc.addXmlEntity("tau", "\u03c4");
		pc.addXmlEntity("upsilon", "\u03c5");
		pc.addXmlEntity("phi", "\u03c6");
		pc.addXmlEntity("chi", "\u03c7");
		pc.addXmlEntity("psi", "\u03c8");
		pc.addXmlEntity("omega", "\u03c9");
		pc.addXmlEntity("thetasym", "\u03d1");
		pc.addXmlEntity("upsih", "\u03d2");
		pc.addXmlEntity("piv", "\u03d6");
		pc.addXmlEntity("bull", "\u2022");
		pc.addXmlEntity("hellip", "\u2026");
		pc.addXmlEntity("prime", "\u2032");
		pc.addXmlEntity("Prime", "\u2033");
		pc.addXmlEntity("oline", "\u203e");
		pc.addXmlEntity("frasl", "\u2044");
		pc.addXmlEntity("weierp", "\u2118");
		pc.addXmlEntity("image", "\u2111");
		pc.addXmlEntity("real", "\u211c");
		pc.addXmlEntity("trade", "\u2122");
		pc.addXmlEntity("alefsym", "\u2135");
		pc.addXmlEntity("larr", "\u2190");
		pc.addXmlEntity("uarr", "\u2191");
		pc.addXmlEntity("rarr", "\u2192");
		pc.addXmlEntity("darr", "\u2193");
		pc.addXmlEntity("harr", "\u2194");
		pc.addXmlEntity("crarr", "\u21b5");
		pc.addXmlEntity("lArr", "\u21d0");
		pc.addXmlEntity("uArr", "\u21d1");
		pc.addXmlEntity("rArr", "\u21d2");
		pc.addXmlEntity("dArr", "\u21d3");
		pc.addXmlEntity("hArr", "\u21d4");
		pc.addXmlEntity("forall", "\u2200");
		pc.addXmlEntity("part", "\u2202");
		pc.addXmlEntity("exist", "\u2203");
		pc.addXmlEntity("empty", "\u2205");
		pc.addXmlEntity("nabla", "\u2207");
		pc.addXmlEntity("isin", "\u2208");
		pc.addXmlEntity("notin", "\u2209");
		pc.addXmlEntity("ni", "\u220b");
		pc.addXmlEntity("prod", "\u220f");
		pc.addXmlEntity("sum", "\u2211");
		pc.addXmlEntity("minus", "\u2212");
		pc.addXmlEntity("lowast", "\u2217");
		pc.addXmlEntity("radic", "\u221a");
		pc.addXmlEntity("prop", "\u221d");
		pc.addXmlEntity("infin", "\u221e");
		pc.addXmlEntity("ang", "\u2220");
		pc.addXmlEntity("and", "\u2227");
		pc.addXmlEntity("or", "\u2228");
		pc.addXmlEntity("cap", "\u2229");
		pc.addXmlEntity("cup", "\u222a");
		pc.addXmlEntity("int", "\u222b");
		pc.addXmlEntity("there4", "\u2234");
		pc.addXmlEntity("sim", "\u223c");
		pc.addXmlEntity("cong", "\u2245");
		pc.addXmlEntity("asymp", "\u2248");
		pc.addXmlEntity("ne", "\u2260");
		pc.addXmlEntity("equiv", "\u2261");
		pc.addXmlEntity("le", "\u2264");
		pc.addXmlEntity("ge", "\u2265");
		pc.addXmlEntity("sub", "\u2282");
		pc.addXmlEntity("sup", "\u2283");
		pc.addXmlEntity("nsub", "\u2284");
		pc.addXmlEntity("sube", "\u2286");
		pc.addXmlEntity("supe", "\u2287");
		pc.addXmlEntity("oplus", "\u2295");
		pc.addXmlEntity("otimes", "\u2297");
		pc.addXmlEntity("perp", "\u22a5");
		pc.addXmlEntity("sdot", "\u22c5");
		pc.addXmlEntity("lceil", "\u2308");
		pc.addXmlEntity("rceil", "\u2309");
		pc.addXmlEntity("lfloor", "\u230a");
		pc.addXmlEntity("rfloor", "\u230b");
		pc.addXmlEntity("lang", "\u2329");
		pc.addXmlEntity("rang", "\u232a");
		pc.addXmlEntity("loz", "\u25ca");
		pc.addXmlEntity("spades", "\u2660");
		pc.addXmlEntity("clubs", "\u2663");
		pc.addXmlEntity("hearts", "\u2665");
		pc.addXmlEntity("diams", "\u2666");

		// 24.4 Character entity references for markup-significant and internationalization characters
		pc.addXmlEntity("quot", "" + '\u0022'); // Eclipse gets really confused!
		pc.addXmlEntity("amp", "\u0026");
		pc.addXmlEntity("lt", "\u003c");
		pc.addXmlEntity("gt", "\u003e");
		pc.addXmlEntity("OElig", "\u0152");
		pc.addXmlEntity("oelig", "\u0153");
		pc.addXmlEntity("Scaron", "\u0160");
		pc.addXmlEntity("scaron", "\u0161");
		pc.addXmlEntity("Yuml", "\u0178");
		pc.addXmlEntity("circ", "\u02c6");
		pc.addXmlEntity("tilde", "\u02dc");
		pc.addXmlEntity("ensp", "\u2002");
		pc.addXmlEntity("emsp", "\u2003");
		pc.addXmlEntity("thinsp", "\u2009");
		pc.addXmlEntity("zwnj", "\u200c");
		pc.addXmlEntity("zwj", "\u200d");
		pc.addXmlEntity("lrm", "\u200e");
		pc.addXmlEntity("rlm", "\u200f");
		pc.addXmlEntity("ndash", "\u2013");
		pc.addXmlEntity("mdash", "\u2014");
		pc.addXmlEntity("lsquo", "\u2018");
		pc.addXmlEntity("rsquo", "\u2019");
		pc.addXmlEntity("sbquo", "\u201a");
		pc.addXmlEntity("ldquo", "\u201c");
		pc.addXmlEntity("rdquo", "\u201d");
		pc.addXmlEntity("bdquo", "\u201e");
		pc.addXmlEntity("dagger", "\u2020");
		pc.addXmlEntity("Dagger", "\u2021");
		pc.addXmlEntity("permil", "\u2030");
		pc.addXmlEntity("lsaquo", "\u2039");
		pc.addXmlEntity("rsaquo", "\u203a");
		pc.addXmlEntity("euro", "\u20ac");
	}

	protected void addLctMappings(ParserConfigImpl pc)
	{
	}

	protected void configureSiteProperties(WikiConfigImpl c)
	{
		c.setSiteName("My Wiki");

		c.setWikiUrl("http://localhost/");

		c.setContentLang("en");

		c.setIwPrefix("en");
	}

	protected void addNamespaces(WikiConfigImpl c)
	{
		c.addNamespace(new NamespaceImpl(
				-2,
				"Media",
				"Media",
				false,
				false,
				new ArrayList<String>()));

		c.addNamespace(new NamespaceImpl(
				-1,
				"Special",
				"Special",
				false,
				false,
				new ArrayList<String>()));

		c.addNamespace(new NamespaceImpl(
				0,
				"",
				"",
				false,
				false,
				new ArrayList<String>()));

		c.addNamespace(new NamespaceImpl(
				1,
				"Talk",
				"Talk",
				true,
				false,
				new ArrayList<String>()));

		c.addNamespace(new NamespaceImpl(
				2,
				"User",
				"User",
				true,
				false,
				new ArrayList<String>()));

		c.addNamespace(new NamespaceImpl(
				3,
				"User talk",
				"User talk",
				true,
				false,
				new ArrayList<String>()));

		c.addNamespace(new NamespaceImpl(
				4,
				"Project",
				"Project",
				true,
				false,
				new ArrayList<String>()));

		c.addNamespace(new NamespaceImpl(
				5,
				"Project talk",
				"Project talk",
				true,
				false,
				new ArrayList<String>()));

		c.addNamespace(new NamespaceImpl(
				6,
				"File",
				"File",
				false,
				true,
				Arrays.asList("Image")));

		c.addNamespace(new NamespaceImpl(
				7,
				"File talk",
				"File talk",
				true,
				false,
				Arrays.asList("Image talk")));

		c.addNamespace(new NamespaceImpl(
				8,
				"MediaWiki",
				"MediaWiki",
				true,
				false,
				new ArrayList<String>()));

		c.addNamespace(new NamespaceImpl(
				9,
				"MediaWiki talk",
				"MediaWiki talk",
				true,
				false,
				new ArrayList<String>()));

		c.addNamespace(new NamespaceImpl(
				10,
				"Template",
				"Template",
				true,
				false,
				new ArrayList<String>()));

		c.addNamespace(new NamespaceImpl(
				11,
				"Template talk",
				"Template talk",
				true,
				false,
				new ArrayList<String>()));

		c.addNamespace(new NamespaceImpl(
				12,
				"Help",
				"Help",
				true,
				false,
				new ArrayList<String>()));

		c.addNamespace(new NamespaceImpl(
				13,
				"Help talk",
				"Help talk",
				true,
				false,
				new ArrayList<String>()));

		c.addNamespace(new NamespaceImpl(
				14,
				"Category",
				"Category",
				false,
				false,
				new ArrayList<String>()));

		c.addNamespace(new NamespaceImpl(
				15,
				"Category talk",
				"Category talk",
				true,
				false,
				new ArrayList<String>()));

		c.setDefaultNamespace(c.getNamespace(0));
		c.setTemplateNamespace(c.getNamespace(10));
	}

	protected void addInterwikis(WikiConfigImpl c)
	{
	}

	/**
	 * Registers the i18n aliases of the parser functions, variables and
	 * behavior switches registered by {@link #addParserFunctions(WikiConfigImpl)}
	 * and of the redirect keyword.
	 *
	 * The core magic words are MediaWiki's canonical English magic words (see
	 * {@code $magicWords} in {@code languages/messages/MessagesEn.php}).
	 * Parser functions that take arguments include the trailing colon (e.g.
	 * {@code PAGENAME:}), those of the ParserFunctions extension also the
	 * leading hash (e.g. {@code #if:}).
	 */
	protected void addI18nAliases(WikiConfigImpl c)
	{
		// --[ ParserFunctions extension ]--

		c.addI18nAlias(new I18nAliasImpl(
				"expr",
				false,
				Arrays.asList("#expr:")));
		c.addI18nAlias(new I18nAliasImpl(
				"if",
				false,
				Arrays.asList("#if:")));
		c.addI18nAlias(new I18nAliasImpl(
				"ifeq",
				false,
				Arrays.asList("#ifeq:")));
		c.addI18nAlias(new I18nAliasImpl(
				"ifexpr",
				false,
				Arrays.asList("#ifexpr:")));
		c.addI18nAlias(new I18nAliasImpl(
				"iferror",
				false,
				Arrays.asList("#iferror:")));
		c.addI18nAlias(new I18nAliasImpl(
				"switch",
				false,
				Arrays.asList("#switch:")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"default",
				false,
				Arrays.asList("#default")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"ifexist",
				false,
				Arrays.asList("#ifexist:")));
		c.addI18nAlias(new I18nAliasImpl(
				"time",
				false,
				Arrays.asList("#time:")));
		c.addI18nAlias(new I18nAliasImpl(
				"timel",
				false,
				Arrays.asList("#timel:")));
		c.addI18nAlias(new I18nAliasImpl(
				"rel2abs",
				false,
				Arrays.asList("#rel2abs:")));
		c.addI18nAlias(new I18nAliasImpl(
				"titleparts",
				false,
				Arrays.asList("#titleparts:")));

		// --[ Convert extension ]--

		c.addI18nAlias(new I18nAliasImpl(
				"convert",
				false,
				Arrays.asList("convert")));

		// --[ Core magic words ]--

		c.addI18nAlias(new I18nAliasImpl(
				"redirect",
				false,
				Arrays.asList("#REDIRECT")));
		c.addI18nAlias(new I18nAliasImpl(
				"notoc",
				false,
				Arrays.asList("__NOTOC__")));
		c.addI18nAlias(new I18nAliasImpl(
				"nogallery",
				false,
				Arrays.asList("__NOGALLERY__")));
		c.addI18nAlias(new I18nAliasImpl(
				"forcetoc",
				false,
				Arrays.asList("__FORCETOC__")));
		c.addI18nAlias(new I18nAliasImpl(
				"toc",
				false,
				Arrays.asList("__TOC__")));
		c.addI18nAlias(new I18nAliasImpl(
				"noeditsection",
				false,
				Arrays.asList("__NOEDITSECTION__")));
		c.addI18nAlias(new I18nAliasImpl(
				"newsectionlink",
				true,
				Arrays.asList("__NEWSECTIONLINK__")));
		c.addI18nAlias(new I18nAliasImpl(
				"nonewsectionlink",
				true,
				Arrays.asList("__NONEWSECTIONLINK__")));
		c.addI18nAlias(new I18nAliasImpl(
				"notitleconvert",
				false,
				Arrays.asList("__NOTITLECONVERT__", "__NOTC__")));
		c.addI18nAlias(new I18nAliasImpl(
				"nocontentconvert",
				false,
				Arrays.asList("__NOCONTENTCONVERT__", "__NOCC__")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"noheader",
				false,
				Arrays.asList("__NOHEADER__")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"currentmonth",
				true,
				Arrays.asList("CURRENTMONTH", "CURRENTMONTH2")));
		c.addI18nAlias(new I18nAliasImpl(
				"currentmonth1",
				true,
				Arrays.asList("CURRENTMONTH1")));
		c.addI18nAlias(new I18nAliasImpl(
				"currentmonthname",
				true,
				Arrays.asList("CURRENTMONTHNAME")));
		c.addI18nAlias(new I18nAliasImpl(
				"currentmonthnamegen",
				true,
				Arrays.asList("CURRENTMONTHNAMEGEN")));
		c.addI18nAlias(new I18nAliasImpl(
				"currentmonthabbrev",
				true,
				Arrays.asList("CURRENTMONTHABBREV")));
		c.addI18nAlias(new I18nAliasImpl(
				"currentday",
				true,
				Arrays.asList("CURRENTDAY")));
		c.addI18nAlias(new I18nAliasImpl(
				"currentday2",
				true,
				Arrays.asList("CURRENTDAY2")));
		c.addI18nAlias(new I18nAliasImpl(
				"currentdayname",
				true,
				Arrays.asList("CURRENTDAYNAME")));
		c.addI18nAlias(new I18nAliasImpl(
				"currentyear",
				true,
				Arrays.asList("CURRENTYEAR")));
		c.addI18nAlias(new I18nAliasImpl(
				"currenttime",
				true,
				Arrays.asList("CURRENTTIME")));
		c.addI18nAlias(new I18nAliasImpl(
				"currenthour",
				true,
				Arrays.asList("CURRENTHOUR")));
		c.addI18nAlias(new I18nAliasImpl(
				"localmonth",
				true,
				Arrays.asList("LOCALMONTH", "LOCALMONTH2")));
		c.addI18nAlias(new I18nAliasImpl(
				"localmonth1",
				true,
				Arrays.asList("LOCALMONTH1")));
		c.addI18nAlias(new I18nAliasImpl(
				"localmonthname",
				true,
				Arrays.asList("LOCALMONTHNAME")));
		c.addI18nAlias(new I18nAliasImpl(
				"localmonthnamegen",
				true,
				Arrays.asList("LOCALMONTHNAMEGEN")));
		c.addI18nAlias(new I18nAliasImpl(
				"localmonthabbrev",
				true,
				Arrays.asList("LOCALMONTHABBREV")));
		c.addI18nAlias(new I18nAliasImpl(
				"localday",
				true,
				Arrays.asList("LOCALDAY")));
		c.addI18nAlias(new I18nAliasImpl(
				"localday2",
				true,
				Arrays.asList("LOCALDAY2")));
		c.addI18nAlias(new I18nAliasImpl(
				"localdayname",
				true,
				Arrays.asList("LOCALDAYNAME")));
		c.addI18nAlias(new I18nAliasImpl(
				"localyear",
				true,
				Arrays.asList("LOCALYEAR")));
		c.addI18nAlias(new I18nAliasImpl(
				"localtime",
				true,
				Arrays.asList("LOCALTIME")));
		c.addI18nAlias(new I18nAliasImpl(
				"localhour",
				true,
				Arrays.asList("LOCALHOUR")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"numberofpages",
				true,
				Arrays.asList("NUMBEROFPAGES")));
		c.addI18nAlias(new I18nAliasImpl(
				"numberofarticles",
				true,
				Arrays.asList("NUMBEROFARTICLES")));
		c.addI18nAlias(new I18nAliasImpl(
				"numberoffiles",
				true,
				Arrays.asList("NUMBEROFFILES")));
		c.addI18nAlias(new I18nAliasImpl(
				"numberofusers",
				true,
				Arrays.asList("NUMBEROFUSERS")));
		c.addI18nAlias(new I18nAliasImpl(
				"numberofactiveusers",
				true,
				Arrays.asList("NUMBEROFACTIVEUSERS")));
		c.addI18nAlias(new I18nAliasImpl(
				"numberofedits",
				true,
				Arrays.asList("NUMBEROFEDITS")));
		c.addI18nAlias(new I18nAliasImpl(
				"numberofviews",
				true,
				Arrays.asList("NUMBEROFVIEWS")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"pagename",
				true,
				Arrays.asList("PAGENAME", "PAGENAME:")));
		c.addI18nAlias(new I18nAliasImpl(
				"pagenamee",
				true,
				Arrays.asList("PAGENAMEE", "PAGENAMEE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"namespace",
				true,
				Arrays.asList("NAMESPACE", "NAMESPACE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"namespacee",
				true,
				Arrays.asList("NAMESPACEE", "NAMESPACEE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"namespacenumber",
				true,
				Arrays.asList("NAMESPACENUMBER", "NAMESPACENUMBER:")));
		c.addI18nAlias(new I18nAliasImpl(
				"talkspace",
				true,
				Arrays.asList("TALKSPACE", "TALKSPACE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"talkspacee",
				true,
				Arrays.asList("TALKSPACEE", "TALKSPACEE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"subjectspace",
				true,
				Arrays.asList("SUBJECTSPACE", "SUBJECTSPACE:", "ARTICLESPACE", "ARTICLESPACE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"subjectspacee",
				true,
				Arrays.asList("SUBJECTSPACEE", "SUBJECTSPACEE:", "ARTICLESPACEE", "ARTICLESPACEE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"fullpagename",
				true,
				Arrays.asList("FULLPAGENAME", "FULLPAGENAME:")));
		c.addI18nAlias(new I18nAliasImpl(
				"fullpagenamee",
				true,
				Arrays.asList("FULLPAGENAMEE", "FULLPAGENAMEE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"subpagename",
				true,
				Arrays.asList("SUBPAGENAME", "SUBPAGENAME:")));
		c.addI18nAlias(new I18nAliasImpl(
				"subpagenamee",
				true,
				Arrays.asList("SUBPAGENAMEE", "SUBPAGENAMEE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"rootpagename",
				true,
				Arrays.asList("ROOTPAGENAME", "ROOTPAGENAME:")));
		c.addI18nAlias(new I18nAliasImpl(
				"rootpagenamee",
				true,
				Arrays.asList("ROOTPAGENAMEE", "ROOTPAGENAMEE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"basepagename",
				true,
				Arrays.asList("BASEPAGENAME", "BASEPAGENAME:")));
		c.addI18nAlias(new I18nAliasImpl(
				"basepagenamee",
				true,
				Arrays.asList("BASEPAGENAMEE", "BASEPAGENAMEE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"talkpagename",
				true,
				Arrays.asList("TALKPAGENAME", "TALKPAGENAME:")));
		c.addI18nAlias(new I18nAliasImpl(
				"talkpagenamee",
				true,
				Arrays.asList("TALKPAGENAMEE", "TALKPAGENAMEE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"subjectpagename",
				true,
				Arrays.asList("SUBJECTPAGENAME", "SUBJECTPAGENAME:", "ARTICLEPAGENAME", "ARTICLEPAGENAME:")));
		c.addI18nAlias(new I18nAliasImpl(
				"subjectpagenamee",
				true,
				Arrays.asList("SUBJECTPAGENAMEE", "SUBJECTPAGENAMEE:", "ARTICLEPAGENAMEE", "ARTICLEPAGENAMEE:")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"msg",
				false,
				Arrays.asList("MSG:")));
		c.addI18nAlias(new I18nAliasImpl(
				"subst",
				false,
				Arrays.asList("SUBST:")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"safesubst",
				false,
				Arrays.asList("SAFESUBST:")));
		c.addI18nAlias(new I18nAliasImpl(
				"msgnw",
				false,
				Arrays.asList("MSGNW:")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"img_thumbnail",
				true,
				Arrays.asList("thumbnail", "thumb")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_manualthumb",
				true,
				Arrays.asList("thumbnail=$1", "thumb=$1")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_right",
				true,
				Arrays.asList("right")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_left",
				true,
				Arrays.asList("left")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_none",
				true,
				Arrays.asList("none")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_width",
				true,
				Arrays.asList("$1px")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_center",
				true,
				Arrays.asList("center", "centre")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_framed",
				true,
				Arrays.asList("framed", "enframed", "frame")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_frameless",
				true,
				Arrays.asList("frameless")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_page",
				true,
				Arrays.asList("page=$1", "page $1")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_upright",
				true,
				Arrays.asList("upright", "upright=$1", "upright $1")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_border",
				true,
				Arrays.asList("border")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_baseline",
				true, Arrays.asList("baseline")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_sub",
				true,
				Arrays.asList("sub")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_super",
				true,
				Arrays.asList("super", "sup")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_top",
				true,
				Arrays.asList("top")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_text_top",
				true,
				Arrays.asList("text-top")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_middle",
				true,
				Arrays.asList("middle")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_bottom",
				true,
				Arrays.asList("bottom")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_text_bottom",
				true,
				Arrays.asList("text-bottom")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_link",
				true,
				Arrays.asList("link=$1")));
		c.addI18nAlias(new I18nAliasImpl(
				"img_alt",
				true,
				Arrays.asList("alt=$1")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"int",
				false,
				Arrays.asList("INT:")));
		c.addI18nAlias(new I18nAliasImpl(
				"sitename",
				true,
				Arrays.asList("SITENAME")));
		c.addI18nAlias(new I18nAliasImpl(
				"ns",
				false,
				Arrays.asList("NS:")));
		c.addI18nAlias(new I18nAliasImpl(
				"nse",
				false,
				Arrays.asList("NSE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"localurl",
				false,
				Arrays.asList("LOCALURL:")));
		c.addI18nAlias(new I18nAliasImpl(
				"localurle",
				false,
				Arrays.asList("LOCALURLE:")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"articlepath",
				false,
				Arrays.asList("ARTICLEPATH")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"server",
				false,
				Arrays.asList("SERVER")));
		c.addI18nAlias(new I18nAliasImpl(
				"servername",
				false,
				Arrays.asList("SERVERNAME")));
		c.addI18nAlias(new I18nAliasImpl(
				"scriptpath",
				false,
				Arrays.asList("SCRIPTPATH")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"stylepath",
				false,
				Arrays.asList("STYLEPATH")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"grammar",
				false,
				Arrays.asList("GRAMMAR:")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"gender",
				false,
				Arrays.asList("GENDER:")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"currentweek",
				true,
				Arrays.asList("CURRENTWEEK")));
		c.addI18nAlias(new I18nAliasImpl(
				"currentdow",
				true,
				Arrays.asList("CURRENTDOW")));
		c.addI18nAlias(new I18nAliasImpl(
				"localweek",
				true,
				Arrays.asList("LOCALWEEK")));
		c.addI18nAlias(new I18nAliasImpl(
				"localdow",
				true,
				Arrays.asList("LOCALDOW")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"revisionid",
				true,
				Arrays.asList("REVISIONID")));
		c.addI18nAlias(new I18nAliasImpl(
				"revisionday",
				true,
				Arrays.asList("REVISIONDAY")));
		c.addI18nAlias(new I18nAliasImpl(
				"revisionday2",
				true,
				Arrays.asList("REVISIONDAY2")));
		c.addI18nAlias(new I18nAliasImpl(
				"revisionmonth",
				true,
				Arrays.asList("REVISIONMONTH")));
		c.addI18nAlias(new I18nAliasImpl(
				"revisionmonth1",
				true,
				Arrays.asList("REVISIONMONTH1")));
		c.addI18nAlias(new I18nAliasImpl(
				"revisionyear",
				true,
				Arrays.asList("REVISIONYEAR")));
		c.addI18nAlias(new I18nAliasImpl(
				"revisiontimestamp",
				true,
				Arrays.asList("REVISIONTIMESTAMP")));
		c.addI18nAlias(new I18nAliasImpl(
				"revisionuser",
				true,
				Arrays.asList("REVISIONUSER")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"plural",
				false,
				Arrays.asList("PLURAL:")));
		c.addI18nAlias(new I18nAliasImpl(
				"fullurl",
				false,
				Arrays.asList("FULLURL:")));
		c.addI18nAlias(new I18nAliasImpl(
				"fullurle",
				false,
				Arrays.asList("FULLURLE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"canonicalurl",
				false,
				Arrays.asList("CANONICALURL:")));
		c.addI18nAlias(new I18nAliasImpl(
				"canonicalurle",
				false,
				Arrays.asList("CANONICALURLE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"lcfirst",
				false,
				Arrays.asList("LCFIRST:")));
		c.addI18nAlias(new I18nAliasImpl(
				"ucfirst",
				false,
				Arrays.asList("UCFIRST:")));
		c.addI18nAlias(new I18nAliasImpl(
				"lc",
				false,
				Arrays.asList("LC:")));
		c.addI18nAlias(new I18nAliasImpl(
				"uc",
				false,
				Arrays.asList("UC:")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"raw",
				false,
				Arrays.asList("RAW:")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"displaytitle",
				true,
				Arrays.asList("DISPLAYTITLE:")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"rawsuffix",
				true,
				Arrays.asList("R")));
		c.addI18nAlias(new I18nAliasImpl(
				"currentversion",
				true,
				Arrays.asList("CURRENTVERSION")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"urlencode",
				false,
				Arrays.asList("URLENCODE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"anchorencode",
				false,
				Arrays.asList("ANCHORENCODE:")));
		c.addI18nAlias(new I18nAliasImpl(
				"currenttimestamp",
				true,
				Arrays.asList("CURRENTTIMESTAMP")));
		c.addI18nAlias(new I18nAliasImpl(
				"localtimestamp",
				true,
				Arrays.asList("LOCALTIMESTAMP")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"directionmark",
				true,
				Arrays.asList("DIRECTIONMARK", "DIRMARK")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"contentlanguage",
				true,
				Arrays.asList("CONTENTLANGUAGE", "CONTENTLANG")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"pagesinnamespace",
				true,
				Arrays.asList("PAGESINNAMESPACE:", "PAGESINNS:")));
		c.addI18nAlias(new I18nAliasImpl(
				"numberofadmins",
				true,
				Arrays.asList("NUMBEROFADMINS")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"formatnum",
				false,
				Arrays.asList("FORMATNUM:")));
		c.addI18nAlias(new I18nAliasImpl(
				"padleft",
				false,
				Arrays.asList("PADLEFT:")));
		c.addI18nAlias(new I18nAliasImpl(
				"padright",
				false,
				Arrays.asList("PADRIGHT:")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"special",
				false,
				Arrays.asList("special")));
		c.addI18nAlias(new I18nAliasImpl(
				"speciale",
				false,
				Arrays.asList("speciale")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"defaultsort",
				true,
				Arrays.asList("DEFAULTSORT:", "DEFAULTSORTKEY:", "DEFAULTCATEGORYSORT:")));
		c.addI18nAlias(new I18nAliasImpl(
				"filepath",
				false,
				Arrays.asList("FILEPATH:")));
		c.addI18nAlias(new I18nAliasImpl(
				"tag",
				false,
				Arrays.asList("#tag:")));
		c.addI18nAlias(new I18nAliasImpl(
				"hiddencat",
				true,
				Arrays.asList("__HIDDENCAT__")));
		c.addI18nAlias(new I18nAliasImpl(
				"expectunusedcategory",
				true,
				Arrays.asList("__EXPECTUNUSEDCATEGORY__")));
		c.addI18nAlias(new I18nAliasImpl(
				"expectunusedtemplate",
				true,
				Arrays.asList("__EXPECTUNUSEDTEMPLATE__")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"pagesincategory",
				true,
				Arrays.asList("PAGESINCATEGORY", "PAGESINCAT")));
		c.addI18nAlias(new I18nAliasImpl(
				"pagesize",
				true,
				Arrays.asList("PAGESIZE")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"index",
				true,
				Arrays.asList("__INDEX__")));
		c.addI18nAlias(new I18nAliasImpl(
				"noindex",
				true,
				Arrays.asList("__NOINDEX__")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"numberingroup",
				true,
				Arrays.asList("NUMBERINGROUP", "NUMINGROUP")));
		*/
		c.addI18nAlias(new I18nAliasImpl(
				"staticredirect",
				true,
				Arrays.asList("__STATICREDIRECT__")));
		c.addI18nAlias(new I18nAliasImpl(
				"protectionlevel",
				true,
				Arrays.asList("PROTECTIONLEVEL:")));
		/*
		c.addI18nAlias(new I18nAliasImpl(
				"formatdate",
				false,
				Arrays.asList("formatdate", "dateformat")));
		c.addI18nAlias(new I18nAliasImpl(
				"url_path",
				false,
				Arrays.asList("PATH")));
		c.addI18nAlias(new I18nAliasImpl(
				"url_wiki",
				false,
				Arrays.asList("WIKI")));
		c.addI18nAlias(new I18nAliasImpl(
				"url_query",
				false,
				Arrays.asList("QUERY")));
		c.addI18nAlias(new I18nAliasImpl(
				"defaultsort_noerror",
				false,
				Arrays.asList("noerror")));
		c.addI18nAlias(new I18nAliasImpl(
				"defaultsort_noreplace",
				false,
				Arrays.asList("noreplace")));
		*/
	}

	protected void addParserFunctions(WikiConfigImpl c)
	{
		addParserFunctions(c, false);
	}

	protected void addParserFunctions(WikiConfigImpl c, boolean skipGroupsWithMissingAliases)
	{
		addParserFunctionGroup(c, BuiltInParserFunctions.group(c), skipGroupsWithMissingAliases);

		addParserFunctionGroup(c, CorePfnBehaviorSwitches.group(c), skipGroupsWithMissingAliases);

		addParserFunctionGroup(c, CorePfnFunctionsFormatting.group(c), skipGroupsWithMissingAliases);
		addParserFunctionGroup(c, CorePfnFunctionsLocalization.group(c), skipGroupsWithMissingAliases);
		addParserFunctionGroup(c, CorePfnFunctionsMiscellaneous.group(c), skipGroupsWithMissingAliases);
		addParserFunctionGroup(c, CorePfnFunctionsNamespaces.group(c), skipGroupsWithMissingAliases);
		addParserFunctionGroup(c, CorePfnFunctionsUrlData.group(c), skipGroupsWithMissingAliases);

		addParserFunctionGroup(c, CorePfnVariablesDateAndTime.group(c), skipGroupsWithMissingAliases);
		addParserFunctionGroup(c, CorePfnVariablesNamespaces.group(c), skipGroupsWithMissingAliases);
		addParserFunctionGroup(c, CorePfnVariablesPageNames.group(c), skipGroupsWithMissingAliases);
		addParserFunctionGroup(c, CorePfnVariablesStatistics.group(c), skipGroupsWithMissingAliases);
		addParserFunctionGroup(c, CorePfnVariablesTechnicalMetadata.group(c), skipGroupsWithMissingAliases);

		addParserFunctionGroup(c, ParserFunctionsPfnExt.group(c), skipGroupsWithMissingAliases);
		addParserFunctionGroup(c, ConvertPnfExt.group(c), skipGroupsWithMissingAliases);
	}

	/**
	 * Registers a group of parser functions.
	 *
	 * @param skipGroupsWithMissingAliases
	 *            If true, only the parser functions the configuration has an
	 *            alias for are registered (a wiki may not know every parser
	 *            function of a group). A group without any such parser
	 *            function is skipped.
	 */
	protected void addParserFunctionGroup(
			WikiConfigImpl c,
			ParserFunctionGroup group,
			boolean skipGroupsWithMissingAliases)
	{
		if (!skipGroupsWithMissingAliases)
		{
			c.addParserFunctionGroup(group);
			return;
		}

		ParserFunctionGroup known = new ParserFunctionGroup(group.getName());
		for (ParserFunctionBase pfn : group.getParserFunctions())
		{
			I18nAliasImpl alias = c.getI18nAliasById(pfn.getId());
			if (alias != null)
				known.addParserFunction(pfn);
		}

		if (!known.getParserFunctions().isEmpty())
			c.addParserFunctionGroup(known);
	}

	protected void addTagExtensions(WikiConfigImpl c)
	{
		c.addTagExtensionGroup(BuiltInTagExtensions.group(c));

		c.addTagExtensionGroup(MathTagExt.group(c));

		c.addTagExtensionGroup(RefTagExt.group(c));
	}
}
