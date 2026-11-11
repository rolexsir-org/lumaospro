#!/usr/bin/env python3
"""Rebuild every campaign asset in order."""

import a1_feature_graphic as a1
import a2_play_screens as a2
import b_social as b
import c_stories as c
import d_web as d
import e_posters as e
import f_brand as f

if __name__ == "__main__":
    a1.build()
    for fn in a2.ALL:
        fn()
    for fn in b.ALL:
        fn()
    for fn in c.ALL:
        fn()
    d.hero_wide(); d.hero_16x9(); d.announcement_card()
    for fn in (e.poster_01, e.poster_02, e.poster_03, e.ensemble, e.launch_square):
        fn()
    f.play_icon(); f.icon_sheet(); f.contact_sheet()
    print("campaign rebuilt")
