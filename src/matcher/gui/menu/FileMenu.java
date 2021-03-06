package matcher.gui.menu;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Window;
import matcher.ProjectConfig;
import matcher.gui.Gui;
import matcher.gui.GuiConstants;
import matcher.mapping.MappingFormat;
import matcher.type.MatchType;

public class FileMenu extends Menu {
	FileMenu(Gui gui) {
		super("File");

		this.gui = gui;

		init();
	}

	private void init() {
		MenuItem menuItem = new MenuItem("New project");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> newProject());

		menuItem = new MenuItem("Load project");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> loadProject());

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Load mappings");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> loadMappings(null));

		menuItem = new MenuItem("Load mappings (Enigma)");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> loadMappings(MappingFormat.ENIGMA));

		menuItem = new MenuItem("Load mappings (MCP dir)");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> loadMappings(MappingFormat.MCP));

		menuItem = new MenuItem("Save mappings");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> saveMappings(false));

		menuItem = new MenuItem("Save mappings (Enigma)");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> saveMappings(true));

		menuItem = new MenuItem("Clear mappings");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.getMatcher().clearMappings());

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Load matches");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> loadMatches());

		menuItem = new MenuItem("Save matches");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> saveMatches());

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Exit");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> Platform.exit());
	}

	private void newProject() {
		ProjectConfig config = ProjectConfig.getLast();

		Dialog<ProjectConfig> dialog = new Dialog<>();
		//dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setResizable(true);
		dialog.setTitle("Project configuration");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);

		dialog.getDialogPane().setContent(new NewProjectPane(config, dialog.getOwner(), okButton));
		dialog.setResultConverter(button -> button == ButtonType.OK ? config : null);

		dialog.showAndWait().ifPresent(newConfig -> {
			if (!newConfig.isValid()) return;

			newConfig.saveAsLast();

			gui.getMatcher().reset();
			gui.onProjectChange();

			gui.runProgressTask("Initializing files...",
					progressReceiver -> gui.getMatcher().init(newConfig, progressReceiver),
					() -> gui.onProjectChange(),
					Throwable::printStackTrace);
		});
	}

	private void loadProject() {
		Path file = Gui.requestFile("Select matches file", gui.getScene().getWindow(), getMatchesLoadExtensionFilters(), true);
		if (file == null) return;

		List<Path> paths = new ArrayList<>();

		Dialog<List<Path>> dialog = new Dialog<>();
		//dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setResizable(true);
		dialog.setTitle("Project paths");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		Node okButton = dialog.getDialogPane().lookupButton(ButtonType.OK);

		dialog.getDialogPane().setContent(new LoadProjectPane(paths, dialog.getOwner(), okButton));
		dialog.setResultConverter(button -> button == ButtonType.OK ? paths : null);

		dialog.showAndWait().ifPresent(newConfig -> {
			if (paths.isEmpty()) return;

			gui.getMatcher().reset();
			gui.onProjectChange();

			gui.runProgressTask("Initializing files...",
					progressReceiver -> gui.getMatcher().readMatches(file, paths, progressReceiver),
					() -> gui.onProjectChange(),
					Throwable::printStackTrace);
		});
	}

	private void loadMappings(MappingFormat format) {
		Window window = gui.getScene().getWindow();
		Path file;

		if (format == null || format.hasSingleFile()) {
			file = Gui.requestFile("Select mapping file", window, getMappingLoadExtensionFilters(), true);
		} else {
			file = Gui.requestDir("Select mapping dir", window);
		}

		if (file == null) return;

		Dialog<boolean[]> dialog = new Dialog<>();
		//dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setResizable(true);
		dialog.setTitle("UID Setup");
		dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		GridPane grid = new GridPane();
		grid.setHgap(GuiConstants.padding);
		grid.setVgap(GuiConstants.padding);

		grid.add(new Label("Target:"), 0, 0);

		ToggleGroup targetGroup = new ToggleGroup();
		RadioButton rbA = new RadioButton("A");
		rbA.setToggleGroup(targetGroup);
		rbA.setSelected(true);
		grid.add(rbA, 1, 0);
		RadioButton rbB = new RadioButton("B");
		rbB.setToggleGroup(targetGroup);
		grid.add(rbB, 2, 0);

		CheckBox replaceBox = new CheckBox("Replace");
		replaceBox.setSelected(true);
		grid.add(replaceBox, 0, 1, 3, 1);

		dialog.getDialogPane().setContent(grid);
		dialog.setResultConverter(button -> button == ButtonType.OK ? new boolean[] { rbA.isSelected(), replaceBox.isSelected() } : null);

		Optional<boolean[]> resultOpt = dialog.showAndWait();
		if (!resultOpt.isPresent()) return;

		boolean[] result = resultOpt.get();

		boolean forA = result[0];
		boolean replace = result[1];

		try {
			gui.getMatcher().readMappings(file, format, forA, replace);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

		gui.onMappingChange();
	}

	private static List<ExtensionFilter> getMappingLoadExtensionFilters() {
		MappingFormat[] formats = MappingFormat.values();
		List<ExtensionFilter> ret = new ArrayList<>(formats.length + 1);
		List<String> supportedExtensions = new ArrayList<>(formats.length);

		for (MappingFormat format : formats) {
			if (format.hasSingleFile()) supportedExtensions.add(format.getGlobPattern());
		}

		ret.add(new FileChooser.ExtensionFilter("All supported", supportedExtensions));

		for (MappingFormat format : formats) {
			if (format.hasSingleFile()) ret.add(new FileChooser.ExtensionFilter(format.name, format.getGlobPattern()));
		}

		return ret;
	}

	private void saveMappings(boolean toDir) {
		Window window = gui.getScene().getWindow();
		Path path;
		String ext;

		if (!toDir) {
			FileChooser fileChooser = new FileChooser();
			fileChooser.setTitle("Save mapping file");

			for (MappingFormat format : MappingFormat.values()) {
				fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(format.name, "*."+format.fileExt));
			}

			File file = fileChooser.showSaveDialog(window);
			path = file == null ? null : file.toPath();
			ext = fileChooser.getSelectedExtensionFilter().getDescription();
		} else {
			path = Gui.requestDir("Save mapping dir", window);

			if (Files.exists(path) && !isDirEmpty(path)) { // reusing existing dir, clear out after confirmation
				if (!gui.requestConfirmation("Save Confirmation", "Replace existing data", "The selected save location is not empty.\nDo you want to clear and reuse it?")) return;

				try {
					if (!clearDir(path, file -> !Files.isDirectory(file) && !file.getFileName().toString().endsWith(".mapping"))) {
						gui.showAlert(AlertType.ERROR, "Save error", "Error while preparing save location", "The target directory contains non-mapping files.");
						return;
					}
				} catch (IOException e) {
					e.printStackTrace();
					gui.showAlert(AlertType.ERROR, "Save error", "Error while preparing save location", e.getMessage());
					return;
				}
			}

			ext = null;
		}

		if (path == null) return;

		MappingFormat format = getFormat(path);

		if (format == null) {
			format = getFormat(ext);
			if (format == null) throw new IllegalStateException("mapping format detection failed");

			if (format.hasSingleFile()) {
				path = path.resolveSibling(path.getFileName().toString()+"."+format.fileExt);
			}
		}

		try {
			if (Files.exists(path)) {
				if (Files.isDirectory(path) != !format.hasSingleFile()) {
					gui.showAlert(AlertType.ERROR, "Save error", "Invalid file selection", "The selected file is of the wrong type.");
					return;
				}

				Files.deleteIfExists(path);
			}

			if (!gui.getMatcher().saveMappings(path, format)) {
				gui.showAlert(AlertType.WARNING, "Mapping save warning", "No mappings to save", "There are currently no names mapped to matched classes, so saving was aborted.");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}

	private static boolean isDirEmpty(Path dir) {
		try (Stream<Path> stream = Files.list(dir)) {
			return !stream.anyMatch(ignore -> true);
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	private static boolean clearDir(Path path, Predicate<Path> disallowed) throws IOException {
		try (Stream<Path> stream = Files.walk(path, FileVisitOption.FOLLOW_LINKS)) {
			if (stream.anyMatch(disallowed)) return false;
		}

		AtomicBoolean ret = new AtomicBoolean(true);

		Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (disallowed.test(file)) {
					ret.set(false);

					return FileVisitResult.TERMINATE;
				} else {
					Files.delete(file);

					return FileVisitResult.CONTINUE;
				}
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
				if (exc != null) throw exc;
				if (!dir.equals(path)) Files.delete(dir);

				return FileVisitResult.CONTINUE;
			}
		});

		return ret.get();
	}

	private static MappingFormat getFormat(Path file) {
		if (Files.isDirectory(file)) return MappingFormat.ENIGMA;

		String name = file.getFileName().toString().toLowerCase(Locale.ENGLISH);

		for (MappingFormat format : MappingFormat.values()) {
			if (format.hasSingleFile()
					&& name.endsWith(format.fileExt)
					&& name.length() > format.fileExt.length()
					&& name.charAt(name.length() - 1 - format.fileExt.length()) == '.') {
				return format;
			}
		}

		return null;
	}

	private static MappingFormat getFormat(String selectedName) {
		for (MappingFormat format : MappingFormat.values()) {
			if (format.name.equals(selectedName)) return format;
		}

		return null;
	}

	private void loadMatches() {
		Path file = Gui.requestFile("Select matches file", gui.getScene().getWindow(), getMatchesLoadExtensionFilters(), true);
		if (file == null) return;

		gui.getMatcher().readMatches(file, null, progress ->  {});
		gui.onMatchChange(EnumSet.allOf(MatchType.class));
	}

	private static List<ExtensionFilter> getMatchesLoadExtensionFilters() {
		return Arrays.asList(new FileChooser.ExtensionFilter("Matches", "*.match"));
	}

	private void saveMatches() {
		Path path = Gui.requestFile("Save matches file", gui.getScene().getWindow(), Arrays.asList(new FileChooser.ExtensionFilter("Matches", "*.match")), false);
		if (path == null) return;

		if (!path.getFileName().toString().toLowerCase(Locale.ENGLISH).endsWith(".match")) {
			path = path.resolveSibling(path.getFileName().toString()+".match");
		}

		try {
			if (Files.isDirectory(path)) {
				gui.showAlert(AlertType.ERROR, "Save error", "Invalid file selection", "The selected file is a directory.");
			} else if (Files.exists(path)) {
				Files.deleteIfExists(path);
			}

			if (!gui.getMatcher().saveMatches(path)) {
				gui.showAlert(AlertType.WARNING, "Matches save warning", "No matches to save", "There are currently no matched classes, so saving was aborted.");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}
	}

	private final Gui gui;
}
