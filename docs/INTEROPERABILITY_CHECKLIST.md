# Scribe Native — Publication Interoperability & Conformance Checklist

## Executive Summary
This document defines the strict, honest manual and automated interoperability validation matrix for all six publication formats emitted by **Scribe Native** (`DOCX`, `EPUB`, `PDF`, `HTML5`, `Markdown Bundle / MD`, and `Plain Text / TXT`).

Each format has been engineered to comply with international standards (ECMA-376, W3C EPUB 3.3, XHTML5, PDF/UA vector print standards, CommonMark/GFM, and UTF-8 RFC 3629). This guide details specific external readers and test procedures to verify visual fidelity, bidirectional (RTL) flow, accessibility, and graceful asset degradation.

---

## 1. Microsoft Word & OpenXML (DOCX)
**Target Standard:** ECMA-376 4th Edition / ISO/IEC 29500:2012 WordprocessingML.

### Target Applications
- **Microsoft Word (Desktop — Windows 11 / macOS):** Gold-standard WordprocessingML parser.
- **Microsoft Word Online (Office 365 Web App):** Cloud reflow and rendering engine.
- **Google Docs (`drive.google.com` import):** Evaluates OpenXML compliance against Google's converter.
- **LibreOffice Writer (Linux / macOS / Windows):** Open-source standard reference implementation.

### Conformance Verification Items
| Checkpoint | Target Element / Behavior | Pass Criteria |
| :--- | :--- | :--- |
| **Bidirectional Paragraphs** | `<w:pPr><w:bidi/></w:pPr>` and `<w:rPr><w:rtl/></w:rPr>` | RTL paragraphs align right and flow text from right to left; punctuation (e.g. `۔`, `!`, `؟`) stays at the logical end; LTR paragraphs in mixed manuscripts remain aligned left without `<w:bidi/>`. |
| **External Hyperlinks** | `<w:hyperlink r:id="...">` with `word/_rels/document.xml.rels` | Links are clickable, styled in primary blue with underline, and resolve correctly without triggering corruption prompts. |
| **Multi-Level Lists** | `word/numbering.xml` (abstractNum 1 & 2, ilvl 0..8) | Unordered lists rotate glyphs (`•`, `◦`, `▪`); ordered lists cycle number formats (`1.`, `a.`, `i.`); 2-space and 4-space tab stops reflect accurate hierarchical indents. |
| **Paragraph Reflow** | Soft breaks vs `<w:r><w:br/></w:r>` | Soft line breaks within markdown paragraphs reflow naturally as continuous text with spaces; hard breaks (two spaces or backslash) break cleanly. |
| **Tables & Grids** | `<w:tblGrid>`, `<w:gridCol>`, `<w:tcW>` | Tables span the full printable width; column widths calculate proportionally; alignment (`left`, `center`, `right`) maps accurately to `<w:jc>`. |
| **Missing Image Callouts** | Editorial callout paragraph (`<w:pBdr>` + `<w:shd>`) | Missing vault assets do not write invalid `<Relationship>` tags to missing files; instead, a shaded editorial callout box displays `[Illustration: Caption]` centered. |
| **Styles & Typography** | `word/styles.xml` | Title, Heading 1, Heading 2, Heading 3, and Normal styles resolve cleanly using the user's selected typography preset (`Classic Serif`, `Modern Sans`, `Monospace`). |

---

## 2. EPUB 3.3 Publication Engine
**Target Standard:** W3C EPUB 3.3 Recommendation & IDPF Conformance.

### Target Applications
- **Calibre E-book Viewer & Editor (Desktop):** Tests strict EPUBCheck compliance and table of contents tree.
- **Apple Books (iOS 17+ / macOS Sonoma+):** Validates typography, pagination, dark/sepia themes, and RTL navigation.
- **Google Play Books (Android App & Web Reader):** Ingests and renders EPUB package uploads without manifest rejections.
- **Thorium Reader:** Modern accessible W3C reference e-reader.

### Conformance Verification Items
| Checkpoint | Target Element / Behavior | Pass Criteria |
| :--- | :--- | :--- |
| **Byte Offset 0 Mimetype** | `mimetype` entry in ZIP archive | Uncompressed `application/epub+zip` located at exact byte offset 0, compression method `STORED`, zero extra field bytes. |
| **EPUB 3 Navigation Document** | `OEBPS/nav.xhtml` | Contains `<nav epub:type="toc">` ordered list of all chapters and `<nav epub:type="landmarks" hidden="hidden">` with `toc` and `bodymatter` links. |
| **Legacy NCX Fallback** | `OEBPS/toc.ncx` | `<navMap>` contains sequential `<navPoint>` nodes matching all chapters for backwards compatibility with legacy devices (Kindle Paperwhite, older Nooks). |
| **Chapter Splitting & Front Matter** | Zero data loss chapter division | Split occurs at H1 or H2 headings; any introductory prose or cover material before Chapter 1 is preserved in `OEBPS/frontmatter.xhtml`. |
| **Bidirectional Styling** | `dir="rtl"` and `xml:lang` on `<html>` & elements | RTL chapters declare `dir="rtl"` on root `<html>`; mixed chapters preserve LTR sections with `dir="ltr"` so English paragraphs do not invert punctuation. |
| **Strict XHTML5 Syntax** | Valid XML void elements | Void elements (`<br/>`, `<hr/>`, `<img .../>`) are self-closing; character entities are valid XML entities. |
| **Missing Asset Safety** | Callout box fallback | Missing images do not register broken `<item>` entries in `content.opf` manifest; chapter HTML embeds styled `<figure class="editorial-callout">`. |

---

## 3. Print-Ready PDF Engine
**Target Standard:** PDF 1.7 / ISO 32000-1 via Android `PrintedPdfDocument` Vector Canvas.

### Target Applications
- **Adobe Acrobat Reader (Desktop / Android / iOS):** Official PDF standard validator.
- **Google Drive PDF Viewer:** High-volume cloud and mobile document viewer.
- **Chrome / Firefox Embedded PDF Viewers:** Web browser desktop and mobile engines.

### Conformance Verification Items
| Checkpoint | Target Element / Behavior | Pass Criteria |
| :--- | :--- | :--- |
| **Vector Sharpness** | 300 DPI text vector paths | Text renders crisp at all zoom levels (100% to 800%) without rasterization artifacts or fuzzy edges. |
| **Paper Sizes & Margins** | A4 (210×297mm) vs NA Letter (8.5×11in) | Margins respect presets (`Standard` 20mm/25mm, `Editorial` 25mm/32mm, `Compact` 12mm/15mm); printable content width matches paper dimensions. |
| **RTL Text Shaping & Alignment** | `TextDirectionHeuristics.RTL` & StaticLayout | Urdu and Arabic paragraphs align to the right; complex script glyphs connect into proper ligatures; sentences ending in periods `۔` or question marks `؟` do not flip punctuation to the wrong side. |
| **Running Headers & Footers** | Automatic pagination | Centered running footers (`- 1 -`, `- 2 -`) appear on every page within the safe printable bottom margin without overlapping body text. |
| **Blockquotes & Accents** | Themed border bars | Left bar for LTR blockquotes (`#1A5276`); right bar for RTL blockquotes with indented text. |
| **Table Pagination & Grids** | Row boundary handling | Table header row is highlighted; cell boundaries are delimited with `#CCCCCC` borders; rows that exceed page height break cleanly across pages. |

---

## 4. Standalone HTML5 Document
**Target Standard:** W3C HTML5 Recommendation & CSS Paged Media Module Level 3.

### Target Applications
- **Google Chrome (Desktop / Android):** Standard Blink layout engine.
- **Mozilla Firefox (Desktop / Android):** Gecko layout engine with strict standards adherence.
- **Apple Safari (macOS / iOS):** WebKit layout engine.

### Conformance Verification Items
| Checkpoint | Target Element / Behavior | Pass Criteria |
| :--- | :--- | :--- |
| **Responsive Viewport** | `<meta name="viewport" content="width=device-width, initial-scale=1.0"/>` | Reading layout scales comfortably on screens from 360px mobile width up to 4K ultra-wide desktop monitors. |
| **Embedded Base64 Media** | `data:image/[mime];base64,...` | Embedded images render completely offline without external network requests; MIME type (`image/png`, `image/jpeg`, `image/webp`, `image/svg+xml`) matches asset bytes. |
| **Print CSS Rules** | `@media print` directives | Includes `@page { margin: 20mm 20mm; }`, `page-break-after: avoid` on headings, `page-break-inside: avoid` on tables and figures, and neutral print ink colors. |
| **Mixed Directionality** | `dir="rtl"` and `dir="ltr"` | Urdu/Arabic paragraphs declare `dir="rtl"`; English sections in an RTL document receive `dir="ltr"`, guaranteeing zero punctuation inversion. |
| **Missing Image Degradation** | `<figure class="editorial-callout">` | Shows italicized `[Illustration: Caption]` box with `#777777` typography and clean background without broken image icons. |

---

## 5. Markdown Asset Bundle (.zip) vs Clean Markdown (.md)
**Target Standard:** CommonMark Spec 0.30 & GitHub Flavored Markdown (GFM).

### Target Applications
- **Obsidian (Desktop & Mobile):** Vault-based Markdown reader and PKM tool.
- **Typora / MarkText:** WYSIWYG Markdown editors.
- **VS Code / GitHub Web Preview:** Developer Markdown parsers.

### Conformance Verification Items
| Checkpoint | Condition | Pass Criteria |
| :--- | :--- | :--- |
| **Dynamic Package Detection** | Images Present vs Absent | If vault images exist, exports `.zip` archive named `[Title].zip`; if no images exist, exports clean `[Title].md` directly. |
| **Zip Directory Layout** | `[Title].md` + `images/` directory | Manuscript resides at archive root; images reside under `images/` folder with normalized filenames (`images/image_1.jpg`). |
| **Relative Image Rewriting** | Vault URI replacement | All internal `vault-img://` URIs are rewritten to relative POSIX paths (`images/image_X.ext`). |
| **Missing Image Fallback** | Unresolvable vault assets | Missing vault images rewrite to `![[Illustration: Missing Asset]]` or caption placeholder so external readers do not throw 404 image errors. |
| **Syntax Fidelity** | GFM tables, task lists, code blocks | Preserves code fences (` ```kotlin `), pipe tables, nested list indentation, and blockquotes. |

---

## 6. Clean Plain Text (TXT)
**Target Standard:** RFC 3629 UTF-8 Plain Text.

### Target Applications
- **Notepad++ (Windows), VS Code, Apple TextEdit, GNU Nano (Linux).**
- **Screen Readers (Android TalkBack, Windows NVDA, Apple VoiceOver).**

### Conformance Verification Items
| Checkpoint | Target Element / Behavior | Pass Criteria |
| :--- | :--- | :--- |
| **Syntax Stripping** | Removal of formatting markers | Strips `#`, `**`, `*`, `~~`, and `>` formatting tokens while preserving intentional blank lines and paragraph separation. |
| **Literal Character Preservation** | `#`, `*`, `\|` as prose | Literal `#` in text (e.g. `The #1 Bestseller`) and literal `*` (e.g. `5 * 5 = 25`) are preserved without corruption. |
| **Illustration Annotations** | Image replacements | Images are converted to semantic editorial text markers: `[Illustration: Caption]` or `[Illustration]`. |
| **Strict UTF-8 Encoding** | Non-Latin scripts | Urdu, Arabic, Persian, Cyrillic, Greek, and Hebrew characters retain 100% byte fidelity without BOM issues or character corruption. |

---

## 7. Manual Verification Scenarios (For Device Testing After Install)

Once you install the APK on an Android device, perform these specific manual test scenarios to verify end-to-end user experience:

### Scenario A: Mixed English & Urdu Document Export
1. Create a note in Scribe containing:
   - English title and first chapter.
   - Urdu heading (`## باب اول`) and Urdu paragraph (`یہ ایک معلوماتی پیراگراف ہے جس میں اردو الفاظ شامل ہیں۔`).
   - Bullet list with one Urdu item (`- پہلا نکتہ`) and one English item (`- Second item`).
2. Open the **Export Sheet** and select **DOCX**.
3. Tap **Export Manuscript**, save the file, and open it in **Microsoft Word** or **Google Docs**.
4. **Verify:**
   - The Urdu paragraph is right-aligned with right-to-left flow.
   - The trailing period `۔` is on the left side (the logical end in RTL).
   - The English paragraph is left-aligned and unaffected.
5. Repeat for **HTML** and open in **Chrome**:
   - Verify `<p dir="rtl">` on the Urdu paragraph and `<p dir="ltr">` or clean `<p>` on the English paragraph.

### Scenario B: Missing Image Asset Callout Check
1. Insert a mock or unresolvable image reference in a note: `![Lost Map](vault-img://missing_sample.jpg)`.
2. Export to **DOCX**, **HTML**, and **Plain Text**.
3. Open each:
   - In **DOCX**: Verify a light gray bordered box appears stating `[Illustration: Lost Map]`. Word must open without any "Word found unreadable content" XML warning.
   - In **HTML**: Verify a centered `<figure class="editorial-callout">` appears with `[Illustration: Lost Map]`.
   - In **TXT**: Verify `[Illustration: Lost Map]` appears as clean text.

### Scenario C: 25k-Word Long Manuscript Stress Test
1. Export a multi-chapter long manuscript (> 20,000 words).
2. Export to **EPUB**:
   - Open in **Calibre** or **Google Play Books**.
   - Verify chapters split cleanly under Table of Contents.
   - Verify instant opening without memory crashes or freezing.
3. Switch to the **Source Inspector Tab** in the Scribe Export Sheet:
   - Verify that scrolling through the manuscript source preview is smooth with the virtualized line gutter and doesn't freeze or drop frames.

### Scenario D: Publication Settings Persistence
1. Open the Export Sheet, switch to the **Publication Settings** tab.
2. Change paper size to **Letter**, font preset to **Classic Serif**, and font size to **14 pt**.
3. Dismiss the sheet, close the app completely from recent apps, and reopen Scribe.
4. Open the Export Sheet again:
   - Verify that your settings (**Letter**, **Classic Serif**, **14 pt**) are preserved.
