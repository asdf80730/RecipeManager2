package haveric.recipeManager.recipes.fuel;

import haveric.recipeManager.RecipeManager;
import haveric.recipeManager.flag.FlagType;
import haveric.recipeManager.flag.Flags;
import haveric.recipeManager.messages.Messages;
import haveric.recipeManager.recipes.BaseRecipe;
import haveric.recipeManager.tools.ToolsItem;
import haveric.recipeManager.common.RMCChatColor;
import haveric.recipeManager.common.RMCVanilla;
import haveric.recipeManager.common.recipes.RMCRecipeType;
import haveric.recipeManager.common.util.RMCUtil;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FuelRecipe extends BaseRecipe {
    private ItemStack ingredient;
    private float minTime;
    private float maxTime;

    public FuelRecipe() {

    }

    public FuelRecipe(Material type, float burnTime) {
        setIngredient(new ItemStack(type, 1, RMCVanilla.DATA_WILDCARD));
        minTime = burnTime;
    }

    public FuelRecipe(Material type, float newMinTime, float newMaxTime) {
        setIngredient(new ItemStack(type, 1, RMCVanilla.DATA_WILDCARD));
        minTime = newMinTime;
        maxTime = newMaxTime;
    }

    public FuelRecipe(Material type, short data, float burnTime) {
        setIngredient(new ItemStack(type, 1, data));
        minTime = burnTime;
    }

    public FuelRecipe(Material type, short data, float newMinTime, float newMaxTime) {
        setIngredient(new ItemStack(type, 1, data));
        minTime = newMinTime;
        maxTime = newMaxTime;
    }

    public FuelRecipe(BaseRecipe recipe) {
        super(recipe);

        if (recipe instanceof FuelRecipe) {
            FuelRecipe r = (FuelRecipe) recipe;

            if (r.ingredient == null) {
                ingredient = null;
            } else {
                ingredient = r.ingredient.clone();
            }

            minTime = r.minTime;
            maxTime = r.maxTime;
        }
    }

    public FuelRecipe(Flags flags) {
        super(flags);
    }

    public ItemStack getIngredient() {
        return ingredient;
    }

    public void setIngredient(ItemStack newIngredient) {
        ingredient = newIngredient;

        hash = ("fuel" + newIngredient.getType().toString() + ":" + newIngredient.getDurability()).hashCode();
    }

    public float getMinTime() {
        return minTime;
    }

    /**
     * Set minimum time it can burn (or fixed if max not defined).
     *
     * @param newMinTime
     *            float value in seconds
     */
    public void setMinTime(float newMinTime) {
        minTime = newMinTime;
    }

    public float getMaxTime() {
        return maxTime;
    }

    /**
     * Set maximum time it can burn.<br>
     * NOTE: minimum time must be smaller than this and higher than -1
     *
     * @param newMaxTime
     *            float value in seconds
     */
    public void setMaxTime(float newMaxTime) {
        maxTime = newMaxTime;
    }

    /**
     * Get the burn time value, randomized if supported, in ticks (multiplied by 20).
     *
     * @return burn time in ticks
     */
    public int getBurnTicks() {
        float time;

        if (maxTime > minTime) {
            time = minTime + (maxTime - minTime) * RecipeManager.random.nextFloat();
        } else {
            time = minTime;
        }

        return (int) Math.round(20.0 * time);
    }

    @Override
    public List<String> getIndexes() {
        String indexString = "" + ingredient.getType().toString();

        if (ingredient.getDurability() != RMCVanilla.DATA_WILDCARD) {
            indexString += ":" + ingredient.getDurability();
        }

        return Collections.singletonList(indexString);
    }

    @Override
    public void resetName() {
        StringBuilder s = new StringBuilder();
        boolean removed = hasFlag(FlagType.REMOVE);

        s.append("fuel ");

        s.append(ingredient.getType().toString().toLowerCase());

        if (ingredient.getDurability() != RMCVanilla.DATA_WILDCARD) {
            s.append(':').append(ingredient.getDurability());
        }

        if (removed) {
            s.append(" [removed recipe]");
        }

        name = s.toString();
        customName = false;
    }

    public boolean hasIngredient() {
        return ingredient != null;
    }

    @Override
    public boolean isValid() {
        return hasIngredient();
    }

    @Override
    public String getInvalidErrorMessage() {
        return super.getInvalidErrorMessage() + " Needs an ingredient!";
    }

    @Override
    public RMCRecipeType getType() {
        return RMCRecipeType.FUEL;
    }

    @Override
    public List<String> printBookIndices() {
        List<String> print = new ArrayList<>();

        if (hasCustomName()) {
            print.add(RMCChatColor.ITALIC + getName());
        } else {
            print.add(ToolsItem.getName(ingredient) + " Fuel");
        }

        return print;
    }

    @Override
    public List<String> printBookRecipes() {
        List<String> recipes = new ArrayList<>();

        recipes.add(printBookResult());

        return recipes;
    }

    public String printBookResult() {
        StringBuilder s = new StringBuilder(256);

        s.append(Messages.getInstance().parse("recipebook.header.fuel"));

        if (hasCustomName()) {
            s.append('\n').append(RMCChatColor.BLACK).append(RMCChatColor.ITALIC).append(getName()).append(RMCChatColor.BLACK);
        }

        s.append("\n\n");
        s.append(Messages.getInstance().parse("recipebook.header.ingredient")).append(RMCChatColor.BLACK);
        s.append('\n').append(ToolsItem.print(ingredient, RMCChatColor.BLACK, RMCChatColor.BLACK));

        s.append("\n\n");
        s.append(Messages.getInstance().parse("recipebook.header.burntime")).append(RMCChatColor.BLACK);
        s.append('\n');

        if (maxTime > minTime) {
            s.append(Messages.getInstance().parse("recipebook.fuel.time.random", "{min}", RMCUtil.printNumber(minTime), "{max}", RMCUtil.printNumber(maxTime)));
        } else {
            s.append(Messages.getInstance().parse("recipebook.fuel.time.fixed", "{time}", RMCUtil.printNumber(minTime)));
        }

        return s.toString();
    }

    @Override
    public List<String> getRecipeIndexesForInput(List<ItemStack> ingredients, ItemStack result) {
        List<String> recipeIndexes = new ArrayList<>();
        if (ingredients.size() == 1) {
            ItemStack ingredient = ingredients.get(0);
            recipeIndexes.add(ingredient.getType().toString());
            recipeIndexes.add(ingredient.getType().toString() + ":" + ingredient.getDurability());
        }

        return recipeIndexes;
    }
}
