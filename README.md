# AndroidToolBox

Android toolbox with two separate tools: an SSH file manager, and a domain registration lookup.

- SSH / SFTP file manager and terminal
- Domain lookup through the official IANA RDAP bootstrap and registry WHOIS

## Saved connections

Passwords, private keys, passphrases, and the rest of each saved SSH profile are encrypted together using AES-256-GCM. The encryption key stays in Android Keystore. Encrypted files are stored in the app's private, non-backup directory.

Existing Base64 profiles are migrated on first load. The encrypted copy is verified before the old preferences and their backup are removed. An unreadable key, corrupted ciphertext, or failed write blocks further profile changes until a successful retry; existing data is never silently reset. Device migration and reinstall do not carry these device-bound credentials to a new installation.

## File visibility

Open the settings icon in the connected SSH file manager to change two independent, persistent filters. System hidden files are hidden by default; user hidden files remain visible by default.

Windows uses the actual System and Hidden attributes when the server allows PowerShell. If attribute lookup is unavailable, filename rules remain available. Unix dotfiles such as `.ssh`, `.git`, and `.env` are user hidden files; known OS metadata such as `.DS_Store` is classified separately as system hidden. Archive entries use filename rules.

## Markdown and editing

Opening `.md`, `.markdown`, `.mdown`, or `.mkd` files offers formatted preview or source editing. Preview supports CommonMark, tables, task lists, and strikethrough. It runs offline with scripts, local file access, and external images disabled. Explicit web and email links can open externally. Archive contents and truncated files remain read-only.

The editor includes language-specific syntax colors, local word and keyword completion, language snippets, bracket and quote pairing, automatic indentation, selection indentation, undo/redo, literal find/replace, line numbers, cursor position, font sizing, line wrapping, and an unsaved-changes prompt. Highlighting and word indexing run in the background; undo history has a memory limit. Completion uses the document and language keywords rather than a language server, so it does not provide compiler diagnostics or project-wide semantic completion.

Hardware keyboard shortcuts: Tab / Shift+Tab for indentation, Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y for history, Ctrl+F for search, and Ctrl+S for save. New feature strings are provided in English and Chinese; other locales fall back to English.

## Validation

Run `./gradlew :app:lintDebug :app:assembleDebug`.

Copyright 2026 [Jinhong270](https://github.com/Jinhong270)

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
