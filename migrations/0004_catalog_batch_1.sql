-- Catalogue batch 1: changes to EXISTING inventory_item / store_item rows (MariaDB; earlier files here are SQLite-era history).
--
-- Companion to seed/inventory_catalog.sql, which holds the NEW items (apply it too: INSERT IGNORE, re-runnable).
-- Every statement is guarded on the current value, so the file can be re-run or applied to a database that already has some of it.
--
--   1. unit corrections, 2. renames to the narrowest meaning (the id keeps its history),
--   3. German category values -> English, receipt-added marker dropped (as the seed already had it),
--   4. store_item rows that belong to a NEW split item are re-pointed (guarded on the current item id).
--
-- Snapshot first (see CLAUDE.md), then apply with:
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < migrations/0004_catalog_batch_1.sql
-- Not applied to the live database by the PR that adds it.

UPDATE inventory_item SET inventory_item_unit = 'ml' WHERE inventory_item_id = 356 AND inventory_item_unit = 'g';  -- Vollmilch: liquid; recipes use ml 343x vs g 79x
UPDATE inventory_item SET inventory_item_unit = 'ml' WHERE inventory_item_id = 361 AND inventory_item_unit = 'g';  -- Hafermilch: liquid
UPDATE inventory_item SET inventory_item_unit = 'ml' WHERE inventory_item_id = 397 AND inventory_item_unit = 'g';  -- Trinkjoghurt: liquid
UPDATE inventory_item SET inventory_item_unit = 'Stück' WHERE inventory_item_id = 569 AND inventory_item_unit = 'g';  -- Brötchen: sold per piece
UPDATE inventory_item SET inventory_item_unit = 'g' WHERE inventory_item_id = 1677 AND inventory_item_unit = 'Stück';  -- Tiefkühlpizza: packs state grams
UPDATE inventory_item SET inventory_item_unit = 'g' WHERE inventory_item_id = 1692 AND inventory_item_unit = 'Packung';  -- Filterkaffee: packs state grams
UPDATE inventory_item SET inventory_item_unit = 'ml' WHERE inventory_item_id = 1687 AND inventory_item_unit = 'Packung';  -- Eiscreme (Packung): packs state ml
UPDATE inventory_item SET inventory_item_unit = 'WL' WHERE inventory_item_id = 1669 AND inventory_item_unit = 'Packung';  -- Vollwaschmittel: per wash load
UPDATE inventory_item SET inventory_item_unit = 'ml' WHERE inventory_item_id = 1691 AND inventory_item_unit = 'Flasche';  -- Cola-Mix (Spezi): all beverages in ml
UPDATE inventory_item SET inventory_item_unit = 'Stück' WHERE inventory_item_id = 1477 AND inventory_item_unit = 'g';  -- Müsliriegel: 6 of 10 current packs state pieces
UPDATE inventory_item SET inventory_item_name_english = 'Ground filter coffee', inventory_item_name_deutsch = 'Filterkaffee (gemahlen)' WHERE inventory_item_id = 1692 AND inventory_item_name_deutsch = 'Kaffee';  -- narrowest meaning keeps the id
UPDATE inventory_item SET inventory_item_name_english = 'Heavy-duty laundry detergent', inventory_item_name_deutsch = 'Vollwaschmittel' WHERE inventory_item_id = 1669 AND inventory_item_name_deutsch = 'Waschmittel';  -- narrowest meaning keeps the id
UPDATE inventory_item SET inventory_item_name_english = 'Ice cream (tub)', inventory_item_name_deutsch = 'Eiscreme (Packung)' WHERE inventory_item_id = 1687 AND inventory_item_name_deutsch = 'Eiscreme';  -- narrowest meaning keeps the id
UPDATE inventory_item SET inventory_item_name_english = 'Cola-mix (Spezi)', inventory_item_name_deutsch = 'Cola-Mix (Spezi)' WHERE inventory_item_id = 1691 AND inventory_item_name_deutsch = 'Spezi';  -- narrowest meaning keeps the id
UPDATE inventory_item SET inventory_item_name_english = 'drinking yogurt (sweet)', inventory_item_name_deutsch = 'Trinkjoghurt' WHERE inventory_item_id = 397 AND inventory_item_name_deutsch = 'trinkjoghurt';  -- narrowest meaning keeps the id
UPDATE inventory_item SET inventory_item_category = 'beverages', inventory_item_subcategory = 'milk & yogurt drinks' WHERE inventory_item_id = 397 AND inventory_item_category = 'dairy';  -- yogurt drinks are drinks
UPDATE inventory_item SET inventory_item_category = 'household', inventory_item_subcategory = NULL WHERE inventory_item_id = 1667 AND inventory_item_category = 'Haushalt';
UPDATE inventory_item SET inventory_item_category = 'household', inventory_item_subcategory = NULL WHERE inventory_item_id = 1668 AND inventory_item_category = 'Haushalt';
UPDATE inventory_item SET inventory_item_category = 'household', inventory_item_subcategory = NULL WHERE inventory_item_id = 1669 AND inventory_item_category = 'Haushalt';
UPDATE inventory_item SET inventory_item_category = 'household', inventory_item_subcategory = NULL WHERE inventory_item_id = 1670 AND inventory_item_category = 'Haushalt';
UPDATE inventory_item SET inventory_item_category = 'sweets & snacks', inventory_item_subcategory = NULL WHERE inventory_item_id = 1671 AND inventory_item_category = 'Süßwaren';
UPDATE inventory_item SET inventory_item_category = 'sweets & snacks', inventory_item_subcategory = NULL WHERE inventory_item_id = 1672 AND inventory_item_category = 'Süßwaren';
UPDATE inventory_item SET inventory_item_category = 'household', inventory_item_subcategory = NULL WHERE inventory_item_id = 1673 AND inventory_item_category = 'Haushalt';
UPDATE inventory_item SET inventory_item_category = 'spices & seasonings', inventory_item_subcategory = NULL WHERE inventory_item_id = 1674 AND inventory_item_category = 'Gewürze';
UPDATE inventory_item SET inventory_item_category = 'frozen', inventory_item_subcategory = NULL WHERE inventory_item_id = 1675 AND inventory_item_category = 'Tiefkühl';
UPDATE inventory_item SET inventory_item_category = 'dairy', inventory_item_subcategory = NULL WHERE inventory_item_id = 1676 AND inventory_item_category = 'Molkerei & Eier';
UPDATE inventory_item SET inventory_item_category = 'frozen', inventory_item_subcategory = NULL WHERE inventory_item_id = 1677 AND inventory_item_category = 'Tiefkühl';
UPDATE inventory_item SET inventory_item_category = 'vegetable', inventory_item_subcategory = NULL WHERE inventory_item_id = 1678 AND inventory_item_category = 'Gemüse';
UPDATE inventory_item SET inventory_item_category = 'spices & seasonings', inventory_item_subcategory = NULL WHERE inventory_item_id = 1679 AND inventory_item_category = 'Gewürze';
UPDATE inventory_item SET inventory_item_category = 'sweets & snacks', inventory_item_subcategory = NULL WHERE inventory_item_id = 1680 AND inventory_item_category = 'Süßwaren';
UPDATE inventory_item SET inventory_item_category = 'household', inventory_item_subcategory = NULL WHERE inventory_item_id = 1681 AND inventory_item_category = 'Haushalt';
UPDATE inventory_item SET inventory_item_category = 'clothing', inventory_item_subcategory = NULL WHERE inventory_item_id = 1682 AND inventory_item_category = 'Kleidung';
UPDATE inventory_item SET inventory_item_category = 'clothing', inventory_item_subcategory = NULL WHERE inventory_item_id = 1683 AND inventory_item_category = 'Kleidung';
UPDATE inventory_item SET inventory_item_category = 'beverages', inventory_item_subcategory = NULL WHERE inventory_item_id = 1684 AND inventory_item_category = 'Getränke';
UPDATE inventory_item SET inventory_item_category = 'vegetable', inventory_item_subcategory = NULL WHERE inventory_item_id = 1685 AND inventory_item_category = 'Gemüse';
UPDATE inventory_item SET inventory_item_category = 'vegetable', inventory_item_subcategory = NULL WHERE inventory_item_id = 1686 AND inventory_item_category = 'Gemüse';
UPDATE inventory_item SET inventory_item_category = 'frozen', inventory_item_subcategory = NULL WHERE inventory_item_id = 1687 AND inventory_item_category = 'Tiefkühl';
UPDATE inventory_item SET inventory_item_category = 'baking supplies', inventory_item_subcategory = NULL WHERE inventory_item_id = 1688 AND inventory_item_category = 'Backen';
UPDATE inventory_item SET inventory_item_category = 'baking supplies', inventory_item_subcategory = 'spreads' WHERE inventory_item_id = 1689 AND inventory_item_category = 'Aufstrich';
UPDATE inventory_item SET inventory_item_category = 'sweets & snacks', inventory_item_subcategory = NULL WHERE inventory_item_id = 1690 AND inventory_item_category = 'Süßwaren';
UPDATE inventory_item SET inventory_item_category = 'beverages', inventory_item_subcategory = NULL WHERE inventory_item_id = 1691 AND inventory_item_category = 'Getränke';
UPDATE inventory_item SET inventory_item_category = 'beverages', inventory_item_subcategory = NULL WHERE inventory_item_id = 1692 AND inventory_item_category = 'Getränke';
UPDATE inventory_item SET inventory_item_category = 'household', inventory_item_subcategory = NULL WHERE inventory_item_id = 1693 AND inventory_item_category = 'Haushalt';
UPDATE inventory_item SET inventory_item_category = 'chilled', inventory_item_subcategory = NULL WHERE inventory_item_id = 1694 AND inventory_item_category = 'Kühlregal';
UPDATE store_item SET store_item_item_id = 1677 WHERE store_item_id = 875 AND store_item_item_id = 200;  -- DR. OETKER Die Ofenfrische Schinken-Ananas [415-g-Packung]: 200 -> Tiefkühlpizza
UPDATE store_item SET store_item_item_id = 1797 WHERE store_item_id = 895 AND store_item_item_id = 58;  -- HENGSTENBERG Knax Gewürzgurken [720-ml-Glas]: 58 -> Gewürzgurken (Glas)
UPDATE store_item SET store_item_item_id = 1677 WHERE store_item_id = 925 AND store_item_item_id = 302;  -- Steinofenpizza Salami XXL [4x350-g-Packung]: 302 -> Tiefkühlpizza
UPDATE store_item SET store_item_item_id = 1703 WHERE store_item_id = 933 AND store_item_item_id = 148;  -- RED BULL Energydrink White Peach [0,25-L-Dose]: 148 -> Energydrink
UPDATE store_item SET store_item_item_id = 1813 WHERE store_item_id = 949 AND store_item_item_id = 31;  -- DOUCEUR Marzipankartoffeln [125 g]: 31 -> Marzipankartoffeln
UPDATE store_item SET store_item_item_id = 824 WHERE store_item_id = 987 AND store_item_item_id = 50;  -- Oro Di Parma Tomaten [425ml]: 50 -> Passierte Tomaten
UPDATE store_item SET store_item_item_id = 1727 WHERE store_item_id = 990 AND store_item_item_id = 1680;  -- FERRERO Kinder Bueno [129-g-Packung]: 1680 -> Waffelschnitte
UPDATE store_item SET store_item_item_id = 1720 WHERE store_item_id = 994 AND store_item_item_id = 1692;  -- STARBUCKS Frappuccino Creamy Coffee [250-ml-Flasche]: 1692 -> Kaffeegetränk (Fertig)
UPDATE store_item SET store_item_item_id = 1727 WHERE store_item_id = 1003 AND store_item_item_id = 1680;  -- Ferrero Kinder Bueno [6 Stück]: 1680 -> Waffelschnitte
UPDATE store_item SET store_item_item_id = 1720 WHERE store_item_id = 1005 AND store_item_item_id = 1692;  -- Starbucks Coffee [220ml]: 1692 -> Kaffeegetränk (Fertig)
UPDATE store_item SET store_item_item_id = 1789 WHERE store_item_id = 1018 AND store_item_item_id = 374;  -- MEGGLE Kräuterbutter [5x20-g-Packung]: 374 -> Kräuterbutter
UPDATE store_item SET store_item_item_id = 1700 WHERE store_item_id = 1024 AND store_item_item_id = 186;  -- RHEINFELS Lemon [0,75-L-Flasche]: 186 -> Zitronenlimonade
UPDATE store_item SET store_item_item_id = 1744 WHERE store_item_id = 1027 AND store_item_item_id = 186;  -- PAULANER Weißbier-Zitrone 0,0 % [0,5-L-Dose]: 186 -> Biermix alkoholfrei
UPDATE store_item SET store_item_item_id = 1788 WHERE store_item_id = 1034 AND store_item_item_id = 374;  -- ARLA Kærgården Butter [250-g-Packung]: 374 -> Butter-Mischstreichfett
UPDATE store_item SET store_item_item_id = 822 WHERE store_item_id = 1039 AND store_item_item_id = 50;  -- CUCINA San-Marzano-Tomaten [420-ml-Dose]: 50 -> Geschälte Tomaten (Dose)
UPDATE store_item SET store_item_item_id = 377 WHERE store_item_id = 1042 AND store_item_item_id = 374;  -- Rama mit Butter XXL [400-g-Packung]: 374 -> Margarine
UPDATE store_item SET store_item_item_id = 1797 WHERE store_item_id = 1056 AND store_item_item_id = 58;  -- ALL SEASONS Gurken [1.062-ml-Glas]: 58 -> Gewürzgurken (Glas)
UPDATE store_item SET store_item_item_id = 1797 WHERE store_item_id = 1059 AND store_item_item_id = 58;  -- TRADER JOE’S Sandwich-Gurken [580-ml-Glas]: 58 -> Gewürzgurken (Glas)
UPDATE store_item SET store_item_item_id = 1743 WHERE store_item_id = 1060 AND store_item_item_id = 188;  -- SCHÖFFERHOFER Weizen-Mix Grapefruit [0,5-L-Dose]: 188 -> Biermix
UPDATE store_item SET store_item_item_id = 1720 WHERE store_item_id = 1062 AND store_item_item_id = 1692;  -- STARBUCKS Eiskaffee [220-ml-Becher]: 1692 -> Kaffeegetränk (Fertig)
UPDATE store_item SET store_item_item_id = 1815 WHERE store_item_id = 1068 AND store_item_item_id = 598;  -- TRADER JOE’S Chicken-Donuts [900-g-Packung]: 598 -> Chicken Donuts
UPDATE store_item SET store_item_item_id = 1709 WHERE store_item_id = 1080 AND store_item_item_id = 154;  -- JIM BEAM Bourbon Whiskey / Black Cherry [0,7-L-Flasche]: 154 -> Whisky
UPDATE store_item SET store_item_item_id = 1699 WHERE store_item_id = 1113 AND store_item_item_id = 181;  -- COMET Orange [1,5 l]: 181 -> Orangenlimonade
UPDATE store_item SET store_item_item_id = 1700 WHERE store_item_id = 1114 AND store_item_item_id = 186;  -- COMET Zitrone [1,5 l]: 186 -> Zitronenlimonade
UPDATE store_item SET store_item_item_id = 1677 WHERE store_item_id = 1118 AND store_item_item_id = 302;  -- WAGNER Piccolinis Salami [270 g]: 302 -> Tiefkühlpizza
UPDATE store_item SET store_item_item_id = 377 WHERE store_item_id = 1134 AND store_item_item_id = 374;  -- RAMA Mit Butter [225 g]: 374 -> Margarine
UPDATE store_item SET store_item_item_id = 1792 WHERE store_item_id = 1144 AND store_item_item_id = 569;  -- BÄCKERKRÖNUNG Franzbrötchen [2 Stück]: 569 -> Franzbrötchen
UPDATE store_item SET store_item_item_id = 1718 WHERE store_item_id = 1153 AND store_item_item_id = 1692;  -- JACOBS Instantkaffee [200 g]: 1692 -> Instantkaffee
UPDATE store_item SET store_item_item_id = 1677 WHERE store_item_id = 1156 AND store_item_item_id = 302;  -- ORIGINAL WAGNER Steinofenpizza Salami [2 x 320 g]: 302 -> Tiefkühlpizza
UPDATE store_item SET store_item_item_id = 1799 WHERE store_item_id = 1166 AND store_item_item_id = 1687;  -- Mars Ice Cream [5 x 49,5 - 50,3ml]: 1687 -> Eisriegel
UPDATE store_item SET store_item_item_id = 1789 WHERE store_item_id = 1167 AND store_item_item_id = 374;  -- Meggle Kräuterbutter [5 Stück]: 374 -> Kräuterbutter
UPDATE store_item SET store_item_item_id = 824 WHERE store_item_id = 1173 AND store_item_item_id = 50;  -- Oro Di Parma Tomaten [425ml]: 50 -> Passierte Tomaten
UPDATE store_item SET store_item_item_id = 1799 WHERE store_item_id = 1199 AND store_item_item_id = 1687;  -- Mars Ice Cream [5 x 49,5 - 50,3ml]: 1687 -> Eisriegel
