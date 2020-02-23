package haveric.recipeManager.nms;

import haveric.recipeManager.messages.MessageSender;
import haveric.recipeManager.tools.BaseRecipeIterator;
import haveric.recipeManager.common.recipes.AbstractBaseRecipe;
import net.minecraft.server.v1_12_R1.*;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftFurnaceRecipe;
import org.bukkit.craftbukkit.v1_12_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_12_R1.inventory.RecipeIterator;
import org.bukkit.inventory.Recipe;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class RecipeIteratorV1_12 extends BaseRecipeIterator implements Iterator<Recipe> {
    private Iterator<IRecipe> recipes;

    private Iterator<ItemStack> smeltingCustom;
    private List<ItemStack> recipeSmeltingCustom = new LinkedList<>();

    private Iterator<ItemStack> smeltingVanilla;
    private List<ItemStack> recipeSmeltingVanilla = new LinkedList<>();

    enum RemoveFrom {
        RECIPES, CUSTOM, VANILLA
    }

    private RemoveFrom removeFrom = null;
    private IRecipe removeRecipe = null;
    private ItemStack removeItem = null;

    public RecipeIteratorV1_12() {
        Iterator<Recipe> backing = getBukkitRecipeIterator();
        if (backing instanceof RecipeIterator) {
            recipes = CraftingManager.recipes.iterator();
            smeltingCustom = RecipesFurnace.getInstance().customRecipes.keySet().iterator();
            smeltingVanilla = RecipesFurnace.getInstance().recipes.keySet().iterator();
        } else {
            throw new IllegalArgumentException("This version is not supported.");
        }
    }

    /**
     * If nothing more is accessible, finalize any removals before informing caller of nothing new.
     */
    @Override
    public boolean hasNext() {
        boolean next = recipes.hasNext() || smeltingCustom.hasNext() || smeltingVanilla.hasNext();
        if (!next) {
            finish();
        }
        return next;
    }

    @Override
    public Recipe next() {
        if (recipes.hasNext()) {
            removeFrom = RemoveFrom.RECIPES;
            removeRecipe = recipes.next();
            try {
                return removeRecipe.toBukkitRecipe();
            } catch (ArrayIndexOutOfBoundsException aioobe) {
                try {
                    Field keyF = removeRecipe.getClass().getField("key");
                    MinecraftKey key = (MinecraftKey) keyF.get(removeRecipe);
                    MessageSender.getInstance().error(null, aioobe, "Failure while traversing iterator on recipe " + key.toString());
                } catch (Exception e) {
                    MessageSender.getInstance().error(null, e, "Failure while traversing iterator, unable to determine recipe.");
                }
            }
            return null;
        } else {
            ItemStack item;
            if (smeltingCustom.hasNext()) {
                removeFrom = RemoveFrom.CUSTOM;
                item = smeltingCustom.next();
            } else {
                removeFrom = RemoveFrom.VANILLA;
                item = smeltingVanilla.next();
            }
            removeItem = item;

            CraftItemStack stack = CraftItemStack.asCraftMirror(RecipesFurnace.getInstance().getResult(item));

            return new CraftFurnaceRecipe(stack, CraftItemStack.asCraftMirror(item));
        }
    }

    /**
     * Backing list is now immutable in 1.12 - 1.14.
     * 
     * We have two modes of operation. For recipes, we don't remove, we simply replace them with dummy data
     * that can never be matched. In this way, ID ordering is preserved and we avoid any unpleasantness with 
     * the fact that the Client and Server both assume recipes have a specific ID order sequence to them.
     *
     * For Smelting, we register the requests to remove, and perform those removals when we are done iterating
     * or are requested to finalize. 
     */
    public void remove() {
        if (removeFrom == null) {
            throw new IllegalStateException();
        }
        switch (removeFrom) {
        case RECIPES:
            //MessageSender.getInstance().info("NMS for 1.12 removing recipe " + removeRecipe);
            try {
                if (removeRecipe instanceof ShapedRecipes) {
                    ShapedRecipes shaped = (ShapedRecipes) removeRecipe;
                    Field widthF = stripPrivateFinal(ShapedRecipes.class, "width");
                    Field heightF = stripPrivateFinal(ShapedRecipes.class, "height");
                    Field itemsF = stripPrivateFinal(ShapedRecipes.class, "items");
                    Field resultF = stripPrivateFinal(ShapedRecipes.class, "result");

                    // now for the _real_ fun, modifying an unmodifiable recipe.
                    // So for shaped recipes, my thought is just to replace the ItemStack with something
                    // nonsensical, set height and width to 1, and hope it isn't cached in too many places.
                    // Oh, and set result to air.
                    widthF.setInt(shaped, 1);
                    heightF.setInt(shaped, 1);
                    resultF.set(shaped, new ItemStack(Items.a, 1));
                    itemsF.set(shaped, NonNullList.a(1, RecipeItemStack.a(new ItemStack[] {new ItemStack(new ItemSnow(Blocks.SNOW_LAYER), 1, Short.MAX_VALUE, false)})));
                } else if (removeRecipe instanceof ShapelessRecipes) {
                    ShapelessRecipes shapeless = (ShapelessRecipes) removeRecipe;
                    Field ingredientsF = stripPrivateFinal(ShapelessRecipes.class, "ingredients");
                    Field resultF = stripPrivateFinal(ShapelessRecipes.class, "result");

                    resultF.set(shapeless, new ItemStack(Items.a, 1));
                    ingredientsF.set(shapeless, NonNullList.a(1, RecipeItemStack.a(new ItemStack[] {new ItemStack(new ItemSnow(Blocks.SNOW_LAYER), 1, Short.MAX_VALUE, false)})));
                } else {
                    throw new IllegalStateException("You cannot replace a grid recipe with a " + removeRecipe.getClass().getName() + " recipe!");
                }
            } catch (Exception e) {
                MessageSender.getInstance().error(null, e, "NMS failure for v1.12 support during grid recipe removal");
            }
            break;
        case CUSTOM:
            recipeSmeltingCustom.add(removeItem);
            break;
        case VANILLA:
            recipeSmeltingVanilla.add(removeItem);
        }
    }

    @Override
    public Iterator<Recipe> getIterator() {
        return this;
    }

    /**
     * Backing list is now immutable in 1.12 - 1.14.
     * 
     * To prevent bad linking to RM unique recipes, we add a new mode "replace" which can be leveraged 
     * instead of remove, to link the MC recipe to the RM recipe directly. We don't actually then
     * add the RM recipe to Bukkit, only to our indexes.
     * 
     * For Smelting, use traditional remove / add.
     */
    @Override
    public void replace(AbstractBaseRecipe recipe, org.bukkit.inventory.ItemStack overrideItem) {
        if (removeFrom == null) {
            throw new IllegalStateException();
        }
        switch (removeFrom) {
        case RECIPES:
            // A _key_ assumption with replace is that the original items and shape is _unchanged_. Only result is overridden.
            try {
                // MessageSender.getInstance().info("NMS for 1.12 replacing recipe " + recipe.getName());
                if (removeRecipe instanceof ShapedRecipes) {
                    ShapedRecipes shaped = (ShapedRecipes) removeRecipe;
                    Field resultF = stripPrivateFinal(ShapedRecipes.class, "result");

                    ItemStack overrideF = CraftItemStack.asNMSCopy(overrideItem);
                    resultF.set(shaped, overrideF);
                } else if (removeRecipe instanceof ShapelessRecipes) {
                    ShapelessRecipes shapeless = (ShapelessRecipes) removeRecipe;
                    Field resultF = stripPrivateFinal(ShapelessRecipes.class, "result");

                    ItemStack overrideF = CraftItemStack.asNMSCopy(overrideItem);
                    resultF.set(shapeless, overrideF);
                } else {
                    throw new IllegalStateException("You cannot replace a grid recipe with a " + removeRecipe.getClass().getName() + " recipe!");
                }
            } catch (Exception e) {
                MessageSender.getInstance().error(null, e, "NMS failure for v1.12 support during grid recipe replace");
            }
            break;
        case CUSTOM:
        case VANILLA:
            throw new IllegalStateException("Replace not supported for Furnace recipes.");
        }
    }
    
    /**
     * This is the companion to remove(), and effectuates removals of furnace recipes. It is called automatically when 
     * the end of the iterator is reached; in other settings, call it manually.
     */
    @Override
    public void finish() {
        if (!recipeSmeltingCustom.isEmpty()) {
            RecipesFurnace furnaces = RecipesFurnace.getInstance();
            recipeSmeltingCustom.forEach(item -> {
                furnaces.customRecipes.remove(item);
                furnaces.customExperience.remove(item);
            });
        }
        if (!recipeSmeltingVanilla.isEmpty()) {
            RecipesFurnace furnaces = RecipesFurnace.getInstance();
            try {
                Field experienceF = RecipesFurnace.class.getDeclaredField("experience");
                experienceF.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<ItemStack, Float> experience = (Map<ItemStack, Float>) experienceF.get(furnaces);
                recipeSmeltingVanilla.forEach(item -> {
                    furnaces.recipes.remove(item);
                    experience.remove(item);
                });
            } catch (Exception e) {
                MessageSender.getInstance().error(null, e, "NMS failure for v1.12 support during vanilla smelting removal");
            }

        }
    }
}
