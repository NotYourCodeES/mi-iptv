package es.mitv.iptv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import es.mitv.iptv.core.ParentalLock;

/** Asks for the 4-digit parental control PIN with the remote's number keys or the on-screen keyboard. */
final class PinDialog {

    /** Checks what was typed: returns an error to show, or null to accept it. */
    interface Check {
        String verify(String pin);
    }

    private PinDialog() {}

    static void show(Activity a, String prompt, Check check, Runnable onAccepted) {
        float density = a.getResources().getDisplayMetrics().density;
        int pad = Math.round(24 * density);

        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, Math.round(8 * density), pad, 0);

        TextView label = new TextView(a);
        label.setText(prompt);
        label.setTextSize(16);
        label.setTextColor(a.getColor(R.color.text2));
        box.addView(label);

        EditText field = new EditText(a);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(ParentalLock.PIN_DIGITS)});
        field.setGravity(Gravity.CENTER);
        field.setTextSize(28);
        field.setLetterSpacing(0.5f);
        box.addView(field);

        TextView error = new TextView(a);
        error.setTextColor(a.getColor(R.color.live));
        error.setTextSize(14);
        error.setVisibility(View.GONE);
        box.addView(error);

        AlertDialog dialog = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.pin_title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        Runnable submit = () -> {
            String problem = check.verify(field.getText().toString());
            if (problem == null) {
                dialog.dismiss();
                onAccepted.run();
            } else {
                error.setText(problem);
                error.setVisibility(View.VISIBLE);
                field.setText("");
            }
        };
        // Accept as soon as the 4th digit is typed, like the phone apps
        field.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a1) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (s.length() == ParentalLock.PIN_DIGITS) submit.run();
            }
        });
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> submit.run()));
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
        field.requestFocus();
    }

    /** Asks for the saved PIN. */
    static void ask(Activity a, ParentalLock lock, Runnable onCorrect) {
        show(a, a.getString(R.string.pin_enter),
                pin -> lock.check(pin) ? null : a.getString(R.string.pin_wrong), onCorrect);
    }

    /** Asks for a new PIN twice and saves it. */
    static void choose(Activity a, ParentalLock lock, Runnable onSaved) {
        final String[] first = new String[1];
        show(a, a.getString(R.string.pin_new), pin -> {
            if (!ParentalLock.isValidPin(pin)) return a.getString(R.string.pin_invalid);
            first[0] = pin;
            return null;
        }, () -> show(a, a.getString(R.string.pin_repeat),
                pin -> pin.equals(first[0]) ? null : a.getString(R.string.pin_mismatch),
                () -> {
                    lock.setPin(first[0]);
                    onSaved.run();
                }));
    }
}
