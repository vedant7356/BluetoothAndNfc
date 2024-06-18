package com.example.bluetoothandnfc;

import android.Manifest;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;
    private static final String TAG = "MainActivity";

    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> bluetoothArrayAdapter;
    private NfcAdapter nfcAdapter;
    private TextView nfcText;
    private TextView bluetoothStatus;
    private Button btnToggleBluetooth;
    private Button btnSearchDevices;
    private Button btnPairedDevices;
    private ProgressBar progressBar;
    private TextView noDevicesText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Bluetooth components
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ListView bluetoothList = findViewById(R.id.bluetooth_list);
        bluetoothList.setAdapter(bluetoothArrayAdapter);

        // Initialize NFC components
        nfcText = findViewById(R.id.nfc_text);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        // Initialize UI components
        bluetoothStatus = findViewById(R.id.bluetooth_status);
        btnToggleBluetooth = findViewById(R.id.btn_toggle_bluetooth);
        btnSearchDevices = findViewById(R.id.btn_search_devices);
        btnPairedDevices = findViewById(R.id.btn_paired_devices);
        progressBar = findViewById(R.id.progress_bar);
        noDevicesText = findViewById(R.id.no_devices_text);

        updateBluetoothStatus();

        btnToggleBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleBluetooth();
            }
        });

        btnSearchDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchDevices();
            }
        });

        btnPairedDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPairedDevices();
            }
        });

        // Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            bluetoothStatus.setText("Bluetooth is not supported on this device.");
            return;
        }

        // Register Bluetooth discovery receiver
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

        // Request Bluetooth permissions if not already granted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_BLUETOOTH_PERMISSIONS);
            } else {
                enableBluetoothAndDiscoverDevices();
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                        != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                        != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION},
                            REQUEST_BLUETOOTH_PERMISSIONS);
                } else {
                    enableBluetoothAndDiscoverDevices();
                }
            } else {
                enableBluetoothAndDiscoverDevices();
            }
        }

        // Check if NFC is supported
        if (nfcAdapter == null) {
            nfcText.setText("NFC is not available on this device.");
        }
    }

    private void updateBluetoothStatus() {
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            bluetoothStatus.setText("Bluetooth Status: Enabled");
        } else {
            bluetoothStatus.setText("Bluetooth Status: Disabled");
        }
    }

    private void toggleBluetooth() {
        if (bluetoothAdapter == null) {
            return;
        }

        if (bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.disable();
        } else {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_ENABLE_BT);
                return;
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        updateBluetoothStatus();
    }

    private void searchDevices() {
        if (bluetoothAdapter == null) {
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        noDevicesText.setVisibility(View.GONE);
        bluetoothArrayAdapter.clear();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, REQUEST_BLUETOOTH_PERMISSIONS);
            return;
        }

        Log.d(TAG, "Starting Bluetooth discovery...");
        bluetoothAdapter.startDiscovery();

        // Stop discovery after a certain amount of time
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                bluetoothAdapter.cancelDiscovery();
                progressBar.setVisibility(View.GONE);
                if (bluetoothArrayAdapter.isEmpty()) {
                    noDevicesText.setVisibility(View.VISIBLE);
                }
            }
        }, 10000); // 10 seconds
    }

    private void showPairedDevices() {
        if (bluetoothAdapter == null) {
            return;
        }

        bluetoothArrayAdapter.clear();
        progressBar.setVisibility(View.VISIBLE);
        noDevicesText.setVisibility(View.GONE);

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                String deviceInfo = device.getName() + " - " + device.getAddress();
                bluetoothArrayAdapter.add(deviceInfo);
            }
        } else {
            noDevicesText.setVisibility(View.VISIBLE);
            bluetoothArrayAdapter.add("No paired devices found.");
        }

        progressBar.setVisibility(View.GONE);
        bluetoothArrayAdapter.notifyDataSetChanged();
    }

    private void enableBluetoothAndDiscoverDevices() {
        updateBluetoothStatus();
        if (bluetoothAdapter.isEnabled()) {
            searchDevices();
        }
    }

    // BroadcastReceiver to handle Bluetooth device discovery
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH_PERMISSIONS);
                    return;
                }
                String deviceInfo = device.getName() + " - " + device.getAddress();
                Log.d(TAG, "Device found: " + deviceInfo);
                bluetoothArrayAdapter.add(deviceInfo);
                bluetoothArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfcAdapter != null) {
            // Set up NFC foreground dispatch
            Intent intent = new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
            IntentFilter[] filters = new IntentFilter[]{};
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            // Disable NFC foreground dispatch
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            // Handle NFC tag discovery
            Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                NdefMessage ndefMessage = ndef.getCachedNdefMessage();
                if (ndefMessage != null) {
                    NdefRecord[] records = ndefMessage.getRecords();
                    for (NdefRecord record : records) {
                        byte[] payload = record.getPayload();
                        String text = new String(payload);
                        nfcText.setText(text);
                    }
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableBluetoothAndDiscoverDevices();
            } else {
                // Permission denied
            }
        } else if (requestCode == REQUEST_ENABLE_BT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableBluetoothAndDiscoverDevices();
            } else {
                // Permission denied
            }
        }
    }
}
