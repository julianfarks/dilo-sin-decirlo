# Informe de pruebas y errores conocidos

## Verificado de verdad
- `python3 tools/cards.py validate` ejecutado: catálogo de 566 cartas válido (≥5 prohibidas por carta, sin duplicados, sin variantes triviales, categorías con ≥20 cartas).

## Escrito pero NO ejecutado todavía
- Todo el código Kotlin y las pruebas unitarias (`app/src/test`) se escribieron en un entorno sin Android SDK ni acceso a dependencias de Gradle. **No se ha compilado ni ejecutado nada de Kotlin localmente.**
- Los APK debug/release solo existen si el flujo de GitHub Actions termina en verde; si falla, el registro queda en la rama `ci-logs`.

## Limitaciones conocidas
- El asistente de voz depende del servicio de reconocimiento del teléfono (puede necesitar Internet, perder o confundir palabras).
- El APK release se firma con la clave de depuración: no apto para Google Play.
- Sonidos y diseño no se han probado en un dispositivo real.
