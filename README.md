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
