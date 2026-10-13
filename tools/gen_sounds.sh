#!/bin/bash
# Synthesizes the mod's placeholder sounds with ffmpeg (original, so no licence questions).
# Run from the repository root: bash tools/gen_sounds.sh
set -e
OUT=src/main/resources/assets/axiomatic/sounds
mkdir -p "$OUT"
enc() { ffmpeg -hide_banner -loglevel error -y "$@" -ac 1 -ar 44100 -c:a libvorbis -q:a 4; }

# A short soft blip: a rule was applied.
enc -f lavfi -i "sine=frequency=1200:duration=0.06" -af "volume=0.4,afade=t=out:st=0.01:d=0.05" "$OUT/applied.ogg"
# A two-tone ding: a numeral was certified.
enc -f lavfi -i "sine=frequency=880:duration=0.12" -f lavfi -i "sine=frequency=1320:duration=0.2" \
    -filter_complex "[0][1]concat=n=2:v=0:a=1,volume=0.5,afade=t=out:st=0.15:d=0.17" "$OUT/certified.ogg"
# A rising arpeggio: a theorem was proven or compiled.
enc -f lavfi -i "sine=frequency=523:duration=0.1" -f lavfi -i "sine=frequency=659:duration=0.1" \
    -f lavfi -i "sine=frequency=784:duration=0.1" -f lavfi -i "sine=frequency=1047:duration=0.35" \
    -filter_complex "[0][1][2][3]concat=n=4:v=0:a=1,volume=0.5,afade=t=out:st=0.4:d=0.25" "$OUT/proven.ogg"
# A falling buzz: a counterexample was found.
enc -f lavfi -i "aevalsrc='0.5*sgn(sin(2*PI*(500-300*t)*t))':d=0.6" -af "lowpass=f=2000,afade=t=out:st=0.3:d=0.3" "$OUT/refuted.ogg"
# A crackling boom: a disaster.
enc -f lavfi -i "anoisesrc=d=1.2:c=brown:a=0.9" -f lavfi -i "sine=frequency=55:duration=1.2" \
    -filter_complex "[0][1]amix=inputs=2,lowpass=f=900,afade=t=out:st=0.2:d=1.0,volume=2" "$OUT/disaster.ogg"
# A low hum pulse: a machine is working.
enc -f lavfi -i "sine=frequency=110:duration=0.4" -af "tremolo=f=8:d=0.6,volume=0.3,afade=t=in:d=0.05,afade=t=out:st=0.25:d=0.15" "$OUT/working.ogg"
ls -la "$OUT"
