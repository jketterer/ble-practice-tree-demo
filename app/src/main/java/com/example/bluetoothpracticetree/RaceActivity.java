package com.example.bluetoothpracticetree;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.bluetoothpracticetree.practicetree.Bulb;
import com.example.bluetoothpracticetree.practicetree.PracticeTree;
import com.example.bluetoothpracticetree.utility.BleGattService;
import com.example.bluetoothpracticetree.utility.BleServerService;
import com.example.bluetoothpracticetree.utility.UuidUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.TreeMap;

/*
    This activity is by far the most complex. It is important to note that this activity is used
    by both the host user and the client users, so checks need to be made before most operations.
    Additionally, the activity needs to differentiate between any one of four racers.

    This activity is responsible for performing the actual practice tree functions of the
    application. It sends and receives updates to and from the server from the other users,
    and responds accordingly. Initially, dial-in information is sent to the server and read back
    for the other users. Stage updates are sent to and from the server, based on whether or not the
    user holds down the stage button. Once all users are staged, this class performs all of the
    logic necessary for a practice tree, calculates the reaction time, sends that to the server,
    then reads and displays the results from the other users on the screen.
 */

public class RaceActivity extends AppCompatActivity {
    private BluetoothGattCharacteristic racer1Dial;
    private BluetoothGattCharacteristic racer2Dial;
    private BluetoothGattCharacteristic racer3Dial;
    private BluetoothGattCharacteristic racerHostDial;

    private BluetoothGattCharacteristic racer1Stage;
    private BluetoothGattCharacteristic racer2Stage;
    private BluetoothGattCharacteristic racer3Stage;
    private BluetoothGattCharacteristic racerHostStage;

    private BluetoothGattCharacteristic racer1Rt;
    private BluetoothGattCharacteristic racer2Rt;
    private BluetoothGattCharacteristic racer3Rt;
    private BluetoothGattCharacteristic racerHostRt;

    private PracticeTree tree1;
    private PracticeTree tree2;
    private PracticeTree tree3;
    private PracticeTree tree4;
    private PracticeTree localTree;

    private TextView rt1;
    private TextView rt2;
    private TextView rt3;
    private TextView rt4;
    private TextView localRt;

    private long dial1;
    private long dial2;
    private long dial3;
    private long dial4;

    private boolean isServer;
    private int racerId;

    private boolean raceStarted = false;
    private long startTime;
    private long reactionTime;
    private long rollout;

    BleServerService serverService;
    BleGattService bleGattService;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_race);

        Button stageButton = findViewById(R.id.stage_button);

        // Set listener for stage button
        stageButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch ( event.getAction() ) {
                    case MotionEvent.ACTION_DOWN: setStage(true);
                        break;
                    case MotionEvent.ACTION_UP: setStage(false);
                        calculateRt();
                        break;
                }
                return true;
            }
        });
        
        tree1 = new PracticeTree((Bulb) findViewById(R.id.prestage1),
                (Bulb) findViewById(R.id.stage1),
                (Bulb) findViewById(R.id.top_yellow1),
                (Bulb) findViewById(R.id.mid_yellow1),
                (Bulb) findViewById(R.id.bot_yellow1),
                (Bulb) findViewById(R.id.green1),
                (Bulb) findViewById(R.id.red1));
        tree2 = new PracticeTree((Bulb) findViewById(R.id.prestage2),
                (Bulb) findViewById(R.id.stage2),
                (Bulb) findViewById(R.id.top_yellow2),
                (Bulb) findViewById(R.id.mid_yellow2),
                (Bulb) findViewById(R.id.bot_yellow2),
                (Bulb) findViewById(R.id.green2),
                (Bulb) findViewById(R.id.red2));
        tree3 = new PracticeTree((Bulb) findViewById(R.id.prestage3),
                (Bulb) findViewById(R.id.stage3),
                (Bulb) findViewById(R.id.top_yellow3),
                (Bulb) findViewById(R.id.mid_yellow3),
                (Bulb) findViewById(R.id.bot_yellow3),
                (Bulb) findViewById(R.id.green3),
                (Bulb) findViewById(R.id.red3));
        tree4 = new PracticeTree((Bulb) findViewById(R.id.prestage4),
                (Bulb) findViewById(R.id.stage4),
                (Bulb) findViewById(R.id.top_yellow4),
                (Bulb) findViewById(R.id.mid_yellow4),
                (Bulb) findViewById(R.id.bot_yellow4),
                (Bulb) findViewById(R.id.green4),
                (Bulb) findViewById(R.id.red4));

        tree1.setPrestage(true);
        tree2.setPrestage(true);
        tree3.setPrestage(true);
        tree4.setPrestage(true);

        rt1 = findViewById(R.id.rt1);
        rt2 = findViewById(R.id.rt2);
        rt3 = findViewById(R.id.rt3);
        rt4 = findViewById(R.id.rt4);

        // Get racer ID from previous activity
        racerId = getIntent().getIntExtra("RACER_ID", 4);
        isServer = (racerId == 4);

        // Get rollout from settings
        SharedPreferences preferences = getSharedPreferences("RACE_PREFS", Context.MODE_PRIVATE);
        rollout = preferences.getLong("rollout", 0);

        // Bind the appropriate service to this activity
        if (isServer) {
            Intent gattServerServiceIntent = new Intent(this, BleServerService.class);
            bindService(gattServerServiceIntent, serverServiceConnection, BIND_AUTO_CREATE);
        } else {
            Intent gattServiceIntent = new Intent(this, BleGattService.class);
            bindService(gattServiceIntent, gattServiceConnection, BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop() {
        if (serverService != null) {
            unregisterReceiver(serverUpdateReceiver);
            unbindService(serverServiceConnection);
        } else if (bleGattService != null) {
            unregisterReceiver(gattUpdateReceiver);
            unbindService(gattServiceConnection);
        }
        serverService = null;
        bleGattService = null;
        super.onStop();
    }

    // This method is called whenever the user releases the stage button
    private void calculateRt() {
        // Only calculate reaction time if race has actually started
        if (raceStarted) {
            long endTime = System.currentTimeMillis();
            reactionTime = endTime - startTime - 1500 + rollout;

            if (reactionTime < 0) {
                localTree.goRed();
            }

            localRt.setText(formatRt(Long.toString(reactionTime)));
            sendRt();

            raceStarted = false;
        }
    }

    // This method sends the calculated reaction time to the appropriate characteristic on the server
    private void sendRt() {
        String text = Long.toString(reactionTime);
        if (isServer) {
            serverService.setHostRt(text);
        } else {
            switch (racerId) {
                case 1: bleGattService.writeCharacteristic(racer1Rt, text);
                    break;
                case 2: bleGattService.writeCharacteristic(racer2Rt, text);
                    break;
                case 3: bleGattService.writeCharacteristic(racer3Rt, text);
                    break;
            }
        }
    }

    // This method stores the correct views for the local user
    private void assignTree() {
        if (isServer) {
            localTree = tree4;
            localRt = rt4;
        } else {
            switch (racerId) {
                case 1: localTree = tree1;
                    localRt = rt1;
                    break;
                case 2: localTree= tree2;
                    localRt = rt2;
                    break;
                case 3: localTree = tree3;
                    localRt = rt3;
                    break;
            }
        }
    }

    // This method reads each dial-in from the server
    private void readDials() {
        // Get local dial-in from settings
        SharedPreferences preferences = getSharedPreferences("RACE_PREFS", Context.MODE_PRIVATE);
        long localDial = preferences.getLong("dial", 10000);

        // Only read the dial-ins for the other three users
        if (isServer) {
            serverService.readCharacteristic(racer1Dial);
            serverService.readCharacteristic(racer2Dial);
            serverService.readCharacteristic(racer3Dial);
            dial4 = localDial;
        } else {
            if (racerId != 1) {
                bleGattService.readCharacteristic(racer1Dial);
            } else { dial1 = localDial; }
            if (racerId != 2) {
                bleGattService.readCharacteristic(racer2Dial);
            } else { dial2 = localDial; }
            if (racerId != 3) {
                bleGattService.readCharacteristic(racer3Dial);
            } else { dial3 = localDial; }
            bleGattService.readCharacteristic(racerHostDial);
        }
    }

    // This method informs the server that the local user is staged
    private void setStage(boolean staged) {
        String value = staged ? "1" : "0";
        if (isServer) {
            serverService.setHostStage(value);
        } else {
            switch (racerId) {
                case 1: bleGattService.writeCharacteristic(racer1Stage, value);
                    break;
                case 2: bleGattService.writeCharacteristic(racer2Stage, value);
                    break;
                case 3: bleGattService.writeCharacteristic(racer3Stage, value);
                    break;
            }
        }

        // Tell the local UI to update
        localTree.setStage(staged);
    }

    // This method updates the UI based on whichever user has staged
    private void updateStage(int id, boolean staged) {
        if (id == 1) {
            tree1.setStage(staged);
        } else if (id == 2) {
            tree2.setStage(staged);
        } else if (id == 3) {
            tree3.setStage(staged);
        } else {
            tree4.setStage(staged);
        }
    }

    // This method begins the race, starting the bulb sequence with the correct handicaps
    private void dropTrees() {
        // Keep track of which users have identical dial-ins
        HashMap<Long, Long> duplicates = new HashMap<>();

        // Assign each dial-in to the correct racer ID
        HashMap<Long, Long> dialIdMap = new HashMap<>();
        dialIdMap.put(1L, dial1);
        dialIdMap.put(2L, dial2);
        dialIdMap.put(3L, dial3);
        dialIdMap.put(4L, dial4);

        // Place each dial-in inside a TreeMap
        TreeMap<Long, Long> dials = new TreeMap<>();
        for (long i = 1; i <= dialIdMap.size(); i++) {
            // If dial-in is not present in the TreeMap, insert it
            if (!dials.containsKey(dialIdMap.get(i))) {
                dials.put(dialIdMap.get(i), i);
            }
            // Otherwise, keep track of the two identical dial-ins
            else {
                duplicates.put(i, dials.get(dialIdMap.get(i)));
            }
        }

        // Create a queue that stores each dial-in from highest to lowest
        final LinkedList<Long> queue = new LinkedList();

        // Insert each dial-in into the queue, including the duplicates
        while (queue.size() != dialIdMap.size()) {
            long nextHighest = dials.lastEntry().getValue();
            queue.add(dials.pollLastEntry().getValue());
            if (duplicates.containsValue(nextHighest)) {
                for (Long key : duplicates.keySet()) {
                    if (duplicates.get(key) == nextHighest) {
                        queue.add(key);
                    }
                }
            }
        }

        // Assign each PracticeTree view to the correct racer ID
        final TreeMap<Long, PracticeTree> practiceTreeMap = new TreeMap<>();
        practiceTreeMap.put(1L, tree1);
        practiceTreeMap.put(2L, tree2);
        practiceTreeMap.put(3L, tree3);
        practiceTreeMap.put(4L, tree4);

        // Keep track of the highest dial-in
        long highestDial = dialIdMap.get(queue.peek());

        long size = queue.size();
        for (int i = 0; i < size; i++) {
            long delay = highestDial - dialIdMap.get(queue.peek());
            final long currentId = queue.peek();

            // Start the bulb sequence for each tree after the appropriate delay
            new Handler(getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Make sure to keep track of the time the local user's tree began
                    if (currentId == (long) racerId) {
                        startTime = System.currentTimeMillis();
                    }
                    PracticeTree currentTree = practiceTreeMap.get(currentId);
                    currentTree.dropTree();
                }
            }, delay);

            // Remove the current dial-in from the queue
            queue.poll();
        }
    }

    // Define some callbacks when the server service connects/disconnects
    private final ServiceConnection serverServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            // Get a handle to the server service and register a broadcast listener
            serverService = ((BleServerService.LocalBinder) service).getService();
            registerReceiver(serverUpdateReceiver, makeGattUpdateIntentFilter());

            // Get necessary characteristics from the server
            BluetoothGattService raceService = serverService.getServiceByUuid(UuidUtils.SERVICE);
            getServerCharacteristics(raceService);

            // Read dial-in information from the server
            readDials();
            assignTree();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            serverService = null;
        }
    };

    // Define some callbacks when the BLE service connects/disconnects
    private final ServiceConnection gattServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            // Get a handle to the BLE service and register a broadcast listener
            bleGattService = ((BleGattService.LocalBinder) service).getService();
            registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());

            // Get necessary characteristics from the server
            BluetoothGattService raceService = bleGattService.getServiceByUuid(UuidUtils.SERVICE);
            getServerCharacteristics(raceService);

            // Subscribe to necessary characteristics
            subscribeToStageNotifications();
            subscribeToRaceFinishedNotification(raceService);

            // Read dial-in information from the server
            readDials();
            assignTree();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            bleGattService = null;
        }
    };

    private void getServerCharacteristics(BluetoothGattService raceService) {
        racer1Dial = raceService.getCharacteristic(UuidUtils.RACER_1_DIAL);
        racer2Dial = raceService.getCharacteristic(UuidUtils.RACER_2_DIAL);
        racer3Dial = raceService.getCharacteristic(UuidUtils.RACER_3_DIAL);
        racerHostDial = raceService.getCharacteristic(UuidUtils.RACER_HOST_DIAL);
        racer1Stage = raceService.getCharacteristic(UuidUtils.RACER_1_STAGE);
        racer2Stage = raceService.getCharacteristic(UuidUtils.RACER_2_STAGE);
        racer3Stage = raceService.getCharacteristic(UuidUtils.RACER_3_STAGE);
        racerHostStage = raceService.getCharacteristic(UuidUtils.RACER_HOST_STAGE);
        racer1Rt = raceService.getCharacteristic(UuidUtils.RACER_1_RT);
        racer2Rt = raceService.getCharacteristic(UuidUtils.RACER_2_RT);
        racer3Rt = raceService.getCharacteristic(UuidUtils.RACER_3_RT);
        racerHostRt = raceService.getCharacteristic(UuidUtils.RACER_HOST_RT);
    }

    private void subscribeToStageNotifications() {
        if (racerId != 1) {
            BluetoothGattDescriptor racer1Descriptor = racer1Stage.getDescriptor(UuidUtils.CCCD);
            bleGattService.setCharacteristicNotification(racer1Stage, racer1Descriptor, true);
        }
        if (racerId != 2) {
            BluetoothGattDescriptor racer2Descriptor = racer2Stage.getDescriptor(UuidUtils.CCCD);
            bleGattService.setCharacteristicNotification(racer2Stage, racer2Descriptor, true);
        }
        if (racerId != 3) {
            BluetoothGattDescriptor racer3Descriptor = racer3Stage.getDescriptor(UuidUtils.CCCD);
            bleGattService.setCharacteristicNotification(racer3Stage, racer3Descriptor, true);
        }

        BluetoothGattDescriptor racerHostDescriptor = racerHostStage.getDescriptor(UuidUtils.CCCD);
        bleGattService.setCharacteristicNotification(racerHostStage, racerHostDescriptor, true);
    }

    private void subscribeToRaceFinishedNotification(BluetoothGattService service) {
        BluetoothGattCharacteristic raceFinished = service.getCharacteristic(UuidUtils.RACE_FINISHED);
        BluetoothGattDescriptor raceFinishedDescriptor = raceFinished.getDescriptor(UuidUtils.CCCD);
        bleGattService.setCharacteristicNotification(raceFinished, raceFinishedDescriptor, true);
    }

    // Create a listener for broadcasts sent by the server service
    private final BroadcastReceiver serverUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            // Once all users have staged, the server will notify each user,
            // and send this broadcast
            if (BleServerService.START_RACE.equals(action)) {
                startRace();
            }
            // The server will notify whenever a user has stage or unstaged,
            // and send this broadcast
            else if (BleServerService.STAGE_UPDATE.equals(action)) {
                parseStageData(intent);
            }
            // Once all users have sent their reaction times, the server will notify,
            // and send this broadcast
            else if (BleServerService.RACE_FINISHED.equals(action)) {
                readRtsFromServer();
            }
            // When the BLE service receives dial-in info from a read, and send this broadcast
            else if (BleServerService.DIAL_UPDATE.equals(action)) {
                updateDial(intent);
            }
            // When a user sends their reaction time to the server, it will notify,
            // and send this broadcast
            else if (BleServerService.RT_UPDATE.equals(action)) {
                updateRt(intent);
            }
        }
    };

    // Create a listener for broadcasts sent by the BLE service
    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            // Once all users have staged, the server will notify each user,
            // and the BLE service will send this broadcast
            if (BleGattService.START_RACE.equals(action)) {
                startRace();
            }
            // The server will notify whenever a user has stage or unstaged,
            // and the BLE service will send this broadcast
            else if (BleGattService.STAGE_UPDATE.equals(action)) {
                parseStageData(intent);
            }
            // Once all users have sent their reaction times, the server will notify,
            // and the BLE service will send this broadcast
            else if (BleGattService.RACE_FINISHED.equals(action)) {
                readRtsFromServer();
            }
            // When the BLE service receives dial-in info from a read, the BLE service
            // will send this broadcast
            else if (BleGattService.DIAL_UPDATE.equals(action)) {
                updateDial(intent);
            }
            // When a user sends their reaction time to the server, it will notify,
            // and the BLE service will send this broadcast
            else if (BleGattService.RT_UPDATE.equals(action)) {
                updateRt(intent);
            }
        }
    };

    // Create an intent filter so the BroadcastReceiver only checks for specific broadcasts
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BleServerService.STAGE_UPDATE);
        intentFilter.addAction(BleServerService.RACE_FINISHED);
        intentFilter.addAction(BleServerService.START_RACE);
        intentFilter.addAction(BleServerService.DIAL_UPDATE);
        intentFilter.addAction(BleServerService.RT_UPDATE);
        intentFilter.addAction(BleGattService.STAGE_UPDATE);
        intentFilter.addAction(BleGattService.RACE_FINISHED);
        intentFilter.addAction(BleGattService.START_RACE);
        intentFilter.addAction(BleGattService.DIAL_UPDATE);
        intentFilter.addAction(BleGattService.RT_UPDATE);
        return intentFilter;
    }

    // Start the actual race sequence
    private void startRace() {
        raceStarted = true;
        resetRts();
        dropTrees();
    }

    // Update UI
    private void resetRts() {
        rt1.setText("");
        rt2.setText("");
        rt3.setText("");
        rt4.setText("");
    }

    // This method parses the data from a STAGE_UPDATE broadcast and updates the correct view
    private void parseStageData(Intent intent) {
        String data = intent.getStringExtra(BleServerService.EXTRA_DATA);
        int id = Integer.parseInt(data.substring(0, 1));
        boolean status = data.substring(2, 4).equals("49");
        updateStage(id, status);
    }

    // This method reads all reaction times from the server
    private void readRtsFromServer() {
        if (isServer) {
            // Delay the server so we ensure the users have written
            new Handler(getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    serverService.readCharacteristic(racer1Rt);
                    serverService.readCharacteristic(racer2Rt);
                    serverService.readCharacteristic(racer3Rt);
                }
            }, 200);
        } else {
            if (racerId != 1) {
                bleGattService.readCharacteristic(racer1Rt);
            }
            if (racerId != 2) {
                bleGattService.readCharacteristic(racer2Rt);
            }
            if (racerId != 3) {
                bleGattService.readCharacteristic(racer3Rt);
            }
            bleGattService.readCharacteristic(racerHostRt);
        }
    }

    // Update the UI when dial-ins are received
    private void updateDial(Intent intent) {
        String charUuuid = intent.getStringExtra(BleGattService.CHARACTERISTIC_UUID);
        String data = intent.getStringExtra(BleServerService.EXTRA_DATA);

        if (UuidUtils.RACER_1_DIAL.toString().equals(charUuuid)) {
            dial1 = Long.parseLong(data);
        } else if (UuidUtils.RACER_2_DIAL.toString().equals(charUuuid)) {
            dial2 = Long.parseLong(data);
        } else if (UuidUtils.RACER_3_DIAL.toString().equals(charUuuid)) {
            dial3 = Long.parseLong(data);
        } else if (UuidUtils.RACER_HOST_DIAL.toString().equals(charUuuid)) {
            dial4 = Long.parseLong(data);
        }
    }

    // Update the UI when reaction times are received
    private void updateRt(Intent intent) {
        String charUuuid = intent.getStringExtra(BleGattService.CHARACTERISTIC_UUID);
        String data = intent.getStringExtra(BleServerService.EXTRA_DATA);

        if (UuidUtils.RACER_1_RT.toString().equals(charUuuid)) {
            rt1.setText(formatRt(data));
            if (Integer.parseInt(data) < 0) {
                tree1.goRed();
            }
        } else if (UuidUtils.RACER_2_RT.toString().equals(charUuuid)) {
            rt2.setText(formatRt(data));
            if (Integer.parseInt(data) < 0) {
                tree2.goRed();
            }
        } else if (UuidUtils.RACER_3_RT.toString().equals(charUuuid)) {
            rt3.setText(formatRt(data));
            if (Integer.parseInt(data) < 0) {
                tree3.goRed();
            }
        } else if (UuidUtils.RACER_HOST_RT.toString().equals(charUuuid)) {
            rt4.setText(formatRt(data));
            if (Integer.parseInt(data) < 0) {
                tree4.goRed();
            }
        }
    }

    // This method takes in a string representing reaction time in milliseconds, and returns
    // a string in the correct format
    private String formatRt(String data) {
        long rt = Long.parseLong(data);
        StringBuilder builder = new StringBuilder();

        if (rt < 0) {
            builder.append("-");
            rt *= -1;
        }
        if (rt > 1000) {
            builder.append("1.");
            rt -= 1000;
        } else {
            builder.append("0.");
        }
        if (rt < 10) {
            builder.append("00");
        } else if (rt < 100) {
            builder.append("0");
        }
        builder.append(rt);

        return builder.toString();
    }
}
