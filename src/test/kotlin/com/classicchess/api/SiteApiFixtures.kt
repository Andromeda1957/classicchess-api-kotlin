package com.classicchess.api

// Real server payloads captured from the versioned site-parity routes. Kept as a
// Kotlin source so the standalone SDK export carries them with the tests.
internal const val SITE_API_FIXTURES: String = """
{
 "daily": {
  "source": "public",
  "date": "2026-09-22",
  "game": {
   "token": "Akiba_Rubinstein/rubinstein-akiba-vs-maroczy-geza-1920",
   "slug": "rubinstein-akiba-vs-maroczy-geza-1920",
   "player": {
    "name": "Akiba Rubinstein",
    "slug": "Akiba_Rubinstein"
   },
   "display_white": "Rubinstein, Akiba",
   "display_black": "Maroczy, Geza",
   "result": "1-0",
   "date": "1920.08.18",
   "year": "1920",
   "event": "Gothenburg 1920",
   "descriptor": "Rubinstein vs Maroczy \u00b7 1\u20130 \u00b7 Gothenburg 1920",
   "urls": {
    "api_detail": "/api/v1/public/games/rubinstein-akiba-vs-maroczy-geza-1920/",
    "api_pgn": "/api/v1/public/games/rubinstein-akiba-vs-maroczy-geza-1920/pgn/",
    "gif": "/games/rubinstein-akiba-vs-maroczy-geza-1920/gif/",
    "html": "/games/rubinstein-akiba-vs-maroczy-geza-1920/",
    "daily": "/daily/"
   }
  }
 },
 "gallery": {
  "source": "public",
  "query": "tal",
  "catalog_count": 3692,
  "count": 90,
  "page": 1,
  "page_size": 1,
  "page_count": 90,
  "has_next": true,
  "has_previous": false,
  "results": [
   {
    "id": "67217590",
    "players": [
     {
      "name": "Efim Geller",
      "slug": "Efim_Geller",
      "api_detail": "/api/v1/public/players/Efim_Geller/"
     },
     {
      "name": "Mikhail Tal",
      "slug": "Mikhail_Tal",
      "api_detail": "/api/v1/public/players/Mikhail_Tal/"
     },
     {
      "name": "Paul Keres",
      "slug": "Paul_Keres",
      "api_detail": "/api/v1/public/players/Paul_Keres/"
     }
    ],
    "date": "1962-07-03",
    "date_detail": "1962-07-03",
    "display_date": "1962-07-03",
    "year": "1962",
    "event": "",
    "event_link": null,
    "location": "",
    "scene": "At the Board",
    "alt_text": "Efim Geller and Mikhail Tal and Paul Keres \u2014 At-the-Board, 1962-07-03",
    "image": {
     "url": "http://testserver/static/core/gallery/1962-07-03__Efim-Geller_and_Mikhail-Tal_and_Paul-Keres__At-the-Board__Anefo-914-0951-Commons-67217590.webp",
     "width": 720,
     "height": 475,
     "bytes": 24324
    },
    "source": {
     "kind": "wikimedia_commons",
     "id": "67217590",
     "name": "Wikimedia Commons",
     "title": "File:Schaken Nederland tegen Rusland beeld 17 Geller en Langeweg beeld 20 Geller en L, Bestanddeelnr 914-0951.jpg",
     "reference": "Wikimedia Commons page 67217590",
     "url": "https://commons.wikimedia.org/wiki/File:Schaken_Nederland_tegen_Rusland_beeld_17_Geller_en_Langeweg_beeld_20_Geller_en_L,_Bestanddeelnr_914-0951.jpg",
     "original_url": "https://upload.wikimedia.org/wikipedia/commons/7/7a/Schaken_Nederland_tegen_Rusland_beeld_17_Geller_en_Langeweg_beeld_20_Geller_en_L%2C_Bestanddeelnr_914-0951.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=original",
     "width": 3648,
     "height": 2406,
     "sha1": "5c3e00640a8a694897530601e1e3b989ac95174f"
    },
    "attribution": {
     "artist": "Harry Pot for Anefo",
     "credit": "http://proxy.handle.net/10648/aa0f205a-d0b4-102d-bcf8-003048976d84",
     "license": "CC0",
     "license_url": "https://creativecommons.org/publicdomain/zero/1.0/deed.en",
     "usage_terms": "Creative Commons Zero, Public Domain Dedication",
     "required": true
    },
    "urls": {
     "api_detail": "/api/v1/public/gallery/67217590/",
     "html": "/gallery/67217590/"
    }
   }
  ]
 },
 "beginner": {
  "source": "public",
  "count": 18,
  "stages": [
   {
    "key": "development-attack",
    "label": "Development & attack",
    "entries": [
     {
      "step": 1,
      "study_focus": "Morphy\u2019s Opera Game: develop with tempo, open lines before attacking, and punish a king left in the center.",
      "game": {
       "token": "Paul_Morphy/morphy-vs-von-braunschweigisouard-1858",
       "slug": "morphy-vs-von-braunschweigisouard-1858",
       "player": {
        "name": "Paul Morphy",
        "slug": "Paul_Morphy"
       },
       "display_white": "Morphy, Paul",
       "display_black": "Duke of Brunswick, Count",
       "result": "1-0",
       "date": "1858.11.02",
       "year": "1858",
       "event": "Opera House: Morphy-Duke of Brunswick",
       "descriptor": "Morphy vs Duke of Brunswick, 1858 \u00b7 1\u20130 \u00b7 Opera House: Morphy-Duke of Brunswick",
       "urls": {
        "api_detail": "/api/v1/public/games/morphy-vs-von-braunschweigisouard-1858/",
        "api_pgn": "/api/v1/public/games/morphy-vs-von-braunschweigisouard-1858/pgn/",
        "gif": "/games/morphy-vs-von-braunschweigisouard-1858/gif/",
        "html": "/games/morphy-vs-von-braunschweigisouard-1858/"
       }
      }
     }
    ]
   }
  ],
  "urls": {
   "html": "/beginners/"
  }
 },
 "search": {
  "mode": "curated",
  "query": "fischer spassky",
  "intent": "matchup",
  "counts": {
   "players": 2,
   "events": 6,
   "games": 55
  },
  "players": [
   {
    "name": "Bobby Fischer",
    "slug": "Bobby_Fischer",
    "game_count": 538,
    "archive_bucket": "Mid-1900s",
    "portrait": "/static/core/study/thumbs/bobby-fischer.webp",
    "urls": {
     "about": "/players/Bobby_Fischer/about/",
     "games": "/players/Bobby_Fischer/games/",
     "api_detail": "/api/v1/public/players/Bobby_Fischer/"
    }
   }
  ],
  "events": [
   {
    "name": "World Championship 1972: Fischer \u2013 Spassky",
    "slug": "wcc-1972-fischer-spassky",
    "type": "World Championship Match",
    "year": 1972,
    "game_count": 20,
    "series": "World Chess Championships",
    "urls": {
     "about": "/events/wcc-1972-fischer-spassky/about/",
     "games": "/events/wcc-1972-fischer-spassky/games/",
     "api_detail": "/api/v1/public/events/wcc-1972-fischer-spassky/",
     "api_about": "/api/v1/public/events/wcc-1972-fischer-spassky/about/"
    }
   }
  ],
  "games": [
   {
    "slug": "spassky-boris-v-vs-fischer-r-1992",
    "white": "Boris Vasilievich Spassky",
    "black": "Robert James Fischer",
    "result": "1-0",
    "date": "1992.??.??",
    "event": "Fischer\u2013Spassky Match 1992 (Sveti Stefan / Belgrade)",
    "opening": "Sicilian",
    "url": "/games/spassky-boris-v-vs-fischer-r-1992/",
    "memberships": [
     {
      "kind": "player",
      "label": "Boris Spassky archive",
      "url": "/players/Boris_Spassky/games/"
     },
     {
      "kind": "event",
      "label": "Fischer\u2013Spassky Match 1992 (Sveti Stefan / Belgrade)",
      "url": "/events/fischer-spassky-1992/about/"
     }
    ],
    "urls": {
     "html": "/games/spassky-boris-v-vs-fischer-r-1992/",
     "api_detail": "/api/v1/public/games/spassky-boris-v-vs-fischer-r-1992/"
    }
   }
  ],
  "source": "public",
  "html_urls": {
   "players": "/search/?q=fischer+spassky&kind=players",
   "events": "/search/?q=fischer+spassky&kind=events",
   "games": "/search/?q=fischer+spassky&kind=games"
  }
 },
 "search_page": {
  "source": "public",
  "mode": "curated",
  "query": "fischer spassky",
  "intent": "matchup",
  "kind": "games",
  "counts": {
   "players": 2,
   "events": 6,
   "games": 55
  },
  "count": 55,
  "page": 1,
  "page_size": 50,
  "page_count": 2,
  "has_next": true,
  "has_previous": false,
  "results": [
   {
    "slug": "spassky-boris-v-vs-fischer-r-1992",
    "white": "Boris Vasilievich Spassky",
    "black": "Robert James Fischer",
    "result": "1-0",
    "date": "1992.??.??",
    "event": "Fischer\u2013Spassky Match 1992 (Sveti Stefan / Belgrade)",
    "opening": "Sicilian",
    "url": "/games/spassky-boris-v-vs-fischer-r-1992/",
    "memberships": [
     {
      "kind": "player",
      "label": "Boris Spassky archive",
      "url": "/players/Boris_Spassky/games/"
     },
     {
      "kind": "event",
      "label": "Fischer\u2013Spassky Match 1992 (Sveti Stefan / Belgrade)",
      "url": "/events/fischer-spassky-1992/about/"
     }
    ],
    "urls": {
     "html": "/games/spassky-boris-v-vs-fischer-r-1992/",
     "api_detail": "/api/v1/public/games/spassky-boris-v-vs-fischer-r-1992/"
    }
   }
  ],
  "html_urls": {
   "players": "/search/?q=fischer+spassky&kind=players",
   "events": "/search/?q=fischer+spassky&kind=events",
   "games": "/search/?q=fischer+spassky&kind=games"
  }
 },
 "about": {
  "source": "public",
  "event": {
   "name": "Anderssen\u2013Harrwitz Match 1848",
   "slug": "anderssen-harrwitz-1848",
   "event_type": {
    "key": "match",
    "label": "Match"
   },
   "year": 1848,
   "game_count": 10,
   "expected_game_count": 10,
   "series": null,
   "source": {
    "event": "Match Anderssen-Harrwitz +5-5=0",
    "site": "Breslau",
    "year": 1848
   },
   "urls": {
    "api_detail": "/api/v1/public/events/anderssen-harrwitz-1848/",
    "api_about": "/api/v1/public/events/anderssen-harrwitz-1848/about/",
    "api_games": "/api/v1/public/games/?archive_event=anderssen-harrwitz-1848",
    "html_games": "/events/anderssen-harrwitz-1848/games/",
    "html_about": "/events/anderssen-harrwitz-1848/about/"
   }
  },
  "dates": "26 January \u2013 February 1848",
  "location": "Breslau, Prussia",
  "auto_vitals": [
   {
    "label": "Format",
    "value": "Match"
   },
   {
    "label": "Dates",
    "value": "26 January \u2013 February 1848"
   },
   {
    "label": "Venue",
    "value": "Breslau, Prussia"
   },
   {
    "label": "Games",
    "value": "10"
   }
  ],
  "bio": {
   "tagline": "Anderssen and Harrwitz divide ten decisive games",
   "lede": "Adolf Anderssen and Daniel Harrwitz split their ten-game Breslau match *5\u20135*. Every game was decisive: each player won five and none ended in a draw.",
   "vitals": [
    {
     "label": "Dates",
     "value": "26 January \u2013 February 1848"
    },
    {
     "label": "Venue",
     "value": "Breslau, Prussia"
    },
    {
     "label": "Format",
     "value": "Ten-game match"
    },
    {
     "label": "Result",
     "value": "Drawn 5\u20135 (+5 \u22125 =0)"
    }
   ],
   "sections": [
    {
     "heading": "Dead level without a draw",
     "body": [
      "The match's symmetry is striking: Anderssen and Harrwitz each won five games, so the contest finished level despite producing no individual draws.",
      "The complete played record supports the formal table's ten-game total and 5\u20135 result."
     ]
    }
   ],
   "numbers": [
    {
     "value": "10",
     "label": "Games"
    },
    {
     "value": "5\u20135",
     "label": "Final score"
    },
    {
     "value": "10",
     "label": "Decisive games"
    },
    {
     "value": "0",
     "label": "Draws"
    }
   ],
   "quotes": [],
   "images": [],
   "sources": [
    {
     "label": "Edo Historical Chess Ratings \u2014 Anderssen\u2013Harrwitz 1848",
     "url": "http://www.edochess.ca/matches/m162.html"
    }
   ]
  },
  "crosstable": {
   "kind": "match",
   "players": [
    {
     "name": "Anderssen, Adolf",
     "surname": "Anderssen",
     "computed_score": 5.0,
     "computed_text": "5",
     "official_score": 5.0,
     "official_text": "5",
     "player_slug": "Adolf_Anderssen"
    },
    {
     "name": "Harrwitz, Daniel",
     "surname": "Harrwitz",
     "computed_score": 5.0,
     "computed_text": "5",
     "official_score": 5.0,
     "official_text": "5"
    }
   ],
   "rows": [
    {
     "number": "1",
     "round": "1",
     "a_white": true,
     "a_pts": 1.0,
     "b_pts": 0.0,
     "a_text": "1",
     "b_text": "0",
     "a_cls": "win",
     "b_cls": "loss",
     "result": "1-0",
     "slug": "anderssen-adolf-vs-harrwitz-daniel-18480129"
    },
    {
     "number": "2",
     "round": "2",
     "a_white": false,
     "a_pts": 0.0,
     "b_pts": 1.0,
     "a_text": "0",
     "b_text": "1",
     "a_cls": "loss",
     "b_cls": "win",
     "result": "1-0",
     "slug": "harrwitz-daniel-vs-anderssen-adolf-18480201"
    },
    {
     "number": "3",
     "round": "3",
     "a_white": true,
     "a_pts": 0.0,
     "b_pts": 1.0,
     "a_text": "0",
     "b_text": "1",
     "a_cls": "loss",
     "b_cls": "win",
     "result": "0-1",
     "slug": "anderssen-adolf-vs-harrwitz-daniel-18480201"
    },
    {
     "number": "4",
     "round": "4",
     "a_white": false,
     "a_pts": 1.0,
     "b_pts": 0.0,
     "a_text": "1",
     "b_text": "0",
     "a_cls": "win",
     "b_cls": "loss",
     "result": "0-1",
     "slug": "harrwitz-daniel-vs-anderssen-adolf-184802-2"
    },
    {
     "number": "5",
     "round": "5",
     "a_white": true,
     "a_pts": 0.0,
     "b_pts": 1.0,
     "a_text": "0",
     "b_text": "1",
     "a_cls": "loss",
     "b_cls": "win",
     "result": "0-1",
     "slug": "anderssen-adolf-vs-harrwitz-daniel-184802-1e1d9608ef03"
    },
    {
     "number": "6",
     "round": "6",
     "a_white": false,
     "a_pts": 1.0,
     "b_pts": 0.0,
     "a_text": "1",
     "b_text": "0",
     "a_cls": "win",
     "b_cls": "loss",
     "result": "0-1",
     "slug": "harrwitz-daniel-vs-anderssen-adolf-184802-1"
    },
    {
     "number": "7",
     "round": "7",
     "a_white": true,
     "a_pts": 1.0,
     "b_pts": 0.0,
     "a_text": "1",
     "b_text": "0",
     "a_cls": "win",
     "b_cls": "loss",
     "result": "1-0",
     "slug": "anderssen-adolf-vs-harrwitz-daniel-184802"
    },
    {
     "number": "8",
     "round": "8",
     "a_white": false,
     "a_pts": 0.0,
     "b_pts": 1.0,
     "a_text": "0",
     "b_text": "1",
     "a_cls": "loss",
     "b_cls": "win",
     "result": "1-0",
     "slug": "harrwitz-daniel-vs-anderssen-adolf-184802-257fd98fcb3b"
    },
    {
     "number": "9",
     "round": "9",
     "a_white": true,
     "a_pts": 0.0,
     "b_pts": 1.0,
     "a_text": "0",
     "b_text": "1",
     "a_cls": "loss",
     "b_cls": "win",
     "result": "0-1",
     "slug": "anderssen-adolf-vs-harrwitz-daniel-184802-fe89e7271a82"
    },
    {
     "number": "10",
     "round": "10",
     "a_white": false,
     "a_pts": 1.0,
     "b_pts": 0.0,
     "a_text": "1",
     "b_text": "0",
     "a_cls": "win",
     "b_cls": "loss",
     "result": "0-1",
     "slug": "harrwitz-daniel-vs-anderssen-adolf-184802"
    }
   ],
   "official": {
    "label": "+5-5=0",
    "reference": "Anderssen, Adolf",
    "opponent": "Harrwitz, Daniel",
    "wins": 5,
    "losses": 5,
    "draws": 0,
    "total_games": 10,
    "scores": {
     "Anderssen, Adolf": 5.0,
     "Harrwitz, Daniel": 5.0
    },
    "ref_score": 5.0,
    "opp_score": 5.0
   },
   "headline": {
    "score": "5\u20135",
    "winner": null,
    "drawn": true,
    "text": "Match drawn 5\u20135",
    "detail": "Match drawn \u00b7 official result +5-5=0"
   },
   "archived_games": 10,
   "computed_complete": true
  }
 },
 "event": {
  "source": "public",
  "event": {
   "name": "Anderssen\u2013Harrwitz Match 1848",
   "slug": "anderssen-harrwitz-1848",
   "event_type": {
    "key": "match",
    "label": "Match"
   },
   "year": 1848,
   "game_count": 10,
   "expected_game_count": 10,
   "series": null,
   "source": {
    "event": "Match Anderssen-Harrwitz +5-5=0",
    "site": "Breslau",
    "year": 1848
   },
   "urls": {
    "api_detail": "/api/v1/public/events/anderssen-harrwitz-1848/",
    "api_about": "/api/v1/public/events/anderssen-harrwitz-1848/about/",
    "api_games": "/api/v1/public/games/?archive_event=anderssen-harrwitz-1848",
    "html_games": "/events/anderssen-harrwitz-1848/games/",
    "html_about": "/events/anderssen-harrwitz-1848/about/"
   }
  }
 },
 "tablebase": {
  "available": true,
  "covered": true,
  "coverage": "complete",
  "set": "lichess-7",
  "maximumPieces": 7,
  "positionPieces": 3,
  "sourceLabel": "Lichess tablebase",
  "moves": [
   {
    "uci": "c1b1",
    "san": "Kb1",
    "wdl": "draw",
    "zeroing": false,
    "dtz": 0
   }
  ],
  "wdl": "draw",
  "dtz": 0
 }
}
"""
