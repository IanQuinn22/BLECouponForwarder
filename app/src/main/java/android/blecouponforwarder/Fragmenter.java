package android.blecouponforwarder;

import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;

public class Fragmenter {

    private static final String TAG = "FRAGMENTER";
    private static boolean advertise_flag = true;
    private static long endTime;


    public static void advertise(final BluetoothLeAdvertiser adv, byte[] data, ParcelUuid uuid, final AdvertiseSettings advertiseSettings, final AdvertiseCallback advertiseCallback){
        int full_packet_count = data.length / 18;
        int last_packet_bytes = data.length % 18;
        int packet_num = 1;
        byte[] adv_packet;
        int loopcount = 0;

        while (advertise_flag && loopcount < 3){
            adv_packet = new byte[20];
            if (full_packet_count == 0){
                adv_packet = new byte[data.length+2];
                adv_packet[0] = (byte)1;
                adv_packet[1] = (byte)1;
                for (int i = 0; i < data.length; i++){
                    adv_packet[i+2] = data[i];
                }
                AdvertiseData advertiseData = new AdvertiseData.Builder()
                        .addServiceData(uuid,adv_packet)
                        .addServiceUuid(uuid)
                        .setIncludeTxPowerLevel(false)
                        .setIncludeDeviceName(false)
                        .build();
                adv.startAdvertising(advertiseSettings, advertiseData, advertiseCallback);
            }
            else {
                while (packet_num <= full_packet_count){
                    if (packet_num == 1){
                        adv_packet[0] = (byte)1;
                    } else {
                        adv_packet[0] = (byte)packet_num;
                    }
                    if (packet_num == full_packet_count && last_packet_bytes == 0){
                        adv_packet[1] = (byte)1;
                    } else {
                        adv_packet[1] = (byte)0;
                    }
                    byte[] sub = Arrays.copyOfRange(data,18*(packet_num - 1),18*packet_num);
                    for (int j = 0; j < sub.length; j++){
                        adv_packet[j+2] = sub[j];
                    }
                    AdvertiseData advertiseData = new AdvertiseData.Builder()
                            .addServiceData(uuid,adv_packet)
                            .addServiceUuid(uuid)
                            .setIncludeTxPowerLevel(false)
                            .setIncludeDeviceName(false)
                            .build();

                    adv.startAdvertising(advertiseSettings,advertiseData,advertiseCallback);
                    Log.i("Packet Number: ",Integer.toString(packet_num));
                    endTime = System.currentTimeMillis() + 1000;
                    while (System.currentTimeMillis() < endTime){
                    }
                    adv.stopAdvertising(advertiseCallback);
                    packet_num++;
                }
                if (last_packet_bytes != 0){
                    adv_packet = new byte[last_packet_bytes+2];
                    adv_packet[0] = (byte)packet_num;
                    adv_packet[1] = (byte)1;
                    for (int k = 0; k < last_packet_bytes; k++){
                        adv_packet[k+2] = data[full_packet_count*18 + k];
                    }
                    final AdvertiseData advertiseData = new AdvertiseData.Builder()
                            .addServiceData(uuid,adv_packet)
                            .addServiceUuid(uuid)
                            .setIncludeTxPowerLevel(false)
                            .setIncludeDeviceName(false)
                            .build();

                    adv.startAdvertising(advertiseSettings, advertiseData, advertiseCallback);
                    endTime = System.currentTimeMillis() + 1000;
                    Log.i("Packet Number: ","Last Packet");
                    while (System.currentTimeMillis() < endTime){
                    }
                    adv.stopAdvertising(advertiseCallback);
                }
                packet_num = 1;
                loopcount++;
            }
        }
        adv.stopAdvertising(advertiseCallback);

    }

    public static void setAdvertiseFlag(boolean set){
        advertise_flag = set;
    }

}
