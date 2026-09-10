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

package org.sweble.wikitext.engine.ext.parser_functions;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.sweble.wikitext.engine.ExpansionFrame;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.parser.WikitextWarning.WarningSeverity;
import org.sweble.wikitext.parser.nodes.WtNode;
import org.sweble.wikitext.parser.nodes.WtTemplate;
import org.sweble.wikitext.parser.parser.LinkTargetException;
import org.sweble.wikitext.parser.utils.StringConversionException;

/**
 * <pre>
 * {{#ifexist:
 *       page title
 *     | value if exists
 *     | value if doesn't exist
 * }}
 * </pre>
 *
 * Whether a page exists is determined with
 * {@link ExpansionFrame#existsPage(PageTitle)}. Like in MediaWiki, the page is
 * not transcluded: The template depth, the post-expand include size and the
 * detection of template loops do not apply. (MediaWiki counts
 * <code>#ifexist</code> as expensive parser function instead, a limit which is
 * not implemented.)
 *
 * Like MediaWiki's <code>SpecialPageFactory::exists()</code>, the core special
 * pages exist without being looked up: A title in the special namespace names
 * a core special page if the part in front of the first slash is the
 * canonical name of one of the special pages of MediaWiki core
 * (case-insensitive). The localized aliases of the special pages and the
 * special pages of extensions are not known. For all other special pages the
 * callback is asked like for any other page.
 */
public class ParserFunctionIfExist
		extends
			ParserFunctionsExtPfn.IfThenElseStmt
{
	private static final long serialVersionUID = 1L;

	private static final int SPECIAL_NAMESPACE_ID = -1;

	/**
	 * The canonical names of the special pages of MediaWiki core (in lower
	 * case): The keys of <code>SpecialPageFactory::CORE_LIST</code> and the
	 * special pages <code>SpecialPageFactory::getPageList()</code> adds in a
	 * default configuration.
	 */
	private static final Set<String> CORE_SPECIAL_PAGES = toLowerCaseSet(
			// SpecialPageFactory::CORE_LIST
			"BrokenRedirects", "Deadendpages", "DoubleRedirects", "Longpages",
			"Ancientpages", "Lonelypages", "Fewestrevisions", "Withoutinterwiki",
			"Protectedpages", "Protectedtitles", "Shortpages",
			"Uncategorizedcategories", "Uncategorizedimages",
			"Uncategorizedpages", "Uncategorizedtemplates", "Unusedcategories",
			"Unusedimages", "Unusedtemplates", "Unwatchedpages",
			"Wantedcategories", "Wantedfiles", "Wantedpages", "Wantedtemplates",
			"Allpages", "Prefixindex", "Categories", "Listredirects",
			"PagesWithProp", "TrackingCategories", "Userlogin", "Userlogout",
			"CreateAccount", "LinkAccounts", "UnlinkAccounts",
			"ChangeCredentials", "RemoveCredentials",
			"AuthenticationPopupSuccess", "Activeusers", "Block", "Unblock",
			"BlockList", "AutoblockList", "ChangePassword", "BotPasswords",
			"PasswordReset", "DeletedContributions", "Preferences",
			"ResetTokens", "Contributions", "Listgrouprights", "Listgrants",
			"Listusers", "Listadmins", "Listbots", "Userrights", "EditWatchlist",
			"PasswordPolicies", "Newimages", "Log", "Watchlist",
			"WatchlistLabels", "Newpages", "Recentchanges",
			"Recentchangeslinked", "Tags", "Listfiles", "Filepath",
			"MediaStatistics", "MIMEsearch", "FileDuplicateSearch", "Upload",
			"UploadStash", "ListDuplicatedFiles", "ApiSandbox", "Interwiki",
			"Statistics", "Allmessages", "Version", "Lockdb", "Unlockdb",
			"NamespaceInfo", "LinkSearch", "Randompage", "RandomInCategory",
			"Randomredirect", "Randomrootpage", "GoToInterwiki",
			"Mostlinkedcategories", "Mostimages", "Mostinterwikis",
			"Mostlinked", "Mostlinkedtemplates", "Mostcategories",
			"Mostrevisions", "ComparePages", "Export", "Import", "Undelete",
			"Whatlinkshere", "MergeHistory", "ExpandTemplates",
			"ChangeContentModel", "Booksources", "ApiHelp", "Blankpage",
			"DeletePage", "Diff", "EditPage", "EditTags", "Emailuser",
			"Movepage", "Mycontributions", "MyLanguage", "Mylog", "Mypage",
			"Mytalk", "PageHistory", "PageInfo", "ProtectPage", "Purge",
			"Myuploads", "AllMyUploads", "NewSection", "PermanentLink",
			"Redirect", "Renameuser", "Revisiondelete", "RunJobs",
			"Specialpages", "PageData", "Contribute", "TalkPage",
			// Added by SpecialPageFactory::getPageList() by default
			"Search", "Confirmemail", "Invalidateemail", "ChangeEmail", "Mute");

	// =========================================================================

	/**
	 * For un-marshaling only.
	 */
	public ParserFunctionIfExist()
	{
		super("ifexist", 1 /* thenArgIndex */);
	}

	public ParserFunctionIfExist(WikiConfig wikiConfig)
	{
		super(wikiConfig, "ifexist", 1 /* thenArgIndex */);
	}

	@Override
	protected boolean evaluateCondition(
			WtTemplate pfn,
			ExpansionFrame frame,
			List<? extends WtNode> args)
	{
		WtNode test = frame.expand(args.get(0));

		String testStr = null;
		try
		{
			testStr = tu().astToText(test).trim();

			PageTitle pageTitle = PageTitle.make(frame.getWikiConfig(), testStr);

			// Like MediaWiki, core special pages are not looked up.
			if (pageTitle.getNamespace() != null
					&& pageTitle.getNamespace().getId() == SPECIAL_NAMESPACE_ID
					&& isCoreSpecialPage(pageTitle.getTitle()))
				return true;

			return frame.existsPage(pageTitle);
		}
		catch (StringConversionException e1)
		{
			// We have to convert the entire argument to a string to create a page name from it.
			fileInvalidNameWarning(frame, WarningSeverity.NORMAL, test);
			return false;
		}
		catch (LinkTargetException e)
		{
			// A page with an illegal name cannot exist.
			fileInvalidPagenameWarning(frame, WarningSeverity.INFORMATIVE, test, testStr);
			return false;
		}
		catch (Exception e)
		{
			// Interpret an error while testing for existence as non-existence.
			fileIllegalArgumentsWarning(
					frame,
					WarningSeverity.NORMAL,
					pfn,
					"Testing for existence of page `" + testStr + "' failed: " + e);
			return false;
		}
	}

	/**
	 * Returns whether the title of a page in the special namespace names one
	 * of the special pages of MediaWiki core. Like in MediaWiki, a subpage
	 * like "Contributions/Example" names the special page in front of the
	 * slash.
	 */
	static boolean isCoreSpecialPage(String title)
	{
		int slash = title.indexOf('/');
		String name = (slash != -1) ? title.substring(0, slash) : title;
		return CORE_SPECIAL_PAGES.contains(name.trim().replace(' ', '_').toLowerCase(Locale.ROOT));
	}

	private static Set<String> toLowerCaseSet(String... names)
	{
		Set<String> set = new HashSet<String>();
		for (String name : Arrays.asList(names))
			set.add(name.toLowerCase(Locale.ROOT));
		return Collections.unmodifiableSet(set);
	}
}
