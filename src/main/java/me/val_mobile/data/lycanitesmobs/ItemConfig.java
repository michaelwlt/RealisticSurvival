/*
    Copyright (C) 2022  Val_Mobile

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package me.val_mobile.data.lycanitesmobs;

import me.val_mobile.data.RSVConfig;
import me.val_mobile.realisticsurvival.RealisticSurvivalPlugin;

public class ItemConfig extends RSVConfig {

    public static final String PATH = "resources/lycanitesmobs/items.yml";
    public static final boolean REPLACE = true;

    public ItemConfig(RealisticSurvivalPlugin plugin) {
        super(plugin, PATH, REPLACE);
    }
}
