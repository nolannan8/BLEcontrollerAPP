package com.avnan.blecontrollerapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_ENABLE_BT = 1;

    private final LinkedList<BluetoothDevice> mDeviceList = new LinkedList<>();

    private RecyclerView mRecyclerView;
    private ScanListAdapter mAdapter;
    private Button mScanBttn;
    private ProgressBar mProgressBar;
    private SwipeRefreshLayout mRefreshLayout;

    // Bluetooth related variables
    BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        mHandler = new Handler();

        final BluetoothManager mBluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (mBluetoothManager.getAdapter() != null)
            mBluetoothAdapter = mBluetoothManager.getAdapter();

        // Check if necessary features are enabled / available
        featureCheck();

        // Get a handle to the RecyclerView
        mRecyclerView = findViewById(R.id.recyclerview);
        // Create an adapter and supply the data to be displayed
        mAdapter = new ScanListAdapter(this, mDeviceList);
        // Connect the adapter with the RecyclerView
        mRecyclerView.setAdapter(mAdapter);
        // Give the RecyclerView a default layout manager
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        mScanBttn = findViewById(R.id.button_scan);
        mProgressBar = findViewById(R.id.progress_bar);
        mRefreshLayout = findViewById(R.id.refresh_layout);

        mRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        startScan(null);
                    }
                }
        );
    }

    @Override
    public void onPause() {
        if (isFinishing()) {
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
        super.onPause();
    }

    private void scanLeDevice(final boolean enable) {
        final BluetoothLeScanner mBLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
        if (enable) {
            // Stops scanning after a pre-defined scan period
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mProgressBar.setVisibility(View.GONE);
                    mBLeScanner.stopScan(mScanCallback);
                    mRefreshLayout.setRefreshing(false);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mProgressBar.setVisibility(View.VISIBLE);
            mDeviceList.clear();
            mAdapter.notifyDataSetChanged();
            mBLeScanner.startScan(mScanCallback);
        } else {
            mScanning = false;
            mBLeScanner.stopScan(mScanCallback);
            mRefreshLayout.setRefreshing(false);
        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            // RSSI = result.getRssi();
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            Log.d(LOG_TAG, "RSSI Value: ");
            Log.d(LOG_TAG, Integer.toString(result.getRssi()));
            BluetoothDevice btDevice = result.getDevice();
            if (btDevice.getName() != null && btDevice.getAddress() != null) {
                if (!mDeviceList.contains(btDevice)) {
                    Log.d(LOG_TAG, "Device found: " + btDevice.getName());
                    Log.d(LOG_TAG, btDevice.getAddress());
                    mDeviceList.addLast(btDevice);
                }
            }
            mAdapter.notifyDataSetChanged();
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    // Scan button's OnClick event
    public void startScan(View view) {
        if (featureCheck())
            scanLeDevice(true);
    }

    // Check if location services and bluetooth is enabled
    public boolean featureCheck() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            return false;
        }
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildLocationServiceAlert();
            return false;
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    2);
            return false;
        }
        return true;
    }

    private void buildLocationServiceAlert() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("This app requires access to location services. Enable location service?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                         startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }

}
