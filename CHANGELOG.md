# Changelog

## 1.0.1 — 2026-10-13

### Library and design

- Refreshed the phone and tablet library and reader around the provided mockups, with a quieter editorial layout, blue controls, hand-drawn accents and responsive tablet layouts.
- Added All, Reading, Want to Read and Finished shelves, book details and persisted shelf actions. A short Undo action reverses an accidental shelf move, and shelf/sort choices survive rotation.
- Added whole-device EPUB and MOBI discovery, optional folder import, idle-aware background scanning, unchanged-file caching and duplicate detection.
- Added multi-select EPUB/MOBI import from the system picker, with sequential processing and a summary of unreadable files.
- Added a Continue Reading home-screen widget with cover art, saved-position launch and a Library shortcut. The widget follows light and dark appearance and decodes cover art at a small size.
- Added local support for readable unencrypted PalmDOC/MOBI6 books.

### Reading

- Added in-book full-screen search with highlighted snippets and direct passage navigation.
- Added Paper, White, Sepia and Night page styles, Auto Night Mode, reader brightness, text-size and margin controls, and saved scroll/slide navigation preferences.
- Added saved highlight notes, automatic note saving, note sharing, and direct jumps back to passages.
- Added a reader sleep timer that dims the display and releases Folio's keep-awake override when the timer ends.
- Added optional keep-screen-awake behavior while a book is open.
- Added Gemini Nano availability detection and on-device passage explanations when supported by Android and the device.
- Included quick chapter jumps and bookmarks, text-attached multi-color highlights, offline word lookup and vocabulary review, People & Topics, reading-time estimates, Comfort Contrast and Evening Warmth.

### Tablet

- Added landscape two-page reading, a collapsible Contents rail with adjustable width, a compact reader-settings popover and split-pane notes review.
- Added hardware-keyboard navigation: Page Up/Page Down move through the book and Ctrl+F opens in-book search.
- Added a persistent tablet library density control to switch between roomy covers and a more compact grid.
- Added a wide-tablet quick-look panel with cover, progress, shelf actions and a direct read action. Drag its divider to choose how much room the book grid and details get.

### Privacy and compatibility

- Folio keeps books, notes, reading progress and search on-device. Health Connect can be opened later by choice; Folio requests no health-data permissions and reads no health data.
- Simplified first launch by deferring whole-device access until a user chooses device scanning; EPUB/MOBI file picking works without that permission.
- Added an animated, interactive in-app tour in Settings that walks through the library, import, reader, annotations, search, learning tools, Gemini Nano, widget, tablet layout and privacy.
- Preserved local Room data through the schema migration for shelf status, highlight notes and reading progress.
