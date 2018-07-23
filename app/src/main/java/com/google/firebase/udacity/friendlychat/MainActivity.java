/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    private static final String FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length";

    private RecyclerView mMessageListView;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;
    private FirebaseDatabase firebaseDatabase;
    private DatabaseReference databaseReference;
    private ChildEventListener childEventListener;
    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener authStateListener;
    private FirebaseStorage firebaseStorage;
    private StorageReference chatPhotoStorageReference;
    private FirebaseRemoteConfig firebaseRemoteConfig;
    private FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder> adapter;

    private String mUsername;

    public static final int RC_AUTH = 1337;
    public static final int RC_PHOTO_PICKER = 1338;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        firebaseDatabase = FirebaseDatabase.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseStorage = FirebaseStorage.getInstance();
        firebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        databaseReference = firebaseDatabase.getReference().child("messages");
        chatPhotoStorageReference = firebaseStorage.getReference().child("chat_photos");
        // Initialize references to views
        mProgressBar = findViewById(R.id.progressBar);
        mMessageListView = findViewById(R.id.messageListView);
        mPhotoPickerButton = findViewById(R.id.photoPickerButton);
        mMessageEditText = findViewById(R.id.messageEditText);
        mSendButton = findViewById(R.id.sendButton);

        Query query = firebaseDatabase.getReference().child("messages").limitToLast(50);

        FirebaseRecyclerOptions<FriendlyMessage> options = new FirebaseRecyclerOptions.Builder<FriendlyMessage>()
            .setQuery(query, FriendlyMessage.class)
            .build();

        // Initialize message ListView and its adapter
        adapter = new FirebaseRecyclerAdapter<FriendlyMessage, MessageViewHolder>(options) {
            @Override
            protected void onBindViewHolder(@NonNull MessageViewHolder holder, int position, @NonNull FriendlyMessage message) {
                holder.bindData(message);
            }

            @NonNull
            @Override
            public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
                View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_message, viewGroup, false);
                return new MessageViewHolder(view);
            }

            @Override
            public void onDataChanged() {
                mMessageListView.smoothScrollToPosition(adapter.getItemCount() - 1);
            }
        };
        LinearLayoutManager manager = new LinearLayoutManager(this);
        manager.setStackFromEnd(true);
        mMessageListView.setLayoutManager(manager);
        mMessageListView.setAdapter(adapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(view -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(view -> {
            FriendlyMessage message = new FriendlyMessage(mMessageEditText.getText().toString().trim(), mUsername, null);
            databaseReference.push().setValue(message);
            // Clear input box
            mMessageEditText.setText("");
        });


        authStateListener = firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                //user signed in
                ondSignedInInit(user.getDisplayName());
            } else {
                //user signed out
                onSignedOutClean();
                startActivityForResult(AuthUI.getInstance().createSignInIntentBuilder()
                    .setIsSmartLockEnabled(false)
                    .setAvailableProviders(Arrays.asList(
                        new AuthUI.IdpConfig.EmailBuilder().build(),
                        new AuthUI.IdpConfig.GoogleBuilder().build()
                    ))
                    .build(), RC_AUTH);
            }
        };

        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
            .setDeveloperModeEnabled(BuildConfig.DEBUG)
            .build();
        firebaseRemoteConfig.setConfigSettings(configSettings);

        Map<String, Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put(FRIENDLY_MSG_LENGTH_KEY, DEFAULT_MSG_LENGTH_LIMIT);
        firebaseRemoteConfig.setDefaults(defaultConfigMap);
        fetchConfig();
    }

    private void ondSignedInInit(String displayName) {
        mUsername = displayName;
        attachDatabaseReadListener();
    }

    private void attachDatabaseReadListener() {
        adapter.startListening();
        /*if (childEventListener == null) {
            childEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
                    FriendlyMessage message = dataSnapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(message);
                }

                @Override
                public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {

                }

                @Override
                public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {

                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            };
            databaseReference.addChildEventListener(childEventListener);
        }*/
    }

    private void onSignedOutClean() {
        mUsername = ANONYMOUS;
        detachDatabaseListener();
    }

    private void detachDatabaseListener() {
        adapter.stopListening();
        /*if (childEventListener != null) {
            databaseReference.removeEventListener(childEventListener);
            childEventListener = null;
        }*/
    }

    private void fetchConfig() {
        long cacheExpiration = 3600;

        if (firebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
            cacheExpiration = 0;
        }
        firebaseRemoteConfig.fetch(cacheExpiration).addOnSuccessListener(aVoid -> {
            firebaseRemoteConfig.activateFetched();
            applyRetrievedLenghtLimit();
        }).addOnFailureListener(e -> {
            Log.w(TAG, "Error fetching config", e);
            applyRetrievedLenghtLimit();
        });
    }

    private void applyRetrievedLenghtLimit() {
        Long friendly_msg_length = firebaseRemoteConfig.getLong(FRIENDLY_MSG_LENGTH_KEY);
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(friendly_msg_length.intValue())});
        Log.d(TAG, "applyRetrievedLenghtLimit = " + friendly_msg_length);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.sign_out_menu: {
                AuthUI.getInstance().signOut(this);
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_AUTH) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Signed in \\o/", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Exiting", Toast.LENGTH_SHORT).show();
                finish();
            }
        } else if (requestCode == RC_PHOTO_PICKER) {
            Uri selectedPhoto = data.getData();
            StorageReference photoRef = chatPhotoStorageReference.child(selectedPhoto.getLastPathSegment());
            UploadTask uploadTask = photoRef.putFile(selectedPhoto);
            Task<Uri> urlTask = uploadTask.continueWithTask(task -> {
                if (!task.isSuccessful()) {
                    throw task.getException();
                }
                return photoRef.getDownloadUrl();
            }).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Uri downloadUri = task.getResult();
                    FriendlyMessage friendlyMessage = new FriendlyMessage(null, mUsername, downloadUri.toString());
                    databaseReference.push().setValue(friendlyMessage);
                } else {
                    //TODO handle failure
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        firebaseAuth.addAuthStateListener(authStateListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        firebaseAuth.removeAuthStateListener(authStateListener);
        detachDatabaseListener();
    }
}
