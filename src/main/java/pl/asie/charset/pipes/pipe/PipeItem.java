/*
 * Copyright (c) 2015-2016 Adrian Siekierka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pl.asie.charset.pipes.pipe;

import com.google.common.collect.Lists;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.Vec3d;
import pl.asie.charset.ModCharset;
import pl.asie.charset.api.lib.IItemInsertionHandler;
import pl.asie.charset.api.pipes.IShifter;
import pl.asie.charset.lib.capability.Capabilities;
import pl.asie.charset.lib.capability.CapabilityHelper;
import pl.asie.charset.lib.utils.ItemUtils;
import pl.asie.charset.lib.utils.SpaceUtils;
import pl.asie.charset.pipes.CharsetPipes;
import pl.asie.charset.pipes.PipeUtils;

import javax.annotation.Nullable;
import java.util.*;

public class PipeItem {
	public static final int MAX_PROGRESS = 128;
	public static final int TICKS_PER_BLOCK = 16;
	public static final int CENTER_PROGRESS = MAX_PROGRESS / 2;
	public static final int SPEED = MAX_PROGRESS / TICKS_PER_BLOCK;
	private static short nextId;

	public final short id;
	byte blocksSinceSync;

	protected EnumFacing input, output;
	protected EnumFacing renderDirection;
	protected boolean reachedCenter;
	protected ItemStack stack = ItemStack.EMPTY;
	protected int progress;

	protected EnumFacing transformedFacing;
	protected Object transformedModel;

	private long lastRecalculationTime = -1;

	private int activeShifterDistance;
	private TilePipe owner;
	private boolean stuck;

	protected void updateRenderDirection() {
		if (renderDirection == null || (getDirection() != null && getDirection().getAxis() != EnumFacing.Axis.Y)) {
			renderDirection = getDirection();
		}
	}

	public EnumFacing getRenderDirection() {
		return renderDirection;
	}

	public PipeItem(TilePipe owner, ItemStack stack, EnumFacing side) {
		this.id = nextId++;
		this.owner = owner;
		this.stack = stack;
		initializeFromEntrySide(side);
	}

	public PipeItem(TilePipe owner, NBTTagCompound nbt) {
		this.id = nextId++;
		this.owner = owner;
		readFromNBT(nbt);
	}

	protected PipeItem(TilePipe tile, short id) {
		this.owner = tile;
		this.id = id;
	}

	public boolean isStuck(EnumFacing dirOther) {
		if (!stuck)
			return false;

		if (dirOther == null)
			return true;

		if (progress <= CENTER_PROGRESS && input != null && dirOther.getAxis() == input.getAxis())
			return true;

		if (progress >= CENTER_PROGRESS && output != null && dirOther.getAxis() == output.getAxis())
			return true;

		return false;
	}

	public boolean isValid() {
		return !stack.isEmpty() && input != null;
	}

	private float getTranslatedCoord(int offset) {
		if (progress >= CENTER_PROGRESS) {
			return 0.5F + (float) offset * (progress - CENTER_PROGRESS) / MAX_PROGRESS;
		} else {
			switch (offset) {
				case -1:
					return 1.0F + (float) offset * progress / MAX_PROGRESS;
				case 0:
				default:
					return 0.5F;
				case 1:
					return (float) offset * progress / MAX_PROGRESS;
			}
		}
	}

	public float getX() {
		return getDirection() != null ? getTranslatedCoord(getDirection().getFrontOffsetX()) : 0.5F;
	}

	public float getY() {
		return getDirection() != null ? getTranslatedCoord(getDirection().getFrontOffsetY()) : 0.5F;
	}

	public float getZ() {
		return getDirection() != null ? getTranslatedCoord(getDirection().getFrontOffsetZ()) : 0.5F;
	}

	public ItemStack getStack() {
		return stack;
	}

	public @Nullable EnumFacing getDirection() {
		return reachedCenter ? output : (input != null ? input.getOpposite() : null);
	}

	private boolean isCentered() {
		return progress == CENTER_PROGRESS;
	}

	// This version takes priority into account (filtered shifters are
	// prioritized over unfiltered shifters at the same distance).
	private int getInternalShifterStrength(PipeLogic.Direction dir) {
		return getInternalShifterStrength(dir.dir, dir.shifter);
	}

	private int getInternalShifterStrength(EnumFacing dir, IShifter shifter) {
		if (shifter == null) {
			return 0;
		} else {
			return owner.getShifterStrength(dir) * 2 + (shifter.hasFilter() ? 0 : 1);
		}
	}

	private void updateStuckFlag() {
		if (progress <= CENTER_PROGRESS) {
			if (!isValidDirection(output)) {
				calculateOutputDirection();
			} else {
				long time = getOwner().getWorld().getTotalWorldTime();
				if (lastRecalculationTime == -1) {
					lastRecalculationTime = time - (getOwner().getPos().hashCode() % TICKS_PER_BLOCK);
				} else if ((lastRecalculationTime + (TICKS_PER_BLOCK)) <= time) {
					calculateOutputDirection();
					lastRecalculationTime = time - (getOwner().getPos().hashCode() % TICKS_PER_BLOCK);
				}
			}
		} else {
			if (!isValidDirection(output)) {
				output = null;
			}
		}

		if (output == null) {
			// Never stuck when UNKNOWN, because the item will drop anyway.
			stuck = false;
		} else {
			if (isCentered() && activeShifterDistance > 0
					&& owner.getShifterStrength(output.getOpposite()) == owner.getShifterStrength(output)
					&& isShifterPushing(owner.getNearestShifter(output.getOpposite()), output.getOpposite())) {
				// Handle the "equal-distance opposite shifters" scenario for stopping items.
				// This does not take filtering into account!
				stuck = true;
			} else {
				stuck = !canMoveDirection(output, false);
			}
		}
	}

	protected PacketItemUpdate getSyncPacket(boolean syncStack) {
		return new PacketItemUpdate(owner, this, syncStack);
	}

	protected void sendPacket(boolean syncStack) {
		if (!owner.getWorld().isRemote) {
			CharsetPipes.packet.sendToAllAround(getSyncPacket(syncStack), owner, CharsetPipes.PIPE_TESR_DISTANCE);
		}
	}

	public boolean move() {
		if (!reachedCenter) {
			boolean atCenter = (progress + SPEED) >= CENTER_PROGRESS;

			if (atCenter) {
				onReachedCenter();
			} else if (!stuck) {
				progress += SPEED;
			}
		} else {
			if (owner.getWorld().isRemote) {
				if (!stuck) {
					progress += SPEED;
				}

				if (progress >= MAX_PROGRESS) {
					onItemEnd();
					return false;
				}
			} else {
				EnumFacing oldOutput = output;
				boolean oldStuck = stuck;

				updateStuckFlag();

				if (!stuck) {
					progress += SPEED;
				}

				if (progress >= MAX_PROGRESS) {
					onItemEnd();
					return false;
				}

				if (oldStuck != stuck || oldOutput != output) {
					sendPacket(false);
				}
			}
		}

		return true;
	}

	private void onItemEnd() {
		TilePipe pipe = output != null ? PipeUtils.getPipe(owner.getWorld(), owner.getPos().offset(output), output.getOpposite()) : null;

		if (owner.getWorld().isRemote) {
			if (blocksSinceSync < 2) {
				if (passToPipe(pipe, output, false)) {
					blocksSinceSync++;
				} else {
					stack = ItemStack.EMPTY;
				}
			} else {
				stack = ItemStack.EMPTY;
			}
		} else {
			if (output != null) {
				if (passToPipe(pipe, output, false)) {
					// Pipe passing does not take into account stack size
					// subtraction, as it re-uses the same object instance.
					// Therefore, we need to quit here.
					return;
				} else if (pipe == null) {
					TileEntity tile = owner.getNeighbourTile(output);
					passToInjectable(tile, output, false);
				}
			}

			if (!stack.isEmpty()) {
				dropItem(true);
			}
		}
	}

	private boolean isValidDirection(EnumFacing dir) {
		if (dir == null || !owner.connects(dir)) {
			return false;
		}

		TileEntity tile = owner.getNeighbourTile(dir);

		if (tile != null) {
			return CapabilityHelper.get(Capabilities.ITEM_INSERTION_HANDLER, tile, dir.getOpposite()) != null;
		}

		return false;
	}

	private boolean canMoveDirection(EnumFacing dir, boolean isPickingDirection) {
		if (dir == null) {
			return activeShifterDistance == 0;
		}

		TilePipe pipe = PipeUtils.getPipe(owner.getWorld(), owner.getPos().offset(dir), dir.getOpposite());

		/* if (isPickingDirection) {
			// If we're picking the direction, only check for pipe *connection*,
			// so that clogging mechanics (pipes which can't take in items) work
			// as intended.
			if (tile instanceof TilePipe) {
				if (((TilePipe) tile).connects(dir.getOpposite())) {
					return true;
				}
			}
		} else { */
		if (passToPipe(pipe, dir, true)) {
			return true;
		}
		// }

		if (pipe == null) {
			TileEntity tile = owner.getNeighbourTile(dir);

			if (passToInjectable(tile, dir, true)) {
				return true;
			}
		}

		return false;
	}

	private boolean isShifterPushing(IShifter p, EnumFacing direction) {
		return p != null
				&& p.getDirection() == direction
				&& p.isShifting()
				&& p.matches(stack);
	}

	private void calculateOutputDirection() {
		if (owner.getWorld() == null || owner.getWorld().isRemote) {
			return;
		}

		PipeLogic.Direction targetDir = null;
		activeShifterDistance = Integer.MAX_VALUE;

		PipeLogic.Direction[] directions = new PipeLogic.Direction[6];
		boolean noDirs = true;

		PipeLogic.Direction[] pDirections = owner.getLogic().getPressuredDirections();

		for (int i = 0; i < 6; i++) {
			PipeLogic.Direction dir = pDirections[i];
			if (dir == null || !dir.test(stack))
				continue;

			if (canMoveDirection(dir.dir, true)) {
				// Pressurizer prioritizing valid direction, move.
			} else if (owner.getLogic().hasPressure(dir.dir.getOpposite())
					&& owner.getShifterStrength(dir.dir) == owner.getShifterStrength(dir.dir.getOpposite())) {
				// Pressurizers freezing an item in place.
			} else {
				continue;
			}

			int newDistance = getInternalShifterStrength(dir);
			if (activeShifterDistance < newDistance) {
				continue;
			} else if (activeShifterDistance > newDistance) {
				activeShifterDistance = newDistance;
				if (!noDirs) {
					for (int j = 0; j < i; j++) {
						directions[j] = null;
					}
				}
			}

			directions[i] = dir;
			noDirs = false;
		}

		int offset = getOwner().getLogic().getRoundRobinPosition(input).ordinal() + 1;

		if (!noDirs) {
			for (int i = 0; i < 6; i++) {
				PipeLogic.Direction dir = directions[(i + offset) % 6];
				if (dir == null || dir.dir == input) {
					continue;
				}

				targetDir = dir;
				if (canMoveDirection(dir.dir, true)) {
					this.output = dir.dir;
					return;
				}
			}
		}

		activeShifterDistance = 0;
		directions = owner.getLogic().getNonPressuredDirections();

		for (int i = 0; i < 6; i++) {
			PipeLogic.Direction dir = directions[(i + offset) % 6];
			if (dir == null || dir.dir == input || !dir.test(stack)) {
				continue;
			}

			targetDir = dir;
			if (canMoveDirection(dir.dir, true)) {
				this.output = dir.dir;
				return;
			}
		}

		this.output = targetDir != null ? targetDir.dir : null;
	}

	private void onReachedCenter() {
		progress = CENTER_PROGRESS;
		this.reachedCenter = true;

		if (owner.getWorld().isRemote) {
			return;
		}

		owner.updateObservers(stack);
		calculateOutputDirection();
		if (output != null) {
			getOwner().getLogic().setRoundRobinPosition(input, output);
		}
		updateRenderDirection();
		updateStuckFlag();
		sendPacket(false);
	}

	protected void reset(TilePipe owner, EnumFacing input) {
		this.owner = owner;
		initializeFromEntrySide(input);

		// Do an early calculation to aid the server side.
		// Won't always be right, might be sometimes right.
		calculateOutputDirection();
	}

	private boolean passToInjectable(TileEntity tile, EnumFacing dir, boolean simulate) {
		IItemInsertionHandler handler = CapabilityHelper.get(Capabilities.ITEM_INSERTION_HANDLER, tile, dir.getOpposite());
		if (handler != null) {
			if (owner.getWorld().isRemote) {
				return true;
			} else {
				ItemStack newStack = handler.insertItem(stack, simulate);
				if (!simulate) {
					stack = newStack;
				}
				return newStack.isEmpty();
			}
		}

		return false;
	}

	private boolean passToPipe(TilePipe pipe, EnumFacing dir, boolean simulate) {
		if (pipe != null) {
			if (pipe.injectItemInternal(this, dir.getOpposite(), simulate)) {
				return true;
			}
		}

		return false;
	}

	protected void dropItem(boolean useOutputDirection) {
		EnumFacing dir = null;

		if (useOutputDirection) {
			// Decide output direction
			int directions = 0;
			for (EnumFacing d : EnumFacing.VALUES) {
				if (owner.connects(d)) {
					directions++;
					dir = d.getOpposite();
					if (directions >= 2) {
						break;
					}
				}
			}

			if (directions >= 2) {
				dir = null;
			}
		}

		ItemUtils.spawnItemEntity(owner.getWorld(), new Vec3d(
				(double) owner.getPos().getX() + 0.5 + (dir != null ? dir.getFrontOffsetX() : 0) * 0.75,
				(double) owner.getPos().getY() + 0.5 + (dir != null ? dir.getFrontOffsetY() : 0) * 0.75,
				(double) owner.getPos().getZ() + 0.5 + (dir != null ? dir.getFrontOffsetZ() : 0) * 0.75
		), stack, 0, 0, 0, 0);

		stack = ItemStack.EMPTY;
	}

	private void initializeFromEntrySide(EnumFacing side) {
		this.input = side;
		this.output = null;
		this.reachedCenter = false;
		this.stuck = false;
		this.progress = 0;

		updateRenderDirection();
	}

	public void readFromNBT(NBTTagCompound nbt) {
		stack = new ItemStack(nbt);
		progress = nbt.getShort("p");
		input = SpaceUtils.getFacing(nbt.getByte("in"));
		output = SpaceUtils.getFacing(nbt.getByte("out"));
		reachedCenter = nbt.getBoolean("reachedCenter");
		if (nbt.hasKey("stuck")) {
			stuck = nbt.getBoolean("stuck");
		}
		if (nbt.hasKey("activePD")) {
			activeShifterDistance = nbt.getInteger("activePD");
		}
		if (nbt.hasKey("rendir")) {
			renderDirection = SpaceUtils.getFacing(nbt.getByte("rendir"));
		}
		updateRenderDirection();
	}

	public void writeToNBT(NBTTagCompound nbt) {
		stack.writeToNBT(nbt);
		nbt.setShort("p", (short) progress);
		nbt.setByte("in", (byte) SpaceUtils.ordinal(input));
		nbt.setByte("out", (byte) SpaceUtils.ordinal(output));
		nbt.setByte("rendir", (byte) SpaceUtils.ordinal(renderDirection));
		nbt.setBoolean("reachedCenter", reachedCenter);
		if (stuck) {
			nbt.setBoolean("stuck", stuck);
		}
		if (activeShifterDistance > 0) {
			nbt.setInteger("activePD", activeShifterDistance);
		}
	}

	public boolean hasReachedCenter() {
		return reachedCenter;
	}

	public void setStuckFlagClient(boolean stuck) {
		if (owner.getWorld().isRemote) {
			this.stuck = stuck;
		}
	}

	public TilePipe getOwner() {
		return owner;
	}

	public float getProgress() {
		return (float) progress / MAX_PROGRESS;
	}
}
