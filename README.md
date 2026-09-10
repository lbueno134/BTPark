# AparcamientoBluetooth

Proyecto Android Studio en Java.

## Función

La aplicación permite seleccionar un dispositivo Bluetooth emparejado como coche.
Una vez configurado:

- Mantiene un Foreground Service.
- Escucha la desconexión Bluetooth.
- Solo reacciona al dispositivo seleccionado.
- Obtiene la posición GPS.
- Guarda cada aparcamiento en SQLite.
- Permite abrir la última posición mediante la aplicación de mapas.
- Intenta arrancar el servicio después de reiniciar el teléfono.

## Primer arranque

1. Empareja previamente el teléfono con el coche.
2. Abre la aplicación.
3. Concede Bluetooth, ubicación y notificaciones.
4. Selecciona el Bluetooth del coche.
5. Pulsa "Guardar configuración".
6. Concede "Permitir todo el tiempo" para ubicación.
7. El servicio queda funcionando.

## Importante

El proyecto usa `BluetoothDevice.ACTION_ACL_DISCONNECTED`. Dependiendo del teléfono,
versión de Android y perfil Bluetooth utilizado por el vehículo, puede ser necesario
adaptar la detección a la conexión concreta que mantiene el Kia.

Android y algunos fabricantes pueden aplicar optimizaciones de batería que afecten
a servicios persistentes. Para un funcionamiento 24/7 puede ser necesario excluir
la aplicación de la optimización de batería del fabricante.

## Google Maps

No se utiliza una API de Google Maps. El botón utiliza un Intent `geo:` y abre
la aplicación de mapas instalada en el teléfono.

Si no hay aplicación de mapas intentará abrir un navegador con la página de google maps.

## Manual de instrucciones

¿Alguna vez has aparcado el coche y después no recuerdas exactamente dónde lo dejaste?

**Leo's BTPark** nace con una idea muy sencilla: ayudarte a recordar dónde has aparcado, de forma automática y sin complicaciones.

Existen muchas aplicaciones para localizar el coche aparcado, pero algunas ofrecen tantas funciones que terminan siendo complicadas de utilizar, mientras que otras incluyen publicidad, compras o suscripciones.

Por eso hemos creado Leo's BTPark: **una aplicación sencilla, automática y centrada únicamente en lo que realmente necesitas.**

### 🚗 Sólo tienes que seleccionar el Bluetooth de tu coche

El funcionamiento es muy sencillo.

La primera vez sólo tienes que seleccionar el dispositivo Bluetooth de tu coche. A partir de ese momento, **Leo's BTPark se encarga de todo automáticamente**.

Mientras el teléfono está conectado al Bluetooth seleccionado, la aplicación entiende que estás utilizando el coche.

Cuando detecta que la conexión Bluetooth se ha perdido, por ejemplo al apagar el coche y alejarte de él, obtiene automáticamente tu ubicación y la guarda como **último lugar de aparcamiento**.

No tienes que abrir la aplicación, pulsar ningún botón ni indicar dónde has aparcado.

### 🔔 Una notificación que siempre está disponible

Leo's BTPark funciona como un **servicio en primer plano**, por lo que Android mantiene una notificación permanente mientras el servicio está activo.

Esta notificación no sólo indica que Leo's BTPark está funcionando, sino que proporciona información útil de un vistazo:

• Indica si actualmente estás **conectado o desconectado del Bluetooth del coche**.
• Muestra información sobre la **última posición de aparcamiento guardada**.
• Es una notificación interactiva: **al pulsarla puedes abrir directamente la última posición en Google Maps**.

De esta forma, aunque no abras la aplicación, tienes siempre a mano la información de dónde dejaste el coche.

### 🔄 Funciona automáticamente en segundo plano

No necesitas mantener la aplicación abierta.

Leo's BTPark se ejecuta automáticamente en segundo plano y continúa funcionando para detectar la conexión y desconexión del Bluetooth del coche.

Además, **el funcionamiento se restablece automáticamente después de reiniciar el teléfono**, por lo que no tienes que acordarte de volver a iniciar la aplicación cada vez que reinicias Android.

Sólo tienes que configurar una vez el Bluetooth de tu coche y dejar que Leo's BTPark haga su trabajo.

### ❤️ Siempre gratuita y sin publicidad

**Leo's BTPark es y será siempre gratuita.**

No hay suscripciones, compras dentro de la aplicación ni funciones bloqueadas mediante pago.

**Y tampoco hay publicidad.**

La aplicación se ha desarrollado teniendo en cuenta que existen muchas aplicaciones de aparcamiento, pero algunas son demasiado complejas para una tarea tan sencilla y otras están llenas de publicidad o utilizan modelos de pago.

Leo's BTPark apuesta por todo lo contrario:

**sencillez, automatización y privacidad, sin publicidad y sin costes.**

Nuestra intención es mantener esta filosofía siempre.

### 📍 Características

✓ Configuración sencilla: sólo tienes que seleccionar el Bluetooth de tu coche
✓ Guarda automáticamente la ubicación al desconectarse del Bluetooth
✓ No necesitas abrir la aplicación al aparcar
✓ Funciona automáticamente en segundo plano
✓ Se inicia de nuevo automáticamente después de reiniciar el teléfono
✓ Servicio en primer plano para mantener el funcionamiento activo
✓ Notificación permanente con el estado de la conexión Bluetooth
✓ La notificación muestra la última información de aparcamiento
✓ Acceso directo a la última posición mediante Google Maps
✓ Guarda únicamente la última ubicación de aparcamiento
✓ No necesitas crear una cuenta
✓ Interfaz sencilla y minimalista
✓ **Siempre gratuita**
✓ **Sin publicidad**

Leo's BTPark está diseñada para hacer una sola cosa y hacerla bien:

**tú aparcas. Bluetooth se desconecta. Leo's BTPark guarda dónde has dejado el coche.**

Y cuando quieras volver, **sólo tienes que pulsar la notificación para encontrarlo.**