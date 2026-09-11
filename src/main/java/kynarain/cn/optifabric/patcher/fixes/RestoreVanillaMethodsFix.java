/*
 * New in this 1.20.6 port of OptiFabric (which is MPL-2.0, see LICENSE.txt). This file has no upstream
 * counterpart.
 *
 * Puts vanilla helper methods back into OptiFine's version of a class.
 *
 * OptiFine recompiles the classes it patches, and javac inlines or merges small private helpers while
 * doing so. Fabric API's mixins still inject into those helpers (they resolve them through their refmap),
 * and Mixin refuses to apply an injection whose target does not exist - with the default require of 1
 * that fails the whole class, exactly like the ShaderProgram constructor case.
 *
 * The vanilla method is copied over verbatim: the injections then have a real target, and because
 * OptiFine's own code no longer calls the inlined-away helper, adding it back cannot change behaviour.
 * Anything it references is resolved lazily by the JVM, so a stale reference inside the restored body
 * cannot break class loading.
 */
package kynarain.cn.optifabric.patcher.fixes;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class RestoreVanillaMethodsFix implements ClassFixer {
	private final String[] names;
	private final boolean replace;

	public RestoreVanillaMethodsFix(String... names) {
		this(false, names);
	}

	/**
	 * @param replace when true the vanilla method replaces OptiFine's version of the same name and
	 *                descriptor instead of only being added when it is missing. Needed when OptiFine kept
	 *                the method but reduced it to a wrapper: its local variables then no longer exist, which
	 *                breaks every {@code LocalCapture} injection into it.
	 */
	public RestoreVanillaMethodsFix(boolean replace, String... names) {
		this.replace = replace;
		this.names = names;
	}

	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		for (String name : names) {
			for (MethodNode vanilla : minecraft.methods) {
				if (!vanilla.name.equals(name)) continue;

				MethodNode existing = find(optifine, vanilla.name, vanilla.desc);
				if (existing != null && !replace) continue;

				MethodNode copy = copyOf(vanilla, optifine.name);
				if (copy == null) continue;

				if (existing != null) optifine.methods.remove(existing);
				optifine.methods.add(copy);
				System.out.println("[OptiFabric] Restored vanilla " + optifine.name + '.' + vanilla.name + vanilla.desc
						+ (existing != null ? " over OptiFine's version" : "") + " so injections into it have a target");
			}
		}
	}

	private static MethodNode find(ClassNode node, String name, String desc) {
		for (MethodNode method : node.methods) {
			if (method.name.equals(name) && method.desc.equals(desc)) return method;
		}

		return null;
	}

	/** An independent copy of a method, through a throw-away class. */
	private static MethodNode copyOf(MethodNode method, String owner) {
		try {
			ClassNode wrapper = new ClassNode();
			wrapper.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner + "$optifabricRestored", null, "java/lang/Object", null);
			wrapper.methods.add(method);

			ClassWriter writer = new ClassWriter(0);
			wrapper.accept(writer);

			ClassNode read = new ClassNode();
			new ClassReader(writer.toByteArray()).accept(read, ClassReader.EXPAND_FRAMES);

			return read.methods.get(0);
		} catch (Throwable t) {
			System.err.println("[OptiFabric] Unable to copy " + owner + '#' + method.name + method.desc);
			t.printStackTrace();

			return null;
		}
	}
}
