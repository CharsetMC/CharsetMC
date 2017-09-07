/*
 * Copyright (c) 2015, 2016, 2017 Adrian Siekierka
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

package pl.asie.charset.module.tweaks.carry;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.INetHandler;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import pl.asie.charset.lib.network.Packet;
import pl.asie.charset.lib.utils.Utils;

/**
 * Created by asie on 1/2/17.
 */
public class PacketCarryGrab extends Packet {
	enum Type {
		BLOCK,
		ENTITY
	}

	private EntityPlayer player;
	private Type type;
	private World world;
	private BlockPos pos;
	private Entity entity;

	public PacketCarryGrab() {

	}

	public PacketCarryGrab(World world, BlockPos pos) {
		this.world = world;
		this.type = Type.BLOCK;
		this.pos = pos;
	}

	public PacketCarryGrab(World world, Entity entity) {
		this.world = world;
		this.type = Type.ENTITY;
		this.entity = entity;
	}

	@Override
	public void readData(INetHandler handler, PacketBuffer buf) {
		int dim = buf.readInt();

		player = getPlayer(handler);
		type = Type.values()[buf.readByte()];
		world = Utils.getLocalWorld(dim);

		switch (type) {
			case BLOCK:
				int x = buf.readInt();
				int y = buf.readInt();
				int z = buf.readInt();
				pos = new BlockPos(x, y, z);
				break;
			case ENTITY:
				int eid = buf.readInt();
				entity = world.getEntityByID(eid);
				break;
		}
	}

	@Override
	public void apply(INetHandler handler) {
		if (player != null) {
			switch (type) {
				case BLOCK:
					CharsetTweakBlockCarrying.grabBlock(player, world, pos);
					break;
				case ENTITY:
					CharsetTweakBlockCarrying.grabEntity(player, world, entity);
					break;
			}
		}
	}

	@Override
	public void writeData(PacketBuffer buf) {
		buf.writeInt(world.provider.getDimension());
		switch (type) {
			case BLOCK:
				buf.writeByte(0);
				buf.writeInt(pos.getX());
				buf.writeInt(pos.getY());
				buf.writeInt(pos.getZ());
				break;
			case ENTITY:
				buf.writeByte(1);
				buf.writeInt(entity.getEntityId());
				break;
		}
	}

	@Override
	public boolean isAsynchronous() {
		return false;
	}
}
