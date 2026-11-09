# Avisos de terceros y alcance de licencias

**Licencia del código original:** [MIT](LICENSE). Solo cubre las contribuciones originales del proyecto que no tengan otro aviso. **No sustituye las licencias de terceros**, aunque su código esté incluido en este repositorio o en una APK generada.

| Componente | Origen | Licencia / información |
|---|---|---|
| APIs adicionales de fabricantes, JSR y MascotCapsule | [J2ME Loader](https://github.com/nikita36078/J2ME-Loader) | Apache-2.0, con algunos archivos bajo EPL-1.0; conservar encabezados. [Aviso](runtime-extra/NOTICE-EXTRA.md) |
| M3G / JSR-184, código nativo y bindings | Nokia / Symbian Foundation, adaptaciones de J2ME Loader | EPL-1.0 para el código de Nokia; consultar [aviso](runtime-m3g/NOTICE-M3G.md). Los archivos modificados sujetos a EPL deben ofrecerse bajo esa licencia al distribuirse. |
| Shaders retro | Autores individuales, colección libretro | MIT o dominio público según archivo. [Relación](runtime/src/main/assets/shaders-retro/LICENSES.md) |
| D8/R8 | R8 project authors | BSD-3-Clause y licencias de dependencias. [Texto y avisos](android-offline/converter/src/main/assets/licenses/r8.txt) |
| apksig | Android Open Source Project | Apache-2.0. [Texto](android-offline/converter/src/main/assets/licenses/apksig.txt) |
| Herramientas de descompilación | Vineflower, CFR y dependencias descargadas | Sus licencias independientes se aplican a los binarios que se descarguen o redistribuyan; comprobar la versión exacta antes de empaquetarlos. |
| Juegos Java ME y sus recursos | Titulares de cada juego | No forman parte de la licencia del proyecto. Convertirlos no transfiere derechos de autor. |

## Distribución

- Conservar los encabezados de copyright y los textos de licencia de cada componente.
- Si se distribuyen fuentes modificadas sujetas a EPL-1.0, ponerlas a disposición bajo sus condiciones.
- Las APK de juegos convertidos pueden incorporar partes del motor y dependencias con avisos propios; el usuario que redistribuya esas APK debe revisar y cumplir las licencias correspondientes.
- Este archivo es un índice, no una auditoría jurídica exhaustiva ni una afirmación de que todos los archivos tengan una licencia uniforme.
- El proyecto es un pasatiempo sin fines de lucro; esa condición **no exime** del cumplimiento de licencias. Se ha utilizado asistencia de IA (incluidos ChatGPT y Claude) en tareas de desarrollo y documentación, con revisión humana necesaria.
