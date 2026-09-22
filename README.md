# AudioAnchor
*A clean, offline Android audio player built for audiobooks, podcasts, and all audio files where listening progress matters.*

<a href="https://f-droid.org/packages/com.prangesoftwaresolutions.audioanchor/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid" height="80">
</a>

![License: GPLv3](https://img.shields.io/badge/license-GPLv3-blue.svg)

## What to expect
AudioAnchor plays audio files that are already on your device: audiobook chapters, podcast episodes, or anything else organized in folders. It automatically tracks your listening progress in every file, so you always pick up right where you left off instead of having to seek back manually.

It's a local player, not a streaming service: it doesn't fetch content or manage podcast subscriptions for you. You bring the files, AudioAnchor organizes and remembers them.

## Why AudioAnchor
- **Remembers your place.** Progress is tracked per file, automatically, so jumping between books or podcasts never costs you your spot.
- **Understands audiobook folders.** Add a directory full of subfolders (one per book) in a single step instead of adding each one individually.
- **Fully offline.** No account, no ads, no analytics or tracking of any kind. AudioAnchor doesn't even request internet access.
- **Free and open source**, licensed under the GPLv3 and available on F-Droid.
- **Flexible library.** Sort albums and tracks by title, date added, progress, or last played, and pin your current favorites to the top.
- **Built for long-form listening.** Adjustable playback speed, a sleep timer with fade-out and shake-to-reset, and bookmarks for the moment you want to find again.
- **Plays nicely with your phone.** Lock screen and notification playback controls, home screen widgets (from a single play button that resumes your latest track up to a full set of skip and bookmark buttons), and audio pauses automatically for incoming calls.
- **Wide format support**, including MP3, M4A/M4B, OGG/Opus, FLAC, WAV, WebM, MKV, and more.
- **Your data stays yours.** Export or import your entire listening history as a database file whenever you like.

## Screenshots

<pre>
<img src="https://github.com/flackbash/AudioAnchor/blob/master/metadata/android/en-US/phoneScreenshots/01MainActivity.jpg" height="400"/>    <img src="https://github.com/flackbash/AudioAnchor/blob/master/metadata/android/en-US/phoneScreenshots/10AlbumActivityLOTR.jpg" height="400"/>    <img src="https://github.com/flackbash/AudioAnchor/blob/master/metadata/android/en-US/phoneScreenshots/20PlayActivityLOTR.jpg" height="400"/>    <img src="https://github.com/flackbash/AudioAnchor/blob/master/metadata/android/en-US/phoneScreenshots/30NotificationLOTR.jpg" height="400"/>
</pre>

## Installation
The best way is to install [F-Droid](https://f-droid.org/) on your Android device, search for AudioAnchor, and install the app from there. This way, AudioAnchor will automatically be kept up to date on your device.\
Alternatively, you can download the latest apk from [F-Droid](https://f-droid.org/packages/com.prangesoftwaresolutions.audioanchor/) (or from [releases](https://github.com/flackbash/AudioAnchor/releases)) and execute it on your device.\
AudioAnchor runs on devices with Android 5.0 (Lollipop, API level 21) and higher.

## Usage
Start off by adding directories to your library. When adding a new directory you have two options:
* You can add a directory that contains audio files
* You can add a directory that contains subdirectories which contain audio files

For example, you might have a directory `Podcast History Hour` that contains audio files.
Add this directory to your library using the option *Add directory that contains audio files*.
Additionally, you might have a directory `AudioBooks` that contains subdirectories such as `The Hobbit` or `Harry Potter II` each of which contains one or several audio files.
You can add this directory to your library using the option *Add directory with subdirectories that contain audio files*.\
This prevents you from having to add your audio books one by one.

Add cover images to the subdirectories to have a neat looking app as shown in the screenshots above.

Once your library is set up, browse and play your audio files just as you would in any other audio player — AudioAnchor takes care of remembering where you left off.

## Permissions
AudioAnchor asks for the following permissions:
- **Storage and media access** — to find your audio files and their cover art, including formats like `.webm` and `.mkv` that Android classifies as video
- **Notifications** — to show playback controls while a file is playing
- **Phone state** — to pause playback automatically during calls
- **Vibration** — for the optional shake-to-reset sleep timer
- **Background playback** — to keep playing reliably while the screen is off or another app is in the foreground

AudioAnchor never requests internet access, and none of these permissions are used to collect or share data.

## License

    Copyright © 2018-2026 Natalie Prange

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
