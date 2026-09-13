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
 * So each interface is read as a class file instead: its own bytecode is parsed with ASM and the abstract methods
 * are copied over by name and descriptor, as strings. No game class is ever resolved here.
 *
 * What the methods *do* changed with 26.1. Throwing was the honest answer while the only live caller was the F3
 * debug line; a real world showed that Fabric API's own rendering hooks call these methods in the middle of
 * ordinary drawing:
 *
 *   UnsupportedOperationException: ... (our placeholder)
 *     at ...OptifineRendererPlaceholder.quadEmitter
 *     at net.minecraft.client.renderer.feature.BlockFeatureRenderer.renderBreakingBlockModelSubmits
 *     at net.minecraft.client.renderer.feature.BlockFeatureRenderer.renderTranslucent
 *     at ...GameRenderer.renderItemInHand
 *
 * BlockFeatureRenderer is not a class OptiFine patches, so those calls are Fabric API's own code running inside the
 * vanilla method, and they happen on the first frame that draws a block. A renderer that cannot draw is exactly
 * what "OptiFine is the terrain renderer, Indigo steps aside" means, so the placeholder now answers those calls
 * with inert objects of the right shape instead of failing: the quads Fabric API emits go nowhere, and OptiFine
 * draws the world itself. Those interfaces are fluent (their abstract methods return the same interface), which is
 * why a stub can hand itself back and needs no state to be correct.
 */
package kynarain.cn.optifabric.mod;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

	/** How deep the return types of an interface are followed. Cycles are handled by the name set, so this only
	 *  bounds how wide the generated family can get. */
	private static final int MAX_DEPTH = 4;

	private RendererApiStubGenerator() {
	}

	/** An instance of a generated class that implements {@code rendererInterface} and draws nothing. */
	public static Object newInstance(Class<?> rendererInterface) throws ReflectiveOperationException, IOException {
		ClassLoader loader = rendererInterface.getClassLoader();
		Set<String> needed = new LinkedHashSet<>();
		collectFamily(rendererInterface, needed, 0);

		Map<String, Class<?>> defined = new LinkedHashMap<>();

		for (String internal : needed) {
			Class<?> iface = Class.forName(internal.replace('/', '.'), false, loader);
			defined.put(internal, MethodHandles.lookup().defineClass(build(iface, internal, needed, loader)));
		}

		Class<?> root = defined.get(Type.getInternalName(rendererInterface));
		System.out.println("[OptiFabric] Generated " + defined.size() + " placeholder class(es) for "
				+ rendererInterface.getName() + " without resolving any of their argument types");

		return root.getDeclaredConstructor().newInstance();
	}

	/** The interface itself plus every interface its methods hand back, which a stub has to be able to return. */
	private static void collectFamily(Class<?> iface, Set<String> needed, int depth) {
		if (depth > MAX_DEPTH || !needed.add(Type.getInternalName(iface))) return;

		ClassLoader loader = iface.getClassLoader();

		for (String desc : descriptors(iface)) {
			Type returns = Type.getReturnType(desc);
			if (returns.getSort() != Type.OBJECT) continue;

			String internal = returns.getInternalName();

			try {
				if (Class.forName(internal.replace('/', '.'), false, loader).isInterface()) {
					collectFamily(Class.forName(internal.replace('/', '.'), false, loader), needed, depth + 1);
				}
			} catch (ClassNotFoundException | LinkageError e) {
				//not a type we can build a stub for; the method returns null then
			}
		}
	}

	private static List<String> descriptors(Class<?> iface) {
		List<String> out = new ArrayList<>();

		for (Class<?> parent : iface.getInterfaces()) out.addAll(descriptors(parent));

		try (InputStream in = resource(iface)) {
			if (in == null) return out;

			ClassNode node = new ClassNode();
			new ClassReader(in).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

			for (MethodNode method : node.methods) {
				if ((method.access & Opcodes.ACC_ABSTRACT) == 0) continue;
				out.add(method.desc);
			}
		} catch (IOException e) {
			//treated as "no return types to worry about"
		}

		return out;
	}

	private static byte[] build(Class<?> iface, String internal, Set<String> family, ClassLoader loader) throws IOException {
		String packageName = Type.getInternalName(RendererApiStubGenerator.class);
		packageName = packageName.substring(0, packageName.lastIndexOf('/') + 1);

		String simple = iface.getSimpleName();
		String name = SIMPLE_NAME.equals(simple) || simple.isEmpty()
				? SIMPLE_NAME + "Stub"
				: SIMPLE_NAME + '$' + simple.replaceAll("[^A-Za-z0-9_$]", "_");

		ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, packageName + name, null, "java/lang/Object",
				new String[] {internal});

		Map<String, List<String>> methods = new LinkedHashMap<>();
		collect(iface, methods);

		for (Map.Entry<String, List<String>> method : methods.entrySet()) {
			for (String desc : method.getValue()) {
				MethodVisitor body = writer.visitMethod(Opcodes.ACC_PUBLIC, method.getKey(), desc, null, null);
				body.visitCode();
				emitReturn(body, iface, internal, desc, family, packageName, loader);
				body.visitMaxs(0, 0); //Computed
				body.visitEnd();
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
		return writer.toByteArray();
	}

	/** What the stub answers with: itself when the type is one it already is, an empty stub otherwise, else default. */
	private static void emitReturn(MethodVisitor body, Class<?> iface, String internal, String desc, Set<String> family,
			String packageName, ClassLoader loader) {
		Type returns = Type.getReturnType(desc);

		if (returns.getSort() == Type.OBJECT) {
			String wanted = returns.getInternalName();

			//A fluent interface (QuadEmitter's methods return QuadEmitter): handing itself back is the whole
			//implementation, and it is also what a supertype of this stub needs.
			if (wanted.equals(internal) || isSupertype(iface, wanted, loader)) {
				body.visitVarInsn(Opcodes.ALOAD, 0);
				body.visitInsn(Opcodes.ARETURN);
				return;
			}

			if (family.contains(wanted)) {
				String stub = packageName + stubSimpleName(wanted);
				body.visitTypeInsn(Opcodes.NEW, stub);
				body.visitInsn(Opcodes.DUP);
				body.visitMethodInsn(Opcodes.INVOKESPECIAL, stub, "<init>", "()V", false);
				body.visitInsn(Opcodes.ARETURN);
				return;
			}
		}

		defaultReturn(body, returns);
	}

	private static String stubSimpleName(String internal) {
		String simple = internal.substring(internal.lastIndexOf('/') + 1);
		int dollar = simple.lastIndexOf('$');
		if (dollar >= 0) simple = simple.substring(dollar + 1);

		return SIMPLE_NAME + '$' + simple.replaceAll("[^A-Za-z0-9_$]", "_");
	}

	private static boolean isSupertype(Class<?> iface, String wanted, ClassLoader loader) {
		try {
			return Class.forName(wanted.replace('/', '.'), false, loader).isAssignableFrom(iface);
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	/** The value to push before returning, for the calls this placeholder declines to draw. */
	private static void defaultReturn(MethodVisitor body, Type returns) {
		switch (returns.getSort()) {
			case Type.VOID:
				body.visitInsn(Opcodes.RETURN);
				break;
			case Type.BOOLEAN:
			case Type.CHAR:
			case Type.BYTE:
			case Type.SHORT:
			case Type.INT:
				body.visitInsn(Opcodes.ICONST_0);
				body.visitInsn(Opcodes.IRETURN);
				break;
			case Type.LONG:
				body.visitInsn(Opcodes.LCONST_0);
				body.visitInsn(Opcodes.LRETURN);
				break;
			case Type.FLOAT:
				body.visitInsn(Opcodes.FCONST_0);
				body.visitInsn(Opcodes.FRETURN);
				break;
			case Type.DOUBLE:
				body.visitInsn(Opcodes.DCONST_0);
				body.visitInsn(Opcodes.DRETURN);
				break;
			default:
				body.visitInsn(Opcodes.ACONST_NULL);
				body.visitInsn(Opcodes.ARETURN);
				break;
		}
	}

	/** Every abstract method the interface (and the interfaces it extends) declares, by name and descriptor. */
	private static void collect(Class<?> iface, Map<String, List<String>> methods) throws IOException {
		for (Class<?> parent : iface.getInterfaces()) {
			collect(parent, methods);
		}

		try (InputStream in = resource(iface)) {
			if (in == null) throw new IOException("Cannot read the class file of " + iface.getName());

			ClassNode node = new ClassNode();
			new ClassReader(in).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

			for (MethodNode method : node.methods) {
				int skip = Opcodes.ACC_STATIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC;

				//Only abstract instance methods need a body (a default method already comes with one, and a static
				//one is never inherited)
				if ((method.access & Opcodes.ACC_ABSTRACT) == 0 || (method.access & skip) != 0) continue;

				methods.computeIfAbsent(method.name, key -> new ArrayList<>()).add(method.desc);
			}
		}
	}

	private static InputStream resource(Class<?> iface) {
		return iface.getClassLoader().getResourceAsStream(iface.getName().replace('.', '/') + ".class");
	}
}
