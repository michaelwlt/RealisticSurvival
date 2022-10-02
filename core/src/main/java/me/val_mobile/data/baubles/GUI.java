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
package me.val_mobile.data.baubles;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;

public abstract class GUI {

    private final Inventory inv;
    private final Collection<ItemStack> disallowedItems = new ArrayList<>();

    public GUI(Player player, int size, String name) {
        inv = Bukkit.createInventory(player, size, name);
    }

    public Collection<ItemStack> getDisallowedItems() {
        return disallowedItems;
    }

    public Inventory getInventory() {
        return inv;
    }

    public void shouldCancel(InventoryClickEvent event) {
    }
}
