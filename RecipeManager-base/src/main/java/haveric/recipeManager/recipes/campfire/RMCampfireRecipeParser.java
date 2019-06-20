package haveric.recipeManager.recipes.campfire;

import haveric.recipeManager.ErrorReporter;
import haveric.recipeManager.RecipeRegistrator;
import haveric.recipeManager.Vanilla;
import haveric.recipeManager.flag.FlagType;
import haveric.recipeManager.flag.Flags;
import haveric.recipeManager.recipes.BaseRecipeParser;
import haveric.recipeManager.recipes.ItemResult;
import haveric.recipeManager.recipes.RecipeFileReader;
import haveric.recipeManager.tools.Tools;
import haveric.recipeManagerCommon.RMCVanilla;
import haveric.recipeManagerCommon.util.ParseBit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class RMCampfireRecipeParser extends BaseRecipeParser {
    public RMCampfireRecipeParser(RecipeFileReader reader, String recipeName, Flags fileFlags, RecipeRegistrator recipeRegistrator) {
        super(reader, recipeName, fileFlags, recipeRegistrator);
    }

    @Override
    public boolean parseRecipe(int directiveLine) {
        RMCampfireRecipe recipe = new RMCampfireRecipe(fileFlags); // create recipe and copy flags from file
        reader.parseFlags(recipe.getFlags()); // check for @flags

        // get the ingredient and cooking time
        String[] split = reader.getLine().split("%");

        if (split.length == 0) {
            return ErrorReporter.getInstance().error("Campfire recipe doesn't have an ingredient!");
        }

        ItemStack ingredient = Tools.parseItem(split[0], RMCVanilla.DATA_WILDCARD, ParseBit.NO_AMOUNT);

        if (ingredient == null) {
            return false;
        }

        if (ingredient.getType() == Material.AIR) {
            return ErrorReporter.getInstance().error("Recipe does not accept AIR as ingredients!");
        }

        recipe.setIngredient(ingredient);

        boolean isRemove = recipe.hasFlag(FlagType.REMOVE);

        if (!isRemove) { // if it's got @remove we don't care about cook time
            float cookTime = Vanilla.CAMPFIRE_RECIPE_TIME;

            if (split.length >= 2) {
                String time = split[1].trim().toLowerCase();
                if (time.equals("instant")) {
                    cookTime = 0;
                } else {
                    try {
                        cookTime = Float.valueOf(time);
                    } catch (NumberFormatException e) {
                        ErrorReporter.getInstance().warning("Invalid cook time float number! Campfire cook time left as default.");
                        cookTime = Vanilla.CAMPFIRE_RECIPE_TIME;
                    }
                }
            }

            recipe.setCookingTime(cookTime);

            reader.nextLine();
        }

        // get result or move current line after them if we got @remove and results
        List<ItemResult> results = new ArrayList<>();

        if (isRemove) { // ignore result errors if we have @remove
            ErrorReporter.getInstance().setIgnoreErrors(true);
        }

        boolean hasResults = parseResults(recipe, results);

        if (!isRemove) { // ignore results if we have @remove
            if (!hasResults) {
                return false;
            }

            ItemResult result = results.get(0);

            recipe.setResult(result);
        }

        if (isRemove) { // un-ignore result errors
            ErrorReporter.getInstance().setIgnoreErrors(false);
        }

        // check if the recipe already exists
        if (!conditionEvaluator.recipeExists(recipe, directiveLine, reader.getFileName())) {
            return false;
        }

        if (recipeName != null && !recipeName.equals("")) {
            recipe.setName(recipeName); // set recipe's name if defined
        }

        // add the recipe to the Recipes class and to the list for later adding to the server
        recipeRegistrator.queueRecipe(recipe, reader.getFileName());


        return true;
    }
}