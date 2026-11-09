# FarmUpCraft

FarmUpCraft is a Minecraft economy and farming mod with player currency,
villages, plots, markets, crop rules, and server-side management tools. The
repository contains a Forge 1.20.1 target and an in-progress NeoForge 26.2
port; feature parity between the two targets is not guaranteed.

## Features

- **Economy and trading:** player and village coin balances, village fees and
  upkeep, a configurable market, buying and selling, and coin payments for
  enchanting.
- **Villages and land:** create and manage villages, invite members, manage
  plots and visitors, show claimed-chunk borders, and inspect village farms,
  animals, structures, and chests.
- **Farming and crafting:** crop and biome planting rules, crop/biome lookup
  tools, HarvestCraft-aware content, and cookware with use limits.
- **World and gameplay systems:** animal, crop, tree, wood, stone, NPC,
  structure, chunk, and island-scan management; the exact systems available
  depend on the target and its installed companion mods.
- **Client integrations:** a player-data HUD, claim/village map features,
  waypoint tools, recipe lookup, crop tooltips, and weather-sound controls.
  JEI and JourneyMap integrations require those companion mods.
- **Persistent server data:** player, village, chunk, market, NPC, world, and
  other mod data are saved and synchronized to clients. Database export,
  restore, query, and (where enabled) managed backups are available to
  operators.

NeoForge is still a partial port, not a feature-complete replacement for the
Forge target. Its current port status, test coverage, known limitations, and
legacy-world migration warning are documented in the development notes below.

## Commands

### NeoForge audio-device failure recovery

NeoForge 26.2 delays automatic sound-engine retries for five real-time minutes after
initialization fails (for example, an HDMI TV disconnecting while off). Vanilla's
original error and stack trace remain visible, followed by one cooldown warning
per failed attempt; repeated per-tick device-loss checks are suppressed during
the cooldown. Sound output may remain unavailable until those five minutes expire.
Manual sound-device/resource reloads bypass the automatic cooldown and can retry
immediately. Successful initialization restores normal device monitoring; another
failure restarts the five-minute cooldown. No device is hardcoded and saved sound settings are
unchanged. This does not suppress unrelated audio errors or attachment-sync errors.
Cooldown boundary/reset tests use `:test --tests '*SoundRetryCooldownTest'`.
The disposable client probe uses `src\networkTest\run-player-actions.ps1
-GemRender -SoundRetry` with prepared dev launches; it checks the transformed
sound engine's suppressed automatic checks, due retry, and actual manual reload.

### Underground gems

Both targets add ruby, opal, sapphire, topaz, and peridot items, with stone and
deepslate ore variants and original temporary 16x16 graphics. Harvesting requires
a diamond-tier pickaxe or better. Ordinary mining drops one gem and 1-5 XP;
diamond-style Fortune increases gem drops (Fortune III: 1-4), while Silk Touch
drops the matching ore block without XP.

The editable biome policy is installed at `config\gem_biome_config.json`.
World-specific seeded assignments are saved under
`<world>\farmupcraft\gems\gem_biomes.json`: each gem selects three distinct eligible
Overworld biomes with separate center heights and +/-15 bands. Cave biomes are
excluded. Rare biomes are excluded except Jade Cliffs, Mystic Grove, Ominous
Woods, and Volcanic Plains. Other mods' eligible Overworld biomes are supported;
absent optional biome IDs do not require installing their mods. Administrator
policy edits must not be used to silently reroll an existing world's assignments.

New terrain receives gems through world generation. Previously generated chunks
are populated incrementally while loaded, using saved completion markers.
Deterministic deposits reproduce their chunk-owned portions across borders.
Generation does not force-load unexplored chunks. Old-chunk population waits
for its loaded 3x3 neighborhood so exact block-biome sampling stays consistent
at borders; an isolated old chunk may remain pending. Per-tick budgets limit
work to 256 samples, 64 placements, and eight considered chunks.
Flat/single-biome worlds with fewer than three eligible biomes explicitly disable
gem generation rather than invent assignments or fail normal world startup.
Previously initialized missing/corrupt rule files still report an error.
Placement replaces only
eligible host materials and stays at least five blocks under the terrain
surface/seabed. For new placements of all five gems, targets touching face-adjacent
air have a deterministic 50% chance to try a fully buried replacement instead.
Up to twelve positions one/two blocks away are tried, within the same owning
chunk, permitted biome, and height band. Hidden targets are unchanged; existing
ores/completed chunks are not retroactively altered. If no valid buried position
exists, the rejected exposed target is omitted, so total amounts may decrease.
Relocations avoid other planned target origins, and overlapping origins are
resolved in stable order so partial-generation retries cannot duplicate gems.
New-world terrain references are Crag 70, Stony Peaks 120, Snowy Slopes 100,
Highland 100, and Jade Cliffs 120; other non-ocean biomes default to 64.
These are terrain anchors, not gem center depths. Actual column burial is still
checked. Existing saved biome assignments, depths, and references are preserved.
This is a per-target exposure rule, not a guaranteed 50% reduction in visible veins.
**This intentionally applies inside
claims and can replace player-placed stone/dirt/sand host blocks. Back up existing
worlds before installing this feature.** The editable `farmupcraft:gem_replaceable`
block tag defines the host materials.

Villagers use a saved, exclusive knowledge roll: 1/10 ordinary hints, 1/30 rare
hints, otherwise no gem knowledge. Each knowledgeable villager knows one hint
about one gem. Rare hints split equally between one possible level and a
two-biome rumor containing one true and one false alternative. Code randomizes
the alternatives' order; the AI phrases only the permitted facts and invalid
factual responses produce explicit errors. Direction evidence uses only nearby
loaded biomes within 128 blocks, with a loose height hint when none is available.
If the biome at the villager's X/Z and the gem's underground height already
matches, the hint also uses the loose height bound instead of a compass direction.
Loose height hints are clamped to the world's saved minimum/maximum Y. At a
world boundary they say "at or above/below" to include the boundary itself;
they never suggest an out-of-world level such as -80 in a -64-bottom world.
Villagers without custom profiles receive a generic conversation profile.
Ordinary gem knowledge has a 7% chance per villager; rumor and approximate-level
knowledge each retain 1/60 (about 1.67%). The remaining 89.67% have no knowledge.
An independent deterministic reduction removes 30% of former ordinary
assignments without changing rare assignments or retained villagers' hint facts.
All spoken gem answers now go through the LLM, including knowledgeable hints,
rumors, and admissions of ignorance. Whole-word mentions of gem/gems or a gem's
singular/plural name trigger the gem prompt (not substrings such as "gemini").
On non-triggering messages, knowledgeable villagers have a 5% unsolicited gem
topic roll before the LLM call. Otherwise no gem prompt or fact is included.
Only the common and selected mode sections are sent, never the whole gem file.
No code-written hint is appended afterward. Code still selects the
permitted fact and knowledge probabilities. The model may rephrase facts and add
personality. Ordinary conversation and ignorant replies have no code
content validation; the ordinary-conversation geology/number/biome filter has
been removed. Knowledgeable replies
must be nonempty and at most 600 characters, mention the assigned gem, retain
any supplied numeric level (including its minus sign) and compass direction.
The Overworld reference is optional. No additional rumor, above/below, or approximate-level
checks apply.
It does not prevent additional invented facts or reliably understand natural
language, even though the prompt still instructs the model not to invent them.
Invalid gem replies are logged and retried once with
the same prompt; a second invalid reply shows an NPC error, never a canned
successful gem fallback. Empty replies, service errors, and timeouts are logged
and shown as "I'm a bit busy right now. Could we talk later?" without a retry.
The busy response is delivered and logged like normal NPC speech. Each
attempt has a 30-second timeout, and replies are discarded if the conversation
ends.

Editable UTF-8 conversation prompts are copied on first use to
`config/farmupcraft/prompts/npc-conversation.txt` and `npc-gems.txt`.
Both are reread on every conversation request, without overwriting user edits.
Gem instructions supplement the existing villager profile: the profile controls
personality and speaking style, while gem constraints govern only permitted facts.
The base uses `{npc_name}`, `{description}`, `{personality}`, `{player_name}`,
and `{player_message}`. The gem file has `[COMMON]`, `[IGNORANCE]`, `[RUMOR]`,
`[ORDINARY]`, and `[LEVEL]` sections; only the common and active mode are sent.
Keep `{gem_mode}` and `{gem_fact}` in the selected text. Unreadable/empty files or
invalid sections/placeholders produce a logged error and a visible notification.
NPC prompts are sent as system instructions, with player chat in a separate
user message. Exact expanded prompts are logged under `[NPC Prompt]` in the
server log; these include NPC profile and player text. Structure-note and
profile-generation requests retain their existing request format.
Existing profile migration preserves custom fields and stores backups under
`npcData\gem-migration-v1-backups`.

Only newly created FarmUpCraft special ruin-note chests use the same hint chances.
Ordinary chests, reused chests, and existing special chests are not retrofitted.
Gem hints are paper notes; no exact deposit coordinates or full rule tables are
provided to ordinary players. Missing market entries receive initial prices:
ruby/sapphire 100, opal 80, topaz/peridot 60; existing configured prices remain.

See [GEMS_PLAN.md](GEMS_PLAN.md) for the design and verification checklist.
The original asset generator is [generate-gem-assets.cjs](scripts/generate-gem-assets.cjs).
NeoForge mining acceptance uses `:runGameTestServer -PgameTests=farmupcraft:gem_*`.
The disposable graphical probe is `src\networkTest\run-player-actions.ps1 -GemRender`
after preparing development launch exports.

`/show cropbiomes <crop> [crop2] [crop3]` accepts up to three space-separated
crop aliases or item IDs. With multiple crops it lists only biomes shared by
every crop, retaining green highlighting for biomes in your village.

`/village list2 [page]` opens an alphabetical graphical village directory.
Each row shows its level, unique claimed-chunk count, X/Z coordinates and
ownership status, highlights your home village, and has an Info button opening
that village's existing dashboard. Scroll within each 64-village page and use
Previous/Next for larger directories. `/village list` keeps its chat output;
the graphical list is also available from `/menu`.

The wood market catalog includes bamboo planks and cherry/mangrove boats
(including chest boats) in both versions, plus the pale oak wood family in
NeoForge. After upgrading an existing world, an operator can run
`/market importtxt wood` to add missing entries without resetting existing
prices or stock. Newly imported items start inactive and enter normal market
rotation; unavailable optional-mod items are skipped with diagnostics.

Biome crop selections are saved by named biome/item IDs. Startup and tag reloads
add rules for newly eligible biomes without rerolling existing selections, and
rebuild crop-search indexes from those saved selections. New allocations use the
world seed, stable biome name, and sorted crop item IDs rather than registry
numbers. BOP Overworld coverage follows its runtime dimension tags, excluding
Nether/End biomes. Removed biome rules are logged and retained (but excluded
from searches) so restoring that biome can recover its crops. Invalid data still
reports errors. Do not clear the biome manager during a mod update: clearing is
an intentional reset and discards existing allocations, including retained ones.
Back up worlds before changing biome mods; this protects FarmUpCraft's crop
data, not Minecraft terrain or other mods' saved-data compatibility.
For example: `/show cropbiomes carrot potato wheat`. Single-crop lookup is
unchanged; unknown crops and more than three inputs produce an error.

Mangrove propagules use the same planting roll as ordinary saplings on both
targets: 10% base success outside nursery plots, 65% inside, plus one percentage
point per two player experience levels. Failed attempts consume one propagule.
This includes planting on mud or clay; mangroves remain unrestricted by biome,
and Creative players keep the existing exemption.

Sapling planting checks the actual placement position, including off-hand use,
replacing grass/flowers, and placement across plot borders. Failed planting shows
an action-bar notice and consumes one sapling. The bonus plot is a **nursery**,
not an orchard. HarvestCraft fruit saplings retain their guaranteed-success
exemption; ordinary saplings can also reach guaranteed success with enough XP.
Leaf loot uses the same extra drop gate for player breaking and natural decay:
20% outside nurseries, or 30% per nursery level (guaranteed at level 4+).
This multiplies the normal vanilla/mod loot chance, rather than replacing it:
an ordinary 5% sapling chance becomes about 1% outside nurseries.

Creepers without a combat target seek visible, reachable vanilla chests, trapped
chests, and barrels within 12 blocks, including claimed village land. They walk
within two blocks and use their normal fuse/explosion, not remote block removal.
Walls block detection, and losing the storage target cancels the fuse. This
requires `mobGriefing=true`; normal explosion permissions and other mods still
apply. Ender chests and shulker boxes are not targeted.

Mature `CropBlock` crops (including vanilla and HarvestCraft) on farm plots have
a harvest failure chance of 30% at farm level 1, 20% at level 2, 10% at level 3,
and zero at level 4+. Both right-click attempts and player breaking use the rule.
Failure resets the crop to age zero without removing its block or giving any
produce/seeds/extra farm bonus; an action-bar notice explains that it must regrow.
Successful harvests retain their existing yields and higher-level right-click
HarvestCraft bonus. Immature crops, non-farm plots, Creative players, bone-meal
interactions, and building-move operations are unaffected.
The right-click failure gate runs at HIGHEST priority, before Harvest With Ease's
HIGH-priority harvesting handler, so it cannot harvest/reset the clicked crop
before the failure roll. The optional NeoForge integration test exercises real
carrot and HarvestCraft bell-pepper right-clicks at all four farm levels.
Run `:runGameTestServer -PgameTests=farmupcraft:harvest_with_ease_failures`
with `-PharvestWithEaseTestJar=<local Harvest With Ease jar>` and
`-PharvestWithEaseCobwebTestJar=<local Cobweb jar>` to enable it. These jars are
test-runtime-only; no harvesting mod is bundled or required by FarmUpCraft.

HarvestCraft tree-fruit right-clicks also run before Harvest With Ease. In the
installed 26.2 HarvestCraft version, tree fruit extends `CropBlock`, so Harvest
With Ease selects it without additional crop configuration. The separate fruit
rule retains 40% base success, +2 percentage points per 100 net pickups, +1 per
XP level, and +20 in an orchard biome, capped at 98% success. Nursery levels add
bonus fruit on success, not failure protection. Failure removes the entire fruit
block and returns no loot. Maple, paperbark, and spiderweb use their actual
products (maple syrup, vanilla paper, and vanilla string) for item/stat lookup.
On both targets, each successful fruit/nut harvest roll has a 50% chance to
reduce a random different fruit/nut's effective pickup progress by one. Only
types with positive progress qualify; progress never falls below zero. Bonus
items do not cause additional rolls. Cinnamon, syrup, paper, and string do not
participate. Effective progress is net pickups minus a separate per-player
penalty saved in player entity data (`farmupcraft_fruit_skill_penalties`),
preserved on death and dimension changes. Vanilla pickup/drop statistics are
not modified; success rates and pickup milestone messages use effective progress.
Both targets log every handled mature tree-fruit harvest roll, including player,
block/position, biome, net pickup count, XP level, raw rate, the 0-99 roll,
`successIfRollBelow` threshold, and `SUCCESS`/`FAILURE`. The log uses the actual
single harvest roll without consuming an extra random value. A successful roll
allows normal harvesting to continue; it is not confirmation of final loot
delivery by another mod. Creative/client/offhand, bone meal, unripe fruit, and
already-canceled interactions do not roll on NeoForge.
The optional `farmupcraft:harvest_with_ease_tree_fruit` world test verifies every
registered mature tree fruit at nursery levels 1/4 and XP levels 0/20/100, with
all 100 rolls, using the same local-jar test properties above.

Ordinary HarvestCraft hanging-fruit trees use five-to-six-block trunks, putting
fruit at block two or three above flat ground with one or two clear blocks below.
Pinenut retains its natural seven-block minimum and height variation. Cinnamon,
maple, and paperbark retain six-block minimum trunks so their lowest harvestable
bark does not extend into the soil. Fruit types, canopy shapes, and placement
rarity are retained; ordinary tree height variation is deliberately narrowed.
This changes newly
generated trees, not trees already in the world. Actual planted-sapling growth
and configured/placed generation are covered by in-world clearance tests.

New structure note chests prefer the nearest valid central area, with randomized
choices within a small distance band rather than corner-first or roof-first
placement. Existing nearby chests are reused; if all are farther than eight
blocks from the center, a safe central chest is added when possible. New chests
stay inside known structure bounds, search up to 16 blocks horizontally from the
center, and require air, solid support, headroom, and a walkable adjacent spot.
Already recorded special chests and visited structures are not moved or rewritten.
AI notes now vary across journals, letters, riddles, reports, folklore, warnings,
and other voices; offline fallbacks also include many new combinations. Previously
cached notes are still consumed unchanged until exhausted. Notes are atmospheric,
not guaranteed quests or treasure directions.

`/village info2` opens the FarmUpcraft Village Overview dashboard on both
targets. Like `/village info`, it defaults to your home village and accepts
an optional quoted village name. Read-only cards show level, treasury,
daily income/upkeep/net, next plot cost, structures, land, nearby villagers,
founder, breeding animal, center, and members, using the existing info
calculations. Debts/negative daily balances are red; positive finances are
green. The village overview has a green/gold village banner, inline land stats,
a short quote unique to each level, and original pixel-style village artwork
that develops from cottages to a walled settlement as the village levels up.
The artwork is illustrative, not a screenshot or terrain map. Separate treasury,
next-level progression, and community panels replace the spreadsheet of cards.
Progression shows the coin cost, saved villager/structure counts and required
counts, next range, plot-upgrade limit changes, and increased base upkeep.
The progression section contains the current level's Level Up button for your
home village; it scrolls with the section rather than staying in the footer.
Daily upkeep is displayed as an orange negative amount; this is presentation
only and does not change the upkeep or net-income calculations.
Confirmation states the charge and requirements; village coins pay first, with
any remainder paid from the player's wallet. Village debt is not spent or added
to the level-up price. Insufficient combined funds leave both balances unchanged.
The server checks
the village identity and quoted level before using the existing level-up action,
then the overview refreshes. Other villages and level 10 cannot be upgraded from
this screen. Requirements remain visible in the progression panel.
Viewing another village by name hides the treasury and progression panels,
their financial data, and the level-up button.
Villager counts and the last census time are saved on each village. Every five
minutes, online players' current village chunks are checked; a village is scanned
only if a player is inside it and its last census is at least two real-world hours
old (or missing). Info and level-up commands use the same refresh gate and saved
count. The scan is centered on the city-center chunk, extends ten chunks in X/Z
and the full world height, and counts living loaded villagers without loading or
generating chunks. Offline or unoccupied villages retain their saved counts;
unloaded villagers are not included.
Scroll to reach the whole overview; wide screens place the treasury and
progression panels side by side. Tooltips appear only for text that was clipped,
not as duplicates of visible information.
Plot info retains its compact stat tiles and existing action buttons.
Reopen the command for updated values; `/village info` remains
the chat version. The market screen title is FarmUpcraft Village Market.

### Automatic roads and railways

`/village routes [villageName]` reads saved survey results; it does not start a
manual survey or bypass the cooldown. The server automatically queues bought
villages, with separate road/rail gates of **24 real hours between attempts**.
It saves the last-checked/start datetime before sampling, the last complete
datetime, next eligible datetime (UTC), segment geometry/endpoints, lengths and
contacts. Interrupted or failed attempts also retain their daily gate.

`/village roads` and `/village railroads` list saved segments, including start/end
coordinates, measured length, compass direction from start to end, and connections
(or `none`). Connections resolve village names, claimed plot types/chunks and
surface-ruin types. Lists show eight segments per page: `/village roads 2` or
`/village roads "Village Name" 2`; named-village and paging forms also work for
railroads. The header includes survey status and last-checked UTC datetime;
incomplete totals are explicitly partial. Listing never starts a new scan.

First discovery of a qualifying road/railway, a new extension of at least six
previously unknown centreline positions, or a new connection notifies all current
members of that village. Online members receive gold chat notices; offline
members' notices are saved and delivered on their next login. Membership is
resolved by home village, not proximity. Persistent geometry/contact history
prevents unchanged daily surveys, junction re-segmentation, small centreline
shifts, or rebuilding a known road from repeating discovery announcements.
Partial-survey discoveries are labelled partial. Consumed inboxes and discovery
history survive restarts; former members do not receive another village's queued
notices. Ordinary new members do not receive historical discoveries retroactively.

Road discovery checks every outward-facing village claim border, tracing inward
and outward from candidates with at least six connected paving positions.
Routes need at least **48 blocks of connected travel**, including their inside
portion. Shorter segments belonging to a qualifying route are retained. Junctions,
claim transitions and surface-ruin contacts split segments; shared sections are
counted once per village/network. Centreline measurement removes paving width;
diagonal and sloped steps use position-to-position distance. A one-block missing
road section is allowed; two missing blocks stop the trace.

Railways are discovered **only in `trainstation` plots** and traced along
matching rail orientations, curves and ascending track. Parallel tracks do not
join sideways, and missing track is never jumped.

Materials include the `farmupcraft:road_surfaces` block tag (paths, gravel,
planks and crafted masonry), all registered slab/stair blocks, Macaw's Bridges
deck variants, and Refurbished Furniture stepping stones. Bridge piers and lights
are not road decks. Tags can extend the default full-block materials. Material
eligibility cannot reliably distinguish a long connected floor/roof/gravel strip
from an intentional road; raw natural stone is excluded by default.

The outward limit is **800 blocks of route length**, plus the connected inside
portion. Sampling is budgeted over server ticks; graph analysis uses a private
off-thread snapshot, never live world reads. Missing chunks are **not loaded or
generated**. Unloaded terrain, distance/work limits and failures are explicitly
incomplete; their partial results do not replace the previous complete measurement,
which remains available as stale. Thus an 800-block/unloaded endpoint is not a
confirmed dead end.

Contacts record other villages' claimed areas and blue claimed plots. Ruins use
actual loaded surface-generation structure pieces at road height, not just a
shared X/Z chunk or a fixed Y=60 cutoff. Underground-generation structures and
vanilla villages are excluded. Results disclose only contact counts, not ruin
coordinates. Surveys currently use overworld claims, consistent with the existing
dimensionless village/plot databases, and have no rewards or upkeep effects.

Inside village boundaries, repeated "You discovered" messages for the same
structure have a two-minute per-player cooldown, including vertical section
crossings while jumping. Different structures and wilderness discoveries
still display immediately. Visited/claimed updates are not delayed.
Entry notices check the structure's covered chunks and full vertical bounding
range, not only its center section. Village lists hide unvisited structures only
when their entire saved bounding box is below Y=60 (legacy records without bounds
use their center). A surface igloo with a basement therefore remains visible.

Middle-click farmland to list inventory crops/seeds plantable under the
target block's biome rules and plot level, with combined inventory/offhand
counts. Level-3+ plots include eligible crops from other biomes in that chunk,
just like planting; Creative and disabled biome rules retain their exemptions.
This only displays a chat list, without creating or moving items.
Middle-clicking an existing crop retains the seed-name/pick behavior.

Right-click a villager with bread in your main hand to toggle following on both
targets. Bread is not consumed, and this click does not open trading or an NPC
conversation. Only the player being followed can toggle it off. Followers walk
using normal pathfinding, stop within two blocks, and wait if their player is
offline, dead, in another dimension, or more than 48 blocks away; they never
teleport. The assigned player UUID survives saves/reloads. Ordinary conversations
temporarily pause following, and toggling it off restores normal villager AI.

LazzyMars and BossPanda96366 receive double positive XP-point gains on both
targets (exact usernames, case-insensitive). This covers orbs and rewards or
commands using XP-point grants. Other players, XP losses, enchanting costs,
direct level grants, and commands setting XP values are unchanged.

`road` is available in `/plot buy road`, `/plot settype road`, and `/plot list`
on both targets. It is a designation only, with no road-specific effects.
Normal plot pricing, village-level type limits, ownership, and protection apply.

`/village structures` hides the internal `exclusives/` prefix in list/detail
names and newly created structure waypoint labels. Stored names, structure
types/IDs, and claim behavior are unchanged; existing records need no rescan.
Waypoints added by clicking those structure coordinates are green; ordinary
waypoint commands retain their gold color. Previously created waypoints are
not recolored automatically.

`/village invite <playerName>` offers online player names with Tab completion
on both targets, including case-insensitive partial-name matching. Invitation
eligibility and acceptance rules are unchanged.

Structure tracking distinguishes the registry type **and** starting chunk on
both targets, so an underground cabin and a surface grave starting in the same
chunk are saved separately. IDs are persisted, with occupied-ID checks; legacy
records keep their IDs, visits, claims, and chest progress. Scans save every
encountered start even when an `upper`/`lower` filter hides it from the results.
After upgrading an existing world, run `/village structures rescan` near missing
structures to recover them from Minecraft's generated structure records. A
Survival player standing inside a recovered structure is marked as having visited
it during the scan; remote unvisited underground structures remain hidden from
village lists. Recovery does not regenerate terrain or reset existing records.

Market item lines show a separate clickable `- sell` when the player has that
item in their sellable inventory. It runs `/market sell item "<itemId>"`,
selling all matching items with the existing market pricing and price changes.
The command rechecks inventory and active-market status when clicked; recipe
links on item names remain unchanged.

The High tab remembers each player's last minimum-price limit in their saved player data,
including across tab changes, closing the market, and reconnecting. New players start at 10 coins.
The Favorites tab lists that player's saved favorite active items across all market categories,
without the High price cutoff. Use the `+` / `-` button beside an item to add or remove it;
hovering explains the action. Favorites persist across reconnects and do not change sale rules.
Updated clients and servers are required together for the market's favorite-state payload.

On both loaders, `/menu` or **Alt+F** opens the general FarmUpcraft menu, with shortcuts
to Village overview, Plot info, Market, Village structures, and Player info / wallet.
The last shortcut shows the existing player details in chat. Change the shortcut under
Controls -> Key Binds -> Miscellaneous -> Open FarmUpcraft menu. The menu does not pause
the game and its buttons retain the normal server-side command permissions.

On both Forge and NeoForge, `/market show2` opens a custom graphical market
(defaults to `general`)
with down/up arrows on the selected High tab to adjust the displayed minimum
price by one coin. Each click requests a fresh server snapshot and returns to
the first page; controls are disabled while waiting and at the price bounds.
Categories match the chat list: `general`, `food`,
`wood`, `stone`, and `high [minCoins]` (default minimum 10).
Compact single-line rows show item icons and current per-item prices (e.g.
`9 coins`), plus `Have XX` only when the player owns some of that item,
sorted by highest price first. **Sell all** sells all matching inventory/offhand
items through the normal server-validated sale command, then refreshes prices,
counts, and the displayed coin balance. A small green/gold "Sale complete"
popup shows "Received X coins" for four seconds after a successful sale.
Its amount comes from a server receipt sent only after wallet credit, not a
client estimate or wallet difference; failed sales do not show a success popup.
The popup survives the automatic market refresh and does not interrupt selling.
Prices can fall during a bulk sale, so
the initial per-item price is not a guaranteed price for the whole stack.
Right-click an icon to show its recipe in JEI; recipe viewing requires JEI,
but the market screen itself does not. The icon tooltip keeps the right-click
recipe hint; the bottom hint and category/sort subtitle are hidden to leave
more room for items. Tabs, page buttons, and Close
provide navigation; there is no manual Refresh button. Sales still refresh
automatically, and reopening the command loads a fresh snapshot.
The existing `/market show` chat interface is unchanged.
The matching FarmUpCraft build must be installed on the client and server.

On both targets, `/village structures2` opens a graphical structure browser
for your home village. Compact two-line rows show visited/claimed status, coordinates, and
distance from your position when opened. Filter tabs select all, claimed,
unclaimed, or unvisited structures. Unvisited surface structures can appear, but unvisited
underground structures remain hidden. The list uses mouse-wheel scrolling, a
draggable scrollbar, and keyboard navigation instead of pages. Filters reset
the scroll position; resizing preserves it. Each row's Map button uses the
existing server-validated stable structure ID and adds a green map waypoint,
requests the village claim overlay, and opens JourneyMap centered on that
structure only after the server validates it and the client adds its marker.
Clickable structure coordinates in chat use the same flow.
Missing JourneyMap is reported in chat rather than silently ignored.
Rows show distances as `135 blocks NE`, using eight compass directions relative
to your position when the screen opens. Row and Map-button hover popups are
removed; distance/direction appear first on the detail line.
The same village membership, underground visibility, and center-chunk claim
rules apply as `/village structures`. Reopen for updated status/distances.

`/plot info2` opens a graphical dashboard for the chunk you are standing in:
colored grouped panels with type and owner together, compact short fields,
and a large top-right abilities panel with fully wrapped text. Narrow windows
stack the panels. The dashboard scrolls instead of paging. Village, wallet,
village level/treasury and permissions cards are omitted; plot level appears
only for upgradeable types (not plain plots, village land or wilderness).
Visitors, protection height, biomes and full farm crop/pasture animal
information remain available. Wilderness is viewable
but has no purchase controls. **Buy** offers plot types to village members on
eligible land; **Change type** is founder-only. Both require a second confirmation
showing the type, location, and coin cost before any command is sent.
Type choices read `farm for 108 coins`. Type changes (including `/plot settype`)
cost half the normal next-plot purchase price, rounded up: 215 becomes 108,
455 becomes 228. Same-type changes and Creative changes remain free.
Purchase and upgrade prices are unchanged.
The server rechecks dimension/chunk, previous type, and the quoted cost before
using the ordinary purchase/type-change handlers. Wallet, village debt,
type limits, adjacency, and Creative rules remain enforced by those handlers.
Successful or rejected actions reopen an authoritative snapshot; errors appear
in chat. The original `/plot info` and `/village structures` chat commands
remain unchanged.

Nursery sections in both `/plot info` and `/plot info2` show a live fruit/nut
tree total and a per-type breakdown (for example, `Almond: 2, Apple: 3`).
Both Forge and NeoForge scan all tagged logs in the current chunk over the
full world height, independent of the viewer's elevation. HarvestCraft tree
fruit/nut blocks (at any growth stage) or vanilla cocoa within one block of
a log, including diagonals, identify its tree. Face-connected logs count
once, regardless of fruit quantity; touching leaves do not merge trees.
Connected timber structures cannot be distinguished from connected trunks.
Trees without any remaining attached fruit are not counted, nor are saplings
or loose fruit away from logs. A mixed-fruit trunk counts once in the total
and once under each attached type. Only logs in this chunk are grouped;
fruit just across its edge is checked only if that neighboring chunk is
already loaded. Viewing never grows, harvests, or changes the trees.

Farm and pasture sections in both `/plot info` and `/plot info2` share the
same detailed server snapshot calculations. Farms include planted crops and
scan height, current Pam harvest-bonus chance, biome planting and trample
rules, growth-speed clarification, crop-help commands, and next upgrade.
Pastures include current count/cap/fullness, animal-type breakdown,
village breeding species/feed, breeding rules, and next upgrade. Milk rules,
shearing bonuses, sheep-dye chance, and egg-hatching restrictions appear only
for applicable selected species or animals present in the pasture; zero drop
bonuses are omitted. Daily limits say "real day". Wool-regrowth and Creative
exception rows are hidden without changing those mechanics. GUI text wraps without requiring hover;
scroll to see longer sections. Viewing does not assign breeding grains or mutate animals.
The pasture scan-height row is hidden in both GUI and chat. Milk and breeding
descriptions omit the adult qualifiers; duck feed says "fish also accepted".
These wording changes do not change animal age requirements.
Farm text uses "Protection at level 2", growth speed "No bonus", and harvest
bonus "None" when zero. The separate harvest-scope row is removed; nonzero
bonuses retain a short Pam/Survival qualifier. Empty visitor lists move onto
the plot type/owner line; named visitors remain in Land & Access.

Pasture animal counts use the plot's saved purchase height (`bought_y`), not
the viewer's or interacting animal's height. The fixed band is purchase Y - 6
through purchase Y + 6 inclusive, across that chunk; an animal's feet/block
position must be inside, so a body overlapping from outside does not count.
The same band gates pasture entry notices, breeding, milking, shearing,
egg hatching/laying permission and level bonuses. Outside the band there are
no pasture bonuses, dye-chance checks, wool-regrowth enforcement or entry notices;
pasture-required Survival interactions and hatching are rejected. Existing
Creative bypasses remain unchanged. Changing type does not move the saved base.
Legacy plots without a saved purchase height retain their existing Y 60 default.

On NeoForge 26.2, `/gamemode` changes only the targeted players, not the
world's default game mode. This prevents a LAN host switching to Creative
from silently putting new and returning guests into Creative when the LAN
game mode follows the world default. `/defaultgamemode` and the explicit LAN
game-mode setting remain deliberate ways to choose the join mode. This
restores the separation already present on Forge 1.20.1. Allow Commands is
independent of game mode; turning it off does not prevent Creative abilities.
If the world default was already changed to Creative before this fix, reset
it once with `/defaultgamemode survival` and select Survival for LAN guests;
the fix does not guess or overwrite an intentionally Creative world.

HarvestCraft cookware tooltips show remaining uses and original capacity.
Capacity is fixed on first use: 500 normally, or 10,000 in a kitchen; moving
plots does not upgrade tools or change warning thresholds (50/500 respectively).
Old tools retain their uses and infer missing capacity on their next use.
Legacy tools at 500 or fewer uses have ambiguous origins, retain the previous
500-capacity estimate, and are labeled as estimated rather than silently refilled.

Enchanting-table coin prices in both targets: the bottom option costs village
level x 100; top and middle cost one-third and two-thirds of that, rounded to
the nearest 50 coins. Players without a village use level-1 prices (50/50/100).
XP, lapis, creative exemption, and village-debt restrictions are unchanged.

Commands are run in-game with the slash prefix. The following index covers the
main command roots and their commonly used actions; argument names, permission
requirements, and available branches can vary by target. Use Minecraft's
command suggestions for the exact syntax in your installed build. Administrative
and testing branches are permission-gated where appropriate.

| Command | Main actions |
| --- | --- |
| `/farmupcraft help` | Player-facing command guide without administrative commands; `/farmcraft help` is also supported. |
| `/farmcraft` | `map`, `waypoint`, `chunk`, `here`, `village`, `frecipe`, settings, scheduler, and admin utilities. |
| `/player` | `info`, `show`, `border`, `color`, village join/leave, player payments, and player/village coin administration. |
| `/market` | `info`, `show`, `buy`, `sell`; operator actions include market configuration, price changes, and daily refresh. |
| `/village` | `info`, `list`, `buy`, `rename`, `levelup`; member invitations, upkeep, farms, animals, structures, chores, and map display. |
| `/plot` | `info`, `buy`, `delete`, `reclaim`, `list`, `upgrade`; manage owners, types, and visitors. |
| `/npc` | Find, claim, and list NPCs; manage profiles and names; initialize villagers. |
| `/scan` | `islands`, `point`, and `clear` for island scans and scan points. |

Named island/lake discoveries now use one center JourneyMap marker, not four
corner markers. Lakes require at least 50 water blocks; islands require at least
40 land blocks. Smaller previously saved records do not produce markers or titles.
Names omit the stored numeric size suffix in chat, map markers, and entry titles
(for example, `Frost-1089` displays as `Frost Island`); saved identifiers are unchanged.
The chat size in chunks is a bounding-box measure, not the block-count threshold.
Successful saved discoveries are shared with everyone in the
same dimension and synchronized on login/dimension change. JourneyMap is optional:
near the surface, entering a discovered feature's bounding box shows a vanilla
title such as "Mist Lake" or "Storm Island", fading over four seconds. Entry
checks run once per second, with a 20-second notification cooldown; the smallest
feature wins overlapping bounds. Underground players more than 16 blocks from
the local surface do not receive area titles. Bounds are approximate rectangular
scan extents, not exact shoreline polygons, and a calculated center can lie over
water. Scan-point discoveries are saved as well as full scans. Creating a village or
leveling it up automatically starts an asynchronous lake/island scan around the
village center with a radius of `25 + 5 * level` chunks (30 chunks at level 1 up
to 75 chunks at level 10), well beyond the green village claim area. Results live under
the current save's `farmupcraft/scans/<namespace>/<dimension>/`.
Legacy Forge global scan files are not automatically assigned to a world.
| `/show` | `biomes`; `/show biomecrops` lists plantable crops for the biome you are standing in, or supply `<biome>` to look up another biome. `/show cropbiomes <crop>` lists suitable biomes. Both crop lookups appear in `/farmupcraft help`. |
| `/structures` | `find`, `list`, `detail`, `chests`, `inside`; manage visited-state markers. |
| `/zone` | `list` and `rename` configured zones. |
| `/managers` | Administrative `clear` and `repopulate` maintenance operations. |
| `/ai` | `test` AI-related behavior. |
| `/database` | Operator `export`, `restore`, and `query` tools for persisted mod data. |
| `/tester` | Development and diagnostic commands; not intended as normal player gameplay. |
| `/farmupcraft-legacy` | NeoForge legacy SavedData preflight. `inspect` only checks a copied legacy data directory; `apply` is deliberately blocked and does not import data. |

### Pasture entry notices

Entering a pasture chunk sends a chat notice with its current animal count
out of 30, colored red and marked **FULL** when at or above the cap.
The notice uses the same nearby-height animal count as existing pasture
breeding checks and `/plot info` (Y - 2 through Y + 3 around the player).
There is a 10-minute cooldown per player, dimension, and pasture chunk;
vertical-only section crossings do not trigger it. Re-entering during the
cooldown does not extend the timer. Cooldowns reset when the server stops.

### NPC conversations

NeoForge 26.2 shows a small camera-facing speech panel above an NPC's name
when you right-click to greet it and when its existing conversation reply
arrives. Only the player receiving that dialogue sees the bubble. Ordinary
chat messages still contain the full reply; the bubble uses a short preview,
wrapped to at most three lines. A new line replaces the old bubble instead
of stacking. Bubbles last sixteen seconds, fade during the last second and
between 12 and 16 blocks away, respect walls, and follow the NPC as it moves.
They are client-only visual entities: no world-save changes, background AI
requests or extra automatic chatter. Changing worlds, disconnecting, losing
the NPC or moving out of range removes them.

To preview the appearance without an AI profile/service, look directly at a
nearby villager and run `/fbubble`, or `/fbubble Your test sentence here`.
The preview is visible only to you and uses the same rendering and lifetime
as real dialogue. Updated NeoForge clients and servers are needed together
for the new speech payload. This first visual prototype is NeoForge-only.
The isolated graphical probe (`src\networkTest\run-player-actions.ps1
-SpeechBubbles -Port 25583`, using freshly prepared network launch exports)
checks the real greeting packet, camera-facing/depth-tested display state,
replacement, wrapping, fading, expiry and distance cleanup, and records
front/side/fading framebuffer screenshots. Test fixtures never enter the
release jar or the user's world.

While an NPC conversation is open, the NPC stops navigating and faces the
player, including while waiting for an LLM reply. The temporary hold ends with
the existing two-minute conversation timeout, moving more than 10 blocks away,
death, logout, changing dimension, or opening a conversation with another NPC.
Normal AI resumes afterward; the NPC's saved `NoAI` flag is never changed.
Villager merchant updates continue during the hold, so earned trade level-ups
are not delayed by chat. Vanilla still waits until the trading screen closes
before counting down its 40-tick level-up timer. Wandering and villager brain
behaviors remain paused; ordinary non-villager NPC goals remain paused too.
If multiple players converse with the same NPC, it faces the most recently
opened valid conversation (UUID order breaks simultaneous ties), and remains
held until no valid conversation remains. Late LLM replies are discarded after
the conversation ends.
On both targets, an NPC remembers each player's UUID in its saved entity data
after delivering a successful chat reply (not an error or busy fallback).
Subsequent clicks randomly select one of ten greetings, such as
"Good to see you, NAME!", "Hello there, NAME!", or "Welcome back, NAME!", instead of
"is listening"; first-time clicks retain the introduction. Clicking alone does
not establish familiarity. This memory survives world reloads and NPC/player
renames, is specific to that NPC and player, and starts with the first successful
exchange after this update; older chat logs are not imported.

### Player help and login welcome

Every login shows a gold reminder: "Use /menu or press Alt+F to see all the options!"
This reminder continues after the new-player welcome period ends.

`/player givecoins <name> <amount>` transfers a positive amount from your wallet
to another online or previously recorded offline player. Insufficient funds,
self-payments, and recipient balance overflow are rejected without changing
either wallet. Operators use `/player admin grantcoins <name> <amount>` to adjust
another wallet without paying from their own; `/player admin givecoins <amount>`
still adjusts their own wallet.
Online player names autocomplete in both `givecoins` and admin `grantcoins`.

Admin commands require operator-level permissions. Minecraft's Open to LAN
**Allow Commands** option grants those permissions to all LAN players, so leave
it off to keep visitors from seeing or using admin commands.

On login, players see "Welcome to FarmUpCraft!" and a pointer to
`/farmupcraft help` during their first five calendar dates, counted using the
server's local date (first login date plus the next four dates). The start date
is saved per player and does not reset on restart or reconnect. Existing players
start their window on their first login after this update. This works in both
targets, on dedicated servers and in single-player worlds.

### Initial market setup

Underlined market items open their recipes in JEI when clicked. These links use
plain-text tooltips with the item ID and a click hint, avoiding item tooltips
that can intercept chat clicks. Items without crafting recipes keep item tooltips.

Both targets automatically import the bundled item lists for empty market
categories at server startup and activate an initial selection when a category
has no active goods. Existing goods, prices, and sales counts are preserved;
restarting does not trigger daily rotation or price increases. Items from
companion mods that are not installed are skipped with a warning.

Operators can manually import missing items with `/market admin importall`,
or just one category with `/market admin importtxt <type>` (`food`, `wood`,
`stone`, or `general`). These imports add missing goods without overwriting
existing entries. `/market importall` is not a valid command.

### Naturalist raw duck ingredients

Naturalist raw duck (`naturalist:duck`) counts as raw meat and raw chicken for
HarvestCraft recipes in both versions, including the nested chicken ingredient
tags. Entries are optional, so Naturalist is not required. Cooked duck is unchanged;
recipes requiring the exact vanilla chicken item rather than a tag are unchanged.

### Temporary Ice and Fire stone-statue compatibility (NeoForge 26.2)

An optional client-only mixin fixes the unofficial Ice and Fire beta's stone-statue
render crash by assigning the render-only hollow entity its owning statue's ID.
Minecraft 26.2 requires an assigned ID when extracting held-item render state.
The model is not spawned or registered, and no statue or saved entity is removed.
The patch is inactive when the targeted renderer is absent; Forge 1.20.1 is unchanged.
This is a temporary workaround for `iceandfire-2.2-mc26.2-unofficial-beta.1`,
not a permanent FarmUpCraft feature. Remove the mixin, its client registration,
`StoneStatueRenderCompat` helper, and `stone_statue_model_id` game test once an
upstream Ice and Fire update fixes ID assignment. Before removal, verify the
updated mod renders the previously crashing statue without this workaround.

### Temporary Ice and Fire dragon-prey compatibility (NeoForge 26.2)

An optional mixin works around a second crash in
`iceandfire-2.2-mc26.2-unofficial-beta.1`: after the prey-shaking animation
passes tick 55, `updatePreyInMouth` casts the client world to `ServerLevel`
to apply bite damage. Only that completion branch is skipped on the client,
before the cast. Earlier mouth positioning/animation and server-side damage,
healing, hunger and passenger release remain unchanged. No entities are removed.
Forge 1.20.1 is unchanged, and the patch is inactive without the targeted mod.
Remove `IceAndFireDragonPreyMixin`, its registration and the
`dragon_prey_bite` game test after verifying an upstream client-side fix.

### HarvestCraft skillet recipe

Both targets override `pamhc2foodcore:tool_skillet` to avoid the spear recipe
collision. Use two copper ingots in the top-left and top-middle slots, with
wooden sticks in the center and bottom-right slots. This crafts one skillet;
the original single-copper layout no longer crafts a skillet.

### Copper golem reach

NeoForge copper golems can interact with containers two blocks above their standing
feet position, rather than only one. Their existing eight-block vertical search,
horizontal interaction distance, line-of-sight checks, and sorting rules are unchanged.

### Mineshaft ore replenishment

Mining a natural ore inside a mineshaft's full structure bounding box in Survival or
Adventure has a 10% chance to create one ore block within 48 blocks horizontally
(three chunks) and within Y +/-6. Each registered ore block type has equal odds,
including compatible `c:ores` / `forge:ores` tags; stone/deepslate variants are
separate block types. Only loaded natural stone/deepslate is replaced, never air,
containers, existing ore, or tracked player-built stone. The destination need not
be inside the mineshaft. If 128 candidate positions find no safe host, nothing spawns.
Each replenishment attempt has a 50% chance to require a host touching air on one
of its six faces; the other half have no air-contact restriction and may also touch
air by chance. No fallback removes the requirement on air-facing attempts.
Successful spawns tell the mining player a lucky-mine message, cycling through ten
different messages. Failed attempts do not show a success message.
Player-placed ores are tracked persistently per dimension from this update onward
and cannot trigger replenishment; older placed ores cannot be identified retroactively.
Piston movement preserves placement exclusions conservatively. Only completed,
uncanceled player mining counts, not explosions or mob block destruction.

### Hard-mode bundle carrying

On Hard difficulty, players already carrying a bundle cannot acquire another through
ground pickup, container clicks/shift-clicks/hotbar swaps, crafting, or trading.
This includes colored bundles and bundles in the offhand or on the inventory
cursor. A blocked item stays intact in its source, with an action-bar warning:
"It's too heavy to carry another bundle." Warnings are limited to once every three
seconds. Other difficulties and acquiring the first bundle are unchanged.
Blocked transfers leave the bundle in its source; blocked crafting/trading does
not consume ingredients, payment, or offer uses. Moving or storing your owned
bundle and exchanging it for another are allowed. Existing extra bundles are
not deleted. Administrative grants and items inside other container items are
not covered by this inventory-acquisition rule.

### Hostile retaliation

Hostile mobs hit by a player gain retaliation obstacle behavior while targeting that
same Survival/Adventure player. If they cannot reach the attacker, they roll a 20%
chance once every 40 ticks (2 seconds) to break one nearby obstructing block or jump
and place stone beneath themselves toward an elevated attacker. Each hit permits at
most one successful action: either one block broken or one pillar block placed.
After that, another hit is required; repeated hits before an action do not stockpile
actions or bypass the two-second interval. Failed rolls or blocked actions do not
consume the permission. There is no vertical pursuit limit beyond the world's build
height, but each pillar block requires a fresh hit and the mob must retain its target.
Only nearby obstacles directly between the mob and player are considered. Containers,
block entities, fluids, and unbreakable blocks are excluded. Village plots do not
provide an extra exemption. Mob-griefing and loader block-change permission hooks
still apply; jumping requires clear headroom and never replaces an occupied block.

### One-time gameplay defaults

Both JARs bundle a Spice of Life: Onion 62-food progression. Diversity requirements
for the eight rewards are **20, 25, 27, 33, 38, 50, 55, 61** (reward effects unchanged).
This adds 10 to every previous requirement and another 10 to the last three.
When Onion is
installed, FarmUpCraft merges the tracking limit and eight rewards into its global
config during mod construction, before Onion initializes. Other options are kept.
The original config is backed up under `config/farmupcraft/`; the
`solonion-progression-v2.done` marker there prevents overwriting later admin edits.
Existing v1 installations receive this update once, with a separate
`solonion-before-progression-v2.json` backup; unrelated config options are preserved.
This applies to all worlds in that instance, including dedicated servers.
Onion remains optional; installing it later triggers the setup on the next launch.

NeoForge normalizes Onion's total-saturation scoring to the Forge 1.20.1
saturation-modifier formula. Keep `complexityStandardSaturation` at **6** in
modern Onion (equivalent to **0.6** in legacy Onion); do not copy 0.6 into the
modern config. Nutrition, effect modifiers, explicit food-diversity overrides,
disabled-diversity mode, history decay, and reward thresholds are unchanged.
This optional mixin only affects Onion scoring, not food hunger/saturation.
Existing food history is preserved; recalculation can lower the score and
remove rewards that are no longer earned. Forge scoring is unchanged.

NeoForge also corrects Onion 1.5.5 lunch-container menus to access the actual
bag/box inventory slot rather than the menu ID. Lunch bags and both lunch boxes
can open regardless of how many menus have been opened, including from the
offhand, without accessing another item's slot.

Each existing or new world also receives random tick speed **1** and sleeping
percentage **50** once on server startup. A marker at
`<world>/data/farmupcraft-startup-rules-v1.done` preserves later gamerule changes.
Do not delete these markers unless you want to reapply the defaults.

FarmUpCraft still randomly attempts to remove regeneration, less often at higher
experience levels. "Your regeneration has faded." appears only when removal
succeeds. These random fades override effect-removal protection, including
Onion's earned food regeneration; ordinary removals such as milk keep their
normal protection rules. Onion can grant regeneration again when it recalculates
food rewards.

NeoForge disables the vanilla uncolored bundle crafting recipe. Existing bundles
and colored-bundle recipes are unchanged. Forge 1.20.1 has no vanilla bundle recipe.

### Structure visits and claims

Creating a village or successfully leveling it up automatically queues missing
AI profiles for loaded villagers in that village's chunks. Existing files and
pending requests are skipped; generation is staggered and uses the existing AI
retry flow. This does not force-load chunks. Villagers born or arriving later
are covered by the next level-up scan or `/npc initvillagers`, not by a
birth/arrival profile-generation hook. The configured AI service is required.

Coordinates in `/village structures` lists and structure details are underlined
and clickable: click them to add a personal JourneyMap waypoint at the structure
center, named after the structure. No operator permission is needed. The click
uses the stable structure ID and rechecks village membership and structure
availability, so moving or reordering the list cannot select another structure.
This adds a green waypoint and opens JourneyMap centered on it; it never
teleports the player. Ordinary `/farmcraft waypoint` commands still only add a
waypoint without opening the map.

Both targets require a structure to be visited before it can be marked claimed.
Buying a village claims territory, not its structures: only the village-center
chunk counts as a purchased plot at founding. A visited structure's saved center/
marker chunk must itself be a purchased (blue) plot to count as claimed.
Purchased chunks overlapping only the structure's edges do not count. Discovering or scanning
an unvisited structure never claims it; visiting it later on an already-bought
plot marks it visited and claimed. Creative-mode exploration still does not
mark structures visited.

Structure scans and global/village structure lists correct and save stale claim
flags using these rules without changing visit history. Ordinary village
territory does not count as a purchased plot.

### Zombie and skeleton torch breaking

Both targets let vanilla zombies and skeletons seek out and break normal and
soul torches (standing or wall-mounted). Redstone torches, lanterns and other
blocks are not targets. Enabled by default; operators can toggle it immediately
with `/farmcraft admin settings mobTorchBreaking true` or `false`. The setting
is saved per world and shown by `/farmcraft admin settings`.

Idle mobs search at most once every 100 ticks (about five seconds), normally
within six blocks. Exactly 1% of searches use a sixteen-block radius instead.
They require a visible, reachable torch, walk to it, and break it only within
two blocks. Combat takes precedence. Searches never load chunks and time out
after ten seconds. The behavior respects `mobGriefing` and block-destruction
event vetoes, but does not exempt village claims.

### Crop lookup autocomplete

The crop lookup arguments support Tab completion: `/show biomecrops ` suggests
all biomes registered in the current world, including loaded mod/datapack biomes;
`/show cropbiomes ` suggests crop item IDs from the configured biome crop rules.
Namespaced IDs can be entered directly without quotes.
`/show biomes <x> <y> <z> <radius>` scans surface biomes at each column's
motion-blocking height (ignoring leaves), not underground biomes at Y=0.
The supplied Y coordinate does not reveal caves beneath the scanned terrain.

### Villager ladders

In both targets, villagers no longer get the automatic upward ladder boost
when pushing against a wall. They retain normal slow ladder descent and can
walk off at the bottom; no teleporting or forced relocation is used. This
applies to adults and children, including ladders extending `LadderBlock`.
Use stairs for villager access between floors. Player climbing, other mobs,
vines, and scaffolding are unchanged.

### Villager sleep healing

In both targets, living villagers heal to their own maximum health immediately
when they enter bed to sleep, including villagers with boosted maximum health.
This does not require village membership. Remaining asleep does not continuously
heal damage; players and other sleeping entities are unaffected.

### Villager household signs

In both targets, loaded villagers with an assigned bed in a bought village get
an oak wall sign outside their home's nearest reachable exterior doorway.
Signs list each resident's name and profession (or `Child`), with two residents
per four-line sign and additional signs beside the same door for overflow.
Existing villagers are included; newborns wait until they receive a bed.

The initial check is staggered within about ten seconds of loading; subsequent
full checks run at most once every 144,000 world ticks (two hours at 20 TPS).
The next-check time is saved on the villager, so reloading does not bypass it.
A lightweight staggered timer gate runs every 200 entity ticks without inspecting
beds, signs, village records, or paths until the full check is due.
Purchase eligibility, new bed assignments, moves, and name/profession changes
are detected at the next scheduled check, so they can take up to two hours.
A bounded walking search
starts beside the bed at the same height or one block up/down, can pass through
hand-openable interior doors and use steps,
and rejects blocked exterior approaches. Nearby open sky identifies the exterior;
the doorway and sign may be under a porch. The search spans 24 blocks horizontally
and twelve vertically from the bed (including downstairs entrances reached by
stairs); sky must be reachable within eight blocks of
the outside approach. Signs use the wall to the right when viewed from outside, skipping both halves of
double doors. Each wall column tries the upper position then directly below it,
so an existing upper sign is preserved and the new sign goes underneath. Overflow
uses the next right-hand column, up to three columns beyond the door edge. Support
must be a sturdy wall face, never a door or trapdoor, in the same bought village. No chunks
are force-loaded. Unsuitable entrances or full sign space are logged and marked
done using the same per-bed flag as successful placement. There are no placement
retries at that bed, even if the entrance or sign space is later improved.
Each scheduled check logs bed/sign coordinates and whether the saved sign is
present, missing, or unloaded. Permanently skipped beds get a diagnostic-only
entrance search, never a placement retry. Diagnostics include visited nodes,
seed count, door/approach rejection counts, unloaded/height/budget limits, and
bed-adjacent feet/head/floor block states (including modded IDs). Placement logs
record the chosen door, outside direction, sign position, and route distance.

The per-bed placement flag is saved on the villager, and shared resident entries
are saved on the sign. Removing a sign does not recreate it at the same bed.
Changing beds removes that resident from an unchanged old sign, deletes the sign
only when it becomes empty, and permits a new entry at the new home. Cleanup waits
for the old sign's chunk to be loaded. Names/professions refresh only on unchanged
generated signs. Player edits to either side, color, glow, wax, or facing protect
the sign permanently from automatic updates or deletion once detected.
Unchanged generated signs attached to a door (or directly above one) get a
one-time relocation attempt on the resident's next scheduled two-hour check.
Shared entries move individually, preserving residents not checked yet and
leaving player-edited signs untouched. No live-world repair command or faster
polling is added; a failed relocation is also marked done for that bed.

### Moving walls and buildings

Both targets provide `/build pull_wall <distance>` and
`/build push_building <distance>` to all players who can build. Run the command,
left-click one bottom corner, then right-click the other bottom corner. The two
blocks must be at the same height on a straight X/Z line. Stand in front of the
wall: pull moves toward your side, push moves away. The second click creates a
preview only: red particle outlines mark the source, green outlines mark the
destination, and a client-side ghost-block preview highlights the source blocks
to be removed in red while rendering semi-transparent destination blocks at the
target positions without changing the world. Chat shows the price and
clickable Confirm/Cancel actions. Run `/build confirm` (or click Confirm) to move;
`/build cancel` cancels without charging. Starting another build command replaces
the old preview. Selections/previews expire after two minutes or on logout/
dimension change. Ghost blocks clear immediately when confirmed, canceled,
expired, or on logout; remaining particles fade naturally.

The wall is one block deep and 15 blocks tall including the bottom row.
Buildings are also 15 blocks tall; depth is found by tracing back along both
bottom edges independently to their first dirt, plain stone, or air block.
The longer edge determines depth, capped at 20 blocks including the front row;
an edge does not resume after its first stopping block. This is a
rectangular selection, not an automatic outline of the building.
Build selection logs include dimension, both selected corners, movement direction,
inclusive source/destination bounds, size, offset, and the exact bottom-edge blocks
that stopped depth detection. Block-entity overlap logs identify the destination,
incoming source block, and whether the destination is inside the moving source.
Block entities inside the source may overlap because they move too; only
stationary block entities outside the source block the move.

Both commands also include supported attachments one block outside the selected
box, such as wall torches, banners, signs (including hanging signs), ladders,
buttons, levers, lanterns, rails, and plants. Support is checked using each
block's survival rules, including modded blocks implementing those rules.
Unrelated neighboring blocks are not added to the move. Attachments extending
farther out, or attachments that would lose their support at the destination,
cancel the move and ask for a larger selection rather than dropping items.
Sign text on both sides, colors/glow/wax, banner patterns/names, and other block
entity data are preserved. Paintings, item frames, and glow item frames move
with their backing blocks, retaining their identity, artwork, contents, and
rotation. Their entire backing must move or have identical support at the new
location. Occupied decoration destinations are rejected.

Pull wall costs 1 gp (one wallet coin) per non-air source block. Push building
costs one-quarter as much: 1 gp per 4 non-air source blocks, with the total
rounded up (5 blocks cost 2 gp). Both prices are independent of movement
distance. Included attachment blocks count toward the price; hanging entities
do not add a separate charge. Creative-mode moves are free and do not require
a wallet; previews show a zero cost. For both wall and building moves, preview
chat shows only the total cost and clickable Confirm/Cancel controls. Switching between
creative and survival requires a new preview because the price changes.
The total is displayed after selection; an
insufficient balance cancels the move without changing blocks. Failed or canceled
moves are not charged. Successful moves deduct and save the cost and display the
remaining balance. Confirmation rechecks permissions, funds, loaded chunks,
world limits, and destination block entities. Any change to source/destination
blocks, block entity data (including inventory), or traced depth boundary
invalidates the preview and requires reselection. Nearby hanging decoration
changes, including adding/removing a painting or changing a frame's item or
rotation, also require a new preview. Walking around the building
does not change the previewed movement direction.

Building and wall widths are both limited to 20 blocks, including both selected corners.
Both push and pull movement distances are limited to 1-10 blocks per operation.
Both corners must be within 64
blocks of the player. All affected blocks must be loaded and inside the world
border/height limits. Source blocks move rather than duplicate, including block
entity inventories/data. Overlap is supported. Destination blocks (including
those under source air) are overwritten, but a destination block entity outside
the source region cancels the entire operation to avoid deleting its contents.
Every affected block is preflighted for break/place events, normal editing
permissions, and the existing village/house protections before moving anything.
Permission probes skip FarmUpCraft's harvesting rolls, bonus drops, and chest/
spawner traps: moving a block is not harvesting it.
Other entities (including villagers, mobs, and armor stands) and claim ownership
are not moved.

Village map overlays automatically turn on when a player founds a village,
accepts a village invitation, or is added to a village by an administrator while
online, in both targets. Use `/village map clear` to hide the overlay or
`/village map` to show it again. Each player's on/off choice is saved and restored
on login, including after a server restart. Clearing all map overlays also saves
the village map as off; plot refreshes respect that choice. Existing player
records without a saved choice default to off until enabled. Village upgrades
refresh overlay tiles for all online members whose village map is enabled,
including the newly added territory. JourneyMap is
required to display the overlay.
The village center always uses purchased-plot blue, including when its chunk
record is missing or still marked as ordinary village territory. Other ordinary
village territory stays green; purchased plots are blue.

`/show biomecrops` and `/show cropbiomes` autocomplete short registry names
(for example `plains`, `orchard`, `onion`, and `wheat`) while resolving full IDs
internally. Ambiguous short names remain namespaced in suggestions. Explicit
namespaced IDs and existing command aliases remain accepted in both targets.
`/show plotcrops` lists every biome across the current 16x16 chunk at the
player's farmland height (feet minus one), followed by one combined, sorted,
deduplicated crop list. Green means a matching crop or seed is in inventory;
yellow means absent. This is informational: ordinary farms still restrict
planting to the local biome, while level 3+ farms allow the chunk's biome union.
Crop-and-seed pairs appear only once in autocomplete; explicit seed IDs still work.
In "Crops you can plant in" lists, crop names are yellow by default and green
when the matching crop/planting item is anywhere in the player's inventory,
including either hand. This applies to
the command and rejected-planting feedback, not the separate biome-name list.
Edible-seed crops retain their crop names (`mustardseeds`, `sesameseeds`); their
extra planting-seed variants do not become duplicate suggestions.

Every five minutes of server ticks, while two or more players are online on Hard
difficulty, all online operators with game-master command permission receive a
reminder to consider `/difficulty normal` for the kids. Difficulty is never changed
automatically. The existing solo reminder to consider Hard remains unchanged.
Both difficulty reminders exclude usernames ending in `bot` (case-insensitive,
including BridgeBot) from player counts and recipients. One human plus a bot
therefore uses the solo reminder, not the multiplayer reminder.

HarvestCraft harvested crops with a matching plantable seed item can also be
planted directly (for example `kaleitem` and `kaleseeditem`). Placement uses the
seed's normal block-item path with the original held stack, preserving biome
checks, protection events, survival checks, and consumption in both targets.

## Development targets

NeoForge 26.2 adds compatibility recipes for SoL Onion 1.5.5's lunch bag and food book to
replace its invalid `food` crafting category with `misc`. Ingredients and
outputs are unchanged; recipes load only when SoL Onion is installed.
Separate FarmUpCraft recipe IDs preserve the early mod loading needed for
Onion startup defaults. Upstream invalid recipes may still log category errors,
but the compatibility recipes provide the same crafts.

Bee and Naturalist butterfly crop fertilization shares a persisted per-chunk
budget in each dimension: up to five successful one-stage boosts in any 6,000 world ticks
(five minutes at 20 TPS). Butterflies add one stage instead of their usual
2-5 stages. More insects do not increase the shared limit; relogging and server
restarts do not reset it. The limit covers all chunks, not just bought farms,
so moving insects outside a village does not bypass it. Normal random growth
and player-used bone meal are unchanged. Naturalist integration is optional.

Naturalist ducks have their own `/village setanimal duck` breeding category;
goats use the sheep category. Ducks accept either their unchanged native
Naturalist food choices (fish) or one village-specific assigned grain.
The grain assignment is stable and saved without rerolling other species.
Both follow the existing own-village pasture requirement, pasture
animal cap, village species/feed selection, and once-per-real-day breeding
limit. Ducks also qualify for pasture animal drop bonuses. Creative-mode
breeding remains unrestricted.

The plot spawn filter exempts Naturalist ducks (including egg hatchlings) and
offspring produced by non-canceled breeding events. Breeding provenance is
saved on the offspring so reloading does not remove that exemption. Lycanites
are never exempt, even if bred; all their existing spawn/light/drop rules are
unchanged. Other non-breeding modded spawns keep their existing restrictions.
These exceptions do not bypass pasture/feed/daily breeding checks or other
mods' spawn cancellations.
Thrown chicken and Naturalist duck eggs can hatch only when the hatchling's
position is inside a pasture plot. Wilderness, ordinary village territory and
other plot types do not allow egg hatching. Eggs still break normally and keep
their native hatch chances inside pastures, but each hatchling must fit within
the existing 30-animal cap (same chunk and five-block-high counting band as
breeding). At 29 animals, even a four-chick egg can add only one; at 30 or more,
none hatch. This does not change breeding,
natural animal spawns or Lycanites rules.
When a chicken or duck egg hits a full pasture, its player thrower receives one
red warning with the current animal count, even if the egg's hatch roll fails.
Dispenser-thrown eggs have no player to notify.
Chicken and Naturalist duck egg laying is allowed in pasture plots and
wilderness only, not other village plots. Blocked laying attempts reset the
normal egg timer without dropping an egg; dropped player items are unaffected.
This does not change the separate thrown-egg hatching restrictions.
Spawner-origin mobs bypass FarmUpCraft's custom spawn suppression in both
the finalize and world-join checks, inside and outside villages. This includes
Lycanites from spawners and NeoForge trial spawners; natural Lycanites spawns
keep their existing restrictions. NeoForge regular cage spawners ignore the
standard monster darkness requirement, so zombie and skeleton spawners work in
lit rooms. Natural spawns still require darkness; trial-spawner behavior and
explicit custom spawner light rules are unchanged. Difficulty, space,
player-distance and nearby-entity checks still apply. Forge retains its vanilla
spawner lighting behavior.

Rejected planting immediately resends the clicked/placement blocks and the
player inventory, correcting client-predicted ghost crops and missing items.
Biome planting rules apply to both hands.
Planting checks use the supporting farmland block's biome Y, including
level-3 chunk-wide crop checks. `/plot info`, `/plot info <biome>` and
`/show plotcrops` sample at the block supporting the player, accounting for
farmland's fractional height. Stand on the farmland being checked; commands
do not infer the height of a different field while standing elsewhere.

Ground plant-eaters seek growing crops within eight blocks and destroy one
without drops at most every 1,200 world ticks (60 seconds at 20 TPS). This
includes vanilla livestock, rabbits, turtles, pandas, camels and sniffers, plus
Naturalist ducks, ostriches, elephants/mammoths, deer, boars, capybaras, zebras,
giraffes, hippos, rhinos, tortoises, turkeys and rats. Predators, insects and
flying birds are excluded. CropBlock crops (including HarvestCraft) and blocks
in the Minecraft crops tag are eligible at any growth stage. Animals need a
reachable path and an unobstructed bite; fences prevent eating through them.
The per-animal cooldown is saved, mobGriefing and block-destruction hooks are
respected, and ridden/riding animals and animals in love do not seek crops.
Existing rabbit carrot-raiding and Naturalist rat crop-harvesting goals are
replaced so they cannot bypass this shared behavior's per-animal limit.

The targets have separate Gradle wrappers/build roots; open the appropriate
target directory in your IDE rather than importing the repository root as a
Gradle project. Each target includes the shared `common` Java 17 subproject.
Common classes and resources are packaged in each mod jar, so no extra common jar is needed.

| Target | Directory | Java target | Gradle |
| --- | --- | --- | --- |
| Minecraft 1.20.1 / Forge | [forge-1.20.1](forge-1.20.1) | 17 | 8.1.1 |
| Minecraft 26.2 / NeoForge (partial port) | [neoforge-26.2](neoforge-26.2) | 25 | 9.2.1 |

The Forge code, resources, wrapper, and development run directory now live
under `forge-1.20.1`. The NeoForge target has currency and crop-rule
codecs/registries, crop/biome data loading, biome-rule persistence, database
storage/query/backup infrastructure, database synchronization payloads,
player/goods/market/world/village/NPC models, all eleven gameplay managers,
fourteen gameplay server command roots plus a legacy-data preflight root,
login/scheduling handlers, and JEI/JourneyMap
command and waypoint payloads. Client HUD/borders, weather/tooltips, and the
enchantment payment gate are also ported. Standalone data generation, populated
database restart/backup/restore, and connected database synchronization now pass
development acceptance. Visual gameplay, old-world migration, broader gameplay
regression, and startup stability remain incomplete.

### Build Forge on Windows

Run Gradle with JDK 17; do not upgrade this target to Java 25. For example,
from the repository root, using the portable JDK installed on this machine:

```powershell
$jdk17 = Join-Path $HOME '.jdks\jdk-17.0.20.1+1'
Push-Location .\forge-1.20.1
try {
    .\gradlew.bat "-Dorg.gradle.java.home=$jdk17" build -x copyJar
} finally {
    Pop-Location
}
```

Use your own JDK 17 installation path on other machines. The explicit Java-home
argument also overrides any stale `org.gradle.java.home` in the user-level
Gradle settings, without changing settings used by other projects.
For IDE use, select JDK 17 as the Forge project's Gradle JVM.

`-x copyJar` prevents the existing Forge build from copying the jar into the
live Minecraft mods directory. Omit it only when intentionally deploying.
Artifacts are written to `forge-1.20.1\build\libs`.

### Build NeoForge on Windows

The NeoForge 26.2 jar also supports Minecraft + NeoForge + FarmUpCraft alone.
HarvestCraft's four modules, Biomes O' Plenty, TerraBlender and GlitchCore are
optional integrations, not mandatory FarmUpCraft dependencies. Their version
constraints still apply when installed, and companion mods can have their own
dependencies. JEI and JourneyMap remain optional; commands that need their UI
report the missing integration instead of loading its API.

Mod-specific datapack content is conditional and optional tag entries are
ignored when their content is absent. Vanilla village, economy and crop
gameplay remain available; installing fewer mods does not add replacement
HarvestCraft crops or BOP biomes. When none of the bundled food-market items
are installed, market initialization explicitly imports vanilla foods at the
usual starting price, so daily market rotation still works. The normal
HarvestCraft food list is unchanged when its items are available.
This is not permission to remove content mods
from an existing modded save: blocks, items and biome/worldgen references in
that save can still require those mods. Use a new world or a disposable copy.

Development runs include the usual content bundle by default. Use
`-PbareNeoForge=true` to omit companion mods from development runtime
classpaths (it does not change what is bundled in the production jar):

```powershell
.\gradlew.bat "-Dorg.gradle.java.home=$($env:JAVA_HOME.TrimEnd('\'))" -PbareNeoForge=true runBareGameTestServer
```

This dedicated acceptance test rejects any runtime mod other than Minecraft,
NeoForge and FarmUpCraft, creates a world, checks village/market registration
and databases, runs daily market rotation, and exercises a real vanilla wheat right-click failure with no
harvesting mod. Use `runClient -PbareNeoForge=true` for a bare graphical
development client. The flag is unnecessary for an installed jar: simply
install only the mods you want.

Bare compatibility was verified with an actual dedicated world test and a
separate graphical client connected to a loopback dedicated server. Both
inventories contained only Minecraft, NeoForge and FarmUpCraft; startup and
datapack loading had no ERROR entries. The non-operator survival client moved,
broke dirt and placed dirt through normal game action paths, with server
confirmation and clean exits. Full-content regression tests also passed for
Harvest With Ease ground crops/tree fruit and bundled recipes/tags.

From `neoforge-26.2`, run `.\gradlew.bat build`. The Java 25 compilation
toolchain can be provisioned by Gradle. Gradle 9.2.1 itself can run with JDK 21.
If a user-level Java-home setting is stale, explicitly override it:

```powershell
Push-Location .\neoforge-26.2
try {
    .\gradlew.bat "-Dorg.gradle.java.home=$($env:JAVA_HOME.TrimEnd('\'))" build
} finally {
    Pop-Location
}
```

This example assumes `JAVA_HOME` points to a valid JDK supported by Gradle 9.2.1.
Artifacts are written to `neoforge-26.2\build\libs`.

The development runtime now includes verified 26.2 releases of all four
HarvestCraft modules, Biomes O' Plenty, TerraBlender, GlitchCore, JEI, The One
Probe, JourneyMap, Villager Names, and Collective. Versions/file IDs are pinned
in `neoforge-26.2/gradle.properties`. These companion mods are not bundled in
the FarmUpCraft jar; installing that jar does not install a complete modpack.
The development client has loaded these mods successfully, but their presence
does not mean all FarmUpCraft integrations or gameplay features are ported.

Use the same Java-home override with `runClient` instead of `build` to launch
the development client, or `test` to run the automated tests. Both builds run
the common-module tests as part of `build`. For a filtered NeoForge test,
use `:test --tests <class-name>` so the filter does not also apply to common.
NeoForge tests use ModDevGradle's FML-aware JUnit support. Registry tests use
NeoForge's ephemeral server fixture to verify real item codecs, serializer
registrations, HarvestCraft lookups, and the loaded bronze coin value.
They also check migrated crop/biome resources and registry-aware saved-data
roundtrips. The latest full validation discovers 528 NeoForge tests: 525 pass, with
zero failures/errors and three platform-dependent legacy-path tests skipped
(Windows colon filenames and unavailable symbolic-link privileges). All fourteen
common tests pass without skips. The skipped cases are not verified coverage.
The fixture does not supply a `ServerLevel`; world-dependent behavior is
checked separately by dedicated development-server acceptance and sixty
actual-world GameTests.

Run `.\gradlew.bat runGameTestServer` with the same Java-home override to run
those GameTests. They cover refinery transfer, ineligible plots, missing
buckets, full-output drops, repeated-notification safety, villager fall
immunity, shared overworld database routing from the Nether, registration of
all fifteen server command roots and database/legacy-preflight permissions, and real
`save-all flush` JSON exports. The export test seeds a player and decodes the
exported record to verify its exact 137-coin balance before removing the fixture.
Four additional enchantment GameTests exercise the real enchanting menu:
successful payment, insufficient coins, creative exemption, and rejected
button/lapis/XP requests. They assert actual applied enchantments, balances,
lapis consumption, and vanilla XP behavior, not just payment helper results.
Additional cases cover village surcharge/debt and reservation refunds, actual
cow interactions and sheep entity ticks, and real HarvestCraft crafting-menu
tool exhaustion/kitchen remainders. The sheep case waits for actual entity
ticks rather than assuming a newly spawned entity ticks within two server ticks.
Four further cases cover house-owner breaking/loot, visitor invitations and
revocation/membership, stone-drop restrictions, and nursery leaf drops through
production interaction/event paths. Seven more cases exercise crop startup
caches, the focused production tag-refresh listener, Nether/End SavedData
isolation versus overworld routing, and villager reincarnation/death identity,
loaded/unclaimed isolation, and forever-kid aging. The refresh test invokes the
production listener with real registries, not a full datapack reload or synthetic
global reload-event dispatch. All 32 cases passed with default background
parallelism.
An additional real-level test verifies optional-integration command feedback
using API-blocking isolated production-bytecode copies. Three companion JUnit
tests check actual API absence, negative linkage controls, client payload
registration/dispatch, logout guards and recipe-command fallback. These are
focused handler-boundary tests, not complete absent-mod client discovery or
graphical startup acceptance. Separate real absent-mod acceptance is described
below. The earlier combined build/test run passed all
33 world tests using the process-scoped diagnostic thread setting noted below.
The subsequent full combined build, including the gated visual helper source
set, passes all 38 world tests. Five new cases cover structure-note contents and
idempotent cache use, visited-barrel block/inventory drops, farm-only hoe
durability and berry consumption, and actual crop planting under biome rules
and disabled enforcement.
Four further cases pass actual non-operator dispatcher rejection of seven
sensitive commands, treasury deposits/withdrawals and insufficient-funds
immutability, and production upkeep scheduling/accounting. The upkeep cases
invoke the production scheduler listener with primed elapsed time: exact timer
boundary/reset, member deduplication, debt, offline exclusion, same-day
idempotency, and ownership-filtered structure income are asserted. These are
real-level scheduler-listener tests, not full login or global event-bus
registration coverage. Those 42 world tests pass with the scoped diagnostic
background-thread setting.
Eleven further parity cases bring the passing world suite to 53: three recipe
cases check all 28 legacy definitions (16 active, 12 disabled), loaded manager
selection, seven real crafting-menu outputs/remainders, and stonecutting; seven
loaded loot-table cases check custom wheat and all six garden overrides,
including deterministic Fortune, maturity, Silk Touch and explosion behavior;
one dispatcher case checks both skeleton-command spellings, permissions,
actual entity counts and cleared AI goals. FarmUpCraft loads after NeoForge so
its recipe overrides win over NeoForge's compatibility recipes.
Seven external-datapack cases bring the world suite to 60: loaded tree/garden
bindings, deterministic generation and rarity rejection, real oil crafting
with rolling-pin durability/remainder, additive fish tags, and conditional
Pipez recipes. All 60 pass both without Pipez and with Pipez 1.2.32+26.2.
The runner creates/replaces a disposable world under `build\gameTest\world`,
not the normal development world. Results are in `build\gameTest-results.xml`.
Test code/data lives in `src/gameTest` and is not included in the release jar.

The database building blocks include all 13 active serializers and JSON/gzip
NBT import/export strategies. The NeoForge `DataSerializer` interface takes an
explicit `HolderLookup.Provider` for encoding and decoding so dynamic registry
references use the correct world context without requiring a live level.
Valid legacy payload field names and version envelopes are retained.
Malformed or unsupported data now fails explicitly instead of returning null,
defaulting, or silently dropping entries. Ordinary JSON does not retain every
NBT numeric width or array tag kind; serializers accept lossless conversions
but reject fractional or overflowing integer values. Use NBT when exact tag
types must survive export/import.

Chunk, NPC, structure, zone, world, village, and villager-record model codecs
are also ported and tested, alongside player, goods, and market models. All
nine original database definitions are registered with codec-based saved data,
queries, atomic imports/full-snapshot replacement, expiry, transactions, and
per-server shutdown. Backup workers capture snapshots on the server thread
before asynchronous export, isolate world paths, and retain only managed backup
files and checksum sidecars. Backups remain disabled by default, matching the
original definitions. The market JSON loader remains inactive, matching its
old commented-out registration. Gameplay consumers and live entity/name
lookups now have ported consumers, but still need broader actual-world coverage.

The animal, plot/chunk, cookware, crop, island-scan, NPC, stone, wood, tree,
structure, and villager managers are now ported with NeoForge event hooks.
Their tests cover actual
HarvestCraft/BOP identifiers, wool color collections, feeding and breeding
decisions, plot policies, rate/threshold calculations, biome restrictions,
warning caches, and offline AI response handling. Static gameplay state and
the AI worker are cleaned up between servers. Island scans and asynchronous
locators are canceled during shutdown; NPC retries and conversations are
server/world-bound and cleared at the appropriate lifecycle boundaries.
These tests are not a complete live-player gameplay regression suite: crop
actions, scans, NPC conversations, login workflows, and timed upkeep still
need live acceptance. Stone rules retain their tag-based selectors; 26.2's loaded dirt tag
does not include grass blocks.

All fifteen server roots are registered through one event handler: `ai`,
`database`, `farmcraft`, `managers`, `market`, `npc`, `player`, `plot`, `scan`,
`show`, `structures`, `tester`, `village`, `zone`, and `farmupcraft-legacy`.
Tests cover command trees,
permissions, economy boundaries, and source handling, but do not execute every
branch with a connected player. Login creates/backfills the player record before
database snapshots without resetting an existing balance. World scheduling
retains the 72,000-tick hourly interval and 6,000-tick reminder/retry interval;
a failed daily market refresh does not advance its successful-run date.
Overworld saves export players, villages, and chunks under the server working
directory's `farmupcraft-data`; shutdown exports run before database cleanup.

Cookware counters use the legacy `farmupcraft_cookware_uses` key inside the
modern custom-data component. The registered mixin supplies durability bars
and prevents exhausted cookware from returning as a crafting remainder,
without removing the ingredient before recipe lookup. Tests verify all nine
tools, 500/10,000-use initialization, counter preservation across actual
HarvestCraft remainders, exhaustion, and unchanged vanilla remainders.
Visual bars and full crafting-menu interaction still need client acceptance.

Enchanting reserves coins only after vanilla validates the request and selects
nonempty enchantments, immediately before applying them. The scoped payment
commits after installing the enchanted item and refunds an uncommitted reserve
on scope exit. Village-level fees/debt checks and creative coin exemption are
retained; failed vanilla or insufficient-funds requests do not debit coins.

The client player-data HUD, `/frecipe`, and claimed-chunk borders are ported.
Borders use public render-state extraction/custom-geometry events instead of
the old weather-rendering mixin; immutable snapshots omit shared internal
walls. Tooltip hints retain crop-tag selection and support a null player.
Pause-screen weather muting preserves the original double-precision volume
across repeated opening notifications. Unit tests cover HUD labels, border
geometry/selection/colors, tooltip filtering, and volume state. A connected
graphical client and separate dedicated server now capture nine real game
framebuffers with ordered acknowledgements. Inspected captures verify the HUD,
visible blue claim walls, wrapped crop tooltip, cookware bar, and real PauseScreen.
The weather option and options file mute and restore exactly; audible weather
is not asserted. Both peers exit cleanly. The gated harness and execution contract
are in `src/visualTest`; screenshots and receipts are under
`build/visual-acceptance`.

The border renderer preserves the legacy opacity with its own fragment shader:
the vanilla particle shader discards every forcefield texel at that opacity.
Walls are divided into 16-block-high sections so nearby wall fragments do not
inherit distant fog from full-level-height corner vertices. Regression tests
cover the shader contract and full-height section bounds, and the actual game
capture verifies visible walls. The installed optional-mod GUI checks below
also pass; broader connected client-command acceptance remains incomplete.

JourneyMap waypoint add/remove/scan payloads now use the v2 API with separate
client-only handlers. Generated native-owned waypoints carry an origin marker
so removal does not delete unrelated player waypoints. Codec, filtering, and
API-facing behavior are tested. Actual production payload delivery now also
creates visible native and scanned waypoints in JourneyMap's manager, opens
the native editor, and saves a verified rename. The persistent flag is checked,
but restart/disk waypoint persistence remains unverified.

Highlight/village-chunk payloads now drive JourneyMap v2 polygons, with
replacement, refresh, clearing, and logout cleanup. Recipe-opening payloads
use the actual JEI output-focus API. A connected nine-stage run verifies JEI's
bread OUTPUT focus with two visible recipe layouts, the native JourneyMap
manager/editor, and rendered red highlight/blue village polygons. Exact
geometry/style and viewport checks require 16-block chunks at four render
pixels/block; inspected screenshots show both colored squares. Production
cleanup removes the tracked polygon handles and both fixture waypoints; the
final native manager is visibly empty. This does not claim a post-clear map
framebuffer or disk persistence. All nine screenshot hashes match receipts,
both peers exit zero, and acceptance ports are released. Border/HUD framebuffer
acceptance is recorded above. JEI tests use its full mod at
runtime so Minecraft-facing API classes load in the same mod layer, rather
than loading the standalone compile API through JUnit's parent classloader.

### Data-generation status

The fourteen generator/provider classes compile; seventeen focused generator
and biome-classification tests pass. `runServerData` completes all providers
and writes only to `build\datagen-verification`, never directly to reviewed
source resources:

```powershell
.\gradlew.bat "-Dorg.gradle.java.home=$($env:JAVA_HOME.TrimEnd('\'))" runServerData
```

Datagen loads installed datapack registries before evaluating item component
initializers and normalizes unbound virtual tags through ordinary named tags,
without rebinding global components or suppressing validation. All nineteen
fresh JSON outputs were reviewed semantically. The packaged food tag now has
1,056 unique optional HarvestCraft foods, and the BOP overworld tag has 59
dimension-correct optional biomes. Crop generation retains 95 harvest items
plus six gardens. Regressions check uniqueness and sorted output, not merely
set membership. Normal `build` does not run data generation.

### Daily chores file

Both jars bundle the original chores list as `farmupcraft/defaults/chores.md`.
When a server starts (including opening a single-player world), FarmUpCraft
creates `config/farmupcraft/chores.md` only if it is missing. An existing legacy
`Rimfog/chores.md` in that instance is copied first when available; otherwise
the bundled list is used. Neither the legacy file nor an existing new file is
overwritten, even when it is empty. Startup initialization failures are logged.

Edit the new config file to customize the list. Login suggestions and
`/village chores` read it directly, so edits do not require rebuilding the jar.
The location is relative to the game's/server's config directory, not inside a
world save. The bundled snapshot is not automatically synchronized with edits
to the old file. The original source chores file and live instance directories
are not modified during the build.

Ten shared tests cover first-run defaults, custom/empty files, legacy copy and
precedence, subsequent edits, invalid paths and concurrent startup. All fourteen
common tests pass under both target builds. An actual NeoForge test-server
startup with no existing config or legacy file created an exact byte-for-byte
copy of the bundled list, and all 60 world tests passed.

### Bundled external HarvestCraft datapack

Both target jars include all 155 JSONs from Rimfog's external
`harvestcraft_datapack`: Forge retains the original 1.20.1 definitions, while
NeoForge includes converted 26.2 counterparts. These comprise 28 vanilla recipe definitions,
seven loot tables, 100 tree worldgen definitions (50 configured and 50 placed),
14 garden worldgen definitions, one oil recipe, three fish/meat tags and two
Pipez recipes. No separate datapack installation is required. The original
pack, its old `pack.mcmeta`, and the Rimfog save are not modified.

The NeoForge conversion uses singular recipe/loot/tag paths and current codecs. Garden
patches use HarvestCraft's registered `original_random_patch`; tree dirt rules
use 26.2's `below_trunk_provider`. Existing HarvestCraft biome modifiers already
attach the converted feature IDs. Preserve original aliases, generation
parameters and recipe quantities: the oil recipe produces eight despite its
legacy filename. Fish tags use HarvestCraft's actual `c:fishes`, `c:rawfish`
and `c:rawmeats` IDs; optional Aquaculture/Alex's Mobs entries do not require
those mods. The legacy cooked-fillet entry in `rawfish` is intentionally retained.

On NeoForge, Pipez remains optional. Its recipe overrides are skipped when absent and load
after Pipez when installed. To reproduce the installed-Pipez acceptance run:

```powershell
.\gradlew.bat "-Dorg.gradle.java.home=$($env:JAVA_HOME.TrimEnd('\'))" -PwithPipez=true runGameTestServer
```

Omit `-PwithPipez=true` for the absent-Pipez run. This flag adds the pinned Pipez
mod only to development runtimes; it does not bundle or require Pipez in the
production jar. Both runs pass all 60 world tests. Retained XML and logs are
under `build\external-pack-acceptance\with-pipez` and `without-pipez`.
Earlier attempts exposed intermittent entity-visibility/readiness failures
in existing fixtures; passing reruns are not a claim that those flakes or the
known upstream startup race are fixed.

Bundled data is available wherever the mod runs. World-generation changes
normally affect new chunks, not existing terrain. World datapacks can override
bundled data; do not also enable the unconverted old pack in a 26.2 world.
The Forge source resources and rebuilt 1.20.1 jar were checked against the
original pack: all 155 JSONs match semantically, with each packaged exactly
once. Its own `pack.mcmeta` retains format 15; the external pack metadata is
not copied. Forge keeps its original tag IDs, mod references and dependency
assumptions, rather than receiving NeoForge's optional-mod guards. The Forge
build passes with Java 17 and `build -x copyJar`; this packaging verification
does not claim a new Forge in-world generation test.

Database synchronization uses named NeoForge clientbound entry/full payloads,
separate client-only handlers, login/dimension snapshots, and disconnect-cache
cleanup. Large packets use NeoForge's negotiated packet splitter rather than
the old custom chunk collector. Tests cover a 3 MiB payload codec, malformed
snapshots, defensive copying, registry references, and empty-table clearing;
actual separate localhost server/client JVMs also passed login snapshots,
the exact balance update from 10 to 654321, Nether resynchronization with new
level/cache identities, a single 1,441,504-byte full snapshot, and logout cache
clearing. Both peers acknowledged exact contents; the strict receipt verifier
passed. This verifies database transport, not JourneyMap/JEI GUI behavior.
Development-only helpers and instructions live in `src/networkTest`; they are
excluded from the production jar and disabled in ordinary runs.

The database-enabled dedicated development server started, saved, stopped,
and restarted successfully. All nine database files and the existing biome
manager file retained identical SHA-256 hashes after restart/save/stop.
A subsequent disposable-world acceptance seeded one cross-referenced populated
row in each of the nine tables, saved/stopped, and restarted in a separate JVM.
Both lifecycles observed new backups from the real tick scheduler. Restart
verified exact loaded data before mutation, then cleared/restored every table
through checksum-validating imports. Independent checks verified all nine
physical backup hashes, nine SavedData files, and three economy exports.
The development-only `src/persistenceTest/EXECUTION.md` documents reproduction
and required evidence. This is not an old Forge save migration test.

That smoke test initialized 105 biomes, 198 indexed items, and 3,084 ordered
crop selections, then verified the saved definitions, selections, and reverse
index were unchanged after restart and a clean save/stop. This is biome-rule
coverage, not a complete economy or gameplay regression test.
The existing crop/biome JSON field names are retained, including
`growth_succes_rate`; the NeoForge resources use singular `tags/block` and
`tags/item` directories and the common `c:is_desert` biome tag.

Old Forge saved-data files are **not automatically migrated**. The 26.2
development world stores biome rules under
`world\dimensions\minecraft\overworld\data\farmupcraft\farmupcraft_biome_rules_manager.dat`.
The payload shape is preserved, but this does not establish that an entire
1.20.1 world can be upgraded safely. Test any future importer on copies.

An operator-only, read-only preflight is available:
`/farmupcraft-legacy inspect "<copied legacy overworld data directory>"`.
Pass the copied world's `data` directory, not the world root. It requires the
biome-rule manager file and all nine known database SavedData files; it checks
legacy envelopes, registry references, duplicates, canonical paths and destination
file absence before reporting eligibility. The biome filename is
`farmupcraft_biome_rules_manager.dat`; database filenames are
`farmupcraft:NAME.dat` or explicitly staged Windows-safe
`farmupcraft_NAME.dat`, where NAME is one of `biome_rules_database`,
`player_database`, `chunk_data_database`, `village_database`,
`goods_data_database_new`, `world_data_database`, `npc_database`,
`game_structure_database`, or `game_zone_database`. Supplying both aliases for
one database is rejected. Source paths equal to, inside or containing the live
world are rejected after canonical resolution, as is any existing destination
file, even an empty one.

**This is not a working importer yet.** The `apply` subcommand always reports
blocked and returns failure without writing. Installing data safely requires
coordinating live database caches with all independent SavedData files and
providing atomic rollback; successful inspection cannot certify unsaved live
state. Neither inspection nor apply modifies the supplied copy or active world.
Do not manually copy these files into an active server as a substitute.
The user has chosen to keep migration preflight-only for now; import/install
writes remain disabled.

Actual optional-integration absence is also verified using separate loopback
dedicated-server and graphical-client processes on port 25580 in a fresh
disposable root. Fresh launch preparation removes both JEI and JourneyMap jars
and rejects leaked bundled/separately resolved APIs. Strict runtime inventories
confirm FarmUpCraft and the content bundle are present while JEI, JourneyMap and
JourneyMap API are absent. Production login, balance update, dimension resync,
large full snapshot and logout-cache receipts pass, and both peers shut down
cleanly. The development-only scripts are in `src/optionalRuntimeTest`; they do
not modify cached jars, original acceptance evidence or EULA/global settings.
The run uses the scoped diagnostic background-thread setting, so it does not
resolve default-parallelism startup stability.

Separate regular-player action acceptance also passes with a real connected
graphical client and dedicated server on loopback 25581. The participant is
non-operator and in survival mode: normal forward-key input moves it over two
blocks, vanilla attack input breaks a dirt block, and client `useItemOn` places
a dirt block. Server-side events, authoritative block states, and held dirt
consumption from three to two gate ordered round-trip acknowledgements. Both
peers exit zero. The server seeds only the disposable wilderness fixture;
measured actions do not use creative mode or operator elevation.
Run `src\networkTest\run-player-actions.ps1` after `networkTestClasses` and
preparing the existing flattened network launch exports. Each attempt creates
a unique test root under `build\player-action-run-*`, enforces runtime bounds,
and preserves receipts and console logs. This is scripted normal client action
coverage, not physical mouse/keyboard automation or a test of the user's
regular world. The regular-world task remains pending explicit world selection
and approval; no live save, EULA, or global settings were changed.

The NeoForge dedicated development server has reached startup successfully.
NeoForge's development-mode patches bypass the EULA-file check; no EULA file
was edited. Normal production server setup still requires the user's EULA
acceptance. Dedicated-server diagnostics were traced to the pinned companion
artifacts: JourneyMap lists `client.SalmonRendererAccessor` in the common
`mixins` array rather than its client-only array; Biomes O' Plenty adds allays
to Mystic Grove without a registered spawn placement and puts ocelots in the
monster category for Rainforest/Rocky Rainforest. No dependency jars were
modified or errors suppressed; upstream corrections and runtime revalidation
remain outstanding. This is not a production-readiness guarantee.
An intermittent companion-registry tag-listener startup failure was observed
with default background parallelism. The combined build/tests, datagen, and
connected/persistence acceptance used process-scoped `-Dmax.bg.threads=1` for
diagnosis, not a production fix or global setting. A subsequent default-
parallelism run passed all 32 GameTests, but does not establish startup stability.
Three bounded default-parallelism server starts with FarmUpCraft removed and the
same companions present all reached ready and stopped cleanly; they did not
reproduce the intermittent race, so isolated upstream attribution remains
unconfirmed by runtime reproduction. A later default unit-fixture start with
FarmUpCraft present hit the same `apple_placed` listener-registration exception.
Only the disposable network client's loading-warning/accessibility prompts
were disabled for automatic connection; warnings remained logged and fatal
loading errors were not bypassed.

First-time builds may take several minutes to download and prepare Minecraft.
Both targets also build from fresh working-tree source-only snapshots in
offline mode, reusing global dependency/build caches without copying project
build outputs or development worlds. The NeoForge snapshot additionally passes
the same 407-test (three platform skips), four-common-test and 33-world-test
checks with the scoped diagnostic setting. This is not a committed
clean-checkout/release-readiness claim.
A quick old/new parity audit identified three concrete gaps, now restored and
verified: all 28 legacy vanilla recipe definitions, custom wheat Fortune loot
plus six HarvestCraft garden loot tables, and `/npc createskeleton <name>`
(with `createskleleton` retained as an alias). Recipe IDs and the 12 disabled
definitions retain legacy behavior; custom `stonecutter_*` IDs do not suppress
differently named vanilla recipes, just as in the Forge target.
A second bounded source audit found no additional corroborated omission in
the inspected defaults, lifecycle, command branches, NPC profiles and resource
references. Neither audit establishes exhaustive branch or runtime equivalence.
Completed parity work remains recorded in [TODO.txt](TODO.txt), separately
from deferred save migration and features lacking full live testing.
See [Plan.md](Plan.md) for the migration checklist.

## How to run the Forge server

0. Get Forge correct version

1. Must install all mods required, 
current zip file location here:
TODO

2. Copy any customized setup configs for:
[SpecialAI](../../AppData/Roaming/.minecraft/config/SpecialAI)
Spice of Life progression is now bundled and installed once automatically;
its config does not need to be copied manually.

3. FarmUpCraft's crop/biome rules and all 155 external HarvestCraft datapack
   JSONs are bundled in both target jars, including worldgen, tags and recipes.
   Forge uses the original definitions; NeoForge uses its converted versions.
   A separate copy of the pack is not needed to supply these defaults.
