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
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import kynarain.cn.optifabric.util.ZipUtils;

public class OptifineJarFixer {
	private static final String POST_EFFECT = "assets/minecraft/post_effect/";
	private static final String SCREENQUAD = "minecraft:core/screenquad";

	private static final Pattern PROGRAM_PASS = Pattern.compile("\"program\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern VERTEX_SHADER = Pattern.compile("\"vertex_shader\"\\s*:\\s*\"([^\"]+)\"");

	/** Rewrites the two kinds of entry described above, in place. */
	public static void fix(File jar, Path minecraftJar) throws IOException {
		//Only the game jar is held open here: the jar being rewritten must stay untouched, or Windows refuses to
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
