package script.library;

import script.obj_id;

/**
 * SWG Evolve style "Path of the Jedi" unlock, server side.
 *
 * Five phases:
 *   1 badges   - the Jedi exploration badge collection, Jabba's theme park badge
 *                and at least 50 badges in the badge book
 *   2 jedi     - the two Jedi artifact collections and the Count Dooku story collection
 *   3 heroics  - the five original heroic collections (Tusken King, IG-88, Axkva Min,
 *                Exar Kun, Star Destroyer)
 *   4 shrines  - a force shrine on ten different planets (recorded by force_shrine)
 *   5 relics   - ten or more slots of the Relics of the Elders (saga relic) book
 *
 * The phase state is recomputed on every login.  When a phase changes the player
 * gets one system message with the whole checklist; when all five pass the character
 * is converted to the Force Sensitive profession and the guard is written.
 *
 * The Evolve Path of the Jedi tab and the eighth character slot are client and
 * central-account features this rebuild does not have, so the checklist is delivered
 * as a system message instead.
 */
public class evolve_jedi_path extends script.base_script
{
    public evolve_jedi_path()
    {
    }

    public static final String GUARD_OBJVAR = "evolve.jediPathComplete";
    public static final String PHASE_MASK_OBJVAR = "evolve.jediPhaseMask";
    public static final String SHRINE_OBJVAR = "evolve.shrinePlanets";

    public static final String BADGE_BOOK = "badge_book";
    public static final int BADGE_COUNT_REQUIRED = 50;
    public static final String JEDI_EXPLORATION_COLLECTION = "col_bdg_explore_jedi";
    public static final String JABBA_BADGE = "bdg_thm_park_jabba_badge";

    public static final String[] JEDI_COLLECTIONS =
    {
        "inv_holocron_collection_01",
        "inv_holocron_collection_02",
        "col_story_count_dooku"
    };

    public static final String[] HEROIC_COLLECTIONS =
    {
        "col_tusken_king_01",
        "col_ig88_factory_01",
        "col_axkva_min_01",
        "col_exar_kun_01",
        "col_star_destroyer_01"
    };

    public static final String RELIC_BOOK = "saga_relic_book";
    public static final int RELIC_SLOTS_REQUIRED = 10;
    public static final int SHRINE_PLANETS_REQUIRED = 10;

    public static final String JEDI_TEMPLATE = "force_sensitive_1a";

    public static boolean[] getPhases(obj_id player) throws InterruptedException
    {
        boolean[] phases = new boolean[5];

        String[] badges = getCompletedCollectionSlotsInBook(player, BADGE_BOOK);
        int badgeCount = (badges == null) ? 0 : badges.length;
        phases[0] = hasCompletedCollection(player, JEDI_EXPLORATION_COLLECTION)
            && badge.hasBadge(player, JABBA_BADGE)
            && badgeCount >= BADGE_COUNT_REQUIRED;

        phases[1] = true;
        for (int i = 0; i < JEDI_COLLECTIONS.length; i++)
        {
            if (!hasCompletedCollection(player, JEDI_COLLECTIONS[i]))
            {
                phases[1] = false;
            }
        }

        phases[2] = true;
        for (int i = 0; i < HEROIC_COLLECTIONS.length; i++)
        {
            if (!hasCompletedCollection(player, HEROIC_COLLECTIONS[i]))
            {
                phases[2] = false;
            }
        }

        phases[3] = countShrines(player) >= SHRINE_PLANETS_REQUIRED;

        String[] relics = getCompletedCollectionSlotsInBook(player, RELIC_BOOK);
        phases[4] = relics != null && relics.length >= RELIC_SLOTS_REQUIRED;

        return phases;
    }

    public static void checkAndUnlock(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player) || hasObjVar(player, GUARD_OBJVAR))
        {
            return;
        }
        boolean[] phases = getPhases(player);
        int mask = 0;
        for (int i = 0; i < phases.length; i++)
        {
            if (phases[i])
            {
                mask |= (1 << i);
            }
        }
        if (mask != getIntObjVar(player, PHASE_MASK_OBJVAR))
        {
            setObjVar(player, PHASE_MASK_OBJVAR, mask);
            sendSystemMessageTestingOnly(player, statusText(phases));
            LOG("evolve_jedi", "path phases for " + player + " = " + mask + " (" + statusText(phases) + ")");
        }
        if (mask == 31)
        {
            setObjVar(player, GUARD_OBJVAR, 1);
            becomeJedi(player);
        }
    }

    public static String statusText(boolean[] phases) throws InterruptedException
    {
        return "Path of the Jedi - badges " + mark(phases[0]) + ", Jedi collections " + mark(phases[1])
            + ", heroics " + mark(phases[2]) + ", shrines " + mark(phases[3])
            + ", relics " + mark(phases[4]);
    }

    public static String mark(boolean done)
    {
        return done ? "[done]" : "[ ]";
    }

    public static int countShrines(obj_id player) throws InterruptedException
    {
        String list = hasObjVar(player, SHRINE_OBJVAR) ? getStringObjVar(player, SHRINE_OBJVAR) : "";
        if (list == null || list.length() == 0)
        {
            return 0;
        }
        String[] planets = split(list, ',');
        if (planets == null)
        {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < planets.length; i++)
        {
            if (planets[i] != null && planets[i].length() > 0)
            {
                count++;
            }
        }
        return count;
    }

    public static void recordShrineVisit(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player))
        {
            return;
        }
        String scene = getCurrentSceneName();
        if (scene == null || scene.length() == 0)
        {
            return;
        }
        String list = hasObjVar(player, SHRINE_OBJVAR) ? getStringObjVar(player, SHRINE_OBJVAR) : "";
        if (list == null)
        {
            list = "";
        }
        if (("," + list + ",").contains("," + scene + ","))
        {
            return;
        }
        String updated = (list.length() == 0) ? scene : list + "," + scene;
        setObjVar(player, SHRINE_OBJVAR, updated);
        LOG("evolve_jedi", "shrine visit on " + scene + " for " + player + " (" + countShrines(player) + "/" + SHRINE_PLANETS_REQUIRED + ")");
        if (countShrines(player) >= SHRINE_PLANETS_REQUIRED)
        {
            sendSystemMessageTestingOnly(player, "The Force shrines of ten worlds have answered you - the Path of the Jedi is one step closer.");
        }
    }

    /**
     * Convert the character to the Force Sensitive profession, mirroring what
     * player_jedi_conversion and terminal_character_builder do on this server.
     */
    public static void becomeJedi(obj_id player) throws InterruptedException
    {
        setJediState(player, JEDI_STATE_JEDI);
        setMaxForcePower(player, 10);
        setForcePowerRegenRate(player, 1);
        setSkillTemplate(player, JEDI_TEMPLATE);
        if (!hasSkill(player, JEDI_TEMPLATE))
        {
            grantSkill(player, JEDI_TEMPLATE);
        }
        if (!hasSkill(player, "force_title_jedi_rank_01"))
        {
            grantSkill(player, "force_title_jedi_rank_01");
        }
        if (!hasSkill(player, "force_title_jedi_rank_02"))
        {
            grantSkill(player, "force_title_jedi_rank_02");
        }
        skill.recalcPlayerPools(player, true);
        utils.fullExpertiseReset(player, true);
        expertise.autoAllocateExpertiseByLevel(player, false);
        sendSystemMessageTestingOnly(player, "The Path of the Jedi is complete. The Force is with you - your character is now a Jedi.");
        LOG("evolve_jedi", "PATH COMPLETE - " + player + " converted to " + JEDI_TEMPLATE);
    }
}
