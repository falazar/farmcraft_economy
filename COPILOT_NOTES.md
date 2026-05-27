# Copilot Project Notes

## Project
- Minecraft Forge 1.20.1 mod: `farmcraft_economy`
- Mod ID: `farmupcraft`
- Java 17

## Key Paths
- Rimfog save: `C:\Users\robin\AppData\Roaming\.minecraft\saves\Rimfog`
- Rimfog datapack recipes: `...\Rimfog\datapacks\harvestcraft_datapack\data\minecraft\recipes\`
- Harvestcraft2 jars extracted: `C:\Users\robin\IdeaProjects\harvestcraft2\`

## Key Classes
- `MarketCommand.java` — `/market` commands, `/market admin` subcommands (10 admin cmds)
- `FarmCraftCommand.java` — `/farmcraft scheduler`, `/farmcraft runchecks`, `/frecipe` server cmd
- `VillageCommand.java` — village upkeep logic, daily guard via `hasRanTodayAlready()`
- `PlayerCommand.java` — `/player admin` subcommands (joinvillage, givecoins)
- `WorldScheduler.java` — hourly tick counter (72000 ticks/hour), `runHourlyChecks()` public
- `JeiIntegration.java` — `@JeiPlugin`, stores `IJeiRuntime` for client
- `OpenJeiRecipePacket.java` — S2C packet to open JEI recipe screen
- `EDBMessages.java` — packet channel registration
- `CropsManager.java` — crop management logic

## Architecture Notes
- S2C packets via `EDBMessages` SimpleChannel
- `buildItemLine()` in MarketCommand: checks RecipeManager for crafting recipe → conditionally underlines + adds ClickEvent for JEI
- ClickEvent.RUN_COMMAND always routes to server (not client dispatcher)
- Village upkeep runs once/day via `markDailyRanToday()` + `hasRanTodayAlready()` guard
