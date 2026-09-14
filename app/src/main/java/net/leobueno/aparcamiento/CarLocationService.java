package net.leobueno.aparcamiento;

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


/**
 * Servicio encargado de controlar la conexión Bluetooth del vehículo
 * y guardar automáticamente la posición cuando se desconecta.
 *
 * El servicio funciona como Foreground Service para poder continuar
 * ejecutándose aunque la aplicación no esté visible.
 */
public class CarLocationService extends Service {

    // Identificador del canal de notificaciones.
    public static final String CHANNEL_ID = "car_location_channel";

    // Identificador único de la notificación permanente del servicio.
    private static final int NOTIFICATION_ID = 1001;


    // Nombre de las preferencias donde se guarda la configuración
    // y la información de la última posición.
    private static final String PREFS = "car_tracker";

    // Clave utilizada para guardar la dirección MAC del coche
    // seleccionado por el usuario.
    private static final String KEY_ADDRESS = "car_bluetooth_address";


    // Handler asociado al hilo principal.
    // Se utiliza para programar la actualización de la notificación
    // al cambiar de día.
    private final Handler handler =
            new Handler(Looper.getMainLooper());


    /*
     * Tarea que se ejecuta cuando llega la hora programada.
     *
     * Actualiza la notificación para que Android pueda mostrar
     * correctamente expresiones como "hoy", "ayer", etc.
     */
    private final Runnable midnightUpdater = new Runnable() {

        @Override
        public void run() {

            // Actualizar la notificación utilizando la última posición
            // almacenada.
            sendNotificationToScreen(
                    Tools.getLocation(getBaseContext()));

            // Programar de nuevo la siguiente medianoche.
            scheduleMidnightUpdate();
        }
    };


    /**
     * Programa la actualización de la notificación para la siguiente
     * medianoche.
     */
    private void scheduleMidnightUpdate() {

        Calendar nextMidnight = Calendar.getInstance();

        // Pasar al día siguiente.
        nextMidnight.add(Calendar.DAY_OF_YEAR, 1);

        // Establecer exactamente las 00:00:00.000.
        nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
        nextMidnight.set(Calendar.MINUTE, 0);
        nextMidnight.set(Calendar.SECOND, 0);
        nextMidnight.set(Calendar.MILLISECOND, 0);

        // Calcular cuánto falta hasta la próxima medianoche.
        long delay =
                nextMidnight.getTimeInMillis()
                        - System.currentTimeMillis();

        /*
         * Programación normal para medianoche.
         *
         * Actualmente está desactivada y se utiliza 10 segundos
         * para realizar pruebas.
         */
//        handler.postDelayed(midnightUpdater, delay);

        // TEMPORAL: ejecutar la actualización cada 10 segundos.
        handler.postDelayed(midnightUpdater, 10000);
    }


    // Cliente de Google Play Services utilizado para obtener
    // la posición mediante el proveedor de localización fusionado.
    private FusedLocationProviderClient locationClient;


    // Receiver que recibe los cambios de estado del Bluetooth
    // del dispositivo configurado como coche.
    private BroadcastReceiver bluetoothReceiver;


    /*
     * Indica si ya hay una petición de localización en curso.
     *
     * AtomicBoolean evita que dos eventos de desconexión Bluetooth
     * puedan iniciar simultáneamente dos peticiones de localización.
     */
    private final AtomicBoolean gettingLocation =
            new AtomicBoolean(false);


    /**
     * Receiver utilizado para detectar que el usuario ha eliminado
     * manualmente la notificación.
     *
     * Si la opción "Always keep the notification" está activada,
     * la notificación se vuelve a crear automáticamente.
     */
    private final BroadcastReceiver notificationDeletedReceiver =
            new BroadcastReceiver() {

                @Override
                public void onReceive(
                        Context context,
                        Intent intent) {

                    // Comprobar si el usuario ha activado la opción
                    // de mantener siempre visible la notificación.
                    boolean recreate =
                            getPrefs().getBoolean(
                                    "nt_always_on",
                                    true);

                    // Comprobar que la notificación eliminada es
                    // la notificación de nuestro servicio.
                    if (recreate &&
                            NOTIFICATION_ID ==
                                    intent.getIntExtra(
                                            "notification_id",
                                            -1)) {

                        // Crear de nuevo la notificación utilizando
                        // la última posición conocida.
                        Notification notification =
                                createNotification(
                                        Tools.getLocation(context));

                        // Desde Android 13 es necesario comprobar
                        // el permiso de notificaciones.
                        if (Build.VERSION.SDK_INT < 33 ||
                                ActivityCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS)
                                        == PackageManager.PERMISSION_GRANTED) {

                            // Volver a mostrar la notificación.
                            NotificationManagerCompat
                                    .from(context)
                                    .notify(
                                            NOTIFICATION_ID,
                                            notification);
                        }
                    }
                }
            };


    /**
     * Inicialización del servicio.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {

        super.onCreate();

        // Crear el canal de notificaciones.
        createNotificationChannel();


        // Receiver utilizado para detectar cuando Android
        // elimina la notificación.
        IntentFilter filter =
                new IntentFilter(
                        "net.leobueno.aparcamientobluetooth.NOTIFICATION_DELETED");


        // Desde Android 13 hay que indicar explícitamente si un
        // receiver registrado dinámicamente puede recibir broadcasts
        // de otras aplicaciones.
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


        // Programar la actualización de medianoche.
        scheduleMidnightUpdate();
    }


    /**
     * Registra el BroadcastReceiver encargado de detectar
     * las conexiones y desconexiones Bluetooth.
     */
    private void registerBluetoothReceiver() {

        bluetoothReceiver =
                new BroadcastReceiver() {

                    @Override
                    public void onReceive(
                            Context context,
                            Intent intent) {

                        Log.e(
                                "CAR_BT",
                                "******** BROADCAST ********");

                        Log.e(
                                "CAR_BT",
                                "ACTION = " + intent.getAction());


                        BluetoothDevice device;


                        // Obtener el dispositivo Bluetooth que ha generado
                        // el evento.
                        //
                        // A partir de Android 13 se utiliza la versión tipada
                        // de getParcelableExtra().
                        if (Build.VERSION.SDK_INT >= 33) {

                            device =
                                    intent.getParcelableExtra(
                                            BluetoothDevice.EXTRA_DEVICE,
                                            BluetoothDevice.class);

                        } else {

                            device =
                                    intent.getParcelableExtra(
                                            BluetoothDevice.EXTRA_DEVICE);
                        }


                        // Si no se ha podido obtener el dispositivo,
                        // no podemos continuar.
                        if (device == null) {
                            return;
                        }


                        // Desde Android 12 (API 31) es necesario disponer
                        // del permiso BLUETOOTH_CONNECT para acceder
                        // a información del dispositivo Bluetooth.
                        if (Build.VERSION.SDK_INT >=
                                Build.VERSION_CODES.S &&
                                ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.BLUETOOTH_CONNECT)
                                        != PackageManager.PERMISSION_GRANTED) {

                            return;
                        }


                        // Recuperar la dirección Bluetooth configurada
                        // por el usuario.
                        String configuredAddress =
                                getSharedPreferences(
                                        PREFS,
                                        MODE_PRIVATE)
                                        .getString(
                                                KEY_ADDRESS,
                                                null);


                        // Si no hay coche configurado, ignorar el evento.
                        if (configuredAddress == null) {
                            return;
                        }


                        // Comprobar que el dispositivo que ha generado
                        // el evento es exactamente el coche configurado.
                        if (!configuredAddress.equals(
                                device.getAddress())) {

                            return;
                        }


                        // Obtener el tipo de evento Bluetooth.
                        String action = intent.getAction();

                        if (action != null)

                            switch (action) {


                                /*
                                 * El coche se ha conectado.
                                 */
                                case BluetoothDevice.ACTION_ACL_CONNECTED:

                                    // Guardar el nuevo estado Bluetooth.
                                    setConnected(true);


                                    // Actualizar la notificación.
                                    Notification notification =
                                            createNotification(
                                                    Tools.getLocation(context));


                                    // Comprobar permiso para mostrar
                                    // notificaciones.
                                    if (ActivityCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS)
                                            != PackageManager.PERMISSION_GRANTED) {

                                        return;
                                    }


                                    // Actualizar la notificación existente.
                                    NotificationManagerCompat
                                            .from(context)
                                            .notify(
                                                    NOTIFICATION_ID,
                                                    notification);

                                    break;


                                /*
                                 * El coche se ha desconectado.
                                 */
                                case BluetoothDevice.ACTION_ACL_DISCONNECTED:

                                    // Guardar el estado como desconectado.
                                    setConnected(false);

                                    // Obtener la posición actual y guardarla
                                    // como nueva posición de aparcamiento.
                                    obtainAndSaveLocation();

                                    break;
                            }
                    }
                };


        /*
         * Crear el filtro de eventos Bluetooth.
         */
        IntentFilter filter =
                new IntentFilter(
                        BluetoothDevice.ACTION_ACL_DISCONNECTED);

        filter.addAction(
                BluetoothDevice.ACTION_ACL_DISCONNECTED);

        filter.addAction(
                BluetoothDevice.ACTION_ACL_CONNECTED);


        // Registrar el receiver.
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


    /**
     * Hace vibrar el dispositivo durante 500 ms.
     *
     * En Android 12 o superior se utiliza VibratorManager.
     */
    public void vibrate() {

        VibratorManager vibratorManager = null;


        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S) {

            // Obtener el administrador de vibración.
            vibratorManager =
                    (VibratorManager)
                            getSystemService(
                                    Context.VIBRATOR_MANAGER_SERVICE);


            // Comprobar que está disponible.
            if (vibratorManager == null) {
                return;
            }


            Vibrator vibrator =
                    vibratorManager.getDefaultVibrator();


            // Comprobar que el dispositivo dispone de vibrador.
            if (!vibrator.hasVibrator()) {
                return;
            }


            // Crear una vibración de 500 ms
            // con amplitud predeterminada.
            VibrationEffect effect =
                    VibrationEffect.createOneShot(
                            500,
                            VibrationEffect.DEFAULT_AMPLITUDE);


            // Android 13 permite especificar el uso de la vibración.
            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU) {

                VibrationAttributes attributes =
                        new VibrationAttributes.Builder()
                                .setUsage(
                                        VibrationAttributes
                                                .USAGE_NOTIFICATION)
                                .build();

                vibrator.vibrate(
                        effect,
                        attributes);

            } else {

                vibrator.vibrate(effect);
            }
        }
    }


    /**
     * Solicita una única posición de alta precisión.
     *
     * Esta función se utiliza cuando se detecta la desconexión
     * Bluetooth del vehículo.
     */
    private void obtainAndSaveLocation() {

        /*
         * Evitar realizar simultáneamente dos solicitudes
         * de localización.
         */
        if (!gettingLocation.compareAndSet(false, true)) {
            return;
        }


        /*
         * Comprobar que tenemos permiso para acceder a la ubicación.
         */
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&

                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            // Liberar el indicador porque no se ha podido
            // iniciar la petición.
            gettingLocation.set(false);

            return;
        }


        /*
         * Crear una petición de localización.
         *
         * PRIORITY_HIGH_ACCURACY solicita la máxima precisión
         * disponible.
         *
         * 1000 ms = intervalo deseado de actualización.
         */
        LocationRequest request =
                new LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        1000)

                        // Intervalo mínimo entre actualizaciones.
                        .setMinUpdateIntervalMillis(500)

                        // Máximo retraso permitido para agrupar
                        // actualizaciones.
                        .setMaxUpdateDelayMillis(2000)

                        // Solo necesitamos una posición.
                        .setMaxUpdates(1)

                        .build();


        /*
         * Callback que recibe el resultado de la localización.
         */
        LocationCallback callback =
                new LocationCallback() {

                    @Override
                    public void onLocationResult(
                            LocationResult result) {

                        // Obtener la última posición recibida.
                        Location location =
                                result.getLastLocation();


                        // Si se ha obtenido una posición válida,
                        // guardarla como posición de aparcamiento.
                        if (location != null) {
                            saveLocation(location);
                        }


                        // Dejar de recibir actualizaciones.
                        locationClient.removeLocationUpdates(this);

                        // Permitir una nueva solicitud posteriormente.
                        gettingLocation.set(false);
                    }
                };


        // Solicitar la posición.
        locationClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper());
    }


    /**
     * Devuelve las SharedPreferences utilizadas por el servicio.
     */
    SharedPreferences getPrefs() {

        return getSharedPreferences(
                PREFS,
                MODE_PRIVATE);
    }


    /**
     * Guarda el estado actual de la conexión Bluetooth.
     *
     * @param flag true si el coche está conectado,
     *             false si está desconectado.
     */
    private void setConnected(boolean flag) {

        SharedPreferences prefs = getPrefs();

        prefs.edit()
                .putBoolean("bt_connected", flag)
                .apply();
    }


    /**
     * Crea la notificación permanente del servicio.
     *
     * Si existe una posición de aparcamiento, al pulsarla se abre
     * Google Maps en dicha posición.
     *
     * Si todavía no existe una posición, al pulsarla se abre
     * la aplicación.
     *
     * @param loc última posición conocida.
     */
    public Notification createNotification(Location loc) {

        // Obtener el estado actual de Bluetooth.
        boolean isconnected =
                getPrefs().getBoolean(
                        "bt_connected",
                        false);


        // Mostrar el símbolo ⚡ cuando el coche está conectado.
        String prefix =
                isconnected ? "⚡" : "";


        // Utilizar el azul Bluetooth cuando está conectado.
        // En caso contrario no establecer color.
        int color =
                isconnected
                        ? ContextCompat.getColor(
                        this,
                        R.color.bluetooth_blue)
                        : Color.TRANSPARENT;


        /*
         * Intent que será ejecutado cuando Android elimine
         * la notificación.
         */
        Intent deleteIntent =
                new Intent(
                        "net.leobueno.aparcamientobluetooth.NOTIFICATION_DELETED");


        // Asegurar que el broadcast permanece dentro de nuestra aplicación.
        deleteIntent.setPackage(
                getPackageName());


        // Indicar qué notificación ha sido eliminada.
        deleteIntent.putExtra(
                "notification_id",
                NOTIFICATION_ID);


        /*
         * PendingIntent utilizado por setDeleteIntent().
         */
        PendingIntent deletePendingIntent =
                PendingIntent.getBroadcast(
                        this,
                        NOTIFICATION_ID,
                        deleteIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                                PendingIntent.FLAG_IMMUTABLE);


        // Si no se ha recibido una posición, intentar obtener
        // la última posición almacenada.
        if (loc == null)
            Tools.getLocation(this);


        /*
         * Tenemos una posición de aparcamiento.
         */
        if (loc != null) {

            double lat =
                    loc.getLatitude();

            double lon =
                    loc.getLongitude();


            // Crear una URI geo: para abrir la posición
            // directamente en Google Maps.
            Uri uri =
                    Uri.parse(
                            "geo:" +
                                    lat +
                                    "," +
                                    lon +
                                    "?q=" +
                                    lat +
                                    "," +
                                    lon);


            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            uri);


            // Intentar abrir específicamente Google Maps.
            intent.setPackage(
                    "com.google.android.apps.maps");


            // PendingIntent que abrirá Google Maps
            // cuando el usuario pulse la notificación.
            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            this,
                            100,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT |
                                    PendingIntent.FLAG_IMMUTABLE);


            // Formatear la fecha/hora de la posición.
            String date =
                    Tools.getDate(
                            getBaseContext(),
                            loc.getTime());


            // Texto que indica que se puede pulsar
            // para ver la posición en Google Maps.
            String content =
                    getString(
                            R.string.pulsa_para_ver_la_posici_n_en_google_maps);


            // Intentar obtener la dirección asociada a la posición.
            String address =
                    getPrefs().getString(
                            "pos_address",
                            null);


            // Si tenemos dirección, mostrarla antes del texto.
            if (address != null) {

                content =
                        address +
                                "\n" +
                                content;
            }


            /*
             * Crear la notificación con la posición guardada.
             */
            return new NotificationCompat.Builder(
                    this,
                    CHANNEL_ID)

                    // Icono diferente según el estado Bluetooth.
                    .setSmallIcon(
                            isconnected
                                    ? R.drawable.ic_volante
                                    : R.drawable.ic_parking)

                    // Título con el estado Bluetooth y la fecha.
                    .setContentTitle(
                            prefix +
                                    getString(
                                            R.string.coche_aparcado) +
                                    date)

                    // Dirección + explicación.
                    .setContentText(content)

                    // Acción al pulsar la notificación.
                    .setContentIntent(pendingIntent)

                    // Color de la notificación.
                    .setColor(color)

                    // Intent ejecutado al eliminarla.
                    .setDeleteIntent(
                            deletePendingIntent)

                    // No eliminarla automáticamente al pulsarla.
                    .setAutoCancel(false)

                    // Utilizar la fecha real del aparcamiento.
                    .setWhen(loc.getTime())

                    // Mostrar la fecha/hora.
                    .setShowWhen(true)

                    // Categorizarla como notificación de servicio.
                    .setCategory(
                            Notification.CATEGORY_SERVICE)

                    // Mantenerla como notificación permanente.
                    .setOngoing(true)

                    .build();
        }


        /*
         * Todavía no existe una posición de aparcamiento.
         *
         * En este caso la notificación abre la actividad principal.
         */
        else {

            Intent openApp =
                    new Intent(
                            this,
                            MainActivity.class);

            // Indicar a MainActivity que queremos abrir
            // la última posición disponible.
            openApp.putExtra(
                    "openGeo",
                    "last");


            PendingIntent pendingIntent =
                    PendingIntent.getActivity(
                            this,
                            0,
                            openApp,
                            PendingIntent.FLAG_UPDATE_CURRENT |
                                    (Build.VERSION.SDK_INT >= 23
                                            ? PendingIntent.FLAG_IMMUTABLE
                                            : 0));


            return new NotificationCompat.Builder(
                    this,
                    CHANNEL_ID)

                    // Icono según estado Bluetooth.
                    .setSmallIcon(
                            isconnected
                                    ? R.drawable.ic_volante
                                    : R.drawable.ic_parking)

                    // Título de la notificación.
                    .setContentTitle(
                            prefix +
                                    getString(
                                            R.string.aparcamiento))

                    // Texto indicando que todavía no hay posición.
                    .setContentText(
                            getString(
                                    R.string.donde_he_aparcado))

                    // Mantener la notificación visible.
                    .setOngoing(true)

                    .setAutoCancel(false)

                    .setCategory(
                            Notification.CATEGORY_SERVICE)

                    // No mostrar hora en este caso.
                    .setShowWhen(false)

                    // Permitir detectar si el usuario la elimina.
                    .setDeleteIntent(
                            deletePendingIntent)

                    // Prioridad baja para evitar molestias.
                    .setPriority(
                            NotificationCompat.PRIORITY_LOW)

                    // Abrir la aplicación al pulsar.
                    .setContentIntent(
                            pendingIntent)

                    .build();
        }
    }


    /**
     * Guarda una nueva posición de aparcamiento.
     */
    private void saveLocation(Location location) {

        SharedPreferences prefs =
                getPrefs();


        // Obtener coordenadas.
        double lat =
                location.getLatitude();

        double lon =
                location.getLongitude();


        // Momento en el que se guarda la posición.
        long ms =
                System.currentTimeMillis();


        /*
         * Guardar la información básica de la posición.
         */
        prefs.edit()
                .putLong(
                        "pos_ms",
                        ms)

                .putString(
                        "pos_latitude",
                        Double.toString(lat))

                .putString(
                        "pos_longitude",
                        Double.toString(lon))

                // La dirección anterior deja de ser válida.
                .putString(
                        "pos_address",
                        null)

                // Guardar la precisión obtenida.
                .putFloat(
                        "pos_accuracy",
                        location.hasAccuracy()
                                ? location.getAccuracy()
                                : -1)

                .apply();


        /*
         * Convertir las coordenadas en una dirección legible.
         *
         * Tools.describe() realiza esta operación de forma
         * asíncrona y devuelve la descripción mediante el callback.
         */
        Tools.describe(
                this,
                location,
                description -> {

                    // Guardar la dirección obtenida.
                    getPrefs()
                            .edit()
                            .putString(
                                    "pos_address",
                                    description)
                            .apply();


                    // Actualizar la notificación para mostrar
                    // también la dirección.
                    sendNotificationToScreen(
                            location);


                    /*
                     * Reproducir sonido y vibración si el usuario
                     * tiene activada la opción correspondiente.
                     */
                    if (getPrefs().getBoolean(
                            "pk_sound",
                            true)) {

                        // Crear el reproductor con el sonido
                        // incluido en res/raw.
                        MediaPlayer mediaPlayer =
                                MediaPlayer.create(
                                        this,
                                        R.raw.sound);


                        if (mediaPlayer != null) {

                            // Liberar los recursos cuando termine.
                            mediaPlayer.setOnCompletionListener(
                                    MediaPlayer::release);

                            // Reproducir el sonido.
                            mediaPlayer.start();
                        }


                        // Vibrar.
                        vibrate();
                    }


                    /*
                     * Código anterior para utilizar el sonido
                     * predeterminado de Android.
                     *
                     * Actualmente está desactivado.
                     */
                    /*
                    if (getPrefs().getBoolean("pk_sound", true)) {

                        Uri notificationSound =
                                RingtoneManager.getDefaultUri(
                                        RingtoneManager.TYPE_NOTIFICATION);

                        Ringtone ringtone =
                                RingtoneManager.getRingtone(
                                        getApplicationContext(),
                                        notificationSound);

                        if (ringtone != null) {
                            ringtone.play();
                        }
                    }
                    */
                });


        /*
         * Actualizar inmediatamente la notificación.
         *
         * Esto permite mostrar las coordenadas aunque todavía
         * no se haya terminado de obtener la dirección.
         */
        sendNotificationToScreen(location);
    }


    /**
     * Actualiza la notificación mostrada en pantalla.
     */
    private void sendNotificationToScreen(
            Location location) {

        // Crear una nueva notificación con la posición actual.
        Notification notification =
                createNotification(location);


        // Desde Android 13 es necesario disponer del permiso
        // POST_NOTIFICATIONS.
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {

            return;
        }


        // Reemplazar la notificación anterior.
        NotificationManagerCompat
                .from(this)
                .notify(
                        NOTIFICATION_ID,
                        notification);


        // Avisar a la actividad principal de que la posición
        // de aparcamiento ha cambiado.
        notifyParkingUpdated();
    }


    /**
     * Envía un broadcast interno a la aplicación para informar
     * de que se ha actualizado la posición de aparcamiento.
     */
    private void notifyParkingUpdated() {

        Intent intent =
                new Intent(
                        "net.leobueno.aparcamientobluetooth.PARKING_UPDATED");


        // Limitar el broadcast a nuestra propia aplicación.
        intent.setPackage(
                getPackageName());


        // Enviar el evento.
        sendBroadcast(intent);
    }


    /**
     * Crea el NotificationChannel utilizado por el servicio.
     *
     * Desde Android 8.0 todos los servicios que muestran
     * notificaciones deben utilizar un canal.
     */
    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= 26) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            getString(
                                    R.string.channel_name),
                            NotificationManager
                                    .IMPORTANCE_LOW);


            // Descripción visible en los ajustes de Android.
            channel.setDescription(
                    getString(
                            R.string.channel_description));


            // Obtener el administrador de notificaciones.
            NotificationManager manager =
                    getSystemService(
                            NotificationManager.class);


            // Registrar el canal.
            manager.createNotificationChannel(
                    channel);
        }
    }


    /**
     * Se ejecuta cada vez que Android inicia el servicio.
     *
     * Aquí se convierte el servicio en Foreground Service y se
     * inicializan los componentes necesarios.
     */
    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {


        // Crear inicialmente la notificación del servicio.
        Notification notification =
                createNotification(
                        Tools.getLocation(this));


        /*
         * Desde Android 10 se puede indicar explícitamente
         * que el Foreground Service utiliza la ubicación.
         */
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q) {

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


        // Obtener el cliente de localización de Google.
        locationClient =
                LocationServices
                        .getFusedLocationProviderClient(
                                this);


        // Empezar a escuchar los cambios de conexión
        // del dispositivo Bluetooth configurado.
        registerBluetoothReceiver();


        /*
         * Pedir a Android que intente mantener el servicio
         * ejecutándose si el proceso es eliminado.
         */
        return START_STICKY;
    }


    /**
     * Se ejecuta cuando Android destruye el servicio.
     *
     * Aquí se liberan todos los receivers y tareas pendientes.
     */
    @Override
    public void onDestroy() {

        // Eliminar la notificación del Foreground Service.
        stopForeground(
                STOP_FOREGROUND_REMOVE);


        // Desregistrar el receiver Bluetooth.
        if (bluetoothReceiver != null) {

            try {

                unregisterReceiver(
                        bluetoothReceiver);

            } catch (Exception ignored) {
                // El receiver podría no estar registrado.
            }
        }


        // Desregistrar el receiver de eliminación
        // de la notificación.
        try {

            unregisterReceiver(
                    notificationDeletedReceiver);

        } catch (Exception ignored) {
            // El receiver podría no estar registrado.
        }


        // Cancelar la actualización programada de medianoche.
        handler.removeCallbacks(
                midnightUpdater);


        super.onDestroy();
    }


    /**
     * Este servicio no permite que otras partes de la aplicación
     * se conecten a él mediante bindService().
     *
     * Por eso devuelve null.
     */
    @Nullable
    @Override
    public IBinder onBind(
            Intent intent) {

        return null;
    }
}