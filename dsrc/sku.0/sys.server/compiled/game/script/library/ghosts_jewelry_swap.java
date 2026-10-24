package script.library;

import script.dictionary;
import script.menu_info;
import script.menu_info_types;
import script.obj_id;
import script.string_id;

/**
 * One-time heroic jewellery style exchange.
 *
 * ghosts_profession_kit attaches this to each piece it grants.  The piece gets a
 * "Use" radial (the standard ui_radial:item_use label), which opens the four-style
 * list; choosing a style grants the five pieces of that style and then destroys
 * the five pieces of the old one.  ghosts.jewelrySwapUsed records that the exchange
 * has been spent, so the radial disappears for good.
 */
public class ghosts_jewelry_swap extends script.base_script
{
    public ghosts_jewelry_swap()
    {
    }

    public int OnObjectMenuRequest(obj_id self, obj_id player, menu_info mi) throws InterruptedException
    {
        if (!canSwap(self, player))
        {
            return SCRIPT_CONTINUE;
        }
        mi.addRootMenu(menu_info_types.ITEM_USE, new string_id("ui_radial", "item_use"));
        return SCRIPT_CONTINUE;
    }

    public int OnObjectMenuSelect(obj_id self, obj_id player, int item) throws InterruptedException
    {
        if (item != menu_info_types.ITEM_USE)
        {
            return SCRIPT_CONTINUE;
        }
        if (hasObjVar(player, ghosts_profession_kit.SWAP_USED_OBJVAR))
        {
            sendSystemMessageTestingOnly(player, "The heroic jewellery style can only be exchanged once.");
            return SCRIPT_CONTINUE;
        }
        if (!canSwap(self, player))
        {
            return SCRIPT_CONTINUE;
        }
        sui.listbox(self, player, "Choose the heroic jewellery style you want. All five pieces of your current set are exchanged for the new style. This can only be done once.", sui.OK_CANCEL, "Heroic Jewellery", ghosts_profession_kit.JEWELRY_STYLE_NAMES, "handleSwapChoice", true, false);
        return SCRIPT_CONTINUE;
    }

    public int handleSwapChoice(obj_id self, dictionary params) throws InterruptedException
    {
        if (params == null || params.isEmpty())
        {
            return SCRIPT_CONTINUE;
        }
        if (sui.getIntButtonPressed(params) == sui.BP_CANCEL)
        {
            return SCRIPT_CONTINUE;
        }
        obj_id player = sui.getPlayerId(params);
        if (!isIdValid(player) || !canSwap(self, player))
        {
            return SCRIPT_CONTINUE;
        }
        int row = sui.getListboxSelectedRow(params);
        if (row < 0 || row >= ghosts_profession_kit.JEWELRY_SETS.length)
        {
            return SCRIPT_CONTINUE;
        }
        int current = hasObjVar(player, ghosts_profession_kit.STYLE_OBJVAR) ? getIntObjVar(player, ghosts_profession_kit.STYLE_OBJVAR) : ghosts_profession_kit.JEWELRY_STYLE;
        if (row == current)
        {
            sendSystemMessageTestingOnly(player, "That is already your heroic jewellery style.");
            return SCRIPT_CONTINUE;
        }

        // the new pieces must not carry another copy of this exchange
        setObjVar(player, ghosts_profession_kit.SWAP_RUNNING_OBJVAR, row);
        boolean granted = ghosts_profession_kit.grantJewelrySet(player, row);
        removeObjVar(player, ghosts_profession_kit.SWAP_RUNNING_OBJVAR);
        if (!granted)
        {
            sendSystemMessageTestingOnly(player, "The exchange failed - your current set has been left untouched. Try again later.");
            return SCRIPT_CONTINUE;
        }
        int removed = removeJewelrySet(player, current);
        setObjVar(player, ghosts_profession_kit.SWAP_USED_OBJVAR, 1);
        setObjVar(player, ghosts_profession_kit.STYLE_OBJVAR, row);
        sendSystemMessageTestingOnly(player, "Heroic jewellery exchanged: " + ghosts_profession_kit.JEWELRY_STYLE_NAMES[row] + " (two rings, necklace and both bracelets).");
        LOG("ghosts_jewelry_swap", "style " + current + " -> " + row + " for " + player + ", old pieces removed=" + removed);
        return SCRIPT_CONTINUE;
    }

    public static int removeJewelrySet(obj_id player, int style) throws InterruptedException
    {
        if (!isIdValid(player) || style < 0 || style >= ghosts_profession_kit.JEWELRY_SETS.length)
        {
            return 0;
        }
        obj_id inventory = utils.getInventoryContainer(player);
        if (!isIdValid(inventory))
        {
            return 0;
        }
        obj_id[] contents = getContents(inventory);
        int removed = 0;
        if (contents != null)
        {
            for (int i = 0; i < contents.length; i++)
            {
                obj_id item = contents[i];
                if (!isIdValid(item))
                {
                    continue;
                }
                String name = getStaticItemName(item);
                if (name == null || name.equals(""))
                {
                    continue;
                }
                for (int j = 0; j < ghosts_profession_kit.JEWELRY_SETS[style].length; j++)
                {
                    if (name.equals(ghosts_profession_kit.JEWELRY_SETS[style][j]))
                    {
                        destroyObject(item);
                        removed++;
                        break;
                    }
                }
            }
        }
        return removed;
    }

    public static boolean canSwap(obj_id item, obj_id player) throws InterruptedException
    {
        if (!isIdValid(item) || !isIdValid(player))
        {
            return false;
        }
        if (hasObjVar(player, ghosts_profession_kit.SWAP_USED_OBJVAR))
        {
            return false;
        }
        obj_id owner = utils.getContainingPlayer(item);
        return isIdValid(owner) && owner == player;
    }
}
