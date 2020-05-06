package com.example.bluetoothpracticetree;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.bluetoothpracticetree.utility.HostAdapter;
import com.example.bluetoothpracticetree.utility.UuidUtils;

import java.util.UUID;

/*
    This activity allows a client to search for an advertising host. Once a suitable host is found,
    the list of hosts will update, and the client can tap their name. Doing so will launch the
    WaitActivity, which connects the client to that host.
 */

public class JoinActivity extends AppCompatActivity {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 10000;

    private BluetoothAdapter bluetoothAdapter;
    private Handler handler;

    private ProgressBar progressBar;
    private HostAdapter hostAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join);

        // Handler provides a way to run a thread after a certain period of time
        handler = new Handler();

        // Define the onClickListener that gets applied to each host in the list
        HostAdapter.OnHostClickListener listener = new HostAdapter.OnHostClickListener() {
            @Override
            public void onItemClick(BluetoothDevice device) {
                // Start the WaitActivity for the clicked device, which connects to it
                Intent intent = new Intent(getApplicationContext(), WaitActivity.class);
                intent.putExtra(WaitActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
                finish();
                startActivity(intent);
            }
        };

        progressBar = findViewById(R.id.join_progress_bar);
        progressBar.setIndeterminate(true);
        RecyclerView recyclerView = findViewById(R.id.available_hosts_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        hostAdapter = new HostAdapter(listener);
        recyclerView.setAdapter(hostAdapter);
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));

        // Get a handle to the device's BluetoothManager and BluetoothAdapter
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Request user to enable Bluetooth if it is not already
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[] {Manifest.permission.ACCESS_FINE_LOCATION},
            0);
        }

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Start scanning for hosts
        scanLeDevice(true);
    }

    // TODO: Use BluetoothLeScanner instead of start/stopLeScan here
    private void scanLeDevice(boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined period
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    bluetoothAdapter.stopLeScan(leScanCallback);
                }
            }, SCAN_PERIOD);
            bluetoothAdapter.startLeScan(leScanCallback);
        } else {
            bluetoothAdapter.stopLeScan(leScanCallback);
        }
    }

    // Provide a callback when a BLE device is found as a result of the scan
    private BluetoothAdapter.LeScanCallback leScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    for (UUID uuid : UuidUtils.parseServiceUuids(scanRecord)) {
                        // If scanned device offers the custom service, it is a host
                        if (uuid.equals(UuidUtils.SERVICE)) {
                            // Add the host to the list and remove the loading bar
                            hostAdapter.addHostName(device);
                            hostAdapter.notifyDataSetChanged();
                            progressBar.setVisibility(View.GONE);
                        }
                    }
                }
            });
        }
    };

    @Override
    protected void onStop() {
        scanLeDevice(false);
        super.onStop();
    }
}
