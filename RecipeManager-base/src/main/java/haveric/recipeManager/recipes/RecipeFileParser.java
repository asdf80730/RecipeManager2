package haveric.recipeManager.recipes;

import haveric.recipeManager.ErrorReporter;
import haveric.recipeManager.RecipeRegistrator;
import haveric.recipeManager.flag.Flags;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;

public class RecipeFileParser {

    private String currentFile;
    private Flags fileFlags;
    private RecipeRegistrator registrator;
    private RecipeParserFactory recipeParserFactory;

    public RecipeFileParser(RecipeRegistrator recipeRegistrator, RecipeParserFactory recipeParserFactory) {
        registrator = recipeRegistrator;
        this.recipeParserFactory = recipeParserFactory;
    }

    public void parseFile(String root, String fileName) throws Throwable {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(root + fileName)));

        currentFile = fileName;
        if (currentFile.isEmpty()) {
            currentFile = root;
        }
        ErrorReporter.getInstance().setFile(currentFile);
        fileFlags = new Flags();
        boolean added = false;

        RecipeFileReader fileReader = new RecipeFileReader(reader, fileName);

        fileReader.parseFlags(fileFlags); // parse file header flags that apply to all recipes

        while (fileReader.searchRecipes()) { // search for recipes...
            int directiveLine = fileReader.getLineNum();
            String recipeName = null;
            String directive = fileReader.getLine().toLowerCase();
            int i = directive.indexOf(' ');

            if (i > 0) {
                recipeName = fileReader.getLine().substring(i + 1);
                directive = directive.substring(0, i);
            }

            BaseRecipeParser parser = recipeParserFactory.getParser(directive, fileReader, recipeName, fileFlags, registrator);
            if (parser != null) {
                added = parser.parseRecipe(directiveLine);
            }

            if (!added) {
                ErrorReporter.getInstance().error("Recipe was not added! Review previous errors and fix them.", "Warnings do not prevent recipe creation but they should be fixed as well!");
            }
        }

        if (fileReader.getLineNum() == 0) {
            ErrorReporter.getInstance().warning("Recipe file '" + fileName + "' is empty.");
        }

        reader.close();
    }
}
