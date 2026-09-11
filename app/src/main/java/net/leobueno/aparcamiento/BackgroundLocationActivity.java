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

public class BackgroundLocationActivity extends Activity
{
    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(getBaseContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(28), dp(28), dp(28), dp(24));
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        setContentView(layout);
        TextView view;
        view = new TextView(this);
        view.setText(R.string.ubicacion_segundo_plano_titulo);
        view.setTextSize(18);
        view.setPadding(0, dp(18), 0, dp(20));
        view.setGravity(Gravity.LEFT);
        layout.addView(view, matchWrap());
        view = new TextView(this);
        view.setText(R.string.ubicacion_segundo_plano_explicacion);
        view.setPadding(0, dp(18), 0, dp(20));
        view.setTextSize(14);
        view.setGravity(Gravity.LEFT);
        layout.addView(view, matchWrap());
        view = new TextView(this);
        view.setText(R.string.ubicacion_segundo_plano_pasos);
        view.setPadding(0, dp(18), 0, dp(20));
        view.setTextSize(16);
        view.setGravity(Gravity.LEFT);
        layout.addView(view, matchWrap());

        Button accept = new Button(this);
        accept.setText(R.string.abrir_ajustes);
        accept.setOnClickListener(v -> {

            Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())
            );

            startActivity(intent);
        });
        layout.addView(accept, matchWrap());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Si el usuario vuelve de Ajustes habiendo concedido el permiso
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
                checkSelfPermission(
                        android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {

            setResult(RESULT_OK);
            finish();
        }
    }
}