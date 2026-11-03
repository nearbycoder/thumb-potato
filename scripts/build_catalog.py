#!/usr/bin/env python3
"""Builds the key-less game catalog that the apps read for platform pages.

There's no free, key-less API that lists console games, so this script asks
Wikidata for every video game on each platform Thumb Potato knows about, adds
box art and a short blurb from English Wikipedia, and writes one compact JSON
file per platform to docs/catalog/v1/, served by GitHub Pages.

Each file is a JSON array of game objects (see SPEC.md, "Catalog format"),
sorted by popularity (Wikipedia language editions), newest releases and
upcoming games always included.

Run: python3 scripts/build_catalog.py  (stdlib only; ~5 minutes)
"""

import datetime as dt
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

UA = "ThumbPotato/1.0 (https://github.com/nearbycoder/thumb-potato)"
SPARQL = "https://query.wikidata.org/sparql"
STEAM_ITEMS = "https://api.steampowered.com/IStoreBrowseService/GetItems/v1/"
STEAM_ASSETS = "https://shared.akamai.steamstatic.com/store_item_assets/"
STEAM_BATCH = 50
WIKI = "https://en.wikipedia.org/w/api.php"
OUT = os.path.join(os.path.dirname(__file__), "..", "docs", "catalog", "v1")
PER_PLATFORM = 1200
RECENT_DAYS = 548  # releases this recent are always kept, whatever their popularity
SUMMARY_CHARS = 320
WIKI_BATCH = 20  # prop=extracts returns at most 20 intros per request
WORKERS = 2

# Mirrors Platform.all in ThumbPotato/Models/Platform.swift and the Android
# Platform list: id, display name, Wikidata items. Minimum Wikipedia editions
# keeps the very large PC and mobile queries inside Wikidata's 60 s limit.
PLATFORMS = [
    ("switch2", "Nintendo Switch 2", ["Q122761124"], 1),
    ("switch", "Nintendo Switch", ["Q19610114"], 1),
    ("ps5", "PlayStation 5", ["Q63184502"], 1),
    ("ps4", "PlayStation 4", ["Q5014725"], 1),
    ("xbox-series", "Xbox Series X|S", ["Q98973368"], 1),
    ("xbox-one", "Xbox One", ["Q13361286"], 1),
    ("pc", "PC", ["Q1406"], 5),
    ("mac", "Mac", ["Q14116"], 3),
    ("mobile", "iOS & Android", ["Q48493", "Q94"], 3),
]

# Wikidata genre labels → the fixed genre list the apps filter on (SPEC.md).
# First matching fragment wins per label; a game keeps every genre it maps to.
GENRES = [
    ("Roguelike", ["roguelike", "roguelite"]),
    ("Metroidvania", ["metroidvania"]),
    ("Platformer", ["platform"]),
    ("Shooter", ["shooter", "shoot 'em up", "run and gun", "battle royale"]),
    ("Fighting", ["fighting", "beat 'em up", "hack and slash", "brawler"]),
    ("RPG", ["role-playing", "dungeon crawl", "otome"]),
    ("Strategy", ["strategy", "tactic", "4x", "tower defense", "moba", "auto battler", "card game", "deck-building"]),
    ("Simulation", ["simulat", "city-building", "management", "life sim", "dating sim", "farming", "god game"]),
    ("Sports", ["sport", "football", "soccer", "basketball", "golf", "tennis", "baseball", "hockey", "skate", "wrestling", "boxing"]),
    ("Racing", ["racing", "kart", "driving"]),
    ("Horror", ["horror"]),
    ("Puzzle", ["puzzle", "match-3", "tile-matching", "logic"]),
    ("Party", ["party", "trivia", "minigame"]),
    ("Music", ["rhythm", "music"]),
    ("Visual Novel", ["visual novel", "interactive fiction", "interactive film"]),
    ("Survival", ["survival game", "survival video game", "crafting"]),
    ("Sandbox", ["sandbox", "open world", "open-world"]),
    ("Adventure", ["adventure", "walking simulator", "point-and-click", "stealth"]),
    ("Action", ["action"]),
]

QUERY = """
SELECT ?game ?label ?article ?links (GROUP_CONCAT(DISTINCT CONCAT(STR(?pd), "@", STR(?pp)); separator="|") AS ?pdates)
  (GROUP_CONCAT(DISTINCT CONCAT(STR(?ad), "@", STR(?ap)); separator="|") AS ?adates) (SAMPLE(?steam) AS ?steamId)
  (GROUP_CONCAT(DISTINCT ?genreLabel; separator="|") AS ?genres)
  (GROUP_CONCAT(DISTINCT ?devLabel; separator="|") AS ?devs) (GROUP_CONCAT(DISTINCT ?pubLabel; separator="|") AS ?pubs) (SAMPLE(?mc) AS ?meta)
WHERE {
  ?game wdt:P31 wd:Q7889; wdt:P400 wd:%(platform)s; wikibase:sitelinks ?links.
  %(filter)s
  ?article schema:about ?game; schema:isPartOf <https://en.wikipedia.org/>.
  ?game rdfs:label ?label FILTER(LANG(?label) = "en")
  OPTIONAL { ?game p:P577 ?st. ?st psv:P577 [wikibase:timeValue ?pd; wikibase:timePrecision ?pp]; pq:P400 wd:%(platform)s; wikibase:rank ?sr. FILTER(?sr != wikibase:DeprecatedRank) }
  OPTIONAL { ?game p:P577 ?ast. ?ast psv:P577 [wikibase:timeValue ?ad; wikibase:timePrecision ?ap]; wikibase:rank ?ar. FILTER(?ar != wikibase:DeprecatedRank) }
  OPTIONAL { ?game wdt:P1733 ?steam. }
  OPTIONAL { ?game wdt:P136 ?genre. ?genre rdfs:label ?genreLabel FILTER(LANG(?genreLabel) = "en") }
  OPTIONAL { ?game wdt:P178 ?d. ?d rdfs:label ?devLabel FILTER(LANG(?devLabel) = "en") }
  OPTIONAL { ?game wdt:P123 ?p. ?p rdfs:label ?pubLabel FILTER(LANG(?pubLabel) = "en") }
  OPTIONAL { ?game p:P444 ?rs. ?rs ps:P444 ?mc; pq:P447 wd:Q150248. }
}
GROUP BY ?game ?label ?article ?links
"""


def fetch(url, data=None, headers=None, attempts=8):
    for attempt in range(attempts):
        try:
            req = urllib.request.Request(url, data=data, headers={"User-Agent": UA, **(headers or {})})
            with urllib.request.urlopen(req, timeout=90) as resp:
                return json.load(resp)
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as err:
            retry_after = getattr(err, "headers", None) and err.headers.get("Retry-After")
            wait = int(retry_after) if retry_after and retry_after.isdigit() else 5 * (attempt + 1)
            print(f"  retry in {wait}s: {err}", file=sys.stderr)
            time.sleep(wait)
    raise RuntimeError(f"giving up on {url[:120]}")


def sparql(query):
    body = urllib.parse.urlencode({"query": query}).encode()
    data = fetch(SPARQL, data=body, headers={
        "Accept": "application/sparql-results+json",
        "Content-Type": "application/x-www-form-urlencoded",
    })
    return [{k: v["value"] for k, v in row.items()} for row in data["results"]["bindings"]]


def normalize_genres(labels):
    out = []
    for label in labels:
        lower = label.lower()
        for genre, fragments in GENRES:
            if any(f in lower for f in fragments):
                if genre not in out:
                    out.append(genre)
                break
    order = [g for g, _ in GENRES]
    return sorted(out, key=order.index)


PRECISION = {"9": "year", "10": "month", "11": "day"}


def earliest_date(values):
    """Earliest of "<xsd:dateTime>@<precision>" values → (date, "year" | "month" | "day").

    Wikidata stores a year-only date as Jan 1, so the precision travels with it;
    precisions coarser than a year (decades) are ignored.
    """
    dates = []
    for value in filter(None, (values or "").split("|")):
        stamp, _, precision = value.partition("@")
        if stamp.startswith("-") or precision not in PRECISION:
            continue
        try:
            # Year/month precision may come as "2027-00-00"; make it parseable.
            date = dt.date.fromisoformat(stamp[:10].replace("-00", "-01"))
        except ValueError:
            continue
        dates.append((date, precision))

    def covers(coarse, other):
        # A "2023" or "May 2023" date is redundant next to an exact date inside it.
        if coarse[1] == "9":
            return other[0].year == coarse[0].year and other[1] > "9"
        if coarse[1] == "10":
            return other[0].year == coarse[0].year and other[0].month == coarse[0].month and other[1] == "11"
        return False

    dates = [d for d in dates if not any(covers(d, o) for o in dates)]
    if not dates:
        return None, None
    best = min(dates, key=lambda d: (d[0], -int(d[1])))
    return best[0], PRECISION[best[1]]


def names(values, limit=2):
    """Up to `limit` names from a "|"-joined list, shortest first (usually the main studio)."""
    items = sorted({v for v in (values or "").split("|") if v}, key=lambda v: (len(v), v))
    return ", ".join(items[:limit]) or None


def parse_metacritic(value):
    m = re.match(r"\s*(\d{1,3})\s*(?:/\s*100)?\s*$", value or "")
    return int(m.group(1)) if m and int(m.group(1)) <= 100 else None


def wiki_title(article_url):
    return urllib.parse.unquote(article_url.rsplit("/wiki/", 1)[1])


def platform_games(pid, wikidata_ids, min_links):
    # One query per Wikidata item: a VALUES list makes the optimizer time out.
    rows = []
    for qid in wikidata_ids:
        filt = f"FILTER(?links >= {min_links})" if min_links > 1 else ""
        rows += sparql(QUERY % {"platform": qid, "filter": filt})
    games = {}
    for row in rows:
        qid = row["game"].rsplit("/", 1)[1]
        date, precision = earliest_date(row.get("pdates"))
        if not date:
            date, precision = earliest_date(row.get("adates"))
        game = games.get(qid)
        if game and game["_date"] and date and game["_date"] <= date:
            continue
        steam = row.get("steamId")
        games[qid] = {
            "id": qid,
            "name": row["label"],
            "_date": date,
            "_precision": precision,
            "genres": normalize_genres([g for g in row.get("genres", "").split("|") if g]),
            "dev": names(row.get("devs")),
            "pub": names(row.get("pubs")),
            "wiki": wiki_title(row["article"]),
            "steam": int(steam) if steam and steam.isdigit() else None,
            "metacritic": parse_metacritic(row.get("meta")),
            "pop": int(row["links"]),
        }
    return list(games.values())


def wiki_details(titles):
    """Cover image and a short intro for each Wikipedia title (batched)."""
    params = {
        "action": "query", "format": "json", "formatversion": "2", "redirects": "1",
        "prop": "pageimages|extracts", "piprop": "thumbnail", "pithumbsize": "600", "pilicense": "any",
        "exintro": "1", "explaintext": "1", "exsentences": "2", "exlimit": "max",
        "titles": "|".join(titles),
    }
    data = fetch(WIKI + "?" + urllib.parse.urlencode(params))
    query = data.get("query", {})
    alias = {}
    for entry in query.get("normalized", []) + query.get("redirects", []):
        alias[entry["to"]] = alias.get(entry["from"], entry["from"])
    out = {}
    # A redirect into a section or an unrelated title (e.g. Witchbrook → Chucklefish)
    # means the game has no article of its own; the target's intro and image are about something else.
    def words(title):
        return {w for w in re.findall(r"[a-z0-9]+", title.lower()) if len(w) > 2 and w != "video"}

    for entry in query.get("redirects", []):
        if entry.get("tofragment") or not words(entry["from"]) & words(entry["to"]):
            out[alias.get(entry["from"], entry["from"]).replace(" ", "_")] = {"section": True}
    for page in query.get("pages", []):
        original = alias.get(page["title"], page["title"])
        # Map back through both normalization and redirects to the requested title.
        original = alias.get(original, original)
        thumb = page.get("thumbnail", {})
        cover = thumb.get("source")
        # Box art is portrait or square; wide images and SVGs are logos or screenshots.
        is_logo = ".svg" in (cover or "").lower() or thumb.get("width", 0) > 1.15 * thumb.get("height", 1)
        cover = cover.split("?", 1)[0] if cover and not is_logo else None
        summary = re.sub(r"\s+", " ", page.get("extract", "")).strip()
        if len(summary) > SUMMARY_CHARS:
            summary = summary[:SUMMARY_CHARS].rsplit(" ", 1)[0] + "…"
        out.setdefault(original.replace(" ", "_"), {"cover": cover, "summary": summary})
    return out


def steam_art(appids):
    """Portrait library art, hero backdrop and Steam Deck rating per Steam app (batched)."""
    request = {
        "ids": [{"appid": a} for a in appids],
        "context": {"language": "english", "country_code": "US"},
        "data_request": {"include_assets": True, "include_platforms": True},
    }
    data = fetch(STEAM_ITEMS + "?" + urllib.parse.urlencode({"input_json": json.dumps(request)}))
    out = {}
    for item in data.get("response", {}).get("store_items", []):
        assets = item.get("assets") or {}
        fmt = assets.get("asset_url_format")
        if not fmt:
            continue

        def url(name):
            file = assets.get(name + "_2x") or assets.get(name)
            return STEAM_ASSETS + fmt.replace("${FILENAME}", file).split("?", 1)[0] if file else None

        deck = (item.get("platforms") or {}).get("steam_deck_compat_category")
        out[item["id"]] = {"art": url("library_capsule"), "hero": url("library_hero"), "deck": deck or None}
    return out


def rank(games, today):
    recent_cutoff = today - dt.timedelta(days=RECENT_DAYS)
    recent = [g for g in games if g["_date"] and g["_date"] >= recent_cutoff]
    rest = [g for g in games if not (g["_date"] and g["_date"] >= recent_cutoff)]
    rest.sort(key=lambda g: (-g["pop"], g["name"]))
    recent.sort(key=lambda g: (-g["pop"], g["name"]))
    keep = recent[: PER_PLATFORM // 3]
    keep += rest[: PER_PLATFORM - len(keep)]
    keep.sort(key=lambda g: (-g["pop"], g["name"]))
    return keep


def main():
    os.makedirs(OUT, exist_ok=True)
    today = dt.date.today()
    by_platform = {}
    for pid, name, qids, min_links in PLATFORMS:
        print(f"{name}: querying Wikidata", file=sys.stderr)
        try:
            games = platform_games(pid, qids, min_links)
        except RuntimeError as err:
            # Keep last week's file for this platform rather than fail the whole run.
            print(f"  {err}; keeping the existing {pid}.json", file=sys.stderr)
            continue
        by_platform[pid] = rank(games, today)
        print(f"  {len(games)} games, keeping {len(by_platform[pid])}", file=sys.stderr)
        time.sleep(2)

    # A game's platforms are every platform list it made it into.
    platforms_of = {}
    for pid, _, _, _ in PLATFORMS:
        if pid in by_platform:
            ids = [g["id"] for g in by_platform[pid]]
        else:
            with open(os.path.join(OUT, f"{pid}.json")) as f:
                ids = [g["id"] for g in json.load(f)]
        for qid in ids:
            platforms_of.setdefault(qid, []).append(pid)

    titles = sorted({g["wiki"] for games in by_platform.values() for g in games})
    print(f"Wikipedia: {len(titles)} articles", file=sys.stderr)
    batches = [titles[i:i + WIKI_BATCH] for i in range(0, len(titles), WIKI_BATCH)]
    details = {}
    with ThreadPoolExecutor(WORKERS) as pool:
        for n, result in enumerate(pool.map(wiki_details, batches), 1):
            details.update(result)
            if n % 50 == 0:
                print(f"  {n}/{len(batches)} batches", file=sys.stderr)

    appids = sorted({g["steam"] for games in by_platform.values() for g in games if g["steam"]})
    print(f"Steam: {len(appids)} apps", file=sys.stderr)
    steam = {}
    with ThreadPoolExecutor(WORKERS) as pool:
        for result in pool.map(steam_art, [appids[i:i + STEAM_BATCH] for i in range(0, len(appids), STEAM_BATCH)]):
            steam.update(result)

    counts = {}
    for pid, name, _, _ in PLATFORMS:
        if pid not in by_platform:
            with open(os.path.join(OUT, f"{pid}.json")) as f:
                counts[pid] = len(json.load(f))
            continue
        out = []
        for g in by_platform[pid]:
            extra = details.get(g["wiki"], {})
            art = steam.get(g["steam"], {})
            record = {
                "id": g["id"],
                "name": g["name"],
                "date": g["_date"].isoformat() if g["_date"] else None,
                "datePrecision": g["_precision"] if g["_precision"] != "day" else None,
                "genres": g["genres"],
                "dev": g["dev"],
                "pub": g["pub"],
                "cover": extra.get("cover"),
                "art": art.get("art"),
                "hero": art.get("hero"),
                "deck": art.get("deck"),
                "summary": extra.get("summary") or None,
                "wiki": None if extra.get("section") else g["wiki"],
                "steam": g["steam"],
                "metacritic": g["metacritic"],
                "pop": g["pop"],
                "platforms": platforms_of[g["id"]],
            }
            out.append({k: v for k, v in record.items() if v not in (None, [], "")})
        with open(os.path.join(OUT, f"{pid}.json"), "w") as f:
            json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
        counts[pid] = len(out)
        with_cover = sum(1 for g in out if "cover" in g or "art" in g)
        print(f"{name}: wrote {len(out)} ({with_cover} with box art)", file=sys.stderr)

    index = {"updated": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), "platforms": counts}
    with open(os.path.join(OUT, "index.json"), "w") as f:
        json.dump(index, f, indent=1)
        f.write("\n")


if __name__ == "__main__":
    main()
