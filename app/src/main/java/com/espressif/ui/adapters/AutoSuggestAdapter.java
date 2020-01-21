package com.espressif.ui.adapters;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;

import java.util.ArrayList;
import java.util.List;


public class AutoSuggestAdapter extends ArrayAdapter<String> implements Filterable {
    private List<String> listData;

    public AutoSuggestAdapter(@NonNull Context context, int resource) {
        super(context, resource);
        listData = new ArrayList<>();
    }

    public void setListData(List<String> list) {
        listData.clear();
        listData.addAll(list);
    }

    @Override
    public int getCount() {
        return listData.size();
    }

    @Nullable
    @Override
    public String getItem(int position) {
        return listData.get(position);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        Filter dataFilter = new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults filterResults = new FilterResults();
                if (constraint != null) {
                    filterResults.values = listData;
                    filterResults.count = listData.size();
                }
                return filterResults;
            }
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                if (results != null && (results.count > 0)) {
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }
            }
        };
        return dataFilter;
    }
}
