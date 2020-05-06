package com.example.bluetoothpracticetree.utility;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/*
    This service establishes the BLE GATT server that runs on the host device and client devices
    connect to. It creates and maintains the custom service and characteristics that hold the
    data needed for the app to function. It provides methods for clients (and the host device) to
    read, write, and subscribe to these characteristics. This service uses broadcasts to communicate
    with the local host device.
 */

public class BleServerService extends Service {
    private static final String TAG = BleServerService.class.getSimpleName();
    private static final int ADVERTISE_TIME = 50000;

    public final static String CLIENTS_CONNECTED =
            "com.example.bluetoothpracticetree.CLIENTS_CONNECTED";
    public final static String BEGIN_RACE_SIGNAL =
            "com.example.bluetoothpracticetree.BEGIN_RACE_SIGNAL";
    public final static String EXTRA_DATA =
            "com.example.bluetoothpracticetree.EXTRA_DATA";
    public final static String CHARACTERISTIC_UUID =
            "com.example.bluetoothpracticetree.CHARACTERISTIC_UUID";
    public final static String DIAL_UPDATE =
            "com.example.bluetoothpracticetree.DIAL_UPDATE";
    public final static String RT_UPDATE =
            "com.example.bluetoothpracticetree.RT_UPDATE";
    public final static String STAGE_UPDATE =
            "com.example.bluetoothpracticetree.STAGE_UPDATE";
    public final static String START_RACE =
            "com.example.bluetoothpracticetree.START_RACE";
    public final static String RACE_FINISHED =
            "com.example.bluetoothpracticetree.RACE_FINISHED";

    private final int MAX_CLIENTS = 1;

    private IBinder binder = new LocalBinder();

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothManager bluetoothManager;
    private BluetoothGattServer bluetoothGattServer;
    private List<BluetoothDevice> devices;
    private String deviceName;

    private boolean raceDone = true;
    private boolean isStaging = false;

    private BluetoothGattCharacteristic beginRaceActivity;
    private BluetoothGattCharacteristic racerId;
    private BluetoothGattCharacteristic racerHostDial;
    private BluetoothGattCharacteristic racer1Stage;
    private BluetoothGattCharacteristic racer2Stage;
    private BluetoothGattCharacteristic racer3Stage;
    private BluetoothGattCharacteristic racerHostStage;
    private BluetoothGattCharacteristic racer1Rt;
    private BluetoothGattCharacteristic racer2Rt;
    private BluetoothGattCharacteristic racer3Rt;
    private BluetoothGattCharacteristic racerHostRt;
    private BluetoothGattCharacteristic raceReady;
    private BluetoothGattCharacteristic raceFinished;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class LocalBinder extends Binder {
        public BleServerService getService() {
            return BleServerService.this;
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (bluetoothManager == null) {
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        devices = new ArrayList<>();

        // Get device name from settings
        SharedPreferences sharedPref = getSharedPreferences("RACE_PREFS", Context.MODE_PRIVATE);
        deviceName = sharedPref.getString("name", "Default");

        // Start the BLE GATT server
        startGattServer();
        return true;
    }

    // This method opens a BLE GATT server on the host device
    private void startGattServer() {
        bluetoothGattServer = bluetoothManager.openGattServer(this, mGattServerCallback);
        if (bluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
            return;
        }

        // Add the custom service, which contains the necessary characteristics
        bluetoothGattServer.addService(createGattService());
    }

    // The method creates a BluetoothGattService, which contains the characteristics used by the app
    private BluetoothGattService createGattService() {
        BluetoothGattService service = new BluetoothGattService(UuidUtils.SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        beginRaceActivity = new BluetoothGattCharacteristic(UuidUtils.BEGIN_RACE_ACTIVITY,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattDescriptor config = new BluetoothGattDescriptor(UuidUtils.CCCD,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        beginRaceActivity.addDescriptor(config);


        racerId = new BluetoothGattCharacteristic(UuidUtils.RACER_ID,
                BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);

        BluetoothGattCharacteristic racer1Dial = new BluetoothGattCharacteristic(UuidUtils.RACER_1_DIAL,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattCharacteristic racer2Dial = new BluetoothGattCharacteristic(UuidUtils.RACER_2_DIAL,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattCharacteristic racer3Dial = new BluetoothGattCharacteristic(UuidUtils.RACER_3_DIAL,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        racerHostDial = new BluetoothGattCharacteristic(UuidUtils.RACER_HOST_DIAL,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);

        racer1Stage = new BluetoothGattCharacteristic(UuidUtils.RACER_1_STAGE,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        racer1Stage.addDescriptor(new BluetoothGattDescriptor(UuidUtils.CCCD,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        racer2Stage = new BluetoothGattCharacteristic(UuidUtils.RACER_2_STAGE,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        racer2Stage.addDescriptor(new BluetoothGattDescriptor(UuidUtils.CCCD,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        racer3Stage = new BluetoothGattCharacteristic(UuidUtils.RACER_3_STAGE,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        racer3Stage.addDescriptor(new BluetoothGattDescriptor(UuidUtils.CCCD,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));
        racerHostStage = new BluetoothGattCharacteristic(UuidUtils.RACER_HOST_STAGE,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        racerHostStage.addDescriptor(new BluetoothGattDescriptor(UuidUtils.CCCD,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));

        racer1Rt = new BluetoothGattCharacteristic(UuidUtils.RACER_1_RT,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        racer2Rt = new BluetoothGattCharacteristic(UuidUtils.RACER_2_RT,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        racer3Rt = new BluetoothGattCharacteristic(UuidUtils.RACER_3_RT,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        racerHostRt = new BluetoothGattCharacteristic(UuidUtils.RACER_HOST_RT,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);

        raceReady = new BluetoothGattCharacteristic(UuidUtils.RACE_READY,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        config = new BluetoothGattDescriptor(UuidUtils.CCCD,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        raceReady.addDescriptor(config);

        raceFinished = new BluetoothGattCharacteristic(UuidUtils.RACE_FINISHED,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        raceFinished.addDescriptor(new BluetoothGattDescriptor(UuidUtils.CCCD,
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE));

        beginRaceActivity.setValue("wait");
        service.addCharacteristic(beginRaceActivity);

        service.addCharacteristic(racer1Dial);
        service.addCharacteristic(racer2Dial);
        service.addCharacteristic(racer3Dial);
        service.addCharacteristic(racerHostDial);
        service.addCharacteristic(racer1Stage);
        service.addCharacteristic(racer2Stage);
        service.addCharacteristic(racer3Stage);
        service.addCharacteristic(racerHostStage);
        service.addCharacteristic(racer1Rt);
        service.addCharacteristic(racer2Rt);
        service.addCharacteristic(racer3Rt);
        service.addCharacteristic(racerHostRt);

        service.addCharacteristic(racerId);
        service.addCharacteristic(raceReady);
        service.addCharacteristic(raceFinished);

        return service;
    }

    // This method begins advertising the host device for clients to scan
    public void advertise() {
        BluetoothLeAdvertiser advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        bluetoothAdapter.setName(deviceName);
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(ADVERTISE_TIME)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build();
        // The advertisement will contain the device name and the service UUID
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(UuidUtils.SERVICE))
                .build();

        if (advertiser != null)
            advertiser.startAdvertising(settings, data, advertiseCallback);
    }

    // Define callbacks for advertising success/failure
    private AdvertiseCallback advertiseCallback = new AdvertiseCallback() {

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.e(TAG, "Advertising failed, error code " + errorCode);
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.i(TAG, "Advertising successfully started");
        }
    };

    // This method informs all clients that the BEGIN_RACE_SIGNAL characteristic
    // has been set to "begin"
    public void startRace() {
        beginRaceActivity.setValue("begin");
        notifyDevices(beginRaceActivity);
        bluetoothAdapter.getBluetoothLeAdvertiser().stopAdvertising(advertiseCallback);
    }

    // This method notifies all connected devices that a characteristic has changed
    private void notifyDevices(BluetoothGattCharacteristic characteristic) {
        for (BluetoothDevice device : devices) {
            bluetoothGattServer.notifyCharacteristicChanged(device, characteristic, false);
        }
    }

    // This method allows the host device to set their dial-in
    public void setHostDial(String value) {
        racerHostDial.setValue(value);
    }

    // This method allows the host device to set their reaction time
    public void setHostRt(String value) {
        racerHostRt.setValue(value);
        notifyDevices(racerHostRt);
        checkForRaceFinished();
    }

    // This method allows the host device to set their stage flag
    public void setHostStage(String value) {
        racerHostStage.setValue(value);
        notifyDevices(racerHostStage);
        checkForAllStaged();
    }

    // This method returns the requested service gotten from the server
    public BluetoothGattService getServiceByUuid(UUID uuid) {
        return bluetoothGattServer.getService(uuid);
    }

    // This method allows the host device to read a characteristic value
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (UuidUtils.RACER_1_DIAL.equals(characteristic.getUuid())
                || UuidUtils.RACER_2_DIAL.equals(characteristic.getUuid())
                || UuidUtils.RACER_3_DIAL.equals(characteristic.getUuid())) {
            broadcastUpdate(BleServerService.DIAL_UPDATE, characteristic);
        } else if (UuidUtils.RACER_1_RT.equals(characteristic.getUuid())
                || UuidUtils.RACER_2_RT.equals(characteristic.getUuid())
                || UuidUtils.RACER_3_RT.equals(characteristic.getUuid())) {
            broadcastUpdate(BleServerService.RT_UPDATE, characteristic);
        }
    }

    // Ensure that all resources are released when the service is closed
    public void close() {
        for (BluetoothDevice device : devices) {
            bluetoothGattServer.cancelConnection(device);
        }
        bluetoothGattServer.close();
        bluetoothAdapter.getBluetoothLeAdvertiser().stopAdvertising(advertiseCallback);
        bluetoothGattServer = null;
        bluetoothAdapter = null;
    }

    // Provide responses when various requests are made to the server
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        // Provide responses when a device connects or disconnects from the server
        @Override
        public void onConnectionStateChange(final BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectDevice(device);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                disconnectDevice(device);
            }
        }

        // Send the appropriate response when a characteristic is read from
        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            Log.w(TAG, "Received characteristic read request from device " + device.getAddress());
            bluetoothGattServer.sendResponse(device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    characteristic.getValue());
        }

        // Send the appropriate response when a characteristic is written to
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            Log.w(TAG, "Received characteristic write request from device " + device.getAddress());
            Log.w(TAG, "Setting characteristic " + characteristic.getUuid() +" from value "
                    + characteristic.getStringValue(0) + " to value " + Arrays.toString(value));
            characteristic.setValue(value);
            bluetoothGattServer.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    null
            );

            // Inform the host device that a client has updated their stage flag
            if (UuidUtils.RACER_1_STAGE.equals(characteristic.getUuid())) {
                broadcastUpdate(STAGE_UPDATE, "1" + Arrays.toString(characteristic.getValue()));
                notifyDevices(characteristic);
            } else if (UuidUtils.RACER_2_STAGE.equals(characteristic.getUuid())) {
                broadcastUpdate(STAGE_UPDATE, "2" + Arrays.toString(characteristic.getValue()));
                notifyDevices(characteristic);
            } else if (UuidUtils.RACER_3_STAGE.equals(characteristic.getUuid())) {
                broadcastUpdate(STAGE_UPDATE, "3" + Arrays.toString(characteristic.getValue()));
                notifyDevices(characteristic);
            }

            checkForAllStaged();
            checkForRaceFinished();
        }

        // Send the appropriate response when a descriptor is written to
        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor, boolean preparedWrite,
                                             boolean responseNeeded, int offset, byte[] value) {
            Log.w(TAG, "Received descriptor write request from device " + device.getAddress());
            bluetoothGattServer.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    0,
                    null
            );
        }
    };

    private void connectDevice(BluetoothDevice device) {
        Log.w(TAG, "BluetoothDevice CONNECTED: " + device);

        // Add newly connected device to device list
        devices.add(device);
        // Increment racer ID
        updateRacerId();

        // If all clients are connected, notify
        if (devices.size() == MAX_CLIENTS) {
            // Send start signal to host activity
            broadcastUpdate(BleServerService.CLIENTS_CONNECTED, "connected");
        }
    }

    private void disconnectDevice(BluetoothDevice device) {
        Log.w(TAG, "BluetoothDevice DISCONNECTED: " + device);

        // Remove disconnected device from device list
        devices.remove(device);
        // Decrement racer ID
        updateRacerId();

        if (devices.size() < MAX_CLIENTS) {
            // Send wait signal to host activity
            broadcastUpdate(BleServerService.CLIENTS_CONNECTED, "not connected");
        }
    }

    // This method checks if all users are staged, and if so, notifies all users to start race
    private void checkForAllStaged() {
        if (!isStaging
                && Arrays.equals(racer1Stage.getValue(), "1".getBytes())
                && Arrays.equals(racer2Stage.getValue(), "1".getBytes())
//                && Arrays.equals(racer3Stage.getValue(), "1".getBytes())
                && Arrays.equals(racerHostStage.getValue(), "1".getBytes())) {
            isStaging = true;
            // Delaying a second check ensures all users are staged for 1.5 seconds
            new Handler(getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Arrays.equals(racer1Stage.getValue(), "1".getBytes())
                            && Arrays.equals(racer2Stage.getValue(), "1".getBytes())
//                          && Arrays.equals(racer3Stage.getValue(), "1".getBytes())
                            && Arrays.equals(racerHostStage.getValue(), "1".getBytes())) {
                        raceDone = false;
                        resetRts();

                        // Tell all clients to start race (drop trees)
                        raceReady.setValue("start");
                        notifyDevices(raceReady);

                        // Tell host device to start race
                        broadcastUpdate(START_RACE);

                        // Reset race ready characteristic
                        raceReady.setValue("stop");
                    }
                    isStaging = false;
                }
            }, 1500);
        }
    }

    // This method resets all reaction times so the server can determine when all users have
    // sent new reaction times
    private void resetRts() {
        String empty = "";
        racer1Rt.setValue(empty);
        racer2Rt.setValue(empty);
        racer3Rt.setValue(empty);
        racerHostRt.setValue(empty);
    }

    // This method checks if all users have sent their reaction times. If so, it broadcasts the
    // RACE_FINISHED characteristic, so each device can pull reaction times from the server
    private void checkForRaceFinished() {
        String empty = "";
        if (!raceDone
                && !Arrays.equals(empty.getBytes(), racer1Rt.getValue())
                && !Arrays.equals(empty.getBytes(), racer2Rt.getValue())
                && !Arrays.equals(empty.getBytes(), racer3Rt.getValue())
                && !Arrays.equals(empty.getBytes(), racerHostRt.getValue())) {
            // Set race finished flag to true and notify clients
            raceFinished.setValue("1");
            notifyDevices(raceFinished);

            // Notify host device
            broadcastUpdate(RACE_FINISHED);
            raceDone = true;
        } else {
            // Set race finished flag to false
            raceFinished.setValue("0");
        }
    }

    // Send broadcast containing action and custom payload
    private void broadcastUpdate(final String action, String payload) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA, payload);
        sendBroadcast(intent);
    }

    // Send broadcast containing action name and characteristic value
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            intent.putExtra(EXTRA_DATA, new String(data));
            intent.putExtra(CHARACTERISTIC_UUID, characteristic.getUuid().toString());
        }

        sendBroadcast(intent);
    }

    // Send broadcast only containing action name
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void updateRacerId() {
        String racerIdValue = Integer.toString(devices.size());
        racerId.setValue(racerIdValue);
    }
}
