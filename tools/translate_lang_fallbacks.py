from __future__ import annotations

import argparse
import json
import re
import ssl
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path


EXEMPT_LOCALES = {
    "en_au.json",
    "en_ca.json",
    "en_gb.json",
    "en_nz.json",
    "en_pt.json",
    "en_ud.json",
    "enp.json",
    "enws.json",
    "jbo_en.json",
    "lol_us.json",
    "ovd.json",
    "pls.json",
    "qid.json",
    "qya_aa.json",
    "rpr.json",
    "tlh_aa.json",
    "tok.json",
}

LOCALE_TO_LANGUAGE = {
    "ast_es.json": "es",
    "az_az.json": "az",
    "ba_ru.json": "ru",
    "bar.json": "de",
    "be_by.json": "be",
    "be_latn.json": "be",
    "br_fr.json": "fr",
    "brb.json": "pt",
    "bs_ba.json": "bs",
    "ca_es.json": "ca",
    "cy_gb.json": "cy",
    "eo_uy.json": "eo",
    "esan.json": "es",
    "eu_es.json": "eu",
    "fa_ir.json": "fa",
    "fil_ph.json": "tl",
    "fo_fo.json": "fo",
    "fra_de.json": "fr",
    "fur_it.json": "it",
    "fy_nl.json": "fy",
    "ga_ie.json": "ga",
    "gd_gb.json": "gd",
    "gl_es.json": "gl",
    "hal_ua.json": "uk",
    "haw_us.json": "haw",
    "hi_in.json": "hi",
    "hn_no.json": "no",
    "hy_am.json": "hy",
    "ig_ng.json": "ig",
    "io_en.json": "eo",
    "is_is.json": "is",
    "isv.json": "sk",
    "ka_ge.json": "ka",
    "kk_kz.json": "kk",
    "kn_in.json": "kn",
    "ksh.json": "de",
    "kw_gb.json": "cy",
    "ky_kg.json": "ky",
    "la_la.json": "la",
    "lb_lu.json": "lb",
    "li_li.json": "nl",
    "lmo.json": "it",
    "lo_la.json": "lo",
    "lt_lt.json": "lt",
    "lv_lv.json": "lv",
    "lzh.json": "zh-TW",
    "mk_mk.json": "mk",
    "mn_mn.json": "mn",
    "ms_my.json": "ms",
    "mt_mt.json": "mt",
    "nah.json": "es",
    "nds_de.json": "de",
    "nn_no.json": "no",
    "no_no.json": "no",
    "oc_fr.json": "ca",
    "qcb_es.json": "es",
    "ry_ua.json": "uk",
    "sah_sah.json": "ru",
    "se_no.json": "no",
    "sl_si.json": "sl",
    "so_so.json": "so",
    "sq_al.json": "sq",
    "sr_cs.json": "sr",
    "sr_sp.json": "sr",
    "sxu.json": "de",
    "szl.json": "pl",
    "ta_in.json": "ta",
    "tl_ph.json": "tl",
    "tt_ru.json": "ru",
    "tzo_mx.json": "es",
    "val_es.json": "ca",
    "vec_it.json": "it",
    "vp_vl.json": "nl",
    "yi_de.json": "yi",
    "yo_ng.json": "yo",
    "zh_hk.json": "zh-TW",
    "zlm_arab.json": "ms",
}

FORCED_TRANSLATIONS = {
    "fil_ph.json": {
        "Game sounds device": "Aparato ng tunog ng laro",
        "Output device": "Aparato ng output",
        "Master volume": "Pangunahing volume",
        "Misc": "Iba pa",
        "Advanced": "Mas abanse",
        "Sound haptics": "Haptics ng tunog",
        "Gameplay haptics": "Haptics ng gameplay",
        "Biome chime": "Hudyat ng biome",
        "Accessibility HUD": "HUD ng accessibility",
        "Timing": "Oras",
        "Audio": "Tunog",
        "Auto": "Awtomatiko",
        "Debug overlay": "Overlay ng debug",
        "Output EQ": "EQ ng output",
        "Smart Volume": "Matalinong volume",
        "Damage burst volume": "Lakas ng bugso ng pinsala",
        "Gameplay haptics cooldown": "Cooldown ng haptics ng gameplay",
        "Spatial": "Espasyal",
        "Spatial panning": "Espasyal na pag-pan",
        "UI": "Interface",
        "Modded": "May mod",
        "Bus": "Bus-kanal",
        "Channel": "Kanal",
        "Burst test": "Pagsubok ng bugso",
        "Sweep (20→120 Hz)": "Pagwalis (20→120 Hz)",
        "Latency pulse": "Pulso ng latency",
        "Spatial debugger": "Debugger na espasyal",
        "Sound Scape (7.1)": "Tunog na tanawin (7.1)",
        "Mode": "Moda",
        "Debug key": "Susi ng debug",
        "Target": "Target na lokasyon",
        "Accel bump": "Bugso ng pagbilis",
    },
    "fy_nl.json": {
        "Bass Shaker Telemetry": "Bass Shaker-telemetry",
        "Misc": "Divers",
        "Melee hit": "Melee-treffer",
        "Melee hit volume": "Folume fan melee-treffer",
        "Sound haptics": "Lûdshaptyk",
        "Gameplay haptics": "Gameplay-haptyk",
        "Flight": "Flecht",
        "Swim": "Swimme",
        "Footsteps": "Fuotstappen",
        "Damage burst": "Skea-útbarsting",
        "Timing": "Tiid",
        "Audio": "Lûd",
        "Auto": "Automatysk",
        "Latency test: OFF": "Latinsjetest: UT",
        "Latency test: ON": "Latinsjetest: OAN",
        "Debug overlay": "Debug-overlay",
        "Debug overlay: ON": "Debug-overlay: OAN",
        "Smart Volume": "Slim folume",
        "Smart Volume: OFF": "Slim folume: UT",
        "Smart Volume: ON": "Slim folume: OAN",
        "Test": "Proef",
        "Accel bump volume": "Folume fan fersnellingsstjit",
        "Sound haptics cooldown": "Ofkuolling lûdshaptyk",
        "Gameplay haptics cooldown": "Ofkuolling gameplay-haptyk",
        "Sound Scape": "Lûdslânskip",
        "UI": "Brûkersflak",
        "Impact": "Ympakt",
        "Modded": "Modde",
        "Bus": "Buskanaal",
        "Burst test": "Útbarstingstest",
        "Sweep (20→120 Hz)": "Frekwinsjesweep (20→120 Hz)",
        "Reload": "Opnij lade",
        "Add": "Tafoegje",
        "Mode": "Modus",
        "Status": "Steat",
        "Routing": "Rûtering",
        "Sound Scape: OFF": "Lûdslânskip: UT",
        "Sound Scape: ON": "Lûdslânskip: OAN",
        "Gameplay": "Spul",
        "Mounted / Flight": "Beriden / Flecht",
        "Mining swing": "Mynslach",
        "Warden heartbeat": "Warden-hertslach",
        "LOW HEALTH": "LEGE SÛNENS",
    },
    "la_la.json": {
        "Bass Shaker Telemetry": "Telemetria Bass Shaker",
        "Misc": "Varia",
        "< Prev": "< Prior",
        "Next >": "Proximum >",
        "Footsteps": "Vestigia",
        "Timing": "Tempus",
        "Audio": "Sonus",
        "Auto": "Automaticum",
        "Latency test: OFF": "Probatio morae: OFF",
        "Latency test: ON": "Probatio morae: ON",
        "Output EQ": "EQ exitus",
        "Output EQ: OFF": "EQ exitus: OFF",
        "EQ frequency": "Frequentia EQ",
        "Calibration": "Calibratio",
        "Test": "Experimentum",
        "Done": "Factum",
        "Cancel": "Cancella",
        "Target output level used by Smart Volume.": "Gradus exitus destinatus a Volumine Callido adhibitus.",
        "UI": "IU",
        "Environmental": "Ambiens",
        "Impact": "Impetus",
        "Modded": "Modificatum",
        "Bus": "Canalis",
        "Channel": "Canalis",
        "Test tone (30 Hz)": "Tonus probationis (30 Hz)",
        "Test tone (60 Hz)": "Tonus probationis (60 Hz)",
        "Reload": "Recarica",
        "Add": "Adde",
        "Set output": "Pone exitum",
        "Status": "Conditio",
        "Routing": "Directio",
        "Edit overrides": "Edere substitutiones",
        "Edit": "Edere",
        "Edit group": "Edere globum",
        "Add override": "Adde substitutionem",
        "Edit override": "Edere substitutionem",
        "Target": "Destinatio",
    },
    "tl_ph.json": {
        "Game sounds device": "Aparato ng tunog ng laro",
        "Output device": "Aparato ng output",
        "Master volume": "Pangunahing volume",
        "Misc": "Iba pa",
        "Advanced": "Mas abanse",
        "Sound haptics": "Haptics ng tunog",
        "Gameplay haptics": "Haptics ng gameplay",
        "Biome chime": "Hudyat ng biome",
        "Accessibility HUD": "HUD ng accessibility",
        "Timing": "Oras",
        "Audio": "Tunog",
        "Auto": "Awtomatiko",
        "Debug overlay": "Overlay ng debug",
        "Output EQ": "EQ ng output",
        "Smart Volume": "Matalinong volume",
        "Damage burst volume": "Lakas ng bugso ng pinsala",
        "Gameplay haptics cooldown": "Cooldown ng haptics ng gameplay",
        "Spatial": "Espasyal",
        "Spatial panning": "Espasyal na pag-pan",
        "UI": "Interface",
        "Modded": "May mod",
        "Bus": "Bus-kanal",
        "Channel": "Kanal",
        "Burst test": "Pagsubok ng bugso",
        "Sweep (20→120 Hz)": "Pagwalis (20→120 Hz)",
        "Latency pulse": "Pulso ng latency",
        "Spatial debugger": "Debugger na espasyal",
        "Sound Scape (7.1)": "Tunog na tanawin (7.1)",
        "Mode": "Moda",
        "Debug key": "Susi ng debug",
        "Target": "Target na lokasyon",
        "Accel bump": "Bugso ng pagbilis",
    },
}

PLACEHOLDER_PATTERN = re.compile(r"%[0-9]*\$?[sd]")
SPLIT_TOKEN = "<<<BST_SPLIT>>>"


def load_json(path: Path) -> dict[str, str]:
    with path.open("r", encoding="utf-8-sig") as handle:
        return json.load(handle)


def write_json(path: Path, data: dict[str, str]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        json.dump(data, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def same_percent(source: dict[str, str], target: dict[str, str], ordered_keys: list[str]) -> float:
    same = sum(1 for key in ordered_keys if target.get(key) == source[key])
    return round((same / len(ordered_keys)) * 100, 1)


def chunked(values: list[str], size: int) -> list[list[str]]:
    return [values[index:index + size] for index in range(0, len(values), size)]


def normalize_translation(source_text: str, translated_text: str) -> str:
    source_placeholders = PLACEHOLDER_PATTERN.findall(source_text)
    translated_placeholders = PLACEHOLDER_PATTERN.findall(translated_text)
    if source_placeholders == translated_placeholders:
        return translated_text
    repaired = PLACEHOLDER_PATTERN.sub("{}", translated_text)
    if repaired.count("{}") == len(source_placeholders):
        for placeholder in source_placeholders:
            repaired = repaired.replace("{}", placeholder, 1)
        return repaired
    if source_placeholders:
        return f"{translated_text} {' '.join(source_placeholders)}"
    return translated_text


def request_translation(target_language: str, text: str) -> list:
    query = urllib.parse.urlencode(
        {
            "client": "gtx",
            "sl": "en",
            "tl": target_language,
            "dt": "t",
            "q": text,
        }
    )
    url = f"https://translate.googleapis.com/translate_a/single?{query}"
    context = ssl._create_unverified_context()
    with urllib.request.urlopen(url, context=context, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def translate_batch(target_language: str, texts: list[str]) -> list[str]:
    if not texts:
        return []
    joined_text = f"\n{SPLIT_TOKEN}\n".join(texts)
    for attempt in range(3):
        try:
            payload = request_translation(target_language, joined_text)
            translated = "".join(part[0] for part in payload[0])
            translated = translated.split(f"\n{SPLIT_TOKEN}\n")
            if len(translated) != len(texts):
                return [translate_batch(target_language, [text])[0] for text in texts]
            return [normalize_translation(source, result) for source, result in zip(texts, translated)]
        except Exception:
            if attempt == 2:
                raise
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError("Unreachable retry state")


def translate_locale(en_data: dict[str, str], locale_path: Path, threshold: float, chunk_size: int) -> bool:
    locale_name = locale_path.name
    if locale_name in EXEMPT_LOCALES:
        return False

    ordered_keys = list(en_data.keys())
    locale_data = load_json(locale_path)
    if same_percent(en_data, locale_data, ordered_keys) < threshold:
        return False

    language = LOCALE_TO_LANGUAGE.get(locale_name)
    if not language:
        raise KeyError(f"No translation target configured for {locale_name}")

    keys_to_translate = [
        key for key in ordered_keys
        if locale_data.get(key, en_data[key]) == en_data[key]
    ]
    if not keys_to_translate:
        return False

    forced_translations = FORCED_TRANSLATIONS.get(locale_name, {})
    translated_values: dict[str, str] = {}
    keys_for_service: list[str] = []
    for key in keys_to_translate:
        source_text = en_data[key]
        if source_text in forced_translations:
            translated_values[key] = forced_translations[source_text]
        else:
            keys_for_service.append(key)

    for chunk in chunked(keys_for_service, chunk_size):
        source_texts = [en_data[key] for key in chunk]
        translated_texts = translate_batch(language, source_texts)
        translated_values.update(dict(zip(chunk, translated_texts)))
        time.sleep(0.2)

    updated = {key: translated_values.get(key, locale_data.get(key, en_data[key])) for key in ordered_keys}
    write_json(locale_path, updated)
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description="Translate still-English locale entries from en_us.json")
    parser.add_argument("--lang-dir", default="src/main/resources/assets/bassshakertelemetry/lang")
    parser.add_argument("--threshold", type=float, default=15.0)
    parser.add_argument("--chunk-size", type=int, default=20)
    parser.add_argument("--only", nargs="*", default=[])
    args = parser.parse_args()

    lang_dir = Path(args.lang_dir)
    en_path = lang_dir / "en_us.json"
    en_data = load_json(en_path)
    only = set(args.only)

    updated_files: list[str] = []
    for locale_path in sorted(lang_dir.glob("*.json")):
        if locale_path.name == "en_us.json":
            continue
        if only and locale_path.name not in only:
            continue
        changed = translate_locale(en_data, locale_path, args.threshold, args.chunk_size)
        if changed:
            updated_files.append(locale_path.name)
            print(f"Translated fallback entries in {locale_path.name}")

    if not updated_files:
        print("No locale files required translation updates.")
        return 0

    print(f"Updated {len(updated_files)} locale files.")
    return 0


if __name__ == "__main__":
    sys.exit(main())