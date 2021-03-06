package haveric.recipeManager.recipes.campfire;

import haveric.recipeManager.RecipeManager;
import haveric.recipeManager.Vanilla;
import haveric.recipeManager.flag.FlagType;
import haveric.recipeManager.flag.Flags;
import haveric.recipeManager.messages.Messages;
import haveric.recipeManager.recipes.BaseRecipe;
import haveric.recipeManager.recipes.ItemResult;
import haveric.recipeManager.recipes.SingleResultRecipe;
import haveric.recipeManager.tools.ToolsItem;
import haveric.recipeManager.common.RMCChatColor;
import haveric.recipeManager.common.recipes.RMCRecipeType;
import haveric.recipeManager.common.util.RMCUtil;
import org.bukkit.Material;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

import java.util.ArrayList;
import java.util.List;

public class RMCampfireRecipe extends SingleResultRecipe {
    private List<Material> ingredientChoice = new ArrayList<>();

    private float minTime = Vanilla.CAMPFIRE_RECIPE_TIME;
    private float maxTime = -1;
    private float experience = 2;

    public RMCampfireRecipe() {

    }

    public RMCampfireRecipe(BaseRecipe recipe) {
        super(recipe);

        if (recipe instanceof RMCampfireRecipe) {
            RMCampfireRecipe r = (RMCampfireRecipe) recipe;

            if (r.ingredientChoice == null) {
                ingredientChoice = null;
            } else {
                ingredientChoice.addAll(r.ingredientChoice);
            }

            minTime = r.minTime;
            maxTime = r.maxTime;
            experience = r.experience;
            hash = r.hash;
        }
    }

    public RMCampfireRecipe(Flags flags) {
        super(flags);
    }

    public RMCampfireRecipe(CampfireRecipe recipe) {
        RecipeChoice choice = recipe.getInputChoice();
        if (choice instanceof RecipeChoice.MaterialChoice) {
            RecipeChoice.MaterialChoice materialChoice = (RecipeChoice.MaterialChoice) choice;

            setIngredientChoice(materialChoice.getChoices());
        }

        setResult(recipe.getResult());
    }

    public List<Material> getIngredientChoice() {
        return ingredientChoice;
    }

    public void setIngredientChoice(List<Material> materials) {
        RecipeChoice.MaterialChoice materialChoice = new RecipeChoice.MaterialChoice(materials);
        setIngredientChoice(materialChoice);
    }

    private void setIngredientChoice(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice) {
            ingredientChoice.clear();
            RecipeChoice.MaterialChoice materialChoice = (RecipeChoice.MaterialChoice) choice;
            ingredientChoice.addAll(materialChoice.getChoices());

            String newHash = "campfire";

            int size = ingredientChoice.size();
            for (int i = 0; i < size; i++) {
                newHash += ingredientChoice.get(i).toString();

                if (i + 1 < size) {
                    newHash += ", ";
                }
            }

            hash = newHash.hashCode();
        }
    }

    public boolean hasCustomTime() {
        return minTime != Vanilla.CAMPFIRE_RECIPE_TIME;
    }

    public float getMinTime() {
        return minTime;
    }

    /**
     * @param newMinTime
     *            min random time range (seconds)
     */
    public void setMinTime(float newMinTime) {
        minTime = newMinTime;
    }

    public float getMaxTime() {
        return maxTime;
    }

    /**
     * @param newMaxTime
     *            max random time range (seconds) or set to -1 to disable
     */
    public void setMaxTime(float newMaxTime) {
        maxTime = newMaxTime;
    }

    /**
     * @return if recipe has random time range
     */
    public boolean hasRandomTime() {
        return maxTime > minTime;
    }

    /**
     * @return min time or if hasRandomTime() gets a random between min and max time.
     */
    public float getCookTime() {
        float time;

        if (hasRandomTime()) {
            time = minTime + ((maxTime - minTime) * RecipeManager.random.nextFloat());
        } else {
            time = minTime;
        }

        return time;
    }

    /**
     * @return getCookTime() multiplied by 20.0 and rounded
     */
    public int getCookTicks() {
        return Math.round(getCookTime() * 20.0f);
    }

    public float getExperience() { return experience; }

    public void setExperience(float newExperience) { experience = newExperience; }

    @Override
    public void resetName() {
        StringBuilder s = new StringBuilder();
        boolean removed = hasFlag(FlagType.REMOVE);

        s.append("campfire ");

        int size = ingredientChoice.size();
        for (int i = 0; i < size; i++) {
            s.append(ingredientChoice.get(i).toString().toLowerCase());

            if (i + 1 < size) {
                s.append(", ");
            }
        }

        s.append(" to ");
        s.append(getResultString());
        if (removed) {
            s.append(" [removed recipe]");
        }

        name = s.toString();
        customName = false;
    }

    @Override
    public List<String> getIndexes() {
        List<String> indexString = new ArrayList<>();

        for (Material material : ingredientChoice) {
            indexString.add(material.toString());
        }

        return indexString;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof CampfireRecipe && hash == obj.hashCode();
    }

    @Override
    public CampfireRecipe toBukkitRecipe(boolean vanilla) {
        if (!hasIngredientChoice() || !hasResult()) {
            return null;
        }

        return new CampfireRecipe(getNamespacedKey(), getResult(), new RecipeChoice.MaterialChoice(ingredientChoice), experience, getCookTicks());
    }

    public boolean hasIngredientChoice() {
        return ingredientChoice != null;
    }

    @Override
    public boolean isValid() {
        return hasIngredientChoice() && (hasFlag(FlagType.REMOVE) || hasFlag(FlagType.RESTRICT) || hasResult());
    }

    @Override
    public String getInvalidErrorMessage() {
        return super.getInvalidErrorMessage() + " Needs a result and ingredient!";
    }

    @Override
    public RMCRecipeType getType() {
        return RMCRecipeType.CAMPFIRE;
    }

    @Override
    public String printBookResult(ItemResult result) {
        StringBuilder s = getHeaderResult("campfire");

        String print = getConditionResultName(result);

        if (print.isEmpty()) {
            print = ToolsItem.printChoice(ingredientChoice, RMCChatColor.RESET, RMCChatColor.BLACK);
        }

        s.append('\n').append(print);

        s.append("\n\n");
        s.append(Messages.getInstance().parse("recipebook.header.cooktime")).append(RMCChatColor.BLACK);
        s.append('\n');

        if (hasCustomTime()) {
            if (maxTime > minTime) {
                s.append(Messages.getInstance().parse("recipebook.smelt.time.random", "{min}", RMCUtil.printNumber(minTime), "{max}", RMCUtil.printNumber(maxTime)));
            } else {
                if (minTime <= 0) {
                    s.append(Messages.getInstance().parse("recipebook.smelt.time.instant"));
                } else {
                    s.append(Messages.getInstance().parse("recipebook.smelt.time.fixed", "{time}", RMCUtil.printNumber(minTime)));
                }
            }
        } else {
            s.append(Messages.getInstance().parse("recipebook.smelt.time.normal", "{time}", RMCUtil.printNumber(minTime)));
        }

        return s.toString();
    }

    @Override
    public int findItemInIngredients(Material type, Short data) {
        int found = 0;

        for (Material material : ingredientChoice) {
            if (type == material) {
                found++;
                break;
            }
        }

        return found;
    }

    @Override
    public List<String> getRecipeIndexesForInput(List<ItemStack> ingredients, ItemStack result) {
        List<String> recipeIndexes = new ArrayList<>();
        if (ingredients.size() == 1) {
            recipeIndexes.add(ingredients.get(0).getType().toString());
        }

        return recipeIndexes;
    }
}
