package net.leobueno.aparcamientobluetooth;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.content.SharedPreferences;

import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.Locale;


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
}
