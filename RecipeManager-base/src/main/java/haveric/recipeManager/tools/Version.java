package haveric.recipeManager.tools;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Rabbit;

public class Version {

    private static String supportVersion = null;

    public static void init() {
        if (supports1_14()) {
            supportVersion = "1.14";
        } else if (supports1_13()) {
            supportVersion = "1.13";
        } else if (supports1_12()) {
            supportVersion = "1.12";
        } else if (supports1_11()) {
            supportVersion = "1.11";
        } else if (supports1_10()) {
            supportVersion = "1.10";
        } else if (supports1_9()) {
            supportVersion = "1.9";
        } else if (supports1_8Plus()) {
            supportVersion = "1.8+";
        } else if (supports1_8()) {
            supportVersion = "1.8";
        } else {
            supportVersion = "1.7";
        }
    }

    private static boolean supports1_14() {
        boolean supports;

        try {
            Material campfire = Material.CAMPFIRE;
            supports = true;
        } catch (NoSuchFieldError e) {
            supports = false;
        }

        return supports;
    }
    private static boolean supports1_13() {
        boolean supports;

        try {
            Material kelp = Material.KELP;
            supports = true;
        } catch (NoSuchFieldError e) {
            supports = false;
        }

        return supports;
    }

    private static boolean supports1_12() {
        boolean supports;

        try {
            @SuppressWarnings("unused")
            EntityType et = EntityType.PARROT;
            supports = true;
        } catch (NoSuchFieldError e) {
            supports = false;
        }

        return supports;
    }

    private static boolean supports1_11() {
        boolean supports;

        try {
            @SuppressWarnings("unused")
            Material shulker = Material.SHULKER_SHELL;
            supports = true;
        } catch (NoSuchFieldError e) {
            supports = false;
        }

        return supports;
    }

    private static boolean supports1_10() {
        boolean supports;

        try {
            @SuppressWarnings("unused")
            EntityType et = EntityType.POLAR_BEAR;
            supports = true;
        } catch (NoSuchFieldError e) {
            supports = false;
        }

        return supports;
    }

    private static boolean supports1_9() {
        boolean supports;

        try {
            @SuppressWarnings("unused")
            Material chorus = Material.CHORUS_FLOWER;
            supports = true;
        } catch (NoSuchFieldError e) {
            supports = false;
        }

        return supports;
    }

    private static boolean supports1_8Plus() {
        boolean supports;

        try {
            @SuppressWarnings("unused")
            Rabbit.Type rabbit = Rabbit.Type.SALT_AND_PEPPER;
            supports = true;
        } catch (NoSuchFieldError | NoClassDefFoundError e) {
            supports = false;
        }

        return supports;
    }

    private static boolean supports1_8() {
        boolean supports;

        try {
            @SuppressWarnings("unused")
            Material slime = Material.SLIME_BLOCK;
            supports = true;
        } catch (NoSuchFieldError e) {
            supports = false;
        }

        return supports;
    }

    private static String getVersion() {
        if (supportVersion == null) {
            init();
        }

        return supportVersion;
    }

    public static boolean has1_14Support() {
        boolean hasSupport = false;
        String version = getVersion();

        if (version.equals("1.14")) {
            hasSupport = true;
        }

        return hasSupport;
    }

    public static boolean has1_13Support() {
        boolean hasSupport = false;
        String version = getVersion();

        if (version.equals("1.14") || version.equals("1.13")) {
            hasSupport = true;
        }

        return hasSupport;
    }

    public static boolean has1_12Support() {
        boolean hasSupport = false;
        String version = getVersion();

        if (version.equals("1.14") || version.equals("1.13") || version.equals("1.12")) {
            hasSupport = true;
        }

        return hasSupport;
    }

    public static boolean has1_11Support() {
        boolean hasSupport = false;
        String version = getVersion();

        if (version.equals("1.14") || version.equals("1.13") || version.equals("1.12") || version.equals("1.11")) {
            hasSupport = true;
        }

        return hasSupport;
    }

    public static boolean has1_10Support() {
        boolean hasSupport = false;
        String version = getVersion();

        if (!version.equals("1.9") && !version.equals("1.8+") && !version.equals("1.8") && !version.equals("1.7")) {
            hasSupport = true;
        }

        return hasSupport;
    }

    public static boolean has1_9Support() {
        boolean hasSupport = false;
        String version = getVersion();

        if (!version.equals("1.8+") && !version.equals("1.8") && !version.equals("1.7")) {
            hasSupport = true;
        }

        return hasSupport;
    }

    public static boolean has1_8PlusSupport() {
        boolean hasSupport = false;
        String version = getVersion();

        if (!version.equals("1.8") && !version.equals("1.7")) {
            hasSupport = true;
        }

        return hasSupport;
    }

    public static boolean has1_8Support() {
        boolean hasSupport = false;
        String version = getVersion();

        if (!version.equals("1.7")) {
            hasSupport = true;
        }

        return hasSupport;
    }
}
