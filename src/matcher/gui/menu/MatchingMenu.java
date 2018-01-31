package matcher.gui.menu;

import java.util.EnumSet;

import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import matcher.Matcher.MatchingStatus;
import matcher.gui.Gui;
import matcher.type.MatchType;

public class MatchingMenu extends Menu {
	MatchingMenu(Gui gui) {
		super("Matching");

		this.gui = gui;

		init();
	}

	private void init() {
		MenuItem menuItem = new MenuItem("Auto match all");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Auto matching...",
				gui.getMatcher()::autoMatchAll,
				() -> gui.onMatchChange(EnumSet.allOf(MatchType.class)),
				Throwable::printStackTrace));

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Auto class match");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Auto matching classes...",
				gui.getMatcher()::autoMatchClasses,
				() -> gui.onMatchChange(EnumSet.allOf(MatchType.class)),
				Throwable::printStackTrace));

		menuItem = new MenuItem("Auto perfect enum match");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Auto matching enums...",
				gui.getMatcher()::autoMatchPerfectEnums,
				() -> gui.onMatchChange(EnumSet.allOf(MatchType.class)),
				Throwable::printStackTrace));

		menuItem = new MenuItem("Auto method match");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Auto matching methods...",
				gui.getMatcher()::autoMatchMethods,
				() -> gui.onMatchChange(EnumSet.of(MatchType.Method)),
				Throwable::printStackTrace));

		menuItem = new MenuItem("Auto field match");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Auto matching fields...",
				gui.getMatcher()::autoMatchFields,
				() -> gui.onMatchChange(EnumSet.of(MatchType.Field)),
				Throwable::printStackTrace));

		menuItem = new MenuItem("Auto method arg match");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> gui.runProgressTask(
				"Auto matching method args...",
				gui.getMatcher()::autoMatchMethodArgs,
				() -> gui.onMatchChange(EnumSet.of(MatchType.MethodArg)),
				Throwable::printStackTrace));

		getItems().add(new SeparatorMenuItem());

		menuItem = new MenuItem("Status");
		getItems().add(menuItem);
		menuItem.setOnAction(event -> showMatchingStatus());
	}

	private void showMatchingStatus() {
		String status = gui.getMatcher().getStringStatusSummary(true);

		gui.showAlert(AlertType.INFORMATION, "Matching status", "Current matching status", status);
	}

	private final Gui gui;
}
