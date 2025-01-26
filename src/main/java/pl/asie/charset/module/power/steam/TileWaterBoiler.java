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

package pl.asie.charset.module.power.steam;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidHandlerConcatenate;
import net.minecraftforge.fluids.capability.templates.FluidHandlerFluidMap;
import pl.asie.charset.lib.block.TileBase;
import pl.asie.charset.module.power.steam.api.IMirror;
import pl.asie.charset.module.power.steam.api.IMirrorTarget;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

public class TileWaterBoiler extends TileMirrorTargetBase implements ITickable {
	private final FluidTank waterTank = new FluidTank(2000);
	private int givenHeat, givenHeatClient;

	public TileWaterBoiler() {
		waterTank.setCanDrain(false);
		waterTank.setCanFill(true);
	}

	public float getGivenHeatClient() {
		return (float) MathHelper.clamp(Math.log10(getGivenHeat()) / 6, 0, 0.4f);
	}

	public int getGivenHeat() {
		return givenHeat;
	}

	float getReflectorStrength() {
		return mirrors.stream().filter(IMirror::isMirrorActive).map(IMirror::getMirrorStrength).reduce(0f, (a, b) -> a + b);
	}

	int getHeat() {
		int v = Math.max((int) (getReflectorStrength() - 3), 0);
		return (v + 1) >> 1; // divide by two, rounding up
	}

	@Override
	public void readNBTData(NBTTagCompound compound, boolean isClient) {
		super.readNBTData(compound, isClient);
		if (!isClient) {
			if (compound.hasKey("water", Constants.NBT.TAG_COMPOUND)) {
				waterTank.readFromNBT(compound.getCompoundTag("water"));
			} else {
				waterTank.setFluid(null);
			}
		}

		givenHeat = compound.getInteger("heat");
	}

	@Override
	public NBTTagCompound writeNBTData(NBTTagCompound compound, boolean isClient) {
		compound = super.writeNBTData(compound, isClient);
		if (!isClient) {
			NBTTagCompound waterCpd = new NBTTagCompound();

			waterTank.writeToNBT(waterCpd);

			compound.setTag("water", waterCpd);
		}

		compound.setInteger("heat", isClient ? givenHeatClient : givenHeat);
		return compound;
	}

	@Override
	public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
		if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && facing != EnumFacing.UP) {
			return true;
		}

		return super.hasCapability(capability, facing);
	}


	@Override
	public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
		if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
			if (facing != EnumFacing.UP) {
				return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(waterTank);
			}
		}
		return super.getCapability(capability, facing);
	}

	@Override
	public void update() {
		super.update();

		if (world.isRemote) {
			return;
		}

		long seed = world.getTotalWorldTime() + pos.toLong();
		int measureTime = 5;
		if (seed % measureTime == 0) {
			int oldGHC = givenHeatClient;
			givenHeatClient = givenHeat / measureTime;
			givenHeat = 0;
			if (oldGHC != givenHeatClient) {
				markBlockForUpdate();
			}
		}

		if (waterTank.getFluidAmount() < 1000) {
			BlockPos below = pos.down();
			while (world.getTileEntity(below) instanceof TileWaterBoiler) {
				below = below.down();
			}
			FluidStack desiredAmount = new FluidStack(FluidRegistry.WATER, 1000);

			// IBlockState state = world.getBlockState(below);
			IFluidHandler handler = FluidUtil.getFluidHandler(world, below, EnumFacing.UP);

			if (handler != null) {
				// TODO: do not always actually remove water.
				FluidStack drained = handler.drain(desiredAmount, true);
				if (drained != null) {
					// no bounds-checking = 999+1000 is 1999
					waterTank.setFluid(new FluidStack(FluidRegistry.WATER, waterTank.getFluidAmount() + drained.amount));
				}
			}
		}

		int heat = getHeat();
		if (heat > 0) {
			applyHeat(heat);
		}
	}

	public void applyHeat(int heat) {
		givenHeat += heat;

		int toBoil = Math.min(heat, waterTank.getFluidAmount());
		if (toBoil <= 0) {
			return;
		}

		int waterToSteam = 160; /* neptunepink claims CovertJaguar gives 1:160 as the water:steam ratio */
		int waterToRemove = Math.max(toBoil / waterToSteam, 1);
		if (waterToRemove > waterTank.getFluidAmount()) {
			return;
		}

		FluidStack wFluid = waterTank.getFluid();
		waterTank.setFluid(wFluid.amount == waterToRemove ? null : new FluidStack(FluidRegistry.WATER, wFluid.amount - waterToRemove));

		BlockPos ppos = pos.up();
		while (world.getTileEntity(ppos) instanceof TileWaterBoiler) {
			ppos = ppos.up();
		}

		toBoil *= CharsetPowerSteam.BOILER_OUTPUT_MULTIPLIER;
		if (toBoil <= 0) {
			return;
		}

		world.getCapability(CharsetPowerSteam.steamWorldCap, null).spawnParticle(
				new SteamParticle(
						world,
						ppos.getX() + 0.25f + (world.rand.nextFloat() * 0.5f),
						ppos.getY() - 0.01f,
						ppos.getZ() + 0.25f + (world.rand.nextFloat() * 0.5f),
						(world.rand.nextFloat() * 0.02f) - 0.01f,
						0.03f * Math.sqrt(toBoil),
						(world.rand.nextFloat() * 0.02f) - 0.01f,
						100,
						toBoil
				)
		);
	}

	@Override
	public boolean hasFastRenderer() {
		return true;
	}

	@Override
	public boolean shouldRenderInPass(int pass) {
		return pass == 1;
	}
}
