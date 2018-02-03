package matcher;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.PathConverter;
import joptsimple.util.PathProperties;
import matcher.classifier.ClassifierLevel;
import matcher.classifier.FieldClassifier;
import matcher.classifier.MethodClassifier;
import matcher.classifier.RankResult;
import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.MatchType;
import matcher.type.MethodInstance;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleConsumer;

/**
 * Created by Thiakil on 8/01/2018.
 */
public class ConsoleMatcher {
	public static void main(String[] args) {
		final OptionParser parser = new OptionParser();
		PathConverter converterReadable = new PathConverter(PathProperties.READABLE);
		PathConverter converterWritable = new PathConverter();
		OptionSpec<Path> inFileLOpt = parser.accepts("in-left", "Left input file").withRequiredArg().withValuesConvertedBy(converterReadable).required();
		OptionSpec<Path> inFileROpt = parser.accepts("in-right", "Right input file").withRequiredArg().withValuesConvertedBy(converterReadable).required();
		OptionSpec<Path> outFileOpt = parser.accepts("out", "Output file (matches file)").withRequiredArg().withValuesConvertedBy(converterWritable).required();
		OptionSpec<Path> seedFileOpt = parser.accepts("seed", "matches base file to load").withRequiredArg().withValuesConvertedBy(converterReadable);
		OptionSpec help = parser.accepts("help").forHelp();
		OptionSpec allowRematches = parser.accepts("allow-rematch");

		final OptionSet options = parser.parse(args);

		if (options.hasArgument(help) || options.valuesOf(inFileLOpt).size() == 0){
			try {
				parser.printHelpOn(System.out);
			} catch (IOException e){
				e.printStackTrace();
				System.exit(1);
			}
			System.exit(0);
			return;
		}

		Path outFile = options.valueOf(outFileOpt);
		try {
			Writer writer = Files.newBufferedWriter(outFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			writer.close();
		} catch (IOException e){
			System.err.println("Could not save matches");
			System.exit(1);
		}

		ClassEnvironment env;

		Matcher.init();

		env = new ClassEnvironment();
		Matcher matcher = new Matcher(env);
		ProjectConfig config = new ProjectConfig();

		System.out.println("Left:");
		options.valuesOf(inFileLOpt).forEach(p->{
			System.out.println(p.getFileName());
			config.getPathsA().add(p);
		});
		System.out.println("Right:");
		options.valuesOf(inFileROpt).forEach(p->{
			System.out.println(p.getFileName());
			config.getPathsB().add(p);
		});

		DoubleConsumer simpleProgressListener = v->{
			System.out.printf("%.2f", v*100.0);
			System.out.print("%          \r");
		};

		System.out.println("Loading inputs");
		matcher.init(config, simpleProgressListener);
		matcher.setAllowRematches(options.hasArgument(allowRematches));

		if (options.has(seedFileOpt)){
			Path seedFile = options.valueOf(seedFileOpt);
			System.out.println("Reading seed matches file");
			matcher.readMatches(seedFile, null, simpleProgressListener);
		}

		System.out.println("Matching perfect enums");
		matcher.autoMatchPerfectEnums(simpleProgressListener);

		System.out.println("Performing initial match (0.9, 0.045)");
		//matcher.autoMatchAll(simpleProgressListener);
		autoMatchAll(env, matcher, 0.9, 0.045, simpleProgressListener);

		System.out.println("Checking duds");
		matcher.unMatchDuds(0.9, 0.05, simpleProgressListener);

		System.out.println("Attempting to match perfect members");
		env.getClassesA().forEach(cls->matchPerfectMembers(cls, matcher, env));

		System.out.println("Propagating names");
		matcher.propagateNames(simpleProgressListener);

		int pass = 2;
		double threshold = 0.9;
		double relThreshold = 0.05;
		String status;
		do {
			threshold-=0.1;
			relThreshold += 0.01;
			do {
				saveMatches(matcher, outFile);
				System.out.printf("Performing pass %d at %.2f & %.3f\r\n", pass++, threshold, relThreshold);
				status = matcher.getStringStatusSummary(true);
				//matcher.autoMatchAll(simpleProgressListener);
				autoMatchAll(env, matcher, threshold, relThreshold, simpleProgressListener);
				System.out.println("Checking duds");
				matcher.unMatchDuds(threshold, relThreshold, simpleProgressListener);
				System.out.println("Attempting to match perfect members");
				env.getClassesA().forEach(cls->matchPerfectMembers(cls, matcher, env));
				System.out.println("Propagating names");
				matcher.propagateNames(simpleProgressListener);
				System.out.println();
			} while (!status.equals(matcher.getStringStatusSummary(true)));
		} while (threshold > 0.60);

		System.out.println(matcher.getStringStatusSummary(true));
		System.out.printf("Performed %d passes\n", pass-1);

		saveMatches(matcher, outFile);
	}

	private static void saveMatches(Matcher matcher, Path outFile){
		try {
			matcher.saveMatches(outFile);
		} catch (IOException e){
			System.err.println("Could not save matches: "+e.getMessage());
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	private static boolean canMatchPerfectMembers(ClassInstance cls) {
		if (cls != null && cls.hasMatch() && cls.getName().equals("aec")){
			System.out.printf("aec: %b, %b\n", hasUnmatchedMembers(cls), hasUnmatchedMembers(cls.getMatch()));
		}
		return cls != null && cls.hasMatch() && hasUnmatchedMembers(cls) && hasUnmatchedMembers(cls.getMatch());
	}

	private static boolean hasUnmatchedMembers(ClassInstance cls) {
		if (cls == null){
			throw new IllegalArgumentException("cls is null");
		}
		if (cls.getMethods() == null){
			throw new IllegalStateException("methods are null somehow, "+cls.toString());
		}
		for (MethodInstance m : cls.getMethods()) {
			if (!m.hasMatch()) return true;
		}

		for (FieldInstance m : cls.getFields()) {
			if (!m.hasMatch()) return true;
		}

		return false;
	}

	private static void matchPerfectMembers(ClassInstance clsA, Matcher matcher, ClassEnvironment env) {
		if (!canMatchPerfectMembers(clsA)) return;

		ClassInstance clsB = clsA.getMatch();
		final double minScore = 1 - 1e-6;
		Map<MethodInstance, MethodInstance> matchedMethods = new IdentityHashMap<>();
		int matchedMethodsCount = 0;
		int matchedfieldsCount = 0;

		for (MethodInstance m : clsA.getMethods()) {
			if (m.hasMatch()) continue;

			List<RankResult<MethodInstance>> results = MethodClassifier.rank(m, clsB.getMethods(), ClassifierLevel.Full, env);

			if (!results.isEmpty() && results.get(0).getScore() >= minScore && (results.size() == 1 || results.get(1).getScore() < minScore)) {
				MethodInstance match = results.get(0).getSubject();
				MethodInstance prev = matchedMethods.putIfAbsent(match, m);
				if (prev != null) matchedMethods.put(match, null);
			} else if (clsA.getName().equals("aec") && m.getName().equals("p")){
				System.out.println("getimeschange didnt match");
				System.out.printf("score %f, size %d, nextscore %f\n", results.get(0).getScore(), results.size(), results.size() > 0 ? results.get(1).getScore() : -1);
				System.exit(0);
			}
		}

		for (Map.Entry<MethodInstance, MethodInstance> entry : matchedMethods.entrySet()) {
			if (entry.getValue() == null) continue;

			matcher.match(entry.getValue(), entry.getKey());
			matchedMethodsCount++;
		}

		Map<FieldInstance, FieldInstance> matchedFields = new IdentityHashMap<>();

		for (FieldInstance m : clsA.getFields()) {
			if (m.hasMatch()) continue;

			List<RankResult<FieldInstance>> results = FieldClassifier.rank(m, clsB.getFields(), ClassifierLevel.Full, env);

			if (!results.isEmpty() && results.get(0).getScore() >= minScore && (results.size() == 1 || results.get(1).getScore() < minScore)) {
				FieldInstance match = results.get(0).getSubject();
				FieldInstance prev = matchedFields.putIfAbsent(match, m);
				if (prev != null) matchedFields.put(match, null);
			}
		}

		for (Map.Entry<FieldInstance, FieldInstance> entry : matchedFields.entrySet()) {
			if (entry.getValue() == null) continue;

			matcher.match(entry.getValue(), entry.getKey());
			matchedfieldsCount++;
		}

		//System.out.printf("Matched %d methods, %d fields\n", matchedMethodsCount, matchedfieldsCount);

	}

	private static void autoMatchAll(ClassEnvironment env, Matcher matcher, double absThreshold, double relThreshold, DoubleConsumer progressReceiver) {
		if (matcher.autoMatchClasses(ClassifierLevel.Initial, absThreshold, relThreshold, progressReceiver)) {
			matcher.autoMatchClasses(ClassifierLevel.Initial, absThreshold, relThreshold, progressReceiver);
		}

		autoMatchLevel(matcher, absThreshold, relThreshold, ClassifierLevel.Intermediate, progressReceiver);
		autoMatchLevel(matcher, absThreshold, relThreshold, ClassifierLevel.Full, progressReceiver);
		autoMatchLevel(matcher, absThreshold, relThreshold, ClassifierLevel.Extra, progressReceiver);

		boolean matchedAny;

		do {
			matchedAny = matcher.autoMatchMethodArgs(ClassifierLevel.Full, absThreshold, relThreshold, progressReceiver);
		} while (matchedAny);

		env.getCache().clear();
	}

	private static void autoMatchLevel(Matcher matcher, double absThreshold, double relThreshold, ClassifierLevel level, DoubleConsumer progressReceiver) {
		boolean matchedAny;
		boolean matchedClassesBefore = true;

		do {
			matchedAny = matcher.autoMatchMethods(level, absThreshold, relThreshold, progressReceiver);
			matchedAny |= matcher.autoMatchFields(level, absThreshold, relThreshold, progressReceiver);

			if (!matchedAny && !matchedClassesBefore) {
				break;
			}

			matchedAny |= matchedClassesBefore = matcher.autoMatchClasses(level, absThreshold, relThreshold, progressReceiver);
		} while (matchedAny);
	}
}
