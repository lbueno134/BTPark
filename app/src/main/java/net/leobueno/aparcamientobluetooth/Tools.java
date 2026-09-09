package net.leobueno.aparcamientobluetooth;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.content.SharedPreferences;
import android.net.Uri;

import java.io.IOException;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class Tools {
    private static final String PREFS = "car_tracker";
    public static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE);
    }
    public static Location getLocation(Context context) {
        Location loc = new Location("gps");
        SharedPreferences prefs = getPrefs(context);
        loc.setLatitude(Double.parseDouble(prefs.getString("pos_latitude", "0")));
        loc.setLongitude(Double.parseDouble(prefs.getString("pos_longitude", "0")));
        loc.setAccuracy(prefs.getFloat("pos_accuracy", 0));
        loc.setTime(prefs.getLong("pos_ms", 0));
        return loc.getTime() != 0 ? loc : null;
    }
    public static String getDate(long time) {
        SimpleDateFormat format = new SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault());
        return format.format(time);
    }
    public static void describe(Context contexto, Location location, Consumer<String> callback) {
        if (location != null) {
            double latitud = location.getLatitude();
            double longitud = location.getLongitude();
            Geocoder geocoder = new Geocoder(contexto, Locale.getDefault());

            // 3. Traducir coordenadas a una descripción (Dirección)
            // Nota: En Android moderno (API 33+) se recomienda usar la versión asíncrona de Geocoder
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(latitud, longitud, 1, new Geocoder.GeocodeListener() {
                    @Override
                    public void onGeocode(List<Address> addresses) {
                        if (!addresses.isEmpty()) {
                            String descripcionLugar = addresses.get(0).getAddressLine(0);
                            callback.accept(descripcionLugar);
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                    }
                });
            } else {
                // Código alternativo para versiones de Android más antiguas (ejecutar preferiblemente en un hilo secundario)
                try {
                    List<Address> addresses = geocoder.getFromLocation(latitud, longitud, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        String descripcionLugar = addresses.get(0).getAddressLine(0);
                        callback.accept(descripcionLugar);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
