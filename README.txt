Spears (Paper plugin) - diamond + netherite spears with reach and Lunge

INSTALL
  Put the built Spears-1.0.0.jar in your server's plugins folder and restart.
  On servers that already have vanilla spears the plugin does nothing (it says so in the console).

BUILD (free, no Java needed on your PC)
  1. Make a public repo on github.com
  2. Upload everything inside this folder (including the .github folder)
  3. Actions tab -> wait for the green check -> download "spears-jar" under Artifacts

COMMANDS (op only)
  /spear give <player> <diamond|netherite> [lunge 0-3]
  /spear book <player> <lunge 1-3>    (use on an anvil with a spear)
  /spear lunge <0-3>                  (sets Lunge on the spear you hold)
  /spear reload

CRAFTING
  Diamond spear: diamond top-right, stick centre, stick bottom-left (diagonal).
  Netherite spear: upgrade a diamond spear at a smithing table (template + netherite ingot).

CONFIG: plugins/Spears/config.yml (reach, lunge speed, hunger cost, sweep)
