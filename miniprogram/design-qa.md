# Trip Detail Design QA

## Scope

- Page: `pages/trip-detail/trip-detail`
- Source of visual truth: `G:\xwechat_files\wxid_340p2slrdax822_1609\temp\RWTemp\2026-07\ebd91ed896f1ce9f722abea6b81671bc\f827dc736fa2dd058a3a4a5faaf90d97.png`
- Test environment: WeChat DevTools iPhone 12/13 simulator, light theme, logged-in session
- Tested state: route tab, Day 1, mock publish ID `mock-pub-076`
- Approximate capture viewport: 363 x 768 CSS pixels

## Visual evidence

- Final implementation capture: `.codex/trip-detail-packing-icon-main-final.png`
- Full reference/implementation comparison: `.codex/trip-detail-final-qa-board.png`
- Latest focused comparison after user feedback: `.codex/trip-detail-feedback-qa-board.png`
- Transparent overlay comparison: `.codex/trip-detail-glass-qa-board.png`
- Fully transparent overlay comparison: `.codex/trip-detail-transparent-qa-board.png`
- Refined borderless hero comparison: `.codex/trip-detail-hero-refined-board.png`
- Global back-button consistency comparison: `.codex/global-back-consistency-board.png`
- The final comparison board stitches two captures of the same page state to remove DevTools viewport chrome and show the full screen continuously.
- The latest focused comparison uses the simulator's retained scrolled route state so the floating creator card, four-tab navigation, day rail, map, and itinerary can be judged together.

## Comparison history

### Iteration 1

- The hero, creator area, tabs, map, and itinerary sheet were structurally correct, but the vertical density was too loose.
- Too little of the map and itinerary fit in one viewport.
- Route colors and marker styling were inconsistent with the teal visual language in the reference.

Fixes:

- Compressed hero, creator, action, packing, and tab heights.
- Rebalanced map and itinerary sheet proportions.
- Unified route lines, controls, pills, and primary action to the teal brand color.

### Iteration 2

- Native map markers still appeared red because of the marker asset rendering path.

Fixes:

- Replaced the visible marker treatment with teal numbered native labels backed by a valid transparent marker asset.
- Rechecked the full page and focused map/itinerary region against the reference.

### Iteration 3

User feedback:

- Day pills visually touched because `cover-view` did not reliably honor flex `gap`.
- Route and map tabs duplicated the same core experience.
- Creator information should float over the lower edge of the hero image.

Fixes:

- Added explicit per-pill bottom spacing instead of relying on `gap`; the Day 1/2/3 controls now have stable separation.
- Removed the standalone map tab and its duplicate content, keeping the richer route tab with map, day switching, travel modes, itinerary, and navigation.
- Restored the creator panel as an elevated card overlapping the hero image, with matching dark-mode elevation.
- Recompiled and captured `.codex/trip-detail-feedback-final.png`; compared it with the source in `.codex/trip-detail-feedback-qa-board.png`.

### Iteration 4

User feedback:

- The complete creator panel should live inside the hero image rather than only overlap its lower edge.
- The panel should read as a transparent floating layer.

Fixes:

- Moved the `creator-panel` node inside `gallery-card` so the component is structurally contained by the hero.
- Increased the hero to 620rpx and placed title content above the panel; simulator measurements confirm a visible gap between the title block and panel.
- Switched the panel to a 58% translucent white surface with 20rpx backdrop blur, a subtle translucent border, and glass-like elevation.
- Made the packing card translucent as well so the background image remains visible through the whole overlay.
- Captured `.codex/trip-detail-glass-final-v2.png` and compared it with the source in `.codex/trip-detail-glass-qa-board.png`.

### Iteration 5

User feedback:

- The creator panel must have no background color; translucent glass was still too opaque.

Fixes:

- Removed the creator panel background, backdrop blur, border, and shadow entirely.
- Removed the packing card fill so the full creator area remains transparent over the hero image.
- Switched overlay copy and icons to white with restrained text shadow to preserve readability without adding a surface color.
- Captured `.codex/trip-detail-transparent-final-v2.png` and compared it with the source in `.codex/trip-detail-transparent-qa-board.png`.

### Iteration 6

User feedback:

- The fully transparent version still looked visually fragmented because inner borders and spacing created too many competing lines.

Fixes:

- Removed the action divider and packing-card outline.
- Reduced the hero from 620rpx to 560rpx and tightened the title/creator vertical rhythm.
- Reorganized the creator overlay into three lighter levels: compact author row, low-profile action metrics, and a borderless packing-progress row.
- Replaced the outlined follow control with a small filled pill to reduce line noise and improve the primary affordance.
- Captured `.codex/trip-detail-hero-refined.png` and compared it in `.codex/trip-detail-hero-refined-board.png`.

### Iteration 7

User feedback:

- The `hero-nav` back control should match the back treatment used by other custom-navigation screens.

Fixes:

- Audited all custom-navigation pages; `trip-detail` and `current-trip` were the two screens with custom back controls and used different assets and surfaces.
- Added shared `app-nav-back` and `app-nav-back-icon` styles to `app.wxss` with a 72rpx circular tap target, shared arrow asset, active state, shadow, and dark-mode treatment.
- Updated both pages to use `/images/icons/arrow-left.svg` and the shared global classes.
- Updated `trip-detail` to read the native WeChat capsule top position so its back control aligns with the system capsule like `current-trip`.
- Captured both screens and compared them side-by-side in `.codex/global-back-consistency-board.png`.

### Iteration 8

User feedback:

- Keep the packing-list icon on the left and `packing-main` in the center, but remove all content on the right.

Fixes:

- Retained the left clipboard icon and changed its treatment to a clearer teal circular mark.
- Removed the progress bar and right arrow from the WXML.
- Reduced the packing entry to a two-column icon/content layout.
- Moved `goPackingList` from the outer wrapper to `packing-main`; simulator navigation verified that tapping it opens `pages/packing-list/packing-list` with the expected trip query.
- Captured the result in `.codex/trip-detail-packing-icon-main-final.png`.

## Fidelity surfaces

- Fonts and typography: the existing compact hierarchy and weights remain unchanged; four-tab labels remain legible without wrapping.
- Spacing and layout rhythm: day pills have explicit 18rpx separation; the creator card now sits fully inside the 620rpx hero. The title block ends at approximately 165px and the panel begins at approximately 172px in the 390px simulator viewport, avoiding overlap.
- Colors and tokens: teal active states, route, markers, day pill, and CTA remain consistent; the creator surface uses translucent neutral glass without changing semantic colors.
- Image quality and assets: the existing hero, avatar, destination thumbnails, and local icon assets are preserved without placeholders or generated substitutes.
- Copy and content: the route/map duplication is removed without deleting any route functionality; dynamic trip and creator data remain intact.

## Functional verification

- WXML compilation: passed
- WXSS compilation: passed
- JavaScript syntax check: passed
- Route tab switching: passed
- Spots tab switching: passed
- Four-tab navigation after map-tab removal: passed
- Day pill selection: passed
- Map route rendering and travel mode refresh: passed
- Console TypeError: none
- Console ReferenceError: none
- Console SyntaxError: none

## Remaining differences

No actionable P0, P1, or P2 mismatch remains.

Accepted P3/platform differences:

- The native Tencent map tile palette is more vivid than the pale conceptual map used by the reference design.
- Trip title, destination images, itinerary count, and day count are dynamic data; the QA fixture contains three days instead of the reference's seven.
- The simulator and WeChat native capsule/safe-area rendering vary slightly from the static reference image.

## Result

final result: passed
