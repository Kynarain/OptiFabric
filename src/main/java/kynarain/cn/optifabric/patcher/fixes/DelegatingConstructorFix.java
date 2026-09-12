/*
 * New in the 1.20.6 port of OptiFabric (which is MPL-2.0, see LICENSE.txt). This file has no upstream
 * counterpart: Minecraft 1.20.6 is the first version where OptiFine's convenience constructor overload
 * broke a Fabric API injection, so the fix was written for this port.
 *
 * Rewrites OptiFine's convenience constructor overload back into the shape the game has, so that other
 * mods' Injectors still apply to it.
 *
 * Minecraft 1.20.6's ShaderProgram has a single constructor
 *
 *     ShaderProgram(ResourceProvider provider, String id, ShaderType type) {
 *         super();                                    // <- Mixin's "this()" boundary
 *         Identifier identifier = new Identifier(id); // <- injections land here legally
 *         ...
 *     }
 *
 * OptiFine splits it in two:
 *
 *     ShaderProgram(ResourceProvider provider, String id, ShaderType type) {
 *         this(provider, new Identifier(id), type);   // <- created BEFORE this(), which Mixin rejects:
 *     }                                               //    "@ModifyArg handler before this() invocation
 *                                                     //     must be static"
 *     ShaderProgram(ResourceProvider provider, Identifier id, ShaderType type) { super(); ... }
 *
 * Fabric API's ShaderProgramMixin injects into "the Identifier(String) call inside <init>", so with
 * OptiFine's layout its instance handler ends up before this() and the whole class fails to apply.
 * Skipping OptiFine's class is not an option (its shader code uses members only it adds) and the
 * injection cannot be dropped either (Fabric's config uses defaultRequire 1), so this fixer inlines
 * OptiFine's real constructor body into the String overload with the Identifier created after super() -
 * the exact shape the game itself has, and the only shape that keeps both sides working.
 *
 * The conversion the inlined copy uses is taken from the game's own constructor as well (see createValue):
 * on 1.21 that is Identifier.ofVanilla(name), which is the call Fabric API wraps there, while OptiFine's
 * delegating constructor builds the Identifier with its constructor instead - mirroring OptiFine left the
 * mixin without an injection point and the whole class failed to transform.
 *
 * OptiFine delegates through a *factory* on other releases (1.21.1: this(provider, Identifier.ofVanilla(id),
 * type)), which is the same problem with another shape: the wrapped call exists, but before this(), where Mixin
 * refuses an instance handler. findDelegation recognises both shapes and inlines either of them.
 */
package kynarain.cn.optifabric.patcher.fixes;

import java.util.ArrayList;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class DelegatingConstructorFix implements ClassFixer {
	private static final String STRING = "Ljava/lang/String;";

	@Override
	public void fix(ClassNode optifine, ClassNode minecraft) {
		List<MethodNode> constructors = new ArrayList<>();

		for (MethodNode method : optifine.methods) {
			if ("<init>".equals(method.name)) constructors.add(method);
		}

		for (MethodNode delegating : constructors) {
			Delegation delegation = findDelegation(optifine, delegating);
			if (delegation == null) continue;

			MethodNode target = null;

			for (MethodNode candidate : constructors) {
				if (candidate != delegating && candidate.desc.equals(delegation.targetDesc)) {
					target = candidate;
					break;
				}
			}

			if (target == null) continue;

			int slot = parameterSlot(target.desc, 'L' + delegation.createdType + ';');
			if (slot < 0) continue;

			MethodNode replacement = inline(optifine, minecraft, delegating.desc, target, slot, delegation);
			if (replacement == null) continue;

			optifine.methods.set(optifine.methods.indexOf(delegating), replacement);
			System.out.println("[OptiFabric] Inlined delegating constructor " + optifine.name + delegating.desc
					+ " so injections into " + delegation.createdType + " still land after super()");
		}
	}

	/** A constructor that only creates something and passes it on to another constructor of the same class. */
	private static Delegation findDelegation(ClassNode owner, MethodNode constructor) {
		MethodInsnNode call = null;

		for (AbstractInsnNode insn : constructor.instructions.toArray()) {
			if (insn instanceof MethodInsnNode methodInsn && "<init>".equals(methodInsn.name) && methodInsn.owner.equals(owner.name)) {
				call = methodInsn; // this(...) - the delegation
			}
		}

		if (call == null) return null;

		for (AbstractInsnNode insn = call.getPrevious(); insn != null; insn = insn.getPrevious()) {
			if (!(insn instanceof MethodInsnNode creation) || creation.owner.equals(owner.name)) continue;

			if ("<init>".equals(creation.name)) {
				//javac emits NEW, DUP, <argument loads>, INVOKESPECIAL <init> - so the NEW is not adjacent to the call
				for (AbstractInsnNode scan = creation.getPrevious(); scan != null; scan = scan.getPrevious()) {
					if (!(scan instanceof TypeInsnNode typeInsn) || typeInsn.getOpcode() != Opcodes.NEW || !typeInsn.desc.equals(creation.owner)) continue;

					boolean duplicated = typeInsn.getNext() != null && typeInsn.getNext().getOpcode() == Opcodes.DUP;

					if (duplicated) return new Delegation(creation.owner, creation.name, creation.desc, call.desc);

					break;
				}
			} else if (creation.getOpcode() == Opcodes.INVOKESTATIC) {
				//OptiFine also delegates through a factory of the game itself, most notably on 1.21.1:
				//  ShaderProgram(provider, String id, type) { this(provider, Identifier.ofVanilla(id), type); }
				//The type that ends up in the other constructor is the factory's return type.
				Type returned = Type.getReturnType(creation.desc);

				if (returned.getSort() == Type.OBJECT) return new Delegation(returned.getInternalName(), creation.name, creation.desc, call.desc);
			}
		}

		return null;
	}

	/** The local variable slot the given type occupies in a method with that descriptor, or -1. */
	private static int parameterSlot(String methodDesc, String typeDesc) {
		Type[] arguments = Type.getArgumentTypes(methodDesc);
		int slot = 1;

		for (Type argument : arguments) {
			if (argument.getDescriptor().equals(typeDesc)) return slot;

			slot += argument.getSize();
		}

		return -1;
	}

	/** A copy of the target constructor with the created type taken as a String and created after super(). */
	private static MethodNode inline(ClassNode owner, ClassNode minecraft, String stringDesc, MethodNode target, int slot, Delegation delegation) {
		MethodNode copy = copyOf(target, owner.name);
		if (copy == null) return null;

		//The created value goes into a fresh slot at the end of the frame, and the body's reads of the
		//parameter move there - the parameter layout itself must keep matching the descriptor, otherwise the
		//verifier sees the slots after it as uninitialised ("Bad local variable type", locals[4] = top).
		int newSlot = target.maxLocals;

		for (AbstractInsnNode insn : copy.instructions.toArray()) {
			if (insn instanceof VarInsnNode var && var.var == slot) {
				if (var.getOpcode() != Opcodes.ALOAD) {
					//The slot is reused for something else later on, rewriting it would corrupt that value
					System.err.println("[OptiFabric] Cannot inline " + owner.name + target.desc + ": slot " + slot + " is stored to as well");

					return null;
				}

				var.var = newSlot;
			} else if (insn instanceof IincInsnNode iinc && iinc.var == slot) {
				System.err.println("[OptiFabric] Cannot inline " + owner.name + target.desc + ": slot " + slot + " is an integer");

				return null;
			}
		}

		AbstractInsnNode superCall = findSuperCall(owner, copy);
		if (superCall == null) return null;

		InsnList creation = createValue(minecraft, stringDesc, slot, newSlot, delegation);
		if (creation == null) return null;

		copy.instructions.insert(superCall, creation);

		copy.desc = replaceParameter(target.desc, slot);
		copy.signature = null; //the generic signature describes the old parameter
		copy.maxLocals = newSlot + 1; //max stack is recomputed by the frame computing writer

		return copy;
	}

	/**
	 * The instructions that turn the String parameter into the type OptiFine's delegating constructor created.
	 *
	 * The vanilla constructor is asked first, because its conversion is the call Fabric API's mixin was written
	 * against: on 1.21 the game's own constructor calls {@code Identifier.ofVanilla(name)} and
	 * ShaderProgramMixin wraps that INVOKE, while OptiFine's delegating constructor creates the Identifier with
	 * its constructor instead. Mirroring OptiFine there left the mixin without its injection point, so the whole
	 * class failed to transform and the game died while OptiFine's Reflector initialised. Only when the vanilla
	 * constructor has no such factory call does this fall back to what OptiFine did itself.
	 */
	private static InsnList createValue(ClassNode minecraft, String stringDesc, int slot, int newSlot, Delegation delegation) {
		MethodInsnNode factory = findFactory(minecraft, stringDesc, delegation.createdType);
		InsnList creation = new InsnList();

		if (factory != null) {
			creation.add(new VarInsnNode(Opcodes.ALOAD, slot));
			creation.add(new MethodInsnNode(Opcodes.INVOKESTATIC, factory.owner, factory.name, factory.desc, factory.itf));
			creation.add(new VarInsnNode(Opcodes.ASTORE, newSlot));

			System.out.println("[OptiFabric] Creates the " + delegation.createdType + " with " + factory.owner + '.'
					+ factory.name + " (as the game does) when inlining " + stringDesc);

			return creation;
		}

		if (!"<init>".equals(delegation.createdName)) {
			//OptiFine's own factory (the game has no equivalent here), reused as it stands
			creation.add(new VarInsnNode(Opcodes.ALOAD, slot));
			creation.add(new MethodInsnNode(Opcodes.INVOKESTATIC, delegation.createdType, delegation.createdName, delegation.createdDesc, false));
			creation.add(new VarInsnNode(Opcodes.ASTORE, newSlot));

			System.out.println("[OptiFabric] Creates the " + delegation.createdType + " with OptiFine's " + delegation.createdType
					+ '.' + delegation.createdName + " when inlining " + stringDesc);

			return creation;
		}

		creation.add(new TypeInsnNode(Opcodes.NEW, delegation.createdType));
		creation.add(new InsnNode(Opcodes.DUP));
		creation.add(new VarInsnNode(Opcodes.ALOAD, slot)); //the String parameter
		creation.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, delegation.createdType, "<init>", delegation.createdDesc, false));
		creation.add(new VarInsnNode(Opcodes.ASTORE, newSlot));

		return creation;
	}

	/** A static {@code (String)} factory for the created type, as called by the game's own constructor. */
	private static MethodInsnNode findFactory(ClassNode minecraft, String stringDesc, String createdType) {
		if (minecraft == null) return null;

		String wanted = '(' + STRING + ")L" + createdType + ';';

		for (MethodNode method : minecraft.methods) {
			if (!"<init>".equals(method.name) || !method.desc.equals(stringDesc)) continue;

			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (insn instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC && call.desc.equals(wanted)) {
					return call;
				}
			}
		}

		return null;
	}

	private static String replaceParameter(String methodDesc, int slot) {
		Type[] arguments = Type.getArgumentTypes(methodDesc);
		Type[] replaced = arguments.clone();
		int current = 1;

		for (int i = 0; i < arguments.length; i++) {
			if (current == slot) {
				replaced[i] = Type.getObjectType(STRING.substring(1, STRING.length() - 1));
				break;
			}

			current += arguments[i].getSize();
		}

		return Type.getMethodDescriptor(Type.getReturnType(methodDesc), replaced);
	}

	/** The superclass constructor call, which is where injections are allowed to be instance methods. */
	private static AbstractInsnNode findSuperCall(ClassNode owner, MethodNode method) {
		for (AbstractInsnNode insn : method.instructions.toArray()) {
			if (insn instanceof MethodInsnNode call && "<init>".equals(call.name) && call.owner.equals(owner.superName)) return insn;
		}

		return null;
	}

	/** An independent copy of a method, through a throw-away class. */
	private static MethodNode copyOf(MethodNode method, String owner) {
		try {
			ClassNode wrapper = new ClassNode();
			wrapper.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner + "$optifabricCopy", null, "java/lang/Object", null);
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

	private record Delegation(String createdType, String createdName, String createdDesc, String targetDesc) {
	}
}
