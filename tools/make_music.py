#!/usr/bin/env python3
"""Original 64-second seamless ambient piece. No samples, numpy or network required.
Dmaj9 / Aadd9 / Bm7 / Gmaj9: slow soft keys, long sine pads, quiet echoes.
The complete synthesis is reproducible; rendered Ogg is bundled for offline use.
"""
import array
import math
import pathlib
import subprocess
import tempfile
import wave

RATE = 22050
SECONDS = 64
N = RATE * SECONDS
left = array.array('f', [0]) * N
right = array.array('f', [0]) * N
chords = [(50, 57, 61, 64, 69), (45, 52, 59, 61, 64), (47, 54, 57, 62, 66), (43, 50, 54, 57, 62)]

def add_note(midi, start, duration, gain, pan, pad=False):
    frequency = 440 * 2 ** ((midi - 69) / 12)
    offset = int(start * RATE)
    span = int(duration * RATE)
    gl, gr = math.sqrt(1-pan) * gain, math.sqrt(pan) * gain
    for j in range(span):
        t = j / RATE
        if pad:
            env = math.sin(math.pi * j / span) ** 2
            value = math.sin(2*math.pi*frequency*t) * .77 + math.sin(2*math.pi*frequency*1.0014*t) * .23
        else:
            env = (1-math.exp(-t*35)) * math.exp(-t/1.4) * min(1, (duration-t)/.3)
            value = math.sin(2*math.pi*frequency*t) + .16*math.sin(2*math.pi*frequency*2*t)*math.exp(-t*2)
        k = (offset+j) % N
        left[k] += value * env * gl
        right[k] += value * env * gr

for bar in range(8):
    chord = chords[bar % 4]
    for i, note in enumerate(chord):
        add_note(note, bar*8-1.5, 11, .035, .2+i*.15, True)
    for i, beat in enumerate((.0, 1.5, 3.5, 6.0)):
        note=chord[(i+bar)%len(chord)]+12
        add_note(note, bar*8+beat, 5, .095, .35+(i%2)*.3)
        add_note(note, bar*8+beat+.45, 4, .022, .65-(i%2)*.3)
        add_note(note, bar*8+beat+.9, 4, .009, .35+(i%2)*.3)
peak=max(max(abs(v) for v in left),max(abs(v) for v in right))
scale=.70/max(peak, .001)
frames=array.array('h')
for a,b in zip(left,right):
    frames.extend((int(a*scale*32767),int(b*scale*32767)))
root=pathlib.Path(__file__).resolve().parents[1]
output=root/'app/src/main/assets/underground-afternoon.ogg'
with tempfile.TemporaryDirectory(prefix='ari-audio-') as temporary:
    wav=pathlib.Path(temporary)/'music.wav'
    with wave.open(str(wav),'wb') as stream:
        stream.setnchannels(2);stream.setsampwidth(2);stream.setframerate(RATE);stream.writeframes(frames.tobytes())
    subprocess.run(['ffmpeg','-v','error','-y','-i',str(wav),'-c:a','libvorbis','-q:a','5',str(output)],check=True)
print(f'{output}: {SECONDS}s, stereo, peak {peak*scale:.3f}, {output.stat().st_size} bytes')
