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

package org.sweble.wikitext.engine.utils;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.collections4.map.MultiValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sweble.wikitext.engine.config.I18nAliasImpl;
import org.sweble.wikitext.engine.config.InterwikiImpl;
import org.sweble.wikitext.engine.config.NamespaceCase;
import org.sweble.wikitext.engine.config.NamespaceImpl;
import org.sweble.wikitext.engine.config.TagExtensionGroup;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.ext.generic.GenericTagExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * @author Samy Ateia, samyateia@hotmail.de
 */
public class LanguageConfigGenerator
{
	private static final Logger logger = LoggerFactory.getLogger(LanguageConfigGenerator.class);

	public static final String API_ENDPOINT_MAGICWORDS =
			".wikipedia.org/w/api.php?action=query&meta=siteinfo&siprop=magicwords&format=xml";

	public static final String API_ENDPOINT_INTERWIKIMAP =
			".wikipedia.org/w/api.php?action=query&meta=siteinfo&siprop=interwikimap&format=xml";

	public static final String API_ENDPOINT_NAMESPACES =
			".wikipedia.org/w/api.php?action=query&meta=siteinfo&siprop=namespaces&format=xml";

	public static final String API_ENDPOINT_NAMESPACEALIASES =
			".wikipedia.org/w/api.php?action=query&meta=siteinfo&siprop=namespacealiases&format=xml";

	/**
	 * Name of the group holding the extension tags reported by the wiki for
	 * which no implementation is available.
	 */
	public static final String SITE_TAG_EXTENSION_GROUP = "Extension - Site (generic)";

	/** Extension tags are reported as {@code <name>}. */
	private static final Pattern EXTENSION_TAG = Pattern.compile("^\\s*<?\\s*([^<>\\s]+)\\s*>?\\s*$");

	/**
	 * The path of the API relative to the server on Wikimedia wikis.
	 */
	public static final String DEFAULT_API_PATH = "/w/api.php";

	private static final String DEFAULT_SCHEME = "https";

	/**
	 * Timeout for establishing a connection to the API in milliseconds.
	 */
	public static final int CONNECT_TIMEOUT_MILLIS = 10000;

	/**
	 * Timeout for reading from the API in milliseconds.
	 */
	public static final int READ_TIMEOUT_MILLIS = 30000;

	/**
	 * The maximum size of a response of the API in bytes.
	 */
	public static final long MAX_RESPONSE_BYTES = 16L * 1024 * 1024;

	/**
	 * The maximum number of HTTP redirects that are followed. Only redirects
	 * to the same scheme, host and port are followed.
	 */
	public static final int MAX_REDIRECTS = 3;

	/**
	 * Matches the body of a MediaWiki link trail regex like
	 * "^([a-z]+)(.*)$". The first group matches the trail itself.
	 */
	private static final Pattern LINK_TRAIL_BODY =
			Pattern.compile("\\^\\((.*)\\)\\(\\.\\*\\)\\$", Pattern.DOTALL);

	/**
	 * Matches the body of the link prefix regex MediaWiki builds from the link
	 * prefix character set, "^((?&gt;.*[^charset]|))(.+)$". Older
	 * configurations use "^((?&gt;.*[^charset])|)(.+)$". The first group
	 * matches the character set.
	 */
	private static final Pattern LINK_PREFIX_CHARSET_BODY =
			Pattern.compile("\\^\\(\\(\\?>\\.\\*\\[\\^(.+)\\](?:\\|\\)|\\)\\|)\\)\\(\\.\\+\\)\\$", Pattern.DOTALL);

	/**
	 * Matches the body of a link prefix regex like "^(.*?)([a-z]+)$" as used
	 * by old MediaWiki versions. The first group matches the prefix itself.
	 */
	private static final Pattern LINK_PREFIX_BODY =
			Pattern.compile("\\^\\(\\.\\*\\?\\)\\((.+)\\)\\$", Pattern.DOTALL);

    private static final String DEFAULT_FALLBACK_USER_AGENT =
            "Sweble Wikitext/unknown (+https://github.com/rzo1/sweble-wikitext/";

	private static final int NS_SPECIAL = -1;

	private static final int NS_USER = 2;

	private static final int NS_USER_TALK = 3;

	private static final int NS_MEDIAWIKI = 8;

	private static final int NS_MEDIAWIKI_TALK = 9;


    // =========================================================================

	public static WikiConfig generateWikiConfig(String languagePrefix)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		return generateWikiConfig(
				languagePrefix + " wiki",
				"https://" + languagePrefix + ".wikipedia.org",
				languagePrefix);
	}

	/**
	 * Generates the configuration of the wiki at the given URL from its
	 * siteinfo. The API endpoint is derived from the site URL, see
	 * {@link #getApiUrl(String)}.
	 *
	 * @param siteName
	 *            The name of the wiki. Only used if the siteinfo does not
	 *            report a site name.
	 * @param siteURL
	 *            The URL of the wiki, e.g. "https://de.wiktionary.org".
	 * @param languagePrefix
	 *            The content language and interwiki prefix of the wiki.
	 */
	public static WikiConfig generateWikiConfig(
			String siteName,
			String siteURL,
			String languagePrefix)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		String apiUrl = getApiUrl(siteURL);
		return generateWikiConfig(
				siteName,
				siteURL,
				languagePrefix,
				getSiteInfoUrl(apiUrl, "namespacealiases"),
				getSiteInfoUrl(apiUrl, "namespaces"),
				getSiteInfoUrl(apiUrl, "interwikimap"),
				getSiteInfoUrl(apiUrl, "magicwords"),
				getSiteInfoUrl(apiUrl, "general"),
				getSiteInfoUrl(apiUrl, "extensiontags"));
	}

	/**
	 * Generates a configuration without the general site information and
	 * without querying the extension tags of the wiki: The site name and
	 * URL are used as given, the article path, link trail and time zone keep
	 * their defaults and the extension tags of {@link DefaultConfigEnWp} are
	 * registered.
	 */
	public static WikiConfig generateWikiConfig(
			String siteName,
			String siteUrl,
			String languagePrefix,
			String apiUrlNamespacealiases,
			String apiUrlNamespaces,
			String apiUrlInterwikimap,
			String apiUrlMagicwords)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		return generateWikiConfig(
				siteName,
				siteUrl,
				languagePrefix,
				apiUrlNamespacealiases,
				apiUrlNamespaces,
				apiUrlInterwikimap,
				apiUrlMagicwords,
				null,
				null);
	}

	/**
	 * @param apiUrlGeneral
	 *            The URL of the general site information (siprop=general) or
	 *            null. See {@link #addGeneralSiteInfo(WikiConfigImpl, String,
	 *            String)} for what it configures. Its case setting is used for
	 *            the namespaces that do not report their own.
	 * @param apiUrlExtensiontags
	 *            If {@code null}, the extension tags of
	 *            {@link DefaultConfigEnWp} are registered.
	 */
	public static WikiConfig generateWikiConfig(
			String siteName,
			String siteUrl,
			String languagePrefix,
			String apiUrlNamespacealiases,
			String apiUrlNamespaces,
			String apiUrlInterwikimap,
			String apiUrlMagicwords,
			String apiUrlGeneral,
			String apiUrlExtensiontags)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		WikiConfigImpl wikiConfig = new WikiConfigImpl();
		wikiConfig.setSiteName(siteName);
		wikiConfig.setWikiUrl(siteUrl);
		wikiConfig.setContentLang(languagePrefix);
		wikiConfig.setIwPrefix(languagePrefix);

		DefaultConfigEnWp config = new DefaultConfigEnWp();
		config.configureEngine(wikiConfig);

		// Used for namespaces that do not report their own case setting
		NamespaceCase wikiCase = NamespaceCase.FIRST_LETTER;
		if (apiUrlGeneral != null)
		{
			NamedNodeMap general = getGeneralSiteInfo(apiUrlGeneral);
			if (general != null)
			{
				// Overrides the link trail and prefix set up by configureEngine()
				addGeneralSiteInfo(wikiConfig, general, siteUrl);
				wikiCase = getCase(general, NamespaceCase.FIRST_LETTER);
			}
		}

		MultiValueMap namespaceAliases = getNamespaceAliases(apiUrlNamespacealiases);
		addNamespaces(wikiConfig, apiUrlNamespaces, namespaceAliases, wikiCase);
		addInterwikis(wikiConfig, apiUrlInterwikimap);
		addi18NAliases(wikiConfig, apiUrlMagicwords);

		config.addParserFunctions(wikiConfig, true);
		if (apiUrlExtensiontags != null)
		{
			// Only the implemented tag extensions and the ones the wiki knows
			new DefaultConfig().addTagExtensions(wikiConfig);
			addTagExtensions(wikiConfig, apiUrlExtensiontags);
		}
		else
		{
			config.addTagExtensions(wikiConfig);
		}

		return wikiConfig;
	}

	/**
	 * Derives the URL of the API from the URL of a wiki. Wikimedia wikis
	 * serve the API at {@value #DEFAULT_API_PATH}, e.g.
	 * "https://de.wiktionary.org" results in
	 * "https://de.wiktionary.org/w/api.php". A URL that already points to an
	 * api.php is used as is (without query), which allows for wikis with a
	 * different script path. Protocol-relative URLs and URLs without scheme
	 * use https.
	 */
	public static String getApiUrl(String siteUrl)
	{
		String url = siteUrl.trim();
		if (url.startsWith("//"))
			url = DEFAULT_SCHEME + ":" + url;
		else if (!url.contains("://"))
			url = DEFAULT_SCHEME + "://" + url;

		URI uri;
		try
		{
			uri = new URI(url);
		}
		catch (URISyntaxException e)
		{
			throw new IllegalArgumentException("Not a valid site URL: `" + siteUrl + "'.", e);
		}

		if (uri.getRawAuthority() == null)
			throw new IllegalArgumentException("Not a valid site URL: `" + siteUrl + "'.");

		String server = uri.getScheme() + "://" + uri.getRawAuthority();
		String path = uri.getRawPath();
		if (path != null && path.endsWith("/api.php"))
			return server + path;
		return server + DEFAULT_API_PATH;
	}

	/**
	 * Returns the URL of a siteinfo query for the given property, e.g.
	 * "general" or "namespaces".
	 */
	public static String getSiteInfoUrl(String apiUrl, String siprop)
	{
		return apiUrl + "?action=query&meta=siteinfo&siprop=" + siprop + "&format=xml";
	}

	/**
	 * Configures the site name, wiki URL (MediaWiki's $wgServer followed by
	 * $wgScript), article path ($wgServer followed by $wgArticlePath), link
	 * trail, link prefix and time zone from the general site information
	 * (siprop=general). Has to be called after the parser was configured
	 * since it overrides the link trail and the link prefix. The case
	 * setting of the wiki is not part of the configuration, the case setting
	 * of each namespace is configured by
	 * {@link #addNamespaces(WikiConfigImpl, String, MultiValueMap, NamespaceCase)}.
	 *
	 * @param siteUrl
	 *            The URL of the wiki. Its scheme completes the
	 *            protocol-relative server URL reported by Wikimedia wikis (e.g.
	 *            "//de.wikipedia.org"). If null, https is used.
	 */
	public static void addGeneralSiteInfo(
			WikiConfigImpl wikiConfig,
			String apiUrlGeneral,
			String siteUrl)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		NamedNodeMap attributes = getGeneralSiteInfo(apiUrlGeneral);
		if (attributes != null)
			addGeneralSiteInfo(wikiConfig, attributes, siteUrl);
	}

	/**
	 * Returns the attributes of the general site information or null if the
	 * response contains none.
	 */
	private static NamedNodeMap getGeneralSiteInfo(String apiUrlGeneral)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		Document document = getXMLFromUrl(apiUrlGeneral);
		NodeList generalNodes = document.getElementsByTagName("general");
		if (generalNodes.getLength() == 0)
		{
			logger.warn("No general site information found at `{}'", apiUrlGeneral);
			return null;
		}
		return generalNodes.item(0).getAttributes();
	}

	private static void addGeneralSiteInfo(
			WikiConfigImpl wikiConfig,
			NamedNodeMap attributes,
			String siteUrl)
	{
		String siteName = getAttributeValue(attributes, "sitename");
		if (siteName != null)
			wikiConfig.setSiteName(siteName);

		String server = getAttributeValue(attributes, "server");
		if (server != null)
		{
			if (server.startsWith("//"))
				server = getScheme(siteUrl) + ":" + server;

			String script = getAttributeValue(attributes, "script");
			if (script != null)
				wikiConfig.setWikiUrl(server + script);

			String articlePath = getAttributeValue(attributes, "articlepath");
			if (articlePath != null)
				wikiConfig.setArticlePath(server + articlePath);
		}

		String linkTrail = getAttributeValue(attributes, "linktrail");
		if (linkTrail != null)
		{
			String pattern = convertLinkTrail(linkTrail);
			if (pattern != null)
			{
				wikiConfig.getParserConfig().setInternalLinkPostfixPattern(pattern);
			}
			else
			{
				logger.warn("Cannot convert the link trail `{}', keeping `{}'",
						linkTrail, wikiConfig.getParserConfig().getInternalLinkPostfixPattern());
			}
		}

		String linkPrefixCharset = getAttributeValue(attributes, "linkprefixcharset");
		String linkPrefix = getAttributeValue(attributes, "linkprefix");
		if (linkPrefixCharset != null || linkPrefix != null)
		{
			String pattern = convertLinkPrefix(linkPrefixCharset, linkPrefix);
			if (pattern == null)
			{
				logger.warn("Cannot convert the link prefix `{}' (character set `{}'), internal links get no prefix",
						linkPrefix, linkPrefixCharset);
			}
			wikiConfig.getParserConfig().setInternalLinkPrefixPattern(
					(pattern == null || pattern.isEmpty()) ? null : pattern);
		}

		String timezone = getAttributeValue(attributes, "timezone");
		if (timezone != null)
		{
			TimeZone tz = TimeZone.getTimeZone(timezone);
			// Unknown IDs silently result in GMT
			if (tz.getID().equals(timezone))
				wikiConfig.setTimezone(tz);
			else
				logger.warn("Unknown time zone `{}'", timezone);
		}
	}

	/**
	 * Converts a link trail as reported by the siteinfo API into the format
	 * expected by
	 * {@link org.sweble.wikitext.engine.config.ParserConfigImpl#setInternalLinkPostfixPattern(String)}.
	 *
	 * MediaWiki's link trail is a PCRE regex like "/^([a-z]+)(.*)$/sD" that
	 * is applied to the text following a link, its first group is the trail
	 * that becomes part of the link. The parser instead takes everything the
	 * postfix pattern matches at the beginning of the text following a link.
	 * Therefore only the first group is kept and the modifiers are turned
	 * into embedded flags. PHP's "u" modifier (UTF-8 with Unicode character
	 * properties) becomes Java's UNICODE_CHARACTER_CLASS flag.
	 *
	 * @return The postfix pattern, an empty pattern if the wiki does not use
	 *         link trails (e.g. "/^()(.*)$/sD" on zh.wikipedia) or null if the
	 *         link trail cannot be converted.
	 */
	public static String convertLinkTrail(String linkTrail)
	{
		if (linkTrail.isEmpty())
			return "";

		char delimiter = linkTrail.charAt(0);
		int end = linkTrail.lastIndexOf(delimiter);
		if (Character.isLetterOrDigit(delimiter) || delimiter == '\\' || end <= 0)
			return null;

		Matcher m = LINK_TRAIL_BODY.matcher(linkTrail.substring(1, end));
		if (!m.matches())
			return null;
		String trail = m.group(1);

		String flags = convertModifiers(linkTrail.substring(end + 1));
		if (flags == null)
			return null;

		String pattern = (trail.isEmpty() || flags.isEmpty()) ?
				trail :
				"(?" + flags + ":" + trail + ")";
		try
		{
			Pattern.compile(pattern);
		}
		catch (PatternSyntaxException e)
		{
			return null;
		}
		return pattern;
	}

	/**
	 * Converts the link prefix as reported by the siteinfo API into the format
	 * expected by
	 * {@link org.sweble.wikitext.engine.config.ParserConfigImpl#setInternalLinkPrefixPattern(String)}.
	 *
	 * Wikis whose language uses link prefixes (MediaWiki's
	 * Language::linkPrefixExtension(), e.g. Arabic) report the characters a
	 * prefix may consist of as "linkprefixcharset", a PCRE character class
	 * body like "a-zA-Z\x{0610}-\x{061A}". Parser::handleInternalLinks2()
	 * attaches the longest run of these characters directly in front of a
	 * link to the link. Additionally the wiki reports this as PCRE regex
	 * "/^((?&gt;.*[^charset]|))(.+)$/sDu" in "linkprefix", its second group
	 * being the prefix. Old MediaWiki versions only report "linkprefix",
	 * possibly as "/^(.*?)(prefix)$/sDu".
	 *
	 * The parser instead takes everything its prefix pattern matches at the
	 * end of the text in front of a link. Therefore the character set becomes
	 * the pattern "[charset]+" and the modifiers become embedded flags like
	 * in {@link #convertLinkTrail(String)}. The character set takes
	 * precedence over the regex.
	 *
	 * @param linkPrefixCharset
	 *            The "linkprefixcharset" of the wiki or null.
	 * @param linkPrefix
	 *            The "linkprefix" of the wiki or null.
	 * @return The prefix pattern, an empty pattern if the wiki does not use
	 *         link prefixes (both values empty or null) or null if the link
	 *         prefix cannot be converted.
	 */
	public static String convertLinkPrefix(String linkPrefixCharset, String linkPrefix)
	{
		if (linkPrefixCharset != null && !linkPrefixCharset.isEmpty())
			linkPrefix = "/^((?>.*[^" + linkPrefixCharset + "]|))(.+)$/sDu";

		if (linkPrefix == null || linkPrefix.isEmpty())
			return "";

		char delimiter = linkPrefix.charAt(0);
		int end = linkPrefix.lastIndexOf(delimiter);
		if (Character.isLetterOrDigit(delimiter) || delimiter == '\\' || end <= 0)
			return null;

		String body = linkPrefix.substring(1, end);
		String prefix;
		Matcher m = LINK_PREFIX_CHARSET_BODY.matcher(body);
		if (m.matches())
		{
			String charset = convertCharacterClassBody(m.group(1));
			if (charset == null)
				return null;
			prefix = "[" + charset + "]+";
		}
		else
		{
			m = LINK_PREFIX_BODY.matcher(body);
			if (!m.matches())
				return null;
			prefix = m.group(1);
		}

		String flags = convertModifiers(linkPrefix.substring(end + 1));
		if (flags == null)
			return null;

		String pattern = flags.isEmpty() ? prefix : "(?" + flags + ":" + prefix + ")";
		try
		{
			// The parser appends the anchor
			Pattern.compile("(" + pattern + ")$");
		}
		catch (PatternSyntaxException e)
		{
			return null;
		}
		return pattern;
	}

	/**
	 * Turns PHP PCRE modifiers into the flags of a Java embedded flag
	 * expression, e.g. "sDu" into "sU".
	 *
	 * @return The flags or null if a modifier is not supported.
	 */
	private static String convertModifiers(String modifiers)
	{
		StringBuilder flags = new StringBuilder();
		for (char modifier : modifiers.toCharArray())
		{
			switch (modifier)
			{
				case 'i':
				case 'm':
				case 's':
				case 'x':
					flags.append(modifier);
					break;
				case 'u':
					flags.append('U');
					break;
				case 'A':
				case 'D':
				case 'S':
					// No effect on a pattern that only matches the trail or prefix
					break;
				default:
					return null;
			}
		}
		return flags.toString();
	}

	/**
	 * Converts the body of a PCRE character class into the body of a Java
	 * character class. Unlike in PCRE, '[' and "&amp;&amp;" are special
	 * inside a Java character class and a leading '^' would negate the class
	 * built from the body.
	 *
	 * @return The converted body or null if it uses POSIX classes like
	 *         "[:alpha:]" or ends in an incomplete escape sequence.
	 */
	private static String convertCharacterClassBody(String body)
	{
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < body.length(); i++)
		{
			char c = body.charAt(i);
			switch (c)
			{
				case '\\':
					if (i + 1 >= body.length())
						return null;
					sb.append(c).append(body.charAt(++i));
					break;
				case '[':
					if (i + 1 < body.length() && body.charAt(i + 1) == ':')
						return null;
					sb.append("\\[");
					break;
				case '&':
					sb.append("\\&");
					break;
				case '^':
					sb.append((i == 0) ? "\\^" : "^");
					break;
				default:
					sb.append(c);
					break;
			}
		}
		return sb.toString();
	}

	private static String getScheme(String siteUrl)
	{
		if (siteUrl != null)
		{
			int i = siteUrl.indexOf("://");
			if (i > 0)
				return siteUrl.substring(0, i);
		}
		return DEFAULT_SCHEME;
	}

	private static String getAttributeValue(NamedNodeMap attributes, String name)
	{
		Node node = attributes.getNamedItem(name);
		return (node != null) ? node.getNodeValue() : null;
	}

	/**
	 * Returns the setting of the {@code case} attribute of a namespace or of
	 * the general site information, or the given default if the attribute is
	 * missing or unknown.
	 */
	private static NamespaceCase getCase(NamedNodeMap attributes, NamespaceCase defaultCase)
	{
		String value = getAttributeValue(attributes, "case");
		if (value == null)
			return defaultCase;

		NamespaceCase result = NamespaceCase.fromValue(value);
		if (result == null)
		{
			logger.warn("Unknown case setting `{}', using `{}'", value, defaultCase.getValue());
			return defaultCase;
		}
		return result;
	}

	/**
	 * Returns the case setting of a namespace that does not report its own.
	 * Like MediaWiki's NamespaceInfo::isCapitalized() the special, user and
	 * MediaWiki namespaces and their talk namespaces are always first-letter,
	 * all others use the setting of the wiki.
	 */
	private static NamespaceCase getDefaultCase(int id, NamespaceCase wikiCase)
	{
		switch (id)
		{
			case NS_SPECIAL:
			case NS_USER:
			case NS_USER_TALK:
			case NS_MEDIAWIKI:
			case NS_MEDIAWIKI_TALK:
				return NamespaceCase.FIRST_LETTER;
			default:
				return wikiCase;
		}
	}

	/**
	 * Registers every extension tag reported by the wiki which is not
	 * registered yet as {@link GenericTagExtension}, so that its body is not
	 * parsed as wikitext.
	 */
	public static void addTagExtensions(
			WikiConfigImpl wikiConfig,
			String apiUrlExtensionTags)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		Document document = getXMLFromUrl(apiUrlExtensionTags);
		NodeList apiExtensionTagLists = document.getElementsByTagName("extensiontags");

		TagExtensionGroup group = new TagExtensionGroup(SITE_TAG_EXTENSION_GROUP);
		List<String> added = new ArrayList<String>();
		for (int i = 0; i < apiExtensionTagLists.getLength(); i++)
		{
			NodeList apiExtensionTags = apiExtensionTagLists.item(i).getChildNodes();
			for (int j = 0; j < apiExtensionTags.getLength(); j++)
			{
				Node apiExtensionTag = apiExtensionTags.item(j);
				if (apiExtensionTag.getNodeType() != Node.ELEMENT_NODE)
					continue;

				Matcher m = EXTENSION_TAG.matcher(apiExtensionTag.getTextContent());
				if (!m.matches())
				{
					logger.warn("Skipping malformed extension tag `{}'", apiExtensionTag.getTextContent());
					continue;
				}

				// MediaWiki registers extension tags in lower case
				String name = m.group(1).toLowerCase();
				if (wikiConfig.getTagExtension(name) != null || added.contains(name))
					continue;

				group.addTagExtension(new GenericTagExtension(wikiConfig, name));
				added.add(name);
			}
		}

		if (!added.isEmpty())
			wikiConfig.addTagExtensionGroup(group);
	}

	public static void addi18NAliases(
			WikiConfigImpl wikiConfig,
			String apiUrlMagicWords)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		// Template when to add '#' prefix to i18n aliases
		WikiConfigImpl template = new WikiConfigImpl();
		new DefaultConfigEnWp().addI18nAliases(template);

		Document document = getXMLFromUrl(apiUrlMagicWords);
		NodeList apiI18nAliases = document.getElementsByTagName("magicword");

		List<AliasCandidate> candidates = new ArrayList<AliasCandidate>();
		for (int i = 0; i < apiI18nAliases.getLength(); i++)
		{
			Node apiI18nAlias = apiI18nAliases.item(i);
			NamedNodeMap attributes = apiI18nAlias.getAttributes();

			String name = attributes.getNamedItem("name").getNodeValue();

			boolean isCaseSensitive = attributes.getNamedItem("case-sensitive") != null;

			I18nAliasImpl defaultAlias = template.getI18nAliasById(name);
			String prefix = "";
			String postfix = "";
			boolean postOptional = false;
			if (defaultAlias != null)
			{
				for (String a : defaultAlias.getAliases())
				{
					if (a.startsWith("#"))
					{
						prefix = "#";
					}

					if (a.endsWith(":"))
					{
						postfix = ":";
					}
					else
					{
						postOptional = true;
					}
				}
			}

			AliasCandidate candidate = new AliasCandidate(name, isCaseSensitive);
			// Pretty-printed responses contain whitespace between the
			// elements, magic words without aliases have no <aliases>.
			NodeList aliasesList = ((Element) apiI18nAlias).getElementsByTagName("alias");
			for (int j = 0; j < aliasesList.getLength(); j++)
			{
				Node aliasNode = aliasesList.item(j);
				String aliasString = aliasNode.getTextContent();
				if (prefix.length() > 0 && !aliasString.startsWith(prefix))
				{
					aliasString = prefix + aliasString;
				}
				if (postOptional || postfix.length() == 0 || aliasString.endsWith(postfix))
				{
					// Add without change to the postfix:
					candidate.addName(aliasString, false);
				}
				if (postfix.length() > 0)
				{
					if (!aliasString.endsWith(postfix))
					{
						// Add missing postfix:
						candidate.addName(aliasString + postfix, true);
					}
					else if (postOptional)
					{
						// Remove existing, optional postfix:
						candidate.addName(aliasString.substring(0, aliasString.length() - postfix.length()), true);
					}
				}
			}
			candidates.add(candidate);
		}

		// Names are looked up case-insensitively, so each name may only
		// belong to one alias. Decide up front which alias gets a contested
		// name instead of depending on the registration order.
		Map<String, AliasCandidate> owners = resolveNameConflicts(candidates);

		for (AliasCandidate candidate : candidates)
		{
			ArrayList<String> aliases = new ArrayList<String>();
			for (String aliasName : candidate.names)
			{
				if (owners.get(aliasName.toLowerCase()) == candidate)
					aliases.add(aliasName);
			}

			I18nAliasImpl i18nAlias = new I18nAliasImpl(candidate.id, candidate.caseSensitive, aliases);
			try
			{
				wikiConfig.addI18nAlias(i18nAlias);
			}
			catch (IllegalArgumentException e)
			{
				// Only happens if the given configuration already contains an
				// alias with this id.
				logger.warn("Skipping i18n alias `{}': {}", candidate.id, e.getMessage());
			}
		}
	}

	/**
	 * Determines for every (lower-case) name which of the given aliases owns
	 * it. If several aliases claim the same name, the owner is chosen as
	 * follows:
	 * <ol>
	 * <li>For parser function names (ending in a colon) a case-sensitive alias
	 * wins over a case-insensitive one. This mirrors MediaWiki's
	 * Parser::callParserFunction(), which consults the case-sensitive function
	 * synonyms first. E.g. on ja.wikipedia {{名前空間:...}} invokes NAMESPACE
	 * and not ns, although both list "名前空間" as synonym.</li>
	 * <li>A name as reported by the siteinfo API wins over a name that was only
	 * generated by adding or removing the colon postfix.</li>
	 * <li>Otherwise the alias reported first by the API wins.</li>
	 * </ol>
	 */
	private static Map<String, AliasCandidate> resolveNameConflicts(
			List<AliasCandidate> candidates)
	{
		Map<String, AliasCandidate> owners = new HashMap<String, AliasCandidate>();
		for (AliasCandidate candidate : candidates)
		{
			for (String lcName : candidate.generated.keySet())
			{
				AliasCandidate owner = owners.get(lcName);
				if (owner == null)
				{
					owners.put(lcName, candidate);
					continue;
				}

				AliasCandidate winner = takesPrecedence(lcName, candidate, owner) ? candidate : owner;
				logger.debug("The name `{}' is claimed by the i18n aliases `{}' and `{}', assigning it to `{}'",
						lcName, owner.id, candidate.id, winner.id);
				owners.put(lcName, winner);
			}
		}
		return owners;
	}

	private static boolean takesPrecedence(
			String lcName,
			AliasCandidate challenger,
			AliasCandidate owner)
	{
		if (lcName.endsWith(":") && challenger.caseSensitive != owner.caseSensitive)
			return challenger.caseSensitive;

		boolean challengerGenerated = challenger.generated.get(lcName);
		boolean ownerGenerated = owner.generated.get(lcName);
		if (challengerGenerated != ownerGenerated)
			return !challengerGenerated;

		return false;
	}

	/**
	 * An i18n alias as reported by the siteinfo API together with the names
	 * derived from it, before it is registered with the configuration.
	 */
	private static final class AliasCandidate
	{
		private final String id;

		private final boolean caseSensitive;

		/** All names in the order they were produced. */
		private final List<String> names = new ArrayList<String>();

		/**
		 * Maps each lower-case name to whether it was only generated by adding
		 * or removing the colon postfix.
		 */
		private final Map<String, Boolean> generated = new LinkedHashMap<String, Boolean>();

		AliasCandidate(String id, boolean caseSensitive)
		{
			this.id = id;
			this.caseSensitive = caseSensitive;
		}

		void addName(String name, boolean isGenerated)
		{
			names.add(name);
			String lcName = name.toLowerCase();
			Boolean old = generated.get(lcName);
			if (old == null || old)
				generated.put(lcName, isGenerated);
		}
	}

	public static void addInterwikis(
			WikiConfigImpl wikiConfig,
			String apiUrlInterwikiMap)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		Document document = getXMLFromUrl(apiUrlInterwikiMap);
		NodeList apiInterwikis = document.getElementsByTagName("iw");

		for (int i = 0; i < apiInterwikis.getLength(); i++)
		{
			Node apiInterWiki = apiInterwikis.item(i);
			NamedNodeMap attributes = apiInterWiki.getAttributes();

			String prefixString = attributes.getNamedItem("prefix").getNodeValue();

			boolean isLocal = false; // if present set true else false
			Node localNode = attributes.getNamedItem("local");
			if (localNode != null)
			{
				isLocal = true;
			}
			boolean isTrans = false; // TODO check dokumentation if really always false?
			String urlStringApi = attributes.getNamedItem("url").getNodeValue();

			InterwikiImpl interwiki = new InterwikiImpl(prefixString, urlStringApi, isLocal, isTrans);
			wikiConfig.addInterwiki(interwiki);
		}
	}

	public static void addNamespaces(
			WikiConfigImpl wikiConfig,
			String apiUrlNamespaces,
			MultiValueMap nameSpaceAliases)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		addNamespaces(wikiConfig, apiUrlNamespaces, nameSpaceAliases, NamespaceCase.FIRST_LETTER);
	}

	/**
	 * @param wikiCase
	 *            The case setting of the wiki as reported by the general site
	 *            information. Only used for namespaces that do not report
	 *            their own case setting.
	 */
	public static void addNamespaces(
			WikiConfigImpl wikiConfig,
			String apiUrlNamespaces,
			MultiValueMap nameSpaceAliases,
			NamespaceCase wikiCase)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		Document document = getXMLFromUrl(apiUrlNamespaces);
		NodeList apiNamespaces = document.getElementsByTagName("ns");

		for (int i = 0; i < apiNamespaces.getLength(); i++)
		{
			Node apiNamespace = apiNamespaces.item(i);
			String name = apiNamespace.getTextContent();
			NamedNodeMap attributes = apiNamespace.getAttributes();
			Integer id = Integer.parseInt(attributes.getNamedItem("id").getNodeValue());
			String canonical = "";
			if (attributes.getNamedItem("canonical") != null)
			{
				canonical = attributes.getNamedItem("canonical").getNodeValue();
			}

			boolean fileNs = false;
			if (canonical.equals("File"))
			{
				fileNs = true;
			}

			Node subpages = attributes.getNamedItem("subpages");
			boolean canHaveSubpages = false;
			if (subpages != null)
			{
				canHaveSubpages = true;
			}

			Collection<String> aliases = new ArrayList<String>();
			if (nameSpaceAliases.containsKey(id))
			{
				@SuppressWarnings("unchecked")
				Collection<String> tmp = nameSpaceAliases.getCollection(id);
				aliases = tmp;
			}

			NamespaceCase nsCase = getCase(attributes, getDefaultCase(id.intValue(), wikiCase));

			NamespaceImpl namespace = new NamespaceImpl(
					id.intValue(),
					name,
					canonical,
					canHaveSubpages,
					fileNs,
					nsCase,
					aliases);
			wikiConfig.addNamespace(namespace);

			if (canonical.equals("Template"))
			{
				wikiConfig.setTemplateNamespace(namespace);
			}
			else if (id.intValue() == 0)
			{
				wikiConfig.setDefaultNamespace(namespace);
			}

		}
	}

	public static MultiValueMap getNamespaceAliases(
			String apiUrlNamespaceAliases)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		Document document = getXMLFromUrl(apiUrlNamespaceAliases);
		NodeList namespaceAliasess = document.getElementsByTagName("ns");
		MultiValueMap namespaces = new MultiValueMap();

		for (int i = 0; i < namespaceAliasess.getLength(); i++)
		{
			Node aliasNode = namespaceAliasess.item(i);
			NamedNodeMap attributes = aliasNode.getAttributes();

			Integer id = Integer.parseInt(attributes.getNamedItem("id").getNodeValue());
			String aliasString = aliasNode.getTextContent();
			namespaces.put(id, aliasString);
		}
		return namespaces;
	}

	/**
	 * Fetches and parses the XML document at the given URL. Only the http,
	 * https and file schemes are accepted. DOCTYPEs are rejected, so neither
	 * external DTDs nor entities are processed. Requests time out after
	 * {@value #CONNECT_TIMEOUT_MILLIS} ms (connect) and
	 * {@value #READ_TIMEOUT_MILLIS} ms (read), responses of more than
	 * {@value #MAX_RESPONSE_BYTES} bytes are rejected. At most
	 * {@value #MAX_REDIRECTS} HTTP redirects are followed and only to the same
	 * scheme, host and port.
	 *
	 * @throws MalformedURLException
	 *             If the URL is malformed or uses another scheme.
	 * @throws IOException
	 *             Also if a redirect leads to another scheme, host or port or
	 *             if there are too many redirects.
	 */
	public static Document getXMLFromUrl(String urlString)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		return getXMLFromUrl(urlString, CONNECT_TIMEOUT_MILLIS, READ_TIMEOUT_MILLIS, MAX_RESPONSE_BYTES);
	}

	static Document getXMLFromUrl(
			String urlString,
			int connectTimeoutMillis,
			int readTimeoutMillis,
			long maxResponseBytes)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		URL url = new URL(urlString);
		String scheme = url.getProtocol();
		if (!"http".equals(scheme) && !"https".equals(scheme) && !"file".equals(scheme))
		{
			throw new MalformedURLException("Unsupported URL scheme `" + scheme + "' in `" + urlString
					+ "', only http, https and file are allowed.");
		}

		DocumentBuilder docBuilder = newDocumentBuilder();

		for (int redirects = 0;; ++redirects)
		{
			URLConnection connection = url.openConnection();
			connection.setConnectTimeout(connectTimeoutMillis);
			connection.setReadTimeout(readTimeoutMillis);
			connection.setRequestProperty("User-Agent", loadDefaultUserAgent());

			if (connection instanceof HttpURLConnection)
			{
				HttpURLConnection http = (HttpURLConnection) connection;

				// The target of a redirect has to be checked first
				http.setInstanceFollowRedirects(false);
				if (isRedirect(http.getResponseCode()))
				{
					String location = http.getHeaderField("Location");
					http.disconnect();

					if (redirects >= MAX_REDIRECTS)
					{
						throw new IOException("Too many redirects (more than " + MAX_REDIRECTS
								+ ") while fetching `" + urlString + "'.");
					}

					url = getRedirectTarget(url, location);
					continue;
				}
			}

			try (InputStream in = connection.getInputStream())
			{
				if (connection.getContentLengthLong() > maxResponseBytes)
					throw new IOException(getResponseTooLargeMessage(urlString, maxResponseBytes));

				return docBuilder.parse(new SizeLimitedInputStream(in, urlString, maxResponseBytes));
			}
		}
	}

	private static boolean isRedirect(int status)
	{
		switch (status)
		{
			case HttpURLConnection.HTTP_MOVED_PERM:
			case HttpURLConnection.HTTP_MOVED_TEMP:
			case HttpURLConnection.HTTP_SEE_OTHER:
			case 307: // Temporary Redirect
			case 308: // Permanent Redirect
				return true;
			default:
				return false;
		}
	}

	/**
	 * Resolves the target of a redirect.
	 *
	 * @throws IOException
	 *             If the redirect has no valid target or if the target has
	 *             another scheme, host or port than the redirecting URL.
	 */
	private static URL getRedirectTarget(URL url, String location) throws IOException
	{
		if (location == null)
			throw new IOException("Got a redirect without target from `" + url + "'.");

		URL target;
		try
		{
			target = new URL(url, location);
		}
		catch (MalformedURLException e)
		{
			throw new IOException("Got a redirect to the invalid URL `" + location + "' from `" + url + "'.", e);
		}

		if (!url.getProtocol().equalsIgnoreCase(target.getProtocol())
				|| !url.getHost().equalsIgnoreCase(target.getHost())
				|| getPort(url) != getPort(target))
		{
			throw new IOException("Refusing to follow the redirect from `" + url + "' to `" + target
					+ "', only redirects to the same scheme, host and port are followed.");
		}

		return target;
	}

	private static int getPort(URL url)
	{
		return (url.getPort() != -1) ? url.getPort() : url.getDefaultPort();
	}

	/**
	 * Creates a document builder that rejects DOCTYPEs and neither loads
	 * external DTDs nor resolves external entities.
	 */
	private static DocumentBuilder newDocumentBuilder() throws ParserConfigurationException
	{
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setXIncludeAware(false);
		factory.setExpandEntityReferences(false);
		// Not supported by all implementations, e.g. Xerces 2.11
		setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
		setAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		return factory.newDocumentBuilder();
	}

	private static void setAttributeIfSupported(DocumentBuilderFactory factory, String name, Object value)
	{
		try
		{
			factory.setAttribute(name, value);
		}
		catch (IllegalArgumentException e)
		{
			logger.debug("The document builder factory does not support `{}'", name);
		}
	}

	private static String getResponseTooLargeMessage(String urlString, long maxResponseBytes)
	{
		return "The response from `" + urlString + "' exceeds the limit of " + maxResponseBytes + " bytes.";
	}

	/**
	 * Fails as soon as more than the given number of bytes was read.
	 */
	private static final class SizeLimitedInputStream
			extends
				FilterInputStream
	{
		private final String urlString;

		private final long maxBytes;

		private long count;

		public SizeLimitedInputStream(InputStream in, String urlString, long maxBytes)
		{
			super(in);
			this.urlString = urlString;
			this.maxBytes = maxBytes;
		}

		@Override
		public int read() throws IOException
		{
			int b = super.read();
			if (b != -1)
				count(1);
			return b;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException
		{
			int n = super.read(b, off, len);
			if (n > 0)
				count(n);
			return n;
		}

		@Override
		public long skip(long n) throws IOException
		{
			long skipped = super.skip(n);
			if (skipped > 0)
				count(skipped);
			return skipped;
		}

		@Override
		public boolean markSupported()
		{
			return false;
		}

		private void count(long n) throws IOException
		{
			count += n;
			if (count > maxBytes)
				throw new IOException(getResponseTooLargeMessage(urlString, maxBytes));
		}
	}

    private static String loadDefaultUserAgent() {
        final Properties properties = new Properties();
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream("sweble-http.properties")) {
            if (input != null) {
                properties.load(input);
                return properties.getProperty("user.agent", DEFAULT_FALLBACK_USER_AGENT);
            }
        } catch (IOException e) {
            logger.warn(e.getLocalizedMessage(), e);
        }
        return DEFAULT_FALLBACK_USER_AGENT;
    }

}
