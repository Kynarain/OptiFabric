package kynarain.cn.optifabric.mod;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * OptiFine 的 FXAA 是通过它自己的 post chain 实现的,它按老位置
 * {@code assets/minecraft/shaders/post/fxaa_of_2x.json} 去找 - 那是 1.21.6 之前放后处理链的地方。
 *
 * <p>1.21.8 起的 OptiFine 只带新形式的 {@code assets/minecraft/post_effect/fxaa_of_*.json}
 * (那个由游戏自己读取),老位置的那个文件没有跟着搬过去,于是每次资源重载都只留下
 * {@code Resource not found: minecraft:shaders/post/fxaa_of_2x.json},而打开抗锯齿时
 * OptiFine 没有任何后处理链可以跑。
 *
 * <p>这里按 OptiFine 自己的 schema 补写这个文件,内容只引用用户自己那份 OptiFine 里**已经存在**的
 * {@code post/fxaa_of_*.vsh} 与 {@code .fsh} —— 不复制、也不分发 OptiFine 的任何文件。
 */
final class OptifinePostChainFixer {

	private static final String POST = "assets/minecraft/shaders/post/";
	private static final String EFFECT = "assets/minecraft/post_effect/";

	private OptifinePostChainFixer() {
	}

	static void fix(File jar) throws IOException {
		Map<String, byte[]> missing = new LinkedHashMap<>();

		try (ZipFile zip = new ZipFile(jar)) {
			for (String level : new String[] {"of_2x", "of_4x"}) {
				String chain = POST + "fxaa_" + level + ".json";

				if (zip.getEntry(chain) != null) {
					continue; // this build still ships its post chain, leave it alone
				}

				//only the builds that moved FXAA to the new form and still ship its shaders
				if (zip.getEntry(EFFECT + "fxaa_" + level + ".json") == null || zip.getEntry(POST + "fxaa_" + level + ".fsh") == null) {
					continue;
				}

				missing.put(chain, postChain(level).getBytes(StandardCharsets.UTF_8));

				System.out.println("[OptiFabric] Wrote " + chain + " - this OptiFine build asks for it but no longer ships it");
			}
		}

		if (!missing.isEmpty()) {
			add(jar, missing);
		}
	}

	/** OptiFine's own schema, the extras of the 2x pass included. */
	private static String postChain(String level) {
		String program = "minecraft:post/fxaa_" + level;
		String extra = "of_2x".equals(level)
				? ",\n"
						+ "        { \"name\": \"SpanMax\",       \"type\": \"float\",     \"count\": 1,  \"values\": [ 8.0 ] },\n"
						+ "        { \"name\": \"SubPixelShift\", \"type\": \"float\",     \"count\": 1,  \"values\": [ 0.25 ] },\n"
						+ "        { \"name\": \"ReduceMul\",     \"type\": \"float\",     \"count\": 1,  \"values\": [ 0.125 ] }"
				: "";

		return "{\n"
				+ "    \"vertex\": \"" + program + "\",\n"
				+ "    \"fragment\": \"" + program + "\",\n"
				+ "    \"samplers\": [\n"
				+ "        { \"name\": \"InSampler\" }\n"
				+ "    ],\n"
				+ "    \"uniforms\": [\n"
				+ "        { \"name\": \"ProjMat\",       \"type\": \"matrix4x4\", \"count\": 16,"
				+ " \"values\": [ 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0 ] },\n"
				+ "        { \"name\": \"OutSize\",       \"type\": \"float\",     \"count\": 2,  \"values\": [ 1.0, 1.0 ] }"
				+ extra
				+ "\n    ]\n"
				+ "}\n";
	}

	/** Copies the jar and appends the entries: a ZipFile cannot be written to while it is open. */
	private static void add(File jar, Map<String, byte[]> extra) throws IOException {
		Path tmp = jar.toPath().resolveSibling(jar.getName() + ".postchain");

		try (ZipFile zip = new ZipFile(jar); ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp))) {
			for (ZipEntry entry : Collections.list(zip.entries())) {
				if (entry.isDirectory() || extra.containsKey(entry.getName())) {
					continue;
				}

				out.putNextEntry(new ZipEntry(entry.getName()));

				try (InputStream in = zip.getInputStream(entry)) {
					in.transferTo((OutputStream) out);
				}

				out.closeEntry();
			}

			for (Map.Entry<String, byte[]> added : extra.entrySet()) {
				out.putNextEntry(new ZipEntry(added.getKey()));
				out.write(added.getValue());
				out.closeEntry();
			}
		}

		Files.move(tmp, jar.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}
}
