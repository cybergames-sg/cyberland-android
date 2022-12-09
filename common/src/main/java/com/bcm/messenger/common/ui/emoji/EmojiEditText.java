package com.bcm.messenger.common.ui.emoji;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputFilter;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatEditText;

import com.bcm.messenger.common.R;
import com.bcm.messenger.common.preferences.TextSecurePreferences;
import com.bcm.messenger.common.provider.AMELogin;
import com.bcm.messenger.common.ui.emoji.EmojiProvider.EmojiDrawable;


public class EmojiEditText extends AppCompatEditText {
    private static final String TAG = EmojiEditText.class.getSimpleName();

    public EmojiEditText(Context context) {
        this(context, null);
    }

    public EmojiEditText(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.editTextStyle);
    }

    public EmojiEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (!TextSecurePreferences.isSystemEmojiPreferred(AMELogin.INSTANCE.getMajorContext())) {
            setFilters(appendEmojiFilter(this.getFilters()));
        }
    }

    public String getTextTrimmed() {
//        return getText().toString().trim();
        Editable editable = getText();
        if (editable != null) {
            return editable.toString().trim();
        }
        return "";
    }

    public void appendFilter(InputFilter newFilter) {
        InputFilter[] result;
        InputFilter[] originalFilters = getFilters();
        if (originalFilters != null) {
            result = new InputFilter[originalFilters.length + 1];
            System.arraycopy(originalFilters, 0, result, 1, originalFilters.length);
        } else {
            result = new InputFilter[1];
        }
        result[0] = newFilter;
        setFilters(result);
    }

    public void insertEmoji(String emoji) {
        final int start = getSelectionStart();
        final int end = getSelectionEnd();

        getText().replace(Math.min(start, end), Math.max(start, end), emoji);
        setSelection(start + emoji.length());
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable drawable) {
        if (drawable instanceof EmojiDrawable) {
            invalidate();
        } else {
            super.invalidateDrawable(drawable);
        }
    }

    private InputFilter[] appendEmojiFilter(@Nullable InputFilter[] originalFilters) {
        InputFilter[] result;

        if (originalFilters != null) {
            result = new InputFilter[originalFilters.length + 1];
            System.arraycopy(originalFilters, 0, result, 1, originalFilters.length);
        } else {
            result = new InputFilter[1];
        }

        result[0] = new EmojiFilter(this);

        return result;
    }
}
