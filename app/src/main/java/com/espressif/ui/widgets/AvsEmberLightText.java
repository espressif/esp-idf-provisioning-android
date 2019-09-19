package com.espressif.ui.widgets;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;

public class AvsEmberLightText extends android.support.v7.widget.AppCompatTextView {

    public AvsEmberLightText(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.setTypeface(Typeface.createFromAsset(context.getAssets(), "fonts/AmazonEmberDisplay_Lt.ttf"));
    }
}
