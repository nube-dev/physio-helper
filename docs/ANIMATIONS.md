# Exercise animations

Put one file per exercise in `app/src/main/res/raw/`, named exactly after the exercise id.
MP4 (H.264) or GIF, looped, ideally under 3 MB each. Names must be lowercase letters, digits
and underscores only — the ids below already are.

| Exercise | File name (add .mp4 or .gif) |
|---|---|
| Cross-body stretch | `stretch_cross_body` |
| Overhead capsular stretch (pending physio) | `stretch_overhead` |
| Wall 1: straight arm push back | `wall_1_extension_straight` |
| Wall 2: elbow push back | `wall_2_extension_elbow` |
| Wall 3: elbow push outward | `wall_3_abduction_elbow` |
| Wall 4: straight arm push out | `wall_4_abduction_straight` |
| Wall 5: fist push forward | `wall_5_flexion` |
| Wall 6a: external rotation (pending physio) | `wall_6a_external_rotation` |
| Wall 6b: internal rotation (pending physio) | `wall_6b_internal_rotation` |
| Prone Y | `prone_y` |
| Prone T | `prone_t` |
| Prone W | `prone_w` |
| Band front raise | `band_front_raise` |
| Band side raise | `band_side_raise` |
| Band shrug | `band_shrug` |
| Band bicep curl | `band_bicep_curl` |
| Band pull at shoulder height | `band_row_shoulder` |
| Band external rotation (pending physio) | `band_external_rotation` |

Only one file per name: `prone_y.mp4` and `prone_y.gif` together will fail the build.

A clip can also be attached on the phone without rebuilding: Program → exercise → Pick clip.
An attached clip overrides the bundled file.
