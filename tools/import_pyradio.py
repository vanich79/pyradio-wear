# -*- coding: utf-8 -*-
"""stations.csv (PyRadio) -> stations.json (ассет приложения).

Чинит дефекты исходника и размечает жанр. Запускается один раз при импорте
плейлиста; в рантайме приложение читает уже готовый JSON.
"""
import csv, json, re
from urllib.parse import urlparse

SRC = r"D:/TicWach_ATLAS/Pyradio/stations.csv"
DST = r"D:/TicWach_ATLAS/Pyradio/app/src/main/assets/stations.json"

# Жанр выведен из имени станции вручную: в исходнике его нет, а на часах
# он единственный способ не листать сорок четыре строки подряд.
GENRE = {
    "Alternative (BAGeL Radio)": "Alternative",
    "Alternative (The Alternative Project)": "Alternative",
    "American Roots (Boot Liquor - SomaFM)": "Roots",
    "Celtic (ThistleRadio - SomaFM)": "Celtic",
    "Chillout (Groove Salad - SomaFM)": "Chillout",
    "Groove Salad Classic (Early 2000s Ambient)": "Ambient",
    "n5MD Radio (Ambient and Experimental)": "Ambient",
    "Vaporwaves [SomaFM]": "Electronic",
    "Commodore 64 Remixes (Slay Radio)": "Chiptune",
    "Covers (SomaFM)": "Covers",
    "Downtempo (Secret Agent - SomaFM)": "Downtempo",
    "Dub Step (Dub Step Beyond - SomaFM)": "Dubstep",
    "Electronic/Dance (Electronic Culture)": "Electronic",
    "Folk (Folk Forward - SomaFM)": "Folk",
    "Hip Hop (Hot 97 NYC)": "Hip Hop",
    "Hip Hop (Power 1051 NYC)": "Hip Hop",
    "House (Beat Blender - SomaFM)": "House",
    "Indie Pop (Indie Pop Rocks! - SomaFM)": "Indie",
    "Intelligent dance music (Cliq Hop - SomaFM)": "IDM",
    "Jazz (Sonic Universe - SomaFM)": "Jazz",
    "Lounge (Illinois Street Lounge - SomaFM)": "Lounge",
    "The Trip: [SomaFM]": "Psychedelic",
    "Pop (PopTron! - SomaFM)": "Pop",
    "Pop/Rock/Urban (Frequence 3 - Paris)": "Pop",
    "Progressive (Tags Trance Trip - SomaFM)": "Trance",
    "Public Radio (NPR National Public Radio Stream)": "Talk",
    "Reggae Dancehall (Ragga Kings)": "Reggae",
    "Heavyweight Reggae": "Reggae",
    "Rock (Digitalis - SomaFM)": "Rock",
    "Vox Noctem: Rock-Goth": "Goth",
    "Beyond Metal (Progressive - Symphonic)": "Metal",
    "Metal Detector": "Metal",
    "DanceUK": "Dance",
    "JazzGroove": "Jazz",
    "Radio Paradise - Main Mix": "Eclectic",
    "Radio Paradise - Mellow Mix": "Eclectic",
    "Radio Paradise - Rock Mix": "Eclectic",
    "Radio Paradise - Eclectic Mix": "Eclectic",
    "Echoes of Bluemars": "Ambient",
    "Echoes of Bluemars - Cryosleep": "Ambient",
    "Echoes of Bluemars - Voices from Within": "Ambient",
    "Synphaera Radio (Space Music)": "Ambient",
    "Radio Levač (Serbian Folk & Country)": "Folk",
    "Radio 35 (Serbian and English Pop, Folk, Country & Hits)": "Pop",
}


def clean_url(u: str) -> str:
    u = u.strip()
    # L36: экранирование из shell уехало в файл — `\?sid\=1` вместо `?sid=1`.
    u = u.replace(r"\?", "?").replace(r"\=", "=").replace(r"\&", "&")
    return u


def clean_name(n: str) -> str:
    return re.sub(r"\s+", " ", n).strip()


def kind_of(u: str) -> str:
    p = urlparse(u).path.lower()
    if p.endswith(".m3u8"):
        return "HLS"          # ExoPlayer разбирает сам
    if p.endswith((".pls", ".m3u")) or p.endswith(".php"):
        return "PLAYLIST"     # нужен наш резолвер
    if p.endswith((".mp3", ".aac", ".ogg")):
        return "DIRECT"
    return "UNKNOWN"          # чаще всего ICY/Shoutcast без расширения


def slug(name: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
    return s or "station"


out, seen = [], set()
with open(SRC, encoding="utf-8", newline="") as f:
    for raw in f:
        line = raw.rstrip("\r\n")
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        rec = next(csv.reader([line]))
        if len(rec) < 2:
            continue
        name, url = clean_name(rec[0]), clean_url(rec[1])
        if not name or not url:
            continue
        sid = slug(name)
        assert sid not in seen, f"дубль идентификатора: {sid}"
        seen.add(sid)
        genre = GENRE.get(name)
        assert genre, f"жанр не размечен: {name!r}"
        out.append({
            "id": sid,
            "name": name,
            "url": url,
            "genre": genre,
            "kind": kind_of(url),
        })

import os
os.makedirs(os.path.dirname(DST), exist_ok=True)
with open(DST, "w", encoding="utf-8", newline="\n") as f:
    json.dump({"version": 1, "source": "PyRadio stations.csv", "stations": out},
              f, ensure_ascii=False, indent=2)
    f.write("\n")

from collections import Counter
print(f"станций: {len(out)}")
print("по типу:  ", dict(Counter(s["kind"] for s in out)))
print("жанров:   ", len(set(s['genre'] for s in out)))
print("cleartext:", sum(1 for s in out if s["url"].startswith("http://")))
