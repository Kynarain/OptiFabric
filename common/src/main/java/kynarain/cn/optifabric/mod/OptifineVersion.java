/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 and 1.21.11 / Fabric Loader 0.19.x.
 *
 * Changes from upstream:
 *   - the current Minecraft version comes from Fabric Loader instead of the launcher's version.json resource;
 *   - Apache Commons IO/FilenameUtils usage replaced with plain JDK code.
 */
package kynarain.cn.optifabric.mod;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipError;
import java.util.zip.ZipException;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import net.fabricmc.loader.api.FabricLoader;

import kynarain.cn.optifabric.util.ASMUtils;
import kynarain.cn.optifabric.util.ZipUtils;

/**
 * Locates the OptiFine jar the user dropped into the mods folder and reads its declared versions.
 */
public class OptifineVersion {
	public static String version;
	public static String minecraftVersion;
	public static JarType jarType;

	public static File findOptifineJar() throws IOException {
		File modsDir = new File(FabricLoader.getInstance().getGameDirectory(), "mods");
		File[] mods = modsDir.listFiles();

		if (mods != null) {
			File optifineJar = null;

			for (File file : mods) {
				if (!file.isDirectory() && hasJarExtension(file.getName()) && !file.getName().startsWith(".") && !file.isHidden()) {
					JarType type = getJarType(file);
					if (type.isError()) {
						jarType = type;
						throw new RuntimeException("An error occurred when trying to find the optifine jar: " + type.name());
					}

					if (type == JarType.OPTIFINE_MOD || type == JarType.OPTIFINE_INSTALLER) {
						if (optifineJar != null) {
							jarType = JarType.DUPLICATED;
							OptifabricError.setError("Please ensure you only have 1 copy of OptiFine in the mods folder!\nFound: %s\n       %s", optifineJar, file);
							throw new FileAlreadyExistsException("Multiple optifine jars: " + file.getName() + " and " + optifineJar.getName());
						}

						jarType = type;
						optifineJar = file;
					}
				}
			}

			if (optifineJar != null) {
				return optifineJar;
			}
		}

		jarType = JarType.MISSING;
		OptifabricError.setError("OptiFabric could not find the OptiFine jar in the mods folder:\n%s\n\n"
				+ "Download OptiFine for Minecraft 1.20.6 and place it in that folder next to this mod.", modsDir);
		throw new FileNotFoundException("Could not find optifine jar");
	}

	private static boolean hasJarExtension(String name) {
		int dot = name.lastIndexOf('.');

		return dot >= 0 && name.regionMatches(true, dot + 1, "jar", 0, 3) && dot == name.length() - 4;
	}

	private static JarType getJarType(File file) throws IOException {
		ClassNode classNode;
		try (JarFile jarFile = new JarFile(file)) {
			JarEntry jarEntry = jarFile.getJarEntry("net/optifine/Config.class"); //F1 (1.14.2) - G9 location
			if (jarEntry == null) {
				jarEntry = jarFile.getJarEntry("notch/net/optifine/Config.class"); //H1 (1.17.1) location
			}
			if (jarEntry == null) {
				return JarType.SOMETHING_ELSE;
			}
			classNode = ASMUtils.readClass(jarFile, jarEntry);
		} catch (ZipException | ZipError e) {
			OptifabricError.setError("The jar at " + file + " is corrupt");
			return JarType.CORRUPT_ZIP;
		}

		for (FieldNode fieldNode : classNode.fields) {
			if ("VERSION".equals(fieldNode.name)) {
				version = (String) fieldNode.value;
			}
			if ("MC_VERSION".equals(fieldNode.name)) {
				minecraftVersion = (String) fieldNode.value;
			}
		}

		if (version == null || version.isEmpty() || minecraftVersion == null || minecraftVersion.isEmpty()) {
			OptifabricError.setError("Unable to find OptiFine version from OptiFine jar at " + file);
			return JarType.INCOMPATIBLE;
		}

		String currentMcVersion = FabricLoader.getInstance().getRawGameVersion();

		if (!currentMcVersion.equals(minecraftVersion)) {
			OptifabricError.setError("This version of OptiFine from %s is not compatible with the current minecraft version\n\nOptifine requires %s you are running %s",
										file, minecraftVersion, currentMcVersion);
			return JarType.INCOMPATIBLE;
		}

		boolean[] isInstaller = new boolean[1];
		ZipUtils.iterateContents(file, (zip, zipEntry) -> {
			if (zipEntry.getName().startsWith("patch/")) {
				isInstaller[0] = true;
				return false;
			} else {
				return true;
			}
		});

		if (isInstaller[0]) {
			return JarType.OPTIFINE_INSTALLER;
		} else {
			return JarType.OPTIFINE_MOD;
		}
	}

	public enum JarType {
		MISSING(true),
		OPTIFINE_MOD(false),
		OPTIFINE_INSTALLER(false),
		INCOMPATIBLE(true),
		CORRUPT_ZIP(true),
		DUPLICATED(true),
		INTERNAL_ERROR(true),
		SOMETHING_ELSE(false);

		private final boolean error;

		JarType(boolean error) {
			this.error = error;
		}

		public boolean isError() {
			return error;
		}
	}
}
