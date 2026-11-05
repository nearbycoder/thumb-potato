# Thumb Potato — Product and Data Spec

Thumb Potato is "play later" for games: the gaming counterpart of [Couch Potato](https://github.com/nearbycoder/couch-potato) (local checkout: `~/.t3/projects/couch-potato`). It finds games across Nintendo Switch 2, Switch, PlayStation 5/4, Xbox Series X|S/One, PC, Mac and mobile, shows where to buy and play them, keeps a backlog, tracks release dates, and helps you plan and pick what to play next.

Architecture, code organization and platform behavior mirror Couch Potato one-to-one; this file lists what is different. When in doubt, do what Couch Potato does. Visual rules are in `DESIGN.md`.

| Couch Potato | Thumb Potato |
|---|---|
| Streaming service (`Provider`), "My services" | `Platform` (console/PC/Mac/mobile), "My platforms" |
| Store offers (stream/rent/buy) | `StoreOffer` (Where to Play: store links + PC prices) |
| Watchlist / Couch Queue | **Backlog** |
| Want to Watch · Watching · Watched · Dropped | Want to Play · Playing · Beaten · Shelved |
| Episode progress | Hours played + progress % + "own it" |
| Couch night | Play session |
| Upcoming episodes + new-episode alerts | **Release Radar** + release-day alerts for followed games |
| Time available (runtime) | Session length (Quick break / An hour / Long session) |
| TMDB key (optional) | RAWG key (optional, free: https://rawg.io/apidocs) |
| TVmaze + iTunes (key-less) | Catalog on Pages + Wikipedia + Steam + CheapShark (key-less) |
| `couchpotato://title/<key>` | `thumbpotato://game/<key>` |
| `couchpotato.backup` | `thumbpotato.backup` |

Identifiers: bundle/application ID `com.nearbycoder.thumbpotato`, CloudKit container `iCloud.com.nearbycoder.thumbpotato`, Apple team `4F2YLTHL8T`, Android package `com.nearbycoder.thumbpotato`, website `https://nearbycoder.github.io/thumb-potato/`, repo `nearbycoder/thumb-potato`. HTTP `User-Agent: ThumbPotato/1.0 (https://github.com/nearbycoder/thumb-potato)` on every request (CheapShark and Wikimedia reject generic agents).

## Platforms

In display order. `id` is used in catalog file names, backups and settings.

| id | Name | Short | Monogram | Color | RAWG platform id |
|---|---|---|---|---|---|
| `switch2` | Nintendo Switch 2 | Switch 2 | NS2 | `#E60012` | look up "Nintendo Switch 2" in `/api/platforms` |
| `switch` | Nintendo Switch | Switch | NS | `#E60012` | 7 |
| `ps5` | PlayStation 5 | PS5 | PS5 | `#0070D1` | 187 |
| `ps4` | PlayStation 4 | PS4 | PS4 | `#003791` | 18 |
| `xbox-series` | Xbox Series X\|S | Xbox Series | XS | `#107C10` | 186 |
| `xbox-one` | Xbox One | Xbox One | XB1 | `#0E6A0E` | 1 |
| `pc` | PC | PC | PC | `#2A475E` | 4 |
| `mac` | Mac | Mac | MAC | `#A2AAAD` | 5 |
| `mobile` | iOS & Android | Mobile | MOB | `#5E5CE6` | 3, 21 |

Default "My platforms" on first launch: none selected; onboarding asks (like Couch Potato's services step).

## Stores (Where to Play)

Each store has a search URL template (`{q}` = URL-encoded name) and the platforms it serves. The detail page shows stores for the game's platforms, the user's platforms first.

| id | Name | Mono | Color | Platforms | Link |
|---|---|---|---|---|---|
| `steam` | Steam | ST | `#1B2838` | pc, mac | `https://store.steampowered.com/app/{steamAppID}` if known, else `https://store.steampowered.com/search/?term={q}` |
| `epic` | Epic Games Store | EG | `#2F2F2F` | pc, mac | `https://store.epicgames.com/en-US/browse?q={q}` |
| `gog` | GOG | GOG | `#86328A` | pc, mac | `https://www.gog.com/en/games?query={q}` |
| `playstation` | PlayStation Store | PS | `#0070D1` | ps5, ps4 | `https://store.playstation.com/en-us/search/{q}` |
| `xbox` | Xbox Store | XB | `#107C10` | xbox-series, xbox-one, pc | `https://www.xbox.com/en-US/search/results/games?q={q}` |
| `nintendo` | Nintendo eShop | N | `#E60012` | switch2, switch | `https://www.nintendo.com/us/search/#q={q}&p=1&cat=gme&sort=df` |
| `appstore` | App Store | AS | `#0A84FF` | mobile, mac | `https://apps.apple.com/us/search?term={q}` |
| `googleplay` | Google Play | GP | `#01875F` | mobile | `https://play.google.com/store/search?q={q}&c=apps` |

PC prices come from **CheapShark** (no key): `GET https://www.cheapshark.com/api/1.0/games?steamAppID={id}` (or `?title={q}&limit=5`) → `gameID`; then `GET /api/1.0/games?id={gameID}` → `deals[]` with `storeID`, `price`, `retailPrice`, `savings`, `dealID`. Store names from `GET /api/1.0/stores` (cache it). Deal link: `https://www.cheapshark.com/redirect?dealID={dealID}`. Show the best 6 deals under "PC prices", cheapest first.

## Game model

```
GameRef { source: wd | steam | rawg, id: String }   key = "<source>-<id>", e.g. wd-Q17185964, steam-1145360, rawg-3498
Game {
  ref, name, summary, coverURL, artURL, heroURL, releaseDate (date, may be in the future), year,
  genres [String from the fixed list below], developer, publisher, platforms [platform id],
  metacritic (0–100)?, steamAppID?, wikiTitle?, popularity (Double), steamDeck (1 unknown, 2 playable, 3 verified)?
}
GameDetail { game, screenshots [URL], trailerURL?, stores [StoreOffer], pcDeals [Deal], similar [Game], website?, steamReviews ("Very Positive · 91%")? }
```

Box art = `coverURL ?? artURL` (Wikipedia box art first, Steam library capsule second). Backdrop = `heroURL ?? first screenshot ?? blurred box art`.

Genres (fixed list, catalog and filters use exactly these): Action, Adventure, RPG, Shooter, Platformer, Puzzle, Strategy, Simulation, Sports, Racing, Fighting, Horror, Roguelike, Metroidvania, Party, Music, Visual Novel, Survival, Sandbox.

Map Steam genres/tags and RAWG genres onto this list by name (e.g. "Role-playing (RPG)" → RPG, "Massively Multiplayer" → drop, "Casual" → Puzzle only if nothing else).

## Data sources

### Key-less (default)

1. **Catalog** — `https://nearbycoder.github.io/thumb-potato/catalog/v1/<platform>.json`, one per platform, plus `index.json` (`{"updated": "...", "platforms": {"switch": 1200, ...}}`). Built daily by `scripts/build_catalog.py` from Wikidata + Wikipedia + Steam. Each file is a JSON array sorted by popularity:

   ```json
   {"id":"Q64577191","name":"The Legend of Zelda: Tears of the Kingdom","date":"2023-05-12",
    "genres":["Action","Adventure"],"dev":"Nintendo EPD","pub":"Nintendo",
    "cover":"https://upload.wikimedia.org/...jpg","art":"https://shared.akamai.steamstatic.com/...library_capsule_2x.jpg",
    "hero":"https://shared.akamai.steamstatic.com/...library_hero_2x.jpg","deck":3,
    "summary":"Two-sentence intro…","wiki":"The_Legend_of_Zelda:_Tears_of_the_Kingdom",
    "steam":1145360,"metacritic":96,"pop":52,"platforms":["switch","switch2"]}
   ```

   Every field except `id`, `name`, `pop`, `platforms` may be missing (`wiki` is omitted when the game has no English Wikipedia article of its own). `id` is a Wikidata QID → `GameRef(wd, id)`. `date` may be in the future (upcoming). `datePrecision` is `"year"` or `"month"` when the release date is only known that coarsely (the `date` is then the 1st of the year/month); it's absent for exact dates. Display such dates as "2027" / "March 2027" (and "TBA 2027" in Release Radar), never as "Jan 1, 2027", and don't schedule release-day alerts for them. Carry it through `Game.releaseDatePrecision` and the backup (`"releaseDatePrecision"`, optional). Download a platform file on first use, cache it on disk with a 24 h TTL (serve stale on failure), and page/filter/sort it locally. Also bundle nothing — always fetch. The dev-time copy lives in `docs/catalog/v1/` in this repo.

2. **Wikipedia** (detail + search, any game ever):
   - Detail: `GET https://en.wikipedia.org/api/rest_v1/page/summary/{wikiTitle}` → `extract` (full intro), `thumbnail.source`. If the game isn't in any downloaded catalog, get its platforms/date/genres from `https://www.wikidata.org/wiki/Special:EntityData/{QID}.json` (P400 platform, P577 date, P136 genre, P178 developer, P123 publisher, P1733 Steam app ID; map platform QIDs per the crawler's `PLATFORMS` list).
   - Search: `GET https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&generator=search&gsrsearch={q}%20hastemplate%3A%22Infobox%20video%20game%22&gsrlimit=20&prop=pageimages|pageprops|extracts&piprop=thumbnail&pithumbsize=600&pilicense=any&ppprop=wikibase_item&exintro=1&explaintext=1&exsentences=2&exlimit=max` → order by `index`; `pageprops.wikibase_item` is the QID. Merge with catalog records by QID when available (richer data).
   - Box art from Wikipedia is non-free cover art used for identification; credit "Box art and descriptions: Wikipedia (CC BY-SA for text)" in About.

3. **Steam** (PC/Mac live data):
   - Featured: `GET https://store.steampowered.com/api/featuredcategories?cc={region}&l=english` → `top_sellers.items`, `new_releases.items`, `specials.items`, `coming_soon.items` (fields `id`, `name`, `discount_percent`, `final_price` cents, `header_image`). Dedupe by `id` (lists repeat). Filter out non-games where obvious (hardware like "Steam Frame", demos, soundtracks: skip names containing "Demo", "Soundtrack").
   - Art for any app IDs (batch ≤ 50): `GET https://api.steampowered.com/IStoreBrowseService/GetItems/v1/?input_json={"ids":[{"appid":N}],"context":{"language":"english","country_code":"US"},"data_request":{"include_assets":true,"include_platforms":true,"include_basic_info":true,"include_reviews":true,"include_release":true}}` → `assets.asset_url_format` (`steam/apps/N/${FILENAME}`), `assets.library_capsule(_2x)` (portrait 3:4-ish box art), `assets.library_hero(_2x)` (backdrop), `platforms.steam_deck_compat_category`, `reviews.summary_filtered.{percent_positive,review_score_label}`, `release.steam_release_date` (unix). Image URL = `https://shared.akamai.steamstatic.com/store_item_assets/` + format with `${FILENAME}` replaced.
   - Detail: `GET https://store.steampowered.com/api/appdetails?appids={id}&cc={region}&l=english` → `short_description`, `screenshots[].path_full`, `movies[]` (trailer: first movie's mp4/hls URL if present; otherwise a YouTube search link "<name> trailer"), `genres[].description`, `metacritic.score`, `release_date.date`, `price_overview.final_formatted`, `developers`, `publishers`, `website`, `platforms` (windows → pc, mac → mac).
   - Search: `GET https://store.steampowered.com/api/storesearch/?term={q}&cc={region}&l=english` (`items[].id`, `name`, `metascore`).
   - A Steam-sourced game (`steam-<appid>`) whose app ID matches a catalog record should resolve to that record's `wd-` ref (dedupe by Steam ID) so the backlog never holds both.

4. **CheapShark** — PC prices (above) and the Tonight "Deals" row: `GET https://www.cheapshark.com/api/1.0/deals?sortBy=Deal%20Rating&pageSize=30&metacritic=75&onSale=1` (fields `title`, `steamAppID`, `salePrice`, `normalPrice`, `savings`, `metacriticScore`, `dealID`, `storeID`). Use Steam art for deals with a `steamAppID`; skip those without.

### With a RAWG key (optional, user pastes it in Settings → Data Sources)

`https://api.rawg.io/api` with `key=<key>`: `/games?search=`, `/games?platforms=<ids>&ordering=-added|-released|-metacritic|name&dates=YYYY-MM-DD,YYYY-MM-DD&genres=<slug>&metacritic=80,100&page=&page_size=40`, `/games/{id}`, `/games/{id}/screenshots`, `/games/{id}/stores`, `/games/{id}/game-series`. Refs `rawg-<id>`. With a key, platform pages, search and filters go to RAWG (infinite scroll); without one they run on the catalog. Treat this path as untested (no key available during development) and keep it isolated in its own client.

## Features

- **Tonight** (home): hero carousel (popular releases from the last 18 months on my platforms, with box art; fall back to all platforms); rows: Continue Playing (backlog Playing), New Releases (catalog, released in the last 120 days, newest first), Coming Soon (catalog, release date in the future, soonest first — "Add to Backlog" here follows the game for release alerts), Top on <platform> for each of my platforms (catalog by popularity), Top Sellers on Steam (if PC or Mac is mine), Deals (CheapShark). Settings gear in the toolbar on phones and the Duo.
- **Browse**: platform tiles (all platforms, mine first) → platform page (paged grid, filter bar); Moods → mood page; Genres → genre page; "See All" pages from Tonight rows. Filters per DESIGN.md: Sort (Popular, Newest, Top rated, A–Z), Released (Any, Upcoming, This year, Last 5 years, 2020s, 2010s, 2000s, Before 2000), Genre (not on mood pages), Critic score (Any, 70+, 80+, 90+). Platform pages show "Not one of your platforms" only as a small ⊕ toolbar button to add it.
- **Search**: catalog (instant, local, all downloaded platform files) + Wikipedia + Steam, merged and deduped; with RAWG key, RAWG instead.
- **Game detail**: backdrop, box art, title, metadata (year · genres · developer), critic score badge, Steam Deck badge (Verified/Playable) when known, platform monogram row (mine highlighted), actions (primary "Add to Backlog" / status menu; secondary "Plan a Session", "Trailer"), summary (expandable), screenshots row, Where to Play (store rows for the game's platforms, mine first, and PC prices from CheapShark), details (release date, developer, publisher, Steam reviews), similar games (same top genre on the same platforms from the catalog), backlog editor when saved (status, platform I'm playing on, hours played, progress %, own it, rating, notes, follow release).
- **Backlog**: segmented Want to Play · Playing · Beaten · Shelved, filter by platform/genre, sort by date added, title, rating, release date. Grid of covers with status/progress overlays. Stats card: games beaten this year, hours logged, backlog size.
- **Planner**: week strip, agenda of play sessions (game, start, duration, note, platform, add to calendar, reminder, done), **Release Radar** (followed backlog games with a future release date, soonest first, with countdown "in 12 days"). Plan a Session sheet: quick picks (Tonight 8 PM, Saturday 2 PM, Sunday 7 PM), date/time, duration (30m, 1h, 2h, 3h, custom), note, calendar toggle, reminder toggle.
- **Shuffle**: pool (Backlog Want to Play+Playing / Discover = catalog for my platforms), mood, session length, platform. Session length → genres: Quick break = Puzzle, Party, Racing, Fighting, Sports, Music, Roguelike, Platformer; An hour = Action, Shooter, Metroidvania, Adventure, Horror, Platformer, Roguelike; Long session = RPG, Strategy, Simulation, Sandbox, Survival, Visual Novel, Adventure.
- **Moods** → genres: Cozy = Simulation, Sandbox, Puzzle, Party, Music; Adrenaline = Shooter, Fighting, Racing, Action, Sports; Epic = RPG, Adventure, Metroidvania; Couch Co-op = Party, Sports, Racing, Fighting, Platformer; Spooky = Horror, Survival; Brainy = Puzzle, Strategy, Visual Novel. Blurbs: "Unwind with something gentle", "Fast, loud and competitive", "Big worlds and long stories", "Games for the whole couch", "Horror and survival", "Puzzles, tactics and stories to think about".
- **Settings**: My Platforms, Region (Steam/CheapShark prices; default device region), Data Sources (RAWG key; catalog last updated date), Notifications (release-day alerts, session reminders), Sync status (Apple), Export/Import backup JSON, Appearance (System/Dark/Light), About (Spud, credits: Wikipedia, Wikidata (CC0), Steam, CheapShark, RAWG; "Thumb Potato isn't affiliated with Nintendo, Sony, Microsoft, Valve or any store; platform and store names appear as text and monograms, not logos").
- **Onboarding**: 3 steps — welcome, pick your platforms, notifications. Skippable.
- **Notifications**: release-day alert at 9 AM local on a followed game's release date; session reminder 15 min before.
- **Deep links**: `thumbpotato://game/<key>`, `thumbpotato://shuffle`, `thumbpotato://planner`, `thumbpotato://backlog`, `thumbpotato://platform/<id>`.

## Saved data

`SavedGame`: key, source, externalID, name, summary, coverURL, heroURL, releaseDate, year, genres, platformIDs (game's platforms), playingOn (platform id?), status (`queued` | `playing` | `beaten` | `shelved`), rating (0–5), notes, hoursPlayed (Double), progress (0–100 Int), owned (Bool), isFollowing (Bool, release alerts), steamAppID?, wikiTitle?, metacritic?, addedAt, updatedAt, finishedAt.

`PlaySession`: id (UUID), gameKey, gameName, coverURL, platformID?, startsAt, durationMinutes, note, calendarEventID?, hasReminder, isDone, createdAt.

Apple: SwiftData + CloudKit (all properties defaulted, no unique constraints, dedupe by key like Couch Potato's `LibraryStore.dedupe`); settings via iCloud key-value store. Android: Room + DataStore.

### Backup format (shared, ISO-8601 dates)

```json
{"format":"thumbpotato.backup","version":1,"exportedAt":"2026-11-03T20:00:00Z",
 "games":[{"key":"wd-Q64577191","source":"wd","externalID":"Q64577191","name":"...","summary":"...",
   "coverURL":"...","heroURL":"...","releaseDate":"2023-05-12","year":2023,"genres":["Action"],
   "platformIDs":["switch"],"playingOn":"switch","status":"playing","rating":4,"notes":"",
   "hoursPlayed":12.5,"progress":40,"owned":true,"isFollowing":false,"steamAppID":null,
   "wikiTitle":"...","metacritic":96,"addedAt":"...","updatedAt":"...","finishedAt":null}],
 "sessions":[{"id":"UUID","gameKey":"wd-Q64577191","gameName":"...","coverURL":"...","platformID":"switch",
   "startsAt":"...","durationMinutes":120,"note":"","hasReminder":true,"isDone":false,"createdAt":"..."}]}
```

Import merges by key (newer `updatedAt` wins) and by session id.

## Layout targets

iPhone, iPad, iPhone Duo (folded 466×678 pt outer panel, unfolded ~951×669), Mac, Android phones, foldables and tablets. Key layouts off size class/window width, never device idiom. Everything in Couch Potato's layout history applies: no text over box art in wide heroes, equal grid columns, Settings reachable in every pose, detail actions visible above the fold on short screens, Shuffle deck scaled to height, service/platform grids drop to one column before names truncate.
