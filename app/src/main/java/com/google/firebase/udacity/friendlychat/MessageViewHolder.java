package com.google.firebase.udacity.friendlychat;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

public class MessageViewHolder extends RecyclerView.ViewHolder {

    private ImageView photoImageView;
    private TextView messageTextView;
    private TextView authorTextView;


    public MessageViewHolder(@NonNull View itemView) {
        super(itemView);
        photoImageView = itemView.findViewById(R.id.photoImageView);
        messageTextView = itemView.findViewById(R.id.messageTextView);
        authorTextView = itemView.findViewById(R.id.nameTextView);
    }

    public void bindData(FriendlyMessage message) {
        boolean isPhoto = message.getPhotoUrl() != null;
        if (isPhoto) {
            messageTextView.setVisibility(View.GONE);
            photoImageView.setVisibility(View.VISIBLE);
            Glide.with(photoImageView.getContext())
                .load(message.getPhotoUrl())
                .into(photoImageView);
        } else {
            messageTextView.setVisibility(View.VISIBLE);
            photoImageView.setVisibility(View.GONE);
            messageTextView.setText(message.getText());
        }
        authorTextView.setText(message.getName());
    }


}
