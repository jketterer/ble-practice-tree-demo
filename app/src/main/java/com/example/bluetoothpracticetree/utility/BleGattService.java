package com.example.bluetoothpracticetree.utility;

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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.Arrays;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

/*
    This service handles all BLE operations for the client device. It provides methods to connect
    to a host device, as well as reading, writing, and subscribing to characteristics. Since
    BLE is asynchronous, many callbacks are used to provide updates to the client. This service
    uses broadcasts to communicate with the activity it is bound to.
 */

public class BleGattService extends Service {
    private final static String TAG = BleGattService.class.getSimpleName();

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetoothpracticetree.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetoothpracticetree.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetoothpracticetree.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetoothpracticetree.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetoothpracticetree.EXTRA_DATA";
    public final static String CHARACTERISTIC_UUID =
            "com.example.bluetoothpracticetree.CHARACTERISTIC_UUID";
    public final static String BEGIN_RACE_ACTIVITY =
            "com.example.bluetoothpracticetree.BEGIN_RACE_ACTIVITY";
    public final static String RACER_ID =
            "com.example.bluetoothpracticetree.RACER_ID";
    public final static String START_RACE =
            "com.example.bluetoothpracticetree.START_RACE";
    public final static String DIAL_UPDATE =
            "com.example.bluetoothpracticetree.DIAL_UPDATE";
    public final static String RT_UPDATE =
            "com.example.bluetoothpracticetree.RT_UPDATE";
    public final static String STAGE_UPDATE =
            "com.example.bluetoothpracticetree.STAGE_UPDATE";
    public final static String RACE_FINISHED =
            "com.example.bluetoothpracticetree.RACE_FINISHED";

    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private String bluetoothDeviceAddress;
    private BluetoothGatt bluetoothGatt;

    private Queue<Runnable> commandQueue = new LinkedBlockingQueue<>();
    private boolean commandQueueBusy;
    private Handler bleHandler;

    private IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        public BleGattService getService() {
            return BleGattService.this;
        }
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

        bleHandler = new Handler();

        return true;
    }

    // This method connects the local device to a remote device using the passed device address
    public void connect(final String address) {
        if (bluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return;
        }
        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return;
        }

        // Connect to the host device
        bluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        bluetoothDeviceAddress = address;
    }

    // This method returns a service provided by the host device, identified by UUID
    public BluetoothGattService getServiceByUuid(UUID uuid) {
        if (bluetoothGatt == null) return null;

        return bluetoothGatt.getService(uuid);
    }

    // This method reads a specific characteristic from the server
    public void readCharacteristic(final BluetoothGattCharacteristic characteristic) {
        if(bluetoothGatt == null) {
            Log.e(TAG, "ERROR: Gatt is 'null', ignoring read request");
            return;
        }
        if(characteristic == null) {
            Log.e(TAG, "ERROR: Characteristic is 'null', ignoring read request");
            return;
        }
        if((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0 ) {
            Log.e(TAG, "ERROR: Characteristic cannot be read");
            return;
        }

        // Enqueue the read command now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                // Read the characteristic
                if(!bluetoothGatt.readCharacteristic(characteristic)) {
                    Log.e(TAG, String.format("ERROR: readCharacteristic failed for characteristic: %s", characteristic.getUuid()));
                    completedCommand();
                } else {
                    Log.d(TAG, String.format("reading characteristic <%s>", characteristic.getUuid()));
                }
            }
        });

        // Run the read command if enqueued successfully
        if(result) {
            nextCommand();
        } else {
            Log.e(TAG, "ERROR: Could not enqueue read characteristic command");
        }
    }

    // This method writes to a specific characteristic on the server
    public void writeCharacteristic(final BluetoothGattCharacteristic characteristic, final String value) {
        if(bluetoothGatt == null) {
            Log.e(TAG, "ERROR: Gatt is 'null', ignoring write request");
            return;
        }
        if(characteristic == null) {
            Log.e(TAG, "ERROR: Characteristic is 'null', ignoring write request");
            return;
        }
        if((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) == 0 ) {
            Log.e(TAG, "ERROR: Characteristic cannot be written");
            return;
        }

        // Enqueue the read command now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                // Write to the characteristic
                characteristic.setValue(value);
                if(!bluetoothGatt.writeCharacteristic(characteristic)) {
                    Log.e(TAG, String.format("ERROR: writeCharacteristic failed for characteristic: %s", characteristic.getUuid()));
                    completedCommand();
                } else {
                    Log.d(TAG, String.format("writing characteristic <%s>", characteristic.getUuid()));
                }
            }
        });

        // Run the write command if enqueued successfully
        if(result) {
            nextCommand();
        } else {
            Log.e(TAG, "ERROR: Could not enqueue write characteristic command");
        }
    }

    // Subscribe to changes to a specific characteristic on the server
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              final BluetoothGattDescriptor configDescriptor,
                                              final boolean enabled) {
        // Check if characteristic is valid
        if(characteristic == null) {
            Log.e(TAG, "ERROR: Characteristic is 'null', ignoring setNotify request");
            return;
        }

        // Get the CCC Descriptor for the characteristic
        if(configDescriptor == null) {
            Log.e(TAG, String.format("ERROR: Could not get CCC descriptor for characteristic %s", characteristic.getUuid()));
            return;
        }

        // Check if characteristic has NOTIFY or INDICATE properties and set the correct byte value to be written
        final byte[] value;
        int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
        } else if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
            value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE;
        } else {
            Log.e(TAG, String.format("ERROR: Characteristic %s does not have notify or indicate property", characteristic.getUuid()));
            return;
        }

        final byte[] finalValue = enabled ? value : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;

        // Queue Runnable to turn on/off the notification now that all checks have been passed
        boolean result = commandQueue.add(new Runnable() {
            @Override
            public void run() {
                // First set notification for Gatt object
                boolean result;
                result = bluetoothGatt.setCharacteristicNotification(configDescriptor.getCharacteristic(), enabled);
                if (!result) {
                    Log.e(TAG, String.format("ERROR: setCharacteristicNotification failed for descriptor: %s", configDescriptor.getUuid()));
                }

                // Then write to descriptor
                configDescriptor.setValue(finalValue);
                result = bluetoothGatt.writeDescriptor(configDescriptor);
                if (!result) {
                    Log.e(TAG, String.format("ERROR: writeDescriptor failed for descriptor: %s", configDescriptor.getUuid()));
                    completedCommand();
                }
            }
        });

        // Run subscribe command if enqueued successfully
        if(result) {
            nextCommand();
        } else {
            Log.e(TAG, "ERROR: Could not enqueue write command");
        }
    }


    // Define callbacks for various GATT responses
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        // Provide responses to connection state changes
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                int bondState = gatt.getDevice().getBondState();
                Log.i(TAG, "Bond state: " + bondState);
                intentAction = ACTION_GATT_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server. Status: " + status);

                // Discover services on device after a delay to avoid race conditions
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        bluetoothGatt.discoverServices();
                    }
                }, 500);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server. Status: " + status);
                close();
                broadcastUpdate(intentAction);
            }
        }

        // Provide responses to services being discovered
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Broadcast that services have been discovered
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                Log.w(TAG, "onServicesDiscovered received: " + status);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        // Provide responses for characteristic being read
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.w(TAG, "onCharacteristicRead(): " + status);
            // If read was a success, broadcast update with appropriate action
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (UuidUtils.BEGIN_RACE_ACTIVITY.equals(characteristic.getUuid())) {
                    broadcastUpdate(BleGattService.BEGIN_RACE_ACTIVITY, characteristic);
                } else if (UuidUtils.RACER_ID.equals(characteristic.getUuid())) {
                    broadcastUpdate(BleGattService.RACER_ID, characteristic);
                } else if (UuidUtils.RACER_1_DIAL.equals(characteristic.getUuid())
                        || UuidUtils.RACER_2_DIAL.equals(characteristic.getUuid())
                        || UuidUtils.RACER_3_DIAL.equals(characteristic.getUuid())
                        || UuidUtils.RACER_HOST_DIAL.equals(characteristic.getUuid())) {
                    broadcastUpdate(BleGattService.DIAL_UPDATE, characteristic);
                } else if (UuidUtils.RACER_1_RT.equals(characteristic.getUuid())
                        || UuidUtils.RACER_2_RT.equals(characteristic.getUuid())
                        || UuidUtils.RACER_3_RT.equals(characteristic.getUuid())
                        || UuidUtils.RACER_HOST_RT.equals(characteristic.getUuid())) {
                    broadcastUpdate(BleGattService.RT_UPDATE, characteristic);
                } else {
                    Log.w(TAG, "Broadcasting update: ACTION_DATA_AVAILABLE");
                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                }
            }
            completedCommand();
        }

        // Provide response for characteristic writes
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.w(TAG, "onCharacteristicRead(): " + status);
            completedCommand();
        }

        // Provide responses for subscribed characteristics being changed
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(UuidUtils.RACE_READY)) {
                broadcastUpdate(START_RACE);
            }

            if (UuidUtils.BEGIN_RACE_ACTIVITY.equals(characteristic.getUuid())) {
                broadcastUpdate(BEGIN_RACE_ACTIVITY, characteristic);
            }

            if (UuidUtils.RACER_1_STAGE.equals(characteristic.getUuid())) {
                broadcastUpdate(BleGattService.STAGE_UPDATE, "1" + Arrays.toString(characteristic.getValue()));
            } else if (UuidUtils.RACER_2_STAGE.equals(characteristic.getUuid())) {
                broadcastUpdate(BleGattService.STAGE_UPDATE, "2" + Arrays.toString(characteristic.getValue()));
            } else if (UuidUtils.RACER_3_STAGE.equals(characteristic.getUuid())) {
                broadcastUpdate(BleGattService.STAGE_UPDATE, "3" + Arrays.toString(characteristic.getValue()));
            } else if (UuidUtils.RACER_HOST_STAGE.equals(characteristic.getUuid())) {
                broadcastUpdate(BleGattService.STAGE_UPDATE, "4" + Arrays.toString(characteristic.getValue()));
            }

            if (UuidUtils.RACE_FINISHED.equals(characteristic.getUuid())) {
                broadcastUpdate(BleGattService.RACE_FINISHED);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
            Log.w(TAG, "onDescriptorWrite() status: " + status);
            completedCommand();
        }
    };

    // Send broadcast only containing action name
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    // Send broadcast containing action name and characteristic value
    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            intent.putExtra(EXTRA_DATA, new String(data));
        }

        sendBroadcast(intent);
    }

    // Send broadcast containing action and custom payload
    private void broadcastUpdate(final String action, String payload) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_DATA, payload);
        sendBroadcast(intent);
    }

    // Required for services
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    // Ensure BLE connection is closed so we can dispose of the resources
    public void close() {
        if (bluetoothGatt == null) {
            return;
        }
        bluetoothGatt.close();
        bluetoothGatt = null;
    }

    // Run the next command in the command queue
    private void nextCommand() {
        // If there is still a command being executed then bail out
        if(commandQueueBusy) {
            return;
        }

        // Check if we still have a valid gatt object
        if (bluetoothGatt == null) {
            Log.e(TAG, String.format("ERROR: GATT is 'null' for peripheral '%s', clearing command queue", bluetoothDeviceAddress));
            commandQueue.clear();
            commandQueueBusy = false;
            return;
        }

        // Execute the next command in the queue
        if (commandQueue.size() > 0) {
            final Runnable bluetoothCommand = commandQueue.peek();
            commandQueueBusy = true;

            bleHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Log.w(TAG, "Running command...");
                        bluetoothCommand.run();
                    } catch (Exception ex) {
                        Log.e(TAG, String.format("ERROR: Command exception for device '%s'", bluetoothDeviceAddress), ex);
                    }
                }
            });
        }
    }

    // Remove the last command from the command queue and start the next
    private void completedCommand() {
        Log.w(TAG, "Completed command");
        commandQueueBusy = false;
        commandQueue.poll();
        nextCommand();
    }
}
