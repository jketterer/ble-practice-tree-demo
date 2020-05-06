package com.example.bluetoothpracticetree.utility;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*
    This class provides a reference to the UUIDs used for the services, characteristics, and
    descriptors used by this app. It also provides any helper methods related to UUIDs.
 */

public class UuidUtils {

    public static final UUID SERVICE = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecebf1");

    public static final UUID BEGIN_RACE_ACTIVITY = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecebf3");
    public static final UUID RACER_ID = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecebf4");

    public static final UUID RACER_1_DIAL = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecebf5");
    public static final UUID RACER_1_STAGE = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecebf6");
    public static final UUID RACER_1_RT = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecebf7");

    public static final UUID RACER_2_DIAL = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecebf8");
    public static final UUID RACER_2_STAGE = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecebf9");
    public static final UUID RACER_2_RT = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecebfa");

    public static final UUID RACER_3_DIAL = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecebfb");
    public static final UUID RACER_3_STAGE = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecebfc");
    public static final UUID RACER_3_RT = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecebfd");

    public static final UUID RACER_HOST_DIAL = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecebfe");
    public static final UUID RACER_HOST_STAGE = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecebff");
    public static final UUID RACER_HOST_RT = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecec00");

    public static final UUID RACE_READY = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecec01");
    public static final UUID RACE_FINISHED = UUID.fromString("5b4a0066-4038-4786-be23-e5bbefecec02");

    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // This method converts the raw advertisement data collected by a scanning device and returns
    // a list of service UUIDs contained in that advertisement.
    public static List<UUID> parseServiceUuids(final byte[] advertisedData)
    {
        List<UUID> uuids = new ArrayList<UUID>();

        if( advertisedData == null )  return uuids;

        int offset = 0;
        while(offset < (advertisedData.length - 2))
        {
            int len = advertisedData[offset++];
            if(len == 0)
                break;

            int type = advertisedData[offset++];
            switch(type)
            {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while(len > 1)
                    {
                        int uuid16 = advertisedData[offset++];
                        uuid16 += (advertisedData[offset++] << 8);
                        len -= 2;
                        uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                    }
                    break;
                case 0x06:// Partial list of 128-bit UUIDs
                case 0x07:// Complete list of 128-bit UUIDs
                    // Loop through the advertised 128-bit UUID's.
                    while(len >= 16)
                    {
                        try
                        {
                            // Wrap the advertised bits and order them.
                            ByteBuffer buffer = ByteBuffer.wrap(advertisedData, offset++, 16).order(ByteOrder.LITTLE_ENDIAN);
                            long mostSignificantBit = buffer.getLong();
                            long leastSignificantBit = buffer.getLong();
                            uuids.add(new UUID(leastSignificantBit, mostSignificantBit));
                        }
                        catch(IndexOutOfBoundsException e)
                        {
                            Log.e("TAG", e.toString());
                        }
                        finally
                        {
                            // Move the offset to read the next uuid.
                            offset += 15;
                            len -= 16;
                        }
                    }
                    break;
                default:
                    offset += (len - 1);
                    break;
            }
        }

        return uuids;
    }
}
