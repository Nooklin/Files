# Nooklin Files

Open-source file manager for Android, part of the Nooklin suite. No Google services, no analytics. Internet is only used for your own Nextcloud server, and only if you connect one.

Version 1.0.0. License GPL-3.0. Changes per version are in [CHANGELOG.md](CHANGELOG.md).

## What it does

- Browse internal storage with a breadcrumb bar, sorting (name, type, size, date) and optional hidden files
- Optional second pane (side by side in landscape, stacked in portrait), copy or move to the other pane
- Copy, move, rename, create folder or file, delete (to own trash or permanently)
- Background copy and move with a notification, progress, speed, remaining time and Cancel
- Conflict prompts per file (overwrite, keep both, skip, apply to all)
- Own trash (Recently Deleted): 30 days, 2 GB or 1000 items, restore or delete permanently
- Zip: compress and extract
- Home: Favorites (folders you add yourself), Locations (On My Phone, USB and SD drives, Nextcloud when connected, Recently Deleted)
- USB and SD drives show up under Locations while mounted. They have no trash, delete is permanent and says so
- Text editor: tap a text file to edit it (up to 1 MB, UTF-8) with undo and redo and syntax highlighting for Kotlin, Java, Python, JavaScript/TypeScript, HTML, XML, CSS, JSON, Shell and C/C++, and a warning on unsaved changes
- Nextcloud (WebDAV, one account, app password): browse, open, download (folders too), upload, new folder, rename, delete, server trash in Recently Deleted; transfers run in the background with a notification
- The second pane can show Nextcloud: copy or move between the phone and the server, and inside the server
- Zip only (no rar), with or without a password (AES 256 when created here)
- Recents: the 100 files with the newest modified date on the phone
- Search bar at the top of Home (whole phone), every folder (the folder and the folders in it) and the Nextcloud screen (open folder)
- Share (off by default, switch on in Settings, Privacy)
- Languages: English and Dutch, follows the system
- FTP and SMB servers: save several (Settings, FTP & SMB servers), they show under Locations on Home. Browse, open, download (folders too), upload, new folder, rename, delete. FTP is plain FTP (not encrypted), SMB is SMB2/3 (no SMB1)
- App Manager (Home, Tools): all apps, back up the APK to Download/App backups, uninstall apps you installed (system apps: back-up and app info only)
- App Manager shows the size of each app under its name (the total with data and cache when Usage access is on) and restores backups (button at the top right, installs through the Android package installer)
- Permissions are explained on the first start (All files access, Notifications) and can be refused: sections that need All files access then show only a message and a button to give it, the rest of the app keeps working. Settings, Permissions shows the status
- Other apps can use Nooklin Files to show a folder or to choose a file
- Storage (Home, Tools): a bar per storage split by kind of file (images, videos, audio, documents, APK files, archives, other), the size and number of files per kind, total, used and free space, type and file system. Apps, app data and the system show as the rest of the used space
- Media (Home, Tools): all videos, images, music and documents on the phone in one list. Group by month, folder, file type or first letter, sort by name, date or size, sticky headers, a menu to jump to any header and thumbnails
- Built-in viewers and players: images (swipe, pinch and double tap to zoom), video and music (Media3 ExoPlayer), PDF, DOCX and EPUB (text only)
- Video gestures: double tap left or right for 10 seconds back or forward, slide up or down on the left for brightness and on the right for volume, slide sideways to seek. Play in background, picture in picture and a notification with controls
- Launcher shortcuts (long press the app icon): Recent Files, App Manager, and Nextcloud, FTP and SMB when set up
- Folder views: List, Details, Grid and Compact grid (the view button at the top of a folder, one choice for all folders)
- Theme, font (Inter by default) and contrast color like the other Nooklin apps
- Privacy: App Lock (the phone's own biometric or screen lock, asked once per start) and Disable Screenshots
- Help & Support (How to use, links to the GitHub repository) and About (change log, GitHub, Privacy, Terms)

## Build

Open in Android Studio or run `./gradlew assembleDebug`. Needs JDK 25 (the Gradle daemon toolchain is resolved by foojay).

## Release

Push a tag like `v1.0.0`. The GitHub Actions workflow in `.github/workflows/release.yml` builds a signed APK and publishes it with a SHA-256 file. It needs the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`. For a local signed build copy `keystore.properties.example` to `keystore.properties` and fill it in.
