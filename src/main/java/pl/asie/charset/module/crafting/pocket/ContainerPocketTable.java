/*
 * Copyright (c) 2015, 2016, 2017, 2018, 2019, 2020 Adrian Siekierka
 *
 * This file is part of Charset.
 *
 * Charset is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Charset is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Charset.  If not, see <http://www.gnu.org/licenses/>.
 */

package pl.asie.charset.module.crafting.pocket;

import com.google.common.collect.ImmutableList;
import invtweaks.api.container.ContainerSection;
import invtweaks.api.container.ContainerSectionCallback;
import invtweaks.api.container.InventoryContainer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pl.asie.charset.lib.item.ISubItemProvider;
import pl.asie.charset.lib.material.FastRecipeLookup;
import pl.asie.charset.lib.inventory.ContainerBase;
import pl.asie.charset.lib.inventory.SlotBlocked;
import pl.asie.charset.lib.utils.ItemUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

//@InventoryContainer // TODO: InvTweaks
public class ContainerPocketTable extends ContainerBase {
    //InventoryPlayer Slots:
    //09 10 11 12 13 14 15 16 17
    //18 19 20 21 22 23 24 25 26
    //27 28 29 30 31 32 33 34 35
    //00 01 02 03 04 05 06 07 08
    private static final int slotCycleOrder[] = {
            15, 16, 17,
            26,
            35, 34, 33,
            24,
    };
    private static final int slotPlayerOrder[] = {
            15, 16, 17,
            24, 25, 26,
            33, 34, 35
    };

    private final EntityPlayer player;
    private final InventoryPlayer playerInv;
    private final int heldPos;
    private InventoryCrafting craftMatrix = new InventoryCrafting(this, 3, 3);
    private IInventory craftResult = new InventoryCraftResult();

    private ArrayList<Slot> nonCraftingInventorySlots = new ArrayList<Slot>();
    private ArrayList<Slot> craftingSlots = new ArrayList<Slot>();
    private ArrayList<Slot> mainInvSlots = new ArrayList<Slot>();
    private ArrayList<Slot> hotbarSlots = new ArrayList<Slot>();
    private RedirectedSlotCrafting craftResultSlot;

    private boolean isCrafting = false;
    private boolean dirty = false;

    protected ItemStack getHeld() {
        return playerInv.getStackInSlot(heldPos);
    }

    public List<Slot> getCraftingSlots() {
        return craftingSlots;
    }

    public List<Slot> getNonCraftingSlots() {
        return nonCraftingInventorySlots;
    }

    public ContainerPocketTable(EntityPlayer player) {
        super(player.inventory);
        this.player = player;
        this.playerInv = player.inventory;
        heldPos = this.playerInv.currentItem;
        craftResultSlot = (RedirectedSlotCrafting) addSlotToContainer(new RedirectedSlotCrafting(player, craftMatrix, craftResult, 207, 28));
        bindPlayerInventory(player.inventory, 8, 8);
        updateCraft();
    }

    void movePlayerToMatrix() {
        for (int i = 0; i < 9; i++) {
            craftMatrix.setInventorySlotContents(i, playerInv.getStackInSlot(slotPlayerOrder[i]));
            playerInv.setInventorySlotContents(slotPlayerOrder[i], ItemStack.EMPTY);
        }
    }

    void copyMatrixToPlayer() {
        for (int i = 0; i < 9; i++) {
            playerInv.setInventorySlotContents(slotPlayerOrder[i], craftMatrix.getStackInSlot(i).copy());
        }
    }

    boolean isWorking = false;

    public void updateCraft() {
        if (isWorking) {
            dirty = true;
            return;
        }
        movePlayerToMatrix();
        ItemStack result = ItemStack.EMPTY;
        IRecipe match = FastRecipeLookup.findMatchingRecipe(craftMatrix, player.getEntityWorld());
        if (match != null) {
            result = match.getCraftingResult(craftMatrix);
        }
        craftResult.setInventorySlotContents(0, result);
        copyMatrixToPlayer();
        dirty = false;
        detectAndSendChanges();
    }

    @Override
    protected Slot addSlotToContainer(Slot slot) {
        if (slot.inventory instanceof InventoryPlayer) {
            if (slot.isHere(slot.inventory, heldPos)) {
                slot = new SlotBlocked(slot.inventory, slot.getSlotIndex(), slot.xPos, slot.yPos);
            }

            if (slot.getSlotIndex() >= 9 && (slot.getSlotIndex() % 9) >= 6) {
                slot = new Slot(slot.inventory, slot.getSlotIndex(), slot.xPos, slot.yPos) {
                    @Override
                    public void onSlotChanged() {
                        super.onSlotChanged();
                        updateCraft();
                    }
                };
                craftingSlots.add(slot);
            } else {
                nonCraftingInventorySlots.add(slot);
                if (slot.getSlotIndex() < 9) {
                    hotbarSlots.add(slot);
                } else {
                    mainInvSlots.add(slot);
                }

                if (slot.getSlotIndex() < 9 && slot.getHasStack() && slot.getStack().getItem() == CharsetCraftingPocket.pocketTable) {
                    slot = new Slot(slot.inventory, slot.getSlotIndex(), slot.xPos, slot.yPos) {
                        @Override
                        public boolean canTakeStack(EntityPlayer playerIn) {
                            return false;
                        }
                    };
                }
            }
        }

        return super.addSlotToContainer(slot);
    }

    class RedirectedSlotCrafting extends SlotCrafting {
        public RedirectedSlotCrafting(EntityPlayer player, InventoryCrafting craftMatrix, IInventory craftResult, int posX, int posY) {
            super(player, craftMatrix, craftResult, 0, posX, posY);
        }

        @Override
        public void onCrafting(ItemStack stack) {
            if (!isCrafting) {
                isCrafting = true;
                movePlayerToMatrix();
                super.onCrafting(stack);
                copyMatrixToPlayer();
                isCrafting = false;
                detectAndSendChanges();
            } else {
                super.onCrafting(stack);
            }
        }

        @Override
        public ItemStack onTake(EntityPlayer thePlayer, ItemStack stack) {
            isCrafting = true;
            movePlayerToMatrix();
            ItemStack stackOut = super.onTake(thePlayer, stack);
            copyMatrixToPlayer();
            updateCraft();
            isCrafting = false;
            detectAndSendChanges();
            return stackOut;
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void setAll(List<ItemStack> p_190896_1_) {
        isWorking = true;
        super.setAll(p_190896_1_);
        isWorking = false;
    }

    @Override
    public void onCraftMatrixChanged(IInventory inv) {
        super.onCraftMatrixChanged(inv);
    }

    @Override
    public void detectAndSendChanges() {
        if (!isCrafting) {
            super.detectAndSendChanges();
        }
    }

    public void onAction(int action, int arg) {
        switch (action) {
            case PacketPTAction.BALANCE:
                craftBalance();
                break;
            case PacketPTAction.FILL:
                craftFill(arg);
                break;
            case PacketPTAction.CLEAR:
                craftClear();
                break;
            case PacketPTAction.SWIRL:
                craftSwirl();
                break;
            default:
                return;
        }
        updateCraft();
    }

    @Override
    public boolean isOwnerPresent() {
        // TODO
        return true;
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int slotId) {
        Slot slot = inventorySlots.get(slotId);
        ItemStack slotStack = slot.getStack();
        if (slot.getHasStack()) {
            slotStack = slotStack.copy();
        }

        if (slot == craftResultSlot) {
            updateCraft();
            ItemStack res = slotStack;

            if (res.isEmpty()) {
                return ItemStack.EMPTY;
            }

            int count = getMaxCraftingAttempts(res);
            if (count <= 0) {
                return ItemStack.EMPTY;
            }

            for (; count > 0; count--) {
                ItemStack craftedStack = craftResultSlot.getStack().copy();
                tryTransferStackInSlot(player, craftResultSlot, nonCraftingInventorySlots);
                craftResultSlot.onTake(player, craftedStack);
                updateCraft();
                ItemStack newRes = craftResultSlot.getStack();
                if (newRes.isEmpty() || !ItemUtils.canMerge(res, newRes) || getMaxCraftingAttempts(newRes, newRes.getCount()) <= 0) {
                    break;
                }
            }

        } else if (nonCraftingInventorySlots.contains(slot)) {
            tryTransferStackInSlot(player, slot, craftingSlots);
        } else if (craftingSlots.contains(slot)) {
            tryTransferStackInSlot(player, slot, nonCraftingInventorySlots);
        }

        return slotStack;
    }

    int getMaxCraftingAttempts(ItemStack res) {
        return getMaxCraftingAttempts(res, res.getMaxStackSize());
    }

    int getMaxCraftingAttempts(ItemStack res, int maxSize) {
        boolean hasEmpty = false;
        int nonEmptyAmount = 0;
        for (Slot slot : nonCraftingInventorySlots) {
            ItemStack is = slot.getStack();
            if (is.isEmpty()) {
                hasEmpty = true;
            } else if (ItemUtils.canMerge(res, is)) {
                nonEmptyAmount += is.getMaxStackSize() - is.getCount();
                if (nonEmptyAmount >= maxSize) {
                    break;
                }
            }
        }
        if (nonEmptyAmount > maxSize) {
            nonEmptyAmount = maxSize;
        }
        int maxCraftingAttempts = nonEmptyAmount / res.getCount();
        if (maxCraftingAttempts == 0 && hasEmpty) {
            return maxSize / res.getCount();
        } else {
            return maxCraftingAttempts;
        }
    }

    void craftClear() {
        for (Slot slot : craftingSlots) {
            tryTransferStackInSlot(player, slot, mainInvSlots);
            if (slot.getHasStack()) {
                tryTransferStackInSlot(player, slot, hotbarSlots);
            }
        }
    }

    void craftSwirl() {
        boolean anyAction = false;
        for (int n = 0; n < 8; n++) {
            //1. find a stack with > 1 item in it
            //2. find an empty slot
            //3. move 1 item from former into latter
            boolean any = false;
            for (int slotIndexIndex = 0; slotIndexIndex < slotCycleOrder.length; slotIndexIndex++) {
                ItemStack is = playerInv.getStackInSlot(slotCycleOrder[slotIndexIndex]);
                if (is.isEmpty() || is.getCount() <= 1) {
                    continue;
                }
                for (int probidex = slotIndexIndex; probidex < slotIndexIndex + slotCycleOrder.length; probidex++) {
                    ItemStack empty = playerInv.getStackInSlot(slotCycleOrder[probidex % slotCycleOrder.length]);
                    if (!empty.isEmpty()) {
                        continue;
                    }
                    playerInv.setInventorySlotContents(slotCycleOrder[probidex % slotCycleOrder.length], is.splitStack(1));
                    any = true;
                    break;
                }
            }
            if (!any) {
                break;
            } else {
                anyAction = true;
            }
        }
        if (!anyAction) {
            //Did nothing. Shift the item around.
            ItemStack swapeh = playerInv.getStackInSlot(slotCycleOrder[slotCycleOrder.length - 1]);
            for (int i = 0; i < slotCycleOrder.length; i++) {
                ItemStack here = playerInv.getStackInSlot(slotCycleOrder[i]);
                playerInv.setInventorySlotContents(slotCycleOrder[i], swapeh);
                swapeh = here;
            }
            playerInv.setInventorySlotContents(slotCycleOrder[0], swapeh);
        }
    }
    
    void craftBalance() {
        class Accumulator {
            ItemStack toMatch;
            int stackCount = 0;
            ArrayList<Integer> matchingSlots = new ArrayList<Integer>(9);

            public Accumulator(ItemStack toMatch, int slot) {
                this.toMatch = toMatch.copy();
                stackCount = toMatch.getCount();
                this.toMatch.setCount(1);
                toMatch.setCount(0);
                matchingSlots.add(slot);
            }

            boolean add(ItemStack ta, int slot) {
                if (ItemUtils.canMerge(toMatch, ta)) {
                    stackCount += ta.getCount();
                    ta.setCount(0);
                    matchingSlots.add(slot);
                    return true;
                }
                return false;
            }
        }
        ArrayList<Accumulator> list = new ArrayList<Accumulator>(9);
        for (Slot s : craftingSlots) {
            int slot = s.getSlotIndex();
            ItemStack here = playerInv.getStackInSlot(slot);
            if (here.isEmpty()) {
                continue;
            }
            boolean found = false;
            for (Accumulator acc : list) {
                if (acc.add(here, slot)) {
                    found = true;
                }
            }
            if (!found) {
                list.add(new Accumulator(here, slot));
            }
        }

        for (Accumulator acc : list) {
            int delta = acc.stackCount / acc.matchingSlots.size();
            // this should be incapable of being 0
            delta = Math.min(delta, 1); // ...we'll make sure anyways.
            for (int slot : acc.matchingSlots) {
                if (acc.stackCount <= 0) {
                    break;
                }
                playerInv.getStackInSlot(slot).setCount(delta);
                acc.stackCount -= delta;
            }
            // we now may have a few left over, which we'll distribute
            while (acc.stackCount > 0) {
                for (int slot : acc.matchingSlots) {
                    if (acc.stackCount <= 0) {
                        break;
                    }
                    playerInv.getStackInSlot(slot).grow(1);
                    acc.stackCount--;
                }
            }
        }
    }

    void craftFill(int slot) {
        final ItemStack toMove = playerInv.getStackInSlot(slot);
        for (Slot matrixSlot : craftingSlots) {
            if (toMove.isEmpty()) {
                break;
            }
            if (matrixSlot.getStack().isEmpty()) {
                matrixSlot.putStack(toMove.splitStack(1));
            }
        }
        playerInv.setInventorySlotContents(slot, toMove.isEmpty() ? ItemStack.EMPTY : toMove);
    }

    // Inventory Tweaks Compat
    // TODO: https://github.com/Inventory-Tweaks/inventory-tweaks/issues/548
    /* @ContainerSectionCallback
    @Optional.Method(modid = "inventorytweaks")
    protected Map<ContainerSection, List<Slot>> invTweaks_getContainerSections() {
        Map<ContainerSection, List<Slot>> map = new EnumMap<>(ContainerSection.class);
        map.put(ContainerSection.INVENTORY, SLOTS_PLAYER);
        map.put(ContainerSection.CRAFTING_IN, craftingSlots);
        map.put(ContainerSection.CRAFTING_OUT, ImmutableList.of(craftResultSlot));
        return map;
    } */
}
