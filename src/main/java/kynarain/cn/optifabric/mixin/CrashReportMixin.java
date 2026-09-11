/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 / Fabric Loader 0.19.x.
 */

package kynarain.cn.optifabric.mixin;

import java.io.File;
import java.util.Optional;

import org.apache.commons.lang3.reflect.ConstructorUtils;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;

import kynarain.cn.optifabric.mod.OptifabricError;
import kynarain.cn.optifabric.mod.OptifabricSetup;
import kynarain.cn.optifabric.mod.OptifineVersion;

@Mixin(CrashReport.class)
abstract class CrashReportMixin {
	private static CrashReportSection makeSection(CrashReport crash, String name) {
		try {
			return ConstructorUtils.invokeExactConstructor(CrashReportSection.class, name);
		} catch (ReflectiveOperationException e) {
			return new CrashReportSection(name);
		}
	}

	@Unique
	private final CrashReportSection optifine = makeSection((CrashReport) (Object) this, "OptiFabric")
			.add("OptiFine jar designed for", OptifineVersion.minecraftVersion)
			.add("OptiFine jar version", OptifineVersion.version)
			.add("OptiFine jar status", () -> {
				if (OptifineVersion.jarType != null) {
					switch (OptifineVersion.jarType) {
					case MISSING:
						return "Not found";

					case CORRUPT_ZIP:
						return "Found corrupt jar whilst searching";

					case INCOMPATIBLE:
						return "Incompatible with the current OptiFabric";

					case INTERNAL_ERROR:
						return "Error whilst searching";

					case DUPLICATED:
						return "Multiple valid jars found";

					case OPTIFINE_INSTALLER:
						return "Valid OptiFine installer";

					case OPTIFINE_MOD:
						return "Valid OptiFine mod";

					case SOMETHING_ELSE:
					default:
						return "Unexpected state: " + OptifineVersion.jarType;
					}
				} else {
					return "Unsearched";
				}
			})
			.add("OptiFine remapped jar", Optional.ofNullable(OptifabricSetup.optifineRuntimeJar).map(jar -> jar.toString().replace(File.separatorChar, '/')).orElse(null))
			.add("OptiFabric error", () -> {
				if (OptifabricError.hasError()) {
					return OptifabricError.getError();
				} else {
					return "<None>";
				}
			})
		;

	@Inject(method = "addStackTrace", at = @At("RETURN"))
	private void addStackTrace(StringBuilder builder, CallbackInfo info) {
		optifine.addStackTrace(builder.append("\n\n"));
	}
}
