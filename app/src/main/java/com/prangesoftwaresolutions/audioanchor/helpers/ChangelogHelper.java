package com.prangesoftwaresolutions.audioanchor.helpers;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.prangesoftwaresolutions.audioanchor.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/*
 * Shows a "What's new" dialog after an app update, built from the plain-text changelog entries
 * bundled under assets/changelogs/. See buildChangelog() for the naming convention.
 */
public class ChangelogHelper {
    private static final String CHANGELOG_ASSET_DIR = "changelogs";

    /*
     * Show the changelog dialog if this is a genuine update (not a first install) and at least
     * one version between previousVersionCode (exclusive) and currentVersionCode (inclusive) has
     * a changelog entry. Does nothing otherwise.
     */
    public static void showIfUpdated(Context context, int previousVersionCode, int currentVersionCode) {
        // previousVersionCode is 0 on a first install (see preference_version_code_default) --
        // there's nothing to have changed relative to, so don't show anything.
        if (previousVersionCode <= 0 || previousVersionCode >= currentVersionCode) {
            return;
        }
        String changelog = buildChangelog(context, previousVersionCode, currentVersionCode);
        if (changelog == null || changelog.isEmpty()) {
            return;
        }
        showDialog(context, changelog);
    }

    /*
     * Concatenate the changelog entries for every version code in (previousVersionCode,
     * currentVersionCode], newest first, skipping any version that has no entry. This covers
     * users who update across more than one version at once (e.g. they skipped a release).
     */
    private static String buildChangelog(Context context, int previousVersionCode, int currentVersionCode) {
        StringBuilder builder = new StringBuilder();
        for (int versionCode = currentVersionCode; versionCode > previousVersionCode; versionCode--) {
            String entry = readChangelogEntry(context, versionCode);
            if (entry == null || entry.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append(entry);
        }
        return builder.toString();
    }

    /*
     * Read assets/changelogs/<versionCode>.txt, or null if that version has no changelog entry.
     */
    private static String readChangelogEntry(Context context, int versionCode) {
        String fileName = CHANGELOG_ASSET_DIR + "/" + versionCode + ".txt";
        StringBuilder builder = new StringBuilder();
        try (InputStream is = context.getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
        } catch (IOException e) {
            // No changelog entry bundled for this version -- expected for versions that don't
            // touch anything user-facing.
            return null;
        }
        String entry = builder.toString().trim();
        return entry.isEmpty() ? null : entry;
    }

    private static void showDialog(Context context, String changelog) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_changelog, null);
        TextView changelogTV = view.findViewById(R.id.changelog_text);
        changelogTV.setText(changelog);

        new AlertDialog.Builder(context)
                .setTitle(R.string.changelog_title)
                .setView(view)
                .setPositiveButton(R.string.dialog_msg_ok, null)
                .show();
    }
}
