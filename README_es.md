**CoreTrace 1.4.0 — Minecraft Java 26.3 · Fabric · Solo cliente**

### Copias y perfiles de tareas (1.4.0)

**Copiar** duplica una tarea justo después de la original y abre la copia para editarla. Conserva la consulta, el nombre CSV, los comandos con sus delays y los ajustes propios, sin compartir cambios con la original. **Vaciar tareas** elimina toda la lista actual; los perfiles guardados se conservan.

En **Cola de tareas → Perfiles de tareas**, escribe un nombre y pulsa **Guardar copia** para guardar todas las tareas, en su orden actual. Pulsa un perfil para cargarlo y reemplazar la lista actual; **×** elimina el perfil guardado. Los perfiles persisten entre reinicios, y editar o ejecutar una lista cargada no modifica su perfil guardado. Los nombres deben ser únicos. Los ajustes globales, sonidos de la cola y Task delay siguen siendo los actuales; el perfil guarda las tareas y sus opciones propias.

Task delay usa **3000 ms** en configuraciones nuevas y al dejar el campo vacío. Los delays ya guardados se respetan. Los comandos individuales mantienen su valor predeterminado de **750 ms**.

En **Cola de tareas**, aparece un triángulo junto a Task delay cuando el valor es menor de 3.000 ms. Al pasar el cursor muestra una advertencia; puedes guardar y ejecutar igualmente cualquier delay válido. El campo vacío usa 3.000 ms y no muestra la advertencia. Los valores menores siguen permitidos.

### Página única y varios comandos (1.3.1)

**Ajustes → Captura → Aceptar página única sin pie** viene activado por defecto. Cuando CoreProtect omite el pie de paginación de la primera página, se espera el tiempo de cierre sin datos, se guarda y se continúa la cola. La exportación indica «Página única guardada»; la marca `page_confirmed` sigue siendo falsa porque el servidor no envió un pie. No se aplica a páginas posteriores de una consulta incompleta. Al desactivarlo se conserva «fin inferido, no confirmado» y la cola se detiene después de guardar. Las tareas heredan esta opción de los ajustes globales o pueden cambiarla en sus propios ajustes.

En **Cola de tareas → Editar tarea → Comandos personalizados**, usa **Agregar**, **×** para eliminar y **↑ / ↓** para ordenar. No hay un límite fijo de comandos; el menú usa páginas. Cada comando tiene su delay en ms (vacío: 750), que se espera después del guardado o del comando anterior. Tras el último comando se espera el Task delay antes de la siguiente tarea. Una lista vacía omite los comandos. El comando único de versiones anteriores migra automáticamente con su delay.


### Cola de tareas (1.3.0)

En **F8 → Cola de tareas**, usa **Agregar** para crear tareas, **×** para eliminarlas y **↑ / ↓** para cambiar su orden. La lista se guarda entre reinicios; no se ejecuta automáticamente al conectarte.

Cada tarea tiene un comando completo de consulta CoreProtect, nombre CSV, ajustes CSV opcionales y un comando personalizado opcional. **Ajustes globales** viene activado. Al desactivarlo, **Ajustes de tarea** permite configurar captura y sonidos sin modificar los ajustes globales. Los sonidos de cada tarea comienzan apagados; los sonidos de toda la cola se eligen en **Sonidos de la cola**.

Abrir **Ajustes CSV** sin guardar ni cambiar opciones conserva la herencia global. **Restaurar CSV global** elimina la configuración CSV propia. El nombre siempre procede del campo CSV de la tarea, incluso si está vacío.

La secuencia es: consulta → captura y guardado → delay del comando personalizado → comando opcional → Task delay → siguiente consulta. Ambos delays admiten 0–3600000 ms. Si se dejan vacíos, **Task delay usa 3000 ms** y el **delay de cada comando usa 750 ms**. Sin comando personalizado se omite su delay. No hay espera adicional entre tareas después de la última. Los tiempos y opciones quedan fijados al iniciar la cola.

Ejemplo: `/co l a:command t:3d`, CSV `Simply1`, ajustes globales activados y comando `/simply 2`. Al terminar de guardar, se espera el delay del comando, se envía `/simply 2`, se espera el Task delay y se inicia la siguiente tarea. Para cambios de mundo lentos, aumenta el Task delay: el mod espera el tiempo configurado, no una confirmación de teletransporte del servidor.

Una consulta fallida, timeout, límite de páginas, cancelación, error de escritura o desconexión detiene la cola. Se conserva lo que haya podido guardarse; no se ejecutan las tareas pendientes. El comando personalizado se envía como comando del jugador con sus permisos normales.

### Carpetas y limpieza del nombre

Un CSV llamado `julio.csv` se guarda dentro de `coretrace/exports/julio/`. Si existe esa carpeta, se usa `julio (2)`, `julio (3)`, etc., sin sobrescribir resultados. Con el nombre vacío se mantiene el nombre de carpeta anterior basado en fecha e identificador.

**Ajustes → Exportación CSV → Autolimpiar** viene apagado. Si se activa, el nombre se borra solo después de guardar la exportación; también se aplica al nombre de una tarea según sus ajustes CSV. Un nombre nuevo escrito durante una captura se conserva.


CoreTrace acompaña al plugin CoreProtect Community Edition. Detecta una consulta que escribes en el chat, recoge sus resultados, solicita las siguientes páginas y guarda una transcripción y un resumen en tu equipo. El código de este asistente es independiente; no contiene ni sustituye el plugin CoreProtect.

Para instalarlo:

1. Abre una instancia de **Minecraft Java 26.3** con **Fabric Loader 0.19.5 o posterior**. El juego utiliza Java 25; el launcher oficial suele gestionar su runtime. En otros launchers, selecciona Java 25.
2. Instala [Fabric API para 26.3](https://modrinth.com/mod/fabric-api/versions?g=26.3), versión **0.161.0+26.3** o posterior compatible.
3. Copia `coretrace-1.4.0+26.3.jar` dentro de la carpeta `mods` de esa instancia. Si tenías la versión anterior, retira su JAR para dejar una sola versión de CoreTrace. Tu configuración e historial se conservan.
4. Si quieres abrirlo desde la lista de mods, instala [Mod Menu compatible con 26.3](https://modrinth.com/mod/modmenu). Es opcional: F8 y `/coretrace` funcionan sin Mod Menu.
5. Entra a un servidor que ya tenga CoreProtect funcionando y donde tu usuario pueda ejecutar las consultas.

[Instalador oficial de Fabric](https://fabricmc.net/use/installer/). Este archivo no es para Forge, NeoForge, Bedrock ni para una versión distinta de Minecraft.

Escribe normalmente tu consulta:

```text
/co l a:-block u:jose t:3d
```

La captura automática viene activada. CoreTrace recoge la página inicial, espera su pie y envía `/co l 2`, `/co l 3`, etc., hasta la última página. Conserva el tamaño de página que utiliza el servidor. La pausa predeterminada entre respuestas es de **1500 ms**. Solo hay una solicitud pendiente a la vez.

Abre **F8** o escribe **`/coretrace`** para ver el menú. En Mod Menu: selecciona **CoreTrace** y abre su configuración. Puedes reasignar F8 en los controles del juego.

**Idioma:** entra en **F8 → Ajustes → Idioma** y pulsa el botón para alternar entre **Español** y **English**. La elección se guarda y funciona independientemente del idioma de Minecraft. Menús y avisos nuevos cambian al instante. El resumen y los encabezados del transcript usan el idioma elegido al iniciar la captura; una captura ya iniciada conserva su idioma de informe. Los mensajes ya escritos en el chat no se reescriben.

**El CSV siempre usa columnas en inglés y conserva el texto original del servidor.** Con CoreProtect en inglés, verás `broke`, `logged in`, `removed`, etc., aunque el menú esté en español. No se traduce el contenido del chat, los comandos, los carteles ni los textos emergentes. Si el servidor usa otro idioma, también se conserva ese original.

[English instructions](README-en.md).

En **Ajustes → Exportación CSV → Nombre del archivo CSV** puedes escribir, por ejemplo, `consulta` o `consulta.csv`. Se generará `consulta.csv`; si la exportación se divide en cuatro partes, serán `consulta_1.csv`, `consulta_2.csv`, `consulta_3.csv` y `consulta_4.csv`. Deja el campo vacío para el nombre predeterminado. Guarda los ajustes antes de iniciar la captura: el nombre y las columnas quedan fijados para esa sesión.

En **Ajustes → Sonidos**, cada selector muestra todos los eventos de sonido de Minecraft cargados, incluida música y discos. Busca por identificador (por ejemplo `allay`, `music` o `note block`) o por el subtítulo en el idioma del juego. Usa **Probar** para escuchar y **Detener vista previa** para detenerlo; al salir también se detiene. Los paquetes de recursos pueden sustituir estos audios. La reproducción respeta el volumen del juego.

**Última exportación** abre la carpeta de la última sesión que contiene el CSV (o sus partes). **Carpeta de archivos** abre la carpeta general de exportaciones. En Windows se usa la apertura nativa de carpetas, sin depender de Java AWT.

El menú incluye:

- Una caja donde escribir el comando completo o solamente los filtros, como `a:-block u:jose t:3d`.
- Progreso, pausa, continuación y cancelación con guardado de lo recibido.
- Visor con filtro por texto y copia de los registros filtrados.
- Hasta 20 consultas recientes y 20 favoritas, conservadas entre reinicios.
- Acceso a la última exportación y a la carpeta de archivos.
- Selector de español e inglés.
- Ajustes de velocidad, tiempo de espera, límite de páginas, textos emergentes y ocultación del chat capturado.
- Selección de columnas del CSV, corte opcional por grupos de páginas y sonidos vanilla de inicio/finalización.

El mod escribe dentro de la **carpeta de la instancia de Minecraft**, normalmente:

```text
.minecraft/coretrace/exports/20260906-120000-000-ab12cd34/
```

Cada captura tiene su propia carpeta y estos cuatro archivos:

| Menú español | Menú inglés | Contenido |
| --- | --- | --- |
| `transcript.log` | `transcript.log` | Mensajes de CoreProtect en orden de recepción, fechas de recepción, textos emergentes y presencia de texto tachado. Se escribe durante la captura. |
| `resumen.txt` | `summary.txt` | Consulta, servidor, alcance, estado final, páginas capturadas y conteos por usuario, tipo de evento, acción y bloque u objeto identificado. |
| `registros.csv` | `records.csv` | Una fila por evento de cualquier tipo reconocido de CoreProtect: columnas estables en inglés y datos originales. UTF-8 con BOM y separador coma. |
| `sesion.json` | `session.json` | Datos estructurados, páginas confirmadas, mensajes pendientes y avisos del servidor. |

El CSV admite las consultas de estas categorías, sin limitarse a bloques:

| Tipo | Ejemplo de filtro | Datos específicos |
| --- | --- | --- |
| Bloques | `a:block`, `a:-block`, `a:+block` | Acción y bloque. |
| Sesiones | `a:session` | Entrada o salida del jugador. |
| Chat | `a:chat` | Texto del mensaje. |
| Comandos | `a:command` | Comando registrado. |
| Interacciones | `a:click` | Objeto pulsado. |
| Contenedores | `a:container` | Acción, cantidad y objeto. |
| Objetos | `a:item` | Recoger, soltar, depositar, retirar, lanzar o disparar. |
| Inventario | `a:inventory` | Movimiento, cantidad y objeto mostrado por CoreProtect. |
| Muertes de entidades | `a:kill` | Entidad registrada. |
| Carteles | `a:sign` | Texto, incluidos saltos de línea. |
| Nombres | `a:username` | Nombre que CoreProtect muestra y nombre registrado al entrar. |

Por ejemplo, `/co l a:chat u:jose t:3d` y `/co l a:container u:jose t:3d` se paginan y exportan de la misma manera. Ejecuta cada consulta por separado: los filtros que CoreProtect permite combinar dependen del plugin. El mod no consulta automáticamente todas las categorías cuando eliges una.

El esquema tiene **24 columnas**: página solicitada y confirmada, filtro de acción, tipo, fecha exacta, tiempo relativo, usuario, acción original, identificador de acción, signo, cantidad, objeto, mundo, X/Y/Z, contenido, nombre registrado, tachado, estado de interpretación, texto original, línea de coordenadas y textos emergentes. Tienes la definición completa en [CSV-SCHEMA.md](CSV-SCHEMA.md).

`action` guarda el verbo original; `action_id` y `event_type` son identificadores estables en inglés. Las coordenadas, cantidades y fechas que el servidor no envía quedan vacías. Chat y comandos tienen el mismo formato en CoreProtect CE: se distinguen por el filtro `a:`. Si solo se captura una página antigua o la consulta resulta ambigua, el CSV usa `message` y `parse_status=ambiguous`. Una acción nueva o desconocida conserva su texto y se marca `unrecognized`. Los formatos personalizados pueden necesitar ajustes del lector.

CoreProtect CE representa ciertos movimientos de inventario, incluidas operaciones internas de fabricación o comercio, como `added` o `removed`. El cliente conserva esa información y no inventa un subtipo que no aparece en la respuesta. El contador del resumen cuenta filas, no suma `amount`.

Para abrir el CSV en Excel o LibreOffice, impórtalo como **UTF-8, separado por comas**, si tu configuración regional no lo separa al abrirlo directamente. Comas, comillas y saltos de línea se escapan como CSV. Los textos que podrían interpretarse como fórmulas reciben un apóstrofo protector en el CSV; el texto capturado de esos mensajes permanece sin ese apóstrofo en el transcript y JSON. Se eliminan los códigos de formato de Minecraft al convertir componentes a texto plano.

Los textos emergentes conservan la fecha exacta que CoreProtect envía al pasar el cursor sobre el tiempo relativo. Los registros idénticos se conservan: dos acciones iguales no se convierten en una sola. El indicador de texto tachado permite conservar esa señal visual de los resultados del plugin.

Los conteos del resumen son **registros de evento**, no cantidades de objetos ni daño neto. Se reconocen verbos frecuentes en inglés, español y portugués; las acciones que no se pueden clasificar permanecen como «sin clasificar» y conservan su texto. Un resultado tachado no se resta automáticamente de los conteos. Las coordenadas se adjuntan al evento precedente.

La captura distingue los siguientes finales:

- **Finalizada con pie de última página:** se recibieron las páginas consecutivas hasta el total anunciado.
- **Página única guardada:** CoreProtect normalmente no envía paginación para una consulta de una sola página. Tras 3500 ms sin más datos se guarda y se continúa por defecto. Al desactivar «Aceptar página única sin pie», se usa «Sin pie, final inferido» y la cola se detiene. En ambos casos se conserva que no hubo confirmación del servidor. Aumenta «Sin pie ms» si necesitas tolerar respuestas más lentas.
- **Sin resultados:** el servidor lo informó explícitamente.
- **Parcial:** cancelación, desconexión, límite de páginas, respuesta inesperada, error del servidor o tiempo agotado.

Si el juego se cierra bruscamente, `transcript.log` puede conservar lo escrito hasta ese momento. Si no tiene una línea que comienza por `FINAL:`, debes tratarlo como una sesión interrumpida. Los archivos de resumen se crean al finalizar normalmente o al cancelar/desconectar mientras el cliente sigue funcionando.

Los ajustes se guardan en `config/coretrace.json`. Sus valores iniciales son:

| Ajuste | Valor inicial | Rango |
| --- | --- | --- |
| Idioma | Español | Español / English |
| Captura automática | Activada | Activada / desactivada |
| Pausa entre páginas | 1500 ms | 750–30000 ms |
| Espera máxima por solicitud | 30000 ms | 5000–180000 ms |
| Cierre sin pie | 3500 ms | 1500 ms hasta espera máxima menos 1000 ms |
| Máximo de páginas por captura | 500 | 1–5000 |
| Guardar textos emergentes | Activado | Activado / desactivado |
| Ocultar mensajes capturados del chat | Desactivado | Activado / desactivado |

Los tiempos y el máximo de páginas se fijan al comenzar cada captura. El cambio de estos ajustes se aplica a la siguiente. Si el servidor limita la frecuencia de comandos, aumenta la pausa, por ejemplo a 2000 o 3000 ms. El exportador también tiene límites de memoria: 512 mensajes por página pendiente y 100000 mensajes de resultados por sesión; si se alcanzan, guarda una captura parcial.

Los comandos locales disponibles son:

| Comando | Acción |
| --- | --- |
| `/coretrace` | Abre el menú. |
| `/coretrace start a:-block u:jose t:3d` | Inicia una captura desde filtros. |
| `/coretrace start /co l a:-block u:jose t:3d` | Inicia una captura desde el comando completo. |
| `/coretrace pause` | Pausa el envío de nuevas páginas; sigue recogiendo la respuesta pendiente. |
| `/coretrace resume` | Continúa una captura pausada en la misma conexión. |
| `/coretrace cancel` | Detiene la captura y exporta lo recibido. |
| `/coretrace status` | Muestra el estado. |
| `/coretrace folder` | Abre la carpeta de exportaciones. |

Una consulta numérica como `/coretrace start /co l 3` permite recoger desde una página existente: el informe indicará que faltan las anteriores. La captura automática del chat comienza con una consulta nueva con filtros, no con un cambio de página aislado. Se reconocen `co`, `core`, `coreprotect` y sus variantes con el espacio de nombres `coreprotect:`. No se aceptan `p:`, `page:` ni `#count` en una consulta nueva: usa una consulta normal desde la primera página.

Durante una captura se bloquea el envío manual de otras órdenes `/co` para evitar sustituir la consulta que CoreProtect mantiene en memoria. Evita usar el inspector mientras está capturando. La pausa no libera esa consulta; cancela si necesitas ejecutar otra orden. Tras un final incierto, el asistente espera el tiempo de espera configurado antes de iniciar otra captura, para dejar llegar las respuestas tardías. No reintenta páginas automáticamente y no reanuda una consulta al cambiar de servidor. El historial permite iniciarla de nuevo.

El cliente solo ve los mensajes que recibe y necesita los permisos normales de CoreProtect. No obtiene acceso a la base de datos ni amplía permisos. La captura automática solo envía consultas; la cola también puede enviar el comando personalizado que configure el usuario. El único tráfico añadido durante una captura son comandos de consulta y paginación. Los archivos se guardan localmente, sin enviarlos a servicios externos.

La interpretación se basa en el formato público de **CoreProtect CE v24.0**: cabeceras, enlaces de página, resultados y coordenadas. Los mensajes modificados por otros plugins, traducciones personalizadas y otras versiones no tienen compatibilidad garantizada. El pie de página no es una instantánea de base de datos: si cambian los datos o la caché durante la consulta, el cliente no puede garantizar identidad transaccional de todos los eventos. Cuando detecta un cambio de total o una página inesperada, detiene y marca la captura como parcial. En una página pendiente del CSV, `page_requested` es la página que se pidió; `page_confirmed=false` indica que no se verificó su correspondencia.

**Compatibilidad del servidor:** CoreTrace está hecho para el **cliente 26.3** y requiere que CoreProtect ya funcione en el servidor al que te conectas. Este mod no hace compatible una instalación del plugin que no lo sea. Comprueba la [publicación oficial de CoreProtect](https://www.spigotmc.org/resources/coreprotect-community-edition.8631/) y sus [permisos](https://docs.coreprotect.net/permissions/).

El proyecto incluye código fuente bajo licencia MIT, estas instrucciones y exportaciones de ejemplo con datos simulados, incluido `ejemplo-simulado/all-event-types.csv` con todas las categorías anteriores. El ejemplo combinado reúne varias consultas simuladas y no representa una única consulta del servidor. No incluye Minecraft, Fabric API, Mod Menu ni el plugin CoreProtect.

Para compilar el código necesitas **JDK 25**. La configuración estándar utiliza Loom 1.17.20 y Gradle 9.5.1; el wrapper se incluye:

```text
Windows: gradlew.bat build
Linux/macOS: ./gradlew build
```

El archivo resultante estará en `build/libs/`. Usa el JAR normal; el archivo que termina en `-sources.jar` es código fuente y no se instala como mod.

También se incluye una compilación alternativa con Python 3.10+ y JDK 25:

```text
python build-portable.py
```

Configura `JAVA_HOME` apuntando al JDK 25 o posterior si no está en el PATH. El script descarga las clases oficiales de Minecraft 26.3, Fabric y Mod Menu, verifica sus hashes publicados, compila las fuentes y ejecuta las pruebas. Guarda las dependencias en `.portable/`, que no se distribuye. Minecraft 26.3 utiliza clases sin ofuscación, por lo que este camino genera el JAR sin una etapa de remapeo. El script requiere acceso de red a los repositorios oficiales. `build-portable.bat` permite ejecutarlo en Windows.

La versión 1.4.0 se compiló con Gradle (`gradlew.bat test build --offline`) y pasó **104 pruebas automatizadas**, sin fallos ni errores. Las 51 clases del JAR se verificaron como bytecode de Java 25 (versión 69). **La interfaz y una sesión real de Minecraft/CoreProtect todavía no se han probado de principio a fin.**

Para la primera comprobación en tu servidor: realiza una consulta pequeña de un usuario y un periodo conocidos, verifica que se muestra su última página y compara el transcript con los resultados del chat. Si el formato no se reconoce, conserva el transcript parcial y una captura del chat para ajustar el lector a ese formato.

Referencias técnicas utilizadas:

- [Fabric para Minecraft 26.3](https://fabricmc.net/2026/09/15/263.html).
- [Proyecto de ejemplo oficial de Fabric](https://github.com/FabricMC/fabric-example-mod).
- [Comandos de CoreProtect](https://docs.coreprotect.net/commands/).
- [Paginación pública de CoreProtect CE v24.0](https://github.com/PlayPro/CoreProtect/blob/v24.0/src/main/java/net/coreprotect/utility/ChatUtils.java).
- [Formato de los resultados de CoreProtect CE v24.0](https://github.com/PlayPro/CoreProtect/blob/v24.0/src/main/java/net/coreprotect/command/lookup/StandardLookupThread.java).
- [Código de Mod Menu](https://github.com/TerraformersMC/ModMenu).

CoreTrace es un proyecto independiente. No está afiliado a PlayPro, Mojang, Microsoft, Fabric ni TerraformersMC. Los nombres de sus productos se usan para describir compatibilidad. Las dependencias y el wrapper de Gradle conservan sus licencias respectivas.

El código de trabajo está directamente en esta carpeta: `src/` contiene las fuentes y pruebas; `gradle/` y los scripts del wrapper permiten compilar. Los JARs se generan en `build/libs/`; las cachés, compilaciones y paquetes de entrega no forman parte del código fuente. La primera compilación tras limpiar cachés puede necesitar descargar dependencias.
