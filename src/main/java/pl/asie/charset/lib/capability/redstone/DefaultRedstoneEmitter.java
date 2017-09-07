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

package pl.asie.charset.lib.capability.redstone;

import pl.asie.charset.api.wires.IRedstoneEmitter;

public class DefaultRedstoneEmitter implements IRedstoneEmitter {
	private int data;

	public DefaultRedstoneEmitter(int data) {
		emit(data);
	}

	public DefaultRedstoneEmitter() {
		emit(0);
	}

	@Override
	public int getRedstoneSignal() {
		return data;
	}

	public void emit(int data) {
		if (data > 15) {
			this.data = 15;
		} else if (data < 0) {
			this.data = 0;
		} else {
			this.data = data;
		}
	}
}
