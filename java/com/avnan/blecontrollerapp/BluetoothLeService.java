package com.avnan.blecontrollerapp;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;
import java.util.UUID;

public class BluetoothLeService extends Service {
    private static final String LOG_TAG = BluetoothLeService.class.getSimpleName();
    // Things that could belong within a Service
    // *******************************************************************************
    // Intent ACTION Strings
    // Bluetooth Adapter, Manager objects
    // The BluetoothGattCallback method
    // The BroadcastUpdate method
    // Connecting, disconnecting, and cancelling connection attempts should be done in the service

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;

    private int mConnectionState = STATE_DISCONNECTED;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public static final String ACTION_GATT_CONNECTED =
            BuildConfig.APPLICATION_ID + ".ACTION_GATT_CONNECTED";
    public static final String ACTION_GATT_DISCONNECTED =
            BuildConfig.APPLICATION_ID + ".ACTION_GATT_DISCONNECTED";
    public static final String ACTION_GATT_SERVICES_DISCOVERED =
            BuildConfig.APPLICATION_ID + ".ACTION_GATT_SERVICES_DISCOVERED";
    public static final String ACTION_DATA_AVAILABLE =
            BuildConfig.APPLICATION_ID + ".ACTION_DATA_AVAILABLE";

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            // Callback indicating when a GATT client has connected/disconnected from a GATT server
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                boolean rssiStatus = mBluetoothGatt.readRemoteRssi();
                broadcastUpdate(intentAction);
                // Attempt to discover the services after a successful connection
                mBluetoothGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                broadcastUpdate(intentAction);
            } else if ((status == 8 && newState == 0) || (status == 133 && newState == 0)) {
                gatt.disconnect();
                gatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Service discovery successful");
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(LOG_TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            // Callback triggered as a result of a remote characteristic notification
            Log.d(LOG_TAG, "Triggered as a result of a remote notification");
            String receivedValue = new String(characteristic.getValue());
            Log.d(LOG_TAG, receivedValue);
            if (receivedValue.equals("rt2,1\r\n")) {
                broadcastUpdate(ACTION_DATA_AVAILABLE);
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, String.format("BluetoothGatt ReadRssi[%d]", rssi));
            }
        }
    };

    private void broadcastUpdate(final String action) {
        Log.d(LOG_TAG, "Broadcasting " + action);
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, make sure BluetoothGatt.close() is called
        // to ensure resources are cleaned up properly. close() is invoked when the UI
        // is disconnected from the service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    // Initializes a reference to the local Bluetooth adapter.
    // Return true if the initialization is successful
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through BluetoothManager
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.d(LOG_TAG, "Unable to initialize BluetoothManager");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.d(LOG_TAG, "Unable to obtain a BluetoothAdapter");
            return false;
        }

        return true;
    }

    // Connects to the GATT server hosted on the ESP32
    // Return true if the connection is initiated successfully
    // Keep in mind, we are connecting to a particular BluetoothDevice object,
    //  obtained through the user's selection from the recycler view's onClick
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.d(LOG_TAG, "BluetoothAdapter not initialized or unspecified address");
            return false;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.d(LOG_TAG, "Device not found. Unable to connect");
            return false;
        }

        // Directly connect to the device
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(LOG_TAG, "Trying to create a new connection");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    // Disconnects an existing connection or cancels a pending connection
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.d(LOG_TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    // After using a given BLE device, the app must call this method to ensure resources are
    // released properly
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    // Request a read on a given characteristic
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.d(LOG_TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    // Write to a given characteristic
    public boolean writeToCharacteristic(BluetoothGattCharacteristic characteristic, String command) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.d(LOG_TAG, "BluetoothAdapter not initialized");
            return false;
        }
        // Have to convert a string into a byte array first
        // then set the value of the characteristic before requesting
        // a write to it
        byte[] commandBytes = command.getBytes();
        characteristic.setValue(commandBytes);
        return mBluetoothGatt.writeCharacteristic(characteristic);
    }

    // Enables or disables notifications on a given characteristic
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.d(LOG_TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        Log.d(LOG_TAG, "Characteristic notification set.");
    }

    // Retrieves a list of supported GATT services on the connected device
    // Invoked only after BluetoothGatt#discoverServices() is successful
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

}
