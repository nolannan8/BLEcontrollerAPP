package com.avnan.blecontrollerapp;

import android.os.AsyncTask;
import android.widget.TextView;

public class ScanAsyncTask extends AsyncTask<Void, Void, String> {
    // Weak references to UI elements

    // Constructor
    ScanAsyncTask() {
        // Attach UI parameters to the weak references mentioned above
    }

    @Override
    protected String doInBackground(Void... voids) {
        return null;
    }

    protected void onPostExecute(String result) {
        // Can update UI elements in this function, since this function
        // runs on the UI thread
        // WeakReferenced UI element.get().setText(----);
    }
}
