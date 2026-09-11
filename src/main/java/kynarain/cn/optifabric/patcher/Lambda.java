/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 and 1.21.11 / Fabric Loader 0.19.x.
 */

/*
 * Copyright 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package kynarain.cn.optifabric.patcher;

import java.util.Objects;

class Lambda {
	public final String owner, name, desc;
	public final String method;

	public Lambda(String owner, String name, String desc, String method) {
		this.owner = owner;
		this.name = name;
		this.desc = desc;
		this.method = method;
	}

	public String getFullName() {
		return owner + '#' + name + desc;
	}

	public String getName() {
		return name.concat(desc);
	}

	@Override
	public int hashCode() {
		return Objects.hash(owner, name, desc, method);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (!(obj instanceof Lambda)) return false;

		Lambda that = (Lambda) obj;
		return Objects.equals(owner, that.owner) && Objects.equals(name, that.name) && Objects.equals(desc, that.desc) && Objects.equals(method, that.method);
	}
}
