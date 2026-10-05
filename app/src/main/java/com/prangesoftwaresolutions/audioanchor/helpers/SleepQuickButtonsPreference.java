package com.prangesoftwaresolutions.audioanchor.helpers;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.prangesoftwaresolutions.audioanchor.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/*
 * Shows the sleep timer's quick button minute values as a row of cards that remove themselves on
 * tap, plus a trailing "add" card (hidden once MAX_BUTTONS is reached) that prompts for a new
 * value. Stored as a single comma-separated preference rather than one preference per button,
 * since the count is now variable (0-MAX_BUTTONS) instead of fixed.
 */
public class SleepQuickButtonsPreference extends Preference {

    public static final int MAX_BUTTONS = 6;
    private static final String DELIMITER = ",";

    private List<Integer> mValues = new ArrayList<>();

    public SleepQuickButtonsPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_sleep_quick_buttons);
    }

    public static List<Integer> parse(String csv) {
        List<Integer> values = new ArrayList<>();
        if (csv == null || csv.isEmpty()) return values;
        for (String part : csv.split(DELIMITER)) {
            try {
                values.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
                // Skip anything malformed rather than failing the whole list.
            }
        }
        return values;
    }

    private static String join(List<Integer> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(DELIMITER);
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getString(index);
    }

    @Override
    protected void onSetInitialValue(@Nullable Object defaultValue) {
        String persisted = getPersistedString(defaultValue != null ? (String) defaultValue : "");
        mValues = parse(persisted);
        Collections.sort(mValues);
        // Self-heal stale unsorted storage from before sorting was added -- without this, only
        // the in-memory list (and thus this screen's own display) would be sorted, while anyone
        // reading the raw preference directly (the sleep timer dialog) would still see the old
        // order until the next add/remove.
        String sortedCsv = join(mValues);
        if (!sortedCsv.equals(persisted)) {
            persistString(sortedCsv);
        }
    }

    private void persist() {
        Collections.sort(mValues);
        persistString(join(mValues));
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        FlowLayout container = (FlowLayout) holder.findViewById(R.id.quick_button_card_container);
        populateCards(container);
    }

    private void populateCards(FlowLayout container) {
        container.removeAllViews();
        Context context = container.getContext();
        int maxMinutes = Integer.parseInt(context.getString(R.string.settings_sleep_timer_max_minutes));

        for (int value : mValues) {
            TextView card = new TextView(context);
            card.setText(context.getString(R.string.settings_sleep_quick_button_remove_label, value));
            card.setTextColor(getCardTextColor(context));
            card.setBackgroundResource(R.drawable.bg_sleep_quick_button_card);
            card.setTextSize(13);
            card.setGravity(Gravity.CENTER);
            int hPad = dpToPx(context, 10);
            int vPad = dpToPx(context, 4);
            card.setPadding(hPad, vPad, hPad, vPad);
            card.setContentDescription(context.getString(R.string.settings_sleep_quick_button_remove_desc, value));
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int margin = dpToPx(context, 4);
            lp.setMargins(margin, margin, margin, margin);
            card.setLayoutParams(lp);
            card.setOnClickListener(v -> {
                mValues.remove(Integer.valueOf(value));
                persist();
                populateCards(container);
            });
            container.addView(card);
        }

        if (mValues.size() < MAX_BUTTONS) {
            ImageView addCard = new ImageView(context);
            addCard.setImageResource(R.drawable.ic_add);
            addCard.setBackgroundResource(R.drawable.bg_sleep_quick_button_add_card);
            int size = dpToPx(context, 28);
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(size, size);
            int margin = dpToPx(context, 4);
            lp.setMargins(margin, margin, margin, margin);
            addCard.setLayoutParams(lp);
            int iPad = dpToPx(context, 5);
            addCard.setPadding(iPad, iPad, iPad, iPad);
            addCard.setContentDescription(context.getString(R.string.settings_sleep_quick_button_add_desc));
            addCard.setOnClickListener(v -> showAddDialog(context, container, maxMinutes));
            container.addView(addCard);
        }
    }

    private void showAddDialog(Context context, FlowLayout container, int maxMinutes) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setGravity(Gravity.CENTER_HORIZONTAL);
        input.setMinWidth(context.getResources().getDimensionPixelSize(R.dimen.minutes_min_width));
        input.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // Wrap in a FrameLayout rather than passing the EditText to setView() directly -- without
        // a wrapper, AlertDialog stretches a directly-set view to the full dialog width,
        // regardless of the EditText's own layout params, giving an oversized text field for
        // what's just a couple of digits.
        FrameLayout wrapper = new FrameLayout(context);
        int sidePad = dpToPx(context, 16);
        int vPad = dpToPx(context, 8);
        wrapper.setPadding(sidePad, vPad, sidePad, vPad);
        wrapper.addView(input);

        new AlertDialog.Builder(context)
                .setTitle(R.string.settings_sleep_quick_button_add_title)
                .setView(wrapper)
                .setPositiveButton(R.string.dialog_msg_ok, (dialog, which) -> {
                    int minutes;
                    try {
                        minutes = Integer.parseInt(input.getText().toString());
                    } catch (NumberFormatException e) {
                        return;
                    }
                    minutes = Math.max(1, Math.min(minutes, maxMinutes));
                    if (mValues.size() < MAX_BUTTONS && !mValues.contains(minutes)) {
                        mValues.add(minutes);
                        persist();
                        populateCards(container);
                    }
                })
                .setNegativeButton(R.string.dialog_msg_cancel, null)
                .show();
    }

    private static int getCardTextColor(Context context) {
        return ContextCompat.getColor(context, R.color.sleepTimerQuickButtonText);
    }

    private static int dpToPx(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
