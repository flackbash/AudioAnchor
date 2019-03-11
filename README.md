# AudioAnchor
*Android audio player that tracks the listening progress of your audio books and podcasts*

<a href="https://f-droid.org/en/packages/com.prangesoftwaresolutions.audioanchor/" target="_blank">
<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="90"/></a>

## Features
AudioAnchor offers a clean and intuitive interface for listening to audio files while keeping track of the listening progress.\
Additional features include:
- set bookmarks
- export and import listening progress as sql database
- sleep timer
- lock screen media controls (for Android 5.0 Lollipop and higher)

## Install
The best way is to install [F-Droid](https://f-droid.org/en/) on your Android device, search for AudioAnchor and install the app from there. This way, AudioAnchor will automatically be kept up to date on your device.\
Alternatively, you can download the latest apk from [F-Droid](https://f-droid.org/en/packages/com.prangesoftwaresolutions.audioanchor/) (or from [releases](https://github.com/flackbash/AudioAnchor/releases)) and execute it on your device.\
The latest release runs on devices with Android API level 19 (Android 4.4 KitKat) and higher.

## Usage
Create a parent directory and put your audio files into direct subdirectories (in the following referred to as "albums") of that parent directory.
For example, if you have the parent directory "Audiofiles", put all the audio files belonging to your "Hardcore History"-podcast into the album "Hardcore History".
When you are first starting AudioAnchor, the app will prompt you to select the parent directory.
Once the parent directory is selected, all your albums will show in a list.\
Add cover images to the subdirectories to have a neat looking app as shown in the screenshots below.
<pre>
        <img src="https://github.com/flackbash/AudioAnchor/blob/master/metadata/android/en-US/phoneScreenshots/01MainActivity.jpg" height="400"/>        <img src="https://github.com/flackbash/AudioAnchor/blob/master/metadata/android/en-US/phoneScreenshots/10AlbumActivityLOTR.jpg" height="400"/>        <img src="https://github.com/flackbash/AudioAnchor/blob/master/metadata/android/en-US/phoneScreenshots/20PlayActivityLOTR.jpg" height="400"/>
</pre>

Browse and play your audio files just as you would in any other audio player.

**Note:** The audio player stops playing when you press the back button in the PlayActivity.
This is also when your progress get's saved.

## Permissions
AudioAnchor asks for the following permissions:
- Read External Storage (to play audio files)
- Write External Storage (to export the listening progress)
- Read Phone State (to pause the audio during a call)
