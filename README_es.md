**CoreTrace 1.7.14 — Minecraft Java 26.3 · Fabric · Solo cliente**

### Editar tareas pendientes en pausa e informes de error (1.7.14)

Al pausar Task Queue puedes agregar, copiar, editar, eliminar y ordenar las **tareas pendientes** desde el menú de la cola. La tarea actual permanece bloqueada porque puede tener páginas o comandos personalizados guardados. Los cambios afectan solo a esta ejecución; se guardan en el punto de recuperación antes de aplicarse y sobreviven a un reinicio del cliente. Si falla el guardado, se conserva la cola anterior. Si eliminas la última tarea pendiente cuando la cola está entre tareas, esta termina de forma segura. Se conservan el libro Excel y los archivos ya generados.

Cuando una captura, reanudación, escritura o cola termina con un error, CoreTrace crea `error-report-...txt` junto a la exportación, en la carpeta del libro Excel o en `coretrace/exports` si el fallo corresponde a la cola. Puedes abrir el informe más reciente desde **Ver registros → Abrir último informe de error**. Incluye versiones del mod y Java, código interno del error, páginas e intentos, estado de Smart Delay y eventos internos recientes. Excluye comandos, direcciones de servidor, chat, registros capturados y mensajes de excepciones. Un kick por spam o un fallo transitorio resuelto automáticamente no genera informe. El archivo permanece local y nunca se envía automáticamente.

### Reanudación fiable de CSV inteligente (1.7.13)

Al comprobar páginas guardadas después de una desconexión, CoreTrace compara fecha y hora UTC de CoreProtect, jugador, acción, contenido y coordenadas interpretadas de varios registros consecutivos. Ignora los espacios iniciales de la línea de coordenadas, ya que CoreProtect cambia su alineación cuando crece el tiempo relativo mostrado (por ejemplo, de `9.98/h ago` a `10.04/h ago`). Una fecha o coordenada diferente sigue impidiendo la coincidencia. Si no encuentra una secuencia fiable, detiene la captura y conserva los datos de recuperación. La corrección se aplica al modo simple y a Task Queue, e incluye el caso reportado de 213 páginas en el que un cambio de un espacio detuvo la reanudación.

### Estabilidad configurable después de un kick (1.7.12)

En **Ajustes → Captura → Smart Delay → Estabilidad tras kicks** puedes configurar cuánto tiempo de páginas confirmadas necesita Smart Delay antes de volver a acelerar después de cada kick por spam. Los cuatro valores predeterminados son **30, 60, 90 y 120 segundos**. Cada campo admite 1–120 segundos; los valores no pueden disminuir entre kicks. Si dejas un campo vacío, usa su valor predeterminado. El submenú incluye un botón para restaurar los cuatro valores. También se aplica a las tareas de Task Queue según sus ajustes efectivos. Las pausas, desconexiones y períodos largos sin actividad no consumen esta espera. Al cargar un perfil antiguo, una espera guardada que supere el período configurado para su número de kicks se reduce a ese período. Los cambios de ajustes se aplican desde la próxima captura.

### Variación de Smart Delay por página (1.7.11)

Smart Delay ahora varía la espera entre páginas de CoreProtect con un patrón de tres pasos alrededor del valor aprendido: **0 %, +5 %, +10 %**. Con una base de 750 ms, las esperas previstas son **750, 788 y 825 ms** (redondeadas al milisegundo más cercano). Se respetan el mínimo configurado y el tope de 30000 ms. Un kick por spam aumenta el valor aprendido a **1,5 veces la espera real elegida para la última página**; por ejemplo, un kick tras el paso de 825 ms lo eleva a unos 1238 ms. Esto no altera el delay manual, los delays de comandos personalizados ni el Task delay. La posición del patrón y las esperas recientes se guardan con el aprendizaje, también para reanudar.

El submenú agrupa **Mín**, **Promedio** y **Máx** bajo **Últimos 3 comandos**. Son estadísticas descriptivas de las tres esperas más recientes elegidas para pedir páginas. **Siguiente** muestra la espera prevista para la próxima página; puede superar el valor aprendido debido a la variación. El promedio no dirige el algoritmo de adaptación. La ventana de observación de 5 segundos sigue determinando cuándo puede cambiar el valor aprendido.

### Smart Delay y ETA (1.7.10)

En **Ajustes → Captura**, el interruptor junto al delay activa **Smart Delay**. Viene **desactivado**: el campo manual conserva su valor y funciona como antes. Al activarlo, el campo manual deja de ser editable y aparece debajo el botón **Smart Delay**. Se aplica a páginas de CoreProtect en capturas simples y Task Queue, con los ajustes efectivos de cada tarea. No modifica los delays entre tareas ni los comandos personalizados.

En el submenú puedes configurar:

- **Mensaje de expulsión por spam**: `Kicked for spamming` por defecto. Busca ese texto dentro del motivo real de desconexión, sin distinguir mayúsculas. Vacío restaura el predeterminado; las desconexiones por otros motivos no penalizan el ritmo.
- **Mínimo (ms)**: **240** por defecto o al dejarlo vacío; rango **240–30000 ms**. Es el límite inferior permitido, no una orden para empezar directamente a esa velocidad. Un aprendizaje nuevo empieza con el delay manual, o con este mínimo si es mayor. Los cambios se aplican desde la próxima captura. Los mínimos ya guardados se conservan; vacía el campo para usar 240 ms.
- **Ventana de observación (s)**: **5 segundos** por defecto o al dejarla vacía; rango **1–300 segundos**. No es el delay entre comandos. Ventanas cortas aportan menos evidencia. No existe una cuota fija de 30 comandos.
- **Estabilidad tras kicks**: cuatro períodos editables de actividad confirmada, **30/60/90/120 segundos** por defecto, con un máximo de dos minutos.
- **Aprendizaje separado por servidor**: **desactivado** por defecto. Desactivado usa un único perfil global guardado, compartido entre servidores, tareas y sesiones. Activado mantiene perfiles independientes por dirección; cambiar la opción no borra los anteriores. Dos direcciones no se fusionan automáticamente, aunque Recuperación permita comprobar registros desde otra dirección.
- **Restablecer este aprendizaje** elimina solo el perfil seleccionado y permite aprender de nuevo.

Los cuatro indicadores de solo lectura muestran el **mínimo, promedio y máximo de los últimos tres delays elegidos para pedir páginas de CoreProtect**, además del **próximo delay previsto**. Antes de reunir tres solicitudes se muestran las muestras disponibles; antes de la primera, aún no hay datos. Son esperas entre comandos, no tiempos de respuesta ni límites garantizados del servidor. Para 550, 440 y 240 ms, se ven 240, 410 y 550 ms. También aparece la fecha del último kick por spam.

Después de seis ventanas de observación estables, Smart Delay reduce el delay un 10 %. Cuando llega al 150 % o menos de un delay que causó problemas, reduce solo un 2 % (redondeado al milisegundo más cercano, al menos 1 ms); un paso rápido no salta ese umbral. Siempre respeta el mínimo configurado. Un kick por spam eleva el valor aprendido a 1,5 veces la última espera entre páginas (hasta 30000 ms), recuerda el valor problemático y exige el período configurado de actividad confirmada antes de volver a acelerar. Las pausas, desconexiones y períodos largos sin actividad no reducen la espera pendiente. La espera y el historial de kicks persisten al reiniciar. El menú muestra los segundos activos restantes. El mínimo aprendido deja de ser permanente: después de estabilizarse, el mod puede probar valores inferiores con pasos pequeños. Todavía pueden producirse nuevos kicks al probar.

Las estadísticas de las últimas tres solicitudes son informativas; la adaptación usa ventanas completas. El enfriamiento de reconexión/reanudación tras un kick sigue siendo al menos una ventana o dos delays. El aprendizaje se guarda en `coretrace/smart-delay.json`. La exportación CSV/Excel y los datos de recuperación mantienen su funcionamiento.

El botón rojo **Restablecer de fábrica** del menú principal abre una confirmación. Al aceptar, restaura todos los ajustes globales, incluido el idioma, y borra todo el aprendizaje global y por servidor de Smart Delay. Conserva los perfiles, la lista actual de tareas y sus opciones propias, favoritos, historial de consultas, archivos generados y datos de recuperación. No está disponible durante una captura, cola, guardado o recuperación en ejecución. Cancelar la ventana no modifica nada.

**Mostrar ETA y progreso**, debajo de **Aceptar página única sin pie**, viene **activado**. Controla todo el indicador sobre la barra rápida: `ETA 01:23:45 · 67 %`. El porcentaje y el tiempo corresponden a la captura/tarea actual, no a todas las tareas futuras de la cola. Usa un promedio suavizado del tiempo real entre páginas confirmadas, incluidas las esperas. Empieza con **Calculando…**, muestra **Pausado** o **Recuperando…** cuando corresponde y recalcula tras esas interrupciones. El 100 % solo aparece cuando la captura termina correctamente. El cálculo mantiene unas pocas variables, no relee CSV y actualiza el texto como máximo una vez por segundo.

### Reanudar desde otra dirección (1.7.7)

En el menú principal, **Reanudar** aparece junto a **Start capture** cuando una captura simple está pausada o existe una captura/cola con recuperación pendiente. Ocupa temporalmente el lugar de Favoritos. Continúa una pausa o inicia otro ciclo de recuperación de los datos guardados.

**Ajustes → Recuperación → Reanudar desde otra dirección** viene activado. Una dirección diferente solo se admite cuando la captura guardada tiene páginas confirmadas y CSV inteligente habilitado. CoreTrace vuelve a ejecutar la consulta y exige coincidencias de registros al inicio y cerca del final guardado antes de incorporar datos nuevos. Tener acceso a CoreProtect por sí solo no demuestra que sea la misma consulta. Si no se puede comprobar la unión, conserva el parcial; no reinicia toda la captura. Sin páginas verificables, o con CSV inteligente desactivado, se exige la dirección original. Una cola entre tareas tampoco ofrece registros de una captura pendiente para verificar otra dirección.

La reanudación tras una desconexión admite hasta **5 intentos automáticos**. Si fallan, conserva el progreso y permite volver a intentarlo con Reanudar. Una discrepancia comprobada de registros detiene los intentos automáticos inmediatamente; volver a pulsar Reanudar permite una nueva comprobación. Las respuestas tardías y el timeout se respetan antes del siguiente intento. Este límite es independiente de los reintentos de conexión y de los reintentos por cambios de orden durante una captura. Con la opción de otra dirección activada, se programa un aviso rojo **2 segundos después** del mensaje de reanudación; se cancela si se descarta o termina la recuperación antes de mostrarlo.

Las entradas nuevas se guardan provisionalmente en bloques `.recovery-prefix-<id>-N.json`. Solo después de verificar las uniones del inicio y del final se publica `.recovery-prefix.json`; hasta entonces las páginas anteriores siguen intactas. El CSV, el resumen y el JSON se escriben por bloques. El desplazamiento de páginas también queda guardado para otra desconexión. Los bloques no confirmados se limpian al finalizar el intento y los necesarios para recuperación se eliminan al completar o cancelar y guardar correctamente. Una página lógica del CSV puede contener varias páginas físicas incorporadas al inicio.

### Reintentos configurables de CSV inteligente (1.7.6)

En **Ajustes → Recuperación**, **Reintentos CSV inteligente** permite elegir entre **1 y 10** intentos cuando los resultados cambian de orden; vacío utiliza **5**. Si se alcanza el límite antes de completar las páginas estables, la captura termina como parcial y conserva los datos para su revisión. La recuperación por inspección tiene su propio límite. Los avisos muestran el límite elegido. Esta opción también se aplica a las capturas de Task Queue, con su configuración efectiva.

### Recuperación estable y carpetas con nombre (1.7.5)

En **Ajustes → Recuperación**, el campo **Páginas estables** permite elegir entre 5 y 5000 páginas; vacío utiliza **20**. El límite de reintentos de CSV inteligente se configura por separado. Después de cada interrupción cuenta las páginas nuevas con pie de página confirmado; si llega al valor elegido sin otro cambio, el contador de reintentos vuelve a cero. Por ejemplo, tras un problema en la página 325, llegar a la 345 sin cambios permite recuperar más tarde otro desplazamiento sin agotar el presupuesto anterior. El contador de recuperación por inspección sigue teniendo su propia regla de cinco páginas. Si no se puede verificar la unión de resultados, se conserva un parcial.

En **Task Queue → Modo Excel**, el nombre escrito para el libro también nombra la carpeta de una cola nueva: `commandOctober30` genera `coretrace/exports/commandOctober30/commandOctober30.xlsx`. Si la carpeta existe, se reserva `commandOctober30 (2)`, `commandOctober30 (3)`, etc., mientras el libro conserva su nombre. Si el campo está vacío, se usa `tasks.xlsx` dentro de una carpeta automática `queue-<fecha>-<id>`. Las colas ya guardadas reanudan en su carpeta original; no se renombran ni se sobrescriben archivos anteriores.

### Reanudación inteligente sin repetir cientos de páginas (1.7.4)

Al reconectar o detectar un desplazamiento de resultados, CoreTrace conserva las páginas confirmadas. Consulta el inicio para incorporar entradas nuevas y busca una secuencia de hasta cinco entradas, con al menos tres diferentes cerca de la última página guardada. La comparación usa la fecha del servidor y el contenido estable, porque textos relativos como «hace 1 minuto» cambian con el tiempo. Reemplaza la zona que se vuelve a leer y continúa cerca del final guardado, sin repetir todas las páginas intermedias. Por ejemplo, si terminó la página 322 y dos registros nuevos desplazan el resultado, busca la unión cerca de 322–323 y continúa desde allí.

La búsqueda inicial avanza hasta encontrar la fecha, hora y secuencia de registros guardados, sin el antiguo límite de 12 páginas. Revisa todos los eventos del mismo instante; si pasa a fechas anteriores sin encontrar la coincidencia requerida, detiene la reanudación. La búsqueda cercana al final sigue limitada a 14 páginas y usa el desplazamiento detectado al inicio; si no encuentra coincidencias fiables, conserva el CSV y la recuperación como resultado parcial y avisa, sin reiniciar automáticamente toda la consulta. La unión exige tres entradas diferentes, de modo que consultas muy cortas pueden quedar como parciales. No hay identificadores únicos de CoreProtect ni una instantánea fija del servidor: cambios invisibles en páginas intermedias y eventos idénticos en el mismo segundo no pueden descartarse con certeza. Las páginas guardadas pueden contener más filas que una página física del servidor después de añadir entradas al inicio; `page_requested` en el CSV representa la página lógica del archivo recuperado.

### Límites de tiempos y cancelación segura (1.7.3)

Los límites obligatorios ya evitan tiempos incompatibles: pausa entre páginas de 750 a 30000 ms, timeout de 5000 a 180000 ms y espera sin pie de al menos 1500 ms, siempre 1000 ms menor que el timeout. El menú ahora indica cuál de estos valores debe corregirse; el motor aplica la misma relación entre espera sin pie y timeout. Al reanudar una pausa de captura se conserva la parte pendiente de la pausa entre páginas, en lugar de empezar otra pausa completa.

Tras **Cancelar tarea actual** durante una captura, se espera al menos el timeout de esa consulta antes de iniciar la siguiente, aunque el Task delay sea menor. Las respuestas tardías de CoreProtect prolongan esa espera. Los delays cortos de tareas y comandos siguen permitidos porque su seguridad depende del servidor y del comando; Task delay menor de 3000 ms sigue mostrando el triángulo de advertencia. Las esperas de recuperación y reconexión también admiten cero por decisión del usuario, sin mezclar consultas: la recuperación espera además un período sin respuestas.

### Recuperación de páginas desplazadas en CSV inteligente (1.7.2)

Si aparecen registros más recientes en una página posterior, CSV inteligente detecta que los resultados se desplazaron durante la consulta. Descarta la página incompleta y, tras la cuenta regresiva de recuperación (4000 ms por defecto) y un período sin respuestas de CoreProtect, repite el comando original para buscar las uniones descritas arriba. También funciona dentro de la cola de tareas y no ejecuta la siguiente tarea mientras la captura se recupera. Se permiten hasta el número configurado de reintentos antes de acumular el número configurado de páginas estables; entonces el contador vuelve a cero. Un cambio de zona horaria o una fecha ilegible sigue deteniendo la captura sin reintentos. No puede garantizar una instantánea de un servidor que agrega registros continuamente.

### Pausar la cola y cancelar solo la tarea actual (1.7.2)

En **Task Queue**, **Pausar cola** se convierte en **Reanudar cola** al pulsarlo. La pausa deja terminar la respuesta de CoreProtect que ya está llegando, pero no solicita otra página ni ejecuta comandos personalizados o tareas nuevas. También congela los delays pendientes. El estado de pausa se guarda para una recuperación tras cerrar el cliente; la consulta pendiente solo se envía al pulsar **Reanudar cola**. Cada botón muestra esta explicación al pasar el cursor.

**Cancelar tarea actual** guarda la captura en curso como parcial y omite sus comandos personalizados. Antes de la siguiente tarea espera al menos el mayor valor entre Task delay y el timeout de esa captura; las respuestas tardías de CoreProtect pueden alargar la espera. Si la captura ya terminó pero hay comandos pendientes, los omite y usa el Task delay normal. Las tareas canceladas se identifican como parciales y se conservan sus carpetas de origen, incluso si el resto de la cola termina; en Modo Excel se conserva la hoja parcial. **Detener cola** cancela la tarea actual y no continúa con las tareas restantes.

### Modo Excel de la cola (1.7.1)

**Task Queue → Modo Excel (.xlsx)** está **desactivado por defecto**. Al activarlo antes de iniciar una cola, aparece **Nombre del libro Excel**. En blanco se genera `tasks.xlsx` dentro de una carpeta automática; por ejemplo, `julio` o `julio.xlsx` genera `coretrace/exports/julio/julio.xlsx`. Si la carpeta ya existe, se añade `(2)`, `(3)`, etc. al nombre de la carpeta, sin cambiar el nombre del libro. El libro tiene una hoja por tarea. El botón explica el comportamiento al pasar el cursor. **Abrir última exportación** lleva a la carpeta del libro. Durante la cola, las capturas individuales mantienen sus CSV, resumen, JSON y transcript.

Cada hoja usa el nombre CSV de la tarea, o `Task N` si está vacío, y respeta sus columnas seleccionadas. Los nombres de hoja se adaptan a Excel (máximo 31 caracteres, caracteres permitidos y nombres únicos). Todas las partes CSV de una tarea se reúnen en su hoja, con un solo encabezado. Coordenadas, cantidad y página solicitada se escriben como números; fechas del servidor y demás textos conservan su representación. La primera fila queda fija y dispone de filtros. Si una tarea supera 1.048.575 registros, continúa en otra hoja con encabezados: el límite de Excel cuenta también la fila del encabezado. La división por páginas de CSV sigue aplicándose a los CSV, no determina las hojas Excel.

El libro se actualiza al guardar cada tarea, también cuando se guarda una captura parcial. La escritura ocurre en segundo plano, leyendo los CSV por registros; no carga toda la cola en memoria. Cada actualización reconstruye el libro y puede tardar más cuanto mayor sea la cola. Hasta terminar la escritura se conserva el libro anterior; su archivo temporal `.xlsx.tmp` se publica mediante reemplazo atómico donde el sistema lo permite. No hace falta tener Excel instalado.

**Recuperación:** la carpeta y el nombre del libro quedan guardados junto al progreso de la cola, independientemente del valor actual del botón. Tras desconexión o cierre, se conservan las hojas anteriores y se reemplaza la hoja de la tarea reanudada, sin añadirla dos veces. CSV inteligente y recuperación de inspecciones siguen actuando sobre la captura antes de generar Excel. Los CSV y `queue-excel.json` son necesarios para reconstruir el libro mientras la cola pueda reanudarse. Al terminar correctamente todas las tareas, el mod elimina las carpetas de esas capturas y `queue-excel.json`: en la carpeta de esa ejecución queda solo el `.xlsx`. La limpieza solo afecta a las carpetas de esta cola; no borra exportaciones anteriores. Si algún archivo está bloqueado o aparece uno ajeno dentro de esas carpetas, el mod avisa y conserva los archivos restantes.

**Cancelar y guardar** guarda las tareas anteriores y el resultado parcial de la tarea actual en el libro antes de descartar la recuperación. En ese caso se conservan los CSV, JSON, resumen y transcript; la limpieza de la cola solo ocurre si todas las tareas terminan correctamente. No crea hojas para tareas que no se ejecutaron. Si falla el CSV o el Excel, la cola no avanza, no ejecuta comandos posteriores y conserva la recuperación y el último libro válido. Si Excel mantiene el archivo abierto y bloquea el reemplazo, ciérralo antes de reintentar. Una celda de más de 32.767 caracteres detiene la exportación Excel para evitar truncarla; los CSV y la recuperación quedan disponibles.

La opción es global para la cola: los perfiles siguen guardando listas de tareas y no cambian este selector. Desactivada, se mantiene la exportación habitual. El mod continúa requiriendo Minecraft 26.3 y Java 25.

### Cancelar y guardar: limpieza de recuperación (1.6.1)

**Cancelar y guardar** detiene la captura y guarda un resultado parcial: CSV, resumen, JSON de sesión y transcript. Los archivos `.recovery-page-N.json` y la referencia `coretrace/recovery.json` se eliminan **después** de que termine la exportación parcial. Si falla el guardado, permanecen para poder recuperar las páginas ya confirmadas. Si se pulsa cancelar mientras se está guardando, se espera a que finalice la operación antes de descartar la recuperación. Esto también se aplica a la tarea activa de la cola; sus comandos posteriores no se ejecutan.

### Exportación progresiva y capturas grandes (1.6.0)

Los CSV se escriben página a página. Con un split de 500, la primera parte aparece al cerrar la página 500, la segunda al cerrar la 1000 y así sucesivamente. Cada parte repite el encabezado y las opciones de Excel. La parte abierta usa `.csv.tmp`; al cerrarla se publica como `.csv` mediante reemplazo atómico cuando el sistema lo permite. Al terminar, también se publica la última parte incompleta. Si sólo existe una parte, el resultado final utiliza el nombre sin sufijo. Sin split, el archivo temporal crece durante la captura y se publica al terminar.

Se retiró el límite global de **100.000 mensajes**. El máximo configurable pasa a **9.999.999 páginas**, conservando 500 por defecto. Al escribir más de 5000 aparece un triángulo rojo que recomienda partes de **500 páginas o menos**; es una recomendación, no una restricción. Sigue vigente la protección contra una respuesta anómala de más de 512 mensajes en una página.

La captura conserva en memoria sólo las últimas **16 páginas** para la vista de resultados; la búsqueda y copia de esa pantalla se limitan a esa vista reciente. Los archivos CSV y JSON conservan toda la captura. La escritura aplica espera entre solicitudes si el disco se retrasa. El JSON final y el resumen se generan leyendo las páginas desde disco; el resumen conserva conteos totales exactos, pero agrupa categorías adicionales después de 4096 categorías distintas por grupo para limitar su memoria.

La recuperación sigue guardándose **por página**, incluso antes de cerrar una parte CSV. No elimines los archivos de recuperación durante una captura: los CSV pueden omitir columnas y no son por sí solos un punto de recuperación. Al recuperar normalmente se validan las páginas guardadas y se reconstruyen las partes desde disco, sin cargar toda la sesión en memoria ni repetir filas de la página interrumpida. Esta preparación puede tardar con capturas grandes. La limpieza automática se realiza sólo tras completar los archivos y abandonar el estado de recuperación. Durante una actualización inteligente pueden quedar partes antiguas hasta que termine correctamente; el resumen final identifica el resultado definitivo.

### Recuperación por inspección accidental (1.6.0)

**Ajustes → Recuperación → Recuperar inspección** está activado por defecto. Se consideran sospechosas las cabeceras inesperadas, los cambios de paginación y las respuestas de CoreProtect después de interactuar con un bloque durante la captura. Como el chat no identifica de forma única cada consulta, la detección es conservadora: una interacción normal seguida de una respuesta también puede provocar una recuperación. No se comparan sólo timestamps ni cinco entradas como si fueran identificadores únicos.

El mod descarta la página en curso, conserva las completas y avisa en rojo. Usa la espera de reanudación de **4000 ms**, editable; vacío vuelve a 4000 y un valor inferior muestra una advertencia. También espera el período configurado de cierre sin datos desde la última respuesta de CoreProtect. Al llegar a cero muestra el 0 durante al menos 250 ms antes de reconstruir la consulta. Si siguen llegando respuestas durante toda la espera máxima por solicitud después del cero, se detiene con un resultado parcial.

Suena `entity.villager.no` al detectar la interrupción, `entity.experience_orb.pickup` en la cuenta regresiva cerca de la mira y `entity.villager.yes` al iniciar la reconstrucción. La reanudación tras desconexión también usa los sonidos de experiencia y aldeano al finalizar la cuenta. La secuencia predeterminada muestra 4, 3, 2, 1, 0; una demora extrema del cliente puede saltarse pasos visuales.

Con CSV inteligente apagado, repite el comando original, ignora la página 1 de reconstrucción y pide la primera página pendiente: si la 185 estaba a medias, vuelve a la 185; si ya estaba guardada, pide la 186. Si cambia el total durante la reconstrucción, se detiene antes de mezclar consultas. Con CSV inteligente encendido, compara el inicio y busca la última unión guardada. La recuperación normal permite **3 intentos consecutivos**, que se restablecen tras 5 páginas estables. La cola permanece en su tarea actual durante la recuperación y no ejecuta sus comandos posteriores hasta que la exportación termine correctamente. Cancelar o desconectarse detiene el conteo. En los ajustes propios de una tarea se pueden cambiar Recuperar inspección y su espera.

### CSV inteligente y limpieza de recuperación (1.6.0)

**Ajustes → Exportación CSV → CSV inteligente** viene activado en configuraciones nuevas. Las preferencias ya guardadas se respetan. También está disponible en los ajustes CSV de cada tarea y se conserva en copias y perfiles. Mientras está activado, `server_timestamp` es obligatorio y no puede desmarcarse; para quitarlo, primero desactiva CSV inteligente. **Desmarcar todo** conserva esa columna. Activar el modo vuelve a incluirla automáticamente. Desactivarlo permite reanudar por número de página, con riesgo de omitir o duplicar registros si los resultados cambiaron.

Al reanudar una captura con este modo, CoreTrace consulta desde la página 1 hasta reconocer las entradas guardadas, conserva las páginas intermedias y busca el final anterior cerca de su posición. Los registros nuevos del inicio se incluyen sin volver a leer cientos de páginas. Los registros antiguos que ya no pertenecen al rango `t:7d` pueden permanecer en las páginas guardadas: esta recuperación conserva un archivo histórico y no reconstruye una instantánea exacta del rango actual. Las tareas ya terminadas de una cola no se repiten.

El mod captura automáticamente la información emergente necesaria para `server_timestamp` y comprueba fechas válidas de más reciente a más antigua, incluso entre páginas. Fechas iguales se permiten y eventos idénticos se conservan como registros distintos. Si falta una fecha válida o cambia la zona horaria, se detiene con un resultado parcial; si aparecen registros más recientes en una página posterior, busca una unión según se explica arriba. Conserva el orden de las entradas sin ordenar el CSV artificialmente. Los números de página de un archivo unido pueden ser lógicos y no coincidir exactamente con la paginación actual del servidor.

La opción se fija al iniciar la captura o cola, igual que los demás ajustes CSV. Cambiarla no altera una ejecución pendiente que ya guardó sus ajustes. Desactivada, se conserva la reanudación normal desde la página interrumpida.

Después de guardar correctamente todos los archivos definitivos y actualizar el estado de recuperación, se borran los `.recovery-page-N.json` y sus temporales de esa carpeta. Se mantienen si aún son necesarios para una recuperación pendiente o si falló la exportación; una cancelación guardada correctamente también los elimina. Si una actualización produce menos partes CSV, también se eliminan las partes antiguas del mismo nombre que ya no pertenecen al resultado. El transcript, resumen y JSON de sesión se conservan.

### Recuperación y autoreconexión (1.5.0)

En **Ajustes → Recuperación**, la reanudación automática viene **activada**, con una espera de **4000 ms** (también al dejar el campo vacío). Puedes cambiarla por cualquier entero no negativo representable de 64 bits. Al volver al mismo servidor, aparece un aviso rojo que explica cómo desactivarla y una cuenta regresiva cerca de la mira, con un sonido cada segundo.

El progreso se guarda por páginas en `coretrace/exports/<carpeta>/.recovery-page-N.json`; `coretrace/recovery.json` identifica la ejecución pendiente. Conserva ambos para recuperar después de cerrar el cliente. Con CSV inteligente desactivado, si se interrumpe en la página 185, el mod repite la consulta original, descarta su respuesta inicial de la página 1, espera su pie confirmado y solicita `/co l 185`. Conserva las páginas anteriores y vuelve a leer la interrumpida, sin agregar duplicados por esa repetición al CSV. Reutiliza la carpeta y los ajustes de exportación originales. El registro de texto conserva las líneas anteriores, incluidas las parciales, y añade un marcador de reanudación.

También recupera la cola: tarea actual, consultas pendientes, posición de los comandos personalizados y espera pendiente entre comandos o tareas. Las opciones de la ejecución permanecen congeladas. Los comandos se reservan en disco antes de enviarlos para no repetir un envío incierto: un cierre abrupto justo entre esa reserva y el envío puede omitir ese comando. No hay confirmación de ejecución del servidor. **Cancelar y guardar** elimina la referencia y los archivos de recuperación después de exportar correctamente, sin borrar los resultados parciales. **Descartar reanudación guardada** elimina la referencia de recuperación sin borrar los archivos exportados.

En **Ajustes → Recuperación → Autoreconexión**, la reconexión está **desactivada por defecto**. La espera inicial entre intentos es **5000 ms** y el máximo es **3 intentos**; campos vacíos usan esos valores. Se admiten esperas enteras no negativas de 64 bits y de 1 a 2147483647 intentos. El aviso verde al iniciar cada tarea viene activado y se puede apagar aquí. Los reintentos se realizan desde la pantalla de desconexión; volver al menú de servidores o cancelar la conexión los detiene. Los ajustes del mod pausan los reintentos. No se reconecta al salir voluntariamente al menú ni abre una conexión por sí solo después de reiniciar el juego. Tras volver a entrar, se aplica la espera independiente de reanudación. Realms no utiliza esta reconexión automática.

La recuperación requiere una consulta original con filtros, no una orden numérica aislada. Un cierre abrupto recupera el último punto escrito en disco; las líneas todavía pendientes pueden necesitar volver a capturarse. La consulta se vuelve a ejecutar: filtros relativos como `t:7d` y cambios en el servidor pueden modificar sus resultados. En la recuperación normal, si falta el pie inicial o cambia el total de páginas, la recuperación se detiene. Un total idéntico no garantiza que los eventos sean los mismos; CoreProtect no ofrece aquí una instantánea inmutable.

### Seleccionar o desmarcar todas las columnas CSV (1.4.2)

En **Ajustes → Exportación CSV → Columnas del CSV**, un botón junto al texto superior alterna entre **Marcar todo** y **Desmarcar todo**. Actúa sobre las columnas de todas las páginas y también funciona en los ajustes CSV propios de una tarea. Al desmarcar todo conserva `server_timestamp`; puedes quitarla manualmente después de desactivar CSV inteligente. Siempre debe quedar al menos una columna antes de volver al menú. Una selección vacía no se guarda.

### Columnas automáticas en Excel (1.4.1)

En **Ajustes → Exportación CSV → Columnas automáticas (Excel)** puedes activar la separación automática de columnas al abrir el CSV en Excel. Está **desactivada por defecto**: apagada conserva exactamente el formato anterior. También está disponible en los ajustes CSV de cada tarea; si no hay ajustes propios, se usa la opción global. Las copias y perfiles conservan los ajustes CSV propios.

Activada añade `sep=,` después del BOM UTF-8 y antes de los encabezados en cada archivo, incluidas las partes divididas. Conserva las filas, columnas elegidas, acentos y comillas. No crea tablas con estilos o filtros: eso requiere un formato como XLSX. Los lectores CSV que no reconocen esta indicación pueden mostrarla como una fila adicional; para ellos deja la opción apagada. El coste adicional es de 7 bytes por archivo, sin trabajo extra por evento; no se espera una diferencia perceptible al exportar. La apertura en Excel no se ha probado en este entorno.

### Copias y perfiles de tareas (1.4.1)

**Copiar** duplica una tarea justo después de la original y abre la copia para editarla. Conserva la consulta, el nombre CSV, los comandos con sus delays y los ajustes propios, sin compartir cambios con la original. **Vaciar tareas** elimina toda la lista actual; los perfiles guardados se conservan.

En **Cola de tareas → Perfiles de tareas**, escribe un nombre y pulsa **Guardar copia** para guardar todas las tareas, en su orden actual. Pulsa un perfil para cargarlo y reemplazar la lista actual; **×** elimina el perfil guardado. Los perfiles persisten entre reinicios, y editar o ejecutar una lista cargada no modifica su perfil guardado. Los nombres deben ser únicos. Los ajustes globales, sonidos de la cola y Task delay siguen siendo los actuales; el perfil guarda las tareas y sus opciones propias.

Task delay usa **3000 ms** en configuraciones nuevas y al dejar el campo vacío. Los delays ya guardados se respetan. Los comandos individuales mantienen su valor predeterminado de **750 ms**.

En **Cola de tareas**, aparece un triángulo junto a Task delay cuando el valor es menor de 3.000 ms. Al pasar el cursor muestra una advertencia; puedes guardar y ejecutar igualmente cualquier delay válido. El campo vacío usa 3.000 ms y no muestra la advertencia. Los valores menores siguen permitidos.

### Página única y varios comandos (1.4.1)

**Ajustes → Captura → Aceptar página única sin pie** viene activado por defecto. Cuando CoreProtect omite el pie de paginación de la primera página, se espera el tiempo de cierre sin datos, se guarda y se continúa la cola. La exportación indica «Página única guardada»; la marca `page_confirmed` sigue siendo falsa porque el servidor no envió un pie. No se aplica a páginas posteriores de una consulta incompleta. Al desactivarlo se conserva «fin inferido, no confirmado» y la cola se detiene después de guardar. Las tareas heredan esta opción de los ajustes globales o pueden cambiarla en sus propios ajustes.

En **Cola de tareas → Editar tarea → Comandos personalizados**, usa **Agregar**, **×** para eliminar y **↑ / ↓** para ordenar. No hay un límite fijo de comandos; el menú usa páginas. Cada comando tiene su delay en ms (vacío: 750), que se espera después del guardado o del comando anterior. Tras el último comando se espera el Task delay antes de la siguiente tarea. Una lista vacía omite los comandos. El comando único de versiones anteriores migra automáticamente con su delay.


### Cola de tareas (1.4.1)

En **F8 → Cola de tareas**, usa **Agregar** para crear tareas, **×** para eliminarlas y **↑ / ↓** para cambiar su orden. La lista se guarda entre reinicios; solo una ejecución interrumpida puede reanudarse automáticamente al conectarte.

Cada tarea tiene un comando completo de consulta CoreProtect, nombre CSV, ajustes CSV opcionales y un comando personalizado opcional. **Ajustes globales** viene activado. Al desactivarlo, **Ajustes de tarea** permite configurar captura y sonidos sin modificar los ajustes globales. Los sonidos de cada tarea comienzan apagados; los sonidos de toda la cola se eligen en **Sonidos de la cola**.

Abrir **Ajustes CSV** sin guardar ni cambiar opciones conserva la herencia global. **Restaurar CSV global** elimina la configuración CSV propia. El nombre siempre procede del campo CSV de la tarea, incluso si está vacío.

La secuencia es: consulta → captura y guardado → delay del comando personalizado → comando opcional → Task delay → siguiente consulta. Ambos delays admiten 0–3600000 ms. Si se dejan vacíos, **Task delay usa 3000 ms** y el **delay de cada comando usa 750 ms**. Sin comando personalizado se omite su delay. No hay espera adicional entre tareas después de la última. Los tiempos y opciones quedan fijados al iniciar la cola.

Ejemplo: `/co l a:command t:3d`, CSV `Simply1`, ajustes globales activados y comando `/simply 2`. Al terminar de guardar, se espera el delay del comando, se envía `/simply 2`, se espera el Task delay y se inicia la siguiente tarea. Para cambios de mundo lentos, aumenta el Task delay: el mod espera el tiempo configurado, no una confirmación de teletransporte del servidor.

Una consulta fallida, timeout, límite de páginas, cancelación, error de escritura o desconexión detiene la cola. Se conserva lo que haya podido guardarse; tras una desconexión, la recuperación puede continuar las tareas pendientes. El comando personalizado se envía como comando del jugador con sus permisos normales.

### Carpetas y limpieza del nombre

Un CSV llamado `julio.csv` se guarda dentro de `coretrace/exports/julio/`. Si existe esa carpeta, se usa `julio (2)`, `julio (3)`, etc., sin sobrescribir resultados. Con el nombre vacío se mantiene el nombre de carpeta anterior basado en fecha e identificador.

**Ajustes → Exportación CSV → Autolimpiar** viene apagado. Si se activa, el nombre se borra solo después de guardar la exportación; también se aplica al nombre de una tarea según sus ajustes CSV. Un nombre nuevo escrito durante una captura se conserva.


CoreTrace acompaña al plugin CoreProtect Community Edition. Detecta una consulta que escribes en el chat, recoge sus resultados, solicita las siguientes páginas y guarda una transcripción y un resumen en tu equipo. El código de este asistente es independiente; no contiene ni sustituye el plugin CoreProtect.

Para instalarlo:

1. Abre una instancia de **Minecraft Java 26.3** con **Fabric Loader 0.19.5 o posterior**. El juego utiliza Java 25; el launcher oficial suele gestionar su runtime. En otros launchers, selecciona Java 25.
2. Instala [Fabric API para 26.3](https://modrinth.com/mod/fabric-api/versions?g=26.3), versión **0.161.0+26.3** o posterior compatible.
3. Copia `coretrace-1.7.11+26.3.jar` dentro de la carpeta `mods` de esa instancia. Si tenías la versión anterior, retira su JAR para dejar una sola versión de CoreTrace. Tu configuración e historial se conservan.
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

[English instructions](README.md).

En **Ajustes → Exportación CSV → Nombre del archivo CSV** puedes escribir, por ejemplo, `consulta` o `consulta.csv`. Se generará `consulta.csv`; si la exportación se divide en cuatro partes, serán `consulta_1.csv`, `consulta_2.csv`, `consulta_3.csv` y `consulta_4.csv`. Deja el campo vacío para el nombre predeterminado. Guarda los ajustes antes de iniciar la captura: el nombre y las columnas quedan fijados para esa sesión.

En **Ajustes → Sonidos**, cada selector muestra todos los eventos de sonido de Minecraft cargados, incluida música y discos. Busca por identificador (por ejemplo `allay`, `music` o `note block`) o por el subtítulo en el idioma del juego. Usa **Probar** para escuchar y **Detener vista previa** para detenerlo; al salir también se detiene. Los paquetes de recursos pueden sustituir estos audios. La reproducción respeta el volumen del juego.

**Última exportación** abre la carpeta de la última sesión que contiene el CSV (o sus partes), o la del libro `.xlsx` si se usó Modo Excel. **Carpeta de archivos** abre la carpeta general de exportaciones. En Windows se usa la apertura nativa de carpetas, sin depender de Java AWT.

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
| Máximo de páginas por captura | 500 | 1–9999999 |
| Guardar textos emergentes | Activado | Activado / desactivado |
| Ocultar mensajes capturados del chat | Desactivado | Activado / desactivado |

Los tiempos y el máximo de páginas se fijan al comenzar cada captura. El cambio de estos ajustes se aplica a la siguiente. Si el servidor limita la frecuencia de comandos, aumenta la pausa, por ejemplo a 2000 o 3000 ms. El exportador conserva una protección por página de 512 mensajes; al superarla guarda una captura parcial. Ya no tiene el límite global de 100000 mensajes.

Los comandos locales disponibles son:

| Comando | Acción |
| --- | --- |
| `/coretrace` | Abre el menú. |
| `/coretrace start a:-block u:jose t:3d` | Inicia una captura desde filtros. |
| `/coretrace start /co l a:-block u:jose t:3d` | Inicia una captura desde el comando completo. |
| `/coretrace pause` | Pausa el envío de nuevas páginas; sigue recogiendo la respuesta pendiente. |
| `/coretrace resume` | Continúa una captura pausada o reintenta una recuperación pendiente. |
| `/coretrace cancel` | Detiene la captura y exporta lo recibido. |
| `/coretrace status` | Muestra el estado. |
| `/coretrace folder` | Abre la carpeta de exportaciones. |

Una consulta numérica como `/coretrace start /co l 3` permite recoger desde una página existente: el informe indicará que faltan las anteriores. La captura automática del chat comienza con una consulta nueva con filtros, no con un cambio de página aislado. Se reconocen `co`, `core`, `coreprotect` y sus variantes con el espacio de nombres `coreprotect:`. No se aceptan `p:`, `page:` ni `#count` en una consulta nueva: usa una consulta normal desde la primera página.

Durante una captura se bloquea el envío manual de otras órdenes `/co` para evitar sustituir la consulta que CoreProtect mantiene en memoria. Evita usar el inspector mientras está capturando. La pausa no libera esa consulta; cancela si necesitas ejecutar otra orden. Tras un final incierto, el asistente espera el tiempo de espera configurado antes de iniciar otra captura, para dejar llegar las respuestas tardías. Los fallos transitorios durante la recuperación admiten hasta cinco intentos; durante una captura normal, un timeout conserva el parcial. Comprueba los registros para reanudar desde otra dirección cuando está habilitado. Tras una desconexión puede recuperar la consulta al volver al mismo servidor. El historial permite iniciarla de nuevo.

El cliente solo ve los mensajes que recibe y necesita los permisos normales de CoreProtect. No obtiene acceso a la base de datos ni amplía permisos. La captura automática solo envía consultas; la cola también puede enviar el comando personalizado que configure el usuario. El único tráfico añadido durante una captura son comandos de consulta y paginación. Los archivos se guardan localmente, sin enviarlos a servicios externos.

La interpretación se basa en el formato público de **CoreProtect CE v24.0**: cabeceras, enlaces de página, resultados y coordenadas. Los mensajes modificados por otros plugins, traducciones personalizadas y otras versiones no tienen compatibilidad garantizada. El pie de página no es una instantánea de base de datos: si cambian los datos o la caché durante la consulta, el cliente no puede garantizar identidad transaccional de todos los eventos. Cuando detecta un cambio de total o una página inesperada, intenta recuperar la inspección; si está desactivado o no puede recuperarse, detiene y marca la captura como parcial. En una página pendiente del CSV, `page_requested` es la página que se pidió; `page_confirmed=false` indica que no se verificó su correspondencia.

**Compatibilidad del servidor:** CoreTrace está hecho para el **cliente 26.3** y requiere que CoreProtect ya funcione en el servidor al que te conectas. Este mod no hace compatible una instalación del plugin que no lo sea. Comprueba la [publicación oficial de CoreProtect](https://www.spigotmc.org/resources/coreprotect-community-edition.8631/) y sus [permisos](https://docs.coreprotect.net/permissions/).

El proyecto incluye código fuente bajo licencia MIT, estas instrucciones y exportaciones de ejemplo con datos simulados, incluido `ejemplo-simulado/all-event-types.csv` con todas las categorías anteriores. El ejemplo combinado reúne varias consultas simuladas y no representa una única consulta del servidor. No incluye Minecraft, Fabric API, Mod Menu ni el plugin CoreProtect.

Para compilar el código necesitas **JDK 25**. La configuración estándar utiliza Loom 1.17.20 y Gradle 9.5.1; el wrapper se incluye:

```text
Windows: gradlew.bat build
Linux/macOS: ./gradlew build
```

El archivo resultante estará en `build/libs/`. Usa el JAR normal; el archivo que termina en `-sources.jar` es código fuente y no se instala como mod.

Para abrir el cliente de desarrollo probado, usa `gradlew.bat runClient -PwithoutModMenu`. Esta opción omite Mod Menu en la ejecución de desarrollo: la versión 20.0.1 fijada para su API opcional no admite Minecraft 26.3 en tiempo de ejecución. F8 funciona sin él.

También se incluye una compilación alternativa con Python 3.10+ y JDK 25:

```text
python build-portable.py
```

Configura `JAVA_HOME` apuntando al JDK 25 o posterior si no está en el PATH. El script descarga las clases oficiales de Minecraft 26.3, Fabric y Mod Menu, verifica sus hashes publicados, compila las fuentes y ejecuta las pruebas. Guarda las dependencias en `.portable/`, que no se distribuye. Minecraft 26.3 utiliza clases sin ofuscación, por lo que este camino genera el JAR sin una etapa de remapeo. El script requiere acceso de red a los repositorios oficiales. `build-portable.bat` permite ejecutarlo en Windows.

La versión 1.7.14 se compiló con JDK 25.0.4 y Gradle (`gradlew.bat test build --offline`) y pasó **231 pruebas automatizadas**, sin fallos ni errores. Las clases del JAR se verificaron como bytecode de Java 25 (versión 69). En la versión 1.7.1 se abrió un libro generado con Microsoft Excel 16.0 y se comprobaron las hojas, coordenadas numéricas, filas, comillas, saltos de línea y conservación de textos que podrían parecer fórmulas. Las pruebas de Excel también cubren el nombre personalizado conservado al reanudar, la eliminación segura de las carpetas de tareas tras la última tarea y comando, la cancelación con resultado parcial, errores de escritura, conservación de backups, columnas diferentes y división de hojas. En 1.7.8, la interfaz de Captura y Smart Delay se revisó en un cliente de desarrollo de Minecraft 26.3 a 640, 854 y 1024 píxeles de ancho, sin controles superpuestos. Se probaron con clics los interruptores de Smart Delay y ETA. Los menús principal y Recovery se habían verificado en la versión anterior. El cálculo del ETA y la detección de kicks se probaron de forma automatizada; falta comprobar el HUD durante una captura y los kicks en un servidor real. **La recuperación contra un servidor real de CoreProtect todavía no se ha probado de principio a fin.** Se mantiene la prueba de captura simulada de 5001 páginas, 105021 registros y 115023 mensajes, exportación antes del final, recuperación tras cierre y recuperación de inspecciones con la cola retenida.

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

En 1.7.9 se comprobaron las posiciones de los cuatro campos de estadísticas con el menú estrecho (296 píxeles de interfaz). La fila se revisó después visualmente en 1.7.10 dentro del cliente de desarrollo. El botón de fábrica y su confirmación se comprobaron a 640 y 1024 píxeles de ancho, con clics para cancelar y aceptar en la instancia de desarrollo.
