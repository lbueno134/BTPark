package net.leobueno.aparcamiento;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * BroadcastReceiver que se ejecuta cuando Android termina de arrancar.
 *
 * Su función es volver a iniciar CarLocationService automáticamente
 * después de un reinicio del dispositivo, siempre que el usuario
 * haya seleccionado previamente un vehículo Bluetooth.
 */
public class BootReceiver extends BroadcastReceiver {

    // Nombre del fichero de preferencias donde se guarda
    // la configuración de la aplicación.
    private static final String PREFS = "car_tracker";

    // Clave que contiene la dirección MAC del dispositivo Bluetooth
    // seleccionado como vehículo.
    private static final String KEY_ADDRESS = "car_bluetooth_address";

    /**
     * Se ejecuta cuando se recibe un broadcast del sistema.
     *
     * @param context contexto proporcionado por Android
     * @param intent  intent que contiene el evento recibido
     */
    @Override
    public void onReceive(
            Context context,
            Intent intent) {

        // Este Receiver puede recibir otros broadcasts.
        // Solo nos interesa el evento de arranque completo del sistema.
        if (!Intent.ACTION_BOOT_COMPLETED.equals(
                intent.getAction())) {
            return;
        }

        Log.d("BootReceiver", "BOOT_COMPLETED recibido");

        // Comprobamos si el usuario ha seleccionado anteriormente
        // un dispositivo Bluetooth como vehículo.
        //
        // Si no existe la dirección Bluetooth, significa que la
        // aplicación todavía no está configurada y no debemos
        // arrancar el servicio.
        if (!context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE)
                .contains(KEY_ADDRESS)) {

            Log.d("BootReceiver", "address not setted");
            return;
        }

        // Creamos el Intent necesario para arrancar
        // CarLocationService.
        Intent serviceIntent =
                new Intent(
                        context,
                        CarLocationService.class);

        try {

            // Desde Android 8.0 (API 26), los servicios que deben
            // continuar ejecutándose en segundo plano tienen que
            // iniciarse como Foreground Service.
            if (Build.VERSION.SDK_INT >= 26) {

                ContextCompat.startForegroundService(
                        context,
                        serviceIntent);

            } else {

                // En versiones anteriores a Android 8.0 se puede
                // utilizar directamente startService().
                context.startService(serviceIntent);
            }

        } catch (Exception ignored) {

            // Si Android impide arrancar el servicio por cualquier
            // motivo, evitamos que el Receiver provoque un fallo
            // de la aplicación y dejamos constancia en el log.
            Log.e(
                    "BootReceiver",
                    "Error arrancando servicio",
                    ignored);
        }
    }
}