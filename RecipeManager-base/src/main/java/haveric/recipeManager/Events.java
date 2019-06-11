package haveric.recipeManager;

import haveric.recipeManager.api.events.RecipeManagerCraftEvent;
import haveric.recipeManager.api.events.RecipeManagerFuelBurnEndEvent;
import haveric.recipeManager.api.events.RecipeManagerFuelBurnRandomEvent;
import haveric.recipeManager.api.events.RecipeManagerPrepareCraftEvent;
import haveric.recipeManager.data.BrewingStandData;
import haveric.recipeManager.data.BrewingStands;
import haveric.recipeManager.data.FurnaceData;
import haveric.recipeManager.data.Furnaces;
import haveric.recipeManager.flag.FlagType;
import haveric.recipeManager.flag.Flaggable;
import haveric.recipeManager.flag.args.Args;
import haveric.recipeManager.messages.MessageSender;
import haveric.recipeManager.messages.Messages;
import haveric.recipeManager.messages.SoundNotifier;
import haveric.recipeManager.recipes.*;
import haveric.recipeManager.tools.Tools;
import haveric.recipeManager.tools.ToolsItem;
import haveric.recipeManager.tools.Version;
import haveric.recipeManagerCommon.recipes.RMCRecipeInfo.RecipeOwner;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Furnace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * RecipeManager handled events
 */
public class Events implements Listener {
    public Events() { }

    public void clean() {
        HandlerList.unregisterAll(this);
    }

    public static void reload() {
        HandlerList.unregisterAll(RecipeManager.getEvents());
        Bukkit.getPluginManager().registerEvents(RecipeManager.getEvents(), RecipeManager.getPlugin());
    }

    /*
     * Workbench craft events
     */

    @EventHandler(priority = EventPriority.LOW)
    public void prepareCraft(PrepareItemCraftEvent event) {
        try {
            CraftingInventory inv = event.getInventory();

            if (inv.getResult() == null) {
                return; // event was cancelled by some other plugin
            }

            Player player;
            if (event.getView() == null) {
                player = null;
            } else {
                player = (Player) event.getView().getPlayer();
            }

            if (!RecipeManager.getPlugin().canCraft(player)) {
                inv.setResult(null);
                return; // player not allowed to craft, stop here
            }

            Location location;
            // get workbench location or null
            if (inv.getSize() > 9) {
                location = Workbenches.get(player);
            } else {
                location = null;
            }

            if (event.isRepair()) {
                prepareRepairRecipe(player, inv, location);
                return; // if it's a repair recipe we don't need to move on
            }

            Recipe bukkitRecipe = event.getRecipe();

            if (bukkitRecipe == null) {
                return; // Bukkit recipe is null ! skip it
            }

            ItemResult result;
            if (inv.getResult() == null) {
                result = null;
            } else {
                result = new ItemResult(inv.getResult());
            }

            if (prepareSpecialRecipe(player, inv, result, bukkitRecipe)) {
                return; // stop here if it's a special recipe
            }

            WorkbenchRecipe recipe = RecipeManager.getRecipes().getWorkbenchRecipe(bukkitRecipe);

            if (recipe == null) {
                return; // not a custom recipe or recipe not found, no need to move on
            }

            Args a = Args.create().player(player).inventory(inv).location(location).recipe(recipe).build();

            result = recipe.getDisplayResult(a); // get the result from recipe

            // Call the RecipeManagerPrepareCraftEvent
            RecipeManagerPrepareCraftEvent callEvent = new RecipeManagerPrepareCraftEvent(recipe, result, player, location);
            Bukkit.getPluginManager().callEvent(callEvent);

            if (callEvent.getResult() == null) {
                result = null;
            } else {
                result = new ItemResult(callEvent.getResult());
            }

            if (result != null) {
                a.setResult(result);

                if (recipe.sendPrepare(a)) {
                    a.sendEffects(a.player(), Messages.getInstance().get("flag.prefix.recipe"));
                } else {
                    a.sendReasons(a.player(), Messages.getInstance().get("flag.prefix.recipe"));
                    result = null;
                }
            }

            inv.setResult(result);
        } catch (Throwable e) {
            if (event.getInventory() != null) {
                event.getInventory().setResult(null);
            }

            CommandSender sender;
            if (event.getView() != null && event.getView().getPlayer() instanceof Player) {
                sender = event.getView().getPlayer();
            } else {
                sender = null;
            }

            MessageSender.getInstance().error(sender, e, event.getEventName() + " cancelled due to error:");
        }
    }

    private boolean prepareSpecialRecipe(Player player, CraftingInventory inv, ItemStack result, Recipe recipe) {
        ItemStack recipeResult = recipe.getResult();

        if (!result.equals(recipeResult)) { // result was processed by the game and it doesn't match the original recipe
            if (!Settings.getInstance().getSpecialLeatherDye()) {
                boolean leatherDye = false;

                if (Version.has1_11Support()) {
                    if (result.hasItemMeta() && result.getItemMeta() instanceof LeatherArmorMeta) {
                        leatherDye = true;
                    }
                } else if (recipeResult.equals(Vanilla.RECIPE_LEATHERDYE)) {
                    leatherDye = true;
                }

                if (leatherDye) {
                    Messages.getInstance().sendOnce(player, "craft.special.leatherdye");
                    inv.setResult(null);
                    return true;
                }
            }

            if (!Settings.getInstance().getSpecialMapCloning()) {
                boolean mapClone = false;

                if (Version.has1_11Support()) {
                    if (result.getType().equals(Material.MAP) && result.getAmount() > 1) {
                        mapClone = true;
                    }
                } else if (recipeResult.equals(Vanilla.RECIPE_MAPCLONE)) {
                    mapClone = true;
                }

                if (mapClone) {
                    Messages.getInstance().sendOnce(player, "craft.special.map.cloning");
                    inv.setResult(null);
                    return true;
                }
            }

            if (!Settings.getInstance().getSpecialMapExtending()) {
                boolean mapExtend = false;

                if (Version.has1_11Support()) {
                    if (recipeResult.equals(Vanilla.RECIPE_MAPEXTEND_1_11)) {
                        mapExtend = true;
                    }
                } else if (recipeResult.equals(Vanilla.RECIPE_MAPEXTEND)) {
                    mapExtend = true;
                }

                if (mapExtend) {
                    Messages.getInstance().sendOnce(player, "craft.special.map.extending");
                    inv.setResult(null);
                    return true;
                }
            }

            if (!Settings.getInstance().getSpecialFireworks()) {
                boolean fireworks = false;


                if (Version.has1_11Support()) {
                    Material fireworkRocketMaterial;
                    if (Version.has1_13Support()) {
                        fireworkRocketMaterial = Material.FIREWORK_ROCKET;
                    } else {
                        fireworkRocketMaterial = Material.getMaterial("FIREWORK");
                    }
                    if (result.getType() == fireworkRocketMaterial) {
                        fireworks = true;
                    }
                } else if (recipeResult.equals(Vanilla.RECIPE_FIREWORKS)) {
                    fireworks = true;
                }

                if (fireworks) {
                    Messages.getInstance().sendOnce(player, "craft.special.fireworks");
                    inv.setResult(null);
                    return true;
                }
            }

            if (!Settings.getInstance().getSpecialFireworkStar()) {
                if (Version.has1_11Support()) {
                    Material fireworkStarMaterial;
                    if (Version.has1_13Support()) {
                        fireworkStarMaterial = Material.FIREWORK_STAR;
                    } else {
                        fireworkStarMaterial = Material.getMaterial("FIREWORK_CHARGE");
                    }
                    if (result.getType() == fireworkStarMaterial) {
                        Messages.getInstance().sendOnce(player, "craft.special.fireworkstar");
                        inv.setResult(null);
                        return true;
                    }
                }
            }

            if (!Settings.getInstance().getSpecialBookCloning()) {
                boolean bookClone = false;

                if (Version.has1_11Support()) {
                    if (result.getType().equals(Material.WRITTEN_BOOK)) {
                        bookClone = true;
                    }
                } else if (recipeResult.equals(Vanilla.RECIPE_BOOKCLONE)) {
                    bookClone = true;
                }

                if (bookClone) {
                    Messages.getInstance().sendOnce(player, "craft.special.book.cloning");
                    inv.setResult(null);
                    return true;
                }
            }

            if (!Settings.getInstance().getSpecialBanner()) {
                boolean isBanner = false;

                if (Version.has1_13Support()) {
                    // TODO: Does 1.13 support special recipes?
                } else if (Version.has1_11Support()) {
                    if (result.getType().equals(Material.getMaterial("BANNER"))) {
                        isBanner = true;
                    }
                } else if (recipeResult.equals(Vanilla.RECIPE_BANNER)) {
                    isBanner = true;
                }

                if (isBanner) {
                    Messages.getInstance().sendOnce(player, "craft.special.banner");
                    inv.setResult(null);
                    return true;
                }
            }

            if (!Settings.getInstance().getSpecialShieldBanner()) {
                boolean isShieldBanner = false;

                if (Version.has1_11Support()) {
                    if (result.getType().equals(Material.SHIELD)) {
                        isShieldBanner = true;
                    }
                } else if (recipeResult.equals(Vanilla.RECIPE_SHIELD_BANNER)) {
                    isShieldBanner = true;
                }

                if (isShieldBanner) {
                    Messages.getInstance().sendOnce(player, "craft.special.shieldbanner");
                    inv.setResult(null);
                    return true;
                }
            }

            if (!Settings.getInstance().getSpecialTippedArrows() && recipeResult.equals(Vanilla.RECIPE_TIPPED_ARROW)) {
                Messages.getInstance().sendOnce(player, "craft.special.tippedarrows");
                inv.setResult(null);
                return true;
            }

            if (!Settings.getInstance().getSpecialShulkerDye()) {
                if (Version.has1_11Support() && ToolsItem.isShulkerBox(result.getType())) {
                    Messages.getInstance().sendOnce(player, "craft.special.shulkerdye");
                    inv.setResult(null);
                    return true;
                }
            }
        }

        return false;
    }

    private void prepareRepairRecipe(Player player, CraftingInventory inv, Location location) throws Throwable {
        if (!Settings.getInstance().getSpecialRepair()) {
            SoundNotifier.sendDenySound(player, location);
            Messages.getInstance().sendOnce(player, "craft.repair.disabled");
            inv.setResult(null);
            return;
        }

        ItemStack result = inv.getRecipe().getResult();

        if (Settings.getInstance().getSpecialRepairMetadata()) {
            ItemStack[] matrix = inv.getMatrix();
            ItemStack[] repaired = new ItemStack[2];

            int repairIndex = 0;

            for (ItemStack item : matrix) {
                if (item != null && item.getType() != Material.AIR) {
                    repaired[repairIndex] = item;

                    if (++repairIndex > 1) {
                        break;
                    }
                }
            }

            if (repaired[0] != null && repaired[1] != null) {
                ItemMeta meta = null;

                if (repaired[0].hasItemMeta()) {
                    meta = repaired[0].getItemMeta();
                } else if (repaired[1].hasItemMeta()) {
                    meta = repaired[1].getItemMeta();
                }

                if (meta != null) {
                    result = inv.getResult();
                    result.setItemMeta(meta);
                }
            }
        }

        RecipeManagerPrepareCraftEvent callEvent = new RecipeManagerPrepareCraftEvent(null, result, player, location);
        Bukkit.getPluginManager().callEvent(callEvent);

        result = callEvent.getResult();

        if (result != null) {
            SoundNotifier.sendRepairSound(player, location);
        }

        inv.setResult(result);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void craftFinish(CraftItemEvent event) {
        try {
            final CraftingInventory inv = event.getInventory();

            ItemResult result;
            if (inv.getResult() == null) {
                result = null;
            } else {
                result = new ItemResult(inv.getResult());
            }

            final Player player;
            if (event.getView() == null) {
                player = null;
            } else {
                player = (Player) event.getView().getPlayer();
            }

            Location location = Workbenches.get(player);

            if (!event.isShiftClick() && result == null) {
                event.setCancelled(true);
                SoundNotifier.sendDenySound(player, location);
                return;
            }

            Recipe bukkitRecipe = event.getRecipe();
            WorkbenchRecipe recipe = RecipeManager.getRecipes().getWorkbenchRecipe(bukkitRecipe);

            if (recipe == null) {
                return;
            }

            Args a = Args.create().player(player).inventory(inv).recipe(recipe).location(location).build();

            if (!recipe.checkFlags(a)) {
                SoundNotifier.sendDenySound(player, location);
                event.setCancelled(true);
                return;
            }

            result = Recipes.recipeGetResult(a, recipe); // gets the same stored result if event was previously cancelled

            int mouseButton;
            if (event.isRightClick()) {
                mouseButton = 1;
            } else {
                mouseButton = 0;
            }
            // Call the PRE event TODO upgrade to MouseButton when PR is pulled
            RecipeManagerCraftEvent callEvent = new RecipeManagerCraftEvent(recipe, result, player, event.getCursor(), event.isShiftClick(), mouseButton);

            PluginManager pm = Bukkit.getPluginManager();
            if (pm != null) { // Null check used for Tests to skip event calling
                pm.callEvent(callEvent);
            }

            if (callEvent.isCancelled()) { // if event was cancelled by some other plugin then cancel this event
                event.setCancelled(true);
                return;
            }

            result = callEvent.getResult(); // get the result from the event if it was changed

            a = Args.create().player(player).inventory(inv).recipe(recipe).location(location).result(result).build();

            int times = craftResult(event, inv, player, recipe, result, a); // craft the result
            if (result != null) {
                a = Args.create().player(player).inventory(inv).recipe(recipe).location(location).result(result).build();

                if (times > 0) {
                    Recipes.recipeResetResult(a.playerUUID());
                }

                ItemStack[] originalMatrix = inv.getMatrix().clone();
                boolean firstRun = true;
                while (--times >= 0) {
                    // Make sure no items have changed or stop crafting
                    if (isDifferentMatrix(originalMatrix, inv.getMatrix())) {
                        //MessageSender.getInstance().info("Stop Crafting - Different matrix");
                        break;
                    }

                    // Make sure all flag conditions are still valid or stop crafting
                    if (!recipe.checkFlags(a) || !result.checkFlags(a)) {
                        //MessageSender.getInstance().info("Stop Crafting - Flags no longer match");
                        break;
                    }

                    a.setFirstRun(firstRun);
                    a.clear();

                    boolean recipeCraftSuccess = recipe.sendCrafted(a);
                    if (recipeCraftSuccess) {
                        a.sendEffects(a.player(), Messages.getInstance().get("flag.prefix.recipe"));
                    }

                    a.clear();

                    boolean resultPrepareSuccess = result.sendPrepare(a);
                    if (resultPrepareSuccess) {
                        a.sendEffects(a.player(), Messages.getInstance().parse("flag.prefix.result", "{item}", ToolsItem.print(result)));
                    }

                    a.clear();

                    boolean resultCraftSuccess = result.sendCrafted(a);
                    if (resultCraftSuccess) {
                        a.sendEffects(a.player(), Messages.getInstance().parse("flag.prefix.result", "{item}", ToolsItem.print(result)));
                    }

                    boolean doneCrafting = false;

                    boolean subtract = false;
                    boolean onlyExtra = true;
                    if (recipeCraftSuccess && resultPrepareSuccess && resultCraftSuccess) {
                        if (recipe.isMultiResult()) {
                            subtract = true;
                            onlyExtra = false;
                        }

                        if (recipe.hasFlag(FlagType.INGREDIENT_CONDITION) || result.hasFlag(FlagType.INGREDIENT_CONDITION)) {
                            subtract = true;
                        }

                        if (result.hasFlag(FlagType.NO_RESULT)) {
                            event.setCurrentItem(new ItemStack(Material.AIR));
                            event.setCursor(new ItemStack(Material.AIR));
                            subtract = true;
                            onlyExtra = false;
                        }

                        if (event.isShiftClick()) {
                            subtract = true;
                            onlyExtra = false;
                            event.setCancelled(true);

                            if (!result.hasFlag(FlagType.NO_RESULT)) {
                                // Make sure inventory can fit the results or stop crafting
                                if (Tools.playerCanAddItem(player, result)) {
                                    player.getInventory().addItem(result.clone());
                                } else {
                                    //MessageSender.getInstance().info("Stop Crafting - Full Inventory");
                                    doneCrafting = true;
                                }
                            }
                        }
                    }

                    if (subtract) {
                        recipe.subtractIngredients(inv, result, onlyExtra);
                    }

                    if (doneCrafting) {
                        break;
                    }

                    // TODO call post-event ?
                    // Bukkit.getPluginManager().callEvent(new RecipeManagerCraftEventPost(recipe, result, player, event.getCursor(), event.isShiftClick(), event.isRightClick() ? 1 : 0));

                    firstRun = false;
                }
            }

            if (pm != null) { // Null check used for Tests to skip event calling
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.getPluginManager().callEvent(new PrepareItemCraftEvent(inv, player.getOpenInventory(), false));
                    }
                }.runTaskLater(RecipeManager.getPlugin(), 0);


                new UpdateInventory(player, 2); // update inventory 2 ticks later
            }
        } catch (Throwable e) {
            event.setCancelled(true);
            CommandSender sender;
            if (event.getView() != null && event.getView().getPlayer() instanceof Player) {
                sender = event.getView().getPlayer();
            } else {
                sender = null;
            }

            MessageSender.getInstance().error(sender, e, event.getEventName() + " cancelled due to error:");
        }
    }

    private boolean isDifferentMatrix(ItemStack[] original, ItemStack[] current) {
        boolean different = false;

        if (original.length == current.length) {
            for (int i = 0; i < original.length; i++) {
                ItemStack originalStack = original[i];
                ItemStack currentStack = current[i];

                if (originalStack != null) {
                    if (currentStack == null && originalStack.getType() == Material.AIR) {
                        // Null == AIR
                    } else if (currentStack == null || currentStack.getType() != originalStack.getType()) {
                        different = true;
                        break;
                    }
                }
            }
        }

        return different;
    }

    private int craftResult(CraftItemEvent event, CraftingInventory inv, Player player, WorkbenchRecipe recipe, ItemResult result, Args a) throws Throwable {
        if (recipe.isMultiResult()) {
            // more special treatment needed for multi-result ones...

            event.setCancelled(true); // need to cancel this from the start.

            // check if result is air / recipe failed
            if (result == null || result.getType() == Material.AIR) {
                Messages.getInstance().sendOnce(player, "craft.recipe.multi.failed");
                SoundNotifier.sendFailSound(player, a.location());
            } else {
                if (event.isShiftClick()) {
                    Messages.getInstance().sendOnce(player, "craft.recipe.multi.noshiftclick");

                    return 0;
                } else {
                    ItemStack cursor = event.getCursor();

                    if (!recipe.hasFlag(FlagType.INDIVIDUAL_RESULTS) && cursor != null && cursor.getType() != Material.AIR) {
                        Messages.getInstance().sendOnce(player, "craft.recipe.multi.chance.cursorhasitem");
                        return 0;
                    }

                    ItemStack merged = ToolsItem.merge(cursor, result);

                    if (merged == null) {
                        Messages.getInstance().sendOnce(player, "craft.recipe.multi.cursorfull");
                        return 0;
                    }

                    event.setCursor(merged);

                }
            }
        } else {
            if (result == null || result.getType() == Material.AIR) {
                event.setCurrentItem(null);
                return 0;
            }

            if (event.isShiftClick()) {
                int craftAmount = recipe.getCraftableTimes(inv); // Calculate how many times the recipe can be crafted
                ItemStack item = result.clone();
                item.setAmount(result.getAmount() * craftAmount);

                int space = Tools.playerFreeSpaceForItem(player, item);
                int crafted = Math.min((int) Math.ceil(space / (double) result.getAmount()), craftAmount);

                if (crafted > 0) {
                    event.setCurrentItem(result);
                    return crafted;
                }

                return 0;
            }

            ItemStack cursor = event.getCursor();
            ItemStack merged = ToolsItem.merge(cursor, result);

            if (merged == null) {
                return 0;
            }

            event.setCurrentItem(result);
        }

        return 1;
    }

    /*
     * Workbench monitor events
     */

    @EventHandler(priority = EventPriority.MONITOR)
    public void inventoryClose(InventoryCloseEvent event) {
        HumanEntity human = event.getPlayer();

        if (event.getView().getType() == InventoryType.WORKBENCH) {
            Workbenches.remove(human);
        }

        if (Settings.getInstance().getFixModResults()) {
            for (ItemStack item : human.getInventory().getContents()) {
                itemProcess(item);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void playerInteract(PlayerInteractEvent event) {
        switch (event.getAction()) {
            case RIGHT_CLICK_BLOCK:
                Block block = event.getClickedBlock();

                Material craftingTableMaterial;
                Material enchantingTableMaterial;
                if (Version.has1_13Support()) {
                    craftingTableMaterial = Material.CRAFTING_TABLE;
                    enchantingTableMaterial = Material.ENCHANTING_TABLE;
                } else {
                    craftingTableMaterial = Material.getMaterial("WORKBENCH");
                    enchantingTableMaterial = Material.getMaterial("ENCHANTMENT_TABLE");
                }

                Material blockType = block.getType();

                if (blockType == Material.FURNACE || blockType == Material.BREWING_STAND || blockType == Material.ANVIL ||
                        blockType == craftingTableMaterial || blockType == enchantingTableMaterial ||
                        (!Version.has1_13Support() && blockType == Material.getMaterial("BURNING_FURNACE"))) {

                    if (!RecipeManager.getPlugin().canCraft(event.getPlayer())) {
                        event.setCancelled(true);
                        return;
                    }

                    if (blockType == craftingTableMaterial) {
                        Workbenches.add(event.getPlayer(), event.getClickedBlock().getLocation());
                    }
                }

                break;

            case PHYSICAL:
                break;

            default:
                Workbenches.remove(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void playerTeleport(PlayerTeleportEvent event) {
        Workbenches.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void playerDeath(PlayerDeathEvent event) {
        Workbenches.remove(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void playerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        Players.remove(player);
        Workbenches.remove(player);
        Recipes.recipeResetResult(player.getUniqueId());
        Messages.getInstance().clearPlayer(name);
    }

    /*
     * Furnace craft events
     */

    @EventHandler
    public void inventoryDrag(InventoryDragEvent event) {
        Inventory inv = event.getInventory();

        if (inv instanceof FurnaceInventory) {
            HumanEntity entity = event.getWhoClicked();

            if (entity instanceof Player) {
                if (event.getRawSlots().contains(0)) {
                    FurnaceInventory inventory = (FurnaceInventory) inv;
                    Furnace furnace = inventory.getHolder();
                    ItemStack slot = inventory.getItem(0);

                    if (slot == null || slot.getType() == Material.AIR) {
                        ItemStack cursor = event.getOldCursor();

                        SmeltRecipe recipe = RecipeManager.getRecipes().getSmeltRecipe(cursor);

                        if (recipe != null) {
                            if (recipe.hasFlag(FlagType.REMOVE)) {
                                event.setCancelled(true);
                            }

                            FurnaceData data = Furnaces.get(furnace.getLocation());
                            ItemStack fuel = data.getFuel();

                            if (fuel == null) {
                                fuel = inventory.getFuel();
                            }

                            ItemStack recipeFuel = recipe.getFuel();

                            if (recipeFuel != null && !ToolsItem.isSameItem(recipeFuel, fuel, true)) {
                                event.setCancelled(true);
                            } else {
                                Args a = Args.create().player(data.getFuelerUUID()).location(furnace.getLocation()).recipe(recipe).result(recipe.getResult()).inventory(inventory).extra(inventory.getSmelting()).build();
                                ItemResult result = recipe.getResult(a);

                                if (furnaceHandleFlaggable(recipe, a, false, true) && (result == null || furnaceHandleFlaggable(result, a, false, true)) && isRecipeSameAsResult(a)) {
                                    ToolsItem.updateFurnaceCookTimeDelayed(furnace, (short) (200 - recipe.getCookTicks()));
                                } else {
                                    ToolsItem.updateFurnaceCookTimeDelayed(furnace, (short) 0);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void inventoryClick(InventoryClickEvent event) {
        try {
            Inventory inv = event.getInventory();
            HumanEntity ent = event.getWhoClicked();

            if (ent instanceof Player) {
                InventoryHolder holder = inv.getHolder();
                Recipes.recipeResetResult(ent.getUniqueId());

                if (inv instanceof FurnaceInventory && holder instanceof Furnace) {
                    furnaceClick(event, (Furnace) holder, (Player) ent);
                } else if (inv instanceof BrewerInventory && holder instanceof BrewingStand) {
                    if (event.getRawSlot() < inv.getSize()) {
                        BrewingStandData data = BrewingStands.get(((BrewingStand) holder).getLocation());
                        data.setFuelerUUID(ent.getUniqueId());
                    }
                }
            }
        } catch (Throwable e) {
            event.setCancelled(true);
            CommandSender sender;
            if (event.getWhoClicked() instanceof Player) {
                sender = event.getWhoClicked();
            } else {
                sender = null;
            }

            MessageSender.getInstance().error(sender, e, event.getEventName() + " cancelled due to error:");
        }
    }

    private void furnaceClick(InventoryClickEvent event, Furnace furnace, Player player) throws Throwable {
        FurnaceData data = Furnaces.get(furnace.getLocation());
        if (data.getFuelerUUID() == null) {
            data.setFuelerUUID(player.getUniqueId());
        }

        if (!RecipeManager.getPlugin().canCraft(player)) {
            event.setCancelled(true);
            return;
        }

        if (event.getRawSlot() == -1) {
            return;
        }

        FurnaceInventory inventory = furnace.getInventory();
        ItemStack cursor = event.getCursor();
        ItemStack clicked = event.getCurrentItem();
        int slot = event.getRawSlot();

        switch (slot) {
            case 0: // INGREDIENT slot
                if (event.getClick() == ClickType.NUMBER_KEY) {
                    if (clicked == null || clicked.getType() == Material.AIR) {
                        int hotbarButton = event.getHotbarButton();
                        ItemStack hotbarItem = player.getInventory().getItem(hotbarButton);

                        if (hotbarItem != null && hotbarItem.getType() != Material.AIR) {
                            SmeltRecipe recipe = RecipeManager.getRecipes().getSmeltRecipe(hotbarItem);

                            if (recipe != null) {
                                if (recipe.hasFlag(FlagType.REMOVE)) {
                                    event.setCancelled(true);
                                }

                                data.setFuelerUUID(player.getUniqueId());

                                Args a = Args.create().player(data.getFuelerUUID()).location(furnace.getLocation()).recipe(recipe).result(recipe.getResult()).inventory(inventory).extra(inventory.getSmelting()).build();

                                if (furnaceHandleFlaggable(recipe, a, false, true) && isRecipeSameAsResult(a)) {
                                    ToolsItem.updateFurnaceCookTimeDelayed(furnace, (short) (200 - recipe.getCookTicks()));
                                } else {
                                    ToolsItem.updateFurnaceCookTimeDelayed(furnace, (short) 0);
                                }
                            }
                        }
                    }
                } else if (cursor != null && cursor.getType() != Material.AIR) {
                    if (clicked == null || clicked.getType() == Material.AIR || !ToolsItem.isSameItem(cursor, clicked, true)) {
                        SmeltRecipe recipe = RecipeManager.getRecipes().getSmeltRecipe(cursor);

                        if (recipe != null) {
                            if (recipe.hasFlag(FlagType.REMOVE)) {
                                event.setCancelled(true);
                            }

                            data.setFuelerUUID(player.getUniqueId());

                            ItemStack fuel = data.getFuel();

                            if (fuel == null) {
                                fuel = inventory.getFuel();
                            }

                            ItemStack recipeFuel = recipe.getFuel();

                            if (recipeFuel != null && !ToolsItem.isSameItem(recipeFuel, fuel, true)) {
                                event.setCancelled(true);
                            } else {
                                Args a = Args.create().player(data.getFuelerUUID()).location(furnace.getLocation()).recipe(recipe).result(recipe.getResult()).inventory(inventory).extra(inventory.getSmelting()).build();
                                ItemResult result = recipe.getResult(a);

                                if (furnaceHandleFlaggable(recipe, a, false, true) && (result == null || furnaceHandleFlaggable(result, a, false, true)) && isRecipeSameAsResult(a)) {
                                    ToolsItem.updateFurnaceCookTimeDelayed(furnace, (short) (200 - recipe.getCookTicks()));
                                } else {
                                    ToolsItem.updateFurnaceCookTimeDelayed(furnace, (short) 0);
                                }
                            }
                        }
                    }
                }
                break;
            case 1: // FUEL slot
                ItemStack fuel = ToolsItem.nullIfAir(cursor);

                if (fuel != null) {
                    data.setFuelerUUID(player.getUniqueId());
                }

                if (event.getClick() == ClickType.NUMBER_KEY) {
                    int hotbarButton = event.getHotbarButton();
                    ItemStack hotbarItem = player.getInventory().getItem(hotbarButton);

                    FuelRecipe fuelRecipe = Recipes.getInstance().getFuelRecipe(hotbarItem);

                    if (fuelRecipe != null && !fuelRecipe.getInfo().getOwner().equals(RecipeOwner.MINECRAFT)) {
                        if (hotbarItem != null && hotbarItem.getType() != Material.AIR) {
                            if (clicked == null || clicked.getType() == Material.AIR) {
                                event.setCurrentItem(hotbarItem.clone());
                                ToolsItem.replaceItem(player.getInventory(), hotbarButton, new ItemStack(Material.AIR));
                                event.setResult(Result.DENY);
                            }
                        }
                    }
                } else if (event.isLeftClick()) {
                    FuelRecipe fuelRecipe = Recipes.getInstance().getFuelRecipe(cursor);

                    if (fuelRecipe != null && !fuelRecipe.getInfo().getOwner().equals(RecipeOwner.MINECRAFT)) {
                        if (cursor != null && cursor.getType() != Material.AIR) {
                            if (clicked == null || clicked.getType() == Material.AIR) {
                                event.setCurrentItem(cursor.clone());
                                event.setCursor(new ItemStack(Material.AIR));
                                event.setResult(Result.DENY);
                            } else {
                                if (ToolsItem.isSameItem(cursor, clicked, false)) {
                                    int clickedAmount = clicked.getAmount();
                                    int cursorAmount = cursor.getAmount();

                                    int total = clickedAmount + cursorAmount;
                                    int maxStack = clicked.getType().getMaxStackSize();
                                    if (total <= maxStack) {
                                        ItemStack combinedClone = clicked.clone();
                                        combinedClone.setAmount(total);
                                        event.setCurrentItem(combinedClone);
                                        event.setCursor(new ItemStack(Material.AIR));
                                        event.setResult(Result.DENY);
                                    } else {
                                        int left = total - maxStack;

                                        ItemStack maxClone = clicked.clone();
                                        maxClone.setAmount(maxStack);
                                        event.setCurrentItem(maxClone);

                                        if (left > 0) {
                                            ItemStack leftClone = clicked.clone();
                                            leftClone.setAmount(left);
                                            event.setCursor(leftClone);
                                        }
                                        event.setResult(Result.DENY);
                                    }
                                } else {
                                    ItemStack clickedClone = clicked.clone();
                                    ItemStack cursorClone = cursor.clone();
                                    event.setCurrentItem(cursorClone);
                                    event.setCursor(clickedClone);
                                    event.setResult(Result.DENY);
                                }
                            }
                        }
                    }
                } else if (event.isRightClick()) {
                    FuelRecipe fuelRecipe = Recipes.getInstance().getFuelRecipe(cursor);

                    if (fuelRecipe != null && !fuelRecipe.getInfo().getOwner().equals(RecipeOwner.MINECRAFT)) {
                        if (cursor != null && cursor.getType() != Material.AIR) {
                            if (clicked == null || clicked.getType() == Material.AIR) {
                                int cursorAmount = cursor.getAmount();
                                ItemStack cursorClone = cursor.clone();
                                cursorClone.setAmount(cursorAmount - 1);

                                ItemStack singleClone = cursor.clone();
                                singleClone.setAmount(1);
                                event.setCurrentItem(singleClone);
                                event.setCursor(cursorClone);
                                event.setResult(Result.DENY);
                            } else {
                                if (ToolsItem.isSameItem(cursor, clicked, false)) {
                                    int clickedAmount = clicked.getAmount();
                                    int cursorAmount = cursor.getAmount();

                                    int maxStack = clicked.getType().getMaxStackSize();

                                    if (clickedAmount + 1 < maxStack) {
                                        ItemStack clickedClone = clicked.clone();
                                        clickedClone.setAmount(clickedAmount + 1);
                                        event.setCurrentItem(clickedClone);

                                        ItemStack cursorClone = cursor.clone();
                                        cursorClone.setAmount(cursorAmount - 1);
                                        event.setCursor(cursorClone);
                                        event.setResult(Result.DENY);
                                    }
                                } else {
                                    ItemStack clickedClone = clicked.clone();
                                    ItemStack cursorClone = cursor.clone();
                                    event.setCurrentItem(cursorClone);
                                    event.setCursor(clickedClone);
                                    event.setResult(Result.DENY);
                                }
                            }
                        }
                    }
                }

                break;

            case 2: // RESULT slot
                break;

            default: // player inventory - Shift+Click handling in player inventory while having furnace UI opened
                if (slot == -999 || !event.isShiftClick() || clicked == null || clicked.getType() == Material.AIR) {
                    break; // abort if clicked outside of inventory OR not shift+click OR clicked on empty slot
                }

                // Get the target slot for the shift+click
                // First checks if the setting is for normal shift+click mode
                // Then checks if the clicked item is a fuel recipe and sends it to fuel slot if so, otherwise to ingredient slot
                // If it's left/right click mode then see if it's right click and send to fuel slot otherwise to ingredient slot
                int targetSlot = 0;

                if (Settings.getInstance().getFurnaceShiftClick() == 'f' || event.isRightClick()) {
                    if (RecipeManager.getRecipes().getFuelRecipe(clicked) != null) {
                        targetSlot = 1;
                    }
                }

                ItemStack item = inventory.getItem(targetSlot); // Get the item at the target slot
                boolean similarItems = clicked.isSimilar(item); // Check if the clicked item is similar to the item at the targeted slot

                // Check if it's normal shift+click mode setting and if targeted slot is the fuel slot and there is an item there but it's not similar to our clicked item
                if (Settings.getInstance().getFurnaceShiftClick() == 'f' && targetSlot == 1 && item != null && !similarItems) {
                    targetSlot = 0; // change the target slot to ingredient slot
                    item = inventory.getItem(targetSlot); // get the item at the new set slot
                    similarItems = clicked.isSimilar(item); // update similarity check
                }

                if (item == null || item.getType() == Material.AIR) { // If targeted item slot is empty
                    if (targetSlot == 1) {
                        inventory.setItem(targetSlot, clicked); // send the item to the slot
                        event.setCurrentItem(null); // clear the clicked slot

                        event.setCancelled(true); // cancel only if we're going to mess with the items
                        new UpdateInventory(player, 0); // update inventory to see the changes client-side
                    } else {
                        SmeltRecipe recipe = RecipeManager.getRecipes().getSmeltRecipe(clicked);

                        if (recipe != null) {
                            data.setFuelerUUID(player.getUniqueId());

                            ItemStack recipeIngredient = recipe.getIngredient();
                            if (ToolsItem.isSameItem(clicked, recipeIngredient, true)) {
                                data = Furnaces.get(furnace.getLocation());
                                fuel = data.getFuel();

                                if (fuel == null) {
                                    fuel = inventory.getFuel();
                                }

                                ItemStack recipeFuel = recipe.getFuel();

                                if (recipeFuel != null && !ToolsItem.isSameItem(recipeFuel, fuel, true)) {
                                    event.setCancelled(true);
                                } else {
                                    Args a = Args.create().player(data.getFuelerUUID()).location(furnace.getLocation()).recipe(recipe).result(recipe.getResult()).inventory(inventory).extra(inventory.getSmelting()).build();
                                    ItemResult result = recipe.getResult(a);

                                    if (furnaceHandleFlaggable(recipe, a, false, true) && (result == null || furnaceHandleFlaggable(result, a, false, true)) && isRecipeSameAsResult(a)) {
                                        inventory.setItem(targetSlot, clicked); // send the item to the slot
                                        event.setCurrentItem(null); // clear the clicked slot
                                        ToolsItem.updateFurnaceCookTimeDelayed(furnace, (short) (200 - recipe.getCookTicks()));
                                    } else {
                                        ToolsItem.updateFurnaceCookTimeDelayed(furnace, (short) 0);
                                    }
                                }
                            } else {
                                event.setCancelled(true);
                            }
                            event.setCancelled(true); // cancel only if we're going to mess with the items
                            new UpdateInventory(player, 0); // update inventory to see the changes client-side
                        }
                    }
                } else {
                    // Otherwise the targeted slot contains some item, need to identify if we can stack over it

                    int maxStack = Math.min(inventory.getMaxStackSize(), item.getType().getMaxStackSize()); // see how much we can place on that slot
                    int itemAmount = item.getAmount(); // get how many items there are in the stack

                    if (similarItems && itemAmount < maxStack) { // if item has room for more and they're similar
                        event.setCancelled(true); // cancel only if we're going to mess with the items
                        data.setFuelerUUID(player.getUniqueId());

                        int amount = itemAmount + clicked.getAmount(); // add the stacks together
                        int diff = amount - maxStack; // check to see if there are any leftovers

                        item.setAmount(Math.min(amount, maxStack)); // set the amount of targeted slot to the added amount OR max stack if it's exceeded

                        if (diff > 0) {
                            clicked.setAmount(diff); // reduce stack amount from clicked stack if there are leftovers
                        } else {
                            event.setCurrentItem(null); // entirely remove the clicked stack if there are no leftovers
                        }

                        new UpdateInventory(player, 0); // update inventory to see the changes client-side
                    }
                }
        }
    }

    private boolean isRecipeSameAsResult(Args a) {
        boolean isSame = false;
        ItemStack smelted = a.inventory().getItem(2);

        if (smelted != null && smelted.getType() != Material.AIR) {
            ItemResult result = a.result();
            if (result != null) {
                isSame = ToolsItem.isSameItem(smelted, result, true);
            }
        } else {
            isSame = true;
        }

        return isSame;
    }

    private boolean furnaceHandleFlaggable(Flaggable flaggable, Args a, boolean craft, boolean sendReasons) {
        if (flaggable == null) {
            return false;
        }

        String msg = Messages.getInstance().parse("flag.prefix.furnace", "{location}", Tools.printLocation(a.location()));

        a.clear();

        if (flaggable.checkFlags(a)) {
            a.sendEffects(a.player(), msg);
        } else {
            if (sendReasons) {
                a.sendReasons(a.player(), msg);
            }
            return false;
        }

        a.clear();

        if (flaggable.sendPrepare(a)) {
            a.sendEffects(a.player(), msg);
        } else {
            if (sendReasons) {
                a.sendReasons(a.player(), msg);
            }
            return false;
        }

        if (craft) {
            a.clear();

            if (flaggable.sendCrafted(a)) {
                a.sendEffects(a.player(), msg);
            } else {
                if (sendReasons) {
                    a.sendReasons(a.player(), msg);
                }
                return false;
            }
        }

        a.clear();

        return true;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void furnaceBurn(FurnaceBurnEvent event) {
        short burnTime = 0;
        short cookTime = 0;

        final Furnace furnace = (Furnace) event.getBlock().getState();

        FurnaceInventory inventory = furnace.getInventory();

        Location furnaceLocation = furnace.getLocation();
        final FurnaceData data = Furnaces.get(furnaceLocation);

        ItemStack fuel = event.getFuel();

        final FuelRecipe fuelRecipe = RecipeManager.getRecipes().getFuelRecipe(fuel);

        if (fuelRecipe != null) {
            if (fuelRecipe.hasFlag(FlagType.REMOVE)) {
                event.setCancelled(true);
            }

            Args a = Args.create().player(data.getFuelerUUID()).location(furnaceLocation).recipe(fuelRecipe).inventory(inventory).extra(inventory.getSmelting()).build();

            if (!furnaceHandleFlaggable(fuelRecipe, a, true, false)) {
                event.setCancelled(true);
            }

            burnTime = (short) fuelRecipe.getBurnTicks();
        }

        data.setFuel(fuel);

        ItemStack ingredient = inventory.getSmelting();
        SmeltRecipe recipe = RecipeManager.getRecipes().getSmeltRecipe(ingredient);

        if (recipe != null) {
            if (recipe.hasFlag(FlagType.REMOVE)) {
                event.setCancelled(true);
            }

            ItemStack recipeFuel = recipe.getFuel();

            if (recipeFuel != null && !ToolsItem.isSameItem(recipeFuel, fuel, true)) {
                event.setCancelled(true);
            }

            Args a = Args.create().player(data.getFuelerUUID()).location(furnaceLocation).recipe(recipe).inventory(inventory).extra(inventory.getSmelting()).build();
            ItemResult result = recipe.getResult(a);

            boolean recipeFlaggable = furnaceHandleFlaggable(recipe, a, false, false);
            boolean resultFlaggable = false;
            if (result != null) {
                resultFlaggable = furnaceHandleFlaggable(result, a, false, false);
            }

            if (!isRecipeSameAsResult(a) || !recipeFlaggable || (result != null && !resultFlaggable)) {
                event.setCancelled(true);
            }

            cookTime = (short) (200 - recipe.getCookTicks());
        }

        if (fuelRecipe != null) {
            event.setBurnTime(burnTime);

            long randTime = (long) Math.floor(Math.random() * burnTime);
            Bukkit.getScheduler().runTaskLater(RecipeManager.getPlugin(), new Runnable() {
                public void run() {
                    Bukkit.getPluginManager().callEvent(new RecipeManagerFuelBurnRandomEvent(fuelRecipe, furnace, data.getFuelerUUID()));
                }
            }, randTime);

            Bukkit.getScheduler().runTaskLater(RecipeManager.getPlugin(), new Runnable() {
                public void run() {
                    Bukkit.getPluginManager().callEvent(new RecipeManagerFuelBurnEndEvent(fuelRecipe, furnace, data.getFuelerUUID()));
                }
            }, burnTime);
        }

        boolean isBurning;
        if (Version.has1_13Support()) {
            isBurning = furnace.getBurnTime() == 0; // TODO: This is a guess for 1.13 update, verify functionality
        } else {
            isBurning = furnace.getType() == Material.getMaterial("BURNING_FURNACE");
        }
        if (recipe != null && !isBurning) {
            runFurnaceUpdateLater(furnace.getBlock(), cookTime);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void randomBurn(RecipeManagerFuelBurnRandomEvent event) {
        FuelRecipe recipe = event.getRecipe();
        Furnace furnace = event.getFurnace();
        FurnaceInventory inventory = furnace.getInventory();
        Args a = Args.create().player(event.getFuelerUUID()).location(furnace.getLocation()).recipe(recipe).inventory(inventory).extra(inventory.getSmelting()).build();

        a.clear();

        String msg = Messages.getInstance().parse("flag.prefix.furnace", "{location}", Tools.printLocation(a.location()));

        if (recipe.sendFuelRandom(a)) {
            a.sendEffects(a.player(), msg);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void afterBurn(RecipeManagerFuelBurnEndEvent event) {
        FuelRecipe recipe = event.getRecipe();
        Furnace furnace = event.getFurnace();
        FurnaceInventory inventory = furnace.getInventory();
        Location furnaceLocation = furnace.getLocation();
        FurnaceData data = Furnaces.get(furnaceLocation);
        data.setFuel(null);

        Args a = Args.create().player(event.getFuelerUUID()).location(furnaceLocation).recipe(recipe).inventory(inventory).extra(inventory.getSmelting()).build();

        a.clear();

        String msg = Messages.getInstance().parse("flag.prefix.furnace", "{location}", Tools.printLocation(a.location()));

        if (recipe.sendFuelEnd(a)) {
            a.sendEffects(a.player(), msg);
        }
    }

    private void runFurnaceUpdateLater(Block block, short cookTime) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Furnace furnace = (Furnace) block.getState();

                furnace.setCookTime(cookTime);
                furnace.update();
            }
        }.runTaskLater(RecipeManager.getPlugin(), 0);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void furnaceSmelt(FurnaceSmeltEvent event) {
        Block block = event.getBlock();
        Furnace furnace = (Furnace) block.getState();
        FurnaceInventory inventory = furnace.getInventory();

        short cookTime = 0;
        ItemStack ingredient = inventory.getSmelting();
        SmeltRecipe recipe = RecipeManager.getRecipes().getSmeltRecipe(ingredient);

        if (recipe != null) {
            if (recipe.hasFlag(FlagType.REMOVE)) {
                event.setCancelled(true);
            }

            FurnaceData data = Furnaces.get(furnace.getLocation());

            Args a = Args.create().player(data.getFuelerUUID()).location(furnace.getLocation()).recipe(recipe).inventory(inventory).extra(inventory.getSmelting()).build();

            ItemResult result = recipe.getResult(a);

            event.setResult(event.getResult());

            boolean recipeFlaggable = furnaceHandleFlaggable(recipe, a, true, true);
            boolean resultFlaggable = false;
            if (result != null) {
                resultFlaggable = furnaceHandleFlaggable(result, a, true, true);
            }

            if (!isRecipeSameAsResult(a) || !recipeFlaggable || (result != null && !resultFlaggable)) {
                event.setResult(new ItemStack(Material.AIR));
            } else {
                if (a.result() == null || a.result().getType() == Material.AIR || result.hasFlag(FlagType.NO_RESULT)) {
                    event.setResult(new ItemStack(Material.AIR));
                } else {
                    event.setResult(result.toItemStack());

                    if (recipe.hasFlag(FlagType.INGREDIENT_CONDITION) || result.hasFlag(FlagType.INGREDIENT_CONDITION)) {
                        recipe.subtractIngredient(inventory, result, true);
                    }
                }
            }

            cookTime = (short) (200 - recipe.getCookTicks());
        }

        if (recipe != null) {
            ItemStack recipeFuel = recipe.getFuel();

            if (recipeFuel != null && !ToolsItem.isSameItem(recipeFuel, inventory.getFuel(), true)) {
               cookTime = 0;
            }

            runFurnaceUpdateLater(block, cookTime);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void furnaceTakeResult(FurnaceExtractEvent event) {
        try {
            if (event.getExpToDrop() == 0) {
                return;
            }

            BlockState state = event.getBlock().getState();

            if (!(state instanceof Furnace)) {
                return; // highly unlikely but better safe than sorry
            }

            SmeltRecipe recipe = furnaceResultRecipe((Furnace) state);

            if (recipe != null) {
                event.setExpToDrop(0);
            }
        } catch (Throwable e) {
            MessageSender.getInstance().error(null, e, event.getEventName() + " cancelled due to error:");
        }
    }

    private SmeltRecipe furnaceResultRecipe(Furnace furnace) {
        ItemStack ingredient = ToolsItem.nullIfAir(furnace.getInventory().getSmelting());
        SmeltRecipe smeltRecipe = null;
        ItemStack result = furnace.getInventory().getResult();

        if (ingredient == null) {
            // Guess recipe by result - inaccurate

            if (result == null) {
                return null;
            }

            for (SmeltRecipe r : RecipeManager.getRecipes().indexSmelt.values()) {
                if (result.isSimilar(r.getResult())) {
                    smeltRecipe = r;
                    break;
                }
            }
        } else {
            smeltRecipe = RecipeManager.getRecipes().getSmeltRecipe(ingredient);
        }

        return smeltRecipe;
    }

    /*
     * Furnace monitor events
     */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void blockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        Location location = block.getLocation();

        if (type == Material.FURNACE || (!Version.has1_13Support() && type == Material.getMaterial("BURNING_FURNACE"))) {
            Furnaces.add(location);
        } else if (type == Material.BREWING_STAND) {
            BrewingStands.add(location);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void blockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        Location location = block.getLocation();

        if (type == Material.FURNACE || (!Version.has1_13Support() && type == Material.getMaterial("BURNING_FURNACE"))) {
            Furnaces.remove(location);
        } else if (type == Material.BREWING_STAND) {
            BrewingStands.remove(location);
        }
    }

    /*
     * Marked item monitor events
     */

    @EventHandler(priority = EventPriority.MONITOR)
    public void playerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());

        if (Settings.getInstance().getUpdateBooks()) {
            RecipeBooks.getInstance().updateBook(player, item);
        }

        if (Settings.getInstance().getFixModResults()) {
            itemProcess(item);
        }
    }

    private void itemProcess(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return;
        }

        List<String> lore = meta.getLore();

        if (lore == null || lore.isEmpty()) {
            return;
        }

        for (int i = 0; i < lore.size(); i++) {
            String s = lore.get(i);

            if (s != null && s.startsWith(Recipes.RECIPE_ID_STRING)) {
                lore.remove(i);
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
        }
    }

    /*
     * Update check notifier
     */

    @EventHandler
    public void playerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Players.addJoined(player);

        if (Settings.getInstance().getUpdateCheckEnabled() && player.hasPermission("recipemanager.command.rmupdate")) {
            String latestVersion = Updater.getLatestVersion();
            String currentVersion = Updater.getCurrentVersion();

            if (latestVersion != null) {
                int compare = Updater.compareVersions();

                if (compare == -1) {
                    MessageSender.getInstance().send(player, "[RecipeManager] New version: <green>" + latestVersion + "<reset>! You're using <yellow>" + currentVersion + "<reset>, grab it at: <light_purple>" + Updater.getLatestLink());
                } else if (compare == 2) {
                    MessageSender.getInstance().send(player, "[RecipeManager] New alpha/beta version: <green>" + latestVersion + " " + Updater.getLatestBetaStatus() + "<reset>! You're using <yellow>" + currentVersion + "<reset>, grab it at: <light_purple>" + Updater.getLatestLink());
                } else if (compare == 3) {
                    MessageSender.getInstance().send(player, "[RecipeManager] BukkitDev has a different alpha/beta version: <green>" + latestVersion + " " + Updater.getLatestBetaStatus() + "<reset>! You're using <yellow>" + currentVersion + " " + Updater.getCurrentBetaStatus() + "<reset>, grab it at: <light_purple>" + Updater.getLatestLink());
                }
            }
        }
    }

    @EventHandler
    public void brewEvent(BrewEvent event) {
        BrewerInventory inventory = event.getContents();

        ItemStack ingredient = inventory.getIngredient();
        BrewRecipe recipe = RecipeManager.getRecipes().getBrewRecipe(ingredient);

        if (recipe != null) {
            Block block = event.getBlock();
            Location location = block.getLocation();
            BrewingStandData data = BrewingStands.get(location);
            Args a = Args.create().inventory(inventory).location(location).player(data.getFuelerUUID()).recipe(recipe).build();
            ItemResult result = recipe.getResult(a);

            if (result != null && recipe.sendPrepare(a)) {
                if (recipe.checkFlags(a) && result.checkFlags(a)) {
                    @SuppressWarnings("unchecked")
                    List<Boolean> potionBools = (List<Boolean>) a.extra();

                    ItemStack bukkitResult = result.toItemStack();

                    boolean cancel = false;
                    if (potionBools.get(0)) {
                        inventory.setItem(0, bukkitResult.clone());
                        cancel = true;
                    }

                    if (potionBools.get(1)) {
                        inventory.setItem(1, bukkitResult.clone());
                        cancel = true;
                    }

                    if (potionBools.get(2)) {
                        inventory.setItem(2, bukkitResult.clone());
                        cancel = true;
                    }

                    if (cancel) {
                        event.setCancelled(true);
                        ItemStack originalIngredient = inventory.getItem(3);
                        originalIngredient.setAmount(originalIngredient.getAmount() - 1);

                        inventory.setItem(3, originalIngredient);
                    }
                }
            }
        }
    }

    @EventHandler
    public void inventoryMove(InventoryMoveItemEvent event) {
        Inventory dest = event.getDestination();

        if (dest instanceof FurnaceInventory) {
            FurnaceInventory furnaceInventory = (FurnaceInventory) dest;

            ItemStack smeltingItem = furnaceInventory.getSmelting();

            if (smeltingItem == null || smeltingItem.getType() == Material.AIR) {
                ItemStack movedItem = event.getItem();

                SmeltRecipe recipe = RecipeManager.getRecipes().getSmeltRecipe(movedItem);

                if (recipe != null) {
                    if (recipe.hasFlag(FlagType.REMOVE)) {
                        event.setCancelled(true);
                    }

                    Furnace furnace = furnaceInventory.getHolder();
                    FurnaceData data = Furnaces.get(furnace.getLocation());

                    Args a = Args.create().player(data.getFuelerUUID()).location(furnace.getLocation()).recipe(recipe).result(recipe.getResult()).inventory(furnaceInventory).extra(furnaceInventory.getSmelting()).build();

                    if (furnaceHandleFlaggable(recipe, a, false, true) && isRecipeSameAsResult(a)) {
                        ToolsItem.updateFurnaceCookTimeDelayed(furnace, (short) (200 - recipe.getCookTicks()));
                    } else {
                        ToolsItem.updateFurnaceCookTimeDelayed(furnace, (short) 0);
                    }
                }
            }
        }
    }
}
