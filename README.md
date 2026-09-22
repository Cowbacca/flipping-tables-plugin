# Flipping Tables for RuneLite

A personal RuneLite client with a sidebar for the Flipping Tables portfolio API. It plans the next Grand Exchange visit using collected coins, chosen inventory, current buy/sell offers and observed buy-limit usage. Suggestions remain manual: the plugin never places or cancels an offer.

## Tested versions

- RuneLite client **1.12.39**, pinned for reproducible compatibility testing.
- RuneLite Windows launcher **2.8.0**, including Java 17; plugin bytecode targets Java 11.
- Gradle **8.10**; Flipping Tables plugin **2.1.2**.
- API: `https://flippingtables.91-98-161-245.sslip.io/api/portfolio-snapshots/advice`.

The standard RuneLite launcher and this personal development client are separate launch paths. Updating the normal launcher does not rebuild this bundled plugin client. Future RuneLite updates should be tested and the pinned `runeLiteVersion` updated before rebuilding.

## Build and launch on Windows

```powershell
$env:JAVA_HOME = 'C:/path/to/your/jdk-11-or-17'
.\gradlew.bat '-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-Djavax.net.ssl.trustStore=NONE' test shadowJar
.\scripts\Install-PersonalClient.ps1 -ApiAccessFile 'C:/path/to/restricted/api-access.env'
```

Building requires a full JDK 11 or 17. The output is `build/libs/flippingtables-2.1.2-all.jar`. From a development shell that permits local build scripts, the installer compiles the small Windows launcher using the .NET Framework compiler already present on Windows, copies the built client into `%LOCALAPPDATA%/FlippingTables`, and creates **Flipping Tables (RuneLite)** in the Start menu. Close the personal client before reinstalling. The API access file is optional; omitting it preserves an existing shortcut's access-file path when available.

Normal launches use `FlippingTables.exe` directly and do not invoke PowerShell or change its execution policy. The native launcher reads `client-version.txt`, uses the official RuneLite installation's bundled Java runtime, and enables assertions. It shows a persistent error message on failure and records startup output in capped logs under the installed `logs` directory. `FlippingTables.exe --check` validates the installed Java/JAR entry point without opening the game; a successful check is not a substitute for verifying full client initialization. `gradlew run` and `scripts/Start-FlippingTables.ps1 -CheckOnly` remain available for development. The bundled JAR contains the launcher entry point, plugin and RuneLite runtime dependencies; regression tests and their dependencies are excluded.

Paste the API token into the sidebar. It remains in memory and is cleared when the plugin shuts down. The personal build records Grand Exchange offer counters by default; its visible control can turn recording off. Recording writes a locked local journal per RuneScape profile and API destination, sends only a generated account UUID and raw offer counters, and never sends account names or raw account hashes. The result service derives accounting from those raw facts. For your own PC, the native launcher's `--api-access-file <path>` option reads an existing restricted access file; the installer records only its path in the shortcut. The token passes through the child process environment without being embedded in the executable, JAR, command line or RuneLite settings. Launcher logs redact the loaded token. Keep that access file out of source control. The API address is configurable in the plugin settings; changing it clears the token and consent in the current session.

If your account uses the Jagex Launcher, follow RuneLite's [official development login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts). That is a deliberate user-controlled setup because it writes credentials capable of logging into your game account. This plugin and its startup script do not enable that setting, read those credentials, or change the Jagex Launcher. Ordinary Plugin Hub distribution would avoid this development-client setup.

## Plan a visit

1. Log in and collect completed/cancelled offers. Collect any coins or items from partial offers that you want available to this plan. Withdraw the coins and stock you intend to use.
2. Open the Flipping Tables sidebar and choose **Read current portfolio**.
3. Set the hours until you next return (decimals such as `2.5` work), then set a budget no higher than the coins currently carried. Advice always evaluates eight-hour buying and selling windows; your return time only counts buy-limit resets available before you return. Outstanding buy reserves are captured separately, so do not add them to this budget.
4. Choose items under **Inventory to sell**, or select all eligible inventory items at once. **Already selling** shows the remaining stock in active sell offers, which is included automatically. Selecting inventory changes only the carried quantity; it never removes stock already listed. Inventory choices and optional costs survive a portfolio refresh within the current session. Unselected carried stock, uncollected items and bank stock are excluded. Optional item costs default to zero/unknown.
5. Review the destination/data disclosure and tick **Send portfolio for advice**, then **Plan next visit**.
6. Review keep/cancel/reprice/create suggestions and apply them manually. Cancelling or collecting an offer keeps the saved advice visible. Read the portfolio again to include newly collected stock, then request a fresh plan when you want updated recommendations. For new buys, type `ft` in the GE search or click its coins button. Choose a suggested item, open the normal quantity or price entry, and click **Use suggested quantity** or **Use suggested price** beneath the input. Press Enter to accept the number, then review and confirm the offer normally. A unique matching suggestion is selected automatically; **Use this suggestion** in the sidebar resolves multiple recommendations for the same item. **Copy quantity** and **Copy price** are also available.
7. A new offer placed with exactly the suggested item, side, quantity and price is marked **Placed**. Remaining suggestions stay available, including when that offer fills immediately. Reopening buy search restores the suggested list if you were using it; typing a normal item search or clicking the coins button switches back to normal search. The plugin never hides the shared text input or submits an offer.

The saved plan remains visible while you cancel, collect, reprice or refresh your portfolio. Changed portfolio or input values are labelled so the saved plan is not mistaken for a new calculation; replacing it requires a successful fresh request. Recognised completed actions are marked so the remaining suggestions stay available. Offer and inventory events are reconciled together at the next game tick. After five minutes the plan remains visible for reference, but its GE suggestion helpers expire. Logging out or changing the API destination clears advice and stock choices. HTTP work runs off the game and Swing threads, requests have a 60-second ceiling, and stale responses are discarded. There is no repeated API polling and the plugin makes no Wiki requests.

Sell price and quantity helpers use an outstanding sell or sell-reprice recommendation from the saved plan. Opening a sell dialog does not fetch a quote by itself. To get advice for newly collected items, read the portfolio, select the inventory to sell, and request a fresh plan. Repricing suggestions remain usable while the old offer is cancelled and collected; the exact replacement is marked done when observed. Sale cards show the evidence window, newest observation and observed/projected volume when the API can price held stock. New plans use a fixed eight-hour selling window; return time does not shorten it. Stock with no quote or no available slot is explained without creating GE helpers.

Limits use actual observed fills rather than requested offer quantities, share one four-hour window per item, and do not count duplicate offer events twice. They are tracked for the current session; earlier/offline purchases and client restarts remain unknown. First observation of an existing partial fill conservatively starts a new four-hour window. Membership currently follows the current world's members flag, so a member playing on a free world uses the conservative three-slot/free-item model.

Amounts are estimates, not realised profit. The server reports an inventory value estimate even when costs are unknown, and discloses when its search budget is exhausted. It does not know queue position or future prices.

## Validation and public distribution

`gradlew test` covers API serialization/authentication/errors, response bounds, partial sell inventory, observed limit windows, input validation, GE search rebuilds and normal typing, item-bound numeric helpers, stale callbacks, and remaining-plan reconciliation across ordered game events. `PanelPreview` renders the real sidebar components into images for layout inspection. Live account interaction still needs an in-game check; offline tests do not prove a game widget ID remains correct after a later OSRS update.

The GE integration uses the pinned RuneLite [search and offer setup script constants](https://github.com/runelite/runelite/blob/runelite-parent-1.12.39/runelite-api/src/main/java/net/runelite/api/ScriptID.java), checks live widget visibility and dialog state again on each helper click, and refreshes after scripts have built the widgets. It does not assume that reusing a chatbox parent means its children survived.

See [public release considerations](PUBLIC_RELEASE.md) for paid API access and Plugin Hub requirements. This branch is a personal client build; no Plugin Hub submission or billing changes are made.
