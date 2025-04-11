package com.espressif.ui.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.espressif.mediwatch.R;

public class ProgressDialogFragment extends DialogFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_MESSAGE = "message";

    private DialogInterface.OnCancelListener cancelListener;

    public static ProgressDialogFragment newInstance(String title, String message) {
        ProgressDialogFragment fragment = new ProgressDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, title);
        args.putString(ARG_MESSAGE, message);
        fragment.setArguments(args);
        return fragment;
    }

    public void setOnCancelListener(DialogInterface.OnCancelListener listener) {
        this.cancelListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        String title = requireArguments().getString(ARG_TITLE);
        String message = requireArguments().getString(ARG_MESSAGE);

        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_progress, null);
        TextView tvMessage = view.findViewById(R.id.tv_message);
        tvMessage.setText(message);

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        builder.setTitle(title)
                .setView(view)
                .setCancelable(true);

        return builder.create();
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        super.onCancel(dialog);
        if (cancelListener != null) {
            cancelListener.onCancel(dialog);
        }
    }
}