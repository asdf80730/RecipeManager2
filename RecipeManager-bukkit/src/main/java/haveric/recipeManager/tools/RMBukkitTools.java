package haveric.recipeManager.tools;

import haveric.recipeManagerCommon.RMCVanilla;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class RMBukkitTools {
    public static boolean compareShapedRecipeToChoice(ShapedRecipe recipe, String[] shape, Map<Character, List<Material>> choiceMap) {
        String[] recipeShape = recipe.getShape();
        if (recipeShape.length != shape.length) {
            return false;
        }

        for (int i = 0; i < shape.length; i++) {
            if (!recipeShape[i].equals(shape[i])) {
                return false;
            }
        }

        Map<Character, RecipeChoice> recipeChoiceMap = recipe.getChoiceMap();

        if (recipeChoiceMap.size() != choiceMap.size()) {
            return false;
        }

        for (Map.Entry<Character, RecipeChoice> entry : recipeChoiceMap.entrySet()) {
            Character character = entry.getKey();

            if (!choiceMap.containsKey(character)) {
                return false;
            }

            if (!compareMaterialChoice(entry.getValue(), choiceMap.get(character), true)) {
                return false;
            }
        }

        return true;
    }

    public static boolean compareShapedRecipeToMatrix(ShapedRecipe recipe, ItemStack[] matrix, ItemStack[] matrixMirror) {
        ItemStack[] ingredients = convertShapedRecipeToItemMatrix(recipe);

        boolean result = compareItemMatrix(ingredients, matrix);

        if (!result) {
            result = compareItemMatrix(ingredients, matrixMirror);
        }

        return result;
    }

    public static ItemStack[] convertShapedRecipeToItemMatrix(ShapedRecipe bukkitRecipe) {
        Map<Character, ItemStack> items = bukkitRecipe.getIngredientMap();
        ItemStack[] matrix = new ItemStack[9];
        String[] shape = bukkitRecipe.getShape();
        int slot = 0;

        int shapeLength = shape.length;
        for (int r = 0; r < shapeLength; r++) {
            for (char col : shape[r].toCharArray()) {
                matrix[slot] = items.get(col);
                slot++;
            }

            slot = ((r + 1) * 3);
        }

        trimItemMatrix(matrix);

        return matrix;
    }

    public static boolean compareItemMatrix(ItemStack[] ingredients, ItemStack[] matrix) {
        for (int i = 0; i < 9; i++) {
            if (matrix[i] == null && ingredients[i] == null) {
                continue;
            }

            if (matrix[i] == null || ingredients[i] == null || ingredients[i].getType() != matrix[i].getType() || (ingredients[i].getDurability() != RMCVanilla.DATA_WILDCARD && ingredients[i].getDurability() != matrix[i].getDurability())) {
                return false;
            }
        }

        return true;
    }

    public static void trimItemMatrix(ItemStack[] matrix) {
        while (matrix[0] == null && matrix[1] == null && matrix[2] == null) {
            matrix[0] = matrix[3];
            matrix[1] = matrix[4];
            matrix[2] = matrix[5];

            matrix[3] = matrix[6];
            matrix[4] = matrix[7];
            matrix[5] = matrix[8];

            matrix[6] = null;
            matrix[7] = null;
            matrix[8] = null;
        }

        while (matrix[0] == null && matrix[3] == null && matrix[6] == null) {
            matrix[0] = matrix[1];
            matrix[3] = matrix[4];
            matrix[6] = matrix[7];

            matrix[1] = matrix[2];
            matrix[4] = matrix[5];
            matrix[7] = matrix[8];

            matrix[2] = null;
            matrix[5] = null;
            matrix[8] = null;
        }
    }

    public static boolean compareMaterialChoiceList(List<RecipeChoice> materialChoices, List<List<Material>> materialsList) {
        int listSize = materialsList.size();
        int choicesListSize = materialChoices.size();

        if (listSize != choicesListSize) {
            return false;
        }

        for (int i = 0; i < listSize; i++) {
            if (!compareMaterialChoice(materialChoices.get(i), materialsList.get(i), false)) {
                return false;
            }
        }

        return true;
    }

    private static boolean compareMaterialChoice(RecipeChoice choice, List<Material> materials, boolean airAllowed) {
        if (choice instanceof RecipeChoice.MaterialChoice) {
            RecipeChoice.MaterialChoice materialChoice = (RecipeChoice.MaterialChoice) choice;
            List<Material> choiceMaterials = materialChoice.getChoices();

            int size = materials.size();
            int choiceSize = choiceMaterials.size();

            if (size != choiceSize) {
                return false;
            }

            for (int j = 0; j < size; j++) {
                if (!materials.contains(choiceMaterials.get(j))) {
                    return false;
                }
            }
        } else if (choice instanceof RecipeChoice.ExactChoice) {
            RecipeChoice.ExactChoice exactChoice = (RecipeChoice.ExactChoice) choice;
            List<ItemStack> choiceMaterials = exactChoice.getChoices();

            int size = materials.size();
            int choiceSize = choiceMaterials.size();

            if (size != choiceSize) {
                return false;
            }

            for (int j = 0; j < size; j++) {
                if (!materials.contains(choiceMaterials.get(j).getType())) {
                    return false;
                }
            }
        } else {
            if (!airAllowed || materials.size() != 1 || materials.get(0) != Material.AIR) {
                return false;
            }
        }

        return true;
    }

    public static boolean compareIngredientList(List<ItemStack> sortedIngr, List<ItemStack> ingredients) {
        int size = ingredients.size();

        if (size != sortedIngr.size()) {
            return false;
        }

        sortIngredientList(ingredients);

        for (int i = 0; i < size; i++) {
            if (!isSameItemPlusDur(sortedIngr.get(i), ingredients.get(i))) {
                return false;
            }
        }

        return true;
    }

    public static boolean isSameItemPlusDur(ItemStack one, ItemStack two) {
        boolean same = false;

        if (one != null && two != null) {
            boolean sameType = one.getType() == two.getType();
            boolean sameDur = one.getDurability() == two.getDurability();
            boolean negativeDur = (one.getDurability() == Short.MAX_VALUE) || (two.getDurability() == Short.MAX_VALUE);

            if (sameType && (sameDur || negativeDur)) {
                same = true;
            }
        }
        return same;
    }

    public static boolean isSameItemFromChoice(RecipeChoice choice, ItemStack item) {
        boolean same = false;

        if (item != null) {
            Material type = item.getType();

            if (choice instanceof RecipeChoice.MaterialChoice) {
                RecipeChoice.MaterialChoice materialChoice = (RecipeChoice.MaterialChoice) choice;

                for (Material material : materialChoice.getChoices()) {
                    if (type == material) {
                        same = true;
                        break;
                    }
                }
            }
        }

        return same;
    }

    public static void sortIngredientList(List<ItemStack> ingredients) {
        ingredients.sort(new Comparator<ItemStack>() {
            public int compare(ItemStack item1, ItemStack item2) {
                String id1 = item1.getType().toString();
                String id2 = item2.getType().toString();

                int compare;
                if (id1.equals(id2)) {
                    if (item1.getDurability() > item2.getDurability()) {
                        compare = -1;
                    } else {
                        compare = 1;
                    }
                } else {
                    compare = id1.compareTo(id2);
                }

                return compare;
            }
        });
    }
}