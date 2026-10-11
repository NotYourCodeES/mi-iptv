# Mi IPTV

**Un reproductor IPTV ligero para Android TV que se configura desde el ordenador o el móvil, sin escribir con el mando.**

🇬🇧 [Read in English](README.md)

Escribir enlaces largos y contraseñas con el mando de la tele es un suplicio. Mi IPTV lleva un pequeño servidor web dentro de la app: abres la dirección que aparece en la tele (o escaneas el código QR), pegas tu enlace M3U o tus datos Xtream Codes en el navegador, y los canales aparecen en la tele en unos segundos.

<p align="center">
  <img src="docs/screenshots/channels.png" width="820" alt="Lista de canales con categorías y vista previa">
</p>
<p align="center">
  <img src="docs/screenshots/qr-setup.png" width="400" alt="Pantalla de bienvenida con la dirección y el código QR">
  &nbsp;
  <img src="docs/screenshots/setup-page.png" width="400" alt="Página de configuración desde el navegador">
</p>

## Funciones

- **Configuración desde cualquier dispositivo de tu red.** Página web integrada (y código QR en la tele) para escribir un enlace M3U o servidor, usuario y contraseña Xtream Codes.
- **Vista previa en directo.** Al moverte por la lista, el canal seleccionado se reproduce en una ventana pequeña; pulsa OK para verlo a pantalla completa.
- **Favoritos.** Mantén pulsado OK sobre un canal para añadirlo. El botón **Guía** del mando abre directamente tus favoritos.
- **App de iPhone como mando (opcional).** [Mi IPTV Remote](ios/) encuentra la tele en tu Wi-Fi y convierte el móvil en un mando táctil, un teclado para buscar canales, una lista de canales con logos y un destino de *Compartir* para mandar enlaces desde Safari a la tele. La tele funciona exactamente igual sin ella.
- **Pensada para el mando.** Arriba/Abajo y CH+/CH− cambian de canal, Atrás no cierra la app por accidente (pulsándolo dos veces seguidas vas al inicio de la tele) y un banner muestra el logo, número y categoría del canal.
- **Logos de los canales** descargados y guardados en memoria con un cargador de imágenes propio.
- **Audio Dolby.** Incluye un descodificador FFmpeg para que los canales con AC-3 / E-AC-3 tengan sonido aunque la tele no lo ofrezca a las apps.
- **Reproducción robusta.** HLS y MPEG-TS, reintento automático si el canal falla y copia de la última lista por si no hay conexión.
- **Bilingüe.** Inglés y español, según el idioma de la tele y del navegador.

## Cómo compilarla e instalarla

1. Abre el proyecto en Android Studio y elige **Build → Assemble Project** (o ejecuta `./gradlew assembleDebug`). También puedes descargar el APK desde la sección **Releases** de GitHub.
2. Instálalo en la tele con la app **Downloader** (sirviendo el APK desde el ordenador con `jwebserver -b 0.0.0.0 -p 8000`), con `adb install` o con un pendrive.
3. Abre **Mi IPTV** en la tele, entra desde el navegador en la dirección que muestra (por ejemplo `http://192.168.1.50:8080`) y escribe los datos de tu proveedor.

### Pruébala sin suscripción IPTV

Usa la [lista de demostración](docs/demo): canales ficticios con logos que emiten películas abiertas y emisiones de prueba públicas. Pega este enlace como **Enlace M3U**:

```
https://raw.githubusercontent.com/NotYourCodeES/mi-iptv/main/docs/demo/playlist.m3u
```

## Mando a distancia

| Botón | Pantalla completa | Lista de canales |
|---|---|---|
| **OK** | Abrir la lista | Ver el canal seleccionado |
| **Mantener OK** | Añadir / quitar favorito | Añadir / quitar favorito |
| **Arriba / Abajo**, **CH+ / CH−** | Canal anterior / siguiente | Moverse por la lista (con vista previa) |
| **Guía** (o azul) | Abrir favoritos | Abrir favoritos |
| **Atrás** | Abrir la lista | Volver al canal que estabas viendo |
| **Atrás dos veces seguidas** | Ir al inicio de la tele | Ir al inicio de la tele |
| **0–9** | Escribir un número de canal | Escribir un número de canal |
| **Último canal** | Canal anterior | Canal anterior |
| **Play / Pausa** | Pausar; pulsa otra vez para volver al directo | — |

## Mando desde el iPhone

La [app de iPhone](ios/) es opcional y funciona en la misma red que la tele:

- **Mando**: superficie táctil como la del Apple TV (desliza para moverte, toca para OK, mantén para favorito) o flechas clásicas, CH y VOL que se repiten al mantenerlos, play/pausa, silencio, último canal y teclado numérico.
- **Teclado**: escribes en el móvil y la tele va mostrando los canales que coinciden.
- **Canales**: la lista completa con logos, categorías y favoritos. Toca un canal para verlo en la tele.
- **Compartir**: en Safari o cualquier app, *Compartir → Mi IPTV* reproduce una emisión en la tele o carga una lista.
- **Emparejamiento**: la primera vez la tele muestra un código de 4 cifras. Los móviles emparejados se pueden olvidar desde la página de configuración.

Consulta [`ios/README.md`](ios/README.md) para compilarla e instalarla con Xcode.

## Aviso

Mi IPTV es solo un reproductor. No incluye ni enlaza ningún canal ni contenido. Úsalo únicamente con listas que tengas derecho a ver: una suscripción de un proveedor con licencia, emisiones abiertas o tu propio servidor multimedia.

## Licencia

[MIT](LICENSE)
