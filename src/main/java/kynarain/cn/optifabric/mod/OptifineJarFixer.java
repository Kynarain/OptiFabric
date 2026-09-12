/*
 * New in the 1.21.x series of this port (which is MPL-2.0, see LICENSE.txt). This file has no upstream
 * counterpart.
 *
 * Repairs what OptiFine's own jar ships for the release it is being used on. This runs on the remapped jar the
 * pipeline has just written, so the class path the game sees already carries the result.
 *
 * 1. assets/minecraft/post_effect/fxaa_of_{2x,4x}.json - the post effect OptiFine applies its FXAA with, in the
 *    format the game has since 1.21.6. Two of its preview builds write that file in a shape their own release
 *    cannot read:
 *
 *      1.21.6 / 1.21.7: the passes carry the key "program":
 *        JsonSyntaxException: No key fragment_shader in MapLike[{"program":"minecraft:post/blit", ...}]
 *        Failed to parse post chain at minecraft:post_effect/fxaa_of_2x.json
 *
 *      1.21.9: the keys are right, but the blit pass asks for "minecraft:post/blit" as the *vertex* shader and
 *        the game only ships post/blit.fsh from 1.21.9 on - its vertex stage is core/screenquad, which is what
 *        the game's own post effects use there:
 *        Couldn't find source for VERTEX shader (minecraft:post/blit)
 *        Couldn't compile pipeline minecraft:fxaa_of_4x/1: vertex shader minecraft:post/blit was invalid
 *
 *    Either way OptiFine's shader initialization fails and the shaderpack the user selected is never loaded at
 *    all ("[Shaders] No shaderpack loaded."), which is where "shaders do nothing on 1.21.9" comes from.
 *
 * 2. net/optifine/shaders/Shaders.class - the 1.21.6 and 1.21.7 preview builds (J6_pre3, J6_pre7) cancel the
 *    load unconditionally, right before it happens:
 *
 *      String packName = shadersConfig.getProperty(...);   // iconst_1; istore_2   <- these two
 *      if (cancelled) { ... }                              // iload_2; ifne ...    <- the check
 *
 *    The bytecode sets cancelled = true and then skips getShaderPack() for good, so *no* shaderpack can be
 *    loaded on those releases whatever the user picks - the settings UI still offers them and silently does
 *    nothing. The 1.21.8 preview and everything later read the flag the two checks above set instead, which is
 *    the shape this fixer restores by dropping those two instructions.
 */
package kynarain.cn.optifabric.mod;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import kynarain.cn.optifabric.util.ZipUtils;

public class OptifineJarFixer {
	private static final String POST_EFFECT = "assets/minecraft/post_effect/";
	private static final String SCREENQUAD = "minecraft:core/screenquad";

	private static final Pattern PROGRAM_PASS = Pattern.compile("\"program\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern VERTEX_SHADER = Pattern.compile("\"vertex_shader\"\\s*:\\s*\"([^\"]+)\"");

	/** A pass that copies one target into another through the game's own post/blit program. */
	private static final Pattern BLIT_PASS = Pattern.compile(
			"\\{\\s*\"vertex_shader\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"fragment_shader\"\\s*:\\s*\"minecraft:post/blit\""
					+ "\\s*,\\s*\"inputs\"\\s*:\\s*(\\[[^\\[\\]]*\\])\\s*,\\s*\"output\"\\s*:\\s*\"([^\"]+)\"\\s*\\}");

	/** Rewrites the two kinds of entry described above, in place. */
	public static void fix(File jar, Path minecraftJar) throws IOException {		//Only the game jar is held open here: the jar being rewritten must stay untouched, or Windows refuses to
		//replace it half way through.
		try (ZipFile minecraft = openQuietly(minecraftJar)) {
			ZipUtils.transformInPlace(jar, (zip, entry) -> {
				String name = entry.getName();

				if (name.startsWith(POST_EFFECT) && name.endsWith(".json")) {
					byte[] fixed = fixPostEffect(zip, minecraft, entry);
					return fixed != null ? new ByteArrayInputStream(fixed) : zip.getInputStream(entry);
				}

				if ("net/optifine/shaders/Shaders.class".equals(name)) {
					byte[] fixed = enableShaderPackLoad(zip, entry);
					return fixed != null ? new ByteArrayInputStream(fixed) : zip.getInputStream(entry);
				}

				if ("net/optifine/shaders/SimpleShaderTexture.class".equals(name)) {
					byte[] fixed = createGpuTexture(zip, entry);
					return fixed != null ? new ByteArrayInputStream(fixed) : zip.getInputStream(entry);
				}

				return zip.getInputStream(entry);
			});
		}
	}

	/** The game's own post effects, and OptiFine's, in the shape the release's parser expects. */
	private static byte[] fixPostEffect(ZipFile optifine, ZipFile minecraft, ZipEntry entry) throws IOException {
		String text;

		try (InputStream in = optifine.getInputStream(entry)) {
			text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		String fixed = text;
		boolean changed = false;

		//1.21.6 / 1.21.7: "program": "<id>" -> "vertex_shader"/"fragment_shader", which is what those parsers read
		if (fixed.contains("\"program\"")) {
			Matcher passes = PROGRAM_PASS.matcher(fixed);
			StringBuffer out = new StringBuffer();

			while (passes.find()) {
				String id = passes.group(1);
				passes.appendReplacement(out, Matcher.quoteReplacement("\"vertex_shader\": \"" + id + "\", \"fragment_shader\": \"" + id + "\""));
				changed = true;
			}

			passes.appendTail(out);
			fixed = out.toString();
		}

		//the blit pass needs the uniforms the game's own blit passes carry - see BLIT_PASS
		Matcher blit = BLIT_PASS.matcher(fixed);
		StringBuffer blitted = new StringBuffer();

		while (blit.find()) {
			String vertex = blit.group(1);
			String inputs = blit.group(2);
			String output = blit.group(3);

			if (!SCREENQUAD.equals(vertex) && !hasShaderSource(optifine, minecraft, vertex, ".vsh")) {
				System.out.println("[OptiFabric] " + entry.getName() + " asks for the vertex shader " + vertex + ", which this release"
						+ " does not have; using " + SCREENQUAD + " as the game's own post effects do");
				vertex = SCREENQUAD;
			}

			//post/blit does fragColor = texture(InSampler, texCoord) * ColorModulate, and ColorModulate comes from
			//the BlitConfig block. Without it the uniform is all zeroes and the whole picture is multiplied by
			//zero - the screen goes black as soon as the user turns antialiasing on. The game's own passes carry
			//it with the identity value, so this one does too.
			blit.appendReplacement(blitted, Matcher.quoteReplacement(
					"{\n                \"vertex_shader\": \"" + vertex + "\",\n"
							+ "                \"fragment_shader\": \"minecraft:post/blit\",\n"
							+ "                \"inputs\": " + inputs.trim() + ",\n"
							+ "                \"uniforms\": { \"BlitConfig\": [ { \"name\": \"ColorModulate\", \"type\": \"vec4\","
							+ " \"value\": [ 1.0, 1.0, 1.0, 1.0 ] } ] },\n"
							+ "                \"output\": \"" + output + "\"\n            }"));
			changed = true;
		}

		blit.appendTail(blitted);
		fixed = blitted.toString();

		//1.21.9 and later: no source for the vertex stage of post/blit, the game uses core/screenquad there
		Matcher vertex = VERTEX_SHADER.matcher(fixed);
		StringBuffer out = new StringBuffer();

		while (vertex.find()) {
			String id = vertex.group(1);

			if (SCREENQUAD.equals(id) || hasShaderSource(optifine, minecraft, id, ".vsh")) {
				vertex.appendReplacement(out, Matcher.quoteReplacement(vertex.group()));
				continue;
			}

			vertex.appendReplacement(out, Matcher.quoteReplacement("\"vertex_shader\": \"" + SCREENQUAD + "\""));
			changed = true;

			System.out.println("[OptiFabric] " + entry.getName() + " asks for the vertex shader " + id + ", which this release"
					+ " does not have; using " + SCREENQUAD + " as the game's own post effects do");
		}

		vertex.appendTail(out);
		fixed = out.toString();

		if (!changed) return null;

		System.out.println("[OptiFabric] Repaired " + entry.getName() + " (OptiFine's copy does not parse or compile on this release)");
		return fixed.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Drops the unconditional {@code cancelled = true} OptiFine's 1.21.6 / 1.21.7 builds put in front of the
	 * shaderpack load, so the flag the two checks above set is the one that decides again.
	 */
	private static byte[] enableShaderPackLoad(ZipFile zip, ZipEntry entry) throws IOException {
		ClassNode node = new ClassNode();

		try (InputStream in = zip.getInputStream(entry)) {
			new ClassReader(in).accept(node, ClassReader.EXPAND_FRAMES);
		}

		boolean changed = false;

		for (MethodNode method : node.methods) {
			if (!"loadShaderPack".equals(method.name) || !"()V".equals(method.desc)) continue;

			//Labels and line numbers sit between the instructions in the tree, so only real instructions are compared
			java.util.List<AbstractInsnNode> instructions = new java.util.ArrayList<>();

			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (insn.getOpcode() >= 0) instructions.add(insn);
			}

			for (int i = 0; i + 3 < instructions.size(); i++) {
				//ICONST_1, ISTORE n, ILOAD n, IFNE - "cancelled = true; if (cancelled) ..." with nothing in between
				if (!(instructions.get(i) instanceof InsnNode push) || push.getOpcode() != Opcodes.ICONST_1) continue;
				if (!(instructions.get(i + 1) instanceof VarInsnNode store) || store.getOpcode() != Opcodes.ISTORE) continue;
				if (!(instructions.get(i + 2) instanceof VarInsnNode load) || load.getOpcode() != Opcodes.ILOAD || load.var != store.var) continue;
				if (!(instructions.get(i + 3) instanceof JumpInsnNode jump) || jump.getOpcode() != Opcodes.IFNE) continue;

				method.instructions.remove(instructions.get(i + 1));
				method.instructions.remove(instructions.get(i));
				changed = true;

				System.out.println("[OptiFabric] OptiFine's build for this release cancels the shaderpack load unconditionally"
						+ " (Shaders.loadShaderPack sets cancelled = true right before checking it); the load works again");

				break;
			}
		}

		if (!changed) return null;

		ClassWriter writer = new ClassWriter(0); //only whole instructions were dropped, so the frames still fit
		node.accept(writer);
		return writer.toByteArray();
	}

	/**
	 * Gives SimpleShaderTexture the texture the game's API expects it to have.
	 *
	 * <p>The 1.21.6 and 1.21.7 preview builds load a shaderpack's custom textures with the pre-1.21.6 GL API:
	 * {@code loadTexture} asks {@code AbstractTexture.getGlTextureId()} - which since 1.21.6 reads
	 * {@code this.texture.getGlTextureId()} on a {@code GpuTexture} that the subclass is supposed to have
	 * created - and then hands that id to {@code TextureUtils.prepareImage} and
	 * {@code NativeImage.uploadTextureSub}. Nothing creates the texture, so the first custom texture of the
	 * pack ends the game with</p>
	 *
	 * <pre>
	 * NullPointerException: Cannot invoke "com.mojang.blaze3d.textures.GpuTexture.getGlTextureId()"
	 *   because "this.field_56974" is null
	 *   at net.minecraft.class_1044.getGlTextureId  &lt;- net.optifine.shaders.SimpleShaderTexture.loadTexture
	 * </pre>
	 *
	 * <p>The 1.21.8 preview (and everything after) does it the way the game does, and those calls exist
	 * unchanged in 1.21.6 - {@code GpuDevice.createTexture(String, int, TextureFormat, int, int, int, int)},
	 * {@code GpuDevice.createTextureView(GpuTexture)}, {@code NativeImage.method_4307/method_4323} and the two
	 * inherited fields. So the old sequence</p>
	 *
	 * <pre>this.getGlTextureId(); image.getWidth(); image.getHeight(); TextureUtils.prepareImage(id, w, h)</pre>
	 *
	 * <p>- which is stack neutral - is replaced by that creation, taken constant for constant from the 1.21.8
	 * build (usage 5, RGBA8, one layer, one mip level, then the texture view). The upload call that follows is
	 * left alone: it works on the image, not on an id.</p>
	 */
	private static byte[] createGpuTexture(ZipFile zip, ZipEntry entry) throws IOException {
		ClassNode node = new ClassNode();

		try (InputStream in = zip.getInputStream(entry)) {
			new ClassReader(in).accept(node, ClassReader.EXPAND_FRAMES);
		}

		boolean changed = false;

		for (MethodNode method : node.methods) {
			if (!"loadTexture".equals(method.name)) continue;

			MethodInsnNode prepare = null;

			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (insn instanceof MethodInsnNode call && "prepareImage".equals(call.name)) prepare = call;
			}

			if (prepare == null) continue; //already the new shape, or not this build at all

			MethodInsnNode id = null;

			for (AbstractInsnNode insn = prepare.getPrevious(); insn != null; insn = insn.getPrevious()) {
				if (insn instanceof MethodInsnNode call && "getGlTextureId".equals(call.name)) {
					id = call;
					break;
				}
			}

			if (id == null) continue;

			AbstractInsnNode start = id.getPrevious(); //the ALOAD 0 of the receiver
			if (start == null) continue;

			method.instructions.insertBefore(start, creation());

			for (AbstractInsnNode insn = start; insn != null; ) {
				AbstractInsnNode next = insn.getNext();
				boolean last = insn == prepare;

				method.instructions.remove(insn);
				if (last) break;

				insn = next;
			}

			changed = true;

			System.out.println("[OptiFabric] OptiFine's build for this release loads its custom shader textures through"
					+ " the pre-1.21.6 GL API (getGlTextureId on a texture nothing created, then TextureUtils.prepareImage);"
					+ " creating the GpuTexture the way the game does instead");
		}

		if (!changed) return null;

		ClassWriter writer = new ClassWriter(0);
		node.accept(writer);
		return writer.toByteArray();
	}

	/** The creation block of the 1.21.8 build, instruction for instruction. */
	private static InsnList creation() {
		InsnList list = new InsnList();
		String device = "com/mojang/blaze3d/systems/GpuDevice";
		String texture = "com/mojang/blaze3d/textures/GpuTexture";
		String format = "com/mojang/blaze3d/textures/TextureFormat";

		list.add(new VarInsnNode(Opcodes.ALOAD, 0));
		list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/mojang/blaze3d/systems/RenderSystem", "getDevice",
				"()L" + device + ";", false));
		list.add(new VarInsnNode(Opcodes.ALOAD, 0));
		list.add(new FieldInsnNode(Opcodes.GETFIELD, "net/optifine/shaders/SimpleShaderTexture", "texturePath", "Ljava/lang/String;"));
		list.add(new InsnNode(Opcodes.ICONST_5)); //usage
		list.add(new FieldInsnNode(Opcodes.GETSTATIC, format, "RGBA8", 'L' + format + ';'));
		list.add(new VarInsnNode(Opcodes.ALOAD, 3));
		list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/class_1011", "method_4307", "()I", false));
		list.add(new VarInsnNode(Opcodes.ALOAD, 3));
		list.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "net/minecraft/class_1011", "method_4323", "()I", false));
		list.add(new InsnNode(Opcodes.ICONST_1)); //layers
		list.add(new InsnNode(Opcodes.ICONST_1)); //mip levels
		list.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, device, "createTexture",
				"(Ljava/lang/String;IL" + format + ";IIII)L" + texture + ";", true));
		list.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/class_1044", "field_56974", 'L' + texture + ';'));

		list.add(new VarInsnNode(Opcodes.ALOAD, 0));
		list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/mojang/blaze3d/systems/RenderSystem", "getDevice",
				"()L" + device + ";", false));
		list.add(new VarInsnNode(Opcodes.ALOAD, 0));
		list.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/class_1044", "field_56974", 'L' + texture + ';'));
		list.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, device, "createTextureView", "(L" + texture + ";)Lcom/mojang/blaze3d/textures/GpuTextureView;", true));
		list.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/class_1044", "field_60597", "Lcom/mojang/blaze3d/textures/GpuTextureView;"));

		return list;
	}

	/** Whether a shader program of that id has a source of that stage in OptiFine's jar or in the game's. */
	private static boolean hasShaderSource(ZipFile optifine, ZipFile minecraft, String id, String extension) {
		int colon = id.indexOf(':');
		String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
		String path = colon < 0 ? id : id.substring(colon + 1);
		String entry = "assets/" + namespace + "/shaders/" + path + extension;

		if (optifine.getEntry(entry) != null) return true;

		return minecraft != null && minecraft.getEntry(entry) != null;
	}

	private static ZipFile openQuietly(Path jar) {
		try {
			return jar != null && Files.isRegularFile(jar) ? new ZipFile(jar.toFile()) : null;
		} catch (IOException e) {
			System.err.println("[OptiFabric] Could not read " + jar + " while repairing OptiFine: " + e);
			return null;
		}
	}
}
