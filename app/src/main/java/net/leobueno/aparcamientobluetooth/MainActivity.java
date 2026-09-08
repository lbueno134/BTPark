package net.leobueno.aparcamientobluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int REQUEST_PERMISSIONS = 10;
    private static final int REQUEST_BACKGROUND_LOCATION = 11;

    private static final String PREFS = "car_tracker";
    private static final String KEY_ADDRESS = "car_bluetooth_address";
    private static final String KEY_NAME = "car_bluetooth_name";

    private Spinner bluetoothSpinner;
    private TextView statusText;
    private TextView lastParkingText;

    private final List<BluetoothDevice> devices = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastRefresh = 0;
    private boolean lastConnection = true;

    private final BroadcastReceiver parkingReceiver =
            new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {

                    if ("net.leobueno.aparcamientobluetooth.PARKING_UPDATED"
                            .equals(intent.getAction())) {
                        updateScreen();
                    }
                }
            };
    private final Runnable refresh = new Runnable() {
        @Override
        public void run() {

            Location loc = Tools.getLocation(getBaseContext());
            long newRefresh = loc != null ? loc.getTime() : 0;
            boolean newConnection = isConnected();
            if (newRefresh != lastRefresh || newConnection != lastConnection)
            {
                updateScreen();
            }
            lastConnection = newConnection;
            lastRefresh = newRefresh;
            // Volver a ejecutar dentro de 1 segundo
            handler.postDelayed(this, 1000);
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        createInterface();
        requestPermissionsIfNeeded();
        Intent intent = getIntent();
        String open = intent.getStringExtra("openGeo");
        if (open != null && open.equals("last"))
            openLastParking();
    }
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onStart() {
        super.onStart();

        IntentFilter filter = new IntentFilter(
                "net.leobueno.aparcamientobluetooth.PARKING_UPDATED"
        );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                    parkingReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            registerReceiver(
                    parkingReceiver,
                    filter
            );
        }
        updateScreen();
    }
    @Override
    protected void onStop() {
        super.onStop();

        unregisterReceiver(parkingReceiver);
    }
    @Override
    protected void onResume() {
        super.onResume();

        if (bluetoothSpinner != null) {
            loadBluetoothDevices();
            updateScreen();
        }
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refresh);
    }

    private void createInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(28), dp(28), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText(R.string.aparcamiento);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView bluetoothLabel = new TextView(this);
        bluetoothLabel.setText(R.string.bluetooth_del_coche);
        bluetoothLabel.setTextSize(16);
        bluetoothLabel.setPadding(0, dp(36), 0, dp(8));
        root.addView(bluetoothLabel, matchWrap());

        bluetoothSpinner = new Spinner(this);
        root.addView(bluetoothSpinner, matchWrap());
        bluetoothSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(
                    AdapterView<?> parent,
                    View view,
                    int position,
                    long id) {

                // Ha cambiado el elemento seleccionado
                saveConfiguration();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                saveConfiguration();
            }
        });
        statusText = new TextView(this);
        statusText.setTextSize(16);
        statusText.setPadding(0, dp(28), 0, dp(4));
        root.addView(statusText, matchWrap());

        lastParkingText = new TextView(this);
        lastParkingText.setTextSize(14);
        lastParkingText.setPadding(0, dp(18), 0, dp(20));
        root.addView(lastParkingText, matchWrap());

        Button mapsButton = new Button(this);
        mapsButton.setText(R.string.ver_ltimo_aparcamiento);
        mapsButton.setOnClickListener(v -> openLastParking());
        root.addView(mapsButton, matchWrap());

        TextView titulo_manual = new TextView(this);
        titulo_manual.setTextSize(18);
        titulo_manual.setText(R.string.instrucciones);
        root.addView(titulo_manual, matchWrap());

        TextView manual = new TextView(this);
        manual.setTextSize(12);
        manual.setText(R.string.manual_text);
        root.addView(manual, matchWrap());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView label = new TextView(this);
        label.setText(R.string.hacer_la_notificaci_n_persistente);
        row.addView(label);
        CheckBox check = new CheckBox(this);
        check.setGravity(Gravity.RIGHT);
        boolean recreate = getPrefs().getBoolean("nt_always_on", true);
        check.setChecked(recreate);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isCheckedNow) {
                getPrefs().edit().putBoolean("nt_always_on", isCheckedNow).apply();
            }
        });        row.addView(check);
        root.addView(row, matchWrap());

        setContentView(root);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void requestPermissionsIfNeeded() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN)) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (Build.VERSION.SDK_INT >= 33 &&
                !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }

        queryBackgroundPermission();
        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissions.toArray(new String[0]),
                    REQUEST_PERMISSIONS);
        } else {
            loadBluetoothDevices();
            updateScreen();
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void loadBluetoothDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return;
        }

        BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        BluetoothAdapter adapter = bluetoothManager.getAdapter();
        //BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

        if (adapter == null) {
            statusText.setText(R.string.este_m_vil_no_tiene_bluetooth);
            return;
        }

        if (!adapter.isEnabled()) {
            statusText.setText(R.string.bluetooth_del_m_vil_est_apagado);
            return;
        }

        devices.clear();
        List<String> names = new ArrayList<>();
        devices.add(null);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();

        for (BluetoothDevice device : bonded) {

            String name;
            try {
                name = device.getName();
            } catch (SecurityException e) {
                name = null;
            }
//Los dispositivos sin nombre no se añaden a la lista
            if (name != null && !name.trim().isEmpty()) {
                devices.add(device);
                names.add(name);
            }
        }

        if (names.isEmpty()) {
            names.add(getString(R.string.no_hay_dispositivos_emparejados));
        }
        else
        {
            names.add(0, getString(R.string.desactivar));
        }

        ArrayAdapter<String> adapterView = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                names);

        adapterView.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);

        bluetoothSpinner.setAdapter(adapterView);

        String savedAddress = getPrefs().getString(KEY_ADDRESS, null);

        bluetoothSpinner.setSelection(0);
        if (savedAddress != null) {
            for (int i = 0; i < devices.size(); i++) {
                if (devices.get(i) != null)
                    if (savedAddress.equals(devices.get(i).getAddress())) {
                        bluetoothSpinner.setSelection(i);
                        break;
                    }
            }
        }
    }

    private boolean doSaveConfiguration() {
        if (devices.isEmpty()) {
            getPrefs().edit()
                    .putString(KEY_ADDRESS, null)
                    .putString(KEY_NAME, null)
                    .apply();
            Toast.makeText(this,
                    R.string.no_hay_ning_n_dispositivo_bluetooth_emparejado,
                    Toast.LENGTH_LONG).show();
            return false;
        }

        int position = bluetoothSpinner.getSelectedItemPosition();
        BluetoothDevice device = null;
        if (position >= 0 && position < devices.size()) {
            device = devices.get(position);
        }

        if (device == null) {
            getPrefs().edit()
                    .putString(KEY_ADDRESS, null)
                    .putString(KEY_NAME, null)
                    .apply();
            return false;
        }
        String name = getString(R.string.coche);
        try {
            if (device.getName() != null) {
                name = device.getName();
            }
        } catch (SecurityException ignored) {
        }

        getPrefs().edit()
                .putString(KEY_ADDRESS, device.getAddress())
                .putString(KEY_NAME, name)
                .apply();

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            Toast.makeText(this,
                    R.string.necesitas_conceder_permiso_de_ubicaci_n_precisa,
                    Toast.LENGTH_LONG).show();
            return false;
        }
        return queryBackgroundPermission();
    }
    private boolean queryBackgroundPermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        {
            Intent intent = new Intent(this, BackgroundLocationActivity.class);
            startActivityForResult(intent, REQUEST_BACKGROUND_LOCATION);
            return false;
        }
        return true;
    }
    private void saveConfiguration() {
        if (doSaveConfiguration())
            startTracking();
        else
            endTracking();
    }

    private void endTracking()
    {
        stopService(new Intent(this, CarLocationService.class));
    }

    private void startTracking() {
        Intent intent = new Intent(this, CarLocationService.class);
        ContextCompat.startForegroundService(this, intent);

        updateScreen();

        Toast.makeText(this,
                R.string.seguimiento_activado,
                Toast.LENGTH_SHORT).show();
    }

    @SuppressLint("SetTextI18n")
    private void updateScreen() {
        String address = getPrefs().getString(KEY_ADDRESS, null);

        if (address == null) {
            statusText.setText(R.string.bluetooth_del_coche_no_configurado);
            lastParkingText.setText(R.string.ltimo_aparcamiento_no_hay_ninguno_registrado);
            return;
        }
        if (isConnected())
            statusText.setText(R.string.esperando_desconexi_n_del_coche);
        else
            statusText.setText(R.string.esperando_al_conexi_n_del_coche);

        Location loc = Tools.getLocation(getBaseContext());

        if (loc == null) {
            lastParkingText.setText(
                    R.string.ltimo_aparcamiento_no_hay_ninguno_registrado);
        } else {
            String date = Tools.getDate(loc.getTime());

            lastParkingText.setText(
                    getString(R.string.ltimo_aparcamiento) +
                    date +
                    getString(R.string.precisi_n) +
                    String.format(Locale.getDefault(), "%.0f m", loc.getAccuracy()));
        }
    }
    private boolean isConnected()
    {
        return getPrefs().getBoolean("bt_connected", false);
    }
    private void openLastParking() {
        Location loc = Tools.getLocation(getBaseContext());
        if (loc == null) {
            Toast.makeText(this,
                    R.string.todav_a_no_hay_ning_n_aparcamiento_registrado,
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {

            Uri uri = Uri.parse(
                    "geo:" + loc.getLatitude() + "," + loc.getLongitude() +
                    "?q=" + loc.getLatitude() + "," + loc.getLongitude() +
                    "("+getString(R.string.coche)+")");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (Exception e) {
            // Fallback: Google Maps mediante URL web
            Uri uri = Uri.parse(
                    "https://www.google.com/maps/search/?api=1&query=" +
                            loc.getLatitude() + "," + loc.getLongitude());

            Intent intent = new Intent(Intent.ACTION_VIEW, uri);

            try {
                startActivity(intent);
            } catch (Exception e2) {
                Toast.makeText(this,
                        R.string.no_hay_una_aplicaci_n_de_mapas_disponible,
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSIONS) {
            loadBluetoothDevices();
            updateScreen();
        }
    }
}
