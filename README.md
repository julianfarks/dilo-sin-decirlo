# ¡DILO SIN DECIRLO!

Juego de fiesta para Android (estilo Tabú): describe la palabra secreta **sin decir** ninguna de las palabras prohibidas. Un solo teléfono, en español, **100 % sin conexión**, sin anuncios ni cuentas.

## Características

- **566 cartas** originales en 25 categorías (comida, animales, videojuegos, deportes, internet, cultura LATAM, difíciles de describir…). Cada carta tiene 5–10 palabras prohibidas.
- **Modos:** por equipos, todos contra todos, contrarreloj, supervivencia (con récord), categorías, aleatorio, difícil y fiesta (reglas locas por turno).
- **Configuración completa:** equipos y nombres, jugadores, duración del turno, puntos objetivo, rondas, cartas por turno, categorías, dificultad, penalizaciones, pasar permitido, presets y partida rápida.
- **Partida:** palabra grande, lista de prohibidas, temporizador, marcador, botones ACERTADA / PASAR / PALABRA PROHIBIDA / PAUSAR, protección contra doble toque y resumen de cada turno.
- **Asistente de voz opcional** (Ajustes): usa el reconocimiento de voz del teléfono; pide confirmación antes de penalizar. *No es perfecto*: puede fallar o necesitar Internet según el dispositivo. El botón manual siempre manda.
- **Rejugabilidad:** sin repetición de cartas, estadísticas, récords, historial, logros, desafío diario, cartas favoritas, ocultar categorías, semilla para partidas reproducibles.
- **Cartas propias:** crear, editar, borrar, importar y exportar (JSON), con validación.
- Sonidos originales sintetizados, vibración y animaciones activables/desactivables.

## Estructura

```
app/src/main/java/app/dilosindecirlo/
  data/      cartas, validador, almacenamiento
  engine/    motor de juego puro (determinista), configuración, mazo, detector de prohibidas
  stats/     estadísticas, logros, desafíos
  feedback/  sonido y vibración
  voice/     asistente de voz
  ui/        tema, componentes y pantallas (Jetpack Compose)
app/src/main/assets/cards.json   catálogo generado
app/src/test/                    pruebas unitarias
tools/cards/*.txt                fuentes editables de las cartas
tools/cards.py                   genera y valida el catálogo
.github/workflows/build.yml      CI: validación, pruebas y APK
```

## Compilar

Requisitos: JDK 17 y Android SDK (API 35).

```bash
python3 tools/cards.py all            # regenera y valida cards.json
./gradlew testDebugUnitTest           # pruebas
./gradlew assembleDebug               # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease             # app/build/outputs/apk/release/app-release.apk
```

También puedes abrir la carpeta en Android Studio. En GitHub, cada push a `main` compila y publica los APK en **Releases → «Última compilación»** (si el flujo termina bien).

## Firma del APK release

Con las variables `DSD_KEYSTORE_PATH`, `DSD_KEYSTORE_PASSWORD`, `DSD_KEY_ALIAS` y `DSD_KEY_PASSWORD` se firma con tu clave. Si no están, se usa `app/debug.keystore` (clave pública de depuración): el APK se puede instalar y probar, **pero no sirve para Google Play**.

## Añadir o cambiar cartas

Edita los `.txt` de `tools/cards/` (formato: `## id | Nombre | emoji | dificultad` y líneas `PALABRA: p1, p2, p3, p4, p5`) y ejecuta `python3 tools/cards.py all`. El validador rechaza duplicados, menos de 5 prohibidas, prohibidas parecidas a la palabra y cartas casi idénticas entre sí.

## Estado de verificación

Ver [TEST_REPORT.md](TEST_REPORT.md).
