package android.blecouponforwarder;

import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;

public class Assembler {

    private static HashMap<String,HashMap<Byte,byte[]>> gatherMap = new HashMap<>();
    private static HashMap<String,HashMap<Byte,byte[]>> rsMap = new HashMap<>();
    private static HashMap<String, Integer> lastPacket = new HashMap<>();
    private static byte[] zero_byte = new byte [] {0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};
    //private static HashMap<String,Integer> expectMap = new HashMap<>();

    public static void gather(String address,byte[] data){
        if (!gatherMap.containsKey(address)){
            /*if (data[0] == (byte)1){
                gatherMap.put(address, Arrays.copyOfRange(data,2,data.length));
                expectMap.put(address,2);
            } else {
                expectMap.put(address,1);
            }*/
            HashMap<Byte,byte[]> temp = new HashMap<>();
            temp.put(data[0],data);
            gatherMap.put(address,temp);
            if (data[1] == (byte)1){
                lastPacket.put(address,(int)data[0]);
            }
        } else {
            /*int expected = expectMap.get(address);
            if (data[0] != (byte)expected){
                return null;
            } else {
                expected++;
                expectMap.put(address,expected);*/
                HashMap temp = gatherMap.get(address);
                //byte[] conjoin = new byte[temp.length + data.length - 2];
                /*for (int i = 0; i < temp.length; i++){
                    conjoin[i] = temp[i];
                }
                for (int j = 0; j < data.length - 2; j++){
                    conjoin[temp.length+j] = data[j+2];
                }*/
                temp.put(data[0],data);
                gatherMap.put(address,temp);
            if (data[1] == (byte)1){
                lastPacket.put(address,(int)data[0]);
            }
                //if (data[1] == (byte)1){
                    //gatherMap.remove(address);
                    //expectMap.remove(address);
                    //int null_ind = conjoin.length;
                    /*for (int k = 2; k < data.length; k++){
                        if (data[k] == (byte)0){
                            null_ind = k;
                            break;
                        }
                    }

                    conjoin = Arrays.copyOfRange(conjoin,0,conjoin.length-22+null_ind-2);*/
                    //return conjoin;
                //}
            //}
        }
        //return null;
    }

    public static void clear(){
        gatherMap.clear();
        //expectMap.clear();
    }

    public static byte[] gatherRS(String address,byte[] data){
        if (!rsMap.containsKey(address)){
            HashMap<Byte,byte[]> temp = new HashMap<>();
            temp.put(data[1],data);
            rsMap.put(address,temp);
        } else {
            HashMap<Byte,byte[]> temp = rsMap.get(address);
            temp.put(data[1],data);
            rsMap.put(address,temp);
            if (data[1] == (byte)2){
                HashMap broadcastData = gatherMap.get(address);
                if (!lastPacket.containsKey(address)){
                    Log.e("Last packet not found: ","STOP");
                    return null;
                }
                return checkRS(lastPacket.get(address),data.length,broadcastData,rsMap.get(address), address);
            }
        }
        return null;
    }

    public static byte[] checkRS(int packet_ct, int packet_len, HashMap<Byte,byte[]> packets, HashMap<Byte,byte[]> rs, String address){
        byte[][] decode = new byte[packet_ct+2][packet_len];
        boolean[] present = new boolean[packet_ct+2];
        int f_count = 0;
        for (int i = 0; i < packet_ct; i++){
            if (packets.containsKey((byte)(i+1))){
                present[i] = true;
                decode[i] = packets.get((byte)(i+1));
            } else {
                present[i] = false;
                f_count++;
                decode[i] = zero_byte;
            }
        }
        for (int j = 1; j <= 2; j++){
            if (rs.containsKey((byte)j)){
                present[packet_ct + j -1] = true;
                decode[packet_ct + j -1] = rs.get((byte)j);
            } else {
                present[packet_ct + j +1] = false;
                f_count++;
                decode[packet_ct + j -1] = zero_byte;
            }
        }
        if (f_count <= 2){
            ReedSolomon codec = ReedSolomon.create(packet_ct,2);
            codec.decodeMissing(decode,present,0,packet_len);
            gatherMap.remove(address);
            rsMap.remove(address);
            lastPacket.remove(address);
            return combine(decode);
        } else {
            Log.e("Too many missing packets","STOP");
        }
        return null;
    }

    public static byte[] combine(byte[][] rs){
       byte[] result = new byte[0];
       for (int i = 0; i < rs.length-3; i++){
           byte[] temp = new byte[result.length+rs[i].length-2];
           for (int j = 0; j < result.length; j++){
               temp[j] = result[j];
           }
           for (int k = 2; k < rs[i].length; k++){
               temp[result.length + k - 2] = rs[i][k];
           }
           result = temp;
       }
       int h = 2;
       while (h < rs[rs.length - 3].length){
           if (rs[rs.length - 3][h] == (byte)0){
               break;
           }
           h++;
       }
       byte[] shorten = Arrays.copyOfRange(rs[rs.length-3],2,h);
       byte[] temp = new byte[shorten.length + result.length];
       for(int d = 0; d < result.length; d++){
           temp[d] = result[d];
       }
       for (int w = 0; w < shorten.length; w++){
           temp[result.length + w] = shorten[w];
       }
       result = temp;
       return result;
    }

}
