#!/usr/bin/env python3
"""Herramienta del catálogo de cartas de ¡Dilo sin decirlo!

Uso:
    python3 tools/cards.py build      # genera app/src/main/assets/cards.json desde tools/cards/*.txt
    python3 tools/cards.py validate   # valida las fuentes y el JSON generado (código de salida != 0 si hay errores)
    python3 tools/cards.py all        # build + validate

Formato de las fuentes (tools/cards/*.txt):
    ## id | Nombre | emoji | dificultad_base(1-3)
    PALABRA: prohibida1, prohibida2, ...   [| dificultad opcional 1-3]
"""
import json
import re
import sys
import unicodedata
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC_DIR = ROOT / "tools" / "cards"
OUT_FILE = ROOT / "app" / "src" / "main" / "assets" / "cards.json"

MIN_CARDS = 500
MIN_FORBIDDEN = 5
MAX_FORBIDDEN = 10
MIN_PER_CATEGORY = 20
MAX_SHARED_FORBIDDEN_BETWEEN_CARDS = 3  # dos cartas no pueden compartir 4+ restricciones
MAX_FORBIDDEN_FREQUENCY = 20  # una misma restricción no debería aparecer en demasiadas cartas

REQUIRED_CATEGORIES = [
    "comida", "animales", "objetos", "tecnologia", "videojuegos", "deportes",
    "entretenimiento", "profesiones", "lugares", "transporte", "hogar", "cuerpo",
    "naturaleza", "musica", "ropa", "escuela", "internet", "emociones", "acciones",
    "cultura", "personajes", "dificiles_describir", "graciosas", "dificiles", "latam",
]

# Debe coincidir con CardText.normalize() en la app.
def normalize(text: str) -> str:
    text = unicodedata.normalize("NFD", text.lower())
    text = "".join(c for c in text if unicodedata.category(c) != "Mn")
    text = re.sub(r"[^a-z0-9ñ ]", " ", text.replace("ñ", "n"))
    return re.sub(r"\s+", " ", text).strip()


def singular(word: str) -> str:
    return word[:-1] if len(word) > 3 and word.endswith("s") else word


def parse_sources():
    categories = []
    cards = []
    errors = []
    current = None
    for path in sorted(SRC_DIR.glob("*.txt")):
        for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            line = raw.strip()
            where = f"{path.name}:{lineno}"
            if not line or line.startswith("#") and not line.startswith("##"):
                continue
            if line.startswith("##"):
                parts = [p.strip() for p in line[2:].split("|")]
                if len(parts) != 4 or not parts[3].isdigit() or not 1 <= int(parts[3]) <= 3:
                    errors.append(f"{where}: cabecera de categoría inválida: {line!r}")
                    current = None
                    continue
                current = {"id": parts[0], "name": parts[1], "emoji": parts[2], "difficulty": int(parts[3])}
                if any(c["id"] == current["id"] for c in categories):
                    errors.append(f"{where}: categoría repetida: {current['id']}")
                categories.append(current)
                continue
            if current is None:
                errors.append(f"{where}: carta fuera de una categoría")
                continue
            if ":" not in line:
                errors.append(f"{where}: línea sin ':' -> {line!r}")
                continue
            word, rest = line.split(":", 1)
            difficulty = current["difficulty"]
            if "|" in rest:
                rest, diff = rest.rsplit("|", 1)
                diff = diff.strip()
                if not diff.isdigit() or not 1 <= int(diff) <= 3:
                    errors.append(f"{where}: dificultad inválida {diff!r}")
                else:
                    difficulty = int(diff)
            forbidden = [f.strip() for f in rest.split(",") if f.strip()]
            cards.append({
                "word": word.strip(),
                "forbidden": forbidden,
                "category": current["id"],
                "difficulty": difficulty,
                "_where": where,
            })
    return categories, cards, errors


def validate_cards(categories, cards):
    errors, warnings = [], []
    cat_ids = {c["id"] for c in categories}

    for req in REQUIRED_CATEGORIES:
        if req not in cat_ids:
            errors.append(f"Falta la categoría obligatoria: {req}")

    if len(cards) < MIN_CARDS:
        errors.append(f"Hay {len(cards)} cartas; se requieren al menos {MIN_CARDS}")

    seen_words = {}
    seen_forbidden_sets = []
    freq = Counter()

    for card in cards:
        where = card.get("_where", "?")
        word = card["word"]
        nword = normalize(word)

        if not nword:
            errors.append(f"{where}: palabra secreta vacía")
            continue
        if card["category"] not in cat_ids:
            errors.append(f"{where}: {word}: categoría inválida {card['category']}")
        if card["difficulty"] not in (1, 2, 3):
            errors.append(f"{where}: {word}: dificultad inválida")

        key = singular(nword)
        if key in seen_words:
            errors.append(f"{where}: {word}: palabra duplicada (ya existe en {seen_words[key]})")
        seen_words[key] = where

        forb = card["forbidden"]
        if len(forb) < MIN_FORBIDDEN:
            errors.append(f"{where}: {word}: solo {len(forb)} palabras prohibidas (mínimo {MIN_FORBIDDEN})")
        if len(forb) > MAX_FORBIDDEN:
            errors.append(f"{where}: {word}: {len(forb)} palabras prohibidas (máximo {MAX_FORBIDDEN})")

        normalized_forb = []
        for f in forb:
            nf = normalize(f)
            if not nf:
                errors.append(f"{where}: {word}: palabra prohibida vacía")
                continue
            if nf in normalized_forb:
                errors.append(f"{where}: {word}: prohibida duplicada: {f}")
            normalized_forb.append(nf)

            # Variantes triviales de la palabra secreta
            if nf == nword or singular(nf) == singular(nword):
                errors.append(f"{where}: {word}: la prohibida '{f}' es la propia palabra secreta")
            elif len(nf) >= 4 and (nf in nword or nword in nf):
                errors.append(f"{where}: {word}: la prohibida '{f}' contiene o está contenida en la palabra secreta")
            else:
                prefix = 0
                for a, b in zip(nf.replace(" ", ""), nword.replace(" ", "")):
                    if a != b:
                        break
                    prefix += 1
                if prefix >= 6:
                    errors.append(f"{where}: {word}: la prohibida '{f}' comparte raíz con la palabra secreta")
            freq[nf] += 1

        seen_forbidden_sets.append((word, where, set(normalized_forb)))

    # Cartas que repiten casi las mismas restricciones
    for i in range(len(seen_forbidden_sets)):
        wa, wha, sa = seen_forbidden_sets[i]
        for j in range(i + 1, len(seen_forbidden_sets)):
            wb, whb, sb = seen_forbidden_sets[j]
            shared = sa & sb
            if len(shared) > MAX_SHARED_FORBIDDEN_BETWEEN_CARDS:
                errors.append(f"{wha} / {whb}: {wa} y {wb} comparten {len(shared)} restricciones: {sorted(shared)}")

    for word, count in freq.most_common():
        if count > MAX_FORBIDDEN_FREQUENCY:
            warnings.append(f"La restricción '{word}' aparece en {count} cartas")

    # Tamaño por categoría
    per_cat = Counter(c["category"] for c in cards)
    for cid in cat_ids:
        if per_cat[cid] < MIN_PER_CATEGORY:
            errors.append(f"La categoría {cid} solo tiene {per_cat[cid]} cartas (mínimo {MIN_PER_CATEGORY})")

    return errors, warnings


def build():
    categories, cards, errors = parse_sources()
    if errors:
        for e in errors:
            print("ERROR:", e)
        sys.exit(1)
    data = {
        "version": 1,
        "categories": categories,
        "cards": [
            {"word": c["word"], "forbidden": c["forbidden"], "category": c["category"], "difficulty": c["difficulty"]}
            for c in cards
        ],
    }
    OUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    OUT_FILE.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"Generado {OUT_FILE.relative_to(ROOT)}: {len(cards)} cartas, {len(categories)} categorías")


def validate():
    categories, cards, errors = parse_sources()
    v_errors, warnings = validate_cards(categories, cards)
    errors += v_errors

    # El JSON generado debe existir y coincidir con las fuentes
    if not OUT_FILE.exists():
        errors.append(f"No existe {OUT_FILE.relative_to(ROOT)}; ejecuta 'build'")
    else:
        try:
            data = json.loads(OUT_FILE.read_text(encoding="utf-8"))
            if len(data["cards"]) != len(cards):
                errors.append(f"cards.json tiene {len(data['cards'])} cartas pero las fuentes tienen {len(cards)}; ejecuta 'build'")
            json_cards = data["cards"]
            for jc in json_cards:
                if not jc.get("word") or len(jc.get("forbidden", [])) < MIN_FORBIDDEN:
                    errors.append(f"cards.json: carta incompleta: {jc}")
        except Exception as ex:  # noqa: BLE001
            errors.append(f"cards.json no se puede cargar: {ex}")

    per_cat = Counter(c["category"] for c in cards)
    diff = Counter(c["difficulty"] for c in cards)
    print(f"Cartas: {len(cards)} | Categorías: {len(categories)}")
    print("Por dificultad:", dict(sorted(diff.items())))
    for c in categories:
        print(f"  {c['id']:<22} {per_cat[c['id']]:>3}")
    for w in warnings:
        print("AVISO:", w)
    if errors:
        print(f"\n{len(errors)} ERROR(ES):")
        for e in errors:
            print(" -", e)
        sys.exit(1)
    print("\nOK: catálogo válido")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "all"
    if cmd == "build":
        build()
    elif cmd == "validate":
        validate()
    elif cmd == "all":
        build()
        validate()
    else:
        print(__doc__)
        sys.exit(2)
