#!/usr/bin/env bash
#
# apply_talon_mods.sh
# One-shot, idempotent Talon (swg-source / NGE) tweaks:
#   1) Big expertise point pool           (player_level.tab)
#   2) "Classless" cross-training         (expertise.java + skill.java)
#        - any character can train ANY profession's expertise trees
#        - removes the anti-cheat that REVOKES cross-profession expertise
#   3) Drop expertise level prerequisites (expertise.tab)  [optional]
#   4) Boosted holocron (collection) drops (loot.java)
#
# Safe to run more than once. Every file it touches is backed up ONCE to
# <file>.talonbak the first time. Use  ./apply_talon_mods.sh --revert  to
# restore every backup and undo everything.
#
# USAGE (from anywhere; point it at the dir that CONTAINS dsrc/ and src/):
#   TALON_ROOT=/path/to/talon ./apply_talon_mods.sh
#   ./apply_talon_mods.sh /path/to/talon
#   ./apply_talon_mods.sh --revert /path/to/talon
#
# AFTER RUNNING you MUST rebuild (this script only edits source):
#   - Recompile the .tab -> .iff datatables (DataTableTool) for
#     player_level.tab and expertise.tab
#   - Recompile the server scripts (your normal script build)
#   - Restart the server; max-level chars need a respec/relog for new points
# --------------------------------------------------------------------------

set -euo pipefail

# ------------------------- TUNABLES ---------------------------------------
# Expertise points at level 90 = EXP_L10 + 40 * EXP_EVEN
#   45  (stock)          -> EXP_L10=5   EXP_EVEN=1
#   135 (your screenshot)-> EXP_L10=15  EXP_EVEN=3
#   270 (fill lots)      -> EXP_L10=30  EXP_EVEN=6
#   ~unlimited feel      -> EXP_L10=100 EXP_EVEN=25   (= 1100)
EXP_L10="${EXP_L10:-15}"
EXP_EVEN="${EXP_EVEN:-3}"

# Holocron drop: independent % per eligible kill (0 disables that mod)
HOLO_CHANCE="${HOLO_CHANCE:-15}"

# Toggle individual mods (1=on 0=off)
DO_POINTS="${DO_POINTS:-1}"
DO_CLASSLESS="${DO_CLASSLESS:-1}"
DO_NOLEVELREQ="${DO_NOLEVELREQ:-1}"
DO_HOLOCRON="${DO_HOLOCRON:-1}"
# --------------------------------------------------------------------------

REVERT=0
ROOT=""
for a in "$@"; do
  case "$a" in
    --revert) REVERT=1 ;;
    *) ROOT="$a" ;;
  esac
done
ROOT="${ROOT:-${TALON_ROOT:-.}}"

if [[ ! -d "$ROOT/dsrc" ]]; then
  echo "ERROR: '$ROOT' has no dsrc/ subfolder. Point me at the dir containing dsrc/ and src/." >&2
  exit 1
fi

DS="$ROOT/dsrc/sku.0"
PLAYER_LEVEL="$DS/sys.shared/compiled/game/datatables/player/player_level.tab"
EXPERTISE_TAB="$DS/sys.shared/compiled/game/datatables/expertise/expertise.tab"
EXPERTISE_JAVA="$DS/sys.server/compiled/game/script/library/expertise.java"
SKILL_JAVA="$DS/sys.server/compiled/game/script/library/skill.java"
LOOT_JAVA="$DS/sys.server/compiled/game/script/library/loot.java"

export EXP_L10 EXP_EVEN HOLO_CHANCE DO_POINTS DO_CLASSLESS DO_NOLEVELREQ DO_HOLOCRON REVERT
export PLAYER_LEVEL EXPERTISE_TAB EXPERTISE_JAVA SKILL_JAVA LOOT_JAVA

python3 - "$@" <<'PYEOF'
import os, sys, shutil

REVERT = os.environ["REVERT"] == "1"
files = {
    "player_level": os.environ["PLAYER_LEVEL"],
    "expertise_tab": os.environ["EXPERTISE_TAB"],
    "expertise_java": os.environ["EXPERTISE_JAVA"],
    "skill_java": os.environ["SKILL_JAVA"],
    "loot_java": os.environ["LOOT_JAVA"],
}

def read(p):
    return open(p, "rb").read().decode("utf-8")

def write_crlf(p, text):
    text = text.replace("\r\n", "\n").replace("\n", "\r\n")
    open(p, "wb").write(text.encode("utf-8"))

def backup_once(p):
    b = p + ".talonbak"
    if not os.path.exists(b):
        shutil.copy2(p, b)

def revert_all():
    n = 0
    for p in files.values():
        b = p + ".talonbak"
        if os.path.exists(b):
            shutil.copy2(b, p); n += 1
            print("  reverted", os.path.relpath(p))
    print(f"Reverted {n} file(s) from .talonbak backups.")

if REVERT:
    revert_all(); sys.exit(0)

changed = []

# --- 1) expertise point pool ---------------------------------------------
if os.environ["DO_POINTS"] == "1":
    p = files["player_level"]; backup_once(p)
    L10 = int(os.environ["EXP_L10"]); EVEN = int(os.environ["EXP_EVEN"])
    lines = read(p).replace("\r\n","\n").split("\n")
    hdr = lines[0].split("\t")
    col = hdr.index("expertise_points"); lvlc = hdr.index("level")
    total = 0
    for i in range(2, len(lines)):
        if not lines[i].strip(): continue
        parts = lines[i].split("\t")
        if len(parts) <= col: continue
        try: lvl = int(parts[lvlc])
        except: continue
        if lvl == 10: parts[col] = str(L10)
        elif lvl > 10 and lvl % 2 == 0: parts[col] = str(EVEN)
        elif int(parts[col] or 0) != 0: parts[col] = "0"
        try: total += int(parts[col])
        except: pass
        lines[i] = "\t".join(parts)
    write_crlf(p, "\n".join(lines))
    changed.append(f"points pool -> {total} at level 90")

# --- 3) expertise.tab: strip PREREQ_LEVEL --------------------------------
if os.environ["DO_NOLEVELREQ"] == "1":
    p = files["expertise_tab"]; backup_once(p)
    lines = read(p).replace("\r\n","\n").split("\n")
    hdr = lines[0].split("\t")
    if "PREREQ_LEVEL" in hdr:
        pc = hdr.index("PREREQ_LEVEL")
        for i in range(2, len(lines)):
            if not lines[i].strip(): continue
            parts = lines[i].split("\t")
            if len(parts) > pc and parts[pc].strip() not in ("", "1"):
                parts[pc] = ""      # blank -> datatable default (1) = no gate
                lines[i] = "\t".join(parts)
        write_crlf(p, "\n".join(lines))
        changed.append("expertise level prerequisites cleared")

# --- 2) classless: expertise.java purchase gate --------------------------
if os.environ["DO_CLASSLESS"] == "1":
    p = files["expertise_java"]; backup_once(p); t = read(p)
    if "TALON_CLASSLESS" not in t:
        old = ('            else \r\n'
               '            {\r\n'
               '                CustomerServiceLog("SuspectedCheaterChannel: ", "DualProfessionCheat: Player " + getFirstName(player) + "(" + player + ") attempted to give themselves an expertise they cannot have.");\r\n'
               '                CustomerServiceLog("SuspectedCheaterChannel: ", "DualProfessionCheat: Player " + getFirstName(player) + "(" + player + ") Their profession is " + profession + " and the skill was " + skillName + ".");\r\n'
               '                return false;\r\n'
               '            }\r\n')
        new = ('            else \r\n'
               '            {\r\n'
               '                // TALON_CLASSLESS: cross-train any profession EXCEPT Jedi/Force,\r\n'
               '                // which stays gated to force_sensitive characters (normal NGE).\r\n'
               '                if (reqProf.equals("force_sensitive"))\r\n'
               '                {\r\n'
               '                    return false;\r\n'
               '                }\r\n'
               '                return true;\r\n'
               '            }\r\n')
        if old in t:
            write_crlf(p, t.replace(old, new)); changed.append("expertise.java: profession gate opened")
        else:
            sys.stderr.write("WARN: expertise.java gate block not found verbatim; skipped (already modded or source differs)\n")

    # skill.java revoke-on-load
    p = files["skill_java"]; backup_once(p); t = read(p)
    if "TALON_CLASSLESS" not in t:
        old = ('                if ((!reqProf.equals("trader") || !isTrader) && !reqProf.equals("all")) {\r\n'
               '                    sendSystemMessage(player, SID_EXPERTISE_WRONG_PROFESSION);\r\n'
               '                    utils.fullExpertiseReset(player, false);\r\n'
               '                    CustomerServiceLog("SuspectedCheaterChannel: ", "DualProfessionCheat: Player " + getFirstName(player) + "(" + player + ") has an expertise that is not for thier profession. All expertises have been revoked.");\r\n'
               '                    CustomerServiceLog("SuspectedCheaterChannel: ", "DualProfessionCheat: Player " + getFirstName(player) + "(" + player + ")\'s profession is " + profession + " and the skill they have is " + expertiseSkill + ".");\r\n'
               '                    return false;\r\n'
               '                }\r\n')
        new = ('                // TALON_CLASSLESS: Jedi/Force stays gated; all other cross-profession allowed\r\n'
               '                if (reqProf.equals("force_sensitive")) {\r\n'
               '                    sendSystemMessage(player, SID_EXPERTISE_WRONG_PROFESSION);\r\n'
               '                    utils.fullExpertiseReset(player, false);\r\n'
               '                    return false;\r\n'
               '                }\r\n')
        if old in t:
            write_crlf(p, t.replace(old, new)); changed.append("skill.java: cross-profession revoke disabled")
        else:
            sys.stderr.write("WARN: skill.java revoke block not found verbatim; skipped (already modded or source differs)\n")

# --- 4) holocron drops ----------------------------------------------------
if os.environ["DO_HOLOCRON"] == "1":
    p = files["loot_java"]; backup_once(p); t = read(p)
    chance = int(os.environ["HOLO_CHANCE"])
    if "HOLOCRON_DROP_CHANCE" not in t:
        anchor = '    public static final int COL_LOOT_MULTIPLIER_ON = 1;\r\n'
        block = (anchor +
                 '    // --- Holocron drop tuning (Force-sensitive collection holocrons) ---\r\n'
                 '    // Independent holocron-only roll; does not touch other collection loot.\r\n'
                 f'    public static final int HOLOCRON_DROP_CHANCE = {chance};   // percent per kill; 0 = off\r\n'
                 '    public static final String COL_JEDI_HOLOCRON = "col_jedi_holocron";\r\n'
                 '    public static final String COL_SITH_HOLOCRON = "col_sith_holocron";\r\n')
        t = t.replace(anchor, block, 1)

        call_old = ('        hasLoot |= addCollectionLoot(target);\r\n'
                    '        hasLoot |= addRareLoot(target);\r\n')
        call_new = ('        hasLoot |= addCollectionLoot(target);\r\n'
                    '        hasLoot |= addHolocronLoot(target);\r\n'
                    '        hasLoot |= addRareLoot(target);\r\n')
        t = t.replace(call_old, call_new, 1)

        fn_anchor = ('    public static boolean addCollectionLoot(obj_id target) throws InterruptedException\r\n'
                     '    {\r\n'
                     '        return addCollectionLoot(target, false, null);\r\n'
                     '    }\r\n')
        fn = (
'    public static boolean addHolocronLoot(obj_id target) throws InterruptedException\r\n'
'    {\r\n'
'        if (HOLOCRON_DROP_CHANCE <= 0) { return false; }\r\n'
'        String creatureName = ai_lib.getCreatureName(target);\r\n'
'        dictionary creatureRow = dataTableGetRow(CREATURES_TABLE, creatureName);\r\n'
'        if (creatureRow == null || creatureRow.isEmpty()) { return false; }\r\n'
'        String myCollectionLoot = creatureRow.getString("collectionLoot");\r\n'
'        if (myCollectionLoot == null || myCollectionLoot.equals("no_loot")) { return false; }\r\n'
'        Vector holoColumns = new Vector();\r\n'
'        holoColumns.setSize(0);\r\n'
'        String[] columns = split(myCollectionLoot, \',\');\r\n'
'        for (int i = 0; i < columns.length; i++)\r\n'
'        {\r\n'
'            if (columns[i].equals(COL_JEDI_HOLOCRON) || columns[i].equals(COL_SITH_HOLOCRON))\r\n'
'            {\r\n'
'                holoColumns = utils.addElement(holoColumns, columns[i]);\r\n'
'            }\r\n'
'        }\r\n'
'        if (holoColumns.size() < 1) { return false; }\r\n'
'        if (rand(1, 100) > HOLOCRON_DROP_CHANCE) { return false; }\r\n'
'        String holoColumn = (String)holoColumns.get(rand(0, holoColumns.size() - 1));\r\n'
'        String[] lootArray = dataTableGetStringColumnNoDefaults(COLLECTIONS_LOOT_TABLE, holoColumn);\r\n'
'        if (lootArray == null || lootArray.length < 1) { return false; }\r\n'
'        String lootToGrant = lootArray[rand(0, lootArray.length - 1)];\r\n'
'        obj_id mobInv = utils.getInventoryContainer(target);\r\n'
'        obj_id lootItem = static_item.createNewItemFunction(lootToGrant, mobInv);\r\n'
'        if (isIdValid(lootItem) && exists(lootItem))\r\n'
'        {\r\n'
'            CustomerServiceLog("CollectionLootChannel: ", "HolocronDrop: " + creatureName + "(" + target + ") dropped: " + lootToGrant);\r\n'
'            return true;\r\n'
'        }\r\n'
'        return false;\r\n'
'    }\r\n')
        t = t.replace(fn_anchor, fn + fn_anchor, 1)
        write_crlf(p, t)
        changed.append(f"holocron drop mod added (chance={chance}%)")
    else:
        # already added: just resync the chance constant
        import re
        t2 = re.sub(r'public static final int HOLOCRON_DROP_CHANCE = \d+;',
                    f'public static final int HOLOCRON_DROP_CHANCE = {chance};', t)
        if t2 != t:
            write_crlf(p, t2); changed.append(f"holocron chance resynced to {chance}%")

print("\n=== Talon mods applied ===")
for c in changed: print("  -", c)
if not changed: print("  (nothing to do — already applied)")
print("\nBackups written as <file>.talonbak  (revert with: --revert)")
print("REMEMBER: recompile .tab->.iff (DataTableTool) + rebuild scripts, then restart.")
PYEOF
