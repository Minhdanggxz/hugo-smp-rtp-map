# RTP Map (Fabric 1.21.4, client-only)

HUD map: green dots = RTP points, yellow marker = you. North is up, map is centered on you.

- Points come from `https://269153435.de/api/mod/rtp?dim=<overworld|nether|end>` (response: `p` = flat array of x,z,y triples).
- Cached in `config/rtpmap/<dim>.bin`, refreshed when older than 30 min. `/rtpmap reload` forces a download.
- Keys (Controls > RTP Map): `K` toggle, `]` zoom in, `[` zoom out.
- Config: `config/rtpmap.properties` (HUD x/y position, map size, zoom step).

Build: push to GitHub, the workflow in `.github/workflows/build.yml` builds it; the jar is under Actions > run > Artifacts.
Local: `gradle build` (Gradle 8.12, Java 21).
