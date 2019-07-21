package haveric.recipeManager.recipes.combine;

import haveric.recipeManager.flag.FlagType;
import haveric.recipeManager.flag.Flags;
import haveric.recipeManager.flag.conditions.ConditionsIngredient;
import haveric.recipeManager.flag.flags.FlagIngredientCondition;
import haveric.recipeManager.flag.flags.FlagItemName;
import haveric.recipeManager.messages.Messages;
import haveric.recipeManager.recipes.BaseRecipe;
import haveric.recipeManager.recipes.ItemResult;
import haveric.recipeManager.recipes.WorkbenchRecipe;
import haveric.recipeManager.tools.Tools;
import haveric.recipeManager.tools.ToolsItem;
import haveric.recipeManager.tools.Version;
import haveric.recipeManagerCommon.RMCChatColor;
import haveric.recipeManagerCommon.RMCVanilla;
import haveric.recipeManagerCommon.recipes.RMCRecipeType;
import haveric.recipeManagerCommon.util.RMCUtil;
import org.apache.commons.lang.mutable.MutableInt;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class CombineRecipe extends WorkbenchRecipe {
    private List<List<Material>> ingredientChoiceList = new ArrayList<>();
    private List<ItemStack> ingredients;

    public CombineRecipe() {
    }

    public CombineRecipe(ShapelessRecipe recipe) {
        if (Version.has1_13Support()) {
            setIngredientChoiceList(recipe.getChoiceList());
        } else {
            setIngredients(recipe.getIngredientList());
        }
        setResult(recipe.getResult());
    }

    public CombineRecipe(BaseRecipe recipe) {
        super(recipe);

        if (recipe instanceof CombineRecipe) {
            CombineRecipe r = (CombineRecipe) recipe;

            if (!r.ingredients.isEmpty()) {
                ingredients.addAll(r.getIngredients());
            }

            if (!r.ingredientChoiceList.isEmpty()) {
                for (List<Material> ingredientChoice : r.ingredientChoiceList) {
                    ArrayList<Material> cloneList = new ArrayList<>(ingredientChoice);
                    ingredientChoiceList.add(cloneList);
                }
            }

        }
    }

    public CombineRecipe(Flags flags) {
        super(flags);
    }

    /**
     * @return clone of ingredients list
     */
    public List<ItemStack> getIngredients() {
        List<ItemStack> clone = new ArrayList<>();

        for (ItemStack i : ingredients) {
            clone.add(i.clone());
        }

        return clone;
    }

    public void addIngredient(Material type) {
        addIngredient(1, type, (short) -1);
    }

    public void addIngredient(Material type, short data) {
        addIngredient(1, type, data);
    }

    public void addIngredient(ItemStack ingredient) {
        addIngredient(ingredient.getAmount(), ingredient.getType(), ingredient.getDurability());
    }

    public void addIngredient(int amount, Material type, short data) {
        if (ingredients == null) {
            ingredients = new ArrayList<>();
        }

        if ((ingredients.size() + amount) > 9) { // check if they're more than they should...
            throw new IllegalArgumentException("Recipe can't have more than 9 ingredients!");
        }

        while (amount-- > 0) {
            ingredients.add(new ItemStack(type, 1, data));
        }

        sort();
    }

    public void setIngredients(List<ItemStack> newIngredients) {
        // unstack ingredients
        ingredients = new ArrayList<>();
        int amount;

        for (ItemStack ingredient : newIngredients) {
            amount = ingredient.getAmount();

            while (amount-- > 0) {
                ingredients.add(new ItemStack(ingredient.getType(), 1, ingredient.getDurability()));
            }
        }

        if (ingredients.size() > 9) { // check if they're more than they should...
            throw new IllegalArgumentException("Recipe can't have more than 9 ingredients!");
        }

        sort();
    }

    public List<List<Material>> getIngredientChoiceList() {
        return ingredientChoiceList;
    }

    public void addIngredientChoice(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice) {
            RecipeChoice.MaterialChoice materialChoice = (RecipeChoice.MaterialChoice) choice;

            List<Material> choices = materialChoice.getChoices();
            ingredientChoiceList.add(choices);
        }

        updateHash();
    }

    public void setIngredientChoiceList(List<RecipeChoice> recipeChoices) {
        ingredientChoiceList.clear();

        for (RecipeChoice choice : recipeChoices) {
            addIngredientChoice(choice);
        }

        updateHash();
    }

    private void updateHash() {
        StringBuilder str = new StringBuilder("combine");

        if (Version.has1_13Support()) {
            int size = ingredientChoiceList.size();
            for (int i = 0; i < size; i++) {
                List<Material> ingredientChoice = ingredientChoiceList.get(i);
                for (Material material : ingredientChoice) {
                    str.append(material.toString()).append(';');
                }

                if (i + 1 < size) {
                    str.append(",");
                }
            }
        } else {
            for (ItemStack item : ingredients) {
                str.append(item.getType().toString()).append(':').append(item.getDurability()).append(';');
            }
        }

        hash = str.toString().hashCode();
    }

    private void sort() {
        // sort by type and data
        Tools.sortIngredientList(ingredients);

        updateHash();
    }

    @Override
    public void resetName() {
        StringBuilder s = new StringBuilder();
        boolean removed = hasFlag(FlagType.REMOVE);

        s.append("shapeless");
        s.append(" (");

        if (Version.has1_13Support()) {
            int ingredientChoiceListSize = ingredientChoiceList.size();

            for (int i = 0; i < ingredientChoiceListSize; i++) {
                List<Material> ingredientChoice = ingredientChoiceList.get(i);

                int ingredientChoiceSize = ingredientChoice.size();
                for (int j = 0; j < ingredientChoiceSize; j++) {
                    s.append(ingredientChoice.get(j).toString().toLowerCase());

                    if (j + 1 < ingredientChoiceSize) {
                        s.append(",");
                    }
                }

                if (i + 1 < ingredientChoiceListSize) {
                    s.append(" ");
                }
            }
        } else {
            int size = ingredients.size();

            for (int i = 0; i < size; i++) {
                ItemStack item = ingredients.get(i);

                if (item == null) {
                    s.append("air");
                } else {
                    s.append(item.getType().toString().toLowerCase());

                    if (item.getDurability() != RMCVanilla.DATA_WILDCARD) {
                        s.append(':').append(item.getDurability());
                    }
                }

                if (i < (size - 1)) {
                    s.append(' ');
                }
            }
        }

        s.append(") ");

        if (removed) {
            s.append("removed recipe");
        } else {
            s.append(getResultsString());
        }

        name = s.toString();
        customName = false;
    }

    @Override
    public ShapelessRecipe toBukkitRecipe(boolean vanilla) {
        if (Version.has1_13Support()) {
            if (!hasIngredientChoices() || !hasResults()) {
                return null;
            }
        } else {
            if (!hasIngredients() || !hasResults()) {
                return null;
            }
        }

        ShapelessRecipe bukkitRecipe;
        if (Version.has1_12Support()) {
            if (vanilla) {
                bukkitRecipe = new ShapelessRecipe(getNamespacedKey(), getFirstResult());
            } else {
                bukkitRecipe = new ShapelessRecipe(getNamespacedKey(), Tools.createItemRecipeId(getFirstResult(), getIndex()));
            }
        } else {
            if (vanilla) {
                bukkitRecipe = new ShapelessRecipe(getFirstResult());
            } else {
                bukkitRecipe = new ShapelessRecipe(Tools.createItemRecipeId(getFirstResult(), getIndex()));
            }
        }

        if (Version.has1_13Support()) {
            for (List<Material> materialChoice : ingredientChoiceList) {
                bukkitRecipe.addIngredient(new RecipeChoice.MaterialChoice(materialChoice));
            }
        } else {
            for (ItemStack item : ingredients) {
                bukkitRecipe.addIngredient(item.getAmount(), item.getType(), item.getDurability());
            }
        }

        return bukkitRecipe;
    }

    public boolean hasIngredients() {
        return ingredients != null && !ingredients.isEmpty();
    }

    public boolean hasIngredientChoices() {
        return ingredientChoiceList != null && !ingredientChoiceList.isEmpty();
    }

    @Override
    public boolean isValid() {
        if (Version.has1_13Support()) {
            return hasIngredientChoices() && (hasFlag(FlagType.REMOVE) || hasFlag(FlagType.RESTRICT) || hasResults());
        } else {
            return hasIngredients() && (hasFlag(FlagType.REMOVE) || hasFlag(FlagType.RESTRICT) || hasResults());
        }
    }

    @Override
    public RMCRecipeType getType() {
        return RMCRecipeType.COMBINE;
    }
    /*
    public String printBookIndex() {
        String print;

        if (hasCustomName()) {
            print = RMCChatColor.ITALIC + getName();
        } else {
            ItemResult result = getFirstResult();

            if (result.hasFlag(FlagType.ITEM_NAME)) {
                FlagItemName flag = (FlagItemName)result.getFlag(FlagType.ITEM_NAME);
                print = RMCUtil.parseColors(flag.getItemName(), false);
            } else {
                print = ToolsItem.getName(getFirstResult());
            }
        }

        return print;
    }
    */

    public List<String> printBookIndices() {
        List<String> print = new ArrayList<>();

        if (hasFlag(FlagType.INDIVIDUAL_RESULTS)) {
            for (ItemResult result : getResults()) {
                print.add(getResultPrintName(result));
            }
        } else {
            print.add(getResultPrintName(getFirstResult()));
        }

        return print;
    }

    private String getResultPrintName(ItemResult result) {
        String print;

        if (result.hasFlag(FlagType.ITEM_NAME)) {
            FlagItemName flag = (FlagItemName)result.getFlag(FlagType.ITEM_NAME);
            print = RMCUtil.parseColors(flag.getItemName(), false);
        } else {
            print = ToolsItem.getName(getFirstResult());
        }

        return print;
    }

    @Override
    public List<String> printBookRecipes() {
        List<String> recipes = new ArrayList<>();

        if (hasFlag(FlagType.INDIVIDUAL_RESULTS)) {
            for (ItemResult result : getResults()) {
                recipes.add(printBookResult(result));
            }
        } else {
            recipes.add(printBookResult(getFirstResult()));
        }

        return recipes;
    }

    private String printBookResult(ItemResult result) {
        StringBuilder s = new StringBuilder(256);

        s.append(Messages.getInstance().parse("recipebook.header.shapeless"));

        if (hasCustomName()) {
            s.append('\n').append(RMCChatColor.BLACK).append(RMCChatColor.ITALIC).append(getName());
        }

        s.append('\n').append(RMCChatColor.GRAY).append('=');

        if (result.hasFlag(FlagType.ITEM_NAME)) {
            FlagItemName flag = (FlagItemName)result.getFlag(FlagType.ITEM_NAME);
            s.append(RMCChatColor.BLACK).append(RMCUtil.parseColors(flag.getItemName(), false));
        } else {
            s.append(ToolsItem.print(getFirstResult(), RMCChatColor.DARK_GREEN, null));
        }

        if (isMultiResult() && !hasFlag(FlagType.INDIVIDUAL_RESULTS)) {
            s.append('\n').append(Messages.getInstance().parse("recipebook.moreresults", "{amount}", (getResults().size() - 1)));
        }

        s.append("\n\n");
        s.append(Messages.getInstance().parse("recipebook.header.ingredients"));

        Map<ItemStack, MutableInt> items = new HashMap<>();

        // TODO: Handle ingredientChoiceList for 1.13
        for (ItemStack item : ingredients) {
            MutableInt i = items.get(item);

            if (i == null) {
                i = new MutableInt();
                items.put(item.clone(), i);
            }

            i.add(item.getAmount());
        }

        for (Entry<ItemStack, MutableInt> e : items.entrySet()) {
            ItemStack item = e.getKey();
            item.setAmount(e.getValue().intValue());

            String print = "";
            if (result.hasFlag(FlagType.INGREDIENT_CONDITION)) {
                FlagIngredientCondition flag = (FlagIngredientCondition) result.getFlag(FlagType.INGREDIENT_CONDITION);
                List<ConditionsIngredient> conditions = flag.getIngredientConditions(item);

                if (conditions.size() > 0) {
                    ConditionsIngredient condition = conditions.get(0);

                    if (condition.hasName()) {
                        print = RMCChatColor.BLACK + condition.getName();
                    } else if (condition.hasLore()) {
                        print = RMCChatColor.BLACK + "" + RMCChatColor.ITALIC + condition.getLores().get(0);
                    }
                }
            }

            if (print.equals("")) {
                print = ToolsItem.print(item, RMCChatColor.RESET, RMCChatColor.BLACK);
            }

            s.append('\n').append(print);
        }

        return s.toString();
    }
}
