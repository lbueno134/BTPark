package net.leobueno.aparcamiento;

// -------------------------------------------------------------------------
// IMPORTACIONES ANDROID
// -------------------------------------------------------------------------

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

// -------------------------------------------------------------------------
// IMPORTACIONES ANDROIDX
// -------------------------------------------------------------------------

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// -------------------------------------------------------------------------
// IMPORTACIONES JAVA
// -------------------------------------------------------------------------

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;


// ========================================================================
// CATEGORÍAS DE DISPOSITIVOS BLUETOOTH
// ========================================================================

/**
 * Categorías utilizadas para clasificar los dispositivos Bluetooth
 * que aparecen en el Spinner.
 *
 * El orden de estas categorías también determina su prioridad de
 * ordenación, ya que posteriormente se utiliza ordinal().
 */
enum BTCategory
{
    BTCar,       // Dispositivo Bluetooth de coche
    BTPhones,    // Auriculares
    BTAudio,     // Equipos de audio
    BTComputer,  // Ordenadores
    BTMobile,    // Teléfonos móviles
    BTWearable,  // Dispositivos llevables
    BTGlasses,   // Gafas/dispositivos similares
    BTNone,      // Sin categoría
    BTOther      // Otros dispositivos
}


// ========================================================================
// ACTIVITY PRINCIPAL
// ========================================================================

public class MainActivity extends Activity
{
    // ---------------------------------------------------------------------
    // CÓDIGOS DE PETICIONES
    // ---------------------------------------------------------------------

    // Código utilizado para identificar la petición de permisos normales.
    private static final int REQUEST_PERMISSIONS = 10;

    // Código utilizado cuando se abre BackgroundLocationActivity.
    private static final int REQUEST_BACKGROUND_LOCATION = 11;


    // ---------------------------------------------------------------------
    // SHARED PREFERENCES
    // ---------------------------------------------------------------------

    // Nombre del fichero de preferencias utilizado por la aplicación.
    private static final String PREFS = "car_tracker";

    // Clave donde se guarda la dirección MAC del Bluetooth seleccionado.
    private static final String KEY_ADDRESS = "car_bluetooth_address";

    // Clave donde se guarda el nombre del Bluetooth seleccionado.
    private static final String KEY_NAME = "car_bluetooth_name";


    // ---------------------------------------------------------------------
    // ELEMENTOS DE LA INTERFAZ
    // ---------------------------------------------------------------------

    // Lista desplegable donde se selecciona el Bluetooth del coche.
    private Spinner bluetoothSpinner;

    // Texto que muestra el estado del seguimiento.
    private TextView statusText;

    // Texto que muestra la información del último aparcamiento.
    private TextView lastParkingText;


    // ---------------------------------------------------------------------
    // DISPOSITIVOS BLUETOOTH
    // ---------------------------------------------------------------------

    /**
     * Lista de dispositivos Bluetooth emparejados.
     *
     * El primer elemento es normalmente null y representa la opción
     * "Desactivar".
     */
    private final List<BluetoothDevice> devices =
            new ArrayList<>();


    // ---------------------------------------------------------------------
    // ACTUALIZACIÓN PERIÓDICA
    // ---------------------------------------------------------------------

    // Handler asociado al hilo principal de Android.
    private final Handler handler =
            new Handler(Looper.getMainLooper());

    // Timestamp de la última ubicación conocida.
    private long lastRefresh = 0;

    // Estado de conexión Bluetooth de la última comprobación.
    private boolean lastConnection = true;


    // =====================================================================
    // RECEIVER DE ACTUALIZACIÓN DEL APARCAMIENTO
    // =====================================================================

    /**
     * Receiver que recibe un broadcast enviado por el servicio
     * cuando se actualiza la posición del aparcamiento.
     */
    private final BroadcastReceiver parkingReceiver =
            new BroadcastReceiver()
            {
                @Override
                public void onReceive(
                        Context context,
                        Intent intent)
                {
                    // Comprobamos que el broadcast corresponde
                    // al evento que nos interesa.
                    if ("net.leobueno.aparcamientobluetooth.PARKING_UPDATED"
                            .equals(intent.getAction()))
                    {
                        // Actualizamos inmediatamente la pantalla.
                        updateScreen();
                    }
                }
            };


    // =====================================================================
    // TAREA DE ACTUALIZACIÓN CADA SEGUNDO
    // =====================================================================

    /**
     * Comprueba periódicamente si ha cambiado:
     *
     * - la posición almacenada;
     * - el estado de conexión Bluetooth.
     *
     * Si alguno de estos valores cambia, actualiza la interfaz.
     */
    private final Runnable refresh =
            new Runnable()
            {
                @Override
                public void run()
                {
                    // Obtiene la última posición almacenada.
                    Location loc =
                            Tools.getLocation(
                                    getBaseContext());

                    // Utilizamos la fecha/hora de la ubicación como
                    // identificador de si la posición ha cambiado.
                    //
                    // Si no existe ubicación, utilizamos 0.
                    long newRefresh =
                            loc != null
                                    ? loc.getTime()
                                    : 0;

                    // Comprueba el estado actual de Bluetooth.
                    boolean newConnection =
                            isConnected();


                    // Si ha cambiado la ubicación o el estado
                    // de conexión, actualizamos la pantalla.
                    if (newRefresh != lastRefresh ||
                            newConnection != lastConnection)
                    {
                        updateScreen();
                    }


                    // Guardamos los valores actuales para compararlos
                    // en la siguiente ejecución.
                    lastConnection = newConnection;
                    lastRefresh = newRefresh;


                    // Programa la siguiente ejecución dentro de 1 segundo.
                    handler.postDelayed(
                            this,
                            1000);
                }
            };


    // =====================================================================
    // CICLO DE VIDA: CREACIÓN
    // =====================================================================

    @Override
    protected void onCreate(
            Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Construye la interfaz gráfica.
        createInterface();

        // Comprueba y solicita los permisos necesarios.
        requestPermissionsIfNeeded();


        // Comprueba si la Activity ha recibido un parámetro
        // solicitando abrir el último aparcamiento.
        Intent intent = getIntent();

        String open =
                intent.getStringExtra("openGeo");

        if (open != null &&
                open.equals("last"))
        {
            openLastParking();
        }
    }


    // =====================================================================
    // CICLO DE VIDA: INICIO
    // =====================================================================

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onStart()
    {
        super.onStart();


        // Crea el filtro para recibir el evento de actualización
        // del aparcamiento.
        IntentFilter filter =
                new IntentFilter(
                        "net.leobueno.aparcamientobluetooth.PARKING_UPDATED");


        // Android 13 (API 33) requiere indicar si el receiver
        // puede recibir broadcasts de otras aplicaciones.
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.TIRAMISU)
        {
            registerReceiver(
                    parkingReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED);
        }
        else
        {
            // Para versiones anteriores se utiliza la forma tradicional.
            registerReceiver(
                    parkingReceiver,
                    filter);
        }


        // Actualiza la información mostrada.
        updateScreen();
    }


    // =====================================================================
    // CICLO DE VIDA: PARADA
    // =====================================================================

    @Override
    protected void onStop()
    {
        super.onStop();

        // Dejamos de recibir el broadcast que habíamos registrado
        // en onStart().
        unregisterReceiver(parkingReceiver);
    }


    // =====================================================================
    // CICLO DE VIDA: VUELTA A PRIMER PLANO
    // =====================================================================

    @Override
    protected void onResume()
    {
        super.onResume();


        // Si la interfaz ya ha sido creada, volvemos a cargar
        // los dispositivos Bluetooth.
        //
        // Esto es especialmente útil al volver desde Ajustes de Android.
        if (bluetoothSpinner != null)
        {
            loadBluetoothDevices();
            updateScreen();
        }


        // Inicia la comprobación periódica.
        handler.post(refresh);
    }


    // =====================================================================
    // CICLO DE VIDA: PÉRDIDA DE PRIMER PLANO
    // =====================================================================

    @Override
    protected void onPause()
    {
        super.onPause();

        // Detiene la tarea periódica mientras la Activity
        // no está en primer plano.
        handler.removeCallbacks(refresh);
    }


    // =====================================================================
    // CREAR SEPARADOR
    // =====================================================================

    /**
     * Crea una línea horizontal utilizada para separar las diferentes
     * secciones de la interfaz.
     */
    View splitter()
    {
        // Crea una View vacía que utilizaremos como línea.
        View divider =
                new View(this);


        // Color del separador.
        divider.setBackgroundColor(
                Color.parseColor("#888888"));


        // Grosor de la línea: 1 dp convertido a píxeles.
        int thicknessInPx =
                (int) (
                        1 *
                                getResources()
                                        .getDisplayMetrics()
                                        .density);


        // El separador ocupa todo el ancho disponible.
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        thicknessInPx);


        // Margen vertical de 8 dp.
        int marginInPx =
                (int) (
                        8 *
                                getResources()
                                        .getDisplayMetrics()
                                        .density);


        params.setMargins(
                0,
                marginInPx,
                0,
                marginInPx);


        // Aplica los parámetros a la vista.
        divider.setLayoutParams(params);

        return divider;
    }


    // =====================================================================
    // HACER UNA VISTA CLICABLE
    // =====================================================================

    /**
     * Hace que una vista pueda recibir pulsaciones.
     *
     * También le proporciona el efecto visual estándar de Android
     * al pulsarla.
     *
     * @param text vista que se hará clicable.
     * @param listener código que se ejecutará al pulsarla.
     */
    void setClickable(
            View text,
            View.OnClickListener listener)
    {
        // Define la acción al pulsar.
        text.setOnClickListener(listener);

        // Indica que la vista puede recibir clics.
        text.setClickable(true);

        // Permite también el foco.
        text.setFocusable(true);

        // Utiliza el selector visual estándar de Android.
        text.setBackground(
                getDrawable(
                        android.R.drawable
                                .list_selector_background));
    }


    // =====================================================================
    // CREACIÓN DE LA INTERFAZ
    // =====================================================================

    /**
     * Construye toda la interfaz mediante código Java.
     *
     * No se utiliza ningún archivo XML de layout.
     */
    private void createInterface()
    {
        // ScrollView para permitir desplazarse verticalmente
        // cuando el contenido no cabe en la pantalla.
        ScrollView scroll =
                new ScrollView(this);


        // Layout vertical que contendrá todos los elementos.
        LinearLayout root =
                new LinearLayout(this);

        scroll.addView(root);


        // Los elementos se colocan verticalmente.
        root.setOrientation(
                LinearLayout.VERTICAL);


        // Márgenes interiores de la pantalla.
        root.setPadding(
                dp(28),
                dp(28),
                dp(28),
                dp(24));


        // Centra horizontalmente los elementos.
        root.setGravity(
                Gravity.CENTER_HORIZONTAL);


        // -----------------------------------------------------------------
        // CABECERA
        // -----------------------------------------------------------------

        FrameLayout titleContainer =
                new FrameLayout(this);


        // Texto del título.
        TextView title =
                new TextView(this);

        title.setText(
                R.string.aparcamiento);

        title.setTextSize(28);

        title.setGravity(
                Gravity.CENTER);


        // El título ocupa todo el ancho del contenedor.
        FrameLayout.LayoutParams titleParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.CENTER);


        titleContainer.addView(
                title,
                titleParams);


        // -----------------------------------------------------------------
        // ICONO DE LA APLICACIÓN
        // -----------------------------------------------------------------

        ImageView icon =
                new ImageView(this);

        icon.setImageResource(
                R.mipmap.ic_launcher);


        // Tamaño del icono: 48 x 48 dp.
        FrameLayout.LayoutParams iconParams =
                new FrameLayout.LayoutParams(
                        dp(48),
                        dp(48),
                        Gravity.CENTER_VERTICAL);


        // Separación respecto al borde izquierdo.
        iconParams.leftMargin =
                dp(12);


        titleContainer.addView(
                icon,
                iconParams);


        // Añade la cabecera completa al layout principal.
        root.addView(
                titleContainer,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(56)));


        // Separador después de la cabecera.
        root.addView(splitter());


        // -----------------------------------------------------------------
        // ESTADO DEL APARCAMIENTO
        // -----------------------------------------------------------------

        statusText =
                new TextView(this);

        statusText.setTextSize(16);

        statusText.setPadding(
                0,
                dp(4),
                0,
                dp(4));


        root.addView(
                statusText,
                matchWrap());


        // Al pulsar sobre el estado se abre el último aparcamiento.
        setClickable(
                statusText,
                v -> openLastParking());


        // -----------------------------------------------------------------
        // INFORMACIÓN DEL ÚLTIMO APARCAMIENTO
        // -----------------------------------------------------------------

        lastParkingText =
                new TextView(this);

        lastParkingText.setTextSize(14);

        lastParkingText.setPadding(
                0,
                dp(4),
                0,
                dp(8));


        root.addView(
                lastParkingText,
                matchWrap());


        // También se puede pulsar sobre esta información
        // para abrir el aparcamiento en Maps.
        setClickable(
                lastParkingText,
                v -> openLastParking());


        root.addView(splitter());


        // -----------------------------------------------------------------
        // SELECCIÓN DEL BLUETOOTH DEL COCHE
        // -----------------------------------------------------------------

        TextView bluetoothLabel =
                new TextView(this);

        bluetoothLabel.setText(
                R.string.bluetooth_del_coche);

        bluetoothLabel.setTextSize(16);

        bluetoothLabel.setPadding(
                0,
                dp(0),
                0,
                dp(8));


        root.addView(
                bluetoothLabel,
                matchWrap());


        // Spinner donde se muestran los dispositivos emparejados.
        bluetoothSpinner =
                new Spinner(this);

        root.addView(
                bluetoothSpinner,
                matchWrap());


        // Se ejecuta cuando cambia la selección.
        bluetoothSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener()
                {
                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id)
                    {
                        // Guarda el nuevo dispositivo seleccionado.
                        saveConfiguration();
                    }


                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent)
                    {
                        // Si no hay ningún elemento seleccionado,
                        // también guardamos la configuración.
                        saveConfiguration();
                    }
                });


        root.addView(splitter());


        // -----------------------------------------------------------------
        // BOTÓN PARA VER EL ÚLTIMO APARCAMIENTO
        // -----------------------------------------------------------------

        Button mapsButton =
                new Button(this);

        mapsButton.setText(
                R.string.ver_ltimo_aparcamiento);


        mapsButton.setOnClickListener(
                v -> openLastParking());


        root.addView(
                mapsButton,
                matchWrap());


        // -----------------------------------------------------------------
        // OPCIÓN DE SONIDO DE NOTIFICACIÓN
        // -----------------------------------------------------------------

        LinearLayout row;
        TextView label;
        CheckBox check;


        row =
                new LinearLayout(this);

        row.setPadding(
                0,
                dp(5),
                0,
                0);

        row.setOrientation(
                LinearLayout.HORIZONTAL);


        // Centra verticalmente el CheckBox y el texto.
        row.setGravity(
                Gravity.CENTER_VERTICAL);


        label =
                new TextView(this);

        label.setGravity(
                Gravity.CENTER_VERTICAL);

        label.setText(
                R.string.notification_sound);


        check =
                new CheckBox(this);

        check.setGravity(
                Gravity.RIGHT);


        // Recupera la configuración almacenada.
        //
        // true es el valor predeterminado: sonido activado.
        boolean pk_sound =
                getPrefs().getBoolean(
                        "pk_sound",
                        true);

        check.setChecked(pk_sound);


        // Guarda inmediatamente el nuevo estado del CheckBox.
        check.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener()
                {
                    @Override
                    public void onCheckedChanged(
                            CompoundButton buttonView,
                            boolean isCheckedNow)
                    {
                        getPrefs()
                                .edit()
                                .putBoolean(
                                        "pk_sound",
                                        isCheckedNow)
                                .apply();
                    }
                });


        // Añade primero el CheckBox y después el texto.
        row.addView(check);
        row.addView(label);


        root.addView(
                row,
                matchWrap());


        // -----------------------------------------------------------------
        // OPCIÓN DE NOTIFICACIÓN PERSISTENTE
        // -----------------------------------------------------------------

        row =
                new LinearLayout(this);

        row.setPadding(
                0,
                0,
                0,
                dp(5));

        row.setOrientation(
                LinearLayout.HORIZONTAL);

        row.setGravity(
                Gravity.CENTER_VERTICAL);


        label =
                new TextView(this);

        label.setText(
                R.string.hacer_la_notificaci_n_persistente);

        label.setGravity(
                Gravity.CENTER_VERTICAL);


        check =
                new CheckBox(this);

        check.setGravity(
                Gravity.RIGHT);


        // Recupera la configuración.
        //
        // true significa que, por defecto, la notificación
        // debe mantenerse activa.
        boolean recreate =
                getPrefs().getBoolean(
                        "nt_always_on",
                        true);

        check.setChecked(recreate);


        // Guarda el nuevo valor cuando el usuario cambia
        // el estado del CheckBox.
        check.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener()
                {
                    @Override
                    public void onCheckedChanged(
                            CompoundButton buttonView,
                            boolean isCheckedNow)
                    {
                        getPrefs()
                                .edit()
                                .putBoolean(
                                        "nt_always_on",
                                        isCheckedNow)
                                .apply();
                    }
                });


        row.addView(check);
        row.addView(label);


        // Texto explicativo de la notificación.
        TextView explain_notification =
                new TextView(this);

        explain_notification.setTextSize(12);

        explain_notification.setText(
                R.string.explain_notification);


        root.addView(
                explain_notification,
                matchWrap());


        root.addView(
                row,
                matchWrap());


        root.addView(splitter());


        // -----------------------------------------------------------------
        // TÍTULO DEL MANUAL
        // -----------------------------------------------------------------

        TextView titulo_manual =
                new TextView(this);

        titulo_manual.setTextSize(18);

        titulo_manual.setText(
                R.string.instrucciones);


        root.addView(
                titulo_manual,
                matchWrap());


        // -----------------------------------------------------------------
        // TEXTO DEL MANUAL
        // -----------------------------------------------------------------

        TextView manual =
                new TextView(this);

        manual.setTextSize(12);

        manual.setText(
                R.string.manual_text);


        root.addView(
                manual,
                matchWrap());


        // Establece el ScrollView como contenido de la Activity.
        setContentView(scroll);
    }


    // =====================================================================
    // PARÁMETROS DE LAYOUT
    // =====================================================================

    /**
     * Devuelve unos LayoutParams que hacen que la vista:
     *
     * - ocupe todo el ancho disponible;
     * - tenga la altura necesaria según su contenido.
     */
    private LinearLayout.LayoutParams matchWrap()
    {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }


    // =====================================================================
    // CONVERSIÓN DE dp A PÍXELES
    // =====================================================================

    /**
     * Convierte un valor expresado en dp a píxeles.
     *
     * De esta forma los tamaños de la interfaz se adaptan
     * a la densidad de pantalla del dispositivo.
     */
    private int dp(int value)
    {
        return (int) (
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
                        + 0.5f);
    }


    // =====================================================================
    // COMPROBACIÓN Y SOLICITUD DE PERMISOS
    // =====================================================================

    /**
     * Comprueba todos los permisos necesarios para el funcionamiento
     * de la aplicación y solicita los que falten.
     */
    private void requestPermissionsIfNeeded()
    {
        // Lista de permisos que se solicitarán conjuntamente.
        List<String> permissions =
                new ArrayList<>();


        // -----------------------------------------------------------------
        // BLUETOOTH
        // -----------------------------------------------------------------

        // Desde Android 12 (API 31) son necesarios los permisos
        // BLUETOOTH_CONNECT y BLUETOOTH_SCAN.
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S)
        {
            if (!hasPermission(
                    Manifest.permission.BLUETOOTH_CONNECT))
            {
                permissions.add(
                        Manifest.permission.BLUETOOTH_CONNECT);
            }


            if (!hasPermission(
                    Manifest.permission.BLUETOOTH_SCAN))
            {
                permissions.add(
                        Manifest.permission.BLUETOOTH_SCAN);
            }
        }


        // -----------------------------------------------------------------
        // UBICACIÓN PRECISA
        // -----------------------------------------------------------------

        if (!hasPermission(
                Manifest.permission.ACCESS_FINE_LOCATION))
        {
            permissions.add(
                    Manifest.permission.ACCESS_FINE_LOCATION);
        }


        // -----------------------------------------------------------------
        // NOTIFICACIONES
        // -----------------------------------------------------------------

        // Desde Android 13 (API 33) las notificaciones requieren
        // un permiso explícito.
        if (Build.VERSION.SDK_INT >= 33 &&
                !hasPermission(
                        Manifest.permission.POST_NOTIFICATIONS))
        {
            permissions.add(
                    Manifest.permission.POST_NOTIFICATIONS);
        }


        // Comprueba el permiso de ubicación en segundo plano.
        queryBackgroundPermission();


        // Si falta algún permiso, lo solicitamos.
        if (!permissions.isEmpty())
        {
            ActivityCompat.requestPermissions(
                    this,
                    permissions.toArray(
                            new String[0]),
                    REQUEST_PERMISSIONS);
        }
        else
        {
            // Todos los permisos normales ya están concedidos.
            loadBluetoothDevices();
            updateScreen();
        }
    }


    // =====================================================================
    // COMPROBAR UN PERMISO
    // =====================================================================

    /**
     * Comprueba si un permiso concreto ha sido concedido.
     *
     * @param permission permiso que se quiere comprobar.
     * @return true si está concedido.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean hasPermission(
            String permission)
    {
        return ContextCompat.checkSelfPermission(
                this,
                permission)
                == PackageManager.PERMISSION_GRANTED;
    }


    // =====================================================================
    // OBTENER ICONO DE UNA CATEGORÍA BLUETOOTH
    // =====================================================================

    /**
     * Devuelve el icono Unicode correspondiente a una categoría
     * de dispositivo Bluetooth.
     */
    private String iconBT(
            BTCategory category)
    {
        switch (category)
        {
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

            // Estas categorías utilizan el mismo símbolo genérico.
            case BTGlasses:
            case BTNone:
            case BTOther:
            default:
                return "ᛒ";
        }
    }


    // =====================================================================
    // CLASIFICAR DISPOSITIVO BLUETOOTH
    // =====================================================================

    /**
     * Determina la categoría de un dispositivo Bluetooth a partir
     * de su BluetoothClass.
     *
     * @param clase clase Bluetooth del dispositivo.
     * @return categoría correspondiente.
     */
    private BTCategory getCategory(
            BluetoothClass clase)
    {
        // Categoría predeterminada.
        BTCategory category =
                BTCategory.BTNone;


        if (clase != null)
        {
            // Extrae la parte Device Class del valor Bluetooth.
            int idclass =
                    clase.getDeviceClass() & 0x1FFF;


            switch (idclass)
            {
                // ---------------------------------------------------------
                // COCHE
                // ---------------------------------------------------------

                case BluetoothClass.Device
                             .AUDIO_VIDEO_CAR_AUDIO:

                case BluetoothClass.Device
                             .AUDIO_VIDEO_HANDSFREE:

                    category =
                            BTCategory.BTCar;
                    break;


                // ---------------------------------------------------------
                // AURICULARES
                // ---------------------------------------------------------

                case BluetoothClass.Device
                             .AUDIO_VIDEO_HEADPHONES:

                case BluetoothClass.Device
                             .AUDIO_VIDEO_WEARABLE_HEADSET:

                    category =
                            BTCategory.BTPhones;
                    break;


                // ---------------------------------------------------------
                // AUDIO
                // ---------------------------------------------------------

                case BluetoothClass.Device
                             .AUDIO_VIDEO_LOUDSPEAKER:

                case BluetoothClass.Device
                             .AUDIO_VIDEO_PORTABLE_AUDIO:

                case BluetoothClass.Device
                             .AUDIO_VIDEO_SET_TOP_BOX:

                case BluetoothClass.Device
                             .AUDIO_VIDEO_HIFI_AUDIO:

                case BluetoothClass.Device
                             .AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER:

                    category =
                            BTCategory.BTAudio;
                    break;


                // ---------------------------------------------------------
                // TELÉFONOS
                // ---------------------------------------------------------

                case BluetoothClass.Device.PHONE_CELLULAR:
                case BluetoothClass.Device.PHONE_CORDLESS:
                case BluetoothClass.Device.PHONE_ISDN:
                case BluetoothClass.Device.PHONE_MODEM_OR_GATEWAY:
                case BluetoothClass.Device.PHONE_SMART:
                case BluetoothClass.Device.PHONE_UNCATEGORIZED:

                    category =
                            BTCategory.BTMobile;
                    break;


                // ---------------------------------------------------------
                // GAFAS / CASCOS
                // ---------------------------------------------------------

                case BluetoothClass.Device.WEARABLE_GLASSES:
                case BluetoothClass.Device.WEARABLE_HELMET:

                    category =
                            BTCategory.BTGlasses;
                    break;


                // ---------------------------------------------------------
                // DISPOSITIVOS LLEVABLES
                // ---------------------------------------------------------

                case BluetoothClass.Device.WEARABLE_PAGER:
                case BluetoothClass.Device.WEARABLE_UNCATEGORIZED:
                case BluetoothClass.Device.WEARABLE_WRIST_WATCH:

                    category =
                            BTCategory.BTWearable;
                    break;


                // ---------------------------------------------------------
                // ORDENADORES
                // ---------------------------------------------------------

                case BluetoothClass.Device.WEARABLE_JACKET:
                case BluetoothClass.Device.COMPUTER_LAPTOP:
                case BluetoothClass.Device.COMPUTER_DESKTOP:

                    category =
                            BTCategory.BTComputer;
                    break;


                // ---------------------------------------------------------
                // OTROS
                // ---------------------------------------------------------

                case BluetoothClass.Device
                             .AUDIO_VIDEO_MICROPHONE:

                case BluetoothClass.Device
                             .AUDIO_VIDEO_VCR:

                case BluetoothClass.Device
                             .AUDIO_VIDEO_VIDEO_CAMERA:

                case BluetoothClass.Device
                             .AUDIO_VIDEO_CAMCORDER:

                case BluetoothClass.Device
                             .AUDIO_VIDEO_VIDEO_MONITOR:

                default:

                    category =
                            BTCategory.BTOther;
                    break;
            }
        }


        return category;
    }


    // =====================================================================
    // PRIORIDAD DE CATEGORÍA
    // =====================================================================

    /**
     * Obtiene la prioridad de ordenación de un dispositivo.
     *
     * La prioridad coincide con el ordinal de BTCategory:
     *
     * BTCar     = 0
     * BTPhones  = 1
     * BTAudio   = 2
     * ...
     */
    private int getCategoryPriority(
            BluetoothDevice d)
    {
        try
        {
            return getCategory(
                    d.getBluetoothClass())
                    .ordinal();
        }
        catch (SecurityException e)
        {
            // Si no se puede acceder a la información Bluetooth,
            // colocamos el dispositivo al final.
        }

        return 1000;
    }


    // =====================================================================
    // CARGAR DISPOSITIVOS BLUETOOTH
    // =====================================================================

    /**
     * Obtiene los dispositivos Bluetooth emparejados, los clasifica,
     * los ordena y los muestra en el Spinner.
     */
    private void loadBluetoothDevices()
    {
        // Android 12+ necesita BLUETOOTH_CONNECT para acceder
        // a los dispositivos emparejados.
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.S &&
                !hasPermission(
                        Manifest.permission.BLUETOOTH_CONNECT))
        {
            return;
        }


        // Obtiene el BluetoothManager del sistema.
        BluetoothManager bluetoothManager =
                (BluetoothManager)
                        getSystemService(
                                Context.BLUETOOTH_SERVICE);


        // Obtiene el adaptador Bluetooth.
        BluetoothAdapter adapter =
                bluetoothManager.getAdapter();


        // -----------------------------------------------------------------
        // NO HAY BLUETOOTH
        // -----------------------------------------------------------------

        if (adapter == null)
        {
            statusText.setText(
                    R.string
                            .este_m_vil_no_tiene_bluetooth);

            return;
        }


        // -----------------------------------------------------------------
        // BLUETOOTH APAGADO
        // -----------------------------------------------------------------

        if (!adapter.isEnabled())
        {
            statusText.setText(
                    R.string
                            .bluetooth_del_m_vil_est_apagado);

            return;
        }


        // Borra la lista anterior.
        devices.clear();


        // Lista de textos que se mostrarán en el Spinner.
        List<String> names =
                new ArrayList<>();


        // El primer elemento representa "Desactivar".
        devices.add(null);


        // Comprobación del permiso de conexión Bluetooth.
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED)
        {
            return;
        }


        // Obtiene los dispositivos Bluetooth emparejados.
        Set<BluetoothDevice> bonded =
                adapter.getBondedDevices();


        // Convierte el Set en una lista para poder ordenarla.
        List<BluetoothDevice> sortedDevices =
                new ArrayList<>(bonded);


        // -----------------------------------------------------------------
        // COMPARATOR
        // -----------------------------------------------------------------

        /**
         * Ordena los dispositivos:
         *
         * 1. Primero por categoría.
         * 2. Dentro de la misma categoría, por nombre.
         */
        Comparator<BluetoothDevice>
                categoryAndNameComparator =
                new Comparator<BluetoothDevice>()
                {
                    @Override
                    public int compare(
                            BluetoothDevice d1,
                            BluetoothDevice d2)
                    {
                        try
                        {
                            int cat1 =
                                    getCategoryPriority(d1);

                            int cat2 =
                                    getCategoryPriority(d2);


                            // Misma categoría:
                            // ordenar alfabéticamente por nombre.
                            if (cat1 == cat2)
                            {
                                String name1 =
                                        d1.getName() != null
                                                ? d1.getName()
                                                : "";

                                String name2 =
                                        d2.getName() != null
                                                ? d2.getName()
                                                : "";

                                return name1
                                        .compareToIgnoreCase(
                                                name2);
                            }


                            // Categorías diferentes:
                            // se utiliza su prioridad.
                            return Integer.compare(
                                    cat1,
                                    cat2);
                        }
                        catch (SecurityException e)
                        {
                            // Si se produce un problema de permisos,
                            // no alteramos el orden entre ambos.
                            return 0;
                        }
                    }
                };


        // Ordena los dispositivos.
        sortedDevices.sort(
                categoryAndNameComparator);


        // -----------------------------------------------------------------
        // CONSTRUIR LA LISTA DEL SPINNER
        // -----------------------------------------------------------------

        for (BluetoothDevice device :
                sortedDevices)
        {
            String name;

            BluetoothClass clase = null;


            try
            {
                // Obtiene el nombre del dispositivo.
                name = device.getName();

                // Obtiene su clasificación Bluetooth.
                clase =
                        device.getBluetoothClass();
            }
            catch (SecurityException e)
            {
                // Si no se puede acceder al dispositivo,
                // lo tratamos como dispositivo sin nombre.
                name = null;
            }


            // Solo mostramos dispositivos que tienen nombre.
            if (name != null &&
                    !name.trim().isEmpty())
            {
                // Guarda el objeto BluetoothDevice correspondiente.
                devices.add(device);


                // Añade al Spinner el icono y el nombre.
                names.add(
                        iconBT(
                                getCategory(clase))
                                + " "
                                + name);
            }
        }


        // -----------------------------------------------------------------
        // NO HAY DISPOSITIVOS EMPAREJADOS
        // -----------------------------------------------------------------

        if (names.isEmpty())
        {
            names.add(
                    "❌ " +
                            getString(
                                    R.string
                                            .no_hay_dispositivos_emparejados));
        }
        else
        {
            // "Desactivar" es la primera opción.
            names.add(
                    0,
                    getString(
                            R.string.desactivar));
        }


        // -----------------------------------------------------------------
        // ADAPTER DEL SPINNER
        // -----------------------------------------------------------------

        ArrayAdapter<String> adapterView =
                new ArrayAdapter<>(
                        this,
                        android.R.layout
                                .simple_spinner_item,
                        names);


        // Layout utilizado para la lista desplegable.
        adapterView.setDropDownViewResource(
                android.R.layout
                        .simple_spinner_dropdown_item);


        // Asigna el adapter al Spinner.
        bluetoothSpinner.setAdapter(
                adapterView);


        // -----------------------------------------------------------------
        // RESTAURAR LA SELECCIÓN ANTERIOR
        // -----------------------------------------------------------------

        String savedAddress =
                getPrefs().getString(
                        KEY_ADDRESS,
                        null);


        // Por defecto seleccionamos "Desactivar".
        bluetoothSpinner.setSelection(0);


        // Si existe una dirección almacenada,
        // buscamos el dispositivo correspondiente.
        if (savedAddress != null)
        {
            for (int i = 0;
                 i < devices.size();
                 i++)
            {
                // El elemento 0 puede ser null.
                if (devices.get(i) != null)
                {
                    if (savedAddress.equals(
                            devices.get(i).getAddress()))
                    {
                        // Restauramos la selección anterior.
                        bluetoothSpinner.setSelection(i);
                        break;
                    }
                }
            }
        }
    }


    // =====================================================================
    // GUARDAR CONFIGURACIÓN
    // =====================================================================

    /**
     * Guarda el dispositivo Bluetooth seleccionado y comprueba
     * que se cumplen los permisos necesarios para iniciar
     * el seguimiento.
     *
     * @return true si la configuración permite iniciar el seguimiento.
     */
    private boolean doSaveConfiguration()
    {
        // Si no hay dispositivos, eliminamos la configuración anterior.
        if (devices.isEmpty())
        {
            getPrefs()
                    .edit()
                    .putString(
                            KEY_ADDRESS,
                            null)
                    .putString(
                            KEY_NAME,
                            null)
                    .apply();


            Toast.makeText(
                            this,
                            R.string
                                    .no_hay_ning_n_dispositivo_bluetooth_emparejado,
                            Toast.LENGTH_LONG)
                    .show();

            return false;
        }


        // Obtiene la posición seleccionada.
        int position =
                bluetoothSpinner
                        .getSelectedItemPosition();


        BluetoothDevice device = null;


        // Comprueba que la posición es válida.
        if (position >= 0 &&
                position < devices.size())
        {
            device =
                    devices.get(position);
        }


        // null representa la opción "Desactivar".
        if (device == null)
        {
            getPrefs()
                    .edit()
                    .putString(
                            KEY_ADDRESS,
                            null)
                    .putString(
                            KEY_NAME,
                            null)
                    .apply();

            return false;
        }


        // Nombre predeterminado.
        String name =
                getString(
                        R.string.coche);


        try
        {
            if (device.getName() != null)
            {
                name =
                        device.getName();
            }
        }
        catch (SecurityException ignored)
        {
            // Si no podemos obtener el nombre,
            // mantenemos "coche".
        }


        // Guarda dirección MAC y nombre.
        getPrefs()
                .edit()
                .putString(
                        KEY_ADDRESS,
                        device.getAddress())
                .putString(
                        KEY_NAME,
                        name)
                .apply();


        // Comprueba que tenemos ubicación precisa.
        if (!hasPermission(
                Manifest.permission.ACCESS_FINE_LOCATION))
        {
            Toast.makeText(
                            this,
                            R.string
                                    .necesitas_conceder_permiso_de_ubicaci_n_precisa,
                            Toast.LENGTH_LONG)
                    .show();

            return false;
        }


        // Finalmente comprueba el permiso de ubicación en segundo plano.
        return queryBackgroundPermission();
    }


    // =====================================================================
    // COMPROBAR PERMISO DE UBICACIÓN EN SEGUNDO PLANO
    // =====================================================================

    /**
     * Comprueba si se ha concedido ACCESS_BACKGROUND_LOCATION.
     *
     * En Android 10 y posteriores, si no está concedido,
     * abre BackgroundLocationActivity para que el usuario
     * pueda acceder a los ajustes.
     *
     * @return true si el permiso ya está concedido o no es necesario.
     */
    private boolean queryBackgroundPermission()
    {
        if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q &&
                !hasPermission(
                        Manifest.permission
                                .ACCESS_BACKGROUND_LOCATION))
        {
            // Abre nuestra pantalla explicativa.
            Intent intent =
                    new Intent(
                            this,
                            BackgroundLocationActivity.class);


            // Esperamos el resultado de esa Activity.
            startActivityForResult(
                    intent,
                    REQUEST_BACKGROUND_LOCATION);


            return false;
        }


        return true;
    }


    // =====================================================================
    // GUARDAR CONFIGURACIÓN
    // =====================================================================

    /**
     * Guarda la configuración y, dependiendo del resultado,
     * inicia o detiene el servicio de seguimiento.
     */
    private void saveConfiguration()
    {
        if (doSaveConfiguration())
        {
            // Configuración válida:
            // iniciar el seguimiento.
            startTracking();
        }
        else
        {
            // Configuración no válida o "Desactivar":
            // detener el seguimiento.
            endTracking();
        }
    }


    // =====================================================================
    // DETENER SERVICIO
    // =====================================================================

    /**
     * Detiene CarLocationService.
     */
    private void endTracking()
    {
        stopService(
                new Intent(
                        this,
                        CarLocationService.class));
    }


    // =====================================================================
    // INICIAR SERVICIO
    // =====================================================================

    /**
     * Inicia CarLocationService como Foreground Service.
     */
    private void startTracking()
    {
        Intent intent =
                new Intent(
                        this,
                        CarLocationService.class);


        // Inicia el servicio utilizando la API de Foreground Service.
        ContextCompat.startForegroundService(
                this,
                intent);


        // Actualiza inmediatamente la pantalla.
        updateScreen();


        // Informa al usuario.
        Toast.makeText(
                        this,
                        R.string.seguimiento_activado,
                        Toast.LENGTH_SHORT)
                .show();
    }


    // =====================================================================
    // ACTUALIZAR INFORMACIÓN DE LA PANTALLA
    // =====================================================================

    /**
     * Actualiza:
     *
     * - el estado del Bluetooth del coche;
     * - la fecha del último aparcamiento;
     * - la precisión de la ubicación;
     * - la dirección obtenida, si existe.
     */
    @SuppressLint("SetTextI18n")
    private void updateScreen()
    {
        // Recupera la dirección MAC configurada.
        String address =
                getPrefs().getString(
                        KEY_ADDRESS,
                        null);


        // -----------------------------------------------------------------
        // NO HAY COCHE CONFIGURADO
        // -----------------------------------------------------------------

        if (address == null)
        {
            statusText.setText(
                    R.string
                            .bluetooth_del_coche_no_configurado);

            lastParkingText.setText(
                    R.string
                            .ltimo_aparcamiento_no_hay_ninguno_registrado);

            return;
        }


        // -----------------------------------------------------------------
        // ESTADO BLUETOOTH
        // -----------------------------------------------------------------

        if (isConnected())
        {
            // El coche está conectado.
            //
            // El servicio está esperando que se desconecte
            // para considerar que se ha aparcado.
            statusText.setText(
                    R.string
                            .esperando_desconexi_n_del_coche);
        }
        else
        {
            // El coche está desconectado.
            //
            // El servicio está esperando que vuelva a conectarse.
            statusText.setText(
                    R.string
                            .esperando_al_conexi_n_del_coche);
        }


        // -----------------------------------------------------------------
        // ÚLTIMA UBICACIÓN
        // -----------------------------------------------------------------

        Location loc =
                Tools.getLocation(
                        getBaseContext());


        if (loc == null)
        {
            // No existe ninguna posición guardada.
            lastParkingText.setText(
                    R.string
                            .ltimo_aparcamiento_no_hay_ninguno_registrado);
        }
        else
        {
            // Formatea la fecha/hora del aparcamiento.
            String date =
                    Tools.getDate(
                            this,
                            loc.getTime());


            // Recupera la dirección textual, si fue obtenida.
            String pos_address =
                    getPrefs().getString(
                            "pos_address",
                            null);


            // Construye el texto que se mostrará.
            lastParkingText.setText(
                    getString(
                            R.string.ltimo_aparcamiento)
                            +
                            date
                            +
                            getString(
                                    R.string.precisi_n)
                            +
                            String.format(
                                    Locale.getDefault(),
                                    "%.0f m",
                                    loc.getAccuracy())
                            +
                            (pos_address != null
                                    ? "\n" + pos_address
                                    : ""));
        }
    }


    // =====================================================================
    // COMPROBAR CONEXIÓN BLUETOOTH
    // =====================================================================

    /**
     * Recupera de SharedPreferences el estado de conexión
     * que mantiene CarLocationService.
     */
    private boolean isConnected()
    {
        return getPrefs().getBoolean(
                "bt_connected",
                false);
    }


    // =====================================================================
    // ABRIR ÚLTIMO APARCAMIENTO
    // =====================================================================

    /**
     * Abre la última ubicación guardada en una aplicación de mapas.
     *
     * Primero intenta utilizar una URI "geo:".
     *
     * Si no existe ninguna aplicación que pueda manejarla,
     * utiliza como alternativa una URL web de Google Maps.
     */
    private void openLastParking()
    {
        // Recupera la última ubicación.
        Location loc =
                Tools.getLocation(
                        getBaseContext());


        // Si todavía no existe una ubicación,
        // mostramos un mensaje.
        if (loc == null)
        {
            Toast.makeText(
                            this,
                            R.string
                                    .todav_a_no_hay_ning_n_aparcamiento_registrado,
                            Toast.LENGTH_LONG)
                    .show();

            return;
        }


        try
        {
            // Construye una URI geo: con las coordenadas.
            Uri uri =
                    Uri.parse(
                            "geo:"
                                    + loc.getLatitude()
                                    + ","
                                    + loc.getLongitude()
                                    + "?q="
                                    + loc.getLatitude()
                                    + ","
                                    + loc.getLongitude()
                                    + "("
                                    + getString(
                                    R.string.coche)
                                    + ")");


            // Intent para abrir la ubicación.
            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            uri);


            // Android buscará una aplicación compatible,
            // normalmente Google Maps.
            startActivity(intent);
        }
        catch (Exception e)
        {
            // -----------------------------------------------------------------
            // FALLBACK: GOOGLE MAPS MEDIANTE URL
            // -----------------------------------------------------------------

            Uri uri =
                    Uri.parse(
                            "https://www.google.com/maps/search/?api=1&query="
                                    +
                                    loc.getLatitude()
                                    +
                                    ","
                                    +
                                    loc.getLongitude());


            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            uri);


            try
            {
                startActivity(intent);
            }
            catch (Exception e2)
            {
                // No existe ninguna aplicación capaz de abrir
                // la ubicación.
                Toast.makeText(
                                this,
                                R.string
                                        .no_hay_una_aplicaci_n_de_mapas_disponible,
                                Toast.LENGTH_LONG)
                        .show();
            }
        }
    }


    // =====================================================================
    // ACCESO A SHARED PREFERENCES
    // =====================================================================

    /**
     * Devuelve el objeto SharedPreferences utilizado por la aplicación.
     *
     * En estas preferencias se guardan, entre otros datos:
     *
     * - Bluetooth seleccionado.
     * - Nombre del Bluetooth.
     * - Sonido de la notificación.
     * - Notificación persistente.
     * - Estado de conexión Bluetooth.
     * - Dirección del último aparcamiento.
     */
    private SharedPreferences getPrefs()
    {
        return getSharedPreferences(
                PREFS,
                MODE_PRIVATE);
    }


    // =====================================================================
    // RESULTADO DE LA PETICIÓN DE PERMISOS
    // =====================================================================

    /**
     * Recibe el resultado de ActivityCompat.requestPermissions().
     */
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults);


        // Comprobamos que se trata de nuestra petición.
        if (requestCode ==
                REQUEST_PERMISSIONS)
        {
            // Una vez procesados los permisos,
            // volvemos a cargar los dispositivos Bluetooth.
            loadBluetoothDevices();

            // Y actualizamos la interfaz.
            updateScreen();
        }
    }
}