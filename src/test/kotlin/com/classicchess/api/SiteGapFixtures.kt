package com.classicchess.api

/** Wire fixtures for the site-gap endpoints. */
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
    "moves": []
  },
  "urls": {
    "api_detail": "/api/v1/public/imported-games/ann.lee+1/g1/",
    "html": "/imported/ann.lee+1/g1/"
  }
}
"""
}
