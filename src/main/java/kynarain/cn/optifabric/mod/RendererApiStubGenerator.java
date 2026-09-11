/*
 * New in the 1.21.11 port of OptiFabric (which is MPL-2.0, see LICENSE.txt).
 *
 * Writes the placeholder rendering plug-in (see RendererApiFallback) at runtime.
 *
 * Fabric's renderer API lives in its own mod, and this mod deliberately depends on nothing but Fabric Loader, so
 * the class cannot be written against that interface at compile time. It is generated instead, and it is generated
 * *without ever looking at the interface's method types through reflection*.
 *
 * That part is not a style choice, it was a real crash. Renderer's methods take Minecraft types as arguments
 * (BlockState, BakedModel, BlockRenderManager, MatrixStack, ...), and Class.getMethods() resolves the parameter and
 * return types of every method it returns. Calling it during preLaunch therefore *loads* those game classes from
 * the classpath - before Fabric Loader has been given OptiFine's patched versions of them. A loaded class is never
 * looked up again, so those classes stayed vanilla for the whole run and the first OptiFine code that needed an
 * OptiFine-added member on one of them died:
 *
 *   java.lang.NoSuchMethodError: 'java.lang.Class net.minecraft.class_2680.getBlockStateBaseCacheClass()'
 *     at net.optifine.reflect.Reflector.<clinit>(Reflector.java:401)
 *
 * (class_2680 is BlockState, and that method is OptiFine's own addition to it. The patched class has it; the class
 * the game ended up with did not.)
 *
 * So the interface is read as a class file instead: its own bytecode is parsed with ASM and the abstract methods are
 * copied over by name and descriptor, as strings. No game class is ever resolved here, and the generated class
 * carries a name worth reading in the F3 "Renderer:" line:
 *
 *   interface net.fabricmc.fabric.api.renderer.v1.Renderer
 *   class     kynarain.cn.optifabric.mod.OptifineRendererPlaceholder
 */
package kynarain.cn.optifabric.mod;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

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
	public static Object newInstance(Class<?> rendererInterface) throws ReflectiveOperationException, IOException {
		Class<?> stub = MethodHandles.lookup().defineClass(build(rendererInterface));
		return stub.getDeclaredConstructor().newInstance();
	}

	private static byte[] build(Class<?> rendererInterface) throws IOException {
		String name = Type.getInternalName(RendererApiStubGenerator.class);
		name = name.substring(0, name.lastIndexOf('/') + 1) + SIMPLE_NAME;

		//Name -> descriptors: one name can carry several overloads (Renderer.render has two), so the descriptors are
		//kept as a list rather than one entry per name
		Map<String, List<String>> methods = new LinkedHashMap<>();
		collect(rendererInterface, methods);

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, null, "java/lang/Object",
				new String[] {Type.getInternalName(rendererInterface)});

		int implemented = 0;

		for (Map.Entry<String, List<String>> method : methods.entrySet()) {
			for (String desc : method.getValue()) {
				MethodVisitor body = writer.visitMethod(Opcodes.ACC_PUBLIC, method.getKey(), desc, null, null);
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
				+ rendererInterface.getName() + " without resolving any of their argument types");

		return writer.toByteArray();
	}

	/** Every abstract method the interface (and the interfaces it extends) declares, by name and descriptor. */
	private static void collect(Class<?> rendererInterface, Map<String, List<String>> methods) throws IOException {
		for (Class<?> parent : rendererInterface.getInterfaces()) {
			collect(parent, methods);
		}

		String resource = rendererInterface.getName().replace('.', '/') + ".class";
		ClassNode node = new ClassNode();

		try (InputStream in = rendererInterface.getClassLoader().getResourceAsStream(resource)) {
			if (in == null) throw new IOException("Cannot read the class file of " + rendererInterface.getName());

			new ClassReader(in).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
		}

		for (MethodNode method : node.methods) {
			int skip = Opcodes.ACC_STATIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC;

			//Only abstract instance methods need a body (a default method already comes with one, and a static one is
			//never inherited)
			if ((method.access & Opcodes.ACC_ABSTRACT) == 0 || (method.access & skip) != 0) continue;

			methods.computeIfAbsent(method.name, key -> new ArrayList<>()).add(method.desc);
		}
	}
}
