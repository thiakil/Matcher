package matcher.gui.menu;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.DoubleConsumer;

import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import matcher.Matcher;
import matcher.ProjectConfig;
import matcher.gui.Gui;
import matcher.type.ClassEnvironment;
import matcher.type.ClassInstance;
import matcher.type.FieldInstance;
import matcher.type.IClassEnv;
import matcher.type.IMatchable;
import matcher.type.MatchType;
import matcher.type.MethodInstance;
import matcher.type.MethodVarInstance;

public class UidMenu extends Menu {
	UidMenu(Gui gui) {
		super("UID");

		this.gui = gui;

		init();
	}

	private void init() {
		MenuItem menuItem = new MenuItem("Setup");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> setup());

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Import matches");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Importing matches...",
				this::importMatches,
				() -> gui.onMatchChange(EnumSet.allOf(MatchType.class)),
				Throwable::printStackTrace));

		menuItem = new MenuItem("Submit matches");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Submitting matches...",
				this::submitMatches,
				() -> { },
				Throwable::printStackTrace));
	}

	private void setup() {
		ProjectConfig config = ProjectConfig.getLast();

		Dialog<ProjectConfig> dialog = new Dialog<>();
		//dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setResizable(true);
		dialog.setTitle("UID Setup");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);

		UidSetupPane setupPane = new UidSetupPane(config, dialog.getOwner(), okButton);
		dialog.getDialogPane().setContent(setupPane);
		dialog.setResultConverter(button -> button == ButtonType.OK ? config : null);

		dialog.showAndWait().ifPresent(newConfig -> {
			setupPane.updateConfig();

			if (!newConfig.isValid()) return;

			newConfig.saveAsLast();
		});
	}

	private void importMatches(DoubleConsumer progressConsumer) {
		ProjectConfig config = ProjectConfig.getLast(); // TODO: keep around

		try {
			HttpURLConnection conn = (HttpURLConnection) new URL("https",
					config.getUidAddress().getHostString(),
					config.getUidAddress().getPort(),
					String.format("/%s/matches/%s/%s", config.getUidProject(), config.getUidVersionA(), config.getUidVersionB())).openConnection();
			conn.setRequestProperty("X-Token", config.getUidToken());

			progressConsumer.accept(0.5);

			try (DataInputStream is = new DataInputStream(conn.getInputStream())) {
				ClassEnvironment env = gui.getEnv();
				Matcher matcher = gui.getMatcher();
				int type;

				while ((type = is.read()) != -1) {
					int uid = is.readInt();
					String idA = is.readUTF();
					String idB = is.readUTF();

					ClassInstance clsA = getCls(env.getEnvA(), idA, type);
					ClassInstance clsB = getCls(env.getEnvB(), idB, type);
					if (clsA == null || clsB == null) continue;

					switch (type) {
					case TYPE_CLASS:
						matcher.match(clsA, clsB);
						break;
					case TYPE_METHOD:
					case TYPE_ARG:
					case TYPE_VAR: {
						MethodInstance methodA = getMethod(clsA, idA, type);
						MethodInstance methodB = getMethod(clsB, idB, type);
						if (methodA == null || methodB == null) break;

						if (type == TYPE_METHOD) {
							matcher.match(methodA, methodB);
						} else {
							idA = idA.substring(idA.lastIndexOf(')') + 1);
							idB = idB.substring(idB.lastIndexOf(')') + 1);

							MethodVarInstance varA = methodA.getVar(idA, type == TYPE_ARG);
							MethodVarInstance varB = methodB.getVar(idB, type == TYPE_ARG);

							if (varA != null && varB != null) {
								matcher.match(varA, varB);
							}
						}

						break;
					}
					case TYPE_FIELD: {
						FieldInstance fieldA = getField(clsA, idA);
						FieldInstance fieldB = getField(clsB, idB);
						if (fieldA == null || fieldB == null) break;

						matcher.match(fieldA, fieldB);
						break;
					}
					}
				}
			}

			progressConsumer.accept(1);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static ClassInstance getCls(IClassEnv env, String fullId, int type) {
		if (type == TYPE_CLASS) {
			return env.getLocalClsById(fullId);
		} else if (type == TYPE_FIELD) {
			int pos = fullId.lastIndexOf('/', fullId.lastIndexOf(";;") - 2);

			return env.getLocalClsById(fullId.substring(0, pos));
		} else {
			int pos = fullId.lastIndexOf('/', fullId.lastIndexOf('(') - 1);

			return env.getLocalClsById(fullId.substring(0, pos));
		}
	}

	private static MethodInstance getMethod(ClassInstance cls, String fullId, int type) {
		int end = type == TYPE_METHOD ? fullId.length() : fullId.lastIndexOf(')') + 1;

		return cls.getMethod(fullId.substring(fullId.lastIndexOf('/', fullId.lastIndexOf('(', end - 1) - 1) + 1, end));
	}

	private static FieldInstance getField(ClassInstance cls, String fullId) {
		return cls.getField(fullId.substring(fullId.lastIndexOf('/', fullId.lastIndexOf(";;") - 2) + 1));
	}

	private void submitMatches(DoubleConsumer progressConsumer) {
		ProjectConfig config = ProjectConfig.getLast(); // TODO: keep around

		try {
			HttpURLConnection conn = (HttpURLConnection) new URL("https",
					config.getUidAddress().getHostString(),
					config.getUidAddress().getPort(),
					String.format("/%s/link/%s/%s", config.getUidProject(), config.getUidVersionA(), config.getUidVersionB())).openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("X-Token", config.getUidToken());
			conn.setDoOutput(true);

			List<IMatchable<?>> requested = new ArrayList<>();

			try (DataOutputStream os = new DataOutputStream(conn.getOutputStream())) {
				for (ClassInstance cls : gui.getEnv().getClassesA()) {
					if (!cls.hasMatch() || !cls.isInput()) continue; // TODO: skip with known + matched uids

					assert cls.getMatch() != cls;

					requested.add(cls);
					os.writeByte(TYPE_CLASS);
					os.writeUTF(cls.getId());
					os.writeUTF(cls.getMatch().getId());

					for (MethodInstance method : cls.getMethods()) {
						if (!method.hasMatch() || !method.isReal()) continue;

						String srcMethodId = cls.getId()+"/"+method.getId();
						String dstMethodId = cls.getMatch().getId()+"/"+method.getMatch().getId();

						requested.add(method);
						os.writeByte(TYPE_METHOD);
						os.writeUTF(srcMethodId);
						os.writeUTF(dstMethodId);

						for (MethodVarInstance arg : method.getArgs()) {
							if (!arg.hasMatch()) continue;

							requested.add(arg);
							os.writeByte(TYPE_ARG);
							os.writeUTF(srcMethodId+arg.getId());
							os.writeUTF(dstMethodId+arg.getMatch().getId());
						}
					}

					for (FieldInstance field : cls.getFields()) {
						if (!field.hasMatch() || !field.isReal()) continue;

						requested.add(field);
						os.writeByte(TYPE_FIELD);
						os.writeUTF(cls.getId()+"/"+field.getId());
						os.writeUTF(cls.getMatch().getId()+"/"+field.getMatch().getId());
					}
				}
			}

			progressConsumer.accept(0.5);

			try (DataInputStream is = new DataInputStream(conn.getInputStream())) {
				for (IMatchable<?> matchable : requested) {
					int uid = is.readInt();
				}
			}

			progressConsumer.accept(1);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static final byte TYPE_CLASS = 0;
	private static final byte TYPE_METHOD = 1;
	private static final byte TYPE_FIELD = 2;
	private static final byte TYPE_ARG = 3;
	private static final byte TYPE_VAR = 4;

	private final Gui gui;
}
