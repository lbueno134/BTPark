package net.leobueno.aparcamiento;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Activity que informa al usuario sobre la necesidad de conceder
 * permiso de ubicación en segundo plano y le permite abrir
 * directamente los ajustes de la aplicación.
 *
 * La pantalla se crea completamente mediante código, sin utilizar
 * un archivo XML de layout.
 */
public class BackgroundLocationActivity extends Activity
{
    /**
     * Crea unos LayoutParams para que la vista ocupe todo el ancho
     * disponible y tenga la altura necesaria según su contenido.
     */
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Convierte un valor expresado en dp a píxeles.
     *
     * Se utiliza para que los tamaños y márgenes de la interfaz
     * sean independientes de la densidad de pantalla del dispositivo.
     */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * Inicializa la interfaz de la Activity.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Layout principal vertical que contiene todos los elementos
        // de la pantalla.
        LinearLayout layout = new LinearLayout(getBaseContext());
        layout.setOrientation(LinearLayout.VERTICAL);

        // Márgenes interiores del layout.
        layout.setPadding(dp(28), dp(28), dp(28), dp(24));

        // Centra horizontalmente los elementos del layout.
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        // Establece el layout como contenido de la Activity.
        setContentView(layout);

        TextView view;

        // -----------------------------------------------------------------
        // Título
        // -----------------------------------------------------------------

        view = new TextView(this);
        view.setText(R.string.ubicacion_segundo_plano_titulo);
        view.setTextSize(18);
        view.setPadding(0, dp(18), 0, dp(20));
        view.setGravity(Gravity.LEFT);

        layout.addView(view, matchWrap());

        // -----------------------------------------------------------------
        // Explicación del motivo por el que se necesita el permiso
        // -----------------------------------------------------------------

        view = new TextView(this);
        view.setText(R.string.ubicacion_segundo_plano_explicacion);
        view.setPadding(0, dp(18), 0, dp(20));
        view.setTextSize(14);
        view.setGravity(Gravity.LEFT);

        layout.addView(view, matchWrap());

        // -----------------------------------------------------------------
        // Instrucciones que debe seguir el usuario en Ajustes
        // -----------------------------------------------------------------

        view = new TextView(this);
        view.setText(R.string.ubicacion_segundo_plano_pasos);
        view.setPadding(0, dp(18), 0, dp(20));
        view.setTextSize(16);
        view.setGravity(Gravity.LEFT);

        layout.addView(view, matchWrap());

        // -----------------------------------------------------------------
        // Botón para abrir los ajustes de la aplicación
        // -----------------------------------------------------------------

        Button accept = new Button(this);
        accept.setText(R.string.abrir_ajustes);

        accept.setOnClickListener(v -> {

            // Abre la pantalla de información de la aplicación
            // en los ajustes del sistema.
            //
            // Desde ahí el usuario puede acceder a los permisos
            // y seleccionar "Permitir siempre" para la ubicación.
            Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())
            );

            startActivity(intent);
        });

        layout.addView(accept, matchWrap());
    }

    /**
     * Se ejecuta cada vez que la Activity vuelve a estar en primer plano.
     *
     * Esto permite comprobar si el usuario ha concedido el permiso
     * mientras estaba en la pantalla de Ajustes.
     */
    @Override
    protected void onResume() {
        super.onResume();

        // El permiso ACCESS_BACKGROUND_LOCATION existe a partir
        // de Android 10 (API 29 / Q).
        //
        // Si estamos en Android 10 o posterior y el usuario ya ha
        // concedido el permiso de ubicación en segundo plano,
        // comunicamos el resultado correcto a la Activity que
        // inició esta pantalla y cerramos esta Activity.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                checkSelfPermission(
                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {

            // Indica a la Activity llamadora que el permiso ya está concedido.
            setResult(RESULT_OK);

            // Ya no es necesario mostrar esta pantalla.
            finish();
        }
    }
}