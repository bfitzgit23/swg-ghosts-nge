package script.library;

import script.npe.handoff_to_tatooine;
import script.obj_id;

/**
 * Ghosts profession kit - the second half of the one-off login gift.
 *
 * The first gift (ghosts_gift) hands out armour, the primary weapon, plain
 * jewellery, three collection items and credits.  This kit adds the profession
 * package, silently, with no window at login:
 *
 *   Jedi / Force Sensitive
 *     - a 1st generation one-handed lightsaber and a training lightsaber.  These are
 *       the static items weapon_npe_lightsaber_02_01 / _02_02, which wrap the
 *       generic_sword_lightsaber_* templates: those carry the blade in their shared
 *       appearance, unlike the craftable swords whose blade is an empty crystal slot.
 *     - the two Jedi artifact ("holocron") collection rewards:
 *         inv_holocron_collection_01 -> item_fannypack_04_01 (Jedi waist pack)
 *         inv_holocron_collection_02 -> the /meditate command (Meditative Discipline)
 *     - the five-piece heroic random-stat jewellery set.
 *
 *   Every other profession
 *     - a second signature weapon (the first one came with ghosts_gift),
 *     - the profession's own collection reward where the data has one
 *       (entertainer: beast holocron from col_entertainer_01,
 *        trader: jeweled necklace schematic from col_dom_trader),
 *     - the same five-piece heroic jewellery set.
 *
 * Two clean-up jobs ride along:
 *   - fixLegacyTwoHandSabers() installs a colour crystal in the bladeless two-handed
 *     saber the first gift hands out, once per character.
 *   - the jewellery pieces carry library.ghosts_jewelry_swap, a one-time "Use" radial
 *     that exchanges the set for another style.
 *
 * Guarded by ghosts.professionKit, separate from ghosts.loginGift so characters
 * that already collected the first gift still get this one exactly once.
 */
public class ghosts_profession_kit extends script.base_script
{
    public ghosts_profession_kit()
    {
    }

    public static final String KIT_OBJVAR = "ghosts.professionKit";
    public static final int KIT_VERSION = 1;

    // static items - the generic sabers have a bladed shared appearance
    public static final String SABER_GEN1 = "weapon_npe_lightsaber_02_01";
    public static final String SABER_TRAINING = "weapon_npe_lightsaber_02_02";

    // datatables/collection/rewards.tab: inv_holocron_collection_01 / _02
    public static final String JEDI_COLLECTION_BELT = "item_fannypack_04_01";
    public static final String JEDI_COLLECTION_COMMAND = "meditate";

    // second weapon per profession - deliberately a different template from the
    // cdef_npe weapon ghosts_gift already hands out
    public static final String WEAPON_PISTOL = "object/weapon/ranged/pistol/pistol_d18_npe.iff";
    public static final String WEAPON_CARBINE = "object/weapon/ranged/carbine/carbine_dh17_black_npe.iff";
    public static final String WEAPON_RIFLE = "object/weapon/ranged/rifle/rifle_dlt20_npe.iff";
    public static final String WEAPON_SNUB = "object/weapon/ranged/carbine/carbine_dh17_snubnose_npe.iff";
    public static final String WEAPON_SCOUT = "object/weapon/ranged/pistol/pistol_scout_blaster_npe.iff";
    public static final String WEAPON_SG82 = "object/weapon/ranged/rifle/rifle_sg82_npe.iff";

    // master_item names (these go through static_item.createNewItemFunction)
    public static final String ENTERTAINER_COLLECTION = "item_beast_holocron_04_08";
    public static final String TRADER_COLLECTION = "col_jeweled_necklace_schematic_02_01";

    // two-handed saber repair
    public static final String SABER_FIX_OBJVAR = "ghosts.saberRepair";
    // one-time removal of the droid_* abilities the FS phase 1 box used to grant
    public static final String DROID_CLEANUP_OBJVAR = "ghosts.droidCommandsRemoved";
    public static final String[] DROID_COMMANDS =
    {
        "droid_flame_jet_1",
        "droid_droideka_shield_1",
        "droid_battery_dump_1",
        "droid_regenerative_plating_1",
        "droid_electrical_shock_1",
        "droid_torturous_needle_1",
        "droid_follow",
        "droid_follow_other",
        "droid_stay",
        "droid_guard",
        "droid_friend",
        "droid_attack",
        "droid_patrol",
        "droid_patrol_point",
        "droid_patrol_clear",
        "droid_store",
        "droid_transfer",
        "droid_group",
        "droid_trick_1",
        "droid_trick_2",
        "droid_trick_3",
        "droid_trick_4"
    };
    public static final String SABER_FIX_TRIES_OBJVAR = "ghosts.saberRepairTries";
    public static final String TWO_HAND_SABER_TEMPLATE = "object/weapon/melee/2h_sword/crafted_saber/sword_lightsaber_two_handed_gen1.iff";
    public static final String COLOR_CRYSTAL_TEMPLATE = "object/tangible/component/weapon/lightsaber/lightsaber_module_force_crystal.iff";
    // jedi.mapOldColorToNewColor(0..11) are the twelve base colour crystals; every
    // Jedi gets one of them picked at random instead of the same colour every time
    public static final int COLOR_CRYSTAL_COUNT = 12;
    // bumping SABER_FIX_VERSION re-runs the repair once for characters that already
    // have the old guard written - that is what gives the existing Jedi their crystal
    public static final int SABER_FIX_VERSION = 2;
    public static final String SABER_COLOR_OBJVAR = "ghosts.saberColor";
    public static final int CRYSTAL_GRANT_VERSION = 1;
    public static final String CRYSTAL_GRANT_OBJVAR = "ghosts.colorCrystalGrant";

    // jewellery style state, shared with library.ghosts_jewelry_swap
    public static final String STYLE_OBJVAR = "ghosts.jewelryStyle";
    public static final String SWAP_USED_OBJVAR = "ghosts.jewelrySwapUsed";
    public static final String SWAP_RUNNING_OBJVAR = "ghosts.jewelrySwapping";
    public static final String SWAP_SCRIPT = "library.ghosts_jewelry_swap";

    public static final String[] JEWELRY_STYLE_NAMES =
    {
        "Gilded Combatant's set",
        "Fancy Combatant's set",
        "Elegant Combatant's set",
        "Fine Combatant's set"
    };

    // The five pieces of each style, styles 01-04 of the heroic random-stat line:
    // two rings (the ring row is not unique, so two copies are created), one necklace,
    // and the left + right bracelet.
    public static final String[][] JEWELRY_SETS =
    {
        {
            "item_heroic_random_ring_01_01",
            "item_heroic_random_ring_01_01",
            "item_heroic_random_neck_01_01",
            "item_heroic_random_bracelet_l_01_01",
            "item_heroic_random_bracelet_r_01_01"
        },
        {
            "item_heroic_random_ring_01_02",
            "item_heroic_random_ring_01_02",
            "item_heroic_random_neck_01_02",
            "item_heroic_random_bracelet_l_01_02",
            "item_heroic_random_bracelet_r_01_02"
        },
        {
            "item_heroic_random_ring_01_03",
            "item_heroic_random_ring_01_03",
            "item_heroic_random_neck_01_03",
            "item_heroic_random_bracelet_l_01_03",
            "item_heroic_random_bracelet_r_01_03"
        },
        {
            "item_heroic_random_ring_01_04",
            "item_heroic_random_ring_01_04",
            "item_heroic_random_neck_01_04",
            "item_heroic_random_bracelet_l_01_04",
            "item_heroic_random_bracelet_r_01_04"
        }
    };

    // granted style - the Gilded Combatant's set
    public static final int JEWELRY_STYLE = 0;

    public static boolean isKitGranted(obj_id player) throws InterruptedException
    {
        return hasObjVar(player, KIT_OBJVAR) && getIntObjVar(player, KIT_OBJVAR) >= KIT_VERSION;
    }

    public static boolean grantIfDue(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return false;
        }
        // repair any bladeless crafted lightsaber the character owns, even for
        // characters that already have the profession kit - it has its own guard
        fixBladelessSabers(player);
        grantColorCrystal(player);
        stripDroidCommands(player);
        // existing characters that already landed before the handoff ran still need
        // the legacy quest - this is a no-op once it has been granted
        handoff_to_tatooine.grantLegacyIfDue(player);

        if (isKitGranted(player))
        {
            return false;
        }
        obj_id inventory = utils.getInventoryContainer(player);
        if (!isIdValid(inventory))
        {
            // do not burn the guard: try again on the next login
            LOG("ghosts_profession_kit", "no inventory container for " + player + " yet - deferring the profession kit");
            return false;
        }

        // Jewellery first: if it cannot be created nothing else is granted either,
        // so a retry on the next login cannot duplicate the profession items.
        if (!grantJewelrySet(player, JEWELRY_STYLE))
        {
            LOG("ghosts_profession_kit", "profession kit deferred for " + player + " - jewellery could not be created");
            return false;
        }

        int profession = ghosts_gift.getProfessionIndex(player);
        int given = 0;

        // Every character on this cluster is Force Sensitive (base_player grants it at
        // first login), so a Jedi state also counts as the Jedi package.
        boolean jediKit = (profession == utils.FORCE_SENSITIVE) || isJedi(player);
        if (jediKit)
        {
            obj_id gen1 = giveStatic(player, SABER_GEN1);
            if (isIdValid(gen1))
            {
                primeSaber(gen1, 20, 60);
                given++;
            }
            obj_id training = giveStatic(player, SABER_TRAINING);
            if (isIdValid(training))
            {
                primeSaber(training, 5, 15);
                given++;
            }
            if (isIdValid(giveStatic(player, JEDI_COLLECTION_BELT)))
            {
                given++;
            }
            if (grantCommand(player, JEDI_COLLECTION_COMMAND))
            {
                given++;
            }
            else
            {
                LOG("ghosts_profession_kit", "could not grant /" + JEDI_COLLECTION_COMMAND + " to " + player);
            }
        }
        if (profession != utils.FORCE_SENSITIVE)
        {
            obj_id weapon = giveTemplate(player, secondWeaponFor(profession));
            if (isIdValid(weapon))
            {
                given++;
            }
            String collection = collectionRewardFor(profession);
            if (collection != null && isIdValid(giveStatic(player, collection)))
            {
                given++;
            }
        }

        setObjVar(player, KIT_OBJVAR, KIT_VERSION);
        sendSystemMessageTestingOnly(player, "Your profession kit is in your inventory: the Jedi sabers and artifact collection rewards (or your profession's second weapon and collection reward), plus the five-piece " + JEWELRY_STYLE_NAMES[JEWELRY_STYLE] + " (two rings, necklace and both bracelets).");
        LOG("ghosts_profession_kit", "profession kit v" + KIT_VERSION + " to " + player + " (" + getSkillTemplate(player) + ", profession " + profession + "): items=" + given + ", jewellery=" + JEWELRY_STYLE);
        return true;
    }

    public static String secondWeaponFor(int profession)
    {
        switch (profession)
        {
            case utils.COMMANDO:
                return WEAPON_RIFLE;
            case utils.SMUGGLER:
                return WEAPON_SNUB;
            case utils.MEDIC:
                return WEAPON_SCOUT;
            case utils.OFFICER:
                return WEAPON_CARBINE;
            case utils.SPY:
                return WEAPON_PISTOL;
            case utils.BOUNTY_HUNTER:
                return WEAPON_SG82;
            case utils.TRADER:
                return WEAPON_SCOUT;
            case utils.ENTERTAINER:
                return WEAPON_PISTOL;
            default:
                return WEAPON_PISTOL;
        }
    }

    /**
     * The profession's own collection reward, where the shipped data has a usable
     * one.  The combat professions have no profession collection in this data set
     * (only event/token rewards), so they get their second weapon instead.
     */
    public static String collectionRewardFor(int profession)
    {
        switch (profession)
        {
            case utils.ENTERTAINER:
                return ENTERTAINER_COLLECTION;
            case utils.TRADER:
                return TRADER_COLLECTION;
            default:
                return null;
        }
    }

    /**
     * Give a saber real combat stats.  The generic saber template inherits a plain
     * sword's 10-50 kinetic profile; this makes it an energy weapon with the values
     * asked for, using the same setters jedi.initSaberBaseStats() uses.
     */
    public static void primeSaber(obj_id saber, int minDamage, int maxDamage) throws InterruptedException
    {
        if (!isIdValid(saber))
        {
            return;
        }
        setWeaponMinDamage(saber, minDamage);
        setWeaponMaxDamage(saber, maxDamage);
        setWeaponDamageType(saber, DAMAGE_ENERGY);
        setWeaponAttackSpeed(saber, 4.5f);
        setWeaponWoundChance(saber, 10.0f);
        setWeaponAttackCost(saber, 100);
        setWeaponDamageRadius(saber, 0.0f);
        setWeaponElementalType(saber, DAMAGE_NONE);
        setWeaponElementalValue(saber, 0);
        weapons.setWeaponData(saber);
    }

    /**
     * The first gift hands out a two-handed lightsaber created straight from its
     * object template.  That template is a craftable shell: the blade is the colour
     * crystal inside saber_inv, and an empty container means the saber is the object
     * the game refuses to let you use ("cannot be used until it has a colour-modifying
     * Force crystal installed").  Install one, once per character.
     */
    /**
     * class_forcesensitive_phase1_04 used to grant the whole droid_* command list.
     * skills.tab no longer lists them, but a character that already learned the box
     * keeps the commands in its command list.  There is no guard any more: the check
     * is cheap and runs on every login, so a character whose first pass failed (or
     * that learned the box afterwards) still loses them.
     */
    public static int stripDroidCommands(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return 0;
        }
        int removed = 0;
        for (int i = 0; i < DROID_COMMANDS.length; i++)
        {
            if (skill.hasCommand(player, DROID_COMMANDS[i]))
            {
                skill.revokeCommand(player, DROID_COMMANDS[i]);
                removed++;
            }
        }
        if (removed > 0)
        {
            LOG("ghosts_profession_kit", "revoked " + removed + " droid command(s) from " + player);
        }
        return removed;
    }

    /**
     * The colour picked for this character the first time the repair or the crystal
     * grant ran.  Keeping it in an objvar means a later re-run cannot recolour the
     * sabers a character already has.
     */
    public static int characterColor(obj_id player) throws InterruptedException
    {
        if (hasObjVar(player, SABER_COLOR_OBJVAR))
        {
            return getIntObjVar(player, SABER_COLOR_OBJVAR);
        }
        int color = rand(0, COLOR_CRYSTAL_COUNT - 1);
        setObjVar(player, SABER_COLOR_OBJVAR, color);
        return color;
    }

    /**
     * True when the container holds a colour crystal itself or a saber with one in
     * its saber_inv.
     */
    public static boolean containerHasColorCrystal(obj_id container) throws InterruptedException
    {
        if (!isIdValid(container))
        {
            return false;
        }
        obj_id[] contents = getContents(container);
        if (contents == null)
        {
            return false;
        }
        for (int i = 0; i < contents.length; i++)
        {
            obj_id item = contents[i];
            if (!isIdValid(item))
            {
                continue;
            }
            if (jedi.isColorCrystal(item))
            {
                return true;
            }
            obj_id saberInv = getObjectInSlot(item, "saber_inv");
            if (isIdValid(saberInv))
            {
                obj_id[] inside = getContents(saberInv);
                if (inside != null)
                {
                    for (int j = 0; j < inside.length; j++)
                    {
                        if (isIdValid(inside[j]) && jedi.isColorCrystal(inside[j]))
                        {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Every existing Jedi gets a colour crystal.  When the repair below already put
     * one inside a crafted saber the character owns it and nothing else is created;
     * otherwise an untuned colour crystal is placed in the inventory.
     */
    public static int grantColorCrystal(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player) || getIntObjVar(player, CRYSTAL_GRANT_OBJVAR) >= CRYSTAL_GRANT_VERSION)
        {
            return 0;
        }
        int profession = ghosts_gift.getProfessionIndex(player);
        if (profession != utils.FORCE_SENSITIVE && !isJedi(player))
        {
            return 0;
        }
        obj_id inventory = utils.getInventoryContainer(player);
        if (!isIdValid(inventory))
        {
            return 0;
        }
        if (containerHasColorCrystal(inventory) || containerHasColorCrystal(getObjectInSlot(player, "bank")))
        {
            setObjVar(player, CRYSTAL_GRANT_OBJVAR, CRYSTAL_GRANT_VERSION);
            LOG("ghosts_profession_kit", "colour crystal already owned by " + player + " - not granting another");
            return 0;
        }
        int color = characterColor(player);
        if (!jedi.createColorCrystal(inventory, color))
        {
            LOG("ghosts_profession_kit", "could not create the granted colour crystal for " + player);
            return 0;
        }
        setObjVar(player, CRYSTAL_GRANT_OBJVAR, CRYSTAL_GRANT_VERSION);
        sendSystemMessageTestingOnly(player, "A colour crystal has been placed in your inventory: tune it and install it in a lightsaber, or use the sabers you already carry.");
        LOG("ghosts_profession_kit", "granted colour " + color + " crystal to " + player);
        return 1;
    }

    public static int fixBladelessSabers(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player) || getIntObjVar(player, SABER_FIX_OBJVAR) >= SABER_FIX_VERSION)
        {
            return 0;
        }
        obj_id inventory = utils.getInventoryContainer(player);
        if (!isIdValid(inventory))
        {
            return 0;
        }
        // one colour per character, picked at random the first time, then kept
        int color = characterColor(player);
        int fixed = repairContainer(inventory, color);

        // the bank is the "bank" slot on the player object; scan it as well so a saber
        // put away in the bank is repaired too
        obj_id bank = getObjectInSlot(player, "bank");
        int tries = hasObjVar(player, SABER_FIX_TRIES_OBJVAR) ? getIntObjVar(player, SABER_FIX_TRIES_OBJVAR) : 0;
        if (isIdValid(bank))
        {
            fixed += repairContainer(bank, color);
        }
        else
        {
            LOG("ghosts_profession_kit", "bank container not available for " + player + " (try " + (tries + 1) + ") - will retry");
        }
        setObjVar(player, SABER_FIX_TRIES_OBJVAR, tries + 1);
        if (isIdValid(bank) || tries >= 4)
        {
            setObjVar(player, SABER_FIX_OBJVAR, SABER_FIX_VERSION);
            removeObjVar(player, SABER_FIX_TRIES_OBJVAR);
        }
        if (fixed > 0)
        {
            sendSystemMessageTestingOnly(player, "A colour crystal has been installed in " + fixed + " bladeless lightsaber(s), so they can be used now.");
        }
        LOG("ghosts_profession_kit", "bladeless saber repair for " + player + ": " + fixed + " saber(s) fixed, bank=" + isIdValid(bank) + ", color=" + color);
        return fixed;
    }

    /**
     * Any lightsaber built from one of the crafted_saber shells is a hilt plus an empty
     * crystal slot; the generic_* NPE sabers are the ones with the blade in their shared
     * appearance and must not be touched.
     */
    public static boolean isBladelessCraftedSaber(obj_id item) throws InterruptedException
    {
        if (!isIdValid(item) || jedi.hasColorCrystal(item))
        {
            return false;
        }
        String template = getTemplateName(item);
        if (template == null || !template.contains("/crafted_saber/"))
        {
            return false;
        }
        if (template.contains("/generic_"))
        {
            return false;
        }
        return true;
    }

    /**
     * Repair every bladeless crafted lightsaber found in one container (inventory or bank).
     */
    public static int repairContainer(obj_id container, int color) throws InterruptedException
    {
        if (!isIdValid(container))
        {
            return 0;
        }
        obj_id[] contents = getContents(container);
        if (contents == null)
        {
            return 0;
        }
        int fixed = 0;
        for (int i = 0; i < contents.length; i++)
        {
            obj_id item = contents[i];
            if (!isIdValid(item))
            {
                continue;
            }
            if (!isBladelessCraftedSaber(item))
            {
                continue;
            }
            if (installColorCrystal(item, color))
            {
                fixed++;
            }
        }
        return fixed;
    }

    public static boolean installColorCrystal(obj_id saber, int color) throws InterruptedException
    {
        if (!isIdValid(saber) || jedi.hasColorCrystal(saber))
        {
            return false;
        }
        obj_id saberInv = getObjectInSlot(saber, "saber_inv");
        if (!isIdValid(saberInv))
        {
            LOG("ghosts_profession_kit", saber + " has no saber_inv container");
            return false;
        }
        // a saber can already hold a colour crystal even when its jedi.saber.color
        // objvar is missing - never install a second one
        obj_id[] existing = getContents(saberInv);
        if (existing != null)
        {
            for (int i = 0; i < existing.length; i++)
            {
                if (isIdValid(existing[i]) && jedi.isColorCrystal(existing[i]))
                {
                    LOG("ghosts_profession_kit", saber + " already holds a colour crystal");
                    return false;
                }
            }
        }

        String crystalName = jedi.mapOldColorToNewColor(color);
        obj_id crystal = static_item.createNewItemFunction(crystalName, saberInv);
        if (!isIdValid(crystal) || getContainedBy(crystal) != saberInv)
        {
            // createNewItemFunction() re-routes to the player's inventory when the
            // target is not a plain container - build the crystal in the saber directly
            if (isIdValid(crystal))
            {
                destroyObject(crystal);
            }
            crystal = createObject(COLOR_CRYSTAL_TEMPLATE, saberInv, "");
            if (isIdValid(crystal))
            {
                setStaticItemName(crystal, crystalName);
                utils.setObjVarsList(crystal, "int:jedi.crystal.stats.color=" + color + ",int:jedi.crystal.stats.level=-1");
                hue.setColor(crystal, "/private/index_color_1", color);
            }
        }
        if (!isIdValid(crystal))
        {
            LOG("ghosts_profession_kit", "could not create a colour crystal inside " + saber);
            return false;
        }
        if (!jedi.hasColorCrystal(saber))
        {
            // the container's OnReceivedItem may already have applied the crystal
            jedi.addCrystalStats(saber, crystal);
        }
        // the blade colour lives in private/index_color_blade; addCrystalStats() already
        // sets it, this covers the case where saber_inv applied the crystal itself
        jedi.setSaberColor(saber, color, 0);
        weapons.setWeaponData(saber);
        LOG("ghosts_profession_kit", "colour " + color + " crystal " + crystal + " (" + crystalName + ") installed in " + saber + ": hasColor=" + jedi.hasColorCrystal(saber));
        return true;
    }

    public static boolean grantJewelrySet(obj_id player, int style) throws InterruptedException
    {
        if (!isIdValid(player) || style < 0 || style >= JEWELRY_SETS.length)
        {
            return false;
        }
        int given = 0;
        for (int i = 0; i < JEWELRY_SETS[style].length; i++)
        {
            obj_id piece = giveStatic(player, JEWELRY_SETS[style][i]);
            if (!isIdValid(piece))
            {
                continue;
            }
            given++;
            // the one-time style exchange lives on the pieces themselves
            if (!hasObjVar(player, SWAP_USED_OBJVAR) && !hasObjVar(player, SWAP_RUNNING_OBJVAR))
            {
                attachScript(piece, SWAP_SCRIPT);
            }
        }
        if (given < JEWELRY_SETS[style].length)
        {
            LOG("ghosts_profession_kit", "only " + given + " of " + JEWELRY_SETS[style].length + " jewellery pieces were created for " + player);
            return false;
        }
        setObjVar(player, STYLE_OBJVAR, style);
        LOG("ghosts_profession_kit", "jewellery style " + style + " granted to " + player);
        return true;
    }

    public static obj_id giveTemplate(obj_id player, String template) throws InterruptedException
    {
        obj_id item = createObjectInInventoryAllowOverload(template, player);
        if (!isIdValid(item))
        {
            LOG("ghosts_profession_kit", "could not create " + template + " for " + player);
        }
        return item;
    }

    public static obj_id giveStatic(obj_id player, String itemName) throws InterruptedException
    {
        obj_id inventory = utils.getInventoryContainer(player);
        if (!isIdValid(inventory))
        {
            return null;
        }
        obj_id item = static_item.createNewItemFunction(itemName, inventory);
        if (!isIdValid(item))
        {
            LOG("ghosts_profession_kit", "could not create static item " + itemName + " for " + player);
        }
        return item;
    }
}
