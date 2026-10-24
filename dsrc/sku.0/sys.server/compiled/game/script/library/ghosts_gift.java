package script.library;

import script.dictionary;
import script.obj_id;

/**
 * Ghosts login gift.
 *
 * Granted once per character, the first time the avatar loads (hooked from
 * script.player.base.base_player.OnInitialize, next to the Force Sensitive grant).
 * Guarded by the ghosts.loginGift objvar so a second login never re-grants it and
 * so a later gift can simply bump GIFT_VERSION.
 *
 * Contents: a full composite armour set, a profession-appropriate starter weapon,
 * profession-appropriate jewellery plus the Mark of the Hero ring, three collection
 * reward items, and 100,000 credits in cash and 100,000 in the bank (granted from the
 * newPlayerQuests named account with money.bankTo(), then money.withdraw() for the cash
 * half - see the note in grantIfDue()).
 *
 * Every object template below was checked against this server's own object template
 * table (data/sku.0/sys.server/built/game/misc/object_template_crc_string_table.iff,
 * 60257 entries); the collection entries were checked against
 * datatables/item/master_item/master_item.tab.
 */
public class ghosts_gift extends script.base_script
{
	public ghosts_gift()
	{
	}

	public static final String GIFT_OBJVAR = "ghosts.loginGift";
	public static final int GIFT_VERSION = 1;

	public static final int CREDITS_CASH = 100000;
	public static final int CREDITS_BANK = 100000;

	public static final String[] ARMOR =
	{
		"object/tangible/wearables/armor/composite/armor_composite_helmet.iff",
		"object/tangible/wearables/armor/composite/armor_composite_chest_plate.iff",
		"object/tangible/wearables/armor/composite/armor_composite_gloves.iff",
		"object/tangible/wearables/armor/composite/armor_composite_bicep_l.iff",
		"object/tangible/wearables/armor/composite/armor_composite_bicep_r.iff",
		"object/tangible/wearables/armor/composite/armor_composite_bracer_l.iff",
		"object/tangible/wearables/armor/composite/armor_composite_bracer_r.iff",
		"object/tangible/wearables/armor/composite/armor_composite_leggings.iff",
		"object/tangible/wearables/armor/composite/armor_composite_boots.iff"
	};

	// static items (datatables/collection/rewards.tab -> master_item.tab)
	public static final String[] COLLECTIONS =
	{
		"col_reward_desert_demon_doll_02_01",
		"col_reward_hoth_meteorite_geode_02_01",
		"col_reward_family_heirloom_02_01"
	};

	// the "heroic" piece: the ring the Hero of Tatooine chain hands out as its mark
	public static final String HEROIC_RING = "object/tangible/wearables/ring/ring_mark_hero.iff";

	public static final String WEAPON_PISTOL = "object/weapon/ranged/pistol/pistol_cdef_npe.iff";
	public static final String WEAPON_CARBINE = "object/weapon/ranged/carbine/carbine_cdef_npe.iff";
	public static final String WEAPON_RIFLE = "object/weapon/ranged/rifle/rifle_cdef_npe.iff";
	public static final String WEAPON_SABER = "object/weapon/melee/2h_sword/crafted_saber/sword_lightsaber_two_handed_gen1.iff";

	public static boolean grantIfDue(obj_id player) throws InterruptedException
	{
		if (!isIdValid(player))
		{
			return false;
		}
		if (hasObjVar(player, GIFT_OBJVAR) && getIntObjVar(player, GIFT_OBJVAR) >= GIFT_VERSION)
		{
			return false;
		}

		obj_id inventory = utils.getInventoryContainer(player);
		if (!isIdValid(inventory))
		{
			// do not burn the guard: try again on the next login
			LOG("ghosts_gift", "no inventory container for " + player + " yet - deferring the login gift");
			return false;
		}

		int profession = getProfessionIndex(player);
		int given = 0;

		given += giveItem(player, inventory, weaponFor(profession));
		given += giveItem(player, inventory, necklaceFor(profession));
		given += giveItem(player, inventory, ringFor(profession));
		given += giveItem(player, inventory, HEROIC_RING);
		for (int i = 0; i < ARMOR.length; i++)
		{
			given += giveItem(player, inventory, ARMOR[i]);
		}
		for (int i = 0; i < COLLECTIONS.length; i++)
		{
			given += giveStaticItem(player, inventory, COLLECTIONS[i]);
		}

		int cashBefore = getCashBalance(player);
		int bankBefore = getBankBalance(player);
		// money.bankTo(<named account>, player, amt) credits the receiver's bank straight from the
		// named account: ServerObject::transferBankCreditsFrom() only adjusts the *receiver's* bank
		// balance (ServerObject.cpp:4906) and never debits the account, and it runs inline because
		// the player object executing this login script is authoritative.  The quest scripts fund
		// their rewards the same way.  Do NOT use money.systemPayout() here: that routes the credit
		// through player_money.acctToPass, which withdraws the whole amount straight back out to
		// cash, so a following withdraw would then fail for lack of bank funds.
		boolean funded = money.bankTo(money.ACCT_NEW_PLAYER_QUESTS, player, CREDITS_BANK + CREDITS_CASH);
		boolean split = false;
		if (getBankBalance(player) >= CREDITS_CASH)
		{
			// move the cash half of the gift out of the bank into the player's pocket
			split = money.withdraw(player, CREDITS_CASH);
		}
		else
		{
			LOG("ghosts_gift", "gift credit did not land in the bank for " + player + " (bank=" + getBankBalance(player) + ") - leaving the whole gift in the bank");
		}
		int cashAfter = getCashBalance(player);
		int bankAfter = getBankBalance(player);

		setObjVar(player, GIFT_OBJVAR, GIFT_VERSION);
		sendSystemMessageTestingOnly(player, "Welcome to SWG Ghosts. Your one-off login gift: a composite armour set, a starter weapon, jewellery, three collection items, and " + CREDITS_CASH + " credits in cash plus " + CREDITS_BANK + " in the bank.");

		LOG("ghosts_gift", "gift v" + GIFT_VERSION + " to " + player + " (" + getSkillTemplate(player) + ", profession " + profession + "): items=" + given + " funded=" + funded + " split=" + split + " cash " + cashBefore + "->" + cashAfter + " bank " + bankBefore + "->" + bankAfter);
		return true;
	}

	public static int getProfessionIndex(obj_id player) throws InterruptedException
	{
		if (utils.isProfession(player, utils.COMMANDO))
		{
			return utils.COMMANDO;
		}
		if (utils.isProfession(player, utils.SMUGGLER))
		{
			return utils.SMUGGLER;
		}
		if (utils.isProfession(player, utils.MEDIC))
		{
			return utils.MEDIC;
		}
		if (utils.isProfession(player, utils.OFFICER))
		{
			return utils.OFFICER;
		}
		if (utils.isProfession(player, utils.SPY))
		{
			return utils.SPY;
		}
		if (utils.isProfession(player, utils.BOUNTY_HUNTER))
		{
			return utils.BOUNTY_HUNTER;
		}
		if (utils.isProfession(player, utils.FORCE_SENSITIVE))
		{
			return utils.FORCE_SENSITIVE;
		}
		if (utils.isProfession(player, utils.TRADER))
		{
			return utils.TRADER;
		}
		if (utils.isProfession(player, utils.ENTERTAINER))
		{
			return utils.ENTERTAINER;
		}
		return 0;
	}

	public static String weaponFor(int profession)
	{
		switch (profession)
		{
			case utils.COMMANDO:
				return WEAPON_RIFLE;
			case utils.BOUNTY_HUNTER:
			case utils.SPY:
				return WEAPON_CARBINE;
			case utils.FORCE_SENSITIVE:
				return WEAPON_SABER;
			default:
				return WEAPON_PISTOL;
		}
	}

	// the s01-s12 ring/necklace family is the wearable jewellery in this data (see
	// object/tangible/wearables/ring + necklace in the template table); the Force
	// Sensitive line gets the ice pendant that the FS medic puzzle hands out.
	public static String necklaceFor(int profession)
	{
		switch (profession)
		{
			case utils.COMMANDO:
				return "object/tangible/wearables/necklace/necklace_s01.iff";
			case utils.SMUGGLER:
				return "object/tangible/wearables/necklace/necklace_s02.iff";
			case utils.MEDIC:
				return "object/tangible/wearables/necklace/necklace_s03.iff";
			case utils.OFFICER:
				return "object/tangible/wearables/necklace/necklace_s04.iff";
			case utils.SPY:
				return "object/tangible/wearables/necklace/necklace_s05.iff";
			case utils.BOUNTY_HUNTER:
				return "object/tangible/wearables/necklace/necklace_s06.iff";
			case utils.FORCE_SENSITIVE:
				return "object/tangible/wearables/necklace/necklace_ice_pendant.iff";
			case utils.TRADER:
				return "object/tangible/wearables/necklace/necklace_s07.iff";
			case utils.ENTERTAINER:
				return "object/tangible/wearables/necklace/necklace_s08.iff";
			default:
				return "object/tangible/wearables/necklace/necklace_s01.iff";
		}
	}

	public static String ringFor(int profession)
	{
		switch (profession)
		{
			case utils.COMMANDO:
			case utils.SPY:
				return "object/tangible/wearables/ring/ring_s01.iff";
			case utils.SMUGGLER:
			case utils.BOUNTY_HUNTER:
				return "object/tangible/wearables/ring/ring_s02.iff";
			case utils.MEDIC:
			case utils.TRADER:
				return "object/tangible/wearables/ring/ring_s03.iff";
			case utils.OFFICER:
			case utils.FORCE_SENSITIVE:
			case utils.ENTERTAINER:
				return "object/tangible/wearables/ring/ring_s04.iff";
			default:
				return "object/tangible/wearables/ring/ring_s01.iff";
		}
	}

	public static int giveItem(obj_id player, obj_id inventory, String template) throws InterruptedException
	{
		obj_id item = createObjectInInventoryAllowOverload(template, player);
		if (!isIdValid(item))
		{
			LOG("ghosts_gift", "could not create " + template + " for " + player);
			return 0;
		}
		return 1;
	}

	public static int giveStaticItem(obj_id player, obj_id inventory, String itemName) throws InterruptedException
	{
		obj_id item = static_item.createNewItemFunction(itemName, inventory);
		if (!isIdValid(item))
		{
			LOG("ghosts_gift", "could not create static item " + itemName + " for " + player);
			return 0;
		}
		return 1;
	}
}
