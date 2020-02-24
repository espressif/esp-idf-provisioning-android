package com.espressif.ui.widgets;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;

public class BookerlyItalicText extends androidx.appcompat.widget.AppCompatTextView {

    public BookerlyItalicText(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setTypeface(Typeface.createFromAsset(context.getAssets(), "fonts/Bookerly-Italic.ttf"));
    }
}
