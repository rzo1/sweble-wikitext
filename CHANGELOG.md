# Change Log
[A guide to writing change logs][keepachangelog]

## 4.1.1 - unreleased

## 4.1.0 - 2026-09-10
Numbers refer to issues and pull requests in [rzo1/sweble-wikitext](https://github.com/rzo1/sweble-wikitext).

### Changed
- **Licensing:** the unit data of `{{convert}}` is derived from Wikipedia's
  Module:Convert/data and licensed under CC BY-SA 4.0. It ships in the new
  artifact `swc-convert-data` with its own NOTICE file, not in the Apache
  licensed jars. `swc-engine` depends on it only as an optional dependency:
  add it to use `{{convert}}`, which otherwise reports an error. The NOTICE is
  repeated in the NOTICE file of the repository root (#178, #179).
- Version 4.1.0; internal modules resolve from the build (#81). The build works
  on Java 11 to 25 (#83) and without a `.git` directory (#76).
- Transclusions follow up to two redirects (`maxRedirects`, #150, #172).
- `EngProcessedPage` stores its processing log as a child; serialized data with
  a `log` property may not load (#172).
- Language conversion (`-{…}-`) is only active when the wiki has variants (#174).
- Parse and render output follow MediaWiki more closely: paragraphs after
  framed images (#74), indented tables in definition lists (#75, #173), `;a:b`
  (#124), unclosed extension tags stay text (#154, #173), HtmlRenderer output
  (#121, #123) and the pretty printer (#116).
- `PageTitle` decodes percent-encoded UTF-8 like `rawurldecode`, so `+` stays
  a plus (#72).
- Namespaces with the same id but different settings, and parser functions of
  the same class with different ids, are no longer equal (#149).
- Machine-facing case conversion and formatting use `Locale.ROOT`; the build
  fails on default locale or charset APIs in main code (#177).
- Dependencies with known vulnerabilities are updated: commons-compress 1.28.0,
  xercesImpl 2.12.2, xalan 2.7.3, commons-jxpath 1.4.0, commons-math3 3.6.1,
  Saxon-HE 12.10 (#147). Further updates by Dependabot.

### Added
- swc-dumpreader reads the MediaWiki XML dump format 0.11 (#63).
- `{{convert}}` follows Module:Convert: all unit families of Module:Convert/data,
  SI prefixes, per units, engineering notation, and the options `disp=`, `adj=`,
  `spell`, `frac`, `comma=`, `order=out`, `sortable=`, `error`, `stylein`,
  `styleout` and `$` (#178).
- Core parser functions and magic words: `formatnum`, `padright`,
  `anchorencode`, `plural`, `grammar`, `int:`, `localurl(e)`, `fullurle`,
  `canonicalurl(e)`, `nse`, `DISPLAYTITLE`, `msgnw:`, `SERVER`, `SERVERNAME`,
  `SCRIPTPATH`, `{{=}}` (#128) and missing date and time variables (#151).
- Behaviour switches like `__NOTOC__`, including localized names (#113).
- Localized image link options via `ParserConfig.getImageLinkOptionId` (#73).
- Limits like MediaWiki's: `maxTemplateDepth` (40), `maxPostExpandIncludeSize`
  (2 MiB) and `maxRedirects` in `EngineConfig` (#150), and a parser nesting depth
  limit, `ParserConfig.getMaxNestingDepth()` (100) (#154, #173).
- Parser functions file warnings instead of silently ignoring errors (#78).
- HtmlRenderer: MediaWiki heading ids and fragment links (#122); the image
  options `upright=`, `class=`, `lang=` and `page=` (#174).
- LanguageConfigGenerator takes the general siteinfo, the extension tags and
  the language variants of the wiki into account (#109, #110, #176).
- Getting started section in the README (#79).

### Removed
- The `Units` and `DefCvt` enums of `{{convert}}` (#178).
- The generated class `encval.EncodingValidatorLexer` (#153).
- The default-value constructors and `setDefault` of `IfThenElseStmt` (#151).
- The protected method `HtmlRenderer.cleanAttribs` (#123).
- The unused `TreeBuilder.getAboveOnStack` (#173).

### Security
- HtmlRenderer sanitizes HTML and escapes attribute values (#107).
- XML parsing and deserialization are hardened against XXE, SSRF and unsafe
  classes; siteinfo or WOM XML with a DOCTYPE is rejected (#148).
- Limits against exponential templates and deep nesting (#150, #154).

### Fixed
- Parser: crashes on character references and data loss in the encoding
  validation (#153), linear parse time (#154), tree builder crashes and lost
  text (#120, #156), image options, free URLs and redirect targets (#155),
  title normalization (#173), case-sensitive namespaces (#119),
  case-insensitive interwiki prefixes (#112), a missing link prefix pattern
  (#118).
- Engine: parser function and expansion semantics (#111), `#expr` (#114), page
  name magic words (#115), `#time` (#127), `#iferror`, `#ifexpr`, `#rel2abs` and
  namespace functions (#151), `#ifexist` (#172), `{{convert}}` (#152), config
  alias matching, equality and serialization (#149), `DefaultConfig.generate()`
  (#125), redirect loops (#150).
- HtmlRenderer crashes (#108, #157) and extension tags (#109).
- Round-trip data pretty printers (#126).
- WOM3: categories, DOM contract, JSON adapter errors, round trips and markup
  for new content (#161, #162, #175).
- Dump reader: version detection, gzip, log items, counts and file size (#158,
  #171). The article cruncher stops on errors instead of hanging and shuts
  down in order (#159, #171). `CompressionFormat.XZ` works (#171).
- Examples: TextConverter, XPath and the DumpCruncher startup (#160, #171).
- LanguageConfigGenerator resolves i18n alias conflicts deterministically (#77).

## 3.1.10 - unreleased
### Changed

### Added

### Fixed
- 

## 3.1.9 - 2018-09-26
### Fixed
- Parser extension groups are not added if required aliases are missing in LanguageConfigGenerator

## 3.1.8 - 2018-09-11
### Changed
- Transclusion statements without ':' and having arguments can also be parser function calls.
- Ignoring repeated registration of alias names (not IDs).

### Added
- Implemented number parser functions.

### Fixed
- Fixed missing EngNowiki visit method in AstToWomConverter

## 3.1.7 - 2017-12-11
### Changed
- Fixed missing RTD information on tbody element and added it to list of elements that cancel a semipre block
- *StringConverter classes in AstTextUtilsImpl and EngineAstTextUtilsImpl are now public

### Added
- New formatting option for non standard elements: LIKE_FORMATTING
- New option preserveSemiPreLeadingSpace which allows to recognize and remove leading spaces in semi pre blocks

### Fixed
- Fixed wrong default enwp configuration: protocol mail -> mailto

## 3.1.6 - 2017-09-14
### Changed
- Added jenkins profile which generates coverage reports when build in Jenkins.
- Bumped version of tooling parent pom and osr-common dependencies to 3.0.7-SNAPSHOT.
- HtmlRenderer.visit(EngProcessedPage n) properly implemented.
- HtmlRenderer.visit(WtRedirect n) properly implemented.
- Ignoring IntelliJ project files and directories.
- Bumped osr-common version number to 3.0.8 to fix problem with signed artifacts.

## 3.1.5 - 2017-06-14
### Changed
- Pre-processing stage now replaced entities.
- Refactored internal link parsing:
  - Deleted INTERNAL_LINK_ALT scope
  - Allowed EXTERNAL_LINK in IMAGE_LINK_ALT scope.
  - Allowed EXTERNAL_LINK and PLAIN_EXTERNAL_LINK in IMAGE_LINK_TITLE scope.
  - Removed what I believe to be unnecessary complexity from internal link 
    grammar. 
- LanguageConfigGenerate automatically prefixes a magic word with "#" or adds  
  the postfix ":" if the same magic word is prefixed or postfixed in 
  DefaultConfigEnWp.

### Added
- Added containsIllegalCodePoints() to class ValidatedWikitext.
- Added convertIllegalCodePoints option to ParserConfig which affect encoding 
  validation stage.

### Removed
- Removed xml-apis dependencies.

### Fixed
- Fixed '|' parsing in external link URLs.
- Framed image links force all block elements inside their title to close when 
  the title scope closes.
- Added missing descriptions to pom.xml files. The release to oss.sonatype.org 
  was failing with a validation error that complained about the missing 
  descriptions.

## 3.1.4 - 2017-02-09
### Added
- Added missing setter methods ing ParserConfigImpl.

### Changed
- ParserConfigImpl now has proper fields for nonStandardElementBehavior, 
  fosterParenting, and fosterParentingForTransclusions and loads/saves those
  fields from the XML configuration.
- Intermediate paragraph tags are no longer subject to foster parenting during
  post-processing.
- Improved parser grammer to improve performance.
- Updated to osr-common 3.0.5.

### Fixed
- Fixed bug in pre-processor which resulted in exception when encountering wiki
  markup similar to this: "<ref></ref><</ref>".
- Improved parser grammar to avoid case where illegally nested internal links
  caused a recursive cascade that practically never finished.

## 3.1.3 - 2017-02-06
### Changed
- More fine grained engine integration test helper functions
- Implemented switch langConvTagsEnabled to help with issue #48 Too aggressive
  parsing of `-{ }-` Language Converter tags
- Added switch tagExtensionNamesCaseSensitive to help with issue #43: pre tag
  support is case sensitive
- Automatically expanding `{{!}}` to `|` thus fixing issue #47

### Fixed
- Changed order of processing in LinkTargetParser to fix issue #45 and perform
  link title sanity check with underscores replaced by spaces.
  The title `Template:Did you know nominations/Steve Taylor & The Perfect Foil; Wow to the Deadness'
  contains invalid entities: &_The_Perfect_Foil;
- Fixed InternalError during postprocessing when encountering a <PRE> tag all
  caps by treating it as startTagR14.
- Replaced throw new InternalError by AssertionError to fix bug
  #35 Internal Error
- Ignoring virtual xml tags in WtPrettyPrinter (Caused problems in issue #44)
- Differentiating between italic/bold started by html tag or ticks thus partly
  fixing issue #44

## 3.1.2 - 2017-01-16
### Changed
- Bumped version of tooling parent pom and osr-common dependencies to 3.0.4

## 3.1.1 - 2016-08-12
### Changed
- Made ScopeStack class and methods public (including inner class Scope)

## 3.1.0 - 2016-06-13
### Changed
- Document automatically generated during deserialization in 
  sweble-wom3-json-tools if not doc is explicitly set does not contain article 
  element by default any more.
- Generalized sweble-wom3-json-tools code to work with w3c docs as well (BREAKS INTERFACE)
- Generalized sweble-engine-serialization code to work with w3c docs as well (BREAKS INTERFACE)
- Generalized Wom3Toolbox code to work with w3c docs as well (BREAKS INTERFACE)

### Added
- WomToolbox.{isWomElement, isRtd, isText, isRtdOrText} methods
- WomSerializer.setDocumentImplClassName method

## 3.0.2 - 2016-06-07
### Changed
- Bumped version of tooling parent pom and osr-common dependencies to 3.0.3

## 3.0.1 - 2016-05-03
### Fixed
- Illegal characters had no rtd assigned

### Added
- Added parser configuration options to turn off foster parenting in 
  post-processing

### Changed
- Bumped version of tooling parent pom and osr-common dependencies to 3.0.2

[keepachangelog]: http://keepachangelog.com/
