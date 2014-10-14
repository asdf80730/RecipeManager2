package haveric.recipeManager.recipes;

import haveric.recipeManager.Messages;
import haveric.recipeManager.flags.Args;
import haveric.recipeManager.flags.FlagDisplayResult;
import haveric.recipeManager.flags.FlagIngredientCondition;
import haveric.recipeManager.flags.FlagIngredientCondition.Conditions;
import haveric.recipeManager.flags.FlagKeepItem;
import haveric.recipeManager.flags.FlagType;
import haveric.recipeManager.flags.Flags;
import haveric.recipeManager.tools.ToolsItem;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;



public class WorkbenchRecipe extends MultiResultRecipe {
    protected WorkbenchRecipe() {
    }

    public WorkbenchRecipe(BaseRecipe recipe) {
        super(recipe);
    }

    public WorkbenchRecipe(Flags flags) {
        super(flags);
    }

    /**
     * Generate a display result for showing off all results (if available).
     *
     * @param a
     * @return the result if it's only one or a special multi-result information item
     */
    public ItemResult getDisplayResult(Args a) {
        a.clear();

        if (!checkFlags(a)) {
            a.sendReasons(a.player(), Messages.FLAG_PREFIX_RECIPE.get());

            return ToolsItem.create(Material.FIRE, 0, 0, Messages.CRAFT_RESULT_DENIED_TITLE.get(), Messages.CRAFT_RESULT_DENIED_INFO.get());
        }

        List<ItemResult> displayResults = new ArrayList<ItemResult>();
        float failChance = 0;
        int secretNum = 0;
        float secretChance = 0;
        int unavailableNum = 0;
        float unavailableChance = 0;
        int displayNum = 0;

        for (ItemResult r : getResults()) {
            r = r.clone();
            a.clearReasons();
            a.setResult(r);
            r.sendPrepare(a);

            if (r.checkFlags(a)) {
                if (r.hasFlag(FlagType.SECRET)) {
                    secretNum++;
                    secretChance += r.getChance();
                } else if (r.getType() == Material.AIR) {
                    failChance = r.getChance();
                } else {
                    displayResults.add(r);

                    a.sendEffects(a.player(), Messages.FLAG_PREFIX_RESULT.get("{item}", ToolsItem.print(r)));
                }
            } else {
                unavailableNum++;
                unavailableChance += r.getChance();

                if (!r.hasFlag(FlagType.SECRET)) {
                    a.sendReasons(a.player(), Messages.FLAG_PREFIX_RESULT.get("{item}", ToolsItem.print(r)));
                }
            }
        }

        displayNum = displayResults.size();
        boolean recieve = (secretNum + displayNum) > 0;

        FlagDisplayResult flag;
        if (a.hasRecipe()) {
            flag = a.recipe().getFlag(FlagDisplayResult.class);
        } else {
            flag = null;
        }

        if (flag != null) {
            if (!recieve && flag.isSilentFail()) {
                return null;
            }

            ItemStack display = flag.getDisplayItem();

            if (display != null) {
                return new ItemResult(display);
            } else if (displayNum > 0) {
                return displayResults.get(0);
            }
        }

        if (unavailableNum == 0 && failChance == 0) {
            if (displayNum == 1 && secretNum == 0) {
                return displayResults.get(0);
            } else if (secretNum == 1 && displayNum == 0) {
                return ToolsItem.create(Material.CHEST, 0, 0, Messages.CRAFT_RESULT_RECIEVE_TITLE_UNKNOWN.get());
            }
        }

        List<String> lore = new ArrayList<String>();
        String title = null;

        if (recieve) {
            title = Messages.CRAFT_RESULT_RECIEVE_TITLE_RANDOM.get();
        } else {
            title = Messages.CRAFT_RESULT_NORECIEVE_TITLE.get();
            lore.add(Messages.CRAFT_RESULT_DENIED_INFO.get());
        }

        for (ItemResult r : displayResults) {
            String cloneMessage = "";
            if (r.hasFlag(FlagType.CLONEINGREDIENT)) {
                cloneMessage = Messages.FLAG_CLONE_RESULTDISPLAY.get();
            }
            lore.add(Messages.CRAFT_RESULT_LIST_ITEM.get("{chance}", formatChance(r.getChance()), "{item}", ToolsItem.print(r), "{clone}", cloneMessage));
        }

        if (failChance > 0) {
            lore.add(Messages.CRAFT_RESULT_LIST_FAILURE.get("{chance}", formatChance(failChance)));
        }

        if (secretNum > 0) {
            lore.add(Messages.CRAFT_RESULT_LIST_SECRETS.get("{chance}", formatChance(secretChance), "{num}", String.valueOf(secretNum)));
        }

        if (unavailableNum > 0) {
            lore.add(Messages.CRAFT_RESULT_LIST_UNAVAILABLE.get("{chance}", formatChance(unavailableChance), "{num}", String.valueOf(unavailableNum)));
        }

        Material displayMaterial;
        if (recieve) {
            displayMaterial = Material.CHEST;
        } else {
            displayMaterial = Material.FIRE;
        }
        return ToolsItem.create(displayMaterial, 0, 0, title, lore);
    }

    private String formatChance(float chance) {
        String formatString;

        if (chance == 100) {
            formatString = "100%";
        } else if (Math.round(chance) == chance) {
            formatString = String.format("%4.0f%%", chance);
        } else {
            formatString = String.format("%4.1f%%", chance);
        }

        return formatString;
    }

    public int getCraftableTimes(CraftingInventory inv) {
        int craftAmount = inv.getMaxStackSize();

        for (ItemStack i : inv.getMatrix()) {
            if (i != null && i.getType() != Material.AIR) {
                craftAmount = Math.min(i.getAmount(), craftAmount);
            }
        }

        return craftAmount;
    }

    public void subtractIngredients(CraftingInventory inv, ItemResult result, boolean onlyExtra) {
        FlagIngredientCondition flagIC;
        if (hasFlag(FlagType.INGREDIENTCONDITION)) {
            flagIC = getFlag(FlagIngredientCondition.class);
        } else {
            flagIC = null;
        }
        FlagKeepItem flagKI;
        if (hasFlag(FlagType.KEEPITEM)) {
            flagKI = getFlag(FlagKeepItem.class);
        } else {
            flagKI = null;
        }

        if (flagIC == null && result != null && result.hasFlag(FlagType.INGREDIENTCONDITION)) {
            flagIC = result.getFlag(FlagIngredientCondition.class);
        }

        if (flagKI == null && result != null && result.hasFlag(FlagType.KEEPITEM)) {
            flagKI = result.getFlag(FlagKeepItem.class);
        }

        for (int i = 1; i < 10; i++) {
            ItemStack item = inv.getItem(i);

            if (item != null) {
                if (flagKI != null) {
                    if (flagKI.getItem(item) != null) {
                        continue;
                    }
                }

                int amt = item.getAmount();
                int newAmt = amt;

                if (flagIC != null) {
                    Conditions cond = flagIC.getIngredientConditions(item);

                    if (cond != null && cond.getAmount() > 1) {
                        newAmt -= (cond.getAmount() - 1);
                    }
                }

                if (!onlyExtra) {
                    newAmt -= 1;
                }

                if (amt != newAmt) {
                    if (newAmt > 0) {
                        item.setAmount(newAmt);
                    } else {
                        inv.clear(i);
                    }
                }
            }
        }
    }
}