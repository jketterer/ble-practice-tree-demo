package com.example.bluetoothpracticetree;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bluetoothpracticetree.utility.BleServerService;

/*
    This activity starts the BLE Server on the host user's device, and begins advertising for
    clients. When all clients have connected, the server sends a signal that this activity reads,
    and allows the host to start the race for all clients.
 */

public class HostActivity extends AppCompatActivity {
    private static final String TAG =  HostActivity.class.getSimpleName();

    private BleServerService serverService;

    private TextView progressLabel;
    private ProgressBar progressBar;
    private Button beginButton;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);

        progressLabel = findViewById(R.id.progress_label);
        progressBar = findViewById(R.id.host_progress_bar);
        progressBar.setIndeterminate(true);
        beginButton = findViewById(R.id.begin_button);

        // This button is only active once all clients have connected
        beginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Tell server to start race
                serverService.startRace();

                // Send server racer id (4) to RaceActivity, and start it
                Intent intent = new Intent(getApplicationContext(), RaceActivity.class);
                intent.putExtra("RACER_ID", 4);
                finish();
                startActivity(intent);
            }
        });
        beginButton.setEnabled(false);

        // Bind server service to this activity
        Intent gattServerServiceIntent = new Intent(this, BleServerService.class);
        bindService(gattServerServiceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    // Define some callbacks when the server service connects/disconnects
    private final ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            // Get a handle for the server service
            serverService = ((BleServerService.LocalBinder) service).getService();
            // Register a listener for service broadcasts
            registerReceiver(gattServerUpdateReceiver, makeGattUpdateIntentFilter());

            if (!serverService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            setDialIn();

            // Start advertising for clients immediately
            serverService.advertise();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            serverService = null;
        }
    };

    @Override
    protected void onStop() {
        unregisterReceiver(gattServerUpdateReceiver);
        if (serverService != null) {
            unbindService(serviceConnection);
        }
        super.onStop();
    }

    // Create a listener for broadcasts sent by the server service
    private final BroadcastReceiver gattServerUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            // If broadcasted update is the BEGIN_RACE_SIGNAL, and value is "begin",
            // start the RaceActivity for all users
            if (BleServerService.CLIENTS_CONNECTED.equals(action)) {
                String data = intent.getStringExtra(BleServerService.EXTRA_DATA);

                if ("connected".equals(data)) {
                    setRaceStart(true);
                } else {
                    setRaceStart(false);
                }
            }
        }
    };

    // Create an intent filter so the BroadcastReceiver only checks for specific broadcasts
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleServerService.CLIENTS_CONNECTED);
        return intentFilter;
    }

    // Set UI
    private void setRaceStart(boolean start) {
        progressBar.setVisibility(start ? View.INVISIBLE : View.VISIBLE);
        progressLabel.setText(!start ? "Waiting for others..." : "Ready to begin");
        beginButton.setEnabled(start);
    }

    // Send dial-in information for the host user to the server service
    private void setDialIn() {
        // Get dial-in value from settings
        SharedPreferences preferences = getSharedPreferences("RACE_PREFS", Context.MODE_PRIVATE);
        long dial = preferences.getLong("dial", 10000);

        serverService.setHostDial(Long.toString(dial));
    }
}
