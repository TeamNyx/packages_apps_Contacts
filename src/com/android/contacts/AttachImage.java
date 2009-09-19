/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts;

import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;

/**
 * Provides an external interface for other applications to attach images
 * to contacts. It will first present a contact picker and then run the
 * image that is handed to it through the cropper to make the image the proper
 * size and give the user a chance to use the face detector.
 */
public class AttachImage extends Activity {
    private static final int REQUEST_PICK_CONTACT = 1;
    private static final int REQUEST_CROP_PHOTO = 2;

    private static final String CONTACT_URI_KEY = "contact_uri";

    public AttachImage() {

    }

    /**
     * Is the raw_contact uri for the contact the user selected
     */
    Uri mContactUri;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        if (icicle != null) {
            mContactUri = icicle.getParcelable(CONTACT_URI_KEY);
        } else {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType(Contacts.CONTENT_ITEM_TYPE);
            startActivityForResult(intent, REQUEST_PICK_CONTACT);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (mContactUri != null) {
            outState.putParcelable(CONTACT_URI_KEY, mContactUri);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent result) {
        if (resultCode != RESULT_OK) {
            finish();
            return;
        }

        if (requestCode == REQUEST_PICK_CONTACT) {
            // A contact was picked. Launch the cropper to get face detection, the right size, etc.
            // TODO: get these values from constants somewhere
            Intent myIntent = getIntent();
            Intent intent = new Intent("com.android.camera.action.CROP", myIntent.getData());
            if (myIntent.getStringExtra("mimeType") != null) {
                intent.setDataAndType(myIntent.getData(), myIntent.getStringExtra("mimeType"));
            }
            intent.putExtra("crop", "true");
            intent.putExtra("aspectX", 1);
            intent.putExtra("aspectY", 1);
            intent.putExtra("outputX", 96);
            intent.putExtra("outputY", 96);
            intent.putExtra("return-data", true);
            startActivityForResult(intent, REQUEST_CROP_PHOTO);

            // while they're cropping, convert the contact into a raw_contact
            final long contactId = ContentUris.parseId(result.getData());
            final long rawContactId = ContactsUtils.queryForRawContactId(getContentResolver(),
                    contactId);

            if (rawContactId == -1) {
                Toast.makeText(this, R.string.contactSavedErrorToast, Toast.LENGTH_LONG).show();
            }

            mContactUri = Uri.withAppendedPath(
                    ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId),
                    RawContacts.Data.CONTENT_DIRECTORY);

        } else if (requestCode == REQUEST_CROP_PHOTO) {
            final Bundle extras = result.getExtras();
            if (extras != null) {
                Bitmap photo = extras.getParcelable("data");
                if (photo != null) {
                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    photo.compress(Bitmap.CompressFormat.JPEG, 75, stream);

                    final ContentValues imageValues = new ContentValues();
                    imageValues.put(Photo.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
                    imageValues.put(Photo.PHOTO, stream.toByteArray());
                    imageValues.put(RawContacts.Data.IS_SUPER_PRIMARY, 1);
                    getContentResolver().insert(mContactUri, imageValues);
                }
            }
            finish();
        }
    }
}
