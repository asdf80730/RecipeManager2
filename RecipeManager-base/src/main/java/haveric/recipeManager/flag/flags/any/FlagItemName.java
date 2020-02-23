package haveric.recipeManager.flag.flags.any;

import haveric.recipeManager.ErrorReporter;
import haveric.recipeManager.flag.Flag;
import haveric.recipeManager.flag.FlagType;
import haveric.recipeManager.flag.args.Args;
import haveric.recipeManager.common.util.RMCUtil;
import org.bukkit.inventory.meta.ItemMeta;

public class FlagItemName extends Flag {

    @Override
    public String getFlagType() {
        return FlagType.ITEM_NAME;
    }

    @Override
    protected String[] getArguments() {
        return new String[] {
            "{flag} <text>",
            "{flag} <text> | display",
            "{flag} <text> | result", };
    }

    @Override
    protected String[] getDescription() {
        return new String[] {
            "Changes result's display name.",
            "",
            "Supports colors (e.g. <red>, <blue>, &4, &F, etc).",
            "",
            "You can also use these variables:",
            "  {player}         = crafter's name or '(nobody)' if not available",
            "  {playerdisplay}  = crafter's display name or '(nobody)' if not available",
            "  {result}         = the result item name or '(nothing)' if recipe failed.",
            "  {recipename}     = recipe's custom or autogenerated name or '(unknown)' if not available",
            "  {recipetype}     = recipe type or '(unknown)' if not available",
            "  {inventorytype}  = inventory type or '(unknown)' if not available",
            "  {world}          = world name of event location or '(unknown)' if not available",
            "  {x}              = event location's X coord or '(?)' if not available",
            "  {y}              = event location's Y coord or '(?)' if not available",
            "  {z}              = event location's Z coord or '(?)' if not available",
            "    Relative positions are supported: {x-1},{y+7},{z+12}",
            "  {rand #1-#2}     = output a random integer between #1 and #2. Example: {rand 5-10} will output an integer from 5-10",
            "  {rand #1-#2, #3} = output a random number between #1 and #2, with decimal places of #3. Example: {rand 1.5-2.5, 2} will output a number from 1.50 to 2.50",
            "  {rand n}         = reuse a random output, where n is the nth {rand} used excluding this format",
            "",
            "Allows quotes to prevent spaces being trimmed.",
            "",
            "Optional Arguments:",
            "  display          = only show on the displayed item when preparing to craft (only relevant to craft/combine recipes)",
            "  result           = only show on the result, but hide from the prepared result",
            "    Default behavior with neither of these arguments is to display in both locations", };
    }

    @Override
    protected String[] getExamples() {
        return new String[] {
            "{flag} <light_purple>Weird Item",
            "{flag} <yellow>{player}'s Sword",
            "{flag} \"  Extra space  \" // Quotes at the beginning and end will be removed, but spaces will be kept.", };
    }


    private String displayName;
    private String resultName;

    public FlagItemName() {
    }

    public FlagItemName(FlagItemName flag) {
        displayName = flag.displayName;
        resultName = flag.resultName;
    }

    @Override
    public FlagItemName clone() {
        return new FlagItemName((FlagItemName) super.clone());
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String newName) {
        displayName = newName;
    }

    public String getResultName() {
        return resultName;
    }

    public void setResultName(String newName) {
        resultName = newName;
    }

    public String getPrintName() {
        if (displayName != null) {
            return displayName;
        } else {
            return resultName;
        }
    }

    @Override
    public boolean onParse(String value, String fileName, int lineNum) {
        super.onParse(value, fileName, lineNum);
        // Match on single pipes '|', but not double '||'
        String[] args = value.split("(?<!\\|)\\|(?!\\|)");
        String name = args[0];

        // Replace double pipes with single pipe: || -> |
        name = name.replaceAll("\\|\\|", "|");
        name = RMCUtil.trimExactQuotes(name);
        name = RMCUtil.parseColors(name, false);

        if (args.length > 1) {
            String display = args[1].trim().toLowerCase();
            if (display.equals("display")) {
                displayName = name;
            } else if (display.equals("result")) {
                resultName = name;
            } else {
                ErrorReporter.getInstance().warning("Flag " + getFlagType() + " has invalid argument: " + args[1] + ". Defaulting to set name in both locations.");
                displayName = name;
                resultName = name;
            }
        } else {
            displayName = name;
            resultName = name;
        }

        return true;
    }

    @Override
    public void onPrepare(Args a) {
        if (canAddMeta(a)) {
            String name;
            if (displayName == null) {
                name = null;
            } else {
                name = a.parseVariables(displayName, true);
            }

            setMetaName(a, name);
        }
    }

    @Override
    public void onCrafted(Args a) {
        if (canAddMeta(a)) {
            String name;
            if (resultName == null) {
                name = null;
            } else {
                name = a.parseVariables(resultName);
            }

            setMetaName(a, name);
        }
    }

    private void setMetaName(Args a, String name) {
        ItemMeta meta = a.result().getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);

            a.result().setItemMeta(meta);
        }
    }

    @Override
    public int hashCode() {
        String toHash = "" + super.hashCode();

        toHash += "displayName: " + displayName;
        toHash += "resultName: " + resultName;

        return toHash.hashCode();
    }
}
