/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 and 1.21.11 / Fabric Loader 0.19.x.
 */

package kynarain.cn.optifabric.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.fabricmc.loader.impl.lib.tinyremapper.IMappingProvider.Member;

public class RemappingUtils {

	private static final MappingResolver RESOLVER = FabricLoader.getInstance().getMappingResolver();
	private static final String INTERMEDIARY = "intermediary";
	private static final String OFFICIAL = "official";
	private static final Pattern CLASS_FINDER = Pattern.compile("Lnet\\/minecraft\\/([^;]+);");

	/**
	 * Whether the game this runs on ships unobfuscated, i.e. its own names are the runtime names.
	 *
	 * <p>Fabric Loader reports "official" for those releases (Minecraft 26.1 and newer publish only the empty
	 * placeholder intermediary 0.0.0, which leaves {@code MappingConfiguration.hasAnyMappings()} false, so
	 * {@code computeRuntimeNamespace()} never enters its intermediary branch). Code that would otherwise ask the
	 * resolver to translate an intermediary id has to name the game directly there - and every intermediary id
	 * it could have used names a class that does not exist.</p>
	 */
	public static boolean hasOfficialNames() {
		return OFFICIAL.equals(RESOLVER.getCurrentRuntimeNamespace());
	}

	public static boolean hasClassName(String className) {
		if (!INTERMEDIARY.equals(RESOLVER.getCurrentRuntimeNamespace())) {
			className = fromIntermediaryDot(className);
		} else {
			className = qualified(className);
		}

		return !className.equals(RESOLVER.unmapClassName("official", className));
	}

	public static String getClassName(String className) {
		return fromIntermediaryDot(className).replace('.', '/');
	}

	/**
	 * An intermediary id is a bare name ({@code class_437}), which is what the {@code net.minecraft.} prefix is
	 * for. A name that already is a full path is not one, and prefixing it produces nonsense that the resolver
	 * rejects outright:
	 *
	 * <pre>IllegalArgumentException: Class names must be provided in dot format:
	 * net.minecraft.net/minecraft/client/renderer/chunk/SectionCompiler</pre>
	 *
	 * <p>On the unobfuscated line every name arrives in that second shape, because there are no short ids to
	 * use - which is how a fixer registration that is perfectly ordinary on 1.21.x took the whole pipeline down
	 * with an ExceptionInInitializerError before a single class was patched.</p>
	 */
	private static String qualified(String owner) {
		if (owner.indexOf('/') >= 0 || owner.startsWith("net.minecraft.")) return owner;

		return "net.minecraft." + owner;
	}

	private static String fromIntermediaryDot(String className) {
		return RESOLVER.mapClassName(INTERMEDIARY, qualified(className).replace('/', '.'));
	}

	public static Member mapMethod(String owner, String name, String desc) {
		return new Member(getClassName(owner), getMethodName(owner, name, desc), mapMethodDescriptor(desc));
	}

	public static String getMethodName(String owner, String methodName, String desc) {
		return RESOLVER.mapMethodName(INTERMEDIARY, qualified(owner).replace('/', '.'), methodName, desc);
	}

	public static String mapMethodDescriptor(String desc) {
		StringBuffer buf = new StringBuffer();

		Matcher matcher = CLASS_FINDER.matcher(desc);
		while (matcher.find()) {
			matcher.appendReplacement(buf, Matcher.quoteReplacement('L' + mapDescriptorClass(matcher.group(1)) + ';'));
		}

		return matcher.appendTail(buf).toString();
	}

	/**
	 * Maps the fragment {@link #CLASS_FINDER} captured, which is what follows {@code Lnet/minecraft/} and so has
	 * that prefix stripped by the pattern itself: a bare intermediary id on the 1.21.x line
	 * ({@code class_1920}) and a path fragment on the unobfuscated one ({@code core/SectionPos}). The prefix goes
	 * back on either way - {@link #qualified} cannot be used here, because it treats anything containing a
	 * slash as already complete and would turn {@code core/SectionPos} into {@code Lcore/SectionPos;}.
	 */
	private static String mapDescriptorClass(String path) {
		return RESOLVER.mapClassName(INTERMEDIARY, "net.minecraft." + path.replace('/', '.')).replace('.', '/');
	}

	public static String mapFieldName(String owner, String name, String desc) {
		return RESOLVER.mapFieldName(INTERMEDIARY, qualified(owner).replace('/', '.'), name, desc);
	}
}
