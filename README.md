# sweble-wikitext

The Sweble Wikitext Components module provides a parser for MediaWiki's wikitext and an engine trying to emulate the behavior of a MediaWiki.

## How to use

This library is published on Maven Central. You can use it by adding the following Maven coordinates to your project:

```
<dependency>
  <groupId>io.github.rzo1.org.sweble.wikitext</groupId>
  <artifactId>swc-parser-lazy</artifactId>
  <version>4.1.0-SNAPSHOT</version>
</dependency>
```
This module exposes the wikitext parser itself, [other Maven modules are available as well](https://search.maven.org/search?q=org.sweble.wikitext).

## Getting started

All modules share the group id `io.github.rzo1.org.sweble.wikitext`. The latest release is `4.0.1`
(see the `v4.0.1` tag); development versions carry a `-SNAPSHOT` suffix. Pick the module you need:

| Artifact | Purpose |
|---|---|
| `swc-parser-lazy` | Wikitext parser producing an AST (`WtNode`) |
| `swc-engine` | MediaWiki emulation: expansion, post-processing, HTML rendering (pulls in `swc-parser-lazy`) |
| `swc-dumpreader` | Streaming reader for MediaWiki XML dumps (plain, `.gz` or `.bz2`) |

```xml
<dependency>
  <groupId>io.github.rzo1.org.sweble.wikitext</groupId>
  <artifactId>swc-engine</artifactId>
  <version>4.0.1</version>
</dependency>
```

The snippets below are exercised by `GettingStartedTest` in `swc-engine` and `swc-dumpreader`; see also
the example modules `swc-example-basic` (HTML / plain text conversion) and `swc-example-dumpcruncher`.

### 1. Parse wikitext into an AST

```java
import org.sweble.wikitext.engine.PageId;
import org.sweble.wikitext.engine.PageTitle;
import org.sweble.wikitext.engine.WtEngineImpl;
import org.sweble.wikitext.engine.config.WikiConfig;
import org.sweble.wikitext.engine.nodes.EngProcessedPage;
import org.sweble.wikitext.engine.utils.DefaultConfigEnWp;

// A configuration resembling the English Wikipedia
WikiConfig config = DefaultConfigEnWp.generate();
WtEngineImpl engine = new WtEngineImpl(config);

PageTitle pageTitle = PageTitle.make(config, "Example");
PageId pageId = new PageId(pageTitle, -1 /* revision */);

// parse() only parses; postprocess() also expands and post-processes the page.
// The last argument is an optional ExpansionCallback used to resolve templates.
EngProcessedPage cp = engine.postprocess(pageId, "'''Hello''' [[World]]!", null);

// cp.getPage() is the root of the AST
System.out.println(cp.getPage());
```

### 2. Render the AST to HTML

`HtmlRenderer` needs an `HtmlRendererCallback` that tells it how to build URLs and whether link targets or
media exist:

```java
import org.sweble.wikitext.engine.output.HtmlRenderer;
import org.sweble.wikitext.engine.output.HtmlRendererCallback;
import org.sweble.wikitext.engine.output.MediaInfo;
import org.sweble.wikitext.engine.utils.UrlEncoding;
import org.sweble.wikitext.parser.nodes.WtUrl;

HtmlRendererCallback callback = new HtmlRendererCallback()
{
	@Override
	public boolean resourceExists(PageTitle target)
	{
		return false; // links to unknown pages are rendered as red links
	}

	@Override
	public MediaInfo getMediaInfo(String title, int width, int height)
	{
		return null; // no media information available
	}

	@Override
	public String makeUrl(PageTitle target)
	{
		return "/wiki/" + UrlEncoding.WIKI.encode(target.getNormalizedFullTitle());
	}

	@Override
	public String makeUrl(WtUrl target)
	{
		return target.getProtocol().isEmpty() ? target.getPath() : target.getProtocol() + ":" + target.getPath();
	}

	@Override
	public String makeUrlMissingTarget(String path)
	{
		return "/w/index.php?title=" + path + "&amp;action=edit&amp;redlink=1";
	}
};

String html = HtmlRenderer.print(callback, config, pageTitle, cp.getPage());
```

### 3. Read a MediaWiki XML dump

`swc-dumpreader` streams a dump and hands every `<page>` to `processPage`. Pages arrive as
JAXB objects of the dump's schema version; `DumpConverter` turns them into the version-independent
`Page` / `Revision` model. Supported export formats are **0.5 to 0.11**
(`http://www.mediawiki.org/xml/export-0.X/`); other formats are rejected with an
`IllegalArgumentException("Unknown xmlns")`. The file name passed as `url` decides decompression:
names ending in `.bz2` or `.gz` are decompressed on the fly.

```java
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.LoggerFactory;
import org.sweble.wikitext.dumpreader.DumpReader;
import org.sweble.wikitext.dumpreader.model.DumpConverter;
import org.sweble.wikitext.dumpreader.model.Page;
import org.sweble.wikitext.dumpreader.model.Revision;
import org.sweble.wikitext.dumpreader.model.UnsupportedDumpFormat;

File dumpFile = new File("enwiki-latest-pages-articles.xml.bz2");
final DumpConverter converter = new DumpConverter();

try (InputStream in = new FileInputStream(dumpFile);
		DumpReader reader = new DumpReader(
				in,
				StandardCharsets.UTF_8,
				dumpFile.getAbsolutePath(),
				LoggerFactory.getLogger("dump"),
				true /* validate against the export schema */)
		{
			@Override
			protected void processPage(Object mediaWiki, Object page)
			{
				try
				{
					Page p = converter.convertPage(page);
					for (Revision r : p.getRevisions())
						System.out.println(p.getTitle() + ": " + r.getText().length() + " chars");
				}
				catch (UnsupportedDumpFormat e)
				{
					throw new IllegalStateException(e);
				}
			}
		})
{
	reader.unmarshal();
}
```

The wikitext of a revision (`r.getText()`) can then be fed into `WtEngineImpl` as shown above.

## How to set up the library for development

You first need to clone the [sweble/osr-common](https://github.com/sweble/osr-common) repository, and then clone this repository inside it, as the `tooling/sweble-wikitext` local directory.
You can then work on this library as a Maven project.

