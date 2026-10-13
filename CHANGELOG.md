# Changelog

## 1.0.1 — 2026-10-13

### Library and design

- Refreshed the phone and tablet library and reader around the provided mockups, with a quieter editorial layout, blue controls, hand-drawn accents and responsive tablet layouts.
- Switched app and widget interface typography to bundled Inter for a consistent, San Francisco-inspired Android look; book text keeps the reader's selected typeface.
- Added All, Reading, Want to Read and Finished shelves, book details and persisted shelf actions. A short Undo action reverses an accidental shelf move, and shelf/sort choices survive app launches.
- Cached shelf filtering and sorting between relevant library changes to avoid repeated list work during unrelated UI updates.
- Added a direct Choose books action to the empty library, simplifying first import without a Settings detour.
- Added whole-device EPUB, MOBI and FB2 discovery, optional folder import, idle-aware background scanning, unchanged-file caching and duplicate detection.
- Moved heavy chapter enrichment out of active use: it waits for idle time, healthy battery and available storage, processes one book at a time, and pauses when Android reports thermal pressure. Books remain readable as soon as import completes.
- Added FB2 (FictionBook 2) and plain-text book support alongside EPUB and MOBI. FB2 is included in device discovery; TXT stays manual to prevent unrelated notes and documents from entering the library.
- Added multi-select EPUB/MOBI/FB2/TXT import from the system picker, with sequential processing and a summary of unreadable files.
- Added a Continue Reading home-screen widget with cover art, saved-position launch and a Library shortcut. The widget follows light and dark appearance and decodes cover art at a small size.
- Made the empty Continue Reading widget open Add Books directly; when every book is finished, it says All Caught Up and returns to the shelf.
- Added local support for readable unencrypted PalmDOC/MOBI6 books.

### Reading

- Added in-book full-screen search with highlighted snippets and direct passage navigation.
- Added Paper, White, Sepia and Night page styles, Auto Night Mode, reader brightness, text-size and margin controls, and saved scroll/slide navigation preferences.
- Added saved highlight notes, automatic note saving, note sharing, and direct jumps back to passages.
- Fixed phone text selection so Folio's Highlight action appears in the selection toolbar and saves a text-attached mark.
- Added a reader sleep timer that dims the display and releases Folio's keep-awake override when the timer ends.
- Added optional keep-screen-awake behavior while a book is open.
- Added Gemini Nano availability detection and on-device passage explanations when supported by Android and the device.
- Included quick chapter jumps and bookmarks, text-attached multi-color highlights, offline word lookup and vocabulary review, People & Topics, reading-time estimates, Comfort Contrast and Evening Warmth.

### Tablet

- Added landscape two-page reading, a collapsible Contents rail with adjustable width, a compact reader-settings popover and split-pane notes review.
- Added hardware-keyboard navigation: Page Up/Page Down move through the book, Ctrl+F opens in-book search, Ctrl+K opens library search, and Escape clears or closes it.
- Added Ctrl+O on tablets to open Add Books from the library; the interactive tour and What’s New explain the shortcut and empty-library import action.
- Added Ctrl+1–4 on tablets to switch between All, Reading, Want to Read and Finished shelves.
- Added a persistent tablet library density control to switch between roomy covers and a more compact grid.
- Added a wide-tablet quick-look panel with cover, progress, shelf actions and a direct read action. Drag its divider to choose how much room the book grid and details get.
- Library shelf and sort preferences now persist across app launches, so Folio returns to the view you chose.

### Privacy and compatibility

- Folio keeps books, notes, reading progress and search on-device. Health Connect can be opened later by choice; Folio requests no health-data permissions and reads no health data.
- Simplified first launch by deferring whole-device access until a user chooses device scanning; EPUB/MOBI/FB2/TXT picking works without that permission.
- Added an animated, interactive in-app tour in Settings that walks through the library, import, reader, annotations, search, learning tools, Gemini Nano, widget, tablet layout and privacy.
- Preserved local Room data through the schema migration for shelf status, highlight notes and reading progress.
