/*
 * New in the 1.21.11 port of OptiFabric (which is MPL-2.0, see LICENSE.txt).
 *
 * Writes the placeholder rendering plug-in (see RendererApiFallback) at runtime.
 *
 * Fabric's renderer API lives in its own mod, and this mod deliberately depends on nothing but Fabric Loader, so
 * the class cannot be written against that interface at compile time. It is generated instead: the interface is
 * read reflectively, so whatever methods it has - and whatever Fabric API adds to it later - are implemented with
 * a body that explains itself, and the class carries a name worth reading in the F3 "Renderer:" line:
 *
 *   interface net.fabricmc.fabric.api.renderer.v1.Renderer
 *   class     kynarain.cn.optifabric.mod.OptifineRendererPlaceholder
 */
package kynarain.cn.optifabric.mod;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class RendererApiStubGenerator {
	/** Also what Fabric API's debug entry prints behind "Renderer:". */
	public static final String SIMPLE_NAME = "OptifineRendererPlaceholder";

	private static final String MESSAGE = "OptiFine is the active terrain renderer: Fabric's renderer API has no"
			+ " rendering plug-in behind it here (Indigo steps aside for OptiFine, see fabric.mod.json), so there is"
			+ " nothing to draw these objects with. This placeholder only exists so Fabric API finds a registered"
			+ " renderer instead of crashing on the lookup itself.";

	private RendererApiStubGenerator() {
	}

	/** An instance of a generated class that implements {@code rendererInterface} and does nothing at all. */
	public static Object newInstance(Class<?> rendererInterface) throws ReflectiveOperationException {
		Class<?> stub = MethodHandles.lookup().defineClass(build(rendererInterface));
		return stub.getDeclaredConstructor().newInstance();
	}

	private static byte[] build(Class<?> rendererInterface) {
		String name = Type.getInternalName(RendererApiStubGenerator.class);
		name = name.substring(0, name.lastIndexOf('/') + 1) + SIMPLE_NAME;

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, null, "java/lang/Object",
				new String[] {Type.getInternalName(rendererInterface)});

		//Only the abstract methods have to be filled in: the static ones are never inherited, and a default
		//method already has the body the interface gave it.
		int implemented = 0;

		for (Method method : rendererInterface.getMethods()) {
			if (!Modifier.isAbstract(method.getModifiers()) || method.isBridge()) continue;

			MethodVisitor body = writer.visitMethod(Opcodes.ACC_PUBLIC, method.getName(),
					Type.getMethodDescriptor(method), null, null);
			body.visitCode();
			body.visitTypeInsn(Opcodes.NEW, "java/lang/UnsupportedOperationException");
			body.visitInsn(Opcodes.DUP);
			body.visitLdcInsn(MESSAGE);
			body.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/UnsupportedOperationException", "<init>",
					"(Ljava/lang/String;)V", false);
			body.visitInsn(Opcodes.ATHROW);
			body.visitMaxs(0, 0); //Computed
			body.visitEnd();
			implemented++;
		}

		MethodVisitor constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		constructor.visitCode();
		constructor.visitVarInsn(Opcodes.ALOAD, 0);
		constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
		constructor.visitInsn(Opcodes.RETURN);
		constructor.visitMaxs(0, 0); //Computed
		constructor.visitEnd();

		writer.visitEnd();

		System.out.println("[OptiFabric] Generated " + name + ", implementing " + implemented + " method(s) of "
				+ rendererInterface.getName());

		return writer.toByteArray();
	}
}
