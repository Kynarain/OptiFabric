/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 26.1.2 / Fabric Loader 0.19.x.
 *
 * 26.x is unobfuscated, so this file names the game by its official names and no mappings sit in between.
 * The 1.21.x project next door (v1.21.x/src/main/java/.../mixin/) carries the same two mixins written
 * against yarn names; this is the 26.x counterpart. What changed:
 *   net.minecraft.util.crash.CrashReport         -> net.minecraft.CrashReport
 *   net.minecraft.util.crash.CrashReportSection  -> net.minecraft.CrashReportCategory
 *   CrashReportSection.add(name, value)          -> CrashReportCategory.setDetail(name, value)
 *   CrashReportSection.addStackTrace(StringBuilder) -> CrashReportCategory.getDetails(StringBuilder)
 *   CrashReport.addDetails(StringBuilder)        -> CrashReport.getDetails(StringBuilder)
 * The injected method needs its descriptor spelled out because CrashReport declares getDetails() as well,
 * so a bare name would be ambiguous.
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

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;

import kynarain.cn.optifabric.mod.OptifabricError;
import kynarain.cn.optifabric.mod.OptifabricSetup;
import kynarain.cn.optifabric.mod.OptifineVersion;

@Mixin(CrashReport.class)
abstract class CrashReportMixin {
	private static CrashReportCategory makeSection(CrashReport crash, String name) {
		try {
			return ConstructorUtils.invokeExactConstructor(CrashReportCategory.class, name);
		} catch (ReflectiveOperationException e) {
			return new CrashReportCategory(name);
		}
	}

	@Unique
	private final CrashReportCategory optifine = makeSection((CrashReport) (Object) this, "OptiFabric")
			.setDetail("OptiFine jar designed for", OptifineVersion.minecraftVersion)
			.setDetail("OptiFine jar version", OptifineVersion.version)
			.setDetail("OptiFine jar status", () -> {
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
			.setDetail("OptiFine remapped jar", Optional.ofNullable(OptifabricSetup.optifineRuntimeJar).map(jar -> jar.toString().replace(File.separatorChar, '/')).orElse(null))
			.setDetail("OptiFabric error", () -> {
				if (OptifabricError.hasError()) {
					return OptifabricError.getError();
				} else {
					return "<None>";
				}
			})
		;

	@Inject(method = "getDetails(Ljava/lang/StringBuilder;)V", at = @At("RETURN"))
	private void addStackTrace(StringBuilder builder, CallbackInfo info) {
		optifine.getDetails(builder.append("\n\n"));
	}
}
