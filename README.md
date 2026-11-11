# JarNix · Java ME Porting Toolkit

**JarNix** (herramienta de PC) convierte juegos Java ME (Symbian S60, Series 40 y cualquier `.jar`/`.jad` de MIDP) en
**proyectos nativos de Android Studio**: código Java editable, recursos originales y una
implementación de J2ME sobre Android con mejoras (escalado, teclado virtual, mandos, límite de FPS...).

No es un emulador: el juego se compila como una app Android normal con su propio icono y paquete.

```
MiJuego.jar ──► j2a analyze ──► j2a port ──► proyecto Android Studio ──► Run ▶ / APK
                 (qué APIs usa)   (parche + descompilación + verificación)
```

## Proyectos y repositorios

- **JarNix (Windows/PC):** este repositorio; interfaz Python, CLI, generador y motor J2Legacy.
- **[J2Droid (Android)](https://github.com/ZenithUzu/J2Droid):** repositorio independiente, con su propio código Android, icono adaptable y distribución de **una sola APK instaladora**. Los juegos convertidos generan APK independientes.
- **Código compartido:** J2Droid sincroniza `android-offline/`, `runtime/`, `runtime-extra/` y `runtime-m3g/` desde JarNix mediante GitHub Actions. Los cambios al motor deben realizarse aquí para evitar divergencias. La compilación y publicación deben verificarse en [Actions de J2Droid](https://github.com/ZenithUzu/J2Droid/actions).

## Estado de las interfaces

JarNix muestra la identidad renovada y dispone de **Proyectos e historial**, una consulta de los JAR cargados en la sesión (nombre, ID, destino y estado). Las funciones de historial y proyectos están en evolución y requieren validación de persistencia y pruebas. J2Droid tiene una sola pantalla: **Importar JAR → Crear APK → Instalar / Guardar**, más **Exportar llaves** e **Importar llaves**. Sus colores siguen Material You (Android 12+) y su icono es adaptativo.

## Nombres y compatibilidad

- **JarNix**: aplicación de escritorio para preparar proyectos Android Studio, compilar APK e instalar mediante ADB.
- **J2Droid**: conversor autónomo de JAR/JAD a APK directamente en Android.
- **J2Legacy**: nombre del motor compartido de compatibilidad Java ME; internamente se conservan los identificadores técnicos `j2a`.

Los nombres nuevos son visibles; **no se renombran** comandos (`tool/j2a.py`), paquetes Java, rutas de caché, IDs de aplicaciones, firmas ni archivos de proyecto existentes. Esto evita romper scripts y actualizaciones. El instalador de J2Droid en su repositorio independiente se publica con el nombre `J2Droid.apk`; los artefactos antiguos de este repositorio pueden conservar nombres históricos.

## Requisitos

**J2Droid (versión Android sin PC):** consulta [android-offline/README.md](android-offline/README.md).
Su única salida es una APK firmada; incluye compilador y motor para convertir localmente.
Las instrucciones siguientes corresponden a la herramienta de escritorio.

- **Python 3.8+** (sin dependencias; Pillow es opcional para agrandar el icono).
- **Android Studio** (ya lo tienes). j2a usa su JDK (`jbr`) y su Android SDK automáticamente.
  Si no los encuentra, define `JAVA_HOME` y `ANDROID_HOME`.
- Internet la primera vez, para descargar Vineflower/CFR (se guardan en `~/.j2a/tools`).

```bash
git clone https://github.com/ZenithUzu/symbian-a-Android-port
cd symbian-a-Android-port
python tool/j2a.py doctor      # comprueba Java, SDK y descompiladores
```


## Llaves de firma (PC y Android, sin contraseñas)

JarNix y J2Droid firman los juegos con las **mismas llaves**, guardadas en `%USERPROFILE%\.j2a\signing\identity.p12` (PC) y dentro de J2Droid (Android).

- **Exportar llaves** guarda un archivo `.p12`; **Importar llaves** lo usa. No se pide ninguna contraseña.
- El archivo de una herramienta sirve en la otra: exporta en J2Droid e importa en JarNix, o al revés.
- Expórtalas antes de borrar o reinstalar J2Droid; al importarlas de nuevo, tus juegos se actualizan sin perder partidas.
- La primera vez, JarNix crea las llaves a partir de tu clave de Android Studio (`~/.android/debug.keystore`), así los juegos que ya instalaste desde la PC se siguen actualizando.
- Los respaldos antiguos con contraseña aún se pueden importar una vez; después vuelve a exportar.

Guarda el archivo `.p12` en un lugar privado: quien lo tenga puede firmar actualizaciones de tus juegos.

**Paquete oficial y actualización cruzada:** PC y Android calculan el mismo `applicationId` a partir de la desarrolladora (`MIDlet-Vendor`) y el nombre del juego (`MIDlet-Name`): `Gameloft S.A.` + `Asphalt 4` → `com.gameloft.asphalt4`. Si el fabricante falta o es un texto de relleno del SDK, el nombre no tiene letras latinas o las clases del juego chocarían con las del port, se usa `com.j2a.game.g<hash>` (fabricante + nombre + clase MIDlet). **Los juegos instalados con el ID antiguo `com.j2a.game.g…` no se actualizan con el nuevo: se instalan como una app aparte** (respalda las partidas con la carpeta Java antes de desinstalar el antiguo). Ambos usan códigos de versión basados en segundos desde 2020. La actualización requiere además **la misma clave privada** y un `versionCode` estrictamente superior. Si se compilan dos APK durante el mismo segundo o el reloj de algún dispositivo es incorrecto, la versión puede no aumentar. Los proyectos PC ya generados conservan su paquete antiguo hasta regenerarlos. La compatibilidad cruzada sigue pendiente de prueba real; no desinstales juegos con partidas para solucionar un error de firma.


### Respaldo de partidas Java mediante SAF (motor compartido)

Tanto los APK generados desde **PC** como los generados desde **Android** utilizan el motor RMS común. En el menú del juego, abre **Más opciones → Seleccionar carpeta Java (SAF)** y autoriza la carpeta `Java` que hayas creado en el almacenamiento compartido. Cada APK requiere su propio permiso SAF. Los registros se guardan primero en el almacenamiento privado y después se respaldan automáticamente en `Java/saves/<applicationId>/` mediante una cola de escritura. Se conserva una copia anterior al publicar un respaldo nuevo. El menú también ofrece respaldo manual y restauración de registros RMS faltantes. **La restauración automática al iniciar y la migración de guardados de APIs distintas de RMS aún no están implementadas.** Si no autorizas la carpeta, el juego sigue guardando localmente, pero no tendrás una copia externa. Esta función todavía necesita pruebas en dispositivos reales y con distintos proveedores SAF.

## Uso con interfaz (lo más fácil)

### APK lista con JarNix

La interfaz ofrece tres destinos en **Entrega**: proyecto de Android Studio, APK instalable,
o APK con instalación por ADB. Para ADB, instala Platform-Tools, activa la depuración USB
y acepta la autorización en el dispositivo. Pulsa **Buscar ADB** y elige el serial de tu
teléfono o emulador autorizado. La instalación usa `adb -s SERIAL install -r`: conserva datos
cuando la firma y Android permiten actualizar. Si la firma es incompatible, muestra el error;
no desinstala el juego ni borra partidas automáticamente.

```powershell
python tool/j2a.py port MiJuego.jar --install --device SERIAL
python tool/j2a.py build proyectos/MiJuego-android --install --device SERIAL
```

Dentro de cada juego, el **Centro de juego** reemplaza la lista Android básica por un panel
de consola oscuro con detalles dorados, tarjetas, valores seleccionados y secciones. Los paneles
de controles y filtros usan el mismo estilo y se pueden recorrer con teclado o mando.
Los efectos integrados CRT, scanlines, LCD, LCD verde, Color vivo y Ámbar tienen intensidad
ajustable y persistente de 0 a 100%. Los shaders externos y los de RetroArch conservan sus
propios parámetros (la intensidad integrada no modifica CRT Lottes ni Matriz de puntos).
Esto cambia los menús del port; conserva la interfaz original dibujada por cada juego J2ME.

La interfaz compila una **APK instalable** por defecto. Añade tus JAR, elige el tipo de entrega
y pulsa **Generar APK**. Los proyectos van en `proyectos/` y las APKs en `apk/`, dentro de la carpeta de la herramienta; **Abrir carpeta** abre esa carpeta
cuando ya existe. La primera compilación descarga Gradle y sus dependencias: necesita internet,
el JDK de Android Studio y los paquetes/licencias del SDK que pida Gradle (SDK Manager).
No hace falta abrir cada proyecto en Android Studio para compilar.

La APK se firma con tus **llaves J2A** (ver *Llaves de firma*), las mismas que usa J2Droid.
Cada APK usa el icono original que trae el JAR (igual en PC y Android); si el JAR no trae icono se usa uno genérico.
No desinstales un juego con partidas guardadas para actualizar: conserva tus llaves.

Los perfiles Original, Equilibrado, Pixel art, Televisión y Teléfono clásico ajustan escalado y efecto.
También puedes elegirlos por separado. Los efectos LCD conservan ahora los detalles del filtro anterior.
Dentro del juego, el menú de filtros incluye perfiles rápidos y una opción para restablecer la imagen.
El rendimiento y la compatibilidad de shaders dependen de la GPU; si un shader no compila, el motor
lo omite y conserva el filtro básico como respaldo.

Por comandos:

```powershell
python tool/j2a.py port MiJuego.jar --apk
python tool/j2a.py build proyectos/MiJuego-android
python tool/j2a.py build proyectos/MiJuego-android -o MisAPKs
```

Si una compilación falla, el proyecto permanece disponible y `apk-build.log` contiene el registro.
Puedes corregir el SDK o el código y repetir `build` sin volver a generar ni perder tus cambios.
En la interfaz, al encontrar un proyecto existente, **No** conserva sus ajustes y compila su APK;
**Sí** lo reemplaza. Elige **Proyecto de Android Studio** si solo quieres generar el proyecto.

Para abrir **JarNix**, haz doble clic en **`j2a-gui.pyw`** (en Linux/macOS: `python3 j2a-gui.pyw`). En Windows, si no abre o sale un error de "Python Launcher", usa **`j2a-gui.bat`**: muestra el error en vez de cerrarse.

1. **Arrastra** uno o varios `.jar` a la lista (o **Añadir juegos…**, o suéltalos sobre `j2a-gui.bat`).
   Cada juego muestra su nombre, su paquete y dónde se creará su proyecto, todo automático.
2. Si quieres cambiar algo de un juego (nombre, paquete o carpeta), haz **doble clic** en él.
3. **Guardar**: `proyectos/Juego-android/` y `apk/`. **Cambiar carpeta base** permite elegir otro destino
   que conserve estas dos subcarpetas. Las rutas no dependen de dónde esté el JAR ni del directorio de la terminal.
4. **Generar APK / Crear proyecto Android / Crear e instalar APK**, según Entrega. Se crean uno tras otro; si uno falla, los demás siguen. Si un proyecto ya existe,
   la ventana pregunta si reemplazarlo.
5. **Abrir carpeta** y ábrela en Android Studio (File → Open) → Run ▶. Ese proyecto es el código ya
   descompilado: lo puedes corregir o mejorar ahí mismo.

**Exportación y dispositivos** contiene solo el modo de código, archivos de filtros externos que se
incluyen al compilar, destino y ADB. Resolución, filtros, pantalla, FPS y controles se ajustan dentro
del juego. Los parámetros avanzados siguen disponibles en la CLI para automatización.

Arrastrar dentro de la ventana usa el paquete `tkinterdnd2`: `j2a-gui.bat` lo instala solo la primera vez
(o `python -m pip install tkinterdnd2`). Sin él, todo lo demás funciona igual.

La ventana usa los mismos comandos de abajo, así que el resultado es idéntico.

## Uso rápido por comandos

```bash
# 1. ¿Qué hay dentro y qué tan portable es?
python tool/j2a.py analyze MiJuego.jar

# 2. Generar el proyecto (código fuente editable)
python tool/j2a.py port MiJuego.jar -o MiJuego-android

# 3. Android Studio → File → Open → MiJuego-android → Run ▶
```

Ejemplos con opciones:

```bash
# Juego de Nokia N95 / E-series en horizontal, escalado pixel-perfect y a 30 FPS
python tool/j2a.py port Juego_320x240.jar --screen 320x240 --orientation landscape --scale integer --fps 30

# Máxima fidelidad: bytecode original, sin descompilar
python tool/j2a.py port Juego.jar --mode jar

# Solo quiero modificar dos clases; el resto queda como bytecode original
python tool/j2a.py port Juego.jar --mode hybrid --edit com.estudio.GameCanvas,com.estudio.Menu

# Solo descompilar para leer el código
python tool/j2a.py decompile Juego.jar -o Juego-src
```

### Modos de `port`

| Modo | Qué genera | Cuándo usarlo |
|---|---|---|
| `source` (defecto) | Todo el juego descompilado en `app/src/main/java`. Las clases que no se descompilan limpias o no compilan se quedan automáticamente en `app/libs/game.jar`. | Quieres modificar/mejorar el juego. |
| `jar` | El bytecode original (parcheado) en `app/libs/game.jar`. | Solo quieres jugarlo en Android con la máxima fidelidad. |
| `hybrid` | Solo las clases de `--edit` como código; el resto como bytecode. | Juegos ofuscados grandes donde solo tocas unas pocas clases. |

### Opciones principales

| Opción | Descripción |
|---|---|
| `--screen 240x320` | Resolución virtual. Normalmente no hace falta: j2a la deduce del manifiesto, del nombre del archivo (`Juego_176x208.jar`, `Juego_N95.jar`), de las imágenes y de los números fijos en el código. Además, el APK ajusta solo el área visible y la rotación si el juego dibuja menos de lo esperado. |
| `--orientation portrait/landscape` | Fija la orientación. Por defecto la app gira con el teléfono. |
| `--scale fit/integer/stretch` | Escalado. |
| `--shader xbr+crt-lottes` | Filtros: uno de escalado (`nearest`, `sharp`, `smooth`, `xbr`, `mmpx`, `aann`) y efectos encima con `+` (`crt`, `crt-lottes`, `scanlines`, `lcd`, `dot`, `mono`). |
| `--shaders carpeta/` | Incluye filtros de RetroArch (`.glsl`, [libretro/glsl-shaders](https://github.com/libretro/glsl-shaders)) o PPSSPP (`.ini` + `.fsh`). Revisa su licencia: muchos son GPL. |
| `--fps-mode vsync/dynamic/unlimited` | Sincronizado con la pantalla, dinámico (la pantalla adapta sus Hz a los FPS del juego) o sin límite. |
| `--keep-j2me-packages` | No renombrar las APIs al paquete del motor (ver "Port nativo"). |
| `--keypad gamepad/full/minimal/none` | Controles en pantalla: mando (por defecto), teclado del teléfono, cruceta + OK u ocultos. |
| `--fps 30` | Limita los FPS (muchos juegos van demasiado rápido en móviles actuales). |
| `--package com.yo.juego`, `--name "Mi Juego"` | ID y nombre de la app. |
| `--midlet 2` / `--midlet-class X` | Qué MIDlet lanzar si el JAR trae varios o no los declara. |
| `--engine cfr` | Usar CFR en vez de Vineflower. |
| `--keep-bytecode A,B` | Forzar que ciertas clases queden como bytecode. |
| `--jad Juego.jad` | JAD explícito (si está junto al `.jar` con el mismo nombre se usa solo). |

Todas las opciones de ejecución se pueden cambiar después en
`app/src/main/assets/j2a/port.properties`.

### Opciones dentro del APK

El jugador abre el **menú del port** con el **gesto o botón Atrás** de Android (o Menú/Escape) y puede cambiar:
resolución (128x160 … 640x360 y **widescreen** con la proporción del móvil), rotación
(la del teléfono por defecto, o fija vertical/horizontal), centrar la imagen, escalado (proporcional, entero,
16:9, estirar), **filtros** (uno de escalado: nítido, sharp, suavizado, **xBR**, **MMPX**, **AANN**; y los efectos
que quieras encima a la vez: **CRT**, **CRT Lottes**, scanlines, rejilla LCD, matriz de puntos, LCD verde, más
los `.glsl` de RetroArch o PPSSPP que incluyas), **modo de FPS** (vsync, dinámico, sin límite), límite de FPS,
contador de FPS, anti-parpadeo, sonido, **captura de pantalla** y restablecer opciones.

En **Controles…**: mando en pantalla con aspecto de consola portátil (cruceta **y** joystick
independientes, A B X Y, L/R, cada botón cambia de imagen al pulsarlo, **Select/Start** = las teclas izquierda/derecha del teléfono, y **123** para el
teclado numérico), teclado de teléfono, **asignar la tecla del juego** a cada control (también los del mando
físico: L2/R2, L3/R3, stick derecho), controles visibles, **moverlos arrastrándolos**, tamaño, opacidad y
vibración. Un mando físico se mapea solo con las mismas teclas.
Todo se guarda entre sesiones.

## Resoluciones y widescreen dentro del juego

La herramienta elige automáticamente la resolución al importar y generar cada juego, y muestra
**resolución y confianza** en la lista. Compara declaraciones de JAR/JAD, nombre de la versión,
modelo, cabeceras PNG/GIF/JPEG y tamaños reales usados en llamadas Graphics e Image.createImage.
Las declaraciones explícitas tienen prioridad; las demás pistas se combinan con puntuaciones
limitadas por fuente para evitar que muchos sprites o constantes casuales dominen la decisión.
Los iconos declarados, atlas, fuentes y hojas de sprites se descartan como pruebas de pantalla.
`analyze --json` incluye candidatos, puntuaciones y motivos; el registro del port explica la elección.

Si faltan datos o las pistas empatan, muestra **confianza baja**. Sin ninguna pista usa 240x320
como valor de compatibilidad, no como resolución confirmada. AutoFit y el menú de resolución
siguen disponibles. El análisis estático no puede garantizar el tamaño de todos los juegos
ofuscados, adaptables o con recursos cifrados; la CLI conserva `--screen` como corrección explícita.

En **Ajustes → Resolución**, primero aparecen la resolución original y las pistas encontradas
en el manifiesto, JAD, nombre, imágenes y código del JAR. Son recomendaciones detectadas, no garantía
de que el juego soporte todas ellas. Después están 40 perfiles clásicos y tamaños alternativos,
incluyendo pantallas pequeñas, formatos apaisados y Communicator, y la resolución personalizada.

**Widescreen → 16:9 / 18:9 / 19.5:9 / 20:9 / 21:9 / formato de esta ventana** amplía las coordenadas
del lienzo que recibe el juego conservando la altura original. Por ejemplo, 320x240 pasa a 427x240
en 16:9. En juegos que dibujan girados, se intercambian los ejes del buffer para ampliar el lado correcto.
Usa escalado proporcional y desactiva AutoFit para que no recorte el escenario nuevo. Requiere reinicio;
puedes restaurar el lienzo original y los ajustes anteriores desde el mismo menú.

Esto permite más escenario en juegos adaptables que consultan getWidth/getHeight. No modifica por sí
solo cámaras, fondos, buffers internos ni HUD codificados para una resolución fija: esos juegos
necesitan parches específicos. El estirado antiguo 16:9 ya no se ofrece como widescreen en el menú;
su valor se conserva para compatibilidad con configuraciones previas de la CLI.

## Cómo funciona

1. **Análisis** — lee `MANIFEST.MF`/JAD y el *constant pool* de cada `.class` para saber qué APIs
   J2ME usa el juego y comprobar si el runtime las implementa (incluye métodos).
2. **Parche de bytecode** — reescribe `Class.getResourceAsStream` (que en Android falla con el
   típico `"".getClass()`), y descarta copias de `javax.microedition.*` que algunos JAR traen dentro.
3. **Descompilación** — Vineflower (o CFR) con el runtime como biblioteca para resolver tipos.
4. **Verificación** — compila el código con `javac` contra tu `android.jar`; lo que no compila
   vuelve a bytecode. El proyecto generado compila siempre.
5. **Proyecto** — plantilla Gradle + módulo `engine` + recursos + icono del MIDlet.

### Port nativo, sin emulador

No hay intérprete ni máquina virtual de J2ME dentro del APK: el código del juego se compila a DEX
y corre directo en ART, como cualquier app. Las llamadas que antes iban al teléfono
(`Canvas`, `Graphics`, `RecordStore`, `Player`...) van al módulo `engine`, que está escrito con APIs
de Android (OpenGL ES, MediaPlayer, AudioTrack, archivos, táctil). Además j2a **renombra** esas APIs
al paquete propio del port (`com.tu.juego.engine.lcdui.Canvas`, `...engine.rms.RecordStore`), tanto
en el código descompilado como en el bytecode, así que el APK final no contiene ninguna clase
`javax.microedition` ni `com.nokia`.

Más detalle:

- [docs/01-decompilacion.md](docs/01-decompilacion.md) — estructura de JAR/JAD, ofuscación, Vineflower/CFR.
- [docs/02-traduccion-a-android.md](docs/02-traduccion-a-android.md) — la capa de traducción J2ME → Android y la tabla de APIs.
- [docs/03-mejoras-y-limitaciones.md](docs/03-mejoras-y-limitaciones.md) — mejoras, límites y cómo añadir APIs.

## APIs soportadas

MIDP 2.0 completo para juegos (Canvas, GameCanvas, Graphics, Image, Font, Sprite, TiledLayer,
LayerManager, Form/List/TextBox/Alert y sus Items), RMS, MMAPI (MIDI/WAV/MP3/AMR/OGG + tonos y
ToneControl), HTTP, Nokia UI (FullCanvas, DirectGraphics, DirectUtils, DeviceControl, Sound) y
Samsung (AudioClip, Vibration, LCDLight). **3D**: M3G (JSR-184) con el motor nativo de Nokia en C y
MascotCapsule V3 (y las APIs 3D de JBlend/Vodafone/Motorola) con el renderizador de J2ME Loader; solo
se compilan en los juegos 3D (NDK, que Android Studio instala). **Además** (de J2ME Loader, en
`runtime-extra/`): APIs de Siemens, Motorola, Sony Ericsson, Samsung, Vodafone/J-Phone/KDDI,
**sensores JSR-256** (acelerómetro), FileConnection/PIM, ubicación, SMS, Bluetooth, AMMS y todos los
controles MMAPI, para que ningún juego se cierre por una clase que falta.

## Estructura del repositorio

```
tool/j2a.py              CLI (analyze, decompile, port, patch, verify, doctor)
tool/j2a_tool/           parser de .class, parche de bytecode, descompilación, generador
runtime/                 motor del port (se copia como módulo "engine" y se renombra al paquete del juego)
runtime-m3g/             3D M3G: motor de Nokia en C + bindings Java (EPL 1.0)
runtime-extra/           APIs de fabricante, sensores, JSR extra y MascotCapsule (de J2ME Loader, Apache 2.0)
template/                plantilla del proyecto Android Studio (Gradle 9.3.1, AGP 9.1.1, compileSdk/targetSdk 37)
samples/demo-midlet/     juego J2ME de prueba (GameCanvas, Sprite, RMS, Forms, sonido)
docs/                    documentación
```

## Probar con el juego de ejemplo

```bash
python samples/demo-midlet/build.py -o demo.jar     # genera demo.jar + demo.jad
python tool/j2a.py port demo.jar -o demo-android
python tool/j2a.py verify demo-android              # compila con javac sin abrir Gradle
```

## Licencia, créditos y uso de IA

El código original del proyecto se publica bajo la [licencia MIT](LICENSE), **sin relicenciar componentes ajenos**. Las APIs procedentes de J2ME Loader, M3G de Nokia, shaders, D8/R8, apksig y otras dependencias mantienen sus propios términos y avisos: consulta [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), [runtime-extra/NOTICE-EXTRA.md](runtime-extra/NOTICE-EXTRA.md) y [runtime-m3g/NOTICE-M3G.md](runtime-m3g/NOTICE-M3G.md).

Proyecto personal, experimental y sin fines de lucro, desarrollado con asistencia de IA (incluidos ChatGPT y Claude). Las aportaciones asistidas requieren revisión y pruebas; no se atribuye autoría sobre bibliotecas, motores o juegos de terceros.

## Aviso legal

Usa j2a con juegos de los que tengas los derechos o copias propias para uso personal.
