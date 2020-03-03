# AudioAnchor
*Android audio player that tracks the listening progress of your audio books and podcasts.*

<a href="https://f-droid.org/packages/com.prangesoftwaresolutions.audioanchor/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid" height="80">
</a>

## Features
AudioAnchor offers a clean and intuitive interface for listening to audio files while keeping track of listening progress.\

Additional features include:
- Set bookmarks
- Adjust playback speed
- Export and import listening progress as SQL database
- Sleep timer
- Lock screen media controls (for Android 5.0 Lollipop and higher)

## Installation
The best way is to install [F-Droid](https://f-droid.org/) on your Android device, search for AudioAnchor, and install the app from there. This way, AudioAnchor will automatically be kept up to date on your device.\
Alternatively, you can download the latest apk from [F-Droid](https://f-droid.org/packages/com.prangesoftwaresolutions.audioanchor/) (or from [releases](https://github.com/flackbash/AudioAnchor/releases)) and execute it on your device.\
The latest release runs on devices with Android API level 19 (Android 4.4 KitKat) and higher.

## Usage
Create a parent directory and put your audio files into direct subdirectories (in the following referred to as "albums") of that parent directory.
For example, if you have the parent directory "Audiofiles", put all the audio files belonging to your "Hardcore History"-podcast into the album "Hardcore History".
When you first start AudioAnchor, the app will prompt you to select the parent directory.
Once the parent directory is selected, all your albums will show in a list.\
Add cover images to the subdirectories to have a neat looking app as shown in the screenshots below.
<pre>
        <img src="https://github.com/flackbash/AudioAnchor/blob/master/metadata/android/en-US/phoneScreenshots/01MainActivity.jpg" height="400"/>
        <img src="https://github.com/flackbash/AudioAnchor/blob/master/metadata/android/en-US/phoneScreenshots/10AlbumActivityLOTR.jpg" height="400"/>
        <img src="https://github.com/flackbash/AudioAnchor/blob/master/metadata/android/en-US/phoneScreenshots/20PlayActivityLOTR.jpg" height="400"/>
</pre>

Browse and play your audio files just as you would in any other audio player.

## Permissions
AudioAnchor asks for the following permissions:
- Read external storage (to play audio files)
- Write external storage (to export the listening progress)
- Read phone state (to pause the audio during a call)

## License

    Copyright © 2018-2020 Natalie Prange

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
