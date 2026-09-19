# Flipping Tables for RuneLite

A personal RuneLite client with a sidebar for the Flipping Tables portfolio API. It plans the next Grand Exchange visit using collected coins, chosen inventory, current buy/sell offers and observed buy-limit usage. Suggestions remain manual: the plugin never places or cancels an offer.

## Tested versions

- RuneLite client **1.12.39**, pinned for reproducible compatibility testing.
- RuneLite Windows launcher **2.8.0**, including Java 17; plugin bytecode targets Java 11.
- Gradle **8.10**; Flipping Tables plugin **2.0.1**.
- API: `https://flippingtables.91-98-161-245.sslip.io/api/portfolio-snapshots/advice`.

The standard RuneLite launcher and this personal development client are separate launch paths. Updating the normal launcher does not rebuild this bundled plugin client. Future RuneLite updates should be tested and the pinned `runeLiteVersion` updated before rebuilding.

## Build and launch on Windows

```powershell
$env:JAVA_HOME = 'C:/path/to/your/jdk-11-or-17'
.\gradlew.bat '-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-Djavax.net.ssl.trustStore=NONE' test shadowJar
.\scripts\Start-FlippingTables.ps1
```

Building requires a full JDK 11 or 17. The output is `build/libs/flippingtables-2.0.1-all.jar`. The startup script uses the current RuneLite installation's bundled Java runtime and enables assertions, as required for developer plugins. `-CheckOnly` validates its launcher without opening the game. `gradlew run` is also available for development. The bundled JAR contains the launcher entry point, plugin and RuneLite runtime dependencies; regression tests and their dependencies are excluded.

Paste the API token into the sidebar. It remains in memory and is cleared when the plugin shuts down. For your own PC, the startup script can read an existing restricted access file with `-ApiAccessFile <path>`; it passes the token through the child process environment without printing it or embedding it in the JAR, command line or RuneLite settings. Keep that access file out of source control. The API address is configurable in the plugin settings; changing it clears the token and consent in the current session.

If your account uses the Jagex Launcher, follow RuneLite's [official development login instructions](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts). That is a deliberate user-controlled setup because it writes credentials capable of logging into your game account. This plugin and its startup script do not enable that setting, read those credentials, or change the Jagex Launcher. Ordinary Plugin Hub distribution would avoid this development-client setup.

## Plan a visit

1. Log in and collect completed/cancelled offers. Collect any coins or items from partial offers that you want available to this plan. Withdraw the coins and stock you intend to use.
2. Open the Flipping Tables sidebar and choose **Read current portfolio**.
3. Set the hours until your next visit (decimals such as `2.5` work) and a budget no higher than the coins currently carried. Outstanding buy reserves are captured separately, so do not add them to this budget.
4. Select carried items that may be sold. Remaining stock in active sell offers is included automatically. Unselected carried stock, uncollected items and bank stock are excluded. Optional item costs default to zero/unknown.
5. Review the destination/data disclosure and tick **Send portfolio for advice**, then **Plan next visit**.
6. Review keep/cancel/reprice/create suggestions. Apply any cancellation manually, then read the updated portfolio and plan again. For a new offer, **Use this suggestion** enables a matching GE price/quantity helper that you must click explicitly. Typing `ft` in the GE search shows suggested buys. **Copy price** is available for manual entry.

Changes to inventory, offers, account/game state, settings or inputs invalidate advice. Advice also expires after five minutes. HTTP work runs off the game and Swing threads, requests have a 60-second ceiling, and stale responses are discarded. There is no repeated API polling and the plugin makes no Wiki requests.

Limits use actual observed fills rather than requested offer quantities, share one four-hour window per item, and do not count duplicate offer events twice. They are tracked for the current session; earlier/offline purchases and client restarts remain unknown. First observation of an existing partial fill conservatively starts a new four-hour window. Membership currently follows the current world's members flag, so a member playing on a free world uses the conservative three-slot/free-item model.

Amounts are estimates, not realised profit. The server reports an inventory value estimate even when costs are unknown, and discloses when its search budget is exhausted. It does not know queue position or future prices.

## Validation and public distribution

`gradlew test` covers API serialization/authentication/errors, response bounds, partial sell inventory, observed limit windows, input validation and advice selection. `PanelPreview` renders the real sidebar components into images for layout inspection. Live account interaction still needs an in-game check; offline tests do not prove a game widget ID remains correct after a later OSRS update.

See [public release considerations](PUBLIC_RELEASE.md) for paid API access and Plugin Hub requirements. This branch is a personal client build; no Plugin Hub submission or billing changes are made.
