<a href="https://github.com/sponsors/gi-os"><img src="https://raw.githubusercontent.com/gi-os/gi-os/main/images/bright-sponsors.svg" width="100%" alt="Thank you to my sponsors"></a>

# BrightLibrary Lite

> **This is the Lite version.** BrightLibrary Lite is built only with Light's official SDK, so it can go in the Light Tool Library. It does less than the full app. For the full app, get [BrightLibrary](https://github.com/gi-os/BrightLibrary) ([on BrightMarket](https://brightmarket.gzl.dev/app/com.lightfastread/)).

An ebook and comic reader for the Light Phone III, built as an official Light SDK tool. It is
the Tool Library version of [BrightLibrary](https://github.com/gi-os/BrightLibrary), which is
itself a fork of **[fluffyspace/FastRead](https://github.com/fluffyspace/FastRead)**: the RSVP
reading engine, both ebook parsers and the whole gesture model are fluffyspace's work (see
[Credits](#credits)). On the phone it shows up as **BrightLibrary Lite**
(`com.gios.brightlibrarylite`).

Built on [lightphone/light-sdk](https://github.com/lightphone/light-sdk) (MIT, see
[LICENSE-light-sdk](LICENSE-light-sdk)). The SDK modules (`sdk/`, `plugin/`, `lint-rules/`) are
unchanged upstream code; the tool lives in `tool/`. The tool code is GPL-3.0, like FastRead and
BrightLibrary (see [LICENSE](LICENSE)).

## Getting books onto the phone

There is no ADD button that opens a file picker. Books come in through Light's **Tool Manager**:

1. On a computer on the same Wi-Fi as the phone, open the Tool Manager.
2. Pick **BrightLibrary Lite**, then the **Books** folder.
3. Upload EPUB, MOBI (also `.azw` / `.azw3`, PalmDOC-compressed) or CBZ files. Subfolders are fine.
4. Open BrightLibrary Lite. New files are imported when the shelf opens. If the tool is already
   running, the Tool Manager's change notification imports them straight away.

The Books folder is the source of truth. Deleting a file there takes the book off the shelf the
next time the folder is checked. Deleting a book on the phone (long-press, DELETE) also deletes its
file from the folder. Replacing a file with a new version under the same name re-imports it and
keeps your place where it still fits. A file that fails to import is remembered by name and size,
so a broken archive is not re-read on every open; upload a different file to try again.

The **+** on the shelf opens a page with these steps and a CHECK FOLDER button.

## What it keeps

All of this is ported from BrightLibrary, mostly line for line:

- **The RSVP word reader and its gesture model**, unchanged from FastRead: hold the right
  two-thirds to read (finger position sets the speed between min and max WPM), tap the left third
  to step back, hold it to rewind, the bottom swipe band, zone swipe, speed ramp-up, sentence and
  paragraph pauses, per-letter slowdown, bionic reading, ORP highlighting, the word fade, the
  three-line context window, chapter list, bookmarks, jump history, zone guides and the in-reader
  quick settings.
- **The page view.** A text book opens to its pages: measured pagination (nothing is clipped),
  tap for the bar with FASTREAD on it, the wheel or a swipe to turn. FASTREAD opens the word
  reader at the first word of the page; going back returns to the pages at the word you reached.
- **The parsers.** `EpubParser` (EPUB 2 and 3, OPF spine, NCX and nav TOC, cover image),
  `MobiParser` (PalmDOC) and `HtmlStripper` are the full app's code with only the debug logging
  removed. They never used Android.
- **Comics and manga.** CBZ files and image-only EPUBs (detected by looking at them) are converted
  on import to greyscale JPEG pages sized for the 1080 px panel, using Android's own
  `BitmapFactory`, `Bitmap` and `Canvas`. The comic reader is the full app's: fit to width with
  stepped scrolling, fit page, pinch zoom, white-border cropping, right-to-left per book, tap
  thirds to turn, and 4-koma mode remembered per series (turned on by the import when the pages
  show a centre gutter).
- **The cover shelf with series stacking.** Two covers to a row with a progress hairline under
  each. Volumes whose titles say `Vol. 3`, `#3`, `Book 3` or `v03` stack into one cell that opens
  into a shelf of their own, in reading order. Covers come from the file; a book without one gets
  the typographic cover. Long-press a book for FIX NAME (on the LP3 keyboard), READ LEFT TO
  RIGHT / RIGHT TO LEFT for comics, and DELETE.
- **Reading position memory.** Every settled page, every comic step and the RSVP position (debounced,
  and again when the tool leaves the screen) is written to the shelf file.
- **The wheel**, with the full app's keycodes: LightOS's `WHEEL_CCW` / `WHEEL_CW` labels resolved
  at runtime, falling back to raw scancodes 19 and 20 from the `Pixart pat9126ja` sensor only. It
  scrolls the shelf, series, settings and sheets with the same smoothing and stray-notch guard, and
  turns pages (four notches) and comic steps (two notches).
- **Volume keys turn pages**, off by default, as in the full app.
- All of the full app's reading settings, and its fonts (Atkinson Hyperlegible, Inter, Lora,
  Bitter, Merriweather, JetBrains Mono, all SIL OFL).

## What it drops, and why

| BrightLibrary feature | Why it is not in the Lite tool |
| --- | --- |
| ADD through the Android file picker (Storage Access Framework) and importing a `content://` Uri | A Light tool cannot start activities (`startActivity` and `androidx.activity.*` are blocked) and cannot use `contentResolver`. The Tool Manager's Books folder replaces it. |
| Calibre library browsing over OPDS, downloads, and reading-progress sync to calibre-web | The full app's Calibre client is built on `java.net.HttpURLConnection` and `javax.xml` DOM parsing, not on the SDK's allowed HTTP stacks (ktor / OkHttp). Rather than rewrite and re-test a network client for the first Lite release, it is left out, and the tool asks for no network access at all. |
| Setup by QR code for the Calibre server | Used CameraX and ML Kit, neither of which is allowed. It also only existed for Calibre. |
| Cover lookups on Open Library, FIND COVER | Network, same reason as Calibre. Covers come from the book file or are drawn as typographic covers. |
| Colour covers (lifting LightOS's greyscale while the shelf is up) | Needs `WRITE_SECURE_SETTINGS` and writes a secure setting. Not available to a tool. |
| Backup to LightSync through light-common | `com.gios:light-common` is not on the SDK's dependency allowlist. |
| Shake-to-report crash reports | Also light-common, and it needs a network token. |
| System / Light / Dark theme choice | The tool always draws the full app's default Light Phone theme (pure black, greyscale). The reading screens were tuned for it. |
| The reading-sessions content provider other apps could query | A tool may not declare a `ContentProvider`. |
| Dialog windows for the reader's sheets and the page view | Not a feature, but worth knowing: a Dialog is its own window and a tool only receives keys through its screen, so the wheel would go dead in it. The sheets are drawn in the reader's window instead, and the page view and word reader are separate SDK screens. The back gesture leaves the current screen. |

## Network and data

The tool requests **no permissions** and makes **no network requests**. It talks to no hosts.
Its one capability is `tool-manager-provider`, which lets LightOS's own Tool Manager (served by
the phone, on your local network) list the Books folder.

Everything is stored in the tool's private files directory:

- `files/shared/Books/` — the files you uploaded (what the Tool Manager shows)
- `files/books/` — extracted text, one file per book
- `files/comics/<id>/` — converted greyscale comic pages
- `files/covers/` — downscaled covers
- `files/library.json`, `files/settings.json`, `files/import-failures.json`

Uninstalling the tool removes all of it.

## Setup

Nothing to configure. Install the tool, then add books as described above. Reading settings are
under the gear on the shelf.

## Build

```
./gradlew -DlightSdk.toolOnly=true :tool:assembleDebug
```

CI (`.github/workflows/build-tool.yml`) runs the same flags as Light's tool builder:
`:tool:testDebugUnitTest :tool:lintDebug :tool:assembleDebug`. The unit tests cover the EPUB
parser, series parsing and shelf stacking, measured pagination, comic page ordering and the
image-EPUB detection, page cropping and sizing, and the volume-key mapping. They are the full
app's tests, moved from JUnit to `kotlin.test`, plus a new EPUB parser test.

The debug build is signed with the SDK's development key. Release builds are signed with the
committed `tool/keystore/bright-lite.jks` (the same key as the other Bright Lite tools) so a
sideloaded release installs over the previous one. Light's Tool Library signs its own builds.

## Tool Library submission notes

- Kotlin only, no manifest of our own; the manifest is generated from `tool/lighttool.toml`.
- Dependencies: only `:sdk:client` (and the Room KSP line from the scaffold, unused).
- No blocked imports or patterns. The screens never touch a `Context`: the SDK's `filesDir` is
  handed to the data layer once, when the first screen is created.
- Hardware keys arrive through each screen's view model (`hw/KeyedViewModel.kt`). Wheel notches
  are always consumed; volume keys only while a reader has claimed them (setting on, book open).
- `onToolManagerDataUpdate()` imports new files if the tool has been opened since the phone
  started. It cannot do it before that, because the SDK gives the files directory to screens, not
  to the entry point. The shelf checks the folder every time it is shown, so nothing is missed.
- No infinite feeds and no auto-refresh. The only background work is importing files the user
  uploaded.
- Not verified on hardware yet: the Tool Manager upload flow end to end, the wheel keycodes
  under the SDK's key routing, and import times for large comics.

## Credits

The reading engine, both parsers and the whole gesture design belong to
**[fluffyspace](https://github.com/fluffyspace)**, in
[fluffyspace/FastRead](https://github.com/fluffyspace/FastRead). This fork contributes a
display treatment for one piece of hardware and nothing else. Thank you.

[queueingqt/FastRead](https://github.com/queueingqt/FastRead) is a fork of that original,
not the source. An earlier version of this file credited it by mistake.

RSVP as a reading technique has been around for decades; FastRead's contribution is a tight, gesture-first Android implementation that respects your privacy and your library.

BrightLibrary Lite is a port of [gi-os/BrightLibrary](https://github.com/gi-os/BrightLibrary)
to the Light SDK. The design tokens under `tool/.../ui/light/` were ported from
`lightphone/light-sdk` by the full app and sit on top of the real SDK here.

## License

GPL-3.0, as FastRead and BrightLibrary are. See [LICENSE](LICENSE). The Light SDK modules in this
repository are MIT; see [LICENSE-light-sdk](LICENSE-light-sdk).
