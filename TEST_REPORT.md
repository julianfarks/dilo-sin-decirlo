# Informe de pruebas y errores conocidos

## Verificado de verdad
- `python3 tools/cards.py validate` ejecutado: catálogo de 566 cartas válido (≥5 prohibidas por carta, sin duplicados, sin variantes triviales, categorías con ≥20 cartas).

## Verificado en GitHub Actions (run 40797323554, commit posterior a la corrección del CI)
- `testDebugUnitTest` (pruebas unitarias), `assembleDebug` y `assembleRelease` terminaron en éxito.
- APK publicados en Releases → «Última compilación» (debug ≈ 9 MB, release ≈ 1,2 MB).

## NO verificado
- La app **no se ha instalado ni probado en un dispositivo o emulador**: interfaz, sonidos, vibración, voz y rendimiento están sin probar a mano.

## Limitaciones conocidas
- El asistente de voz depende del servicio de reconocimiento del teléfono (puede necesitar Internet, perder o confundir palabras).
- El APK release se firma con la clave de depuración: no apto para Google Play.
- Sonidos y diseño no se han probado en un dispositivo real.
