package net.leobueno.aparcamientobluetooth;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
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
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class CarLocationService extends Service {

    public static final String CHANNEL_ID = "car_location_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static final String PREFS = "car_tracker";
    private static final String KEY_ADDRESS = "car_bluetooth_address";

    private FusedLocationProviderClient locationClient;
    private BroadcastReceiver bluetoothReceiver;
    private final AtomicBoolean gettingLocation = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
    }
    private void enviarCorreo(String mail, Location location) {

        if (location == null) {
            return;
        }

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        SimpleDateFormat formato =
                new SimpleDateFormat("d MMM HH:mm", Locale.getDefault());

        String date = formato.format(location.getTime());

        String mapsUrl = "https://www.google.com/maps/search/?api=1&query="
                + latitude + "," + longitude;

        String asunto = getString(R.string.posici_n_de_aparcamiento_a_las) + date;

        String cuerpo = getString(R.string.he_aparcado_el_coche_en_esta_posici_n)
                + getString(R.string.latitud) + latitude + "\n"
                + getString(R.string.longitud) + longitude + "\n\n"
                + getString(R.string.abrir_posici_n_en_google_maps)
                + mapsUrl;

        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"+mail));
        intent.putExtra(Intent.EXTRA_SUBJECT, asunto);
        intent.putExtra(Intent.EXTRA_TEXT, cuerpo);

        startActivity(Intent.createChooser(intent, "Enviar posición"));
    }
    private void registerBluetoothReceiver() {
        bluetoothReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(
                    Context context,
                    Intent intent) {

                Log.e("CAR_BT", "******** BROADCAST ********");
                Log.e("CAR_BT", "ACTION = " + intent.getAction());

                BluetoothDevice device;

                if (Build.VERSION.SDK_INT >= 33) {
                    device = intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE,
                            BluetoothDevice.class);
                } else {
                    device = intent.getParcelableExtra(
                            BluetoothDevice.EXTRA_DEVICE);
                }

                if (device == null) {
                    return;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT)
                                != PackageManager.PERMISSION_GRANTED) {
                    return;
                }

                String configuredAddress =
                        getSharedPreferences(
                                PREFS,
                                MODE_PRIVATE)
                                .getString(KEY_ADDRESS, null);

                if (configuredAddress == null) {
                    return;
                }

                if (!configuredAddress.equals(
                        device.getAddress())) {
                    return;
                }
                switch (intent.getAction())
                {
                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        setConnected(true);
                        Notification notification = createNotification(getLocation());
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification);
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        setConnected(false);
                        obtainAndSaveLocation();
                        break;
                }
            }
        };

        IntentFilter filter = new IntentFilter(
                BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                    bluetoothReceiver,
                    filter,
                    Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(
                    bluetoothReceiver,
                    filter);
        }
    }

    private void obtainAndSaveLocation() {
        if (!gettingLocation.compareAndSet(false, true)) {
            return;
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            gettingLocation.set(false);
            return;
        }

        LocationRequest request =
                new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        1000)
                        .setMinUpdateIntervalMillis(500)
                        .setMaxUpdateDelayMillis(2000)
                        .setMaxUpdates(1)
                        .build();

        LocationCallback callback =
                new LocationCallback() {

                    @Override
                    public void onLocationResult(
                            LocationResult result) {

                        Location location = result.getLastLocation();

                        if (location != null) {
                            saveLocation(location);
                        }
                        locationClient.removeLocationUpdates(this);
                        gettingLocation.set(false);
                    }
                };

        locationClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper());
    }
    SharedPreferences getPrefs() {
        return getSharedPreferences(
                PREFS,
                MODE_PRIVATE);
    }
    private void setConnected(boolean flag)
    {
        SharedPreferences prefs = getPrefs();
        prefs.edit().putBoolean("bt_connected", flag).apply();
    }
    public Notification createNotification(Location loc) {
        boolean isconnected = getPrefs().getBoolean("bt_connected", false);
        String prefix = isconnected ? "⚡" : "";
        int color = isconnected ? Color.BLUE : Color.TRANSPARENT;
        if (loc != null) {
            double lat = loc.getLatitude();
            double lon = loc.getLongitude();
            long ms = System.currentTimeMillis();
            Uri uri = Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage("com.google.android.apps.maps");

            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    100,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            String date = DateFormat.getDateTimeInstance(
                    DateFormat.SHORT,
                    DateFormat.SHORT,
                    Locale.getDefault()).format(new Date(ms));
            return new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_car)
                    .setContentTitle(prefix + getString(R.string.coche_aparcado) + date)
                    .setContentText(getString(R.string.pulsa_para_ver_la_posici_n_en_google_maps))
                    .setContentIntent(pendingIntent)
                    .setColor(color)
                    .setAutoCancel(false)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setOngoing(true)
                    .build();
        }
        else {
            Intent openApp = new Intent(this, MainActivity.class);
            openApp.putExtra("openGeo", "last");
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    openApp,
                    PendingIntent.FLAG_UPDATE_CURRENT |
                            (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

            return new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_car)
                    .setContentTitle(prefix + getString(R.string.aparcamiento))
                    .setContentText(getString(R.string.donde_he_aparcado))
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setColor(color)
                    .setPriority(
                            NotificationCompat.PRIORITY_LOW)
                    .setContentIntent(pendingIntent)
                    .build();
        }
    }
    public Location getLocation() {
        Location loc = new Location("gps");
        SharedPreferences prefs = getPrefs();
        loc.setLatitude(Double.parseDouble(prefs.getString("pos_latitude", "0")));
        loc.setLongitude(Double.parseDouble(prefs.getString("pos_longitude", "0")));
        loc.setAccuracy(prefs.getFloat("pos_accuracy", 0));
        loc.setTime(prefs.getLong("pos_ms", 0));
        return loc.getTime() != 0 ? loc : null;
    }
    private void saveLocation(Location location) {
        SharedPreferences prefs = getPrefs();
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        long ms = System.currentTimeMillis();
        prefs.edit().
                putLong("pos_ms", ms).
                putString("pos_latitude",Double.toString(lat)).
                putString("pos_longitude", Double.toString(lon)).
                putFloat("pos_accuracy", location.hasAccuracy() ? location.getAccuracy() : -1).
                apply();

        Notification notification = createNotification(location);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            getString(R.string.channel_name),
                            NotificationManager
                                    .IMPORTANCE_LOW);

            channel.setDescription(
                    getString(R.string.channel_description));

            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class);

            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        Notification notification = createNotification(getLocation());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo
                            .FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(
                    NOTIFICATION_ID,
                    notification);
        }

        locationClient =
                LocationServices
                        .getFusedLocationProviderClient(this);

        registerBluetoothReceiver();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {

        stopForeground(STOP_FOREGROUND_REMOVE);
        if (bluetoothReceiver != null) {
            try {
                unregisterReceiver(bluetoothReceiver);
            } catch (Exception ignored) {
            }
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
