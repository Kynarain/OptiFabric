/*
 * New in the 1.21.11 port of OptiFabric (which is MPL-2.0, see LICENSE.txt).
 *
 * Moves every call to a method onto another name, in the classes that call it.
 *
 * StubInjectionTargetFix renames the method a Fabric API hook injects into and leaves a copy behind under the
 * original name, so the hook lands in code nobody runs. That only holds while the calls to that method live in
 * the same class: LevelRenderer.method_62210 is called from inside LevelRenderer itself, so that fixer could
 * rewrite the calls it found there. The moving block hook is the other way round - the vanilla method is called
 * from a neighbour class (net/minecraft/class_11684.method_73002), which then still resolved to the copy Mixin
 * had just injected into, and the crash came straight back:
 *
 *   UnsupportedOperationException: Attempted to retrieve active rendering plug-in before one was registered.
 *     at FabricBlockModelRenderer.render -> Renderer.get()
 *     at class_11681.handler$zmb000$fabric-renderer-api-v1$beforeRenderMovingBlocks
 *     at class_11681.method_72998
 *     at class_11684.method_73002
 *
 * This fixer closes that gap from the calling side, and it is registered as an "extra class": the caller is not
 * something OptiFine patches, so it is taken over the same way the callee is (see OptifineFixer#registerExtraClass).
 * Only the invoked name changes - owner, arguments and the instruction itself stay untouched, so no local
 * variable, stack map or injection offset moves.
 */
package kynarain.cn.optifabric.patcher.fixes;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import kynarain.cn.optifabric.util.RemappingUtils;

public class CallSiteRedirectFix implements ClassFixer {
	private final String owner;
	private final String name;
	private final String desc;
	private final String newName;
	private final String reason;

	/**
	 * @param owner the class the called method belongs to, in intermediary notation ({@code class_11681})
	 * @param name the called method's name
	 * @param desc its descriptor in intermediary notation
	 * @param newName the name the calls are moved to
	 * @param reason what the redirect is for, only used in the log line
	 */
	public CallSiteRedirectFix(String owner, String name, String desc, String newName, String reason) {
		this.owner = RemappingUtils.getClassName(owner);
		this.name = name;
		this.desc = RemappingUtils.mapMethodDescriptor(desc);
		this.newName = newName;
		this.reason = reason;
	}

	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		int redirected = 0;

		for (MethodNode method : optifine.methods) {
			if (method.instructions == null) continue;

			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (!(insn instanceof MethodInsnNode call)) continue;
				if (!call.owner.equals(owner) || !call.name.equals(name) || !call.desc.equals(desc)) continue;

				call.name = newName;
				redirected++;
			}
		}

		if (redirected > 0) {
			System.out.println("[OptiFabric] Moved " + redirected + " call(s) from " + optifine.name + " onto "
					+ owner + '.' + newName + desc + " instead of " + name + " (" + reason + ')');
		}
	}
}
