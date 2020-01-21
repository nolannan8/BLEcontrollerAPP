package com.avnan.blecontrollerapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.avnan.blecontrollerapp.ScanListAdapter.EXTRA_DEVICE;

public class DeviceDetailsActivity extends AppCompatActivity {
    // The command string format
    // rtX,SS,TM,ID\r\n
    // rt1, rt2, rt3
    // SS = Signal strength
    // TM = Timer value (00, 20, 40, 60)
    // ID = 1 to 5
    // \r\n - needs to be added to the end of the string

    private static final String LOG_TAG = DeviceDetailsActivity.class.getSimpleName();

    private BluetoothLeService mBluetoothLeService;
    private BluetoothDevice mBluetoothDevice;

    private BluetoothGattService mMainService;
    private BluetoothGattCharacteristic mControlInput;
    private BluetoothGattCharacteristic mControlAcknowledgement;

    private String mTimerCommandString;

    private boolean isCorrectDevice = false;
    private boolean isCountingDown = false;

    private int mTimerValue;
    private int shortAnimationDuration;
    private int longAnimationDuration;

    private TextView mDeviceNameTitle;
    private TextView mDeviceStatLabel;

    private TextView mCountdownDisplay;

    private Button mStartTimerBttn;
    private Button mStopTimerBttn;

    private RadioGroup mFanControlRadioGroup;

    private CountDownTimer mFanTimer;

    private ImageButton mConnectBttn;
    private ImageButton mDisconnectBttn;

    // Code to manage Service lifecycle
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) iBinder).getService();
            if (!mBluetoothLeService.initialize()) {
                finish();
            }
            // Automatically connect to the device upon successful start-up initialization
            showConnectingState();
            mBluetoothLeService.connect(mBluetoothDevice.getAddress());
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case BluetoothLeService.ACTION_GATT_CONNECTED:
                        break;
                    case BluetoothLeService.ACTION_DATA_AVAILABLE:
                        Log.d(LOG_TAG, "Received a notification from the ESP");
                        if (isCountingDown)
                            startFanTimer(mTimerValue);
                        break;
                    case BluetoothLeService.ACTION_GATT_DISCONNECTED:
                        if (!isCountingDown)
                            clearTimerDisplay();
                        showDisconnectedState();
                        toggleFanTimerControl(false);
                        break;
                    case BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED:
                        getGattProfile(mBluetoothLeService.getSupportedGattServices());
                        showConnectedState();
                        if (isCorrectDevice) {
                            // Enable the Fan Timer Control
                            toggleFanTimerControl(true);
                        }

                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_details);
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);

        mDeviceStatLabel = findViewById(R.id.label_device_status);
        mDeviceNameTitle = findViewById(R.id.title_device_name);

        mStartTimerBttn = findViewById(R.id.button_start_timer);
        mFanControlRadioGroup = findViewById(R.id.radio_group_bt_timer);
        mStopTimerBttn = findViewById(R.id.button_stop_timer);
        mCountdownDisplay = findViewById(R.id.text_time_remaining);

        mConnectBttn = findViewById(R.id.button_connect);
        mDisconnectBttn = findViewById(R.id.button_disconnect);

        Intent detailsIntent = getIntent();
        mBluetoothDevice = detailsIntent.getExtras().getParcelable(EXTRA_DEVICE);
        mDeviceNameTitle.setText(mBluetoothDevice.getName());

        shortAnimationDuration = getResources().getInteger(
                android.R.integer.config_shortAnimTime);

        longAnimationDuration = getResources().getInteger(
                android.R.integer.config_longAnimTime);
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        if (isFinishing()) {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mGattUpdateReceiver);
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        super.onDestroy();
    }

    /*
    * ALL UI RELATED FUNCTIONS GO HERE
    * **********************************************************************************************
    * */

    // Functions that toggle UI elements
    // - show/hide timer countdown
    // - show/hide countdown loading spinner
    // - show/hide timer menu (20, 40, 60 minute selection)
    // - show/hide the loading screen (when first connecting to device)
    // - show disconnected/connected state

    // Show the countdown timer label and the cancel button when enable is true (hide when false)
    // Essentially, if enable is true, this function should display the remaining time to the user
    // and give them the option to cancel

//    public void showCountdownLoading() {
//        mTimerProgressBar.setVisibility(View.VISIBLE);
//    }
//    public void hideCountdownLoading() {
//        mTimerProgressBar.setVisibility(View.INVISIBLE);
//    }

    // Show the timer menu options. 20, 40, or 60 minutes. This menu contains the possible
    // options for the user to set the timer for.
    public void toggleFanTimerControl(final boolean enable) {
        // Clear the radio group selection
        mFanControlRadioGroup.clearCheck();
        for (int i = 0; i < mFanControlRadioGroup.getChildCount(); i++) {
            mFanControlRadioGroup.getChildAt(i).setEnabled(enable);
        }
        mStartTimerBttn.setEnabled(false);
    }

    // Toggle the 'Connect' and 'Disconnect' buttons according to the current state of the device
    // Eg. If the device is connected, disable 'Connect' and enable the 'Disconnect' option
    public void showConnectedState() {
        mDeviceStatLabel.setText(R.string.connected);
        mDisconnectBttn.setEnabled(true);
        mConnectBttn.setEnabled(false);

        if (isCorrectDevice)
            mStopTimerBttn.setEnabled(true);
    }
    public void showDisconnectedState() {
        mDeviceStatLabel.setText(R.string.disconnected);
        mDisconnectBttn.setEnabled(false);
        mConnectBttn.setEnabled(true);

        mStopTimerBttn.setEnabled(false);
    }
    public void showConnectingState() {
        mDeviceStatLabel.setText(R.string.connecting);
        mConnectBttn.setEnabled(true);
        mDisconnectBttn.setEnabled(true);

        mStopTimerBttn.setEnabled(false);
    }

    // ***************************************** On Clicks *****************************************
    public void onRadioButtonClicked(final View view) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                stopFanTimer();
                clearTimerDisplay();
                mStartTimerBttn.setEnabled(true);
                switch (view.getId()) {
                    case R.id.radio_bttn_twenty:
                        mTimerCommandString = "rt3,FF,20,1\r\n";
                        mTimerValue = 20*60;
                        mCountdownDisplay.setText(R.string.twenty);
                        break;
                    case R.id.radio_bttn_forty:
                        mTimerCommandString = "rt3,FF,40,1\r\n";
                        mTimerValue = 40*60;
                        mCountdownDisplay.setText(R.string.forty);
                        break;
                    case R.id.radio_bttn_sixty:
                        mTimerCommandString = "rt3,FF,60,1\r\n";
                        mTimerValue = 60*60;
                        mCountdownDisplay.setText(R.string.sixty);
                        break;
                }
                // Set the mCountdownDisplay value
            }
        });
    }
    public void startFanOverride(View view) {
        isCountingDown = true;
        writeToDevice(mTimerCommandString);
        // Disable the Fan Timer Control
        toggleFanTimerControl(false);
    }
    public void stopFanOverride(View view) {
        // The function called when the 'Stop' button is pressed
        isCountingDown = false;
        mTimerCommandString = "rt3,FF,00,1\r\n";
        // Send the stop command to the control unit - make sure the fan stops
        writeToDevice(mTimerCommandString);
        // Toggle the fan timer control on
        toggleFanTimerControl(true);
        // Call the function responsible for cancelling the android timer object
        stopFanTimer();
        // Clear the ticking timer display
        clearTimerDisplay();
    }

    public void startFanTimer(int timerValue) {
        // Called when the start timer acknowledgement is successful
        if (mFanTimer == null) {
            Log.i(LOG_TAG, "Instantiating new timer");
            toggleFanTimerControl(false);
            mFanTimer = new CountDownTimer(timerValue*1000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    long secondsUntilFinished = millisUntilFinished / 1000;
                    long minutes = secondsUntilFinished / 60;
                    long seconds = secondsUntilFinished % 60;
                    mCountdownDisplay.setText(String.format(Locale.CANADA, "%02d:%02d", minutes, seconds));
                    Log.i(LOG_TAG, String.format(Locale.CANADA, "%02d:%02d", minutes, seconds));
                }

                @Override
                public void onFinish() {
                    // Disable the Remaining Time cancel button
                    // Enable the Fan Timer Control
                    Log.i(LOG_TAG, "Done");
                    toggleFanTimerControl(true);
                    clearTimerDisplay();
                    mTimerValue = 0;
                }
            }.start();
        }
    }
    public void stopFanTimer() {
        if (mFanTimer != null) {
            mFanTimer.cancel();
            mFanTimer = null;
            Log.i(LOG_TAG, "Made it null");
        }
        mTimerValue = 0;
    }
    public void clearTimerDisplay() {
        mCountdownDisplay.setText(R.string.text_timer_blank);
    }
    // *********************************************************************************************
    // *********************************************************************************************

    public void connectDevice(View view) {
        if (mBluetoothLeService != null) {
            showConnectingState();
            mBluetoothLeService.connect(mBluetoothDevice.getAddress());
        }
    }

    public void disconnectDevice(View view) {
        if (mBluetoothLeService != null) {
            showDisconnectedState();
            mBluetoothLeService.disconnect();
        }
    }

    // Referencing the required profile (the services, characteristics, and descriptors)
    private void getGattProfile(List<BluetoothGattService> gattServices) {
        for (BluetoothGattService gattService : gattServices) {
            if (gattService.getUuid().equals(UUID.fromString(GattAttributes.HRV_CONTROL_SERV_UUID))) {
                mMainService = gattService;
                mControlInput = gattService.getCharacteristic(UUID.fromString(GattAttributes.HRV_CONTROL_CHAR_UUID));
                mControlAcknowledgement = gattService.getCharacteristic(UUID.fromString(GattAttributes.CONTROL_ACKNOWLEDGE_UUID));
                mBluetoothLeService.setCharacteristicNotification(mControlAcknowledgement, true);
            }
        }
        if (mMainService != null && mControlInput != null) {
            isCorrectDevice = true;
        }
    }

    // No UI components. Can run within the service / work thread
    public void writeToDevice(String str) {
        if (mControlInput == null) {
            return;
        }
        mBluetoothLeService.writeToCharacteristic(mControlInput, str);
    }

    // Just setting intent filter variables
    private static IntentFilter makeGattUpdateIntentFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        filter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        filter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        filter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        return filter;
    }

}
