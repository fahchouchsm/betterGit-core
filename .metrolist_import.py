import csv
import hashlib
import json
import re
import shutil
import sqlite3
import sys
import tempfile
import time
import unicodedata
import zipfile
from concurrent.futures import ThreadPoolExecutor, as_completed
from difflib import SequenceMatcher
from pathlib import Path

sys.path.insert(0, "/tmp/metrolist-python")
from ytmusicapi import YTMusic

SOURCE = Path("/home/simo/Downloads/Metrolist_20260813203538.backup")
CSV_PATH = Path("/home/simo/Downloads/dawdaw.csv")
OUTPUT = Path("/home/simo/MEGA/projects/betterGit-core/Metrolist_20260813203538_fixed.backup")
CACHE = Path("/tmp/metrolist-search-cache.json")
PLAYLIST_NAME = CSV_PATH.stem


def normalized(value):
    value = unicodedata.normalize("NFKD", value or "").encode("ascii", "ignore").decode()
    value = re.sub(r"\b(feat|ft|featuring)\b.*", "", value, flags=re.I)
    return re.sub(r"[^a-z0-9]+", " ", value.lower()).strip()


def similarity(left, right):
    return SequenceMatcher(None, normalized(left), normalized(right)).ratio()


def score_result(row, result):
    wanted_title = row["Track Name"]
    wanted_artists = [a.strip() for a in row["Artist Name(s)"].split(";") if a.strip()]
    result_artists = [a.get("name", "") for a in result.get("artists", [])]
    title_score = similarity(wanted_title, result.get("title", ""))
    artist_score = max(
        (similarity(wanted, found) for wanted in wanted_artists for found in result_artists),
        default=0,
    )
    album_score = similarity(row.get("Album Name", ""), (result.get("album") or {}).get("name", ""))
    wanted_duration = round(int(row.get("Duration (ms)") or 0) / 1000)
    found_duration = result.get("duration_seconds") or 0
    difference = abs(wanted_duration - found_duration) if found_duration else 999
    duration_score = max(0, 1 - difference / 30)
    exact_bonus = 12 if normalized(wanted_title) == normalized(result.get("title", "")) else 0
    return 55 * title_score + 22 * artist_score + 18 * duration_score + 5 * album_score + exact_bonus


def find_track(index, row):
    query = f'{row["Track Name"]} {row["Artist Name(s)"].replace(";", " ")}'
    last_error = None
    for attempt in range(3):
        try:
            results = YTMusic().search(query, filter="songs", limit=10)
            candidates = [r for r in results if r.get("videoId")]
            if not candidates:
                return index, None, "no results"
            chosen = max(candidates, key=lambda result: score_result(row, result))
            chosen["matchScore"] = round(score_result(row, chosen), 2)
            return index, chosen, None
        except Exception as error:
            last_error = str(error)
            time.sleep(1.5 * (attempt + 1))
    return index, None, last_error


def load_rows():
    with CSV_PATH.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def resolve_tracks(rows):
    if CACHE.exists():
        cache = json.loads(CACHE.read_text(encoding="utf-8"))
    else:
        cache = {}
    resolved = [None] * len(rows)
    pending = []
    for index, row in enumerate(rows):
        key = row["Track URI"]
        if key in cache:
            resolved[index] = cache[key]
        else:
            pending.append((index, row))
    print(f"Resolving {len(pending)} tracks ({len(rows) - len(pending)} cached)", flush=True)
    completed = 0
    with ThreadPoolExecutor(max_workers=6) as pool:
        futures = [pool.submit(find_track, index, row) for index, row in pending]
        for future in as_completed(futures):
            index, result, error = future.result()
            resolved[index] = result
            cache[rows[index]["Track URI"]] = result
            completed += 1
            if completed % 10 == 0 or error or completed == len(pending):
                CACHE.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")
                status = f"; error: {error}" if error else ""
                print(f"Resolved {completed}/{len(pending)}{status}", flush=True)
    return resolved


def insert_playlist(db_path, rows, resolved):
    connection = sqlite3.connect(db_path)
    connection.execute("PRAGMA foreign_keys=ON")
    now = int(time.time() * 1000)
    playlist_id = "LP" + hashlib.sha1(f"{PLAYLIST_NAME}:{now}".encode()).hexdigest()[:8]
    connection.execute(
        "INSERT INTO playlist(id,name,createdAt,lastUpdateTime,isEditable,bookmarkedAt,isLocal,isAutoSync) VALUES(?,?,?,?,1,?,0,0)",
        (playlist_id, PLAYLIST_NAME, now, now, now),
    )
    inserted = 0
    low_confidence = []
    missing = []
    for position, (row, result) in enumerate(zip(rows, resolved)):
        if not result:
            missing.append(row["Track Name"])
            continue
        video_id = result["videoId"]
        duration = result.get("duration_seconds") or round(int(row.get("Duration (ms)") or 0) / 1000)
        thumbnail = (result.get("thumbnails") or [{}])[-1].get("url")
        album = result.get("album") or {}
        album_id = album.get("id")
        year_text = row.get("Release Date", "")[:4]
        year = int(year_text) if year_text.isdigit() else result.get("year")
        connection.execute(
            "INSERT OR IGNORE INTO song(id,title,duration,thumbnailUrl,albumId,albumName,explicit,year,liked,totalPlayTime,isLocal,isDownloaded,isUploaded,isVideo,isEpisode,isCached) VALUES(?,?,?,?,?,?,?,?,0,0,0,0,0,0,0,0)",
            (video_id, result.get("title") or row["Track Name"], duration, thumbnail, album_id, album.get("name") or row.get("Album Name"), int(bool(result.get("isExplicit"))), year),
        )
        if album_id:
            connection.execute(
                "INSERT OR IGNORE INTO album(id,title,year,thumbnailUrl,songCount,duration,explicit,lastUpdateTime,isLocal,isUploaded) VALUES(?,?,?,?,1,?,?,?,0,0)",
                (album_id, album.get("name") or row.get("Album Name") or "Unknown", year, thumbnail, duration, int(bool(result.get("isExplicit"))), now),
            )
            connection.execute(
                "INSERT OR IGNORE INTO song_album_map(songId,albumId,`index`) VALUES(?,?,0)",
                (video_id, album_id),
            )
        for artist_position, artist in enumerate(result.get("artists") or []):
            artist_name = artist.get("name") or "Unknown artist"
            artist_id = artist.get("id") or "artist:" + hashlib.sha1(artist_name.encode()).hexdigest()[:16]
            connection.execute(
                "INSERT OR IGNORE INTO artist(id,name,lastUpdateTime,isLocal,isPodcastChannel) VALUES(?,?,?,0,0)",
                (artist_id, artist_name, now),
            )
            connection.execute(
                "INSERT OR IGNORE INTO song_artist_map(songId,artistId,position) VALUES(?,?,?)",
                (video_id, artist_id, artist_position),
            )
        connection.execute(
            "INSERT INTO playlist_song_map(playlistId,songId,position) VALUES(?,?,?)",
            (playlist_id, video_id, position),
        )
        inserted += 1
        if result.get("matchScore", 0) < 75:
            low_confidence.append((row["Track Name"], result.get("title"), result.get("matchScore")))
    connection.commit()
    connection.execute("PRAGMA wal_checkpoint(TRUNCATE)")
    connection.close()
    return playlist_id, inserted, missing, low_confidence


def main():
    rows = load_rows()
    resolved = resolve_tracks(rows)
    with tempfile.TemporaryDirectory(prefix="metrolist-build-", dir="/tmp") as temp_dir:
        temp = Path(temp_dir)
        with zipfile.ZipFile(SOURCE) as archive:
            archive.extractall(temp)
        playlist_id, inserted, missing, low_confidence = insert_playlist(temp / "song.db", rows, resolved)
        with zipfile.ZipFile(OUTPUT, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for name in ("settings.preferences_pb", "song.db", "song.db-wal", "song.db-shm"):
                path = temp / name
                if path.exists():
                    archive.write(path, name)
    report = {
        "playlistId": playlist_id,
        "playlistName": PLAYLIST_NAME,
        "csvTracks": len(rows),
        "insertedTracks": inserted,
        "missingTracks": missing,
        "lowConfidenceMatches": low_confidence,
        "output": str(OUTPUT),
    }
    Path(str(OUTPUT) + ".report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({k: v if not isinstance(v, list) else len(v) for k, v in report.items()}, indent=2), flush=True)


if __name__ == "__main__":
    main()
