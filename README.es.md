# AudioAnchor
*Reproductor de audio para Android que sigue el progreso de escucha de tus audiolibros y podcasts.*

<a href="https://f-droid.org/packages/com.prangesoftwaresolutions.audioanchor/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid" height="80">
</a>

## Características
AudioAnchor ofrece una interfaz limpia e intuitiva para escuchar archivos de audio sin perder de vista el progreso de la escucha.

Características adicionales:
- Establecer marcadores
- Ajustar la velocidad de reproducción
- Exportación e importación del progreso de escucha como base de datos SQL
- Temporizador de apagado
- Controles multimedia de la pantalla de bloqueo (para Android 5.0 Lollipop y versiones posteriores)

## Instalación
Lo mejor es instalar [F-Droid](https://f-droid.org/) en su dispositivo Android, busque AudioAnchor e instale la aplicación desde allí. De esta forma, AudioAnchor se mantendrá actualizado automáticamente en su dispositivo.\
Alternativamente, puede descargar la última apk de [F-Droid](https://f-droid.org/packages/com.prangesoftwaresolutions.audioanchor/) (o desde [releases](https://github.com/flackbash/AudioAnchor/releases)) y ejecútelo en su dispositivo.\
La última versión funciona en dispositivos con Android API nivel 19 (Android 4.4 KitKat) y superior.

## Utilización
Empiece añadiendo directorios a su biblioteca. Al añadir un nuevo directorio tienes dos opciones:
* Puede añadir un directorio que contenga archivos de audio
* Puede añadir un directorio que contenga subdirectorios que contengan archivos de audio

Por ejemplo, puede tener un directorio `Podcast History Hour` que contenga archivos de audio.
Añada este directorio a su biblioteca utilizando la opción *Añadir directorio que contiene archivos de audio*.
Además, puede tener un directorio `AudioBooks` que contenga subdirectorios como `The Hobbit` o `Harry Potter II` cada uno de los cuales contenga uno o varios archivos de audio.
Puede añadir este directorio a su biblioteca utilizando la opción *Añadir directorio con subdirectorios que contengan archivos de audio*.\
Esto le evita tener que añadir sus audiolibros uno a uno.

Añade imágenes de portada a los subdirectorios para tener una aplicación de aspecto cuidado, como se muestra en las capturas de pantalla siguientes.

<pre>
<img src="https://github.com/flackbash/AudioAnchor/blob/master/metadata/android/en-US/phoneScreenshots/01MainActivity.jpg" height="400"/>    <img src="https://github.com/flackbash/AudioAnchor/blob/master/metadata/android/en-US/phoneScreenshots/10AlbumActivityLOTR.jpg" height="400"/>    <img src="https://github.com/flackbash/AudioAnchor/blob/master/metadata/android/en-US/phoneScreenshots/20PlayActivityLOTR.jpg" height="400"/>
</pre>

Navegue y reproduzca sus archivos de audio como lo haría en cualquier otro reproductor de audio.

## Permisos
AudioAnchor pide los siguientes permisos:
- Leer almacenamiento externo (para reproducir archivos de audio)
- Escribir almacenamiento externo (para exportar el progreso de la escucha)
- Leer el estado del teléfono (para pausar el audio durante una llamada)

## Licencia

    Copyright © 2018-2022 Natalie Prange

    Este programa es software libre: puede redistribuirlo y/o modificarlo
    bajo los términos de la Licencia Pública General GNU publicada por la
    la Free Software Foundation, ya sea la versión 3 de la Licencia, o
    (a su elección) cualquier versión posterior.

    Este programa se distribuye con la esperanza de que sea útil,
    pero SIN NINGUNA GARANTÍA; ni siquiera la garantía implícita de
    COMERCIABILIDAD o IDONEIDAD PARA UN PROPÓSITO PARTICULAR.  Consulte la
    Licencia Pública General GNU para más detalles.

    Debería haber recibido una copia de la Licencia Pública General GNU
    junto con este programa.  Si no es así, consulte <https://www.gnu.org/licenses/>.
