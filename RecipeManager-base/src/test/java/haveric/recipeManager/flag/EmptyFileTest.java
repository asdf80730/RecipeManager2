package haveric.recipeManager.flag;

import haveric.recipeManager.RecipeProcessor;
import haveric.recipeManager.recipes.BaseRecipe;
import haveric.recipeManagerCommon.recipes.RMCRecipeInfo;
import org.junit.Test;

import java.io.File;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class EmptyFileTest extends FlagBaseTest {
    @Test
    public void onRecipeParse() {
        File file = new File(baseRecipePath + "emptyFile/emptyFile.txt");
        RecipeProcessor.reload(null, true, file.getPath(), workDir.getPath());

        Map<BaseRecipe, RMCRecipeInfo> queued = RecipeProcessor.getRegistrator().getQueuedRecipes();

        assertEquals(0, queued.size());
    }
}
