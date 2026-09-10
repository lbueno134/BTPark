package net.leobueno.aparcamientobluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
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

import java.util.Calendar;
import java.util.concurrent.atomic.AtomicBoolean;


public class CarLocationService extends Service {

    public static final String CHANNEL_ID = "car_location_channel";
    private static final int NOTIFICATION_ID = 1001;

    private static final String PREFS = "car_tracker";
    private static final String KEY_ADDRESS = "car_bluetooth_address";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable midnightUpdater = new Runnable() {
        @Override
        public void run() {
            sendNotificationToScreen(Tools.getLocation(getBaseContext()));

            // Programar la siguiente medianoche
            scheduleMidnightUpdate();
        }
    };
    private void scheduleMidnightUpdate() {
        Calendar nextMidnight = Calendar.getInstance();

        nextMidnight.add(Calendar.DAY_OF_YEAR, 1);
        nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
        nextMidnight.set(Calendar.MINUTE, 0);
        nextMidnight.set(Calendar.SECOND, 0);
        nextMidnight.set(Calendar.MILLISECOND, 0);

        long delay = nextMidnight.getTimeInMillis()
                - System.currentTimeMillis();

//        handler.postDelayed(midnightUpdater, delay);
        handler.postDelayed(midnightUpdater, 10000);
    }
    private FusedLocationProviderClient locationClient;
    private BroadcastReceiver bluetoothReceiver;
    private final AtomicBoolean gettingLocation = new AtomicBoolean(false);

    private final BroadcastReceiver notificationDeletedReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    boolean recreate = getPrefs().getBoolean("nt_always_on", true);
                    if (recreate && NOTIFICATION_ID == intent.getIntExtra(
                            "notification_id", -1)) {

                        Notification notification =
                                createNotification(Tools.getLocation(context));

                        if (Build.VERSION.SDK_INT < 33 ||
                                ActivityCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS)
                                        == PackageManager.PERMISSION_GRANTED) {

                            NotificationManagerCompat
                                    .from(context)
                                    .notify(NOTIFICATION_ID, notification);
                        }
                    }
                }
            };
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        IntentFilter filter =
                new IntentFilter("net.leobueno.aparcamientobluetooth.NOTIFICATION_DELETED");

        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                    notificationDeletedReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(
                    notificationDeletedReceiver,
                    filter);
        }
        scheduleMidnightUpdate();
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
                String action = intent.getAction();
                if (action != null)
                    switch (action)
                    {
                        case BluetoothDevice.ACTION_ACL_CONNECTED:
                            setConnected(true);
                            Notification notification = createNotification(Tools.getLocation(context));
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
    public void vibrate() {
        VibratorManager vibratorManager = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            vibratorManager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            if (vibratorManager == null) {
                return;
            }
            Vibrator vibrator = vibratorManager.getDefaultVibrator();
            if (!vibrator.hasVibrator()) {
                return;
            }
            VibrationEffect effect = VibrationEffect.createOneShot(
                    500,
                    VibrationEffect.DEFAULT_AMPLITUDE
            );
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

                VibrationAttributes attributes =
                        new VibrationAttributes.Builder()
                                .setUsage(VibrationAttributes.USAGE_NOTIFICATION)
                                .build();
                vibrator.vibrate(effect, attributes);
            } else {
                vibrator.vibrate(effect);
            }
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
        int color = isconnected ? ContextCompat.getColor(this, R.color.bluetooth_blue) : Color.TRANSPARENT;
        Intent deleteIntent = new Intent(
                "net.leobueno.aparcamientobluetooth.NOTIFICATION_DELETED");

        deleteIntent.setPackage(getPackageName());

        deleteIntent.putExtra(
                "notification_id",
                NOTIFICATION_ID);

        PendingIntent deletePendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        NOTIFICATION_ID,
                        deleteIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE);
        if (loc == null)
             Tools.getLocation(this);
        if (loc != null) {
            double lat = loc.getLatitude();
            double lon = loc.getLongitude();
            Uri uri = Uri.parse("geo:" + lat + "," + lon + "?q=" + lat + "," + lon);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage("com.google.android.apps.maps");

            PendingIntent pendingIntent = PendingIntent.getActivity(
                        this,
                        100,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
            String date = Tools.getDate(getBaseContext(), loc.getTime());
            String content = getString(R.string.pulsa_para_ver_la_posici_n_en_google_maps);
            String address = getPrefs().getString("pos_address", null);
            if (address != null)
            {
                content = address+"\n"+content;
            }
            return new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(isconnected ? R.drawable.ic_volante : R.drawable.ic_parking)
                    .setContentTitle(prefix + getString(R.string.coche_aparcado) + date)
                    .setContentText(content)
                    .setContentIntent(pendingIntent)
                    .setColor(color)
                    .setDeleteIntent(deletePendingIntent)
                    .setAutoCancel(false)
                    .setWhen(loc.getTime())
                    .setShowWhen(true)
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
                    .setSmallIcon(isconnected ? R.drawable.ic_volante : R.drawable.ic_parking)
                    .setContentTitle(prefix + getString(R.string.aparcamiento))
                    .setContentText(getString(R.string.donde_he_aparcado))
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setColor(color)
                    .setShowWhen(false)
                    .setDeleteIntent(deletePendingIntent)
                    .setPriority(
                            NotificationCompat.PRIORITY_LOW)
                    .setContentIntent(pendingIntent)
                    .build();
        }
    }
    private void saveLocation(Location location)
    {
        SharedPreferences prefs = getPrefs();
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        long ms = System.currentTimeMillis();
        prefs.edit().
                putLong("pos_ms", ms).
                putString("pos_latitude",Double.toString(lat)).
                putString("pos_longitude", Double.toString(lon)).
                putString("pos_address", null).
                putFloat("pos_accuracy", location.hasAccuracy() ? location.getAccuracy() : -1).
                apply();
        Tools.describe(this, location, description -> {
            getPrefs().edit().putString("pos_address", description).apply();
            sendNotificationToScreen(location);
            if (getPrefs().getBoolean("pk_sound", true)) {
                MediaPlayer mediaPlayer = MediaPlayer.create(
                        this,
                        R.raw.sound
                );

                if (mediaPlayer != null) {
                    mediaPlayer.setOnCompletionListener(MediaPlayer::release);
                    mediaPlayer.start();
                }
                vibrate();
            }
/*            if (getPrefs().getBoolean("pk_sound", true)) {
                Uri notificationSound = RingtoneManager.getDefaultUri(
                        RingtoneManager.TYPE_NOTIFICATION
                );
                Ringtone ringtone = RingtoneManager.getRingtone(
                        getApplicationContext(),
                        notificationSound
                );
                if (ringtone != null) {
                    ringtone.play();
                }
            }*/
        });
        sendNotificationToScreen(location);
    }
    private void sendNotificationToScreen(Location location) {
        Notification notification = createNotification(location);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification);
        notifyParkingUpdated(); //Enviar la notificación para actualizar la pantalla.
    }
    private void notifyParkingUpdated() {

        Intent intent = new Intent("net.leobueno.aparcamientobluetooth.PARKING_UPDATED");

        // Solo para que el broadcast quede dentro de nuestra aplicación
        intent.setPackage(getPackageName());

        sendBroadcast(intent);
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

        Notification notification = createNotification(Tools.getLocation(this));
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
        try {
            unregisterReceiver(notificationDeletedReceiver);
        } catch (Exception ignored) {
        }
        handler.removeCallbacks(midnightUpdater);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
