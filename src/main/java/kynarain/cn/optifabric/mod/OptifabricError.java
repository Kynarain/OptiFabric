/*
 * Ported from OptiFabric (https://github.com/Chocohead/OptiFabric), MPL-2.0.
 * Adapted for Minecraft 1.20.6 / Fabric Loader 0.19.x.
 */
package kynarain.cn.optifabric.mod;

import java.io.PrintWriter;
import java.io.StringWriter;

public class OptifabricError {
	private static String error;
	private static String stack;

	public static boolean hasError() {
		return error != null;
	}

	public static String getError() {
		return error;
	}

	public static void setError(String error) {
		OptifabricError.error = error;
	}

	public static void setError(Throwable t, String error) {
		OptifabricError.error = error;
		logError(t);
	}

	public static void setError(String error, Object... args) {
		OptifabricError.error = String.format(error, args);
	}

	public static void logError(Throwable t) {
		StringWriter error = new StringWriter();
		t.printStackTrace(new PrintWriter(error));
		stack = error.toString();
	}

	public static String getErrorLog() {
		return stack;
	}
}
