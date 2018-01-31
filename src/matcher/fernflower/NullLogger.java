package matcher.fernflower;

import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;

/**
 * No-op logger.
 */
public class NullLogger extends IFernflowerLogger {
	public static NullLogger INSTANCE = new NullLogger();

	private NullLogger(){}

	@Override
	public void writeMessage(String message, Severity severity) {
		if (severity == Severity.WARN){
			System.out.println(message);
		}
	}

	@Override
	public void writeMessage(String message, Severity severity, Throwable t) {
		if (severity == Severity.WARN){
			System.out.println(message);
			t.printStackTrace();
		}
	}
}
