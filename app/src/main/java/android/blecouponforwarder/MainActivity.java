package android.blecouponforwarder;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "BLECouponAdvertiser";
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    private static final int SCAN_PERIOD = 140000;
    private static final String SHARED_PREFS_NAME = "txeddystone-uid-prefs";
    private static final String PREF_TX_POWER_LEVEL = "tx_power_level";
    private static final String PREF_TX_ADVERTISE_MODE = "tx_advertise_mode";
    private static final String PREF_NAMESPACE = "namespace";;
    private static final ParcelUuid SERVICE_UUID =
            ParcelUuid.fromString("0000FEAA-0000-1000-8000-00805F9B34FB");
    //private static final ParcelUuid MASK = ParcelUuid.fromString("FFFFFFFF-FFFF-FFFF-FFFF-000000000000");
    private SharedPreferences sharedPreferences;
    private BluetoothAdapter btAdapter;
    private BluetoothLeAdvertiser adv;
    private BluetoothLeScanner bluetoothLeScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private AdvertiseCallback advertiseCallback;
    private int txPowerLevel;
    private int advertiseMode;
    private TextView coupons;
    private Switch broadcast;
    private Spinner couponSpinner;
    private EditText coupon_name;
    private Button create;
    private Button accept;
    private Handler handler = new Handler();
    private byte[] id;
    private byte[] zero = new byte[10];
    private byte[] empty = new byte[0];
    private ArrayList<String> couponNames = new ArrayList<String>();
    private HashMap<String,byte[]> currentCoupons = new HashMap<String,byte[]>();
    ArrayAdapter<String> couponAdapter;
    String selected = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, 0);
        txPowerLevel = sharedPreferences.getInt(PREF_TX_POWER_LEVEL,
                AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);
        advertiseMode = sharedPreferences.getInt(PREF_TX_ADVERTISE_MODE,
                AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY);
        init();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                init();
            } else {
                finish();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (coupon_name != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString(PREF_NAMESPACE, coupon_name.getText().toString());
            editor.putInt(PREF_TX_POWER_LEVEL, txPowerLevel);
            editor.putInt(PREF_TX_ADVERTISE_MODE, advertiseMode);
            editor.apply();
        }
        if (btAdapter != null && btAdapter.isEnabled()) {
            scanLeDevice(false);
        }
    }

    // Checks if Bluetooth advertising is supported on the device and requests enabling if necessary.
    private void init() {
        BluetoothManager manager = (BluetoothManager) getApplicationContext().getSystemService(
                Context.BLUETOOTH_SERVICE);
        btAdapter = manager.getAdapter();
        Random random = new Random();
        byte[] dname = new byte[6];
        random.nextBytes(dname);
        btAdapter.setName(new String(dname));
        if (btAdapter == null) {
            showFinishingAlertDialog("Bluetooth Error", "Bluetooth not detected on device");
        } else if (!btAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            this.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);
        } else if (!btAdapter.isMultipleAdvertisementSupported()) {
            showFinishingAlertDialog("Not supported", "BLE advertising not supported on this device");
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                bluetoothLeScanner = btAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                filters = new ArrayList<ScanFilter>();
                filters.add(new ScanFilter.Builder().setServiceUuid(SERVICE_UUID).build());
            }
            adv = btAdapter.getBluetoothLeAdvertiser();
            advertiseCallback = createAdvertiseCallback();
            Random rd = new Random();
            id = new byte[10];
            rd.nextBytes(id);
            byte z = (byte)0;
            for (int i = 0; i < 10; i++){
                zero[i] = z;
            }
            buildUi();
        }
    }

    // Pops an AlertDialog that quits the app on OK.
    private void showFinishingAlertDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }

                }).show();
    }

    private AdvertiseCallback createAdvertiseCallback() {
        return new AdvertiseCallback() {

            @Override

            public void onStartFailure(int errorCode) {
                switch (errorCode) {
                    case ADVERTISE_FAILED_DATA_TOO_LARGE:
                        showToastAndLogError("ADVERTISE_FAILED_DATA_TOO_LARGE");
                        break;
                    case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                        showToastAndLogError("ADVERTISE_FAILED_TOO_MANY_ADVERTISERS");
                        break;
                    case ADVERTISE_FAILED_ALREADY_STARTED:
                        showToastAndLogError("ADVERTISE_FAILED_ALREADY_STARTED");
                        break;
                    case ADVERTISE_FAILED_INTERNAL_ERROR:
                        showToastAndLogError("ADVERTISE_FAILED_INTERNAL_ERROR");
                        break;
                    case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                        showToastAndLogError("ADVERTISE_FAILED_FEATURE_UNSUPPORTED");
                        break;
                    default:
                        showToastAndLogError("startAdvertising failed with unknown error " + errorCode);
                        break;
                }
            }

        };
    }

    private void buildUi() {
        broadcast = (Switch) findViewById(R.id.broadcast);
        broadcast.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override

            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startAdvertising();
                } else {
                    stopAdvertising();
                }
            }

        });
        coupon_name = (EditText) findViewById(R.id.new_coupon_name);
        coupon_name.setText("Coupon Name");
        coupons = findViewById(R.id.coupons);
        couponSpinner = (Spinner) findViewById(R.id.couponSpinner);
        couponAdapter = new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,couponNames);
        couponAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        couponSpinner.setAdapter(couponAdapter);
        setCouponSelectionListener();
        accept = findViewById(R.id.accept);
        accept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                scanLeDevice(true);
                accept.setEnabled(false);
            }
        });
        create = findViewById(R.id.create);
        create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v){
                String name = coupon_name.getText().toString();
                couponNames.add(name);
                currentCoupons.put(name,empty);
                updateView();
                couponAdapter.notifyDataSetChanged();
            }
        });
    }

    private void setCouponSelectionListener() {
        couponSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selected = (String) parent.getItemAtPosition(position);
                Log.i("selected",selected);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // NOP
            }
        });
    }

    private void startAdvertising() {
        Log.i(TAG, "Starting ADV, Coupon = " + couponSpinner.getSelectedItem());

        AdvertiseSettings advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(advertiseMode)
                .setTxPowerLevel(txPowerLevel)
                .setConnectable(true)
                .build();

        byte[] forwarders = currentCoupons.get(selected);
        byte[] name = selected.getBytes();
        byte[] serviceData = new byte[forwarders.length + 20 + name.length];
        for (int i = 0; i < forwarders.length; i++){
            serviceData[i] = forwarders[i];
        }
        for (int h = 0; h < 10; h++){
            serviceData[forwarders.length + h] = id[h];
        }
        for (int j = 0; j < 10; j++){
            serviceData[forwarders.length + 10 + j] = zero[j];
        }
        for (int k = 0; k < name.length; k++){
            serviceData[forwarders.length + 20 + k] = name[k];
        }

        coupon_name.setError(null);
        setEnabledViews(false, coupon_name, create);
        Fragmenter.setAdvertiseFlag(true);
        Fragmenter.advertise(adv,serviceData,SERVICE_UUID,advertiseSettings,advertiseCallback);
    }

    private void stopAdvertising() {
        Log.i(TAG, "Stopping ADV");
        Fragmenter.setAdvertiseFlag(false);
        adv.stopAdvertising(advertiseCallback);
        setEnabledViews(true, coupon_name, create);
    }

    public void scanLeDevice(final boolean enable) {
        if (enable) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    bluetoothLeScanner.stopScan(leScanCallback);
                    accept.setEnabled(true);
                    Assembler.clear();
                }
            }, SCAN_PERIOD);
            bluetoothLeScanner.startScan(filters,settings,leScanCallback);
        } else {
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("callbackType", String.valueOf(callbackType));
            Log.i("result", result.toString());
            //String address = result.getDevice().getAddress();
            String address = result.getDevice().getName();
            byte[] pData = Assembler.gather(address,result.getScanRecord().getServiceData(SERVICE_UUID));
            if (pData != null){
                updateCoupons(pData);
                couponAdapter.notifyDataSetChanged();
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    private void setEnabledViews(boolean enabled, View... views) {
        for (View v : views) {
            v.setEnabled(enabled);
        }
    }

    private void updateCoupons(byte[] data) {
        byte[] fwdchain = new byte[0];
        byte[] temp;
        int index = 0;
        byte[] fwd_id = new byte[10];
        while (true) {
            for (int i = 0; i < 10; i++) {
                fwd_id[i] = data[index + i];
            }
            if (Arrays.equals(fwd_id,zero)) {
                break;
            } else {
                index += 10;
                temp = new byte[fwdchain.length + 10];
                for (int j = 0; j < fwdchain.length; j++) {
                    temp[j] = fwdchain[j];
                }
                for (int k = 0; k < 10; k++) {
                    temp[fwdchain.length + k] = fwd_id[k];
                }
                fwdchain = temp;
            }
        }
        index+=10;
        int cname_index = 0;
        byte[] cname = new byte[data.length-index];
        while (index < data.length){
            cname[cname_index] = data[index];
            index++;
            cname_index++;
        }
        String name = new String(cname);
        if (!currentCoupons.containsKey(name)){
            currentCoupons.put(name,fwdchain);
            couponNames.add(name);
        }
        updateView();
    }

    private void updateView(){
        String available_coupons = "";
        for (String name : couponNames){
            available_coupons = available_coupons + "\n" + name;
        }
        coupons.setText(available_coupons);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showToastAndLogError(String message) {
        showToast(message);
        Log.e(TAG, message);
    }
}