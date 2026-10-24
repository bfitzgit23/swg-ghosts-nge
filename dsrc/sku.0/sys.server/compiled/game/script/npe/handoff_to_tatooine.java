package script.npe;

import script.library.groundquests;
import script.location;
import script.obj_id;

public class handoff_to_tatooine extends script.base_script
{
    public handoff_to_tatooine()
    {
    }
    public static final String questNewbieStart = "quest/speeder_quest";
    public static final String questNewbieStartBH = "quest/speeder_quest";
    public static final String questCrafterEntertainer = "quest/tatooine_eisley_noncombat";

    // attachScript() only runs OnAttach, and the player is still online when the
    // station exit attaches this script and warps them to Tatooine - OnLogin would
    // not fire until the next relog, which is why the legacy quest never started.
    public int OnAttach(obj_id self) throws InterruptedException
    {
        grantHandoff(self);
        return SCRIPT_CONTINUE;
    }

    public int OnLogin(obj_id self) throws InterruptedException
    {
        grantHandoff(self);
        detachScript(self, "npe.handoff_to_tatooine");
        return SCRIPT_CONTINUE;
    }

    public void grantHandoff(obj_id self) throws InterruptedException
    {
        String profession = getSkillTemplate(self);
        if (profession == null)
        {
            LOG("npe_handoff", "no skill template yet for " + self + " - deferring the Tatooine handoff");
            return;
        }
        int crafter = profession.indexOf("trader");
        int entertainer = profession.indexOf("entertainer");
        int bountyhunter = profession.indexOf("bounty_hunter");
        if (crafter > -1 || entertainer > -1)
        {
            if (!groundquests.isQuestActiveOrComplete(self, questCrafterEntertainer))
            {
                grantQuestLogged(self, questCrafterEntertainer);
            }
        }
        else if (bountyhunter > -1)
        {
            if (!groundquests.hasCompletedQuest(self, questNewbieStartBH) && !groundquests.isQuestActive(self, questNewbieStartBH))
            {
                grantQuestLogged(self, questNewbieStartBH);
            }
        }
        else
        {
            if (!groundquests.hasCompletedQuest(self, questNewbieStart) && !groundquests.isQuestActive(self, questNewbieStart))
            {
                grantQuestLogged(self, questNewbieStart);
            }
        }
        if (crafter > -1)
        {
            newbieTutorialSetToolbarElement(self, 10, "/survey");
        }
        newbieTutorialEnableHudElement(self, "radar", true, 0);
        LOG("npe_handoff", "Tatooine handoff finished for " + self + " (" + profession + ")");
    }

    /**
     * A character that landed on Tatooine before the handoff ran never received the
     * legacy quest.  Grant it once on the next login, but only on Tatooine and only
     * when the quest is neither active nor complete.
     */
    public static void grantLegacyIfDue(obj_id player) throws InterruptedException
    {
        if (!isIdValid(player) || hasObjVar(player, "ghosts.legacyHandoff"))
        {
            return;
        }
        String scene = getCurrentSceneName();
        if (scene == null || scene.length() == 0)
        {
            location loc = getLocation(player);
            if (loc != null)
            {
                scene = loc.area;
            }
        }
        if (scene == null || !scene.equals("tatooine"))
        {
            return;
        }
        if (groundquests.isQuestActiveOrComplete(player, questNewbieStart) || groundquests.isQuestActiveOrComplete(player, questCrafterEntertainer))
        {
            setObjVar(player, "ghosts.legacyHandoff", 1);
            return;
        }
        LOG("npe_handoff", "legacy quest missing for landed character " + player + " - granting it now");
        int questId = groundquests.getQuestIdFromString(questNewbieStart);
        if (questId == 0)
        {
            LOG("npe_handoff", "quest " + questNewbieStart + " is not registered on this server");
            return;
        }
        groundquests.grantQuest(player, questNewbieStart);
        setObjVar(player, "ghosts.legacyHandoff", 1);
    }

    public void grantQuestLogged(obj_id player, String questName) throws InterruptedException
    {
        int questId = groundquests.getQuestIdFromString(questName);
        if (questId == 0)
        {
            LOG("npe_handoff", "quest " + questName + " is not registered on this server - cannot start the legacy quest");
            return;
        }
        LOG("npe_handoff", "granting " + questName + " (id " + questId + ") to " + player);
        groundquests.grantQuest(player, questName);
    }
}
