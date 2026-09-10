package net.leobueno.aparcamientobluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

enum BTCategory
{
    BTCar,
    BTPhones,
    BTAudio,
    BTComputer,
    BTMobile,
    BTWearable,
    BTGlasses,
    BTNone,
    BTOther
}

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

    View splitter() {
        View divider = new View(this); // Usa 'getContext()' si estás en un Fragment

// 2. Definir el color de fondo (usamos el gris estándar del sistema)
//        divider.setBackgroundResource(android.R.drawable.divider_horizontal_bright);
// Alternativa con color hexadecimal propio:
        divider.setBackgroundColor(Color.parseColor("#888888"));

        // 3. Configurar dimensiones y márgenes (Ancho: MATCH_PARENT, Alto: 1dp o 2dp)
        int thicknessInPx = (int) (1 * getResources().getDisplayMetrics().density); // Convierte 1dp a píxeles de forma exacta
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                thicknessInPx
        );

        // 4. Configurar los márgenes verticales (separación con los bloques)
        int marginInPx = (int) (8 * getResources().getDisplayMetrics().density); // Convierte 16dp a píxeles
        params.setMargins(0, marginInPx, 0, marginInPx);

// 5. Aplicar los parámetros a la vista
        divider.setLayoutParams(params);
        return divider;
    }
    private void createInterface() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        scroll.addView(root);

        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(28), dp(28), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

// Cabecera
        FrameLayout titleContainer = new FrameLayout(this);

        TextView title = new TextView(this);
        title.setText(R.string.aparcamiento);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);

        FrameLayout.LayoutParams titleParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER);

        titleContainer.addView(title, titleParams);

// Icono a la izquierda del título
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);

        FrameLayout.LayoutParams iconParams =
                new FrameLayout.LayoutParams(
                        dp(48),
                        dp(48),
                        Gravity.CENTER_VERTICAL);

        iconParams.leftMargin = dp(12);

        titleContainer.addView(icon, iconParams);

        root.addView(titleContainer,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(56)));
        root.addView(splitter());
//Estado de aparcamiento
        statusText = new TextView(this);
        statusText.setTextSize(16);
        statusText.setPadding(0, dp(4), 0, dp(4));
        root.addView(statusText, matchWrap());

        lastParkingText = new TextView(this);
        lastParkingText.setTextSize(14);
        lastParkingText.setPadding(0, dp(4), 0, dp(8));
        root.addView(lastParkingText, matchWrap());
        root.addView(splitter());
//Selección de bluetooth
        TextView bluetoothLabel = new TextView(this);
        bluetoothLabel.setText(R.string.bluetooth_del_coche);
        bluetoothLabel.setTextSize(16);
        bluetoothLabel.setPadding(0, dp(0), 0, dp(8));
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
        root.addView(splitter());

        Button mapsButton = new Button(this);
        mapsButton.setText(R.string.ver_ltimo_aparcamiento);
        mapsButton.setOnClickListener(v -> openLastParking());
        root.addView(mapsButton, matchWrap());

        LinearLayout row;
        TextView label;
        CheckBox check;
        row = new LinearLayout(this);
        row.setPadding(0, dp(5), 0, 0);
        row.setOrientation(LinearLayout.HORIZONTAL);
        label = new TextView(this);
        label.setText(R.string.notification_sound);
        check = new CheckBox(this);
        check.setGravity(Gravity.RIGHT);
        boolean pk_sound = getPrefs().getBoolean("pk_sound", true);
        check.setChecked(pk_sound);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isCheckedNow) {
                getPrefs().edit().putBoolean("pk_sound", isCheckedNow).apply();
            }
        });
        row.addView(check);
        row.addView(label);
        root.addView(row, matchWrap());

        row = new LinearLayout(this);
        row.setPadding(0, 0, 0, dp(5));
        row.setOrientation(LinearLayout.HORIZONTAL);
        label = new TextView(this);
        label.setText(R.string.hacer_la_notificaci_n_persistente);
        check = new CheckBox(this);
        check.setGravity(Gravity.RIGHT);
        boolean recreate = getPrefs().getBoolean("nt_always_on", true);
        check.setChecked(recreate);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isCheckedNow) {
                getPrefs().edit().putBoolean("nt_always_on", isCheckedNow).apply();
            }
        });
        row.addView(check);
        row.addView(label);

        TextView explain_notification = new TextView(this);
        explain_notification.setTextSize(12);
        explain_notification.setText(R.string.explain_notification);
        root.addView(explain_notification, matchWrap());
        root.addView(row, matchWrap());

        root.addView(splitter());
        TextView titulo_manual = new TextView(this);
        titulo_manual.setTextSize(18);
        titulo_manual.setText(R.string.instrucciones);
        root.addView(titulo_manual, matchWrap());

        TextView manual = new TextView(this);
        manual.setTextSize(12);
        manual.setText(R.string.manual_text);
        root.addView(manual, matchWrap());


        setContentView(scroll);
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
    private String iconBT(BTCategory category)
    {
        switch (category) {
            case BTCar:
                return "\uD83D\uDE98\uFE0E";
            case BTPhones:
                return "\uD83C\uDFA7\uFE0E";
            case BTAudio:
                return "\uD83D\uDD0A\uFE0E";
            case BTComputer:
                return "\uD83D\uDCBB\uFE0E";
            case BTMobile:
                return "\uD83D\uDCF1\uFE0E";
            case BTWearable:
                return "⌚\uFE0E";
            case BTGlasses:
            case BTNone:
            case BTOther:
            default:
                return "ᛒ";
        }
    }
    private BTCategory getCategory(BluetoothClass clase)
    {
        BTCategory category = BTCategory.BTNone;
        if (clase != null) {
            int idclass = clase.getDeviceClass() & 0x1FFF;
            switch (idclass) {
                // 🚗 COCHE
                case BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO:
                case BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE:
                    category = BTCategory.BTCar;
                    break;

                // 🎧 AURICULARES
                case BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES:
                case BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET:
                    category = BTCategory.BTPhones;
                    break;
                case BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER:
                case BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO:
                case BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX:
                case BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO:
                case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER:
                    category = BTCategory.BTAudio;
                    break;
                // 📱 TELÉFONOS (Clases mayores externas a Audio/Video)
                case BluetoothClass.Device.PHONE_CELLULAR:
                case BluetoothClass.Device.PHONE_CORDLESS:
                case BluetoothClass.Device.PHONE_ISDN:
                case BluetoothClass.Device.PHONE_MODEM_OR_GATEWAY:
                case BluetoothClass.Device.PHONE_SMART:
                case BluetoothClass.Device.PHONE_UNCATEGORIZED:
                    category = BTCategory.BTMobile;
                    break;
                case BluetoothClass.Device.WEARABLE_GLASSES:
                case BluetoothClass.Device.WEARABLE_HELMET:
                    category = BTCategory.BTGlasses;
                    break;
                case BluetoothClass.Device.WEARABLE_PAGER:
                case BluetoothClass.Device.WEARABLE_UNCATEGORIZED:
                case BluetoothClass.Device.WEARABLE_WRIST_WATCH:
                    category = BTCategory.BTWearable;
                    break;
                // 💻 ORDENADORES (Clases mayores externas a Audio/Video)
                case BluetoothClass.Device.WEARABLE_JACKET:
                case BluetoothClass.Device.COMPUTER_LAPTOP:
                case BluetoothClass.Device.COMPUTER_DESKTOP:
                    category = BTCategory.BTComputer;
                    break;

                // 🪵 OTROS (Todo el resto de Audio, Video y periféricos)
                case BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE:
                case BluetoothClass.Device.AUDIO_VIDEO_VCR:
                case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CAMERA:
                case BluetoothClass.Device.AUDIO_VIDEO_CAMCORDER:
                case BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR:
                default:
                    category = BTCategory.BTOther;
                    break;
            }
        }
        return category;
    }
    private int getCategoryPriority(BluetoothDevice d)
    {
        try {
            return getCategory(d.getBluetoothClass()).ordinal();
        }
        catch (SecurityException e)
        {

        }
        return 1000;
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
        List<BluetoothDevice> sortedDevices = new ArrayList<>(bonded);

// 2. Definir el Comparator combinando Categoría + Nombre
        Comparator<BluetoothDevice> categoryAndNameComparator = new Comparator<BluetoothDevice>() {
            @Override
            public int compare(BluetoothDevice d1, BluetoothDevice d2) {
                try {
                    int cat1 = getCategoryPriority(d1);
                    int cat2 = getCategoryPriority(d2);

                    // Si pertenecen a la misma categoría, desempatamos alfabéticamente por su nombre
                    if (cat1 == cat2) {
                        String name1 = d1.getName() != null ? d1.getName() : "";
                        String name2 = d2.getName() != null ? d2.getName() : "";
                        return name1.compareToIgnoreCase(name2);
                    }

                    // Si son de distintas categorías, ordena según el peso asignado (0, 1, 2...)
                    return Integer.compare(cat1, cat2);
                } catch (SecurityException e) {
                    return 0;
                }
            }
        };

// 3. Ejecutar la ordenación
        sortedDevices.sort(categoryAndNameComparator);
        for (BluetoothDevice device : sortedDevices) {

            String name;
            BluetoothClass clase = null;
            try {
                name = device.getName();
                clase = device.getBluetoothClass();
            } catch (SecurityException e) {
                name = null;
            }
//Los dispositivos sin nombre no se añaden a la lista
            if (name != null && !name.trim().isEmpty()) {
                devices.add(device);
                names.add(iconBT(getCategory(clase))+" "+name);
            }
        }
        if (names.isEmpty()) {
            names.add("❌ "+getString(R.string.no_hay_dispositivos_emparejados));
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
            String date = Tools.getDate(this, loc.getTime());
            String pos_address = getPrefs().getString("pos_address", null);
            lastParkingText.setText(
                    getString(R.string.ltimo_aparcamiento) +
                    date +
                    getString(R.string.precisi_n) +
                    String.format(Locale.getDefault(), "%.0f m", loc.getAccuracy()) +
                    (pos_address != null ? "\n"+pos_address : ""));
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
