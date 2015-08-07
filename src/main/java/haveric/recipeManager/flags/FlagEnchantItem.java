package haveric.recipeManager.flags;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.enchantments.Enchantment;

import haveric.recipeManager.ErrorReporter;
import haveric.recipeManager.Files;
import haveric.recipeManager.tools.Tools;

public class FlagEnchantItem extends Flag {
    // Flag definition and documentation

    private static final FlagType TYPE = FlagType.ENCHANTITEM;
    protected static final String[] A = new String[] {
        "{flag} <enchantment> [level]", };

    protected static final String[] D = new String[] {
        "Enchants the result with the specified enchantment at specified level.",
        "You must specify an enchantment name, you can find all of them in '" + Files.FILE_INFO_NAMES + "' file at 'ENCHANTMENTS LIST' section.",
        "Optionally you can set the level of enchantment ",
        "  Default is the enchantment's start level ",
        "  You can use 'max' to set it to enchantment's max level.",
        "  You can use 'remove' to remove the enchantment (from a cloned ingredient)",
        "",
        "Enchantments are forced and there is no level cap!",
        "This flag may be used more times to add more enchantments to the item.", };

    protected static final String[] E = new String[] {
        "{flag} OXYGEN // enchant with oxygen at level 1",
        "{flag} DIG_SPEED max // enchant with dig speed at max valid level",
        "{flag} ARROW_INFINITE 127 // enchant with arrow infinite forced at level 127",
        "{flag} SHARPNESS remove // removes a sharpness enchant"};

    // Flag code

    private Map<Enchantment, Integer> enchants = new HashMap<Enchantment, Integer>();
    private List<Enchantment> enchantsToRemove = new ArrayList<Enchantment>();

    public FlagEnchantItem() {
    }

    public FlagEnchantItem(FlagEnchantItem flag) {
        enchants.putAll(flag.enchants);
        enchantsToRemove.addAll(flag.enchantsToRemove);
    }

    public Map<Enchantment, Integer> getEnchants() {
        return enchants;
    }

    public List<Enchantment> getEnchantsToRemove() {
        return enchantsToRemove;
    }

    @Override
    public FlagEnchantItem clone() {
        super.clone();
        return new FlagEnchantItem(this);
    }

    @Override
    public FlagType getType() {
        return TYPE;
    }

    @Override
    public void onRemove() {
        getResult().getEnchantments().clear();
    }

    @Override
    public boolean onParse(String value) {
        String[] split = value.split(" ");
        value = split[0].trim();

        Enchantment enchant = Tools.parseEnchant(value);

        if (enchant == null) {
            ErrorReporter.error("Flag " + getType() + " has invalid enchantment: " + value, "Read '" + Files.FILE_INFO_NAMES + "' for enchantment names.");
            return false;
        }

        int level = enchant.getStartLevel();

        if (split.length > 1) {
            value = split[1].toLowerCase().trim();

            if (value.equals("max")) {
                level = enchant.getMaxLevel();
            } else if (value.equals("remove")) {
                enchantsToRemove.add(enchant);
            } else {
                try {
                    level = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    ErrorReporter.error("Flag " + getType() + " has invalid enchantment level number: " + value);
                    return false;
                }
            }
        }

        enchants.put(enchant, level);

        return true;
    }

    @Override
    protected void onPrepare(Args a) {
        if (!a.hasResult()) {
            a.addCustomReason("Needs result!");
            return;
        }

        for (Entry<Enchantment, Integer> e : enchants.entrySet()) {
            a.result().addUnsafeEnchantment(e.getKey(), e.getValue());
        }

        for (Enchantment e : enchantsToRemove) {
            a.result().removeEnchantment(e);
        }
    }

    /*
     * @Override public List<String> information() { List<String> list = new ArrayList<String>(1);
     *
     * list.add("enchant...");
     *
     * return list; }
     */
}
