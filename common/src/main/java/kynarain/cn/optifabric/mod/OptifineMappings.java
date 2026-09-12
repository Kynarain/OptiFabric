/*
 * New in the 1.20.6 port of OptiFabric (which is MPL-2.0, see LICENSE.txt). This file has no upstream
 * counterpart.
 *
 * Replaces the hand written "contextual mapping" entries upstream added for a handful of known cases with
 * a check that derives the same information from the mappings themselves.
 */
package kynarain.cn.optifabric.mod;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import net.fabricmc.loader.impl.lib.mappingio.MappingReader;
import net.fabricmc.loader.impl.lib.mappingio.tree.MappingTreeView;
import net.fabricmc.loader.impl.lib.mappingio.tree.MemoryMappingTree;
import net.fabricmc.loader.impl.lib.tinyremapper.IMappingProvider;
import net.fabricmc.loader.impl.lib.tinyremapper.TinyUtils;

/**
 * The mappings bundled into this jar, plus the fixups OptiFine's patches need on top of them.
 */
public final class OptifineMappings {
	public static final String OFFICIAL = "official";
	public static final String INTERMEDIARY = "intermediary";

	/**
	 * Names a recompiled class can declare a Minecraft member under: Minecraft's obfuscated names are short
	 * and lower case, and javac's synthetic outer-instance fields keep their {@code this$0} style names
	 * (OptiFine's patches for ClientWorld$ClientEntityHandler, ModelLoader$BakerImpl and
	 * ChunkBuilder$BuiltChunk$RebuildTask all hit that, and mods shadow those fields).
	 */
	private static final Pattern OBFUSCATED_NAME = Pattern.compile("[a-z][a-z0-9]{0,2}|this\\$\\d+");

	private static final String INT_2_OBJECT_MAP = "Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;";
	private static final String INT_2_OBJECT_OPEN_HASH_MAP = "it/unimi/dsi/fastutil/ints/Int2ObjectOpenHashMap";

	private static MemoryMappingTree mappings;

	private OptifineMappings() {
	}

	/**
	 * Whether this jar carries official -> intermediary mappings, i.e. whether it was built for an obfuscated
	 * release. A build for an unobfuscated release (Minecraft 26.1 and newer) bundles none, because the game's own
	 * names already are the runtime names there and there is nothing to rename.
	 */
	public static boolean hasBundledMappings() {
		return OptifineMappings.class.getResource("/mappings/mappings.tiny") != null;
	}

	public static synchronized MemoryMappingTree get() {
		if (mappings != null) return mappings;

		InputStream bundled = OptifineMappings.class.getResourceAsStream("/mappings/mappings.tiny");

		if (bundled == null) {
			throw new IllegalStateException("OptiFabric is missing its bundled official -> intermediary mappings"
					+ " (/mappings/mappings.tiny). Either the jar was built incorrectly, or it was built for an"
					+ " unobfuscated release (Minecraft 26.1 and newer), which carries none because it needs none -"
					+ " in that case nothing should be asking for them (see OptifineSetup and findFieldRenames).");
		}

		MemoryMappingTree tree = new MemoryMappingTree();

		try (BufferedReader in = new BufferedReader(new InputStreamReader(bundled, StandardCharsets.UTF_8))) {
			MappingReader.read(in, tree);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read the bundled " + OFFICIAL + " -> " + INTERMEDIARY + " mappings", e);
		}

		return mappings = tree;
	}

	public static IMappingProvider provider(String from, String to) {
		return TinyUtils.createMappingProvider(get(), from, to);
	}

	/** A field OptiFine declares under its obfuscated name, and the name (and type) the game has for it. */
	public record FieldRename(String owner, String from, String to, String toDesc) {
	}

	/**
	 * Member mappings are matched by owner, name and descriptor. OptiFine's patches sometimes declare a
	 * Minecraft field with a different name and type than the game has - the particle factories map, for
	 * example, is {@code k : java/util/Map} in OptiFine's patch but {@code field_3835 : Int2ObjectMap} in the
	 * game - so the remapper cannot rename it and it stays obfuscated. Other mods then fail on it:
	 * fabric-registry-sync-v0's {@code @Shadow field_3835} cannot be applied at all, which is exactly the case
	 * upstream hard coded. Renaming alone is not enough either, that shadow is used as an Int2ObjectMap.
	 *
	 * @param patchedClasses the already remapped patched classes, by internal name
	 */
	public static List<FieldRename> findFieldRenames(Map<String, ClassNode> patchedClasses) {
		//Nothing was renamed into intermediary in the first place, so nothing can have been left under an obfuscated
		//name either - and the repair is asked for on every run, including the ones whose jar carries no mappings.
		if (!hasBundledMappings()) return List.of();

		MappingTreeView tree = get();
		int from = tree.getNamespaceId(OFFICIAL);
		int to = tree.getNamespaceId(INTERMEDIARY);
		List<FieldRename> renames = new ArrayList<>();

		for (Map.Entry<String, ClassNode> entry : patchedClasses.entrySet()) {
			MappingTreeView.ClassMappingView mapping = tree.getClass(entry.getKey(), to);
			if (mapping == null) continue;

			Collection<? extends MappingTreeView.FieldMappingView> mappedFields = mapping.getFields();

			for (FieldNode field : entry.getValue().fields) {
				if (!OBFUSCATED_NAME.matcher(field.name).matches()) continue;

				for (MappingTreeView.FieldMappingView candidate : mappedFields) {
					if (!field.name.equals(candidate.getName(from))) continue;

					String realName = candidate.getName(to);

					if (realName != null && !field.name.equals(realName)) {
						renames.add(new FieldRename(entry.getKey(), field.name, realName, candidate.getDesc(to)));
					}

					break;
				}
			}
		}

		return renames;
	}

	/** Renames those field declarations and every reference to them inside the patched classes. */
	public static int applyFieldRenames(Map<String, ClassNode> patchedClasses, List<FieldRename> renames) {
		if (renames.isEmpty()) return 0;

		Map<String, FieldRename> byOwnerAndName = new LinkedHashMap<>(renames.size() * 2);

		for (FieldRename rename : renames) {
			byOwnerAndName.put(rename.owner() + '.' + rename.from(), rename);
		}

		int applied = 0;

		for (ClassNode node : patchedClasses.values()) {
			//A type correction is only safe if every value stored into the field can be converted as well,
			//otherwise OptiFine's own bytecode would no longer verify
			Map<String, Boolean> canRetype = new HashMap<>();

			for (FieldRename rename : byOwnerAndName.values()) {
				if (rename.owner().equals(node.name)) {
					canRetype.put(rename.to(), canRetype(node, rename.from(), rename.toDesc()));
				}
			}

			for (FieldNode field : node.fields) {
				FieldRename rename = byOwnerAndName.get(node.name + '.' + field.name);
				if (rename == null) continue;

				field.name = rename.to();

				if (Boolean.TRUE.equals(canRetype.get(rename.to())) && rename.toDesc() != null) {
					field.desc = rename.toDesc();
					field.signature = null; //the generic signature OptiFine wrote describes the old type
				}

				applied++;
			}

			for (MethodNode method : node.methods) {
				for (AbstractInsnNode insn : method.instructions.toArray()) {
					if (!(insn instanceof FieldInsnNode fieldInsn)) continue;

					FieldRename rename = byOwnerAndName.get(fieldInsn.owner + '.' + fieldInsn.name);

					if (rename == null) {
						rename = inherited(patchedClasses, fieldInsn, byOwnerAndName);
					}

					if (rename == null) continue;

					fieldInsn.name = rename.to();

					if (Boolean.TRUE.equals(canRetype.get(rename.to())) && rename.toDesc() != null) {
						fieldInsn.desc = rename.toDesc();

						if (fieldInsn.getOpcode() == Opcodes.PUTFIELD) {
							retypeStoredValue(fieldInsn);
						}
					}
				}
			}
		}

		return applied;
	}

	/**
	 * The same repair for a reference whose owner is a <em>subclass</em> of the class that declares the field.
	 *
	 * <p>javac writes the subclass as the owner when the receiver has that static type, so
	 * {@code BlockModelRenderer$AmbientOcclusionCalculator} reads the {@code LightCacheOF} field its superclass
	 * declares as {@code getfield class_778$class_780.h}, while the declaration - and therefore the rename table
	 * - lives under {@code class_778$class_10931}. Matching the reference against the declaring class only left
	 * those 19 references under the official name, which is a NoSuchFieldError on the first ambient occlusion
	 * pass: the field is gone from the class that is asked for it and no supertype has it either. Vanilla
	 * classes need no walk - their own reference was remapped by the remapper already.</p>
	 */
	private static FieldRename inherited(Map<String, ClassNode> patchedClasses, FieldInsnNode reference,
			Map<String, FieldRename> byOwnerAndName) {
		Set<String> seen = new HashSet<>();
		Deque<String> queue = new ArrayDeque<>();
		queue.add(reference.owner);

		while (!queue.isEmpty()) {
			String type = queue.poll();
			if (!seen.add(type)) continue;

			FieldRename rename = byOwnerAndName.get(type + '.' + reference.name);
			if (rename != null) return rename;

			ClassNode node = patchedClasses.get(type);
			if (node == null) continue; //a vanilla ancestor keeps the name the game already has

			if (node.superName != null) queue.add(node.superName);
			if (node.interfaces != null) queue.addAll(node.interfaces);
		}

		return null;
	}

	/** Retyping a stored value is only sound when the reference itself is the one the check approved. */

	/** @return whether every value OptiFine stores into the field can be turned into the game's type */
	private static boolean canRetype(ClassNode node, String fieldName, String desc) {
		if (desc == null || !desc.equals(INT_2_OBJECT_MAP)) return false;

		for (MethodNode method : node.methods) {
			for (AbstractInsnNode insn : method.instructions.toArray()) {
				if (insn.getOpcode() != Opcodes.PUTFIELD) continue;

				FieldInsnNode putField = (FieldInsnNode) insn;
				if (!putField.owner.equals(node.name) || !putField.name.equals(fieldName)) continue;

				if (storedType(putField) == null) return false;
			}
		}

		return true;
	}

	/** The type of the plain JDK map OptiFine constructs for the value it stores, or null if it is something else. */
	private static String storedType(FieldInsnNode putField) {
		AbstractInsnNode constructor = putField.getPrevious();
		if (!(constructor instanceof MethodInsnNode ctor) || !"<init>".equals(ctor.name) || !"()V".equals(ctor.desc)) return null;

		AbstractInsnNode dup = ctor.getPrevious();
		AbstractInsnNode created = dup != null ? dup.getPrevious() : null;
		if (!(created instanceof TypeInsnNode type) || type.getOpcode() != Opcodes.NEW) return null;

		return type.desc.startsWith("java/util/") ? type.desc : null;
	}

	/** Turns a stored {@code new HashMap()} into an Int2ObjectOpenHashMap, so the field really is the game's type. */
	private static void retypeStoredValue(FieldInsnNode putField) {
		if (storedType(putField) == null) return;

		MethodInsnNode ctor = (MethodInsnNode) putField.getPrevious();
		TypeInsnNode created = (TypeInsnNode) ctor.getPrevious().getPrevious();
		String was = created.desc;

		created.desc = INT_2_OBJECT_OPEN_HASH_MAP;
		ctor.owner = INT_2_OBJECT_OPEN_HASH_MAP;

		System.out.println("[OptiFabric]   stored value " + was.replace('/', '.') + " -> " + INT_2_OBJECT_OPEN_HASH_MAP.replace('/', '.'));
	}

	public static String describe(List<FieldRename> renames) {
		return renames.stream().map(rename -> rename.owner().replace('/', '.') + '.' + rename.from() + " -> " + rename.to())
				.collect(Collectors.joining(", "));
	}
}
