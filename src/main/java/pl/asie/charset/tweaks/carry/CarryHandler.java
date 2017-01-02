package pl.asie.charset.tweaks.carry;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class CarryHandler {
    public static final Storage STORAGE = new Storage();
    public static final BlockPos ACCESS_POS = new BlockPos(0, 64, 0);

    private Entity player;
    private Access access;
    private IBlockState block;
    private NBTTagCompound tile;

    public CarryHandler() {
        this.access = new Access();
    }

    public CarryHandler setPlayer(Entity player) {
        this.player = player;
        return this;
    }

    public IBlockAccess getBlockAccess() {
        return access;
    }

    public boolean isCarrying() {
        return block != null;
    }

    public void put(IBlockState state, TileEntity tile) {
        this.block = state;
        if (tile != null) {
            this.tile = tile.writeToNBT(new NBTTagCompound());
            this.tile.setInteger("x", 0);
            this.tile.setInteger("y", 64);
            this.tile.setInteger("z", 0);
        } else {
            this.tile = null;
        }
    }

    public boolean grab(World world, BlockPos pos) {
        if (block == null) {
            block = world.getBlockState(pos);

            if (block.getBlock().isAir(block, world, pos)) {
                block = null;
                return false;
            }

            if (block.getBlock().hasTileEntity(block)) {
                tile = world.getTileEntity(pos).writeToNBT(new NBTTagCompound());
                tile.setInteger("x", 0);
                tile.setInteger("y", 64);
                tile.setInteger("z", 0);
                world.removeTileEntity(pos);
            }

            world.setBlockToAir(pos);

            return true;
        } else {
            return false;
        }
    }

    public boolean place(World world, BlockPos pos, EnumFacing facing) {
        if (block != null) {
            if (world.mayPlace(block.getBlock(), pos, false, facing, (Entity)null)) {
                world.setBlockState(pos, block);
                block = null;

                if (tile != null) {
                    tile.setInteger("x", pos.getX());
                    tile.setInteger("y", pos.getY());
                    tile.setInteger("z", pos.getZ());
                    world.setTileEntity(pos, TileEntity.create(world, tile));
                    tile = null;
                }

                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public void empty() {
        block = null;
        tile = null;
    }

    public IBlockState getBlockState() {
        return block;
    }

    public TileEntity getTileEntity() {
        // TODO: optimize
        return tile != null ? TileEntity.create(player.world, tile) : null;
    }

    public static class Storage implements Capability.IStorage<CarryHandler> {
        @Override
        public NBTBase writeNBT(Capability<CarryHandler> capability, CarryHandler instance, EnumFacing side) {
            NBTTagCompound compound = new NBTTagCompound();
            if (instance.block != null) {
                compound.setString("b:name", instance.block.getBlock().getRegistryName().toString());
                compound.setByte("b:meta", (byte) instance.block.getBlock().getMetaFromState(instance.block));

                if (instance.tile != null) {
                    compound.setTag("b:tile", instance.tile);
                }
            }
            return compound;
        }

        @Override
        public void readNBT(Capability<CarryHandler> capability, CarryHandler instance, EnumFacing side, NBTBase nbt) {
            instance.empty();

            if (nbt instanceof NBTTagCompound) {
                NBTTagCompound compound = (NBTTagCompound) nbt;
                if (compound.hasKey("b:name") && compound.hasKey("b:meta")) {
                    Block block = Block.getBlockFromName(compound.getString("b:name"));
                    instance.block = block.getStateFromMeta(compound.getByte("b:meta"));

                    if (compound.hasKey("b:tile")) {
                        instance.tile = compound.getCompoundTag("b:tile");
                    }
                }
            }
        }
    }

    public static class Provider implements ICapabilitySerializable<NBTTagCompound> {
        final CarryHandler handler;

        public Provider(EntityPlayer entity) {
            handler = new CarryHandler().setPlayer(entity);
        }

        @Override
        public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
            return capability == TweakCarry.CAPABILITY;
        }

        @Nullable
        @Override
        public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
            return capability == TweakCarry.CAPABILITY ? TweakCarry.CAPABILITY.cast(handler) : null;
        }

        @Override
        public NBTTagCompound serializeNBT() {
            return (NBTTagCompound) STORAGE.writeNBT(TweakCarry.CAPABILITY, handler, null);
        }

        @Override
        public void deserializeNBT(NBTTagCompound nbt) {
            STORAGE.readNBT(TweakCarry.CAPABILITY, handler, null, nbt);
        }
    }

    private class Access implements IBlockAccess {
        private BlockPos getPlayerPos() {
            return player != null ? player.getPosition() : ACCESS_POS;
        }

        @Nullable
        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return pos.equals(ACCESS_POS) ? CarryHandler.this.getTileEntity() : null;
        }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return player != null
                ? player.world.getCombinedLight(getPlayerPos(), lightValue)
                : (15 << 20 | 15 << 4);
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return pos.equals(ACCESS_POS) ? block : Blocks.AIR.getDefaultState();
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return !pos.equals(ACCESS_POS);
        }

        @Override
        public Biome getBiome(BlockPos pos) {
            return player != null ? player.world.getBiome(getPlayerPos()) : Biome.getBiome(1);
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return 0;
        }

        @Override
        public WorldType getWorldType() {
            return player != null ? player.world.getWorldType() : WorldType.DEFAULT;
        }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
            return false;
        }
    }
}
