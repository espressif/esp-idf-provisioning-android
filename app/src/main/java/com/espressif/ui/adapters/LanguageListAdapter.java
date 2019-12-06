package com.espressif.ui.adapters;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.espressif.provision.R;
import com.espressif.ui.models.Language;

import java.util.ArrayList;

public class LanguageListAdapter extends ArrayAdapter<Language> {

    private Context context;
    private ArrayList<Language> languageList;
    private int position = -1;

    public LanguageListAdapter(Context context, int resource, ArrayList<Language> languageList) {
        super(context, resource, languageList);
        this.context = context;
        this.languageList = languageList;
    }

    public View getView(int position, View convertView, ViewGroup parent) {

        Language language = languageList.get(position);

        //get the inflater and inflate the XML layout for each item
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.item_language, null);

        TextView langText = view.findViewById(R.id.tv_language_name);
        ImageView checkMark = view.findViewById(R.id.iv_lang_selection);

        langText.setText(language.getLanguageName());

        if (language.isSelected()) {
            this.position = position;
            checkMark.setVisibility(View.VISIBLE);
        } else {
            checkMark.setVisibility(View.GONE);
        }

        return view;
    }

    public void setLanguageChecked(int pos) {

        if (position != -1) {

            languageList.get(position).setSelected(false);
        }
        position = pos;
        languageList.get(position).setSelected(true);
        notifyDataSetChanged();
    }
}
