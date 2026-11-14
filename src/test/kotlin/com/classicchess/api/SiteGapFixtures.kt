package com.classicchess.api

/** Wire fixtures for the site-gap and api-gaps endpoints. */
internal object SiteGapFixtures {
    const val PUBLIC_IMPORTED_GAME = """
{
  "token": "g1",
  "username": "ann.lee+1",
  "white": "Ann",
  "black": "Bob",
  "display_white": "Ann",
  "display_black": "Bob",
  "result": "1-0",
  "event": "Club",
  "site": "",
  "date": "2026.01.02",
  "round": "1",
  "white_elo": null,
  "black_elo": 1500,
  "eco": "C20",
  "opening": "King pawn",
  "move_count": 1,
  "imported_at": "2026-01-02T00:00:00+00:00",
  "pgn": "1. e4 1-0",
  "mainline": {
    "initial_fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    "final_fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1",
    "moves": [
      {"ply": 1, "san": "e4", "uci": "e2e4", "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"}
    ],
    "truncated": false,
    "parse_error": false
  },
  "urls": {
    "api_detail": "/api/v1/public/imported-games/ann.lee+1/g1/",
    "html": "/imported/ann.lee+1/g1/"
  }
}
"""

    const val CHESS_TODAY = """{"month": 11, "day": 9, "date": "2026-11-09", "timezone": "America/Chicago",
 "url": "/chess-today/11/9/", "featured_entry_id": "tal-born", "entries": [{"id": "tal-born", "category": "birth",
 "year": 1936, "text": "Mikhail Tal is born in Riga.", "details": [], "sources": [], "related": [],
 "target_url": "/players/Mikhail_Tal/", "photograph": null}]}"""

    const val GYM_SHARED_GAME = """{"id": 42, "bot": "morphy", "bot_version": "v4", "bot_name": "Paul Morphy",
 "player": "someone", "human_color": "white", "moves": ["e2e4"], "sans": ["e4"],
 "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1", "ply": 1, "result": "*", "finished": false,
 "termination": "", "termination_label": "", "opening": "King's Pawn Game", "time_control": "3+0",
 "date": "2026-11-12", "pgn": "1. e4 *", "urls": {"html": "/gym/shared/42/"}}"""

    const val SITE_CONTENT_SEARCH = """{"source": "public", "mode": "site", "query": "castling", "kind": "site",
 "count": 21, "page": 2, "page_size": 20, "page_count": 2, "has_next": false, "has_previous": true,
 "results": [{"kind": "page", "kind_label": "Page", "title": "Rules", "title_highlights": [], "heading": "Castling",
 "heading_highlights": [[0, 8]], "context": "Rules", "snippet": "Castling moves the king two squares.",
 "snippet_highlights": [[0, 8]], "urls": {"html": "/rules/#castling"}}], "html_urls": {}}"""

    fun siteUpdatePost(id: String) = """{"id": "$id", "date": "2026-11-12", "category": "feature",
 "category_label": "Feature", "title": "Post $id", "summary": "Summary.", "body": ["Body."],
 "link": {"url": "/whats-new/", "label": "What's New"}, "photo": {"kind": "brand", "alt_text": "Classic Chess",
 "image": {"url": "/static/brand.png", "width": 64, "height": 64}, "gallery_photo": null},
 "urls": {"api": "/api/v1/public/site-updates/$id/"}}"""

    fun siteUpdatePage(page: Int, hasNext: Boolean, vararg ids: String) = """{"source": "public", "count": 3,
 "page": $page, "page_size": 2, "page_count": 2, "has_next": $hasNext, "has_previous": ${page > 1},
 "results": [${ids.joinToString { siteUpdatePost(it) }}], "urls": {}}"""
}
