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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.collections4.map.MultiValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sweble.wikitext.engine.config.I18nAliasImpl;
import org.sweble.wikitext.engine.config.InterwikiImpl;
import org.sweble.wikitext.engine.config.NamespaceImpl;
import org.sweble.wikitext.engine.config.TagExtensionGroup;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.config.WikiConfigImpl;
import org.sweble.wikitext.engine.ext.generic.GenericTagExtension;
import org.w3c.dom.Document;
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

	public static final String API_ENDPOINT_EXTENSIONTAGS =
			".wikipedia.org/w/api.php?action=query&meta=siteinfo&siprop=extensiontags&format=xml";

	/**
	 * Name of the group holding the extension tags reported by the wiki for
	 * which no implementation is available.
	 */
	public static final String SITE_TAG_EXTENSION_GROUP = "Extension - Site (generic)";

	/** Extension tags are reported as {@code <name>}. */
	private static final Pattern EXTENSION_TAG = Pattern.compile("^\\s*<?\\s*([^<>\\s]+)\\s*>?\\s*$");

    private static final String DEFAULT_FALLBACK_USER_AGENT =
            "Sweble Wikitext/unknown (+https://github.com/rzo1/sweble-wikitext/";


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

	public static WikiConfig generateWikiConfig(
			String siteName,
			String siteURL,
			String languagePrefix)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		String endpointPrefix = "https://" + languagePrefix;
		return generateWikiConfig(
				siteName,
				siteURL,
				languagePrefix,
				endpointPrefix + API_ENDPOINT_NAMESPACEALIASES,
				endpointPrefix + API_ENDPOINT_NAMESPACES,
				endpointPrefix + API_ENDPOINT_INTERWIKIMAP,
				endpointPrefix + API_ENDPOINT_MAGICWORDS,
				endpointPrefix + API_ENDPOINT_EXTENSIONTAGS);
	}

	/**
	 * Does not query the extension tags of the wiki but registers the
	 * extension tags of {@link DefaultConfigEnWp} instead.
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
				null);
	}

	/**
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

		MultiValueMap namespaceAliases = getNamespaceAliases(apiUrlNamespacealiases);
		addNamespaces(wikiConfig, apiUrlNamespaces, namespaceAliases);
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
			Node aliasesNode = apiI18nAlias.getFirstChild();
			NodeList aliasesList = aliasesNode.getChildNodes();
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

			NamespaceImpl namespace = new NamespaceImpl(
					id.intValue(),
					name,
					canonical,
					canHaveSubpages,
					fileNs,
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

	public static Document getXMLFromUrl(String urlString)
		throws IOException,
			ParserConfigurationException,
			SAXException
	{
		URL url = new URL(urlString);
		URLConnection connection = url.openConnection();
        connection.setRequestProperty("User-Agent", loadDefaultUserAgent());
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = documentBuilderFactory.newDocumentBuilder();
		Document document = docBuilder.parse(connection.getInputStream());
		return document;
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
