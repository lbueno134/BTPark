package net.leobueno.aparcamiento;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {

    private static final String PREFS = "car_tracker";
    private static final String KEY_ADDRESS = "car_bluetooth_address";

    @Override
    public void onReceive(
            Context context,
            Intent intent) {

        if (!Intent.ACTION_BOOT_COMPLETED.equals(
                intent.getAction())) {
            return;
        }

        Log.d("BootReceiver", "BOOT_COMPLETED recibido");

        if (!context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE)
                .contains(KEY_ADDRESS)) {
            Log.d("BootReceiver", "address not setted");
            return;
        }

        Intent serviceIntent =
                new Intent(
                        context,
                        CarLocationService.class);

        try {
            if (Build.VERSION.SDK_INT >= 26) {
                ContextCompat.startForegroundService(
                        context,
                        serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception ignored) {
            Log.e("BootReceiver", "Error arrancando servicio", ignored);
        }
    }
}
