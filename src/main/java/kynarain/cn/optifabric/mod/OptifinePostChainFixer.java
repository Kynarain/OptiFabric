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
 * OptiFine 的抗锯齿走的是它自己的 post chain,而它按老位置
 * {@code assets/minecraft/shaders/post/fxaa_of_2x.json} 去找 - 1.21.6 之前后处理链就放在那里。
 *
 * <p>1.21.8 起的构建只带新形式的 {@code assets/minecraft/post_effect/fxaa_of_*.json},老位置的那个
 * 没跟着搬过去:每次资源重载都留下 {@code Resource not found: minecraft:shaders/post/fxaa_of_2x.json},
 * 打开抗锯齿时 OptiFine 手上没有可跑的链。
 *
 * <p>这里做两件事:
 * <ol>
 *   <li>按 OptiFine 自己的 schema 补写那个老式文件,内容只引用用户自己那份 OptiFine 里**已经存在**的
 *       {@code post/fxaa_of_*.vsh} 与 {@code .fsh} —— 不复制、也不分发 OptiFine 的任何文件;</li>
 *   <li>把游戏那条同名的 post effect 拿掉。那两个着色器是写给 OptiFine 自己的链运行器的:它们声明
 *       {@code Projection}/{@code SamplerInfo}/{@code FxaaConfig} 这些 uniform 块,链运行器会按老式
 *       json 里的 {@code ProjMat}/{@code OutSize}/... 填进去,而游戏的后处理管线不按这个形式给,
 *       顶点位置算不出来 -> 第一个 pass 往 swap 里画空 -> 第二个 pass 再把 swap 拷回主画面 ->
 *       整屏变黑。两条路同时开着只会互相打架,所以让 OptiFine 自己的链独占。</li>
 * </ol>
 */
final class OptifinePostChainFixer {

	private static final String POST = "assets/minecraft/shaders/post/";
	private static final String EFFECT = "assets/minecraft/post_effect/";
	private static final String[] LEVELS = {"of_2x", "of_4x"};

	private OptifinePostChainFixer() {
	}

	static void fix(File jar) throws IOException {
		Map<String, byte[]> added = new LinkedHashMap<>();
		Map<String, String> dropped = new LinkedHashMap<>();

		try (ZipFile zip = new ZipFile(jar)) {
			for (String level : LEVELS) {
				String chain = POST + "fxaa_" + level + ".json";
				String effect = EFFECT + "fxaa_" + level + ".json";

				if (zip.getEntry(POST + "fxaa_" + level + ".fsh") == null) {
					continue; // not an FXAA build
				}

				if (zip.getEntry(chain) != null) {
					//this build still ships its own post chain, nothing to write
				} else if (zip.getEntry(effect) != null) {
					added.put(chain, postChain(level).getBytes(StandardCharsets.UTF_8));
					System.out.println("[OptiFabric] Wrote " + chain + " - this OptiFine build asks for it but no longer ships it");
				} else {
					continue; // no chain to be had either way
				}

				if (zip.getEntry(effect) != null) {
					dropped.put(effect, chain);
					System.out.println("[OptiFabric] Dropped " + effect + " - antialiasing runs through OptiFine's own chain (" + chain + ")");
				}
			}
		}

		if (!added.isEmpty() || !dropped.isEmpty()) {
			rewrite(jar, added, dropped);
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

	/** Copies the jar into a new one with the entries added and dropped: a ZipFile cannot be written to while it is open. */
	private static void rewrite(File jar, Map<String, byte[]> added, Map<String, String> dropped) throws IOException {
		Path tmp = jar.toPath().resolveSibling(jar.getName() + ".postchain");

		try (ZipFile zip = new ZipFile(jar); ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(tmp))) {
			for (ZipEntry entry : Collections.list(zip.entries())) {
				if (entry.isDirectory() || dropped.containsKey(entry.getName()) || added.containsKey(entry.getName())) {
					continue;
				}

				out.putNextEntry(new ZipEntry(entry.getName()));

				try (InputStream in = zip.getInputStream(entry)) {
					in.transferTo((OutputStream) out);
				}

				out.closeEntry();
			}

			for (Map.Entry<String, byte[]> entry : added.entrySet()) {
				out.putNextEntry(new ZipEntry(entry.getKey()));
				out.write(entry.getValue());
				out.closeEntry();
			}
		}

		Files.move(tmp, jar.toPath(), StandardCopyOption.REPLACE_EXISTING);
	}
}
