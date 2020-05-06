package com.example.bluetoothpracticetree;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bluetoothpracticetree.utility.BleGattService;
import com.example.bluetoothpracticetree.utility.UuidUtils;

/*
    This activity provides a "waiting room" for the user while the host waits for all clients
    to connect. In the background, it establishes a connection to the BleGattService, which
    handles all BLE operations. This activity connects to the host, gets the racer ID for
    this user, sends the local dial-in information, and waits for the host's signal to start
    the RaceActivity.
 */

public class WaitActivity extends AppCompatActivity {
    private static final String TAG =  WaitActivity.class.getSimpleName();
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private BleGattService bluetoothLeService;
    private BluetoothGattService service;
    private TextView textView;
    private TextView racerIdLabel;
    private TextView racerIdHolder;
    private String deviceAddress;

    // This value identifies the user, and determines which characteristics to write to
    private int racerId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wait);

        ProgressBar progressBar = findViewById(R.id.wait_progress_bar);
        progressBar.setIndeterminate(true);

        textView = findViewById(R.id.wait_label);
        textView.setText(R.string.connecting);

        racerIdLabel = findViewById(R.id.racer_id_label);
        racerIdLabel.setVisibility(View.INVISIBLE);

        racerIdHolder = findViewById(R.id.racer_id_holder);

        // Get host device address from previous activity
        final Intent intent = getIntent();
        deviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Start the local BLE service and bind it to this activity
        Intent gattServiceIntent = new Intent(this, BleGattService.class);
        bindService(gattServiceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    // Define some callbacks when the BLE service connects/disconnects
    private final ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            // Get a handle to the BLE service
            bluetoothLeService = ((BleGattService.LocalBinder) service).getService();
            // Register a listener for service broadcasts
            registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
            if (!bluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the host device
            bluetoothLeService.connect(deviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bluetoothLeService = null;
        }
    };

    @Override
    protected void onStop() {
        unregisterReceiver(gattUpdateReceiver);
        unbindService(serviceConnection);
        super.onStop();
    }

    // Create a listener for broadcasts sent by the BLE service
    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BleGattService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.d(TAG, "Device connected");
            }
            // Once services have been discovered for the host, the BLE service sends this broadcast
            else if (BleGattService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                Log.d(TAG, "Services discovered.");
                waitForHost();

                // Get the custom service from the host device
                service = bluetoothLeService.getServiceByUuid(UuidUtils.SERVICE);
                if (service != null) {
                    // Get various characteristics/descriptors from the host device
                    BluetoothGattCharacteristic clientsConnected = service.getCharacteristic(UuidUtils.BEGIN_RACE_ACTIVITY);
                    BluetoothGattCharacteristic racerId = service.getCharacteristic(UuidUtils.RACER_ID);
                    BluetoothGattCharacteristic raceReady = service.getCharacteristic(UuidUtils.RACE_READY);
                    BluetoothGattDescriptor clientsConnectedDescriptor  = clientsConnected.getDescriptor(UuidUtils.CCCD);
                    BluetoothGattDescriptor raceReadyDescriptor  = raceReady.getDescriptor(UuidUtils.CCCD);

                    // Subscribe to notifications for certain characteristics
                    bluetoothLeService.setCharacteristicNotification(clientsConnected, clientsConnectedDescriptor, true);
                    bluetoothLeService.setCharacteristicNotification(raceReady, raceReadyDescriptor, true);

                    // Read the racer ID for this device from the server
                    bluetoothLeService.readCharacteristic(racerId);
                }
            }
            // When host begins the racer after clients connect, this broadcast gets sent
            else if (BleGattService.BEGIN_RACE_ACTIVITY.equals(action)) {
                String data = intent.getStringExtra(BleGattService.EXTRA_DATA);
                if ("begin".equals(data)) {
                    beginRace();
                }
            }
            // When the BLE service gets a response from the racer ID read, this broadcast gets sent
            else if (BleGattService.RACER_ID.equals(action)) {
                String data = intent.getStringExtra(BleGattService.EXTRA_DATA);
                setRacerId(data);
            }
            // Alert the user if they lose connection
            else if (BleGattService.ACTION_GATT_DISCONNECTED.equals(action)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                });

                AlertDialog dialog = builder
                        .setMessage("Connection failed. Try again.")
                        .create();
                dialog.show();
            }
        }
    };

    // Create an intent filter so the BroadcastReceiver only checks for specific broadcasts
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleGattService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BleGattService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BleGattService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BleGattService.BEGIN_RACE_ACTIVITY);
        intentFilter.addAction(BleGattService.RACER_ID);
        return intentFilter;
    }

    // Update UI
    private void waitForHost() {
        textView.setText(R.string.wait_for_host);
    }

    // Start the RaceActivity, and pass on the racer ID
    private void beginRace() {
        Intent intent = new Intent(getApplicationContext(), RaceActivity.class);
        intent.putExtra("RACER_ID", racerId);
        finish();
        startActivity(intent);
    }

    // Sets the local racer ID
    private void setRacerId(String idString) {
        racerId = Integer.parseInt(idString);
        racerIdHolder.setText(idString);
        racerIdLabel.setVisibility(View.VISIBLE);
        sendDialIn();
    }

    // Send dial-in information for the local user to the server
    private void sendDialIn() {
        BluetoothGattCharacteristic dialCharacteristic;

        // Make sure to only write to the correct characteristic, based on racer ID
        if (racerId == 1) {
            dialCharacteristic = service.getCharacteristic(UuidUtils.RACER_1_DIAL);
        } else if (racerId == 2) {
            dialCharacteristic = service.getCharacteristic(UuidUtils.RACER_2_DIAL);
        } else {
            dialCharacteristic = service.getCharacteristic(UuidUtils.RACER_3_DIAL);
        }

        // Get dial-in value from settings
        SharedPreferences preferences = getSharedPreferences("RACE_PREFS", Context.MODE_PRIVATE);
        long dial = preferences.getLong("dial", 10000);
        bluetoothLeService.writeCharacteristic(dialCharacteristic, Long.toString(dial));
    }
}
