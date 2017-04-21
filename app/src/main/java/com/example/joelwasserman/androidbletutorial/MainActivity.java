package com.example.joelwasserman.androidbletutorial;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.ParcelUuid;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.util.Iterator;
import java.util.Map;

import static java.lang.Integer.MIN_VALUE;

public class MainActivity extends AppCompatActivity {

    BluetoothManager btManager;
    BluetoothAdapter btAdapter;
    BluetoothLeScanner btScanner;
    Button startScanningButton;
    Button stopScanningButton;
    TextView peripheralTextView;
    private final static int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    private static long lastTS = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        peripheralTextView = (TextView) findViewById(R.id.PeripheralTextView);
        peripheralTextView.setMovementMethod(new ScrollingMovementMethod());

        startScanningButton = (Button) findViewById(R.id.StartScanButton);
        startScanningButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startScanning();
            }
        });

        stopScanningButton = (Button) findViewById(R.id.StopScanButton);
        stopScanningButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                stopScanning();
            }
        });
        stopScanningButton.setVisibility(View.INVISIBLE);

        btManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = btManager.getAdapter();
        btScanner = btAdapter.getBluetoothLeScanner();


        if (btAdapter != null && !btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent,REQUEST_ENABLE_BT);
        }

        // Make sure we have access coarse location enabled, if not, prompt the user to enable it
        if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("This app needs location access");
            builder.setMessage("Please grant location access so this app can detect peripherals.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                }
            });
            builder.show();
        }
    }

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for(byte b: a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // Device scan callback.
    private ScanCallback leScanCallback = new ScanCallback() {


        @Override
        public void onScanResult(int callbackType, ScanResult result) {

            String devName, devAddr, rawBytesString, appData;
            int rssi, txPower, advFlags;
            long ts, tsDiff = 0;
            BluetoothDevice btDev;
            ScanRecord sr;
            byte[] rawBytes;

            boolean service_data_rcvd = false;

            sr = result.getScanRecord();

            btDev = result.getDevice();
            rssi = result.getRssi();

            if(btDev != null){
                devAddr = btDev.getAddress();
                devName = btDev.getName();
            }
            else{
                devAddr = null;
                devName = null;
            }


            txPower = sr.getTxPowerLevel();
            rawBytes = sr.getBytes();
            advFlags = sr.getAdvertiseFlags();

            int field_len, type, i, j;
            byte[] rawValue;
            i = 0;
            rawBytesString = "";
            appData = "";


            //environmental sensor demo variables
            float temp=0;
            float press=0;
            int raw_temp=0;
            int raw_press=0;





            while(i < rawBytes.length){
                field_len = rawBytes[i];
                type = rawBytes[i+1];

                if(type == 0x16){ //AD_TYPE_SERVICE_DATA
                    rawValue = new byte[field_len-1];
                    for(j = 0; j < field_len-1; j++){
                        rawValue[j] = rawBytes[i+2+j];
                    }

                    rawBytesString = rawBytesString + "\n" + byteArrayToHex(rawValue);

                    if(rawValue[0] == 0x00 && rawValue[1] == 0x01){ //app temperature and pressure sensor beacon demo
                        raw_temp = 0x000000ff & rawValue[3];
                        raw_temp = (raw_temp << 8) | (0x000000ff & rawValue[2]);
                        temp = (float) raw_temp / 10;
                        raw_press = 0x000000ff & rawValue[6];
                        raw_press = raw_press << 8 | (0x000000ff & rawValue[5]);
                        raw_press = raw_press << 8 | (0x000000ff & rawValue[4]);
                        press = (float) raw_press / 100;
                        appData = "\n\tTemperature: " + temp + " °C \n\tPressure: " + press + " mbr";
                    }

                    //ts = result.getTimestampNanos();
                    ts = System.currentTimeMillis();
                    if(lastTS == 0) tsDiff = 0;
                    else tsDiff = (ts - lastTS);
                    //else tsDiff = (ts - lastTS)/1000000;
                    lastTS = ts;

                    service_data_rcvd = true;
                    break;
                }


                i = i + field_len + 1;
                if(i > 31)break;
            }

            String scanSummary = "";
            if(devName != null) scanSummary = "\nDevice name: " + devName;
            if(devAddr != null) scanSummary = scanSummary + "\nAddress: " + devAddr;
            if(txPower != MIN_VALUE) scanSummary = scanSummary + "\nTXPower: " + txPower;
            if(advFlags != -1) scanSummary = scanSummary + "\nAdv flags: " + advFlags;
            scanSummary = scanSummary + "\nRSSI: " + rssi;
            if(appData != "")scanSummary = scanSummary + "\nAppData: " + appData;
            //if(rawBytesString != "")scanSummary = scanSummary + "\nrawBytes: " + rawBytesString;


            /*String s= byteArrayToHex(result.getScanRecord().getBytes());


            if(result.getScanRecord().getServiceData()==null) s= "null";
            else {
                for (Map.Entry<ParcelUuid, byte[]> entry : result.getScanRecord().getServiceData().entrySet()) {
                    if (entry.getKey() != null) {
                        raw_temp = 0x000000ff & entry.getValue()[1];
                        raw_temp = (raw_temp << 8) | (0x000000ff & entry.getValue()[0]);
                        temp = (float) raw_temp / 10;
                        raw_press = 0x000000ff & entry.getValue()[4];
                        raw_press = raw_press << 8 | (0x000000ff & entry.getValue()[3]);
                        raw_press = raw_press << 8 | (0x000000ff & entry.getValue()[2]);
                        press = (float) raw_press / 100;
                    }
                }
            }*/

            if(service_data_rcvd && (tsDiff > 200 || tsDiff == 0)) //maggiore di 200 da eliminare in futuro
                peripheralTextView.append("\n[" + tsDiff + "ms]" + scanSummary + "\n");

            //peripheralTextView.append(s+"\n"+"Device Name: " + result.getDevice().getName() + " rssi: " + result.getRssi()+"\n"+ "temp: "+temp+", press: "+press+"\n");
           // peripheralTextView.append(s+"\n");
            // auto scroll for text view
            final int scrollAmount = peripheralTextView.getLayout().getLineTop(peripheralTextView.getLineCount()) - peripheralTextView.getHeight();
            // if there is no need to scroll, scrollAmount will be <=0
            if (scrollAmount > 0)
                peripheralTextView.scrollTo(0, scrollAmount);
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    System.out.println("coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {

                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }

    public void startScanning() {
        System.out.println("start scanning");
        peripheralTextView.setText("");
        startScanningButton.setVisibility(View.INVISIBLE);
        stopScanningButton.setVisibility(View.VISIBLE);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.startScan(leScanCallback);
            }
        });
    }

    public void stopScanning() {
        System.out.println("stopping scanning");
        peripheralTextView.append("Stopped Scanning");
        startScanningButton.setVisibility(View.VISIBLE);
        stopScanningButton.setVisibility(View.INVISIBLE);
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.stopScan(leScanCallback);
            }
        });
    }
}
