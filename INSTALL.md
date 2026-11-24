# Ancient Magic - visuals pack (bloom + painted sky + ink Hate impact)

Copy the `src/` folder over your project (same paths, replace when asked), then do the ONE deletion below.

## 1. Delete the misplaced pipeline
    src/main/resources/assets/ancientmagic/pinwheel/shaders/program/sky_tint.json   <- DELETE
It was a post pipeline sitting in the shader-program folder. The corrected copy is now
`pinwheel/post/sky_tint.json` (where Veil looks for pipelines and where SkyEffects' Javadoc says it lives).

## 2. Files
NEW
- java/.../BloomFx.java                                   bloom pulse manager (add/remove pipeline, upload uniforms)
- pinwheel/post/bloom.json                                extract -> blur x2 -> composite
- pinwheel/post/sky_tint.json                             moved here from shaders/program/
- pinwheel/framebuffers/bloom_a|b|c.json                  global half / quarter-res bloom buffers
- pinwheel/shaders/program/bloom_{extract,blur_h,blur_v,composite}.{json,fsh}
REPLACED
- java/.../ClientConfig.java        + skyContent, bloom, bloomIntensity, inkImpact
- java/.../SkyEffects.java          + moon/sun direction, time, kind uniforms; bloom pulse on trigger
- java/.../ImpactFrames.java        + bloom pulse on hit, ImpactInk uniform
- java/.../AncientMagicClient.java  + BloomFx.init
- lang/en_us.json                   + new config names
- pinwheel/shaders/program/sky_grade.fsh     painted sky (blood moon + clouds + stars / aurora + sun rays + clouds)
- pinwheel/shaders/program/impact_hate.fsh   radial blur + Sobel ink + halftone + hard first frame

## 3. Tuning knobs
- Bloom threshold: `THRESHOLD` in BloomFx.java. Too much glow on snow/sand -> raise it (.85-.9).
- Bloom strength: `bloomIntensity` in the client config, or the peak values passed to `BloomFx.pulse(...)`.
- Moon size: `const float R` in `bloodSky()` of sky_grade.fsh. Cloud speed: the `t * 0.020` in `clouds()`.
- Moon / sun height: the last number of each `Kind` in SkyEffects.java (radians above the horizon).
- Halftone dot size: `cell` in impact_hate.fsh. Outline thickness: `px` in the Sobel block.

## 4. Not tested (written from the Veil docs, never run)
Things to check first if something looks off, in order:
1. Veil log shows an error about `ancientmagic:bloom_a`/`bloom_c` -> the global framebuffer JSONs are the guess to fix.
2. No glow at all -> make sure `pm.add(bloom)` happens (log it in BloomFx.onTick) and that bloom.json loads.
3. Sky looks flat red/gold -> `SkyContent` uniform is 0 (config off) or `viewDirFromUv` include failed.
4. Moon in the wrong place / mirrored -> flip the sign of `SkyDir.z` in SkyEffects.state().
