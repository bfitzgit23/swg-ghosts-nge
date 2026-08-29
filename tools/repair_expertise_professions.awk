BEGIN { FS = OFS = "\t" }

# First input is the stock/Talon backup.  It is used only as a map of each
# expertise node's owning tree and profession; current faction gates and the
# expanded hybrid ranks remain untouched.
FILENAME ~ /\.talonbak$/ {
    if (FNR > 2) {
        sub(/\r$/, "", $8)
        old_tree[$1] = $2
        old_tier[$1] = $3
        old_grid[$1] = $4
        old_prof[$1] = $8
    }
    next
}

FNR <= 2 { print; next }

# Non-expertise skills were accidentally appended to this table.  They are
# still ordinary skills; they simply do not belong in the expertise window.
$1 !~ /^expertise_/ { next }

{
    if ($1 in old_tree) {
        $2 = old_tree[$1]
        $3 = old_tier[$1]
        $4 = old_grid[$1]
        $8 = old_prof[$1]
    } else {
        # Added hybrid ranks inherit ownership from their profession prefix.
        if      ($1 ~ /^expertise_bh_/)     $8 = "bounty_hunter"
        else if ($1 ~ /^expertise_fs_/)     $8 = "force_sensitive"
        else if ($1 ~ /^expertise_sm_/)     $8 = "smuggler"
        else if ($1 ~ /^expertise_of_/)     $8 = "officer"
        else if ($1 ~ /^expertise_me_/)     $8 = "medic"
        else if ($1 ~ /^expertise_co_/)     $8 = "commando"
        else if ($1 ~ /^expertise_sp_/)     $8 = "spy"
        else if ($1 ~ /^expertise_en_/)     $8 = "entertainer"
        else if ($1 ~ /^expertise_trader_/) $8 = "trader"
        else if ($1 ~ /^expertise_bm_/)     $8 = "all"

        # Locate a lower rank of the same added node and inherit its normal
        # tree position.  Rank itself remains the expanded hybrid value.
        stem = $1
        sub(/_[0-9]+$/, "", stem)
        base = ""
        for (r = 1; r <= 4; ++r) {
            candidate = stem "_" r
            if (candidate in old_tree) { base = candidate; break }
        }
        if (base != "") {
            $2 = old_tree[base]
            $3 = old_tier[base]
            $4 = old_grid[base]
        } else if ($1 ~ /^expertise_fs_general_/) {
            $2 = 4
        }
    }
    print
}
